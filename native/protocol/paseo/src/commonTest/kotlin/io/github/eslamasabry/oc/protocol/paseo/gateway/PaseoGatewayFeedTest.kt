@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.feed.PaseoChatFeed
import io.github.eslamasabry.oc.protocol.paseo.feed.PaseoFeedStatus
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The chat feed (slice D) over the real gateway and a scripted daemon: the integration of B-AGENT-149/153/154/181. */
class PaseoGatewayFeedTest {
    @Test
    fun `B-AGENT-153 the feed lists this folder's agents through the gateway and hides other folders`() = runTest {
        val h = GatewayHarness(this)
        val feed = PaseoChatFeed(h.gateway.feedPort(), backgroundScope)
        feed.refresh()
        val rows = feed.snapshot().rows
        assertEquals(listOf("a1"), rows.map { it.sessionId.raw })
        assertEquals("claude", rows.single().agentId)
        assertTrue(feed.snapshot().complete)
    }

    @Test
    fun `B-AGENT-154 a pending permission makes its row need you, without details`() = runTest {
        val h = GatewayHarness(this)
        val feed = PaseoChatFeed(h.gateway.feedPort(), backgroundScope)
        feed.refresh()
        h.daemon.push("agent_permission_request", jsonOf("agentId" to "a1", "request" to writePermission("perm-1")))
        h.pump()
        assertEquals(PaseoFeedStatus.NeedsYou, feed.snapshot().rows.single().status)
    }

    @Test
    fun `B-AGENT-181 an untitled record takes the title saved for its conversation`() = runTest {
        val daemon = FakeDaemon().seedDefaults()
        daemon.handlers["fetch_agents_request"] = {
            "fetch_agents_response" to jsonOf(
                "entries" to listOf(mapOf("agent" to agentJson("a2", title = null))),
                "pageInfo" to mapOf("hasMore" to false),
            )
        }
        val h = GatewayHarness(this, daemon)
        h.gateway.savedTitle = { key -> if (key == "claude:native-a2") "Fix the parser" else null }
        val session = (h.gateway.allSessions() as io.github.eslamasabry.oc.core.common.Outcome.Ok).value.single()
        assertEquals("Fix the parser", session.title)
        // Without a saved title the stand-in stays.
        val other = GatewayHarness(this, FakeDaemon().seedDefaults().also { d ->
            d.handlers["fetch_agents_request"] = {
                "fetch_agents_response" to jsonOf("entries" to listOf(mapOf("agent" to agentJson("a3", title = null))), "pageInfo" to mapOf("hasMore" to false))
            }
        })
        assertEquals("Claude Code conversation", (other.gateway.allSessions() as io.github.eslamasabry.oc.core.common.Outcome.Ok).value.single().title)
    }

    @Test
    fun `B-AGENT-149 an idle older copy is archived once, a busy one is left alone, a failed archive keeps the list`() = runTest {
        val daemon = FakeDaemon().seedDefaults()
        daemon.handlers["fetch_agents_request"] = {
            "fetch_agents_response" to jsonOf(
                "entries" to listOf(
                    mapOf("agent" to agentJson("old", status = "idle")),
                    mapOf("agent" to agentJson("busy", status = "running")),
                ),
                "pageInfo" to mapOf("hasMore" to false),
            )
        }
        var archives = 0
        daemon.handlers["archive_agent_request"] = { m ->
            archives++
            "agent_archived" to jsonOf("agentId" to m.str("agentId"))
        }
        val h = GatewayHarness(this, daemon)
        h.gateway.allSessions()
        val port = h.gateway.feedPort()
        assertFalse(port.archiveOlderCopy(SessionId("busy")))
        assertTrue(port.archiveOlderCopy(SessionId("old")))
        assertFalse(port.archiveOlderCopy(SessionId("old")))
        assertEquals(1, archives)
        // A refused archive is false and the record stays listed.
        daemon.handlers["archive_agent_request"] = { "rpc_error" to jsonOf("error" to "nope") }
        h.gateway.allSessions()
        h.daemon.push("agent_update", jsonOf("kind" to "upsert", "agent" to agentJson("busy", status = "idle")))
        h.pump()
        assertFalse(port.archiveOlderCopy(SessionId("busy")))
        assertTrue(port.sessionPage(null, 50).items.any { it.id.raw == "busy" })
    }
}
