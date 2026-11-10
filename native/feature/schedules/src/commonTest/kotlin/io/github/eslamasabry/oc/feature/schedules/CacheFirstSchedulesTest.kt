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

import io.github.eslamasabry.oc.feature.schedules.presentation.EditorFields
import io.github.eslamasabry.oc.feature.schedules.presentation.KeptScheduleEditor
import io.github.eslamasabry.oc.feature.schedules.presentation.KeptSchedules
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditorLastKnown
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesLastKnown
import kotlinx.serialization.json.Json
@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstSchedulesTest {
    private val pid = ProfileId("p1")
    private val t0 = 1_793_606_400_000L

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

        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun target(profileId: ProfileId): ScheduleTarget {
            gate?.await()
            return live()
        }

        private fun live() = ScheduleTarget("Home server", available, "/work/recent")

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

    private class Shelf : SchedulesLastKnown {
        var list: KeptSchedules? = null
        override fun peek(profile: ProfileId) = list
        override suspend fun read(profile: ProfileId) = list
        override suspend fun keep(profile: ProfileId, kept: KeptSchedules) {
            list = Json.decodeFromString(KeptSchedules.serializer(), Json.encodeToString(KeptSchedules.serializer(), kept))
        }
    }

    private class EditShelf : ScheduleEditorLastKnown {
        val items = mutableMapOf<String, KeptScheduleEditor>()
        override fun peek(profile: ProfileId, item: String) = items[item]
        override suspend fun read(profile: ProfileId, item: String) = items[item]
        override suspend fun keep(profile: ProfileId, item: String, kept: KeptScheduleEditor) {
            items[item] = Json.decodeFromString(KeptScheduleEditor.serializer(), Json.encodeToString(KeptScheduleEditor.serializer(), kept))
        }
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

    private val kept =
        KeptSchedules(
            serverName = "Home server",
            rows = listOf(KeptSchedules.Row("k1", "Check the nightly build", RecurrenceKind.Daily.wire, hour = 9, nextRunAtMs = t0, enabled = true, continues = false)),
        )

    @Test
    fun `CF-70 the first frame with kept schedules is the list, not a loading page`() = runTest {
        val shelf = Shelf().also { it.list = kept }
        servers.gate = kotlinx.coroutines.CompletableDeferred()
        val vm = SchedulesViewModel(SchedulesRoute.List(pid), engine, servers, nav, clock, setOf(shelf))
        val first = vm.state.value
        assertEquals(SchedulesPhase.List, first.phase)
        assertEquals("Check the nightly build", first.rows.single().prompt)
        assertEquals("Home server", first.serverName)
        assertTrue(first.updating)
    }

    @Test
    fun `CF-71 the live list replaces the kept rows in place and is kept for next time`() = runTest {
        val edit = ScheduleEditViewModel(SchedulesRoute.Edit(pid), engine, servers, nav, clock)
        edit.save(EditorFields(prompt = "Live prompt", project = "/work/app", kind = RecurrenceKind.Daily, time = "09:00"))
        val shelf = Shelf().also { it.list = kept }
        val vm = SchedulesViewModel(SchedulesRoute.List(pid), engine, servers, nav, clock, setOf(shelf))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(listOf("Live prompt"), vm.state.value.rows.map { it.prompt })
        assertEquals(false, vm.state.value.updating)
        assertEquals(listOf("Live prompt"), shelf.list!!.rows.map { it.prompt })
    }

    @Test
    fun `CF-72 the first frame of the editor with kept facts is the form, ready`() = runTest {
        val shelf = EditShelf()
        shelf.items[KeptScheduleEditor.NEW] =
            KeptScheduleEditor(project = "/work/kept", models = listOf(KeptScheduleEditor.Model("anthropic", "claude", "Claude")))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        servers.gate = gate
        val vm = ScheduleEditViewModel(SchedulesRoute.Edit(pid), engine, servers, nav, clock, setOf(shelf))
        // Before the live read finishes the state is the kept form; afterwards the live project replaces it.
        assertEquals(EditPhase.Ready, vm.state.value.phase)
        assertEquals("/work/kept", vm.state.value.fields.project)
        assertEquals(listOf("claude"), vm.state.value.models.map { it.modelId })
        gate.complete(Unit)
        assertEquals("/work/recent", vm.state.value.fields.project)
        assertEquals("/work/recent", shelf.items[KeptScheduleEditor.NEW]!!.project)
    }

    @Test
    fun `CF-73 with nothing kept the first-ever load is loading`() = runTest {
        val vm = SchedulesViewModel(SchedulesRoute.List(pid), engine, servers, nav, clock, setOf(Shelf()))
        assertEquals(SchedulesPhase.Loading, vm.state.value.phase)
    }
}
