package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.flatMap
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellList
import io.github.eslamasabry.oc.core.model.ManagedShellOutput
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.github.eslamasabry.oc.core.model.McpConfigScope
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerKind
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.TerminalShellOption
import io.github.eslamasabry.oc.core.model.TerminalShellSettings
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlin.time.Duration
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// Ports of lib/api2/gateway_operations (ops_dev, ops_projects, ops_shells, ops_catalog). Every call
// carries the
// location it was started with: a location change while a call waits never retargets it.

internal fun loc(
    at: ServerLocation,
    extra: Map<String, String?> = emptyMap(),
): Map<String, String?> =
    mapOf("location[directory]" to at.directory, "location[workspace]" to at.workspace) + extra

private fun dataOf(element: JsonElement): JsonElement =
    (element as? JsonObject)?.get("data") ?: element

private fun dataMaps(element: JsonElement): List<JsonObject> =
    ((dataOf(element) as? JsonArray) ?: (element as? JsonArray))?.filterIsInstance<JsonObject>()
        ?: emptyList()

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.number(key: String): Long? =
    (this[key] as? JsonPrimitive)
        ?.takeIf { !it.isString }
        ?.doubleOrNull
        ?.takeIf { it.isFinite() }
        ?.toLong()

private fun rejected(): Outcome<Nothing> = Failure.Rejected(null).failed()

// ---- gitInit (B-PROTO-035): POST /vcs/init?provider=git; 409 means the folder already is a
// repository.

internal suspend fun Oc2Gateway.gitInit(): Outcome<Unit> =
    when (
        val reply =
            json(
                "/vcs/init",
                HttpMethod.Post,
                query = loc(location.value, mapOf("provider" to "git")),
            )
    ) {
        is Outcome.Ok -> Unit.ok()
        is Outcome.Failed -> if (reply.failure is Failure.Conflict) Unit.ok() else reply
    }

// ---- worktreeCreate (B-PROTO-035): stable takes {projectID, name} and answers {directory}.

internal suspend fun Oc2Gateway.worktreeCreate(
    projectDirectory: String,
    name: String?,
): Outcome<WorktreeInfo> {
    val place = json("/location", query = mapOf("location[directory]" to projectDirectory))
    if (place is Outcome.Failed) return place
    val id =
        ((place as Outcome.Ok).value as? JsonObject)
            ?.let { it["project"] as? JsonObject }
            ?.text("id")
    if (id.isNullOrEmpty()) return Failure.NotFound().failed()
    val created =
        json(
            "/worktree/${id.encodeURLPathPart()}",
            HttpMethod.Post,
            buildJsonObject { if (name != null) put("name", name) },
        )
    if (created is Outcome.Failed) return created
    val raw = (created as Outcome.Ok).value
    // Stable answers {directory}; the beta wrapped it in {data}.
    val data = (dataOf(raw) as? JsonObject) ?: return Failure.UnreadableReply().failed()
    val directory =
        data.text("directory")?.takeIf { it.isNotEmpty() }
            ?: return Failure.UnreadableReply().failed()
    return WorktreeInfo(
            name ?: directory.trimEnd('/').substringAfterLast('/'),
            directory,
            data.text("branch"),
            true,
        )
        .ok()
}

// ---- shellSettings (B-PROTO-035)

private suspend fun Oc2Gateway.shellOptions(
    at: ServerLocation
): Outcome<List<TerminalShellOption>> =
    json("/config/shell", query = loc(at)).map { root ->
        dataMaps(root).mapNotNull { item ->
            val path =
                item.text("path")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name =
                item.text("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            TerminalShellOption(path, name, item["acceptable"] == JsonPrimitive(true))
        }
    }

internal suspend fun Oc2Gateway.shellSettings(): Outcome<TerminalShellSettings> {
    val at = location.value
    val options = shellOptions(at)
    if (options is Outcome.Failed) return options
    val config = json("/config", query = loc(at))
    if (config is Outcome.Failed) return config
    // Config documents run lowest to highest priority; the last one that sets a shell wins.
    var selected = ""
    for (entry in dataMaps((config as Outcome.Ok).value)) {
        val shell = (entry["info"] as? JsonObject)?.text("shell")?.trim()
        if (!shell.isNullOrEmpty()) selected = shell
    }
    return TerminalShellSettings(selected, (options as Outcome.Ok).value.toImmutableList()).ok()
}

/**
 * The server accepts any string, so only a listed, acceptable shell is ever sent; empty clears it
 * with null.
 */
internal suspend fun Oc2Gateway.selectShell(value: String): Outcome<Unit> {
    val at = location.value
    val normalized = value.trim()
    if (normalized.isNotEmpty()) {
        val options = shellOptions(at)
        if (options is Outcome.Failed) return options
        if ((options as Outcome.Ok).value.none { it.path == normalized && it.acceptable })
            return rejected()
    }
    return json(
            "/experimental/config",
            HttpMethod.Patch,
            JsonObject(
                mapOf("shell" to if (normalized.isEmpty()) JsonNull else JsonPrimitive(normalized))
            ),
            loc(at),
        )
        .map {}
}

// ---- managed shells (B-PROTO-059)

private fun managedShellOf(json: JsonObject): ManagedShell? {
    val id = json.text("id") ?: return null
    val time = json["time"] as? JsonObject
    val metadata = json["metadata"] as? JsonObject
    return ManagedShell(
        id = id,
        command = json.text("command") ?: "",
        status =
            when (json.text("status")) {
                "running" -> ManagedShellStatus.Running
                "exited" -> ManagedShellStatus.Exited
                "timeout" -> ManagedShellStatus.Timeout
                "killed" -> ManagedShellStatus.Killed
                else -> ManagedShellStatus.Unknown
            },
        startedAtMs = time?.number("started") ?: return null,
        completedAtMs = time.number("completed"),
        exitCode = json.number("exit")?.toInt(),
        sessionId = metadata?.text("sessionID"),
        directory = json.text("cwd"),
        ownerToken = metadata?.text("ocDevelopmentServiceOwner"),
    )
}

private fun shellPath(id: String): String = "/shell/${id.encodeURLPathPart()}"

internal suspend fun Oc2Gateway.startShell(
    command: String,
    directory: String,
    ownerToken: String,
): Outcome<ManagedShell> {
    if (command.isBlank() || command.length > 4096 || directory.isBlank() || ownerToken.isEmpty())
        return rejected()
    return json(
            "/shell",
            HttpMethod.Post,
            buildJsonObject {
                put("command", command)
                put("cwd", directory)
                put("timeout", 0)
                putJsonObject("metadata") { put("ocDevelopmentServiceOwner", ownerToken) }
            },
            loc(location.value),
        )
        .flatMap { root ->
            (dataOf(root) as? JsonObject)?.let(::managedShellOf)?.ok()
                ?: Failure.UnreadableReply().failed()
        }
}

/**
 * 404/405/501 is a server without shells (supported=false); any other failure surfaces, never a
 * false "none".
 */
internal suspend fun Oc2Gateway.shells(): Outcome<ManagedShellList> =
    when (val reply = json("/shell", query = loc(location.value))) {
        is Outcome.Ok ->
            ManagedShellList(
                    true,
                    dataMaps(reply.value)
                        .map { managedShellOf(it) ?: return Failure.UnreadableReply().failed() }
                        .toImmutableList(),
                )
                .ok()
        is Outcome.Failed -> {
            val f = reply.failure
            val absent =
                f is Failure.NotFound ||
                    (f is Failure.Rejected && f.status == 405) ||
                    (f is Failure.Server && f.status == 501)
            if (absent)
                ManagedShellList(false, kotlinx.collections.immutable.persistentListOf()).ok()
            else reply
        }
    }

internal suspend fun Oc2Gateway.managedShellRead(id: String): Outcome<ManagedShell?> =
    when (val reply = json(shellPath(id), query = loc(location.value))) {
        is Outcome.Ok ->
            (dataOf(reply.value) as? JsonObject)?.let(::managedShellOf)?.ok()
                ?: Failure.UnreadableReply().failed()
        is Outcome.Failed -> if (reply.failure is Failure.NotFound) null.ok() else reply
    }

internal suspend fun Oc2Gateway.shellOutput(
    id: String,
    cursor: Long,
    limit: Int,
): Outcome<ManagedShellOutput> =
    json(
            "${shellPath(id)}/output",
            query =
                loc(
                    location.value,
                    mapOf(
                        "cursor" to cursor.toString(),
                        "limit" to limit.coerceIn(1, 65_536).toString(),
                    ),
                ),
        )
        .flatMap { root ->
            val data = dataOf(root) as? JsonObject
            val next = data?.number("cursor")
            val size = data?.number("size")
            if (data == null || next == null || size == null) Failure.UnreadableReply().failed()
            else
                ManagedShellOutput(
                        data.text("output") ?: "",
                        next,
                        size,
                        data["truncated"] == JsonPrimitive(true),
                    )
                    .ok()
        }

internal suspend fun Oc2Gateway.stopShell(id: String): Outcome<Unit> =
    json(shellPath(id), HttpMethod.Delete, query = loc(location.value)).map {}

/**
 * null = no timeout (0 on the wire). The stable line removed this route: it fails without a
 * request.
 */
internal suspend fun Oc2Gateway.shellTimeout(
    id: String,
    timeout: Duration?,
): Outcome<ManagedShell> =
    json(
            "${shellPath(id)}/timeout",
            HttpMethod.Patch,
            buildJsonObject { put("timeout", timeout?.inWholeMilliseconds ?: 0L) },
            loc(location.value),
        )
        .flatMap { root ->
            (dataOf(root) as? JsonObject)?.let(::managedShellOf)?.ok()
                ?: Failure.UnreadableReply().failed()
        }

/**
 * The server process id from the health facts (`/info` on stable), as the restart detector's
 * identity.
 */
internal suspend fun Oc2Gateway.shellServerIdentity(): Outcome<String?> =
    json("/health").map { root -> (root as? JsonObject)?.number("pid")?.toString() }

private val HttpUrl = Regex("^(https?)://([^/?#\\s]+)([/?#]\\S*)?$", RegexOption.IGNORE_CASE)

// ---- MCP runtime adds (B-PROTO-026, B-PROTO-030)

/** Dart `McpServerDraft.toConfigJson` + the v2 timeout object; null when the draft is invalid. */
internal fun mcpConfig(draft: McpServerDraft): JsonObject? {
    val name = draft.name.trim()
    if (name.isEmpty() || name.contains('\r') || name.contains('\n')) return null
    val timeout = draft.timeoutMs
    if (timeout != null && timeout <= 0) return null
    fun pairsOk(values: Map<String, String>) = values.all { (k, v) ->
        k.isNotBlank() &&
            k.none { it == '\r' || it == '\n' || it == '=' } &&
            v.none { it == '\r' || it == '\n' }
    }
    return when (draft.kind) {
        McpServerKind.Remote -> {
            val url = draft.url?.trim().orEmpty()
            val match = HttpUrl.matchEntire(url) ?: return null
            if ('@' in match.groupValues[2]) return null
            if (!pairsOk(draft.headers)) return null
            buildJsonObject {
                put("type", "remote")
                put("url", url)
                if (draft.headers.isNotEmpty())
                    putJsonObject("headers") { draft.headers.forEach { (k, v) -> put(k, v) } }
                if (!draft.detectOAuth) put("oauth", false)
                timeout?.let { putTimeout(it) }
            }
        }
        McpServerKind.Local -> {
            val parts = draft.command.map { it.trim() }
            if (parts.isEmpty() || parts.any { it.isEmpty() } || !pairsOk(draft.environment))
                return null
            buildJsonObject {
                put("type", "local")
                putJsonArray("command") { parts.forEach { add(JsonPrimitive(it)) } }
                draft.cwd?.trim()?.takeIf { it.isNotEmpty() }?.let { put("cwd", it) }
                if (draft.environment.isNotEmpty())
                    putJsonObject("environment") {
                        draft.environment.forEach { (k, v) -> put(k, v) }
                    }
                timeout?.let { putTimeout(it) }
            }
        }
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putTimeout(ms: Int) =
    putJsonObject("timeout") {
        put("startup", ms)
        put("catalog", ms)
        put("execution", ms)
    }

/**
 * v2 adds MCP servers for the current location until restart (PUT /mcp/{name}); persistent scopes
 * are refused without a request. PUT replaces existing names, so a name already in this location is
 * refused (Conflict) and both the uniqueness read and the PUT use the location captured at the
 * start.
 */
internal suspend fun Oc2Gateway.mcpAdd(
    draft: McpServerDraft,
    scope: McpConfigScope,
): Outcome<Unit> {
    if (scope != McpConfigScope.RuntimeLocation)
        return Failure.Unavailable(FeatureId("mcpConfigWrites")).failed()
    val config = mcpConfig(draft) ?: return rejected()
    val at = location.value
    val name = draft.name.trim()
    val existing = json("/mcp", query = loc(at))
    if (existing is Outcome.Failed) return existing
    val inventory =
        ((existing as Outcome.Ok).value as? JsonObject)?.get("data") as? JsonArray
            ?: return Failure.UnreadableReply().failed()
    if (
        inventory.size > 1000 ||
            inventory.any { it !is JsonObject || (it["name"] as? JsonPrimitive)?.isString != true }
    )
        return Failure.UnreadableReply().failed()
    if (inventory.any { (it as JsonObject).text("name") == name })
        return Failure.Conflict().failed()
    if (location.value != at) return Failure.Refused(RefusalReason.StaleScope).failed()
    return json(
            "/mcp/${name.encodeURLPathPart()}",
            HttpMethod.Put,
            buildJsonObject { put("config", config) },
            loc(at),
        )
        .map {}
}
