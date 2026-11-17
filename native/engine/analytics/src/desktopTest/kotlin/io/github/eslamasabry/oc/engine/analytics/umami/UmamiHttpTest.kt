package io.github.eslamasabry.oc.engine.analytics.umami

import io.github.eslamasabry.oc.engine.analytics.DeviceClass
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UmamiHttpTest {
    private val source =
        UmamiSource(
            "00000000-0000-4000-8000-000000000001",
            UmamiBuild("3.0.1", "10081"),
            UmamiDevice("en", "browser", 16, DeviceClass.Phone),
        )

    @Test
    fun `B-180 a server that accepts but never answers is cut off by the total timeout`() = runTest {
        val release = CountDownLatch(1)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val accepted = CountDownLatch(1)
            val thread = Thread {
                server.accept().use { socket ->
                    accepted.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }.apply { isDaemon = true; start() }
            try {
                val event = umamiEvent("app_open", mapOf("kind" to "cold"), umamiPages.getValue("conversations"),
                    source, 1000)!!
                val transport = UmamiHttp("http://localhost:${server.localPort}") { "Mozilla/5.0" }
                val start = System.nanoTime()
                val failed = transport.prepare(listOf(event)).execute() is Outcome.Failed
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                assertTrue(accepted.await(1, TimeUnit.SECONDS), "the test reached the unresponsive server")
                assertTrue(failed && elapsedMs >= 1000, "the unresponsive request must return a typed failure")
                assertTrue(elapsedMs < 6000, "a dead endpoint cannot hold the worker indefinitely: $elapsedMs")
                assertTrue(UmamiHttp.CALL_TIMEOUT_MS <= 3000 && UmamiHttp.CONNECT_TIMEOUT_MS <= 1500)
            } finally {
                release.countDown()
                thread.join(1000)
            }
        }
    }

    /** One loopback exchange; returns the request line and the exact request body bytes the app put on the wire. */
    private fun capture(reply: String, send: (String) -> Unit): Pair<String, String> {
        var line = ""
        var body = ""
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val thread = Thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) head.append(input.read().toChar())
                    line = head.lineSequence().first()
                    val length = Regex("(?i)content-length: *(\\d+)").find(head)!!.groupValues[1].toInt()
                    body = String(input.readNBytes(length), Charsets.UTF_8)
                    val bytes = reply.toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n" + reply).toByteArray())
                    socket.getOutputStream().flush()
                }
            }.apply { isDaemon = true; start() }
            send("http://localhost:${server.localPort}")
            thread.join(5000)
        }
        return line to body
    }

    private fun sample(name: String, props: Map<String, String>, at: Long) = umamiEvent(name, props, umamiPages.getValue("conversations"),
        source, at)!!

    @Test
    fun `B-180 the single event request on the wire pins the monthly visitor hash period`() = runTest {
        val (line, body) = capture("""{"sessionId":"s","visitId":"v"}""") { url ->
            kotlinx.coroutines.runBlocking { UmamiHttp(url) { "Mozilla/5.0" }.prepare(listOf(sample("app_open", mapOf("kind" to "cold"), 1000))).execute() }
        }
        assertTrue(line.startsWith("POST /api/send "), line)
        val payload = UmamiCodec.json.parseToJsonElement(body).jsonObject.getValue("payload").jsonObject
        assertEquals("month", payload["salt"]?.jsonPrimitive?.content, "the period is ours, not the server's default")
    }

    @Test
    fun `B-180 every item of a batch request on the wire pins the monthly visitor hash period`() = runTest {
        val reply = """{"size":2,"processed":2,"errors":0,"details":[]}"""
        val (line, body) = capture(reply) { url ->
            kotlinx.coroutines.runBlocking {
                UmamiHttp(url) { "Mozilla/5.0" }.prepare(listOf(sample("app_open", mapOf("kind" to "cold"), 1000), sample("app_open", mapOf("kind" to "warm"), 2000))).execute()
            }
        }
        assertTrue(line.startsWith("POST /api/batch "), line)
        val items: JsonArray = UmamiCodec.json.parseToJsonElement(body).jsonArray
        assertEquals(2, items.size)
        for (item in items) {
            assertEquals("month", (item as JsonObject).getValue("payload").jsonObject["salt"]?.jsonPrimitive?.content)
        }
    }

    private fun wirePayload(device: UmamiDevice, props: Map<String, String> = mapOf("kind" to "cold")): JsonObject {
        val src = UmamiSource(source.website, source.build, device)
        val event = umamiEvent("app_open", props, umamiPages.getValue("conversations"), src, 1000)!!
        val (_, body) = capture("""{"sessionId":"s","visitId":"v"}""") { url ->
            kotlinx.coroutines.runBlocking { UmamiHttp(url) { "Mozilla/5.0" }.prepare(listOf(event)).execute() }
        }
        val resolution = Regex("[0-9]{3,5}\\s*[x\u00d7]\\s*[0-9]{3,5}")
        assertTrue(!resolution.containsMatchIn(body), "no resolution anywhere in the body")
        return UmamiCodec.json.parseToJsonElement(body).jsonObject.getValue("payload").jsonObject
    }

    @Test
    fun `B-SEC-214 the request body on the wire carries no screen resolution`() = runTest {
        val payload = wirePayload(source.device)
        assertTrue("screen" !in payload.keys, "the exact screen size is not sent: ${payload.keys}")
    }

    @Test
    fun `B-SEC-215 the wire carries the Android major, the device class, the build and the tag`() = runTest {
        val payload = wirePayload(source.device)
        val data = payload.getValue("data").jsonObject
        assertEquals("16", data["os_major"]?.jsonPrimitive?.content)
        assertEquals("phone", data["device_class"]?.jsonPrimitive?.content)
        assertEquals("10081", data["build"]?.jsonPrimitive?.content)
        assertEquals("10081", payload["tag"]?.jsonPrimitive?.content, "the tag stays")
        val tablet = wirePayload(UmamiDevice("en", "browser", 15, DeviceClass.Tablet)).getValue("data").jsonObject
        assertEquals("15", tablet["os_major"]?.jsonPrimitive?.content)
        assertEquals("tablet", tablet["device_class"]?.jsonPrimitive?.content)
    }

    @Test
    fun `B-SEC-216 guard - the wire payload holds exactly the allowed device facts and nothing else`() = runTest {
        val payload = wirePayload(source.device)
        assertEquals(
            setOf("website", "hostname", "language", "url", "title", "tag", "timestamp", "data", "name", "salt"),
            payload.keys,
        )
        val dataKeys = payload.getValue("data").jsonObject.keys
        assertEquals(setOf("kind", "version", "build", "os_major", "device_class"), dataKeys)
        // A new fact about the device needs a decision: it fails here until the notices and this list say so.
        assertEquals(
            listOf("language", "userAgent", "osMajor", "deviceClass"),
            UmamiDevice::class.java.declaredFields.map { it.name }.filter { !it.startsWith("$") },
        )
        // A foldable or desktop gives no class at all, and an absurd major no version: nothing invented.
        val odd = wirePayload(UmamiDevice("en", "browser", 0, DeviceClass.Foldable)).getValue("data").jsonObject
        assertTrue("os_major" !in odd.keys && "device_class" !in odd.keys, odd.keys.toString())
    }
}
