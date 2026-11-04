package io.github.eslamasabry.oc.engine.cards

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Flutter `gen_ui_server_test.dart` against the native [CardToolServer] script, run under Node.js exactly as the
 * built-in Linux runs it (B-CHAT-047, B-CHAT-048). Needs `node` on PATH (or /opt/node22/bin/node).
 */
class CardToolServerTest {
    private lateinit var directory: File
    private lateinit var marker: File
    private lateinit var helper: Helper
    private val servers = mutableListOf<HttpServer>()

    private val card: JsonObject =
        obj("v" to 1, "id" to "sample", "title" to "Sample", "body" to JsonArray(emptyList()))

    private val searchMatch =
        obj(
            "catalogId" to "com.example/design", "name" to "Design references", "description" to "Find useful design references.",
            "runtime" to "hosted", "needsSignIn" to "unknown", "connected" to false,
        )
    private val searchSuccess = obj("status" to "ok", "matches" to JsonArray(listOf(searchMatch)))

    @BeforeTest
    fun start() {
        directory = Files.createTempDirectory("gen-ui-server-").toFile()
        marker = File(directory, "enabled").apply { writeText("enabled\n") }
        val script = File(directory, "server.cjs").apply { writeText(CardToolServer.script(marker.path)) }
        helper = Helper(script)
    }

    @AfterTest
    fun stop() {
        servers.forEach { it.stop(0) }
        val code = helper.close()
        directory.deleteRecursively()
        assertEquals(0, code)
        assertEquals(0, helper.stderrBytes, "the helper never logs raw input or errors")
    }

    @Test
    fun `B-CHAT-047 initializes, handles notifications and advertises scoped card tools`() {
        val initialized = helper.initialize("2024-11-05")
        assertEquals("2024-11-05", initialized.str("protocolVersion"))
        assertEquals(obj("tools" to obj("listChanged" to false)), initialized["capabilities"])
        helper.notification("notifications/cancelled", obj("requestId" to "old"))
        val tools = helper.request("tools/list").obj("result")["tools"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("show", "find_connectors"), tools.map { it.str("name") })
        val schema = tools.first().obj("inputSchema")
        assertEquals(JsonPrimitive(false), schema["additionalProperties"])
        assertEquals(listOf("v", "id", "title", "body"), schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        val props = schema.obj("properties")
        assertEquals(10, props.obj("body").obj("items")["oneOf"]!!.jsonArray.size)
        assertEquals(4, props.obj("ask")["oneOf"]!!.jsonArray.size)
        val search = tools.last()
        assertEquals(
            obj("readOnlyHint" to true, "destructiveHint" to false, "idempotentHint" to true, "openWorldHint" to false),
            search["annotations"],
        )
        assertEquals(JsonPrimitive(false), search.obj("inputSchema")["additionalProperties"])
        assertEquals(JsonObject(emptyMap()), helper.request("ping")["result"])
        assertEquals(obj("code" to -32601, "message" to "Method not found"), helper.request("unknown")["error"])
    }

    @Test
    fun `B-CHAT-047 negotiates an unknown version and requires initialization`() {
        assertEquals(-32000, helper.request("tools/list").obj("error")["code"]!!.jsonPrimitive.int)
        assertEquals("2025-11-25", helper.initialize("1900-01-01").str("protocolVersion"))
        val duplicate = helper.request("initialize", initParams("2025-11-25"))
        assertEquals(-32602, duplicate.obj("error")["code"]!!.jsonPrimitive.int)
    }

    @Test
    fun `B-CHAT-047 a valid call returns immediately without claiming delivery`() {
        helper.initialize()
        val result = helper.call(card).obj("result")
        assertFalse(result["isError"] == JsonPrimitive(true))
        val text = result["content"]!!.jsonArray.single().jsonObject.str("text")
        assertTrue("Card accepted for display" in text)
        assertTrue("This call does not return their answer." in text)
        assertFalse("Card displayed" in text)
        assertEquals(setOf("content"), result.keys)
    }

    @Test
    fun `B-CHAT-047 invalid, disabled and unknown calls share one safe generic error`() {
        helper.initialize()
        val bad = helper.call(JsonObject(card + ("unknown" to JsonPrimitive("private-sentinel"))))
        val unsupported = helper.call(JsonObject(card + ("ask" to obj("kind" to "voice"))))
        val unknown = helper.request("tools/call", obj("name" to "private-sentinel", "arguments" to card))
        marker.delete()
        val disabled = helper.call(card)
        val generic =
            obj("isError" to true, "content" to JsonArray(listOf(obj("type" to "text", "text" to "Agent card unavailable or invalid."))))
        for (reply in listOf(bad, unsupported, unknown, disabled)) {
            assertEquals(generic, reply["result"])
            assertFalse("private-sentinel" in reply.toString())
        }
    }

    @Test
    fun `B-CHAT-047 every call rereads the marker and refuses symlinks or bad contents`() {
        helper.initialize()
        assertFalse(helper.call(card).obj("result").containsKey("isError"))
        marker.writeText("disabled")
        assertEquals(JsonPrimitive(true), helper.call(card).obj("result")["isError"])
        marker.delete()
        val target = File(directory, "target").apply { writeText("enabled\n") }
        Files.createSymbolicLink(marker.toPath(), target.toPath())
        assertEquals(JsonPrimitive(true), helper.call(card).obj("result")["isError"])
        Files.delete(marker.toPath())
        marker.writeText("enabled\n")
        assertFalse(helper.call(card).obj("result").containsKey("isError"))
    }

    @Test
    fun `B-CHAT-047 raw frame bytes are bounded before parsing and the next frame recovers`() {
        helper.initialize()
        helper.raw(("x".repeat(32768) + "é".repeat(16385) + "\n").encodeToByteArray())
        assertEquals(obj("code" to -32700, "message" to "Frame too large"), helper.next()["error"])
        assertEquals(JsonObject(emptyMap()), helper.request("ping")["result"])
        helper.raw(("x".repeat(65536) + "\n").encodeToByteArray()) // exactly 64 KiB: a parse error, not overflow
        assertEquals("Parse error", helper.next().obj("error").str("message"))
        assertEquals(JsonObject(emptyMap()), helper.request("ping")["result"])
    }

    @Test
    fun `B-CHAT-047 split frames, invalid UTF-8 and malformed JSON stay protocol-only`() {
        helper.initialize()
        helper.raw("""{"jsonrpc":"2.0","id":1000,"method":""".encodeToByteArray())
        helper.raw("\"ping\"}\n".encodeToByteArray())
        assertEquals(obj("jsonrpc" to "2.0", "id" to 1000, "result" to JsonObject(emptyMap())), helper.next())
        helper.raw(byteArrayOf(0xff.toByte(), 10))
        assertEquals(-32700, helper.next().obj("error")["code"]!!.jsonPrimitive.int)
        helper.raw("private-sentinel\n".encodeToByteArray())
        val bad = helper.next()
        assertEquals(-32700, bad.obj("error")["code"]!!.jsonPrimitive.int)
        assertFalse("private-sentinel" in bad.toString())
        helper.raw("[]\n".encodeToByteArray())
        assertEquals(-32600, helper.next().obj("error")["code"]!!.jsonPrimitive.int)
        assertEquals(JsonObject(emptyMap()), helper.request("ping")["result"])
    }

    @Test
    fun `B-CHAT-047 the marker path refuses relative or embedded control paths`() {
        for (path in listOf("relative", "/tmp/bad\npath", "/tmp/bad\u0000path")) {
            assertFailsWith<IllegalArgumentException> { CardToolServer.script(path) }
        }
    }

    @Test
    fun `B-CHAT-048 connector search without a bridge returns a safe actionable result`() {
        helper.initialize()
        assertEquals(
            obj(
                "status" to "catalogue_not_loaded",
                "message" to "The connector catalogue could not be loaded right now. Try again in a moment.",
                "matches" to JsonArray(emptyList()),
            ),
            searchResult(helper.search(obj("query" to "design"))),
        )
        marker.delete()
        assertEquals(JsonPrimitive(true), helper.search(obj("query" to "design")).obj("result")["isError"])
    }

    @Test
    fun `B-CHAT-048 search reads the current bridge and sends a bounded authorized query`() {
        val received = mutableListOf<JsonObject>()
        val port =
            bridge { method, path, auth, body ->
                assertEquals("POST", method)
                assertEquals("/find-connectors", path)
                assertEquals("Bearer " + "a".repeat(64), auth)
                received += Json.parseToJsonElement(body).jsonObject
                200 to searchSuccess.toString()
            }
        val bridgeFile = File(CardToolServer.searchBridgePath(marker.path))
        writeBridge(bridgeFile, "http://127.0.0.1:$port/find-connectors")
        helper.initialize()
        assertEquals(searchSuccess, searchResult(helper.search(obj("query" to "design"))))
        assertEquals(
            obj("arguments" to obj("query" to "design", "limit" to 5), "directory" to helper.cwd.canonicalPath),
            received.single(),
        )
        bridgeFile.delete()
        assertEquals("catalogue_not_loaded", searchResult(helper.search(obj("query" to "design"))).str("status"))
        assertEquals(1, received.size)
    }

    @Test
    fun `B-CHAT-048 invalid queries and bridge paths never reach the bridge`() {
        var requests = 0
        val port = bridge { _, _, _, _ -> requests++; 200 to searchSuccess.toString() }
        val bridgeFile = File(CardToolServer.searchBridgePath(marker.path))
        val endpoint = "http://127.0.0.1:$port/find-connectors"
        writeBridge(bridgeFile, endpoint)
        helper.initialize()
        for (args in listOf(
            obj("query" to ""),
            obj("query" to "x".repeat(101)),
            obj("query" to "design", "limit" to 0),
            obj("query" to "design", "limit" to 11),
            obj("query" to "design", "limit" to 1.5),
            obj("query" to "design", "url" to "private-sentinel"),
            obj("query" to "https://example.com/private-sentinel"),
            obj("query" to "www.example.com/private-sentinel"),
            obj("query" to "token=private-sentinel"),
            obj("query" to "sk-proj-" + "x".repeat(24)),
        )) {
            val reply = helper.search(args)
            assertFalse("private-sentinel" in reply.toString())
            assertEquals(JsonPrimitive(true), reply.obj("result")["isError"], args.toString())
        }
        for (invalid in listOf(
            "http://localhost:$port/find-connectors",
            "http://127.0.0.1:$port/other",
            "$endpoint?secret=private-sentinel",
            "$endpoint#private-sentinel",
            "http://user@127.0.0.1:$port/find-connectors",
            "https://example.com/find-connectors",
        )) {
            writeBridge(bridgeFile, invalid)
            assertEquals("unavailable", searchResult(helper.search(obj("query" to "design"))).str("status"), invalid)
        }
        writeBridge(bridgeFile, endpoint, bearer = "bad-token-private-sentinel")
        assertEquals("unavailable", searchResult(helper.search(obj("query" to "design"))).str("status"))
        bridgeFile.delete()
        val target = File(directory, "bridge-target")
        writeBridge(target, endpoint)
        Files.createSymbolicLink(bridgeFile.toPath(), target.toPath())
        assertEquals("unavailable", searchResult(helper.search(obj("query" to "design"))).str("status"))
        assertEquals(0, requests)
    }

    @Test
    fun `B-CHAT-048 search trims before Unicode bounds and maps a rejected bridge input safely`() {
        val seen = mutableListOf<String>()
        var reject = false
        val port =
            bridge { _, _, _, body ->
                seen += Json.parseToJsonElement(body).jsonObject.obj("arguments").str("query")
                if (reject) 400 to """{"error":"Invalid connector search request."}""" else 200 to """{"status":"ok","matches":[]}"""
            }
        writeBridge(File(CardToolServer.searchBridgePath(marker.path)), "http://127.0.0.1:$port/find-connectors")
        helper.initialize()
        val art = "🎨".repeat(100)
        assertEquals("ok", searchResult(helper.search(obj("query" to "  $art  "))).str("status"))
        assertEquals(art, seen.single())
        reject = true
        val rejected = helper.search(obj("query" to "design"))
        assertEquals(JsonPrimitive(true), rejected.obj("result")["isError"])
        assertFalse("unavailable. Try again" in rejected.toString())
    }

    @Test
    fun `B-CHAT-048 bridge responses are bounded and strictly validated`() {
        var response = ""
        val port = bridge { _, _, _, _ -> 200 to response }
        writeBridge(File(CardToolServer.searchBridgePath(marker.path)), "http://127.0.0.1:$port/find-connectors")
        helper.initialize()
        fun matchWith(key: String, value: JsonElement) =
            obj("status" to "ok", "matches" to JsonArray(listOf(JsonObject(searchMatch + (key to value))))).toString()
        for (invalid in listOf(
            "private-sentinel",
            "x".repeat(16385),
            JsonObject(searchSuccess + ("token" to JsonPrimitive("private-sentinel"))).toString(),
            matchWith("url", JsonPrimitive("https://private-sentinel")),
            matchWith("name", JsonPrimitive("https://private-sentinel")),
            matchWith("connected", JsonPrimitive("yes")),
        )) {
            response = invalid
            val reply = helper.search(obj("query" to "design"))
            assertEquals(
                obj(
                    "status" to "unavailable",
                    "message" to "Connector search is unavailable. Try again from Tools.",
                    "matches" to JsonArray(emptyList()),
                ),
                searchResult(reply),
            )
            assertFalse("private-sentinel" in reply.toString())
        }
    }

    // --- helpers ---

    private fun searchResult(reply: JsonObject): JsonObject {
        val result = reply.obj("result")
        val structured = result.obj("structuredContent")
        assertEquals(structured, Json.parseToJsonElement(result["content"]!!.jsonArray.single().jsonObject.str("text")))
        return structured
    }

    private fun writeBridge(file: File, endpoint: String, bearer: String = "a".repeat(64)) {
        file.writeText(obj("endpoint" to endpoint, "bearer" to bearer).toString())
    }

    private fun bridge(handle: (method: String, path: String, auth: String?, body: String) -> Pair<Int, String>): Int {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            val (status, text) =
                try {
                    handle(exchange.requestMethod, exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization"), body)
                } catch (e: Throwable) {
                    500 to ""
                }
            val bytes = text.encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong().takeIf { it > 0 } ?: -1)
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) } else exchange.close()
        }
        server.start()
        servers += server
        return server.address.port
    }

    private fun initParams(version: String) =
        obj("protocolVersion" to version, "capabilities" to JsonObject(emptyMap()), "clientInfo" to obj("name" to "test", "version" to "1"))

    private inner class Helper(script: File) {
        val cwd: File = directory
        private val process: Process =
            ProcessBuilder(node(), script.path).directory(cwd).start()
        private val lines = LinkedBlockingQueue<String>()
        @Volatile var stderrBytes = 0
        private var nextId = 1

        init {
            thread(isDaemon = true) { process.inputStream.bufferedReader().forEachLine { lines.put(it) } }
            thread(isDaemon = true) { stderrBytes += process.errorStream.readBytes().size }
        }

        fun initialize(version: String = "2025-11-25"): JsonObject {
            val reply = request("initialize", initParams(version))
            notification("notifications/initialized")
            return reply.obj("result")
        }

        fun call(arguments: JsonObject) = request("tools/call", obj("name" to "show", "arguments" to arguments))

        fun search(arguments: JsonObject) = request("tools/call", obj("name" to "find_connectors", "arguments" to arguments))

        fun request(method: String, params: JsonObject? = null): JsonObject {
            val id = nextId++
            val frame = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(id))
                put("method", JsonPrimitive(method))
                if (params != null) put("params", params)
            }
            raw((frame.toString() + "\n").encodeToByteArray())
            val reply = next()
            assertEquals(JsonPrimitive(id), reply["id"])
            return reply
        }

        fun notification(method: String, params: JsonObject? = null) {
            val frame = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("method", JsonPrimitive(method))
                if (params != null) put("params", params)
            }
            raw((frame.toString() + "\n").encodeToByteArray())
        }

        fun raw(bytes: ByteArray) {
            process.outputStream.write(bytes)
            process.outputStream.flush()
        }

        fun next(): JsonObject = Json.parseToJsonElement(assertNotNull(lines.poll(5, TimeUnit.SECONDS), "no reply")).jsonObject

        fun close(): Int {
            process.outputStream.close()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("helper did not exit")
            }
            Thread.sleep(50)
            return process.exitValue()
        }
    }

    private companion object {
        fun node(): String =
            System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, "node") }.firstOrNull { it.canExecute() }?.path
                ?: File("/opt/node22/bin/node").takeIf { it.canExecute() }?.path
                ?: error("B-CHAT-047/048 need Node.js (the built-in Linux runs the helper with it)")

        fun value(v: Any?): JsonElement =
            when (v) {
                null -> JsonNull
                is JsonElement -> v
                is String -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                else -> error("not json")
            }

        fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { it.first to value(it.second) })

        fun JsonObject.obj(key: String): JsonObject = this[key]!!.jsonObject

        fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content
    }
}
