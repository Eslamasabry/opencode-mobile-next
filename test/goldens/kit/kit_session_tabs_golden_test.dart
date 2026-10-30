// Gallery (gate G4) for KitSessionTabs (a screen's open sessions as pills):
// at 412x915 and 1280x800, dark and light, and at 2.0 text.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_session_tabs_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _part() => Padding(
  padding: const EdgeInsets.symmetric(vertical: 16),
  child: KitSessionTabs(
    semanticsLabel: 'Open terminals',
    endedLabel: 'ended',
    tabs: [
      KitSessionTab(label: 'build-server', selected: true, onTap: () {}),
      KitSessionTab(label: 'Shell 2', selected: false, onTap: () {}),
      KitSessionTab(label: 'tests', selected: false, ended: true, onTap: () {}),
    ],
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
          name: kitGalleryName('kit_session_tabs_default', size, light: light),
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
          'kit_session_tabs_default',
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
