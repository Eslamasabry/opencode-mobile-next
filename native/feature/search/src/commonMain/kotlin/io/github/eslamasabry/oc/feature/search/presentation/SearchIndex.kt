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
    Privacy,
    Voice,
    Updates,
    About,
    ReportProblem,
    Guide,
    KeepRunning,
    Usage,
    LinuxPorts,
    LinuxPackages,
    AddServer,
    Disconnect,
    Demo,
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
                    // Flutter opens GlobalSessionsScreen: the All conversations page, not the typed search.
                    ChatsRoute.All(),
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
                add("settings-providers", SearchPlace.Providers, LibraryRoute("providers", profileId = profile))
            if (caps.setupConfigRead)
                add("inside-server-ai-setup", SearchPlace.AiSetup, ServersRoute.Setup(profile))
            // F: search_index/places.dart:206 (`hasTeam`): the team page, not the phone's agents.
            if (facts.hasTeam || (platform.builtinLinux && caps.phoneTeamExecution))
                add("ai-team", SearchPlace.Agents, TeamRoute.Home(profile))
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
            // The open conversation's files, else the project's own files with no conversation.
            val files =
                ref?.let { FilesRoute.Folder(it) }
                    ?: directory?.let { FilesRoute.ProjectFolder(profile, it) }
            if (files != null && facts.hasShell && caps.fileBrowsing)
                add("project-files", SearchPlace.ProjectFiles, files)
            if (ref != null && caps.sessionDiff)
                add("project-changes", SearchPlace.ProjectChanges, GitRoute(ref))
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
                if (facts.hasShell && caps.fileBrowsing && caps.textSearch && files != null)
                    add("project-search", SearchPlace.ProjectSearch, files)
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
        // S6: every Settings row and the pages inside them, so Search reaches what walking the hub reaches. Each
        // opens its own page; Settings itself shows a row only when this phone can serve it, and so do these gates.
        add("settings-privacy", SearchPlace.Privacy, SettingsRoute.Target("settings-privacy"))
        if (platform.voice || platform.readAloud)
            add("settings-voice", SearchPlace.Voice, SettingsRoute.Target("settings-voice"))
        if (platform.inAppUpdater || platform.desktopReleaseCheck)
            add("settings-updates", SearchPlace.Updates, SettingsRoute.Target("settings-updates"))
        add("settings-about", SearchPlace.About, SettingsRoute.Target("settings-about"))
        add("settings-report-problem", SearchPlace.ReportProblem, SettingsRoute.ReportProblem)
        add("settings-guide", SearchPlace.Guide, SettingsRoute.Target("settings-guide"))
        if (platform.backgroundService)
            add("settings-background", SearchPlace.KeepRunning, PhoneRoute.KeepRunning)
        add("settings-usage", SearchPlace.Usage, UsageRoute.Hub())
        add("settings-servers-add", SearchPlace.AddServer, ServersRoute.Edit())
        add("inside-server-disconnect", SearchPlace.Disconnect, ServersRoute.Home)
        add("settings-demo", SearchPlace.Demo, DemoRoute)
        if (platform.builtinLinux) {
            add("settings-on-this-phone", SearchPlace.Phone, PhoneRoute.ThisPhone)
            add("inside-phone-running", SearchPlace.Running, PhoneRoute.Running)
            add("inside-phone-storage", SearchPlace.PhoneStorage, PhoneRoute.ManageSpace)
            add("inside-linux-ports", SearchPlace.LinuxPorts, LinuxRoute.Ports)
            add("inside-linux-packages", SearchPlace.LinuxPackages, LinuxRoute.Packages)
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
