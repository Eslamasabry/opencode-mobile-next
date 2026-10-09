// Coverage ratchet for what an OpenCode 1 server sends the model picker: the
// provider list and agents of the original routes, the v2 catalog it also
// serves, and the settings' default model and agent. The wire payloads are
// served to the app's real OpenCode 1 client and the picker is drawn from
// what it parsed. Credentials in those payloads must never reach the screen.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'lists_support.dart';
import 'models_support.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late WireServer wire;
  final fetched = <String, Map<String, Object?>>{};
  final family = CoverageFamily('oc1_models', prefix: '');

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    wire = await WireServer.start();
    await withRealHttp(() async {
      final api = OpenCodeApi(baseUrl: wire.baseUrl);
      final repository = SdkProductRepository(api.sdkClient);
      for (final variant in family.cases) {
        final body = variant['payload'] as Map;
        const location = {
          'directory': '/work/shopfront',
          'project': {'id': 'prj_shopfront', 'directory': '/work/shopfront'},
        };
        wire.handler = (r) => switch (r.path) {
          '/provider' => body['list'],
          '/agent' => body['agents'],
          '/config' => body['config'],
          '/api/provider' => {'location': location, 'data': body['providers']},
          '/api/model' => {'location': location, 'data': body['models']},
          '/api/agent' => {'location': location, 'data': body['agents']},
          _ => null,
        };
        try {
          final id = variant['id'] as String;
          if (id == 'providers_v1') {
            fetched[id] = {
              'providers': await api.providers(),
              'agents': await api.agents(),
              'defaults': await repository.loadChatDefaults(),
            };
          } else {
            fetched[id] = {'catalog': await repository.loadCatalog()};
          }
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
    testWidgets('opencode 1 models · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final data = fetched[id]!;
      final api = ModelsApi()
        ..providerList = data['providers'] as ProvidersResponse?
        ..agentList = (data['agents'] as List<AgentInfo>?) ?? const [];
      final repository = ModelsRepository()
        ..catalogSnapshot = data['catalog'] as CatalogSnapshot?
        ..defaults = data['defaults'] as ChatDefaults?;
      final controller = await listsController(
        api: api,
        repository: repository,
      );
      final screens = ListsScreens(
        tester,
        controller,
        GlobalKey(),
        'oc1-models-$id',
      );
      await screens.modelPicker();
      File(
        'build/coverage/oc1models_$id.txt',
      ).writeAsStringSync(flat(screens.text));
      final problems = checkCase(family, variant, screens.text);
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screens.text)}');
    });
  }
}
