// Coverage ratchet for what a server answers the Tools tabs (see
// paseo_coverage_support.dart for the rules). Each case is the answer the way
// OpenCode sends it, read by the app's real repository over a local port and
// drawn by the real Settings > Tools tabs.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';
import 'tools_support.dart';

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
  final family = CoverageFamily('tools_wire', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('tools · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final endpoint = variant['endpoint'] as String;
      final payload = variant['payload'];
      final repo = ToolsRepo();
      ToolsSection section;
      switch (endpoint) {
        case '/api/command':
          repo.commands = await readFromWire(tester, {
            '/api/command': payload,
          }, (r) => r.listCommands());
          section = ToolsSection.commands;
        case '/api/skill':
          repo.skills = await readFromWire(tester, {
            '/api/skill': payload,
          }, (r) => r.listSkills());
          section = ToolsSection.skills;
        case '/api/reference':
          repo.references = await readFromWire(tester, {
            '/api/reference': payload,
          }, (r) => r.listReferences());
          section = ToolsSection.references;
        case '/experimental/tool':
          final p = payload as Map;
          final read = await readFromWire(
            tester,
            {
              '/experimental/tool': p['tools'],
              '/experimental/tool/ids': p['ids'],
              '/experimental/capabilities': p['capabilities'],
            },
            (r) async => (
              await r.listCodingTools(providerID: 'anthropic', modelID: 'opus'),
              await r.listCodingToolIDs(),
              await r.loadExperimentalCapabilities(),
            ),
          );
          repo.tools = read.$1;
          repo.toolIds = read.$2;
          repo.experimental = read.$3;
          section = ToolsSection.tools;
        case '/mcp':
          repo.servers = await readFromWire(tester, {
            '/mcp': payload,
          }, (r) => r.listMcpServers());
          section = ToolsSection.mcp;
        case '/experimental/resource':
          repo.resources = await readFromWire(tester, {
            '/experimental/resource': payload,
          }, (r) => r.listMcpResources());
          section = ToolsSection.mcp;
        default:
          throw StateError('no screen for $endpoint');
      }
      final (store, controller) = await toolsConnection(repo);
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        serversApp(
          boundary,
          store,
          controller,
          home: CapabilitiesScreen(
            controller: controller,
            initialSection: section,
          ),
        ),
      );
      await frames(tester, 14);
      final seen = <String>[screenText(tester).join('\n')];
      await writeCasePng(tester, boundary, 'tools_$id');
      // Open what a row opens (a skill, a reference, a tool), one at a time.
      if (section == ToolsSection.skills ||
          section == ToolsSection.references ||
          section == ToolsSection.tools) {
        final count = find
            .descendant(
              of: find.byKey(ValueKey('tools-section-${section.name}')),
              matching: find.byType(KitRow),
            )
            .evaluate()
            .length;
        for (var i = 0; i < count; i++) {
          final rows = find.descendant(
            of: find.byKey(ValueKey('tools-section-${section.name}')),
            matching: find.byType(KitRow),
          );
          if (i >= rows.evaluate().length) break;
          await tester.tap(rows.at(i), warnIfMissed: false);
          await frames(tester, 10);
          seen.add(screenText(tester).join('\n'));
          final fold = find.text('Details');
          if (fold.evaluate().isNotEmpty) {
            await tester.tap(fold.last, warnIfMissed: false);
            await frames(tester, 8);
            seen.add(screenText(tester).join('\n'));
          }
          if (i == 0) {
            await writeCasePng(tester, boundary, 'tools_${id}_opened');
          }
          final navigator = tester.state<NavigatorState>(
            find.byType(Navigator).first,
          );
          await navigator.maybePop();
          await frames(tester, 8);
        }
      }
      if (section == ToolsSection.mcp) {
        // A failed server's own words wait behind its Details action.
        for (final name in ['linear', 'figma']) {
          final action = find.byKey(ValueKey('mcp-error-$name'));
          if (action.evaluate().isEmpty) continue;
          await tester.tap(action, warnIfMissed: false);
          await frames(tester, 8);
          seen.add(screenText(tester).join('\n'));
          final navigator = tester.state<NavigatorState>(
            find.byType(Navigator).first,
          );
          await navigator.maybePop();
          await frames(tester, 6);
        }
      }
      final screen = seen.join('\n');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
