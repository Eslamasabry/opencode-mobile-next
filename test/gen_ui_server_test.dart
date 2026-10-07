import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/gen_ui_server.dart';

const _card = {'v': 1, 'id': 'sample', 'title': 'Sample', 'body': <Object>[]};

void main() {
  late Directory directory;
  late File marker;
  late _Helper helper;

  setUp(() async {
    directory = await Directory.systemTemp.createTemp('gen-ui-server-');
    marker = File('${directory.path}/enabled');
    await marker.writeAsString('enabled\n');
    final script = File('${directory.path}/server.cjs');
    await script.writeAsString(
      genUiServerScript(enabledMarkerPath: marker.path),
    );
    helper = await _Helper.start(script.path);
  });

  tearDown(() async {
    await helper.close();
    await directory.delete(recursive: true);
  });

  test('initializes, handles notifications and advertises only show', () async {
    final initialized = await helper.initialize(version: '2024-11-05');
    expect(initialized['protocolVersion'], '2024-11-05');
    expect(initialized['capabilities'], {
      'tools': {'listChanged': false},
    });
    helper.notification('notifications/cancelled', {'requestId': 'old'});
    final listed = await helper.request('tools/list');
    final tools = (listed['result'] as Map)['tools'] as List;
    expect(tools, hasLength(1));
    final tool = tools.single as Map;
    expect(tool['name'], 'show');
    final schema = tool['inputSchema'] as Map;
    expect(schema['additionalProperties'], isFalse);
    expect(schema['required'], ['v', 'id', 'title', 'body']);
    final props = schema['properties'] as Map;
    expect(((props['body'] as Map)['items'] as Map)['oneOf'], hasLength(10));
    expect((props['ask'] as Map)['oneOf'], hasLength(4));
    expect((await helper.request('ping'))['result'], isEmpty);
    expect((await helper.request('unknown'))['error'], {
      'code': -32601,
      'message': 'Method not found',
    });
  });

  test('negotiates unknown version and requires initialization', () async {
    final early = await helper.request('tools/list');
    expect((early['error'] as Map)['code'], -32000);
    final initialized = await helper.initialize(version: '1900-01-01');
    expect(initialized['protocolVersion'], '2025-11-25');
    final duplicate = await helper.request('initialize', {
      'protocolVersion': '2025-11-25',
      'capabilities': <String, Object>{},
      'clientInfo': {'name': 'test', 'version': '1'},
    });
    expect((duplicate['error'] as Map)['code'], -32602);
  });

  test('valid call returns immediately without claiming delivery', () async {
    await helper.initialize();
    final response = await helper.call(_card);
    final result = response['result'] as Map;
    expect(result['isError'], isNot(true));
    final text = ((result['content'] as List).single as Map)['text'] as String;
    expect(text, contains('Card accepted for display'));
    expect(text, contains('This call does not return their answer.'));
    expect(text, isNot(contains('Card displayed')));
    expect(result.keys, unorderedEquals(['content']));
  });

  test(
    'invalid, disabled and unknown calls share safe generic error',
    () async {
      await helper.initialize();
      final bad = await helper.call({..._card, 'unknown': 'private-sentinel'});
      final unsupported = await helper.call({
        ..._card,
        'ask': {'kind': 'voice'},
      });
      final unknown = await helper.request('tools/call', {
        'name': 'private-sentinel',
        'arguments': _card,
      });
      await marker.delete();
      final disabled = await helper.call(_card);
      for (final reply in [bad, unsupported, unknown, disabled]) {
        expect(reply['result'], {
          'isError': true,
          'content': [
            {'type': 'text', 'text': 'Agent card unavailable or invalid.'},
          ],
        });
        expect(jsonEncode(reply), isNot(contains('private-sentinel')));
      }
    },
  );

  test(
    'every call rereads marker and refuses symlinks or bad contents',
    () async {
      await helper.initialize();
      expect((await helper.call(_card))['result'], isNot(contains('isError')));
      await marker.writeAsString('disabled');
      expect(((await helper.call(_card))['result'] as Map)['isError'], isTrue);
      await marker.delete();
      final target = File('${directory.path}/target');
      await target.writeAsString('enabled\n');
      await Link(marker.path).create(target.path);
      expect(((await helper.call(_card))['result'] as Map)['isError'], isTrue);
      await Link(marker.path).delete();
      await marker.writeAsString('enabled\n');
      expect((await helper.call(_card))['result'], isNot(contains('isError')));
    },
  );

  test('bounds raw frame bytes before parsing and recovers next frame', () async {
    await helper.initialize();
    helper.raw(utf8.encode('${'x' * 32768}${'é' * 16385}\n'));
    expect((await helper.next())['error'], {
      'code': -32700,
      'message': 'Frame too large',
    });
    expect((await helper.request('ping'))['result'], isEmpty);
    // This frame is precisely 64 KiB and therefore a parse error, not overflow.
    helper.raw(utf8.encode('${'x' * 65536}\n'));
    expect(((await helper.next())['error'] as Map)['message'], 'Parse error');
    expect((await helper.request('ping'))['result'], isEmpty);
  });

  test(
    'split frames, invalid UTF-8 and malformed JSON stay protocol-only',
    () async {
      await helper.initialize();
      helper.raw(utf8.encode('{"jsonrpc":"2.0","id":1000,"method":'));
      helper.raw(utf8.encode('"ping"}\n'));
      expect(await helper.next(), {
        'jsonrpc': '2.0',
        'id': 1000,
        'result': <String, Object>{},
      });
      helper.raw([0xff, 10]);
      expect(((await helper.next())['error'] as Map)['code'], -32700);
      helper.raw(utf8.encode('private-sentinel\n'));
      final bad = await helper.next();
      expect((bad['error'] as Map)['code'], -32700);
      expect(jsonEncode(bad), isNot(contains('private-sentinel')));
      helper.raw(utf8.encode('[]\n'));
      expect(((await helper.next())['error'] as Map)['code'], -32600);
      expect((await helper.request('ping'))['result'], isEmpty);
    },
  );

  test('marker path rejects relative or embedded control paths', () {
    for (final path in ['relative', '/tmp/bad\npath', '/tmp/bad\u0000path']) {
      expect(
        () => genUiServerScript(enabledMarkerPath: path),
        throwsArgumentError,
      );
    }
  });
}

class _Helper {
  _Helper(this.process)
    : output = StreamIterator(
        process.stdout.transform(utf8.decoder).transform(const LineSplitter()),
      ) {
    errors = process.stderr.fold<int>(
      0,
      (count, bytes) => count + bytes.length,
    );
  }

  static Future<_Helper> start(String script) async =>
      _Helper(await Process.start('node', [script]));

  final Process process;
  final StreamIterator<String> output;
  late final Future<int> errors;
  var _nextId = 1;

  Future<Map<String, dynamic>> initialize({
    String version = '2025-11-25',
  }) async {
    final reply = await request('initialize', {
      'protocolVersion': version,
      'capabilities': <String, Object>{},
      'clientInfo': {'name': 'test', 'version': '1'},
    });
    notification('notifications/initialized');
    return reply['result'] as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> call(Map<String, Object> card) =>
      request('tools/call', {'name': 'show', 'arguments': card});

  Future<Map<String, dynamic>> request(
    String method, [
    Map<String, Object>? params,
  ]) async {
    final id = _nextId++;
    raw(
      utf8.encode(
        '${jsonEncode({'jsonrpc': '2.0', 'id': id, 'method': method, 'params': ?params})}\n',
      ),
    );
    final reply = await next();
    expect(reply['id'], id);
    return reply;
  }

  void notification(String method, [Map<String, Object>? params]) => raw(
    utf8.encode(
      '${jsonEncode({'jsonrpc': '2.0', 'method': method, 'params': ?params})}\n',
    ),
  );

  void raw(List<int> bytes) => process.stdin.add(bytes);

  Future<Map<String, dynamic>> next() async {
    expect(await output.moveNext().timeout(const Duration(seconds: 5)), isTrue);
    return jsonDecode(output.current) as Map<String, dynamic>;
  }

  Future<void> close() async {
    await process.stdin.close();
    try {
      expect(await process.exitCode.timeout(const Duration(seconds: 5)), 0);
    } on TimeoutException {
      process.kill();
      await process.exitCode;
      rethrow;
    } finally {
      await output.cancel();
    }
    expect(await errors, 0, reason: 'Helper must never log raw input/errors.');
  }
}
