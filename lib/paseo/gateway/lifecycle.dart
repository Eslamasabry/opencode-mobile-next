part of '../gateway.dart';

class _PaseoEventChannel implements LiveEventChannel {
  final Future<void> Function() disposeCallback;
  final void Function()? onStart;
  bool _disposed = false;
  bool _started = false;
  _PaseoEventChannel(this.disposeCallback, {this.onStart});
  @override
  void start() {
    if (_disposed || _started) return;
    _started = true;
    onStart?.call();
  }

  @override
  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    await disposeCallback();
  }
}

/// The conversation's channel: it retries on the gateway's schedule.
class _PaseoRetryingChannel extends _PaseoEventChannel
    implements RetryingEventChannel {
  _PaseoRetryingChannel(
    super.disposeCallback, {
    super.onStart,
    required this.nextAttempt,
  });

  @override
  final ValueListenable<DateTime?> nextAttempt;
}

extension _PaseoLifecycle on PaseoGateway {
  void _scheduleReconnect() {
    if (!_listening || _closed || _retry != null || _recovering != null) return;
    final delay = Duration(seconds: 1 << _retryAttempt.clamp(0, 4));
    _retryAttempt++;
    _nextAttempt.value = DateTime.now().add(delay);
    _retry = Timer(delay, () {
      _retry = null;
      unawaited(_recover());
    });
  }

  Future<void> _recover() => _recovering ??= _recoverNow().whenComplete(() {
    _recovering = null;
    if (!transport.connected || _recoveryDegraded) _scheduleReconnect();
  });

  Future<void> _recoverNow() async {
    try {
      await transport.connect();
    } on PaseoFailure catch (error) {
      lastRecoveryFailure = error;
      _recoveryDegraded = true;
      final terminal =
          error.kind == PaseoFailureKind.authentication ||
          error.kind == PaseoFailureKind.hostRefused;
      _emitState(
        terminal ? StreamStatus.disconnected : StreamStatus.reconnecting,
      );
      if (terminal) {
        if (_events.hasListener) _events.addError(error);
        _listening = false;
      }
      return;
    }
    // Turn boundaries may have been missed while away; the snapshots read
    // next are the authority on what is still running. A list read retried
    // on the same socket missed nothing: its turn events still count.
    final fresh = _reconciledEpoch != transport.epoch;
    if (fresh) {
      _reconciledEpoch = transport.epoch;
      _awaitingTurn.clear();
      _turnActive.clear();
    }
    // Listing re-establishes the agent subscription on the new socket and
    // refreshes statuses and pending permissions missed while away.
    Object? listFailure;
    var listed = false;
    final listing = _readRecoveryList().then<void>(
      (_) => listed = true,
      onError: (Object error) => listFailure = error,
    );
    // The daemon answered this socket's hello, so the link is up. A list
    // read it leaves unanswered (seen for the whole of a long tool call)
    // is reconciliation, not the link: after a short wait the link is
    // announced and the turn's events keep flowing while the list is read
    // (and, failing, read again on the retry schedule: [_recover]
    // reschedules while degraded). Waiting for the list held the chat on
    // "isn't answering" until the turn ended.
    var announced = !fresh;
    if (fresh) {
      final waited = Completer<void>();
      final grace = Timer(const Duration(seconds: 3), waited.complete);
      await Future.any([listing, waited.future]);
      grace.cancel();
      if (!listed && listFailure == null && !_closed && _listening) {
        announced = true;
        _emitState(StreamStatus.connected);
      }
    }
    await listing;
    if (_closed || !_listening) return;
    final failure = listFailure;
    if (failure != null) {
      lastRecoveryFailure = failure;
      _recoveryDegraded = true;
      if (!transport.connected) {
        _emitState(StreamStatus.reconnecting);
      } else if (!announced) {
        _emitState(StreamStatus.connected);
      }
      return;
    }
    lastRecoveryFailure = null;
    _recoveryDegraded = false;
    _retryAttempt = 0;
    if (!announced) _emitState(StreamStatus.connected);
  }

  Future<void> _readRecoveryList() async {
    if (_directory == null) return;
    if (_trackLocalWork) {
      await sessions();
    } else {
      await sessionPage(limit: 200);
    }
  }

  /// Closes the gateway for good: retries stop, every cache and pending
  /// request is dropped, and the transport closes once browser launches are
  /// revoked.
  void _closeNow() {
    if (_closed) return;
    final browserCleanup = _revokeBrowserSource(clearRequests: false);
    _browserRequested.clear();
    _browserRevisions.clear();
    _closed = true;
    _localWork.invalidate();
    unawaited(_localWork.changes.close());
    _listening = false;
    _locationEpoch++;
    _providerRevision++;
    _retry?.cancel();
    _retry = null;
    _nextAttempt.value = null;
    _agents.clear();
    _sessions.clear();
    _statuses.clear();
    _cancelRecordBusyExpiry();
    _drafts.clear();
    _draftProviders.clear();
    _draftModels.clear();
    _liveAgentSessions.clear();
    _uncertain.clear();
    _awaitingTurn.clear();
    _turnActive.clear();
    _permissions.clear();
    _questions.clear();
    _nativeQuestionChanges.add(null);
    _nativePermissionChanges.add(null);
    _live.clear();
    _realIDs.clear();
    _appIDs.clear();
    _heldEvents.clear();
    unawaited(_daemonEvents.cancel());
    unawaited(_daemonDisconnects.cancel());
    unawaited(browserCleanup.then((_) => transport.close()));
    unawaited(_events.close());
    unawaited(_streamStates.close());
    unawaited(_nativeQuestionChanges.close());
    unawaited(_nativePermissionChanges.close());
  }
}
