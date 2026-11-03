import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/ui/screens/perf_trace_section.dart';

/// Answers every request with [status] and a JSON body of [bytes] length,
/// declared in Content-Length the way a real server does.
class _SizedAdapter implements HttpClientAdapter {
  _SizedAdapter({this.status = 200, this.bytes = 64});

  final int status;
  final int bytes;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final body = utf8.encode('"${'x' * (bytes - 2)}"');
    return ResponseBody.fromBytes(
      body,
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
        Headers.contentLengthHeader: ['${body.length}'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

PerfSpan _only(String name) =>
    PerfTrace.spans.singleWhere((span) => span.name == name);

void main() {
  final logged = <String>[];

  setUp(() {
    PerfTrace.resetForTesting();
    PromptTrace.resetForTesting();
    logged.clear();
    PerfTrace.logSink = logged.add;
  });

  tearDown(() => PerfTrace.logSink = null);

  group('spans', () {
    test('nest through zones: a child records the open parent', () async {
      await PerfTrace.span('outer', () async {
        await PerfTrace.span('inner', () async {
          PerfTrace.mark('inside');
        });
        PerfTrace.spanSync('sync', () => 1);
      });

      expect(_only('inner').parent, 'outer');
      expect(_only('sync').parent, 'outer');
      expect(_only('inside').parent, 'inner');
      expect(_only('inside').isMark, isTrue);
      expect(_only('outer').parent, isNull);
      expect(PerfTrace.spans.map((s) => s.name), [
        'inside',
        'inner',
        'sync',
        'outer',
      ]);
    });

    test('work that outlives its parent is not attributed to it', () async {
      final later = Completer<void>();
      Future<void>? background;
      await PerfTrace.span('connect', () async {
        // An event stream started inside connect keeps its zone; what it
        // does after connect finished must not claim connect as parent.
        background = later.future.then(
          (_) => PerfTrace.span('events.refresh', () async {}),
        );
      });
      later.complete();
      await background;
      expect(_only('events.refresh').parent, isNull);
    });

    test('an error passes through untouched and marks the span', () async {
      final error = StateError('boom');
      await expectLater(
        PerfTrace.span('fails', () async => throw error),
        throwsA(same(error)),
      );
      expect(
        () => PerfTrace.spanSync('fails.sync', () => throw error),
        throwsA(same(error)),
      );
      expect(_only('fails').outcome, PerfOutcome.error);
      expect(_only('fails.sync').failed, isTrue);
      expect(logged.where((l) => l.contains('outcome=error')), hasLength(2));
    });

    test('the ring buffer keeps only the newest spans', () {
      PerfTrace.capacity = 3;
      for (var i = 0; i < 5; i++) {
        PerfTrace.spanSync('step$i', () {});
      }
      expect(PerfTrace.spans.map((s) => s.name), ['step2', 'step3', 'step4']);
      expect(PerfTrace.recent(2).map((s) => s.name), ['step4', 'step3']);
    });

    test('markOnce fires once per process', () {
      PerfTrace.markOnce('app.first_frame');
      PerfTrace.markOnce('app.first_frame');
      expect(
        PerfTrace.spans.where((s) => s.name == 'app.first_frame'),
        hasLength(1),
      );
    });

    test('log lines have the fixed tag and shape; fast spans under their '
        'threshold stay out of the log but in the buffer', () async {
      await PerfTrace.span('connect.health', () async {}, attrs: {'n': 2});
      PerfTrace.begin('http oc1 GET /path', logMinMs: 50).finish();
      expect(PerfTrace.spans, hasLength(2));
      expect(logged, hasLength(1));
      expect(
        logged.single,
        matches(RegExp(r'^OCTRACE \d+(\.\d)?ms connect\.health n=2$')),
      );
    });

    test('recordSince and recordDuration time work across callbacks', () {
      final start = PerfTrace.nowMicros - 2500;
      PerfTrace.recordSince('events.first', start, attrs: {'api': 'oc1'});
      PerfTrace.recordDuration(
        'setup.component',
        const Duration(seconds: 8),
        attrs: {'id': 'linux'},
      );
      expect(_only('events.first').durationMicros, greaterThanOrEqualTo(2500));
      expect(_only('setup.component').durationMs, 8000);
      expect(
        logged.last,
        startsWith('OCTRACE 8000ms setup.component id=linux'),
      );
    });

    test('stats group by name with nearest-rank percentiles', () {
      for (final ms in [10, 20, 30, 40, 1000]) {
        PerfTrace.recordDuration('catalog.load', Duration(milliseconds: ms));
      }
      PerfTrace.recordDuration(
        'sessions.refresh',
        const Duration(milliseconds: 5),
        error: 'x',
      );
      PerfTrace.mark('not.a.stat');
      final stats = PerfTrace.stats();
      expect(stats.map((s) => s.name), ['catalog.load', 'sessions.refresh']);
      final catalog = stats.first;
      expect(catalog.count, 5);
      expect(catalog.p50Ms, 30);
      expect(catalog.p95Ms, 1000);
      expect(catalog.maxMs, 1000);
      expect(stats.last.errors, 1);
    });
  });

  group('no secrets', () {
    test('secret-named attributes and token-like values are redacted', () {
      PerfTrace.spanSync(
        'login',
        () {},
        attrs: {
          'Authorization': 'Basic b3BlbmNvZGU6aHVudGVyMg==',
          'password': 'hunter2',
          'api_key': 'k',
          'note': 'value ${'a1B2' * 10} and http://user:pw@host/x',
        },
      );
      final span = _only('login');
      expect(span.attrs['Authorization'], '[redacted]');
      expect(span.attrs['password'], '[redacted]');
      expect(span.attrs['api_key'], '[redacted]');
      expect(span.attrs['note'], isNot(contains('a1B2a1B2')));
      expect(span.attrs['note'], isNot(contains('user:pw')));
      final report = PerfTrace.reportText();
      expect(report, isNot(contains('hunter2')));
      expect(report, isNot(contains('b3BlbmNvZGU6aHVudGVyMg')));
    });

    test('path templates drop ids and every query value', () {
      expect(
        PerfTrace.pathTemplate('/session/ses_4fA9kQ2zzZ/message?limit=100'),
        '/session/:id/message',
      );
      expect(
        PerfTrace.pathTemplate(
          'http://127.0.0.1:4096/api/session/0b7c6c8e-1f2a-4f7e-9c1d-3a2b1c0d9e8f/'
          'permission/per_abcdef123?auth_token=secret',
        ),
        '/api/session/:id/permission/:id',
      );
      expect(PerfTrace.pathTemplate('/pty/42/connect'), '/pty/:id/connect');
      expect(PerfTrace.pathTemplate('/file/%2Fhome%2Fme'), '/file/:id');
      expect(PerfTrace.pathTemplate('/provider'), '/provider');
    });

    test('the password script is only ever labelled generically', () {
      final script = BuiltinLinux.writePasswordScript;
      expect(BuiltinLinux.scriptLabel(script), 'write-password');
      expect(
        BuiltinLinux.scriptLabel(BuiltinLinux.folderExistsScript('/p/secret')),
        "test -d '…'",
      );
      expect(
        BuiltinLinux.scriptLabel('set -eu\n\n# c\nls -la /root\n'),
        'ls -la /root',
      );
    });
  });

  group('http', () {
    test(
      'every request is a span: method, path template, status, bytes',
      () async {
        final transport = Api2Transport(
          baseUrl: 'http://host:4097',
          password: 'secret',
          // This fixture represents a server whose HTTP warning was approved.
          cleartextConfirmedOrigin: 'http://host:4097',
        );
        addTearDown(transport.close);
        final adapter = _SizedAdapter(bytes: 4096);
        transport.dio.httpClientAdapter = adapter;

        await PerfTrace.span('location.select', () async {
          await transport.getJson(
            '/session/ses_abc123XYZ/message',
            query: {'directory': '/home/me/private-project'},
          );
        });

        final span = _only('http oc2 GET /session/:id/message');
        expect(span.attrs['status'], '200');
        expect(span.attrs['bytes'], '4096');
        expect(span.parent, 'location.select');
        expect(span.outcome, PerfOutcome.ok);
        final everything = PerfTrace.reportText();
        expect(everything, isNot(contains('private-project')));
        expect(everything, isNot(contains('secret')));
        // The request itself still carried its query and credentials.
        expect(adapter.requests.single.queryParameters['directory'], isNotNull);
      },
    );

    test('a failed request is recorded as an error with its status', () async {
      final dio = PerfTraceInterceptor.traced(Dio(), 'oc1')
        ..httpClientAdapter = _SizedAdapter(status: 500, bytes: 10);
      addTearDown(dio.close);
      await expectLater(
        dio.get<Object?>('/provider'),
        throwsA(isA<DioException>()),
      );
      final span = _only('http oc1 GET /provider');
      expect(span.failed, isTrue);
      expect(span.attrs['status'], '500');
      expect(logged.single, contains('outcome=error'));
    });

    test('attaching twice adds one interceptor', () {
      final dio = Dio();
      PerfTraceInterceptor.attach(dio, 'oc1');
      PerfTraceInterceptor.attach(dio, 'oc1');
      expect(dio.interceptors.whereType<PerfTraceInterceptor>(), hasLength(1));
    });
  });

  group('prompt timeline', () {
    test('sent → accepted → first token → turn done', () async {
      await PromptTrace.track('ses_1', () async {});
      // The user's own text part echoes back first and does not count.
      PromptTrace.observe('message.part.updated', {
        'part': {'sessionID': 'ses_1', 'messageID': 'msg_user', 'type': 'text'},
      });
      // A stale idle before the turn visibly starts does not end it.
      PromptTrace.observe('session.idle', {'sessionID': 'ses_1'});
      expect(PerfTrace.spans.where((s) => s.name == 'prompt.turn'), isEmpty);
      PromptTrace.observe('session.status', {
        'sessionID': 'ses_1',
        'status': {'type': 'busy'},
      });
      PromptTrace.observe('message.updated', {
        'info': {'id': 'msg_a', 'sessionID': 'ses_1', 'role': 'assistant'},
      });
      expect(
        PerfTrace.spans.where((s) => s.name == 'prompt.first_token'),
        isEmpty,
      );
      PromptTrace.observe('message.part.updated', {
        'part': {'sessionID': 'ses_1', 'messageID': 'msg_a', 'type': 'text'},
      });
      PromptTrace.observe('message.part.delta', {'sessionID': 'ses_1'});
      PromptTrace.observe('session.status', {
        'sessionID': 'ses_1',
        'status': {'type': 'idle'},
      });
      PromptTrace.observe('session.idle', {'sessionID': 'ses_1'});

      expect(PerfTrace.spans.map((s) => s.name), [
        'prompt.sent',
        'prompt.accepted',
        'prompt.first_token',
        'prompt.turn',
      ]);
    });

    test('a refused prompt is an error and ends the tracking', () async {
      await expectLater(
        PromptTrace.track('ses_2', () async => throw StateError('no')),
        throwsStateError,
      );
      expect(_only('prompt.accepted').failed, isTrue);
      PromptTrace.observe('message.part.delta', {'sessionID': 'ses_2'});
      expect(
        PerfTrace.spans.where((s) => s.name == 'prompt.first_token'),
        isEmpty,
      );
    });
  });

  group('diagnostics section', () {
    testWidgets('shows grouped stats and recent spans, copies a safe report, '
        'and clears', (tester) async {
      String? clipboard;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            clipboard = (call.arguments as Map)['text'] as String?;
          }
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      for (final ms in [120, 7700]) {
        PerfTrace.recordDuration(
          'http oc1 GET /provider',
          Duration(milliseconds: ms),
          attrs: {'status': 200, 'token': 'abc'},
        );
      }
      PerfTrace.recordDuration(
        'sessions.refresh',
        const Duration(milliseconds: 40),
      );

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(child: PerfTraceSection()),
          ),
        ),
      );

      expect(find.text('Performance'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('perf-trace-stat-http oc1 GET /provider')),
        findsOneWidget,
      );
      expect(find.textContaining('2× · typical 120ms'), findsOneWidget);
      expect(find.textContaining('longest 7700ms'), findsOneWidget);
      expect(find.text('sessions.refresh'), findsNWidgets(2));

      await tester.tap(find.byKey(const ValueKey('perf-trace-actions')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('perf-trace-copy')));
      await tester.pump();
      expect(clipboard, contains('http oc1 GET /provider'));
      expect(clipboard, contains('7700ms'));
      expect(clipboard, isNot(contains('abc')));
      // The copy is announced to assistive tech (KitCopy), not drawn.

      // A span finishing while the screen is open shows up.
      PerfTrace.recordDuration('catalog.load', const Duration(seconds: 3));
      await tester.pump();
      expect(find.text('catalog.load'), findsNWidgets(2));

      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('perf-trace-actions')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('perf-trace-clear')));
      await tester.pumpAndSettle();
      expect(PerfTrace.spans, isEmpty);
      expect(find.text('Nothing measured yet.'), findsOneWidget);
    });
  });
}
