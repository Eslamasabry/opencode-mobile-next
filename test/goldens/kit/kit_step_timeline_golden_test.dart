// Gallery (gate G4) for KitStepTimeline (docs/ux-system/kit-api/
// KitStepTimeline.md): a turn's work as one quiet line that opens in place to
// a timeline. Four scenes at 412x915 in dark and light, each over a
// transcript-like ground with a line of prose above and below: the line
// folded, opened (a file write with its preview card), running and folded
// (the live mark and words), running and opened (the step in progress last).
// The opened scene again in Arabic (the rail on the right), at 1280x800, and
// at 2.0 text.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_step_timeline_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

import 'kit_gallery.dart';
import 'kit_step_timeline_scenes.dart';

/// A reply's prose, the work under it, and the reply's next line.
Widget _scene(StepWords t, String scene) => Padding(
  padding: const EdgeInsetsDirectional.symmetric(horizontal: 16),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      KitText(
        t(
          'I will add a home screen and check it with a test.',
          'سأضيف شاشة رئيسية وأتحقق منها باختبار.',
        ),
      ),
      const SizedBox(height: 8),
      stepTimelineWork(t, scene),
      const SizedBox(height: 8),
      KitText(
        t(
          'The screen is in and the route points to it.',
          'الشاشة جاهزة والمسار يشير إليها.',
        ),
      ),
    ],
  ),
);

String _en(String en, String ar) => en;
String _ar(String en, String ar) => ar;

const _scenes = <String, String>{
  'collapsed': 'collapsed',
  'expanded': 'expanded',
  'running': 'running',
  'running_open': 'running-open',
};

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final entry in _scenes.entries) {
      testWidgets('kit_step_timeline ${entry.key} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_step_timeline_${entry.key}',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: _scene(_en, entry.value),
        );
      });
    }

    testWidgets('kit_step_timeline expanded · Arabic · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_step_timeline_expanded',
          const Size(412, 915),
          light: light,
          ar: true,
        ),
        size: const Size(412, 915),
        light: light,
        locale: const Locale('ar'),
        child: _scene(_ar, 'expanded'),
      );
    });

    testWidgets('kit_step_timeline expanded · 1280x800 · $mode', (
      tester,
    ) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_step_timeline_expanded',
          const Size(1280, 800),
          light: light,
        ),
        size: const Size(1280, 800),
        light: light,
        child: _scene(_en, 'expanded'),
      );
    });

    for (final size in kitGalleryScaledSizes) {
      final at = kitGallerySize(size);
      testWidgets('kit_step_timeline expanded · 2.0 text · $at · $mode', (
        tester,
      ) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_step_timeline_expanded',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          child: _scene(_en, 'expanded'),
        );
      });
    }
  }
}
