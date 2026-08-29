import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/mcp_oauth.dart';

class _RealHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('discovers only explicit HTTP loopback OAuth redirects', () {
    final authorization = Uri.parse(
      'https://login.example.com/authorize?redirect_uri='
      'http%3A%2F%2F127.0.0.1%3A19876%2Fmcp%2Foauth%2Fcallback',
    );
    expect(
      mcpLoopbackRedirect(authorization),
      Uri.parse('http://127.0.0.1:19876/mcp/oauth/callback'),
    );

    for (final redirect in [
      'https://127.0.0.1:19876/callback',
      'http://example.com:19876/callback',
      'http://user@127.0.0.1:19876/callback',
      'http://127.0.0.1/callback',
    ]) {
      expect(
        mcpLoopbackRedirect(
          Uri.parse(
            'https://login.example.com/authorize?redirect_uri=${Uri.encodeQueryComponent(redirect)}',
          ),
        ),
        isNull,
        reason: redirect,
      );
    }
  });

  test('manual callback parsing validates state before exposing the code', () {
    expect(
      parseMcpAuthorizationCode(
        'http://127.0.0.1:19876/mcp/oauth/callback?code=code-1&state=state-1',
        expectedState: 'state-1',
      ),
      'code-1',
    );
    expect(
      parseMcpAuthorizationCode('raw-code-2', expectedState: 'state-1'),
      'raw-code-2',
    );
    expect(
      () => parseMcpAuthorizationCode(
        'http://127.0.0.1:19876/mcp/oauth/callback?code=code-1&state=wrong',
        expectedState: 'state-1',
      ),
      throwsA(
        isA<McpOAuthCallbackException>().having(
          (error) => error.message,
          'message',
          contains('state does not match'),
        ),
      ),
    );
  });

  test(
    'loopback listener rejects a mismatched state then captures one code',
    () async {
      await HttpOverrides.runZoned(() async {
        final listener = await McpOAuthLoopbackListener.bind(
          redirect: Uri.parse('http://127.0.0.1:0/mcp/oauth/callback'),
          expectedState: 'state-1',
        );
        final client = HttpClient();
        try {
          final rejected = await client.getUrl(
            listener.callbackUri.replace(
              queryParameters: {'code': 'wrong-code', 'state': 'wrong-state'},
            ),
          );
          final rejectedResponse = await rejected.close();
          await rejectedResponse.drain<void>();
          expect(rejectedResponse.statusCode, HttpStatus.badRequest);

          final accepted = await client.getUrl(
            listener.callbackUri.replace(
              queryParameters: {'code': 'code-1', 'state': 'state-1'},
            ),
          );
          final acceptedResponse = await accepted.close();
          await acceptedResponse.drain<void>();
          expect(acceptedResponse.statusCode, HttpStatus.ok);
          expect(await listener.code, 'code-1');
        } finally {
          client.close(force: true);
          await listener.close();
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );
}
