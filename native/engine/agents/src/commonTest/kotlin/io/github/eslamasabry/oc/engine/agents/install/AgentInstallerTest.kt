package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeJobs : AgentSetupJobs {
    var running: Boolean? = false
    var free: Long? = Long.MAX_VALUE
    var job: AgentSetupJob? = null
    var startFails = false
    val calls = mutableListOf<String>()
    var started: List<AgentSetupComponent> = emptyList()

    override suspend fun anySetupRunning(): Boolean? = running
    override suspend fun freeBytes(): Long? = free
    override suspend fun start(owner: OwnerId, agent: AgentId, components: List<AgentSetupComponent>): Outcome<Unit> {
        calls += "start:${owner.raw}:${agent.raw}"
        if (startFails) return Failure.Device().failed()
        started = components
        job = AgentSetupJob(agent, AgentSetupJobState.Running, components.first().id, null, null, null)
        return Unit.ok()
    }
    override suspend fun job(owner: OwnerId): Outcome<AgentSetupJob?> = job.ok()
    override suspend fun cancel(owner: OwnerId, agent: AgentId): Outcome<Unit> { calls += "cancel:${owner.raw}:${agent.raw}"; return Unit.ok() }
}

class AgentInstallerTest {
    private val owner = ProfileId("owner")
    private val codex = AgentId("codex")

    @Test
    fun `B-AGENT-120 the install runs the pinned components in order, every agent component as the agent user`() = runTest {
        val jobs = FakeJobs()
        val prefs = FakeAgentPrefs()
        val r = AgentInstaller(prefs, jobs).install(owner, codex, AgentArchitecture.Arm64)
        assertEquals(InstallStart.Started, (r as Outcome.Ok).value)
        assertEquals(
            listOf("linux", "essentials", "python", "agent-user", "agent-node", "agent-paseo", "agent-codex"),
            jobs.started.map { it.id },
        )
        assertEquals(listOf("agent-node", "agent-paseo", "agent-codex"), jobs.started.filter { it.agentUser }.map { it.id })
        assertEquals(listOf("agent-paseo", "python"), jobs.started.last().dependsOn)
        assertEquals("codex", prefs.string("oc.agentInstall.owner"))
    }

    @Test
    fun `B-LINUX-132 the marker is committed before the job starts and a refused commit starts nothing`() = runTest {
        val jobs = FakeJobs()
        val prefs = FakeAgentPrefs().apply { refuseWrites = true }
        assertIs<Outcome.Failed>(AgentInstaller(prefs, jobs).install(owner, codex, AgentArchitecture.Arm64))
        assertEquals(emptyList(), jobs.calls)
    }

    @Test
    fun `B-LINUX-132 another setup running, or unknown, refuses with setup busy`() = runTest {
        for (state in listOf(true, null)) {
            val jobs = FakeJobs().apply { running = state }
            val r = AgentInstaller(FakeAgentPrefs(), jobs).install(owner, codex, AgentArchitecture.Arm64)
            assertEquals(InstallStart.Refused(InstallRefusal.SetupBusy), (r as Outcome.Ok).value)
            assertEquals(emptyList(), jobs.calls)
        }
    }

    @Test
    fun `B-LINUX-136 the storage guard reserves the download plus the extracted size of each catalog install`() = runTest {
        val installer = AgentInstaller(FakeAgentPrefs(), FakeJobs())
        for (agent in AgentCatalog.builtIn.agents.filter { it.runsOnPhoneHost }) {
            for (arch in AgentArchitecture.entries) {
                val artifact = agent.artifactFor(arch)!!
                // The retained archive and the extracted payload coexist, plus 64 MiB of scratch (Dart
                // requiredSetupFreeBytes); never less than the legacy 300 MB / 2x policy.
                // The guard reserves for the largest download of the agent, whichever architecture installs.
                val download = agent.recipe!!.artifacts.values.maxOf { it.downloadBytes!! }
                val peak = download + artifact.installedBytes!! + 64L * 1024 * 1024
                val expected = maxOf(maxOf(download * 2, 300L * 1000 * 1000), peak)
                assertEquals(expected, installer.requiredBytes(agent.id, arch), agent.id.raw)
            }
        }
        val need = installer.requiredBytes(codex, AgentArchitecture.Arm64)!!
        val tight = FakeJobs().apply { free = need - 1 }
        val r = AgentInstaller(FakeAgentPrefs(), tight).install(owner, codex, AgentArchitecture.Arm64)
        assertEquals(InstallStart.Refused(InstallRefusal.NotEnoughSpace), (r as Outcome.Ok).value)
        val exact = FakeJobs().apply { free = need }
        assertEquals(InstallStart.Started, (AgentInstaller(FakeAgentPrefs(), exact).install(owner, codex, AgentArchitecture.Arm64) as Outcome.Ok).value)
    }

    /**
     * Device QA B16 (2026-11-11): Gemini CLI's own room (300 MB) fitted in the emulator's 730 MB, but the shared parts
     * the job installs first were not counted, each later step refused below its floor, and the install failed two
     * minutes in. The figure checked before starting counts every shared part not in place at the job's floor.
     */
    @Test
    fun `before starting, the shared parts not yet on the phone are counted at the job's floor each`() {
        val installer = AgentInstaller(FakeAgentPrefs(), FakeJobs())
        val gemini = AgentId("gemini")
        for (arch in AgentArchitecture.entries) {
            val own = installer.requiredBytes(gemini, arch)!!
            assertEquals(own, installer.requiredBytesBeforeStart(gemini, arch, sharedPartsInPlace = true))
            val fresh = installer.requiredBytesBeforeStart(gemini, arch, sharedPartsInPlace = false)!!
            assertEquals(own + 3 * 300_000_000L, fresh, arch.name)
            // The device's numbers: the old figure let the install start; the honest one does not.
            val deviceFree = 730_000_000L
            assertTrue(own <= deviceFree, "the agent's own room fitted, which is why the old review said yes")
            assertTrue(fresh > deviceFree, "with Linux packages, Node.js and the helper still to come it does not")
        }
        assertEquals(Long.MAX_VALUE, AgentInstaller.withSharedParts(Long.MAX_VALUE - 1, sharedPartsInPlace = false))
        assertNull(installer.requiredBytesBeforeStart(AgentId("nope"), AgentArchitecture.Arm64, sharedPartsInPlace = false))
    }

    @Test
    fun `B-LINUX-135 restore re-attaches to the same owner's running job and clears a marker pointing at nothing`() = runTest {
        val jobs = FakeJobs()
        val prefs = FakeAgentPrefs(mapOf("oc.agentInstall.owner" to "codex"))
        jobs.job = AgentSetupJob(codex, AgentSetupJobState.Running, "agent-codex", 50, 200, null)
        val attached = (AgentInstaller(prefs, jobs).restore(owner) as Outcome.Ok).value
        assertEquals(AgentSetupPhase.Installing, attached.phase)
        assertEquals(0.25, attached.fraction)
        assertEquals("codex", prefs.string("oc.agentInstall.owner"))
        for (ended in listOf(null, AgentSetupJob(codex, AgentSetupJobState.Done, null, null, null, null),
            AgentSetupJob(codex, AgentSetupJobState.Failed, null, null, null, AgentSetupFailure.Checksum),
            AgentSetupJob(AgentId("gemini"), AgentSetupJobState.Running, null, null, null, null))) {
            val p = FakeAgentPrefs(mapOf("oc.agentInstall.owner" to "codex"))
            jobs.job = ended
            AgentInstaller(p, jobs).restore(owner)
            assertNull(p.string("oc.agentInstall.owner"), ended.toString())
        }
    }

    @Test
    fun `a failed install stays on the page, with its kind, until the next attempt or until it is dismissed`() = runTest {
        val jobs = FakeJobs()
        val prefs = FakeAgentPrefs(mapOf("oc.agentInstall.owner" to "codex"))
        jobs.job = AgentSetupJob(codex, AgentSetupJobState.Failed, "agent-codex", null, null, AgentSetupFailure.Checksum)
        val first = (AgentInstaller(prefs, jobs).restore(owner) as Outcome.Ok).value
        assertEquals(AgentSetupPhase.Failed, first.phase)
        // The marker is gone after that read, as before; the result is not.
        assertNull(prefs.string("oc.agentInstall.owner"))
        val later = AgentInstaller(prefs, jobs)
        val again = (later.restore(owner) as Outcome.Ok).value
        assertEquals(AgentSetupPhase.Failed, again.phase)
        assertEquals(codex, again.agentId)
        assertEquals(AgentSetupFailure.Checksum, again.failure)
        later.dismissResult(owner)
        assertEquals(AgentSetupPhase.Idle, (later.restore(owner) as Outcome.Ok).value.phase)
    }

    @Test
    fun `an interrupted install is kept the same way, and a new attempt clears the old result`() = runTest {
        val jobs = FakeJobs()
        val prefs = FakeAgentPrefs(mapOf("oc.agentInstall.owner" to "codex"))
        jobs.job = AgentSetupJob(codex, AgentSetupJobState.Interrupted, "agent-codex", null, null, null)
        AgentInstaller(prefs, jobs).restore(owner)
        val kept = (AgentInstaller(prefs, jobs).restore(owner) as Outcome.Ok).value
        assertEquals(AgentSetupPhase.Interrupted, kept.phase)
        assertEquals(codex, kept.agentId)
        val installer = AgentInstaller(prefs, jobs)
        assertEquals(InstallStart.Started, (installer.install(owner, codex, AgentArchitecture.Arm64) as Outcome.Ok).value)
        assertEquals(AgentSetupPhase.Installing, (installer.restore(owner) as Outcome.Ok).value.phase)
        jobs.job = AgentSetupJob(codex, AgentSetupJobState.Done, null, null, null, null)
        installer.restore(owner)
        assertEquals(AgentSetupPhase.Idle, (AgentInstaller(prefs, jobs).restore(owner) as Outcome.Ok).value.phase)
    }

    @Test
    fun `a marker left by the Flutter app, with no job of this app behind it, shows no interrupted install`() = runTest {
        for (other in listOf(null, AgentSetupJob(AgentId("gemini"), AgentSetupJobState.Done, null, null, null, null))) {
            val jobs = FakeJobs().apply { job = other }
            val prefs = FakeAgentPrefs(mapOf("oc.agentInstall.owner" to "claude"))
            val read = (AgentInstaller(prefs, jobs).restore(owner) as Outcome.Ok).value
            assertEquals(AgentSetupPhase.Idle, read.phase)
            assertNull(prefs.string("oc.agentInstall.owner"))
            assertNull(prefs.string("oc.agentInstallResult.owner"))
            assertEquals(AgentSetupPhase.Idle, (AgentInstaller(prefs, jobs).restore(owner) as Outcome.Ok).value.phase)
        }
    }

    @Test
    fun `a followed install whose job disappears is kept as interrupted, never a silent not installed`() = runTest {
        // Device QA B16: the tile read "Installing…", then plain "Not installed" with nothing said.
        for (gone in listOf(null, AgentSetupJob(AgentId("gemini"), AgentSetupJobState.Done, null, null, null, null))) {
            val jobs = FakeJobs()
            val prefs = FakeAgentPrefs()
            val installer = AgentInstaller(prefs, jobs)
            val started = installer.install(owner, codex, AgentArchitecture.Arm64)
            assertEquals(InstallStart.Started, (started as Outcome.Ok).value)
            assertEquals(AgentSetupPhase.Installing, (installer.restore(owner) as Outcome.Ok).value.phase)
            jobs.job = gone
            val ended = (installer.restore(owner) as Outcome.Ok).value
            assertEquals(AgentSetupPhase.Interrupted, ended.phase, gone.toString())
            assertEquals(codex, ended.agentId)
            assertNull(prefs.string("oc.agentInstall.owner"))
            // Every later read, by this installer or a new one after a restart, still shows it until it is dismissed.
            assertEquals(AgentSetupPhase.Interrupted, (installer.restore(owner) as Outcome.Ok).value.phase)
            val restarted = AgentInstaller(prefs, jobs).restore(owner)
            assertEquals(AgentSetupPhase.Interrupted, (restarted as Outcome.Ok).value.phase)
            installer.dismissResult(owner)
            assertEquals(AgentSetupPhase.Idle, (installer.restore(owner) as Outcome.Ok).value.phase)
        }
    }

    @Test
    fun `a job that fails while followed keeps its kind across every later read`() = runTest {
        val jobs = FakeJobs()
        val prefs = FakeAgentPrefs()
        val installer = AgentInstaller(prefs, jobs)
        installer.install(owner, codex, AgentArchitecture.Arm64)
        jobs.job = AgentSetupJob(codex, AgentSetupJobState.Running, "agent-node", 5, 50, null)
        val running = (installer.observe(owner, jobs.job) as Outcome.Ok).value
        assertEquals("agent-node", running.componentId)
        assertEquals(5L, running.downloadedBytes)
        jobs.job = AgentSetupJob(codex, AgentSetupJobState.Failed, "agent-codex", null, null, AgentSetupFailure.Storage)
        repeat(3) {
            val read = (installer.restore(owner) as Outcome.Ok).value
            assertEquals(AgentSetupPhase.Failed, read.phase)
            assertEquals(AgentSetupFailure.Storage, read.failure)
        }
    }

    @Test
    fun `B-LINUX-135 cancel stops only the owned job and is a no-op without a marker`() = runTest {
        val jobs = FakeJobs()
        AgentInstaller(FakeAgentPrefs(), jobs).cancel(owner)
        assertEquals(emptyList(), jobs.calls)
        val prefs = FakeAgentPrefs(mapOf("oc.agentInstall.owner" to "codex"))
        val installer = AgentInstaller(prefs, jobs)
        installer.cancel(owner)
        assertEquals(listOf("cancel:owner:codex"), jobs.calls)
        assertNull(prefs.string("oc.agentInstall.owner"))
        assertEquals(AgentSetupPhase.Interrupted, installer.progress.value.phase)
    }

    @Test
    fun `B-AGENT-124 the bar is determinate only from measured bytes and failures stay typed`() = runTest {
        val prefs = FakeAgentPrefs(mapOf("oc.agentInstall.owner" to "codex"))
        val installer = AgentInstaller(prefs, FakeJobs())
        val unknown = (installer.observe(owner, AgentSetupJob(codex, AgentSetupJobState.Running, "agent-node", 10, null)) as Outcome.Ok).value
        assertNull(unknown.fraction)
        val failed = (installer.observe(owner, AgentSetupJob(codex, AgentSetupJobState.Failed, "agent-codex", 10, 20, AgentSetupFailure.Checksum)) as Outcome.Ok).value
        assertEquals(AgentSetupPhase.Failed, failed.phase)
        assertEquals(AgentSetupFailure.Checksum, failed.failure)
        assertNull(prefs.string("oc.agentInstall.owner"))
    }

    @Test
    fun `B-AGENT-120 server types and unknown agents are never installed as phone agents`() = runTest {
        val jobs = FakeJobs()
        for (id in listOf("opencode2", "missing")) {
            val r = AgentInstaller(FakeAgentPrefs(), jobs).install(owner, AgentId(id), AgentArchitecture.Arm64)
            assertEquals(InstallStart.Refused(InstallRefusal.NotInstallable), (r as Outcome.Ok).value)
        }
        assertTrue(jobs.calls.isEmpty())
    }

    @Test
    fun `B-AGENT-120 a retired agent is refused before anything starts`() = runTest {
        val jobs = FakeJobs()
        val r = AgentInstaller(FakeAgentPrefs(), jobs).install(owner, AgentId("gemini"), AgentArchitecture.Arm64)
        assertEquals(InstallStart.Refused(InstallRefusal.NotInstallable), (r as Outcome.Ok).value)
        assertTrue(jobs.calls.isEmpty())
    }
}

private fun AgentSetupJob(agent: AgentId, state: AgentSetupJobState, component: String?, done: Long?, total: Long?) =
    AgentSetupJob(agent, state, component, done, total, null)
