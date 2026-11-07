# Scoreboard group C: `:ui:kit-content` (2026-11-07)

Branch `native/s17-content` from `native/screens-merge22` (e6a10900e). Orders: 18 (the five kit-content parts), 08
(`part-kit_log_panel`), 06 (`part-kit_qr`), 23 (`part-kit_web_preview`). Module `:ui:kit-content` only.

## How it was measured (this container, not the VPS)

- Flutter goldens: FLUTTER_REF `8302de537` is not in this clone, so the reference PNGs were taken from
  `origin/master` (the released 2.1.4+62 tree) and copied into `looks-gate/flutter/8302de537/`; `rb_gate.py fetch`
  then wrote `render.tsv`. `rb_gate.py` ran from a scratch copy whose only change reads Flutter sources from
  `origin/master` instead of FREF (the folder name stays `8302de537`). If master's goldens differ from FREF's, the
  VPS run is the one that counts.
- Gate cases: `./gradlew :ui:kit-content:desktopTest --tests '*.rb.o18.*'` (JDK 21, Android SDK 37.1 installed
  locally; Maven Central answered 429, so a local init script outside the repo pointed Gradle at Google's Maven
  Central mirror).
- To get every picture measured, the counting run used `OC_ALIGN=report` and `OC_LAYOUT_BASELINE=update` (the
  layout baseline file it wrote was deleted, nothing baselined). The guard findings are listed below; they still fail
  the tests in a normal run.
- `rb_gate.py evaluate --order 18` with `RB_PARTS` = the five kit-content parts (strict: thresholds of
  `gate_thresholds.tsv`, no waivers).

## Before / after

| part (order 18) | goldens | rendered before (89ec49e55) | pass before | rendered after | pass after |
|---|---:|---:|---:|---:|---:|
| `part-kit_viewer` | 48 | 0 | 0 | 48 | 1 |
| `part-kit_terminal_view` | 34 | 0 | 0 | 34 | 21 (19 with the guards on) |
| `part-kit_image_gallery` | 20 | 0 | 0 | 20 | 0 |
| `part-kit_image_markup` | 16 | 0 | 0 | 16 | 0 |
| `part-kit_scanner` | 13 | 0 | 0 | 13 | 0 |
| **order 18, kit-content** | **131** | **0** | **0** | **131** | **22** |

| other orders | goldens | rendered before | pass before | rendered after | pass after |
|---|---:|---:|---:|---:|---:|
| `part-kit_log_panel` (08) | 26 | 0 | 0 | 26 | 1 |
| `part-kit_qr` (06) | 10 | 10 | 2 | 10 | 2 (no change; the misses are 1.3-3.2 px in the Copy button row, `:ui:kit`) |
| `part-kit_web_preview` (23) | 8 | 0 | 0 | 0 | 0 (Android-only part) |

Log panel: a new `KitLogPanel(lines: KitLogBuffer, …)` overload ports `kit_log_panel.dart` (neither existing
log panel was that port). Its text lines sit within 1 px of Flutter's; what fails is the Wrap/Copy glyph weight and
the fold chevron (`KitIconButton`/`KitIcons`, `KitDetailsFold`), `KitErrorNotice`'s smaller message (read_failed)
and the smaller `KitJumpPill` (scrolled_up). In a strict run 11 of its 12 gate methods fail on the align gate where
the panel matches Flutter: words at 28 dp (16 gutter + 12 panel inset) and a 27 dp gap under the header. The same
conflict hits the terminal output shots. **Decision for the lead:** a rule for inset surfaces, or baseline lines;
nothing was baselined.

## What changed

- **KitViewer** (new, `viewer/KitViewer.kt`): the Dart viewer as one composable over `KitViewerSource` — header
  (name, mono parent folder, More, Close), the primary on its own row, the truncation notice with Open all, Find with
  marks, and every body. `KitViewerSheet` is the 0.95-height sheet with its handle. Measured against Flutter, the
  header, button row, code lines, skeleton, image, PDF, SVG and table now sit within 1 px of the goldens.
- **KitViewerTableBody**: columns measured like `_KitTable` (longest of 100 cells, 40 characters, between 96 and
  240 dp, plus space3 sides), 56 dp rows, label header, secondary cells, hairline rules.
- **KitPdfPage**: the caption is space1 under its page and centred over the page width.
- **Terminal**: `KitTerminalOutput` and `KitTerminalLiveView` (the Dart output and live forms); key caps fill their
  48 dp slots, the grid stays centred in Arabic, hairline and focus ring follow the view's pixel ratio.
- **KitImageGallery** and **KitImageMarkup** (new).

## What still fails, and whose part it is

Viewer (47 of 48 fail). The frame itself lines up; what remains is outside this module:

1. **Icon stroke weight** (`:ui:kit`, `KitIcons` / `KitIcon`): native glyphs draw thinner than Flutter's Phosphor
   regular font glyphs — the header's × (also smaller) and the zoom controls' arrows. The header band fails the gate's
   "line moved" rule (its centroid is measured in x as well as y) on every viewer, gallery and sheet shot. Image,
   PDF light, SVG, delimited and loading fail on this alone; everything else in those shots is within 1 px.
2. **Syntax colour and Markdown prose** (`:ui:kit-chat`): kit-content cannot depend on kit-chat, so the viewer takes
   `codeLine` and `prose` from the host. The kit-content gate case renders without them (code in one colour,
   Markdown as source). The code, truncated, finding and markdown shots need a gate case at a level that can use both
   kits (a feature module), or the tokenizer moved down to a module both kits may use.
3. **KitStateView inline size** (`:ui:kit`): Dart's `KitStateSize.inline` starts the icon, title and body at the
   gutter and ends the button; native always centres. Binary and error shots.
4. **KitNotice message role** (`:ui:kit`): the notice's words are smaller than Flutter's (truncated shots). Its
   action row also sits 10 dp under the words (a 2 dp + 8 dp gap), which the align gate refuses; the viewer therefore
   draws Open all itself under the notice.
5. **KitSearchField** (`:ui:kit`): focus ring and height differ (finding shots); the tapped Next button keeps its
   tooltip on screen.
6. **KitSkeletonRows** (`:ui:kit`): Dart holds the list cap itself; the viewer holds it for now. Its shape boxes are
   unmerged semantics nodes 35 dp apart, which the align gate reads as a gap (`kit_viewer_loading*`).
7. **KitDivider** (`:ui:kit`) draws `1 / density` dp, a full pixel at the gate's density 1; Flutter's is one physical
   pixel. The viewer draws its rule with `kitHairline()`.

Terminal (13 fail; see the fork notes): the sheet's Close glyph (`KitSheet`, `:ui:kit`), the larger sheet header at
text 2.0, Arabic digits by owner decision 2026-11-03 (Flutter's golden shows Latin digits), Flutter's own resize
artefacts in `live_360x800` and `live_text2`, and `:core:common` `TerminalText.ansiRole` colours. The output shots
also break three guard rules where they match Flutter (text at x 28 dp = sheet rail 20 + code padding 8; Show
earlier / Open all at 0 dp from the text; 6 dp under the sheet's Close, which is `KitSheet`).

Gallery, markup, scanner (49 fail): `KitTopBar` (`:ui:kit`) draws the title at 28 sp instead of the headline size,
greys a disabled action instead of moving it to ⋯, has no hairline on wide windows, and leaves a 7 dp title/subtitle
gap the align gate refuses; `KitStateView` centring; `KitWorkingMark` draws a dot with motion off where Flutter
shows the arc; `KitScanner` (`:ui:kit`) has no camera lifecycle, so starting, slow and paused render the nearest
state, and its brackets and rejected notice differ.

Web preview (order 23, 8 goldens): the part is `:android:webview` `KitWebPreview` (Android only), so no desktop gate
case can render it from kit-content. It needs a common frame (address bar, error state) in kit-content first.

## Tests in a normal run (`./gradlew :ui:kit-content:desktopTest`)

143 tests, 6 fail (before the log panel; its 11 gate methods add to these in a strict run), all on guard findings named above, none on a pre-existing test: `KitViewerGateTest.markdown`
(blank source lines, item 2), `KitViewerGateTest.loading` (item 6), `KitImageGalleryGateTest.states` and
`KitImageMarkupGateTest.states` (KitTopBar 7 dp), `KitTerminalViewGateTest.output` and `.scaled_and_arabic`.
Nothing was added to a baseline. Robolectric `testAndroidHostTest` cannot start in this container ("Thermal status
listener failed to set" in every ContentGoldensTest case), so `src/screenshots/KitPdfPage/*` was not re-recorded
after the caption change: re-record it on the PC.

## Pictures

Side by side (Flutter left, native right) in [score-C/](score-C/).

## On-device checks for the coordinator

1. Files › open a code file on the phone: the sheet rises to 95 % with its handle; name, folder, ⋯ and × on one
   line; "Add to prompt" on its own row under the name; code on the darker fill with grey line numbers.
2. ⋯ › Find in file, type "name", tap ↓: "2 of 4", the second match marked stronger.
3. Open a CSV: columns sized to their words, 56 dp rows, a hairline under each row, sideways scroll on a wide table.
4. Open a large PNG on a tablet: it shows at its own size, centred, not stretched; the zoom buttons work.
5. Open a PDF: "Page 1 of 2" sits just under the page, centred.
6. Server terminal: every key a full rounded square; Arabic on a tablet keeps the grid centred.
7. Conversation picture → gallery: swipe, zoom stops swiping, Edit → draw, Undo, Crop, Done; Send closes it.
