// Gallery (gate G4) for KitStreamCaret: the soft accent caret after the
// words of a reply still being written.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_stream_caret_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

const _words = Text.rich(
  TextSpan(
    text: 'Reading the settings page and tidying',
    children: [WidgetSpan(child: KitStreamCaret())],
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
          name: kitGalleryName('kit_stream_caret_default', size, light: light),
          size: size,
          light: light,
          child: _words,
        );
      });
    }
    testWidgets('default · 2.0 text · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_stream_caret_default',
          kitGalleryScaledSizes.first,
          light: light,
          text2: true,
        ),
        size: kitGalleryScaledSizes.first,
        light: light,
        textScale: 2,
        child: _words,
      );
    });
  }
}
