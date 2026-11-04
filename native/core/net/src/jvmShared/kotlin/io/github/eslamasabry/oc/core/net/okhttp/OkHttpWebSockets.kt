package io.github.eslamasabry.oc.core.net.okhttp

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.net.CallKind
import io.github.eslamasabry.oc.core.net.StatusMapping
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.TransportFailures
import io.github.eslamasabry.oc.core.net.ws.WsClose
import io.github.eslamasabry.oc.core.net.ws.WsMessage
import io.github.eslamasabry.oc.core.net.ws.WsSession
import io.github.eslamasabry.oc.core.net.ws.WsSpec
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.buffer

/**
 * Manual, bounded RFC 6455 upgrade on the REST connection's exact pin/consent/trust client (plan 04
 * §5.2).
 */
internal object OkHttpWebSockets {
    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    private const val MAX_MESSAGE = 16L * 1024 * 1024
    private val random = SecureRandom()

    suspend fun connect(
        client: OkHttpClient,
        spec: WsSpec,
        owner: CoroutineScope,
        dispatchers: AppDispatchers,
        classify: (Throwable) -> TransportError,
    ): Outcome<WsSession> {
        if (
            spec.maxMessageBytes !in 1..MAX_MESSAGE ||
                spec.incomingByteBudget !in spec.maxMessageBytes..(2 * MAX_MESSAGE) ||
                spec.pingIntervalMs !in 1_000..60_000 ||
                spec.pongTimeoutMs !in 1_000..60_000 ||
                spec.handshakeTimeoutMs !in 1_000..60_000
        )
            return Outcome.Failed(Failure.UnreadableReply())
        owner.ensureActive()
        return attempt({ error ->
            if (error is HandshakeFailure) error.failure
            else TransportFailures.of(CallKind.Read, classify(error), error)
        }) {
            // The REST client's trust interceptor, pin manager, NO_PROXY and pool remain the same.
            // Only the upgrade
            // timeout/protocol changes; no built-in WebSocket (forced deflate and no incoming cap)
            // is involved.
            val attempts = AtomicInteger()
            val upgradeClient =
                StaleConnectionRetry.withoutRetries(client)
                    .newBuilder()
                    .protocols(listOf(Protocol.HTTP_1_1))
                    .retryOnConnectionFailure(false)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .callTimeout(spec.handshakeTimeoutMs, TimeUnit.MILLISECONDS)
                    .addNetworkInterceptor { chain ->
                        // OkHttp's status-driven follow-ups are refused before another ticket
                        // reaches the wire.
                        if (attempts.incrementAndGet() > 1) throw ResendRefusedException()
                        chain.proceed(chain.request())
                    }
                    .build()
            val key = Base64.getEncoder().encodeToString(ByteArray(16).also(random::nextBytes))
            val request =
                Request.Builder()
                    .url(spec.url)
                    .apply { spec.headers.forEach { (name, value) -> header(name, value) } }
                    .removeHeader("Sec-WebSocket-Extensions")
                    .header("Connection", "Upgrade")
                    .header("Upgrade", "websocket")
                    .header("Sec-WebSocket-Version", "13")
                    .header("Sec-WebSocket-Key", key)
                    .apply { spec.subprotocol?.let { header("Sec-WebSocket-Protocol", it) } }
                    .build()
            val call = upgradeClient.newCall(request)
            val cancelOpening = owner.coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
            val response =
                try {
                    call.await()
                } finally {
                    cancelOpening?.dispose()
                }
            try {
                validate(response, key, spec)
                owner.ensureActive()
                val socket = response.socket ?: throw HandshakeFailure(Failure.UnreadableReply())
                val released = AtomicBoolean()
                val lifetime = SupervisorJob(owner.coroutineContext[Job])
                val release = {
                    if (released.compareAndSet(false, true)) {
                        socket.cancel()
                        response.close()
                    }
                    Unit
                }
                lifetime.invokeOnCompletion { release() }
                val raw =
                    RawWebSocket(
                        socket.source.buffer().inputStream().toByteReadChannel(dispatchers.io),
                        socket.sink.buffer().outputStream().asByteWriteChannel(),
                        maxFrameSize = spec.maxMessageBytes,
                        masking = true,
                        coroutineContext =
                            owner.coroutineContext +
                                lifetime +
                                dispatchers.io +
                                CoroutineExceptionHandler { _, _ ->
                                    release()
                                    lifetime.cancel()
                                },
                        channelsConfig = boundedWsChannels(),
                    )
                BoundedWsSession(raw, spec, lifetime, release)
            } catch (error: Throwable) {
                response.socket?.cancel()
                response.close()
                throw error
            }
        }
    }

    private fun validate(response: Response, key: String, spec: WsSpec) {
        if (response.code != 101) {
            throw HandshakeFailure(
                StatusMapping.failureFor(response.code, spec.headers.containsKey("Authorization"))
            )
        }
        val connectionUpgrade =
            response
                .headers("Connection")
                .flatMap { it.split(',') }
                .any { it.trim().equals("upgrade", true) }
        if (!response.header("Upgrade").equals("websocket", true) || !connectionUpgrade) {
            throw HandshakeFailure(Failure.UnreadableReply())
        }
        val expected =
            Base64.getEncoder()
                .encodeToString(
                    MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray())
                )
        if (
            response.header("Sec-WebSocket-Accept") != expected ||
                response.header("Sec-WebSocket-Extensions") != null
        ) {
            throw HandshakeFailure(Failure.UnreadableReply())
        }
        val echoed = response.header("Sec-WebSocket-Protocol")
        if (echoed != null && echoed != spec.subprotocol)
            throw HandshakeFailure(Failure.UnreadableReply())
    }

    private class HandshakeFailure(val failure: Failure) :
        IOException("websocket handshake refused")

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, _, _ ->
                        response.socket?.cancel()
                        response.close()
                    }
                }

                override fun onFailure(call: Call, error: IOException) {
                    continuation.resumeWithException(error)
                }
            }
        )
    }
}

/** Bounded raw frames, bounded complete messages and a running fragmented-message cap. */
private class BoundedWsSession(
    private val raw: WebSocketSession,
    private val spec: WsSpec,
    private val lifetime: Job,
    private val release: () -> Unit,
) : WsSession {
    // Every message is <=maxMessageBytes: this queue's capacity conservatively enforces the byte
    // budget.
    private val messages =
        Channel<WsMessage>((spec.incomingByteBudget / spec.maxMessageBytes).toInt())
    private val stopped = CompletableDeferred<WsClose?>()
    override val incoming: Flow<WsMessage> = messages.receiveAsFlow()
    override val closeReason = stopped
    private val sentPing = AtomicLong()
    private val pong = AtomicLong()

    init {
        lifetime.invokeOnCompletion {
            stopped.complete(null)
            messages.close()
            release()
        }
        raw.launch { read() }
        raw.launch {
            while (isActive && !stopped.isCompleted) {
                delay(spec.pingIntervalMs)
                val token = sentPing.incrementAndGet()
                if (
                    attempt<Unit>({ Failure.Network(NetworkWhy.Reset) }) {
                        raw.outgoing.send(Frame.Ping(token.toString().encodeToByteArray()))
                    }
                        is Outcome.Failed
                )
                    break
                raw.launch {
                    delay(spec.pongTimeoutMs)
                    if (pong.get() < token && !stopped.isCompleted) close(1001)
                }
            }
        }
    }

    private suspend fun read() {
        val assembled = ByteArrayOutputStream()
        var textMessage = false
        try {
            for (frame in raw.incoming) {
                when (frame) {
                    is Frame.Ping -> raw.outgoing.send(Frame.Pong(frame.data))
                    is Frame.Pong ->
                        frame.data.decodeToString().toLongOrNull()?.let { token ->
                            if (token <= sentPing.get()) pong.accumulateAndGet(token, ::maxOf)
                        }
                    is Frame.Close -> {
                        val code = frame.readReason()?.code ?: 1000.toShort()
                        attempt<Unit>({ Failure.Network(NetworkWhy.Reset) }) {
                            raw.outgoing.send(Frame.Close(CloseReason(code, "")))
                        }
                        stopped.complete(WsClose(code))
                        break
                    }
                    is Frame.Text,
                    is Frame.Binary -> {
                        if (assembled.size() == 0) textMessage = frame is Frame.Text
                        if (assembled.size().toLong() + frame.data.size > spec.maxMessageBytes) {
                            stopped.complete(WsClose(1009, Failure.UnreadableReply()))
                            attempt<Unit>({ Failure.Network(NetworkWhy.Reset) }) {
                                raw.outgoing.send(Frame.Close(CloseReason(1009, "")))
                            }
                            break
                        }
                        assembled.write(frame.data)
                        if (frame.fin) {
                            val bytes = assembled.toByteArray()
                            assembled.reset()
                            messages.send(
                                if (textMessage)
                                    WsMessage.Text(
                                        bytes.decodeToString(throwOnInvalidSequence = true)
                                    )
                                else WsMessage.Binary(bytes)
                            )
                        }
                    }
                    else -> Unit
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: FrameTooBigException) {
            stopped.complete(WsClose(1009, Failure.UnreadableReply()))
        } catch (_: Exception) {
            stopped.complete(WsClose(null, Failure.Network(NetworkWhy.Reset)))
        } finally {
            stopped.complete(null)
            messages.close()
            release()
            lifetime.cancel()
        }
    }

    override suspend fun sendText(text: String): Outcome<Unit> {
        if (stopped.isCompleted) return Outcome.Failed(Failure.Network(NetworkWhy.Reset))
        if (text.encodeToByteArray().size > spec.maxMessageBytes)
            return Outcome.Failed(Failure.UnreadableReply())
        return attempt({ Failure.Network(NetworkWhy.Reset) }) {
            raw.outgoing.send(Frame.Text(text))
        }
    }

    override suspend fun close(code: Short) {
        if (!stopped.isCompleted) {
            withTimeoutOrNull(2_000) {
                attempt<Unit>({ Failure.Network(NetworkWhy.Reset) }) {
                    raw.outgoing.send(Frame.Close(CloseReason(code, "")))
                }
                stopped.await()
            }
            stopped.complete(WsClose(code))
        }
        release()
        lifetime.cancel()
    }
}
