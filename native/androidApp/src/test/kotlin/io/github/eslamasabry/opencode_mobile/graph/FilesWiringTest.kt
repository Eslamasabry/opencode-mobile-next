package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.files.ShareHandler
import io.github.eslamasabry.oc.engine.files.WifiShareRules
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
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
}
