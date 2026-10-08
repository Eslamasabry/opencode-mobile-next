part of 'live_background.dart';

/// A temporary claim on the notification service, independent of the saved
/// background preference. Android acknowledges activation; no Dart timer keeps
/// this claim alive or restarts a service that Android or the user stopped.
class _SignInForegroundState {
  _SignInForegroundState(this.controller);

  final BackgroundLiveController controller;
  final Set<_SignInForegroundLease> _leases = {};
  Future<void>? _activation;
  Future<Map<String, dynamic>>? _nativeEnable;
  Future<void>? _stopping;
  int _generation = 0;
  int _stopRevision = 0;
  bool _owned = false;

  bool get hasClaims => _leases.any((lease) => lease._valid || lease._held);
  bool get preservesUserClaim => _owned || hasClaims;
  bool get _wanted => _leases.any((lease) => lease._valid);
  bool get _holdsService => _leases.any((lease) => lease._valid || lease._held);

  AgentSignInForegroundLease reserve() {
    final lease = _SignInForegroundLease(this);
    _leases.add(lease); // Admission is reserved before any asynchronous work.
    if (controller._foregroundDisposed) {
      lease._invalidate(AgentSignInForegroundFailure.cancelled);
    } else if (!platformCapabilities.supportsBackgroundService) {
      lease._invalidate(AgentSignInForegroundFailure.unsupported);
    } else if (controller.active &&
        _leases.any((other) => other._valid && other._admitted)) {
      lease._admit();
    } else {
      _ensureActivation();
    }
    return lease;
  }

  void _ensureActivation() {
    if (_activation != null || !_wanted || controller._foregroundDisposed) {
      return;
    }
    final generation = _generation;
    final activation = _activate(generation);
    _activation = activation;
    unawaited(
      activation.then((_) {
        if (_activation != activation) return;
        _activation = null;
        // Only a new, explicit reservation after cancellation can start again.
        if (_leases.any((lease) => lease._valid && !lease._admitted)) {
          _ensureActivation();
        }
      }),
    );
  }

  bool _current(int generation) {
    if (generation != _generation ||
        !_wanted ||
        controller._foregroundDisposed) {
      return false;
    }
    if (!platformCapabilities.supportsBackgroundService) {
      invalidate(AgentSignInForegroundFailure.unsupported);
      return false;
    }
    return true;
  }

  Future<void> _activate(int generation) async {
    var startedHere = false;
    try {
      await _stopping;
      if (!_current(generation)) return;
      final pause = BackgroundPauseState.fromPlatform(
        await controller._invoke('getBackgroundPause'),
      );
      if (!_current(generation)) return;
      if (!pause.supported || pause.paused) {
        controller._applyPauseState(pause);
        return;
      }
      if (pause.active) {
        // Keep ownership if an earlier admitted lease still owns this service;
        // an independently running service is borrowed and never stopped here.
        controller._backgroundPause = pause;
        controller._setActive(true);
      } else {
        startedHere = !controller.enabled;
        if (startedHere) _owned = true;
        final status = await invokeEnable();
        if (!_current(generation)) {
          if (startedHere && status['active'] == true) {
            _owned = true;
            await _stopIfUnclaimed();
          }
          return;
        }
        final receipt = BackgroundPauseState.fromPlatform(
          status['backgroundPause'],
        );
        if (status['active'] != true || !receipt.supported || !receipt.active) {
          invalidate(AgentSignInForegroundFailure.unavailable);
          if (status['active'] == true && startedHere) {
            await _stopIfUnclaimed();
          } else {
            _owned = false;
          }
          return;
        }
        controller._backgroundPause = receipt;
        controller._setActive(true);
        controller.notificationGranted = status['notificationGranted'] == true;
        controller.batteryOptimizationIgnored =
            status['batteryOptimizationIgnored'] == true;
        controller.stoppedByAndroidTimeout = false;
      }
      for (final lease in _leases.toList()) {
        if (lease._valid) lease._admit();
      }
      controller.notifyListeners();
    } on AgentSignInForegroundException {
      // A canceled or rejected startup may finish after its caller released.
      // Observe cleanup failure here, preserving ownership for a later release.
      if (_current(generation)) {
        invalidate(AgentSignInForegroundFailure.unavailable);
      }
    } on PlatformException catch (error) {
      if (_current(generation)) {
        invalidate(
          error.code == 'notification_denied'
              ? AgentSignInForegroundFailure.permission
              : AgentSignInForegroundFailure.unavailable,
        );
      }
      if (startedHere) _owned = false;
    } catch (_) {
      if (_current(generation)) {
        invalidate(AgentSignInForegroundFailure.unavailable);
      }
      if (startedHere) _owned = false;
    }
  }

  /// Explicit user enable and temporary admission share one native request.
  Future<Map<String, dynamic>> invokeEnable() =>
      _nativeEnable ??= _enableNative();

  Future<Map<String, dynamic>> _enableNative() async {
    final stopRevision = _stopRevision;
    try {
      final status = await controller._invoke('enable');
      if (stopRevision != _stopRevision &&
          status['active'] == true &&
          !controller.enabled &&
          platformCapabilities.supportsBackgroundService) {
        // An explicit stop may overtake Android's start acknowledgment. The
        // canceled request must not resurrect the service after that stop.
        _owned = true;
        try {
          final stopped = await controller._invoke('disable');
          if (stopped['active'] == false) {
            _owned = false;
            controller._setActive(false);
          }
        } catch (_) {
          // Retain ownership for a later cleanup attempt.
        }
        return {...status, 'active': false};
      }
      return status;
    } finally {
      _nativeEnable = null;
    }
  }

  Future<bool> enableUserClaim() async {
    final previous = controller.enabled;
    final revision = controller._pauseRevision;
    controller.enabled = true;
    controller.notifyListeners();
    if (!controller.active) {
      _ensureActivation();
      await _activation;
    }
    if (controller._foregroundDisposed ||
        revision != controller._pauseRevision) {
      return controller.enabled && !controller._foregroundDisposed;
    }
    if (!controller.active) {
      controller.enabled = previous;
      controller.notifyListeners();
      return previous;
    }
    await controller.preferences.setBool(
      BackgroundLiveController.preferenceKey,
      true,
    );
    if (revision != controller._pauseRevision && !controller.enabled) {
      await controller.preferences.setBool(
        BackgroundLiveController.preferenceKey,
        false,
      );
    }
    return controller.enabled;
  }

  void invalidate(AgentSignInForegroundFailure reason) {
    ++_generation;
    for (final lease in _leases.toList()) {
      lease._invalidate(reason);
    }
  }

  void serviceStopped(AgentSignInForegroundFailure reason) {
    ++_stopRevision;
    _owned = false;
    invalidate(reason);
  }

  void cancelForUser() {
    ++_stopRevision;
    invalidate(AgentSignInForegroundFailure.cancelled);
  }

  Future<bool> disableUserClaim() async {
    final revision = ++controller._pauseRevision;
    controller.enabled = false;
    controller.notifyListeners();
    try {
      await controller.preferences.setBool(
        BackgroundLiveController.preferenceKey,
        false,
      );
      final status = await controller._invoke('disable');
      if (revision == controller._pauseRevision &&
          !controller._foregroundDisposed) {
        controller._applyStatus(status);
        controller._applyPauseState(
          const BackgroundPauseState(supported: true),
        );
      }
    } catch (_) {
      if (revision == controller._pauseRevision) {
        controller.lastError = 'Android could not change background mode.';
      }
    }
    controller.notifyListeners();
    return false;
  }

  Future<void> release(_SignInForegroundLease lease) async {
    lease._invalidate(AgentSignInForegroundFailure.cancelled);
    lease._held = false;
    _leases.remove(lease);
    await _stopIfUnclaimed();
  }

  Future<void> _stopIfUnclaimed() async {
    if (!_owned ||
        controller.enabled ||
        _holdsService ||
        !platformCapabilities.supportsBackgroundService) {
      return;
    }
    if (_stopping != null) return _stopping;
    final stopping = _stopOwned();
    _stopping = stopping;
    try {
      await stopping;
    } finally {
      if (_stopping == stopping) _stopping = null;
    }
  }

  Future<void> _stopOwned() async {
    // A pending enable is cleaned when its actual receipt arrives, never by
    // blocking cancellation on that receipt. Retain ownership after failure so
    // another release can retry without surfacing native exception text.
    if (_nativeEnable != null) return;
    try {
      final status = await controller._invoke('disable');
      if (status['active'] != false) {
        throw const AgentSignInForegroundException(
          AgentSignInForegroundFailure.unavailable,
        );
      }
      _owned = false;
      controller._setActive(false);
      controller._backgroundPause = const BackgroundPauseState(supported: true);
      controller.notifyListeners();
    } catch (_) {
      // The caller retains its cleanup binding and can retry; never claim that
      // Android stopped the owned service without an inactive receipt.
      throw const AgentSignInForegroundException(
        AgentSignInForegroundFailure.unavailable,
      );
    }
  }
}

class _SignInForegroundLease implements AgentSignInForegroundLease {
  _SignInForegroundLease(this.owner) {
    // Cancellation may precede the consumer attaching its readiness handler.
    // Preserve the error for consumers while draining the unobserved branch.
    unawaited(
      _ready.future.then<void>((_) {}, onError: (Object _, StackTrace _) {}),
    );
  }

  final _SignInForegroundState owner;
  final Completer<void> _ready = Completer<void>();
  final StreamController<void> _lost = StreamController<void>.broadcast(
    sync: true,
  );
  bool _valid = true;
  bool _admitted = false;
  bool _held = false;

  @override
  Future<void> get ready => _ready.future;
  @override
  bool get active =>
      _valid && _admitted && !owner.controller._foregroundDisposed;
  @override
  Stream<void> get lost => _lost.stream;

  void _admit() {
    if (!_valid || _admitted) return;
    _admitted = true;
    _held = true;
    _ready.complete();
  }

  void _invalidate(AgentSignInForegroundFailure reason) {
    if (!_valid) return;
    _valid = false;
    if (!_ready.isCompleted) {
      _ready.completeError(AgentSignInForegroundException(reason));
    }
    _lost.add(null);
  }

  @override
  Future<void> release() async {
    await owner.release(this);
    if (!_lost.isClosed) await _lost.close();
  }
}
