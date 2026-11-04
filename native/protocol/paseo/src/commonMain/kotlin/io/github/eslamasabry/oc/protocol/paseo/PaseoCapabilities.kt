package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities

/**
 * The Paseo base (plan 04 §11.4): a port of Dart `paseoServerCapabilities`
 * (lib/paseo/gateway/capabilities.dart), written as the flags that differ from the core defaults.
 * Runtime facts are applied by [paseoCapabilities].
 *
 * Files, changes, worktrees and terminal are on (S5-PASEOP1); so are read-only sub-agent sessions.
 */
public object PaseoCapabilities {
    public val Base: ServerCapabilities =
        ServerCapabilities(
            scheduledPrompts = true,
            hostAgentProviders = true,
            hostAgentPermissionActions = true,
            promptImagesOnly = true,
            promptEchoTextOnly = true,
            subagentReplies = false,
            promptAgentMentions = false,
            offlinePromptQueue = false,
            conversationShell = false,
            projectManagement = false,
            globalSessionSearch = false,
            sessionFork = false,
            sessionCompact = false,
            savedPermissionList = false,
            messageCompletionEndsRun = false,
            sessionRevert = false,
            sessionImportExport = false,
            sessionNotes = false,
            serverCatalog = false,
            sessionModelProviderSwitching = false,
            agentSelection = false,
            profileAttentionPolling = false,
            managedWorkspaces = false,
            workspaceWarp = false,
            sessionSteal = false,
            consoleOrganizations = false,
            mcpOAuth = false,
            mcpConfigWrites = false,
            sessionShare = false,
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
            cliSessionResume = false,
            // Files, Changes, Worktrees and Terminal (gateway/PaseoWorkspace.kt, PaseoTerminals.kt,
            // PaseoOperations.kt).
            fileBrowsing = true,
            terminal = true,
            sessionDiff = true,
            worktreeCreate = true,
            worktreeBrowsing = true,
            // Claude Code's sub-agents as read-only child sessions (gateway/PaseoSubAgents.kt);
            // they take no replies.
            subagentSessions = true,
        )
}

/** Handoff writes require the current connection's negotiated workspace file editing support. */
public fun paseoCapabilities(
    connected: Boolean,
    workspaceFileEditing: Boolean,
): ServerCapabilities =
    PaseoCapabilities.Base.copy(
        genUi = false,
        agentHandoffFiles = connected && workspaceFileEditing,
    )
