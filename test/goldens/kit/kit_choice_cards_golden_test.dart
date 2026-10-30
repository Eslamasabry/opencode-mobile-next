// Gallery (gate G4) for KitChoiceCards (docs/ux-system/kit-api/
// KitChoiceCards.md): the default at the phone and the wide size (also at
// text 2.0), and each declared state (working, disabled) at the phone size,
// dark and light.
//
// Regenerate deliberately:
//   TZ=UTC flutter test --update-goldens test/goldens/kit/kit_choice_cards_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit_gallery.dart';

const _phone = Size(412, 915);

void _noop(Object? _) {}

Widget _cards({String? busy, bool disabled = false}) => Padding(
  padding: const EdgeInsets.all(16),
  child: KitChoiceCards<String>(
    semanticsLabel: 'Where should your agent run?',
    choices: [
      KitChoice(
        value: 'phone',
        leading: const KitIcon(AppIconography.phone),
        title: 'On this phone',
        supporting: 'About 4 minutes · 208 MB download',
        enabled: !disabled,
        disabledReason: disabled ? 'Not on this device' : null,
      ),
      const KitChoice(
        value: 'computer',
        leading: KitIcon(AppIconography.computer),
        title: 'On my computer',
        supporting: 'Pair with a code, or type its address',
      ),
    ],
    selected: disabled ? 'computer' : 'phone',
    busy: busy,
    onSelected: _noop,
  ),
);

void main() {
  setUpAll(loadKitGalleryFonts);
  setUp(() => SharedPreferences.setMockInitialValues({}));

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('default · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_choice_cards_default', size, light: light),
          size: size,
          light: light,
          child: _cards(),
        );
      });
      testWidgets('default · text 2.0 · ${kitGallerySize(size)} · $mode', (
        tester,
      ) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_choice_cards_default',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          child: _cards(),
        );
      });
    }
    // Each declared state (KIT-12), named literally for the manifest.
    testWidgets('working · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_choice_cards_working', _phone, light: light),
        size: _phone,
        light: light,
        child: _cards(busy: 'phone'),
      );
    });
    testWidgets('disabled · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_choice_cards_disabled', _phone, light: light),
        size: _phone,
        light: light,
        child: _cards(disabled: true),
      );
    });
  }
}
