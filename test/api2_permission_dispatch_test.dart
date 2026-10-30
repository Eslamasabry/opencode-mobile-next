import 'package:flutter_test/flutter_test.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/api2/dialect.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

import 'api2_interaction_gateway_test.dart' as wire;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final sends = <String, Future<void> Function(Api2Gateway)>{
    'prompt': (api) => api.promptAsync('s', text: 'write a file'),
    'queued prompt': (api) => api.promptAsync(
      's',
      text: 'write a file',
      delivery: PromptDelivery.queue,
    ),
    'command': (api) => api.slashCommand('s', 'review', ''),
    'shell': (api) => api.shell('s', command: 'pwd', agent: 'build'),
  };

  for (final send in sends.entries) {
    for (final failure in [
      null,
      'get',
      'agent',
      'patch',
      'readback',
      'agent changed',
    ]) {
      test(
        '${send.key}: permission preparation ${failure ?? 'completes before send'}',
        () async {
          final session = <String, dynamic>{
            'id': 's',
            'agent': 'build',
            'permissions': <Object?>[],
            'metadata': {'keep': true},
          };
          late List<wire.Recorded> recorded;
          var reads = 0;
          await wire.withServer(
            handler: (request) async {
              if (request.method == 'GET' &&
                  request.uri.path == '/api/agent/build') {
                await wire.writeJson(request, {
                  'data': {
                    'id': 'build',
                    'permissions': [
                      {'action': '*', 'resource': '*', 'effect': 'allow'},
                    ],
                  },
                }, status: failure == 'agent' ? 503 : 200);
              } else if (request.method == 'GET') {
                reads++;
                await wire.writeJson(request, {
                  'data': {
                    ...session,
                    if (failure == 'agent changed' && reads > 1)
                      'agent': 'plan',
                  },
                }, status: failure == 'get' ? 503 : 200);
              } else if (request.method == 'PATCH') {
                if (failure != 'readback') {
                  session.addAll(
                    Map<String, dynamic>.from(recorded.last.body as Map),
                  );
                }
                await wire.writeJson(
                  request,
                  null,
                  status: failure == 'patch' ? 503 : 204,
                );
              } else if (request.uri.path.endsWith('/prompt')) {
                await wire.writeJson(request, {
                  'data': {
                    'id': 'msg_one',
                    'sessionID': 's',
                    'type': 'user',
                    'payload': {'text': 'write a file'},
                  },
                });
              } else {
                await wire.writeNoContent(request);
              }
            },
            (server, requests) async {
              recorded = requests;
              final api = wire.gatewayFor(server);
              addTearDown(api.close);
              if (failure != null) {
                await expectLater(
                  send.value(api),
                  throwsA(isA<ApiException>()),
                );
                expect(requests.where((r) => r.method == 'POST'), isEmpty);
              } else {
                await send.value(api);
                expect(requests.map((r) => r.method), [
                  'GET',
                  'GET',
                  'PATCH',
                  'GET',
                  'POST',
                ]);
                expect((session['metadata'] as Map)['keep'], true);
                expect(session['permissions'], [
                  {'action': '*', 'resource': '*', 'effect': 'ask'},
                  {'action': '*', 'resource': '*', 'effect': 'ask'},
                ]);
              }
            },
          );
        },
      );
    }
  }

  // An automatically approved conversation hands its tools back to the
  // server's own rules. Requests the phone never sees (sent while it was
  // away, or hidden by a pending list the server fails to encode) otherwise
  // hold the tool until the server's hour-long idle sweep stops the reply.
  group('automatic approval', () {
    const askAll = {'action': '*', 'resource': '*', 'effect': 'ask'};
    Future<void> run({
      required bool automatic,
      required Future<void> Function(Api2Gateway api) act,
      required void Function(
        Map<String, dynamic> session,
        List<wire.Recorded> requests,
      )
      check,
    }) async {
      // The session as an earlier message left it: permission requests on.
      final session = <String, dynamic>{
        'id': 's',
        'agent': 'build',
        'permissions': automatic ? [askAll, askAll] : <Object?>[],
        'metadata': {
          if (automatic)
            'oc.mobile.permissionRequests': {
              'version': 1,
              'original': <Object?>[],
              'applied': [askAll, askAll],
            },
        },
      };
      late List<wire.Recorded> recorded;
      await wire.withServer(
        handler: (request) async {
          if (request.method == 'GET' &&
              request.uri.path == '/api/agent/build') {
            await wire.writeJson(request, {
              'data': {
                'id': 'build',
                'permissions': [
                  {'action': '*', 'resource': '*', 'effect': 'allow'},
                ],
              },
            });
          } else if (request.method == 'GET') {
            await wire.writeJson(request, {'data': session});
          } else if (request.method == 'PATCH') {
            session.addAll(
              Map<String, dynamic>.from(recorded.last.body as Map),
            );
            await wire.writeJson(request, null, status: 204);
          } else if (request.uri.path.endsWith('/prompt')) {
            await wire.writeJson(request, {
              'data': {
                'id': 'msg_one',
                'sessionID': 's',
                'type': 'user',
                'payload': {'text': 'write a file'},
              },
            });
          } else {
            await wire.writeNoContent(request);
          }
        },
        (server, requests) async {
          recorded = requests;
          final api = wire.gatewayFor(server)
            ..serverRunsTools = (id) => automatic && id == 's';
          addTearDown(api.close);
          await act(api);
          check(session, requests);
        },
      );
    }

    test('a message restores the server rules before it is sent', () async {
      await run(
        automatic: true,
        act: (api) => api.promptAsync('s', text: 'write a file'),
        check: (session, requests) {
          expect(requests.map((r) => r.method), [
            'GET',
            'GET',
            'PATCH',
            'GET',
            'POST',
          ]);
          expect(session['permissions'], isEmpty);
        },
      );
    });

    test('switching it off mid-reply turns requests back on', () async {
      await run(
        automatic: false,
        act: (api) => api.syncSessionPermissions('s'),
        check: (session, requests) {
          expect(requests.map((r) => r.method), ['GET', 'GET', 'PATCH', 'GET']);
          expect(session['permissions'], [askAll, askAll]);
        },
      );
    });
  });

  test('a message right after the server boots the project waits for the '
      'agent instead of failing', () async {
    Api2Gateway.agentBootRetryDelay = Duration.zero;
    addTearDown(
      () => Api2Gateway.agentBootRetryDelay = const Duration(milliseconds: 500),
    );
    var agentReads = 0;
    final session = <String, dynamic>{
      'id': 's',
      'agent': 'build',
      'permissions': <Object?>[],
      'metadata': <String, dynamic>{},
    };
    late List<wire.Recorded> recorded;
    await wire.withServer(
      handler: (request) async {
        if (request.method == 'GET' && request.uri.path == '/api/agent/build') {
          // The first read lands while the project's services boot.
          agentReads++;
          await wire.writeJson(
            request,
            agentReads == 1
                ? {'_tag': 'NotFoundError', 'message': 'Agent not found'}
                : {
                    'data': {
                      'id': 'build',
                      'permissions': [
                        {'action': '*', 'resource': '*', 'effect': 'allow'},
                      ],
                    },
                  },
            status: agentReads == 1 ? 404 : 200,
          );
        } else if (request.method == 'GET') {
          await wire.writeJson(request, {'data': session});
        } else if (request.method == 'PATCH') {
          session.addAll(Map<String, dynamic>.from(recorded.last.body as Map));
          await wire.writeJson(request, null, status: 204);
        } else if (request.uri.path.endsWith('/prompt')) {
          await wire.writeJson(request, {
            'data': {
              'id': 'msg_one',
              'sessionID': 's',
              'type': 'user',
              'payload': {'text': 'hi'},
            },
          });
        } else {
          await wire.writeNoContent(request);
        }
      },
      (server, requests) async {
        recorded = requests;
        final api = wire.gatewayFor(server);
        addTearDown(api.close);
        await api.promptAsync('s', text: 'hi');
        expect(agentReads, 2);
        expect(requests.last.uri.path, endsWith('/prompt'));
      },
    );
  });

  test('stable Auto-approve and Allow once send decision once', () async {
    await wire.withServer(handler: wire.writeNoContent, (
      server,
      requests,
    ) async {
      final api = wire.gatewayFor(server);
      addTearDown(api.close);
      api.transport.settleDialect(Api2Dialect.stable);
      await api.respondPermissionV2('s', 'per_shell', 'once');
      expect(requests.single.body, {'decision': 'once'});
    });
  });
}
