import 'dart:async';

import 'agent_helper_status.dart';

/// A 5-second timer starts a bounded probe after 45 seconds of silence. Even
/// the latest tick completes its 10-second probe within 60 seconds.
const turnStallSilence = Duration(seconds: 45);
const turnStallTick = Duration(seconds: 5);
const turnStallProbeBudget = Duration(seconds: 10);

enum TurnStallKind { modelSlow, helperDown, network }

/// Safe observations only; errors, URLs, prompts and credentials stay outside
/// the classifier. Null means the probe could not establish that fact.
final class TurnStallEvidence {
  const TurnStallEvidence({
    required this.transportConnected,
    this.helperRunning,
    this.helperStatus,
    this.endpointReachable,
  });

  final bool transportConnected;
  final bool? helperRunning;
  final AgentHelperStatus? helperStatus;
  final bool? endpointReachable;
}

/// An unavailable helper is direct evidence. A healthy reachable endpoint
/// makes a slow model an inference, not a measured model failure. Every other
/// case is an interrupted or unconfirmed connection; it never proves that
/// the phone's internet is down.
TurnStallKind classifyTurnStall(TurnStallEvidence evidence) {
  if (evidence.helperRunning == false) return TurnStallKind.helperDown;
  if (evidence.transportConnected && evidence.endpointReachable == true) {
    return TurnStallKind.modelSlow;
  }
  return TurnStallKind.network;
}

/// Probes that throw or never finish yield unknown connection evidence within
/// the budget. The original future cannot mutate tracker state after timeout.
Future<TurnStallEvidence> boundedTurnStallProbe({
  required Future<TurnStallEvidence> Function() probe,
  required bool transportConnected,
  Duration timeout = turnStallProbeBudget,
  Future<void>? cancelled,
}) async {
  final result = Completer<TurnStallEvidence>();
  final unknown = TurnStallEvidence(transportConnected: transportConnected);
  void complete(TurnStallEvidence value) {
    if (!result.isCompleted) result.complete(value);
  }

  final deadline = Timer(timeout, () => complete(unknown));
  unawaited(
    Future<TurnStallEvidence>.sync(
      probe,
    ).then(complete, onError: (Object _, StackTrace _) => complete(unknown)),
  );
  if (cancelled != null) {
    unawaited(
      cancelled.then(
        (_) => complete(unknown),
        onError: (Object _, StackTrace _) => complete(unknown),
      ),
    );
  }
  try {
    return await result.future;
  } finally {
    deadline.cancel();
  }
}

final class TurnStallDiagnosis {
  const TurnStallDiagnosis({
    required this.kind,
    required this.evidence,
    required this.silentFor,
  });

  final TurnStallKind kind;
  final TurnStallEvidence evidence;
  final Duration silentFor;

  /// Contract copy for the frontend; localize these words in the UI lane.
  String get message => switch (kind) {
    TurnStallKind.modelSlow =>
      'The model may be taking longer. Wait or stop and try again.',
    TurnStallKind.helperDown =>
      evidence.helperStatus?.notice ??
          'The agent helper stopped. Restart it and try again.',
    TurnStallKind.network =>
      evidence.endpointReachable == false || !evidence.transportConnected
          ? 'The connection to the agent was lost. Reconnect and try again.'
          : 'The connection could not be checked. Reconnect and try again.',
  };
}

/// A probe is valid only for the active turn and the exact silence it checked.
/// Tokens are opaque so another session, a resumed turn, or a late probe cannot
/// publish stale findings.
final class TurnStallProbe {
  const TurnStallProbe._(this.sessionId, this._revision);
  final String sessionId;
  final int _revision;
}

/// Timer-free, protocol-neutral turn liveness. The connection owner feeds
/// target-session progress and schedules the timer/probes. Global heartbeats
/// and events from other conversations must not call [noteProgress].
final class TurnStallTracker {
  TurnStallTracker({
    Duration Function()? elapsed,
    this.silenceThreshold = turnStallSilence,
    this.maximumSessions = 100,
  }) : assert(silenceThreshold > Duration.zero),
       assert(maximumSessions > 0),
       _elapsed = elapsed ?? _monotonicClock();

  final Duration Function() _elapsed;
  final Duration silenceThreshold;
  final int maximumSessions;
  final _turns = <String, _SilentTurn>{};
  int _revision = 0;

  static Duration Function() _monotonicClock() {
    final clock = Stopwatch()..start();
    return () => clock.elapsed;
  }

  /// Start only at a new prompt/busy transition, never at every status poll.
  /// An already-active turn keeps its original silence clock.
  void begin(String sessionId) {
    if (sessionId.isEmpty || _turns.containsKey(sessionId)) return;
    if (_turns.length >= maximumSessions) _turns.remove(_turns.keys.first);
    _turns[sessionId] = _SilentTurn(_elapsed(), ++_revision);
  }

  void noteProgress(String sessionId) {
    final turn = _turns[sessionId];
    if (turn == null) return;
    turn.lastProgress = _elapsed();
    turn.revision = ++_revision;
    turn.probing = false;
    turn.diagnosis = null;
  }

  /// A permission, question, card or form waiting for a person is not a stall.
  /// Resuming starts a fresh silence period, with no old diagnosis carried over.
  void setWaiting(String sessionId, bool waiting) {
    final turn = _turns[sessionId];
    if (turn == null || turn.waiting == waiting) return;
    turn.waiting = waiting;
    noteProgress(sessionId);
  }

  void finish(String sessionId) => _turns.remove(sessionId);

  /// Recheck changed helper/transport facts without inventing turn progress.
  void invalidateEvidence(String sessionId) {
    final turn = _turns[sessionId];
    if (turn == null) return;
    turn.revision = ++_revision;
    turn.probing = false;
    turn.diagnosis = null;
  }

  void clear() => _turns.clear();

  TurnStallDiagnosis? diagnosisFor(String sessionId) =>
      _turns[sessionId]?.diagnosis;

  /// A fixed snapshot; safe to iterate while the owner starts probes.
  List<String> get dueSessionIds {
    final now = _elapsed();
    return [
      for (final entry in _turns.entries)
        if (_due(entry.value, now)) entry.key,
    ];
  }

  bool _due(_SilentTurn turn, Duration now) =>
      !turn.waiting &&
      !turn.probing &&
      turn.diagnosis == null &&
      now - turn.lastProgress >= silenceThreshold;

  TurnStallProbe? takeProbe(String sessionId) {
    final turn = _turns[sessionId];
    if (turn == null || !_due(turn, _elapsed())) return null;
    turn.probing = true;
    return TurnStallProbe._(sessionId, turn.revision);
  }

  /// Null means progress, a user wait, stop, reconnect, or a new turn overtook
  /// the probe. A classification never finishes, resends, or cancels a turn.
  TurnStallDiagnosis? completeProbe(
    TurnStallProbe probe,
    TurnStallEvidence evidence,
  ) {
    final turn = _turns[probe.sessionId];
    if (turn == null ||
        turn.revision != probe._revision ||
        !turn.probing ||
        turn.waiting) {
      return null;
    }
    turn.probing = false;
    return turn.diagnosis = TurnStallDiagnosis(
      kind: classifyTurnStall(evidence),
      evidence: evidence,
      silentFor: _elapsed() - turn.lastProgress,
    );
  }
}

final class _SilentTurn {
  _SilentTurn(this.lastProgress, this.revision);
  Duration lastProgress;
  int revision;
  bool waiting = false;
  bool probing = false;
  TurnStallDiagnosis? diagnosis;
}
