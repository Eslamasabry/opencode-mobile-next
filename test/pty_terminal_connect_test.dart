import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ProductException;
import 'package:opencode_mobile/network/platform_websocket.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';

import 'network/support/fake_platform_websocket.dart';

class _RealHttpOverrides extends HttpOverrides {}

/// One recorded ticket request.
class _TicketRequest {
  _TicketRequest(this.path, this.headers);

  final String path;
  final HttpHeaders headers;
}

/// In a browser the gateway obtains a single-use ticket over HTTP (with Basic
/// auth and the CSRF header) and hands a ticket-only `ws://` URL to the
/// platform connector: the password never enters the socket URL, and the
/// channel wraps whatever socket the browser provides. Native builds keep
/// their own `dart:io` socket with the certificate checks, so these tests pin
/// the browser build.
void main() {
  late HttpServer server;
  late List<_TicketRequest> tickets;
  late FakePlatformWebSocket socket;
  Uri? connected;

  setUp(() async {
    tickets = [];
    socket = FakePlatformWebSocket();
    connected = null;
    platformWebSocketConnector =
        (Uri uri, {Duration timeout = platformWebSocketConnectTimeout}) async {
          connected = uri;
          return socket;
        };
    debugPlatformCapabilities = const PlatformCapabilities.browser();
    addTearDown(() => debugPlatformCapabilities = null);
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    server.listen((request) async {
      tickets.add(_TicketRequest(request.uri.path, request.headers));
      final response = request.response;
      response.headers.contentType = ContentType.json;
      if (request.uri.path == '/api/pty/pty_1/connect-token') {
        response.write(
          jsonEncode({
            'data': {'ticket': 'tkt-v2'},
          }),
        );
      } else {
        response.statusCode = HttpStatus.notFound;
      }
      await response.close();
    });
  });

  tearDown(() async {
    platformWebSocketConnector = connectPlatformWebSocket;
    await server.close(force: true);
  });

  String baseUrl() => 'http://${server.address.address}:${server.port}';

  test(
    'a browser connects the terminal with a ticket-only socket URL',
    () async {
      await HttpOverrides.runZoned(() async {
        final client = Api2Client.connect(
          baseUrl: baseUrl(),
          password: 'the-password',
          directory: '/work',
        );
        addTearDown(client.close);
        final gateway = Api2OperationsGateway(client: client);
        final channel = await gateway.connectTerminal('pty_1');

        expect(tickets.single.path, '/api/pty/pty_1/connect-token');
        expect(tickets.single.headers.value('x-opencode-ticket'), '1');
        expect(
          tickets.single.headers.value('authorization'),
          startsWith('Basic '),
        );

        final uri = connected!;
        expect(uri.scheme, 'ws');
        expect(uri.path, '/api/pty/pty_1/connect');
        expect(uri.queryParameters['ticket'], 'tkt-v2');
        expect(uri.queryParameters['location[directory]'], '/work');
        expect(uri.toString(), isNot(contains('the-password')));
        expect(uri.queryParameters.containsKey('auth_token'), isFalse);

        expect(channel.cursor, 0);
        channel.write('pwd\n');
        expect(socket.sent, ['pwd\n']);
        await channel.close();
        expect(socket.closed, isTrue);
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'a socket the platform cannot open surfaces as a product error',
    () async {
      platformWebSocketConnector =
          (Uri uri, {Duration timeout = platformWebSocketConnectTimeout}) =>
              Future.error(
                const PlatformWebSocketException(
                  'The browser could not open the terminal socket.',
                ),
              );
      await HttpOverrides.runZoned(() async {
        final client = Api2Client.connect(
          baseUrl: baseUrl(),
          password: 'the-password',
          directory: '/work',
        );
        addTearDown(client.close);
        final gateway = Api2OperationsGateway(client: client);
        await expectLater(
          gateway.connectTerminal('pty_1'),
          throwsA(
            isA<ProductException>()
                .having(
                  (error) => error.message,
                  'message',
                  'Could not connect to the terminal',
                )
                .having(
                  (error) => error.cause,
                  'cause',
                  isA<PlatformWebSocketException>(),
                ),
          ),
        );
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );
}
