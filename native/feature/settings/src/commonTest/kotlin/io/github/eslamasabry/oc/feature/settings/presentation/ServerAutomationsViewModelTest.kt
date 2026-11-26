package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.AutomationAction
import io.github.eslamasabry.oc.core.model.AutomationOutcome
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.AutomationTrigger
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.model.TerminalShellSettings
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ServerSettingsPage
import io.github.eslamasabry.oc.ui.routes.ServerSettingsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ServerAutomationsViewModelTest {
    private val pid = ProfileId("srv_1")
    private val nowMs = 1_795_676_400_000L

    private val fixedClock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeNav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)
        val opened = mutableListOf<AppRoute>()

        override fun open(route: AppRoute) {
            opened += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() = Unit
    }

    private class FakePrefs : ServerPreferencesSource {
        override fun watch(profile: ProfileId): Flow<Outcome<ServerLocalFacts>> = flowOf()

        override suspend fun models(profile: ProfileId): Outcome<ImmutableList<ServerModelChoice>> =
            Outcome.Ok(persistentListOf())

        override suspend fun selectModel(
            profile: ProfileId,
            model: ModelRef?,
            variant: String,
        ): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun watching(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
            Outcome.Ok(Unit)

        override suspend fun notifications(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
            Outcome.Ok(Unit)

        override suspend fun shareRead(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
            Outcome.Ok(Unit)

        override suspend fun reset(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)
    }

    private class FakeSource(
        initialFacts: ServerSettingsFacts,
        var listResult: Outcome<ImmutableList<ServerAutomation>> = Outcome.Ok(persistentListOf()),
        val failPauseIds: MutableSet<String> = mutableSetOf(),
        val notFoundPauseIds: MutableSet<String> = mutableSetOf(),
        val failDeleteIds: MutableSet<String> = mutableSetOf(),
        val notFoundDeleteIds: MutableSet<String> = mutableSetOf(),
    ) : ServerSettingsSource, ServerAutomationsSource {
        val factsFlow = MutableStateFlow<ServerSettingsFacts?>(initialFacts)
        var listCalls = 0
        var nextListDeferred: CompletableDeferred<Outcome<ImmutableList<ServerAutomation>>>? = null
        var nextPauseDeferred: CompletableDeferred<Outcome<ServerAutomation>>? = null
        val pausedIds = mutableListOf<String>()
        val resumedIds = mutableListOf<String>()
        val deletedIds = mutableListOf<String>()

        override fun watch(profile: ProfileId): Flow<ServerSettingsFacts?> = factsFlow

        override suspend fun health(profile: ProfileId): Outcome<Health> =
            Outcome.Ok(Health(true, "2.4.1"))

        override suspend fun check(profile: ProfileId): Outcome<ImmutableList<ConnectionResult>> =
            Outcome.Ok(persistentListOf())

        override suspend fun shells(profile: ProfileId): Outcome<TerminalShellSettings> =
            Outcome.Failed(Failure.Unavailable(FeatureId("shellSettings")))

        override suspend fun selectShell(
            profile: ProfileId,
            selected: String,
        ): Outcome<TerminalShellSettings> =
            Outcome.Failed(Failure.Unavailable(FeatureId("shellSettings")))

        override suspend fun connect(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun disconnect(profile: ProfileId): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun recheckAgents(profile: ProfileId): Outcome<ImmutableList<String>> =
            Outcome.Ok(persistentListOf())

        override suspend fun automations(
            profile: ProfileId
        ): Outcome<ImmutableList<ServerAutomation>> {
            listCalls++
            val deferred = nextListDeferred
            if (deferred != null) {
                nextListDeferred = null
                return deferred.await()
            }
            return listResult
        }

        override suspend fun pauseAutomation(
            profile: ProfileId,
            id: String,
        ): Outcome<ServerAutomation> {
            pausedIds += id
            val deferred = nextPauseDeferred
            if (deferred != null) {
                nextPauseDeferred = null
                return deferred.await()
            }
            if (id in notFoundPauseIds) return Outcome.Failed(Failure.NotFound())
            if (id in failPauseIds) return Outcome.Failed(Failure.Network())
            val found =
                (listResult as? Outcome.Ok)?.value?.first { it.id == id }
                    ?: error("Missing $id")
            return Outcome.Ok(found.copy(status = AutomationStatus.Paused, nextDueAtMs = null))
        }

        override suspend fun resumeAutomation(
            profile: ProfileId,
            id: String,
        ): Outcome<ServerAutomation> {
            resumedIds += id
            val found =
                (listResult as? Outcome.Ok)?.value?.first { it.id == id }
                    ?: error("Missing $id")
            return Outcome.Ok(found.copy(status = AutomationStatus.Active))
        }

        override suspend fun deleteAutomation(profile: ProfileId, id: String): Outcome<Unit> {
            deletedIds += id
            if (id in notFoundDeleteIds) return Outcome.Failed(Failure.NotFound())
            if (id in failDeleteIds) return Outcome.Failed(Failure.Network())
            return Outcome.Ok(Unit)
        }
    }

    private fun facts(supportsAutomations: Boolean) =
        ServerSettingsFacts(
            name = "Laptop",
            address = "https://laptop.example:4096",
            connected = true,
            hasSecret = true,
            lastVersion = if (supportsAutomations) "2.4.1" else "2.3.2",
            canCheck = true,
            capabilities =
                ServerCapabilities(
                    scheduledPrompts = true,
                    serverAutomations = supportsAutomations,
                ),
        )

    private fun sampleAutomations() =
        listOf(
                ServerAutomation(
                    id = "aut_paused",
                    name = "Release summary",
                    status = AutomationStatus.Paused,
                    trigger = AutomationTrigger.Event(server = "github", event = "release"),
                    action = AutomationAction.Prompt("Write notes", subagent = "writer"),
                    timeZone = "Europe/Berlin",
                ),
                ServerAutomation(
                    id = "aut_later",
                    name = "Nightly cleanup",
                    status = AutomationStatus.Active,
                    trigger = AutomationTrigger.Cron("0 3 * * *", timeZone = "Europe/Berlin"),
                    action = AutomationAction.Shell("git gc"),
                    timeZone = "Europe/Berlin",
                    nextDueAtMs = nowMs + 7_200_000L,
                    lastRunAtMs = nowMs - 86_400_000L,
                    lastOutcome = AutomationOutcome.Failed,
                ),
                ServerAutomation(
                    id = "aut_soon",
                    name = "CI check",
                    status = AutomationStatus.Active,
                    trigger = AutomationTrigger.Every("1h"),
                    action = AutomationAction.Prompt("Check CI"),
                    timeZone = "Europe/Berlin",
                    nextDueAtMs = nowMs + 3_600_000L,
                    lastRunAtMs = nowMs - 3_600_000L,
                    lastOutcome = AutomationOutcome.Fired,
                ),
            )
            .toImmutableList()

    private fun createVm(source: FakeSource, nav: FakeNav = FakeNav()) =
        ServerSettingsViewModel(
            ServerSettingsRoute(pid, ServerSettingsPage.Home),
            source,
            source,
            FakePrefs(),
            nav,
            fixedClock,
        )

    @Test
    fun `B-AUTO-009 unsupported server never loads automations and hides section`() = runTest {
        val source = FakeSource(facts(supportsAutomations = false))
        val vm = createVm(source)
        assertEquals(0, source.listCalls)
        assertFalse(vm.state.value.showAutomations)
        assertTrue(vm.state.value.automations.isEmpty())
    }

    @Test
    fun `B-AUTO-010 loads sorted automations, pauses and resumes`() = runTest {
        val source = FakeSource(facts(supportsAutomations = true), Outcome.Ok(sampleAutomations()))
        val vm = createVm(source)

        assertTrue(vm.state.value.showAutomations)
        assertEquals(
            listOf("aut_soon", "aut_later", "aut_paused"),
            vm.state.value.automations.map { it.id },
        )
        assertEquals(2, vm.state.value.activeAutomationsCount)

        vm.pauseAutomation("aut_soon")
        assertEquals(listOf("aut_soon"), source.pausedIds)
        assertEquals(
            AutomationStatus.Paused,
            vm.state.value.automations.first { it.id == "aut_soon" }.status,
        )

        vm.resumeAutomation("aut_paused")
        assertEquals(listOf("aut_paused"), source.resumedIds)
        assertEquals(
            AutomationStatus.Active,
            vm.state.value.automations.first { it.id == "aut_paused" }.status,
        )
    }

    @Test
    fun `B-AUTO-011 delete asks confirmation first and removes automation only on confirm`() =
        runTest {
            val source =
                FakeSource(facts(supportsAutomations = true), Outcome.Ok(sampleAutomations()))
            val vm = createVm(source)

            vm.openAutomation("aut_later")
            assertEquals("aut_later", vm.state.value.selectedAutomation?.id)

            vm.askDeleteAutomation("aut_later")
            assertEquals("aut_later", vm.state.value.deleteAutomationTarget?.id)
            assertTrue(source.deletedIds.isEmpty())

            vm.cancelDeleteAutomation()
            assertNull(vm.state.value.deleteAutomationTarget)
            assertTrue(source.deletedIds.isEmpty())

            vm.askDeleteAutomation("aut_later")
            vm.confirmDeleteAutomation()
            assertEquals(listOf("aut_later"), source.deletedIds)
            assertFalse(vm.state.value.automations.any { it.id == "aut_later" })
            assertNull(vm.state.value.selectedAutomation)
        }

    @Test
    fun `B-AUTO-012 pause all confirms first, pauses only active automations, and reports count`() =
        runTest {
            val source =
                FakeSource(facts(supportsAutomations = true), Outcome.Ok(sampleAutomations()))
            val vm = createVm(source)

            vm.askStopAllAutomations()
            assertTrue(vm.state.value.stopAllAutomationsConfirm)
            assertTrue(source.pausedIds.isEmpty())

            vm.confirmStopAllAutomations()
            assertFalse(vm.state.value.stopAllAutomationsConfirm)
            assertEquals(listOf("aut_soon", "aut_later"), source.pausedIds)
            assertEquals(0, vm.state.value.activeAutomationsCount)
            assertEquals(AutomationsNotice.StoppedAll(2), vm.state.value.automationsNotice)
        }

    @Test
    fun `B-AUTO-012 partial pause all reports exact paused out of total`() = runTest {
        val source =
            FakeSource(
                facts(supportsAutomations = true),
                Outcome.Ok(sampleAutomations()),
                failPauseIds = mutableSetOf("aut_later"),
            )
        val vm = createVm(source)

        vm.askStopAllAutomations()
        vm.confirmStopAllAutomations()
        assertEquals(AutomationsNotice.StoppedPartial(1, 2), vm.state.value.automationsNotice)
        assertEquals(1, vm.state.value.activeAutomationsCount)
    }

    @Test
    fun `B-AUTO-013 unavailable from 403 disabled hides automations cleanly while network error offers retry`() =
        runTest {
            val unavailable =
                FakeSource(
                    facts(supportsAutomations = true),
                    Outcome.Failed(Failure.Unavailable(FeatureId("serverAutomations"))),
                )
            val vmHidden = createVm(unavailable)
            assertFalse(vmHidden.state.value.showAutomations)
            assertTrue(vmHidden.state.value.automationsHidden)
            assertFalse(vmHidden.state.value.automationsLoadFailed)

            val failing =
                FakeSource(facts(supportsAutomations = true), Outcome.Failed(Failure.Network()))
            val vmFailed = createVm(failing)
            assertTrue(vmFailed.state.value.showAutomations)
            assertTrue(vmFailed.state.value.automationsLoadFailed)
        }

    @Test
    fun `B-AUTO-021 failed pause or delete while sheet is open surfaces error inside the open sheet`() =
        runTest {
            val source =
                FakeSource(
                    facts(supportsAutomations = true),
                    Outcome.Ok(sampleAutomations()),
                    failPauseIds = mutableSetOf("aut_later"),
                    failDeleteIds = mutableSetOf("aut_later"),
                )
            val vm = createVm(source)

            vm.openAutomation("aut_later")
            assertFalse(vm.state.value.automationSheetFailed)

            vm.pauseAutomation("aut_later")
            assertEquals("aut_later", vm.state.value.selectedAutomationId)
            assertTrue(vm.state.value.automationSheetFailed)
            assertNull(vm.state.value.automationsNotice)

            vm.askDeleteAutomation("aut_later")
            vm.confirmDeleteAutomation()
            assertEquals("aut_later", vm.state.value.selectedAutomationId)
            assertTrue(vm.state.value.automationSheetFailed)

            vm.closeAutomation()
            assertNull(vm.state.value.selectedAutomationId)
            assertFalse(vm.state.value.automationSheetFailed)
        }

    @Test
    fun `B-AUTO-022 in-flight refresh does not overwrite a mutation and refresh skips while working`() =
        runTest {
            val initial = sampleAutomations()
            val source = FakeSource(facts(supportsAutomations = true), Outcome.Ok(initial))
            val vm = createVm(source)
            assertEquals(1, source.listCalls)

            // Start a slow refresh before pausing aut_soon.
            val staleRefresh = CompletableDeferred<Outcome<ImmutableList<ServerAutomation>>>()
            source.nextListDeferred = staleRefresh
            vm.refreshAutomations()
            assertEquals(2, source.listCalls)
            assertTrue(vm.state.value.automationsLoading)

            // Pause completes while stale refresh is still in flight.
            vm.pauseAutomation("aut_soon")
            assertEquals(
                AutomationStatus.Paused,
                vm.state.value.automations.first { it.id == "aut_soon" }.status,
            )

            // Stale refresh returns the old Active list; it must NOT revert aut_soon to Active.
            staleRefresh.complete(Outcome.Ok(initial))
            assertFalse(vm.state.value.automationsLoading)
            assertEquals(
                AutomationStatus.Paused,
                vm.state.value.automations.first { it.id == "aut_soon" }.status,
            )

            // Refresh called while a mutation is in flight must be skipped.
            val slowPause = CompletableDeferred<Outcome<ServerAutomation>>()
            source.nextPauseDeferred = slowPause
            vm.pauseAutomation("aut_later")
            assertTrue(vm.state.value.automationsWorking)
            val callsBefore = source.listCalls
            vm.refreshAutomations()
            assertEquals(callsBefore, source.listCalls)
            slowPause.complete(
                Outcome.Ok(
                    initial.first { it.id == "aut_later" }.copy(status = AutomationStatus.Paused)
                )
            )
            assertFalse(vm.state.value.automationsWorking)
        }

    @Test
    fun `B-AUTO-023 404 on pause or delete removes stale row, closes sheet, and refreshes list`() =
        runTest {
            val remaining = sampleAutomations().filterNot { it.id == "aut_later" }.toImmutableList()
            val source =
                FakeSource(
                    facts(supportsAutomations = true),
                    Outcome.Ok(sampleAutomations()),
                    notFoundPauseIds = mutableSetOf("aut_later"),
                )
            val vm = createVm(source)
            assertEquals(1, source.listCalls)

            vm.openAutomation("aut_later")
            source.listResult = Outcome.Ok(remaining)
            vm.pauseAutomation("aut_later")

            assertEquals(2, source.listCalls)
            assertNull(vm.state.value.selectedAutomationId)
            assertFalse(vm.state.value.automationSheetFailed)
            assertFalse(vm.state.value.automations.any { it.id == "aut_later" })
        }
}
