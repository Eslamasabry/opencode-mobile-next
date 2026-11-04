package io.github.eslamasabry.oc.core.net.okhttp

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.net.CallKind
import io.github.eslamasabry.oc.core.net.StatusMapping
import io.github.eslamasabry.oc.core.net.TerminalSocketSpec
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.TransportFailures
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.buffer

/**
 * Plan 04 N4-D5: own capped RFC6455 framer; OkHttp's WebSocket always offers deflate and buffers
 * without a cap.
 */
internal suspend fun openTerminalSocket(
    client: OkHttpClient,
    spec: TerminalSocketSpec,
    owner: CoroutineScope,
    dispatchers: AppDispatchers,
    classify: (Throwable) -> TransportError,
): Outcome<TerminalChannel> {
    val random = SecureRandom()
    val key = Base64.getEncoder().encodeToString(ByteArray(16).also(random::nextBytes))
    val upgrade =
        StaleConnectionRetry.forUpgrade(client)
            .newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            // A status follow-up (503 Retry-After: 0, 421) would send the one-use ticket twice.
            .addNetworkInterceptor(
                Interceptor { chain ->
                    val attempt = chain.request().tag(TicketAttempt::class.java)
                    if (attempt != null && !attempt.sent.compareAndSet(false, true))
                        throw ResendRefusedException()
                    chain.proceed(chain.request())
                }
            )
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(5_000, TimeUnit.MILLISECONDS)
            .callTimeout(15_000, TimeUnit.MILLISECONDS)
            .build()
    val request =
        try {
            Request.Builder()
                .url(spec.url)
                .header("Connection", "Upgrade")
                .header("Upgrade", "websocket")
                .header("Sec-WebSocket-Version", "13")
                .header("Sec-WebSocket-Key", key)
                .tag(TicketAttempt::class.java, TicketAttempt())
                .apply { spec.headers.forEach { (name, value) -> header(name, value) } }
                .build()
        } catch (_: IllegalArgumentException) {
            return Outcome.Failed(Failure.UnreadableReply())
        }
    val call = upgrade.newCall(request)
    val response =
        try {
            withTimeoutOrNull(15_000) { call.awaitUpgrade() }
                ?: run {
                    call.cancel()
                    return Outcome.Failed(Failure.TimedOut())
                }
        } catch (cancelled: CancellationException) {
            call.cancel()
            throw cancelled
        } catch (error: IOException) {
            return Outcome.Failed(TransportFailures.of(CallKind.Read, classify(error), error))
        }
    if (response.code != 101) {
        val failure =
            StatusMapping.failureFor(response.code, spec.headers.containsKey("Authorization"))
        response.close()
        return Outcome.Failed(failure)
    }
    val expected =
        Base64.getEncoder()
            .encodeToString(
                MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray())
            )
    val connection = response.headers("Connection").flatMap { it.split(',') }
    if (
        !response.header("Upgrade").equals("websocket", true) ||
            connection.none { it.trim().equals("upgrade", true) } ||
            response.header("Sec-WebSocket-Accept") != expected ||
            response.header("Sec-WebSocket-Extensions") != null ||
            response.header("Sec-WebSocket-Protocol") != null
    ) {
        response.close()
        return Outcome.Failed(Failure.UnreadableReply())
    }
    val socket = response.socket
    if (socket == null) {
        response.close()
        return Outcome.Failed(Failure.UnreadableReply())
    }
    val channel =
        OkHttpTerminalChannel(
            socket.source.buffer().inputStream(),
            socket.sink.buffer().outputStream(),
            spec.initialCursor,
            owner,
            dispatchers,
            random,
        ) {
            socket.cancel()
            response.close()
        }
    return Outcome.Ok(channel)
}

private class TicketAttempt {
    val sent = AtomicBoolean(false)
}

private suspend fun Call.awaitUpgrade(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, _, _ -> response.close() }
            }
        }
    )
}

/**
 * Only one maximum-size message can wait for its consumer; the reader holds at most one additional
 * message.
 */
private class OkHttpTerminalChannel(
    private val input: InputStream,
    private val writer: OutputStream,
    initialCursor: Long,
    owner: CoroutineScope,
    private val dispatchers: AppDispatchers,
    private val random: SecureRandom,
    private val releaseSocket: () -> Unit,
) : TerminalChannel {
    private val scope =
        CoroutineScope(
            owner.coroutineContext + SupervisorJob(owner.coroutineContext[Job]) + dispatchers.io
        )
    private val messages = Channel<String>(1)
    private val position = AtomicLong(initialCursor.coerceAtLeast(0))
    private val released = AtomicBoolean(false)
    private val closing = AtomicBoolean(false)
    private val writeLane = Mutex()
    private val peerClosed = CompletableDeferred<Unit>()
    @Volatile private var pong = CompletableDeferred<Unit>()
    override val output: Flow<String> = messages.receiveAsFlow()
    override val cursor: Long
        get() = position.get()

    init {
        // A blocking socket read must be cancelled at the start of graph cancellation, before its
        // reader can join.
        scope.launch {
            try {
                awaitCancellation()
            } finally {
                release()
            }
        }
        scope.launch { readLoop() }
        scope.launch {
            while (!closing.get() && !released.get()) {
                delay(20_000)
                if (closing.get() || released.get()) break
                pong = CompletableDeferred()
                try {
                    sendFrame(9, byteArrayOf())
                    if (withTimeoutOrNull(15_000) { pong.await() } == null) release()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    release()
                }
            }
        }
    }

    override suspend fun write(value: String) {
        if (closing.get() || released.get()) throw IOException("terminal connection closed")
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_MESSAGE) throw IOException("terminal input is too large")
        withContext(dispatchers.io) { sendFrame(1, bytes) }
    }

    override suspend fun close() {
        if (closing.compareAndSet(false, true) && !released.get()) {
            val deadline = scope.launch {
                delay(5_000)
                release()
            }
            try {
                withTimeoutOrNull(5_000) {
                    withContext(dispatchers.io) { sendFrame(8, byteArrayOf(3, -24)) }
                    peerClosed.await()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                /* The peer already closed. */
            } finally {
                deadline.cancel()
                release()
            }
        }
    }

    private suspend fun readLoop() {
        val buffer = ByteArrayOutputStream()
        var fragmented = false
        var messageKind = 0
        try {
            while (!released.get()) {
                val first = input.read()
                if (first == -1) break
                val second = readByte()
                val final = first and 128 != 0
                val opcode = first and 15
                // No extensions were negotiated, and servers MUST NOT mask their frames.
                if (first and 112 != 0 || second and 128 != 0)
                    throw IOException("invalid terminal frame")
                var length = (second and 127).toLong()
                if (length == 126L) length = ((readByte() shl 8) or readByte()).toLong()
                else if (length == 127L) {
                    length = 0
                    repeat(8) {
                        val next = readByte()
                        if (length > MAX_MESSAGE.toLong())
                            throw IOException("terminal frame is too large")
                        length = (length shl 8) or next.toLong()
                    }
                }
                if (
                    length > MAX_MESSAGE || length < 0 || (opcode >= 8 && (!final || length > 125))
                ) {
                    sendFrame(8, byteArrayOf(3, -15)) // 1009: never allocate an over-cap frame.
                    throw IOException("terminal frame is too large")
                }
                if (opcode !in setOf(0, 1, 2, 8, 9, 10)) throw IOException("invalid terminal frame")
                if (opcode < 8 && buffer.size().toLong() + length > MAX_MESSAGE) {
                    sendFrame(8, byteArrayOf(3, -15))
                    throw IOException("terminal message is too large")
                }
                val data = ByteArray(length.toInt())
                readFully(data)
                when (opcode) {
                    8 -> {
                        if (data.size == 1) throw IOException("invalid terminal close")
                        if (closing.compareAndSet(false, true)) sendFrame(8, data)
                        peerClosed.complete(Unit)
                        break
                    }
                    9 -> sendFrame(10, data)
                    10 -> pong.complete(Unit)
                    else -> {
                        if (opcode == 0 && !fragmented || opcode != 0 && fragmented)
                            throw IOException("invalid terminal continuation")
                        if (opcode != 0) messageKind = opcode
                        if (buffer.size().toLong() + data.size > MAX_MESSAGE)
                            throw IOException("terminal message is too large")
                        buffer.write(data)
                        fragmented = !final
                        if (final) {
                            val bytes = buffer.toByteArray()
                            buffer.reset()
                            if (messageKind == 2 && bytes.firstOrNull() == 0.toByte()) {
                                readCursor(bytes)
                            } else if (bytes.isNotEmpty()) {
                                position.addAndGet(bytes.size.toLong())
                                messages.send(bytes.toString(Charsets.UTF_8))
                            }
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            messages.close(IOException("terminal connection closed"))
        } finally {
            peerClosed.complete(Unit)
            release()
        }
    }

    private fun readCursor(bytes: ByteArray) {
        try {
            val metadata =
                Json.parseToJsonElement(bytes.copyOfRange(1, bytes.size).toString(Charsets.UTF_8))
                    as? JsonObject
            (metadata?.get("cursor") as? JsonPrimitive)
                ?.takeIf { !it.isString }
                ?.longOrNull
                ?.takeIf { it >= 0 }
                ?.let(position::set)
        } catch (_: IllegalArgumentException) {
            /* Invalid metadata is never terminal output. */
        }
    }

    private fun readByte(): Int =
        input.read().takeIf { it >= 0 } ?: throw IOException("terminal connection closed")

    private fun readFully(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) throw IOException("terminal connection closed")
            offset += count
        }
    }

    private suspend fun sendFrame(opcode: Int, bytes: ByteArray) = writeLane.withLock {
        if (released.get()) throw IOException("terminal connection closed")
        writer.write(128 or opcode)
        when {
            bytes.size < 126 -> writer.write(128 or bytes.size)
            bytes.size <= 65_535 -> {
                writer.write(128 or 126)
                writer.write(bytes.size ushr 8)
                writer.write(bytes.size)
            }
            else -> {
                writer.write(128 or 127)
                repeat(4) { writer.write(0) }
                for (shift in 24 downTo 0 step 8) writer.write(bytes.size ushr shift)
            }
        }
        val mask = ByteArray(4).also(random::nextBytes)
        writer.write(mask)
        // Never allocate a second frame-sized masked copy of the person's input.
        val chunk = ByteArray(minOf(bytes.size, 8_192))
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(chunk.size, bytes.size - offset)
            repeat(count) {
                chunk[it] =
                    (bytes[offset + it].toInt() xor mask[(offset + it) and 3].toInt()).toByte()
            }
            writer.write(chunk, 0, count)
            offset += count
        }
        writer.flush()
    }

    private fun release() {
        if (released.compareAndSet(false, true)) {
            releaseSocket()
            messages.close()
            scope.cancel()
        }
    }

    private companion object {
        const val MAX_MESSAGE = 16 * 1024 * 1024
    }
}
