package io.github.eslamasabry.oc.engine.project.terminal

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A server terminal (PTY) as the server reports it (Flutter `TerminalProcess`). */
public data class TerminalProcess(
    val id: String,
    val title: String,
    val command: String,
    val arguments: List<String>,
    val directory: String,
    val running: Boolean,
    val pid: Int,
    val exitCode: Int? = null,
)

/** The project location a connection targets (`location[directory]`, `location[workspace]`). */
public data class TerminalLocation(val directory: String?, val workspace: String? = null)

/** One OpenCode 2 request. [path] is the full server path, `/api` prefix included. Never logged. */
public data class TerminalWireRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val body: JsonObject? = null,
)

/**
 * The authenticated OpenCode 2 transport, as the terminal commands need it; bound by the protocol layer. A non-2xx
 * answer is a [Failure] (404 is [Failure.NotFound], or [Failure.Rejected] with status 404 where the transport keeps
 * the status); the reply body is the decoded JSON, or null when empty.
 */
public interface TerminalWire {
    /** The location the client targets now. */
    public val location: TerminalLocation

    public suspend fun send(request: TerminalWireRequest): Outcome<JsonElement?>
}

/** Why a rerun did not start; each has one sentence in the UI's words. */
public sealed interface TerminalRerunResult {
    public data class Started(val process: TerminalProcess) : TerminalRerunResult

    /** "This terminal command cannot be rerun." — still running, no executable or folder, or a NUL in its text. */
    public data object CannotRerun : TerminalRerunResult

    /** "The terminal location has changed." — nothing was sent. */
    public data object LocationChanged : TerminalRerunResult

    /** "Could not confirm the rerun. Refresh terminals before trying again." — never retried, no server text kept. */
    public data object Unconfirmed : TerminalRerunResult
}

/** Optional facet: rerun a PTY's reported executable, arguments, folder and title in a NEW PTY. */
public interface TerminalCommandGateway {
    public suspend fun rerunTerminalCommand(ended: TerminalProcess): TerminalRerunResult
}

/** Capability checks for the terminal lifecycle facet: an unwired facet is unavailable; never inferred from flavor. */
public object TerminalLifecycleCapabilities {
    public fun supportsTerminalCommandRerun(terminal: Boolean, gateway: TerminalCommandGateway?): Boolean =
        terminal && gateway != null

    /** Neither pinned PTY contract reports the process start time. */
    public const val TERMINAL_AGE: Boolean = false

    /** Shell info has no effective deadline, including after a timeout update. */
    public const val EXTERNAL_COMMAND_TIME_LEFT: Boolean = false
}

/**
 * Validates and snapshots an ended process before a rerun: null for a running process, a blank executable or folder,
 * or a NUL in any of them or the arguments. Never synthesises shell lines; arguments stay separate.
 */
public fun terminalRerunSnapshot(ended: TerminalProcess): TerminalProcess? {
    if (ended.running || ended.command.isBlank() || ended.directory.isBlank()) return null
    if ((listOf(ended.command, ended.directory) + ended.arguments).any { it.contains('\u0000') }) return null
    return ended.copy(arguments = ended.arguments.toList(), running = false)
}

/**
 * The OpenCode 2 terminal commands over [wire] (Flutter `Api2TerminalCommandGateway` and `removeTerminal`). The
 * location is captured at construction so a later project switch cannot retarget a retained terminal row: recreate
 * this when the location or profile changes.
 */
public class Oc2TerminalCommands(private val wire: TerminalWire) : TerminalCommandGateway {
    private val captured: TerminalLocation = wire.location

    /** Closes a terminal. One that is already gone (404: it exited, or another client closed it) counts as closed. */
    public suspend fun closeTerminal(id: String): Outcome<Unit> =
        when (val reply = wire.send(TerminalWireRequest(method = "DELETE", path = "$API/pty/$id"))) {
            is Outcome.Ok -> Unit.ok()
            is Outcome.Failed -> if (reply.failure.isGone()) Unit.ok() else reply
        }

    override suspend fun rerunTerminalCommand(ended: TerminalProcess): TerminalRerunResult {
        val command = terminalRerunSnapshot(ended) ?: return TerminalRerunResult.CannotRerun
        val directory = captured.directory
        if (directory.isNullOrBlank() || wire.location != captured) return TerminalRerunResult.LocationChanged
        val query = buildMap {
            put("location[directory]", directory)
            captured.workspace?.let { put("location[workspace]", it) }
        }
        val body =
            buildJsonObject {
                put("command", command.command)
                putJsonArray("args") { command.arguments.forEach { add(JsonPrimitive(it)) } }
                put("cwd", command.directory)
                put("title", command.title)
            }
        val reply = wire.send(TerminalWireRequest(method = "POST", path = "$API/pty", query = query, body = body))
        // Transport failures may echo command arguments: none of their text is kept.
        val json = (reply as? Outcome.Ok)?.value ?: return TerminalRerunResult.Unconfirmed
        val process = parseTerminal((json as? JsonObject)?.get("data")) ?: return TerminalRerunResult.Unconfirmed
        return TerminalRerunResult.Started(process)
    }

    private fun Failure.isGone(): Boolean =
        this is Failure.NotFound || (this is Failure.Rejected && status == 404)

    private companion object {
        const val API = "/api"
    }
}

/** A strict reading of a PTY object: every field present with its type, or null. */
internal fun parseTerminal(raw: JsonElement?): TerminalProcess? {
    val data = raw as? JsonObject ?: return null
    fun string(key: String): String? = (data[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val id = string("id")?.takeIf { it.isNotEmpty() } ?: return null
    val title = string("title") ?: return null
    val command = string("command") ?: return null
    val args = data["args"] as? JsonArray ?: return null
    val arguments =
        args.map { value -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null }
    val cwd = string("cwd") ?: return null
    val status = string("status")?.takeIf { it == "running" || it == "exited" } ?: return null
    val pid = (data["pid"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return null
    val exitRaw = data["exitCode"]
    val exitCode =
        if (exitRaw == null || exitRaw is JsonNull) {
            null
        } else {
            (exitRaw as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return null
        }
    return TerminalProcess(
        id = id,
        title = title,
        command = command,
        arguments = arguments,
        directory = cwd,
        running = status == "running",
        pid = pid,
        exitCode = exitCode,
    )
}
