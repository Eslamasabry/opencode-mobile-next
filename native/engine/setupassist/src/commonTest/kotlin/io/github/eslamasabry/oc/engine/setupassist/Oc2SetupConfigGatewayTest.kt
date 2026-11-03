package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of test/setup_config_adapter_test.dart (the dialect is the borrowed client's business, so one run covers both). */
class Oc2SetupConfigGatewayTest {
    private data class Sent(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>)

    /** The authenticated client: it adds the Basic header itself; the gateway only names path and location. */
    private class FakeClient : SetupHttpClient {
        val requests = mutableListOf<Sent>()
        var reply: (String) -> SetupHttpReply = { SetupHttpReply.Json(JsonObject(emptyMap())) }
        override var location: SetupLocation = SetupLocation("/projects/app", "workspace-one")

        override suspend fun getJson(path: String, query: Map<String, String>): SetupHttpReply {
            requests += Sent("GET", path, query, mapOf("Authorization" to "Basic ZmFrZQ=="))
            return reply(path)
        }
    }

    private val redactor = Redactor()
    private val http = FakeClient()
    private val gateway = Oc2SetupConfigGateway(http, redactor)

    private fun payload(text: String) {
        http.reply = { SetupHttpReply.Json(json(text)) }
    }

    @Test
    fun `B-PROTO-104 reads config in the selected location using existing auth`() = runTest {
        payload(
            """[{"type":"document","path":"/global/opencode.json","info":{"model":"example/global"}},
               {"type":"document","path":"/projects/app/opencode.json","info":{"model":"example/project"}}]""",
        )
        val config = (gateway.readConfig() as SetupOutcome.Ok).value
        val sources = config["sources"] as JsonArray
        assertEquals(2, sources.size)
        assertEquals(JsonPrimitive("/global/opencode.json"), sources.first().jsonObject["path"])
        assertFalse(config.containsKey("model"))
        val request = http.requests.single()
        assertEquals("/config", request.path)
        assertEquals("GET", request.method)
        assertEquals("/projects/app", request.query["location[directory]"])
        assertEquals("workspace-one", request.query["location[workspace]"])
        assertTrue(request.headers.containsKey("Authorization"))
        assertTrue(request.query.keys.none { "auth" in it } && request.query.values.none { "auth" in it })
    }

    @Test
    fun `B-PROTO-104 inspection keeps its captured location after the client switches`() = runTest {
        http.location = SetupLocation("/projects/other", "workspace-other")
        payload("[]")
        gateway.readConfig()
        payload("""{"data":[]}""")
        gateway.listMcpServers()
        assertEquals(2, http.requests.size)
        for (request in http.requests) {
            assertEquals("GET", request.method)
            assertEquals("/projects/app", request.query["location[directory]"])
            assertEquals("workspace-one", request.query["location[workspace]"])
        }
    }

    @Test
    fun `B-PROTO-104 loaded provider credentials are registered for redaction before returning config`() = runTest {
        for (dialect in listOf("beta", "stable")) {
            val secret = "fixture-setup-$dialect-ingress-value"
            payload("""[{"type":"document","info":{"providers":{"example":{"options":{"apiKey":"$secret"}}}}}]""")
            assertTrue(gateway.readConfig() is SetupOutcome.Ok)
            assertFalse(redactor.redact(secret).contains(secret))
        }
    }

    @Test
    fun `B-PROTO-105 a write refusal performs no transport request`() = runTest {
        assertTrue(gateway.support.readConfig)
        assertTrue(gateway.support.mcpInventory)
        assertFalse(gateway.support.writeConfig)
        assertFalse(gateway.support.assistant)
        for (patch in listOf(
            """{"model":"example/changed"}""",
            """{"shell":"/bin/sh"}""",
            """{"mcp":{"docs":{"type":"remote","url":"https://example.com/mcp","enabled":false}}}""",
            """{"mcp":{"docs":null}}""",
        )) {
            assertEquals(SetupFailureCode.Unsupported, gateway.patchConfig(obj(patch)).failureOrNull()?.code)
        }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `B-PROTO-105 inventory keeps statuses without raw server errors`() = runTest {
        payload(
            """{"data":[{"name":"docs","status":{"status":"needs_auth"}},
               {"name":"failed-server","status":{"status":"failed","error":"token=fake-secret"}},
               {"name":"future","status":{"status":"token=fake-secret"}}]}""",
        )
        val servers = (gateway.listMcpServers() as SetupOutcome.Ok).value
        assertEquals(listOf("docs", "failed-server", "future"), servers.map { it.name })
        assertEquals(listOf("needs_auth", "failed", "unknown"), servers.map { it.status })
        assertEquals("/mcp", http.requests.single().path)
        assertFalse(servers.toString().contains("fake-secret"))
    }

    @Test
    fun `B-PROTO-105 HTTP 401, 403 and 500 failures never expose response details`() = runTest {
        for (status in listOf(401, 403, 500)) {
            http.reply = { SetupHttpReply.Status(status) }
            val failure = gateway.readConfig().failureOrNull()!!
            assertEquals(if (status == 500) SetupFailureCode.Transport else SetupFailureCode.NeedsSignIn, failure.code)
            assertFalse(failure.message.contains("synthetic-secret"))
        }
    }

    @Test
    fun `B-PROTO-105 a network failure maps to offline with safe copy`() = runTest {
        http.reply = { SetupHttpReply.Network }
        val failure = gateway.readConfig().failureOrNull()!!
        assertEquals(SetupFailureCode.Offline, failure.code)
        assertEquals("The server is unavailable. Reconnect and try again.", failure.message)
        http.reply = { SetupHttpReply.RemovedInStable }
        assertEquals(SetupFailureCode.Unsupported, gateway.readConfig().failureOrNull()?.code)
    }

    @Test
    fun `B-PROTO-105 malformed config cannot masquerade as empty config`() = runTest {
        http.reply = { SetupHttpReply.Json(JsonPrimitive("invalid response")) }
        assertEquals(SetupFailureCode.Invalid, gateway.readConfig().failureOrNull()?.code)
        payload("""[{"path":"/no/type"}]""")
        assertEquals(SetupFailureCode.Invalid, gateway.readConfig().failureOrNull()?.code)
    }

    @Test
    fun `B-PROTO-105 malformed inventory cannot masquerade as no servers`() = runTest {
        http.reply = { SetupHttpReply.Json(JsonPrimitive("invalid response")) }
        assertEquals(SetupFailureCode.Invalid, gateway.listMcpServers().failureOrNull()?.code)
        payload("""{"data":[{"status":{}}]}""")
        assertEquals(SetupFailureCode.Invalid, gateway.listMcpServers().failureOrNull()?.code)
    }

}
