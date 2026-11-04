package io.github.eslamasabry.oc.engine.agents.context

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.sessionimport.Oc2BasicAuth
import io.github.eslamasabry.oc.engine.agents.sessionimport.Oc2Routes
import io.github.eslamasabry.oc.engine.agents.sessionimport.RouteAnswer
import io.github.eslamasabry.oc.engine.agents.sessionimport.RouteRequest
import io.github.eslamasabry.oc.engine.agents.sessionimport.RouteTransport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Port of test/active_context_test.dart (gateway and mapper tests; the widget tests are UI).

private class Server(var reply: (RouteRequest) -> RouteAnswer) : RouteTransport {
    val requests = mutableListOf<RouteRequest>()
    override suspend fun send(request: RouteRequest): Outcome<RouteAnswer> {
        requests += request
        return reply(request).ok()
    }
}

private fun gateway(server: Server) = ActiveContextGateway(Oc2Routes(server, Oc2BasicAuth("opencode", "pw")))

private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

private fun map(type: String, values: String): ActiveContextMessage {
    val extra = json(values).jsonObject
    val message = JsonObject(mapOf("id" to JsonPrimitive("msg_$type"), "type" to JsonPrimitive(type)) + extra)
    return ActiveContextMapper.map(message)!!
}

private fun Outcome<*>.failure(): Failure = assertIs<Outcome.Failed>(this).failure

class ActiveContextTest {
    @Test
    fun `B-CHAT-001 context GET uses session identity and authentication, retaining more than 5000 messages`() = runTest {
        val server = Server {
            RouteAnswer(
                200,
                JsonObject(
                    mapOf(
                        "data" to JsonArray(
                            List(5001) { i ->
                                JsonObject(
                                    mapOf(
                                        "id" to JsonPrimitive("msg_$i"),
                                        "type" to JsonPrimitive("user"),
                                        "text" to JsonPrimitive("مرحبا 👋 $i"),
                                    ),
                                )
                            },
                        ),
                    ),
                ),
            )
        }
        val rows = assertIs<Outcome.Ok<List<ActiveContextMessage>>>(gateway(server).load("ses_test")).value
        assertEquals(5001, rows.size)
        assertEquals("مرحبا 👋 5000", rows.last().content.single().text)
        val request = server.requests.single()
        assertEquals("/api/session/ses_test/context", request.path)
        assertTrue(request.query.isEmpty())
        assertEquals("GET", request.method)
        // Basic base64("opencode:pw"), as the Dart handler asserts.
        assertEquals("Basic b3BlbmNvZGU6cHc=", request.headers["authorization"])
    }

    @Test
    fun `B-CHAT-002 malformed and duplicate snapshots fail visibly without disabling context`() = runTest {
        var response: JsonElement = json("{}")
        val server = Server { RouteAnswer(200, response) }
        val ops = gateway(server)
        for (invalid in listOf(
            "{}",
            """{"data":[null]}""",
            """{"data":[{"id":"msg_a"}]}""",
            """{"data":[{"id":"msg_a","type":"user"},{"id":"msg_a","type":"user"}]}""",
        )) {
            response = json(invalid)
            assertIs<Failure.UnreadableReply>(ops.load("ses_test").failure(), invalid)
            assertTrue(ops.supported)
        }
        response = json("""{"data":[{"id":"msg_unknown","type":"future-kind","private":"hidden"}]}""")
        val unknown = assertIs<Outcome.Ok<List<ActiveContextMessage>>>(ops.load("ses_test")).value.single()
        assertEquals("future-kind", unknown.type)
        assertEquals(ContextContentKind.Notice, unknown.content.single().kind)
        assertFalse(unknown.matches("hidden"))
    }

    @Test
    fun `B-CHAT-002 missing session and auth remain recoverable, missing route is capability failure`() = runTest {
        var status = 404
        var tag = "SessionNotFoundError"
        val server = Server { RouteAnswer(status, JsonObject(mapOf("_tag" to JsonPrimitive(tag)))) }
        val ops = gateway(server)
        for (code in listOf(404, 401, 500)) {
            status = code
            val failure = ops.load("ses_test").failure()
            when (code) {
                404 -> assertEquals("SessionNotFoundError", assertIs<Failure.NotFound>(failure).tag?.raw)
                401 -> assertIs<Failure.SignIn>(failure)
                500 -> assertEquals(500, assertIs<Failure.Server>(failure).status)
            }
            assertTrue(ops.supported)
        }
        status = 404
        tag = "RouteNotFound"
        assertEquals(ActiveContextGateway.FEATURE, assertIs<Failure.Unavailable>(ops.load("ses_test").failure()).feature)
        assertFalse(ops.supported)
        val count = server.requests.size
        assertIs<Failure.Unavailable>(ops.load("ses_test").failure())
        assertEquals(count, server.requests.size)
    }

    @Test
    fun `B-CHAT-003 explicit inspector retains instruction text and files without binary bodies or metadata`() {
        for (type in listOf("system", "synthetic", "skill", "user")) {
            val row = map(
                type,
                """{"text":"Instruction مرحبا 👋","name":"Review","metadata":{"secret":"hidden"},
                   "files":[{"name":"photo.png","mime":"image/png","data":"BINARY_HIDDEN","source":{"uri":"https://hidden.invalid"}}]}""",
            )
            assertTrue(row.matches("مرحبا"), type)
            assertFalse(row.matches("hidden"), type)
            if (type == "user") assertTrue(row.matches("photo.png"))
        }
    }

    @Test
    fun `B-CHAT-003 tool content preserves all text results and distinguishes pruned and truncated output`() {
        val row = map(
            "assistant",
            """{"content":[
              {"type":"reasoning","text":"private reasoning","time":{"pruned":1}},
              {"type":"tool","name":"read","state":{"status":"completed","input":{"path":"notes.md"},
                "content":[{"type":"text","text":"first result"},{"type":"text","text":"second مرحبا result"},
                  {"type":"file","name":"report.pdf","mime":"application/pdf","uri":"https://hidden.invalid"}],
                "metadata":{"hidden":"secret"}}},
              {"type":"tool","name":"bash","time":{"pruned":2},"state":{"status":"completed","input":{"command":"pwd"},
                "content":[{"type":"text","text":"pruned secret"}]}},
              {"type":"future-content","secret":"hidden"}
            ]}""",
        )
        assertEquals(
            listOf("first result", "second مرحبا result"),
            row.content.filter { it.kind == ContextContentKind.ToolOutput }.map { it.text },
        )
        assertEquals(2, row.content.count { it.kind == ContextContentKind.Pruned })
        assertTrue(row.matches("notes.md"))
        assertTrue(row.matches("report.pdf"))
        for (hidden in listOf("hidden", "private reasoning", "pruned secret")) assertFalse(row.matches(hidden), hidden)
        // The tool input reads as Dart's two-space JSON.
        assertEquals(
            "{\n  \"path\": \"notes.md\"\n}",
            row.content.first { it.kind == ContextContentKind.ToolInput && it.name == "read" }.text,
        )
        val shell = map("shell", """{"command":"pwd","output":{"output":"/project","truncated":true}}""")
        assertEquals(ContextContentKind.Truncated, shell.content.last().kind)
    }

    @Test
    fun `B-CHAT-003 search preview centers a distant literal match without slicing emoji pairs`() {
        val text = "👋 ".repeat(150) + "literal [match] مرحبا" + " 👋".repeat(150)
        val row = map("user", JsonObject(mapOf("text" to JsonPrimitive(text))).toString())
        val preview = row.previewFor("[match]")
        assertTrue(preview.contains("[match]"))
        assertTrue(preview.startsWith("…"))
        assertTrue(preview.endsWith("…"))
        assertEquals(preview, preview.encodeToByteArray().decodeToString())
        assertWholePairs(preview)
        // Both cuts land on a low surrogate here; each moves back one unit instead of splitting the pair.
        val tight = "👋".repeat(150) + "a[match]b" + "👋".repeat(150)
        val cut = map("user", JsonObject(mapOf("text" to JsonPrimitive(tight))).toString()).previewFor("[match]")
        assertEquals("…" + tight.substring(240, 419) + "…", cut)
        assertWholePairs(cut)
    }

    private fun assertWholePairs(preview: String) {
        preview.forEachIndexed { i, c ->
            if (c.isHighSurrogate()) assertTrue(preview[i + 1].isLowSurrogate())
            if (c.isLowSurrogate()) assertTrue(preview[i - 1].isHighSurrogate())
        }
    }
}
