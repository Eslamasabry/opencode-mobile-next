# Unit S3-COMPMOTION: composer and turn motion (08-M5) in `:ui:kit-chat`

Date: 2026-11-01. Branch: `native/compmotion-2026-11-01` (from `claude/native-compmotion-reduced-motion-3n3tv3` at
`7db496b2`). Write set for this session (narrowed by the brief): the 08-M5 files of `native/ui/kit-chat/**`, new files
only — `ComposerGlow.kt`, `MovingPart.kt`, `KitComposerStop`, the status strip reveal, the "Stop and send" pill and the
`KitTurnLive` sweep — plus this report. Nothing outside it was edited (no build file, catalog, settings or kit change).

Inputs read: `native/AGENTS.md`, `docs/native/units/INTEGRATE-W1.md`, plan 15 (row S3-COMPMOTION), plan 08 §2, §3,
§4, §5.2, §5.3, §5.15, §11 M5, plan 06 (LOOK-4 amber meanings, §7.8 kit-chat part list), the catalog rows below, and the
Flutter sources being ported: `lib/ui/kit/chat/kit_composer_classic.dart` (`_Classic`, `_ClassicHalo`,
`_ActivityRingPainter`, `_glowHues`), `kit_composer_glow.dart` (`_ActivityGlow`, `_GlowPainter`), `kit_turn.dart`
(`_LiveSweep`).

**Result.** The liked running green glow is ported with its exact geometry and hue (Flutter's straight sRGB partner
lerp, not Compose's Oklab `lerp`), as the one Glow choice (Off / Classic / Soft ring), with Full / Calm / Off / Saver
behaviour, battery saver and heat running it Calm through `KitTheme`. The Stop chip, strip reveal, "Stop and send" pill,
field-button swap and the live-line sweep are built. 34 new desktop tests and 15 key-frame goldens are green; the
whole `:ui:kit-chat` desktop suite (119 tests) passes. Not done in this session: the step light, Signal lamps,
`KitToolRow` status, `KitRequestCard` motion, `KitQueuedMessage`, `KitBackgroundWorkLine`, `KitFindMark`, IME frame and
the Flutter side-by-side capture (§7).

## 1. What was built

All in `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/`:

| File | Part | Notes |
|---|---|---|
| `composer/MovingPart.kt` | `KitGlowChoice` (public; `of(on, soft)`), `MovingPart` + `movingPart(level, glowOn)` (MOT-15), `GlowPlan` + `glowPlan(choice, level, saver, stillFrame)`, loop maths (`lapPhase`, `haloAlpha`, `fadeProgress`), `rememberLoopPhase` and `rememberGlowStrength` | The loop clock uses `withInfiniteAnimationFrameNanos` (R4), runs only while asked, follows `debugTimeScale` × system animator scale (08 §3.2), parks otherwise. Strength eases over `GLOW_FADE` (450 ms) on `KitEasing.Enter`; Off snaps; first composition is final (R8). |
| `composer/ComposerGlow.kt` | `KitComposerGlow(active, choice, corner, content)`, `ComposerHalo`, `Modifier.composerRing`, `GlowColours`/`glowColours`, `srgbLerp`, `ringBand` | Classic: 1.6 dp sweep inside the edge, stops `[0,.10,.18,.26,.38,1]`, base = accent @ 0.28, partner = sRGB lerp(accent, text1, 0.55 dark / 0.30 light); one lap per 3600 ms; halo = its own node, constant `Shadow` (blur 18, spread 1) under `graphicsLayer { alpha = strength × (0.25 − 0.10 cos 4πφ) }`. Soft: 2.2 dp line + 12 dp wash on a constant `BlurEffect(7)` layer, peaks 0.75 / 0.22, 8000 ms, rotation `φ − ¼`. Calm: still ring (Soft at half strength), halo held at 0.25. Off: nothing. Saver: 1 dp accent hairline. Path and brushes cached per size; each frame only rotates the canvas; the pill never recomposes. Halo and wash layers lay out larger than the pill so the blur is not cut. |
| `composer/KitComposerStop.kt` | `KitComposerStop(label, targetLabel, onStop, state, armKey, workingReason, stopAfterStep)`, `KitStopState {Running, Stuck, Working}`, `KitStopAfterStep`, `StopGuard`, `StopGlyph` | `KitChip` look in a 48 dp `KitTappable` (Pill). 800 ms guard on the **frame clock** (armed on the frame after each `armKey` change; a tap is judged on the next frame) — no timer (R9). Commit haptic only on an accepted stop. Stuck: tone cross-fade (`specs.quick()`) to the chip's Attention tone. Working: disabled with the reason as state description, no hold menu. Hold: one-item `KitMenu` "Stop after this step" (LongPress haptic from `KitTappable`), also a custom accessibility action. Screen-reader name = `targetLabel`. |
| `composer/ComposerStripReveal.kt` | `KitComposerStripReveal(stop, modifier, chips)` | Empty strip + Stop: unfolds (`expandVertically` + `fadeIn`, `specs.enter()`); empties: folds (`specs.quick()`). With chips: the Stop alone fades in/out (`QUICK`) at the trailing end with its width taken at once; chips scroll sideways inside their own row. Off: `EnterTransition.None`/`ExitTransition.None`. Leaving content stays drawn while it folds. |
| `composer/KitStopAndSendPill.kt` | `KitStopAndSendPill(label, onClick)`, `KitComposerSwap(target, content)` | Amber `attentionFill` pill, stop square + words in `onAttentionFill`, `KitHaptic.Send`. The swap is a `Crossfade` on `specs.quick()` (instant under Off; no scale, MOT-2). |
| `turn/KitLiveSweep.kt` | `KitLiveSweep(working, content)`, `liveSweepCentre`, `liveSweepSpeed` | `text3 → text1 → text3` band ±0.18 of the width, centre −0.3 → 1.3 (reversed RTL), 2200 ms a pass, Calm half speed, Off / not working: plain words. Offscreen layer + two `SrcIn` rects; the band brush is cached and translated per frame. One `Box` either way, so the words keep their state when the sweep starts or stops. |

Tests (`src/desktopTest/.../composer/`, `.../turn/`, `src/androidHostTest/.../ComposerMotionGoldensTest.kt`) and goldens
(`src/screenshots/ComposerMotion/*.png`).

## 2. Catalog rows covered

| Row | How | Tests |
|---|---|---|
| B-CHAT-006 (glow on by default, classic, one colour, normal speed; a stored off stays off) | the kit half: `KitGlowChoice.of()` defaults on/Classic, off stays Off, Soft; default `choice` of `KitComposerGlow` is Classic; one colour (sRGB partner lerp), app speed. Persistence across restarts is `:engine:settings` (`Preferences.glowStyle`), not this unit. | `MovingPartTest` B-CHAT-006, `GlowColoursTest` B-CHAT-006, `ComposerGlowUiTest` B-CHAT-006; goldens `glow_*` |
| B-KIT-002 (the reply glow is one choice) | `KitGlowChoice` Off/Classic/Soft with the app's own lap times; no colour or speed input exists | `MovingPartTest` B-KIT-002 |
| B-CHAT-012 (a stop pressed here …) — **partly** | the Stop control's guard and confirmed-stop path; the per-session "Stopped" memory is `:engine:delivery` | `StopGuardTest` B-CHAT-012 |
| Plan-named tests (no catalog id) | MOT-15 `MovingPartTest`; `GlowColoursTest`; `StopGuardTest`; `ComposerKeysUiTest` (strip, Stop, pill, swap half); `RecompositionBudgetTest` (as `ComposerGlowUiTest` + `LiveSweepTest` draw-only cases); MOT-7, MOT-13, M8-6, X6, R8 cases | see §3 |

Not covered (outside this session's narrowed write set; still on the S3-COMPMOTION row): B-CHAT-050 (`HeldAnswerTest`,
request card), B-CHAT-063 (work line), B-CHAT-117 (tool spinner settle), B-CHAT-123 (approval card motion), B-KIT-036
(step light, `StepLightPhaseTest`), `SignalLampTest`, `HaloPainterReuseTest`, `ImeClearanceTest`.

## 3. Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` (JDK 17 installed for the toolchain),
`ANDROID_HOME=/opt/android-sdk` (cmdline-tools `commandlinetools-linux-16111833_latest.zip`; `platform-tools`,
`platforms;android-37.1`, `build-tools;37.0.0`). Container-only, not committed: `~/.gradle/init.d/central-mirror.gradle.kts`
(Google's Central mirror first for plugins and dependencies, `gradlePluginPortal()` kept for build-logic, and
`robolectric.dependency.repo.url` on every `Test`), `~/.gradle/gradle.properties` `-Xmx6g`, 4 workers.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :ui:kit-chat:compileKotlinDesktop` (baseline) | first failed on plugin resolution (no mirror); with the init script: BUILD SUCCESSFUL |
| 2 | `./gradlew :ui:kit-chat:desktopTest --tests '*.composer.*' --tests '*.turn.*'` (first run of the new tests) | 33 run, 3 red: two `GlowColoursTest` cases (tolerance below Compose's 8-bit sRGB packing → `1/255`), `StopGuardTest` B-CHAT-012 (the first guard used `delay`, which the v2 test dispatcher drains; moved to the frame clock, which is also rule R9) |
| 3 | same, after the fixes | **34 tests, 0 failures** (ComposerGlowUiTest 7, ComposerKeysUiTest 5, GlowColoursTest 4, MovingPartTest 8, StopGuardTest 5, LiveSweepTest 5) |
| 4 | `./gradlew :ui:kit-chat:recordRoborazziAndroidHostTest --tests '*ComposerMotionGoldensTest'` | 15 tests, 0 failures; 21 PNGs. The first capture showed the Classic halo cut at the pill's rectangle → halo/wash layers given room (commit `fix(ui-kit-chat): give the glow halo room …`), re-recorded |
| 5 | `./gradlew :ui:kit-chat:verifyRoborazziAndroidHostTest --tests '*ComposerMotionGoldensTest'` | BUILD SUCCESSFUL |
| 6 | `./gradlew :ui:kit-chat:desktopTest` (whole module) | BUILD SUCCESSFUL, **119 tests, 0 failures** (the existing 85 unchanged) |
| 7 | `native/tool/check_commits.sh HEAD~4 HEAD` | OK, 4 commits |

Frame-budget evidence (desktop compose clock, 16 ms frames): during 3 s of a travelling glow the pill composes 0 more
times and re-lays out 0 times while the ring redraws ≥ 150 frames; a Calm glow, a battery-saver glow and a glow present
at first composition request no frames once lit; a faded-out glow leaves the tree and requests no frames; the live
sweep redraws per frame with 0 recompositions and 0 re-layouts of the words. Timing in ms per frame (≤ 0.5 ms UI,
≤ 1.5 ms RenderThread) needs the device/macrobenchmark lane (`StreamingBenchmark`, S8-9), not this container.

Golden files (`native/ui/kit-chat/src/screenshots/ComposerMotion/`): `glow_classic_{dark,light}_phase057`,
`glow_soft_{dark,light}_phase008` (Soft's still frame is Flutter's 0.08, not 0.57), `glow_calm`, `glow_saver_hairline`,
`stop_chip_arrive_{0,75,150}ms_{full,calm}`, `stop_strip_reveal_{0,125,250}ms`, `stop_chip_stuck_amber`,
`stop_chip_working`, `stop_and_send_pill_{ltr,rtl}`, `live_sweep_mid_{ltr,rtl}`. Not captured: `voice_stop_ring_*`
(the voice circle is S5-VOICE).

## 4. Deviations and decisions

1. **No new `KitMotionSpecs` builders.** `:ui:kit`'s `KitMotionSpecs` has `enter/quick/exit/spatial/largeTravel` only;
   plan 08 §2.7's `glowFade()`, `lap()`, `breath()` are missing and `:ui:kit` is not in this write set. Instead of
   writing `tween(`/`infiniteRepeatable(` in kit-chat (R1), the glow fade and the loops run on the frame clock with the
   tokens (`GLOW_FADE`, `GLOW_CLASSIC_LAP`, `GLOW_SOFT_LAP`, `LIVE_SWEEP_LAP`) and `KitEasing.Enter`; fades and folds
   use `specs.enter()/quick()`. No literal duration appears in the new files. See catalog requests.
2. **Stop guard on the frame clock.** Plan 08 sketches "taps inside 800 ms are ignored"; implemented with
   `withFrameNanos` (arm on the send's frame, judge the tap on the next frame) so there is no timer (R9) and tests are
   deterministic. A tap acts one frame later (≤ 16 ms). A tap before the chip's first frame is ignored too.
3. **Glow strength under `stillFrame`.** Still frames draw the travelling look at its frame phase (Classic 0.57, Soft
   0.08, Flutter's values) with the halo held at 0.25; the strength fade is not a loop, so it still runs.
4. **Soft ring blur.** Compose common has no `MaskFilter`; the wash is a band on its own layer with a constant
   `BlurEffect` (Android 12+ and desktop; below API 31 the wash draws unblurred — acceptable, it is 0.22 alpha).
5. **Saver hairline vs. Off.** `glowPlan` checks Saver before motion Off (Saver forces Off but still shows the hairline,
   08 §5.3).
6. **Lifecycle gate.** 08 §3.5 asks loops to run only while the window is `STARTED`. `lifecycle-runtime-compose` is not a
   `:ui:kit-chat` dependency and the build file is outside this write set; loops rely on the frame clock stopping with
   the window (Choreographer) and on leaving composition. `LocalKitPageVisible` does not exist yet.
7. **Names.** The strip part is `KitComposerStripReveal` (not `KitComposerStatusStrip`) and the field swap is
   `KitComposerSwap`, so they cannot collide with the composer UI unit's own files; the composer frame should wrap its
   strip row and field button with them.
8. **Stop and send words** are drawn with `BasicText` in `onAttentionFill`: `KitText` has no on-attention tone. They are
   frame text without the kit's frame cap until the kit offers that tone.

## 5. Catalog requests and requests to other owners

- **Kit owner (`:ui:kit`):** add `KitMotionSpecs.glowFade()`, `lap(ms)`, `breath(halfMs, easing)` and
  `KitMotionState.progressLoopSpeed` / `transforms` (08 §2.7); then kit-chat can swap its frame-clock fade for them.
  Add `TextTone.OnAttention` (or a `KitText` colour slot) for the "Stop and send" words. Consider a public
  `KitPartGlyphs.Stop` (the stop square is local to kit-chat today).
- **`:ui:kit-chat` build file owner:** `lifecycle-runtime-compose` (catalog alias exists: `libs.lifecycle.runtime.compose`)
  for the `STARTED` gate of 08 §3.5.
- **Feature owner (`:feature:chat`, composer UI unit):** map `Preferences.activityGlow` + `glowStyle` with
  `KitGlowChoice.of(on, soft = glowStyle == SoftRing)`; pass `armKey` = the accepted-send counter, `targetLabel` =
  "Stop {agent}" (`kitTurnLiveStopShort` for `label`), `workingReason`, and `stopAfterStep` only where the agent can
  steer; wrap the composer pill in `KitComposerGlow`, the strip in `KitComposerStripReveal`, the field button in
  `KitComposerSwap`; put the turn's live words in `KitLiveSweep`. Words come from `:ui:words` (none are hard-coded in
  the parts).
- No version-catalog or build-logic requests.

## 6. Accessibility, privacy

- The glow and the sweep draw only; they add no semantics and take no touches. The Stop target's name is the target
  ("Stop Claude Code"); "Stop after this step" is also a custom action; Working reads as not enabled with its reason;
  the 800 ms guard does not hide or disable the chip. The pill reads as its words. Amber is used only for the stuck
  chip and the pill (approved meanings, 06 LOOK-4).
- No credentials, prompt text or URLs are handled.

## 7. Open problems

1. The rest of the S3-COMPMOTION row is not built: step light and Signal lamps (B-KIT-036, `StepLightPhaseTest`,
   `SignalLampTest`), the live line's Signal go-lamp, `KitToolRow` status, `KitRequestCard` motion and `HeldAnswerTest`
   (B-CHAT-050, -123), `KitQueuedMessage`, `KitBackgroundWorkLine`, `KitFindMark`, the IME frame (`ImeClearanceTest`),
   and work-line motion (B-CHAT-063, -117). They need their write-set files (and S3-WORK's parts) first.
2. `HaloPainterReuseTest` (Android host: 3 s of breathing builds the shadow painter once) is not written; the design
   that makes it pass (constant `Shadow`, alpha on the layer) is in place.
3. The glow compared side by side with a Flutter capture on the same emulator (`judge-visuals-hard`) is not done — it
   needs the emulator lane and an APK. The goldens here are the native half.
4. Per-frame time budgets (≤ 0.5 ms UI, ≤ 1.5 ms RenderThread) are unmeasured until the macrobenchmark lane (S8-9).
5. `tool/format-changed.sh` named in `native/AGENTS.md` does not exist on this branch; the new files were formatted by
   hand in the module's style (no detekt/ktfmt run).
