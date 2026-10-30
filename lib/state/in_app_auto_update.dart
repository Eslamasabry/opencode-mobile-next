import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../builtin/builtin_linux.dart';
import '../builtin/builtin_server.dart';
import '../builtin/setup/phone_setup.dart';
import '../builtin/setup/setup_contract.dart';
import 'connection.dart';
import 'phone_host.dart';

/// Whether the in-app server should be updated to the pinned OpenCode now.
///
/// The server on this phone is only worth restarting when it is behind, and
/// never under running work, over another job, or on a half-finished switch
/// or install. One automatic try per app run and pinned version: a failure
/// is left to the manual "Update OpenCode" row.
@visibleForTesting
bool shouldAutoUpdateInApp({
  required String? installedVersion,
  required String pinnedVersion,
  required bool busy,
  required bool engineRunning,
  required bool switchPending,
  required bool installProblem,
  required bool attemptedThisRun,
}) {
  final installed = installedVersion?.trim().replaceFirst(RegExp('^v'), '');
  if (installed == null || installed.isEmpty) return false;
  if (installed == pinnedVersion) return false;
  if (attemptedThisRun || busy || engineRunning) return false;
  if (switchPending || installProblem) return false;
  return true;
}

/// Keeps the in-app server on the version this app pins, by itself.
///
/// It runs the same job as This phone's "Update OpenCode" row, when nothing
/// is working, and looks again whenever work ends or the host's state
/// changes (no polling of its own).
class InAppAutoUpdater {
  InAppAutoUpdater({
    required this.host,
    required this.engine,
    required this.workChanges,
    required this.workBusy,
  }) {
    host.addListener(_changed);
    workChanges.addListener(_changed);
    unawaited(_start());
  }

  final PhoneHost host;
  final SetupEngine engine;

  /// Fires when sessions start or finish.
  final Listenable workChanges;
  final bool Function() workBusy;

  final _attempted = <String>{};
  bool _disposed = false;
  bool _checking = false;
  bool _started = false;

  Future<void> _start() async {
    await host.refresh();
    _started = true;
    _changed();
  }

  void _changed() {
    if (_disposed || !_started || _checking) return;
    unawaited(_maybeUpdate());
  }

  Future<void> _maybeUpdate() async {
    final runtime = host.runtime;
    final key = '${runtime.wireName}:${runtime.pinnedVersion}';
    if (!_decide(key)) return;
    _checking = true;
    try {
      // The host's status may be older than the work that just ended.
      await host.refresh();
      if (_disposed || !_decide(key)) return;
      _attempted.add(key);
      await engine.run(
        {'opencode'},
        params: {
          'opencode': {'runtime': runtime.wireName},
        },
      );
    } catch (_) {
      // Left to the manual row; the engine reports its own failure.
    } finally {
      _checking = false;
    }
  }

  bool _decide(String key) => shouldAutoUpdateInApp(
    installedVersion: host.installed ? host.version : null,
    pinnedVersion: host.runtime.pinnedVersion,
    busy: workBusy(),
    engineRunning: engine.progress.value.state == SetupState.running,
    switchPending: host.switchTarget != null,
    installProblem:
        host.problem == PhoneHostProblem.install ||
        engine.progress.value.state == SetupState.failed,
    attemptedThisRun: _attempted.contains(key),
  );

  void dispose() {
    _disposed = true;
    host.removeListener(_changed);
    workChanges.removeListener(_changed);
  }
}

/// How long the connection to this phone's server must have been up before
/// its empty busy list is trusted.
const inAppAutoUpdateSettle = Duration(seconds: 30);

/// App-lifetime owner of [InAppAutoUpdater]; null where there is no in-app
/// Linux or the engine is not wired.
final inAppAutoUpdateProvider = Provider<InAppAutoUpdater?>((ref) {
  if (!BuiltinLinux.supported) return null;
  final connection = ref.read(connProvider);
  final host = InAppPhoneHost(
    linux: ref.read(builtinLinuxProvider),
    starter: ref.read(builtinServerStarterProvider),
    connection: connection,
    engine: PhoneSetup.engine,
    pollInterval: null,
    // The updater reads the engine when it decides. Following it made the
    // engine poll setup status every minute for the whole app session.
    followEngine: false,
  );
  late final InAppAutoUpdater updater;
  DateTime? connectedSince;
  Timer? settle;
  updater = InAppAutoUpdater(
    host: host,
    engine: PhoneSetup.engine,
    workChanges: connection,
    workBusy: () {
      // Only a live connection to this phone's server can say it is idle:
      // busySessions covers the connected server alone, and stays empty
      // until the first status read (a restarted server may be resuming),
      // so the connection must also have settled for a while.
      final id = host.profile?.id;
      if (id == null ||
          connection.profile?.id != id ||
          !connection.isConnected) {
        connectedSince = null;
        return true;
      }
      final since = connectedSince ??= DateTime.now();
      final waited = DateTime.now().difference(since);
      if (waited < inAppAutoUpdateSettle) {
        settle?.cancel();
        settle = Timer(inAppAutoUpdateSettle - waited, updater._changed);
        return true;
      }
      if (connection.busySessions.isNotEmpty) return true;
      return connection.localPhoneAgentWorkBusy(id) == true;
    },
  );
  ref.onDispose(() {
    settle?.cancel();
    updater.dispose();
    host.dispose();
  });
  return updater;
});
