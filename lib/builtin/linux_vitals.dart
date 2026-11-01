/// The vitals on top of Linux on this phone (S2a, docs/design/
/// linux-manager-2026-10-31/2-architecture.md §2): how hot Android says the
/// phone is, how much memory the phone has free, and how much CPU and
/// memory the app's own Linux uses. Everything comes from Android's side,
/// never from inside Ubuntu, where `/proc/stat` and friends are frozen
/// stand-ins.
///
/// Patchable sources only:
/// - heat: `oc/thermal` `current` (PowerManager's thermal status);
/// - phone memory free: the app process reads the kernel's live
///   `/proc/meminfo` (`MemAvailable`), which Android lets apps read;
/// - Linux CPU and memory: the native process list (`processes`, about a
///   second of CPU measured per call), every process but the app's own.
///
/// Not readable without native code, so not shown (S2b adds them): battery
/// level, charging and temperature (BatteryManager), and the phone's
/// low-memory flag.
///
/// Sampling is cheap: [LinuxVitalsWatch] reads only while a page has
/// started it, at most every [LinuxVitalsWatch.every] (5 s), one read at a
/// time, and skips a tick while its page is covered.
library;

import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../platform/thermal.dart';
import 'phone_processes.dart';

/// Heat in the four words the page uses; null status: Android does not say.
enum LinuxHeat { cool, warm, hot, tooHot }

LinuxHeat? linuxHeatOf(ThermalStatus status) => switch (status) {
  ThermalStatus.unknown => null,
  ThermalStatus.none || ThermalStatus.light => LinuxHeat.cool,
  ThermalStatus.moderate => LinuxHeat.warm,
  ThermalStatus.severe => LinuxHeat.hot,
  ThermalStatus.critical ||
  ThermalStatus.emergency ||
  ThermalStatus.shutdown => LinuxHeat.tooHot,
};

/// One reading. A null field could not be read and is not shown.
@immutable
class LinuxVitals {
  const LinuxVitals({
    this.heat,
    this.phoneMemoryFreeBytes,
    this.linuxCpuPercent,
    this.linuxMemoryBytes,
    this.heaviestProgram,
    this.heaviestCpuPercent,
  });

  final LinuxHeat? heat;
  final int? phoneMemoryFreeBytes;

  /// Share of the whole phone (all cores), 0–100.
  final double? linuxCpuPercent;
  final int? linuxMemoryBytes;

  /// The Linux program using the most CPU, and its share of one core.
  final String? heaviestProgram;
  final double? heaviestCpuPercent;

  /// A program that keeps at least half a core busy is worth naming.
  static const heavyCore = 50.0;

  bool get hasHeavyProgram =>
      heaviestProgram != null && (heaviestCpuPercent ?? 0) >= heavyCore;

  @override
  bool operator ==(Object other) =>
      other is LinuxVitals &&
      other.heat == heat &&
      other.phoneMemoryFreeBytes == phoneMemoryFreeBytes &&
      other.linuxCpuPercent == linuxCpuPercent &&
      other.linuxMemoryBytes == linuxMemoryBytes &&
      other.heaviestProgram == heaviestProgram &&
      other.heaviestCpuPercent == heaviestCpuPercent;

  @override
  int get hashCode => Object.hash(
    heat,
    phoneMemoryFreeBytes,
    linuxCpuPercent,
    linuxMemoryBytes,
    heaviestProgram,
    heaviestCpuPercent,
  );
}

/// `MemAvailable` of [text] (`/proc/meminfo`) in bytes; null when absent.
int? parseMemAvailable(String text) {
  for (final line in text.split('\n')) {
    if (!line.startsWith('MemAvailable:')) continue;
    final kb = int.tryParse(
      line.substring(13).trim().split(RegExp(r'\s+')).first,
    );
    return kb == null ? null : kb * 1024;
  }
  return null;
}

/// Linux's share of [snapshot]: every process but the app's own Android
/// ones. [cores] turns per-core percentages into a share of the phone.
({double cpu, int memory, String? heaviest, double? heaviestCpu}) linuxShareOf(
  PhoneProcessSnapshot snapshot, {
  required int cores,
}) {
  var cpu = 0.0;
  var memory = 0;
  PhoneProcess? top;
  for (final p in snapshot.processes) {
    if (p.owner == PhoneProcessOwner.app) continue;
    cpu += p.cpuPercent;
    memory += p.rssBytes;
    if (top == null || p.cpuPercent > top.cpuPercent) top = p;
  }
  final share = cores <= 0 ? cpu : cpu / cores;
  return (
    cpu: share.clamp(0, 100).toDouble(),
    memory: memory,
    heaviest: top == null || top.name.isEmpty ? null : top.name,
    heaviestCpu: top?.cpuPercent,
  );
}

/// Reads one [LinuxVitals]; each source may fail alone.
class LinuxVitalsSource {
  LinuxVitalsSource({
    ThermalBridge? thermal,
    PhoneProcesses? processes,
    Future<String> Function()? meminfo,
    int? cores,
  }) : _thermal = thermal ?? ThermalBridge(),
       _processes = processes ?? PhoneProcesses(),
       _meminfo = meminfo ?? (() => File('/proc/meminfo').readAsString()),
       _cores = cores ?? Platform.numberOfProcessors;

  final ThermalBridge _thermal;
  final PhoneProcesses _processes;
  final Future<String> Function() _meminfo;
  final int _cores;

  Future<LinuxVitals> read() async {
    final heat = linuxHeatOf((await _thermal.current()).status);
    int? free;
    try {
      free = parseMemAvailable(await _meminfo());
    } on Object {
      free = null;
    }
    double? cpu;
    int? memory;
    String? heaviest;
    double? heaviestCpu;
    try {
      final share = linuxShareOf(await _processes.list(), cores: _cores);
      cpu = share.cpu;
      memory = share.memory;
      heaviest = share.heaviest;
      heaviestCpu = share.heaviestCpu;
    } on Object {
      // The list could not be read now: Linux's share is left out.
    }
    return LinuxVitals(
      heat: heat,
      phoneMemoryFreeBytes: free,
      linuxCpuPercent: cpu,
      linuxMemoryBytes: memory,
      heaviestProgram: heaviest,
      heaviestCpuPercent: heaviestCpu,
    );
  }
}

/// Follows the vitals while a page shows them: [start] when it opens,
/// [stop] when it closes. [visible] is asked before each read; a covered
/// page skips the tick.
class LinuxVitalsWatch extends ChangeNotifier {
  LinuxVitalsWatch(
    this.source, {
    this.every = const Duration(seconds: 5),
    bool Function()? visible,
  }) : _visible = visible ?? (() => true);

  final LinuxVitalsSource source;

  /// At most one read this often.
  final Duration every;
  final bool Function() _visible;

  LinuxVitals? _vitals;
  Timer? _timer;
  bool _reading = false;
  bool _disposed = false;

  /// Null until the first reading arrives.
  LinuxVitals? get vitals => _vitals;
  bool get running => _timer != null;

  void start() {
    if (_timer != null || _disposed) return;
    _timer = Timer.periodic(every, (_) => unawaited(_tick()));
    unawaited(_tick());
  }

  void stop() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> _tick() async {
    if (_reading || _disposed || !_visible()) return;
    _reading = true;
    try {
      final next = await source.read();
      // Stopped meanwhile: the page is gone, nobody is told.
      if (_disposed || _timer == null) return;
      if (next != _vitals) {
        _vitals = next;
        notifyListeners();
      }
    } on Object {
      // Nothing new this time; the last reading stays.
    } finally {
      _reading = false;
    }
  }

  @override
  void dispose() {
    _disposed = true;
    stop();
    super.dispose();
  }
}

/// Where the Linux page reads its vitals; tests give a fake source.
final linuxVitalsSourceProvider = Provider<LinuxVitalsSource>(
  (ref) => LinuxVitalsSource(),
);
