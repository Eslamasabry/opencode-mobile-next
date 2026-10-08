// Golden renders of the first-run welcome and a new chat's composer in the
// five FG5 languages (docs/l10n/fg5-key-set.md): Japanese, Simplified
// Chinese, Spanish, Brazilian Portuguese and Russian. 412x915 at device
// pixel ratio 2 (so the kana and Han strokes read), light, with the app's
// real fonts; Japanese and Chinese fall back to the Noto Sans CJK subsets in
// test/fixtures/fonts, as an Android device falls back to its own. The
// welcome runs on a fake empty profile store, so these are not device proof.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/languages_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fg5_locale_screens.dart';
import 'kit/kit_gallery.dart' show loadKitGalleryFonts;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadFg5CjkFonts();
  });

  for (final locale in fg5Locales) {
    final token = fg5Token(locale);

    testWidgets('languages_welcome_${token}_light', (tester) async {
      final boundary = GlobalKey();
      debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
      final done = await mountFg5Welcome(
        tester,
        locale: locale,
        devicePixelRatio: 2,
        boundary: boundary,
      );
      try {
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('languages_welcome_${token}_light.png'),
        );
      } finally {
        await done();
        debugDefaultTargetPlatformOverride = null;
      }
    });

    testWidgets('languages_composer_${token}_light', (tester) async {
      final boundary = GlobalKey();
      debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
      final done = await mountFg5Composer(
        tester,
        locale: locale,
        devicePixelRatio: 2,
        boundary: boundary,
      );
      try {
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('languages_composer_${token}_light.png'),
        );
      } finally {
        await done();
        debugDefaultTargetPlatformOverride = null;
      }
    });
  }
}
