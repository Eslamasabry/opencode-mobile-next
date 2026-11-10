import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/widget_action_runner.dart';
import 'package:opencode_mobile/background/widget_v2_contract.dart';
import 'package:opencode_mobile/state/profiles.dart';

class _RealHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  for (final protocol in ['OC2 stable', 'OC2 beta']) {
    final stable = protocol == 'OC2 stable';
    final oc1 = protocol == 'OC1';
    for (final kind in ['allowOnce', 'reject']) {
      test(
        'cold $protocol $kind reconciles real status route after reply',
        () async {
          await HttpOverrides.runZoned(
            () async {
              final server = await HttpServer.bind(
                InternetAddress.loopbackIPv4,
                0,
              );
              final seen = <String>[];
              final replies = <Object?>[];
              final queries = <Map<String, String>>[];
              var pending = true;
              var statusReads = 0;
              const sessionID = 'ses_widget_test';
              const requestID = 'per_widget_test';
              final decision = kind == 'allowOnce' ? 'once' : 'reject';
              final pendingPath = oc1
                  ? '/permission'
                  : '/api/permission/request';
              final replyPath = oc1
                  ? '/permission/$requestID/reply'
                  : '/api/session/$sessionID/permission/$requestID/reply';
              final statusPath = oc1
                  ? '/session/status'
                  : '/api/session/active';
              server.listen((request) async {
                final path = request.uri.path;
                seen.add('${request.method} $path');
                final response = request.response
                  ..headers.contentType = ContentType.json;
                Object? body = <String, Object?>{};
                if (oc1 && path == '/global/health') {
                  body = {'healthy': true, 'version': '1.19.0'};
                } else if (path == '/api/health' && !stable) {
                  body = {'healthy': true, 'version': '0.0.0-beta-18600'};
                } else if (path == '/api/info' && stable) {
                  body = {'version': '2.1.1'};
                } else if (path == pendingPath) {
                  queries.add(request.uri.queryParameters);
                  final permissions = [
                    if (pending)
                      {
                        'id': requestID,
                        'sessionID': sessionID,
                        if (oc1) ...{
                          'permission': 'bash',
                          'patterns': ['sleep 50'],
                          'always': <String>[],
                          'metadata': <String, Object?>{},
                        } else ...{
                          'action': 'shell',
                          'resources': ['sleep 50'],
                        },
                      },
                  ];
                  body = oc1 ? permissions : {'data': permissions};
                } else if (path.endsWith('/$sessionID/message')) {
                  final messages = [
                    if (oc1)
                      {
                        'info': {
                          'id': 'user-1',
                          'sessionID': sessionID,
                          'role': 'user',
                        },
                        'parts': [],
                      }
                    else
                      {
                        'id': 'user-1',
                        'type': 'user',
                        'text': 'Synthetic prompt',
                      },
                  ];
                  body = oc1 ? messages : {'data': messages};
                } else if (path == statusPath && request.method == 'GET') {
                  statusReads++;
                  // The model continues briefly after the decision, then ends.
                  final active = {
                    if (statusReads == 1)
                      sessionID: {'type': oc1 ? 'busy' : 'running'},
                  };
                  body = oc1 ? active : {'data': active};
                } else if (path == replyPath && request.method == 'POST') {
                  final reply = jsonDecode(
                    await utf8.decoder.bind(request).join(),
                  );
                  replies.add(reply);
                  if (reply is Map &&
                      reply.length == 1 &&
                      reply[stable ? 'decision' : 'reply'] == decision) {
                    pending = false;
                    response.statusCode = HttpStatus.noContent;
                    body = null;
                  } else {
                    response.statusCode = HttpStatus.badRequest;
                  }
                } else {
                  response.statusCode = HttpStatus.notFound;
                }
                if (body != null) response.write(jsonEncode(body));
                await response.close();
              });
              final profile = ServerProfile(
                id: 'profile',
                name: 'Synthetic server',
                baseUrl: 'http://127.0.0.1:${server.port}',
                flavor: ServerFlavor.v2,
                // Cached version must not substitute for a fresh negotiation.
                serverVersion: stable ? '0.0.0-beta-18600' : '2.1.1',
              );
              final fingerprint = widgetProfileFingerprint(profile);
              final snapshot = <String, dynamic>{
                'version': 2,
                'profileID': profile.id,
                'scopeToken': 'scope',
                'connected': false,
                'pendingCount': 2,
                'runningCount': 1,
                'onDemand': {'fingerprint': fingerprint},
                'sessions': [
                  {
                    'id': sessionID,
                    'busy': true,
                    'startedAt': 1,
                    'step': 'Running shell',
                    'requestID': requestID,
                    'onDemandActions': ['allowOnce', 'reject'],
                  },
                  {
                    'id': 'ses_other',
                    'requestID': 'per_other',
                    'onDemandActions': ['allowOnce', 'reject'],
                  },
                ],
              };
              final runner = WidgetActionRunner(
                loadProfile: (_) async => profile,
                loadTarget: (_, _) async => {
                  'profileID': profile.id,
                  'scopeToken': 'scope',
                  'fingerprint': fingerprint,
                  'directory': '/synthetic/project',
                  'workspace': 'wrk_test',
                },
                isCurrent: (_, _) async => true,
                statusPollInterval: const Duration(milliseconds: 1),
                // Deliberately use the production factory, client and gateway.
              );
              try {
                final result = await runner.execute(
                  WidgetAction(
                    id: 'tap',
                    action: kind,
                    profileID: profile.id,
                    scopeToken: 'scope',
                    sessionID: sessionID,
                    requestID: requestID,
                  ),
                  snapshot,
                  DateTime.now()
                      .add(const Duration(seconds: 10))
                      .millisecondsSinceEpoch,
                );
                expect(replies, [
                  {stable ? 'decision' : 'reply': decision},
                ]);
                expect(result['status'], 'accepted');
                expect(pending, isFalse);
                expect(seen, [
                  oc1 ? 'GET /global/health' : 'GET /api/health',
                  if (stable) ...['GET /api/info', 'GET /api/info'],
                  'GET $pendingPath',
                  'GET ${oc1 ? '' : '/api'}/session/$sessionID/message',
                  'POST $replyPath',
                  'GET $statusPath',
                  'GET $statusPath',
                ]);
                expect(queries.single, {
                  oc1 ? 'directory' : 'location[directory]':
                      '/synthetic/project',
                  oc1 ? 'workspace' : 'location[workspace]': 'wrk_test',
                });
                final updated = result['snapshot'] as Map;
                expect(updated['pendingCount'], 1);
                expect(updated['runningCount'], 0);
                expect(updated['connected'], isFalse);
                expect(statusReads, 2);
                final rows = updated['sessions'] as List;
                expect((rows.first as Map).containsKey('requestID'), isFalse);
                expect(rows.first['busy'], isFalse);
                expect(rows.first['startedAt'], 0);
                expect(rows.first['step'], '');
                expect(rows.last, (snapshot['sessions'] as List).last);
                // Duplicate delivery of the same tap cannot send another reply.
                await runner.execute(
                  WidgetAction(
                    id: 'tap',
                    action: kind,
                    profileID: profile.id,
                    scopeToken: 'scope',
                    sessionID: sessionID,
                    requestID: requestID,
                  ),
                  snapshot,
                  DateTime.now().millisecondsSinceEpoch + 10000,
                );
                expect(replies, hasLength(1));
              } finally {
                runner.cancel();
                await server.close(force: true);
              }
            },
            createHttpClient: (_) =>
                _RealHttpOverrides().createHttpClient(null),
          );
        },
      );
    }
  }
}
