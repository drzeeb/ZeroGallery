package de.zerogallery.testutil

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Points [Dispatchers.Main] (used internally by `viewModelScope`) at a [TestDispatcher] for the
 * duration of a test, so a [androidx.lifecycle.ViewModel] under test can be driven deterministically
 * instead of needing a real Android main-thread looper (unavailable in a plain JVM unit test).
 *
 * Exposes that same [testDispatcher] so it can *also* be passed into `runTest(testDispatcher) { }`:
 * without sharing the identical dispatcher/scheduler, `viewModelScope`'s internal sharing coroutine
 * (driving a `stateIn`'d `StateFlow`, e.g. [de.zerogallery.ui.gallery.GalleryViewModel.uiState])
 * and the test body's own collector would run on two independent, uncoordinated virtual-time
 * schedulers - the collector's subscription would just sit queued and never actually resume the
 * shared flow, since nothing would ever tell *that* scheduler to run it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}


