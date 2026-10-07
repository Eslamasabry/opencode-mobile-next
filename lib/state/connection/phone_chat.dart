part of '../connection.dart';

// The phone project engine and phone chat status.

const _phoneChatObservationLimit = Duration(seconds: 15);

const _phoneChatPollTimeout = Duration(seconds: 4);

const _openTurnGrace = Duration(minutes: 3);

/// [ConnectionController]'s phone project engine and phone chat.
mixin _ConnectionControllerPhoneChat on ChangeNotifier {
  ConnectionController get _self;

  String? _phoneChatOwner;
  bool _phoneChatStatusKnown = false;
  int _phoneChatUnknownRevision = 0;
  int _phoneChatStatusReadRevision = 0;
  Stopwatch? _phoneChatObservationAge;
  (String?, String?, String?)? _phoneChatObservationScope;
  String? _phoneChatDispatchProfile;
  final _phoneChatDispatch = PhoneChatDispatchTracker();

  /// A chat reply is on the wire: busy on the server, or a prompt sent that
  /// the server has not reported busy yet. The same signal the chat uses to
  /// keep its stop button, so nothing that must not cut a reply short can
  /// miss the first seconds of one.
  ///
  /// Only the person's own conversations count: the AI Team's own sessions
  /// (its planner and workers, in the team's folders) never hold a flow that
  /// waits for "your reply". A session whose folder is not known yet counts.
  bool get replyInFlight => _self._replyInFlight;

  /// Sessions with a prompt just sent (by this phone's chat, or seen as a
  /// new user message on the live stream) that the server has not yet
  /// reported busy. It bridges the first seconds of a reply, which a busy
  /// status alone can miss; a session going idle closes it and it never
  /// outlives [_openTurnGrace], so a lost event cannot hold a flow forever.
  final Map<String, DateTime> _openTurns = {};
  TurnStallTracker _turnStalls = TurnStallTracker();
  final _turnStallSessions = <String>{};
  Timer? _turnStallTimer;
  Future<TurnStallEvidence> Function()? _turnStallProbe;
  bool _turnStallProbing = false;
  bool? _turnStallTransportConnected;

  TurnStallDiagnosis? turnStallFor(String sessionId) =>
      _turnStalls.diagnosisFor(sessionId);

  @visibleForTesting
  void configureTurnStallForTesting({
    required Duration Function() elapsed,
    required Future<TurnStallEvidence> Function() probe,
  }) {
    _self._resetTurnStalls();
    _turnStalls = TurnStallTracker(elapsed: elapsed);
    _turnStallProbe = probe;
  }

  /// The chat just put a prompt for [sessionId] on the wire.
  void noteLocalTurn(String sessionId) {
    if (sessionId.isEmpty) return;
    _openTurns[sessionId] = DateTime.now();
    _self._syncTurnStalls();
  }
}

extension _ConnectionControllerPhoneChatImpl on ConnectionController {
  /// The body of [replyInFlight].
  bool get _replyInFlight =>
      busySessions.any(_isPersonsSession) ||
      _phoneChatDispatch.sessionIds.any(_isPersonsSession) ||
      _openTurns.entries.any(
        (e) =>
            _isPersonsSession(e.key) &&
            DateTime.now().difference(e.value) < _openTurnGrace,
      );

  bool _isPersonsSession(String sessionId) =>
      !isAiTeamDirectory(sessionsById[sessionId]?.directory);

  bool _phoneChatEligible(String id) {
    final owner = _connectedProfile;
    return !_disposed &&
        !isIsolated &&
        owner?.id == id &&
        owner?.backend == ServerBackend.openCode &&
        owner?.flavor == ServerFlavor.v1 &&
        BuiltinLinux.managesServerUrl(owner?.baseUrl) &&
        owner?.orchestration?.provider == OrchestrationProvider.phoneEngine &&
        !_deletingReadProfiles.contains(id);
  }

  void _invalidatePhoneChatStatus() {
    _phoneChatStatusKnown = false;
    _phoneChatUnknownRevision++;
  }

  PhoneChatActivity _phoneChatSnapshot(String id) {
    if (!_phoneChatEligible(id)) {
      return const PhoneChatActivity();
    }
    final dirs = <String>{if (directory?.isNotEmpty == true) directory!};
    final sessions = <String>{
      ...busySessions,
      ..._phoneChatDispatch.sessionIds,
    };
    dirs.addAll(_phoneChatDispatch.directories.where((d) => d.isNotEmpty));
    for (final session in sessions) {
      final where = sessionsById[session]?.directory;
      if (where != null && where.isNotEmpty) {
        dirs.add(where);
      }
    }
    return PhoneChatActivity(
      known:
          _phoneChatStatusKnown &&
          _phoneChatObservationScope ==
              (_connectedProfile?.id, directory, workspace) &&
          _phoneChatObservationAge != null &&
          _phoneChatObservationAge!.elapsed <= _phoneChatObservationLimit &&
          status == StreamStatus.connected &&
          !_lifecycleSuspended &&
          !locationLoading &&
          directory?.isNotEmpty == true,
      sessionIds: sessions.toList()..sort(),
      directories: dirs.toList()..sort(),
    );
  }

  void _syncPhoneChatHeartbeat() {
    final id = _connectedProfile?.id;
    final next = id != null && _phoneChatEligible(id) ? id : null;
    if (_phoneChatOwner != next) {
      final previous = _phoneChatOwner;
      _phoneChatOwner = next;
      if (previous != null) {
        final switchingPhoneAlias =
            !_disposed &&
            id != null &&
            id != previous &&
            BuiltinLinux.managesServerUrl(_connectedProfile?.baseUrl);
        if (switchingPhoneAlias) {
          unawaited(
            phoneProjectEngine
                .suspendChatAdmission(previous)
                .catchError((Object _) {}),
          );
        } else {
          unawaited(phoneProjectEngine.stopChatHeartbeat(previous));
        }
      }
    }
    if (next != null) {
      unawaited(
        phoneProjectEngine.pushChatHeartbeat(next).catchError((Object _) {}),
      );
    }
  }

  void _reconcilePhoneChat(
    Map<String, String>? statuses,
    int readEpoch, {
    Stopwatch? observationAge,
    int? unknownRevision,
  }) {
    final age = observationAge ?? (Stopwatch()..start());
    _phoneChatStatusKnown =
        (unknownRevision == null ||
            unknownRevision == _phoneChatUnknownRevision) &&
        statuses != null &&
        statuses.length <= 1000 &&
        statuses.keys.every(
          (id) =>
              id.isNotEmpty && id.length <= 256 && !id.contains(RegExp(r'\s')),
        ) &&
        statuses.values.every(
          (s) => s == 'idle' || s == 'busy' || s == 'retry',
        ) &&
        age.elapsed <= _phoneChatObservationLimit;
    _phoneChatObservationAge = _phoneChatStatusKnown ? age : null;
    _phoneChatObservationScope = _phoneChatStatusKnown
        ? (_connectedProfile?.id, directory, workspace)
        : null;
    if (!_phoneChatStatusKnown) {
      return;
    }
    _phoneChatDispatch.reconcile(statuses!, readEpoch, directory);
  }

  Future<void> _beforePhoneChatDispatch(
    ServerProfile owner,
    OpenCodeApi transport,
    String sessionId,
  ) async {
    try {
      if (_disposed ||
          !identical(api, transport) ||
          _connectedProfile?.id != owner.id) {
        throw const PhoneEngineException('chatTransportRetired');
      }
      await phoneProjectEngine.preparePhoneAliasDispatch(owner.id);
      if (_connectedProfile?.orchestration?.provider !=
          OrchestrationProvider.phoneEngine) {
        return;
      }
      if (!_phoneChatEligible(owner.id) || !identical(api, transport)) {
        throw const PhoneEngineException('chatTransportRetired');
      }
      final generation = _generation;
      _phoneChatDispatch.begin(sessionId, transport.directory);
      _notifyListeners();
      try {
        await phoneProjectEngine.beforePersonDispatch(owner.id);
        if (!_isCurrent(generation, transport) ||
            !_phoneChatEligible(owner.id)) {
          throw const PhoneEngineException('chatTransportRetired');
        }
      } catch (_) {
        // The callback failed before OpenCode transport could send anything.
        _phoneChatDispatchSettled(sessionId, removeUnsent: true);
        rethrow;
      }
    } on PhoneEngineException catch (error) {
      throw ApiException(
        error.code == 'chatTransportRetired'
            ? 'The chat connection changed. Reconnect before sending.'
            : 'AI Team could not pause safely. Stop AI Team before sending.',
      );
    }
  }

  void _phoneChatDispatchSettled(
    String sessionId, {
    bool removeUnsent = false,
  }) {
    _phoneChatDispatch.settled(sessionId, removeUnsent: removeUnsent);
    // HTTP acceptance/error does not assert idle; the next fresh status read owns it.
    _notifyListeners();
  }
}
