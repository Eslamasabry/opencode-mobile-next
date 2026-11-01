import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/network/platform_websocket.dart';

/// Native `dart:io` socket behaviour not covered by the shared streaming
/// contract: a refused address fails fast with a message that never leaks
/// the ticket, and the production seam points at the real connector.
void main() {
  test('the production connector seam is the platform connector', () {
    expect(platformWebSocketConnector, equals(connectPlatformWebSocket));
  });

  test('a refused socket fails with a ticket-free message', () async {
    final uri = Uri.parse(
      'ws://127.0.0.1:1/pty/x/connect?ticket=secret-ticket',
    );
    final stopwatch = Stopwatch()..start();
    await expectLater(
      connectPlatformWebSocket(uri, timeout: const Duration(seconds: 5)),
      throwsA(
        isA<PlatformWebSocketException>().having(
          (error) => error.message,
          'message',
          allOf(contains('127.0.0.1:1'), isNot(contains('secret-ticket'))),
        ),
      ),
    );
    expect(stopwatch.elapsed, lessThan(const Duration(seconds: 6)));
  });

  test(
    'a server that never upgrades is bounded by the connect timeout',
    () async {
      final server = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close());
      // Accept and hold the connection without ever answering.
      final held = <Socket>[];
      server.listen(held.add);
      addTearDown(() async {
        for (final socket in held) {
          socket.destroy();
        }
      });
      final uri = Uri.parse(
        'ws://127.0.0.1:${server.port}/pty/x/connect?ticket=secret-ticket',
      );
      await expectLater(
        connectPlatformWebSocket(
          uri,
          timeout: const Duration(milliseconds: 500),
        ),
        throwsA(
          isA<PlatformWebSocketException>().having(
            (error) => error.message,
            'message',
            allOf(contains('timed out'), isNot(contains('secret-ticket'))),
          ),
        ),
      );
    },
  );

  test('endpoint descriptions drop the query', () {
    expect(
      describeSocketEndpoint(Uri.parse('wss://host.example:4097/x?ticket=t')),
      'host.example:4097',
    );
  });
}
