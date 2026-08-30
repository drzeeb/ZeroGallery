package de.zerogallery.domain.model

import android.net.Uri

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
)

