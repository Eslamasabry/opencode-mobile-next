@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Device QA 2026-11-13 (review #4): the install sheet of one agent showed a spinner and "Another setup is running" for
 * half an hour, named nothing, offered no way to stop it and had lost its close control. The sheet now knows which job
 * holds the phone's setup, can say how far it is, offers to stop it, and can always be closed.
 */
class AgentsInstallBlockedTest {
    private object NoComputers : AgentComputers {
        override val computers = MutableStateFlow<ImmutableList<AgentComputer>>(persistentListOf())

        override suspend fun check(address: String, password: String, folder: String) = Outcome.Failed(Failure.TimedOut())

        override suspend fun add(name: String, address: String, password: String, folder: String) =
            Outcome.Failed(Failure.TimedOut())

        override suspend fun agents(id: ProfileId): Outcome<ImmutableList<String>> = Outcome.Failed(Failure.TimedOut())

        override suspend fun newConversation(id: ProfileId): Outcome<ConversationRef> = Outcome.Failed(Failure.TimedOut())
    }

    private val gemini = PhoneAgentRow("gemini", "Gemini CLI", PhoneAgentStatus.NotInstalled, installable = true)
    private val qwen = PhoneAgentRow("qwen", "Qwen Code", PhoneAgentStatus.NotInstalled, installable = true)

    private class Phone(vararg rows: PhoneAgentRow) : PhoneAgents {
        override val state =
            MutableStateFlow(PhoneAgentsState(available = true, agents = persistentListOf(*rows), owner = ProfileId("owner")))
        var cancels = 0
        var refuse: PhoneAgentInstallIssue? = null

        /** While set, [install] waits for it (a press that is still being handled). */
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun refresh(): Outcome<Unit> = Unit.ok()

        override suspend fun install(id: String): Outcome<PhoneAgentInstallResult> {
            hold?.await()
            refuse?.let { return PhoneAgentInstallResult.Refused(it).ok() }
            return PhoneAgentInstallResult.Started.ok()
        }

        override suspend fun cancelInstall(): Outcome<Unit> {
            cancels++
            state.value =
                state.value.copy(
                    installation = null,
                    agents = state.value.agents.map { it.copy(installable = true) }.toPersistentList(),
                )
            return Unit.ok()
        }

        /** Another agent's job takes the phone's setup. */
        fun otherJobRuns(id: String, step: PhoneAgentInstallStep? = PhoneAgentInstallStep.Linux) {
            state.value =
                state.value.copy(
                    installation = PhoneAgentInstallation(id, PhoneAgentInstallPhase.Installing, null, step = step),
                    agents = state.value.agents.map { it.copy(installable = false) }.toPersistentList(),
                )
        }
    }

    private fun TestScope.presenter(phone: PhoneAgents) =
        AgentsPresenter(
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            NoComputers,
            phone,
            onBack = {},
            openChat = {},
        )

    @Test
    fun `a blocked install review names the job that holds the phone and its step`() = runTest {
        val phone = Phone(gemini, qwen)
        val presenter = presenter(phone)
        presenter.requestInstall("gemini")
        assertNull(presenter.state.value.installBlocker, "nothing runs: nothing blocks")
        phone.otherJobRuns("qwen", PhoneAgentInstallStep.Runtime)
        val blocker = assertNotNull(presenter.state.value.installBlocker)
        assertEquals("qwen", blocker.agentId)
        assertEquals("Qwen Code", blocker.name)
        assertEquals(PhoneAgentInstallStep.Runtime, blocker.step)
    }

    @Test
    fun `the stop action exists only while a job runs that can be stopped, and it frees the review`() = runTest {
        val phone = Phone(gemini, qwen)
        val presenter = presenter(phone)
        presenter.requestInstall("gemini")
        assertNull(presenter.state.value.installBlocker)
        phone.otherJobRuns("qwen")
        assertTrue(presenter.state.value.installBlocker!!.canCancel)
        presenter.cancelPhoneInstall()
        assertEquals(1, phone.cancels)
        assertNull(presenter.state.value.installBlocker, "the job is gone: the review is free again, by itself")
        assertTrue(presenter.state.value.phone.agents.single { it.id == "gemini" }.installable)
    }

    @Test
    fun `a refusal because the phone's setup is busy with no job named still says so, with nothing to stop`() = runTest {
        val phone = Phone(gemini)
        val presenter = presenter(phone)
        presenter.requestInstall("gemini")
        phone.refuse = PhoneAgentInstallIssue.Busy
        presenter.installPhone("gemini")
        assertEquals(PhoneAgentInstallIssue.Busy, presenter.state.value.installIssue)
        // The phone's read now says the agent cannot start (its setup is busy).
        phone.state.value = phone.state.value.copy(agents = persistentListOf(gemini.copy(installable = false)))
        val blocker = assertNotNull(presenter.state.value.installBlocker)
        assertNull(blocker.agentId)
        assertEquals(false, blocker.canCancel)
    }

    @Test
    fun `the review closes from its X and from Back in every state, a press in flight included`() = runTest {
        val phone = Phone(gemini, qwen)
        val presenter = presenter(phone)
        presenter.requestInstall("gemini")
        presenter.dismissInstall()
        assertNull(presenter.state.value.installingAgent, "idle")
        presenter.requestInstall("gemini")
        phone.otherJobRuns("qwen")
        presenter.back()
        assertNull(presenter.state.value.installingAgent, "blocked, Back")
        presenter.requestInstall("gemini")
        phone.state.value = phone.state.value.copy(installation = null, agents = persistentListOf(gemini, qwen))
        phone.hold = CompletableDeferred()
        presenter.installPhone("gemini")
        assertTrue(presenter.state.value.busy, "the press is still being handled")
        presenter.dismissInstall()
        assertNull(presenter.state.value.installingAgent, "busy, the X")
        presenter.requestInstall("gemini")
        presenter.back()
        assertNull(presenter.state.value.installingAgent, "busy, Back")
        phone.hold!!.complete(Unit)
    }
}
