// Gallery (gate G4) for KitFeedItem (one conversation in a feed: title, then state, project, time): at 412x915 and 1280x800, dark and
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
      title: 'Fix the login bug',
      state: 'Needs you',
      stateTone: KitFeedStateTone.needsYou,
      time: '2 h ago',
      onTap: () {},
    ),
    KitFeedItem(
      project: 'beta',
      title: 'Add dark mode',
      state: 'Working',
      stateTone: KitFeedStateTone.running,
      time: 'now',
      onTap: () {},
    ),
    KitFeedItem(
      project: 'gamma',
      title: 'Explain the build',
      state: 'Done',
      stateTone: KitFeedStateTone.done,
      time: '3 h ago',
      onTap: () {},
    ),
    KitFeedItem(
      project: 'alpha',
      agent: 'Claude Code',
      title: 'Rename the settings page and move its tests next to it',
      time: '3 d ago',
      onTap: () {},
    ),
    KitFeedItem(
      project: 'gamma',
      title: 'Plan the release notes',
      state: 'Failed',
      time: 'Sep 24',
      onTap: () {},
    ),
  ],
);

Widget _disabled() => KitRowGroup(
  leadingIcons: false,
  children: const [
    KitFeedItem(
      project: 'alpha',
      title: 'Fix the login bug',
      state: 'Working',
      stateTone: KitFeedStateTone.running,
      time: '2 h ago',
      onTap: null,
    ),
  ],
);

void main() {
  setUpAll(loadKitGalleryFonts);

  // Disabled: no tap handler (read-only).
  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('disabled · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_feed_item_disabled', size, light: light),
          size: size,
          light: light,
          child: _disabled(),
        );
      });
    }
  }

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
