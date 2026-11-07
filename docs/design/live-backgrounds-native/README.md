# Live backgrounds, native (E20-S28)

Owner, 2026-11-07: build the live backgrounds the way native Android/Compose engineers do, not as a port of the
Flutter Canvas code. This folder explains the approach and holds the per-look stills and 3 s captures for review.
It supersedes `docs/design/native-ui-rebuild` orders 31–33 and their pixel gates for this work.

> Written by builder C. Builder A owns the shared infrastructure (clock, shader host, wiring, picker) and may
> replace the "Host" section with the landed details; the look contract below is the one all three builders code to.

## What is kept from Flutter

- The look ids stored in `oc.effectsLiveStyle` (Dart `KitLiveStyle`): `aurora`, `auroraImage`, `mesh`, `dotRipple`,
  `topo`, `flowThreads`, `glowSpill`, `lightRays`, `constellation`, `halftone`, `liquidBlobs`, `matrixRain`,
  `blueprint`, `circuit`, `oscilloscope`, `radar`, `honeycomb`, `brushedMetal`, `silk`, `caustics`, `bokeh`.
- The mood of each look, the settings (Background on/off, default off; Intensity Subtle .7 / Normal 1.0 / Bold 1.6;
  a style school can cap or hide it; Saver hides it; reduced motion or motion Off shows a still frame) and the theme
  colours (kit roles, so every look follows light/dark and the person's accent).

Nothing else: no Canvas drawing code, no per-frame CPU loops.

## Approach

- **One AGSL program per look**, drawn as one full-screen rect with a `ShaderBrush(RuntimeShader)` inside the page
  ground's `drawBehind`/`graphicsLayer` (Android 13+, API 33). The page content is in its own layer above and never
  recomposes because of the background: only uniforms change, inside the draw phase.
- **Contract** (`ui/kit/.../live/LiveLook.kt`):
  `interface LiveLook { val id: String; val shader: String /*AGSL*/; fun uniforms(u: LiveUniforms) }`.
  A look sets only its own knobs in `uniforms` (allocation-free, it runs every frame). One line per look in
  `LiveLooks.all`.
- **Shared AGSL library** (`LiveShaderLib.kt`): the shared uniform header
  (`uRes`, `uTime`, `uIntensity`, `uDensity`, `uDark`, `layout(color)` `uGround`/`uAccent`/`uInk`), hash and value
  noise, 3-octave fbm, 1 px anti-aliased line coverage, `lvLayer` (tint the ground towards ink and accent) and
  `lvOut`, which
  - keeps the look in the low-contrast range: it limits how far the ground's luma may move (soft knee, peaks flatten
    instead of clipping), so secondary text keeps 4.5:1 over every pixel at every intensity in dark and light;
  - adds triangular dither of one 8-bit step, so slow gradients never band.
- Looks work in dp (`lvDp`) so patterns keep their size across densities; every motion is slow (tens of dp a second
  or slower, a radar turn takes 19 s).
- **Host** (builder A): one shared frame clock (`withFrameNanos` in one place) feeding `uTime`; capped at 30 fps;
  pauses after 30 s without touch, in the background, with the screen off, under thermal throttling or battery
  saver; resumes on touch. Reduced motion / motion Off draws one still frame.
- **Below API 33** (minSdk 26): a static fallback (a still pre-rendered from the same shader at build time, or a
  simple gradient), never a per-frame CPU canvas loop.
- **Desktop twin**: the programs use only float, ES2-style maths, so the same source compiles as a Skia
  `RuntimeEffect` on desktop. Desktop tests compile and render every look; `native/tool/design/live_look_stills.py`
  (skia-python) renders the stills and captures in this folder from the Kotlin sources.

## Files here

`<builder>-<look>-<dark|light>.png` — a still at 540×1200 (half of 1080×2400, density 2.625) of a home-like screen
(title, eight chat rows, a line of secondary text straight on the background) over the look at Normal intensity.
`<builder>-<look>-<dark|light>.gif` — 3 s of the same screen at 15 fps (360 px wide). These are rendered from the
shader sources with Skia, not captured from the device; the rows are a stand-in, not the kit's real rows. The
emulator pass (real home screen, `dumpsys gfxinfo`) is still to do — see each builder's look list.

Builder C's looks: [LOOKS-C.md](LOOKS-C.md).
