/// Turning what a server or a model provider says went wrong into what a
/// person needs to know.
///
/// Agent errors reach the app as whatever text the runtime produced:
/// `ECONNRESET: The socket connection was closed unexpectedly. For more
/// information, pass `verbose: true` in the second argument to fetch()`.
/// That is a message from one programmer to another. The reader of a chat on
/// a phone needs three things instead: what happened, whether it will sort
/// itself out, and what they can do. This file recognises the causes that
/// account for nearly all such errors; the wording lives in the
/// localizations, and the original text is always kept for a Details view.
library;

enum AgentErrorCause {
  /// The link between the server and the model provider broke mid-request.
  connectionDropped,

  /// The provider did not answer in time.
  timedOut,

  /// The server could not reach the provider at all (DNS, refused, offline).
  providerUnreachable,

  /// The request itself was too big for the connection or the provider.
  requestTooLarge,

  /// The provider is limiting the request rate.
  rateLimited,

  /// The provider is overloaded or temporarily unavailable.
  providerBusy,

  /// The provider account has no credit or quota left.
  outOfCredit,

  /// A plan's usage allowance for this model is used up for now (OpenCode's
  /// free models and Go plan say "Free usage limit reached" / "Go usage
  /// limit exceeded"). It resets with the plan's window; another model works
  /// meanwhile.
  usageLimit,

  /// The provider needs a missing or rejected credential replaced.
  providerAuth,

  /// The machine running the server has no disk space left.
  serverDiskFull,
}

/// Causes a server may retry. Actual retry state must come from the server.
const selfHealingAgentErrors = {
  AgentErrorCause.connectionDropped,
  AgentErrorCause.timedOut,
  AgentErrorCause.rateLimited,
  AgentErrorCause.providerBusy,
};

final _rules = <(RegExp, AgentErrorCause)>[
  // Order matters: the most specific signal first. A rate-limit reply often
  // also mentions a status code or a retry, and "overflow" also says socket.
  (
    RegExp(
      r'queue overflow|payload too large|request entity too large|\b413\b'
      r'|request too large|body exceeded',
      caseSensitive: false,
    ),
    AgentErrorCause.requestTooLarge,
  ),
  // A plan's allowance used up: before credit and rate rules, since the
  // same reply can carry a 429 or a "subscribe" link.
  (
    RegExp(
      r'usage limit (?:reached|exceeded)|usagelimiterror'
      r'|(?:free|plan|daily|weekly|monthly|\d+.?hour) limit (?:reached|exceeded)',
      caseSensitive: false,
    ),
    AgentErrorCause.usageLimit,
  ),
  // Providers also use HTTP 429 for exhausted credit. Waiting cannot fix it.
  (
    RegExp(
      r'insufficient.?(quota|credit|funds|balance)|out of credit'
      r'|exceeded your current quota|billing|payment required|\b402\b',
      caseSensitive: false,
    ),
    AgentErrorCause.outOfCredit,
  ),
  (
    RegExp(r'rate.?limit|too many requests|\b429\b', caseSensitive: false),
    AgentErrorCause.rateLimited,
  ),
  (
    RegExp(
      r'invalid.?api.?key|incorrect api key|api.?key (?:is )?missing'
      r'|missing api.?key|authentication (?:failed|required)'
      r'|unauthorized|\b401\b',
      caseSensitive: false,
    ),
    AgentErrorCause.providerAuth,
  ),
  (
    RegExp(
      r'overloaded|service unavailable|bad gateway|gateway time-?out'
      r'|temporarily unavailable|\b50[234]\b|\b529\b',
      caseSensitive: false,
    ),
    AgentErrorCause.providerBusy,
  ),
  (RegExp(r'ENOSPC|no space left on device'), AgentErrorCause.serverDiskFull),
  (
    RegExp(
      r'ETIMEDOUT|ESOCKETTIMEDOUT|timed? ?out|deadline exceeded',
      caseSensitive: false,
    ),
    AgentErrorCause.timedOut,
  ),
  (
    RegExp(
      r'ECONNREFUSED|ENOTFOUND|EAI_AGAIN|EHOSTUNREACH|ENETUNREACH'
      r'|getaddrinfo|fetch failed|unable to connect|network is unreachable',
      caseSensitive: false,
    ),
    AgentErrorCause.providerUnreachable,
  ),
  (
    RegExp(
      r'ECONNRESET|EPIPE|ECONNABORTED|socket hang up'
      r'|socket connection was closed|connection (was )?(closed|reset|lost)'
      r'|premature close|stream (closed|ended) unexpectedly|terminated',
      caseSensitive: false,
    ),
    AgentErrorCause.connectionDropped,
  ),
];

/// The recognised cause of [raw], or null for a typed or generic fallback.
/// Unrecognised server text still belongs under Details.
AgentErrorCause? classifyAgentError(String raw) {
  if (raw.trim().isEmpty) return null;
  for (final (pattern, cause) in _rules) {
    if (pattern.hasMatch(raw)) return cause;
  }
  return null;
}

final _developerAdvice = RegExp(
  r'\s*For more information, pass `?verbose: ?true`? in the second argument '
  r'to fetch\(\)\.?',
  caseSensitive: false,
);
final _errnoPrefix = RegExp(r'^(E[A-Z]{3,}|[A-Za-z]+Error)\s*:\s*');

/// [text] without the parts only its author could use: advice about library
/// flags, and the errno or exception class in front of the sentence.
String cleanAgentErrorText(String text) {
  var cleaned = text.replaceAll(_developerAdvice, '').trim();
  final prefixed = _errnoPrefix.firstMatch(cleaned);
  if (prefixed != null && cleaned.length > prefixed.end) {
    cleaned = cleaned.substring(prefixed.end).trim();
  }
  return cleaned.isEmpty ? text.trim() : cleaned;
}
