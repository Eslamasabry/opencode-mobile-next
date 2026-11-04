package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/** One-shot reader PTYs use the same pinned, origin-checked client as ordinary operations. */
internal class Oc2Terminals(
    private val gateway: Oc2Gateway,
    private val net: NetClient,
    private val origin: ServerOrigin,
    private val authorization: String?,
    private val redactor: Redactor,
) {
    suspend fun list(): Outcome<ImmutableList<TerminalProcess>> =
        when (val result = gateway.json("/pty", query = query())) {
            is Outcome.Failed -> result
            is Outcome.Ok -> {
                val rows =
                    data(result.value) as? JsonArray ?: return Failure.UnreadableReply().failed()
                val parsed = rows.map { process(it) ?: return Failure.UnreadableReply().failed() }
                parsed.filter { it.title != USAGE_TITLE }.toImmutableList().ok()
            }
        }

    suspend fun create(
        command: String?,
        arguments: List<String>,
        title: String?,
    ): Outcome<TerminalProcess> {
        if (
            command != null &&
                (command.isBlank() || '\u0000' in command || arguments.any { '\u0000' in it })
        )
            return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val result =
            gateway.json(
                "/pty",
                HttpMethod.Post,
                buildJsonObject {
                    command?.let {
                        put("command", it)
                        put("args", JsonArray(arguments.map(::JsonPrimitive)))
                    }
                    title?.let { put("title", it) }
                },
                query(),
            )
        return when (result) {
            is Outcome.Failed -> result
            is Outcome.Ok -> process(data(result.value))?.ok() ?: Failure.UnreadableReply().failed()
        }
    }

    suspend fun remove(id: String): Outcome<Unit> =
        when (val result = gateway.json(path(id), HttpMethod.Delete)) {
            is Outcome.Ok -> Unit.ok()
            is Outcome.Failed -> if (result.failure is Failure.NotFound) Unit.ok() else result
        }

    suspend fun connect(id: String, cursor: Long?): Outcome<TerminalChannel> {
        val at = gateway.location.value
        val ticketReply =
            gateway.json(
                path(id) + "/connect-token",
                HttpMethod.Post,
                query = query(),
                headers = mapOf("x-opencode-ticket" to "1"),
            )
        if (ticketReply is Outcome.Failed) return ticketReply
        val ticket =
            (data((ticketReply as Outcome.Ok).value) as? JsonObject)?.get("ticket")
                as? JsonPrimitive
        val value =
            ticket?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
                ?: return Failure.UnreadableReply().failed()
        redactor.registerSecret(value)
        if (gateway.location.value != at) return Failure.Refused(RefusalReason.StaleScope).failed()
        return attempt({ Failure.ComputerDidNotAnswer() }) {
                val socket =
                    net.http.webSocketSession {
                        url(origin.toString() + "/api" + path(id) + "/connect")
                        url.protocol =
                            if (origin.scheme == "https") URLProtocol.WSS else URLProtocol.WS
                        url.parameters.append("ticket", value)
                        cursor?.let { url.parameters.append("cursor", it.toString()) }
                        at.directory?.let { url.parameters.append("location[directory]", it) }
                        authorization?.let { header(HttpHeaders.Authorization, it) }
                    }
                if (gateway.location.value != at) {
                    socket.close()
                    return@attempt Failure.Refused(RefusalReason.StaleScope).failed()
                }
                object : TerminalChannel {
                        private var nextCursor = cursor ?: 0L
                        override val cursor: Long
                            get() = nextCursor

                        override val output: Flow<String> = flow {
                            for (frame in socket.incoming) {
                                if (gateway.location.value != at) break
                                val bytes =
                                    when (frame) {
                                        is Frame.Text -> frame.data
                                        is Frame.Binary -> frame.data
                                        else -> continue
                                    }
                                if (frame is Frame.Binary && bytes.firstOrNull() == 0.toByte()) {
                                    val control =
                                        try {
                                            Json.parseToJsonElement(
                                                bytes.copyOfRange(1, bytes.size).decodeToString()
                                            ) as? JsonObject
                                        } catch (_: IllegalArgumentException) {
                                            null
                                        }
                                    (control?.get("cursor") as? JsonPrimitive)
                                        ?.longOrNull
                                        ?.takeIf { it >= 0 }
                                        ?.let { nextCursor = it }
                                } else if (bytes.isNotEmpty()) {
                                    nextCursor += bytes.size
                                    emit(bytes.decodeToString())
                                }
                            }
                        }

                        override suspend fun write(value: String) {
                            socket.send(Frame.Text(value))
                        }

                        override suspend fun close() {
                            socket.close()
                        }
                    }
                    .ok()
            }
            .let { result ->
                when (result) {
                    is Outcome.Ok -> result.value
                    is Outcome.Failed -> result
                }
            }
    }

    private fun query(): Map<String, String?> =
        mapOf("location[directory]" to gateway.location.value.directory)

    private fun path(id: String): String = "/pty/${id.encodeURLPathPart()}"

    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value

    private fun process(value: JsonElement): TerminalProcess? {
        val obj = value as? JsonObject ?: return null
        fun text(key: String): String? =
            (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val id = text("id")?.takeIf { it.isNotBlank() } ?: return null
        return TerminalProcess(
            id,
            text("title").orEmpty(),
            text("command").orEmpty(),
            (obj["args"] as? JsonArray)
                ?.map { (it as? JsonPrimitive)?.content ?: return null }
                ?.toImmutableList() ?: persistentListOf(),
            text("cwd").orEmpty(),
            text("status") == "running",
            (obj["pid"] as? JsonPrimitive)?.intOrNull ?: 0,
            (obj["exitCode"] as? JsonPrimitive)?.intOrNull,
        )
    }

    companion object {
        const val USAGE_TITLE: String = "OpenCode Mobile usage check"
    }
}
