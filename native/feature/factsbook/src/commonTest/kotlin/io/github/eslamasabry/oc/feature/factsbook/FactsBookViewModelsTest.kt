package io.github.eslamasabry.oc.feature.factsbook

import io.github.eslamasabry.oc.engine.factsbook.CaptureKind
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouKind
import io.github.eslamasabry.oc.feature.factsbook.presentation.AboutYouViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.ConnectViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingFilter
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingViewModel
import io.github.eslamasabry.oc.ui.routes.FactsBookRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The pages' ViewModels over a real engine and a fake node (Flutter test/factsbook/factsbook_screens_test.dart). */
@OptIn(ExperimentalCoroutinesApi::class)
class FactsBookViewModelsTest {
    private val nav = Nav()

    @BeforeTest
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-FB-020 the Memory section stays hidden while the developer switch is off`() = runTest {
        val access = FakeAccess(on = false)
        val vm = MemorySectionViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertFalse(vm.state.value.shown)
        access.enabled.value = true
        assertTrue(vm.state.first { it.loaded }.shown)
    }

    @Test
    fun `B-FB-021 unpaired the section offers Set up FactsBook and it opens Connect`() = runTest {
        val vm = MemorySectionViewModel(FakeAccess(paired = false), nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val state = vm.state.first { it.loaded }
        assertFalse(state.paired)
        vm.setUp()
        assertEquals(listOf<Any>(FactsBookRoute.Connect), nav.opened)
    }

    @Test
    fun `B-FB-022 paired the section counts what needs the person once and shows the newest thing`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        val vm = MemorySectionViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val state = vm.state.first { it.recent.isNotEmpty() }
        assertEquals(1, state.needsYouCount)
        assertEquals(NeedsYouKind.Review, state.needsYou.single().kind)
        assertEquals("Sara", state.recent.single().title)
        vm.openThing("per_01")
        assertEquals(listOf<Any>(FactsBookRoute.Thing("per_01")), nav.opened)
    }

    @Test
    fun `B-FB-023 About you reads the list again for a kind chip`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        val vm = AboutYouViewModel(access, nav)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals("Sara", vm.state.first { it.things != null }.things!!.single().title)
        vm.filter(ThingFilter.People)
        assertTrue("GET /v1/things?kind=person" in access.node.asked)
        vm.search("  tea  ")
        assertEquals(FactsBookRoute.Search("tea"), nav.opened.last())
    }

    @Test
    fun `B-FB-024 a note made while the node is away stays in the editor and is kept on the phone`() = runTest {
        val access = FakeAccess().apply { node.unreachable = true }
        val vm = CaptureViewModel(access, nav)
        vm.kind(CaptureKind.Note)
        vm.save("Call the dentist")
        val state = vm.state.first { !it.working && it.notice != null }
        assertEquals(CaptureNotice.KeptOnPhone, state.notice)
        assertEquals(0, state.clearSeed) // the text is not cleared
        assertTrue(access.secrets.values.getValue("oc.factsbookOutbox.p1").contains("Call the dentist"))
    }

    @Test
    fun `B-FB-025 an empty note is not saved and says why`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        val vm = CaptureViewModel(access, nav)
        vm.save("   ")
        assertTrue(vm.state.value.needsText)
        assertFalse(access.node.asked.any { it == "POST /v1/inbox" })
    }

    @Test
    fun `B-FB-026 a fact added on the thing page says whether it waits for review`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        access.node.on(
            "GET /v1/things/per_01",
            """{"id":"per_01","title":"Sara","kind":"person","rev":3,"updated_at":"2026-10-10T08:00:00Z","facts":[]}""",
        )
        access.node.on("POST /v1/things/per_01/facts", """{"status":"proposed","id":"f_9","review_id":"rvw_9"}""")
        val vm = ThingViewModel(FactsBookRoute.Thing("per_01"), access, nav)
        vm.state.first { it.thing != null }
        vm.addFact("  Likes   green tea ")
        assertEquals(ThingNotice.FactProposed, vm.state.first { it.notice != null }.notice)
    }

    @Test
    fun `B-FB-027 Connect refuses an address that is not private before anything is sent`() = runTest {
        val access = FakeAccess(paired = false)
        val vm = ConnectViewModel(access, nav)
        vm.connect("factsbook.example.com", "123456", "")
        assertEquals("address_not_allowed", vm.state.first { it.error != null }.error!!.code)
        assertTrue(access.pairNode.asked.isEmpty())
        assertTrue(nav.opened.isEmpty())
    }

    @Test
    fun `B-FB-028 discarding a kept note waits for the person's answer`() = runTest {
        val outbox =
            """[{"id":"out_1_0","key":"cap-abcdefghijklmnopqrstuvwx","request":{"kind":"note","text":"Old note","trust":"user_authored"},"created_at":"2026-10-01T09:00:00Z","retries":0}]"""
        val access = FakeAccess(secrets = MemorySecrets(mapOf("oc.factsbook.p1" to "t", "oc.factsbookOutbox.p1" to outbox)))
        access.node.unreachable = true
        val vm = NotSavedViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val entry = vm.state.first { it.entries.isNotEmpty() }.entries.single()
        vm.askDiscard(entry)
        assertTrue(access.secrets.values.containsKey("oc.factsbookOutbox.p1")) // not yet
        vm.cancelDiscard()
        assertNull(vm.state.value.confirmDiscard)
        vm.askDiscard(entry)
        vm.discard()
        assertFalse(access.secrets.values.containsKey("oc.factsbookOutbox.p1"))
        assertTrue(vm.state.first { !it.working }.entries.isEmpty())
    }
}
