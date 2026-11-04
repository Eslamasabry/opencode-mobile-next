package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities

/** Stable user-facing feature identities, independent of any backend name. */
public enum class ServerFeatureId(public val wire: String) {
    Files("files"),
    Changes("changes"),
    Terminal("terminal"),
    Shell("shell"),
    DevServices("dev-services"),
    Projects("projects"),
    Worktrees("worktrees"),
    CloudEnvironments("cloud-environments"),
    Attachments("attachments"),
    WebSearch("web-search"),
    Subagents("subagents"),
    Compact("compact"),
    Share("share"),
    Fork("fork"),
    Revert("revert"),
    Archive("archive"),
    DeleteMessage("delete-message"),
    Todos("todos"),
    Notes("notes"),
    ImportExport("import-export"),
    AllConversations("all-conversations"),
    AlwaysAllow("always-allow"),
    SavedPermissions("saved-permissions"),
    Approvals("approvals"),
    SendLater("send-later"),
    SendWhileWorking("send-while-working"),
    ContinueOnComputer("continue-on-computer"),
    Models("models"),
    Skills("skills"),
    Mcp("mcp"),
    McpAdd("mcp-add"),
    Plugins("plugins"),
    Usage("usage"),
    ServerUpdates("server-updates"),
    BackgroundNotifications("background-notifications"),
    OnThisPhone("on-this-phone"),
    Voice("voice"),
}

public data class ServerFeatureUi(
    val id: ServerFeatureId,
    val available: Boolean,
    val device: Boolean,
    val capability: String?,
)

public data class ServerCapabilitiesUi(val serverName: String, val features: List<ServerFeatureUi>)

public object ServerFeatures {
    public fun project(
        name: String,
        s: ServerCapabilities,
        p: PlatformCapabilities,
        usage: Boolean,
    ): ServerCapabilitiesUi =
        ServerCapabilitiesUi(
            name,
            listOf(
                ServerFeatureUi(
                    ServerFeatureId.Files,
                    s.fileBrowsing,
                    false,
                    "flag:fileBrowsing+terminal",
                ),
                ServerFeatureUi(
                    ServerFeatureId.Changes,
                    s.sessionDiff || s.fileBrowsing,
                    false,
                    "flag:sessionDiff",
                ),
                ServerFeatureUi(
                    ServerFeatureId.Terminal,
                    s.terminal,
                    false,
                    "flag:fileBrowsing+terminal",
                ),
                ServerFeatureUi(ServerFeatureId.Shell, s.shellSettings, false, null),
                ServerFeatureUi(
                    ServerFeatureId.DevServices,
                    s.developmentServices,
                    false,
                    "flag:developmentServices",
                ),
                ServerFeatureUi(ServerFeatureId.Projects, s.projectManagement, false, null),
                ServerFeatureUi(
                    ServerFeatureId.Worktrees,
                    s.projectManagement || s.worktreeBrowsing,
                    false,
                    "flag:worktreeCreate+sessionShare+managedWorkspaces",
                ),
                ServerFeatureUi(
                    ServerFeatureId.CloudEnvironments,
                    s.managedWorkspaces,
                    false,
                    "flag:worktreeCreate+sessionShare+managedWorkspaces",
                ),
                ServerFeatureUi(
                    ServerFeatureId.Attachments,
                    s.promptAttachments,
                    false,
                    "flag:promptAttachments",
                ),
                ServerFeatureUi(ServerFeatureId.WebSearch, s.webSearch, false, null),
                ServerFeatureUi(ServerFeatureId.Subagents, s.promptAgentMentions, false, null),
                ServerFeatureUi(ServerFeatureId.Compact, s.sessionCompact, false, null),
                ServerFeatureUi(
                    ServerFeatureId.Share,
                    s.sessionShare,
                    false,
                    "flag:worktreeCreate+sessionShare+managedWorkspaces",
                ),
                ServerFeatureUi(ServerFeatureId.Fork, s.sessionFork, false, null),
                ServerFeatureUi(
                    ServerFeatureId.Revert,
                    s.sessionRevert,
                    false,
                    "flag:stagedRevert+sessionNotes",
                ),
                ServerFeatureUi(ServerFeatureId.Archive, s.sessionArchive, false, null),
                ServerFeatureUi(ServerFeatureId.DeleteMessage, s.messageDelete, false, null),
                ServerFeatureUi(ServerFeatureId.Todos, s.sessionTodos, false, null),
                ServerFeatureUi(
                    ServerFeatureId.Notes,
                    s.sessionNotes,
                    false,
                    "flag:stagedRevert+sessionNotes",
                ),
                ServerFeatureUi(ServerFeatureId.ImportExport, s.sessionImportExport, false, null),
                ServerFeatureUi(
                    ServerFeatureId.AllConversations,
                    s.globalSessionSearch,
                    false,
                    null,
                ),
                ServerFeatureUi(
                    ServerFeatureId.AlwaysAllow,
                    s.persistentPermissionGrants,
                    false,
                    null,
                ),
                ServerFeatureUi(
                    ServerFeatureId.SavedPermissions,
                    s.savedPermissionList,
                    false,
                    null,
                ),
                ServerFeatureUi(ServerFeatureId.Approvals, s.permissionRequests, false, null),
                ServerFeatureUi(ServerFeatureId.SendLater, s.offlinePromptQueue, false, null),
                ServerFeatureUi(ServerFeatureId.SendWhileWorking, s.inbox, false, null),
                ServerFeatureUi(
                    ServerFeatureId.ContinueOnComputer,
                    s.cliSessionResume,
                    false,
                    null,
                ),
                ServerFeatureUi(
                    ServerFeatureId.Models,
                    s.serverCatalog,
                    false,
                    "flag:serverCatalog",
                ),
                ServerFeatureUi(
                    ServerFeatureId.Skills,
                    s.serverCatalog,
                    false,
                    "flag:serverCatalog",
                ),
                ServerFeatureUi(ServerFeatureId.Mcp, s.serverCatalog, false, "flag:serverCatalog"),
                ServerFeatureUi(
                    ServerFeatureId.McpAdd,
                    s.mcpConfigWrites || s.mcpRuntimeAdds,
                    false,
                    null,
                ),
                ServerFeatureUi(ServerFeatureId.Plugins, s.pluginInventory, false, null),
                ServerFeatureUi(ServerFeatureId.Usage, usage, false, "flag:usageStatistics"),
                ServerFeatureUi(
                    ServerFeatureId.ServerUpdates,
                    s.remoteUpgrade,
                    false,
                    "flag:remoteUpgrade",
                ),
                ServerFeatureUi(
                    ServerFeatureId.BackgroundNotifications,
                    p.notifications && p.backgroundService,
                    true,
                    null,
                ),
                ServerFeatureUi(ServerFeatureId.OnThisPhone, p.builtinLinux, true, null),
                ServerFeatureUi(ServerFeatureId.Voice, p.voice, true, null),
            ),
        )
}
