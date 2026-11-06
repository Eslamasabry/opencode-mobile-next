package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Port of `test/api2_permission_dispatch_test.dart` (row B-PROTO-028). */
class Oc2PermissionDispatchTest {
    private val askAll = """{"action":"*","resource":"*","effect":"ask"}"""

    /** One session `s` as an OC2 server keeps it; [failure] picks which preparation step breaks. */
    private class Server(
        var session: JsonObject,
        val failure: String? = null,
        agentNotFoundFirst: Boolean = false,
        /** How many agent reads answer 404 first (the server still booting the project). */
        val agentNotFoundReads: Int = if (agentNotFoundFirst) 1 else 0,
    ) {
        val requests = mutableListOf<String>()
        val bodies = mutableListOf<JsonElement?>()
        var sessionReads = 0
        var agentReads = 0
    }

    private fun gateway(server: Server, dialect: Oc2Dialect = Oc2Dialect.Beta): Oc2Gateway =
        Oc2Gateway(
            NetClient(
                HttpClient(
                    MockEngine { request ->
                        val path = request.url.encodedPath
                        server.requests += request.method.value
                        val body = (request.body as? TextContent)?.text?.let { Json.parseToJsonElement(it) }
                        server.bodies += body
                        when {
                            request.method == HttpMethod.Get && path == "/api/agent/build" -> {
                                server.agentReads++
                                if (server.agentReads <= server.agentNotFoundReads) {
                                    respond("""{"_tag":"NotFoundError","message":"Agent not found"}""", HttpStatusCode.NotFound)
                                } else {
                                    respond(
                                        """{"data":{"id":"build","permissions":[{"action":"*","resource":"*","effect":"allow"}]}}""",
                                        if (server.failure == "agent") HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK,
                                    )
                                }
                            }
                            request.method == HttpMethod.Get -> {
                                server.sessionReads++
                                val data = if (server.failure == "agent changed" && server.sessionReads > 1) {
                                    JsonObject(server.session + ("agent" to JsonPrimitive("plan")))
                                } else server.session
                                respond(
                                    """{"data":$data}""",
                                    if (server.failure == "get") HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK,
                                )
                            }
                            request.method == HttpMethod.Patch -> {
                                if (server.failure != "readback") server.session = JsonObject(server.session + (body as JsonObject))
                                respond("", if (server.failure == "patch") HttpStatusCode.ServiceUnavailable else HttpStatusCode.NoContent)
                            }
                            path.endsWith("/prompt") ->
                                respond("""{"data":{"id":"msg_one","sessionID":"s","type":"user","payload":{"text":"write a file"}}}""", HttpStatusCode.OK)
                            else -> respond("", HttpStatusCode.NoContent)
                        }
                    },
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
            initialDialect = dialect,
        )

    private fun freshSession() = Json.parseToJsonElement("""{"id":"s","agent":"build","permissions":[],"metadata":{"keep":true}}""").jsonObject

    private val sends: Map<String, suspend (Oc2Gateway) -> Outcome<*>> = mapOf(
        "prompt" to { g -> g.prompt(SessionId("s"), OutgoingPrompt("write a file"), "c1") },
        "shell" to { g -> g.shell(SessionId("s"), "pwd", "build", null) },
        "command" to { g -> g.slashCommand(SessionId("s"), "help", "", null) },
    )

    private fun effects(session: JsonObject) = (session["permissions"] as JsonArray).map { (it as JsonObject)["effect"].toString().trim('"') }

    @Test
    fun `B-PROTO-028 every send kind installs and reads back the permission layer before it is sent`() = runTest {
        for ((kind, send) in sends) {
            val server = Server(freshSession())
            val g = gateway(server)
            assertIs<Outcome.Ok<*>>(send(g), kind)
            assertEquals(listOf("GET", "GET", "PATCH", "GET", "POST"), server.requests, kind)
            assertEquals(JsonPrimitive(true), (server.session["metadata"] as JsonObject)["keep"], "$kind: other metadata kept")
            assertEquals(listOf("ask", "ask"), effects(server.session), kind)
            g.close()
        }
    }

    @Test
    fun `B-PROTO-028 a failed read, agent read, write, read-back or a changed agent sends nothing`() = runTest {
        for ((kind, send) in sends) {
            for (failure in listOf("get", "agent", "patch", "readback", "agent changed")) {
                val server = Server(freshSession(), failure)
                val g = gateway(server)
                assertIs<Outcome.Failed>(send(g), "$kind/$failure")
                assertTrue(server.requests.none { it == "POST" }, "$kind/$failure: ${server.requests}")
                g.close()
            }
        }
    }

    private fun automaticSession(automatic: Boolean) = Json.parseToJsonElement(
        if (automatic) {
            """{"id":"s","agent":"build","permissions":[$askAll,$askAll],
               "metadata":{"oc.mobile.permissionRequests":{"version":1,"original":[],"applied":[$askAll,$askAll]}}}"""
        } else {
            """{"id":"s","agent":"build","permissions":[],"metadata":{}}"""
        },
    ).jsonObject

    @Test
    fun `B-PROTO-028 with automatic approval a message restores the server's rules before it is sent`() = runTest {
        val server = Server(automaticSession(true))
        val g = gateway(server)
        g.serverRunsTools = { it == SessionId("s") }
        assertIs<Outcome.Ok<*>>(g.prompt(SessionId("s"), OutgoingPrompt("write a file"), "c1"))
        assertEquals(listOf("GET", "GET", "PATCH", "GET", "POST"), server.requests)
        assertTrue((server.session["permissions"] as JsonArray).isEmpty())
        g.close()
    }

    @Test
    fun `B-PROTO-028 switching automatic approval off mid-reply turns requests back on`() = runTest {
        val server = Server(automaticSession(false))
        val g = gateway(server)
        g.serverRunsTools = { false }
        assertEquals(Outcome.Ok(Unit), g.syncSessionPermissions(SessionId("s")))
        assertEquals(listOf("GET", "GET", "PATCH", "GET"), server.requests)
        assertEquals(listOf("ask", "ask"), effects(server.session))
        g.close()
    }

    @Test
    fun `B-PROTO-028 a message right after the server boots the project waits for the agent instead of failing`() = runTest {
        val server = Server(Json.parseToJsonElement("""{"id":"s","agent":"build","permissions":[],"metadata":{}}""").jsonObject, agentNotFoundFirst = true)
        val g = gateway(server)
        assertIs<Outcome.Ok<*>>(g.prompt(SessionId("s"), OutgoingPrompt("hi"), "c1"))
        assertEquals(2, server.agentReads)
        assertEquals("POST", server.requests.last())
        g.close()
    }

    private fun agentSession() = Json.parseToJsonElement("""{"id":"s","agent":"build","permissions":[],"metadata":{}}""").jsonObject

    @Test
    fun `B-PROTO-028 B-111 right after a restart the agent read backs off for up to 20 s before the message is sent`() = runTest {
        // The phone server after an idle stop or an app update: 404 on the agent for about 10 s.
        val server = Server(agentSession(), agentNotFoundReads = 5)
        val g = gateway(server) // the event stream has not connected yet: the server is booting
        val started = testScheduler.currentTime
        assertIs<Outcome.Ok<*>>(g.prompt(SessionId("s"), OutgoingPrompt("hi"), "c1"))
        assertEquals(6, server.agentReads)
        assertEquals("POST", server.requests.last())
        assertTrue(testScheduler.currentTime - started <= 20_000, "bounded wait")
        g.close()
    }

    @Test
    fun `B-PROTO-028 B-111 an agent still missing after the boot window is not sent, with the real reason`() = runTest {
        val server = Server(agentSession(), agentNotFoundReads = Int.MAX_VALUE)
        val g = gateway(server)
        val started = testScheduler.currentTime
        val sent = assertIs<Outcome.Failed>(g.prompt(SessionId("s"), OutgoingPrompt("hi"), "c1"))
        assertIs<io.github.eslamasabry.oc.core.common.Failure.NotFound>(sent.failure)
        assertTrue(server.requests.none { it == "POST" }, "nothing sent")
        assertTrue(testScheduler.currentTime - started in 15_000..20_000, "waited ${testScheduler.currentTime - started} ms")
        g.close()
    }

    @Test
    fun `B-PROTO-028 B-111 long after the stream connected a missing agent fails after the short retry`() = runTest {
        val server = Server(agentSession(), agentNotFoundReads = 5)
        val g = gateway(server)
        g.lastConnectedAtMs = 1_000L - 120_000L // connected two minutes ago: not a boot
        assertIs<Outcome.Failed>(g.prompt(SessionId("s"), OutgoingPrompt("hi"), "c1"))
        assertEquals(3, server.agentReads)
        assertTrue(server.requests.none { it == "POST" })
        g.close()
    }

    @Test
    fun `B-PROTO-028 Auto-approve and Allow once send the decision once on the stable server`() = runTest {
        val server = Server(freshSession())
        val g = gateway(server, Oc2Dialect.Stable)
        assertEquals(Outcome.Ok(Unit), g.replyPermission(SessionId("s"), RequestId("per_shell"), PermissionReply.Once))
        assertEquals(Json.parseToJsonElement("""{"decision":"once"}"""), server.bodies.single())
        g.close()
    }
}
