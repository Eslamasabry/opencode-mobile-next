package netp

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect

/**
 * P-N5 retry trap, through the FULL Ktor client over the hardened OkHttp engine (plan 04 §3.2, §3.5, §17) plus the
 * audit BLOCKER cases (codex-04 #1): 503 Retry-After: 0, 408, bodyless mutations, and Ktor's own engine default.
 * "Exactly one request seen by the server" is the invariant for every mutation.
 */
class PN5RetryTrapTest {
    private lateinit var server: MockWebServer

    @BeforeTest fun start() { server = MockWebServer(); server.start() }
    @AfterTest fun stop() { server.close() }

    private fun client(auditFixes: Boolean = true, reassertKtorConfig: Boolean = true): HttpClient {
        val ok = HardenedOkHttp.build(
            JvmTransportSpec(origin = origin(), cleartextPermitted = true, pinnedLeafSha256 = null, auditFixes = auditFixes),
        )
        return HardenedOkHttp.ktorClient(HardenedOkHttp.ktorEngine(ok, reassertKtorConfig))
    }

    private fun origin() = "http://${server.hostName}:${server.port}"
    private fun url(path: String) = server.url(path).toString()

    private fun mutationsSeen(): Int {
        var n = 0
        while (true) {
            val r = server.takeRequest(200, TimeUnit.MILLISECONDS) ?: break
            if (r.method != "GET") n++
        }
        return n
    }

    /** Warm the pool so the mutation runs on a REUSED connection, then cut the socket once the request is read. */
    private suspend fun postOnReusedConnectionThenDisconnect(http: HttpClient): Result<HttpResponse> {
        server.enqueue(MockResponse.Builder().body("warm").build())
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(MockResponse.Builder().body("{\"resent\":true}").build()) // only reached by a resend
        assertEquals("warm", http.get(url("/api/info")).bodyAsText())
        return runCatching { http.post(url("/api/session/s/prompt")) { setBody("{\"text\":\"hi\"}") } }
    }

    @Test fun `P-N5 POST on a reused connection, disconnect after request - exactly one request`() = runBlocking {
        val r = postOnReusedConnectionThenDisconnect(client())
        assertTrue(r.exceptionOrNull() is IOException, "lost response must surface as an IOException (DeliveryUnknown)")
        assertEquals(1, mutationsSeen())
        val reused = server.requestCount // 2 = warm GET + one POST
        assertEquals(2, reused)
    }

    @Test fun `P-N5 evidence - Ktor engine without the config block re-enables retry, resends the POST`() = runBlocking {
        // plan 04 §3.2: Ktor applies its default config (retryOnConnectionFailure(true)) after `preconfigured`.
        // Without the audit's one-shot bodies, this resends. Kept as evidence that the config block is load-bearing.
        val r = postOnReusedConnectionThenDisconnect(client(auditFixes = false, reassertKtorConfig = false))
        val seen = mutationsSeen()
        println("NETP-RESULT pn5.ktorDefaultConfig.mutationsSeen=$seen outcome=${r.exceptionOrNull()?.javaClass?.simpleName ?: "200"}")
        assertEquals(2, seen, "expected the resend that the plan's config block exists to stop")
    }

    @Test fun `P-N5 audit fixes alone hold even if Ktor's default retry leaks back in`() = runBlocking {
        val r = postOnReusedConnectionThenDisconnect(client(auditFixes = true, reassertKtorConfig = false))
        assertTrue(r.isFailure)
        assertEquals(1, mutationsSeen())
    }

    @Test fun `P-N5 audit BLOCKER evidence - plan builder resends a POST on 503 Retry-After 0`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "0").build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        val r = client(auditFixes = false).post(url("/api/session/s/prompt")) { setBody("{\"text\":\"hi\"}") }
        val seen = mutationsSeen()
        println("NETP-RESULT pn5.planBuilder.503RetryAfter0.mutationsSeen=$seen finalStatus=${r.status.value}")
        assertEquals(2, seen, "codex-04 BLOCKER reproduced: retryOnConnectionFailure(false) does not stop it")
    }

    @Test fun `P-N5 503 Retry-After 0 on a POST with a body - exactly one request`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "0").build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        val r = client().post(url("/api/session/s/prompt")) { setBody("{\"text\":\"hi\"}") }
        assertEquals(503, r.status.value)
        assertEquals("0", r.headers[HardenedOkHttp.ORIGINAL_RETRY_AFTER], "the server's hint stays readable")
        assertEquals(1, mutationsSeen())
    }

    @Test fun `P-N5 503 Retry-After 0 on a bodyless POST and a DELETE - exactly one request each`() = runBlocking {
        for (call in listOf<suspend (HttpClient) -> HttpResponse>(
            { it.post(url("/api/session/s/interrupt")) },
            { it.delete(url("/api/session/s")) },
        )) {
            val http = client()
            server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "0").build())
            server.enqueue(MockResponse.Builder().body("{\"resent\":true}").build())
            assertEquals(503, call(http).status.value)
            assertEquals(1, mutationsSeen())
            server.takeRequest(10, TimeUnit.MILLISECONDS) // nothing left in flight
            server.close(); server = MockWebServer(); server.start()
        }
    }

    @Test fun `P-N5 408 on a POST - exactly one request`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(408).build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        assertEquals(408, client().post(url("/api/session/s/prompt")) { setBody("{}") }.status.value)
        assertEquals(1, mutationsSeen())
    }

    @Test fun `P-N5 307 on a POST is not followed`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(307).addHeader("Location", "/elsewhere").build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        assertEquals(307, client().post(url("/api/session/s/prompt")) { setBody("{}") }.status.value)
        assertEquals(1, mutationsSeen())
    }

    @Test fun `P-N5 GET 503 Retry-After 0 is also not retried by the stack (plan 3_5 service_starting)`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "0").build())
        server.enqueue(MockResponse.Builder().body("{}").build())
        assertEquals(503, client().get(url("/api/info")).status.value)
        assertEquals(1, server.requestCount)
    }

    @Test fun `P-N5 a request to another origin is refused before any socket`() = runBlocking {
        val e = assertFailsWith<TrustRefusal> { client().post("http://127.0.0.2:${server.port}/x") { setBody("{}") } }
        assertEquals(TrustRefusal.Kind.OriginChanged, e.kind)
        assertEquals(0, server.requestCount)
    }
}
