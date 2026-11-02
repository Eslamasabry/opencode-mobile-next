@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentsPresenterTest {
    private class FakeComputers : AgentComputers {
        override val computers = MutableStateFlow<ImmutableList<AgentComputer>>(persistentListOf())
        var checkResult: Outcome<ComputerCheck> = ComputerCheck.Ready("0.8.0", persistentListOf("Claude Code")).ok()
        var addResult: Outcome<ProfileId>? = null
        var agentsResult: Outcome<ImmutableList<String>> = persistentListOf("Claude Code", "Pi").ok()
        var startResult: Outcome<ConversationRef>? = null
        val calls = mutableListOf<String>()

        override suspend fun check(address: String, password: String, folder: String): Outcome<ComputerCheck> {
            calls += "check:$address:$folder"
            return checkResult
        }

        override suspend fun add(name: String, address: String, password: String, folder: String): Outcome<ProfileId> {
            calls += "add:$name:$address:$folder"
            val result = addResult ?: ProfileId("p1").ok()
            if (result is Outcome.Ok) {
                computers.value = (computers.value + AgentComputer(result.value, name, address, folder, connected = true)).toImmutableList()
            }
            return result
        }

        override suspend fun agents(id: ProfileId): Outcome<ImmutableList<String>> {
            calls += "agents:${id.raw}"
            return agentsResult
        }

        override suspend fun newConversation(id: ProfileId): Outcome<ConversationRef> {
            calls += "new:${id.raw}"
            return startResult ?: ConversationRef(id, SessionId("s1")).ok()
        }
    }

    private class Rig(test: TestScope, phone: PhoneAgentsState = PhoneAgentsState()) {
        val computers = FakeComputers()
        val opened = mutableListOf<ConversationRef>()
        var backs = 0
        val presenter = AgentsPresenter(
            CoroutineScopeOf(test),
            computers,
            NoPhoneAgents(MutableStateFlow(phone)),
            onBack = { backs++ },
            openChat = { opened += it },
        )
    }

    @Test
    fun `B-AGENT-111 without a phone agent host the phone part is a final unavailable state, never empty or loading`() = runTest {
        val rig = Rig(this)
        val state = rig.presenter.state.value
        assertFalse(state.phone.available)
        assertTrue(state.phone.agents.isEmpty())
        assertFalse(state.busy)
    }

    @Test
    fun `B-PROTO-070 checking a computer saves nothing and shows the ready agents`() = runTest {
        val rig = Rig(this)
        rig.presenter.startAdding()
        rig.presenter.check(" 127.0.0.1:6767 ", "pw", " /home/me/app ")
        assertEquals(listOf("check:127.0.0.1:6767:/home/me/app"), rig.computers.calls)
        val ready = assertIs<ComputerCheck.Ready>(rig.presenter.state.value.check)
        assertEquals(listOf("Claude Code"), ready.agents)
        assertTrue(rig.presenter.state.value.computers.isEmpty())
    }

    @Test
    fun `B-PROTO-070 an edit after a check clears the stale result`() = runTest {
        val rig = Rig(this)
        rig.presenter.check("127.0.0.1:6767", "", "/w")
        rig.presenter.edited()
        assertNull(rig.presenter.state.value.check)
    }

    @Test
    fun `B-AGENT-160 a refused check keeps the typed failure for Details and never navigates`() = runTest {
        val rig = Rig(this)
        rig.computers.checkResult = Failure.SignIn(SignInWhy.PasswordRejected).failed()
        rig.presenter.check("127.0.0.1:6767", "bad", "/w")
        val issue = rig.presenter.state.value.issue!!
        assertEquals(AgentsIssue.CheckFailed, issue.kind)
        assertIs<Failure.SignIn>(issue.failure)
        assertTrue(rig.opened.isEmpty())
    }

    @Test
    fun `B-AGENT-153 a saved computer joins the list with the agents its check found`() = runTest {
        val rig = Rig(this)
        rig.presenter.startAdding()
        rig.presenter.check("127.0.0.1:6767", "", "/w")
        rig.presenter.add("Desk", "127.0.0.1:6767", "", "/w")
        val state = rig.presenter.state.value
        assertEquals(listOf("Desk"), state.computers.map { it.name })
        assertEquals(listOf("Claude Code"), state.ready[ProfileId("p1")]?.toList())
        assertFalse(state.adding)
        assertEquals(1, state.savedGeneration)
        assertEquals(AgentsIssue.Added, state.issue?.kind)
    }

    @Test
    fun `B-AGENT-153 a failed save keeps the form open and the list unchanged`() = runTest {
        val rig = Rig(this)
        rig.presenter.startAdding()
        rig.computers.addResult = Outcome.Failed(Failure.StorageRefused())
        rig.presenter.add("Desk", "127.0.0.1:6767", "", "/w")
        val state = rig.presenter.state.value
        assertTrue(state.adding)
        assertTrue(state.computers.isEmpty())
        assertEquals(AgentsIssue.AddFailed, state.issue?.kind)
        assertEquals(0, state.savedGeneration)
    }

    @Test
    fun `B-AGENT-111 reading a computer's agents reaches a final list`() = runTest {
        val rig = Rig(this)
        rig.presenter.readAgents(ProfileId("p9"))
        assertEquals(listOf("Claude Code", "Pi"), rig.presenter.state.value.ready[ProfileId("p9")]?.toList())
        assertFalse(rig.presenter.state.value.busy)
    }

    @Test
    fun `B-NAV-030 a new conversation on a computer opens that computer's chat`() = runTest {
        val rig = Rig(this)
        rig.presenter.newConversation(ProfileId("p2"))
        assertEquals(listOf(ConversationRef(ProfileId("p2"), SessionId("s1"))), rig.opened)
    }

    @Test
    fun `B-NAV-030 a refused start stays on the screen with a plain issue`() = runTest {
        val rig = Rig(this)
        rig.computers.startResult = Outcome.Failed(Failure.ComputerDidNotAnswer())
        rig.presenter.newConversation(ProfileId("p2"))
        assertTrue(rig.opened.isEmpty())
        assertEquals(AgentsIssue.StartFailed, rig.presenter.state.value.issue?.kind)
    }

    @Test
    fun `one tap admits one operation`() = runTest {
        val rig = Rig(this)
        rig.presenter.back()
        assertEquals(1, rig.backs)
    }
}

/** Runs the presenter's work eagerly on the test scheduler. */
private fun CoroutineScopeOf(test: TestScope): kotlinx.coroutines.CoroutineScope =
    kotlinx.coroutines.CoroutineScope(test.backgroundScope.coroutineContext + UnconfinedTestDispatcher(test.testScheduler))
