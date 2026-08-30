package de.zerogallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import de.zerogallery.data.mediastore.MediaThumbnailFetcher

/**
 * Configures the app-wide Coil [ImageLoader] with a video frame decoder so that thumbnails for
 * both images and videos can be requested through the exact same `AsyncImage(model = uri)` call,
 * without any per-item branching in the UI layer.
 *
 * [MediaThumbnailFetcher] is registered *before* the default fetchers/[VideoFrameDecoder]: for
 * small, grid-sized requests it reuses Android's own cached `MediaStore` thumbnails instead of
 * decoding the complete original photo/video file, which is both faster and lighter on memory -
 * see its class doc for details. It only ever applies to those small requests, so full-resolution
 * decoding (still via [VideoFrameDecoder] for videos) continues to back the detail viewer.
 */
class ZeroGalleryApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(MediaThumbnailFetcher.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}

