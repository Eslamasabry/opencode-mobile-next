// Coverage ratchet for what a server answers the terminal screens (see
// paseo_coverage_support.dart for the rules): the terminal list and the shell
// choices, read by the app's real OpenCode 1 repository over a local port
// (and, for the list, by the OpenCode 2 gateway: both must read the same
// terminal), and drawn by the real Terminal page and Settings > Default shell.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';
import 'tools_support.dart';

String _show(TerminalProcess p) =>
    '${p.id}|${p.title}|${p.command}|${p.arguments}|${p.directory}|${p.running}|${p.pid}|${p.exitCode}';

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
          '/pty': list,
        }, (r) => r.listTerminals());
        // OpenCode 2 sends the same terminals in its envelope.
        final v2 = await tester.runAsync(() async {
          final transport = Api2Transport(
            baseUrl: 'http://127.0.0.1:1',
            password: '',
          );
          transport.dio.httpClientAdapter = WireAdapter(
            (options) => wireJson({
              'data': list,
              'location': {
                'directory': '/work/shop',
                'workspaceID': 'wrk_main',
                'project': {'id': 'prj_shop', 'directory': '/work/shop'},
              },
            }),
          );
          final gateway = Api2OperationsGateway(
            client: Api2Client(transport: transport),
          );
          final read = await gateway.listTerminals();
          transport.dio.close(force: true);
          return read;
        });
        expect(
          [for (final p in v2!) _show(p)],
          [for (final p in repo.terminals) _show(p)],
          reason: 'OpenCode 1 and 2 read the same terminals',
        );
        home = (c) => TerminalScreen(controller: c, page: true);
      } else {
        repo.shellSettings = await readFromWire(tester, {
          '/pty/shells': payload['shells'],
          '/global/config': payload['config'],
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
        // Open each terminal: its own page says more than its row.
        for (var i = 0; i < repo.terminals.length; i++) {
          await tester.tap(
            find.text(repo.terminals[i].title).first,
            warnIfMissed: false,
          );
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
