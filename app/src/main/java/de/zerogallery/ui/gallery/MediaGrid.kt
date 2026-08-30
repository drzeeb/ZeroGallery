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
import de.zerogallery.ui.util.WindowWidthSizeClass
import de.zerogallery.ui.util.rememberWindowWidthSizeClass

/**
 * Adaptive, Pinterest/Photos-style thumbnail grid.
 *
 * Uses [GridCells.Adaptive] rather than a hard-coded column count: the number of columns grows
 * automatically with the available width, so the exact same composable yields ~3 columns on a
 * narrow phone and significantly more on a tablet or in landscape. On top of that,
 * [windowWidthSizeClass] scales the minimum thumbnail size and the grid's spacing/padding up on
 * larger screens, so tablets get comfortably sized tiles and margins instead of many tiny,
 * edge-to-edge tiles.
 */
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    windowWidthSizeClass: WindowWidthSizeClass = rememberWindowWidthSizeClass(),
) {
    val minThumbnailSize = when (windowWidthSizeClass) {
        WindowWidthSizeClass.COMPACT -> 120.dp
        WindowWidthSizeClass.MEDIUM -> 140.dp
        WindowWidthSizeClass.EXPANDED -> 160.dp
    }
    val spacing = when (windowWidthSizeClass) {
        WindowWidthSizeClass.COMPACT -> 2.dp
        WindowWidthSizeClass.MEDIUM -> 4.dp
        WindowWidthSizeClass.EXPANDED -> 8.dp
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minThumbnailSize),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalArrangement = Arrangement.spacedBy(spacing),
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


