part of '../main.dart';

extension _OcAppLaunchRoute on _OcAppState {
  /// The active connection can open a session right now: transport,
  /// repository are ready and nothing is mid-flight. Mirrors
  /// the readiness the share route waits for.
  bool get _launchConnectionReady =>
      _controller.hasConnectedServer &&
      !_controller.connectionLoading &&
      !_controller.locationLoading;

  bool get _launchProfileNeedsReentry {
    final profile = _controller.profile;
    return profile != null &&
        (profile.requiresPasswordReentry || profile.requiresCodexTokenReentry);
  }

  /// A saved server is on its way: either a connect is in flight, or nothing
  /// has failed yet and the root screen is about to start one (cold start).
  /// A missing profile, a credential re-entry, or a recorded failure is
  /// final and never counts as waiting.
  bool get _launchNewTaskWaiting {
    if (_controller.profile == null || _launchProfileNeedsReentry) return false;
    if (_launchConnectionReady) return false;
    if (_controller.connectionLoading || _controller.locationLoading) {
      return true;
    }
    return _controller.lastError == null;
  }

  /// A pinned-session shortcut waits under the same rule as New task, but
  /// only for its own server: a launch stamped with another profile is
  /// final and is dropped with a notice instead of waiting for a server that
  /// is never going to be the active one.
  bool _sessionLaunchWaiting(SessionLaunch launch) =>
      _controller.profile?.id == launch.profileID && _launchNewTaskWaiting;

  /// A home-screen shortcut arrives as an action or as session IDs, never
  /// as text. Connect opens the servers screen over whatever is showing,
  /// leaving the current connection alone. New task waits for the saved
  /// server to become ready and then opens an empty session. A pinned
  /// session waits the same way and then opens exactly that chat. The Quick
  /// Settings tile opens Activity. Nothing is ever sent on the user's
  /// behalf, and a launch that cannot complete is consumed with a notice
  /// rather than left pending indefinitely.
  void _scheduleLaunchRoute() {
    if (_launchRouteScheduled) return;
    final action = _launchShortcut.pending.value;
    final session = _launchShortcut.pendingSession.value;
    if (action == null && session == null) return;
    final waiting = action == null
        ? _sessionLaunchWaiting(session!)
        : action == LaunchAction.newTask && _launchNewTaskWaiting;
    if (waiting) {
      if (!_launchWaitingNoticeShown) {
        _launchWaitingNoticeShown = true;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          final context = _navigatorKey.currentContext;
          if (!mounted || context == null || !_launchWaitingNoticeShown) {
            return;
          }
          final l10n = AppLocalizations.of(context);
          _showWaiting(
            _OcAppState._waitingNotice,
            action == null
                ? l10n.launchUiSessionWaiting
                : l10n.launchShortcutWaiting,
          );
        });
      }
      return;
    }
    _launchRouteScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      try {
        if (!mounted) return;
        final navigator = _navigatorKey.currentState;
        if (navigator == null) return;
        final current = _launchShortcut.pending.value;
        if (current == null) {
          final launch = _launchShortcut.pendingSession.value;
          if (launch != null) _openSessionForLaunch(navigator, launch);
          return;
        }
        switch (current) {
          case LaunchAction.connect:
            _consumeLaunchAction(current);
            _showServersForLaunch(navigator);
          case LaunchAction.newTask:
            await _openNewTaskForLaunch(navigator, current);
          case LaunchAction.activity:
            _openActivityForLaunch(navigator, current);
          case LaunchAction.phoneSetup:
          case LaunchAction.phoneSetupDone:
            // Never waits on the connection: setup has its own screens, and
            // the job's state (read from setup.json) decides where to land.
            _consumeLaunchAction(current);
            await openPhoneSetupFromNotification(
              navigator,
              topRouteName: _routeTracker.topName,
            );
        }
      } finally {
        _launchRouteScheduled = false;
        // A retained action re-evaluates against the current state; a
        // consumed one returns immediately.
        if (mounted) _scheduleLaunchRoute();
      }
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  Future<void> _openNewTaskForLaunch(
    NavigatorState navigator,
    LaunchAction action,
  ) async {
    final l10n = AppLocalizations.of(navigator.context);
    if (_controller.profile == null) {
      _consumeLaunchAction(action);
      _showServersForLaunch(navigator);
      _showLaunchNotice(l10n.launchShortcutNoServer);
      return;
    }
    if (_launchProfileNeedsReentry) {
      _consumeLaunchAction(action);
      _showServersForLaunch(navigator);
      _showLaunchNotice(l10n.launchShortcutReentry);
      return;
    }
    if (!_launchConnectionReady) {
      // The state moved between scheduling and this frame; the listener
      // re-evaluates the retained action when the connection settles.
      if (_launchNewTaskWaiting) return;
      _consumeLaunchAction(action);
      _showServersForLaunch(navigator);
      _showLaunchNotice(l10n.launchShortcutConnectionFailed);
      return;
    }
    final location = _controller.locationRevision;
    final api = _controller.api;
    final repository = _controller.repository;
    try {
      final session = await _controller.createSession();
      if (!mounted) return;
      if (location != _controller.locationRevision ||
          !identical(api, _controller.api) ||
          !identical(repository, _controller.repository)) {
        throw ProductException(l10n.e7LocaleUiConnectionChanged);
      }
      _consumeLaunchAction(action);
      unawaited(
        navigator.pushNamed(
          '/chat/${session.id}',
          arguments: const ChatRouteArguments.newlyCreated(),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      _consumeLaunchAction(action);
      _say(
        l10n.appNewConversationFailed,
        supporting: productErrorText(error, l10n: l10n),
        failed: true,
      );
    }
  }

  /// Opens the exact pinned session a launcher shortcut named, by IDs only.
  /// The chat is pushed without a draft and without any send; a launch for
  /// another server is dropped with a notice rather than switching servers
  /// silently, mirroring widget-row taps.
  void _openSessionForLaunch(NavigatorState navigator, SessionLaunch launch) {
    final l10n = AppLocalizations.of(navigator.context);
    final profile = _controller.profile;
    if (profile == null) {
      _consumeSessionLaunch(launch);
      _showServersForLaunch(navigator);
      _showLaunchNotice(l10n.launchUiSessionNoServer);
      return;
    }
    if (profile.id != launch.profileID) {
      _consumeSessionLaunch(launch);
      _showLaunchNotice(l10n.launchUiSessionOtherServer);
      return;
    }
    if (_launchProfileNeedsReentry) {
      _consumeSessionLaunch(launch);
      _showServersForLaunch(navigator);
      _showLaunchNotice(l10n.launchUiSessionReentry);
      return;
    }
    if (!_launchConnectionReady) {
      // The state moved between scheduling and this frame; the listener
      // re-evaluates the retained launch when the connection settles.
      if (_sessionLaunchWaiting(launch)) return;
      _consumeSessionLaunch(launch);
      _showServersForLaunch(navigator);
      _showLaunchNotice(l10n.launchUiSessionConnectionFailed);
      return;
    }
    _consumeSessionLaunch(launch);
    final route = '/chat/${launch.sessionID}';
    // A warm tap on the chat that is already showing stays where it is.
    if (_routeTracker.topName == route) return;
    unawaited(navigator.pushNamed(route));
  }

  /// The Quick Settings tile, the sessions widget and the app shortcut open
  /// Chats on its "Needs you" filter (the Inbox is part of Chats now) over
  /// whatever is showing. Chats reflects the connection as it settles, so the
  /// tap never waits; without a saved server there is nothing to show and the
  /// servers screen opens instead. The wire id stays `activity`: the native
  /// side is unchanged.
  void _openActivityForLaunch(NavigatorState navigator, LaunchAction action) {
    _consumeLaunchAction(action);
    if (_controller.profile == null) {
      _showServersForLaunch(navigator);
      _showLaunchNotice(
        AppLocalizations.of(navigator.context).launchUiActivityNoServer,
      );
      return;
    }
    dispatchAtShellRoot(
      navigator,
      _shortcutSignals,
      const OpenChatsIntent(needsYou: true),
    );
  }

  /// Consumes [action] only if it is still the pending one, so an action that
  /// arrived while this one was being handled is not swallowed with it.
  void _consumeLaunchAction(LaunchAction action) {
    _launchWaitingNoticeShown = false;
    _clearNotice(_OcAppState._waitingNotice);
    if (_launchShortcut.pending.value == action) _launchShortcut.take();
  }

  /// Same single-consumption rule for a pinned-session launch.
  void _consumeSessionLaunch(SessionLaunch launch) {
    _launchWaitingNoticeShown = false;
    _clearNotice(_OcAppState._waitingNotice);
    if (_launchShortcut.pendingSession.value == launch) {
      _launchShortcut.takeSession();
    }
  }

  /// Pushes the servers screen unless one is already on top. Routes beneath
  /// stay: an open chat keeps its draft, and the connection is untouched.
  void _showServersForLaunch(NavigatorState navigator) {
    final top = _routeTracker.topName;
    final rootShowsServers =
        top == '/' &&
        (_controller.profile == null || _launchProfileNeedsReentry);
    if (top == '/servers' || rootShowsServers) return;
    unawaited(navigator.pushNamed('/servers'));
  }

  void _showLaunchNotice(String message) => _say(message);

  ServerProfile? _savedProfile(String id) {
    for (final profile in _controller.store.profiles) {
      if (profile.id == id) return profile;
    }
    return null;
  }
}
