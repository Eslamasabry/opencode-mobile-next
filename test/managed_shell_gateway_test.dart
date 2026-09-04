import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'api2_interaction_gateway_test.dart'
    show withServer, writeJson, writeNoContent;

Map<String, dynamic> shellJson() => {
  'id': 'sh_a',
  'command': 'flutter test',
  'status': 'running',
  'cwd': '/private/work',
  'file': '/private/output',
  'shell': '/bin/sh',
  'metadata': {'sessionID': 'ses_a'},
  'time': {'started': 1000},
};

void main() {
  test('v1 never probes the unrelated shell API', () async {
    final api = OpenCodeApi(baseUrl: 'http://localhost:1');
    addTearDown(api.close);
    final repo = SdkProductRepository(api.sdkClient);
    expect((await repo.loadRunningShells()).supported, isFalse);
    expect(await repo.managedShellServerIdentity(), isNull);
    expect(
      () => repo.stopManagedShell('sh_a'),
      throwsA(isA<ProductException>()),
    );
  });

  test(
    'v2 shell reads, byte cursors, timeout and delete retain workspace scope',
    () async {
      await withServer(
        handler: (request) async {
          switch (request.uri.path) {
            case '/api/shell':
              await writeJson(request, {
                'data': [shellJson()],
              });
            case '/api/shell/sh_a':
              if (request.method == 'DELETE') {
                await writeNoContent(request);
              } else {
                await writeJson(request, {'data': shellJson()});
              }
            case '/api/shell/sh_a/timeout':
              expect(request.method, 'PATCH');
              await writeJson(request, {'data': shellJson()});
            case '/api/shell/sh_a/output':
              expect(request.uri.queryParameters['cursor'], '2');
              expect(request.uri.queryParameters['limit'], '65536');
              await writeJson(request, {
                'data': {
                  'output': '🙂',
                  'cursor': 6,
                  'size': 6,
                  'truncated': false,
                },
              });
            case '/api/health':
              await writeJson(request, {'healthy': true, 'pid': 77});
            default:
              fail('Unexpected ${request.uri}');
          }
        },
        (server, requests) async {
          final client = Api2Client.connect(
            baseUrl: 'http://${server.address.host}:${server.port}',
            password: 'test',
            directory: '/work',
            workspace: 'ws_a',
          );
          addTearDown(client.close);
          final repo = Api2OperationsGateway(client: client);
          final list = await repo.loadRunningShells();
          expect(list.supported, isTrue);
          expect(list.shells.single.sessionID, 'ses_a');
          expect((await repo.getManagedShell('sh_a'))!.running, isTrue);
          final page = await repo.readManagedShellOutput(
            'sh_a',
            cursor: 2,
            limit: 1000000,
          );
          expect(page.cursor, 6);
          expect(page.text, '🙂');
          await repo.setManagedShellTimeout('sh_a', const Duration(minutes: 5));
          await repo.setManagedShellTimeout('sh_a', null);
          await repo.stopManagedShell('sh_a');
          expect(await repo.managedShellServerIdentity(), '77');
          final updates = requests.where((r) => r.method == 'PATCH').toList();
          expect(updates[0].body, {'timeout': 300000});
          expect(updates[1].body, {'timeout': 0});
          for (final request in requests.where(
            (r) => r.uri.path != '/api/health',
          )) {
            expect(request.uri.queryParameters['location[directory]'], '/work');
            expect(request.uri.queryParameters['location[workspace]'], 'ws_a');
          }
        },
      );
    },
  );

  for (final status in [404, 405, 501, 401, 500]) {
    test(
      'shell discovery handles HTTP $status without advertising false support',
      () async {
        await withServer(
          handler: (request) async {
            request.response.statusCode = status;
            await request.response.close();
          },
          (server, _) async {
            final client = Api2Client.connect(
              baseUrl: 'http://${server.address.host}:${server.port}',
              password: 'test',
            );
            addTearDown(client.close);
            final repo = Api2OperationsGateway(client: client);
            if ([404, 405, 501].contains(status)) {
              expect((await repo.loadRunningShells()).supported, isFalse);
            } else {
              await expectLater(
                repo.loadRunningShells(),
                throwsA(isA<ProductException>()),
              );
            }
          },
        );
      },
    );
  }

  test(
    'shell lifecycle and session shell changes survive event projection without fake messages',
    () {
      final adapter = Api2EventAdapter();
      for (final type in ['shell.created', 'shell.exited', 'shell.deleted']) {
        final envelope = Api2EventEnvelope.fromJson({
          'type': type,
          'data': {'id': 'sh_a'},
        });
        expect(envelope.event, isA<Api2ManagedShellEvent>());
        final events = adapter.adapt(envelope);
        expect(events.single.type, type);
        expect(events.single.properties['id'], 'sh_a');
      }
      final events = adapter.adapt(
        Api2EventEnvelope.fromJson({
          'type': 'session.shell.started',
          'data': {'sessionID': 'ses_a', 'shell': shellJson()},
        }),
      );
      expect(events.single.type, 'session.shell.changed');
      expect(events.single.properties['sessionID'], 'ses_a');
    },
  );
}
