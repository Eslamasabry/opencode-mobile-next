package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.net.CallKind
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.TransportFailures
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.encodeURLPathPart
import io.ktor.http.takeFrom
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Opens the PTY websocket the handshake describes (platform OkHttp in the app). [headers] carries
 * the phone's Basic header so a bring-your-own-server proxy still applies revocation before
 * forwarding; a refusal is its Failure and no channel exists.
 */
public fun interface Oc2TerminalSocketOpener {
    public suspend fun open(url: String, headers: Map<String, String>): Outcome<TerminalChannel>
}

/**
 * The websocket request after the ticket step: the single-use ticket is the only credential in
 * [url].
 */
public data class Oc2TerminalHandshake(val url: String, val headers: Map<String, String>) {
    override fun toString(): String = "Oc2TerminalHandshake(headers=${headers.keys})"
}

/**
 * Flutter `connectTerminal` (B-PROTO-036): `POST /pty/{id}/connect-token` with `x-opencode-ticket:
 * 1` and the default Basic header, then
 * `ws(s)://…/api/pty/{id}/connect?ticket=…&cursor=…&location[directory]=…`. No password or
 * `auth_token` ever goes into a URL.
 */
internal suspend fun Oc2Gateway.terminalHandshake(id: String, cursor: Long?): Outcome<Oc2TerminalHandshake> {
    if (terminalClosed) return Failure.Refused(RefusalReason.StaleScope).failed()
    if (id.isBlank() || id.length > 256 || id.any { it.code < 32 || it.code == 127 }) return Failure.UnreadableReply().failed()
    if (cursor != null && cursor < 0) return Failure.UnreadableReply().failed()
    val here = location.value
    val directory = here.directory
    val path = "/pty/${id.encodeURLPathPart()}"
    val reply =
        net.exchange({
            method = HttpMethod.Post
            url(origin.toString() + "/api" + path + "/connect-token")
            directory?.let { url.parameters.append("location[directory]", it) }
            here.workspace?.let { url.parameters.append("location[workspace]", it) }
            auth?.let { header(HttpHeaders.Authorization, it) }
            header("x-opencode-ticket", "1")
            header(HttpHeaders.Accept, "application/json")
        }) { response ->
            // Read at most the bound plus one byte, so an oversized response is refused before buffering it whole.
            val channel = response.bodyAsChannel()
            val bytes = ByteArray(16_385)
            var length = 0
            while (length < bytes.size) {
                val read = channel.readAvailable(bytes, length, bytes.size - length)
                if (read < 0) break
                length += read
            }
            if (length > 16_384) null else bytes.decodeToString(endIndex = length)
        }
    val text =
        when (reply) {
            is Outcome.Failed -> return reply
            is Outcome.Ok -> reply.value ?: return Failure.UnreadableReply().failed()
        }
    if (terminalClosed || location.value != here) return Failure.Refused(RefusalReason.StaleScope).failed()
    val root = io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire.parse(text) as? JsonObject
    val data = (root?.get("data") as? JsonObject) ?: root
    val ticket =
        (data?.get("ticket") as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() && it.length <= 4096 && it.all { char -> char.code in 33..126 } }
            ?: return Failure.UnreadableReply().failed()
    val socket = URLBuilder().takeFrom(origin.toString() + "/api" + path + "/connect")
    socket.protocol = if (origin.scheme == "https") URLProtocol.WSS else URLProtocol.WS
    socket.parameters.append("ticket", ticket)
    cursor?.let { socket.parameters.append("cursor", it.toString()) }
    directory?.let { socket.parameters.append("location[directory]", it) }
    here.workspace?.let { socket.parameters.append("location[workspace]", it) }
    return Oc2TerminalHandshake(
            socket.buildString(),
            buildMap { auth?.let { put(HttpHeaders.Authorization, it) } },
        )
        .ok()
}

/**
 * Ticket, then socket. A refused socket (revoked after the ticket) is a Failure; nothing is
 * retried.
 */
internal suspend fun Oc2Gateway.connectTerminalWith(
    opener: Oc2TerminalSocketOpener,
    id: String,
    cursor: Long?,
): Outcome<TerminalChannel> {
    val here = location.value
    fun stale(): Boolean = terminalClosed || location.value != here
    if (stale()) return Failure.Refused(RefusalReason.StaleScope).failed()
    val handshake = when (val result = terminalHandshake(id, cursor)) {
        is Outcome.Failed -> return result
        is Outcome.Ok -> result.value
    }
    if (stale()) return Failure.Refused(RefusalReason.StaleScope).failed()
    val channel = when (val result = opener.open(handshake.url, handshake.headers)) {
        is Outcome.Failed -> return result
        is Outcome.Ok -> result.value
    }
    if (stale()) {
        channel.close()
        return Failure.Refused(RefusalReason.StaleScope).failed()
    }
    return channel.ok()
}


/** Opens on the connection's hardened client, retaining its exact origin, consent and certificate pin. */
internal fun oc2TerminalSockets(net: NetClient): Oc2TerminalSocketOpener =
    Oc2TerminalSocketOpener { address, headers ->
        attempt({ TransportFailures.of(CallKind.Read, TransportError.Other, it) }) {
            val session = net.http.webSocketSession {
                url(address)
                for ((name, value) in headers) header(name, value)
            }
            val cursor = Url(address).parameters["cursor"]?.toLongOrNull() ?: 0L
            Oc2PtyTerminalChannel(session, cursor)
        }
    }

/** Flutter PtyTerminalChannel: raw UTF-8 output with byte cursors and zero-prefixed control frames. */
private class Oc2PtyTerminalChannel(
    private val session: DefaultClientWebSocketSession,
    initialCursor: Long,
) : TerminalChannel {
    override var cursor: Long = initialCursor
        private set

    override val output: Flow<String> = flow {
        for (frame in session.incoming) {
            val bytes: ByteArray
            val text: String
            when (frame) {
                is Frame.Binary -> {
                    bytes = frame.data
                    if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
                        control(bytes)
                        continue
                    }
                    text = bytes.decodeToString()
                }
                is Frame.Text -> {
                    text = frame.readText()
                    bytes = text.encodeToByteArray()
                }
                else -> continue
            }
            if (text.isEmpty()) continue
            cursor = if (cursor > Long.MAX_VALUE - bytes.size) Long.MAX_VALUE else cursor + bytes.size
            emit(text)
        }
    }

    private fun control(bytes: ByteArray) {
        try {
            val metadata = Json.parseToJsonElement(bytes.copyOfRange(1, bytes.size).decodeToString()) as? JsonObject
            val value = metadata?.get("cursor") as? JsonPrimitive
            val next = value?.takeUnless { it.isString }?.longOrNull
            if (next != null && next >= 0) cursor = next
        } catch (_: IllegalArgumentException) {
            // Malformed metadata is never terminal text.
        }
    }

    override suspend fun write(value: String) {
        session.send(Frame.Text(value))
    }

    override suspend fun close() {
        session.close(CloseReason(CloseReason.Codes.NORMAL, ""))
    }
}
