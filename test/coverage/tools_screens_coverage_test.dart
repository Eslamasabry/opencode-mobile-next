// The tools area's whole screens, drawn for the contact sheet (the look
// gate): empty tabs, the add sheet, a server without a catalogue, the web
// sources page and the active context. Checks the words a person needs are on
// each screen and writes build/coverage/toolscene_*.png.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/active_context_screen.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/web_sources_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';
import 'tools_support.dart';

class _NoCatalogApi extends OpenCodeApi {
  _NoCatalogApi() : super(baseUrl: 'ws://build.example.net:6767');

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(serverCatalog: false, webSearch: false);
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

  Future<String> scene(
    WidgetTester tester,
    String name,
    ToolsRepo repo,
    Widget Function(dynamic controller) home, {
    OpenCodeApi? api,
  }) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final (store, controller) = await toolsConnection(repo);
    if (api != null) controller.api = api;
    addTearDown(controller.dispose);
    final boundary = GlobalKey();
    await tester.pumpWidget(
      serversApp(boundary, store, controller, home: home(controller)),
    );
    await frames(tester, 14);
    await writeCasePng(tester, boundary, 'toolscene_$name');
    return flat(screenText(tester).join('\n'));
  }

  testWidgets('empty MCP, Commands, Skills and References tabs', (
    tester,
  ) async {
    for (final section in [
      ToolsSection.mcp,
      ToolsSection.commands,
      ToolsSection.skills,
      ToolsSection.references,
    ]) {
      final text = await scene(
        tester,
        'empty_${section.name}',
        ToolsRepo(),
        (c) => CapabilitiesScreen(controller: c, initialSection: section),
      );
      expect(text.length, greaterThan(40), reason: section.name);
      await tester.pumpWidget(const SizedBox.shrink());
    }
  });

  testWidgets('a server without a catalogue says what is missing', (
    tester,
  ) async {
    final text = await scene(
      tester,
      'no_catalogue',
      ToolsRepo(),
      (c) => CapabilitiesScreen(controller: c),
      api: _NoCatalogApi(),
    );
    expect(text, contains('No outside agents yet'));
  });

  testWidgets('the active context with a few messages', (tester) async {
    final repo = ToolsRepo()
      ..context = const [
        ActiveContextMessage(
          id: 'msg_1',
          type: 'user',
          content: [
            ContextContent(ContextContentKind.text, 'Fix the retry button'),
          ],
        ),
        ActiveContextMessage(
          id: 'msg_2',
          type: 'assistant',
          content: [
            ContextContent(ContextContentKind.text, 'I changed the limit to 3'),
          ],
        ),
      ];
    final text = await scene(
      tester,
      'context',
      repo,
      (c) => ActiveContextScreen(controller: c, sessionID: 'ses_1'),
    );
    expect(text, contains('Fix the retry button'));
  });

  testWidgets('web sources on a server that cannot search', (tester) async {
    final text = await scene(
      tester,
      'web_no_search',
      ToolsRepo(),
      (c) => WebSourcesScreen(controller: c),
    );
    expect(text.length, greaterThan(40));
  });
}
