@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.feature.agents.ui.installFailedWords
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsTileFailedSpace
import io.github.eslamasabry.oc.ui.words.res.agentsTileFailedStopped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Device QA 2026-11-11 (B16): Install on Gemini CLI read "Installing…" with no progress for about two minutes, then the
 * tile went back to "Not installed" with nothing said. A failed install now ends on its tile with the reason and Try
 * again, a read after it keeps it, an install that vanishes without a result reads as interrupted, a phone short of
 * space says so in the review before anything starts, and a running install names its step.
 */
class AgentsInstallResultTest {
    private object NoComputers : AgentComputers {
        override val computers = MutableStateFlow<ImmutableList<AgentComputer>>(persistentListOf())

        override suspend fun check(address: String, password: String, folder: String) = Outcome.Failed(Failure.TimedOut())

        override suspend fun add(name: String, address: String, password: String, folder: String) =
            Outcome.Failed(Failure.TimedOut())

        override suspend fun agents(id: ProfileId): Outcome<ImmutableList<String>> = Outcome.Failed(Failure.TimedOut())

        override suspend fun newConversation(id: ProfileId): Outcome<ConversationRef> = Outcome.Failed(Failure.TimedOut())
    }

    /**
     * A phone with the real one's lane (AppPhoneAgents: every read and step holds one mutex). [disk] is what the next
     * read finds: the test moves the install job along by changing it.
     */
    private class JobPhone(vararg rows: PhoneAgentRow) : PhoneAgents {
        override val state =
            MutableStateFlow(PhoneAgentsState(available = true, agents = persistentListOf(*rows), owner = ProfileId("owner")))
        private val lane = Mutex()
        var disk: PhoneAgentsState = state.value
        var space: PhoneAgentSpace? = null
        val installs = mutableListOf<String>()
        var dismissed = 0
        var cancels = 0

        /**
         * How many next reads give way to a step (AppPhoneAgents since w4-probes): they return Ok and publish nothing,
         * so "refresh returned Ok" does not mean the rows are final.
         */
        var giveWay = 0

        override suspend fun refresh(): Outcome<Unit> =
            lane.withLock {
                if (giveWay > 0) giveWay-- else state.value = disk
                Unit.ok()
            }

        override suspend fun installSpace(id: String): PhoneAgentSpace? = space

        override suspend fun install(id: String): Outcome<PhoneAgentInstallResult> =
            lane.withLock {
                installs += id
                disk =
                    disk.copy(
                        agents = disk.agents.map { if (it.id == id) it.copy(installable = false) else it }.toPersistentList(),
                        installation = PhoneAgentInstallation(id, PhoneAgentInstallPhase.Installing, null),
                    )
                state.value = disk
                PhoneAgentInstallResult.Started.ok()
            }

        override suspend fun dismissInstallResult(): Outcome<Unit> =
            lane.withLock {
                dismissed++
                disk = disk.copy(installation = null)
                Unit.ok()
            }

        override suspend fun cancelInstall(): Outcome<Unit> =
            lane.withLock {
                cancels++
                disk = endJob(disk, null)
                state.value = disk
                Unit.ok()
            }

        /** The job ended: [installation] is what the installer left (null for nothing), the agent still not there. */
        fun endJob(from: PhoneAgentsState, installation: PhoneAgentInstallation?): PhoneAgentsState =
            from.copy(
                installation = installation,
                agents = from.agents.map { it.copy(installable = true) }.toPersistentList(),
            )
    }

    private fun TestScope.presenter(phone: PhoneAgents) =
        AgentsPresenter(
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            NoComputers,
            phone,
            onBack = {},
            openChat = {},
        )

    private val gemini =
        PhoneAgentRow("gemini", "Gemini CLI", PhoneAgentStatus.NotInstalled, installable = true, downloadBytes = 21_000_000L)

    private fun AgentsPresenter.geminiTile(): AgentTileModel {
        val ui = state.value
        return agentTile(ui.phone.agents.single { it.id == "gemini" }, ui.phone.installation, quiet = ui.installQuiet)
    }

    private fun TestScope.installing(): Pair<JobPhone, AgentsPresenter> {
        val phone = JobPhone(gemini)
        val presenter = presenter(phone)
        presenter.requestInstall("gemini")
        presenter.installPhone("gemini")
        assertEquals(listOf("gemini"), phone.installs)
        assertEquals(AgentTileState.Installing, presenter.geminiTile().state)
        return phone to presenter
    }

    @Test
    fun `a job that fails ends on the tile with its reason and Try again, and a later read does not erase it`() = runTest {
        val (phone, presenter) = installing()
        phone.disk =
            phone.endJob(
                phone.disk,
                PhoneAgentInstallation("gemini", PhoneAgentInstallPhase.Failed, null, failure = PhoneAgentInstallFailure.Storage),
            )
        advanceTimeBy(2_100)
        runCurrent()
        val failed = presenter.geminiTile()
        assertEquals(AgentTileState.InstallFailed, failed.state)
        assertEquals(AgentTileAct.Retry, failed.act)
        assertEquals(Res.string.agentsTileFailedSpace, installFailedWords(failed))
        // The page shown again reads the phone: the failure stays (the installer keeps it until Try again or Dismiss).
        presenter.refreshPhone()
        assertEquals(AgentTileState.InstallFailed, presenter.geminiTile().state)
        // Try again opens the review at once; its Install starts the install once more.
        presenter.requestInstall("gemini")
        assertEquals("gemini", presenter.state.value.installingAgent?.id)
        presenter.installPhone("gemini")
        assertEquals(listOf("gemini", "gemini"), phone.installs)
        assertEquals(AgentTileState.Installing, presenter.geminiTile().state)
    }

    @Test
    fun `an install cut short by a time limit or a stop is a failure that says it stopped`() = runTest {
        val (phone, presenter) = installing()
        phone.disk = phone.endJob(phone.disk, PhoneAgentInstallation("gemini", PhoneAgentInstallPhase.Interrupted, null))
        advanceTimeBy(2_100)
        runCurrent()
        val stopped = presenter.geminiTile()
        assertEquals(AgentTileState.InstallFailed, stopped.state)
        assertEquals(AgentTileAct.Retry, stopped.act)
        assertEquals(Res.string.agentsTileFailedStopped, installFailedWords(stopped))
    }

    @Test
    fun `an install whose job disappears without a result reads as interrupted until it is dismissed`() = runTest {
        val (phone, presenter) = installing()
        // The job is gone and nothing was kept: the phone reads plain "not installed" again.
        phone.disk = phone.endJob(phone.disk, null)
        advanceTimeBy(2_100)
        runCurrent()
        assertEquals(AgentTileState.InstallFailed, presenter.geminiTile().state)
        assertEquals(PhoneAgentInstallPhase.Interrupted, presenter.state.value.phone.installation?.phase)
        presenter.refreshPhone()
        assertEquals(AgentTileState.InstallFailed, presenter.geminiTile().state, "a read does not erase it")
        presenter.dismissInstallResult()
        assertEquals(1, phone.dismissed)
        assertNull(presenter.state.value.phone.installation)
        assertEquals(AgentTileState.NotInstalled, presenter.geminiTile().state)
    }

    @Test
    fun `a failed result reaches the tile after reads that gave way, and a read that gives way never erases it`() =
        runTest {
            val (phone, presenter) = installing()
            phone.disk =
                phone.endJob(
                    phone.disk,
                    PhoneAgentInstallation(
                        "gemini",
                        PhoneAgentInstallPhase.Failed,
                        null,
                        failure = PhoneAgentInstallFailure.Download,
                    ),
                )
            // Two reads stopped by steps publish nothing: the tile still says installing, never a silent Not installed.
            phone.giveWay = 2
            advanceTimeBy(4_100)
            runCurrent()
            assertEquals(AgentTileState.Installing, presenter.geminiTile().state)
            assertNull(presenter.state.value.lostInstall)
            // The next read that finishes shows the failure the installer kept.
            advanceTimeBy(2_100)
            runCurrent()
            assertEquals(AgentTileState.InstallFailed, presenter.geminiTile().state)
            assertEquals(PhoneAgentInstallFailure.Download, presenter.state.value.phone.installation?.failure)
            // A later read that gives way leaves it as it is.
            phone.giveWay = 1
            presenter.refreshPhone()
            assertEquals(AgentTileState.InstallFailed, presenter.geminiTile().state)
        }

    @Test
    fun `a cancel the person asked for is not shown as a lost install`() = runTest {
        val (phone, presenter) = installing()
        presenter.cancelPhoneInstall()
        assertEquals(1, phone.cancels)
        presenter.refreshPhone()
        assertNull(presenter.state.value.phone.installation)
        assertEquals(AgentTileState.NotInstalled, presenter.geminiTile().state)
    }

    @Test
    fun `a phone short of space is caught in the review before anything starts`() = runTest {
        val phone = JobPhone(gemini).apply { space = PhoneAgentSpace(neededBytes = 300_000_000L, freeBytes = 200_000_000L) }
        val presenter = presenter(phone)
        presenter.requestInstall("gemini")
        assertEquals(phone.space, presenter.state.value.installSpace)
        assertTrue(presenter.state.value.installSpace!!.short)
        presenter.installPhone("gemini")
        assertTrue(phone.installs.isEmpty(), "an install that cannot fit never starts")
        assertEquals(PhoneAgentInstallIssue.Space, presenter.state.value.installIssue)
        assertEquals("gemini", presenter.state.value.installingAgent?.id, "the review stays open and says why")
        // Space freed: the same press starts it.
        phone.space = PhoneAgentSpace(neededBytes = 300_000_000L, freeBytes = 900_000_000L)
        presenter.installPhone("gemini")
        assertEquals(listOf("gemini"), phone.installs)
    }

    /**
     * B16's own numbers: 730 MB free, Gemini CLI's own room 300 MB, no phone agent installed yet. The shared parts
     * (Linux packages, Node.js, the helper) are still to come, so the review must say it cannot fit and keep Install
     * from starting; once another agent is installed only Gemini's own room is needed.
     */
    @Test
    fun `B16 numbers - 730 MB free and nothing shared installed keeps Install from starting, with the reason`() = runTest {
        val geminiOwn = 300_000_000L
        val free = 730_000_000L
        val fresh = persistentListOf(gemini, gemini.copy(id = "codex", name = "Codex"))
        assertFalse(phoneSharedPartsInPlace(fresh))
        val needed = io.github.eslamasabry.oc.engine.agents.install.AgentInstaller.withSharedParts(
            geminiOwn,
            phoneSharedPartsInPlace(fresh),
        )
        assertEquals(1_200_000_000L, needed)
        val phone = JobPhone(gemini).apply { space = PhoneAgentSpace(needed, free) }
        val presenter = presenter(phone)
        presenter.requestInstall("gemini")
        assertTrue(presenter.state.value.installSpace!!.short, "the review knows before the press")
        presenter.installPhone("gemini")
        assertTrue(phone.installs.isEmpty())
        assertEquals(PhoneAgentInstallIssue.Space, presenter.state.value.installIssue)

        // A row still being read proves nothing; an installed agent (any state past install) brought the shared parts.
        assertFalse(phoneSharedPartsInPlace(persistentListOf(gemini.copy(status = PhoneAgentStatus.Checking))))
        for (installed in
            listOf(
                PhoneAgentStatus.Ready,
                PhoneAgentStatus.SignInNeeded,
                PhoneAgentStatus.PhoneCheck,
                PhoneAgentStatus.Stopped,
                PhoneAgentStatus.Limit,
            )) {
            val rows = persistentListOf(gemini, gemini.copy(id = "codex", status = installed))
            assertTrue(phoneSharedPartsInPlace(rows), installed.name)
            assertFalse(
                PhoneAgentSpace(
                        io.github.eslamasabry.oc.engine.agents.install.AgentInstaller.withSharedParts(geminiOwn, true),
                        free,
                    )
                    .short
            )
        }
    }

    @Test
    fun `the running step and its bytes reach the tile, and a long quiet step says it is still working`() = runTest {
        val (phone, presenter) = installing()
        phone.disk =
            phone.disk.copy(
                installation =
                    PhoneAgentInstallation(
                        "gemini",
                        PhoneAgentInstallPhase.Installing,
                        null,
                        step = PhoneAgentInstallStep.Runtime,
                        doneBytes = 10_000_000L,
                        totalBytes = 30_000_000L,
                    )
            )
        advanceTimeBy(2_100)
        runCurrent()
        val tile = presenter.geminiTile()
        assertEquals(PhoneAgentInstallStep.Runtime, tile.install?.step)
        assertEquals(10_000_000L, tile.install?.doneBytes)
        assertEquals(30_000_000L, tile.install?.totalBytes)
        assertFalse(tile.quiet)
        // Nothing new for half a minute: still working, never a silent bar.
        advanceTimeBy(31_000)
        runCurrent()
        assertTrue(presenter.state.value.installQuiet)
        assertTrue(presenter.geminiTile().quiet)
        // A new byte count: the quiet note goes.
        phone.disk =
            phone.disk.copy(installation = phone.disk.installation!!.copy(step = PhoneAgentInstallStep.Agent, doneBytes = 1L))
        advanceTimeBy(2_100)
        runCurrent()
        assertFalse(presenter.state.value.installQuiet)
        assertEquals(PhoneAgentInstallStep.Agent, presenter.geminiTile().install?.step)
    }

    @Test
    fun `the setup job's components map to the steps the tile names`() {
        assertEquals(PhoneAgentInstallStep.Linux, phoneInstallStep("essentials", "gemini"))
        assertEquals(PhoneAgentInstallStep.Linux, phoneInstallStep("python", "gemini"))
        assertEquals(PhoneAgentInstallStep.Runtime, phoneInstallStep("agent-node", "gemini"))
        assertEquals(PhoneAgentInstallStep.Helper, phoneInstallStep("agent-paseo", "gemini"))
        assertEquals(PhoneAgentInstallStep.Agent, phoneInstallStep("agent-gemini", "gemini"))
        assertNull(phoneInstallStep(null, "gemini"))
        assertNull(phoneInstallStep("agent-codex", "gemini"))
    }
}
