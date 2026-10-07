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
          if (question == null ||
              question.sessionID != action.sessionID ||
              !_questionSupportsQuickReply(question)) {
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

  bool get _canShowCodingAlert =>
      keepLiveInBackground &&
      _lifecycleWasBackgrounded &&
      backgroundLive.notificationGranted &&
      profileMonitor.rulesFor(_alertProfileId).notifications &&
      !profileMonitor.rulesFor(_alertProfileId).quietAt(DateTime.now());

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

  void _settleSessionAttention(String sessionID, CodingAlertKind kind) {
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
    if (!_alertedStatusSessions.add(sessionID)) return;
    unawaited(
      backgroundLive
          .showCodingAlert(
            kind: kind,
            profileID: _alertProfileId,
            agentName: isAgentBackend ? profile?.name ?? '' : '',
            sessionID: sessionID,
            key: _statusAlertKey(sessionID),
          )
          .then((shown) {
            if (!shown && !_disposed && _lifecycleWasBackgrounded) {
              _alertedStatusSessions.remove(sessionID);
            }
          }),
    );
  }

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
    // v2 permission alerts carry the RemoteInput Reply action: its text
    // maps to reject-with-message (see _handleCodingAlertAction).
    final permissionReply =
        kind == CodingAlertKind.permission &&
        _v2PermissionSessions.containsKey(requestID);
    final alerted = (kind: kind, requestID: requestID);
    if (_alertedInputKinds[sessionID] == alerted) return;
    _alertedInputKinds[sessionID] = alerted;
    unawaited(
      backgroundLive
          .showCodingAlert(
            kind: kind,
            profileID: _alertProfileId,
            agentName: isAgentBackend ? profile?.name ?? '' : '',
            sessionID: sessionID,
            key: _inputAlertKey(sessionID),
            quickReply: quickReplyQuestion != null || permissionReply,
            requestID: requestID,
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
