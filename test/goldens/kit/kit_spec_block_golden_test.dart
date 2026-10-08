// Regenerate deliberately, and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'kit_gallery.dart';

void main() {
  setUpAll(loadKitGalleryFonts);
  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    testWidgets('empty $mode', (tester) async {
      final c = TextEditingController(text: '');
      addTearDown(c.dispose);
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_spec_block_empty',
          const Size(412, 915),
          light: light,
        ),
        size: const Size(412, 915),
        light: light,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: KitSpecBlock(label: 'Goal', controller: c, readOnly: false),
        ),
      );
    }, variant: TargetPlatformVariant.only(TargetPlatform.android));
    testWidgets('disabled $mode', (tester) async {
      final c = TextEditingController(text: 'Keep drafts across restarts.');
      addTearDown(c.dispose);
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_spec_block_disabled',
          const Size(412, 915),
          light: light,
        ),
        size: const Size(412, 915),
        light: light,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: KitSpecBlock(label: 'Goal', controller: c, readOnly: true),
        ),
      );
    }, variant: TargetPlatformVariant.only(TargetPlatform.android));
    for (final size in [const Size(412, 915), const Size(1280, 800)]) {
      testWidgets('text2 $size $mode', (tester) async {
        final c = TextEditingController(text: 'Keep drafts across restarts.');
        addTearDown(c.dispose);
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_spec_block_text2', size, light: light),
          size: size,
          light: light,
          textScale: 2,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: KitSpecBlock(label: 'Goal', controller: c, readOnly: true),
          ),
        );
      }, variant: TargetPlatformVariant.only(TargetPlatform.android));
    }
  }
}
