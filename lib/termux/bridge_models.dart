import 'scripts/script_support.dart' show termuxManagedServerPort;

/// The runtime selected for the one app-managed Ubuntu server. It is separate
/// from a server's reported version and survives restarts in the manager state.
enum TermuxRuntime {
  openCode1('opencode1', '1.18.32'),
  openCode2('opencode2', '2.0.10');

  const TermuxRuntime(this.wireName, this.pinnedVersion);
  final String wireName;
  final String pinnedVersion;

  static TermuxRuntime parse(String? value) => switch (value?.trim()) {
    null || '' || 'opencode1' => openCode1,
    'opencode2' => openCode2,
    _ => throw const TermuxBridgeException(
      'The managed server has an unsupported runtime selection.',
      code: 'invalid_runtime',
    ),
  };
}

/// The installed app-owned environment, independent of server running state.
class TermuxStorageSnapshot {
  const TermuxStorageSnapshot({
    required this.totalBytes,
    required this.availableBytes,
  });
  final int totalBytes;
  final int availableBytes;

  factory TermuxStorageSnapshot.parse(String output) {
    final values = <String, int>{};
    for (final line in output.trim().split('\n')) {
      final match = RegExp(
        r'^(total_kib|available_kib)=([0-9]{1,13})$',
      ).firstMatch(line);
      if (match == null || values.containsKey(match[1])) {
        throw const TermuxBridgeException(
          'Could not read Termux storage.',
          code: 'invalid_storage',
        );
      }
      values[match[1]!] = int.parse(match[2]!);
    }
    final total = values['total_kib'];
    final available = values['available_kib'];
    if (total == null || available == null || total <= 0 || available > total) {
      throw const TermuxBridgeException(
        'Could not read Termux storage.',
        code: 'invalid_storage',
      );
    }
    return TermuxStorageSnapshot(
      totalBytes: total * 1024,
      availableBytes: available * 1024,
    );
  }
}

class TermuxInstallation {
  final bool ubuntuInstalled;
  final String? openCodeVersion;
  final TermuxRuntime runtime;
  final bool runtimeSelected;

  const TermuxInstallation({
    required this.ubuntuInstalled,
    this.openCodeVersion,
    this.runtime = TermuxRuntime.openCode1,
    this.runtimeSelected = false,
  });

  factory TermuxInstallation.parse(String output) {
    final match = RegExp(
      r'^ubuntu=(absent|installed)\r?\nversion=([^\r\n]*)(?:\r?\nruntime=(opencode1|opencode2))?\r?\n?$',
    ).firstMatch(output.trim());
    if (match == null) {
      throw const TermuxBridgeException(
        'Could not read the installed Ubuntu and OpenCode versions.',
        code: 'invalid_installation_probe',
      );
    }
    final installed = match[1] == 'installed';
    final version = match[2]!;
    if (version.isNotEmpty &&
        (!installed ||
            !RegExp(
              r'^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$',
            ).hasMatch(version))) {
      throw const TermuxBridgeException(
        'OpenCode returned an unexpected version response.',
        code: 'invalid_installation_probe',
      );
    }
    return TermuxInstallation(
      ubuntuInstalled: installed,
      openCodeVersion: version.isEmpty ? null : version,
      runtime: TermuxRuntime.parse(match[3]),
      runtimeSelected: match[3] != null,
    );
  }
}

class TermuxCapabilities {
  final bool installed;
  final String? version;
  final bool serviceAvailable;
  final bool protocolSupported;
  final bool permissionGranted;

  const TermuxCapabilities({
    required this.installed,
    required this.version,
    required this.serviceAvailable,
    required this.protocolSupported,
    required this.permissionGranted,
    this.platformSupported = true,
  });

  /// What a platform without a Termux bridge reports: nothing is installed,
  /// nothing is granted, and — unlike an Android phone that simply has not
  /// installed Termux yet — [platformSupported] says installing it would not
  /// help. Callers use that to choose between "install Termux" and "this is
  /// not a thing here".
  const TermuxCapabilities.unavailable()
    : installed = false,
      version = null,
      serviceAvailable = false,
      protocolSupported = false,
      permissionGranted = false,
      platformSupported = false;

  /// False when the running platform has no Termux bridge at all.
  final bool platformSupported;

  factory TermuxCapabilities.fromMap(Map<String, dynamic> map) =>
      TermuxCapabilities(
        installed: map['installed'] == true,
        version: map['version']?.toString(),
        serviceAvailable: map['serviceAvailable'] == true,
        protocolSupported: map['protocolSupported'] == true,
        permissionGranted: map['permissionGranted'] == true,
      );
}

class TermuxCommandResult {
  final String stdout;
  final String stderr;
  final int exitCode;
  final int errorCode;
  final String errorMessage;

  const TermuxCommandResult({
    required this.stdout,
    required this.stderr,
    required this.exitCode,
    required this.errorCode,
    required this.errorMessage,
  });

  bool get successful => errorCode == -1 && exitCode == 0;

  /// Termux's own wording when it SIGKILLs an execution because its service
  /// is stopping (or the user cancelled it from the notification).
  bool get killedByServiceShutdown =>
      !successful &&
      errorMessage.contains('android is killing the execution service');

  String get failureMessage {
    final details = [
      errorMessage.trim(),
      stderr.trim(),
    ].where((part) => part.isNotEmpty).join('\n');
    return details.isEmpty
        ? 'Termux command failed (error $errorCode, exit $exitCode).'
        : details;
  }

  factory TermuxCommandResult.fromMap(Map<String, dynamic> map) =>
      TermuxCommandResult(
        stdout: map['stdout']?.toString() ?? '',
        stderr: map['stderr']?.toString() ?? '',
        exitCode: (map['exitCode'] as num?)?.toInt() ?? -1,
        errorCode: (map['err'] as num?)?.toInt() ?? -1,
        errorMessage: map['errorMessage']?.toString() ?? '',
      );
}

class TermuxSetupStatus {
  final String phase;
  final String message;
  final int port;
  final String runner;
  final String version;
  final int? pid;
  final int? startedAtEpochSeconds;
  final String operationID;
  final String operationResult;
  final String failureKind;
  final TermuxRuntime runtime;
  final bool runtimeSelected;
  final TermuxRuntime? switchPrevious;
  final TermuxRuntime? switchTarget;
  final String switchPhase;
  final bool switchReturnAvailable;
  bool get switchPending => switchPrevious != null && switchTarget != null;

  const TermuxSetupStatus({
    required this.phase,
    required this.message,
    required this.port,
    required this.runner,
    required this.version,
    required this.pid,
    this.startedAtEpochSeconds,
    this.operationID = '',
    this.operationResult = '',
    this.failureKind = '',
    this.runtime = TermuxRuntime.openCode1,
    this.runtimeSelected = false,
    this.switchPrevious,
    this.switchTarget,
    this.switchPhase = '',
    this.switchReturnAvailable = false,
  });

  bool get isRunning => const {
    'queued',
    'preparing',
    'installing_dependencies',
    'installing_ubuntu',
    'installing_opencode',
    'refreshing_models',
    'restarting',
    'starting_server',
  }.contains(phase);
  bool get isReady => phase == 'ready';
  bool get isFailed => phase == 'failed';
  bool get canRecover =>
      isFailed &&
      !switchPending &&
      runner == 'proot' &&
      port == termuxManagedServerPort &&
      (failureKind == 'crash' || failureKind == 'recovery');

  factory TermuxSetupStatus.parse(String output) {
    final values = <String, String>{};
    for (final line in output.split('\n')) {
      final separator = line.indexOf('=');
      if (separator <= 0) continue;
      values[line.substring(0, separator)] = line.substring(separator + 1);
    }
    final rawStartedAt = values['started_at'] ?? '';
    final startedAt = RegExp(r'^[0-9]+$').hasMatch(rawStartedAt)
        ? int.tryParse(rawStartedAt)
        : null;
    return TermuxSetupStatus(
      phase: values['phase'] ?? 'unknown',
      message: values['message'] ?? 'Unknown setup state',
      port: int.tryParse(values['port'] ?? '') ?? 4096,
      runner: values['runner'] ?? '',
      version: values['version'] ?? '',
      pid: int.tryParse(values['pid'] ?? ''),
      startedAtEpochSeconds: startedAt != null && startedAt >= 0
          ? startedAt
          : null,
      operationID: values['operation'] ?? '',
      operationResult: values['operation_result'] ?? '',
      failureKind: values['failure_kind'] ?? '',
      runtime: TermuxRuntime.parse(values['runtime']),
      runtimeSelected: values['runtime']?.trim().isNotEmpty == true,
      switchPrevious: values['switch_previous'] == null
          ? null
          : TermuxRuntime.parse(values['switch_previous']),
      switchTarget: values['switch_target'] == null
          ? null
          : TermuxRuntime.parse(values['switch_target']),
      switchPhase: values['switch_phase'] ?? '',
      switchReturnAvailable: values['switch_return'] == 'opencode1',
    );
  }
}

class TermuxSetupSnapshot {
  static const _marker = '__OC_SETUP_OUTPUT__';

  final TermuxSetupStatus status;
  final String output;

  const TermuxSetupSnapshot({required this.status, required this.output});

  factory TermuxSetupSnapshot.parse(String raw) {
    final markerIndex = raw.indexOf(_marker);
    if (markerIndex < 0) {
      return TermuxSetupSnapshot(
        status: TermuxSetupStatus.parse(raw),
        output: '',
      );
    }
    return TermuxSetupSnapshot(
      status: TermuxSetupStatus.parse(raw.substring(0, markerIndex)),
      output: raw.substring(markerIndex + _marker.length).trim(),
    );
  }
}

class TermuxBridgeException implements Exception {
  final String message;
  final String code;

  const TermuxBridgeException(this.message, {this.code = 'termux_error'});

  @override
  String toString() => message;
}
