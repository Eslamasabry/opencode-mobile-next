# Glass looks: several kinds of glass, picked from live previews

**Status:** v2, 2026-11-09, after three goldfish reviews. The reviews are in `docs/designs/reviews/glass-looks-*.md`
(commit 74b05a976); v1 is in this file's history. Next: the owner picks from the mockups. Nothing gets built until
gate 0 and gate 1 (§6) pass.

**Owner, 2026-11-09:** "Why is glass so frosty and not glossy? Can we have multiple glass options for users to decide? A
bottom sheet with glass previews and a color motion behind all the glass previews, so the user can see how it looks."

**Mockups:** `/home/eslam/Storage2/mockups/2026-11-09-glass-looks/` (one phone screen per image; README there). They
are CSS stand-ins with by-eye tints. They show layouts and character, not measured glass.

**Base:**
- `native/glass-live` e20eb5c6a: `GlassLook` in `:ui:design` (`style/GlassLook.kt`), holding `Default` (clearer and
  glossier) and `Frosted` (glass-v3's values). GlassSpec, Surface, LiquidEdge and the kit already read it.
- The per-look measured floor (`LiveReach`) is still in progress on that branch.
- This design starts from that branch's merge into `native/next`, and names the merge commit when it lands.

**Keeps:**
- `liquid-glass.md` v2 plus the owner override:
  - liquid glass on floating pieces only, behind `kitLiquidFace`;
  - `FrostPolicy` and its holds;
  - `LiquidEdge.BUDGET`;
  - no glass on glass;
  - no dispersion.
- glass-v3's crisp neutral rims, no halos, the pane inset, and the shader look recorded once.
- composer-edge's scroll-edge fades.
- theme-switch-premium's motion levels.
- Owner rules: no haze plates; accent only on selected states; no boxed icons or pill badges; no new UI levels;
  readable text; sheets and option cards with real previews.

## Changes from v1

1. **Sequenced behind the frost fix.** Gate 0 is the glass-live agent's per-look measured floor and gloss fix, already in
   progress. The owner judges it on real phone captures before any look or picker work. v1 built the feature first.
2. **Measured, not by eye.** Each look's tints come from `GlassPanesTest` after the floor, never from a starting table.
   v1's 17-row table of numbers is gone. Looks are now a few levers over the existing `GlassLook` (§2), and the numbers
   live in `GlassLooks.kt` after gate 1.
3. **Clear must earn its place.** Clear and Glossy both ship only if a measured gap survives the floor, in dark and in
   light, over the aurora and the shader looks (gate 1). Otherwise three looks ship: Glossy, Frosted, Smoked.
4. **Glossy is calm.**
   - Glossy is `GlassLook.Default`, exactly what glass-live ships.
   - The lower-edge light return and the bottom shade are gone.
   - Saturation is raised only as far as the floor still passes with the look's starting tint.
   - The default look is therefore not a new, brighter look: users see glass-live's own change and nothing more.
5. **Migration.** A one-time write-then-delete migration replaces v1's indefinite fallback read: `Preferences.kt` drops
   default-valued keys, which made that read lose choices. The change to Frosted for existing users is flagged (D7).
6. **Readability.** The single `BLURRED_INK` (.20, calibrated at 7 dp) becomes a function of the blur radius. A new
   text-stripe stress backdrop covers transcript text drifting under thin glass. Every role that carries words gets a
   minimum blur from that test, not 5 dp by assertion.
7. **Performance.**
   - Each preview pane has a cost budget.
   - Previews are cached stills, and only the selected one is live if the budget requires it.
   - The field's cadence divides the panel rate (60 fps on 60 and 120 Hz, 45 on 90 Hz), not 30.
   - It is measured with the existing `FieldFrameStats` (the JankStats rule, no new dependency) and `dumpsys gfxinfo`.
   - It is tested on a mid-range phone, not only the owner's.
8. **Smoked is limited** to floating pieces, and the whole chrome flips as one unit. The ink-flip unit for photos is
   specified (§2).
9. **Implementation detail added:**
   - the storage keys and the three enum sites;
   - the `FrostPolicy` signature;
   - the per-pane look override for previews;
   - the tween's interpolated set;
   - write failure;
   - holds in the picker;
   - string keys;
   - the tests;
   - owners and merge order.

## 1. Why today's glass reads frosty (unchanged findings, now owned by gate 0)

1. Over the shader looks, the floor uses the look's unblurred hard cap, so panes thicken to a 39–98 % tint. The fix is
   per-look spots measured under the real blur (glass-live, `LiveReach`).
2. In dark mode, `GlassFinish.thickened` drops the lit corner and the sheen first. Gloss survives on only 0–4 of 31–62
   panes.
3. A 7–12 dp blur takes the detail that makes glass read as clear.
4. The light theme's white tint at 0.30–0.40 reads as milk.
5. The highlights are deliberately quiet.

glass-live addresses 2–5 in `GlassLook.Default`, and 1 in `LiveReach`. If gate 0 satisfies the owner, the looks
feature is about choice, not about fixing frost.

## 2. The looks

A look is a `GlassLook` preset plus at most four liquid levers. The per-role `LiquidEdge.recipe(role, dark)` values stay
as they are today (bar 7 dp blur and 34 lens, composer the same, sheet 6/8 and 44, menu 4/6 and 36, pill 2 and 12,
action 1 and 26). A look changes them only through these rules:

| Lever | Rule | Clamp |
|---|---|---|
| Lens | on or off per look | off is not a continuous parameter: see the tween (§4) |
| Liquid blur | `max(role blur, minWordsBlur(role, look))`; Frosted ×2 | pill ≤ 6 dp, action ≤ 4 dp, sheet ≤ 16 dp |
| Starting tint | the role's tint × the look's factor; the floor then thickens it | never below the floor, never above Solid |
| Tint tone | the theme's ground (all), except Smoked: the dark theme's ground in both modes | — |
| Clear panes and gloss | the look's `GlassLook` preset | saturation ≤ the highest value at which the floor passes with the starting tint |

| Look | `GlassLook` | Lens | Blur | Tint factor | What it is |
|---|---|---|---|---|---|
| **Glossy** (default) | `Default` | on | role | 1.0 | glass-live's glass: clear, crisp, a calm specular and glint |
| Clear (conditional) | `Default` with 6 dp clear panes and specular at 0.8× | on | role, at least `minWordsBlur` | 0.7 | thinner and more see-through, less shine |
| Frosted | `Frosted` (glass-v3's values) | off | role ×2 | 1.3 | milky blur; the most readable; works on Android 12 |
| Smoked | `Default` | on | role | dark ground, starting at 0.40 | dark tinted glass, floating pieces only |

**Glossy's gloss** is `Default`'s specular (0.80 / 1.0), glint (0.75 of it) and liquid rim (0.46 / 0.62). There is no
lower-edge return, no bottom shade and no extra saturation. The owner's "very artificial" (10-26) is a constraint here,
not a risk bullet.

**Why Clear is conditional (gate 1).** After the floor, `GlassPanesTest` must show both of these between Clear and
Glossy, for every pack, in both modes, over both the aurora spots and the per-look shader spots:
- at least 8 tint points of difference on the median pane;
- or a gloss-kept count that differs by at least a quarter of the panes.

If either mode fails, Clear is cut and three looks ship.

### Readability (the floor, recalibrated)

- **Unchanged invariant:** text1, text2 and text3 reach min(4.5:1, Solid's) + 0.04; icons and track edges reach 3:1;
  nothing reads worse than Solid. The thickening order is unchanged: tint first, then the lit corner, then the sheen,
  then Solid.
- **`BLURRED_INK` becomes `blurredInk(blurDp, wordsSp = 14)`.** The share of ink a line of words keeps after a Gaussian
  blur of that radius, computed once from a rendered 14 sp line (stroke coverage convolved with the kernel). At 7 dp it
  must reproduce .20 (a regression check). At 5 dp and 2 dp it is higher, which makes the spots harsher and the floor
  thicker. That is the honest cost of thin glass.
- **Stress backdrop for drifting text.** A new spot source draws stripes at the words' scale: 14 sp lines of text1 on the
  ground, in every phase of a 24 sp line pitch, blurred by the role's blur. It joins the colour spots for every role
  that carries words. Because the floor takes the worst phase, text drifting under a pane cannot open a gap. The test
  "Clear pill over drifting transcript" fails before `minWordsBlur` exists and passes after.
- **`minWordsBlur(role, look)`** is the smallest blur at which the blurred stripes stay under 1.5:1 against each other,
  so the text behind is colour, never words. It applies to every role that carries words: bar, composer, pills, sheet
  and menu. For a pill this likely means 4–5 dp, not 2.
- **Ink flip unit.** On floating chrome, `LiquidInk` decides per **cluster**, not per pane:
  - the header capsule is one cluster;
  - the bottom block (the chips, the jump pill and the message box) is another.

  So over a dark photo, the light-mode message box and the chips beside it flip together. The decision uses the
  cluster's mean sampled backdrop, with today's hysteresis and 180 ms cross-fade. This is a change in
  `KitLiquidGlass.kt`.
- **Smoked:**
  - Floating pieces only. In-flow panes keep `Default`'s clear panes in the page's own ink, so a light page never mixes
    dark-ink cards into it.
  - Smoked chrome uses the dark theme's ink, accent and status colours as one cluster decision, so no light-theme status
    colour sits on a dark face.
  - The floor is measured over the light page's brightest and most saturated spots, the case that turns a smoke face
    muddy.
  - Under high contrast, Smoked returns Solid like every look, and Settings says why.

## 3. Storage and the old switch

- **One key, one enum.** `oc.glassLook` (`glossy | clear | frosted | smoked`) is device-wide and never swept, like
  today's key. `GlassEdges` and `oc.glassEdges` are removed with the migration, not kept as a fallback.
- **One-time migration in `engine/settings` at preferences load:**
  1. If `oc.glassEdges` is present, write `oc.glassLook` from it (`frosted` → `frosted`; anything else → nothing, which
     means the default) and remove `oc.glassEdges`, in one edit.
  2. Nothing ever reads `oc.glassEdges` again. After that, `oc.glassLook` can be default-omitted, like every other key
     (`Preferences.kt:141`'s rule), because no fallback remains to resurrect an old value.
  3. Tests:
     - absent → Glossy;
     - `liquid` → Glossy;
     - `frosted` → Frosted, with the old key gone;
     - unknown → Glossy;
     - **frosted → pick Glossy → restart → Glossy**;
     - a second launch is a no-op.
  4. A downgrade to an older build loses a Frosted choice. That's acceptable: native is unreleased and has one user.
     It's written in the release notes of the build that migrates.
- **Code sites:**
  - the three enums mapped by name in `PreferencesChoices.kt` (lines 88, 142 and 174: design `GlassEdges`, `KitEdges`,
    `StoredEdges`) become `GlassLook` keys;
  - `PersonalChange.Edges` and `PreferenceChange.Edges` become `.GlassLook`;
  - `SettingsPresenter.onGlassEdges` becomes `onGlassLook`;
  - `glassEdgesHold` becomes `glassLookHold`;
  - `FakeSettingsPorts`;
  - the `PreferenceKeys` sets (lines 23, 52 and 65).
- **`FrostPolicy`:**
  - `edgesHeldBy(c, strength, look: GlassLookId)` returns `EdgeHold.Frosted` when the look has no lens, and `Platform`
    when the lens is missing.
  - `clearPanes(...)` stays true for every look except holds.
  - About 25 test call sites in `LiquidEdgeTest`, `GlassPanesTest` and `KitLiquidGlassTest` move to the new parameter.
- **Frosted for existing Frosted users (flagged, D7).** Today, Frosted brings back the old thick Flutter panes
  (`clearPanes` is false). The new Frosted look draws `GlassLook.Frosted`'s lighter clear panes with no lens. That is a
  visible change for someone who chose Frosted. The alternative keeps the thick panes for Frosted.
- **Frost strength** (Off / Weak / Balanced / Strong) is not exposed in native, and stays at Balanced. Off remains a
  hold.

## 4. The picker

**Where it opens** (D5):
- the **Glass look** row, which replaces Glass edges under Corners and spacing;
- re-tapping the chosen Glass card. Its second line names the look.

**If a hold applies** (Saver, reduced motion, high contrast, frost Off, or another style), the row is disabled and shows
the reason as a note. The sheet does not open, because every preview would look the same.

**The sheet:** a `KitSheet` titled "Glass look", with a close button.
- **Previews** (layout: D1): four panes, one per look, laid over the field (D2). Each pane draws its look through a
  **per-pane look override**: a `look` parameter on the liquid face, not a change to the global `Appearance`. Each
  pane shows its look's name and one line, on the glass, with the chosen ring and a check on the selected one.
- **Disclosure line:** "The previews use bright colours so the differences show. The page behind shows your real
  background." The page behind the sheet is the real result.
- **On tap:**
  1. The look is written through the existing path; on failure it reverts and shows `SettingsNote.AppearanceNotSaved`.
  2. Rapid taps: the last tap wins after 300 ms.
  3. The page and the sheet move to the new look:
     - the tint, rim, sheen and specular are interpolated **after** the floor thickens each face (200 ms);
     - the lens's refraction height animates to or from 0 over that tween, then the lens switches on or off at the end;
     - the blur steps at the midpoint.

     Under Calm, Off or reduced motion this is an instant cut.
  4. The haptic tick fires, following the Haptics setting.
- **Kit parts:** `KitSheet`, `KitGlassLookPreview` (new: look, source, selected, onSelect, semantics), `KitLookField`
  (new) and `KitGroupNote`. Each preview is a radio with its name and line. The field is hidden from TalkBack.
- **RTL and text size:** RTL mirrors the grid and the top-start specular, as every Glass face does today. Frame text is
  capped at 1.3×.
- **Words:** the keys are:
  - `appearanceGlassLook`, `appearanceGlassLookPick`;
  - `appearanceGlassLookGlossy`, `appearanceGlassLookGlossyLine`;
  - the same name and line pair for Clear, Frosted and Smoked;
  - `appearanceGlassLookDisclosure`;
  - `appearanceGlassLookHeld*`, one per hold, reusing the existing hold reasons where they exist;
  - `appearanceGlassCardLook`, for "Glass · %s".

  They go in English and Arabic. `appearanceGlassEdges*` is removed.

## 5. Performance

1. **One field recording.**
   - The field is drawn into one `GraphicsLayer` at half resolution, the `rememberLiveShaderBackdrop` pattern.
   - The previews sample it through the existing `KitLiquidSource`. They claim from the window's existing
     `LiquidEdge.grant` with a scoped claim group, not a second budget type.
2. **A cost budget per preview pane.** Each pane's blur and lens pass is about 165 × 190 dp. On the reference
   mid-range device, the four panes together must fit in 4 ms of GPU time per field frame, measured with
   `FrameMetrics`' GPU duration.
3. **Stills first.**
   - Each preview is rendered once over a still frame of the field into a cached `ImageBitmap` when the sheet opens.
   - If the budget passes, the selected preview is live over the moving field, and so are the other three.
   - If it fails, only the selected preview is live; the other three stay cached stills, re-rendered when the selection
     changes.
   - Low-tier devices (under 6 GB RAM, or no runtime shaders) get a still field and still previews, plus a 600 ms drift
     after each tap.
4. **Cadence.**
   - `LiveFrameClock` gains a `cadence` parameter (it has none today; the change and its test are in phase 2).
   - The field steps at the panel rate divided by the smallest n that gives 60 fps or less: 60 on 60 and 120 Hz, 45 on
     90 Hz, 48 on 144 Hz.
   - The panes redraw only on a new field version.
5. **Stops when unseen.** The field clock pauses:
   - on `ON_STOP`;
   - on sheet dismiss (it releases the layer);
   - when the sheet's settled state drops below half (`KitSheet` exposes its detent state).

   It rests 30 s after the last touch. The sheet's open animation runs over a still field, which starts after the
   sheet settles, so the page's switch to frost and the field's first frame never share a frame.
6. **Measurement:**
   - the existing `FieldFrameStats` (`android/platform`: FrameMetrics, JankStats' jank rule, no new dependency);
   - `dumpsys gfxinfo` for p50, p90 and p95.

   The baseline is the conversations list in Glass over Aurora drift, 10 s, the same build. It is recorded on the owner's
   phone **and** on a Firebase Test Lab mid-range Android 13+ device.

   **Pass:** janky frames under 5 % and p95 within 1 ms of the baseline on both devices, with zero frames while the
   field rests.

## 6. Phasing

0. **Gate 0, the frost fix (glass-live, in progress).**
   - `GlassLook.Default` plus the `LiveReach` per-look measured floor land on `native/next`.
   - The owner judges them on **phone captures**, one screen per image: chat, the conversations list and a sheet, over
     the painted aurora and Aurora drift, light and dark, with today's Frosted and Solid as controls.
   - If the owner is satisfied, the looks feature continues for choice. If not, glass-live iterates. Nothing below
     starts first.
1. **Gate 1, the measured looks.**
   - `GlassLooks.kt` candidates;
   - `blurredInk(blurDp)`;
   - the stripe stress backdrop;
   - `minWordsBlur`;
   - `GlassPanesTest` over 4 looks × every pack × light/dark × (aurora, per-look shader, stripes).

   The output is a table of post-floor tint and gloss-kept per look and role. It decides Clear (§2) and fixes every
   number. If the matrix (about 10× glass-v3's 992 panes) exceeds the fast lane's budget, it moves to the full lane, with
   one pack per look kept fast.
2. **Kit:**
   - the look levers in `LiquidEdge`;
   - the cluster ink flip;
   - the per-pane look override;
   - `KitGlassLookPreview` and `KitLookField`;
   - the `LiveFrameClock` cadence;
   - the scoped grant.

   Tests: budget (four previews plus the sheet's face), still field under every hold, no frames at rest, the radio
   semantics, an RTL golden, a 1.3× golden, and pixel equality for the other styles. Goldens: 3–4 looks × 2 modes × the
   preview sheet only, in the full lane.
3. **Settings:**
   - the migration;
   - the enum collapse;
   - the presenter and `AppearancePage`;
   - the words in English and Arabic.

   Tests: the migration matrix (§3), `onGlassLook`, the holds, and write failure.
4. **Proof:**
   - phone and mid-range captures of each look;
   - a video of the sheet;
   - `FieldFrameStats` and gfxinfo numbers;
   - evidence under `docs/native/qa/glass-looks-<date>/`.

**Owners and merge order:** glass-live merges first. Then one kit builder owns `:ui:design` effects and style
(`LiquidEdge.kt`, `GlassFinish.kt`, `FrostPolicy.kt`, `GlassLook*.kt`) and `:ui:kit` glass (`KitLiquidGlass.kt`,
`KitSurfaceGlass.kt`, live). Then one settings builder owns `engine/settings` (`Preferences.kt`, `PreferenceKeys.kt`) and
`:feature:settings` (`PreferencesChoices.kt`, `SettingsPresenter.kt`, `AppearancePage.kt`, the words). The coordinator
names both agents at launch.

## 7. Risks

- **Gate 0 may make the feature unnecessary.** That is a good outcome. Ship the fix, and then ask whether choice is
  still wanted.
- **Three looks, not four.** Gate 1 cuts Clear by rule.
- **"Very artificial" again.** Glossy is `Default`, and the owner judges it on phone captures before any picker exists.
- **Preview ≠ applied.** The field is brighter than any real background. The disclosure line and the live page behind
  the sheet are the honest view; if the owner prefers it, D2 C shows the real background instead.
- **Mid-range jank.** Stills first, the device tier and the cadence rule cover it; the numbers decide.
- **Frosted users see lighter panes** (D7).

## 8. Owner decisions (A/B/C, with a recommendation)

| # | Question | A | B | C | Recommend |
|---|---|---|---|---|---|
| D1 | Picker layout | A row of large previews (01) | **A 2×2 grid** (02) | A plain list; the page behind is the preview (03, without the top preview) | **B**, with C as the fallback if the budget fails on the mid-range phone |
| D2 | Behind the previews | Colour only (04) | **Colour plus drifting text, with the disclosure line** (02) | Your own background (05) | **B:** differences show; the page behind is the truth |
| D3 | Which looks | Four | **Glossy, Frosted, Smoked, plus Clear only if gate 1 measures a gap** | Two: Glossy and Frosted | **B** |
| D4 | Default | **Glossy = glass-live's `Default` (no extra change)** | Frosted | Clear | **A** |
| D5 | Where it opens | The row (06) | Re-tapping the Glass card (07) | **Both** | **C** |
| D6 | Smoked | **Floating pieces only, dark glass in both modes** (11, 15) | Dark mode only | Drop Smoked | **A** |
| D7 | Frosted for current Frosted users | **The new lighter panes, no lens** | Keep today's thick Flutter panes | — | **A**, matching the owner's 11-08 "much lighter" for in-flow panes; flagged in the release notes |

The mockups 08–15 show each look on the chat over the painted aurora. They were re-rendered for v2: Glossy is calm, with
no lower-edge return, no shade, and saturation at Default's level.
