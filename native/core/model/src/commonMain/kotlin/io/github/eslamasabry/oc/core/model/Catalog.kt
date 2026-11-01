package io.github.eslamasabry.oc.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Models, providers, agents, commands, MCP, integrations, web search (value_types_core/catalog.dart,
// models_requests.dart, web_search.dart). Provider config can carry API keys: nothing here is ever logged
// (AGENTS.md: never echo provider credentials).

public data class ProviderInfo(
    val id: String,
    val name: String,
    val modelIds: ImmutableList<String>,
    /** Raw per-model data; may carry credentials-adjacent config. Never logged. */
    val modelData: ImmutableMap<String, JsonObject>,
)

public data class ProvidersResponse(
    val providers: ImmutableList<ProviderInfo>,
    val availableProviders: ImmutableList<ProviderInfo>,
    val defaultProviderId: String?,
    val defaultModelId: String?,
)

public data class AgentInfo(val name: String, val mode: String?, val color: String?, val model: String?)

public data class CatalogVariant(val id: String, val disabled: Boolean, val options: JsonObject)

public data class ModelCost(
    val inputPerMillion: Double,
    val outputPerMillion: Double,
    val cacheReadPerMillion: Double,
    val cacheWritePerMillion: Double,
)

public data class CatalogModel(
    val id: String,
    val providerId: String,
    val name: String,
    val family: String?,
    val enabled: Boolean,
    val status: String,
    val contextLimit: Int,
    val outputLimit: Int,
    val reasoning: Boolean,
    val attachments: Boolean,
    val tools: Boolean,
    val variants: ImmutableList<CatalogVariant>,
    val cost: ModelCost?,
    val releasedAtMs: Long?,
)

public data class CatalogProvider(val id: String, val name: String, val enabled: Boolean, val integrationId: String?)

public data class CatalogAgent(
    val id: String,
    val mode: String,
    val description: String?,
    val hidden: Boolean,
    val maxSteps: Int?,
    val color: String?,
    val model: String?,
)

public data class CatalogSnapshot(
    val providers: ImmutableList<CatalogProvider>,
    val models: ImmutableList<CatalogModel>,
    val agents: ImmutableList<CatalogAgent>,
)

public data class ChatDefaults(val model: ModelRef?, val agent: String?)

public data class ExperimentalServerCapabilities(val backgroundSubagents: Boolean)

public data class CodingToolInfo(val id: String, val description: String, val parameters: JsonElement?)

public data class CommandInfo(val name: String, val description: String?, val agent: String?, val subtask: Boolean)

public data class SkillInfo(
    val id: String?,
    val name: String,
    val description: String?,
    val location: String,
    val content: String,
    val slashCommand: Boolean,
)

public data class ReferenceInfo(val name: String, val path: String, val description: String?)

// ---- MCP

public data class McpServerInfo(val name: String, val status: String, val error: String?)

public enum class McpServerKind { Remote, Local }

public enum class McpConfigScope { Project, Global, RuntimeLocation }

/** [headers] and [environment] can hold secrets: register them with the Redactor before any use. */
public data class McpServerDraft(
    val name: String,
    val kind: McpServerKind,
    val url: String?,
    val command: ImmutableList<String>,
    val cwd: String?,
    val headers: ImmutableMap<String, String>,
    val environment: ImmutableMap<String, String>,
    val detectOAuth: Boolean,
    val timeoutMs: Int?,
)

public data class McpResourceInfo(val name: String, val server: String, val uri: String, val description: String?, val mimeType: String?)

/** Dart McpAuthLaunch: the authorization URL is server-authored, so it goes through ExternalLinkPolicy. */
public data class McpAuthLaunch(val authorizationUrl: String, val oauthState: String)

// ---- Integrations

public data class IntegrationConnectionInfo(val type: String, val id: String?, val label: String, val status: String?)

public data class IntegrationMethodInfo(
    val type: String,
    val id: String?,
    val label: String,
    val prompts: ImmutableList<JsonObject>,
    val environmentNames: ImmutableList<String>,
    val presetAnswers: ImmutableMap<String, String>,
)

public data class IntegrationInfo(
    val id: String,
    val name: String,
    val methods: ImmutableList<IntegrationMethodInfo>,
    val connections: ImmutableList<IntegrationConnectionInfo>,
    val connectionCount: Int,
)

public enum class IntegrationAuthMode { Auto, Code }

public enum class IntegrationAuthState { Pending, Complete, Failed, Expired }

public data class IntegrationAuthLaunch(
    val attemptId: String,
    val url: String,
    val instructions: String,
    val mode: IntegrationAuthMode,
    val expiresAtMs: Long?,
)

public data class IntegrationAuthStatus(val state: IntegrationAuthState, val message: String?, val expiresAtMs: Long?)

// ---- Web search

public data class WebSearchProvider(val id: String, val name: String)

public data class WebSearchResult(val url: String, val title: String?, val content: String?)

public data class WebSearchResponse(val providerId: String, val results: ImmutableList<WebSearchResult>)
