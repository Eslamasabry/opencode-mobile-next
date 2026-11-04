package io.github.eslamasabry.oc.feature.search.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.*

/**
 * The label is resolved from native resources by the screen; admission uses confirmed capabilities
 * only.
 */
public enum class SearchPlace {
    Chats,
    Projects,
    ProjectFiles,
    ProjectChanges,
    ProjectTerminal,
    ProjectHealth,
    ProjectWorktrees,
    ProjectSearch,
    AllConversations,
    Settings,
    Models,
    Providers,
    Integrations,
    Commands,
    Tools,
    Skills,
    References,
    AiSetup,
    Agents,
    Phone,
    Running,
    Services,
    PhoneStorage,
    Notifications,
    BackgroundChecks,
    Appearance,
    Language,
    Tips,
    Shortcuts,
}

public data class SearchIndexEntry(
    val id: String,
    val place: SearchPlace,
    val destination: AppRoute,
)

/**
 * B-NAV-058. Every row carries the actual destination it can open; no guessed backend or device
 * names.
 */
public object SearchIndex {
    public fun entries(profile: ProfileId, facts: SearchGoToFacts): List<SearchIndexEntry> =
        buildList {
            val caps = facts.capabilities
            val platform = facts.platform
            val ref = facts.projectRef
            val directory = facts.projectDirectory
            fun add(id: String, place: SearchPlace, route: AppRoute) =
                add(SearchIndexEntry(id, place, route))
            addAll(appEntries(platform, facts.hasShell))
            if (facts.hasShell && caps.projectManagement)
                add("tab-files", SearchPlace.Projects, ProjectsRoute.Picker(profile))
            if (caps.globalSessionSearch)
                add(
                    "all-conversations",
                    SearchPlace.AllConversations,
                    SearchRoute.Conversations(profile),
                )
            if (caps.serverCatalog) {
                add("settings-models", SearchPlace.Models, SettingsRoute.ModelDefaults(profile))
                add("settings-mcp", SearchPlace.Integrations, McpCatalogRoute(profile, directory))
                if (caps.slashCommands)
                    add(
                        "inside-capabilities-commands",
                        SearchPlace.Commands,
                        LibraryRoute("commands", profileId = profile),
                    )
                if (caps.toolInventory)
                    add("inside-capabilities-tools", SearchPlace.Tools, ToolsRoute(profile))
                add(
                    "inside-capabilities-skills",
                    SearchPlace.Skills,
                    LibraryRoute("skills", profileId = profile),
                )
                add(
                    "inside-capabilities-references",
                    SearchPlace.References,
                    LibraryRoute("references", profileId = profile),
                )
            }
            if (caps.agentAccount || caps.serverCatalog)
                add("settings-providers", SearchPlace.Providers, ProvidersRoute(profile))
            if (caps.setupConfigRead)
                add("inside-server-ai-setup", SearchPlace.AiSetup, ServersRoute.Setup(profile))
            if (platform.builtinLinux && caps.phoneTeamExecution)
                add("ai-team", SearchPlace.Agents, AgentsRoute.Home)
            if (caps.profileAttentionPolling)
                add(
                    "inside-notifications-servers",
                    SearchPlace.BackgroundChecks,
                    SettingsRoute.Target("notifications-servers"),
                )
            if (platform.builtinLinux && facts.managedOnPhone)
                add("inside-linux-services", SearchPlace.Services, LinuxRoute.KeptServices)
            if (caps.terminal)
                add("project-terminal", SearchPlace.ProjectTerminal, RemoteTerminalRoute(profile))
            if (ref != null) {
                if (facts.hasShell && caps.fileBrowsing)
                    add("project-files", SearchPlace.ProjectFiles, FilesRoute.Folder(ref))
                if (caps.sessionDiff)
                    add("project-changes", SearchPlace.ProjectChanges, GitRoute(ref))
            }
            if (directory != null) {
                val workspace = ProjectsRoute.Project(profile, directory, initialWorkspace = true)
                if (caps.languageServiceStatus || caps.formatterStatus)
                    add(
                        "project-health",
                        SearchPlace.ProjectHealth,
                        SearchRoute.ProjectHealth(profile, directory),
                    )
                if (caps.worktreeBrowsing)
                    add("project-worktrees", SearchPlace.ProjectWorktrees, workspace)
                if (facts.hasShell && caps.fileBrowsing && caps.textSearch && ref != null)
                    add("project-search", SearchPlace.ProjectSearch, FilesRoute.Folder(ref))
            }
        }

    public fun appEntries(
        platform: io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities,
        hasShell: Boolean,
    ): List<SearchIndexEntry> = buildList {
        fun add(id: String, place: SearchPlace, route: AppRoute) =
            add(SearchIndexEntry(id, place, route))
        if (hasShell) {
            add("tab-chats", SearchPlace.Chats, ChatsRoute.Home)
            add("tab-settings", SearchPlace.Settings, SettingsRoute.Home)
        }
        add(
            "settings-category-appearance",
            SearchPlace.Appearance,
            SettingsRoute.Target("settings-appearance"),
        )
        add(
            "inside-appearance-language",
            SearchPlace.Language,
            SettingsRoute.Target("settings-language"),
        )
        add("help-tips", SearchPlace.Tips, TeachingRoute.Tips)
        if (platform.builtinLinux) {
            add("settings-on-this-phone", SearchPlace.Phone, PhoneRoute.ThisPhone)
            add("inside-phone-running", SearchPlace.Running, PhoneRoute.Running)
            add("inside-phone-storage", SearchPlace.PhoneStorage, PhoneRoute.ManageSpace)
        }
        if (platform.notifications)
            add(
                "inside-notifications-quiet",
                SearchPlace.Notifications,
                SettingsRoute.Target("settings-notifications"),
            )
        if (platform.desktopReleaseCheck)
            add("library-keyboard-shortcuts", SearchPlace.Shortcuts, SearchRoute.Shortcuts)
    }

    /** Title matches lead aliases, preserving the stable catalog order within each group. */
    public fun find(
        query: String,
        entries: List<SearchIndexEntry>,
        words: (SearchPlace) -> Pair<String, String>,
    ): List<SearchIndexEntry> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        fun matches(text: String): Boolean = terms.all { text.lowercase().contains(it) }
        return entries.filter { matches(words(it.place).first) } +
            entries.filter {
                !matches(words(it.place).first) &&
                    matches(words(it.place).let { it.first + " " + it.second })
            }
    }
}
