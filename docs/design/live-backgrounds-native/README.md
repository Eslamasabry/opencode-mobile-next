# Live backgrounds, built natively (E20-S28)

Owner, 2026-11-07: build the animated page backgrounds the way native Android and Compose engineers do, not as a port of
the Flutter Canvas code. This folder explains the approach and holds the pictures of each look. Orders 31–33 in
`docs/design/native-ui-rebuild` and their pixel gates do not apply to this work; the owner's instruction replaces them.

## Approach

- **One shader per look.** Each look is an AGSL program (`android.graphics.RuntimeShader`, Android 13 / API 33 and
  later), drawn as one full-screen rect with a `ShaderBrush` behind the page's ground (`drawBehind` / `graphicsLayer`).
  No per-frame Canvas drawing, no bitmaps, no particle lists on the CPU.
- **One frame clock.** The host drives every look from one shared clock (`withFrameNanos` in one place), capped at
  30 fps. It stops completely after 30 s with no touch, when the app goes to the background, when the screen is off,
  and under thermal pressure or battery saver; a touch starts it again. Per frame it sets a handful of float uniforms,
  with no allocations. Only the background's draw layer is invalidated, so the page content above it never recomposes
  because of the background.
- **Shared uniforms.** Every look reads the same uniforms, declared once in `LiveShaderLib.HEADER`:
  `uResolution` (pixels), `uDensity` (pixels per dp), `uTime` (seconds of the shared clock), `uGround`, `uAccent`, `uInk`
  (the kit colour roles: page ground, accent, body text), and `uIntensity` (Subtle .7, Normal 1, Bold 1.6, after the
  style's cap). Looks work in dp, so they look the same on every screen.
- **Theme colours only.** A look mixes the page ground towards a tint made from the accent and the text colour. It
  follows light and dark and the person's accent with no extra colour of its own.
- **Readable text first.** `liveOut()` in the shared library is the only way a look writes a colour. It caps the mix at
  20% even at Bold, so body text keeps at least 4.5:1 on every pixel of every look in light and dark (test `B-LIVE-B03`).
- **No banding, no jaggies.** `liveOut()` adds triangular dither of ±1/255 to every pixel, so long soft gradients never
  band. AGSL has no derivatives, so edges are anti-aliased with widths derived from `uDensity` (one pixel = `1/uDensity` dp).
- **Calm.** Speeds are slow (a ring takes about six seconds to cross a phone; contour lines drift over minutes). These
  are backgrounds, not screensavers.
- **Below Android 13 (minSdk 26).** No runtime shaders and never a per-frame CPU loop: the host draws a still instead
  (a still rendered from the same shader at build time, or a simple gradient). Builder A owns that choice.
- **Settings stay as they are.** Background is off by default; Intensity is Subtle, Normal or Bold; a style can cap or
  hide it; Saver hides it; reduced motion and motion Off show one still frame (the clock does not run).

## Files

| File | What it is | Owner |
|---|---|---|
| `ui/kit/.../live/LiveLook.kt` | `LiveLook` (`id`, `shader`, `uniforms(u)`) and `LiveUniforms` | A (B created it because A had not landed) |
| `ui/kit/.../live/LiveShaderLib.kt` | shared uniforms, hash, value noise, fbm, `glowTint()`, `liveOut()` (cap and dither) | A (as above) |
| `ui/kit/.../live/LiveLookRegistry.kt` | id → look; one line per look | A; B and C add lines |
| `ui/kit/.../live/looks/Live<Look>.kt` | one look each: a Kotlin raw string of AGSL | B and C |
| `ui/kit/src/desktopTest/.../live/SkiaLiveLook.kt` | compiles a look with Skia's runtime effects on desktop (Skia's shading language is the same one AGSL is) | B |
| `ui/kit/src/desktopTest/.../live/LiveLooksBTest.kt` | id mapping, compile, contrast, clock rests = still | B |
| `make-gifs.sh` | renders the stills and GIFs below | B |

The host, the clock, the fallback below API 33, the wiring in `KitLiveBackground` and the Appearance picker's live
thumbnails are builder A's.

## Builder B's looks

Pictures: a home list made of real kit conversation rows, with the look behind it at Normal. `<look>-dark.png` and
`<look>-light.png` are 1080 × 2400 (density 2.625); `<look>-dark.gif` and `<look>-light.gif` are 3 s at **four times
the real speed** (at real speed a 3 s clip barely moves, which is the point). Rendered by Skia on desktop with the
same shader text, not on a device; the coordinator's emulator run is the proof on Android.

| Look | In one line |
|---|---|
| `dotRipple` | A quiet 22 dp dot grid; two slow rings pass through it from drifting points and the dots swell and warm towards the accent as they pass. |
| `topo` | Hairline contour lines of a map, every fifth line a little stronger, over a landscape that shifts over minutes. |
| `flowThreads` | Two loose bundles of six fine silk threads in long slow waves, fanning apart and together, each with a soft halo. |
| `lightRays` | Soft shafts of accent light from above the top corner, slowly breathing and swaying, gone before the lower half. |
| `constellation` | Small twinkling stars that wander a little; hairlines join close neighbours and fade in and out, so figures form and dissolve. |
| `bokeh` | Out-of-focus lights in three depths rising very slowly, near ones larger and softer, each with a faint lens rim. |
| `halftone` | A 45° print screen of fine dots whose size follows a soft drifting cloud, like ink on newsprint. |

## Cost

Not measured on a device yet: this environment has no Android SDK or emulator. The coordinator measures each look on
the emulator at 1080 × 2400 with `adb shell dumpsys gfxinfo <package> framestats` (or FrameMetrics) with the look on,
the page idle and the clock running; the budget is under 4 ms GPU per frame and under 1 ms added UI-thread time.

What was measured here is a **relative** cost: one 1080 × 2400 frame on Skia's CPU rasteriser (`skia-python`), which
runs the same program per pixel. It ranks the looks; it is not a GPU time.

| Look | CPU raster, one 1080 × 2400 frame | Relative |
|---|---|---|
| dotRipple | 143 ms | 1.0 |
| lightRays | 200 ms | 1.4 |
| halftone | 259 ms | 1.8 |
| bokeh | 635 ms | 4.4 |
| topo | 773 ms | 5.4 |
| flowThreads | 1099 ms | 7.7 |
| constellation | 3224 ms | 22.5 |

Two looks were restructured after the first measurement: bokeh keeps each light inside its own cell, so a pixel looks
at one cell per depth instead of nine (4052 → 635 ms), and constellation only tests the 12 hairlines that can cross a
pixel's cell instead of all 36 pairs (9348 → 3224 ms). Constellation is still the heaviest and is the one to watch on
the emulator; if it misses 4 ms, the next step is a larger cell (fewer stars per pixel) or dropping the star glow.
