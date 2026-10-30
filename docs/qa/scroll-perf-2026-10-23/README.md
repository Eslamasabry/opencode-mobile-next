# Scroll performance: the shell pill's words (2026-10-23)

**Finding.** Scrolling Conversations under Glass and Crystal took about 47 ms a frame on emulator-5584, while Solid took about 17 ms. The cause was the words on the shell's server pill.

**Cause.**

1. Under Glass and Crystal, a pane's words sit on its plate (hook H10, `KitTextHalo` with `allTones`). The plate is six blurred shadows per text run.
2. The page around the list is recorded again on every frame of a scroll's stretch at an end, and while the scrollbar fades.
3. The bar had no layer of its own, so the pill's words were recorded again with the page. Each time, the engine blurred every run anew.

That cost about 38 ms of raster a frame. In Solid there is no plate, so Solid was never slow.

**Fix.** `KitGlass` puts its content on a layer of its own (`RepaintBoundary` inside the pane's clip), so the words keep their picture. The frost and the lens stay on the page's layer.

## How it was measured

- Build: release APKs on emulator-5584 (host GPU, ANGLE, Impeller on OpenGL ES).
- Frame times: `frametime.sh`, 6 swipes, SurfaceFlinger `--latency`.
- CPU: per-thread CPU from `/proc/<pid>/task`.
- Probe builds also log Flutter `FrameTiming` (build and raster ms).
- Raster thread before the fix: 100% busy. simpleperf put 43% of it in the kernel and goldfish pipe, 23% in ANGLE and 9% in libflutter. So a frame's cost follows the GPU commands it sends.

## Throwaway probe 2803 (branch `perf/probe`, not merged)

Each switch was read from a file at launch. The emulator prefs were Glass: `effectsSurfaceStyle=glass`, although earlier runs called this "Solid". Raw lines are in `probe-toggles.txt`.

| Switch | Frame mean | Raster/frame |
|---|---|---|
| none | 46.7 ms | 46.7 ms |
| bar removed | 16.8 | 6.7 |
| pill removed | 17.0 | 9.0 |
| pill words replaced by an empty box | 16.7 | 8.8 |
| pill without its glass pane | 16.8 | 8.0 |
| plate off (`nohalo`) | 16.7 | 8.1 |
| RepaintBoundary around the bar | 17.1 | 10.6 |
| round search and gear off | 46.8 | 46.9 |
| pill shadow off | 48.1 | 48.1 |
| off-state blur slot off | 47.3 | 47.4 |
| inner clip off | 47.1 | 47.3 |
| backdrop batch off | 51.2 | 51.3 |
| chip edge mask off | 47.1 | 48.8 |
| stretch off | 52.5 (fewer frames) | 43.3 |

## Before and after

- Before: e74cbd41e, probe 2803 with no switches.
- After: APK 2805.
- Both runs are in one session with the same prefs (light, Graphite). Raw lines are in `matrix.txt`.

| Style | Screen | Before mean / p90 | After mean / p90 |
|---|---|---|---|
| Solid | Conversations | 16.9 / 17.4 | 16.8 / 17.2 |
| Solid | Chat | 27.4 / 19.0 * | 27.2 / 18.9 * |
| Solid | Settings | 16.7 / 19.1 | 16.8 / 19.6 |
| Glass | Conversations | **46.6 / 54.7** | **17.2 / 18.4** |
| Glass | Chat | 27.4 / 18.3 * | 27.5 / 19.9 * |
| Glass | Settings | 16.7 / 18.2 | 16.7 / 17.7 |
| Crystal | Conversations | **46.5 / 56.9** | **17.1 / 18.4** |
| Crystal | Chat | 27.2 / 17.8 * | 27.1 / 18.8 * |
| Crystal | Settings | 16.8 / 17.8 | 16.8 / 18.0 |

\* In chat the list settles between swipes. SurfaceFlinger's mean then counts the idle gaps of about 370 ms. Flutter's own longest frame there was 15 ms, with 3–7 ms raster.

## Pixels

Conversations screenshots on the device, before (2803 with no switches) against after (2805), the status bar left out:

- Solid: no difference.
- Glass: no difference.
- Crystal: no difference.

The rejected variant, a RepaintBoundary around the whole top bar, was just as fast. It moved the Glass frost by up to 8/255 and Crystal's round buttons by up to 13/255, because they sampled from inside the new layer.

Six Crystal-fallback goldens were updated: `kit_surfaces_*crystal*`. Under Skia, one or two pixels differ by one colour step.

## Tests

- `test/kit/kit_glass_repaint_test.dart` fails without the fix and passes with it.
- Every golden test passed on the PC: all of `test/goldens` and the 95 golden files elsewhere.
- 203 non-golden files that touch the bar, KitGlass, the composer and the screens passed on the build server. The exception is `chat_4_golden_test`: a golden file, which passes on the PC.
- `flutter analyze lib test tool`: clean.
