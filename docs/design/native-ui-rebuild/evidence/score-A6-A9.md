# Scoreboard group A6–A9 (`:ui:kit`): notice, row, sheet, bar title, breadcrumb, phrase chips

Branch `native/s17-kit-b` from `native/screens-merge22` (e6a10900e). Orders of
[SCOREBOARD-2026-11-07.md](../SCOREBOARD-2026-11-07.md) "Next orders" A6 (08), A7 (11, sheets), A8 (13), A9 (15
phrase chips). Spec: Flutter at FLUTTER_REF `8302de537`.

## How it was measured

- Container (not the VPS): JDK 21, Gradle 9.7.1, no Android SDK; desktop tests only. Goldens from
  `rb_gate.py --order NN fetch` with `FLUTTER_REPO` = this checkout (FREF fetched from origin).
- Render: `./gradlew :ui:kit:desktopTest --tests '*.rb.o08.KitNoticeGateTest' --tests '*.rb.o08.KitRowGateTest'
  --tests '*.rb.o11.KitSheetGateTest' --tests '*.rb.o13.*' --tests '*.rb.o15.KitPhraseChipsGateTest'`, with the
  layout guard and the alignment gate **on** (no `OC_LAYOUT_BASELINE`).
- Evaluate, strict (every order counted as done: `RB_STATE` pointing at a folder with `01.done` … `60.done`):
  `RB_DEV=1 RB_PARTS=<the parts> rb_gate.py --order NN evaluate` per order, parts limited to this group.
- Before = the same commands on e6a10900e (changes stashed). After = the branch head.

## Result (strict, this group's parts only)

| order / parts | goldens | pass before | pass after | not rendered before → after |
|---|---:|---:|---:|---:|
| 08 `part-kit_notice` | 52 | 0 | **18** | 40 → 10 |
| 08 `part-kit_row` | 26 | 0 | 0 | 22 → 22 |
| 11 `part-kit_sheet` | 36 | 2 | **10** | 2 → 2 |
| 13 `part-kit_top_bar`, `part-kit_breadcrumb` | 42 | 15 | 15 | 0 → 0 |
| 15 `part-kit_phrase_chips` | 8 | 0 | **8** | 6 → 0 |
| **total** | **164** | **17** | **51** | 70 → 34 |

No shot that passed before fails now. New passes: `kit_notice_default` at all six window sizes (light and dark),
`kit_notice_cost`, `kit_notice_ok`, `kit_notice_offer_light`, `kit_notice_working_light`; `kit_sheet_default`,
`_disabled`, `_half`, `_not_dismissible` (light and dark); all eight `kit_phrase_chips_*`.

Gate cases added: notice ok, warning, working, offer, offer_wrapped, offer text2/ar (both sizes), cost, default
text2/ar (both sizes) — 26 goldens; phrase chips 1280x800, text2 (dark, as Flutter renders them), empty — 6 goldens.

## What each fix was (Flutter line → native)

- **Notice 16 dp low** (A6): the gate case padded the `_Section` panel on all sides; Dart pads the gutter only
  (`kit_notice_golden_test.dart:30`). Scene fix.
- **Notice body narrower**: native `Row(spacedBy(12))` put 12 dp before the close as well; Dart has only
  `SizedBox(space3)` after the mark (`kit_notice.dart:510`). Notes are `space1` apart, title gap `space1/2`, actions
  `space2` above, `expand: false` (`:518-526`). Untitled message is the body size (`:470-472`).
- **Offer**: one line (idea mark, sentence, tertiary action, close), stacked only when it does not fit (`_offerStacks`,
  `_offerLine`, `:546-640`). Cost: no glyph, row title over secondary items (`:344-366`).
- **Notice cap**: the matrix asked for `list`; the contract (08.gate.tsv) says `reading`. The X2 failure is gone.
- **Sheet 48 px taller** (A7): Dart `KitRow(padding: EdgeInsets.symmetric(vertical: 8))` *replaces* the row's padding
  (`kit_row.dart:491-513`); the gate case added 8 dp outside each row (3 rows × 16). `KitRow` now takes `padding`.
  The check is `KitRowIcon(current: true)` (accent) in the scene. An Arabic title sits on the ambient start edge.
  The sheet's Close is text1 (`kit_icon_button.dart:295`).
- **Bar title** (A8): headline for every pushed page (`kit_top_bar.dart:591`). The kit gate shots do not move on this
  (they are dominated by the layout below), but settings, notifications and servers no longer draw a 28 sp title.
- **Phrase chips** (A9): the row filled its slot; it is now a `KitFilterChips` line (as wide as its chips, gutter,
  fading edge), so the gallery centres it like Flutter.

## Left open (and why)

- **Top bar with Back** (13: `kit_top_bar_default*`, `_needs_you`, `_subtitle_working`; box 5–18 %): Flutter puts the
  title beside Back on one 56 dp row. Native puts Back on its own line and the title on the gutter by rule (E20-S08,
  `native/docs/kit/LAYOUT-RULES.md` §2, the layout guard's `content-edge`, owned by `:ui:gate`). Changing the kit
  breaks the guard; the rule needs a decision from the design authority first.
- **Notice action button height**: the secondary button is 50 px where Flutter's is 46 (KitButton, order 07). Width
  and side padding already match (16 dp).
- **Notice text2/Arabic/offer variants**: 1–5 px line shifts; the offer_wrapped action sits 4 dp lower than Flutter
  because the layout guard's `tight` rule needs 4 dp between words and a control (Flutter has 0).
- **Sheet wide shots** (1280x800): KitActionBlock stacking at 560 dp (order 07). `with_icon`: the globe glyph's art
  (icon set, order 01). `kit_sheet_consequences`: the alignment gate rejects `KitMarkedConsequences` (order 12);
  it failed the same way before.
- **Row default** (blob 39/36) and **breadcrumb collapsed/focused** (1 px text-width rounding, focus ring of order 01).
- **Row gate cases** for 22 more `kit_row_*` goldens not written.
- Pre-existing failures in the same run, untouched: `KitNavGateTest.kit_nav_rail_needs_you_1280x800`,
  `KitScreenGateTest.kit_screen_search`.
