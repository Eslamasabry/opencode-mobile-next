package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.FakeSockets
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue 147 (Galaxy A54, Android 16): a working Antigravity conversation "disconnects in the middle of a task". When
 * Android stops the agent helper (or the phone's Linux) the app starts it again; the helper then has only the agent's
 * saved record (no `activeTurn`, often still "running"), and the turn is gone. Before, the reply read as finished
 * (`RunEnded(Succeeded)`, at once or after three more minutes of "working") with no word about what happened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaseoHelperRestartTurnTest {
    private fun restartedHelper(a1: JsonObject): FakeDaemon =
        FakeDaemon().seedDefaults().apply {
            handlers["fetch_agents_request"] = {
                "fetch_agents_response" to jsonOf(
                    "entries" to listOf(mapOf("agent" to a1)),
                    "pageInfo" to mapOf("nextCursor" to null, "prevCursor" to null, "hasMore" to false),
                )
            }
            handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to a1) }
        }

    /** The agent works (a loaded, running snapshot), then the socket drops and [after] answers the new one. */
    private fun TestScope.runThenReconnect(after: JsonObject): GatewayHarness {
        val first = FakeDaemon().seedDefaults()
        val h = GatewayHarness(this, first, FakeSockets(first, restartedHelper(after)))
        h.gateway.startListening()
        h.pump()
        first.push("agent_update", jsonOf("kind" to "upsert", "agent" to agentJson("a1", status = "running")))
        h.pump()
        assertEquals(1, h.events.count { it == GatewayEvent.RunStarted(SessionId("a1")) }, "the turn ran")
        h.events.clear()
        first.drop()
        h.pump()
        advanceTimeBy(1_001)
        h.pump()
        return h
    }

    private fun GatewayHarness.ends(): List<RunOutcome> =
        events.filterIsInstance<GatewayEvent.RunEnded>().filter { it.sessionId == SessionId("a1") }.map { it.outcome }

    @Test
    fun `I147-AGY-1 a turn the restarted helper only has a running record of ends as stopped by a restart`() = runTest {
        val h = runThenReconnect(agentJson("a1", status = "running", loaded = false))
        assertEquals(listOf<RunOutcome>(RunOutcome.Interrupted(InterruptReason.Shutdown)), h.ends())
        // Never "working" for three more minutes, then a finished reply.
        advanceTimeBy(200_000)
        h.pump()
        assertEquals(listOf<RunOutcome>(RunOutcome.Interrupted(InterruptReason.Shutdown)), h.ends())
    }

    @Test
    fun `I147-AGY-1b a turn whose record was saved idle before the helper restarted ends as finished`() = runTest {
        val h = runThenReconnect(agentJson("a1", status = "idle", loaded = false))
        assertEquals(listOf<RunOutcome>(RunOutcome.Succeeded), h.ends())
    }

    @Test
    fun `I147-AGY-1c a dropped socket with the helper still running the turn ends nothing`() = runTest {
        val h = runThenReconnect(agentJson("a1", status = "running"))
        assertEquals(emptyList(), h.ends())
    }

    @Test
    fun `I147-AGY-1d a turn that finished while the socket was down still ends as finished`() = runTest {
        val h = runThenReconnect(agentJson("a1", status = "idle"))
        assertEquals(listOf<RunOutcome>(RunOutcome.Succeeded), h.ends())
    }
}
