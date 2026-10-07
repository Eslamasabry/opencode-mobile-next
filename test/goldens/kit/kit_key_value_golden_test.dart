// Gallery (gate G4) for KitKeyValue: label and value rows, at 412x915 and 1280x800, dark and
// light, at 2.0 text, and once right to left.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_key_value_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _pad(Widget child) =>
    Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: child);

final _states = <String, Widget Function()>{
  'default': () => _pad(
    const KitKeyValue(
      rows: [
        KitKeyValueRow(label: 'Duration', value: '4 min 12 s'),
        KitKeyValueRow(label: 'Files changed', value: '7'),
        KitKeyValueRow(label: 'Cost', value: '\$0.42'),
        KitKeyValueRow(
          label: 'Branch',
          value: 'feature/a-long-branch-name-that-wraps-and-is-never-cut',
        ),
      ],
    ),
  ),
};

const _phone = Size(412, 915);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('default · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_key_value_default', size, light: light),
          size: size,
          light: light,
          child: _states['default']!(),
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
          'kit_key_value_default',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: _states['default']!(),
      );
    });
  }

  testWidgets('default · right to left · dark', (tester) async {
    await kitGalleryPart(
      tester,
      name: kitGalleryName(
        'kit_key_value_default',
        _phone,
        light: false,
        ar: true,
      ),
      size: _phone,
      light: false,
      locale: const Locale('ar'),
      child: _states['default']!(),
    );
  });
}
