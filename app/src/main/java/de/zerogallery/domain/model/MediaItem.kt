package de.zerogallery.domain.model

import android.net.Uri

/**
 * Where a [MediaItem] was found - see [MediaItem.source]. Deliberately an explicit field rather
 * than callers sniffing [MediaItem.uri]'s scheme/authority: both [MediaSource.MEDIA_STORE] and
 * [MediaSource.HIDDEN_FOLDER] items are plain `content://` uris (the latter via the Storage
 * Access Framework, see [de.zerogallery.data.filesystem.HiddenMediaScanner]), so the scheme alone
 * can no longer tell them apart the way it could back when hidden items were raw `file://` paths.
 */
enum class MediaSource {
    /** A regular item, queried directly from the platform's `MediaStore`. */
    MEDIA_STORE,

    /**
     * Found by [de.zerogallery.data.filesystem.HiddenMediaScanner] inside a user-picked, dot-
     * prefixed "hidden" folder that `MediaStore` never indexes at all - see its class doc.
     */
    HIDDEN_FOLDER,
}

/**
 * A single photo or video, as read from the device's local MediaStore.
 *
 * This is a pure domain model: it never leaks `Cursor`/`ContentResolver` details to callers.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mediaType: MediaType,
    /** Unix epoch, in seconds, matching [android.provider.MediaStore.MediaColumns.DATE_ADDED]. */
    val dateAddedSeconds: Long,
    val sizeBytes: Long,
    /** Duration in milliseconds. Always `null` for [MediaType.IMAGE]. */
    val durationMillis: Long? = null,
    /**
     * The display name of the folder this item lives in (`MediaStore`'s
     * `BUCKET_DISPLAY_NAME`, e.g. "Camera", "Screenshots", "WhatsApp Images"). Blank if unknown.
     */
    val bucketName: String = "",
    val source: MediaSource = MediaSource.MEDIA_STORE,
)


