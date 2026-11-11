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
bar's recipe (blur 3 dp, bend 34 dp, rim 22 dp, tint .18 dark / .12 light); fully grown it is the menu's (blur 6 dp dark
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
nothing). In a chat the message box, the bar and the dock take three, so two pills keep their lens: the jump pill and
the first chip over the message box; the next chip (the last to claim) is drawn as frost of the same shape. With only
the jump pill out nothing loses its lens. On home the floating bar (its page recording counts too), the + and the dock
fit; nothing loses its lens. The owner's rule that the dock is always present means this claim is permanent on both
screens (`LiquidEdgeDockTest`). If chips matter more than the dock, ranking the dock below the pills while it is tucked
is a one-line change.

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
  show as stripes under its words; "Thinking" is weaker over them. The action words keep their gap now, but in the
  scrub swell's narrow cells "Rename" and "Remove" are cut to "Rena..." and "Rem..." (the cells are too narrow for
  them; before the gap fix they read "RenameRemove").
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
