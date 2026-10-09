// Coverage ratchets for the app's own diagnostics (Report a problem): the
// exit history Android reports, the errors the app kept, the opt-in crash
// store and the performance timings. Each case goes through the app's real
// reader and the real widgets, then is checked against its ledger (see
// paseo_coverage_support.dart). Secret-looking markers in free text must
// never reach the screen; the privacy gates are in diag_privacy_test.dart.
//
// Times in a case are "milliseconds before now" (negative) so "Today" stays
// true whenever the test runs.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/platform/app_exit.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:opencode_mobile/ui/screens/exit_history_section.dart';
import 'package:opencode_mobile/ui/screens/perf_trace_section.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() {
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
    mockNoTermux();
  });

  final families = {
    for (final name in ['diag_exit', 'diag_error', 'diag_crash', 'diag_perf'])
      name: CoverageFamily(name, prefix: ''),
  };
  for (final family in families.values) {
    group('ledger · ${family.name}', () => registerLedgerTests(family));
  }

  Future<GlobalKey> pump(WidgetTester tester, Widget home) async {
    tester.view.physicalSize = const Size(412, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final boundary = GlobalKey();
    final (store, controller) = await serversState();
    controller.dispose();
    await tester.pumpWidget(
      captureApp(
        boundaryKey: boundary,
        controller: controller,
        store: store,
        home: home,
      ),
    );
    await frames(tester, 14);
    return boundary;
  }

  Future<void> finish(
    WidgetTester tester,
    GlobalKey boundary,
    CoverageFamily family,
    Map variant,
    String screen,
  ) async {
    await writeCasePng(tester, boundary, 'diag_${variant['id']}');
    // "{date}" stands for today's date as the app writes it.
    final today = DateTime.now();
    final date =
        '${today.year}-${today.month.toString().padLeft(2, '0')}-'
        '${today.day.toString().padLeft(2, '0')}';
    final filled = {
      ...variant,
      'fields': [
        for (final field in (variant['fields'] as List).cast<Map>())
          {
            ...field,
            'probes': [
              for (final probe in (field['probes'] as List).cast<String>())
                probe.replaceAll('{date}', date),
            ],
          },
      ],
    };
    final problems = checkCase(family, filled, screen, primaryText: screen);
    await tester.pumpWidget(const SizedBox.shrink());
    expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
  }

  /// Taps every row key that exists, then reads the screen.
  Future<String> openRows(WidgetTester tester, Iterable<String> keys) async {
    for (final key in keys) {
      final finder = find.byKey(ValueKey(key));
      if (finder.evaluate().isEmpty) continue;
      await tester.ensureVisible(finder.first);
      await tester.tap(finder.first, warnIfMissed: false);
      await frames(tester, 6);
    }
    return screenText(tester).join('\n');
  }

  // ----------------------------------------------------------------- exits
  final exitFamily = families['diag_exit']!;
  for (final variant in exitFamily.cases) {
    testWidgets('exit history · ${variant['id']}', (tester) async {
      final payload = variant['payload'] as Map;
      final wire = Map<String, dynamic>.from(payload['wire'] as Map);
      final now = DateTime.now().millisecondsSinceEpoch;
      final answer = <String, Object?>{
        'supported': wire['supported'],
        if (wire['error'] != null) 'error': wire['error'],
        'entries': [
          for (final e in (payload['entries'] as List).cast<Map>())
            {
              'reason': e['reason'],
              'subReason': e['subReason'],
              'importance': e['importance'],
              'timestamp': now + (e['timestamp'] as num).toInt(),
              'status': e['status'],
              'description': e['description'],
            },
        ],
      };
      const channel = MethodChannel(AppLifecycleBridge.channelName);
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async => answer);
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      final history = await tester.runAsync(
        () => AppLifecycleBridge().exitHistory(),
      );
      final boundary = await pump(
        tester,
        Material(
          child: ListView(
            children: [
              ExitHistorySection(
                history: history,
                onRetry: () {},
                clock: () => DateTime.fromMillisecondsSinceEpoch(now),
              ),
            ],
          ),
        ),
      );
      final screen = await openRows(tester, [
        'exit-history-routine',
        for (var i = 0; i < 6; i++) 'exit-history-$i',
      ]);
      await finish(tester, boundary, exitFamily, variant, screen);
    });
  }

  // ---------------------------------------------------------------- errors
  final errorFamily = families['diag_error']!;
  for (final variant in errorFamily.cases) {
    testWidgets('recent errors · ${variant['id']}', (tester) async {
      final e = Map<String, dynamic>.from(variant['payload'] as Map);
      final diagnostics = AppDiagnosticsController();
      addTearDown(diagnostics.dispose);
      final at = DateTime.now().add(
        Duration(milliseconds: e['timestamp'] as int),
      );
      for (var i = 0; i < ((e['occurrences'] as int?) ?? 1); i++) {
        diagnostics.record(
          e['message'] as String,
          e['stack'] == null
              ? null
              : StackTrace.fromString(e['stack'] as String),
          source: e['source'] as String,
          at: at,
        );
      }
      final boundary = await pump(
        tester,
        AppDiagnosticsScreen(
          diagnostics: diagnostics,
          version: () async => '9.9.9+1',
          share: (_, _) async => true,
        ),
      );
      final screen = await openRows(tester, ['diagnostic-entry-1']);
      await finish(tester, boundary, errorFamily, variant, screen);
    });
  }

  // ----------------------------------------------------------------- crash
  final crashFamily = families['diag_crash']!;
  for (final variant in crashFamily.cases) {
    testWidgets('crash reports · ${variant['id']}', (tester) async {
      final payload = variant['payload'] as Map;
      final input = Map<String, dynamic>.from(payload['input'] as Map);
      final directory = Directory.systemTemp.createTempSync('diag-crash-');
      final diagnostics = AppDiagnosticsController();
      final crash = CrashDiagnosticsController.open(
        directory: directory,
        diagnostics: diagnostics,
      );
      addTearDown(() {
        crash.dispose();
        diagnostics.dispose();
        if (directory.existsSync()) directory.deleteSync(recursive: true);
      });
      expect(crash.setEnabled(true), isTrue);
      final now = DateTime.now().millisecondsSinceEpoch;
      switch (payload['how']) {
        case 'flutter':
          crash.capture(
            StateError(input['errorMessage'] as String),
            StackTrace.fromString(input['errorStack'] as String),
            'flutter',
          );
        case 'anr':
          crash.importAndroidAnr(now);
        case 'native':
          crash.importNativeCrash({
            'timestamp': now,
            'message': input['nativeText'],
          });
      }
      final boundary = await pump(
        tester,
        AppDiagnosticsScreen(
          diagnostics: diagnostics,
          version: () async => '9.9.9+1',
          crash: crash,
        ),
      );
      var screen = await openRows(tester, ['crash-report-0']);
      final details = find.text('Details');
      if (details.evaluate().isNotEmpty) {
        await tester.tap(details.last, warnIfMissed: false);
        await frames(tester, 6);
        screen = screenText(tester).join('\n');
      }
      await finish(tester, boundary, crashFamily, variant, screen);
    });
  }

  // ------------------------------------------------------------------ perf
  final perfFamily = families['diag_perf']!;
  for (final variant in perfFamily.cases) {
    testWidgets('performance timings · ${variant['id']}', (tester) async {
      final span = Map<String, dynamic>.from(variant['payload'] as Map);
      PerfTrace.resetForTesting();
      addTearDown(PerfTrace.resetForTesting);
      PerfTrace.logSink = null;
      final attrs = Map<String, Object?>.from((span['attrs'] as Map?) ?? {});
      void record() {
        if (span['isMark'] == true) {
          PerfTrace.mark(span['name'] as String, attrs: attrs);
        } else {
          PerfTrace.recordDuration(
            span['name'] as String,
            Duration(milliseconds: (span['durationMs'] as num).toInt()),
            attrs: attrs,
            error: span['outcome'] == 'error' ? StateError('failed') : null,
          );
        }
      }

      if (span['parent'] != null) {
        PerfTrace.spanSync(span['parent'] as String, record);
      } else {
        record();
      }
      final boundary = await pump(
        tester,
        const Material(child: SingleChildScrollView(child: PerfTraceSection())),
      );
      await finish(
        tester,
        boundary,
        perfFamily,
        variant,
        screenText(tester).join('\n'),
      );
    });
  }
}
