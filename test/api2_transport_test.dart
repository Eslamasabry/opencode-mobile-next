import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/transport.dart';

class _RealHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('normalizes server roots with trailing slashes and /api suffixes', () {
    expect(
      Api2Transport.normalizeServerRoot('http://host:4097'),
      'http://host:4097',
    );
    expect(
      Api2Transport.normalizeServerRoot('http://host:4097/'),
      'http://host:4097',
    );
    expect(
      Api2Transport.normalizeServerRoot('http://host:4097/api'),
      'http://host:4097',
    );
    expect(
      Api2Transport.normalizeServerRoot('http://host:4097/api/'),
      'http://host:4097',
    );
  });

  test('builds Basic auth from the opencode username and password', () {
    final transport = Api2Transport(
      baseUrl: 'http://host:4097',
      password: 'secret',
    );
    expect(transport.basicToken, base64Encode(utf8.encode('opencode:secret')));
    expect(
      transport.authorizationHeader,
      'Basic ${base64Encode(utf8.encode('opencode:secret'))}',
    );
    expect(transport.apiBase, 'http://host:4097/api');
    transport.close();
  });

  test('auth_token query fallback carries the Basic token url-encoded', () {
    final transport = Api2Transport(
      baseUrl: 'http://host:4097',
      password: 'p+w/d==',
    );
    final uri = transport.authTokenUri(
      '/event',
      query: {'location[directory]': '/work/app'},
    );
    expect(uri.path, '/api/event');
    expect(uri.queryParameters['auth_token'], transport.basicToken);
    expect(uri.queryParameters['location[directory]'], '/work/app');
    expect(uri.toString(), contains('auth_token='));
    expect(uri.toString(), isNot(contains(transport.password)));

    final upgraded = transport.withAuthToken(
      Uri.parse('ws://host:4097/api/pty/pty_1/connect?cursor=-1'),
    );
    expect(upgraded.queryParameters['cursor'], '-1');
    expect(upgraded.queryParameters['auth_token'], transport.basicToken);
    transport.close();
  });

  test('401 maps to Api2AuthRequired even with an empty body', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        request.response.statusCode = HttpStatus.unauthorized;
        request.response.headers.set(
          'www-authenticate',
          'Basic realm="Secure Area"',
        );
        await request.response.close();
      });
      final transport = Api2Transport(
        baseUrl: 'http://${server.address.host}:${server.port}',
        password: 'wrong',
      );
      try {
        await expectLater(
          transport.health(),
          throwsA(isA<Api2AuthRequired>()),
        );
      } finally {
        transport.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('tagged error envelopes surface tag, status, and extras', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        request.response.statusCode = HttpStatus.notFound;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            '_tag': 'SessionNotFoundError',
            'sessionID': 'ses_missing',
            'message': 'Session not found: ses_missing',
          }),
        );
        await request.response.close();
      });
      final transport = Api2Transport(
        baseUrl: 'http://${server.address.host}:${server.port}',
        password: 'pw',
      );
      try {
        await transport.getJson('/session/ses_missing');
        fail('expected Api2RequestError');
      } on Api2RequestError catch (e) {
        expect(e.statusCode, 404);
        expect(e.tag, 'SessionNotFoundError');
        expect(e.isNotFound, isTrue);
        expect(e.detail('sessionID'), 'ses_missing');
        expect(e.message, contains('Session not found'));
      } finally {
        transport.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('503 boot bodies map to Api2Unavailable', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        request.response.statusCode = HttpStatus.serviceUnavailable;
        request.response.headers.contentType = ContentType.json;
        request.response.write(jsonEncode({'code': 'service_starting'}));
        await request.response.close();
      });
      final transport = Api2Transport(
        baseUrl: 'http://${server.address.host}:${server.port}',
        password: 'pw',
      );
      try {
        await transport.getJson('/session');
        fail('expected Api2Unavailable');
      } on Api2Unavailable catch (e) {
        expect(e.statusCode, 503);
        expect(e.tag, 'service_starting');
      } finally {
        transport.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('connection refusal maps to Api2NetworkError', () async {
    await HttpOverrides.runZoned(() async {
      final transport = Api2Transport(
        baseUrl: 'http://127.0.0.1:1',
        password: 'pw',
      );
      try {
        await expectLater(
          transport.getJson('/health'),
          throwsA(isA<Api2NetworkError>()),
        );
      } finally {
        transport.close();
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('health sends Basic auth and parses the payload', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      String? authHeader;
      String? path;
      server.listen((request) async {
        authHeader = request.headers.value('authorization');
        path = request.uri.path;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            'healthy': true,
            'version': '0.0.0-beta-18600',
            'pid': 4242,
          }),
        );
        await request.response.close();
      });
      final transport = Api2Transport(
        baseUrl: 'http://${server.address.host}:${server.port}',
        password: 'pw',
      );
      try {
        final health = await transport.checkServer();
        expect(health.healthy, isTrue);
        expect(health.version, '0.0.0-beta-18600');
        expect(health.pid, 4242);
        expect(path, '/api/health');
        expect(authHeader, 'Basic ${base64Encode(utf8.encode('opencode:pw'))}');
      } finally {
        transport.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });
}
