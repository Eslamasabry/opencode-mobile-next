# Native UI rebuild: the golden standard (2026-11-05)

Binding manual for every agent that ports the OpenCode Mobile UI from Flutter to native Kotlin Multiplatform and
Compose. Owner, 2026-11-05: "Why can't we rebuild what we had already?" and "the same designs, the same backgrounds.
The user should be able to select his material." This document turns that into rules a builder can follow without
judgement calls.

Words: **must** and **never** are rules. Each rule has a **Check**: the command or file that proves it. A rule without
a passing check is not met. There is no "should" in this document.

Companion files, all in `docs/design/native-ui-rebuild/`:

| File | What it is |
|---|---|
| `batches.md` | Every order, its parts, engine, dependencies and size. Read it to see where your order sits. |
| `orders/<NN>-<name>.md` | Your work order. The header lines (`ORDER`, `ENGINE`, `DEPENDS`, `WRITES`, `GATE`, `TIMEOUT_MIN`) are machine-read. |
| `orders/<NN>.gate.tsv` | Your gate manifest: parts, capacity classes and every golden the gate compares. Generated; never edit. |
| `orders/INDEX.tsv` | All orders in one table (order, engine, depends, writes, gate, timeout). |
| `tracker.json` | Every part, school and screen with its id, order, Flutter refs, golden count and native file, for the visual tracker. |
| `waivers/<NN>.tsv` | Your waivers (§9.5), if any. You create it. |
| `evidence/<NN>.md` | Your gate summary, waivers and folded gaps. You create it. |

---

## 1. Scope and non-goals

**In scope.** An exact rebuild, in native Compose, of:

1. the Flutter design kit `lib/ui/kit/**` (278 files) and its tokens, at FLUTTER_REF;
2. every style school the user can pick: **Solid, Glass, Soft, Brutal, Paper, Signal** (§5);
3. the live backgrounds: the 20 looks of 2.1.1 plus the Glass-era painted aurora (§5.5);
4. every Flutter screen mapped to its native home (orders 40-53), in journey order.

**Non-goals.** You must never:

- improve, modernise or "Material 3-ify" anything. The owner rejected that pass ("ugly still"). Improvements come later,
  one at a time, with owner approval. The proposals in `docs/design/native-looks-2026-11-04/direction.md` §5-§7 (new
  tokens, 72 dp rows, M3 springs, 28 dp sheets, concepts A/B) are **superseded** by this document;
- port Crystal (retired 2026-10-26, not requested), Clay (dropped 2026-10-21), or AI Team and FactsBook screens
  (deferred, ADR D6; tracked as `screen-H-deferred` and `screen-N-deferred`);
- change behaviour, data flow, ViewModels, engines or navigation. This is a look rebuild. A screen order that needs data
  the screen state lacks stops (§13).

**Check:** the gate compares every result to Flutter's own goldens (§9). Anything "better" than Flutter fails it.

---

## 2. Sources of truth

### 2.1 Hierarchy

When sources disagree, the higher one wins, always:

1. **Flutter code** at FLUTTER_REF (Glass: GLASS_REF). The Dart file is the spec: every constant, branch, colour role,
   padding and animation.
2. **Flutter goldens** at the same ref (`test/goldens/kit/*.png`, `test/goldens/**`, `test/revamp/goldens/*.png`). They
   show what the code draws. When code and golden seem to disagree, you misread the code: read it again.
3. **`docs/ux-system/kit-v2.md`** and the kit API pages `docs/ux-system/kit-api/*.md` at FLUTTER_REF: rules, caps and
   intent. The design-schools docs `docs/design/design-schools/{contract,schools/*}.md` for the schools (their §0 "as
   built" wins over the rest of the contract).
4. Anything else: this standard, the port plan, the kit map, native KDoc, your own memory of Material. Lowest.

Native code is never a source of truth for a look. The looks-pass changes listed in
`docs/design/native-looks-2026-11-04/port-plan.md` ("Native looks-pass changes to check against the goldens") stay only
if the goldens agree; otherwise you put Flutter's look back.

**Check:** every number in your diff cites a Flutter line (§9.2 rule L1-L3).

### 2.2 References and where they live (VPS `eslam@169.58.71.211`)

| Name | Value | Where |
|---|---|---|
| FLUTTER_REF | `8302de53773adef45eaaad016df2bd3cd51d1ebe` (`mobile-next/master`, product 2.1.1) | checkout `~/wt/flutter-ref` (the lead provides it). Read a file: `cat ~/wt/flutter-ref/<path>` or `git -C ~/wt/flutter-ref show 8302de537:<path>` |
| GLASS_REF | `1bbdc3f9c7779148a689186e6d5ca3cd59062ab5` (the last commit with Glass, parent of `ecf6069b3` "retire Glass and Crystal") | `~/Storage/Code/oc_app` on the VPS: `git -C ~/Storage/Code/oc_app show 1bbdc3f9c:<path>`. It is not in `mobile-next` history, so never look for it in `~/wt/flutter-ref`. |
| Native base | branch `native/looks-pass-2026-11-04` | the lead merges every order into it |
| Your worktree | `~/wt/rb-<NN>`, branch `native/rb-<NN>-<name>` | created by the lead from the native base after your dependencies merged |
| Flutter goldens for the gate | `looks-gate/flutter/8302de537/` and `looks-gate/flutter/1bbdc3f9c/` in your worktree | filled by `gate_order.sh` (rb_gate.py fetch); never commit them |
| Gate output | `looks-out/gate/` in your worktree | written by the gate; never commit it |

Never use a branch name (`mobile-next/master`, `master`) to read Flutter: always the SHA above. Local `master` on the PC
is three weeks stale and has the old type scale.

---

## 3. Environment and machine rules

1. All work happens in your worktree on the VPS. You never create, move or delete a worktree. **Check:** `git rev-parse --show-toplevel` prints `/home/eslam/wt/rb-<NN>`.
2. Gradle runs on the VPS only, and only through `native/tool/design/gate_order.sh <NN>` or the exact task
   `cd native && ./gradlew <module>:desktopTest --tests '*.rb.o<NN>.*'` taken under the same lock (gate_order.sh holds one
   of two slots, `~/.oc-rb-gradle.{1,2}.lock`). Never `./gradlew build`, never `assemble*`, never `--no-daemon` loops, never
   two Gradle runs at once from your worktree. **Check:** `ps -u eslam -o args | grep -c "[g]radlew.*rb-<NN>"` is at most 1.
3. The VPS disk is shared and nearly full (about 28 GB free). Never write outside your worktree except the lock files.
   Never copy the Flutter repo. **Check:** `du -sh ~/wt/rb-<NN>` stays under 6 GB.
4. Never kill a process you did not start, and never by pattern (`pkill -f` matches other agents' servers). Kill by PID.
5. Never push, never open a PR, never tag, never sign, never touch release scripts. The lead merges.

---

## 4. The token contract

Tokens are generated, never typed.

| Token family | Flutter source (FLUTTER_REF) | Native, generated | Native readers |
|---|---|---|---|
| Sizes, radii, spacing, static constants | `lib/ui/kit/kit_tokens.dart` (`KitTokens.fallback`) | `FlutterKitTokens` | `design/tokens/Tokens.kt`, `Space.kt`, `Shape.kt` |
| Type roles | `lib/ui/kit/kit_text.dart` (`KitText.styleFor`) | `FlutterKitType` | `design/type/TypeScale.kt`, `TypeRole.kt` |
| Motion | `lib/ui/kit/kit_motion.dart` | `FlutterKitMotion` | `design/motion/MotionTokens.kt` |
| Colour roles, every pack | `lib/ui/theme_roles.dart` | `FlutterKitRoles` | `design/color/*` |
| School constants, sharp radii | `lib/ui/kit/style/kit_style_*.dart` | `FlutterKitStyles` | `design/style/*Spec.kt` |

The generated file is `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/generated/FlutterKit.kt`,
written by `native/tool/design/gen_tokens.py --repo ~/wt/flutter-ref --ref 8302de537 --out <that file>` (commit 15e525a19).

Rules:

- **T1.** Never edit `generated/FlutterKit.kt`, nor the lead-only token files `design/tokens/*`, `design/type/*`,
  `design/color/*`, `design/motion/MotionTokens.kt`. They are in no order's write set. **Check:** gate_order.sh rule W1.
- **T2.** A part reads a size, radius, gap, duration, weight or colour from the tokens (`Tokens`, `Space`, `Shapes`,
  `TypeRole`, `MotionTokens`, `LocalKitColors`/`Roles`) whenever Flutter reads it from `KitTokens`, `KitText`,
  `KitMotion` or `ThemeRoles`. **Check:** code review against the Dart line; a literal in its place fails L1-L3 because
  the Dart line has no number.
- **T3.** A number Flutter writes inline in the part (for example `static const glyphSize = 26.0;` in `kit_row.dart:332`)
  is copied inline, with its citation (§9.2). **Check:** rules L1-L3.
- **T4.** A token Flutter has and the generated file lacks is a blocker: mark the part blocked with
  `needs token: <Dart file>:<line> <name>` (§13). Never add it by hand.
- **T5.** Colours are roles. Never write `Color(0x…)`, `Color(r, g, b)` or an alpha you made up. An alpha Flutter writes
  inline (`accent.withValues(alpha: .18)`, `kit_row.dart:1008`) is copied with its citation.

---

## 5. The style-school contract

### 5.1 The schools

The person picks one Style in **Settings › Appearance › Style**. Every kit surface follows it; screens never know which
one is on (kit-only rule, §7). The native model is `design/style/Style.kt` (`enum class Style(storageName)`), resolved by
`StyleResolver` into a `StyleSpec` per school, which turns a `SurfaceRequest` into a `SurfaceDecoration` that
`KitSurface` paints.

| Style | Storage name | Flutter spec | Ref | One line (Flutter KDoc) |
|---|---|---|---|---|
| Solid (default; "flat") | `solid` | `lib/ui/kit/style/kit_style_solid.dart` | FLUTTER_REF | Matte panels with a hairline edge; lists flat on the page |
| Glass | `glass` | `lib/ui/kit/style/kit_style_glass.dart` | GLASS_REF | Frosted panes the live background shows through |
| Soft (neumorphism) | `soft` | `lib/ui/kit/style/kit_style_soft.dart` | FLUTTER_REF | The ground's own colour, shaped by paired soft shadows |
| Brutal | `brutal` | `lib/ui/kit/style/kit_style_brutal.dart` | FLUTTER_REF | Bold ink edges and hard offset shadows with no blur |
| Paper | `paper` | `lib/ui/kit/style/kit_style_paper.dart` | FLUTTER_REF | Flat print, Swiss style: ink rules on paper, square, no shadows |
| Signal | `signal` | `lib/ui/kit/style/kit_style_signal.dart`, `kit_signal.dart`, `kit_signal_slip.dart` | FLUTTER_REF | A railway signal box: cream paper and ink, one track, signal lights |

Picker order (Glass back in its GLASS_REF place, after Solid): **Solid, Glass, Soft, Brutal, Paper, Signal**.

### 5.2 What each school sets (its `KitStyleTokens`, verbatim from the Dart files)

Defaults are the Fine-tune knobs the school starts at (Corners · Depth · Density · Edges). A blank cell is the default
of `KitStyleTokens` (`kit_style.dart`).

| Token | Solid | Glass | Soft | Brutal | Paper | Signal |
|---|---|---|---|---|---|---|
| defaults | Round · Flat · Comfortable · Hairline | Round · Flat · Comfortable · Hairline | Round · **Raised** · Comfortable · **None** | **Soft** · **Raised** · Comfortable · Hairline | **Sharp** · Flat · Comfortable · Hairline | **Sharp** · Flat · Comfortable · Hairline |
| sharpRadius | 2 | 2 | 2 | 2 | **0** | **4** |
| keepsUnderHighContrast | | | | true | true | true |
| dependsOnDepth | | | true | | | |
| keepsNestedSurfaces | true | | | | | |
| flatLists | true | | | | | true |
| flattensNested | | | | true | | |
| track | | | | | | true |
| blursBehind (GLASS_REF hook) | | true | | | | |
| type | (lighter 2026-10-30 weights) | weightBoost 50 | firmWeights | displayWeight 800, tracking -.03, bodyWeight 500, monoLabels, firmWeights | displayWeight 750, tracking -.03, largeTitle 34, title 24, monoLabels, firmWeights | displayWeight 700, largeTitle 32 at 760, tracking -.02, title 22, engravedLabels, firmWeights |
| live | as chosen | fillsMiddle, lookWhenChosen `auroraImage` | maxStrength .7 | maxStrength .7 | offWhenChosen | offWhenChosen |
| glow | halo | halo | halo | no halo, line 2 | no halo, line 1 | halo |
| needsYou | caption, by halo | caption, by halo | no tint, no ring, start edge | pill, no tint/ring/edge | by halo | no tint, ring or edge |
| fieldHalo | | | true | | | |
| linkUnderline | | | | edge (2x ink) | | |
| adjustRoles (hook H6) | none | scrim = shade at .24 dark / .10 light | overrides (see Dart) | overrides (see Dart) | overrides (see Dart) | overrides (see Dart; cream paper and ink, night box in dark) |

The table is a reading aid. The Dart files are the spec (§2.1); order 03 ports them value for value and proves it with
`rb/o03/<School>SpecParityTest.kt` (every constant of the Dart file asserted). **Check:** gate 03.

### 5.3 Glass: what comes back from GLASS_REF

Glass returns as a full school, built from `1bbdc3f9c` in three orders:

| Piece | GLASS_REF source | Native | Order |
|---|---|---|---|
| The spec: opacity per role and brightness (content .42/.50, control .48/.56, track .38/.46, floating .50/.62, sheet .58/.72, field .5, toggle .28/.38), frost per role, sheen .08, paneFrost 20, floatFrost 26, inner glow, chosen ring 2, rims, plate shade, `onColor` text1, scrim | `lib/ui/kit/style/kit_style_glass.dart` | NEW `design/style/GlassSpec.kt` | 03 |
| The hooks: `blursBehind`, `KitStyleType.weightBoost`, `KitLivePolicy.fillsMiddle` and `lookWhenChosen`, `KitSurfaceDecoration.fillStart`, `frost`, `wordsPlate`, `innerGlow`; `Style.Glass("glass")` | `lib/ui/kit/style/kit_style.dart`, `lib/ui/kit/kit_surface_look.dart`, `lib/state/effects.dart` | `Style.kt`, `StyleSpec.kt`, `StyleResolver.kt`, `Surface.kt` | 03 |
| Painting: the blur behind each pane once per stack, the top-start sheen, the inner glow, the rims, the words plate | `kit_surface_look.dart`, `kit_surface_paint.dart`, `kit_words_plate.dart`, `kit_text_halo.dart`, `glass/kit_glass.dart` | NEW `KitSurfaceGlass.kt`, NEW `KitWordsPlate.kt`, NEW `KitGlass.kt` (+ `AndroidFrost.kt` blur from order 21) | 34 |
| The painted aurora look (`auroraImage`, two WebP images) and the vivid wash | `live_background/styles/kit_live_aurora_image.dart`, `kit_live_vivid.dart`, `assets/backgrounds/aurora-{dark,light}.webp` | NEW `kit/live/KitLiveAuroraImage.kt`, `KitLiveVivid.kt`, `composeResources/drawable/aurora_{dark,light}.webp` | 35 |

Not restored: Crystal, `kit_crystal_engine.dart`, `kit_gloss.dart`, the `liquid_glass_easy` lens. Glass goldens are
compared at GLASS_REF on tier T6 (§9.3) because the 2026-10-30 type scale changed after them; the spec values are
compared exactly by the parity test.

Blur on Android uses the platform blur already behind `AndroidFrost.kt` (RenderEffect, API 31+); below that, and on
desktop, the pane falls back to its tint at the opacity above with no blur, as `kit_glass.dart` falls back when the
engine cannot blur. **Check:** gate 34 renders on desktop (fallback) against the goldens; `rb/o34/GlassFallbackParityTest`.

### 5.4 Fine-tune, high contrast, Saver

Port `KitStyles` (`kit_style.dart`) and `KitEffects` (`lib/state/effects.dart`) exactly:

- Changing the Style resets Fine-tune (`withSurfaceStyle`). The UI shows only Corners and Spacing (owner 2026-10-22).
- Corners: Round keeps a radius, Soft takes about 60 %, Sharp takes the school's `sharpRadius`; stadiums and circles
  stay. Compact density: rows 48/54, button 48, section gap 16, label gap 6; touch targets never shrink.
- High contrast (platform): Solid unless the school `keepsUnderHighContrast`; Depth flat, Edges on.
- Saver (true black): Solid, motion off, live background off, celebrations off; the stored choices stay untouched.

**Check:** `rb/o03/StyleResolverParityTest` covers every school × brightness × the 24 knob combinations × 5 roles, as
`test/kit/kit_style_test.dart` does at FLUTTER_REF.

### 5.5 Backgrounds (the live background)

Settings › Appearance › Background: a switch (off by default: "the page its plain colour"), a look, and Intensity
(Subtle .7 · Normal 1.0 · Bold 1.6). Looks, in picker order (`kit_live_styles.dart`; GLASS_REF adds `auroraImage` second):

aurora (default id), auroraImage (Glass), mesh, dotRipple, topo, flowThreads, glowSpill, lightRays, constellation,
halftone, liquidBlobs, matrixRain, blueprint, circuit, oscilloscope, radar, honeycomb, brushedMetal, silk, caustics, bokeh.

The ids are the Dart `KitLiveStyle.id` strings; they are stored in `oc.effectsLiveStyle` already (`:engine:settings`
`Effects.liveStyle`), so they must match byte for byte. Each look is one file in `lib/ui/kit/live_background/styles/`,
drawn with `kit_live_toolkit.dart` and `kit_live_support.dart`, clocked by `kit_live_clock.dart`, framed by
`kit_live_background.dart`. A school's `live` policy caps or turns it off (§5.2). Reduced motion and motion Off show
the look's still frame; Saver hides it. Native package: `kit/live/` (orders 22, 24, 31-33, 35).

**Check:** gates 31-33 (goldens in `test/goldens/live_styles/` and the `kit_live_background_*` scenes) and a
`rb/o<NN>/<Look>ParityTest` per look for its constants.

### 5.6 Choosing a style, live, with a cross-fade

1. Settings › Appearance (order 44) is a rebuild of `lib/ui/screens/settings/appearance_one_page.dart` and
   `appearance_screen.dart`: four sections (Colours, Style, Background, Text & motion), each question with its control in
   place, two columns from the expanded width. The Style question shows one `KitOptionCard` per school with its own
   sample (`kit_option_card.dart`; the Glass card as at GLASS_REF).
2. A tap writes the style through the existing settings path and applies at once; there is no Apply button.
3. The switch animates per surface exactly as Flutter: every `KitSurface` animates its decoration over
   `KitSurfaceLook.styleFade` (200 ms, `kit_surface.dart:319`), state changes over 120 ms; with reduced motion or motion
   Off the change is instant (`Duration.zero`). The page does not reload, scroll position and focus stay.
4. Choosing Paper or Signal turns the live background off and remembers that the style did it (`liveHeldByStyle`);
   choosing another style turns it back on. Choosing Glass picks the `auroraImage` look (`lookWhenChosen`).

**Check:** order 44 gate (Appearance goldens `settings_*` and `kit_option_card_*`), plus `rb/o01/StyleFadeTest` (the 200 ms
lerp and the reduced-motion snap in `KitSurface`).

### 5.7 Storage

Keys and wire names stay Flutter's: `oc.effectsSurfaceStyle` (`solid|glass|soft|brutal|paper|signal`), `oc.effectsCorners`,
`oc.effectsDensity`, `oc.effectsLiveBackground`, `oc.effectsLiveStyle`, `oc.effectsLiveIntensity`. Lead prerequisite for
order 44: `glass` in `:engine:settings` `SurfaceStyle` (`engine/settings/.../prefs/PreferenceTypes.kt`). An unknown
stored name reads as Solid.

---

## 6. The per-part porting recipe

Do the parts of your order in the order the order lists them. For each part:

1. **Report start.** `oc-update 'rb-<NN>-<engine>' '<part id>: started' working <done>/<total>` (§11).
2. **Read the Dart file whole** at its ref (`cat ~/wt/flutter-ref/lib/ui/kit/<file>`). Then read every Dart file it
   imports from `lib/ui/kit/` that your order owns or folds. Write down (in your scratch notes, not the repo) every
   constant, every branch on state, style, size class, text scale, direction and motion.
3. **Read its golden test** (`test/goldens/kit/<part>_golden_test.dart`; gallery scenes: the test named in your order).
   Note each scene: the inputs (strings, icons, states), the frame (`kitGalleryPart` → `Frame.Part`, own backdrop →
   `Frame.Screen`), the sizes and modes it writes. The golden name grammar is
   `<shot>[_ar][_text2][_<W>x<H>]_<light|dark>`.
4. **Look at the goldens.** `rb_gate.py fetch` has put them in `looks-gate/flutter/8302de537/`; open each PNG.
5. **Map widget to Compose** with the table in §6.1. Port into the native files your order owns. Keep every existing
   public signature; add parameters only with defaults (§7 rule A3). Every number cites its Dart line (§9.2).
6. **Write the gate cases** in `native/<module>/src/desktopTest/kotlin/<package>/rb/o<NN>/<Part>GateTest.kt`, package
   `io.github.eslamasabry.oc.<module package>.rb.o<NN>` (for example `io.github.eslamasabry.oc.ui.kit.rb.o05`): one
   `FlutterGoldenGate.check("<golden stem without _light/_dark>") { … }` per golden in your manifest, with the Flutter
   scene's exact inputs, and one `FlutterGoldenGate.matrix("<part id>", caps = …) { … }` per part (§8.2). Strings are
   test inputs, so they are typed in the test exactly as the Dart test has them.
7. **Run** `RB_DEV=1 native/tool/design/gate_order.sh <NN>` and open `looks-out/gate/<shot>-diff.png` for every fail.
   Iterate: change the part, never the test inputs, never the thresholds. At most 12 iterations per golden; after that
   the golden is blocked (§13).
8. **Logic-only parts** (no widget; marked `own:logic` in the manifest): write
   `rb/o<NN>/<Pascal>ParityTest.kt` asserting every constant and rule of the Dart file (durations, curves, maths,
   thresholds, enum orders, storage names). **Check:** evaluate rule P1.
9. **Folded parts** (their Dart code lives in a native file another order owns): read them, compare with the owner's
   native code, and list gaps under "Folded gaps" in `evidence/<NN>.md`. Never edit the owner's file.
10. **Words.** A part that writes its own words (a label, a "Copy" or "Details") takes them from `ui/words`. If the
    English text already exists there, reuse that key. Otherwise add it to your own
    `native/ui/words/src/commonMain/composeResources/values/strings_rb<NN>.xml` and `values-ar/strings_rb<NN>.xml`,
    key `rb<NN>_<arbKey>`, with the English and Arabic text copied from `lib/l10n/app_en.arb` and `app_ar.arb` at
    FLUTTER_REF. **Check:** rule C1 (no typed copy), and both files have the same keys.
11. **Commit** the part (§10), then report `gate-pass` (the gate does it) or `blocked` (§13).

### 6.1 Widget → Compose map

| Flutter | Compose (kit internals only) |
|---|---|
| `Container`/`DecoratedBox` with a kit decoration | `KitSurface(level, role, …)` (the resolver paints it); never `Modifier.background` |
| `Padding`, `SizedBox`, `Column`, `Row`, `Stack`, `Align`, `Expanded`, `Flexible`, `Spacer`, `Wrap` | `padding`, `size`/`Spacer`, `Column`, `Row`, `Box`, `align`, `weight(1f)`, `weight(1f, fill = false)`, `Spacer`, `FlowRow` |
| `Text`/`RichText` with a `KitTextRole` | `KitText(…, role = TypeRole.<Role>)`; spans: `AnnotatedString` |
| `Icon(AppIconography.x)` | `KitIcon(KitIcons.x)`; the glyph names match `app_iconography.dart` |
| `InkWell`/`GestureDetector` | `KitTappable` (ripple, focus ring, 48 dp minimum, haptics) |
| `CustomPainter` | `Canvas`/`drawBehind` in the part, same geometry, same order of draws |
| `BoxShadow` | the decoration's `shadows` (spread, blur and offset copied; blur radius = Flutter `blurRadius`, sigma = blur/2) |
| `AnimatedContainer`/`AnimationController` with `KitMotion` | `animate*AsState` / `Animatable` with `MotionTokens` durations and the same curve; under `MotionLevel.Off` or reduced motion: the end value at once |
| `MediaQuery.textScalerOf` | `LocalDensity.current.fontScale`; chrome roles capped at 1.3 by `KitText` (`TextScalePolicy`), reading roles scale fully |
| `Directionality`/`EdgeInsetsDirectional` | `LocalLayoutDirection`; start/end padding; mirrored offsets (`hardOffset` dx) in RTL |
| `LayoutBuilder` + `KitLayout.windowOf` | `BoxWithConstraints` + `WindowClass` |
| `KitMaxWidth.<role>(child)` | `KitMaxWidth(CapRole.<Role>) { … }` |

---

## 7. Native rules every part keeps

- **A1 Kit only.** Screens (`native/feature/**`, `native/ui/shell/**`) arrange kit parts with layout, scrolling,
  semantics and focus only. A missing part is a kit part, added by the order that owns the Flutter part, never drawn on
  a screen. **Check:** rule K1 (no `Text(`, `Icon(`, `Canvas(`, `Surface(`, Material components, `.background(`,
  `.border(`, `.clip(`, `.shadow(`, `.drawBehind(`, `.clickable(` added under `native/feature/` or `native/ui/shell/`).
- **A2 No Material leaking.** `androidx.compose.material3` and `MaterialTheme` only in the bridge files
  (`KitTheme.kt`, `theme/MaterialBridge.kt`, `theme/KitThemePreview.kt`, `motion/KitMotionScheme.kt`,
  `motion/KitRefresh.kt`, `motion/KitGrow.kt`, and the two kit-chat files that already import it). No Material default
  colour, shape, typography, ripple or elevation may reach a pixel. **Check:** rule M1; the goldens.
- **A3 Signatures.** Every existing public function keeps its parameters, names, order and types; new parameters come
  with defaults. **Check:** rule S1.
- **A4 Text scale.** Chrome text caps at 1.3×, reading text scales fully (`KitText`). **Check:** matrix renders at
  1.0, 1.3 and 2.0 (§8.2) and the `_text2` goldens.
- **A5 Hard max width.** Every part holds its role's cap from `test/kit/kit_capacity.json` through `KitMaxWidth`
  (conversation 700, reading 720, list 960, sheet 640, confirm 560, dialog 480, popover 320, toast 480, state 440,
  lane 400, control 400, chip 320, glyph 96). **Check:** matrix `cap` rows; the manifest lists each class and role.
- **A6 Touch and semantics.** Every tappable is at least 48×48 dp and has a label (text or content description);
  decorative glyphs are hidden from semantics; reading order is top to bottom, start to end. **Check:** matrix
  `target` and `label` rows.
- **A7 Android-native feel.** Presses use the kit ripple (`KitTappable`), modals use `KitPredictiveBack`; no custom
  back handling in a part. **Check:** review; gate 03 and 11 parity tests.
- **A8 Reduced motion.** Every animation reads `MotionLevel` and the platform's reduced motion; Off shows the finished
  frame. **Check:** matrix renders run with `MotionLevel.Off`; each animated part has a parity test for the Off branch.
- **A9 Arabic and RTL.** Mirrored layout, mirrored offsets, Noto Sans Arabic fallback, no tracking in Arabic.
  **Check:** `_ar` goldens and matrix `rtl` renders.
- **A10 No raw error text.** Error copy is plain words with a way forward; technical text only under `KitDetailsFold`.
  **Check:** rule C1; review of every string you add.

---

## 8. The mandatory tools

### 8.1 Token generator (order 00, done)

`native/tool/design/gen_tokens.py --repo ~/wt/flutter-ref --ref 8302de537 --out native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/generated/FlutterKit.kt`.
Lead only. Port plan: `native/tool/design/port_plan.py` → `docs/design/native-looks-2026-11-04/port-plan.md`
(the batch numbering this standard keeps).

### 8.2 The golden-diff gate: `FlutterGoldenGate` v2 (order 01 builds it)

Today (commit cd03df939) the gate is `native/ui/kit-chat/src/desktopTest/kotlin/io/github/eslamasabry/oc/ui/kitchat/gate/FlutterGoldenGate.kt`
with `check(name, frame, motion, content)`: it renders at 412×915, density 1, in light and dark, and writes
`looks-out/gate/<golden>_<mode>-{flutter,native,diff}.png` and a whole-frame percentage in `report.tsv`. A whole-frame
percentage hides misses (0.4 % of 412×915 is a whole button), so order 01, **before any part**, moves and extends it
exactly as follows. Nothing else may be changed in the gate by any order.

**Module.** New Gradle module `:ui:gate` at `native/ui/gate` (`plugins { id("oc.kmp.compose") }`, included in
`native/settings.gradle.kts` the way `:ui:kit-content` is, one line in `native/MODULE_OWNERS`:
`:ui:gate	S	design-authority	rebuild`). Source set `desktopMain`, package `io.github.eslamasabry.oc.ui.gate`.
`desktopMain` depends on `:ui:design`, `:ui:kit` and the Compose desktop UI-test artifacts the kit-chat gate uses today.
`desktopTest` of `:ui:gate` depends on `:ui:kit-chat`, `:ui:kit-content`, `:core:security` (cross-kit galleries of orders
34-37 live there). `:ui:kit`, `:ui:kit-chat` and `:ui:kit-content` add `implementation(project(":ui:gate"))` to their
`desktopTest` dependencies (screen orders add it to their own feature module). The kit-chat copy and `GateProofTest` move
to `:ui:gate` (`GateProofTest` keeps its three cases).

**API (additive; the existing call shape keeps working):**

```kotlin
package io.github.eslamasabry.oc.ui.gate

enum class GateRef(val dir: String) { Product("8302de537"), Glass("1bbdc3f9c") }

object FlutterGoldenGate {
    enum class Frame { Part, Screen }
    const val CHANNEL_SLACK: Int = 24

    /** Renders [content] once per mode as the Flutter golden `<name>_<mode>` and records the comparison.
     *  [name] is the golden stem without the mode; its suffixes set the axes (see "Name grammar").
     *  Throws only when the golden file is missing or the content throws; thresholds are applied by gate_order.sh. */
    fun check(
        name: String,
        frame: Frame = Frame.Part,
        motion: MotionLevel = MotionLevel.Off,
        style: Style = Style.Solid,
        ref: GateRef = GateRef.Product,
        modes: List<String> = listOf("light", "dark"),
        wrap: @Composable (@Composable () -> Unit) -> Unit = { it() },
        content: @Composable () -> Unit,
    ): Map<String, Double>          // mode -> box %

    /** The native-only axes for one part: every Style × light/dark × font scale 1.0/1.3/2.0 × LTR/RTL at 412×915,
     *  each cap class measured at 1600×1000 (light, Solid, 1.0, LTR), touch targets and labels at 412×915. */
    fun matrix(
        part: String,                               // the tracker id, e.g. "part-kit_row"
        caps: Map<String, String> = emptyMap(),     // Flutter class -> Flutter role ("list", "chip", …) from the manifest
        frame: Frame = Frame.Part,
        wrap: @Composable (@Composable () -> Unit) -> Unit = { it() },
        content: @Composable () -> Unit,
    )
}
```

**Name grammar** (Flutter's `kitGalleryName`): `<shot>[_ar][_text2][_<W>x<H>]`. `_ar` → locale `ar`, RTL, Noto Sans
Arabic fallback (copy `test/fixtures/fonts/NotoSansArabic-{Regular,Bold}.ttf` from FLUTTER_REF into
`native/ui/gate/src/desktopMain/resources/fonts/`, OFL); `_text2` → `fontScale = 2f`; `_<W>x<H>` → that window
(default 412×915). Golden path: `looks-gate/flutter/<ref.dir>/<name>_<mode>.png`, falling back to `looks-gate/flutter/`
for Product.

**Frames.** `Frame.Part` repeats Flutter's `kitGalleryPart` (ground, 16 dp top and bottom, centred, at most 720 dp);
`Frame.Screen` gives the whole window. The theme comes from `KitTheme(appearance = KitAppearance(mode, motion, style),
platform = DesktopKitPlatform(), localeTag, stillFrame = true)`. After composition: idle, advance the clock 1,200 ms,
idle, capture.

**Measures**, per shot, comparing the Flutter and native frames pixel by pixel (a pixel differs when any channel differs
by more than `CHANNEL_SLACK`):

- `frame_pct`: differing pixels in the whole frame (kept for continuity);
- `box`: the ink box, the union of pixels that differ from the frame's ground colour (sampled at (2,2) in each image)
  in either image, padded by 4 px and clipped to the frame; the whole frame when nothing differs from the ground;
- `box_pct`: differing pixels inside `box` ÷ `box` area × 100;
- `max_blob`: erode the differing-pixel mask once (a pixel stays only when it and all 8 neighbours differ), then the
  size in pixels of the largest 8-connected cluster;
- `size`: `same-size`, or `size <nw>x<nh> vs <fw>x<fh>`.

**Outputs** (all under the worktree root, which the gate finds as `user.dir/../../..`):

- `looks-out/gate/<shot>-flutter.png`, `-native.png`, `-diff.png` (native dimmed, differing pixels red, the ink box
  outlined in blue);
- `looks-out/gate/rows/<shot>.tsv`, one line: `shot	ref	frame_pct	box_pct	max_blob	x,y,w,h	size` (`shot` includes the
  mode; percentages with two decimals);
- `looks-out/gate/matrix/<part>.tsv`: first line `# styles: <storage names of Style.entries, comma-separated>`, then
  `render	<part>	<style>	<mode>	<scale>	<dir>	PASS|FAIL	<detail>` (scale `1.0|1.3|2.0`, dir `ltr|rtl`; FAIL on any
  exception or an empty composition), `cap	<part>	<Class>	<role>	<painted dp>	<cap dp>	PASS|FAIL` (painted = ink width at
  1600×1000), `target	<part>	<smallest touch side dp>	PASS|FAIL` (PASS at 47.5 or more), `label	<part>	<unlabelled
  clickables>	PASS|FAIL`.

**Check:** order 01's gate runs `GateProofTest` and `rb/o01/GateSelfTest` (a red 8×8 square on an otherwise identical frame
must give `max_blob` ≥ 36; a 1 px horizontal shift of a text line must give `max_blob` ≤ 16).

### 8.3 The order gate: `native/tool/design/gate_order.sh <NN>`

Committed with this standard, with `rb_gate.py` and `gate_thresholds.tsv`. It runs, from the worktree root:

1. `rb_gate.py static` — write set, numbers, Material, kit-only, copy, signatures, commits (§9.2);
2. `rb_gate.py fetch` — your goldens into `looks-gate/flutter/<ref9>/`;
3. Gradle, under a slot lock: `<module>:desktopTest --tests '*.rb.o<NN>.*'` for each module in your manifest;
4. `rb_gate.py evaluate` — every manifest golden against its tier, waivers, the matrix, parity tests; posts
   `gate-pass`/`gate-fail` per part (and per school for orders 34, 36, 37) to the tracker with the worst shot's images.

It exits 0 only when all four pass. `RB_DEV=1` skips the clean-tree and commit checks while you iterate; the final run is
without it. **Check:** its exit code is the order's `GATE:` line.

---

## 9. Acceptance gates

### 9.1 What must pass, per part

| Axis | Flutter golden exists | Native-only check |
|---|---|---|
| light, dark | every golden of the part, both modes | matrix |
| every school | the school galleries (orders 36, 37; Glass 34) | matrix renders in every `Style` |
| text 1.0 / 1.3 / 2.0 | 1.0 and `_text2` goldens | matrix renders at 1.0, 1.3, 2.0 |
| LTR / RTL | `_ar` goldens | matrix `rtl` renders |
| window sizes | `_360x800`, `_800x1280`, `_1280x800`, `_1600x1000` goldens | matrix `cap` rows at 1600 |
| max width | — | `cap` rows: painted ≤ cap for each class and role |
| touch, labels | — | `target` and `label` rows |
| logic | — | `<Pascal>ParityTest` for `own:logic` parts |

### 9.2 Static rules (rb_gate.py static)

| Rule | Fails when |
|---|---|
| W0 | a file under `looks-gate/` or `looks-out/` is committed |
| W1 | a changed path matches none of your `WRITES:` globs |
| W2 | uncommitted changes at the final run |
| L1 | an added line in `src/{commonMain,androidMain,desktopMain}` has a number other than 0 and 1 and no citation |
| L2 | a citation names a file that is not at the ref |
| L3 | a number on the line is not on the cited Flutter line(s) |
| L4 | more than 20 `// NV:` lines in the order |
| M1 | Material outside the bridge files (A2) |
| K1 | a drawing call added on a screen (A1) |
| C1 | user-visible words typed in code (`KitText("…")`, `text = "… …"`, `label =`, `title =`, `contentDescription =`, …) |
| S1 | an existing public signature changed incompatibly (A3) |
| G1, G2 | a commit subject or trailer does not follow §10 |

Citation format, at the end of the line: `// F: lib/ui/kit/kit_row.dart:332` (several: `// F: lib/ui/kit/kit_row.dart:928,929 lib/ui/kit/kit_tokens.dart:40-44`).
Glass: `// FG: lib/ui/kit/style/kit_style_glass.dart:57`. A number that is not visual (an index, a list capacity, a
retry count) takes `// NV: <reason>`; at most 20 per order.

### 9.3 Thresholds (lead-only file `native/tool/design/gate_thresholds.tsv`)

| Tier | Parts | box % ≤ | blob px ≤ |
|---|---|---|---|
| T1 | foundation: tokens, text, icons, surfaces, dividers, tappable | 1.0 | 16 |
| T2 | controls, rows, states, sheets, menus, frame, navigation, school galleries | 2.0 | 36 |
| T3 | text-heavy: markdown, code, logs, terminal, diff, viewer, transcript, tool rows | 3.0 | 49 |
| T4 | painted: glow, step light, rings, charts, meters, QR, images, scenes, live looks | 4.0 | 100 |
| T5 | screens | 4.0 | 100 |
| T6 | Glass goldens at GLASS_REF | 8.0 | 400 |

A shot passes when `size` is `same-size`, `box_pct` ≤ the tier's box % and `max_blob` ≤ the tier's blob. The tier of each
golden is in your manifest. The blob rule catches what a percentage hides: a missing 8 dp dot is a 64 px blob.
Calibration (lead only): if a reviewed exact port measures over its tier, the lead raises that tier in the file, with the
measured shot in the commit message; agents never touch the file.

### 9.4 Done, per order

1. `native/tool/design/gate_order.sh <NN>` exits 0 on a clean tree (no `RB_DEV`).
2. `docs/design/native-ui-rebuild/evidence/<NN>.md` has: the final gate output (the evaluate table), the waivers with
   reasons, the folded gaps, and one line per blocked item.
3. Every part of the order shows `gate-pass` in the tracker; you post `done` (§11).
4. Commits follow §10; nothing pushed.

### 9.5 Waivers

A golden that cannot pass for a reason outside your write set is waived in `docs/design/native-ui-rebuild/waivers/<NN>.tsv`,
one line: `<shot with mode>	<CODE>	<one-line reason naming the owner part or the missing state>`.

| Code | Allowed in | Meaning |
|---|---|---|
| `PLATFORM_ONLY` | any order | the golden shows a Flutter-only platform piece (for example a desktop scrollbar Flutter draws in a phone gallery) |
| `GALLERY_FOREIGN` | galleries (29, 30, 34, 36, 37) | the failing region belongs to another order's part; name its tracker id |
| `NO_NATIVE_STATE` | screens | the native screen state has no such state; name the missing `XUi` field |
| `DEFERRED` | screens | the golden shows a deferred or dropped screen (plan 07 §6) |

Caps: kit orders 5 %, galleries 30 %, screens 25 % of the order's goldens. A waiver with another code or no reason is
rejected. **Check:** evaluate rule V1.

---

## 10. Commits

- One commit per part (or per golden fix), in your worktree, never pushed.
- Subject: `<type>(<scope>): <what now matches Flutter> [skip ci]`, type `feat|fix|test|refactor|docs|chore`, scope the
  module or area in lower case (`kit`, `kit-chat`, `kit-content`, `design`, `gate`, `chats`, `settings`, …).
- Body: one or two plain sentences, then the trailers, each on its own line:

```
feat(kit): KitRow draws its 26 dp glyph and the needs-you dot as Flutter does [skip ci]

The row's leading glyph, the amber dot and the accent wash follow kit_row.dart at 8302de537.

Order: 08-rows-a
Engine: sonnet
Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
```

Codex and Command Code agents write the same without the `Co-Authored-By` line. **Check:** rules G1, G2.

---

## 11. Reporting

Two channels; both never fail the caller.

**Progress** (one line per step, after every commit and at every state change):

- On the VPS or the PC: `~/.local/bin/oc-update '<agent>' '<one line>' working|done|failed|blocked <DONE>/<TOTAL>`.
  Agent name: `rb-<NN>-<engine>`; DONE/TOTAL counts your order's parts (screens for screen orders).
- Anywhere else: `POST http://100.126.15.6:8768/u` (from the VPS: `http://127.0.0.1:8768/u`, the tunnel), JSON body
  `{"agent": "rb-05-codex", "msg": "part-kit_field: gate pass", "state": "working", "done": 7, "total": 10}`.
  The tracker (`/home/eslam/Storage/Code/parity-tracker/server.py`) requires the keys `agent` and `msg`; `state` is one of
  `working|waiting|blocked|done|failed|idle`; `done`/`total` are integers.

**Visual tracker** (per part, school or screen; gate_order.sh posts parts and schools for you):

- `POST http://100.126.15.6:8768/ui` (VPS: `http://127.0.0.1:8768/ui`), JSON
  `{"id": "<tracker id>", "state": "todo|working|gate-fail|gate-pass|done|blocked", "diff": 1.4, "agent": "rb-05-codex",
  "engine": "sonnet|codex|commandcode", "note": "<one line>", "images": {"flutter": "rb-05/<shot>-flutter.png",
  "native": "rb-05/<shot>-native.png", "diff": "rb-05/<shot>-diff.png"}}`. On the PC: `~/.local/bin/oc-ui <id> <state> …`.
- You post `working` when you start a part, `blocked` with the reason when you stop on it, and `done` for each part
  after the order's final gate passed. Screen orders post each `screen-<row>` id themselves.
- Image paths are relative to `/home/eslam/Storage2/wt/looks/gate/` on the PC; the tracker's puller copies
  `~/wt/rb-<NN>/looks-out/gate/` there as `rb-<NN>/` (lead prerequisite).

---

## 12. File ownership

- Your order's `WRITES:` globs are the only paths you may create, change or delete. Every order's set is disjoint from
  every other order's (checked when the orders were generated). **Check:** rule W1.
- A file in no order's set is read-only for all agents: the lead's tokens and tools, `native/ui/routes`, engines,
  ViewModels, other modules' build files, `MODULE_OWNERS` (except order 01's one line), CI.
- **Seams.** Where one order must call into a file another order owns (Glass painting in `KitSurface.kt`, the live-look
  registry), the lead lands a one-line call and a stub file before dispatching the order that fills the stub; the order
  lists them under "Lead prerequisites". Agents never land seams.
- Existing tests: you may change a test only when it is in your write set (`<OwnedFile>Test.kt`) and it asserts a value
  that contradicts Flutter; the commit says which Dart line wins. Any other test your change breaks makes the part
  blocked (§13).
- Native baselines (`native/ui/*/src/screenshots/<OwnedFile>/`) are re-recorded only for files you own:
  `cd native && ./gradlew <module>:testAndroidHostTest -Proborazzi.test.record=true --tests '<the test class that
  captures them>'`, under the gate lock. Flutter goldens are never edited, copied into the repo, or regenerated.

---

## 13. When to stop and mark blocked

Stop work on the item (part, golden or screen), post `blocked` with one line, add it to `evidence/<NN>.md`, and go on
with the next item, when any of these is true:

1. the fix needs a path outside your `WRITES:` (name the file, the line and the Flutter line it must follow);
2. the generated tokens lack a value Flutter reads (`needs token: <file>:<line>`);
3. a dependency's part draws wrong in your golden (name its tracker id; that is a `GALLERY_FOREIGN` waiver only in
   gallery orders);
4. a golden still fails after 12 iterations (attach the diff path);
5. the screen state lacks data the Flutter screen shows (name the missing field);
6. the order and this standard disagree, or a rule cannot be met as written;
7. you are about to guess. Never guess a value, a colour, a size or an intent.

Mark the whole order `failed` only when step 0 of the recipe cannot run (worktree, ref or Gradle missing). Never mark
`done` with a red gate.

---

## 14. Worked examples (real parts)

### 14.1 `part-kit_row` (order 08, tier T2, golden `kit_row_default`)

1. Read `~/wt/flutter-ref/lib/ui/kit/kit_row.dart` (1,424 lines), `kit_lead_column.dart` (folded: its native code is in
   `KitRow.kt`, which this order owns) and `kit_row_parts.dart` (order 10 owns `KitRowParts.kt`; you call it, never edit it). Constants to carry: `roomyHeight = 64.0` (`:121`), `glyphSize = 26.0`
   (`:332`), the needs-you dot `10` with a `2` ring (`:928-929`), the accent wash `alpha: .18` (`:1008`), and the text-scale
   rules: title 2 lines from 1.3×, supporting 2 lines from 1.3× and 3 from 2.0× (`:441-453`).
2. Read `test/goldens/kit/kit_row_golden_test.dart`: `_defaultRows` = "Fix the login bug" with `AppIconography.chat`
   and supporting "Finished 5h ago"; "Model" with `model` and the value "Claude Sonnet 4"; "Project settings" with
   `settings` and a chevron, in a group labelled "Conversations"; 13 scenes in the manifest (`kit_row_default`, `_1280x800`, `destructive`, `disabled`, `focused`, `hover`, `selected`, `server`, `unavailable`, `context_menu`, `menu_*`), light and dark.
3. Native: `kit/row/KitRow.kt` (owned). A line such as `val glyph = 26.dp // F: lib/ui/kit/kit_row.dart:332` passes L1-L3.
4. Gate case, in `native/ui/kit/src/desktopTest/kotlin/io/github/eslamasabry/oc/ui/kit/rb/o08/KitRowGateTest.kt`:

```kotlin
package io.github.eslamasabry.oc.ui.kit.rb.o08

class KitRowGateTest {
    private val rows: @Composable () -> Unit = {
        KitRowGroup(label = "Conversations") {
            KitRow("Fix the login bug", supporting = "Finished 5h ago", leading = KitRowLeading.Glyph(KitIcons.chat), onClick = {})
            KitRow("Model", leading = KitRowLeading.Glyph(KitIcons.model), trailing = KitRowTrailing.Value("Claude Sonnet 4"), onClick = {})
            KitRow("Project settings", leading = KitRowLeading.Glyph(KitIcons.settings), trailing = KitRowTrailing.Chevron, onClick = {})
        }
    }
    @Test fun kit_row_default() { FlutterGoldenGate.check("kit_row_default", content = rows) }
    @Test fun kit_row_default_1280x800() { FlutterGoldenGate.check("kit_row_default_1280x800", content = rows) }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_row", caps = mapOf("KitRow" to "list"), content = rows) }
}
```

5. Run `RB_DEV=1 native/tool/design/gate_order.sh 08`; read `looks-out/gate/kit_row_default_light-diff.png`. A red block
   at the left edge is a missing glyph column (the looks-pass "bare row glyphs" change): put Flutter's 26 dp glyph back.
   Thin red outlines on letters are anti-aliasing and erode away.

### 14.2 `part-kit_action_stack` (order 08, NEW `kit/action/KitActionStack.kt`, golden `kit_action_stack_default`)

1. `kit_action_stack.dart` is 94 lines: a primary, a secondary, at most two tertiary actions
   (`assert(tertiary.length <= 2)`, `:41`), `tokens.space2` between actions (`:53`) and `tokens.space1` before a reason
   (`:56`). Both gaps are tokens, so the native file has no number to cite for them.
2. The Flutter class is `KitActionStack`; native today has `KitActionBlock` (owned by order 07, `kit_buttons.dart`).
   You create `KitActionStack.kt` and build it from order 07's `KitButton`; you never edit `KitActionBlock.kt`.
3. The golden test is `kit_action_golden_test.dart` (`_stackDefault`: "Update", "Check now", tertiary "Restart").
   The `cap` row for `KitActionStack` must say `reading` (720).
4. The tertiary count is a contract: `require(tertiary.size <= 2) // F: lib/ui/kit/kit_action_stack.dart:41`.

### 14.3 `part-kit_composer` (order 14, tier T2, golden `kit_composer_idle_empty`, `Frame.Screen`)

1. Read `chat/kit_composer.dart` and the folded `kit_composer_layer.dart`, `kit_composer_edge.dart`,
   `kit_composer_caption.dart`, `kit_composer_types.dart` (order 16 verifies them; you port them into
   `KitComposerBox.kt`, which you own).
2. The test's `_Backdrop` draws transcript words behind the composer and pins it 8 + 16 dp from the bottom inside the
   gutter, so the gate case uses `Frame.Screen` (the proof case in `GateProofTest` shows the exact backdrop).
3. The port plan records a looks-pass change: the native message box became two rows (words, then plus, chips and
   Send). Flutter's idle box is **one row: plus, words, mic**, in a 26 dp pill (`composerRadius`, token). The golden
   decides: you put the one-row box back. That is not an "improvement" decision; it is §2.1.
4. Motion: the gate renders with `MotionLevel.Off`; the running glow (order 15) is not in the idle shot.

---

## 15. Glossary

| Term | Meaning |
|---|---|
| Part | One Flutter kit file and the native code that reproduces it; tracker id `part-<file stem>` (`part-glass-<stem>` for GLASS_REF files). |
| Folded part | A Flutter file whose native code lives in a file another order owns; its order verifies, never edits. |
| Order | One unit of work for one agent: `orders/<NN>-<name>.md`. Kit orders 01-34 keep the port plan's batch numbers. |
| School / Style | A design school the person picks (§5). Code: `Style`, Dart `KitSurfaceStyle`. |
| Knobs / Fine-tune | Corners, Depth, Density (Spacing), Edges over a school's defaults. |
| Golden | A Flutter PNG at FLUTTER_REF or GLASS_REF; the target picture. |
| Gallery | A golden scene that draws several parts (school galleries, scenes). |
| Shot | One golden in one mode: `<stem>_<light|dark>`. |
| Ink box | The region where either picture differs from the page ground (§8.2). |
| Blob | The largest cluster of differing pixels after erosion (§8.2). |
| Tier | The threshold class of a golden (§9.3). |
| Matrix | The native-only renders per part: schools × modes × text scales × directions, caps, targets, labels. |
| Seam | A one-line call between two orders' files, landed by the lead. |
| Chrome / reading text | Text roles capped at 1.3× / text roles that scale fully. |
| FLUTTER_REF / GLASS_REF | `8302de537…` (2.1.1) / `1bbdc3f9c…` (last Glass). |
| Lead | The design lead: owns tokens, thresholds, seams, merges, the tracker puller. |
