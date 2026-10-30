// Golden renders of screen-system-1's pages (wave 2b), rebuilt from kit
// parts: About (Privacy and Open source), App diagnostics with captured
// errors and timings, its clear question, Keep running (steps left, and
// all set), and Available on this server for a server that lacks things.
// Phone 412x915 and one wide window (1280x800), dark and light (owner
// decision 2026-09-27: no Arabic), with the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_system_1_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/codex/gateway.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart' show KitReport;
import 'package:opencode_mobile/ui/screens/about_screen.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:opencode_mobile/ui/screens/keep_running_screen.dart';
import 'package:opencode_mobile/ui/screens/server_capabilities_screen.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import 'screen_system_1_fixtures.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  required Widget home,
  Size size = _phone,
  Future<void> Function()? then,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: ProviderScope(
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: light),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: child!,
            ),
            home: home,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    // Package info and the documents resolve on real futures.
    for (var i = 0; i < 5; i++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 60)),
      );
      await tester.pumpAndSettle();
    }
    if (then != null) await then();
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

/// Types [description] and opens Details, which holds exactly what is sent.
Future<void> _review(WidgetTester tester, String description) async {
  await tester.enterText(
    find.descendant(
      of: find.byKey(const ValueKey('report-problem-description')),
      matching: find.byType(EditableText),
    ),
    description,
  );
  await tester.pumpAndSettle();
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  await tester.ensureVisible(
    find.byKey(const ValueKey('report-problem-details')),
  );
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const ValueKey('report-problem-details')));
  // The version resolves on a real future.
  for (var i = 0; i < 3; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 60)),
    );
    await tester.pumpAndSettle();
  }
}

/// A failed team run as Report this failure attaches it (P8.4).
const _failedJob = KitReport(
  title: 'Sync engine stopped',
  details: 'tests failed',
  source: 'failed job · teamRun · oc-loy',
  log:
      '\$ pytest -q\n'
      '..F.\n'
      'FAILED tests/test_calc.py::test_subtract\n'
      '    assert subtract(1, 2) == -1\n'
      'E   assert 3 == -1\n'
      '1 failed, 3 passed in 0.42s',
);

void main() {
  setUpAll(loadCaptureFonts);
  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    rootBundle
      ..evict('PRIVACY.md')
      ..evict('THIRD_PARTY_NOTICES.md');
    PackageInfo.setMockInitialValues(
      appName: 'OpenCode Mobile',
      packageName: 'com.opencode.mobile',
      version: '1.0.44',
      buildNumber: '52',
      buildSignature: '',
    );
  });
  tearDown(() {
    debugPlatformCapabilities = null;
    clearKeepAliveMock();
    PerfTrace.clear();
  });

  for (final light in [false, true]) {
    final theme = light ? 'light' : 'dark';

    testWidgets('about ($theme)', (tester) async {
      await _shot(
        tester,
        'system_about_loaded',
        light: light,
        home: const AboutScreen(),
      );
    });

    testWidgets('about open source ($theme)', (tester) async {
      await _shot(
        tester,
        'system_about_open_source',
        light: light,
        home: const AboutScreen(),
        then: () async {
          await tester.scrollUntilVisible(
            find.byKey(const ValueKey('about-all-licences')),
            300,
            scrollable: find
                .descendant(
                  of: find.byKey(const ValueKey('about-page')),
                  matching: find.byType(Scrollable),
                )
                .first,
          );
        },
      );
    });

    testWidgets('app diagnostics ($theme)', (tester) async {
      final controller = await systemController(codexServerCapabilities);
      addTearDown(controller.dispose);
      recordSampleErrors(controller);
      recordSampleTimings();
      await _shot(
        tester,
        'system_app_diagnostics',
        light: light,
        home: AppDiagnosticsKeptScreen(controller: controller),
      );
    });

    testWidgets('app diagnostics clear sheet ($theme)', (tester) async {
      final controller = await systemController(codexServerCapabilities);
      addTearDown(controller.dispose);
      recordSampleErrors(controller);
      await _shot(
        tester,
        'system_app_diagnostics_clear_sheet',
        light: light,
        home: AppDiagnosticsKeptScreen(controller: controller),
        then: () async {
          await tester.ensureVisible(
            find.byKey(const ValueKey('clear-app-diagnostics')),
          );
          await tester.pumpAndSettle();
          await tester.tap(find.byKey(const ValueKey('clear-app-diagnostics')));
          await tester.pumpAndSettle();
        },
      );
    });

    testWidgets('report a problem preview sheet ($theme)', (tester) async {
      final controller = await systemController(codexServerCapabilities);
      addTearDown(controller.dispose);
      recordSampleErrors(controller);
      await _shot(
        tester,
        'system_report_problem_preview',
        light: light,
        home: AppDiagnosticsScreen(
          controller: controller,
          error: const KitReport(
            title: "Couldn't load files",
            errorType: 'FormatException',
          ),
        ),
        then: () => _review(tester, 'The file list stays empty after Refresh'),
      );
    });

    testWidgets('report a problem with a failed job log ($theme)', (
      tester,
    ) async {
      SharedPreferences.setMockInitialValues({});
      await _shot(
        tester,
        'system_report_problem_job_log',
        light: light,
        home: const AppDiagnosticsScreen(error: _failedJob),
      );
    });

    testWidgets('keep running ($theme)', (tester) async {
      mockKeepAlive(maker: 'Xiaomi', battery: true);
      await _shot(
        tester,
        'system_keep_running',
        light: light,
        home: const Scaffold(
          body: SingleChildScrollView(child: KeepRunningSection()),
        ),
      );
    });

    testWidgets('keep running all set ($theme)', (tester) async {
      mockKeepAlive(maker: 'Google', battery: true);
      await _shot(
        tester,
        'system_keep_running_set',
        light: light,
        home: const Scaffold(
          body: SingleChildScrollView(child: KeepRunningSection()),
        ),
      );
    });

    testWidgets('server capabilities ($theme)', (tester) async {
      final controller = await systemController(
        codexServerCapabilities,
        usageStatistics: false,
      );
      addTearDown(controller.dispose);
      await _shot(
        tester,
        'system_server_capabilities',
        light: light,
        home: ServerCapabilitiesScreen(controller: controller),
      );
    });
  }

  testWidgets('about wide (dark)', (tester) async {
    await _shot(
      tester,
      'system_about_loaded',
      light: false,
      size: _wide,
      home: const AboutScreen(),
    );
  });

  testWidgets('app diagnostics wide (dark)', (tester) async {
    final controller = await systemController(codexServerCapabilities);
    addTearDown(controller.dispose);
    recordSampleErrors(controller);
    recordSampleTimings();
    await _shot(
      tester,
      'system_app_diagnostics',
      light: false,
      size: _wide,
      home: AppDiagnosticsKeptScreen(controller: controller),
    );
  });

  testWidgets('report a problem preview sheet wide (dark)', (tester) async {
    final controller = await systemController(codexServerCapabilities);
    addTearDown(controller.dispose);
    recordSampleErrors(controller);
    await _shot(
      tester,
      'system_report_problem_preview',
      light: false,
      size: _wide,
      home: AppDiagnosticsScreen(controller: controller),
      then: () => _review(tester, 'The file list stays empty after Refresh'),
    );
  });

  testWidgets('report a problem with a failed job log wide (dark)', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    await _shot(
      tester,
      'system_report_problem_job_log',
      light: false,
      size: _wide,
      home: const AppDiagnosticsScreen(error: _failedJob),
    );
  });

  testWidgets('server capabilities wide (dark)', (tester) async {
    final controller = await systemController(
      codexServerCapabilities,
      usageStatistics: false,
    );
    addTearDown(controller.dispose);
    await _shot(
      tester,
      'system_server_capabilities',
      light: false,
      size: _wide,
      home: ServerCapabilitiesScreen(controller: controller),
    );
  });

  testWidgets('keep running wide (light)', (tester) async {
    mockKeepAlive(maker: 'Xiaomi', battery: true);
    await _shot(
      tester,
      'system_keep_running',
      light: true,
      size: _wide,
      home: const Scaffold(
        body: SingleChildScrollView(child: KeepRunningSection()),
      ),
    );
  });
}
