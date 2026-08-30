package de.zerogallery.data.mediastore

import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import de.zerogallery.domain.model.MediaItem
import java.io.File

/**
 * Deletes photos/videos, split by how each item was found:
 *
 * - Regular `MediaStore` items (`content://`): on API 30+, actually deleting them requires the
 *   user's explicit confirmation via a system dialog (scoped storage) for anything this app
 *   doesn't itself own - [createDeleteRequest] returns the [IntentSender] the caller must launch
 *   (via `ActivityResultContracts.StartIntentSenderForResult`) to show that confirmation. Below
 *   API 30, [delete] can remove them outright, since this app already holds the broad legacy
 *   storage permission there.
 * - [de.zerogallery.data.filesystem.HiddenMediaScanner] items (`file://`): never indexed by
 *   `MediaStore` in the first place, so there's no scoped-storage delete-request flow for them at
 *   all - [delete] just deletes the underlying file directly, the same way that scanner reads
 *   them (both require "All files access" to already be granted).
 */
object MediaDeleter {

    /**
     * Returns the confirmation [IntentSender] for deleting [uris] (must all be `content://`
     * `MediaStore` uris), or `null` below API 30 (nothing to confirm - see [delete] instead) or
     * if [uris] is empty.
     */
    fun createDeleteRequest(context: Context, uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) return null
        return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
    }

    /**
     * Deletes [items] directly, with no confirmation dialog - appropriate for
     * [de.zerogallery.data.filesystem.HiddenMediaScanner]'s `file://` items on any API level, and
     * for regular `content://` items below API 30 (see [createDeleteRequest] for API 30+, where
     * `content://` items need that confirmation flow instead). Returns how many were actually
     * deleted; items that fail (already gone, permission denied, ...) are skipped rather than
     * aborting the whole batch.
     */
    fun delete(context: Context, items: List<MediaItem>): Int {
        var deletedCount = 0
        for (item in items) {
            val deleted = if (item.uri.scheme == ContentResolver.SCHEME_FILE) {
                item.uri.path?.let { File(it).delete() } ?: false
            } else {
                try {
                    context.contentResolver.delete(item.uri, null, null) > 0
                } catch (e: Exception) {
                    false
                }
            }
            if (deleted) deletedCount++
        }
        return deletedCount
    }
}

