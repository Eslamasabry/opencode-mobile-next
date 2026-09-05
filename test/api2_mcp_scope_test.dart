import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'api2_interaction_gateway_test.dart' show writeJson;

Future<void> withServer(
  Future<void> Function(HttpRequest) handler,
  Future<void> Function(HttpServer) body,
) async {
  final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
  server.listen(handler);
  try {
    await body(server);
  } finally {
    await server.close(force: true);
  }
}

Api2OperationsGateway gatewayFor(HttpServer server) => Api2OperationsGateway(
  client: Api2Client.connect(
    baseUrl: 'http://${server.address.host}:${server.port}',
    password: 'fixture',
  ),
);

const draft = McpServerDraft(
  name: 'docs',
  kind: McpServerKind.remote,
  url: 'https://example.com/mcp',
);

void main() {
  test('v2 rejects persistent scope and existing names without PUT', () async {
    final calls = <String>[];
    await withServer(
      (request) async {
        calls.add(request.method);
        await writeJson(request, {
          'data': [
            {
              'name': 'docs',
              'status': {'status': 'disabled'},
            },
          ],
        });
      },
      (base) async {
        final gateway = gatewayFor(base);
        addTearDown(gateway.client.close);
        for (final scope in [McpConfigScope.project, McpConfigScope.global]) {
          await expectLater(
            gateway.addMcpServer(draft, scope: scope),
            throwsA(isA<ProductException>()),
          );
        }
        expect(calls, isEmpty);
        await expectLater(
          gateway.addMcpServer(draft, scope: McpConfigScope.runtimeLocation),
          throwsA(isA<ProductException>()),
        );
        expect(calls, ['GET']);
      },
    );
  });

  test(
    'MCP uniqueness read and add keep the same location across awaits',
    () async {
      final read = Completer<void>();
      final release = Completer<void>();
      final calls = <Uri>[];
      await withServer(
        (request) async {
          calls.add(request.uri);
          if (request.method == 'GET') {
            read.complete();
            await release.future;
            await writeJson(request, {'data': []});
          } else {
            final body = jsonDecode(await utf8.decoder.bind(request).join());
            expect(body['config']['url'], draft.url);
            request.response.statusCode = HttpStatus.noContent;
            await request.response.close();
          }
        },
        (base) async {
          final Api2OperationsGateway gateway = gatewayFor(base);
          addTearDown(gateway.client.close);
          gateway.setLocation(directory: '/work/first', workspace: 'first');
          final pending = gateway.addMcpServer(
            draft,
            scope: McpConfigScope.runtimeLocation,
          );
          await read.future;
          gateway.setLocation(directory: '/work/second', workspace: 'second');
          release.complete();
          await pending;
          expect(calls, hasLength(2));
          for (final uri in calls) {
            expect(uri.queryParameters['location[directory]'], '/work/first');
            expect(uri.queryParameters['location[workspace]'], 'first');
          }
        },
      );
    },
  );
}
