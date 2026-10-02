// Regenerate deliberately, and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import '../../kit/team_kit_test_support.dart';
import 'kit_gallery.dart';

void main() {
  setUpAll(loadKitGalleryFonts);
  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    testWidgets('loading $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_findings_card_loading',
          const Size(412, 915),
          light: light,
        ),
        size: const Size(412, 915),
        light: light,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: teamSample('kit_findings_card', state: KitTeamState.loading),
        ),
      );
    }, variant: TargetPlatformVariant.only(TargetPlatform.android));
    testWidgets('empty $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_findings_card_empty',
          const Size(412, 915),
          light: light,
        ),
        size: const Size(412, 915),
        light: light,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: teamSample('kit_findings_card', state: KitTeamState.empty),
        ),
      );
    }, variant: TargetPlatformVariant.only(TargetPlatform.android));
    testWidgets('error $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_findings_card_error',
          const Size(412, 915),
          light: light,
        ),
        size: const Size(412, 915),
        light: light,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: teamSample('kit_findings_card', state: KitTeamState.failed),
        ),
      );
    }, variant: TargetPlatformVariant.only(TargetPlatform.android));
    testWidgets('working $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_findings_card_working',
          const Size(412, 915),
          light: light,
        ),
        size: const Size(412, 915),
        light: light,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: teamSample('kit_findings_card', state: KitTeamState.running),
        ),
      );
    }, variant: TargetPlatformVariant.only(TargetPlatform.android));
    testWidgets(
      'disabled $mode',
      (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_findings_card_disabled',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: teamSample('kit_findings_card', state: KitTeamState.stale),
          ),
        );
      },
      variant: TargetPlatformVariant.only(TargetPlatform.android),
    );
    testWidgets(
      'answered $mode',
      (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_findings_card_answered',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: teamSample('kit_findings_card', state: KitTeamState.done),
          ),
        );
      },
      variant: TargetPlatformVariant.only(TargetPlatform.android),
    );
    testWidgets(
      'needs_you $mode',
      (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_findings_card_needs_you',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: teamSample(
              'kit_findings_card',
              state: KitTeamState.needsYou,
            ),
          ),
        );
      },
      variant: TargetPlatformVariant.only(TargetPlatform.android),
    );
    testWidgets('stalled $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_findings_card_stalled',
          const Size(412, 915),
          light: light,
        ),
        size: const Size(412, 915),
        light: light,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: teamSample('kit_findings_card', state: KitTeamState.stalled),
        ),
      );
    }, variant: TargetPlatformVariant.only(TargetPlatform.android));
    testWidgets('stale $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_findings_card_stale',
          const Size(412, 915),
          light: light,
        ),
        size: const Size(412, 915),
        light: light,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: teamSample('kit_findings_card', state: KitTeamState.stale),
        ),
      );
    }, variant: TargetPlatformVariant.only(TargetPlatform.android));
    for (final size in [const Size(412, 915), const Size(1280, 800)]) {
      testWidgets(
        'text2 $size $mode',
        (tester) async {
          await kitGalleryPart(
            tester,
            name: kitGalleryName('kit_findings_card_text2', size, light: light),
            size: size,
            light: light,
            textScale: 2,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: teamSample('kit_findings_card'),
            ),
          );
        },
        variant: TargetPlatformVariant.only(TargetPlatform.android),
      );
    }
  }
}
