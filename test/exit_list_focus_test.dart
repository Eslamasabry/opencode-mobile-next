// Recent app exits put problems first (device check on APK 2196: 7 of 10
// rows were "App updated"). Problem exits are the only rows by default,
// at most five until "Show all N"; routine exits fold under one quiet row;
// no problems is one line with no empty card; times read "Today 08:37",
// "Yesterday 21:42", else the date.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/domain/app_diagnostics_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:opencode_mobile/ui/screens/exit_history_section.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Gateway extends ChangeNotifier implements AppDiagnosticsGateway {
  _Gateway(this.history);

  final AppExitHistory history;
  int? askedLimit;

  @override
  Future<AppExitHistory> exitHistory({int limit = 10}) async {
    askedLimit = limit;
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
  bool get shareSupported => false;

  @override
  Future<CrashReportResult> previewCrashReport() async =>
      const CrashReportResult(error: DiagnosticsError.unavailable);

  @override
  Future<CrashShareResult> shareCrashReport(CrashReportPreview preview) async =>
      const CrashShareResult(opened: false);
}

final _now = DateTime(2026, 10, 8, 9);

AppExitEntry _exit(AppExitCategory category, int minutesAgo, {int? reason}) =>
    AppExitEntry(
      reason:
          reason ??
          switch (category) {
            AppExitCategory.normal => 1,
            AppExitCategory.update => 10,
            AppExitCategory.forceStop => 11,
            AppExitCategory.lowMemory => 3,
            AppExitCategory.crash => 4,
            AppExitCategory.killed => 9,
          },
      importance: 100,
      at: _now.subtract(Duration(minutes: minutesAgo)),
      category: category,
    );

Finder _key(String key) => find.byKey(ValueKey(key));

void main() {
  late AppDiagnosticsController diagnostics;

  setUp(() {
    KitRedact.clearKnownSecrets();
    SharedPreferences.setMockInitialValues({});
    diagnostics = AppDiagnosticsController();
  });

  tearDown(() => diagnostics.dispose());

  Future<_Gateway> pump(
    WidgetTester tester,
    List<AppExitEntry> entries, {
    Locale? locale,
  }) async {
    final gateway = _Gateway(AppExitHistory(supported: true, entries: entries));
    addTearDown(gateway.dispose);
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
          diagnosticsGateway: gateway,
          clock: () => _now,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(_key('exit-history'));
    await tester.pumpAndSettle();
    return gateway;
  }

  testWidgets('the device-check case: one crash among seven updates and '
      'two closes shows the crash, the rest folded', (tester) async {
    final gateway = await pump(tester, [
      _exit(AppExitCategory.update, 23),
      _exit(AppExitCategory.forceStop, 32),
      _exit(AppExitCategory.update, 40),
      _exit(AppExitCategory.update, 42),
      _exit(AppExitCategory.crash, 47),
      _exit(AppExitCategory.update, 49),
      _exit(AppExitCategory.forceStop, 51),
      _exit(AppExitCategory.update, 54),
      _exit(AppExitCategory.update, 55),
      _exit(AppExitCategory.update, 56),
    ]);

    // The most Android gives, so problems are found among the updates.
    expect(gateway.askedLimit, 50);
    expect(find.text('App stopped unexpectedly'), findsOneWidget);
    expect(find.textContaining('Today 08:13'), findsOneWidget);
    expect(
      find.text('9 routine closes (updates, you closed it)'),
      findsOneWidget,
    );
    expect(find.text('App updated'), findsNothing);
    expect(find.text('App stopped'), findsNothing);

    await tester.tap(find.text('9 routine closes (updates, you closed it)'));
    await tester.pumpAndSettle();
    expect(find.text('App updated'), findsNWidgets(7));
    expect(find.text('App stopped'), findsNWidgets(2));
  });

  testWidgets('at most five problems until Show all', (tester) async {
    await pump(tester, [
      for (var i = 0; i < 8; i++)
        _exit(
          i.isEven ? AppExitCategory.crash : AppExitCategory.lowMemory,
          i * 10,
        ),
    ]);
    expect(_key('exit-history-4'), findsOneWidget);
    expect(_key('exit-history-5'), findsNothing);
    expect(find.text('Show all 8'), findsOneWidget);
    expect(_key('exit-history-routine'), findsNothing);

    await tester.ensureVisible(_key('exit-history-show-all'));
    await tester.pumpAndSettle();
    await tester.tap(_key('exit-history-show-all'));
    await tester.pumpAndSettle();
    expect(_key('exit-history-7'), findsOneWidget);
    expect(find.text('Show all 8'), findsNothing);
  });

  testWidgets('no problem exits: one line, no empty card, routine still '
      'folded', (tester) async {
    await pump(tester, [
      _exit(AppExitCategory.update, 5),
      _exit(AppExitCategory.normal, 60),
    ]);
    expect(find.text('No unexpected closes recently.'), findsOneWidget);
    expect(_key('exit-history-0'), findsNothing);
    expect(
      find.text('2 routine closes (updates, you closed it)'),
      findsOneWidget,
    );
    await tester.pumpWidget(const SizedBox.shrink());

    await pump(tester, const []);
    expect(find.text('No unexpected closes recently.'), findsOneWidget);
    expect(
      find.descendant(
        of: _key('exit-history'),
        matching: find.byType(KitRowGroup),
      ),
      findsNothing,
    );
  });

  testWidgets('an exit Android cannot explain is a problem; exiting on '
      'its own is routine', (tester) async {
    await pump(tester, [
      _exit(AppExitCategory.normal, 5, reason: 0),
      _exit(AppExitCategory.normal, 6, reason: 1),
      _exit(AppExitCategory.killed, 7),
    ]);
    expect(find.text('Closed for an unknown reason'), findsOneWidget);
    expect(find.text('Android ended the app'), findsOneWidget);
    expect(find.text('App closed'), findsNothing);
    expect(
      find.text('1 routine close (updates, you closed it)'),
      findsOneWidget,
    );
  });

  testWidgets('Arabic', (tester) async {
    await pump(tester, [
      _exit(AppExitCategory.crash, 5),
      _exit(AppExitCategory.update, 6),
      _exit(AppExitCategory.update, 7),
    ], locale: const Locale('ar'));
    expect(find.textContaining('إغلاقان اعتياديان'), findsOneWidget);
    expect(find.textContaining('اليوم'), findsOneWidget);
  });

  test('time labels: today, yesterday, then the date', () {
    final copy = lookupAppLocalizations(const Locale('en'));
    String label(DateTime at) => exitTimeLabel(at, copy: copy, now: _now);
    expect(label(DateTime(2026, 10, 8, 8, 37)), 'Today 08:37');
    expect(label(DateTime(2026, 10, 8, 0, 1)), 'Today 00:01');
    expect(label(DateTime(2026, 10, 7, 21, 42)), 'Yesterday 21:42');
    expect(label(DateTime(2026, 10, 7, 0, 0)), 'Yesterday 00:00');
    expect(label(DateTime(2026, 10, 6, 8, 5)), 'Oct 6 08:05');
    expect(label(DateTime(2025, 12, 31, 23, 59)), 'Dec 31, 2025 23:59');
  });

  test('which exits are problems', () {
    expect(isProblemExit(_exit(AppExitCategory.crash, 0)), isTrue);
    expect(isProblemExit(_exit(AppExitCategory.lowMemory, 0)), isTrue);
    expect(isProblemExit(_exit(AppExitCategory.killed, 0)), isTrue);
    expect(isProblemExit(_exit(AppExitCategory.normal, 0, reason: 0)), isTrue);
    expect(isProblemExit(_exit(AppExitCategory.normal, 0)), isFalse);
    expect(isProblemExit(_exit(AppExitCategory.update, 0)), isFalse);
    expect(isProblemExit(_exit(AppExitCategory.forceStop, 0)), isFalse);
  });
}
