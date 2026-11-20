package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.install.AgentAutoUpdater
import io.github.eslamasabry.oc.engine.agents.install.AgentInstallRecord
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateHost
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateJobs
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStep
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoSessionStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Galaxy S22, build 10048: Codex 0.169.0 installed as the bare binary was not replaced. The helper held two earlier
 * Codex conversations loaded (not working). A loaded agent that runs no turn is idle: its reinstall starts at once.
 */
class AgentReinstallIdleTest {
    private val codex = AgentId("codex")
    private val launch =
        "#!/bin/sh\nexec \"/home/oc/.local/share/oc-agents/codex/0.169.0/payload/codex-aarch64-unknown-linux-musl\" \"\$@\"\n"

    @Test
    fun `B-AGENT-UPD-010 agents loaded but idle do not hold the reinstall, a running turn does`() = runTest {
        // The helper's records of the two loaded Codex conversations and four Antigravity ones: loaded, no turn.
        val loaded =
            listOf(
                """{"id":"a1","provider":"codex","status":"idle"}""",
                """{"id":"a2","provider":"codex","status":"initializing","activeTurn":null}""",
                """{"id":"a3","provider":"antigravity","status":"idle"}""",
            ).map { Json.parseToJsonElement(it).jsonObject }
        val now = 1_800_000_000_000L
        var statuses = loaded.map { paseoSessionStatus(it, now) }
        assertEquals(List(3) { SessionStatus.Idle }, statuses)

        // No record at all, as an install made before records were written would be.
        val recorded = mapOf(codex to AgentInstallRecord.fromFiles(null, launch))
        val behind = AgentInstallRecord.behind(listOf(codex), recorded, AgentCatalog.builtIn, AgentArchitecture.Arm64)
        val host =
            object : AgentUpdateHost {
                override suspend fun refresh() {}
                override val readDue: Boolean = false
                override suspend fun read() {}
                override val installed: List<AgentId> = listOf(codex)
                override val behind: Set<AgentId> = behind
                override val reinstall: Set<AgentId> = behind
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
        // A running turn of a loaded agent (an active turn) holds it.
        val working = Json.parseToJsonElement("""{"id":"a1","status":"running","activeTurn":{"id":"t"}}""").jsonObject
        statuses = listOf(paseoSessionStatus(working, now))
        val updater = AgentAutoUpdater(host, jobs) { statuses.any { it == SessionStatus.Busy } }
        updater.changed()
        assertEquals(AgentUpdateStep.WaitingAgentsBusy, updater.decision.value.step)
        assertEquals(emptyList(), started)
        // The turn ended; the agents stay loaded but idle: the reinstall starts.
        statuses = loaded.map { paseoSessionStatus(it, now) }
        updater.changed()
        assertEquals(listOf(codex), started)
        assertEquals(AgentUpdateStep.Updating, updater.decision.value.step)
    }
}
