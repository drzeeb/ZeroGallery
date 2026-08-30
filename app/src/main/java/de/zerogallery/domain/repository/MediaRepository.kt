package de.zerogallery.domain.repository

import de.zerogallery.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

/** Read-only access to the device's local media library (photos & videos). */
interface MediaRepository {

    /**
     * Emits the full, sorted (newest first) list of media items and re-emits automatically
     * whenever the underlying MediaStore collections change (new photo taken, item deleted, ...).
     */
    fun observeMedia(): Flow<List<MediaItem>>
}

