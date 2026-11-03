package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.McpServerKind
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * OC2 connector (MCP) reads and runtime toggles for the native Connectors screen (unit BUILD-INTEGRATIONS-A), kept in
 * their own file so Oc2Gateway.kt is untouched. The same wire as Flutter `ops_catalog.dart`: `GET /mcp`,
 * `POST /mcp/{name}/connect|disconnect`, `PUT /mcp/{name}` (add until restart), `DELETE /mcp/{name}`, every call scoped to the gateway's current location. Each returns null when
 * [gateway] is not an OC2 gateway, so the caller says the server cannot do it rather than guess. Server error text is
 * never surfaced: a failed connector keeps only the fact that it failed.
 */
public object Oc2Connectors {
    /** Whether [gateway] speaks this wire (list, toggle, add for the current location until restart, remove). */
    public fun handles(gateway: ServerGateway): Boolean = gateway is Oc2Gateway

    /** Connectors configured at the current location (Flutter `listMcpServers`). */
    public suspend fun servers(gateway: ServerGateway): Outcome<ImmutableList<McpServerInfo>>? {
        val oc2 = gateway as? Oc2Gateway ?: return null
        return when (val reply = oc2.json("/mcp", query = location(oc2))) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                val root = reply.value
                val data = (root as? JsonObject)?.get("data") ?: root
                val rows = data as? JsonArray ?: return Failure.UnreadableReply().failed()
                rows.mapNotNull { row ->
                    val item = row as? JsonObject ?: return@mapNotNull null
                    val name = (item["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    val status = item["status"]
                    val word =
                        when (status) {
                            is JsonObject -> (status["status"] as? JsonPrimitive)?.contentOrNull
                            is JsonPrimitive -> status.contentOrNull
                            else -> null
                        } ?: "unknown"
                    // The server's error words stay off screen (no raw errors); only their presence is kept.
                    val failed = (status as? JsonObject)?.get("error").let { it != null && it !is JsonNull }
                    McpServerInfo(name, word, if (failed) "" else null)
                }.toImmutableList().ok()
            }
        }
    }

    /** Connects one configured connector at the current location. */
    public suspend fun connect(gateway: ServerGateway, name: String): Outcome<Unit>? = toggle(gateway, name, "connect")

    /** Disconnects one connector at the current location (it stays configured). */
    public suspend fun disconnect(gateway: ServerGateway, name: String): Outcome<Unit>? =
        toggle(gateway, name, "disconnect")

    /**
     * Adds [draft] at the current location until OpenCode restarts (Flutter `addMcpServer`, runtime location). `PUT`
     * replaces a server of the same name, so the list is read first and a taken name is refused (`Failure.Conflict`);
     * both requests use the location read once at the start. An invalid draft is `Failure.Rejected` before any request.
     */
    public suspend fun add(gateway: ServerGateway, draft: McpServerDraft): Outcome<Unit>? {
        val oc2 = gateway as? Oc2Gateway ?: return null
        val name = draft.name.trim()
        val config = configJson(draft) ?: return Failure.Rejected(null).failed()
        val at = location(oc2)
        val existing =
            when (val reply = oc2.json("/mcp", query = at)) {
                is Outcome.Failed -> return reply
                is Outcome.Ok -> names(reply.value) ?: return Failure.UnreadableReply().failed()
            }
        if (name in existing) return Failure.Conflict().failed()
        val body = buildJsonObject { put("config", config) }
        return when (val reply = oc2.json("/mcp/${name.encodeURLPathPart()}", HttpMethod.Put, body, at)) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> Unit.ok()
        }
    }

    /** Removes one connector at the current location; one kept in the server's configuration returns on restart. */
    public suspend fun remove(gateway: ServerGateway, name: String): Outcome<Unit>? {
        val oc2 = gateway as? Oc2Gateway ?: return null
        return when (val reply = oc2.json("/mcp/${name.encodeURLPathPart()}", HttpMethod.Delete, query = location(oc2))) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> Unit.ok()
        }
    }

    /** Flutter `McpServerDraft.toConfigJson` (plus the timeout block `addMcpServer` adds); null when invalid. */
    internal fun configJson(draft: McpServerDraft): JsonObject? {
        val name = draft.name.trim()
        if (name.isEmpty() || name.any { it == '\r' || it == '\n' }) return null
        val timeout = draft.timeoutMs
        if (timeout != null && timeout <= 0) return null
        fun validPairs(values: Map<String, String>): Boolean =
            values.all { (key, value) ->
                key.isNotBlank() && key.none { it == '\r' || it == '\n' || it == '=' } && value.none { it == '\r' || it == '\n' }
            }
        return when (draft.kind) {
            McpServerKind.Remote -> {
                val url = draft.url?.trim().orEmpty()
                val scheme = url.substringBefore("://", "").lowercase()
                val authority = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
                if ((scheme != "https" && scheme != "http") || authority.isEmpty() || '@' in authority) return null
                if (!validPairs(draft.headers)) return null
                buildJsonObject {
                    put("type", "remote")
                    put("url", url)
                    if (draft.headers.isNotEmpty()) put("headers", buildJsonObject { draft.headers.forEach { (k, v) -> put(k, v) } })
                    if (!draft.detectOAuth) put("oauth", false)
                    if (timeout != null) put("timeout", timeouts(timeout))
                }
            }
            McpServerKind.Local -> {
                val parts = draft.command.map { it.trim() }
                if (parts.isEmpty() || parts.any { it.isEmpty() }) return null
                if (!validPairs(draft.environment)) return null
                buildJsonObject {
                    put("type", "local")
                    put("command", buildJsonArray { parts.forEach { add(JsonPrimitive(it)) } })
                    draft.cwd?.trim()?.takeIf { it.isNotEmpty() }?.let { put("cwd", it) }
                    if (draft.environment.isNotEmpty()) {
                        put("environment", buildJsonObject { draft.environment.forEach { (k, v) -> put(k, v) } })
                    }
                    if (timeout != null) put("timeout", timeouts(timeout))
                }
            }
        }
    }

    private fun timeouts(ms: Int): JsonObject =
        buildJsonObject {
            put("startup", ms)
            put("catalog", ms)
            put("execution", ms)
        }

    private fun names(root: JsonElement): Set<String>? {
        val data = (root as? JsonObject)?.get("data") ?: root
        val rows = data as? JsonArray ?: return null
        return rows.mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }.toSet()
    }

    private suspend fun toggle(gateway: ServerGateway, name: String, verb: String): Outcome<Unit>? {
        val oc2 = gateway as? Oc2Gateway ?: return null
        return when (
            val reply = oc2.json("/mcp/${name.encodeURLPathPart()}/$verb", HttpMethod.Post, query = location(oc2))
        ) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> Unit.ok()
        }
    }

    private fun location(oc2: Oc2Gateway): Map<String, String?> {
        val at = oc2.location.value
        return mapOf("location[directory]" to at.directory, "location[workspace]" to at.workspace)
    }
}
