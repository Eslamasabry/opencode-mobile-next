part of '../connection.dart';

// The session list: refresh, paging, busy status, create, rename, delete and pins.

const _maxObservedCompletedMessages = 512;

/// [ConnectionController]'s session list.
mixin _ConnectionControllerSessions on ChangeNotifier {
  ConnectionController get _self;

  bool sessionsLoading = false;
  String? sessionsError;

  Map<String, Session> sessionsById = {};
  String? _sessionsCursor;
  bool get hasMoreSessions => _sessionsCursor != null;
  bool sessionsLoadingMore = false;
  String? sessionsMoreError;
  bool sessionsNeedReload = false;
  final Set<String> _sessionPageIDs = {};
  final Set<String> _sessionInventoryIDs = {};
  bool _sessionInventoryInitialized = false;
  final Set<String> _usedSessionCursors = {};
  int _sessionSnapshotRevision = 0;
  final Map<(ServerGateway, int, String, int), Future<void>> _sessionReads = {};
  final Map<String, String> sessionDetailsErrors = {};
  final Set<String> _deletedSessionIDs = {};
  Set<String> busySessions = {};

  /// Assistant message ids whose completion (or error) this phone received
  /// as a live `message.updated` event on the current connection. Bound to
  /// the exact message record, never to a session-level idle timestamp, so a
  /// run-results view can say "observed live" only for that step. In-memory,
  /// bounded, and cleared with the rest of the connection state.
  final Set<String> observedCompletedMessageIDs = {};

  /// Sessions currently in provider-retry backoff, keyed by session ID.
  /// Populated from `session.status` `{type: 'retry'}` (v1 and v2), the v2
  /// `session.retry.scheduled` event, and the v1 status endpoint on refresh;
  /// an entry is removed as soon as the session reports busy or idle. Retry
  /// sessions remain in [busySessions] as before.
  Map<String, SessionRetryState> retryStates = {};

  // ---------------- Sessions ----------------

  Future<void> refreshSessions() =>
      PerfTrace.span('sessions.refresh', _self._refreshSessions);

  Future<void> loadMoreSessions() => _self._loadMoreSessions();

  /// Direct routes and active sessions are independent of inventory pages.
  Future<void> ensureSession(String id) {
    // An opened conversation is seen: its Done tag goes.
    if (_self._finishedUnseen.remove(id)) _self._notifyListeners();
    return _self._refreshOneSession(id);
  }

  /// Polling fallback plus terminal-state reconciliation for connected SSE.
  void enablePollingFallback() => _self._enablePollingFallback();

  /// Busy conversations the status check has seen idle once; see
  /// [_reconcileBusySessionStatuses].
  final _idleOnce = <String>{};

  @visibleForTesting
  Future<void> reconcileBusySessionsForTesting() =>
      _self._refreshBusySessionStatuses();

  Future<Session> createSession() =>
      PerfTrace.span('session.create', _self._createSession);

  Future<void> renameSession(String sessionID, String title) =>
      _self._renameSession(sessionID, title);

  Future<void> deleteSession(String sessionID) =>
      _self._deleteSession(sessionID);

  List<Session>? _sortedSessionInputs;
  Set<String>? _sortedSessionPins;
  List<Session> _sortedSessionResult = const [];

  /// Immutable ordering snapshot. Session and SessionTime ordering fields are
  /// final; compare eligible object identities in input order so even callers
  /// mutating the public map, equal-time reorders and inventory changes count.
  /// Status-only notifications retain this list, without suppressing listeners.
  List<Session> sortedSessions() {
    final pins = pinnedSessionIDs;
    final inputs = sessionsById.values
        .where(
          (s) =>
              s.parentID == null &&
              !s.archived &&
              (!_sessionInventoryInitialized ||
                  _sessionInventoryIDs.contains(s.id)),
        )
        .toList();
    if (listEquals(inputs, _sortedSessionInputs) &&
        setEquals(pins, _sortedSessionPins)) {
      return _sortedSessionResult;
    }
    _sortedSessionInputs = inputs;
    _sortedSessionPins = pins;
    final sorted = List<Session>.of(inputs)
      ..sort((a, b) {
        final pinOrder =
            (pins.contains(b.id) ? 1 : 0) - (pins.contains(a.id) ? 1 : 0);
        if (pinOrder != 0) return pinOrder;
        final au = a.time?.updated ?? a.time?.created ?? 0;
        final bu = b.time?.updated ?? b.time?.created ?? 0;
        return bu.compareTo(au);
      });
    return _sortedSessionResult = List.unmodifiable(sorted);
  }

  Set<String> get pinnedSessionIDs => _self._pinnedSessionIDs;
  bool isSessionPinned(String id) => pinnedSessionIDs.contains(id);
  bool get canPinSessions =>
      _self._pinProfile.isNotEmpty &&
      !_self._deletingReadProfiles.contains(_self._pinProfile);

  Future<void> setSessionPinned(
    String id,
    bool pinned, {
    required int locationRevision,
  }) => _self._setSessionPinned(id, pinned, locationRevision: locationRevision);

  bool get pinnedSessionsLoadFailed =>
      pinnedSessionIDs.any((id) => sessionDetailsErrors.containsKey(id));

  List<Session> archivedSessions() {
    final list =
        sessionsById.values
            .where(
              (session) =>
                  session.archived &&
                  (!_sessionInventoryInitialized ||
                      _sessionInventoryIDs.contains(session.id)),
            )
            .toList()
          ..sort(
            (a, b) => (b.time?.archived ?? 0).compareTo(a.time?.archived ?? 0),
          );
    return list;
  }
}

extension _ConnectionControllerSessionsImpl on ConnectionController {
  void _noteObservedCompletion(String messageID) {
    if (messageID.isEmpty) return;
    if (observedCompletedMessageIDs.length >= _maxObservedCompletedMessages) {
      observedCompletedMessageIDs.remove(observedCompletedMessageIDs.first);
    }
    observedCompletedMessageIDs.add(messageID);
  }

  Future<void> _refreshSessions() async {
    final currentApi = api;
    final generation = _generation;
    if (currentApi == null) return;
    final refreshGeneration = ++_sessionsRefreshGeneration;
    final revision = _sessionRevision;
    final chatReadEpoch = _phoneChatDispatch.epoch;
    sessionsLoading = true;
    sessionsLoadingMore = false;
    sessionsError = null;
    sessionsMoreError = null;
    _notifyListeners();
    try {
      // Both reads describe this location independently. Attach the status
      // error handler immediately so even a failed/retired page cannot leave
      // an unhandled background error. SSE revisions still win below.
      final chatObservationAge = Stopwatch()..start();
      final chatUnknownRevision = _phoneChatUnknownRevision;
      final chatStatusReadRevision = ++_phoneChatStatusReadRevision;
      final statusRead = () async {
        try {
          return (await currentApi.sessionStatuses(), null);
        } catch (error) {
          return (null, error);
        }
      }();
      final page = await currentApi.sessionPage();
      if (!_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      final (statuses, statusError) = await statusRead;
      // Retry details ride on the same v1 status payload; fetch them only
      // when a session is actually retrying so the common path stays one
      // request.
      Map<String, SessionRetryState>? retries;
      if (statuses != null &&
          statuses.values.contains('retry') &&
          currentApi is SessionRetryGateway) {
        try {
          retries = await (currentApi as SessionRetryGateway)
              .sessionRetryStates();
        } catch (_) {
          retries = null;
        }
      }
      if (!_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      _sessionSnapshotRevision = revision;
      _sessionPageIDs.clear();
      _usedSessionCursors.clear();
      _mergeSessionPage(page, revision);
      sessionsMoreError = null;
      sessionsNeedReload = false;
      final phoneStatusCurrent =
          _connectedProfile == null ||
          !_phoneChatEligible(_connectedProfile!.id) ||
          chatStatusReadRevision == _phoneChatStatusReadRevision;
      if (statuses != null && phoneStatusCurrent) {
        final statusIDs = {
          ...sessionsById.keys,
          ...busySessions,
          ...statuses.keys,
        };
        for (final id in statusIDs) {
          if ((_sessionStatusRevisions[id] ?? 0) > revision) continue;
          if (statuses[id] != null && statuses[id] != 'idle') {
            busySessions.add(id);
            _markSessionAttentionActive(id);
            if (!sessionsById.containsKey(id)) {
              unawaited(_refreshOneSession(id));
            }
          } else {
            busySessions.remove(id);
            _openTurns.remove(id);
            _settleSessionAttention(id, CodingAlertKind.complete);
          }
          if (statuses[id] == 'retry') {
            final retry = retries?[id];
            if (retry != null) {
              retryStates[id] = retry;
            } else if (retries != null) {
              retryStates.remove(id);
            }
          } else {
            retryStates.remove(id);
          }
        }
      }
      if (phoneStatusCurrent) {
        _reconcilePhoneChat(
          statuses,
          chatReadEpoch,
          observationAge: chatObservationAge,
          unknownRevision: chatUnknownRevision,
        );
      }
      sessionsLoading = false;
      sessionsError = statusError?.toString();
      if (statusError != null) _recordLocationError(sessionsError!);
      _notifyListeners();
      _saveSessionInventoryPreview();
      unawaited(_refreshPinnedSessions());
    } catch (error) {
      if (!_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      _invalidatePhoneChatStatus();
      sessionsLoading = false;
      sessionsError = error.toString();
      _recordLocationError(sessionsError!);
      _notifyListeners();
    }
  }

  void _removeSession(String id) {
    _failedAttentionSessions.remove(id);
    _markSessionChanged(id);
    _deletedSessionIDs.add(id);
    final tailOwner = _connectedProfile ?? profile;
    if (tailOwner != null) {
      unawaited(_sessionTailCache.removeSession(tailOwner.id, id));
    }
    sessionsById.remove(id);
    sessionDetailsErrors.remove(id);
    _sessionInventoryIDs.remove(id);
    _forgetSessionModel(id);
    busySessions.remove(id);
    _openTurns.remove(id);
    retryStates.remove(id);
    _dismissSessionCodingAlerts(id);
    permissions.removeWhere((_, value) => value.sessionID == id);
    _autoApprovingPermissionIDs.removeWhere(
      (requestID) => !permissions.containsKey(requestID),
    );
    _autoApprovalFailures.removeWhere(
      (requestID, _) => !permissions.containsKey(requestID),
    );
    _autoApprovedBySession.remove(id);
    final removedQuestionIDs = questions.entries
        .where((entry) => entry.value.sessionID == id)
        .map((entry) => entry.key)
        .toList();
    for (final questionID in removedQuestionIDs) {
      _markQuestionChanged(questionID);
      questions.remove(questionID);
    }
    _legacyPermissionIdentities.removeWhere(
      (_, identity) => identity.sessionID == id,
    );
    _v2PermissionSessions.removeWhere((_, sessionID) => sessionID == id);
    _v2QuestionSessions.removeWhere((_, sessionID) => sessionID == id);
    final removedFormIDs = forms.values
        .where((form) => form.sessionID == id)
        .map((form) => form.id)
        .toList();
    if (removedFormIDs.isNotEmpty) _formRevision += 1;
    for (final formID in removedFormIDs) {
      forms.remove(formID);
    }
    if (_inboxBySession.remove(id) != null) inboxRevision += 1;
    _syncInputAlerts();
    _saveSessionInventoryPreview();
    _notifyListeners();
  }

  void _mergeSessionPage(ServerPage<Session> page, int revision) {
    if (!_sessionInventoryInitialized) {
      for (final session in sessionsById.values) {
        _rememberSessionMembership(session);
      }
      _sessionInventoryInitialized = true;
    }
    for (final session in page.items) {
      _sessionPageIDs.add(session.id);
      if (_deletedSessionIDs.contains(session.id)) continue;
      if ((_sessionRevisions[session.id] ?? 0) <= revision) {
        sessionsById[session.id] = _preserveReadState(session);
        _sessionInventoryIDs.add(session.id);
      }
    }
    _sessionsCursor = page.hasMore ? page.nextCursor : null;
    // Absence is meaningful only after walking the entire inventory. A
    // partial head refresh must not delete older cached chats or their alerts.
    if (!page.hasMore) {
      for (final id in _sessionInventoryIDs.toList()) {
        if (!_sessionPageIDs.contains(id) &&
            (_sessionRevisions[id] ?? 0) <= _sessionSnapshotRevision) {
          _sessionInventoryIDs.remove(id);
        }
      }
    }
  }

  void _rememberSessionMembership(
    Session session, {
    bool authoritative = false,
  }) {
    if ((directory == null || session.directory == directory) &&
        (workspace == null || session.workspaceID == workspace)) {
      _sessionInventoryIDs.add(session.id);
    } else if (authoritative &&
        ((directory != null &&
                session.directory != null &&
                session.directory != directory) ||
            (workspace != null && session.workspaceID != workspace))) {
      _sessionInventoryIDs.remove(session.id);
    }
  }

  /// The body of [loadMoreSessions].
  Future<void> _loadMoreSessions() async {
    if (sessionsLoading || sessionsLoadingMore) return;
    if (sessionsNeedReload) {
      await refreshSessions();
      return;
    }
    final cursor = _sessionsCursor;
    final currentApi = api;
    if (cursor == null || currentApi == null) return;
    final generation = _generation;
    final refreshGeneration = ++_sessionsRefreshGeneration;
    final revision = _sessionRevision;
    sessionsLoadingMore = true;
    sessionsMoreError = null;
    _notifyListeners();
    try {
      final page = await currentApi.sessionPage(cursor: cursor);
      if (!_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      if (page.hasMore &&
          (page.nextCursor == cursor ||
              _usedSessionCursors.contains(page.nextCursor))) {
        sessionsNeedReload = true;
        throw const ProductException(
          'The session list changed. Reload recent sessions to continue.',
        );
      }
      _usedSessionCursors.add(cursor);
      _mergeSessionPage(page, revision);
    } catch (error) {
      if (!_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      sessionsMoreError = error.toString();
      if (error is ApiException &&
          (error.statusCode == 400 || error.statusCode == 410)) {
        sessionsNeedReload = true;
      }
    } finally {
      if (_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        sessionsLoadingMore = false;
        _notifyListeners();
      }
    }
  }

  Future<void> _refreshOneSession(String id) {
    final currentApi = api;
    if (currentApi == null) return Future.value();
    final key = (currentApi, _generation, id, _sessionRevisions[id] ?? 0);
    final pending = _sessionReads[key];
    if (pending != null) return pending;
    late final Future<void> tracked;
    tracked = _readOneSession(id).whenComplete(() {
      if (identical(_sessionReads[key], tracked)) _sessionReads.remove(key);
    });
    _sessionReads[key] = tracked;
    return tracked;
  }

  Future<void> _readOneSession(String id) async {
    final currentApi = api;
    final generation = _generation;
    if (currentApi == null) return;
    final revision = _sessionRevisions[id] ?? 0;
    try {
      final session = await currentApi.session(id);
      if (!_isCurrent(generation, currentApi) ||
          _deletedSessionIDs.contains(id) ||
          revision != (_sessionRevisions[id] ?? 0)) {
        return;
      }
      final revertChanged =
          sessionsById[id]?.stagedRevert?.fingerprint !=
          session.stagedRevert?.fingerprint;
      sessionsById[id] = _preserveReadState(session);
      sessionDetailsErrors.remove(id);
      _rememberSessionMembership(session, authoritative: true);
      _markSessionChanged(id, affectsStatus: false);
      if (revertChanged) _resetSessionHistory(id);
      _notifyListeners();
    } on ApiException catch (error) {
      if (error.statusCode == 404 &&
          _isCurrent(generation, currentApi) &&
          revision == (_sessionRevisions[id] ?? 0)) {
        _removeSession(id);
      } else if (_isCurrent(generation, currentApi) &&
          revision == (_sessionRevisions[id] ?? 0)) {
        sessionDetailsErrors[id] = error.toString();
        _notifyListeners();
      }
    } catch (error) {
      if (_isCurrent(generation, currentApi) &&
          revision == (_sessionRevisions[id] ?? 0)) {
        sessionDetailsErrors[id] = error.toString();
        _notifyListeners();
      }
    }
  }

  /// The body of [enablePollingFallback].
  void _enablePollingFallback() {
    if (_poll?.isActive ?? false) return;
    _poll = Timer.periodic(const Duration(seconds: 5), (_) {
      final phoneAdmission =
          _connectedProfile != null &&
          _phoneChatEligible(_connectedProfile!.id) &&
          status == StreamStatus.connected &&
          !_lifecycleSuspended &&
          !locationLoading &&
          directory?.isNotEmpty == true;
      if (phoneAdmission) {
        // A lost status/page read while idle must not strand admission in
        // UNKNOWN. Read status alone, independently of a slow session page.
        unawaited(_refreshBusySessionStatuses());
      }
      if (sessionsLoading || sessionsLoadingMore) return;
      if (shouldPoll) {
        unawaited(refreshSessions());
      } else if (!phoneAdmission &&
          (busySessions.isNotEmpty || _phoneChatDispatch.isNotEmpty)) {
        // An otherwise healthy SSE connection can still lose one terminal
        // event during a network handoff. Reconcile only active sessions so a
        // missed `idle` cannot leave the chat thinking forever.
        unawaited(_refreshBusySessionStatuses());
      }
    });
  }

  Future<void> _refreshBusySessionStatuses() {
    final inFlight = _busyStatusRefresh;
    if (inFlight != null) return inFlight;
    late final Future<void> tracked;
    tracked = _reconcileBusySessionStatuses().whenComplete(() {
      if (identical(_busyStatusRefresh, tracked)) {
        _busyStatusRefresh = null;
      }
    });
    _busyStatusRefresh = tracked;
    return tracked;
  }

  Future<void> _reconcileBusySessionStatuses() async {
    final currentApi = api;
    final generation = _generation;
    final scope = (
      _connectedProfile?.id,
      directory,
      workspace,
      locationRevision,
    );
    final phoneAdmission =
        _connectedProfile != null &&
        _phoneChatEligible(_connectedProfile!.id) &&
        status == StreamStatus.connected &&
        !_lifecycleSuspended &&
        !locationLoading &&
        directory?.isNotEmpty == true;
    final tracked = {
      for (final id in {...busySessions, ..._phoneChatDispatch.sessionIds})
        id: _sessionStatusRevisions[id] ?? 0,
    };
    if (currentApi == null || (tracked.isEmpty && !phoneAdmission)) return;
    bool current() =>
        _isCurrent(generation, currentApi) &&
        scope ==
            (_connectedProfile?.id, directory, workspace, locationRevision) &&
        currentApi.directory == directory &&
        currentApi.workspace == workspace;
    final statusRevision = _sessionRevision;
    final chatUnknownRevision = _phoneChatUnknownRevision;
    final chatStatusReadRevision = ++_phoneChatStatusReadRevision;
    final chatReadEpoch = _phoneChatDispatch.epoch;
    final observationAge = Stopwatch()..start();
    Map<String, String> statuses;
    try {
      final read = currentApi.sessionStatuses();
      statuses = phoneAdmission
          ? await read.timeout(_phoneChatPollTimeout)
          : await read;
    } catch (_) {
      if (current()) {
        _invalidatePhoneChatStatus();
        _syncPhoneChatHeartbeat();
      }
      return;
    }
    if (!current() ||
        chatUnknownRevision != _phoneChatUnknownRevision ||
        (phoneAdmission &&
            chatStatusReadRevision != _phoneChatStatusReadRevision)) {
      return;
    }
    _reconcilePhoneChat(
      statuses,
      chatReadEpoch,
      observationAge: observationAge,
      unknownRevision: chatUnknownRevision,
    );
    if (phoneAdmission && !_phoneChatStatusKnown) {
      _syncPhoneChatHeartbeat();
      return;
    }
    var changed = false;
    if (phoneAdmission) {
      // A status-only poll may see a person session before any session page
      // or SSE metadata. Its busy/retry ID must block admission immediately.
      for (final entry in statuses.entries) {
        if (entry.value != 'busy' && entry.value != 'retry') continue;
        if ((_sessionStatusRevisions[entry.key] ?? 0) > statusRevision) {
          continue;
        }
        changed = busySessions.add(entry.key) || changed;
        _idleOnce.remove(entry.key);
      }
    }
    for (final entry in tracked.entries) {
      if ((_sessionStatusRevisions[entry.key] ?? 0) != entry.value) continue;
      final remoteStatus = statuses[entry.key] ?? 'idle';
      if (remoteStatus != 'idle') {
        _idleOnce.remove(entry.key);
        continue;
      }
      // An OpenCode 2 run is missing from the active list for a moment
      // between two steps. Taken at its word once, that ended a run that
      // was still going: no Stop, no progress, a "finished" turn, while the
      // agent kept working. Idle twice running is idle; a real end is
      // reported by the server's own event long before.
      if (_idleOnce.add(entry.key)) continue;
      _idleOnce.remove(entry.key);
      final removed = busySessions.remove(entry.key);
      _openTurns.remove(entry.key);
      changed = retryStates.remove(entry.key) != null || changed;
      changed = removed || changed;
      if (removed) {
        _settleSessionAttention(entry.key, CodingAlertKind.complete);
        _markSessionChanged(entry.key);
        unawaited(_refreshOneSession(entry.key));
      }
    }
    _syncPhoneChatHeartbeat();
    if (changed) _notifyListeners();
  }

  Future<ServerGateway> _requireActionTransport() async {
    final actionApi = await prepareActionTransport();
    if (actionApi != null) return actionApi;
    throw ApiException(
      connectionError ?? 'OpenCode is reconnecting. Try again shortly.',
    );
  }

  Future<Session> _createSession({bool Function()? scopeIsCurrent}) async {
    final currentApi = await _requireActionTransport();
    if (scopeIsCurrent != null && !scopeIsCurrent()) {
      throw const ProductException(
        'The connection or location changed. Start the task again.',
      );
    }
    final generation = _generation;
    final revision = _sessionRevision;
    final session = currentApi is SessionSelectionGateway
        ? await (currentApi as SessionSelectionGateway).createSelectedSession(
            SessionSelection(
              model: selectedModel,
              variant: selectedVariant,
              agent: selectedAgent,
            ),
          )
        : await currentApi.createSession();
    if (scopeIsCurrent != null && !scopeIsCurrent()) {
      throw const ProductException(
        'The connection or location changed while the session was created. '
        'Find it in the original worktree before using it.',
      );
    }
    if (_isCurrent(generation, currentApi) &&
        (_sessionRevisions[session.id] ?? 0) <= revision) {
      _markSessionChanged(session.id);
      sessionsById[session.id] = session;
      _sessionInventoryIDs.add(session.id);
      _notifyListeners();
    }
    return session;
  }

  /// The body of [renameSession].
  Future<void> _renameSession(String sessionID, String title) async {
    final currentApi = await _requireActionTransport();
    final generation = _generation;
    await currentApi.renameSession(sessionID, title);
    if (_isCurrent(generation, currentApi)) {
      _markSessionChanged(sessionID);
      await _refreshOneSession(sessionID);
    }
  }

  /// The body of [deleteSession].
  Future<void> _deleteSession(String sessionID) async {
    final currentApi = await _requireActionTransport();
    final generation = _generation;
    await currentApi.deleteSession(sessionID);
    if (_isCurrent(generation, currentApi)) _removeSession(sessionID);
  }

  String get _pinProfile => (_connectedProfile ?? profile)?.id ?? '';
  String get _pinScope => SessionPinStore.scope(directory, workspace);

  /// The body of [pinnedSessionIDs].
  Set<String> get _pinnedSessionIDs =>
      _pinProfile.isEmpty ? const {} : _sessionPins.ids(_pinProfile, _pinScope);

  /// The body of [setSessionPinned].
  Future<void> _setSessionPinned(
    String id,
    bool pinned, {
    required int locationRevision,
  }) async {
    if (!canPinSessions || this.locationRevision != locationRevision) {
      throw StateError('The session location changed');
    }
    final profileID = _pinProfile;
    await _sessionPins.setPinned(profileID, _pinScope, id, pinned);
    if (!_disposed) _notifyListeners();
  }

  Future<void> _refreshPinnedSessions() async {
    final currentApi = api;
    final generation = _generation;
    if (currentApi == null) return;
    for (final id in pinnedSessionIDs) {
      if (!_isCurrent(generation, currentApi)) return;
      if (!_sessionInventoryIDs.contains(id) ||
          sessionDetailsErrors.containsKey(id)) {
        await _refreshOneSession(id);
      }
    }
  }
}
