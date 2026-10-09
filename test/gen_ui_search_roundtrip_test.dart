import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/gen_ui_server.dart';
import 'package:opencode_mobile/domain/mcp_connector_search.dart';
import 'package:opencode_mobile/domain/setup_registry.dart';
import 'package:opencode_mobile/state/mcp_connector_search_bridge.dart';

void main() {
  test(
    'generated helper searches loaded catalogue through the real app bridge',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'connector-roundtrip-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final marker = File('${directory.path}/enabled');
      await marker.writeAsString('enabled\n');
      final entries = [
        RegistryEntry.fromJson({
          'name': 'com.example/design',
          'version': '1.0.0',
          'title': 'Design references',
          'description':
              'Useful design references. token=private-value https://private.example.test',
          'remotes': [
            {'type': 'streamable-http', 'url': 'https://example.com/mcp'},
          ],
        })!,
      ];
      var calls = 0;
      final bridge = await McpConnectorSearchBridge.start(
        search: (request) async {
          calls++;
          expect(request.keys, unorderedEquals(['arguments', 'directory']));
          expect(request['directory'], Directory.current.path);
          return searchMcpConnectors(
            arguments: request['arguments'],
            entries: entries,
            connected: {'design': true},
          );
        },
        isCurrent: () => true,
      );
      addTearDown(bridge.close);
      await File('${marker.path}.search.json').writeAsString(
        jsonEncode({
          'endpoint': bridge.endpoint.toString(),
          'bearer': bridge.bearer,
        }),
      );
      final script = File('${directory.path}/helper.cjs');
      await script.writeAsString(
        genUiServerScript(enabledMarkerPath: marker.path),
      );
      final process = await Process.start('node', [script.path]);
      final output = process.stdout.transform(utf8.decoder).join();
      final errors = process.stderr.transform(utf8.decoder).join();
      final requests = [
        {
          'jsonrpc': '2.0',
          'id': 1,
          'method': 'initialize',
          'params': {
            'protocolVersion': '2024-11-05',
            'capabilities': <String, Object>{},
            'clientInfo': {'name': 'roundtrip-test', 'version': '1'},
          },
        },
        {'jsonrpc': '2.0', 'method': 'notifications/initialized'},
        {
          'jsonrpc': '2.0',
          'id': 2,
          'method': 'tools/call',
          'params': {
            'name': 'find_connectors',
            'arguments': {'query': '  design  ', 'limit': 1},
          },
        },
      ];
      for (final request in requests) {
        process.stdin.writeln(jsonEncode(request));
      }
      await process.stdin.close();
      final encoded = await output.timeout(
        const Duration(seconds: 10),
        onTimeout: () {
          process.kill();
          throw StateError('Helper roundtrip timed out');
        },
      );
      expect(await process.exitCode, 0);
      expect(await errors, isEmpty);
      final replies = encoded
          .trim()
          .split('\n')
          .map((line) => jsonDecode(line) as Map)
          .toList();
      final result = replies.last['result'] as Map;
      final structured = result['structuredContent'] as Map;
      expect(structured['status'], 'ok');
      expect(structured['matches'], [
        {
          'catalogId': 'com.example/design',
          'name': 'Design references',
          'description': 'Useful design references. token=•••',
          'runtime': 'hosted',
          'needsSignIn': 'unknown',
          'connected': true,
        },
      ]);
      expect(
        jsonDecode((result['content'] as List).single['text'] as String),
        structured,
      );
      expect(calls, 1);
      expect(encoded, isNot(contains(bridge.bearer)));
      expect(encoded, isNot(contains('https://example.com')));
      expect(encoded, isNot(contains('private-value')));
    },
  );
}
