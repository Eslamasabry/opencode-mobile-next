import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_error_text.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/agent_error_words.dart';

void main() {
  final en = lookupAppLocalizations(const Locale('en'));
  const reset =
      'ECONNRESET: The socket connection was closed unexpectedly. For more '
      'information, pass `verbose: true` in the second argument to fetch()';

  test('runtime errors are recognised by cause', () {
    final cases = {
      reset: AgentErrorCause.connectionDropped,
      'socket hang up': AgentErrorCause.connectionDropped,
      'WebSocket inbound queue overflow': AgentErrorCause.requestTooLarge,
      '413 Payload Too Large': AgentErrorCause.requestTooLarge,
      'Error: 429 Too Many Requests': AgentErrorCause.rateLimited,
      'rate_limit_exceeded: slow down': AgentErrorCause.rateLimited,
      'Overloaded': AgentErrorCause.providerBusy,
      '503 Service Unavailable': AgentErrorCause.providerBusy,
      'connect ETIMEDOUT 1.2.3.4:443': AgentErrorCause.timedOut,
      'The operation timed out.': AgentErrorCause.timedOut,
      'getaddrinfo ENOTFOUND api.openai.com':
          AgentErrorCause.providerUnreachable,
      'fetch failed': AgentErrorCause.providerUnreachable,
      'You exceeded your current quota, please check your plan':
          AgentErrorCause.outOfCredit,
      'ENOSPC: no space left on device, write': AgentErrorCause.serverDiskFull,
      // OpenCode's own plan limits, seen on the owner's phone and emulator.
      'Free usage limit reached. Subscribe to OpenCode Go to keep going: '
              'https://opencode.ai/go':
          AgentErrorCause.usageLimit,
      'Go usage limit exceeded': AgentErrorCause.usageLimit,
    };
    for (final entry in cases.entries) {
      expect(classifyAgentError(entry.key), entry.value, reason: entry.key);
    }
    // A rate limit that also mentions a dropped socket is a rate limit.
    expect(
      classifyAgentError('429 rate limit; connection closed'),
      AgentErrorCause.rateLimited,
    );
    expect(classifyAgentError('The file lib/main.dart does not exist'), isNull);
    expect(classifyAgentError(''), isNull);
  });

  test('a recognised cause is said in plain words, with what happens next', () {
    final words = agentErrorWords(reset, en);
    expect(words.headline, 'The connection to the model dropped.');
    expect(words.hint, isNot(contains('retries by itself')));
    expect(words.humanized, isTrue);
    expect(words.headline, isNot(contains('ECONNRESET')));
    expect(words.headline, isNot(contains('fetch()')));
  });

  test('unknown failures keep technical text out of the headline', () {
    for (final locale in [const Locale('en'), const Locale('ar')]) {
      final strings = lookupAppLocalizations(locale);
      final words = agentErrorWords(
        'TypeError: Cannot read properties of undefined; api_key=private-value',
        strings,
      );
      expect(words.headline, isNot(contains('TypeError')));
      expect(words.headline, isNot(contains('undefined')));
      expect(words.headline, isNot(contains('private-value')));
      expect(words.hint, isNotNull);
      expect(words.humanized, isFalse);
    }
  });

  test('exhausted credit takes precedence over an HTTP 429', () {
    expect(
      classifyAgentError('HTTP 429: insufficient_quota; check billing'),
      AgentErrorCause.outOfCredit,
    );
  });

  test('missing and rejected provider keys have a sign-in recovery', () {
    for (final raw in [
      '401 Unauthorized: invalid_api_key',
      'API key is missing',
      'Incorrect API key provided',
      'AuthenticationError: authentication failed',
    ]) {
      expect(classifyAgentError(raw)?.name, 'providerAuth', reason: raw);
      final words = agentErrorWords(raw, en);
      expect(words.headline, 'The model provider needs a valid sign-in.');
      expect(words.hint, contains('provider settings'));
    }
  });

  test('terminal errors do not promise an automatic retry', () {
    for (final raw in [
      '429 Too Many Requests',
      '503 Service Unavailable',
      'ETIMEDOUT',
      'ECONNRESET',
    ]) {
      expect(
        agentErrorWords(raw, en).hint,
        isNot(contains('retries by itself')),
      );
    }
  });

  test('every cause has Arabic wording too', () {
    final ar = lookupAppLocalizations(const Locale('ar'));
    final words = agentErrorWords(reset, ar);
    expect(words.headline, isNot(en.agentErrorConnectionDropped));
    expect(words.hint, isNotNull);
  });
}
