package netp

import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.streams.asByteWriteChannel
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameTooBigException
import io.ktor.websocket.RawWebSocket
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readReason
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.buffer

/** Plan 04 §5.2 `WsSpec`. */
data class WsSpec(
    val url: String,
    val headers: List<Pair<String, String>>,
    val subprotocol: String?,
    val maxMessageBytes: Long,
    val pingIntervalMs: Long = 20_000,
    val pongTimeoutMs: Long = 15_000,
    val handshakeTimeoutMs: Long = 8_000,
) {
    override fun toString(): String = "WsSpec(url=<redacted>, headers=${headers.size}, max=$maxMessageBytes)"
}

sealed interface WsMessage {
    data class Text(val text: String) : WsMessage
    class Binary(val bytes: ByteArray) : WsMessage
}

data class WsClose(val code: Short, val reason: String, val byPeer: Boolean)

class WsHandshakeFailure(val kind: Kind) : IOException(kind.name) {
    enum class Kind { SignInNeeded, Forbidden, NotSwitching, NotUpgraded, BadAccept, ExtensionNegotiated, SubprotocolMismatch, Timeout }
}

object OkHttpWebSockets {
    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    private val random = SecureRandom()

    /** Steps 1-5 of plan 04 §5.2. [scope] owns the socket's coroutines (the graph scope in production). */
    suspend fun connect(client: OkHttpClient, spec: WsSpec, scope: CoroutineScope): WsSession {
        val upgradeClient = client.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val key = Base64.getEncoder().encodeToString(ByteArray(16).also(random::nextBytes))
        val request = Request.Builder().url(spec.url) // ws(s):// becomes http(s):// in OkHttp
            .header("Connection", "Upgrade")
            .header("Upgrade", "websocket")
            .header("Sec-WebSocket-Version", "13")
            .header("Sec-WebSocket-Key", key)
            .apply { spec.subprotocol?.let { header("Sec-WebSocket-Protocol", it) } }
            .apply { spec.headers.forEach { (k, v) -> header(k, v) } }
            .build()
        val call = upgradeClient.newCall(request)
        val response = withTimeoutOrNull(spec.handshakeTimeoutMs) { call.await() }
            ?: run { call.cancel(); throw WsHandshakeFailure(WsHandshakeFailure.Kind.Timeout) }
        try {
            validate(response, key, spec.subprotocol)
        } catch (e: WsHandshakeFailure) {
            response.close()
            throw e
        }
        val socket = response.socket ?: run { response.close(); throw WsHandshakeFailure(WsHandshakeFailure.Kind.NotUpgraded) }
        val input = socket.source.buffer().inputStream().toByteReadChannel(Dispatchers.IO)
        val output = socket.sink.buffer().outputStream().asByteWriteChannel()
        val raw = RawWebSocket(
            input, output,
            maxFrameSize = spec.maxMessageBytes,
            masking = true,
            // Ktor's writer rethrows from `flushAndClose()` once the socket is gone ("Socket closed"); without a handler
            // that escapes as an uncaught exception (seen on the test server side), which would crash an Android app.
            coroutineContext = scope.coroutineContext + Job(scope.coroutineContext[Job]) + Dispatchers.IO +
                CoroutineExceptionHandler { _, _ -> },
        )
        return WsSession(raw, spec) { socket.cancel(); response.close() }
    }

    private fun validate(r: Response, key: String, subprotocol: String?) {
        when (r.code) {
            101 -> Unit
            401 -> throw WsHandshakeFailure(WsHandshakeFailure.Kind.SignInNeeded)
            403 -> throw WsHandshakeFailure(WsHandshakeFailure.Kind.Forbidden)
            else -> throw WsHandshakeFailure(WsHandshakeFailure.Kind.NotSwitching)
        }
        val connectionUpgrade = r.headers("Connection").flatMap { it.split(',') }.any { it.trim().equals("upgrade", true) }
        if (!r.header("Upgrade").equals("websocket", ignoreCase = true) || !connectionUpgrade) {
            throw WsHandshakeFailure(WsHandshakeFailure.Kind.NotUpgraded)
        }
        val expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray()))
        if (r.header("Sec-WebSocket-Accept") != expected) throw WsHandshakeFailure(WsHandshakeFailure.Kind.BadAccept)
        if (r.header("Sec-WebSocket-Extensions") != null) throw WsHandshakeFailure(WsHandshakeFailure.Kind.ExtensionNegotiated)
        val echoed = r.header("Sec-WebSocket-Protocol")
        if (echoed != null && echoed != subprotocol) throw WsHandshakeFailure(WsHandshakeFailure.Kind.SubprotocolMismatch)
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, _, _ -> response.close() }
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        })
    }
}

/** Plan 04 §5.2 step 5: pings, pong timeout, fragment reassembly with a running total cap, close handling. */
class WsSession internal constructor(
    private val raw: WebSocketSession,
    private val spec: WsSpec,
    private val release: () -> Unit,
) {
    private val messages = Channel<WsMessage>(Channel.UNLIMITED)
    val incoming: ReceiveChannel<WsMessage> get() = messages
    private val closed = CompletableDeferred<WsClose?>()
    val closeReason: Deferred<WsClose?> get() = closed
    private val lastPongNanos = AtomicLong(System.nanoTime())

    init {
        raw.launch { readLoop() }
        raw.launch { pingLoop() }
        closed.invokeOnCompletion { raw.launch { delay(1_000); release() } }
    }

    private suspend fun readLoop() {
        val buffer = ByteArrayOutputStream()
        var textMessage = true
        try {
            for (frame in raw.incoming) {
                when (frame) {
                    is Frame.Ping -> raw.outgoing.send(Frame.Pong(frame.data))
                    is Frame.Pong -> lastPongNanos.set(System.nanoTime())
                    is Frame.Close -> {
                        val reason = frame.readReason() ?: CloseReason(1005.toShort(), "")
                        runCatching { raw.outgoing.send(Frame.Close(CloseReason(reason.code, ""))) }
                        closed.complete(WsClose(reason.code, reason.message, byPeer = true))
                        break
                    }
                    is Frame.Text, is Frame.Binary -> {
                        if (buffer.size() == 0) textMessage = frame is Frame.Text
                        if (buffer.size().toLong() + frame.data.size > spec.maxMessageBytes) {
                            localClose(CloseReason.Codes.TOO_BIG.code, "message too big")
                            break
                        }
                        buffer.write(frame.data)
                        if (frame.fin) {
                            val bytes = buffer.toByteArray()
                            buffer.reset()
                            messages.send(if (textMessage) WsMessage.Text(bytes.decodeToString()) else WsMessage.Binary(bytes))
                        }
                    }
                    else -> Unit
                }
            }
            // Ktor closes `incoming` with FrameTooBigException after sending Close(1009) itself.
        } catch (_: FrameTooBigException) {
            closed.complete(WsClose(CloseReason.Codes.TOO_BIG.code, "frame too big", byPeer = false))
        } catch (_: Exception) {
            closed.complete(null)
        } finally {
            messages.close()
            closed.complete(null)
        }
    }

    private suspend fun pingLoop() {
        while (raw.isActive && !closed.isCompleted) {
            delay(spec.pingIntervalMs)
            val sentAt = System.nanoTime()
            runCatching { raw.outgoing.send(Frame.Ping(ByteArray(0))) }.onFailure { return }
            raw.launch {
                delay(spec.pongTimeoutMs)
                if (lastPongNanos.get() < sentAt && !closed.isCompleted) localClose(CloseReason.Codes.GOING_AWAY.code, "pong timeout")
            }
        }
    }

    private suspend fun localClose(code: Short, reason: String) {
        runCatching { raw.outgoing.send(Frame.Close(CloseReason(code, reason))) }
        closed.complete(WsClose(code, reason, byPeer = false))
    }

    suspend fun sendText(text: String) = raw.outgoing.send(Frame.Text(text))
    suspend fun sendBinary(bytes: ByteArray) = raw.outgoing.send(Frame.Binary(true, bytes))

    /** Sends Close(code) and waits up to 2 s for the peer's Close. */
    suspend fun close(code: Short = 1000) {
        if (!closed.isCompleted) {
            runCatching { raw.outgoing.send(Frame.Close(CloseReason(code, ""))) }
            withTimeoutOrNull(2_000) { closed.await() } ?: closed.complete(WsClose(code, "", byPeer = false))
        }
    }
}
