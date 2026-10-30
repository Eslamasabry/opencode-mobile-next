// Gallery (gate G4) for KitWordText: a long step line cut after a whole
// word at one and two lines, and an Arabic line, beside a line that fits.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_word_text_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

const _words =
    'Ran the checkout tests on a clean cache and retry when the first run '
    'fails because the price refresh races the coupon check';

Widget _lines({bool ar = false}) => Padding(
  padding: const EdgeInsets.all(20),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      const KitWordText('Read 3 files', maxLines: 1),
      const SizedBox(height: 16),
      KitWordText(
        ar
            ? 'شغّل اختبارات الدفع على ذاكرة نظيفة وأعد المحاولة عندما يفشل '
                  'التشغيل الأول لأن تحديث السعر يسبق فحص القسيمة'
            : _words,
        maxLines: 1,
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      ),
      const SizedBox(height: 16),
      const KitWordText(
        _words,
        maxLines: 2,
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      ),
    ],
  ),
);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGallerySizes) {
      testWidgets('cut · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_word_text_cut', size, light: light),
          size: size,
          light: light,
          child: _lines(),
        );
      });
    }
    testWidgets('cut · Arabic · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_word_text_cut',
          const Size(412, 915),
          light: light,
          ar: true,
        ),
        size: const Size(412, 915),
        light: light,
        locale: const Locale('ar'),
        child: _lines(ar: true),
      );
    });
    for (final size in kitGalleryScaledSizes) {
      testWidgets('cut · 2.0 text · ${kitGallerySize(size)} · $mode', (
        tester,
      ) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_word_text_cut',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          child: _lines(),
        );
      });
    }
  }
}
