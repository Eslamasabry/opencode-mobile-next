package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.files.ShareHandler
import io.github.eslamasabry.oc.engine.files.WifiShareRules
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/** The real Wi-Fi share socket over HTTP (B-MEDIA-034), bound to loopback for the test only. */
class FilesWiringTest {
    @Test
    fun `B-MEDIA-034 the Android socket serves the token path, 404 elsewhere, 405 for POST, counts downloads`() = runTest {
        val data = FileData("dir/report final.txt", "text/plain", "hello wifi".encodeToByteArray())
        val token = "abcDEF123_-xyz"
        var served = 0
        val handler = object : ShareHandler {
            override fun reply(method: String, rawPath: String) = WifiShareRules.reply(method, rawPath, token, "report final.txt", data)
            override fun served() { served++ }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = SocketShareServer(ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")), handler, scope)
        server.start()
        val base = "http://127.0.0.1:${server.port}"
        fun get(path: String, method: String = "GET"): Triple<Int, String, Map<String, List<String>>> {
            val c = URL(base + path).openConnection(Proxy.NO_PROXY) as HttpURLConnection
            c.requestMethod = method
            if (method == "POST") { c.doOutput = true; c.outputStream.write(byteArrayOf(1)) }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.decodeToString() ?: ""
            return Triple(code, text, c.headerFields.filterKeys { it != null })
        }
        val ok = withContext(Dispatchers.IO) { get("/$token/report%20final.txt") }
        assertEquals(200, ok.first)
        assertEquals("hello wifi", ok.second)
        assertTrue(ok.third["Content-Disposition"]!!.single().startsWith("attachment;"))
        // The count lands right after the last byte is flushed; give the server thread a moment.
        withContext(Dispatchers.IO) { repeat(100) { if (served == 0) Thread.sleep(10) } }
        assertEquals(1, served)
        assertEquals(404, withContext(Dispatchers.IO) { get("/") }.first)
        assertEquals(404, withContext(Dispatchers.IO) { get("/wrong/report%20final.txt") }.first)
        assertEquals(405, withContext(Dispatchers.IO) { get("/$token/report%20final.txt", "POST") }.first)
        assertEquals(200, withContext(Dispatchers.IO) { get("/$token/report%20final.txt", "HEAD") }.first)
        assertEquals(1, served)
        server.close()
        assertFailsWith<java.io.IOException> { withContext(Dispatchers.IO) { get("/$token/report%20final.txt") } }
        scope.cancel()
    }

    @Test
    fun `B-MEDIA-034 Stop sharing also ends a download that is already running`() = runTest {
        val size = 64 * 1024 * 1024
        val data = FileData("big.bin", "application/octet-stream", ByteArray(size))
        val token = "tok"
        val handler = object : ShareHandler {
            override fun reply(method: String, rawPath: String) = WifiShareRules.reply(method, rawPath, token, "big.bin", data)
            override fun served() {}
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = SocketShareServer(ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")), handler, scope)
        server.start()
        val received = withContext(Dispatchers.IO) {
            Socket(Proxy.NO_PROXY).use { client ->
                client.connect(InetSocketAddress("127.0.0.1", server.port))
                client.soTimeout = 5_000
                client.getOutputStream().write("GET /$token/big.bin HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                val input = client.getInputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                // The download has started: the first bytes arrived.
                while (total < 128 * 1024) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                }
                server.close()
                Thread.sleep(200)
                try {
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                    }
                } catch (_: java.io.IOException) {
                    // A reset also ends it.
                }
                total
            }
        }
        assertTrue(received < size, "the download kept running after Stop ($received of $size bytes)")
        scope.cancel()
    }
}
