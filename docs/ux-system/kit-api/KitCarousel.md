# KitCarousel — API (Appearance studio, 2026-10-20)

Cards to swipe through, one page at a time, with the next card peeking in and where you are shown under them. Themes and live looks in Settings › Appearance.

## Replaces

- The theme swatch grid of 31 tiles (a very long page) and the look picker page. One row of cards says "there is more" and keeps the page short.

## File

- `lib/ui/kit/kit_carousel.dart`, exported from `kit.dart`.
- Tests: `test/kit/kit_carousel_test.dart`. Gallery: `test/goldens/kit/kit_carousel_golden_test.dart`.
- Caller: `lib/ui/screens/settings/personal_settings_screens.dart` (Theme and Background tabs).

## Public API

```dart
class KitCarousel extends StatefulWidget {
  const KitCarousel({
    super.key,
    required this.semanticsLabel, // "Themes"
    required this.itemCount,
    required this.itemBuilder,    // one card, usually a KitOptionCard
    required this.itemHeight,     // at normal text; grows with the text size
    this.initialIndex = 0,        // the card shown first (the chosen one)
    this.carouselKey,
  });
}
```

## Look

- Pages are `KitCarousel.cardShare` (62 %) of the width: the next card is cut off at the edge. The first card starts on the gutter and the row runs to the window's edge.
- Under the cards: dots (the nearest page a short accent bar, the rest `surface3` dots) up to `dotsUpTo` (8) cards; more show a short track with a growing accent bar, since thirty dots say nothing.
- The cards' height grows by up to 140 dp from 1.0 to 2.5 × text so a label never clips.

## Behaviour

- **Swiping is an extra.** Every card is its own target (`KitTappable` in `KitOptionCard`), reachable by Tab (focus scrolls the card into view) and by a screen reader.
- A mouse, a trackpad and a stylus drag it too.
- The place indicator animates over `KitMotion.quick` (at once under reduced motion).

States: none — it holds the cards it is given; each carries its own looks.

## Accessibility

One group named `semanticsLabel`, value "3 of 5" (`kitCarouselPlace`); the dots and bar are excluded from semantics. Cards keep their own semantics.

## RTL

The first card is at the start edge (right in Arabic) and swiping runs in reading direction; the bar grows from the start.

## Motion and haptics

Page snapping is the framework's. Place indicator 150 ms. No haptics.

## Depends on

`KitTokens`, `KitMotion`, `PageView`.
