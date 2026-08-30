package de.zerogallery.ui.gallery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val ThumbHeight = 48.dp
private val ThumbWidth = 5.dp
private val ThumbTouchWidth = 28.dp
private val HideDelay = 1_000.milliseconds

/**
 * A Google Photos-style "fast scroll" thumb overlaid on [MediaGrid]/[FolderGrid]: a small pill on
 * the right edge that tracks the grid's current scroll position, and can be dragged up/down to
 * jump directly to any position - much faster than flinging through a grid that can hold
 * thousands of items one screen at a time.
 *
 * Only shown while the grid itself is actively being scrolled or while the thumb is being
 * dragged, fading out [HideDelay] after either stops so it doesn't clutter the grid at rest, same
 * as Photos/most other fast-scrollers. Hidden entirely whenever [gridState] has too few items to
 * actually need one.
 *
 * The right-edge touch target itself, however, always stays "live" (just invisible while faded
 * out - [Modifier.alpha] only affects drawing, not touch handling) rather than only mounting once
 * [gridState] happens to already be mid-scroll: otherwise there'd be no way to ever *start* a drag
 * from rest, since the thumb visually appearing was itself gated on a scroll already being in
 * progress - a chicken-and-egg that made the thumb effectively ungrabbable.
 *
 * Position/dragging is necessarily approximate: [LazyGridState] never lays out (or even knows the
 * size of) items far outside the current viewport, so there's no real "total content height" to
 * derive an exact scroll percentage from. Both the thumb's resting position and the drag-to-index
 * mapping are instead computed from the grid's total item count - a coarse but stable
 * approximation that's perfectly fine for a *fast, approximate* navigation aid rather than a
 * precise scrollbar.
 */
@Composable
internal fun FastScrollbar(gridState: LazyGridState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragThumbTopPx by remember { mutableFloatStateOf(0f) }
    val thumbHeightPx = with(density) { ThumbHeight.toPx() }

    val isScrollable by remember {
        derivedStateOf {
            gridState.layoutInfo.totalItemsCount > gridState.layoutInfo.visibleItemsInfo.size
        }
    }
    val restingProgress by remember { derivedStateOf { gridState.scrollProgress() } }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(gridState.isScrollInProgress, isDragging, isScrollable) {
        if (isScrollable && (gridState.isScrollInProgress || isDragging)) {
            visible = true
        } else {
            delay(HideDelay)
            visible = false
        }
    }
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, label = "fastScrollbarAlpha")

    if (!isScrollable) return

    val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbTopPx = if (isDragging) dragThumbTopPx else restingProgress * travel

    Box(
        modifier = modifier
            .width(ThumbTouchWidth)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(gridState) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragThumbTopPx = (offset.y - thumbHeightPx / 2).coerceIn(0f, travel)
                        coroutineScope.launch {
                            gridState.scrollToFraction(if (travel > 0f) dragThumbTopPx / travel else 0f)
                        }
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    dragThumbTopPx = (dragThumbTopPx + dragAmount).coerceIn(0f, travel)
                    coroutineScope.launch {
                        gridState.scrollToFraction(if (travel > 0f) dragThumbTopPx / travel else 0f)
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, thumbTopPx.roundToInt()) }
                .width(ThumbWidth)
                .height(ThumbHeight)
                .alpha(alpha)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * Approximate normalized (0f..1f) scroll progress, accounting for partial scroll within the first
 * visible item (not just its index) so the thumb moves smoothly rather than snapping between
 * discrete steps - see [FastScrollbar]'s class doc for why this can only ever be approximate.
 */
internal fun LazyGridState.scrollProgress(): Float {
    val info = layoutInfo
    val totalItems = info.totalItemsCount
    val visibleItems = info.visibleItemsInfo
    if (totalItems <= 0 || visibleItems.isEmpty()) return 0f
    val first = visibleItems.first()
    val itemHeight = first.size.height.takeIf { it > 0 } ?: return 0f
    val fractionalIndex = first.index + (-first.offset.y.toFloat() / itemHeight)
    val scrollableRange = (totalItems - visibleItems.size).coerceAtLeast(1)
    return (fractionalIndex / scrollableRange).coerceIn(0f, 1f)
}

/** Jumps to the item at [fraction] (0f..1f) through the grid's total item count. */
internal suspend fun LazyGridState.scrollToFraction(fraction: Float) {
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems <= 0) return
    val targetIndex = (fraction.coerceIn(0f, 1f) * (totalItems - 1)).roundToInt()
        .coerceIn(0, totalItems - 1)
    scrollToItem(targetIndex)
}








