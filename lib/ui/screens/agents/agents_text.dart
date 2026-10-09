import 'package:flutter/widgets.dart';

import '../../../domain/agent_auth_probe.dart';
import '../../../domain/agent_catalog.dart';
import '../../../domain/agent_sign_in.dart';
import '../../../domain/agent_tools/agent_certification.dart';
import '../../../domain/phone_agent_host.dart';
import '../../../domain/product_failure.dart';
import '../../../domain/server_gateway.dart' show ProductException;
import '../../../domain/phone_agents.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit_bidi.dart';
import '../../kit/kit_row.dart' show KitRowChip;
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
/// whole setup. Isolated left to right, so the digits and the unit keep
/// their order ("21 MB", never "MB 21") inside right-to-left copy.
String? agentPayloadSize(String agentId) {
  final bytes = AgentCatalog.builtIn.agents
      .where((agent) => agent.id == agentId)
      .firstOrNull
      ?.recipe
      ?.artifacts[AgentArchitecture.arm64]
      ?.downloadBytes;
  return bytes == null ? null : _sizeText(bytes);
}

/// What removing an agent freed ("98 MB", "412 kB"), as the host measured it,
/// in the same left-to-right isolate as [agentPayloadSize].
String agentFreedSize(int bytes) => _sizeText(bytes);

String _sizeText(int bytes) => KitBidi.ltr(
  bytes >= 1000000000
      ? '${(bytes / 1000000000).toStringAsFixed(1)} GB'
      : bytes >= 1000000
      ? '${(bytes / 1000000).round()} MB'
      : bytes >= 1000
      ? '${(bytes / 1000).round()} kB'
      : '$bytes B',
);

/// Where a row stands, in words: one line, and a second quiet line where it
/// applies ("Can't reopen old conversations").
///
/// [account] is the agent's own sign-in status check. Only a check that
/// confirmed a sign-in lets a ready row say "Signed in" (with the account's
/// name when the check gave one); an install or a terminal's exit code never
/// does. Without it the row keeps the plain "Ready". [context] lets a row at
/// its plan limit say when it resets.
String agentRowLine(
  AppLocalizations l10n,
  AgentRow row, {
  AgentAuthProbeResult? account,
  BuildContext? context,
}) {
  switch (row.status) {
    case PhoneAgentStatus.ready:
      final first = account?.state == AgentAuthProbeState.signedIn
          ? agentSignedInLine(l10n, account!)
          : l10n.agentsStateReady;
      return row.resumeLabel != null
          ? '$first\n${l10n.agentsStateCantReopen}'
          : first;
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
      // The host's own reset time, when it gave one: never an estimate.
      final resetAt = row.resetAt;
      return resetAt == null || context == null
          ? l10n.agentsStateLimit
          : l10n.agentsStateLimitReset(KitTime.moment(context, resetAt));
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

/// "Signed in as {account}" when the status check named the account (kept to
/// one line, isolated so a Latin address inside Arabic copy stays whole),
/// else "Signed in".
String agentSignedInLine(AppLocalizations l10n, AgentAuthProbeResult account) {
  final name = account.accountDisplayName?.replaceAll(RegExp(r'\s+'), ' ');
  return name == null || name.trim().isEmpty
      ? l10n.agentsSignedIn
      : l10n.agentsSignedInAs(KitBidi.auto(name.trim()));
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

/// The act alone, for a row's chip whose title already names the agent
/// ("Install", "Sign in"); [agentFixLabel] is what screen readers hear.
String agentFixChipLabel(AppLocalizations l10n, PhoneAgentFixAction action) =>
    switch (action) {
      PhoneAgentFixAction.install => l10n.agentsInstallHint,
      PhoneAgentFixAction.signIn => l10n.agentsChipSignIn,
      PhoneAgentFixAction.resume => l10n.agentsChipResume,
      PhoneAgentFixAction.runPhoneCheck => l10n.agentsChipCheck,
    };

/// [row]'s one fix action as a row chip: the short act on the chip, the act
/// with its target for screen readers. Null when the row needs none.
KitRowChip? agentFixChip(
  AppLocalizations l10n,
  AgentRow row, {
  required Key key,
  required VoidCallback onPressed,
}) {
  final action = row.fixAction;
  if (action == null) return null;
  return KitRowChip(
    key: key,
    label: agentFixChipLabel(l10n, action),
    semanticsLabel: agentFixLabel(l10n, action, KitBidi.auto(row.name)),
    onPressed: onPressed,
  );
}

/// Ready on this phone and certified on the version this app installs
/// (docs/verification/agent-certification-matrix.json, bundled): the agent
/// picker lets the person choose it.
bool agentChoosable(AgentRow row) =>
    row.chatSelectable &&
    AgentCertificationMatrix.bundled.certifiedForChat(row.id);

/// Where a row stands in the agent picker: [agentRowLine], except that a
/// ready agent whose version is not certified says so.
String agentPickerLine(AppLocalizations l10n, AgentRow row) =>
    row.chatSelectable && !agentChoosable(row)
    ? l10n.agentsStateNotCertified
    : agentRowLine(l10n, row);

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

/// The sentence that ends a removal: what was freed, or that nothing was
/// installed.
String agentRemovalResultText(
  AppLocalizations l10n,
  String agent,
  AgentRemovalResult result,
) => result.alreadyAbsent
    ? l10n.agentsAlreadyRemoved(KitBidi.auto(agent))
    : l10n.agentsRemoved(
        KitBidi.auto(agent),
        agentFreedSize(result.freedBytes),
      );

/// The controller's three fixed removal messages (BA10) in the person's
/// language. Anything else is the unconfirmed sentence: no raw error ever
/// shows as copy.
String agentRemovalFailureText(AppLocalizations l10n, Object error) {
  final message = error is ProductException ? error.message : null;
  return switch (message) {
    "This agent can't be removed here." => l10n.agentsRemoveUnsupported,
    'This agent is in use. Finish its work and try again.' =>
      l10n.agentsRemoveBusy,
    _ => l10n.agentsRemoveUnconfirmed,
  };
}
