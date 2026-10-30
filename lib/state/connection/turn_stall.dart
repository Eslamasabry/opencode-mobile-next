part of '../connection.dart';

extension _ConnectionTurnStalls on ConnectionController {
  /// Stops the watch (its clock, probe and findings) without forgetting
  /// which turns are open and which of their tools run: a reconnect or a
  /// return from the background does not replay a tool that started before
  /// it, so a forgotten long command (a 15-minute test run) read as silence
  /// and blamed the model (owner, 2026-10-27). The next sync drops the
  /// sessions that are no longer active, with their tools.
  void _resetTurnStalls() {
    _turnStallTimer?.cancel();
    _turnStallTimer = null;
    _cancelTurnStallProbe();
    _turnStalls.clear();
    _turnStallTransportConnected = null;
    _turnStallRereadTicks = 0;
  }

  /// [_resetTurnStalls] that also forgets the open turns and their tools:
  /// another server, a disconnect, disposal.
  void _forgetTurnStalls() {
    _resetTurnStalls();
    _turnStallSessions.clear();
    _turnStallTools.clear();
  }

  void _cancelTurnStallProbe() {
    final cancellation = _turnStallProbeCancellation;
    _turnStallProbeCancellation = null;
    if (cancellation != null && !cancellation.isCompleted) {
      cancellation.complete();
    }
  }

  bool _turnWaiting(String id) =>
      permissions.values.any((p) => p.sessionID == id) ||
      questions.values.any((q) => q.sessionID == id) ||
      forms.values.any((f) => f.sessionID == id || f.sessionID == 'global') ||
      waitingCardsForSession(id).isNotEmpty;

  /// A read in flight keeps the last verdict, so the periodic re-read does
  /// not flip it (and restart the silence clock) every time it starts.
  void _noteTurnStallAsksReadable() {
    if (!permissionsLoading && !questionsLoading) {
      _turnStallAsksUnreadable =
          permissionsError != null || questionsError != null;
    }
  }

  /// [_syncTurnStalls] for a notify. With no turn open or tracked, no tool
  /// seen and no timer, it could change nothing a later sync would not
  /// redo, so the many notifies of an idle connection skip it.
  void _syncTurnStallsOnNotify() {
    _noteTurnStallAsksReadable();
    if (_turnStallSessions.isEmpty &&
        _turnStallTimer == null &&
        _turnStallTools.isEmpty &&
        busySessions.isEmpty &&
        _openTurns.isEmpty) {
      return;
    }
    _syncTurnStalls();
  }

  void _syncTurnStalls() {
    _turnStallSyncs += 1;
    // Snapshot-only controllers do not own a live turn or a wakeup clock.
    // Arm deadlines only alongside a transport owned by this controller.
    if (_disposed ||
        isIsolated ||
        _lifecycleSuspended ||
        _lifecycleWasBackgrounded ||
        (!_turnStallTestClock && _events == null && _poll?.isActive != true)) {
      _resetTurnStalls();
      return;
    }
    final now = DateTime.now();
    final active = {
      ...busySessions.where(_isPersonsSession),
      for (final entry in _openTurns.entries)
        if (_isPersonsSession(entry.key) &&
            now.difference(entry.value) < _openTurnGrace)
          entry.key,
    };
    for (final id in _turnStallSessions.difference(active)) {
      _turnStalls.finish(id);
      _turnStallTools.remove(id);
    }
    _turnStallSessions
      ..clear()
      ..addAll(active);
    if (_turnStallTransportConnected != null &&
        _turnStallTransportConnected != isConnected) {
      for (final id in active) {
        _turnStalls.invalidateEvidence(id);
      }
    }
    _turnStallTransportConnected = isConnected;
    _noteTurnStallAsksReadable();
    for (final id in active) {
      _turnStalls.begin(id);
      _turnStalls.setWaiting(id, _turnWaiting(id));
      // A tool waiting on an ask the stream missed looks like a tool that
      // runs. While the waiting requests cannot be read, that ask can never
      // show, so the tool no longer holds the watch off: the turn reaches
      // the normal stall notice instead of hanging until the server's sweep.
      _turnStalls.setToolRunning(
        id,
        !_turnStallAsksUnreadable && _turnStallTools[id]?.isNotEmpty == true,
      );
    }
    if (active.isEmpty) {
      _turnStallTimer?.cancel();
      _turnStallTimer = null;
      _cancelTurnStallProbe();
    } else {
      _turnStallTimer ??= Timer.periodic(
        turnStallTick,
        (_) => unawaited(_checkTurnStalls()),
      );
    }
  }

  void _turnStallOnEvent(EventEnvelope event) {
    final props = event.properties;
    final nested = props['part'] ?? props['info'];
    final raw =
        props['sessionID'] ?? (nested is Map ? nested['sessionID'] : null);
    if (raw is! String || raw.isEmpty) return;
    if (event.type == 'message.part.updated' && nested is Map) {
      final status = nested['state'] is Map ? nested['state']['status'] : null;
      final partId = (nested['id'] ?? nested['callID'])?.toString() ?? '';
      if (nested['type'] == 'tool' && partId.isNotEmpty && status is String) {
        final tools = _turnStallTools.putIfAbsent(raw, () => <String>{});
        if (status == 'pending' || status == 'running') {
          tools.add(partId);
        } else {
          tools.remove(partId);
          if (tools.isEmpty) _turnStallTools.remove(raw);
        }
      }
    }
    _syncTurnStalls();
    // Status polls, heartbeats and activity in other sessions cannot extend
    // the silence clock. Only actual message/tool output counts as progress.
    if (event.type == 'message.updated' ||
        event.type == 'message.part.updated' ||
        event.type == 'message.part.delta' ||
        event.type == 'tool.progress') {
      final hadDiagnosis = _turnStalls.diagnosisFor(raw) != null;
      _turnStalls.noteProgress(raw);
      if (hadDiagnosis) _notifyListeners();
    }
  }

  Future<TurnStallEvidence> _readTurnStallEvidence() async {
    final override = _turnStallProbe;
    if (override != null) return override();
    final gateway = api;
    bool? reachable;
    if (gateway != null) {
      try {
        reachable = (await gateway.health()).healthy;
      } catch (_) {
        reachable = false;
      }
    }
    return TurnStallEvidence(
      transportConnected: isConnected,
      endpointReachable: reachable,
    );
  }

  /// Every [turnStallRereadEvery] while a turn is open on a live stream, the
  /// waiting permissions and questions are read again. A live ask can be
  /// lost (a network handoff, a stream that reconnected without replaying
  /// it); its tool then looks like one that runs and holds the stall watch
  /// off, so without this read the chat would say "working" until the
  /// server's idle sweep.
  void _turnStallRereadRequests() {
    if (!isConnected || _turnStallSessions.isEmpty) {
      _turnStallRereadTicks = 0;
      return;
    }
    _turnStallRereadTicks += 1;
    if (_turnStallRereadTicks * turnStallTick.inMilliseconds <
        turnStallRereadEvery.inMilliseconds) {
      return;
    }
    _turnStallRereadTicks = 0;
    unawaited(refreshPendingPermissions());
    unawaited(refreshPendingQuestions());
  }

  Future<void> _checkTurnStalls() async {
    if (_disposed || _lifecycleSuspended || _lifecycleWasBackgrounded) {
      return;
    }
    _syncTurnStalls();
    _turnStallRereadRequests();
    if (_turnStallProbeCancellation != null) return;
    final probes = [
      for (final id in _turnStalls.dueSessionIds) ?_turnStalls.takeProbe(id),
    ];
    if (probes.isEmpty) return;
    final cancellation = Completer<void>();
    _turnStallProbeCancellation = cancellation;
    final generation = _generation;
    final tracker = _turnStalls;
    try {
      final evidence = await boundedTurnStallProbe(
        probe: _readTurnStallEvidence,
        transportConnected: isConnected,
        cancelled: cancellation.future,
      );
      if (_disposed ||
          _lifecycleSuspended ||
          _lifecycleWasBackgrounded ||
          generation != _generation ||
          !identical(tracker, _turnStalls) ||
          !identical(cancellation, _turnStallProbeCancellation)) {
        return;
      }
      var changed = false;
      for (final probe in probes) {
        if (_turnStalls.completeProbe(probe, evidence) != null) changed = true;
      }
      if (changed) _notifyListeners();
    } finally {
      if (identical(cancellation, _turnStallProbeCancellation)) {
        _turnStallProbeCancellation = null;
      }
    }
  }
}
