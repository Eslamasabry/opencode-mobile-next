package io.github.eslamasabry.oc.engine.agents.diagnostics

import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.engine.agents.host.AgentHelperExit
import io.github.eslamasabry.oc.engine.agents.host.HelperExitKind
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentHost
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/agent_helper_diagnostics_test.dart (B-AGENT-022). */
class HelperDiagnosticsTest {
    private val at = 1_791_633_600_000L // 2026-10-09T12:00:00Z

    private fun record(running: Boolean = false, requested: String = "false", at: String = this.at.toString()): JsonObject =
        Json.parseToJsonElement(
            """{"running":$running,"lastExitCode":137,"lastExitAtMs":$at,"lastStopRequested":$requested,""" +
                """"exitReason":"memory_or_phantom_kill"}""",
        ).jsonObject

    @Test
    fun `B-AGENT-022 helper metadata survives restart with a cautious plain notice`() {
        val stopped = AgentHelperStatus.fromRecord(record())
        assertEquals(at, stopped.lastExitAtEpochMillis)
        assertEquals(137L, stopped.lastExitCode)
        assertTrue(stopped.unexpectedStop)
        assertEquals(HelperNotice.PossiblyStoppedByAndroid, stopped.notice)
        assertNull(AgentHelperStatus.fromRecord(record(running = true)).notice)
        for (requested in listOf("true", "null")) assertNull(AgentHelperStatus.fromRecord(record(requested = requested)).notice)
        assertNull(AgentHelperStatus.fromRecord(JsonObject(emptyMap())).notice)
    }

    @Test
    fun `B-AGENT-022 malformed and old metadata cannot invent a dated kill`() {
        for (stamp in listOf("null", "-1", "0", "1.5", "1e3", "8640000000000001", "\"secret\"")) {
            assertNull(AgentHelperStatus.fromRecord(record(at = stamp)).lastExitAtEpochMillis, stamp)
        }
        assertNull(AgentHelperStatus.fromRecord(Json.parseToJsonElement("""{"running":"false"}""").jsonObject).running)
        val other = JsonObject(record().toMutableMap().apply { put("exitReason", Json.parseToJsonElement("\"secret\"")) })
        assertEquals(HelperNotice.StoppedUnexpectedly, AgentHelperStatus.fromRecord(other).notice)
    }

    @Test
    fun `B-AGENT-022 recent exits name only observed unexpected helper exits without profile data`() {
        val services: Map<String, JsonElement> = mapOf(
            "agent-host.private-profile" to record(running = true),
            "agent-auth.other" to record(),
            "server" to record(),
            "agent-host.stopped" to record(requested = "true"),
            "agent-host.old-apk" to Json.parseToJsonElement("""{"lastExitCode":137}"""),
        )
        val exits = HelperDiagnostics.exitsFromServices(services, limit = 10)
        assertEquals(listOf(AgentHelperExit(at, HelperExitKind.LimitOrMemory)), exits)
        assertFalse("private-profile" in exits.toString())
        assertTrue(HelperDiagnostics.exitsFromServices(services, limit = 0).isEmpty())
        assertEquals(1, HelperDiagnostics.exitsFromServices(services, limit = 1).size)
    }

    @Test
    fun `B-AGENT-022 diagnostics read the host's exits newest first, never requested stops, clamped`() = runTest {
        val host = FakeAgentHost()
        host.exits = listOf(
            AgentHelperExit(1_000, HelperExitKind.Unexpected),
            AgentHelperExit(3_000, HelperExitKind.Requested),
            AgentHelperExit(2_000, HelperExitKind.LimitOrMemory),
        )
        val diagnostics = HelperDiagnostics(host)
        assertEquals(listOf(2_000L, 1_000L), diagnostics.recentExits(10).getOrNull()!!.map { it.atEpochMillis })
        assertEquals(listOf(2_000L), diagnostics.recentExits(1).getOrNull()!!.map { it.atEpochMillis })
        assertTrue(diagnostics.recentExits(-5).getOrNull()!!.isEmpty())
    }
}
