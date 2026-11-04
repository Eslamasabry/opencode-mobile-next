package io.github.eslamasabry.oc.engine.agents.sessionimport

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

// Port of test/session_import_test.dart (the model and wire tests; the widget tests are UI).

/** The Dart `transfer()` fixture, built over MUTABLE maps and lists so isolation can be proven. */
private fun transfer(redacted: Boolean = false, parent: String? = null): MutableMap<String, Any?> = mutableMapOf(
    "info" to mutableMapOf<String, Any?>(
        "id" to "ses_transfer",
        "projectID" to "source_project",
        "title" to "Transfer العربية",
        "location" to mutableMapOf<String, Any?>("directory" to "/source/private"),
        "time" to mutableMapOf<String, Any?>("created" to 1, "updated" to 2),
        "cost" to 0,
        "tokens" to mutableMapOf<String, Any?>(
            "input" to 0, "output" to 0, "reasoning" to 0,
            "cache" to mutableMapOf<String, Any?>("read" to 0, "write" to 0),
        ),
    ).also { if (parent != null) it["parentID"] = parent },
    "messages" to mutableListOf<Any?>(
        mutableMapOf<String, Any?>(
            "id" to "msg_user",
            "type" to "user",
            "time" to mutableMapOf<String, Any?>("created" to 1),
            "text" to if (redacted) "[redacted:text:msg_user]" else "Hello العربية",
        ),
        mutableMapOf<String, Any?>(
            "id" to "msg_location",
            "type" to "location-switched",
            "time" to mutableMapOf<String, Any?>("created" to 2),
            "location" to mutableMapOf<String, Any?>("directory" to "/source/private"),
            "subpath" to "src",
        ),
    ),
)

/**
 * A JsonElement VIEW over the mutable tree: JsonObject/JsonArray keep a reference to the map/list they are built
 * from, so a mutation of the source tree shows through unless the document copied it (the Dart isolation test).
 */
@Suppress("UNCHECKED_CAST")
private fun view(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is JsonElement -> value
    is MutableMap<*, *> -> JsonObject(ViewMap(value as MutableMap<String, Any?>))
    is MutableList<*> -> JsonArray(ViewList(value as MutableList<Any?>))
    else -> error("unsupported $value")
}

private class ViewMap(val source: MutableMap<String, Any?>) : AbstractMap<String, JsonElement>() {
    override val entries: Set<Map.Entry<String, JsonElement>>
        get() = source.entries.map { e -> object : Map.Entry<String, JsonElement> {
            override val key = e.key
            override val value = view(e.value)
        } }.toSet()
}

private class ViewList(val source: MutableList<Any?>) : AbstractList<JsonElement>() {
    override val size: Int get() = source.size
    override fun get(index: Int): JsonElement = view(source[index])
}

@Suppress("UNCHECKED_CAST")
private fun Any?.map(): MutableMap<String, Any?> = this as MutableMap<String, Any?>

@Suppress("UNCHECKED_CAST")
private fun Any?.list(): MutableList<Any?> = this as MutableList<Any?>

/** A frozen copy of the fixture, for "equals what was sent". */
private fun frozen(value: Any?): JsonElement = Json.parseToJsonElement(view(value).toString())

private fun doc(value: Any?): SessionImportDocument {
    val result = SessionImportDocument.fromJson(view(value))
    assertIs<SessionImportResult.Ok<SessionImportDocument>>(result)
    return result.value
}

private fun assertInvalid(value: Any?) =
    assertEquals(
        SessionImportResult.Refused(SessionImportProblem.Invalid),
        SessionImportDocument.fromJson(value as? JsonElement ?: view(value)),
        "expected invalid: $value",
    )

private fun SessionImportDocument.body(destination: SessionImportDestination): JsonObject {
    val result = requestBody(destination)
    assertIs<SessionImportResult.Ok<JsonObject>>(result)
    return result.value
}

private class FakeTransport(var reply: (RouteRequest) -> RouteAnswer) : RouteTransport {
    val requests = mutableListOf<RouteRequest>()
    override suspend fun send(request: RouteRequest): Outcome<RouteAnswer> {
        requests += request
        return reply(request).ok()
    }
}

class SessionImportTest {
    @Test
    fun `B-PROTO-097 streamed export envelope and bare payload preserve Unicode and all records`() {
        val payload = transfer()
        val bytes = Json.encodeToString(JsonElement.serializer(), JsonObject(mapOf("data" to frozen(payload))))
            .encodeToByteArray()
        // One byte per chunk: every multi-byte Arabic letter is split across chunks.
        val read = SessionImportDocument.read(bytes.asSequence().map { byteArrayOf(it) })
        assertIs<SessionImportResult.Ok<SessionImportDocument>>(read)
        val streamed = read.value
        val bare = doc(payload)
        assertEquals("Transfer العربية", streamed.title)
        assertEquals(2, streamed.messageCount)
        assertEquals(streamed.id, bare.id)
        val request = streamed.body(SessionImportDestination("/destination", "wrk_dest"))
        assertEquals(frozen(payload["messages"]), request["messages"])
        assertEquals(frozen(payload["info"]), request["info"])
        assertEquals(
            JsonObject(mapOf("directory" to JsonPrimitive("/destination"), "workspaceID" to JsonPrimitive("wrk_dest"))),
            request["location"],
        )
        // The source keeps its own location; only the request names the destination.
        assertEquals(mapOf("directory" to "/source/private"), payload["info"].map()["location"])
    }

    @Test
    fun `B-PROTO-097 unknown message types and nested protocol fields survive review`() {
        val payload = transfer()
        payload["info"].map()["title"] = "Future\ntranscript"
        val message = payload["messages"].list().first().map()
        message["type"] = "future-message-v2"
        message["future"] = mutableMapOf<String, Any?>(
            "parts" to mutableListOf<Any?>(
                mutableMapOf<String, Any?>("kind" to "vendor-extension", "values" to mutableListOf<Any?>(1, true, null)),
            ),
        )
        val doc = doc(payload)
        assertEquals("Future\ntranscript", doc.title)
        val body = doc.body(SessionImportDestination("/destination"))
        val first = body["messages"]!!.jsonArray.first()
        assertEquals(frozen(message), first)
        assertEquals(
            Json.parseToJsonElement("""{"parts":[{"kind":"vendor-extension","values":[1,true,null]}]}"""),
            first.jsonObject["future"],
        )
    }

    @Test
    fun `B-PROTO-098 review snapshot isolates nested input and request mutations`() {
        val payload = transfer()
        val sourceMessage = payload["messages"].list().first().map()
        sourceMessage["metadata"] = mutableMapOf<String, Any?>("labels" to mutableListOf<Any?>("original"))
        val doc = doc(payload)

        payload["info"].map()["time"].map()["archived"] = 99
        payload["info"].map()["location"].map()["directory"] = "/changed"
        sourceMessage["time"].map()["created"] = 99
        sourceMessage["metadata"].map()["labels"].list()[0] = "changed"
        payload["messages"].list().clear()

        val firstBody = doc.body(SessionImportDestination("/destination"))
        val secondBody = doc.body(SessionImportDestination("/destination"))
        // Each request is a fresh tree: a transport that held one cannot reach the next.
        assertNotSame(firstBody["info"], secondBody["info"])
        assertNotSame(firstBody["messages"], secondBody["messages"])
        assertFalse(doc.archived)
        assertEquals(JsonObject(mapOf("directory" to JsonPrimitive("/source/private"))), secondBody["info"]!!.jsonObject["location"])
        assertEquals(Json.parseToJsonElement("""{"created":1,"updated":2}"""), secondBody["info"]!!.jsonObject["time"])
        val messages = secondBody["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("Hello العربية", messages.first().jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(
            JsonArray(listOf(JsonPrimitive("original"))),
            messages.first().jsonObject["metadata"]!!.jsonObject["labels"],
        )
        assertEquals(2, doc.messageCount)
    }

    @Test
    fun `B-SEC-161 blank, control, and malformed references and destinations are rejected`() {
        for (id in listOf("ses", "ses\nchild", "ses child", "wrong_session")) {
            val payload = transfer()
            payload["info"].map()["id"] = id
            assertInvalid(payload)
        }
        for (parent in listOf("", "ses\nparent", "message_parent")) {
            assertInvalid(transfer(parent = parent))
        }
        for (workspace in listOf("wrk", "wrk\tworkspace", "workspace")) {
            val payload = transfer()
            payload["info"].map()["location"].map()["workspaceID"] = workspace
            assertInvalid(payload)
            val doc = doc(transfer())
            assertEquals(
                SessionImportResult.Refused(SessionImportProblem.Invalid),
                doc.requestBody(SessionImportDestination("/destination", workspace)),
            )
        }
        val badDirectory = transfer()
        badDirectory["info"].map()["location"].map()["directory"] = "/bad\n"
        assertInvalid(badDirectory)
        assertEquals(
            SessionImportResult.Refused(SessionImportProblem.Invalid),
            doc(transfer()).requestBody(SessionImportDestination("/destination\u0000")),
        )

        // Dart's cyclic tree cannot exist as a JsonElement; the same guard bounds nesting at 256 levels.
        val deep = transfer()
        var nested: MutableMap<String, Any?> = mutableMapOf()
        deep["messages"].list().first().map()["nested"] = nested
        repeat(300) { nested = mutableMapOf<String, Any?>().also { child -> nested["self"] = child } }
        assertInvalid(deep)

        val nonFinite = transfer()
        nonFinite["messages"].list().first().map()["futureValue"] = Double.NaN
        assertInvalid(nonFinite)
    }

    @Test
    fun `B-SEC-161 identifies redaction and parent without discarding the source`() {
        val doc = doc(transfer(redacted = true, parent = "ses_parent"))
        assertTrue(doc.hasRedactions)
        assertEquals("ses_parent", doc.parentId)
        assertEquals(2, doc.messageCount)
        assertFalse(doc(transfer()).hasRedactions)
    }

    @Test
    fun `B-SEC-161 malformed identifying data and duplicate messages are rejected`() {
        val bad = transfer()
        bad["messages"].list().add(bad["messages"].list().first())
        val cases: List<JsonElement> = listOf(
            JsonNull,
            JsonArray(emptyList()),
            Json.parseToJsonElement("""{"messages":[]}"""),
            JsonObject(mapOf("data" to frozen(transfer()), "extra" to JsonPrimitive(true))),
            view(bad),
        )
        for (value in cases) assertInvalid(value)
        assertEquals(
            SessionImportResult.Refused(SessionImportProblem.Invalid),
            SessionImportDocument.fromJson(null),
        )
        assertEquals(
            SessionImportResult.Refused(SessionImportProblem.Invalid),
            SessionImportDocument.read(sequenceOf("# Markdown".encodeToByteArray())),
        )
        assertEquals(
            SessionImportResult.Refused(SessionImportProblem.Invalid),
            doc(transfer()).requestBody(SessionImportDestination(" ")),
        )
    }

    @Test
    fun `B-SEC-161 oversize file is never read and stops pulling chunks`() {
        assertEquals(
            SessionImportResult.Refused(SessionImportProblem.TooLarge),
            SessionImportDocument.admitLength(SessionImportDocument.MAX_BYTES + 1),
        )
        assertEquals(SessionImportResult.Ok(Unit), SessionImportDocument.admitLength(SessionImportDocument.MAX_BYTES))
        var pulled = 0
        val chunks = generateSequence { pulled++; ByteArray(4) }
        assertEquals(
            SessionImportResult.Refused(SessionImportProblem.TooLarge),
            SessionImportDocument.read(chunks, maxBytes = 10),
        )
        assertEquals(3, pulled)
    }

    @Test
    fun `B-PROTO-099 import wire body retains source IDs and explicit destination with no query`() = runTest {
        val transport = FakeTransport {
            val info = frozen(transfer()["info"]).jsonObject
            val data = JsonObject(
                info + mapOf(
                    "projectID" to JsonPrimitive("target"),
                    "location" to Json.parseToJsonElement("""{"directory":"/destination","workspaceID":"wrk_dest"}"""),
                ),
            )
            RouteAnswer(200, JsonObject(mapOf("data" to data)))
        }
        val gateway = SessionImportGateway(Oc2Routes(transport, Oc2BasicAuth("opencode", "pw")))
        val result = gateway.importSession(doc(transfer()), SessionImportDestination("/destination", "wrk_dest"))
        assertIs<Outcome.Ok<ImportedSession>>(result)
        assertEquals("ses_transfer", result.value.id)
        assertEquals("/destination", result.value.directory)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/session/import", request.path)
        assertTrue(request.query.isEmpty())
        assertEquals("Basic b3BlbmNvZGU6cHc=", request.headers["authorization"])
        val body = request.body!!.jsonObject
        assertEquals(Json.parseToJsonElement("""{"directory":"/destination","workspaceID":"wrk_dest"}"""), body["location"])
        assertEquals(frozen(transfer()["info"]), body["info"])
        assertEquals(frozen(transfer()["messages"]), body["messages"])
        assertFalse(request.toString().contains("b3BlbmNvZGU6cHc"))
    }

    @Test
    fun `B-PROTO-099 import failures keep capability and never retry or mutate IDs`() = runTest {
        for (status in listOf(400, 401, 404, 409, 500)) {
            val transport = FakeTransport {
                val tag = if (status == 404) "SessionNotFoundError" else "Failure"
                RouteAnswer(status, JsonObject(mapOf("_tag" to JsonPrimitive(tag))))
            }
            val gateway = SessionImportGateway(Oc2Routes(transport, Oc2BasicAuth("opencode", "pw")))
            val document = doc(transfer())
            val result = gateway.importSession(document, SessionImportDestination("/destination"))
            assertIs<Outcome.Failed>(result)
            val failure = result.failure
            when (status) {
                400 -> assertEquals(400, assertIs<Failure.Rejected>(failure).status)
                401 -> assertEquals(SignInWhy.PasswordRejected, assertIs<Failure.SignIn>(failure).why)
                404 -> assertEquals("SessionNotFoundError", assertIs<Failure.NotFound>(failure).tag?.raw)
                409 -> assertIs<Failure.Conflict>(failure)
                500 -> assertEquals(500, assertIs<Failure.Server>(failure).status)
            }
            assertTrue(gateway.supported, "status $status")
            assertEquals(1, transport.requests.size, "status $status")
            assertEquals("ses_transfer", transport.requests.single().body!!.jsonObject["info"]!!.jsonObject["id"]!!.jsonPrimitive.content)
            assertEquals("ses_transfer", document.id)
        }
    }

    @Test
    fun `B-PROTO-099 a missing route latches unsupported and a mismatched success is unconfirmed`() = runTest {
        for (status in listOf(404, 405, 501)) {
            val transport = FakeTransport { RouteAnswer(status, JsonObject(mapOf("_tag" to JsonPrimitive("RouteNotFound")))) }
            val gateway = SessionImportGateway(Oc2Routes(transport, Oc2BasicAuth("opencode", "pw")))
            val result = gateway.importSession(doc(transfer()), SessionImportDestination("/destination"))
            assertEquals(SessionImportGateway.FEATURE, assertIs<Failure.Unavailable>((result as Outcome.Failed).failure).feature)
            assertFalse(gateway.supported)
            gateway.importSession(doc(transfer()), SessionImportDestination("/destination"))
            assertEquals(1, transport.requests.size)
        }
        val transport = FakeTransport {
            RouteAnswer(200, Json.parseToJsonElement("""{"data":{"id":"ses_other","location":{"directory":"/current"}}}"""))
        }
        val gateway = SessionImportGateway(Oc2Routes(transport, Oc2BasicAuth("opencode", "pw")))
        val result = gateway.importSession(doc(transfer()), SessionImportDestination("/destination"))
        assertIs<Failure.UnreadableReply>((result as Outcome.Failed).failure)
        assertTrue(gateway.supported)
        assertEquals(1, transport.requests.size)
    }
}
