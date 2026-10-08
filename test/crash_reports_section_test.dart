// Saved crash reports on Report a problem (BD7 consent UI,
// docs/design/bd7-contract.md): the switch is off until the person turns it
// on, its words say what is kept; saved reports show as plain rows whose
// technical source and category sit under Details; turning it off asks
// nothing; "Delete saved crash reports" is its own named, confirmed action.
// A real CrashDiagnosticsController on a temporary directory; no channel.
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_diagnostics.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

Finder _key(String key) => find.byKey(ValueKey(key));

Finder _inSection(Finder finder) =>
    find.descendant(of: _key('crash-reports'), matching: finder);

Future<void> _tapKey(WidgetTester tester, String key) async {
  await tester.ensureVisible(_key(key));
  await tester.pumpAndSettle();
  await tester.tap(_key(key));
  await tester.pumpAndSettle();
}

bool _switchValue(WidgetTester tester) => tester
    .widget<KitSwitchRow>(
      find.ancestor(
        of: _key('crash-reports-switch'),
        matching: find.byType(KitSwitchRow),
      ),
    )
    .value;

void main() {
  late Directory directory;
  late AppDiagnosticsController diagnostics;
  late CrashDiagnosticsController crash;

  setUp(() {
    KitRedact.clearKnownSecrets();
    SharedPreferences.setMockInitialValues({});
    directory = Directory.systemTemp.createTempSync('crash-consent-ui-');
    diagnostics = AppDiagnosticsController();
    crash = CrashDiagnosticsController.open(
      directory: directory,
      diagnostics: diagnostics,
    );
  });

  tearDown(() {
    crash.dispose();
    diagnostics.dispose();
    if (directory.existsSync()) directory.deleteSync(recursive: true);
  });

  Future<void> pump(
    WidgetTester tester, {
    CrashDiagnosticsController? controller,
    Future<CrashDiagnosticsController?>? ready,
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
          crash: controller,
          crashReady: ready,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  void seedReports() {
    expect(crash.setEnabled(true), isTrue);
    crash.capture(StateError('private-value'), StackTrace.current, 'flutter');
    crash.importAndroidAnr(DateTime.now().millisecondsSinceEpoch);
  }

  testWidgets('off by default: the switch says what is kept, and turning '
      'it on saves the choice', (tester) async {
    await pump(tester, controller: crash);

    await tester.ensureVisible(_key('crash-reports-switch'));
    await tester.pumpAndSettle();
    expect(find.text('Save crash reports on this phone'), findsOneWidget);
    expect(find.textContaining('Never sent automatically'), findsOneWidget);
    expect(find.textContaining('no error messages'), findsOneWidget);
    expect(find.textContaining('latest 20'), findsOneWidget);
    expect(_switchValue(tester), isFalse);
    expect(crash.enabled, isFalse);
    expect(_key('crash-reports-none'), findsNothing);
    expect(_key('crash-reports-delete'), findsNothing);

    await _tapKey(tester, 'crash-reports-switch');

    expect(crash.enabled, isTrue);
    expect(_switchValue(tester), isTrue);
    expect(find.text('No crash reports yet'), findsOneWidget);
    expect(_key('crash-reports-failed'), findsNothing);
  });

  testWidgets('saved reports show as plain rows; the preview keeps the '
      'technical category under Details', (tester) async {
    seedReports();
    await pump(tester, controller: crash);

    await tester.ensureVisible(_key('crash-reports-delete'));
    await tester.pumpAndSettle();
    // Newest first, in plain words; the fixed category is not row copy.
    expect(_inSection(find.text('The app stopped responding')), findsOneWidget);
    expect(
      _inSection(find.text('The app hit an unexpected error')),
      findsOneWidget,
    );
    expect(_inSection(find.textContaining('private-value')), findsNothing);
    expect(find.text('Delete 2 saved crash reports'), findsOneWidget);
    // Each crash shows once: not again in the recent errors, and no raw
    // category or source id is row text anywhere on the page.
    expect(_key('clear-app-diagnostics'), findsNothing);
    expect(find.textContaining('recent error'), findsNothing);
    expect(find.textContaining('Invalid state'), findsNothing);
    expect(find.textContaining('Android reported'), findsNothing);
    expect(find.textContaining('crash.'), findsNothing);
    // Only saved reports to lose: the switch says just that.
    expect(
      find.text('Turning this off deletes the saved crash reports.'),
      findsOneWidget,
    );

    await _tapKey(tester, 'crash-report-1');

    expect(_key('crash-report-preview-sheet'), findsOneWidget);
    final sheet = _key('crash-report-preview-sheet');
    expect(
      find.descendant(
        of: sheet,
        matching: find.text('The app hit an unexpected error'),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: sheet,
        matching: find.textContaining('Kept on this phone only'),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(of: sheet, matching: find.text('Invalid state')),
      findsNothing,
    );

    await _tapKey(tester, 'crash-report-details');

    expect(
      find.descendant(of: sheet, matching: find.text('Invalid state')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: sheet, matching: find.text('crash.flutter')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: sheet, matching: find.textContaining('private')),
      findsNothing,
    );
  });

  testWidgets('turning it off asks nothing and the saved reports go', (
    tester,
  ) async {
    seedReports();
    await pump(tester, controller: crash);

    await _tapKey(tester, 'crash-reports-switch');

    // No question: no confirm sheet, the choice is applied at once.
    expect(_key('crash-reports-delete-confirm'), findsNothing);
    expect(crash.enabled, isFalse);
    expect(crash.savedCount, 0);
    expect(_switchValue(tester), isFalse);
    expect(_key('crash-report-0'), findsNothing);
    expect(_key('crash-reports-delete'), findsNothing);
  });

  testWidgets('Delete saved crash reports confirms, deletes and keeps '
      'saving on', (tester) async {
    seedReports();
    await pump(tester, controller: crash);

    await _tapKey(tester, 'crash-reports-delete');
    expect(find.text('Delete saved crash reports?'), findsOneWidget);
    expect(
      find.textContaining('Saving crash reports stays on'),
      findsOneWidget,
    );
    expect(crash.savedCount, 2);

    await _tapKey(tester, 'crash-reports-delete-confirm');

    expect(crash.savedCount, 0);
    expect(crash.enabled, isTrue);
    expect(_switchValue(tester), isTrue);
    expect(find.text('No crash reports yet'), findsOneWidget);
    expect(_key('crash-reports-delete'), findsNothing);
  });

  testWidgets('a failed change says so instead of confirming it', (
    tester,
  ) async {
    seedReports();
    await pump(tester, controller: crash);
    directory.deleteSync(recursive: true);

    await _tapKey(tester, 'crash-reports-delete');
    await _tapKey(tester, 'crash-reports-delete-confirm');

    expect(crash.storageFailed, isTrue);
    expect(_key('crash-reports-failed'), findsOneWidget);
    expect(
      find.text(
        "Couldn't update crash reports. Restart the app and try again.",
      ),
      findsOneWidget,
    );
  });

  testWidgets('a store that could not open shows a disabled switch with '
      'its reason', (tester) async {
    await pump(tester, ready: Future.value());

    await tester.ensureVisible(_key('crash-reports-switch'));
    await tester.pumpAndSettle();
    expect(
      find.text(
        "Crash reports aren't available right now. Restart the app and try "
        'again.',
      ),
      findsOneWidget,
    );
    await tester.tap(_key('crash-reports-switch'), warnIfMissed: false);
    await tester.pumpAndSettle();
    expect(_switchValue(tester), isFalse);
  });

  testWidgets('nothing is drawn while start-up is still opening the store', (
    tester,
  ) async {
    final opening = Completer<CrashDiagnosticsController?>();
    await pump(tester, ready: opening.future);
    expect(_key('crash-reports-switch'), findsNothing);

    opening.complete(crash);
    await tester.pumpAndSettle();
    expect(_key('crash-reports-switch'), findsOneWidget);
  });

  testWidgets('the switch says what it clears only when something would '
      'go', (tester) async {
    await pump(tester, controller: crash);
    await tester.ensureVisible(_key('crash-reports-switch'));
    await tester.pumpAndSettle();
    // Nothing saved: no warning line, and the note does not mention it.
    expect(_key('crash-reports-effect'), findsNothing);
    expect(find.textContaining('clears'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());

    diagnostics.record(StateError('handled'), null, source: 'flutter');
    await pump(tester, controller: crash);
    await tester.ensureVisible(_key('crash-reports-switch'));
    await tester.pumpAndSettle();
    expect(find.text('1 recent error'), findsOneWidget);
    expect(
      find.text('Turning this on clears the saved error above.'),
      findsOneWidget,
    );
  });

  testWidgets('with other saved errors, turning off and Delete name them', (
    tester,
  ) async {
    seedReports();
    diagnostics
      ..record(StateError('one'), null, source: 'flutter')
      ..record(ArgumentError('two'), null, source: 'widget');
    await pump(tester, controller: crash);

    // The two handled errors are listed; the two crashes are not.
    expect(find.text('2 recent errors'), findsOneWidget);
    await tester.ensureVisible(_key('crash-reports-delete'));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'Turning this off deletes the saved crash reports and clears the 2 '
        'saved errors above.',
      ),
      findsOneWidget,
    );

    await _tapKey(tester, 'crash-reports-delete');
    expect(
      find.text(
        'Deletes the crash reports and clears the 2 saved errors above. '
        'Saving crash reports stays on.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('Clear errors says the saved crash reports go too', (
    tester,
  ) async {
    seedReports();
    diagnostics.record(StateError('one'), null, source: 'flutter');
    await pump(tester, controller: crash);

    await _tapKey(tester, 'clear-app-diagnostics');
    expect(
      find.text('The saved crash reports are deleted too.'),
      findsOneWidget,
    );
    await _tapKey(tester, 'clear-app-diagnostics-confirm');
    expect(crash.savedCount, 0);
  });

  testWidgets('the page Details fold sits on the screen gutter', (
    tester,
  ) async {
    await pump(tester, controller: crash);
    final toggle = find.byKey(const ValueKey('kit-details-toggle'));
    await tester.ensureVisible(toggle);
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(toggle).dx, 16);
    expect(tester.getTopRight(toggle).dx, 412 - 16);
  });

  testWidgets('Arabic copy', (tester) async {
    seedReports();
    await pump(tester, controller: crash, locale: const Locale('ar'));

    await tester.ensureVisible(_key('crash-reports-delete'));
    await tester.pumpAndSettle();
    expect(find.text('حفظ تقارير الأعطال على هذا الهاتف'), findsOneWidget);
    expect(_inSection(find.text('توقف التطبيق عن الاستجابة')), findsOneWidget);
    expect(find.text('حذف تقريرَي عطل محفوظَين'), findsOneWidget);
  });
}
