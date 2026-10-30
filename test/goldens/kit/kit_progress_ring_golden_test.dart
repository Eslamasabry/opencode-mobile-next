// Gallery (gate G4) for KitProgressRing (docs/ux-system/kit-api/
// KitProgressRing.md): a measured step, steps done of all, halted and
// finished, at the phone and the desktop size, and at 2x text.
//
// Regenerate deliberately:
//   TZ=UTC flutter test --update-goldens test/goldens/kit/kit_progress_ring_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

const _trail = [
  KitTrailMark.done,
  KitTrailMark.done,
  KitTrailMark.done,
  KitTrailMark.current,
  KitTrailMark.waiting,
];

final _states = <String, Widget Function()>{
  'measured': () => const KitProgressRing.measured(
    value: 0.64,
    caption: '41 of 64 MB',
    headline: 'Downloading OpenCode',
    semanticsLabel: 'Setup progress',
    trail: _trail,
  ),
  'steps': () => const KitProgressRing.steps(
    done: 3,
    of: 5,
    headline: 'Starting OpenCode',
    semanticsLabel: 'Setup progress',
    trail: _trail,
  ),
  'halted': () => const KitProgressRing.steps(
    done: 3,
    of: 5,
    headline: 'Setup is paused',
    semanticsLabel: 'Setup progress',
    trail: _trail,
    halted: true,
  ),
  'finished': () => const KitProgressRing.finished(
    headline: 'All set',
    semanticsLabel: 'Setup progress',
    trail: [
      KitTrailMark.done,
      KitTrailMark.done,
      KitTrailMark.done,
      KitTrailMark.done,
      KitTrailMark.done,
    ],
  ),
};

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final MapEntry(key: state, value: build) in _states.entries) {
      testWidgets('$state · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_progress_ring_$state',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: build(),
        );
      });
    }
    testWidgets('measured · 1280x800 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_progress_ring_measured',
          const Size(1280, 800),
          light: light,
        ),
        size: const Size(1280, 800),
        light: light,
        child: _states['measured']!(),
      );
    });
  }

  for (final size in const [Size(412, 915), Size(1280, 800)]) {
    testWidgets('measured · 2.0 text · ${kitGallerySize(size)} · dark', (
      tester,
    ) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_progress_ring_measured',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: _states['measured']!(),
      );
    });
  }
}
