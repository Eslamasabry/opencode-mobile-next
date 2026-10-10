// Coverage ratchet for what an OpenCode 1 server sends the settings pages:
// the always-allowed actions and the shells the default-shell row offers. The
// wire bodies are served to the app's real OpenCode 1 client and the pages
// are drawn from what it parsed (see paseo_coverage_support.dart).
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'lists_support.dart';
import 'paseo_coverage_support.dart';
import 'settings_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late WireServer wire;
  final fetched = <String, Map<String, Object?>>{};
  final family = CoverageFamily('oc1_settings', prefix: '');

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    wire = await WireServer.start();
    await withRealHttp(() async {
      final api = OpenCodeApi(baseUrl: wire.baseUrl);
      final repository = SdkProductRepository(api.sdkClient);
      for (final variant in family.cases) {
        final body = variant['payload'];
        wire.handler = (r) => _route(variant, body, r);
        try {
          fetched[variant['id'] as String] = switch (variant['kind']) {
            'saved' => {'saved': await repository.listSavedPermissions()},
            _ => {'shells': await repository.loadTerminalShellSettings()},
          };
        } on ProductException catch (error) {
          fail('${variant['id']}: ${error.message}: ${error.cause}');
        }
      }
    });
  });
  tearDownAll(() => wire.close());

  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 1 settings · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final data = fetched[id]!;
      final repository = SettingsRepository();
      final controller = await listsController(repository: repository);
      final screens = ListsScreens(
        tester,
        controller,
        GlobalKey(),
        'oc1-settings-$id',
      );
      if (variant['kind'] == 'saved') {
        repository.saved = data['saved'] as dynamic;
        await screens.savedPermissions();
      } else {
        repository.shells = data['shells'] as dynamic;
        await screens.defaultShell();
      }
      File(
        'build/coverage/oc1settings_$id.txt',
      ).writeAsStringSync(flat(screens.text));
      final problems = checkCase(family, variant, screens.text);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 2));
      controller.dispose();
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screens.text)}');
    });
  }
}

Object? _route(Map variant, Object? body, WireRequest r) {
  final b = body is Map ? body : const {};
  switch (r.path) {
    case '/project/current':
      return {
        'id': 'prj_shopfront',
        'worktree': '/work/shopfront',
        'time': {'created': 1, 'updated': 1},
        'sandboxes': <String>[],
      };
    case '/api/permission/saved':
      return {'data': body};
    case '/global/config':
      return b['config'];
    case '/pty/shells':
      return b['shells'];
  }
  return null;
}
