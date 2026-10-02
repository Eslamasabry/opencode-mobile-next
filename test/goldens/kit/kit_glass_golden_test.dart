// Gallery (gate G4) for KitGlass, docs/ux-system/kit-api/KitGlass.md: the
// plain solid surface under the dock, rail, top controls and composer, alone
// and as the joined pair. Opaque surface2, hairline, the one tight shadow.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_glass_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/glass/kit_glass.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_gallery.dart';

/// Words behind the surface, so the shot shows it is opaque.
Widget _scene(Widget Function(KitTokens tokens) surface) => Builder(
  builder: (context) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsets.all(tokens.gutter),
      child: Stack(
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (var i = 0; i < 12; i++)
                Padding(
                  padding: EdgeInsets.symmetric(vertical: tokens.space3),
                  child: KitText('Conversation ${i + 1}: the quick brown fox.'),
                ),
            ],
          ),
          Positioned(
            top: tokens.space6,
            left: 0,
            right: 0,
            child: surface(tokens),
          ),
        ],
      ),
    );
  },
);

Widget _default() => _scene(
  (tokens) => KitGlass(
    child: Padding(
      padding: EdgeInsets.all(tokens.space4),
      child: const KitText('A solid surface over the page'),
    ),
  ),
);

Widget _pair({required bool joined}) => _scene(
  (tokens) => KitGlass.pair(
    joined: joined,
    leading: Padding(
      padding: EdgeInsets.all(tokens.space3),
      child: const KitText('Laptop'),
    ),
    trailing: Padding(
      padding: EdgeInsets.all(tokens.space3),
      child: const KitText('Go'),
    ),
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGallerySizes) {
      final at = kitGallerySize(size);
      testWidgets('kit_glass default · $at · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_glass_default', size, light: light),
          size: size,
          light: light,
          child: _default(),
        );
      });
    }
    for (final size in kitGalleryScaledSizes) {
      final at = kitGallerySize(size);
      testWidgets('kit_glass default · 2.0 text · $at · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_glass_default',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          child: _default(),
        );
      });
    }
    for (final joined in [false, true]) {
      testWidgets('kit_glass pair ${joined ? 'joined' : 'apart'} · $mode', (
        tester,
      ) async {
        const phone = Size(412, 915);
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            joined ? 'kit_glass_joined' : 'kit_glass_apart',
            phone,
            light: light,
          ),
          size: phone,
          light: light,
          child: _pair(joined: joined),
        );
      });
    }
  }
}
