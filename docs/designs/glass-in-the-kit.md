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
`render-after-dark.png` (as built), both forced clear on desktop (Motion Calm, not Off, which is reduced motion and holds
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
