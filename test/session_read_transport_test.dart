import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';

class _RealHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'session reads use generated contracts without losing loose data',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <Uri>[];
        server.listen((request) async {
          requests.add(request.uri);
          request.response.headers.contentType = ContentType.json;
          switch (request.uri.path) {
            case '/session':
              request.response.write(
                jsonEncode([
                  {
                    'id': 'session-1',
                    'title': 'Loose session',
                    'directory': '/work/acme',
                    'time': {'created': 1000, 'updated': 2000},
                  },
                ]),
              );
            case '/session/status':
              request.response.write(
                jsonEncode({
                  'session-1': {'type': 'busy'},
                  'session-2': {
                    'type': 'retry',
                    'attempt': 2,
                    'message': 'retrying',
                    'next': 3000,
                  },
                }),
              );
            case '/session/session-1':
              request.response.write(
                jsonEncode({
                  'id': 'session-1',
                  'title': 'Loose session',
                  'directory': '/work/acme',
                  'time': {'created': 1000, 'updated': 2000},
                }),
              );
            case '/session/session-1/message':
              request.response.write(
                jsonEncode([
                  {
                    'info': {
                      'id': 'message-1',
                      'sessionID': 'session-1',
                      'role': 'assistant',
                      'time': {'created': 1000, 'completed': 2000},
                    },
                    'parts': [
                      {
                        'id': 'part-1',
                        'sessionID': 'session-1',
                        'messageID': 'message-1',
                        'type': 'tool',
                        'tool': 'custom_plugin',
                        'state': {
                          'status': 'completed',
                          'input': {
                            'pluginField': {'nested': true},
                          },
                          'output': {'result': 'ok', 'extra': 7},
                          'metadata': {'pluginSpecific': true},
                        },
                      },
                    ],
                  },
                ]),
              );
            default:
              request.response.statusCode = HttpStatus.notFound;
          }
          await request.response.close();
        });

        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        )..setLocation(directory: '/work/acme', workspace: 'workspace-1');
        try {
          final sessions = await api.sessions();
          final statuses = await api.sessionStatuses();
          final session = await api.session('session-1');
          final messages = await api.messages('session-1');

          expect(sessions.single.title, 'Loose session');
          expect(statuses, {'session-1': 'busy', 'session-2': 'retry'});
          expect(session.directory, '/work/acme');
          final tool = messages.single.parts.single;
          expect(tool.toolName, 'custom_plugin');
          expect(tool.toolState.input['pluginField'], {'nested': true});
          expect(tool.toolState.outputValue, {'result': 'ok', 'extra': 7});
          expect(tool.toolState.metadata?['pluginSpecific'], isTrue);
          expect(requests.map((uri) => uri.path), [
            '/session',
            '/session/status',
            '/session/session-1',
            '/session/session-1/message',
          ]);
          for (final uri in requests) {
            expect(uri.queryParameters, {
              'directory': '/work/acme',
              'workspace': 'workspace-1',
              if (uri.path.endsWith('/message')) 'limit': '100',
            });
          }
        } finally {
          api.close();
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test('session read errors retain generated OpenCode details', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        await request.drain<void>();
        final missing = request.uri.path.contains('session-1');
        request.response.statusCode = missing
            ? HttpStatus.notFound
            : HttpStatus.badRequest;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            '_tag': missing ? 'SessionNotFoundError' : 'InvalidRequestError',
            'requestID': 'request-${request.uri.path.replaceAll('/', '-')}',
            'message': 'read rejected',
          }),
        );
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        await expectLater(
          api.sessions(),
          throwsA(
            isA<ApiException>()
                .having((error) => error.statusCode, 'statusCode', 400)
                .having(
                  (error) => error.errorTag,
                  'errorTag',
                  'InvalidRequestError',
                ),
          ),
        );
        await expectLater(
          api.sessionStatuses(),
          throwsA(
            isA<ApiException>().having(
              (error) => error.statusCode,
              'statusCode',
              400,
            ),
          ),
        );
        await expectLater(
          api.session('session-1'),
          throwsA(
            isA<ApiException>()
                .having((error) => error.statusCode, 'statusCode', 404)
                .having(
                  (error) => error.errorTag,
                  'errorTag',
                  'SessionNotFoundError',
                ),
          ),
        );
        await expectLater(
          api.messages('session-1'),
          throwsA(
            isA<ApiException>().having(
              (error) => error.statusCode,
              'statusCode',
              404,
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
