import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/connection_failure.dart';

void main() {
  ConnectionFailure d(
    String error, {
    String url = 'http://127.0.0.1:4096',
    bool termux = true,
    bool codex = false,
    bool tokenRequired = false,
    int attempts = 1,
  }) => ConnectionFailure.diagnose(
    error: error,
    baseUrl: url,
    supportsTermux: termux,
    usesConnectionToken: codex,
    requiresTokenReentry: tokenRequired,
    attempts: attempts,
  );

  test('loopback offers retry without assuming Termux hosts the endpoint', () {
    final f = d('Health check failed: connection refused');
    expect(f.title, 'Nothing is listening on this device');
    expect(f.primary, ConnectionFailureAction.retry);
    expect(f.checks.join(' '), contains('Termux'));
    expect(f.checks.join(' '), contains('adb reverse'));
  });

  test('loopback without Termux support retains generic retry', () {
    final f = d('Health check failed: connection refused', termux: false);
    expect(f.primary, ConnectionFailureAction.retry);
    expect(f.checks.join(' '), isNot(contains('Termux')));
  });

  test(
    'a bare "Health check failed" on loopback still gets the loopback advice',
    () {
      final f = d('Health check failed');
      expect(f.title, 'Nothing is listening on this device');
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
    expect(f.checks.join(' '), contains('Codex listener'));
    expect(f.checks.join(' '), isNot(contains('Nothing is listening')));
  });

  test('certificate problems name the certificate', () {
    final f = d(
      'Health check failed: certificate not trusted',
      url: 'https://dev.tail.net',
    );
    expect(f.title, 'Certificate not trusted');
    expect(f.primary, ConnectionFailureAction.changeServer);
  });

  test('remote host refusing is "not reachable" with network checks', () {
    final f = d(
      'Health check failed: connection refused',
      url: 'https://dev.tail.net:4096',
    );
    expect(f.title, 'Server not reachable');
    expect(f.explanation, contains('dev.tail.net:4096'));
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
}
