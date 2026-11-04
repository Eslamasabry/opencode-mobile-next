package io.github.eslamasabry.oc.core.domain.capabilities

import io.github.eslamasabry.oc.core.common.FeatureId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the connected server can do (PROTOCOLS-38). 86 flags with names, order and defaults
 * identical to Dart's `ServerCapabilities` (lib/domain/server_gateway/capabilities.dart);
 * `CapabilitySnapshotTest` checks it against `capabilities/default.json`. Generated once from the
 * Dart constructor, hand-owned from here (S0-CORE).
 *
 * Per-backend bases (`Oc2Capabilities.Base`, …) live in their protocol modules (plan 04 §11.4). The
 * UI gates on these flags and [PlatformCapabilities], never on BackendKind (AGENTS.md). Field
 * documentation is the Dart file's.
 */
@Serializable
public data class ServerCapabilities(
    val scheduledPrompts: Boolean = false,
    val phoneTeamExecution: Boolean = false,
    val genUi: Boolean = false,
    val sessionAddressHandoff: Boolean = false,
    val agentHandoffFiles: Boolean = false,
    val setupConfigRead: Boolean = false,
    val setupConfigWrite: Boolean = false,
    val setupMcpInventory: Boolean = false,
    val setupAssistantSession: Boolean = false,
    val clientPromptMessageID: Boolean = false,
    val commandReceipts: Boolean = false,
    val agentAccount: Boolean = false,
    val hostAgentProviders: Boolean = false,
    val hostAgentPermissionActions: Boolean = false,
    val promptAttachments: Boolean = true,
    val promptImagesOnly: Boolean = false,
    val promptEchoTextOnly: Boolean = false,
    val subagentReplies: Boolean = true,
    val subagentSessions: Boolean = false,
    val promptAgentMentions: Boolean = true,
    val promptSteering: Boolean = false,
    val offlinePromptQueue: Boolean = true,
    val fileBrowsing: Boolean = true,
    val terminal: Boolean = true,
    val conversationShell: Boolean = true,
    val projectManagement: Boolean = true,
    val globalSessionSearch: Boolean = true,
    val sessionDiff: Boolean = true,
    val sessionFork: Boolean = true,
    val sessionCompact: Boolean = true,
    val persistentPermissionGrants: Boolean = true,
    val permissionRequests: Boolean = true,
    val savedPermissionList: Boolean = true,
    val messageCompletionEndsRun: Boolean = true,
    val sessionRevert: Boolean = true,
    val sessionImportExport: Boolean = true,
    val sessionNotes: Boolean = true,
    val serverCatalog: Boolean = true,
    val sessionModelProviderSwitching: Boolean = true,
    val agentSelection: Boolean = true,
    val slashCommands: Boolean = true,
    val profileAttentionPolling: Boolean = true,
    val managedWorkspaces: Boolean = true,
    val workspaceWarp: Boolean = true,
    val sessionSteal: Boolean = true,
    val consoleOrganizations: Boolean = true,
    val mcpOAuth: Boolean = true,
    val mcpChatConnect: Boolean = false,
    val mcpChatOAuth: Boolean = false,
    val mcpChatToolRefresh: Boolean = false,
    val mcpConfigWrites: Boolean = true,
    val mcpRuntimeAdds: Boolean = false,
    val mcpRuntimeRemovals: Boolean = false,
    val integrationCredentials: Boolean = false,
    val integrationCommandAuth: Boolean = false,
    val pluginInventory: Boolean = false,
    val webSearch: Boolean = false,
    val sessionShare: Boolean = true,
    val sessionArchive: Boolean = true,
    val sessionDelete: Boolean = true,
    val sessionTodos: Boolean = true,
    val messageDelete: Boolean = true,
    val workspaceSymbols: Boolean = true,
    val textSearch: Boolean = true,
    val languageServiceStatus: Boolean = true,
    val formatterStatus: Boolean = true,
    val toolInventory: Boolean = true,
    val experimentalCapabilities: Boolean = true,
    val shellSettings: Boolean = true,
    val developmentServices: Boolean = false,
    val remoteUpgrade: Boolean = true,
    val clientDiagnostics: Boolean = true,
    val gitInit: Boolean = true,
    val providerRuntimeRefresh: Boolean = true,
    val configuredProviderFallback: Boolean = true,
    val globalEventStream: Boolean = true,
    val worktreeReset: Boolean = true,
    val worktreeCreate: Boolean = true,
    val worktreeBrowsing: Boolean = false,
    val legacyQuestionRequests: Boolean = true,
    val forms: Boolean = false,
    val inbox: Boolean = false,
    val cliSessionResume: Boolean = true,
    val projectsAreGitRepositories: Boolean = true,
    val remoteShell: Boolean = false,
    val claudeAgent: Boolean = false,
    val agentFeatures: Boolean = false,
    val worktreeInspection: Boolean = false,
) {
    /** Whether a conversation runs shell commands here (Dart `conversationShellOn`). */
    public val conversationShellOn: Boolean
        get() = terminal && conversationShell

    /**
     * Preserve every protocol capability while applying managed-host readiness (Dart `withGenUi`).
     */
    public fun withGenUi(value: Boolean, agentHandoffFiles: Boolean? = null): ServerCapabilities =
        copy(genUi = value, agentHandoffFiles = agentHandoffFiles ?: this.agentHandoffFiles)

    /** Preserve every protocol capability while applying [claudeAgent] (Dart `withClaudeAgent`). */
    public fun withClaudeAgent(value: Boolean): ServerCapabilities = copy(claudeAgent = value)

    /**
     * Every flag by name, in declaration order. Derived from the serial form, so it cannot drift.
     */
    public fun flags(): Map<String, Boolean> =
        FlagJson.encodeToJsonElement(serializer(), this).jsonObject.mapValues {
            it.value.jsonPrimitive.boolean
        }

    /** The flag a [FeatureId] names; null when no flag has that name. */
    public fun flag(name: String): Boolean? = flags()[name]

    public fun has(feature: FeatureId): Boolean = flag(feature.name) == true

    public companion object {
        /**
         * No gateway has confirmed any capability yet. Keep constructor defaults for legacy
         * snapshots only.
         */
        public val Unknown: ServerCapabilities =
            ServerCapabilities(
                promptAttachments = false,
                subagentReplies = false,
                promptAgentMentions = false,
                offlinePromptQueue = false,
                fileBrowsing = false,
                terminal = false,
                conversationShell = false,
                projectManagement = false,
                globalSessionSearch = false,
                sessionDiff = false,
                sessionFork = false,
                sessionCompact = false,
                persistentPermissionGrants = false,
                permissionRequests = false,
                savedPermissionList = false,
                messageCompletionEndsRun = false,
                sessionRevert = false,
                sessionImportExport = false,
                sessionNotes = false,
                serverCatalog = false,
                sessionModelProviderSwitching = false,
                agentSelection = false,
                slashCommands = false,
                profileAttentionPolling = false,
                managedWorkspaces = false,
                workspaceWarp = false,
                sessionSteal = false,
                consoleOrganizations = false,
                mcpOAuth = false,
                mcpConfigWrites = false,
                sessionShare = false,
                sessionArchive = false,
                sessionDelete = false,
                sessionTodos = false,
                messageDelete = false,
                workspaceSymbols = false,
                textSearch = false,
                languageServiceStatus = false,
                formatterStatus = false,
                toolInventory = false,
                experimentalCapabilities = false,
                shellSettings = false,
                remoteUpgrade = false,
                clientDiagnostics = false,
                gitInit = false,
                providerRuntimeRefresh = false,
                configuredProviderFallback = false,
                globalEventStream = false,
                worktreeReset = false,
                worktreeCreate = false,
                legacyQuestionRequests = false,
                cliSessionResume = false,
                projectsAreGitRepositories = false,
            )

        private val FlagJson = Json { encodeDefaults = true }
    }
}
