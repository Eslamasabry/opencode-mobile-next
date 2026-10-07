import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/host_agent_providers.dart';
import 'package:opencode_mobile/paseo/host_agent_providers.dart';

Map<String, dynamic> _entry(String provider, String? error) => {
  'provider': provider,
  'status': 'error',
  'enabled': true,
  'error': ?error,
};

void main() {
  group('issue #95: an agent that is not signed in says so', () {
    test('fx 0.0.12 asking for Vercel AI Gateway access reads as signed '
        'out, not hidden', () {
      final entry = _entry(
        'fx',
        'Failed to fetch models for fx: fx needs access to Vercel AI '
            'Gateway. Run fx login to sign in, fx setup to use an API key, '
            'or set AI_GATEWAY_API_KEY.',
      );
      expect(paseoProviderNeedsSignIn(entry), isTrue);
      final row = paseoHostAgentCatalog([entry]).providers.single;
      expect(row.availability, HostAgentProviderAvailability.needsHostSignIn);
    });

    test('the usual agent CLI phrasings read as signed out', () {
      for (final message in [
        'AuthRequired: run codex login',
        'Authentication required',
        'Not logged in. Please run /login',
        'Please set an API key or sign in with Google',
        'Request failed: 401 Unauthorized',
        'Use qwen auth to configure credentials',
      ]) {
        expect(
          paseoProviderNeedsSignIn(_entry('codex', message)),
          isTrue,
          reason: message,
        );
      }
    });

    test('other failures are not mistaken for sign-in', () {
      for (final message in [
        'spawn ENOENT',
        'Timed out resolving gemini catalogue key',
        'Model list unavailable',
        null,
      ]) {
        expect(
          paseoProviderNeedsSignIn(_entry('gemini', message)),
          isFalse,
          reason: '$message',
        );
      }
    });
  });
}
