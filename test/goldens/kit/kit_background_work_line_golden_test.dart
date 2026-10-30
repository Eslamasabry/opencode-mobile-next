// Gallery (gate G4) for KitBackgroundWorkLine
// (docs/ux-system/kit-api/KitBackgroundWorkLine.md): one task and several
// (folded), each under the end of a reply at 412x915 in dark and light; the
// one-task line at 1280x800 and at 2.0 text. Phone and one wide size only,
// English only (owner decision 2026-09-27).
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_background_work_line_golden_test.dart
// and look at every changed image before committing it.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_background_work_line.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

import 'kit_gallery.dart';

/// The end of a reply and the line under it.
Widget _scene(KitBackgroundWorkLine line) => Padding(
  padding: const EdgeInsetsDirectional.symmetric(horizontal: 16),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      const KitText(
        "The owner build is compiling. Once it's done I'll install it and "
        'check your Home screen for the new shelves.',
      ),
      const SizedBox(height: 8),
      line,
    ],
  ),
);

KitBackgroundWorkLine _line(int count) => KitBackgroundWorkLine(
  tasks: [
    KitBackgroundTask(
      name: 'Build the owner APK',
      since: clock.now().subtract(const Duration(minutes: 2)),
    ),
    if (count > 1)
      KitBackgroundTask(
        name: 'flutter test',
        since: clock.now().subtract(const Duration(minutes: 14)),
      ),
  ],
  onOpenTask: (_) {},
);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final (state, count) in [('one', 1), ('many', 2)]) {
      testWidgets('kit_background_work_line $state · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_background_work_line_$state',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: _scene(_line(count)),
        );
      });
    }

    testWidgets('kit_background_work_line one · 1280x800 · $mode', (
      tester,
    ) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_background_work_line_one',
          const Size(1280, 800),
          light: light,
        ),
        size: const Size(1280, 800),
        light: light,
        child: _scene(_line(1)),
      );
    });

    for (final size in kitGalleryScaledSizes) {
      final at = kitGallerySize(size);
      testWidgets('kit_background_work_line one · 2.0 text · $at · $mode', (
        tester,
      ) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_background_work_line_one',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          child: _scene(_line(1)),
        );
      });
    }
  }
}
