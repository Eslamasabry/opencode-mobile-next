// Gallery (gate G4) for KitInkLayer (owner rule 2026-10-26: native Android
// press ripple; docs/ux-system/kit-v2.md top note): the parts that carry a
// ripple layer, at rest with motion on, on Android, at 412x915 and
// 1280x800 and at text size 2, dark and light. A ripple only exists while a finger is down, so at rest the layer
// draws nothing: these shots show the parts look exactly as before. The
// ripple itself is covered by test/kit/kit_ink_layer_test.dart.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_ink_layer_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

void _noop() {}

Widget _scene() => Padding(
  padding: const EdgeInsets.symmetric(horizontal: 16),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      const KitRow(title: 'Open project', onTap: _noop),
      const KitRow(title: 'Recent sessions', onTap: _noop),
      const SizedBox(height: 16),
      const KitButton.primary(label: 'Connect', onPressed: _noop),
      const SizedBox(height: 8),
      const KitButton.secondary(label: 'Not now', onPressed: _noop),
      const SizedBox(height: 16),
      Row(
        children: [
          const KitChip.action(label: 'Filter', onPressed: _noop),
          const SizedBox(width: 8),
          KitIconButton(
            icon: Icons.refresh,
            tooltip: 'Reload',
            onPressed: _noop,
          ),
        ],
      ),
    ],
  ),
);

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    testWidgets('ink layer at rest · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_ink_layer_rest', _phone, light: light),
        size: _phone,
        light: light,
        removeAnimations: false,
        child: _scene(),
      );
    });
    testWidgets('ink layer at rest · 1280x800 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_ink_layer_rest', _wide, light: light),
        size: _wide,
        light: light,
        removeAnimations: false,
        child: _scene(),
      );
    });
    testWidgets('ink layer at rest · text2 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_ink_layer_rest',
          _phone,
          light: light,
          text2: true,
        ),
        size: _phone,
        light: light,
        textScale: 2,
        removeAnimations: false,
        child: _scene(),
      );
    });
  }
}
