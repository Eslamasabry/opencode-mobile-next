/// The manager of the app's own Linux: reads what runs, listens, takes
/// space and is installed, and does the short acts (stop, clean) and the
/// long ones (install, remove, update) on it.
///
/// Short work goes through [BuiltinLinux.run], which takes the same
/// installer slot phone setup uses: setup and a cleanup cannot run at the
/// same time, and a busy slot is reported as [LinuxProblem.busy]. Long work
/// runs as the service `linux-job` ([LinuxJobController]): it survives
/// leaving the screen, its log is its progress, and it stops itself after
/// [linuxJobBudget].
library;

import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../builtin_linux.dart';
import '../builtin_server.dart' show builtinLinuxProvider;
import '../setup/setup_contract.dart' show setupToolsChanged;
import 'linux_packages.dart';
import 'linux_ports.dart';
import 'linux_processes.dart';
import 'linux_storage.dart';

/// Why the manager could not do something, in a fixed vocabulary the
/// screens turn into words. The phone's own text stays in
/// [LinuxManagerException.details].
enum LinuxProblem {
  /// Another phone task holds the installer slot.
  busy,

  /// Phone setup is running.
  setupRunning,

  /// A package job is running.
  jobRunning,

  /// Linux is not installed or not available on this device.
  unavailable,

  /// The act ran and did not finish.
  failed,
}

class LinuxManagerException implements Exception {
  const LinuxManagerException(this.problem, {this.details});

  final LinuxProblem problem;
  final String? details;

  @override
  String toString() => 'LinuxManagerException(${problem.name})';
}

/// The Android service name of the long job.
const linuxJobService = 'linux-job';

class LinuxManager {
  LinuxManager(
    this.linux, {
    this.retryDelay = const Duration(milliseconds: 700),
  });

  final BuiltinLinux linux;

  /// The pause before a read that met a passing failure is tried again.
  final Duration retryDelay;

  /// Reads that met another phone task, or a moment when the phone could
  /// not prove who owns every program in Linux (one that just ended or is
  /// starting), are tried twice more before the page says anything.
  static const _attempts = 3;

  static bool _passing(BuiltinLinuxException error) =>
      error.code == 'runtime_ownership_unknown' ||
      error.message.contains('Another phone task');

  Future<String> _run(
    String script, {
    Duration timeout = const Duration(seconds: 60),
  }) async {
    BuiltinLinuxRunResult? result;
    for (var attempt = 1; result == null; attempt++) {
      try {
        result = await linux.run(script, timeout: timeout);
      } on BuiltinLinuxException catch (error) {
        final passing = _passing(error);
        if (passing && attempt < _attempts) {
          await Future<void>.delayed(retryDelay);
          continue;
        }
        throw LinuxManagerException(
          passing ? LinuxProblem.busy : LinuxProblem.unavailable,
          details: [
            error.code,
            error.details ?? error.message,
          ].nonNulls.join('\n'),
        );
      }
    }
    if (result.exitCode != 0) {
      throw LinuxManagerException(
        LinuxProblem.failed,
        details: 'exit ${result.exitCode}\n${result.output}',
      );
    }
    return result.output;
  }

  /// Throws [LinuxProblem.setupRunning] while phone setup runs, and
  /// [LinuxProblem.jobRunning] while a package job runs.
  Future<void> requireQuiet() async {
    try {
      final raw = await linux.setupStatus();
      if (raw != null) {
        final record = jsonDecode(raw);
        if (record is Map && record['state'] == 'running') {
          throw const LinuxManagerException(LinuxProblem.setupRunning);
        }
      }
    } on LinuxManagerException {
      rethrow;
    } on BuiltinLinuxException catch (error) {
      throw LinuxManagerException(
        LinuxProblem.unavailable,
        details: error.details ?? error.message,
      );
    } on FormatException {
      // An unreadable journal says nothing about a running job.
    }
    if ((await linux.status()).serviceRunning(linuxJobService)) {
      throw const LinuxManagerException(LinuxProblem.jobRunning);
    }
  }

  // --- Running ---------------------------------------------------------

  Future<LinuxProcessScan> scanProcesses() async =>
      LinuxProcessScan.parse(await _run(linuxProcessScanScript));

  /// Stops [targets] and what they started, as [scan] shows it now.
  /// Protected processes are never sent.
  Future<LinuxStopResult> stop(
    Iterable<LinuxProcess> targets,
    LinuxProcessScan scan,
  ) async {
    final all = scan.withDescendants(targets.where((p) => !p.protected));
    if (all.isEmpty) return const LinuxStopResult({});
    return LinuxStopResult.parse(
      await _run(
        linuxStopScript([for (final p in all) p.identity]),
        timeout: const Duration(seconds: 60),
      ),
    );
  }

  // --- Ports -----------------------------------------------------------

  Future<List<LinuxPort>> scanPorts(LinuxProcessScan scan) async {
    final named = {for (final p in scan.processes) ...portsNamedIn(p.cmd)};
    return parseLinuxPorts(await _run(linuxPortScript(named)), scan);
  }

  // --- Storage ---------------------------------------------------------

  Future<LinuxStorageReport> measureStorage() async => LinuxStorageReport.parse(
    await _run(linuxStorageScript, timeout: const Duration(minutes: 3)),
  );

  /// Bytes [action] would give back now.
  Future<int> cleanableBytes(LinuxCleanupAction action) async {
    final du = parseLinuxDu(
      await _run(action.measureScript, timeout: const Duration(minutes: 2)),
    );
    return du.values.fold<int>(0, (a, b) => a + b) * 1024;
  }

  /// Runs [action] and returns the bytes it freed (two readings).
  Future<int> cleanup(LinuxCleanupAction action) async {
    assert(action.paths.every(linuxCleanupPathIsSafe));
    await requireQuiet();
    final before = parseLinuxDu(
      await _run(action.measureScript, timeout: const Duration(minutes: 2)),
    );
    await _run(action.script, timeout: const Duration(minutes: 5));
    final after = parseLinuxDu(
      await _run(action.measureScript, timeout: const Duration(minutes: 2)),
    );
    return linuxFreedBytes(before, after);
  }

  // --- Packages --------------------------------------------------------

  Future<List<LinuxPackage>> installedPackages() async =>
      parseLinuxPackages(await _run(linuxPackageListScript));

  Future<List<LinuxPackageCandidate>> checkPackages(List<String> names) async =>
      parseLinuxPackageCandidates(await _run(linuxPackageCheckScript(names)));

  /// The packages apt would remove along with [names].
  Future<List<String>> planRemoval(List<String> names) async =>
      parseLinuxRemovePlan(await _run(linuxRemovePlanScript(names)));

  // --- Long job --------------------------------------------------------

  Future<void> startJob(
    LinuxJobKind kind,
    List<String> names, {
    required String notice,
  }) async {
    await requireQuiet();
    // Taking the slot once proves nothing else holds it right now.
    await _run('exit 0', timeout: const Duration(seconds: 12));
    try {
      await linux.startService(
        linuxJobService,
        linuxJobScript(kind, names),
        notice: notice,
      );
    } on BuiltinLinuxException catch (error) {
      throw LinuxManagerException(
        LinuxProblem.unavailable,
        details: error.details ?? error.message,
      );
    }
  }

  Future<void> stopJob() => linux.stopService(linuxJobService);

  Future<LinuxJobState> readJob() async {
    final running = (await linux.status()).serviceRunning(linuxJobService);
    final log = await linux.serviceLog(linuxJobService, tailBytes: 24576);
    return LinuxJobState.fromLog(log, running: running);
  }
}

enum LinuxJobStatus { none, running, done, failed, interrupted, timedOut }

/// A package job as its log tells it.
@immutable
class LinuxJobState {
  const LinuxJobState({
    this.kind,
    this.names = const [],
    this.status = LinuxJobStatus.none,
    this.percent,
    this.stage,
    this.exitCode,
    this.tail = '',
  });

  static const none = LinuxJobState();

  final LinuxJobKind? kind;
  final List<String> names;
  final LinuxJobStatus status;

  /// 0-100 from apt's own status stream, null while unknown.
  final int? percent;

  /// The script's latest stage text: technical, for Details only.
  final String? stage;
  final int? exitCode;

  /// The end of the log, for Details.
  final String tail;

  bool get running => status == LinuxJobStatus.running;

  /// Another task held the slot when the job began.
  bool get wasBusy => exitCode == 75;

  factory LinuxJobState.fromLog(String log, {required bool running}) {
    if (log.trim().isEmpty && !running) return none;
    LinuxJobKind? kind;
    var names = const <String>[];
    int? percent;
    String? stage;
    int? exitCode;
    for (final line in log.split('\n')) {
      if (line.startsWith('::oc job ')) {
        final word = line.substring(9).trim();
        kind = LinuxJobKind.values.where((k) => k.name == word).firstOrNull;
      } else if (line.startsWith('::oc names')) {
        names = line
            .substring(10)
            .trim()
            .split(' ')
            .where((s) => s.isNotEmpty)
            .toList();
      } else if (line.startsWith('::oc percent ')) {
        final p = double.tryParse(line.substring(13).trim());
        if (p != null) percent = p.round().clamp(0, 100);
      } else if (line.startsWith('::oc stage ')) {
        stage = line.substring(11).trim();
      } else if (line.startsWith(linuxJobExitPrefix)) {
        exitCode = int.tryParse(
          line.substring(linuxJobExitPrefix.length).trim(),
        );
      }
    }
    if (kind == null && !running) return none;
    final status = running && exitCode == null
        ? LinuxJobStatus.running
        : exitCode == 0
        ? LinuxJobStatus.done
        : exitCode == 124
        ? LinuxJobStatus.timedOut
        : exitCode != null
        ? LinuxJobStatus.failed
        : LinuxJobStatus.interrupted;
    final lines = log.trimRight().split('\n');
    return LinuxJobState(
      kind: kind,
      names: names,
      status: status,
      percent: percent,
      stage: stage,
      exitCode: exitCode,
      tail: lines.skip(lines.length > 40 ? lines.length - 40 : 0).join('\n'),
    );
  }
}

/// Follows the one package job: polls while it runs, and keeps the last
/// state for every screen that shows it (this object outlives screens).
class LinuxJobController extends ChangeNotifier {
  LinuxJobController(
    this.manager, {
    this.interval = const Duration(seconds: 2),
  });

  final LinuxManager manager;
  final Duration interval;
  LinuxJobState _state = LinuxJobState.none;
  Timer? _timer;
  bool _reading = false;
  bool _disposed = false;
  bool _dismissed = false;

  LinuxJobState get state => _state;

  /// The person has seen how the last job ended: it is not shown again.
  void dismiss() {
    if (_state.running) return;
    _dismissed = true;
    _state = LinuxJobState.none;
    if (!_disposed) notifyListeners();
  }

  void _set(LinuxJobState next) {
    if (_dismissed && !next.running) next = LinuxJobState.none;
    final finished = _state.running && !next.running;
    _state = next;
    if (finished) setupToolsChanged.value++;
    if (!_disposed) notifyListeners();
    if (next.running) {
      _timer ??= Timer.periodic(interval, (_) => unawaited(refresh()));
    } else {
      _timer?.cancel();
      _timer = null;
    }
  }

  /// Reads the job again (after the app restarted, or on a tick).
  Future<void> refresh() async {
    if (_reading || _disposed) return;
    _reading = true;
    try {
      _set(await manager.readJob());
    } on BuiltinLinuxException {
      // Unreadable now: the last state stays.
    } finally {
      _reading = false;
    }
  }

  Future<void> start(
    LinuxJobKind kind,
    List<String> names, {
    required String notice,
  }) async {
    await manager.startJob(kind, names, notice: notice);
    _dismissed = false;
    _set(
      LinuxJobState(kind: kind, names: names, status: LinuxJobStatus.running),
    );
    unawaited(refresh());
  }

  Future<void> stop() async {
    await manager.stopJob();
    await refresh();
  }

  @override
  void dispose() {
    _disposed = true;
    _timer?.cancel();
    super.dispose();
  }
}

final linuxManagerProvider = Provider<LinuxManager>(
  (ref) => LinuxManager(ref.watch(builtinLinuxProvider)),
);

/// One controller for the app, so a job started on one screen is seen on
/// every other and after coming back.
final linuxJobProvider = Provider<LinuxJobController>((ref) {
  final controller = LinuxJobController(ref.watch(linuxManagerProvider));
  ref.onDispose(controller.dispose);
  unawaited(controller.refresh());
  return controller;
});
