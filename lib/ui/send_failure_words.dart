import '../domain/agent_catalog.dart' show AgentCatalog;
import '../domain/agent_persona.dart';
import '../domain/agent_run_failure.dart';
import '../domain/agent_sign_in.dart' show AgentSignInPhase;
import '../domain/phone_agents.dart' show PhoneAgentStatus;
import '../domain/phone_agents_source.dart' show PhoneAgentsSource;
import '../l10n/app_localizations.dart';
import 'agents/agent_persona_text.dart';
import 'kit/kit_redact.dart';
import 'kit/kit_technical_value.dart';

/// The agent a refusal concerns, by the name the app gives it ("Kilo"):
/// the phone agent catalog's name, else the persona's, else its own id as
/// the host gives it;
/// "The agent" when the host did not say.
String agentRunFailureAgentName(String? provider, AppLocalizations l10n) {
  final id = provider?.trim() ?? '';
  if (id.isEmpty) return l10n.chatSendFailureAgentFallback;
  final listed = AgentCatalog.builtIn.byId(id)?.name;
  if (listed != null && listed.isNotEmpty) return listed;
  final persona = AgentPersona.of(id);
  if (persona.id != AgentPersonaId.other) return persona.name(l10n);
  return id;
}

/// What a refused send says, in the app's words: why, in a few words
/// ("Kilo needs you to sign in"). The host's own sentence is never copy; it
/// is under Details ([agentRunFailureDetails]). [accountNeeded]: the app
/// already shows the agent signed in (Kilo works without an account for its
/// free models), so a sign-in refusal means this model needs an account.
String agentRunFailureWords(
  AgentRunFailure failure,
  AppLocalizations l10n, {
  bool accountNeeded = false,
}) {
  final agent = agentRunFailureAgentName(failure.provider, l10n);
  return switch (failure.kind) {
    AgentRunFailureKind.signIn when accountNeeded =>
      l10n.chatSendFailureNeedsAccount(agent),
    AgentRunFailureKind.signIn => l10n.chatSendFailureSignIn(agent),
    AgentRunFailureKind.noCredit => l10n.chatSendFailureNoCredit(agent),
    AgentRunFailureKind.modelUnavailable =>
      l10n.chatSendFailureModelUnavailable,
    AgentRunFailureKind.busy => l10n.chatSendFailureBusy(agent),
    AgentRunFailureKind.connectionLost => switch (agentModelCompany(
      failure.provider,
    )) {
      final company? => l10n.chatSendFailureLostConnectionTo(agent, company),
      null => l10n.chatSendFailureLostConnection(agent),
    },
    AgentRunFailureKind.unknown => l10n.agentErrorUnknown,
  };
}

/// The company whose models an agent calls ("Anthropic" for Claude Code),
/// for words like "Claude Code lost its connection to Anthropic"; null when
/// the agent can call many (Kilo, OpenCode) or is not known.
String? agentModelCompany(String? provider) => switch (provider?.trim()) {
  'claude' => 'Anthropic',
  'codex' => 'OpenAI',
  'gemini' => 'Google',
  _ => null,
};

/// The technical side of a refused send for the Details sheet: which agent,
/// which model and the error code as values, and the host's sentence as the
/// text. Every piece is redacted (provider keys and passwords never show).
({List<KitTechnicalValue> values, String text}) agentRunFailureDetails(
  AgentRunFailure failure,
  AppLocalizations l10n,
) => (
  values: [
    if (failure.provider case final provider?)
      KitTechnicalValue(
        l10n.chatSendFailureDetailsAgent,
        KitRedact.text(provider),
      ),
    if (failure.model case final model?)
      KitTechnicalValue(
        l10n.chatSendFailureDetailsModel,
        KitRedact.text(model),
      ),
    if (failure.code case final code?)
      KitTechnicalValue(l10n.chatSendFailureDetailsCode, KitRedact.text(code)),
  ],
  text: KitRedact.text(failure.reason),
);

/// Whether this phone already shows [agentId] signed in or ready (its sheet
/// says "Signed in"): a sign-in refusal from it then means the model needs
/// the agent's account, not that the agent signed out.
bool agentShownSignedIn(PhoneAgentsSource phone, String agentId) =>
    phone.agentSignInState(agentId)?.phase == AgentSignInPhase.signedIn ||
    phone.agentRows.any(
      (row) => row.id == agentId && row.status == PhoneAgentStatus.ready,
    );
