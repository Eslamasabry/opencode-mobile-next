part of '../search_index.dart';

extension _IndexPlaces on _IndexBuild {
  List<SearchEntry> places() {
    return [
      // ---- Places ------------------------------------------------------
      SearchEntry(
        id: 'tab-chats',
        kind: SearchEntryKind.destination,
        icon: AppIconography.chat,
        title: l10n.shellTabChats,
        keywords: l10n.discoverChatsAliases,
        pages: const ['chats-home'],
        gate: (scope) => scope.hasShell,
        open: _shell(const SelectDestinationIntent(0)),
      ),
      SearchEntry(
        id: 'tab-files',
        kind: SearchEntryKind.destination,
        icon: AppIconography.files,
        title: l10n.shellTabFiles,
        keywords: l10n.discoverProjectAliases,
        pages: const ['project-hub'],
        gate: (scope) =>
            scope.hasShell && ProjectHub.isAvailable(scope.capabilities),
        open: _shell(const SelectDestinationIntent(1)),
      ),
      SearchEntry(
        id: 'tab-settings',
        kind: SearchEntryKind.destination,
        icon: AppIconography.settings,
        title: l10n.librarySettingsTitle,
        keywords: '',
        pages: const ['settings'],
        gate: (scope) => scope.hasShell,
        open: _shell(const SelectDestinationIntent(2)),
      ),
      SearchEntry(
        id: 'project-files',
        kind: SearchEntryKind.destination,
        icon: AppIconography.files,
        title: l10n.readerUiFiles,
        parent: l10n.shellTabFiles,
        keywords: l10n.discoverFilesAliases,
        pages: const ['files'],
        // Files lives inside the Project tab, so it needs the shell too.
        gate: (scope) =>
            scope.hasShell && _hasProjectTool(scope, ProjectTool.files),
        open: _shell(const OpenProjectToolIntent(ProjectTool.files)),
      ),
      SearchEntry(
        id: 'project-changes',
        kind: SearchEntryKind.destination,
        icon: AppIconography.review,
        title: l10n.readerUiChanges,
        parent: l10n.shellTabFiles,
        keywords: '${l10n.discoverChangesAliases} ${l10n.demoReviewChanges}',
        pages: const ['review-workspace'],
        gate: (scope) => _hasProjectTool(scope, ProjectTool.changes),
        open: _projectTool(ProjectTool.changes),
      ),
      SearchEntry(
        id: 'project-terminal',
        kind: SearchEntryKind.destination,
        icon: AppIconography.terminal,
        title: l10n.libraryTerminalTitle,
        parent: l10n.shellTabFiles,
        keywords: l10n.discoverTerminalAliases,
        pages: const ['terminal'],
        gate: (scope) => _hasProjectTool(scope, ProjectTool.terminal),
        open: _projectTool(ProjectTool.terminal),
      ),
      SearchEntry(
        id: 'project-health',
        kind: SearchEntryKind.destination,
        icon: AppIconography.diagnostics,
        title: l10n.e7LibraryProjectHealth,
        parent: l10n.shellTabFiles,
        keywords: l10n.discoverHealthAliases,
        pages: const ['project-health'],
        gate: (scope) => _hasProjectTool(scope, ProjectTool.health),
        open: _projectTool(ProjectTool.health),
      ),
      SearchEntry(
        id: 'project-worktrees',
        kind: SearchEntryKind.destination,
        icon: AppIconography.branch,
        title: l10n.e7LibraryWorktrees,
        parent: l10n.shellTabFiles,
        keywords: l10n.discoverWorktreesAliases,
        pages: const ['worktrees'],
        gate: (scope) => _hasProjectTool(scope, ProjectTool.worktrees),
        open: _projectTool(ProjectTool.worktrees),
      ),
      // Development services and cloud environments moved to the Project tab
      // with the retired Manage project page (slice-P3.11a).
      SearchEntry(
        id: 'project-services',
        kind: SearchEntryKind.destination,
        icon: AppIconography.processor,
        title: l10n.servicesTitle,
        parent: l10n.shellTabFiles,
        keywords: l10n.discoverServicesAliases,
        pages: const ['development-services'],
        gate: (scope) => _hasProjectTool(scope, ProjectTool.services),
        open: _projectTool(ProjectTool.services),
      ),
      SearchEntry(
        id: 'project-workspaces',
        kind: SearchEntryKind.destination,
        icon: AppIconography.cloud,
        title: l10n.e7LibraryManagedWorkspaces,
        parent: l10n.shellTabFiles,
        keywords: l10n.discoverCloudEnvironmentsAliases,
        pages: const ['managed-workspaces'],
        gate: (scope) => _hasProjectTool(scope, ProjectTool.workspaces),
        open: _projectTool(ProjectTool.workspaces),
      ),
      SearchEntry(
        id: 'project-search',
        kind: SearchEntryKind.destination,
        icon: AppIconography.search,
        title: l10n.readerUiSearchFiles,
        parent: l10n.shellTabFiles,
        keywords: l10n.discoverSearchFilesAliases,
        pages: const ['files'],
        gate: (scope) =>
            scope.hasShell && _hasProjectTool(scope, ProjectTool.search),
        open: _shell(const OpenProjectToolIntent(ProjectTool.search)),
      ),
      SearchEntry(
        id: 'all-conversations',
        kind: SearchEntryKind.destination,
        icon: AppIconography.searchList,
        title: l10n.globalSessionsTitle,
        parent: l10n.shellTabChats,
        keywords: l10n.discoverAllConversationsAliases,
        pages: const ['global-sessions'],
        gate: (scope) =>
            scope.controller.isConnected &&
            scope.capabilities.globalSessionSearch,
        open: _screen(
          (scope) => GlobalSessionsScreen(controller: scope.controller),
        ),
      ),
      // Archived conversations are a filter of All conversations (P3.12);
      // this opens it with that filter on.
      SearchEntry(
        id: 'archived-conversations',
        kind: SearchEntryKind.destination,
        icon: AppIconography.archive,
        title: l10n.searchArchivedConversations,
        parent: l10n.globalSessionsTitle,
        keywords: l10n.globalSessionsArchivedShort,
        pages: const ['global-sessions'],
        gate: (scope) =>
            scope.controller.isConnected &&
            scope.capabilities.globalSessionSearch,
        open: _screen(
          (scope) => GlobalSessionsScreen(
            controller: scope.controller,
            archived: true,
          ),
        ),
      ),
      SearchEntry(
        id: 'ai-team-agents',
        kind: SearchEntryKind.destination,
        icon: AppIconography.agent,
        title: l10n.teamRolesTitle,
        // Settings › AI Team › Agents: the team's roles.
        parent: l10n.librarySettingsTitle,
        keywords: l10n.teamRolesSearchAliases,
        pages: const ['team-agents'],
        gate: (scope) => scope.hasTeam,
        open: (context, scope) {
          final team = scope.controller.orchestration;
          if (team == null) return openTeamPage(context, scope.controller);
          final projects = team.projectController;
          if (team.capabilities.projectLifecycle && projects != null) {
            return openTeamRoles(context, projects);
          }
          return pushKitPage<void>(
            context,
            (_) => TeamAgentsScreen(
              controller: team,
              connection: scope.controller,
            ),
          );
        },
      ),
      SearchEntry(
        id: 'ai-team-project-demo',
        kind: SearchEntryKind.destination,
        icon: AppIconography.agent,
        title: l10n.teamProjectTryDemo,
        keywords: l10n.teamProjectDemoDisclosure,
        pages: const ['team-project-demo'],
        open: _screen(
          (scope) =>
              TeamProjectDemoScreen(preferences: scope.controller.store.prefs),
        ),
      ),
      SearchEntry(
        id: 'ai-team',
        kind: SearchEntryKind.destination,
        icon: AppIconography.agent,
        title: l10n.teamUiHomeTitle,
        // Settings › AI Team, no longer a Work section.
        parent: l10n.librarySettingsTitle,
        keywords: l10n.discoverTeamAliases,
        pages: const ['team-home', 'team-projects'],
        // Not in the index at all while the server has no plugin config.
        gate: (scope) => scope.hasTeam,
        open: (context, scope) => openTeamPage(context, scope.controller),
      ),
    ];
  }
}
