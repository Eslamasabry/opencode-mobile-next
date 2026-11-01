# S2-KITB · kit motion parts (08 M2), sheet and dialog motion (08 M3), and the K2 parts

Date: 2026-11-01. Branch: `native/kitb-2026-11-01`, created from `claude/native-kitb-motion-tests-yab45l` at `7db496b2`
(the M0 tip after the part-A/part-B merges).

**Inputs read:**
- `native/AGENTS.md`; the slice-0 reports P1 and P7; the unit reports INTEGRATE-W1, S1-KITA1 and S1-KITA2.
- Plan 01, plan 15 (the S2-KITB row), plan 06 (§3.2, §7, §16, the K2 row) and plan 08 (§2, §3, §5.5, §5.6, §5.9.2,
  §5.10, §5.15, §7, §9.3 and §11 M2/M3).
- The kit-api specs `KitMotionParts`, `KitTabSwitcher`, `KitSearchField`, `KitJumpPill` and `KitTip`.
- The Dart sources `kit_scroll_fade.dart` and `kit_filter_chips.dart`.
- The catalog rows B-KIT-011, 014, 015, 016, 019, 022, 023, 025, 030, 031, 032, 050, 051 and 054, plus B-NAV-038 and B-NAV-073.

**Shipping state:** implemented. Tested locally on desktop and with Robolectric goldens. Committed and pushed. Not
merged. Nothing was run on an emulator or a device. No PR, no CI run, no release.

**Finish line, honestly:** the motion parts, the sheet and dialog motion pieces, and seven of the K2 parts exist. Each
has its §5.15 `Motion:` KDoc line, row-named tests, reduced-motion tests and goldens. The plan 15 row is **not fully
met**:
- Some K2 parts are not built. See §6.
- The modal transitions exist as builders, but `KitSheet`, `KitDialog` and `KitMenu` do not use them yet. Those are
  part-B files, outside this unit's write set.
- Several 06 §16.3 items are open: api specs, `KIT.md` rows, capacity scenes, previews and VPS goldens.

---

## 1. What was built (all new files under `native/ui/kit/src/`)

| File (`commonMain/…/ui/kit/`) | Parts | Motion class (manifest) |
|---|---|---|
| `motion/KitMotionBuilders.kt` | `KitPace`. Builders on `KitMotionSpecs`: `pace`, `spatialFast`, `fadeThroughOut`/`In` (83 + 167 ms), `iconTurn`, `tipFade`, `travelOrNull`, `lap`, `breath`. Under Off every builder returns `snap()`, and every builder scales with `debugTimeScale`. | — |
| `motion/KitMotionManifest.kt` | `KitMotionClass` (the 8 classes of §3.3), `KitMotionAnswer`, `KitMotionRules.answer` (the §3.3/§7 matrix as data), and `KitMotionManifest.parts` (23 entries, one class each) | — |
| `motion/KitMotionScheme.kt` | The M3 `MotionScheme` with standard numbers that snaps under Off. Not wired yet (§4 D2). | — |
| `motion/KitLeaving.kt` | internal `Modifier.kitLeaving`: a leaving child ignores touches and is cleared from semantics | — |
| `motion/KitSwap.kt` | `KitSwap` (QUICK cross-fade; no scale and no size animation; the leaving child keeps its state) and `KitSwapCount` (60 % roll by sign at Full, a cross-fade at Calm, clipped only while it rolls) | Fade / Travel |
| `motion/KitSpin.kt` | `KitSpin` and `KitChevron` (180° on `SpatialFast`). At Calm both cross-fade between the two angles. | Travel |
| `motion/KitReveal.kt` | `KitReveal`: unfolds and fades in over STANDARD, folds out over QUICK. The only layout animation. | Fold |
| `motion/KitEntrance.kt` | `KitEntrance`: a STANDARD fade, plus an 8 dp rise at Full only. `play = false` shows it at once. | Travel |
| `motion/KitPresence.kt` | `KitPresence`: the slot width opens on `SpatialFast` with a QUICK fade. At Calm it only fades and the slot jumps. | Travel |
| `motion/KitAnimatedRows.kt` | `KitAnimatedRows`: a short list. The first load plays nothing. A removed row keeps its place until its fold ends. | Fold |
| `motion/KitAnimatedValue.kt` | `KitAnimatedValue` and `animateKitValue`: the first value shows at once; `jump` and Off show the new value on the same frame | Fade |
| `motion/KitDim.kt` | `Modifier.kitDim`, `KitDimLevel` (Stale 0.60, Disabled 0.38) and `kitDimAlpha` | Fade |
| `motion/KitRefresh.kt` | `KitRefresh`, built on M3 `PullToRefreshBox` with the portal indicator: two brackets that draw in with the pull (Flutter `easeOutCubic`), a spark when armed, and a breath plus a circling spark while refreshing (Full only, never in a still frame). Done shrinks from `MARK_FROM` (instant at Off). Also a TalkBack "Refresh" custom action, Ctrl+R / F5, and `RefreshPhase` / `RefreshRules`. No haptic (D8-3). | Follow + AmbientLoop |
| `frame/KitJumpPill.kt` | `KitJumpPill` (a solid pill with words, an 8 dp rise toward its own edge, a QUICK exit), `KitJumpEdge`, `KitJumpPillLayer`, `kitJumpLatestLabel(newCount)` from `:ui:words`, and `JumpPillRules` (480 dp compared after `toPx`) | Travel |
| `frame/KitScrollFade.kt` | `Modifier.kitScrollFade(ScrollState, width)`: a DstIn gradient read in the draw phase. An edge fades only when there is more beyond it. Mirrored in RTL; the outer third of the fade is clear. Also `ScrollFadeRules` and `KitFadeEdges`. | Follow |
| `nav/KitTabStrip.kt` | `KitTab` and `KitTabStrip` (2–8 tabs; a `surface3` pill indicator drawn behind the chosen tab that glides on `SpatialFast` at Full only; counts roll; null counts show no number; a needs-you dot with its count; arrow keys move in reading order; the chosen tab is brought into view; faded edges). Also `TabRules`, `TabArrow` and `TabBodyMotion`. | Travel |
| `nav/KitTabSwitcher.kt` | `KitTabSwitcher(index, bodies)`: bodies keep their state through a `SaveableStateHolder`. Full fades through; Calm cross-fades over QUICK; Off is instant. `KitTabSwitcher(tabs, index, onSelected, bodies)` puts the strip over the body and refuses a mismatched count in debug. | Fade |
| `input/KitSearchField.kt` | `KitSearchField`: a 300 ms debounce through `collectLatest`, immediate on Clear and on Enter; the count line appears only after typing settles, as a polite live region; a partial line; an active filter as a removable chip; Esc and the back gesture clear first; a disabled field must give a reason. Also `KitSearchNoMatch` (the query isolated with FSI/PDI, Clear search, one optional action), `SearchRules` and `SearchEscape`. | Fade |
| `chip/KitFilterChips.kt` | `KitFilterChips`: one sideways line on the gutter with a gutter + 32 dp fade. Also `FilterChipsRules`. | Follow |
| `teaching/KitTip.kt` | `KitTip`: a popup anchored to its content. It sits above the anchor when the anchor is below the top quarter, otherwise below. It aligns to the anchor's nearer edge, takes no focus, fades on any touch or after `TIP_HOLD`, and rises 4 dp toward the anchor at Full. Also `KitTipBubble` (≤ 280 dp, polite live region) and `TipRules`. | Travel |
| `modal/KitModalMotion.kt` | `KitModalMotion`: dialog enter (STANDARD fade + 8 dp rise) and exit (QUICK fade); menu enter (QUICK fade + 8 dp drop, or rise when the menu opens above) and exit; sheet slide on `Spatial`; scrim fades. All `None` under Off. `KitModalMotionRules` sets `INITIAL_SCALE = 1` (MOT-2). | Travel / Settle |
| `modal/KitSheetStep.kt` | `KitSheetStep`: QUICK out, STANDARD in, height on `Spatial`, clipped | Fold |
| `modal/KitSideSheet.kt` | `KitSideSheet`: an end-edge sheet. It slides on `LargeTravel`, mirrored in RTL. Drag follows the finger and settles by velocity or by half its width. The back gesture previews 10 % toward the edge, completion asks to close, and cancel returns. A `DIM_MOST` scrim closes it when tapped. Under Off it snaps and Back commits only. Also `SideSheetRules` and `SideSheetValue`. | Follow + Settle |

Every public part has a KDoc with `States / Text / Cap / Motion / Replaces` lines. `KitRefresh` adds a `Hidden` line.
Kit words come from `:ui:words`: `kitJumpLatest`, `kitJumpNewLatest`, `kitSearch*`, `kitTabLabel` and `workRefresh`.

## 2. Catalog rows covered (tests named after the rows)

- `KitbRulesTest`: 17 tests in `commonTest`, run on desktop.
- `KitbDesktopTest`: 26 tests, `runComposeUiTest` with the clock driven by hand. A child is counted as composed through
  a `DisposableEffect`, because a leaving child is hidden from semantics by design.
- `KitbGoldensTest`: 9 scenes × 4 variants = 36 PNGs.

| Row | What is proven |
|---|---|
| B-KIT-023 | Every S2-KITB builder snaps under Off, and Full uses the plan's numbers (QUICK, STANDARD, TIP_FADE, 83/167 fade-through, `SpatialFast`). Calm does not travel. `KitSwap`, `KitSwapCount`, `KitReveal`, `KitPresence`, `KitAnimatedValue`, `KitAnimatedRows` and `KitEntrance` are final after **one frame** under Off. Under Full the leaving child stays for its fade, out of semantics. The first build is final. `kitDim` alphas. The jump pill's words, rise and hidden state. Scroll-fade edges, stops and RTL. |
| B-KIT-030 | `worthShowing(8)` is false and `(9)` is true. `onSearch` runs once after 300 ms, not on each keystroke, and at once on Clear. The count and partial lines show only after typing settles. Esc and the back gesture clear first. No-match isolates the query, and Clear calls back. A disabled field without a reason is refused. |
| B-KIT-050 | 2–8 tabs; bodies must match, or the switcher is refused in debug; a one-tab strip is refused. A tap on a different tab calls once; the chosen tab does nothing. A loading count shows no number. The needs-you count shows. A body keeps its saved state across switches. Arrows move in reading order and are mirrored in RTL. The body switch fades through at Full, cross-fades at Calm and is instant at Off (rule and composed). |
| B-KIT-054 | The §3.3 class × level matrix as data. All 23 manifest entries each have exactly one class. Refresh phases (pull, arm, release before armed returns, refresh, done holds) and bracket drawing. Refresh never loops at Calm, at Off or in a still frame. The TalkBack "Refresh" action runs it. |
| B-NAV-038 (the drawing half) | Tip placement and the 280 dp cap; the rise at Full only; the tip fades by itself after `TIP_HOLD` and calls `onDismissed` once. The once-or-twice, cooldown and one-at-a-time rules belong to `TipController` (S2-TEACH). |
| B-KIT-032 | Side-sheet anchors are mirrored in RTL; the preview moves 10 %; settle by velocity and by half its width; drag is clamped. The side sheet closes from the scrim and from Back, and leaves at once under Off. A sheet step is instant under Off and cross-fades under Full. |
| B-KIT-016, B-KIT-022 | A dialog rises and a menu drops or rises at Full only; there is no scale. Every `KitModalMotion` transition is `None` under Off. |

Not covered by this unit:
- B-KIT-011, 014, 015, 019, 025, 031 and 051. These are the motion clauses of part-B parts (button working cross-fade,
  switch, segmented indicator, icon-button turn, progress tween, press). Those files are part B's. The builders they
  need now exist (`spatialFast`, `iconTurn`, `pace`).
- B-NAV-073 (`KitArrival`). Not built.

## 3. Commands and results

Run from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and `ANDROID_HOME=/opt/android-sdk`.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :ui:kit:compileKotlinDesktop` | BUILD SUCCESSFUL |
| 2 | `./gradlew :ui:kit:desktopTest --tests '*Kitb*'` | 43 / 43: `KitbRulesTest` 17, `KitbDesktopTest` 26 |
| 3 | `./gradlew :ui:kit:desktopTest` | 50 / 50: 43 new plus P7's `KitSeamsDesktopTest` 7. Run with part A's and part B's tests set aside; see the note below. |
| 4 | `./gradlew :ui:kit:recordRoborazziAndroidHostTest --tests '*KitbGoldensTest*'` | 36 PNGs under `ui/kit/src/screenshots/{KitMotionParts,KitTabSwitcher,KitSearchField,KitFilterChips,KitJumpPill,KitTip,KitRefresh,KitSheetStep,KitSideSheet}/` |
| 5 | `./gradlew :ui:kit:verifyRoborazziAndroidHostTest` | `KitbGoldensTest` 36/36, `KitbRulesTest` 17/17 (on Android host), `TextScaleCapTest` 3/3, `AndroidKitPlatformTest` 2/2. Five failures, none from this unit (see below). |

The five failures in run 5:
- `KitGoldensTest.kitSheet` ×4 are P7 goldens. Part A's `KitScreen` changed them; S1-KITA1 D9 says they are red until
  the sheet owner re-records them.
- `KitFrostFallbackGoldenTest` failed because Robolectric could not download its SDK jar through the proxy.

**Note on the kit's test source set (important for the coordinator):** on this tree, `:ui:kit`'s common and desktop
test sources **do not compile**. Part A's and part B's tests (`PartAScenes.kt`, `PartBScenes.kt`, `PartBRulesTest.kt`,
`PartADesktopTest.kt` and `PartBDesktopTest.kt`) still use the old `Failure.Net.*` and `Failure.Storage`, which
INTEGRATE-W1 removed. This is the INTEGRATE-W2 gap.

To run my own tests, I moved those 5 files, plus `PartAGoldensTest.kt` and `PartBGoldensTest.kt` (which depend on the
scenes), out of the tree, ran the tasks, and restored them with `git checkout`. **Nothing of theirs is changed or
committed.**

My tests use only the flat contract. They do not touch `Failure` at all.

Goldens I checked by eye:
- `KitTabSwitcher` light-en;
- `KitSearchField` dark-en and dark-ar (magnifier at the start, Clear at the end, count line at the start in RTL);
- `KitMotionParts` light-en;
- `KitJumpPill` light-ar;
- `KitRefresh` dark-en;
- `KitFilterChips` light-en (the fifth chip fades at the end edge);
- `KitSideSheet` light-ar (the sheet on the left, the end side in RTL, with the scrim over the page).

That review found one defect, which is fixed: the first recording had no ground in the scenes, so the dark variants
showed a white page.

The Robolectric `android-all-instrumented` jar for 17-robolectric-15733970-i7 was fetched into `~/.m2`. Its SHA-1
(`42dc8bf9…5b74`) matches the mirror's `.sha1`.

**Environment (container only, not in the repo):**
- commandlinetools 16111833 with `platform-tools`, `platforms;android-37.1` and `build-tools;37.0.0`;
- `native/local.properties` (gitignored);
- `~/.gradle/init.d/central-mirror.gradle.kts`, which puts Google's Central mirror first because the foojay plugin could
  not be resolved through the default repositories;
- `~/.gradle/gradle.properties` listing JDK 21 and 17 (`openjdk-17-jdk-headless` installed with apt).

A privacy grep of the diff (emails and private IP ranges) found nothing.

## 4. Deviations

| # | Plan says | Built | Why |
|---|---|---|---|
| D1 | All builders live in `KitMotion.kt`; rule R1 exempts only `KitMotion.kt`, `KitMotionScheme.kt`, `KitEasing.kt` and `ShellMotion.kt` | The extra builders are extension functions in `motion/KitMotionBuilders.kt`. `ICON_TURN_MS` and `FADE_THROUGH_SPLIT` are local constants because `MotionTokens` lacks them. | `KitMotion.kt` and `MotionTokens.kt` are K0 files, and my write set is new kit files only. The kit owner folds these in at the contract freeze. Until then `KitMotionBuilders.kt` must be the fifth R1-exempt file. |
| D2 | `KitTheme` passes `KitMotionScheme(off)` to `MaterialTheme` | `KitMotionScheme` exists but is not wired in | `KitTheme.kt` is a part-A file. Until it is wired, the M3 pull-to-refresh state's own settle does not snap under an in-app Off; the system "remove animations" path still applies. |
| D3 | M3: `KitSheetHost`, `KitSheet` and `KitDialog` move per §5.5 (sheet slide on `Spatial`, dialog fade + rise, no scale; an Off back handler) | `KitModalMotion` (the transitions), `KitSheetStep` and `KitSideSheet` | `KitSheet.kt`, `KitDialog.kt` and `KitMenu.kt` are part-B files. They still use `scaleIn(0.96)`, which MOT-2 forbids. The part-B owner swaps in `KitModalMotion.*` and, from Expanded up, `KitSideSheet` for `Full` sheets. |
| D4 | Plan 06 says the side sheet uses `AnchoredDraggableState` | `Animatable` + `draggable` + `SideSheetRules.settle`, with the same anchors and the same behaviour | Avoids the moving `AnchoredDraggable` API; the rules are pure and tested. |
| D5 | The search field builds on `KitField` chrome and has a "Filter" menu button | Its own field chrome (`BasicTextField` + `TextFieldState`, the same tokens). No filter menu: the active filter shows as a chip only. | `KitField` has no leading or trailing slots. The menu button needs `KitMenu` anchoring, which can come in a follow-up. The remove-filter label is a parameter because `kitSearchRemoveFilter` is not in `:ui:words`. |
| D6 | Needs-you on a tab uses `KitNeedsYou.badge(count)` | A private amber dot with its count, on `attentionSurface` | Part B's `KitNeedsYou` has no badge form. |
| D7 | `KitEntrance` plays on mount | The same, plus `play` and `key` | `play = false` lets a part that was there from the start show at once (R8). |
| D8 | Plan 08's `KitSpin` chevron uses `SpatialFast`; the kit-api spec says STANDARD on emphasized | `SpatialFast`, with a cross-fade at Calm | Plan 08 is the newer, binding text. |
| D9 | The jump pill clears `KitBottomInset` | `KitJumpPillLayer(bottomInset: Dp)` | `KitInsets` is not built. |
| D10 | Goldens are recorded on the VPS | Recorded in this container | As P7 and part B did. |

## 5. Catalog requests

- **Version catalog:** none.
- **`MotionTokens`** (the K0 owner): add `ICON_TURN_MS = 200` and `FADE_THROUGH_SPLIT = 1/3` (both named in plan 08
  §2.7), and fold `KitMotionBuilders.kt` into `KitMotionSpecs`.
- **`:ui:words`** (the l10n owner): add `kitSearchRemoveFilter` ("Remove filter {name}") and a kit-owned "Refresh" word.
  `KitRefresh` uses `workRefresh` today.
- **Konsist R1** (S0-GATES): exempt `motion/KitMotionBuilders.kt` until D1 is folded.

## 6. Open problems (not done)

1. **K2 parts not built:**
   - last known (`KitLastKnownRow`);
   - server trust;
   - top-bar pill;
   - scope;
   - link (`KitLinkScope`);
   - typing guard;
   - link scope;
   - scroll area.
   M2 parts not built: `KitArrival` (B-NAV-073), `KitFeedList` motion, `KitSwipeAction` and `KitTour`.
   The §5.15 rows inside part-A/B/kit-chat files are also not done (they are not my files).
2. **Wiring:**
   - `KitMotionScheme` into `KitTheme` (D2);
   - `KitModalMotion` into `KitSheet`, `KitDialog`, `KitMenu` and `KitUndoBar`, and `KitSideSheet` into the sheet host
     (D3);
   - `KitChevron` into `KitExpandRow`, and `KitReveal` into the status-line fold.
3. **06 §16.3 items not done:**
   - `api/<Part>.md` specs and `KIT.md` rows;
   - capacity scenes (280/412/1600 dp × 1.0/1.3/2.0 × LTR/RTL);
   - previews;
   - `AccessibilityRulesTest`;
   - the frame-capture series of §9.4 (`*_0/125/250ms`); only settled frames are recorded;
   - `ReducedMotionSettlesTest` under `MotionDurationScale(0)`. The Off level is tested; the system-scale path is not.
4. **Not checked on an emulator:**
   - the M3 pull-to-refresh feel and its threshold;
   - the side sheet's drag direction in RTL (the drag deltas are physical);
   - the tip `Popup`'s outside-touch dismissal on Android.
5. **Goldens:** recorded in the container. Re-record them on the VPS.
6. **Test source set:** the kit's common and desktop tests do not compile until part A's and part B's tests move to the
   flat `Failure` (INTEGRATE-W2). See §3.
