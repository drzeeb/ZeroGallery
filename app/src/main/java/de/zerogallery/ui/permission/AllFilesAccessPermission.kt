package de.zerogallery.ui.permission

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.net.toUri

/**
 * Wraps Android 11+'s "All files access" special permission (`MANAGE_EXTERNAL_STORAGE`), needed
 * only for the folder view's "show hidden folders" toggle (see
 * [de.zerogallery.data.filesystem.HiddenMediaScanner]) - the regular, per-media-type permissions
 * in [MediaPermissions] are architecturally incapable of ever surfacing content the platform's
 * media scanner skipped (dot-prefixed directories), since it was never indexed into `MediaStore`
 * in the first place. Unlike a normal runtime permission, this can't be requested via a simple
 * dialog - the user has to flip a switch on a dedicated system Settings screen, which is why
 * there's a [requestIntent] to launch rather than an `ActivityResultContracts.RequestPermission`.
 */
object AllFilesAccessPermission {

    /** Always `true` below API 30: scoped storage's "all files" restriction doesn't exist yet there. */
    fun isGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /**
     * An intent opening the system Settings screen to grant this app "All files access". Falls
     * back to the general, app-list variant of that screen if the per-app one isn't resolvable,
     * which some OEM-modified Settings apps omit. Only ever called while [isGranted] is `false`,
     * which - combined with the @RequiresApi below - implies API 30+ at the call site.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun requestIntent(context: Context): Intent {
        val perAppIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData("package:${context.packageName}".toUri())
        return if (perAppIntent.resolveActivity(context.packageManager) != null) {
            perAppIntent
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }
}

