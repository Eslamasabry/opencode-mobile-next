# Glass looks v1: design critic review (architecture, performance, readability, risk)

Reviewer: Design Critic goldfish. Target: `docs/designs/glass-looks-2026-11-09.md`. Cross-read with
`LiquidEdge.kt` (recipes, BLURRED_INK .20, BUDGET 5), `GlassFinish.kt`, `FrostPolicy.kt`,
`docs/native/qa/glass-v3-2026-11-09/README.md` and the repo's glass history ("very artificial", 10-26).
Verdict: promising direction, but the readability and performance claims are asserted, not demonstrated. Four items block.

## Findings, ranked

### 1. Blocker. "Readability holds for every look over every live background" is not what the floor measures
Scope: section 2 "Readability guarantee", tests paragraph.
The guarantee rests on the floor being measured "over the backdrop as this look presses and blurs it". Today the floor does
not do that. The glass-v3 README states that over a shader look the bound is the look's hard cap, unblurred
(`LiveSpots.of`), "so conservative", and that per-look spots under the real blur is the unbuilt "next step". The draft
promises this as already part of the guarantee. Until that exists, the guarantee is either (a) the conservative cap, which
re-creates cause 1 (39-98 % tints, i.e. frost) for every look, or (b) an unproven blurred estimate. Further gaps:
- Spots are discrete (darkest / brightest per look colour). A moving field with text lines drifting under a pane can
  produce local contrast the spot set does not include, especially for Clear at 2-4 dp blur where text behind is partly
  legible. The floor reasons about colour, not high-frequency content. The doc's `BLURRED_INK` = .20 assumption (ink
  covering 20 % of a line) holds only at 7 dp over 14 sp words, and the doc itself restricts Clear's 2 dp to pills,
  previews and the +. That makes Clear's bars/composer 5 dp, which is lower than the 7 dp the constant was calibrated for.
  `BLURRED_INK` must be recomputed per look and per role.
- Pills (2 dp) carry words (chips, jump pill) over moving transcript text, in Clear. 2 dp is not a frost; it is a see-through
  pane with legible text beneath legible text. This is the one place the "text readable" owner rule is most at risk and the
  doc waves it through as "pills, previews and the +".
- Photo and user-image backgrounds: the doc says "over a photo it can end up as thick as Glossy" - then Clear is not a look.
Recommend: make per-look blurred spot measurement and `BLURRED_INK` per recipe a precondition of phase 1, with a test that
runs a text-line stress backdrop (high-frequency stripes at the words' scale), not just colour spots.

### 2. Blocker. Clear and Glossy collapse under the floor, and the doc's own table predicts it
Scope: section 2 table, Risks first bullet, D3.
Starting tints are Clear 0.10/0.14 and Glossy 0.16/0.22 - a 6-8 point gap. glass-v3 shows the floor lifting tint to
14-34 % over the painted aurora (16-34 % dark/light for content/chosen) and 39-98 % over shader looks, ahead of any look
difference. After thickening, both sit at the floor's value; the surviving differences are blur 2/4 dp vs 4/8 dp (and bars
never under 5 dp, so equal), saturation 1.25 vs 1.6, and gloss. Gloss is the part the floor strips first in dark mode
(cause 2: 0-4 of 31-62 panes keep it; Glossy's brighter sheen is exactly what fails the words test hardest). So on dark
Graphite, Clear and Glossy differ by a rim alpha and a saturation notch. The mockups are CSS, tints "set by eye" (mockups
README item 3), so the comparison images cannot show this; the doc's own mockups note says Clear/Glossy/Frosted "look much
alike" over colour. Recommend: before any build, compute the post-floor tint and gloss-kept count per look from
`GlassPanesTest`, and require a measurable gap (for example at least 8 tint points or visible specular) between adjacent
looks over both backgrounds. If not met, cut Clear (D3 B) now; do not wait for renders.

### 3. Blocker. Performance: "one recording" does not remove four lens-and-blur passes, and nothing is measured on a mid-range phone
Scope: section 5.
- One shared `GraphicsLayer` saves recording, not per-pane cost. Each preview still runs saturation/contrast matrix, blur,
  runtime-shader lens (AGSL) and tint/sheen/specular draws over that layer every time it changes. Four panes plus the sheet's
  own face and the Appearance page behind it (in-flow panes blurring the same recording, per section 4) is six or seven
  blur-plus-lens passes per frame while the field moves. The budget cap of 4 plus the field is described as "cost 1" but
  `LiquidEdge.BUDGET` is a count of surfaces, not a GPU cost model; an unbounded in-flow pane list behind the sheet is
  explicitly outside it.
- The only evidence in the repo: glass-v3 on a software-GPU emulator, one shader backdrop plus the page's panes, p50 133
  -> 150 ms, p99 150 -> 200 ms, +13 % at p50. That is already unusable-looking and is not a phone. There is no mid-range data.
  "Pass bar: janky frames < 5 %" is on the owner's phone only, which is the highest tier the owner has, not the floor.
- "Re-records at most 30 times a second, skipping vsyncs" - the panes consume a version-changed layer, so panes redraw at
  30; at 120 Hz that is uneven frame pacing (each other frame skipped) and will look worse than a steady 60. Say which.
- The sheet opens over a page whose chrome "drops to frost" - an extra shader/recording switch during the open animation,
  when the field starts, is the worst moment for jank. Not discussed.
- Fallback ordering: 15 fps, then still. A 15 fps field with a 200 ms tween on tap reads as stutter. Prefer pre-deciding the
  still field with a 600 ms drift as the default on low-tier devices (device class gate), not as a late fallback.
Recommend: a device-tier gate (RAM/refresh/GPU class) defaulting the field to still on mid and low tiers; measure on a
real mid-range device (not the owner's) and the emulator with a hardware GPU; make the field's frame cost a budgeted number,
not "cheap".

### 4. Blocker. Migration drops a stored choice and contradicts how the key is stored
Scope: section 3 "Storage".
Read `engine/settings/.../Preferences.kt:141`: the write path stores `oc.glassEdges` as `null` (removes the key) when the
value equals the default (`Liquid`). Consequences of the proposed fallback ("if `oc.glassLook` is missing, read
`oc.glassEdges`: frosted -> Frosted, anything else -> Glossy"):
- A user with `oc.glassEdges=frosted` who picks Glossy: if `glassLook` is written default-omitted like every other
  preference (`Glossy` is the new default), the key vanishes and the read falls through to `glassEdges=frosted` -> the user
  is back on Frosted after restart. The fallback must not live beyond one explicit one-time migration that writes
  `oc.glassLook` and then deletes `oc.glassEdges`.
- Current Liquid users (key absent) silently change from today's liquid look to the brighter Glossy. That is the default
  change the owner has not seen yet (D4). Their old "Liquid" was neither Clear nor Glossy; map to the look closest to
  today's values and make Glossy opt-in until approved.
- Frosted users: today's Frosted is "blur only and the old thick Flutter panes" (`clearPanes` false for Frosted). The doc
  says Frosted "no longer brings back the old thick Flutter panes: its own milk replaces them". That is a visible change for
  people who chose Frosted precisely for the old behaviour. Say so; test it.
- Wiring: three parallel enums (`GlassEdges` in design, `KitEdges` in settings, `StoredEdges` in prefs) are converted by name
  (`PreferencesChoices.kt`). A fourth value is not additive there; the doc names none of it.
- Downgrade: an older build ignores `oc.glassLook` and `oc.glassEdges` was deleted - rollback loses the choice.
Recommend: one-time migration with explicit write-then-delete; store the new key even for the default; list the three enum
sites; test the frosted -> glossy -> restart round trip.

### 5. Major. Smoked breaks the ink and accent model
Scope: section 2 Smoked, D6.
Near-black face in light mode flips text to the dark theme's ink, and accent to the dark theme's accent. `LiquidInk` flips
today based on a sampled backdrop for floating panes; Smoked flips by decision, for in-flow panes too (cards, row groups),
mixing light-theme page chrome with dark-theme ink inside panes. Interactions the doc does not cover: focus rings, scrim
and shadow colours, status colours (error/warn tuned for a light ground), icons that sample the ground, high-contrast hold
(returns Solid light: Smoked disappears). Also "near-black `#0A0B0D` in both modes" at 0.38-0.42 tint over a bright light
page is a mid-grey, not a car window; and the stated test "measure the dark face over the light backdrop's brightest spot"
tests only the best case for light words, not the worst (dark spot of a light page behind 0.38 smoke is nearly pure dark, fine,
but a saturated bright spot gives muddy low-chroma pane, which is where it looks artificial).

### 6. Major. Mixed-theme and photo flip rules are described as existing but are new interactions
Scope: Readability bullets, mockups 12/13.
"A light-mode Clear or Glossy message box over a dark photo flips to the dark theme's ink and face rather than
thickening". `LiquidInk` exists, but the flip applies to the message box only; chips, bars, sheets keep light ink over the
same photo. Two adjacent panes (message box dark, chips light) over one photo is the exact mismatched look that reads
fake. Specify the unit that flips (a stack? the whole composer cluster?) and the hysteresis so it does not flicker as the
field moves.

### 7. Major. Preview honesty: "each pane shows its own readability" cannot be verified
Scope: section 4.
Preview panes carry words on moving text/colour; the doc says the picker shows "the real thickened face, never an
idealised one", but the picker's backdrop (its own field) is not any user backdrop. The thickened result on the field
differs from the thickened result on the user's chosen look. After picking, the user sees a different (thicker) glass than
the preview, which is the "preview does not equal applied" failure the owner has named before. Show the post-floor result on
the user's actual current background (or both), or disclose.

### 8. Minor. Frame-time claim about reduced motion and the TalkBack/accessibility statement
- "Reduced motion: one still frame" - but the 200 ms face tween on tap is a motion; under Calm/Off confirm it is a cut.
- RTL "grid mirrors, specular stays top-start" - top-start moves right in RTL, which also moves the rim segment away from the
  light source convention used in the other styles; confirm the owner is fine with a flipped light direction.

### 9. Minor. Gate 0 asks the owner to judge on emulator renders
Scope: section 6.
Emulator renders (swiftshader) differ in blur, color and tone from a phone display; the owner's earlier complaints came
from device use. Include phone captures for the gate, one screen per image, with the glass at real size.

## What holds up
- Keeping the floor and the holds, and "Nothing ever reads worse than Solid", is the right invariant.
- Bars/composer never under 5 dp is correctly called out; extend the same reasoning to pills.
- Gate 0 before picker work is correct; make it the first deliverable and pass/fail.
