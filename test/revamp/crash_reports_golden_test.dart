// Golden renders of the saved crash reports section on Report a problem
// (BD7 consent UI): off, on with two saved reports, and a report's preview
// with Details open. Phone 412x915, dark and light, the app's real fonts at
// DPR 1. Record times are local wall-clock values, so the images do not
// depend on the machine's time zone.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/crash_reports_golden_test.dart
// and look at every changed image before committing it.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_diagnostics.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

const _phone = Size(412, 915);

void main() {
  setUpAll(loadCaptureFonts);

  late Directory directory;
  late AppDiagnosticsController diagnostics;
  late CrashDiagnosticsController crash;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    directory = Directory.systemTemp.createTempSync('crash-consent-golden-');
    diagnostics = AppDiagnosticsController();
  });

  tearDown(() {
    crash.dispose();
    diagnostics.dispose();
    if (directory.existsSync()) directory.deleteSync(recursive: true);
  });

  /// Saved consent and two records, written as the store keeps them.
  void seed() {
    int at(int day, int hour, int minute) =>
        DateTime(2026, 10, day, hour, minute).millisecondsSinceEpoch;
    File(
      '${directory.path}/${CrashDiagnosticsController.consentFileName}',
    ).writeAsStringSync('${at(1, 8, 0)}');
    File('${directory.path}/crash-diagnostics.json').writeAsStringSync(
      jsonEncode([
        {
          'source': 'flutter',
          'category': 'Invalid state',
          'time': at(6, 21, 42),
        },
        {
          'source': 'anr',
          'category': 'Android reported that the app stopped responding',
          'time': at(7, 9, 14),
        },
      ]),
    );
  }

  Future<void> shot(
    WidgetTester tester,
    String name, {
    required bool light,
    Future<void> Function()? then,
  }) async {
    crash = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
    tester.view.physicalSize = _phone;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    final boundary = GlobalKey();
    try {
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: light),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: child!,
            ),
            home: AppDiagnosticsScreen(
              diagnostics: diagnostics,
              version: () async => '1.2.0+2171',
              crash: crash,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      // The section near the top of the window, as a person scrolls to it.
      await Scrollable.ensureVisible(
        tester.element(find.byKey(const ValueKey('crash-reports'))),
        alignment: 0.15,
      );
      await tester.pumpAndSettle();
      if (then != null) await then();
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(boundary),
        matchesGoldenFile('goldens/${name}_${light ? 'light' : 'dark'}.png'),
      );
    } finally {
      await tester.pumpWidget(const SizedBox.shrink());
      debugDefaultTargetPlatformOverride = null;
    }
  }

  for (final light in [false, true]) {
    final theme = light ? 'light' : 'dark';

    testWidgets('crash reports off ($theme)', (tester) async {
      await shot(tester, 'crash_reports_off', light: light);
    });

    testWidgets('crash reports off with saved errors above ($theme)', (
      tester,
    ) async {
      diagnostics
        ..record(
          StateError('Render failed while laying out the list'),
          null,
          source: 'flutter',
          at: DateTime(2026, 10, 7, 9, 41, 5),
        )
        ..record(
          StateError('Event stream closed before the reply'),
          null,
          source: 'sse',
          at: DateTime(2026, 10, 7, 9, 44, 9),
        );
      await shot(tester, 'crash_reports_off_with_errors', light: light);
    });

    testWidgets('recent error opened to its Details ($theme)', (tester) async {
      diagnostics
        ..record(
          StateError('Render failed while laying out the list'),
          StackTrace.fromString('#0 build (lib/ui/chat.dart:42:3)'),
          source: 'flutter',
          at: DateTime(2026, 10, 7, 9, 41, 5),
        )
        ..record(
          StateError('Event stream closed before the reply'),
          null,
          source: 'sse',
          at: DateTime(2026, 10, 7, 9, 44, 9),
        );
      await shot(
        tester,
        'recent_error_details',
        light: light,
        then: () async {
          final title = find.text('Lost the live connection to the server');
          await tester.ensureVisible(title);
          await tester.pumpAndSettle();
          await tester.tap(title);
          await tester.pumpAndSettle();
          await Scrollable.ensureVisible(tester.element(title), alignment: 0.2);
        },
      );
    });

    testWidgets('crash reports on with saved reports ($theme)', (tester) async {
      seed();
      await shot(tester, 'crash_reports_on', light: light);
    });

    testWidgets('crash report preview with Details ($theme)', (tester) async {
      seed();
      await shot(
        tester,
        'crash_report_preview',
        light: light,
        then: () async {
          await tester.tap(find.byKey(const ValueKey('crash-report-1')));
          await tester.pumpAndSettle();
          await tester.tap(find.byKey(const ValueKey('crash-report-details')));
          await tester.pumpAndSettle();
        },
      );
    });
  }
}
