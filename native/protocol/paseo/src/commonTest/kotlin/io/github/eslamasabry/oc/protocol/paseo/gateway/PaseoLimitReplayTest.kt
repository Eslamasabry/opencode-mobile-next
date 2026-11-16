package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.HistoryOpenGateway
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * signin-truth-2 (Galaxy, build 10055): the Claude Code conversations "Hi" and "Reply with OK" opened empty, where 10050
 * showed the reply "You've hit your weekly limit · resets Nov 20, 1am (UTC)". signin-truth made that line a usage-limit
 * error row. A REPLAYED conversation (its history read from the helper when the person opens it, the agent idle and not
 * loaded) keeps its prompt and that row: the history mapping drops nothing.
 */
class PaseoLimitReplayTest {
    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this, "expected Ok, got $this").value

    private val limit = "You've hit your weekly limit · resets Nov 20, 1am (UTC)"

    private fun helper(): FakeDaemon =
        FakeDaemon().seedDefaults().apply {
            val agent = agentJson("c1", status = "idle", loaded = false)
            handlers["fetch_agents_request"] = {
                "fetch_agents_response" to jsonOf(
                    "entries" to listOf(mapOf("agent" to agent)),
                    "pageInfo" to mapOf("nextCursor" to null, "prevCursor" to null, "hasMore" to false),
                )
            }
            handlers["fetch_agent_request"] = { "fetch_agent_response" to jsonOf("agent" to agent) }
            handlers["fetch_agent_timeline_request"] = { m ->
                "fetch_agent_timeline_response" to jsonOf(
                    "agentId" to m.str("agentId"),
                    "error" to null,
                    "entries" to listOf(
                        mapOf(
                            "provider" to "claude",
                            "item" to mapOf("type" to "user_message", "text" to "Hi", "messageId" to "u1"),
                            "timestamp" to "2026-11-15T15:19:00.000Z",
                            "seqStart" to 1,
                        ),
                        mapOf(
                            "provider" to "claude",
                            "item" to mapOf("type" to "assistant_message", "text" to limit, "messageId" to "a1"),
                            "timestamp" to "2026-11-15T15:19:00.500Z",
                            "seqStart" to 2,
                        ),
                    ),
                )
            }
        }

    @Test
    fun `I147-TRUTH-40 a replayed conversation whose last turn is the weekly limit keeps its prompt and the limit row`() =
        runTest {
            val h = GatewayHarness(this, helper())
            h.gateway.startListening()
            h.pump()
            assertIs<Outcome.Ok<*>>(h.gateway.allSessions())
            (h.gateway as HistoryOpenGateway).historyOpened(SessionId("c1"))
            val messages = h.gateway.messagePage(SessionId("c1"), null, 100).ok().items
            assertEquals(2, messages.size, "the prompt and the reply: $messages")
            assertEquals(Role.User, messages[0].info.role)
            assertEquals("Hi", messages[0].parts.single().text)
            val reply = messages[1]
            assertEquals(Role.Assistant, reply.info.role)
            assertEquals(limit, reply.info.errorText, "the limit line is kept for the row and its Details")
            assertNotEquals(MessageErrorKind.ProviderAuth, reply.info.errorKind)
            assertTrue(reply.info.time?.completedMs != null, "a finished reply, not one still streaming")
            assertEquals("claude", reply.info.providerId)
        }
}
