<!-- Planner's output, 2026-11-11, read-only study of rc-master at 4995a248f2. Coordinator's decisions are in DECISIONS.md. -->

# Step 4 build plan: the body runs under a floating liquid bar

> Built 2026-11-11 on native/glass-floating-bar. Where this plan was wrong or the build departs from it, see "As built,
> step 4" at the end of `../glass-in-the-kit.md`.

All line numbers refer to `~/Storage/wt/rc-master/native` (branch native/fix-3.0.1, HEAD 4995a248f2). I read it and changed nothing.

## 1. How it works today

`KitScreen` (`ui/kit/.../frame/KitScreen.kt`) has two layouts.

**Overlay layout (only the chat uses it)** (lines 301-475). It is used when `overlayChrome` is set and there is no `search`, no `header` and no loading line.
- **Recording:** "recording" means a copy of what is drawn, which the glass bends. The chat makes it itself: `rememberKitFrostState()` (ChatScreen.kt:453) and `kitFrostSource(frost)` (ChatScreen.kt:896).
- **Liquid bar:** the bar gets its lens face from `kitLiquidFace(LiquidRole.Bar, frost, …)` (KitScreen.kt:318) and is drawn by `KitLiquidBarPane` (440).
- **Status bar and insets:** the body runs under the status bar because only the side and bottom insets are applied (378); the bar is pushed down by `statusPx` (429).
- **Resting position:** at rest the body is padded by `LocalChromePadding` (349-369). The body must read it itself through `kitScreenChromePadding()`.
- **Fade:** `kitScrollEdges` (385-414) clears the content under half the status bar and fades it over 12 dp. `KitStatusEdge` blurs it there (419-422).
- **Keyboard:** the insets include the keyboard but are read through derived values (339-341).
- **Bottom block:** it floats, and the body fades out above it.

**Stacked layout (every other page)** (476-653).
- **Insets:** the whole Column is padded by the insets, keyboard included (509).
- **"Runs" path:** a body runs under the bar only when `runs = frost == null && topBar != null && header != null && kitLiquidGlassOn()` (483).
- **Recording:** the kit records the body (`rememberKitLiquidSource()` at 481, `kitLiquidSource(under)` at 568). It records only while a pane that the budget has granted is using it.
- **Lift:** `kitRunUnderBar` (KitLiquidGlass.kt:698) lifts the body by `runPx`, which is the bar plus the status bar (494-506).
- **Resting position:** a `Spacer(runDp)` placed before the header (604) holds the content below the bar at rest. Because the spacer belongs to the scrolling header (`KitScrollingHeaderState`), it slides away first when the page scrolls.
- **Fade:** `drawStatusEdge` plus `kitScrollEdges`, scaled by `runShare` (549-566). `KitScrollingHeaderFrame` adds a 12 dp fade at the top.
- **Status line or search:** either one sets `runTarget = 0` (495), so the body eases back below the bar.
- **Bottom block:** it sits below the body and is never overlaid (632-651).

**When a part gets the lens.** `kitLiquidOn` (KitLiquidGlass.kt:111) is true only under all of these: Glass style, no battery saver, no reduced motion, no high contrast, Frost not Off, a glass look that has a lens, and Android 13+ (`TIRAMISU`, AndroidKitPlatform.kt:125). Note that this is 13, not 12. Desktop is always false.

**Lens budget.** `LiquidEdge.grant` (LiquidEdge.kt:418) counts each pane as 1. A kit recording costs 1 once; a feature's recording costs nothing. A part left outside the budget is drawn as frost in the same shape.

**Chips.** `KitChip` becomes liquid only when `LocalKitLiquidChrome` is provided (KitChip.kt:~130). `KitStatusPill` has no liquid branch at all.

**Home today.** It passes `topBar = null` and puts the title, the chips row and everything else in `header` (ChatsScreen.kt:244-286). With no bar, `runs` is never true. The + button reads the list's own recording, `listGlass` (229, 296, 507).

## 2. The smallest kit change

Add one parameter, `floatingBar: KitFloatingBar? = null`:

```kotlin
public class KitFloatingBar(
    val scrollsElsewhere: Boolean = false,   // when there is no lens: lay bar + pills as the scrolling header (home's look today)
    val source: KitFrostState? = null,       // the feature's own recording; null = the kit records the body
    val pills: (@Composable () -> Unit)? = null,
)
```

Changes in the stacked layout:
- **Condition.** Set `floats = floatingBar != null && topPart != null && kitLiquidGlassOn() && !window.short`, and `runs = old || floats` (483).
- **Headerless pages.** When `floats` is true and there is no header, the frame makes a header that holds only the spacer (601-608). At rest the layout is then identical to today. The body only grows as the spacer scrolls away.
- **Source.** Use `source ?: under` for the bar face (487-488), the words host (521) and the status-edge layer (556). Skip `kitLiquidSource(under)` when a `source` is given (568).
- **Pills.** Restructure the bar box (512-529) so the capsule covers only `topPart`. The pills sit below it, outside the capsule, with `LocalKitLiquidChrome provides source`. `barPx` measures both.
- **Fallback.** When there is no lens and `scrollsElsewhere` is set, the frame draws no bar. Instead the header becomes `topPart`, then the pills, then the page's header. When there is no lens and `scrollsElsewhere` is false, the frame draws today's pinned bar.

**Why Solid, Soft, Brutal, Paper and Signal cannot change:** `floats` requires `kitLiquidGlassOn()`, and that requires the Glass style. When `floats` is false, every branch runs today's code. The `frost` parameter keeps its meaning. A new pixel-equality test proves it (section 7).

## 3. Home

- **Bar:** `KitPageHeading` (title, the "2 need you" line, Search, Settings) moves unchanged into `topBar`, inside the capsule.
- **Pills:** the existing `HeaderRow` block (ChatsScreen.kt:265-272) becomes `pills`.
  - `KitChip` (the project chip and "Running") reads `LocalKitLiquidChrome`, so both become liquid Pills with no change to the chip.
  - `KitStatusPill` ("This phone") needs the same 8-line liquid branch, a kit change.
- **Large title: keep it large, as in the mock.**
  - If the title collapsed, the bar's height would change on every scroll frame. That would re-lay the run and spacer and reshape the lens every frame, which is both a cost and an artifact risk.
  - The home list is short.
  - The mock the owner reacted to has it large.
  - Collapsing can be its own step later.
- **Resting position:** the first panel's label sits right below the pills, where it sits today below the chips. Section 7 has the test.
- **Lines that change in ChatsScreen.kt:**
  - 247: `topBar = null` becomes `topBar = { KitPageHeading(...) }`, using the code moved from 256-264.
  - 250-274: `header` is removed; its 265-272 content goes into the pills.
  - New argument: `floatingBar = KitFloatingBar(scrollsElsewhere = true, source = listGlass, pills = {…})`.
  - Comments at 228 and 245-248 are updated, plus one import.
  - Lines 288-525 (rows, states, + button, Undo bar) do not change.

## 4. Settings

`SettingsFrame` (SettingsScreen.kt:351-373) is one call site that serves 21 pages. The change is one line, `floatingBar = KitFloatingBar()`. It needs no pills and no source; the kit records the body.

Wide windows use `KitPaneScreen` (HubPage.kt) and stay as they are. Settings could ride a default flip with no edit at all, but see section 5.

## 5. Default or opt-in

**Recommendation: opt-in now. Ship home and Settings, judge them on the phone, then flip the default and keep an explicit opt-out list.**

A flip would affect about 113 of the 123 call sites that have a bar (135 call sites in 119 files). Eight already run under the bar because they have a header, and the chat uses the overlay layout. A flip is wrong for these classes:

| Class | Count | Why it is wrong |
|---|---|---|
| Short pages that never scroll | many | A lens over nothing, still costing 2 budget units. |
| Pages with text fields | 36 files | When a field takes focus, Compose scrolls it into view at the top of the scroll area. That area now starts under the bar, so the field can end up hidden. TalkBack has the same problem. |
| Terminals | 6 | If the terminal passes scroll on to the page, the spacer slides away and the terminal gains rows, which resizes the terminal session. `SignInTerminalScreen` and `LocalTerminalScreen` already run under the bar today because they have a header; check them. |
| Viewers, editors, scanners | about 10 | Zoom and pan take the scroll, and "fit to window" would fit to the taller area. |
| Sticky headers | none today | They would pin under the bar. |
| `KitPaneScreen` list pane | 1 | Two bars in one window cost 4 units. |
| Bottom-block screens | 39 | Fine at the top edge, since the block stays outside the run. They do overlap with the field risk above. |

## 6. Cost and risk

- **Budget on home (5 for the page):** bar over `listGlass` 1, + button 1, three pills 3, total 5. Everything fits and nothing new is recorded. Inside a sheet or menu the separate allowance of 3 is untouched.
- **Who loses the lens first:** the Undo bar (Status, step 2), then the pill that was composed last, then the + button. The pill that is composed last is not fixed: "This phone" appears after the servers are read, so it may be the first pill to lose its lens.
- **Settings:** bar 1 plus the kit recording 1.
- **Frame cost:** Settings adds one recorded layer of the body each frame it changes, plus one lens pass. Home adds two more pill lens passes. Benchmark P50/P90/P99 frame time and jank on the owner's phone class, Glass with the aurora: add a `float` scene to `GlassProofActivity` and a test to `GlassBenchmark`, measured before and after.
- **Reduced motion, saver, high contrast, Frosted look, Android 12 and below:** no lens, so the fallback, which is today's layout.
- **Background setting off:** the lens still runs, over a flat ground.
- **Right-to-left:** the run is vertical and the capsule is symmetric. Check the order of the pills.
- **Large text:** the pinned block grows and never scrolls away. On a short window the frame falls back, and tests cover text sizes 1.3 and 2.0.
- **Keyboard:** handled by the stacked insets. Home's search is a separate screen.
- **Pull to refresh (a bug to fix):** `KitRefresh`'s indicator (KitRefresh.kt:~165) anchors to the lifted top, so it is hidden under the bar. Add an `indicatorTop = { runPx }` offset. This probably affects All conversations already (I infer this; I have not seen it).
- **A jump to the top done in code** (`scrollToItem(0)`) does not bring the spacer back. This is the same as on header pages today.
- **Back-to-top or jump pill:** home has none.

## 7. Tests and renders

**Existing renders must stay pixel-identical.** Desktop has no lens, so these all go through the fallback, which makes them the proof:
- chats goldens: `chats-header-*`, `s10-home-*`, `home-app-closed-*`, `home-opening-failed-*`;
- settings goldens: `hub-*`, `s8-*`, `appearance-*`;
- `A1HomeRendersTest`, `LooksCapture`.

These may need review: `HomeJankScreenTest` (composition counts) and `KitScreenRefreshHeaderTest`.

**New kit tests** in `KitFloatingBarTest.kt`, using `ChatEdgeATest`'s `LensPlatform` (a fake lens with a blur):
- first row starts at the bottom of the bar plus pills, ±0.5 dp;
- after scrolling, content shows under the bar and is cleared above the status-bar edge;
- dragging back brings the spacer back;
- refresh indicator sits below the bar;
- budget: bar, 3 pills and the + are all granted; adding a Status part makes it the one that goes frost (also as a pure `LiquidEdge.grant` test);
- Solid, Soft, Brutal, Paper and Signal, plus Glass held (reduced motion, Frosted look, no lens), render pixel-equal with and without `floatingBar`;
- right-to-left, text size 2.0, short-window fallback.

**Feature tests:** home and Settings resting position under `LensPlatform`.

**Before and after pictures:** render home in Glass dark with `LensPlatform`, scrolled 120 dp, saved as PNGs beside the design note. They cannot show real bending, the aurora (the desktop test host does not paint it), ink flips, motion or frame cost.

## 8. Build order

| # | Commit | Kit files | Feature files |
|---|---|---|---|
| 1 | `KitFloatingBar`, `floats`, spacer header, `source` | KitScreen.kt; new frame/KitFloatingBarTest.kt | none |
| 2 | Refresh indicator offset | KitRefresh.kt, KitScreen.kt, test | none |
| 3 | Pills slot, fallback with `scrollsElsewhere`, `KitStatusPill` liquid branch | KitScreen.kt, KitStatusPill.kt, tests | none |
| 4 | Short-window guard; right-to-left and large-text tests | KitScreen.kt, tests | none |
| 5 | Home | none | ChatsScreen.kt and a new chats desktop test |
| 6 | Settings | none | SettingsScreen.kt:351-373 and a settings test |
| 7 | Benchmark scene and before/after PNGs | GlassProofActivity.kt, GlassBenchmark.kt (benchmark modules, not the kit) | none |

## 9. Questions for the owner, with recommended answers

1. **Large title or collapse?** Keep it large.
2. **Pills are a new level of chrome, which the frame's rule R9 forbids.** Allow it, limited to `KitFloatingBar`.
3. **The divider between "This phone" and the filters floats over the rows.** Keep it for the first APK and judge it on the phone.
4. **Should the Frosted look (no lens) float too?** Not in step 4.
5. **Flip the default now?** No. Flip after the phone verdict, with an opt-out list.
6. **Float the Settings pages that have fields?** Yes, but only if the focus test on Server configuration passes. Otherwise float only the Hub and Group pages.
7. **Short windows fall back to today's layout?** Yes.

### Critical Files for Implementation
- ~/Storage/wt/rc-master/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitScreen.kt
- ~/Storage/wt/rc-master/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/frame/KitStatusPill.kt
- ~/Storage/wt/rc-master/native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitRefresh.kt
- ~/Storage/wt/rc-master/native/feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt
- ~/Storage/wt/rc-master/native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/SettingsScreen.kt