package de.zerogallery.ui.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaType

/** Walks up the [ContextWrapper] chain to find the enclosing [Activity], if any. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Full-screen, swipeable detail viewer opened by tapping a tile in
 * [de.zerogallery.ui.gallery.MediaGrid].
 *
 * Photos support pinch-to-zoom/pan ([ZoomableAsyncImage]); videos play inline via Media3/
 * ExoPlayer ([VideoPlayer]), automatically pausing as soon as the user swipes to a different page.
 *
 * The back button/filename bar is an overlay drawn *on top of* the media rather than a
 * [androidx.compose.material3.Scaffold] bar that reserves its own space - the pager behind it
 * always fills the entire screen edge-to-edge (this activity is edge-to-edge, see
 * [de.zerogallery.MainActivity]). A single tap on the media toggles [isChromeVisible], hiding or
 * showing that overlay, matching the common gallery-app convention (Google Photos, etc.) of
 * tap-to-toggle chrome so the photo/video itself can be viewed completely unobstructed.
 *
 * Hiding [isChromeVisible] also hides the system status/navigation bars via
 * [WindowInsetsControllerCompat] for a genuinely immersive fullscreen (a swipe from the edge
 * temporarily reveals them again, same as any other fullscreen video player) - without this, the
 * system bars would keep drawing on top of the media even with our own overlay hidden. They're
 * restored as soon as the chrome is toggled back on, and unconditionally when leaving this screen
 * so the rest of the app isn't left in an immersive state.
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
    var isChromeVisible by remember { mutableStateOf(true) }

    val view = LocalView.current
    DisposableEffect(isChromeVisible) {
        val window = view.context.findActivity()?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isChromeVisible) {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            Box(modifier = Modifier.fillMaxSize()) {
                when (item.mediaType) {
                    MediaType.IMAGE -> ZoomableAsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        onTap = { isChromeVisible = !isChromeVisible },
                    )

                    MediaType.VIDEO -> VideoPlayer(
                        uri = item.uri,
                        isActive = pagerState.currentPage == page,
                        isChromeVisible = isChromeVisible,
                        onTap = { isChromeVisible = !isChromeVisible },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isChromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                ),
            )
        }
    }
}


