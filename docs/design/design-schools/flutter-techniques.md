# Design schools in Flutter: techniques, budgets and traps (2026-10-21)

*Part of [design schools](README.md). Read this before you paint a single shadow. Everything here works on the pinned **Flutter 3.47.1** (Shorebird engine `03e67977`), with framework APIs only and Impeller on Android. I checked each API named below against the pinned SDK source in `~/.shorebird/bin/cache/flutter/91f8bd75…/`.*

## 0. The rules that cover every technique

1. **Everything is a kit concern.** Screens never paint a shadow, blur or border. The style resolver ([contract.md](contract.md) §4) hands kit parts a `KitSurfaceLook`, and the part paints it.
2. **There is no raster cache on Impeller.** Skia used to cache a static `RepaintBoundary` subtree. Impeller redraws every layer on every frame it is visible, so a shadow that costs 0.3 ms costs 0.3 ms × the number of rows on screen during a scroll. Budget per *visible instance*, never per widget type.
3. **Stay on the engine's fast paths.** A blurred, solid-colour **rounded rectangle with uniform radii** has a dedicated analytic pipeline in Impeller (`SolidRRectBlurContents`, and `SolidRSuperellipseBlurContents` for superellipses). An ordinary `BoxShadow` under a `BorderRadius.circular(r)` box hits that path. Mixed radii (the prompt bubble's 6 dp tail corner), paths, `drawDRRect` and anything under a `saveLayer` fall back to a real Gaussian blur over an offscreen texture, which is far more expensive.
4. **No `saveLayer` you did not ask for.** `Opacity`, `ShaderMask`, `ColorFiltered`, clipping with `Clip.antiAliasWithSaveLayer` and `BackdropFilter` all allocate offscreen targets ([Flutter performance best practices](https://docs.flutter.dev/perf/best-practices)). Fade with colour alpha (`color.withValues(alpha:)`), not `Opacity`, inside parts that repeat in lists.
5. **Asset changes cost a release.** A new font or a `.frag` shader is a pubspec or asset change, and AGENTS.md says that needs a new Shorebird **release**, not a patch. Prefer framework drawing. Add an asset only when measured numbers say so.
6. **Every effect has a still and a solid form.** Reduced motion (`KitMotion.reduced`) stills it. Saver and high contrast swap it for its solid equivalent ([contract.md](contract.md) §6).

## 1. Glass: blur-behind

### Technique

```dart
// One blur per floating surface: the top bar, the composer, one sheet.
ClipRRect(                       // the clip bounds the backdrop read; without it
  borderRadius: radius,          // BackdropFilter reads (and blurs) the whole screen
  child: BackdropFilter.grouped( // shares one backdrop capture with its siblings
    filter: ImageFilter.blur(
      sigmaX: look.blurSigma, sigmaY: look.blurSigma,
      bounds: Offset.zero & size, // bounded blur: no colour bleed from outside the pane
    ),
    child: DecoratedBox(decoration: look.decoration, child: child), // the translucent tint and rim
  ),
)
// …and one BackdropGroup per route, placed by KitScreen:
BackdropGroup(child: page)
```

- `BackdropGroup` + `BackdropFilter.grouped` are in the pinned SDK (`widgets/basic.dart:471`, `:674`). Grouped filters share one backdrop capture: "The engine will perform only one backdrop blur but the results will be visually identical to multiple blurs" ([BackdropFilter API](https://api.flutter.dev/flutter/widgets/BackdropFilter-class.html), [BackdropGroup](https://api.flutter.dev/flutter/widgets/BackdropGroup-class.html)). **Overlapping filters must not share a key** (a sheet over a blurred bar): give the sheet its own `BackdropGroup` or a separate `BackdropKey`.
- `ImageFilter.blur(bounds:)` is the bounded blur in the pinned engine (`painting.dart:4361`). It treats samples outside the rect as transparent and renormalises the result, so the pane's edge doesn't pick up a halo of whatever sits outside it. Use it for every pane.
- `BackdropFilter.enabled` turns the filter off without rebuilding the subtree. Use it for "nothing is behind me" (see the budget) and for the Saver and high-contrast fallback.
- Sigma is a Gaussian standard deviation. CSS `blur(Npx)` corresponds roughly to sigma ≈ N/2. The NN/g guidance is "More background blur is better, especially with intricate backgrounds" ([NN/g, Glassmorphism](https://www.nngroup.com/articles/glassmorphism/)). The live background is already low-frequency, so sigma **12–20** is enough. Larger sigma costs more and buys nothing here.

### Where glass gets a real blur, and where it doesn't

| Surface | Blur | Why |
|---|---|---|
| Top bar, composer, nav dock and rail, jump pill | **Yes**, one each, grouped | Transcript text scrolls behind them. Without a blur, letters show through ("text behind glass must read as colour, never as letters", visual language §6). |
| Bottom sheet, dialog, menu popover | **Yes**, one per modal, own group | It sits over a scrim and a page. One modal at a time (kit-v2 §4.7). |
| Panels, cards, rows, chips, bubbles in a scrolling list | **No.** Translucent fill only | Nothing sharp moves behind them, only the slow live background. A fill at α 0.62–0.78 over a low-frequency wash looks frosted already. A blur here multiplies the cost by the number of rows and gains nothing. |
| Any surface while the live background is off and nothing scrolls under it | **No** (`enabled: false`) | There's nothing to blur, and the fill alone gives the same pixels. |

### Budget

- **At most 3 live backdrop filters on screen** (bar, composer, and one sheet or the dock), in at most 2 groups.
- Glass raster time at most **+2 ms** at p90 over solid on the reference scroll ([contract.md](contract.md) §9). A blur pane may never animate its size inside a list.
- In a golden or capture, a blur reads a frozen live background (`KitLiveBackground.paintWithoutLoops`), so it is deterministic.

### Traps

- **No clip means a full-screen blur.** A `BackdropFilter` without a clip ancestor filters everything to the screen's edge. That is the classic frame-rate killer.
- **Stacked blurs in lists.** Every ungrouped `BackdropFilter` triggers its own backdrop read and blur pass. Grouping helps, but third-party measurements still put many grouped filters at 1.8–3× a single capture (see the [g1455 docs](https://pub.dev/documentation/g1455/0.1.3/); the vendor's own claim, one device). The answer is to *not blur rows*.
- **Opacity above a blur.** An `Opacity` or fade transition above a `BackdropFilter` forces a `saveLayer`, and with `BlendMode.srcOver` the result can be wrong. The API doc suggests `BlendMode.src` in that case. Route transitions fade pages, so the bar's blur must survive a fade: test a push and pop in the capture.
- **Platform views** (the web preview, maps) are not blurred under them on some platforms ([glassmorphism package notes](https://pub.dev/packages/glassmorphism)). Keep glass off panes that sit over a platform view.
- **Impeller blur strength has changed between releases** (the iOS regression flutter/flutter#162951 is cited by the glassmorphism package). Pin sigma in tokens and re-check the capture after every Flutter bump.

## 2. Soft (neumorphism): paired outer shadows, and inset for pressed

### Technique

The raised state is **two `BoxShadow`s on a uniform-radius rounded rect**. Both hit the fast path, so this is cheap.

```dart
BoxDecoration(
  color: ground,                                 // ground-matched: the surface is the ground
  borderRadius: BorderRadius.circular(r),        // uniform radii → fast analytic shadow
  boxShadow: [
    BoxShadow(color: light, offset: Offset(-d * dir, -d), blurRadius: b), // highlight, top-start
    BoxShadow(color: dark,  offset: Offset( d * dir,  d), blurRadius: b), // shade, bottom-end
  ],
)
// dir = Directionality.of(context) == TextDirection.rtl ? -1 : 1 (see §5)
```

The pressed or selected state is **inset**: the two shadows fall *inside* the shape. Flutter's `BoxShadow` has no `inset` flag. `BlurStyle.inner` is a different thing (a soft inner edge, not an offset shadow). There are two ways to build it:

1. **The Blink method (baseline, no asset).** Clip to the shape. Fill a shape that is a big rect *minus* the rounded rect (`Path.combine(PathOperation.difference, …)` or `canvas.drawDRRect(outer, inner)`), offset by the shadow offset, with `MaskFilter.blur(BlurStyle.normal, sigma)`. This is the algorithm Chromium uses for `inset` box-shadow, and the BSD-licensed [`flutter_inset_shadow`](https://pub.dev/packages/flutter_inset_shadow) uses it too. The cost is a generic Gaussian blur (no fast path), so use it **only where few instances are visible at once**: Soft's pressed and selected controls, a field's inset well, a segmented track.

   ```dart
   class _InsetShadowPainter extends CustomPainter {   // kit-internal
     void paint(Canvas canvas, Size size) {
       final rrect = radius.resolve(dir).toRRect(Offset.zero & size);
       canvas.save();
       canvas.clipRRect(rrect);
       for (final s in shadows) {                       // KitInsetShadow(color, offset, sigma)
         final hole = rrect.shift(s.offset);
         final frame = Rect.fromLTRB(-s.sigma * 4, -s.sigma * 4,
             size.width + s.sigma * 4, size.height + s.sigma * 4);
         canvas.drawDRRect(RRect.fromRectAndRadius(frame, Radius.zero).inflate(abs(s.offset)),
             hole, Paint()..color = s.color..maskFilter = MaskFilter.blur(BlurStyle.normal, s.sigma));
       }
       canvas.restore();
     }
   }
   ```

2. **An SDF fragment shader (the upgrade path).** It computes the signed distance to the rounded rect at `p - offset` and turns it into a falloff (`1 - smoothstep` or an erf approximation). It is one cheap pass with no offscreen texture, at any resolution. `FragmentProgram` runs under Impeller and in `flutter test` (the live backgrounds already rely on it). The cost is a pubspec `shaders:` entry, which means a release. **Switch to it only if** the capture frame timing (§9) shows the Blink painter over budget, and keep the `KitInsetShadow` API unchanged so the swap is internal.

### Budget

- Raised (outer pair): about 2 analytic draws per surface, unlimited within reason.
- Inset (Blink): **at most 8 visible at once.** That covers a segmented control, a tab strip, a field and a pressed button.
- Every soft surface keeps uniform radii. The prompt bubble's shadow is drawn on a uniform-radius rrect behind the tailed bubble, a trick that keeps it on the fast path. The tail corner shows no visible shadow difference at 6 dp.

### Traps

- **A ground near white or near black kills the highlight** ([CSS-Tricks, Neumorphism and CSS](https://css-tricks.com/neumorphism-and-css/)). This is why Soft *moves the ground*: light `#E6E7E3`, dark `#1C1E23` ([schools/soft.md](schools/soft.md)). The resolver must then re-run the accent guard ([contract.md](contract.md) §3.3).
- **In dark, the dark shadow vanishes on a near-black ground.** Lift the ground, use a 1 px top rim of white at α 0.05–0.06, and push the shade to black α 0.6+. The owner said the dark concept barely shows depth, and this is the fix.
- **Shadows read as noise in a dense list.** Raise the *group panel*, never each row. Rows inside a raised panel stay flat.

## 3. (Clay, dropped)

The owner dropped Clay on 2026-10-21 ("It feels so fluffy"). Its technique notes are kept in [dropped/clay.md](dropped/clay.md). The section number stays so that cross-references to §4 onward remain valid.

## 4. Brutal: hard offset shadows and thick borders (the cheapest of all)

```dart
BoxDecoration(
  color: surface,
  borderRadius: BorderRadius.circular(r),
  border: Border.all(color: ink, width: 2),
  boxShadow: [BoxShadow(color: ink, offset: Offset(4 * dir, 4), blurRadius: 0)], // no blur
)
```

- A shadow with blur 0 is a plain filled rrect: the cheapest shadow there is. Neobrutalism's rule is "avoid using blur on them" ([NN/g, Neobrutalism](https://www.nngroup.com/articles/neobrutalism/)).
- **Pressed** = translate the face by the shadow offset and drop the shadow (the "push into the page" move). Use `Transform.translate` on the face only, never on the hit-test box (hit testing follows the transform), and keep the 48 dp target fixed. Under reduced motion, the pressed state is still applied, but instantly.
- Borders are **logical** px (2 dp), not physical hairlines. Snap to the pixel grid at DPR 2.625 and 3 by rounding `width * dpr` to an integer.

## 5. RTL: mirror the light, consistently

`BoxShadow.offset` is physical (x is always to the right). The kit must mirror it under `TextDirection.rtl` so that "top-start light / bottom-end shade" and "the brutal shadow falls toward the reading end" hold in Arabic.

- The resolver does it once: `KitShadowSpec.resolve(TextDirection)` multiplies dx by −1 in RTL. Parts pass `Directionality.of(context)`.
- A custom `Decoration` (§6) receives `ImageConfiguration.textDirection` in `BoxPainter.paint`, so it can mirror at paint time. That makes a decoration built once correct in both directions.
- Glass rims (light top edge, dark bottom edge) are vertical, so they don't mirror. The glow's travel direction already follows the existing composer code.
- Golden: one RTL Arabic variant per style ([contract.md](contract.md) §8). The test asserts the shadow's dx sign flips.

## 6. Switching style: cross-fade without janking

There are three moments, each with its own technique:

| Moment | Technique |
|---|---|
| **The person picks another style or knob** (Appearance studio, onboarding) | **Snapshot cross-fade at the scope.** `KitStyleScope` grabs the outgoing frame with `RenderRepaintBoundary.toImageSync()` and paints it over the new tree, fading α 1 → 0 over **200 ms** (`KitMotion.quick`–`standard` range, `KitMotion.enter` curve), then disposes the image. One texture and one alpha draw per frame cover everything: fonts, density (a layout jump that must *not* be animated in lists, kit-v2 §4.11), corners, fills, blur on and off. Under `KitMotion.reduced` the swap is instant. The trap: platform views are not captured, so skip the snapshot when a platform view is on screen and swap instantly. |
| **A part changes state** (pressed, selected, hover, focus) | **Implicit lerp inside the part.** `KitSurfaceLook.lerp` (a custom `Decoration` with `lerpFrom`/`lerpTo`) lets `AnimatedContainer`-style transitions interpolate shadows (`BoxShadow.lerpList`), inner shadows, border width and fill. Use `KitMotion.quick` (150 ms). Brutal's press is instant (`KitMotion.pressHold` semantics). |
| **Theme or brightness change** | Already handled by `MaterialApp`'s `AnimatedTheme` (200 ms `kThemeAnimationDuration`). Style tokens that live outside the theme follow the snapshot path above. |

Avoid an `AnimatedSwitcher` cross-fade of two live subtrees. It builds the whole screen twice, and every blur twice. `flutter_animate` is banned.

## 7. Typography per school

**Bundled today** (pubspec): `AppSans` = Geist Variable (wght 100–900) and its italic, `AppMono` = Geist Mono Variable, and Phosphor icons. Arabic uses the system font (Noto). All are SIL OFL.

**Recommendation: ship all five styles on Geist and Geist Mono first.** Variable weight, tracking and size contrast carry the character, which means no release and no new APK size:

| Style | Display (largeTitle/title) | Body | Labels/meta | Notes |
|---|---|---|---|---|
| Solid | Geist 650, −0.025em | Geist 400 | Geist 600 | today's table (visual language §2, §7) |
| Glass | Geist 650 | Geist 400–450 | Geist 600 | never below 400 on translucent fills; no thin weights |
| Soft | Geist 600 | Geist 400 | Geist 550 | slightly softer display weight |
| Brutal | **Geist 800**, −0.03em | Geist 500 | **Geist Mono 600** (sentence case, never uppercase) | heavy, blunt; mono labels carry the "raw" note |
| Paper | **Geist 750 at a larger largeTitle (34/38)**, −0.03em | Geist 400 | Geist Mono 500 for meta | Swiss size contrast, flush-start, grid; [International Typographic Style](https://en.wikipedia.org/wiki/International_Typographic_Style) |

**Optional open-licence faces** (each is a release and about 100–400 KB). Only if the owner wants more character after seeing Geist versions:

- Brutal: [Archivo](https://fonts.google.com/specimen/Archivo) (OFL, variable width and weight) or [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk) (OFL). The latter was the app's old display face, which visual language §2 retired, so it needs an owner yes.
- Paper: [Inter Tight](https://fonts.google.com/specimen/Inter+Tight) or [IBM Plex Sans](https://fonts.google.com/specimen/IBM+Plex+Sans) (both OFL).
- Under an Arabic locale every style keeps the system face, with tracking 0 (connected script). A style may change weight, never the Arabic family.

## 8. Accessibility fallbacks

| Signal | Flutter source | What the style system does |
|---|---|---|
| High contrast | `MediaQuery.highContrastOf(context)`: iOS 13+ "Increase Contrast"; **Android API 34+ "High contrast text"** (pinned SDK, `media_query.dart:652`) | Glass and Soft resolve to **Solid**. Brutal and Paper keep their look (they are already high contrast). Every style forces Edges on with a ≥ 3:1 boundary colour, Depth flat, and an opaque fill. |
| Reduce transparency | **Not exposed by Flutter** (no `MediaQueryData` field; Android has no standard equivalent) | Our own controls stand in: Saver (forces Solid) and the Style picker itself. Glass always meets AA on its worst-case backdrop (contract §7), so it never *depends* on a reduce-transparency switch. NN/g's third best practice, "let users adjust transparency", is met by Style. |
| Reduce motion | `MediaQuery.disableAnimationsOf` + Effects › Motion Off (`KitMotion.reduced`) | No style cross-fade (instant), no press translation animation, the glow is still. |
| Bold text | `MediaQuery.boldTextOf` | Each style's body weight steps up one notch (400 → 600). Brutal's display stays 800. |
| Large text | `TextScaler` up to 2.0 (G6) | Density Compact never shrinks below the 48 dp target. Radii don't scale with text. |
| Battery | Saver (Appearance) | Forces **Solid**, true black and motion off. This mirrors Windows, where "Acrylic effects are automatically disabled when a device enters Battery Saver mode" ([Microsoft, Acrylic](https://learn.microsoft.com/en-us/windows/apps/design/style/acrylic)). |

## 9. Measuring the 60 fps budget

- The reference scenario: the chat with a running turn (live line, glowing composer, steps), scrolled for 5 s by a scripted fling, at 412×915 and DPR 2.625, live background Aurora at Normal.
- The harness: a `tool/capture/style_perf_test.dart` integration run in `--profile` on the **emulator with the host GPU** (owner rule: emulator, never the owner's phone). It collects `SchedulerBinding.addTimingsCallback` `FrameTiming`s.
- Pass criteria per style: p90 raster ≤ **8 ms**, no frame over **16 ms** after warm-up, and a raster p90 delta against Solid of ≤ **+2 ms** (Glass) or ≤ **+1 ms** (Soft, Brutal, Paper).
- Emulator numbers are relative, not absolute. Report them against Solid on the same run, never as an absolute phone claim.

## 10. Golden tests: N styles × light/dark without exploding the suite

There are 4,644 goldens today (`find test -name '*.png'`), and multiplying them by 5 styles is out of the question. The strategy:

1. **Solid owns the existing suite.** Every existing golden renders Solid and **must not change**. That is the proof that Solid is pixel-identical. A school branch that changes any existing PNG fails review.
2. **One style sheet per style × brightness.** `test/goldens/kit/style_sheet_golden_test.dart` (baseline harness) renders a tall 412-wide sheet with every surface family ([contract.md](contract.md) §5) in its states: rest, pressed, selected, disabled, focused, needs-you, running composer (glow frozen). That is **4 non-solid styles × 2 = 8 images**.
3. **Variants, one each per style:** Arabic RTL dark (mirrored shadows), 320 dp at 2.0 text light, and high contrast (proves the fallback). That is **12 images**.
4. **A knob matrix on one style** (Paper, the cheapest): Sharp/Compact/Flat/None against the defaults, **4 images**. The knobs are style-agnostic, so one style proves the plumbing.
5. **Invariants without pixels.** `test/kit/style/kit_style_contract_test.dart` iterates **every style × brightness × knob combination (5 × 2 × 3 × 2 × 2 × 2 = 480)** in pure Dart and asserts the contract (§7 of contract.md): text contrast on the resolved fill, including glass's worst-case backdrop; 3:1 boundaries where needed; the focus ring at 3:1; Saver and high-contrast resolution; the RTL sign flip; lerp endpoints; uniform radii wherever a shadow is cast. This runs in milliseconds.
6. **Full screens are captures, not goldens.** `tool/capture/style_schools_test.dart` writes one full phone screen per image for review (the [owner's one-screen-per-image rule](README.md#rules)). It lives outside the suite and runs on demand.
7. Blur and soft shadows may differ by a few LSBs between hosts. Give the style-sheet images an entry in `test/goldens/tolerances.json`, never a global tolerance.

That adds about **24 goldens** for five styles, instead of about 18,600 (the suite × 4).

## 11. pub.dev verdicts

| Package | Version / date | Licence | Verdict |
|---|---|---|---|
| [glassmorphism](https://pub.dev/packages/glassmorphism) | 4.0.0, Sept 2026 | Apache-2.0 per metadata (its README links MIT, inconsistent) | **No.** One `BackdropFilter` per container, no grouping. Its own doc warns that many blurred widgets are expensive. Our resolver does the same thing better with `BackdropGroup`. |
| [glass_kit](https://pub.dev/packages/glass_kit) | 4.0.2, Sept 2025 | (check) | **No.** The same per-container blur model. |
| [liquid_glass_renderer](https://pub.dev/packages/liquid_glass_renderer) | 1.0.0-dev.1, 20 Oct 2026 | "(pending)" | **No.** A pre-release that needs Flutter GPU (preview, an `--enable-flutter-gpu` flag). The owner rejected liquid glass anyway (branch `feat/true-liquid-glass`). |
| [flutter_neumorphic](https://pub.dev/packages/flutter_neumorphic) | 3.2.0, Nov 2021 | MIT | **No.** Unmaintained for 5 years, and it brings its own widget set (that would break kit-only). |
| [flutter_neumorphic_plus](https://pub.dev/packages/flutter_neumorphic_plus) | 3.5.0, Jul 2025 | MIT | **No.** A maintained fork, but it is a parallel widget kit (`NeumorphicButton` and the rest). We need decorations, not widgets. |
| [flutter_inset_shadow](https://pub.dev/packages/flutter_inset_shadow) | 2.0.3, Apr 2024 | BSD-3-Clause | **Don't depend on it. Borrow the idea.** It extends `BoxShadow` with `inset`, using the Blink algorithm. That's about 60 lines, which we write ourselves in the kit (§2.1). Depending on a 2-year-old package that shadows framework types (`BoxDecoration`, `BoxShadow`) would leak into every import. |
| [flutter_inset_box_shadow](https://pub.dev/packages/flutter_inset_box_shadow) | 1.0.8, Apr 2022 | (check) | **No.** The abandoned predecessor of the above. |
| [clay_containers](https://pub.dev/packages/clay_containers) | 0.3.4, Dec 2023 | (check) | **No.** It is actually neumorphic containers, and a widget kit. |
| [neubrutalism_ui](https://pub.dev/packages/neubrutalism_ui) | 2.0.2, Nov 2023 | (check) | **No.** Brutal is a border and a blur-0 shadow, which needs no package. |
| [animated_theme_switcher](https://pub.dev/packages/animated_theme_switcher) | 2.0.10, Dec 2023 | (check) | **No.** It replaces the theme machinery. Our snapshot cross-fade (§6) is about 40 lines. |

**Net: no new dependency.** Every effect is framework drawing: `BoxShadow`, `BackdropFilter.grouped`, `ImageFilter.blur(bounds:)`, `MaskFilter.blur`, `CustomPainter` and `toImageSync`. An SDF shader is the only optional asset, and only behind measured need.
