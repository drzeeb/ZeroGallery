package de.zerogallery.data.mediastore

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaSource

/**
 * Deletes photos/videos, split by how each item was found ([MediaItem.source]):
 *
 * - [MediaSource.MEDIA_STORE]: on API 30+, actually deleting one of these normally requires the
 *   user's explicit confirmation via a system dialog (scoped storage) for anything this app
 *   doesn't itself own - [createDeleteRequest] returns the [IntentSender] the caller must launch
 *   (via `ActivityResultContracts.StartIntentSenderForResult`) to show that confirmation. Below
 *   API 30, where scoped storage doesn't apply yet, [delete] can remove it directly instead, with
 *   no dialog at all.
 * - [MediaSource.HIDDEN_FOLDER] (see [de.zerogallery.data.filesystem.HiddenMediaScanner]): never
 *   indexed by `MediaStore` in the first place, so there's no scoped-storage delete-request flow
 *   for it at all - [delete] instead deletes the underlying Storage-Access-Framework document
 *   directly, the same way that scanner reads it (both rely on
 *   [de.zerogallery.data.filesystem.HiddenFolderAccess]'s persisted, user-granted permission).
 */
object MediaDeleter {

    /**
     * Whether deleting a [MediaSource.MEDIA_STORE] item still needs the system's scoped-storage
     * confirmation dialog (see [createDeleteRequest]) - always `false` below API 30, where scoped
     * storage doesn't apply yet, in which case [delete] can remove it directly instead, with no
     * dialog at all.
     */
    fun needsSystemConfirmation(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

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
     * [MediaSource.HIDDEN_FOLDER] items on any API level, and for [MediaSource.MEDIA_STORE] items
     * whenever [needsSystemConfirmation] is `false` (see [createDeleteRequest] for when it's
     * `true` instead). Returns how many were actually deleted; items that fail (already gone,
     * permission denied, ...) are skipped rather than aborting the whole batch.
     */
    fun delete(context: Context, items: List<MediaItem>): Int {
        var deletedCount = 0
        for (item in items) {
            val deleted = try {
                when (item.source) {
                    MediaSource.HIDDEN_FOLDER ->
                        DocumentsContract.deleteDocument(context.contentResolver, item.uri)

                    MediaSource.MEDIA_STORE ->
                        context.contentResolver.delete(item.uri, null, null) > 0
                }
            } catch (e: Exception) {
                false
            }
            if (deleted) deletedCount++
        }
        return deletedCount
    }
}

