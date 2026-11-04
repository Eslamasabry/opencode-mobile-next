package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.encodeURLPathPart
import io.ktor.http.takeFrom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.*
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
internal suspend fun Oc2Gateway.terminalHandshake(
    id: String,
    cursor: Long?,
): Outcome<Oc2TerminalHandshake> {
    if (terminalClosed) return Failure.Refused(RefusalReason.StaleScope).failed()
    if (id.isBlank() || id.length > 256 || id.any { it.code < 32 || it.code == 127 })
        return Failure.UnreadableReply().failed()
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
            // Read at most the bound plus one byte, so an oversized response is refused before
            // buffering it whole.
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
        (data?.get("ticket") as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotEmpty() && it.length <= 4096 && it.all { char -> char.code in 33..126 } }
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

private fun terminalOf(value: JsonElement): TerminalProcess? {
    val item = value as? JsonObject ?: return null
    fun text(key: String): String = (item[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val id = text("id").takeIf { it.isNotEmpty() } ?: return null
    return TerminalProcess(
        id,
        text("title"),
        text("command"),
        ((item["args"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?: emptyList())
            .toImmutableList(),
        text("cwd"),
        text("status") == "running",
        (item["pid"] as? JsonPrimitive)?.intOrNull ?: 0,
        (item["exitCode"] as? JsonPrimitive)?.intOrNull,
    )
}

private fun terminalData(value: JsonElement): JsonElement =
    (value as? JsonObject)?.get("data") ?: value

internal suspend fun Oc2Gateway.terminalsRead(): Outcome<ImmutableList<TerminalProcess>> =
    json("/pty", query = loc(location.value)).map { value ->
        ((terminalData(value) as? JsonArray)?.mapNotNull(::terminalOf) ?: emptyList())
            .filter { it.title != "OpenCode Mobile usage check" }
            .toImmutableList()
    }

internal suspend fun Oc2Gateway.terminalCreate(title: String?): Outcome<TerminalProcess> =
    when (
        val reply =
            json(
                "/pty",
                HttpMethod.Post,
                buildJsonObject { title?.let { put("title", it) } },
                loc(location.value),
            )
    ) {
        is Outcome.Failed -> reply
        is Outcome.Ok ->
            terminalOf(terminalData(reply.value))?.ok() ?: Failure.UnreadableReply().failed()
    }

internal suspend fun Oc2Gateway.terminalRename(id: String, title: String): Outcome<Unit> =
    json("/pty/${id.encodeURLPathPart()}", HttpMethod.Put, buildJsonObject { put("title", title) })
        .map {}

internal suspend fun Oc2Gateway.terminalResize(id: String, rows: Int, cols: Int): Outcome<Unit> {
    if (rows < 1 || cols < 1) return Failure.Rejected(null).failed()
    return json(
            "/pty/${id.encodeURLPathPart()}",
            HttpMethod.Put,
            buildJsonObject {
                putJsonObject("size") {
                    put("rows", rows)
                    put("cols", cols)
                }
            },
        )
        .map {}
}

internal suspend fun Oc2Gateway.terminalRemove(id: String): Outcome<Unit> =
    when (val reply = json("/pty/${id.encodeURLPathPart()}", HttpMethod.Delete)) {
        is Outcome.Ok -> Unit.ok()
        is Outcome.Failed -> if (reply.failure is Failure.NotFound) Unit.ok() else reply
    }
