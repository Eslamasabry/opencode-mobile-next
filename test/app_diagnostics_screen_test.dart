// Report a problem (slice-P8.2, vision 2026-10-20 "B, trimmed"): the page
// arrives knowing what went wrong, Details shows exactly what is sent, and
// Send opens the prefilled GitHub form (through openExternalLink) or Copy
// copies it. Nothing here files an issue: the launcher is a fake. Fake keys
// only.
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/report_problem.dart';
import 'package:opencode_mobile/feedback/bug_report.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _fakeKey =
    'sk-ant-'
    'api03-FAKEFAKEFAKEFAKEFAKE1234567890';

Widget _app(Widget home) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

/// Records what reaches the clipboard.
List<String> _clipboard(WidgetTester tester) {
  final copied = <String>[];
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    SystemChannels.platform,
    (call) async {
      if (call.method == 'Clipboard.setData') {
        copied.add((call.arguments as Map)['text'] as String);
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
  return copied;
}

Finder _key(String key) => find.byKey(ValueKey(key));

Future<void> _tapKey(WidgetTester tester, String key) async {
  await tester.ensureVisible(_key(key));
  await tester.pumpAndSettle();
  await tester.tap(_key(key));
  await tester.pumpAndSettle();
}

/// Opens Details and returns exactly the text that is sent.
Future<String> _details(WidgetTester tester) async {
  if (_key('report-problem-preview-text').evaluate().isEmpty) {
    await _tapKey(tester, 'report-problem-details');
  }
  return tester.widget<KitText>(_key('report-problem-preview-text')).text;
}

Future<void> _type(WidgetTester tester, String words) async {
  await tester.enterText(
    find.descendant(
      of: _key('report-problem-description'),
      matching: find.byType(EditableText),
    ),
    words,
  );
  await tester.pumpAndSettle();
}

void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

void main() {
  setUp(() {
    KitRedact.clearKnownSecrets();
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('a kept error is what went wrong; Details shows exactly what '
      'is sent; Send opens the prefilled GitHub form after the link '
      'confirmation', (tester) async {
    _phone(tester);
    final diagnostics = AppDiagnosticsController()
      ..record(
        StateError('render failed with $_fakeKey'),
        StackTrace.fromString('#0 build (lib/chat.dart:42:3)'),
        source: 'flutter',
      );
    final launched = <Uri>[];
    await tester.pumpWidget(
      _app(
        AppDiagnosticsScreen(
          diagnostics: diagnostics,
          version: () async => '9.9.9+1',
          linkLauncher: (uri) async {
            launched.add(uri);
            return true;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Report a problem'), findsOneWidget);
    // It arrives knowing: the kept error in plain words, never its message.
    expect(find.text("A screen couldn't be drawn"), findsOneWidget);
    expect(find.textContaining('render failed'), findsNothing);
    expect(find.text('Optional'), findsOneWidget);
    expect(
      find.text('Passwords, keys and server addresses are taken out.'),
      findsOneWidget,
    );
    expect(
      find.text('Review it on GitHub before posting. It will be public.'),
      findsOneWidget,
    );
    // The one primary names its target.
    expect(find.text('Send as a GitHub issue'), findsOneWidget);
    // Folded until asked.
    expect(_key('report-problem-preview-text'), findsNothing);

    await _type(tester, 'Chat went blank after Send');
    final preview = await _details(tester);
    expect(preview, startsWith('Chat went blank after Send'));
    expect(preview, contains('App: 9.9.9+1'));
    expect(preview, contains('Connection: none'));
    expect(preview, contains('Diagnostics\n['));
    expect(preview, contains('render failed with'));
    expect(preview, isNot(contains(_fakeKey)));

    await _tapKey(tester, 'report-problem-send');
    // openExternalLink asks first and names the host.
    expect(find.text('Open external link?'), findsOneWidget);
    expect(launched, isEmpty);
    await tester.tap(find.text('Open link'));
    await tester.pumpAndSettle();

    expect(launched, hasLength(1));
    final uri = launched.single;
    expect(uri.host, 'github.com');
    expect(uri.path, '/Eslamasabry/opencode-mobile-next/issues/new');
    expect(uri.queryParameters['title'], 'Chat went blank after Send');
    expect(uri.queryParameters['app-version'], '9.9.9+1');
    // The form gets the same parts Details showed.
    expect(preview, contains(uri.queryParameters['what-happened']!));
    expect(preview, endsWith(uri.queryParameters['logs']!));
    expect(uri.toString(), isNot(contains(_fakeKey)));
  });

  testWidgets('an attached failure titles the card and needs no words; Copy '
      'copies exactly what Details shows', (tester) async {
    _phone(tester);
    final copied = _clipboard(tester);
    await tester.pumpWidget(
      _app(
        AppDiagnosticsScreen(
          diagnostics: AppDiagnosticsController()
            ..record(StateError('boom'), null, source: 'sse'),
          version: () async => '9.9.9+1',
          error: const KitReport(
            title: "Couldn't load files",
            details: 'FormatException: bad listing',
            errorType: 'FormatException',
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text("Couldn't load files"), findsOneWidget);
    expect(
      find.text('The error details and app information are attached.'),
      findsOneWidget,
    );
    final preview = await _details(tester);
    expect(preview, startsWith("Couldn't load files\n\n(not described)"));
    expect(preview, contains('Type: FormatException'));
    // The diagnostics kept on the phone go with it.
    expect(preview, contains('boom'));

    await tester.tap(_key('report-problem-copy'));
    await tester.pump();
    expect(copied, [preview]);
    await tester.pumpAndSettle();
  });

  testWidgets('with nothing noticed the words are required, and nothing '
      'opens until there are some', (tester) async {
    _phone(tester);
    final launched = <Uri>[];
    await tester.pumpWidget(
      _app(
        AppDiagnosticsScreen(
          diagnostics: AppDiagnosticsController(),
          version: () async => '9.9.9+1',
          linkLauncher: (uri) async {
            launched.add(uri);
            return true;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No error noticed'), findsOneWidget);
    expect(find.text('What happened?'), findsOneWidget);
    expect(find.text('Optional'), findsNothing);

    await _tapKey(tester, 'report-problem-send');
    expect(find.text('Say what happened first'), findsOneWidget);
    expect(find.text('Open external link?'), findsNothing);

    await _type(tester, 'Voice typing stops');
    expect(find.text('Say what happened first'), findsNothing);
    await _tapKey(tester, 'report-problem-send');
    await tester.tap(find.text('Open link'));
    await tester.pumpAndSettle();
    expect(launched.single.queryParameters['title'], 'Voice typing stops');
  });

  testWidgets('diagnostics too long for the link: the page says so before '
      'Send, and Send copies them before the form opens', (tester) async {
    _phone(tester);
    final copied = _clipboard(tester);
    final diagnostics = AppDiagnosticsController(maxEntries: 40);
    for (var i = 0; i < 12; i++) {
      diagnostics.record(
        StateError('failure $i ${'with a long explanation ' * 6}'),
        StackTrace.fromString('#0 frame $i\n#1 frame\n#2 frame\n#3 frame'),
        source: 'flutter',
        at: DateTime(2026, 9, 27, 10, i),
      );
    }
    final launched = <Uri>[];
    await tester.pumpWidget(
      _app(
        AppDiagnosticsScreen(
          diagnostics: diagnostics,
          version: () async => '9.9.9+1',
          linkLauncher: (uri) async {
            launched.add(uri);
            return true;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    await _type(tester, 'Everything is slow');
    expect(_key('report-problem-link-copies'), findsOneWidget);
    final preview = await _details(tester);

    await _tapKey(tester, 'report-problem-send');
    await tester.tap(find.text('Open link'));
    await tester.pumpAndSettle();

    expect(copied, hasLength(1));
    expect(preview, endsWith(copied.single));
    expect(launched.single.toString().length, lessThanOrEqualTo(2048));
    expect(launched.single.queryParameters['logs'], contains('paste them'));
  });

  testWidgets('everything this phone kept is one tap under Details: saved '
      'errors in plain words, and Clear asks with the count and empties '
      'the saved report too', (tester) async {
    _phone(tester);
    final directory = Directory.systemTemp.createTempSync('p82-report-');
    addTearDown(() => directory.deleteSync(recursive: true));
    // A previous run saved one error; this run opens the store again.
    final store = (await tester.runAsync(() async {
      final before = await ReportProblem.open(directory: directory);
      before.recordError(StateError('saved before restart'), null);
      before.dispose();
      return ReportProblem.open(directory: directory);
    }))!;
    addTearDown(store.dispose);
    final diagnostics = AppDiagnosticsController()
      ..record(StateError('this run'), null, source: 'sse');

    await tester.pumpWidget(
      _app(
        AppDiagnosticsScreen(
          diagnostics: diagnostics,
          store: store,
          version: () async => '9.9.9+1',
        ),
      ),
    );
    await tester.pumpAndSettle();
    // The saved report is preferred: its error leads the card.
    expect(find.text('Something went wrong'), findsOneWidget);

    await _tapKey(tester, 'report-problem-details');
    await _tapKey(tester, 'report-problem-kept');
    expect(find.byType(AppDiagnosticsKeptScreen), findsOneWidget);
    expect(find.text('App diagnostics'), findsOneWidget);
    // Listed in plain words; the message stays under the row's Details.
    expect(find.text('Something went wrong'), findsOneWidget);
    expect(find.textContaining('saved before restart'), findsNothing);
    expect(find.text('1 recent error'), findsOneWidget);

    await _tapKey(tester, 'clear-app-diagnostics');
    expect(find.text('Clear 1 error?'), findsOneWidget);
    expect(find.textContaining('also from the saved report'), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(store.entries, hasLength(1));

    await _tapKey(tester, 'clear-app-diagnostics');
    await tester.tap(_key('clear-app-diagnostics-confirm'));
    await tester.pumpAndSettle();
    expect(store.entries, isEmpty);
    expect(diagnostics.count, 0);
    expect(File('${directory.path}/report_problem.json').existsSync(), false);
    expect(_key('clear-app-diagnostics'), findsNothing);
  });

  testWidgets('the words survive leaving the page and are forgotten once '
      'the report went to GitHub', (tester) async {
    _phone(tester);
    Widget page() => AppDiagnosticsScreen(
      diagnostics: AppDiagnosticsController(),
      version: () async => '9.9.9+1',
      linkLauncher: (_) async => true,
    );
    await tester.pumpWidget(_app(page()));
    await tester.pumpAndSettle();
    await _type(tester, 'Kept while away');
    await tester.pumpWidget(_app(const SizedBox.shrink()));
    await tester.pumpWidget(_app(page()));
    await tester.pumpAndSettle();
    expect(find.text('Kept while away'), findsOneWidget);
    // One app-wide draft, the same key with or without a server (G10):
    // never keyed to a profile it could outlive.
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('oc.draft.report-problem.app'), 'Kept while away');

    await _tapKey(tester, 'report-problem-send');
    await tester.tap(find.text('Open link'));
    await tester.pumpAndSettle();
    await tester.pumpWidget(_app(const SizedBox.shrink()));
    await tester.pumpWidget(_app(page()));
    await tester.pumpAndSettle();
    expect(find.text('Kept while away'), findsNothing);
    expect(prefs.getString('oc.draft.report-problem.app'), isNull);
  });

  testWidgets('every Report a problem opens this page, with the failure '
      'attached', (tester) async {
    await tester.pumpWidget(_app(const Scaffold(body: SizedBox.shrink())));
    final context = tester.element(find.byType(SizedBox));
    unawaited(
      openBugReport(context, error: const KitReport(title: 'Sync failed')),
    );
    await tester.pumpAndSettle();
    expect(find.byType(AppDiagnosticsScreen), findsOneWidget);
    expect(find.text('Sync failed'), findsOneWidget);
  });

  testWidgets('the page fits a wide window and a phone at large text', (
    tester,
  ) async {
    for (final (size, scale) in [
      (const Size(1280, 800), 1.0),
      (const Size(360, 740), 2.0),
    ]) {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MediaQuery(
          data: MediaQueryData(
            size: size,
            textScaler: TextScaler.linear(scale),
          ),
          child: _app(
            AppDiagnosticsScreen(
              diagnostics: AppDiagnosticsController()
                ..record(StateError('boom'), null, source: 'sse'),
              version: () async => '9.9.9+1',
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.scrollUntilVisible(
        _key('report-problem-send'),
        200,
        scrollable: find
            .descendant(
              of: _key('app-diagnostics-report'),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      expect(_key('report-problem-send'), findsOneWidget);
      expect(tester.takeException(), isNull);
    }
  });
}
