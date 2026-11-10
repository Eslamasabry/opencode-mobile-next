package io.github.eslamasabry.oc.feature.factsbook

import io.github.eslamasabry.oc.engine.factsbook.FactsBookLoad
import io.github.eslamasabry.oc.engine.factsbook.TriggersLoad
import io.github.eslamasabry.oc.feature.factsbook.presentation.AboutYouViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.EventViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.NeedsYouViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.ReminderChecks
import io.github.eslamasabry.oc.feature.factsbook.presentation.RemindersViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.SettingsViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.StatusViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.TriggersViewModel
import io.github.eslamasabry.oc.ui.routes.FactsBookRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Cache-first FactsBook pages (owner rule, 2026-11-09): a page opened again draws what the node last answered at once,
 * with the node away, and never shows its skeleton or an error in place of rows it already has.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstFactsBookViewModelsTest {
    private val nav = Nav()
    private val reports = FakeReports()
    private val checks = ReminderChecks()
    private val kept = MemoryKept()

    @BeforeTest
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    private fun FakeNode.everything() {
        healthy()
        triggers()
        on("GET /v1/things/per_01", """{"id":"per_01","title":"Sara","kind":"person","rev":3,"updated_at":"2026-10-10T08:00:00Z","facts":[]}""")
        on("GET /v1/areas", """{"items":[{"slug":"health","storage":"sensitive","review":"first","share":{},"brief":true,"export":false,"notify":"generic"}]}""")
        on(
            "GET /v1/reminders",
            """{"items":[{"id":"rem_1","what":"Gym","rev":4,"schedule":{"id":"sch_0","when":{"every":"day","at":"07:00"},"zone":"vault"}}]}""",
        )
        on(
            "GET /v1/config",
            """{"rev":7,"vault_zone":"Africa/Cairo","lock_timeout_minutes":15,"brief":{"cap_per_day":5,"time":"08:00","quiet_hours":"22:00-07:00"}}""",
        )
    }

    /** A first visit on a live node: every page is opened once, so its answer is kept. */
    private suspend fun TestScope.firstVisit() {
        val live = FakeAccess(owner = true, kept = kept).apply { node.everything() }
        listOf<() -> Unit>(
            { AboutYouViewModel(live, nav) },
            { ThingViewModel(FactsBookRoute.Thing("per_01"), live, nav, checks) },
            { AreasViewModel(live, nav) },
            { RemindersViewModel(live, nav) },
            { StatusViewModel(live, nav, FixedClock()) },
            { SettingsViewModel(live, nav) },
            { TriggersViewModel(live, nav, FixedClock()) },
        ).forEach { it() }
        live.engine.open("p1")
        live.engine.refresh()
    }

    /** The same node, away: nothing answers, so everything shown comes from what was kept. */
    private fun away(): FakeAccess = FakeAccess(owner = true, kept = kept).apply { node.unreachable = true }

    @Test
    fun `CF-60 the first frame of the Memory section and Needs you has the rows with the node away`() = runTest {
        firstVisit()
        val access = away()
        val section = MemorySectionViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { section.state.collect {} }
        val s = section.state.first { it.loaded }
        assertEquals(FactsBookLoad.Ready, s.load)
        // The fixture's node holds one change to review, one reminder a rule proposed and one open offer; each waits
        // for the person and the count says all three (the overview's Needs you counts proposals and offers too).
        assertEquals(3, s.needsYouCount)
        val care = NeedsYouViewModel(access, nav, FixedClock(), reports, checks)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { care.state.collect {} }
        val n = care.state.first { it.review.isNotEmpty() }
        assertEquals(FactsBookLoad.Ready, n.load)
        assertEquals(listOf("rvw_1"), n.review.map { it.id })
        assertEquals(1, n.proposals.size) // the proposed reminder kept from the triggers
    }

    @Test
    fun `CF-61 the first frame of About you and a thing page has the rows with the node away`() = runTest {
        firstVisit()
        val access = away()
        val about = AboutYouViewModel(access, nav)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { about.state.collect {} }
        assertEquals("Sara", about.state.first { it.things != null }.things!!.single().title)
        val thing = ThingViewModel(FactsBookRoute.Thing("per_01"), access, nav, checks)
        assertEquals("Sara", thing.state.first { it.thing != null }.thing!!.title)
        val areas = AreasViewModel(access, nav)
        assertEquals(listOf("health"), areas.state.first { it.areas != null }.areas!!.map { it.slug })
    }

    @Test
    fun `CF-62 the first frame of Reminders has the list with the node away`() = runTest {
        firstVisit()
        val vm = RemindersViewModel(away(), nav)
        assertEquals(listOf("rem_1"), vm.state.first { it.reminders != null }.reminders!!.map { it.id })
    }

    @Test
    fun `CF-63 the first frame of Status and Settings has the modules and the brief cap with the node away`() = runTest {
        firstVisit()
        val access = away()
        assertNotNull(StatusViewModel(access, nav, FixedClock()).state.first { it.status != null }.status)
        assertEquals(5L, SettingsViewModel(access, nav).state.first { it.settings != null }.settings!!.brief.capPerDay)
    }

    @Test
    fun `CF-64 the first frame of Triggers and a message's page has the rules and the event with the node away`() = runTest {
        firstVisit()
        val access = away()
        val vm = TriggersViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val s = vm.state.first { it.load == TriggersLoad.Ready }
        assertEquals(1, s.groups.sumOf { it.rules.size })
        val event = EventViewModel(FactsBookRoute.Event("evt_1"), access, nav)
        assertTrue(event.state.first { it.event != null }.event!!.id == "evt_1")
    }

    @Test
    fun `CF-65 with nothing kept the first load is the same loading as before`() = runTest {
        val access = FakeAccess(owner = true, kept = MemoryKept()).apply { node.unreachable = true }
        val vm = AboutYouViewModel(access, nav)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertTrue(vm.state.value.things == null)
    }
}
