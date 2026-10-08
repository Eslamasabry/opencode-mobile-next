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

extension _PaseoLifecycle on PaseoGateway {
  void _scheduleReconnect() {
    if (!_listening || _closed || _retry != null || _recovering != null) return;
    final delay = Duration(seconds: 1 << _retryAttempt.clamp(0, 4));
    _retryAttempt++;
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
      // Turn boundaries may have been missed while away; the snapshots read
      // next are the authority on what is still running.
      _awaitingTurn.clear();
      _turnActive.clear();
      // Listing re-establishes the agent subscription on the new socket and
      // refreshes statuses and pending permissions missed while away.
      if (_directory != null) {
        if (_trackLocalWork) {
          await sessions();
        } else {
          await sessionPage(limit: 200);
        }
      }
      if (_closed || !_listening) return;
      _recoveryDegraded = false;
      _retryAttempt = 0;
      _emitState(StreamStatus.connected);
    } on PaseoFailure catch (error) {
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
    }
  }
}
