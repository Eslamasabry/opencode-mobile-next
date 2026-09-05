import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

class _FailingCatalogClient extends Api2Client {
  _FailingCatalogClient()
    : super.connect(baseUrl: 'http://localhost', password: 'fixture');
  final first = Completer<List<Api2ProviderInfo>>();
  @override
  Future<List<Api2ProviderInfo>> providers() => first.future;
  @override
  Future<List<Api2ModelInfo>> models() => Future.error(
    Api2Unavailable('Model catalog unavailable', statusCode: 503),
  );
  @override
  Future<List<Api2AgentInfo>> agents() async => [];
  @override
  Future<Api2ModelInfo?> defaultModel() async => null;
}

void main() {
  for (final liveGateway in [false, true]) {
    test(
      'catalog handles a later failure while the first request waits ($liveGateway)',
      () async {
        final client = _FailingCatalogClient();
        addTearDown(client.close);
        final future = liveGateway
            ? Api2Gateway(client: client).providers()
            : Api2OperationsGateway(client: client).loadCatalog();
        final checked = expectLater(
          future,
          throwsA(liveGateway ? isA<ApiException>() : isA<ProductException>()),
        );
        await Future<void>.delayed(const Duration(milliseconds: 20));
        client.first.complete([]);
        await checked;
      },
    );
  }

  test(
    'OAuth and MCP actions preserve scope and confirm completed code sign-in',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      final calls = <({String method, Uri uri, dynamic body})>[];
      var attempts = 0;
      server.listen((request) async {
        final text = await utf8.decoder.bind(request).join();
        calls.add((
          method: request.method,
          uri: request.uri,
          body: text.isEmpty ? null : jsonDecode(text),
        ));
        final path = request.uri.path;
        Object? data;
        if (path.endsWith('/connect/oauth') && request.method == 'POST') {
          data = {
            'attemptID': 'attempt-${++attempts}',
            'mode': 'code',
            'url': 'https://example.com/auth',
          };
        } else if (path.contains('/connect/oauth/') &&
            request.method == 'GET') {
          data = {'status': 'complete'};
        } else if (request.method == 'GET') {
          data = <Object>[];
        }
        request.response.statusCode = data == null ? 204 : 200;
        if (data != null) {
          request.response.headers.contentType = ContentType.json;
          request.response.write(jsonEncode({'data': data}));
        }
        await request.response.close();
      });
      final client = Api2Client.connect(
        baseUrl: 'http://${server.address.host}:${server.port}',
        password: 'fixture',
        directory: '/work/first',
        workspace: 'workspace-first',
      );
      addTearDown(client.close);
      final gateway = Api2OperationsGateway(client: client);
      await gateway.listIntegrations();
      await gateway.connectIntegrationKey('provider', 'fixture-key');
      final launch = await gateway.startIntegrationOAuth('provider', 'code');
      // A workspace change while the browser is open must not retarget sign-in.
      gateway.setLocation(
        directory: '/work/second',
        workspace: 'workspace-second',
      );
      await gateway.completeIntegrationOAuth(
        launch.attemptID,
        code: 'fixture-code',
      );
      expect(
        (await gateway.integrationOAuthStatus(launch.attemptID)).state,
        IntegrationAuthState.complete,
      );
      final afterComplete = calls.length;
      expect(
        (await gateway.integrationOAuthStatus(launch.attemptID)).state,
        IntegrationAuthState.complete,
      );
      await gateway.completeIntegrationOAuth(
        launch.attemptID,
        code: 'fixture-code',
      );
      expect(
        calls.length,
        afterComplete,
        reason: 'Completed retries use the cached terminal result',
      );

      final cancel = await gateway.startIntegrationOAuth('provider', 'code');
      gateway.setLocation(
        directory: '/work/third',
        workspace: 'workspace-third',
      );
      await gateway.cancelIntegrationOAuth(cancel.attemptID);
      await expectLater(
        gateway.integrationOAuthStatus(cancel.attemptID),
        throwsA(isA<ProductException>()),
      );

      await gateway.listMcpServers();
      await gateway.connectMcp('local');
      await gateway.disconnectMcp('local');
      await gateway.addMcpServer(
        const McpServerDraft(
          name: 'local',
          kind: McpServerKind.local,
          command: ['node', 'server.js'],
          timeoutMs: 5000,
        ),
        scope: McpConfigScope.runtimeLocation,
      );
      await gateway.addMcpServer(
        const McpServerDraft(
          name: 'remote',
          kind: McpServerKind.remote,
          url: 'https://example.com/mcp',
          timeoutMs: 7000,
        ),
        scope: McpConfigScope.runtimeLocation,
      );
      await gateway.addMcpServer(
        const McpServerDraft(
          name: 'default',
          kind: McpServerKind.remote,
          url: 'https://example.com/mcp',
        ),
        scope: McpConfigScope.runtimeLocation,
      );

      for (final (index, call) in calls.indexed) {
        final suffix = index < afterComplete
            ? 'first'
            : call.uri.path.contains('/mcp')
            ? 'third'
            : 'second';
        expect(
          call.uri.queryParameters['location[directory]'],
          '/work/$suffix',
          reason: call.uri.toString(),
        );
        expect(
          call.uri.queryParameters['location[workspace]'],
          'workspace-$suffix',
          reason: call.uri.toString(),
        );
      }
      final configs = calls
          .where((call) => call.method == 'PUT')
          .map((call) => call.body['config'] as Map)
          .toList();
      expect(configs[0]['timeout'], {
        'startup': 5000,
        'catalog': 5000,
        'execution': 5000,
      });
      expect(configs[1]['timeout'], {
        'startup': 7000,
        'catalog': 7000,
        'execution': 7000,
      });
      expect(configs[2].containsKey('timeout'), isFalse);
    },
  );
}
