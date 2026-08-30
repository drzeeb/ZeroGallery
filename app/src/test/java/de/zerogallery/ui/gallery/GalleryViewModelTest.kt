package de.zerogallery.ui.gallery

import android.net.Uri
import de.zerogallery.domain.model.MediaItem
import de.zerogallery.domain.model.MediaType
import de.zerogallery.domain.repository.MediaRepository
import de.zerogallery.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class GalleryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeUri: Uri = mock(Uri::class.java)

    private fun item(id: Long) = MediaItem(
        id = id,
        uri = fakeUri,
        displayName = "item-$id",
        mediaType = MediaType.IMAGE,
        dateAddedSeconds = 0,
        sizeBytes = 0,
    )

    /** Counts subscriptions so [GalleryViewModel.refresh] re-subscribing (rather than just re-emitting) can be asserted on. */
    private class FakeMediaRepository(initialItems: List<MediaItem> = emptyList()) : MediaRepository {
        val mediaFlow = MutableStateFlow(initialItems)
        var observeMediaCallCount = 0
            private set

        override fun observeMedia(): Flow<List<MediaItem>> {
            observeMediaCallCount++
            return mediaFlow
        }
    }

    @Test
    fun `uiState starts as Loading before any permission result`() {
        val viewModel = GalleryViewModel(FakeMediaRepository())

        assertEquals(GalleryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `uiState becomes PermissionRequired when permission is denied`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = GalleryViewModel(FakeMediaRepository())
        val collectJob = viewModel.uiState.onEach { }.launchIn(this)

        viewModel.onPermissionResult(false)

        assertEquals(GalleryUiState.PermissionRequired, viewModel.uiState.value)
        collectJob.cancel()
    }

    @Test
    fun `uiState becomes Empty when permission granted and the repository has no items`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = GalleryViewModel(FakeMediaRepository(emptyList()))
        val collectJob = viewModel.uiState.onEach { }.launchIn(this)

        viewModel.onPermissionResult(true)

        assertEquals(GalleryUiState.Empty, viewModel.uiState.value)
        collectJob.cancel()
    }

    @Test
    fun `uiState becomes Content with the repository's items once permission is granted`() = runTest(mainDispatcherRule.testDispatcher) {
        val items = listOf(item(1), item(2))
        val viewModel = GalleryViewModel(FakeMediaRepository(items))
        val collectJob = viewModel.uiState.onEach { }.launchIn(this)

        viewModel.onPermissionResult(true)

        assertEquals(GalleryUiState.Content(items), viewModel.uiState.value)
        collectJob.cancel()
    }

    @Test
    fun `uiState falls back to PermissionRequired again once permission is revoked`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = GalleryViewModel(FakeMediaRepository(listOf(item(1))))
        val collectJob = viewModel.uiState.onEach { }.launchIn(this)

        viewModel.onPermissionResult(true)
        viewModel.onPermissionResult(false)

        assertEquals(GalleryUiState.PermissionRequired, viewModel.uiState.value)
        collectJob.cancel()
    }

    @Test
    fun `refresh re-subscribes to the repository even without a permission change`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeMediaRepository(listOf(item(1)))
        val viewModel = GalleryViewModel(repository)
        val collectJob = viewModel.uiState.onEach { }.launchIn(this)
        viewModel.onPermissionResult(true)
        val subscriptionsAfterGrant = repository.observeMediaCallCount

        viewModel.refresh()

        assertTrue(repository.observeMediaCallCount > subscriptionsAfterGrant)
        collectJob.cancel()
    }

    @Test
    fun `Factory creates a working GalleryViewModel backed by the given repository`() {
        val repository = FakeMediaRepository()

        val viewModel = GalleryViewModel.Factory(repository).create(GalleryViewModel::class.java)

        assertEquals(GalleryUiState.Loading, viewModel.uiState.value)
    }
}



