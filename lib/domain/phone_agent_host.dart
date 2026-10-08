import 'agent_catalog.dart';

const phoneAgentHostSecretPrefix = 'oc.agentHostSecret.';
const phoneAgentGatePrefix = 'oc.agentPhoneGate.';
const phoneAgentInstallPrefix = 'oc.agentInstall.';

enum AgentSetupPhase { idle, installing, done, interrupted, failed }

/// Observed payload removal, excluding retained accounts and shared tools.
final class AgentRemovalResult {
  const AgentRemovalResult({
    required this.agentId,
    required this.freedBytes,
    required this.alreadyAbsent,
  });
  final String agentId;
  final int freedBytes;
  final bool alreadyAbsent;
}

enum AgentHostFailure {
  unavailable,
  storage,
  install,
  interrupted,
  version,
  daemon,
  hello,
  wrongArchitecture,
  stale,
  busy,
}

final class AgentHostException implements Exception {
  const AgentHostException(this.reason);
  final AgentHostFailure reason;
  @override
  String toString() => 'AgentHostException(${reason.name})';
}

/// Safe setup progress. No shell output, provider messages or native log tail.
final class AgentSetupProgress {
  const AgentSetupProgress({
    required this.agentId,
    required this.phase,
    this.fraction,
    this.componentId,
    this.failure,
  });
  final String agentId;
  final AgentSetupPhase phase;
  final double? fraction;
  final String? componentId;

  /// Closed failure projection. Native/provider error text never reaches UI.
  final AgentHostFailure? failure;
}

enum AgentPhoneCheckStep { install, version, daemon, hello }

final class AgentPhoneCheckResult {
  AgentPhoneCheckResult({
    required this.agentId,
    required this.architecture,
    required this.passed,
    required List<AgentPhoneCheckStep> completed,
    this.failure,
  }) : completed = List.unmodifiable(completed);
  final String agentId;
  final AgentArchitecture? architecture;
  final bool passed;
  final List<AgentPhoneCheckStep> completed;
  final AgentHostFailure? failure;
  bool get arm64Qualified => passed && architecture == AgentArchitecture.arm64;
}

/// UI receives domain data; controller supplies the built-in implementation.
abstract interface class PhoneAgentHost {
  Stream<AgentSetupProgress> get setupChanges;
  AgentSetupProgress get setupProgress;
  Future<void> install(String agentId);
  Future<void> restoreInstall();
  Future<void> cancelInstall();
  Future<void> start();
  Future<void> stop();
  Future<AgentPhoneCheckResult> selfTest(String agentId);
}
