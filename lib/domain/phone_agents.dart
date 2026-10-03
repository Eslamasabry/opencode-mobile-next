import 'agent_catalog.dart';
import 'agent_sign_in.dart';
import 'server_gateway/capabilities.dart';

enum UnverifiedResumePolicy { hide, label }

enum PhoneAgentStatus {
  stoppedInBackground,
  signedOut,
  limitReached,
  ready,
  unavailable,
  needsInstall,
  needsQualification,
}

enum PhoneAgentFixAction {
  install,
  signIn,
  resume,
  runPhoneCheck;

  String get label => switch (this) {
    install => 'Install',
    signIn => 'Sign in',
    resume => 'Resume',
    runPhoneCheck => 'Run phone check',
  };
}

enum PhoneAgentHiddenReason {
  catalogUnavailable,
  runtimeUnknown,
  needsInstall,
  hostUnavailable,
  needsQualification,
  signedOut,
  limitReached,
  resumeUnverified,
  stoppedInBackground,
}

/// Sanitized host facts for one agent. An installed artifact or an agent name
/// cannot supply capability proof. The host producer owns freshness/lifecycle.
final class PhoneAgentRuntime {
  const PhoneAgentRuntime({
    required this.agentId,
    this.installed = false,
    this.hostAvailable = false,
    this.architectureQualified = false,
    this.capabilities = const AgentCapabilities(),
    this.signInPhase,
    this.stoppedInBackground = false,
    this.resetAt,
  });

  final String agentId;
  final bool installed;
  final bool hostAvailable;
  final bool architectureQualified;
  final AgentCapabilities capabilities;
  final AgentSignInPhase? signInPhase;
  final bool stoppedInBackground;

  /// Host-supplied time only. No estimate is made when it is absent.
  final DateTime? resetAt;
}

/// Safe presentation data for kit-only UI. Setup inventory and chat selection
/// are distinct: an uninstalled agent can offer Install while hidden in chat.
final class AgentRow {
  const AgentRow._({
    required this.id,
    required this.name,
    required this.iconKey,
    required this.status,
    required this.statusMessage,
    required this.setupVisible,
    required this.chatVisible,
    required this.chatSelectable,
    required this.installable,
    required this.capabilities,
    this.fixAction,
    this.hiddenReason,
    this.resumeLabel,
    this.resetAt,
  });

  final String id;
  final String name;
  final String iconKey;
  final PhoneAgentStatus status;
  final String statusMessage;
  final PhoneAgentFixAction? fixAction;
  final bool setupVisible;
  final bool chatVisible;
  final bool chatSelectable;
  final bool installable;
  final AgentCapabilities capabilities;

  /// Explains why the agent is absent from chat selection, not setup inventory.
  final PhoneAgentHiddenReason? hiddenReason;
  final String? resumeLabel;
  final DateTime? resetAt;
}

/// Intersect only semantically matching server gates. The server contract has
/// no model-list, durable agent-resume, or cancel switches: those operations
/// remain subject to runtime proof. cliSessionResume means a terminal command
/// on a computer and is deliberately not a durable-session resume gate.
AgentCapabilities phoneAgentCapabilities({
  required AgentCapabilities runtimeCapabilities,
  required ServerCapabilities serverCapabilities,
}) => AgentCapabilities(
  resumeVerified: runtimeCapabilities.resumeVerified,
  modelList: runtimeCapabilities.modelList,
  permissions:
      runtimeCapabilities.permissions &&
      serverCapabilities.hostAgentPermissionActions,
  images: runtimeCapabilities.images && serverCapabilities.promptAttachments,
  cancel: runtimeCapabilities.cancel,
);

AgentRow buildAgentRow({
  required AgentDescriptor descriptor,
  required AgentArchitecture architecture,
  required ServerCapabilities serverCapabilities,
  PhoneAgentRuntime? runtime,
  UnverifiedResumePolicy unverifiedResumePolicy = UnverifiedResumePolicy.hide,
}) {
  final fact = runtime?.agentId == descriptor.id ? runtime : null;
  final installable = descriptor.installableOn(architecture);
  final catalogVisible = descriptor.availability != AgentAvailability.hidden;
  const noProof = AgentCapabilities();

  AgentRow blocked(
    PhoneAgentStatus status,
    String message,
    PhoneAgentHiddenReason reason, [
    PhoneAgentFixAction? action,
  ]) => AgentRow._(
    id: descriptor.id,
    name: descriptor.name,
    iconKey: descriptor.iconKey,
    status: status,
    statusMessage: message,
    fixAction: action,
    setupVisible: catalogVisible,
    chatVisible: false,
    chatSelectable: false,
    installable: installable,
    capabilities: noProof,
    hiddenReason: reason,
    resetAt: status == PhoneAgentStatus.limitReached ? fact?.resetAt : null,
  );

  if (!catalogVisible) {
    return blocked(
      PhoneAgentStatus.unavailable,
      'This agent is not available yet.',
      PhoneAgentHiddenReason.catalogUnavailable,
    );
  }
  if (fact == null) {
    return blocked(
      PhoneAgentStatus.unavailable,
      'Check the host to see whether this agent is ready.',
      PhoneAgentHiddenReason.runtimeUnknown,
      PhoneAgentFixAction.resume,
    );
  }
  if (!fact.installed) {
    return blocked(
      installable
          ? PhoneAgentStatus.needsInstall
          : PhoneAgentStatus.unavailable,
      installable
          ? 'Install this agent on your phone to get started.'
          : 'Installation is not available for this phone yet.',
      PhoneAgentHiddenReason.needsInstall,
      installable ? PhoneAgentFixAction.install : null,
    );
  }
  if (fact.stoppedInBackground) {
    return blocked(
      PhoneAgentStatus.stoppedInBackground,
      'Stopped while the app was in the background.',
      PhoneAgentHiddenReason.stoppedInBackground,
      PhoneAgentFixAction.resume,
    );
  }
  if (!fact.hostAvailable) {
    return blocked(
      PhoneAgentStatus.unavailable,
      'The host is not available. Resume it and check again.',
      PhoneAgentHiddenReason.hostUnavailable,
      PhoneAgentFixAction.resume,
    );
  }
  if (!fact.architectureQualified) {
    return blocked(
      PhoneAgentStatus.needsQualification,
      'Run the phone check before using this agent.',
      PhoneAgentHiddenReason.needsQualification,
      PhoneAgentFixAction.runPhoneCheck,
    );
  }
  if (descriptor.signInMethod != AgentSignInMethod.none &&
      fact.signInPhase == null) {
    return blocked(
      PhoneAgentStatus.unavailable,
      'Checking sign-in on your phone.',
      PhoneAgentHiddenReason.runtimeUnknown,
    );
  }
  if (fact.signInPhase == AgentSignInPhase.limitReached) {
    return blocked(
      PhoneAgentStatus.limitReached,
      'Usage limit reached. Try again when it resets.',
      PhoneAgentHiddenReason.limitReached,
    );
  }
  if (fact.signInPhase == AgentSignInPhase.failed) {
    return blocked(
      PhoneAgentStatus.unavailable,
      'Sign-in could not be checked. Try signing in again.',
      PhoneAgentHiddenReason.signedOut,
      PhoneAgentFixAction.signIn,
    );
  }
  if (descriptor.signInMethod != AgentSignInMethod.none &&
      fact.signInPhase != AgentSignInPhase.signedIn) {
    return blocked(
      PhoneAgentStatus.signedOut,
      descriptor.signInMethod == AgentSignInMethod.apiKeyHost
          ? 'Set up a provider key on the host to use this agent.'
          : 'Sign in on the host to use this agent.',
      PhoneAgentHiddenReason.signedOut,
      PhoneAgentFixAction.signIn,
    );
  }

  final capabilities = phoneAgentCapabilities(
    runtimeCapabilities: fact.capabilities,
    serverCapabilities: serverCapabilities,
  );
  final unverifiedResume = !capabilities.resumeVerified;
  final visible =
      !unverifiedResume ||
      unverifiedResumePolicy == UnverifiedResumePolicy.label;
  return AgentRow._(
    id: descriptor.id,
    name: descriptor.name,
    iconKey: descriptor.iconKey,
    status: PhoneAgentStatus.ready,
    statusMessage: unverifiedResume
        ? visible
              ? 'New chats are available. Old chats cannot be reopened yet.'
              : 'Continuing saved chats has not been checked yet.'
        : 'Ready to use.',
    setupVisible: true,
    chatVisible: visible,
    chatSelectable: visible,
    installable: installable,
    capabilities: capabilities,
    hiddenReason: visible ? null : PhoneAgentHiddenReason.resumeUnverified,
    resumeLabel: unverifiedResume ? 'Can’t reopen old chats' : null,
  );
}
