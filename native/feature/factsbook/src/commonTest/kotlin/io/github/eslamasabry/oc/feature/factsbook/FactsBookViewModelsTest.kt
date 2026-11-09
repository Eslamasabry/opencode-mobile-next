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
import io.github.eslamasabry.oc.feature.factsbook.presentation.ReminderChecks
import io.github.eslamasabry.oc.feature.factsbook.presentation.ReminderCheckResult
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureReminder
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeState
import io.github.eslamasabry.oc.feature.factsbook.presentation.PhoneNodeViewModel
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
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.NewChatOutcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
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
    private val reports = FakeReports()
    private val checks = ReminderChecks()

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
    fun `B-FB-021 unpaired the section offers Set up FactsBook, memory on this phone where Linux exists and Connect elsewhere`() = runTest {
        val vm = MemorySectionViewModel(FakeAccess(paired = false), nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val state = vm.state.first { it.loaded }
        assertFalse(state.paired)
        vm.setUp()
        val noLinux = MemorySectionViewModel(FakeAccess(paired = false).apply { nodeHost.supportedHere = false }, nav, FixedClock())
        noLinux.setUp()
        assertEquals(listOf<Any>(FactsBookRoute.PhoneNode, FactsBookRoute.Connect), nav.opened)
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
        val vm = CaptureViewModel(access, nav, checks)
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
        val vm = CaptureViewModel(access, nav, checks)
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
        val vm = ThingViewModel(FactsBookRoute.Thing("per_01"), access, nav, checks)
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

    private val keptNote =
        """[{"id":"out_1_0","key":"cap-abcdefghijklmnopqrstuvwx","request":{"kind":"note","text":"Old note","trust":"user_authored"},"created_at":"2026-10-01T09:00:00Z","retries":0}]"""

    @Test
    fun `B-FB-028b Try now with the node away says still not saved and keeps the note`() = runTest {
        val access = FakeAccess(secrets = MemorySecrets(mapOf("oc.factsbook.p1" to "t", "oc.factsbookOutbox.p1" to keptNote)))
        access.node.unreachable = true
        val vm = NotSavedViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val entry = vm.state.first { it.entries.isNotEmpty() }.entries.single()
        vm.retry(entry)
        val state = vm.state.first { !it.working && it.notice != null }
        assertEquals(io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedNotice.Still, state.notice)
        assertNull(state.error)
        assertEquals(1, state.entries.size)
        vm.dismiss()
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `B-FB-028c Try now that goes through says saved and the note leaves the page`() = runTest {
        val access = FakeAccess(secrets = MemorySecrets(mapOf("oc.factsbook.p1" to "t", "oc.factsbookOutbox.p1" to keptNote)))
        access.node.unreachable = true
        val vm = NotSavedViewModel(access, nav, FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val entry = vm.state.first { it.entries.isNotEmpty() }.entries.single()
        access.node.unreachable = false
        access.node.healthy()
        access.node.on("POST /v1/outbox", """{"receipts":[{"key":"cap-abcdefghijklmnopqrstuvwx","status":"accepted"}]}""")
        vm.retry(entry)
        val state = vm.state.first { !it.working && it.notice != null }
        assertEquals(io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedNotice.Saved, state.notice)
        assertTrue(state.entries.isEmpty())
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
        val vm = ReminderEditViewModel(FactsBookRoute.ReminderEdit("rem_1"), access, nav, FixedClock(), checks)
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
        val vm = ReminderEditViewModel(FactsBookRoute.ReminderEdit("rem_1"), access, nav, FixedClock(), checks)
        vm.state.first { it.draft != null }
        vm.save()
        assertTrue(vm.state.first { !it.working }.mismatch)
        assertFalse(access.node.asked.any { it.startsWith("PATCH") })
        assertEquals(0, nav.backs)
    }

    @Test
    fun `B-FB-031 Needs you lists the changes waiting and cannot approve without an owner device`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        val vm = NeedsYouViewModel(access, nav, FixedClock(), reports, checks)
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
        val vm = NeedsYouViewModel(access, nav, FixedClock(), reports, checks)
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
        val vm = NeedsYouViewModel(access, nav, FixedClock(), reports, checks)
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
        val vm = NeedsYouViewModel(access, nav, FixedClock(), reports, checks)
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

    @Test
    fun `B-FB-081 Talk about this accepts the offer, opens a new chat with only its summary and reports the first message`() = runTest {
        val access = FakeAccess(owner = true).apply { node.healthy(); node.triggers() }
        access.node.on("POST /v1/offers/off_1/accept", """{"id":"st_1","offer_id":"off_1","receipt_key":"offer-k","status":"pending"}""")
        access.node.on("POST /v1/offers/off_1/receipt", """{"id":"st_1","offer_id":"off_1","receipt_key":"offer-k","status":"started"}""")
        val vm = NeedsYouViewModel(access, nav, FixedClock(), reports, checks)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val offer = vm.state.first { it.offers.isNotEmpty() }.offers.single()
        vm.talk(offer, "Lead in:")
        vm.state.first { it.working.isEmpty() && nav.opened.any { r -> r is ChatsRoute.New } }
        val chat = nav.opened.filterIsInstance<ChatsRoute.New>().single()
        assertEquals("Lead in:\n\nA clinic visit next week", chat.initialText)
        assertEquals("offer-k", chat.reportKey)
        assertEquals(ProfileId("p1"), chat.profile)
        assertEquals(0, access.node.asked.count { it == "POST /v1/offers/off_1/receipt" }, "accepting is not a start")
        reports.report("offer-k", NewChatOutcome.FirstMessageSent)
        vm.state.first { access.node.asked.contains("POST /v1/offers/off_1/receipt") }
        assertEquals(1, access.node.asked.count { it == "POST /v1/offers/off_1/receipt" })
        assertTrue(access.node.bodies[access.node.asked.indexOf("POST /v1/offers/off_1/receipt")]!!.contains("\"first_message_sent\":true"))
        reports.report("offer-k", NewChatOutcome.Left)
        assertEquals(1, access.node.asked.count { it == "POST /v1/offers/off_1/receipt" }, "one report per start")
    }

    @Test
    fun `B-FB-082 an offer the node will not start opens nothing and says so, and a changed one says it was refreshed`() = runTest {
        val access = FakeAccess(owner = true).apply { node.healthy(); node.triggers() }
        access.node.on("POST /v1/offers/off_1/accept", """{"id":"st_1","offer_id":"off_1","receipt_key":"offer-k","status":"unknown"}""")
        val vm = NeedsYouViewModel(access, nav, FixedClock(), reports, checks)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val offer = vm.state.first { it.offers.isNotEmpty() }.offers.single()
        vm.talk(offer, "Lead in:")
        assertEquals(CareNotice.OfferUnknown, vm.state.first { it.notice != null }.notice)
        assertTrue(nav.opened.none { it is ChatsRoute.New })

        access.node.on("POST /v1/offers/off_1/accept", """{"error":{"code":"stale_approval"}}""", status = 409)
        vm.talk(offer, "Lead in:")
        assertEquals(CareNotice.OfferStale, vm.state.first { it.notice == CareNotice.OfferStale }.notice)
        assertTrue(nav.opened.none { it is ChatsRoute.New })
    }

    private val pairAnswer =
        """{"token":"t2","client_id":"cli_9","scopes":["read","write","admin","owner"],"vault":"v9","root_fingerprint":"fp9","node":"n9"}"""

    @Test
    fun `B-FB-073 setting up memory on this phone installs, starts, pairs with this phone's key and lands in Add something`() = runTest {
        val access = FakeAccess(paired = false)
        access.backgroundScope = backgroundScope
        access.pairNode.on("GET /v1/info", "{}")
        access.pairNode.on("POST /v1/pair", pairAnswer)
        val vm = PhoneNodeViewModel(access, nav)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.state.first { it.node.state == PhoneNodeState.NotInstalled }
        vm.runHere()
        vm.state.first { it.node.state == PhoneNodeState.Running && !it.node.busy }
        assertEquals(listOf("install", "vault", "start", "pair 64"), access.nodeHost.steps)
        val link = access.engine.state.value.link!!
        assertEquals("http://127.0.0.1:8787", link.baseUrl)
        assertEquals("dev_9", link.ownerDeviceId)
        assertTrue(access.engine.state.value.canApprove)
        assertEquals(FactsBookRoute.Capture, nav.opened.last())
    }

    @Test
    fun `B-FB-074 a memory the person stopped is not started again when the app opens, and one they did not stop is`() = runTest {
        val access = FakeAccess(paired = false)
        access.pairNode.on("GET /v1/info", "{}")
        access.pairNode.on("POST /v1/pair", pairAnswer)
        access.engine.open("p1")
        access.phoneNode.runHere()
        access.phoneNode.stop()
        assertEquals(PhoneNodeState.Stopped, access.phoneNode.status.value.state)
        access.nodeHost.steps.clear()
        access.phoneNode.ensureRunning()
        assertFalse("start" in access.nodeHost.steps)
        access.store.setNodeStopped(false)
        access.phoneNode.ensureRunning()
        assertTrue("start" in access.nodeHost.steps)
        assertEquals(PhoneNodeState.Running, access.phoneNode.status.value.state)
    }

    @Test
    fun `B-FB-078 with a rule on, Add something lets the rules read the note unless the person turns it off`() = runTest {
        val access = FakeAccess(owner = true).apply { node.healthy(); node.triggers() }
        access.node.on("POST /v1/events", """{"event":{"id":"evt_9","source":{"kind":"note"},"captured_at":"2026-11-08T10:00:00Z","evidence_ref":"itm_9"}}""")
        access.node.on("POST /v1/inbox", """{"status":"saved","item_id":"itm_10"}""")
        val vm = CaptureViewModel(access, nav, checks)
        val ready = vm.state.first { it.rulesSupported }
        assertEquals(1, ready.rulesOn)
        assertTrue(ready.rulesLook)
        vm.save("Appointment: Clinic | 2026-11-12T09:00")
        vm.state.first { it.notice == CaptureNotice.Saved }
        assertTrue(access.node.asked.contains("POST /v1/events"))
        assertFalse(access.node.asked.contains("POST /v1/inbox"))
        vm.rules(false)
        vm.save("Buy milk")
        vm.state.first { it.clearSeed == 2 }
        assertTrue(access.node.asked.contains("POST /v1/inbox"))
    }

    @Test
    fun `B-FB-084 Also remind me about it checks the reminder first, then saves the note and makes the reminder`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        access.node.on("POST /v1/inbox", """{"status":"saved","item_id":"itm_10"}""")
        access.node.on("POST /v1/reminders", """{"status":"ok","id":"rem_9"}""")
        val vm = CaptureViewModel(access, nav, checks)
        vm.remind(true)
        vm.save("Gym every day at 7")
        assertEquals(FactsBookRoute.ReminderEdit(null, fromText = "Gym every day at 7"), nav.opened.last())
        assertFalse(access.node.asked.contains("POST /v1/inbox"), "nothing is saved before the check")
        val daily = kotlinx.serialization.json.Json.parseToJsonElement("""{"every":"day","at":"07:00"}""") as kotlinx.serialization.json.JsonObject
        val checked =
            io.github.eslamasabry.oc.engine.factsbook.ReminderPreview(
                what = "Gym", zone = "vault", locale = "en", ambiguities = emptyList(), adjustments = emptyList(), digest = "dg",
                raw = kotlinx.serialization.json.JsonObject(emptyMap()),
                schedule = io.github.eslamasabry.oc.engine.factsbook.ReminderSchedule("sch_0", io.github.eslamasabry.oc.engine.factsbook.ScheduleWhen(daily), "vault", daily),
            )
        checks.tell(ReminderCheckResult.Checked(checked))
        val done = vm.state.first { it.reminder != null }
        assertEquals(CaptureNotice.Saved, done.notice)
        assertTrue(done.reminder is CaptureReminder.Made)
        assertFalse(done.remind)
        assertEquals(listOf("POST /v1/inbox", "POST /v1/reminders"), access.node.asked.filter { it.startsWith("POST") })
    }

    @Test
    fun `B-FB-084 Change what I wrote saves nothing and keeps the words`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        val vm = CaptureViewModel(access, nav, checks)
        vm.remind(true)
        vm.save("Call the bank")
        checks.tell(ReminderCheckResult.Cancelled)
        val after = vm.state.first { !it.working }
        assertEquals(0, after.clearSeed)
        assertNull(after.notice)
        assertTrue(access.node.asked.none { it.startsWith("POST") })
    }

    @Test
    fun `B-FB-085 the check from Add something starts from the words and hands the reading back without saving`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        access.node.on("POST /v1/reminders/parse", preview)
        val vm = ReminderEditViewModel(FactsBookRoute.ReminderEdit(null, fromText = "Gym every day at 7"), access, nav, FixedClock(), checks)
        val shown = vm.state.first { it.draft != null }
        assertEquals("Gym every day at 7", shown.wrote)
        assertEquals(io.github.eslamasabry.oc.engine.factsbook.ReminderRepeat.Daily, shown.draft!!.timing.repeat)
        assertEquals(setOf("date"), shown.ambiguities.toSet(), "the words gave a time, not a day")
        vm.edit(shown.draft!!.copy(what = "Gym", timing = shown.draft!!.timing.copy(at = kotlinx.datetime.LocalDateTime(2000, 1, 1, 7, 0))))
        val heard = mutableListOf<ReminderCheckResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { checks.results.collect { heard += it } }
        vm.save()
        vm.state.first { !it.working }
        assertTrue(heard.single() is ReminderCheckResult.Checked)
        assertFalse(access.node.asked.contains("POST /v1/reminders"), "Add something makes it after the note")
        assertEquals(1, nav.backs)
    }

    @Test
    fun `B-FB-086 Change on a proposed reminder checks it and replaces the proposal, which waits for approval again`() = runTest {
        val access = FakeAccess(owner = true).apply { node.healthy(); node.triggers() }
        access.node.on(
            "GET /v1/reminder-proposals",
            """{"items":[{"id":"rpr_1","rev":2,"event_id":"evt_1","firing_id":"trg_1","area":"health","digest":"dg","state":"proposed",
              "preview":{"what":"Gym","zone":"vault","locale":"en","preview_digest":"pd","schedule":{"id":"sch_0","when":{"every":"day","at":"07:00"},"zone":"vault"}}}]}""",
        )
        access.node.on("POST /v1/reminders/parse", preview)
        access.node.on("POST /v1/reminder-proposals/rpr_1/decision", """{"status":"ok","id":"rpr_1"}""")
        val needs = NeedsYouViewModel(access, nav, FixedClock(), reports, checks)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { needs.state.collect {} }
        val proposal = needs.state.first { it.proposals.isNotEmpty() && it.canApprove }.proposals.single()
        assertTrue(needs.state.value.canChangeProposals)
        needs.changeProposal(proposal)
        val route = nav.opened.last() as FactsBookRoute.ReminderEdit
        assertEquals("rpr_1", route.proposalId)
        val vm = ReminderEditViewModel(route, access, nav, FixedClock(), checks)
        assertEquals("Gym", vm.state.first { it.draft != null }.draft!!.what)
        vm.save()
        needs.state.first { it.notice == CareNotice.ProposalEdited }
        assertTrue(access.node.bodies[access.node.asked.indexOf("POST /v1/reminder-proposals/rpr_1/decision")]!!.contains("\"edit\""))
        assertEquals(1, nav.backs)
    }

    @Test
    fun `B-FB-A01 Remind me about a thing checks the reminder with its name and area, then makes it and says so`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        access.node.on(
            "GET /v1/things/per_01",
            """{"id":"per_01","title":"Sara","kind":"person","area":"family","rev":3,"updated_at":"2026-10-10T08:00:00Z","facts":[]}""",
        )
        access.node.on("POST /v1/reminders", """{"status":"ok","id":"rem_9"}""")
        val vm = ThingViewModel(FactsBookRoute.Thing("per_01"), access, nav, checks)
        val ready = vm.state.first { it.thing != null && !it.loading }
        assertTrue(ready.canWrite)
        assertTrue(ready.canRemind)
        vm.remind()
        assertEquals(FactsBookRoute.ReminderEdit(null, fromText = "Sara", area = "family", exact = true), nav.opened.last())
        val once = kotlinx.serialization.json.Json.parseToJsonElement("""{"at":"2026-11-10T09:00"}""") as kotlinx.serialization.json.JsonObject
        val checked =
            io.github.eslamasabry.oc.engine.factsbook.ReminderPreview(
                what = "Sara", zone = "vault", locale = "en", ambiguities = emptyList(), adjustments = emptyList(), digest = "dg",
                raw = kotlinx.serialization.json.JsonObject(emptyMap()),
                schedule = io.github.eslamasabry.oc.engine.factsbook.ReminderSchedule("sch_0", io.github.eslamasabry.oc.engine.factsbook.ScheduleWhen(once), "vault", once),
            )
        checks.tell(ReminderCheckResult.Checked(checked))
        assertEquals(ThingNotice.ReminderMade, vm.state.first { it.notice != null }.notice)
        assertTrue(access.node.asked.contains("POST /v1/reminders"))
    }

    @Test
    fun `B-FB-A02 Remind me about a thing makes nothing when the check is cancelled`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        access.node.on(
            "GET /v1/things/per_01",
            """{"id":"per_01","title":"Sara","kind":"person","rev":3,"updated_at":"2026-10-10T08:00:00Z","facts":[]}""",
        )
        val vm = ThingViewModel(FactsBookRoute.Thing("per_01"), access, nav, checks)
        vm.state.first { it.thing != null }
        vm.remind()
        assertEquals(FactsBookRoute.ReminderEdit(null, fromText = "Sara", area = "general", exact = true), nav.opened.last())
        checks.tell(ReminderCheckResult.Cancelled)
        assertNull(vm.state.value.notice)
        assertTrue(access.node.asked.none { it.startsWith("POST") })
    }

    @Test
    fun `B-FB-A03 a fact or note that waits for the OK offers See Needs you`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        val thing = ThingViewModel(FactsBookRoute.Thing("per_01"), access, nav, checks)
        assertTrue(ThingNotice.FactProposed.waitsForOk)
        assertTrue(ThingNotice.ChangeProposed.waitsForOk)
        assertFalse(ThingNotice.FactAdded.waitsForOk)
        thing.seeNeedsYou()
        CaptureViewModel(access, nav, checks).seeNeedsYou()
        assertEquals(listOf<Any>(FactsBookRoute.NeedsYou, FactsBookRoute.NeedsYou), nav.opened.takeLast(2))
    }

    @Test
    fun `B-FB-A06 a fact change based on an old copy reads the thing again`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        access.node.on(
            "GET /v1/things/per_01",
            """{"id":"per_01","title":"Sara","kind":"person","rev":3,"updated_at":"2026-10-10T08:00:00Z","facts":[{"id":"f_1","thing_id":"per_01","text":"Likes tea","provenance":"told","observed_at":"2026-10-10T08:00:00Z","source":"you","rev":2}]}""",
        )
        access.node.on("PATCH /v1/facts/f_1", """{"error":{"code":"stale_approval"}}""", status = 409)
        val vm = ThingViewModel(FactsBookRoute.Thing("per_01"), access, nav, checks)
        val fact = vm.state.first { it.thing != null && !it.loading }.thing!!.facts.first()
        val before = access.node.asked.count { it == "GET /v1/things/per_01" }
        vm.editFact(fact, "Likes coffee")
        vm.state.first { it.writeError != null }
        vm.state.first { !it.loading && access.node.asked.count { a -> a == "GET /v1/things/per_01" } > before }
        assertTrue(access.node.asked.count { it == "GET /v1/things/per_01" } > before)
    }

    @Test
    fun `B-FB-A07 a new reminder starts in the app language once the screen says it`() = runTest {
        val access = FakeAccess().apply { node.healthy() }
        val vm = ReminderEditViewModel(FactsBookRoute.ReminderEdit(null), access, nav, FixedClock(), checks)
        vm.state.first { it.draft != null }
        vm.appLanguage("ar-EG")
        assertEquals("ar", vm.state.value.draft!!.locale)
        vm.edit(vm.state.value.draft!!.copy(what = "x"))
        vm.appLanguage("en")
        assertEquals("ar", vm.state.value.draft!!.locale)
    }
}
