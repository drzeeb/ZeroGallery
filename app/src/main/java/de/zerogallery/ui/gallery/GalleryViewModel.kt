package de.zerogallery.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.zerogallery.domain.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the permission state and exposes the resulting [GalleryUiState] as a single source of
 * truth for the gallery screen. Media is only queried from [MediaRepository] once permission has
 * been granted; the screen falls back to [GalleryUiState.PermissionRequired] otherwise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(
    private val repository: MediaRepository,
) : ViewModel() {

    private val hasPermission = MutableStateFlow(false)

    // Bumped by refresh() to force re-subscribing to repository.observeMedia() even when
    // hasPermission's value hasn't changed - needed after the user grants "All files access" in
    // system Settings and returns to the app, since that's a separate special permission
    // MediaStoreRepository can't observe changes to the way it observes MediaStore itself.
    private val refreshSignal = MutableStateFlow(0)

    val uiState: StateFlow<GalleryUiState> = combine(hasPermission, refreshSignal) { granted, _ -> granted }
        .flatMapLatest { granted ->
            if (!granted) {
                flowOf(GalleryUiState.PermissionRequired)
            } else {
                repository.observeMedia().map { items ->
                    if (items.isEmpty()) GalleryUiState.Empty else GalleryUiState.Content(items)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = GalleryUiState.Loading,
        )

    /** Called after the runtime permission request completes. */
    fun onPermissionResult(granted: Boolean) {
        hasPermission.value = granted
    }

    /** Forces media to be re-queried, e.g. after returning from the "All files access" Settings screen. */
    fun refresh() {
        refreshSignal.value++
    }

    class Factory(private val repository: MediaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GalleryViewModel(repository) as T
        }
    }
}

