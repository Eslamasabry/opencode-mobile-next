// The one LinuxServices fake for page tests (the Services pages) and API
// tests: services in memory, in the order given, with the same definition
// rules as the real one, knobs to fail or hold an act, and a log of every
// call. No files, no channel, no timers. Acts change [states] but do not
// push to [watch]; a test pushes with [emit] (or [put]), as the supervisor
// would on its next state write.

import 'dart:async';

import 'package:opencode_mobile/builtin/linux_services/linux_service_files.dart';
import 'package:opencode_mobile/builtin/linux_services/linux_services.dart';
import 'package:opencode_mobile/ui/kit/kit_log_panel.dart' show KitLogLine;

class FakeLinuxServices implements LinuxServices {
  FakeLinuxServices({
    Iterable<LinuxServiceState> states = const [],
    this.linuxInstalled = true,
  }) : states = List.of(states);

  /// What [read] answers, in this order.
  List<LinuxServiceState> states;

  /// False: every act throws [LinuxServiceProblem.linuxMissing].
  bool linuxInstalled;

  /// Thrown by [read] while set.
  Object? readError;

  /// Thrown by [start] / [save] while set.
  Object? startError;
  Object? saveError;

  /// Thrown by the next act (then cleared).
  LinuxServiceException? failNext;

  /// While not completed, every act waits for it (a slow phone).
  Completer<void>? hold;

  /// The last definition [save] accepted.
  LinuxServiceDef? saved;

  /// Every act, in order: `save my-bot`, `start syncthing`, ….
  final calls = <String>[];

  /// What [logTail] answers: [logs] for that service, else [log].
  List<KitLogLine> log = const [];
  final logs = <String, List<KitLogLine>>{};

  /// Whether [ensureSupervisor] would have the supervisor running, and how
  /// often it was asked (not in [calls]: pages may ask at any time).
  bool supervisorRunning = false;
  int ensures = 0;

  final _changes = StreamController<List<LinuxServiceState>>.broadcast();

  LinuxServiceState? operator [](String name) =>
      states.where((s) => s.name == name).firstOrNull;

  /// Sets or replaces [state] and tells watchers.
  void put(LinuxServiceState state) {
    final at = states.indexWhere((s) => s.name == state.name);
    if (at < 0) {
      states = [...states, state];
    } else {
      states = [...states]..[at] = state;
    }
    emit();
  }

  /// Pushes the current [states] to watchers.
  void emit() => _changes.add(List.of(states));

  void _replace(String name, LinuxServiceState state) {
    states = [for (final s in states) s.name == name ? state : s];
  }

  Future<void> _act(String what) async {
    calls.add(what);
    await hold?.future;
    final fail = failNext;
    if (fail != null) {
      failNext = null;
      throw fail;
    }
    if (!linuxInstalled) {
      throw const LinuxServiceException(LinuxServiceProblem.linuxMissing);
    }
  }

  LinuxServiceState _require(String name) =>
      this[name] ??
      (throw LinuxServiceException(
        LinuxServiceProblem.invalidName,
        details: 'no service $name',
      ));

  @override
  Future<List<LinuxServiceState>> read() async {
    if (readError case final error?) throw error;
    if (!linuxInstalled) {
      throw const LinuxServiceException(LinuxServiceProblem.linuxMissing);
    }
    return List.of(states);
  }

  @override
  Stream<List<LinuxServiceState>> watch({
    Duration every = const Duration(seconds: 2),
  }) => _changes.stream;

  @override
  Future<void> save(LinuxServiceDef def) async {
    await _act('save ${def.name}');
    if (saveError case final error?) throw error;
    final old = this[def.name];
    validateServiceDef(
      def,
      others: [
        for (final s in states)
          if (s.name != def.name) s.def,
      ],
      isNew: old == null,
    );
    saved = def;
    final next = old == null
        ? LinuxServiceState(def: def, on: false, phase: LinuxServicePhase.off)
        : LinuxServiceState(
            def: def,
            on: old.on,
            phase: old.phase,
            pid: old.pid,
            since: old.since,
            lastExitAt: old.lastExitAt,
            nextTryAt: old.nextTryAt,
            restarts: old.restarts,
            lastExit: old.lastExit,
          );
    if (old == null) {
      states = [...states, next];
    } else {
      _replace(def.name, next);
    }
  }

  @override
  Future<void> start(String name) async {
    await _act('start $name');
    if (startError case final error?) throw error;
    final old = _require(name);
    supervisorRunning = true;
    _replace(
      name,
      LinuxServiceState(
        def: old.def,
        on: true,
        phase: LinuxServicePhase.running,
        pid: old.pid ?? 4000 + states.length,
        since: old.phase == LinuxServicePhase.running
            ? old.since
            : DateTime.now(),
        restarts: old.restarts,
      ),
    );
  }

  @override
  Future<void> stop(String name) async {
    await _act('stop $name');
    final old = _require(name);
    _replace(
      name,
      LinuxServiceState(
        def: old.def,
        on: false,
        phase: LinuxServicePhase.off,
        lastExitAt: old.lastExitAt,
        lastExit: old.lastExit,
        restarts: old.restarts,
      ),
    );
  }

  @override
  Future<void> restart(String name) async {
    await _act('restart $name');
    _require(name);
  }

  @override
  Future<void> remove(String name) async {
    await _act('remove $name');
    states = [
      for (final s in states)
        if (s.name != name) s,
    ];
    logs.remove(name);
  }

  @override
  Future<List<KitLogLine>> logTail(String name, {int bytes = 65536}) async =>
      logs[name] ?? log;

  @override
  Future<void> ensureSupervisor() async {
    ensures++;
    if (states.any((s) => s.on)) supervisorRunning = true;
  }

  Future<void> dispose() => _changes.close();
}
