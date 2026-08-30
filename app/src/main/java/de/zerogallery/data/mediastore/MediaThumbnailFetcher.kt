package de.zerogallery.data.mediastore

import android.content.ContentResolver
import android.graphics.Bitmap
import android.os.Build
import android.util.Size as AndroidSize
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Dimension
import coil3.toAndroidUri

/**
 * Cap on the requested pixel size this fetcher will handle - deliberately small, matching
 * [de.zerogallery.ui.gallery.MediaGrid]'s thumbnail tiles rather than a full-screen photo. Above
 * this, [MediaThumbnailFetcher.Factory.create] returns `null` so the request falls through to
 * Coil's normal, full-resolution `ContentUriFetcher` (`+VideoFrameDecoder` for videos) - needed
 * for [de.zerogallery.ui.detail.MediaDetailScreen], where pinch-zooming into a photo/video frame
 * this small would look visibly blurry.
 */
private const val MaxThumbnailSizePx = 512

/**
 * Loads grid thumbnails via [ContentResolver.loadThumbnail] (API 29+) instead of Coil's default
 * behaviour of opening and decoding the *entire original* photo/video file for every single tile.
 *
 * `loadThumbnail` reuses a small, already-generated bitmap that Android itself maintains for every
 * `MediaStore` item (the same one the system's own Photos/Files apps use) - dramatically cheaper
 * than decoding a full 12+ MP camera photo, and *especially* cheaper than the previous per-tile
 * video path, which had to decode an actual video frame (`VideoFrameDecoder`, backed by
 * `MediaMetadataRetriever`) from the complete video file just to show one small thumbnail. This
 * was one of the bigger contributors to fast-scroll dragging feeling janky, since every newly
 * revealed row of tiles had to pay that full decode cost from scratch.
 *
 * This only ever affects small, grid-sized requests (see [MaxThumbnailSizePx]) for regular
 * `content://` `MediaStore` items - it deliberately does *not* apply to
 * [de.zerogallery.data.filesystem.HiddenMediaScanner]'s `file://` items (never registered with
 * `MediaStore`, so there's no OS thumbnail to reuse for them) or to full-size detail-viewer
 * requests, both of which continue to fall through to Coil's normal fetchers/decoders unchanged.
 *
 * Since Android already generates and caches these thumbnails itself (regardless of whether this
 * app reads them), routing grid tiles through them is a pure win with no extra on-disk storage
 * cost - unlike, say, adding an *app-level* disk cache of decoded thumbnails, which would just
 * duplicate space already spent by the OS for no real speed benefit on local files.
 */
internal class MediaThumbnailFetcher(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val size: AndroidSize,
) : Fetcher {

    @RequiresApi(Build.VERSION_CODES.Q)
    override suspend fun fetch(): FetchResult? {
        val bitmap: Bitmap = try {
            contentResolver.loadThumbnail(uri.toAndroidUri(), size, null)
        } catch (_: Exception) {
            return null
        }
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<Uri> {

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            if (data.scheme != ContentResolver.SCHEME_CONTENT) return null
            val width = (options.size.width as? Dimension.Pixels)?.px ?: return null
            val height = (options.size.height as? Dimension.Pixels)?.px ?: return null
            if (width > MaxThumbnailSizePx || height > MaxThumbnailSizePx) return null
            return MediaThumbnailFetcher(
                contentResolver = options.context.contentResolver,
                uri = data,
                size = AndroidSize(width, height),
            )
        }
    }
}




