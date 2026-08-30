package de.zerogallery.ui.detail

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/** Zoom is clamped between "fit" (1x) and this factor to avoid pinching into blurry oblivion. */
private const val MaxZoom = 5f

/**
 * Full-screen photo with pinch-to-zoom and pan, for use inside [MediaDetailScreen]'s pager.
 *
 * Zoom/pan state is `remember`ed *keyed on [model]*, so it automatically resets to a fitted view
 * whenever the pager moves to a different image - the user never gets stuck zoomed into a photo
 * they've already swiped away from.
 *
 * Gestures are handled with a custom, manually-consuming detector rather than
 * `detectTransformGestures` on purpose: that function treats *any* single-finger movement as a
 * "pan" and unconditionally consumes it, which silently ate every horizontal drag before it could
 * ever reach the enclosing `HorizontalPager` - making it impossible to swipe between photos at
 * all. Here, a single-finger drag is only consumed to pan the image while it's actually zoomed in
 * ([scale] > 1); at the default 1x fit, single-finger drags are left unconsumed so the pager
 * receives them as a normal page-swipe. Two-finger pinch gestures (zooming in/out) are always
 * consumed, regardless of the current zoom level.
 */
@Composable
fun ZoomableAsyncImage(model: Any, contentDescription: String?, modifier: Modifier = Modifier) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            )
            .pointerInput(model) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val isPinch = event.changes.size > 1
                        val isZoomedIn = scale > 1f

                        if (isPinch || isZoomedIn) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, MaxZoom)
                            scale = newScale
                            offset = if (newScale <= 1f) Offset.Zero else offset + panChange
                            event.changes.forEach { it.consume() }
                        }
                        // else: a plain single-finger drag while at 1x zoom - leave it unconsumed
                        // so the HorizontalPager can treat it as a swipe-to-next/previous gesture.
                    } while (event.changes.any { it.pressed })
                }
            },
    )
}



