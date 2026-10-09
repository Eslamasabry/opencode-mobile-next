// Coverage ratchet for web search (the Web sources page): the provider list
// and a search answer the way the server sends them, validated and mapped as
// the app's OpenCode 2 gateway does, and drawn by the real page (see
// paseo_coverage_support.dart for the rules).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/web_sources_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';
import 'tools_support.dart';

class _SearchApi extends OpenCodeApi implements WebSearchGateway {
  _SearchApi(this.found, this.response)
    : super(baseUrl: 'https://studio.example.net:4096');

  final List<WebSearchProvider> found;
  final WebSearchResponse response;

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(webSearch: true);

  @override
  Future<List<WebSearchProvider>> webSearchProviders() async => found;

  @override
  Future<WebSearchResponse> searchWeb(
    String query, {
    required String providerID,
  }) async => response;
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
  final family = CoverageFamily('tools_web', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('web search · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final payload = Map<String, dynamic>.from(variant['payload'] as Map);
      final providers = [
        for (final p in (payload['providers']['data'] as List).cast<Map>())
          WebSearchProvider(id: p['id'] as String, name: p['name'] as String),
      ];
      final data = Map<String, dynamic>.from(payload['search']['data'] as Map);
      final response = WebSearchResponse(
        providerID: data['providerID'] as String,
        results: [
          for (final r in (data['results'] as List).cast<Map>())
            WebSearchResult(
              url: r['url'] as String,
              title: r['title'] as String?,
              content: r['content'] as String?,
            ),
        ],
      );
      final repo = ToolsRepo();
      final (store, controller) = await toolsConnection(repo);
      controller.api = _SearchApi(providers, response);
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        serversApp(
          boundary,
          store,
          controller,
          home: WebSourcesScreen(controller: controller),
        ),
      );
      await frames(tester, 10);
      final seen = <String>[screenText(tester).join('\n')];
      await tester.enterText(
        find.byType(TextField).first,
        'flutter networking',
      );
      await tester.pump();
      final submit = find.byKey(const ValueKey('web-search-submit'));
      await tester.ensureVisible(submit);
      await tester.tap(submit, warnIfMissed: false);
      await frames(tester, 14);
      seen.add(screenText(tester).join('\n'));
      await writeCasePng(tester, boundary, 'tools_$id');
      final screen = seen.join('\n');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      await tester.pumpWidget(const SizedBox.shrink());
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
