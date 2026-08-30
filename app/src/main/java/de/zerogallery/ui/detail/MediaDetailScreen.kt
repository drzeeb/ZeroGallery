package de.zerogallery.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaType

/**
 * Full-screen, swipeable detail viewer opened by tapping a tile in
 * [de.zerogallery.ui.gallery.MediaGrid].
 *
 * Photos support pinch-to-zoom/pan ([ZoomableAsyncImage]); videos play inline via Media3/
 * ExoPlayer ([VideoPlayer]), automatically pausing as soon as the user swipes to a different page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }
    val currentItem = items.getOrNull(pagerState.currentPage)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentItem?.displayName.orEmpty(),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
        ) { page ->
            val item = items[page]
            Box(modifier = Modifier.fillMaxSize()) {
                when (item.mediaType) {
                    MediaType.IMAGE -> ZoomableAsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                    )

                    MediaType.VIDEO -> VideoPlayer(
                        uri = item.uri,
                        isActive = pagerState.currentPage == page,
                    )
                }
            }
        }
    }
}

