# KitChoiceTiles — a few big choices that lead with the reason (2026-10-20)

Unit: vision slice "Model picker: three plain choices" (docs/qa/vision-model-picker-2026-10-20). Rule: "Choices lead with the reason; three recommended rows plus All models (N)" and "a selected row gets a check and a mint hairline, never a solid mint row" (vision learnings, Patterns to kit).

## Purpose

The first view of a choice that most people make by its reason, not its name: the model sheet's "Default", "Recently used", "Cheapest". Each choice is its own tile with a title (the reason) and a supporting line (the name and plain facts). A tap chooses and the host closes its sheet: there is no Apply. One quiet line under the tiles ("All models (42) ›") opens the full choice for people who know names.

Use `KitChoiceList` for a list of equals (languages, servers, modes). Use `KitChoiceTiles` only for at most three recommended choices with a way to everything else.

## API

```dart
KitChoiceTiles<T>({
  required List<KitChoice<T>> choices, // title = the reason, supporting = the name and facts
  required T? selected,                // the value in use: check on an accent disc + accent ring
  required ValueChanged<T> onSelected, // one tap, one callback
  T? busy,                             // the value being applied: a still-under-reduced-motion spinner; every tile waits
  KitChoiceMore? more,                 // the quiet last line: label + onTap (+ key)
  String? semanticsLabel,              // the group's name
})
```

## Look

- Tiles: `panelCornerRadius`, one surface step up from the sheet (`surface3` in dark; `surface1` with a hairline in light), `space3` between tiles, title in `headline`, supporting line in `body` / secondary tone.
- Selected: an accent ring of `KitTokens.focusRingWidth` and the check on an accent disc. Unselected tiles keep a ring of the same width in their own colour, so choosing never shifts the words. Never a solid accent fill (the accent fill is for the one main action).
- The more line: `rowTitle`, secondary tone, a trailing chevron that follows the text direction.
- Motion: the ring colour and the check's scale over `KitMotion.quick`; reduced motion changes at once.

## States

`States: disabled, working.` Disabled: the reason first on the supporting line. Working: `busy` is being applied (spinner on that tile, every tile waits). Selected is a look, not a state. Titles and supporting lines wrap at 200 % text and 320 dp; nothing truncates.

## Accessibility

Each tile is one merged node: selected state, in a mutually exclusive group, its title and supporting line read together. The more line is its own button.

## Tests

`test/kit/kit_choice_tiles_test.dart`: one tap one callback, selected semantics, busy blocks every tap, the more line, a disabled reason, 320 dp at 200 % text in both directions.
