package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.protocol.paseo.*
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.boolOrNull
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.longOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.paseoList
import io.github.eslamasabry.oc.protocol.paseo.paseoObject
import io.github.eslamasabry.oc.protocol.paseo.paseoString
import io.github.eslamasabry.oc.protocol.paseo.str
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

/*
 * Terminals the daemon runs in the open folder (port of lib/paseo/gateway/terminals.dart, B-AGENT-159): list, open,
 * type, resize, rename and close (`list_terminals_request`, `create_terminal_request`, `subscribe_terminal_request`,
 * `terminal_input`, `kill_terminal_request`, `terminal.rename.request`). Output arrives as binary frames
 * `[opcode, slot, …]` on the same socket.
 */

private fun PaseoGateway.terminalProcess(terminal: JsonObject): TerminalProcess {
    val id = paseoString(terminal["id"], max = 256)
    val title = terminal.str("title")?.trim()?.takeIf { it.isNotEmpty() }
    val name = terminal.str("name")?.takeIf { it.isNotEmpty() }
    val directory = terminal.str("cwd")?.takeIf { it.isNotEmpty() } ?: scopeDir
    return TerminalProcess(
        id = id,
        title = title ?: name ?: id,
        // The row says where it runs: the daemon names no command.
        command = folderName(directory),
        arguments = persistentListOf(),
        directory = directory,
        // The daemon lists a terminal only while its shell runs.
        running = true,
        pid = 0,
        exitCode = null,
    )
}

internal suspend fun PaseoGateway.readTerminals(): ImmutableList<TerminalProcess> {
    val payload = workspaceRequest("list_terminals_request", jsonOf("cwd" to scopeDir))
    return paseoList(payload["terminals"], max = 1000).map { terminalProcess(paseoObject(it)) }.toImmutableList()
}

internal suspend fun PaseoGateway.openTerminal(title: String?): TerminalProcess {
    val name = title?.trim().orEmpty()
    val payload = workspaceRequest(
        "create_terminal_request",
        jsonOf("cwd" to scopeDir, "name" to name.ifEmpty { null }).withoutNulls(),
        mutation = true,
    )
    return terminalProcess(paseoObject(payload["terminal"]))
}

internal suspend fun PaseoGateway.retitleTerminal(id: String, title: String) {
    val payload = workspaceRequest(
        "terminal.rename.request",
        jsonOf("terminalId" to paseoString(id, max = 256), "title" to title.trim()),
        mutation = true,
    )
    if (payload["success"].boolOrNull() != true) paseoFail(PaseoFailureKind.Unavailable)
}

internal suspend fun PaseoGateway.sizeTerminal(id: String, rows: Int, cols: Int) {
    if (rows <= 0 || cols <= 0) return
    transport.send(
        "terminal_input",
        jsonOf("terminalId" to paseoString(id, max = 256), "message" to mapOf("type" to "resize", "rows" to rows, "cols" to cols)),
    )
}

/** A shell that already ended is gone, which is what was asked for; one still listed failed to close. */
internal suspend fun PaseoGateway.killTerminal(id: String) {
    val payload = workspaceRequest("kill_terminal_request", jsonOf("terminalId" to paseoString(id, max = 256)), mutation = true)
    if (payload["success"].boolOrNull() == true) return
    if (readTerminals().any { it.id == id }) paseoFail(PaseoFailureKind.Unavailable)
}

/**
 * Opens the live output of terminal [id]. The daemon first sends what the screen shows now (its restore frame), so
 * every connect starts from a reset screen and the current state.
 */
internal suspend fun PaseoGateway.subscribeTerminal(id: String): TerminalChannel {
    val terminalId = paseoString(id, max = 256)
    val scope = scopeDir
    val epoch = locationEpoch
    // Listen before asking: the restore frame can follow the answer at once.
    val channel = PaseoTerminalChannel(this, terminalId)
    try {
        val payload = try {
            transport.request(
                "subscribe_terminal_request",
                jsonOf("terminalId" to terminalId, "restore" to mapOf("mode" to "visible-snapshot", "scrollbackLines" to 500)),
                mutation = true,
            )
        } catch (e: PaseoException) {
            throw PaseoException(e.kind)
        }
        checkLocation(scope, epoch)
        val slot = payload["slot"].longOrNull()
        if (slot == null || slot < 0 || slot > 255) paseoFail(PaseoFailureKind.InvalidResponse)
        channel.attach(transport.epoch, slot.toInt())
        return channel
    } catch (e: Throwable) {
        channel.close()
        throw e
    }
}

/**
 * One terminal's output as text. Frames of other slots and connections are ignored; a lost connection or an exited
 * shell ends the stream. Listener callbacks run on the transport's lane, the gateway's lane.
 */
internal class PaseoTerminalChannel(private val gateway: PaseoGateway, private val terminalId: String) : TerminalChannel {
    private val out = Channel<String>(Channel.UNLIMITED)
    private val decoder = Utf8StreamDecoder()
    private var epoch: Int? = null
    private var slot: Int? = null
    private var closed = false
    private val early = ArrayList<PaseoBinaryFrame>()

    private val removeListener = gateway.transport.addListener(
        object : PaseoTransportListener {
            override fun onBinary(frame: PaseoBinaryFrame) = onFrame(frame)

            override fun onPush(push: PaseoPush) {
                if (push.type == "terminal_stream_exit" && push.payload.str("terminalId") == terminalId) finish()
            }

            override fun onDisconnect(epoch: Int) = finish()
        },
    )

    override val output: Flow<String> = out.receiveAsFlow()
    override val cursor: Long? get() = null

    fun attach(epoch: Int, slot: Int) {
        this.epoch = epoch
        this.slot = slot
        val held = early.toList()
        early.clear()
        held.forEach(::onFrame)
    }

    private fun onFrame(frame: PaseoBinaryFrame) {
        if (closed) return
        val mine = slot
        if (mine == null) {
            if (early.size < 64) early += frame
            return
        }
        if (frame.slot != mine || frame.epoch != epoch) return
        when (frame.opcode) {
            RESTORE -> {
                // The restore replaces the screen: reset it, then replay.
                decoder.reset()
                out.trySend("\u001bc")
                emit(decoder.decode(frame.payload))
            }
            OUTPUT -> emit(decoder.decode(frame.payload))
        }
    }

    private fun emit(text: String) {
        if (text.isNotEmpty()) out.trySend(text)
    }

    /** Ends the stream without telling the daemon (it ended it, or the link is gone). */
    private fun finish() {
        if (closed) return
        closed = true
        removeListener()
        out.close()
    }

    override suspend fun write(value: String) {
        if (closed || value.isEmpty()) return
        gateway.transport.send(
            "terminal_input",
            jsonOf("terminalId" to terminalId, "message" to mapOf("type" to "input", "data" to value)),
            expectedEpoch = epoch,
        )
    }

    override suspend fun close() {
        withContext(gateway.lane) {
            if (closed) return@withContext
            val wasAttached = slot != null
            finish()
            val transport = gateway.transport
            if (wasAttached && transport.connected && transport.epoch == epoch) {
                try {
                    transport.send("unsubscribe_terminal_request", jsonOf("terminalId" to terminalId), expectedEpoch = epoch)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: PaseoException) {
                    // The connection is going away; the daemon drops the stream with it.
                }
            }
        }
    }

    private companion object {
        const val OUTPUT = 0x01
        const val RESTORE = 0x05
    }
}

/** Decodes UTF-8 across frames: a character split between two frames arrives whole. */
internal class Utf8StreamDecoder {
    private var pending = ByteArray(0)

    fun reset() {
        pending = ByteArray(0)
    }

    fun decode(bytes: ByteArray): String {
        val all = if (pending.isEmpty()) bytes else pending + bytes
        // Find where the last (possibly incomplete) sequence starts.
        var cut = all.size
        var back = 0
        var i = all.size - 1
        while (i >= 0 && back < 4) {
            val b = all[i].toInt() and 0xFF
            if (b and 0xC0 != 0x80) {
                val need = when {
                    b and 0x80 == 0 -> 1
                    b and 0xE0 == 0xC0 -> 2
                    b and 0xF0 == 0xE0 -> 3
                    b and 0xF8 == 0xF0 -> 4
                    else -> 1
                }
                if (all.size - i < need) cut = i
                break
            }
            i--
            back++
        }
        pending = all.copyOfRange(cut, all.size)
        return all.copyOfRange(0, cut).decodeToString()
    }
}

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
        val channel = PaseoTerminalStream(g, terminal, scope, epoch)
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

private class PaseoTerminalStream(
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
                    this@PaseoTerminalStream.frame(frame)

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
            // Only frames that could belong to this terminal are held until the subscribe ack;
            // another terminal's traffic or an old connection must not exhaust the buffer.
            if (frame.opcode != 0x01 && frame.opcode != 0x05) return
            if (frame.epoch != g.transport.epoch) return
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
                g.terminalChannels.remove(this@PaseoTerminalStream)
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
