import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';

class _RealHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('abort uses the generated location-scoped contract', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      late String method;
      late Uri uri;
      server.listen((request) async {
        method = request.method;
        uri = request.uri;
        await request.drain<void>();
        request.response.statusCode = HttpStatus.noContent;
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      )..setLocation(directory: '/work/acme', workspace: 'workspace-1');
      try {
        await api.abort('session-1');

        expect(method, 'POST');
        expect(uri.path, '/session/session-1/abort');
        expect(uri.queryParameters, {
          'directory': '/work/acme',
          'workspace': 'workspace-1',
        });
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('declared abort errors retain product-facing details', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        await request.drain<void>();
        request.response.statusCode = HttpStatus.badRequest;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            '_tag': 'InvalidRequestError',
            'requestID': 'request-abort-1',
            'message': 'session cannot be stopped',
          }),
        );
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        await expectLater(
          api.abort('session-1'),
          throwsA(
            isA<ApiException>()
                .having((error) => error.statusCode, 'statusCode', 400)
                .having(
                  (error) => error.errorTag,
                  'errorTag',
                  'InvalidRequestError',
                )
                .having(
                  (error) => error.requestID,
                  'requestID',
                  'request-abort-1',
                )
                .having(
                  (error) => error.message,
                  'message',
                  contains('session cannot be stopped'),
                ),
          ),
        );
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });
}
