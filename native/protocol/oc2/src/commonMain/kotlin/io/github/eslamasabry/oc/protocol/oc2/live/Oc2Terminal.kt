package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.encodeURLPathPart
import io.ktor.http.takeFrom
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Opens the PTY websocket the handshake describes (platform OkHttp in the app). [headers] carries the phone's Basic
 * header so a bring-your-own-server proxy still applies revocation before forwarding; a refusal is its Failure and
 * no channel exists.
 */
public fun interface Oc2TerminalSocketOpener {
    public suspend fun open(url: String, headers: Map<String, String>): Outcome<TerminalChannel>
}

/** The websocket request after the ticket step: the single-use ticket is the only credential in [url]. */
public data class Oc2TerminalHandshake(val url: String, val headers: Map<String, String>) {
    override fun toString(): String = "Oc2TerminalHandshake(headers=${headers.keys})"
}

/**
 * Flutter `connectTerminal` (B-PROTO-036): `POST /pty/{id}/connect-token` with `x-opencode-ticket: 1` and the
 * default Basic header, then `ws(s)://…/api/pty/{id}/connect?ticket=…&cursor=…&location[directory]=…`. No password
 * or `auth_token` ever goes into a URL.
 */
internal suspend fun Oc2Gateway.terminalHandshake(id: String, cursor: Long?): Outcome<Oc2TerminalHandshake> {
    val directory = location.value.directory
    val path = "/pty/${id.encodeURLPathPart()}"
    val reply =
        net.exchange({
            method = HttpMethod.Post
            url(origin.toString() + "/api" + path + "/connect-token")
            directory?.let { url.parameters.append("location[directory]", it) }
            location.value.workspace?.let { url.parameters.append("location[workspace]", it) }
            auth?.let { header(HttpHeaders.Authorization, it) }
            header("x-opencode-ticket", "1")
            header(HttpHeaders.Accept, "application/json")
        }) { it.bodyAsText() }
    val text =
        when (reply) {
            is Outcome.Failed -> return reply
            is Outcome.Ok -> reply.value
        }
    val root = io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire.parse(text) as? JsonObject
    val data = (root?.get("data") as? JsonObject) ?: root
    val ticket =
        (data?.get("ticket") as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
            ?: return Failure.UnreadableReply().failed()
    val socket = URLBuilder().takeFrom(origin.toString() + "/api" + path + "/connect")
    socket.protocol = if (origin.scheme == "https") URLProtocol.WSS else URLProtocol.WS
    socket.parameters.append("ticket", ticket)
    cursor?.let { socket.parameters.append("cursor", it.toString()) }
    directory?.let { socket.parameters.append("location[directory]", it) }
    return Oc2TerminalHandshake(
            socket.buildString(),
            buildMap { auth?.let { put(HttpHeaders.Authorization, it) } },
        )
        .ok()
}

/** Ticket, then socket. A refused socket (revoked after the ticket) is a Failure; nothing is retried. */
internal suspend fun Oc2Gateway.connectTerminalWith(
    opener: Oc2TerminalSocketOpener,
    id: String,
    cursor: Long?,
): Outcome<TerminalChannel> =
    when (val handshake = terminalHandshake(id, cursor)) {
        is Outcome.Failed -> handshake
        is Outcome.Ok -> opener.open(handshake.value.url, handshake.value.headers)
    }
