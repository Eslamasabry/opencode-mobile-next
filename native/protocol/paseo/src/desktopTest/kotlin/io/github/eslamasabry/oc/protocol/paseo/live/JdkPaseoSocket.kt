package io.github.eslamasabry.oc.protocol.paseo.live

import io.github.eslamasabry.oc.protocol.paseo.PaseoEndpoint
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoFrame
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoSocket
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoSocketFactory
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

/**
 * TEST-ONLY socket for the live proof (`PaseoLiveProofTest`), on the JDK's `java.net.http` WebSocket: it offers no
 * extensions (the JDK refuses an extension answer it did not offer), never follows redirects, bypasses proxies
 * (plan 04 §5.2 NO_PROXY) and caps a message at [maxMessageBytes]. The production socket is `:core:net`'s `WsSession`
 * (P-N1, plan 04 §5.2); `:protocol:paseo` may not have platform source sets (ADR §7.2), so this lives in desktopTest.
 */
class JdkPaseoSocketFactory(private val maxMessageBytes: Int = PaseoTransport.MAX_FRAME_BYTES) : PaseoSocketFactory {
    override suspend fun open(endpoint: PaseoEndpoint, password: String): PaseoSocket {
        val client = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(8))
            .build()
        val socket = JdkPaseoSocket(maxMessageBytes)
        val builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
        if (password.isNotEmpty()) {
            builder.subprotocols("paseo.bearer.$password")
            builder.header("Authorization", "Bearer $password")
        }
        try {
            socket.ws = builder.buildAsync(URI(endpoint.url), socket.listener).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val handshake = (e as? WebSocketHandshakeException) ?: (e.cause as? WebSocketHandshakeException)
                ?: ((e as? CompletionException)?.cause as? WebSocketHandshakeException)
            val status = handshake?.response?.statusCode()
            throw when {
                status == 401 -> PaseoException(PaseoFailureKind.Authentication)
                // 403: a refused password when one was sent, else a host outside the daemon's allowlist.
                status == 403 -> PaseoException(if (password.isEmpty()) PaseoFailureKind.HostRefused else PaseoFailureKind.Authentication)
                handshake != null -> PaseoException(PaseoFailureKind.InvalidResponse)
                else -> PaseoException(PaseoFailureKind.Disconnected)
            }
        }
        return socket
    }
}

private class JdkPaseoSocket(private val maxMessageBytes: Int) : PaseoSocket {
    private val channel = Channel<PaseoFrame>(Channel.UNLIMITED)
    private val sends = Mutex()
    lateinit var ws: WebSocket
    override val incoming get() = channel

    @Volatile override var closeCode: Int? = null

    val listener = object : WebSocket.Listener {
        private val text = StringBuilder()
        private val bytes = ByteArrayOutputStream()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (text.length > maxMessageBytes) {
                webSocket.sendClose(1009, "")
                channel.close()
                return null
            }
            if (last) {
                channel.trySend(PaseoFrame.Text(text.toString()))
                text.setLength(0)
            }
            webSocket.request(1)
            return null
        }

        override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*>? {
            val chunk = ByteArray(data.remaining())
            data.get(chunk)
            bytes.write(chunk)
            if (bytes.size() > maxMessageBytes) {
                webSocket.sendClose(1009, "")
                channel.close()
                return null
            }
            if (last) {
                channel.trySend(PaseoFrame.Binary(bytes.toByteArray()))
                bytes.reset()
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String?): CompletionStage<*>? {
            closeCode = statusCode
            channel.close()
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable?) {
            channel.close()
        }
    }

    override suspend fun send(text: String) {
        sends.withLock { ws.sendText(text, true).await() }
    }

    override suspend fun close() {
        if (!::ws.isInitialized) return
        withTimeoutOrNull(2_000) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "").await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Already closed.
            }
        }
        ws.abort()
        channel.close()
    }
}
