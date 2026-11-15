package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.HistoryOpenGateway
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Issue 147 (Galaxy S22): Paseo 0.15.4 starts a saved agent's process whenever it is asked for its timeline. After the
 * helper started, the home's previews, the return brief and other readers asked for every earlier conversation's
 * timeline: six agent processes with nothing asked of them (90 conversations on the owner's phone).
 */
class PaseoHistoryNoWakeTest {
    private val stored = (1..6).map { agentJson("s$it", status = "idle", loaded = false) }

    private fun helper(): FakeDaemon =
        FakeDaemon().seedDefaults().apply {
            handlers["fetch_agents_request"] = {
                "fetch_agents_response" to jsonOf(
                    "entries" to stored.map { mapOf("agent" to it) },
                    "pageInfo" to mapOf("nextCursor" to null, "prevCursor" to null, "hasMore" to false),
                )
            }
            handlers["fetch_agent_request"] = { m ->
                "fetch_agent_response" to jsonOf("agent" to stored.first { it.str("id") == m.str("agentId") })
            }
            handlers["fetch_agent_timeline_request"] = { m ->
                "fetch_agent_timeline_response" to jsonOf("agentId" to m.str("agentId"), "error" to null, "entries" to emptyList<JsonObject>())
            }
        }

    @Test
    fun `I147-AGY-14 the home's reads of saved conversations start no agent, opening one reads exactly its history`() =
        runTest {
            val h = GatewayHarness(this, helper())
            h.gateway.startListening()
            h.pump()
            // The home feed and its previews (and the return brief, widgets, monitors) read every row.
            assertIs<Outcome.Ok<*>>(h.gateway.allSessions())
            for (agent in stored) assertIs<Outcome.Ok<*>>(h.gateway.messagePage(SessionId(agent.str("id")!!), null, 6))
            assertEquals(0, h.daemon.of("fetch_agent_timeline_request").size, "no saved agent was woken")
            // A person opens one conversation.
            (h.gateway as HistoryOpenGateway).historyOpened(SessionId("s3"))
            assertIs<Outcome.Ok<*>>(h.gateway.messagePage(SessionId("s3"), null, 100))
            val asked = h.daemon.of("fetch_agent_timeline_request")
            assertEquals(listOf("s3"), asked.map { it.str("agentId") })
        }

    @Test
    fun `I147-AGY-14b a running agent's history is read without being opened`() = runTest {
        val h = GatewayHarness(this, helper())
        h.gateway.startListening()
        h.pump()
        h.daemon.handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to agentJson("s1", status = "running")) }
        assertIs<Outcome.Ok<*>>(h.gateway.messagePage(SessionId("s1"), null, 6))
        assertTrue(h.daemon.of("fetch_agent_timeline_request").isNotEmpty())
    }
}
