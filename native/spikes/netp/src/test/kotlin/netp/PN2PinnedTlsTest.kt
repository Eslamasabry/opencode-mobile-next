package netp

import java.security.MessageDigest
import java.security.Provider
import java.security.cert.CertificateExpiredException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.conscrypt.Conscrypt
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * P-N2 on the JVM: `PinnedLeafTrustManager` + hostname verifier, TLS by Conscrypt (the Android platform provider) and,
 * for comparison, the JDK's SunJSSE. Server: okhttp-tls self-signed leaves on mockwebserver3.
 * The emulator half (API 37, 10.0.2.2) needs KVM, which this container lacks; see the report.
 */
class PN2PinnedTlsTest {
    private val servers = mutableListOf<MockWebServer>()

    @AfterTest fun stop() = servers.forEach { it.close() }

    private fun tlsServer(cert: HeldCertificate): MockWebServer {
        val s = MockWebServer()
        val server = HandshakeCertificates.Builder().heldCertificate(cert).build()
        s.useHttps(server.sslSocketFactory())
        s.start()
        servers += s
        return s
    }

    private fun leaf(cn: String = "localhost", notBefore: Long? = null, notAfter: Long? = null): HeldCertificate =
        HeldCertificate.Builder().commonName(cn).addSubjectAlternativeName(cn)
            .apply { if (notBefore != null && notAfter != null) validityInterval(notBefore, notAfter) }
            .build()

    private fun sha256(cert: HeldCertificate) = MessageDigest.getInstance("SHA-256").digest(cert.certificate.encoded)

    private fun client(server: MockWebServer, pin: ByteArray, provider: Provider?): OkHttpClient =
        HardenedOkHttp.build(
            JvmTransportSpec(
                origin = "https://${server.hostName}:${server.port}",
                cleartextPermitted = false,
                pinnedLeafSha256 = pin,
                sslProvider = provider,
            ),
        )

    private fun OkHttpClient.get(server: MockWebServer) =
        newCall(Request.Builder().url(server.url("/api/info")).header("Authorization", "Basic c2VjcmV0").build()).execute()

    private fun pinOf(c: OkHttpClient) = c.x509TrustManager as PinnedLeafTrustManager

    private fun requestsSeen(server: MockWebServer): Int {
        var n = 0
        while (true) {
            val r = server.takeRequest(100, TimeUnit.MILLISECONDS) ?: break
            if (r.failure == null && r.method.isNotEmpty()) n++
        }
        return n
    }

    @ParameterizedTest(name = "{0}") @MethodSource("providers")
    fun `P-N2 matching pin works`(name: String, provider: Provider?) {
        val cert = leaf()
        val server = tlsServer(cert)
        server.enqueue(MockResponse.Builder().body("ok").build())
        val c = client(server, sha256(cert), provider)
        if (provider != null) assertTrue(Conscrypt.isConscrypt(c.sslSocketFactory), "handshake must run in Conscrypt")
        c.get(server).use { assertEquals("ok", it.body.string()) }
        assertTrue(pinOf(c).checks >= 1, "$name routed the chain through the pinned manager")
        assertEquals(1, requestsSeen(server))
    }

    @ParameterizedTest(name = "{0}") @MethodSource("providers")
    fun `P-N2 pin is the identity - a leaf for another hostname still passes`(name: String, provider: Provider?) {
        val cert = leaf(cn = "not-this-host.example")
        val server = tlsServer(cert)
        server.enqueue(MockResponse.Builder().body("ok").build())
        client(server, sha256(cert), provider).get(server).use { assertEquals(200, it.code) }
    }

    @ParameterizedTest(name = "{0}") @MethodSource("providers")
    fun `P-N2 changed certificate fails in the handshake, latches, and the server sees zero requests`(name: String, provider: Provider?) {
        val pinned = leaf()
        val server = tlsServer(leaf()) // a different key and certificate on the same origin
        server.enqueue(MockResponse.Builder().body("never").build())
        server.enqueue(MockResponse.Builder().body("never").build())
        val c = client(server, sha256(pinned), provider)
        val e = assertFailsWith<SSLException> { c.get(server) }
        assertTrue(generateSequence<Throwable>(e) { it.cause }.any { it is PinMismatchException }, "cause: $e")
        assertTrue(pinOf(c).latchedMismatch)
        val refusal = assertFailsWith<TrustRefusal> { c.get(server) }
        assertEquals(TrustRefusal.Kind.CertificateChanged, refusal.kind)
        assertEquals(0, requestsSeen(server), "no HTTP byte (and no Authorization) may reach a mismatched server")
        println("NETP-RESULT pn2.$name.changedCert=${e.javaClass.simpleName}/PinMismatchException latched=true requests=0")
    }

    @ParameterizedTest(name = "{0}") @MethodSource("providers")
    fun `P-N2 expired matching certificate is its own error and does not latch`(name: String, provider: Provider?) {
        val now = System.currentTimeMillis()
        val cert = leaf(notBefore = now - 10 * DAY, notAfter = now - DAY)
        val server = tlsServer(cert)
        server.enqueue(MockResponse.Builder().body("never").build())
        val c = client(server, sha256(cert), provider)
        val e = assertFailsWith<SSLException> { c.get(server) }
        val chain = generateSequence<Throwable>(e) { it.cause }.toList()
        assertTrue(chain.any { it is CertificateExpiredException }, "cause chain: $chain")
        assertTrue(chain.none { it is PinMismatchException })
        assertTrue(!pinOf(c).latchedMismatch, "expired != changed (B-SEC-007)")
        assertEquals(0, requestsSeen(server))
    }

    @ParameterizedTest(name = "{0}") @MethodSource("providers")
    fun `P-N2 a pinned client refuses plain http before any socket`(name: String, provider: Provider?) {
        val cert = leaf()
        val server = tlsServer(cert)
        val c = HardenedOkHttp.build(
            JvmTransportSpec("http://${server.hostName}:${server.port}", true, sha256(cert), sslProvider = provider),
        )
        val e = assertFailsWith<TrustRefusal> {
            c.newCall(Request.Builder().url("http://${server.hostName}:${server.port}/api/info").build()).execute()
        }
        assertEquals(TrustRefusal.Kind.PinNeedsHttps, e.kind)
    }

    companion object {
        private const val DAY = 24L * 3600 * 1000

        @JvmStatic fun providers() = listOf(
            arrayOf<Any?>("Conscrypt", Conscrypt.newProvider()),
            arrayOf<Any?>("SunJSSE", null),
        )
    }
}
