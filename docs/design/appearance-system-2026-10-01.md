# Appearance system (proposal, 2026-10-01)

Owner request: "I want appearances to be customizable, so we can create a big variety and let users choose."
Trigger: a user misses the 1.0.44 "blue conical border", the composer activity ring (`6c788eff`: a `SweepGradient` primary→tertiary around the whole box while a turn runs), removed in `5bc1b864` and replaced by the living edge.

Status: design only, no product code. Samples (rendered from the real screens under existing theme packs, plus prototype painters for the effects that do not exist yet) are in the session scratchpad: `appearance/presets-sheet.png`, `appearance/composer-effects-sheet.png`, `appearance/effect-*-dark.gif`. The presets sheet only recolours (packs, light/dark); shape, density, type and surface style cannot be sampled until the kit reads the new axes, so those rows of the preset table are specification, not evidence.

## 1. Rules this must keep

- Kit only (AGENTS.md, kit-v2 §9): every visual behaviour is a kit part option or token with a default. Screens never add colour, shape or motion code. Variety comes from values, not from per-screen one-offs.
- Effects: no sharp lines, hues and fades, never fast (a hard speed cap), all motion honours Motion (Full/Calm/Off) and the system's remove animations, WCAG AA text contrast in every combination, light and dark.
- Cheap on mid and low phones: no per-frame blur stacks; ring and edge effects are one shader paint over the composer's own rectangle.

## 2. Axes

| # | Axis | Values | Default (Graphite) | Read by |
|---|---|---|---|---|
| 1 | Colour pack | the 31 existing `ThemePackId` values (Graphite, Catppuccin, ..., Material You) | opencode | `ThemeRoles` via `AppTheme.fromPalette` (unchanged) |
| 2 | Accent | pack's own, or one of 10 curated hues; derived with `ThemeRoles.withAccent` so contrast is re-guarded | pack's own | `KitTokens.accent`, send button, focus, edge |
| 3 | Accent pair | solo, or primary→tertiary (pack's tertiary or a curated partner) | pack's | activity effects, aurora, KitStatusMark working |
| 4 | Surface style | glass, flat (tonal, no blur), solid (opaque, hairline) | glass | `KitGlass`, `KitSurface`, `KitPanel`, sheets |
| 5 | Corner shape | sharp (6 dp scale), standard (today), soft (+6 dp) | standard | every `*Radius` token, one multiplier plus a floor |
| 6 | Density | compact, comfortable (today), roomy | comfortable | `rowHeight*`, `space1..6`, `gutter`, `sectionGap` (never below the 48 dp touch target) |
| 7 | Type family | Geist (today), Geist with Mono headings ("Terminal"), System | Geist | `KitText` roles |
| 8 | Message style | wide (today: prompt bubble, reply full width), bubbles (both sides) | wide | `KitMessage`, `KitTurn` |
| 9 | Composer activity | living edge (today), ring sweep, aurora edge, breathing glow, off | living edge | `KitComposer` edge layer |
| 10 | Background | flat, subtle gradient (ground to a 4% accent tint, static) | flat | `KitScreen` backdrop only |

Not axes: Motion (stays its own Full/Calm/Off control, applies on top of everything), language, light/dark/system, glass on/off (becomes axis 4's value; the old Effects toggle migrates onto it).

Guard rails inside an axis: Contrast preset locks axes 4 (solid), 9 (off or breathing) and 10 (flat). A combination that fails the contrast floor is not blocked; the resolver nudges the offending role (existing `_guardAccent` behaviour) and the Customize row says "Adjusted for readability".

## 3. Activity effects (axis 9)

All draw on the composer's rounded outline from one `KitEdgeEffect` painter, behind the glass rim, decorative (excluded from semantics; the phase caption stays the live region).

| Effect | What it is | Cost |
|---|---|---|
| Living edge (current) | status bends into the top edge with a pace-driven glow (`kit_composer.dart` `_LivingEdge`, speed cap `edgeLightMaxLapsPerSecond` = 1 lap/6 s) | unchanged |
| Ring sweep (softened 1.0.44) | one `SweepGradient` stroke, 2.2 dp, tails fade to alpha 0 at both ends (no seam), primary→tertiary, 1 lap/10 s thinking, never faster than 1 lap/6 s | one gradient stroke |
| Aurora edge | slow three-hue sweep (primary, tertiary, secondary) as a thin edge plus a soft inner wash, full lap in 14 s | one shader, two draws |
| Breathing glow | still hue, edge alpha and inner falloff rise and fall on `KitMotion.breath` (4 s) | one shader, no travel |
| Off | the plain rim, caption only | none |

Prototype note: the sampled painters use stacked strokes for the falloff. Production replaces that with one fragment shader (`shaders/kit_edge.frag`, a signed-distance falloff of the rounded rectangle plus the sweep hue, same pattern as `kit_glass.frag`): one pass over the composer's rectangle, no blur, no loop, uniforms only (time, hues, speed phase). Under Skia without the shader loaded it falls back to the single gradient stroke of "ring sweep".

Rules, enforced by one constant set in `KitMotion` (new, next to the edge-light ones): `effectMaxLapsPerSecond = 1/6` for every travelling effect, `effectSpeedEase = 800 ms`, `effectHueFade = 450 ms`, peak alpha 0.75 on the stroke and 0.12 on the wash, minimum alpha at the tails 0. Calm: a still glow at half alpha. Off and reduced motion: no effect (rim only). A pause when the app is backgrounded or the route is hidden (`TickerMode`), as today.

Contrast: the effect never sits under text (it is on the outline, inside the glass the text is unaffected), but the stroke must reach 3:1 against the ground at its peak for the status to be visible without colour (the caption carries the meaning regardless). The resolver clamps stroke luminance to that floor.

## 4. Presets

A preset is a named bundle of the ten axes plus a default Motion level hint (never forced). Choosing one sets the axes; changing any axis afterwards shows the preset as "Custom (based on X)".

| Preset | Pack | Surface | Corners | Density | Type | Messages | Activity | Background | Character |
|---|---|---|---|---|---|---|---|---|---|
| Graphite (default, = today) | opencode | glass | standard | comfortable | Geist | wide | living edge | flat | neutral graphite, green accent |
| Classic (about 1.0.44) | forest ground, mint accent, blue partner | glass | standard | comfortable | Geist | wide | ring sweep | flat | the look people miss, with the ring softened |
| Paper | paper | flat | sharp | comfortable | Geist | wide | breathing glow | flat | warm, quiet, light-first |
| Terminal | gruvbox | flat | sharp | compact | Mono headings | wide | off | flat | dense, no decoration |
| Aurora | tokyoNight | glass | soft | comfortable | Geist | bubbles | aurora edge | subtle gradient | the showy one |
| Rose | rosePine | glass | soft | roomy | Geist | bubbles | breathing glow | subtle gradient | gentle, rounded |
| Nord Calm | nord | flat | standard | comfortable | Geist | wide | living edge | flat | cool and calm; suggests Motion Calm |
| High Contrast | highContrast | solid | standard | roomy | System | wide | off | flat | AAA text, hairline borders, no blur |

"Material You" stays a pack choice under Customize, not a preset (its hues are the phone's).

## 5. Storage

Per-device, in the existing display preferences next to `oc.themePack`, `oc.appearance`, `oc.effectsMotion`. Not per profile (no deletion sweep entry needed; document in `profileScopedPreferenceKeys` comments that these are intentionally device-wide). Keys, one value each, stored by enum name so new values can be added anywhere (unknown reads as the default):

`oc.appearancePreset` (preset id or `custom`), `oc.appearanceAccent` (hex or empty), `oc.appearanceAccentPair`, `oc.appearanceSurface`, `oc.appearanceCorners`, `oc.appearanceDensity`, `oc.appearanceType`, `oc.appearanceMessages`, `oc.appearanceActivity`, `oc.appearanceBackground`. `oc.themePack` stays the colour pack key.

Rule: the axis keys are the truth; `oc.appearancePreset` only says which preset to show as selected and is cleared to `custom` by any axis edit. Writes go through `_saveDisplayPreference`, so a failed write throws the same plain-words error as today.

Migration (no stored-format break): absent axis keys read as the Graphite values, so an existing install is unchanged. `oc.themePack` keeps its meaning. Glass: `KitEffects.glass` is today a fixed true; the surface axis becomes its stored form (glass maps to true, flat and solid to false), and the old effects keys are read once by the same fold as the existing Calm migration. A user on a non-Graphite pack sees "Custom (based on Graphite)" with their pack. Downgrade is safe (older builds ignore the new keys).

## 6. Architecture

One immutable `KitAppearance` (new, `lib/ui/kit/kit_appearance.dart`, state enums in `lib/state/appearance.dart` like `effects.dart`) holds the ten axis values. It is provided above the navigator by `KitAppearanceScope`, beside `KitEffectsScope`, and resolved once per change:

```
KitAppearance + ThemePack + Brightness
   ├─ ThemeRoles   (pack colours, accent override, contrast guard)   -> AppTheme.fromRoles
   ├─ KitTokens    (radii x corner multiplier, spacing x density, surfaces) -> ThemeExtension
   ├─ KitMotion    (activity effect id, caps; reads Motion + reduced)
   └─ KitType      (family per role)
```

- `AppTheme.fromPalette` gains an optional `KitAppearance`; `KitTokens.fromRoles(roles, text, appearance)` multiplies existing literals through two scalar tokens (`cornerScale`, `densityScale`) instead of adding fields to every part. Defaults reproduce today's numbers exactly, so existing goldens must not change under Graphite (that is the migration test).
- Parts keep reading `KitTokens.of(context)`. New reads only: `KitGlass` and `KitSurface` read `appearance.surface` (glass, flat: tonal fill with a hairline, solid: opaque); `KitMessage` and `KitTurn` read `messages`; `KitComposer` reads `activity` and delegates to `KitEdgeEffect` (kit part, one file, one shader); `KitScreen` reads `background`; `KitText` reads `type`.
- The system always wins, as today: remove animations or high contrast forces effect off and solid surfaces regardless of the choice, and the Appearance page says so.
- Screens do not change at all. Anything a screen would colour or shape differently is a missing token.

## 7. Settings › Appearance UX

1. Light or dark (unchanged).
2. Presets: a horizontally scrolling gallery of eight cards, each a live miniature (the real `KitScene` for a small chat with composer, rendered under that preset; the active effect plays its three-second loop only on the focused card and only under Motion Full). Tap applies at once, with an Undo snackbar (`KitUndo`).
3. Customize (a `KitExpandRow`, closed by default): one `KitChoiceRow`/`KitSegmented` per axis, each with a one-line description and a live preview strip above the rows that updates as choices change. "Adjusted for readability" notes appear inline.
4. Motion (unchanged), Language (unchanged).
5. Reset: "Back to Graphite" row, confirmed by `KitConfirmSheet` (neutral), names what it resets (appearance only, not language or motion).

All new copy in `app_en.arb` and `app_ar.arb`; Arabic must be checked in RTL (the effect starts at the top centre and runs in the reading direction).

## 8. Performance and accessibility rules

- One `RepaintBoundary` around the edge effect, own ticker, stops off screen and in the background. Effect shader reads uniforms only. Budget: no frame over 32 ms caused by the effect; `gfxinfo` comparison (effect on vs off, janky frames at most 1 point worse) before it ships, the same gate as `docs/qa/liquid-glass-2026-09-25/README.md`.
- Preset previews in the gallery are static except the focused one, and are not built while the gallery is off screen.
- Changing an axis rebuilds the theme once; no animation of the theme itself (an instant swap, with `KitMotion.quick` cross-fade only under Full).
- Contrast: every pack × light/dark × surface style is checked in a unit test with the existing contrast helper (text on ground, on surface1/2, accent on ground, onAccent on accent) at 4.5:1 for text, 3:1 for the edge stroke and large controls. Flat and solid are the same colours without translucency, so they cannot be worse than glass. Corner and density axes must keep 48 dp targets.
- Effects honour Motion: Full (all), Calm (still glow), Off (rim only); the system setting overrides.
- No colour-only meaning: the phase caption and stop button carry the running state in every effect.

## 9. Tests and goldens

- `test/appearance_resolver_test.dart`: each axis changes only its tokens; Graphite defaults equal today's `KitTokens` field by field; unknown stored values read as defaults; any axis edit sets preset to `custom`.
- `test/appearance_contrast_test.dart`: pack × brightness × surface; accent override guard.
- `test/appearance_storage_test.dart`: keys, migration from `oc.themePack` and the old effects keys, reset.
- Kit goldens: `KitComposer` running under each effect × light/dark at frames t = 0 and 0.5 (Calm: still frame; Off: rim). Motion honoured (pump with `disableAnimations`).
- Preset goldens at 412×915, light and dark, 8 presets: chat with running turn, Work list, Settings › Appearance, a sheet (confirm), and Add-server form. 8 × 5 × 2 = 80 images; the existing 412×915 harness (`captureApp`, `workController`, `mountSettingsScene`) gains an `appearance:` parameter instead of the `Theme(...)` wrapper used for these samples. Graphite goldens must be byte-identical to today's.
- Ratchet: `test/kit_ratchet_test.dart` stays green (no new literals outside the kit); add a test that nothing outside `lib/ui/kit/` imports `kit_appearance.dart` except the theme and the Appearance screen.
- Gallery: `KitEdgeEffect` and the preset card get kit galleries at the §8.4 sizes.

## 10. Slice plan for parallel agents

Freeze first (30 minutes, coordinator): enum names and values in `lib/state/appearance.dart`, the `KitAppearance` field list, key names, `KitMotion` effect constants.

| Slice | Owner scope (write set) | Depends on | Acceptance | Focused checks |
|---|---|---|---|---|
| A. State and storage | `lib/state/appearance.dart` (new), `lib/state/profiles.dart` (keys, getters, setters, migration), `ConnectionController` listenable wiring in `lib/state/connection.dart` | frozen enums | defaults equal today; migration test | `appearance_storage_test` |
| B. Resolver and tokens | `lib/ui/kit/kit_appearance.dart` (new), `lib/ui/kit/kit_tokens.dart`, `lib/ui/app_theme.dart`, `lib/ui/theme_roles.dart` (accent guard only), `lib/main.dart` (scope; single owner) | A | Graphite goldens unchanged; contrast test | `appearance_resolver_test`, `appearance_contrast_test`, existing theme and token tests |
| C. Surfaces, messages, background | `lib/ui/kit/kit_surface.dart`, `kit_panel.dart`, `glass/kit_glass.dart`, `chat/kit_message.dart`, `chat/kit_turn.dart`, `kit_screen.dart` | B | flat/solid/glass per surface; bubbles; goldens per surface | kit galleries |
| D. Edge effects | `lib/ui/kit/chat/kit_composer.dart` (single owner, with the chat library), new `lib/ui/kit/kit_edge_effect.dart`, `shaders/kit_edge.frag`, `lib/ui/kit/kit_motion.dart` constants | B | four effects, caps, Calm and Off; `gfxinfo` evidence in `docs/qa/appearance-effects-<date>/README.md` | composer tests, `motion_states_test`, device run |
| E. Type and density | `lib/ui/kit/kit_text.dart`, `kit_row.dart`, `kit_row_parts.dart`, `kit_layout.dart` | B | density never below 48 dp; mono headings | row and text galleries |
| F. Appearance screen | `lib/ui/screens/settings/personal_settings_screens.dart`, `lib/l10n/*` (single owner), new kit parts `KitPresetCard`, `KitAppearancePreview` | A, B | gallery, Customize, reset, Undo, RTL | settings goldens |
| G. Preset goldens and docs | `test/goldens/appearance_*`, `docs/qa/appearance-<date>/README.md` | C, D, E, F | 80 images reviewed | the golden file |

C, D, E can run in parallel after B; F can start on mock data after A. `kit_composer.dart` and `chat_screen.dart` stay single-owner. Serialise machine-heavy checks through `machine_lock.sh`. Suggested order for value: D with Classic first (answers the user who misses the ring), then A, B, F (presets), then C, E.

## 11. Non-goals

User-defined colour pickers (curated accents only), per-screen themes, animated themes, downloadable theme files, per-profile appearance, a new font bundle (System and the existing Geist faces only).

## 12. Open decisions for the owner

1. Scope of the first release: (a) Classic ring only, as a single "Composer activity" choice (about 1 slice, fastest answer to the complaint); (b) full presets plus Customize (all slices); (c) presets first with only axes 1, 4, 9 customizable, rest later. Recommended: (c), shipping the ring in it.
2. Default for existing users: (a) everyone stays on Graphite with the living edge (recommended; ring is opt-in); (b) re-offer Classic once through a one-time Undo-able prompt.
3. Message style and type family: (a) include "bubbles" and "mono headings" now (they touch the transcript, the most-reviewed surface); (b) defer them until the colour, surface, corner and activity axes have shipped (recommended).
