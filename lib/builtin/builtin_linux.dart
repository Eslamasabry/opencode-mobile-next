import 'package:flutter/services.dart';

import '../api/server_probe.dart' show ServerFlavor;
import '../diagnostics/perf_trace.dart';
import '../domain/phone_agent_context.dart';
import '../platform/platform_capabilities.dart';
import '../termux/bridge.dart' show TermuxBridge, TermuxRuntime;
import '../termux/opencode_ubuntu_setup.dart';

/// Where the built-in Ubuntu is in its life, as the Android side reports it.
enum BuiltinLinuxPhase {
  idle,
  installing,
  ready,
  failed;

  static BuiltinLinuxPhase parse(Object? value) => switch (value) {
    'installing' => installing,
    'ready' => ready,
    'failed' => failed,
    _ => idle,
  };
}

/// Native restoration after OS process reclamation; force-stop stays stopped.
enum BuiltinServerRestorePhase {
  idle,
  waiting,
  restoring,
  unavailable;

  static BuiltinServerRestorePhase parse(Object? value) => switch (value) {
    null || 'idle' => idle,
    'waiting' => waiting,
    'restoring' => restoring,
    _ => unavailable,
  };
}

/// Fixed reasons only; native process identity and errors stay private.
enum BuiltinServerRestoreReason {
  stopped,
  policyDisabled,
  budgetExhausted,
  ownershipUnknown,
  storageUnavailable,
  componentRecoveryRequired,
  systemTimeout;

  static BuiltinServerRestoreReason? parse(Object? value) => switch (value) {
    null => null,
    'stopped' => stopped,
    'policyDisabled' => policyDisabled,
    'budgetExhausted' => budgetExhausted,
    'storageUnavailable' => storageUnavailable,
    'componentRecoveryRequired' => componentRecoveryRequired,
    'systemTimeout' => systemTimeout,
    _ => ownershipUnknown,
  };
}

/// Selects a canonical native command. Contains no script or account data.
class BuiltinServerRestoreRecipe {
  const BuiltinServerRestoreRecipe({
    required this.profileId,
    required this.runtime,
  });

  final String profileId;
  final TermuxRuntime runtime;

  Map<String, Object> toMap() => {
    'version': 1,
    'profileId': profileId,
    'runtime': runtime.name,
  };
}

/// One reading of `status`.
class BuiltinLinuxStatus {
  const BuiltinLinuxStatus({
    required this.installed,
    required this.phase,
    this.message,
    this.serverRunning = false,
    this.serverRestartWanted = false,
    this.serverRecoveryGeneration,
    this.serverRecoveryAuthority = false,
    this.serverRecoveryScheduled = false,
    this.restorePhase = BuiltinServerRestorePhase.idle,
    this.restoreReason,
    this.serverUptime,
    this.serverPort,
    this.abi = '',
    this.bytesUsed,
    this.services = const [],
  });

  /// Nothing installed and nothing running: what a runner without the
  /// channel (desktop, tests) honestly has.
  const BuiltinLinuxStatus.absent()
    : installed = false,
      phase = BuiltinLinuxPhase.idle,
      message = null,
      serverRunning = false,
      serverRestartWanted = false,
      serverRecoveryGeneration = null,
      serverRecoveryAuthority = false,
      serverRecoveryScheduled = false,
      restorePhase = BuiltinServerRestorePhase.idle,
      restoreReason = null,
      serverUptime = null,
      serverPort = null,
      abi = '',
      bytesUsed = null,
      services = const [];

  factory BuiltinLinuxStatus.fromMap(Map<Object?, Object?> map) {
    int? asInt(Object? value) => value is num ? value.toInt() : null;
    final message = map['message'];
    return BuiltinLinuxStatus(
      installed: map['installed'] == true,
      phase: BuiltinLinuxPhase.parse(map['phase']),
      message: message is String && message.trim().isNotEmpty ? message : null,
      serverRunning: map['serverRunning'] == true,
      serverRestartWanted: map['serverRestartWanted'] == true,
      serverRecoveryGeneration: asInt(map['serverRecoveryGeneration']),
      serverRecoveryAuthority: map['serverRecoveryAuthority'] == true,
      serverRecoveryScheduled: map['serverRecoveryScheduled'] == true,
      restorePhase: BuiltinServerRestorePhase.parse(map['restorePhase']),
      restoreReason: BuiltinServerRestoreReason.parse(map['restoreReason']),
      serverUptime: switch (asInt(map['serverUptimeMs'])) {
        final int ms when ms >= 0 => Duration(milliseconds: ms),
        _ => null,
      },
      serverPort: asInt(map['serverPort']),
      abi: (map['abi'] ?? '').toString(),
      bytesUsed: asInt(map['bytesUsed']),
      services: [
        for (final name
            in map['services'] is List ? map['services'] as List : const [])
          if (name is String) name,
      ],
    );
  }

  final bool installed;
  final BuiltinLinuxPhase phase;
  final String? message;
  final bool serverRunning;

  /// Native intent survives a crash, but explicit Stop and service timeout
  /// clear it. An older APK without this authority never opts in.
  final bool serverRestartWanted;

  /// Admission token invalidated by pause, cancellation and manual actions.
  final int? serverRecoveryGeneration;

  /// Native is the sole durable budget writer once the profile is migrated.
  final bool serverRecoveryAuthority;
  final bool serverRecoveryScheduled;
  final BuiltinServerRestorePhase restorePhase;
  final BuiltinServerRestoreReason? restoreReason;

  /// How long the app's own tracked OpenCode process has run; null when none
  /// runs, or from an older APK that does not say.
  final Duration? serverUptime;
  final int? serverPort;
  final String abi;
  final int? bytesUsed;

  /// The long-running services that run now, by name: `server` (OpenCode)
  /// and, when it is on, AI Team ([BuiltinLinux.startService]).
  final List<String> services;

  bool serviceRunning(String name) => services.contains(name);
}

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
      uptimeMs: nonNegativeInt(map['uptimeMs']),
      restartCount: nonNegativeInt(map['restartCount']) ?? 0,
      exitReason: BuiltinServiceExitReason.parse(map['exitReason']),
    );
  }

  final bool running;
  final int? lastExitCode;

  /// Completed run duration. Null means no valid observation is available.
  final int? lastUptimeMs;

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
  const BuiltinLinuxException(this.message, {this.code});

  final String message;
  final String? code;

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

/// Ubuntu shipped inside the app, with no Termux (GitHub issue #87).
///
/// The Android side (BuiltinLinux.kt) owns the download, proot and the
/// long-running services (the OpenCode server, AI Team); this class only
/// speaks its method channel and writes the shell scripts that run inside
/// Ubuntu. Instances are cheap and hold no state, so a screen can take one
/// as a parameter and a test can pass a subclass or a mocked channel.
class BuiltinLinux {
  BuiltinLinux({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel(channelName);

  static const channelName =
      'io.github.eslamasabry.opencode_mobile/builtin_linux';

  /// Not Termux's 4096. [TermuxBridge.managesServerUrl] treats
  /// 127.0.0.1:4096 as the Termux server and would try Termux restarts and
  /// Termux wake locks on it; a separate port keeps the two apart, and both
  /// can even run at once.
  static const serverPort = 4097;
  static const serverUrl = 'http://127.0.0.1:$serverPort';
  static const serverUsername = 'opencode';

  /// The OpenCode server's name among the services (BuiltinLinux.kt SERVER).
  static const serverServiceName = 'server';

  /// The Ubuntu working directory the server starts in. Same rule as the
  /// Termux runner: never the home folder, which OpenCode would scan whole.
  static const projectsDir = '/root/projects';

  /// Where the start script reads the server password from, so the secret is
  /// not part of the long-running command line or the server log.
  static const passwordFile = '/root/.oc-builtin/server.password';

  /// The proot build ships for Android only; no other runner has the channel.
  static bool get supported => platformCapabilities.isAndroid;

  static bool managesServerUrl(String? value) {
    final uri = value == null ? null : Uri.tryParse(value);
    if (uri == null || uri.scheme != 'http') return false;
    final port = uri.hasPort ? uri.port : 80;
    return uri.host == '127.0.0.1' && port == serverPort;
  }

  final MethodChannel _channel;

  Future<BuiltinLinuxStatus> status() async {
    if (!supported) return const BuiltinLinuxStatus.absent();
    final raw = await _invoke<Map<Object?, Object?>>('status');
    return BuiltinLinuxStatus.fromMap(raw ?? const {});
  }

  /// Starts the download and unpack; returns at once. Poll [status].
  Future<void> installUbuntu() => _invoke<void>('installUbuntu');

  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    final raw = await _invoke<Map<Object?, Object?>>('run', {
      'script': script,
      'timeoutSeconds': timeout.inSeconds,
    });
    final exitCode = raw?['exitCode'];
    return BuiltinLinuxRunResult(
      exitCode: exitCode is num ? exitCode.toInt() : -1,
      output: (raw?['output'] ?? '').toString(),
    );
  }

  /// Setup checks in the fixed oc view. Never use for credentials or sign-in.
  Future<BuiltinLinuxRunResult> runAgentSetupCheck(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    final raw = await _invoke<Map<Object?, Object?>>('run', {
      'script': script,
      'timeoutSeconds': timeout.inSeconds,
      'agentUser': true,
    });
    return BuiltinLinuxRunResult(
      exitCode: raw?['exitCode'] is num
          ? (raw!['exitCode'] as num).toInt()
          : -1,
      output: (raw?['output'] ?? '').toString(),
    );
  }

  Future<void> startServer(
    String script, {
    int port = serverPort,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    final arguments = {
      'script': script,
      'port': port,
      if (restoreRecipe != null) 'restoreRecipe': restoreRecipe.toMap(),
    };
    try {
      await _invoke<void>('startServer', arguments);
    } on BuiltinLinuxException {
      if (restoreRecipe == null) rethrow;
      throw const BuiltinLinuxException(
        'The phone server could not start. Open setup and try again.',
        code: 'server_start_unavailable',
      );
    }
  }

  /// Restarts only a stopped server whose native intent and admission token
  /// still match, while the Android activity is resumed. Does not opt in.
  Future<void> restartServer(
    String script, {
    int port = serverPort,
    required int expectedGeneration,
  }) => _invokeRecovery('restartServer', {
    'script': script,
    'port': port,
    'expectedGeneration': expectedGeneration,
  });

  /// Releases ownership of an automatic attempt after the health probe.
  /// Repeated confirmation is allowed only for the same live native process
  /// and generation, so durable act recording can retry after confirmation.
  /// Before confirmation, lifecycle/policy cancellation stops only that attempt.
  Future<void> confirmServerRecovery({required int expectedGeneration}) =>
      _invokeRecovery('confirmServerRecovery', {
        'expectedGeneration': expectedGeneration,
      });

  /// Saves the legacy count in native storage without arming crash recovery.
  Future<Map<Object?, Object?>> stageServerRecovery(
    String profileId,
    Map<String, Object?> legacyBudget,
  ) => _recoveryMap('stageServerRecovery', {
    'profileId': profileId,
    'legacyBudget': legacyBudget,
  });

  Future<Map<Object?, Object?>> bindServerRecovery({
    required String profileId,
    required bool enabled,
    Map<String, Object?>? legacyBudget,
  }) => _recoveryMap('bindServerRecovery', {
    'profileId': profileId,
    'enabled': enabled,
    'legacyBudget': ?legacyBudget,
  });

  Future<List<Map<Object?, Object?>>> serverRecoveryReceipts(
    String profileId,
  ) async {
    try {
      final value = await _invoke<List<Object?>>('serverRecoveryReceipts', {
        'profileId': profileId,
      });
      if (value == null ||
          value.any((receipt) => receipt is! Map<Object?, Object?>)) {
        throw const BuiltinLinuxException(
          'The phone server could not restart.',
          code: 'recovery_unavailable',
        );
      }
      return value.cast<Map<Object?, Object?>>();
    } catch (_) {
      throw const BuiltinLinuxException(
        'The phone server could not restart.',
        code: 'recovery_unavailable',
      );
    }
  }

  Future<void> ackServerRecoveryReceipt(String profileId, String eventId) =>
      _invokeRecovery('ackServerRecoveryReceipt', {
        'profileId': profileId,
        'eventId': eventId,
      });

  Future<Map<Object?, Object?>> serverRecoveryBudget(String profileId) =>
      _recoveryMap('serverRecoveryBudget', {'profileId': profileId});

  Future<Map<Object?, Object?>> updateServerRecoveryReceipt(
    String profileId,
    Map<String, Object?> budget,
  ) => _recoveryMap('updateServerRecoveryReceipt', {
    'profileId': profileId,
    'budget': budget,
  });

  Future<Map<Object?, Object?>> confirmManualServerStart(String profileId) =>
      _recoveryMap('confirmManualServerStart', {'profileId': profileId});

  Future<void> unbindServerRecovery(String profileId, {bool delete = false}) =>
      _invokeRecovery(
        delete ? 'deleteServerRecovery' : 'unbindServerRecovery',
        {'profileId': profileId},
      );

  Future<Map<Object?, Object?>> _recoveryMap(
    String method,
    Map<String, Object?> arguments,
  ) async {
    try {
      final value = await _invoke<Map<Object?, Object?>>(method, arguments);
      if (value == null) {
        throw const BuiltinLinuxException(
          'The phone server could not restart.',
          code: 'recovery_unavailable',
        );
      }
      return value;
    } catch (_) {
      throw const BuiltinLinuxException(
        'The phone server could not restart.',
        code: 'recovery_unavailable',
      );
    }
  }

  /// Invalidates pending automatic work without changing the person's intent.
  Future<void> cancelServerRecovery() =>
      _invokeRecovery('cancelServerRecovery');

  Future<void> _invokeRecovery(String method, [Object? arguments]) async {
    try {
      await _invoke<void>(method, arguments);
    } on BuiltinLinuxException {
      throw const BuiltinLinuxException(
        'The phone server could not restart.',
        code: 'recovery_unavailable',
      );
    }
  }

  Future<void> stopServer() => _invoke<void>('stopServer');

  /// Setup alone may restore this stop if its following engine activation
  /// fails. The existing deliberate-stop method never opts into rollback.
  Future<void> stopServerForPhoneEngineSetup() =>
      _invoke<void>('stopServer', {'phoneEngineSetup': true});

  Future<BuiltinPhoneEngineStatus> startPhoneEngine({
    required String profileId,
    int port = 4098,
    String? notice,
  }) async => BuiltinPhoneEngineStatus.fromMap(
    await _invoke<Map<Object?, Object?>>('startPhoneEngine', {
          'profileId': profileId,
          'port': port,
          'notice': ?notice,
        }) ??
        const {},
  );

  Future<BuiltinPhoneEngineStatus> phoneEngineStatus(String profileId) async =>
      BuiltinPhoneEngineStatus.fromMap(
        await _invoke<Map<Object?, Object?>>('phoneEngineStatus', {
              'profileId': profileId,
            }) ??
            const {},
      );

  Future<BuiltinPhoneEngineCredentials> phoneEngineCredentials(
    String profileId,
  ) async {
    final raw = await _invoke<Map<Object?, Object?>>('phoneEngineCredentials', {
      'profileId': profileId,
    });
    final baseUrl = raw?['baseUrl'];
    final bearerToken = raw?['bearerToken'];
    final uri = baseUrl is String ? Uri.tryParse(baseUrl) : null;
    if (uri == null ||
        uri.scheme != 'http' ||
        uri.host != '127.0.0.1' ||
        uri.userInfo.isNotEmpty ||
        uri.path.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        !uri.hasPort ||
        uri.port < 1024 ||
        bearerToken is! String ||
        !RegExp(r'^[a-f0-9]{64}$').hasMatch(bearerToken)) {
      throw const BuiltinLinuxException(
        'The phone engine is unavailable.',
        code: 'engine_auth_unavailable',
      );
    }
    return BuiltinPhoneEngineCredentials(
      baseUrl: baseUrl as String,
      bearerToken: bearerToken,
    );
  }

  Future<BuiltinPhoneEngineStatus> stopPhoneEngine(String profileId) async =>
      BuiltinPhoneEngineStatus.fromMap(
        await _invoke<Map<Object?, Object?>>('stopPhoneEngine', {
              'profileId': profileId,
            }) ??
            const {},
      );

  Future<void> deletePhoneEngine(String profileId) =>
      _invoke<void>('deletePhoneEngine', {'profileId': profileId});

  /// Runs only isolated proof fixtures. A complete result does not enable work.
  Future<Map<Object?, Object?>> runPhoneEngineBoundaryProbe() async =>
      await _invoke<Map<Object?, Object?>>('runPhoneEngineBoundaryProbe') ??
      const {};

  /// Explicit restart path. An existing chat server is never stopped here.
  /// A successful launch is still not a completed device boundary proof.
  Future<void> startProtectedPhoneServer({
    required String profileId,
    required String script,
    int port = serverPort,
  }) => _invoke<void>('startProtectedPhoneServer', {
    'profileId': profileId,
    'script': script,
    'port': port,
  });

  /// Keeps the phone awake while a reply runs on the in-app server ([on]),
  /// for at most [hold] (Android caps it at 15 minutes); the caller renews
  /// it while the reply lasts and turns it off when it ends. Held only while
  /// the server runs. Returns whether it is held.
  Future<bool> holdAwakeForWork(
    bool on, {
    Duration hold = const Duration(minutes: 10),
  }) async =>
      await _invoke<bool>('holdAwakeForWork', {
        'on': on,
        'forMs': hold.inMilliseconds,
      }) ??
      false;

  /// Acquire or renew one opaque chat lease; off releases only [leaseId].
  /// Native lifetime limits remain authoritative. A capped logical lease cannot
  /// be renewed until it is explicitly closed; never release/reacquire to renew.
  /// Setup, sign-in and terminal leases are owned independently in native code.
  Future<BuiltinWorkLeaseStatus> setChatWorkLease({
    required String leaseId,
    required bool on,
    Duration hold = const Duration(minutes: 10),
  }) async {
    if (!supported) return const BuiltinWorkLeaseStatus();
    if (RegExp(r'^[A-Za-z0-9_.-]{1,80}$').firstMatch(leaseId)?.end !=
        leaseId.length) {
      throw const BuiltinLinuxException(
        'This chat could not keep the phone awake. Try again.',
        code: 'work_lease_invalid',
      );
    }
    final raw = await _invoke<Object?>('setChatWorkLease', {
      'leaseId': leaseId,
      'on': on,
      'forMs': hold.inMilliseconds,
    });
    return raw is Map<Object?, Object?>
        ? BuiltinWorkLeaseStatus.fromMap(raw)
        : const BuiltinWorkLeaseStatus();
  }

  /// How proot runs the server now and whether the phone is kept awake.
  Future<BuiltinPerformance> performance() async {
    if (!supported) return const BuiltinPerformance();
    final raw = await _invoke<Map<Object?, Object?>>('performance');
    return BuiltinPerformance.fromMap(raw ?? const {});
  }

  Future<String> serverLog({int tailBytes = 32768}) async =>
      await _invoke<String>('serverLog', {'tailBytes': tailBytes}) ?? '';

  /// Starts [script] as the long-running service [name] in its own proot,
  /// owned by the app like the OpenCode server (which is the service
  /// `server`). A service that runs already is restarted. [notice] is what
  /// the ongoing notification says while it runs.
  ///
  /// Never start a long-running program from [run] instead: its proot ends
  /// with the script and takes everything the script started with it.
  Future<void> startService(
    String name,
    String script, {
    int? port,
    String? notice,
  }) => _invoke<void>('startService', {
    'name': name,
    'script': script,
    'port': ?port,
    'notice': ?notice,
  });

  Future<void> stopService(String name) =>
      _invoke<void>('stopService', {'name': name});

  /// The end of the service [name]'s log, for a failure's details.
  Future<String> serviceLog(String name, {int tailBytes = 16384}) async =>
      await _invoke<String>('serviceLog', {
        'name': name,
        'tailBytes': tailBytes,
      }) ??
      '';

  /// Stops the runtime and removes Ubuntu/tools, preserving /root/projects.
  /// Existing callers get the safe default. Use [remove] for explicit deletion.
  Future<void> uninstall() => remove();

  /// Literal app name the destructive confirmation asks the person to type.
  static const deletionConfirmationName = 'OpenCode';

  /// Measures both removal choices afresh. Throws on unknown/inaccessible
  /// storage rather than presenting zero bytes. No paths or contents returned.
  Future<BuiltinProjectStorage> projectStorage() async {
    final raw = await _invokeProjectStorage<Object?>('projectStorage');
    if (raw is! Map<Object?, Object?>) {
      throw const BuiltinLinuxException(
        'Project storage could not be measured.',
        code: 'storage_unavailable',
      );
    }
    return BuiltinProjectStorage.fromMap(raw);
  }

  /// Remove the runtime, keeping projects by default, including on reinstall.
  /// Only the explicit destructive choice with the exact typed app name can
  /// remove projects. Native code repeats the check and rejects active setup.
  /// Migration/deletion failure throws: refresh storage, never assume success.
  Future<void> remove({
    bool alsoDeleteProjects = false,
    String? confirmationName,
  }) async {
    if (alsoDeleteProjects && confirmationName != deletionConfirmationName) {
      throw const BuiltinLinuxException(
        'Type the app name to delete projects.',
        code: 'confirmation_required',
      );
    }
    // A distinct method fails closed on an older APK whose uninstall ignores
    // preservation arguments. Never fall back to that destructive method.
    await _invokeProjectStorage<void>('removeRuntime', {
      'alsoDeleteProjects': alsoDeleteProjects,
      if (alsoDeleteProjects) 'confirmationName': deletionConfirmationName,
    });
  }

  // Keep native filesystem paths/errors and user-entered text out of messages
  // and diagnostics. This flow persists no Dart state or diagnostic payload.
  Future<T?> _invokeProjectStorage<T>(String method, [Object? args]) async {
    try {
      return await _invoke<T>(method, args);
    } on BuiltinLinuxException catch (error) {
      final code = switch (error.code) {
        'unsupported_platform' || 'missing_plugin' => error.code,
        _ =>
          method == 'projectStorage' ? 'storage_unavailable' : 'removal_failed',
      };
      throw BuiltinLinuxException(
        method == 'projectStorage'
            ? 'Project storage could not be measured.'
            : 'The runtime could not be removed.',
        code: code,
      );
    }
  }

  /// Starts a phone setup job in native code (SetupRunner.kt) and returns at
  /// once; [setupStatus] follows it. The shapes are the engine's
  /// (lib/builtin/setup/setup_engine.dart).
  Future<void> startSetup({
    required String jobId,
    required List<Map<String, Object?>> components,
    required Map<String, Map<String, String>> params,
    required Map<String, String> texts,
  }) => _invoke<void>('startSetup', {
    'jobId': jobId,
    'components': components,
    'params': params,
    'texts': texts,
  });

  /// The setup job as `files/linux/setup.json` holds it (the live one while
  /// it runs), or null when there has never been one.
  Future<String?> setupStatus() => _invoke<String>('setupStatus');

  /// Stops the running setup job; what finished stays installed.
  Future<void> cancelSetup() => _invoke<void>('cancelSetup');

  /// Reports a step the app runs itself (starting OpenCode) as finished.
  Future<void> completeSetupStep({
    required String jobId,
    required String id,
    required bool ok,
    String? error,
    String? version,
  }) => _invoke<void>('completeSetupStep', {
    'jobId': jobId,
    'id': id,
    'ok': ok,
    'error': error,
    'version': version,
  });

  /// Opens the device's Storage settings (not this app's own App Info), so
  /// low free space found during phone setup pre-flight (P0.8) can actually
  /// be freed. Android's `ACTION_INTERNAL_STORAGE_SETTINGS` first, App Info
  /// on a ROM that hides it; returns whether either opened.
  Future<bool> openStorageSettings() async =>
      await _invoke<bool>('openStorageSettings') ?? false;

  // Every call into Android is timed as `linux.<method>`; a `run` also
  // carries a label from its script (see [scriptLabel]). The status reads
  // that setup polls twice a second reach the device log only when slow.
  Future<T?> _invoke<T>(String method, [Object? arguments]) {
    final script = arguments is Map ? arguments['script'] : null;
    return PerfTrace.span(
      'linux.$method',
      () => _invokeUntraced<T>(method, arguments),
      attrs: {
        if (method == 'run' && script is String) 'label': scriptLabel(script),
      },
      logMinMs:
          const {
            'setupStatus',
            'status',
            'holdAwakeForWork',
            'setChatWorkLease',
            'performance',
          }.contains(method)
          ? 50
          : 0,
    );
  }

  /// A short, shareable name for [script]: its first line that does real
  /// work, with quoted text blanked. The password script is only ever
  /// "write-password" — its body holds the password.
  static String scriptLabel(String script) {
    if (script.contains('$passwordFile.tmp')) return 'write-password';
    for (final raw in script.split('\n')) {
      final line = raw.trim();
      if (line.isEmpty ||
          line.startsWith('#') ||
          line.startsWith('set ') ||
          line.startsWith('umask ') ||
          line.startsWith('export ')) {
        continue;
      }
      final blanked = line
          .replaceAll(RegExp(r"'[^']*'"), "'…'")
          .replaceAll(RegExp(r'"[^"]*"'), '"…"');
      return blanked.length > 60 ? '${blanked.substring(0, 59)}…' : blanked;
    }
    return 'script';
  }

  Future<T?> _invokeUntraced<T>(String method, [Object? arguments]) async {
    if (!supported) {
      throw const BuiltinLinuxException(
        'The built-in Linux runs on Android only.',
        code: 'unsupported_platform',
      );
    }
    try {
      return await _channel.invokeMethod<T>(method, arguments);
    } on PlatformException catch (error) {
      throw BuiltinLinuxException(
        error.message?.trim().isNotEmpty == true
            ? error.message!
            : 'The built-in Linux failed (${error.code}).',
        code: error.code,
      );
    } on MissingPluginException {
      throw const BuiltinLinuxException(
        'This build of the app has no built-in Linux.',
        code: 'missing_plugin',
      );
    }
  }

  static String _quote(String value) => "'${value.replaceAll("'", "'\"'\"'")}'";

  /// The runtime a saved in-app profile was set up with.
  static TermuxRuntime runtimeFor(ServerFlavor flavor) =>
      flavor == ServerFlavor.v2
      ? TermuxRuntime.openCode2
      : TermuxRuntime.openCode1;

  static final _versionPattern = RegExp(r'^[A-Za-z0-9._+-]+$');

  /// Installs OpenCode inside Ubuntu with the very script the Termux manager
  /// runs ([openCodeUbuntuSetupScript]), at the same pinned version unless a
  /// caller asks for another on purpose.
  ///
  /// OpenCode 1 then refreshes its model catalog, as the Termux setup does.
  /// Unlike Termux a failed refresh does not fail the install: the server
  /// fetches the catalog itself later, and a flaky network at this moment
  /// should not throw away a finished install.
  static String installOpenCodeScript({
    TermuxRuntime runtime = TermuxRuntime.openCode1,
    String? version,
  }) {
    final selected = version ?? runtime.pinnedVersion;
    if (!_versionPattern.hasMatch(selected)) {
      throw ArgumentError.value(version, 'version', 'Invalid package version.');
    }
    final refresh = runtime == TermuxRuntime.openCode1
        ? "opencode models --refresh >/dev/null 2>&1 || "
              "echo '[oc] The model list could not be refreshed now; "
              "OpenCode will fetch it later.'\n"
        : '';
    // Interpolated verbatim: the shared text is a raw string, so nothing in
    // it is re-read as Dart here.
    return 'set -eu\n'
        '$_fastPrerequisites'
        'export OC_REQUESTED_VERSION=${_quote(selected)}\n'
        'export OC_RUNTIME=${_quote(runtime.wireName)}\n'
        "bash -s <<'OC_PROOT_SETUP'\n"
        '${openCodeUbuntuSetupScript}OC_PROOT_SETUP\n'
        '$refresh';
  }

  /// Node from its official pinned download instead of Ubuntu's `npm`
  /// package, and only the few small packages OpenCode needs. Ubuntu's npm
  /// drags in hundreds of packages, which under proot took ten minutes on
  /// the emulator; the shared setup then finds everything in place and skips
  /// apt entirely. npm's global folder is /usr/local, so `opencode` lands on
  /// the PATH the server starts with.
  static final _fastPrerequisites =
      '''export DEBIAN_FRONTEND=noninteractive
if ! command -v curl >/dev/null 2>&1 || ! command -v git >/dev/null 2>&1 ||
   ! command -v ssh >/dev/null 2>&1 || [ ! -s /etc/ssl/certs/ca-certificates.crt ]; then
  echo '[oc] Installing Git, SSH and certificates'
  apt-get update -y -o Acquire::Retries=5 >/dev/null
  apt-get install -y --no-install-recommends -o Acquire::Retries=5 \\
    curl ca-certificates git openssh-client >/dev/null
fi
if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  case "\$(uname -m)" in
    aarch64|arm64) node_arch=arm64; node_sha=${TermuxBridge.localAgentsPins['node_sha256_arm64']} ;;
    x86_64|amd64) node_arch=x64; node_sha=${TermuxBridge.localAgentsPins['node_sha256_x64']} ;;
    *) echo "[oc] Unsupported CPU: \$(uname -m)" >&2; exit 64 ;;
  esac
  node_version=${TermuxBridge.localAgentsPins['node_version']}
  echo "[oc] Installing Node.js \$node_version"
  curl -fsSL --retry 5 -o /tmp/node.tar.gz \\
    "${TermuxBridge.localAgentsPins['node_base_url']}/\$node_version/node-\$node_version-linux-\$node_arch.tar.gz"
  echo "\$node_sha  /tmp/node.tar.gz" | sha256sum -c --quiet -
  rm -rf /opt/node && mkdir -p /opt/node
  tar -xzf /tmp/node.tar.gz -C /opt/node --strip-components=1
  rm -f /tmp/node.tar.gz
  for tool in node npm npx; do ln -sf /opt/node/bin/\$tool /usr/local/bin/\$tool; done
  npm config set prefix /usr/local
fi
''';

  /// Prints the installed version of [runtime], or fails when it is missing.
  static String versionScript(TermuxRuntime runtime) =>
      runtime == TermuxRuntime.openCode2
      ? 'command -v opencode2 >/dev/null 2>&1 && opencode2 --version'
      : 'command -v opencode >/dev/null 2>&1 && opencode --version';

  /// The version out of `opencode --version` / `opencode2 --version`, which
  /// the Termux manager trims the same way (`opencode2 v2.0.10` → `2.0.10`).
  static String? parseVersion(String output) {
    for (final raw in output.split('\n').reversed) {
      var line = raw.trim();
      if (line.isEmpty) continue;
      for (final prefix in const ['opencode2 v', 'opencode v']) {
        if (line.startsWith(prefix)) line = line.substring(prefix.length);
      }
      return _versionPattern.hasMatch(line) ? line : null;
    }
    return null;
  }

  /// Lists the project folders under [projectsDir], one name per line.
  /// Creates the parent first so a fresh install lists nothing instead of
  /// failing.
  static String listProjectsScript() =>
      'set -eu\n'
      'mkdir -p $projectsDir\n'
      "find $projectsDir -mindepth 1 -maxdepth 1 -type d -printf '%f\\n' "
      '| sort\n';

  /// The folder names out of [listProjectsScript]. Hidden folders are left
  /// out: they are tool state, not projects.
  static List<String> parseProjectList(String output) => [
    for (final line in output.split('\n'))
      if (line.trim().isNotEmpty && !line.trim().startsWith('.')) line.trim(),
  ];

  /// Succeeds when [path] is a folder inside Ubuntu. Lets the app check a
  /// typed path without asking OpenCode about it: OpenCode caches a folder
  /// it was asked about before it existed as broken.
  static String folderExistsScript(String path) => 'test -d ${_quote(path)}';

  /// Makes [path] a new git project, or leaves an existing folder alone.
  /// Prints `created <path>` or `exists <path>`, so the app knows whether
  /// anything could already be running there.
  static String createFolderScript(String path) {
    if (!path.startsWith('/') || path.contains('\n') || path.contains('\x00')) {
      throw ArgumentError.value(path, 'path', 'Must be an absolute path.');
    }
    return 'set -eu\n'
        'dir=${_quote(path)}\n'
        'if [ -d "\$dir" ]; then printf \'exists %s\\n\' "\$dir"; exit 0; fi\n'
        'mkdir -p "\$dir"\n'
        'git init -q "\$dir"\n'
        'printf \'created %s\\n\' "\$dir"\n';
  }

  /// Saves the server password inside Ubuntu, readable by its root only.
  static String writePasswordScript(String password) {
    if (password.isEmpty) {
      throw ArgumentError.value(password, 'password', 'Must not be empty.');
    }
    final dir = passwordFile.substring(0, passwordFile.lastIndexOf('/'));
    return 'set -eu\n'
        'umask 077\n'
        'mkdir -p $dir\n'
        "printf '%s' ${_quote(password)} > $passwordFile.tmp\n"
        'mv $passwordFile.tmp $passwordFile\n';
  }

  /// The long-running server, mirroring the Termux runner: started in
  /// [projectsDir], bound to 127.0.0.1 only (nothing on this phone's network
  /// or the internet can reach it), Basic auth `opencode` + the password.
  ///
  /// OpenCode 2 gets the Termux runner's isolated XDG folders, so its data and
  /// config never mix with OpenCode 1 installed in the same Ubuntu.
  static String serverScript({
    TermuxRuntime runtime = TermuxRuntime.openCode1,
    int port = serverPort,
  }) {
    if (port < 1024 || port > 65535) {
      throw ArgumentError.value(
        port,
        'port',
        'Must be between 1024 and 65535.',
      );
    }
    final opencode2 = runtime == TermuxRuntime.openCode2;
    final binary = opencode2 ? 'opencode2' : 'opencode';
    final isolated = opencode2
        ? 'mkdir -p /root/.oc-opencode2/data/opencode /root/.oc-opencode2/cache '
              '/root/.oc-opencode2/state /root/.oc-opencode2/config/opencode\n'
              'unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT\n'
              'export XDG_DATA_HOME=/root/.oc-opencode2/data\n'
              'export XDG_CACHE_HOME=/root/.oc-opencode2/cache\n'
              'export XDG_STATE_HOME=/root/.oc-opencode2/state\n'
              'export XDG_CONFIG_HOME=/root/.oc-opencode2/config\n'
              'export OPENCODE_CONFIG_DIR=/root/.oc-opencode2/config/opencode\n'
              'export OPENCODE_DB=/root/.oc-opencode2/data/opencode/opencode.db\n'
        : '';
    return 'set -eu\n'
        'mkdir -p $projectsDir\n'
        'cd $projectsDir\n'
        '[ -s $passwordFile ] || { echo "[oc] The server password is missing" >&2; exit 78; }\n'
        '$isolated'
        // Tell the agent where it runs (lib/domain/phone_agent_context.dart);
        // never blocks the start.
        '(\n${PhoneAgentContext.builtinScript}) >/dev/null 2>&1 || true\n'
        'password=\$(cat $passwordFile)\n'
        'export OPENCODE_SERVER_USERNAME=$serverUsername\n'
        'export OPENCODE_SERVER_PASSWORD="\$password"\n'
        'export OPENCODE_PASSWORD="\$password"\n'
        'unset password\n'
        'exec $binary serve --hostname 127.0.0.1 --port $port\n';
  }
}
