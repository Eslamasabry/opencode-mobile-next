// Golden renders of FD1 Recent app exits (rows with one opened to Details,
// unsupported, failed read) and the FD2 crash report share preview on
// Report a problem. Phone 412x915, dark and light, the app's real fonts at
// DPR 1. Exit times are local wall-clock values; the share preview is
// captured as the sheet alone and its records are written in UTC, so no
// image depends on the machine's time zone.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/diagnostics_fd_golden_test.dart
// and look at every changed image before committing it.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_report.dart';
import 'package:opencode_mobile/domain/app_diagnostics_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

const _phone = Size(412, 915);

class _Gateway extends ChangeNotifier implements AppDiagnosticsGateway {
  _Gateway(this.reports, this.history);

  final CrashReportBuilder reports;
  final AppExitHistory history;

  @override
  Future<AppExitHistory> exitHistory({int limit = 10}) async => history;

  @override
  BackgroundPauseState get backgroundPause => BackgroundPauseState.unsupported;

  @override
  Future<BackgroundPauseState> refreshBackgroundPause() async =>
      BackgroundPauseState.unsupported;

  @override
  Future<BackgroundResumeResult> resumeBackground() async =>
      const BackgroundResumeResult(BackgroundPauseState.unsupported);

  @override
  bool get shareSupported => true;

  @override
  Future<CrashReportResult> previewCrashReport() => reports.preview();

  @override
  Future<CrashShareResult> shareCrashReport(CrashReportPreview preview) =>
      reports.share(preview);
}

AppExitEntry _exit(AppExitCategory category, DateTime at, int reason) =>
    AppExitEntry(reason: reason, importance: 100, at: at, category: category);

/// The APK 2196 device check: problems among many updates and closes.
final _exits = AppExitHistory(
  supported: true,
  entries: [
    _exit(AppExitCategory.update, DateTime(2026, 10, 8, 8, 37), 10),
    _exit(AppExitCategory.forceStop, DateTime(2026, 10, 8, 8, 28), 11),
    _exit(AppExitCategory.update, DateTime(2026, 10, 8, 8, 20), 10),
    _exit(AppExitCategory.update, DateTime(2026, 10, 8, 8, 18), 10),
    _exit(AppExitCategory.crash, DateTime(2026, 10, 7, 21, 42), 4),
    _exit(AppExitCategory.update, DateTime(2026, 10, 7, 20, 11), 10),
    _exit(AppExitCategory.lowMemory, DateTime(2026, 10, 6, 8, 5), 3),
    _exit(AppExitCategory.update, DateTime(2026, 10, 5, 19, 30), 10),
  ],
);

/// Only routine exits: no problem to show.
final _routineOnly = AppExitHistory(
  supported: true,
  entries: [
    _exit(AppExitCategory.update, DateTime(2026, 10, 8, 8, 37), 10),
    _exit(AppExitCategory.forceStop, DateTime(2026, 10, 7, 22, 3), 11),
  ],
);

void main() {
  setUpAll(loadCaptureFonts);

  late Directory directory;
  late AppDiagnosticsController diagnostics;
  late CrashDiagnosticsController crash;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    directory = Directory.systemTemp.createTempSync('fd-golden-');
    diagnostics = AppDiagnosticsController();
  });

  tearDown(() {
    crash.dispose();
    diagnostics.dispose();
    if (directory.existsSync()) directory.deleteSync(recursive: true);
  });

  /// Saved consent and two records, written as the store keeps them.
  /// [utc]: times fixed in UTC (the share text prints UTC); otherwise
  /// local wall-clock times (the rows print local time).
  void seed({bool utc = false}) {
    int at(int day, int hour, int minute) =>
        (utc
                ? DateTime.utc(2026, 10, day, hour, minute)
                : DateTime(2026, 10, day, hour, minute))
            .millisecondsSinceEpoch;
    File(
      '${directory.path}/${CrashDiagnosticsController.consentFileName}',
    ).writeAsStringSync('${at(1, 8, 0)}');
    File('${directory.path}/crash-diagnostics.json').writeAsStringSync(
      jsonEncode([
        {
          'source': 'flutter',
          'category': 'Invalid state',
          'time': at(6, 17, 42),
        },
        {
          'source': 'anr',
          'category': 'Android reported that the app stopped responding',
          'time': at(7, 5, 14),
        },
      ]),
    );
  }

  Future<void> shot(
    WidgetTester tester,
    String name, {
    required bool light,
    required AppExitHistory history,
    String scrollTo = 'exit-history',
    Future<void> Function()? then,
    String? capture,
  }) async {
    crash = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
    final gateway = _Gateway(
      CrashReportBuilder(
        loadController: () async => crash,
        shareText: (_) async => true,
      ),
      history,
    );
    addTearDown(gateway.dispose);
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
              diagnosticsGateway: gateway,
              clock: () => DateTime(2026, 10, 8, 9),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await Scrollable.ensureVisible(
        tester.element(find.byKey(ValueKey(scrollTo))),
        alignment: 0.1,
      );
      await tester.pumpAndSettle();
      if (then != null) await then();
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await expectLater(
        capture == null ? find.byKey(boundary) : find.byKey(ValueKey(capture)),
        matchesGoldenFile('goldens/${name}_${light ? 'light' : 'dark'}.png'),
      );
    } finally {
      await tester.pumpWidget(const SizedBox.shrink());
      debugDefaultTargetPlatformOverride = null;
    }
  }

  for (final light in [false, true]) {
    final theme = light ? 'light' : 'dark';

    testWidgets('recent app exits, one opened ($theme)', (tester) async {
      await shot(
        tester,
        'exit_history_one_open',
        light: light,
        history: _exits,
        then: () async {
          await tester.tap(find.text('App stopped unexpectedly'));
          await tester.pumpAndSettle();
        },
      );
    });

    testWidgets('recent app exits, routine opened ($theme)', (tester) async {
      await shot(
        tester,
        'exit_history_routine_open',
        light: light,
        history: _exits,
        then: () async {
          final routine = find.byKey(const ValueKey('exit-history-routine'));
          await tester.ensureVisible(routine);
          await tester.pumpAndSettle();
          await tester.tap(routine);
          await tester.pumpAndSettle();
          await Scrollable.ensureVisible(
            tester.element(routine),
            alignment: 0.1,
          );
        },
      );
    });

    testWidgets('recent app exits, no problems ($theme)', (tester) async {
      await shot(
        tester,
        'exit_history_no_problems',
        light: light,
        history: _routineOnly,
      );
    });

    testWidgets('recent app exits unsupported ($theme)', (tester) async {
      await shot(
        tester,
        'exit_history_unsupported',
        light: light,
        history: const AppExitHistory.unsupported(),
      );
    });

    testWidgets('recent app exits read failed ($theme)', (tester) async {
      await shot(
        tester,
        'exit_history_failed',
        light: light,
        history: AppExitHistory(
          supported: true,
          error: DiagnosticsError.unavailable,
        ),
      );
    });

    testWidgets('crash reports with Share ($theme)', (tester) async {
      seed();
      await shot(
        tester,
        'crash_reports_share_row',
        light: light,
        history: AppExitHistory(supported: true),
        scrollTo: 'crash-reports',
        capture: 'crash-reports',
      );
    });

    testWidgets('crash report share preview ($theme)', (tester) async {
      seed(utc: true);
      await shot(
        tester,
        'crash_report_share_preview',
        light: light,
        history: AppExitHistory(supported: true),
        scrollTo: 'crash-reports',
        capture: 'crash-report-share-sheet',
        then: () async {
          final row = find.byKey(const ValueKey('crash-reports-share'));
          await tester.ensureVisible(row);
          await tester.pumpAndSettle();
          await tester.tap(row);
          await tester.pumpAndSettle();
        },
      );
    });
  }
}
