import '../domain/models.dart';
import 'server_gateway.dart';

export 'agent_handoff_record.dart';
export 'agent_handoff_history.dart';

/// Explicit file API, never an agent prompt or shell-command workaround.
abstract interface class AgentHandoffFileGateway {
  /// Exclusively creates `.handoff/<recordId>/record.md` in [directory], after
  /// writing a self-ignoring .gitignore in that fresh subdirectory. Returns the
  /// relative path only after content read-back. Never overwrites a record.
  Future<String> writeAgentHandoff({
    required String directory,
    required String recordId,
    required String markdown,
  });
}

/// A target whose initial prompt has a dispatch-time cancellation fence and
/// whose durable session identity can be persisted after first-prompt creation.
abstract interface class AgentHandoffTargetGateway
    implements AgentHandoffFileGateway {
  String handoffSessionId(String sessionID);

  /// The id the app opens [sessionID]'s conversation by, on the connection
  /// it opens it on (which may not be this gateway); identity after restart.
  String handoffNavigationSessionId(String sessionID);
  Future<void> promptAgentHandoff(
    String sessionID, {
    required String text,
    required ModelRef model,
    String? agent,
    String? variant,
    required void Function() beforeSend,
  });
}

enum HandoffOrigin { server, phoneAgent }

/// [filesystemId] is app-owned evidence of a shared filesystem, such as the
/// identity of the same managed Linux installation. Never derive it from a
/// URL, matching directory strings, or an agent's assertion. Null is unknown.
final class HandoffScope {
  const HandoffScope({
    required this.profileId,
    required this.directory,
    this.workspace,
    this.filesystemId,
    this.origin = HandoffOrigin.server,
  });

  /// Persisted parent profile, never an ephemeral agent transport profile.
  final String profileId;
  final HandoffOrigin origin;
  final String directory;
  final String? workspace;
  final String? filesystemId;

  bool sharesProject(HandoffScope other) =>
      filesystemId != null &&
      filesystemId!.isNotEmpty &&
      filesystemId == other.filesystemId &&
      directory == other.directory &&
      directory.startsWith('/') &&
      !directory.split('/').any((part) => part == '.' || part == '..');
}

final class HandoffTarget {
  const HandoffTarget({
    required this.gateway,
    required this.scope,
    required this.model,
    required this.label,
    this.agent,
    this.variant = '',
    this.startNewAgent = true,
  });
  final ServerGateway gateway;
  final HandoffScope scope;
  final ModelRef model;
  final String label;
  final String? agent;
  final String variant;

  /// False only for a model choice in the current conversation's runtime.
  /// A Claude Code choice is an agent choice even on the same phone.
  final bool startNewAgent;
}

enum HandoffRoute { switchModel, newConversation, unavailable }

/// Displayable unavailability without exposing backend flavors or raw errors.
enum HandoffUnavailableReason {
  projectNotShared,
  safeFileWriterUnavailable,
  targetDispatchUnavailable;

  String get message => switch (this) {
    projectNotShared =>
      'These conversations are not connected to the same project.',
    safeFileWriterUnavailable =>
      'This destination cannot safely save a conversation record yet.',
    targetDispatchUnavailable =>
      'This destination cannot start a linked conversation yet.',
  };
}

enum HandoffFailureKind {
  unavailable,
  projectMismatch,
  staleScope,
  targetUnavailable,
  sourceChanged,
  preparationFailed,
  invalidDraft,
  fileWriteFailed,
  creationUnknown,
  deliveryUnknown,
  linkFailed,
}

final class AgentHandoffFailure implements Exception {
  const AgentHandoffFailure(this.kind);
  final HandoffFailureKind kind;
  @override
  String toString() => 'AgentHandoffFailure(${kind.name})';
}

HandoffRoute agentHandoffRoute({
  required ServerGateway source,
  required HandoffScope sourceScope,
  required ModelRef? sourceModel,
  required HandoffTarget target,
}) {
  if (identical(source, target.gateway) &&
      sourceScope.profileId == target.scope.profileId &&
      sourceScope.origin == target.scope.origin &&
      sourceScope.directory == target.scope.directory &&
      sourceScope.workspace == target.scope.workspace &&
      !target.startNewAgent &&
      (source.capabilities.sessionModelProviderSwitching ||
          sourceModel?.providerID == target.model.providerID)) {
    return HandoffRoute.switchModel;
  }
  if (!sourceScope.sharesProject(target.scope) ||
      !target.gateway.capabilities.agentHandoffFiles ||
      target.gateway is! AgentHandoffTargetGateway) {
    return HandoffRoute.unavailable;
  }
  return HandoffRoute.newConversation;
}

/// Companion to [agentHandoffRoute] for disabled destination rows. The presence
/// of a generic filesystem RPC alone does not enable the verified file writer.
HandoffUnavailableReason? agentHandoffUnavailableReason({
  required ServerGateway source,
  required HandoffScope sourceScope,
  required ModelRef? sourceModel,
  required HandoffTarget target,
}) {
  if (agentHandoffRoute(
        source: source,
        sourceScope: sourceScope,
        sourceModel: sourceModel,
        target: target,
      ) !=
      HandoffRoute.unavailable) {
    return null;
  }
  if (!sourceScope.sharesProject(target.scope)) {
    return HandoffUnavailableReason.projectNotShared;
  }
  if (!target.gateway.capabilities.agentHandoffFiles) {
    return HandoffUnavailableReason.safeFileWriterUnavailable;
  }
  return HandoffUnavailableReason.targetDispatchUnavailable;
}
