package de.zerogallery.ui.permission

import android.Manifest
import android.os.Build

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
}

