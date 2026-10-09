// Coverage ratchet for the Paseo timeline items that are not tool steps:
// user and assistant messages, thoughts, todo lists, errors, notifications,
// compaction and plugin items (see paseo_coverage_support.dart for the
// rules). Each case is one timeline item, mapped by the app's real
// paseoTimelineMessages and drawn by the real conversation screen with its
// folds opened.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/paseo/mappers.dart';

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
  final family = CoverageFamily('items');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('paseo item · $id', (tester) async {
      final item = (variant['payload'] as Map).cast<String, dynamic>();
      final now = DateTime.now().toUtc();
      String stamp(int secondsAgo) =>
          now.subtract(Duration(seconds: secondsAgo)).toIso8601String();
      final entries = [
        if (item['type'] != 'user_message')
          {
            'seqStart': 1,
            'provider': 'claude',
            'timestamp': stamp(90),
            'item': {
              'type': 'user_message',
              'messageId': 'prompt-1',
              'text': 'Add a retry button to the upload screen',
            },
          },
        {
          'seqStart': 2,
          'provider': 'claude',
          'timestamp': stamp(60),
          'item': item,
        },
        // A turn ends with an answer, unless the item is the answer or the
        // failure that ended it.
        if (!const {
          'user_message',
          'assistant_message',
          'error',
        }.contains(item['type']))
          {
            'seqStart': 3,
            'provider': 'claude',
            'timestamp': stamp(30),
            'item': {
              'type': 'assistant_message',
              'messageId': 'answer-1',
              'text': 'Done.',
            },
          },
      ];
      final messages = paseoTimelineMessages(checkoutSessionID, {
        'entries': entries,
      }, busy: false);
      final boundary = GlobalKey();
      await pumpCoverageChat(tester, boundary, messages: messages);
      await openFolds(tester);
      final screen = screenText(tester).join('\n');
      final problems = checkCase(family, variant, screen);
      await writeCasePng(tester, boundary, 'items_$id');
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
