# Smoothness (jank) of the native app, 2026-11-09

Owner, 2026-11-09: "Find smoothness issues in native also. I found many, especially in glass." The native app exists
for smooth 120 Hz motion; the Flutter app's telemetry has more than 15 % janky chat frames for 13 of 17 users.

Branch `native/smoothness`. Measured, found, fixed, and what is left for the owner.

## How it was measured

| Source | What it is | Trust |
|---|---|---|
| **Owner's phone** (read-only `dumpsys gfxinfo … framestats`) | Real GPU (Skia GL), 120 Hz, the owner's own use since the last app start | The truth for "how bad is it today" |
| **Firebase Test Lab, Galaxy S24 (SC-51E, Android 16, 120 Hz)** | `GlassBenchmark` (Macrobenchmark `FrameTimingMetric`, R8 release-like build, `CompilationMode.Full`, 3 iterations) on `GlassProofActivity`: one kit page that scrolls itself the same way every run, in Solid, Glass, Glass + aurora shader, Glass + painted aurora | The before/after proof: same frames, same real device, two builds |
| Emulator OCN_U (API 35) | `-gpu host` does not run on this PC: headless it fails with "Failed to get EGL display", windowed it segfaults (also tried ANGLE, system libs, surfaceless EGL). Only SwiftShader (software GPU) boots, and it crashed twice under Glass. | **Not used for any claim about real GPUs.** Kept only as a relative "how much more drawing does Glass ask for" signal |

Paths: Test Lab measured list scroll, chat scroll and the reply-menu path in four looks, before and after. The app's
other paths (All conversations scroll, push/pop, sheets and menus, chat scroll in a 400-message chat, composer
typing) were run on the emulator against the FX-OC2 fixture server; those numbers are software-GPU numbers, shown
below only as relative signals. Not measured: streaming a reply (the stub model's prompt did not start a reply in the
fixture's beta server, so the "stream" runs drew 29 frames of nothing) and the Agents tabs (no agents on the fixture).

## Real device: Galaxy S24, 120 Hz (Test Lab), before and after

Before = `native/next` at b1dea5ee9 plus the proof page; after = the same plus the ink sampler and shared frost fixes.
16 s of scrolling per run (≈1 900 frames), 3 runs each. "Late" = finished after its frame deadline
(FrameTimeline); "> 1 vsync late" = more than 8.3 ms late, a frame the eye sees repeated.

| Path · look | frames/run | late frames % | >1 vsync late % (per run) | frame CPU p50 / p90 ms (median run) |
|---|---|---|---|---|
| listGlassAurora | 1938 → 1945 | 100 → 100 | 0.9 / 50.7 / 0.1 → 97.0 / 0.0 / 0.0 | 8.8 / 10.4 → 7.1 / 8.0 |
| listGlass | 1888 → 1888 | 96 → 98 | 0.1 / 0.0 / 0.1 → 0.0 / 0.0 / 0.1 | 7.0 / 8.2 → 7.2 / 8.2 |
| listSolid | 1884 → 1883 | 0 → 0 | 0.0 / 0.0 / 0.0 → 0.0 / 0.0 / 0.0 | 4.1 / 4.4 → 4.1 / 4.6 |
| chatGlassAurora | 1937 → 1932 | 100 → 100 | 0.2 / 0.2 / 0.1 → 0.0 / 9.2 / 0.1 | 6.8 / 7.3 → 6.8 / 7.3 |
| listGlassDrift | 1890 → 1891 | 100 → 100 | 0.3 / 8.5 / 0.3 → 0.2 / 0.1 / 0.0 | 7.2 / 8.1 → 7.1 / 8.1 |
| chatGlass | 1877 → 1884 | 90 → 87 | 0.0 / 0.1 / 0.1 → 0.0 / 0.0 / 0.0 | 6.8 / 7.4 → 6.7 / 7.3 |
| chatSolid | 1882 → 1881 | 33 → 12 | 0.0 / 0.0 / 0.0 → 0.0 / 0.0 / 0.0 | 6.2 / 6.9 → 6.2 / 6.8 |

Reading it:

- **Solid holds 120 Hz with room to spare** (4.1 ms of frame CPU, no late frames in the list).
- **Every Glass page finishes each frame 1–5 ms after its deadline, before and after.** The S24 still delivers
  ≈118 frames a second, so this is one extra frame of latency, not stutter; true stutter (> 1 vsync late) comes in
  whole runs where the device drops into a slow state (the 50.7 % and 97.0 % runs, one before and one after: the
  same device state, not the build; per-run frame CPU 11.7–12.6 ms against 7.0–7.1 ms in the other runs).
- Glass costs ≈3 ms more frame CPU than Solid (render thread) and ≈3–4 ms more GPU per frame. Trace of a normal run
  each, Glass + aurora list, before → after: GPU work per frame (GPU completion wait) **4.2 → 3.1 ms (−26 %)**,
  offscreen render passes per frame **21.9 → 16.3 (−26 %)**, HWC release waits 1 229 → 249, render-thread drawing
  5.8 → 6.0 ms (unchanged). The shared frost removes a quarter of Glass's GPU work over the aurora; it does not yet
  bring Glass inside the 8.3 ms deadline.
- What the remaining render-thread time is (trace, Glass list without background): per frame 6 offscreen passes,
  "flush layers" 1.5 ms, two 168×168 px layers for the liquid + alone; the liquid panes (bar, +, composer, chips:
  each a layer with colour, blur and lens passes, a rim pass and a shadow) are the cost that stays.


## The owner's report: a reply's ⋯ menu opening in Glass

Same device and method; the proof page opens and closes a reply's menu over the chat 8 times (system animations on).
Before = b1dea5ee9 + proof page; after = + ink sampler, shared frost and the modal allowance.

| Path · look | frames/run | late frames % | >1 vsync late % (per run) | frame CPU p50 / p90 ms (median run) |
|---|---|---|---|---|
| menuGlass | 315 → 352 | 45 → 41 | 23.1 / 23.8 / 22.8 → 12.1 / 12.2 / 13.0 | 5.9 / 23.3 → 5.7 / 11.5 |
| menuSolid | 347 → 343 | 14 → 17 | 11.5 / 11.5 / 11.4 → 13.1 / 12.7 / 12.7 | 3.8 / 6.5 → 4.1 / 7.5 |
| chatGlassAurora | 1929 → 1941 | 100 → 100 | 0.2 / 0.1 / 0.1 → 0.1 / 1.3 / 0.1 | 6.8 / 7.3 → 6.8 / 7.5 |
| menuGlassAurora | 1673 → 1834 | 90 → 91 | 7.8 / 7.2 / 7.2 → 9.1 / 5.0 / 5.8 | 6.7 / 8.3 → 6.8 / 9.4 |

- **Glass (still background): stutter frames 23 % → 12 % in every run (23.1/23.8/22.8 → 12.1/12.2/13.0), frame CPU
  p90 23.3 → 11.5 ms.** Glass now stutters as much as Solid does on the same path (11.5–13 %), no more.
- The trace shows why (one run each): before, the UI thread did **178 `RenderProxy#copyHWBitmapInto readback`s
  (285 ms in all, up to 11.8 ms each)** and the render thread 178 extra `DrawFrames` for them: the menu's and the
  chrome's ink samplers reading the page back while the menu grew and the anchor's ripple redrew the page. After: none.
- Glass over the aurora: 7.4 % → 6.6 % stutter frames (runs 7.8/7.2/7.2 → 9.1/5.0/5.8): within noise; that path is
  bound by the background's per-frame cost (cause 3).
- What is left on this path in every style, Solid too (11–13 % stutter frames): opening a menu creates a popup
  window and composes it (`Compose:onRemembered` up to 28 ms, first `traversal` of the new window up to 43 ms).
  Option below.

## The owner's phone (read-only)

First read 07:05 (app up 24 min, 48 922 frames):

| | Value |
|---|---|
| Janky frames (missed the frame deadline) | **3.81 %** (1 866) |
| Janky frames (legacy definition) | **29.6 %** |
| Frame time p50 / p90 / p95 / p99 | 10 / 17 / 22 / **53 ms** (vsync is 8.3 ms) |
| GPU p50 / p90 / p99 | 5 / 7 / 15 ms |
| Slow UI thread / slow draw commands / missed vsync | 1 308 / 1 509 / 1 284 |
| Last 120 frames: UI thread p50, render thread p50 | 1.1 ms, **7.5 ms** (of an 8.3 ms vsync) |
| `ImageReader-…` sample buffers alive | **230** (139×18, 129×21, 45×10, 70×10, 41×10 px: the bar, composer and chips' ink samples at 1/8 size) |
| Average frame rate over the 24 min | 33 fps: long stretches of continuous drawing (the live background) |

The phone was not connected for a second read later in the day (adb showed no device from 08:30 on); read it again
after the next build reaches it: `dumpsys gfxinfo io.github.eslamasabry.opencode_mobile.native` — the
`ImageReader-…` count should stay at 0–5 instead of 230, and Slow UI thread frames should drop.

## Emulator (SwiftShader software GPU, 540×1200, 60 Hz): relative signal only

Before the fixes. Late = frames over the deadline; UI and render thread are framestats p50 / p90 of the last 120 frames.

| Path | Look | Frames | Late % | p50 / p90 / p99 ms | UI thread p50 / p90 | Render thread p50 / p90 |
|---|---|---|---|---|---|---|
| Home at rest, 6 s | Solid / Glass (no background) | 0 / 0 | – | – | – | – |
| Home at rest, 6 s | Glass + aurora | 174 | 84 | 69 / 81 / 93 | 0.2 / 0.3 | 40 / 51 |
| All conversations, 8 flings | Solid | 504 | 7.5 | 19 / 32 / 65 | 0.8 / 2.6 | 19 / 29 |
| All conversations, 8 flings | Glass (no background) | 436 | 10.8 | 28 / 36 / 101 | 0.6 / 1.8 | 23 / 30 |
| All conversations, 8 flings | Glass + aurora | 281 | 75 | 61 / 113 / 150 | 0.3 / 4.0 | 37 / 57 |
| Server sheet + projects menu, 3× | Solid | 390 | 5.9 | 16 / 29 / 44 | 0.4 / 1.4 | 16 / 26 |
| Server sheet + projects menu, 3× | Glass + aurora | 785 | 85 | 69 / 150 / 300 | 0.2 / 0.7 | 49 / 85 |
| Open a chat and back, 4× | Solid | 304 | 21 | 28 / 65 / 500 | 1.2 / 4.2 | 20 / 26 |
| 400-message chat, 10 flings | Solid | 481 | 13 | 32 / 44 / 85 | 0.8 / 2.1 | 24 / 30 |
| Composer, 10 words typed | Solid | 52 | 63 | 29 / 48 / 97 | **4.1 / 10.4** | 24 / 30 |

What it says, as a ratio only: Glass + aurora asks the GPU for 2–10× the work of Solid on the same paths, and typing
costs the UI thread 4–10 ms per key even in Solid on a fast desktop CPU (cause 6). The emulator crashed twice under
Glass + aurora in the chat, so no after-numbers were taken there; Test Lab is the before/after proof.

## Causes, ranked by how much jank they explain

1. **Every liquid pane's ink sampler stalled the UI thread on the GPU (fixed).** Biggest single cause of the menu
   jank (see above). Each liquid pane (bar, composer,
   every chip, the jump pill, the +) read the content behind it every 100 ms while that content moved (scrolling,
   streaming), through `GraphicsLayer.toImageBitmap()` and a hardware-bitmap `copy()`. On Android that makes a new
   `HardwareRenderer` and `ImageReader` per sample, draws, and waits for the GPU on the UI thread, then waits again for
   the copy. Five panes in a chat = up to 50 blocking GPU round trips a second, exactly while the person scrolls or a
   reply streams. Evidence: the phone held 230 `ImageReader` buffers of exactly the panes' sample sizes; 1 308 slow UI
   thread frames; p99 53 ms. Fix: `AndroidBackdropSampler` keeps one small `HardwareRenderer` + CPU-readable
   `ImageReader` per pane for its life; a sample records a display list and returns; the pixels are read on a
   background thread. No look change.
2. **Every in-flow Glass pane blurred the whole picture again, every frame the aurora moved (fixed).** Rows, chips,
   fields and cards each recorded the live background into their own layer and ran their own blur. Ten panes on a
   page = ten blurs and ten offscreen layers a frame while the shader look animates (every vsync) or while a list
   scrolls. Evidence: the phone's render thread at 7.5 ms of an 8.3 ms vsync with only 1.1 ms of UI work, 1 509 slow
   draw-command frames, 12.5 MB of stencil buffers (path clips); on the emulator Glass + aurora drew 3–10× more work
   than Solid on the same paths. Fix: the panes of one sigma share one blurred copy, blurred at half size with half
   the radius (a quarter of the pixels), and each pane draws its part clipped to its shape. Look: identical in the
   middle of a pane; at a pane's edge the frost now shows what lies just beyond the edge instead of repeating the
   edge pixels (what real frosted glass does).
3. **The live background draws every frame while it runs (by design, not changed).** Glass + aurora renders at the
   display rate whenever the clock runs (until 30 s without a touch). On the emulator: 0 frames at rest in Solid or
   Glass without a background, a full frame every vsync with the aurora. Each `KitScreen` owns its own
   `KitLiveBackground`, so during a push or pop two shaders and two glass stacks draw at once. Options for the owner
   below.
4. **Opening a sheet or menu flipped other panes' glass (fixed).** The window's liquid budget (5) was shared: a menu
   took two slots (its pane and the page it records), so on a chat with the composer, the bar and three chips two
   chips flipped to frost as the menu opened and back as it closed, each rebuilding its glass, and every pane on the
   page recomposed on each change of the window's grants. Sheets and menus now have their own allowance; each pane
   reads only its own grant.
5. **Opening any menu creates a popup window (not fixed, every style).** 11–13 % stutter frames on the menu path in
   Solid too: window creation and first composition (up to 28–43 ms on the UI thread on the first frame).
6. **Typing in the composer recomposes the whole composer per key (not fixed: chat-composer and kit-chat owners).**
   `ComposerScreen` reads `field.text` in composition and passes it to `KitComposerTextBox`, which measures the text
   in composition inside a `BoxWithConstraints` to decide the one-row/stacked layout, and `onTextChanged` round-trips
   through the view model. Emulator (fast desktop CPU), Solid: 60 % janky while typing, UI thread p90 10–12 ms per
   key, max 24–30 ms. A phone CPU is slower. Fix proposal: read the text in a `snapshotFlow` only, decide "stacked" in
   a layout modifier (measure in the layout phase, not composition), and skip the view-model round trip per key.
7. **Smaller items seen in review, not measured as large:** list `items` without `contentType` on Conversations and
   All conversations (one row type, so reuse already works); `WordsPlateNode` draws flipped words through two
   `saveLayer`s of 9× the text's area while a pane is mid-flip; each `KitFrost` builds its 64×64 noise tile with
   4 096 `drawRect` calls on first composition; the Kyant pane's effect chain is rebuilt on every frame of a sheet's
   predictive back (by design of the library).

## Fixes (landed)

| Commit | What | Guard test |
|---|---|---|
| 59323bece | Ink samples without stalling the UI thread | `KitLiquidInkSamplerTest`: one sampler per pane, no samples while the content is still, samples when it draws again, never a snapshot read back, released with the pane |
| d97e463f0 | Glass panes share one blurred copy | `KitSharedFrostTest`: three panes of one sigma and one of another make exactly two blurred copies, and each pane still shows the picture through its frost |
| d84052d90 | A menu or sheet never takes the page's liquid slots | `LiquidEdgeTest`: opening a reply's menu (or a sheet and a menu) changes no other pane's face with the composer, the bar and three chips |
| 61a2a4f51, 54b0629fe, 8f28e5b71 | `GlassProofActivity` + `GlassBenchmark` (benchmark build type only; scroll, chat and menu scenes) | the Test Lab runs above |

## Round 2

The menu layer, the half-resolution liquid backdrop and the one live background landed on 2026-11-09; see
[round-2.md](round-2.md) for what changed, the guard tests and the S24 numbers.

## Left, with options for the owner

- **Live background cost** (cause 3). (a) keep as is; (b) one background for the whole app instead of one per
  screen (no double shader during push/pop; same look); (c) cap the shader looks at 60 fps on 120 Hz screens (the
  looks are slow drifts; nobody sees 120 vs 60 in an aurora; half the GPU time). (b) changes no look; (c) is a small
  look change and needs the owner's yes.
- **Glass at 120 Hz is still ≈3 ms over the frame deadline on a Galaxy S24** (every Glass frame one vsync late, at
  full rate). The liquid panes' own layers (colour, blur, lens, rim, shadow per pane) are what stays. Options:
  (a) accept it (smooth cadence, one frame more latency); (b) draw each liquid pane's blur and lens at half
  resolution with the rim at full (needs a small fork of the Kyant library; the bent, blurred content looks the
  same, the rim stays crisp); (c) fewer liquid panes per screen (e.g. the chat's chips frosted, composer and bar
  liquid): a look change. (b) is my recommendation; it needs a yes because it touches the vendored library.
- **Menu popup** (cause 5): draw menus in a layer of the app's own window instead of a popup window (no window
  creation per open; same look). Kit change, no look change.
- **Composer typing** (cause 6): a kit-chat and chat-composer change, owners to schedule.
- **A real-GPU emulator on the PC**: `-gpu host` fails on this machine (Intel UHD 630, Mesa 26); measurements need
  Test Lab or the phone until that is fixed.

## Files

- Scripts used: `GlassProofActivity` (`native/androidApp/src/benchmark/…/proof/`), `GlassBenchmark`
  (`native/benchmark/…`). Test Lab: `gcloud firebase test android run --type instrumentation --app <previewBenchmark
  apk> --test <benchmark apk> --device model=SC-51E,version=36 --test-targets "class
  io.github.eslamasabry.oc.benchmark.GlassBenchmark" --environment-variables additionalTestOutputDir=/sdcard/Download
  --directories-to-pull /sdcard/Download` (project `oc-mobile-testlab-8699`, billing off).
