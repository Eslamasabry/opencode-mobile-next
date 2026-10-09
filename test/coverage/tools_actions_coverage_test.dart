// Action gate for the tools area: every call that CHANGES something (the MCP
// calls, config writes, running a command, Paseo's plugin and agent-config
// messages) and every control on the Tools screens has a decision in
// test/fixtures/coverage/tools_actions_ledger.json:
//
//   reachable: <screen> > <control>    a tap path here checks the control
//                                      exists and does the thing, or `proof:`
//                                      names an existing test that does (the
//                                      ratchet checks it is still there)
//   not offered: <reason>              why no control calls it
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show McpServerInfo;
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'servers_support.dart';
import 'tools_support.dart';

Map<String, String> _load(String name) =>
    (jsonDecode(File('test/fixtures/coverage/$name').readAsStringSync()) as Map)
        .cast<String, String>();

final _ledger = _load('tools_actions_ledger.json');
final _wire =
    ((jsonDecode(
                  File(
                    'test/fixtures/coverage/tools_actions_samples.json',
                  ).readAsStringSync(),
                )
                as Map)['wire']
            as List)
        .cast<String>();

/// Every control the Tools screens offer, by the name the ledger uses.
const _app = [
  'app tools.tabs',
  'app mcp.add',
  'app mcp.connect',
  'app mcp.error-details',
  'app mcp.disconnect',
  'app mcp.sign-in',
  'app mcp.remove',
  'app mcp.copy-resource',
  'app catalog.browse',
  'app catalog.load',
  'app catalog.switch',
  'app catalog.stop',
  'app mcp.form-save',
  'app commands.run',
  'app commands.refresh',
  'app skills.open',
  'app references.copy',
  'app tools.open-tool',
  'app external.add-and-ask',
  'app web.search-and-add',
  'app context.open-and-copy',
  'app local-agent.connect',
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

Future<ToolsRepo> openTools(
  WidgetTester tester,
  ToolsRepo repo,
  ToolsSection section,
) async {
  final (store, controller) = await toolsConnection(repo);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    serversApp(
      GlobalKey(),
      store,
      controller,
      home: CapabilitiesScreen(controller: controller, initialSection: section),
    ),
  );
  await frames(tester, 14);
  return repo;
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
      reason: 'calls and controls with no entry in tools_actions_ledger.json',
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

  path(['app tools.tabs'], (tester) async {
    final repo = ToolsRepo();
    await openTools(tester, repo, ToolsSection.mcp);
    for (final label in ['Commands', 'Tools', 'Skills', 'References']) {
      await tester.tap(find.byKey(ValueKey('capabilities-tab-$label')));
      await frames(tester, 8);
      expect(
        find.byKey(ValueKey('tools-section-${label.toLowerCase()}')),
        findsOneWidget,
        reason: label,
      );
    }
  });

  path(['app mcp.add'], (tester) async {
    await openTools(
      tester,
      ToolsRepo()
        ..servers = const [McpServerInfo(name: 'github', status: 'connected')],
      ToolsSection.mcp,
    );
    await tester.tap(find.text('Add MCP server').first);
    await frames(tester, 10);
    expect(find.byKey(const ValueKey('mcp-add-sheet')), findsOneWidget);
    expect(find.byKey(const ValueKey('mcp-add-catalog')), findsOneWidget);
    expect(find.byKey(const ValueKey('mcp-add-manual')), findsOneWidget);
  });

  path(['app mcp.connect', 'oc1 POST /mcp/{name}/connect'], (tester) async {
    final repo = ToolsRepo()
      ..servers = const [McpServerInfo(name: 'notion', status: 'disabled')];
    await openTools(tester, repo, ToolsSection.mcp);
    final action = find.byKey(const ValueKey('mcp-action-notion'));
    expect(action, findsOneWidget);
    await tester.tap(action);
    await frames(tester, 10);
    expect(repo.connected, ['notion']);
  });

  path(['app mcp.error-details'], (tester) async {
    final repo = ToolsRepo()
      ..servers = const [
        McpServerInfo(
          name: 'linear',
          status: 'failed',
          error: 'connection refused by linear.example',
        ),
      ];
    await openTools(tester, repo, ToolsSection.mcp);
    await tester.tap(find.byKey(const ValueKey('mcp-error-linear')));
    await frames(tester, 10);
    expect(find.textContaining('connection refused'), findsWidgets);
  });
}
