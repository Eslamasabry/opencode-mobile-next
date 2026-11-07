import 'dart:async';

import 'package:flutter/foundation.dart';

import '../builtin/builtin_linux.dart';
import '../builtin/builtin_server.dart';
import '../builtin/deliberate_stop.dart';
import '../builtin/setup/setup_contract.dart';
import '../domain/opencode_history_copy.dart';
import '../l10n/app_localizations.dart';
import 'connection.dart';
import 'profiles.dart';

/// What This phone's status line says, in the order a person cares about.
enum PhoneHostState {
  checking,
  notSetUp,

  /// A setup, update or tool install is running.
  settingUp,

  /// Something needs the person: a start failed ([PhoneHost.failure] says
  /// what).
  needsYou,
  starting,
  stopping,
  stopped,
  running,
}

/// What [PhoneHost.failure] was about, so the page can say it in plain
/// words and offer the act that fixes it: a failed install is installed
/// again, a failed start is started again.
enum PhoneHostProblem {
  /// The server did not start, or stopped by itself (a crash).
  start,

  /// The app cannot safely identify its old processes; reopen it through Android.
  componentRecovery,

  /// The server did not stop.
  stop,

  /// An install or an update did not finish.
  install,

  /// The page could not ask the host how the server is (the in-app Linux
  /// could not be read).
  check,
}

/// OpenCode on this phone (the built-in Linux): the one model This phone
/// reads.
///
/// It reports and does status, start, stop, the log and the saved
/// connection. Installing, updating and adding tools are jobs phone setup
/// runs, so the page hands those over.
abstract class PhoneHost extends ChangeNotifier {
  PhoneHostState get state;

  /// OpenCode 1 or 2.
  OpenCodeRuntime get runtime;

  /// The version the host reported, when it did.
  String? get version;

  /// Storage the host measured, when it did.
  int? get bytesUsed;

  /// What went wrong last, as the host said it: technical text, shown
  /// only under Details.
  String? get failure;

  /// What [failure] was about; null when nothing failed.
  PhoneHostProblem? get problem => null;

  /// Whether [runtime] was read or saved, rather than assumed: a page that
  /// could not ask the host does not call it "OpenCode 1".
  bool get runtimeKnown => true;

  /// The saved connection for [runtime], or null when there is none yet.
  ServerProfile? get profile;

  bool get installed =>
      state != PhoneHostState.checking && state != PhoneHostState.notSetUp;

  Future<void> refresh();

  /// Read-only admission; callers recheck on tap before promising a move.
  /// Hosts without the check leave the offer unavailable.
  Future<OpenCodeHistorySwitchAdmission> checkHistorySwitch() async =>
      OpenCodeHistorySwitchAdmission.unavailable;

  /// Starts the server and waits until it answers.
  Future<void> start(AppLocalizations l10n);

  /// Stops the server. The app lets go of the connection it can no longer
  /// serve.
  Future<void> stop(AppLocalizations l10n);

  /// The server's log, newest last.
  Future<String> readLog();
}

/// OpenCode inside the app ([BuiltinLinux]).
class InAppPhoneHost extends PhoneHost {
  InAppPhoneHost({
    required this.linux,
    required this.starter,
    required this.connection,
    this.engine,
    this.pollInterval = const Duration(seconds: 5),
    this.followEngine = true,
  }) {
    starter.addListener(_starterChanged);
    if (followEngine) engine?.progress.addListener(notifyListeners);
  }

  /// Whether this host re-notifies on every [engine] change. A listener is a
  /// watching page to the engine, which then reads setup status while the
  /// app is open; an owner that only reads [state] when it decides passes
  /// false.
  final bool followEngine;

  final BuiltinLinux linux;
  final BuiltinServerStarter starter;
  final ConnectionController connection;

  /// Phone setup's engine, for "a job is running"; null where it is not
  /// wired (tests of the page alone).
  final SetupEngine? engine;
  final Duration? pollInterval;

  BuiltinLinuxStatus? _status;
  bool _stopping = false;
  String? _failure;
  PhoneHostProblem? _problem;
  Timer? _poll;
  bool _disposed = false;

  @override
  void dispose() {
    _disposed = true;
    _poll?.cancel();
    starter.removeListener(_starterChanged);
    if (followEngine) engine?.progress.removeListener(notifyListeners);
    super.dispose();
  }

  void _starterChanged() {
    if (_disposed) return;
    notifyListeners();
    if (!starter.starting) unawaited(refresh());
  }

  @override
  ServerProfile? get profile {
    ServerProfile? chosen;
    final store = connection.store;
    for (final profile in store.profiles) {
      if (!looksLikeInAppServer(profile)) continue;
      if (profile.id == store.activeId) return profile;
      chosen = profile;
    }
    return chosen;
  }

  @override
  OpenCodeRuntime get runtime {
    final profile = this.profile;
    if (profile != null) return BuiltinLinux.runtimeForProfile(profile);
    // A pre-selection installation is legacy; only a fresh host adopts OC2.
    return _status?.installed == true
        ? OpenCodeRuntime.openCode1
        : OpenCodeRuntime.defaultForNewInstall;
  }

  @override
  String? get version {
    final version = profile?.serverVersion?.trim();
    return version == null || version.isEmpty ? null : version;
  }

  @override
  int? get bytesUsed {
    final bytes = _status?.bytesUsed;
    return bytes != null && bytes > 0 ? bytes : null;
  }

  @override
  String? get failure =>
      _failure ?? starter.failureFor(profile)?.technicalDetails;

  @override
  PhoneHostProblem? get problem =>
      starter.failureFor(profile)?.problem ==
          BuiltinStartProblem.componentRecovery
      ? PhoneHostProblem.componentRecovery
      : _failure == null
      ? null
      : _problem;

  @override
  PhoneHostState get state {
    if (engine?.progress.value.state == SetupState.running) {
      return PhoneHostState.settingUp;
    }
    if (starter.starting) return PhoneHostState.starting;
    if (_stopping) return PhoneHostState.stopping;
    final status = _status;
    if (status == null) return PhoneHostState.checking;
    if (!status.installed || profile == null) return PhoneHostState.notSetUp;
    if (status.serverRunning) return PhoneHostState.running;
    return problem != null ? PhoneHostState.needsYou : PhoneHostState.stopped;
  }

  @override
  Future<OpenCodeHistorySwitchAdmission> checkHistorySwitch() =>
      _checkHistorySwitch(linux);

  @override
  Future<void> refresh() async {
    _poll?.cancel();
    try {
      final status = await linux.status();
      if (_disposed) return;
      _status = status;
      notifyListeners();
    } on BuiltinLinuxException catch (error) {
      if (_disposed) return;
      _status ??= const BuiltinLinuxStatus.absent();
      _failure = error.message;
      _problem = PhoneHostProblem.check;
      notifyListeners();
    }
    final interval = pollInterval;
    if (interval != null && !_disposed) {
      // Two refreshes can overlap (a start finishing while a poll is in
      // flight); the later one replaces the earlier one's timer.
      _poll?.cancel();
      _poll = Timer(interval, () {
        if (!_disposed) unawaited(refresh());
      });
    }
  }

  @override
  Future<void> start(AppLocalizations l10n) async {
    final profile = this.profile;
    if (profile == null) return;
    _failure = null;
    notifyListeners();
    final failed = await starter.start(profile);
    if (_disposed) return;
    if (failed != null) {
      _failure = l10n.builtinServerStartFailed(failed.reason(l10n));
      _problem = failed.problem == BuiltinStartProblem.componentRecovery
          ? PhoneHostProblem.componentRecovery
          : PhoneHostProblem.start;
    }
    await refresh();
  }

  @override
  Future<void> stop(AppLocalizations l10n) async {
    _stopping = true;
    _failure = null;
    notifyListeners();
    try {
      await linux.stopServer();
      final stopped = profile;
      if (stopped != null) {
        await DeliberateServerStop.mark(connection.store.prefs, stopped.id);
      }
    } on BuiltinLinuxException catch (error) {
      _failure = error.message;
      _problem = PhoneHostProblem.stop;
    }
    if (_disposed) return;
    await refresh();
    _stopping = false;
    if (!_disposed) notifyListeners();
  }

  @override
  Future<String> readLog() async {
    try {
      return await linux.serverLog();
    } on BuiltinLinuxException catch (error) {
      return error.message;
    }
  }
}

Future<OpenCodeHistorySwitchAdmission> _checkHistorySwitch(
  BuiltinLinux linux,
) async {
  try {
    // Capture only the read-only guard's output. The subshell keeps
    // its refusal exit from escaping before the transport captures stderr.
    final result = await linux.run(
      '(\n$openCode1HistorySwitchCheckScript\n) 2>&1',
      timeout: const Duration(seconds: 20),
    );
    return OpenCodeHistorySwitchAdmission.fromResult(
      result.exitCode,
      result.output,
    );
  } catch (_) {
    return OpenCodeHistorySwitchAdmission.unavailable;
  }
}
