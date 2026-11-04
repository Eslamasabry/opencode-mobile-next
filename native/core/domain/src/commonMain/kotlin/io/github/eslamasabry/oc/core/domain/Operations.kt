package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.BackgroundWorkResult
import io.github.eslamasabry.oc.core.model.BackgroundWorkSupport
import io.github.eslamasabry.oc.core.model.CatalogSnapshot
import io.github.eslamasabry.oc.core.model.ChatDefaults
import io.github.eslamasabry.oc.core.model.CodingToolInfo
import io.github.eslamasabry.oc.core.model.CommandInfo
import io.github.eslamasabry.oc.core.model.ConsoleOrganization
import io.github.eslamasabry.oc.core.model.ExperimentalServerCapabilities
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.FormatterHealth
import io.github.eslamasabry.oc.core.model.GlobalSessionResult
import io.github.eslamasabry.oc.core.model.IntegrationAuthLaunch
import io.github.eslamasabry.oc.core.model.IntegrationAuthStatus
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.model.LanguageServiceHealth
import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellList
import io.github.eslamasabry.oc.core.model.ManagedShellOutput
import io.github.eslamasabry.oc.core.model.McpAuthLaunch
import io.github.eslamasabry.oc.core.model.McpConfigScope
import io.github.eslamasabry.oc.core.model.McpResourceInfo
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.ProjectDirectoryInfo
import io.github.eslamasabry.oc.core.model.ReferenceInfo
import io.github.eslamasabry.oc.core.model.SavedPermission
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SkillInfo
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.core.model.TerminalShellSettings
import io.github.eslamasabry.oc.core.model.VcsDiffMode
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.core.model.WorkspaceAdapterInfo
import io.github.eslamasabry.oc.core.model.WorkspaceInfo
import io.github.eslamasabry.oc.core.model.WorkspaceProject
import io.github.eslamasabry.oc.core.model.WorkspaceSymbol
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * The feature-absent answer every default below gives (the Dart ProductRepository graceful
 * default).
 */
public fun unavailable(feature: String): Outcome<Nothing> =
    Outcome.Failed(Failure.Unavailable(FeatureId(feature)))

/*
 * The product operations surface paired with a ServerGateway (interfaces.dart:252–494). Every method has a default
 * returning Failure.Unavailable naming the ServerCapabilities flag that gates it, so a backend overrides only what
 * it supports. Names in `unavailable("…")` are capability flag names (FeatureId contract).
 */

public interface SessionOperationsGateway {
    public suspend fun backgroundWorkSupport(): Outcome<BackgroundWorkSupport> =
        Outcome.Ok(BackgroundWorkSupport.Unavailable)

    public suspend fun backgroundSession(session: SessionId): Outcome<BackgroundWorkResult> =
        unavailable("experimentalCapabilities")

    public suspend fun sessionDetails(id: SessionId): Outcome<Session> =
        unavailable("projectManagement")

    public suspend fun sessionChildren(id: SessionId): Outcome<ImmutableList<Session>> =
        unavailable("subagentSessions")

    /** The share URL, or null when the server made none. */
    public suspend fun shareSession(id: SessionId): Outcome<String?> = unavailable("sessionShare")

    public suspend fun unshareSession(id: SessionId): Outcome<Unit> = unavailable("sessionShare")

    public suspend fun archiveSession(id: SessionId): Outcome<Unit> = unavailable("sessionArchive")

    public suspend fun forkSession(id: SessionId, from: MessageId? = null): Outcome<SessionId> =
        unavailable("sessionFork")

    public suspend fun deleteMessage(session: SessionId, message: MessageId): Outcome<Unit> =
        unavailable("messageDelete")

    public suspend fun revertSession(id: SessionId, to: MessageId): Outcome<Unit> =
        unavailable("sessionRevert")

    public suspend fun restoreSession(id: SessionId): Outcome<Unit> = unavailable("sessionRevert")

    public suspend fun compactSession(id: SessionId, model: ModelRef): Outcome<Unit> =
        unavailable("sessionCompact")

    public suspend fun addSessionLocationReminder(
        session: SessionId,
        directory: String,
    ): Outcome<Unit> = unavailable("managedWorkspaces")
}

public interface HostGateway {
    /** The version the server will upgrade to. */
    public suspend fun upgradeServer(target: String): Outcome<String> = unavailable("remoteUpgrade")

    /** [extra] must already be redacted; never credentials, prompts or file contents. */
    public suspend fun writeClientLog(
        message: String,
        extra: ImmutableMap<String, String> = persistentMapOf(),
    ): Outcome<Unit> = unavailable("clientDiagnostics")

    public suspend fun listProjects(): Outcome<ImmutableList<WorkspaceProject>> =
        unavailable("projectManagement")

    public suspend fun renameProject(
        projectId: String,
        projectDirectory: String,
        name: String,
    ): Outcome<WorkspaceProject> = unavailable("projectManagement")

    public suspend fun currentProject(): Outcome<WorkspaceProject?> =
        unavailable("projectManagement")

    public suspend fun listProjectDirectories(
        projectId: String
    ): Outcome<ImmutableList<ProjectDirectoryInfo>> = unavailable("projectManagement")

    public suspend fun listWorktrees(
        projectDirectory: String,
        projectId: String? = null,
    ): Outcome<ImmutableList<WorktreeInfo>> = unavailable("projectManagement")

    public suspend fun createWorktree(
        projectDirectory: String,
        name: String? = null,
    ): Outcome<WorktreeInfo> = unavailable("worktreeCreate")

    public suspend fun listWorktreeFileStatuses(
        directory: String
    ): Outcome<ImmutableList<VersionControlFile>> = unavailable("projectManagement")

    public suspend fun resetWorktree(projectDirectory: String, directory: String): Outcome<Unit> =
        unavailable("worktreeReset")

    public suspend fun removeWorktree(projectDirectory: String, directory: String): Outcome<Unit> =
        unavailable("projectManagement")

    public suspend fun listWorkspaces(): Outcome<ImmutableList<WorkspaceInfo>> =
        unavailable("managedWorkspaces")

    public suspend fun listManagedWorkspaces(
        projectDirectory: String
    ): Outcome<ImmutableList<WorkspaceInfo>> = unavailable("managedWorkspaces")

    public suspend fun listWorkspaceAdapters(
        projectDirectory: String
    ): Outcome<ImmutableList<WorkspaceAdapterInfo>> = unavailable("managedWorkspaces")

    public suspend fun syncWorkspaceList(projectDirectory: String): Outcome<Unit> =
        unavailable("managedWorkspaces")

    public suspend fun createManagedWorkspace(
        projectDirectory: String,
        type: String,
        branch: String? = null,
    ): Outcome<WorkspaceInfo> = unavailable("managedWorkspaces")

    public suspend fun removeManagedWorkspace(projectDirectory: String, id: String): Outcome<Unit> =
        unavailable("managedWorkspaces")

    public suspend fun listGlobalSessions(
        search: String? = null,
        includeArchived: Boolean = false,
        cursor: String? = null,
        limit: Int = 50,
    ): Outcome<ServerPage<GlobalSessionResult>> = unavailable("globalSessionSearch")

    public suspend fun moveSession(
        session: SessionId,
        directory: String,
        moveChanges: Boolean,
    ): Outcome<Unit> = unavailable("managedWorkspaces")

    public suspend fun warpSession(
        session: SessionId,
        workspaceId: String?,
        copyChanges: Boolean,
    ): Outcome<Unit> = unavailable("workspaceWarp")

    public suspend fun startWorkspaceSync(): Outcome<Boolean> = unavailable("managedWorkspaces")

    /** The new session id. */
    public suspend fun stealSessionIntoWorkspace(session: SessionId): Outcome<SessionId> =
        unavailable("sessionSteal")

    public suspend fun listConsoleOrganizations(): Outcome<ImmutableList<ConsoleOrganization>> =
        unavailable("consoleOrganizations")

    public suspend fun switchConsoleOrganization(organization: ConsoleOrganization): Outcome<Unit> =
        unavailable("consoleOrganizations")
}

public interface VcsGateway {
    public suspend fun versionControlHealth(): Outcome<VersionControlHealth> =
        unavailable("projectManagement")

    public suspend fun initializeGitRepository(): Outcome<Unit> = unavailable("gitInit")

    public suspend fun listFileStatuses(): Outcome<ImmutableList<VersionControlFile>> =
        unavailable("projectManagement")

    public suspend fun listLanguageServices(): Outcome<ImmutableList<LanguageServiceHealth>> =
        unavailable("languageServiceStatus")

    public suspend fun listFormatters(): Outcome<ImmutableList<FormatterHealth>> =
        unavailable("formatterStatus")

    public suspend fun findWorkspaceSymbols(
        query: String
    ): Outcome<ImmutableList<WorkspaceSymbol>> = unavailable("workspaceSymbols")

    public suspend fun listVcsDiffs(mode: VcsDiffMode): Outcome<ImmutableList<FileDiff>> =
        unavailable("sessionDiff")
}

public interface TerminalGateway {
    public suspend fun listTerminals(): Outcome<ImmutableList<TerminalProcess>> =
        unavailable("terminal")

    public suspend fun terminalShellSettings(): Outcome<TerminalShellSettings> =
        unavailable("shellSettings")

    public suspend fun selectTerminalShell(value: String): Outcome<Unit> =
        unavailable("shellSettings")

    public suspend fun createTerminal(title: String? = null): Outcome<TerminalProcess> =
        unavailable("terminal")

    /**
     * App-owned one-shot readers keep the executable and arguments separate; no shell
     * interpolation.
     */
    public suspend fun createCommandTerminal(
        command: String,
        arguments: List<String>,
        title: String,
    ): Outcome<TerminalProcess> = unavailable("terminal")

    public suspend fun renameTerminal(id: String, title: String): Outcome<Unit> =
        unavailable("terminal")

    public suspend fun resizeTerminal(id: String, rows: Int, cols: Int): Outcome<Unit> =
        unavailable("terminal")

    public suspend fun removeTerminal(id: String): Outcome<Unit> = unavailable("terminal")

    /** Opens the PTY link; [cursor] resumes output where the last channel stopped. */
    public suspend fun connectTerminal(id: String, cursor: Long? = null): Outcome<TerminalChannel> =
        unavailable("terminal")
}

public interface ManagedShellGateway {
    public suspend fun startManagedShell(
        command: String,
        directory: String,
        ownerToken: String,
    ): Outcome<ManagedShell> = unavailable("developmentServices")

    public suspend fun runningShells(): Outcome<ManagedShellList> =
        unavailable("developmentServices")

    public suspend fun managedShell(id: String): Outcome<ManagedShell?> =
        unavailable("developmentServices")

    public suspend fun managedShellOutput(
        id: String,
        cursor: Long,
        limit: Int = 65_536,
    ): Outcome<ManagedShellOutput> = unavailable("developmentServices")

    public suspend fun stopManagedShell(id: String): Outcome<Unit> =
        unavailable("developmentServices")

    /** null [timeout] = no timeout. */
    public suspend fun setManagedShellTimeout(
        id: String,
        timeout: Duration?,
    ): Outcome<ManagedShell> = unavailable("developmentServices")

    public suspend fun managedShellServerIdentity(): Outcome<String?> =
        unavailable("developmentServices")
}

public interface CatalogGateway {
    public suspend fun catalog(): Outcome<CatalogSnapshot> = unavailable("serverCatalog")

    public suspend fun chatDefaults(): Outcome<ChatDefaults> = unavailable("serverCatalog")

    public suspend fun experimentalCapabilities(): Outcome<ExperimentalServerCapabilities> =
        unavailable("experimentalCapabilities")

    public suspend fun codingToolIds(): Outcome<ImmutableList<String>> =
        unavailable("toolInventory")

    public suspend fun codingTools(model: ModelRef): Outcome<ImmutableList<CodingToolInfo>> =
        unavailable("toolInventory")

    public suspend fun commands(): Outcome<ImmutableList<CommandInfo>> =
        unavailable("slashCommands")

    public suspend fun skills(): Outcome<ImmutableList<SkillInfo>> = unavailable("serverCatalog")

    public suspend fun references(): Outcome<ImmutableList<ReferenceInfo>> =
        unavailable("serverCatalog")
}

public interface McpGateway {
    public suspend fun mcpServers(): Outcome<ImmutableList<McpServerInfo>> =
        unavailable("serverCatalog")

    public suspend fun mcpResources(): Outcome<ImmutableList<McpResourceInfo>> =
        unavailable("serverCatalog")

    public suspend fun connectMcp(name: String): Outcome<Unit> = unavailable("serverCatalog")

    public suspend fun disconnectMcp(name: String): Outcome<Unit> = unavailable("serverCatalog")

    public suspend fun startMcpAuthentication(name: String): Outcome<McpAuthLaunch> =
        unavailable("mcpOAuth")

    public suspend fun completeMcpAuthentication(
        name: String,
        code: String,
    ): Outcome<McpServerInfo> = unavailable("mcpOAuth")

    public suspend fun cancelMcpAuthentication(name: String): Outcome<Unit> =
        unavailable("mcpOAuth")

    public suspend fun addMcpServer(draft: McpServerDraft, scope: McpConfigScope): Outcome<Unit> =
        unavailable("mcpConfigWrites")
}

/**
 * Provider integrations. Keys passed here are registered with the Redactor by the caller before the
 * call.
 */
public interface IntegrationGateway {
    public suspend fun integrations(): Outcome<ImmutableList<IntegrationInfo>> =
        unavailable("integrationCredentials")

    public suspend fun connectIntegrationKey(
        id: String,
        key: String,
        label: String? = null,
    ): Outcome<Unit> = unavailable("integrationCredentials")

    public suspend fun disconnectIntegration(integration: IntegrationInfo): Outcome<Unit> =
        unavailable("integrationCredentials")

    public suspend fun refreshProviderRuntime(): Outcome<Unit> =
        unavailable("providerRuntimeRefresh")

    public suspend fun startIntegrationOAuth(
        id: String,
        methodId: String,
        inputs: ImmutableMap<String, String> = persistentMapOf(),
        label: String? = null,
    ): Outcome<IntegrationAuthLaunch> = unavailable("integrationCredentials")

    public suspend fun integrationOAuthStatus(attemptId: String): Outcome<IntegrationAuthStatus> =
        unavailable("integrationCredentials")

    public suspend fun completeIntegrationOAuth(
        attemptId: String,
        code: String? = null,
    ): Outcome<Unit> = unavailable("integrationCredentials")

    public suspend fun cancelIntegrationOAuth(attemptId: String): Outcome<Unit> =
        unavailable("integrationCredentials")
}

/** Question dialogs (location-wide list) and saved permission management. */
public interface RequestGateway {
    public suspend fun questions(): Outcome<ImmutableList<PendingQuestion>> =
        unavailable("legacyQuestionRequests")

    public suspend fun answerQuestion(
        id: String,
        answers: ImmutableList<ImmutableList<String>>,
    ): Outcome<Unit> = unavailable("legacyQuestionRequests")

    public suspend fun rejectQuestion(id: String): Outcome<Unit> =
        unavailable("legacyQuestionRequests")

    public suspend fun savedPermissions(): Outcome<ImmutableList<SavedPermission>> =
        unavailable("savedPermissionList")

    public suspend fun removeSavedPermission(id: String): Outcome<Unit> =
        unavailable("savedPermissionList")
}

/** Everything the product screens drive beyond the live transport. */
public interface ServerOperationsGateway :
    SessionOperationsGateway,
    HostGateway,
    VcsGateway,
    TerminalGateway,
    ManagedShellGateway,
    CatalogGateway,
    McpGateway,
    IntegrationGateway,
    RequestGateway {
    /** Follows the live gateway's location (Dart `setLocation`). */
    public fun setLocation(location: ServerLocation) {}
}
