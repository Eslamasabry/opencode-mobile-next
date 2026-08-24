import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RealHttpOverrides extends HttpOverrides {}

class _HydrationApi extends OpenCodeApi {
  _HydrationApi() : super(baseUrl: 'http://localhost');

  int failuresRemaining = 1;
  int calls = 0;
  List<PermissionRequest> result = [];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async {
    calls += 1;
    if (failuresRemaining > 0) {
      failuresRemaining -= 1;
      throw ApiException('temporarily unavailable');
    }
    return result;
  }
}

class _ReplyApi extends OpenCodeApi {
  _ReplyApi() : super(baseUrl: 'http://localhost');

  final List<
    ({
      String requestID,
      String reply,
      String? legacySessionID,
      String? legacyPermissionID,
    })
  >
  replies = [];

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
  }) async {
    replies.add((
      requestID: requestID,
      reply: reply,
      legacySessionID: legacySessionID,
      legacyPermissionID: legacyPermissionID,
    ));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('permission request parses the current contract', () {
    final request = PermissionRequest.fromJson(const {
      'id': 'request-1',
      'sessionID': 'session-1',
      'permission': 'bash',
      'patterns': ['git status', 'git diff'],
      'metadata': {'cwd': '/workspace'},
      'always': ['git *'],
      'tool': {'messageID': 'message-1', 'callID': 'call-1'},
    });

    expect(request.id, 'request-1');
    expect(request.sessionID, 'session-1');
    expect(request.permission, 'bash');
    expect(request.patterns, ['git status', 'git diff']);
    expect(request.metadata, {'cwd': '/workspace'});
    expect(request.always, ['git *']);
    expect(request.tool?.messageID, 'message-1');
    expect(request.tool?.callID, 'call-1');
  });

  test('tool states expose pending raw input and error text', () {
    final pending = ToolState.fromJson(const {
      'status': 'pending',
      'input': {'command': 'partial'},
      'raw': '{"command":"git status',
    });
    final failed = ToolState.fromJson(const {
      'status': 'error',
      'input': {'command': 'git status'},
      'error': 'permission denied',
    });

    expect(pending.inputJson, '{"command":"git status');
    expect(failed.output, 'permission denied');
  });

  test('permission API lists and replies through current endpoints', () async {
    await HttpOverrides.runZoned(
      () async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests =
            <({String method, String path, String body, Uri uri})>[];
        server.listen((request) async {
          final body = await utf8.decoder.bind(request).join();
          requests.add((
            method: request.method,
            path: request.uri.path,
            body: body,
            uri: request.uri,
          ));
          request.response.headers.contentType = ContentType.json;
          if (request.method == 'GET' && request.uri.path == '/permission') {
            request.response.write(
              jsonEncode([
                {
                  'id': 'request-1',
                  'sessionID': 'session-1',
                  'permission': 'bash',
                  'patterns': ['git status'],
                  'metadata': <String, Object?>{},
                  'always': ['git status'],
                },
              ]),
            );
          } else {
            request.response.write('true');
          }
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          )..setLocation(directory: '/work/acme', workspace: 'workspace-1');
          final pending = await api.pendingPermissions();
          await api.respondPermission(
            'request-1',
            'always',
            legacySessionID: 'legacy-session',
            legacyPermissionID: 'legacy-permission',
          );

          expect(pending.single.id, 'request-1');
          expect(requests[0].method, 'GET');
          expect(requests[0].path, '/permission');
          expect(requests[1].method, 'POST');
          expect(requests[1].path, '/permission/request-1/reply');
          expect(requests[1].uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'workspace-1',
          });
          expect(jsonDecode(requests[1].body), {'reply': 'always'});
          expect(requests, hasLength(2));
        } finally {
          await server.close(force: true);
        }
      },
      createHttpClient: (_) {
        return _RealHttpOverrides().createHttpClient(null);
      },
    );
  });

  test(
    'permission API falls back only for an unavailable current endpoint',
    () async {
      await HttpOverrides.runZoned(
        () async {
          final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
          final requests = <({String path, String body, Uri uri})>[];
          server.listen((request) async {
            final body = await utf8.decoder.bind(request).join();
            requests.add((
              path: request.uri.path,
              body: body,
              uri: request.uri,
            ));
            request.response.headers.contentType = ContentType.json;
            switch (request.uri.path) {
              case '/permission/legacy-1/reply':
                request.response.statusCode = HttpStatus.notFound;
                request.response.write(jsonEncode({'_tag': 'NotFoundError'}));
                break;
              case '/session/session-1/permissions/legacy-1':
                request.response.write('true');
                break;
              case '/permission/stale-1/reply':
                request.response.statusCode = HttpStatus.notFound;
                request.response.write(
                  jsonEncode({
                    '_tag': 'PermissionNotFoundError',
                    'requestID': 'stale-1',
                    'message': 'Permission request not found',
                  }),
                );
                break;
              case '/permission/unrelated-1/reply':
                request.response.statusCode = HttpStatus.notFound;
                request.response.write(
                  jsonEncode({
                    '_tag': 'PermissionNotFoundError',
                    'requestID': 'another-request',
                    'message': 'A different permission was not found',
                  }),
                );
                break;
              default:
                request.response.statusCode = HttpStatus.internalServerError;
                request.response.write(jsonEncode({'message': 'broken'}));
            }
            await request.response.close();
          });

          try {
            final api = OpenCodeApi(
              baseUrl: 'http://${server.address.host}:${server.port}',
            )..setLocation(directory: '/work/acme', workspace: 'workspace-1');
            await api.respondPermission(
              'legacy-1',
              'once',
              legacySessionID: 'session-1',
              legacyPermissionID: 'legacy-1',
            );
            await expectLater(
              api.respondPermission(
                'stale-1',
                'once',
                legacySessionID: 'session-1',
                legacyPermissionID: 'stale-1',
              ),
              throwsA(
                isA<ApiException>().having(
                  (error) => error.isPermissionNotFound('stale-1'),
                  'canonical permission-not-found identity',
                  isTrue,
                ),
              ),
            );
            await expectLater(
              api.respondPermission(
                'unrelated-1',
                'once',
                legacySessionID: 'session-1',
                legacyPermissionID: 'unrelated-1',
              ),
              throwsA(
                isA<ApiException>()
                    .having(
                      (error) => error.statusCode,
                      'status code',
                      HttpStatus.notFound,
                    )
                    .having(
                      (error) => error.isPermissionNotFound('unrelated-1'),
                      'matching permission identity',
                      isFalse,
                    ),
              ),
            );
            await expectLater(
              api.respondPermission(
                'broken-1',
                'once',
                legacySessionID: 'session-1',
                legacyPermissionID: 'broken-1',
              ),
              throwsA(
                isA<ApiException>().having(
                  (error) => error.statusCode,
                  'status code',
                  HttpStatus.internalServerError,
                ),
              ),
            );

            expect(requests.map((request) => request.path), [
              '/permission/legacy-1/reply',
              '/session/session-1/permissions/legacy-1',
              '/permission/stale-1/reply',
              '/permission/unrelated-1/reply',
              '/permission/broken-1/reply',
            ]);
            expect(jsonDecode(requests[1].body), {'response': 'once'});
            expect(requests[0].uri.queryParameters, {
              'directory': '/work/acme',
              'workspace': 'workspace-1',
            });
            expect(requests[1].uri.queryParameters, {
              'directory': '/work/acme',
              'workspace': 'workspace-1',
            });
          } finally {
            await server.close(force: true);
          }
        },
        createHttpClient: (_) {
          return _RealHttpOverrides().createHttpClient(null);
        },
      );
    },
  );

  test(
    'controller tracks concurrent requests and current session statuses',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final controller = ConnectionController(ProfileStore(prefs: prefs));

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.asked',
          properties: const {
            'id': 'request-1',
            'sessionID': 'session-1',
            'permission': 'bash',
            'patterns': ['git status'],
            'metadata': <String, Object?>{},
            'always': <String>[],
          },
        ),
      );
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.asked',
          properties: const {
            'id': 'request-2',
            'sessionID': 'session-1',
            'permission': 'edit',
            'patterns': ['lib/main.dart'],
            'metadata': <String, Object?>{},
            'always': <String>[],
          },
        ),
      );

      expect(controller.permissions.keys, ['request-1', 'request-2']);
      expect(controller.permissionsForSession('session-1'), hasLength(2));

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.replied',
          properties: const {
            'sessionID': 'session-1',
            'requestID': 'request-1',
            'reply': 'once',
          },
        ),
      );
      expect(controller.permissions.keys, ['request-2']);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.status',
          properties: const {
            'sessionID': 'session-1',
            'status': {'type': 'retry', 'attempt': 1, 'next': 1000},
          },
        ),
      );
      expect(controller.busySessions, contains('session-1'));

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.status',
          properties: const {
            'sessionID': 'session-1',
            'status': {'type': 'idle'},
          },
        ),
      );
      expect(controller.busySessions, isNot(contains('session-1')));
    },
  );

  test(
    'controller accepts legacy permission events without losing identity',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final api = _ReplyApi();
      final controller = ConnectionController(ProfileStore(prefs: prefs))
        ..api = api;

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.updated',
          properties: const {
            'id': 'legacy-1',
            'sessionID': 'session-1',
            'type': 'bash',
            'pattern': ['git status', 'git diff'],
            'messageID': 'message-1',
            'callID': 'call-1',
            'metadata': {'cwd': '/workspace'},
          },
        ),
      );
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.updated',
          properties: const {
            'id': 'legacy-2',
            'sessionID': 'session-1',
            'type': 'edit',
            'pattern': 'lib/main.dart',
            'messageID': 'message-2',
            'metadata': <String, Object?>{},
          },
        ),
      );

      expect(controller.permissions.keys, ['legacy-1', 'legacy-2']);
      expect(controller.permissions['legacy-1']?.permission, 'bash');
      expect(controller.permissions['legacy-1']?.patterns, [
        'git status',
        'git diff',
      ]);

      controller.handleEventForTesting(
        EventEnvelope(
          type: 'permission.replied',
          properties: const {
            'sessionID': 'session-1',
            'permissionID': 'legacy-1',
            'response': 'once',
          },
        ),
      );
      await controller.answerPermission('legacy-2', 'always');

      expect(controller.permissions, isEmpty);
      expect(api.replies.single, (
        requestID: 'legacy-2',
        reply: 'always',
        legacySessionID: 'session-1',
        legacyPermissionID: 'legacy-2',
      ));
    },
  );

  testWidgets('pending permission hydration retries transient failures', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final api = _HydrationApi()
      ..result = [
        PermissionRequest.fromJson(const {
          'id': 'request-1',
          'sessionID': 'session-1',
          'permission': 'bash',
          'patterns': ['git status'],
          'metadata': <String, Object?>{},
          'always': <String>[],
        }),
      ];
    final controller = ConnectionController(ProfileStore(prefs: prefs))
      ..api = api;

    await controller.refreshPendingPermissions();
    expect(controller.permissions, isEmpty);

    await tester.pump(const Duration(milliseconds: 251));

    expect(api.calls, 2);
    expect(controller.permissions.keys, ['request-1']);
    controller.dispose();
  });

  test('answering an externally resolved permission is idempotent', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final controller = ConnectionController(ProfileStore(prefs: prefs));
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.asked',
        properties: const {
          'id': 'request-1',
          'sessionID': 'session-1',
          'permission': 'bash',
          'patterns': ['git status'],
          'metadata': <String, Object?>{},
          'always': <String>[],
        },
      ),
    );
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.replied',
        properties: const {'requestID': 'request-1', 'reply': 'once'},
      ),
    );

    await expectLater(
      controller.answerPermission('request-1', 'once'),
      completes,
    );
  });
}
