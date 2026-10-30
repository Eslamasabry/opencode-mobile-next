import '../domain/agent_error_text.dart';
import '../l10n/app_localizations.dart';

/// What to show for an agent error: a sentence a person can use, and, when
/// the cause is recognised, what happens next or what to do. [humanized] is
/// true when a cause was recognised, so it can override a typed error's
/// general headline. All copy is app-authored; server text belongs in Details.
typedef AgentErrorWords = ({String headline, String? hint, bool humanized});

AgentErrorWords agentErrorWords(String raw, AppLocalizations l10n) {
  final words = switch (classifyAgentError(raw)) {
    AgentErrorCause.connectionDropped => (
      l10n.agentErrorConnectionDropped,
      l10n.agentErrorConnectionDroppedHint,
    ),
    AgentErrorCause.timedOut => (
      l10n.agentErrorTimedOut,
      l10n.agentErrorTimedOutHint,
    ),
    AgentErrorCause.providerUnreachable => (
      l10n.agentErrorProviderUnreachable,
      l10n.agentErrorProviderUnreachableHint,
    ),
    AgentErrorCause.requestTooLarge => (
      l10n.agentErrorRequestTooLarge,
      l10n.agentErrorRequestTooLargeHint,
    ),
    AgentErrorCause.rateLimited => (
      l10n.agentErrorRateLimited,
      l10n.agentErrorRateLimitedHint,
    ),
    AgentErrorCause.providerBusy => (
      l10n.agentErrorProviderBusy,
      l10n.agentErrorProviderBusyHint,
    ),
    AgentErrorCause.outOfCredit => (
      l10n.agentErrorOutOfCredit,
      l10n.agentErrorOutOfCreditHint,
    ),
    AgentErrorCause.usageLimit => (
      l10n.agentErrorUsageLimit,
      l10n.agentErrorUsageLimitHint,
    ),
    AgentErrorCause.providerAuth => (
      l10n.agentErrorProviderAuth,
      l10n.agentErrorProviderAuthHint,
    ),
    AgentErrorCause.serverDiskFull => (
      l10n.agentErrorServerDiskFull,
      l10n.agentErrorServerDiskFullHint,
    ),
    null => null,
  };
  if (words != null) {
    return (headline: words.$1, hint: words.$2, humanized: true);
  }
  // Unknown technical text belongs in Details too. Keep humanized false
  // so callers with a typed error can still choose its more specific words.
  return (
    headline: l10n.agentErrorUnknown,
    hint: l10n.agentErrorUnknownHint,
    humanized: false,
  );
}
