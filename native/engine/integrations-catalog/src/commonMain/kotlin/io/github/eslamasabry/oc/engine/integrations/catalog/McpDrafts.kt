package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.McpConfigScope
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Flutter McpServerDraft.toConfigJson (lib/domain/server_gateway/value_types_catalog.dart): fail closed on unsafe
// transport data before anything is sent.

/** Why a draft cannot be sent; each has one plain sentence in :ui:words (never the draft's own text). */
public enum class McpDraftProblem { Name, Timeout, RemoteUrl, HeaderPair, LocalCommand, EnvironmentPair }

public sealed interface McpDraftCheck {
    public data class Valid(val config: JsonObject) : McpDraftCheck

    public data class Invalid(val problem: McpDraftProblem) : McpDraftCheck
}

/**
 * Where an MCP server the person adds from the app is saved, or null when this server cannot take one. A server that
 * writes its configuration keeps it in the project; one that only adds at run time keeps it for this project location
 * until it restarts. Every add page asks this one rule, so no page offers Add where the server refuses it, and no page
 * hides Add where the server takes it (an OpenCode 2 server only adds at run time).
 */
public fun mcpAddScope(capabilities: ServerCapabilities): McpConfigScope? =
    when {
        capabilities.mcpConfigWrites -> McpConfigScope.Project
        capabilities.mcpRuntimeAdds -> McpConfigScope.RuntimeLocation
        else -> null
    }

private val LINE_BREAK = Regex("[\r\n]")
private val PAIR_NAME_BAD = Regex("[\r\n=]")

/** The server config object for [draft], or why it is refused. Headers and environment values are secrets. */
public fun mcpDraftConfig(draft: McpServerDraft): McpDraftCheck {
    val name = draft.name.trim()
    if (name.isEmpty() || LINE_BREAK.containsMatchIn(name)) return McpDraftCheck.Invalid(McpDraftProblem.Name)
    val timeout = draft.timeoutMs
    if (timeout != null && timeout <= 0) return McpDraftCheck.Invalid(McpDraftProblem.Timeout)
    return when (draft.kind) {
        McpServerKind.Remote -> {
            val value = draft.url?.trim().orEmpty()
            val url = CatalogUrl.parse(value)
            if (url == null || !url.hasAuthority || url.host.isEmpty() || (url.scheme != "https" && url.scheme != "http") ||
                url.userInfo.isNotEmpty()
            ) {
                return McpDraftCheck.Invalid(McpDraftProblem.RemoteUrl)
            }
            if (!validPairs(draft.headers)) return McpDraftCheck.Invalid(McpDraftProblem.HeaderPair)
            McpDraftCheck.Valid(
                JsonObject(
                    buildMap {
                        put("type", JsonPrimitive("remote"))
                        put("url", JsonPrimitive(value))
                        if (draft.headers.isNotEmpty()) put("headers", stringMap(draft.headers))
                        if (!draft.detectOAuth) put("oauth", JsonPrimitive(false))
                        if (timeout != null) put("timeout", JsonPrimitive(timeout))
                    },
                ),
            )
        }
        McpServerKind.Local -> {
            val parts = draft.command.map { it.trim() }
            if (parts.isEmpty() || parts.any { it.isEmpty() }) return McpDraftCheck.Invalid(McpDraftProblem.LocalCommand)
            if (!validPairs(draft.environment)) return McpDraftCheck.Invalid(McpDraftProblem.EnvironmentPair)
            McpDraftCheck.Valid(
                JsonObject(
                    buildMap {
                        put("type", JsonPrimitive("local"))
                        put("command", JsonArray(parts.map(::JsonPrimitive)))
                        draft.cwd?.trim()?.takeIf { it.isNotEmpty() }?.let { put("cwd", JsonPrimitive(it)) }
                        if (draft.environment.isNotEmpty()) put("environment", stringMap(draft.environment))
                        if (timeout != null) put("timeout", JsonPrimitive(timeout))
                    },
                ),
            )
        }
    }
}

private fun validPairs(values: Map<String, String>): Boolean =
    values.all { (key, value) ->
        key.trim().isNotEmpty() && !PAIR_NAME_BAD.containsMatchIn(key) && !LINE_BREAK.containsMatchIn(value)
    }

private fun stringMap(values: Map<String, String>): JsonObject = JsonObject(values.mapValues { JsonPrimitive(it.value) })
