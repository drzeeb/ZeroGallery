package de.zerogallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import de.zerogallery.data.mediastore.MediaStoreRepository
import de.zerogallery.ui.gallery.GalleryRoute
import de.zerogallery.ui.gallery.GalleryViewModel
import de.zerogallery.ui.theme.ZeroGalleryTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels {
        GalleryViewModel.Factory(MediaStoreRepository(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ZeroGalleryTheme {
                GalleryRoute(viewModel = viewModel)
            }
        }
    }
}

