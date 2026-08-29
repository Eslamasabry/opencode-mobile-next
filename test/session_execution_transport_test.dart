import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';

class _RealHttpOverrides extends HttpOverrides {}

typedef _Request = ({String method, Uri uri, Object? body});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'command uses generated wire contract while shell retains variant',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <_Request>[];
        server.listen((request) async {
          requests.add((
            method: request.method,
            uri: request.uri,
            body: jsonDecode(await utf8.decoder.bind(request).join()),
          ));
          request.response.statusCode = HttpStatus.noContent;
          await request.response.close();
        });

        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        )..setLocation(directory: '/work/acme', workspace: 'workspace-1');
        final model = ModelRef(
          providerID: 'zai-coding-plan',
          modelID: 'glm-5.2',
        );

        try {
          await api.slashCommand(
            'session-1',
            'review',
            '--base main',
            model: model,
            variant: 'high',
          );
          await api.shell(
            'session-1',
            command: 'flutter test',
            agent: 'build',
            model: model,
            variant: 'max',
          );

          expect(requests, hasLength(2));
          expect(requests[0].method, 'POST');
          expect(requests[0].uri.path, '/session/session-1/command');
          expect(requests[0].uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'workspace-1',
          });
          expect(requests[0].body, {
            'model': 'zai-coding-plan/glm-5.2',
            'arguments': '--base main',
            'command': 'review',
            'variant': 'high',
          });

          expect(requests[1].method, 'POST');
          expect(requests[1].uri.path, '/session/session-1/shell');
          expect(requests[1].uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'workspace-1',
          });
          expect(requests[1].body, {
            'agent': 'build',
            'model': {'providerID': 'zai-coding-plan', 'modelID': 'glm-5.2'},
            'variant': 'max',
            'command': 'flutter test',
          });
        } finally {
          api.close();
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test('declared command errors retain product-facing details', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        await request.drain<void>();
        request.response.statusCode = HttpStatus.notFound;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            '_tag': 'CommandNotFoundError',
            'requestID': 'request-command-1',
            'message': 'command is unavailable',
          }),
        );
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        await expectLater(
          api.slashCommand('session-1', 'missing', ''),
          throwsA(
            isA<ApiException>()
                .having((error) => error.statusCode, 'statusCode', 404)
                .having(
                  (error) => error.errorTag,
                  'errorTag',
                  'CommandNotFoundError',
                )
                .having(
                  (error) => error.requestID,
                  'requestID',
                  'request-command-1',
                )
                .having(
                  (error) => error.message,
                  'message',
                  contains('command is unavailable'),
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
