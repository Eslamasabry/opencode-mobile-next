import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/plugin_mapper.dart';
import 'package:opencode_mobile/domain/plugin_inventory.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

class _Adapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Object payload = {'data': []};
  int status = 200;
  Completer<void>? gate;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    await gate?.future;
    return ResponseBody.fromString(
      jsonEncode(payload),
      status,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  test(
    'plugin metadata keeps declared source and never exposes raw diagnostics',
    () {
      final plugin = mapPluginInfo({
        'id': 'reviewer',
        'status': 'failed',
        'tui': true,
        'source': {'type': 'package', 'package': '@example/reviewer@1.2.3'},
        'error': 'token=synthetic-secret',
        'options': {'apiKey': 'synthetic-secret'},
      });
      expect(plugin.id, 'reviewer');
      expect(plugin.status, PluginStatus.failed);
      expect(plugin.source, PluginSourceKind.package);
      expect(plugin.packageName, '@example/reviewer@1.2.3');
      expect(plugin.terminalUi, isTrue);
      final unsafe = mapPluginInfo({
        'id': 'https://user:synthetic-secret@example.com',
        'status': 'failed',
        'tui': false,
        'source': {
          'type': 'package',
          'package':
              'https://user:synthetic-secret@example.com/plugin?token=secret',
        },
        'error': 'synthetic-secret',
      });
      expect(unsafe.id, isNull);
      expect(unsafe.packageName, isNull);
      expect(
        mapPluginInfo({
          'status': 'failed',
          'tui': false,
          'source': {'type': 'local', 'path': '/private/credentials/plugin.js'},
        }).source,
        PluginSourceKind.local,
      );
    },
  );

  test(
    'plugin GET pins location and preserves failure/unknown metadata',
    () async {
      final client = Api2Client.connect(
        baseUrl: 'https://plugins.example',
        password: 'synthetic-password',
        directory: '/first',
        workspace: 'wrk_one',
      );
      addTearDown(client.close);
      final adapter = _Adapter()
        ..payload = {
          'data': [
            {
              'id': 'builtin',
              'source': {'type': 'builtin'},
              'status': 'active',
              'tui': false,
            },
            {
              'source': {'type': 'local', 'path': '/private/plugin.js'},
              'status': 'failed',
              'error': 'synthetic-secret',
              'tui': true,
            },
            {
              'id': 'future',
              'source': {'type': 'future'},
              'status': 'paused',
              'tui': false,
            },
          ],
        }
        ..gate = Completer<void>();
      client.transport.dio.httpClientAdapter = adapter;
      final gateway = Api2OperationsGateway(client: client);
      final pending = gateway.listPlugins();
      client.setLocation(directory: '/second', workspace: 'wrk_two');
      adapter.gate!.complete();
      final plugins = await pending;
      final request = adapter.requests.single;
      expect(request.method, 'GET');
      expect(request.uri.path, '/api/plugin');
      expect(request.queryParameters, {
        'location[directory]': '/first',
        'location[workspace]': 'wrk_one',
      });
      expect(request.uri.toString(), isNot(contains('synthetic-password')));
      expect(plugins[1].id, isNull);
      expect(plugins[1].status, PluginStatus.failed);
      expect(plugins[2].status, PluginStatus.unknown);
      expect(plugins[2].source, PluginSourceKind.unknown);
    },
  );

  test(
    'invalid inventories and server failures never become empty success or raw errors',
    () async {
      final client = Api2Client.connect(
        baseUrl: 'https://plugins.example',
        password: 'fixture',
      );
      addTearDown(client.close);
      final adapter = _Adapter();
      client.transport.dio.httpClientAdapter = adapter;
      final gateway = Api2OperationsGateway(client: client);
      for (final payload in [
        {'unexpected': []},
        {
          'data': [null],
        },
        {
          'data': [
            {'status': 'active'},
          ],
        },
      ]) {
        adapter.payload = payload;
        await expectLater(
          gateway.listPlugins(),
          throwsA(
            isA<ProductException>().having(
              (error) => error.message,
              'message',
              'Could not load plugins. Try again.',
            ),
          ),
        );
      }
      adapter.status = 500;
      adapter.payload = {'message': 'synthetic-secret'};
      await expectLater(
        gateway.listPlugins(),
        throwsA(
          isA<ProductException>().having(
            (error) => error.toString(),
            'safe error',
            isNot(contains('synthetic-secret')),
          ),
        ),
      );
    },
  );

  test('plugin events only forward an inventory refresh hint', () {
    for (final type in ['plugin.added', 'plugin.updated']) {
      final events = Api2EventAdapter().adapt(
        Api2EventEnvelope.fromJson({
          'type': type,
          'data': {'id': 'plugin', 'error': 'synthetic-secret'},
        }),
      );
      expect(events.single.type, type);
      expect(events.single.properties, isEmpty);
    }
  });
}
