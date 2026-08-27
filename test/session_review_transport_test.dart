import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';

class _RealHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'session todos and diff use generated location-scoped contracts',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <HttpRequest>[];
        server.listen((request) async {
          requests.add(request);
          request.response.headers.contentType = ContentType.json;
          switch (request.uri.path) {
            case '/session/ses-review/todo':
              request.response.write(
                jsonEncode([
                  {
                    'content': 'Verify release diff',
                    'status': 'in_progress',
                    'priority': 'high',
                  },
                ]),
              );
            case '/session/ses-review/diff':
              request.response.write(
                jsonEncode([
                  {
                    'file': 'lib/review.dart',
                    'patch': '@@ -1 +1 @@\n-old\n+new',
                    'additions': 1,
                    'deletions': 1,
                    'status': 'modified',
                  },
                  {
                    'file': 'lib/future.dart',
                    'patch': '@@ -0,0 +1 @@\n+future',
                    'additions': 1,
                    'deletions': 0,
                    'status': 'renamed',
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
          final todos = await api.todos('ses-review');
          final diffs = await api.diff('ses-review');

          expect(todos.single.content, 'Verify release diff');
          expect(todos.single.status, 'in_progress');
          expect(todos.single.priority, 'high');
          expect(diffs.first.file, 'lib/review.dart');
          expect(diffs.first.patch, '@@ -1 +1 @@\n-old\n+new');
          expect(diffs.first.counts, (added: 1, removed: 1));
          expect(diffs.first.status, 'modified');
          expect(
            diffs.last.status,
            isNull,
            reason:
                'future statuses must not render as SDK implementation names',
          );
          expect(requests.map((request) => request.method), ['GET', 'GET']);
          expect(requests.map((request) => request.uri.path), [
            '/session/ses-review/todo',
            '/session/ses-review/diff',
          ]);
          for (final request in requests) {
            expect(request.uri.queryParameters, {
              'directory': '/work/acme',
              'workspace': 'workspace-1',
            });
          }
        } finally {
          api.close();
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'successful loose todo and diff data retain older-server fields',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        server.listen((request) async {
          request.response.headers.contentType = ContentType.json;
          switch (request.uri.path) {
            case '/session/ses-loose/todo':
              request.response.write(
                jsonEncode([
                  {'content': 'Old server todo', 'status': 'pending'},
                ]),
              );
            case '/session/ses-loose/diff':
              request.response.write(
                jsonEncode([
                  {
                    'file': 'legacy.txt',
                    'before': 'old\n',
                    'after': 'new\n',
                    'status': 'legacy_changed',
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
          final todos = await api.todos('ses-loose');
          final diffs = await api.diff('ses-loose');

          expect(todos.single.content, 'Old server todo');
          expect(todos.single.priority, isNull);
          expect(diffs.single.before, 'old\n');
          expect(diffs.single.after, 'new\n');
          expect(diffs.single.status, 'legacy_changed');
          expect(diffs.single.counts, (added: 1, removed: 1));
        } finally {
          api.close();
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'session todo and diff errors retain generated OpenCode details',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        server.listen((request) async {
          await request.drain<void>();
          request.response.statusCode = HttpStatus.badRequest;
          request.response.headers.contentType = ContentType.json;
          request.response.write(
            jsonEncode({
              '_tag': 'InvalidRequestError',
              'requestID': 'request-review',
              'message': 'review rejected',
            }),
          );
          await request.response.close();
        });

        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        try {
          for (final call in <Future<Object?> Function()>[
            () => api.todos('ses-review'),
            () => api.diff('ses-review'),
          ]) {
            await expectLater(
              call(),
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
                      'request-review',
                    ),
              ),
            );
          }
        } finally {
          api.close();
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );
}
