part of '../workspace_screen.dart';

/// Opening, creating and navigating from the Work tab.
extension _WorkspaceActions on _WorkspaceScreenState {
  /// The one status line, in priority order: server not answering (inside
  /// [WorkStatusLine]), the project list, the waiting requests, a leftover
  /// process on the phone, a location notice.
  Widget _statusLine(BuildContext context, AppLocalizations l10n) {
    final controller = widget.controller;
    final capabilities = controller.capabilities;
    Widget line(WorkRunawayNotice? runaway) {
      final stale =
          controller.isConnected &&
          (controller.permissionsError != null ||
              controller.questionsError != null ||
              (capabilities.forms && controller.formsError != null));
      final notice = controller.locationNotice;
      return WorkStatusLine(
        controller: controller,
        serverOnThisPhone: widget.serverOnThisPhone,
        onRestartServer: widget.onRestartServer,
        others: [
          // A failed load is a failure, never the "needs you" amber
          // (LOOK-4).
          if (capabilities.projectManagement && _projectError != null)
            WorkStatus(
              id: 'projects',
              icon: AppIconography.warning,
              tone: AppStatusTone.failure,
              message: l10n.workspaceProjectListUnavailable,
              action: KitAction(
                key: const ValueKey('work-status-projects-retry'),
                label: l10n.workspaceRetryProjects,
                onPressed: _load,
              ),
              more: [
                if (capabilities.globalSessionSearch)
                  KitAction(
                    label: l10n.workspaceSearchAllSessions,
                    onPressed: _openAllSessions,
                  ),
              ],
            ),
          if (capabilities.projectManagement && _workspaceError != null)
            WorkStatus(
              id: 'workspaces',
              icon: AppIconography.warning,
              tone: AppStatusTone.failure,
              message: _workspaceError!,
              action: KitAction(
                label: l10n.commonRetry,
                onPressed: _loadWorkspaces,
              ),
            ),
          if (stale)
            WorkStatus(
              id: 'stale',
              message: l10n.workStale,
              action: KitAction(
                key: const ValueKey('work-status-stale-refresh'),
                label: l10n.workRefresh,
                onPressed: () => unawaited(_refreshRequests()),
              ),
            ),
          runaway?.status(
            l10n,
            // Every process on the phone, where the rest can be stopped.
            onSeeRunning: () => unawaited(
              pushKitPage<void>(context, (_) => const TermuxProcessesScreen()),
            ),
          ),
          if (notice != null)
            WorkStatus(
              id: 'notice',
              message: notice,
              messageKey: const ValueKey('location-recovery-notice'),
              onDismiss: controller.dismissLocationNotice,
            ),
        ],
      );
    }

    final debugWatcher = WorkspaceScreen.debugRunawayWatcher;
    if (debugWatcher != null) {
      return debugWatcher(context, (context, notice) => line(notice));
    }
    // A leftover helper burning CPU on the phone's own server is watched
    // only where there is a phone server to watch (TEAM-305).
    if (platformCapabilities.supportsTermux &&
        TermuxBridge.managesServerUrl(controller.profile?.baseUrl)) {
      return TermuxRunawayWatcher(builder: (context, notice) => line(notice));
    }
    return line(null);
  }

  Future<void> _refreshRequests() async {
    final controller = widget.controller;
    await Future.wait<void>([
      controller.refreshPendingPermissions(),
      controller.refreshPendingQuestions(),
      if (controller.capabilities.forms) controller.refreshPendingForms(),
    ]);
  }

  /// Opens [session]: beside the list from expanded, else as a page.
  /// Opens [session]: a conversation that needs you lands on the waiting
  /// request's card (P4.2a, from Work too); [action] is a conversation-menu
  /// pick the chat runs once open (P10.2, the same menu as the chat's).
  void _openSession(Session session, {SessionMenuAction? action}) {
    // Speed contract item 2: the chat joins this history read (one call).
    unawaited(widget.controller.prefetchSessionTail(session.id));
    final controller = widget.controller;
    final waiting = action != null
        ? null
        : controller.permissionForSession(session.id)?.id ??
              controller.questionForSession(session.id)?.id ??
              controller.formForSession(session.id)?.id;
    if (KitScreen.showsDetail(context)) {
      _set(() {
        _selectedSessionID = session.id;
        _detailRequestID = waiting;
        _detailAction = action;
        _detailOpen++;
      });
      return;
    }
    Navigator.of(context).pushNamed(
      '/chat/${session.id}',
      arguments: ChatRouteArguments(
        landOnRequestID: waiting,
        menuAction: action,
      ),
    );
  }

  /// A Work row's conversation-menu pick: the acts this page can do alone
  /// (Details, Share, Stop sharing, Rename) run here; the rest open the
  /// conversation and run there.
  void _sessionMenuAction(Session session, SessionMenuAction action) {
    switch (action) {
      case SessionMenuAction.details:
        unawaited(_openSessionContext(session));
      case SessionMenuAction.share:
        unawaited(_share(session));
      case SessionMenuAction.unshare:
        unawaited(_unshare(session));
      case SessionMenuAction.rename:
        unawaited(_rename(session));
      default:
        _openSession(session, action: action);
    }
  }

  /// Asks once per server whether it can run a team; the answer shows or
  /// hides the Solo · Team choice.
  bool _teamPossibleNow() {
    final profile = widget.controller.profile;
    if (profile == null) return false;
    if (widget.controller.orchestration != null) return true;
    final asked = '${profile.id}|${profile.baseUrl}';
    if (_teamAskedFor != asked) {
      _teamAskedFor = asked;
      _teamPossible = false;
      unawaited(() async {
        final possible = await teamPossibleOn(profile);
        if (!mounted || _teamAskedFor != asked) return;
        if (possible != _teamPossible) _set(() => _teamPossible = possible);
      }());
    }
    return _teamPossible;
  }

  /// What New conversation can offer here: Team where a team can run,
  /// a separate copy where the server makes worktrees of this project, and
  /// the project's other cloud machines.
  NewConversationOptions _newConversationOptions() {
    final project = _isolatedTaskProject;
    final current = _selectedProject;
    return NewConversationOptions(
      project:
          current?.name ??
          (_selectedDirectory == null
              ? null
              : _WorkspaceScreenState._basename(_selectedDirectory!)),
      team: _teamPossibleNow(),
      teamOn: widget.controller.orchestration != null,
      separateCopy: project != null,
      clouds: [
        for (final workspace in _workspaces)
          if (workspace.id != _selectedWorkspaceID)
            NewConversationCloud(
              id: workspace.id,
              name: _WorkspaceScreenState._workspaceName(workspace),
              status: workspace.status,
            ),
      ],
    );
  }

  /// The one New conversation: asks how to start where there is more than
  /// one way, remembers the answer for this server, and starts it. Every
  /// start ends in a conversation (or, for a team that is off, the team's
  /// off state, where it is set up).
  Future<void> _newConversation() async {
    if (_creating) return;
    final options = _newConversationOptions();
    if (options.onlySolo) {
      await _createSession();
      return;
    }
    final profile = widget.controller.profile;
    final prefs = widget.controller.store.prefs;
    final choice = await showNewConversationSheet(
      context,
      options: options,
      remembered: profile == null
          ? null
          : NewConversationMemory.read(prefs, profile.id),
    );
    if (!mounted || choice == null) return;
    if (profile != null) {
      await NewConversationMemory.remember(prefs, profile.id, choice);
    }
    if (!mounted) return;
    switch (choice.kind) {
      case NewConversationKind.solo:
        await _createSession();
      case NewConversationKind.team:
        await _createTeamTask();
      case NewConversationKind.separateCopy:
        await _startIsolatedTask();
      case NewConversationKind.cloud:
        await _createOnCloud(choice.workspaceId!);
    }
  }

  /// Moves to the cloud machine and starts the conversation there.
  Future<void> _createOnCloud(String workspaceId) async {
    WorkspaceInfo? target;
    for (final workspace in _workspaces) {
      if (workspace.id == workspaceId) target = workspace;
    }
    if (target == null) return;
    await _selectWorkspace(target);
    if (!mounted) return;
    // The move failed (and said so): nothing starts in the wrong place.
    if (widget.controller.locationError != null ||
        _selectedWorkspaceID != workspaceId) {
      return;
    }
    await _createSession();
  }

  /// New team task: the team's conversation when it is on, else the team
  /// page, which sets it up for this kind of server while it is off.
  Future<void> _createTeamTask() async {
    final team = widget.controller.orchestration;
    if (team == null || team.projectController != null) {
      await openTeamPage(context, widget.controller);
    } else {
      await TeamConversation.start(context, team);
    }
  }

  /// The AI Team strip: "AI Team · 2 working · 1 needs you" (counts left out
  /// at zero, "nothing running" when idle) over up to three of its most
  /// urgent tasks. The header opens the team page; a task opens its
  /// conversation.
  Widget _teamStrip(OrchestrationController team, AppLocalizations l10n) {
    final projects = team.projectController;
    if (team.capabilities.projectLifecycle && projects != null) {
      return TeamProjectStrip(
        controller: projects,
        onOpen: (id) => unawaited(
          pushKitPage<void>(
            context,
            (_) =>
                TeamProjectsScreen(controller: projects, initialProjectId: id),
          ),
        ),
      );
    }
    final glance = teamGlanceFromSnapshot(team.snapshot);
    final open = teamOpenTasks(team);
    final title = glance.isIdle
        ? l10n.teamStripIdle
        : [
            l10n.teamStripTitle,
            if (glance.working > 0) l10n.teamStripWorking(glance.working),
            if (glance.needsYou > 0) l10n.teamStripNeedsYou(glance.needsYou),
          ].join(' · ');
    return KitRowGroup(
      key: const ValueKey('work-team-strip'),
      leadingIcons: false,
      gapBefore: 0,
      children: [
        KitRow(
          key: const ValueKey('work-team-strip-header'),
          title: title,
          onTap: () => unawaited(openTeamPage(context, widget.controller)),
        ),
        for (final task in glance.top)
          for (final run in open)
            if (run.id == task.id)
              TeamTaskRow(
                key: ValueKey('team-work-${run.id}'),
                team: team,
                run: run,
                connected: widget.controller.isConnected,
                onOpen: () => _openTeamTask(team, run.id),
              ),
      ],
    );
  }

  void _openTeamTask(OrchestrationController team, String runId) =>
      unawaited(TeamConversation.open(context, team, runId: runId));

  Future<void> _openAllSessions() => pushKitPage<void>(
    context,
    (_) => GlobalSessionsScreen(controller: widget.controller),
  );

  Future<void> _createProjectFolder() async {
    final path = await ProjectFolderActions.createFolder(
      context,
      widget.controller,
      suggestedName: ProjectFolderActions.suggestedName(_projects),
    );
    if (path != null && mounted) await _load();
  }

  Future<void> _openProjectFolder() async {
    final path = await ProjectFolderActions.openFolder(
      context,
      widget.controller,
    );
    if (path != null && mounted) await _load();
  }

  Future<void> _openProjects() async {
    await pushKitPage<bool>(
      context,
      (_) => ProjectsScreen(
        controller: widget.controller,
        selectedProjectID: _selectedProjectID,
      ),
    );
    if (mounted) await _load();
  }
}
