part of '../connection.dart';

// Launcher surfaces: home-screen widget, pinned shortcuts, attention tile and the live status notification.

/// [ConnectionController]'s launcher surfaces.
mixin _ConnectionControllerSurfaces on ChangeNotifier {
  ConnectionController get _self;

  /// The most recent home-screen widget write started by [notifyListeners].
  Future<void>? _pendingWidgetSnapshotWrite;

  /// The most recent launcher shortcut / tile writes started by
  /// [notifyListeners], so deletion can wait for them instead of racing.
  Future<void>? _pendingLauncherWrite;

  /// Set while [deleteProfileAndLocalData] runs, so a notification cannot
  /// republish the sessions of the profile being erased — to the widget, the
  /// launcher shortcuts, or the tile.
  bool _widgetSnapshotSuspended = false;
  Object? _widgetScopeIdentity;
  String _widgetScope = '';
  final Map<String, String> _widgetRunMarkers = {};
  // Display only: one bounded reply and current assistant identity per session.
  final Map<String, String> _widgetReplyPreviews = {};
  final Map<String, String> _widgetAssistantMessages = {};
  WidgetActionResult? _widgetActionResult;
  final Map<String, WidgetActionResult> _widgetActionResults = {};
  final Set<String> _widgetActionsInFlight = {};
  final Map<String, List<MobileTaskItem>> _widgetTasks = {};

  /// When each shown server was first seen unreachable, by profile id, for
  /// the Status widget's "offline 12 min". Cleared when it answers again.
  final Map<String, int> _widgetOfflineSince = {};

  /// The ongoing "OpenCode is connected" notification's content, derived
  /// from the same truth the Activity tab shows: how many sessions run, how
  /// many requests wait, the most recently active busy session's title, and
  /// a generic sentence for the tool it is running.
  @visibleForTesting
  LiveStatus liveStatus() => _self._liveStatus();

  /// What [sessionID] does while it runs, for its row on the Conversations
  /// list: since when its turn runs, the shell command it runs now (its
  /// first line) and otherwise the task in progress or the tool's sentence.
  /// Null while it does not run.
  ({DateTime since, String? command, String? line})? runningNow(
    String sessionID,
  ) => _self._runningNow(sessionID);
}

extension _ConnectionControllerSurfacesImpl on ConnectionController {
  /// Keeps the launcher's pinned-session shortcuts and the Quick Settings
  /// tile's count in step with the connected profile. Both writers skip
  /// unchanged payloads. Nothing is published while connecting or while the
  /// lifecycle has suspended the transport: the entries stay as the last
  /// connected state left them, and only an explicit [disconnect] or a
  /// profile deletion withdraws them.
  void _publishLaunchSurfaces(List<Session> sortedSessions) {
    if (status != StreamStatus.connected) return;
    final profileID = _connectedProfile?.id ?? store.activeId ?? '';
    if (profileID.isEmpty) return;
    final pins = pinnedSessionIDs;
    final write = Future.wait<void>([
      _pinnedShortcuts.update(
        sessions: [
          for (final session in sortedSessions)
            if (pins.contains(session.id)) session,
        ],
        profileID: profileID,
        untitledLabel: _shellStrings().launchUiPinnedUntitled,
      ),
      _attentionTile.update(
        pendingCount: permissions.length + questions.length + forms.length,
        profileID: profileID,
      ),
    ]);
    _pendingLauncherWrite = write;
    unawaited(write);
  }

  /// Localized copy for surfaces the controller writes without a widget tree
  /// (launcher shortcut labels). Follows the in-app language choice, then the
  /// device locale, and falls back to English for a language the app does
  /// not ship.
  AppLocalizations _shellStrings() {
    final locale = appLocale.value ?? PlatformDispatcher.instance.locale;
    final supported = AppLocalizations.supportedLocales.any(
      (candidate) => candidate.languageCode == locale.languageCode,
    );
    return lookupAppLocalizations(
      supported ? Locale(locale.languageCode) : const Locale('en'),
    );
  }

  /// The body of [liveStatus].
  LiveStatus _liveStatus() {
    Session? current;
    for (final id in busySessions) {
      final session = sessionsById[id];
      if (session == null) continue;
      if (current == null ||
          (session.time?.updated ?? 0) > (current.time?.updated ?? 0)) {
        current = session;
      }
    }
    final title = current == null
        ? null
        : displaySessionTitleText(current.title);
    final detail = current == null ? null : _runningToolDetail[current.id];
    return LiveStatus(
      runningCount: busySessions.length,
      pendingCount: awaitingPermissionCount + questions.length + forms.length,
      title: title == null || title.isEmpty ? null : title,
      detail: detail,
      status: _notificationStatus(),
    );
  }

  ({DateTime since, String? command, String? line})? _runningNow(
    String sessionID,
  ) {
    if (!busySessions.contains(sessionID)) return null;
    final since =
        _openTurns[sessionID] ??
        _busySince.putIfAbsent(sessionID, DateTime.now);
    return (
      since: since,
      command: _runningCommand[sessionID],
      line: _sessionTasks[sessionID]?.current ?? _runningToolDetail[sessionID],
    );
  }

  /// A session's project: the last folder of its directory.
  String _projectNameOf(Session? session) =>
      _projectNameOfDirectory(session?.directory);

  /// A project's name: the last folder of [path].
  String _projectNameOfDirectory(String? path) {
    final directory = path?.trim() ?? '';
    if (directory.isEmpty) return '';
    final parts = directory.split(RegExp(r'[\\/]+'))
      ..removeWhere((part) => part.isEmpty);
    return parts.isEmpty ? '' : parts.last;
  }

  /// Everything the one status notification says, gathered from session
  /// truth. The words come from [buildNotificationStatus].
  @visibleForTesting
  NotificationStatusInput notificationStatusInput() {
    final pendingSessionIDs = <String>{
      for (final permission in awaitingPermissions) permission.sessionID,
      for (final question in questions.values) question.sessionID,
      for (final form in forms.values) form.sessionID,
    };
    final now = DateTime.now();
    final working = <StatusChat>[];
    final retries = <StatusRetry>[];
    final busy =
        [
          for (final id in busySessions)
            if (sessionsById[id]?.parentID == null) id,
        ]..sort(
          (a, b) => (sessionsById[b]?.time?.updated ?? 0).compareTo(
            sessionsById[a]?.time?.updated ?? 0,
          ),
        );
    for (final id in busy) {
      final session = sessionsById[id];
      final title = displaySessionTitleText(session?.title);
      final retry = retryStates[id];
      if (retry != null) {
        final model = displayModelForSession(id);
        final provider = model == null
            ? null
            : presentedProviderName(
                model.providerID,
                catalog?.providers ?? const [],
              );
        retries.add(
          StatusRetry(
            chatId: id,
            chatTitle: title,
            provider: provider == null || provider.trim().isEmpty
                ? null
                : provider.trim(),
            nextTry: retry.next,
          ),
        );
        continue;
      }
      // A chat waiting on the person is counted as needing them, not as
      // working: the status says each thing once.
      if (pendingSessionIDs.contains(id)) continue;
      final tasks = _sessionTasks[id];
      final since = _openTurns[id] ?? _busySince.putIfAbsent(id, () => now);
      final line = tasks?.current ?? _runningToolDetail[id];
      working.add(
        StatusChat(
          id: id,
          title: title,
          project: _projectNameOf(session),
          liveLine: line,
          tasksDone: tasks?.total == null || tasks!.total == 0
              ? null
              : tasks.done,
          tasksTotal: tasks?.total == null || tasks!.total == 0
              ? null
              : tasks.total,
          elapsed: now.difference(since),
        ),
      );
    }

    String firstTitle = '';
    String firstProject = '';
    String? firstSession;
    if (awaitingPermissions.isNotEmpty) {
      firstSession = awaitingPermissions.first.sessionID;
    } else if (questions.isNotEmpty) {
      firstSession = questions.values.first.sessionID;
    } else if (forms.isNotEmpty) {
      firstSession = forms.values.first.sessionID;
    }
    if (firstSession != null) {
      final session = sessionsById[firstSession];
      firstTitle = displaySessionTitleText(session?.title);
      firstProject = _projectNameOf(session);
    }

    var liveServers = 1;
    for (final side in _sides.values) {
      if (side.status == StreamStatus.connected) liveServers++;
    }
    if (working.isNotEmpty) {
      _lastActivityLine = working.first.liveLine ?? working.first.title;
    }
    return NotificationStatusInput(
      working: working,
      pendingCount: awaitingPermissionCount + questions.length + forms.length,
      firstPendingTitle: firstTitle,
      firstPendingProject: firstProject,
      retries: retries,
      pause: backgroundLive.stoppedByAndroidTimeout
          ? NotificationPauseReason.androidLimit
          : thermalPauseActive.value
          ? NotificationPauseReason.phoneHot
          : NotificationPauseReason.none,
      reconnecting: status == StreamStatus.reconnecting,
      reconnectingLastActivity: _lastActivityLine,
      phoneServerRunning: looksLikeInAppServer(profile),
      liveServerCount: liveServers,
      serverName: profile?.name ?? '',
      lastReplyAt: _lastReply?.at,
      lastReplyTitle: _lastReply?.title ?? '',
    );
  }

  /// The status notification's words, in the person's language.
  NotificationStatus _notificationStatus() {
    final locale = appLocale.value ?? PlatformDispatcher.instance.locale;
    return buildNotificationStatus(
      notificationStatusInput(),
      _shellStrings(),
      formatTime: (time) =>
          DateFormat.Hm(locale.toLanguageTag()).format(time.toLocal()),
    );
  }

  /// A run began: remember when, for the elapsed time on the status.
  void _noteRunStarted(String sessionID) {
    _busySince.putIfAbsent(sessionID, DateTime.now);
  }

  /// A run ended. [replied] marks a normal finish: it becomes "Last reply
  /// 12 min ago" on the idle status.
  void _noteRunEnded(String sessionID, {required bool replied}) {
    _widgetRunMarkers.remove(sessionID);
    _busySince.remove(sessionID);
    _runningCommand.remove(sessionID);
    _sessionTasks.remove(sessionID);
    _widgetTasks.remove(sessionID);
    if (replied && sessionsById[sessionID]?.parentID == null) {
      _lastReply = (
        title: displaySessionTitleText(sessionsById[sessionID]?.title),
        at: DateTime.now(),
      );
    }
  }

  /// Reads the plan the agent keeps ("5 of 6 tasks") from a todos list.
  /// Returns whether it changed what the status says.
  bool _noteTasks(String sessionID, Object? todos) {
    final view = MobileTaskView.fromTodos(todos);
    if (view == null || view.trackedCount == 0) {
      _widgetTasks.remove(sessionID);
      return _sessionTasks.remove(sessionID) != null;
    }
    _widgetTasks[sessionID] = view.tasks
        .where((task) => task.status != MobileTaskStatus.cancelled)
        .toList();
    String? current;
    for (final task in view.tasks) {
      if (task.status == MobileTaskStatus.inProgress) {
        current = task.text.trim().isEmpty ? null : task.text.trim();
        break;
      }
    }
    final next = (
      done: view.completedCount,
      total: view.trackedCount,
      current: current,
    );
    final changed = _sessionTasks[sessionID] != next;
    _sessionTasks[sessionID] = next;
    return changed;
  }

  void _publishLiveStatus() {
    if (_disposed || !keepLiveInBackground || !backgroundLive.active) return;
    _runningToolDetail.removeWhere((id, _) => !busySessions.contains(id));
    _runningCommand.removeWhere((id, _) => !busySessions.contains(id));
    // Plans and start times are dropped when a run ends ([_noteRunEnded]),
    // not here: a polled "busy" can arrive after the plan's first event.
    // Only a runaway map is trimmed.
    if (_sessionTasks.length > 64) {
      _sessionTasks.removeWhere((id, _) => !busySessions.contains(id));
    }
    if (_busySince.length > 64) {
      _busySince.removeWhere((id, _) => !busySessions.contains(id));
    }
    unawaited(backgroundLive.publishLiveStatus(liveStatus()));
  }
}
