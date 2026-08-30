package de.zerogallery.data.filesystem

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaType
import java.io.File

/**
 * Finds photos/videos that `MediaStore` is architecturally incapable of ever indexing: the
 * platform's media scanner explicitly skips any directory whose name starts with a dot (or that
 * contains a `.nomedia` file) - exactly the convention several messaging/"vault" apps rely on to
 * hide their media from gallery apps like this one. There is no `MediaStore` query that can
 * surface this content; it was simply never scanned into that database in the first place. The
 * only way to find it is reading the raw filesystem directly, which in turn requires the
 * "All files access" (`MANAGE_EXTERNAL_STORAGE`) special permission on API 30+ (see
 * [de.zerogallery.ui.permission.AllFilesAccessPermission]) - below that API level, or without the
 * permission granted, [scan] simply returns an empty list rather than throwing, so callers can
 * unconditionally merge its result into their regular `MediaStore`-backed list.
 *
 * Only *top-level* dot-directories directly under each storage volume's root are treated as
 * hidden-folder entry points - matching how vault apps commonly place them (e.g.
 * "/storage/emulated/0/.MyVault") - and their contents are then walked recursively so a nested
 * "Photos" subfolder inside one is still included, without treating every dot-prefixed directory
 * anywhere on the device (which would include noisy, irrelevant system/app-cache directories) as
 * its own separate entry point.
 */
object HiddenMediaScanner {

    private val imageExtensions =
        setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp")
    private val videoExtensions =
        setOf("mp4", "mkv", "webm", "3gp", "3gpp", "mov", "avi", "m4v")

    fun scan(context: Context): List<MediaItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        if (!Environment.isExternalStorageManager()) return emptyList()
        return volumeRoots(context).flatMap(::scanVolumeRoot)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun volumeRoots(context: Context): List<File> {
        val storageManager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return storageManager.storageVolumes.mapNotNull { it.directory }
    }

    private fun scanVolumeRoot(root: File): List<MediaItem> {
        val hiddenFolders = root.listFiles { file -> file.isDirectory && file.name.startsWith(".") }
            ?: return emptyList()
        return hiddenFolders.flatMap { folder -> scanFolder(folder, bucketName = folder.name) }
    }

    private fun scanFolder(folder: File, bucketName: String): List<MediaItem> {
        val children = folder.listFiles() ?: return emptyList()
        return children.flatMap { child ->
            if (child.isDirectory) {
                scanFolder(child, bucketName)
            } else {
                listOfNotNull(mediaItemOrNull(child, bucketName))
            }
        }
    }

    private fun mediaItemOrNull(file: File, bucketName: String): MediaItem? {
        val mediaType = when (file.extension.lowercase()) {
            in imageExtensions -> MediaType.IMAGE
            in videoExtensions -> MediaType.VIDEO
            else -> return null
        }
        return MediaItem(
            // Distinguished from regular MediaStore ids (small, sequential autoincrement values)
            // by always setting the highest bit, so a hash collision between the two sources
            // would require an astronomically unlikely coincidence rather than just a common one.
            id = (1L shl 62) or (file.absolutePath.hashCode().toLong() and 0x3FFFFFFFL),
            uri = Uri.fromFile(file),
            displayName = file.name,
            mediaType = mediaType,
            dateAddedSeconds = file.lastModified() / 1000,
            sizeBytes = file.length(),
            bucketName = bucketName,
        )
    }
}

