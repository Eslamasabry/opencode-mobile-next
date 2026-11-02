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
                assertEquals(artifact.downloadBytes!! + artifact.installedBytes!!, installer.requiredBytes(agent.id, arch), agent.id.raw)
            }
        }
        val need = installer.requiredBytes(codex, AgentArchitecture.Arm64)!!
        val tight = FakeJobs().apply { free = need - 1 }
        val r = AgentInstaller(FakeAgentPrefs(), tight).install(owner, codex, AgentArchitecture.Arm64)
        assertEquals(InstallStart.Refused(InstallRefusal.NotEnoughSpace), (r as Outcome.Ok).value)
        val exact = FakeJobs().apply { free = need }
        assertEquals(InstallStart.Started, (AgentInstaller(FakeAgentPrefs(), exact).install(owner, codex, AgentArchitecture.Arm64) as Outcome.Ok).value)
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
}

private fun AgentSetupJob(agent: AgentId, state: AgentSetupJobState, component: String?, done: Long?, total: Long?) =
    AgentSetupJob(agent, state, component, done, total, null)
