// Gallery (gate G4) for KitOptionCard (one visual choice, drawn):
// theme swatches (chosen and disabled), glow, surface, the Accent row and plain, 412x915 and 1280x800, dark and light, and at 2.0 text.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_option_card_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

import 'kit_gallery.dart';

Widget _swatch(ThemePackId id, String label, {bool selected = false}) =>
    SizedBox(
      width: 130,
      height: KitOptionCard.themeHeight,
      child: KitOptionCard.theme(
        roles: themePack(id).dark.themeRoles,
        label: label,
        selected: selected,
        onPressed: () {},
      ),
    );

Widget _cards() => Padding(
  padding: const EdgeInsets.all(16),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _swatch(ThemePackId.nord, 'Nord', selected: true),
          const SizedBox(width: 12),
          _swatch(ThemePackId.dracula, 'Dracula'),
        ],
      ),
      const SizedBox(height: 12),
      Row(
        children: [
          Expanded(
            child: KitOptionCard.glow(
              style: KitGlowStyle.classic,
              label: 'Classic',
              selected: false,
              onPressed: () {},
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: KitOptionCard.glow(
              style: KitGlowStyle.softRing,
              label: 'Soft ring',
              selected: true,
              onPressed: () {},
            ),
          ),
        ],
      ),
      const SizedBox(height: 12),
      Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final (style, label) in [
            (KitSurfaceStyle.solid, 'Solid'),
            (KitSurfaceStyle.soft, 'Soft'),
            (KitSurfaceStyle.brutal, 'Brutal'),
          ]) ...[
            if (style != KitSurfaceStyle.solid) const SizedBox(width: 8),
            Expanded(
              child: KitOptionCard.surface(
                style: style,
                label: label,
                selected: style == KitSurfaceStyle.soft,
                onPressed: () {},
              ),
            ),
          ],
        ],
      ),
      const SizedBox(height: 12),
      KitAccentRow(
        semanticsLabel: 'Accent',
        matchLabel: 'Match theme',
        theme: graphiteDark,
        swatches: const [
          KitAccentSwatch(id: 'teal', color: Color(0xFF3CCFCF), label: 'Teal'),
          KitAccentSwatch(id: 'blue', color: Color(0xFF5AB0FF), label: 'Blue'),
          KitAccentSwatch(id: 'pink', color: Color(0xFFF2A7E6), label: 'Pink'),
        ],
        selected: 'blue',
        onChanged: (_) {},
      ),
      const SizedBox(height: 12),
      // The grid form: every swatch in sight, named, four to a row.
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: KitAccentRow.grid(
          semanticsLabel: 'Accent',
          matchLabel: 'Match',
          theme: graphiteDark,
          swatches: const [
            KitAccentSwatch(
              id: 'mint',
              color: Color(0xFF3DDC97),
              label: 'Mint',
            ),
            KitAccentSwatch(
              id: 'teal',
              color: Color(0xFF3CCFCF),
              label: 'Teal',
            ),
            KitAccentSwatch(
              id: 'blue',
              color: Color(0xFF5AB0FF),
              label: 'Blue',
            ),
            KitAccentSwatch(
              id: 'violet',
              color: Color(0xFFB79CFF),
              label: 'Violet',
            ),
            KitAccentSwatch(
              id: 'pink',
              color: Color(0xFFF2A7E6),
              label: 'Pink',
            ),
            KitAccentSwatch(
              id: 'mono',
              color: Color(0xFFF2F2F2),
              label: 'Mono',
            ),
            KitAccentSwatch(
              id: 'silver',
              color: Color(0xFFB8BDC6),
              label: 'Silver',
            ),
          ],
          selected: 'teal',
          onChanged: (_) {},
        ),
      ),
      const SizedBox(height: 12),
      KitOptionCard.plain(
        icon: AppIconography.waveform,
        label: 'Calm',
        caption: 'Drawings appear, nothing keeps moving and nothing celebrates',
        selected: true,
        onPressed: () {},
      ),
    ],
  ),
);

Widget _disabled() => Padding(
  padding: const EdgeInsets.all(16),
  child: Row(
    children: [
      SizedBox(
        width: 150,
        height: KitOptionCard.themeHeight,
        child: KitOptionCard.theme(
          roles: themePack(ThemePackId.nord).dark.themeRoles,
          label: 'Material You',
          selected: false,
          onPressed: null,
          disabledReason: 'Not on this phone',
        ),
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
          name: kitGalleryName('kit_option_card_default', size, light: light),
          size: size,
          light: light,
          child: _cards(),
        );
      });
    }
    // disabled is KitOptionCard's declared state: gate G4 (stateScenes) reads
    // the 412x915 names from a literal `kitGalleryShot` call.
    testWidgets('disabled · 412x915 · $mode', (tester) async {
      await kitGalleryShot(
        tester,
        name: kitGalleryName(
          'kit_option_card_disabled',
          const Size(412, 915),
          light: light,
        ),
        size: const Size(412, 915),
        light: light,
        open: (context) => Navigator.of(context).push<void>(
          PageRouteBuilder<void>(
            transitionDuration: Duration.zero,
            reverseTransitionDuration: Duration.zero,
            pageBuilder: (_, _, _) => Scaffold(body: _disabled()),
          ),
        ),
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
          'kit_option_card_default',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: _cards(),
      );
    });
  }
}
