# Glass looks: several kinds of glass, picked from live previews

**Status:** v1 draft, 2026-11-09 (the elephant). Next: independent goldfish reviews, then v2, then the owner picks from
the mockups. Nothing gets built from this version.

**Owner, 2026-11-09:** "Why is glass so frosty and not glossy? Can we have multiple glass options for users to decide? A
bottom sheet with glass previews and a color motion behind all the glass previews, so the user can see how it looks."

**Mockups:** `/home/eslam/Storage2/mockups/2026-11-09-glass-looks/` (one phone screen per image, README there).

**Keeps these decisions:**
- `liquid-glass.md` v2 and the owner's override ("go full blown glass"): liquid glass on floating pieces only, behind one
  kit entry point (`kitLiquidFace`); `FrostPolicy` decides, with its holds; the window budget (`LiquidEdge.BUDGET`); no
  glass on glass; dispersion off.
- glass-v3 (2026-11-09): crisp 1 dp neutral white rims, no shadow halos, the top-start specular and glint, the Glass pane
  inset, and the shader look recorded once into the glass backdrop.
- composer-edge (2026-11-09): no ground bands behind the chrome; the transcript fades at the scroll edges and the glass
  samples the whole recording.
- theme-switch-premium.md: the motion levels (Full, Calm, Off) and "no replay on programmatic changes".
- Owner rules: no haze plates behind text; neutral white rims; accent only on selected states; no boxed icons; no pill
  badges; no new UI levels; text stays readable (the contrast floor); bottom sheets and option cards with real previews.

**Depends on:** the tint and gloss rework now in progress, which exposes a `GlassLook` spec. This feature adds named
presets over that spec, a stored choice and a picker. It does not change the floor maths.

## 1. Why today's glass reads frosty

These causes are measured in the glass-v3 proof, `docs/native/qa/glass-v3-2026-11-09/README.md`.

1. **The floor makes panes thick over the live looks.** Over a shader look, the contrast bound uses the look's hard cap,
   unblurred. So the clear panes thicken to a 39–98 % tint (Graphite: 40–75 %), against 14–34 % over the painted aurora.
   A 60 % tint is frost, whatever the blur. The README already names the fix: measure each look's real spots under the
   real blur.
2. **In dark mode, the gloss is dropped first.** When a face misses its target, `GlassFinish.thickened` removes the lit
   corner, then the sheen. "Gloss kept" over the aurora in dark mode is 0–4 of 31–62 panes. Dark Glass has almost no
   shine left.
3. **The blur takes the detail.** Liquid bars and the composer use a 7 dp blur, and in-flow panes 10–12 dp. Glass looks
   glossy when you can see sharp detail through it, with bright highlights on top. A blur turns that detail into soft
   colour, and soft colour reads as frost.
4. **Light mode is milky.** The light tint is the white surface at 0.30–0.40, over a 1.45–1.7× saturation lift of pastel
   aurora colours.
5. **The highlights are deliberately quiet.** There is a 1 dp rim, a 2.5 dp glint fading out within 120 dp, and a sheen
   capped at 40 dp. Nothing catches light broadly, and no light comes back off the lower edge.

So one "Glass" can't be both glossy and the safest to read. This design names a few honest points on that line and lets
people pick, while keeping the floor everywhere.

## 2. The looks

Each look is a named preset of the `GlassLook` parameters. The values below are starting points for the gate renders,
not final numbers. The final numbers live in one table in code (`GlassLooks.kt` in `:ui:design`), next to today's
`LiquidEdge.recipe`. Each look scales today's per-role recipe (bar, composer, sheet, menu, pill, action), so the role
differences stay.

| Parameter | Clear | **Glossy** (default) | Frosted | Smoked |
|---|---|---|---|---|
| Idea | Very see-through, light shine | Clear glass with strong shine: the liquid-glass hero | Milky blur, easiest to read | Dark tinted glass, like a car window |
| Starting tint, dark / light | 0.10 / 0.14 | 0.16 / 0.22 | 0.30 / 0.48 | 0.42 / 0.38 |
| Tint tone | the theme's ground | the theme's ground | lifted: ground +12 % white (dark); white (light) | near-black `#0A0B0D` in both modes |
| Blur, floating / in-flow (bars and the message box never under 5 dp) | 2 / 6 dp | 4 / 8 dp | 14 / 16 dp | 6 / 10 dp |
| Refraction (the lens) | 30 dp in a 22 dp rim | 40 dp in a 22 dp rim | none | 30 dp in an 18 dp rim |
| Top-start specular, dark / light | 0.45 / 0.75 | 0.80 / 0.95, plus a weak lower-end return | 0.30 / 0.55 | 0.70 / 0.80 |
| Broad sheen (top 40 dp), dark / light | 0.06 / 0.16 | 0.16 / 0.36 | 0.08 / 0.20 | 0.12 / 0.18 |
| Glint line | on | on, brighter | off | on |
| 1 dp rim, dark / light | 0.20 / 0.50 | 0.30 / 0.65 | 0.18 / 0.45 | 0.22 / 0.30 |
| Saturation lift | 1.25 | 1.6 | 1.5 | 1.1 |
| Contrast press | 0.95 | 0.92 | 0.86 | 0.80 |
| Needs Android 13 for the lens | yes | yes | no (blur only) | yes |

**What is the same in every look:**
- Bars and the message box never blur under 5 dp, so no readable text sits behind their words. This is today's
  `LiquidEdge` rule for those roles ("frost it to colour and shape"), and the floor's `BLURRED_INK` assumption depends
  on it. Clear's 2 dp applies to pills, previews and the + only.
- Rims are neutral white.
- The accent appears only on selected states.
- No shadow halos.
- In-flow panes never get the lens: they take the look's tint, blur and gloss only (owner rule: liquid on floating
  pieces, lighter frost in-flow).
- Holds win: Saver, reduced motion, high contrast and frost Off keep today's held faces in any look.

### Readability guarantee (the floor still applies)

The look sets only the **starting** tint and the gloss. The existing floor then does what it does today:
- `LiquidEdge.faceTint` for floating panes, and `GlassFinish.thickened` for in-flow panes.
- The face thickens until text1, text2 and text3 reach min(4.5:1, Solid's) + 0.04, and icons and track edges reach 3:1.
- This is measured over the backdrop as **this look** presses and blurs it: its saturation, press and blur.
- Nothing ever reads worse than Solid. The last fallback is still the opaque Solid face.

Per look:
- **Clear:** less blur leaves a wider spread of colours behind the words, so the floor thickens Clear more over busy
  content. Over a calm background it stays thin; over a photo it can end up as thick as Glossy. The picker shows the
  real thickened face, never an idealised one. Adaptive ink (`LiquidInk`) keeps working on floating panes. A light-mode
  Clear or Glossy message box over a dark photo flips to the dark theme's ink and face rather than thickening into milk
  (mockups 12 and 13).
- **Glossy:** its gloss is the shine, not extra clarity. That means:
  - a wider and brighter top-start rim segment;
  - a broad sheen in the top 40 dp;
  - a bright glint;
  - a faint return of light at the lower end;
  - a slight shade towards the bottom, so the face reads curved.

  The brighter sheen and specular stay in the top 40 dp and the top-start corner. The contrast test measures
  words where they lie (`GlassFinish.sheenAt`, `PANE_WORDS_DP`), including Glossy's stronger sheen. Where the sheen
  would take words below target, the existing order applies: the lit corner goes first, then the sheen.
  - Risk: in dark mode that could strip the gloss again (cause 2). The gate renders must show Glossy dark keeping its
    specular. The specular is a rim segment, so it never lies under words, and only the sheen may be dropped.
- **Frosted:** the most blur and the thickest milk, so the floor rarely has to add anything. It is the recommended look
  over busy shader backgrounds and the one the hold notes point to.
- **Smoked:** the face is near-black in **both** modes, so its words are the dark theme's ink in light mode too: the
  `LiquidInk` flip, decided by the look rather than sampled.
  - Light words on a 0.38+ smoke over a light page pass easily. The test measures the dark face over the light
    backdrop's brightest spot.
  - Selected states keep the accent, using the dark theme's accent on smoked faces.

**Tests:** `GlassPanesTest` and `LiquidEdgeTest` gain the look axis: 4 looks × every pack × light/dark × aurora spots
and per-look shader spots. `ChromeEdgeBlendTest` runs per look. A pixel-equal test checks that every non-Glass style is
unchanged.

## 3. How this relates to "Glass edges: Liquid / Frosted"

**Merge it.** The Liquid/Frosted segmented control under Corners and spacing becomes one row, **Glass look**, in the
same place. It opens the picker. This adds no UI level: one row replaces one control.

- **Frosted** is a look. It is today's "Frosted" choice in its new form: blur only, no lens. It is still the safe choice
  on Android 12 and the kill switch for the lens.
- Clear, Glossy and Smoked are the "Liquid" side.
- **Storage:** the new key is `oc.glassLook` (`clear | glossy | frosted | smoked`), app-wide like `oc.glassEdges`. If
  it's missing, it reads `oc.glassEdges`: `frosted` maps to Frosted and anything else to Glossy. The app has one user
  today, so there is no other migration.
- `FrostPolicy.edgesHeldBy` reads the look:
  - Frosted gives `EdgeHold.Frosted`, as today.
  - A lens look below Android 13 gives `EdgeHold.Platform`. It draws that look without the lens (its own tint, blur
    and gloss), so Glossy on Android 12 is still glossier than Frosted.
- `clearPanes` keeps the clear in-flow panes for every look. Frosted no longer brings back the old thick Flutter panes:
  its own milk replaces them.

## 4. The picker

**Where it opens** (decision D5):
- the **Glass look** row (replacing Glass edges);
- tapping the Glass style card when Glass is already chosen. The card's second line shows the look ("Glass · Glossy").
  This is a gesture with a visible twin (the row), so nothing is hidden-only.

**The sheet** (a standard `KitSheet`, partial height, about 65 % of the screen):
- **Title:** "Glass look", with a close button. No Done button: a tap applies the look.
- **Preview field:** one rounded area filled by a shared **moving colour field** (decision D2). It is drawn once. Every
  preview pane is drawn with the real kit glass over that one recording.
- **Four preview panes, one per look** (layout: decision D1). Each pane is the look itself, at the floating-pane role,
  with its name and one line inside it:
  - Clear: "See-through, light shine"
  - Glossy: "Bright shine, bends at the edges"
  - Frosted: "Soft blur, easiest to read"
  - Smoked: "Dark tinted glass"

  The words lie on the glass, so each pane also shows its own readability.
- **Selected:** the kit's chosen ring (2 dp accent, `GlassSpec.CHOSEN_RING`) and a check at the top end. This is the
  only accent in the sheet.
- **One note line** under the field:
  - by default: "Tap a look to use it. Everything behind this sheet changes with it."
  - with reduced motion: "Reduced motion is on, so the colours stand still."
  - below Android 13: "Bending needs Android 13. These show the blur and shine only."
- **On tap:**
  - `oc.glassLook` is written at once (the existing Settings action path).
  - The sheet's own face and the page behind it move to the new look. The face's tint, gloss and lens animate over
    200 ms (the kit's standard tween, `MotionLevel`-aware), with no screenshot cover.
  - The haptic tick fires, following the Haptics setting.
- **What changes behind the sheet:** the Appearance page, with its in-flow panes and its bar. The existing rule "a
  sheet open, the chrome under it drops to frost" stays. In-flow panes show the look's tint, blur and gloss, so the
  change is visible without breaking the no-glass-on-glass rule.
- **Accessibility:**
  - Each preview is a radio button with its name and line. The selected state is announced.
  - TalkBack follows reading order.
  - Frame text is capped at 1.3×.
  - In RTL the grid mirrors, and the specular stays top-start, which mirrors already.
  - The colour field is decorative and hidden from TalkBack.
- **Words:** in `strings_settings.xml` and the Arabic file. The look names are short nouns that translate cleanly.

## 5. Performance: four live panes and a moving field in one sheet

1. **One recording.** The field is drawn by one node into one `GraphicsLayer`, at half resolution, then scaled. This is
   the pattern of `rememberLiveShaderBackdrop` from glass-v3. All four previews sample that one layer as their source (a
   kit `KitLiquidSource`). No preview records anything of its own.
2. **The field is cheap.** On Android 13+ it is one runtime shader: the aurora-drift program with the theme's accent,
   partner and code colours, plus the text lines drawn as a static layer that is translated. Below 13 it is four
   radial-gradient brushes on slow paths. There is no per-frame text layout.
3. **Capped frame rate.** The field re-records at most 30 times a second, skipping vsyncs on 60, 90 and 120 Hz panels.
   The panes redraw only when the recording's version changes, as liquid panes already do.
4. **Budget.** The previews claim from a picker-local `KitLiquidBudget`, capped at 4, with the shared field costing 1.
   So they never evict the sheet's own face. The window budget (5) is unchanged.
5. **Stops when unseen.**
   - The field's clock is a `LiveFrameClock` scoped to the sheet. It pauses on `ON_STOP`, when the sheet is dragged
     below half, and on dismiss, when the layer is released.
   - It rests after 30 s without a touch, like the live looks, and wakes on the next touch.
6. **Reduced motion, Calm, Saver:** one still frame of the field. The previews still show the four looks.
7. **Pass bar:** on the owner's phone, measured with JankStats.
   - Open the sheet, leave it 10 s, then tap through all four looks.
   - Janky frames must stay under 5 %, and p95 frame time within 1 ms of the conversations list in Glass over Aurora
     drift.
   - Zero frames while the field is resting.
8. **If it fails:** drop the field to 15 fps. If it still fails, use a still field, with a short (600 ms) drift after
   each tap so the change still reads.

## 6. Phasing

0. **Gate: the looks on real screens, before any picker work.**
   - Emulator renders of each look: chat with header, chips and composer; the conversations list; a sheet. Each over
     the painted aurora and Aurora drift, light and dark. One screen per image.
   - In the same step, measure the per-look shader spots under the real blur (glass-v3's named next step). This alone
     may answer most of "too frosty", so judge Glossy with it.
   - The owner keeps or kills each look. A killed look leaves the set (D3).
1. **Looks:**
   - `GlassLooks.kt` presets over the `GlassLook` spec;
   - `oc.glassLook`, with the fallback read of `oc.glassEdges`;
   - `FrostPolicy` reading the look;
   - the contrast matrix tests.

   Owner: whoever owns `:ui:design` after the tint and gloss rework lands.
2. **Kit parts:**
   - `KitGlassLookPreview`: one preview pane over a given source.
   - `KitLookField`: the shared moving field, its clock and its cap.
   - The picker's sheet content.

   All in `:ui:kit`, with kit tests (budget, still field under reduced motion, no frames at rest).
3. **Settings:** the Glass look row replaces Glass edges; the Glass card's second line and its re-tap; English and
   Arabic words. This is a small hook in `:feature:settings`, done when that module is free.
4. **Proof:**
   - a recorded video of the sheet on the emulator and the phone;
   - renders of each look;
   - JankStats numbers;
   - evidence under `docs/native/qa/glass-looks-<date>/`.

**Interface to freeze with the tint and gloss agent before step 1.** The names are a proposal:
`GlassLook(tintDark, tintLight, tintTone, blurDp, paneBlurDp, refractionDp, rimMaxDp, rimShare, specularDark,
specularLight, sheenDark, sheenLight, glint, rimLight, saturation, press, lens)`.

## 7. Risks

- **Clear drifts towards Glossy or Frosted.** Over busy content the floor thickens Clear. Gate 0 shows whether Clear is
  distinct enough. If not, cut it (D3 B).
- **"Very artificial" again** (owner, 10-26). Glossy's broader specular is the riskiest part. It stays a rim segment
  plus a sheen capped at 40 dp, and the owner judges it on renders before it ships.
- **The gloss is stripped in dark mode** (cause 2). Glossy needs the specular to survive the floor. It does, because the
  specular is never under words, but the gate renders must prove it.
- **Smoked in light mode** turns the light app's chrome dark, which can read as a mixed theme. That is D6.
- **Four lens passes plus a moving field** on mid-range phones. The cap, the budget and the fallbacks are in §5, and the
  phone numbers decide.
- **Dependency:** the `GlassLook` spec is still being written. If it lands with different names, the presets adapt; the
  picker does not care.
- **Module ownership:** `:ui:design` and `:ui:kit` glass files are being edited by the tint and gloss agent now. This
  work starts after that agent merges.

## 8. Owner decisions (A/B/C, with a recommendation)

Every decision has mockups, one screen per image.

| # | Question | A | B | C | Recommend |
|---|---|---|---|---|---|
| D1 | Picker layout | A row of large previews, swiped sideways (01) | **A 2×2 grid over one shared field** (02) | One big preview on top, a list of looks below (03) | **B:** all four looks side by side over the same moving colours, which is the point of the sheet |
| D2 | What moves behind the previews | Colour only (04) | **Colour plus drifting lines of text** (02) | Your own background (05) | **B:** smooth colour hides the difference between blur and clear; text lines make bending and blur visible. C is closest to real use, but the dark aurora hides the differences |
| D3 | Which looks | **Four: Clear, Glossy, Frosted, Smoked** | Three: Glossy, Frosted, Smoked | Two: Glossy and Frosted (today's switch, renamed) | **A**, unless gate 0 shows Clear too close to Glossy; then B |
| D4 | The default look | **Glossy** | Frosted | Clear | **A:** it answers "frosty, not glossy" |
| D5 | Where the picker opens | A "Glass look" row replacing Glass edges (06) | Tapping the chosen Glass card again (07) | **Both** | **C:** the row is the visible way; the card tap is a shortcut |
| D6 | Smoked in light mode | **Dark glass in both modes** (15) | Light mode becomes a grey tint, light ink kept | Hide Smoked in light mode | **A:** it reads as a deliberate look; judge it on image 15 |

Images 08–15 show each look on the chat screen over the painted aurora, dark and then light. They answer D3, D4 and D6.
