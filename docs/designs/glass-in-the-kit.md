# Glass in the kit: the surface system applies the recipes, not each component

Status: v2, 2026-11-11, after three reviews of v1 (code accuracy, architecture, scope). Step 1 built (see "As built, step 1"; 1b held).

## What the owner asked

2026-11-11, on the phone, Glass style, dark, home screen: "Is this supposed to be glass?" (the list panel, the chips
and the + read as flat dark shapes with a rim; `glass-in-the-kit/phone-home-dark-today.jpg`). Then: "Create a liquid
glass skill and sprinkle the love on the app." Then, after the audit and four mockups: **"Now since we know the
recipes I want you to apply them on the kit itself not on individual components."** Later the same day: "some
outliers are using manual stuff breaking our rules of kit first."

The owner did not pick between the mockups in words. This note builds the coordinator's recommendation, mock 3
("both, lightly": `glass-in-the-kit/mock-3-both.jpg`). The mock is the reference for light and colour; the numbers in
this note rule; the owner's phone accepts or rejects the result.

## Finish line (all steps)

Under Glass, a kit part gets its glass from the kit by stating what it is. The frost numbers live in `GlassLook` and
`GlassSpec`, the lens numbers in `LiquidEdge`, and no component paints its own surface. On the owner's phone the home
screen reads as glass.

## The steps, each ending in an APK the owner judges on the phone

1. **The visible recipe** (this build): a pane that stands on its own face, a page with light in it, dividers that
   stop before the rim. Dark theme is the judging theme; light changes only where stated.
2. The fallback without a halo, the light theme's edge (after a light mock), an explicit lens request in the
   resolver, the undo bar as glass.
3. The sweep: every direct fill moves behind the resolver or onto an allowlist; a ratchet test.
4. A floating liquid bar over scrolling content as the page frame's default, home first as an opt-in
   (`mock-4-floating-bar.jpg`). Until this step the lens has nothing to bend outside the chat; say so to the owner.

Not in any step here: new joined shapes, the edge dock, the content-reactive rim. Solid, Soft, Brutal and Paper must
not change in any step: any change in their goldens fails the build.

## What exists today (checked against the code at 45485f30b4)

- One resolver: `kitSurfaceLook(KitSurfaceInput)` (`ui/kit/.../foundation/KitSurface.kt:138`), 72 calls. Under Glass
  it is fed by `GlassSpec` and `GlassLook` (`ui/design/.../style/`). `clear = isGlass && blurs && kitClearPanesOn()`
  (`KitSurface.kt:177`) picks between the clear pane and the thick fallback.
- A clear Content pane in dark: tint .16 (`GlassSpec.kt:112`); a lit corner already exists (`litLiftDark` .04) and a
  sheen over 30 % of the height, at most 40 dp; a rim drawn as a two-stop gradient from `edgeStart` to `edgeEnd`
  (`KitSurfaceGlass.kt:471-492`) with `PANE_RIM_START_DARK` .24 and `PANE_RIM_END_DARK` .07, light .60 and .16
  (`KitGlassPanes.kt:88-91`, internal constants, not in `GlassLook`, shared by the Default, Frosted and Smoked looks
  and by chosen rows). The specular (.80 over 120 dp at the top start) already overpaints the top-start rim.
- The page: `AuroraImage.CHROMA_DARK` .14, `CHROMA_LIGHT` .12 (`AuroraImage.kt:34-35`). Chroma also feeds the "spots"
  the kit uses to keep words readable (`spotsOf`, `clearSpotsOf`, `KitLiveBackground.kt:116,201`,
  `LiquidEdge.themeSpots`), so a brighter page makes panes and liquid faces thicker (`GlassFinish.thickened`).
- The Background setting is off by default (`Appearance.kt:55`). With it off (and under battery saver, reduced
  motion, high contrast, below Android 12, and in every desktop render) Glass is the thick fallback: opacity .42 to
  .62 by role and a 32 dp shadow on Content, Floating and Sheet (`GlassSpec.kt:156-262`). The owner's phone has it on.
- `SurfaceRole.Floating` is already used by eight parts (`KitFab`, `KitComposerBox`, `KitDialog`,
  `KitFloatingPanel`, `KitNavRail`, `KitZoom`, `KitTerminalView`, `KitWorkGraph`) and resolves to a frost pane with
  a shadow. A lens needs three things the resolver does not have: a recording to bend, a claim on the window's
  budget, and a rounded shape (`KitLiquidGlass.kt:202-313`).
- Lens faces are wired by hand: about nine `kitLiquidFace` calls plus the menu and sheet wrappers; numbers in
  `LiquidEdge.baseRecipe`.
- 59 direct fills (`background(colors.surfaceN)` or `colors.ground`) in 41 kit files. Some are already behind an
  `isSolid` check or are `?:` fallbacks of the resolver; some must stay opaque (swipe rows, viewer grounds, diff
  grounds, the theme preview).
- The home panel's row lines come from the private `HairlineColumn` (`KitRowGroup.kt:133-160`); `KitRowPanel` passes
  `endInset = 0.dp` (`KitRecentRow.kt:327`), so they run into the rim.
- `KitToastHost` has no caller in the app and `LocalKitToasts` no provider: the toast is never shown.

Full audit: `glass-in-the-kit/AUDIT-2026-11-11.md`. Rules: the `liquid-glass` skill (`~/.claude/skills/liquid-glass`).

## Step 1. The visible recipe (build now)

Owner of the edits: the glass builder, in `ui/design` and `ui/kit` only. `feature/chats/.../ChatsScreen.kt` is being
edited by another builder: do not touch it.

**1a. A lit bottom end on every clear pane.** One light, two lit arcs: the rim is brightest where the edge faces
the top start and the bottom end, as on the lens panes.

- `SurfaceDecoration` gets one new field for the rim between the two lit arcs (the "rest" of the rim), and
  `drawGlassFace` draws three stops: start, rest, end. `SurfaceFade.between` and `rememberSurfaceFade` get a rule
  for the new field.
- The four pane rim numbers move from internal constants in `ui/kit` into `GlassLook` fields, so a look owns them.
  Default, dark: start .24 (unchanged; the specular sits on it), rest .10, end .30. Default, light: unchanged in
  this step (start .60, end .16, rest follows today's gradient). Frosted and Smoked keep today's values exactly:
  give them fields equal to today's numbers.
- Chosen rows keep their accent rim as it is.

**1b. A page with light in it.** In the painted aurora plate only (`KitLiveBackground.kt:144-148`), dark theme:

- `CHROMA_DARK` .14 to .20. `CHROMA_LIGHT` unchanged.
- Two fixed lights, defined in fractions of the page so they hold on every screen and width, in the accent's hue,
  soft-edged (full at the centre, fading over the outer 55 % of the radius), still while the aurora drifts under
  them: one at 15 % across and 25 % down with a radius of 45 % of the width, one at 70 % across and 55 % down with a
  radius of 40 % of the width. Peak added luma about .10 each. The light theme gets none in this step.
- Words do not pay: the spots that feed `thickened` are capped at today's values, so no pane or liquid face is
  thicker than today. The existing contrast tests must pass with the lights included, measured at the brightest
  light; if one fails, lower the light, do not thicken a pane.
- Shader looks and look thumbnails are unchanged.

**1c. Dividers stop before the rim under Glass.** `KitRowPanel` passes an end inset of one gutter to its hairline
column when the style is Glass; the other styles keep 0. Fixed in the kit; `ChatsScreen.kt` is not edited, and
`prominentDividers` keeps its meaning.

**Not in step 1:** the sheen band (40 dp is close enough to the mock), a face lift (one exists), the fallback, the
light theme's edge, the + button (it already has a lens and waits for content under it in step 4), the toast.

**Checks for step 1**

1. `:ui:design` and `:ui:kit` tests updated for the new field and numbers: the rim's three stops in dark, Frosted
   and Smoked equal to today, the fade rule, chroma and the two lights' positions, the spots cap (panes no thicker
   than today), the divider inset under Glass and 0 elsewhere. Tests that pin old values are changed with a line
   saying why (`GlassPanesTest`, `GlassSpecParityTest`, `KitRowPanelProminentTest`, `GlassLiveTest`,
   `LiveReachTest`, as they apply).
2. Goldens: only Glass goldens may change (desktop shows the fallback, so expect the divider inset and little else);
   look at each one that changes.
3. A picture before the APK: a desktop render test that forces the clear pane over a painted plate (a home-like
   panel with rows, dark), before and after, saved as PNGs next to this note. Desktop has no lens; that is fine for
   in-flow panes. If the clear state cannot be forced on desktop within half an hour, say so and skip it.
4. The APK is built by the coordinator on GitHub from the combined line; the owner's phone is the first real look.

### As built, step 1 (2026-11-11, branch `native/liquid-merge-one-body`)

**1a, as the note says.** `SurfaceDecoration.edgeRest` holds the rim between its two lit ends (null: the old straight
fade); `drawGlassFace` and `kitDecorated` draw start, rest, end as three stops along the same top-start to bottom-end
gradient; `SurfaceFade.between` fades a rest to and from a straight rim through the straight rim's own middle (a rim
change also restarts `rememberSurfaceFade`, which compares whole decorations, so it needed no code of its own). The
`PANE_RIM_*` constants are gone: `GlassLook` has `paneRimStart/Rest/End` for dark and light. Default: dark .24 / .10 /
.30, light .60 / straight / .16. Frosted: dark .24 / straight / .07, light .60 / straight / .16 (the old numbers).
Smoked is `GlassLook.Default` with Frosted's rim, so it keeps the old rim too. Chosen rows keep their accent rim (no
rest). Seen in the desktop render: the list panel's bottom-end corner and edge are now lit, the top-end and bottom-start
corners quiet. On a wide short pane (All conversations) the fade runs almost straight across, so both round ends light
up, the right one along its top as well; that is the gradient's geometry, not a new number.

**1b, not shipped as the note's numbers: the rule it carries does not allow them.** Built and wired: `CHROMA_DARK`
separate from `CHROMA_DARK_QUIET` (.14, Flutter's) which the look thumbnails keep; `spotsChromaFor` caps the spots at the
quiet plate's; the two lights (`AuroraImage.LIGHTS_DARK`, 15 % / 25 % r 45 %, 70 % / 55 % r 40 %), full over the inner
45 % with a smoothstep fade, in the accent's hue, drawn additively over the plate in page fractions and still while it
drifts. Measured on the VPS (`GlassInTheKitStep1Test`): today's clear panes have 0.012 of contrast to spare at chroma .14.
At .15 a Frosted Opencode pane's secondary words read 4.51 against 4.54, at .20 4.43; with chroma .20 and the .10 lights
(1.32 of one light's peak where they meet on a wide page) the home's Glossy list panel's secondary words read 3.63
against 4.54. The most light every pane reads under is about .001 of luma. The plate adds colour, not brightness, so
the panes' tint is fitted to the ground's brightness and any brightness behind them costs words. Following "lower the
light, do not thicken a pane", `CHROMA_DARK` stays .14 and `LIGHT_PEAK_LUMA` is 0: the page looks as before. Turning
either on is one number, and is the owner's call: a brighter page needs thicker panes. The test's report: for the
note's page the home's list panel (Glossy Content/Surface1) would thicken by a median .42 of tint over the packs, and
some pane by up to .60 (Glossy Solarized Content/Surface2, .16 to .76): no longer clear glass. So the light, if wanted,
needs another answer for the words than a thicker tint. `render-1b-as-noted-dark.png` shows the page with the note's
numbers, for that call; its words fail the floor over the brightest point.

**1b', brighter secondary ink (coordinator, 2026-11-11), measured and held.** Wired: `GlassSpec.PAGE_SECOND_INK_LIFT`
moves text2 and text3 toward text1 (`GlassSpec.liftedInks`), only on a dark Glass page whose painted aurora the panes
are clear over (`GlassSpec.pageInkLift`, provided by `KitLiveBackground` as `LocalKitPageInkLift`, applied by KitText's
tone ink); a liquid pane's `SECOND_INK_ON_GLASS` and the page lift never stack, the larger applies. Pane tints are still
measured with the plain inks, so no pane changes thickness. Measured at chroma .20 and the brightest point (1.32 of a
light's peak), with the panes as thick as today: the words read under a light peak of at most .011 at a lift of .05,
.026 at .20, .040 at .30, .051 at .40, .061 at .50; the secondary ink stays at least 1.3 times below the primary on the
pane's face only up to a lift of .40 (1.30; 1.27 at .45, 1.23 at .50). The icons and track edges, which keep their
inks (3:1 floor), read under a peak of at most .030 (tightest: Glossy Ocean Control/Surface3, `#6D7B8A` over
`#194B65`, 2.23 against 3.04 at .10). So no (lift, light) pair reaches a .05 light with every clear pane's words and
icons at the floor; words alone allow lift .40 with a .05 light, at the edge of the hierarchy limit. Per the
coordinator's rule (nothing passes at .05: stop and report), the lift ships at 0 with the page held. Raising the page
needs an answer for the icons and track edges too.

**1c, as the note says.** `StyleTokens.dividerEndGutters` (Glass 1, every other style 0) sets `KitRowPanel`'s hairline
end inset to one gutter. In the render the lines end where the time column ends, clear of the rim.

**Checks.** Design and kit tests on the VPS; renders `render-before-dark.png` (base numbers) and
`render-after-dark.png` (as built: 1a and 1c, the page held), both forced clear on desktop (Motion Calm, not Off, which is reduced motion and holds
the frost) by `GlassRecipeRenders`. No golden compared anywhere uses Glass with these parts, so no golden changed.

## Step 2 (after the owner has judged step 1)

- **Fallback:** Content and Control panes keep the crisp face (sheen, rim) at their thick opacity with no shadow
  halo; Floating and Sheet keep their shadow until looked at separately. Departs from the frozen Flutter look on
  purpose. Needs the owner's yes (asked 2026-11-11, recommendation: no halo).
- **Light theme edge:** a shade hairline (ink at .08 all round, .14 at the bottom end) under the white specular, on
  every clear pane. A light-theme mock on a real light screenshot comes first. This departs from "lit at the bottom
  end": on a near-white page light does not show, shade does.
- **An explicit lens request:** `KitSurfaceInput` gains `liquid: LiquidRole? = null`; a part that floats over
  content passes its kind (`Status`, `Pill`, ...). The resolver returns the lens face when a recording is in scope
  (a new overlay-level local) and the budget grants it, else today's `Floating` pane. Role alone does not choose the
  recipe: the eight current `Floating` users are unchanged. Joins, touch light and ink-flip hosts attach to the
  returned face as modifiers. First users: the undo bar (mounted in the overlay layer so it does not record
  itself), then the home chips and the + button as the proof.
- The toast: mount it or delete it; a separate decision.
- Whether choosing Glass should turn the Background on: a question for the owner, with a mock of Glass with the
  Background off.

## Step 3. The sweep

Allowlist first, with a one-line reason each (code and diff grounds, viewer and scanner grounds, swipe rows that
must cover their action, the theme preview, tiny marks). Then move the rest behind the resolver in batches by role,
with the other styles' goldens as the net; anything whose role is not obvious goes on the allowlist, not guessed. A
ratchet test counts direct fills (including `colors.surfaceN.copy(...)`) and lets the number only go down.

## Risks

- A brighter page lowers word contrast. Guard: the spots cap and the contrast tests at the brightest light.
- The new rim field touches every pane of every Glass look. Guard: Frosted and Smoked pinned to today's values by
  test; other styles do not read it.
- Desktop cannot show the clear pane by default, so tests can pass while the look is wrong. Guard: check 3, then
  the phone.

## As built, step 4 (2026-11-11, branch native/glass-floating-bar)

Plan: `glass-in-the-kit/STEP4-PLAN.md`. Opt-in, home and the Settings frame only.

**What the person sees, where the lens is drawn** (Glass, a look with a lens, Android 13+, no battery saver, no
reduced motion, no high contrast, a window at least 480 dp tall):

- Home: the title row ("Conversations", the "need you" line, Search, Settings) and the chips row (server pill,
  divider, filters) lie on ONE liquid pane pinned under the status bar: one body of glass, the bar's shape and radius,
  one bottom edge (coordinator 2026-11-11, replacing the first build's separate pills, whose gaps let the list show
  through). The chips, the status pill and the divider are controls on the pane: no lens and no frost of their own
  (the pane is the root of their glass stack), their outlined control faces kept. At rest the list starts exactly where
  it did (tested to half a dp). Pulled up, the list runs on under the pane and the status bar; nothing of it shows
  above the pane's bottom edge except through the glass (it is cleared beside the pane's rounded ends and in its 6 dp
  margins); under the status bar it dissolves into a blur of itself, as the chat does (chat edge A). The title stays
  large; nothing collapses.
- Settings (every page of the narrow-window Settings frame): the bar with Back and the title is a liquid capsule; the
  page scrolls on under it. The kit records the page for it.
- The refresh disc comes out below the pane, not under it.
- A part brought into view while it would rest under the bar (a focused field, a keyboard or TalkBack move) brings the
  page back to its resting layout, so it lies below the bar.
- A tap on the floating chrome (the title, the gaps between the chips) is the chrome's: it never reaches a row that has
  scrolled under it. A drag that starts on the chrome does not scroll the page any more.

**Everywhere else** the page is today's: every other style, Glass without a lens (reduced motion, the Frosted look,
battery saver, high contrast, Android 12 and below, desktop) and short windows. Home's title and chips are then the
scrolling header again; Settings keeps its pinned bar. Every screen that does not pass the option is unchanged.

**Kit API:** `KitScreen(floatingBar: KitFloatingBar? = null)`; `KitFloatingBar(scrollsElsewhere, source, below)`
(`ui/kit/.../frame/KitFloatingBar.kt`; `below` is a second row inside the bar's pane); `KitRefresh(indicatorTopPx)`.
The first build's liquid branch in `KitStatusPill` is reverted: nothing uses it now. No new glass numbers.

**Lens budget on home** (tested): the pane 1, the + 1 = 2 of 5; the list's recording is the feature's and costs
nothing. Chips on the pane cost nothing, so a fourth chip (two or more servers saved) changes nothing.

**Departures from the plan, and where the plan was wrong**

- Line numbers in the plan are about three lines off in this tree (`runs` is at 480, not 483); the structure it
  describes is right.
- `floats` also requires `frost == null`: a chat (it passes `frost`) never takes the floating path.
- The status bar's edge cannot simply read `source ?: under`: a feature's recording lies where its list lies, not at
  the body's top, so the frame draws it shifted by the difference of the two origins.
- The plan's separate pills (§2, §3) were built first and replaced by one pane after the scrolled render (see above).
  `pills` is now `below`.
- The fallback without a lens and without `scrollsElsewhere` puts `below` (if any) at the top of the scrolling header.
  No page uses that yet.
- A bar that is the page's own heading (`scrollsElsewhere`) keeps the body's width cap on medium and wider windows (it
  was part of the capped header before). Phones are not affected.
- Not in the plan: a part brought into view under the bar. The first cut changed the scrollers' bring-into-view rule;
  it could not help a page too short to scroll (its spacer had gone, the scroller was at its top) and it would have
  shifted sideways scrollers too. Built instead: the scrolling header answers bring-into-view requests and brings the
  whole spacer back when the part starts under the chrome.
- Not in the plan: taps falling through the floating chrome to a row under it (Compose hit-tests siblings and stops at
  the first with pointer input; the title has none). The chrome now takes every touch inside it. Cost: a drag that
  starts on the title or the chips no longer scrolls the list (accepted).
- Large text: the pane is not capped; at text size 2 home's chrome ends at 208 of 800 dp (Settings 160). Noted, not
  changed.
- The focus test runs on Report a problem, not Server configuration: Server configuration draws its own `KitScreen`,
  not the Settings frame, so it does not float. The Settings frame's pages with fields are the hub (its search, at the
  top) and Report a problem (a multi-line field). Both float.
- The refresh offset is applied only on floating pages. Pages that already run under their bar because they have a
  header (eight call sites) keep their disc hidden under the bar, as before: the plan suspects All conversations is one
  of them. That is a separate fix for the coordinator to schedule.
- "Settings: 21 pages": the frame has 23 call sites (20 in SettingsScreen.kt, 2 in HubPage.kt, 1 in
  ReportProblemPage.kt), all floating.

**Pictures** (desktop, Glass dark, 412 × 915 dp at 2.5×, a 32 dp status bar; a stand-in lens that blurs what it reads
under its tint, so no bend, no rim light, no aurora, no ink flip timing and no frame cost can be judged from them):
`glass-in-the-kit/render-step4-home-rest.png`, `render-step4-home-scrolled.png` (about 120 dp),
`render-step4-home-scrolled-bright.png` (about 170 dp), and `render-step4-home-rest-before.png`,
`render-step4-home-scrolled-before.png` (base 7acb5fd8f1).

- Rest: every row sits where it sits before ("Right now" on the same pixel row). One dark rounded pane, 6 dp in from
  the sides, holds the title row and the chips row; the chips keep their outlined faces and sit on it, the divider
  between "This phone" and the filters is on the pane. "Right now" keeps today's gap below the chips row; the pane's
  bottom edge sits 4 dp inside that row's foot (the bar's pane inset), about 28 dp above the label. With nothing under it the pane reads as a slightly lighter dark panel.
- Scrolled about 120 dp: the "Right now" label and the title of the running row show blurred inside the pane, between
  the title and the chips; nothing of the page shows sharp between them, beside the pane or above its bottom edge. The
  Running chip picks up the green of "Working" under it. Just below the pane's bottom edge the bottom of the running
  row's panel (its rounded outline) shows as a thin arc, a second edge close under the pane's edge.
- Scrolled about 170 dp (the running row under the title): its preview line and the green "Working" show blurred in
  the pane behind "Conversations" and the Search and Settings icons; the title and icons read clearly over them on the
  desktop stand-in. "Recent" sits whole just below the pane's edge.
- Before: at rest the title and outlined chips on the page's ground; scrolled the same distance, they have scrolled
  away and the rows sit at the same heights as in the scrolled "after" picture, with nothing over them.

**What to look at on the phone** (Glass, a look with a lens, Background on, dark):

1. Home at rest: the first panel's label sits where it did; the title row and the chips lie on one pane.
2. Pull the list up slowly: rows pass under the pane and bend in its rim; under the status
   bar they blur into the dim. Judge the words of the title and the chips over bright rows.
3. One pane under the title and chips: does it read as one body of glass, and do the outlined chips read as controls
   on it (not as glass on glass)?
4. The thin edge of a row's panel just below the pane's bottom edge while scrolling.
5. Pull down at the top: the refresh disc comes out below the pane.
6. Tap the title area while rows are under it: nothing opens. Drag starting on the title: the list does not move
   (new; say if that feels wrong).
7. Settings: hub, a group page, Appearance, Report a problem (tap the field after scrolling it under the bar; it
   should come back below the bar). Then turn on battery saver or reduced motion: both pages go back to today's look.
8. Frame smoothness while scrolling home and Settings with the aurora on (not measured: no benchmark scene was added).

**Checks run (VPS, desktop tests; no device, no emulator), on the one-pane build 52c24149cc**

- `KitFloatingBarTest` 12 of 12: resting place (home and Settings shapes; the pane spans the bar and the chips row, 4 dp
  in), scrolled look (blur under the status bar and in the pane; the margins beside the pane cleared to the ground above
  its bottom edge; rows sharp below it), drag back, the refresh disc below the pane, the lens budget (the pane and the +
  only, with four chips on the pane; and `LiquidEdge.grant` alone), pixel equality with and without the option in Solid,
  Soft, Brutal, Paper, Signal and Glass with no lens, reduced motion and the Frosted look, the short window, right to
  left, text size 2, a focused part brought back below the bar, taps on the chrome.
- `FloatingHomeTest` 2 of 2, `FloatingSettingsTest` 2 of 2 (the Report a problem field moved under the bar and focused
  comes back below it, so the field pages float).
- The kit's frame tests (54, 9 failed), the chats and settings golden tests plus `HomeJank`, `S10Home`,
  `SettingsScreenUi`, `SettingsHubScroll` and `ReportProblem` tests (chats 22, 2 failed; settings 39, 1 failed): every
  failure also fails on the base 7acb5fd8f1 with the same message (`KitScreenGateTest` missing Flutter goldens and an
  align rule; `ChatsHeaderGoldensTest` offline variants, where the list already shows a "This phone isn't answering"
  notice the goldens lack; one `E20S09OpeningGoldensTest` test that cannot find "Check that the server is on"; `SettingsGoldensTest` B-SEC-191). Before the one-pane
  change the whole chats and settings desktop suites ran too (255 and 254 tests), with all 66 failures identical on the
  base. No golden was recorded or changed.
- `compileAndroidMain` for ui:kit, feature:chats and feature:settings.
- Not run: the whole kit suite, the benchmark scene (not built), anything on a device.

## Polish after the first phone look (2026-11-11, branch native/glass-polish)

The owner's phone (Glass, dark, bright page glow) showed two faults on the home.

**A. Chips cut by the floating pane.** Cause: the chips line on the pane's second row ran on to the window's edge
(`KitFilterChips` with `bleed`, no end inset), past the pane's 6 dp side, so the last chip ran across the pane's rounded
corner and outline; and the pane ends 4 dp inside the row's foot, with the chips' 48 dp targets centring 32 dp faces,
so its bottom edge sat about 4 dp under the chips. Changed: inside the floating pane's second row (only where the lens
is drawn) a sideways line clips at the pane's side, rests its chips on the gutter inside it (10 dp from the pane's side),
and fades with the kit's scroll fade (`kitScrollFade`) over 16 dp only at an end with more past it
(`FloatingPaneRow`, `LocalKitOnFloatingPane`, read by `KitFilterChips`). The row keeps 16 dp under it, the room the page
heading keeps above its title row, so the pane's edges sit 12 dp from the title row and from the chips row. Cost: under
the lens the list at rest now starts 16 dp lower than without the lens (it used to start on the same pixel).
Without the lens nothing changes.

**B. Thick bright bars on the panes' straight edges.** Cause: the rim's picture light (`BackdropRimLight` in
`KitSurfaceGlass.kt`) was a 1.5 dp band clipped to two rectangles, the straight part of the top edge and of the start
side, with hard ends at the corners' tangent points, drawn over the 1 dp rim at up to .85. With today's bright glow it
read as a bar about 2.5 dp wide, while the corners next to it kept only the thin rim. The pane's own three-stop rim
(`edgeRest`) is a 1 dp stroke round the whole outline and could not draw it. Changed: the light is now a ring as wide as
the rim (1 dp, never wider) round the pane's whole outline, corners included, through one even-odd ring clip; its
strength (gain, most) is unchanged, so it rises and falls only with the picture behind the edge. It now also lights the
end side and the bottom edge where the picture behind them is bright. No `GlassLook` number changed; other styles,
looks without the light and the held fallback draw no picture light, as before.

**Pictures** (desktop, `KitRimLightTest`'s render: a dark 1 dp-rimmed pane over a plate with a bright green glow at
its start side; the light drawn by the kit's own `BackdropRimLight`, no lens): `glass-in-the-kit/render-rim-light-before.png`
shows the phone's fault: a thick white bar on the straight start side and along the top, both ending hard where the
corners begin, the corners and the other two sides only the thin grey rim. `render-rim-light-after.png`: one thin rim all
round, white where the glow lies behind it, fading smoothly round the corners to grey on the dark end side; no bar, no
hard end. Note: the end side and the bottom now catch some light too where the picture behind them is a little above the
ground (grey on the near-black side of the plate), so a pane's whole rim reads brighter over a mid-dark glow than
before. The desktop cannot show the lens, the aurora or the phone's own rim; the chip row has no picture (the test
measures it).

**Checks run (VPS desktop tests, no device)**: `KitFloatingBarTest` 12 of 12 (two expectations moved by the pane's
room: the list at rest and the scrolled distance), `KitFloatingBarRowTest` (new, five chips at 360 dp: the clip edge on
the pane's side, the fade present at an end with more and absent at an end without, the last chip's rest 10 dp inside,
every un-faded chip face inside the pane's rounded outline, equal room above the title row and under the chips row),
`FloatingHomeTest` 2 of 2, `KitRimLightTest` (new: lit within the 1 dp rim and dark 1.5 dp in, on all four edges and
all four corners; even along side, tangent point and arc over an even picture; no step over 16 of 255 along the edge
over a graded one; red before the fix), `GlassPanesKitTest` 4 of 4, `compileAndroidMain` for ui:kit and feature:chats.
Not run: goldens (the non-floating layout and the other styles are untouched in code), anything on a device.

**What to look at on the phone** (Glass, a look with a lens, Background on, dark, a bright glow):

1. Home at rest: the chips row has the same air under it as the title has above it; the last chip ends on the gutter
   inside the pane when scrolled to the end, and while more lies past the end it thins out before the pane's side.
2. Scroll the chips sideways: a chip leaving at either end thins out over a short step; nothing is cut by the pane's
   rounded corner or at the divider.
3. The list panel, "All conversations" and the header pane over the bright glow: one thin, even rim all round, brighter
   where the glow behind it is brighter, with no thick bar and no hard end where a straight edge meets a corner.
4. The list at rest starts 16 dp lower under the lens than with reduced motion; say if that jump between the two reads.

## Dock glass, as built (2026-11-11)

The owner, on the phone, Glass dark, the dock's swell open over a chat: "Why isn't it glass?" The dock was an opaque
dark slab while the bar, the message box and the menu were liquid glass.

**What it is now.** Under Glass, with a lens (the same holds as every liquid pane: Glass style, a look with a lens, not
Frosted, no battery saver, motion not reduced, no high contrast, Android 13 or later) and a recording of what lies behind
the dock in scope, the dock is ONE body of liquid glass: the strip (box A) and the swell, peek or list (box B) joined by
the joined-glass field with the dock's own `joinReach`, drawn by one node (`effects/KitLiquidBody.kt`). The lens pass,
the tint, the cover and one rim light follow the body's outline. Otherwise `rememberDockFace` returns today's face,
unchanged (the dock's looks pictures and tests did not change).

**The recipe.** A new liquid role, `LiquidRole.Dock`, ranked between the + (Action) and the bars. At rest it is the
bar's recipe (blur 3 dp, bend 34 dp, rim 22 dp, tint .18 dark / .12 light); fully grown it is the menu's glass as it was until words on glass 3 (`LiquidEdge.swellRecipe`: blur 6 dp dark
/ 3 light, bend 48 dp, rim 26 dp, tint .20 / .10); in between every number is mixed by the body's growth with no step
(`LiquidEdge.dockRecipe`, `LiquidEdge.mix`). The tint colour is mixed the same way between the bar's face and the menu's
face. One resolution for the whole growth (the grown end's), so the sharpness never steps while the swell grows. The
words' ink is the bar's: the theme's text, flipping to the other theme's over content of the other brightness (sampled
under the body's bounds only), the second line drawn brighter on glass; the canvas marks (grip, charge arc, "+n")
follow the same flip.

**How one node draws both sides.** The join shader is unchanged (not one character): the node hands it box A as its own
side and a box far outside the window as B's side box, so every pixel of the body counts as its own and its material
ramp stays at its own end. The bar + menu and box + pill joins draw exactly as before (their shader tests pass
unchanged). The dock's copy of the field (`DockJoinField`) is kept: replacing it with `LiquidJoin.sample` would allocate
on every sample of the outline trace (it is called thousands of times a frame by the Solid face).

**Where the recording comes from.** A new composition local, `LocalKitDockGlass`, provided by the two host screens:

- In a chat, the transcript's recording (`frost`, the one the bar and the message box read). The dock used to sit INSIDE
  that recorded box, so it would have bent itself; it now stands beside it, in the same body box, with the same size and
  place.
- On home, the list's recording (`listGlass`, the one the floating + reads).

**What loses its lens when the dock appears.** The dock costs one pane of the window's five (a feature's recording costs
nothing), and its rank follows its growth (`LiquidEdge.dockRank`): tucked or at rest it ranks below every pill, so in a
chat the message box, the bar, the jump pill and two chips over the message box all keep their lens and the resting dock
is frost of the same shape (blur, tint, cover and rim; no bend). Grown (swell, peek or list out) it ranks between the +
and the bars: it takes a lens and the last chip to claim gives way to frost while the dock is open. With fewer pills the
resting dock keeps its lens too. On home the floating bar (its page recording counts too), the + and the dock fit,
resting or grown (`LiquidEdgeDockTest`).

**Limits.**
- No shoulders into the screen edge under Glass (the two-box join does not draw a third shape).
- No touch light inside the dock, no shadow.
- The strip grows above and below an open swell or peek by the fillet's blend plus the corner (so the fillet has a
  straight side to meet); with the tall peek that is about 120 dp of strip with nothing in it (device QA M3). This is the
  dock's geometry, not the glass.
- The words' flip samples the body's bounds as one mean; a body over half-bright, half-dark content flips by the mean.
- The desktop pictures run the real join shader and lens pass (desktop's Skia), so they show the bend and the rim, at
  desktop's blur; nothing was run on a phone.

**Pictures** (desktop, Glass dark, a page of text with bright bands, 412 dp; the real join shader at desktop's blur):
`glass-in-the-kit/render-dock-rest.png`, `render-dock-swell.png`, `render-dock-list.png`. What they show:
- At rest: the strip is glass; the lines behind it are blurred to grey bars and the bright bands show through as a lighter
  patch; one thin light rim along the outline; the rings, names and the + read.
- Swell open: strip and swell are one body, the fillets above and below the swell round with no seam or line where they
  meet; the strip runs on above the swell (the extension noted under Limits). The blurred text lines behind the swell
  show as stripes under its words; "Thinking" is weaker over them. The scrub swell now offers only the ring's state's
  actions (here, a thinking ring: Open and Stop), whole. Rename and Remove live in the held ring's card and the list. A
  word that does not fit its cell whole leaves the glyph alone, still labelled: at 360 dp and text size 1.3 the scrub
  swell of a request shows the Open and Allow glyphs alone and "Deny" in words; in the held ring's card at 360 dp and
  size 1, Rename and Remove show their glyphs alone (the five cells are too narrow for those two words).
- List out: the whole list is one pane of the same glass; the bright bands behind show through as pale bars, and the
  rows over them ("Docs", its second line) read with less contrast than over the dark lines; the words did not flip
  (the body's mean stays dark).

**What to check on the phone.**
1. Open a chat in Glass, dark and light: the dock's strip shows the transcript blurred and bent at its rim, one thin rim
   light, no dark slab.
2. Scrub a ring: the swell grows out of the strip as one body; no seam or line where the swell meets the strip, the
   fillets round, the frost thickening a little as it grows.
3. Hold a ring for the peek and pull the list out: same glass, words readable over bright and dark rows.
4. Scroll bright content (an image, a white code block) under the dock: the words flip to dark ink and back.
5. With the dock out in a chat, count the lenses: the bar, the message box, the jump pill and one chip stay liquid; a
   second chip turns to frost.
6. Battery saver, reduced motion, the Frosted look and the Solid style: the dock is today's solid face.

## Words on the chat's glass (2026-11-11, branch native/glass-words)

Seen on the phone (Android 15, Glass, dark, tonight's build): the message box's placeholder ("Steer while it works…",
"Ask OpenCode…") had blurred transcript lines running straight through it whenever the transcript was scrolled away
from the newest message.

**Cause.** The body's own fade (`kitScrollEdges` in `KitScreen`) never reached the glass. The message box's lens reads
the screen's recording of the transcript (`KitFrostState`, recorded by `kitFrostSource` inside the body, before any
fade), and under the Glass slab the body is not faded above the bottom block at all. With the morning's blur cut (4 to
2 dp) the transcript showed through the box's words at nearly full brightness. A pane that is not joined to another
also gets no luminance knee: the knee lives only in the joined shader (`KitLiquidJoin.kt` `kneeOf`, and the dock's
body); the library pane (`AndroidLiquidGlass.pane`: colour controls, blur, lens) has none, so the box alone held back
nothing. The "messages waiting to send" line under the bar: see "The notice line" below.

**What changed** (remedy 2 of the skill, a scroll-edge fade; no blur, tint, saturation or plate changed):

- `frame/KitWordsFade.kt`: where the lens is drawn (Glass, a look with a lens, not held), the frame hands the bottom
  block a copy of the recording, re-recorded each time the body records, cleared below the top edge of the block's
  words pane: whole at the edge, gone by the inner end of the lens rim (`LiquidEdge.rimPx` for the pane's size, the
  same rim the Android lens bends in: 22 dp for the box), along the kit's smooth step on whole pixels. The rim still
  bends real content, so the box stays glass at its edge; under the words nothing bright is left, and the words'
  ink flip samples what is really behind them.
- `frame/KitScreenBottom.kt`: `Modifier.kitScreenWordsPane(role)` reports the pane's top edge and rim; the message box
  (`KitComposerBox`) carries it while it is liquid.
- Draw phase only: the body, its layout and scroll, the bar's glass and the jump pill (which reads its own recording)
  are untouched. Every other style, and Glass without a lens, hand the block the recording itself, as before.
- Not done: a knee for panes that are not joined. It needs a runtime-shader step in the Android library pane's effect
  chain (`AndroidLiquidGlass.pane`, androidMain), outside this change and only judgeable on a device; with the fade
  nothing bright lies behind the box's words, so it is left for later.

**Pictures** (desktop; the desktop's lens stand-in is the recipe's blur under the tint, with no bend and no rim light):
`glass-in-the-kit/render-words-chat-before.png` and `render-words-chat-after.png`, Glass dark, a white-text transcript
scrolled away from the newest message, the "1 message is waiting to send" line under the bar.

What they show. Before: the placeholder "Ask OpenCode…" has a blurred transcript line ("…the log, compares the
timings") running straight through it, and two more blurred lines fill the box down to its row of controls; the box
over them reads as a grey wash. After: the placeholder stands on the box's own dark glass with nothing behind it; along
the box's top edge a faint blurred trace of the line behind it remains for the depth of the rim and is gone before the
placeholder, so the edge still shows something real behind the glass. On the phone the lens also bends that band, which
the desktop cannot show. The transcript, the jump pill and its join, the bar and the notice line are the same pixels
in both. One thing seen in the small test scene (a screen of solid white rows): the box no longer flips to the light
face there, because what it reads behind its words is now the page; the jump pill, which still reads the white rows,
stays light, so the joined pill and box show two faces meeting at the neck. Over a real transcript (light words on the
dark page) neither flips.

**Checks** (VPS desktop tests, fake lens = the recipe's blur only):
- `ChatWordsGlassTest` (new, kit-chat): white rows scrolled under the box, compared with the same scene whose rows draw
  nothing. Inside the placeholder's line across the field: before the fix mean 219.7, max 234 of 255 (failed); after,
  mean 0.01, max 0.3 (limit 2 and 8). At the box's top edge (1 to 5 dp in, beside the pill) the rows still add 91.9 of
  255 (at least 20). The transcript's scroll state, the rows' places and the jump pill's place are the same with and
  without the lens, and the same before and after (the box sits at 6/340/234/456 dp in both). Outside liquid Glass
  (Solid, Soft, Brutal, Paper, Signal, Glass on a platform with no lens, Glass's Frosted look) the bottom block sees the
  screen's own recording.
- Renders of the eight looks before and after: the seven without a lens are pixel-identical; Glass with the lens
  differs only inside the box and the pill's neck.
- Unchanged and green: `KitScreen*`, `ChatEdgeATest`, `KitFloatingBar*`, `ScrollEdgeWholePixelTest`, the kit-chat
  composer tests, `JumpFadeEdgeTest`, `E20S30JumpPillTest`.
- Not run: anything on a device; the lens's bend and rim light; a phone's GPU on the extra recording.

**What to check on the phone** (Glass, a look with a lens, dark and light):
1. Scroll a long chat up so the jump pill shows: "Ask OpenCode…" / "Steer while it works…" and typed words have nothing
   moving behind them; the box's top rim still shows the transcript bending as it passes under the edge.
2. The pill on the box: one body, no line or dark notch at the neck, as before.
3. Scroll fast: the faint band at the box's top edge follows the transcript with no lag and no flash.
4. Keyboard up (the box moves): the clear area moves with it at once.
5. Frosted look, Solid and the other styles: exactly as before.

### The notice line

Seen on the emulator (Android 15, build 6fe709ef3, `16-glass-header/d-chat-scrolled-up.png`): "1 message is waiting to
send. Review" crossed by sharp transcript text. On the owner's phone the same line was clear. **Cause:** `KitScreen`
cleared the body down to the line's foot only where the page runs under the status bar (`glassTop` asked for
`statusPx > 0`). That emulator's status bar is drawn by the system (a dark band, no top inset for the page), so nothing
was cleared there at all. **Changed:** with a line under a liquid or frosted bar and no top inset, the body is cleared
down to the line's foot and fades in over the same 12 dp under it; with no line, nothing changes; the Ground look (the
solid styles) keeps its band. **Test:** `ChatWordsGlassTest` now runs the line with a 24 dp inset and with none: with
none, before the fix the rows added mean 233 of 255 inside the line (failed); after, 0. With the inset: 0 before and
after.

### As built: the top panes and the request card's label (2026-11-11, branch native/glass-words2)

**Top panes.** `KitWordsFade` is now one fade with two sides (`KitWordsClear`): `Below` (the message box, as before) and
`Above` (a pane's bottom edge): the copy of the page recording is whole at the pane's bottom edge and gone by the inner
end of its lens rim (`LiquidEdge.rimPx`, Bar recipe), on whole pixels. It copies either a feature's recording
(`KitFrostState`) or the kit's own (`KitLiquidSource`); for the kit's own the copy takes the original's coordinates and
the frame holds the original open (`users++`) while the copy is used, since the kit records only while a pane reads it.
The chat's bar: the recorder runs on the body after it has drawn (the body sits in its own layer, so this never
redraws it); the bar's face and its words host read the copy. Home and Settings: the same on the floating pane's box.
The status edge keeps the whole recording: `ChatEdgeATest` (chat edge A, the owner's pick) requires the page to blur
under the status bar, so this is a departure from the rule written above. Draw phase only: no layout, scroll or
composition change; other styles and Glass without a lens are untouched.

**The request card's label (and anything above the box).** Cause as suspected: under a slab the body was left whole
behind the whole bottom block, so a label with no glass of its own stood on sharp lines, and the phrase chips' own
glass read a copy that was whole above the box's top edge. Now the block reports its top (`KitScreenBottom.blockTop`)
and the frame knows when something stands above the box (`stacked`: the box's top lies more than 24 dp below the
block's top). Then (a) the body fades out over the last 8 dp above the block's top, as under a block that is not a slab;
(b) the box's copy is also cleared above its top edge, so chips and controls above it see nothing bright; (c) the jump
pill is not joined to the box (a joined pill is drawn at the box's top, which lies below the card: that is why the
tester's picture showed it between the card and the box, while the layout places it above the whole block); it stays
above the block. With nothing above the box nothing changes.

**Not done: the jump pill's own label with typed text.** The pill reads the transcript list's own inner recording, not
the screen's copy, so blurred lines show behind "Jump to latest" when the box is typed in and the pill is joined. A
fix needs a third copy of the list's recording cleared in the pill's label band (its geometry is the join's, which
moves), recorded each frame for the pill's backdrop in `kit-chat`, or the pill's backdrop switched to the faded copy,
which is whole above the box's edge and so does not help. Left for a device pass.

**Pictures** (desktop, Glass dark, white rows scrolled under the panes): `glass-in-the-kit/render-words-top-chat-*.png`,
`render-words-home-*.png`, `render-words-needs-decision-*.png`, `-before` and `-after`.

**Checks** (GitHub, desktop): `ChatWordsGlassTest` (bar title, label, chips), `KitFloatingBarTest` B-KIT-FLOAT-013
(title and chips of Home and Settings; B-KIT-FLOAT-002's capsule check moved to the pane's rim). Before the fix the four
new tests failed; after, they pass together with `ChatEdgeATest`, the other `KitFloatingBar*`, `ScrollEdge*` and
`Jump*` tests. Not run: anything on a device; the lens's bend and rim light.

Last run on the branch tip (VPS desktop): `ChatWordsGlassTest` 5 of 5, `JumpFadeEdgeTest`, `ChatEdgeATest`,
`KitFloatingBarTest`, `KitFloatingBarRowTest`, `KitScreen*`, `ScrollEdgeWholePixelTest` green; `compileAndroidMain` for
ui:kit, ui:kit-chat and feature:chat green. Nothing was run on a device.

## Dock follow-ups, as built (2026-11-11)

From the emulator run of 2026-11-11 (run 4). Six items; the first five are in the kit, the sixth needs a frame or feature
change and is not built.

**1. Whole rings only (1b).** With the keyboard up the strip is one ring tall, and while the column scrolled (or the area
changed under it) the next ring stood half in view above the "+". A ring now takes room and draws only when its whole slot
lies inside the column's viewport (`ringStandsWhole`, applied in the ring's layout); otherwise it is not in the strip (it
stays in the list), in every frame, so no ring ever overlaps the "+" (a ring that does not stand is measured at no size and
draws nothing, so it has no bounds). The ring riding out to be renamed is exempt (it
stands in box B). Picture: `glass-in-the-kit/render-dock3-1-before.png` and `render-dock3-1-after.png` (keyboard up, the
column caught between two rings: before, three rings in slices; after, the one whole ring). The pictures come from
`KitEdgeDockFollowUpPicturesTest` (desktop's Skia, Glass dark); the "before" ones from the same scenes on the commit before the
follow-ups.

**2. `KitDockModel.offersNew` (default true).** False: no "+" in the strip and the strip has no foot (it is 60 dp shorter,
`DockColumn.foot`), no "New conversation" row in the list (and the list's box is as much shorter), and the foot zone takes
no touches. With no rings and `offersNew == false` the dock composes nothing at all (no node, no touches, no back-gesture
exclusion) and closes anything it had open. The tucked nub never had a "+".

**3. Rename (3a).** The field showed two lines of a long name because the swell was sized for at most three and only after
the name had been laid out as a guess. The field now grows with what the name measures, one to four lines
(`DockDims.RENAME_LINES` 3 to 4), the swell's height following on the dock's fill spring (`renameMeasured`), then scrolls
inside itself with the caret kept in view. In a short area (the keyboard up) lines are taken off so the swell, its fillets
and the strip's corners stay inside the area above the keyboard. The empty body came from scrolling the column to the ring
that rides out, leaving no ring in the one slot of room; while renaming, the column now keeps another ring in view and the
strip's rings are dimmed (45 %). The strip still grows above and below the swell by the fillet and the corner, as for every
swell (see Limits above): that is the dock's geometry. Picture: `render-dock3-3-before.png`, `render-dock3-3-after.png`.

**4. The list lets the page through (4b).** The grown dock used the Menu's glass: blur 6 dp dark (3 dp light), bend 48 dp (since words on glass 3 menus frost at 12 dp in both themes; the dock keeps this glass as `LiquidEdge.swellRecipe`).
For the list only the blur and the bend now move to the Sheet's (`LiquidEdge.baseRecipe`: blur 12 dp, bend 48 dp; the bend
is the same number, so the change is the blur: dark 6 to 12 dp, light 3 to 12 dp) by the list's own growth
(`LiquidEdge.toward`, `KitLiquidBodyBoxes.listShare`): zero while a swell or peek is out, rising with the list from zero, so
no step in either direction. Strip and swell keep their numbers (3 dp at rest, 6 dp dark grown). Tint (.20 dark, .10 light),
rim, saturation, the luminance knee and the brighter second ink are unchanged. Picture: `render-dock3-4-before.png`,
`render-dock3-4-after.png` (desktop's Skia blur; not seen on a phone).

**5. Names off the screen edge (4a, 4c).** A ring's name sat in a 56 dp box centred on the ring, so a long name's ellipsis
ended 6 dp from the screen edge. It now ends the page gutter (the design tokens' 16 dp, `DockDims.GUTTER`) from the dock's own
screen edge and keeps 6 dp from the strip's inner side: the box is 46 dp wide and centred 5 dp toward the middle from the
ring (`DockDims.labelMax`, `labelShift`), mirrored for a dock on the left edge, so for both edges and in RTL. A short name
therefore stands 5 dp off its ring's centre.

**6. The Undo bar over the dock's foot (5a): not built.** `KitUndoBar` (`modal/KitUndoBar.kt`, the composable at line 91)
exposes neither its height nor an inset, and each host places it in its own box (`feature/chats` `EdgeDockHost.kt` places it
at the bottom with `undoBottom`, `ChatsScreen.kt` and others likewise). Lifting the dock needs one of: the bar reporting its
measured height (a new kit local or an `onHeight` callback, a change outside the dock files), and the host adding it to
`KitDockInsets.bottom` while an offer is shown (`EdgeDockHost.kt`, where `undo` is known and `insets` is passed on).

### Dock follow-ups 2 (run 5), as built (2026-11-11)

**7. The list on the wrong edge (run 5, d).** Driven through the real pointer loop at 360 dp and 320 dp wide, both edges
(`KitEdgeDockListGestureTest`), the list was never misplaced: a flick inward from a ring (a sideways move before the swell
shows, then release) opens the list flush with the dock's own edge on every width (`panelBox`: the pane is
`min(320, width - 48)` wide, its inner edge keeps the 48 dp gutter, the corners on the inner side are rounded, the outer side
runs `OVERHANG` 40 dp past the screen so no edge or corner shows there). The picture is that list on the LEFT edge (a pane 320 dp
wide, which at 2.625 px per dp is 840 px, as measured: the screen is about 411 dp wide, not 360), with its overhang past the left
edge and its rounded right side. The dock was on the left because a flick that starts on the dock's head (the grip, the top 36
dp of the strip) is a drag of the dock, not a pull: `DockMode.Move` ended in `DockOutcome.Place`, which puts the dock on the
half of the screen the finger lets go on, so a flick inward crossed the middle and carried the dock to the other edge (the
rings and names then stand in the left-edge list). Now a drag from the head that lets go at 900 dp/s or more, mostly sideways,
toward the middle is the list's flick (`DockRules.flickSpeed`, `DockOutcome.PullEnd`): the list opens on the edge the dock was on, the dock
goes back to its height. Moving the dock to the other edge is a slower drag from the head (or the nub) that lets go on the other half.
Gestures: from a ring, sideways first, release at any speed that carries the strip past half the list (flick at about 760 dp/s or
more; slower and short settles back, outward below 62 % of the rest width tucks); from the head, below 900 dp/s: moves the dock,
release past the middle changes edge; from the head, 900 dp/s or more toward the middle: the list; from the head, fast outward or
mostly vertical: a placement on the same edge; from the nub: along the edge moves, inward past 24 dp brings it out.

**8. Rename takes the keyboard from another field.** With the keyboard open on the chat's message box, the rename field now first
clears focus (`clearFocus(force)`), waits a frame and then requests it, so the keyboard's input follows the new field, and when
the field leaves it clears focus only if it still holds it, so focus ends on nothing and not on the message box. The desktop
harness could not reproduce the fault (`KitEdgeDockRenameFocusTest` passed on the code before the change: it sets text through
semantics, which does not go through the keyboard's input session); the change is therefore not proven on a device.

**9. Rings while the keyboard is up.** The column does not scroll by drag: a vertical drag in the strip is the scrub (it moves
the swell over the rings in view), the dock's main gesture, so a scroll by drag would collide with it. The column scrolls only
to keep the current conversation, the renamed ring or the ring that needs the person in view (`scrollToShow`); the other rings
are reached through the list (pull inward). `KitEdgeDockListGestureTest` holds this as a test. Not changed.

**7b. The short list in the second try (run 5, d2).** A flick inward from a ring on the right edge opened the list on the right
edge, as it should. The pane was 209 dp tall with three rows. Both boxes are as tall as the area the host hands the dock allows
(`panelBox`: the content's height, at most the area's height minus 100 dp for the shoulders' room; the strip is capped the same way by
`DockColumn.of`, three whole rings at 62 dp pitch fit 310 to 372 dp of area). A strip of three rings and a pane of the same height,
centred on the same point, mean the dock's area was about 310 to 372 dp tall that moment, not 800: the kit did not cap the pane,
and the other four rows are inside it, scrolled (`KitEdgeDockListGestureTest`, "list height", reproduces it with a 448 dp bottom
inset: the pane takes all the height the area allows and scrolls its seven rows). What took the area is for the host to check
(the home's `windowInsetsPadding(safeDrawing)` carries the keyboard's inset, which would leave a short area after a search or a
rename). The pane is also 280 dp wide in both pictures where 312 dp is expected at 360 dp (`min(320, width - 48)`), so `width -
edgeInset - farInset` was 328: 32 dp of horizontal inset the dock was given, which the kit cannot tell from a host's inset.
Nothing here makes one movement both open the list and move the dock: the outcomes are exclusive (`PullEnd` or `Place`), and in
the old code a flick from the head only moved the dock.

**7c. The list is a whole pane (run 5, d and d2: "cut off, runs off the screen").** The opened list was attached to the edge like the
strip: no border or corners on that side, its body running 40 dp past the screen. Now `listOuterSide(width, growth)` moves box B's
outer side from `width + OVERHANG` to `width - GUTTER` (16 dp) with the list's own growth, on the same spring and in the pull by its
growth (it crosses the edge at about 0.5 and holds from 0.71; no second animation, no new spring); all four corners are the pane's
radius and the rim is drawn all round (the Solid outline closes on the outer side and lets go of the shoulders as the pane comes off
the edge). The inner side keeps its 48 dp gutter, so the pane is 296 dp wide at 360 dp and 256 dp at 320 dp (before: 312 and 272, running
to the edge), on either edge and mirrored in RTL; rows keep their own 16 dp gutter inside it. Box A (the strip) rides inside the pane
while the list opens (its right side follows B's, so no sliver is left at the edge) and the rings and the + stand in by the same
amount (`stripShift`) to stay inside A. The row title cut at a word break ("Quarterly budget review for the") had no "..." because
the one-line cut at a space drops the rest without marking it; the rows' names now have no break opportunity (`dockTitleLine`, a
no-break space) so the overflow ends them in the mark. The desktop harness shows the mark both before and after (it does not
reproduce the platform's cut), so that part is not proven on a device. Tests: `KitEdgeDockListPaneTest`; the picture is written by
`KitEdgeDockListPanePicturesTest` (`render-dock5-list-after.png`; CI keeps no files, so the pictures are made where it runs).

## Words on glass 3: menus and sheets (2026-11-11, branch native/glass-words3)

From the emulator sweep (Glass dark): behind "Rename conversation" in the chat bar's menu a dark user bubble read as a
highlighted row; a menu title was overprinted by text behind it; a reply's menu was translucent over body text; the green
"Ask about this file" button was a bright blurry blob behind a sheet's "Copy path", and the green mic button glowed beside
"Cancel". Finish line: on Glass with the lens on, behind the words of a menu and a sheet nothing reads as a shape, a line
of text or a bright or coloured blob, and their rims still bend real content. No change to bars, the message box, chips,
pills, the dock or the action button, to any other look, to the menu's drop and fill.

### As built: 1. Menus frost as the dock's list does

`LiquidEdge.baseRecipe`: the Menu role's blur is 12 dp in both themes (was 6 dark, 3 light); bend (48), rim (26 dp), tint
(.20 / .10) and saturation (1.6) are unchanged. Two things derive from the Menu recipe and are handled so nothing steps:
the edge dock's swell (the grown end of the dock's mix) keeps the old glass as `LiquidEdge.swellRecipe` (blur 6 / 3, no
hold), selected by `kitLiquidPaneSpec(swell = true)` in `KitLiquidBody`, so the dock's strip, swell and list are the
numbers they were (its list still moves to the sheet's 12 dp by `toward`); and the joined menu. The joined body draws
ONE frost, the body's (the bar's 3 dp), so a Menu recipe alone would never have reached a menu that opens out of the chat
bar, which is the case in the pictures. While a menu is joined the body's blur now moves from the bar's to the menu's as
the menu's rows come in (`joinedFrost`, the drop's `content` 0 to 1, linear, no step; bend, rim and tint ease by the
join's own band as before), and the lens pass's resolution follows the mixed blur. So the bar's half of the body frosts at
12 dp while a menu hangs from it (a departure from "no change to bars": it is the one frost of one body); a join with the
jump pill (blur 1) is untouched, since only the menu (a drop whose glass holds, `LiquidHold`) moves the body's frost.

### As built: 2. The knee reaches plain panes

The vendored library's pane takes effects: no fork is needed. `AndroidLiquidGlass.pane` adds a runtime-shader effect after
the lens (`runtimeShaderEffect("OcHold", LiquidHoldShader.SOURCE, "content")`) when `recipe.hold.knee` (the Menu and Sheet
roles; `LiquidHold`). It is the join shader's knee line for line (`kneeOf`: polarity by ink, following the words' flip),
written once in `KitLiquidHold.kt` as AGSL/SkSL; every declared uniform (`knee`, `hold`, `box`, `corner`, `cc`) is set in
every effect run (`KitLiquidHoldShaderTest` compares the declared names with the set ones, for this shader and the join
shader). Desktop: `kitLiquidPlainStandIn` (colour controls, blur, the hold, the tint; no bend, no rim light) is the
stand-in the tests draw the real menu/sheet faces through. The luminance of white behind a sheet's words is held to .50
(`LiquidJoin.held`); a bright white card behind the words: peak luma 207 to 110 (menu), 203 to 109 (sheet), still a grey card, not a white one.

### As built: 3. Colour held back

Measured on a pure green (0,255,0) and a pure orange (255,128,0) 56 dp circle behind the words' band (a menu and a sheet,
dark, desktop stand-in, mean chroma over the circle's core, over the empty pane, of 255): today 199 / 198 (menu), 191 / 190
(sheet); knee alone 119 / 152 (.60 / .77 of today's) and 115 / 146 (sheet); (a) saturation 1.0 for the Sheet and Menu roles
with the knee 118 / 139 (.59 / .70), sheet 114 / 134 (.60 / .70); (b) shipped 47 / 46 (.24 / .23), sheet 45 / 43 (.24 / .23).
(a) cannot work: a pure colour is already saturated at 1.0, and saturation 1.6 only lifts mixed colours (the app's own
green, #34C759: 166 today, 77 with (a), 46 with (b)). So (b): after the knee the colour that is left is limited
(`LiquidHold.chroma` = `LiquidEdge.HOLD_CHROMA` = .26): a chroma (largest minus smallest channel) up to .4 of that is
left alone, above it eases to .26 (`t + (cap - t)(1 - exp(-(c - t)/(cap - t)))`), the light the knee's, in the lens's inner
part only (`smoothstep(rim/2, rim, depth)`), so the rim keeps its colour (a green block behind a pane's edge: chroma 121
in the rim band, 51 at the middle). The first try scaled the colour by `(knee / light)^3` above the knee: its core was as
quiet (.08 / .22) but the sub-knee halo kept its full colour, so a green disc read as a green ring round a grey centre
(`render-words3-sheet-exponent-ring.png`); the limiter has no ring (the chroma never falls as the colour gets stronger,
a unit test). Both shaders; the join shader gets it only for a body joined to a menu (the bar's half too: one body).

### 4. A dark bubble is still a plateau

Behind a menu's row a dark bubble (and the mid ground it sits on) keeps its plateau: contrast between the bubble and its
ground 40 of 255 before and after (a frost cannot remove an area), the steepest edge 16.3 to 9.6 of 255 per 4 dp (a line of text behind the menu: peak 95 to 72, steepest edge 1.9 to 0.0, contrast 68 to 18). Under
light words in a dark theme the knee holds back the bright end only. If the picture on the device still shows a
highlighted row, the next lever is a floor for the dark end (the mirror of the knee), not more blur.

**Pictures** (desktop, Glass dark, stand-in lens: no bend, no rim light): `glass-in-the-kit/render-words3-menu-before.png`
and `-after.png`, `render-words3-sheet-before.png` and `-after.png`.

**Checks** (GitHub, desktop): `LiquidWordsHoldTest` (recipes, the reference colour), `GlassWordsMenuSheetTest` (the real
menu and sheet faces over a line of text, a dark bubble, a bright card, a green and an orange circle: before and after
numbers, the rim, the other looks), `KitLiquidHoldShaderTest` (both shaders against the reference, the uniforms),
`LiquidEdgeDockTest` and `LiquidJoinTest` (the dock keeps its numbers). Not run: anything on a device; the lens's bend and
rim light, the Android runtime shader (compiled as SkSL on desktop from the same text).
