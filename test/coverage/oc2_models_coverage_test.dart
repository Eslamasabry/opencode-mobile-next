// Coverage ratchet for what an OpenCode 2 server sends the model picker
// (providers, models, agents and the settings' default model and agent): the
// wire payloads are served to the app's real OpenCode 2 gateways and the
// picker is drawn from what they parsed. Credentials in those payloads must
// never reach the screen (see paseo_coverage_support.dart for the rules).
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'lists_support.dart';
import 'models_support.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late WireServer wire;
  final fetched = <String, Map<String, Object?>>{};
  final family = CoverageFamily('oc2_models', prefix: '');

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    wire = await WireServer.start();
    await withRealHttp(() async {
      final client = Api2Client.connect(
        baseUrl: wire.baseUrl,
        password: 'fixture',
      );
      final gateway = Api2Gateway(client: client);
      final repository = Api2OperationsGateway(client: client);
      for (final variant in family.cases) {
        final body = variant['payload'] as Map;
        const location = {
          'directory': '/work/shopfront',
          'project': {
            'id': 'prj_shopfront',
            'directory': '/work/shopfront',
            'canonical': '/work/shopfront',
          },
        };
        wire.handler = (r) {
          final path = r.path.substring(4);
          return switch (path) {
            '/provider' => {'location': location, 'data': body['providers']},
            '/model' => {'location': location, 'data': body['models']},
            '/model/default' => {
              'location': location,
              'data': (body['models'] as List).first,
            },
            '/agent' => {'location': location, 'data': body['agents']},
            '/config' => body['config'],
            _ => null,
          };
        };
        try {
          fetched[variant['id'] as String] = {
            'providers': await gateway.providers(),
            'agents': await gateway.agents(),
            'catalog': await repository.loadCatalog(),
            'defaults': await repository.loadChatDefaults(),
          };
        } on ProductException catch (error) {
          fail('${variant['id']}: ${error.message}: ${error.cause}');
        }
      }
      client.close();
    });
  });
  tearDownAll(() => wire.close());

  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 2 models · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final data = fetched[id]!;
      final api = ModelsApi(api2ServerCapabilities)
        ..providerList = data['providers'] as ProvidersResponse
        ..agentList = data['agents'] as List<AgentInfo>;
      final repository = ModelsRepository()
        ..catalogSnapshot = data['catalog'] as CatalogSnapshot
        ..defaults = data['defaults'] as ChatDefaults;
      final controller = await listsController(
        api: api,
        repository: repository,
      );
      final screens = ListsScreens(
        tester,
        controller,
        GlobalKey(),
        'oc2-models-$id',
      );
      await screens.modelPicker();
      File(
        'build/coverage/oc2models_$id.txt',
      ).writeAsStringSync(flat(screens.text));
      final problems = checkCase(family, variant, screens.text);
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screens.text)}');
    });
  }
}
