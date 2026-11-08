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
import io.github.eslamasabry.oc.feature.factsbook.presentation.ReminderEditViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.NeedsYouViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.CareNotice
import io.github.eslamasabry.oc.engine.factsbook.ProposalDecision
import io.github.eslamasabry.oc.engine.factsbook.RuleProblem
import io.github.eslamasabry.oc.engine.factsbook.TriggerSourceKind
import io.github.eslamasabry.oc.engine.factsbook.TriggersLoad
import io.github.eslamasabry.oc.feature.factsbook.presentation.EventViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleEditorViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.TriggersNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.TriggersViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.SettingsViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.StatusViewModel
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

    private val preview =
        """{"what":"Gym","zone":"vault","locale":"en","ambiguities":[],"preview_digest":"dg","schedule":{"id":"sch_0","when":{"every":"day","at":"07:00"},"zone":"vault"}}"""

    private fun FakeAccess.dailyReminder() {
        node.on(
            "GET /v1/reminders",
            """{"items":[{"id":"rem_1","what":"Gym","rev":4,"schedule":{"id":"sch_0","when":{"every":"day","at":"07:00"},"zone":"vault"}}]}""",
        )
    }

    @Test
    fun `B-FB-029 a reminder whose reading agrees is saved and the page closes`() = runTest {
        val access = FakeAccess().apply { node.healthy(); dailyReminder() }
        access.node.on("POST /v1/reminders/parse", preview)
        access.node.on("PATCH /v1/reminders/rem_1", """{"status":"ok","id":"rem_1"}""")
        val vm = ReminderEditViewModel(FactsBookRoute.ReminderEdit("rem_1"), access, nav, FixedClock())
        vm.state.first { it.draft != null }
        vm.save()
        vm.state.first { !it.working }
        assertTrue("PATCH /v1/reminders/rem_1" in access.node.asked)
        assertEquals(1, nav.backs)
    }

    @Test
    fun `B-FB-030 a reminder the node reads differently is not saved`() = runTest {
        val access = FakeAccess().apply { node.healthy(); dailyReminder() }
        access.node.on("POST /v1/reminders/parse", preview.replace("07:00", "19:00"))
        val vm = ReminderEditViewModel(FactsBookRoute.ReminderEdit("rem_1"), access, nav, FixedClock())
        vm.state.first { it.draft != null }
        vm.save()
        assertTrue(vm.state.first { !it.working }.mismatch)
        assertFalse(access.node.asked.any { it.startsWith("PATCH") })
        assertEquals(0, nav.backs)
    }

    @Test
    fun `B-FB-031 Needs you lists the changes waiting and cannot approve without an owner device`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        val vm = NeedsYouViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val state = vm.state.first { it.review.isNotEmpty() }
        assertEquals("rvw_1", state.review.single().id)
        assertFalse(state.canApprove)
        assertFalse(access.node.asked.any { it.contains("/decision") })
    }

    @Test
    fun `B-FB-035 an approval on Needs you is signed, sent once and says it was approved`() = runTest {
        val access = FakeAccess(owner = true).apply { node.healthy() }
        access.node.on("POST /v1/review/rvw_1/decision", """{"status":"ok","id":"rvw_1"}""")
        val vm = NeedsYouViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val state = vm.state.first { it.review.isNotEmpty() && it.canApprove }
        vm.approve(state.review.single())
        vm.state.first { it.notice == CareNotice.Approved }
        assertEquals(1, access.node.asked.count { it == "POST /v1/review/rvw_1/decision" })
    }

    @Test
    fun `B-FB-036 a change approved elsewhere first says it changed and nothing else`() = runTest {
        val access = FakeAccess(owner = true).apply { node.healthy() }
        access.node.on("POST /v1/review/rvw_1/decision", """{"error":{"code":"stale_approval"}}""", status = 409)
        val vm = NeedsYouViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val item = vm.state.first { it.review.isNotEmpty() && it.canApprove }.review.single()
        vm.approveEdited(item, "  Likes green tea ")
        val after = vm.state.first { it.notice != null || it.failure != null }
        assertEquals(CareNotice.Stale, after.notice)
        assertNull(after.failure)
    }

    @Test
    fun `B-FB-037 Status reads the node, says the history is not checked without one, and Disconnect lands home`() = runTest {
        val access = FakeAccess(owner = true).apply { node.healthy() }
        val vm = StatusViewModel(access, nav, FixedClock())
        val state = vm.state.first { !it.loading }
        assertTrue(state.status != null)
        assertNull(state.control) // the fake node will not show /v1/control: "not checked", never "verified"
        assertTrue(state.canApprove)
        vm.askDisconnect()
        assertTrue(vm.state.value.confirmDisconnect)
        vm.disconnect()
        vm.state.first { !it.confirmDisconnect }
        assertFalse(access.engine.state.value.paired)
        assertFalse(access.secrets.values.containsKey("oc.factsbook.p1"))
        assertEquals(1, nav.lands)
    }

    @Test
    fun `B-FB-038 Settings changes the brief cap and the home zone only with approval, against the revision seen`() = runTest {
        val access = FakeAccess(owner = true)
        access.node.on(
            "GET /v1/config",
            """{"rev":7,"vault_zone":"Africa/Cairo","lock_timeout_minutes":15,"brief":{"cap_per_day":5,"time":"08:00","quiet_hours":"22:00-07:00"}}""",
        )
        access.node.on("PATCH /v1/config", """{"status":"ok","id":"config","rev":8}""")
        val vm = SettingsViewModel(access, nav)
        vm.state.first { it.settings != null }
        vm.setBriefCap(3)
        vm.state.first { !it.saving }
        assertEquals(1, access.node.asked.count { it == "PATCH /v1/config" })

        val unowned = FakeAccess(owner = false)
        unowned.node.answers.putAll(access.node.answers)
        val off = SettingsViewModel(unowned, nav)
        off.state.first { it.settings != null }
        off.setBriefCap(3)
        assertFalse(unowned.node.asked.any { it == "PATCH /v1/config" })
    }

    @Test
    fun `B-FB-065 Triggers groups rules by source and pauses one, signed, saying it was paused`() = runTest {
        val access = FakeAccess(owner = true).apply { node.healthy(); node.triggers() }
        access.node.on("PUT /v1/rules/rul_1", RULE.replace("\"rev\":4", "\"rev\":5").replace("\"enabled\":true", "\"enabled\":false"))
        val vm = TriggersViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val state = vm.state.first { it.load == TriggersLoad.Ready && it.canApprove }
        assertEquals(TriggerSourceKind.Share, state.groups.single().kind)
        assertEquals(1, state.activity.getValue("rul_1").proposed)
        vm.togglePause(state.groups.single().rules.single())
        vm.state.first { it.notice == TriggersNotice.Paused }
        assertEquals(1, access.node.asked.count { it == "PUT /v1/rules/rul_1" })

        val unowned = FakeAccess(owner = false).apply { node.healthy(); node.triggers() }
        val off = TriggersViewModel(unowned, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { off.state.collect {} }
        val ready = off.state.first { it.load == TriggersLoad.Ready }
        off.togglePause(ready.groups.single().rules.single())
        assertFalse(unowned.node.asked.any { it.startsWith("PUT") })
    }

    @Test
    fun `B-FB-066 a new rule is checked before it is sent, tried on a sample without saving, then saved signed`() = runTest {
        val access = FakeAccess(owner = true).apply { node.healthy(); node.triggers() }
        access.node.on("POST /v1/rules/test", """{"matched":true,"reason":"matched","summary":"Library visit","when":"2026-11-12T09:00"}""")
        access.node.on("POST /v1/rules", RULE.replace("rul_1", "rul_9"))
        val vm = RuleEditorViewModel(FactsBookRoute.Rule(null), access, nav, FixedClock())
        vm.state.first { it.canApprove }
        vm.save()
        assertEquals(RuleProblem.NameEmpty, vm.state.value.problem)
        assertFalse(access.node.asked.contains("POST /v1/rules"))
        vm.edit { it.copy(name = "Library", words = "library, visit") }
        vm.useExample()
        assertTrue(vm.state.value.sample.startsWith("Appointment: Library visit | 2026-11-12T09:00"))
        vm.test()
        assertTrue(vm.state.first { it.result != null }.result!!.matched)
        assertFalse(access.node.asked.contains("POST /v1/rules"))
        vm.save()
        val saved = vm.state.first { it.notice == RuleNotice.Saved }
        assertEquals("rul_9", saved.saved?.id)
    }

    @Test
    fun `B-FB-067 what happened to a message shows its rule and what it proposed, and a gone one says so`() = runTest {
        val access = FakeAccess(owner = true).apply { node.healthy(); node.triggers() }
        val vm = EventViewModel(FactsBookRoute.Event("evt_1"), access, nav)
        val state = vm.state.first { !it.loading }
        assertEquals("Clinic", state.event?.claims?.sender)
        assertEquals("Clinic", state.ruleNames["trg_1"])
        assertEquals(listOf("rpr_1"), state.proposals.map { it.id })
        val gone = EventViewModel(FactsBookRoute.Event("evt_x"), access, nav).state.first { !it.loading }
        assertEquals("not_found", gone.error?.code)
    }

    @Test
    fun `B-FB-068 Needs you counts a rule's proposal and offer once, confirms it signed and dismisses the offer`() = runTest {
        val access = FakeAccess(owner = true).apply { node.healthy(); node.triggers() }
        access.node.on("POST /v1/reminder-proposals/rpr_1/decision", """{"status":"ok","id":"rpr_1"}""")
        access.node.on("POST /v1/offers/off_1/cancel", """{"id":"off_1","rev":2,"digest":"od","state":"cancelled","expires_at":"2026-11-09T10:00:00Z"}""")
        val vm = NeedsYouViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val state = vm.state.first { it.proposals.isNotEmpty() && it.offers.isNotEmpty() }
        assertEquals(3, state.waiting) // one change, one proposal, one offer
        assertEquals(3, access.engine.state.value.needsYouCount)
        assertEquals("evt_1", state.fromEvent["rpr_1"]?.id)
        assertEquals("Clinic", state.offerRule["off_1"])
        vm.decideProposal(state.proposals.single(), ProposalDecision.Confirm)
        vm.state.first { it.notice == CareNotice.Approved }
        vm.notNow(state.offers.single())
        vm.state.first { it.working.isEmpty() && access.node.asked.contains("POST /v1/offers/off_1/cancel") }
        assertEquals(1, access.node.asked.count { it == "POST /v1/reminder-proposals/rpr_1/decision" })
    }
}
