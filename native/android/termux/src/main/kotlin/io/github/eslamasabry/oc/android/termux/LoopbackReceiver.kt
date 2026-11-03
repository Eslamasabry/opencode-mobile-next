package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The app side of one copy: a tiny HTTP/1.1 receiver on 127.0.0.1 that takes exactly one authenticated `PUT /archive`
 * chunk per [expect]. A request without the single fresh bearer and the single expected `x-offset` is answered 403 and
 * never touches the file or the pending receive, so another local app can neither write nor abort the copy. Each
 * accepted chunk is flushed and checkpointed before the 204, so a lost bridge resumes after the last flushed chunk.
 */
internal class LoopbackReceiver(
    private val token: String,
    private val archive: TermuxMigrationArchive,
    private val target: FileChannel,
    private val checkpoint: Path,
    initialOffset: Long,
) : Closeable {
    private companion object {
        const val MAX_HEAD = 16 * 1024
        const val READ_TIMEOUT_MS = 10_000
    }

    private val server = ServerSocket(0, 16, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    private val clients: MutableSet<Socket> = ConcurrentHashMap.newKeySet()
    private val accepting = AtomicBoolean(false)

    @Volatile private var pending: CompletableDeferred<Unit>? = null

    @Volatile var offset: Long = initialOffset
        private set

    val port: Int get() = server.localPort

    /** Opens the next receive; the returned value settles when that chunk was flushed (or failed). */
    fun expect(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { pending = it }

    /** Accepts connections until [close]; each one is handled on its own coroutine in [scope]. */
    fun serve(scope: CoroutineScope) {
        while (!server.isClosed) {
            val socket = try {
                server.accept()
            } catch (_: Exception) {
                return
            }
            clients.add(socket)
            scope.launch {
                try {
                    handle(socket)
                } finally {
                    clients.remove(socket)
                    closeQuietly(socket)
                }
            }
        }
    }

    fun stop(reason: TermuxMigrationException) {
        closeQuietly(server)
        pending?.completeExceptionally(reason)
    }

    override fun close() {
        closeQuietly(server)
        for (client in clients) closeQuietly(client)
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = READ_TIMEOUT_MS
            val input = BufferedInputStream(socket.getInputStream())
            val head = readHead(input)
            val lines = head?.split("\r\n")
            val request = lines?.firstOrNull()?.split(' ')
            val headers = lines?.drop(1)?.filter { it.isNotEmpty() }?.map { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) "" to line else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
            } ?: emptyList()
            fun values(name: String) = headers.filter { it.first == name }.map { it.second }
            val completion = pending
            val authorized = request != null && request.size == 3 && request[0] == "PUT" && request[1] == "/archive" &&
                completion != null && !completion.isCompleted &&
                values("authorization") == listOf("Bearer $token") &&
                values("x-offset") == listOf(offset.toString())
            if (!authorized || completion == null || !accepting.compareAndSet(false, true)) {
                respond(socket, "403 Forbidden")
                return
            }
            try {
                receive(socket, input, values("transfer-encoding"), values("content-length"), values("expect"))
                respond(socket, "204 No Content")
                completion.complete(Unit)
            } catch (_: Exception) {
                respond(socket, "400 Bad Request")
                completion.completeExceptionally(TermuxMigrationException(TermuxMigrationFailure.invalidArchive))
            } finally {
                accepting.set(false)
            }
        } catch (_: Exception) {
            // A peer may vanish or stall: never leak raw socket exceptions.
        }
    }

    private fun receive(socket: Socket, input: InputStream, encoding: List<String>, length: List<String>, expect: List<String>) {
        val expected = minOf(BridgeTermuxMigrationTransport.CHUNK_BYTES, archive.bytes - offset)
        if (expect.any { it.equals("100-continue", ignoreCase = true) }) {
            socket.getOutputStream().apply {
                write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
        }
        var bytes = 0L
        fun write(buffer: ByteArray, count: Int) {
            bytes += count
            if (bytes > expected) throw IllegalStateException()
            val wrapped = ByteBuffer.wrap(buffer, 0, count)
            while (wrapped.hasRemaining()) target.write(wrapped)
        }
        val buffer = ByteArray(65536)
        when {
            encoding.isNotEmpty() -> {
                if (encoding != listOf("chunked")) throw IllegalStateException()
                while (true) {
                    val size = readLine(input).substringBefore(';').trim().toLong(16)
                    if (size == 0L) {
                        while (readLine(input).isNotEmpty()) Unit
                        break
                    }
                    if (bytes + size > expected) throw IllegalStateException()
                    var remaining = size
                    while (remaining > 0) {
                        val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                        if (count < 0) throw IllegalStateException()
                        write(buffer, count)
                        remaining -= count
                    }
                    if (readLine(input).isNotEmpty()) throw IllegalStateException()
                }
            }
            length.isNotEmpty() -> {
                if (length.size != 1) throw IllegalStateException()
                val declared = length.single().toLong()
                if (declared != expected) throw IllegalStateException()
                var remaining = declared
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                    if (count < 0) throw IllegalStateException()
                    write(buffer, count)
                    remaining -= count
                }
            }
            else -> while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                write(buffer, count)
            }
        }
        if (bytes != expected) throw IllegalStateException()
        target.force(true)
        offset += bytes
        writeCheckpoint(checkpoint, archive, offset)
    }

    private fun readHead(input: InputStream): String? {
        val head = StringBuilder()
        while (head.length < MAX_HEAD) {
            val byte = input.read()
            if (byte < 0) return null
            head.append(byte.toChar())
            if (head.endsWith("\r\n\r\n")) return head.substring(0, head.length - 4)
        }
        return null
    }

    private fun readLine(input: InputStream): String {
        val line = StringBuilder()
        while (line.length < 1024) {
            val byte = input.read()
            if (byte < 0) throw IllegalStateException()
            if (byte == '\n'.code) return line.toString().removeSuffix("\r")
            line.append(byte.toChar())
        }
        throw IllegalStateException()
    }

    private fun respond(socket: Socket, status: String) {
        try {
            socket.getOutputStream().apply {
                write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
            socket.shutdownOutput()
        } catch (_: Exception) {
            // A peer may disappear on cancellation.
        }
    }

    private fun closeQuietly(closeable: Closeable) {
        try {
            closeable.close()
        } catch (_: Exception) {
            // Nothing to report.
        }
    }
}
