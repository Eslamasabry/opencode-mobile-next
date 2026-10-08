// Recent errors on Report a problem in plain words (no-raw-errors rule):
// each row's title says what the person noticed, chosen by where the error
// came from; the time is the subtitle; the redacted message and the source
// id appear only under the row's Details. The report payload is unchanged.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/report_problem.dart';
import 'package:opencode_mobile/feedback/problem_report.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:opencode_mobile/ui/screens/recent_error_words.dart';
import 'package:shared_preferences/shared_preferences.dart';

Finder _key(String key) => find.byKey(ValueKey(key));

Future<void> _tap(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

ProblemReportEvent _event(
  String source, {
  ProblemEventKind kind = ProblemEventKind.error,
}) => ProblemReportEvent(
  kind: kind,
  time: DateTime(2026, 10, 8, 9),
  source: source,
  message: 'Bad state: raw',
);

void main() {
  late AppDiagnosticsController diagnostics;

  setUp(() {
    KitRedact.clearKnownSecrets();
    SharedPreferences.setMockInitialValues({});
    final at = DateTime(2026, 10, 7, 9, 41, 5);
    diagnostics = AppDiagnosticsController()
      ..record(
        StateError('Render failed while laying out the transcript'),
        StackTrace.fromString('#0 build (lib/ui/chat.dart:42:3)'),
        source: 'flutter',
        at: at,
      )
      ..record(
        StateError('Event stream closed before the reply ended'),
        null,
        source: 'sse',
        at: at.add(const Duration(minutes: 3)),
      )
      ..record(
        StateError('Mystery failure'),
        null,
        source: 'gadget',
        at: at.add(const Duration(minutes: 4)),
      );
  });

  tearDown(() => diagnostics.dispose());

  Future<void> pump(WidgetTester tester, {Locale? locale}) async {
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
          share: (_, _) async => true,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('rows read in plain words with the time; no raw message or '
      'source is row text', (tester) async {
    await pump(tester);
    await tester.ensureVisible(_key('clear-app-diagnostics'));
    await tester.pumpAndSettle();

    expect(find.text('3 recent errors'), findsOneWidget);
    expect(find.text('A screen couldn\'t be drawn'), findsOneWidget);
    expect(find.text('Lost the live connection to the server'), findsOneWidget);
    expect(find.text('Something went wrong'), findsOneWidget);
    expect(find.textContaining('2026-10-07 09:44:05'), findsOneWidget);

    // No raw text anywhere on the page until a row is opened.
    for (final raw in [
      'Bad state',
      'Render failed',
      'Event stream closed',
      'Mystery failure',
    ]) {
      expect(find.textContaining(raw), findsNothing, reason: raw);
    }
    // Source ids, alone or leading a subtitle ("sse · 09:44").
    for (final id in ['sse', 'flutter', 'gadget']) {
      expect(find.text(id), findsNothing, reason: id);
      expect(find.textContaining('$id ·'), findsNothing, reason: id);
    }
  });

  testWidgets('opening a row shows the source and redacted message under '
      'Details', (tester) async {
    await pump(tester);
    final row = find.ancestor(
      of: find.text('Lost the live connection to the server'),
      matching: find.byType(KitExpandRow),
    );
    await _tap(tester, find.text('Lost the live connection to the server'));

    expect(
      find.descendant(of: row, matching: find.text('Details')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: row, matching: find.text('sse')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: row,
        matching: find.textContaining('Event stream closed before the reply'),
      ),
      findsOneWidget,
    );
  });

  testWidgets('the report still carries the raw message and source', (
    tester,
  ) async {
    await pump(tester);
    await tester.enterText(
      find.descendant(
        of: _key('report-problem-description'),
        matching: find.byType(EditableText),
      ),
      'Replies stop halfway',
    );
    await tester.pumpAndSettle();
    await _tap(tester, _key('report-problem-review'));
    for (var i = 0; i < 3; i++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 60)),
      );
      await tester.pumpAndSettle();
    }
    final preview = tester
        .widget<KitText>(_key('report-problem-preview-text'))
        .text;
    expect(preview, contains('Event stream closed before the reply ended'));
    expect(preview, contains('Render failed while laying out the transcript'));
    expect(preview, contains('sse'));
    expect(preview, contains('flutter'));
  });

  testWidgets('the native crash summary stays listed in plain words; crash '
      'store records do not', (tester) async {
    diagnostics
      ..record('Native crash', null, source: 'crash.last')
      ..record('Invalid state', null, source: 'crash.flutter');
    await pump(tester);
    await tester.ensureVisible(_key('clear-app-diagnostics'));
    await tester.pumpAndSettle();
    expect(find.text('4 recent errors'), findsOneWidget);
    expect(find.text('The app closed unexpectedly'), findsOneWidget);
    expect(find.textContaining('Invalid state'), findsNothing);
  });

  testWidgets('Arabic titles', (tester) async {
    await pump(tester, locale: const Locale('ar'));
    expect(find.text('انقطع الاتصال المباشر بالخادم'), findsOneWidget);
    expect(find.text('تعذّر رسم إحدى الشاشات'), findsOneWidget);
    expect(find.text('حدث خطأ ما'), findsOneWidget);
  });

  test('titles by source and kind', () {
    final copy = lookupAppLocalizations(const Locale('en'));
    String title(String source, [ProblemEventKind? kind]) => recentErrorTitle(
      copy,
      _event(source, kind: kind ?? ProblemEventKind.error),
    );
    expect(title('widget'), "A screen couldn't be drawn");
    expect(title('sse.reconnect'), 'Lost the live connection to the server');
    expect(title('crash.last'), 'The app closed unexpectedly');
    expect(title('android.exit'), 'Android closed the app');
    expect(
      title('anything', ProblemEventKind.androidExit),
      'Android closed the app',
    );
    expect(title('thermal.stream'), 'Phone temperature changed');
    expect(title('bootstrap-reset'), 'The app had trouble starting');
    expect(title('report-problem'), "Couldn't open saved problem reports");
    expect(title('app'), 'Something went wrong');
    expect(crashStoreSources, isNot(contains('crash.last')));
  });
}
