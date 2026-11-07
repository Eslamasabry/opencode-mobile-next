import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/connection_failure.dart';

void main() {
  ConnectionFailure d(
    String error, {
    String url = 'http://127.0.0.1:4096',
    bool codex = false,
    bool tokenRequired = false,
    int attempts = 1,
  }) => ConnectionFailure.diagnose(
    error: error,
    baseUrl: url,
    usesConnectionToken: codex,
    requiresTokenReentry: tokenRequired,
    attempts: attempts,
  );

  test('loopback offers retry without guessing what hosts the endpoint', () {
    final f = d('Health check failed: connection refused');
    expect(f.title, 'Nothing answered on this phone');
    expect(f.primary, ConnectionFailureAction.retry);
    expect(f.checks.join(' '), isNot(contains('Termux')));
    expect(f.checks.join(' '), contains('adb reverse'));
  });

  test(
    'a bare "Health check failed" on loopback still gets the loopback advice',
    () {
      final f = d('Health check failed');
      expect(f.title, 'Nothing answered on this phone');
    },
  );

  test('401 asks for the password, not a retry', () {
    final f = d('Health check failed (HTTP 401)', url: 'https://dev.tail.net');
    expect(f.title, 'Password rejected');
    expect(f.primary, ConnectionFailureAction.updatePassword);
  });

  test(
    'a missing Codex token asks for the token without a connection guess',
    () {
      final f = d('connection refused', codex: true, tokenRequired: true);
      expect(f.title, 'Connection token required');
      expect(f.primary, ConnectionFailureAction.updateToken);
      expect(f.checks.join(' '), contains('connection token'));
      expect(f.checks.join(' '), isNot(contains('Termux')));
    },
  );

  test('a rejected Codex token has a token recovery action', () {
    final f = d(
      'Codex authentication failed: token rejected',
      url: 'wss://codex.example',
      codex: true,
    );
    expect(f.title, 'Connection token rejected');
    expect(f.primary, ConnectionFailureAction.updateToken);
    expect(f.checks.join(' '), isNot(contains('password')));
  });

  test('Codex local refusal points at its listener or tunnel', () {
    final f = d('connection refused', codex: true);
    expect(f.title, 'Codex listener unavailable');
    expect(f.primary, ConnectionFailureAction.retry);
    final checks = f.checks.join(' ');
    expect(checks, contains('Codex listener'));
    expect(checks, contains('tunnel'));
    expect(checks, isNot(contains('Termux')));
    expect(checks, isNot(contains('opencode')));
    expect(checks, isNot(contains('HTTPS')));
    expect(checks, isNot(contains('pair')));
  });

  test('remote Codex refusal recommends a wss endpoint', () {
    final f = d(
      'connection refused',
      url: 'https://codex.example:443',
      codex: true,
    );
    expect(f.title, 'Codex endpoint unreachable');
    expect(f.checks.join(' '), contains('wss://'));
    expect(f.checks.join(' '), isNot(contains('HTTPS')));
  });

  test('unknown Codex loopback errors stay an honest generic failure', () {
    final f = d('Health check failed', codex: true);
    expect(f.title, 'Could not connect');
    expect(f.checks.join(' '), contains('agent server'));
    expect(f.checks.join(' '), isNot(contains('Nothing answered')));
  });

  test('a changed certificate blocks and asks for explicit review', () {
    final f = d(
      'The server certificate changed.',
      url: 'https://private.example',
    );
    expect(f.title, "The server's certificate changed");
    expect(f.primary, ConnectionFailureAction.reviewCertificate);
    expect(f.explanation, contains('before sending your password'));
    expect(f.checks, contains('Re-check certificate'));
  });

  test('certificate problems name the certificate', () {
    final f = d(
      'Health check failed: certificate not trusted',
      url: 'https://dev.tail.net',
    );
    expect(f.title, 'Certificate not trusted');
    expect(f.primary, ConnectionFailureAction.reviewCertificate);
  });

  test('remote host refusing is "not reachable" with network checks', () {
    final f = d(
      'Health check failed: connection refused',
      url: 'https://dev.tail.net:4096',
    );
    expect(f.title, 'Server not reachable');
    // The address is under Details (the card's), not in the words.
    expect(f.explanation, isNot(contains('dev.tail.net')));
    expect(f.tailnet, isFalse);
    expect(f.primary, ConnectionFailureAction.retry);
  });

  test('timeouts blame the network in between', () {
    final f = d('Health check failed: timed out', url: 'https://dev.tail.net');
    expect(f.title, 'The server did not answer in time');
  });

  test('unhealthy server is a server problem', () {
    final f = d(
      'Server health check reported unhealthy',
      url: 'https://dev.tail.net',
    );
    expect(f.title, 'The server answered with an error');
  });

  test('three attempts adds the "retrying will not start a server" line', () {
    final f = d('Health check failed: connection refused', attempts: 3);
    expect(f.checks.last, contains('Tried 3 times'));
    expect(
      d('Health check failed: connection refused', attempts: 1).checks.last,
      isNot(contains('Tried')),
    );
  });

  test('the raw error is kept verbatim for the details expander', () {
    const raw = 'Health check failed: SocketException: weird';
    expect(d(raw).rawError, raw);
  });

  test('a server at the address Termux used gets the general loopback '
      'advice: the app no longer starts anything there', () {
    expect(
      ConnectionFailure.diagnose(
        error: 'Connection refused',
        baseUrl: 'http://127.0.0.1:4096',
      ).primary,
      ConnectionFailureAction.retry,
    );
  });

  test('OpenCode inside the app: starting it is the fix, and nothing is '
      'said about Termux, tunnels or passwords', () {
    for (final error in const [
      'Connection refused',
      'Health check timed out',
      'HTTP 401 Unauthorized',
    ]) {
      final failure = ConnectionFailure.diagnose(
        error: error,
        baseUrl: 'http://127.0.0.1:4097',
        inAppServer: true,
        attempts: 4,
      );
      expect(failure.primary, ConnectionFailureAction.startPhoneServer);
      expect(failure.checks, isEmpty, reason: error);
      expect(
        '${failure.title} ${failure.explanation}',
        isNot(
          matches(RegExp('Termux|adb|tunnel|password', caseSensitive: false)),
        ),
        reason: error,
      );
    }
    expect(
      ConnectionFailure.diagnose(
        error: 'Connection refused',
        baseUrl: 'http://127.0.0.1:4097',
        inAppServer: true,
      ).title,
      'OpenCode inside the app is stopped',
    );
    expect(
      ConnectionFailure.diagnose(
        error: 'the server stopped',
        baseUrl: 'http://127.0.0.1:4097',
        inAppServer: true,
        inAppStartFailed: true,
      ).title,
      'OpenCode inside the app did not start',
    );
  });

  test('a Tailscale address that did not answer says to check Tailscale', () {
    final f = d(
      'Health check failed: connection refused',
      url: 'http://100.101.102.103:4096',
    );
    expect(f.tailnet, isTrue);
    expect(f.checks.first, contains('Tailscale'));
    expect(ConnectionFailure.isTailnetHost('laptop.tail1234.ts.net'), isTrue);
    expect(ConnectionFailure.isTailnetHost('100.63.0.1'), isFalse);
    expect(ConnectionFailure.isTailnetHost('192.168.1.2'), isFalse);
    // A refused sign-in means Tailscale did its part.
    expect(
      d('Health check failed (HTTP 401)', url: 'http://100.64.0.2').tailnet,
      isFalse,
    );
  });
}
