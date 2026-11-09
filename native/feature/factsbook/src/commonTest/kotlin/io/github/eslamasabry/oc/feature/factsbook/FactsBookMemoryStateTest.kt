package io.github.eslamasabry.oc.feature.factsbook

import io.github.eslamasabry.oc.engine.factsbook.FactsBookLoad
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouKind
import io.github.eslamasabry.oc.engine.factsbook.NodeHostFacts
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The Memory section's behaviour while the memory on this phone comes up, stops or is counted (F: memory_section.dart). */
@OptIn(ExperimentalCoroutinesApi::class)
class FactsBookMemoryStateTest {
    private val nav = Nav()

    @BeforeTest
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    private val running = NodeHostFacts(linuxReady = true, binaryInstalled = true, dataExists = true, serviceRunning = true, binaryAvailable = true)

    @Test
    fun `B-FB-120 a memory that is still coming up is read again instead of shown as an error`() = runTest {
        val access = FakeAccess().apply { node.unreachable = true; nodeHost.facts = running }
        access.engine.open("p1")
        access.phoneNode.refresh()
        assertTrue(access.phoneNode.status.value.pairedHere)
        val vm = MemorySectionViewModel(access, nav, FixedClock())
        vm.readRetryDelay = 1.seconds
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val waiting = vm.state.first { it.error != null }
        assertTrue(waiting.nodeStarting, "said as starting, not as did not answer")
        access.node.unreachable = false
        access.node.healthy()
        val ready = vm.state.first { it.load == FactsBookLoad.Ready && it.error == null }
        assertFalse(ready.nodeStarting)
    }

    @Test
    fun `B-FB-121 after twelve tries the section stops waiting and says it did not answer`() = runTest {
        val access = FakeAccess().apply { node.unreachable = true; nodeHost.facts = running }
        access.engine.open("p1")
        access.phoneNode.refresh()
        val vm = MemorySectionViewModel(access, nav, FixedClock())
        vm.readRetryDelay = 1.seconds
        vm.readRetryAttempts = 2
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        advanceUntilIdle()
        assertFalse(vm.state.value.nodeStarting)
        assertEquals(FactsBookLoad.Failed, vm.state.value.load)
    }

    @Test
    fun `B-FB-122 when the memory on this phone stops the section reads again so the row says stopped`() = runTest {
        val access = FakeAccess().apply { node.healthy(); nodeHost.facts = running }
        access.engine.open("p1")
        access.phoneNode.refresh()
        val vm = MemorySectionViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.state.first { it.recent.isNotEmpty() }
        val before = access.node.asked.count { it == "GET /v1/status" }
        access.nodeHost.facts = running.copy(serviceRunning = false)
        access.phoneNode.refresh()
        advanceUntilIdle()
        assertTrue(access.node.asked.count { it == "GET /v1/status" } > before)
        assertTrue(vm.state.value.nodeStopped)
    }

    @Test
    fun `B-FB-123 a reminder a rule proposed that the node also lists for review is counted once`() = runTest {
        val access = FakeAccess().apply {
            node.healthy()
            node.triggers()
            node.on("GET /v1/review", """{"items":[{"rvw":"rpr_1","class":"fact_add","state":"proposed","digest":"d","proposal":{"text":"x"}}]}""")
        }
        val vm = MemorySectionViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val state = vm.state.first { s -> s.needsYou.any { it.kind == NeedsYouKind.Offer } }
        assertEquals(1, state.needsYou.first { it.kind == NeedsYouKind.Review }.count)
        assertEquals(2, state.needsYouCount) // one change to review (not two) and one offer
        assertTrue(access.engine.state.value.reviewCards.isEmpty())
    }

    @Test
    fun `B-FB-124 Try again starts a stopped memory on this phone before it reads again`() = runTest {
        val access = FakeAccess(paired = true).apply { node.healthy(); nodeHost.facts = running.copy(serviceRunning = false) }
        access.backgroundScope = backgroundScope
        access.engine.open("p1")
        access.phoneNode.refresh()
        val vm = MemorySectionViewModel(access, nav, FixedClock())
        vm.retry()
        advanceUntilIdle()
        assertTrue("start" in access.nodeHost.steps)
    }
}
