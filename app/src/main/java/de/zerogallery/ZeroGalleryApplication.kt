package de.zerogallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder

/**
 * Configures the app-wide Coil [ImageLoader] with a video frame decoder so that thumbnails for
 * both images and videos can be requested through the exact same `AsyncImage(model = uri)` call,
 * without any per-item branching in the UI layer.
 */
class ZeroGalleryApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}

