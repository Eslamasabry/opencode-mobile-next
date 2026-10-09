// Coverage ratchet for what an OpenCode 2 server puts in a conversation
// through messages (see paseo_coverage_support.dart for the rules). Each case
// is a message list the way GET /session/{id}/message and the durable log
// send it, parsed by the app's real Api2Message and gateway mapper and drawn
// by the real conversation screen with its folds opened.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
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
  final family = CoverageFamily('oc2_messages', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 2 message · $id', (tester) async {
      final raw = (variant['payload'] as List).cast<Map>();
      final now = DateTime.now().millisecondsSinceEpoch;
      final messages = mapApi2Messages(checkoutSessionID, [
        for (final (index, message) in raw.indexed)
          Api2Message.fromJson(
            _recent(message, now - (raw.length - index) * 20000),
          )!,
      ]);
      final boundary = GlobalKey();
      await pumpCoverageChat(
        tester,
        boundary,
        messages: messages,
        capabilities: api2ServerCapabilities,
      );
      final screen = await openEverything(tester, rows: id != 'tool_task');
      final problems = checkCase(family, variant, screen);
      await writeCasePng(tester, boundary, 'oc2msg_$id');
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}

/// The message with its creation time moved to [at], so a live line does not
/// read "43327 min"; every other time in it keeps its distance.
Map<String, dynamic> _recent(Map message, int at) {
  final copy = Map<String, dynamic>.from(message);
  final time = Map<String, dynamic>.from(copy['time'] as Map);
  final shift = at - (time['created'] as num).toInt();
  copy['time'] = {
    for (final entry in time.entries)
      entry.key: entry.value is num
          ? (entry.value as num).toInt() + shift
          : entry.value,
  };
  return copy;
}
