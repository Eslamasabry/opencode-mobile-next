// Gallery (gate G4) for KitStatusPill (the tonal server pill): at 412x915 and 1280x800, dark and
// light, and at 2.0 text.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_status_pill_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart' show AppStatusTone;
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _part() => Wrap(
  spacing: 8,
  runSpacing: 8,
  children: [
    KitStatusPill(
      label: 'This phone',
      semanticsLabel: 'This phone, Connected, Switch server',
      tone: AppStatusTone.ok,
      onPressed: () {},
    ),
    KitStatusPill(
      label: 'Office workstation',
      semanticsLabel: 'Office workstation, Offline, Switch server',
      tone: AppStatusTone.failure,
      word: 'Offline',
      onPressed: () {},
    ),
    KitStatusPill(
      label: 'Laptop',
      semanticsLabel: 'Laptop, Connecting, Switch server',
      tone: AppStatusTone.progress,
      word: 'Connecting…',
      onPressed: () {},
    ),
  ],
);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('default · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_status_pill_default', size, light: light),
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
          'kit_status_pill_default',
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
