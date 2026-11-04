package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
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

