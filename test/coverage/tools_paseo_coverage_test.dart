// Coverage ratchet for Paseo's command list: the daemon's answer is read by
// the app's real Paseo gateway over a scripted socket and drawn by the real
// commands sheet. The daemon's skills and agent-config messages are messages
// the app never asks for; the ledger says so.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show CommandInfo, ProductException;
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
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
  final family = CoverageFamily('tools_paseo', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('paseo commands · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final daemon = WireDaemon({'status': 'server_info', 'version': '0.9.2'});
      daemon.handlers['get_providers_snapshot_request'] = (_) => (
        'get_providers_snapshot_response',
        {
          'entries': [
            {
              'provider': 'claude',
              'status': 'ready',
              'enabled': true,
              'source': 'builtin',
              'models': [
                {
                  'provider': 'claude',
                  'id': 'opus',
                  'label': 'Opus',
                  'isDefault': true,
                },
              ],
              'modes': <Object>[],
            },
          ],
          'generatedAt': '2026-10-09T08:00:00.000Z',
        },
      );
      daemon.handlers['list_commands_request'] = (_) => (
        'list_commands_response',
        Map<String, dynamic>.from(variant['payload'] as Map),
      );
      final gateway = PaseoGateway(
        transport: PaseoTransport(
          endpoint: 'ws://127.0.0.1:6767',
          socketFactory: (_, _) async => daemon,
        ),
        directory: '/work/shop',
      );
      final repo = ToolsRepo();
      // The daemon refusing becomes the failure the app made of it.
      final read = await tester.runAsync(() async {
        try {
          return (commands: await gateway.listCommands(), failure: null);
        } on PaseoFailure catch (error) {
          return (commands: const <CommandInfo>[], failure: error);
        }
      });
      repo.commands = read!.commands;
      if (read.failure != null) {
        repo.commandsError = ProductException(read.failure!.message);
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
            initialSection: ToolsSection.commands,
          ),
        ),
      );
      await frames(tester, 14);
      final screen = screenText(tester).join('\n');
      await writeCasePng(tester, boundary, 'tools_$id');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
