// Gallery (gate G4) for KitStartBox (the new conversation field): at 412x915 and 1280x800, dark and
// light, and at 2.0 text.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_start_box_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _part({String text = ''}) => Padding(
  padding: const EdgeInsets.all(16),
  child: KitStartBox(
    controller: TextEditingController(text: text),
    focusNode: FocusNode(),
    question: 'What would you like to build?',
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
          name: kitGalleryName('kit_start_box_default', size, light: light),
          size: size,
          light: light,
          child: _part(),
        );
      });
    }
  }

  for (final light in [false, true]) {
    testWidgets('typed · 412x915 · ${light ? 'light' : 'dark'}', (
      tester,
    ) async {
      await kitGalleryPart(
        tester,
        name: 'kit_start_box_typed_${light ? 'light' : 'dark'}',
        size: kitGalleryScaledSizes.first,
        light: light,
        child: _part(text: 'A neon brick breaker game for the phone'),
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
          'kit_start_box_default',
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
