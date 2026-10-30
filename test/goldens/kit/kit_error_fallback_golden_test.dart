// Gallery (gate G4) for KitErrorFallback: what a part shows in place of
// itself when it fails to build, on the theme's ground.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_error_fallback_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('error · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_error_fallback_error', size, light: light),
          size: size,
          light: light,
          child: const KitErrorFallback(),
        );
      });
    }
    testWidgets('error · 2.0 text · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_error_fallback_error',
          kitGalleryScaledSizes.first,
          light: light,
          text2: true,
        ),
        size: kitGalleryScaledSizes.first,
        light: light,
        textScale: 2,
        child: const KitErrorFallback(),
      );
    });
  }
}
