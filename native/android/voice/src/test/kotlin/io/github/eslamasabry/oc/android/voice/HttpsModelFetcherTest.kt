package io.github.eslamasabry.oc.android.voice

import io.github.eslamasabry.oc.android.voice.net.HttpsModelFetcher
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failureOrNull
import io.github.eslamasabry.oc.engine.voice.models.FetchRequest
import io.github.eslamasabry.oc.engine.voice.models.FetchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A real socket on the loopback address; the fetcher's connect seam points the https URL at it. */
class HttpsModelFetcherTest {
    private val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
    private val heads = mutableListOf<String>()
    private var answer: (String) -> ByteArray = { ok("hello".toByteArray()) }

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    val input = it.getInputStream().bufferedReader()
                    val head = generateSequence { input.readLine() }.takeWhile { line -> line.isNotEmpty() }.joinToString("\n")
                    synchronized(heads) { heads += head }
                    it.getOutputStream().write(answer(head))
                    it.getOutputStream().flush()
                }
            }
        }
    }

    @After
    fun stop() = server.close()

    private fun ok(body: ByteArray, extra: String = "") =
        ("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n$extra\r\n").toByteArray() + body

    private val fetcher =
        HttpsModelFetcher(
            io = Dispatchers.IO,
            connect = { url -> URL("http", server.inetAddress.hostAddress, server.localPort, URL(url).path).openConnection() as HttpURLConnection },
        )

    private suspend fun FetchResponse.body(): String {
        val out = StringBuilder()
        val buffer = ByteArray(4)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.append(String(buffer, 0, n))
        }
        close()
        return out.toString()
    }

    @Test
    fun `B-MEDIA-025 a GET sends the voice user agent, the range and identity encoding, and no cookies`() =
        runBlocking {
            val response = assertIs<Outcome.Ok<FetchResponse>>(fetcher.open(FetchRequest("https://huggingface.co/r/resolve/v/f", rangeStart = 7))).value
            assertEquals(200, response.status)
            assertEquals(5L, response.contentLength)
            assertEquals("hello", response.body())
            val head = heads.single()
            assertTrue(head.startsWith("GET /r/resolve/v/f HTTP/1.1"))
            assertTrue("User-Agent: OpenCode-Mobile/voice-model" in head)
            assertTrue("Range: bytes=7-" in head)
            assertTrue("Accept-Encoding: identity" in head)
            assertTrue("Cookie" !in head && "Authorization" !in head)
        }

    @Test
    fun `B-MEDIA-025 redirects are handed back, never followed, and a missing length is allowed`() =
        runBlocking {
            answer = { "HTTP/1.1 302 Found\r\nLocation: https://cdn.example/x\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray() }
            val redirect = assertIs<Outcome.Ok<FetchResponse>>(fetcher.open(FetchRequest("https://huggingface.co/a", null))).value
            assertEquals(302, redirect.status)
            assertEquals("https://cdn.example/x", redirect.location)
            redirect.close()
            assertEquals(1, heads.size)

            answer = { "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 2-4/5\r\nConnection: close\r\n\r\nllo".toByteArray() }
            val partial = assertIs<Outcome.Ok<FetchResponse>>(fetcher.open(FetchRequest("https://huggingface.co/a", 2))).value
            assertEquals("bytes 2-4/5", partial.contentRange)
            assertNull(partial.contentLength)
            assertEquals("llo", partial.body())
        }

    @Test
    fun `B-MEDIA-025 anything but HTTPS is refused before connecting`() =
        runBlocking {
            val real = HttpsModelFetcher(Dispatchers.IO)
            assertEquals(
                MediaProblem.DownloadHttpsRequired,
                (real.open(FetchRequest("http://huggingface.co/a", null)).failureOrNull() as Failure.Media).problem,
            )
        }

    @Test
    fun `B-MEDIA-013 a server that is not there is a plain kind with no URL in it`() =
        runBlocking {
            server.close()
            val failure = fetcher.open(FetchRequest("https://huggingface.co/a?token=x", null)).failureOrNull() as Failure.Media
            assertEquals(MediaProblem.DownloadNoResponse, failure.problem)
            assertTrue("huggingface" !in failure.details!!.text && "token" !in failure.details!!.text)
        }
}
