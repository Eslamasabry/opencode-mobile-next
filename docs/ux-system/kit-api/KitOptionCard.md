# KitOptionCard — API (Appearance studio, 2026-10-20; final design 2026-10-21)

One visual choice, drawn. The chosen card has an accent ring and a check badge, so selection is never colour alone.

## Replaces

- The abstract theme tiles (`KitSwatch`, bars and dots) on Settings › Appearance, the look picker's private tile, and the segmented bars that named a glowing-border style or a Motion level in words only. `KitSwatch` stays for other uses.

## File

- `lib/ui/kit/kit_option_card.dart`, exported from `kit.dart`.
- Tests: `test/kit/kit_option_card_test.dart`. Gallery: `test/goldens/kit/kit_option_card_golden_test.dart`.

## Public API

```dart
class KitOptionCard extends StatelessWidget {
  const KitOptionCard.theme({roles, label, selected, onPressed, disabledReason, saverLook}); // a compact swatch in its own roles
  const KitOptionCard.look({look, label, selected, onPressed});                   // a live look behind a mock chat
  const KitOptionCard.glow({style, label, selected, onPressed});                  // the message box border of one KitGlowStyle
  const KitOptionCard.surface({style, label, selected, onPressed, disabledReason}); // a sample of one KitSurfaceStyle
  const KitOptionCard.plain({icon, label, caption, selected, onPressed});         // icon, title, one line of words

  static const double themeHeight = 168;  // in a KitCarousel, at normal text
  static const double lookHeight = 190;
}

class KitAccentSwatch {
  const KitAccentSwatch({required String id, required Color color, required String label});
}

class KitAccentRow extends StatelessWidget {
  const KitAccentRow({
    required String semanticsLabel,       // "Accent"
    required String matchLabel,           // "Match theme"
    required ThemeRoles theme,            // the theme's own roles: its main and partner colours
    required List<KitAccentSwatch> swatches,
    required String? selected,            // a swatch id; null: Match theme
    required ValueChanged<String?> onChanged,
  });
}
```

## Look

- A `surface1` card on `cardRadius` with a hairline edge; selected: an accent ring (`focusRingWidth`) and a check badge in an accent circle that fades in from 0.9 (`KitMotion.markFrom`) on `enter` over `KitMotion.standard`; no overshoot.
- **theme:** a compact swatch (about two and a half across a phone in a `KitCarousel` with `share` ≈ .4): its own ground with the person's live look as one still frame (strength 1.6), a stripe of its main colour and a dot of its partner colour at the foot, the small check badge at the top end; the name under the tile. The ring (`focusRingWidth`, current accent) is on the tile. Under Saver (`saverLook`) the ground is black and the stored look is drawn faintly (35 %), never an empty square. Disabled: the reason is written on the tile.
- **surface:** a small panel on the page's plain colour, made by the style's own resolver: Solid (matte `surface2` with a hairline), Soft (lit gradient with a light and a dark soft shadow), Brutal (ink edge and a hard shadow), Paper (ink rules, square). Disabled, the reason is said once under the row by the host; it is still announced.
- **look:** the look (strength 2.5, so it reads at this size) behind a tiny mock chat.
- **glow:** a message box pill. Classic is a thin dim line with one bright head on its top edge; Soft ring is a thick bright line inside a wide soft wash and no head, so the two differ at a glance.
- **plain:** the icon (accent when chosen), the badge at the end, the title and the caption.
- The check badge is a 20 dp accent disc.
- **KitAccentRow:** a "Match theme" tile (the theme's two colours overlapping, the words under them) and round 36 dp swatches in 48 dp targets, one line that scrolls sideways when it does not fit. Chosen: a ring in the swatch's own colour stands off it and a check sits on it (Match theme: a ring in the Accent). Every swatch is a button in a mutually exclusive group, value "In use" when chosen.

## Behaviour

- A tap chooses at once (`KitTappable`: 48 dp, focus ring, hover).
- Disabled (`onPressed` null, `disabledReason` required): the reason is visible words under the name, never faded text.

States: disabled.

## Accessibility

One button named `label`, selected semantics, value "In use" (`kitSwatchInUse`), the reason as hint; its drawing is excluded. In a mutually exclusive group.

## RTL

Mirrors: the badge and the bubbles follow reading direction.

## Motion and haptics

Ring and badge change over `KitMotion.standard`, at once under reduced motion or Motion Off. No haptics.

## Depends on

`ThemeRoles`, `KitTappable`, `KitLiveBackground`, `liveMockChat`, `KitIcon`, `KitText`, `KitTokens`.
