package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.TerminalGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.protocol.oc2.wire.int
import io.github.eslamasabry.oc.protocol.oc2.wire.str
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** All operations retain the location captured before their first suspension. */
internal class Oc2Terminals(
    private val gateway: Oc2Gateway,
    private val location: ServerLocation,
    private val connect: suspend (String, Long?, ServerLocation) -> Outcome<TerminalChannel>,
) : TerminalGateway {
    private val query =
        mapOf(
            "location[directory]" to location.directory,
            "location[workspace]" to location.workspace,
        )

    override suspend fun listTerminals(): Outcome<ImmutableList<TerminalProcess>> {
        val reply = gateway.json("/pty", query = query)
        if (reply is Outcome.Failed) return reply
        val rows =
            data((reply as Outcome.Ok).value) as? JsonArray
                ?: return Failure.UnreadableReply().failed()
        if (rows.size > 1000) return Failure.UnreadableReply().failed()
        val processes = rows.map { parse(it) ?: return Failure.UnreadableReply().failed() }
        return processes.filterNot { it.title == USAGE_TITLE }.toImmutableList().ok()
    }

    override suspend fun createTerminal(title: String?): Outcome<TerminalProcess> {
        if (title != null && !validTitle(title)) return Failure.Rejected(400).failed()
        val reply =
            gateway.json(
                "/pty",
                HttpMethod.Post,
                buildJsonObject { title?.let { put("title", it) } },
                query,
            )
        if (reply is Outcome.Failed) return reply
        return parse(data((reply as Outcome.Ok).value))?.ok() ?: Failure.UnreadableReply().failed()
    }

    /**
     * App-owned one-shot readers (AGENT-1): the executable and its arguments stay separate, so
     * nothing is interpolated by a shell.
     */
    override suspend fun createCommandTerminal(
        command: String,
        arguments: List<String>,
        title: String,
    ): Outcome<TerminalProcess> {
        if (
            command.isBlank() ||
                '\u0000' in command ||
                arguments.any { '\u0000' in it } ||
                !validTitle(title)
        )
            return Failure.Rejected(400).failed()
        val reply =
            gateway.json(
                "/pty",
                HttpMethod.Post,
                buildJsonObject {
                    put("command", command)
                    put("args", JsonArray(arguments.map(::JsonPrimitive)))
                    put("title", title)
                },
                query,
            )
        if (reply is Outcome.Failed) return reply
        return parse(data((reply as Outcome.Ok).value))?.ok() ?: Failure.UnreadableReply().failed()
    }

    override suspend fun renameTerminal(id: String, title: String): Outcome<Unit> {
        if (!validId(id) || !validTitle(title)) return Failure.Rejected(400).failed()
        return gateway
            .json(path(id), HttpMethod.Put, buildJsonObject { put("title", title) }, query)
            .map { Unit }
    }

    override suspend fun resizeTerminal(id: String, rows: Int, cols: Int): Outcome<Unit> {
        if (!validId(id) || rows !in 1..1000 || cols !in 1..1000)
            return Failure.Rejected(400).failed()
        return gateway
            .json(
                path(id),
                HttpMethod.Put,
                buildJsonObject {
                    put(
                        "size",
                        buildJsonObject {
                            put("rows", rows)
                            put("cols", cols)
                        },
                    )
                },
                query,
            )
            .map { Unit }
    }

    override suspend fun removeTerminal(id: String): Outcome<Unit> {
        if (!validId(id)) return Failure.Rejected(400).failed()
        return when (val reply = gateway.json(path(id), HttpMethod.Delete, query = query)) {
            is Outcome.Ok -> Unit.ok()
            is Outcome.Failed -> if (reply.failure is Failure.NotFound) Unit.ok() else reply
        }
    }

    override suspend fun connectTerminal(id: String, cursor: Long?): Outcome<TerminalChannel> {
        if (!validId(id) || (cursor != null && cursor < 0)) return Failure.Rejected(400).failed()
        return connect(id, cursor, location)
    }

    private fun parse(value: JsonElement): TerminalProcess? {
        val obj = value as? JsonObject ?: return null
        val id = obj.str("id")?.takeIf(::validId) ?: return null
        val args =
            (obj["args"] as? JsonArray)?.map {
                (it as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            } ?: emptyList()
        return TerminalProcess(
            id,
            gateway.terminalRedact(obj.str("title") ?: ""),
            obj.str("command") ?: "",
            args.toImmutableList(),
            obj.str("cwd") ?: "",
            obj.str("status") == "running",
            obj.int("pid") ?: 0,
            obj.int("exitCode"),
        )
    }

    private fun path(id: String): String = "/pty/${id.encodeURLPathPart()}"

    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value

    private fun validTitle(value: String): Boolean =
        value.isNotBlank() && value.length <= 200 && value.none { it.code < 32 }

    companion object {
        internal fun validId(value: String): Boolean =
            value.isNotBlank() &&
                value.length <= 256 &&
                value.all { it.isLetterOrDigit() || it in "._-" }

        private const val USAGE_TITLE = "OpenCode Mobile usage check"
    }
}
