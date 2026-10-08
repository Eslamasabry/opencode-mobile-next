import 'builtin_linux.dart';

/// Foreground restoration of a native idle stop, independent of manual Start.
class PhoneServerIdle {
  PhoneServerIdle({
    required this.linux,
    required this.isForeground,
    required this.ownerId,
    required this.isReadable,
    required this.resumeAgents,
    this.changed,
  });

  final BuiltinLinux linux;
  final bool Function() isForeground;
  final String? Function() ownerId;
  final bool Function(String) isReadable;
  final Future<void> Function({
    required String profileId,
    required int expectedIdleGeneration,
  })
  resumeAgents;
  final void Function()? changed;
  Future<void>? _pending;
  Object? _pendingToken;
  String? _pendingOwner;
  int _epoch = 0;
  bool _disposed = false;
  bool _running = false;
  String? _failure;

  bool get running => _running;
  String? get failure => _failure;

  Future<void> check() {
    if (_disposed) return Future.value();
    final owner = ownerId();
    if (owner == null || !isForeground() || !isReadable(owner)) {
      return Future.value();
    }
    if (_pending != null && _pendingOwner == owner) return _pending!;
    final token = Object();
    final epoch = _epoch;
    _pendingToken = token;
    _pendingOwner = owner;
    _running = true;
    _failure = null;
    final operation = _restore(owner, epoch, token);
    _pending = operation;
    _notify();
    return operation;
  }

  bool _current(String owner, int epoch) {
    try {
      return !_disposed &&
          _epoch == epoch &&
          isForeground() &&
          ownerId() == owner &&
          isReadable(owner);
    } catch (_) {
      return false;
    }
  }

  Future<void> _restore(String owner, int epoch, Object token) async {
    try {
      if (!_current(owner, epoch)) return;
      var status = await linux.status();
      if (!_current(owner, epoch)) return;
      if (!status.serverIdlePolicySupported) return;
      _require(status.serverIdleReceiptValid);
      if (!status.serverRestartWanted ||
          !(status.serverIdleStopped || status.serverIdleHelperStopped)) {
        return;
      }
      final generation = status.serverIdleGeneration;
      _require(generation != null && generation > 0);
      final expected = generation!;
      final helperWasStopped = status.serverIdleHelperStopped;
      if (status.serverIdleStopped) {
        status = await linux.resumeIdleStoppedPhoneServer(
          profileId: owner,
          expectedIdleGeneration: expected,
        );
        if (!_current(owner, epoch)) return;
      }
      _requireLive(status, expected);
      _require(status.serverIdleHelperStopped == helperWasStopped);
      if (helperWasStopped) {
        await resumeAgents(profileId: owner, expectedIdleGeneration: expected);
        if (!_current(owner, epoch)) return;
      }
      // A Stop or new generation during helper readiness cannot be completed.
      status = await linux.status();
      if (!_current(owner, epoch)) return;
      _requireLive(status, expected);
      final completed = await linux.completePhoneServerIdleResume(
        profileId: owner,
        expectedIdleGeneration: expected,
      );
      if (!_current(owner, epoch)) return;
      _require(
        completed.serverIdlePolicySupported &&
            completed.serverIdleReceiptValid &&
            completed.serverRestartWanted &&
            completed.serverRunning &&
            !completed.serverIdleStopped &&
            !completed.serverIdleHelperStopped &&
            completed.serverIdleGeneration == (helperWasStopped ? 0 : expected),
      );
    } catch (_) {
      if (_current(owner, epoch)) _failure = 'idle_resume_unavailable';
    } finally {
      if (identical(_pendingToken, token)) {
        _pending = null;
        _pendingToken = null;
        _pendingOwner = null;
        _running = false;
        _notify();
      }
    }
  }

  void _requireLive(BuiltinLinuxStatus status, int generation) {
    _require(
      status.serverIdlePolicySupported &&
          status.serverIdleReceiptValid &&
          status.serverRestartWanted &&
          status.serverRunning &&
          !status.serverIdleStopped &&
          status.serverIdleGeneration == generation,
    );
  }

  void _require(bool admitted) {
    if (!admitted) throw const _IdleUnavailable();
  }

  void _notify() {
    if (_disposed) return;
    try {
      changed?.call();
    } catch (_) {
      /* Never expose callback errors. */
    }
  }

  void invalidate() {
    if (_disposed) return;
    _epoch++;
    _pending = null;
    _pendingToken = null;
    _pendingOwner = null;
    _running = false;
    _failure = null;
    _notify();
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _epoch++;
    _pending = null;
    _pendingToken = null;
    _pendingOwner = null;
    _running = false;
  }
}

class _IdleUnavailable {
  const _IdleUnavailable();
}
