part of '../connection.dart';

// Coalesced reads of the waiting requests (permissions, questions).

/// One kind of waiting-request read. Callers in the same stream epoch (one
/// transport generation, one event-stream connect) share the read already
/// running. A read that started in the current epoch, while the stream is
/// connected, makes another automatic read redundant: every ask and reply
/// since then arrives as an event. A new connect always reads again
/// (asks are never replayed after a reconnect, per the protocol notes).
class _PendingReadGate {
  Future<void>? _inFlight;
  Object? _inFlightEpoch;
  Object? _freshEpoch;

  Future<void> run(Object epoch, Future<void> Function() read) {
    final running = _inFlight;
    if (running != null && _inFlightEpoch == epoch) return running;
    late final Future<void> tracked;
    tracked = read()
        .then((_) {
          if (identical(_inFlight, tracked)) _freshEpoch = epoch;
        })
        .whenComplete(() {
          if (identical(_inFlight, tracked)) {
            _inFlight = null;
            _inFlightEpoch = null;
          }
        });
    _inFlight = tracked;
    _inFlightEpoch = epoch;
    return tracked;
  }

  /// A read that started in [epoch] finished, or one is running in it.
  bool covers(Object epoch) => _freshEpoch == epoch || _inFlightEpoch == epoch;
}

extension _ConnectionControllerPendingReads on ConnectionController {
  /// Identifies the transport and the event-stream connect a read started
  /// under.
  Object get _pendingReadEpoch => (_generation, _streamConnects);

  /// Re-reads the waiting permissions unless the connected stream already
  /// covers them. For automatic reads (wake, folder open, alert routing);
  /// a person's refresh calls [refreshPendingPermissions].
  Future<void> _syncPendingPermissions() {
    final epoch = _pendingReadEpoch;
    if (status == StreamStatus.connected &&
        permissionsError == null &&
        _permissionReads.covers(epoch)) {
      return _permissionReads._inFlight ?? Future<void>.value();
    }
    return refreshPendingPermissions();
  }

  /// The questions counterpart of [_syncPendingPermissions].
  Future<void> _syncPendingQuestions() {
    final epoch = _pendingReadEpoch;
    if (status == StreamStatus.connected &&
        questionsError == null &&
        _questionReads.covers(epoch)) {
      return _questionReads._inFlight ?? Future<void>.value();
    }
    return refreshPendingQuestions();
  }

  /// Sessions first, then the waiting requests. By the time the sessions
  /// are in, the event stream started alongside has usually connected and
  /// re-read the requests itself (it does on every connect), so they are
  /// read once rather than twice per wake. Without a stream they are read
  /// here all the same.
  Future<void> _refreshLocationReads() async {
    final sessions = refreshSessions();
    try {
      await sessions;
    } catch (_) {
      // Reported by the sessions read; the requests still load.
    }
    await Future.wait<void>([
      _syncPendingPermissions(),
      _syncPendingQuestions(),
      sessions,
    ]);
  }
}
