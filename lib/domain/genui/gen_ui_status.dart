import '../agent_tools/agent_tool_adapter.dart';

export '../agent_tools/agent_tool_adapter.dart';

/// Ready agents, derived from evidence rather than an agent-supplied label.
/// One [AgentToolAdapter] per agent; the former enum's names still resolve.
typedef GenUiAgent = AgentToolAdapter;

/// Stable localization keys. Never holds command, config or exception text.
enum GenUiSetupProblem {
  unsupportedHost,
  runtimeMissing,
  notQualified,
  permissionDenied,
  conflict,
  installationFailed,
  registrationFailed,
  verificationFailed,
  removalFailed,
  storageFailed,
  busy,
}

sealed class GenUiSetupStatus {
  const GenUiSetupStatus();
  List<GenUiAgent> get agents => const [];
}

final class GenUiSetupOff extends GenUiSetupStatus {
  const GenUiSetupOff();
}

final class GenUiSetupUnavailable extends GenUiSetupStatus {
  const GenUiSetupUnavailable({required this.reason, this.affected = const []});
  final GenUiSetupProblem reason;

  /// The agents [reason] is about; empty when it is about none in
  /// particular (the server, or no agent here at all).
  final List<GenUiAgent> affected;
}

final class GenUiSetupInstalling extends GenUiSetupStatus {
  const GenUiSetupInstalling();
}

final class GenUiSetupOn extends GenUiSetupStatus {
  GenUiSetupOn({required List<GenUiAgent> agents})
    : agents = List.unmodifiable(agents);
  @override
  final List<GenUiAgent> agents;
}

final class GenUiSetupPartial extends GenUiSetupStatus {
  GenUiSetupPartial({
    required List<GenUiAgent> agents,
    required this.reason,
    List<GenUiAgent> affected = const [],
  }) : agents = List.unmodifiable(agents),
       affected = List.unmodifiable(affected);

  /// The agents cards are on for.
  @override
  final List<GenUiAgent> agents;
  final GenUiSetupProblem reason;

  /// The agents [reason] is about: cards are not on for them.
  final List<GenUiAgent> affected;
}

final class GenUiSetupRestartRequired extends GenUiSetupStatus {
  GenUiSetupRestartRequired({required List<GenUiAgent> agents})
    : agents = List.unmodifiable(agents);
  @override
  final List<GenUiAgent> agents;
}

final class GenUiSetupFailed extends GenUiSetupStatus {
  const GenUiSetupFailed({required this.reason, this.affected = const []});
  final GenUiSetupProblem reason;

  /// The agents [reason] is about; empty when it is about none in
  /// particular.
  final List<GenUiAgent> affected;
}
