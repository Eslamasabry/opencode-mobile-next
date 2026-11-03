package io.github.eslamasabry.oc.feature.about

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.about.presentation.InMemoryTipsStore
import io.github.eslamasabry.oc.feature.about.presentation.TipKind
import io.github.eslamasabry.oc.feature.about.presentation.TipsRegistry
import io.github.eslamasabry.oc.feature.about.presentation.TipsStore
import io.github.eslamasabry.oc.feature.about.presentation.TipsViewModel
import io.github.eslamasabry.oc.feature.about.presentation.TourLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Help › Tips (test/teaching_test.dart:220,296) over the in-memory store and a recording tour launcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class TipsViewModelTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private class Tours(private val runs: Boolean) : TourLauncher {
        val started = mutableListOf<String>()

        override fun start(id: String): Boolean {
            started += id
            return runs
        }
    }

    @Test
    fun `B-NAV-074 Help Tips lists every tip and tutorial from the registry, Make it yours among the tours`() {
        // The same ids, in the same order, as :engine:settings TeachingRegistry.defaults (the Flutter registry).
        assertEquals(
            listOf("stop-after-step", "send-later", "hold-mic", "make-it-yours", "whats-new"),
            TipsRegistry.entries.map { it.id },
        )
        val ui = TipsViewModel(InMemoryTipsStore(), Tours(runs = true), FakeNavigator()).state.value
        assertEquals(listOf("stop-after-step", "send-later", "hold-mic"), ui.tips.map { it.id })
        assertEquals(listOf("make-it-yours", "whats-new"), ui.tours.map { it.id })
        assertTrue(ui.tours.all { it.kind == TipKind.Tour })
        // One tour, not two: the older Appearance tour is gone.
        assertFalse(TipsRegistry.entries.any { it.id == "appearance-studio" })
    }

    @Test
    fun `B-NAV-074 Show me replays a tip in place and counts nothing`() {
        val store = InMemoryTipsStore()
        val vm = TipsViewModel(store, Tours(runs = true), FakeNavigator())
        vm.onShowMe(TipsRegistry.STOP_AFTER_STEP)
        assertEquals(TipsRegistry.STOP_AFTER_STEP, vm.state.value.replaying)
        assertTrue(store.done.value.isEmpty())
        vm.onReplayDismissed(TipsRegistry.STOP_AFTER_STEP)
        assertNull(vm.state.value.replaying)
    }

    @Test
    fun `B-NAV-074 Show me on Make it yours runs the tour on its own screen`() {
        val tours = Tours(runs = true)
        val vm = TipsViewModel(InMemoryTipsStore(), tours, FakeNavigator())
        vm.onShowMe(TipsRegistry.MAKE_IT_YOURS)
        assertEquals(listOf(TipsRegistry.MAKE_IT_YOURS), tours.started)
        assertNull(vm.state.value.replaying)
        // Without a tour screen in this build, the offer shows in place: Show me is never dead.
        val none = TipsViewModel(InMemoryTipsStore(), Tours(runs = false), FakeNavigator())
        none.onShowMe(TipsRegistry.MAKE_IT_YOURS)
        assertEquals(TipsRegistry.MAKE_IT_YOURS, none.state.value.replaying)
    }

    @Test
    fun `B-NAV-074 Show tips again resets every tip and says so`() = runTest {
        val store = InMemoryTipsStore()
        store.markDone(TipsRegistry.SEND_LATER)
        val vm = TipsViewModel(store, Tours(runs = true), FakeNavigator())
        assertFalse(vm.state.value.resetDone)
        vm.onShowTipsAgain()
        assertTrue(store.done.value.isEmpty())
        assertTrue(vm.state.value.resetDone)
        assertFalse(vm.state.value.resetting)
    }

    @Test
    fun `B-NAV-074 a failed reset does not claim the tips will show again`() = runTest {
        val failing =
            object : TipsStore {
                override val done: StateFlow<Set<String>> = MutableStateFlow(setOf(TipsRegistry.HOLD_MIC))

                override suspend fun showTipsAgain(): Outcome<Unit> = Outcome.Failed(Failure.StorageRefused())
            }
        val nav = FakeNavigator()
        val vm = TipsViewModel(failing, Tours(runs = true), nav)
        vm.onShowTipsAgain()
        assertFalse(vm.state.value.resetDone)
        vm.onBack()
        assertEquals(1, nav.backs)
    }
}
