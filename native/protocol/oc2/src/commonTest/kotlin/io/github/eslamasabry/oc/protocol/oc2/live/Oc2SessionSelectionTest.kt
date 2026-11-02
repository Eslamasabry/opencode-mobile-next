package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
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

/**
 * Session selection and catalog against the stable 2.2.5 contract: POST /session/{id}/model,
 * POST /session/{id}/agent, GET /session/{id}, GET /model, GET /model/default. Mutations are
 * never silently dropped and raw provider config never leaves the gateway.
 */
class Oc2SessionSelectionTest {
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
        }

    private fun gateway(engine: MockEngine): Oc2Gateway =
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
            initialDialect = Oc2Dialect.Stable,
        )

    @Test
    fun `B-NAV-034 model mutation sends the strict body with an encoded id and omits no-variant`() =
        runTest {
            val bodies = mutableListOf<String>()
            val engine = MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(
                    "Basic b3BlbmNvZGU6dGVzdC1zZWNyZXQ=",
                    request.headers[HttpHeaders.Authorization],
                )
                assertEquals("/api/session/ses%2F1/model", request.url.encodedPath)
                bodies += (request.body as TextContent).text
                respond("", HttpStatusCode.NoContent)
            }
            val gateway = gateway(engine)
            assertIs<Outcome.Ok<Unit>>(
                gateway.setSessionModel(SessionId("ses/1"), ModelRef("prov", "m"), "")
            )
            assertIs<Outcome.Ok<Unit>>(
                gateway.setSessionModel(SessionId("ses/1"), ModelRef("prov", "m"), "default")
            )
            assertIs<Outcome.Ok<Unit>>(
                gateway.setSessionModel(SessionId("ses/1"), ModelRef("prov", "m"), "fast")
            )
            assertEquals(
                listOf(
                    """{"model":{"id":"m","providerID":"prov"}}""",
                    """{"model":{"id":"m","providerID":"prov"}}""",
                    """{"model":{"id":"m","providerID":"prov","variant":"fast"}}""",
                ),
                bodies,
            )
            gateway.close()
        }

    @Test
    fun `B-NAV-034 agent mutation sends the strict agent body`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/session/s/agent", request.url.encodedPath)
            assertEquals("""{"agent":"build"}""", (request.body as TextContent).text)
            respond("", HttpStatusCode.NoContent)
        }
        val gateway = gateway(engine)
        assertIs<Outcome.Ok<Unit>>(gateway.setSessionAgent(SessionId("s"), "build"))
        gateway.close()
    }

    @Test
    fun `B-NAV-034 createSelectedSession sets defaults after creation and reads the latched state back`() =
        runTest {
            val calls = mutableListOf<String>()
            val engine = MockEngine { request ->
                calls += "${request.method.value} ${request.url.encodedPath}"
                when ("${request.method.value} ${request.url.encodedPath}") {
                    "POST /api/session" -> respond("""{"data":{"id":"new"}}""", HttpStatusCode.OK)
                    "POST /api/session/new/model" -> {
                        assertEquals(
                            """{"model":{"id":"m","providerID":"p","variant":"fast"}}""",
                            (request.body as TextContent).text,
                        )
                        respond("", HttpStatusCode.NoContent)
                    }
                    "POST /api/session/new/agent" -> {
                        assertEquals("""{"agent":"build"}""", (request.body as TextContent).text)
                        respond("", HttpStatusCode.NoContent)
                    }
                    "GET /api/session/new" ->
                        respond(
                            """{"data":{"id":"new","model":{"id":"m","providerID":"p","variant":"default"},"agent":"build"}}""",
                            HttpStatusCode.OK,
                        )
                    else -> error("Unexpected route")
                }
            }
            val gateway = gateway(engine)
            val session =
                (
                    gateway.createSelectedSession(
                        SessionSelection(ModelRef("p", "m"), "fast", "build", true, true)
                    ) as Outcome.Ok
                ).value
            assertEquals(
                listOf(
                    "POST /api/session",
                    "POST /api/session/new/model",
                    "POST /api/session/new/agent",
                    "GET /api/session/new",
                ),
                calls,
            )
            // "default" is the server's no-variant sentinel; the app reads it as empty.
            assertEquals(
                SessionSelection(ModelRef("p", "m"), "", "build", true, true),
                session.selection,
            )
            gateway.close()
        }

    @Test
    fun `B-NAV-034 a refused default selection propagates its typed failure and stops`() =
        runTest {
            val calls = mutableListOf<String>()
            val engine = MockEngine { request ->
                calls += "${request.method.value} ${request.url.encodedPath}"
                when ("${request.method.value} ${request.url.encodedPath}") {
                    "POST /api/session" -> respond("""{"data":{"id":"new"}}""", HttpStatusCode.OK)
                    "POST /api/session/new/model" ->
                        respond("""{"error":"unknown model"}""", HttpStatusCode.BadRequest)
                    else -> error("Selection must stop at the refused default")
                }
            }
            val gateway = gateway(engine)
            val result =
                gateway.createSelectedSession(
                    SessionSelection(ModelRef("p", "nope"), "", "build", true, true)
                )
            val failure = assertIs<Outcome.Failed>(result).failure
            assertEquals(400, assertIs<Failure.Rejected>(failure).status)
            assertEquals(listOf("POST /api/session", "POST /api/session/new/model"), calls)
            gateway.close()
        }

    @Test
    fun `B-NAV-034 session readback marks model and agent known even when the server sent none`() =
        runTest {
            val engine = MockEngine { request ->
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    when (request.url.encodedPath) {
                        "/api/session/bare" -> """{"data":{"id":"bare"}}"""
                        "/api/session/set" ->
                            """{"data":{"id":"set","model":{"id":"p/m","providerID":"p","variant":"fast"},"agent":"plan"}}"""
                        else -> error("Unexpected route")
                    },
                    HttpStatusCode.OK,
                )
            }
            val gateway = gateway(engine)
            val bare = (gateway.session(SessionId("bare")) as Outcome.Ok).value
            assertEquals(
                SessionSelection(null, "", null, modelKnown = true, agentKnown = true),
                bare.selection,
            )
            val set = (gateway.session(SessionId("set")) as Outcome.Ok).value
            // The composite id is read bare; a real variant survives normalization.
            assertEquals(
                SessionSelection(ModelRef("p", "m"), "fast", "plan", true, true),
                set.selection,
            )
            assertEquals("p/m", set.model)
            gateway.close()
        }

    @Test
    fun `B-NAV-034 catalog maps attachments capabilities and enabled flags without raw config`() =
        runTest {
            val paths = mutableListOf<String>()
            val engine = MockEngine { request ->
                paths += request.url.encodedPath
                respond(
                    when (request.url.encodedPath) {
                        "/api/provider" ->
                            """{"data":[{"id":"p","name":"P","activation":"disabled"},{"id":"q","name":"Q"},{"id":"p","name":"dup"}]}"""
                        "/api/model" ->
                            """{"data":[
                                {"id":"p/img","providerID":"p","enabled":true,"name":"Img","status":"ok",
                                 "capabilities":{"tools":true,"input":["text","image"],"output":["text"]},
                                 "limit":{"context":128000,"output":8192}},
                                {"id":"q/txt","providerID":"q","enabled":false}
                            ]}""".replace("\n", "")
                        "/api/agent" ->
                            """{"data":[{"id":"build","mode":"primary"},{"id":"sub","mode":"subagent","hidden":true}]}"""
                        else -> error("Unexpected catalog route")
                    },
                    HttpStatusCode.OK,
                )
            }
            val gateway = gateway(engine)
            val catalog = (gateway.catalog() as Outcome.Ok).value
            assertEquals(
                listOf("/api/provider", "/api/model", "/api/agent"),
                paths.distinct(),
            )
            val providers = catalog.providers.associateBy { it.id }
            assertEquals(2, providers.size)
            assertFalse(providers.getValue("p").enabled)
            assertTrue(providers.getValue("q").enabled)
            assertEquals("P", providers.getValue("p").name)
            val img = catalog.models.single { it.id == "img" }
            val txt = catalog.models.single { it.id == "txt" }
            assertTrue(img.attachments)
            assertTrue(img.tools)
            assertTrue(img.enabled)
            assertEquals(128000, img.contextLimit)
            assertEquals(8192, img.outputLimit)
            assertFalse(txt.attachments)
            assertFalse(txt.enabled)
            assertEquals("unknown", txt.status)
            val build = catalog.agents.single { it.id == "build" }
            assertEquals("primary", build.mode)
            assertTrue(catalog.agents.single { it.id == "sub" }.hidden)
            gateway.close()
        }

    @Test
    fun `B-NAV-034 chat defaults read only the confirmed default model endpoint`() = runTest {
        val present =
            MockEngine { request ->
                assertEquals("/api/model/default", request.url.encodedPath)
                respond(
                    """{"data":{"id":"p/m","providerID":"p","enabled":true}}""",
                    HttpStatusCode.OK,
                )
            }
        val withDefault = gateway(present)
        assertEquals(
            ChatDefaults(ModelRef("p", "m"), null),
            (withDefault.chatDefaults() as Outcome.Ok).value,
        )
        withDefault.close()
        val absent =
            MockEngine { request ->
                assertEquals("/api/model/default", request.url.encodedPath)
                respond("""{"data":null}""", HttpStatusCode.OK)
            }
        val withoutDefault = gateway(absent)
        assertEquals(
            ChatDefaults(null, null),
            (withoutDefault.chatDefaults() as Outcome.Ok).value,
        )
        withoutDefault.close()
    }

    @Test
    fun `B-NAV-034 catalog refuses a malformed model list or row`() = runTest {
        val notList =
            MockEngine { request ->
                if (request.url.encodedPath == "/api/provider")
                    respond("""{"data":[]}""", HttpStatusCode.OK)
                else respond("""{"data":{"not":"a list"}}""", HttpStatusCode.OK)
            }
        val skewed = gateway(notList)
        assertIs<Outcome.Failed>(skewed.catalog())
        skewed.close()
        val badRow =
            MockEngine { request ->
                if (request.url.encodedPath == "/api/provider")
                    respond("""{"data":[]}""", HttpStatusCode.OK)
                else respond("""{"data":[{"providerID":"p"}]}""", HttpStatusCode.OK)
            }
        val rowless = gateway(badRow)
        assertIs<Failure.UnreadableReply>(assertIs<Outcome.Failed>(rowless.catalog()).failure)
        rowless.close()
    }

    @Test
    fun `B-NAV-034 model switching, agent choice and the callable catalog are enabled`() = runTest {
        val engine = MockEngine { error("Capabilities are read without network") }
        val gateway = gateway(engine)
        assertTrue(gateway.capabilities.value.sessionModelProviderSwitching)
        // POST /session/{id}/agent is callable (Flutter api2ServerCapabilities keeps agentSelection).
        assertTrue(gateway.capabilities.value.agentSelection)
        assertTrue(gateway.capabilities.value.serverCatalog)
        gateway.close()
    }
}
