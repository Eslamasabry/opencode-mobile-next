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
      if (target.kind == CodingAlertKind.quota) {
        unawaited(
          _controller.quotaMonitor
              .resolveRoute(target.profileID, target.monitorToken)
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
                      initialSection: UsageSection.remaining,
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
        navigator.push(
          chatLandingRoute(
            sessionID: target.sessionID,
            landOnRequestID: waiting?.id,
          ),
        );
        return;
      }
      navigator.push(
        KitPageRoute<void>(
          builder: (_) => ChatScreen(sessionID: target.sessionID),
        ),
      );
    });
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
      // Same order as the dock: the number in the hint is the tab's position.
      DesktopCommand(
        label: l10n.e7LocaleUiFiles,
        icon: Icons.folder_outlined,
        hint: l10n.e7LocaleUiFilesHint,
        keys: '$mod + 2',
        onInvoke: () => go(1),
      ),
      // One Settings command: the third tab is the hub. "$mod + ," still
      // opens the same hub over the current screen without leaving it.
      DesktopCommand(
        label: l10n.e7LocaleUiSettings,
        icon: Icons.settings_outlined,
        hint: l10n.e7LocaleUiMoreHint,
        keys: '$mod + 3',
        onInvoke: () => go(2),
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
    ];
  }
}
