part of 'builtin_linux.dart';

/// How proot runs the in-app server, as `/proc` shows it (BuiltinLinux.kt
/// `performance`).
enum BuiltinProotMode {
  /// proot's seccomp filter is in place: most system calls run without
  /// stopping in proot. What Termux's proot does too.
  seccomp,

  /// Every system call stops in proot: several times slower.
  ptrace,

  /// No server runs, or the kernel does not say.
  unknown;

  static BuiltinProotMode parse(Object? value) => switch (value) {
    'seccomp' => seccomp,
    'ptrace' => ptrace,
    _ => unknown,
  };
}

/// Classification of an exit, without inferring a confirmed memory shortage.
enum BuiltinServiceExitReason {
  /// Exit 137 may be a memory or Android phantom-process kill. The exit code
  /// alone cannot distinguish those from another SIGKILL.
  memoryOrPhantomKill,
  exited,
  unknown;

  static BuiltinServiceExitReason parse(Object? value) => switch (value) {
    'memory_or_phantom_kill' => memoryOrPhantomKill,
    'exited' => exited,
    _ => unknown,
  };
}

/// One app-owned service's lifetime diagnostics, without command or log text.
class BuiltinServiceDiagnostics {
  const BuiltinServiceDiagnostics({
    this.running = false,
    this.lastExitCode,
    this.lastUptimeMs,
    this.lastExitAtMs,
    this.lastStopRequested,
    this.uptimeMs,
    this.restartCount = 0,
    this.exitReason = BuiltinServiceExitReason.unknown,
  });

  factory BuiltinServiceDiagnostics.fromMap(Map<Object?, Object?> map) {
    // Native counters are integers. Do not truncate fractional or non-finite
    // values into apparently valid diagnostics.
    int? asInt(Object? value) => value is int ? value : null;
    int? nonNegativeInt(Object? value) => switch (asInt(value)) {
      final int number when number >= 0 => number,
      _ => null,
    };
    return BuiltinServiceDiagnostics(
      running: map['running'] == true,
      lastExitCode: asInt(map['lastExitCode']),
      lastUptimeMs: nonNegativeInt(map['lastUptimeMs']),
      lastExitAtMs: switch (map['lastExitAtMs']) {
        final int at when at > 0 && at <= 8640000000000000 => at,
        _ => null,
      },
      lastStopRequested: map['lastStopRequested'] is bool
          ? map['lastStopRequested'] as bool
          : null,
      uptimeMs: nonNegativeInt(map['uptimeMs']),
      restartCount: nonNegativeInt(map['restartCount']) ?? 0,
      exitReason: BuiltinServiceExitReason.parse(map['exitReason']),
    );
  }

  final bool running;
  final int? lastExitCode;

  /// Completed run duration. Null means no valid observation is available.
  final int? lastUptimeMs;

  /// Wall-clock time of the observed exit, never fabricated after app death.
  final int? lastExitAtMs;

  /// Null on older APKs. True when our runtime deliberately stopped the child.
  final bool? lastStopRequested;

  /// Current run duration. Null means no run or no valid observation.
  final int? uptimeMs;
  final int restartCount;
  final BuiltinServiceExitReason exitReason;
}

/// Admission of one named chat lease, independent of other work's wake lock.
class BuiltinWorkLeaseStatus {
  const BuiltinWorkLeaseStatus({this.held = false, this.capped = false});

  factory BuiltinWorkLeaseStatus.fromMap(Map<Object?, Object?> map) {
    if (map['held'] is! bool ||
        map['capped'] is! bool ||
        map['held'] == true && map['capped'] == true) {
      return const BuiltinWorkLeaseStatus();
    }
    return BuiltinWorkLeaseStatus(
      held: map['held'] == true,
      capped: map['capped'] == true,
    );
  }

  /// This lease has an admitted CPU hold; never the aggregate work-lock state.
  final bool held;

  /// Native monotonic lifetime expired or exhausted this logical chat lease.
  final bool capped;
}

/// One reading of `performance`: what the Performance details show.
class BuiltinPerformance {
  const BuiltinPerformance({
    this.serverRunning = false,
    this.prootMode = BuiltinProotMode.unknown,
    this.prootFilters,
    this.serverFilters,
    this.workHeld = false,
    this.services = const {},
  });

  factory BuiltinPerformance.fromMap(Map<Object?, Object?> map) {
    int? asInt(Object? value) => value is int ? value : null;
    return BuiltinPerformance(
      serverRunning: map['serverRunning'] == true,
      prootMode: BuiltinProotMode.parse(map['prootMode']),
      prootFilters: asInt(map['prootFilters']),
      serverFilters: asInt(map['serverFilters']),
      workHeld: map['workHeld'] == true,
      services: Map.unmodifiable({
        if (map['services'] case final Map services)
          for (final entry in services.entries)
            if (entry.key case final String name when name.isNotEmpty)
              if (entry.value case final Map<Object?, Object?> diagnostics)
                name: BuiltinServiceDiagnostics.fromMap(diagnostics),
      }),
    );
  }

  final bool serverRunning;
  final BuiltinProotMode prootMode;

  /// Seccomp filters on proot itself (Android's own app filter) and on the
  /// server under it; the server has one more when proot's is in place.
  final int? prootFilters;
  final int? serverFilters;

  /// Whether the phone is kept awake for a running reply now.
  final bool workHeld;

  /// Current and previously launched services keyed by native service name.
  /// Empty on older APKs. These snapshots contain no credentials or log text.
  final Map<String, BuiltinServiceDiagnostics> services;
}

/// The answer of one `run`.
class BuiltinLinuxRunResult {
  const BuiltinLinuxRunResult({required this.exitCode, required this.output});

  final int exitCode;

  /// The last 64 KB of stdout and stderr together.
  final String output;

  bool get ok => exitCode == 0;
}

class BuiltinLinuxException implements Exception {
  const BuiltinLinuxException(this.message, {this.code, this.details});

  final String message;
  final String? code;

  /// The phone's own description of the failure, when it sent one: kept for
  /// Details, never shown as the message.
  final String? details;

  @override
  String toString() => message;
}

/// Native lifetime and prerequisites, without authentication material.
class BuiltinPhoneEngineStatus {
  const BuiltinPhoneEngineStatus({
    required this.profileId,
    this.running = false,
    this.port,
    this.boundary = false,
    this.execution = false,
    this.restartRequired = false,
    this.boundaryReason = 'boundary_unverified',
    this.boundaryTier = 'none',
    this.boundaryGeneration,
    this.protectionRequired = false,
    this.unconfinedChildren = false,
  });

  factory BuiltinPhoneEngineStatus.fromMap(Map<Object?, Object?> map) {
    final tier = map['boundaryTier'];
    if (map.containsKey('boundaryTier') &&
        !{'none', 'landlock', 'proot'}.contains(tier)) {
      throw const BuiltinLinuxException(
        'Phone engine status is unavailable.',
        code: 'engine_status_invalid',
      );
    }
    return BuiltinPhoneEngineStatus(
      profileId: map['profileId'] is String ? map['profileId'] as String : '',
      running: map['running'] == true,
      port: map['port'] is num ? (map['port'] as num).toInt() : null,
      boundary: map['boundary'] == true,
      boundaryTier: tier as String? ?? 'none',
      execution: map['execution'] == true,
      restartRequired: map['restartRequired'] == true,
      boundaryReason: map['boundaryReason'] is String
          ? map['boundaryReason'] as String
          : 'boundary_unverified',
      boundaryGeneration: map['boundaryGeneration'] is String
          ? map['boundaryGeneration'] as String
          : null,
      protectionRequired: map['protectionRequired'] == true,
      unconfinedChildren: map['unconfinedChildren'] == true,
    );
  }

  final String profileId;
  final bool running;
  final int? port;
  final bool boundary;
  final bool execution;
  final bool restartRequired;
  final String boundaryReason;

  /// `none`, `landlock`, or `proot`; no tier is inferred from flags in a
  /// status response from an older native bridge.
  final String boundaryTier;
  final String? boundaryGeneration;
  final bool protectionRequired;

  /// App-wide live old server/terminal prerequisite, independent of daemon life.
  final bool unconfinedChildren;
}

/// Ephemeral channel handoff. Never persist this object or include it in logs.
class BuiltinPhoneEngineCredentials {
  const BuiltinPhoneEngineCredentials({
    required this.baseUrl,
    required this.bearerToken,
  });

  final String baseUrl;
  final String bearerToken;

  @override
  String toString() => 'BuiltinPhoneEngineCredentials(<redacted>)';
}

/// Fresh logical file sizes, not the cached status estimate or filesystem
/// allocation. Values can change while programs write; refresh after removal.
class BuiltinProjectStorage {
  const BuiltinProjectStorage({
    required this.runtimeBytes,
    required this.projectsBytes,
    required this.measuredAtMilliseconds,
  });

  factory BuiltinProjectStorage.fromMap(Map<Object?, Object?> map) {
    int read(String key) {
      final value = map[key];
      if (value is! int || value < 0) {
        throw const BuiltinLinuxException(
          'Project storage could not be measured.',
          code: 'storage_unavailable',
        );
      }
      return value;
    }

    return BuiltinProjectStorage(
      runtimeBytes: read('runtimeBytes'),
      projectsBytes: read('projectsBytes'),
      measuredAtMilliseconds: read('measuredAtMilliseconds'),
    );
  }

  /// Ubuntu, tools, runtime settings/logs and any downloaded Ubuntu archive.
  final int runtimeBytes;

  /// Files beneath /root/projects, including hidden files and repositories.
  /// Symlinks are not followed; data elsewhere in Ubuntu is not preserved.
  final int projectsBytes;
  final int measuredAtMilliseconds;

  int get keepProjectsFreedBytes => runtimeBytes;
  int get deleteEverythingFreedBytes => runtimeBytes + projectsBytes;
}
