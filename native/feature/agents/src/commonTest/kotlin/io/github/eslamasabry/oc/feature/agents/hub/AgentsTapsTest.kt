@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
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

/**
 * Owner 2026-11-11: "I had to press 4 times on codex install for it to respond and same for sign in button." The
 * page's own read of the phone (on opening and on every return to the page) held the one-operation lock for as long as
 * the sign-in probes took, and Install and Sign in returned on that lock without a sign. Every tap now answers.
 */
class AgentsTapsTest {
    private object NoComputers : AgentComputers {
        override val computers = MutableStateFlow<ImmutableList<AgentComputer>>(persistentListOf())

        override suspend fun check(address: String, password: String, folder: String) = Outcome.Failed(Failure.TimedOut())

        override suspend fun add(name: String, address: String, password: String, folder: String) =
            Outcome.Failed(Failure.TimedOut())

        override suspend fun agents(id: ProfileId): Outcome<ImmutableList<String>> = Outcome.Failed(Failure.TimedOut())

        override suspend fun newConversation(id: ProfileId): Outcome<ConversationRef> = Outcome.Failed(Failure.TimedOut())
    }

    /** A phone whose read waits (the helper does not answer the sign-in probes) until [reading] completes. */
    private class SlowPhone(vararg rows: PhoneAgentRow) : PhoneAgents {
        override val state =
            MutableStateFlow(PhoneAgentsState(available = true, agents = persistentListOf(*rows), owner = ProfileId("owner")))

        var reading: CompletableDeferred<Unit>? = CompletableDeferred()
        var reads = 0
        private var running = 0
        var mostAtOnce = 0

        override suspend fun refresh(): Outcome<Unit> {
            reads++
            running++
            mostAtOnce = maxOf(mostAtOnce, running)
            try {
                reading?.await()
            } finally {
                running--
            }
            return Unit.ok()
        }

        val installs = mutableListOf<String>()
        var installing: CompletableDeferred<Unit>? = null

        override suspend fun install(id: String): Outcome<PhoneAgentInstallResult> {
            installs += id
            installing?.await()
            return PhoneAgentInstallResult.Started.ok()
        }
    }

    private class Rig(test: TestScope, val phone: SlowPhone) {
        val signIns = mutableListOf<Pair<ProfileId, String>>()
        val presenter =
            AgentsPresenter(
                CoroutineScope(test.backgroundScope.coroutineContext + UnconfinedTestDispatcher(test.testScheduler)),
                NoComputers,
                phone,
                onBack = {},
                openChat = {},
                openSignIn = { owner, agent -> signIns += owner to agent },
            )

        fun finishReading() {
            val gate = phone.reading
            phone.reading = null
            gate?.complete(Unit)
        }
    }

    private val codexMissing =
        PhoneAgentRow("codex", "Codex", PhoneAgentStatus.NotInstalled, installable = true, downloadBytes = 114_000_000L)
    private val geminiMissing = PhoneAgentRow("gemini", "Gemini CLI", PhoneAgentStatus.NotInstalled, installable = true)
    private val codexSignIn = PhoneAgentRow("codex", "Codex", PhoneAgentStatus.SignInNeeded, signInAvailable = true)

    @Test
    fun `the page's own read of the phone is not a busy page`() = runTest {
        val rig = Rig(this, SlowPhone(codexMissing))
        // The read the page starts on opening is still running.
        assertEquals(1, rig.phone.reads)
        assertFalse(rig.presenter.state.value.busy)
        // Coming back to the page reads again: still not busy.
        rig.presenter.refreshPhone()
        assertFalse(rig.presenter.state.value.busy)
        rig.finishReading()
    }

    @Test
    fun `Install pressed while the page reads the phone opens the install review at once and installs`() = runTest {
        val rig = Rig(this, SlowPhone(codexMissing))
        rig.presenter.requestInstall("codex")
        assertEquals("codex", rig.presenter.state.value.installingAgent?.id)
        rig.presenter.installPhone("codex")
        assertEquals(listOf("codex"), rig.phone.installs)
        rig.finishReading()
    }

    @Test
    fun `Sign in pressed while the page reads the phone opens the sign-in page on the first press`() = runTest {
        val rig = Rig(this, SlowPhone(codexSignIn))
        rig.presenter.signInPhone("codex")
        assertEquals(listOf(ProfileId("owner") to "codex"), rig.signIns)
        assertNull(rig.presenter.state.value.sheetAgent)
        rig.finishReading()
    }

    @Test
    fun `Sign in pressed while a real step runs opens the agent's sheet, and signs in once the step ends`() = runTest {
        val rig = Rig(this, SlowPhone(codexSignIn, geminiMissing))
        rig.finishReading()
        rig.phone.installing = CompletableDeferred()
        rig.presenter.installPhone("gemini")
        assertTrue(rig.presenter.state.value.busy)
        rig.presenter.signInPhone("codex")
        // Not nothing: the sheet answers the tap (its Sign in shows the running step), and nothing starts twice.
        assertEquals("codex", rig.presenter.state.value.sheetAgent)
        assertTrue(rig.signIns.isEmpty())
        rig.phone.installing!!.complete(Unit)
        assertFalse(rig.presenter.state.value.busy)
        rig.presenter.signInPhone("codex")
        assertEquals(listOf(ProfileId("owner") to "codex"), rig.signIns)
        assertNull(rig.presenter.state.value.sheetAgent)
    }

    @Test
    fun `Install pressed while another install starts opens its review, and that review stays open`() = runTest {
        val rig = Rig(this, SlowPhone(codexMissing, geminiMissing))
        rig.finishReading()
        rig.phone.installing = CompletableDeferred()
        rig.presenter.requestInstall("gemini")
        rig.presenter.installPhone("gemini")
        assertTrue(rig.presenter.state.value.busy)
        rig.presenter.requestInstall("codex")
        assertEquals("codex", rig.presenter.state.value.installingAgent?.id)
        rig.phone.installing!!.complete(Unit)
        // Gemini's start closes Gemini's review only.
        assertEquals("codex", rig.presenter.state.value.installingAgent?.id)
        // A second press on Install while the first install ran started nothing twice.
        assertEquals(listOf("gemini"), rig.phone.installs)
    }

    @Test
    fun `an install the phone no longer offers says why instead of nothing`() = runTest {
        val rig = Rig(this, SlowPhone(codexMissing))
        rig.finishReading()
        rig.presenter.requestInstall("codex")
        rig.phone.state.value =
            rig.phone.state.value.copy(
                agents = persistentListOf(codexMissing.copy(installable = false)),
                installation = PhoneAgentInstallation("gemini", PhoneAgentInstallPhase.Installing, 0.1),
            )
        rig.presenter.installPhone("codex")
        assertTrue(rig.phone.installs.isEmpty())
        assertEquals(PhoneAgentInstallIssue.Busy, rig.presenter.state.value.installIssue)
    }

    @Test
    fun `reads asked for while one runs run once more after it, never two at once`() = runTest {
        val rig = Rig(this, SlowPhone(codexMissing))
        rig.presenter.refreshPhone()
        rig.presenter.refreshPhone()
        assertEquals(1, rig.phone.reads)
        rig.finishReading()
        assertEquals(2, rig.phone.reads)
        assertEquals(1, rig.phone.mostAtOnce)
    }
}
