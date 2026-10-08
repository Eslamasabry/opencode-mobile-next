// FD1 Recent app exits and FD2 share saved crash reports on Report a problem
// (BC contract, docs/design/BC-diagnostics-contract.md). A fake gateway
// supplies exit history; crash report preview/share run through the real
// CrashReportBuilder over a real CrashDiagnosticsController on a temporary
// directory, with the share sheet faked. Nothing is uploaded.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_report.dart';
import 'package:opencode_mobile/domain/app_diagnostics_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Gateway extends ChangeNotifier implements AppDiagnosticsGateway {
  _Gateway(this.reports, {this.share = true});

  final CrashReportBuilder reports;
  final bool share;
  AppExitHistory history = AppExitHistory(supported: true);
  int exitReads = 0;
  CrashReportResult? previewOverride;

  @override
  Future<AppExitHistory> exitHistory({int limit = 10}) async {
    exitReads++;
    return history;
  }

  @override
  BackgroundPauseState get backgroundPause => BackgroundPauseState.unsupported;

  @override
  Future<BackgroundPauseState> refreshBackgroundPause() async =>
      BackgroundPauseState.unsupported;

  @override
  Future<BackgroundResumeResult> resumeBackground() async =>
      const BackgroundResumeResult(BackgroundPauseState.unsupported);

  @override
  bool get shareSupported => share;

  @override
  Future<CrashReportResult> previewCrashReport() async =>
      previewOverride ?? await reports.preview();

  @override
  Future<CrashShareResult> shareCrashReport(CrashReportPreview preview) =>
      reports.share(preview);
}

Finder _key(String key) => find.byKey(ValueKey(key));

Future<void> _tap(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

final _crashExit = AppExitEntry(
  reason: 4,
  importance: 100,
  at: DateTime(2026, 10, 7, 21, 42),
  category: AppExitCategory.crash,
);
final _memoryExit = AppExitEntry(
  reason: 3,
  importance: 400,
  at: DateTime(2026, 10, 6, 8, 5),
  category: AppExitCategory.lowMemory,
);

void main() {
  late Directory directory;
  late AppDiagnosticsController diagnostics;
  late CrashDiagnosticsController crash;
  late List<String> shared;
  late bool shareOpens;
  late _Gateway gateway;

  setUp(() {
    KitRedact.clearKnownSecrets();
    SharedPreferences.setMockInitialValues({});
    directory = Directory.systemTemp.createTempSync('fd-diagnostics-');
    diagnostics = AppDiagnosticsController();
    crash = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
    shared = [];
    shareOpens = true;
    gateway = _Gateway(
      CrashReportBuilder(
        loadController: () async => crash,
        shareText: (text) async {
          shared.add(text);
          return shareOpens;
        },
      ),
    );
  });

  tearDown(() {
    gateway.dispose();
    crash.dispose();
    diagnostics.dispose();
    if (directory.existsSync()) directory.deleteSync(recursive: true);
  });

  Future<void> pump(
    WidgetTester tester, {
    AppDiagnosticsGateway? withGateway,
    Locale? locale,
  }) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        locale: locale,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: AppDiagnosticsScreen(
          diagnostics: diagnostics,
          version: () async => '9.9.9+1',
          crash: crash,
          diagnosticsGateway: withGateway ?? gateway,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  void seedReports() {
    expect(crash.setEnabled(true), isTrue);
    crash.capture(StateError('private-value'), null, 'flutter');
    crash.importAndroidAnr(DateTime.now().millisecondsSinceEpoch);
  }

  group('FD1 Recent app exits', () {
    testWidgets('rows are a plain category and time; Android numbers sit '
        'under Details', (tester) async {
      gateway.history = AppExitHistory(
        supported: true,
        entries: [_crashExit, _memoryExit],
      );
      await pump(tester);
      await tester.ensureVisible(_key('exit-history-1'));
      await tester.pumpAndSettle();

      expect(find.text('Recent app exits'), findsOneWidget);
      expect(find.text('App stopped unexpectedly'), findsOneWidget);
      expect(find.text('Phone needed memory'), findsOneWidget);
      expect(find.textContaining('2026-10-07 21:42'), findsOneWidget);
      expect(find.text('Reason code'), findsNothing);
      expect(find.textContaining('Nothing here is sent'), findsOneWidget);

      await _tap(tester, find.text('App stopped unexpectedly'));
      final row = _key('exit-history-0');
      expect(
        find.descendant(of: row, matching: find.text('Reason code')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: row, matching: find.text('4')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: row, matching: find.text('100')),
        findsOneWidget,
      );
    });

    testWidgets('empty and unsupported are different states', (tester) async {
      await pump(tester);
      await tester.ensureVisible(_key('exit-history'));
      await tester.pumpAndSettle();
      expect(find.text('No app exits recorded yet'), findsOneWidget);
      expect(find.text('Not available on this phone'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());

      gateway.history = const AppExitHistory.unsupported();
      await pump(tester);
      await tester.ensureVisible(_key('exit-history'));
      await tester.pumpAndSettle();
      expect(find.text('Not available on this phone'), findsOneWidget);
      expect(
        find.text('Android 11 and later keep this record.'),
        findsOneWidget,
      );
      expect(find.text('No app exits recorded yet'), findsNothing);
    });

    testWidgets('a failed read says so and Try again reads again', (
      tester,
    ) async {
      gateway.history = AppExitHistory(
        supported: true,
        error: DiagnosticsError.unavailable,
      );
      await pump(tester);
      expect(gateway.exitReads, 1);
      expect(
        find.text(
          "Couldn't read recent app exits. Try again, or reopen the app.",
        ),
        findsOneWidget,
      );

      gateway.history = AppExitHistory(supported: true, entries: [_crashExit]);
      await _tap(tester, _key('exit-history-retry'));
      expect(gateway.exitReads, 2);
      expect(_key('exit-history-failed'), findsNothing);
      expect(find.text('App stopped unexpectedly'), findsOneWidget);
    });

    testWidgets('coming back to the app reads the history again', (
      tester,
    ) async {
      await pump(tester);
      expect(gateway.exitReads, 1);
      for (final state in const [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      await tester.pumpAndSettle();
      expect(gateway.exitReads, 1);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(gateway.exitReads, 2);
    });

    testWidgets('an Android exit shows once: in Recent app exits when it '
        'could be read, else in the recent errors', (tester) async {
      diagnostics.record(
        'Android ended the app (crash): reason 4',
        null,
        source: 'android.exit',
      );
      gateway.history = AppExitHistory(supported: true, entries: [_crashExit]);
      await pump(tester);
      expect(find.text('Android closed the app'), findsNothing);
      expect(find.textContaining('recent error'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());

      gateway.history = const AppExitHistory.unsupported();
      await pump(tester);
      expect(find.text('1 recent error'), findsOneWidget);
      expect(find.text('Android closed the app'), findsOneWidget);
    });

    testWidgets('Arabic', (tester) async {
      gateway.history = AppExitHistory(supported: true, entries: [_crashExit]);
      await pump(tester, locale: const Locale('ar'));
      await tester.ensureVisible(_key('exit-history-0'));
      await tester.pumpAndSettle();
      expect(find.text('آخر مرات إغلاق التطبيق'), findsOneWidget);
      expect(find.text('توقف التطبيق بشكل غير متوقع'), findsOneWidget);
    });
  });

  group('FD2 share saved crash reports', () {
    testWidgets('hidden without saved reports or a share sheet', (
      tester,
    ) async {
      await pump(tester);
      expect(_key('crash-reports-share'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());

      seedReports();
      final noShare = _Gateway(gateway.reports, share: false);
      addTearDown(noShare.dispose);
      await pump(tester, withGateway: noShare);
      await tester.ensureVisible(_key('crash-reports-delete'));
      await tester.pumpAndSettle();
      expect(_key('crash-reports-share'), findsNothing);
    });

    testWidgets('the preview shows exactly what is shared, and only Share '
        'report hands it over', (tester) async {
      seedReports();
      await pump(tester);

      await _tap(tester, _key('crash-reports-share'));
      expect(_key('crash-report-share-sheet'), findsOneWidget);
      expect(find.text('Preview crash report'), findsOneWidget);
      expect(find.text('Share saved crash reports'), findsOneWidget);
      expect(find.textContaining('2 reports · '), findsOneWidget);
      final shown = tester
          .widget<KitText>(_key('crash-report-share-text'))
          .text;
      expect(shown, startsWith('OpenCode Mobile crash report'));
      expect(shown, isNot(contains('private-value')));
      // Opening the preview shares nothing.
      expect(shared, isEmpty);

      await _tap(tester, _key('crash-report-share'));

      expect(shared, [shown]);
      expect(_key('crash-report-share-sheet'), findsNothing);
      // The chooser opening is not delivery: nothing claims it was sent.
      expect(
        find.textContaining(RegExp(r'\b[Ss]ent\b(?! automatically)')),
        findsNothing,
      );
    });

    testWidgets('new evidence makes the preview stale: rebuilt, said, and '
        'shared only on another tap', (tester) async {
      seedReports();
      await pump(tester);
      await _tap(tester, _key('crash-reports-share'));
      expect(find.textContaining('2 reports · '), findsOneWidget);

      crash.capture(ArgumentError('x'), null, 'widget');
      await _tap(tester, _key('crash-report-share'));

      expect(shared, isEmpty);
      expect(
        find.text(
          'Saved details changed. Check the new report before sharing.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('3 reports · '), findsOneWidget);

      await _tap(tester, _key('crash-report-share'));
      expect(shared, hasLength(1));
      expect(shared.single, contains('widget'));
    });

    testWidgets('a share sheet that does not open says so and keeps the '
        'preview', (tester) async {
      seedReports();
      shareOpens = false;
      await pump(tester);
      await _tap(tester, _key('crash-reports-share'));
      await _tap(tester, _key('crash-report-share'));

      expect(_key('crash-report-share-sheet'), findsOneWidget);
      expect(find.text("Couldn't open sharing. Try again."), findsOneWidget);
    });

    testWidgets('a report that cannot be built says why on the page', (
      tester,
    ) async {
      seedReports();
      gateway.previewOverride = const CrashReportResult(
        error: DiagnosticsError.captureDisabled,
      );
      await pump(tester);
      await _tap(tester, _key('crash-reports-share'));

      expect(_key('crash-report-share-sheet'), findsNothing);
      expect(
        find.text(
          'Saved crash reports are off. Turn them on to save future app '
          'errors.',
        ),
        findsOneWidget,
      );
    });
  });
}
