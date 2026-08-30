package de.zerogallery.data.filesystem

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaSource
import de.zerogallery.domain.model.MediaType

/**
 * Finds photos/videos that `MediaStore` is architecturally incapable of ever indexing: the
 * platform's media scanner explicitly skips any directory whose name starts with a dot (or that
 * contains a `.nomedia` file) - exactly the convention several messaging/"vault" apps rely on to
 * hide their media from gallery apps like this one. There is no `MediaStore` query that can
 * surface this content; it was simply never scanned into that database in the first place.
 *
 * Reads via the Storage Access Framework instead of raw filesystem access: [HiddenFolderAccess]
 * holds a persisted, user-granted permission scoped to exactly the one folder they picked via the
 * system's folder picker (`ACTION_OPEN_DOCUMENT_TREE`), and [DocumentFile] walks it recursively
 * from there. This deliberately does *not* use the broad "All files access" special permission
 * (`MANAGE_EXTERNAL_STORAGE`) - that grants access to the *entire* device's storage for the sake
 * of this one narrow feature, which is exactly the kind of permission Google Play's review
 * process explicitly polices for apps like a gallery whose core functionality doesn't otherwise
 * need it. [scan] simply returns an empty list until the user has picked a folder (see
 * [HiddenFolderAccess.isConfigured]), rather than throwing, so callers can unconditionally merge
 * its result into their regular `MediaStore`-backed list.
 *
 * Unlike the old whole-device-storage walk, only the single folder the user explicitly picked
 * (and its subfolders) is ever read - if they use more than one hidden-folder app, each one needs
 * to be picked individually. [DocumentFile] traversal is also considerably slower than raw
 * [java.io.File] access (each listing is its own cross-process content query), which is an
 * accepted trade-off for not needing a device-wide permission for it.
 */
object HiddenMediaScanner {

    private val imageExtensions =
        setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp")
    private val videoExtensions =
        setOf("mp4", "mkv", "webm", "3gp", "3gpp", "mov", "avi", "m4v")

    fun scan(context: Context): List<MediaItem> {
        val treeUri = HiddenFolderAccess.treeUri(context) ?: return emptyList()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val bucketName = root.name ?: return emptyList()
        return scanFolder(root, bucketName)
    }

    private fun scanFolder(folder: DocumentFile, bucketName: String): List<MediaItem> {
        return folder.listFiles().flatMap { child ->
            if (child.isDirectory) {
                scanFolder(child, bucketName)
            } else {
                listOfNotNull(mediaItemOrNull(child, bucketName))
            }
        }
    }

    private fun mediaItemOrNull(file: DocumentFile, bucketName: String): MediaItem? {
        val name = file.name ?: return null
        val mediaType = when (name.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            in imageExtensions -> MediaType.IMAGE
            in videoExtensions -> MediaType.VIDEO
            else -> return null
        }
        return MediaItem(
            // Distinguished from regular MediaStore ids (small, sequential autoincrement values)
            // by always setting the highest bit, so a hash collision between the two sources
            // would require an astronomically unlikely coincidence rather than just a common one.
            id = (1L shl 62) or (file.uri.toString().hashCode().toLong() and 0x3FFFFFFFL),
            uri = file.uri,
            displayName = name,
            mediaType = mediaType,
            dateAddedSeconds = file.lastModified() / 1000,
            sizeBytes = file.length(),
            bucketName = bucketName,
            source = MediaSource.HIDDEN_FOLDER,
        )
    }
}

