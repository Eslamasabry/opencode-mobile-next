# Glass that shows the live background move — emulator proof, 2026-11-09

Owner: "Why is glass not interacting with the background? Animations like the matrix one or the other themes?" and
then "why is glass so frosty and not glossy?". Branch `native/glass-live`.

- **Before** = `native-preview-fast-bc46d2aea.apk` (the build on the owner's phone; glass-v3 included).
- **After** = `native/glass-live` 15a026bd3, `native-preview-pc-15a026bd3.apk` (signer 12de0107).
- Emulator OC_API35 (API 35, google_apis x86_64), **host GPU** (Intel UHD 630, `-gpu host`), 1080×2400, 60 Hz. The
  liquid-glass QA server (OpenCode 2.2.6) through `adb reverse`. Graphite, Dark, Glass, Glass edges Liquid, Normal
  strength. `adb shell screenrecord`, 8 Mbit/s.

## Why the glass did not react

1. **Panes were thickened for a guess.** Every pane over every shader look was tinted for the worst any look could draw,
   unblurred (the whole of `liveOut`'s cap): 57 % on a dark card, 40 % on a light one, over matrix rain as over aurora.
2. **The frost wiped thin marks out.** A 10–12 dp blur averages matrix rain's 2 dp glyphs to nothing, whatever the tint.
3. **The clock snapped.** After 30 s without a touch the background (and everything glass showed of it) stopped dead,
   and a touch started it at full speed.
4. **Nothing on the glass answered to what was behind it.** Rim, sheen and specular were fixed white.

The liquid lenses (bar, composer, chips, sheets) already bent the live picture: every source draws the page's one
picture recording under its content (`drawGroundUnder`), and the library re-renders through the reference. No change
needed there; their tint now follows the look too.

## What changed

- **Measured tints** (`LiveReach`, `LiveSpots.forLook`): every look rendered through the host's own shader code at Bold,
  31 packs × light/dark × 8 moments, blurred, farthest pixel from the ground + 15 %. A pane is thickened for that look
  only. Liquid faces read the look from the appearance (`kitLivePictureSpots`), so sheets and menus know it too.
- **One GlassLook spec** (`ui/design/.../style/GlassLook.kt`): every blur, tint-lift and gloss number in one place for
  the coming glass-look presets. Default: 6 dp frost, light sheen .34 → .24, specular .55/.90 → .80/1.0, glint .60 →
  .75, liquid rim .36/.55 → .46/.62.
- **Rim catches the backdrop's light**: a 1.5 dp band on the lit edges (top, start) shows the live picture as white light,
  brighter where the picture is brighter than the ground (dark themes). One reference to the shared recording per pane;
  rectangle clips, no path mask.
- **Clock eases**: slows to a stop over the last 1.5 s before the 30 s rest, gathers speed over 0.9 s after a touch.
- **Glass picture ≤ 60 steps/s** on 90/120 Hz screens (the shader layer itself still follows every frame).
- The painted aurora keeps 6 steps a second: its drift moves under a pixel a second (`AuroraImage.FRAMES_PER_SECOND`),
  so faster steps would re-blur every pane for nothing visible.

## Tint per look (Graphite, Bold, clear card pane; the bar and message box are liquid faces)

Measured reach = share of `liveOut`'s cap the look reaches after a 10 dp frost (dark / light). "Before" is the whole-cap
guess every look got. Card tints computed by `LiveReachMeasureTest` with the 10 dp rows; the shipped 6 dp frost uses the
lens rows (larger, proven safe because a Gaussian never moves a pixel further from the ground), so matrix-like looks
land a little above these numbers.

| Look | reach frost dark / light | card dark | card light | bar dark | composer dark |
|---|---|---|---|---|---|
| aurora | 1.014 / 1.025 | 57 % -> 57 % | 40 % -> 40 % | 56 % -> 56 % | 59 % -> 59 % |
| mesh | 1.013 / 1.024 | 57 % -> 57 % | 40 % -> 40 % | 56 % -> 56 % | 59 % -> 59 % |
| dotRipple | 0.109 / 0.153 | 57 % -> 16 % | 40 % -> 22 % | 56 % -> 56 % | 59 % -> 59 % |
| topo | 0.358 / 0.511 | 57 % -> 16 % | 40 % -> 22 % | 56 % -> 49 % | 59 % -> 52 % |
| flowThreads | 0.421 / 0.608 | 57 % -> 16 % | 40 % -> 24 % | 56 % -> 52 % | 59 % -> 55 % |
| glowSpill | 1.016 / 1.018 | 57 % -> 57 % | 40 % -> 40 % | 56 % -> 56 % | 59 % -> 59 % |
| lightRays | 0.354 / 0.494 | 57 % -> 16 % | 40 % -> 22 % | 56 % -> 52 % | 59 % -> 55 % |
| constellation | 0.106 / 0.159 | 57 % -> 16 % | 40 % -> 22 % | 56 % -> 56 % | 59 % -> 59 % |
| halftone | 0.344 / 0.485 | 57 % -> 16 % | 40 % -> 22 % | 56 % -> 47 % | 59 % -> 49 % |
| liquidBlobs | 1.013 / 1.018 | 57 % -> 57 % | 40 % -> 40 % | 56 % -> 56 % | 59 % -> 59 % |
| matrixRain | 0.092 / 0.121 | 57 % -> 16 % | 40 % -> 22 % | 56 % -> 47 % | 59 % -> 49 % |
| blueprint | 0.442 / 0.536 | 57 % -> 21 % | 40 % -> 22 % | 56 % -> 56 % | 59 % -> 59 % |
| circuit | 0.123 / 0.159 | 57 % -> 16 % | 40 % -> 22 % | 56 % -> 47 % | 59 % -> 49 % |
| oscilloscope | 0.588 / 0.739 | 57 % -> 37 % | 40 % -> 35 % | 56 % -> 56 % | 59 % -> 59 % |
| radar | 0.471 / 0.567 | 57 % -> 21 % | 40 % -> 24 % | 56 % -> 56 % | 59 % -> 59 % |
| honeycomb | 0.559 / 0.760 | 57 % -> 33 % | 40 % -> 35 % | 56 % -> 54 % | 59 % -> 57 % |
| brushedMetal | 0.824 / 1.146 | 57 % -> 54 % | 40 % -> 40 % | 56 % -> 56 % | 59 % -> 59 % |
| silk | 1.020 / 1.023 | 57 % -> 57 % | 40 % -> 40 % | 56 % -> 56 % | 59 % -> 59 % |
| caustics | 0.888 / 0.958 | 57 % -> 57 % | 40 % -> 40 % | 56 % -> 56 % | 59 % -> 59 % |
| bokeh | 0.440 / 0.631 | 57 % -> 21 % | 40 % -> 27 % | 56 % -> 52 % | 59 % -> 55 % |

Findings: the wash looks (aurora, mesh, silk, glow spill, liquid blobs, caustics) fill the whole cap even blurred, so
their panes stay as thick as before: that is what text1/2/3 at 4.5:1 over their brightest spots costs. The liquid bar
and message box sit at their content floor (blurred words and surfaces behind them, 47 % dark with no background at
all), so a look adds little there. And the old guess was too thin in one place: the dot, line and instrument looks do
not end in the prelude's distance cap, so under the lens on a light theme several reach past it (constellation 1.47×,
radar 1.39×); their rows say so.

Matrix rain through a frosted pane: even at 6 dp its 2 dp glyphs blur to faint green streaks; the motion shows best in
the liquid lenses and on the lit rim. That is frosted glass; a clearer preset belongs to the glass-look design.

## Videos (one phone screen each, 7–9 s)

| | Before | After |
|---|---|---|
| Matrix, home | `before-matrix-home.mp4` | `after-matrix-home.mp4` |
| Matrix, chat + composer | `before-matrix-chat.mp4` | `after-matrix-chat.mp4` |
| Matrix, model sheet | `before-matrix-sheet.mp4` | `after-matrix-sheet.mp4` |
| Aurora drift, home | `before-aurora-home.mp4` | `after-aurora-home.mp4` |
| Aurora drift, chat + composer | `before-aurora-chat.mp4` | `after-aurora-chat.mp4` |
| Aurora drift, model sheet | `before-aurora-sheet.mp4` | `after-aurora-sheet.mp4` |
| Rest at 30 s, then a touch | `before-aurora-rest-wake.mp4` | `after-aurora-rest-wake.mp4` |

Rest/wake, measured as mean frame-to-frame change of the background strip every 0.1 s: before goes 0.06 → 0.00 in one
step and 0.00 → 0.02–0.08 in one step; after slows 0.04 → 0.03 → 0.02 → 0.01 → 0.00 and gathers 0.00 → 0.01 → 0.03 →
0.04 → 0.06.

## Frame cost (`dumpsys gfxinfo`, same emulator session, before and after installed in turn)

Full lines in `frames-before.txt` and `frames-after.txt`. The emulator's GPU needs ~17 ms a frame at 1080×2400, so
read these as relative.

| Scenario | Before p50 / p90 / p99, janky | After p50 / p90 / p99, janky |
|---|---|---|
| Aurora, home idle 8 s | 21 / 22 / 27 ms, 0 % | 21 / 23 / 32 ms, 0 % |
| Aurora, home scroll | 21 / 27 / 32 ms, 0 % | 20 / 28 / 38 ms, 0.5 % |
| Aurora, chat scroll | 16 / 31 / 46 ms, 4.2 % | 16 / 31 / 53 ms, 6.1 % |
| Matrix, home idle 8 s | 21 / 22 / 25 ms, 0 % | 16 / 18 / 21 ms, 0 % |
| Matrix, home scroll | 21 / 27 / 32 ms, 0 % | 20 / 27 / 48 ms, 0.8 % |
| Matrix, chat scroll | 20 / 28 / 40 ms, 2.7 % | 20 / 29 / 48 ms, 4.3 % |

Medians and GPU medians unchanged; the tail grows a little on scroll (each pane's rim-light layer is recorded again when
the pane moves). A first version with a ring path mask cost more (chat scroll 6–10 % janky) and was replaced by
rectangle clips. No work while the clock rests: the rim light references the recording and draws nothing new.
