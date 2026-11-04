package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.net.ws.WsMessage
import io.github.eslamasabry.oc.core.net.ws.WsSession
import io.github.eslamasabry.oc.core.net.ws.WsSpec
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import io.github.eslamasabry.oc.protocol.oc2.wire.long
import io.github.eslamasabry.oc.protocol.oc2.wire.str
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.encodeURLPathPart
import kotlin.concurrent.atomics.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

/** One-use ticket, then one same-origin upgrade. Neither mutations nor input are ever retried. */
internal suspend fun Oc2Gateway.connectTerminalAt(
    id: String,
    cursor: Long?,
    at: ServerLocation,
): Outcome<TerminalChannel> {
    if (!Oc2Terminals.validId(id) || (cursor != null && cursor < 0))
        return Failure.Rejected(400).failed()
    val scope = terminalScope ?: return Failure.Unavailable(FeatureId("terminal")).failed()
    val dispatchers =
        terminalDispatchers ?: return Failure.Unavailable(FeatureId("terminal")).failed()
    val query = mapOf("location[directory]" to at.directory, "location[workspace]" to at.workspace)
    val ticketReply =
        json(
            "/pty/${id.encodeURLPathPart()}/connect-token",
            HttpMethod.Post,
            query = query,
            headers = mapOf("x-opencode-ticket" to "1"),
        )
    if (ticketReply is Outcome.Failed) return ticketReply
    val envelope =
        (ticketReply as Outcome.Ok).value as? JsonObject
            ?: return Failure.UnreadableReply().failed()
    val ticket =
        ((envelope["data"] as? JsonObject) ?: envelope).str("ticket")?.takeIf {
            it.isNotEmpty() && it.length <= 4096 && it.all { c -> c.code in 33..126 }
        } ?: return Failure.UnreadableReply().failed()
    terminalRegisterSecret(ticket)
    val url =
        URLBuilder(terminalOrigin.toString() + "/api/pty/${id.encodeURLPathPart()}/connect")
            .apply {
                protocol = if (terminalOrigin.scheme == "https") URLProtocol.WSS else URLProtocol.WS
                parameters.append("ticket", ticket)
                cursor?.let { parameters.append("cursor", it.toString()) }
                query.forEach { (key, value) -> value?.let { parameters.append(key, it) } }
            }
            .buildString()
    val opened = terminalNet.connectSocket(WsSpec(url, terminalAuthHeaders), scope, dispatchers)
    if (opened is Outcome.Failed) return opened
    return Oc2TerminalChannel((opened as Outcome.Ok).value, cursor ?: 0, ::terminalRedact).ok()
}

private class Oc2TerminalChannel(
    private val socket: WsSession,
    initialCursor: Long,
    private val redact: (String) -> String,
) : TerminalChannel {
    private val position = AtomicLong(initialCursor)
    override val cursor: Long
        get() = position.load()

    override val output: Flow<String> = flow {
        socket.incoming.collect { message ->
            val bytes =
                when (message) {
                    is WsMessage.Text -> message.text.encodeToByteArray()
                    is WsMessage.Binary -> message.bytes
                }
            if (message is WsMessage.Binary && bytes.firstOrNull() == 0.toByte()) {
                val control =
                    Oc2Wire.parse(bytes.copyOfRange(1, bytes.size).decodeToString()) as? JsonObject
                control?.long("cursor")?.takeIf { it >= 0 }?.let(position::store)
            } else if (bytes.isNotEmpty()) {
                val previous = position.load()
                position.store(
                    if (previous > Long.MAX_VALUE - bytes.size) Long.MAX_VALUE
                    else previous + bytes.size
                )
                emit(redact(bytes.decodeToString()))
            }
        }
    }

    override suspend fun write(value: String) {
        if (socket.sendText(value) is Outcome.Failed) throw TerminalWriteFailure()
    }

    override suspend fun close() {
        socket.close()
    }

    override fun toString(): String = "TerminalChannel(<redacted>)"
}

/** The legacy channel API returns Unit for writes; callers show their own safe failure copy. */
private class TerminalWriteFailure : Exception("terminal connection closed")
