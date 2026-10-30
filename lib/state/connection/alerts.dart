part of '../connection.dart';

// Coding alerts: input and finished-run notifications and their actions.

/// A question qualifies for a notification quick reply only when one typed
/// answer can truthfully satisfy it: a single prompt that accepts custom
/// text.
bool _questionSupportsQuickReply(PendingQuestion question) =>
    question.prompts.length == 1 && question.prompts.single.custom;

/// [ConnectionController]'s coding alerts.
mixin _ConnectionControllerAlerts on ChangeNotifier {
  ConnectionController get _self;

  final Set<String> _attentionActiveSessions = {};
  final Map<String, ({CodingAlertKind kind, String requestID})>
  _alertedInputKinds = {};
  final Set<String> _alertedStatusSessions = {};

  /// Generic sentence for the tool each busy session is running right now,
  /// gleaned from `message.part.updated` on the way past. Feeds the ongoing
  /// Android notification only; pruned lazily against [busySessions].
  final Map<String, String> _runningToolDetail = {};

  /// The first line of the shell command each busy session runs right now
  /// (`flutter test`), for its row on the Conversations list. Never in a
  /// notification; cleared with [_runningToolDetail].
  final Map<String, String> _runningCommand = {};

  /// The tool and the file or command of recent tool calls, by call id, so a
  /// request that names only its call ("outside this project") can say what
  /// the call does even where its conversation is not open. At most 64.
  final Map<String, ({String tool, String? target})> _toolCalls = {};

  /// What the tool call behind [request] does, when it was seen on the way.
  ({String tool, String? target})? permissionToolCall(
    PermissionRequest request,
  ) {
    final callID = request.tool?.callID;
    return callID == null || callID.isEmpty ? null : _toolCalls[callID];
  }

  /// When each busy session was first seen busy (elapsed time on the status).
  final Map<String, DateTime> _busySince = {};

  /// Each busy session's plan: finished and tracked tasks, and the one in
  /// progress. Feeds "5 of 6 tasks" and the status's live line.
  final Map<String, ({int done, int total, String? current})> _sessionTasks =
      {};

  /// The last reply that finished, for "Last reply 12 min ago".
  ({String title, DateTime at})? _lastReply;

  /// What the last working chat was doing, for the reconnecting status.
  String? _lastActivityLine;
  CodingAlertOpen? _pendingCodingAlertOpen;

  PendingQuestion? questionForSession(String sessionID) =>
      _self._questionForSession(sessionID);

  bool get keepLiveInBackground =>
      !_self.isIsolated && _self.backgroundLive.enabled;

  Future<bool> setKeepLiveInBackground(bool enabled) =>
      !_self._ownsProfileServices
      ? Future.value(false)
      : _self.backgroundLive.setEnabled(enabled);

  CodingAlertOpen? get pendingCodingAlertOpen => _pendingCodingAlertOpen;

  CodingAlertOpen? takePendingCodingAlertOpen() {
    final value = _pendingCodingAlertOpen;
    _pendingCodingAlertOpen = null;
    return value;
  }

  Future<void> restoreBackgroundLiveMode() async {
    if (!_self._ownsProfileServices) return;
    await _self.backgroundLive.restore();
    await consumeCodingAlertOpen();
  }

  Future<void> consumeCodingAlertOpen() => _self._consumeCodingAlertOpen();

  /// Whether a run finishing after the person leaves would reach them as a
  /// notification: the background connection is on, Android granted the
  /// permission, this server's alerts and the finished-run choice are on, and
  /// it is not quiet hours. The same conditions [_settleSessionAttention]
  /// checks, minus "the app is in the background", which leaving makes true.
  bool get finishedRunNotificationsReady =>
      _self._finishedRunNotificationsReady;
}

extension _ConnectionControllerAlertsImpl on ConnectionController {
  /// Resolves an Android notification action while the app stays
  /// backgrounded. Alerts exist only while live mode keeps the transport
  /// alive, so replies go through that live transport directly; running the
  /// foreground wake path here would count as an app resume and clear every
  /// alert. Returns false so Android re-posts the alert when the reply cannot
  /// be delivered.
  Future<bool> _handleCodingAlertAction(CodingAlertAction action) async {
    if (action.profileID != (profile?.id ?? '')) return false;
    if (_disposed || _lifecycleSuspended) return false;
    final currentApi = api;
    final current = repository;
    try {
      switch (action.decision) {
        case 'allow':
        case 'deny':
          // Resolution is bound to the exact request the notification
          // represented; a stale or missing ID refreshes the alert instead
          // of resolving whichever request happens to be pending now.
          final permission = permissions[action.requestID];
          if (permission == null || permission.sessionID != action.sessionID) {
            _syncInputAlerts();
            return true;
          }
          if (currentApi == null) return false;
          await _sendPermissionReply(
            currentApi,
            permission.id,
            action.decision == 'allow' ? 'once' : 'reject',
          );
          return true;
        case 'reply':
          final text = action.reply?.trim() ?? '';
          if (text.isEmpty) return false;
          if (action.kind == CodingAlertKind.permission) {
            // On OpenCode 2 permissions, Reply maps to reject-with-message
            // (the message is shown to the model — steering by rejection).
            // RequestID binding rules stay exactly as for allow/deny: the
            // reply resolves only the exact request this notification
            // represented, otherwise the alert refreshes.
            final permission = permissions[action.requestID];
            if (permission == null ||
                permission.sessionID != action.sessionID ||
                !_v2PermissionSessions.containsKey(action.requestID)) {
              _syncInputAlerts();
              return true;
            }
            if (currentApi == null) return false;
            await _sendPermissionReply(
              currentApi,
              permission.id,
              'reject',
              message: text,
            );
            return true;
          }
          final question = questions[action.requestID];
          // A typed reply needs a prompt that accepts custom text; an option
          // button sends one of the question's own labels, which is always an
          // answer it accepts.
          if (question == null ||
              question.sessionID != action.sessionID ||
              !(_questionSupportsQuickReply(question) ||
                  notificationOptionsOf(question).contains(text))) {
            _syncInputAlerts();
            return true;
          }
          await _sendQuestionAnswer(currentApi, current, question.id, [
            [text],
          ]);
          return true;
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  /// A button on the status notification. "Stop the reply" stops exactly the
  /// chat the notification named, and only while it is still running or
  /// waiting on a provider; anything else is left to open the app.
  Future<bool> _handleStatusAction(String action, String sessionID) async {
    if (_disposed || _lifecycleSuspended) return false;
    if (action != 'stop_reply' || sessionID.isEmpty) return false;
    final currentApi = api;
    if (currentApi == null || !busySessions.contains(sessionID)) return false;
    // Stopped from this app: the chat says "You stopped this reply.", not
    // that another device stopped it.
    noteStopFromThisApp(sessionID);
    try {
      await currentApi.abort(sessionID);
      return true;
    } catch (_) {
      noteStopFromThisApp(sessionID, asked: false);
      return false;
    }
  }

  /// The body of [questionForSession].
  PendingQuestion? _questionForSession(String sessionID) {
    for (final question in questions.values) {
      if (question.sessionID == sessionID) return question;
    }
    return null;
  }

  PendingQuestion? _quickReplyQuestionForSession(String sessionID) {
    for (final question in questions.values) {
      if (question.sessionID == sessionID &&
          _questionSupportsQuickReply(question)) {
        return question;
      }
    }
    return null;
  }

  /// The body of [consumeCodingAlertOpen].
  Future<void> _consumeCodingAlertOpen() async {
    if (!_ownsProfileServices) return;
    final value = await backgroundLive.consumeCodingAlertOpen();
    if (_disposed || value == null) return;
    // Home-screen widget rows outlive profile switches: a tap stamped with
    // another profile's ID opens the app normally rather than silently
    // routing into (or switching to) that profile's chat. Notification taps
    // carry no profile ID and keep routing as before.
    if (value.monitorToken.isEmpty &&
        value.profileID.isNotEmpty &&
        value.profileID != store.activeId &&
        // A conversation on another server in the list opens on its own
        // connection.
        !_sides.containsKey(value.profileID)) {
      return;
    }
    _pendingCodingAlertOpen = value;
    _notifyListeners();
  }

  String _inputAlertKey(String sessionID) =>
      profile == null ? 'input:$sessionID' : 'input:${profile!.id}:$sessionID';
  String _statusAlertKey(String sessionID) => profile == null
      ? 'status:$sessionID'
      : 'status:${profile!.id}:$sessionID';

  /// Whether an alert may post now. Quiet hours do not stop one: requests
  /// and failures still post, silently ([_quietNow]); only the lower-priority
  /// finished-reply alerts wait for the morning.
  bool get _canShowCodingAlert =>
      keepLiveInBackground &&
      _lifecycleWasBackgrounded &&
      backgroundLive.notificationGranted &&
      profileMonitor.rulesFor(_alertProfileId).notifications;

  /// Quiet hours are on for this server right now.
  bool get _quietNow =>
      profileMonitor.rulesFor(_alertProfileId).quietAt(DateTime.now());

  /// The body of [finishedRunNotificationsReady].
  bool get _finishedRunNotificationsReady {
    if (!platformCapabilities.supportsNotifications) return false;
    final rules = profileMonitor.rulesFor(_alertProfileId);
    return keepLiveInBackground &&
        backgroundLive.notificationGranted &&
        rules.notifications &&
        notificationPreferences.finishedRuns &&
        !rules.quietAt(DateTime.now());
  }

  void _markSessionAttentionActive(String sessionID) {
    _failedAttentionSessions.remove(sessionID);
    if (sessionID.isEmpty) return;
    _attentionActiveSessions.add(sessionID);
    if (_alertedStatusSessions.remove(sessionID)) {
      unawaited(backgroundLive.dismissCodingAlert(_statusAlertKey(sessionID)));
    }
  }

  void _settleSessionAttention(
    String sessionID,
    CodingAlertKind kind, {
    String reason = '',
  }) {
    if (sessionID.isEmpty || !_attentionActiveSessions.remove(sessionID)) {
      return;
    }
    // A run this connection watched has ended: the list says Done until the
    // conversation is opened (any server, not only those that report it).
    if (kind == CodingAlertKind.complete) _finishedUnseen.add(sessionID);
    if (kind == CodingAlertKind.complete &&
        profileMonitor.rulesFor(_alertProfileId).enabled) {
      return;
    }
    if (!_canShowCodingAlert ||
        !notificationPreferences.finishedRuns ||
        sessionsById[sessionID]?.parentID != null) {
      return;
    }
    final quiet = _quietNow;
    // A finished reply can wait for the morning; a failure posts, silently.
    if (quiet && kind == CodingAlertKind.complete) return;
    if (!_alertedStatusSessions.add(sessionID)) return;
    unawaited(() async {
      final chatTitle = displaySessionTitleText(sessionsById[sessionID]?.title);
      final detail = kind == CodingAlertKind.complete
          ? await _firstReplyLineFor(sessionID)
          : '';
      if (_disposed) return;
      // The reply may have been opened, or another run started, meanwhile.
      if (!_alertedStatusSessions.contains(sessionID)) return;
      final shown = await backgroundLive.showCodingAlert(
        kind: kind,
        profileID: _alertProfileId,
        agentName: isAgentBackend ? profile?.name ?? '' : '',
        sessionID: sessionID,
        key: _statusAlertKey(sessionID),
        subtext: _alertSubtitle(sessionID),
        chatTitle: chatTitle,
        detail: detail,
        reason: kind == CodingAlertKind.error
            ? (reason.isEmpty ? 'other' : reason)
            : '',
        silent: quiet,
      );
      if (!shown && !_disposed && _lifecycleWasBackgrounded) {
        _alertedStatusSessions.remove(sessionID);
      }
    }());
  }

  /// The first line of the newest reply, for the finished alert: read from
  /// the transcript's newest page, never waited on for long. Empty when it
  /// cannot be read quickly.
  Future<String> _firstReplyLineFor(String sessionID) async {
    try {
      if (!canLoadSessionTail(sessionID)) return '';
      final page = await loadSessionTail(
        sessionID,
      ).timeout(const Duration(seconds: 2));
      return firstReplyLine(page.items);
    } catch (_) {
      return '';
    }
  }

  /// "MacBook · api-server": the saved server's name and the conversation's
  /// project, one subtitle for every alert about [sessionID].
  String _alertSubtitle(String sessionID) => notificationSubtitle(
    profile?.name,
    _projectNameOf(sessionsById[sessionID]),
  );

  void _showInputAlert(String sessionID, CodingAlertKind kind) {
    if (sessionID.isEmpty ||
        !_canShowCodingAlert ||
        !notificationPreferences.requests) {
      return;
    }
    // The alert represents one exact request: the front permission, or the
    // quick-reply-eligible question (falling back to the front question).
    final quickReplyQuestion = kind == CodingAlertKind.question
        ? _quickReplyQuestionForSession(sessionID)
        : null;
    // Forms deliberately never quick-reply (multi-field forms cannot be
    // answered from a RemoteInput); their alert deep-links into the app.
    final requestID = kind == CodingAlertKind.permission
        ? permissionForSession(sessionID)?.id
        : (quickReplyQuestion ?? questionForSession(sessionID))?.id ??
              formForSession(sessionID)?.id;
    if (requestID == null || requestID.isEmpty) return;
    final alerted = (kind: kind, requestID: requestID);
    if (_alertedInputKinds[sessionID] == alerted) return;
    _alertedInputKinds[sessionID] = alerted;
    // What the shade shows: a permission asks "Run npm install?" over the
    // exact command or file (masked); a question is its own words over its
    // few choices. The lock screen keeps fixed words only.
    final permission = kind == CodingAlertKind.permission
        ? permissionForSession(sessionID)
        : null;
    final question = kind == CodingAlertKind.question
        ? (quickReplyQuestion ?? questionForSession(sessionID))
        : null;
    final detail = permission != null
        ? redactForNotification(
            permission.commandPreview ?? permission.filePath ?? '',
          )
        : question != null && question.prompts.isNotEmpty
        ? redactForNotification(question.prompts.first.question)
        : '';
    final ask = permission == null ? null : permissionAskOf(permission);
    unawaited(
      backgroundLive
          .showCodingAlert(
            kind: kind,
            profileID: _alertProfileId,
            agentName: isAgentBackend ? profile?.name ?? '' : '',
            sessionID: sessionID,
            key: _inputAlertKey(sessionID),
            requestID: requestID,
            subtext: _alertSubtitle(sessionID),
            chatTitle: displaySessionTitleText(sessionsById[sessionID]?.title),
            detail: detail,
            options: question == null
                ? const []
                : notificationOptionsOf(question),
            silent: _quietNow,
            askAction: ask?.action ?? '',
            askTarget: ask?.target ?? '',
          )
          .then((shown) {
            if (!shown &&
                !_disposed &&
                _lifecycleWasBackgrounded &&
                _alertedInputKinds[sessionID] == alerted) {
              _alertedInputKinds.remove(sessionID);
            }
          }),
    );
  }

  void _syncInputAlerts() {
    final permissionSessions = {
      for (final permission in awaitingPermissions) permission.sessionID,
    }..removeWhere((id) => id.isEmpty);
    final questionSessions = {
      for (final question in questions.values) question.sessionID,
      // Pending forms alert like questions (kind `question`, no quick
      // reply); global forms have no session to alert on.
      for (final form in forms.values)
        if (form.sessionID != 'global') form.sessionID,
    }..removeWhere((id) => id.isEmpty);
    final pendingSessions = {...permissionSessions, ...questionSessions};

    for (final sessionID in _alertedInputKinds.keys.toList()) {
      if (pendingSessions.contains(sessionID)) continue;
      _alertedInputKinds.remove(sessionID);
      unawaited(backgroundLive.dismissCodingAlert(_inputAlertKey(sessionID)));
    }
    if (!_canShowCodingAlert) return;
    for (final sessionID in pendingSessions) {
      _showInputAlert(
        sessionID,
        permissionSessions.contains(sessionID)
            ? CodingAlertKind.permission
            : CodingAlertKind.question,
      );
    }
  }

  /// The person opened [sessionID]: its finished or stopped alert has done
  /// its job.
  void _dismissFinishedAlert(String sessionID) {
    if (_alertedStatusSessions.remove(sessionID)) {
      unawaited(backgroundLive.dismissCodingAlert(_statusAlertKey(sessionID)));
    }
  }

  void _dismissSessionCodingAlerts(String sessionID) {
    _attentionActiveSessions.remove(sessionID);
    if (_alertedInputKinds.remove(sessionID) != null) {
      unawaited(backgroundLive.dismissCodingAlert(_inputAlertKey(sessionID)));
    }
    if (_alertedStatusSessions.remove(sessionID)) {
      unawaited(backgroundLive.dismissCodingAlert(_statusAlertKey(sessionID)));
    }
  }

  void _dismissAllCodingAlerts({bool clearActive = false}) {
    for (final sessionID in _alertedInputKinds.keys.toList()) {
      unawaited(backgroundLive.dismissCodingAlert(_inputAlertKey(sessionID)));
    }
    for (final sessionID in _alertedStatusSessions.toList()) {
      unawaited(backgroundLive.dismissCodingAlert(_statusAlertKey(sessionID)));
    }
    _alertedInputKinds.clear();
    _alertedStatusSessions.clear();
    _dismissTeamAlerts();
    if (clearActive) _attentionActiveSessions.clear();
  }
}
