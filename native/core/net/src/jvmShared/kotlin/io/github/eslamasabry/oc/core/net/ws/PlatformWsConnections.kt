package io.github.eslamasabry.oc.core.net.ws

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.net.CallKind
import io.github.eslamasabry.oc.core.net.StatusMapping
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.TransportFailures
import io.github.eslamasabry.oc.core.net.okhttp.JvmWsUpgrade
import io.github.eslamasabry.oc.core.net.okhttp.OkHttpEngineFactory
import io.github.eslamasabry.oc.core.net.okhttp.TrustEnforcement
import io.github.eslamasabry.oc.core.net.okhttp.hardenedBaseClient
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.websocket.RawWebSocket
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.buffer

/** Raw HTTP/1 upgrade; the inherited trust gate runs before the bearer header can leave. */
public class PlatformWsConnections(private val dispatchers: AppDispatchers) : SocketConnections {
    override suspend fun open(spec: SocketSpec): Outcome<SocketSession> =
        openBoundedWs(spec, dispatchers) { io ->
            val client =
                TrustEnforcement.install(hardenedBaseClient(spec.transport), spec.transport)
            fun release() {
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
            suspendCancellableCoroutine { continuation ->
                val handle =
                    JvmWsUpgrade.open(
                        client,
                        spec.url,
                        spec.headers,
                        spec.subprotocol,
                        spec.handshakeTimeoutMs,
                    ) { reply ->
                        when (reply) {
                            is JvmWsUpgrade.Reply.Failed -> {
                                release()
                                val failure =
                                    when {
                                        reply.invalid -> Failure.UnreadableReply()
                                        reply.status != null ->
                                            StatusMapping.failureFor(reply.status!!)
                                        else ->
                                            TransportFailures.of(
                                                CallKind.Read,
                                                if (reply.timeout) TransportError.Timeout
                                                else
                                                    reply.cause?.let {
                                                        OkHttpEngineFactory().classify(it)
                                                    } ?: TransportError.Other,
                                                reply.cause,
                                            )
                                    }
                                if (continuation.isActive) continuation.resume(failure.failed())
                            }
                            is JvmWsUpgrade.Reply.Opened -> {
                                val socket = reply.socket
                                if (!continuation.isActive) {
                                    reply.cancel()
                                    release()
                                } else {
                                    try {
                                        val input =
                                            socket.source
                                                .buffer()
                                                .inputStream()
                                                .toByteReadChannel(io.coroutineContext)
                                        val output = ByteChannel(autoFlush = true)
                                        io.launch {
                                            output.copyTo(socket.sink.buffer().outputStream())
                                        }
                                        val raw =
                                            RawWebSocket(
                                                input,
                                                output,
                                                spec.maxMessageBytes.toLong(),
                                                true,
                                                io.coroutineContext,
                                                boundedWsChannels(),
                                            )
                                        val answer =
                                            RawWsSocket(raw) {
                                                    reply.cancel()
                                                    release()
                                                }
                                                .ok()
                                        continuation.resume(answer) { _, value, _ ->
                                            (value as? Outcome.Ok)?.value?.cancel()
                                        }
                                    } catch (_: Exception) {
                                        reply.cancel()
                                        release()
                                        if (continuation.isActive)
                                            continuation.resume(Failure.UnreadableReply().failed())
                                    }
                                }
                            }
                        }
                    }
                continuation.invokeOnCancellation {
                    handle.cancel()
                    release()
                }
            }
        }
}
