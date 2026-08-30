package de.zerogallery.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.zerogallery.R
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaType
import de.zerogallery.ui.util.WindowWidthSizeClass
import de.zerogallery.ui.util.rememberWindowWidthSizeClass
import kotlin.math.roundToInt



/** Minimum tile size and inter-tile spacing for both [MediaGrid] and [FolderGrid]. */
internal fun gridMinThumbnailSize(windowWidthSizeClass: WindowWidthSizeClass) =
    when (windowWidthSizeClass) {
        WindowWidthSizeClass.COMPACT -> 120.dp
        WindowWidthSizeClass.MEDIUM -> 140.dp
        WindowWidthSizeClass.EXPANDED -> 160.dp
    }

internal fun gridSpacing(windowWidthSizeClass: WindowWidthSizeClass) =
    when (windowWidthSizeClass) {
        WindowWidthSizeClass.COMPACT -> 2.dp
        WindowWidthSizeClass.MEDIUM -> 4.dp
        WindowWidthSizeClass.EXPANDED -> 8.dp
    }

/**
 * Adaptive, Pinterest/Photos-style thumbnail grid.
 *
 * Uses [GridCells.Adaptive] rather than a hard-coded column count: the number of columns grows
 * automatically with the available width, so the exact same composable yields ~3 columns on a
 * narrow phone and significantly more on a tablet or in landscape. On top of that,
 * [windowWidthSizeClass] scales the minimum thumbnail size and the grid's spacing/padding up on
 * larger screens, so tablets get comfortably sized tiles and margins instead of many tiny,
 * edge-to-edge tiles.
 *
 * [groups] (see [groupMedia]) drives optional section headers - a blank [MediaGroup.label] (used
 * for [MediaGroupingMode.NONE] and for a single opened folder's contents, see [FolderGrid])
 * renders no header at all, just a continuous flat grid. Non-blank labels get a full-width header
 * row above their section, e.g. "August 2026".
 *
 * Tapping a tile invokes [onItemClick] with its index into the *flattened* concatenation of all
 * [groups] - i.e. its position in whatever order is currently displayed on screen, not
 * necessarily the original chronological order. The caller uses that index to open
 * [de.zerogallery.ui.detail.MediaDetailScreen] at that page against that same flattened list, so
 * swiping through the detail viewer always matches whatever order the grid is currently showing.
 *
 * A [FastScrollbar] thumb is overlaid on the right edge, letting a large grid be dragged through
 * quickly instead of only flinging one screen at a time - see its class doc for details.
 *
 * The currently active section's own header (same [MediaGroupHeader], blank labels never render
 * one at all - see above) is additionally *pinned* to the very top of the grid at all times, both
 * during normal scrolling and while fast-scrolling - e.g. "August 2026" stays put while scrolling
 * through that month's tiles, however far below the header itself has actually scrolled, so it's
 * still always clear which section is on screen right now (including where a fast-scroll drag has
 * currently landed, without needing a separate indicator just for that). As the *next* section's
 * real header row scrolls up to meet it, the pinned header is pushed up and out of the way by
 * exactly that much (see [computeStickyHeaderOffset]) rather than one abruptly popping in front of
 * the other, then swaps over to that next section once its header reaches the top for real.
 *
 * Long-pressing a tile invokes [onItemLongClick] with that item - used to enter/extend a
 * multi-select mode (see [de.zerogallery.ui.gallery.GalleryRoute]). While [selectedIds] is
 * non-empty, tapping a tile is expected to toggle its selection instead of opening the detail
 * viewer - that distinction is made by whatever [onItemClick] does, not by [MediaGrid] itself, so
 * it stays a simple, stateless "index was tapped" callback either way.
 */
@Composable
fun MediaGrid(
    groups: List<MediaGroup>,
    onItemClick: (index: Int) -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass = rememberWindowWidthSizeClass(),
    selectedIds: Set<Long> = emptySet(),
    onItemLongClick: (MediaItem) -> Unit = {},
) {
    val minThumbnailSize = gridMinThumbnailSize(windowWidthSizeClass)
    val spacing = gridSpacing(windowWidthSizeClass)
    val gridState = rememberLazyGridState()
    var isDraggingThumb by remember { mutableStateOf(false) }
    var stickyHeaderHeightPx by remember { mutableFloatStateOf(0f) }
    val boundaries = remember(groups) { groupBoundaries(groups) }
    val currentLabel by remember(boundaries) {
        derivedStateOf {
            val firstVisibleIndex = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            currentGroupLabel(boundaries, firstVisibleIndex)
        }
    }
    val stickyHeaderOffsetPx by remember(stickyHeaderHeightPx) {
        derivedStateOf {
            val visibleItems = gridState.layoutInfo.visibleItemsInfo
            // While the *real* header row for the current section is itself still fully in view
            // (at or below the very top, e.g. the still-unscrolled initial state), it already does
            // the job on its own - null suppresses the pinned copy entirely so the two don't
            // render right on top of/next to each other.
            val currentHeaderOffsetY = visibleItems
                .firstOrNull { (it.key as? String) == "header-$currentLabel" }
                ?.offset?.y
            if (currentHeaderOffsetY != null && currentHeaderOffsetY >= 0) {
                null
            } else {
                val nextHeaderTopPx = visibleItems
                    .asSequence()
                    .filter { (it.key as? String)?.startsWith("header-") == true && it.offset.y > 0 }
                    .minOfOrNull { it.offset.y.toFloat() }
                computeStickyHeaderOffset(stickyHeaderHeightPx, nextHeaderTopPx)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = minThumbnailSize),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isDraggingThumb,
            contentPadding = PaddingValues(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            var flatIndex = 0
            for (group in groups) {
                if (group.label.isNotBlank()) {
                    item(key = "header-${group.label}", span = { GridItemSpan(maxLineSpan) }) {
                        MediaGroupHeader(label = group.label)
                    }
                }
                val startIndex = flatIndex
                itemsIndexed(items = group.items, key = { _, item -> item.id }) { indexInGroup, item ->
                    val index = startIndex + indexInGroup
                    MediaGridItem(
                        item = item,
                        isSelected = item.id in selectedIds,
                        showSelectionUi = selectedIds.isNotEmpty(),
                        onClick = { onItemClick(index) },
                        onLongClick = { onItemLongClick(item) },
                    )
                }
                flatIndex += group.items.size
            }
        }

        val stickyOffset = stickyHeaderOffsetPx
        if (currentLabel.isNotBlank() && stickyOffset != null) {
            MediaGroupHeader(
                label = currentLabel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .onSizeChanged { stickyHeaderHeightPx = it.height.toFloat() }
                    .offset { IntOffset(0, stickyOffset.roundToInt()) },
            )
        }

        FastScrollbar(
            gridState = gridState,
            onDraggingChange = { isDraggingThumb = it },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}

/**
 * A folder/album *picker*: one tile per [MediaGroupingMode.FOLDER] section, showing its most
 * recent item as a cover photo plus the folder's name and item count - deliberately *not* also
 * showing every single photo inside it.
 *
 * Without this, switching to folder grouping used to dump every item from every folder straight
 * into one giant, continuously-scrolling grid (just with header rows in between) - exactly as
 * overwhelming to scroll through as the ungrouped grid whenever one folder (e.g. "Screenshots")
 * happens to contain hundreds of items and the folder you actually wanted is further down. Tiles
 * here are tapped via [onFolderClick] to *open* a folder and only then see its individual items,
 * same as a typical file manager or Google Photos' "Albums" tab.
 */
@Composable
fun FolderGrid(
    folders: List<MediaGroup>,
    onFolderClick: (MediaGroup) -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass = rememberWindowWidthSizeClass(),
) {
    val minThumbnailSize = gridMinThumbnailSize(windowWidthSizeClass)
    val spacing = gridSpacing(windowWidthSizeClass)
    val gridState = rememberLazyGridState()
    var isDraggingThumb by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = minThumbnailSize),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isDraggingThumb,
            contentPadding = PaddingValues(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(items = folders, key = { it.label }) { folder ->
                FolderGridItem(folder = folder, onClick = { onFolderClick(folder) })
            }
        }

        FastScrollbar(
            gridState = gridState,
            onDraggingChange = { isDraggingThumb = it },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun FolderGridItem(folder: MediaGroup, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
    ) {
        val cover = folder.items.firstOrNull()
        if (cover != null) {
            AsyncImage(
                model = cover.uri,
                contentDescription = folder.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    ),
                )
                .padding(8.dp),
        ) {
            Text(
                text = folder.label,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.media_folder_item_count,
                    folder.items.size,
                    folder.items.size,
                ),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MediaGroupHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun MediaGridItem(
    item: MediaItem,
    isSelected: Boolean,
    showSelectionUi: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (isSelected) Modifier.padding(8.dp).clip(RoundedCornerShape(4.dp)) else Modifier),
        )

        if (item.mediaType == MediaType.VIDEO && !showSelectionUi) {
            VideoBadge(
                durationMillis = item.durationMillis ?: 0L,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        if (showSelectionUi) {
            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.Black.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
private fun VideoBadge(durationMillis: Long, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .padding(4.dp)
            .background(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = formatDuration(durationMillis),
            color = Color.White,
            fontSize = 11.sp,
        )
    }
}


