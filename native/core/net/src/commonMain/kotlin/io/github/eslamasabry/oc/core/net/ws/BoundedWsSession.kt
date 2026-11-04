package io.github.eslamasabry.oc.core.net.ws

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.common.supervisedContext
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameTooBigException
import io.ktor.websocket.FrameType
import io.ktor.websocket.WebSocketSession
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class RawWsSocket(val session: WebSocketSession, val cancel: () -> Unit)

/**
 * Creates a separately owned IO scope; owner cancellation closes the socket before blocked IO is
 * joined.
 */
internal suspend fun openBoundedWs(
    spec: WsSpec,
    dispatchers: AppDispatchers,
    openRaw: suspend (CoroutineScope) -> Outcome<RawWsSocket>,
): Outcome<WsSession> {
    val owner = spec.owner.coroutineContext[Job]
    if (
        owner == null ||
            !owner.isActive ||
            spec.maxMessageBytes !in 1..16 * 1024 * 1024 ||
            spec.handshakeTimeoutMs !in 1..60_000 ||
            spec.pingIntervalMs < 1 ||
            spec.pongTimeoutMs < 1
    ) {
        return Failure.Network(NetworkWhy.Offline).failed()
    }
    val io =
        CoroutineScope(
            supervisedContext(null, dispatchers.io, "bounded-websocket") +
                CoroutineExceptionHandler { _, _ -> }
        )
    val raw =
        try {
            openRaw(io)
        } catch (e: CancellationException) {
            io.cancel()
            throw e
        } catch (_: Exception) {
            io.cancel()
            return Failure.Network(NetworkWhy.Reset).failed()
        }
    if (raw is Outcome.Failed) {
        io.cancel()
        return raw
    }
    val socket = (raw as Outcome.Ok).value
    if (!owner.isActive) {
        socket.cancel()
        io.cancel()
        return Failure.Network(NetworkWhy.Offline).failed()
    }
    return BoundedWsSession(spec, socket, io, owner, dispatchers).ok()
}

internal class BoundedWsSession(
    private val spec: WsSpec,
    private val raw: RawWsSocket,
    private val io: CoroutineScope,
    owner: Job,
    dispatchers: AppDispatchers,
) : WsSession {
    private val stopped = AtomicBoolean(false)

    private class Delivered(val message: WsMessage, val size: Int)

    private val messages = Channel<Delivered>(1)
    private val budget = WsByteBudget(spec.maxMessageBytes * 2)
    private val closed = CompletableDeferred<WsClose>()
    override val incoming: Flow<WsMessage> = flow {
        for (entry in messages) {
            try {
                emit(entry.message)
            } finally {
                budget.release(entry.size)
            }
        }
    }
    override val closeReason: CompletableDeferred<WsClose> = closed
    private val writing = Mutex()
    private var pendingPong: Pair<ByteArray, CompletableDeferred<Unit>>? = null
    private val ownerHandle = AtomicReference<DisposableHandle?>(null)
    private val lane = dispatchers.serial("websocket-state")

    init {
        ownerHandle.store(
            owner.invokeOnCompletion {
                shutdown(WsClose(failure = Failure.Network(NetworkWhy.Reset)))
            }
        )
        if (stopped.load()) ownerHandle.exchange(null)?.dispose()
        io.launch(lane) { read() }
        io.launch(lane) {
            var sequence = 0
            while (!stopped.load()) {
                delay(spec.pingIntervalMs)
                val bytes = (++sequence).toString().encodeToByteArray()
                val ready = CompletableDeferred<Unit>()
                pendingPong = bytes to ready
                try {
                    writing.withLock { raw.session.send(Frame.Ping(bytes)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    shutdown(WsClose(failure = Failure.Network(NetworkWhy.Reset)))
                    return@launch
                }
                if (
                    withTimeoutOrNull(spec.pongTimeoutMs) {
                        ready.await()
                        true
                    } != true
                ) {
                    finish(1001, Failure.TimedOut())
                    return@launch
                }
                pendingPong = null
            }
        }
    }

    private suspend fun read() {
        var kind: FrameType? = null
        val parts = ArrayList<ByteArray>()
        var bytes = 0
        try {
            for (frame in raw.session.incoming) {
                if (frame.rsv1 || frame.rsv2 || frame.rsv3) {
                    finish(1002, Failure.UnreadableReply())
                    return
                }
                when (frame.frameType) {
                    FrameType.PING -> writing.withLock { raw.session.send(Frame.Pong(frame.data)) }
                    FrameType.PONG ->
                        pendingPong?.let { (expected, done) ->
                            if (frame.data.contentEquals(expected)) done.complete(Unit)
                        }
                    FrameType.CLOSE -> {
                        val code =
                            if (frame.data.size >= 2)
                                ((frame.data[0].toInt() and 255) shl 8) or
                                    (frame.data[1].toInt() and 255)
                            else 1000
                        finish(code, null)
                        return
                    }
                    FrameType.TEXT,
                    FrameType.BINARY -> {
                        if (kind != null && kind != frame.frameType) {
                            finish(1002, Failure.UnreadableReply())
                            return
                        }
                        kind = frame.frameType
                        if (parts.size >= 4096 || frame.data.size > spec.maxMessageBytes - bytes) {
                            finish(1009, Failure.UnreadableReply())
                            return
                        }
                        bytes += frame.data.size
                        parts += frame.data
                        if (frame.fin) {
                            val joined = ByteArray(bytes)
                            var offset = 0
                            for (part in parts) {
                                part.copyInto(joined, offset)
                                offset += part.size
                            }
                            val message =
                                if (kind == FrameType.TEXT) {
                                    val text =
                                        try {
                                            joined.decodeToString(throwOnInvalidSequence = true)
                                        } catch (_: Exception) {
                                            finish(1007, Failure.UnreadableReply())
                                            return
                                        }
                                    WsMessage.Text(text)
                                } else WsMessage.Binary(joined)
                            parts.clear()
                            bytes = 0
                            kind = null
                            budget.acquire(joined.size)
                            try {
                                messages.send(Delivered(message, joined.size))
                            } catch (e: Throwable) {
                                budget.release(joined.size)
                                throw e
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: FrameTooBigException) {
            shutdown(WsClose(1009, Failure.UnreadableReply()))
        } catch (_: Exception) {
            shutdown(WsClose(failure = Failure.UnreadableReply()))
        } finally {
            shutdown(WsClose(failure = Failure.Network(NetworkWhy.Reset)))
        }
    }

    override suspend fun sendText(text: String): Outcome<Unit> {
        if (text.length > spec.maxMessageBytes) return Failure.UnreadableReply().failed()
        val bytes = text.encodeToByteArray()
        if (bytes.size > spec.maxMessageBytes) return Failure.UnreadableReply().failed()
        return send(Frame.Text(true, bytes))
    }

    override suspend fun sendBinary(bytes: ByteArray): Outcome<Unit> {
        if (bytes.size > spec.maxMessageBytes) return Failure.UnreadableReply().failed()
        return send(Frame.Binary(true, bytes.copyOf()))
    }

    private suspend fun send(frame: Frame): Outcome<Unit> = writing.withLock {
        if (stopped.load()) return@withLock Failure.Network(NetworkWhy.Reset).failed()
        try {
            raw.session.send(frame)
            raw.session.flush()
            Unit.ok()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            shutdown(WsClose(failure = Failure.Network(NetworkWhy.Reset)))
            Failure.Network(NetworkWhy.Reset).failed()
        }
    }

    override suspend fun close(code: Short) {
        finish(code.toInt(), null)
    }

    private suspend fun finish(code: Int, failure: Failure?) {
        if (stopped.load()) return
        withTimeoutOrNull(200) {
            try {
                writing.withLock {
                    raw.session.send(Frame.Close(CloseReason(code.toShort(), "")))
                    raw.session.flush()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Unit
            }
        }
        shutdown(WsClose(code, failure))
    }

    private fun shutdown(reason: WsClose) {
        if (!stopped.compareAndSet(false, true)) return
        ownerHandle.exchange(null)?.dispose()
        closed.complete(reason)
        messages.close()
        raw.cancel()
        io.cancel()
    }
}
