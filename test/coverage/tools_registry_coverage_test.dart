// Coverage ratchet for one listing of the public MCP registry (see
// paseo_coverage_support.dart for the rules): the registry's answer is read by
// the app's real client and drawn by the real MCP catalogue. Header and
// variable VALUES in a listing are credentials: they are never kept, and the
// ledger says so.
import 'package:dio/dio.dart' show CancelToken;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/setup_registry.dart';
import 'package:opencode_mobile/ui/screens/mcp_catalog_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';
import 'tools_support.dart';

class _ReadClient implements SetupRegistryClient {
  _ReadClient(this.entries);

  final List<RegistryEntry> entries;

  @override
  Future<List<RegistryEntry>> fetch({
    CancelToken? cancelToken,
    String? search,
  }) async => entries;

  @override
  void dispose() {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
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
  final family = CoverageFamily('tools_registry', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('registry · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      // The app's real client reads the registry's answer (in real time)...
      final real = SetupRegistryClient(
        adapter: WireAdapter((options) => wireJson(variant['payload']!)),
      );
      final entries = (await tester.runAsync(real.fetch))!;
      real.dispose();
      // ...and the catalogue draws what it read.
      final client = _ReadClient(entries);
      final repo = ToolsRepo();
      final (store, controller) = await toolsConnection(repo);
      await store.prefs.setString(
        'oc.setupRegistry.srv',
        '{"version":1,"optedIn":true}',
      );
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        serversApp(
          boundary,
          store,
          controller,
          home: McpCatalogScreen(controller: controller, client: client),
        ),
      );
      await frames(tester, 16);
      final seen = <String>[screenText(tester).join('\n')];
      await writeCasePng(tester, boundary, 'tools_$id');
      // Turning a listing on opens the form filled in from it.
      final switches = find.byWidgetPredicate(
        (w) =>
            w.key is ValueKey &&
            '${(w.key! as ValueKey).value}'.startsWith('mcp-catalog-switch-'),
      );
      final shown = switches.evaluate().length;
      for (var i = 0; i < shown; i++) {
        await tester.tap(switches.at(i), warnIfMissed: false);
        await frames(tester, 14);
        seen.add(screenText(tester).join('\n'));
        if (i == 0) await writeCasePng(tester, boundary, 'tools_${id}_form');
        final navigator = tester.state<NavigatorState>(
          find.byType(Navigator).first,
        );
        await navigator.maybePop();
        await frames(tester, 8);
      }
      final screen = seen.join('\n');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      // A credential in the listing must never reach the screen.
      for (final secret in [
        'sk-test-0000000000000000',
        'sk-default-000000000000',
        '/srv/files',
      ]) {
        if (screen.contains(secret)) problems.add('"$secret" is on screen');
      }
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
