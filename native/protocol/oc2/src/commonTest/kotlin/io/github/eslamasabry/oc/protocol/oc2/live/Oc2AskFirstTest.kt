package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Codex chat-header BLOCKER 1 (B-AGENT-221): with "Ask first", an OC2 session whose agent allows shell or edits used to
 * dispatch without an ask policy, so tools ran without a permission card. Flutter installs and reads back the policy
 * before every prompt, command and shell line (`lib/api2/gateway.dart` `_prepareSessionDispatch`).
 */
class Oc2AskFirstTest {
    private val order = mutableListOf<String>()

    private fun gateway(permissions: PermissionServer): Oc2Gateway =
        Oc2Gateway(
            NetClient(
                HttpClient(
                    MockEngine { request ->
                        order += "${request.method.value} ${request.url.encodedPath}"
                        with(permissions) {
                            answer(request) {
                                respond("""{"data":{"id":"inbox1","sessionID":"s","type":"user","clientMessageID":"c1"}}""", HttpStatusCode.OK)
                            }
                        }
                    }
                ) {
                    expectSuccess = false
                    followRedirects = false
                },
                { TransportError.Other },
            ),
            ServerOrigin("https", "example.test", 443),
            ConnectionCredentials.None,
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
            },
            Redactor(),
            initialDialect = Oc2Dialect.Beta,
        )

    private fun effects(server: PermissionServer): List<String> =
        (server.installed!!["permissions"] as JsonArray).map { ((it as JsonObject)["effect"] as JsonPrimitive).content }

    @Test
    fun `B-AGENT-221 a prompt installs ask-first before it is sent`() = runTest {
        val server = PermissionServer()
        val g = gateway(server)
        assertIs<Outcome.Ok<*>>(g.prompt(SessionId("s"), OutgoingPrompt("hello"), "c1"))
        assertEquals("ask", server.defaultEffect())
        assertTrue("allow" !in effects(server), "every allow becomes an ask: ${effects(server)}")
        val patch = order.indexOf("PATCH /api/session/s")
        val prompt = order.indexOf("POST /api/session/s/prompt")
        assertTrue(patch in 0 until prompt, "the policy is in place before the prompt: $order")
        assertEquals(1, order.count { it == "POST /api/session/s/prompt" })
        g.close()
    }

    @Test
    fun `B-AGENT-221 a server that does not keep the policy gets no prompt`() = runTest {
        val server = PermissionServer(honourPatch = false)
        val g = gateway(server)
        val failure = assertIs<Outcome.Failed>(g.prompt(SessionId("s"), OutgoingPrompt("hello"), "c1")).failure
        assertEquals(RefusalReason.ConsentMissing, assertIs<Failure.Refused>(failure).reason)
        assertTrue(order.none { it.endsWith("/prompt") }, "nothing sent: $order")
        g.close()
    }

    @Test
    fun `B-AGENT-221 an installed policy is not written again`() = runTest {
        val server = PermissionServer()
        val g = gateway(server)
        assertIs<Outcome.Ok<*>>(g.prompt(SessionId("s"), OutgoingPrompt("one"), "c1"))
        assertIs<Outcome.Ok<*>>(g.prompt(SessionId("s"), OutgoingPrompt("two"), "c2"))
        assertEquals(1, server.patches.size)
        g.close()
    }

    @Test
    fun `B-AGENT-221 a command and a shell line install ask-first too`() = runTest {
        val server = PermissionServer()
        val g = gateway(server)
        assertIs<Outcome.Ok<Unit>>(g.slashCommand(SessionId("s"), "/review", "", null))
        assertEquals("ask", server.defaultEffect())
        assertTrue(order.indexOf("PATCH /api/session/s") < order.indexOf("POST /api/session/s/command"))
        assertIs<Outcome.Ok<Unit>>(g.shell(SessionId("s"), "ls", "", null))
        assertTrue(order.any { it == "POST /api/session/s/shell" })
        g.close()
    }

    @Test
    fun `B-AGENT-221 a deny of the agent stays a deny`() = runTest {
        val server =
            PermissionServer(
                agentJson =
                    """{"id":"plan","hidden":false,"mode":"primary","permissions":[{"action":"edit","resource":"*","effect":"deny"}]}""",
            )
        val g = gateway(server)
        assertIs<Outcome.Ok<*>>(g.prompt(SessionId("s"), OutgoingPrompt("hello"), "c1"))
        assertEquals(listOf("ask", "deny"), effects(server))
        g.close()
    }

}
