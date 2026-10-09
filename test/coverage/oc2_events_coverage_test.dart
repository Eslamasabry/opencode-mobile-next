// Coverage ratchet for the events an OpenCode 2 server streams to a chat (see
// paseo_coverage_support.dart for the rules). Each case is a run of envelopes
// the way /api/event sends them, pushed through the app's real adapter
// (Api2EventAdapter) and event handler while the real conversation screen is
// open. Event types that never reach a conversation have one ledger line each
// (`eventTypes.<type>`), all "ignored: <reason>".
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';

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
  final family = CoverageFamily('oc2_events', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 2 events · $id', (tester) async {
      final events = (variant['payload'] as List).cast<Map>();
      final boundary = GlobalKey();
      // What the server would return when the app refetches the history: the
      // prompts it was sent (an inbox delivery makes the app refetch).
      final served = <MessageWithParts>[];
      final controller = await pumpCoverageChat(
        tester,
        boundary,
        messages: served,
        capabilities: api2ServerCapabilities,
      );
      final adapter = Api2EventAdapter();
      final now = DateTime.now().millisecondsSinceEpoch;
      for (final raw in events) {
        final json = _live(Map<String, dynamic>.from(raw), now);
        final data = json['data'] as Map;
        if (json['type'] == 'session.inbox.enqueued' &&
            (data['item'] as Map)['type'] == 'user') {
          served.add(
            MessageWithParts(
              info: MessageInfo(
                id: data['inboxID'] as String,
                sessionID: checkoutSessionID,
                role: 'user',
              ),
              parts: [
                Part(
                  type: 'text',
                  text:
                      ((data['item'] as Map)['payload'] as Map)['text']
                          as String,
                ),
              ],
            ),
          );
        }
        final envelope = Api2EventEnvelope.fromJson(json);
        for (final event in adapter.adapt(envelope)) {
          controller.handleEventForTesting(event);
        }
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
      await writeCasePng(tester, boundary, 'oc2ev_$id');
      await tester.pump(const Duration(seconds: 30));
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}

/// The envelope with recent times, so a live line reads seconds, not a month,
/// and a retry counts down to a moment ahead of now.
Map<String, dynamic> _live(Map<String, dynamic> envelope, int now) {
  final data = Map<String, dynamic>.from(envelope['data'] as Map);
  if (data['at'] is num) data['at'] = now + 20000;
  final status = data['status'];
  if (status is Map && status['next'] is num) {
    data['status'] = {
      ...Map<String, dynamic>.from(status),
      'next': now + 20000,
    };
  }
  return {...envelope, 'created': now - 20000, 'data': data};
}
