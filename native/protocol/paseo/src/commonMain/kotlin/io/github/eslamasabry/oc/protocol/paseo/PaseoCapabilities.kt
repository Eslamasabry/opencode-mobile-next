package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities

/**
 * The Paseo base (plan 04 §11.4): a port of Dart `paseoServerCapabilities` (lib/paseo/gateway/capabilities.dart),
 * written as the flags that differ from the core defaults. Runtime facts are applied by [paseoCapabilities].
 *
 * Deviation (S3-PASEO report D3): the P1 extras this unit does not port yet are OFF here, so no screen offers them:
 * files, changes (diff), worktrees, terminal and sub-agent sessions (S5-PASEOP1 turns them on with their code).
 */
public object PaseoCapabilities {
    public val Base: ServerCapabilities = ServerCapabilities(
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
        // TODO(S5-PASEOP1): Dart has these on; their gateways are P1 extras.
        fileBrowsing = true,
        terminal = false,
        sessionDiff = true,
        worktreeCreate = true,
        worktreeBrowsing = true,
        subagentSessions = false,
    )
}

/**
 * Dart `capabilities` getter: gen-UI off; handoff files only while connected to a daemon that negotiated writing.
 * TODO(S5-PASEOP1): handoff is a P1 extra; until its writer is ported the flag stays off whatever the daemon says.
 */
@Suppress("UNUSED_PARAMETER")
public fun paseoCapabilities(connected: Boolean, workspaceFileEditing: Boolean): ServerCapabilities =
    PaseoCapabilities.Base.copy(genUi = false, agentHandoffFiles = false)
