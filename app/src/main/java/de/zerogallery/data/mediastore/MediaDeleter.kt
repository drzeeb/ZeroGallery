package de.zerogallery.data.mediastore

import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import de.zerogallery.domain.model.MediaItem
import java.io.File

/**
 * Deletes photos/videos, split by how each item was found:
 *
 * - Regular `MediaStore` items (`content://`): on API 30+, actually deleting them normally
 *   requires the user's explicit confirmation via a system dialog (scoped storage) for anything
 *   this app doesn't itself own - [createDeleteRequest] returns the [IntentSender] the caller must
 *   launch (via `ActivityResultContracts.StartIntentSenderForResult`) to show that confirmation.
 *   *However*, an app holding the "All files access" special permission
 *   (`MANAGE_EXTERNAL_STORAGE`, see [de.zerogallery.ui.permission.AllFilesAccessPermission]) is
 *   exempt from that restriction entirely - [needsSystemConfirmation] returns `false` in that case
 *   (and below API 30, where scoped storage doesn't exist yet), and [delete] can then remove them
 *   outright with no dialog at all.
 * - [de.zerogallery.data.filesystem.HiddenMediaScanner] items (`file://`): never indexed by
 *   `MediaStore` in the first place, so there's no scoped-storage delete-request flow for them at
 *   all - [delete] just deletes the underlying file directly, the same way that scanner reads
 *   them (both require "All files access" to already be granted).
 */
object MediaDeleter {

    /**
     * Whether deleting a regular `content://` `MediaStore` item still needs the system's
     * scoped-storage confirmation dialog (see [createDeleteRequest]) - `false` below API 30
     * (scoped storage doesn't apply yet) or once "All files access" has been granted (which
     * exempts this app from that restriction), in which case [delete] can remove it directly
     * instead, with no dialog at all.
     */
    fun needsSystemConfirmation(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()

    /**
     * Returns the confirmation [IntentSender] for deleting [uris] (must all be `content://`
     * `MediaStore` uris), or `null` if none is actually needed (see [needsSystemConfirmation]) or
     * if [uris] is empty. The explicit SDK check duplicates part of [needsSystemConfirmation]'s
     * condition - lint's `NewApi` check can't follow that guarantee across the function call, so
     * it's spelled out again here directly guarding the API 30+ call.
     */
    fun createDeleteRequest(context: Context, uris: List<Uri>): IntentSender? {
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !needsSystemConfirmation(context)) {
            return null
        }
        return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
    }

    /**
     * Deletes [items] directly, with no confirmation dialog - appropriate for
     * [de.zerogallery.data.filesystem.HiddenMediaScanner]'s `file://` items on any API level, and
     * for regular `content://` items whenever [needsSystemConfirmation] is `false` (see
     * [createDeleteRequest] for when it's `true` instead). Returns how many were actually deleted;
     * items that fail (already gone, permission denied, ...) are skipped rather than aborting the
     * whole batch.
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

