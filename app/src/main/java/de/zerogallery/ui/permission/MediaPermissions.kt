package de.zerogallery.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime permissions required to read photos and videos from the device's MediaStore.
 *
 * Uses the granular per-media-type permissions introduced in API 33 (Android 13), falling back
 * to the legacy, coarser storage permission on older platform versions.
 */
object MediaPermissions {

    val required: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /**
     * Whether [required] has already been granted, e.g. from a previous app launch or because the
     * user granted it from the system Settings screen. Used so the app remembers a prior grant
     * instead of always showing the permission rationale screen again on every cold start.
     */
    fun hasAll(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

