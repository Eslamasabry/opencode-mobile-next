// Coverage ratchet for GET /api/session/stats, the Usage page's Spent tab:
// the payload is served to the app's real OpenCode 2 gateway and the page is
// drawn from what it parsed (see paseo_coverage_support.dart for the rules).
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/usage_overview.dart';
import 'package:opencode_mobile/ui/screens/usage_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'lists_support.dart';
import 'models_support.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late WireServer wire;
  final fetched = <String, UsageStatistics>{};
  final family = CoverageFamily('oc2_usage', prefix: '');

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    wire = await WireServer.start();
    await withRealHttp(() async {
      final client = Api2Client.connect(
        baseUrl: wire.baseUrl,
        password: 'fixture',
      );
      final repository = Api2OperationsGateway(client: client);
      for (final variant in family.cases) {
        wire.handler = (r) => r.path == '/api/session/stats'
            ? {'data': variant['payload']}
            : null;
        try {
          fetched[variant['id'] as String] = await repository
              .loadUsageStatistics(
                UsageQuery(
                  from: 1,
                  to: DateTime.now().millisecondsSinceEpoch,
                  timezone: 'Asia/Dubai',
                ),
              );
        } on Object catch (error) {
          fail('${variant['id']}: $error');
        }
      }
      client.close();
    });
  });
  tearDownAll(() => wire.close());

  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 2 usage · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 2200);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final repository = ModelsRepository()..stats = fetched[id];
      final controller = await listsController(repository: repository);
      final screens = ListsScreens(
        tester,
        controller,
        GlobalKey(),
        'oc2-usage-$id',
      );
      final overview = UsageOverview(
        controller,
        clock: DateTime.now,
        timezoneLoader: () async => 'Asia/Dubai',
      );
      await screens.show(
        UsageScreen(controller: controller, overview: overview),
      );
      File(
        'build/coverage/oc2usage_$id.txt',
      ).writeAsStringSync(flat(screens.text));
      final problems = checkCase(family, variant, screens.text);
      await tester.pumpWidget(const SizedBox.shrink());
      overview.dispose();
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screens.text)}');
    });
  }
}
