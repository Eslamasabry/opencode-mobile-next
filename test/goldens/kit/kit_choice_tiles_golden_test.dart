// Gallery (gate G4) for KitChoiceTiles (docs/ux-system/kit-api/
// KitChoiceTiles.md): the default at the phone and the wide size (also at
// text 2.0), and each declared state (working, disabled) at the phone size,
// dark and light.
//
// Regenerate deliberately:
//   TZ=UTC flutter test --update-goldens test/goldens/kit/kit_choice_tiles_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_choice_list.dart';
import 'package:opencode_mobile/ui/kit/kit_choice_tiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit_gallery.dart';

const _phone = Size(412, 915);

const _choices = [
  KitChoice(value: 'sonnet', title: 'Default', supporting: 'Claude Sonnet 5.5'),
  KitChoice(value: 'sol', title: 'Recently used', supporting: 'GPT-6 Sol'),
  KitChoice(
    value: 'pickle',
    title: 'Cheapest',
    supporting: 'Big Pickle · Free',
  ),
];

void _noop(Object? _) {}

Widget _tiles({String? busy, bool disabled = false}) => Padding(
  padding: const EdgeInsets.all(16),
  child: KitChoiceTiles<String>(
    semanticsLabel: 'Model',
    choices: disabled
        ? [
            for (final choice in _choices)
              KitChoice(
                value: choice.value,
                title: choice.title,
                supporting: choice.supporting,
                enabled: false,
                disabledReason: 'Saving the choice…',
              ),
          ]
        : _choices,
    selected: 'sonnet',
    busy: busy,
    onSelected: _noop,
    more: KitChoiceMore(label: 'All models (9)', onTap: () {}),
  ),
);

void main() {
  setUpAll(loadKitGalleryFonts);
  setUp(() => SharedPreferences.setMockInitialValues({}));

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    // kitGalleryScaledSizes is the phone and the wide size.
    for (final size in kitGalleryScaledSizes) {
      testWidgets('default · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_choice_tiles_default', size, light: light),
          size: size,
          light: light,
          child: _tiles(),
        );
      });
      testWidgets('default · text 2.0 · ${kitGallerySize(size)} · $mode', (
        tester,
      ) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_choice_tiles_default',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          child: _tiles(),
        );
      });
    }
    // Each declared state (KIT-12), named literally for the manifest.
    testWidgets('working · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_choice_tiles_working', _phone, light: light),
        size: _phone,
        light: light,
        child: _tiles(busy: 'pickle'),
      );
    });
    testWidgets('disabled · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_choice_tiles_disabled', _phone, light: light),
        size: _phone,
        light: light,
        child: _tiles(disabled: true),
      );
    });
  }
}
