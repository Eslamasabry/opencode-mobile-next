// Action gate for diagnostics, crash reports, export, import, notes and the
// demo: every call that CHANGES something or LEAVES the phone (the share
// sheet, the clipboard, a saved file, the GitHub link, the crash store) and
// every control on those screens has a decision in
// test/fixtures/coverage/diag_actions_ledger.json:
//
//   reachable: <screen> > <control>   a tap path here checks the control
//                                     exists and does the thing, or `proof:`
//                                     names an existing test that does (the
//                                     ratchet checks it is still there)
//   not offered: <reason>             why no control calls it
//
// What leaves the phone is checked for secrets in diag_privacy_test.dart.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/app_exit_history.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/demo_screen.dart';
import 'package:opencode_mobile/ui/screens/exit_history_section.dart';
import 'package:opencode_mobile/ui/screens/session_export_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'servers_support.dart';

Map<String, String> _load(String name) =>
    (jsonDecode(File('test/fixtures/coverage/$name').readAsStringSync()) as Map)
        .cast<String, String>();

final _ledger = _load('diag_actions_ledger.json');
final _wire =
    ((jsonDecode(
                  File(
                    'test/fixtures/coverage/diag_actions_samples.json',
                  ).readAsStringSync(),
                )
                as Map)['wire']
            as List)
        .cast<String>();

/// Every control these screens offer, by the name the ledger uses.
const _app = [
  'app crash.delete',
  'app crash.preview-row',
  'app crash.send-automatically',
  'app crash.share',
  'app crash.switch-off',
  'app crash.switch-on',
  'app demo.leave',
  'app demo.reset',
  'app demo.send',
  'app demo.set-up-server',
  'app exit.retry',
  'app exit.show-all',
  'app export.cancel',
  'app export.format',
  'app export.markdown-redact',
  'app export.redact',
  'app export.save-json',
  'app export.save-markdown',
  'app import.choose-file',
  'app import.destination',
  'app import.import',
  'app import.open',
  'app note.delete',
  'app note.discard',
  'app note.refresh',
  'app note.save',
  'app perf.clear',
  'app perf.copy',
  'app report.clear-errors',
  'app report.copy',
  'app report.copy-details',
  'app report.github',
  'app report.include',
  'app report.review',
  'app report.share',
];

final _paths = <String, Future<void> Function(WidgetTester)>{};

void path(List<String> keys, Future<void> Function(WidgetTester tester) body) {
  for (final key in keys) {
    _paths[key] = body;
  }
  testWidgets('action · ${keys.first}', (tester) async {
    tester.view.physicalSize = const Size(412, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await body(tester);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}

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

  test('every mutating call and control has a decision', () {
    final wanted = {..._wire, ..._app};
    expect(
      wanted.difference(_ledger.keys.toSet()),
      isEmpty,
      reason: 'calls and controls with no entry in diag_actions_ledger.json',
    );
    expect(
      _ledger.keys.toSet().difference(wanted),
      isEmpty,
      reason: 'ledger entries for calls or controls that no longer exist',
    );
    for (final entry in _ledger.entries) {
      final ok =
          entry.value.startsWith('reachable: ') ||
          (entry.value.startsWith('not offered: ') && entry.value.length > 30);
      expect(ok, isTrue, reason: '${entry.key}: reachable / not offered');
    }
  });

  test('a proof names a test that still exists', () {
    for (final entry in _ledger.entries) {
      final at = entry.value.indexOf('; proof: ');
      if (at < 0) continue;
      final parts = entry.value
          .substring(at + '; proof: '.length)
          .split(' :: ');
      expect(parts, hasLength(2), reason: entry.key);
      final file = File(parts[0]);
      expect(file.existsSync(), isTrue, reason: '${entry.key}: ${parts[0]}');
      expect(
        file.readAsStringSync(),
        contains(parts[1]),
        reason: '${entry.key}: no test called "${parts[1]}" in ${parts[0]}',
      );
    }
  });

  test('every reachable control without a proof has a tap path here', () {
    final tapped = {
      for (final entry in _ledger.entries)
        if (entry.value.startsWith('reachable: ') &&
            !entry.value.contains('; proof: '))
          entry.key,
    };
    expect(
      tapped.difference(_paths.keys.toSet()),
      isEmpty,
      reason: '"reachable" with no tap path and no proof',
    );
    expect(
      _paths.keys.toSet().difference(tapped),
      isEmpty,
      reason: 'tap paths the ledger does not call "reachable"',
    );
  });

  path(['app exit.show-all'], (tester) async {
    final history = AppExitHistory(
      supported: true,
      entries: [
        for (var i = 0; i < 7; i++)
          AppExitEntry(
            reason: 4,
            importance: 100,
            at: DateTime.now().subtract(Duration(hours: i + 1)),
            category: AppExitCategory.crash,
          ),
      ],
    );
    final (store, controller) = await serversState();
    controller.dispose();
    await tester.pumpWidget(
      serversApp(
        GlobalKey(),
        store,
        controller,
        home: Material(
          child: ListView(
            children: [ExitHistorySection(history: history, onRetry: () {})],
          ),
        ),
      ),
    );
    await frames(tester, 10);
    expect(find.byKey(const ValueKey('exit-history-5')), findsNothing);
    await tester.ensureVisible(
      find.byKey(const ValueKey('exit-history-show-all')),
    );
    await tester.tap(find.byKey(const ValueKey('exit-history-show-all')));
    await frames(tester, 10);
    expect(find.byKey(const ValueKey('exit-history-6')), findsOneWidget);
  });

  path(['app export.save-markdown'], (tester) async {
    final (store, controller) = await serversState();
    addTearDown(controller.dispose);
    final saved = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        theme: captureTheme(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SessionExportScreen(
          controller: controller,
          sessionID: 'ses_1',
          markdown: () => Uint8List.fromList(utf8.encode('the transcript')),
          saveFile: (name, bytes, mime) async {
            saved.add('$name|$mime|${utf8.decode(bytes)}');
            return Uri.file('/saved.md');
          },
        ),
      ),
    );
    await frames(tester, 10);
    // The transcript is not redacted, and the screen says so before saving.
    expect(
      find.byKey(const ValueKey('export-markdown-unredacted')),
      findsOneWidget,
    );
    expect(find.textContaining('not redacted'), findsWidgets);
    await tester.tap(find.text('Save readable transcript'));
    await frames(tester, 10);
    expect(saved, ['opencode-ses_1.md|text/markdown|the transcript']);
  });

  Future<void> openDemo(WidgetTester tester) async {
    final (store, controller) = await serversState();
    controller.dispose();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(controller),
        ],
        child: MaterialApp(
          theme: captureTheme(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(builder: (_) => const DemoScreen()),
                ),
                child: const Text('Open demo'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open demo'));
    await frames(tester, 12);
    expect(find.byType(DemoScreen), findsOneWidget);
  }

  path(['app demo.reset'], (tester) async {
    await openDemo(tester);
    await tester.tap(find.byKey(const Key('demo-reset')));
    await frames(tester, 12);
    expect(find.byType(DemoScreen), findsOneWidget);
    expect(find.byKey(const Key('demo-reset')), findsOneWidget);
  });

  path(['app demo.leave'], (tester) async {
    await openDemo(tester);
    await tester.tap(find.byKey(const Key('demo-leave')));
    await frames(tester, 12);
    expect(find.byType(DemoScreen), findsNothing);
    expect(find.text('Open demo'), findsOneWidget);
  });
}
