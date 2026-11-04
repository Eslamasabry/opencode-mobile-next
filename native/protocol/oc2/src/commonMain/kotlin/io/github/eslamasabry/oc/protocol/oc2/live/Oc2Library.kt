package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.http.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.json.*

/**
 * Library operations on the live authenticated connection, pinned to the place captured at entry.
 */
internal class Oc2Library(
    private val gateway: Oc2Gateway,
    location: ServerLocation,
    private val redactor: Redactor,
) {
    private val query =
        buildMap<String, String?> {
            location.directory?.takeIf { it.isNotEmpty() }?.let { put("location[directory]", it) }
            location.workspace?.takeIf { it.isNotEmpty() }?.let { put("location[workspace]", it) }
        }

    suspend fun plugins(): Outcome<ImmutableList<LibraryPlugin>> {
        val value =
            when (val read = gateway.json("/plugin", query = query)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return read
            }
        val rows =
            (value as? JsonObject)?.get("data") as? JsonArray
                ?: return Failure.UnreadableReply().failed()
        if (rows.size > 1000) return Failure.UnreadableReply().failed()
        val result = mutableListOf<LibraryPlugin>()
        for (raw in rows) {
            val row = raw as? JsonObject ?: return Failure.UnreadableReply().failed()
            val source = row["source"] as? JsonObject ?: return Failure.UnreadableReply().failed()
            val type = source.text("type") ?: return Failure.UnreadableReply().failed()
            val state = row["state"] as? JsonObject
            val status =
                row.text("status")
                    ?: state?.text("status")
                    ?: return Failure.UnreadableReply().failed()
            val features = row["features"] as? JsonObject
            val rawTui = row["tui"] ?: features?.let { it["tui"] ?: JsonPrimitive(false) }
            val tui =
                (rawTui as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
                    ?: return Failure.UnreadableReply().failed()
            val kind =
                when (type) {
                    "builtin" -> LibraryPluginSource.Builtin
                    "package" -> LibraryPluginSource.Package
                    "local" -> LibraryPluginSource.Local
                    "sdk" -> LibraryPluginSource.Sdk
                    else -> LibraryPluginSource.Unknown
                }
            result +=
                LibraryPlugin(
                    id =
                        row.text("id")?.takeIf {
                            it.length <= 200 && ID.matches(it) && !redactor.containsSecret(it)
                        },
                    status =
                        when (status) {
                            "active" -> LibraryPluginStatus.Active
                            "failed" -> LibraryPluginStatus.Failed
                            else -> LibraryPluginStatus.Unknown
                        },
                    source = kind,
                    terminalUi = tui,
                    packageName =
                        source.text("package")?.takeIf {
                            kind == LibraryPluginSource.Package &&
                                it.length <= 200 &&
                                PACKAGE.matches(it) &&
                                !redactor.containsSecret(it)
                        },
                )
        }
        return result.toImmutableList().ok()
    }

    suspend fun skills(): Outcome<ImmutableList<SkillInfo>> {
        val value =
            when (val read = gateway.json("/skill", query = query)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return read
            }
        val rows =
            ((value as? JsonObject)?.get("data") ?: value) as? JsonArray
                ?: return Failure.UnreadableReply().failed()
        if (rows.size > 1000) return Failure.UnreadableReply().failed()
        val result = mutableListOf<SkillInfo>()
        for (raw in rows) {
            val row = raw as? JsonObject ?: return Failure.UnreadableReply().failed()
            val id = row.text("id") ?: continue
            if (id.isEmpty()) continue
            val name = row.text("name")?.takeIf { it.isNotBlank() } ?: id
            result += SkillInfo(id, redactor.details(name.take(200)).text, null, "", "", false)
        }
        return result.toImmutableList().ok()
    }

    suspend fun activateSkill(session: SessionId, skillId: String, resume: Boolean): Outcome<Unit> {
        if (skillId.isBlank() || skillId.length > 512 || CONTROL.containsMatchIn(skillId))
            return Failure.UnreadableReply().failed()
        val result =
            gateway.json(
                "/session/${session.raw.encodeURLPathPart()}/skill",
                HttpMethod.Post,
                buildJsonObject {
                    put("skill", skillId)
                    put("resume", resume)
                },
            )
        return when (result) {
            is Outcome.Ok -> Unit.ok()
            is Outcome.Failed -> {
                val failure = result.failure
                when {
                    failure is Failure.Rejected && failure.status in listOf(405, 501) ->
                        Failure.Unavailable(FeatureId("sessionSkills")).failed()
                    failure is Failure.NotFound &&
                        failure.tag?.raw !in setOf("SessionNotFoundError", "SkillNotFoundError") ->
                        Failure.Unavailable(FeatureId("sessionSkills")).failed()
                    failure is Failure.Server && failure.status == 501 ->
                        Failure.Unavailable(FeatureId("sessionSkills")).failed()
                    failure is Failure.Server ||
                        failure is Failure.Network ||
                        failure is Failure.TimedOut ->
                        Failure.Refused(RefusalReason.DispatchUncertain).failed()
                    else -> result
                }
            }
        }
    }

    suspend fun mcpServers(): Outcome<ImmutableList<McpServerInfo>> {
        val value =
            when (val read = gateway.json("/mcp", query = query)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return read
            }
        val rows =
            ((value as? JsonObject)?.get("data") ?: value) as? JsonArray
                ?: return Failure.UnreadableReply().failed()
        if (rows.size > 1000) return Failure.UnreadableReply().failed()
        val result = mutableListOf<McpServerInfo>()
        for (raw in rows) {
            val row = raw as? JsonObject ?: return Failure.UnreadableReply().failed()
            val name = row.text("name") ?: return Failure.UnreadableReply().failed()
            if (!safeName(name)) continue
            val status =
                row.text("status") ?: (row["status"] as? JsonObject)?.text("status") ?: "unknown"
            result += McpServerInfo(name, status.takeIf { it in MCP_STATUSES } ?: "unknown", null)
        }
        return result.toImmutableList().ok()
    }

    suspend fun connectMcp(name: String): Outcome<Unit> {
        if (!gateway.capabilities.value.serverCatalog)
            return Failure.Unavailable(FeatureId("serverCatalog")).failed()
        if (!safeName(name)) return Failure.UnreadableReply().failed()
        return gateway
            .json("/mcp/${name.encodeURLPathPart()}/connect", HttpMethod.Post, query = query)
            .map { Unit }
    }

    suspend fun disconnectMcp(name: String): Outcome<Unit> {
        if (!gateway.capabilities.value.serverCatalog)
            return Failure.Unavailable(FeatureId("serverCatalog")).failed()
        if (!safeName(name)) return Failure.UnreadableReply().failed()
        return gateway
            .json("/mcp/${name.encodeURLPathPart()}/disconnect", HttpMethod.Post, query = query)
            .map { Unit }
    }

    suspend fun addMcpServer(draft: McpServerDraft, scope: McpConfigScope): Outcome<Unit> {
        if (scope != McpConfigScope.RuntimeLocation)
            return Failure.Unavailable(FeatureId("mcpPersistentScope")).failed()
        val name = draft.name.trim()
        if (!safeName(name)) return Failure.UnreadableReply().failed()
        val config = draftConfig(draft) ?: return Failure.UnreadableReply().failed()
        val existing =
            when (val read = mcpServers()) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return read
            }
        if (existing.any { it.name == name }) return Failure.Conflict().failed()
        return gateway
            .json(
                "/mcp/${name.encodeURLPathPart()}",
                HttpMethod.Put,
                buildJsonObject { put("config", config) },
                query,
            )
            .map { Unit }
    }

    private fun draftConfig(draft: McpServerDraft): JsonObject? {
        val timeout = draft.timeoutMs
        if (timeout != null && timeout <= 0) return null
        fun validPairs(pairs: Map<String, String>) = pairs.all { (key, value) ->
            key.isNotBlank() && !PAIR_BAD.containsMatchIn(key) && !LINE.containsMatchIn(value)
        }
        if (!validPairs(draft.headers) || !validPairs(draft.environment)) return null
        draft.headers.values.forEach(redactor::registerSecret)
        draft.environment.values.forEach(redactor::registerSecret)
        val pairs = { values: Map<String, String> ->
            JsonObject(values.mapValues { JsonPrimitive(it.value) })
        }
        return buildJsonObject {
            when (draft.kind) {
                McpServerKind.Remote -> {
                    val raw = draft.url?.trim().orEmpty()
                    val url =
                        try {
                            Url(raw)
                        } catch (_: IllegalArgumentException) {
                            return null
                        }
                    if (
                        url.protocol !in setOf(URLProtocol.HTTP, URLProtocol.HTTPS) ||
                            url.host.isEmpty() ||
                            url.user != null ||
                            url.password != null
                    )
                        return null
                    put("type", "remote")
                    put("url", raw)
                    if (draft.headers.isNotEmpty()) put("headers", pairs(draft.headers))
                    if (!draft.detectOAuth) put("oauth", false)
                }
                McpServerKind.Local -> {
                    val command = draft.command.map { it.trim() }
                    if (command.isEmpty() || command.any { it.isEmpty() }) return null
                    put("type", "local")
                    put("command", JsonArray(command.map(::JsonPrimitive)))
                    draft.cwd?.trim()?.takeIf { it.isNotEmpty() }?.let { put("cwd", it) }
                    if (draft.environment.isNotEmpty()) put("environment", pairs(draft.environment))
                }
            }
            if (timeout != null)
                put(
                    "timeout",
                    buildJsonObject {
                        put("startup", timeout)
                        put("catalog", timeout)
                        put("execution", timeout)
                    },
                )
        }
    }

    private fun safeName(name: String) =
        name.isNotBlank() &&
            name == name.trim() &&
            name.length <= 512 &&
            !CONTROL.containsMatchIn(name) &&
            !redactor.containsSecret(name)

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        val ID = Regex("^@?[A-Za-z0-9][A-Za-z0-9_./@-]*$")
        val PACKAGE =
            Regex(
                "^(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*(?:@[A-Za-z0-9][A-Za-z0-9._+-]*)?$"
            )
        val CONTROL = Regex("[\\x00-\\x1f\\x7f]")
        val PAIR_BAD = Regex("[\\r\\n=]")
        val LINE = Regex("[\\r\\n]")
        val MCP_STATUSES =
            setOf(
                "connected",
                "disabled",
                "failed",
                "needs_auth",
                "needs_client_registration",
                "disconnected",
                "connecting",
                "unknown",
            )
    }
}
