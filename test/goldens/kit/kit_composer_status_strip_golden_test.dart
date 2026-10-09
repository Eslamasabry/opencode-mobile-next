// Gallery for KitComposerStatusStrip in the overlap case: a pause chip beside
// a long model label at 360 dp and 2.0 text (a Claude Code chat opened while
// its helper starts). Dark, English and Arabic (RTL).
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_composer_status_strip_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _strip() => KitComposerStatusStrip(
  chips: const [
    KitChip(icon: AppIconography.pause, label: 'Auto-approve paused'),
  ],
  model: KitComposerChips.model(
    label: 'Loading conversation selection…',
    onPressed: () {},
  ),
);

void main() {
  setUpAll(loadKitGalleryFonts);

  const size = Size(360, 800);
  for (final ar in [false, true]) {
    testWidgets(
      'overlap case · 360 dp · 2.0 text · ${ar ? 'ar' : 'en'} · dark',
      (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_composerstatusstrip_wrapped',
            size,
            light: false,
            ar: ar,
            text2: true,
          ),
          size: size,
          light: false,
          textScale: 2,
          locale: Locale(ar ? 'ar' : 'en'),
          child: _strip(),
        );
      },
    );
  }
}
