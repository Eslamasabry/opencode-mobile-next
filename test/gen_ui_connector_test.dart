import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart' show ToolState;
import 'package:opencode_mobile/builtin/agents/gen_ui_server.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_validation_js.dart';

const _scope = GenUiScope(
  profileID: 'profile',
  sourceId: 'source',
  directory: '/project',
);

Map<String, dynamic> _input({String reason = 'Find design references.'}) => {
  'v': 1,
  'id': 'suggest-design',
  'title': 'Suggested connector',
  'body': <Object>[],
  'connector': {'catalogId': 'com.example/mobbin', 'reason': reason},
};

GenUiParse? _parse(
  Map<String, dynamic> input, {
  bool executed = true,
  bool synthetic = false,
  String status = 'completed',
}) => genUiFromPart(
  Part(
    type: 'tool',
    callID: 'call',
    messageID: 'assistant',
    toolName: 'oc-ui_show',
    synthetic: synthetic,
    toolState: ToolState(status: status, executed: executed, input: input),
  ),
  scope: _scope,
  sessionID: 'session',
  messageID: 'assistant',
);

Future<String> _node(String script, String input) async {
  final process = await Process.start('node', ['-e', script]);
  final output = process.stdout.transform(utf8.decoder).join();
  final errors = process.stderr.drain<void>();
  process.stdin.write(input);
  await process.stdin.close();
  final result = await output.timeout(
    const Duration(seconds: 15),
    onTimeout: () {
      process.kill();
      throw StateError('Connector helper timed out');
    },
  );
  await errors;
  expect(await process.exitCode, 0);
  return result;
}

void main() {
  test(
    'connector suggestion preserves identity and normalizes plain reason',
    () {
      final input = _input(reason: 'Find\u202edesign references.');
      final parsed = _parse(input);
      expect(parsed, isA<GenUiParsed>());
      final card = (parsed as GenUiParsed).card;
      final dynamic suggestion = (card as dynamic).connector;
      expect(suggestion.catalogId, 'com.example/mobbin');
      expect(suggestion.reason, 'Finddesign references.');
      expect(card.scope, _scope);
      expect(card.ask, isNull);
      expect(card.sessionID, 'session');
      expect(card.callID, 'call');
      final changed =
          (_parse(_input(reason: 'Another reason.')) as GenUiParsed).card;
      expect(changed.identity, card.identity);
      expect(changed.revision, isNot(card.revision));
      final plain = Map<String, dynamic>.from(input)..remove('connector');
      final dynamic plainCard = (_parse(plain) as GenUiParsed).card;
      expect(plainCard.connector, isNull);
    },
  );

  test('connector keeps executed tool provenance requirements', () {
    expect(_parse(_input(), executed: false), isNull);
    expect(_parse(_input(), synthetic: true), isNull);
    expect(_parse(_input(), status: 'running'), isNull);
  });

  test(
    'Dart and helper reject malformed or actionable connector payloads',
    () async {
      final accepted = [
        _input(),
        _input(reason: '🌍' * 500),
        {
          ..._input(),
          'connector': {'catalogId': '${'a' * 254}/b', 'reason': 'x'},
        },
      ];
      final rejected = <Map<String, dynamic>>[
        {
          ..._input(),
          'ask': {'kind': 'confirm'},
        },
        {..._input(), 'connector': null},
        for (final id in [
          '',
          'mobbin',
          '/mobbin',
          'com.example/',
          'a/b/c',
          'https://example.com/mcp',
          'com.example/工具',
          'com.example/mobbin\n',
          '${'a' * 255}/b',
        ])
          {
            ..._input(),
            'connector': {'catalogId': id, 'reason': 'x'},
          },
        for (final reason in ['', '\u202e', 'x' * 501]) _input(reason: reason),
        for (final key in ['url', 'command', 'headers', 'token'])
          {
            ..._input(),
            'connector': {
              'catalogId': 'com.example/mobbin',
              'reason': 'x',
              key: 'forbidden',
            },
          },
        {
          ..._input(),
          'connector': {'catalogId': 'com.example/mobbin'},
        },
        {
          ..._input(),
          'body': [
            for (var i = 0; i < 20; i++) {'type': 'text', 'text': 'x' * 2000},
          ],
        },
      ];
      final inputs = [...accepted, ...rejected];
      final js =
          jsonDecode(
                await _node('''
$genUiValidationJavascript
let input = ''; process.stdin.setEncoding('utf8');
process.stdin.on('data', part => input += part);
process.stdin.on('end', () => process.stdout.write(JSON.stringify(
  JSON.parse(input).map(value => {
    try { return {value: normalizeGenUiCard(value)}; }
    catch (_) { return {rejected: true}; }
  })
)));
''', jsonEncode(inputs)),
              )
              as List;
      for (var i = 0; i < inputs.length; i++) {
        final parsed = _parse(inputs[i]);
        if (i < accepted.length) {
          expect(parsed, isA<GenUiParsed>(), reason: 'accepted $i');
          expect(js[i]['rejected'], isNull, reason: 'accepted JS $i');
          final dynamic suggestion =
              ((parsed as GenUiParsed).card as dynamic).connector;
          expect(js[i]['value']['connector'], {
            'catalogId': suggestion.catalogId,
            'reason': suggestion.reason,
          });
        } else {
          expect(parsed, isA<GenUiUnreadable>(), reason: 'rejected $i');
          expect(js[i]['rejected'], isTrue, reason: 'rejected JS $i');
        }
      }
    },
  );

  test(
    'MCP helper advertises connector suggestions and accepts their calls',
    () async {
      final temporary = await Directory.systemTemp.createTemp(
        'gen-ui-connector-',
      );
      addTearDown(() => temporary.delete(recursive: true));
      final marker = File('${temporary.path}/enabled');
      await marker.writeAsString('enabled\n');
      final requests = [
        {
          'jsonrpc': '2.0',
          'id': 1,
          'method': 'initialize',
          'params': {
            'protocolVersion': '2024-11-05',
            'capabilities': <String, Object>{},
            'clientInfo': {'name': 'connector-test', 'version': '1'},
          },
        },
        {'jsonrpc': '2.0', 'method': 'notifications/initialized'},
        {
          'jsonrpc': '2.0',
          'id': 2,
          'method': 'tools/list',
          'params': <String, Object>{},
        },
        {
          'jsonrpc': '2.0',
          'id': 3,
          'method': 'tools/call',
          'params': {'name': 'show', 'arguments': _input()},
        },
      ];
      final output = await _node(
        genUiServerScript(enabledMarkerPath: marker.path),
        '${requests.map(jsonEncode).join('\n')}\n',
      );
      final replies = output
          .trim()
          .split('\n')
          .map((line) => jsonDecode(line) as Map)
          .toList();
      final tool = replies[1]['result']['tools'][0] as Map;
      expect(tool['inputSchema']['properties']['connector'], isA<Map>());
      expect(tool['description'], contains('catalog'));
      expect(replies[2]['result']['isError'], isNot(true));
    },
  );
}
