package de.zerogallery.ui.detail

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.zerogallery.R
import de.zerogallery.ui.gallery.formatDuration
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The on-screen aspect-ratio behaviours a user can cycle through by tapping the aspect-ratio
 * button in [VideoPlayer], mirroring the aspect-ratio cycling found in players like VLC:
 * - [FIT]: the whole video stays visible, letterboxed with black bars if its aspect ratio doesn't
 *   match the screen - the default, no cropping or distortion.
 * - [STRETCH]: the video fills the entire screen edge-to-edge with no black bars, preserving its
 *   original aspect ratio by cropping whatever doesn't fit (zoom-to-fill). VLC itself never
 *   distorts the picture when "filling" the screen either - it always preserves the video's
 *   proportions and crops the overflow instead, never scaling width/height independently.
 *
 * [AspectRatioFrameLayout]'s `RESIZE_MODE_*` constants are `@UnstableApi` in Media3, opted into
 * project-wide via `app/lint.xml` since they're just plain, stable resize-mode integers under the
 * hood - not something worth propagating `@UnstableApi` for through every composable call site.
 */
private enum class VideoResizeMode(val frameLayoutMode: Int, val labelRes: Int) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, R.string.video_resize_mode_fit),
    STRETCH(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, R.string.video_resize_mode_stretch),
}

/** How long the current mode's label stays visible after tapping the aspect-ratio button. */
private const val ResizeModeLabelVisibleMillis = 1_200L

/**
 * Plays a single local video via Media3/ExoPlayer (Apache 2.0 - no licensing conflicts with this
 * project's own Apache 2.0 license, unlike GPL/LGPL alternatives such as LibVLC).
 *
 * A dedicated [ExoPlayer] is created per [uri] and released as soon as this composable leaves the
 * composition or [uri] changes - the pager only keeps the current (and maybe one adjacent) page
 * composed, so at most one or two players ever exist at a time. Playback is driven by [isActive]:
 * the pager sets this to `true` only for the currently visible page, so swiping away from a video
 * pauses it immediately instead of letting it keep playing off-screen.
 *
 * [PlayerView]'s built-in controller (`useController = true`) is a classic Android `View` with its
 * own touch handling (seek bar drag, tap-to-show/hide controls). Nested inside a Compose
 * `HorizontalPager`, that native touch dispatch can permanently steal the pager's swipe gesture
 * after the first page change (a well-known Android View/Compose interop pitfall). To avoid this,
 * the controller is disabled and play/pause is instead handled by a dedicated Compose button.
 *
 * Tapping *anywhere* on the video only toggles [isChromeVisible] via [onTap] - it deliberately does
 * *not* also toggle play/pause, unlike an earlier version of this screen. Coupling the two meant
 * there was no way to hide the chrome for a clean, distraction-free fullscreen view without also
 * pausing the video. Instead, a dedicated play/pause button (along with the aspect-ratio button and
 * its label) is only shown while [isChromeVisible] is true, mirroring how most video players
 * separate "tap to show/hide controls" from "tap the actual button to control playback". While the
 * chrome is hidden, nothing at all is drawn on top of the video - no buttons, no icons.
 *
 * Playback is also paused whenever the app itself goes into the background (`Lifecycle.Event
 * .ON_STOP` - covers the home button, switching apps, locking the screen, etc.), same as
 * [isActive] pausing it when swiped off-screen within the pager. It intentionally does *not*
 * auto-resume when the app comes back to the foreground - the user has to explicitly tap play
 * again, same as e.g. YouTube, rather than audio/video suddenly starting back up on its own.
 *
 * A scrubber [Slider] at the bottom (also only shown while [isChromeVisible]) lets the user seek
 * within the video. [ExoPlayer] has no "position changed" callback, so the current position is
 * polled on a 200ms timer instead ([positionMs]) - cheap enough for a single foreground player and
 * far simpler than reaching for `Player.Listener.onEvents` diffing. Polling is suspended while the
 * user is actively dragging the thumb ([isSeeking]) so it can't fight the drag by snapping the
 * thumb back to the *actual* (pre-seek) playback position on every tick; the real seek only
 * happens once the drag ends (`onValueChangeFinished`), not on every intermediate value while
 * dragging, to avoid flooding the player with seek requests.
 *
 * A horizontal drag *while [isChromeVisible] is false* scrubs the same way, live, instead of
 * swiping to the next/previous item in the pager - dragging across the full screen width covers
 * the entire video, same ratio as the [Slider]. This has to fully replace the pager's own gesture
 * handling rather than run alongside it: a custom `awaitEachGesture` loop consumes every move
 * event for the whole gesture as soon as the chrome is hidden (even before a direction is known),
 * so the pager never gets a chance to see any of it and start its own swipe-to-next-item drag -
 * only once the accumulated horizontal movement exceeds touch slop *and* dominates over vertical
 * movement does it actually start scrubbing; a vertical-dominant drag is still consumed (so it can
 * later drive brightness/volume gestures) but otherwise left a no-op for now. The same loop also
 * detects a plain tap (near-zero total movement) to toggle the chrome, replacing the plain
 * `Modifier.clickable` an earlier version of this composable used for that alone. While
 * [isChromeVisible] is true, nothing is consumed here at all, so the pager's normal swipe-to-next
 * behaviour keeps working exactly as before.
 */
@Composable
fun VideoPlayer(
    uri: Uri,
    isActive: Boolean,
    isChromeVisible: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3MediaItem.fromUri(uri))
            prepare()
        }
    }

    var isPlaying by remember(exoPlayer) { mutableStateOf(exoPlayer.isPlaying) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(isActive) {
        exoPlayer.playWhenReady = isActive
        onDispose { }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        exoPlayer.pause()
    }

    var durationMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var positionMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var isSeeking by remember(exoPlayer) { mutableStateOf(false) }
    var seekPositionMs by remember(exoPlayer) { mutableFloatStateOf(0f) }

    LaunchedEffect(exoPlayer, isChromeVisible) {
        while (isChromeVisible) {
            if (!isSeeking) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val duration = exoPlayer.duration
                if (duration > 0) durationMs = duration
            }
            delay(200)
        }
    }

    var resizeModeIndex by remember(uri) { mutableIntStateOf(0) }
    var isResizeModeLabelVisible by remember(uri) { mutableStateOf(false) }
    val resizeMode = VideoResizeMode.entries[resizeModeIndex]

    // Reflects whichever is currently authoritative: the live drag/slider value while seeking,
    // the polled playback position otherwise.
    val displayPositionMs = if (isSeeking) seekPositionMs.toLong() else positionMs

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            update = { playerView -> playerView.resizeMode = resizeMode.frameLayoutMode },
        )

        // See class doc: handles both the tap-to-toggle-chrome gesture and, while the chrome is
        // hidden, a horizontal drag-to-scrub gesture that fully takes over from the pager's
        // default swipe-to-next-item behaviour.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isChromeVisible, exoPlayer) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDrag = Offset.Zero
                        var isSeekGesture = false
                        var seekGestureStartPositionMs = 0L
                        var seekGestureDurationMs = 1L

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.positionChange()
                            totalDrag += delta

                            if (!isChromeVisible) {
                                if (!isSeekGesture &&
                                    abs(totalDrag.x) > viewConfiguration.touchSlop &&
                                    abs(totalDrag.x) > abs(totalDrag.y)
                                ) {
                                    isSeekGesture = true
                                    isSeeking = true
                                    seekGestureStartPositionMs =
                                        exoPlayer.currentPosition.coerceAtLeast(0L)
                                    seekGestureDurationMs = exoPlayer.duration.coerceAtLeast(1L)
                                }
                                if (isSeekGesture) {
                                    val deltaMs =
                                        (totalDrag.x / size.width) * seekGestureDurationMs
                                    seekPositionMs = (seekGestureStartPositionMs + deltaMs)
                                        .coerceIn(0f, seekGestureDurationMs.toFloat())
                                }
                                // Consume every move once the chrome is hidden, even before a
                                // direction is known - see class doc for why.
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        if (isSeekGesture) {
                            exoPlayer.seekTo(seekPositionMs.toLong())
                            isSeeking = false
                        } else if (totalDrag.getDistance() < viewConfiguration.touchSlop) {
                            onTap()
                        }
                    }
                },
        )

        // Feedback for the drag-to-scrub gesture while the chrome (and with it, the regular seek
        // bar) is hidden - without this there'd be no indication at all of where a scrub landed.
        AnimatedVisibility(
            visible = isSeeking && !isChromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                text = "${formatDuration(displayPositionMs)} / ${formatDuration(durationMs)}",
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        AnimatedVisibility(
            visible = isChromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .padding(12.dp),
                    )
                }

                // Top padding clears MediaDetailScreen's overlay app bar (status bar + ~56.dp title
                // bar) so this button doesn't sit underneath it.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 56.dp, end = 8.dp, bottom = 8.dp),
                ) {
                    IconButton(
                        onClick = {
                            resizeModeIndex = (resizeModeIndex + 1) % VideoResizeMode.entries.size
                            isResizeModeLabelVisible = true
                            coroutineScope.launch {
                                delay(ResizeModeLabelVisibleMillis)
                                isResizeModeLabelVisible = false
                            }
                        },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AspectRatio,
                            contentDescription = stringResource(R.string.video_resize_mode_action),
                            tint = Color.White,
                        )
                    }

                    AnimatedVisibility(
                        visible = isResizeModeLabelVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 56.dp, end = 8.dp),
                    ) {
                        Text(
                            text = stringResource(resizeMode.labelRes),
                            color = Color.White,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                // Seek bar, bottom-aligned. displayPositionMs (hoisted above, also driving the
                // hidden-chrome scrub indicator) reflects the drag in progress (if any) rather
                // than the polled playback position, so the thumb doesn't jump back to the
                // pre-seek position while the user is still dragging it.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = formatDuration(displayPositionMs),
                        color = Color.White,
                        modifier = Modifier.width(48.dp),
                    )
                    Slider(
                        value = displayPositionMs.toFloat(),
                        onValueChange = {
                            isSeeking = true
                            seekPositionMs = it
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(seekPositionMs.toLong())
                            isSeeking = false
                        },
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatDuration(durationMs),
                        color = Color.White,
                        modifier = Modifier.width(48.dp),
                    )
                }
            }
        }
    }
}


