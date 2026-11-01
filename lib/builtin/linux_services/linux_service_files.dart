/// The services' files, as text: the definition format (§1.3), the
/// supervisor's state file, the rules a definition must follow, and the
/// order a list of services is shown in. Pure Dart, shared by the real
/// [LinuxServices] and its fake.
library;

import 'package:flutter/foundation.dart';

import '../../ui/kit/kit_log_panel.dart' show KitLogLevel, KitLogLine;
import 'linux_services.dart';

/// Guest paths (inside Ubuntu).
const linuxServicesHome = '/root/.oc-builtin/svc';
const linuxServicesLogs = '/var/log/oc-svc';

/// The native service that runs the supervisor.
const linuxServicesNativeName = 'svc';

/// At most this many definitions.
const linuxServicesMax = 16;

final _name = RegExp(r'^[a-z][a-z0-9-]{1,23}$');
final _envKey = RegExp(r'^[A-Za-z_][A-Za-z0-9_]{0,63}$');

/// Names the app's own native services and jobs use.
bool linuxServiceNameReserved(String name) =>
    const {'server', 'svc', 'linux-job', 'phone-engine'}.contains(name) ||
    name.startsWith('agent-') ||
    name.startsWith('aiteam') ||
    name.startsWith('oc-');

bool linuxServiceNameValid(String name) =>
    _name.hasMatch(name) && !linuxServiceNameReserved(name);

/// Ports the app's own servers use on this phone: OpenCode (4097, and
/// Termux's 4096), Paseo (6767), AI Team (8472, a Termux team 8372).
const linuxReservedPorts = {4096, 4097, 6767, 8372, 8472};

bool _oneLine(String text) =>
    !text.contains('\n') && !text.contains('\r') && !text.contains('\x00');

/// Throws the [LinuxServiceException] the first broken rule calls for.
/// [others] are the other services' definitions; [isNew] counts the limit.
void validateServiceDef(
  LinuxServiceDef def, {
  required Iterable<LinuxServiceDef> others,
  required bool isNew,
}) {
  Never fail(LinuxServiceProblem problem, String why) =>
      throw LinuxServiceException(problem, details: why);
  if (!linuxServiceNameValid(def.name)) {
    fail(LinuxServiceProblem.invalidName, 'name ${def.name}');
  }
  final list = others.toList();
  if (list.any((o) => o.name == def.name)) {
    fail(LinuxServiceProblem.nameTaken, 'name ${def.name}');
  }
  if (isNew && list.length >= linuxServicesMax) {
    fail(LinuxServiceProblem.tooMany, '${list.length} services');
  }
  final command = def.command.trim();
  if (command.isEmpty || command.length > 4096 || !_oneLine(def.command)) {
    fail(LinuxServiceProblem.invalidCommand, 'command');
  }
  if (!def.dir.startsWith('/') ||
      !_oneLine(def.dir) ||
      def.dir.split('/').contains('..')) {
    fail(LinuxServiceProblem.invalidCommand, 'dir ${def.dir}');
  }
  if (!_oneLine(def.title) || def.title.trim().isEmpty) {
    fail(LinuxServiceProblem.invalidName, 'title');
  }
  final seconds = def.stopTimeout.inSeconds;
  if (seconds < 1 || seconds > 120) {
    fail(LinuxServiceProblem.invalidCommand, 'stop timeout $seconds');
  }
  for (final entry in def.env.entries) {
    if (!_envKey.hasMatch(entry.key) || !_oneLine(entry.value)) {
      fail(LinuxServiceProblem.invalidCommand, 'env ${entry.key}');
    }
  }
  final port = def.port;
  if (port != null) {
    if (port < 1024 || port > 65535 || linuxReservedPorts.contains(port)) {
      fail(LinuxServiceProblem.reservedPort, 'port $port');
    }
    if (list.any((o) => o.port == port)) {
      fail(LinuxServiceProblem.portTaken, 'port $port');
    }
  }
}

String _restartWord(LinuxRestart restart) => switch (restart) {
  LinuxRestart.always => 'always',
  LinuxRestart.onFailure => 'on-failure',
  LinuxRestart.never => 'never',
};

/// The `.svc` file for [def] (§1.3). Validate first: values are one line.
String encodeServiceDef(LinuxServiceDef def) {
  final lines = [
    'version=1',
    'name=${def.name}',
    'title=${def.title}',
    'command=${def.command}',
    'dir=${def.dir}',
    if (def.port != null) 'port=${def.port}',
    'restart=${_restartWord(def.restart)}',
    'stop_timeout=${def.stopTimeout.inSeconds}',
    if (def.catalogId != null) 'catalog=${def.catalogId}',
    for (final entry in def.env.entries) 'env.${entry.key}=${entry.value}',
  ];
  return '${lines.join('\n')}\n';
}

/// [text] as a definition named [name]; null when it is not one.
LinuxServiceDef? decodeServiceDef(String name, String text) {
  final values = <String, String>{};
  final env = <String, String>{};
  for (final line in text.split('\n')) {
    if (line.startsWith('#')) continue;
    final at = line.indexOf('=');
    if (at <= 0) continue;
    final key = line.substring(0, at);
    final value = line.substring(at + 1);
    if (key.startsWith('env.')) {
      env[key.substring(4)] = value;
    } else {
      values[key] = value;
    }
  }
  final command = values['command'];
  if (values['name'] != name || command == null || command.trim().isEmpty) {
    return null;
  }
  final port = int.tryParse(values['port'] ?? '');
  final stop = int.tryParse(values['stop_timeout'] ?? '');
  return LinuxServiceDef(
    name: name,
    title: (values['title'] ?? '').trim().isEmpty ? name : values['title'],
    command: command,
    dir: values['dir'] ?? '/root',
    port: port,
    restart: switch (values['restart']) {
      'always' => LinuxRestart.always,
      'never' => LinuxRestart.never,
      _ => LinuxRestart.onFailure,
    },
    stopTimeout: Duration(seconds: stop == null || stop < 1 ? 10 : stop),
    catalogId: values['catalog'],
    env: env,
  );
}

/// What the supervisor wrote in `state/<name>`.
@immutable
class LinuxServiceStateFile {
  const LinuxServiceStateFile({
    required this.phase,
    this.pid,
    this.since,
    this.restarts = 0,
    this.lastExit,
    this.lastExitAt,
    this.nextTry,
    this.fails = 0,
    this.held = false,
  });

  final LinuxServicePhase phase;
  final int? pid;
  final DateTime? since;
  final int restarts;
  final int? lastExit;
  final DateTime? lastExitAt;
  final DateTime? nextTry;

  /// Short runs in a row (five: gave up).
  final int fails;

  /// The heat guard holds every service off.
  final bool held;

  /// Null when [text] is not a whole state file (being written, or empty).
  static LinuxServiceStateFile? parse(String text) {
    final values = <String, String>{};
    for (final line in text.split('\n')) {
      final at = line.indexOf('=');
      if (at > 0) values[line.substring(0, at)] = line.substring(at + 1);
    }
    // `held` is the last line the supervisor writes: without it the file is
    // still being written.
    if (!values.containsKey('held')) return null;
    final phase = parsePhase(values['phase']);
    if (phase == null) return null;
    DateTime? at(String key) {
      final seconds = int.tryParse(values[key] ?? '');
      return seconds == null || seconds <= 0
          ? null
          : DateTime.fromMillisecondsSinceEpoch(seconds * 1000);
    }

    return LinuxServiceStateFile(
      phase: phase,
      pid: int.tryParse(values['pid'] ?? ''),
      since: at('since'),
      restarts: int.tryParse(values['restarts'] ?? '') ?? 0,
      lastExit: int.tryParse(values['last_exit'] ?? ''),
      lastExitAt: at('last_exit_at'),
      nextTry: at('next_try'),
      fails: int.tryParse(values['fails'] ?? '') ?? 0,
      held: values['held'] == '1',
    );
  }

  static LinuxServicePhase? parsePhase(String? word) => switch (word) {
    'off' => LinuxServicePhase.off,
    'starting' => LinuxServicePhase.starting,
    'running' => LinuxServicePhase.running,
    'backoff' => LinuxServicePhase.backoff,
    'port_busy' => LinuxServicePhase.portBusy,
    'failed' => LinuxServicePhase.failed,
    'exited' => LinuxServicePhase.exited,
    'stopping' => LinuxServicePhase.stopping,
    _ => null,
  };
}

/// One service from its files. [want] is the person's choice; [state] the
/// supervisor's file, null when there is none (never started, or the
/// supervisor is not running). [supervisorRunning] false: whatever the old
/// file says, nothing runs now.
LinuxServiceState combineServiceState({
  required LinuxServiceDef def,
  required bool want,
  required LinuxServiceStateFile? state,
  required bool supervisorRunning,
}) {
  if (state == null || !supervisorRunning) {
    return LinuxServiceState(
      def: def,
      on: want,
      // On with no supervisor (the app just opened, or Android ended it):
      // the app starts it now, so it reads as starting. Never `off` while
      // on: on-and-off is the heat hold, which only the supervisor says.
      phase: want ? LinuxServicePhase.starting : LinuxServicePhase.off,
      lastExit: state?.lastExit,
      lastExitAt: state?.lastExitAt,
      restarts: state?.restarts ?? 0,
    );
  }
  var phase = state.phase;
  // Asked to run and the supervisor has not reached it yet (within a tick).
  if (want && !state.held && phase == LinuxServicePhase.off) {
    phase = LinuxServicePhase.starting;
  }
  final running =
      phase == LinuxServicePhase.running || phase == LinuxServicePhase.stopping;
  return LinuxServiceState(
    def: def,
    on: want,
    phase: phase,
    pid: running ? state.pid : null,
    since: running ? state.since : null,
    lastExitAt: state.lastExitAt,
    nextTryAt:
        phase == LinuxServicePhase.backoff ||
            phase == LinuxServicePhase.portBusy
        ? state.nextTry
        : null,
    restarts: state.restarts,
    lastExit: state.lastExit,
  );
}

int _urgency(LinuxServicePhase phase) => switch (phase) {
  LinuxServicePhase.failed => 0,
  LinuxServicePhase.portBusy => 1,
  LinuxServicePhase.backoff => 2,
  LinuxServicePhase.starting || LinuxServicePhase.stopping => 3,
  LinuxServicePhase.running => 4,
  LinuxServicePhase.exited => 5,
  LinuxServicePhase.off => 6,
};

/// One list, most urgent first (failed, port busy, retrying, running, off),
/// then by title: the order the Services page shows.
List<LinuxServiceState> sortServices(Iterable<LinuxServiceState> states) =>
    states.toList()..sort((a, b) {
      final byUrgency = _urgency(a.phase).compareTo(_urgency(b.phase));
      if (byUrgency != 0) return byUrgency;
      return a.def.title.toLowerCase().compareTo(b.def.title.toLowerCase());
    });

/// A `::svc` line the supervisor writes into a service's log.
@immutable
class LinuxServiceMarker {
  const LinuxServiceMarker(this.kind, this.values);

  /// start, exit, backoff, gave-up, port-busy, stop.
  final String kind;

  /// The numbers after it: start (epoch, restart count), exit (code,
  /// uptime in s), backoff (delay in s), port-busy (port).
  final List<int> values;

  static LinuxServiceMarker? parse(String line) {
    if (!line.startsWith('::svc ')) return null;
    final words = line.substring(6).trim().split(' ');
    if (words.isEmpty || words.first.isEmpty) return null;
    return LinuxServiceMarker(words.first, [
      for (final word in words.skip(1)) ?int.tryParse(word),
    ]);
  }
}

/// [text] (the end of a log, maybe cut mid-line at the start) as lines.
/// Marker lines carry their level: a crash or giving up is an error, a
/// retry or a busy port a warning.
List<KitLogLine> serviceLogLines(String text, {bool cutStart = false}) {
  final lines = text.split('\n');
  if (cutStart && lines.length > 1) lines.removeAt(0);
  if (lines.isNotEmpty && lines.last.isEmpty) lines.removeLast();
  return [
    for (final line in lines)
      KitLogLine(line, level: _levelOf(LinuxServiceMarker.parse(line))),
  ];
}

KitLogLevel _levelOf(LinuxServiceMarker? marker) => switch (marker?.kind) {
  'gave-up' => KitLogLevel.error,
  'exit' when marker!.values.isNotEmpty && marker.values.first != 0 =>
    KitLogLevel.error,
  'backoff' || 'port-busy' => KitLogLevel.warning,
  _ => KitLogLevel.normal,
};
