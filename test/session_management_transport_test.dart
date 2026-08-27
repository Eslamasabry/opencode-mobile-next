import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';

class _RealHttpOverrides extends HttpOverrides {}

typedef _Request = ({String method, Uri uri, Object? body});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('create rename and delete use generated location contracts', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final requests = <_Request>[];
      server.listen((request) async {
        final text = await utf8.decoder.bind(request).join();
        requests.add((
          method: request.method,
          uri: request.uri,
          body: text.isEmpty ? null : jsonDecode(text),
        ));
        request.response.headers.contentType = ContentType.json;
        if (request.method == 'POST') {
          // Older servers may return the useful session fields without newer
          // generated-contract metadata such as slug and version.
          request.response.write(
            jsonEncode({
              'id': 'session-created',
              'title': 'New session',
              'directory': '/work/acme',
              'time': {'created': 1000, 'updated': 1000},
            }),
          );
        } else if (request.method == 'PATCH') {
          request.response.write(
            jsonEncode({'id': 'session-created', 'title': 'Renamed'}),
          );
        } else {
          request.response.statusCode = HttpStatus.noContent;
        }
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      )..setLocation(directory: '/work/acme', workspace: 'workspace-1');
      try {
        final created = await api.createSession();
        await api.renameSession(created.id, 'Renamed');
        await api.deleteSession(created.id);

        expect(created.id, 'session-created');
        expect(created.title, 'New session');
        expect(requests.map((request) => request.method), [
          'POST',
          'PATCH',
          'DELETE',
        ]);
        expect(requests.map((request) => request.uri.path), [
          '/session',
          '/session/session-created',
          '/session/session-created',
        ]);
        for (final request in requests) {
          expect(request.uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'workspace-1',
          });
        }
        expect(requests[0].body, <String, Object?>{});
        expect(requests[1].body, {'title': 'Renamed'});
        expect(requests[2].body, isNull);
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('session mutation errors retain generated server details', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        await request.drain<void>();
        request.response.statusCode = request.method == 'DELETE'
            ? HttpStatus.notFound
            : HttpStatus.badRequest;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            '_tag': request.method == 'DELETE'
                ? 'SessionNotFoundError'
                : 'InvalidRequestError',
            'requestID': 'request-${request.method.toLowerCase()}',
            'message': '${request.method} rejected',
          }),
        );
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        await expectLater(
          api.createSession(),
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
                  'request-post',
                ),
          ),
        );
        await expectLater(
          api.renameSession('session-1', 'Renamed'),
          throwsA(
            isA<ApiException>()
                .having((error) => error.statusCode, 'statusCode', 400)
                .having(
                  (error) => error.requestID,
                  'requestID',
                  'request-patch',
                ),
          ),
        );
        await expectLater(
          api.deleteSession('session-1'),
          throwsA(
            isA<ApiException>()
                .having((error) => error.statusCode, 'statusCode', 404)
                .having(
                  (error) => error.errorTag,
                  'errorTag',
                  'SessionNotFoundError',
                )
                .having(
                  (error) => error.requestID,
                  'requestID',
                  'request-delete',
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
