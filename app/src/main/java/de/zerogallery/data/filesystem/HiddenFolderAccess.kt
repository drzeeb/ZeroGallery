package de.zerogallery.data.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

private const val PREFS_NAME = "hidden_folder_access"
private const val KEY_TREE_URIS = "tree_uris"
// Read (and immediately migrated away from) for anyone upgrading from before multiple hidden
// folders were supported, when this held a single plain string uri instead of KEY_TREE_URIS's set.
private const val KEY_TREE_URI_LEGACY = "tree_uri"

/**
 * Manages the user-picked "hidden folder" tree uris backing the folder view's "show hidden
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
 * *just* the folder(s) they want surfaced, one at a time via the system's own folder picker -
 * Android then grants this app a *persistable* permission scoped to only that folder (and its
 * contents), with no special manifest permission declared at all. [add] takes and stores each
 * grant (as a set of plain string uris in `SharedPreferences`, similar to
 * [de.zerogallery.ui.gallery.GallerySettings]) so they survive app restarts exactly like a normal
 * runtime permission would; [treeUris] hands them all back to [HiddenMediaScanner] to actually
 * read from.
 */
object HiddenFolderAccess {

    fun isConfigured(context: Context): Boolean = treeUris(context).isNotEmpty()

    fun treeUris(context: Context): Set<Uri> {
        migrateLegacyIfNeeded(context)
        return prefs(context).getStringSet(KEY_TREE_URIS, null).orEmpty().map { it.toUri() }.toSet()
    }

    /**
     * Persists [treeUri] (as returned by the `OpenDocumentTree` picker) so it keeps working across
     * app restarts, in addition to any previously picked folder(s) - [Context.getContentResolver]'s
     * `takePersistableUriPermission` is what actually makes the grant durable; without it, it would
     * only last until the app process is killed, same as a regular, non-persistable uri grant.
     */
    fun add(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val updated = treeUris(context).map { it.toString() }.toMutableSet().apply { add(treeUri.toString()) }
        prefs(context).edit { putStringSet(KEY_TREE_URIS, updated) }
    }

    /**
     * Drops one previously picked folder, e.g. the user explicitly removing it again in the
     * "Manage hidden folders" dialog, or [HiddenMediaScanner] discovering its permission grant no
     * longer actually works (revoked by the user in system settings, or the folder itself deleted)
     * - either way, the next refresh then behaves as if that folder specifically was never picked
     * (see [HiddenMediaScanner.scan]) instead of silently failing forever, while any *other* picked
     * folders keep working as before. Auto Backup/device transfer never restore the actual grants
     * these uri strings refer to (see `backup_rules.xml`/`data_extraction_rules.xml`, which
     * explicitly exclude these prefs for the same reason), so a fresh install/new device is never
     * in this broken state to begin with.
     */
    fun remove(context: Context, treeUri: Uri) {
        val updated = treeUris(context).map { it.toString() }.toMutableSet()
        if (!updated.remove(treeUri.toString())) return
        prefs(context).edit { putStringSet(KEY_TREE_URIS, updated) }
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    private fun migrateLegacyIfNeeded(context: Context) {
        val legacy = prefs(context).getString(KEY_TREE_URI_LEGACY, null) ?: return
        prefs(context).edit {
            putStringSet(KEY_TREE_URIS, setOf(legacy))
            remove(KEY_TREE_URI_LEGACY)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

