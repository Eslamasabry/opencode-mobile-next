import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/widgets.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'bridge.dart';

enum ManagedRecoveryError {
  settingsUnreadable,
  enableFailed,
  ownershipChanged,
  uncertainResult,
}

/// One foreground recovery owner per managed Termux installation. The durable
/// budget is never reset by reconnects, successful restarts or app recreation.
class ManagedServerRecovery extends ChangeNotifier with WidgetsBindingObserver {
  ManagedServerRecovery._(this.prefs, this.profileID, this._now) {
    _restore();
    WidgetsBinding.instance.addObserver(this);
    _foreground =
        WidgetsBinding.instance.lifecycleState == null ||
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    _schedule();
  }

  static const maxAttempts = 3;
  static const backoff = [
    Duration(seconds: 5),
    Duration(seconds: 15),
    Duration(seconds: 45),
  ];
  static final _instances =
      Map<SharedPreferences, ManagedServerRecovery>.identity();
  static String preferenceKey(String profileID) =>
      'oc.managedServerRecovery.$profileID';

  static ManagedServerRecovery forProfile(
    SharedPreferences prefs,
    String profileID, {
    DateTime Function()? now,
  }) => _instances.putIfAbsent(
    prefs,
    () => ManagedServerRecovery._(prefs, profileID, now ?? DateTime.now),
  );

  static void syncProfiles(
    SharedPreferences prefs,
    Iterable<String> managedProfileIDs,
  ) {
    final ids = managedProfileIDs.toSet();
    final current = _instances[prefs];
    if (current != null && ids.contains(current.profileID)) return;
    disposeForPreferences(prefs);
    if (ids.isEmpty || !TermuxBridge.supported) return;
    // Duplicate profiles share a single installation and a single retry budget.
    final owner = ids.where((id) {
      try {
        return (jsonDecode(prefs.getString(preferenceKey(id)) ?? '{}')
                as Map)['enabled'] ==
            true;
      } catch (_) {
        return false;
      }
    }).firstOrNull;
    if (owner != null) forProfile(prefs, owner);
  }

  static void disposeForPreferences(SharedPreferences prefs) {
    _instances.remove(prefs)?.dispose();
  }

  /// Admission closes synchronously, including when profile deletion has more
  /// async work ahead. Revocation then cancels a dispatched owned recovery.
  static Future<void> disableForProfile(
    SharedPreferences prefs,
    String profileID,
  ) {
    final current = _instances[prefs];
    if (current != null && current.profileID == profileID) {
      return current.setEnabled(false);
    }
    final raw = prefs.getString(preferenceKey(profileID));
    if (raw == null) return Future.value();
    return _disableStored(prefs, profileID, raw);
  }

  static Future<void> _disableStored(
    SharedPreferences prefs,
    String id,
    String raw,
  ) async {
    final data = jsonDecode(raw) as Map<String, dynamic>;
    data['enabled'] = false;
    final token = data['token'];
    try {
      if (!await prefs.setString(preferenceKey(id), jsonEncode(data))) {
        throw StateError('Could not save recovery preference');
      }
    } finally {
      if (token is String && token.isNotEmpty) {
        await TermuxBridge.run(
          TermuxBridge.recoveryControlScript(token, enable: false),
        );
      }
    }
  }

  final SharedPreferences prefs;
  final String profileID;
  final DateTime Function() _now;
  bool enabled = false;
  int attempts = 0;
  DateTime? nextAttemptAt;
  ManagedRecoveryError? error;
  bool busy = false;
  TermuxSetupStatus? status;
  String _token = '';
  String _operation = '';
  String _pendingOperation = '';
  bool _foreground = false;
  bool _disposed = false;
  bool _paused = false;
  int _epoch = 0;
  Timer? _timer;

  bool get exhausted => attempts >= maxAttempts;
  bool get paused => _paused || !_foreground;

  void _restore() {
    try {
      final raw = prefs.getString(preferenceKey(profileID));
      if (raw == null) return;
      final data = jsonDecode(raw) as Map<String, dynamic>;
      final token = data['token'];
      final count = data['attempts'];
      final operation = data['operation'];
      final pending = data['pendingOperation'];
      if (token is! String ||
          !RegExp(r'^[a-zA-Z0-9_-]{1,64}$').hasMatch(token) ||
          count is! int ||
          count < 0 ||
          count > maxAttempts ||
          operation is! String ||
          pending is! String ||
          !RegExp(r'^[a-zA-Z0-9_-]{0,64}$').hasMatch(operation) ||
          !RegExp(r'^[a-zA-Z0-9_-]{0,64}$').hasMatch(pending)) {
        return;
      }
      _token = token;
      attempts = count;
      _operation = operation;
      _pendingOperation = pending;
      final next = data['nextAttemptAtMs'];
      if (next != null && (next is! int || next < 0)) return;
      nextAttemptAt = next is int
          ? DateTime.fromMillisecondsSinceEpoch(next)
          : null;
      _paused = data['paused'] == true;
      enabled = data['enabled'] == true;
    } catch (_) {
      error = ManagedRecoveryError.settingsUnreadable;
    }
  }

  Future<void> _save() async {
    if (!await prefs.setString(
      preferenceKey(profileID),
      jsonEncode({
        'enabled': enabled,
        'token': _token,
        'attempts': attempts,
        'operation': _operation,
        'pendingOperation': _pendingOperation,
        'nextAttemptAtMs': nextAttemptAt?.millisecondsSinceEpoch,
        'paused': _paused,
      }),
    )) {
      throw StateError('Could not save recovery preference');
    }
  }

  Future<void> setEnabled(bool value) async {
    final epoch = ++_epoch;
    _timer?.cancel();
    if (!value) {
      enabled = false;
      _paused = false;
      _notify();
      try {
        await _save();
      } finally {
        if (_token.isNotEmpty) {
          await TermuxBridge.run(
            TermuxBridge.recoveryControlScript(_token, enable: false),
          );
        }
      }
      return;
    }
    if (busy || _disposed || !_foreground) return;
    busy = true;
    error = null;
    _notify();
    final token = List.generate(
      16,
      (_) => Random.secure().nextInt(256).toRadixString(16).padLeft(2, '0'),
    ).join();
    try {
      final result = await TermuxBridge.run(
        TermuxBridge.recoveryControlScript(token, enable: true),
      );
      if (_disposed || epoch != _epoch) {
        await TermuxBridge.run(
          TermuxBridge.recoveryControlScript(token, enable: false),
        );
        return;
      }
      final snapshot = TermuxSetupStatus.parse(result.stdout);
      if (!snapshot.isReady ||
          snapshot.runner != 'proot' ||
          snapshot.port != TermuxBridge.managedServerPort) {
        await TermuxBridge.run(
          TermuxBridge.recoveryControlScript(token, enable: false),
        );
        throw StateError('Managed server is not ready');
      }
      _token = token;
      _operation = snapshot.operationID;
      _pendingOperation = '';
      attempts = 0;
      nextAttemptAt = null;
      enabled = true;
      _paused = false;
      status = snapshot;
      await _save();
    } catch (_) {
      enabled = false;
      error = ManagedRecoveryError.enableFailed;
      // A failed preference write must never leave an armed policy invisible.
      try {
        await TermuxBridge.run(
          TermuxBridge.recoveryControlScript(token, enable: false),
        );
      } catch (_) {}
    } finally {
      busy = false;
      _notify();
      _schedule();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    ++_epoch;
    _timer?.cancel();
    _notify();
    _schedule();
  }

  void _schedule() {
    _timer?.cancel();
    if (_disposed ||
        !enabled ||
        !_foreground ||
        _paused ||
        busy ||
        (exhausted && _pendingOperation.isEmpty)) {
      return;
    }
    _timer = Timer(const Duration(seconds: 5), checkNow);
  }

  /// A manual check resumes a transient probe error without resetting retries.
  Future<void> retryCheck() async {
    _paused = false;
    error = null;
    await _save();
    await checkNow();
  }

  Future<void> checkNow() async {
    if (_disposed || !enabled || !_foreground || _paused || busy) return;
    busy = true;
    final epoch = _epoch;
    bool current() => !_disposed && enabled && _foreground && epoch == _epoch;
    try {
      final snapshot = await TermuxBridge.status();
      if (!current()) return;
      status = snapshot;
      if (_pendingOperation.isNotEmpty &&
          snapshot.operationID == _pendingOperation) {
        _operation = _pendingOperation;
        _pendingOperation = '';
        await _save();
        if (!current()) return;
      }
      if (snapshot.operationID != _operation || snapshot.phase == 'stopped') {
        _paused = true;
        error = ManagedRecoveryError.ownershipChanged;
        await _save();
        return;
      }
      if (snapshot.isReady && nextAttemptAt != null) {
        nextAttemptAt = null;
        await _save();
        return;
      }
      if (!snapshot.canRecover || exhausted) return;
      final now = _now();
      if (nextAttemptAt == null) {
        nextAttemptAt = now.add(backoff[attempts]);
        await _save();
        return;
      }
      if (now.isBefore(nextAttemptAt!)) return;
      // Reserve and durably consume the attempt before dispatch. App death
      // cannot grant a fresh budget for an operation with an unknown outcome.
      attempts++;
      _pendingOperation = '${_token.substring(0, 16)}-$attempts';
      nextAttemptAt = attempts < maxAttempts
          ? now.add(backoff[attempts])
          : null;
      await _save();
      if (!current()) return;
      await TermuxBridge.run(
        TermuxBridge.restartScript(
          operationID: _pendingOperation,
          recoveryToken: _token,
          expectedOperationID: _operation,
        ),
        timeout: const Duration(seconds: 90),
      );
    } catch (_) {
      if (current()) {
        // A definite startup failure can use the remaining bounded budget.
        // Unknown callback outcomes pause instead of overlapping operations.
        if (_pendingOperation.isNotEmpty) {
          try {
            final observed = await TermuxBridge.status();
            if (!current()) return;
            if (observed.operationID == _pendingOperation &&
                observed.canRecover) {
              _operation = _pendingOperation;
              _pendingOperation = '';
              status = observed;
              await _save();
              return;
            }
          } catch (_) {}
        }
        if (!current()) return;
        // Unknown callback outcomes require an explicit check. Never blindly
        // issue another restart while a previous command could still run.
        _paused = true;
        error = ManagedRecoveryError.uncertainResult;
        try {
          await _save();
        } catch (_) {}
      }
    } finally {
      busy = false;
      _notify();
      _schedule();
    }
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    ++_epoch;
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}
