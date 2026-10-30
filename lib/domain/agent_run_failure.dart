/// An agent host that refused a turn, or could not start one, and said why.
///
/// A Paseo daemon answers a send it could not start with
/// `send_agent_message_response {accepted: false, error: "<sentence>"}`. The
/// sentence is the provider's own (Kilo, Claude Code, Codex...): "Authentication
/// required", "Insufficient credits", "Model not found". The app kept it in
/// a transport failure that only Details read, and the chat sorted the
/// transport's generic words instead, so every refusal read as "The request
/// could not finish." and Send again went into the same wall.
///
/// [AgentRunFailure] keeps the reason with what it concerns (the agent, the
/// model) on the failure the gateway throws, and sorts it into the few kinds
/// a person can act on. The reason is never copy: the chat says the kind in
/// its own words and shows the reason under Details, redacted.
library;

import 'agent_error_text.dart';

/// What a person can do about a refused turn.
enum AgentRunFailureKind {
  /// The agent or its provider needs a sign-in, an account or a key.
  signIn,

  /// The account has no credit (or quota) for this model.
  noCredit,

  /// The model is unknown, not offered, or not allowed on this account.
  modelUnavailable,

  /// The provider is limiting requests or is overloaded; later works.
  busy,

  /// The agent lost its connection to its model provider (no network, DNS,
  /// a dropped or timed-out call): the turn stopped where it was, and
  /// asking it to continue picks up from there.
  connectionLost,

  /// Anything else: the reason is still there for Details.
  unknown,
}

/// A refused or failed start of an agent's turn, with the host's reason and
/// what it concerns. A plain value: the transport failure that carries it
/// keeps its own type and kind (`PaseoFailure.runFailure`).
final class AgentRunFailure {
  AgentRunFailure({
    required String reason,
    this.provider,
    this.model,
    String? code,
  }) : reason = reason.trim(),
       code = code ?? agentRunFailureCode(reason);

  /// The host's sentence, as given (the transport already masked its
  /// password). Untrusted: shown only under Details, after redaction.
  final String reason;

  /// The agent's provider id on the host ("kilo", "claude"), when known.
  final String? provider;

  /// The model the turn asked for ("kilo/google/gemini-3-pro-image").
  final String? model;

  /// An error code the reason carries ("401", "-32000"), when it has one.
  final String? code;

  AgentRunFailureKind get kind => classifyAgentRunFailure(reason);

  @override
  String toString() => 'AgentRunFailure($kind, $provider, $model, $code)';
}

/// What an agent host says about a conversation's last turn: whether it
/// failed, the reason when the host still holds one, and the other records
/// of the same conversation (a host that forked it on a resume: one native
/// session behind several agent ids).
typedef AgentLastTurn = ({
  bool failed,
  AgentRunFailure? reason,
  List<String> sameConversation,
});

/// A host that keeps each agent's last failure (Paseo: `lastError`, and an
/// `error` attention or status that outlive a restart).
abstract interface class AgentTurnFailureGateway {
  AgentLastTurn lastAgentTurn(String sessionID);
}

final _signIn = RegExp(
  r'auth(?:entication)?[ _-]?required|not (?:signed|logged) ?in'
  r'|(?:sign|log) ?in (?:is )?required|please (?:sign|log) ?in'
  r'|(?:sign|log) ?in to (?:use|continue)|login required|no account'
  r'|requires? (?:an? )?(?:account|sign ?in|login)'
  r'|failed to authenticate|oauth (?:session|token) (?:has )?expired'
  r'|please run /login|\b(?:run|use) \S+ (?:login|auth)\b'
  r'|authentication_error|unauthori[sz]ed'
  r'|invalid.?api.?key|missing api.?key|api.?key (?:is )?(?:missing|required|not set)'
  r'|\b401\b',
  caseSensitive: false,
);

final _noCredit = RegExp(
  r'insufficient.?(?:credits?|quota|funds|balance)'
  r'|(?:no|not enough|out of|zero) credits?'
  r'|(?:add|buy|purchase|top ?up) (?:more |some )?credits?'
  r'|credits? (?:are |is )?(?:required|exhausted|depleted)'
  r'|requires? (?:a )?(?:paid|credits)|paid (?:model|plan) (?:is )?required'
  r'|payment required|exceeded your current quota|\b402\b',
  caseSensitive: false,
);

final _modelUnavailable = RegExp(
  r'model\b[^.\n]{0,80}?\b(?:not found|not available|unavailable'
  r'|not supported|unsupported|not allowed|not enabled|does not exist|disabled)'
  r'|(?:unknown|invalid|unsupported) model|model_not_found|no such model'
  r'|no endpoints? found|not allowed to use (?:this|the) model'
  r'|(?:no )?access to (?:this|the) model',
  caseSensitive: false,
);

final _busy = RegExp(r'\bbusy\b|try again later', caseSensitive: false);

/// The kind of [reason], most specific first: a sign-in fixes more than
/// credit does, and both more than another model.
AgentRunFailureKind classifyAgentRunFailure(String reason) {
  final text = reason.trim();
  if (text.isEmpty) return AgentRunFailureKind.unknown;
  if (_signIn.hasMatch(text) && !_noCredit.hasMatch(text)) {
    return AgentRunFailureKind.signIn;
  }
  if (_noCredit.hasMatch(text)) return AgentRunFailureKind.noCredit;
  if (_modelUnavailable.hasMatch(text)) {
    return AgentRunFailureKind.modelUnavailable;
  }
  return switch (classifyAgentError(text)) {
    AgentErrorCause.providerAuth => AgentRunFailureKind.signIn,
    AgentErrorCause.outOfCredit => AgentRunFailureKind.noCredit,
    // A plan's allowance for this model is used up: another model works now.
    AgentErrorCause.usageLimit => AgentRunFailureKind.modelUnavailable,
    AgentErrorCause.rateLimited ||
    AgentErrorCause.providerBusy => AgentRunFailureKind.busy,
    AgentErrorCause.providerUnreachable ||
    AgentErrorCause.connectionDropped ||
    AgentErrorCause.timedOut => AgentRunFailureKind.connectionLost,
    _ when _busy.hasMatch(text) => AgentRunFailureKind.busy,
    _ => AgentRunFailureKind.unknown,
  };
}

final _codeIn = RegExp(
  r'\bcode[:=]\s*"?(-?[A-Za-z0-9_.]{1,40})'
  r'|\b(?:HTTP|status)(?: code)?[: ]\s*(\d{3})\b'
  r'|(-32\d{3})\b',
  caseSensitive: false,
);

/// The error code [reason] names ("code: -32603", "HTTP 402"), or null.
String? agentRunFailureCode(String reason) {
  final match = _codeIn.firstMatch(reason);
  if (match == null) return null;
  return match.group(1) ?? match.group(2) ?? match.group(3);
}
