@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.check.PhoneCheckFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** G2 and G3 of the Agents page audit: a phone check you can run, and a stopped helper you can resume. */
class AgentsStatesTest {
    private object NoComputers : AgentComputers {
        override val computers = MutableStateFlow<ImmutableList<AgentComputer>>(persistentListOf())

        override suspend fun check(address: String, password: String, folder: String) = Outcome.Failed(Failure.TimedOut())

        override suspend fun add(name: String, address: String, password: String, folder: String) =
            Outcome.Failed(Failure.TimedOut())

        override suspend fun agents(id: ProfileId): Outcome<ImmutableList<String>> = Outcome.Failed(Failure.TimedOut())

        override suspend fun newConversation(id: ProfileId): Outcome<ConversationRef> = Outcome.Failed(Failure.TimedOut())
    }

    private class Phone(vararg rows: PhoneAgentRow) : PhoneAgents {
        override val state =
            MutableStateFlow(PhoneAgentsState(available = true, agents = persistentListOf(*rows), owner = ProfileId("owner")))

        override suspend fun refresh(): Outcome<Unit> = Unit.ok()

        fun set(vararg rows: PhoneAgentRow) {
            state.value = state.value.copy(agents = persistentListOf(*rows))
        }
    }

    private class Host : PhoneAgentHostStarter {
        var calls = 0

        override suspend fun connect(): Outcome<Unit> {
            calls++
            return Unit.ok()
        }
    }

    private class Checker(var answer: PhoneCheckFailure? = null) : PhoneAgentChecker {
        val asked = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun check(id: String): Outcome<PhoneCheckFailure?> {
            asked += id
            gate?.await()
            return answer.ok()
        }
    }

    private fun presenter(test: TestScope, phone: Phone, host: Host?, checker: Checker?) =
        AgentsPresenter(
            CoroutineScope(test.backgroundScope.coroutineContext + UnconfinedTestDispatcher(test.testScheduler)),
            NoComputers,
            phone,
            onBack = {},
            openChat = {},
            phoneHost = host,
            checker = checker,
        )

    private val needsCheck = PhoneAgentRow("antigravity", "Antigravity", PhoneAgentStatus.PhoneCheck)
    private val stopped = PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Stopped)

    @Test
    fun `an agent that needs the phone check gets the Check act, and a stopped one gets Resume`() {
        assertEquals(AgentTileAct.Check, agentTile(needsCheck, null).act)
        assertEquals(AgentTileState.PhoneCheck, agentTile(needsCheck, null).state)
        assertEquals(AgentTileAct.Resume, agentTile(stopped, null).act)
        assertEquals(AgentTileState.Stopped, agentTile(stopped, null).state)
    }

    @Test
    fun `a check that runs, or a helper being started, reads Checking and offers no act`() {
        val checking = agentTile(needsCheck, null, checking = true)
        assertEquals(AgentTileState.Checking, checking.state)
        assertNull(checking.act)
        val resuming = agentTile(stopped, null, resuming = true)
        assertEquals(AgentTileState.Checking, resuming.state)
        assertNull(resuming.act)
    }

    @Test
    fun `Check runs the phone check for that agent, shows it running, and the tile leaves Phone check needed`() = runTest {
        val phone = Phone(needsCheck)
        val checker = Checker().apply { gate = CompletableDeferred() }
        val presenter = presenter(this, phone, null, checker)
        presenter.checkPhoneAgent("antigravity")
        assertEquals(listOf("antigravity"), checker.asked)
        assertEquals("antigravity", presenter.state.value.checkingAgent)
        // A second tap while it runs does nothing.
        presenter.checkPhoneAgent("antigravity")
        assertEquals(1, checker.asked.size)
        // The check qualifies it; the page reads the phone again and the tile is no longer waiting for a check.
        phone.set(needsCheck.copy(status = PhoneAgentStatus.Ready))
        checker.gate!!.complete(Unit)
        assertNull(presenter.state.value.checkingAgent)
        assertNull(presenter.state.value.checkFailure)
        val tile = agentTile(presenter.state.value.phone.agents.single(), null)
        assertEquals(AgentTileState.Ready, tile.state)
    }

    @Test
    fun `a failed check says why and the same act tries again`() = runTest {
        val phone = Phone(needsCheck)
        val checker = Checker(PhoneCheckFailure.Daemon)
        val presenter = presenter(this, phone, null, checker)
        presenter.checkPhoneAgent("antigravity")
        val state = presenter.state.value
        assertEquals("antigravity", state.checkFailedAgent)
        assertEquals(PhoneCheckFailure.Daemon, state.checkFailure)
        assertEquals(AgentTileAct.Check, agentTile(state.phone.agents.single(), null).act)
        checker.answer = null
        presenter.checkPhoneAgent("antigravity")
        assertEquals(2, checker.asked.size)
        assertNull(presenter.state.value.checkFailure)
        assertNull(presenter.state.value.checkFailedAgent)
    }

    @Test
    fun `an agent that does not need a check is not checked`() = runTest {
        val checker = Checker()
        val presenter = presenter(this, Phone(stopped), null, checker)
        presenter.checkPhoneAgent("claude")
        assertTrue(checker.asked.isEmpty())
        assertFalse(presenter.state.value.checkingAgent != null)
    }

    @Test
    fun `Resume starts the helper again by hand even after the page's one automatic restart`() = runTest {
        val phone = Phone(stopped)
        val host = Host()
        val presenter = presenter(this, phone, host, null)
        // The page connected once on opening and restarted the stopped agent once; the tile still says stopped.
        val before = host.calls
        assertTrue(before >= 1)
        phone.state.value = phone.state.value.copy(agents = persistentListOf(stopped.copy(name = "Claude Code ")))
        val settled = host.calls
        presenter.resumePhoneAgent("claude")
        assertEquals(settled + 1, host.calls, "the tap restarts it; the automatic restart is spent")
        assertFalse(presenter.state.value.helperConnecting)
    }

    @Test
    fun `a helper that keeps stopping is restarted by the page at most once on its own`() = runTest {
        val phone = Phone(PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready))
        val host = Host()
        presenter(this, phone, host, null)
        assertEquals(1, host.calls)
        phone.set(stopped)
        assertEquals(2, host.calls, "one automatic restart for a Stopped tile")
        phone.set(stopped.copy(name = "Claude Code "))
        phone.set(stopped)
        assertEquals(2, host.calls, "no restart loop: the person's tap is next")
    }
}
