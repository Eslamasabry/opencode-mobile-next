// Coverage ratchet for what a server answers the terminal screens (see
// paseo_coverage_support.dart for the rules): the terminal list and the shell
// choices, read by the app's OpenCode 2 gateway over a local port and
// drawn by the real Terminal page and Settings > Default shell.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';

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
  final family = CoverageFamily('terminal_wire', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('terminal · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final payload = Map<String, dynamic>.from(variant['payload'] as Map);
      final repo = ToolsRepo();
      Widget Function(dynamic controller) home;
      if (variant['kind'] == 'pty_list') {
        final list = payload['list'] as List;
        repo.terminals = await readFromWire(tester, {
          '/api/pty': {'data': list},
        }, (r) => r.listTerminals());
        home = (c) => TerminalScreen(controller: c, page: true);
      } else {
        repo.shellSettings = await readFromWire(tester, {
          '/api/config/shell': {'data': payload['shells']},
          '/api/config': {
            'data': [
              {'type': 'document', 'info': payload['config']},
            ],
          },
        }, (r) => r.loadTerminalShellSettings());
        home = (c) => Material(
          child: KitScreen(
            body: ListView(children: [DefaultShellRow(controller: c)]),
          ),
        );
      }
      final (store, controller) = await toolsConnection(repo);
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        serversApp(boundary, store, controller, home: home(controller)),
      );
      await frames(tester, 14);
      final seen = <String>[screenText(tester).join('\n')];
      await writeCasePng(tester, boundary, 'term_$id');
      if (variant['kind'] == 'pty_list') {
        // Show each terminal (its tab): its Details say what runs.
        for (var i = 0; i < repo.terminals.length; i++) {
          final tab = find.byKey(
            ValueKey('terminal-session-${repo.terminals[i].id}'),
          );
          if (tab.evaluate().isNotEmpty) {
            await tester.tap(tab, warnIfMissed: false);
          }
          await frames(tester, 12);
          seen.add(screenText(tester).join('\n'));
          if (i == 0) await writeCasePng(tester, boundary, 'term_${id}_open');
          // The terminal's Details: what runs, where, and its number.
          await tester.tap(
            find.byKey(const ValueKey('terminal-surface-menu')),
            warnIfMissed: false,
          );
          await frames(tester, 8);
          await tester.tap(
            find.byKey(const ValueKey('terminal-details')),
            warnIfMissed: false,
          );
          await frames(tester, 10);
          seen.add(screenText(tester).join('\n'));
          if (i == 1) {
            await writeCasePng(tester, boundary, 'term_${id}_details');
          }
          final sheet = tester.state<NavigatorState>(
            find.byType(Navigator).first,
          );
          await sheet.maybePop();
          await frames(tester, 6);
          final navigator = tester.state<NavigatorState>(
            find.byType(Navigator).first,
          );
          await navigator.maybePop();
          await frames(tester, 8);
        }
      }
      if (variant['kind'] == 'shells') {
        // The choices wait in the sheet the row opens.
        final row = find.byType(DefaultShellRow);
        await tester.tap(
          find.descendant(of: row, matching: find.byType(KitRow)).first,
          warnIfMissed: false,
        );
        await frames(tester, 12);
        seen.add(screenText(tester).join('\n'));
        await writeCasePng(tester, boundary, 'term_${id}_sheet');
      }
      final screen = seen.join('\n');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      await tester.pumpWidget(const SizedBox.shrink());
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
