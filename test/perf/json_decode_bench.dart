// JSON decode + model mapping micro-benchmark for big chats and lists.
//
// Not part of the suite (no `_test` suffix). Run it on its own:
//
//   TZ=UTC flutter test test/perf/json_decode_bench.dart
//
// It prints one table: per path and payload size, the median and worst time
// of decoding and of mapping to the app's models, and how long the main
// isolate stalls when the decode runs inline vs in `Isolate.run`. The
// results and the decision are in docs/qa/json-decode-2026-10-28/README.md.
//
// Payloads reuse the shapes from test/fixtures/coverage (OpenCode 2
// messages and sessions, Paseo agents) with tool output inflated to reach
// a realistic big-chat size.

import 'dart:convert';
import 'dart:io';
import 'dart:isolate';
import 'dart:math';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/paseo/mappers.dart';

const _warmup = 3;
const _runs = 10;

final _fused = const Utf8Decoder().fuse(const JsonDecoder());

void main() {
  test('json decode benchmark', () async {
    final out = StringBuffer()
      ..writeln()
      ..writeln(
        '| path | payload | items | decode med/max ms | '
        'map med/max ms | main-isolate total med ms |',
      )
      ..writeln('|---|---|---|---|---|---|');

    // OpenCode 2: one `GET /session/{id}/message?limit=100` page. Dio decodes
    // the bytes (fused UTF-8 + JSON); the gateway then maps on the main
    // isolate.
    for (final outputChars in [2000, 8000, 24000]) {
      final bytes = _oc2MessagePage(100, outputChars);
      late Object? decoded;
      final decode = _time(() => decoded = _fused.convert(bytes));
      late int count;
      final map = _time(() {
        final page = Api2Page.fromJson(decoded, Api2Message.fromJson);
        count = mapApi2Messages('ses_1', page.data.reversed.toList()).length;
      });
      out.writeln(_row('OC2 messages page', bytes.length, count, decode, map));
    }

    // Paseo: `fetch_agent_timeline_response` with limit 0 (the whole
    // history) arrives as one WebSocket text frame, capped at 8 MB.
    for (final target in [2, 4, 8]) {
      final frame = _paseoTimelineFrame(target * 1024 * 1024 - 4096);
      late Map<String, dynamic> payload;
      final decode = _time(() {
        final json = jsonDecode(frame) as Map<String, dynamic>;
        payload =
            (json['message'] as Map<String, dynamic>)['payload']
                as Map<String, dynamic>;
      });
      late int count;
      final map = _time(() {
        count = paseoTimelineMessages('a1', payload, busy: false).length;
      });
      out.writeln(
        _row(
          'Paseo full timeline',
          utf8.encode(frame).length,
          count,
          decode,
          map,
        ),
      );
    }

    // Lists: the OpenCode 2 session page (limit 100) and the Paseo agent page
    // at its 200-row cap.
    {
      final bytes = _oc2SessionPage(100);
      late Object? decoded;
      final decode = _time(() => decoded = _fused.convert(bytes));
      late int count;
      final map = _time(() {
        final page = Api2Page.fromJson(decoded, Api2Session.fromJson);
        count = page.data.map(mapApi2Session).length;
      });
      out.writeln(_row('OC2 session list', bytes.length, count, decode, map));
    }
    {
      final frame = _paseoAgentsFrame(200);
      late Map<String, dynamic> payload;
      final decode = _time(() {
        final json = jsonDecode(frame) as Map<String, dynamic>;
        payload =
            (json['message'] as Map<String, dynamic>)['payload']
                as Map<String, dynamic>;
      });
      late int count;
      final map = _time(() {
        count = [
          for (final e in payload['entries'] as List)
            paseoSession(
              (e as Map<String, dynamic>)['agent'] as Map<String, dynamic>,
            ),
        ].length;
      });
      out.writeln(
        _row('Paseo agent list', utf8.encode(frame).length, count, decode, map),
      );
    }

    // Isolate cost: a round trip for a tiny payload, and the longest
    // main-isolate stall while an 8 MB frame decodes inline vs in a worker.
    out
      ..writeln()
      ..writeln('| isolate measure | med ms | max ms |')
      ..writeln('|---|---|---|');
    const tiny = '{"type":"session","message":{"type":"pong","payload":{}}}';
    final spawn = await _timeAsync(() => Isolate.run(() => jsonDecode(tiny)));
    out.writeln('| Isolate.run round trip, 60 B | ${_fmt(spawn)} |');
    final small = utf8.encode(jsonEncode(_oc2MessagePageJson(4, 800)));
    final smallInline = _time(() => _fused.convert(small));
    final smallIsolate = await _timeAsync(
      () => Isolate.run(() => _fused.convert(small)),
    );
    out
      ..writeln(
        '| inline decode, ${_kb(small.length)} | ${_fmt(smallInline)} |',
      )
      ..writeln(
        '| Isolate.run decode, ${_kb(small.length)} | ${_fmt(smallIsolate)} |',
      );
    final big = _paseoTimelineFrame(8 * 1024 * 1024 - 4096);
    final inlineStall = await _stall(() async => jsonDecode(big));
    final isolateStall = await _stall(() => Isolate.run(() => jsonDecode(big)));
    final isolateWall = await _timeAsync(
      () => Isolate.run(() => jsonDecode(big)),
    );
    List<Object?> decodeAndMap(String frame) {
      final json = jsonDecode(frame) as Map<String, dynamic>;
      final payload =
          (json['message'] as Map<String, dynamic>)['payload']
              as Map<String, dynamic>;
      return [json, paseoTimelineMessages('a1', payload, busy: false)];
    }

    final bothInline = await _stall(() async => decodeAndMap(big));
    final bothIsolate = await _stall(
      () => Isolate.run(() => decodeAndMap(big)),
    );
    final decodedBig = jsonDecode(big) as Map<String, dynamic>;
    final payloadBig =
        (decodedBig['message'] as Map<String, dynamic>)['payload']
            as Map<String, dynamic>;
    final copyIn = await _stall(
      () => Isolate.run(
        () => paseoTimelineMessages('a1', payloadBig, busy: false).length,
      ),
    );
    out
      ..writeln(
        '| longest main stall, 8 MB decode + map inline | ${_fmt(bothInline)} |',
      )
      ..writeln(
        '| longest main stall, 8 MB decode + map in one Isolate.run | '
        '${_fmt(bothIsolate)} |',
      )
      ..writeln(
        '| longest main stall, map only, decoded 8 MB copied into '
        'Isolate.run | ${_fmt(copyIn)} |',
      )
      ..writeln('| longest main stall, 8 MB inline | ${_fmt(inlineStall)} |')
      ..writeln(
        '| longest main stall, 8 MB in Isolate.run | ${_fmt(isolateStall)} |',
      )
      ..writeln(
        '| wall time, 8 MB in Isolate.run (UI free) | ${_fmt(isolateWall)} |',
      );

    stdout.writeln(out);
  }, timeout: const Timeout(Duration(minutes: 10)));
}

// ---------------------------------------------------------------- timing

typedef _Stats = ({double median, double max});

_Stats _time(void Function() body) {
  for (var i = 0; i < _warmup; i++) {
    body();
  }
  final samples = <double>[];
  for (var i = 0; i < _runs; i++) {
    final sw = Stopwatch()..start();
    body();
    samples.add(sw.elapsedMicroseconds / 1000);
  }
  return _stats(samples);
}

Future<_Stats> _timeAsync(Future<Object?> Function() body) async {
  for (var i = 0; i < _warmup; i++) {
    await body();
  }
  final samples = <double>[];
  for (var i = 0; i < _runs; i++) {
    final sw = Stopwatch()..start();
    await body();
    samples.add(sw.elapsedMicroseconds / 1000);
  }
  return _stats(samples);
}

/// The longest gap between two main-isolate event-loop turns while [body]
/// runs: what a frame would have waited for.
Future<_Stats> _stall(Future<Object?> Function() body) async {
  final samples = <double>[];
  for (var i = 0; i < _warmup + _runs; i++) {
    var running = true;
    var worst = 0;
    final ticker = () async {
      final sw = Stopwatch()..start();
      var last = 0;
      while (running) {
        await Future<void>.delayed(Duration.zero);
        final now = sw.elapsedMicroseconds;
        worst = max(worst, now - last);
        last = now;
      }
    }();
    await Future<void>.delayed(const Duration(milliseconds: 5));
    await body();
    await Future<void>.delayed(const Duration(milliseconds: 5));
    running = false;
    await ticker;
    if (i >= _warmup) samples.add(worst / 1000);
  }
  return _stats(samples);
}

_Stats _stats(List<double> samples) {
  samples.sort();
  return (median: samples[samples.length ~/ 2], max: samples.last);
}

String _fmt(_Stats s) =>
    '${s.median.toStringAsFixed(1)} | ${s.max.toStringAsFixed(1)}';

String _kb(int bytes) => bytes >= 1024 * 1024
    ? '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB'
    : '${(bytes / 1024).toStringAsFixed(1)} KB';

String _row(String path, int bytes, int count, _Stats decode, _Stats map) =>
    '| $path | ${_kb(bytes)} | $count | '
    '${decode.median.toStringAsFixed(1)} / ${decode.max.toStringAsFixed(1)} | '
    '${map.median.toStringAsFixed(1)} / ${map.max.toStringAsFixed(1)} | '
    '${(decode.median + map.median).toStringAsFixed(1)} |';

// -------------------------------------------------------------- payloads

final _rng = Random(7);
const _words = [
  'final',
  'return',
  'await',
  'widget',
  'state',
  'session',
  'message',
  'error:',
  'PASS',
  'test/upload_screen_test.dart',
  '+12',
  'import',
  "'package:flutter/material.dart';",
  '{',
  '}',
  '=>',
  'null',
  'const',
  'flutter',
  'analyze',
  'No issues found!',
  'lib/ui/kit/kit_button.dart',
  '"quoted"',
  r'C:\path',
  'tab\there',
  'مرحبا',
  '✓',
];

/// Log- or code-like text with newlines, quotes and escapes, like a tool's
/// output.
String _text(int chars) {
  final b = StringBuffer();
  var line = 0;
  while (b.length < chars) {
    b
      ..write(_words[_rng.nextInt(_words.length)])
      ..write(' ');
    if (++line % 9 == 0) b.write('\n');
  }
  return b.toString().substring(0, chars);
}

Map<String, dynamic> _fixture(String name) =>
    jsonDecode(File('test/fixtures/coverage/$name').readAsStringSync())
        as Map<String, dynamic>;

Object? _case(Map<String, dynamic> fixture, String id) =>
    (fixture['cases'] as List).cast<Map<String, dynamic>>().firstWhere(
      (c) => c['id'] == id,
    )['payload'];

Object? _copy(Object? v) => jsonDecode(jsonEncode(v));

Uint8List _oc2MessagePage(int count, int outputChars) =>
    utf8.encode(jsonEncode(_oc2MessagePageJson(count, outputChars)));

/// A desc-ordered message page: turns of one prompt followed by assistant
/// steps that each ran a tool from the fixture set, with outputs inflated.
Map<String, dynamic> _oc2MessagePageJson(int count, int outputChars) {
  final fixture = _fixture('oc2_messages_samples.json');
  List<Map<String, dynamic>> payload(String id) =>
      (_case(fixture, id) as List).cast<Map<String, dynamic>>();
  final user = payload('user_prompt_all_fields').first;
  final reply = payload('assistant_all_fields').last;
  final tools = [
    for (final id in [
      'tool_bash',
      'tool_read',
      'tool_edit',
      'tool_grep',
      'tool_glob',
      'tool_write',
    ])
      payload(id).last,
  ];
  final messages = <Map<String, dynamic>>[];
  var n = 0;
  while (messages.length < count) {
    final prompt = _copy(user) as Map<String, dynamic>
      ..['id'] = 'msg_${n++}'
      ..['text'] = _text(300)
      ..['files'] = const [];
    messages.add(prompt);
    for (var step = 0; step < 8 && messages.length < count; step++) {
      final message =
          _copy(step == 7 ? reply : tools[step % tools.length])
                as Map<String, dynamic>
            ..['id'] = 'msg_${n++}';
      for (final part in (message['content'] as List)) {
        final p = part as Map<String, dynamic>;
        if (p['type'] == 'text' || p['type'] == 'reasoning') {
          p['text'] = _text(600);
        }
        final state = p['state'];
        if (p['type'] == 'tool' && state is Map<String, dynamic>) {
          final output = _text(outputChars);
          state['content'] = [
            {'type': 'text', 'text': output},
          ];
          final meta = state['metadata'];
          if (meta is Map<String, dynamic> && meta.containsKey('output')) {
            meta['output'] = output;
          }
        }
      }
      messages.add(message);
    }
  }
  return {
    'data': messages.reversed.toList(),
    'cursor': {'next': 'cur_next', 'previous': null},
  };
}

Uint8List _oc2SessionPage(int count) {
  final session = _case(_fixture('oc2_lists_samples.json'), 'session_full');
  return utf8.encode(
    jsonEncode({
      'data': [
        for (var i = 0; i < count; i++)
          (_copy(session) as Map<String, dynamic>)
            ..['id'] = 'ses_$i'
            ..['title'] = _text(60),
      ],
      'cursor': {'next': 'cur_next'},
    }),
  );
}

String _frame(String type, Map<String, dynamic> payload) => jsonEncode({
  'type': 'session',
  'message': {
    'type': type,
    'payload': {'requestId': 'r1', ...payload},
  },
});

String _paseoAgentsFrame(int count) {
  final page =
      _case(_fixture('paseo_lists_samples.json'), 'page_two_agents')
          as Map<String, dynamic>;
  final entry = (page['entries'] as List).first;
  return _frame('fetch_agents_response', {
    'entries': [
      for (var i = 0; i < count; i++)
        (_copy(entry) as Map<String, dynamic>)
          ..['agent']['id'] = 'agt_$i'
          ..['agent']['title'] = _text(60),
    ],
    'pageInfo': {'hasMore': false},
  });
}

/// A whole Claude Code history as Paseo projects it: per turn a prompt,
/// reasoning, a shell, a read, an edit and a search step, then the answer.
String _paseoTimelineFrame(int targetBytes) {
  final entries = <Map<String, dynamic>>[];
  var seq = 0;
  var bytes = 0;
  final t0 = DateTime.utc(2026, 10, 1);
  Map<String, dynamic> entry(Map<String, dynamic> item) {
    final e = {
      'provider': 'claude',
      'item': item,
      'timestamp': t0.add(Duration(seconds: seq)).toIso8601String(),
      'seqStart': ++seq,
    };
    bytes += jsonEncode(e).length;
    return e;
  }

  while (bytes < targetBytes) {
    final turn = seq;
    entries
      ..add(
        entry({
          'type': 'user_message',
          'text': _text(300),
          'messageId': 'u$turn',
        }),
      )
      ..add(entry({'type': 'reasoning', 'text': _text(800)}))
      ..add(
        entry({
          'type': 'tool_call',
          'callId': 'toolu_${turn}_sh',
          'name': 'Bash',
          'status': 'completed',
          'detail': {
            'type': 'shell',
            'command': 'flutter test test/upload_screen_test.dart',
            'output': _text(6000),
            'exitCode': 0,
          },
        }),
      )
      ..add(
        entry({
          'type': 'tool_call',
          'callId': 'toolu_${turn}_rd',
          'name': 'Read',
          'status': 'completed',
          'detail': {
            'type': 'read',
            'filePath': '/root/projects/app/lib/upload/upload_screen.dart',
            'content': _text(8000),
          },
        }),
      )
      ..add(
        entry({
          'type': 'tool_call',
          'callId': 'toolu_${turn}_ed',
          'name': 'Edit',
          'status': 'completed',
          'detail': {
            'type': 'edit',
            'filePath': '/root/projects/app/lib/upload/upload_screen.dart',
            'oldString': _text(400),
            'newString': _text(500),
            'unifiedDiff': _text(1500),
          },
        }),
      )
      ..add(
        entry({
          'type': 'tool_call',
          'callId': 'toolu_${turn}_gr',
          'name': 'Grep',
          'status': 'completed',
          'detail': {
            'type': 'search',
            'pattern': 'retry',
            'content': _text(2000),
            'numMatches': 14,
            'numFiles': 3,
          },
        }),
      )
      ..add(
        entry({
          'type': 'assistant_message',
          'text': _text(1200),
          'messageId': 'm$turn',
        }),
      );
  }
  return _frame('fetch_agent_timeline_response', {
    'agentId': 'a1',
    'epoch': 'e1',
    'endCursor': {'epoch': 'e1', 'seq': seq},
    'hasOlder': false,
    'hasNewer': false,
    'entries': entries,
  });
}
