package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.core.net.CallKind
import io.github.eslamasabry.oc.core.net.StatusMapping
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.TransportFailures
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import io.ktor.websocket.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/**
 * Current Flutter PTY calls, through the same dialect, origin, TLS pin and cleartext trust as every
 * HTTP call.
 */
private fun ptyData(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value

private fun JsonObject.ptyText(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun ptyRow(value: JsonElement): TerminalProcess? {
    val row = ptyData(value) as? JsonObject ?: return null
    val id = row.ptyText("id").takeIf { it.isNotBlank() } ?: return null
    return TerminalProcess(
        id,
        row.ptyText("title").ifBlank { id },
        row.ptyText("command"),
        (row["args"] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .toImmutableList(),
        row.ptyText("cwd"),
        row.ptyText("status") == "running",
        (row["pid"] as? JsonPrimitive)?.intOrNull ?: 0,
        (row["exitCode"] as? JsonPrimitive)?.intOrNull,
    )
}

internal suspend fun Oc2Gateway.ptyList(): Outcome<ImmutableList<TerminalProcess>> {
    val result = json("/pty", query = loc(location.value))
    if (result is Outcome.Failed) return result
    val rows =
        ptyData((result as Outcome.Ok).value) as? JsonArray
            ?: return Failure.UnreadableReply().failed()
    if (rows.size > 1000) return Failure.UnreadableReply().failed()
    val parsed = rows.map { ptyRow(it) ?: return Failure.UnreadableReply().failed() }
    return parsed.filterNot { it.title == "OpenCode Mobile usage check" }.toImmutableList().ok()
}

internal suspend fun Oc2Gateway.ptyCreate(
    title: String?,
    command: String? = null,
    args: ImmutableList<String>? = null,
): Outcome<TerminalProcess> {
    val reply =
        json(
            "/pty",
            HttpMethod.Post,
            buildJsonObject {
                title?.let { put("title", it) }
                command?.let { put("command", it) }
                args?.let { put("args", JsonArray(it.map(::JsonPrimitive))) }
            },
            loc(location.value),
        )
    return when (reply) {
        is Outcome.Failed -> reply
        is Outcome.Ok -> ptyRow(reply.value)?.ok() ?: Failure.UnreadableReply().failed()
    }
}

internal suspend fun Oc2Gateway.ptyRename(id: String, title: String): Outcome<Unit> =
    json("/pty/${id.encodeURLPathPart()}", HttpMethod.Put, buildJsonObject { put("title", title) })
        .map { Unit }

internal suspend fun Oc2Gateway.ptyResize(id: String, rows: Int, cols: Int): Outcome<Unit> {
    if (rows !in 1..1000 || cols !in 1..2000) return Failure.Rejected(null).failed()
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
        .map { Unit }
}

internal suspend fun Oc2Gateway.ptyRemove(id: String): Outcome<Unit> =
    when (val result = json("/pty/${id.encodeURLPathPart()}", HttpMethod.Delete)) {
        is Outcome.Ok -> Unit.ok()
        is Outcome.Failed -> if (result.failure is Failure.NotFound) Unit.ok() else result
    }

internal suspend fun Oc2Gateway.ptyConnect(id: String, cursor: Long?): Outcome<TerminalChannel> {
    val ticket = terminalHandshake(id, cursor)
    if (ticket is Outcome.Failed) return ticket
    val handshake = (ticket as Outcome.Ok).value
    return attempt({ error ->
        if (error is ResponseException) StatusMapping.failureFor(error.response.status.value)
        else TransportFailures.of(CallKind.Read, TransportError.Other, error)
    }) {
        val socket =
            net.http.webSocketSession {
                url(handshake.url)
                handshake.headers.forEach { (key, value) -> header(key, value) }
            }
        socket.maxFrameSize = 1_048_576
        Oc2PtyChannel(socket, cursor ?: 0)
    }
}

/**
 * NUL binary metadata never enters the transcript; cursor counts delivered UTF-8 bytes, not
 * characters.
 */
private class Oc2PtyChannel(private val socket: WebSocketSession, initialCursor: Long) :
    TerminalChannel {
    private var offset = initialCursor
    override val cursor: Long
        get() = offset

    override val output: Flow<String> = flow {
        for (frame in socket.incoming) {
            val bytes =
                when (frame) {
                    is Frame.Text,
                    is Frame.Binary -> frame.data
                    else -> continue
                }
            if (bytes.size > 1_048_576) {
                socket.close(CloseReason(CloseReason.Codes.TOO_BIG, ""))
                return@flow
            }
            if (frame is Frame.Binary && bytes.firstOrNull() == 0.toByte()) {
                val meta =
                    try {
                        Json.parseToJsonElement(bytes.copyOfRange(1, bytes.size).decodeToString())
                            as? JsonObject
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                (meta?.get("cursor") as? JsonPrimitive)
                    ?.longOrNull
                    ?.takeIf { it >= 0 }
                    ?.let { offset = it }
                continue
            }
            val text = bytes.decodeToString()
            if (text.isEmpty()) continue
            offset =
                if (Long.MAX_VALUE - offset < bytes.size) Long.MAX_VALUE else offset + bytes.size
            emit(text)
        }
    }

    override suspend fun write(value: String) {
        require(value.encodeToByteArray().size <= 1_048_576)
        socket.send(Frame.Text(value))
    }

    override suspend fun close() {
        socket.close(CloseReason(CloseReason.Codes.NORMAL, ""))
    }
}
