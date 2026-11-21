package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Issue 147: an install of the pinned version from another artifact is behind and is updated like any update. */
class AgentInstallRecordTest {
    private val catalog = AgentCatalog.builtIn
    private val codex = AgentId("codex")
    private val arm = AgentArchitecture.Arm64
    private val codexAgent = catalog.byId(codex)!!

    /** The record and program the phones with Codex 0.169.0 from the bare binary have. */
    private val oldBare =
        AgentInstallRecorded(
            "0.169.0|arm64|97b97d87eea075c5cb7709c7f6156a451622a7667953ecda40e0276637196f3f",
            "/home/oc/.local/share/oc-agents/codex/0.169.0/payload/codex-aarch64-unknown-linux-musl",
        )

    @Test
    fun `B-AGENT-UPD-001 the same version with an old checksum is behind, an equal record is not`() {
        assertEquals(setOf(codex), AgentInstallRecord.behind(listOf(codex), mapOf(codex to oldBare), catalog, arm))
        val current =
            AgentInstallRecorded(
                AgentInstallRecord.expectedRecord(codexAgent, arm),
                AgentInstallRecord.expectedProgram(codexAgent, arm),
            )
        assertEquals("/home/oc/.local/share/oc-agents/codex/0.171.0/payload/bin/codex", current.program)
        assertTrue(AgentInstallRecord.behind(listOf(codex), mapOf(codex to current), catalog, arm).isEmpty())
        // The right artifact but a launcher that runs something else is behind too.
        val otherProgram = current.copy(program = oldBare.program)
        assertEquals(setOf(codex), AgentInstallRecord.behind(listOf(codex), mapOf(codex to otherProgram), catalog, arm))
        // Neither file (installed some other way) or not installed: left alone.
        assertTrue(AgentInstallRecord.behind(listOf(codex), mapOf(codex to AgentInstallRecorded(null, null)), catalog, arm).isEmpty())
        assertTrue(AgentInstallRecord.behind(emptyList(), mapOf(codex to oldBare), catalog, arm).isEmpty())
    }

    @Test
    fun `B-AGENT-UPD-002 the update plan installs the package for an old record, waiting while the agents work`() = runTest {
        val records: Set<AgentId> = AgentInstallRecord.behind(listOf(codex), mapOf(codex to oldBare), catalog, arm)
        val host =
            object : AgentUpdateHost {
                override suspend fun refresh() {}
                override suspend fun read() {}
                override val readDue: Boolean = false
                override val installed: List<AgentId> = listOf(codex)
                // As the binding computes it: version check (nothing) + install record (old checksum).
                override val behind: Set<AgentId> = records
                override val reinstall: Set<AgentId> = records
                override val helperBehind: Boolean = false
                override val pinKey: String = "codex 0.169.0 c8d0b4ab2a97"
                override val switchPending: Boolean = false
                override val installProblem: Boolean = false
                override val metered: Boolean = false
                // The person chose Update for it on the sheet (the choice gate).
                override fun updateAllowed(agent: AgentId): Boolean = true
            }
        val started = mutableListOf<AgentId>()
        val jobs =
            object : AgentUpdateJobs {
                override val running: Boolean = false
                override val failed: Boolean = false
                override suspend fun update(agent: AgentId): Boolean = started.add(agent)
            }
        var busy = true
        val updater = AgentAutoUpdater(host, jobs) { busy }
        updater.changed()
        // A reinstall of the same version replaces the folder the running agent uses: never under live work.
        assertTrue(started.isEmpty())
        assertEquals(AgentUpdateStep.WaitingAgentsBusy, updater.decision.value.step)
        busy = false
        updater.changed()
        assertEquals(listOf(codex), started)
        assertEquals(AgentUpdateStep.Updating, updater.decision.value.step)
        assertEquals(codex, updater.decision.value.agent)
    }

    @Test
    fun `B-AGENT-UPD-003 an install with the app's launcher but no record at all is behind`() {
        val launch = "#!/bin/sh\nexec \"/home/oc/.local/share/oc-agents/codex/0.169.0/payload/codex-aarch64-unknown-linux-musl\" \"\$@\"\n"
        val noRecord = AgentInstallRecord.fromFiles(pin = null, launch = launch)
        assertEquals(null, noRecord.record)
        assertEquals(oldBare.program, noRecord.program)
        assertEquals(setOf(codex), AgentInstallRecord.behind(listOf(codex), mapOf(codex to noRecord), catalog, arm))
        // The files as the install script writes them read back exactly.
        val read = AgentInstallRecord.fromFiles("0.169.0|arm64|97b97d87eea075c5cb7709c7f6156a451622a7667953ecda40e0276637196f3f", launch)
        assertEquals(oldBare, read)
        // npm agents run through node: the program is the script the launcher names.
        val node = "#!/bin/sh\nexec /home/oc/.local/node/bin/node \"/x/payload/package/bundle/gemini.js\" \"\$@\"\n"
        assertEquals("/x/payload/package/bundle/gemini.js", AgentInstallRecord.fromFiles(null, node).program)
    }
}
