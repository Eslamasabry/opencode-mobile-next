import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/domain/background_work.dart';

import 'api2_interaction_gateway_test.dart'
    show withServer, writeJson, writeNoContent;

MessageWithParts work(
  String name, {
  String status = 'running',
  bool background = false,
  bool completed = false,
}) => MessageWithParts(
  info: MessageInfo(
    id: 'msg_a',
    sessionID: 'ses_a',
    role: 'assistant',
    time: MsgTime(created: 1, completed: completed ? 2 : null),
  ),
  parts: [
    Part(
      type: 'tool',
      toolName: name,
      toolState: ToolState.fromJson({
        'status': status,
        'input': <String, dynamic>{},
        'metadata': {'background': background},
      }),
    ),
  ],
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('only supported foreground work is eligible', () {
    final messages = [
      work('task'),
      work('shell'),
      work('bash'),
      work('pty'),
      work('task', background: true),
      work('task', status: 'completed'),
      work('task', completed: true),
    ];
    expect(
      foregroundBackgroundableParts(
        messages,
        BackgroundWorkSupport.unavailable,
      ),
      isEmpty,
    );
    expect(
      foregroundBackgroundableParts(
        messages,
        BackgroundWorkSupport.subagents,
      ).map((part) => part.toolName),
      ['task'],
    );
    expect(
      foregroundBackgroundableParts(
        messages,
        BackgroundWorkSupport.subagentsAndShells,
      ).map((part) => part.toolName),
      ['task', 'shell'],
    );
  });

  test(
    'v1 capability and promotion use the scoped experimental API, including false',
    () async {
      var enabled = false;
      var promoted = false;
      await withServer(
        handler: (request) async {
          if (request.uri.path == '/experimental/capabilities') {
            await writeJson(request, {'backgroundSubagents': enabled});
          } else {
            expect(request.method, 'POST');
            expect(request.uri.path, '/experimental/session/ses_a/background');
            await writeJson(request, promoted);
          }
        },
        (server, requests) async {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repo = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/work', workspace: 'ws_a');
          addTearDown(api.close);
          expect(
            await repo.loadBackgroundWorkSupport(),
            BackgroundWorkSupport.unavailable,
          );
          enabled = true;
          expect(
            await repo.loadBackgroundWorkSupport(),
            BackgroundWorkSupport.subagents,
          );
          expect(
            await repo.backgroundSession('ses_a'),
            BackgroundWorkResult.unchanged,
          );
          promoted = true;
          expect(
            await repo.backgroundSession('ses_a'),
            BackgroundWorkResult.promoted,
          );
          for (final request in requests) {
            expect(request.uri.queryParameters['directory'], '/work');
            expect(request.uri.queryParameters['workspace'], 'ws_a');
          }
        },
      );
    },
  );

  test('v2 204 acknowledges a request without claiming promotion', () async {
    await withServer(
      handler: (request) async {
        expect(request.method, 'POST');
        expect(request.uri.path, '/api/session/ses_a/background');
        await writeNoContent(request);
      },
      (server, requests) async {
        final client = Api2Client.connect(
          baseUrl: 'http://${server.address.host}:${server.port}',
          password: 'test-password',
          directory: '/work',
          workspace: 'ws_a',
        );
        addTearDown(client.close);
        final repo = Api2OperationsGateway(client: client);
        expect(
          await repo.loadBackgroundWorkSupport(),
          BackgroundWorkSupport.subagentsAndShells,
        );
        expect(
          await repo.backgroundSession('ses_a'),
          BackgroundWorkResult.requested,
        );
        expect(
          requests.single.uri.queryParameters['location[directory]'],
          '/work',
        );
        expect(
          requests.single.uri.queryParameters['location[workspace]'],
          'ws_a',
        );
      },
    );
  });
}
