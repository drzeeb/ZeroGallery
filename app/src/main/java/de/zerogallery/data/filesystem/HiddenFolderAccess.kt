package de.zerogallery.data.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

private const val PREFS_NAME = "hidden_folder_access"
private const val KEY_TREE_URI = "tree_uri"

/**
 * Manages the user-picked "hidden folder" tree uri backing the folder view's "show hidden
 * folders" toggle (see [HiddenMediaScanner]).
 *
 * `MediaStore`'s media scanner never indexes dot-prefixed directories at all (the convention
 * several vault/messaging apps use to hide their media from gallery apps like this one), so the
 * only way to ever read one is raw filesystem/document access outside of `MediaStore`. This used
 * to be done via the "All files access" special permission (`MANAGE_EXTERNAL_STORAGE`) - broad,
 * whole-device-storage access for the sake of one narrow feature, which is exactly the kind of
 * permission Google Play's review explicitly polices for apps (like a gallery) whose core
 * functionality doesn't otherwise need it.
 *
 * The Storage Access Framework's [Intent.ACTION_OPEN_DOCUMENT_TREE] instead lets the user pick
 * *just* the one hidden folder they want surfaced, via the system's own folder picker - Android
 * then grants this app a *persistable* permission scoped to only that folder (and its contents),
 * with no special manifest permission declared at all. [persist] takes and stores that grant (as a
 * plain string uri in `SharedPreferences`, similar to [de.zerogallery.ui.gallery.GallerySettings])
 * so it survives app restarts exactly like a normal runtime permission would; [treeUri] hands it
 * back to [HiddenMediaScanner] to actually read from.
 */
object HiddenFolderAccess {


    fun isConfigured(context: Context): Boolean = treeUri(context) != null

    fun treeUri(context: Context): Uri? =
        prefs(context).getString(KEY_TREE_URI, null)?.toUri()

    /**
     * Persists [treeUri] (as returned by the `OpenDocumentTree` picker) so it keeps working across
     * app restarts, replacing any previously picked folder - [Context.getContentResolver]'s
     * `takePersistableUriPermission` is what actually makes the grant durable; without it, it would
     * only last until the app process is killed, same as a regular, non-persistable uri grant.
     */
    fun persist(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs(context).edit { putString(KEY_TREE_URI, treeUri.toString()) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

