package de.zerogallery.ui.detail

import androidx.compose.foundation.gestures.detectTransformGestures
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
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, MaxZoom)
                    scale = newScale
                    offset = if (newScale <= 1f) Offset.Zero else offset + pan
                }
            },
    )
}

