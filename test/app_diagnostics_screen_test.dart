// Report a problem (slice-P8.2, map: app-diagnostics and
// report-problem-preview-sheet): describe, attach, preview exactly what is
// sent, then GitHub (through openExternalLink), Copy or Share. Nothing here
// files an issue: the launcher and the share sheet are fakes. Fake keys only.
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

String _previewText(WidgetTester tester) =>
    tester.widget<KitText>(_key('report-problem-preview-text')).text;

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

  testWidgets('describe, review exactly what is sent, then open the '
      'prefilled GitHub form through the link confirmation', (tester) async {
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
          share: (_, _) async => true,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Report a problem'), findsOneWidget);
    expect(find.textContaining('before anything leaves'), findsOneWidget);
    // The error kept on this phone is listed and included by default.
    expect(find.text('1 recent error'), findsOneWidget);
    expect(find.textContaining('1 event from this phone'), findsOneWidget);

    // Nothing described and no error attached: say what happened first.
    await _tapKey(tester, 'report-problem-review');
    expect(find.text('Say what happened first'), findsOneWidget);
    expect(_key('report-problem-preview-sheet'), findsNothing);

    await tester.enterText(
      find.descendant(
        of: _key('report-problem-description'),
        matching: find.byType(EditableText),
      ),
      'Chat went blank after Send',
    );
    await tester.pumpAndSettle();
    await _tapKey(tester, 'report-problem-review');

    expect(_key('report-problem-preview-sheet'), findsOneWidget);
    expect(find.text('This is exactly what is sent'), findsOneWidget);
    expect(find.textContaining('GitHub issues are public'), findsOneWidget);
    final preview = _previewText(tester);
    expect(preview, startsWith('Chat went blank after Send'));
    expect(preview, contains('App: 9.9.9+1'));
    expect(preview, contains('Diagnostics\n['));
    expect(preview, contains('render failed with'));
    expect(preview, isNot(contains(_fakeKey)));

    await _tapKey(tester, 'report-problem-open-github');
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
    // The form gets the same parts the preview showed.
    expect(preview, contains(uri.queryParameters['what-happened']!));
    expect(preview, endsWith(uri.queryParameters['logs']!));
    expect(uri.toString(), isNot(contains(_fakeKey)));
  });

  testWidgets('an attached error needs no description; diagnostics can be '
      'left out; Copy copies exactly the preview', (tester) async {
    _phone(tester);
    final copied = _clipboard(tester);
    final diagnostics = AppDiagnosticsController()
      ..record(StateError('boom'), null, source: 'sse');
    await tester.pumpWidget(
      _app(
        AppDiagnosticsScreen(
          diagnostics: diagnostics,
          version: () async => '9.9.9+1',
          error: const KitReport(
            title: "Couldn't load files",
            details: 'FormatException: bad listing',
            errorType: 'FormatException',
          ),
          share: (_, _) async => true,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text("Attached: Couldn't load files"), findsOneWidget);
    await tester.tap(_key('report-problem-include'));
    await tester.pumpAndSettle();
    await _tapKey(tester, 'report-problem-review');

    final preview = _previewText(tester);
    expect(preview, startsWith("Couldn't load files\n\n(not described)"));
    expect(preview, contains('Type: FormatException'));
    expect(preview, isNot(contains('Diagnostics')));
    expect(preview, isNot(contains('boom')));

    await tester.tap(_key('report-problem-copy'));
    await tester.pump();
    expect(copied, [preview]);
    await tester.pumpAndSettle();
  });

  testWidgets('Share hands the preview to the share sheet; a share sheet '
      'that does not open copies it instead', (tester) async {
    _phone(tester);
    final copied = _clipboard(tester);
    final shared = <(String, String)>[];
    var opens = true;
    await tester.pumpWidget(
      _app(
        AppDiagnosticsScreen(
          diagnostics: AppDiagnosticsController(),
          version: () async => '9.9.9+1',
          share: (text, subject) async {
            shared.add((text, subject));
            return opens;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(EditableText), 'Voice typing stops');
    await _tapKey(tester, 'report-problem-review');
    final preview = _previewText(tester);
    await tester.tap(_key('report-problem-share'));
    await tester.pumpAndSettle();

    expect(shared, [(preview, 'Voice typing stops')]);
    expect(_key('report-problem-preview-sheet'), findsNothing);
    expect(copied, isEmpty);

    opens = false;
    await _tapKey(tester, 'report-problem-review');
    await tester.tap(_key('report-problem-share'));
    await tester.pumpAndSettle();
    expect(copied, [preview]);
  });

  testWidgets('diagnostics too long for the link are copied before the form '
      'opens, and the sheet says so', (tester) async {
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
          share: (_, _) async => true,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(EditableText), 'Everything is slow');
    await _tapKey(tester, 'report-problem-review');
    expect(_key('report-problem-link-copies'), findsOneWidget);
    final preview = _previewText(tester);

    await _tapKey(tester, 'report-problem-open-github');
    await tester.tap(find.text('Open link'));
    await tester.pumpAndSettle();

    expect(copied, hasLength(1));
    expect(preview, endsWith(copied.single));
    expect(launched.single.toString().length, lessThanOrEqualTo(2048));
    expect(launched.single.queryParameters['logs'], contains('paste them'));
  });

  testWidgets('errors saved before a restart are listed, and Clear asks with '
      'the count and empties the saved report too', (tester) async {
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
          share: (_, _) async => true,
        ),
      ),
    );
    await tester.pumpAndSettle();
    // The saved error is listed in plain words; its message stays under
    // the row's Details.
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
    expect(_key('report-problem-include'), findsNothing);
  });

  testWidgets('the description survives leaving the page and is forgotten '
      'once the report went to GitHub', (tester) async {
    _phone(tester);
    Widget page() => AppDiagnosticsScreen(
      diagnostics: AppDiagnosticsController(),
      version: () async => '9.9.9+1',
      linkLauncher: (_) async => true,
      share: (_, _) async => true,
    );
    await tester.pumpWidget(_app(page()));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(EditableText), 'Kept while away');
    await tester.pumpAndSettle();
    await tester.pumpWidget(_app(const SizedBox.shrink()));
    await tester.pumpWidget(_app(page()));
    await tester.pumpAndSettle();
    expect(find.text('Kept while away'), findsOneWidget);
    // One app-wide draft, the same key with or without a server (G10):
    // never keyed to a profile it could outlive.
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('oc.draft.report-problem.app'), 'Kept while away');

    await _tapKey(tester, 'report-problem-review');
    await _tapKey(tester, 'report-problem-open-github');
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
    expect(find.text('Attached: Sync failed'), findsOneWidget);
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
              share: (_, _) async => true,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.scrollUntilVisible(
        _key('report-problem-review'),
        200,
        scrollable: find
            .descendant(
              of: _key('app-diagnostics'),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      expect(_key('report-problem-review'), findsOneWidget);
      expect(tester.takeException(), isNull);
    }
  });
}
