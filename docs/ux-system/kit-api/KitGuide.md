# KitGuide — API (guided Appearance, 2026-10-23)

A settings page that asks one decision at a time. A rail of chips sits at the top, each showing its current value. There is no preview (2026-10-26): every choice applies at once, so the page itself shows it. Under the rail is either the open question or the page's start words. Built for Settings › Appearance (owner-approved proposal "Appearance, one decision at a time", 23 Oct); any page with a few groups of single choices can use it.

## File

- `lib/ui/kit/kit_guide.dart`, exported from `kit.dart`.
- Tests: `test/kit/kit_guide_test.dart` (with its reduced-motion samples); gallery `test/goldens/kit/kit_guide_golden_test.dart`.
- Caller: `lib/ui/screens/settings/appearance_screen.dart`.

## Public API

```dart
class KitGuideItem {            // one chip: a group, or a question in one
  const KitGuideItem({
    required String id, required String label, required String value,
    Key? key, bool done = false, bool next = false, bool held = false,
    String? semanticsLabel,     // "Theme, Nord, question 2 of 3 in Colours"
  });
}

class KitGuideGroup {           // the group open in the rail
  const KitGuideGroup({required String label, required VoidCallback onBack, Key? backKey});
}

class KitGuideQuestion {        // the one question on screen
  const KitGuideQuestion({
    required String id, required String title, required String changes, required Widget child,
  });
}

class KitGuideNext {            // the guided visit's bar along the foot
  const KitGuideNext({required String count, String? label, VoidCallback? onPressed,
    bool ready = false, Key? key});
}

class KitGuide extends StatelessWidget {
  const KitGuide({
    super.key,
    required List<KitGuideItem> items,   // the groups, or one group's questions
    required ValueChanged<String> onItem,
    String? selected,                    // the item whose question is open
    KitGuideGroup? group,                // null: the start view
    KitGuideQuestion? question,
    Widget? home,                        // under the rail with no question open
    Widget? notice,                      // over the question (a refused save)
    KitGuideNext? next,                  // null: no bar (a returning visit)
  });
}
```

## Look

- Chips: `surface1` with a hairline, `cardRadius`, the label in `rowTitle` and the value in `secondary` (two lines at most). The chosen chip is tinted with the accent and has an accent ring. A `next` chip has an accent edge and a 7 dp accent dot at its top end. A `done` chip has a small accent check after its label. A `held` chip's value is in the tertiary tone. The chip is still a target, and opening it says why.
- Each chip takes the style in force through `KitSurfaceLook.decorate` (role `control`). Where the style draws its own face, the accent ring is drawn over it, so the choice never relies on the style.
- Start view: the groups two by two (one per line from text 1.6×). Where the style sets lists flat (Solid) it is the page's own list instead: a `KitRowGroup` of `KitRow`s, each with its value under its name and a chevron, since each opens its questions. In a group, a row with "‹ All", the group's name and a dot per question (lit up to the open one) sits over the questions in one line (stacked from text 1.3×).
- Question: its title in `headline`, then one line: "Changes" in the accent caption, followed by what it changes in `secondary`. Its control comes under that.
- Next bar: a hairline, the count in `secondary` at the start, and the step as a `KitButton` at the end. The button is secondary until `ready`, then primary.

## Layout (kit-v2 §8)

- Ordinary text: the rail stays on top, the question scrolls in the rest, and the bar is pinned at the foot.
- Large text (1.3× and up) or a short window: everything scrolls as one page, with the bar at the foot.
- Unbounded height (inside another scroll view): one column, as tall as it is.

## Motion

Every motion takes 150–250 ms. A new question fades in and rises 2%. The rail cross-fades between the groups and a group's questions. A check or a dot fades in from 0.9 (`KitMotion.markFrom`) on `enter`, with no overshoot, and the place dots change colour. Under reduced motion every change is instant. Nothing loops.

## Accessibility

- Each chip is one button. It reads its `semanticsLabel`, or its label and value, and says when it is selected.
- The group's name and the question's title are headers. The Changes line reads as one sentence.
- Back reads "Back to all chips".

States: none — a frame around the controls it is given.
