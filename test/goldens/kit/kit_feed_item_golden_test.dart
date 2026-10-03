// Gallery (gate G4) for KitFeedItem (one conversation in a feed: project, title, state or time, last line): at 412x915 and 1280x800, dark and
// light, and at 2.0 text.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_feed_item_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _part() => KitRowGroup(
  leadingIcons: false,
  children: [
    KitFeedItem(
      project: 'alpha',
      gitLabel: 'Git',
      title: 'Fix the login bug',
      preview: 'Allow running the tests?',
      tag: const KitStatusTag(
        label: 'Needs you',
        tone: KitStatusTagTone.needsYou,
      ),
      onTap: () {},
    ),
    KitFeedItem(
      project: 'beta',
      title: 'Add dark mode',
      preview: 'Editing lib/ui/theme.dart',
      tag: const KitStatusTag(label: 'Running', tone: KitStatusTagTone.running),
      onTap: () {},
    ),
    KitFeedItem(
      project: 'alpha',
      gitLabel: 'Git',
      title: 'Rename the settings page and move its tests next to it',
      preview: 'Done. Four files changed.',
      time: '3d ago',
      onTap: () {},
    ),
    KitFeedItem(
      project: 'gamma',
      title: 'Plan the release notes',
      time: 'Sep 24',
      onTap: () {},
    ),
  ],
);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('default · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_feed_item_default', size, light: light),
          size: size,
          light: light,
          child: _part(),
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
          'kit_feed_item_default',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: _part(),
      );
    });
  }
}
