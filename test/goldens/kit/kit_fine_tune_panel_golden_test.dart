// Gallery (gate G4) for KitFineTunePanel (Settings › Appearance ›
// Fine-tune): default (two knobs changed, so the reset shows) and disabled
// (Saver's one line), at 412x915 and 1280x800, dark and light, and at 2.0
// text.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_fine_tune_panel_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

List<KitSegment<T>> _segments<T extends Enum>(Map<T, String> labels) => [
  for (final MapEntry(key: value, value: label) in labels.entries)
    KitSegment(value: value, label: label),
];

Widget _part({bool disabled = false}) => Padding(
  padding: const EdgeInsets.all(16),
  child: KitFineTunePanel(
    resetLabel: 'Reset to Solid defaults',
    onReset: () {},
    disabledReason: disabled
        ? 'Saver keeps surfaces solid, so Fine-tune waits until Saver is off.'
        : null,
    knobs: [
      KitFineTuneKnob<KitCorners>(
        title: 'Corners',
        defaultNote: 'Default: Round',
        selected: KitCorners.sharp,
        segments: _segments({
          KitCorners.round: 'Round',
          KitCorners.soft: 'Soft',
          KitCorners.sharp: 'Sharp',
        }),
        onChanged: (_) {},
      ),
      KitFineTuneKnob<KitDepth>(
        title: 'Depth',
        hint: 'Raised lifts cards off the page.',
        defaultNote: 'Default: Flat',
        selected: KitDepth.flat,
        segments: _segments({KitDepth.flat: 'Flat', KitDepth.raised: 'Raised'}),
        onChanged: (_) {},
      ),
      KitFineTuneKnob<KitDensity>(
        title: 'Density',
        hint: 'Compact fits more on the screen.',
        defaultNote: 'Default: Comfortable',
        selected: KitDensity.compact,
        segments: _segments({
          KitDensity.comfortable: 'Comfortable',
          KitDensity.compact: 'Compact',
        }),
        onChanged: (_) {},
      ),
      KitFineTuneKnob<KitEdges>(
        title: 'Edges',
        hint: 'A thin line around cards.',
        defaultNote: 'Default: Hairline',
        selected: KitEdges.hairline,
        segments: _segments({
          KitEdges.hairline: 'Hairline',
          KitEdges.none: 'None',
        }),
        disabledReason: 'High contrast keeps cards flat with edges.',
        onChanged: null,
      ),
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
          name: kitGalleryName(
            'kit_fine_tune_panel_default',
            size,
            light: light,
          ),
          size: size,
          light: light,
          child: _part(),
        );
      });
      testWidgets('disabled · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_fine_tune_panel_disabled',
            size,
            light: light,
          ),
          size: size,
          light: light,
          child: _part(disabled: true),
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
          'kit_fine_tune_panel_default',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: SingleChildScrollView(child: _part()),
      );
    });
  }
}
