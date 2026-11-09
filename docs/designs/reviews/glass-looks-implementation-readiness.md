# Glass looks v1: implementation readiness review

Reviewer: Implementation Readiness goldfish (Tech Lead). Target: `docs/designs/glass-looks-2026-11-09.md`. Checked
against `FrostPolicy.kt`, `LiquidEdge.kt`, `Appearance.kt`, `engine/settings/.../Preferences.kt`, `PreferenceKeys.kt`,
`feature/settings` (`AppearancePage.kt`, `SettingsPresenter.kt`, `PreferencesChoices.kt`), `KitLiquidGlass.kt`,
`KitGlassPanes.kt`, `KitSurfaceGlass.kt`.
Verdict: not ready. Phase 1 cannot start without decisions on the base spec, the storage rule, and the recipe mapping.

## Findings, ranked

### 1. Blocker. The `GlassLook` type does not exist and is the thing everything else hangs on
Scope: "Depends on", section 6 "Interface to freeze", phase 1.
`grep GlassLook` over `native/` finds no matches. The doc defers to a spec "still being written" by another agent and
proposes a 17-field constructor "as a proposal". An implementer cannot write `GlassLooks.kt` against a type that may land
with different names. Also, the proposed fields (`tintDark`, `tintLight`, `tintTone`, `blurDp`, `paneBlurDp`...) mix per-role
and per-look data: today `LiquidEdge.recipe(role, dark)` returns a `LiquidRecipe` with eight fields, and the look table has
no `shadow` and a new `tintTone`, `glint`, `lens`. Decide and write: (a) which existing type is extended (`LiquidRecipe`?
`GlassSpec`? `GlassFinish`?), (b) the exact new fields and defaults, (c) the commit the base is. Block phase 1 on the other
agent's merge, naming the merge commit.

### 2. Blocker. "Each look scales today's per-role recipe" is undefined
Scope: section 2 intro, table rows Blur and Refraction.
Today the recipes are per role: Bar 7 dp blur / 34 lens; Composer 7 / 34; Sheet 6 (dark) 8 (light) / 44 with rim max 30;
Menu 4/6 / 36 rim 26; Pill 2 / 12 rim 12; Action 1 / 26 rim 40. The table gives one blur pair "floating / in-flow" and one
refraction ("40 dp in a 22 dp rim") per look. Questions an implementer must answer and the doc does not:
- What is "scale"? Multiplier on the role value, replacement, or min/max? If Glossy "4 dp floating" replaces the Bar's 7 dp it
  violates the doc's own rule "bars and the message box never under 5 dp". If it multiplies, what is the factor per look?
- Frosted blur 14 dp "floating": Pill/Action today are 2 and 1 dp small shapes; 14 dp on a 36 dp pill erases it. Per-role
  clamps are missing.
- Clear "2 dp applies to pills, previews and the +" yet Action today is 1 dp and Pill 2: Clear is identical to today for
  those; say so.
- Lens rim: the table gives a rim of 22 dp for all, but today's rimMaxDp is 22 default, 30 sheet, 26 menu, 12 pill, 40
  action. Do the looks override per-role rims?
Provide a table: look x role -> concrete LiquidRecipe values, or a deterministic formula, plus the clamps.

### 3. Blocker. Storage and migration are specified in one sentence and conflict with how preferences are written
Scope: section 3 Storage.
Facts from code: `oc.glassEdges` is written as null (key removed) when the value equals default `Liquid`
(`Preferences.kt:141`), read through `wireOf<GlassEdges>(...) ?: Liquid` (`:218`), listed in two key sets
(`PreferenceKeys.kt:52,65`), "device-wide, never swept". The change flows through three enums
(`GlassEdges` design, `KitEdges`, `StoredEdges`) mapped by `name` in `PreferencesChoices.kt` lines 88, 142, 174, plus
`PersonalChange.Edges`, `SettingsPresenter.onGlassEdges`, `glassEdgesHold`, `FakeSettingsPorts`, `AppearancePage`, and
string resources `appearanceGlassEdges*`. The doc must say:
- Is `oc.glassLook` default-omitted? If yes (as the codebase does), a user who had `oc.glassEdges=frosted` and picks Glossy
  loses it: key removed, fallback reads `glassEdges=frosted` -> Frosted again. If no, `oc.glassLook=glossy` must be stored
  explicitly. Pick one and test frosted -> glossy -> restart.
- When is `oc.glassEdges` deleted? "It reads `oc.glassEdges`" never says when it stops. Define a one-time migration (read,
  write new key, remove old), where it runs (the preferences load in `engine/settings`), and what a second launch sees.
- Is `GlassEdges` removed or kept? `FrostPolicy.edgesHeldBy(c, strength, edges)` signature changes; `Appearance.glassEdges`
  field; all test call sites listed in `LiquidEdgeTest`, `GlassPanesTest`, `KitLiquidGlassTest` (about 25 call sites) need
  updating. Provide the new `FrostPolicy` signature: `edgesHeldBy(c, strength, look)`? and the new `clearPanes`, because
  `clearPanes` today uses `edges != Frosted`; "keeps clear panes for every look" changes that rule and the doc says Frosted
  loses the old thick panes (behaviour change for Frosted users; list it as a migration note; AGENTS.md requires them).
- Is the key device-wide or per profile? State it (today's: device-wide, never swept).

### 4. Major. The Frosted look must be defined: is it "today's Frosted" or "a new milk"?
Scope: section 3 bullets, section 2 table.
Section 3 says "Frosted is today's Frosted choice in its new form"; the later bullet says it is "blur only, no lens", panes
draw "its own milk" (not the old thick panes). The table says Frosted tint 0.30/0.48, blur 14/16, saturation 1.5 and "needs
Android 13: no (blur only)". Today's Frosted edge = `EdgeHold.Frosted`, which draws the FrostSpec (strength.sigmaDp,
saturation, tint) path and the Flutter-faithful thick in-flow panes. Which code path draws the new Frosted - the frost path
or the liquid-glass path with `lens=false`? Different files (`KitFrost`-style vs `KitLiquidGlass`), different
holds. Decide, because "Platform" hold, "Frosted" hold and Saver/Contrast/Motion all return the same held face ("Holds win:
Saver ... keep today's held faces in any look"), so the Frosted look is subtly the same as a hold, yet Settings must still say
why. Also the existing user-strength setting (Off/Weak/Balanced/Strong) interplay with each look's blur: does Strong multiply
a look's blur? Unstated.

### 5. Major. Contrast floor changes are required but described as "does not change the floor maths"
Scope: "Depends on"; section 2 Readability; section 6 phase 1.
The doc requires: measuring each look's real spots under the real blur (the glass-v3 "next step", not built), a per-look
`BLURRED_INK` (currently one constant `.20f`, calibrated at 7 dp), a per-look press/saturation applied to the spot colours,
Smoked's dark ink flip for in-flow panes, and Glossy's stronger sheen in `GlassFinish.sheenAt`. These are changes to the
floor. Give: the function signatures (`LiveSpots.of(look)`, `AuroraImage.clearSpotsOf(look)`), where the look's saturation
and press are applied, and the exact pass criteria (4.5 + .04 for text, 3:1 icons, as now) per look. Also state expected
runtime: 4 looks x every pack x 2 modes x 2 spot sets x ~10 pane kinds is about 10x glass-v3's 992 panes; say whether it
stays in the fast lane.

### 6. Major. Picker kit parts are named but not specified
Scope: section 4, section 6 phase 2.
For `KitGlassLookPreview` and `KitLookField` the implementer needs: signatures, parameters (look, source layer,
selected, onClick, semantics), how a preview obtains "a given source" (the glass pipeline today takes the recorded page via
`KitLiquidSource`; how a different look's recipe is applied to a pane without changing global `Appearance`? Today
`FrostPolicy.liquidEdges(conditions, appearance.frost, appearance.glassEdges)` reads `LocalAppearance`. Four previews need
four different looks at once, so a per-pane look override (a `CompositionLocalProvider` or a parameter on the liquid pane)
must be designed; it touches `KitLiquidGlass.kt` (675 lines, single owner). Also `KitLiquidBudget` is a "picker-local"
budget - how does it relate to `LiquidEdge.grant/BUDGET`, which is window-level and the existing API? Name the API. The
kit ratchet (G16) and the "kit only" rule also need the sheet content to be composed of kit parts; list them.

### 7. Major. Performance acceptance is not executable as written
Scope: section 5 items 7, 8.
- "JankStats": the repo has `FieldFrameStats.kt` in `android/platform` and gfxinfo usage in QA; no JankStats dependency found
  in gradle or toml. Say which measurement is used and where the harness lives; JankStats requires a dependency (a pubspec/
  native change; per AGENTS.md rules, release-relevant).
- "p95 frame time within 1 ms of the conversations list in Glass over Aurora drift": the baseline is itself new, not
  recorded on the phone. Define the baseline capture (device, build, 10 s, same steps).
- "Zero frames while the field is resting": the sheet's field clock "rests after 30 s without a touch" and "pauses when the
  sheet is dragged below half" - specify the drag-detection hook in `KitSheet` (it exposes what?), and the lifecycle API.
- "Re-records at most 30 times a second" - state the mechanism (frame clock throttle in `LiveFrameClock`? does it have a
  rate parameter?). If it lacks one, that is a change to `LiveFrameClock` and its test.
- Mid-range phone: name the device (or class) beyond the owner's.

### 8. Major. The tap flow and tween need exact behaviour
Scope: section 4 "On tap".
- "The sheet's own face and the page behind it move to the new look over 200 ms": interpolating tint, gloss and lens between
  two recipes means interpolating `LiquidRecipe` fields and the lens's presence (Frosted has none; going lens -> no lens is
  not a continuous parameter, a shader on/off). Define the interpolated set (tint, rim alpha, sheen, specular) and what
  happens to blur/lens (cross-fade? snap? hold until end?). Also the thickened floor result is recomputed per look and per
  backdrop; interpolate after thickening.
- "`oc.glassLook` is written at once (the existing Settings action path)" - the existing path `write(PersonalChange.Edges...)`
  with `SettingsNote.AppearanceNotSaved` on failure; if the write fails, does the sheet revert? Debounce for rapid taps?
- Held cases: when Saver/reduced motion/high contrast hold, which previews does the picker show - all four held (identical)
  or the looks as they would be? The note line covers three of the cases; Saver, high contrast and frost Off are missing,
  and "Frost Off" hides everything.

### 9. Minor. Words and localisation are incomplete
Scope: section 4 Words.
The note-line strings, the four names and their one-liners (8 strings plus the note variants, the card second line
"Glass · Glossy"), the row label, the radio semantics, the removed `appearanceGlassEdges*` strings (5+) and the Arabic
translations. Provide the keys list. Check for the existing `docs/localization-todo.md`-style tracker for native resources.

### 10. Minor. Tests list is partial
Scope: section 2 Tests, section 6 phases.
Add: storage migration test (absent, liquid, frosted, unknown, round trip with default-omit), presenter test for
`onGlassLook` and holds, kit test for per-pane look override, budget with four previews plus sheet face, picker semantics
(radio group, selected announce), RTL mirror golden, 1.3x text scale golden, plus the pixel-equal test for other styles (named).
State the number of goldens (4 looks x 2 modes x screens) and where they live, since the kit golden suite cost is a known
problem.

### 11. Minor. Ownership and sequencing
Scope: section 7 last bullets, section 6 phase 1 "Owner: whoever owns `:ui:design`".
Single-owner units touched: `KitLiquidGlass.kt`, `KitSurfaceGlass.kt`, `FrostPolicy.kt`, `LiquidEdge.kt`, `Preferences.kt`
(`engine/settings`), `SettingsPresenter.kt`, `AppearancePage.kt`. "Whoever owns" is not an owner. Name one agent per unit and
the merge order after the tint and gloss agent lands.

## Ready already
- The set of look parameters and the idea of per-look presets over one table is clear.
- Hold behaviour (Saver, Motion, Contrast, Style, Off) is already coded and the doc keeps it.
- The picker's UI contents (title, panes, selected ring, note line, accessibility) are specific enough to build once the
  kit part signatures exist.
