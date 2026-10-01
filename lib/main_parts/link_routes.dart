part of '../main.dart';

extension _OcAppLinkRoutes on _OcAppState {
  /// A session handoff link (F4-S2) names a saved server and a session and
  /// nothing else. Known server: open that exact session with explicit
  /// navigation, switching the connection first when the link names a
  /// saved server other than the active one. Unknown server: an honest
  /// banner with a way to the servers screen. Nothing is ever sent, created
  /// or resumed on the user's behalf, and a link that cannot complete is
  /// consumed with a notice rather than retried forever.
  void _scheduleSessionLinkRoute() {
    if (_linkRouteScheduled) return;
    final link = _sessionLink.pending.value;
    if (link == null) return;
    final target = _savedProfile(link.profileID);
    final targetIsActive =
        target != null && target.id == _controller.profile?.id;
    if (targetIsActive && _launchNewTaskWaiting) {
      if (!_linkWaitingNoticeShown) {
        _linkWaitingNoticeShown = true;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          final context = _navigatorKey.currentContext;
          if (!mounted || context == null || !_linkWaitingNoticeShown) return;
          _showWaiting(
            _OcAppState._waitingNotice,
            AppLocalizations.of(context).handoffUiLinkWaiting,
          );
        });
      }
      return;
    }
    _linkRouteScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      try {
        if (!mounted) return;
        final navigator = _navigatorKey.currentState;
        if (navigator == null) return;
        final current = _sessionLink.pending.value;
        if (current == null) return;
        await _openSessionForLink(navigator, current);
      } finally {
        _linkRouteScheduled = false;
        if (mounted) _scheduleSessionLinkRoute();
      }
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  Future<void> _openSessionForLink(
    NavigatorState navigator,
    SessionLink link,
  ) async {
    final l10n = AppLocalizations.of(navigator.context);
    final target = _savedProfile(link.profileID);
    if (target == null) {
      _consumeSessionLink(link);
      _showSessionLinkServerMissing(navigator, l10n);
      return;
    }
    if (target.requiresPasswordReentry || target.requiresCodexTokenReentry) {
      _consumeSessionLink(link);
      _showServersForLaunch(navigator);
      _showLaunchNotice(l10n.handoffUiLinkReentry);
      return;
    }
    if (target.id != _controller.profile?.id) {
      // Another saved server: switch the connection first. The servers
      // screen does the same on a tap, then lands on home.
      Object? failure;
      try {
        await _controller.connect(target);
      } catch (error) {
        failure = error;
      }
      if (!mounted) return;
      if (failure != null || _controller.api == null) {
        _consumeSessionLink(link);
        _showServersForLaunch(navigator);
        _showLaunchNotice(l10n.handoffUiLinkConnectionFailed);
        return;
      }
      _consumeSessionLink(link);
      navigator.pushNamedAndRemoveUntil('/home', (_) => false);
      unawaited(navigator.pushNamed('/chat/${link.sessionID}'));
      return;
    }
    if (!_launchConnectionReady) {
      // The state moved between scheduling and this frame; the listener
      // re-evaluates the retained link when the connection settles.
      if (_launchNewTaskWaiting) return;
      _consumeSessionLink(link);
      _showServersForLaunch(navigator);
      _showLaunchNotice(l10n.handoffUiLinkConnectionFailed);
      return;
    }
    _consumeSessionLink(link);
    unawaited(navigator.pushNamed('/chat/${link.sessionID}'));
  }

  /// A conversation link that carries the server's address (P3.9). Taken
  /// once into the one address coordinator, which stays network-silent; the
  /// sheet then asks before every step and says plainly when this link type
  /// is not available yet. Only a found, existing conversation navigates,
  /// through the same path as a local link. A link arriving while the sheet
  /// is up replaces the one it shows.
  void _scheduleAddressLinkRoute() {
    if (_addressLinkRouteScheduled) return;
    if (_sessionLink.pendingAddress.value == null &&
        _sessionLink.pendingAddressFailure.value == null) {
      return;
    }
    _addressLinkRouteScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _addressLinkRouteScheduled = false;
      final navigator = _navigatorKey.currentState;
      if (!mounted || navigator == null) return;
      unawaited(_openAddressLink(navigator));
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  Future<void> _openAddressLink(NavigatorState navigator) async {
    final addresses = ref.read(sessionAddressProvider);
    var failure = _sessionLink.takeAddressFailure();
    final link = _sessionLink.takeAddress();
    if (link != null) {
      failure = null;
      try {
        addresses.receive(link.encode());
      } on SessionAddressFailure catch (error) {
        addresses.cancel();
        failure = error.code;
      }
    }
    if (_addressSheetOpen) return;
    _addressSheetOpen = true;
    try {
      final opened = await showSessionAddressSheet(
        navigator.context,
        controller: addresses,
        failure: failure,
        onAddServer: (origin) async {
          await navigator.pushNamed(
            '/servers',
            arguments: ServersRouteRequest.add(initialUrl: origin),
          );
        },
        onSignIn: (profileId) async {
          await navigator.pushNamed(
            '/servers',
            arguments: ServersRouteRequest.connect(profileId),
          );
        },
      );
      if (opened == null || !mounted) return;
      final route = SessionLink.tryCreate(
        profileID: opened.profileId,
        sessionID: opened.sessionId,
      );
      if (route != null) await _openSessionForLink(navigator, route);
    } finally {
      _addressSheetOpen = false;
    }
  }

  /// An AI Team link (TEAM-203) names a saved server and a gate or run and
  /// nothing else: the notification tap and the `opencode-mobile://team`
  /// link both land here. Known, active server: Activity opens with the
  /// exact Gate sheet (or the task's conversation). Another saved server: switch
  /// first, as the session link does. Unknown server: the same honest
  /// banner. Opening never answers anything.
  void _scheduleTeamLinkRoute() {
    if (_teamLinkRouteScheduled) return;
    final link = _sessionLink.pendingTeam.value;
    if (link == null) return;
    final target = _savedProfile(link.profileId);
    final targetIsActive =
        target != null && target.id == _controller.profile?.id;
    if (targetIsActive && _launchNewTaskWaiting) return;
    _teamLinkRouteScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      try {
        if (!mounted) return;
        final navigator = _navigatorKey.currentState;
        if (navigator == null) return;
        final current = _sessionLink.pendingTeam.value;
        if (current == null) return;
        await _openTeamLink(navigator, current);
      } finally {
        _teamLinkRouteScheduled = false;
        if (mounted) _scheduleTeamLinkRoute();
      }
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  Future<void> _openTeamLink(NavigatorState navigator, TeamLink link) async {
    final l10n = AppLocalizations.of(navigator.context);
    final target = _savedProfile(link.profileId);
    if (target == null) {
      _consumeTeamLink(link);
      _showSessionLinkServerMissing(navigator, l10n);
      return;
    }
    if (target.requiresPasswordReentry || target.requiresCodexTokenReentry) {
      _consumeTeamLink(link);
      _showServersForLaunch(navigator);
      _showLaunchNotice(l10n.handoffUiLinkReentry);
      return;
    }
    if (target.id != _controller.profile?.id) {
      Object? failure;
      try {
        await _controller.connect(target);
      } catch (error) {
        failure = error;
      }
      if (!mounted) return;
      if (failure != null || _controller.api == null) {
        _consumeTeamLink(link);
        _showServersForLaunch(navigator);
        _showLaunchNotice(l10n.handoffUiLinkConnectionFailed);
        return;
      }
      _consumeTeamLink(link);
      navigator.pushNamedAndRemoveUntil('/home', (_) => false);
      _pushTeamDestination(navigator, link);
      return;
    }
    if (!_launchConnectionReady) {
      if (_launchNewTaskWaiting) return;
      _consumeTeamLink(link);
      _showServersForLaunch(navigator);
      _showLaunchNotice(l10n.handoffUiLinkConnectionFailed);
      return;
    }
    _consumeTeamLink(link);
    _pushTeamDestination(navigator, link);
  }

  /// Activity with the gate's sheet opening on top (it waits for the
  /// plugin's first snapshot), or the task's conversation for a run. A
  /// profile without the plugin gets the plain Activity list.
  void _pushTeamDestination(NavigatorState navigator, TeamLink link) {
    final team = _controller.orchestration;
    if (team != null &&
        team.capabilities.projectLifecycle &&
        team.projectController != null) {
      navigator.push(
        KitPageRoute<void>(
          builder: (_) => teamProjectDestination(
            team,
            requestId: link.kind == TeamLinkKind.gate ? link.id : null,
            taskId: link.kind == TeamLinkKind.run ? link.id : null,
          ),
        ),
      );
      return;
    }
    switch (link.kind) {
      case TeamLinkKind.gate:
        navigator.push(
          KitPageRoute<void>(
            builder: (_) => ActivityScreen(
              controller: _controller,
              initialTeamGateId: team == null ? null : link.id,
            ),
          ),
        );
      case TeamLinkKind.run:
        if (team == null) {
          navigator.push(
            KitPageRoute<void>(
              builder: (_) => ActivityScreen(controller: _controller),
            ),
          );
          return;
        }
        // The task's one page: its conversation, as every other door to a
        // task opens (docs/design/team-conversation-2026-09-26.md).
        navigator.push(TeamConversation.route(team, runId: link.id));
    }
  }

  void _consumeTeamLink(TeamLink link) {
    if (_sessionLink.pendingTeam.value == link) _sessionLink.takeTeam();
  }

  /// Consumes [link] only if it is still the pending one, so a link that
  /// arrived while this one was being handled is not swallowed with it.
  void _consumeSessionLink(SessionLink link) {
    _linkWaitingNoticeShown = false;
    _clearNotice(_OcAppState._waitingNotice);
    if (_sessionLink.pending.value == link) _sessionLink.take();
  }

  /// A link for a server this phone has not saved: the link names only the
  /// sending phone's profile id (never the server's address, see
  /// [SessionLink.profileID]), so the sheet cannot fill anything in. It
  /// offers Add server itself, not the list to find it on (P3.9).
  void _showSessionLinkServerMissing(
    NavigatorState navigator,
    AppLocalizations l10n,
  ) {
    unawaited(() async {
      final add = await showKitConfirm(
        navigator.context,
        title: l10n.handoffUiLinkAddTitle,
        body: l10n.handoffUiLinkServerMissing,
        confirmLabel: l10n.handoffUiLinkAddServer,
        cancelLabel: l10n.handoffUiLinkDismiss,
        icon: AppIconography.add,
        sheetKey: const Key('session-link-server-missing'),
        confirmKey: const Key('session-link-add-server'),
      );
      if (!add || !mounted) return;
      unawaited(
        _navigatorKey.currentState?.pushNamed(
          '/servers',
          arguments: const ServersRouteRequest.add(),
        ),
      );
    }());
  }
}
