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
  const GenUiSetupUnavailable({required this.reason});
  final GenUiSetupProblem reason;
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
  GenUiSetupPartial({required List<GenUiAgent> agents, required this.reason})
    : agents = List.unmodifiable(agents);
  @override
  final List<GenUiAgent> agents;
  final GenUiSetupProblem reason;
}

final class GenUiSetupRestartRequired extends GenUiSetupStatus {
  GenUiSetupRestartRequired({required List<GenUiAgent> agents})
    : agents = List.unmodifiable(agents);
  @override
  final List<GenUiAgent> agents;
}

final class GenUiSetupFailed extends GenUiSetupStatus {
  const GenUiSetupFailed({required this.reason});
  final GenUiSetupProblem reason;
}
