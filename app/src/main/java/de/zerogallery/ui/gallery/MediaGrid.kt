package de.zerogallery.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaType

/** Minimum width a grid cell may shrink to before an extra column is added. */
private val MinThumbnailSize = 120.dp

/**
 * Adaptive, Pinterest/Photos-style thumbnail grid.
 *
 * Uses [GridCells.Adaptive] rather than a hard-coded column count: the number of columns grows
 * automatically with the available width, so the exact same composable yields ~3 columns on a
 * narrow phone and significantly more on a tablet or in landscape - without any manual
 * `WindowSizeClass` breakpoints (those are introduced in Phase 3 for coarser layout decisions,
 * e.g. switching to a navigation rail).
 */
@Composable
fun MediaGrid(items: List<MediaItem>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = MinThumbnailSize),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items = items, key = { it.id }) { item ->
            MediaGridItem(item)
        }
    }
}

@Composable
private fun MediaGridItem(item: MediaItem) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (item.mediaType == MediaType.VIDEO) {
            VideoBadge(
                durationMillis = item.durationMillis ?: 0L,
                modifier = Modifier.align(Alignment.BottomEnd),
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
            modifier = Modifier.aspectRatio(1f),
        )
        Text(
            text = formatDuration(durationMillis),
            color = Color.White,
            fontSize = 11.sp,
        )
    }
}


