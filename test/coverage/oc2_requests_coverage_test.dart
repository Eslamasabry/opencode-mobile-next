// Coverage ratchet for what an OpenCode 2 server asks the person: permission
// requests and forms (see paseo_coverage_support.dart for the rules). Each
// case is the request the way the server sends it, parsed by the app's real
// OpenCode 2 models and mappers and drawn by the real conversation screen;
// Details and the form sheet are opened.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/api2/models.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_chat_harness.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  final family = CoverageFamily('oc2_requests', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 2 request · $id', (tester) async {
      final request = Map<String, dynamic>.from(variant['payload'] as Map);
      final isForm = variant['kind'] == 'form';
      final form = isForm ? Api2FormInfo.fromJson(request)! : null;
      final boundary = GlobalKey();
      final controller = await pumpCoverageChat(
        tester,
        boundary,
        messages: const [],
        capabilities: api2ServerCapabilities,
        forms: [?form],
        setUp: (controller) {
          if (!isForm) {
            final permission = mapApi2PermissionRequest(
              Api2PermissionRequest.fromJson(request)!,
            );
            controller.permissions = {permission.id: permission};
          }
        },
      );
      if (isForm) {
        controller.handleEventForTesting(
          EventEnvelope(type: 'form.v2.created', properties: {'form': request}),
        );
        await frames(tester);
      }
      final primary = screenText(tester).join('\n');
      await writeCasePng(tester, boundary, 'oc2req_$id');
      final opened = await openRequestCards(
        tester,
        boundary: boundary,
        name: 'oc2req_$id',
      );
      final screen = '$primary\n$opened';
      final problems = checkCase(family, variant, screen, primaryText: screen);
      await tester.pump(const Duration(seconds: 30));
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
