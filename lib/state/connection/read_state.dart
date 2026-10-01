part of '../connection.dart';

// Read state, the return brief and shared session views.

/// [ConnectionController]'s read state and return brief.
mixin _ConnectionControllerReadState on ChangeNotifier {
  ConnectionController get _self;

  bool get supportsSessionReadState =>
      _self.repository is SessionReadStateGateway;

  /// Saved scope excludes connection epochs so dismissal survives recovery;
  /// callbacks additionally retain the location revision to reject old UI.
  (String, String, int) get returnBriefScope => _self._returnBriefScope;

  ReturnBriefAck get returnBriefAcknowledgement {
    final scope = returnBriefScope;
    return _self._returnBriefStore.acknowledged(scope.$1, scope.$2);
  }

  Future<void> dismissReturnBrief(
    ReturnBrief shown, {
    required (String, String, int) expectedScope,
  }) => _self._dismissReturnBrief(shown, expectedScope: expectedScope);
  bool get shareSessionViews => _self._shareSessionViews;
  bool _savingReadPrivacy = false;
  bool get savingReadPrivacy => _savingReadPrivacy;
  int _readPrivacyRevision = 0;
  int get readPrivacyRevision => _readPrivacyRevision;
  final _viewOperations = <Object, Future<void>>{};
  final _deletingReadProfiles = <String>{};
  final _closedQueueProfiles = <String>{};
  final _profileDeletionRevisions = <String, int>{};
  final _profileDeletions = <String, Future<DeleteProfileResult>>{};
  Future<void> _profileDeletionChanges = Future.value();

  Future<void> setShareSessionViews(bool value) =>
      _self._setShareSessionViews(value);

  bool isSessionUnread(Session session) => _self._isSessionUnread(session);

  /// Called only by a visible, loaded chat. Refresh/polling never invokes it.
  /// Recheck visibility and privacy after wake, then acknowledge the exact
  /// observed completion; a newer idle transition remains unread.
  Future<void> viewSession(
    String id, {
    required bool Function() isForeground,
    int? observedIdle,
    int? expectedLocationRevision,
  }) => _self._viewSession(
    id,
    isForeground: isForeground,
    observedIdle: observedIdle,
    expectedLocationRevision: expectedLocationRevision,
  );
}

extension _ConnectionControllerReadStateImpl on ConnectionController {
  /// The body of [returnBriefScope].
  (String, String, int) get _returnBriefScope {
    final owner = _connectedProfile ?? profile;
    return (
      owner?.id ?? '',
      jsonEncode([owner?.baseUrl, directory, workspace]),
      locationRevision,
    );
  }

  /// The body of [dismissReturnBrief].
  Future<void> _dismissReturnBrief(
    ReturnBrief shown, {
    required (String, String, int) expectedScope,
  }) async {
    if (returnBriefScope != expectedScope ||
        !isProfileReadable(expectedScope.$1)) {
      throw StateError('The project changed. Review its current brief.');
    }
    await _returnBriefStore.acknowledge(
      expectedScope.$1,
      expectedScope.$2,
      shown,
    );
    if (!_disposed) _notifyListeners();
  }

  bool _readProfileAvailable(String id) =>
      !_deletingReadProfiles.contains(id) &&
      (id.isEmpty || store.profiles.any((profile) => profile.id == id));

  /// The body of [setShareSessionViews].
  Future<void> _setShareSessionViews(bool value) async {
    if (_savingReadPrivacy) return;
    final previous = _shareSessionViews;
    if (previous == value) return;
    _shareSessionViews = value;
    _savingReadPrivacy = true;
    final revision = ++_readPrivacyRevision;
    _notifyListeners();
    try {
      if (!await store.prefs.setBool('oc.shareSessionViews', value)) {
        throw StateError('Could not save the read-state preference');
      }
    } catch (_) {
      // A failed opt-out stays private in this process. Turning sharing on
      // requires a successful saved preference before observers may send.
      if (_readPrivacyRevision == revision) {
        _shareSessionViews = false;
        _readPrivacyRevision++;
        _notifyListeners();
      }
      rethrow;
    } finally {
      _savingReadPrivacy = false;
      _readPrivacyRevision++;
      if (!_disposed) _notifyListeners();
    }
  }

  (String, String) _sessionReadKey(Session session) {
    final currentProfile = _connectedProfile ?? profile;
    return (
      currentProfile?.id ?? '',
      jsonEncode([
        currentProfile?.baseUrl,
        session.directory ?? directory,
        session.workspaceID ?? workspace,
        session.id,
      ]),
    );
  }

  /// The body of [isSessionUnread].
  bool _isSessionUnread(Session session) {
    if (!supportsSessionReadState || busySessions.contains(session.id)) {
      return false;
    }
    final (profileID, key) = _sessionReadKey(session);
    final cached = sessionsById[session.id];
    final matching =
        cached != null && _sessionReadKey(cached) == (profileID, key);
    final idle = _newerWatermark(
      session.time?.idle,
      matching ? cached.time?.idle : null,
    );
    final local = _sessionReadStore.viewed(profileID, key);
    final remote = shareSessionViews
        ? _newerWatermark(
            session.time?.viewed,
            matching ? cached.time?.viewed : null,
          )
        : 0;
    return idle > local && idle > remote;
  }

  int _newerWatermark(int? a, int? b) => (a ?? 0) > (b ?? 0) ? a! : b ?? 0;

  Session _preserveReadState(Session incoming) {
    final previous = sessionsById[incoming.id];
    if (previous == null ||
        !supportsSessionReadState ||
        _sessionReadKey(previous) != _sessionReadKey(incoming)) {
      return incoming;
    }
    return incoming.copyWith(
      time: (incoming.time ?? SessionTime()).withReadState(
        idle: _newerWatermark(previous.time?.idle, incoming.time?.idle),
        viewed: _newerWatermark(previous.time?.viewed, incoming.time?.viewed),
      ),
    );
  }

  void _applySessionViewed(String id, int idle) {
    final previous = sessionsById[id] ?? Session(id: id);
    if ((previous.time?.viewed ?? 0) >= idle) return;
    _markSessionChanged(id, affectsStatus: false);
    sessionsById[id] = previous.copyWith(
      time: (previous.time ?? SessionTime()).withReadState(viewed: idle),
    );
    _notifyListeners();
    if (previous.time?.idle == null) unawaited(_refreshOneSession(id));
  }

  /// The body of [viewSession].
  Future<void> _viewSession(
    String id, {
    required bool Function() isForeground,
    int? observedIdle,
    int? expectedLocationRevision,
  }) {
    if (expectedLocationRevision != null &&
        expectedLocationRevision != locationRevision) {
      return Future.value();
    }
    final session = sessionsById[id];
    final idle = observedIdle ?? session?.time?.idle;
    if (!supportsSessionReadState ||
        session == null ||
        idle == null ||
        idle <= 0 ||
        idle > (session.time?.idle ?? 0) ||
        busySessions.contains(id) ||
        !isForeground()) {
      return Future.value();
    }
    final scope = locationRevision;
    final privacy = _readPrivacyRevision;
    final (profileID, localKey) = _sessionReadKey(session);
    if (!_readProfileAvailable(profileID)) return Future.value();
    final operationKey = (scope, privacy, id, idle);
    final existing = _viewOperations[operationKey];
    if (existing != null) {
      // A newly opened chat must recheck its own visibility after an older
      // viewer's wake finishes; that viewer may have been disposed meanwhile.
      return existing.then(
        (_) => viewSession(
          id,
          isForeground: isForeground,
          observedIdle: idle,
          expectedLocationRevision: scope,
        ),
      );
    }
    final completion = Completer<void>();
    _viewOperations[operationKey] = completion.future;
    bool current() =>
        !_disposed &&
        locationRevision == scope &&
        _readPrivacyRevision == privacy &&
        isForeground() &&
        !busySessions.contains(id) &&
        _readProfileAvailable(profileID) &&
        !_deletedSessionIDs.contains(id) &&
        _sessionReadKey(sessionsById[id] ?? session) == (profileID, localKey);
    () async {
      try {
        // This device saw this run, even with sharing disabled or a failed
        // connection. The local cache never queues a server write.
        await _sessionReadStore.record(profileID, localKey, idle);
        if (!current()) {
          completion.complete();
          return;
        }
        _notifyListeners();
        if (shareSessionViews &&
            !_savingReadPrivacy &&
            (sessionsById[id]?.time?.viewed ?? 0) < idle) {
          final transport = await prepareActionRepository();
          if (!current() || !shareSessionViews || _savingReadPrivacy) {
            completion.complete();
            return;
          }
          if (transport is! SessionReadStateGateway) {
            completion.complete();
            return;
          }
          await (transport as SessionReadStateGateway).viewSession(id, idle);
          if (current() && identical(repository, transport)) {
            _applySessionViewed(id, idle);
          }
        }
        completion.complete();
      } catch (error, stack) {
        completion.completeError(error, stack);
      } finally {
        if (identical(_viewOperations[operationKey], completion.future)) {
          _viewOperations.remove(operationKey);
        }
      }
    }();
    return completion.future;
  }
}
