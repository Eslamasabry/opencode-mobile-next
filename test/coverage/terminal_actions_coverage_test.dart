// Action gate for the terminal area: every call that CHANGES something (the
// pty create, update, remove and ticket calls, Paseo's terminal messages) and
// every control on the terminal screens has a decision in
// test/fixtures/coverage/terminal_actions_ledger.json:
//
//   reachable: <screen> > <control>   a tap path here checks the control
//                                     exists and does the thing, or `proof:`
//                                     names an existing test that does (the
//                                     ratchet checks it is still there)
//   not offered: <reason>             why no control calls it
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'servers_support.dart';
import 'tools_support.dart';

Map<String, String> _load(String name) =>
    (jsonDecode(File('test/fixtures/coverage/$name').readAsStringSync()) as Map)
        .cast<String, String>();

final _ledger = _load('terminal_actions_ledger.json');
final _wire =
    ((jsonDecode(
                  File(
                    'test/fixtures/coverage/terminal_actions_samples.json',
                  ).readAsStringSync(),
                )
                as Map)['wire']
            as List)
        .cast<String>();

/// Every control the terminal screens offer, by the name the ledger uses.
const _app = [
  'app terminal.new',
  'app terminal.rename',
  'app terminal.remove-ended',
  'app terminal.open',
  'app terminal.reconnect',
  'app terminal.keys',
  'app terminal.source',
  'app terminal.local-restart',
  'app shell.choose',
  'app procs.details',
  'app procs.stop-one',
  'app procs.stop-kind',
  'app procs.stop-orphans',
  'app procs.retry',
  'app storage.scan',
  'app storage.stop-scan',
  'app storage.clean',
  'app migration.review-and-copy',
  'app migration.stop',
  'app migration.resume',
];

final _paths = <String, Future<void> Function(WidgetTester)>{};

void path(List<String> keys, Future<void> Function(WidgetTester tester) body) {
  for (final key in keys) {
    _paths[key] = body;
  }
  testWidgets('action · ${keys.first}', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await body(tester);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}

const _running = TerminalProcess(
  id: 'pty_run1',
  title: 'Build watcher',
  command: 'bash',
  arguments: [],
  directory: '/work/shop',
  running: true,
  pid: 4242,
);
const _ended = TerminalProcess(
  id: 'pty_done1',
  title: 'Test run',
  command: 'flutter',
  arguments: ['test'],
  directory: '/work/shop',
  running: false,
  pid: 4343,
  exitCode: 1,
);

Future<void> openTerminal(WidgetTester tester, ToolsRepo repo) async {
  final (store, controller) = await toolsConnection(repo);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    serversApp(
      GlobalKey(),
      store,
      controller,
      home: TerminalScreen(controller: controller, page: true),
    ),
  );
  await frames(tester, 14);
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
      reason:
          'calls and controls with no entry in terminal_actions_ledger.json',
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

  path(['app terminal.new', 'oc1 POST /pty', 'oc1 POST /api/pty'], (
    tester,
  ) async {
    final repo = ToolsRepo()..terminals = const [_running];
    await openTerminal(tester, repo);
    await tester.tap(find.byKey(const ValueKey('terminal-new')).first);
    await frames(tester, 12);
    expect(repo.created, hasLength(1));
  });

  path(
    ['app terminal.rename', 'oc1 PUT /pty/{ptyID}', 'oc1 PUT /api/pty/{ptyID}'],
    (tester) async {
      final repo = ToolsRepo()..terminals = const [_running];
      await openTerminal(tester, repo);
      await tester.longPress(find.text('Build watcher').first);
      await frames(tester, 6);
      await tester.tap(find.byKey(const ValueKey('terminal-menu-rename')));
      await frames(tester, 8);
      await tester.enterText(
        find.byKey(const ValueKey('terminal-rename-field')),
        'Watcher',
      );
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('terminal-rename-confirm')));
      await frames(tester, 10);
      expect(repo.renamed, ['pty_run1:Watcher']);
    },
  );

  path(['app terminal.remove-ended'], (tester) async {
    final repo = ToolsRepo()..terminals = const [_running, _ended];
    await openTerminal(tester, repo);
    await tester.tap(find.byKey(const ValueKey('terminal-list-menu')));
    await frames(tester, 6);
    await tester.tap(find.byKey(const ValueKey('terminal-remove-ended')));
    await frames(tester, 8);
    await tester.tap(
      find.byKey(const ValueKey('terminal-remove-ended-confirm')),
    );
    await frames(tester, 10);
    expect(repo.removedTerminals, ['pty_done1']);
  });

  path(['app shell.choose'], (tester) async {
    final repo = ToolsRepo()
      ..shellSettings = const TerminalShellSettings(
        selected: '',
        options: [
          TerminalShellOption(
            path: '/bin/bash',
            name: 'bash',
            acceptable: true,
          ),
          TerminalShellOption(
            path: '/usr/bin/fish',
            name: 'fish',
            acceptable: true,
          ),
        ],
      );
    final (store, controller) = await toolsConnection(repo);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      serversApp(
        GlobalKey(),
        store,
        controller,
        home: Material(
          child: KitScreen(
            body: ListView(children: [DefaultShellRow(controller: controller)]),
          ),
        ),
      ),
    );
    await frames(tester, 12);
    await tester.tap(
      find
          .descendant(
            of: find.byType(DefaultShellRow),
            matching: find.byType(KitRow),
          )
          .first,
    );
    await frames(tester, 10);
    await tester.tap(find.byKey(const ValueKey('server-shell-/usr/bin/fish')));
    await frames(tester, 12);
    expect(repo.selectedShells, ['fish']);
  });
}
