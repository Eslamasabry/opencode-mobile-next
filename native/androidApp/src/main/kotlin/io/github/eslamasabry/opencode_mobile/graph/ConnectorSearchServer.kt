package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.catalog.ConnectorSearchEndpoint
import io.github.eslamasabry.oc.engine.integrations.catalog.ConnectorSearchServers
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

/**
 * A phone-only bridge. Neither its loopback address nor its bearer ever enters a remote host's
 * configuration.
 */
@ContributesBinding(AppScope::class)
@Inject
class AppConnectorSearchServers(
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
    private val clock: Clock,
) : ConnectorSearchServers {
    override suspend fun open(
        current: () -> Boolean,
        search: suspend (JsonObject) -> JsonObject,
    ): ConnectorSearchEndpoint =
        withContext(dispatchers.io) {
            val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
            val secret =
                ByteArray(32)
                    .also { SecureRandom().nextBytes(it) }
                    .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            redactor.registerSecret(secret)
            SearchEndpoint(server, secret, current, search, appScope, dispatchers, redactor, clock)
                .also { it.start() }
        }
}

private class SearchEndpoint(
    private val server: ServerSocket,
    override val bearer: String,
    private val sourceCurrent: () -> Boolean,
    private val search: suspend (JsonObject) -> JsonObject,
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
    private val clock: Clock,
) : ConnectorSearchEndpoint {
    override val endpoint: String = "http://127.0.0.1:${server.localPort}/find-connectors"
    private val closed = AtomicBoolean(false)
    private val active = AtomicInteger(0)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private var acceptJob: Job? = null

    private fun current(): Boolean =
        !closed.get() &&
            try {
                sourceCurrent()
            } catch (_: Exception) {
                false
            }

    override fun toString(): String = "ConnectorSearchEndpoint(private)"

    fun start() {
        acceptJob =
            appScope.launch(dispatchers.io) {
                while (!closed.get()) {
                    val socket =
                        try {
                            server.accept()
                        } catch (_: Exception) {
                            break
                        }
                    if (closed.get()) {
                        socket.close()
                        break
                    }
                    sockets += socket
                    // Bound open readers too, before allowing an untrusted client to send headers
                    // slowly.
                    if (sockets.size > 12) {
                        sockets -= socket
                        socket.close()
                        continue
                    }
                    launch {
                        try {
                            serve(socket)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {} finally {
                            sockets -= socket
                            socket.close()
                        }
                    }
                }
            }
    }

    private suspend fun serve(socket: Socket) {
        val deadline = clock.now().toEpochMilliseconds() + 5_000
        fun readBudget() {
            val remaining = deadline - clock.now().toEpochMilliseconds()
            if (remaining <= 0) throw java.net.SocketTimeoutException()
            socket.soTimeout = remaining.coerceAtMost(5_000).toInt()
        }
        var admitted = false
        val reply =
            try {
                withTimeout(5_000) {
                    val input = BufferedInputStream(socket.getInputStream())
                    val requestLine =
                        line(input, 2048, ::readBudget)?.split(' ')
                            ?: return@withTimeout Reply(400, "Invalid request")
                    val headers = HashMap<String, MutableList<String>>()
                    var bytes = 0
                    while (true) {
                        val row =
                            line(input, 4096, ::readBudget)
                                ?: return@withTimeout Reply(400, "Invalid request")
                        bytes += row.length + 2
                        if (bytes > 8192) return@withTimeout Reply(413, "Request too large")
                        if (row.isEmpty()) break
                        val separator = row.indexOf(':')
                        if (separator <= 0) return@withTimeout Reply(400, "Invalid request")
                        headers
                            .getOrPut(row.substring(0, separator).lowercase()) { mutableListOf() }
                            .add(row.substring(separator + 1).trim())
                    }
                    val authorizations = headers["authorization"]
                    if (
                        authorizations?.size != 1 ||
                            !equalSecret(authorizations.single(), "Bearer $bearer")
                    )
                        return@withTimeout Reply(401, "Unauthorized")
                    if (requestLine.size != 3 || requestLine[1] != "/find-connectors")
                        return@withTimeout Reply(404, "Not found")
                    if (requestLine[0] != "POST")
                        return@withTimeout Reply(405, "Method not allowed")
                    if (!current()) return@withTimeout Reply(403, "Source unavailable")
                    if (headers.containsKey("transfer-encoding"))
                        return@withTimeout Reply(400, "Invalid request")
                    val lengths = headers["content-length"]
                    if (lengths?.size != 1) return@withTimeout Reply(400, "Invalid request")
                    val length =
                        lengths.single().toIntOrNull()
                            ?: return@withTimeout Reply(400, "Invalid request")
                    if (length !in 0..2048) return@withTimeout Reply(413, "Request too large")
                    if (active.incrementAndGet() > 4) {
                        active.decrementAndGet()
                        return@withTimeout Reply(429, "Search busy")
                    }
                    admitted = true
                    val body = ByteArray(length)
                    var count = 0
                    while (count < length) {
                        readBudget()
                        val read = input.read(body, count, length - count)
                        if (read < 0) return@withTimeout Reply(400, "Invalid request")
                        count += read
                    }
                    val request =
                        try {
                            Json.parseToJsonElement(
                                body.decodeToString(throwOnInvalidSequence = true)
                            ) as? JsonObject
                        } catch (_: IllegalArgumentException) {
                            null
                        } catch (_: CharacterCodingException) {
                            null
                        } ?: return@withTimeout Reply(400, "Invalid request")
                    if (!current()) return@withTimeout Reply(403, "Source unavailable")
                    val result = search(request)
                    if (!current()) return@withTimeout Reply(403, "Source unavailable")
                    val encoded = result.toString().encodeToByteArray()
                    if (encoded.size > 16384) Reply(502, "Search response too large")
                    else Reply(200, body = encoded)
                }
            } catch (_: TimeoutCancellationException) {
                Reply(504, "Search timed out")
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                Reply(400, "Invalid request")
            } catch (_: java.net.SocketTimeoutException) {
                Reply(504, "Search timed out")
            } catch (_: Exception) {
                if (current()) Reply(500, "Search unavailable")
                else Reply(403, "Source unavailable")
            } finally {
                if (admitted) active.decrementAndGet()
            }
        if (closed.get()) return
        val safeReply =
            if (reply.status == 200 && !current()) Reply(403, "Source unavailable") else reply
        val body =
            safeReply.body
                ?: buildJsonObject { put("error", safeReply.error.orEmpty()) }
                    .toString()
                    .encodeToByteArray()
        val header =
            "HTTP/1.1 ${safeReply.status} Result\r\nContent-Type: application/json\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\nContent-Length: ${body.size}\r\n\r\n"
        socket.getOutputStream().apply {
            write(header.encodeToByteArray())
            write(body)
            flush()
        }
    }

    override suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        withContext(kotlinx.coroutines.NonCancellable + dispatchers.io) {
            server.close()
            sockets.forEach {
                try {
                    it.close()
                } catch (_: Exception) {}
            }
        }
        acceptJob?.cancel()
        redactor.forget(bearer)
    }

    private data class Reply(
        val status: Int,
        val error: String? = null,
        val body: ByteArray? = null,
    )
}

private fun line(input: BufferedInputStream, maximum: Int, readBudget: () -> Unit): String? {
    val bytes = ArrayList<Byte>()
    while (bytes.size <= maximum) {
        readBudget()
        val next = input.read()
        if (next < 0) return null
        if (next == 10) return bytes.toByteArray().decodeToString().removeSuffix("\r")
        if (next > 127 || next == 0) return null
        bytes += next.toByte()
    }
    return null
}

private fun equalSecret(actual: String, expected: String): Boolean {
    if (actual.length != expected.length) return false
    var different = 0
    for (i in expected.indices) different = different or (actual[i].code xor expected[i].code)
    return different == 0
}
