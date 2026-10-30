import 'package:flutter/services.dart';

import 'builtin_linux.dart';

/// Which part of the app a process on this phone belongs to (native
/// `NativeProcessList.OWNERS`).
enum PhoneProcessOwner {
  /// The app's own Android processes.
  app('app'),

  /// The in-app OpenCode server and everything it runs.
  opencode('opencode'),

  /// The agent helper (Paseo) with Claude Code and its tools.
  paseo('paseo'),

  /// An agent's sign-in.
  agentSignIn('agent-sign-in'),

  /// The AI Team engine.
  engine('engine'),

  /// Another named service of the app.
  helper('helper'),

  /// A one-off job: a setup step or a check.
  task('task'),

  /// A terminal session.
  terminal('terminal'),

  /// Left running by a Linux that has ended; nothing will ever stop it.
  orphan('orphan'),

  /// None of the above.
  other('other');

  const PhoneProcessOwner(this.wire);
  final String wire;

  static PhoneProcessOwner parse(Object? value) => values.firstWhere(
    (owner) => owner.wire == value,
    orElse: () => PhoneProcessOwner.other,
  );
}

/// One process of this app on the phone.
class PhoneProcess {
  const PhoneProcess({
    required this.pid,
    required this.ppid,
    required this.name,
    required this.command,
    required this.cpuPercent,
    required this.rssBytes,
    required this.elapsed,
    required this.owner,
    this.session,
  });

  /// Null when [map] is not a process row.
  static PhoneProcess? fromMap(Map<Object?, Object?> map) {
    final pid = map['pid'];
    if (pid is! int) return null;
    final cpu = map['cpuPercent'];
    final session = map['session'];
    return PhoneProcess(
      pid: pid,
      ppid: map['ppid'] is int ? map['ppid'] as int : 0,
      name: map['name'] is String ? map['name'] as String : '',
      command: map['command'] is String ? map['command'] as String : '',
      cpuPercent: cpu is num ? cpu.toDouble() : 0,
      rssBytes: map['rssBytes'] is int ? map['rssBytes'] as int : 0,
      elapsed: Duration(
        seconds: map['elapsedSeconds'] is int
            ? map['elapsedSeconds'] as int
            : 0,
      ),
      owner: PhoneProcessOwner.parse(map['owner']),
      session: session is String && session.isNotEmpty ? session : null,
    );
  }

  final int pid;
  final int ppid;

  /// The kernel's short name (at most 15 characters).
  final String name;

  /// Its command line, at most 200 characters, secret-looking values masked.
  final String command;

  /// Share of one CPU core over about a second, so up to 100 × cores.
  final double cpuPercent;
  final int rssBytes;

  /// How long it has run.
  final Duration elapsed;
  final PhoneProcessOwner owner;

  /// The agent session it runs (Claude Code's `--resume` id), if any.
  final String? session;
}

/// What runs now, and what the orphan clean-up has stopped since app start.
class PhoneProcessSnapshot {
  const PhoneProcessSnapshot({
    this.processes = const [],
    this.orphansReaped = 0,
    this.orphansLastReapedAt,
  });

  factory PhoneProcessSnapshot.fromMap(Map<Object?, Object?> map) {
    final at = map['orphansLastReapedAtMs'];
    return PhoneProcessSnapshot(
      processes: List.unmodifiable([
        if (map['processes'] case final List rows)
          for (final row in rows)
            if (row is Map<Object?, Object?>) ?PhoneProcess.fromMap(row),
      ]),
      orphansReaped: map['orphansReaped'] is int
          ? map['orphansReaped'] as int
          : 0,
      orphansLastReapedAt: at is int
          ? DateTime.fromMillisecondsSinceEpoch(at, isUtc: true)
          : null,
    );
  }

  final List<PhoneProcess> processes;
  final int orphansReaped;
  final DateTime? orphansLastReapedAt;
}

/// Why a process was not stopped.
enum PhoneProcessStopRefusal {
  /// The app itself.
  app('app'),

  /// The root of a server, helper, terminal or task the app runs: stop
  /// that through its own Stop instead.
  serverRoot('serverRoot'),

  /// No such live process of this app (it may have ended already).
  notFound('notFound'),

  /// Not a process id.
  invalid('invalid'),

  /// Still running after SIGTERM and SIGKILL.
  stillRunning('stillRunning');

  const PhoneProcessStopRefusal(this.wire);
  final String wire;

  static PhoneProcessStopRefusal parse(Object? value) => values.firstWhere(
    (reason) => reason.wire == value,
    orElse: () => PhoneProcessStopRefusal.notFound,
  );
}

class PhoneProcessStopResult {
  const PhoneProcessStopResult({
    this.stopped = const [],
    this.refused = const {},
  });

  factory PhoneProcessStopResult.fromMap(Map<Object?, Object?> map) =>
      PhoneProcessStopResult(
        stopped: List.unmodifiable([
          if (map['stopped'] case final List pids)
            for (final pid in pids)
              if (pid is int) pid,
        ]),
        refused: Map.unmodifiable({
          if (map['refused'] case final List rows)
            for (final row in rows)
              if (row case {'pid': final int pid, 'reason': final reason})
                pid: PhoneProcessStopRefusal.parse(reason),
        }),
      );

  final List<int> stopped;
  final Map<int, PhoneProcessStopRefusal> refused;
}

/// "What's running on this phone": this app's processes, read natively
/// (BuiltinLinux.processes / stopProcesses). Read-only except [stop].
class PhoneProcesses {
  PhoneProcesses({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel(BuiltinLinux.channelName);

  final MethodChannel _channel;

  /// Takes about a second: CPU use is measured over that time.
  Future<PhoneProcessSnapshot> list() async {
    if (!BuiltinLinux.supported) return const PhoneProcessSnapshot();
    final raw = await _channel.invokeMethod<Map<Object?, Object?>>('processes');
    return PhoneProcessSnapshot.fromMap(raw ?? const {});
  }

  /// SIGTERM, then SIGKILL after a short wait, to each of [pids] that is a
  /// live process of this app and neither the app nor a server's root.
  Future<PhoneProcessStopResult> stop(Iterable<int> pids) async {
    if (!BuiltinLinux.supported) return const PhoneProcessStopResult();
    final raw = await _channel.invokeMethod<Map<Object?, Object?>>(
      'stopProcesses',
      {'pids': pids.toSet().toList()},
    );
    return PhoneProcessStopResult.fromMap(raw ?? const {});
  }
}
