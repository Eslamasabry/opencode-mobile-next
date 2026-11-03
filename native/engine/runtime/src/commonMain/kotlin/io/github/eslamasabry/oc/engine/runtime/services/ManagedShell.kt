package io.github.eslamasabry.oc.engine.runtime.services

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/*
 * Engine-side mirror of lib/domain/managed_shell.dart: a server-owned, non-interactive command (OpenCode 2's
 * `/api/shell`). Host paths beyond the working folder and process IDs are deliberately absent. :engine:runtime depends
 * on :core:common only, so these are small engine types; the integrator maps the :core:domain / :protocol:oc2 shell
 * types onto [ManagedShellPort] (field for field: id, command, cwd → directory, status, time.started →
 * startedAtMillis, time.completed, exit, metadata.ocDevelopmentServiceOwner → ownerToken, metadata.sessionID).
 */

public enum class ManagedShellStatus { Running, Exited, Timeout, Killed, Unknown }

public data class ManagedShell(
    val id: String,
    val command: String,
    val status: ManagedShellStatus,
    val startedAtMillis: Long,
    val completedAtMillis: Long? = null,
    val exitCode: Int? = null,
    val sessionId: String? = null,
    val directory: String? = null,
    /** Only this feature's ownership field, never arbitrary server metadata. */
    val ownerToken: String? = null,
) {
    val running: Boolean get() = status == ManagedShellStatus.Running
}

public data class ManagedShellList(val supported: Boolean, val shells: List<ManagedShell> = emptyList())

public data class ManagedShellOutput(
    val text: String,
    /** Absolute byte offsets supplied by the server, never string lengths. */
    val cursor: Long,
    val size: Long,
    val truncated: Boolean,
)

/**
 * The connected server's managed shells (port of `ManagedShellGateway`). Every call answers an [Outcome]; [get]
 * answers `Ok(null)` for a shell the server no longer has (HTTP 404).
 */
public interface ManagedShellPort {
    public suspend fun start(command: String, directory: String, ownerToken: String): Outcome<ManagedShell>

    public suspend fun running(): Outcome<ManagedShellList>

    public suspend fun get(id: String): Outcome<ManagedShell?>

    public suspend fun output(id: String, cursor: Long, limit: Int = MAX_OUTPUT_READ): Outcome<ManagedShellOutput>

    public suspend fun stop(id: String): Outcome<Unit>

    public companion object {
        /** The most one output read asks for (the server's own cap). */
        public const val MAX_OUTPUT_READ: Int = 65_536
    }
}

/** The OpenCode 2 request a start sends: what the :protocol:oc2 binding must put on the wire (B-LINUX-060). */
public data class ManagedShellStartRequest(
    val method: String,
    /** Relative to the server's `/api` prefix. */
    val path: String,
    val query: Map<String, String>,
    val body: JsonObject,
)

/** The wire contract of `/api/shell` for this feature (port of lib/api2/gateway_operations/ops_shells.dart). */
public object ManagedShellWire {
    /** The one metadata key the app writes and reads back. */
    public const val OWNER_KEY: String = "ocDevelopmentServiceOwner"

    /**
     * A location-scoped create: `POST /api/shell?location[directory]=…&location[workspace]=…` with no timeout and only
     * the ownership nonce as metadata. Null for an invalid command (blank, over 4096) or a missing folder or owner.
     */
    public fun startRequest(
        command: String,
        directory: String,
        workspace: String?,
        ownerToken: String,
    ): ManagedShellStartRequest? {
        if (command.isBlank() || command.length > 4096 || directory.isBlank() || ownerToken.isEmpty()) return null
        val query = buildMap {
            put("location[directory]", directory)
            if (workspace != null) put("location[workspace]", workspace)
        }
        val body = buildJsonObject {
            put("command", command)
            put("cwd", directory)
            put("timeout", 0)
            putJsonObject("metadata") { put(OWNER_KEY, ownerToken) }
        }
        return ManagedShellStartRequest("POST", "/api/shell", query, body)
    }

    /** A shell from the server's `data` object, keeping only the fields above; null when id or start time is missing. */
    public fun decode(json: JsonObject): ManagedShell? {
        val id = json.string("id") ?: return null
        val time = json["time"] as? JsonObject ?: JsonObject(emptyMap())
        val metadata = json["metadata"] as? JsonObject ?: JsonObject(emptyMap())
        val started = (time["started"] as? JsonPrimitive)?.longOrNull ?: return null
        val status = json.string("status")
        return ManagedShell(
            id = id,
            command = json.string("command") ?: "",
            directory = json.string("cwd"),
            ownerToken = metadata.string(OWNER_KEY),
            status = ManagedShellStatus.entries.firstOrNull { it.name.lowercase() == status } ?: ManagedShellStatus.Unknown,
            startedAtMillis = started,
            completedAtMillis = (time["completed"] as? JsonPrimitive)?.longOrNull,
            exitCode = (json["exit"] as? JsonPrimitive)?.longOrNull?.toInt(),
            sessionId = metadata.string("sessionID"),
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
