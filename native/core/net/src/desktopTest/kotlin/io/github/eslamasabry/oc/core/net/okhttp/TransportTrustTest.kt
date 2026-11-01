package io.github.eslamasabry.oc.core.net.okhttp

import io.github.eslamasabry.oc.core.common.CertificateWhy
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.HostClass
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Review w1-core-net MAJOR 4, 6 and 10 on the real stack (Ktor client factory over the OkHttp engine factory, real
 * loopback sockets, so real time). Plan 04 §3.1 (lifetime), §3.3–3.4 (timeouts), §4.4–4.6 (pin, trust gate).
 */
class TransportTrustTest {
    private val servers = mutableListOf<CountingServer>()
    private val clients = mutableListOf<NetClient>()

    @AfterTest
    fun stop() {
        clients.forEach { it.close() }
        servers.forEach { it.close() }
    }

    private fun plainServer() = CountingServer().also { servers += it }

    private fun tlsServer(leaf: SelfSignedLeaf): CountingServer {
        val socket = leaf.serverContext().serverSocketFactory.createServerSocket(0, 50, InetAddress.getLoopbackAddress())
        return CountingServer(socket, scheme = "https").also { servers += it }
    }

    private fun leaf(): SelfSignedLeaf = SelfSignedLeaf.create()

    private fun sha256(leaf: SelfSignedLeaf) = MessageDigest.getInstance("SHA-256").digest(leaf.certificate.encoded)

    private fun net(spec: TransportSpec, onBuilt: (OkHttpClient) -> Unit = {}): NetClient {
        val engines = OkHttpEngineFactory(base = { s -> hardenedBaseClient(s).also(onBuilt) })
        return HttpClientFactory(engines).create(spec).also { clients += it }
    }

    private fun spec(
        scheme: String,
        port: Int,
        cleartext: Boolean = false,
        pin: ByteArray? = null,
        readTimeoutMs: Long = 120_000,
    ) = TransportSpec(
        origin = ServerOrigin(scheme, "127.0.0.1", port),
        hostClass = HostClass.PrivateLan,
        cleartextPermitted = cleartext,
        pinnedLeafSha256 = pin,
        readTimeoutMs = readTimeoutMs,
    )

    private fun failureOf(outcome: Outcome<*>): Failure = assertIs<Outcome.Failed>(outcome).failure

    private suspend fun NetClient.get(url: String): Outcome<String> = exchange({ url(url) }) { it.bodyAsText() }

    @Test
    fun `B-SEC-007 plain http without consent is refused and nothing is sent (review MAJOR 4)`() = runBlocking {
        val server = plainServer()
        val outcome = net(spec("http", server.port, cleartext = false)).get(server.url("/api/health"))
        assertEquals(Failure.Refused(RefusalReason.CleartextNotConfirmed), failureOf(outcome))
        assertEquals(0, server.connectionsSeen.get())
    }

    @Test
    fun `B-SEC-007 a request to another origin is refused and nothing is sent (review MAJOR 4)`() = runBlocking {
        val server = plainServer()
        val outcome = net(spec("http", server.port + 1, cleartext = true)).get(server.url("/api/health"))
        assertEquals(Failure.Refused(RefusalReason.OriginChanged), failureOf(outcome))
        assertEquals(0, server.connectionsSeen.get())
    }

    @Test
    fun `B-SEC-007 a pin on plain http is refused (review MAJOR 4)`() = runBlocking {
        val server = plainServer()
        val outcome = net(spec("http", server.port, cleartext = true, pin = ByteArray(32))).get(server.url("/api/health"))
        assertEquals(Failure.Refused(RefusalReason.PinNeedsHttps), failureOf(outcome))
        assertEquals(0, server.connectionsSeen.get())
    }

    @Test
    fun `B-SEC-007 a different leaf never passes a pinned client and no request is written (review MAJOR 4)`() = runBlocking {
        val served = leaf()
        val server = tlsServer(served)
        server.handler = { _, _ -> Reply.Ok("secret data") }
        val client = net(spec("https", server.port, pin = sha256(leaf())))
        assertEquals(Failure.Certificate(CertificateWhy.Changed), failureOf(client.get(server.url("/api/health"))))
        // Latched: the next call on this client is refused before any socket.
        val connections = server.connectionsSeen.get()
        assertEquals(Failure.Certificate(CertificateWhy.Changed), failureOf(client.get(server.url("/api/health"))))
        assertEquals(connections, server.connectionsSeen.get())
        assertEquals(0, server.requests.size)
    }

    @Test
    fun `B-SEC-007 the pinned leaf is the identity, without CA trust (review MAJOR 4)`() = runBlocking {
        val served = leaf()
        val server = tlsServer(served)
        server.handler = { _, _ -> Reply.Ok("hello") }
        val outcome = net(spec("https", server.port, pin = sha256(served))).get(server.url("/api/health"))
        assertEquals("hello", assertIs<Outcome.Ok<String>>(outcome).value)
    }

    @Test
    fun `B-PROTO-148 closing the client ends an open stream and frees the engine (review MAJOR 6)`() = runBlocking {
        val server = plainServer()
        server.handler = { _, _ -> Reply.Chunked(listOf("data: a\n\n"), gapMs = 0, hangAfter = true) }
        var built: OkHttpClient? = null
        val client = net(spec("http", server.port, cleartext = true)) { built = it }
        val stream = async(Dispatchers.IO) {
            client.exchange({ url(server.url("/api/event")) }) { response ->
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(64)
                while (channel.readAvailable(buffer, 0, buffer.size) >= 0) Unit
            }
        }
        withTimeout(5_000) { while (server.requests.isEmpty()) kotlinx.coroutines.delay(10) }
        client.close()
        // The reader ends at once: as a failed exchange, or cancelled with the engine's call context.
        val ended: Any = withTimeout(5_000) {
            try {
                stream.await()
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) throw e
                e
            }
        }
        assertTrue(ended is Outcome.Failed || ended is CancellationException, "$ended")
        val okhttp = built!!
        withTimeout(5_000) { while (!okhttp.dispatcher.executorService.isShutdown) kotlinx.coroutines.delay(10) }
        assertEquals(0, okhttp.connectionPool.connectionCount())
        assertEquals(0, okhttp.dispatcher.runningCallsCount())
    }

    @Test
    fun `plan 04 section 3_4 a reply that keeps sending past the read timeout is not cut off (review MAJOR 10)`() = runBlocking {
        val server = plainServer()
        // 8 chunks 200 ms apart: 1.6 s in all, never 600 ms without a byte.
        server.handler = { _, _ -> Reply.Chunked(List(8) { "part$it;" }, gapMs = 200) }
        val outcome = net(spec("http", server.port, cleartext = true, readTimeoutMs = 600)).get(server.url("/export"))
        assertEquals((0 until 8).joinToString("") { "part$it;" }, assertIs<Outcome.Ok<String>>(outcome).value)
    }

    @Test
    fun `plan 04 section 3_4 a reply that goes quiet for the read timeout times out`() = runBlocking {
        val server = plainServer()
        server.handler = { _, _ -> Reply.Chunked(listOf("start;"), gapMs = 0, hangAfter = true) }
        val outcome = net(spec("http", server.port, cleartext = true, readTimeoutMs = 600)).get(server.url("/export"))
        assertTrue(failureOf(outcome) is Failure.TimedOut, "${failureOf(outcome)}")
    }
}

/**
 * A self-signed `localhost` leaf made by the JDK's own `keytool` (`:core:net` may not use `okhttp-tls`; module rules).
 * No CA trusts it, so only a pin can make a client accept it.
 */
internal class SelfSignedLeaf private constructor(private val keyStore: KeyStore) {
    val certificate: X509Certificate = keyStore.getCertificate(ALIAS) as X509Certificate

    fun serverContext(): SSLContext {
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, PASSWORD) }
        return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
    }

    companion object {
        private const val ALIAS = "leaf"
        private val PASSWORD = "changeit".toCharArray()

        fun create(): SelfSignedLeaf {
            val dir = Files.createTempDirectory("oc-leaf")
            val file = dir.resolve("leaf.p12")
            val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
            val process = ProcessBuilder(
                keytool, "-genkeypair", "-alias", ALIAS, "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2",
                "-keystore", file.toString(), "-storetype", "PKCS12", "-storepass", String(PASSWORD),
            ).redirectErrorStream(true).start()
            val output = process.inputStream.readBytes().decodeToString()
            check(process.waitFor() == 0) { "keytool failed: $output" }
            val store = KeyStore.getInstance("PKCS12")
            Files.newInputStream(file).use { store.load(it, PASSWORD) }
            Files.delete(file)
            Files.delete(dir)
            return SelfSignedLeaf(store)
        }
    }
}
