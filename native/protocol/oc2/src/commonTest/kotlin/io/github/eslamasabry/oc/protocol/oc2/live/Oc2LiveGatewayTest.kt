package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.net.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class Oc2LiveGatewayTest {
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
        }

    private fun gateway(engine: MockEngine, dialect: Oc2Dialect? = Oc2Dialect.Beta): Oc2Gateway =
        Oc2Gateway(
            NetClient(
                HttpClient(engine) {
                    expectSuccess = false
                    followRedirects = false
                },
                { TransportError.Other },
            ),
            ServerOrigin("https", "example.test", 443),
            ConnectionCredentials("opencode", "test-secret"),
            clock,
            Redactor(),
            initialDialect = dialect,
        )

    @Test
    fun `B-PROTO-129 redirect is refused and never followed`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://other.test"))
        }
        val gateway = gateway(engine)
        assertIs<Outcome.Failed>(gateway.health())
        assertEquals(1, calls)
        gateway.close()
    }

    @Test
    fun `B-PROTO-042 mismatched prompt receipt is uncertain without a retry`() = runTest {
        var sends = 0
        val engine = MockEngine { request ->
            assertEquals(
                "Basic b3BlbmNvZGU6dGVzdC1zZWNyZXQ=",
                request.headers[HttpHeaders.Authorization],
            )
            if (request.method == HttpMethod.Get)
                respond("""{"data":{"id":"s","title":"Test"}}""", HttpStatusCode.OK)
            else {
                sends++
                assertEquals("/api/session/s/prompt", request.url.encodedPath)
                assertTrue((request.body as TextContent).text.contains("\"text\":\"hello\""))
                respond(
                    """{"data":{"id":"other","sessionID":"s","type":"user"}}""",
                    HttpStatusCode.OK,
                )
            }
        }
        val gateway = gateway(engine)
        assertEquals(
            PromptAdmission.Uncertain,
            (gateway.prompt(SessionId("s"), OutgoingPrompt("hello"), "ours") as Outcome.Ok).value,
        )
        assertEquals(1, sends)
        gateway.close()
    }

    @Test
    fun `B-PROTO-043 staged revert refuses prompt before dispatch`() = runTest {
        var sends = 0
        val engine = MockEngine { request ->
            if (request.method != HttpMethod.Get) sends++
            respond("""{"data":{"id":"s","revert":{"messageID":"m"}}}""", HttpStatusCode.OK)
        }
        val gateway = gateway(engine)
        assertIs<Outcome.Failed>(gateway.prompt(SessionId("s"), OutgoingPrompt("hello")))
        assertEquals(0, sends)
        gateway.close()
    }

    @Test
    fun `B-PROTO-135 paged history retains canonical content ids and cursor`() = runTest {
        val engine = MockEngine {
            respond(
                """{"data":[{"id":"a","type":"assistant","content":[{"type":"text","text":"world"},{"type":"reasoning","text":"thought"}]},{"id":"u","type":"user","text":"hello"}],"cursor":{"next":"opaque"}}""",
                HttpStatusCode.OK,
            )
        }
        val gateway = gateway(engine)
        val page = (gateway.messagePage(SessionId("s"), limit = 2) as Outcome.Ok).value
        assertEquals("opaque", page.nextCursor)
        assertEquals(listOf("text-0", "reasoning-0"), page.items[1].parts.map { it.id })
        assertEquals(listOf(PartType.Text, PartType.Reasoning), page.items[1].parts.map { it.type })
        gateway.close()
    }

    @Test
    fun `B-PROTO-146 malformed active snapshot never means idle`() = runTest {
        val engine = MockEngine {
            respond("""{"data":{"s":{"type":"mystery"}}}""", HttpStatusCode.OK)
        }
        val gateway = gateway(engine)
        assertIs<Outcome.Failed>(gateway.sessionStatuses())
        gateway.close()
    }

    @Test
    fun `B-PROTO-146 beta health qualifies while OC1 and invented beta tags fail`() = runTest {
        for ((version, supported) in
            listOf(
                "0.0.0-beta-19242" to true,
                "2.0.29" to true,
                "1.19.0" to false,
                "0.0.0-beta-oops" to false,
            )) {
            val engine = MockEngine {
                respond("{\"healthy\":true,\"version\":\"$version\"}", HttpStatusCode.OK)
            }
            val gateway = gateway(engine, null)
            assertEquals(supported, gateway.health() is Outcome.Ok, version)
            gateway.close()
        }
    }

    @Test
    fun `B-PROTO-043 stale scope after preflight refuses prompt without dispatch`() = runTest {
        var checks = 0
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            checks++
            respond("""{"data":{"id":"s"}}""", HttpStatusCode.OK)
        }
        val gateway = gateway(engine)
        val result =
            gateway.promptWithMessageId(SessionId("s"), MessageId("m"), OutgoingPrompt("hello")) {
                false
            }
        assertEquals(Failure.Refused(RefusalReason.StaleScope), (result as Outcome.Failed).failure)
        assertEquals(1, checks)
        gateway.close()
    }

    @Test
    fun `B-PROTO-042 unreadable response after prompt remains uncertain and sends once`() =
        runTest {
            var sends = 0
            val engine = MockEngine { request ->
                if (request.method == HttpMethod.Get)
                    respond("""{"data":{"id":"s"}}""", HttpStatusCode.OK)
                else {
                    sends++
                    respond("not json", HttpStatusCode.OK)
                }
            }
            val gateway = gateway(engine)
            assertEquals(
                PromptAdmission.Uncertain,
                (gateway.prompt(SessionId("s"), OutgoingPrompt("hello")) as Outcome.Ok).value,
            )
            assertEquals(1, sends)
            gateway.close()
        }

    @Test
    fun `B-PROTO-146 interrupt uses continue query and reports no running turn honestly`() =
        runTest {
            val engine = MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/api/session/s/interrupt", request.url.encodedPath)
                assertEquals("true", request.url.parameters["continue"])
                respond("""{"interrupted":false}""", HttpStatusCode.OK)
            }
            val gateway = gateway(engine)
            assertEquals(
                StopResult.NothingRunning,
                (gateway.stop(SessionId("s"), resume = true) as Outcome.Ok).value,
            )
            gateway.close()
        }

    @Test
    fun `B-PROTO-135 creating session without a folder omits empty location`() = runTest {
        val engine = MockEngine { request ->
            val body = (request.body as TextContent).text
            assertFalse(body.contains("location"))
            respond("""{"data":{"id":"s"}}""", HttpStatusCode.OK)
        }
        val gateway = gateway(engine)
        assertIs<Outcome.Ok<Session>>(gateway.createSession())
        gateway.close()
    }

    @Test
    fun `B-PROTO-135 public model inventory joins models without reading provider config`() =
        runTest {
            val paths = mutableListOf<String>()
            val engine = MockEngine { request ->
                paths += request.url.encodedPath
                val body =
                    when (request.url.encodedPath) {
                        "/api/provider" -> """{"data":[{"id":"p","name":"Provider"}]}"""
                        "/api/model" ->
                            """{"data":[{"id":"p/model","providerID":"p","enabled":true}]}"""
                        "/api/model/default" -> """{"data":{"id":"p/model","providerID":"p"}}"""
                        else -> error("Unexpected inventory route")
                    }
                respond(body, HttpStatusCode.OK)
            }
            val gateway = gateway(engine)
            val inventory = (gateway.providers() as Outcome.Ok).value
            assertEquals(listOf("model"), inventory.providers.single().modelIds)
            assertEquals("p", inventory.defaultProviderId)
            assertEquals("model", inventory.defaultModelId)
            assertEquals(listOf("/api/provider", "/api/model", "/api/model/default"), paths)
            gateway.close()
        }

    @Test
    fun `B-PROTO-033 permission actions use captured session and request ids`() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                assertEquals("/api/permission/request", request.url.encodedPath)
                respond(
                    """{"data":[{"id":"r","sessionID":"s","action":"shell","resources":["echo ok"]}]}""",
                    HttpStatusCode.OK,
                )
            } else {
                assertEquals("/api/session/s/permission/r/reply", request.url.encodedPath)
                assertEquals(
                    "{\"reply\":\"reject\",\"message\":\"No thanks\"}",
                    (request.body as TextContent).text,
                )
                respond("null", HttpStatusCode.OK)
            }
        }
        val gateway = gateway(engine)
        val permission = (gateway.pendingPermissions() as Outcome.Ok).value.single()
        assertEquals(SessionId("s"), permission.sessionId)
        assertEquals(RequestId("r"), permission.id)
        assertIs<Outcome.Ok<Unit>>(
            gateway.replyPermission(
                permission.sessionId,
                permission.id,
                PermissionReply.Reject,
                "No thanks",
            )
        )
        gateway.close()
    }

    @Test
    fun `B-PROTO-135 history retains special rows and reconstructs stored attachments`() {
        val history = Oc2History(Redactor())
        fun decode(text: String) =
            history.message(
                "s",
                requireNotNull(io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire.parse(text)),
            )!!
        val instructions =
            decode(
                """{"id":"m","type":"system","text":"private instructions","description":"Instructions updated for this session"}"""
            )
        assertEquals(PartType.Notice, instructions.parts.single().type)
        assertEquals("instructions", instructions.parts.single().noticeMetadata["kind"])
        assertEquals("", instructions.parts.single().text)
        val user =
            decode("""{"id":"u","type":"user","files":[{"data":"aGVsbG8=","mime":"text/plain"}]}""")
        assertEquals("data:text/plain;base64,aGVsbG8=", user.parts.single().url)
    }

    @Test
    fun `B0 QA wave1 every notice message gets its own part id`() {
        val history = Oc2History(Redactor())
        val ids =
            listOf("m1", "m2", "m3").map { id ->
                history
                    .message(
                        "s",
                        requireNotNull(
                            io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire.parse(
                                """{"id":"$id","type":"model-switched","model":{"id":"glm","providerID":"zai"}}"""
                            )
                        ),
                    )!!
                    .parts
                    .single()
                    .id
            }
        assertEquals(3, ids.toSet().size, "notice part ids: $ids")
    }

    @Test
    fun `B-PROTO-039 health negotiates beta and stable before permission and interrupt mutations`() =
        runTest {
            for (stable in listOf(false, true)) {
                val paths = mutableListOf<String>()
                val engine = MockEngine { request ->
                    paths += request.url.encodedPath
                    when (request.url.encodedPath) {
                        "/api/health" ->
                            if (stable) respond("{}", HttpStatusCode.NotFound)
                            else
                                respond(
                                    """{"healthy":true,"version":"0.0.0-beta-19242"}""",
                                    HttpStatusCode.OK,
                                )
                        "/api/info" -> {
                            assertTrue(stable)
                            respond("""{"version":"2.0.29"}""", HttpStatusCode.OK)
                        }
                        "/api/session/s/permission/r/reply" -> {
                            assertEquals(HttpMethod.Post, request.method)
                            val body = (request.body as TextContent).text
                            assertEquals(
                                if (stable) "{\"decision\":\"once\"}" else "{\"reply\":\"once\"}",
                                body,
                            )
                            respond("null", HttpStatusCode.OK)
                        }
                        "/api/session/s/interrupt" -> {
                            assertEquals(
                                "true",
                                request.url.parameters[if (stable) "resume" else "continue"],
                            )
                            assertNull(request.url.parameters[if (stable) "continue" else "resume"])
                            respond("""{"interrupted":true}""", HttpStatusCode.OK)
                        }
                        "/api/session/s",
                        "/api/session/s/rename" -> {
                            assertEquals(
                                if (stable) "/api/session/s" else "/api/session/s/rename",
                                request.url.encodedPath,
                            )
                            assertEquals(
                                if (stable) HttpMethod.Patch else HttpMethod.Post,
                                request.method,
                            )
                            respond("null", HttpStatusCode.OK)
                        }
                        else -> error("Unexpected route")
                    }
                }
                val gateway = gateway(engine, null)
                // The first mutation negotiates through read-only health before any write is
                // admitted.
                assertIs<Outcome.Ok<Unit>>(
                    gateway.replyPermission(SessionId("s"), RequestId("r"), PermissionReply.Once)
                )
                assertEquals(
                    StopResult.Stopped,
                    (gateway.stop(SessionId("s"), resume = true) as Outcome.Ok).value,
                )
                assertIs<Outcome.Ok<Unit>>(gateway.renameSession(SessionId("s"), "Renamed"))
                assertEquals(
                    if (stable) listOf("/api/health", "/api/info") else listOf("/api/health"),
                    paths.take(if (stable) 2 else 1),
                )
                assertEquals(1, paths.count { it == "/api/health" })
                assertEquals(if (stable) 1 else 0, paths.count { it == "/api/info" })
                gateway.close()
            }
        }

    @Test
    fun `B-PROTO-039 failed generation read never dispatches a mutation or probes info after auth refusal`() =
        runTest {
            val paths = mutableListOf<String>()
            val engine = MockEngine { request ->
                paths += request.url.encodedPath
                assertEquals(HttpMethod.Get, request.method)
                respond("{}", HttpStatusCode.Unauthorized)
            }
            val gateway = gateway(engine, null)
            assertIs<Outcome.Failed>(gateway.createSession())
            assertEquals(listOf("/api/health"), paths)
            gateway.close()
        }

    @Test
    fun `B-PROTO-025 stable permission 404 is never retried as beta`() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(
                "{\"decision\":\"reject\",\"message\":\"No\"}",
                (request.body as TextContent).text,
            )
            respond("{}", HttpStatusCode.NotFound)
        }
        val gateway = gateway(engine, Oc2Dialect.Stable)
        assertIs<Outcome.Failed>(
            gateway.replyPermission(SessionId("s"), RequestId("r"), PermissionReply.Reject, "No")
        )
        assertEquals(1, calls)
        gateway.close()
    }

    @Test
    fun `B-PROTO-024 forms remain unavailable without network on either dialect`() = runTest {
        for (dialect in Oc2Dialect.entries) {
            val engine = MockEngine { error("Unsupported form mutation reached transport") }
            val gateway = gateway(engine, dialect)
            assertFalse(gateway.capabilities.value.forms)
            assertIs<Outcome.Failed>(gateway.cancelForm(SessionId("s"), FormId("f")))
            gateway.close()
        }
    }

    @Test
    fun `B-PROTO-020 a short session page discards a decorative cursor but a full page keeps it`() =
        runTest {
            val engine = MockEngine {
                respond(
                    """{"data":[{"id":"a","title":null},{"id":"b","title":"Second"}],"cursor":{"next":"opaque"}}""",
                    HttpStatusCode.OK,
                )
            }
            val gateway = gateway(engine)
            val short = (gateway.sessionPage(limit = 100) as Outcome.Ok).value
            assertEquals(2, short.items.size)
            assertNull(short.nextCursor)
            assertNull(short.items.first().title)
            val full = (gateway.sessionPage(limit = 2) as Outcome.Ok).value
            assertEquals("opaque", full.nextCursor)
            gateway.close()
        }

    @Test
    fun `B-PROTO-027 nullable session strings and cursor stay absent rather than literal null`() =
        runTest {
            val engine = MockEngine {
                respond(
                    """{"data":[{"id":"a","title":null,"agent":null}],"cursor":{"next":null},"nextCursor":null}""",
                    HttpStatusCode.OK,
                )
            }
            val gateway = gateway(engine)
            val page = (gateway.sessionPage(limit = 1) as Outcome.Ok).value
            assertNull(page.nextCursor)
            assertNull(page.items.single().title)
            assertNull(page.items.single().agent)
            gateway.close()
        }
}
