// Gallery (gate G4) for KitPageHeading: the Files page's heading, the large
// title with the project as a switcher line under it, and the plain line a
// folder shows ("neon-breaker / src").
//
// Phone 412x915 and one wide size (1280x800), light and dark, plus the
// phone at text 2x (owner decision 2026-09-27: no Arabic galleries).
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_page_heading_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_page_heading.dart';

import 'kit_gallery.dart';

void _noop() {}

Widget _switcher() => const Column(
  crossAxisAlignment: CrossAxisAlignment.stretch,
  children: [
    KitPageHeading(
      title: 'Files',
      line: 'neon-breaker',
      onLineTap: _noop,
      lineTapLabel: 'Project neon-breaker. Switch project',
    ),
  ],
);

Widget _plain() => const Column(
  crossAxisAlignment: CrossAxisAlignment.stretch,
  children: [KitPageHeading(title: 'levels', line: 'neon-breaker / src')],
);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in const [Size(412, 915), Size(1280, 800)]) {
      testWidgets('switcher line · ${kitGallerySize(size)} · $mode', (
        tester,
      ) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_page_heading_switcher', size, light: light),
          size: size,
          light: light,
          child: _switcher(),
        );
      });
    }
    testWidgets('plain line · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_page_heading_disabled',
          const Size(412, 915),
          light: light,
        ),
        size: const Size(412, 915),
        light: light,
        child: _plain(),
      );
    });
    testWidgets('switcher line · text 2x · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_page_heading_switcher',
          const Size(412, 915),
          light: light,
          text2: true,
        ),
        size: const Size(412, 915),
        light: light,
        textScale: 2,
        child: _switcher(),
      );
    });
  }
}
