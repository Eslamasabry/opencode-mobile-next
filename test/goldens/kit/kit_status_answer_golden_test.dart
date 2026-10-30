// Gallery (gate G4) for KitStatusAnswer (a status page's one big answer): at
// 412x915 and 1280x800, dark and light, and at 2.0 text, each tone.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_status_answer_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _part() => Padding(
  padding: const EdgeInsets.all(24),
  child: Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      const KitStatusAnswer(
        tone: KitAnswerTone.ok,
        answer: 'Everything is running',
        line: 'Uses about 410 MB of memory',
      ),
      const SizedBox(height: 32),
      KitStatusAnswer(
        tone: KitAnswerTone.paused,
        answer: 'Android closed the server',
        line: 'It restarts by itself in about 40 s',
        action: KitAction(label: "Use this phone's server", onPressed: () {}),
      ),
      const SizedBox(height: 32),
      const KitStatusAnswer(
        tone: KitAnswerTone.needsYou,
        answer: "The server didn't start",
      ),
      const SizedBox(height: 32),
      const KitStatusAnswer(
        tone: KitAnswerTone.off,
        answer: 'The server is off',
      ),
    ],
  ),
);

/// The calm scales with no mark (KitStateView.calm's words).
Widget _calm() => const Padding(
  padding: EdgeInsets.all(24),
  child: Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      KitStatusAnswer(
        answer: 'No conversations yet',
        scale: KitAnswerScale.calm,
        line: 'Say what you want to build, and an agent gets to work.',
      ),
      SizedBox(height: 32),
      KitStatusAnswer(
        answer: 'Nothing matches “deploy staging”',
        scale: KitAnswerScale.compact,
      ),
    ],
  ),
);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    testWidgets('calm · phone · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_status_answer_calm',
          kitGalleryScaledSizes.first,
          light: light,
        ),
        size: kitGalleryScaledSizes.first,
        light: light,
        child: _calm(),
      );
    });
  }

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('default · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_status_answer_default', size, light: light),
          size: size,
          light: light,
          child: _part(),
        );
      });
    }
  }

  for (final size in kitGalleryScaledSizes) {
    testWidgets('default · 2.0 text · ${kitGallerySize(size)} · dark', (
      tester,
    ) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_status_answer_default',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: _part(),
      );
    });
  }
}
