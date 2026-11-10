# liquid_glass_easy 5.0.0: reference and fit for this app (2026-10-19)

Sources read on 2026-10-19: every page under https://ahmeedgamil.github.io/liquid_glass_easy/en/ listed in the brief (start, engines, lens, lite, batch, blend, group, touch, adaptivity, morph, components, slider, toggle, button, fab, appbar, tabbar, scaffold, scrolledge, motion, draggable, shadow, sheet, dialog, efficiency, api, api-view, api-lens, api-grouping, api-touch, api-adaptivity, api-components, api-chrome, api-shape-helpers), pub.dev (package page, versions, changelog, license, and the pub.dev JSON API), and the GitHub repo (README listing, raw pubspec.yaml, `gh api` for pushed date, issues page).

Reading caveats, stated once:
- WebFetch returns a model-written digest, not raw HTML. Code samples marked **verbatim** were returned in code blocks by the fetch; samples marked **reconstructed** were summarised by the fetch and rebuilt here from the parameter tables. Treat reconstructed samples as unverified until compiled.
- `../demos/index.html` has no readable content (title only, "liquid_glass_web_demos"); it is an app, not text. Demos are unverified.
- I could not list the repo's `lib/assets/shaders` folder via the contents API (404 on `lib/shaders`); the shader list comes from the pubspec instead (eight programs, below).
- Nothing was run: no Flutter, no tests, no code edits.

---

## 1. What it is

| Fact | Value | Source |
|---|---|---|
| Package | `liquid_glass_easy` | pub.dev |
| Latest version | **5.0.0**, published 2026-10-10 (pub.dev API) | pub.dev API |
| Publisher | hexamil.com (verified); author Ahmed Gamil (AhmeedGamil) | pub.dev, LICENSE |
| License | **MIT** (copyright 2025 Ahmed Gamil) | pub.dev license tab |
| Score | 276 likes, 160 pub points, about 30.1k downloads total (weekly figure blank) | pub.dev |
| Platforms | Android, iOS, Linux, macOS, web, Windows | pub.dev |
| Dart SDK | `>=3.6.2 <4.0.0` | raw pubspec |
| Flutter | `>=3.3.0` (declared floor; the real floor is higher, see 6) | raw pubspec |
| Dependencies | `flutter`, `meta ^1.9.0`. Dev: `flutter_test`, `flutter_lints ^5.0.0` | raw pubspec |
| Assets | 8 fragment shaders under `lib/assets/shaders/`: `liquid_glass.frag`, `liquid_glass_skia.frag`, `liquid_glass_border.frag`, `liquid_glass_border_skia.frag`, `metaball_glass.frag`, `metaball_glass_skia.frag`, `liquid_glass_desktop.frag`, `metaball_glass_desktop.frag` | raw pubspec |
| Repo | github.com/AhmeedGamil/liquid_glass_easy: 139 stars, 159 commits, last commit **2026-10-10** ("Lite glass: docs describe the painted surface rim"), last push 2026-10-15 | `gh api` |
| Open issues | 4 issues (+1 PR listed): #32 "Can not use SnackBar" (opened Oct 19), #31 "Skills file for vibe coders", #30 TabBar opt-in press-and-slide pill drag, #19 "LiquidGlassToggle is not working well on desktop and web" (4 comments). Closed count not shown. | issues page |
| Versions | 24 releases in about 10 months. 5.0.0 (8 days old), 4.3.4/4.3.3 (21 days), 4.3.2 (23), 4.3.1 (34), 4.3.0 (37), 4.2.0 (54), 4.1.x (59), 4.0.0 (2 mo), 3.x (2-4 mo), 2.x (4 mo), 1.x (9-10 mo) | pub.dev versions |

**Maintenance state.** Active and fast-moving: one maintainer, 24 releases, a breaking 5.0.0 major 8 days ago, a release every 1-3 weeks. Risk of API churn is real (4.x to 5.0 changed `LiquidGlassStyle`, lights, refraction model and lite glass; see changelog digest below). Repo has `ADAPTIVITY.md`, `showcases/`, `example/`. No tests directory is listed at top level (unverified whether tests exist under `lib/`/`example/`).

**Description** (pubspec): "Apple iOS Liquid Glass for Flutter: a shader lens that refracts what is behind it, with blending, morph, adaptive chrome, controls, batching and lite glass."

### Changelog digest (from pub.dev; paraphrased by the fetch)

- **5.0.0** (breaking): light `thickness` is literal logical px (convert 4.3.4 values: specular x5.4, glow x29.4); `refractionIndex` (1.1) and `refractionWidth` (12) replace distortion/depth/radial mode/`diagonalFlip`; `LiquidGlassStyle` is flat (shape, specular, glow, refraction, magnification, dispersion, saturation, blur as one sigma double, color, shadow, adaptivity, `liteGlass`); glow angle is absolute; `chromaticAberration` becomes `dispersion` in logical px (old x660), off by default except on switch thumb and tab bar glass pill; `LiquidGlassLitePickup` removed, `liteGlass` is a bool, lite rim painted over the surface reading nothing (robust through transitions and overscroll); scaffold lost dialog parameters (use `showLiquidGlassDialog`); standalone `LiquidGlassTabBar` with glass pill now positions itself at the bottom; lens shader declares 20 uniforms (was 26). No Flutter 3.47, Android or Impeller-specific fixes listed.
- **4.3.4**: desktop shadow drawn from two blurred rrects for subpixel motion.
- **4.3.3**: Windows/Linux under Impeller use the mobile shader (analytic gradient because hardware derivatives return zero there); compile times fell sharply (Windows blender 162 s to 8.3 s, lens 5.6 s to 2.1 s; example first frame 175 s to 9 s, **Flutter 3.47.4, debug build**); Impeller no longer loads the border shader (`createBorderShader` throws `UnsupportedError`).
- **4.3.2**: `LiquidGlassShadow.insideGlass`; tab bar capsule gets own read inside batches; **Flutter 3.47 fix**: batched blurred lenses and the blender drew glass offset inside the outline; shaders now detect the engine frame; batching saves less on 3.47 because of an engine bug.
- **4.3.1**: morph leaving-child fix; batched blurred lens no longer runs ahead of its page during route transitions.
- **4.3.0**: lite pickup (since removed), `LiquidGlassGroup` deprecated, `LiquidGlassMorph`, `LiquidGlassBatch`, sheet and dialog presenters added.
- **4.2.0**: adaptivity, `LiquidGlassScrollEdge`. **4.1.x**: FAB, dialogs, Impeller content wobble fix. **4.0.0**: tab bar/switch renamed. **3.5.0**: `LiquidGlassFlex`. **3.3.1**: web on Flutter 3.44+ (SkSL validation). **3.2.2**: **vertically mirrored refraction on OpenGL ES** (Android emulators, engines after 3.44.0). **3.0.0**: `LiquidGlassLens`. Earlier: ready-made components, OpenGL ES texture fix, lens position clamp.

---

## 2. Concepts

| Concept | What it does | When to use |
|---|---|---|
| **Lens** (`LiquidGlassLens`) | One glass surface. Bends (refracts) the content behind it at its edge across a band `refractionWidth` px wide by `refractionIndex`, with optional magnification, dispersion (colour fringe), blur, saturation, tint, shadow, rim lights. Size comes from layout. Child is always clipped to the glass shape. | A single floating panel over content that is already painted below it in a `Stack`. |
| **Style** (`LiquidGlassStyle`) | Flat, copyable look descriptor (`copyWith`, `merge`) shared by lenses and every component. | Define once, apply across components. |
| **Shapes** (`LiquidGlassShape`) | Outline only: `roundedRectangle` (cheapest clip), `squircle` (L^n, iOS curvature), `continuousRoundedRectangle` (Apple capsule corners; the default). `cornerRadius` default 50. `clipQuality`: `roundedRectangle` (default) or `exact` (adds a save layer). | `squircle` + `exact` for true curve; leave the default for continuous. |
| **Rim lights** (`LiquidGlassLight`) | The border is two lights: `specular` (crisp, takes the backdrop colour brightened) and `glow` (softer, wider). Each is a pair of opposing arcs. | Tune the rim; start from a component's `defaultStyle` and `copyWith`. |
| **Lite glass** (`LiquidGlassLite`, `style.liteGlass`, `LiquidGlassEngine.liteGlassOn*`) | Frost + tint + painted rim, no shader, no refraction, no capture, no lens budget. Keeps shape, colour, blur, shadow, flat magnification. | Cards, rows, sheets, long lists, low-end devices, web; and the cheap fallback for everything. |
| **Batch** (`LiquidGlassBatch`) | Impeller only: many lenses share one backdrop read; each keeps its own look; no member limit. Members must not overlap. `LiquidGlassBatch.exclude` opts one subtree out. | Lists or clusters of many separate lenses. |
| **Blend** (`LiquidGlassBlender`) | Fuses 2-8 lenses into one liquid surface with a metaball bridge (`smoothness` default 48); one shader, one read, one material, one rim around the merged silhouette. `smoothness: 0` is the old Group (hard union, no bridge). | Shapes that must read as one piece of glass, merging when they touch. |
| **Group** (`LiquidGlassGroup`) | Deprecated alias for `LiquidGlassBlender(smoothness: 0)`. | Do not use. |
| **Morph** (`LiquidGlassMorph`) | A glass sheet that measures its child and animates to the new size; two blobs of one liquid with a neck. Presets `fluid`, `anchoredPop`, `droplet`, `calm`, `plain`. | A control that changes size (menu to icon). Children need keys. Heavier than a lens. |
| **Touch** (`LiquidGlassTouch` / `LiquidGlassFlex`) | Lens deforms under a finger without changing layout: stretch, squeeze, lean, grip, hold swell, tap pop, spring release. Translucent listener, does not enter the gesture arena, so buttons inside still get taps. Null (default) costs nothing. | Press feedback on a glass control. |
| **Adaptivity** (`LiquidGlassAdaptivity`) | Samples the background (CIE L* lightness, smoothed with hysteresis) and flips glass tint and content colour between dark and light palettes with a 300 ms animation. Needs a sampler: `LiquidGlassScaffold.adaptivity` or `LiquidGlassView.adaptiveSampling`. Without one it runs on the fallback brightness and logs a one-time warning. | Floating bars over variable content (photos, mixed themes). |
| **Legibility** (`LiquidGlassAdaptiveArea`, `Link`, `Controller`, `AdaptiveContent`) | Share one verdict across a region, across subtrees, pause/resume sampling, or colour bare icons/text. Can drive status/nav bar icon brightness (`LiquidGlassSystemChrome`). | Keep text readable on moving backgrounds. |
| **Scroll edge** (`LiquidGlassScrollEdge`) | A fading dim + blur band pinned to the top/bottom edge, under floating chrome (the iOS 26 dimming scroll-edge effect). Touches pass through; one `BackdropFilter`; `blur: 0` removes it. | Keep floating bars legible over scrolling lists. |
| **View** (`LiquidGlassView`) | Capture pipeline for Skia/web; harmless on Impeller; also owns the batch and the adaptive sampler. | Required for refraction on Skia/web. |
| **Motion** (`LiquidGlassMotionPill`, `LiquidGlassLensMotion(Spec)`) | Moving thumb/pill that morphs between rest and active size and squashes/stretches with acceleration. | Slider thumbs, tab pills, custom thumbs. |

Verdict priority for adaptivity (first match wins): `permanentBrightness` (the only setting that stops sampling for that surface), a followed `link`, the enclosing `LiquidGlassAdaptiveArea`, the surface's own sampling, `initialBrightness`, `brightnessFallback` (`appTheme` default, or `platform`). Without a `Theme` ancestor `appTheme` silently reads light.

---

## 3. Full API reference

Types and defaults are from the generated API pages ("generated from the package source on every build") unless marked "(inferred)". Exported names (api.html): view (`LiquidGlassView`, `LiquidGlassViewController`, `LiquidGlassController`, `LiquidGlassShaders`, `LiquidGlassEngine`, `LiquidGlassRefreshRate`, `LiquidGlassPosition`, `LiquidGlassOffsetPosition`, `LiquidGlassAlignPosition`); lens and style (`LiquidGlassLens`, `LiquidGlassLite`, `LiquidGlassStyle`, `LiquidGlassShape`, `LiquidGlassLight`, `LiquidGlassShadow`, `LiquidGlassCornerStyle`, `LiquidGlassClipQuality`); combining (`LiquidGlassGroup`, `LiquidGlassBatch`, `LiquidGlassBlender`); touch and motion (`LiquidGlassTouch`, `LiquidGlassFlex`, `LiquidGlassFlexDeform`, `LiquidGlassFlexAdvanced`, `LiquidGlassFlexOutline`, `LiquidGlassMotionPill`, `LiquidGlassLensMotion`, `LiquidGlassLensMotionSpec`, `LiquidGlassDraggable`); adaptivity (`LiquidGlassAdaptivity`, `LiquidGlassAdaptiveSampling`, `LiquidGlassAdaptiveArea`, `LiquidGlassAdaptiveAreaScope`, `LiquidGlassAdaptiveContent`, `LiquidGlassAdaptivityLink`, `LiquidGlassAdaptivityController`, `LiquidGlassBrightnessFallback`, `LiquidGlassSystemChrome`, `LiquidGlassScrollEdge`, `LiquidGlassEdge`, `LiquidGlassScrollEdgeStyle`); components (`LiquidGlassButton`, `LiquidGlassFab`, `LiquidGlassFloatingActionButton` typedef, `LiquidGlassDialog`, `LiquidGlassAlertDialog`, `showLiquidGlassDialog`, `LiquidGlassSheet`, `showLiquidGlassSheet`, `LiquidGlassSheetAnchor`, `LiquidGlassSlider`, `LiquidGlassSliderLayout`, `LiquidGlassSwitch`, `LiquidGlassSwitchLayout`, `LiquidGlassMorph`, `LiquidGlassMorphMotion`, `LiquidGlassMorphAdvanced`); chrome (`LiquidGlassScaffold`, `LiquidGlassScaffoldAdaptivity`, `LiquidGlassAppBar`, `LiquidGlassTabBar`, `LiquidGlassTabBarItem`, `LiquidGlassTabBarAction`, `LiquidGlassTabItemStyle`, `LiquidGlassTabPillStyle`, `LiquidGlassTabMagnifierPillStyle`, `LiquidGlassPillMode`, `LiquidGlassGlyph`, `LiquidGlassGlyphBuilder`, `LiquidGlassLabel`, `LiquidGlassLabelBuilder`); shape helper functions (`liquidGlassClip`, `liquidGlassClipCornerRadius`, `liquidGlassCornerStyle`, `liquidGlassUsesRoundedClip`, `liquidGlassCornerSegments`, `liquidGlassSquirclePath`, `liquidGlassContinuousRoundedRectPath`).

Import: `package:liquid_glass_easy/liquid_glass_easy.dart`.

### 3.1 LiquidGlassView (StatefulWidget)

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `key` | `Key?` | none | |
| `controller` | `LiquidGlassViewController?` | none | Capture/sync control. |
| `backgroundWidget` | `Widget` | **required** | Tree drawn behind every lens. Capture source on Skia/web, rendered normally on Impeller. |
| `child` | `Widget?` | none | Drawn above background, below lenses. Lenses at any depth connect automatically. |
| `pixelRatio` | `double` | `1.0` | Capture resolution. Keep below 1.0 for full-screen backgrounds; `0.0` = device default; 1.0 recommended max. Skia only. |
| `realTimeCapture` | `bool` | `true` | Re-capture each frame; false reuses a cached snapshot. Skia only. |
| `useSync` | `bool` | `true` | Sync capture with the frame; false is async, cheaper, can lag one frame, stable at ~0.5 ratio. Skia only. |
| `refreshRate` | `LiquidGlassRefreshRate` | `deviceRefreshRate` | Capture cadence. Skia only. |
| `useImpellerBackdrop` | `bool?` | null (auto) | Force Impeller path (true) or legacy capture (false). On Skia set false if lenses fail to render. |
| `regionCapture` | `bool` | `false` | Skia sync path only: capture a sub-image per lens. Helps few small lenses on a big background; hurts many. |
| `batch` | `bool` | `true` | Impeller: `child` lenses share one backdrop read, `backgroundWidget` lenses another. Members must not overlap. |
| `foregroundBatchId` | `int?` | null | Share the child-batch key across views (used by the scaffold). |
| `adaptiveSampling` | `LiquidGlassAdaptiveSampling?` | null | Opt in to the luminance sampler: `const LiquidGlassAdaptiveSampling()` (pixelRatio 0.05, frameLimit 8). |

**LiquidGlassViewController**: `Future<void> captureOnce()` (one snapshot, does not stop real-time), `void startRealtimeCapture()`, `void stopRealtimeCapture()`.

**LiquidGlassController** (single lens, outside the tree): `showLiquidGlass({int? animationTimeMillisecond, VoidCallback? onComplete})`, `hideLiquidGlass({...})`, `resetLiquidGlassPosition()`.

**LiquidGlassShaders** (static): `Future<void> ensureLoaded([bool? impeller])`, `bool isLoadedFor(bool impeller)`, `bool get isLoaded`, `ui.FragmentShader createMainShader([bool? impeller])`, `createBorderShader([bool? impeller])` (Skia only; throws `UnsupportedError` on Impeller). Programs compile once per app run on the first lens that mounts; until then lenses show the frosted fallback. Shader cache is per app instance.

**LiquidGlassEngine** (static, set before the first lens builds): `bool liteGlassOnSkia` (default false), `bool liteGlassOnImpeller` (default false), `bool get liteGlass`.

**LiquidGlassRefreshRate**: `low` (~10 fps), `medium` (~24), `high` (~60), `deviceRefreshRate` (default).

**LiquidGlassPosition** (base; `Offset resolve(Size parentSize, Size lensSize)`), **LiquidGlassOffsetPosition**(`left`, `top`, `right`, `bottom`, all `double?`, null), **LiquidGlassAlignPosition**(`required Alignment alignment`, `EdgeInsets margin = EdgeInsets.zero`).

### 3.2 LiquidGlassLens (StatefulWidget)

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `key` | `Key?` | | |
| `style` | `LiquidGlassStyle` | `const LiquidGlassStyle()` | Null `style.shape` falls back to continuous rounded rectangle. |
| `visibility` | `bool` | `true` | False disables glass and removes `child`; instant, no backdrop cost. |
| `useImpellerBackdrop` | `bool?` | null | Inherits from the view, then `ImageFilter.isShaderFilterSupported`. |
| `touch` | `LiquidGlassTouch?` | null | Null = no listener/ticker. |
| `deform` | `LiquidGlassFlexDeform?` | null | External deformation for hosts; needs `restSize`; overrides `touch`. |
| `restSize` | `Size?` | null | Undeformed size for `deform`. |
| `honorBackdropAlpha` | `bool` | `false` | Skia capture only; folds backdrop alpha into coverage. |
| `child` | `Widget?` | null | Drawn on the glass, clipped to the shape. |

Render modes (automatic): Impeller refracts the live backdrop (no view needed); Skia/web with an ancestor `LiquidGlassView` that has `backgroundWidget` refracts the capture; Skia/web without one falls back to frosted (blur, tint, border; no refraction) and logs one debug warning. **Lenses in scrollables on Impeller/Android:** stretch overscroll can render lenses black at the scroll edges; wrap the scrollable in `ScrollConfiguration(behavior: MaterialScrollBehavior().copyWith(overscroll: false), ...)`.

### 3.3 LiquidGlassStyle

| Field | Type | Default | Meaning |
|---|---|---|---|
| `shape` | `LiquidGlassShape?` | null (consumer default) | Outline. |
| `specular` | `LiquidGlassLight?` | null (consumer default; lens draws a default specular) | Crisp rim. |
| `glow` | `LiquidGlassLight?` | null (lens: none) | Soft side rim. |
| `refractionIndex` | `double` | `1.1` | 1.0 none; 1.02 clear edge; 1.5 very strong. |
| `refractionWidth` | `double` | `12.0` | Bend band width, logical px. |
| `magnification` | `double` | `1.0` | Zoom of what is seen through. |
| `dispersion` | `double` | `0` | Colour fringe; useful range 0-13; non-zero adds a backdrop read per pixel in the band. |
| `saturation` | `double` | `1.0` | 0 grayscale. |
| `blur` | `double` | `0.0` | Gaussian sigma, logical px. |
| `color` | `Color` | `Color(0x00000000)` | Base tint; the border also takes tint from it. |
| `shadow` | `LiquidGlassShadow?` | null | Honoured by lens; **not supported inside a blender**. |
| `adaptivity` | `LiquidGlassAdaptivity?` | null | Palette flip. |
| `liteGlass` | `bool` | `false` | Draw as lite regardless of engine. |

Methods: `copyWith(...)` (null keeps current; `shape`, `specular`, `glow`, `shadow`, `adaptivity` cannot be cleared), `merge(LiquidGlassStyle? other)`.

### 3.4 LiquidGlassShape, enums, LiquidGlassLight, LiquidGlassShadow

**LiquidGlassShape**: `cornerStyle` (`LiquidGlassCornerStyle`, default `continuousRoundedRectangle`), `cornerRadius` (`double`, 50.0), `clipQuality` (`LiquidGlassClipQuality`, `roundedRectangle`). Named constructors `.roundedRectangle`, `.squircle`, `.continuousRoundedRectangle` (each `cornerRadius` 50.0, `clipQuality` `roundedRectangle`), `copyWith`.

**LiquidGlassCornerStyle**: `roundedRectangle`, `squircle`, `continuousRoundedRectangle` (default). **LiquidGlassClipQuality**: `roundedRectangle` (plain `ClipRRect`, cheapest, may not match refraction edge for squircle/continuous), `exact` (`ClipPath`, adds a save layer).

**LiquidGlassLight** (`.specular(...)`, `.glow(...)`):

| Parameter | Type | Specular default | Glow default | Meaning |
|---|---|---|---|---|
| `thickness` | `double` | 5.0 | 10.0 | Band depth in, logical px; 0 draws nothing. |
| `sharpness` | `double` | 0.5 | (none) | 0 hairline, 1 solid over first 40% of depth. |
| `angle` | `double` | 39.0 | 90.0 | Axis, degrees; 0 left/right, 90 top/bottom; absolute. |
| `spread` | `double` | 0.9 | 0.5 | Wrap around outline, 0-1. |
| `intensity` | `double` | 1.0 | 1.0 | 0 off; range 0-3. |
| `oppositeIntensity` | `double` | 1.0 | 1.0 | Far arc multiple; range 0-2. |
| `color` | `Color?` | null | null | Null = picked-up backdrop colour; set colour's alpha controls replacement. |

Also `copyWith` (cannot clear `color`) and `static lerp(a, b, t)`.

**LiquidGlassShadow** (StatelessWidget; must be a *parent* of the glass, never its child):

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `blur` | `double` | 3.5 | Blur and ring thickness. |
| `opacity` | `double` | 0.2 | |
| `color` | `Color` | `Colors.black` | |
| `offset` | `Offset?` | `blur + 2` (page says downward shift) | |
| `cornerRadius` | `double?` | null (capsule) | |
| `scale` | `Offset` | `Offset(1,1)` | Lens stretch. |
| `inset` | `double` | 0 | |
| `visible` | `bool` | true | |
| `insideGlass` | `bool` | true | False clips ring outside the outline and paints over the glass (avoids a dark band under strong refraction). |
| `child` | `Widget?` | | The glass. |

Ring is blended with `BlendMode.multiply`. On desktop (Windows/Linux/macOS) it is drawn from two blurred rrects for subpixel motion; mobile and web use the path.

### 3.5 LiquidGlassLite (StatelessWidget)

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `shape` | `LiquidGlassShape` | `const LiquidGlassShape()` | |
| `specular` | `LiquidGlassLight?` | null (= `LiquidGlassLight.specular()`) | |
| `glow` | `LiquidGlassLight?` | null (none) | Under the specular. |
| `borderAlpha` | `double` | 1.0 | Rim opacity multiplier. |
| `blur` | `double` | 3.0 | 0 = bare rim, no backdrop read. |
| `magnification` | `double` | 1.0 | Flat zoom. |
| `color` | `Color?` | null | Tint between frost and child. |
| `shapeScale` | `Offset` | `Offset(1,1)` | |
| `child` | `Widget?` | null | Null: fills constraints, draws the rim only. |

Lite cost: frost = one backdrop read; rim = a second read (two total, same as a blurred lens); with `blur: 0` and `magnification: 1` only the rim read remains; with specular intensity/thickness 0 and no glow and no blur there is no read at all (a plain tinted shape, suited to long lists). Rim colour caveat: the colour lift is a saturation-scaling matrix, so a neutral background gains no warmth as with the shader. Under lite: components keep behaviour; touch/adaptivity/scroll edge work; `LiquidGlassMorph` runs plain (no neck, `motion` ignored); `LiquidGlassBlender` does not blend (members paint separately); `LiquidGlassBatch` is inert.

### 3.6 Combining

**LiquidGlassBatch**(`key`, `required Widget child`, `bool enabled = true`); `.exclude(key, child)`; `static int? backdropIdOf(BuildContext)`. Rules: Impeller only (no-op on Skia); members must not overlap (all sample a copy taken before any painted); tab bar capsule always keeps its own read; slider/switch keep thumbs on their own reads; `LiquidGlassView` and `LiquidGlassScaffold` batch by default; sheets/dialogs are outside the tree and need `batch: true`; **Flutter 3.47 engine bug in backdrop sharing: batches render correctly but save less**.

**LiquidGlassBlender**(`key`, `required child`, `style = const LiquidGlassStyle()`, `double? smoothness = 48`, `bool? useImpellerBackdrop`, `bool useEngineBlur = true`, `bool debugClipBounds = false`); static `minLensCount` (2), `maxLensCount` (8; the class docs say 2-8 but one child-param row says 2-6, an inconsistency in the docs). `smoothness: 0` hard union; `null` = 0. Skia needs a `LiquidGlassView`. Blur above about 7 on Skia diverges from Impeller. A blender inside a batch uses the batch's read.

**LiquidGlassGroup** (deprecated): `LiquidGlassGroup({key, required child, style, smoothness (double?, null), useImpellerBackdrop, useEngineBlur (true), debugClipBounds})`; migrate to `LiquidGlassBlender(smoothness: 0, ...)`.

### 3.7 Touch and motion

**LiquidGlassTouch**: `const LiquidGlassTouch({LiquidGlassFlex? flex})`, `const LiquidGlassTouch.flexing(LiquidGlassFlex)`, `isEmpty`, `copyWith`.

**LiquidGlassFlex**:

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `stretch` | `double` | 13 | Peak elongation px. |
| `squeeze` | `double` | 0.70 | Share taken from cross axis; 1 preserves area. |
| `lean` | `double` | 0.50 | Body slide after finger, fraction of stretch. |
| `grip` | `double` | 0.70 | 0 symmetric, 1 fully local. |
| `compressInward` | `bool` | true | |
| `holdScale` | `double` | 0.030 | Signed size change while down. |
| `tapScale` | `double` | 0.020 | Tap pop; 0 disables. |
| `maxPull` | `double` | 48 | Saturation distance px. |
| `lockAxis` | `Axis?` | null | |
| `advanced` | `LiquidGlassFlexAdvanced` | `const LiquidGlassFlexAdvanced()` | |

Presets `.subtle()`, `.uniform()`, `.pronounced()`; `copyWith`. **LiquidGlassFlexAdvanced**: `childFollow` 1, `refractionBoost` 0.15, `magnificationBoost` 0, `stiffness` 320, `damping` 24, `releaseDamping` 17. **LiquidGlassFlexDeform**: required `left,right,top,bottom,childScaleX,childScaleY,childTranslateX,childTranslateY,pressAmount`; `none`; helpers `sizeFrom`, `scaleFrom`, `clipScaleFrom`. **LiquidGlassFlexOutline** (debug): `legacy`, `shaderOnly`, `full` (shipped). Behaviour: first pointer owns the lens; layout never changes.

**LiquidGlassMotionPill**: `center` (`Offset`, required), `active` (`bool`, required), `restSize`, `activeSize` (`Size`, required), `style` (`LiquidGlassStyle?`), `motion` (`LiquidGlassLensMotionSpec`), `expandStiffness` 247, `expandDamping` 18.9, `contractStiffness` 110, `contractDamping` 14.7, `cover` (`Widget?`, clipped to pill, use a plain fill), `shadow`, `honorBackdropAlpha` true, `onGlassVisibilityChanged` (`ValueChanged<bool>?`). **LiquidGlassLensMotionSpec**: `sampleWindow` 0.3, `sensitivity` 0.00007, `maxDeformation` 0.3, `responseTime` 0.18. **LiquidGlassLensMotion**: `start()`, `stop()`, `track(Offset, {now, dt})`, `isTracking`, `deviation`. **LiquidGlassDraggable**: `child` (required), `enabled` true, `initialOffset` `Offset.zero`, `onChanged` `ValueChanged<Offset>?`.

### 3.8 Adaptivity

**LiquidGlassAdaptivity**: `glassColorOnDark` `0x26000000`; `contentColorOnDark` white; `glassColorOnLight` `0x3DFFFFFF`; `contentColorOnLight` `0xFF1C1C1E`; `duration` 300 ms; `permanentBrightness` null; `initialBrightness` null; `brightnessFallback` `appTheme`; `link` null; `controller` null; `darkBelow` 0.60; `lightAbove` 0.60 (CIE L* thresholds; equal = no hysteresis); `continuousGlassColor` false. Members: `copyWith`, `kContinuousFloor`, `kContinuousCeiling`, `none`.

**LiquidGlassAdaptiveSampling**: `pixelRatio` 0.05, `frameLimit` 8, `minimumRegionSamples` 8. **LiquidGlassAdaptiveArea**: `adaptivity` (required), `child` (required), `systemChrome` `none`, `debugBounds` false; `maybeOf`. **LiquidGlassAdaptiveAreaScope**: InheritedWidget. **LiquidGlassAdaptiveContent**: `adaptivity` null, `child` null, `builder` `(BuildContext, Color, Brightness)` (wins over child). **LiquidGlassAdaptivityLink**: `ValueNotifier<Brightness?>` + `lightness` `ValueNotifier<double?>`; one publisher per link; match `duration`. **LiquidGlassAdaptivityController**(`enabled = true`), `enable()`, `disable()`, `adaptOnce()`, `refreshGeneration`; owner disposes. **LiquidGlassBrightnessFallback**: `appTheme`, `platform`. **LiquidGlassSystemChrome**: `none` (area default), `statusBar` (scaffold default), `navigationBar`, `both` (icon brightness only).

**LiquidGlassScrollEdge**: `edge` `LiquidGlassEdge.bottom` (`top`, `bottom`), `height` null (fills constraints), `color` `0x8A000000`, `adaptivity` null, `blur` 5 (0 removes the filter), `curve` `Curves.easeInOut`, `blurCurve` `defaultBlurCurve` (= `Curves.easeInQuart`), `style` `LiquidGlassScrollEdgeStyle.soft` (`soft`/`hard`, same cost), `child` null. Touches pass through except `child`. Inside a batch it counts as an overlap (page tells you to check cost).

### 3.9 Components

**LiquidGlassSlider**: `value`, `onChanged` (required), `onChangeStart`, `onChangeEnd`, `minimumValue` 0, `maximumValue` 1, `isContinuous` true, `activeColor` iOS blue `0xFF0A84FF` (API page; the guide page says "system blue"), `inactiveColor` black 8% (`0x14000000`), `thumbColor` white, `width`, `height` (shorthand for `layout`, win over it), `layout` (`LiquidGlassSliderLayout`), `minimumIcon`, `maximumIcon`, `style` (null overrides thumb glass), `motion` (`LiquidGlassLensMotionSpec`), `pixelRatio` 1.0. Self-contained: no view needed, works on both engines. **LiquidGlassSliderLayout**: `width` 280, `trackHeight` 6, `thumbWidth` 37, `thumbHeight` 24, `liftedThumbWidth` 58, `liftedThumbHeight` 38.333, `iconSize` 20, `iconGap` 8, `horizontalInset` (derived), `height` (derived); `derivedHorizontalInset`, `derivedHeight`, `resolveHorizontalInset`, `resolveHeight`, `copyWith`. Keep `horizontalInset`/`height` null or the lifted thumb clips. `onChanged` fires per pixel; do heavy work in `onChangeEnd`. `motion: LiquidGlassLensMotionSpec(maxDeformation: 0)` disables deformation; on a dark background pass a translucent white `inactiveColor`.

**LiquidGlassSwitch**: `value`, `onChanged` (required), `activeColor` iOS green `0xFF34C759`, `inactiveColor` `0x4C787880`, `thumbColor` white, `width`, `height` (size the track), `layout` (`LiquidGlassSwitchLayout`), `style`, `reserveSwellRoom` false, `pixelRatio` 1.0. Owns its own `LiquidGlassView`. Under a clipping ancestor (`ClipRRect`, `Card`, clipped scroll view) set `reserveSwellRoom: true`. Scale with `layout: const LiquidGlassSwitchLayout().scaled(1.4)`. **LiquidGlassSwitchLayout**: `width` 63, `height` 28, `padding` 2, `thumbWidth` 37, `thumbHeight` 24, `expandedThumbWidth` 58, `expandedThumbHeight` 38.333, `pinchedHeight` 22, `overshootRoom` 20; derived `bodyScale`, `minThumbCenterX`, `maxThumbCenterX`, `travel`, `padX`, `padY`, `viewWidth`, `viewHeight`; `copyWith`, `scaled`. Open issue #19 says the toggle works poorly on desktop and web.

**LiquidGlassButton**: `label`, `icon`, `onPressed` (null disables and dims), `width` null (hugs), `height` 48, `padding` (horizontal 20), `style` (default tuned iOS glass; `LiquidGlassButton.defaultStyle`), `visibility` true, `foregroundColor`, `fontSize` 16, `fontWeight` w600, `iconSize` 20, `touch`, `child` (replaces row, never sizes the button). Adaptivity overrides `foregroundColor` on adaptive surfaces; pin with `adaptivity: LiquidGlassAdaptivity.none`.

**LiquidGlassFab**: `icon`, `child`, `onPressed`, `size` 56, `padding` all 16, `style`, `visibility` true (animates), `foregroundColor`, `iconSize` 24, `heroTag`, `tooltip`. `.extended`: required `label` (Widget), `icon`, `onPressed`, `height` 48, `width`, `padding` (h20, v12), `iconSize` 20, same others. `LiquidGlassFloatingActionButton` typedef. `defaultStyle`.

**LiquidGlassAppBar**: `leading`, `title`, `actions` (`[]`), `centerTitle` true, `height` 56, `width` 360 (null hugs), `horizontalPadding` 14, `actionSpacing` 8, `style`, `visibility` true, `foregroundColor`, `fontSize` 18, `touch`. Needs a per-frame capture on Skia (keep `realTimeCapture: true`).

**LiquidGlassTabBar**: `items` (`List<LiquidGlassTabBarItem>`), `selectedIndex`, `onChanged` (required), `itemStyle` (`LiquidGlassTabItemStyle`), `pillStyle` (`LiquidGlassTabPillStyle`), `style`, `visibility` true, `width` 300 (guide page says 320; API page says 300, use the API value), `height` 64, `margin` `EdgeInsets.only(bottom: 24)` (+ safe area), `itemPadding` 6, `alignment` `Alignment.bottomCenter`. Methods `resolveGlassPill`, `buildGlassPillBar`, `resolveBarPosition`. **LiquidGlassTabBarItem**: `icon`, `selectedIcon`, `label`, `iconBuilder` (`LiquidGlassGlyphBuilder`), `labelBuilder`. **LiquidGlassTabBarAction**: `icon`, `onTap`, `foregroundColor`, `size` 56, `style`, `visibility`, `child`, `touch`. **LiquidGlassTabItemStyle**: `selectedColor` white, `unselectedColor` white70, `iconSize` 24, `underGlassIconSize`, `labelFontSize` 10.5, `underGlassLabelFontSize`, `iconLabelGap` 2, `selectedFontWeight` w600, `unselectedFontWeight` w500. **LiquidGlassTabPillStyle**: `mode` `both`, `show` true, `color` `0x2EAEAEB2`, `animated` false, `animationDuration` 320 ms, `animationCurve` `Curves.easeOutCubic`, `blur` 0, `growHeight` 9, `refractionIndex` 1.025, `refractionWidth` 12, `magnification` 1, `shape`, `glassStyle`, `rest`, `travelStiffness` 280, `travelDamping` 31.4, `motion` (maxDeformation 0.12), `magnifierPill`. **LiquidGlassTabMagnifierPillStyle**: `enabled` true, `magnification` 0.87 (Impeller only). **LiquidGlassPillMode**: `none` (single lens + optional sliding highlight, works everywhere), `impellerOnly`, `both`. **LiquidGlassGlyph**: `color`, `size`, `selected`, `underGlass`. **LiquidGlassLabel**: `color`, `fontSize`, `fontWeight`, `text`, `selected`, `underGlass`, `textStyle`.

**LiquidGlassScaffold**: `body` (required), `appBar`, `bottomNavigationBar`, `bottomNavigationBarAction`, `floatingActionButton`, `floatingActionButtonAlignment` `AlignmentDirectional.bottomEnd`, `floatingActionButtonClearance` (measured for the tab bar else `kFallbackNavClearance`), `lenses` `[]`, `backgroundColor`, `safeArea` true, `appBarTopMargin` 0, `bottomNavigationBarActionMargin` 16, `floatingActionButtonMargin` 16, `controller`, `pixelRatio` 1.0, `realTimeCapture` true, `useSync` true, `refreshRate`, `useImpellerBackdrop`, `batch` true, `adaptivity` (`LiquidGlassScaffoldAdaptivity?`). Z-order bottom to top: lenses, appBar, bottomNavigationBar, bottomNavigationBarAction, floatingActionButton. No dialog slot. **LiquidGlassScaffoldAdaptivity**: `adaptivity` (required), `systemChrome` `statusBar`, `topFollowLink`, `bottomFollowLink`, `sampling` (pixelRatio 0.05, 8/s), `topHeight`, `bottomHeight`, `debugBounds`.

**LiquidGlassSheet**: `child` (required), `header`, `anchor` `floating`, `grabber` true, `grabberColor`, `grabberSize` 38x5, `margin`, `padding` `fromLTRB(20,4,20,20)`, `style`, `visibility` true, `foregroundColor`, `safeArea` true, `touch`; statics `defaultStyle`, `defaultShape()`, `defaultMargin(...)`. **showLiquidGlassSheet**: forwards `showModalBottomSheet` params (`barrierColor`, `barrierLabel`, `isScrollControlled` false, `scrollControlDisabledMaxHeightRatio` 9/16, `useRootNavigator` false, `isDismissible` true, `enableDrag` true, `useSafeArea` false, `constraints`, `routeSettings`, `transitionAnimationController`, `anchorPoint`) plus `style`, `anchor`, `header`, `grabber`, `grabberColor`, `margin`, `padding`, `foregroundColor`, `avoidKeyboard` true (needs `isScrollControlled: true`), `touch`, `batch` false. **LiquidGlassSheetAnchor**: `floating` (inset card), `attached` (full width, top corners only).

**LiquidGlassDialog**: `child` (required), `width` 340, `padding` all 24, `style`, `visibility` true, `alignment` center. **LiquidGlassAlertDialog**: `icon`, `iconColor`, `iconPadding` (bottom 16), `title`, `titlePadding` (bottom 12), `titleTextStyle`, `content`, `contentPadding` (bottom 24), `contentTextStyle`, `actions`, `actionsPadding` zero, `actionsAlignment` end, `actionsOverflowAlignment` end, `actionsOverflowDirection` down, `actionsOverflowButtonSpacing`, `style`, `width` 340, `scrollable` false, `visibility` true. **showLiquidGlassDialog**: `context`, `builder` (required), `barrierDismissible` true, `barrierColor` `0x80000000`, `barrierLabel`, `useSafeArea` true, `useRootNavigator` true, `routeSettings`, `anchorPoint`, `transitionDuration` 350 ms, `transitionCurve`, `reverseTransitionCurve`, `batch` false (Impeller only; joins the scaffold's chrome batch; then the dialog refracts the page as it lay under the bar, no tab-bar glass, no barrier scrim, and its glass buttons read the page, not the dialog, unless wrapped in `LiquidGlassBatch.exclude`).

**LiquidGlassMorph**: `child`, `width`, `height` (pin an axis), `alignment` center, `motion` `fluid`, `style` `LiquidGlassStyle()`, `smoothness` 40, `onEnd`, `debugClipBounds` false, static `alignmentFor(rect, field)`. **LiquidGlassMorphMotion**: `stiffness` 195, `damping` 19.5, `stretch` 0.6, `anchor` center, `blended` true, `advanced`; `.spring(duration 450 ms, bounce 0.3, ...)`; presets `fluid` (default), `anchoredPop`, `droplet`, `calm`, `plain` (single lens, one backdrop pass); `zeta`, `copyWith`. **LiquidGlassMorphAdvanced**: `contentOutEnd` 0.40, `contentInStart` 0.30, `contentInEnd` 0.80, `linger` 0.12 s, `followDelay` 0.04 s, `leadBounce` 0.10, `seedScale` 1.0, `drainSpeed` 1.5, `drainInward` 0.35, `sourceFollows` false, `neckRamp` 24, `newScaleFrom` 0.90, `oldScaleTo` 0.92, `contentBlur` 8, `contentFollow` 0, `contentSlide` 0, `thickenBlur` 0.35, `thickenRefraction` 0.30.

### 3.10 Shape helpers (free functions; rarely called directly)

`liquidGlassClip({required LiquidGlassShape shape, required Widget child, Offset shapeScale = const Offset(1,1)})`, `liquidGlassClipCornerRadius(shape)`, `liquidGlassCornerStyle(shape)` (returns shader selector 0.0 circular, 1.0 squircle, 2.0 continuous), `liquidGlassUsesRoundedClip(shape)` (always true), `liquidGlassCornerSegments(double reach)` (0.05 px error budget, floor 3, cap 40), `liquidGlassSquirclePath(Size size, double r, double smoothing, {int? seg, double renderScale = 1.0})`, `liquidGlassContinuousRoundedRectPath(Size size, double r, {int? seg, double renderScale = 1.0})`.

---

## 4. Components: minimal examples

Verbatim = returned verbatim by the fetch. Imports and surrounding `build` omitted.

**Lens** (reconstructed from the page's prose; the page's example uses squircle radius 44, index 1.02, width 34, and the "Basic lens" is a lens in a sized box over a `Stack`):

```dart
Stack(children: [
  myPaintedContent,
  Positioned(
    left: 24, top: 120,
    child: SizedBox(
      width: 220, height: 96,
      child: LiquidGlassLens(
        style: const LiquidGlassStyle(
          shape: LiquidGlassShape.squircle(cornerRadius: 44),
          refractionIndex: 1.02,
          refractionWidth: 34,
        ),
        child: const Center(child: Text('Glass')),
      ),
    ),
  ),
])
```

**Slider** (verbatim):

```dart
LiquidGlassSlider(
  value: volume,
  onChanged: (v) => setState(() => volume = v),
  onChangeEnd: (v) => savePreference(v),
  activeColor: Colors.white,
  width: 280,
)
```

**Switch** (verbatim):

```dart
LiquidGlassSwitch(
  value: wifi,
  onChanged: (v) => setState(() => wifi = v),
  activeColor: const Color(0xFF0A84FF),
  width: 64,
  height: 28,
)
```

**Button** (verbatim):

```dart
LiquidGlassButton(
  label: 'Continue',
  icon: Icons.arrow_forward_rounded,
  width: 220,
  touch: const LiquidGlassTouch(flex: LiquidGlassFlex.subtle()),
  onPressed: () => next(),
)
```

**FAB** (verbatim):

```dart
LiquidGlassFab(
  icon: Icons.add_rounded,
  onPressed: () => compose(),
)

LiquidGlassFab.extended(
  icon: Icons.edit_rounded,
  label: const Text('Compose'),
  onPressed: () => compose(),
)
```

**App bar** (verbatim):

```dart
LiquidGlassView(
  backgroundWidget: myScrollingPage,   // what the bar refracts
  child: Align(
    alignment: Alignment.topCenter,
    child: LiquidGlassAppBar(
      leading: const Icon(Icons.menu_rounded),
      title: const Text('Library'),
      actions: const [Icon(Icons.search_rounded)],
      width: 356,
    ),
  ),
)
```

**Tab bar** (verbatim, plus standalone and `none` mode):

```dart
LiquidGlassTabBar(
  items: const [
    LiquidGlassTabBarItem(
      icon: Icons.today_outlined,
      selectedIcon: Icons.today_rounded,
      label: 'Today',
    ),
    LiquidGlassTabBarItem(icon: Icons.bar_chart_rounded, label: 'Charts'),
  ],
  selectedIndex: index,
  onChanged: (i) => setState(() => index = i),
  width: 330,
)

Stack(
  children: [
    MyPage(),
    LiquidGlassTabBar(
      items: items,
      selectedIndex: index,
      onChanged: (i) => setState(() => index = i),
    ),
  ],
)

LiquidGlassTabBar(
  items: items,
  selectedIndex: index,
  onChanged: (i) => setState(() => index = i),
  pillStyle: const LiquidGlassTabPillStyle(
    mode: LiquidGlassPillMode.none,
  ),
)
```

**Scaffold** (reconstructed; the page example passes `body` with an `IndexedStack`, `appBar`, `bottomNavigationBar`). Keep tab state by switching the `IndexedStack` index, not by swapping `body`:

```dart
LiquidGlassScaffold(
  body: IndexedStack(index: index, children: pages),
  appBar: const LiquidGlassAppBar(title: Text('Library')),
  bottomNavigationBar: LiquidGlassTabBar(
    items: items,
    selectedIndex: index,
    onChanged: (i) => setState(() => index = i),
  ),
)
```

**Scroll edge** (verbatim):

```dart
Stack(
  children: [
    myScrollingList,
    Align(
      alignment: Alignment.bottomCenter,
      child: LiquidGlassScrollEdge(
        edge: LiquidGlassEdge.bottom,
        height: 120,
      ),
    ),
    myFloatingTabBar,
  ],
)
```

**Sheet** (reconstructed; params from the table, call shape follows `showModalBottomSheet`):

```dart
showLiquidGlassSheet(
  context: context,
  builder: (context) => const Text('Sheet content'),
)
```

**Dialog** (verbatim):

```dart
showLiquidGlassDialog(
  context: context,
  builder: (context) => LiquidGlassAlertDialog(
    icon: const Icon(Icons.delete_outline_rounded),
    title: const Text('Delete this album?'),
    content: const Text('The photos in it stay in your library.'),
    actions: [
      LiquidGlassButton(label: 'Cancel', onPressed: () => pop(context)),
      LiquidGlassButton(label: 'Delete', onPressed: () => confirm(context)),
    ],
  ),
);
```

**Draggable** (verbatim):

```dart
LiquidGlassDraggable(
  enabled: true,
  initialOffset: Offset.zero,
  onChanged: (o) => setState(() => offset = o),
  child: const SizedBox.square(
    dimension: 190,
    child: LiquidGlassLens(
      style: LiquidGlassStyle(
        shape: LiquidGlassShape.squircle(cornerRadius: 56),
      ),
    ),
  ),
)
```

**Shadow** (verbatim):

```dart
LiquidGlassLens(
  style: const LiquidGlassStyle(
    shadow: LiquidGlassShadow(blur: 3.5, opacity: 0.2),
  ),
  child: myContent,
)

// standalone: shadow is the PARENT of the glass
LiquidGlassShadow(
  child: LiquidGlassLens(style: myStyle, child: myContent),
)

LiquidGlassShadow(
  blur: 4,
  opacity: 0.25,
  insideGlass: false,
)
```

**Motion** (verbatim):

```dart
LiquidGlassMotionPill(
  center: Offset(edge + travel * fraction, box.height / 2),
  active: dragging,       // lifted: expanded, and tracking itself
  restSize: const Size(76, 44),
  activeSize: const Size(112, 62),
  motion: const LiquidGlassLensMotionSpec(),
  cover: myRestPill,      // fades out as the glass comes up
)
```

**Touch** (verbatim; gesture placement matters):

```dart
LiquidGlassLens(
  touch: const LiquidGlassTouch(
    flex: LiquidGlassFlex(),
  ),
  style: const LiquidGlassStyle(
    shape: LiquidGlassShape.continuousRoundedRectangle(cornerRadius: 26),
  ),
  child: myContent,
)

// WRONG: ripple never shows (the lens paints over the Material the splash lands on)
InkWell(
  onTap: doThing,
  child: LiquidGlassLens(touch: myTouch, child: myRow),
)

// RIGHT: inside the lens
LiquidGlassLens(
  touch: myTouch,
  child: InkWell(onTap: doThing, child: myRow),
)
```

**Blender** (verbatim, group form):

```dart
LiquidGlassBlender(
  smoothness: 0,   // one shader, one read, no bridge — the group
  style: const LiquidGlassStyle(
    shape: LiquidGlassShape.continuousRoundedRectangle(cornerRadius: 28),
  ),
  child: Row(
    children: const [
      SizedBox(width: 96, height: 96, child: LiquidGlassLens()),
      SizedBox(width: 96, height: 96, child: LiquidGlassLens()),
      SizedBox(width: 96, height: 96, child: LiquidGlassLens()),
    ],
  ),
)
```

**Capture once, and precompile shaders** (verbatim):

```dart
final controller = LiquidGlassViewController();

LiquidGlassView(
  controller: controller,
  backgroundWidget: const MyBackground(),
  realTimeCapture: false,
  child: const MyGlassUI(),
);

// Re-snapshot by hand after the background actually changes:
await controller.captureOnce();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Every program at once: the lens's, and the blender's.
  await LiquidGlassShaders.ensureLoaded();
  runApp(const MyApp());
}
```

**Morph** (reconstructed; the page example uses `alignment`, `motion`, and a `child` keyed by an `open` flag):

```dart
LiquidGlassMorph(
  alignment: Alignment.bottomRight,
  motion: LiquidGlassMorphMotion.fluid,
  child: open
      ? const SizedBox(key: ValueKey('menu'), width: 220, height: 160, child: MenuBody())
      : const SizedBox(key: ValueKey('icon'), width: 56, height: 56, child: Icon(Icons.menu_rounded)),
)
```

---

## 5. Engines and performance

**Engines.**

| Engine / platform | Behaviour | Rating (package docs) |
|---|---|---|
| Impeller (iOS, Android, desktop since Flutter 3.47) | Lenses read the live backdrop (`ImageFilter` shader path). No view, no capture. One shader pass per lens, or per batch. | Best |
| Skia, desktop | Capture via `LiquidGlassView`. | Strongest Skia |
| Skia, mobile | Capture; affordable for a few lenses; lower `pixelRatio`; `realTimeCapture: false` for static backgrounds. | Good |
| Skia/web | Always Skia on web (CanvasKit and HTML lack the backdrop shader). Capture crosses the WebGL path every frame. | Weakest; lite glass first |

Order of operations differs: Impeller blurs first then runs the shader (soft edge); Skia runs the shader on the sharp capture then blurs the interior (crisper rim). Impeller is the reference look. Skia-only options: `pixelRatio`, `realTimeCapture`, `useSync`, `refreshRate`, `regionCapture` (sync path only).

**Lens budget and what costs what.**
- A plain lens: one backdrop read + one shader; two passes on Impeller once `blur > 0`. A blurred lite lens also costs two reads (frost + rim).
- `dispersion != 0` adds a backdrop read per pixel in the band. Default 0.
- Batch: one read per batch instead of one per lens (the page's example: twenty cards = twenty reads per frame, forty with blur). No member limit, but no overlap. Impeller only.
- Blender: 2-8 members (cap 8 because the metaball field compares every member per fragment).
- Morph is a heavier shader than a lens; `plain` uses one lens and one pass. Blur and refraction grow with the glass, so large surfaces cost more.
- Tab bar pill: `both` on Skia with shader glass captures the page a second time (expensive); use `impellerOnly`. On Impeller it is "efficient enough to use freely".
- Free by construction: `touch: null` (no listener, no ticker), `visibility: false` (no backdrop cost, child removed), adaptivity (samples only while adaptive elements are visible, at most 8/s, 0.05 pixel ratio, none when no frames draw), lite glass (no shader, no capture, no lens slot).
- Impeller docs give no numeric lens budget beyond "twenty cards" and the 8-member blender cap. **No frame-time or Android device benchmarks are published** (unverified; the only timing numbers are shader *compile* times on Windows).

**Efficiency rules (efficiency.html), condensed.**
1. Choose the cheapest piece: lite glass, then lens, then scaffold (bar + page + tabs), then batch for many lenses, blender `smoothness: 0` for one panel of up to 8, blend for merging, morph for resizing, slider/switch are self-contained.
2. On Skia, ask whether the background moves: static -> `realTimeCapture: false` plus `captureOnce()`; scrolling/video/animated -> live.
3. One `LiquidGlassView` per screen, not per widget. `pixelRatio` 0.7 is usually invisible and cheaper (1.0 recommended max).
4. Avoid blur on Skia; above about 7 it stops resembling a real backdrop blur.
5. Keep `dispersion` 0 unless visible.
6. Use the morph pill selectively (`impellerOnly`); with lite glass `both` costs nothing on Skia.
7. Do not overlap lenses in a batch; use `LiquidGlassBatch.exclude`.
8. Avoid glass over glass and glass inside scrollables (float glass over the scroll instead).
9. Call `LiquidGlassShaders.ensureLoaded()` in `main()` (lens shows the frosted fallback until its program compiles).
10. Recommended view settings: general `useSync: true`, `pixelRatio` 0.8-1.0; performance-first `useSync: false`, `pixelRatio` 0.5-0.7; static background `realTimeCapture: false` (largest gain).

**"Mistakes that look like bugs".** Flat frosted blur with no bend = Skia/web without a view, or lite glass, or shader still compiling; outdated page in glass = `realTimeCapture: false` without `captureOnce()`; two batched lenses look like one = overlap; **lens in a list turns black when pulled past its end on Impeller (Android stretch overscroll renders the list into its own texture without the page behind it)**; morph does not animate = children lack keys.

**Android specifically.**
- Impeller is the Android default, so the package's best path applies; the app runs Impeller on Android (per brief; the 2026-09-25 QA record says the app does not disable Impeller in `AndroidManifest`).
- Documented Android costs/hazards: stretch overscroll turning lenses black (fix: disable overscroll behaviour on scrollables containing lenses); OpenGL ES backends flip the texture Y axis (3.2.2 fixed a vertically mirrored refraction on Android emulators and engines after 3.44.0); Flutter 3.47 backdrop-sharing engine bug reduces batch savings (4.3.2); the lite rim is robust through route transitions and overscroll because it reads nothing from the backdrop.
- Not documented: mid-range GPU frame times, thermal behaviour, memory of the backdrop copies, behaviour of the shader on Mali/Adreno in Vulkan vs GLES. Mark all unverified.
- Prior in-repo evidence on this same class of effect (our own `kit_glass.frag` used `ImageFilter.shader` on a `BackdropFilterLayer`): the emulator's SwiftShader renderer segfaulted (QEMU exit 139) on bright light backdrops (docs/qa/slice-fix-glass-light-2026-09-29). Any package that uses backdrop shader filters can hit the same software-renderer fault; it was only an emulator, but the project's rule is that emulator proof gates every build.

---

## 6. Fit for THIS app

Inputs read: `/home/owner/Storage/Code/oc_app/AGENTS.md`; `docs/ux-system/kit-v2.md` in the worktree (note at top: "liquid and frosted glass are removed ... `KitGlass` keeps its name as that thin solid part"; §9 Kit only; G2/G16 ratchets); `lib/ui/kit/` (about 150 parts, `glass/kit_glass.dart`, `kit_nav.dart`, `kit_sheet*.dart`, `kit_dialog.dart`, `kit_buttons.dart`, `kit_floating_action.dart`, `kit_segmented.dart`, `kit_row_parts.dart`); `docs/design/design-standard.md` §floating surface; `docs/design/visual-language-2026-09-26.md` §6; `docs/qa/liquid-glass-2026-09-25/README.md`; `docs/qa/slice-fix-glass-light-2026-09-29/README.md`; git log. The worktree branch is checked out at `1faf73d32`.

### 6.1 Prior glass work in this repo (so the owner decision is read in context)

| Date | Commit(s) | What |
|---|---|---|
| 2026-09-25 | `ds/liquid-glass`, `docs/qa/liquid-glass-2026-09-25` | First `KitGlass` with custom `shaders/kit_glass.frag` on `ImageFilter.shader`; **explicitly "No package needed"** after a feasibility pass on the pinned engine. Looks: liquid / frosted / solid. |
| 2026-09-26/28 | `2f96633`, `e74d28e`, `138f821`, `6ae40ca` ... | Crisp rim, ambient fields, fluid glass. |
| ~2026-09 | `8b875722d` | Tried **`liquid_glass_widgets` 1.8.1** (a different package) as an optional engine behind `KitGlass`, sheets, dialogs, request cards. |
| 2026-09-29 | `feffd8bb4`, `03c7bf4e6`, `93f5c7e06` | Light-mode crash on emulator (SwiftShader segfault), `KitGlassSafety` fallback, strikes only on real crashes. |
| 2026-10-02 | `e044453ae`, `fa13c6af0` then `fb71dc0b3`, `4225ec3bd` | Physical Snell-refraction shader; then **owner removed liquid and frosted glass for good**; `KitGlass` is now an opaque `surface2` + hairline + one tight shadow; shader, filter, crash guard, Effects glass toggle deleted. Memory rule "No glass": owner 2026-10-02, glass branches abandoned. |

So the question is: the owner removed glass after several iterations that had real costs (an emulator crash, a safety subsystem, an Effects toggle, 100+ golden regenerations). A package does not change the *product* reasons; it only changes the build cost. Treat adoption as reopening a closed owner decision, not as a technical swap.

### 6.2 Is it compatible with Flutter 3.47.1 and the pubspec's Dart constraint?

- App `pubspec.yaml`: `environment: sdk: ^3.10.0`. Package: `sdk: ">=3.6.2 <4.0.0"`, `flutter: ">=3.3.0"`. The Dart constraint is **compatible** (3.10 satisfies >=3.6.2).
- Flutter 3.47.1: no resolver blocker (`>=3.3.0`). Behavioural compatibility is **likely but unverified**: the package's own changelog targets Flutter 3.47 (4.3.2 added a 3.47 fix; 4.3.3 measured on 3.47.4; the docs say "Impeller ... on desktop since Flutter 3.47" and mention a 3.47 backdrop-sharing engine bug). The shorebird-pinned engine is 3.47.1, a patch release earlier than 3.47.4; the app's own 2026-09-25 notes show `ImageFilter.shader` and `BackdropKey`/`BackdropGroup` work on this engine. I did not run `flutter pub get` or any build (per the brief), so resolution and compile are unverified.
- `flutter_lints ^5.0.0` is a dev dependency of the package only; the app uses `^6.0.0`. Dev dependencies of dependencies are not resolved, so no conflict.

### 6.3 Does it add dependencies?

Runtime: `flutter` (already), and **`meta ^1.9.0`**. `meta` is almost certainly already present transitively (Flutter itself depends on it); unverified against `pubspec.lock`. So effectively **no new third-party runtime dependency**. It does add **8 fragment shader assets** to the bundle (compiled by `impellerc` for the target at build) and roughly a large `lib/src` Dart surface (size unmeasured; unverified). The app currently declares no `flutter: shaders:` block of its own (grep on the worktree pubspec found none; the old `shaders/kit_glass.frag` was deleted with glass).

### 6.4 Does it work with Shorebird?

- Shaders and other `flutter: shaders:` entries are build-time assets. Shorebird patches ship only Dart code (AOT snapshot diffs); they cannot add or change assets. This matches AGENTS.md ("native/asset/pubspec changes need a new release") and the prior glass-shader work.
- Therefore: adding the package (pubspec change + shader assets) **requires a new Shorebird release**; it cannot be delivered as a patch. After that release, Dart-only tuning of styles can ship as patches **only if** it does not reference a shader not in the released bundle (e.g. enabling a component that needs `metaball_glass.frag` is fine only if that program was in the release; since the package declares all eight, they all ship with the release).
- Not verified here: whether Shorebird's current patch tooling flags a changed `FragmentProgram` asset set as incompatible (expected: it ignores asset diffs and can crash if code expects a missing asset; this is a general Shorebird caveat, unverified for the pinned CLI 1.6.x).
- Also note `LiquidGlassShaders.ensureLoaded()` awaited in `main()` adds startup work; `lib/main.dart` is a single-owner file (AGENTS.md), so that edit needs the owner slot.

### 6.5 What wrapping it as kit parts would look like

Rule: UI outside `lib/ui/kit/` may not import package widgets; G16's allowlist is framework widgets only, so `LiquidGlass*` widgets would be **imported only inside `lib/ui/kit/`**, behind kit parts, and `test/kit_ratchet_test.dart` (framework widget names) would not see them outside the kit. A new G-gate (a lint-style test that `package:liquid_glass_easy` is imported only under `lib/ui/kit/glass/`) is advisable so the dependency stays swappable.

Structure (mirrors how `KitGlass` already isolates its engine in `lib/ui/kit/glass/`):

- `lib/ui/kit/glass/kit_glass_engine.dart`: the only importer of the package; `KitGlassEngine.init()` (called from `main`), a feature/availability probe (Impeller + shader loaded + accessibility/effects flags), and a mapping `KitTokens` -> `LiquidGlassStyle` (radius from `KitTokens.navRadius`, hairline colours as specular colour, no glow, shadow from `surfaceShadows`).
- Each kit part keeps its public API and picks the solid body when glass is off or unavailable (preserve today's solid fallback: opaque `surface2`).

Candidate mapping:

| Existing kit part | Package component | Notes |
|---|---|---|
| `KitGlass` (dock, rail, top controls, composer shell) | `LiquidGlassLens` with `style` from tokens; batch via the screen's view | Today a thin solid surface. The pair (`KitGlass.pair`, server pill + search) could be `LiquidGlassBlender` with `smoothness` (the joined/split behaviour is exactly a metaball use). |
| `KitNav` / `KitNavBar` (bottom dock with lens pill) | `LiquidGlassTabBar` or `LiquidGlassScaffold.bottomNavigationBar` | Kit already has its own `_LensPainter`/spring lens pill (kit_nav.dart ~1040); package pill would replace it. Package forces its own scaffold/view for the best path; the app's shell is `KitNav`, so use standalone tab bar with `pillStyle.mode: none` first. |
| `KitNavRail` (tablet/PC) | none (package has no rail) | stays solid. |
| `KitFloatingAction`, `KitJumpPill` | `LiquidGlassFab` / `LiquidGlassFab.extended`, `LiquidGlassButton` | `KitJumpPill` is documented "never glass" in source comments; would need that spec changed. |
| `KitButton` | `LiquidGlassButton` | Only a floating/primary variant; list/dialog buttons stay solid. |
| `KitSwitchRow` (the switch inside) | `LiquidGlassSwitch` | Self-contained; the visual is iOS-26-like; check against kit switch spec and a11y labelling (named switch rule). |
| `KitSegmented`, level meters | `LiquidGlassSlider` for a slider (no kit slider found by name; unverified) | No kit slider seen in `lib/ui/kit` listing; a new `KitSlider` would be added if wanted. |
| `KitSheet` / `kit_sheet_modal.dart` | `showLiquidGlassSheet` / `LiquidGlassSheet` | Design standard says "never a sheet" for glass; sheets keep solid bodies. |
| `KitDialog` / `_KitAlert` | `showLiquidGlassDialog` / `LiquidGlassAlertDialog` | Same objection; dialogs hold text and a destructive confirmation. |
| `KitMenu` | `LiquidGlassMorph` (menu from icon) | Interesting but heavy; the kit menu already has its own motion rules (MOT-5, `KitMotion`). |
| Scroll fade under floating chrome (`KitBottomInset`, composer area) | `LiquidGlassScrollEdge` | Genuinely useful and cheap (one `BackdropFilter`); could be kit-wrapped alone as `KitScrollEdge` without refraction. |

Non-mappable or conflicting kit rules: kit timings come from `KitMotion` (literal `Duration(milliseconds:` is ratcheted out of `lib/ui`); package springs and 300-350 ms durations are internal. `KitHaptics` is the only haptics; package components do not vibrate (no mention). `AnimatedSize` banned outside kit. Every package component needs a `KitEffects`/accessibility gate (high contrast, accessible navigation, remove animations -> solid), which the package does **not** provide (it has no reduce-transparency handling; the 2026-09-25 record notes Android exposes no transparency flag), so the kit part must implement it.

### 6.6 Risks

| Risk | Detail | Severity for this app |
|---|---|---|
| **Reopening a closed decision** | Owner removed glass "for good" (2026-10-02, "No glass" rule); the design standard and kit-v2 say no blur, no translucency, no shader in KitGlass. | Governance: needs an explicit owner reversal in writing; docs (design-standard §floating surface, kit-v2 header note, `KitGlass` spec) would have to change. |
| Mid-range Android performance | No published Android benchmarks. A refraction lens is a backdrop read + shader per lens per frame on Impeller; composer, dock, top controls over a scrolling chat could be 3+ lenses (batch helps; the batch must not overlap and is weakened by the Flutter 3.47 backdrop bug). The project's origin story (#87 "choppy UI") made performance the gate; `docs/qa/liquid-glass-2026-09-25` required a `gfxinfo` janky-frame comparison. | High until measured on the owner's phone class. |
| Legibility | Text over refracted live content (transcript behind the composer; code, diffs) is the worst case; visual-language §6 already required "a dimming layer, text behind glass must read as colour, never as letters". Package answers: adaptivity (tint + content colour flip; needs a sampler view), `LiquidGlassScrollEdge`, higher `color` alpha. Adaptivity samples at 0.05 ratio, 8/s: OK for bars, not for fine contrast guarantees. Contrast tests (4.5:1) in the repo (pack x light/dark) would need a glass variant; the glass's effective colour is not statically knowable. | High on chat/composer; fine on icon-only chrome. |
| Golden tests | The repo has ~100+ goldens and a golden tolerance harness (`test/golden_tolerance.dart`, `golden_harness_*`). Plain `flutter test` runs Skia in flutter_tester: `ImageFilter.isShaderFilterSupported` is false, so package lenses show the frosted fallback (Skia path without a view), not refraction; goldens would capture the frosted/Lite path only and silently differ from the device. `flutter test --enable-impeller` is the only way to render the shader in tests (per the 2026-09-25 record), a different, slower lane. Recommended: tests force `LiquidGlassEngine.liteGlassOnSkia/Impeller = true` or the kit part's solid mode, and the real glass is proved on emulator/device only. | Medium. |
| Widget tests with shaders / pending timers | `flutter_animate` is banned for pending timers; the package animates with its own tickers/springs (touch flex, motion pill, adaptivity 300 ms flip, morph, scaffold show/hide, tab pill springs) and runs an adaptive sampler on a ~8/s cadence plus post-frame capture loops. These can keep `pumpAndSettle` from settling or leave pending timers/tickers after dispose. Unverified: I did not inspect the package source for `Timer`/`Ticker` use or for dispose correctness. Mitigation: never mount package widgets in widget tests (kit part returns solid under `kIsTest`-style gate / lite), and run one dedicated Impeller test lane. `LiquidGlassShaders.ensureLoaded()` also needs a fake or guard in tests (loading `FragmentProgram` assets under `flutter test` works for compiled assets but programs are Impeller-targeted; unverified). | Medium-high if mounted in tests; low if gated. |
| Emulator fault | Our own backdrop shader crashed the SwiftShader emulator (exit 139) on bright backdrops; the project requires an emulator pass before any APK reaches the owner. A package uses the same engine path; it may or may not hit the same fault (unverified). Package changelog explicitly handled OpenGL ES (mirrored refraction, 3.2.2). | Medium. |
| API churn / bus factor | One maintainer, 5.0.0 breaking release 8 days ago, 24 releases in 10 months, 4 open issues (one on desktop/web toggle, one on SnackBar). Pin an exact version (`liquid_glass_easy: 5.0.0`, the repo already pins exact versions for many packages). | Medium. |
| Glass in scrollables / overscroll | Android stretch overscroll renders lenses black; the app's lists all use a custom scroll behaviour? (unverified); chat transcript is a long scroll. | Medium. |
| Architecture | Package scaffold/view want to own the page (`LiquidGlassScaffold`, `LiquidGlassView`); the app's shell is `KitNav`/`KitScreen` and `lib/main.dart`/`chat_screen.dart` are single-owner. Using only standalone lenses on Impeller avoids that. | Low-medium. |
| Shorebird | Needs a new release, not a patch. | Process only. |
| a11y | No transparency-reduction hook, no semantics for glass; package components' labels (switch/slider) would need semantics review (owner rule "named switch"). | Medium. |
| Localization / RTL | Scaffold action follows text direction; tab bar items are `String? label` (kit uses l10n strings; fine). Arabic app: confirm lights/arcs are not mirrored incorrectly (unverified). | Low. |

### 6.7 Where it would be tasteful (3 to 5 places), and where never

Only if the owner reverses the 2026-10-02 rule, and only on Impeller with a solid fallback.

1. **Bottom dock lens pill** (`KitNav`): the selected tab marker as a small lens over the dock, `LiquidGlassTabBar`-style pill or a single `LiquidGlassLens`. Small area, icon-only, bounded; the dock already has a "lens" concept (`_KitNavLens`).
2. **Floating "jump to latest" / scroll-to-end pill and floating action** (`KitJumpPill`, `KitFloatingAction`): one small lens over the transcript; a single control, no text paragraphs.
3. **Scroll-edge dimming under the dock/composer** (`LiquidGlassScrollEdge` alone, without refraction): the one component that helps legibility and costs one `BackdropFilter`; arguably the best value even without glass anywhere else.
4. **Switch thumb and slider thumb** (`KitSwitchRow`, a future `KitSlider`): self-contained, no page capture, tiny area.
5. **Server pill + search pair in the top controls** (`KitGlass.pair`): a `LiquidGlassBlender` merge when scrolled is a natural use of the metaball join; short labels only.

**Never** (consistent with the design standard, visual-language §6 and the legibility risk):
- Message bubbles, transcript, tool output, code blocks, diffs, terminal, logs (`KitCodeBlock`, `KitDiffView`, `KitTerminalView`, `KitLogPanel`).
- The chat **composer text field** and any surface holding a text field or paragraph text (it needs 4.5:1 contrast; previously needed a dimming layer).
- Sheets and dialog bodies, especially destructive confirmations (`KitConfirmSheet`, `_KitAlert`), request/permission cards (`KitRequestCard`) and security screens (trust, credentials, one-time code, QR).
- Rows, cards, lists, settings pages, onboarding/setup, page backgrounds, full-screen anything.
- Glass over glass, glass inside a scrollable list, and anything repeated per list item.
- Any place that must stay readable with Effects off / high contrast / accessible navigation / remove animations (these must always fall back to solid).
- Golden-tested screens without a solid test mode.

---

## 7. Open questions for the owner

1. Do you want to reverse the 2026-10-02 "no glass for good" rule at all, or only allow a non-refractive piece such as the scroll-edge dimming band? (This decides whether the rest of the document matters.)
2. If glass returns, what is the allowed scope: only items 1-5 in 6.7, and is "Impeller only, solid everywhere else, one Effects/appearance toggle" acceptable, given earlier glass needed a crash-safety subsystem?
3. Accept that adding it forces a **new Shorebird release** (not a patch) and a new APK, and does that fit the release plan (1.4.x line, 2.0.0+55 in the pubspec)?
4. What performance gate decides it: the same `gfxinfo` janky-frame comparison as `docs/qa/liquid-glass-2026-09-25` on your phone, and what is the cutoff (for example no more than one point worse)?
5. Should the kit wrapper pin an exact version and keep a single import point (`lib/ui/kit/glass/`), so we can drop the package like the last one without touching screens?
6. Test policy: OK to prove real glass only on emulator/device and keep widget/golden tests on a solid or lite mode, instead of adding an `--enable-impeller` test lane?
7. Is a package acceptable compared with the repo's earlier finding ("No package needed", own shader) and the earlier try of `liquid_glass_widgets`, or do you prefer a small in-house lens limited to the dock pill?
8. Legibility: do you want chat-adjacent chrome (composer shell, top controls) excluded outright, as proposed, even though the visual-language doc once put glass on them?
9. Should open upstream issues (#19 toggle on desktop/web, #30 tab-bar pill drag, #32 SnackBar) block a decision, given the app also targets desktop and web shells (`window_manager`, `webview`)?
