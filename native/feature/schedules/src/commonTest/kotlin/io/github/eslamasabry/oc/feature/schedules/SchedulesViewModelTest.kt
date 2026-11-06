package io.github.eslamasabry.oc.feature.schedules

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.schedules.ArmedEntry
import io.github.eslamasabry.oc.engine.schedules.MissedRunPolicy
import io.github.eslamasabry.oc.engine.schedules.Recurrence
import io.github.eslamasabry.oc.engine.schedules.ScheduledPrompt
import io.github.eslamasabry.oc.feature.schedules.presentation.LastRunUi
import io.github.eslamasabry.oc.engine.schedules.RunStatus
import io.github.eslamasabry.oc.engine.schedules.ScheduledRun
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.engine.schedules.RunReport
import io.github.eslamasabry.oc.engine.schedules.ScheduleAlarms
import io.github.eslamasabry.oc.engine.schedules.ScheduleBackend
import io.github.eslamasabry.oc.engine.schedules.ScheduleConnection
import io.github.eslamasabry.oc.engine.schedules.ScheduleEngine
import io.github.eslamasabry.oc.engine.schedules.ScheduleNotices
import io.github.eslamasabry.oc.engine.schedules.SchedulePlatform
import io.github.eslamasabry.oc.engine.schedules.ScheduleProfiles
import io.github.eslamasabry.oc.feature.schedules.presentation.ConversationSeed
import io.github.eslamasabry.oc.feature.schedules.presentation.EditIssue
import io.github.eslamasabry.oc.feature.schedules.presentation.EditPhase
import io.github.eslamasabry.oc.feature.schedules.presentation.EditorRules
import io.github.eslamasabry.oc.feature.schedules.presentation.ModelChoice
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditViewModel
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleServers
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleTarget
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesPhase
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesViewModel
import io.github.eslamasabry.oc.feature.schedules.presentation.Unavailable
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.SchedulesRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulesViewModelTest {
    private val pid = ProfileId("p1")
    private val t0 = 1_793_606_400_000L // 2026-11-02T08:00:00Z, a Monday

    private class Memory : ScheduleBackend {
        val values = mutableMapOf<String, String>()

        override suspend fun read(key: String): String? = values[key]

        override suspend fun compareAndSet(key: String, expected: String?, value: String?): Boolean {
            if (values[key] != expected) return false
            if (value == null) values.remove(key) else values[key] = value
            return true
        }
    }

    private class Profiles : ScheduleProfiles {
        override suspend fun ids(): List<String> = listOf("p1")

        override suspend fun fingerprint(profileId: String): String? = "n1:a".takeIf { profileId == "p1" }

        override suspend fun open(profileId: String, projectPath: String, workspace: String?): Outcome<ScheduleConnection> =
            Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Network())
    }

    private class Alarms(var supported: Boolean = true) : ScheduleAlarms {
        val armed = mutableMapOf<String, List<ArmedEntry>>()
        val runNows = mutableListOf<String>()

        override fun status() = SchedulePlatform(supported = supported, notificationsAllowed = true)

        override fun replace(profileId: String, entries: List<ArmedEntry>) {
            armed[profileId] = entries
        }

        override fun runNow(profileId: String, scheduleId: String, revision: Int) {
            runNows += scheduleId
        }

        override fun cancelProfile(profileId: String) {}
    }

    private object Quiet : ScheduleNotices {
        override fun started(entry: ArmedEntry) {}

        override fun finished(entry: ArmedEntry, report: RunReport) {}

        override fun clear(entry: ArmedEntry) {}
    }

    private class Servers(var available: Boolean = true) : ScheduleServers {
        var seed: ConversationSeed? = null

        override suspend fun target(profileId: ProfileId) = ScheduleTarget("Home server", available, "/work/recent")

        override suspend fun models(profileId: ProfileId) = listOf(ModelChoice("anthropic", "claude", "Claude"))

        override suspend fun conversation(ref: ConversationRef): ConversationSeed? = seed
    }

    private class Nav : AppNavigator {
        val opened = mutableListOf<AppRoute>()
        val chats = mutableListOf<ConversationRef>()
        var backs = 0
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            opened += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            chats += ref
        }

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {
            backs++
        }
    }

    private class FixedClock(val ms: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(ms)
    }

    private val alarms = Alarms()
    private val servers = Servers()
    private val nav = Nav()
    private var ids = 0
    private val engine = ScheduleEngine(Memory(), Profiles(), alarms, Quiet, { t0 }, { TimeZone.UTC }, { "s${++ids}" })
    private val clock = FixedClock(t0)

    @BeforeTest
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    private fun list(route: SchedulesRoute.List = SchedulesRoute.List(pid)) = SchedulesViewModel(route, engine, servers, nav, clock)

    private fun editor(route: SchedulesRoute.Edit = SchedulesRoute.Edit(pid)) = ScheduleEditViewModel(route, engine, servers, nav, clock)

    private fun daily(prompt: String = "Check the nightly build") =
        io.github.eslamasabry.oc.feature.schedules.presentation.EditorFields(
            prompt = prompt,
            project = "/work/app",
            kind = RecurrenceKind.Daily, // the editor itself starts on Once (Flutter schedule_sheet.dart)
            time = "09:00",
        )

    @Test
    fun `SCREENS-NAV-98 an empty server offers one way to make a scheduled prompt`() = runTest {
        val vm = list()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(SchedulesPhase.Empty, vm.state.value.phase)
        assertEquals("Home server", vm.state.value.serverName)
        vm.create()
        assertEquals(SchedulesRoute.Edit(pid, null, null, null), nav.opened.single())
    }

    @Test
    fun `SCREENS-NAV-98 a server that cannot schedule shows a plain reason, not the list`() = runTest {
        servers.available = false
        val vm = list()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(SchedulesPhase.Unavailable, vm.state.value.phase)
        assertEquals(Unavailable.Server, vm.state.value.unavailable)
        assertTrue(alarms.armed.isEmpty())
    }

    @Test
    fun `B-STATE-089 a phone that cannot schedule says so`() = runTest {
        alarms.supported = false
        val vm = list()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(Unavailable.Phone, vm.state.value.unavailable)
    }

    @Test
    fun `B-STATE-089 save from the editor arms it and the list shows it with its next time`() = runTest {
        val edit = editor()
        assertEquals(EditPhase.Ready, edit.state.value.phase)
        assertEquals("/work/recent", edit.state.value.fields.project) // the newest conversation's project
        edit.save(daily())
        assertEquals(1, nav.backs)
        assertEquals(1, alarms.armed["p1"]!!.size)
        val vm = list()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val row = vm.state.value.rows.single()
        assertEquals("Check the nightly build", row.prompt)
        assertEquals(t0 + 3_600_000L, row.nextRunAtMs)
        assertEquals(SchedulesPhase.List, vm.state.value.phase)
    }

    @Test
    fun `B-STATE-089 pause, run now and delete go through the engine and confirm before deleting`() = runTest {
        editor().save(daily())
        val vm = list()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val id = vm.state.value.rows.single().id
        vm.setEnabled(id, false)
        assertTrue(alarms.armed["p1"]!!.isEmpty())
        vm.runNow(id)
        assertEquals(listOf(id), alarms.runNows)
        assertEquals(id, vm.state.value.started)
        vm.askDelete(id)
        assertEquals(id, vm.state.value.confirmDelete)
        vm.cancelDelete()
        assertEquals(1, vm.state.value.rows.size)
        vm.askDelete(id)
        vm.confirmDelete()
        assertEquals(SchedulesPhase.Empty, vm.state.value.phase)
    }

    @Test
    fun `B-NAV-055 a schedule made from a conversation keeps that conversation and its model`() = runTest {
        val ref = ConversationRef(pid, SessionId("ses_7"))
        servers.seed = ConversationSeed("/work/app", "Fix login", "anthropic", "claude", "build")
        val edit = editor(SchedulesRoute.Edit(pid, from = ref))
        assertTrue(edit.state.value.continuesConversation)
        assertEquals("Fix login", edit.state.value.conversationTitle)
        assertEquals("claude", edit.state.value.fields.model?.modelId)
        edit.save(edit.state.value.fields.copy(prompt = "Run the tests", time = "18:00"))
        val saved = engine.store.load("p1").single()
        assertEquals("ses_7", saved.sessionId)
        assertEquals("anthropic", saved.modelProviderId)
        assertEquals("build", saved.agent)
    }

    @Test
    fun `editing keeps the id and moves to the next revision`() = runTest {
        editor().save(daily())
        val id = engine.store.load("p1").single().id
        val edit = editor(SchedulesRoute.Edit(pid, id))
        assertTrue(edit.state.value.editing)
        assertEquals("09:00", edit.state.value.fields.time)
        edit.save(edit.state.value.fields.copy(kind = RecurrenceKind.Weekdays, time = "07:15", missed = MissedRunPolicy.Skip))
        val saved = engine.store.load("p1").single()
        assertEquals(id, saved.id)
        assertEquals(2, saved.revision)
        assertEquals(RecurrenceKind.Weekdays, saved.recurrence.kind)
        assertEquals(MissedRunPolicy.Skip, saved.missedPolicy)
    }

    @Test
    fun `B-NAV-055 the editor shows the last run and what to do about its failure`() = runTest {
        editor().save(daily())
        val id = engine.store.load("p1").single().id
        engine.store.recordRun(
            "p1",
            ScheduledRun("r1", id, t0, t0, RunStatus.Failed, finishedAtMs = t0 + 1_000, errorCode = "needs_you"),
        )
        val edit = editor(SchedulesRoute.Edit(pid, id))
        assertEquals(LastRunUi(RunStatus.Failed, "needs_you", t0 + 1_000, null, false), edit.state.value.lastRun)
        assertNull(editor().state.value.lastRun) // a new schedule has no history
    }

    @Test
    fun `B-STATE-024 typed editor fields survive recreation and the prompt comes back after leaving`() = runTest {
        val edit = editor()
        val seed = edit.state.value.seed
        edit.edited(daily("Summarise the open issues").copy(time = "07:15"))
        // Rotation: the screen is rebuilt from the view model's fields, with the same seed.
        assertEquals(seed, edit.state.value.seed)
        assertEquals("Summarise the open issues", edit.state.value.fields.prompt)
        assertEquals("07:15", edit.state.value.fields.time)
        // Leaving the editor and opening it again: the typed prompt is back.
        val again = editor()
        assertEquals("Summarise the open issues", again.state.value.fields.prompt)
        // Saved: the draft is forgotten.
        again.save(daily("Summarise the open issues"))
        assertEquals("", editor().state.value.fields.prompt)
    }

    @Test
    fun `the editor names the one thing to fix and keeps what was typed`() = runTest {
        val edit = editor()
        edit.save(daily(prompt = " "))
        assertEquals(EditIssue.PromptRequired, edit.state.value.issue)
        edit.save(daily().copy(time = "9h"))
        assertEquals(EditIssue.TimeInvalid, edit.state.value.issue)
        // A once time already past is caught before the engine, as Flutter's schedule_sheet.dart `save` does.
        val past = daily().copy(kind = RecurrenceKind.Once, date = "2026-11-01")
        edit.edited(past)
        edit.save(past)
        assertEquals(EditIssue.FutureRequired, edit.state.value.issue)
        assertNull(edit.state.value.error)
        assertEquals("2026-11-01", edit.state.value.fields.date)
        assertEquals(0, nav.backs)
        assertNull(alarms.armed["p1"]?.firstOrNull())
    }

    @Test
    fun `B-NAV-055 Send later opens on the next valid slot, never a time already past`() = runTest {
        servers.seed = ConversationSeed("/work/app", "Fix login", null, null, null)
        val ref = ConversationRef(pid, SessionId("ses_7"))
        // Before nine and after nine (any device zone): the seeded day and time are ahead of now.
        for (now in listOf(t0, t0 + 3 * 3_600_000L, t0 + 15 * 3_600_000L)) {
            val edit = ScheduleEditViewModel(SchedulesRoute.Edit(pid, from = ref), engine, servers, nav, FixedClock(now))
            val fields = edit.state.value.fields
            assertEquals(RecurrenceKind.Once, fields.kind)
            assertEquals("09:00", fields.time)
            val (rec, issue) = EditorRules.recurrence(fields.copy(prompt = "Ship it"), now)
            assertNull(issue)
            assertTrue(rec!!.atMs!! > now, "seeded ${fields.date} ${fields.time} is not after $now")
            assertTrue(rec.atMs!! - now <= 24 * 3_600_000L, "seeded slot is more than a day away")
        }
    }

    @Test
    fun `B-NAV-055 an expired once schedule and a repeating one switched to Once reopen on a time still ahead`() {
        val utc = "UTC"
        val base =
            ScheduledPrompt(
                id = "a1",
                profileId = "p1",
                profileFingerprint = "n1:a",
                prompt = "Check the build",
                projectPath = "/work/app",
                recurrence = Recurrence.once(t0 - 2 * 3_600_000L), // 06:00 today, already gone
                createdAtMs = t0 - 86_400_000L,
            )
        val expired = EditorRules.fields(base, emptyList(), utc, nowMs = t0)
        assertEquals("06:00", expired.time)
        assertEquals("2026-11-03", expired.date) // today's 06:00 has passed: tomorrow
        assertEquals(RecurrenceKind.Once, EditorRules.recurrence(expired, t0, utc).first?.kind)
        // Earlier today but later than now keeps today.
        val later = EditorRules.fields(base.copy(recurrence = Recurrence.once(t0 + 3_600_000L)), emptyList(), utc, nowMs = t0)
        assertEquals("2026-11-02", later.date)
        // A daily 07:30 switched to Once starts on the next day 07:30 is ahead, not on a blank date.
        val daily = EditorRules.fields(base.copy(recurrence = Recurrence.daily(7, 30)), emptyList(), utc, nowMs = t0)
        assertEquals("2026-11-03", daily.date)
        val (rec, issue) = EditorRules.recurrence(daily.copy(kind = RecurrenceKind.Once), t0, utc)
        assertNull(issue)
        assertTrue(rec!!.atMs!! > t0)
    }

    @Test
    fun `B-NAV-054 a run's conversation opens from its history row`() = runTest {
        val vm = list()
        vm.openConversation("ses_9")
        assertEquals(ConversationRef(pid, SessionId("ses_9")), nav.chats.single())
    }
}
