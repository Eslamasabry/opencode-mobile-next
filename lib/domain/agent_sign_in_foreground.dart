import 'dart:async';

enum AgentSignInForegroundFailure {
  unsupported,
  paused,
  permission,
  unavailable,
  cancelled,
}

final class AgentSignInForegroundException implements Exception {
  const AgentSignInForegroundException(this.reason);
  final AgentSignInForegroundFailure reason;
  @override
  String toString() => 'AgentSignInForegroundException(${reason.name})';
}

/// Reservation precedes asynchronous service admission and process launch.
abstract interface class AgentSignInForegroundPort {
  AgentSignInForegroundLease reserveAgentSignInForeground();
}

abstract interface class AgentSignInForegroundLease {
  Future<void> get ready;
  bool get active;
  Stream<void> get lost;
  Future<void> release();
}

/// The canonical phone-account owner supplies the app's shared service port.
/// Shell cleanup is registered before admission, including pending launches.
abstract final class AgentSignInForegroundRegistry {
  static final _bindings = <String, AgentSignInForegroundBinding>{};
  static final _draining = <String, AgentSignInForegroundBinding>{};

  static AgentSignInForegroundBinding? bindingFor(String profileId) =>
      _bindings[profileId];

  static AgentSignInForegroundBinding bind(
    String profileId,
    AgentSignInForegroundPort port,
  ) {
    final old = _bindings[profileId] ?? _draining[profileId];
    if (old != null && old.current && identical(old._port, port)) return old;
    final gate = old?.close() ?? Future<void>.value();
    // Observe failure even when no terminal is opened. Admission still awaits
    // the original gate and refuses a failed old-owner drain.
    unawaited(gate.then<void>((_) {}, onError: (Object _) {}));
    final binding = AgentSignInForegroundBinding._(profileId, port, gate);
    _bindings[profileId] = binding;
    return binding;
  }

  static Future<void> unbind(AgentSignInForegroundBinding binding) async {
    if (identical(_bindings[binding.profileId], binding)) {
      _bindings.remove(binding.profileId);
    }
    _draining[binding.profileId] = binding;
    try {
      await binding.close();
      if (identical(_draining[binding.profileId], binding)) {
        _draining.remove(binding.profileId);
      }
    } catch (_) {
      throw const AgentSignInForegroundException(
        AgentSignInForegroundFailure.unavailable,
      );
    }
  }
}

final class AgentSignInForegroundBinding {
  AgentSignInForegroundBinding._(this.profileId, this._port, this._gate);
  final String profileId;
  final AgentSignInForegroundPort _port;
  final Future<void> _gate;
  final _cleanups = <Object, Future<void> Function()>{};
  final _leases = <_BoundForegroundLease>{};
  bool _valid = true;
  Future<void>? _closing;

  bool get current =>
      _valid &&
      identical(AgentSignInForegroundRegistry.bindingFor(profileId), this);

  Object addCleanup(Future<void> Function() cleanup) {
    if (!current) {
      throw const AgentSignInForegroundException(
        AgentSignInForegroundFailure.cancelled,
      );
    }
    final token = Object();
    _cleanups[token] = cleanup;
    return token;
  }

  void removeCleanup(Object token) => _cleanups.remove(token);

  AgentSignInForegroundLease reserve() {
    if (!current) {
      throw const AgentSignInForegroundException(
        AgentSignInForegroundFailure.cancelled,
      );
    }
    final lease = _BoundForegroundLease(this);
    _leases.add(lease);
    unawaited(lease._start());
    return lease;
  }

  Future<void> close() =>
      _closing ??= _close().whenComplete(() => _closing = null);

  Future<void> _close() async {
    _valid = false;
    for (final lease in _leases.toList()) {
      lease._invalidate();
    }
    // Keep process protection until the terminal has removed its native PTY.
    // Failed drains retain their cleanup so profile deletion can safely retry.
    for (final entry in _cleanups.entries.toList()) {
      await entry.value();
      _cleanups.remove(entry.key);
    }
    // A replacement that never launched still inherits the older drain.
    // Closing it must not let another replacement skip that pending work.
    await _gate;
    for (final lease in _leases.toList()) {
      await lease.release();
    }
  }
}

final class _BoundForegroundLease implements AgentSignInForegroundLease {
  _BoundForegroundLease(this._binding) {
    unawaited(_ready.future.then<void>((_) {}, onError: (Object _) {}));
  }
  final AgentSignInForegroundBinding _binding;
  final _ready = Completer<void>();
  final _lost = StreamController<void>.broadcast(sync: true);
  AgentSignInForegroundLease? _delegate;
  StreamSubscription<void>? _subscription;
  bool _invalid = false;
  Future<void>? _releasing;

  @override
  Future<void> get ready => _ready.future;
  @override
  bool get active =>
      !_invalid && _binding.current && (_delegate?.active ?? false);
  @override
  Stream<void> get lost => _lost.stream;

  Future<void> _start() async {
    try {
      await _binding._gate;
      if (_invalid || !_binding.current) return _invalidate();
      final delegate = _binding._port.reserveAgentSignInForeground();
      _delegate = delegate;
      _subscription = delegate.lost.listen((_) => _invalidate());
      await delegate.ready;
      if (_invalid || !_binding.current || !delegate.active) {
        _invalidate();
        await delegate.release();
        return;
      }
      if (!_ready.isCompleted) _ready.complete();
    } catch (error) {
      if (!_ready.isCompleted) {
        _ready.completeError(
          error is AgentSignInForegroundException
              ? error
              : const AgentSignInForegroundException(
                  AgentSignInForegroundFailure.unavailable,
                ),
        );
      }
      _invalid = true;
      try {
        await _delegate?.release();
      } catch (_) {
        // Keep the binding's lease for a later owner-drain retry. Readiness
        // already reports a closed failure; async cleanup emits no raw error.
      }
    }
  }

  void _invalidate() {
    if (_invalid) return;
    _invalid = true;
    if (!_ready.isCompleted) {
      _ready.completeError(
        const AgentSignInForegroundException(
          AgentSignInForegroundFailure.cancelled,
        ),
      );
    }
    if (!_lost.isClosed) _lost.add(null);
  }

  @override
  Future<void> release() => _releasing ??= _release().catchError((Object _) {
    _releasing = null;
    throw const AgentSignInForegroundException(
      AgentSignInForegroundFailure.unavailable,
    );
  });

  Future<void> _release() async {
    _invalidate();
    await _subscription?.cancel();
    await _delegate?.release();
    _binding._leases.remove(this);
    await _lost.close();
  }
}
