# Live backgrounds, built natively (E20-S28)

Owner, 2026-11-07: build the animated page backgrounds the way native Android/Compose engineers build them, not as a
port of the Flutter canvas code. This folder explains the approach and holds the stills and short captures of builder
A's looks. It supersedes `docs/design/native-ui-rebuild` orders 31–33 and their pixel gates for this work.

Kept from Flutter: the stored look ids (`oc.effectsLiveStyle`), the mood of each look, the settings (Background off by
default; Intensity Subtle .7 / Normal 1.0 / Bold 1.6; a style may cap or hide it; Saver hides it; reduced motion or
motion Off shows a still frame) and the theme colours. Nothing else.

## How it works

Code: `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/`.

| Part | File | What it does |
|---|---|---|
| Look contract | `LiveLook.kt` | `interface LiveLook { val id: String; val shader: String /*AGSL*/; fun uniforms(u: LiveUniforms) }` |
| Shader library | `LiveShaderLib.kt` | Prelude prepended to every look: shared uniforms, hash, value noise, fbm, glow blend, `liveOut` (contrast cap + dither) |
| Frame clock | `LiveFrameClock.kt` | The single `withFrameNanos` loop: 30 fps cap, rests after 30 s without touch, wakes on touch, holds when unused |
| Shader host | `LiveShaderLayer.kt` | One full-size `drawRect(ShaderBrush)` in its own layer at half resolution; uniforms set in the draw block |
| Registry | `LiveLooks.kt` | id → look, picker order, names (English and Arabic from `:ui:words`), renderer choice |
| Wiring | `KitLiveBackground.kt` | Page ground: flat, shader, still fallback, or the painted aurora (`auroraImage`) |
| Thumbnails | `KitLiveLookStill.kt` | The picker card runs the same shader small, live only while on screen |
| Platform | `platform/AndroidLiveShader.kt`, `platform/DesktopLiveShader.kt` | `RuntimeShader` (API 33+) / Skia `RuntimeEffect` behind `KitPlatform.liveShader` |
| Looks | `looks/*.kt` | One file per look: AGSL as a Kotlin raw string, plus its per-frame uniforms |

### Rendering

- Each look is an AGSL fragment shader (`android.graphics.RuntimeShader`, API 33+). The host draws it as one rect with a
  `ShaderBrush` in a `drawBehind` of a `Spacer` that fills the page ground, before the content.
- The shader runs at half resolution per side: the node is laid out at 50 % and drawn through an offscreen layer scaled
  ×2, so the GPU shades a quarter of the pixels and filters the result up. The looks are soft, so nothing is lost.
- Uniforms: `uRes`, `uTime`, `uIntensity`, `uDensity`, `uDark`, `uGround`, `uAccent`, `uPartner`, `uGlow`; colours come
  from the kit colour roles (`KitColors.ground`, `accent`, `partner`, glow = accent 30 % toward `text1`), so every look
  follows light, dark and the person's accent. Looks set extra uniforms in `uniforms()` (mesh points, blob centres are
  computed once per frame on the CPU, not per pixel).
- Zero heap allocation per frame on Android: one `RuntimeShader` per look, `setFloatUniform` in place, one brush.
  (Desktop Skia shaders are immutable, so desktop builds a shader object per frame; desktop only.)
- `liveOut` keeps every pixel within a small distance of the ground (0.20 light / 0.30 dark in RGB length) so text keeps
  its contrast at Bold, and adds a one-step triangular dither so soft gradients never band.

### Time

- One `LiveFrameClock` per page (`rememberLiveFrameClock` in `KitLiveBackground`, provided as `LocalLiveClock`). Its
  `seconds` is snapshot state read only inside draw blocks, so a step re-records the background layer and nothing else:
  the page content never recomposes or redraws because of the background (tested).
- 30 steps a second at most, whatever the display rate. One step is at most ¼ s, so time never jumps after a pause.
- Rests after 30 s without a touch (a non-consuming pointer listener on the page) and wakes on the next touch. While it
  rests, or no one uses it, the loop sleeps on a channel: no frame callbacks at all.
- Stops with the app: Compose's frame clock pauses when the window is not visible (app in background, screen off).
- Battery Saver and severe heat turn motion to Calm in `KitTheme` (now live on Android: a power-save receiver and a
  thermal listener), and Calm, Off, reduced motion, tests and screenshots draw one still frame (`STILL_SECONDS`).

### Below Android 13

`KitPlatform.liveShader` returns null below API 33 (`androidRunsLiveShaders`), and the page draws `LiveStill`: the
ground with two soft radial glows in the accent and partner colours, cached with `drawWithCache` and never redrawn per
frame. No CPU per-frame canvas loop exists anywhere. The painted aurora (`auroraImage`) is a bitmap and draws on every
API level, as before.

### Adding a look (builders B and C)

1. Add `looks/<Name>Look.kt`: an `object` implementing `LiveLook` whose `shader` defines
   `half4 main(float2 fragCoord)` and ends with `return liveOut(colour, fragCoord);`. Use the prelude's helpers; do not
   redeclare shared uniforms. Constant loop bounds, no derivatives, no `%` on floats (AGSL and SkSL share this subset).
2. Add one line to `LiveLooks.shaderLooks`. The picker, names and wiring pick it up.
3. `LiveBackgroundDesktopTest` compiles every offered look with Skia (the same language); add the id to
   `LiveLooksTest.builderA`-style lists in your own test.

## Builder A's looks

Stills and 3 s captures in this folder: `<look>-dark.png`, `<look>-light.png`, `<look>-dark.gif`, `<look>-light.gif`.
They are rendered from the looks' real shader text (prelude + body, compiled by Skia, the same SkSL that Android runs as
AGSL) at half resolution and scaled to 1080×2400 (PNG saved at 720×1600), under a mock home screen with rows in the app's default dark and light
colours (ground #0b0c0e / #f3f3f1, accent #3ddc8a / #087f43, partner stand-in #7aa2f7 / #2f5fbf). Each GIF is 3 s at
10 fps, 240 px wide, played at 4× the real speed so the slow drift is visible in a short clip. See "How the captures were made" for what they are not.

| Look | id | One line |
|---|---|---|
| Aurora drift | `aurora` | Two curtains of light hang in the upper page, bright lower hems fading up through faint rays, over a slow haze. |
| Painted aurora | `auroraImage` | Unchanged: Glass's painted plate, tinted to the accent, drifting on the shared clock at six steps a second. |
| Breathing mesh | `mesh` | Four soft colour points wander and blend into one smooth gradient that gently brightens and dims. |
| Silk | `silk` | Wide diagonal folds of satin with a soft sheen sliding along each fold; settles toward the message box. |
| Caustics | `caustics` | A fine net of pool light from two layers of drifting cells, brightest where the layers' lines cross. |
| Liquid blobs | `liquidBlobs` | Four soft drops of colour drift and melt into each other when they meet. |
| Glow spill | `glowSpill` | A pool of accent light spills up from the bottom edge, breathing slowly, with a fine still grain. |

### How the captures were made

This container has no Android SDK or emulator, so the captures are not device screenshots. They come from
`render.py`-style tooling: Skia (skia-python, the same SkSL compiler as desktop Compose) compiles each look's shader text
read straight from the Kotlin sources and draws it with the same uniforms the host sets. The rows on top are a mock home
screen, not the app's own `KitRow`s. The emulator pass (on the coordinator's machine) replaces them with real screens.

## Performance

Budget per look at 1080×2400: under 4 ms GPU, under 1 ms added UI-thread time. Not measured here (no emulator). To
measure on the emulator:

```bash
adb shell dumpsys gfxinfo io.github.eslamasabry.opencode_mobile.native reset
# open Chats with the look on, wait 10 s, touch once, wait 10 s
adb shell dumpsys gfxinfo io.github.eslamasabry.opencode_mobile.native framestats
```

Read "GPU" (`GpuCompleted - SwapBuffers` per frame) and "UI thread" (`SyncStart - IntendedVsync`). Expected cost per
frame: one quarter-resolution shader pass (~650k fragments) plus one bilinear upscale blit, at 30 fps.
