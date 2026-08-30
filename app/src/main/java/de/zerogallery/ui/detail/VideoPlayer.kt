package de.zerogallery.ui.detail

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.zerogallery.R
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The on-screen aspect-ratio behaviours a user can cycle through by tapping the aspect-ratio
 * button in [VideoPlayer], mirroring the aspect-ratio cycling found in players like VLC:
 * - [FIT]: the whole video stays visible, letterboxed with black bars if its aspect ratio doesn't
 *   match the screen - the default, no cropping or distortion.
 * - [CROP]: the video fills the entire screen with no black bars, preserving its aspect ratio by
 *   cropping whatever doesn't fit (zoom-to-fill).
 * - [STRETCH]: the video is enlarged to fill the entire screen edge-to-edge, exactly like [CROP] -
 *   *not* [AspectRatioFrameLayout.RESIZE_MODE_FILL], which stretches width/height independently
 *   and visibly distorts the picture (circles become ovals, people look squashed/elongated). A
 *   video's aspect ratio always has to be preserved one way or another, so "filling the whole
 *   screen" and "distorting the image" are mutually exclusive - the former is achieved here by
 *   uniformly scaling up and letting whatever doesn't fit run off-screen, same as [CROP].
 *
 * [AspectRatioFrameLayout]'s `RESIZE_MODE_*` constants are `@UnstableApi` in Media3, opted into
 * project-wide via `app/lint.xml` since they're just plain, stable resize-mode integers under the
 * hood - not something worth propagating `@UnstableApi` for through every composable call site.
 */
private enum class VideoResizeMode(val frameLayoutMode: Int, val labelRes: Int) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, R.string.video_resize_mode_fit),
    CROP(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, R.string.video_resize_mode_crop),
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
 * the controller is disabled and play/pause is instead handled by a plain Compose `clickable`
 * overlay, which only reacts to taps and lets horizontal drags pass through to the pager untouched.
 */
@Composable
fun VideoPlayer(uri: Uri, isActive: Boolean, modifier: Modifier = Modifier) {
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

    var resizeModeIndex by remember(uri) { mutableIntStateOf(0) }
    var isResizeModeLabelVisible by remember(uri) { mutableStateOf(false) }
    val resizeMode = VideoResizeMode.entries[resizeModeIndex]

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!isPlaying) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(12.dp),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
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
    }
}


