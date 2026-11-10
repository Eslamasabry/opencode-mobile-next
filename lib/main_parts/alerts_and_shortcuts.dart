part of '../main.dart';

extension _OcAppAlertsAndShortcuts on _OcAppState {
  void _scheduleCodingAlertRoute() {
    if (_codingAlertRouteScheduled ||
        _controller.pendingCodingAlertOpen == null ||
        (_controller.pendingCodingAlertOpen?.monitorToken.isEmpty != false &&
            !_controller.hasConnectedServer)) {
      return;
    }
    _codingAlertRouteScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _codingAlertRouteScheduled = false;
      if (!mounted) return;
      final navigator = _navigatorKey.currentState;
      if (navigator == null) {
        _scheduleCodingAlertRoute();
        return;
      }
      final target = _controller.takePendingCodingAlertOpen();
      if (target == null) return;
      if (target.kind.isTeam) {
        // AI Team alerts (TEAM-203) carry a gate or run id and the saved
        // server's id, nothing else; they route like the team deep link.
        final link = TeamLink.tryCreate(
          kind:
              (target.kind == CodingAlertKind.teamCompleted ||
                  target.kind == CodingAlertKind.teamProgress)
              ? TeamLinkKind.run
              : TeamLinkKind.gate,
          profileId: target.profileID.isEmpty
              ? _controller.profile?.id
              : target.profileID,
          id: target.sessionID,
        );
        if (link != null) unawaited(_openTeamLink(navigator, link));
        return;
      }
      if (target.kind == CodingAlertKind.quota ||
          target.kind == CodingAlertKind.quotaReset) {
        unawaited(
          (_controller.usageSubscriptions.ownsAlertToken(
                    target.profileID,
                    target.monitorToken,
                  )
                  ? Future<Object?>.value(true)
                  : _controller.quotaMonitor.resolveRoute(
                      target.profileID,
                      target.monitorToken,
                    ))
              .then((route) {
                if (!mounted) {
                  return;
                }
                if (route == null) {
                  _say(
                    lookupAppLocalizations(
                      Localizations.localeOf(navigator.context),
                    ).quotaMonitorSourceChanged,
                  );
                  return;
                }
                navigator.push(
                  KitPageRoute<void>(
                    // Quota monitoring is part of Usage → Remaining.
                    builder: (_) => UsageHubScreen(
                      controller: _controller,
                      initialSection: UsageSection.plans,
                    ),
                  ),
                );
              }),
        );
        return;
      }
      if (target.monitorToken.isNotEmpty) {
        final route = _controller.profileMonitor.routeForToken(
          target.profileID,
          target.monitorToken,
        );
        if (route != null && route.sessionID == target.sessionID) {
          unawaited(
            openMonitoredRequest(navigator.context, _controller, route),
          );
        } else {
          _say(
            lookupAppLocalizations(
              Localizations.localeOf(navigator.context),
            ).monitorOpenFailed,
          );
        }
        return;
      }
      // A question lands on its card in its conversation: answering is
      // inline, the same as a permission.
      if (target.kind == CodingAlertKind.question) {
        final waiting = _controller.questions.values
            .where((question) => question.sessionID == target.sessionID)
            .firstOrNull;
        _pushAlertChat(
          navigator,
          target.sessionID,
          landOnRequestID: waiting?.id,
        );
        return;
      }
      if (target.kind == CodingAlertKind.complete &&
          target.profileID.isNotEmpty) {
        // A home-screen widget row may be a conversation of another project
        // or connection: open it the way the Conversations list does first.
        unawaited(
          _controller.openWidgetConversation(target.sessionID).then((opened) {
            if (!mounted) return;
            if (opened) {
              _pushAlertChat(
                navigator,
                target.sessionID,
                focusComposer: target.reply,
              );
            } else {
              // Not found (deleted, or its connection is gone): the
              // Conversations screen, never a broken chat.
              navigator.pushNamedAndRemoveUntil('/home', (_) => false);
            }
          }),
        );
        return;
      }
      _pushAlertChat(navigator, target.sessionID, focusComposer: target.reply);
    });
  }

  /// Opens the chat a notification names, unless that chat is already the
  /// page showing: a second copy would make Back reveal the same chat again
  /// and read as a dead end. The request it names is a card in that chat.
  void _pushAlertChat(
    NavigatorState navigator,
    String sessionID, {
    String? landOnRequestID,
    bool focusComposer = false,
  }) {
    final route = chatLandingRoute(
      sessionID: sessionID,
      landOnRequestID: landOnRequestID,
      focusComposer: focusComposer,
    );
    if (_routeTracker.topName == '/chat/$sessionID') {
      // Already showing. A widget's Reply still lands ready to type: the
      // same chat replaces itself with its composer focused.
      if (focusComposer) unawaited(navigator.pushReplacement(route));
      return;
    }
    unawaited(navigator.push(route));
  }

  // ------------------------------------------------------------------
  // Desktop shortcut layer (no-op on Android: AppShortcuts passes through).
  // ------------------------------------------------------------------

  Future<void> _startNewSession() async {
    final navigator = _navigatorKey.currentState;
    if (navigator == null) return;
    try {
      final session = await _controller.createSession();
      await navigator.pushNamed(
        '/chat/${session.id}',
        arguments: const ChatRouteArguments.newlyCreated(),
      );
    } catch (error) {
      if (!mounted) return;
      _say(
        AppLocalizations.of(navigator.context).appNewConversationFailed,
        supporting: productErrorText(error),
        failed: true,
      );
    }
  }

  void _openSettings() {
    _navigatorKey.currentState?.push(
      KitPageRoute<void>(
        builder: (_) => SettingsScreen(controller: _controller),
      ),
    );
  }

  List<DesktopCommand> _shellCommands(BuildContext context) {
    final mod = shortcutModifierLabel;
    final l10n = AppLocalizations.of(context);
    void go(int index) {
      final navigator = _navigatorKey.currentState;
      if (navigator == null) return;
      dispatchAtShellRoot(
        navigator,
        _shortcutSignals,
        SelectDestinationIntent(index),
      );
    }

    final scope = SearchScope(
      controller: _controller,
      hasShell: true,
      thermalGuard: ref.read(thermalGuardSlotProvider).value != null,
    );
    return [
      DesktopCommand(
        label: l10n.e7LocaleUiNewSession,
        icon: Icons.add_rounded,
        hint: l10n.e7LocaleUiNewSessionHint,
        keys: '$mod + N',
        onInvoke: () => unawaited(_startNewSession()),
      ),
      DesktopCommand(
        label: l10n.shellTabChats,
        icon: Icons.workspaces_outline,
        hint: l10n.e7LocaleUiWorkspaceHint,
        keys: '$mod + 1',
        onInvoke: () => go(0),
      ),
      // Search's own empty state (mockup 6): Files, Projects, Settings,
      // Servers, Terminal, Scheduled prompts, the AI Team and Help where this
      // connection has them, then the last three conversations. Files and
      // Settings carry $mod + 2 and 3; "$mod + ," still opens Settings over
      // the current screen.
      ...goToCommands(
        l10n,
        controller: _controller,
        mod: mod,
        target: () => _navigatorKey.currentState?.overlay?.context,
        atShell: (intent) {
          final navigator = _navigatorKey.currentState;
          if (navigator == null) return;
          dispatchAtShellRoot(navigator, _shortcutSignals, intent);
        },
        openChat: (target, item) async {
          final problem = await ConnectionChatsHost(
            _controller,
          ).openChat(target, item);
          if (problem != null && mounted) _say(problem, failed: true);
          return problem;
        },
      ),
      DesktopCommand(
        label: l10n.e7LocaleUiKeyboardShortcuts,
        icon: Icons.keyboard_outlined,
        keys: '$mod + /',
        onInvoke: () => unawaited(showShortcutsHelp(context)),
      ),
      DesktopCommand(
        label: l10n.e7LocaleUiRefreshSessions,
        icon: Icons.refresh_rounded,
        onInvoke: () => unawaited(_controller.refreshSessions()),
      ),
      DesktopCommand(
        label: l10n.e7LocaleUiDiagnostics,
        icon: Icons.bug_report_outlined,
        hint: l10n.e7LocaleUiDiagnosticsHint,
        onInvoke: () => _navigatorKey.currentState?.pushNamed('/debug'),
      ),
      // The rest of the launcher is the app-wide search index, so a setting
      // or a Project tool is found here exactly as it is in Settings. The
      // three tabs are the numbered commands above.
      for (final entry in searchIndex(l10n, scope))
        if (!entry.id.startsWith('tab-') &&
            !_goToEntryIds.contains(entry.id) &&
            entry.id != 'library-keyboard-shortcuts' &&
            entry.id != 'app-diagnostics-entry')
          DesktopCommand(
            label: entry.title,
            icon: entry.icon,
            hint: entry.parent == null
                ? null
                : l10n.discoverSearchIn(entry.parent!),
            keywords: entry.keywords,
            onInvoke: () {
              // A context under the navigator: the launcher's own is gone by
              // the time a command runs.
              final target = _navigatorKey.currentState?.overlay?.context;
              if (target != null) unawaited(entry.open(target, scope));
            },
          ),
      // Conversations, every agent's (Claude Code's on this phone too), found
      // by title, project or agent once something is typed. Each opens on
      // its own backend, the way the Conversations list opens it.
      for (final item in _controller.chatFeed().items.take(400))
        if (!item.isSubagent)
          DesktopCommand(
            label: item.title,
            icon: AppIconography.chat,
            hint: l10n.launcherConversationHint(
              item.projectName,
              item.agentLabel ?? 'OpenCode',
            ),
            keywords: '${item.projectName} ${item.agentLabel ?? 'OpenCode'}',
            onlyWhenSearched: true,
            onInvoke: () {
              final target = _navigatorKey.currentState?.overlay?.context;
              if (target == null) return;
              unawaited(
                ConnectionChatsHost(_controller).openChat(target, item).then((
                  problem,
                ) {
                  if (problem != null && mounted) _say(problem, failed: true);
                }),
              );
            },
          ),
      // Projects by name, once typed: each opens that project's Files page
      // (the phone has no Files tab; a project's files are one search away).
      if (ProjectHub.isAvailable(_controller.capabilities))
        for (final project in ConnectionChatsHost(
          _controller,
        ).source.projectSummaries.take(200))
          if (!isTemporaryProjectDirectory(project.directory))
            DesktopCommand(
              label: project.name,
              icon: AppIconography.folderOpen,
              hint: l10n.discoverSearchIn(l10n.shellTabFiles),
              keywords: '${l10n.shellTabFiles} ${project.directory}',
              onlyWhenSearched: true,
              onInvoke: () {
                final target = _navigatorKey.currentState?.overlay?.context;
                if (target == null) return;
                unawaited(
                  openProjectFiles(
                    target,
                    _controller,
                    directory: project.directory,
                  ),
                );
              },
            ),
    ];
  }
}

/// Index entries a Go to row already stands for: listing them too would
/// show each place twice once something is typed.
const _goToEntryIds = {
  'settings-saved-servers',
  'project-terminal',
  'ai-team',
  'settings-setup-guide',
};
