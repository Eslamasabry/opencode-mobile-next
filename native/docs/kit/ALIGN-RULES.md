# Alignment rules

The pixel-level alignment every native screen keeps, and the two layers that measure it (owner brief 2026-11-07:
"cure my OCD"). It sits next to [LAYOUT-RULES.md](LAYOUT-RULES.md) (gutters, caps, targets, cut words): the layout
guard asks "does it fit", the alignment gate asks "does it line up". Numbers come from the kit tokens
(`FlutterKitTokens`, `Space`, `KitIconSize`); the device script reads the same numbers from
`native/config/align/spec.json`, which `AlignSpecFileTest` holds equal to the tokens. Tolerance: **0.5 dp**.

## Rules

| id | Rule | Where | Expected |
|---|---|---|---|
| `start-edge` | The start of every leading run of words and every leading icon falls on a page column. | screens | words at 16 (page edge), 20 (sheet rail), 16+40 = 56 and 16+32 = 48 (text column after a lead tile or compact lead), 20+40 = 60, 20+32 = 52; an icon may instead centre on the lead tile (16+14 = 30, 20+14 = 34) |
| `gap-step` | The vertical gap between a block and the next block below it in the same column (semantics siblings) is a spacing step. | screens | a multiple of 4 dp, or the 2 dp row line gap; gaps over 48 dp are page layout and are not read |
| `icon-label` | An icon and the words it labels (the nearest words after it on the same line, within 24 dp) share a centre line. | all | icon centre = the label's first-line centre or its box centre |
| `end-edge` | The trailing part of each row (chevron, ⋯, value: the end-most words or icon in the row's end half) ends on one edge per list. | all | the most common end in the list |
| `target` | Every tappable is at least 48 x 48 dp (mirrors the layout guard's `small-target`). | all | 48 dp |
| `ellipsis-frame` | Frame words never end in "…": window chrome, a bar's title (`kitContentEdge`), a button, chip or other non-row control. Screen standard §5. | all | no ellipsis; even a `kitMayTruncate` slot inside a control |
| `clip` | No words or icon are laid out past their parent's box (they are cut off or hang out). Not read under scrollers and lists. | all | inside the parent |
| `icon-size` | A KitIcon glyph is drawn at a kit size. | all | `KitIconSize`: 16, 20, 22, 24, 28 |
| `icon-squeezed` | A KitIcon glyph's box is not smaller than its glyph (sweep 2 P-101: `Modifier.size(16.dp)` on a 24 dp icon cut the status marks). | all | box ≥ asked size |
| `row-height` | Rows of one kind (same number of text lines, with or without an icon) in one list are equally tall. | all | the most common height in the list |

"Screens" are gate shots with `Frame.Screen`; "all" includes lone kit parts in the galleries (`Frame.Part`), whose
gallery scaffolding would otherwise break the page rules. Rows are tappables that bleed (`KitEdge.Bleed`); a list is
a `kitList`, a lazy list or a vertical scroller. What is not read: words inside chrome, sideways scrollers, floating
menus and non-row controls (a button centres its own label) for `start-edge`; centred and end-aligned words for
`start-edge`; right-to-left words (the gate audits Arabic shots in a left-to-right window) and anything in the end half of the window for `start-edge` (trailing parts); boxes of 1 dp or less (live regions).

## Layer 1: JVM (every gate shot, no device)

`AlignGuard` in `:ui:gate` (`AlignRules` = the rules over a neutral model, `AlignRead` = the model from the
unmerged semantics tree). `LayoutGuard.check`, which `FlutterGoldenGate.check` calls for every gate shot, hands it
the phone (412 x 915 dp) light render it already makes, so there is no extra render.

- Run: any module's `desktopTest` with gate shots, e.g. `./gradlew :feature:settings:desktopTest`.
- Output: `looks-out/align/<module>/<shot>.tsv` (`shot rule subject detail`) and `<shot>.edges.tsv` (each distinct
  start edge of the screen and how many nodes start there).
- Baseline: `native/config/align/baseline/<module>.tsv` (`shot rule subject`). A finding it does not list fails the
  test. It only shrinks: `tool/qa/layout_baseline.sh --kind align` (run by t1) fails when it grows or a module gains
  a baseline file.
- `OC_ALIGN_BASELINE=update` rewrites the baseline lines of the shots a run checked (after a fix: drop the lines and
  commit the smaller file). `OC_ALIGN=report` writes findings and never fails (a sweep of a branch whose baseline is
  stale).
- A screen without a golden opts in with `LayoutGuard.check(shot, Scene(...))`.
- Self-tests: `AlignRulesTest` (each rule fires on a bad fixture and not on a good one), `AlignReadTest` (squeezed
  KitIcon, icon off its label, words at 32 dp, read from real renders), `AlignSpecFileTest`.

## Layer 2: device (sweeps)

`native/tool/design/align_device.py` (python3 -I, stdlib only) dumps the current screen with `uiautomator dump`
(read-only, no screenshot), converts bounds with `wm density` and applies the rules a bounds tree can show:
`start-edge`, `gap-step`, `icon-label`, `end-edge`, `target`, `icon-size` (box size: a squeezed glyph shows as a
16 dp box), `row-height`.

```
python3 -I native/tool/design/align_device.py --serial emulator-5554 --name settings-hub
python3 -I native/tool/design/align_device.py --xml dump.xml --density 420 --name x
python3 -I native/tool/design/align_device.py --self-test
```

It prints the findings by rule and the screen's start edges, and writes `looks-out/align-device/<name>.json` (and the
dump). A dump cannot show ellipses (the tree has the full words), a glyph's asked size, text alignment, or a box cut by
its parent, and a clickable row merges its words into itself, so the device layer reads standalone words and row
boxes. Run it on every screen of a sweep; the JVM layer is the gate.
