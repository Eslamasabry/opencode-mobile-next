// Coverage ratchet for what an OpenCode 1 server puts in a conversation
// through messages and parts (see paseo_coverage_support.dart for the rules).
// test/fixtures/coverage/oc1_parts_samples.json is generated from the OpenAPI
// contract by tool/coverage/oc_samples.mjs; each case is a message list the
// way the server sends it, parsed by the app's real MessageInfo/Part code and
// drawn by the real conversation screen with its folds opened.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';

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
  final family = CoverageFamily('oc1_parts', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 1 message · $id', (tester) async {
      final bundles = (variant['payload'] as List).cast<Map>();
      final messages = [
        for (final bundle in bundles)
          MessageWithParts(
            info: MessageInfo.fromJson(
              Map<String, dynamic>.from(bundle['info'] as Map),
            ),
            parts: [
              for (final part in (bundle['parts'] as List).cast<Map>())
                Part.fromJson(Map<String, dynamic>.from(part)),
            ],
          ),
      ];
      final boundary = GlobalKey();
      await pumpCoverageChat(tester, boundary, messages: messages);
      await openFolds(tester, rows: id != 'tool_task');
      final screen = screenText(tester).join('\n');
      final problems = checkCase(family, variant, screen);
      for (final hidden in (variant['hidden'] as List? ?? const [])) {
        if (flat(screen).contains(flat(hidden as String))) {
          problems.add('"$hidden" is on screen but must stay hidden');
        }
      }
      await writeCasePng(tester, boundary, 'oc1_$id');
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
