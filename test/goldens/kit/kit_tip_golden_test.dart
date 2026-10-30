// Gallery (gate G4) for KitTip (the teaching system's tip, anchored above a
// control): at 412x915 and 1280x800, dark and light, and at 2.0 text.
//
// Regenerate deliberately:
//   TZ=UTC flutter test --update-goldens test/goldens/kit/kit_tip_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _part() => Padding(
  padding: const EdgeInsets.only(top: 320, left: 24, right: 24, bottom: 24),
  child: Align(
    alignment: AlignmentDirectional.centerEnd,
    child: KitTip(
      message: 'Hold to stop after this step',
      visible: true,
      onDismissed: () {},
      child: KitIconButton(
        icon: Icons.stop_circle_outlined,
        tooltip: 'Stop',
        onPressed: () {},
      ),
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
          name: kitGalleryName('kit_tip_default', size, light: light),
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
          'kit_tip_default',
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
