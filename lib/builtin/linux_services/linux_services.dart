/// Services in the app's own Linux: programs a person keeps running (a bot,
/// Syncthing, SSH), run by the app's supervisor `oc-svd` inside one native
/// service, `svc` (docs/design/linux-manager-2026-10-31/2-architecture.md
/// §1; the API below is the frozen §1.8 contract the Services pages build
/// on).
///
/// Everything is files in the rootfs: a definition per service, the
/// person's choice (`want`: on or off), a restart token (`kick`), and the
/// supervisor's state and logs. Dart reads and writes them directly; no
/// channel call and no proot is needed to read a state or a log.
library;

import 'package:flutter/foundation.dart';

import '../../ui/kit/kit_log_panel.dart' show KitLogLine;

// The app's one LinuxServices (the real one), where the pages look for it.
export 'rootfs_linux_services.dart' show linuxServicesProvider;

/// What a service is doing, as the supervisor last wrote it.
enum LinuxServicePhase {
  /// Not running, and not wanted (or held off while the phone is too hot).
  off,

  /// Asked to run; the supervisor has not started it yet.
  starting,

  running,

  /// It crashed and is started again at [LinuxServiceState.nextTryAt].
  backoff,

  /// Something else answers on its port; tried again on the backoff.
  portBusy,

  /// Stopped after crashing too many times in a row; waits for Start.
  failed,

  /// It ended by itself and its restart rule does not bring it back.
  exited,

  /// Being stopped (SIGTERM sent; SIGKILL after its stop timeout).
  stopping,
}

/// When a service that ends by itself is started again.
enum LinuxRestart {
  /// Whatever its exit code.
  always,

  /// Only after a non-zero exit (a crash).
  onFailure,

  /// Never: it runs once.
  never,
}

/// One service's definition (`/root/.oc-builtin/svc/defs/<name>.svc`).
@immutable
class LinuxServiceDef {
  const LinuxServiceDef({
    required this.name,
    required this.command,
    String? title,
    this.dir = '/root',
    this.port,
    this.restart = LinuxRestart.onFailure,
    this.stopTimeout = const Duration(seconds: 10),
    this.catalogId,
    this.env = const {},
  }) : title = title ?? name;

  /// `[a-z][a-z0-9-]{1,23}`, not a reserved name.
  final String name;

  /// What people call it ("Syncthing"); the name when not given.
  final String title;

  /// One shell command line, run with `bash -c` in [dir].
  final String command;

  /// An absolute folder inside Linux.
  final String dir;

  /// The port it listens on, 1024–65535, when it has one.
  final int? port;
  final LinuxRestart restart;

  /// How long a stop waits after SIGTERM before SIGKILL (1–120 s).
  final Duration stopTimeout;

  /// The catalog app it came from; null for a command of the person's own.
  final String? catalogId;
  final Map<String, String> env;

  LinuxServiceDef copyWith({
    String? title,
    String? command,
    String? dir,
    int? port,
    bool clearPort = false,
    LinuxRestart? restart,
    Duration? stopTimeout,
    Map<String, String>? env,
  }) => LinuxServiceDef(
    name: name,
    title: title ?? this.title,
    command: command ?? this.command,
    dir: dir ?? this.dir,
    port: clearPort ? null : port ?? this.port,
    restart: restart ?? this.restart,
    stopTimeout: stopTimeout ?? this.stopTimeout,
    catalogId: catalogId,
    env: env ?? this.env,
  );

  @override
  bool operator ==(Object other) =>
      other is LinuxServiceDef &&
      other.name == name &&
      other.title == title &&
      other.command == command &&
      other.dir == dir &&
      other.port == port &&
      other.restart == restart &&
      other.stopTimeout == stopTimeout &&
      other.catalogId == catalogId &&
      mapEquals(other.env, env);

  @override
  int get hashCode => Object.hash(
    name,
    title,
    command,
    dir,
    port,
    restart,
    stopTimeout,
    catalogId,
    Object.hashAllUnordered(env.entries.map((e) => '${e.key}=${e.value}')),
  );
}

/// A service as it is now: its definition, the person's choice ([on]) and
/// what the supervisor last said.
@immutable
class LinuxServiceState {
  const LinuxServiceState({
    required this.def,
    required this.on,
    required this.phase,
    this.pid,
    this.since,
    this.lastExitAt,
    this.nextTryAt,
    this.restarts = 0,
    this.lastExit,
  });

  final LinuxServiceDef def;

  /// The person's choice: keep it running (true) or keep it stopped.
  final bool on;
  final LinuxServicePhase phase;
  final int? pid;

  /// When the current run began.
  final DateTime? since;
  final DateTime? lastExitAt;

  /// When a service in [LinuxServicePhase.backoff] or
  /// [LinuxServicePhase.portBusy] is tried again.
  final DateTime? nextTryAt;

  /// Automatic restarts since the supervisor started.
  final int restarts;
  final int? lastExit;

  String get name => def.name;

  @override
  bool operator ==(Object other) =>
      other is LinuxServiceState &&
      other.def == def &&
      other.on == on &&
      other.phase == phase &&
      other.pid == pid &&
      other.since == since &&
      other.lastExitAt == lastExitAt &&
      other.nextTryAt == nextTryAt &&
      other.restarts == restarts &&
      other.lastExit == lastExit;

  @override
  int get hashCode => Object.hash(
    def,
    on,
    phase,
    pid,
    since,
    lastExitAt,
    nextTryAt,
    restarts,
    lastExit,
  );
}

/// Why a services act failed; screens turn it into words.
enum LinuxServiceProblem {
  /// Linux is not installed (or not on this device).
  linuxMissing,
  invalidName,

  /// Another service has this name already.
  nameTaken,

  /// Empty, too long, or not one line.
  invalidCommand,

  /// Below 1024, above 65535, or one the app uses (OpenCode, Paseo, AI Team).
  reservedPort,

  /// Another service uses this port.
  portTaken,

  /// The limit of services is reached.
  tooMany,

  /// The supervisor could not be started.
  supervisorDown,

  /// A file could not be written (often: no space).
  writeFailed,
}

class LinuxServiceException implements Exception {
  const LinuxServiceException(this.problem, {this.details});

  final LinuxServiceProblem problem;

  /// Technical text, for Details only (redacted by the screen).
  final String? details;

  @override
  String toString() => 'LinuxServiceException(${problem.name})';
}

/// The services API (§1.8). [RootfsLinuxServices] is the real one; tests and
/// pages use `FakeLinuxServices` (test/support/fake_linux_services.dart).
abstract interface class LinuxServices {
  /// Definitions and state files; never throws on one bad file (that one is
  /// left out).
  Future<List<LinuxServiceState>> read();

  /// [read] every [every], only while listened to.
  Stream<List<LinuxServiceState>> watch({
    Duration every = const Duration(seconds: 2),
  });

  /// Validates and saves [def]; does not start it.
  Future<void> save(LinuxServiceDef def);

  /// Keep it running: want=on, and a new kick unless it runs already;
  /// starts the supervisor when needed.
  Future<void> start(String name);

  /// Keep it stopped: want=off.
  Future<void> stop(String name);

  /// A new kick: stop and start again.
  Future<void> restart(String name);

  /// Stops it, waits for it to be off (at most its stop timeout + 2 s),
  /// then deletes its definition, state and logs.
  Future<void> remove(String name);

  /// The end of its log, newest last, at most [bytes].
  Future<List<KitLogLine>> logTail(String name, {int bytes = 65536});

  /// Starts the native service `svc` when a service is on and it does not
  /// run; installs or upgrades the supervisor script first.
  Future<void> ensureSupervisor();
}
