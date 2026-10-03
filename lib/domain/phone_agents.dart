import 'agent_catalog.dart';
import 'agent_sign_in.dart';
import 'server_gateway/capabilities.dart';

/// Legacy hide callers also show labelled agents under the owner's new rule.
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

  /// The catalog has no download for this phone's processor.
  unsupportedArchitecture,

  /// The agent has no verified download recipe yet.
  unverifiedDownload,
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
    this.resumeNote,
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
  final String? resumeNote;
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
  UnverifiedResumePolicy unverifiedResumePolicy = UnverifiedResumePolicy.label,
}) {
  final fact = runtime?.agentId == descriptor.id ? runtime : null;
  final installable = descriptor.installableOn(architecture);
  // Server types (OpenCode 1 and 2) are not phone agents: only the agents the
  // phone host runs are listed.
  final runsOnPhoneHost =
      descriptor.route == AgentRoute.paseoNative ||
      descriptor.route == AgentRoute.acpPaseo;
  final catalogVisible =
      runsOnPhoneHost && descriptor.availability != AgentAvailability.hidden;
  // Why this agent cannot be installed here at all (a real blocker), or null.
  final PhoneAgentHiddenReason? cannotInstall = installable
      ? null
      : descriptor.recipe == null ||
            descriptor.unavailableReason ==
                AgentUnavailableReason.recipeUnverified ||
            descriptor.unavailableReason ==
                AgentUnavailableReason.dependencyClosureUnverified
      ? PhoneAgentHiddenReason.unverifiedDownload
      : PhoneAgentHiddenReason.unsupportedArchitecture;
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
    resumeLabel: fact?.capabilities.resumeVerified == true
        ? null
        : agentResumeUnverifiedLabel,
    resumeNote: fact?.capabilities.resumeVerified == true
        ? null
        : agentStartsNewChatNote,
    resetAt: status == PhoneAgentStatus.limitReached ? fact?.resetAt : null,
  );

  if (!catalogVisible) {
    return blocked(
      PhoneAgentStatus.unavailable,
      'This agent is not available yet.',
      PhoneAgentHiddenReason.catalogUnavailable,
    );
  }
  // Nothing is known about the host yet (or it could not be read): the way
  // forward is Install. The install and the phone check that follows it are
  // the verification, so an unverified phone is never a dead end. Only a
  // real blocker (the processor, a missing download) stops it, and says so.
  if (fact == null || !fact.installed) {
    if (cannotInstall != null) {
      return blocked(
        PhoneAgentStatus.unavailable,
        cannotInstall == PhoneAgentHiddenReason.unsupportedArchitecture
            ? 'This phone cannot run this agent.'
            : 'This agent has no verified download yet.',
        cannotInstall,
      );
    }
    return blocked(
      PhoneAgentStatus.needsInstall,
      'Install this agent on your phone to get started.',
      fact == null
          ? PhoneAgentHiddenReason.runtimeUnknown
          : PhoneAgentHiddenReason.needsInstall,
      PhoneAgentFixAction.install,
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
  return AgentRow._(
    id: descriptor.id,
    name: descriptor.name,
    iconKey: descriptor.iconKey,
    status: PhoneAgentStatus.ready,
    statusMessage: unverifiedResume
        ? agentResumeUnverifiedLabel
        : 'Ready to use.',
    setupVisible: true,
    chatVisible: true,
    chatSelectable: true,
    installable: installable,
    capabilities: capabilities,
    resumeLabel: unverifiedResume ? agentResumeUnverifiedLabel : null,
    resumeNote: unverifiedResume ? agentStartsNewChatNote : null,
  );
}
