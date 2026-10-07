import '../builtin/agents/phone_agents_host.dart';
import '../domain/agent_catalog.dart';
import '../domain/agent_sign_in.dart';
import '../domain/agent_auth_probe.dart';
import '../domain/phone_agent_host.dart';
import '../domain/phone_agents.dart';
import '../paseo/gateway.dart';

/// What the controller needs from one profile's phone agent host. The
/// production adapter wraps [BuiltinPhoneAgents]; tests supply a fake.
abstract interface class PhoneAgentHostPort implements PhoneAgentHost {
  /// The phone's CPU architecture, or null when unknown/unsupported.
  Future<AgentArchitecture?> architecture();

  /// Sanitized installation/host/sign-in facts for one catalog agent.
  Future<PhoneAgentRuntime> inspect(
    String agentId, {
    AgentSignInState? signIn,
    AgentCapabilities capabilities,
  });

  /// A new gateway to the loopback host, scoped to [directory]. The caller
  /// owns it and closes it.
  Future<PaseoGateway> openGateway(String directory);

  /// Like [openGateway] but synchronous and lazy: it connects on first use.
  /// Valid after one successful [openGateway]. The caller owns it.
  PaseoGateway newGatewaySync(String directory);

  Future<void> dispose();
}

/// Production adapter; adds nothing but the shared interface.
abstract interface class PhoneAgentAuthPort {
  Future<AgentAuthProbeResult> probeSignIn(String agentId);
  bool supportsSignOut(String agentId);
  Future<AgentAuthProbeResult> signOut(String agentId);
}

abstract interface class PhoneAgentLivenessPort {
  Future<bool?> helperRunning();
}

final class BuiltinPhoneAgentHostPort
    implements PhoneAgentHostPort, PhoneAgentAuthPort, PhoneAgentLivenessPort {
  BuiltinPhoneAgentHostPort(this._host);
  final BuiltinPhoneAgents _host;
  @override
  Future<bool?> helperRunning() => _host.helperRunning();

  @override
  Future<AgentAuthProbeResult> probeSignIn(String agentId) =>
      _host.probeSignIn(agentId);
  @override
  bool supportsSignOut(String agentId) => _host.supportsSignOut(agentId);
  @override
  Future<AgentAuthProbeResult> signOut(String agentId) =>
      _host.signOut(agentId);

  @override
  Stream<AgentSetupProgress> get setupChanges => _host.setupChanges;
  @override
  AgentSetupProgress get setupProgress => _host.setupProgress;
  @override
  Future<void> install(String agentId) => _host.install(agentId);
  @override
  Future<void> restoreInstall() => _host.restoreInstall();
  @override
  Future<void> cancelInstall() => _host.cancelInstall();
  @override
  Future<void> start() => _host.start();
  @override
  Future<void> stop() => _host.stop();
  @override
  Future<AgentPhoneCheckResult> selfTest(String agentId) =>
      _host.selfTest(agentId);
  @override
  Future<AgentArchitecture?> architecture() => _host.architecture();
  @override
  Future<PhoneAgentRuntime> inspect(
    String agentId, {
    AgentSignInState? signIn,
    AgentCapabilities capabilities = const AgentCapabilities(),
  }) => _host.inspect(agentId, signIn: signIn, capabilities: capabilities);
  @override
  Future<PaseoGateway> openGateway(String directory) =>
      _host.openGateway(directory);
  @override
  PaseoGateway newGatewaySync(String directory) =>
      _host.newGatewaySync(directory);
  @override
  Future<void> dispose() => _host.dispose();
}
