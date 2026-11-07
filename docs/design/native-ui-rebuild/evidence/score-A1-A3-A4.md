# Scoreboard group A1 + A3 + A4 (`:ui:kit` orders 01, 05, 06)

Branch `native/s17-kit-a`, started from `native/screens-merge22` (e6a10900e). Scoreboard:
`SCOREBOARD-2026-11-07.md` on `native/rb-score` (Next orders, A1, A3, A4).

## Pass counts (strict: every order's DEPENDS_ON counted as done)

| Order | Goldens | Before (e6a10900e) | After (this branch) | Change |
|---|---:|---:|---:|---:|
| 01 foundation | 226 | 147 | 166 | +19 |
| 05 controls | 300 | 53 | 110 | +57 |
| 06 controls | 131 | 68 | 76 | +8 |
| **Total** | **657** | **268** | **352** | **+84** |

No shot that passed before fails now (checked shot by shot, `comm` of the PASS lists).

How it was measured: `native/tool/design/gate_order.sh --recheck <NN>` with `RB_DEV=1`, every
`~/wt/ui/state/NN.done` present (strict), `FLUTTER_REPO` = this repository (FLUTTER_REF `8302de537`), tracker posts
pointed at a dead port. Run in a cloud container (JDK 21, no Android SDK; desktop tests only). Maven Central
rate-limited the container, so a local Gradle init script (not committed) pointed Central at Google's mirror. For
order 01 the Gradle step still exits 1 on one shot (below), so its count comes from `rb_gate.py evaluate --order 01`
on what rendered, the way the scoreboard counted it. The "before" numbers are the same commands on the start head.

## What changed

| Fix | Files (order) | Goldens it moves |
|---|---|---|
| `kitHairlineBorder`: a border of exactly one physical pixel. Compose's `Modifier.border` rounds a stroke up to a whole pixel, so a DPR-3 hairline was three times too heavy. Used by the segmented track, swatches and outlined surfaces. | `foundation/KitSurface.kt` (01), `choice/KitSegmented.kt`, `choice/KitSwatch.kt` (05) | segmented; `kit_swatch_*` 0 → 16 of 30 |
| Segmented track: the 48 dp target is the whole segment and the chosen block is inset inside it (track = 48 dp + two hairlines; it was 4 dp taller). Stacked form: a real `KitDivider` between rows. | `choice/KitSegmented.kt` (05) | `kit_segmented_*` 4 → 22 of 30 |
| Choice rows floor at the two-line row height (64 dp, Flutter `rowHeightTwoLine`); radio and check marks are the 20 dp small icon growing with text to 1.5×, 2 dp ring, half-size dot, filled check, scale-in and colour cross-fade (QUICK, instant under reduced motion). | `choice/KitChoiceList.kt` (05) | stacked segments, choice list |
| `KitGlyph` (every control's own mark) draws the Phosphor font glyph like Flutter's `Icon` and `KitIcon`, painted on a canvas so it has no text node. The vector path was ~1 px higher and 15 % heavier (light) / lighter (dark). | `icon/KitIcon.kt`, `foundation/KitPartGlyphs.kt` (01) | `kit_icon_button_*` 7 → 26 of 36, surface tiles, surface levels |
| Dividers take the kit pixel ratio and draw a sub-pixel line at its exact thickness. | `foundation/KitDivider.kt` (06) | divider vertical |
| Search no-match is Flutter's inline state (`KitInlineStateView`): at the reading edge, small tile, headline-size title, full-width actions, kit entrance. | `input/KitSearchField.kt` (06) | `kit_search_field_nomatch` box 31 % → 2.3-4 % |
| Needs-you row titles wrap to two lines. | `state/KitNeedsYou.kt` (06) | `kit_needs_you_row_1280x800` box 10.5 → 4.3 %; on the phone 19 → 21 % (the title now wraps, the supporting line still cannot: KitRow, below) |
| An explained section label reads as a term: text1 word over a dotted text2 underline (Skia's dotted spacing). | `row/KitSectionLabel.kt` (06) | `kit_section_label_page` moved 85 → 3-5 px |
| Gate cases: `KitChoiceListGateTest` (loading, empty, a disabled choice, multi + matrix); tappable gallery rows declare the row role; the section scene marks its following group a section; the no-match scene's body is a scroller as in the Dart `ListView`. | `rb/o05`, `rb/o01`, `rb/o06` tests | choice list: 8 goldens now rendered, 4 pass; 12 tappable shots now render |

Pictures (Flutter | native before | native after, the gate's own renders): [score-A1-A3-A4/](score-A1-A3-A4/).

## Not done, and why

- **`kit_text_type_ar` (A1)**: native reading text takes its direction from its own words (plan 06 decision K17,
  owner-checked in §19 Q4); Flutter's `KitText` follows the ambient direction, so a line that starts with a Latin
  role label lays out left to right natively. Changing it reverses an owner decision; the owner should choose.
- **Arabic face (`_ar` shots in 01, 05, 06)**: on desktop `FontFamily.SansSerif` is whatever the gate host has
  (DejaVu here), while the Flutter goldens load Roboto + Noto Sans Arabic as a device does. On a phone native uses the
  same system faces. The fix belongs in `:ui:gate` (load the Flutter gallery's Arabic fallback for desktop renders),
  not in the kit. `_ar` results are host-dependent until then.
- **`kit_tappable_focused` (01)**: still stops order 01's Gradle step. The interactive tappable shots use
  `Frame.Screen` (Flutter's bare scaffold), where the layout guard holds the gallery row to the page's 400 dp control
  cap. Pre-existing; needs the guard to know a gallery scaffold from a page (`:ui:gate`).
- **Needs-you row mark and two-line supporting line, needs-you marks/badges, divider text2 insets**: `KitRow`
  (order 08) draws the needs-you state as a dot and keeps the supporting line to one line when a state word leads it,
  and its leading glyph does not grow with text. Flutter's row takes any leading widget (`KitTaskMark`). These need a
  `KitRow` change by its owner (orders 08/10).
- **Segmented disabled reason**: Flutter indents it 8 dp; native puts it on the control's edge on purpose (E20-S08).
  Left as is; the owner should choose (the 8 failing `disabled` shots move 8 px).
- **Gate cases still missing in 05**: `kit_field`, `kit_progress*`, `kit_term*`, `kit_progress_row`,
  `kit_status_mark` (the `all` scene needs `KitRow` to take a leading mark), and the choice-list sheet, "other",
  receipts, row menus and picker rows (no native API yet).
- **`kit_search_field_nomatch`** stays a measured fail by design: the Dart gallery puts the state's words at 32 dp
  (its list padding plus the state's own gutter), which the alignment rules forbid; native keeps them on the 16 dp
  gutter.

## Tests

`S17KitAParityTest` (8 tests, `:ui:kit:desktopTest`), each red on the code before its fix (checked by restoring the
old sources): segmented track height, sub-pixel hairline border, choice-row floor, divider follows the pixel ratio,
no-match at the reading edge (LTR and RTL), needs-you title wraps, control mark = KitIcon's font glyph with no text
node, choice mark 20 dp → 30 dp with text. Also run green: `KitbDesktopTest`, `PartBDesktopTest`, and every `rb.o01`,
`rb.o05`, `rb.o06` gate class except `KitTappableGateTest.focused` (pre-existing, above).

## On-device checks (emulator)

1. Settings › Appearance (light/dark segmented control): the track has a single faint edge, no heavy outline; it is
   as tall as a 48 dp row plus the edge; tapping anywhere in a segment's height chooses it and the block slides.
2. Theme packs grid: tile edges are one device pixel; the chosen tile's accent edge is one pixel.
3. Any choice sheet (model, voice): radio rings are 20 dp at default text, larger at 200 % text; choosing one scales
   the dot in; with Remove animations on, it appears at once. Rows are at least 64 dp tall.
4. A search with no result (Settings search, Search, a model sheet): the "Nothing … matches" state starts at the left
   edge in English and the right edge in Arabic, with a small search tile; TalkBack still announces "No results".
5. Icon buttons in top bars: glyphs look the same weight as the Flutter app; TalkBack reads the button label only.
6. A screen with an explained section label (e.g. MCP servers): dotted underline under the label; tapping it shows
   the explanation.
