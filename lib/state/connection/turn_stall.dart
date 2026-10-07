part of '../connection.dart';

extension _ConnectionTurnStalls on ConnectionController {
  void _resetTurnStalls() {
    _turnStallTimer?.cancel();
    _turnStallTimer = null;
    _turnStallSessions.clear();
    _turnStalls.clear();
    _turnStallTransportConnected = null;
  }

  bool _turnWaiting(String id) =>
      permissions.values.any((p) => p.sessionID == id) ||
      questions.values.any((q) => q.sessionID == id) ||
      forms.values.any((f) => f.sessionID == id || f.sessionID == 'global') ||
      waitingCardsForSession(id).isNotEmpty;

  void _syncTurnStalls() {
    if (_disposed || isIsolated || _lifecycleSuspended) return;
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
    for (final id in active) {
      _turnStalls.begin(id);
      _turnStalls.setWaiting(id, _turnWaiting(id));
    }
    if (active.isEmpty) {
      _turnStallTimer?.cancel();
      _turnStallTimer = null;
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

  Future<void> _checkTurnStalls() async {
    if (_disposed || _turnStallProbing || _lifecycleSuspended) return;
    _syncTurnStalls();
    final probes = [
      for (final id in _turnStalls.dueSessionIds) ?_turnStalls.takeProbe(id),
    ];
    if (probes.isEmpty) return;
    _turnStallProbing = true;
    try {
      final evidence = await boundedTurnStallProbe(
        probe: _readTurnStallEvidence,
        transportConnected: isConnected,
      );
      if (_disposed || _lifecycleSuspended) return;
      var changed = false;
      for (final probe in probes) {
        if (_turnStalls.completeProbe(probe, evidence) != null) changed = true;
      }
      if (changed) _notifyListeners();
    } finally {
      _turnStallProbing = false;
    }
  }
}
