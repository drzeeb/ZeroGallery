package de.zerogallery.data.mediastore

import android.content.Context
import android.content.Intent
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaType

/**
 * Builds an `ACTION_SEND`/`ACTION_SEND_MULTIPLE` [Intent] to share one or more [MediaItem]s with
 * another app, e.g. via `Intent.createChooser`.
 *
 * Every [MediaItem.uri] is already a plain, directly shareable `content://` uri - regular
 * `MediaStore` items' as-is, and [de.zerogallery.data.filesystem.HiddenMediaScanner]'s items via
 * the Storage Access Framework document tree the user picked (see
 * [de.zerogallery.data.filesystem.HiddenFolderAccess]) - so no rewriting (e.g. via `FileProvider`,
 * needed back when hidden items were raw `file://` paths) is needed for either.
 */
object MediaSharer {

    fun shareIntent(context: Context, items: List<MediaItem>): Intent {
        val uris = items.map { it.uri }
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


    /** An image or video MIME type if every item shares it, or a wildcard for a mixed batch. */
    internal fun commonMimeType(items: List<MediaItem>): String = when {
        items.all { it.mediaType == MediaType.IMAGE } -> "image/*"
        items.all { it.mediaType == MediaType.VIDEO } -> "video/*"
        else -> "*/*"
    }
}

