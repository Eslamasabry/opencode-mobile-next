// Gallery (gate G4) for KitMiniTable: a header and rows that scroll inside their panel, at 412x915 and 1280x800, dark and
// light, at 2.0 text, and once right to left.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_mini_table_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _pad(Widget child) =>
    Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: child);

final _states = <String, Widget Function()>{
  'default': () => _pad(
    const KitMiniTable(
      columns: ['File', 'Added', 'Removed', 'Status'],
      rows: [
        KitMiniTableRow(['lib/main.dart', '12', '3', 'Changed']),
        KitMiniTableRow(['lib/ui/kit/kit.dart', '5', '0', 'Changed']),
        KitMiniTableRow(['test/kit/kit_chart_test.dart', '140', '0', 'New']),
        KitMiniTableRow(['docs/old-plan.md', '0', '86', 'Removed']),
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
          name: kitGalleryName('kit_mini_table_default', size, light: light),
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
          'kit_mini_table_default',
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
        'kit_mini_table_default',
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
