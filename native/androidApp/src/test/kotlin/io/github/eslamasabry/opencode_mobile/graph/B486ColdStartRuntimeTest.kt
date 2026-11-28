package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Channel
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import io.github.eslamasabry.oc.core.data.cache.LastKnown
import io.github.eslamasabry.oc.core.data.cache.ServerMark
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.TransientOwners
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.runtime.server.LocalServerWait
import io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * B-486, the real start wiring: [PreviewRuntime.restoreSavedConnection] and its list sources over the real stores,
 * registry and helper starter. Only the servers are missing: every connection ends "the computer did not answer", and
 * the phone has no Linux, so a helper start is refused.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class B486ColdStartRuntimeTest {
    private val computer = ProfileId("computer")
    private val thisPhone = ProfileId("phone")
    private val helper = ProfileId("phone-agents")

    @Test
    fun `B-486 the Phone agents source is adopted only after the primary connection`() = runTest {
        val answer = CompletableDeferred<Unit>()
        val world = World(testScheduler, NoAnswer(answer))
        world.save(StoredProfile(computer, "Computer", COMPUTER_URL), active = true)

        world.runtime.restoreSavedConnection()
        runCurrent()
        assertEquals(1, world.gateway.connects, "the primary connection is being opened")
        assertEquals(0, world.phone.recalls, "adoption must wait for the primary connection")

        answer.complete(Unit)
        runCurrent()
        assertEquals(1, world.phone.recalls, "adoption follows the primary connection")
        world.close()
    }

    @Test
    fun `B-486 an adoption that failed before the activity resumed is tried again at the first resume`() = runTest {
        // First run: an agent is installed and no Phone agents source is saved yet, so adoption starts the helper.
        val world = World(testScheduler, NoAnswer(), FakePhone(oneAgentInstalled()))

        world.runtime.restoreSavedConnection()
        runCurrent()
        assertEquals(1, world.phone.recalls, "adoption ran once, before any activity resumed, and failed")

        world.gates.activityResumed.value = true
        runCurrent()
        assertEquals(2, world.phone.recalls, "the first resume must try the adoption again")
        world.close()
    }

    @Test
    fun `B-486 an adoption that failed with the activity resumed is not repeated`() = runTest {
        val world = World(testScheduler, NoAnswer(), FakePhone(oneAgentInstalled()))
        world.gates.activityResumed.value = true

        world.runtime.restoreSavedConnection()
        runCurrent()
        world.gates.activityResumed.value = false
        runCurrent()
        world.gates.activityResumed.value = true
        runCurrent()
        assertEquals(1, world.phone.recalls)
        world.close()
    }

    @Test
    fun `B-486 an offline computer the app does not start shows no saved chats`() = runTest {
        val world = World(testScheduler, NoAnswer())
        world.save(StoredProfile(computer, "Computer", COMPUTER_URL), active = false)
        world.keepOneChat(computer)

        world.runtime.showListSource(computer, shown = true)
        runCurrent()
        assertTrue(computer in world.runtime.sourceFailures, "the failure is named on the computer's own row")
        assertEquals(emptyList(), world.rows(computer), "saved chats stand in only for a source the app starts")
        world.close()
    }

    @Test
    fun `B-486 the server in use keeps its saved chats on show when its connection fails at cold start`() = runTest {
        val answer = CompletableDeferred<Unit>()
        val world = World(testScheduler, NoAnswer(answer))
        world.save(StoredProfile(computer, "Computer", COMPUTER_URL), active = true)
        world.keepOneChat(computer)

        world.runtime.restoreSavedConnection()
        runCurrent()
        assertEquals(listOf(SAVED_CHAT), world.rows(computer), "the saved chats show while it connects")

        answer.complete(Unit)
        runCurrent()
        assertTrue(computer in world.runtime.sourceFailures)
        assertEquals(listOf(SAVED_CHAT), world.rows(computer), "and stay when the connection fails")
        world.close()
    }

    @Test
    fun `B-486 the agents helper is started while the phone server is still starting`() = runTest {
        val world = World(testScheduler, NoAnswer())
        val phoneAnswers = CompletableDeferred<Unit>()
        world.gates.gate = {
            phoneAnswers.await()
            LocalServerWait.Ready
        }
        world.save(StoredProfile(thisPhone, "This phone", ManagedLocalServer.BUILTIN_URL), active = true)
        world.saveHelper(helper, owner = thisPhone.raw)

        world.runtime.restoreSavedConnection()
        runCurrent()
        assertEquals(0, world.gateway.connects, "the phone server has not answered yet")
        assertTrue(world.phone.stateReads > 0, "the helper's start must not wait behind the phone server")
        world.close()
    }

    private fun oneAgentInstalled(): PhoneAgentsState =
        PhoneAgentsState(
            available = true,
            agents = persistentListOf(PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready)),
        )

    /** Every server is offline: a connection waits for [hold] (when there is one), then ends unanswered. */
    private class NoAnswer(private val hold: CompletableDeferred<Unit>? = null) : GatewayFactory {
        override val backend: BackendKind = BackendKind.OpenCode2
        var connects = 0

        override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> =
            Outcome.Failed(Failure.ComputerDidNotAnswer())

        override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> {
            connects++
            hold?.await()
            return Outcome.Failed(Failure.ComputerDidNotAnswer())
        }
    }

    /**
     * The phone's agents as the helper starter reads them. [recalls] counts the adoptions that ran; a helper start
     * reads [state] before anything else, so [stateReads] says whether one was asked for.
     */
    private class FakePhone(initial: PhoneAgentsState = PhoneAgentsState()) : PhoneAgents {
        private val flow = MutableStateFlow(initial)
        var recalls = 0
        var stateReads = 0

        override val state: StateFlow<PhoneAgentsState>
            get() {
                stateReads++
                return flow
            }

        override suspend fun recall() {
            recalls++
        }
    }

    /** The real runtime over the real stores of a phone with no Linux and no server that answers. */
    private class World(
        scheduler: TestCoroutineScheduler,
        val gateway: NoAnswer,
        val phone: FakePhone = FakePhone(),
    ) {
        private val app: Application = RuntimeEnvironment.getApplication()
        private val dispatchers = TestDispatchers(StandardTestDispatcher(scheduler))
        private val redactor = Redactor()
        private val clock = TestClock()
        private val scope = AppCoroutineScope(SupervisorJob() + dispatchers.default)
        private val build = AppBuildInfo("3.0.3", 10140, Channel.Preview, debuggable = false)
        private val migration = MigrationWiring(app, build, redactor, scope, dispatchers, clock)
        private val gate = StateMigrationGate.alwaysOpen()
        private val stores = PreviewStores(app, dispatchers, redactor, gate, migration, build)
        private val registry =
            ConnectionRegistry(
                setOf(gateway),
                ConnectionGraph.Factory { _, _, _ -> error("no connection opens in this test") },
                scope,
                dispatchers,
            )
        private val inventory = NativeSessionInventory(stores, redactor, clock)
        private val stages = LaunchStages(scope, lazy<PhoneServerSource> { error("no phone server in this test") })
        private lateinit var starter: AppLocalAgentHostStarter
        val gates = LocalServerGates()
        val runtime =
            PreviewRuntime(
                application = app,
                stores = stores,
                registry = registry,
                profileScope = stores.profileScope,
                sessionReads = NativeSessionReads(stores, registry, ResumedActivities(app), scope, dispatchers),
                redactor = redactor,
                appScope = scope,
                dispatchers = dispatchers,
                draftOwners = emptySet(),
                runtimeOwner = RuntimeProfileOwner(stores, PlatformCapabilities.android()),
                inventory = inventory,
                cleaners = emptySet(),
                deliveryStores = emptySet(),
                profileClosers = emptySet(),
                clock = clock,
                localGates = gates,
                phoneHelper = lazy { starter },
                visibility = AppVisibility(app),
                stages = stages,
            )

        init {
            val payloads = PhonePayloadGuard(app, stores, dispatchers, TransientOwners(), redactor, NoLastKnown())
            val paseo = AppPaseoGatewayFactory(dispatchers, clock, stores.profiles, stores, payloads)
            starter = AppLocalAgentHostStarter(app, stores, dispatchers, redactor, phone, runtime, paseo, stages)
        }

        suspend fun save(profile: StoredProfile, active: Boolean) {
            check(stores.warm() is Outcome.Ok)
            check(stores.profiles.upsert(profile) is Outcome.Ok)
            if (active) check(stores.profiles.setActive(profile.id) is Outcome.Ok)
        }

        /** The saved "Phone agents" source of the phone [owner]: `<owner>-agents`, as the helper starter saves it. */
        suspend fun saveHelper(id: ProfileId, owner: String) {
            val profile =
                StoredProfile(id, "This phone", HELPER_URL, backend = BackendKind.Paseo, codexDirectory = HELPER_FOLDER)
            save(profile, active = false)
            val saved =
                stores.prefs.commit(stores.lanes.app) {
                    putString("oc.builtinServerOwner", owner)
                    putString("oc.phoneAgentOwner.${id.raw}", owner)
                }
            check(saved is Outcome.Ok)
        }

        /** One conversation of [id] kept from the last run, as the list saves it. */
        suspend fun keepOneChat(id: ProfileId) {
            val profile = checkNotNull(stores.profiles.profile(id)).profile
            val chat = Session(id = SessionId(SAVED_CHAT), title = "Saved chat", time = SessionTime(updatedMs = 1_000))
            inventory.save(profile, listOf(chat)) { true }
            check(inventory.read(profile).size == 1)
        }

        fun rows(id: ProfileId): List<String> =
            runtime.snapshot.value.items.filter { it.profileId == id }.map { it.sessionId.raw }

        fun close() {
            scope.cancel()
        }
    }

    /** Nothing is kept from an earlier run in this test; the payload guard only needs the seam to exist. */
    private class NoLastKnown : LastKnown {
        override fun <T> peek(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<T>, item: String): T? = null

        override suspend fun <T> read(
            profile: ProfileId,
            mark: ServerMark,
            shelf: CacheShelf<T>,
            item: String,
        ): T? = null

        override suspend fun <T> write(
            profile: ProfileId,
            mark: ServerMark,
            shelf: CacheShelf<T>,
            item: String,
            value: T,
        ): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun warm(profile: ProfileId, mark: ServerMark, shelf: CacheShelf<*>) {
            // Nothing to warm: this stub keeps no last-known data at all.
        }

        override suspend fun clear(profile: ProfileId): Outcome<Int> = Outcome.Ok(0)
    }

    private companion object {
        const val COMPUTER_URL = "https://computer.example"
        const val HELPER_URL = "ws://127.0.0.1:4099"
        const val HELPER_FOLDER = "/root/projects"
        const val SAVED_CHAT = "saved-1"
    }
}
