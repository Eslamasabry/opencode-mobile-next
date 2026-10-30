// Gallery (gate G4) for KitStepSlider (a slider that rests on steps):
// default and disabled, 412x915 and 1280x800, dark and light, and at 2.0 text.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_step_slider_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _part({required bool enabled, String selected = 'c'}) => Padding(
  padding: const EdgeInsets.all(16),
  child: Align(
    alignment: Alignment.topCenter,
    child: KitStepSlider<String>(
      semanticsLabel: 'Text size',
      steps: const [
        KitSliderStep(value: 'a', label: 'Smallest'),
        KitSliderStep(value: 'b', label: 'Small'),
        KitSliderStep(value: 'c', label: 'Default'),
        KitSliderStep(value: 'd', label: 'Large'),
      ],
      selected: selected,
      onChanged: enabled ? (_) {} : null,
    ),
  ),
);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('default · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_step_slider_default', size, light: light),
          size: size,
          light: light,
          child: _part(enabled: true),
        );
      });
      if (size.width != 412) {
        testWidgets('disabled · ${kitGallerySize(size)} · $mode', (
          tester,
        ) async {
          await kitGalleryPart(
            tester,
            name: kitGalleryName(
              'kit_step_slider_disabled',
              size,
              light: light,
            ),
            size: size,
            light: light,
            child: _part(enabled: false, selected: 'b'),
          );
        });
      }
    }
  }

  // disabled is KitStepSlider's declared state: gate G4 (stateScenes) reads
  // the 412x915 names from a literal `kitGalleryShot` call.
  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    testWidgets('disabled · 412x915 · $mode', (tester) async {
      await kitGalleryShot(
        tester,
        name: kitGalleryName(
          'kit_step_slider_disabled',
          const Size(412, 915),
          light: light,
        ),
        size: const Size(412, 915),
        light: light,
        open: (context) => Navigator.of(context).push<void>(
          PageRouteBuilder<void>(
            transitionDuration: Duration.zero,
            reverseTransitionDuration: Duration.zero,
            pageBuilder: (_, _, _) =>
                Scaffold(body: _part(enabled: false, selected: 'b')),
          ),
        ),
      );
    });
  }

  for (final size in kitGalleryScaledSizes) {
    testWidgets('default · 2.0 text · ${kitGallerySize(size)} · dark', (
      tester,
    ) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_step_slider_default',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: _part(enabled: true),
      );
    });
  }
}
