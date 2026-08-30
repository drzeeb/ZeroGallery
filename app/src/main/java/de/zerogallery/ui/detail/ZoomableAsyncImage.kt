package de.zerogallery.ui.detail

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** Zoom is clamped between "fit" (1x) and this factor to avoid pinching into blurry oblivion. */
private const val MaxZoom = 5f

/** Zoom level a double-tap jumps to (matching what most gallery apps use, e.g. Google Photos). */
private const val DoubleTapZoom = 3f

/** Short and snappy on purpose - this is a quick zoom-in/out flourish, not a slow transition. */
private const val DoubleTapZoomAnimationMillis = 200

/** Max time between the two taps' "up" events to still count as one double-tap. */
private const val DoubleTapTimeoutMillis = 300L

/** Max distance between the two taps' positions to still count as one double-tap, in px. */
private const val DoubleTapSlopPx = 100f

/**
 * Computes the translation needed so that [tapPosition] (in the image's un-scaled local
 * coordinate space) ends up visually centered within [containerSize] once scaled by [zoom].
 *
 * Derived from `graphicsLayer`'s transform order: `visualPos = pivot + zoom * (p - pivot) +
 * translation`, with `pivot` being the container's center (the default `transformOrigin`). Solving
 * for the translation that puts `tapPosition` at `pivot` gives `translation = (pivot - tapPosition)
 * * zoom`. Pure and side-effect-free so it can be unit-tested without pulling in Compose UI/an
 * Android device.
 */
internal fun computeDoubleTapOffset(containerSize: IntSize, tapPosition: Offset, zoom: Float): Offset {
    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
    return (center - tapPosition) * zoom
}

/**
 * Full-screen photo with pinch-to-zoom, pan and double-tap-to-zoom, for use inside
 * [MediaDetailScreen]'s pager.
 *
 * Zoom/pan state is `remember`ed *keyed on [model]*, so it automatically resets to a fitted view
 * whenever the pager moves to a different image - the user never gets stuck zoomed into a photo
 * they've already swiped away from.
 *
 * All gestures (pinch, pan, double-tap) are handled by a single, custom `awaitEachGesture` loop
 * rather than combining Compose's `detectTransformGestures`/`detectTapGestures` on purpose:
 *
 * - `detectTransformGestures` treats *any* single-finger movement as a "pan" and unconditionally
 *   consumes it, which silently ate every horizontal drag before it could ever reach the enclosing
 *   `HorizontalPager` - making it impossible to swipe between photos at all. Here, a single-finger
 *   drag is only consumed to pan the image while it's actually zoomed in (`scale` > 1); at the
 *   default 1x fit, drags are left unconsumed so the pager receives them as a normal page-swipe.
 * - Layering a *separate* `detectTapGestures(onDoubleTap = ...)` `pointerInput` alongside that pan
 *   logic doesn't work either: `detectTapGestures` only starts tracking a tap from an *unconsumed*
 *   down event, but while zoomed in, the pan logic above consumes every single-finger event
 *   (including a stationary tap-and-release) before the tap detector ever saw it - so double-tap
 *   could zoom **in** (at 1x, nothing gets consumed) but never back **out** (while already zoomed,
 *   every tap got eaten as a "pan"). Tracking taps ourselves in the same loop - independent of
 *   whether we also consumed those events for panning - sidesteps that entirely.
 *
 * Pinch/pan updates [scale]/[offset] instantly (`snapTo`), since the user's fingers are actively
 * driving them frame-by-frame; the double-tap zoom transition is animated (`animateTo`) with a
 * short, snappy [DoubleTapZoomAnimationMillis]-long tween instead.
 */
@Composable
fun ZoomableAsyncImage(model: Any, contentDescription: String?, modifier: Modifier = Modifier) {
    val scale = remember(model) { Animatable(1f) }
    val offset = remember(model) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var containerSize by remember(model) { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .graphicsLayer(
                scaleX = scale.value,
                scaleY = scale.value,
                translationX = offset.value.x,
                translationY = offset.value.y,
            )
            .pointerInput(model) {
                var lastTapUpTimeMillis = 0L
                var lastTapPosition: Offset? = null

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalPanDistance = 0f
                    var sawMultiTouch = false

                    do {
                        val event = awaitPointerEvent()
                        val isPinch = event.changes.size > 1
                        if (isPinch) sawMultiTouch = true
                        val isZoomedIn = scale.value > 1f

                        if (isPinch || isZoomedIn) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            totalPanDistance += panChange.getDistance()
                            val newScale = (scale.value * zoomChange).coerceIn(1f, MaxZoom)
                            val newOffset = if (newScale <= 1f) Offset.Zero else offset.value + panChange
                            // awaitPointerEventScope is a restricted-suspension scope: it can't
                            // call arbitrary suspend functions like Animatable.snapTo directly, so
                            // this has to hop into a plain coroutine. Both snapTo calls are bundled
                            // into a single launch to keep them in lockstep with each other.
                            coroutineScope.launch {
                                scale.snapTo(newScale)
                                offset.snapTo(newOffset)
                            }
                            event.changes.forEach { it.consume() }
                        }
                        // else: a plain single-finger drag while at 1x zoom - leave it unconsumed
                        // so the HorizontalPager can treat it as a swipe-to-next/previous gesture.
                    } while (event.changes.any { it.pressed })

                    // A "tap" is a gesture that never turned into a real pinch or a noticeable
                    // drag - regardless of whether we already consumed its events above for
                    // (near-zero) panning purposes while zoomed in.
                    val wasTap = !sawMultiTouch && totalPanDistance < viewConfiguration.touchSlop
                    if (wasTap) {
                        val now = SystemClock.uptimeMillis()
                        val previousTapPosition = lastTapPosition
                        val isDoubleTap = previousTapPosition != null &&
                            now - lastTapUpTimeMillis <= DoubleTapTimeoutMillis &&
                            (down.position - previousTapPosition).getDistance() < DoubleTapSlopPx

                        if (isDoubleTap) {
                            val targetScale: Float
                            val targetOffset: Offset
                            if (scale.value > 1f) {
                                targetScale = 1f
                                targetOffset = Offset.Zero
                            } else {
                                targetScale = DoubleTapZoom
                                targetOffset =
                                    computeDoubleTapOffset(containerSize, down.position, DoubleTapZoom)
                            }
                            coroutineScope.launch {
                                scale.animateTo(targetScale, tween(DoubleTapZoomAnimationMillis))
                            }
                            coroutineScope.launch {
                                offset.animateTo(targetOffset, tween(DoubleTapZoomAnimationMillis))
                            }
                            lastTapPosition = null
                            lastTapUpTimeMillis = 0L
                        } else {
                            lastTapPosition = down.position
                            lastTapUpTimeMillis = now
                        }
                    }
                }
            },
    )
}







