// Coverage ratchet for what an OpenCode 1 server asks the person: permission
// requests and questions (see paseo_coverage_support.dart for the rules).
// Each case is the request the way the server sends it, parsed by the app's
// real PermissionRequest / PendingQuestion code and drawn by the real
// conversation screen; Details is opened.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

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
  final family = CoverageFamily('oc1_requests', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 1 request · $id', (tester) async {
      final request = Map<String, dynamic>.from(variant['payload'] as Map);
      final isQuestion = variant['kind'] == 'question';
      final boundary = GlobalKey();
      await pumpCoverageChat(
        tester,
        boundary,
        messages: const [],
        setUp: (controller) {
          if (isQuestion) {
            final question = PendingQuestion.fromJson(request);
            controller.questions = {question.id: question};
          } else {
            final permission = PermissionRequest.fromJson(request);
            controller.permissions = {permission.id: permission};
          }
        },
      );
      final primary = screenText(tester).join('\n');
      await writeCasePng(tester, boundary, 'oc1req_$id');
      // The card, its Details and the answer sheet of a question.
      final opened = await openRequestCards(
        tester,
        boundary: boundary,
        name: 'oc1req_$id',
      );
      final screen = '$primary\n$opened';
      final problems = checkCase(
        family,
        variant,
        screen,
        // The answer sheet is how a question is answered, not a fold.
        primaryText: screen,
      );
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
