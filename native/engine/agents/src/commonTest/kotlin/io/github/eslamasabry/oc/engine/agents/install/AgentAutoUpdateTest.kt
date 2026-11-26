package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The agents' automatic update: the rule as a table, and the runner's once-per-run and not-while-busy behaviour. */
class AgentAutoUpdateTest {
    private val claude = AgentId("claude")
    private val codex = AgentId("codex")
    private val qwen = AgentId("qwen")

    @Test
    fun `AGENT-AUTO-UPDATE the target is the first installed agent that is behind, else any installed one`() {
        val installed = listOf(claude, codex, qwen)
        assertEquals(codex, AgentAutoUpdateRule.target(installed, setOf(codex, qwen), helperBehind = false))
        assertEquals(claude, AgentAutoUpdateRule.target(installed, setOf(claude, codex), helperBehind = true))
        // Everything on its pin and the helper too: nothing to do.
        assertNull(AgentAutoUpdateRule.target(installed, emptySet(), helperBehind = false))
        // Only the helper is behind: any installed agent's install brings it.
        assertEquals(claude, AgentAutoUpdateRule.target(installed, emptySet(), helperBehind = true))
        // Nothing installed: a behind helper alone is not a reason to install an agent.
        assertNull(AgentAutoUpdateRule.target(emptyList(), emptySet(), helperBehind = true))
        // An agent that is behind but not installed is never a target.
        assertNull(AgentAutoUpdateRule.target(listOf(claude), setOf(codex), helperBehind = false))
    }

    @Test
    fun `AGENT-AUTO-UPDATE the rule updates only an installed target and holds under every blocker`() {
        data class Row(val name: String, val target: AgentId?, val busy: Boolean = false, val setup: Boolean = false,
            val switch: Boolean = false, val problem: Boolean = false, val metered: Boolean = false,
            val tried: Boolean = false, val expect: Boolean)
        val rows = listOf(
            Row("behind and idle", claude, expect = true),
            Row("nothing behind", null, expect = false),
            Row("work running", claude, busy = true, expect = false),
            Row("a setup job runs", claude, setup = true, expect = false),
            Row("a switch is pending", claude, switch = true, expect = false),
            Row("an install is half finished", claude, problem = true, expect = false),
            Row("metered data", claude, metered = true, expect = false),
            Row("already tried this run", claude, tried = true, expect = false),
        )
        for (r in rows) {
            assertEquals(
                r.expect,
                AgentAutoUpdateRule.shouldUpdate(r.target, r.busy, r.setup, r.switch, r.problem, r.metered, r.tried),
                r.name,
            )
        }
    }

    private class Host : AgentUpdateHost {
        var refreshes = 0
        override suspend fun refresh() { refreshes++ }
        var due = false
        var reads = 0
        var afterRead: (() -> Unit)? = null
        override val readDue: Boolean get() = due
        var keepDueAfterRead = false
        override suspend fun read() {
            reads++
            if (!keepDueAfterRead) due = false
            afterRead?.invoke()
        }
        override var installed: List<AgentId> = listOf(AgentId("claude"))
        override var behind: Set<AgentId> = setOf(AgentId("claude"))
        override var helperBehind: Boolean = true
        override var pinKey: String = "paseo 0.17.3 claude 2.1.327"
        override var switchPending: Boolean = false
        override var installProblem: Boolean = false
        override var metered: Boolean = false
        override var resume: AgentId? = null

        /** The person's decision (the update sheet): which agents are chosen to be updated. All, unless a test says. */
        var allowed: Set<AgentId> = setOf(AgentId("claude"), AgentId("codex"), AgentId("qwen"))
        override fun updateAllowed(agent: AgentId): Boolean = agent in allowed
    }

    private class Jobs : AgentUpdateJobs {
        val started = mutableListOf<AgentId>()
        var accept = true
        override var running: Boolean = false
        override var failed: Boolean = false
        override suspend fun update(agent: AgentId): Boolean {
            if (!accept) return false
            started += agent
            return true
        }
    }

    @Test
    fun `AGENT-AUTO-UPDATE the runner starts the install once and not again for the same pins`() = runTest {
        val host = Host()
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host, jobs) { false }
        updater.changed()
        updater.changed()
        updater.changed()
        assertEquals(listOf(claude), jobs.started)
        // New pins (the next app build) are a new try.
        host.pinKey = "paseo 0.15.5 claude 2.1.322"
        updater.changed()
        assertEquals(listOf(claude, claude), jobs.started)
    }

    @Test
    fun `AGENT-AUTO-UPDATE nothing moves without a decision, and the chosen agent alone runs once it is given`() = runTest {
        val host = Host().apply {
            installed = listOf(claude, codex)
            behind = setOf(claude, codex)
            allowed = emptySet()
        }
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host, jobs) { false }
        updater.changed()
        updater.changed()
        // No decision: no install starts and nothing is spent, however often it looks.
        assertTrue(jobs.started.isEmpty())
        host.allowed = setOf(codex)
        updater.changed()
        // Only the agent the person chose; Claude was not chosen and is never started.
        assertEquals(listOf(codex), jobs.started)
    }

    @Test
    fun `AGENT-AUTO-UPDATE every agent that is behind gets its own one try, in turn`() = runTest {
        val host = Host().apply {
            installed = listOf(claude, codex)
            behind = setOf(claude, codex)
        }
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host, jobs) { false }
        updater.changed()
        assertEquals(listOf(claude), jobs.started)
        // Claude's install brought the helper too; Codex is behind and still gets its try.
        host.helperBehind = false
        updater.changed()
        assertEquals(listOf(claude, codex), jobs.started)
        updater.changed()
        assertEquals(listOf(claude, codex), jobs.started)
    }

    @Test
    fun `AGENT-AUTO-UPDATE the runner does not start while work runs and starts when it ends`() = runTest {
        val host = Host()
        val jobs = Jobs()
        var busy = true
        val updater = AgentAutoUpdater(host, jobs) { busy }
        updater.changed()
        assertTrue(jobs.started.isEmpty())
        // Held back before reading anything: a busy phone is not asked to read its agents.
        assertEquals(0, host.refreshes)
        busy = false
        updater.changed()
        assertEquals(listOf(claude), jobs.started)
    }

    @Test
    fun `AGENT-AUTO-UPDATE a refused start is not spent and a metered network holds the update`() = runTest {
        val host = Host()
        val jobs = Jobs().apply { accept = false }
        val updater = AgentAutoUpdater(host, jobs) { false }
        updater.changed()
        assertTrue(jobs.started.isEmpty())
        jobs.accept = true
        updater.changed()
        assertEquals(listOf(claude), jobs.started)
        val second = Jobs()
        host.pinKey = "other"
        host.metered = true
        AgentAutoUpdater(host, second) { false }.changed()
        assertTrue(second.started.isEmpty())
        host.metered = false
        host.installProblem = true
        AgentAutoUpdater(host, second) { false }.changed()
        assertFalse(second.started.isNotEmpty())
    }

    @Test
    fun `AGENT-AUTO-UPDATE no facts means one read even while the agents are busy, and the update waits for idle`() =
        runTest {
            // Nothing is known (the person never opened the Agents page): no installed agent, nothing behind.
            val host = Host().apply { installed = emptyList(); behind = emptySet(); helperBehind = false; due = true }
            host.afterRead = {
                host.installed = listOf(claude)
                host.behind = setOf(claude)
                // The read finds the helper behind too: an install that replaces the helper is the one that waits for
                // idle (an agent-only update does not, since 3d7f396ce2; see the test further down).
                host.helperBehind = true
            }
            val jobs = Jobs()
            var busy = true
            val updater = AgentAutoUpdater(host, jobs) { busy }
            // A stale busy reading must not keep the version read from happening: it only asks each agent its version.
            updater.changed()
            assertEquals(1, host.reads)
            assertTrue(jobs.started.isEmpty())
            assertEquals(AgentUpdateStep.WaitingAgentsBusy, updater.decision.value.step)
            busy = false
            updater.changed()
            assertEquals(listOf(claude), jobs.started)
            assertEquals(AgentUpdateStep.Updating, updater.decision.value.step)
            // One read only, however often it looks again.
            host.due = true
            updater.changed()
            assertEquals(1, host.reads)
        }

    @Test
    fun `AGENT-AUTO-UPDATE the decision says what the updater waits for`() = runTest {
        val host = Host().apply { due = false }
        val jobs = Jobs()
        var busy = false
        val updater = AgentAutoUpdater(host, jobs) { busy }
        host.metered = true
        updater.changed()
        assertEquals(AgentUpdateStep.WaitingMetered, updater.decision.value.step)
        assertEquals("agent update: waiting, download deferred", updater.decision.value.line())
        host.metered = false
        jobs.running = true
        updater.changed()
        assertEquals(AgentUpdateStep.WaitingSetup, updater.decision.value.step)
        jobs.running = false
        host.switchPending = true
        updater.changed()
        assertEquals(AgentUpdateStep.WaitingSwitch, updater.decision.value.step)
        host.switchPending = false
        busy = true
        updater.changed()
        assertEquals("agent update: waiting, agents busy", updater.decision.value.line())
        busy = false
        updater.changed()
        assertEquals("agent update: started claude", updater.decision.value.line())
        host.behind = emptySet()
        host.helperBehind = false
        jobs.running = false
        updater.changed()
        assertEquals(AgentUpdateStep.NothingBehind, updater.decision.value.step)
    }

    @Test
    fun `AGENT-AUTO-UPDATE an update the app was stopped in the middle of runs again once, even with nothing known`() =
        runTest {
            // Force-stopped mid-install: the install forgot the agent's kept facts, so nothing counts as installed or
            // behind; the interrupted update is still resumed, once.
            val host = Host().apply {
                installed = emptyList(); behind = emptySet(); helperBehind = false; resume = claude
            }
            val jobs = Jobs()
            val updater = AgentAutoUpdater(host, jobs) { false }
            updater.changed()
            assertEquals(listOf(claude), jobs.started)
            updater.changed()
            assertEquals(listOf(claude), jobs.started)
        }

    @Test
    fun `AGENT-AUTO-UPDATE an agent-only update does not wait for agents that are busy, a helper update does`() =
        runTest {
            val host = Host().apply {
                installed = listOf(claude, codex); behind = setOf(codex); helperBehind = false
            }
            val jobs = Jobs()
            // Claude's conversation is busy; Codex's own files are replaced now, the helper is untouched.
            AgentAutoUpdater(host, jobs) { true }.changed()
            assertEquals(listOf(codex), jobs.started)
            val second = Jobs()
            host.pinKey = "other"
            host.helperBehind = true
            AgentAutoUpdater(host, second) { true }.changed()
            assertTrue(second.started.isEmpty())
        }

    @Test
    fun `AGENT-AUTO-UPDATE the line says updated for the agent whose install ended`() = runTest {
        val host = Host()
        val updater = AgentAutoUpdater(host, Jobs()) { false }
        updater.changed()
        updater.finished(claude)
        assertEquals(AgentUpdateStep.Updated, updater.decision.value.step)
        assertEquals("agent update: finished claude", updater.decision.value.line())
        updater.changed()
        assertEquals(AgentUpdateStep.Updated, updater.decision.value.step)
    }

    @Test
    fun `AGENT-AUTO-UPDATE a read that cannot finish is reported and not repeated this run`() = runTest {
        val host = Host().apply { due = true }
        host.keepDueAfterRead = true
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host, jobs) { false }
        updater.changed()
        assertEquals(AgentUpdateStep.ReadFailed, updater.decision.value.step)
        updater.changed()
        assertEquals(1, host.reads)
    }

    @Test
    fun `AGENT-AUTO-UPDATE a read that finds everything on its pins starts nothing`() = runTest {
        val host = Host().apply { behind = emptySet(); helperBehind = false; due = true }
        val jobs = Jobs()
        AgentAutoUpdater(host, jobs) { false }.changed()
        assertEquals(1, host.reads)
        assertTrue(jobs.started.isEmpty())
    }

    @Test
    fun `AGENT-AUTO-UPDATE nothing happens when every installed agent and the helper are on their pins`() = runTest {
        val host = Host().apply { behind = emptySet(); helperBehind = false }
        val jobs = Jobs()
        AgentAutoUpdater(host, jobs) { false }.changed()
        assertTrue(jobs.started.isEmpty())
        assertEquals(0, host.refreshes)
    }

    @Test
    fun `DOWNLOAD-CONSENT Download now runs the helper's install on a metered network once, and Wait keeps the hold`() =
        runTest {
            val host = Host().apply { due = false; metered = true }
            val jobs = Jobs()
            val updater = AgentAutoUpdater(host, jobs) { false }
            repeat(3) { updater.changed() }
            assertTrue(jobs.started.isEmpty())
            assertEquals(AgentUpdateStep.WaitingMetered, updater.decision.value.step)
            updater.allowMeteredOnce()
            updater.changed()
            assertEquals(listOf(claude), jobs.started)
            assertEquals(AgentUpdateStep.Updating, updater.decision.value.step)
        }

    @Test
    fun `DOWNLOAD-CONSENT the agents' consent is spent by its install and does not pass to the next pin`() = runTest {
        val host = Host().apply { due = false; metered = true }
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host, jobs) { false }
        updater.allowMeteredOnce()
        updater.changed()
        assertEquals(1, jobs.started.size)
        host.pinKey = "paseo 9.9.9 claude 9.9.9"
        updater.changed()
        assertEquals(1, jobs.started.size)
        assertEquals(AgentUpdateStep.WaitingMetered, updater.decision.value.step)
    }

    @Test
    fun `DOWNLOAD-CONSENT becoming unmetered while it waits runs the install`() = runTest {
        val host = Host().apply { due = false; metered = true }
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host, jobs) { false }
        updater.changed()
        assertTrue(jobs.started.isEmpty())
        host.metered = false
        updater.changed()
        assertEquals(listOf(claude), jobs.started)
    }
}
