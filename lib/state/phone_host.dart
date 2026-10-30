import 'dart:async';

import 'package:flutter/foundation.dart';

import '../builtin/builtin_linux.dart';
import '../builtin/builtin_server.dart';
import '../builtin/deliberate_stop.dart';
import '../builtin/setup/setup_contract.dart';
import '../builtin/setup/termux_setup_host.dart';
import '../domain/opencode_history_copy.dart';
import '../l10n/app_localizations.dart';
import '../termux/bridge.dart';
import '../termux/termux_reach.dart';
import 'connection.dart';
import 'local_server_controls.dart';
import 'profiles.dart';
import 'termux_host_setup.dart';

/// Where OpenCode runs on this phone: inside the app (the bundled Linux) or
/// in Termux. This phone is one page for both; this says which one it shows.
enum PhoneHostKind { inApp, termux }

/// What This phone's status line says, in the order a person cares about.
enum PhoneHostState {
  checking,
  notSetUp,

  /// A setup, update, switch or tool install is running.
  settingUp,

  /// Something needs the person: a start or a switch failed, or a switch
  /// stopped half way ([PhoneHost.failure] says what).
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

  /// The page could not ask the host how the server is (Termux did not
  /// answer, or the in-app Linux could not be read).
  check,
}

/// Whether the manager's failure [message] (with its [failureKind]) is about
/// the server starting or running rather than an install or update: the
/// manager writes these while it starts, waits for or watches the server.
@visibleForTesting
PhoneHostProblem termuxProblemOf(String failureKind, String message) {
  if (failureKind == 'crash' || failureKind == 'recovery') {
    return PhoneHostProblem.start;
  }
  const startMessages = [
    'OpenCode server exited',
    'OpenCode server did not become',
    'The local OpenCode server stopped unexpectedly',
    'Managed server did not start',
    'The local server port is still in use',
    'The local server password is missing',
    'Could not record the managed server process identity',
    'Automatic recovery was disabled',
  ];
  for (final start in startMessages) {
    if (message.startsWith(start)) return PhoneHostProblem.start;
  }
  return PhoneHostProblem.install;
}

/// OpenCode on this phone, whichever host runs it: the one model This phone
/// reads, so the page is the same for the in-app Linux and for Termux.
///
/// It reports and does only what both hosts share (status, start, stop, the
/// log, the saved connection). Installing, updating, switching and adding
/// tools are jobs each host runs through phone setup, so the page hands
/// those over by [kind].
abstract class PhoneHost extends ChangeNotifier {
  PhoneHostKind get kind;
  PhoneHostState get state;

  /// OpenCode 1 or 2.
  TermuxRuntime get runtime;

  /// The version the host reported, when it did.
  String? get version;

  /// Storage the host measured, when it did (the in-app Linux measures its
  /// own folder; Termux has its own Storage page).
  int? get bytesUsed;

  /// What went wrong last, as the host said it: technical text, shown
  /// only under Details.
  String? get failure;

  /// What [failure] was about; null when nothing failed.
  PhoneHostProblem? get problem => null;

  /// Why the app cannot reach Termux now (Termux only): one typed cause
  /// with one fix. While it is set, acts that run in Termux cannot work.
  TermuxProblem? get termuxProblem => null;

  /// Whether [runtime] was read or saved, rather than assumed: a page that
  /// could not ask the host does not call it "OpenCode 1".
  bool get runtimeKnown => true;

  /// The saved connection for [runtime], or null when there is none yet.
  ServerProfile? get profile;

  /// A switch between OpenCode 1 and 2 stopped half way (Termux only).
  TermuxRuntime? get switchTarget => null;
  TermuxRuntime? get switchPrevious => null;

  /// Whether Termux's OpenCode 2 can go back to 1 (an OpenCode 2 installed
  /// before switching existed cannot).
  bool get canSwitchRuntime => true;

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
  PhoneHostKind get kind => PhoneHostKind.inApp;

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
  TermuxRuntime get runtime {
    final profile = this.profile;
    if (profile != null) return BuiltinLinux.runtimeForProfile(profile);
    // A pre-selection installation is legacy; only a fresh host adopts OC2.
    return _status?.installed == true
        ? TermuxRuntime.openCode1
        : TermuxRuntime.defaultForNewInstall;
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

/// OpenCode in Termux, run by the Termux manager.
class TermuxPhoneHost extends PhoneHost {
  TermuxPhoneHost({
    required this.connection,
    required this.copy,
    this.readyTimeout = const Duration(seconds: 120),
  });

  final ConnectionController connection;

  /// The person's language, for the name a restored connection is saved
  /// under.
  final AppLocalizations Function() copy;
  final Duration readyTimeout;

  TermuxSetupStatus? _status;
  TermuxInstallation? _installation;
  String _log = '';
  bool _starting = false;
  bool _stopping = false;
  bool _checked = false;
  String? _failure;
  PhoneHostProblem? _problem;
  TermuxProblem? _reach;
  bool _disposed = false;

  ProfileStore get _store => connection.store;

  @override
  PhoneHostKind get kind => PhoneHostKind.termux;

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }

  @override
  TermuxRuntime get runtime {
    final status = _status;
    if (status != null &&
        (status.runtimeSelected || status.version.isNotEmpty)) {
      return status.runtime;
    }
    final installation = _installation;
    if (installation != null &&
        (installation.runtimeSelected || installation.ubuntuInstalled)) {
      return installation.runtime;
    }
    final saved = profile;
    return saved == null
        ? TermuxRuntime.defaultForNewInstall
        : ManagedRuntimeFlavor.runtimeOf(saved);
  }

  @override
  ServerProfile? get profile {
    ServerProfile? any;
    for (final profile in _store.profiles) {
      if (profile.backend != ServerBackend.openCode ||
          !TermuxBridge.managesServerUrl(profile.baseUrl)) {
        continue;
      }
      if (_status != null || _installation != null) {
        if (ManagedRuntimeFlavor.runtimeOf(profile) == _knownRuntime) {
          return profile;
        }
      }
      any ??= profile;
    }
    return any;
  }

  TermuxRuntime? get _knownRuntime {
    final status = _status;
    if (status != null &&
        (status.runtimeSelected || status.version.isNotEmpty)) {
      return status.runtime;
    }
    return _installation?.runtime;
  }

  @override
  String? get version {
    final version = _status?.version.trim();
    if (version != null && version.isNotEmpty) return version;
    return _installation?.openCodeVersion;
  }

  @override
  int? get bytesUsed => null;

  @override
  String? get failure => _failure;

  @override
  PhoneHostProblem? get problem => _failure == null ? null : _problem;

  @override
  TermuxProblem? get termuxProblem => _reach;

  @override
  bool get runtimeKnown => _knownRuntime != null || profile != null;

  @override
  TermuxRuntime? get switchTarget =>
      _status?.switchPending == true ? _status!.switchTarget : null;

  @override
  TermuxRuntime? get switchPrevious =>
      _status?.switchPending == true ? _status!.switchPrevious : null;

  @override
  bool get canSwitchRuntime =>
      runtime == TermuxRuntime.openCode1 ||
      _status?.switchReturnAvailable == true;

  @override
  PhoneHostState get state {
    if (_starting) return PhoneHostState.starting;
    if (_stopping) return PhoneHostState.stopping;
    if (!_checked) return PhoneHostState.checking;
    final status = _status;
    if (status != null && status.switchPending) {
      return PhoneHostState.needsYou;
    }
    if (status != null && status.isRunning) {
      return status.phase == 'restarting' || status.phase == 'starting_server'
          ? PhoneHostState.starting
          : PhoneHostState.settingUp;
    }
    if (status != null && status.isReady) return PhoneHostState.running;
    // Termux could not be asked or did not answer: not knowing is not
    // "not set up".
    if (status == null &&
        (_reach != null || problem == PhoneHostProblem.check)) {
      return PhoneHostState.needsYou;
    }
    if (version == null) return PhoneHostState.notSetUp;
    if (status != null && status.isFailed) return PhoneHostState.needsYou;
    return _failure != null ? PhoneHostState.needsYou : PhoneHostState.stopped;
  }

  @override
  Future<OpenCodeHistorySwitchAdmission> checkHistorySwitch() =>
      _checkHistorySwitch(TermuxSetupHost());

  @override
  Future<void> refresh() async {
    if (!TermuxBridge.supported) {
      _checked = true;
      notifyListeners();
      return;
    }
    // What Android says first: without Termux, or without access to it, no
    // command can run, and asking would only fail slowly.
    try {
      final blocked = termuxProblemOfCapabilities(
        await TermuxBridge.capabilities(),
      );
      if (_disposed) return;
      if (blocked != null) {
        _reach = blocked;
        _status = null;
        _checked = true;
        notifyListeners();
        return;
      }
    } catch (_) {
      // Unknown: the status read below says what it can.
    }
    try {
      final snapshot = await TermuxBridge.setupSnapshot();
      if (_disposed) return;
      final hadReach = _reach != null;
      _reach = null;
      if (hadReach) _failure = null;
      _status = snapshot.status;
      _log = snapshot.output;
      if (snapshot.status.isFailed && !snapshot.status.switchPending) {
        _failure = snapshot.status.message;
        _problem = termuxProblemOf(
          snapshot.status.failureKind,
          snapshot.status.message,
        );
      } else if (_problem == PhoneHostProblem.check) {
        // Termux answers again: its not answering is over.
        _failure = null;
        _problem = null;
      }
    } on TermuxBridgeException catch (error) {
      if (_disposed) return;
      final reach = termuxProblemOfError(error);
      if (reach == TermuxProblem.unknown) {
        _reach = null;
        _failure = error.message;
        _problem = PhoneHostProblem.check;
      } else {
        // A cause with its own fix: said as that, never as "try again".
        _reach = reach;
        _failure = error.message;
        _problem = null;
        _status = null;
      }
    }
    try {
      _installation = await TermuxBridge.inspectInstallation();
    } on TermuxBridgeException {
      // The status alone still says running or stopped.
    }
    if (_disposed) return;
    _checked = true;
    notifyListeners();
    final installed = _installation?.openCodeVersion != null;
    if (installed) await _restoreProfile();
  }

  Future<void> _restoreProfile() async {
    try {
      final restored = await restoreManagedTermuxProfile(
        _store,
        runtime,
        name: copy().e7SetupThisDevice,
      );
      if (restored && !_disposed) notifyListeners();
    } on TermuxBridgeException {
      // No password to take back: Connect then asks for one.
    }
  }

  LocalServerControls get _controls =>
      LocalServerControls(store: _store, connection: connection);

  @override
  Future<void> start(AppLocalizations l10n) async {
    _starting = true;
    _failure = null;
    notifyListeners();
    try {
      await _controls.restart(timeout: readyTimeout);
    } on LocalServerControlFailure catch (error) {
      _failure = error.message.isEmpty
          ? l10n.e7SetupRestartFailed(l10n.phoneServerCardStopped)
          : error.message;
      _problem = PhoneHostProblem.start;
    }
    _starting = false;
    if (_disposed) return;
    await refresh();
  }

  @override
  Future<void> stop(AppLocalizations l10n) async {
    _stopping = true;
    _failure = null;
    notifyListeners();
    try {
      await _controls.stop();
    } on LocalServerControlFailure catch (error) {
      _failure = l10n.e7SetupStopFailed(error.message);
      _problem = PhoneHostProblem.stop;
    }
    _stopping = false;
    if (_disposed) return;
    await refresh();
  }

  @override
  Future<String> readLog() async {
    try {
      final snapshot = await TermuxBridge.setupSnapshot();
      _log = snapshot.output;
    } on TermuxBridgeException catch (error) {
      return error.message;
    }
    return _log;
  }
}

Future<OpenCodeHistorySwitchAdmission> _checkHistorySwitch(
  BuiltinLinux linux,
) async {
  try {
    // Capture only the read-only guard's output on either host. Subshell keeps
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
