import 'package:flutter/widgets.dart';

import '../../../domain/agent_catalog.dart';
import '../../../domain/agent_sign_in.dart';
import '../../../domain/phone_agent_host.dart';
import '../../../domain/product_failure.dart';
import '../../../domain/phone_agents.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit_time.dart';

/// The words and glyphs of the agent surfaces, in one place so the sheet,
/// Settings and the status lines say the same thing.

/// A glyph for an agent's icon key; a generic one when unknown.
IconData agentIcon(String iconKey) => switch (iconKey) {
  'opencode' => AppIconography.code,
  _ => AppIconography.terminal,
};

/// The agent's own download size ("98 MB", "1.2 GB") from the catalog, or
/// null when the recipe does not say. Only the agent payload, never the
/// whole setup.
String? agentPayloadSize(String agentId) {
  final bytes = AgentCatalog.builtIn.agents
      .where((agent) => agent.id == agentId)
      .firstOrNull
      ?.recipe
      ?.artifacts[AgentArchitecture.arm64]
      ?.downloadBytes;
  if (bytes == null) return null;
  return bytes >= 1000000000
      ? '${(bytes / 1000000000).toStringAsFixed(1)} GB'
      : '${(bytes / 1000000).round()} MB';
}

/// True while a browser sign-in for [agentId] waits for its code (the page
/// is ready and no code was sent yet): the person is in the browser, or left
/// the sheet, and comes back to the same code.
bool agentLoginPending(PhoneAgentsSource agents, String agentId) {
  final state = agents.agentSignInState(agentId);
  return state != null &&
      !state.codeSubmitted &&
      (state.phase == AgentSignInPhase.urlReady ||
          state.phase == AgentSignInPhase.awaitingCode) &&
      agents.agentSignInUrl(agentId) != null;
}

/// Where a row stands, in words: one line, and a second quiet line where it
/// applies ("Can't reopen old conversations").
String agentRowLine(AppLocalizations l10n, AgentRow row) {
  switch (row.status) {
    case PhoneAgentStatus.ready:
      return row.resumeLabel != null
          ? '${l10n.agentsStateReady}\n${l10n.agentsStateCantReopen}'
          : l10n.agentsStateReady;
    case PhoneAgentStatus.needsInstall:
      final size = agentPayloadSize(row.id);
      return size == null
          ? l10n.agentsStateNotInstalledNoSize
          : l10n.agentsStateNotInstalled(size);
    case PhoneAgentStatus.signedOut:
      return l10n.agentsStateSignInNeeded;
    case PhoneAgentStatus.needsQualification:
      return l10n.agentsStatePhoneCheck;
    case PhoneAgentStatus.stoppedInBackground:
      return l10n.agentsStateStopped;
    case PhoneAgentStatus.limitReached:
      return l10n.agentsStateLimit;
    case PhoneAgentStatus.unavailable:
      return switch (row.hiddenReason) {
        PhoneAgentHiddenReason.unsupportedArchitecture =>
          l10n.agentsStateNeedsArm,
        PhoneAgentHiddenReason.unverifiedDownload => l10n.agentsStateNoDownload,
        _ =>
          row.fixAction == PhoneAgentFixAction.signIn
              ? l10n.agentsStateSignInNeeded
              : row.hiddenReason == PhoneAgentHiddenReason.runtimeUnknown
              ? l10n.agentsStateChecking
              : l10n.agentsStateUnavailable,
      };
  }
}

/// A step that failed: plain words with the way forward, and the technical
/// text for the Details control (never shown as copy).
class AgentFailure {
  const AgentFailure(this.words, [this.technical]);
  final String words;
  final String? technical;
}

/// Words and Details for [error]: typed failures say what they mean; anything
/// else gets the one plain sentence, with its text under Details.
AgentFailure agentFailure(AppLocalizations l10n, Object error) {
  final technical =
      ProductFailure.from(error).technicalDetails ?? error.toString();
  return switch (error) {
    AgentHostException(:final reason) => AgentFailure(
      agentHostFailureText(l10n, reason),
      technical,
    ),
    AgentSignInException(:final failure) => AgentFailure(
      agentSignInFailureText(l10n, failure, 'This agent'),
      technical,
    ),
    _ => AgentFailure(l10n.agentsActionFailed, technical),
  };
}

/// The label of a row's one fix action, naming its target.
String agentFixLabel(
  AppLocalizations l10n,
  PhoneAgentFixAction action,
  String agent,
) => switch (action) {
  PhoneAgentFixAction.install => l10n.agentsInstallAction(agent),
  PhoneAgentFixAction.signIn => l10n.agentsSignInAction(agent),
  PhoneAgentFixAction.resume => l10n.agentsResumeAction(agent),
  PhoneAgentFixAction.runPhoneCheck => l10n.agentsCheckAction(agent),
};

/// A status line's words.
String agentStatusLineText(
  BuildContext context,
  AppLocalizations l10n,
  PhoneAgentStatusLine line,
) => switch (line.kind) {
  PhoneAgentStatusLineKind.limitReached =>
    line.resetAt == null
        ? l10n.agentsLimitUnknown(line.agentName)
        : l10n.agentsLimitReset(
            line.agentName,
            KitTime.clock(context, line.resetAt!),
          ),
  PhoneAgentStatusLineKind.signedOut => l10n.agentsSignedOutLine(
    line.agentName,
  ),
  PhoneAgentStatusLineKind.stopped => l10n.agentsStoppedLine(line.agentName),
};

/// A phone-check step's name.
String agentCheckStepName(AppLocalizations l10n, AgentPhoneCheckStep step) =>
    switch (step) {
      AgentPhoneCheckStep.install => l10n.agentsStepInstall,
      AgentPhoneCheckStep.version => l10n.agentsStepVersion,
      AgentPhoneCheckStep.daemon => l10n.agentsStepConnection,
      AgentPhoneCheckStep.hello => l10n.agentsStepReady,
    };

/// A failed check in plain words with the way forward.
String agentHostFailureText(AppLocalizations l10n, AgentHostFailure? failure) =>
    switch (failure) {
      AgentHostFailure.storage => l10n.agentsFailStorage,
      AgentHostFailure.install => l10n.agentsFailInstall,
      AgentHostFailure.interrupted => l10n.agentsFailInterrupted,
      AgentHostFailure.version => l10n.agentsFailVersion,
      AgentHostFailure.daemon => l10n.agentsFailDaemon,
      AgentHostFailure.hello => l10n.agentsFailHello,
      AgentHostFailure.wrongArchitecture => l10n.agentsFailArchitecture,
      AgentHostFailure.stale => l10n.agentsFailStale,
      AgentHostFailure.busy => l10n.agentsFailBusy,
      AgentHostFailure.unavailable || null => l10n.agentsFailUnavailable,
    };

/// A failed sign-in in plain words with the way forward.
String agentSignInFailureText(
  AppLocalizations l10n,
  AgentSignInFailure? failure,
  String agent,
) => switch (failure) {
  AgentSignInFailure.invalidCode => l10n.agentsSignInBadCode,
  AgentSignInFailure.authenticationRejected => l10n.agentsSignInRejected(agent),
  AgentSignInFailure.hostUnavailable => l10n.agentsSignInHostDown,
  AgentSignInFailure.unavailable => l10n.agentsSignInUnavailable,
  _ => l10n.agentsSignInFailed,
};
