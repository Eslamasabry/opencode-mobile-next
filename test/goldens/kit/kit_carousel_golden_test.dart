// Gallery (gate G4) for KitCarousel (cards to swipe through):
// five cards (dots) and thirty cards (a bar), 412x915 and 1280x800, dark and light, and at 2.0 text.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_carousel_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

import 'kit_gallery.dart';

Widget _part(int count) => Padding(
  padding: const EdgeInsets.symmetric(vertical: 16),
  child: Align(
    alignment: Alignment.topCenter,
    child: KitCarousel(
      semanticsLabel: 'Themes',
      itemCount: count,
      itemHeight: KitOptionCard.themeHeight,
      initialIndex: 1,
      itemBuilder: (context, i) {
        final id = ThemePackId.values[i % ThemePackId.values.length];
        return KitOptionCard.theme(
          roles: themePack(id).dark.themeRoles,
          label: themePackLabels[id]!,
          selected: i == 1,
          onPressed: () {},
        );
      },
    ),
  ),
);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('five · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_carousel_five', size, light: light),
          size: size,
          light: light,
          child: _part(5),
        );
      });
      testWidgets('many · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_carousel_many', size, light: light),
          size: size,
          light: light,
          child: _part(30),
        );
      });
    }
  }

  for (final size in kitGalleryScaledSizes) {
    testWidgets('five · 2.0 text · ${kitGallerySize(size)} · dark', (
      tester,
    ) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_carousel_five',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: _part(5),
      );
    });
  }
}
