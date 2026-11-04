package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.protocol.codex.CodexWireException
import io.github.eslamasabry.oc.protocol.codex.CodexSocket
import io.github.eslamasabry.oc.protocol.codex.CodexTransport
import io.github.eslamasabry.oc.protocol.codex.validCodexEndpoint
import java.net.Proxy
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.buffer

/**
 * B-AGENT-062/067: a redirect-free HTTP/1.1 upgrade with no compression, followed by a bounded RFC
 * 6455 reader. OkHttp's standard WebSocket is intentionally not used: it buffers an unbounded
 * message before delivering it.
 */
internal suspend fun openCodexFrames(
    endpoint: String,
    token: String,
    dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
): CodexSocket =
    withContext(dispatchers.io) {
        fun invalid(): Nothing = throw CodexWireException(Failure.Refused(RefusalReason.InvalidAddress))
        if (
            !validCodexEndpoint(endpoint) ||
                token.isEmpty() ||
                token.length > 8192 ||
                token.any { it.code <= 32 || it.code == 127 }
        )
            invalid()
        val uri = URI(endpoint)
        val http =
            endpoint
                .replaceFirst("wss://", "https://")
                .replaceFirst("ws://", "http://")
                .toHttpUrlOrNull() ?: invalid()
        if (uri.host?.removeSurrounding("[", "]")?.lowercase() != http.host.lowercase()) invalid()
        val client =
            OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .protocols(listOf(Protocol.HTTP_1_1))
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(8, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        val random = SecureRandom()
        val key = ByteArray(16).also(random::nextBytes).toByteString().base64()
        val call =
            client.newCall(
                Request.Builder()
                    .url(http)
                    .header("Connection", "Upgrade")
                    .header("Upgrade", "websocket")
                    .header("Sec-WebSocket-Version", "13")
                    .header("Sec-WebSocket-Key", key)
                    .header("Authorization", "Bearer $token")
                    .build()
            )
        val response =
            try {
                suspendCancellableCoroutine<Response> { waiting ->
                    waiting.invokeOnCancellation { call.cancel() }
                    call.enqueue(
                        object : Callback {
                            override fun onFailure(call: Call, error: java.io.IOException) {
                                if (waiting.isActive)
                                    waiting.resumeWithException(
                                        CodexWireException(Failure.ComputerDidNotAnswer())
                                    )
                            }

                            override fun onResponse(call: Call, response: Response) {
                                if (waiting.isActive) waiting.resume(response)
                                else {
                                    response.socket?.cancel()
                                    response.close()
                                }
                            }
                        }
                    )
                }
            } catch (error: Exception) {
                call.cancel()
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
                throw error
            }
        try {
            if (response.code == 401) throw CodexWireException(Failure.SignIn(SignInWhy.TokenRejected))
            if (response.code == 403) throw CodexWireException(Failure.SignIn(SignInWhy.Forbidden))
            val expected =
                (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encodeUtf8().sha1().base64()
            val connection = response.headers("Connection").flatMap { it.split(',') }
            if (
                response.code != 101 ||
                    !response.header("Upgrade").equals("websocket", true) ||
                    connection.none { it.trim().equals("Upgrade", true) } ||
                    response.headers("Sec-WebSocket-Accept").singleOrNull() != expected ||
                    response.headers("Sec-WebSocket-Extensions").isNotEmpty() ||
                    response.headers("Sec-WebSocket-Protocol").isNotEmpty()
            )
                throw CodexWireException(Failure.UnreadableReply())
            val socket = response.socket ?: throw CodexWireException(Failure.UnreadableReply())
            BoundedCodexFrames(socket, client, dispatchers, appScope, random)
        } catch (error: Exception) {
            response.socket?.cancel()
            response.close()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            throw error
        }
    }

private class BoundedCodexFrames(
    private val socket: okio.Socket,
    private val client: OkHttpClient,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
    private val random: SecureRandom,
) : CodexSocket {
    private val source = socket.source.buffer()
    private val sink = socket.sink.buffer()
    // B-AGENT-067: at most one complete undelivered message plus the current fragment assembly (8
    // MiB total).
    private val frames = Channel<String>(1)
    override val incoming: ReceiveChannel<String> = frames
    private val writes = Mutex()
    private val closed = AtomicBoolean(false)
    private val pendingPing = AtomicReference<ByteArray?>(null)
    private val reader: Job
    private val pinger: Job

    init {
        reader =
            appScope.launch(dispatchers.io) {
                try {
                    readFrames()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: CodexWireException) {
                    frames.close(error)
                } catch (_: Exception) {
                    frames.close(CodexWireException(Failure.ComputerDidNotAnswer()))
                } finally {
                    finish()
                }
            }
        pinger =
            appScope.launch(dispatchers.io) {
                try {
                    delay(20_000)
                    while (isActive && !closed.get()) {
                        val ping = ByteArray(8).also(random::nextBytes)
                        pendingPing.set(ping)
                        writeFrame(9, ping)
                        delay(15_000)
                        if (pendingPing.get() != null) {
                            finish()
                            break
                        }
                        delay(5_000)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    finish()
                }
            }
        reader.invokeOnCompletion { pinger.cancel() }
    }

    private fun unreadable(): Nothing = throw CodexWireException(Failure.UnreadableReply())

    private suspend fun readFrames() {
        val message = Buffer()
        var fragmented = false
        while (!closed.get()) {
            val first = source.readByte().toInt() and 255
            val second = source.readByte().toInt() and 255
            val final = first and 128 != 0
            val opcode = first and 15
            if (first and 112 != 0 || second and 128 != 0) unreadable()
            val encodedLength = second and 127
            val length =
                when (encodedLength) {
                    126 ->
                        (source.readShort().toInt() and 65535).toLong().also {
                            if (it < 126) unreadable()
                        }
                    127 -> source.readLong().also { if (it < 65536) unreadable() }
                    else -> encodedLength.toLong()
                }
            if (opcode >= 8) {
                if (!final || length > 125 || opcode !in setOf(8, 9, 10)) unreadable()
                val payload = source.readByteArray(length)
                when (opcode) {
                    8 -> {
                        if (payload.size == 1) unreadable()
                        if (payload.size >= 2) {
                            val code =
                                ((payload[0].toInt() and 255) shl 8) or (payload[1].toInt() and 255)
                            if (
                                code < 1000 ||
                                    code >= 5000 ||
                                    code in setOf(1004, 1005, 1006, 1015) ||
                                    code in 1016..2999
                            )
                                unreadable()
                            decode(payload.copyOfRange(2, payload.size))
                        }
                        writeFrame(8, payload)
                        return
                    }
                    9 -> writeFrame(10, payload)
                    10 ->
                        pendingPing.get()?.let {
                            if (it.contentEquals(payload)) pendingPing.compareAndSet(it, null)
                        }
                }
                continue
            }
            if (opcode == 1) {
                if (fragmented) unreadable()
                fragmented = !final
            } else if (opcode == 0) {
                if (!fragmented) unreadable()
                if (final) fragmented = false
            } else unreadable()
            // Validate every frame length and the running fragment total BEFORE any payload
            // allocation/read.
            if (
                length < 0 ||
                    length > CodexTransport.MAX_FRAME_BYTES ||
                    message.size + length > CodexTransport.MAX_FRAME_BYTES
            )
                unreadable()
            var remaining = length
            while (remaining > 0) {
                val read = source.read(message, minOf(remaining, 8192))
                if (read < 0) unreadable()
                remaining -= read
            }
            if (final) frames.send(decode(message.readByteArray()))
        }
    }

    private fun decode(bytes: ByteArray): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    override suspend fun send(text: String): Unit =
        withContext(dispatchers.io) {
            val payload = text.toByteArray(StandardCharsets.UTF_8)
            if (payload.size > CodexTransport.MAX_FRAME_BYTES) unreadable()
            writeFrame(1, payload)
        }

    private suspend fun writeFrame(opcode: Int, payload: ByteArray): Unit = writes.withLock {
        if (closed.get()) throw CodexWireException(Failure.ComputerDidNotAnswer())
        val mask = ByteArray(4).also(random::nextBytes)
        sink.writeByte(128 or opcode)
        when {
            payload.size < 126 -> sink.writeByte(128 or payload.size)
            payload.size <= 65535 -> {
                sink.writeByte(128 or 126)
                sink.writeShort(payload.size)
            }
            else -> {
                sink.writeByte(128 or 127)
                sink.writeLong(payload.size.toLong())
            }
        }
        sink.write(mask)
        // A small mask block avoids a second full-message allocation on a 4 MiB send.
        val block = ByteArray(minOf(payload.size, 8192))
        var position = 0
        while (position < payload.size) {
            val count = minOf(block.size, payload.size - position)
            for (index in 0 until count) block[index] =
                (payload[position + index].toInt() xor mask[(position + index) and 3].toInt())
                    .toByte()
            sink.write(block, 0, count)
            position += count
        }
        sink.flush()
    }

    private fun finish() {
        if (!closed.compareAndSet(false, true)) return
        socket.cancel()
        frames.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    override suspend fun close(): Unit =
        withContext(dispatchers.io) {
            finish()
            reader.cancel()
            pinger.cancel()
        }
}
