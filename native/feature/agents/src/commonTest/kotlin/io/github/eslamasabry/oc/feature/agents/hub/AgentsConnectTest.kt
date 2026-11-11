@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** The phone agents connect on their own (no "Connect phone agents" button), as Flutter's Agents page does. */
class AgentsConnectTest {
    private object NoComputers : AgentComputers {
        override val computers = MutableStateFlow<ImmutableList<AgentComputer>>(persistentListOf())

        override suspend fun check(address: String, password: String, folder: String) =
            Outcome.Failed(Failure.TimedOut())

        override suspend fun add(name: String, address: String, password: String, folder: String) =
            Outcome.Failed(Failure.TimedOut())

        override suspend fun agents(id: ProfileId): Outcome<ImmutableList<String>> =
            Outcome.Failed(Failure.TimedOut())

        override suspend fun newConversation(id: ProfileId): Outcome<ConversationRef> =
            Outcome.Failed(Failure.TimedOut())
    }

    private class Phone(agents: List<PhoneAgentRow>, available: Boolean = true) : PhoneAgents {
        override val state =
            MutableStateFlow(
                PhoneAgentsState(available = available, agents = persistentListOf(*agents.toTypedArray()), owner = ProfileId("owner"))
            )

        override suspend fun refresh(): Outcome<Unit> = Unit.ok()

        val installs = mutableListOf<String>()

        override suspend fun install(id: String): Outcome<PhoneAgentInstallResult> {
            installs += id
            return PhoneAgentInstallResult.Started.ok()
        }

        fun set(vararg agents: PhoneAgentRow) {
            state.value = state.value.copy(agents = persistentListOf(*agents))
        }
    }

    private class Host(var answer: Outcome<Unit> = Unit.ok()) : PhoneAgentHostStarter {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun connect(): Outcome<Unit> {
            calls++
            gate?.await()
            return answer
        }
    }

    private class SlowHost(val failures: Int) : PhoneAgentHostStarter {
        var calls = 0

        override suspend fun connect(): Outcome<Unit> =
            if (++calls <= failures) Outcome.Failed(Failure.ComputerDidNotAnswer()) else Unit.ok()
    }

    private fun presenter(test: TestScope, phone: Phone, host: Host) =
        AgentsPresenter(
            CoroutineScope(test.backgroundScope.coroutineContext + UnconfinedTestDispatcher(test.testScheduler)),
            NoComputers,
            phone,
            onBack = {},
            openChat = {},
            phoneHost = host,
        )

    private val claudeReady = PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready)
    private val geminiMissing =
        PhoneAgentRow("gemini", "Gemini CLI", PhoneAgentStatus.NotInstalled, installable = true)

    @Test
    fun `B-AGENT-180 the page connects phone agents on its own when an agent is installed`() = runTest {
        val host = Host()
        val presenter = presenter(this, Phone(listOf(claudeReady, geminiMissing)), host)
        assertEquals(1, host.calls)
        val state = presenter.state.value
        assertTrue(state.helperConnected)
        assertFalse(state.helperConnectFailed)
        assertFalse(state.helperConnecting)
    }

    @Test
    fun `a helper that refuses its first look but answers a moment later ends connected without a tap`() = runTest {
        val host = SlowHost(failures = 1)
        val presenter =
            AgentsPresenter(
                CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
                NoComputers,
                Phone(listOf(claudeReady)),
                onBack = {},
                openChat = {},
                phoneHost = host,
            )
        // The page looks again after a pause; the presenter lives in the background scope, which idle ignores.
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(2, host.calls)
        assertTrue(presenter.state.value.helperConnected)
        assertFalse(presenter.state.value.helperConnectFailed)
    }

    @Test
    fun `B-AGENT-181 nothing installed tries nothing and shows no error`() = runTest {
        val host = Host(Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic)))
        val presenter = presenter(this, Phone(listOf(geminiMissing)), host)
        assertEquals(0, host.calls)
        assertFalse(presenter.state.value.helperConnectFailed)
        assertNull(presenter.state.value.helperConnectFailure)
        // Try again is not a way around it either: with nothing installed there is nothing to connect.
        presenter.connectPhoneHelper()
        assertEquals(0, host.calls)
        assertFalse(presenter.state.value.helperConnectFailed)
    }

    @Test
    fun `B-AGENT-181 a phone without its Linux tries nothing`() = runTest {
        val host = Host()
        presenter(this, Phone(listOf(claudeReady), available = false), host)
        assertEquals(0, host.calls)
    }

    @Test
    fun `B-AGENT-182 only a real failed connection is reported, with its reason, and Try again connects again`() = runTest {
        val failure = Failure.Runtime(RuntimeProblem.ServerStartUnavailable)
        val host = Host(Outcome.Failed(failure))
        val presenter = presenter(this, Phone(listOf(claudeReady)), host)
        assertTrue(presenter.state.value.helperConnectFailed)
        assertEquals(failure, presenter.state.value.helperConnectFailure)
        host.answer = Unit.ok()
        presenter.connectPhoneHelper()
        assertEquals(2, host.calls)
        assertFalse(presenter.state.value.helperConnectFailed)
        assertNull(presenter.state.value.helperConnectFailure)
        assertTrue(presenter.state.value.helperConnected)
    }

    @Test
    fun `a helper that is too old says so and its update runs the install of an agent that is there`() = runTest {
        val failure = Failure.Runtime(RuntimeProblem.HelperUpdateRequired)
        val phone = Phone(listOf(claudeReady, geminiMissing))
        val presenter = presenter(this, phone, Host(Outcome.Failed(failure)))
        assertEquals(failure, presenter.state.value.helperConnectFailure)
        presenter.updatePhoneHelper()
        assertEquals(listOf("claude"), phone.installs)
    }

    @Test
    fun `a failed start replaces the old stopped-by-Android notice, which is only history`() = runTest {
        val phone = Phone(listOf(claudeReady))
        phone.state.value =
            phone.state.value.copy(helperStoppedUnexpectedly = true, helperMayHaveBeenStoppedByAndroid = true)
        val host = Host(Outcome.Failed(Failure.Runtime(RuntimeProblem.HelperUpdateRequired)))
        val presenter = presenter(this, phone, host)
        assertTrue(presenter.state.value.helperConnectFailed)
        assertFalse(presenter.state.value.phone.helperStoppedUnexpectedly)
        assertFalse(presenter.state.value.phone.helperMayHaveBeenStoppedByAndroid)
        // Without a failed attempt after it, the recorded stop is still shown.
        val quiet = Phone(listOf(claudeReady))
        quiet.state.value = quiet.state.value.copy(helperStoppedUnexpectedly = true)
        assertTrue(presenter(this, quiet, Host(Unit.ok())).state.value.phone.helperStoppedUnexpectedly)
    }

    @Test
    fun `a helper Android ended after it was connected is started again by the page, once`() = runTest {
        val phone = Phone(listOf(claudeReady))
        val host = Host()
        val presenter = presenter(this, phone, host)
        assertEquals(1, host.calls)
        val running = phone.state.value
        val stopped = running.copy(helperStoppedUnexpectedly = true, helperMayHaveBeenStoppedByAndroid = true)
        phone.state.value = stopped
        assertEquals(2, host.calls, "the page answers the stop itself; before, nothing started the helper again")
        // Android ends it again: no loop. The notice stays, and its own action starts the helper.
        phone.state.value = running
        phone.state.value = stopped
        assertEquals(2, host.calls)
        assertTrue(presenter.state.value.phone.helperStoppedUnexpectedly)
        presenter.connectPhoneHelper()
        assertEquals(3, host.calls)
    }

    @Test
    fun `the recorded stop is not shown while the page is starting the helper`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val phone = Phone(listOf(claudeReady))
        phone.state.value =
            phone.state.value.copy(helperStoppedUnexpectedly = true, helperMayHaveBeenStoppedByAndroid = true)
        val presenter = presenter(this, phone, Host().also { it.gate = gate })
        assertTrue(presenter.state.value.helperConnecting)
        assertFalse(presenter.state.value.phone.helperStoppedUnexpectedly)
        assertFalse(presenter.state.value.phone.helperMayHaveBeenStoppedByAndroid)
        gate.complete(Unit)
    }

    @Test
    fun `B-AGENT-183 an agent that finishes installing connects, a refresh of the same agents does not`() = runTest {
        val host = Host()
        val phone = Phone(listOf(geminiMissing))
        presenter(this, phone, host)
        assertEquals(0, host.calls)
        val geminiInstalled = geminiMissing.copy(status = PhoneAgentStatus.SignInNeeded)
        phone.set(geminiInstalled)
        assertEquals(1, host.calls)
        phone.set(geminiInstalled.copy(signInAvailable = true))
        assertEquals(1, host.calls)
        phone.set(geminiInstalled, claudeReady)
        assertEquals(2, host.calls)
    }

    @Test
    fun `B-AGENT-183 connecting is not a busy page and never runs twice at once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val host = Host().also { it.gate = gate }
        val phone = Phone(listOf(claudeReady))
        val presenter = presenter(this, phone, host)
        assertTrue(presenter.state.value.helperConnecting)
        assertFalse(presenter.state.value.busy)
        presenter.connectPhoneHelper()
        assertEquals(1, host.calls)
        gate.complete(Unit)
        // The tap that came while connecting runs once more after it.
        assertEquals(2, host.calls)
        assertFalse(presenter.state.value.helperConnecting)
    }

    @Test
    fun `B-AGENT-184 a tile opens its agent's sheet and an act from the sheet closes it`() = runTest {
        val phone = Phone(listOf(claudeReady, geminiMissing))
        val presenter = presenter(this, phone, Host())
        presenter.openAgent("absent")
        assertNull(presenter.state.value.sheetAgent)
        presenter.openAgent("gemini")
        assertEquals("gemini", presenter.state.value.sheetAgent)
        presenter.requestInstall("gemini")
        assertNull(presenter.state.value.sheetAgent)
        assertEquals("gemini", presenter.state.value.installingAgent?.id)
        presenter.dismissInstall()
        presenter.openAgent("claude")
        presenter.back()
        assertNull(presenter.state.value.sheetAgent)
    }

    @Test
    fun `B-AGENT-185 tile states and their one act`() {
        val installing = PhoneAgentInstallation("gemini", PhoneAgentInstallPhase.Installing, 0.42)
        assertEquals(AgentTileState.Ready, agentTile(claudeReady, null).state)
        assertNull(agentTile(claudeReady, null).act)
        assertEquals(AgentTileAct.Install, agentTile(geminiMissing, null).act)
        val tile = agentTile(geminiMissing, installing)
        assertEquals(AgentTileState.Installing, tile.state)
        assertEquals(0.42, tile.fraction)
        assertNull(tile.act)
        assertEquals(AgentTileState.Installing, agentTile(geminiMissing, installing.copy(fraction = null)).state)
        val signIn = PhoneAgentRow("agy", "Antigravity", PhoneAgentStatus.SignInNeeded, signInAvailable = true)
        assertEquals(AgentTileAct.SignIn, agentTile(signIn, null).act)
        assertNull(agentTile(signIn.copy(signInAvailable = false), null).act)
        val done = agentTile(signIn, PhoneAgentInstallation("agy", PhoneAgentInstallPhase.Done, 1.0))
        assertTrue(done.justInstalled)
        assertFalse(agentTile(claudeReady, PhoneAgentInstallation("claude", PhoneAgentInstallPhase.Done, 1.0)).justInstalled)
        val arm = PhoneAgentRow("x", "X", PhoneAgentStatus.NotInstalled, supportedInstallation = false)
        assertIs<AgentTileModel>(agentTile(arm, null))
        assertEquals(AgentTileState.CannotRun, agentTile(arm, null).state)
        assertNull(agentTile(arm, null).act)
    }

    @Test
    fun `B-AGENT-193 the computers tab reads each computer's agents on its own, never the phone's helper`() = runTest {
        val read = mutableListOf<String>()
        val desk = AgentComputer(ProfileId("desk"), "Desk", "ws://d", "/p", connected = true)
        val helper = AgentComputer(ProfileId("owner-agents"), "Phone agents", "ws://127.0.0.1:4099", "/root/projects", connected = true)
        val computers =
            object : AgentComputers by NoComputers {
                override val computers = MutableStateFlow<ImmutableList<AgentComputer>>(persistentListOf(helper, desk))

                override suspend fun agents(id: ProfileId): Outcome<ImmutableList<String>> {
                    read += id.raw
                    return persistentListOf("Claude Code").ok()
                }
            }
        val phone = Phone(listOf(claudeReady))
        phone.state.value = phone.state.value.copy(conversations = helper.id)
        val presenter =
            AgentsPresenter(
                CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
                computers,
                phone,
                onBack = {},
                openChat = {},
            )
        presenter.selectTab(AgentsTab.Computers)
        assertEquals(listOf("desk"), read)
        assertEquals(persistentListOf("Claude Code"), presenter.state.value.ready[desk.id])
        presenter.selectTab(AgentsTab.Phone)
        presenter.selectTab(AgentsTab.Computers)
        assertEquals(listOf("desk"), read) // already read: a tab switch does not read again
        presenter.pullRefresh()
        assertEquals(listOf("desk", "desk"), read) // a pull does
        assertFalse(presenter.state.value.refreshing)
    }

    @Test
    fun `HELPER-DOWN no tile says Ready or offers New conversation beside "the agent helper did not start"`() = runTest {
        val helperId = ProfileId("owner-agents")
        val unconnected = AgentComputer(helperId, "Phone agents", "ws://127.0.0.1:4099", "/root/projects", connected = false)
        val computers =
            object : AgentComputers by NoComputers {
                override val computers = MutableStateFlow<ImmutableList<AgentComputer>>(persistentListOf(unconnected))
            }
        val phone = Phone(listOf(claudeReady))
        phone.state.value = phone.state.value.copy(conversations = helperId)
        val host = Host(Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable)))
        val presenter =
            AgentsPresenter(
                CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
                computers,
                phone,
                onBack = {},
                openChat = {},
                phoneHost = host,
            )
        val state = presenter.state.value
        assertTrue(state.helperConnectFailed, "the page says the helper did not start")
        assertTrue(phoneHelperDown(state))
        assertEquals(AgentTileState.Unavailable, agentTile(claudeReady, null, helperDown = phoneHelperDown(state)).state)
        assertFalse(phoneChatOffered(state), "a saved but unconnected helper offers no conversation")

        // The helper connects another way (New conversation starts it too): the failure line goes, the tiles are Ready.
        computers.computers.value = persistentListOf(unconnected.copy(connected = true))
        val live = presenter.state.value
        assertFalse(live.helperConnectFailed)
        assertNull(live.helperConnectFailure)
        assertEquals(AgentTileState.Ready, agentTile(claudeReady, null, helperDown = phoneHelperDown(live)).state)
        assertTrue(phoneChatOffered(live))
    }

    @Test
    fun `HELPER-DOWN Try again that starts the helper brings the tiles back to Ready`() = runTest {
        val host = Host(Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable)))
        val phone = Phone(listOf(claudeReady))
        phone.state.value = phone.state.value.copy(conversations = ProfileId("owner-agents"))
        val presenter = presenter(this, phone, host)
        assertTrue(phoneHelperDown(presenter.state.value))
        host.answer = Unit.ok()
        presenter.connectPhoneHelper()
        val state = presenter.state.value
        assertFalse(phoneHelperDown(state))
        assertTrue(state.helperConnected)
        assertTrue(phoneChatOffered(state))
    }
}
