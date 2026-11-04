package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.protocol.paseo.*
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoBinaryFrame
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoPush
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransportListener
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Real daemon PTYs; the output channel owns only a subscription, never the daemon terminal's
 * lifetime.
 */
internal class PaseoTerminals(private val g: PaseoGateway, private val location: ServerLocation) {
    private val directory: String
        get() {
            val dir = location.directory ?: paseoFail(PaseoFailureKind.ScopeMismatch)
            if (
                !location.workspace.isNullOrEmpty() ||
                    !dir.startsWith('/') ||
                    dir.length > 4096 ||
                    dir.any { it.code < 32 || it.code == 127 }
            )
                paseoFail(PaseoFailureKind.ScopeMismatch)
            return dir
        }

    private suspend inline fun <T> call(crossinline block: suspend () -> T): Outcome<T> =
        withContext(g.lane) { paseoAttempt { block() } }

    private suspend fun request(
        type: String,
        body: JsonObject,
        mutation: Boolean = false,
    ): JsonObject {
        val scope = g.scopeDir
        val epoch = g.locationEpoch
        if (g.closed) paseoFail(PaseoFailureKind.Disconnected)
        val response =
            g.transport.request(
                type,
                body,
                mutation = mutation,
                beforeSend = { g.checkLocation(scope, epoch) },
            )
        g.checkLocation(scope, epoch)
        if (response["error"] != null && response["error"] != JsonNull)
            paseoFail(PaseoFailureKind.Unavailable)
        return response
    }

    private fun process(raw: JsonObject): TerminalProcess {
        val id = paseoString(raw["id"], max = 256)
        val cwd = raw.str("cwd")?.takeIf { it.isNotBlank() } ?: directory
        return TerminalProcess(
            id,
            raw.str("title")?.trim()?.takeIf { it.isNotEmpty() }
                ?: raw.str("name")?.takeIf { it.isNotEmpty() }
                ?: id,
            cwd.trimEnd('/').substringAfterLast('/'),
            persistentListOf(),
            cwd,
            true,
            0,
            null,
        )
    }

    private suspend fun listed(): ImmutableList<TerminalProcess> =
        paseoList(
                request("list_terminals_request", jsonOf("cwd" to directory))["terminals"],
                max = 1000,
            )
            .map { process(paseoObject(it)) }
            .toImmutableList()

    suspend fun list(): Outcome<ImmutableList<TerminalProcess>> = call { listed() }

    suspend fun create(title: String?): Outcome<TerminalProcess> = call {
        val name = title?.trim()?.takeIf { it.isNotEmpty() }?.let { paseoString(it, max = 256) }
        process(
            paseoObject(
                request(
                    "create_terminal_request",
                    jsonOf("cwd" to directory, "name" to name).withoutNulls(),
                    true,
                )["terminal"]
            )
        )
    }

    suspend fun rename(id: String, title: String): Outcome<Unit> = call {
        val response =
            request(
                "terminal.rename.request",
                jsonOf(
                    "terminalId" to paseoString(id, max = 256),
                    "title" to paseoString(title.trim(), max = 256),
                ),
                true,
            )
        if (response["success"].boolOrNull() != true) paseoFail(PaseoFailureKind.Unavailable)
    }

    suspend fun resize(id: String, rows: Int, cols: Int): Outcome<Unit> = call {
        if (rows <= 0 || cols <= 0) return@call
        val scope = g.scopeDir
        val epoch = g.locationEpoch
        g.transport.send(
            "terminal_input",
            jsonOf(
                "terminalId" to paseoString(id, max = 256),
                "message" to mapOf("type" to "resize", "rows" to rows, "cols" to cols),
            ),
        )
        g.checkLocation(scope, epoch)
    }

    suspend fun remove(id: String): Outcome<Unit> = call {
        val terminal = paseoString(id, max = 256)
        val response = request("kill_terminal_request", jsonOf("terminalId" to terminal), true)
        if (response["success"].boolOrNull() != true && listed().any { it.id == terminal })
            paseoFail(PaseoFailureKind.Unavailable)
    }

    suspend fun connect(id: String): Outcome<TerminalChannel> = call {
        val terminal = paseoString(id, max = 256)
        val scope = g.scopeDir
        val epoch = g.locationEpoch
        // Register before subscribing: the restore frame may immediately follow its
        // acknowledgement.
        val channel = PaseoTerminalChannel(g, terminal, scope, epoch)
        try {
            val response =
                request(
                    "subscribe_terminal_request",
                    jsonOf(
                        "terminalId" to terminal,
                        "restore" to mapOf("mode" to "visible-snapshot", "scrollbackLines" to 500),
                    ),
                    true,
                )
            val slot =
                response["slot"].longOrNull()?.takeIf { it in 0..255 }?.toInt()
                    ?: paseoFail(PaseoFailureKind.InvalidResponse)
            channel.attach(g.transport.epoch, slot)
            channel
        } catch (error: Exception) {
            channel.close()
            throw error
        }
    }
}

private class PaseoTerminalChannel(
    private val g: PaseoGateway,
    private val id: String,
    private val directory: String,
    private val locationEpoch: Int,
) : TerminalChannel {
    private val outputChannel = Channel<String>(64)
    override val output: Flow<String> = outputChannel.receiveAsFlow()
    override val cursor: Long? = null
    private val early = ArrayList<PaseoBinaryFrame>()
    private var earlyBytes = 0
    private var epoch: Int? = null
    private var slot: Int? = null
    private var closed = false
    private var decoder = TerminalUtf8()
    private val removeListener: () -> Unit =
        g.transport.addListener(
            object : PaseoTransportListener {
                override fun onBinary(frame: PaseoBinaryFrame) =
                    this@PaseoTerminalChannel.frame(frame)

                override fun onPush(push: PaseoPush) {
                    if (push.type == "terminal_stream_exit" && push.payload.str("terminalId") == id)
                        g.scope.launch { close() }
                }

                override fun onDisconnect(epoch: Int) {
                    g.scope.launch { close() }
                }
            }
        )

    init {
        g.terminalChannels += this
    }

    fun attach(epoch: Int, slot: Int) {
        if (closed) paseoFail(PaseoFailureKind.Disconnected)
        this.epoch = epoch
        this.slot = slot
        val pending = early.toList()
        early.clear()
        earlyBytes = 0
        pending.forEach(::frame)
    }

    private fun frame(frame: PaseoBinaryFrame) {
        if (closed) return
        if (slot == null) {
            if (early.size >= 64 || earlyBytes + frame.payload.size > 1024 * 1024) {
                fail()
                return
            }
            early += frame
            earlyBytes += frame.payload.size
            return
        }
        if (frame.slot != slot || frame.epoch != epoch) return
        if (frame.opcode != 0x01 && frame.opcode != 0x05) return
        if (frame.opcode == 0x05) {
            decoder = TerminalUtf8()
            offer("\u001bc")
        }
        offer(decoder.add(frame.payload))
    }

    private fun offer(text: String) {
        var start = 0
        while (!closed && start < text.length) {
            var end = (start + 32_768).coerceAtMost(text.length)
            if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate())
                end--
            if (outputChannel.trySend(text.substring(start, end)).isFailure) {
                fail()
                return
            }
            start = end
        }
    }

    private fun fail() {
        outputChannel.close(PaseoException(PaseoFailureKind.Unavailable))
        g.scope.launch { close() }
    }

    override suspend fun write(value: String) =
        withContext(g.lane) {
            if (closed || value.isEmpty()) return@withContext
            g.checkLocation(directory, locationEpoch)
            g.transport.send(
                "terminal_input",
                jsonOf("terminalId" to id, "message" to mapOf("type" to "input", "data" to value)),
                expectedEpoch = epoch,
            )
        }

    override suspend fun close(): Unit =
        withContext(NonCancellable + g.lane) {
            if (closed) return@withContext
            closed = true
            try {
                if (slot != null && g.transport.connected && g.transport.epoch == epoch)
                    g.transport.send(
                        "unsubscribe_terminal_request",
                        jsonOf("terminalId" to id),
                        expectedEpoch = epoch,
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                /* The daemon drops subscriptions with the connection. */
            } finally {
                g.terminalChannels.remove(this@PaseoTerminalChannel)
                removeListener()
                early.clear()
                outputChannel.close()
            }
        }
}

/**
 * Incremental UTF-8 decoder: a scalar split between socket frames is published only once it is
 * complete.
 */
private class TerminalUtf8 {
    private var pending = 0
    private var point = 0
    private var minimum = 0

    fun add(bytes: ByteArray): String = buildString {
        fun appendPoint(code: Int) {
            if (code <= 0xffff) append(code.toChar())
            else {
                val value = code - 0x10000
                append((0xd800 + (value shr 10)).toChar())
                append((0xdc00 + (value and 1023)).toChar())
            }
        }
        for (raw in bytes) {
            val value = raw.toInt() and 255
            if (pending > 0 && value !in 0x80..0xbf) {
                append('\uFFFD')
                pending = 0
            }
            if (pending > 0) {
                point = (point shl 6) or (value and 63)
                pending--
                if (pending == 0)
                    appendPoint(
                        if (point >= minimum && point <= 0x10ffff && point !in 0xd800..0xdfff) point
                        else 0xfffd
                    )
            } else
                when (value) {
                    in 0..0x7f -> append(value.toChar())
                    in 0xc2..0xdf -> {
                        point = value and 31
                        pending = 1
                        minimum = 0x80
                    }
                    in 0xe0..0xef -> {
                        point = value and 15
                        pending = 2
                        minimum = 0x800
                    }
                    in 0xf0..0xf4 -> {
                        point = value and 7
                        pending = 3
                        minimum = 0x10000
                    }
                    else -> append('\uFFFD')
                }
        }
    }
}
