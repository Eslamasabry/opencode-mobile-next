package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.domain.unavailable
import io.github.eslamasabry.oc.core.model.McpConfigScope
import io.github.eslamasabry.oc.core.model.McpResourceInfo
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.McpServerKind
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.wire.str
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Runtime-only MCP operations from the frozen OpenCode 2 `/api/mcp` contract. */
internal class Oc2Mcp(private val gateway: Oc2Gateway, private val redactor: Redactor) :
    McpGateway {
    // A second tap cannot pass the uniqueness read while the first add is still being dispatched.
    private val writes = Mutex()

    override suspend fun mcpServers(): Outcome<ImmutableList<McpServerInfo>> =
        servers(gateway.location.value)

    private suspend fun servers(at: ServerLocation): Outcome<ImmutableList<McpServerInfo>> {
        return when (val reply = gateway.json("/mcp", query = query(at))) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                if (gateway.location.value != at) return stale()
                val list = data(reply.value) as? JsonArray
                if (list == null || list.size > 2000) unreadable()
                else {
                    val names = HashSet<String>()
                    val result = ArrayList<McpServerInfo>(list.size)
                    for (value in list) {
                        val item = value as? JsonObject ?: return unreadable()
                        val name = item.str("name")?.takeIf(::validName) ?: return unreadable()
                        if (!names.add(name)) return unreadable()
                        val statusObject = item["status"] as? JsonObject
                        val status = statusObject?.str("status") ?: item.str("status") ?: "unknown"
                        result +=
                            McpServerInfo(
                                name,
                                status,
                                statusObject?.str("error")?.let { redactor.redact(it).take(4096) },
                            )
                    }
                    result.toImmutableList().ok()
                }
            }
        }
    }

    override suspend fun mcpResources(): Outcome<ImmutableList<McpResourceInfo>> {
        val at = gateway.location.value
        return when (val reply = gateway.json("/mcp/resource", query = query(at))) {
            is Outcome.Failed -> reply
            is Outcome.Ok -> {
                if (gateway.location.value != at) return stale()
                val root = data(reply.value) as? JsonObject ?: return unreadable()
                val list = root["resources"] as? JsonArray ?: return unreadable()
                if (list.size > 2000) return unreadable()
                val result = ArrayList<McpResourceInfo>(list.size)
                for (value in list) {
                    val item = value as? JsonObject ?: return unreadable()
                    val name = item.str("name")?.takeIf { it.isNotBlank() } ?: continue
                    result +=
                        McpResourceInfo(
                            name,
                            item.str("server").orEmpty(),
                            item.str("uri").orEmpty(),
                            item.str("description"),
                            item.str("mimeType"),
                        )
                }
                result.toImmutableList().ok()
            }
        }
    }

    override suspend fun connectMcp(name: String): Outcome<Unit> = connection(name, "connect")

    override suspend fun disconnectMcp(name: String): Outcome<Unit> = connection(name, "disconnect")

    private suspend fun connection(name: String, action: String): Outcome<Unit> {
        if (!validName(name)) return Failure.Rejected(400).failed()
        val at = gateway.location.value
        val answer =
            gateway
                .json(
                    "/mcp/${name.encodeURLPathPart()}/$action",
                    HttpMethod.Post,
                    query = query(at),
                )
                .map {}
        return if (gateway.location.value == at) answer else stale()
    }

    override suspend fun addMcpServer(draft: McpServerDraft, scope: McpConfigScope): Outcome<Unit> {
        if (scope != McpConfigScope.RuntimeLocation) return unavailable("mcpConfigWrites")
        val name = draft.name.trim()
        if (!validName(name)) return Failure.Rejected(400).failed()
        val config = config(draft) ?: return Failure.Rejected(400).failed()
        // Register known secrets before the request can reach transport diagnostics.
        draft.headers.values.forEach(redactor::registerSecret)
        draft.environment.values.forEach(redactor::registerSecret)
        val at = gateway.location.value
        return writes.withLock {
            if (gateway.location.value != at) return@withLock stale()
            val existing =
                when (val read = servers(at)) {
                    is Outcome.Failed -> return@withLock read
                    is Outcome.Ok -> read.value
                }
            if (gateway.location.value != at) return@withLock stale()
            // PUT replaces a name: refuse even a disabled or failed existing connector.
            if (existing.any { it.name == name }) return@withLock Failure.Conflict().failed()
            val answer =
                gateway
                    .json(
                        "/mcp/${name.encodeURLPathPart()}",
                        HttpMethod.Put,
                        buildJsonObject { put("config", config) },
                        query(at),
                    )
                    .map {}
            if (gateway.location.value == at) answer else stale()
        }
    }

    private fun config(draft: McpServerDraft): JsonObject? {
        val timeout = draft.timeoutMs
        if (timeout != null && timeout <= 0) return null
        if (!validPairs(draft.headers) || !validPairs(draft.environment)) return null
        val url =
            if (draft.kind == McpServerKind.Remote) {
                val raw =
                    draft.url?.trim()?.takeIf { it.isNotEmpty() && !hasControl(it) } ?: return null
                val parsed =
                    try {
                        Url(raw)
                    } catch (_: Exception) {
                        return null
                    }
                if (
                    parsed.protocol.name !in setOf("http", "https") ||
                        parsed.host.isEmpty() ||
                        !parsed.user.isNullOrEmpty() ||
                        !parsed.password.isNullOrEmpty()
                )
                    return null
                raw
            } else null
        val command = draft.command.map { it.trim() }
        if (
            draft.kind == McpServerKind.Local &&
                (command.isEmpty() || command.any { it.isEmpty() || '\u0000' in it })
        )
            return null
        if (draft.cwd?.let(::hasControl) == true) return null
        return buildJsonObject {
            when (draft.kind) {
                McpServerKind.Remote -> {
                    put("type", "remote")
                    put("url", url!!)
                    if (draft.headers.isNotEmpty()) put("headers", pairs(draft.headers))
                    if (!draft.detectOAuth) put("oauth", false)
                }
                McpServerKind.Local -> {
                    put("type", "local")
                    put("command", JsonArray(command.map(::JsonPrimitive)))
                    draft.cwd?.trim()?.takeIf { it.isNotEmpty() }?.let { put("cwd", it) }
                    if (draft.environment.isNotEmpty()) put("environment", pairs(draft.environment))
                }
            }
            timeout?.let { value ->
                put(
                    "timeout",
                    buildJsonObject {
                        put("startup", value)
                        put("catalog", value)
                        put("execution", value)
                    },
                )
            }
        }
    }

    private fun validName(value: String): Boolean =
        value.isNotBlank() &&
            value !in setOf(".", "..") &&
            value.length <= 256 &&
            !hasControl(value)

    private fun validPairs(values: Map<String, String>): Boolean = values.all { (name, value) ->
        name.isNotBlank() && '=' !in name && !hasControl(name) && !hasControl(value)
    }

    private fun pairs(values: Map<String, String>): JsonObject =
        JsonObject(values.mapValues { JsonPrimitive(it.value) })

    private fun hasControl(value: String): Boolean = value.any { it < ' ' || it == '\u007f' }

    private fun query(at: ServerLocation): Map<String, String?> =
        mapOf("location[directory]" to at.directory, "location[workspace]" to at.workspace)

    private fun data(value: JsonElement): JsonElement = (value as? JsonObject)?.get("data") ?: value

    private fun <T> unreadable(): Outcome<T> = Failure.UnreadableReply().failed()

    private fun <T> stale(): Outcome<T> = Failure.Refused(RefusalReason.StaleScope).failed()
}
