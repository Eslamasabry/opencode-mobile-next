# Layout rules

The numbers every native screen keeps, and the test that holds them (story E02-S13). Source of truth: the Flutter kit's
`docs/ux-system/kit-v2.md` on `master` (§2.12 KitScreen, §8 every screen size, §8.6 capacity and maximum widths). Kit
part specs: `docs/design/native-rewrite-2026-11-01/plan/06-ui-kit-design-system.md`. The tokens live in `:ui:design`
(`FlutterKitTokens`, `Space`, `Layout`, `WindowClass`); no screen writes these numbers itself.

## 1. Windows and the page gutter

| Window class (`WindowClass`) | Width | Page layout (kit-v2 §8.2 `KitScreen`) |
|---|---|---|
| Compact (phone) | < 600 dp | full width, **16 dp gutter** on both sides (`FlutterKitTokens.GUTTER`) |
| Medium (small tablet, phone landscape) | 600–839 dp | content centred at `READING_WIDTH` 720 or `LIST_WIDTH` 960, still at least 16 dp from each edge |
| Expanded / Large (tablet landscape, PC) | ≥ 840 / ≥ 1200 dp | the same, or two panes: list 296 dp (`PANE_LIST_WIDTH`), detail up to 700 dp |

A short window (height < 480 dp) keeps the compact layout for parts that stack vertically.

## 2. Edges

- **Words** (every text node) stay inside the 16 dp gutter: start ≥ 16 dp, end ≤ window − 16 dp.
- **Controls** (every tappable or field box) stay at least 4 dp (`Space.S1`) from the window edge. A 48 dp icon button
  whose box sits 4 dp from the edge puts its 24 dp glyph on the 16 dp gutter, as Flutter's app bar does; a box at
  0 dp ("an action flush at x = 0") is a violation.
- **Exceptions are declared in the kit, next to the part**, with `Modifier.kitEdge(...)` (`:ui:kit`
  `foundation/KitLayoutMarks.kt`), never in a global ignore list:
  - `KitEdge.Bleed`: the part's own box may reach the edges. Declared by every `KitTappableRole.Row` tappable, the
    switch rows (`KitSwitchRow`, `KitIconSwitchRow`, `KitLeadingSwitchRow`, `KitOpenSwitchRow`), `KitChoiceList`
    rows and the `KitFloatingPanel` scrim. The words and controls inside are still held to the gutter.
  - `KitEdge.Chrome`: window chrome anchored to an edge (a navigation rail or bar). The part owns its insets; nothing
    inside it is checked against the gutter.
  - Dividers, sheet frames and backgrounds carry no words and no tap, so the edge rule never reads them.
  - Content inside a sideways scroller (a chip row, a carousel) scrolls past the edge by design and is not checked.

The top bar follows the same rule: with no Back or Close, its title's words start on the 16 dp gutter (Flutter
`kit_top_bar.dart`: "No exit: the title starts on the gutter").

## 3. One start edge per list

Rows stacked in one list column (the same list and the same row box) start their titles on one edge, within 1 dp. A
list is a `KitRowGroup` (`kitList`), a lazy list (collection info) or a vertical scroller. A row without a leading tile
in a group whose rows have one is a violation: give it the same leading slot, or move it to its own group.

## 4. Touch targets

Every tappable is at least **48 × 48 dp** (kit-v2 §8.3: "Touch targets stay 48 dp everywhere"; `tokens.minTarget`).
Desktop density never shrinks a target; a fine pointer adds hover, not smaller targets.

## 5. Vertical spacing

Spacing comes only from the kit scale: `Space.S1..S6` = 4, 8, 12, 16, 20, 24 dp, and sums of them (the 4 dp grid).
The guard reads it where the semantics tree can show it honestly:

- a control and the words or control directly above or below it are at least **4 dp** apart (`tight`), and never
  overlap (`overlap`): a headline must not sit against a search field. A part drawn inside a control's box (a clear
  button in a field, a placeholder) is not a neighbour;
- two stacked controls up to 48 dp apart (two `S6` steps) are spaced on the **4 dp grid** (`off-scale`); a larger
  gap is page layout (a pinned bar, a weighted spacer), not a spacing step.

Gaps between two runs of words are typography (line height of the type scale), so the guard does not read them.

## 6. Words that do not fit

Each text slot's overflow rule is part of the kit part (kit-v2 §8.6): wrap, ellipsis after N lines, or a sideways
scroller.

- **May end in "…"**: titles (`KitText` roles LargeTitle, Title, Headline, RowTitle), a row's supporting line, and a
  technical value cut on purpose (`KitMonoText` with `KitMonoCut.End` / `Middle`). The part declares it with
  `Modifier.kitMayTruncate(slot)`.
- **May not**: buttons, chips, labels, body words, status words. They wrap or the part grows (`ellipsis`).
- **Never**: words cut off with no sign (`clipped`, Flutter's `clip:N` / `visible:N` violations): lines past
  `maxLines` dropped, or a line wider or the lines taller than the text's box by more than 1 dp.

## 7. Maximum widths (kit-v2 §8.6, `Layout`)

| Role | Cap (dp) | Parts |
|---|---|---|
| conversation | 700 | messages, turns, tool rows, composer |
| reading | 720 | prose, cards, notices, forms (`KitText` holds it) |
| list | 960 | rows, lists, tables, code, logs, search fields |
| sheet | 640 | sheets and pickers |
| confirm | 560 | confirmations |
| dialog / toast | 480 | dialogs, the undo bar |
| state | 440 | page states |
| control | 400 | one button or compact control |
| chip / popover | 320 | chips, pills, tags; menus and bubbles |
| glyph | 96 | icons, marks, avatars |

A part holds its own cap with `Modifier.kitMaxWidth(CapRole.X)` as its outermost modifier, so a caller can never
widen it (`KitButton` holds the control cap even when the caller passes `fillMaxWidth`). The guard checks the
widest caps on the tablet window: no words or list row wider than 960 dp (`wide-text`, `wide-row`) and no button
wider than 400 dp (`wide-control`).

## 8. The guard

`LayoutGuard` in `:ui:gate` (`native/ui/gate/src/desktopMain/.../LayoutGuard.kt`) renders a scene at a **phone
(412 × 915 dp)** and a **tablet (1280 × 800 dp)** window, light and dark, walks the Compose semantics tree and reports
each broken rule as `window  rule  subject` (the subject is the node's words, digits folded to `#`).

- **Where it runs**: inside `FlutterGoldenGate.check`, so every gate shot (every screen fixture with a Flutter golden
  under `native/feature/*/src/desktopTest`, and every kit gallery scene under `native/ui/kit/src/desktopTest`) is
  audited by that module's `desktopTest`. Screens get every rule; a lone kit part (`Frame.Part`) skips the page rules
  (§2, §3). A screen without a golden can call `LayoutGuard.check(shot) { Screen(...) }` from its own test.
- **Findings** of each shot are written to `looks-out/layout/<module>/<shot>.tsv`.
- **Baseline**: `native/config/layout/baseline/<module>.tsv` lists the screen-specific findings that existed when the
  guard arrived. A finding not listed fails the test. The baseline **may only shrink**: `tool/qa/layout_baseline.sh`
  (run by T1 every time) fails when a baseline gains a line or a new baseline file appears compared with
  merge-base(<base>, HEAD). After fixing a screen, run its tests with `OC_LAYOUT_BASELINE=update` to drop the fixed
  lines and commit the smaller file; the ratchet refuses a file that grew.
- **Fixing**: one cause that breaks many screens is fixed in the kit part; a screen's own mistake is fixed in the
  screen. Never baseline a new finding.
- **Not yet covered**: right-to-left windows (the guard measures left-to-right edges), text scale 2.0, and screen tests
  that render without `FlutterGoldenGate` (they can opt in with `LayoutGuard.check`).
