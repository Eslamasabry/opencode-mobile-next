import 'dart:async';
import 'dart:convert';
import 'dart:ui' show Locale, PlatformDispatcher;

import 'package:flutter/foundation.dart';

import '../../diagnostics/perf_trace.dart';
import '../../l10n/app_localizations.dart';
import '../../termux/bridge.dart' show TermuxRuntime;
import '../../ui/kit/kit_redact.dart';
import '../builtin_linux.dart';
import 'components.dart';
import 'setup_contract.dart';
import 'setup_scripts.dart';
import 'termux_setup_host.dart';

export 'setup_contract.dart' show SetupHostKind;

part 'setup_engine/start.dart';
part 'setup_engine/poll.dart';
part 'setup_engine/progress_helpers.dart';
part 'setup_engine/job_record.dart';

/// What the engine asks of the app once OpenCode is installed: start the
/// server for [runtime] on [SetupFinishRequest.host] and connect to it.
@immutable
class SetupFinishRequest {
  const SetupFinishRequest({
    required this.runtime,
    required this.openCodeChanged,
    this.version,
    this.host = SetupHostKind.builtin,
  });

  final SetupHostKind host;
  final TermuxRuntime runtime;

  /// OpenCode was installed or updated by this job, so a server already
  /// running is the old one and must be restarted.
  final bool openCodeChanged;
  final String? version;
}

/// Starts and connects; returns null when OpenCode answers, else the reason
/// in plain words. The app shell provides it (setup_finish.dart).
typedef SetupFinisher = Future<String?> Function(SetupFinishRequest request);

/// The phone setup engine (docs/design/phone-setup-v2-2026-09-24.md, "The
/// engine and its resume rule").
///
/// [run] checks every component first and skips what is installed, so each
/// run is also a resume; the rest goes to the native job runner
/// (SetupRunner.kt), which keeps going while the app is in the background and
/// writes setup.json. This class polls that and turns it into
/// [SetupProgress].
///
/// Starting OpenCode is the job's last step (`start`, a
/// [SetupComponent.jobStep]): the native job waits on it while [finisher]
/// starts the server and connects, and only then is the job done. A job
/// interrupted there continues like any other: every check passes and only
/// the start is left.
class ChannelSetupEngine implements SetupEngine {
  ChannelSetupEngine({
    BuiltinLinux? linux,
    this.finisher,
    AppLocalizations Function()? strings,
    List<SetupComponent> Function(
      AppLocalizations l10n,
      Map<String, Map<String, String>> params,
    )?
    components,
    this.pollInterval = const Duration(milliseconds: 500),
    this.readTimeout = const Duration(seconds: 10),
    DateTime Function()? clock,
  }) : _linux = linux ?? BuiltinLinux(),
       strings = strings ?? deviceStrings,
       _components =
           components ??
           ((l10n, params) => setupComponents(l10n, params: params)),
       _clock = clock ?? DateTime.now {
    _progress = _WatchedProgress(_watchersChanged);
  }

  /// Uses the same component engine inside Termux's proot-distro Ubuntu.
  /// [finisher] must start and authenticate the Termux server, returning null
  /// only after connection succeeds. No built-in server fallback is used.
  factory ChannelSetupEngine.termux({
    required SetupFinisher finisher,
    AppLocalizations Function()? strings,
    List<SetupComponent> Function(
      AppLocalizations l10n,
      Map<String, Map<String, String>> params,
    )?
    components,
    Duration pollInterval = const Duration(milliseconds: 500),
    Duration readTimeout = const Duration(seconds: 10),
    DateTime Function()? clock,
  }) => ChannelSetupEngine(
    linux: TermuxSetupHost(),
    finisher: finisher,
    strings: strings,
    components:
        components ??
        ((l10n, params) =>
            setupComponents(l10n, params: params, host: SetupHostKind.termux)),
    pollInterval: pollInterval,
    readTimeout: readTimeout,
    clock: clock,
  );

  /// Fixed for this instance, including restoration and the finish request.
  SetupHostKind get host =>
      _linux is TermuxSetupHost ? SetupHostKind.termux : SetupHostKind.builtin;

  final BuiltinLinux _linux;

  /// The words the engine writes (titles, stages, errors, the notification).
  AppLocalizations Function() strings;
  final List<SetupComponent> Function(
    AppLocalizations l10n,
    Map<String, Map<String, String>> params,
  )
  _components;
  final DateTime Function() _clock;
  final Duration pollInterval;

  /// How long one read of the job may take before the poll gives up on it
  /// and tries again; a reply lost on the way back must not stop polling.
  final Duration readTimeout;

  /// Set by the app shell once it can start and connect (main.dart).
  SetupFinisher? finisher;

  /// Follows the device language, English for one the app does not ship.
  /// The app shell replaces it with its own language choice.
  static AppLocalizations deviceStrings() {
    final locale = PlatformDispatcher.instance.locale;
    final supported = AppLocalizations.supportedLocales.any(
      (candidate) => candidate.languageCode == locale.languageCode,
    );
    return lookupAppLocalizations(
      supported ? Locale(locale.languageCode) : const Locale('en'),
    );
  }

  late final _WatchedProgress _progress;

  /// The job the engine last saw, as setup.json has it.
  SetupJobRecord? _record;

  /// The components of the job being shown, in its order.
  List<SetupComponent> _jobComponents = const [];

  /// Per job: the highest fraction each component reached, so a new stage
  /// that starts measuring from zero never moves the bar backwards.
  final _floors = <String, double>{};
  String? _floorsJob;

  bool _starting = false;

  /// The job [_start] is checking or handing over. Until the runner has it,
  /// a record of any other job is old news: showing it would replace this
  /// job's rows (the Add tools crash) and its progress.
  String? _startingJobId;
  bool _cancelRequested = false;

  /// Whether the job being started (before setup.json has it) is a first
  /// setup, for the progress published while its checks run.
  bool _localFirstSetup = false;

  /// What the job being started adds ([SetupProgress.adding]), likewise.
  List<String> _localAdding = const [];

  /// The last job given to the native runner by this engine.
  String? _handedOver;
  bool _handoverUncertain = false;
  String? _handoverError;
  String? _finishing;

  /// App-side components ([SetupComponent.app]) this engine has started,
  /// as `job/component`, so a poll never starts one twice.
  final _appStarted = <String>{};

  /// The app-side install running now, if any.
  ({String jobId, String id, SetupAppComponent app})? _appRun;

  /// Its latest progress, laid over the job's record (the native runner
  /// only waits for an app step and knows nothing of its bytes).
  SetupAppProgress? _appLive;
  DateTime? _appLiveShown;
  Timer? _poll;
  bool _polling = false;
  bool _disposed = false;

  @override
  List<SetupComponent> get registry => _components(strings(), const {});

  @override
  ValueListenable<SetupProgress> get progress => _progress;

  @override
  Future<void> run(
    Set<String> ids, {
    Map<String, Map<String, String>> params = const {},
  }) async {
    if (_starting) return;
    _starting = true;
    _cancelRequested = false;
    try {
      if (_handoverUncertain) {
        await _refresh();
        if (_handoverUncertain || _progress.value.state == SetupState.running) {
          _ensurePolling();
          return;
        }
      }
      final last = await _read();
      if (last != null && last.state == 'running') {
        // Already running (the app came back to it): watch, do not restart.
        _show(last);
        _ensurePolling();
        return;
      }
      final effectiveParams = effectiveJobParams(params, last);
      if (host == SetupHostKind.termux) {
        TermuxSetupHost.validateParams(effectiveParams);
      }
      try {
        await _start(ids, effectiveParams);
      } catch (error, stack) {
        // Never thrown at a caller that may not show it (the AI Team page
        // swallowed it, the tools sheet said "OpenCode is unreachable"): the
        // job fails, and the progress screen says so with the text under
        // Details.
        debugPrint('setup: start failed ${error.runtimeType}: $error\n$stack');
        _failStart(error);
      }
    } finally {
      _starting = false;
      _startingJobId = null;
    }
  }

  @override
  Future<void> cancel() async {
    _cancelRequested = true;
    // Still checking: nothing native runs yet, and _start stops on the flag.
    if (_starting && _handedOver != _progress.value.jobId) return;
    final current = _progress.value;
    if (current.state != SetupState.running) return;
    _appRun?.app.cancel();
    try {
      await _linux.cancelSetup();
    } on BuiltinLinuxException {
      // Nothing native is running (still checking): the flag stops it.
    }
    await _refresh();
  }

  @override
  Future<void> restore() async {
    final record = await _read();
    if (record == null) return;
    _show(record);
    if (record.state == 'running') _ensurePolling();
  }

  /// Continues the saved selection and parameters after a cold restart.
  /// A running job is observed, never duplicated; completed jobs are left alone.
  Future<void> resume() async {
    final record = await _read();
    if (record == null) return;
    _show(record);
    if (record.state == 'running') {
      _ensurePolling();
    } else if (record.canContinue) {
      await run({
        for (final component in record.components) component.id,
      }, params: record.params);
    }
  }

  @override
  Future<Set<String>> installedOptional() async {
    final optional = [
      for (final component in registry)
        if (!component.required && !component.jobStep) component,
    ];
    if (optional.isEmpty) return {};
    final apps = await _checkApps(optional);
    final installed = {
      for (final entry in apps.entries)
        if (entry.value.ok) entry.key,
    };
    final inLinux = [
      for (final component in optional)
        if (component.app == null) component,
    ];
    if (inLinux.isEmpty) return installed;
    try {
      final checks = await _check(inLinux);
      return {
        ...installed,
        for (final component in inLinux)
          if (checks[component.id]?.ok == true) component.id,
      };
    } on BuiltinLinuxException {
      return installed;
    }
  }

  // ---- polling -------------------------------------------------------------

  void _watchersChanged() {
    if (_progress.watched) _ensurePolling();
  }

  /// What the trace already holds, as `job/component`, so each finished
  /// step is recorded once however often it is polled.
  final _traced = <String>{};

  /// Stops polling for good (tests; the app keeps its one engine).
  void dispose() {
    _disposed = true;
    _poll?.cancel();
    _polling = false;
  }
}
