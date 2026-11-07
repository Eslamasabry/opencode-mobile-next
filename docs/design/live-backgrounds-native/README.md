# Live backgrounds, built natively (E20-S28)

Owner, 2026-11-07: build the animated page backgrounds the way native Android/Compose engineers build them, not as a
port of the Flutter canvas code. This folder explains the approach and holds a still of every look (dark and light)
and a short capture of each. It supersedes `docs/design/native-ui-rebuild` orders 31–33 and their pixel gates for this
work.

Three builders made the looks in parallel: builder A the shared infrastructure and six looks, builders B and C seven
looks each. They are merged onto A's infrastructure: one look contract, one shader prelude, one registry, one clock.
Each look's shader art is the one its builder made.

Kept from Flutter: the stored look ids (`oc.effectsLiveStyle`), the mood of each look, the settings (Background off by
default; Intensity Subtle .7 / Normal 1.0 / Bold 1.6; a style may cap or hide it; Saver hides it; reduced motion or
motion Off shows a still frame) and the theme colours. Nothing else: no Canvas drawing code, no per-frame CPU loops.

## How it works

Code: `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/`.

| Part | File | What it does |
|---|---|---|
| Look contract | `LiveLook.kt` | `interface LiveLook { val id: String; val shader: String /*AGSL*/; fun uniforms(u: LiveUniforms) }` |
| Shader library | `LiveShaderLib.kt` | `PRELUDE` prepended to every look; two optional helper sets, `TINT` and `LAYER` (below) |
| Frame clock | `LiveFrameClock.kt` | The single `withFrameNanos` loop: one step per display frame, rests after 30 s without touch, wakes on touch |
| Shader host | `LiveShaderLayer.kt` | One full-size `drawRect(ShaderBrush)` in its own layer at half resolution; `setFrame` sets the uniforms in draw |
| Registry | `LiveLooks.kt` | id → look, picker order, names (English and Arabic from `:ui:words`), renderer choice |
| Wiring | `KitLiveBackground.kt` | Page ground: flat, shader, still fallback, or the painted aurora (`auroraImage`) |
| Thumbnails | `KitLiveLookStill.kt` | The Appearance › Look card runs the same shader small, live only while on screen |
| Platform | `platform/AndroidLiveShader.kt`, `platform/DesktopLiveShader.kt` | `RuntimeShader` (API 33+) / Skia `RuntimeEffect` behind `KitPlatform.liveShaders` |
| Looks | `looks/*Look.kt` | One file per look: AGSL as a Kotlin raw string, plus its per-frame uniforms |

### Rendering

- Each look is an AGSL fragment shader (`android.graphics.RuntimeShader`, API 33+). The host draws it as one rect with a
  `ShaderBrush` in a `drawBehind` of a `Spacer` that fills the page ground, before the content.
- The shader runs at half resolution per side: the node is laid out at 50 % and drawn through an offscreen layer scaled
  ×2, so the GPU shades a quarter of the pixels and filters the result up. `uDensity` is render pixels per dp, so looks
  that work in dp keep their size.
- Shared uniforms (set by the host every frame, never declared by a look): `uRes`, `uTime`, `uIntensity`, `uDensity`,
  `uDark`, `uGround`, `uAccent`, `uPartner`, `uGlow`, `uInk`. Colours come from the kit roles (`KitColors.ground`,
  `accent`, `partner`, glow = accent 30 % toward `text1`, ink = `text1`), so every look follows light, dark and the
  person's accent. Looks set their own extra uniforms in `uniforms()` (mesh points, blob centres, a cell size).
- Zero heap allocation per frame on Android: one `RuntimeShader` per look, `setFloatUniform` in place, one brush.
- Every look ends through one of three outputs, each of which keeps text readable and adds a one-step triangular dither
  so soft gradients never band:
  - `liveOut(colour, fragCoord)` (prelude, A's looks): keeps every pixel within 0.20 (light) / 0.30 (dark) RGB distance
    of the ground.
  - `liveOut(fragCoord, tint, amount)` (`TINT`, B's looks): mixes the ground towards the tint by at most 20 % at Bold.
  - `lvOut(lvLayer(k, g), fragCoord)` (`LAYER`, C's looks): ground towards ink by k, then the accent by g; the luma may
    move at most 0.15, with a soft knee so peaks flatten instead of clipping.

### Helper sets

A look puts one in front of its body (`shader = LiveShaderLib.TINT + """..."""`); the host still prepends `PRELUDE`.
Their names never clash with the prelude's or each other's.

- `TINT`: `hash21`, `hash22`, `vnoise`, `fbm2`, `luma`, `isDark()`, `glowTint()` (accent lifted towards the text on
  dark grounds), `px()` (one pixel in dp for edges without derivatives) and the three-argument `liveOut`.
- `LAYER`: `LV_TAU`, `lvHash`, `lvNoise`, `lvFbm` (3 octaves), `lvLine` (1 px anti-aliased line), `lvDp`, `lvAmt`
  (intensity, a little less on light grounds), `lvLayer`, `lvKeepContrast`, `lvOut`.

### Time

- One `LiveFrameClock` per page (`rememberLiveFrameClock` in `KitLiveBackground`, provided as `LocalLiveClock`). Its
  `seconds` is snapshot state read only inside draw blocks, so a step re-records the background layer and nothing else:
  the page content never recomposes or redraws because of the background (tested).
- **No frame cap** (owner, 11-07, "30fps only?"): the clock steps on every vsync (`withFrameNanos`), so a look follows
  the display at 60, 90 or 120 Hz. Looks move by time, not by frame, so their speed is the same at every rate. One step
  is at most ¼ s, so time never jumps after a pause.
- Rests after 30 s without a touch (a non-consuming pointer listener on the page) and wakes on the next touch. While it
  rests, or no one uses it, the loop sleeps on a channel: no frame callbacks at all.
- Stops with the app: Compose's frame clock pauses when the window is not visible (app in background, screen off).
- Battery Saver and severe heat turn motion to Calm in `KitTheme` (live on Android: a power-save receiver and a thermal
  listener), and Calm, Off, reduced motion, tests and screenshots draw one still frame (`STILL_SECONDS`).

### Below Android 13

`KitPlatform.liveShaders` returns null below API 33 (`androidRunsLiveShaders`), and the page draws `LiveStill`: the
ground with two soft radial glows in the accent and partner colours, cached with `drawWithCache` and never redrawn per
frame. The painted aurora (`auroraImage`) is a bitmap and draws on every API level, as before.

### Adding a look

1. Add `looks/<Name>Look.kt`: an `object` implementing `LiveLook` whose `shader` defines `half4 main(float2 ...)` and
   ends through one of the outputs above. Use the prelude's helpers (or a helper set); never redeclare a shared
   uniform. Constant loop bounds, no derivatives, no `%` on floats (AGSL and SkSL share this subset).
2. Add one line to `LiveLooks.shaderLooks`. The picker, names and wiring pick it up.
3. `LiveBackgroundDesktopTest` compiles every offered look; check readability the way `LiveLooksBTest` /
   `LiveLooksCTest` do (they render through the host's own `setFrame`, in the real kit colours).

## The looks

All 21 stored ids are offered in Appearance › Look: the painted aurora and 20 shader looks.

Pictures in this folder: `<id>-dark.png` and `<id>-light.png` are stills made by `make-stills.sh`: the look through
the real `KitLiveBackground` (half-resolution layer, the host's uniforms, the still-frame moment, Normal intensity) on
desktop Skia, behind a home list of real kit conversation rows, at 1080 × 2400 (density 2.625), saved at 720 × 1600 in
256 colours. `<id>-dark.gif` / `<id>-light.gif` are 3 s at **four times the real speed** (at real speed a 3 s clip
barely moves, which is the point); each builder made the captures of their own looks with their own harness before
the merge (A: mock home screen; B: real kit rows, full resolution; C: mock home screen, 15 fps). The shader art in them
is the same text that is merged. None of these are device captures: the coordinator's emulator pass is the proof on
Android.

### Builder A

| Look | id | One line |
|---|---|---|
| Aurora drift | `aurora` | Two curtains of light hang in the upper page, bright lower hems fading up through faint rays, over a slow haze. |
| Painted aurora | `auroraImage` | Unchanged: Glass's painted plate, tinted to the accent, drifting on the shared clock at six steps a second. |
| Breathing mesh | `mesh` | Four soft colour points wander and blend into one smooth gradient that gently brightens and dims. |
| Silk | `silk` | Wide diagonal folds of satin with a soft sheen sliding along each fold; settles toward the message box. |
| Caustics | `caustics` | A fine net of pool light from two layers of drifting cells, brightest where the layers' lines cross. |
| Liquid blobs | `liquidBlobs` | Four soft drops of colour drift and melt into each other when they meet. |
| Glow spill | `glowSpill` | A pool of accent light spills up from the bottom edge, breathing slowly, with a fine still grain. |

### Builder B (`TINT`)

| Look | id | One line |
|---|---|---|
| Dot ripple | `dotRipple` | A quiet 22 dp dot grid; two slow rings pass through it from drifting points and the dots swell and warm towards the accent as they pass. |
| Topo | `topo` | Hairline contour lines of a map, every fifth line a little stronger, over a landscape that shifts over minutes. |
| Flow threads | `flowThreads` | Two loose bundles of six fine silk threads in long slow waves, fanning apart and together, each with a soft halo. |
| Light rays | `lightRays` | Soft shafts of accent light from above the top corner, slowly breathing and swaying, gone before the lower half. |
| Constellation | `constellation` | Small twinkling stars that wander a little; hairlines join close neighbours and fade in and out, so figures form and dissolve. |
| Bokeh | `bokeh` | Out-of-focus lights in three depths rising very slowly, near ones larger and softer, each with a faint lens rim. |
| Halftone | `halftone` | A 45° print screen of fine dots whose size follows a soft drifting cloud, like ink on newsprint. |

Readability: body text keeps at least 4.5:1 over every pixel at Bold, light and dark (`B-LIVE-B03`).

### Builder C (`LAYER`)

| Look | id | One line |
|---|---|---|
| Matrix rain | `matrixRain` | Half the columns carry small 3×5 pixel glyphs drifting down at 20–45 dp/s in the accent, a brighter head and a long fading trail; glyphs change every couple of seconds. |
| Blueprint | `blueprint` | A faint accent wash with a 12 dp grid, heavier lines every 60 dp, measurement ticks and a few registration rings, drifting ~2 dp/s under a wandering soft light. |
| Circuit | `circuit` | Board traces on a 22 dp grid that always join up: long buses with short branches, ring pads at ends, dots at junctions; accent pulses run along some lanes at ~25 dp/s. |
| Oscilloscope | `oscilloscope` | A scope graticule (48 dp divisions, ticked centre axes) with one calm accent trace across the middle, a phosphor glow and a faint afterimage; it breathes, never flickers. |
| Radar | `radar` | Range rings and a crosshair above centre; a soft beam turns once every 19 s and a few contacts light up as it passes and fade (new ones each turn). |
| Honeycomb | `honeycomb` | A fine 18 dp hexagon lattice like a carbon panel, each cell lit faintly from above; a wide soft accent band slides diagonally and lifts the edges it crosses. |
| Brushed metal | `brushedMetal` | Fine horizontal brushed grain (two stretched noise scales and a hairline speckle), lit from the top, with a broad soft highlight gliding across it carrying a hint of accent. |

Readability (`B-LIVE-C03`): secondary text (`text2`) keeps at least 4.5:1 over every pixel at Subtle, Normal and
Bold, light and dark, and no pixel strays more than 60/255 from the ground. Builder C's measurement before the merge
(lowest `text2` contrast; the plain ground gives about 7:1):

| Look | dark .7 / 1 / 1.6 | light .7 / 1 / 1.6 |
|---|---|---|
| matrixRain | 5.31 / 5.04 / 4.91 | 5.29 / 5.02 / 4.90 |
| blueprint | 6.26 / 5.62 / 5.13 | 5.99 / 5.68 / 5.20 |
| circuit | 5.74 / 5.31 / 5.04 | 5.70 / 5.34 / 5.05 |
| oscilloscope | 5.29 / 4.97 / 4.84 | 5.41 / 5.13 / 4.95 |
| radar | 5.31 / 5.04 / 4.90 | 5.44 / 5.15 / 4.97 |
| honeycomb | 5.63 / 5.21 / 4.97 | 5.55 / 5.20 / 4.96 |
| brushedMetal | 6.17 / 5.64 / 5.25 | 5.81 / 5.42 / 5.00 |

## Tests

`native/ui/kit/src/commonTest/.../live/` and `native/ui/kit/src/desktopTest/.../live/`:

| Test | Proves |
|---|---|
| `LiveFrameClockTest` | one step per display frame at 60, 90 and 120 Hz; rest after 30 s; touch wakes; held stops; no jumps |
| `LiveLooksTest` | every stored id is kept, every one is drawn and offered, each shader answers to its id, the shared-uniform contract |
| `LiveBackgroundDesktopTest` | every look compiles with the prelude; a frame per display frame redraws only the background layer; Off is one still |
| `LiveLooksBTest` | B's looks: registry, compile on prelude + `TINT`, body text 4.5:1 at Bold, deterministic and moving |
| `LiveLooksCTest` | C's looks: registry, compile on prelude + `LAYER`, own uniforms declared, `text2` 4.5:1 at every intensity, moving |
| `LiveLookStills` | renders the stills here (runs only with `LIVE_STILLS_OUT` set) |

## Performance

Budget per look at 1080 × 2400: under 4 ms GPU, under 1 ms added UI-thread time per frame. With the cap gone a look
draws up to 120 times a second on a 120 Hz screen, so the budget matters more than before: 4 ms leaves room inside an
8.3 ms frame. Not measured here: this container has no Android SDK or emulator. To measure on the emulator, at the
display's own rate:

```bash
adb shell dumpsys gfxinfo io.github.eslamasabry.opencode_mobile.native reset
# open Chats with the look on (Appearance › Background on, Look <id>), wait 10 s, touch once, wait 10 s
adb shell dumpsys gfxinfo io.github.eslamasabry.opencode_mobile.native framestats
```

Read "GPU" (`GpuCompleted - SwapBuffers` per frame) and "UI thread" (`SyncStart - IntendedVsync`). Expected cost per
frame: one quarter-resolution shader pass (~650k fragments) plus one bilinear upscale blit.

What was measured before the merge is a **relative** cost: builder B timed one full-resolution 1080 × 2400 frame on
Skia's CPU rasteriser (`skia-python`), which runs the same program per pixel. It ranks the looks; it is not a GPU time.

| Look | CPU raster, one 1080 × 2400 frame | Relative |
|---|---|---|
| dotRipple | 143 ms | 1.0 |
| lightRays | 200 ms | 1.4 |
| halftone | 259 ms | 1.8 |
| bokeh | 635 ms | 4.4 |
| topo | 773 ms | 5.4 |
| flowThreads | 1099 ms | 7.7 |
| constellation | 3224 ms | 22.5 |

Constellation is the heaviest and the one to watch on the emulator; if it misses 4 ms, the next step is a larger cell
(fewer stars per pixel) or dropping the star glow. Of builder C's looks, by construction: matrixRain, oscilloscope and
radar are straight-line maths with no loops; circuit four segment distances; honeycomb one hex lookup; blueprint one
value-noise sample; brushedMetal two noise samples and a hash (likely the heaviest of C's).
