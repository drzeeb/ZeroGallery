package de.zerogallery.ui.gallery

import de.zerogallery.domain.model.MediaItem

/** UI state for the gallery screen, driven by [GalleryViewModel]. */
sealed interface GalleryUiState {

    /** Initial state, before the permission result / first MediaStore query is known. */
    data object Loading : GalleryUiState

    /** The user hasn't granted the required media permissions yet. */
    data object PermissionRequired : GalleryUiState

    /** Permission granted, but the device has no photos or videos. */
    data object Empty : GalleryUiState

    /** Permission granted and at least one media item was found. */
    data class Content(val items: List<MediaItem>) : GalleryUiState
}

