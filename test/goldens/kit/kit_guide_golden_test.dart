// Gallery (gate G4) for KitGuide, docs/ux-system/kit-api/KitGuide.md: the
// start view with the guide's dot, a check and a held chip; a question open
// with its "Changes" line and the Next bar filled; and the returning view
// with no bar, and no preview. At 412x915 and 1280x800, in light
// and dark, and the question at text 2.0.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_guide_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

const _groups = [
  KitGuideItem(id: 'colours', label: 'Colours', value: 'Dark, Nord'),
  KitGuideItem(id: 'style', label: 'Style', value: 'Glass', next: true),
  KitGuideItem(
    id: 'background',
    label: 'Background',
    value: 'Off in Saver',
    held: true,
  ),
  KitGuideItem(
    id: 'comfort',
    label: 'Text & motion',
    value: 'Default, full motion',
  ),
];

const _questions = [
  KitGuideItem(id: 'mode', label: 'Mode', value: 'Dark', done: true),
  KitGuideItem(id: 'theme', label: 'Theme', value: 'Nord'),
  KitGuideItem(id: 'accent', label: 'Accent', value: 'Match theme', next: true),
];

Widget _guide(String state, double height) => SizedBox(
  height: height,
  child: switch (state) {
    'question' => KitGuide(
      items: _questions,
      selected: 'theme',
      onItem: (_) {},
      group: KitGuideGroup(label: 'Colours', onBack: () {}),
      question: KitGuideQuestion(
        id: 'theme',
        title: 'Which colours?',
        changes: 'The colours of every page',
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: KitSegmented<int>(
            semanticsLabel: 'Theme',
            selected: 1,
            segments: const [
              KitSegment(value: 0, label: 'OpenCode'),
              KitSegment(value: 1, label: 'Nord'),
              KitSegment(value: 2, label: 'Dracula'),
            ],
            onChanged: (_) {},
          ),
        ),
      ),
      next: KitGuideNext(
        count: '2 of 10',
        label: 'Next: Accent',
        onPressed: () {},
        ready: true,
      ),
    ),
    'returning' => KitGuide(
      items: [
        for (final g in _groups)
          KitGuideItem(id: g.id, label: g.label, value: g.value),
      ],
      onItem: (_) {},
      home: const Padding(
        padding: EdgeInsets.all(16),
        child: KitText('Changed last: Theme', role: KitTextRole.secondary),
      ),
    ),
    _ => KitGuide(
      items: _groups,
      onItem: (_) {},
      home: const Padding(
        padding: EdgeInsets.all(16),
        child: KitText(
          'Pick up where you left off, or tap any chip.',
          role: KitTextRole.secondary,
        ),
      ),
      next: KitGuideNext(
        count: '4 of 10 seen',
        label: 'Continue: Style',
        onPressed: () {},
        ready: true,
      ),
    ),
  },
);

void main() {
  setUpAll(loadKitGalleryFonts);
  const phone = Size(412, 915);
  const wide = Size(1280, 800);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final state in ['default', 'question', 'returning']) {
      testWidgets('$state · 412x915 · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_guide_$state', phone, light: light),
          size: phone,
          light: light,
          child: _guide(state, 820),
        );
      });
    }
    testWidgets('default · 1280x800 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_guide_default', wide, light: light),
        size: wide,
        light: light,
        child: _guide('question', 700),
      );
    });
  }

  for (final size in kitGalleryScaledSizes) {
    testWidgets('default · 2.0 text · ${kitGallerySize(size)} · dark', (
      tester,
    ) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_guide_default',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: _guide('question', size.height - 64),
      );
    });
  }
}
