// Coverage ratchet for what Paseo sends the model picker: the entries of
// get_providers_snapshot_response, answered by a scripted Paseo peer to the
// app's real gateway and drawn by the real picker (see
// paseo_coverage_support.dart for the rules).
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../paseo_acp_pilot_test.dart' show FakePaseoSocket;
import 'lists_support.dart';
import 'models_support.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final family = CoverageFamily('providers');
  final fetched = <String, Map<String, Object?>>{};

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    for (final variant in family.cases) {
      final socket = FakePaseoSocket();
      socket.handlers['get_providers_snapshot_request'] = (_) => (
        'get_providers_snapshot_response',
        variant['payload'] as Map<String, dynamic>,
      );
      final gateway = PaseoGateway(
        directory: '/root/projects/shopfront',
        defaultProviderModes: const {'claude': 'default'},
        transport: PaseoTransport(
          endpoint: 'ws://100.64.0.20:6767',
          socketFactory: (_, _) async => socket,
        ),
      );
      fetched[variant['id'] as String] = {
        'providers': await gateway.providers(),
        'agents': await gateway.agents(),
        'defaults': await gateway.loadChatDefaults(),
      };
      gateway.close();
      await socket.close();
    }
  });

  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('paseo providers · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final data = fetched[id]!;
      final api = ModelsApi(paseoServerCapabilities)
        ..providerList = data['providers'] as ProvidersResponse
        ..agentList = data['agents'] as List<AgentInfo>;
      final repository = ModelsRepository()
        ..defaults = data['defaults'] as ChatDefaults;
      final controller = await listsController(
        api: api,
        repository: repository,
      );
      final screens = ListsScreens(
        tester,
        controller,
        GlobalKey(),
        'paseo-providers-$id',
      );
      await screens.modelPicker();
      File(
        'build/coverage/paseoproviders_$id.txt',
      ).writeAsStringSync(flat(screens.text));
      final problems = checkCase(family, variant, screens.text);
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screens.text)}');
    });
  }
}
