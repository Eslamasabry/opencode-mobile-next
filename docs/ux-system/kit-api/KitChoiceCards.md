# KitChoiceCards — large answers to a first page's one question (2026-10-20)

Unit: vision slice "Welcome is one question" (docs/qa/vision-welcome-2026-10-20). Pick: concept A, "Two big doors": one question, two large answers, one button.

## Purpose

Two or three answers to the one question a first page asks ("Where should your agent run?"), a large card each: an icon, the answer in title size, one calm line of what it costs or means ("About 4 minutes · 208 MB download"). A tap chooses; the page's one main button, which names the chosen answer, acts. Nothing navigates from a card.

Use `KitChoiceTiles` for a sheet's few recommended choices that apply on tap, `KitChoiceList` for a list of equals, `KitAgentChoice` for agents (their marks come from the persona registry).

## API

```dart
KitChoiceCards<T>({
  required List<KitChoice<T>> choices, // leading = a KitIcon (drawn large, tinted), title, supporting = the one line
  required T? selected,                // the chosen answer: mint ring, breath of mint, mint icon, filled check
  required ValueChanged<T> onSelected, // a tap chooses; it goes nowhere
  T? busy,                             // the answer being acted on: a spinner where the check goes; every card waits
  String? semanticsLabel,              // the question
})
```

## Look

- Cards: `cardRadius`, solid `surface1`, `space5` padding, `space4` between cards. The icon at `markSize` at the start, the mark (check or empty ring, `iconTileSize`) at the end of the same row; the title (`title` role, `headline` at large text) and the line (`body`, secondary) under them.
- Chosen: an accent ring twice the focus ring, a 7 % accent breath in the surface, the icon in the accent and a filled accent check. Others: a hairline that reads on the surface, the icon in `text2` and an empty `text3` ring. The padding gives back what the thicker ring takes, so choosing never moves the words. Never a solid accent card.
- Motion: ring, tint and icon colour over `KitMotion.quick`; the check fades in from 0.9 (`KitMotion.markFrom`) on `enter` over `KitMotion.standard`, no overshoot, and leaves the same way; a changed line rises in (`KitEntrance`). Reduced motion changes at once.

## States

`States: disabled, working.` Disabled: the reason first on the line, muted, no tap. Working: `busy` (spinner on that card, every card waits). Selected is a look, not a state. Titles and lines wrap at 200 % text and 320 dp.

## Accessibility

Each card is one merged button node: selected state, in a mutually exclusive group, title and line read together ("On this phone. About 4 minutes · 208 MB download").

## Tests

`test/kit/kit_choice_cards_test.dart`, gallery `test/goldens/kit/kit_choice_cards_golden_test.dart`, G6 scenes `test/kit/kit_welcome_overflow_scenes.dart`.
