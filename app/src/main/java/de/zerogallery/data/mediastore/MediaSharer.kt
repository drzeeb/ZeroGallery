package de.zerogallery.data.mediastore

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaType
import java.io.File

/**
 * Builds an `ACTION_SEND`/`ACTION_SEND_MULTIPLE` [Intent] to share one or more [MediaItem]s with
 * another app, e.g. via `Intent.createChooser`.
 *
 * Regular `MediaStore` items' `content://` uris are already shareable as-is. Items found by
 * [de.zerogallery.data.filesystem.HiddenMediaScanner] are plain `file://` uris instead - handing
 * one of those to another app directly would crash with a `FileUriExposedException` on API 24+
 * (`StrictMode` forbids exposing raw file:// paths outside the app), so [sharableUri] first
 * rewrites it to a `content://` uri backed by this app's [FileProvider] (see
 * `res/xml/file_paths.xml` and the `<provider>` entry in `AndroidManifest.xml`), which grants the
 * receiving app temporary, revocable read access instead.
 */
object MediaSharer {

    fun shareIntent(context: Context, items: List<MediaItem>): Intent {
        val uris = items.map { sharableUri(context, it) }
        val mimeType = commonMimeType(items)
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList(uris),
            )
        }
        return intent
            .setType(mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun sharableUri(context: Context, item: MediaItem): Uri {
        if (item.uri.scheme != ContentResolver.SCHEME_FILE) return item.uri
        val path = item.uri.path ?: return item.uri
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
    }

    /** An image or video MIME type if every item shares it, or a wildcard for a mixed batch. */
    internal fun commonMimeType(items: List<MediaItem>): String = when {
        items.all { it.mediaType == MediaType.IMAGE } -> "image/*"
        items.all { it.mediaType == MediaType.VIDEO } -> "video/*"
        else -> "*/*"
    }
}

