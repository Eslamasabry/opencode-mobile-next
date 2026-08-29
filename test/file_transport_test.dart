import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';

class _RealHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('file surfaces use generated location-scoped contracts', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final requests = <HttpRequest>[];
      server.listen((request) async {
        requests.add(request);
        request.response.headers.contentType = ContentType.json;
        switch (request.uri.path) {
          case '/file':
            request.response.write(
              jsonEncode([
                {
                  'name': 'main.dart',
                  'path': 'lib/main.dart',
                  'absolute': '/work/acme/lib/main.dart',
                  'type': 'file',
                  'ignored': false,
                },
                {
                  'name': 'lib',
                  'path': 'lib',
                  'absolute': '/work/acme/lib',
                  'type': 'directory',
                  'ignored': false,
                },
              ]),
            );
          case '/file/content':
            request.response.write(
              jsonEncode({
                'type': 'binary',
                'content': 'AQID',
                'encoding': 'base64',
                'mimeType': 'image/png',
              }),
            );
          case '/find/file':
            request.response.write(jsonEncode(['lib/main.dart']));
          case '/find':
            request.response.write(
              jsonEncode([
                {
                  'path': {'text': 'lib/main.dart'},
                  'lines': {'text': 'final marker = true;\n'},
                  'line_number': 12,
                  'absolute_offset': 120,
                  'submatches': [
                    {
                      'match': {'text': 'marker'},
                      'start': 6,
                      'end': 12,
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
        final files = await api.listFiles('lib');
        final content = await api.fileContent('lib/main.dart');
        final names = await api.findFile('main');
        final matches = await api.findText('marker');

        expect(files.map((node) => node.name), ['lib', 'main.dart']);
        expect(files.first.isDir, isTrue);
        expect(content.isBinary, isTrue);
        expect(content.encoding, 'base64');
        expect(content.mimeType, 'image/png');
        expect(content.bytes(), [1, 2, 3]);
        expect(names, ['lib/main.dart']);
        expect(matches.single.path, 'lib/main.dart');
        expect(matches.single.lineNumber, 12);
        expect(matches.single.snippet, 'final marker = true;');

        expect(requests.map((request) => request.method), [
          'GET',
          'GET',
          'GET',
          'GET',
        ]);
        expect(requests.map((request) => request.uri.path), [
          '/file',
          '/file/content',
          '/find/file',
          '/find',
        ]);
        expect(requests[0].uri.queryParameters, {
          'path': 'lib',
          'directory': '/work/acme',
          'workspace': 'workspace-1',
        });
        expect(requests[1].uri.queryParameters, {
          'path': 'lib/main.dart',
          'directory': '/work/acme',
          'workspace': 'workspace-1',
        });
        expect(requests[2].uri.queryParameters, {
          'query': 'main',
          'directory': '/work/acme',
          'workspace': 'workspace-1',
          'limit': '50',
        });
        expect(requests[3].uri.queryParameters, {
          'pattern': 'marker',
          'directory': '/work/acme',
          'workspace': 'workspace-1',
        });
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('successful loose file data remains compatible', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        switch (request.uri.path) {
          case '/file':
            request.response.headers.contentType = ContentType.json;
            request.response.write(
              jsonEncode([
                {'name': 'legacy.txt', 'path': 'legacy.txt', 'type': 'file'},
              ]),
            );
          case '/file/content':
            if (request.uri.queryParameters['path'] == 'raw.txt') {
              request.response.headers.contentType = ContentType.text;
              request.response.write('old server raw text');
            } else {
              request.response.headers.contentType = ContentType.json;
              request.response.write(
                jsonEncode({
                  'content': ['one', 'two'],
                }),
              );
            }
          case '/find/file':
            request.response.headers.contentType = ContentType.json;
            request.response.write(jsonEncode(['legacy.txt', 7]));
          case '/find':
            request.response.headers.contentType = ContentType.json;
            request.response.write(
              jsonEncode([
                {
                  'path': 'legacy.txt',
                  'lines': 'old marker\n',
                  'line_number': 4,
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
      );
      try {
        final files = await api.listFiles('');
        final raw = await api.fileContent('raw.txt');
        final lines = await api.fileContent('lines.txt');
        final names = await api.findFile('legacy');
        final matches = await api.findText('marker');

        expect(files.single.path, 'legacy.txt');
        expect(raw.content, 'old server raw text');
        expect(lines.content, 'one\ntwo');
        expect(names, ['legacy.txt', '7']);
        expect(matches.single.path, 'legacy.txt');
        expect(matches.single.lineNumber, 4);
        expect(matches.single.snippet, 'old marker');
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('file errors retain generated OpenCode details', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        await request.drain<void>();
        request.response.statusCode = HttpStatus.badRequest;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            '_tag': 'BadRequestError',
            'requestID': 'request-file',
            'message': 'bad request',
          }),
        );
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        for (final call in <Future<Object?> Function()>[
          () => api.listFiles('lib'),
          () => api.fileContent('lib/main.dart'),
          () => api.findFile('main'),
          () => api.findText('marker'),
        ]) {
          await expectLater(
            call(),
            throwsA(
              isA<ApiException>()
                  .having((error) => error.statusCode, 'statusCode', 400)
                  .having(
                    (error) => error.errorTag,
                    'errorTag',
                    'BadRequestError',
                  )
                  .having(
                    (error) => error.requestID,
                    'requestID',
                    'request-file',
                  ),
            ),
          );
        }
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });
}
