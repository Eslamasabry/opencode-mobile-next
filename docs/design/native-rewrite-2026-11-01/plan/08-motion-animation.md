# 08. Motion and animation: tokens, every interaction, reduced motion, budgets and proof

Written 2026-11-01. Owners of the code this section specifies: the kit owner (`:ui:design` motion files, `:ui:kit`
motion parts and the `KitPlatform` motion seams), the kit-chat owner (`:ui:kit-chat` streaming, composer and step
motion), and the coordinator (`:ui:shell` transitions and the predictive-back gate). Feature agents never write motion
code; they pick kit parts and route classes.

## For the owner, in five lines

1. Everything moves with one small set of timings, mostly 150–250 ms, with Android's own feel: ripple, stretch at the
   end of a list, the predictive back preview and the phone's own vibration. Nothing new bounces.
2. The chat keeps what you liked: words fade in as they stream, the reply grows smoothly, your message lifts out of the
   box, the green glow turns while the agent works, and Stop stays visible above the box.
3. A chat grows out of its row when you open it and shrinks back into it when you tap Back. The back swipe shows
   Android's own preview instead and never fights the grow animation, which was the demo's glitch.
4. "Remove animations", your Motion choice (Full, Calm, Off), battery saver and a hot phone all slow motion down or stop
   it. Under Off nothing moves at all.
5. Every animation is proved three ways: tests for the rules, frame-by-frame pictures, and speed measurements at 120 Hz,
   with no dropped frames while a reply streams.

Four small choices for you are in §12. Each has the default we build if you say nothing.

Tags: **[V]** checked today (2026-11-01) against official docs, androidx source, or the bytecode of the published
artifacts in `~/.gradle/caches`. **[R]** read in the Flutter repo, a map, a research file, the ADR, a sibling plan section
or a memory file. **[E]** an estimate that the first measurement replaces. **[U]** unverified; the named spike or proof
settles it. Behaviour ids such as `B-KIT-023` are rows of `../catalog/BEHAVIORS.md`.

---

## 0. Scope, inputs, and how this section fits

### 0.1 What this section decides

- The motion tokens: durations, easings, springs, loop speeds, distances and the named exceptions (§2).
- The motion levels and reduced-motion rules, and how "Off" reaches every animation (§3).
- The rules every motion line of code follows (§4).
- An exact spec for every interaction the task names, plus the small "micro" motions of the kit (§5).
- The haptics map (§6), the reduced-motion matrix (§7) and the 120 Hz frame budgets (§8).
- How each motion is verified: logic tests, UI tests, frame captures, Macrobenchmark, Perfetto, device checks (§9).
- The spikes (§10), the work units for agents (§11) and the owner decisions (§12).

### 0.2 What it does not decide

- Token *ownership* stays with section 06 (`06-ui-kit-design-system.md` §4.8–§4.9). This section restates the tokens it
  uses and lists the corrections it needs from section 06 in §13.2. The coordinator merges them.
- Route classes and the back-dispatch order belong to section 07 (§3). This section gives each class its exact motion.
- The transcript data path (50 ms publish, block items, Markdown tail re-parse) belongs to section 09. This section
  specifies only what moves on screen.
- Widget, notification and Live Update animations (RemoteViews `Chronometer`, spinners) belong to section 11.
- Live backgrounds and illustration scenes are not in the switch release (ADR §12 D11). Their tokens are reserved here so
  a later port does not invent new ones.

### 0.3 Binding inputs

| Input | What it binds here |
|---|---|
| ADR §9.3 | 50 ms publish; one `LazyColumn(reverseLayout = true)` of block items; fresh words fade in a **draw-phase mask**, never per-frame `AnnotatedString` spans; the live block grows on a critically damped spring (stiffness 700); `LineBreak.Simple`; `preferredFrameRate(High)` only while the block reveals; selection off on the live block until Compose 1.13 |
| ADR §9.4 | Nav3 + `AppNavigator`; sheets are screen state on the M3 1.5 in-window `BottomSheet`; option A for list → chat with **no 700 ms latch**; option B (Nav3 1.3 + Compose 1.13 deferred transitions) in `:ui:shell` `androidMain` as a `JETPACK_AHEAD` row; the root never intercepts Back; `MotionScheme.standard()`, never expressive; kit duration tokens 150–250 ms; `MotionPolicy` (Full/Calm/Off, Saver, heat); no raw `tween`/`spring` outside the kit; native feel = AndroidX defaults, never re-implemented |
| ADR §6.3 rule 1 | features may not import raw `tween`/`spring`/`animate*`, `performHapticFeedback`, `LocalHapticFeedback`, `material3`, raw `clickable` |
| ADR §7.6 | Android-only APIs that are ahead of CMP live only in kit/shell `androidMain`, each with a `JETPACK_AHEAD.md` row |
| §02 §1.1 rule 3 | the only motions longer than 250 ms are named exceptions listed in this section (§2.6) |
| §02 §4 S-9 to S-13 | the smoothness numbers this section's budgets must meet (§8) |
| §06 §4.8, §4.9, §5.1, §6 | `MotionTokens`, `MotionPolicy`, `LocalKitMotion`, `KitPlatform` signals and haptics seam |
| §07 §3 | route classes `GrowFromRow`, `Page`, `FullScreenDialog`, `TopLevel`, `Replace`, `Root`; back-owner order |

Where this section and the ADR differ, the ADR wins. Where it differs from section 06 or 07, §13.2 names the difference
and the reason, and the coordinator decides.

### 0.4 Sources read for this section

- Research: `../research/motion-native.md` (all), `rendering-chat.md` §1, §4, §6, §7, `testing-perf.md` §4, §5, §9,
  `kmp-cmp-status.md` (versions).
- Maps: `../map/ui-kit.md` UI-KIT-07 to -10, -13, -18, -20, -22 to -30, -41, -50 to -52, K4, K5;
  `chat-genui.md` CHAT-GENUI-08, -09, -14, -21, -26, -27, -33, -40, -43, -56; `screens-nav.md` §1, §3.2.
- Flutter source (read-only, `/home/eslam/Storage/Code/wt/oc_app-batch1`): `lib/ui/kit/kit_motion.dart`,
  `motion/*.dart`, `chat/kit_composer_edge.dart`, `kit_composer_classic.dart`, `kit_message_lift.dart`,
  `kit_markdown_fresh.dart`, `kit_stream_caret.dart`, `kit_step_light.dart`, `kit_turn.dart` (`_LiveSweep`),
  `kit_arrival.dart`, `kit_tip.dart`, `kit_jump_pill.dart`, `docs/ux-system/revamp/STANDARDS.md` MOT-1 to MOT-15,
  `docs/qa/micro-motion-2026-10-27/README.md`.
- Demo (`/home/eslam/Storage/Code/kmp-demo`): `App.kt` (gesture latch), `ui/Theme.kt` (springs),
  `ui/ChatScreen.kt` (`RevealLoop`, `runningGlow`, composer), `ui/Kit.kt` (haptics).
- Owner memory: `android-native-feel`, `micro-animations-wanted`, `composer-green-glow-liked`,
  `chat-kit-decisions-2026-10-02` (incl. the 2026-10-30 Stop override), `no-glass-rule`, `steer-not-queue`,
  `teach-what-is-hidden`, `text-scale-cap`, `no-new-ui-levels`, `simplify-and-natural`, `judge-visuals-hard`,
  `test-on-emulator-not-phone`, `owner-phone-debug-allowed`, `no-owner-phone-screenshots`, `dev-freeze-native-rewrite-2026-11-01`.
- Checked today [V]: in the local Gradle cache, `material3-android 1.5.0-alpha22` (`MotionScheme` is a public interface
  with six `FiniteAnimationSpec` methods; in-window `BottomSheet(...)` and `sheetPredictiveBackScaling` exist;
  `ripple(...)` overloads take a `Shape`; `RippleConfiguration` exists), `ui-android 1.12.1` (13 `HapticFeedbackType`
  values; `DefaultHapticFeedback` passes the raw `HapticFeedbackConstants` value through `ViewCompat`;
  `FrameRateCategory`; `Modifier.dropShadow(shape, block)`), `foundation-android 1.12.1` (`animateItem(fadeIn,
  placement, fadeOut)`, `LazyListState.requestScrollToItem`, `combinedClickable(..., hapticFeedbackEnabled, ...)`,
  `AndroidEdgeEffectOverscrollEffect`), `animation-android 1.12.1` (`SharedContentConfig.isEnabled`,
  `shouldKeepEnabledForOngoingAnimation`, `permitTransformDuringDeferredTransition`,
  `alternativeTargetBoundsInTransitionScopeAfterRemoval`, `skipToLookaheadSize`), `animation-core-android 1.12.1`
  (`PathEasing`, `EaseOutCubic`/`EaseInCubic`, `DeferredTransitionState`), `navigationevent-android 1.1.2`
  (`NavigationEventDispatcher.transitionState: StateFlow<NavigationEventTransitionState>`), the Flutter embedding jar
  (`PlatformPlugin.vibrateHapticFeedback` constants, §6.1), the Navigation3 release notes (1.2.0 stable 2026-09-23;
  1.3.0-alpha01 moved `NavDisplay` onto `DeferredAnimatedContent`) and androidx-main `NavDisplay.kt`
  (`transitionSpec`, `popTransitionSpec`, `predictivePopTransitionSpec(@SwipeEdge Int)` parameters and metadata
  helpers).

---

## 1. Decisions in one table

| # | Topic | Decision | Why |
|---|---|---|---|
| M8-1 | Spatial springs | The **standard** M3 spring values (ζ 0.9; stiffness 1400 / 700 / 300) for everything new that a finger drives or can interrupt. The Expressive set is never used. | Expressive fast-spatial overshoots 9.5 %; the owner asked for "nothing bouncy". ADR §9.4. |
| M8-2 | Fades and colour | Fixed-duration tweens from the Flutter vocabulary: `QUICK` 150 ms, `STANDARD` 250 ms, `EaseOutCubic` in, `EaseInCubic` out. Exits use `QUICK`. | The owner approved this vocabulary in Flutter; tweens are cheaper and exact for alpha. |
| M8-3 | Owner exceptions | Exactly nine named motions may exceed 250 ms or scale (§2.6). Each has its source. | §02 rule 3; MOT-2; MOT-5. |
| M8-4 | Motion levels | `Full`, `Calm`, `Off`, resolved by one pure function from the system animator scale, the user's choice, Saver, battery saver and heat (§3). | B-KIT-006/-008; MOT-13; demo bug (scale read once). |
| M8-5 | How Off works | Every kit spec builder returns `snap()` under Off; `KitMotionScheme(off)` for wrapped M3 parts; `EnterTransition.None`/`ExitTransition.None` for routes; loops never start. Compose's own animator-scale handling covers the system "Remove animations" path. | No global switch exists in Compose for an in-app Off (motion-native §9.1). |
| M8-6 | Loops | Two kinds. **Progress loops** (live-line sweep, step light, spinners, Stop ring) run at full speed under Full, half speed under Calm (Flutter parity), and are still under Off. **Ambient loops** (glow travel, caret breath, refresh breath) run only under Full. All loops stop when not visible. | Flutter `_LiveSweep` and `_StepLight` already run at half speed under Calm; MOT-6, MOT-15. |
| M8-7 | One moving part in the composer | One pure resolver decides: glow travels, or Stop's ring turns, or nothing (MOT-15). | Owner rule 2026-10-03. |
| M8-8 | List → chat | Option A at the switch: container transform on tap and button-back; disabled while a back gesture runs; re-enabled when the navigation transition **settles** (a state machine, no timer). Option B is a spike that becomes default when Nav3 1.3 and Compose 1.13 are stable. | ADR §9.4; the demo's 700 ms latch was a timing guess. |
| M8-9 | Predictive back preview | The Material full-screen preview: scale 1 → 0.9, shift away from the swipe edge by up to `width/20 − 8 dp`, corners to 28 dp, fade-through starting at 35 % progress, easing `(0.1, 0.1, 0, 1)`. Shift follows the **swipe edge**, never the layout direction. | Material predictive-back values (motion-native §3.1); Arabic users swipe from either edge. |
| M8-10 | Sheets | `KitSheetHost` on the in-window M3 `BottomSheet`: drag, fling, settle and predictive back are Google's; the kit adds only the scrim and the dirty-sheet rule. | ADR §9.4; one window = one inset model. |
| M8-11 | Streaming | A paced reveal (base 45 characters per second, never more than 250 ms behind, snapped to whole words), each revealed run fading in over 250 ms in a draw-phase mask, the live block growing on `spring(1f, 700f)` (growth only), a breathing caret under Full. | The demo's reveal felt smooth to the owner; the Flutter fade and grow values are owner-approved (2026-10-27). |
| M8-12 | Stop | Per the 2026-10-30 owner override, the field button morphs only between mic and Send. Stop is its own key at the trailing end of the chip row for the whole running turn. "Send-to-stop" is therefore a hand-off, not a morph (§5.2). | Memory `chat-kit-decisions-2026-10-02`. |
| M8-13 | Haptics | One `KitHaptic` enum, mapped once in `AndroidKitPlatform` to `View.performHapticFeedback` constants. Default constants keep today's Flutter feel for Send, Commit, Done and LongPress, and use Android's semantic constants for switches and segments on API 34+ (§6). | Parity with what the owner feels today; MOT-11; owner rule "max native". |
| M8-14 | Overscroll, fling, ripple | Platform defaults, never replaced app-wide: stretch on API 31+, glow on 26–30, platform spline fling, `RippleDrawable` ripple. | ADR §9.4; owner rule 2026-10-26. |
| M8-15 | IME | The chat body sits in `imePadding()`, so the reverse-layout transcript and the composer ride the keyboard frame by frame; the composer's clearance is a measured spacer item, never `contentPadding`. `imeNestedScroll` stays off until a spike and the owner say yes (D8-2). | Docs: Spacer over contentPadding; one layout pass per IME frame. |
| M8-16 | Frame rate | `KitPlatform.highFrameRate(active)` only while a reveal, transform or predictive preview runs, and only under Full. Never below 60 fps. | ADR §9.3; owner "nothing less than 60". |
| M8-17 | Proof | Every interaction has a logic test, a frame-capture set and, where it moves a lot of pixels, a Macrobenchmark journey (§9). | §02 S-9 to S-13. |

---

## 2. Tokens

All tokens are plain numbers in `:ui:design` (`ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/motion/MotionTokens.kt`,
no Compose). Compose specs are built from them only in `:ui:kit` `motion/KitMotion.kt` and `KitMotionScheme.kt`
(§2.7). The names follow Flutter's `KitMotion` so a Flutter reader finds them.

### 2.1 Durations

| Token | ms | Meaning | Used by (§5) | Flutter source |
|---|---|---|---|---|
| `QUICK` | 150 | a control answering a touch; every exit | chips, toggles, icon cross-fade, count roll, colour change, menu, all exits, Calm cross-fades | `KitMotion.quick` |
| `STANDARD` | 250 | a part appearing or changing size | reveal/fold, entrances, word fade, sent-bubble lift, page shared axis, tab fade-through, jump pill | `KitMotion.standard` |
| `ROW_OPEN` | 450 | chat growing out of its row (exception) | §5.4 | Flutter Android page transition (`FadeForwardsPageTransitionsBuilder.kTransitionMilliseconds` = 450 [V]) |
| `ROW_RETURN` | 300 | chat shrinking back into its row (exception) | §5.4 | `KitMotion.rowReturn` |
| `GLOW_FADE` | 450 | the glow's strength easing in and out | §5.3 | `edgeLightHueFade` |
| `FROST_FADE` | 180 | chat header turning from solid to frost while content scrolls under it | §5.15 | `KitFrostHeader` |
| `STYLE_FADE` / `STATE_FADE` | 200 / 120 | a surface's fill, edge and shadow when the Style or a state changes | §5.15, B-KIT-046 | `kitSurfaceBox` |
| `PRESS_HOLD` | 100 | least time a pressed state stays visible (a state, not a movement; holds under Off) | §5.15 | `pressHold` |
| `STOP_IGNORE_AFTER_SEND` | 800 | Stop ignores taps for this long after a send or after it appears (fix `49d067f2d`) | §5.2 | composer |
| `PREDICTIVE_SPAN` | 300 | the time axis a predictive preview is seeked along; a committed pop finishes the remaining `(1 − progress) × 300` ms | §5.4, §5.9 | new (Material) |
| `ESCALATE_AFTER`, `UNDO_WINDOW`, `COPIED_HOLD`, `TYPING_SETTLE`, `HELD_ANSWER` | 8000, 8000, 2000, 300, 3000 | waits and holds, not motion; named so no literal appears | status, undo, copy, search, request card | Flutter constants; `DelayedAnswers.window` |
| `ARRIVAL_HOLD`, `TIP_HOLD`, `TIP_FADE` | 2400, 4000, 200 | how long an arrival wash and a tip stay; the tip's own fade | §5.8, §5.15 | `KitArrival.hold`, `KitTip.defaultHold`, `KitTip.fade` |

### 2.2 Easings

| Token | Compose easing | Use |
|---|---|---|
| `Enter` | `EaseOutCubic` (`androidx.compose.animation.core`) [V] | anything that runs forward in time: arrivals, fades in, word fade, lift, page fades, and a part leaving "forward" (Flutter rule: `enter` for any 0 → 1 progress) |
| `Exit` | `EaseInCubic` [V] | an accelerating exit only (a full-screen page sliding away, §5.9). Flutter's `exit` curve was used only in reverse slots; run forward in time it equals `EaseOutCubic` (worked in §5.4), so ordinary fade-outs use `Enter` |
| `Emphasized` | `PathEasing` of Material's emphasized path: `M0,0 C0.05,0 0.133333,0.06 0.166666,0.4 C0.208333,0.82 0.25,1 1,1` | the container transform's bounds, both directions (§5.4) |
| `PredictiveBack` | `CubicBezierEasing(0.1f, 0.1f, 0f, 1f)` | the predictive preview (Material) |
| `Pulse` | `CubicBezierEasing(0.42f, 0f, 0.58f, 1f)` | breathing (caret, step breathe, halo) |
| `Steady` | `LinearEasing` | sweeps and laps |
| `StepEase` / `StepOut` | `CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)` (Flutter `easeInOutQuad`) / `CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)` (`easeOutQuad`) | the step light only (§5.7) |
| `Land` | `CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)` (`easeOutBack`) | the step light's glyph return only (Flutter parity); drawings later (D11). Never controls. |

### 2.3 Springs

Compose `spring(dampingRatio, stiffness)`, unit mass. Settle time is to within 2 % of the target, computed from the
spring equation [E, motion-native §2.1 method].

| Token | ζ / stiffness | Settles in | Overshoot | Use |
|---|---|---|---|---|
| `SpatialFast` | 0.9 / 1400 | ≈ 126 ms | 0.2 % | Stop key slot, chevron turn, tab indicator, small drags released |
| `Spatial` | 0.9 / 700 | ≈ 178 ms | 0.2 % | page shared-axis travel, list placement, sheet settle (via `KitMotionScheme`), predictive-cancel return, composer height |
| `SpatialSlow` | 0.9 / 300 | ≈ 271 ms | 0.2 % | nothing at the switch; reserved for wide-screen panes |
| `LargeTravel` | 1.0 / 550 | ≈ 247 ms | 0 | full-height or full-width slides (FullScreenDialog, end-side sheet), where even 0.2 % would show as several pixels |
| `ReplyGrow` | 1.0 / 700 | ≈ 210 ms per new line | 0 | the live reply block growing (Flutter `replyGrow` (700, 53), ζ = 53 / (2·√700) ≈ 1.00) |
| `EffectsFast` / `Effects` / `EffectsSlow` | 1.0 / 3800, 1600, 800 | 95 / 146 / 206 ms | 0 | only inside `KitMotionScheme` for wrapped M3 parts' alpha and colour |

Where these come from: the M3 motion token files. `MotionScheme.standard()` uses exactly `SpatialFast`/`Spatial`/
`SpatialSlow` and the three effects springs; `MotionScheme.expressive()` differs only in its spatial springs
(0.6 / 800, 0.8 / 380, 0.8 / 200), which overshoot up to 9.5 % [V, motion-native §2.1]. This app takes the standard
set. The four Flutter tab-lens springs (`lensLead` and friends, ζ 0.62–0.81) are ported unchanged only for the rail's
selection lens, per section 06 §4.8; their flattening is section 06's owner question Q5 and is not repeated here.

### 2.4 Loops and rates

| Token | Value | Loop kind (§3.3) | Calm | Off |
|---|---|---|---|---|
| `GLOW_CLASSIC_LAP` | 3600 ms per lap (Normal); Slow ×2 = 7200; Fast ÷2 = 1800, never faster than 1800 (`glowMaxLapsPerSecond` = 1/1.8) | ambient | still ring | none (Saver: one still hairline) |
| `GLOW_SOFT_LAP` | 8000 ms; never faster than 6000 (`edgeLightMaxLapsPerSecond` = 1/6) | ambient | still at half strength | none |
| `HALO_BREATHS_PER_LAP` | 2 (halo alpha 0.15 ↔ 0.35) | ambient | held at 0.25 | none |
| `STOP_RING_LAP` | 2000 ms | progress, but only when the glow is not travelling (MOT-15) | still track | still track |
| `LIVE_SWEEP_LAP` | 2200 ms; Calm 4400 ms | progress | half speed | still words |
| `STEP_LAP` / `STEP_LAP_CALM` | 2000 / 4000 ms | progress | half speed | still node |
| `STEP_BREATHE`, `STEP_FLASH`, `STEP_TICK`, `STEP_SPRING`, `STEP_FADE` | 1600, 300, 600, 260, 200 ms | one-shots of the step light | as Full | none |
| `CARET_BREATH` | 1100 ms per breath | ambient | still caret | still caret |
| `SPINNER_LAP` | 1333 ms (M3 indeterminate cycle) | progress | half speed | still dot |
| `CONTEXT_VALUE`, `CONTEXT_COLOR`, `CONTEXT_HALO`, `CONTEXT_DRAIN`, `CONTEXT_SPIN` | 600, 320, 700, 900, 1200 ms | value changes of the context ring (progress representation, kept for parity) | fades only | instant |
| `REFRESH_BREATH` | 4000 ms (`breath`) | ambient | still portal | still portal |
| Reserved (not at the switch, D11) | `ENTRANCE` 900, `CELEBRATION` 1400, `BREATH` 4000, live background 12 ticks/s with 30 s idle rest | ambient | — | — |

### 2.5 Distances, scales and ratios

| Token | Value | Use |
|---|---|---|
| `PAGE_TRAVEL` | 30 dp | shared-axis X page travel (Flutter `KitPageTransitionsBuilder.travel`) |
| `PAGE_FADE_OUT_SHARE` | 0.3 | the leaving page fades out in the first 30 % of `STANDARD` (75 ms) |
| `ENTRANCE_RISE` | 8 dp (`space2`) | entrances, jump pill, late-arriving feed rows, undo bar (Full only) |
| `MENU_DROP` | 8 dp | a menu drops from its anchor (rises when it opens above) (Full only) |
| `TIP_RISE` | 4 dp | a tip rises toward its anchor (Full only) |
| `COUNT_ROLL_SHARE` | 0.6 | a rolling count travels 60 % of its own height |
| `MARK_FROM` | 0.9 | a mark (check, dot, tick) never appears from nothing: it fades in from 0.9 scale (Full only) |
| `LIFT_START_SCALE` | 0.92 | the sent bubble's starting scale (exception) |
| `PREDICTIVE_MIN_SCALE` | 0.9 | predictive preview end scale |
| `PREDICTIVE_FADE_AT` | 0.35 | the preview starts fading at 35 % of the span |
| `PREVIEW_CORNER` | 28 dp | the preview card's corners at full progress |
| `ROW_CORNER` | 20 dp (`bubbleCorner`) | the growing box's corners at the row end of the transform |
| `DIM_MOST` | 0.32 | the dim over the page under a growing chat or a sheet (`roles.scrim` at this alpha) |
| `ROW_BOUNDS_SHARE` / `ROW_CONTENT_IN` / `ROW_HANDOFF` | 0.8 / [0.2, 0.6] / [0, 0.2] of `ROW_OPEN` | the transform's three tracks (§5.4) |
| `ROW_CONTENT_OUT` / `ROW_HANDBACK` | [0, 0.4] / [0.8, 1] of `ROW_RETURN` | the return's tracks |
| `REVEAL_BASE_CPS` | 45 characters per second | the streaming reveal's resting pace |
| `REVEAL_MAX_LAG` | 250 ms | the reveal never falls further behind than this |
| `DESCENDER_ALLOWANCE` | 4 dp | the growing block's clip leaves room for descenders (`2d9a65b45`) |
| `AWAY_FROM_LATEST` | 480 dp | the transcript is "away from latest" past this distance from the newest end |
| `FOLLOW_SLACK` | 48 dp | still counts as "at the live edge" within this distance |
| `GLOW_STROKE` / `HALO_BLUR` / `HALO_SPREAD` | 1.6 dp / 18 dp / 1 dp | classic glow ring and halo |

### 2.6 The named exceptions (the only motions over 250 ms or with scale)

| # | Motion | Value | Owner source |
|---|---|---|---|
| X1 | Chat grows out of its row | 450 ms (bounds 0–360 ms) | 2026-10-27 chat motion; MOT-2 |
| X2 | Chat shrinks back into its row | 300 ms | same |
| X3 | Sent bubble lifts out of the composer from 0.92 scale | 250 ms, scale | same; MOT-2 |
| X4 | Reply box grows to each new height | spring, ≈ 210 ms per line | same; MOT-5 |
| X5 | Work-line fold and step arrival (layout animation inside a scrolling list) | 250 / 150 ms | same; MOT-5 |
| X6 | Glow fade in/out | 450 ms | composer glow (owner likes it, 2026-10-14) |
| X7 | Predictive back preview (scale to 0.9) | follows the finger; commit finishes in ≤ 300 ms | platform behaviour, owner rule 2026-10-26; MOT-2 exception |
| X8 | Loops (glow lap, sweep, step light, Stop ring, caret, spinner) | §2.4 | each a Flutter feature the owner has seen; MOT-6, MOT-15 |
| X9 | Context ring value moves (600–1200 ms) | §2.4 | Flutter parity; a value shown as progress, not a transition |

Nothing else may exceed 250 ms or scale. A new exception needs an owner decision recorded in this table.

### 2.7 Code: tokens, state and spec builders

```kotlin
// ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/motion/MotionTokens.kt  (no Compose)
package io.github.eslamasabry.oc.ui.design.motion

public object MotionTokens {
    public const val QUICK: Int = 150
    public const val STANDARD: Int = 250
    public const val ROW_OPEN: Int = 450
    public const val ROW_RETURN: Int = 300
    public const val GLOW_FADE: Int = 450
    public const val FROST_FADE: Int = 180
    public const val STYLE_FADE: Int = 200
    public const val STATE_FADE: Int = 120
    public const val PRESS_HOLD: Int = 100
    public const val STOP_IGNORE_AFTER_SEND: Int = 800
    public const val PREDICTIVE_SPAN: Int = 300
    // loops (ms per lap / breath)
    public const val GLOW_CLASSIC_LAP: Int = 3600
    public const val GLOW_MIN_LAP: Int = 1800
    public const val GLOW_SOFT_LAP: Int = 8000
    public const val GLOW_SOFT_MIN_LAP: Int = 6000
    public const val STOP_RING_LAP: Int = 2000
    public const val LIVE_SWEEP_LAP: Int = 2200
    public const val STEP_LAP: Int = 2000
    public const val STEP_BREATHE: Int = 1600
    public const val STEP_FLASH: Int = 300
    public const val STEP_TICK: Int = 600
    public const val STEP_SPRING: Int = 260
    public const val STEP_FADE: Int = 200
    public const val CARET_BREATH: Int = 1100
    public const val SPINNER_LAP: Int = 1333
    // distances (dp) and ratios
    public const val PAGE_TRAVEL_DP: Int = 30
    public const val ENTRANCE_RISE_DP: Int = 8
    public const val MENU_DROP_DP: Int = 8
    public const val TIP_RISE_DP: Int = 4
    public const val PREVIEW_CORNER_DP: Int = 28
    public const val ROW_CORNER_DP: Int = 20
    public const val DESCENDER_ALLOWANCE_DP: Int = 4
    public const val AWAY_FROM_LATEST_DP: Int = 480
    public const val FOLLOW_SLACK_DP: Int = 48
    public const val PAGE_FADE_OUT_SHARE: Float = 0.3f
    public const val COUNT_ROLL_SHARE: Float = 0.6f
    public const val MARK_FROM: Float = 0.9f
    public const val LIFT_START_SCALE: Float = 0.92f
    public const val PREDICTIVE_MIN_SCALE: Float = 0.9f
    public const val PREDICTIVE_FADE_AT: Float = 0.35f
    public const val DIM_MOST: Float = 0.32f
    public const val REVEAL_BASE_CPS: Float = 45f
    public const val REVEAL_MAX_LAG_MS: Int = 250
    // springs: (dampingRatio, stiffness)
    public const val SPATIAL_FAST_K: Float = 1400f; public const val SPATIAL_K: Float = 700f
    public const val SPATIAL_SLOW_K: Float = 300f;  public const val SPATIAL_ZETA: Float = 0.9f
    public const val LARGE_TRAVEL_K: Float = 550f;  public const val REPLY_GROW_K: Float = 700f
}
```

```kotlin
// ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/motion/KitMotion.kt
package io.github.eslamasabry.oc.ui.kit.motion

/** What the kit may move right now. Built once by KitTheme from MotionPolicy (§3); read only by kit parts. */
@Immutable
public data class KitMotionState(
    val level: MotionLevel,
    /** Tests, screenshots and the gallery: loops draw one fixed frame (§9.4). */
    val stillFrame: Boolean = false,
    /** Gallery and debug builds only: slows every kit animation for review (1 = normal). */
    val debugTimeScale: Float = 1f,
) {
    val reduced: Boolean get() = level == MotionLevel.Off
    val travels: Boolean get() = level == MotionLevel.Full
    val ambientLoops: Boolean get() = level == MotionLevel.Full && !stillFrame
    /** Lap-speed multiplier for progress loops: 1 Full, 0.5 Calm, 0 Off (still). */
    val progressLoopSpeed: Float get() = if (stillFrame) 0f else when (level) {
        MotionLevel.Full -> 1f; MotionLevel.Calm -> 0.5f; MotionLevel.Off -> 0f
    }
}

/** The only place tween/spring/keyframes are created (Konsist: §4 rule R1). */
@Stable
public class KitMotionSpecs internal constructor(private val s: KitMotionState) {
    private fun ms(v: Int) = (v * s.debugTimeScale).toInt()
    private fun k(stiffness: Float) = stiffness / (s.debugTimeScale * s.debugTimeScale)

    public fun <T> enter(): FiniteAnimationSpec<T> = if (s.reduced) snap() else tween(ms(STANDARD), easing = EaseOutCubic)
    public fun <T> quick(): FiniteAnimationSpec<T> = if (s.reduced) snap() else tween(ms(QUICK), easing = EaseOutCubic)
    public fun <T> exit(): FiniteAnimationSpec<T> = if (s.reduced) snap() else tween(ms(QUICK), easing = EaseOutCubic)
    public fun <T> spatialFast(): FiniteAnimationSpec<T> = if (s.reduced) snap() else spring(SPATIAL_ZETA, k(SPATIAL_FAST_K))
    public fun <T> spatial(): FiniteAnimationSpec<T> = if (s.reduced) snap() else spring(SPATIAL_ZETA, k(SPATIAL_K))
    public fun <T> largeTravel(): FiniteAnimationSpec<T> = if (s.reduced) snap() else spring(1f, k(LARGE_TRAVEL_K))
    public fun <T> replyGrow(): FiniteAnimationSpec<T> = if (s.reduced) snap() else spring(1f, k(REPLY_GROW_K))
    public fun <T> glowFade(): FiniteAnimationSpec<T> = if (s.reduced) snap() else tween(ms(GLOW_FADE), easing = EaseOutCubic)
    /** One lap of a loop; callers create it only when KitMotionState allows that loop class (R4). */
    public fun lap(lapMillis: Int): InfiniteRepeatableSpec<Float> =
        infiniteRepeatable(tween(ms(lapMillis), easing = LinearEasing), RepeatMode.Restart)
    /** A movement that only Full may make; null = do not move (Calm and Off fade or jump instead). */
    public fun <T> travel(): FiniteAnimationSpec<T>? = if (s.travels) spatial() else null
    public val state: KitMotionState get() = s
}

public object KitMotion {
    public val current: KitMotionSpecs
        @Composable @ReadOnlyComposable get() = LocalKitMotionSpecs.current
    public val state: KitMotionState
        @Composable @ReadOnlyComposable get() = LocalKitMotion.current
}

public object KitEasing {
    public val Enter: Easing = EaseOutCubic
    public val Exit: Easing = EaseInCubic
    public val Emphasized: Easing = PathEasing(Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    })
    public val PredictiveBack: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
    public val Pulse: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    public val Steady: Easing = LinearEasing
}

/** The standard M3 numbers; snap under Off. Passed to MaterialTheme(motionScheme = …) by KitTheme. */
internal class KitMotionScheme(private val off: Boolean) : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = if (off) snap() else spring(0.9f, 700f)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = if (off) snap() else spring(0.9f, 1400f)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = if (off) snap() else spring(0.9f, 300f)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = if (off) snap() else spring(1f, 1600f)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = if (off) snap() else spring(1f, 3800f)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = if (off) snap() else spring(1f, 800f)
}
```

`MotionScheme` is a public interface in M3 1.5.0-alpha22 [V, javap]; that it may be implemented outside Material is
checked when the kit compiles in slice 0 (K0). `snap()` returns a `SnapSpec`, which is a `DurationBasedAnimationSpec`
and therefore a `FiniteAnimationSpec` [U: compile check in K0].

---

## 3. Motion levels and reduced motion

### 3.1 Inputs

| Input | Source on Android | Live? | Stored where |
|---|---|---|---|
| System animator scale | `Settings.Global.ANIMATOR_DURATION_SCALE` via a `ContentObserver` on `Settings.Global.getUriFor(ANIMATOR_DURATION_SCALE)` in `AndroidKitPlatform` | yes (the demo read it once; that was a bug) | system |
| Accessibility "Remove animations" | sets all three animator scales to 0, so it arrives through the line above | yes | system |
| User Motion choice | Settings › Appearance › Motion: `oc.effectsMotion` = `full`/`calm`/`off`; a stored `oc.effectsCelebrations = false` with `full` reads as `calm`; unknown reads as `full` (B-KIT-006, B-STATE-003, B-MIGR-005) | yes | `:engine:settings` over the adopted Flutter prefs file |
| Saver (app appearance mode) | `oc.appearance = saver` | yes | same |
| Battery saver | `PowerManager.isPowerSaveMode` + `ACTION_POWER_SAVE_MODE_CHANGED` | yes | system |
| Heat | `PowerManager.addThermalStatusListener`, status ≥ `THERMAL_STATUS_SEVERE` | yes | system |
| Glow switch | `oc.effectsActivityGlow` (default on), `oc.effectsGlowStyle` (classic default), `oc.effectsGlowColours`, `oc.effectsGlowSpeed` (B-CHAT-006) | yes | same as Motion |

iOS (later): `UIAccessibility.isReduceMotionEnabled` + its notification maps to scale 0. Desktop: none (scale 1).

### 3.2 The resolver (pure Kotlin, `:ui:design`)

Section 06 §4.8 owns this function. Restated so this section reads alone:

```kotlin
public enum class MotionLevel { Full, Calm, Off }
public data class MotionSignals(val systemScale: Float, val powerSave: Boolean, val severeHeat: Boolean)

public fun resolveMotion(s: MotionSignals, user: MotionLevel, saver: Boolean): MotionLevel = when {
    s.systemScale == 0f || user == MotionLevel.Off || saver -> MotionLevel.Off      // system always wins; Saver = motion off
    s.severeHeat || s.powerSave || user == MotionLevel.Calm -> MotionLevel.Calm     // MOT-13
    else -> MotionLevel.Full
}
```

A non-zero system scale other than 1 (developer options 0.5× or 2×) is not a level change. Compose already multiplies
every composition-scoped animation by it through `MotionDurationScale` [V, `WindowRecomposer.android.kt`]. Our own
`withFrameNanos` loops (reveal clock, §5.1) read `KitPlatform.signals.animatorScale` and multiply their rates by
`1 / scale` so they agree.

### 3.3 Motion classes and what each level allows

Every kit animation belongs to exactly one class. A part's KDoc `Motion:` line (section 06 §14.4) names its class.

| Class | Examples | Full | Calm | Off (user, Saver, or system 0) |
|---|---|---|---|---|
| **Fade** | alpha, colour, cross-fade, word fade | yes | yes | instant |
| **Fold** | a part's height (`KitReveal`, work-line fold, reply grow, sheet step) | yes | yes | instant |
| **Travel** | translation, rise, drop, roll, slide, chevron turn, lift, list placement, page slide | yes | becomes a cross-fade of `QUICK` | instant |
| **Transform** | container transform, page shared axis, slide-up page | yes | cross-fade `QUICK` (route), none for the shared bounds | instant |
| **Follow** | anything under the finger: sheet drag, pull distance, predictive preview, overscroll stretch, IME | follows | follows | sheet and pull follow; predictive preview is **not drawn** (back commits on release); stretch is the platform's |
| **Settle** | the spring after a finger lets go: sheet settle, predictive cancel, refresh disc return | spring | spring | snap |
| **Progress loop** | live-line sweep, step light, spinner, Stop ring (when it may move) | full speed | half speed | still frame |
| **Ambient loop** | glow travel, halo breath, caret breath, refresh breath | runs | still frame | none or still (per part) |
| **Ripple** | press feedback | platform ripple | platform ripple | in-app Off: flat pressed fill; system 0: the platform's own static state |
| **Haptic `Done`** | a waited-for finish | yes | yes | skipped (Flutter parity) |

Other haptics are not motion and fire at every level (they obey the Vibration choice and the system touch setting).

### 3.4 How "Off" reaches every animation

Compose has no single switch for an in-app Off while the system animates [R motion-native §9.1]. Four mechanisms
together make it total:

1. `KitMotionSpecs` returns `snap()` for every spec under Off (§2.7). Every kit animation takes its spec from there
   (Konsist rule R1, §4).
2. `KitMotionScheme(off = true)` is passed to `MaterialTheme(motionScheme = …)`, so the wrapped M3 parts (sheet,
   switch, pull-to-refresh state, slider) snap too.
3. `:ui:shell` route specs return `EnterTransition.None togetherWith ExitTransition.None` and the predictive spec returns
   no transform (§5.4, §5.9).
4. Loops are never started: a loop composable checks `KitMotion.state` before it creates its
   `rememberInfiniteTransition` (§4 rule R4). Relying on `InfiniteTransition`'s behaviour at scale 0 is not allowed,
   because that behaviour is unverified [U, spike S8-5].

The system "Remove animations" path additionally gets Compose's own `MotionDurationScale(0)`, which ends every
composition-scoped animation on the next frame [V].

### 3.5 Lifecycle and visibility

- Every loop runs only while its part is composed **and** the window is at least `STARTED`
  (`LocalLifecycleOwner.current.lifecycle.currentStateAsState() >= STARTED`). A covered page (under another entry
  after its transition settled) stops its loops: `:ui:shell` provides `LocalKitPageVisible` per entry, false once the
  entry is fully covered.
- `withFrameNanos` loops park when idle (no frame requested); the reveal clock parks between bursts (§5.1).
- Nothing animates in the background: no frame callbacks while the activity is stopped (Choreographer stops), and no
  timers stand in for animations (§02 S-17).

### 3.6 Tests for the policy (write first, `commonTest`)

| Test class | Catalog rows | Asserts |
|---|---|---|
| `MotionPolicyTest` | B-KIT-006, B-KIT-008, B-STATE-003 | every row of the resolver table: scale 0 wins; Off; Saver → Off without changing the stored choice; power save → Calm; severe heat → Calm; Full otherwise |
| `MotionPrefMigrationTest` | B-KIT-006, B-STATE-003, B-MIGR-005 | `full` + `oc.effectsCelebrations = false` reads Calm; unknown reads Full; writing Full clears the old celebration key |
| `MotionClassTableTest` | B-KIT-054 | the §3.3 table as data: for each class and level, the expected behaviour flag; every kit motion part is registered with exactly one class (the manifest lists them) |
| `KitMotionSpecsTest` | B-KIT-023 | under Off every builder returns `SnapSpec`; under Calm `travel()` is null; `debugTimeScale` scales durations and stiffness |
| `MovingPartTest` | MOT-15 (Flutter `kit_composer_moving_part_test.dart`) | §5.3 resolver table |

---

## 4. Rules for all motion code

| # | Rule | Enforced by |
|---|---|---|
| R1 | `tween(`, `spring(`, `keyframes`, `infiniteRepeatable(`, `PathEasing(`, `CubicBezierEasing(`, `Easing {`, `snap(` appear only in `:ui:kit` `KitMotion.kt`, `KitMotionScheme.kt`, `KitEasing.kt` and `:ui:shell` `ShellMotion.kt` (route specs need `ContentTransform` builders). Every other kit, kit-chat and shell file gets specs from `KitMotion.current`. | Konsist `KitInternalRulesTest` (section 06 §14.5); `LiteralBanTest` bans `\d+\.milliseconds`, `tween\(\d`, `delay\(\d` in UI code |
| R2 | Features import no animation API at all (ADR §6.3 rule 1). They choose a route class and kit parts. | Konsist feature allowlist |
| R3 | Animated values are read in the **draw or layout phase**: `Modifier.graphicsLayer { }` (lambda form), `Modifier.offset { }`, `drawBehind`, `drawWithCache`, `layout { }`. Reading an animated `State` in composition is a review rejection, except for one-shot `AnimatedVisibility`/`AnimatedContent` switches. | review + `RecompositionBudgetTest` (§9.3) |
| R4 | Infinite animations use `rememberInfiniteTransition` or `withInfiniteAnimationFrameNanos`, never `while (true) { delay() }`. They are created only when `KitMotion.state` allows that loop class (§3.3). | Konsist (bans `while (true)` + `delay(` in kit motion files); the Compose test harness pauses framework loops but a hand loop hangs it [R testing-perf §4.2] |
| R5 | No layout animation inside a scrolling list item, except the named exceptions X4 and X5 (MOT-5). `animateContentSize` is banned in `:ui:kit-chat` except in `GrowOnly` (§5.1). | Konsist |
| R6 | Lazy items set `placementSpec = null` unless the list kind in §5.6 says otherwise. | kit list helpers own `animateItem`; features never call it |
| R7 | Text that is translated or scaled by an animation sets `TextStyle(textMotion = TextMotion.Animated)` only for that animation (the lifting bubble); everything else keeps `TextMotion.Static` [V release-note guidance]. | review |
| R8 | A part's first composition shows its final state; no entrance plays for content that was there when the screen opened (Flutter "first build is final"). | each part's UI test (§9.3) |
| R9 | No animation is driven by a timer (`delay` then set state) where a transition state exists. The one gate that needs "after the transition" (§5.4) listens to the transition. | review; the demo's 700 ms latch is the counter-example |
| R10 | Haptics only through `KitHaptic` and only for the MOT-11 list (§6). | Konsist bans `performHapticFeedback`, `LocalHapticFeedback` outside `AndroidKitPlatform` |
| R11 | `LocalOverscrollFactory` and `ScrollableDefaults.flingBehavior()` are never replaced app-wide. | Konsist |
| R12 | Every motion part reports itself to the test-only `LocalKitMotionProbe` (a no-op in release) when it starts and ends an animation, so tests can assert "nothing is running" (MOT-7). | `ReducedMotionSettlesTest` |

---

## 5. Interaction specs

Each spec gives: what the person sees; the exact values; how it is built; each level; RTL and accessibility; the
frame budget; how it is verified; the catalog rows; the owning module. "Full/Calm/Off" refer to §3.3.

### 5.1 Streaming text reveal (and the reply grow, caret, follow-scroll, jump pill)

**What the person sees.** New words appear word by word at a calm reading pace, each fading from transparent to full
in 250 ms. The reply's box grows smoothly to each new line. A thin accent caret sits after the last word and breathes
once a second. While the person is at the newest end, the list follows; when they scroll up, it stops following and a
"Jump to latest" pill appears.

**Pipeline** (section 09 owns the data half; the motion half lives in `:ui:kit-chat` `stream/`):

1. `TranscriptEngine` publishes at most every 50 ms; the first delta flushes at once (ADR §9.3).
2. The live text part's tail block is one lazy item keyed `"b:$partId:$startOffset"`; only it re-lays out.
3. `RevealState` (one per live text part, held by the kit-chat part, keyed by part id) keeps:
   - `shown: Int` — how many characters of the tail block are laid out;
   - `runs: MutableList<FreshRun(start, end, bornNanos)>` — ranges still fading;
   - `frameNanos: Long` — the clock the draw phase reads.
4. **Reveal clock** (a `withFrameNanos` loop; parks when there is nothing to reveal or fade):
   - rate = `max(REVEAL_BASE_CPS, backlog / 0.25 s)` characters per second, where backlog = text length − `shown`; so
     the reveal never lags more than `REVEAL_MAX_LAG` (250 ms) behind the network;
   - each frame adds `rate × dt` to a carry, `dt` capped at 50 ms;
   - the new `shown` is snapped **forward to the end of a word** (whitespace or punctuation), so layout happens only
     when a whole word lands (about 8–20 times a second);
   - a newly revealed range becomes a `FreshRun` born at that frame.
5. **Word fade** (draw phase only): the live block's `Text` draws fully; a `drawWithCache` layer on an offscreen
   `graphicsLayer` draws, for each `FreshRun`, the glyph path from `TextLayoutResult.getPathForRange(start, end)` with
   `BlendMode.DstOut` at alpha `1 − EaseOutCubic(age / 250 ms)`. `DstOut` lowers the text's own alpha, so it works on
   any surface (no painted veil). The offscreen layer is removed as soon as `runs` is empty. Never per-frame
   `AnnotatedString` colour spans (they re-lay out the paragraph every frame [V rendering-chat §6.1]).
6. **Reply grow**: `Modifier.growOnly(spec = KitMotion.current.replyGrow(), enabled = isLiveBlock)` on the live block
   item. It animates **growth only** (a shrink, e.g. a rewrap, is taken at once), clips with a
   `DESCENDER_ALLOWANCE` of 4 dp, and carries velocity when another line lands mid-way (Flutter `_KitMdGrow`). It is a
   custom `LayoutModifierNode`, not `animateContentSize`, because `animateContentSize` also animates shrink.
7. **Caret** (`KitStreamCaret`): drawn in `drawBehind` at `textLayout.getCursorRect(shown)`; 2 dp wide (drawn, takes no
   layout width), no taller than the line, in `accent`; breathes alpha 1 ↔ 0.35 over `CARET_BREATH` (1100 ms) on
   `Pulse` easing under Full; still at alpha 1 under Calm and Off. No caret after a code block, a table or pictures.
   Removed when the part ends (`session.text.ended` / `time.end`) (B-CHAT-060).
8. **Frame rate**: `highFrameRate(active = runs.isNotEmpty() || growing)` on the live block, Full only (M8-16).
9. **Selection**: the live block sits under `DisableSelection` until the Compose 1.13 selection fix is stable
   (`JETPACK_AHEAD` row in section 06 §6.1).

```kotlin
// ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/stream/RevealState.kt  (sketch)
@Stable
internal class RevealState {
    var shown by mutableIntStateOf(0); private set
    val runs = mutableStateListOf<FreshRun>()
    var frameNanos by mutableLongStateOf(0L); private set

    suspend fun drive(text: () -> CharSequence, motion: () -> KitMotionState, animatorScale: () -> Float) {
        snapshotFlow { text().length > shown || runs.isNotEmpty() }.collectLatest { busy ->
            if (!busy) return@collectLatest                                  // parked: no frames while idle
            var last = 0L; var carry = 0f
            while (true) {
                val more = withFrameNanos { now ->
                    val t = text(); val m = motion()
                    if (m.reduced) { shown = t.length; runs.clear(); return@withFrameNanos false }
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f) / animatorScale().coerceAtLeast(0.01f)
                    last = now
                    val backlog = t.length - shown
                    carry += maxOf(REVEAL_BASE_CPS, backlog / (REVEAL_MAX_LAG_MS / 1000f)) * dt
                    val want = shown + carry.toInt(); carry -= carry.toInt()
                    val next = t.wordEndAtOrAfter(want).coerceAtMost(t.length)
                    if (next > shown) { runs += FreshRun(shown, next, now); shown = next }   // layout: a word landed
                    frameNanos = now                                                         // draw-only invalidation
                    runs.removeAll { now - it.bornNanos > STANDARD * 1_000_000L }
                    t.length > shown || runs.isNotEmpty()
                }
                if (!more) break
            }
        }
    }
}

internal fun Modifier.freshFade(layout: () -> TextLayoutResult?, state: RevealState): Modifier =
    graphicsLayer { compositingStrategy = if (state.runs.isEmpty()) CompositingStrategy.Auto else CompositingStrategy.Offscreen }
        .drawWithCache {
            val path = Path()
            onDrawWithContent {
                drawContent()
                val tl = layout() ?: return@onDrawWithContent
                val now = state.frameNanos                                         // read in draw only
                for (r in state.runs) {
                    val age = ((now - r.bornNanos) / (STANDARD * 1e6f)).coerceIn(0f, 1f)
                    val hide = 1f - EaseOutCubic.transform(age)
                    if (hide <= 0f) continue
                    path.reset(); path.addPath(tl.getPathForRange(r.start, r.end))
                    drawPath(path, Color.Black, alpha = hide, blendMode = BlendMode.DstOut)
                }
            }
        }
```

`compositingStrategy` is read inside the `graphicsLayer` lambda, so switching it does not recompose [V: lambda form is
draw-phase]. `shown` changes only when a word lands, so the tail `Text` recomposes at most at the word rate.

**Follow-scroll.** `LazyColumn(reverseLayout = true)`:

- "At the live edge" = `firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset ≤ FOLLOW_SLACK` (48 dp).
- While following, growth of the bottom item keeps its bottom glued for free; a **new** item inserted at index 0 would
  land out of view (the list anchors on the old first key [V]), so the kit calls `listState.requestScrollToItem(0)` in the
  same snapshot as the insert. No animation; the grow (step 6) makes the hop smooth.
- After the person sends: if index 0 is within one viewport, `animateScrollToItem(0)` (a glide); otherwise
  `scrollToItem(0)` (a jump) (Flutter `_followAfterSend`). Under Off: always the jump.
- The person's own drag (an `interactionSource` drag) turns following off; reaching the live edge or tapping the pill
  turns it on.
- Bottom clearance for the composer is the measured spacer item at index 0 (§5.12), so the newest words clear the
  composer, never flush under it (`8c614c58d`).

**Jump pill** (`KitJumpPill`): visible when `derivedStateOf { distance from newest > AWAY_FROM_LATEST (480 dp) }`;
enters with fade `STANDARD` + rise `ENTRANCE_RISE` (8 dp, Full only) on `Enter`; leaves with fade `QUICK` + sink 8 dp
(Full only). Tap: if the newest item is within two viewports, `animateScrollToItem(0)`; else `scrollToItem(2)` then
`animateScrollToItem(0)` (jump close, glide the last stretch). No haptic (MOT-11: plain tap).

**Sent bubble lift** (X3, `KitMessageLift`): when the composer accepts a send it records the field's window rect
(`onGloballyPositioned`) in the kit-chat `LiftChannel` (`mark(rect)`). The new prompt item `claim()`s it within 1 s of
its first layout. The bubble is laid out in its final place from the first frame; only `graphicsLayer` moves:
`translationY = max(0, from.centerY − place.centerY) × (1 − p)`, `scale = 0.92 + 0.08 × p`, `alpha = min(1, 2p)`,
transform origin = bottom-**end** (bottom-right in LTR, bottom-left in RTL), `p` animated over `STANDARD` on `Enter`.
It rises from behind the composer, which stays drawn above it with its glow unchanged. Calm: alpha only, in place. Off:
no lift. The prompt item's `animateItem` fade-in is null while a lift claim exists (no double fade). `TextMotion.Animated`
on the bubble's text during the lift only.

**Levels.** Full: everything above. Calm: paced reveal, word fade and grow stay (Fade and Fold classes); caret still;
lift is a fade in place; follow glides. Off: text appears as published (50 ms coalescing kept), no fade, no grow, no
caret breath, follow jumps.

**Accessibility.** No `liveRegion` on streaming text; the live line announces the reply's end once (section 09).
Reading text scales fully with the system; nothing here depends on the 1.3× frame cap.

**Budget (120 Hz).** Fade-only frames: ≤ 2 ms UI thread. Word frames (≤ 20/s): ≤ 4 ms (one block re-layout + list
placement). §02 S-9: `frameOverrunMs` p95 ≤ 0, p99 ≤ 4 ms on the NX721J.

**Verified by.** `RevealClockTest` (pure: pace 45 cps when caught up; lag never above 250 ms for a 4,000-character
burst; whole-word snapping; parks when idle; Off reveals all at once), `GrowOnlyTest` (growth animates, shrink is
instant, velocity carried), frame captures `stream_reveal_{0,80,160,250}ms` (§9.4), `StreamingBenchmark` (§9.5 J2),
`TailRelayoutCounterTest` (one block re-laid out per tail update on a 200-block reply, §02 S-9). Rows: B-CHAT-060,
B-CHAT-096 (merge), E2E-05.

**Owner.** `:ui:kit-chat` owner; Opus agent for `RevealState`/`freshFade`/`growOnly` (hardest render work).

### 5.2 Send → Stop (the composer's controls)

**Current owner decision** (2026-10-30, overrides 11B): Stop is **not** in the field. The field's trailing button is
mic while the field is empty and Send once there are words. Stop is a separate key at the trailing end of the chip row
above the field for the whole running turn; it turns amber when the turn looks stuck. There is no Stop on the live
line. So "send-to-stop" is a hand-off between two keys, specified here.

**Sequence when the person sends** (times from the moment Send acts):

| t | What happens | Spec |
|---|---|---|
| 0 | Send acts on touch-down, or on **lift** when its long-press menu exists ("Send later or repeat", fix `ee0815e9f`). `KitHaptic.Send` fires. The pressed ripple shows for at least `PRESS_HOLD`. | §6 |
| 0–250 ms | The sent bubble lifts (§5.1). | X3 |
| on accept | The host clears the field (never the kit, DATA-1). The trailing glyph cross-fades **Send → mic** over `QUICK` (both glyphs on the same accent circle; no scale, MOT-2). | Fade |
| on accept | **Stop arrives**: its slot opens from 0 to 48 dp wide on `SpatialFast` (≈ 126 ms) while the key fades in over `QUICK`, starting when the slot is 40 % open. The chips before it do not move if the row has free space (the row's flexible spacer absorbs the slot); if the row is full, they shift with the slot. Calm: the slot appears at once, the key fades. Off: instant. | Travel + Fade |
| on accept | Stop ignores taps for `STOP_IGNORE_AFTER_SEND` (800 ms), so a double tap on Send can never stop the turn and its sub-agents (fix `49d067f2d`). Its pressed state still shows. | guard |
| on accept | The glow fades in over `GLOW_FADE` (§5.3). | X6 |
| running | The moving part (§5.3): the glow travels, or (glow off) Stop's ring turns. | MOT-15 |
| stuck | The Stop key's fill cross-fades to `attention` (amber) over `QUICK`. | Fade |
| turn ends | Stop fades out over `QUICK`, then its slot closes on `SpatialFast`. The glow fades out over `GLOW_FADE`. `KitHaptic.Done` fires once on the busy → idle edge (skipped under Off; CHAT-GENUI-56). | Fade + Travel |

**Stop key look.** A 40 dp neutral circle (`text1` fill, `ground` square glyph; never red outside Signal, R2) inside a
48 dp target. The ring: 2 dp stroke at 2 dp outside the circle; the track is `text3` at 30 % alpha; the moving arc is 90°
of `text1`. When the ring may move (§5.3), the arc turns one lap per `STOP_RING_LAP` (2000 ms) on `Steady`,
clockwise in both directions. Otherwise only the track shows. The ring is drawn in `drawBehind` with
`rotate(phase × 360)`; no recomposition per frame.

**Taps on Stop.** Tap = stop now: `KitHaptic.Commit` (a confirmed stop, MOT-11), pressed ripple. Long-press (only where
a send steers) = "Stop after this step": `KitHaptic.LongPress`, the platform long-press ripple, the live line's words
cross-fade to "Stopping after this step · Undo" (`KitSwap`, `QUICK`). The tip `stop-after-step` teaches the hold after a
reply has run 10 s (teaching registry; owner rule "teach what is hidden").

**While the turn runs and the field has words** (steer, never queue): the trailing button stays **Send** (accent), and
its delivery caption cross-fades to what Send will do ("Adds to this turn" / "Stops and sends" for agents that restart
the turn) over `QUICK`. Nothing else changes shape.

**While a request waits** ("Waiting for you"): the card owns the composer region (§5.8); Stop and the glow leave as
in "turn ends", without the Done haptic; they return when the answer is sent.

**Field button morphs** (all on the same 48 dp target, `QUICK` cross-fade, Full/Calm; instant under Off): mic ⇄ Send,
mic → voice-mode states (`KitComposerVoice`; section 10 owns the voice pill), Send → spinner while a send is
waiting for the transport (a 2 dp spinner, progress loop).

**Accessibility.** Each key has its own label ("Send", "Stop the reply", "Stop after this step" as a custom action).
The 800 ms guard does not hide the key from TalkBack.

**Budget.** A key arriving: ≤ 3 ms UI per frame (one row re-layout). The ring: draw-only, ≤ 0.3 ms.

**Verified by.** `StopGuardTest` (pure: taps inside 800 ms are ignored; the pressed state still shows), `MovingPartTest`,
`ComposerKeysUiTest` (desktop compose test: mic → Send on typing; Send → mic and Stop arrives after accept; Stop leaves at
idle; haptic sequence `[Send]`, then `[Done]` at idle, recorded by `FakeKitPlatform`), frame captures
`stop_arrive_{0,60,150}ms_{full,calm}`, `stop_ring_still`, `stop_stuck_amber`. Rows: B-CHAT-012, B-CHAT-086 (Continue
after a cut-off), the widget tests `chat_send_stop_fixes_test.dart` (later batch).

**Owner.** `:ui:kit-chat` owner (`KitComposer`, `KitComposerStatusStrip`, `KitStopKey`).

### 5.3 The running green glow around the composer

**What the person sees** (owner likes it, 2026-10-14; keep the hue). While a reply runs, a soft green glow surrounds
the composer pill: a bright highlight travels the border over a dim base ring, and a soft halo underneath breathes
twice per lap. It fades in when the reply starts and out when it ends.

**Default look: Classic, One colour, Normal speed** (B-CHAT-006; `oc.effectsGlowStyle` absent = classic):

| Element | Value |
|---|---|
| Ring | 1.6 dp stroke on the composer's own rounded rect (radius 26 dp; 18 dp on wide windows), inset by half the stroke |
| Ring colours | sweep gradient, colours `[base, primary, partner, primary, base, base]` at stops `[0, .10, .18, .26, .38, 1]`, where `base = primary @ 0.28`, `primary = roles.accent`, `partner = lerp(accent, text1, 0.55)` in dark and `lerp(accent, text1, 0.30)` in light (One colour); Two colours uses `roles.partner` (never amber) |
| Rotation | one lap per `GLOW_CLASSIC_LAP` (3600 ms) on `Steady`; Slow 7200, Fast 1800 (never faster) |
| Halo | drop shadow of the pill's shape in `primary`, blur 18 dp, spread 1 dp, alpha `0.25 − 0.10 × cos(4π × lapPhase)` (0.15 ↔ 0.35, two breaths per lap) |
| Strength | 0 → 1 over `GLOW_FADE` (450 ms) on `Enter` when a reply starts; 1 → 0 over 450 ms when it ends or a request starts waiting |
| Frost | the glow and halo sit outside the frost clip (D10) |

**Soft ring** (the other choice): a 2.2 dp line with a 12 dp wash (blur 7), peaks 0.75 / 0.22, one lap per 8000 ms
(never faster than 6000). Brutal draws a hard 2 dp ring with no halo; Paper a thin rule (style hooks, section 06).

**The one moving part (MOT-15).** One pure resolver, called by the composer frame and by the Stop key:

```kotlin
// ui/kit-chat/.../composer/MovingPart.kt
internal enum class MovingPart { Glow, StopRing, None }
internal fun movingPart(level: MotionLevel, glowOn: Boolean): MovingPart = when {
    level != MotionLevel.Full -> MovingPart.None       // Calm: still glow; Off: nothing moves
    glowOn -> MovingPart.Glow                           // the glow travels; Stop shows its still track
    else -> MovingPart.StopRing                         // glow switched off: Stop's ring turns
}
```

**Levels.** Full: travels and breathes. Calm: a still `primary` ring and the halo held at 0.25 (it reads as lit, not as
a frozen frame). Off: no glow. Saver (which forces Off): one still 1 dp hairline in `primary` while a reply runs.

**How it is built** (draw phase only; the composer never recomposes for the glow):

```kotlin
// ui/kit-chat/.../composer/ComposerGlow.kt  (sketch)
@Composable
internal fun Modifier.composerGlow(active: Boolean, look: GlowLook, shape: RoundedCornerShape): Modifier {
    val motion = KitMotion.state
    val specs = KitMotion.current
    val strength = remember { Animatable(0f) }
    LaunchedEffect(active, motion.level) {
        val target = if (active && motion.level != MotionLevel.Off) 1f else 0f
        strength.animateTo(target, specs.glowFade())
    }
    // rememberLapPhase = rememberInfiniteTransition().animateFloat(0f, 1f, specs.lap(look.lapMillis)); created only when allowed
    val phase = if (active && motion.ambientLoops && look.travels) rememberLapPhase(specs, look.lapMillis) else null
    return this
        .dropShadow(shape) {                                 // halo; block runs in the draw phase
            radius = 18.dp.toPx(); spread = 1.dp.toPx(); color = look.primary
            alpha = strength.value * (phase?.let { 0.25f - 0.10f * cos(4f * PI.toFloat() * it.value) } ?: 0.25f)
        }
        .drawWithCache {
            val ring = ringPath(size, shape, GLOW_STROKE.toPx())          // outer rrect minus inner rrect, cached per size
            val sweep = Brush.sweepGradient(look.colorStops, center)      // cached; rotation is a canvas transform
            onDrawWithContent {
                drawContent()
                val s = strength.value
                if (s == 0f) return@onDrawWithContent
                clipPath(ring) {
                    if (phase == null) drawRect(look.primary, alpha = s)          // still ring (Calm)
                    else rotate(phase.value * 360f) { drawCircle(sweep, radius = size.maxDimension, alpha = s) }
                }
            }
        }
}
```

Rotating the canvas inside a clip of the ring path turns the gradient while the ring's rounded-rect shape stays put; no
brush or shader is allocated per frame. `rememberLapPhase` wraps `rememberInfiniteTransition().animateFloat(0f, 1f,
specs.lap(lapMillis))`; its `State` is read only in the draw lambdas (R3). Under Saver (which forces Off) the part
draws the still 1 dp `primary` hairline instead, from the same modifier. The
halo's `Modifier.dropShadow(shape, block)` exists in Compose UI 1.12.1 [V]; if its cost on the NX721J fails the budget,
the fallback is the demo's three stacked strokes (16 / 9 / 2 dp at alpha 0.10 / 0.20 / 0.95) [S8-9].

**Budget.** Draw-only: ≤ 0.5 ms UI thread, ≤ 1.5 ms RenderThread per frame. No `highFrameRate` request: an ambient
loop may run at the display's Normal rate (≥ 60 Hz on adaptive-refresh devices) [R rendering-chat §7.1].

**Verified by.** `MovingPartTest`, `GlowColoursTest` (partner lerp per brightness; Two colours never amber), frame
captures `glow_{classic,soft}_{dark,light}_phase057` with `stillFrame` (phase 0.57 centres the highlight on the top edge,
Flutter `_Classic.framePhase`), `glow_calm`, `glow_saver_hairline`, `RecompositionBudgetTest` (the composer recomposes
0 times during 3 s of glow), `StreamingBenchmark` (the glow runs throughout J2). Rows: B-CHAT-006, B-KIT-002.

**Owner.** `:ui:kit-chat` owner.

### 5.4 Row → chat container transform, without breaking predictive back

**What the person sees.** Tapping a conversation row grows the chat out of that row's box into the full screen; the list
under it dims a little and holds still. Tapping Back (top-bar Back, 3-button Back, Esc) shrinks the chat back into its
row. The back **swipe** instead shows Android's own predictive preview (the chat card shrinks toward 90 % and follows the
finger); letting go finishes the pop, dragging back cancels it. The two never mix.

**Route class:** `GrowFromRow` (section 07 §3.1) for `ChatRoute.Chat` opened from a row that registered an origin
(Conversations, Inbox, All conversations). Opened any other way (notification, link, search): `Page`.

**Forward (tap), 450 ms (X1), Full only:**

| Track | Time | Spec |
|---|---|---|
| Bounds: row rect → full window | 0–360 ms | `sharedBounds` with `BoundsTransform { _, _ -> tween(360, easing = KitEasing.Emphasized) }` |
| Box corners | 20 dp → 0 along the bounds' progress | `clipInOverlayDuringTransition = OverlayClip(AnimatedCornerShape(progress))` (a `Shape` whose `createOutline` reads the corner `State`; shapes do not morph by themselves [V docs]) |
| Box ground | the chat's `ground` from frame 1 (the box is the row's surface becoming the page) | chat root background |
| Row content hand-off | row words fade out 0–90 ms on `Enter` | the row's `sharedBounds(exit = fadeOut(tween(90, easing = EaseOutCubic)))` |
| Chat content | fades in 90–270 ms on `Enter` | the chat's `sharedBounds(enter = fadeIn(tween(180, delayMillis = 90, easing = EaseOutCubic)))` |
| Chat layout | laid out once at full size, revealed by the growing box (no per-frame re-measure of the transcript, no scaling: MOT-2) | `Modifier.skipToLookaheadSize()` on the chat content |
| Dim over the list | 0 → `DIM_MOST` (0.32) of `roles.scrim`, 0–90 ms on `Enter` | the shell's list-entry decorator draws it from `transition.animateFloat` in `drawWithContent` |
| List | holds still; queued reorders wait (`f7e775dd3`) | kit feed list `frozen = true` while the gate is not Idle or a page covers the list (§5.6) |

Visible motion ends at 360 ms; `ROW_OPEN` (450) is the Flutter route length the timings were cut from.

**Button-back return, 300 ms (X2), Full only:**

| Track | Time | Spec |
|---|---|---|
| Bounds: full window → row rect | 0–300 ms | `tween(300, easing = KitEasing.Emphasized)`. Do **not** reverse or flip the easing: Flutter's `rowReturnPath` (flipped emphasized on a reversed animation) is exactly the plain emphasized curve run forward in time |
| Box corners | 0 → 20 dp | as above |
| Chat content | fades out 0–120 ms (`ROW_CONTENT_OUT`) | the chat's `sharedBounds(exit = fadeOut(tween(120, easing = EaseOutCubic)))` (Flutter's reverse-slot `easeInCubic` over [0.6, 1] equals `EaseOutCubic` forward in time) |
| Row content | fades back in 240–300 ms (`ROW_HANDBACK`) | the row's `sharedBounds(enter = fadeIn(tween(60, delayMillis = 240, easing = EaseOutCubic)))` |
| Dim | 0.32 → 0 linearly over 300 ms (`Steady`) | decorator |
| Fallback | the row is gone (archived, deleted) or not on screen: no shared bounds; a `Page` pop instead | `SharedContentConfig.alternativeTargetBoundsInTransitionScopeAfterRemoval` returns null; the gate checks the origin is attached and inside the viewport before choosing |

**The predictive-back gate (option A; replaces the demo's 700 ms latch).** One state machine, in `:ui:kit`
(`motion/RowTransformGate.kt`), fed by `:ui:shell`:

```kotlin
public enum class GateState { Idle, Opening, Closing, Gesture, GestureSettling }

public sealed interface GateEvent {
    public data object TapOpen : GateEvent            // AppNavigator.openChat from a row with an origin
    public data object ButtonBack : GateEvent         // Back committed without a gesture (button, 3-button tap, Esc, Ctrl+W)
    public data object GestureStarted : GateEvent     // dispatcher.transitionState became InProgress
    public data object GestureCommitted : GateEvent   // NavDisplay.onBack called while in Gesture
    public data object GestureCancelled : GateEvent   // transitionState returned to Idle without onBack
    public data object TransitionSettled : GateEvent  // no entry transition is running (shell decorator count = 0)
}

public fun reduce(s: GateState, e: GateEvent): GateState = when (e) {
    GateEvent.TapOpen -> if (s == GateState.Idle) GateState.Opening else s
    GateEvent.ButtonBack -> if (s == GateState.Idle || s == GateState.Opening) GateState.Closing else s
    GateEvent.GestureStarted -> GateState.Gesture                       // also mid-flight during Opening (Nav3 1.2)
    GateEvent.GestureCommitted, GateEvent.GestureCancelled -> if (s == GateState.Gesture) GateState.GestureSettling else s
    GateEvent.TransitionSettled -> if (s == GateState.Gesture) s else GateState.Idle
}

/** Shared bounds may run only outside a gesture and its settling. */
public fun sharedBoundsEnabled(s: GateState, level: MotionLevel): Boolean =
    level == MotionLevel.Full && s != GateState.Gesture && s != GateState.GestureSettling
```

- The kit's `SharedContentConfig` for chat keys returns `isEnabled = sharedBoundsEnabled(gate.state, level)` and
  `shouldKeepEnabledForOngoingAnimation = false`, so a back gesture that starts during the forward transform turns the
  shared element off at once (the chat snaps to full size and the preview takes over) instead of finishing a transform
  against the finger [V: the property exists; its default value is not relied on].
- `TransitionSettled` comes from the shell's entry decorator: each entry reports
  `snapshotFlow { contentScope.transition.isRunning }`; the shell sends `TransitionSettled` when the count of running
  entries drops to 0. **This is why section 07 §3.2's `LaunchedEffect(isTransitionActive)` is replaced** (§13.2):
  `isTransitionActive` is true only for matched shared elements [V, Compose 1.11 note], and a gesture pop has none, so
  that effect would never clear the flag and the next tap would lose its transform.
- `GestureStarted`/`Cancelled` come from `dispatcher.transitionState` (a `StateFlow<NavigationEventTransitionState>`
  [V]); `GestureCommitted` from `NavDisplay(onBack = …)` while the gate is in `Gesture`.
- The direction of the bounds spec (360 ms open vs 300 ms return) is read from the gate state (`Opening` vs `Closing`)
  inside the `BoundsTransform` lambda.

**Predictive preview (gesture) for `GrowFromRow` and `Page`** (Material full-screen values, M8-9):

```kotlin
// ui/shell/src/commonMain/.../nav/ShellMotion.kt  (sketch)
internal fun predictivePop(edge: Int, m: KitMotionState, edgeShiftPx: Int): ContentTransform {
    if (m.reduced) return EnterTransition.None togetherWith ExitTransition.None        // back commits on release, no preview
    val span = PREDICTIVE_SPAN
    val sign = if (edge == NavigationEvent.EDGE_LEFT) 1 else -1                        // away from the swipe edge, not by RTL
    val fadeAt = (span * PREDICTIVE_FADE_AT).toInt()
    return EnterTransition.None togetherWith (
        scaleOut(targetScale = PREDICTIVE_MIN_SCALE, animationSpec = tween(span, easing = KitEasing.PredictiveBack)) +
        slideOutHorizontally(tween(span, easing = KitEasing.PredictiveBack)) { sign * edgeShiftPx } +
        fadeOut(keyframes { durationMillis = span; 1f at 0; 1f at fadeAt; 0f at span })
    )
}
```

- `edgeShiftPx = (windowWidth / 20 − 8 dp)` in pixels, computed in the shell.
- Corners: the shell's entry decorator applies `graphicsLayer { clip = true; shape = RoundedCornerShape(c) }` with
  `c = lerp(0, PREVIEW_CORNER (28 dp), eased progress)`, progress read from `transitionState.latestEvent.progress` in the
  draw phase.
- The page underneath is not scaled or dimmed during the preview ("the list holds still").
- Commit: Nav3 finishes the seeked transition, `(1 − progress) × 300 ms`. Cancel: Nav3 returns it to the start [U: the
  cancel curve Nav3 1.2 uses; P2 records it; if it is linear, the shell wraps the cancel in the `Spatial` spring through
  `rememberNavigationEventState`].
- Under Calm the preview still draws (Follow class); under Off it is not drawn and Back commits on release.
- Android 16's long-press Back on 3-button navigation produces the same `InProgress` events and therefore the same
  preview [U, motion-native §12.9; checked on the AVD with 3-button navigation in P2].

**Option B (target)**: Nav3 1.3 moved `NavDisplay` onto `DeferredAnimatedContent` [V]. With
`permitTransformDuringDeferredTransition = true` the shared bounds ride the preview, and on release they hand over with
velocity into the row. It lives in `:ui:shell` `androidMain` (`DeferredHandoff.kt`) behind the same kit API, with a
`JETPACK_AHEAD.md` row, and becomes the default the week Google marks Nav3 1.3 and Compose 1.13 stable, if spike S8-1
passes.

**Levels.** Full: as above. Calm: `GrowFromRow` behaves as a cross-fade of `QUICK` (no shared bounds, no dim);
predictive preview still draws. Off: no transition; no preview.

**Accessibility.** TalkBack focus moves to the chat's title once the transition settles (`TransitionSettled`), never
at its start. The row keeps its content description.

**RTL.** Bounds are physical rectangles (no mirroring needed). The predictive shift follows the swipe edge.

**Budget.** First frame of the open composes the chat (allowed: one frame up to 16.6 ms). All later frames: no
composition, layout of the shared bounds only, ≤ 3 ms UI. Preview frames: graphics layer only, ≤ 2 ms. §02 S-13:
`frameOverrunMs` p95 ≤ 0; p90 frame duration < 8.3 ms.

**Verified by.**
- `RowTransformGateTest` (pure, `commonTest`): every transition of the table; a tap after a committed gesture gets its
  transform back once `TransitionSettled` arrives (the bug in the 07 sketch); a gesture during `Opening` disables shared
  bounds; Calm/Off never enable them.
- `PredictiveBackPolicyTest` (`navigationevent-testing` 1.2.0 `navigationEventInput`): progress events during an active
  row → page transform never start the transform's back animation; the root never consumes Back.
- Frame captures `grow_open_{0,90,180,270,360}ms`, `grow_return_{0,120,240,300}ms`, `predictive_{25,50,75}pct_{left,right}_{ltr,rtl}`.
- Macrobenchmark `RowToChatBenchmark` (J4) and `PredictiveBackBenchmark` (J5, commit and cancel).
- Device checks P2 (§9.7): the six motion-native §4.4 checks on the emulator, then the owner's phone.
- Rows: B-KIT-054; NAV back rows in section 07.

**Owners.** Coordinator (`:ui:shell` decorator, gate wiring, `ShellMotion`), kit owner (`RowTransformGate`,
`Modifier.growFromRow(key)`, `KitRouteTransition`), Opus for P2 and S8-1.

### 5.5 Sheets (drag, fling, predictive back) and dialogs

`KitSheetHost` renders the M3 1.5 in-window `BottomSheet(...)` [V: present in 1.5.0-alpha22 with
`sheetPredictiveBackScaling`] plus a kit scrim; the body is a `KitSheet` frame (section 06 §7.4).

| Moment | Spec | Level notes |
|---|---|---|
| Open | the sheet slides up from below the window on `KitMotionScheme.defaultSpatialSpec` (`Spatial`, ≈ 178 ms); the scrim fades 0 → `DIM_MOST` of `roles.scrim`, its alpha read from `sheetState.requireOffset()` in `drawBehind` (draw phase) | Calm: same (Fold/Settle); Off: snap |
| Drag | follows the finger 1:1 (anchored draggable); nested scroll: the body list scrolls first and the sheet drags only when the list is at its top | all levels |
| Release / fling | M3 decides the target from position and velocity (M3 defaults: positional threshold 56 dp, velocity threshold 125 dp/s [U: values in 1.5; keep the defaults whatever they are]); settles on `Spatial` | Off: snap |
| Predictive back | M3's own: the surface scales by up to 48 dp in x and 24 dp in y, keeping its aspect; commit hides the sheet; cancel springs back [V source] | Off: commit only |
| Dirty sheet without a draft | `sheetState.confirmValueChange` refuses `Hidden`; the sheet springs back (`Spatial`) and the discard question replaces the body in place (no sheet on a sheet, `KitSheetStep`) | — |
| Height change inside | never an animated sheet height: the body part that changes uses `KitReveal` (Fold), and the sheet follows each frame | Off: instant |
| Second view (`KitSheetStep`) | `AnimatedContent` cross-fade `QUICK` out / `STANDARD` in with `SizeTransform(clip = true)` on `Spatial` (a sheet is not a scrolling list, so Fold is allowed) | Calm: fade + jump height; Off: instant |
| Keyboard | the sheet's content uses `imePadding()`; its action block stays pinned above the keyboard and rides it frame by frame (one window, one inset model) | all |
| Close | slides down on `Spatial`; scrim fades with the offset | Off: snap |
| Haptics | none (MOT-11: sheets never vibrate) | — |

Wide windows (section 06 §10): `full` sheets become a 400–480 dp **end-side** sheet that slides in from the end edge on
`LargeTravel` (mirrored in RTL) and follows the predictive gesture toward the end edge (up to 10 % of its width), then
slides out on commit. A centred 560 dp panel (expanded and large) fades in over `STANDARD` with an 8 dp rise (Full),
fades out over `QUICK`; Back dismisses it on commit with no preview.

**Dialogs** (`KitDialog`, `KitAlert`): in-window overlay, 480 dp centred panel; fade in `STANDARD` (+ 8 dp rise under
Full), fade out `QUICK`; scrim as sheets. No scale (MOT-2). Back dismisses on commit, no preview.

**Menus** (`KitMenu`, a `Popup`): fade in `QUICK`, and under Full the panel drops `MENU_DROP` (8 dp) from its anchor
(rises when it opens above), position only; closes with a `QUICK` fade. Opened by long-press with `KitHaptic.LongPress`.

**Verified by.** `SheetBackOwnerTest` (sheet > selection > find > page > root; part of section 07's `BackOwnerPolicy`),
`DirtySheetTest` (refused dismiss + in-place question), frame captures `sheet_open_{0,90,180}ms`, `sheet_predictive_50pct`,
`sheet_step_{0,125,250}ms`, `dialog_open_{0,125,250}ms`, `menu_open_{0,75,150}ms_{full,calm}`; Macrobenchmark
`SheetBenchmark` (J6: open, drag half, fling closed, open, predictive close). Rows: B-KIT-015, B-KIT-016, B-KIT-022,
B-KIT-032. Spike S8-7 (in-window sheet as a modal: scrim, TalkBack focus trap, IME). Fallback: `ModalBottomSheet`
behind the same kit API (ADR §9.4).

**Owner.** Kit owner (`KitSheetHost`, `KitSheet`, `KitDialog`, `KitMenu`).

### 5.6 Lists: insert, remove, reorder

Features never call `animateItem`; kit list parts apply it per **list kind**:

| List kind | Parts | New item | Removed item | Moved item | First load |
|---|---|---|---|---|---|
| **Feed** (Conversations, Inbox, All conversations, servers, notification and schedule lists) | `KitFeedList` (lazy) | fade `STANDARD` on `Enter`; under Full a late arrival also rises 8 dp (`graphicsLayer.translationY`, `Enter`) | fade `QUICK`; the gap closes with placement | placement `Spatial` under Full; `null` (jump) under Calm and Off | no animation: the list is created only after the first page of rows exists; skeleton rows are a different part (`KitSkeletonRows`, no motion) |
| **Transcript** (chat) | kit-chat transcript helpers | fade `STANDARD` (notices, tool rows, cards); prompt items skip it while a lift claim exists (§5.1) | fade `QUICK` | never (`placementSpec = null`, rendering-chat §4.6): when the live block grows, every row above moves each frame and a placement spring would make them lag | none |
| **Short list** (≤ 50 rows in a `Column`: checklist, settings sub-rows, terminal list, server switcher) | `KitAnimatedRows` | `expandVertically` + fade `STANDARD` | shrink + fade `QUICK` | cross-fade in place | none |
| **Search results / filter change** | any | the list is wrapped in `key(query, filter) { … }`, so a new result set appears without per-item animation (Flutter: "restart with a new key") | — | — | — |
| **History pages** (older messages, load more) | transcript, feed | none: they arrive outside the viewport and the key anchor keeps the view still [V] | — | — | — |

**Late arrival.** `KitFeedList` remembers the set of keys present at its first layout; a key not in that set gets the
rise once, then joins the set. A key arriving while the list is frozen waits.

**Frozen lists.** `KitFeedList(frozen: Boolean)`: while true, the part keeps showing its last rows (a held snapshot);
updates queue; on release the diff animates once with the Feed rules. Frozen while the row → chat gate is not `Idle`,
and while a page covers the list (`LocalKitPageVisible == false`), so a chat returns to a row that has not moved
(`f7e775dd3`, `89828a3c8`).

**Swipe actions** (`KitSwipeAction`, only for acts with Undo, always with a menu twin, A11Y-5): the row follows the
finger (Follow); past the threshold and released, the row continues out of the screen on `SpatialFast` and the list
closes the gap with placement (Feed rules); if `onAct` returns false, the row springs back (`Spatial`) and a notice
says why. The Undo bar arrives (§5.15). This is Android's own pattern; Flutter snapped the row back first (§13.1).
No threshold haptic (D8-3).

**User reordering by drag:** no Flutter feature has it; none at the switch. A future one uses a kit
`KitReorderableList` with `KitHaptic.LongPress` on lift and `KitHaptic.Select` per slot crossed.

**Arrival wash** (`KitArrival`, a search result or a notification landing on a row): the list scrolls the row into view
at 30 % from the top over `STANDARD` on `Emphasized` (Off: jump); the row is washed with `accent` at alpha 0.18 at once,
held `ARRIVAL_HOLD` (2400 ms), then fades out over 900 ms on `Enter` (Off: removed at once); TalkBack focus moves to the
row. Rows: B-NAV-073.

**Budget.** Placement frames: ≤ 3 ms UI. Insert frames: one item composed (prefetched where possible).

**Verified by.** `FeedListMotionTest` (desktop compose test: first load plays nothing; a late arrival rises once; a move
animates under Full and jumps under Calm; frozen holds and releases once), `HeldSnapshotTest` (pure), frame captures
`feed_insert_{0,125,250}ms`, `feed_move_{0,90,180}ms`, `swipe_{threshold,out,gap}`. Rows: B-KIT-027 (swipe), B-KIT-051
(tap shows on the next frame), B-NAV-073.

**Owner.** Kit owner (`KitFeedList`, `KitAnimatedRows`, `KitSwipeAction`, `KitArrival`); kit-chat owner (transcript).

### 5.7 Tool step: running → done

Three presentations, all owner-approved in Flutter:

**A. The folded work line** (default; one line per turn, "Read 3 files · edited 1"):
- While running: a `KitStatusMark.working` spinner (16 dp, 2 dp stroke, accent) beside the label, one lap per
  `SPINNER_LAP` (1333 ms); half speed under Calm; a still dot under Off (progress loop).
- When a step finishes: the counts roll (`KitSwap.count`: the old number leaves and the new one arrives, travelling
  `COUNT_ROLL_SHARE` (60 %) of the line height, up when it rises, over `QUICK`; Calm cross-fades; Off instant; the
  leaving number is excluded from semantics).
- When the turn's work ends: the spinner cross-fades to the done check over `QUICK`, the check fading in from
  `MARK_FROM` (0.9) under Full. A failure the agent got past stays folded and neutral (STATE-15); a turn that ended on a
  failure shows the neutral failed glyph, still, with Retry arriving by `KitReveal`. No shake, no red, no haptic.
- Fold open/close (X5): the timeline unfolds inside the same lazy item with `KitReveal` (height + fade `STANDARD` in,
  `QUICK` out; Calm the same; Off instant); the chevron turns 180° on `SpatialFast` under Full and swaps glyphs with a
  cross-fade under Calm. The work line and its timeline are **one** lazy item (`work:$turnId`), so the fold is a size
  change of one item and the rows around it follow without placement animation.

**B. The opened timeline's step light** (`KitStepTimeline`; Flutter `kit_step_light.dart`; owner restored it after a
snapshot commit deleted it):
- Running: a light travels the rail from above the node (`[0, 0.28]` of the lap on `StepEase`), splits and runs round
  both sides of the node (`[0.26, 0.66]`), meets at the bottom centre and leaves below (`[0.64, 0.95]` on `StepOut`); one
  lap per `STEP_LAP` (2000 ms; Calm 4000); the node tile breathes over `STEP_BREATHE` (1600 ms). The halves mirror, so
  RTL needs nothing extra (B-KIT-036).
- Done: the node flashes (`STEP_FLASH` 300 ms), the done tick shows for `STEP_TICK` (600 ms), the tool glyph returns
  from 0.4 to 1 scale over `STEP_SPRING` (260 ms) on `Land`, and the light fades over `STEP_FADE` (200 ms).
- Off: the still looks only (state glyph per node; no light).
- Drawn in one `drawWithCache` per node, phase read in the draw phase (Flutter `4a907c935` moved it off rebuilds for the
  same reason).

**C. A tool row's status** (`KitToolRow`): "Running" → "Done · 1.2 s" cross-fades (`KitSwap`, `QUICK`). The duration
ticks in tenths under a minute while the row is visible and running (a text update at 10 Hz is not motion); it stops
ticking when the row scrolls off or the page is covered.

**Step arrival** (X5): a new step in an **open** timeline unfolds from 0 height with a fade over `STANDARD`; in a folded
work line only the count rolls.

**Spinner truth** (behaviour, not motion, but it decides when the spinner stops): a tool spinner stops when later words
in its turn exist or the turn ended (B-CHAT-117).

**Budget.** Spinner and light: draw-only, ≤ 0.5 ms. A fold: ≤ 4 ms (one item's re-layout per frame).

**Verified by.** `StepLightPhaseTest` (pure: segment boundaries, mirrored halves meet at the bottom centre, Calm lap
4000), `CountRollTest` (direction by sign; Calm cross-fade; Off instant; first build final), frame captures
`steplight_{0,25,50,75}pct`, `step_done_{0,300,600,860}ms`, `workline_fold_{0,125,250}ms`, `count_roll_{0,75,150}ms`.
Rows: B-CHAT-063, B-KIT-036, B-CHAT-117, B-KIT-023.

**Owner.** `:ui:kit-chat` owner (`KitWorkLine`, `KitStepTimeline`, `KitToolRow`); kit owner (`KitSwap`, `KitSpin`,
`KitStatusMark`).

### 5.8 Permission and request card appear

**What the person sees.** When the agent asks (a permission, a question, a form, a choice), one card arrives above the
composer: it unfolds and fades in; the glow and Stop leave; the live line says "Waiting for you". After an answer the
card collapses to a one-line row with a receipt.

| Moment | Spec |
|---|---|
| Arrive | `KitReveal` in the composer region: height 0 → card height and alpha 0 → 1 over `STANDARD` on `Enter` (Fold + Fade; Calm the same; Off instant). The composer region grows, so the transcript's clearance spacer grows with it each frame and, at the live edge, the newest words stay visible above the card. The card never takes focus from the field and never opens a modal. |
| Arrive from a notification or Inbox row | as above, then the arrival wash (§5.6) once (`chat-request-<id>`) |
| Glow and Stop | leave as in §5.2 "turn ends" (no Done haptic); the live line's words cross-fade to "Waiting for you" (`QUICK`) and its sweep stops (not working) |
| Height cap | the card is capped at 45 % of the window height (60 % from 250 % text); its body scrolls; a cap change never animates |
| Answer tapped | the answer is held `HELD_ANSWER` (3 s) as a collapsed line "Allowed · Undo": the card's body folds away (`KitReveal`, `QUICK`) and the one-line row cross-fades in (`STANDARD`); `KitHaptic.Send` fires (the person's answer leaves) |
| Sending / not confirmed / answered elsewhere | the receipt words cross-fade (`QUICK`); nothing moves |
| Leave | when the request leaves (answered, expired, answered elsewhere), the row folds away (`QUICK`) unless it stays as a receipt in the transcript |
| Next request | waits until the previous card has left; then arrives as above ("2 more waiting" count rolls) |

Signal style prints the card as a slip from a printer slot (section 06 style hooks): the slip's travel is a Travel
motion (Full only), the same 250 ms.

**Verified by.** `HeldAnswerTest` (pure: 3 s hold, Undo inside, leaving sends at once, a second answer ignored —
B-CHAT-050), `RequestCardMotionUiTest` (arrival unfolds; first build final when the chat opens with a waiting request;
Off instant), frame captures `request_arrive_{0,125,250}ms_{dark,light}`, `request_answered_collapse_{0,150,250}ms`.
Rows: B-CHAT-123, B-CHAT-050.

**Owner.** `:ui:kit-chat` owner (`KitRequestCard`, composer region layout).

### 5.9 Page and tab transitions

Route classes from section 07 §3.1; exact motion here. `dir = +1` in LTR and `−1` in RTL (forward comes from the end
edge). `ShellMotion` builds all of them from `KitMotionSpecs`; Nav3 receives them through the `NavDisplay`
`transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec` parameters and the per-entry metadata helpers
(`NavDisplay.transitionSpec { }`, `popTransitionSpec { }`, `predictivePopTransitionSpec { edge -> }`) [V androidx-main].

| Class | Forward | Pop (button) | Predictive preview | Calm | Off |
|---|---|---|---|---|---|
| `Page` (most pages; Settings) | shared axis X: new page `slideInHorizontally(spatial) { dir × 30 dp }` + `fadeIn(tween(175, delayMillis = 75, Enter))`; old page `slideOutHorizontally(spatial) { −dir × 30 dp }` + `fadeOut(tween(75, Enter))` | mirror of forward | §5.4 Material preview | cross-fade `QUICK` | none |
| `GrowFromRow` | §5.4 | §5.4 | §5.4 | cross-fade `QUICK` | none |
| `FullScreenDialog` (New conversation, pairing scanner; Close at end) | slides up from the bottom edge: `slideInVertically(largeTravel) { fullHeight }` + `fadeIn(QUICK)`; the page under it does not move | `slideOutVertically(tween(QUICK, easing = EaseInCubic)) { fullHeight }` | the page moves down by up to 10 % of its height and scales to 0.95 while the finger moves, then slides fully out on commit (easing in §5.9.1) | cross-fade `QUICK` | none |
| `TopLevel` (rail/sidebar switch, Ctrl+1..3) | fade-through: leaving fades out over the first third (83 ms), chosen fades in over 83–250 ms, on `Enter`; no slide | same | Material preview | cross-fade `QUICK` | none |
| `Replace` (new conversation → its chat; import → chat) | cross-fade `STANDARD` | — | — | cross-fade `QUICK` | none |
| `Root` (`replaceRoot`, `land`) | cross-fade `STANDARD` | — | — (the root never intercepts Back; the system plays back-to-home) | cross-fade `QUICK` | none |

List-detail scenes (wide windows): changing the detail pane cross-fades the pane over `QUICK`; the list pane does not
move (section 07 §3.1). A rail or sidebar selection indicator moves on `SpatialFast` (Full), jumps under Calm/Off.

#### 5.9.1 The `FullScreenDialog` predictive easing

A seeked `ContentTransform` maps gesture progress to its own fraction, and its end state is the full exit. To keep the
preview small, the slide uses a two-part easing: the first 70 % of the fraction moves the page by only 10 % of its
height; the last 30 % (only reached after a commit) carries it out.

```kotlin
internal val SheetLikePredictive: Easing = Easing { t ->
    if (t < 0.7f) 0.1f * KitEasing.PredictiveBack.transform(t / 0.7f)
    else 0.1f + 0.9f * EaseInCubic.transform((t - 0.7f) / 0.3f)
}
// predictive spec for FullScreenDialog:
// slideOutVertically(tween(PREDICTIVE_SPAN, easing = SheetLikePredictive)) { it } +
// scaleOut(0.95f, tween(PREDICTIVE_SPAN, easing = KitEasing.PredictiveBack))
```

#### 5.9.2 Tabs inside a page (`KitTabSwitcher`, `KitTabStrip`)

- Body switch: fade-through `STANDARD` (leaving fades in the first third, chosen fades in after; destinations stay
  alive; no slide, no swipe paging). Calm: simultaneous cross-fade `QUICK`. Off: instant (B-KIT-050).
- Tab indicator: moves to the chosen tab on `SpatialFast` (Full); jumps under Calm/Off.
- Tab counts roll (`KitSwap.count`).
- No horizontal swipe between tab bodies: it would compete with code, diff and table scrollers and with the back gesture
  at the edges; the tabs are always visible rows (obvious options first).
- Haptic: none on a tab tap (a plain tap, MOT-11).

**Verified by.** `ShellMotionTest` (pure: each class × level returns the expected transform kinds; `dir` flips in RTL;
predictive shift sign follows the edge, not the direction), `TabSwitcherMotionTest`, frame captures
`page_fwd_{0,75,150,250}ms_{ltr,rtl}`, `fsdialog_up_{0,120,250}ms`, `fsdialog_predictive_{30,60}pct`,
`toplevel_fade_{0,83,166,250}ms`. Macrobenchmark `PageBenchmark` (part of J4: open Settings, back). Rows: B-KIT-054,
B-KIT-050.

**Owner.** Coordinator (`ShellMotion`), kit owner (`KitTabSwitcher`, `KitTabStrip`).

### 5.10 Pull to refresh

`KitRefresh` keeps the Flutter look (the portal's two brackets drawing in with the pull, the spark landing when armed;
default of D8-4), built on M3's `PullToRefreshState` + `Modifier.pullToRefresh` [V: `androidx.compose.material3.pulltorefresh`
in 1.5.0-alpha22] with a kit indicator.

| Phase | Spec |
|---|---|
| Pulling | the 40 dp disc slides down with the pull (`state.distanceFraction`, read in `drawWithCache`/`graphicsLayer`), the brackets' stroke length = `EaseOutCubic(min(1, fraction))`; follows the finger at every level |
| Armed (fraction ≥ 1) | the spark lands between the brackets over `QUICK`; no haptic (D8-3) |
| Released before armed | the disc returns on `Spatial` (Off: snap) |
| Refreshing | the disc rests at 40 dp; the portal breathes over `REFRESH_BREATH` (4000 ms) and the spark circles (ambient: Full only; Calm/Off: still) |
| Done | the disc shrinks away (alpha + scale to `MARK_FROM`, `QUICK`; Off: removed at once) and new rows arrive with the Feed rules |
| List behaviour | the refresh consumes the top pull, so the list does not stretch at the top while refresh is enabled; the bottom end still stretches |
| Without a finger | a TalkBack "Refresh" custom action and Ctrl+R / F5 run the same refresh with the same drawing |

**Verified by.** `RefreshPhaseTest` (pure: phase transitions; release before armed returns; Off never loops), frame
captures `refresh_pull_{25,50,100}pct`, `refresh_running_still`, `refresh_done_{0,75,150}ms`; Macrobenchmark
`RefreshBenchmark` (J8). Rows: B-KIT-054 (MOT-4).

**Owner.** Kit owner.

### 5.11 Overscroll stretch

- Android's own effect, from Compose's default `LocalOverscrollFactory` (`AndroidEdgeEffectOverscrollFactory` [V]):
  stretch on API 31+, glow on API 26–30 (minSdk 26). The app never replaces the factory (R11).
- The reverse-layout transcript stretches at both ends like any list (the platform handles orientation).
- An inner scroller that sits inside an outer scroller of the **same** orientation and shows a double stretch (the
  request card's body inside the composer region; a code block's vertical overflow inside a sheet) uses
  `rememberOverscrollEffect()?.withoutVisualEffect()` on the inner one only. Horizontal inner scrollers (code, diff,
  table) keep their own horizontal stretch.
- Levels: the stretch follows the finger at every level; its release is the platform's, scaled by the system animator
  scale (0 = instant). An in-app Off does not change it (Follow class).
- Fling: `ScrollableDefaults.flingBehavior()` (platform spline decay), untouched.
- iOS later: CMP's bounce. Desktop: none.

**Verified by.** a device check only (Robolectric does not render `EdgeEffect`): §9.7 rows OV-1 to OV-3 on the API 37
emulator and an API 30 image for the glow. `OverscrollFactoryTest` asserts no module provides `LocalOverscrollFactory`
(Konsist).

**Owner.** Kit owner.

### 5.12 IME-follow composer

**Layout (chat screen, built by the kit-chat `KitChatFrame` part):**

```kotlin
// shape only; the real part lives in :ui:kit-chat
Box(Modifier.fillMaxSize().imePadding()) {                          // the whole body shrinks with the keyboard, frame by frame
    KitTranscriptList(                                              // LazyColumn(reverseLayout = true)
        clearance = { composerHeightPx },                           // item 0: a spacer whose height is read in layout { }
        modifier = Modifier.fillMaxSize(),
    )
    KitComposer(
        modifier = Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding()                                // becomes 0 while the keyboard covers the nav bar (insets consumed)
            .onSizeChanged { composerHeightPx = it.height },
    )
}
```

- `android:windowSoftInputMode="adjustResize"` (`:androidApp` manifest) and `enableEdgeToEdge()`; required for IME
  insets.
- On API 30+ Compose drives `WindowInsets.ime` from `WindowInsetsAnimation`, so the body, the transcript and the
  composer ride the keyboard **frame by frame** [V]. On API 26–29 the keyboard may jump [U]; accepted (old devices).
- The reverse-layout transcript keeps the newest content glued to the bottom as the viewport shrinks; a reader who has
  scrolled up keeps their first visible item.
- The clearance is a measured spacer item read in the layout phase, never `contentPadding` (a composition parameter that
  would recompose the list on every IME frame; the insets docs also recommend a spacer).
- Never read `WindowInsets.ime.getBottom()` in composition (one frame late) [V].
- The jump pill, the undo bar and tips sit above the composer by reading `LocalKitBottomInset` (section 06 §5.1).
- **Composer height** grows and shrinks with lines up to 40 % of the window height, then scrolls inside: the pill's
  height change animates on `SpatialFast` (a Fold of a non-list part: Full and Calm), instant under Off. The field is
  never re-parented while focused.
- **Back** with the keyboard open closes the keyboard first (system); no handler.
- **Android 17 rotation** (IME not restored after an unhandled rotation [V motion-native §3.1]): the chat screen saves
  `hadImeFocus` (`rememberSaveable`) and, after recreation, requests focus and shows the keyboard again if the
  composer had focus and a draft. Decided here; no owner question.
- **`imeNestedScroll`** (drag the conversation to pull the keyboard open or push it closed, Google Messages style) is
  off at the switch. It is `@ExperimentalLayoutApi` [V], a hidden gesture that needs a tip, and it changes what a drag
  near the newest end does. Spike S8-3 on the NX721J, then owner decision D8-2.
- Frost (D10): the composer pane turns to frost only while content is scrolled under it; that switch is a
  `FROST_FADE` (180 ms) cross-fade of the pane's fill; the keyboard does not change it.

**Levels.** The keyboard animation is the system's (Follow class) at every level. The composer height change is a
Fold, so it animates under Full and Calm and is instant under Off.

**Budget.** IME frames: no composition; layout of the root box + re-placement of visible transcript items, ≤ 4 ms UI.

**Verified by.** `ImeClearanceTest` (desktop compose test with a fake bottom inset: the spacer equals the composer's
height; no recomposition of the list when the inset animates — counted by `RecompositionBudgetTest`), Macrobenchmark
`ImeBenchmark` (J7: tap the field, keyboard up, type 3 lines, keyboard down), device check IME-1 to IME-4 (§9.7).

**Owner.** `:ui:kit-chat` owner (`KitChatFrame`, `KitComposer`); `:androidApp` owner (manifest flag).

### 5.13 Haptics

See §6 (its own section because every part uses it).

### 5.14 Reduced motion

See §7 (the full matrix) and §3 (the policy).

### 5.15 Micro motions of the kit (the "every part answers" table)

Every kit part's KDoc carries a `Motion:` line (section 06 §14.4). These are the lines to copy. "Full / Calm / Off" as
§3.3.

| Part | Change | Full | Calm | Off | Rows |
|---|---|---|---|---|---|
| `KitTappable` (rows, chips, buttons) | press | platform ripple bounded to the part's shape at `pressedAlpha` 0.12 of the part's ink; pressed state shown on the next frame and for at least `PRESS_HOLD`; inside a scroller a quick tap shows on release | same | flat pressed fill (in-app Off); platform static state (system 0) | B-KIT-051 |
| `KitButton` | enabled ⇄ disabled | fill and words colour tween `QUICK` | same | instant | B-KIT-011 |
| `KitButton` | working | words cross-fade to a 2 dp spinner (`QUICK`); spinner progress loop | spinner half speed | still dot | B-KIT-011 |
| `KitIconButton` | glyph toggle (show/hide, play/pause) | glyph cross-fade `QUICK`, colour tween `QUICK` | same | instant | B-KIT-019 |
| `KitIconButton.copy` | copied | glyph → check cross-fade `QUICK`, check from `MARK_FROM`; back after `COPIED_HOLD` | no scale | instant | — |
| `KitSwap` | any keyed content swap | cross-fade `QUICK`, keeps the leaving child's state (`8bff010c9`) | same | instant | B-KIT-023 |
| `KitSwap.count` | number change | roll 60 % of height, up/down by sign, `QUICK` | cross-fade | instant | B-KIT-023 |
| `KitSpin` | chevron open/close | 180° on `SpatialFast` | glyph cross-fade | instant | — |
| `KitReveal` | part appears / leaves | height + fade `STANDARD` in, `QUICK` out; leaving child ignores touches and is hidden from TalkBack | same | instant | B-KIT-023 |
| `KitEntrance` | late part arrives | fade `STANDARD` + 8 dp rise | fade | instant | B-KIT-023 |
| `KitPresence` | slot along a row | slot width on `SpatialFast` + fade `QUICK` | fade, slot jumps | instant | — |
| `KitAnimatedValue` | eased number | tween `STANDARD` `Enter`; first build instant | same | instant | B-KIT-023 |
| `KitDim` | non-text opacity | tween `QUICK`; debug assert if text is inside | same | instant | B-KIT-023 |
| `KitSwitch` (M3 switch inside) | toggle | thumb on `KitMotionScheme` fast spatial; `KitHaptic.ToggleOn/Off` | same | snap | B-KIT-014 |
| `KitSegmented` | choose | indicator on `SpatialFast`; `KitHaptic.Select` | indicator jumps, fill fades | instant | B-KIT-031 |
| `KitStepSlider`, `KitWheel` | each step | thumb follows the finger; `KitHaptic.Select` once per new step | same | same | — |
| `KitProgress` / bars | value | tween `STANDARD` `Enter`; never backwards within a job | same | instant | B-KIT-025 |
| `KitLoadingBar` (2 dp) | indeterminate | progress loop | half speed | still bar | — |
| `KitContextRing` | value / colour / halo / drain / spin | 600 / 320 / 700 / 900 / 1200 ms (X9) | fades only | instant | — |
| `KitStatusLine` | appears / changes | `KitReveal` / `KitSwap` | same | instant | B-KIT-035 |
| `KitStatusMark` | waiting / working / done / failed | working = 16 dp spinner; done check from `MARK_FROM` | spinner half speed | still dot | — |
| `KitUndo` | appears / leaves | fade `STANDARD` + 8 dp rise above `LocalKitBottomInset`; leaves with fade `QUICK` | fade | instant | — |
| `KitTip` | appears / leaves | fade `TIP_FADE` (200) + 4 dp rise toward the anchor; any touch fades it; leaves after `TIP_HOLD` | fade | instant | B-NAV-038 |
| `KitTour` | step change | highlight glides between anchors on `Spatial`; card cross-fades `QUICK` | highlight jumps | instant | B-NAV-074 |
| `KitMenu` | open / close | §5.5 | — | — | B-KIT-022 |
| `KitSurface` (any style) | Style change / state change | fill, edge, shadow colours over `STYLE_FADE` 200 / `STATE_FADE` 120; pressed face sinks as its shadow fades (Soft, Brutal) | same | instant | B-KIT-046 |
| `KitFrostHeader` | content scrolls under | solid → frost over `FROST_FADE` 180 | same | frost held solid | B-KIT-018 |
| `KitTurnLive` (live line) | working | light sweeps over the words: gradient `text3 → text1 → text3`, centre from −0.3 to 1.3 of the width (reversed in RTL), band ±0.18, `Steady`, one pass per `LIVE_SWEEP_LAP` (2200 ms) | 4400 ms | still words | — |
| `KitTerminalView` | content arrives | cursor blinks while content arrives and ends lit (B-KIT-055) | same | lit, still | B-KIT-055 |
| `KitScreen` system bars | theme changes | icon brightness switches with the theme in the same frame (no animation; owner rule R4) | same | same | — |

---

## 6. Haptics map

### 6.1 Today's feel (the Flutter app the owner uses)

Read from the Flutter embedding jar's `PlatformPlugin.vibrateHapticFeedback` [V, javap] and `kit_haptics.dart` [R]:

| `KitHaptics` (Dart) | Flutter call | Android constant the phone plays |
|---|---|---|
| `send` | `lightImpact` | `VIRTUAL_KEY` (1) |
| `commit` | `mediumImpact` | `KEYBOARD_TAP` (3) |
| `select` | `selectionClick` | `CLOCK_TICK` (4) |
| `done` | `successNotification` | `CONFIRM` (16) on API 30+; nothing below |
| `longPress` | `Feedback.forLongPress` | `LONG_PRESS` (0) |

Note: batch1 has no visible Vibration row; `KitEffects.haptics` defaults to on and is not persisted [R `lib/main.dart:483`,
`effects.dart`]. The system "Touch feedback" setting applies automatically (`View.performHapticFeedback`).

### 6.2 The native map (default of D8-1)

`KitHaptic` (section 03 §12.2): `Send, Done, Commit, LongPress, Select, ToggleOn, ToggleOff`. `AndroidKitPlatform`
plays them with `view.performHapticFeedback(constant)` directly, so the exact constant is ours and the system setting is
honoured; no `VIBRATE` permission; no `VibrationEffect` (too loud, and the new envelope APIs are Android 16 26Q4-only).

| `KitHaptic` | When (MOT-11) | API 34+ | API 30–33 | API 26–29 | Compose type with the same constant |
|---|---|---|---|---|---|
| `Send` | the person's words leave: Send, an answer on a request card, a phrase chip tap that sends | `VIRTUAL_KEY` | same | same | `VirtualKey` |
| `Done` | a waited-for finish, once: a reply finished (busy → idle), setup ready, task merged; skipped under Off | `CONFIRM` | `CONFIRM` | none | `Confirm` |
| `Commit` | a confirmed stop, delete or discard (Stop tap; `KitConfirmSheet` of kind stop/destructive/discard) | `KEYBOARD_TAP` | same | same | `KeyboardTap` |
| `LongPress` | a long-press opens a menu or starts a selection | `LONG_PRESS` | same | same | `LongPress` |
| `ToggleOn` / `ToggleOff` | a switch flips | `TOGGLE_ON` / `TOGGLE_OFF` | `CLOCK_TICK` | `CLOCK_TICK` | `ToggleOn` / `ToggleOff` |
| `Select` | a segment chosen, a slider step or wheel value passed | `SEGMENT_TICK` | `CLOCK_TICK` | `CLOCK_TICK` | `SegmentTick` |

Send, Done, Commit and LongPress are exactly today's constants (nothing lost). Switches and segments use Android's
semantic constants where the phone has them (Android 14+), which is "max native". The owner's NX721J runs Android 16,
so the toggle and segment ticks are the only ones that may feel different; the owner phone test listens for it (D8-1).

```kotlin
// ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/platform/AndroidHaptics.kt
internal fun constantFor(kind: KitHaptic, sdk: Int): Int? = when (kind) {
    KitHaptic.Send -> HapticFeedbackConstants.VIRTUAL_KEY
    KitHaptic.Done -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else null
    KitHaptic.Commit -> HapticFeedbackConstants.KEYBOARD_TAP
    KitHaptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
    KitHaptic.ToggleOn -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
    KitHaptic.ToggleOff -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
    KitHaptic.Select -> if (sdk >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
}
// AndroidKitPlatform.hapticPerformer(): { kind -> if (vibrationOn && allowedAt(level, kind)) constantFor(kind, SDK_INT)?.let(view::performHapticFeedback) }
// allowedAt(level, Done) = level != Off; every other kind = true.
```

### 6.3 Rules

- **Never** on scroll, plain taps of rows or buttons, tab taps, failures, copy, sheets, dialogs, pull-to-refresh arming,
  swipe thresholds, predictive back, or streaming (MOT-11; D8-3 keeps the last three off).
- **Exactly one** long-press tick: `KitTappable` uses `combinedClickable(hapticFeedbackEnabled = false, …)` [V: the
  parameter exists in foundation 1.12.1] and plays `KitHaptic.LongPress` itself, so the in-app Vibration choice applies
  and the ripple and the tick share one path (Flutter fix `2048185f7`).
- Text-handle ticks come with `BasicTextField`; never add more.
- Notification arrival vibration belongs to the notification channel (section 11).
- The in-app Vibration choice (`KitEffects.haptics`, default on) gates every kind; when the owner later asks for a
  visible row, it is stored device-wide next to the other effects keys.
- iOS later: `UIImpactFeedbackGenerator`/`UISelectionFeedbackGenerator` behind the same enum. Desktop: no-op.

### 6.4 Tests

| Test | Kind | Asserts |
|---|---|---|
| `HapticConstantsTest` | Android host (Robolectric, SDK 29/33/36 configs) | `constantFor` table per SDK |
| `HapticPolicyTest` | `commonTest` | `allowedAt`: Done skipped under Off; nothing when Vibration is off |
| `KitHapticsUiTest` | desktop compose tests with `FakeKitPlatform` (records kinds) | Send on send; Commit on Stop; LongPress once per long-press; ToggleOn/Off on a switch; Select once per new slider step and none for the same step; nothing on a row tap, copy, sheet open or failure (B-KIT-014, B-KIT-031) |
| Device check H-1 to H-3 | emulator (no vibrator: verify calls via logcat tag in the `qa` build) and the owner's phone (feel) | §9.7 |

---

## 7. Reduced-motion matrix (every interaction)

"Instant" = the next frame shows the final state and nothing keeps running (MOT-7). System "Remove animations" behaves
exactly like Off.

| Interaction | Full | Calm | Off / Saver / system 0 |
|---|---|---|---|
| Streaming reveal | paced, words fade, box grows, caret breathes | paced, words fade, box grows, caret still | text as published, no fade, no grow, caret still |
| Follow-scroll after send | glide (or jump if far) | glide | jump |
| Jump pill | fade + 8 dp rise | fade | instant |
| Sent bubble lift | rise from the field, 0.92 → 1, fade | fade in place | none |
| Send ⇄ mic | cross-fade | cross-fade | instant |
| Stop arrives / leaves | slot opens + fade | key fades, slot jumps | instant |
| Stop ring | turns only if glow is off | still track | still track |
| Glow | travels + halo breathes | still ring + steady halo | none (Saver: still hairline) |
| Glow fade in/out | 450 ms | 450 ms | instant |
| Row → chat | container transform 450 / 300 ms | cross-fade `QUICK` | instant |
| Predictive preview | drawn | drawn | not drawn; commits on release |
| Page shared axis | slide 30 dp + fade | cross-fade `QUICK` | instant |
| Full-screen dialog | slide up / down | cross-fade `QUICK` | instant |
| Top-level / tab fade-through | fade-through 250 | cross-fade 150 | instant |
| Sheet open / close / settle | springs | springs | snap (drag still follows) |
| Sheet predictive back | M3 preview | M3 preview | commit only |
| Dialog / menu | fade + rise / drop | fade | instant |
| List insert / remove | fade (+ rise for late feed rows) | fade | instant |
| List move | placement spring | jump | jump |
| Swipe action | row out + gap closes | row out + gap closes | jump |
| Work-line fold / step arrival | fold + fade | fold + fade | instant |
| Step light | lap 2000 | lap 4000 | still node |
| Spinners, loading bar | full speed | half speed | still |
| Live line sweep | 2200 ms | 4400 ms | still |
| Request card arrive / collapse | fold + fade | fold + fade | instant |
| Pull to refresh | follows; breath while running | follows; still while running | follows; still; leaves at once |
| Overscroll | platform stretch | platform stretch | platform stretch (release scaled by system scale) |
| IME | frame by frame | frame by frame | frame by frame (system) |
| Composer height | `SpatialFast` | `SpatialFast` | instant |
| Ripple | platform | platform | flat fill (in-app Off) |
| Arrival wash | scroll glide + wash fade | same | jump + wash removed at once |
| Tips | fade + 4 dp rise | fade | instant |
| Count roll | roll | cross-fade | instant |
| Style change | 200 ms cross-fade | same | instant |
| Haptic `Done` | yes | yes | no |

---

## 8. 120 Hz budgets

### 8.1 The numbers

- One frame at 120 Hz is **8.33 ms**; at 60 Hz 16.67 ms. Budgets are set for 120 Hz; on a 60 Hz device the same work
  has double the room.
- Gate (from §02): `frameOverrunMs` p95 **≤ 0** and p99 **≤ 4 ms** for streaming (S-9), fling (S-10) and transitions
  (S-13), on the NX721J at 120 Hz; p90 frame duration < 8.3 ms for transitions (S-13); ≤ 1 % janky app-layer frames
  while streaming (S-12). The emulator gives relative numbers only (within 10 % of the last green).
- Per-interaction targets below are [E] working budgets for agents; the first measured value plus a margin replaces
  each, and they are never loosened after (§02 §4.2).

| Interaction | Composition per frame | Layout per frame | Draw | UI-thread target p95 [E] | Frame-rate vote |
|---|---|---|---|---|---|
| Stream, fade-only frame | none | none | one offscreen layer (live block) | ≤ 2 ms | High while active (Full) |
| Stream, word frame (≤ 20/s) | the live block item | one block + list placement | same | ≤ 4 ms | High |
| Glow / Stop ring / live sweep | none | none | clip + one circle + one shadow / one arc / one gradient text | ≤ 0.5 ms | none (Normal ≥ 60 Hz is fine) |
| Row → chat, first frame | the chat entry | lookahead + layout | full | ≤ 16.6 ms (one frame may overrun) | High |
| Row → chat, other frames | none | shared bounds only (content skips to lookahead size) | clip + content | ≤ 3 ms | High |
| Predictive preview | none | none | graphics layer | ≤ 2 ms | High (touch boost also applies) |
| Page shared axis | new page on frame 1, then none | none | graphics layers | ≤ 2 ms after frame 1 | movement auto-boosts |
| Sheet drag / settle | none | sheet offset | sheet + scrim | ≤ 2 ms | touch boost |
| IME | none | root + visible transcript items re-placed | full | ≤ 4 ms | movement auto-boosts |
| List placement | none | re-place | rows | ≤ 3 ms | auto |
| Transcript fling (5,000 parts) | prefetched items (pausable composition) | new items | rows | ≤ 6 ms | touch/fling boost |
| Pull to refresh | none | none | indicator | ≤ 1 ms | touch boost |

### 8.2 What keeps the budget (rules that make the numbers possible)

1. Draw- and layout-phase reads only (R3). The glow, rings, sweeps, word fade, caret, step light and refresh never
   recompose.
2. One re-laid-out block per tail update (ADR §9.3, `TailRelayoutCounterTest`).
3. `skipToLookaheadSize()` on the chat content during the container transform.
4. `placementSpec = null` in the transcript (R6).
5. `compose-stability.conf` and preserved instances (ADR §9.3); `RecompositionBudgetTest` (§9.3) fails on regressions.
6. Baseline and Startup Profiles generated from journeys that include the motions (stream, fling, open chat, back, sheet)
   [R testing-perf §9.3]; R8 full mode.
7. `highFrameRate` only while needed and only under Full; dropped under Calm (heat, battery saver) to save power.
8. Loops stop when not visible (§3.5).

### 8.3 Adaptive refresh

On Android 15 QPR1+ devices with adaptive refresh (`Display.hasArrSupport()`), views default to "Normal" (about
60 Hz); movement and size animations get a higher rate automatically; a touch boosts for a while [V rendering-chat
§7.1]. An alpha-only animation (word fade) may stay at Normal, which is why the live block votes `High` while it reveals.
`AndroidKitPlatform` logs `hasArrSupport()` and the display's supported modes into diagnostics once per launch, so the
owner's phone report shows them. Owner rule: nothing ever runs below 60 fps; `preferredFrameRate` is never set below
60.

---

## 9. Verification

### 9.1 The ladder (which check, when)

| Tier (§02 / ADR §10.3) | Motion checks |
|---|---|
| T0 (agent, one target) | the part's pure tests (`commonTest` on desktop JVM) and its desktop compose test |
| T1 (VPS, touched modules) | all of the above + Roborazzi `verify` of the part's frame captures + Konsist motion rules |
| T2 (VPS, integration) | everything + `RecompositionBudgetTest` + `ReducedMotionSettlesTest` over the whole kit manifest |
| T3 (PC emulator, one lock holder) | Maestro parity flows with animations **off** (§02 S-2) + one smoke flow with animations **on** + the device checklist §9.7 |
| T4 (PC emulator; phone only with the owner's OK) | Macrobenchmark journeys J2–J8 + Perfetto comparisons + profile regeneration |

### 9.2 Logic tests (write first; named after catalog rows where one exists)

`MotionPolicyTest`, `MotionPrefMigrationTest`, `MotionClassTableTest`, `KitMotionSpecsTest`, `MovingPartTest`,
`RevealClockTest`, `GrowOnlyTest`, `StopGuardTest`, `RowTransformGateTest`, `PredictiveBackPolicyTest`, `ShellMotionTest`,
`HeldSnapshotTest`, `StepLightPhaseTest`, `CountRollTest`, `HeldAnswerTest`, `RefreshPhaseTest`, `HapticPolicyTest`,
`GlowColoursTest`. All run in `commonTest` (desktop JVM) except `HapticConstantsTest` (Android host). Each is listed
with its rows in §3.6 and §5. Names follow the ratchet form, e.g.
`` `B-KIT-006 unknown stored motion reads Full`() ``.

### 9.3 UI tests (kit tests of new native parts)

These test the native kit parts as they are built (section 06 §16, ADR P7). They are **not** the port of the Flutter
widget tests, which the owner scheduled for later ("7400 for widgets can come later").

- Harness: `runComposeUiTest` (desktop JVM, `commonTest` UI) with `mainClock.autoAdvance = false`; Android host
  Robolectric `createComposeRule` for Android-only seams.
- `LocalKitMotion` is provided per test: `Full`, `Calm`, `Off`; the system path is tested with
  `runComposeUiTest(effectContext = object : MotionDurationScale { override val scaleFactor = 0f })`.
- **`ReducedMotionSettlesTest`** (MOT-7, B-KIT-023, B-KIT-054): for every part in the kit manifest, under Off and under
  scale 0: change its state, advance **one** frame, assert the final state is drawn and `LocalKitMotionProbe` reports
  zero running animations.
- **`FirstBuildFinalTest`** (R8): every part composed with a non-default state shows it on frame one without running.
- **`RecompositionBudgetTest`**: a counting wrapper (`:ui:testing` `RecompositionCounter`) asserts the composer
  recomposes 0 times during 3 s of glow, the transcript list 0 times during an IME inset animation, and the live block
  at most once per word landed.
- **`StillFrameTest`**: with `stillFrame = true`, every loop draws a deterministic frame (needed for goldens).
- Loops in tests: framework infinite animations are paused by the test harness's `InfiniteAnimationPolicy`
  [U: confirmed on CMP 1.13 in S8-5]; hand-written loops are banned (R4), so no test hangs.

### 9.4 Frame captures (screenshot frames)

- Tool: Roborazzi 1.77.0 (ADR §8), recorded **only on the VPS** (the golden host); the PC may compare but never record.
- Method: set the state, `mainClock.autoAdvance = false`, advance with `mainClock.advanceTimeBy(ms)`, then
  `onRoot().captureRoboImage(path)`. Loops use `stillFrame`.
- Naming: `motion/<part>/<interaction>_<t>ms_<level>_<theme>_<dir>.png`, e.g.
  `motion/chat/grow_open_180ms_full_dark_ltr.png`.
- Matrix (kept small): every capture in **dark + en**; the direction-sensitive ones (lift, page slides, predictive shift,
  live sweep, menu drop, swipe) also in **ar (RTL)**; one light capture per interaction; Calm one mid-frame; Off one
  frame (which must equal the Full end frame).
- The capture list is the union of the "Verified by" lines in §5 (about 130 images [E]). Each golden header comment
  names the regenerate command.
- **Review:** the owner's rule "one full real phone screen per image" applies to anything shown to the owner; frame
  captures are for agents and the visual auditor. For the owner, §9.7 records short emulator videos instead.

### 9.5 Macrobenchmark journeys (`:android:benchmark`, `com.android.test`)

- androidx.benchmark 1.5.0, `FrameTimingMetric` (`frameDurationCpuMs`, `frameOverrunMs`), `TraceSectionMetric` for our
  sections, `CompilationMode.Partial()`, 10 iterations, release-like `benchmark` build type, target package from the
  benchmark config of section 13 (an `applicationIdSuffix` build so it never touches the owner's install).
- Emulator: `OC_API37` on the PC, **gesture navigation** on (`adb shell cmd overlay enable
  com.android.internal.systemui.navbar.gestural`), `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`;
  relative numbers only. Absolute numbers on the NX721J only with the owner's OK.
- Fixture: the mock-model server (testing-perf §6.4); prompt "story" streams a 6,000-word reply with 3 code fences at
  30–80 tokens/s; "Fixture chat" has the 5,000-part transcript.
- Trace sections (androidx.tracing 2.0.5; never message text in names): `kit.reveal.word`, `kit.reveal.fade`,
  `kit.glow.draw`, `shell.rowTransform`, `shell.predictive`, `kit.sheet.settle`, plus section 09's `chat.applyDelta`.

| Journey | Class | Steps | Metric | Gate |
|---|---|---|---|---|
| J2 Streaming | `StreamingBenchmark` | open Fixture chat; type "story" into `chat-composer-field`; tap `chat-send-button`; wait for the last paragraph | `FrameTimingMetric` over the stream (glow running) | S-9, S-12 |
| J3 Fling | `TranscriptFlingBenchmark` | open the 5,000-part chat; 5 flings up, 5 down | `FrameTimingMetric` | S-10 |
| J4 Row → chat | `RowToChatBenchmark` | from Conversations tap the "Fixture chat" row; wait settle; tap the top-bar Back; repeat 5×; then open Settings and back | `FrameTimingMetric`, `TraceSectionMetric("shell.rowTransform")` | S-13 |
| J5 Predictive back | `PredictiveBackBenchmark` | open a chat; commit: `device.swipe(arrayOf(Point(2, y), Point(w * 6 / 10, y)), 40)`; reopen; cancel: `device.swipe(arrayOf(Point(2, y), Point(w / 2, y), Point(4, y)), 50)`; repeat from the right edge | `FrameTimingMetric` | S-13 |
| J6 Sheet | `SheetBenchmark` | open the model sheet; drag to half; fling down; reopen; predictive close | `FrameTimingMetric` | S-13 |
| J7 Keyboard | `ImeBenchmark` | tap the field; keyboard up; type three lines; keyboard down (Back) | `FrameTimingMetric` | S-13 |
| J8 Refresh | `RefreshBenchmark` | Conversations: pull to refresh 3× | `FrameTimingMetric` | S-13 |

```kotlin
// android/benchmark/src/main/kotlin/io/github/eslamasabry/oc/benchmark/motion/PredictiveBackBenchmark.kt  (sketch)
@RunWith(AndroidJUnit4::class)
class PredictiveBackBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    @Test fun commitAndCancelFromBothEdges() = rule.measureRepeated(
        packageName = BenchTarget.PACKAGE, metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(), iterations = 10, startupMode = StartupMode.WARM,
        setupBlock = { uiAutomator { startApp(BenchTarget.PACKAGE); onElement { textAsString() == "Fixture chat" }.click() } },
    ) {
        val (w, h) = device.displayWidth to device.displayHeight; val y = h / 2
        device.swipe(arrayOf(Point(2, y), Point(w * 6 / 10, y)), 40)               // commit from the left edge
        device.waitForIdle()
        uiAutomator { onElement { textAsString() == "Fixture chat" }.click() }
        device.swipe(arrayOf(Point(w - 3, y), Point(w / 2, y), Point(w - 5, y)), 50) // cancel from the right edge
        device.waitForIdle()
    }
}
```

### 9.6 Perfetto and the owner's phone

- Same journey on the Flutter reference build and the native build, recorded with `tool/perf/oc.pbtxt`
  (SurfaceFlinger FrameTimeline, by layer) and compared with the trace-processor queries of section 13; Flutter frames
  are invisible to `FrameTimingMetric`, so this is the only fair comparison (§02 S-12).
- On the NX721J, with the owner's OK each time and read-only adb only: `adb shell dumpsys gfxinfo
  io.github.eslamasabry.opencode_mobile framestats` around a streaming run, and `adb shell dumpsys display` for the
  refresh modes. Never a screen capture or a screen recording on the owner's phone.

### 9.7 Device checklist and evidence (emulator first, phone last)

Run on the API 37 emulator (gesture navigation, then 3-button navigation), an API 30 image (glow overscroll, old IME),
en and ar, Full / Calm / Off, and system "Remove animations". Record each as a short emulator screen recording
(`adb shell screenrecord --time-limit 20`, one interaction per clip) plus a slowed run with the developer animator
scale at 5× for frame inspection. Evidence goes to `docs/qa/native/motion-<YYYY-MM-DD>/README.md` (audit-trail rule)
with the build number, the commands and a pass/fail per row.

| Id | Check | Pass |
|---|---|---|
| P2-1 | slow drag + commit on a GrowFromRow chat | lands on the list with no jump; no shared-bounds flash |
| P2-2 | drag + cancel | the chat returns with no flicker; a following tap on a row still grows (gate cleared) |
| P2-3 | fling commit | carries velocity; finishes in ≤ 300 ms |
| P2-4 | RTL (Arabic), swipe from each edge | the card shifts away from the swiped edge |
| P2-5 | open a chat, scroll the list away underneath (via a new conversation arriving), Back | falls back to the Page pop |
| P2-6 | Off and "Remove animations" | no preview; plain pop |
| P2-7 | 3-button navigation, tap Back / long-press Back (Android 16) | tap = shrink into row; long-press = preview |
| ST-1 | stream the story fixture at the live edge | words fade in word by word; no jumping lines; glow turns; caret breathes |
| ST-2 | scroll up during the stream | follow stops; pill appears; tap returns |
| SS-1 | send, then double-tap where Send was | the turn is not stopped |
| OV-1..3 | overscroll top and bottom of chats, settings, transcript; API 30 | stretch (API 37) / glow (API 30); no double stretch |
| IME-1..4 | keyboard up/down at the live edge, while reading history, in a sheet, after rotation (Android 17 image) | composer and words ride the keyboard; reading position kept; sheet actions pinned; keyboard returns after rotation |
| SH-1..3 | sheet drag, fling, predictive close; dirty sheet swipe | as §5.5 |
| H-1..3 | haptic log (`qa` build logs `KitHaptic` kinds) for send, stop, long-press, switch, segment | exact sequence; nothing on taps, scroll, sheets |
| RM-1 | toggle "Remove animations" while a reply streams | everything stops on the next frame; the reply finishes as plain text |
| PH-1 | owner phone (after the emulator pass, as part of §02 S-27) | the owner's verdict on smoothness, the haptic feel (D8-1) and the predictive preview, recorded in their words |

---

## 10. Spikes (each ≤ 2 days, one agent, own worktree, emulator only unless stated)

| # | Question | Acceptance | Fallback | Agent | When |
|---|---|---|---|---|---|
| S8-1 | Option B: does Nav3 1.3-alpha + Compose 1.13-beta deferred handoff with `permitTransformDuringDeferredTransition` give a jank-free shrink into the row under the gesture? | the six P2 checks + fling velocity handoff; Android resolves to Google's nav3 binary | stay on option A | Opus | slice 0 (with P2), re-run at each Nav3 1.3 pre-release |
| S8-2 | Does the `DstOut` word-fade layer meet the stream budget on the NX721J? | J2 within S-9 on the emulator; `kit.reveal.fade` ≤ 1 ms p95 | fade the newest **line** with one vertical gradient mask instead of per-run paths; if still over, whole-block fade for newly inserted blocks only | Opus (kit-chat) | slice 2 |
| S8-3 | `imeNestedScroll` on the NX721J: does dragging the transcript move the keyboard naturally without breaking scroll-to-read? | owner-visible clip on the emulator + a tip registered | stays off (D8-2) | Sonnet | slice 3 |
| S8-4 | Haptic constants on the NX721J: are `VIRTUAL_KEY`, `KEYBOARD_TAP`, `TOGGLE_*`, `SEGMENT_TICK` distinct and pleasant? | owner listens during the phone test | §6.2 table stays; owner may pick 06's alternative (D8-1) | coordinator with owner | slice 6 phone test |
| S8-5 | `InfiniteTransition` at animator scale 0 and the CMP 1.13 test harness's `InfiniteAnimationPolicy` | loops never block `waitForIdle`; at scale 0 our guard keeps them unstarted anyway | none needed (R4 already prevents it) | Sonnet | slice 0 (K0) |
| S8-6 | Is `Modifier.preferredFrameRate` exported to `commonMain` by CMP 1.13? | compiles in common | stays a `JETPACK_AHEAD` row behind `KitPlatform.highFrameRate` (already planned) | Sonnet | slice 0 |
| S8-7 | M3 1.5 in-window `BottomSheet` used modally: scrim, TalkBack focus trap, IME, predictive scaling | TalkBack cannot reach the page under an open sheet; IME rides; preview plays | `ModalBottomSheet` behind `KitSheetHost` | Sonnet | slice 0 (K0) |
| S8-8 | Reverse layout vs normal layout when the reader is near the bottom with the growing tail partly visible (text pushed up under the eyes) | the variant where reading position holds | compensate with `scrollBy(sizeDelta)` for the first visible item, or pause growth until the reader returns | Opus (with section 09) | slice 2 |
| S8-9 | Halo cost: `Modifier.dropShadow` lambda vs three stacked strokes | `kit.glow.draw` ≤ 0.5 ms UI and no RenderThread spikes on the emulator | stacked strokes | Sonnet | slice 2 |

---

## 11. Work units (for agent briefs)

Each unit lists its write set, read set, acceptance and catalog rows. Units nest inside section 06's kit units and
section 07's proof P2, so ownership stays single.

| Unit | Slice | Inside | Agent | Write set | Acceptance | Rows | Effort [E] |
|---|---|---|---|---|---|---|---|
| M0 Motion foundation | 0 | §06 K0 | kit owner (Opus) | `:ui:design` `motion/MotionTokens.kt`, `MotionPolicy.kt`; `:ui:kit` `motion/KitMotion.kt`, `KitMotionScheme.kt`, `KitEasing`, `LocalKitMotion(Specs)`, `LocalKitMotionProbe`; `platform/AndroidHaptics.kt`; motion signals in `AndroidKitPlatform` | §3.6 tests green; `HapticConstantsTest`; Konsist R1/R4/R10/R11 live; ripple + haptic seen on the emulator (P7) | B-KIT-006, -008, B-STATE-003, B-MIGR-005 | 0.5 aw |
| M1 Predictive back + row transform A | 0 | §07 P2 | Opus | `:ui:kit` `motion/RowTransformGate.kt`, `Modifier.growFromRow`, `KitRouteTransition`; `:ui:shell` `nav/ShellMotion.kt`, entry decorator (settle count, dim, preview corners) | `RowTransformGateTest`, `PredictiveBackPolicyTest`, `ShellMotionTest`; device checks P2-1..7 | B-KIT-054 | 1 aw (+ S8-1 0.4 aw) |
| M2 Kit motion parts | 1 | §06 K1 | kit agent (Sonnet) under the kit owner | `KitSwap`(+`count`), `KitSpin`, `KitReveal`, `KitEntrance`, `KitPresence`, `KitAnimatedRows`, `KitAnimatedValue`, `KitDim`, `KitRefresh`, `KitFeedList` motion, `KitSwipeAction`, `KitArrival`, `KitTip`/`KitTour` motion, `KitMenu`/`KitUndo` motion | §5.15 lines in every KDoc; `ReducedMotionSettlesTest`, `FirstBuildFinalTest`, `FeedListMotionTest`, `RefreshPhaseTest`; frame captures recorded on the VPS | B-KIT-011, -014, -015, -019, -022, -023, -025, -031, -046, -050, -051, B-NAV-073 | 1.5 aw |
| M3 Sheet and dialog motion | 1 | §06 K0/K1 | kit owner | `KitSheetHost`, `KitSheet`, `KitSheetStep`, `KitDialog` | §5.5 tests and captures; S8-7 | B-KIT-015, -016, -032 | 0.5 aw |
| M4 Streaming motion | 2 | kit-chat | Opus (kit-chat) | `:ui:kit-chat` `stream/RevealState.kt`, `FreshFade.kt`, `GrowOnly.kt`, `StreamCaret.kt`, `MessageLift.kt`, follow + jump pill | `RevealClockTest`, `GrowOnlyTest`, `TailRelayoutCounterTest`, captures; J2 + J3 within S-9/S-10 on the emulator; S8-2, S8-8 | B-CHAT-060, -096 | 1.5 aw |
| M5 Composer and turn motion | 2–3 | kit-chat | kit-chat owner (Sonnet; Opus for the glow render-compare loop) | `ComposerGlow.kt`, `MovingPart.kt`, `KitStopKey`, field button morphs, `KitTurnLive` sweep, `KitWorkLine`/`KitStepTimeline` light, `KitToolRow` status, `KitRequestCard` motion, IME frame | `MovingPartTest`, `StopGuardTest`, `GlowColoursTest`, `StepLightPhaseTest`, `HeldAnswerTest`, `ComposerKeysUiTest`, `ImeClearanceTest`; the glow compared side by side with a Flutter build capture on the same emulator (owner rule "judge visuals hard") | B-CHAT-006, -012, -050, -063, -117, -123, B-KIT-002, -036 | 2 aw |
| M6 Benchmarks | 2 onward | §13 owner | Sonnet | `:android:benchmark` `motion/*.kt` (J2–J8), trace sections, baseline-profile journeys including the motions | journeys run on `OC_API37`; numbers stored next to the APK; first native numbers replace the [E] budgets | S-9 … S-13 | 1 aw |
| M7 Motion evidence pass | 6 (before any owner APK of the switch) | coordinator | Sonnet + coordinator review | `docs/qa/native/motion-<date>/README.md` + clips | every §9.7 row passes on the emulator; then PH-1 with the owner | — | 0.5 aw |

Brief text every motion unit carries (owner rules, motion edition): 150–250 ms; exits faster; springs only for finger-driven
or interruptible motion; no overshoot in new motion; nothing moves under Off; Calm keeps fades and folds, drops travel and
ambient loops, halves progress loops; one ambient loop per region and one moving part in the composer; native ripple,
stretch, predictive back and the MOT-11 haptics only; RTL-aware directions; a tip for any hidden gesture; kit only; read
animated values in draw or layout; say in the brief what the change removes.

---

## 12. Owner decisions (each has a default; work proceeds on it until you answer)

| # | Question | Default (built if you say nothing) | Alternative |
|---|---|---|---|
| D8-1 | Vibration feel. Keep today's exact vibrations for send, stop, finish and long-press, and use Android 14+'s own switch and segment ticks? | **Yes** (§6.2) | section 06's proposal: send = Confirm, stop = LongPress |
| D8-2 | Drag the conversation to open or close the keyboard (Google Messages style)? It is a hidden gesture and needs a tip. | **Off** at the switch; on only if spike S8-3 looks right on your phone | on, with a tip |
| D8-3 | A small tick when a swipe action passes its point, when pull-to-refresh arms, and when you copy? (Today: only copy ticks.) | **No ticks** for any of the three (MOT-11) | ticks for swipe and refresh (Android's threshold tick); copy keeps its tick |
| D8-4 | Pull to refresh: keep the app's portal drawing, or use Android's stock spinner? | **Keep the portal drawing** | stock M3 indicator |

Settled elsewhere and not asked again: Stop's place (2026-10-30), the glow (2026-10-14), no glass except the frost
(D10), no bottom bar (2026-10-20), the chat motions (2026-10-27), the bouncy lens springs (section 06 Q5).

---

## 13. Changes from Flutter, and corrections for sibling sections

### 13.1 Changes from the Flutter app (each with its reason)

| Change | Flutter | Native | Reason |
|---|---|---|---|
| Predictive back with the row transform | yields to the system builder during the gesture | the same idea as a settle-driven gate (§5.4); option B later | the demo's 700 ms latch was a guess; ADR §9.4 |
| Page transition on Android | Flutter's Android fade-forwards, 450 ms | shared axis X, 250 ms (`Page`) | ADR §9.4 names shared axis X; §02 rule 3 keeps 450 ms only for the row transform |
| Sheets | Flutter 3.47 sheets had no predictive back | M3 in-window sheet with predictive back | native feel |
| Swipe action | the row snapped back, then the list removed it | the row continues out, then the gap closes | Android's standard pattern |
| Toggle and segment haptics | `CLOCK_TICK` | `TOGGLE_ON/OFF`, `SEGMENT_TICK` on API 34+ | max native (D8-1) |
| Copy haptic | a light tick on copy | none | MOT-11; the Flutter audit left it to the owner (D8-3) |
| Streaming reveal | text shown as published, new runs fading | paced reveal (45 cps, ≤ 250 ms behind) + the same fade | the demo the owner felt used a paced reveal |

### 13.2 Corrections for sections 06 and 07 (coordinator merges)

| Where | Says | Should say | Why |
|---|---|---|---|
| §06 §4.8 springs table | "`lift` (260, 20) … sent bubble lift from 0.92" | `lift` is the **tab lens** lifting while dragged; the sent-bubble lift is a 250 ms `EaseOutCubic` tween (X3) | `kit_message_lift.dart` uses `KitMotion.standard` + `KitMotion.enter`; `lift` belongs to the lens [R] |
| §06 §4.8 `MotionTokens.EXIT = 100` | exits 100 ms | exits use `QUICK` (150 ms), as every Flutter reverse slot did; drop `EXIT` or keep it unused | parity; no Flutter part used 100 ms |
| §06 §4.9 haptics | Send = `Confirm`, Commit = `LongPress` | §6.2 table (default of D8-1) | today's constants, read from the Flutter embedding [V] |
| §06 §5.1 `LocalKitMotion` | `KitMotionState(level, reduced, travels)` | add `stillFrame`, `debugTimeScale`, `ambientLoops`, `progressLoopSpeed` (§2.7) | loops and frame captures need them |
| §07 §3.2 sketch | clears `poppedByGesture` in `LaunchedEffect(isTransitionActive)` | clear on `TransitionSettled` from the entry decorator (§5.4 gate) | `isTransitionActive` stays false for a gesture pop with disabled shared bounds, so the flag would never clear and the next tap would lose its transform |
| §03 §15.4 `RouteMotion` | `Root, SharedAxisX, GrowFromRow, SlideUp` | section 07's names: `GrowFromRow, Page, FullScreenDialog, TopLevel, Replace, Root` | one name per class; §07 is newer and complete |

---

## 14. Multiplatform notes (iOS and Linux desktop later)

| Piece | `commonMain` | Android | iOS (later) | Desktop (later) |
|---|---|---|---|---|
| Tokens, policy, specs, gate, reveal clock, glow, lift | all | — | — | — |
| Motion signals | interface | animator scale, power save, thermal | `isReduceMotionEnabled` → scale 0 | none (scale 1) |
| Back | `NavigationBackHandler`, Nav3 specs | system predictive back | edge swipe-back (CMP; Nav3 iOS defaults follow layout direction since CMP 1.13.0-alpha01) | Esc |
| Press | `KitPlatform.pressIndication` | `RippleDrawable` ripple | highlight / dim | hover fill + focus ring |
| Haptics | `KitHaptic` | `performHapticFeedback` constants | UIFeedbackGenerators | no-op |
| Overscroll | default factory | stretch / glow | bounce | none |
| IME | `imePadding` | `WindowInsetsAnimation` | safe area + keyboard insets | n/a |
| Frame rate | `highFrameRate` | `preferredFrameRate` | ProMotion via CADisplayLink (CMP) [U] | none |

An iOS design pass (ADR §12 D13) decides whether iOS keeps the container transform or uses its own push.

---

## 15. Sources

- ADR `01-architecture-decision.md` §1–§12; `02-principles-scope.md` §1.1, §4; `03-architecture-modules.md` §12.2,
  §15.3–§15.7; `06-ui-kit-design-system.md` §4.8, §4.9, §5.1, §5.6, §6, §14; `07-screens-navigation.md` §3.
- Research: `motion-native.md` §0–§12; `rendering-chat.md` §0, §4, §6, §7; `testing-perf.md` §4, §5, §9;
  `kmp-cmp-status.md`.
- Maps: `ui-kit.md` §1, UI-KIT-07 … -58, K4, K5; `chat-genui.md` §0.3, CHAT-GENUI-08, -09, -14, -21, -26, -27, -33,
  -40, -43, -56; `screens-nav.md` §1, §3.2.
- Catalog: `../catalog/BEHAVIORS.md` rows cited inline.
- Flutter source (read-only): `lib/ui/kit/kit_motion.dart`; `lib/ui/kit/motion/{kit_haptics,kit_page_transitions,
  kit_reveal,kit_animated_rows,kit_refresh,kit_tab_switcher,kit_motion_parts}.dart`; `lib/ui/kit/chat/{kit_composer_edge,
  kit_composer_classic,kit_message_lift,kit_markdown_fresh,kit_stream_caret,kit_step_light,kit_turn}.dart`;
  `lib/ui/kit/{kit_arrival,kit_tip,kit_jump_pill,kit_menu}.dart`; `lib/state/delayed_answers.dart`;
  `docs/ux-system/revamp/STANDARDS.md` (MOT-1 … MOT-15); `docs/qa/micro-motion-2026-10-27/README.md`;
  Shorebird Flutter `page_transitions_theme.dart` (`kTransitionMilliseconds = 450`).
- Demo: `/home/eslam/Storage/Code/kmp-demo/shared/src/commonMain/kotlin/io/github/eslamasabry/kmpdemo/{App.kt,
  ui/Theme.kt, ui/ChatScreen.kt, ui/Kit.kt}`.
- Checked today [V] (local Gradle cache, javap): `androidx.compose.material3:material3-android:1.5.0-alpha22`
  (`MotionScheme`, `BottomSheetKt.BottomSheet`, `sheetPredictiveBackScaling`, `RippleKt.ripple(…, Shape, …)`,
  `RippleConfiguration`, `pulltorefresh/*`); `androidx.compose.ui:ui-android:1.12.1` (`HapticFeedbackType$Companion`,
  `DefaultHapticFeedback` constant pass-through, `FrameRateCategory`, `DropShadowKt.dropShadow(Modifier, Shape, Function1)`);
  `androidx.compose.foundation:foundation-android:1.12.1` (`LazyItemScope.animateItem`, `LazyListState.requestScrollToItem`,
  `ClickableKt.combinedClickable(…, boolean hapticFeedbackEnabled, …)`, `AndroidEdgeEffectOverscrollFactory`);
  `androidx.compose.animation:animation-android:1.12.1` (`SharedTransitionScope$SharedContentConfig`,
  `skipToLookaheadSize`); `androidx.compose.animation:animation-core-android:1.12.1` (`PathEasing`, `EasingFunctionsKt`,
  `DeferredTransitionState`); `androidx.navigationevent:navigationevent-android:1.1.2`
  (`NavigationEventDispatcher.getTransitionState`); `io.flutter:flutter_embedding_release` (`PlatformPlugin`
  haptic constants).
- Web, accessed 2026-11-01: Navigation3 release notes https://developer.android.com/jetpack/androidx/releases/navigation3
  (1.2.0 stable 2026-09-23; 1.3.0-alpha01 `DeferredAnimatedContent` handoff fix; alpha02 title/url metadata; alpha03 no
  changes); androidx-main `navigation3/navigation3-ui/src/commonMain/kotlin/androidx/navigation3/ui/NavDisplay.kt`
  (transition parameters, metadata helpers, `defer`/`animateTo` under `InProgress`); `FrameRateCategory` and
  `preferredFrameRate` API reference https://developer.android.com/reference/kotlin/androidx/compose/ui/FrameRateCategory
  (CMP export not confirmed: spike S8-6). Everything else cites the research files, which list their own sources.
