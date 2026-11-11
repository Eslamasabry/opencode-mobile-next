@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The page's read of the phone no longer holds the page's one-operation lock (w4-taps), so a read can now run beside a
 * real step. These pin what that must never cost: a read that ends after a step began never puts its older answer over
 * the step's, the tiles never flip back, and with the agent helper down (w4-agent-models: tiles "Not available", one
 * automatic retry when the page is shown again) Install and Sign in still answer the first press and nothing starts
 * twice at once.
 */
class AgentsReadOrderTest {
    private open class Computers : AgentComputers {
        override val computers = MutableStateFlow<ImmutableList<AgentComputer>>(persistentListOf())
        val starts = mutableListOf<ProfileId>()
        var starting: CompletableDeferred<Unit>? = null

        override suspend fun check(address: String, password: String, folder: String) = Outcome.Failed(Failure.TimedOut())

        override suspend fun add(name: String, address: String, password: String, folder: String) =
            Outcome.Failed(Failure.TimedOut())

        override suspend fun agents(id: ProfileId): Outcome<ImmutableList<String>> = Outcome.Failed(Failure.TimedOut())

        override suspend fun newConversation(id: ProfileId): Outcome<ConversationRef> {
            starts += id
            starting?.await()
            return ConversationRef(id, SessionId("s1")).ok()
        }
    }

    /**
     * A phone with the real one's lane (AppPhoneAgents: every read and step holds one mutex and writes the rows it
     * found under it). A read takes its picture of the phone when it starts and waits on [reading] before writing it.
     */
    private class LanePhone(vararg rows: PhoneAgentRow) : PhoneAgents {
        override val state =
            MutableStateFlow(PhoneAgentsState(available = true, agents = persistentListOf(*rows), owner = ProfileId("owner")))
        private val lane = Mutex()

        /** What is on the phone now; a read writes what it saw when it began. */
        var disk: PhoneAgentsState = state.value
        var reading: CompletableDeferred<Outcome<Unit>>? = CompletableDeferred()
        var removing: CompletableDeferred<Unit>? = null
        val installs = mutableListOf<String>()
        val removals = mutableListOf<String>()

        override suspend fun refresh(): Outcome<Unit> =
            lane.withLock {
                val seen = disk
                val answer = reading?.await() ?: Unit.ok()
                if (answer is Outcome.Ok) state.value = seen
                answer
            }

        override suspend fun install(id: String): Outcome<PhoneAgentInstallResult> =
            lane.withLock {
                installs += id
                disk =
                    disk.copy(
                        agents = disk.agents.map { if (it.id == id) it.copy(installable = false) else it }.toPersistentList(),
                        installation = PhoneAgentInstallation(id, PhoneAgentInstallPhase.Installing, 0.0),
                    )
                state.value = disk
                PhoneAgentInstallResult.Started.ok()
            }

        override suspend fun remove(id: String): Outcome<PhoneAgentRemoval> =
            lane.withLock {
                removals += id
                removing?.await()
                PhoneAgentRemoval.Removed(1_000L).ok()
            }

        fun endRead(answer: Outcome<Unit>) {
            val gate = reading
            reading = null
            gate?.complete(answer)
        }
    }

    /** The agent helper: [answer] for every start, [gate] holds a start open; counts starts running at once. */
    private class Helper(var answer: Outcome<Unit>) : PhoneAgentHostStarter {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        private var running = 0
        var mostAtOnce = 0

        override suspend fun connect(): Outcome<Unit> {
            calls++
            running++
            mostAtOnce = maxOf(mostAtOnce, running)
            try {
                gate?.await()
            } finally {
                running--
            }
            return answer
        }
    }

    private class Rig(
        test: TestScope,
        val phone: LanePhone,
        val helper: Helper? = null,
        val computers: Computers = Computers(),
    ) {
        val signIns = mutableListOf<String>()
        val chats = mutableListOf<ConversationRef>()
        val presenter =
            AgentsPresenter(
                CoroutineScope(test.backgroundScope.coroutineContext + UnconfinedTestDispatcher(test.testScheduler)),
                computers,
                phone,
                onBack = {},
                openChat = { chats += it },
                phoneHost = helper,
                openSignIn = { _, agent -> signIns += agent },
            )

        fun codexTile(): AgentTileState {
            val phone = presenter.state.value.phone
            return agentTile(phone.agents.single { it.id == "codex" }, phone.installation).state
        }
    }

    private val codexMissing = PhoneAgentRow("codex", "Codex", PhoneAgentStatus.NotInstalled, installable = true)
    private val claudeRemovable = PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready, removable = true)
    private val claudeReady = PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready)
    private val codexSignIn = PhoneAgentRow("codex", "Codex", PhoneAgentStatus.SignInNeeded, signInAvailable = true)

    @Test
    fun `a read that fails while a removal runs never shows the read refusal over it`() = runTest {
        val rig = Rig(this, LanePhone(claudeRemovable))
        rig.phone.removing = CompletableDeferred()
        // The page's own read (on opening) is still running when the person removes Claude Code: the removal waits for
        // the phone, and the read then fails.
        rig.presenter.requestRemoval("claude")
        rig.presenter.confirmRemoval()
        assertTrue(rig.presenter.state.value.busy)
        rig.phone.endRead(Outcome.Failed(Failure.TimedOut()))
        assertEquals(listOf("claude"), rig.phone.removals)
        assertNull(rig.presenter.state.value.removal, "the removal is still running: no refusal shows over it")
        rig.phone.removing!!.complete(Unit)
        assertEquals(PhoneAgentRemoval.Removed(1_000L), rig.presenter.state.value.removal)
    }

    @Test
    fun `a read that fails on its own still says the phone agents could not be read`() = runTest {
        val rig = Rig(this, LanePhone(claudeRemovable))
        rig.phone.endRead(Outcome.Failed(Failure.TimedOut()))
        assertEquals(
            PhoneAgentRemoval.Refused(PhoneAgentRemovalIssue.Unavailable),
            rig.presenter.state.value.removal,
        )
    }

    @Test
    fun `an install pressed during a read waits for it, shows working, and its tile never flips back`() = runTest {
        val rig = Rig(this, LanePhone(codexMissing))
        rig.presenter.requestInstall("codex")
        rig.presenter.installPhone("codex")
        // The phone's lane is held by the read: the install waits, and the review's Install shows it working.
        assertTrue(rig.phone.installs.isEmpty())
        assertTrue(rig.presenter.state.value.busy)
        assertEquals("codex", rig.presenter.state.value.installingAgent?.id)
        // The read ends with what it saw before the install (Codex not installed); the install then writes its own.
        rig.phone.endRead(Unit.ok())
        assertEquals(listOf("codex"), rig.phone.installs)
        assertFalse(rig.presenter.state.value.busy)
        assertNull(rig.presenter.state.value.installingAgent, "the review closes once the install has started")
        assertEquals(AgentTileState.Installing, rig.codexTile())
        // The page shown again reads the phone after the install: still installing, never "Not installed" again.
        rig.presenter.refreshPhone()
        assertEquals(AgentTileState.Installing, rig.codexTile())
        assertEquals(listOf("codex"), rig.phone.installs)
    }

    @Test
    fun `with the helper down Install answers the first press and goes through the phone, not the helper`() = runTest {
        val helper = Helper(Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable)))
        val rig = Rig(this, LanePhone(claudeReady, codexMissing), helper)
        assertTrue(phoneHelperDown(rig.presenter.state.value))
        assertEquals(1, helper.calls)
        rig.presenter.requestInstall("codex")
        assertEquals("codex", rig.presenter.state.value.installingAgent?.id)
        rig.phone.endRead(Unit.ok())
        rig.presenter.installPhone("codex")
        assertEquals(listOf("codex"), rig.phone.installs)
        assertEquals(1, helper.calls, "the install asked nothing of the helper")
    }

    @Test
    fun `with the helper down Sign in opens the sign-in page on the first press`() = runTest {
        val helper = Helper(Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable)))
        val rig = Rig(this, LanePhone(claudeReady, codexSignIn), helper)
        assertTrue(phoneHelperDown(rig.presenter.state.value))
        rig.presenter.signInPhone("codex")
        assertEquals(listOf("codex"), rig.signIns)
        assertEquals(1, helper.calls)
    }

    @Test
    fun `the page's one retry, a Try again and a read together never start the helper twice at once`() = runTest {
        val helper = Helper(Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable)))
        val rig = Rig(this, LanePhone(claudeReady, codexMissing), helper)
        rig.phone.endRead(Unit.ok())
        assertEquals(1, helper.calls)
        assertTrue(rig.presenter.state.value.helperConnectFailed)
        // Shown again: the one automatic retry starts and is held open, and the read it brings probes slowly.
        helper.gate = CompletableDeferred()
        rig.phone.reading = CompletableDeferred()
        rig.presenter.refreshPhone()
        assertEquals(2, helper.calls)
        assertTrue(rig.presenter.state.value.helperConnecting)
        // Meanwhile: Try again, the page shown once more, and an Install press.
        rig.presenter.connectPhoneHelper()
        rig.presenter.refreshPhone()
        rig.presenter.requestInstall("codex")
        assertEquals("codex", rig.presenter.state.value.installingAgent?.id, "Install still answers at once")
        assertEquals(2, helper.calls, "nothing starts beside the running start")
        helper.answer = Unit.ok()
        helper.gate!!.complete(Unit)
        rig.phone.endRead(Unit.ok())
        // The Try again pressed meanwhile runs once after it; never two at once, never a loop.
        assertEquals(3, helper.calls)
        assertEquals(1, helper.mostAtOnce)
        assertTrue(rig.presenter.state.value.helperConnected)
        rig.presenter.refreshPhone()
        assertEquals(3, helper.calls)
    }

    @Test
    fun `a computer's New chat answers while a phone step waits for the phone`() = runTest {
        val rig = Rig(this, LanePhone(codexMissing))
        rig.presenter.requestInstall("codex")
        rig.presenter.installPhone("codex")
        assertTrue(rig.presenter.state.value.busy, "the install waits for the page's read")
        rig.presenter.newConversation(ProfileId("desk"))
        assertEquals(listOf(ConversationRef(ProfileId("desk"), SessionId("s1"))), rig.chats)
        rig.phone.endRead(Unit.ok())
    }

    @Test
    fun `a second New chat press while the first conversation is being made starts nothing more`() = runTest {
        val computers = Computers().also { it.starting = CompletableDeferred() }
        val rig = Rig(this, LanePhone(codexMissing), computers = computers)
        rig.presenter.newConversation(ProfileId("desk"))
        rig.presenter.newConversation(ProfileId("desk"))
        assertEquals(listOf(ProfileId("desk")), computers.starts)
        computers.starting!!.complete(Unit)
        assertEquals(1, rig.chats.size)
        rig.phone.endRead(Unit.ok())
    }
}
