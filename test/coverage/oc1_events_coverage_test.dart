// Coverage ratchet for the events an OpenCode 1 server streams to a chat (see
// paseo_coverage_support.dart for the rules). Each case is a short run of
// events fed to the app's real event handler while the real conversation
// screen is open. Event types that never reach a conversation have one ledger
// line each (`eventTypes.<type>`), all "ignored: <reason>".
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
  final family = CoverageFamily('oc1_events', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 1 events · $id', (tester) async {
      final events = (variant['payload'] as List).cast<Map>();
      final boundary = GlobalKey();
      final controller = await pumpCoverageChat(
        tester,
        boundary,
        messages: const [],
      );
      for (final event in events) {
        final properties = Map<String, dynamic>.from(
          event['properties'] as Map,
        );
        // A retry counts down to a moment ahead of now.
        final status = properties['status'];
        if (status is Map && status['type'] == 'retry') {
          properties['status'] = <String, dynamic>{
            ...Map<String, dynamic>.from(status),
            'next': DateTime.now().millisecondsSinceEpoch + 20000,
          };
        }
        // Recent times, so a live line does not read "43327 min".
        final info = properties['info'];
        if (info is Map && info['time'] is Map) {
          properties['info'] = <String, dynamic>{
            ...Map<String, dynamic>.from(info),
            'time': <String, dynamic>{
              ...Map<String, dynamic>.from(info['time'] as Map),
              'created': DateTime.now().millisecondsSinceEpoch - 20000,
            },
          };
        }
        controller.handleEventForTesting(
          EventEnvelope(type: event['type'] as String, properties: properties),
        );
        await frames(tester, 2);
      }
      await frames(tester);
      final screen = await openEverything(tester);
      final problems = checkCase(family, variant, screen);
      for (final hidden in (variant['hidden'] as List? ?? const [])) {
        if (flat(screen).contains(flat(hidden as String))) {
          problems.add('"$hidden" is on screen but must stay hidden');
        }
      }
      await writeCasePng(tester, boundary, 'oc1ev_$id');
      // A request answered elsewhere leaves a short-lived timer behind.
      await tester.pump(const Duration(seconds: 30));
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
