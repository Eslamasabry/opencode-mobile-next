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
   it, and the Motion page says which one did. Under Off nothing moves at all. Calm keeps page moves and the grow
   from the row, as today.
5. Every animation is proved three ways: tests for the rules, frame-by-frame pictures, and speed measurements at 120 Hz,
   with no dropped frames while a reply streams.

Five small choices for you are in §12. Each has the default we build if you say nothing.

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

Where this section and the ADR differ, the ADR wins, except where an owner rule that the ADR did not consider is at
stake; then §12 asks the owner (D8-5) and §13.2 names the ADR wording to correct. Where it differs from a sibling
section, §13.2 names the difference and the reason, and the coordinator decides. The streaming code is specified once,
in section 09 §8; §5.1 gives only its motion values and rules.

### 0.4 Sources read for this section

- Research: `../research/motion-native.md` (all), `rendering-chat.md` §1, §4, §6, §7, `testing-perf.md` §4, §5, §9,
  `kmp-cmp-status.md` (versions).
- Maps: `../map/ui-kit.md` UI-KIT-07 to -10, -13, -18, -20, -22 to -30, -41, -50 to -52, K4, K5;
  `chat-genui.md` CHAT-GENUI-08, -09, -14, -21, -26, -27, -33, -40, -43, -56; `screens-nav.md` §1, §3.2.
- Flutter source (read-only, `/home/eslam/Storage/Code/wt/oc_app-batch1`): `lib/ui/kit/kit_motion.dart`,
  `motion/*.dart`, `chat/kit_composer_edge.dart`, `kit_composer_classic.dart`, `kit_message_lift.dart`,
  `kit_markdown_fresh.dart`, `kit_stream_caret.dart`, `kit_step_light.dart`, `kit_step_signal.dart`, `kit_turn.dart`
  (`_LiveSweep`), `kit_composer_status_strip.dart` (`KitComposerStop`), `kit_composer_caption.dart` (`_StopRing`,
  `_CircleKind.stopAndSend`), `kit_feed_item.dart` (`_StateSweep`), `kit_arrival.dart`, `kit_tip.dart`,
  `kit_jump_pill.dart`, `kit_icon_button.dart` (`KitIconMotion.turn`), `kit_state_view.dart`, `kit_checklist.dart`,
  `chat/kit_mic_hold.dart`, `lib/ui/screens/chats/chats_home_screen.dart` (`_open`), `lib/state/app_preferences.dart`
  (`_read`), `lib/ui/screens/settings/appearance_screen.dart` (`_motionBody`), `docs/ux-system/revamp/STANDARDS.md`
  MOT-1 to MOT-15, `docs/qa/micro-motion-2026-10-27/README.md`, and the jitter fixes J1–J10 of 2026-10-28/29
  (`c45c02031`, `d1a2daf9a`, `39be428aa`, `e268c012b`, `dd8c662b8`, `d95a6cd80`, `0380766e5`, `bbd1c1124`, `b4339921c`).
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
| M8-3 | Owner exceptions | Exactly thirteen named motions (X1–X13) may exceed 250 ms or scale, plus X14 while D8-5 keeps its default (§2.6). Each has its source. | §02 rule 3; MOT-2; MOT-5. |
| M8-4 | Motion levels | `Full`, `Calm`, `Off`, resolved by one pure function from the system animator scale, the user's choice, Saver, battery saver and heat (§3). It returns the level **and the reason**, so the Motion page can say why motion is lower than chosen. | B-KIT-006/-008; MOT-12 ("the page says so"); MOT-13; demo bug (scale read once). |
| M8-5 | How Off works | Every kit spec builder returns `snap()` under Off; `KitMotionScheme(off)` for wrapped M3 parts; `EnterTransition.None`/`ExitTransition.None` for routes; loops never start. Compose's own animator-scale handling covers the system "Remove animations" path. | No global switch exists in Compose for an in-app Off (motion-native §9.1). |
| M8-6 | Calm, and loops | **Calm means what it means today:** ambient loops stop and small travels (count rolls, menu drops, rises, the lift) become fades. Page transitions, the predictive preview and the grow from the row **still play** under Calm (Flutter checks only `reduced` for them). Loops come in two kinds. **Progress loops** (live-line sweep, feed-row and step-name sweeps, step light, spinners) run under Full and Calm, and are still under Off; the live-line sweep, a running step's light and spinners run at half speed under Calm, the two name sweeps keep 2200 ms (Flutter parity). **Ambient loops** (glow travel, halo and caret breath, refresh breath, the Signal go-lamp breath, the voice "Stop reading" ring) run only under Full. All loops stop when not visible. | `KitMotion.travels`/`reduced` in `kit_motion.dart`; `KitAndroidPageTransitionsBuilder` checks only `reduced`; `_LiveSweep`, `_StepLight`, `_StateSweep`, `_StepSweep`; MOT-6, MOT-15. |
| M8-7 | One moving part in the composer | One pure resolver decides: glow travels, or the voice-mode "Stop reading" circle's ring turns, or nothing (MOT-15). The running-turn Stop is a chip with no ring (§5.2). | Owner rule 2026-10-03; 2026-10-30 Stop chip. |
| M8-8 | List → chat | Option A at the switch: container transform on a row tap and on button-back; disabled while a back gesture runs. Only a row's own tap claims an origin; a gate returns to Idle when the open is abandoned or when no entry transition is running (level-triggered, no timer). The Conversations list holds its row order in the **feed ViewModels**, not in the list part, because Nav3 disposes a covered entry. Option B is a spike that becomes default when Nav3 1.3 and Compose 1.13 are stable. | ADR §9.4; the demo's 700 ms latch was a timing guess; Flutter `f7e775dd3`, `KitPageOrigin.claimWithin`/`forget`. |
| M8-9 | Predictive back preview | The Material **full-screen** values: exit scale 1 → 0.9, fade-out from 35 % progress, easing `(0.1, 0.1, 0, 1)`. Two additions that are not in the full-screen spec and are settled by P2-9: a shift away from the swipe edge borrowed from the shared-element preview (up to `width/20 − 8 dp`) [U] and rounded corners up to 28 dp [E]. Material's enter scale 1.1 → 1 for the page underneath is **dropped on purpose**: the list holds still. Shift follows the **swipe edge**, never the layout direction. | Material predictive-back values (motion-native §3.1); Arabic users swipe from either edge. |
| M8-10 | Sheets | `KitSheetHost` on the in-window M3 `BottomSheet`: drag, fling, settle and predictive back are Google's; the kit adds the scrim, the dirty-sheet rule and, under Off, its own back handler that commits without a preview. Wide-window end-side sheets are a kit `KitSideSheet` (M3 1.5 has none). | ADR §9.4; one window = one inset model. |
| M8-11 | Streaming | Specified **once**, in section 09 §8 (`RevealRegistry`, `drive`, `kitFreshFade`, growth). This section gives only the motion values and levels (§5.1): a paced reveal (base 45 characters per second, never more than 250 ms behind, snapped to whole words), each revealed run fading in over 250 ms in a draw-phase mask, the live block growing on `spring(1f, 700f)` (growth only), a breathing caret under Full. Text already on screen never replays. | The demo's reveal felt smooth to the owner; the Flutter fade and grow values are owner-approved (2026-10-27); one write set (unit S2-KC). |
| M8-12 | Stop | Per the 2026-10-30 owner override, the field button changes only between mic, Send and, for agents that cannot be steered, the amber "Stop and send" pill. The running-turn Stop is the `KitComposerStop` **chip** ("Stop", a stop icon) at the trailing end of the status strip for the whole running turn; it fades in and out with `KitSwap`. "Send-to-stop" is therefore a hand-off, not a morph (§5.2). | Memory `chat-kit-decisions-2026-10-02`; `kit_composer_status_strip.dart`. |
| M8-13 | Haptics | One `KitHaptic` enum, mapped once in `AndroidKitPlatform` to `View.performHapticFeedback` constants (not `LocalHapticFeedback`: §13.2 corrects the ADR wording). Default constants keep today's Flutter feel for Send, Commit, Done and LongPress, and use Android's semantic constants for switches and segments on API 34+ (§6). Every call site that vibrates today keeps its vibration (§6.2). | Parity with what the owner feels today; MOT-11; owner rule "max native". |
| M8-14 | Overscroll, fling, ripple | Platform defaults, never replaced app-wide: stretch on API 31+, glow on 26–30, platform spline fling, `RippleDrawable` ripple. | ADR §9.4; owner rule 2026-10-26. |
| M8-15 | IME | The chat body sits in `imePadding()`, so the reverse-layout transcript and the composer ride the keyboard frame by frame; the composer's clearance is a measured spacer item, never `contentPadding`. `imeNestedScroll` stays off until a spike and the owner say yes (D8-2). | Docs: Spacer over contentPadding; one layout pass per IME frame. |
| M8-16 | Frame rate | `KitPlatform.highFrameRate(active)` only while a reveal, transform or predictive preview runs, and only under Full. Never below 60 fps. | ADR §9.3; owner "nothing less than 60". |
| M8-17 | Proof | Every interaction has a logic test, a frame-capture set and, where it moves a lot of pixels, a Macrobenchmark journey (§9). | §02 S-9 to S-13. |
| M8-18 | No unrequested movement | Nothing that appears, changes or leaves on its own may move content the person is reading (rule R13). Timed notices fold at the next page change; stand-ins are replaced in place with no entrance. | The owner's jitter fixes J1–J10 (2026-10-28/29). |

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
| `ROW_BOUNDS_MS` | 360 | the growing box's bounds, row → full window (the first 80 % of `ROW_OPEN`) | §5.4 | `_GrowFromRow` interval [0, 0.8] |
| `ROW_HANDOFF_MS` | 90 | the row's words fade out; also the delay before the chat's content fades in; also the dim rising | §5.4 | interval [0, 0.2] of `ROW_OPEN` |
| `ROW_CONTENT_IN_MS` | 180 | the chat's content fades in, after `ROW_HANDOFF_MS` | §5.4 | interval [0.2, 0.6] |
| `ROW_CONTENT_OUT_MS` | 120 | on return, the chat's content fades out | §5.4 | interval [0, 0.4] of `ROW_RETURN` |
| `ROW_HANDBACK_DELAY` / `ROW_HANDBACK_MS` | 240 / 60 | on return, the row's words fade back in | §5.4 | interval [0.8, 1] |
| `PAGE_FADE_OUT_MS` / `PAGE_FADE_IN_DELAY` | 75 / 75 | shared axis X: the leaving page fades out in 75 ms; the new page fades in over the remaining 175 ms of `STANDARD` | §5.9 | Material shared axis (only if D8-5 picks it) |
| `ANDROID_PAGE_MS` | 450 | `Page` on Android while D8-5 keeps its default: Android's fade-forwards (exception X14) | §5.9 | `FadeForwardsPageTransitionsBuilder` [V] |
| `ARRIVAL_FADE_MS` | 900 | an arrival wash fading off its row (exception X10) | §5.6 | `KitMotion.entrance` used by `KitArrival` |
| `ICON_TURN_MS` | 200 | the Settings gear's quarter turn | §5.15 | `KitIconButton.turnDuration` |
| `GLOW_FADE` | 450 | the glow's strength easing in and out | §5.3 | `edgeLightHueFade` |
| `FROST_FADE` | 180 | chat header turning from solid to frost while content scrolls under it | §5.15 | `KitFrostHeader` |
| `STYLE_FADE` / `STATE_FADE` | 200 / 120 | a surface's fill, edge and shadow when the Style or a state changes | §5.15, B-KIT-046 | `kitSurfaceBox` |
| `PRESS_HOLD` | 100 | least time a pressed state stays visible (a state, not a movement; holds under Off) | §5.15 | `pressHold` |
| `STOP_IGNORE_AFTER_SEND` | 800 | the Stop chip ignores taps for this long after a send or after it appears (fix `49d067f2d`; `chat_stop_guard_test.dart`) | §5.2 | composer |
| `PREDICTIVE_SPAN` | 300 | the time axis a predictive preview is seeked along; a committed pop finishes the remaining `(1 − progress) × 300` ms | §5.4, §5.9 | new (Material) |
| `ESCALATE_AFTER`, `UNDO_WINDOW`, `COPIED_HOLD`, `TYPING_SETTLE`, `HELD_ANSWER`, `ORIGIN_CLAIM_WITHIN` | 8000, 8000, 2000, 300, 3000, 10000 | waits and holds, not motion; named so no literal appears | status, undo, copy, search, request card, the row origin claim (§5.4) | Flutter constants; `DelayedAnswers.window`; `KitPageOrigin.claimWithin` |
| `ARRIVAL_HOLD`, `TIP_HOLD`, `TIP_FADE` | 2400, 4000, 200 | how long an arrival wash and a tip stay; the tip's own fade | §5.8, §5.15 | `KitArrival.hold`, `KitTip.defaultHold`, `KitTip.fade` |

### 2.2 Easings

| Token | Compose easing | Use |
|---|---|---|
| `Enter` | `CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)`, Flutter's `Curves.easeOutCubic` control points. **Not** androidx `EaseOutCubic`, which is (0.33, 1, 0.68, 1), a different curve [V slice-0 P7 D2; 06 §4.8 had it right] | anything that runs forward in time: arrivals, fades in, word fade, lift, page fades, and a part leaving "forward" (Flutter rule: `enter` for any 0 → 1 progress) |
| `Exit` | `CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)`, Flutter's `Curves.easeInCubic` [V pinned Flutter `curves.dart:1568`]; androidx `EaseInCubic` is a different curve [U exact values], same correction as `Enter` | an accelerating exit only (a full-screen page sliding away, §5.9). Flutter's `exit` curve was used only in reverse slots; run forward in time it equals `EaseOutCubic` (worked in §5.4), so ordinary fade-outs use `Enter` |
| `Emphasized` | `PathEasing` of Material's emphasized path: `M0,0 C0.05,0 0.133333,0.06 0.166666,0.4 C0.208333,0.82 0.25,1 1,1` | the container transform's bounds, both directions (§5.4); Android's fade-forwards page (D8-5 default; Flutter's `easeInOutCubicEmphasized` is the same path) |
| `PredictiveBack` | `CubicBezierEasing(0.1f, 0.1f, 0f, 1f)` | the predictive preview (Material) |
| `Pulse` | `CubicBezierEasing(0.42f, 0f, 0.58f, 1f)` | the glow halo's breath |
| `Steady` | `LinearEasing` | sweeps, laps, and the step light's waiting breathe (Flutter drives it uncurved) |
| `StepEase` / `StepOut` | `CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)` (Flutter `easeInOutQuad`) / `CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)` (`easeOutQuad`) | the step light (§5.7) and the stream caret's breath (`kit_stream_caret.dart` uses `KitMotion.stepEase`) |
| `Land` | `CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)` (`easeOutBack`) | the step light's glyph return only (Flutter parity); drawings later (D11). Never controls. |

### 2.3 Springs

Compose `spring(dampingRatio, stiffness)`, unit mass. Settle time is to within 2 % of the target, computed from the
spring equation [E, motion-native §2.1 method].

| Token | ζ / stiffness | Settles in | Overshoot | Use |
|---|---|---|---|---|
| `SpatialFast` | 0.9 / 1400 | ≈ 126 ms | 0.2 % | chevron turn, in-page tab indicator, segmented indicator, small drags released, composer height |
| `Spatial` | 0.9 / 700 | ≈ 178 ms | 0.2 % | page shared-axis travel, list placement, sheet settle (via `KitMotionScheme`), predictive-cancel return, composer height |
| `SpatialSlow` | 0.9 / 300 | ≈ 271 ms | 0.2 % | nothing at the switch; reserved for wide-screen panes |
| `LargeTravel` | 1.0 / 550 | ≈ 247 ms | 0 | full-height or full-width slides (FullScreenDialog, end-side sheet), where even 0.2 % would show as several pixels |
| `ReplyGrow` | 1.0 / 700 | ≈ 210 ms per new line | 0 | the live reply block growing (Flutter `replyGrow` (700, 53), ζ = 53 / (2·√700) ≈ 1.00) |
| `EffectsFast` / `Effects` / `EffectsSlow` | 1.0 / 3800, 1600, 800 | 95 / 146 / 206 ms | 0 | only inside `KitMotionScheme` for wrapped M3 parts' alpha and colour |

Where these come from: the M3 motion token files. `MotionScheme.standard()` uses exactly `SpatialFast`/`Spatial`/
`SpatialSlow` and the three effects springs; `MotionScheme.expressive()` differs only in its spatial springs
(0.6 / 800, 0.8 / 380, 0.8 / 200), which overshoot up to 9.5 % [V, motion-native §2.1]. This app takes the standard
set. The four Flutter tab-lens springs (`lensLead` and friends, ζ 0.62–0.81) are ported unchanged only for the rail's
and sidebar's selection lens (the one rail indicator spec; §5.9 says the same), per section 06 §4.8; their flattening
is section 06's owner question Q5 and is not repeated here. If Q5 flattens them, the rail indicator moves on
`SpatialFast`.

### 2.4 Loops and rates

| Token | Value | Loop kind (§3.3) | Calm | Off |
|---|---|---|---|---|
| `GLOW_CLASSIC_LAP` | 3600 ms per lap (the app's own speed; no Slow/Fast choice since the 2026-10-23 Appearance cuts) | ambient | still ring | none (Saver: one still hairline) |
| `GLOW_SOFT_LAP` | 8000 ms | ambient | still at half strength | none |
| `HALO_BREATHS_PER_LAP` | 2 (halo alpha 0.15 ↔ 0.35) | ambient | held at 0.25 | none |
| `STOP_RING_LAP` | 2000 ms; only the voice-mode "Stop reading" circle has the ring, and it turns only when the glow is not travelling (MOT-15) | ambient (Full only, Flutter `_StopRing` checks `KitMotion.loops` and `_movingPart`) | still track | still track |
| `LIVE_SWEEP_LAP` | 2200 ms; Calm 4400 ms | progress | half speed | still words |
| `STEP_SWEEP` | 2200 ms (`KitMotion.stepSweep`): the "Working" word on a running Conversations row (`_StateSweep`) and a running step's name in the timeline (`_StepSweep`) | progress | same speed (Flutter parity) | still words |
| `STEP_LAP` / `STEP_LAP_CALM` | 2000 / 4000 ms. A **running** step: 2000 (Calm 4000). A **background** step: always 4000 at 0.6 intensity. **Stalled** with the connection down: the lap is held where it is, at 0.5 intensity | progress | half speed (running) | still node |
| `STEP_BREATHE` | 1600 ms, repeat with reverse: a **waiting** step's attention outline, strength 0.5 ↔ 1; also the Signal go-lamp's breath (strength 0.75 ↔ 1) | waiting outline: progress; Signal lamp: ambient | waiting: runs; Signal lamp: still | still |
| `STEP_TICK` + `STEP_SPRING` | 600 + 260 ms: the **done** ending (success tick, then the glyph grows back from 0.4 by size on `Land`) | one-shot (exception X11) | as Full | none |
| `STEP_FLASH` | 300 ms: the **failed** ending (a `text2` outline fades out, then the failed mark) | one-shot (X11) | as Full | none |
| `STEP_FADE` | 200 ms: the **stopped** ending (the accent outline fades); also the background-work line's fade | one-shot (X11) | as Full | none |
| `CARET_BREATH` | 1100 ms **each way** (repeat with reverse; one full breath is 2.2 s): alpha 1 → 0.25 on `StepEase` | ambient | still caret (alpha 1) | still caret |
| `SPINNER_LAP` | 1333 ms (M3 indeterminate cycle) | progress | half speed | still dot |
| `CONTEXT_VALUE`, `CONTEXT_COLOR`, `CONTEXT_HALO`, `CONTEXT_DRAIN`, `CONTEXT_SPIN` | 600, 320, 700, 900, 1200 ms | value changes of the context ring (progress representation, kept for parity) | fades only | instant |
| `REFRESH_BREATH` | 4000 ms (`breath`) | ambient | still portal | still portal |
| Reserved (not at the switch, D11) | `CELEBRATION` 1400, `BREATH` 4000, live background 12 ticks/s with 30 s idle rest. (`ENTRANCE` 900 is no longer reserved: the arrival wash uses it as `ARRIVAL_FADE_MS`, X10.) | ambient | — | — |

### 2.5 Distances, scales and ratios

| Token | Value | Use |
|---|---|---|
| `PAGE_TRAVEL` | 30 dp | shared-axis X page travel (Flutter `KitPageTransitionsBuilder.travel`; used on Android only if D8-5 picks shared axis) |
| `ANDROID_PAGE_SHIFT` | 0.25 of the width | fade-forwards: the new page slides in from 25 % of the width at the end edge; the page under it slides 25 % toward the start (D8-5 default) |
| `FADE_THROUGH_SPLIT` | 1/3 | fade-through: the leaving content fades out in the first third (83 ms of `STANDARD`), the chosen one fades in over the rest |
| `ENTRANCE_RISE` | 8 dp (`space2`) | entrances, jump pill, late-arriving feed rows, undo bar (Full only) |
| `MENU_DROP` | 8 dp | a menu drops from its anchor (rises when it opens above) (Full only) |
| `TIP_RISE` | 4 dp | a tip rises toward its anchor (Full only) |
| `COUNT_ROLL_SHARE` | 0.6 | a rolling count travels 60 % of its own height |
| `MARK_FROM` | 0.9 | a mark (check, dot, tick, option mark, guide chip mark) and the refresh disc never appear from nothing: they fade in from 0.9 scale (Full only; exception X12) |
| `LIFT_START_SCALE` | 0.92 | the sent bubble's starting scale (exception) |
| `PREDICTIVE_MIN_SCALE` | 0.9 | predictive preview end scale (Material full-screen) |
| `PREDICTIVE_FADE_AT` | 0.35 | the preview starts fading at 35 % of the span (Material full-screen) |
| `PREVIEW_EDGE_SHIFT` | `width/20 − 8 dp` | the preview's shift away from the swipe edge: Material's **shared-element** preview value, borrowed for the full-screen preview [U: P2-9 decides keep or drop] |
| `PREVIEW_CORNER` | 28 dp | the preview card's corners at full progress: our choice, in no Material source [E: P2-9 decides the value against the phone's own back preview] |
| `FSD_PREVIEW_SLIDE` / `FSD_PREVIEW_SCALE` | 0.10 of the height / 0.95 | the most a `FullScreenDialog` moves down and shrinks during its predictive preview (§5.9.1; exception X13) |
| `ROW_CORNER` | 20 dp (`bubbleCorner`) | the growing box's corners at the row end of the transform |
| `DIM_MOST` | 0.32 | the dim over the page under a growing chat or a sheet (`roles.scrim` at this alpha) |
| Row transform tracks | see `ROW_BOUNDS_MS` … `ROW_HANDBACK_MS` in §2.1 | the transform's tracks are durations, not ratios, so `KitMotionSpecs` builds them directly (§2.7) |
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
| X8 | Loops (glow lap, sweeps, step light, waiting breathe, Signal lamp, voice Stop ring, caret, spinner, refresh breath) | §2.4 | each a Flutter feature the owner has seen; MOT-6, MOT-15 |
| X9 | Context ring value moves (600–1200 ms) | §2.4 | Flutter parity; a value shown as progress, not a transition |
| X10 | Arrival wash fades off its row | 900 ms fade (`ARRIVAL_FADE_MS`) after a 2400 ms hold | Flutter `KitArrival` (`KitMotion.entrance`); B-NAV-073 |
| X11 | Step light endings | done: tick 600 ms, then the glyph grows from 0.4 to 1 over 260 ms on `Land` (overshoots, by size); failed: 300 ms; stopped: 200 ms | Flutter `kit_step_light.dart`; the owner restored the step light after a snapshot commit deleted it |
| X12 | Marks and the refresh disc scale from `MARK_FROM` (0.9) | `QUICK`, scale 0.9 ↔ 1, Full only | Flutter `KitMotion.markFrom` (option cards, guide chips, done checks, `KitRefresh`) |
| X13 | `FullScreenDialog` predictive preview (scale to 0.95, down by up to 10 %) | follows the finger; commit finishes as a fade (§5.9.1) | platform behaviour, as X7 |
| X14 | `Page` on Android = Android's fade-forwards (D8-5 default only) | 450 ms, slide 25 % + fade, `Emphasized` | Flutter `KitAndroidPageTransitionsBuilder`; owner rule 2026-10-26 "max Android native". Removed from this table if D8-5 picks shared axis X |

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
    // the row transform's tracks (§5.4) and the page specs (§5.9)
    public const val ROW_BOUNDS_MS: Int = 360
    public const val ROW_HANDOFF_MS: Int = 90
    public const val ROW_CONTENT_IN_MS: Int = 180
    public const val ROW_CONTENT_OUT_MS: Int = 120
    public const val ROW_HANDBACK_DELAY: Int = 240
    public const val ROW_HANDBACK_MS: Int = 60
    public const val PAGE_FADE_OUT_MS: Int = 75
    public const val PAGE_FADE_IN_DELAY: Int = 75
    public const val ANDROID_PAGE_MS: Int = 450                // D8-5 default (X14)
    public const val ARRIVAL_FADE_MS: Int = 900                // X10
    public const val ICON_TURN_MS: Int = 200
    // loops (ms per lap / breath)
    public const val GLOW_CLASSIC_LAP: Int = 3600
    public const val GLOW_SOFT_LAP: Int = 8000
    public const val STOP_RING_LAP: Int = 2000
    public const val LIVE_SWEEP_LAP: Int = 2200
    public const val STEP_SWEEP: Int = 2200
    public const val STEP_LAP: Int = 2000
    public const val STEP_LAP_CALM: Int = 4000
    public const val STEP_BREATHE: Int = 1600
    public const val STEP_FLASH: Int = 300
    public const val STEP_TICK: Int = 600
    public const val STEP_SPRING: Int = 260
    public const val STEP_FADE: Int = 200
    public const val CARET_BREATH: Int = 1100                  // each way; repeat with reverse
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
    public const val ANDROID_PAGE_SHIFT: Float = 0.25f
    public const val FADE_THROUGH_SPLIT: Float = 1f / 3f
    public const val COUNT_ROLL_SHARE: Float = 0.6f
    public const val MARK_FROM: Float = 0.9f
    public const val LIFT_START_SCALE: Float = 0.92f
    public const val PREDICTIVE_MIN_SCALE: Float = 0.9f
    public const val PREDICTIVE_FADE_AT: Float = 0.35f
    public const val FSD_PREVIEW_SLIDE: Float = 0.10f
    public const val FSD_PREVIEW_SCALE: Float = 0.95f
    public const val CARET_LOW_ALPHA: Float = 0.25f
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
    /** Small travels (rolls, drops, rises, the lift): Full only. Calm turns them into fades (Flutter `travels`). */
    val travels: Boolean get() = level == MotionLevel.Full
    /** Route transitions, the row transform and the predictive preview: Full **and Calm** (Flutter checks only `reduced`). */
    val transforms: Boolean get() = level != MotionLevel.Off
    val ambientLoops: Boolean get() = level == MotionLevel.Full && !stillFrame
    /** Lap-speed multiplier for the half-speed progress loops: 1 Full, 0.5 Calm, 0 Off (still). */
    val progressLoopSpeed: Float get() = if (stillFrame) 0f else when (level) {
        MotionLevel.Full -> 1f; MotionLevel.Calm -> 0.5f; MotionLevel.Off -> 0f
    }
}

/**
 * The only place tween/spring/keyframes are created (Konsist: §4 rule R1). Every builder returns snap() under Off
 * and scales with debugTimeScale; kit, kit-chat and shell files name a builder, never a number.
 */
@Stable
public class KitMotionSpecs internal constructor(private val s: KitMotionState) {
    private fun ms(v: Int) = (v * s.debugTimeScale).toInt()
    private fun k(stiffness: Float) = stiffness / (s.debugTimeScale * s.debugTimeScale)
    private fun <T> t(d: Int, e: Easing = EaseOutCubic, delay: Int = 0): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else tween(ms(d), delayMillis = ms(delay), easing = e)

    public fun <T> enter(): FiniteAnimationSpec<T> = t(STANDARD)
    public fun <T> quick(): FiniteAnimationSpec<T> = t(QUICK)
    public fun <T> exit(): FiniteAnimationSpec<T> = t(QUICK)
    public fun <T> spatialFast(): FiniteAnimationSpec<T> = if (s.reduced) snap() else spring(SPATIAL_ZETA, k(SPATIAL_FAST_K))
    public fun <T> spatial(): FiniteAnimationSpec<T> = if (s.reduced) snap() else spring(SPATIAL_ZETA, k(SPATIAL_K))
    public fun <T> largeTravel(): FiniteAnimationSpec<T> = if (s.reduced) snap() else spring(1f, k(LARGE_TRAVEL_K))
    public fun <T> replyGrow(): FiniteAnimationSpec<T> = if (s.reduced) snap() else spring(1f, k(REPLY_GROW_K))
    public fun <T> glowFade(): FiniteAnimationSpec<T> = t(GLOW_FADE)
    public fun <T> iconTurn(): FiniteAnimationSpec<T> = t(ICON_TURN_MS)
    public fun <T> arrivalFadeOut(): FiniteAnimationSpec<T> = t(ARRIVAL_FADE_MS)
    public fun <T> arrivalScroll(): FiniteAnimationSpec<T> = t(STANDARD, KitEasing.Emphasized)

    // §5.4 the row transform's tracks
    public fun <T> rowBounds(opening: Boolean): FiniteAnimationSpec<T> = t(if (opening) ROW_BOUNDS_MS else ROW_RETURN, KitEasing.Emphasized)
    public fun <T> rowHandOff(): FiniteAnimationSpec<T> = t(ROW_HANDOFF_MS)
    public fun <T> rowContentIn(): FiniteAnimationSpec<T> = t(ROW_CONTENT_IN_MS, delay = ROW_HANDOFF_MS)
    public fun <T> rowContentOut(): FiniteAnimationSpec<T> = t(ROW_CONTENT_OUT_MS)
    public fun <T> rowHandBack(): FiniteAnimationSpec<T> = t(ROW_HANDBACK_MS, delay = ROW_HANDBACK_DELAY)
    public fun <T> rowDim(opening: Boolean): FiniteAnimationSpec<T> =
        if (opening) t(ROW_HANDOFF_MS) else t(ROW_RETURN, KitEasing.Steady)

    // §5.9 pages and fade-through
    public fun <T> pageFadeOut(): FiniteAnimationSpec<T> = t(PAGE_FADE_OUT_MS)
    public fun <T> pageFadeIn(): FiniteAnimationSpec<T> = t(STANDARD - PAGE_FADE_IN_DELAY, delay = PAGE_FADE_IN_DELAY)
    public fun <T> androidPage(): FiniteAnimationSpec<T> = t(ANDROID_PAGE_MS, KitEasing.Emphasized)      // D8-5 default
    public fun <T> androidPageFadeIn(): FiniteAnimationSpec<T> = t(ANDROID_PAGE_MS * 3 / 4, KitEasing.Steady)
    public fun <T> androidPageFadeOut(): FiniteAnimationSpec<T> = t(ANDROID_PAGE_MS / 4, KitEasing.Steady)
    public fun <T> fullScreenDialogOut(): FiniteAnimationSpec<T> = t(QUICK, KitEasing.Exit)
    private val split get() = (STANDARD * FADE_THROUGH_SPLIT).roundToInt()                                 // 83 ms
    public fun <T> fadeThroughOut(): FiniteAnimationSpec<T> = t(split)
    public fun <T> fadeThroughIn(): FiniteAnimationSpec<T> = t(STANDARD - split, delay = split)

    // §5.4, §5.9.1 predictive previews: seeked by the gesture; the shell never asks for them under Off
    public fun <T> predictiveScale(): FiniteAnimationSpec<T> = t(PREDICTIVE_SPAN, KitEasing.PredictiveBack)
    public fun <T> predictiveSlide(): FiniteAnimationSpec<T> = t(PREDICTIVE_SPAN, KitEasing.PredictiveBack)
    public fun predictiveFade(): FiniteAnimationSpec<Float> = if (s.reduced) snap() else keyframes {
        durationMillis = ms(PREDICTIVE_SPAN); 1f at 0; 1f at ms((PREDICTIVE_SPAN * PREDICTIVE_FADE_AT).toInt()); 0f at ms(PREDICTIVE_SPAN)
    }

    /** One lap of a loop; callers create it only when KitMotionState allows that loop class (R4). */
    public fun lap(lapMillis: Int): InfiniteRepeatableSpec<Float> =
        infiniteRepeatable(tween(ms(lapMillis), easing = LinearEasing), RepeatMode.Restart)
    /** A breath (caret, waiting outline, Signal lamp): one way per call, repeated with reverse. */
    public fun breath(halfMillis: Int, easing: Easing): InfiniteRepeatableSpec<Float> =
        infiniteRepeatable(tween(ms(halfMillis), easing = easing), RepeatMode.Reverse)
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
    public val Enter: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)   // Flutter easeOutCubic, NOT androidx EaseOutCubic (P7 D2)
    public val Exit: Easing = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)  // Flutter easeInCubic
    public val Emphasized: Easing = PathEasing(Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    })
    public val PredictiveBack: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
    public val Pulse: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    public val Steady: Easing = LinearEasing
    public val StepEase: Easing = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
    public val StepOut: Easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
    public val Land: Easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)
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
| Battery saver | `PowerManager.isPowerSaveMode` + `ACTION_POWER_SAVE_MODE_CHANGED` (API 21+, safe at minSdk 26) | yes | system |
| Heat | `PowerManager.addThermalStatusListener`, status ≥ `THERMAL_STATUS_SEVERE`. **API 29+ only**: guarded by `Build.VERSION.SDK_INT >= 29`; below 29 `severeHeat = false` (no listener, no reflection) | yes (29+) | system |
| Glow | **one three-way choice** (Off / Classic / Soft ring): `oc.effectsActivityGlow` (default on) + `oc.effectsGlowStyle` (classic default). Its colours and speed are the app's own (B-KIT-002, B-CHAT-006; Flutter `app_preferences.dart` `_read`: "The reply glow is one choice … its colours and speed are the app's own") | yes | same as Motion |
| Retired keys | `oc.effectsGlowColours`, `oc.effectsGlowSpeed` (ignored since the 2026-10-23 Appearance cuts), `oc.effectsHaptics`, `oc.effectsGlass` (ignored: "Glass and Vibration were switched off: both are fixed now"). The first-launch migration (section 05) **deletes** all four and never maps them to a choice. A future Vibration switch uses a **new** key name, so a stale `oc.effectsHaptics = false` can never turn haptics off | — | deleted at migration |

iOS (later): `UIAccessibility.isReduceMotionEnabled` + its notification maps to scale 0. Desktop: none (scale 1).

### 3.2 The resolver (pure Kotlin, `:ui:design`)

Section 06 §4.8 owns this function. Restated so this section reads alone:

```kotlin
public enum class MotionLevel { Full, Calm, Off }
public data class MotionSignals(val systemScale: Float, val powerSave: Boolean, val severeHeat: Boolean)

/** Why the level is what it is; the Appearance Motion row turns it into a plain note (table below). */
public enum class MotionReason { System, Saver, User, BatterySaver, Heat }
public data class MotionResolution(val level: MotionLevel, val reason: MotionReason)

public fun resolveMotion(s: MotionSignals, user: MotionLevel, saver: Boolean): MotionResolution = when {
    s.systemScale == 0f -> MotionResolution(MotionLevel.Off, MotionReason.System)           // system always wins
    saver -> MotionResolution(MotionLevel.Off, MotionReason.Saver)                           // Saver = motion off
    user == MotionLevel.Off -> MotionResolution(MotionLevel.Off, MotionReason.User)
    user == MotionLevel.Calm -> MotionResolution(MotionLevel.Calm, MotionReason.User)
    s.severeHeat -> MotionResolution(MotionLevel.Calm, MotionReason.Heat)                    // MOT-13
    s.powerSave -> MotionResolution(MotionLevel.Calm, MotionReason.BatterySaver)             // MOT-13
    else -> MotionResolution(MotionLevel.Full, MotionReason.User)
}
```

**What the Motion row says** (Settings › Appearance › Motion, under the three choices; the note replaces the chosen
level's hint, as `_motionBody` does today). Plain words; the level the person chose stays selected.

| Reason (when it lowers the choice) | English (`app_en.arb`) | Arabic (`app_ar.arb`) |
|---|---|---|
| `System` (chosen ≠ Off) | existing `effectsMotionSystemOff`: "Your phone’s Remove animations is on, so nothing moves whatever you choose here" | existing key's Arabic |
| `Saver` | existing `appearanceSaverMotion`: "Saver keeps motion off." | existing key's Arabic |
| `BatterySaver` (chosen Full) | new `effectsMotionBatterySaver`: "Battery saver is on, so motion stays calm until it is off." | "توفير البطارية مفعّل، لذا تبقى الحركة هادئة حتى توقفه." |
| `Heat` (chosen Full) | new `effectsMotionHeat`: "Your phone is hot, so motion stays calm until it cools down." | "هاتفك ساخن، لذا تبقى الحركة هادئة حتى يبرد." |
| `User` | no note: the chosen level's own hint shows | — |

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
| **Travel** | small translations: rise, drop, roll, chevron turn, lift, list placement. (The Settings gear's quarter turn is not in this class: Flutter turns it under Calm too, §5.15.) | yes | becomes a cross-fade of `QUICK` | instant |
| **Transform** | route transitions: container transform (`GrowFromRow`), `Page`, slide-up `FullScreenDialog`, fade-through | yes | **yes, as Full** (Flutter parity: `KitAndroidPageTransitionsBuilder` and `_GrowFromRow` check only `reduced`), but no `highFrameRate` vote | instant |
| **Follow** | anything under the finger: sheet drag, pull distance, predictive preview, overscroll stretch, IME | follows | follows | sheet and pull follow; predictive preview is **not drawn** (back commits on release); stretch is the platform's |
| **Settle** | the spring after a finger lets go: sheet settle, predictive cancel, refresh disc return | spring | spring | snap |
| **Progress loop** | live-line sweep, running step light, spinner (half speed under Calm); feed-row "Working" sweep, step-name sweep, waiting step's breathe (same speed under Calm) | full speed | half or same speed (per part, §2.4) | still frame |
| **Ambient loop** | glow travel, halo breath, caret breath, refresh breath, Signal go-lamp breath, voice "Stop reading" ring | runs | still frame | none or still (per part) |
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
  (`LocalLifecycleOwner.current.lifecycle.currentStateAsState() >= STARTED`).
- In a **single-pane** scene a covered page needs nothing more: once the forward transition settles, `NavDisplay`
  composes only the top scene, so the covered entry's parts (and their loops) leave composition. Its state survives
  only through the saveable-state and ViewModel decorators, which is why the list hold lives in ViewModels (§5.6).
- `LocalKitPageVisible` exists **only for list-detail scenes** (wide windows), where the list pane really stays
  composed beside a detail pane that is a page of its own (for example a full-pane overlay). The shell sets it false for
  a pane that is fully covered; loops there stop. It is never used to hold data.
- `withFrameNanos` loops park when idle (no frame requested); the reveal clock parks between bursts (§5.1).
- Nothing animates in the background: no frame callbacks while the activity is stopped (Choreographer stops), and no
  timers stand in for animations (§02 S-17).

### 3.6 Tests for the policy (write first, `commonTest`)

| Test class | Catalog rows | Asserts |
|---|---|---|
| `MotionPolicyTest` | B-KIT-006, B-KIT-008, B-STATE-003 | every row of the resolver table **with its reason**: scale 0 wins (`System`); Saver → Off (`Saver`) without changing the stored choice; user Off/Calm (`User`); Full + severe heat → Calm (`Heat`); Full + power save → Calm (`BatterySaver`); heat and power save together report `Heat`; Full otherwise. `MotionNoteTest`: each reason maps to the §3.2 note key, `User` to none |
| `MotionSignalsSdkTest` | — | Robolectric at SDK 26, 29 and 36: `AndroidKitPlatform` builds `MotionSignals` without crashing; at 26 `severeHeat` is false and no thermal listener is registered; ARR diagnostics are skipped below 36 (§8.3) |
| `MotionPrefMigrationTest` | B-KIT-006, B-STATE-003, B-MIGR-005 | `full` + `oc.effectsCelebrations = false` reads Calm; a wrong-typed `oc.effectsCelebrations = "no"` reads Full without crashing; unknown reads Full; writing Full clears the old celebration key; `oc.effectsHaptics = false` and `oc.effectsGlass = false` are deleted and haptics stay on; a stored `oc.effectsGlowSpeed = fast` (or `42`) and `oc.effectsGlowColours = two` are deleted and change nothing (one colour, 3600 ms lap) |
| `MotionClassTableTest` | B-KIT-054 | the §3.3 table as data: for each class and level, the expected behaviour flag; every kit motion part is registered with exactly one class (the manifest lists them) |
| `KitMotionSpecsTest` | B-KIT-023 | under Off **every** builder (including `rowBounds`, `rowHandOff`, `rowContentIn/Out`, `rowHandBack`, `rowDim`, `pageFadeIn/Out`, `androidPage`, `fadeThroughIn/Out`, `predictive*`, `arrivalFadeOut`, `iconTurn`) returns `SnapSpec`; under Calm `travel()` is null and `transforms` is true; `debugTimeScale` scales durations, delays and stiffness |
| `MovingPartTest` | MOT-15 (Flutter `kit_composer_moving_part_test.dart`) | §5.3 resolver table |

---

## 4. Rules for all motion code

| # | Rule | Enforced by |
|---|---|---|
| R1 | `tween(`, `spring(`, `keyframes`, `infiniteRepeatable(`, `PathEasing(`, `CubicBezierEasing(`, `Easing {`, `snap(` appear only in `:ui:kit` `KitMotion.kt`, `KitMotionScheme.kt`, `KitEasing.kt` and `:ui:shell` `ShellMotion.kt`. `ShellMotion.kt` only **combines** `KitMotionSpecs` builders into `ContentTransform`s (`slideInHorizontally(specs.spatial())`, `fadeIn(specs.pageFadeIn())`, …); it holds no number. Every other kit, kit-chat and shell file (including `Modifier.growFromRow`, the shell's entry decorator and `KitSheetHost`) names a builder from `KitMotion.current` (§2.7). | Konsist `KitInternalRulesTest` (section 06 §14.5); `LiteralBanTest` bans `\d+\.milliseconds`, `tween\(\d`, `delay\(\d`, `delayMillis = \d` in all UI code and exempts **only** the four R1 files |
| R2 | Features import no animation API at all (ADR §6.3 rule 1). They choose a route class and kit parts. | Konsist feature allowlist |
| R3 | Animated values are read in the **draw or layout phase**: `Modifier.graphicsLayer { }` (lambda form), `Modifier.offset { }`, `drawBehind`, `drawWithCache`, `layout { }`. Reading an animated `State` in composition is a review rejection, except for one-shot `AnimatedVisibility`/`AnimatedContent` switches. | review + `RecompositionBudgetTest` (§9.3) |
| R4 | Infinite animations use `rememberInfiniteTransition` or `withInfiniteAnimationFrameNanos`, never `while (true) { delay() }`. They are created only when `KitMotion.state` allows that loop class (§3.3). | Konsist (bans `while (true)` + `delay(` in kit motion files); the Compose test harness pauses framework loops but a hand loop hangs it [R testing-perf §4.2] |
| R5 | No layout animation inside a scrolling list item, except the named exceptions X4 and X5 (MOT-5). `animateContentSize` is banned in `:ui:kit-chat` (it animates shrink too); the reply grows through the growth-only `LayoutModifierNode` of section 09 §8.4 (§5.1). Tool-row and thought bodies inside a list item fade in and close at once, with no height animation (§5.15). | Konsist |
| R6 | Lazy items set `placementSpec = null` unless the list kind in §5.6 says otherwise. | kit list helpers own `animateItem`; features never call it |
| R7 | Text that is translated or scaled by an animation sets `TextStyle(textMotion = TextMotion.Animated)` only for that animation (the lifting bubble); everything else keeps `TextMotion.Static` [V release-note guidance]. | review |
| R8 | A part's first composition shows its final state; no entrance plays for content that was there when the screen opened (Flutter "first build is final"). | each part's UI test (§9.3) |
| R9 | No animation is driven by a timer (`delay` then set state) where a transition state exists. The one gate that needs "after the transition" (§5.4) listens to the transition. | review; the demo's 700 ms latch is the counter-example |
| R10 | Haptics only through `KitHaptic` and only at the call sites of the §6.2 "When" column (MOT-11 plus every place that vibrates today). | Konsist bans `performHapticFeedback`, `LocalHapticFeedback` outside `AndroidKitPlatform` (why `View` and not `LocalHapticFeedback`: §6.2, §13.2) |
| R11 | `LocalOverscrollFactory` and `ScrollableDefaults.flingBehavior()` are never replaced app-wide. | Konsist |
| R12 | Every motion part reports itself to the test-only `LocalKitMotionProbe` (a no-op in release) when it starts and ends an animation, so tests can assert "nothing is running" (MOT-7). | `ReducedMotionSettlesTest` |
| R13 | **No unrequested movement** (the owner's jitter fixes J1–J10). Nothing that appears, changes or leaves on its own may move content the person is reading: (1) a notice that ends on a timer folds at the next page change, never under the reader (J5, `AppExitFoldOnPageChange`); (2) no line comes and goes above rows: inline status reserves its place or floats (J6); (3) the opening card is kept and only its words change (J1); (4) stand-ins and last-known rows hold their places, and live rows replace them in place with no entrance or placement (J2, J3, J9, J10); (5) a chip shows its last-known value from frame 1 (J8); (6) opening a chat never re-sorts it. Flutter commits: `c45c02031`, `d1a2daf9a`, `39be428aa`, `d95a6cd80`, `e268c012b`, `dd8c662b8`, `0380766e5`, `bbd1c1124`, `b4339921c`. | `NoUnrequestedMovementTest` (§9.3) and device checks JT-1..JT-5 (§9.7) |

---

## 5. Interaction specs

Each spec gives: what the person sees; the exact values; how it is built; each level; RTL and accessibility; the
frame budget; how it is verified; the catalog rows; the owning module. "Full/Calm/Off" refer to §3.3.

### 5.1 Streaming text reveal (and the reply grow, caret, follow-scroll, jump pill)

**What the person sees.** New words appear word by word at a calm reading pace, each fading from transparent to full
in 250 ms. The reply's box grows smoothly to each new line. A thin accent caret sits after the last word and breathes
slowly (it fades to a quarter and back, about two seconds a breath). Text that was already on screen when the chat
opened never fades or grows again. While the person is at the newest end, the list follows; when they scroll up, it
stops following and a "Jump to latest" pill appears.

**One spec, one write set.** Section 09 §8 is the single spec for the reveal code: `RevealRegistry`,
`RevealState.drive`, `Modifier.kitFreshFade` and the growth node, in `:ui:kit-chat` `…ui.kit.chat.transcript`, written
once by unit S2-KC (09 §15). This section does **not** specify a second `RevealState`, `freshFade` or `GrowOnly`. It
fixes the motion values and levels, and the rules below that 09 must carry. §13.2 lists the matching corrections to
09. The old unit M4 is folded into S2-KC (§11).

**Motion values (binding on 09 §8):**

| What | Value | Token |
|---|---|---|
| Reveal pace | `max(45 cps, backlog / 0.25 s)`; never more than 250 ms behind the network; `dt` capped at 50 ms | `REVEAL_BASE_CPS`, `REVEAL_MAX_LAG_MS` |
| Reveal step | snapped forward to the end of a whole word, so layout happens only when a word lands (about 8–20 times a second) | — |
| Word fade | each revealed run fades from hidden to full over 250 ms on `EaseOutCubic` | `STANDARD`, `KitEasing.Enter` |
| Reply growth | growth only, `spring(1f, 700f)` (≈ 210 ms a line), velocity carried when another line lands mid-way; a shrink (a rewrap) is taken at once; clip leaves 4 dp for descenders | `replyGrow()`, `DESCENDER_ALLOWANCE_DP` |
| Caret | 2 dp wide (drawn, takes no layout width), no taller than the line, `accent`; breathes alpha 1 → 0.25 over 1100 ms and back over 1100 ms on `StepEase` under Full; alpha 1 and still under Calm and Off | `CARET_BREATH`, `CARET_LOW_ALPHA` |
| Frame-rate vote | `highFrameRate(active = runs not empty or growing)` on the live block, Full only | M8-16 |

**Rules 09 §8 must carry** (merged from both sections; each is a test in the list under "Verified by"):

1. **Order.** A new tail block waits for the block before it in the same part (`predecessorDone`, 09), so words never
   appear out of order.
2. **Offsets.** `shown` and every fresh run index the **rendered** `AnnotatedString` prefix (after inline Markdown is
   applied), never the raw Markdown (09).
3. **Animator scale.** The clock divides `dt` by `KitPlatform.signals.animatorScale` (developer option 0.5× or 2×), so
   our `withFrameNanos` clock agrees with Compose's own `MotionDurationScale` (§3.2). Under Off it reveals everything at
   once and draws no fade.
4. **What is already there is final (R8).** A `RevealState` starts with `shown = text.length`, no fresh runs and no
   growth, **unless** its block began after the transcript was first shown (Flutter `_KitMdFresh.fadeIn`: then the
   block's first words fade) or its reply began after the transcript was shown (Flutter `_KitMdGrow.fromZero`: then
   its first lines grow from zero). So opening a chat in the middle of a reply shows the text at once; only words that
   arrive after opening are paced and faded. A finished or reloaded reply never fades.
5. **State outlives the item.** The registry is transcript-level (remembered per chat entry, outside the lazy items),
   keyed by part id + block start. Scrolling the live block out of the `LazyColumn` and back, or any recomposition of
   the item, never resets `shown` or replays the fade. A refetch that replaces the live part (reconnect, ADR: reconcile
   by refetch) keeps `shown`, clamped to the new length.
6. **The fade mask is line boxes, not glyphs.** On Android `TextLayoutResult.getPathForRange` is
   `Layout.getSelectionPath`: line-box rectangles, copied into a new `Path` on every call. So each run's rectangles are
   computed **once per `TextLayoutResult`** inside `drawWithCache` (keyed on the layout and the run list), and each
   rectangle is widened by a small overhang allowance (2 dp at each horizontal end, the line's full height), so italic
   and Arabic glyphs that overhang their advance box fade with their word. Drawn with `BlendMode.DstOut` at alpha
   `1 − EaseOutCubic(age / 250 ms)` on an offscreen layer that exists only while runs remain; never per-frame
   `AnnotatedString` colour spans (they re-lay out the paragraph every frame [V rendering-chat §6.1]).
7. **Growth node.** The primary implementation is the growth-only `LayoutModifierNode` (port of `_KitMdGrow`,
   `2d9a65b45`), not `animateContentSize`, which animates shrink too (R5). It is no longer a fallback.
8. **Levels** are the table under "Levels" below (it replaces 09 §8.6; Flutter agrees with it:
   `kit_markdown_fresh.dart:342` grows unless reduced, `kit_turn.dart:1193` sweeps at 4400 ms under Calm,
   `kit_message_lift.dart` uses `standard` + `enter`).
9. **Selection** on the live block stays off (`DisableSelection`) until the Compose 1.13 selection fix is stable
   (`JETPACK_AHEAD` row in section 06 §6.1).

**Caret** (`KitStreamCaret`, kit-chat): drawn in `drawBehind` at `textLayout.getCursorRect(shown)`; values in the table
above (Flutter `kit_stream_caret.dart`: `Tween(1 → .25)`, `KitMotion.stepEase`, `repeat(reverse: true)` at 1100 ms).
No caret after a code block, a table or pictures. Removed when the part ends (`session.text.ended` / `time.end`)
(B-CHAT-060).

**Follow-scroll.** `LazyColumn(reverseLayout = true)`; **index 0 is the composer clearance spacer** (§5.12) and the
newest block is index 1.

- "At the live edge" = the distance from the newest end is ≤ `FOLLOW_SLACK` (48 dp).
- At the live edge the first visible item is normally the spacer, so the list anchors on the spacer's key and a new
  block **inserted at index 1** appears just above it with no scroll call. Growth of the newest block keeps its bottom
  glued for free.
- Only when the person still counts as "at the live edge" but the anchor is already past the spacer (the spacer
  scrolled out within `FOLLOW_SLACK`) does the kit call `listState.requestScrollToItem(0)` in the same snapshot as the
  insert. No animation; the grow makes the hop smooth.
- After the person sends: if index 0 is within one viewport, `animateScrollToItem(0)` (a glide); otherwise
  `scrollToItem(0)` (a jump) (Flutter `_followAfterSend`). Under Off: always the jump.
- The person's own drag (an `interactionSource` drag) turns following off; reaching the live edge or tapping the pill
  turns it on.

**Jump pill** (`KitJumpPill`): visible when `derivedStateOf { distance from newest > AWAY_FROM_LATEST (480 dp) }`;
enters with fade `STANDARD` + rise `ENTRANCE_RISE` (8 dp, Full only) on `Enter`; leaves with fade `QUICK` + sink 8 dp
(Full only). Its "N new" count rolls (`KitSwap.count`). Tap: if the newest item is within two viewports,
`animateScrollToItem(0)`; else `scrollToItem(2)` then `animateScrollToItem(0)` (jump close, glide the last stretch). No
haptic (MOT-11: plain tap). The "Earlier messages" pill at the top end is its twin and moves the same way.

**Sent bubble lift** (X3, `KitMessageLift`): when the composer accepts a send it records the field's window rect
(`onGloballyPositioned`) in the kit-chat `LiftChannel` (`mark(rect)`). The new prompt item `claim()`s it within 1 s of
its first layout. The bubble is laid out in its final place from the first frame; only `graphicsLayer` moves:
`translationY = max(0, from.centerY − place.centerY) × (1 − p)`, `scale = 0.92 + 0.08 × p`, `alpha = min(1, 2p)`,
transform origin = bottom-**end** (bottom-right in LTR, bottom-left in RTL), `p` animated over `STANDARD` on `Enter`
(a tween, not the `lift` spring; §13.2). It rises from behind the composer, which stays drawn above it with its glow
unchanged. Calm: alpha only, in place. Off: no lift. The prompt item's `animateItem` fade-in is null while a lift claim
exists (no double fade). `TextMotion.Animated` on the bubble's text during the lift only.

**Levels** (this table replaces 09 §8.6):

| Motion | Full | Calm | Off |
|---|---|---|---|
| Word reveal pace | clock | clock | words appear with each publish (still ≤ 1 / 50 ms) |
| Fresh-word fade | 250 ms | 250 ms (a fade is not a travel) | none |
| Reply growth | `replyGrow` spring | `replyGrow` spring (a Fold) | jump |
| Follow after send | glide ≤ 1 viewport, else jump | glide ≤ 1 viewport, else jump | jump |
| Work-line fold, step arrival | fold + fade 250 ms | fold + fade 250 ms | instant |
| Sent-bubble lift | 250 ms rise from 0.92 (`STANDARD`, `Enter`) | fade in place | none |
| Stream caret | breathes (1100 ms each way) | still | still |
| Live-line light sweep | 2200 ms | 4400 ms | still |
| Composer glow vs voice Stop ring (MOT-15) | glow travels if on, else ring turns | neither | neither |
| Haptics | Send, Done, Commit, LongPress (never on scroll, stream or plain taps) | same | Done skipped |

**Accessibility.** No `liveRegion` on streaming text; the live line announces the reply's end once (section 09).
Reading text scales fully with the system; nothing here depends on the 1.3× frame cap.

**Budget (120 Hz).** Fade-only frames: ≤ 2 ms UI thread. Word frames (≤ 20/s): ≤ 4 ms (one block re-layout + list
placement). §02 S-9: `frameOverrunMs` p95 ≤ 0, p99 ≤ 4 ms on the NX721J.

**Verified by** (S2-KC's acceptance; 09 §14 lists the same tests). `RevealClockTest` (pure: pace 45 cps when caught up;
lag never above 250 ms for a 4,000-character burst; whole-word snapping; a tail block waits for its predecessor;
parks when idle; Off reveals all at once; `dt` divided by an animator scale of 0.5 and 2), `RevealInitTest` (opening a
chat mid-reply shows the text at once with no fresh runs; a block born after the transcript was shown fades its first
words; disposing and re-composing the item does not replay; a reloaded reply never fades; a refetch keeps `shown`),
`GrowOnlyTest` (growth animates, shrink is instant, velocity carried; `fromZero` only for a reply born after the
transcript was shown), `FadeMaskTest` (run rectangles computed once per layout; widened by the overhang allowance),
`FollowAnchorTest` (insert at index 1 with the spacer as anchor keeps the newest block in view without a scroll call;
the anchor-past-spacer case calls `requestScrollToItem(0)`), frame captures `stream_reveal_{0,80,160,250}ms`,
`stream_reveal_italic_120ms`, `stream_reveal_ar_120ms_rtl`, `caret_{0,1100,2200}ms` (§9.4), `StreamingBenchmark`
(section 13 PJ-05), `TailRelayoutCounterTest` (one block re-laid out per tail update on a 200-block reply, §02 S-9).
Rows: B-CHAT-060, B-CHAT-096 (merge), E2E-05.

**Owner.** `:ui:kit-chat` owner; the code is written once in unit S2-KC (09), Opus for the reveal, mask and growth node.

### 5.2 Send → Stop (the composer's controls)

**Current owner decision** (2026-10-30, overrides 11B): Stop is **not** in the field. The field's trailing button is
mic while the field is empty and Send once there are words (or the amber "Stop and send" pill, below). The running-turn
Stop is the `KitComposerStop` **chip** at the trailing end of the composer's status strip (the chip row above the field)
for the whole running turn, whether or not the field has text. There is no Stop on the live line. So "send-to-stop" is
a hand-off between two controls, specified here from batch1's `kit_composer_status_strip.dart` and `kit_composer.dart`.

**Sequence when the person sends** (times from the moment Send acts):

| t | What happens | Spec |
|---|---|---|
| 0 | Send acts on touch-down, or on **lift** when its long-press menu exists ("Send later or repeat", fix `ee0815e9f`). `KitHaptic.Send` fires. The pressed ripple shows for at least `PRESS_HOLD`. | §6 |
| 0–250 ms | The sent bubble lifts (§5.1). | X3 |
| on accept | The host clears the field (never the kit, DATA-1). The trailing control swaps **Send → mic** with `KitSwap` (cross-fade `QUICK`; no scale, MOT-2). | Fade |
| on accept | **The Stop chip arrives.** If the strip already shows chips or the model chip, the chip fades in at the trailing end with `KitSwap` (`QUICK`); it is a fixed-width sibling whose width is taken at once (no slot spring), the line of chips keeps its place and gives its room up first (scrolling sideways inside itself when it no longer fits); the Stop never shrinks or scrolls away. If the strip was empty, the whole strip unfolds with `KitReveal` (height + fade `STANDARD`) with the chip in it. Calm: the same (Fade and Fold). Off: instant. | Fade / Fold |
| on accept | The chip ignores taps for `STOP_IGNORE_AFTER_SEND` (800 ms), so a double tap on Send can never stop the turn and its sub-agents (fix `49d067f2d`, `chat_stop_guard_test.dart`). Its pressed state still shows. | guard |
| on accept | The glow fades in over `GLOW_FADE` (§5.3). | X6 |
| running | The moving part (§5.3): the glow travels, or nothing. The chip itself never moves. | MOT-15 |
| stuck | The chip's tone cross-fades from neutral to `attention` (amber) over `QUICK`. | Fade |
| stop sent | While the stop is on its way (`working`), the chip is disabled: taps do nothing, the hold menu is empty, TalkBack reads it as unavailable with its name. | — |
| turn ends | The chip fades out with `KitSwap` (`QUICK`); if the strip becomes empty it folds away (`KitReveal`, `QUICK`). The glow fades out over `GLOW_FADE`. `KitHaptic.Done` fires once on the busy → idle edge (skipped under Off; CHAT-GENUI-56). | Fade + Fold |

**Stop chip look** (`KitComposerStop`): a `KitChip` with the word "Stop" (`kitTurnLiveStopShort`) and the stop icon,
neutral tone, attention tone when `stuck`; never red (red is for destructive acts). The chip is the look; a 48 dp
`KitTappable` (button shape) around it is the target, the hold menu and the screen reader's name. No ring and no other
motion on the chip.

**Taps on Stop.**
- **Tap** = stop now: `KitHaptic.Commit` (a confirmed stop, MOT-11; Flutter `chat_session_actions.dart` plays it unless
  the session is isolated), pressed ripple.
- **Long-press or right-click** opens a one-item `KitMenu` with "Stop after this step", only where the agent can do it
  (otherwise no menu). The long-press plays `KitHaptic.LongPress` and the platform long-press ripple (§6.3); the menu
  drops as in §5.5. Choosing the item sends the note; the live line's words cross-fade to "Stopping after this step"
  (`KitSwap`, `QUICK`), followed by "· Undo" **only while the note still waits in the server's inbox**
  (`capabilities.inbox`; Flutter `chat_stop_after_step.dart`). Servers without an inbox (a direct Codex steer) show no
  Undo, because there is nothing to take back. Undo cancels the note before the agent reads it.
- **Tip.** The `stop-after-step` tip is anchored to the chip (`KitTip` around it) and teaches the hold after a reply has
  run 10 s (teaching registry; owner rule "teach what is hidden"). Shown once, dismissed by the host.

**While the turn runs and the field has words** (steer, never queue):
- An agent that takes messages mid-reply: the trailing button stays **Send** (accent), and the delivery caption
  cross-fades to what Send will do ("Adds to this turn") over `QUICK`.
- An agent that must stop its reply to take the message (`delivery == stopAndSend`, busy, not sending): the Send disc
  becomes the **"Stop and send" pill**, an amber (`attentionFill`) pill with a stop square and the words, so the stop is
  plain before the tap. This is a shape change: `KitSwap` cross-fades disc ⇄ pill over `QUICK`; the pill's wider
  width is taken at once (no width animation, as today). Calm the same; Off instant. Its haptic is `KitHaptic.Send`.

**While a request waits** ("Waiting for you"): the card owns the composer region (§5.8); the Stop chip and the glow
leave as in "turn ends", without the Done haptic; they return when the answer is sent.

**Field button changes** (all on the same 48 dp target, `KitSwap` cross-fade `QUICK`, Full/Calm; instant under Off):
mic ⇄ Send ⇄ "Stop and send"; mic → voice-mode states (`KitComposerVoice`; section 10 owns the voice pill); Send →
spinner while a send is waiting for the transport (a 2 dp spinner, progress loop).

**The voice-mode "Stop reading" circle** (section 10's voice pill, `_CircleKind.stop`) is the only control that keeps
the old neutral circle with a turning ring: a 40 dp `text1` circle with a `ground` square inside a 48 dp target, ringed
by an arc in `partner` over a `partner` track at 75 % alpha (Flutter `_StopRing`). The arc turns one lap per
`STOP_RING_LAP` (2000 ms) on `Steady` only when it is the moving part (§5.3: Full and the glow not travelling);
otherwise the ring is still and whole. Drawn in `drawBehind` with `rotate(phase × 360)`; no recomposition per frame.

**Accessibility.** The chip's label and tooltip name the target: "Stop {agent}" ("Stop Claude Code"; actions name their
target). "Stop after this step" is also a TalkBack custom action on the chip. The 800 ms guard does not hide the chip
from TalkBack. "Stop and send" reads as its words. The voice circle reads "Stop reading".

**Budget.** The chip arriving or the strip unfolding: ≤ 3 ms UI per frame (one row re-layout). The voice ring:
draw-only, ≤ 0.3 ms.

**Verified by.** `StopGuardTest` (pure: taps inside 800 ms are ignored; the pressed state still shows), `MovingPartTest`,
`ComposerKeysUiTest` (desktop compose test: mic → Send on typing; Send → mic and the Stop chip fades in after accept;
the strip unfolds when it was empty; the chip leaves at idle and an empty strip folds; `stuck` gives the attention tone;
`working` disables taps and empties the menu; hold opens a one-item menu "Stop after this step"; Undo shows only with
the inbox capability; a non-steering agent with words in the field shows the "Stop and send" pill; haptics `[Send]`,
then `[Done]` at idle; Stop tap `[Commit]`; hold `[LongPress]` once; recorded by `FakeKitPlatform`), frame captures
`stop_chip_arrive_{0,75,150}ms_{full,calm}`, `stop_strip_reveal_{0,125,250}ms`, `stop_chip_stuck_amber`,
`stop_chip_working`, `stop_and_send_pill_{ltr,rtl}`, `voice_stop_ring_{still,moving}`. Rows: B-CHAT-012, B-CHAT-086
(Continue after a cut-off), the widget tests `chat_send_stop_fixes_test.dart` and `chat_stop_guard_test.dart` (later
batch).

**Owner.** `:ui:kit-chat` owner (`KitComposer`, `KitComposerStatusStrip`, `KitComposerStop` chip, the voice circle's
ring with section 10's owner).

### 5.3 The running green glow around the composer

**What the person sees** (owner likes it, 2026-10-14; keep the hue). While a reply runs, a soft green glow surrounds
the composer pill: a bright highlight travels the border over a dim base ring, and a soft halo underneath breathes
twice per lap. It fades in when the reply starts and out when it ends.

**The glow is one choice: Off, Classic or Soft ring** (B-KIT-002, B-CHAT-006; `oc.effectsGlowStyle` absent = classic).
Its colours and speed are the app's own: since the owner-approved Appearance cuts of 2026-10-23 there is no Two colours
and no Slow/Fast, and the retired keys are deleted at migration (§3.1). **Classic:**

| Element | Value |
|---|---|
| Ring | 1.6 dp stroke on the composer's own rounded rect (radius 26 dp; 18 dp on wide windows), inset by half the stroke |
| Ring colours | sweep gradient, colours `[base, primary, partner, primary, base, base]` at stops `[0, .10, .18, .26, .38, 1]`, where `base = primary @ 0.28`, `primary = roles.accent`, `partner = lerp(accent, text1, 0.55)` in dark and `lerp(accent, text1, 0.30)` in light (the one-colour partner lerp; never amber) |
| Rotation | one lap per `GLOW_CLASSIC_LAP` (3600 ms) on `Steady` |
| Halo | drop shadow of the pill's shape in `primary`, blur 18 dp, spread 1 dp, alpha `0.25 − 0.10 × cos(4π × lapPhase)` (0.15 ↔ 0.35, two breaths per lap) |
| Strength | 0 → 1 over `GLOW_FADE` (450 ms) on `Enter` when a reply starts; 1 → 0 over 450 ms when it ends or a request starts waiting |
| Frost | the glow and halo sit outside the frost clip (D10) |

**Soft ring** (the other choice): a 2.2 dp line with a 12 dp wash (blur 7), peaks 0.75 / 0.22, one lap per 8000 ms.
Brutal draws a hard 2 dp ring with no halo; Paper a thin rule (style hooks, section 06).

**The one moving part (MOT-15).** One pure resolver, called by the composer frame and by the voice-mode "Stop reading"
circle (the only control with a ring; the running-turn Stop chip has none, §5.2):

```kotlin
// ui/kit-chat/.../composer/MovingPart.kt
internal enum class MovingPart { Glow, StopRing, None }
internal fun movingPart(level: MotionLevel, glowOn: Boolean): MovingPart = when {
    level != MotionLevel.Full -> MovingPart.None       // Calm: still glow; Off: nothing moves
    glowOn -> MovingPart.Glow                           // the glow travels; a voice Stop circle shows its still track
    else -> MovingPart.StopRing                         // glow switched off: the voice Stop circle's ring turns
}
```

**Levels.** Full: travels and breathes. Calm: a still `primary` ring and the halo held at 0.25 (it reads as lit, not as
a frozen frame). Off: no glow. Saver (which forces Off): one still 1 dp hairline in `primary` while a reply runs.

**How it is built** (draw phase only; the composer never recomposes for the glow):

```kotlin
// ui/kit-chat/.../composer/ComposerGlow.kt  (sketch)
/** The halo: its own node behind the pill. The dropShadow parameters never change, so its painter is built once. */
@Composable
internal fun ComposerHalo(strength: () -> Float, breath: (() -> Float)?, look: GlowLook, shape: Shape, modifier: Modifier) {
    Box(
        modifier
            .graphicsLayer { alpha = strength() * (breath?.invoke() ?: 0.25f) }   // a layer property: no shadow rebuild
            .dropShadow(shape) {                                                // constant: alpha 1, blur 18, spread 1
                radius = HALO_BLUR.toPx(); spread = HALO_SPREAD.toPx(); color = look.primary; alpha = 1f
            },
    )
}

@Composable
internal fun Modifier.composerRing(strength: () -> Float, phase: (() -> Float)?, look: GlowLook, shape: RoundedCornerShape): Modifier =
    drawWithCache {
        val ring = ringPath(size, shape, GLOW_STROKE.toPx())          // outer rrect minus inner rrect, cached per size
        val sweep = Brush.sweepGradient(look.colorStops, center)      // cached; rotation is a canvas transform
        onDrawWithContent {
            drawContent()
            val s = strength()
            if (s == 0f) return@onDrawWithContent
            clipPath(ring) {
                if (phase == null) drawRect(look.primary, alpha = s)          // still ring (Calm)
                else rotate(phase() * 360f) { drawCircle(sweep, radius = size.maxDimension, alpha = s) }
            }
        }
    }

// In KitComposerFrame (sketch):
//   val strength = remember { Animatable(0f) }
//   LaunchedEffect(active, motion.level) { strength.animateTo(if (active && !motion.reduced) 1f else 0f, specs.glowFade()) }
//   val phase = if (active && motion.ambientLoops && look.travels) rememberLapPhase(specs, look.lapMillis) else null
//   val breath = phase?.let { p -> { 0.25f - 0.10f * cos(4f * PI.toFloat() * p.value) } }   // 0.15 ↔ 0.35, two per lap
//   Box { ComposerHalo({ strength.value }, breath, look, shape, Modifier.matchParentSize()); Pill(Modifier.composerRing(...)) }
```

Rotating the canvas inside a clip of the ring path turns the gradient while the ring's rounded-rect shape stays put; no
brush or shader is allocated per frame. `rememberLapPhase` wraps `rememberInfiniteTransition().animateFloat(0f, 1f,
specs.lap(lapMillis))`; its `State` is read only in the draw and layer lambdas (R3). Under Saver (which forces Off) the
part draws the still 1 dp `primary` hairline instead, from the same ring modifier.

**Why the halo is its own node.** In ui 1.12.1 `BlockDropShadowNode` calls `invalidateShadow()` on every change of a
scope property (`setAlpha`, `setRadius`, `setColor`, …), which sets `shadowPainter = null` [V, javap of
`BlockDropShadowNode`]. Animating `alpha` inside the `dropShadow` block would therefore build a new `DropShadowPainter`
up to 120 times a second for the whole time a reply streams. Keeping the shadow's parameters constant and modulating it
with `graphicsLayer { alpha }` changes only a layer property. `Modifier.dropShadow(shape, block)` exists in Compose UI
1.12.1 [V]. Spike S8-9 measures this variant first; if its cost on the NX721J still fails the budget, the fallback is
the demo's three stacked strokes (16 / 9 / 2 dp at alpha 0.10 / 0.20 / 0.95).

**Budget.** Draw-only: ≤ 0.5 ms UI thread, ≤ 1.5 ms RenderThread per frame. No `highFrameRate` request: an ambient
loop may run at the display's Normal rate (≥ 60 Hz on adaptive-refresh devices) [R rendering-chat §7.1].

**Verified by.** `MovingPartTest`, `GlowColoursTest` (the one-colour partner lerp per brightness: 0.55 dark, 0.30 light;
never amber), `HaloPainterReuseTest` (Android host: 3 s of breathing builds the shadow painter once), frame
captures `glow_{classic,soft}_{dark,light}_phase057` with `stillFrame` (phase 0.57 centres the highlight on the top edge,
Flutter `_Classic.framePhase`), `glow_calm`, `glow_saver_hairline`, `RecompositionBudgetTest` (the composer recomposes
0 times during 3 s of glow), `StreamingBenchmark` (the glow runs throughout J2). Rows: B-CHAT-006, B-KIT-002.

**Owner.** `:ui:kit-chat` owner.

### 5.4 Row → chat container transform, without breaking predictive back

**What the person sees.** Tapping a conversation row grows the chat out of that row's box into the full screen; the list
under it dims a little and holds still. Tapping Back (top-bar Back, 3-button Back, Esc) shrinks the chat back into its
row. The back **swipe** instead shows Android's own predictive preview (the chat card shrinks toward 90 % and follows the
finger); letting go finishes the pop, dragging back cancels it. The two never mix. All of this plays under Full and Calm
(as today); under Off nothing moves.

**Route class:** `GrowFromRow` (section 07 §3.1) for `ChatRoute.Chat` opened by **a row's own tap** on Conversations,
Inbox or All conversations. Opened any other way (notification, link, search, widget, share, a row's menu): `Page`,
even if that conversation's row is on screen.

**The origin claim** (port of Flutter `KitPageOrigin.mark` / `claimWithin` / `forget`). `Modifier.growFromRow(key)` on a
row only registers the row's bounds for shared bounds; it does not make an open a `GrowFromRow`. The row's click handler
(in the kit feed row, never in a feature) calls `rowOrigin.claim(ChatKey)`: a **one-shot** mark held by the kit's
`RowTransformGate`. `AppNavigator.openChat(key, fromOrigin = true)` consumes it only if the key matches and the claim is
younger than `ORIGIN_CLAIM_WITHIN` (10 s: reopening an old agent's conversation can take seconds). A claim nothing
consumes is dropped by `forget()` (open failed or declined) or by expiry, and each drop sends `OpenAbandoned`.
Notification, link and search opens call `openChat(key, fromOrigin = false)` and never touch the claim.

**The open is asynchronous** (Flutter `chats_home_screen.dart` `_open`). Between the tap and the push:
- the tapped row's state word becomes "Opening" (`chatsHomeOpening`) with the running tone and its `STEP_SWEEP` light,
  and the page's loading bar runs, so the person sees the tap took;
- other row taps are ignored until this open ends (Flutter `_openingID`);
- an old agent conversation that cannot reopen first asks (the resume-notice confirm); declining changes nothing;
- `openChat` may take several seconds or return a problem, which shows as the page's notice in plain words.
The gate stays in `Pending` meanwhile (below); the list is held from the tap (§5.6).

**Forward (tap), 450 ms (X1), Full and Calm:**

| Track | Time | Spec (builders of `KitMotionSpecs`, §2.7) |
|---|---|---|
| Bounds: row rect → full window | 0–360 ms | `sharedBounds` with `BoundsTransform { _, _ -> specs.rowBounds(opening = true) }` |
| Box corners | 20 dp → 0 along the bounds' progress | `clipInOverlayDuringTransition = OverlayClip(AnimatedCornerShape(progress))` (a `Shape` whose `createOutline` reads the corner `State`; shapes do not morph by themselves [V docs]) |
| Box ground | the chat's `ground` from frame 1 (the box is the row's surface becoming the page) | chat root background |
| Row content hand-off | row words fade out 0–90 ms on `Enter` | the row's `sharedBounds(exit = fadeOut(specs.rowHandOff()))` |
| Chat content | fades in 90–270 ms on `Enter` | the chat's `sharedBounds(enter = fadeIn(specs.rowContentIn()))` |
| Chat layout | laid out once at full size, revealed by the growing box (no per-frame re-measure of the transcript, no scaling: MOT-2) | `Modifier.skipToLookaheadSize()` on the chat content |
| Dim over the list | 0 → `DIM_MOST` (0.32) of `roles.scrim`, 0–90 ms on `Enter` | the shell's list-entry decorator: `transition.animateFloat(transitionSpec = { specs.rowDim(opening = true) })`, read in `drawWithContent` |
| List | holds still; queued reorders wait (`f7e775dd3`) | the feed ViewModels' row-order hold (§5.6), not the list part |

Visible motion ends at 360 ms; `ROW_OPEN` (450) is the Flutter route length the timings were cut from.

**Button-back return, 300 ms (X2), Full and Calm:**

| Track | Time | Spec |
|---|---|---|
| Bounds: full window → row rect | 0–300 ms | `specs.rowBounds(opening = false)`. Do **not** reverse or flip the easing: Flutter's `rowReturnPath` (flipped emphasized on a reversed animation) is exactly the plain emphasized curve run forward in time |
| Box corners | 0 → 20 dp | as above |
| Chat content | fades out 0–120 ms | the chat's `sharedBounds(exit = fadeOut(specs.rowContentOut()))` (Flutter's reverse-slot `easeInCubic` over [0.6, 1] equals `EaseOutCubic` forward in time) |
| Row content | fades back in 240–300 ms | the row's `sharedBounds(enter = fadeIn(specs.rowHandBack()))` |
| Dim | 0.32 → 0 linearly over 300 ms (`Steady`) | decorator, `specs.rowDim(opening = false)` |
| Fallback | the row is gone (archived, deleted) or not on screen: no shared bounds; a `Page` pop instead | `SharedContentConfig.alternativeTargetBoundsInTransitionScopeAfterRemoval` returns null; the gate checks the origin is attached and inside the viewport before choosing |

**What `NavDisplay` itself plays for `GrowFromRow`** (`ShellMotion`, combining builders only):
- Forward: `EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished`, with
  `targetContentZIndex = 1f`, so the list entry stays composed and still under the growing chat until the shared
  bounds finish, and the chat draws above it.
- Pop (button): `EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished`, with
  `targetContentZIndex = 0f` for the list, so the leaving chat (which entered at 1f) stays above while it shrinks
  [U: P2 confirms the z-order Nav3 1.2 passes through; the fallback is an explicit `Modifier.zIndex` in the decorator].
- **Whenever `sharedBoundsEnabled` is false** (gesture or its settling, origin gone, `Pending` abandoned, Off excluded)
  the shell uses the **`Page`** spec for that forward or pop (§5.9), never `None`/`KeepUntilTransitionsFinished`
  without shared bounds, so the chat never appears or vanishes without motion. Under Off both are `None`.

**The predictive-back gate (option A; replaces the demo's 700 ms latch).** One pure state machine in `:ui:kit`
(`motion/RowTransformGate.kt`), fed by `:ui:shell`. It is **level-triggered**: it never waits for an edge that may not
come.

```kotlin
public enum class GateState { Idle, Pending, Opening, Closing, Gesture, GestureSettling }

public sealed interface GateEvent {
    public data object TapOpen : GateEvent                     // a row's own click claimed an origin
    public data object OpenAbandoned : GateEvent               // openChat returned a problem, the confirm was declined, or the claim expired (10 s)
    public data class TransitionStarted(val claimed: Boolean) : GateEvent  // runningCount went 0 → >0; claimed = its target is the claimed ChatKey
    public data object ButtonBack : GateEvent                  // Back committed without a gesture; also a pop from code after a guarded gesture
    public data object GestureStarted : GateEvent              // dispatcher.transitionState became InProgress
    public data object GestureCommitted : GateEvent            // NavDisplay.onBack called while in Gesture
    public data object GestureCancelled : GateEvent            // transitionState returned to Idle without onBack (a cancel, or a page guard took it)
    public data object TransitionSettled : GateEvent           // a level check: sent whenever runningCount becomes 0 AND one frame after every gate change
}

/** Pure. [running] = the shell's level-based count of entries whose transition is running right now. */
public fun reduce(s: GateState, e: GateEvent, running: Int): GateState = when (e) {
    GateEvent.TapOpen -> if (s == GateState.Idle) GateState.Pending else s
    GateEvent.OpenAbandoned -> if (s == GateState.Pending) GateState.Idle else s
    is GateEvent.TransitionStarted -> when {
        s != GateState.Pending -> s
        e.claimed -> GateState.Opening
        else -> GateState.Idle                                   // another page went first: the claim is dropped
    }
    GateEvent.ButtonBack -> if (s == GateState.Idle || s == GateState.Opening || s == GateState.GestureSettling) GateState.Closing else s
    GateEvent.GestureStarted -> GateState.Gesture                // also mid-flight during Opening (Nav3 1.2)
    GateEvent.GestureCommitted, GateEvent.GestureCancelled -> if (s == GateState.Gesture) GateState.GestureSettling else s
    GateEvent.TransitionSettled -> when (s) {
        GateState.Opening, GateState.Closing, GateState.GestureSettling -> if (running == 0) GateState.Idle else s
        else -> s                                                // Idle stays; Pending waits for its push or abandonment; Gesture waits for its end
    }
}

/** Shared bounds may run only outside a gesture and its settling, and never under Off. */
public fun sharedBoundsEnabled(s: GateState, level: MotionLevel): Boolean =
    level != MotionLevel.Off && s != GateState.Gesture && s != GateState.GestureSettling
```

How the shell feeds it:
- **The settle signal is the composed-entry count** (changed after slice-0 P2 D4, which built and tested it). After a
  committed pop the leaving entry is disposed when its exit ends, and after a cancel the revealed entry is disposed, so
  "**no more than one entry is composed**" is exact, and it also works when the transition is `None` (Off). It is
  still state, never a timer. (The earlier `transition.isRunning` running-set design is replaced.)
- **`TransitionSettled` is a level check, never only an edge.** The shell sends it when the count becomes 0, and also
  one frame after **every** gate change (`withFrameNanos`, then send), so a cancel at progress ≈ 0, a guarded gesture
  that Nav3 never seeks, or an Off pop with no animation reaches `Idle` on the next frame. `Pending` ignores it: it
  ends on `TransitionStarted`, `OpenAbandoned` or expiry.
- `GestureStarted`/`GestureCancelled` come from `dispatcher.transitionState` (a `StateFlow<NavigationEventTransitionState>`
  [V]); `GestureCommitted` from `NavDisplay(onBack = …)` while the gate is in `Gesture`.
- **A page guard took the gesture** (the chat's own Back guard while the draft is dirty, 07 §3.4): the gate goes
  `Gesture` → `GestureCancelled` → `GestureSettling` → `Idle` one frame later. When the guard then saves and pops from
  code, it pops with `nav.pop(PopCause.AfterGuard)`; the shell maps that to `ButtonBack` and plays the **`Page`** pop
  (the shrink is kept for a plain Back press).
- `TapOpen` comes only from the origin claim; `OpenAbandoned` from `forget()` and claim expiry.
- The kit's `SharedContentConfig` for chat keys returns `isEnabled = sharedBoundsEnabled(gate.state, level)` and
  `shouldKeepEnabledForOngoingAnimation = false` (on Compose 1.13.0-beta01 `isEnabled` is an extension **property**,
  `val SharedContentState.isEnabled`, and `alternativeTargetBoundsInTransitionScopeAfterRemoval` is an extension
  function `(targetBoundsBeforeRemoval: Rect, sharedTransitionLayoutSize: Size): Rect?` [V P2 §3]), so a back gesture that starts during the forward transform turns the
  shared element off at once (the chat snaps to full size and the preview takes over) instead of finishing a transform
  against the finger [V: the property exists; its default value is not relied on].
- The direction of the bounds spec (open vs return) is read from the gate state (`Opening` vs `Closing`) inside the
  `BoundsTransform` lambda.
- **This is why section 07 §3.2's `LaunchedEffect(isTransitionActive)` is replaced** (§13.2): `isTransitionActive` is
  true only for matched shared elements [V, Compose 1.11 note], and a gesture pop has none, so that effect would never
  clear the flag and the next tap would lose its transform.

**Predictive preview (gesture) for `GrowFromRow` and `Page`** (Material full-screen values, M8-9; shared axis is not
involved):

```kotlin
// ui/shell/src/commonMain/.../nav/ShellMotion.kt  (sketch: combines KitMotionSpecs builders, holds no number)
internal fun predictivePop(edge: Int, m: KitMotionState, specs: KitMotionSpecs, edgeShiftPx: Int): ContentTransform {
    if (m.reduced) return EnterTransition.None togetherWith ExitTransition.None        // back commits on release, no preview
    val sign = when (edge) {                                                           // away from the swipe edge, not by RTL
        NavigationEvent.EDGE_LEFT -> 1
        NavigationEvent.EDGE_RIGHT -> -1
        else -> 0                                                                      // EDGE_NONE: 3-button long-press Back, keys
    }
    return EnterTransition.None togetherWith (
        scaleOut(targetScale = PREDICTIVE_MIN_SCALE, animationSpec = specs.predictiveScale()) +
        slideOutHorizontally(specs.predictiveSlide()) { sign * edgeShiftPx } +
        fadeOut(specs.predictiveFade())
    )
}
```

- `edgeShiftPx = (windowWidth / 20 − 8 dp)` in pixels, computed in the shell [U: P2-9 decides whether the shift stays].
- **Corners follow the exiting entry's own transition**, never the gesture event. In navigationevent 1.1.2
  `dispatchOnCompleted`/`dispatchOnCancelled` set the dispatcher state to `Idle` at once, while Nav3 animates the rest
  of the commit or the cancel through `SeekableTransitionState.animateTo`; a radius read from
  `transitionState.latestEvent.progress` would snap from 28 dp to square on release while the card is still at about
  0.92 scale. So the shell's entry decorator derives the radius from the same transition that drives the scale:
  `val corner by transition.animateDp(transitionSpec = { specs.predictiveScale() }) { if (it == EnterExitState.PostExit) PREVIEW_CORNER_DP.dp else 0.dp }`,
  applied as `graphicsLayer { clip = true; shape = RoundedCornerShape(corner) }`. It follows seek, commit and cancel
  alike. Used only while the pop is predictive.
- The page underneath is not scaled or dimmed during the preview ("the list holds still"); Material's enter scale
  1.1 → 1 is dropped on purpose (M8-9).
- Commit: Nav3 finishes the seeked transition, `(1 − progress) × 300 ms`. Cancel: Nav3 returns it to the start [U: the
  cancel curve Nav3 1.2 uses; P2 records it; if it is linear, the shell wraps the cancel in the `Spatial` spring through
  `rememberNavigationEventState`].
- Under Calm the preview still draws (Follow class); under Off it is not drawn and Back commits on release.
- Android 16's long-press Back on 3-button navigation produces the same `InProgress` events with `EDGE_NONE`, so the
  card scales and fades in place with no shift [U, motion-native §12.9; checked on the AVD with 3-button navigation in
  P2-7].

**Option B (target)** is **not a per-file seam**. Nav3 1.3 moved `NavDisplay` itself onto `DeferredAnimatedContent`
[V], and declaring Nav3 1.3 anywhere in the Android graph lifts the whole Android classpath (Gradle picks the highest
version), so a `DeferredHandoff.kt` "behind the same kit API" would not be a real boundary. Option B is therefore:
1. an **Android-only version constraint** in `libs.versions.toml` (Jetpack `navigation3-ui` 1.3.x and Compose 1.13 for
   the Android target), plus
2. a `RowTransformGate` **mode flag** `deferredHandoff`: when true, shared bounds stay **on** during the gesture with
   `permitTransformDuringDeferredTransition = true`, so they ride the preview and hand over with velocity into the row
   on release; `sharedBoundsEnabled` then ignores `Gesture`.
ADR D12 bars an alpha in the release classpath, so the spike (S8-1) runs on its own worktree branch, and the constraint
and the flag flip only the week Google marks Nav3 1.3 and Compose 1.13 stable, if S8-1 passes. A `JETPACK_AHEAD.md`
row records it.

**Levels.** Full and Calm: as above (Calm casts no `highFrameRate` vote). Off: no transition; no preview.

**Accessibility.** TalkBack focus moves to the chat's title once the transition settles (`TransitionSettled` → `Idle`),
never at its start. The row keeps its content description; while opening it reads "Opening".

**RTL.** Bounds are physical rectangles (no mirroring needed). The predictive shift follows the swipe edge.

**Budget.** First frame of the open composes the chat (allowed: one frame up to 16.6 ms). All later frames: no
composition, layout of the shared bounds only, ≤ 3 ms UI. Preview frames: graphics layer only, ≤ 2 ms. §02 S-13:
`frameOverrunMs` p95 ≤ 0; p90 frame duration < 8.3 ms.

**Verified by.**
- `RowTransformGateTest` (pure, `commonTest`): every transition of the table, with `running` 0 and > 0; a tap after a
  committed gesture gets its transform back once `TransitionSettled` arrives (the bug in the 07 sketch); a gesture
  during `Opening` disables shared bounds; Off never enables them, Calm does; and the stuck-gate cases, each of which
  must return to `Idle` and release the feed hold: **declined confirm**, **failed open**, **claim expiry (10 s)**,
  **cancel at progress ≈ 0 with no animation**, **a gesture taken by the draft guard** (then the guard's pop is
  `ButtonBack` with the `Page` spec), **an entry disposed while its transition runs**, and **a notification open while
  the row is visible** (no claim, so `Page`); plus the return choice: a **detached** origin (row archived or deleted)
  and an **off-viewport** origin both choose the `Page` pop.
- `ShellMotionTest` (pure): `GrowFromRow` forward/pop use `None` + `KeepUntilTransitionsFinished` with the z-indexes
  above; with `sharedBoundsEnabled` false they return the `Page` spec; `predictivePop` shift sign is +1 for
  `EDGE_LEFT`, −1 for `EDGE_RIGHT` and 0 for `EDGE_NONE`, in LTR and RTL.
- `PredictiveBackPolicyTest` (`navigationevent-testing` **1.1.2**, the same line as the shipped navigationevent;
  `navigationEventInput`): progress events during an active row → page transform never start the transform's back
  animation; the root never consumes Back.
- Frame captures `grow_open_{0,90,180,270,360}ms`, `grow_return_{0,120,240,300}ms`,
  `predictive_{25,50,75}pct_{left,right}_{ltr,rtl}`, `predictive_50pct_none`, `predictive_commit_+50ms`,
  `predictive_cancel_+50ms` (no corner pop on release).
- Macrobenchmark `RowToChatBenchmark` (section 13 PJ-07) and `PredictiveBackBenchmark` (PJ-08, commit and cancel).
- Device checks P2 (§9.7): the motion-native §4.4 checks on the emulator, then the owner's phone.
- Rows: B-KIT-054 (the row transform and back clauses; its Android page clause waits on D8-5); NAV back rows in
  section 07.

**Owners.** Coordinator (`:ui:shell` motion decorator with the running set and the dim and corner tracks, gate wiring,
`ShellMotion`, `PopCause.AfterGuard` in `:ui:routes`, `FeedHold` in `:ui:routes`), kit owner (`RowTransformGate`, the
origin claim, `Modifier.growFromRow(key)`, `KitRouteTransition`), Opus for P2 and S8-1.

### 5.5 Sheets (drag, fling, predictive back) and dialogs

`KitSheetHost` renders the M3 1.5 in-window `BottomSheet(...)` [V: present in 1.5.0-alpha22 with
`sheetPredictiveBackScaling`] plus a kit scrim; the body is a `KitSheet` frame (section 06 §7.4).

| Moment | Spec | Level notes |
|---|---|---|
| Open | the sheet slides up from below the window on `KitMotionScheme.defaultSpatialSpec` (`Spatial`, ≈ 178 ms); the scrim fades 0 → `DIM_MOST` of `roles.scrim`, its alpha read from `sheetState.requireOffset()` in `drawBehind` (draw phase) | Calm: same (Fold/Settle); Off: snap |
| Drag | follows the finger 1:1 (anchored draggable); nested scroll: the body list scrolls first and the sheet drags only when the list is at its top | all levels |
| Release / fling | M3 decides the target from position and velocity (M3 defaults: positional threshold 56 dp, velocity threshold 125 dp/s [U: values in 1.5; keep the defaults whatever they are]); settles on `Spatial` | Off: snap |
| Predictive back | M3's own: the surface scales by up to 48 dp in x and 24 dp in y, keeping its aspect; commit hides the sheet; cancel springs back [V source]. This scaling follows gesture progress, not the `MotionScheme`, so `KitMotionScheme(off)` cannot stop it | **Off: commit only**, by mechanism: `KitSheetHost` passes `BottomSheet(backHandlerEnabled = false)` [V: parameter present in 1.5.0-alpha22] and installs its own `NavigationBackHandler` that hides the sheet (snap) on completion and ignores progress |
| Dirty sheet without a draft | `sheetState.confirmValueChange` refuses `Hidden`; the sheet springs back (`Spatial`) and the discard question replaces the body in place (no sheet on a sheet, `KitSheetStep`) | — |
| Height change inside | never an animated sheet height: the body part that changes uses `KitReveal` (Fold), and the sheet follows each frame | Off: instant |
| Second view (`KitSheetStep`) | `AnimatedContent` cross-fade `QUICK` out / `STANDARD` in with `SizeTransform(clip = true)` on `Spatial` (a sheet is not a scrolling list, so Fold is allowed) | Calm: same (Fade + Fold); Off: instant |
| Keyboard | the sheet's content uses `imePadding()`; its action block stays pinned above the keyboard and rides it frame by frame (one window, one inset model) | all |
| Close | slides down on `Spatial`; scrim fades with the offset | Off: snap |
| Haptics | none (MOT-11: sheets never vibrate) | — |

Wide windows (section 06 §10): `full` sheets become a 400–480 dp **end-side** sheet that slides in from the end edge on
`LargeTravel` (mirrored in RTL) and follows the predictive gesture toward the end edge (up to 10 % of its width), then
slides out on commit. Material 3 1.5 has **no side sheet** (`material3-android 1.5.0-alpha22` contains no `SideSheet`
class [V, jar listing]), so this is a kit part, **`KitSideSheet`**, not the in-window `BottomSheet`: an
`AnchoredDraggableState<SideSheetValue>` (anchors `Hidden` at the end edge and `Shown`) drives the offset and the
scrim; drag and fling follow the finger and settle on `largeTravel()`; a page-level `NavigationBackHandler` reads
`progress` to shift the sheet toward the end edge by up to 10 % of its width, hides it on completion and springs back
(`Spatial`) on cancel; under Off it snaps and commits only. Owner: kit owner (unit M3); tests `SideSheetAnchorsTest`
(anchors mirror in RTL; Off snaps), `SideSheetBackTest` (`navigationevent-testing`: progress shifts, completion hides,
cancel returns); captures `sidesheet_open_{0,125,250}ms_{ltr,rtl}`, `sidesheet_predictive_50pct`; spike S8-10. A
centred 560 dp panel (expanded and large) fades in over `STANDARD` with an 8 dp rise (Full),
fades out over `QUICK`; Back dismisses it on commit with no preview.

**Dialogs** (`KitDialog`, `KitAlert`): in-window overlay, 480 dp centred panel; fade in `STANDARD` (+ 8 dp rise under
Full), fade out `QUICK`; scrim as sheets. No scale (MOT-2). Back dismisses on commit, no preview.

**Menus** (`KitMenu`, a `Popup`): fade in `QUICK`, and under Full the panel drops `MENU_DROP` (8 dp) from its anchor
(rises when it opens above), position only; closes with a `QUICK` fade. Opened by long-press with `KitHaptic.LongPress`.

**Verified by.** `SheetBackOwnerTest` (sheet > selection > find > page > root; part of section 07's `BackOwnerPolicy`),
`DirtySheetTest` (refused dismiss + in-place question), frame captures `sheet_open_{0,90,180}ms`, `sheet_predictive_50pct`,
`sheet_step_{0,125,250}ms`, `dialog_open_{0,125,250}ms`, `menu_open_{0,75,150}ms_{full,calm}`; Macrobenchmark
`SheetBenchmark` (section 13 PJ-09). Rows: B-KIT-015, B-KIT-016, B-KIT-022,
B-KIT-032. Spike S8-7 (in-window sheet as a modal: scrim, TalkBack focus trap, IME). Fallback: `ModalBottomSheet`
behind the same kit API (ADR §9.4).

**Owner.** Kit owner (`KitSheetHost`, `KitSheet`, `KitDialog`, `KitMenu`).

### 5.6 Lists: insert, remove, reorder

Features never call `animateItem`; kit list parts apply it per **list kind**:

| List kind | Parts | New item | Removed item | Moved item | First load |
|---|---|---|---|---|---|
| **Feed** (Conversations, Inbox, All conversations, servers, notification and schedule lists) | `KitFeedList` (lazy) | fade `STANDARD` on `Enter`; under Full a late arrival also rises 8 dp (`graphicsLayer.translationY`, `Enter`). **Never** for a row that replaces a stand-in or last-known row with the same key, and never for the first live snapshot after an opening (R13) | fade `QUICK`; the gap closes with placement | placement `Spatial` under Full; `null` (jump) under Calm and Off | no animation. Last-known rows (`KitLastKnownRow`) or stand-ins show from frame 1 and the live rows **replace them in place** (same keys, no entrance, no placement); skeleton rows are a different part (`KitSkeletonRows`, no motion) |
| **Transcript** (chat) | kit-chat transcript helpers | fade `STANDARD` (notices, tool rows, cards); prompt items skip it while a lift claim exists (§5.1); the opening excerpt's rows are replaced in place by the live history (no entrance) | fade `QUICK` | never (`placementSpec = null`, rendering-chat §4.6): when the live block grows, every row above moves each frame and a placement spring would make them lag | none |
| **Short list** (≤ 50 rows in a `Column`: checklist, settings sub-rows, terminal list, server switcher) | `KitAnimatedRows` | `expandVertically` + fade `STANDARD` | shrink + fade `QUICK` | cross-fade in place | none |
| **Search results / filter change** | any | the list is wrapped in `key(query, filter) { … }`, so a new result set appears without per-item animation (Flutter: "restart with a new key") | — | — | — |
| **History pages** (older messages, load more) | transcript, feed | none: they arrive outside the viewport and the key anchor keeps the view still [V] | — | — | — |

**Late arrival.** `KitFeedList` remembers the set of keys present at its first **live** layout (stand-in and last-known
keys count as present); a key not in that set gets the rise once, then joins the set. The set lives in the feed
ViewModel's published state (with the hold below), not in the list part, so a list that left composition and came
back does not replay rises.

**The row-order hold** (Flutter `f7e775dd3` "the Conversations list holds still until a chat has landed in its row",
`89828a3c8`). Under Nav3, a covered single-pane entry **leaves composition** once the forward transition settles: only
the top scene is composed, and an entry's state survives only through the saveable-state and ViewModel decorators
(§3.5). A snapshot held inside `KitFeedList` would be destroyed, and on Back the list would recompose from the live
feed, where the conversation the person just sent in has sorted to the top; the chat would shrink into a slot another
conversation now holds. So the hold lives in the **feed ViewModels**:

1. `FeedHold` (`:ui:routes`, plain Kotlin, coordinator-owned; injected like `AppNavigator`) exposes
   `heldFor: StateFlow<ChatKey?>`. The shell sets it on a row's origin claim (`TapOpen`, §5.4), keeps it while that
   chat entry is on the back stack, and clears it after the return's `TransitionSettled` (gate `Idle` with the chat
   gone), or at once on `OpenAbandoned`.
2. The Conversations, Inbox and All conversations ViewModels combine their live feed with `heldFor`: while it is
   non-null they keep publishing the **row order** (and the row set) they last published; row contents may still
   update in place (a state word, a time), because they do not move rows. Queued inserts, removals and reorders wait.
   While held, the ViewModel also stops rebuilding the feed on every change (Flutter `89828a3c8`: the covered list
   does not rebuild per change).
3. On release the ViewModel publishes the live order once, and `KitFeedList` animates that diff once with the Feed
   rules (placement under Full).
4. `KitFeedList` itself holds nothing; its `frozen` parameter is removed. `LocalKitPageVisible` is limited to
   list-detail scenes and is used only to pause loops (§3.5).

**Swipe actions** (`KitSwipeAction`, only for acts with Undo, always with a menu twin, A11Y-5): the row follows the
finger (Follow); past the threshold and released, the row continues out of the screen on `SpatialFast` and the list
closes the gap with placement (Feed rules); if `onAct` returns false, the row springs back (`Spatial`) and a notice
says why. The Undo bar arrives (§5.15). This is Android's own pattern; Flutter snapped the row back first (§13.1).
No threshold haptic (D8-3).

**User reordering by drag:** no Flutter feature has it; none at the switch. A future one uses a kit
`KitReorderableList` with `KitHaptic.LongPress` on lift and `KitHaptic.Select` per slot crossed.

**Arrival wash** (`KitArrival`, a search result or a notification landing on a row): the list scrolls the row into view
at 30 % from the top with `specs.arrivalScroll()` (`STANDARD` on `Emphasized`; Off: jump); the row is washed with
`accent` at alpha 0.18 at once, held `ARRIVAL_HOLD` (2400 ms), then fades out with `specs.arrivalFadeOut()`
(`ARRIVAL_FADE_MS` 900 ms on `Enter`, exception X10; Off: removed at once); TalkBack focus moves to the row.
Rows: B-NAV-073.

**Budget.** Placement frames: ≤ 3 ms UI. Insert frames: one item composed (prefetched where possible).

**Verified by.** `FeedListMotionTest` (desktop compose test: first load plays nothing; live rows replacing last-known
rows with the same keys play no rise and no placement; a late arrival rises once; a move animates under Full and jumps
under Calm; a released diff animates once), `FeedHoldViewModelTest` (replaces the old pure `HeldSnapshotTest`; uses a
fake feed and a fake `FeedHold`: open a row → a send reorders the live feed → Back → the opened conversation's row
index is unchanged until the gate settles, then the order updates once; an abandoned open releases at once; contents
update in place while held), frame captures `feed_insert_{0,125,250}ms`, `feed_move_{0,90,180}ms`,
`feed_standin_replace_{0,125}ms` (must be identical frames), `swipe_{threshold,out,gap}`, `arrival_wash_{0,2400,2850,3300}ms`.
Device check P2-8 (§9.7). Rows: B-KIT-027 (swipe), B-KIT-051 (tap shows on the next frame), B-NAV-073.

**Owner.** Kit owner (`KitFeedList`, `KitAnimatedRows`, `KitSwipeAction`, `KitArrival`); coordinator (`FeedHold` and
the shell wiring); the Conversations, Inbox and All conversations feature agents (their ViewModels apply the hold, from
a shared `holdOrder(live, heldFor)` helper the coordinator ships in `:ui:routes`); kit-chat owner (transcript).

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
snapshot commit deleted it). The node's look is a state table; the light's path is the same for every lap: it travels
the rail from above the node (`[0, 0.28]` of the lap on `StepEase`), splits and runs round both sides of the node
(`[0.26, 0.66]`), meets at the bottom centre and leaves below (`[0.64, 0.95]` on `StepOut`). The halves mirror, so RTL
needs nothing extra (B-KIT-036).

| Node state | Full | Calm | Off (and system 0) |
|---|---|---|---|
| **running** | accent light laps every `STEP_LAP` (2000 ms) at intensity 1; the step's name sweeps (`STEP_SWEEP` 2200 ms) | lap 4000 ms; name sweep 2200 ms | still accent outline at intensity 1; still name |
| **background** | lap always 4000 ms (`STEP_LAP_CALM`) at intensity 0.6 | same | still outline at 0.6 |
| **stalled** (running or background, connection down) | the lap is **held where it is** (not restarted), intensity × 0.5 | same | still outline at × 0.5 |
| **waiting** (for the person) | the attention outline breathes, strength 0.5 ↔ 1, `STEP_BREATHE` 1600 ms each way (repeat with reverse, `Steady`) | same | still attention outline at 1 |
| **queued** | still, dim `text3` outline at 0.45 | same | same |
| **done** (from running or background) | a success-colour tick shows for `STEP_TICK` (600 ms), then the tool glyph grows back from 0.4 to 1 **by its size** (not a transform) over `STEP_SPRING` (260 ms) on `Land`; no flash (X11) | same | the resting glyph at once |
| **failed** (from running, background or waiting) | a `text2` outline fades out over `STEP_FLASH` (300 ms), then the failed mark shows (X11) | same | the failed mark at once |
| **stopped** (from running, background or waiting) | the accent outline fades out over `STEP_FADE` (200 ms), then the resting glyph (X11) | same | the resting glyph at once |
| none / settled | the resting glyph tile | same | same |

An ending plays only when the node changes from a live or waiting state while it is on screen; a node first composed
in an ended state shows its resting look (R8). Drawn in one `drawWithCache` per node, phase read in the draw phase
(Flutter `4a907c935` moved it off rebuilds for the same reason).

**Signal style** draws lamps instead of the light (`kit_step_signal.dart`, owner 2026-10-30): **running** a green
go-lamp that breathes (`STEP_BREATHE`, strength 0.75 ↔ 1, ambient: Full only); **background** a green lamp, still;
**waiting** a red stop-lamp, still; **queued** an unlit lamp; **done** or **stopped** a hollow ring; **failed** the
failed mark. No endings play under Signal. The folded work line's lead is the breathing go-lamp while a step runs.

**C. A tool row's status** (`KitToolRow`): "Running" → "Done · 1.2 s" cross-fades (`KitSwap`, `QUICK`). The duration
ticks in tenths under a minute while the row is visible and running (a text update at 10 Hz is not motion); it stops
ticking when the row scrolls off or the page is covered.

**Step arrival** (X5): a new step in an **open** timeline unfolds from 0 height with a fade over `STANDARD`, and under
Full it also rises 6 dp (`graphicsLayer.translationY`, `Enter`); Calm: unfold + fade, no rise; Off: instant. In a folded
work line only the count rolls.

**Spinner truth** (behaviour, not motion, but it decides when the spinner stops): a tool spinner stops when later words
in its turn exist or the turn ended (B-CHAT-117).

**Budget.** Spinner and light: draw-only, ≤ 0.5 ms. A fold: ≤ 4 ms (one item's re-layout per frame).

**Verified by.** `StepLightPhaseTest` (pure: segment boundaries, mirrored halves meet at the bottom centre; for every
row of the state table and each level, the expected lap length, intensity and breathe; stalled holds the phase; each
ending plays only from a live or waiting state and never on first composition), `SignalLampTest` (state → lamp; the
go-lamp breathes only under Full), `CountRollTest` (direction by sign; Calm cross-fade; Off instant; first build final),
frame captures `steplight_{0,25,50,75}pct`, `step_{running,background,stalled,waiting,queued}_still`,
`step_done_{0,300,600,730,860}ms`, `step_failed_{0,150,300}ms`, `step_stopped_{0,100,200}ms`,
`step_signal_{running,background,waiting,queued,done,failed}`, `step_arrival_{0,125,250}ms_{full,calm}`,
`workline_fold_{0,125,250}ms`, `count_roll_{0,75,150}ms`.
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

Signal style shows the card as a printed slip (`kitSignalSlip`, section 06 style hooks). The slip is **static** today
(no animation in `kit_signal_slip.dart`), so it arrives exactly like any card (`KitReveal`); no slip travel is added.
A printing motion would be new motion and needs an owner decision first.

**Verified by.** `HeldAnswerTest` (pure: 3 s hold, Undo inside, leaving sends at once, a second answer ignored —
B-CHAT-050), `RequestCardMotionUiTest` (arrival unfolds; first build final when the chat opens with a waiting request;
Off instant), frame captures `request_arrive_{0,125,250}ms_{dark,light}`, `request_answered_collapse_{0,150,250}ms`.
Rows: B-CHAT-123, B-CHAT-050.

**Owner.** `:ui:kit-chat` owner (`KitRequestCard`, composer region layout).

### 5.9 Page and tab transitions

Route classes from section 07 §3.1; exact motion here. `dir = +1` in LTR and `−1` in RTL (forward comes from the end
edge). `ShellMotion` builds all of them by combining `KitMotionSpecs` builders (it holds no number, R1); Nav3 receives
them through the `NavDisplay` `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec` parameters and the
per-entry metadata helpers (`NavDisplay.transitionSpec { }`, `popTransitionSpec { }`,
`predictivePopTransitionSpec { edge -> }`) [V androidx-main].

**Calm plays route transitions as Full** (Flutter parity: `KitAndroidPageTransitionsBuilder` and `_GrowFromRow` check
only `reduced`; §3.3 Transform class). Calm only drops the `highFrameRate` vote. Off: none.

| Class | Forward | Pop (button) | Predictive preview | Calm | Off |
|---|---|---|---|---|---|
| `Page` on **Android** (most pages; Settings) — **D8-5 default: Android's own fade-forwards** (today's feel, X14) | new page `slideInHorizontally(specs.androidPage()) { dir × ANDROID_PAGE_SHIFT × width }` + `fadeIn(specs.androidPageFadeIn())`; old page `slideOutHorizontally(specs.androidPage()) { −dir × ANDROID_PAGE_SHIFT × width }` + `fadeOut(specs.androidPageFadeOut())`, over `ground`; 450 ms on `Emphasized`, fades linear over the first 75 % (in) and 25 % (out), ported from Flutter's `FadeForwardsPageTransitionsBuilder` source | mirror of forward | §5.4 Material preview | as Full | none |
| `Page`, **D8-5 alternative** (and iOS/desktop later) | shared axis X: new page `slideInHorizontally(specs.spatial()) { dir × PAGE_TRAVEL }` + `fadeIn(specs.pageFadeIn())` (75–250 ms); old page `slideOutHorizontally(specs.spatial()) { −dir × PAGE_TRAVEL }` + `fadeOut(specs.pageFadeOut())` (0–75 ms) | mirror of forward | §5.4 Material preview | as Full | none |
| `GrowFromRow` | §5.4 | §5.4 | §5.4 | as Full | none |
| `FullScreenDialog` (New conversation, pairing scanner; Close at end) | slides up from the bottom edge: `slideInVertically(specs.largeTravel()) { fullHeight }` + `fadeIn(specs.quick())`; the page under it does not move | `slideOutVertically(specs.fullScreenDialogOut()) { fullHeight }` (`QUICK` on `EaseInCubic`) | §5.9.1: the page moves down by up to 10 % and scales to 0.95 over the **whole** gesture, fading from 35 %; a commit finishes as a fade | as Full | none |
| `TopLevel` (rail/sidebar switch, Ctrl+1..3) | fade-through: leaving `fadeOut(specs.fadeThroughOut())` (0–83 ms), chosen `fadeIn(specs.fadeThroughIn())` (83–250 ms), on `Enter`; no slide | same | Material preview | as Full | none |
| `Replace` (new conversation → its chat; import → chat) | cross-fade `specs.enter()` | — | — | as Full | none |
| `Root` (`replaceRoot`, `land`) | cross-fade `specs.enter()` | — | — (the root never intercepts Back; the system plays back-to-home) | as Full | none |

List-detail scenes (wide windows): changing the detail pane cross-fades the pane over `QUICK`; the list pane does not
move (section 07 §3.1). The rail's and sidebar's selection lens moves on section 06's lens springs (the one rail
indicator spec, §2.3; `SpatialFast` if 06 Q5 flattens them) under Full; it jumps under Calm and Off.

**B-KIT-054's Android clause** ("page transitions use the Android system transition on Android") is covered only by the
D8-5 default. If the owner picks shared axis X, the row is marked "changed by D8-5 / §13.1" in the catalog and the
native test is named after the amended row.

#### 5.9.1 The `FullScreenDialog` predictive preview

Nav3 1.2.0 seeks the predictive transform with the **raw** `NavigationEvent.progress`
(`SeekableTransitionState.seekTo(progress, previousScene)` in `NavDisplay`), and a slow drag can pass 0.7 before the
finger lifts. So no part of the seek range may carry the page off the screen. The whole range is a preview, the same
pattern as the `Page` preview: the page moves down by at most `FSD_PREVIEW_SLIDE` (10 % of its height) and scales to
`FSD_PREVIEW_SCALE` (0.95) along `PredictiveBack`, and fades out from `PREDICTIVE_FADE_AT` (35 %). A commit finishes the
remaining fraction, so it ends as a fade, never a fly-off.

```kotlin
// ShellMotion (sketch): combines builders only
internal fun fullScreenDialogPredictive(specs: KitMotionSpecs, m: KitMotionState): ContentTransform =
    if (m.reduced) EnterTransition.None togetherWith ExitTransition.None
    else EnterTransition.None togetherWith (
        slideOutVertically(specs.predictiveSlide()) { (it * FSD_PREVIEW_SLIDE).roundToInt() } +
        scaleOut(targetScale = FSD_PREVIEW_SCALE, animationSpec = specs.predictiveScale()) +
        fadeOut(specs.predictiveFade())
    )
```

If P2 shows that the person expects the page to slide away on commit, the alternative is to own the gesture for this
route class: a page-level `NavigationBackHandler` drives the 10 % / 0.95 preview from `progress`, and `onBackCompleted`
pops with the button pop spec (slide down). It is not built unless P2 asks for it.

#### 5.9.2 Tabs inside a page (`KitTabSwitcher`, `KitTabStrip`)

- Body switch: fade-through `STANDARD` (`fadeThroughOut`/`fadeThroughIn`; destinations stay alive; no slide, no swipe
  paging). Calm: simultaneous cross-fade `QUICK`. Off: instant (B-KIT-050).
- Tab indicator: moves to the chosen tab on `SpatialFast` (Full); jumps under Calm/Off.
- Tab counts roll (`KitSwap.count`).
- No horizontal swipe between tab bodies: it would compete with code, diff and table scrollers and with the back gesture
  at the edges; the tabs are always visible rows (obvious options first).
- Haptic: none on a tab tap (a plain tap, MOT-11).

**Verified by.** `ShellMotionTest` (pure: each class × level returns the expected transform kinds, Calm equal to Full
for route classes; `dir` flips in RTL; predictive shift sign follows the edge, not the direction, and is 0 for
`EDGE_NONE`; the `FullScreenDialog` predictive slide never exceeds 10 % of the height at fraction 1), `TabSwitcherMotionTest`,
frame captures `page_fwd_{0,112,225,450}ms_{ltr,rtl}` (D8-5 default; compared with the Flutter build's frames at the
same times) or `page_fwd_{0,75,150,250}ms_{ltr,rtl}` (alternative), `fsdialog_up_{0,120,250}ms`,
`fsdialog_predictive_{30,60,90}pct`, `toplevel_fade_{0,83,166,250}ms`. Macrobenchmark `PageBenchmark` (part of section 13
PJ-07: open Settings, back). Rows: B-KIT-054 (Android clause: D8-5), B-KIT-050.

**Owner.** Coordinator (`ShellMotion`), kit owner (`KitTabSwitcher`, `KitTabStrip`, the builders in `KitMotion.kt`).

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
`RefreshBenchmark` (section 13 PJ-11). Rows: B-KIT-054 (MOT-4).

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
    KitTranscriptList(                                              // LazyColumn(reverseLayout = true), edge to edge
        clearance = { composerHeightPx },                           // item 0: a spacer whose height is read in layout { }
        modifier = Modifier.fillMaxSize(),
    )
    KitComposer(
        modifier = Modifier.align(Alignment.BottomCenter)
            .onSizeChanged { composerHeightPx = it.height }        // measured OUTSIDE the inset: includes the nav-bar padding
            .navigationBarsPadding(),                               // becomes 0 while the keyboard covers the nav bar (insets consumed)
    )
}
```

The order matters. The transcript fills the box down to the screen's bottom edge whenever the keyboard is down
(edge-to-edge), so the clearance must include the navigation-bar inset (about 24 dp with gesture navigation, 48 dp with
3-button). Measuring inside `navigationBarsPadding()` would leave the newest words under the composer by exactly that
inset, which is the bug Flutter fixed in `8c614c58d`.

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
- **Rotation.** The main mechanism is the manifest: `MainActivity` keeps the full `configChanges` list (sections 07
  §7 and 11), so a rotation does **not** recreate the activity; Compose re-lays out, the field keeps focus and the
  keyboard stays. Android 17's "IME not restored after an unhandled rotation" [V motion-native §3.1] therefore matters
  only on the recreation paths that remain (process death, a configuration change outside the list): for those the chat
  screen saves `hadImeFocus` (`rememberSaveable`) and, after recreation, requests focus and shows the keyboard again if
  the composer had focus and a draft. Decided here; no owner question.
- **`imeNestedScroll`** (drag the conversation to pull the keyboard open or push it closed, Google Messages style) is
  off at the switch. It is `@ExperimentalLayoutApi` [V], a hidden gesture that needs a tip, and it changes what a drag
  near the newest end does. Spike S8-3 on the NX721J, then owner decision D8-2.
- Frost (D10): the composer pane turns to frost only while content is scrolled under it; that switch is a
  `FROST_FADE` (180 ms) cross-fade of the pane's fill; the keyboard does not change it.

**Levels.** The keyboard animation is the system's (Follow class) at every level. The composer height change is a
Fold, so it animates under Full and Calm and is instant under Off.

**Budget.** IME frames: no composition; layout of the root box + re-placement of visible transcript items, ≤ 4 ms UI.

**Verified by.** `ImeClearanceTest` (compose test with fake insets; asserts **positions**, not sizes: in root
coordinates the newest transcript item's bottom is at or above the composer's top; four cases: keyboard down and up,
each with a gesture-navigation inset (24 dp) and a 3-button inset (48 dp); and no recomposition of the list while the
inset animates, counted by `RecompositionBudgetTest`), Macrobenchmark `ImeBenchmark` (section 13 PJ-10), device check
IME-1 to IME-4 (§9.7).

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
| `KitTurnLive` (live line) | working | light sweeps over the words: gradient `text3 → text1 → text3`, centre from −0.3 to 1.3 of the width (reversed in RTL), band ±0.18, `Steady`, one pass per `LIVE_SWEEP_LAP` (2200 ms). Under the **Signal** style the sweep is replaced by the breathing go-lamp (`kitSignalHead(breathing: true)`, `STEP_BREATHE`, strength 0.75 ↔ 1, Full only) next to the `kitSegmentTimer` | 4400 ms (Signal: still lamp) | still words / still lamp | — |
| `KitTerminalView` | content arrives | no kit motion: the terminal emulator draws its own cursor. (B-KIT-055's blink tests `StatesTerminalScene`, an illustration scene deferred with D11, not the real terminal view.) | same | same | deferred: B-KIT-055, B-KIT-028 (D11) |
| `KitTerminalKeys` (extra key caps) | press | a **flat pressed fill**, no ripple (key caps behave like a keyboard) | same | same | — |
| `KitIconButton` with `KitIconMotion.turn` (the Settings gear) | press opens a page | the glyph turns a quarter (90°) over `ICON_TURN_MS` (200 ms) ease-out (`specs.iconTurn()`), clockwise in LTR and counter-clockwise in RTL; it turns back when its page is left and its screen is on top again (at once if nothing covered the screen); no idle movement | same (a rotation in place is not a travel; Flutter checks only `reduced`) | no turn | B-KIT-019 |
| `KitComposer` "+" (tools) | tray opens / closes | "+" turns an eighth (45°) into "×" over `QUICK` on `Enter`; the tray unfolds and folds with `KitReveal` | same | instant | — |
| `KitComposer` Send glyph | direction | the Send arrow **mirrors in RTL** (a glyph, not a motion; captured in RTL) | — | — | — |
| `KitStateView` | a new state shows | fade `STANDARD` + 8 dp rise when it first shows and whenever it becomes a **different** state (icon, tone or drawing); new words or progress inside the same state do not replay it; details unfold with `KitReveal`; progress → ok while watched plays `KitHaptic.Done` (§6.2) | fade only | instant | B-KIT-054 |
| `KitNeedsYou` (badge) | appears / count changes | fade `STANDARD` on `Enter`, leaves with fade `QUICK`; the count rolls (`KitSwap.count`) | fade; count cross-fades | instant | — |
| `KitGuide` | next question | the question fades in over `STANDARD` with an 8 dp rise; chosen chips show their mark from `MARK_FROM` over `QUICK` (X12) | fade; mark fades | instant | — |
| `KitOptionCard` | selected | the selection mark fades in from `MARK_FROM` over `QUICK` (X12); fill colour tween `QUICK` | mark fades | instant | — |
| `KitProgressRing` | value | the arc eases to the new value over `STANDARD` on `Enter`; never backwards within a job | same | instant | — |
| `KitImage`, `KitAvatar` | first frame decoded | fade in over `QUICK` on `Enter`; a cached image shows at once (first build final) | same | instant | — |
| `KitTopBar` | title changes (scrolled title, page title) | the old title fades out and the new one in over `QUICK` (a cross-over, no slide) | same | instant | — |
| `KitSessionTabs` | selection | the selected tab's fill moves/fades over `QUICK` on `Enter` | same | instant | — |
| `KitQueuedMessage` (steer bubble) | queued ⇄ sent | the bubble's look swaps with `KitSwap` over `QUICK` | same | instant | — |
| `KitBackgroundWorkLine` | appears / leaves | fade over `STEP_FADE` (200 ms) on `Enter`; it reserves its place (R13) | same | instant | — |
| `KitToolRow`, thought block | body opens / closes | the opened body fades in over `QUICK` at once at full height; closing is **instant**; no height animation inside a list item (R5) | same | instant | — |
| `KitFindMark` | the current match changes | the highlight band cross-fades from the old match to the new one over `QUICK`; marks never travel | same | instant | — |
| `KitFeedItem` "Working" word | a conversation runs | the word sweeps once per `STEP_SWEEP` (2200 ms): gradient `text3 → text1 → text3`, centre −0.3 → 1.3 of the width (reversed in RTL), band ±0.18 (`_StateSweep`); also "Opening" while a row opens (§5.4) | same speed | still word | — |
| `KitSegmentTimer` (Signal) | a run's time | digits change as text (not motion); under Signal it stands next to the breathing go-lamp, which replaces the live-line sweep | same (lamp still) | same (lamp still) | — |
| `KitScreen` system bars | theme changes | icon brightness switches with the theme in the same frame (no animation; owner rule R4) | same | same | — |

Captures for these rows (desktop harness unless marked): `gear_turn_{0,100,200}ms_{ltr,rtl}`, `send_glyph_{ltr,rtl}`,
`plus_turn_{0,75,150}ms`, `stateview_new_state_{0,125,250}ms`, `needsyou_{0,125,250}ms`, `guide_next_{0,125,250}ms`,
`optioncard_mark_{0,75,150}ms`, `progressring_{0,125,250}ms`, `image_first_frame_{0,75,150}ms`,
`topbar_title_{0,75,150}ms`, `sessiontabs_{0,75,150}ms`, `queued_swap_{0,75,150}ms`, `bgline_{0,100,200}ms`,
`toolrow_open_{0,75,150}ms`, `findmark_{0,75,150}ms`, `feed_sweep_{0,1100}ms_{ltr,rtl}`, `stepname_sweep_{0,1100}ms`,
`signal_live_lamp_{breath0,breath1}`, `terminal_keycap_pressed` (android harness: no ripple). Each part is also listed in
the kit motion manifest that `MotionClassTableTest` and `ReducedMotionSettlesTest` walk.

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

**Why `View.performHapticFeedback` and not `LocalHapticFeedback`** (ADR §9.4 says "`LocalHapticFeedback` mapped from
`KitHaptic`"; §13.2 corrects that wording): Compose's `DefaultHapticFeedback` passes its constant through `ViewCompat`,
which remaps `TOGGLE_ON`, `TOGGLE_OFF` and `SEGMENT_TICK` on older APIs to its own fallbacks [V, ui-android 1.12.1]. The
`View` call keeps those three exact on API 34+ and keeps **our** chosen `CLOCK_TICK` fallback below 34 (today's feel).
`LocalHapticFeedback` stays banned everywhere, the kit included (R10).

| `KitHaptic` | When (MOT-11) | API 34+ | API 30–33 | API 26–29 | Compose type with the same constant |
|---|---|---|---|---|---|
| `Send` | the person's words leave: Send and "Stop and send" (`kit_composer.dart`); voice-mode Send and Send now (`kit_composer_layer.dart`); a committed mic hold-to-talk release (`kit_mic_hold.dart`, not on a cancelled release); an answer on a request card (`kit_request_card.dart`, once per answer) or request sheet (`kit_request_sheet.dart`); a choice list pick that sends (`kit_choice_list.dart`); a phrase chip tap that sends; **copy** (`kit_copy.dart`, while D8-3 keeps its default) | `VIRTUAL_KEY` | same | same | `VirtualKey` |
| `Done` | a waited-for finish, once, never on mount, skipped under Off and system 0: a reply finished (busy → idle, `chat_notices.dart`); `KitStateView` turning from progress to ok **while the person watches** (a server start or connect finishing; `kit_state_view.dart`); `KitChecklist` when every step is done, only while visible (`kit_checklist.dart`); phone setup ready (`phone_setup_ready_screen.dart`); the QR scanner locking on a code (section 10). ("Task merged" is AI Team, which comes after the switch; it is not in this list.) | `CONFIRM` | `CONFIRM` | none | `Confirm` |
| `Commit` | a confirmed stop, delete or discard: the Stop chip's tap (`chat_session_actions.dart`, not for an isolated session); `KitConfirmSheet` of kind stop/destructive/discard; the **start** of a mic hold-to-talk (`kit_mic_hold.dart`) | `KEYBOARD_TAP` | same | same | `KeyboardTap` |
| `LongPress` | a long-press opens a menu or starts a selection (`KitTappable`, once) | `LONG_PRESS` | same | same | `LongPress` |
| `ToggleOn` / `ToggleOff` | a switch flips (`KitSwitch`, a switch row) | `TOGGLE_ON` / `TOGGLE_OFF` | `CLOCK_TICK` | `CLOCK_TICK` | `ToggleOn` / `ToggleOff` |
| `Select` | a segment chosen (`KitSegmented`), a slider step (`KitStepSlider`) or wheel value passed (`KitWheel`); each cross into or out of the mic hold's cancel zone (`kit_mic_hold.dart`) | `SEGMENT_TICK` | `CLOCK_TICK` | `CLOCK_TICK` | `SegmentTick` |

Section 10 (voice, scanner) uses this same list; it adds no kinds and no call sites of its own beyond the mic hold
and the QR lock named here.

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

- **Never** on scroll, plain taps of rows or buttons, tab taps, failures, sheets, dialogs, pull-to-refresh arming,
  swipe thresholds, predictive back, or streaming (MOT-11). Copy keeps today's light tick (`Send`) unless the owner
  removes it (D8-3).
- **Exactly one** long-press tick: `KitTappable` uses `combinedClickable(hapticFeedbackEnabled = false, …)` [V: the
  parameter exists in foundation 1.12.1] and plays `KitHaptic.LongPress` itself, so the in-app Vibration choice applies
  and the ripple and the tick share one path (Flutter fix `2048185f7`).
- Text-handle ticks come with `BasicTextField`; never add more.
- Notification arrival vibration belongs to the notification channel (section 11).
- The in-app Vibration choice (`KitEffects.haptics`, default on) gates every kind. It is not stored today; when the
  owner later asks for a visible row, it is stored device-wide under a **new** key name (never `oc.effectsHaptics`,
  which old installs may hold as `false` and which the migration deletes, §3.1).
- iOS later: `UIImpactFeedbackGenerator`/`UISelectionFeedbackGenerator` behind the same enum. Desktop: no-op.

### 6.4 Tests

| Test | Kind | Asserts |
|---|---|---|
| `HapticConstantsTest` | Android host (Robolectric, SDK 29/33/36 configs) | `constantFor` table per SDK |
| `HapticPolicyTest` | `commonTest` | `allowedAt`: Done skipped under Off; nothing when Vibration is off |
| `KitHapticsUiTest` | desktop compose tests with `FakeKitPlatform` (records kinds) | Send on send and on "Stop and send"; Commit on the Stop chip; LongPress once per long-press; ToggleOn/Off on a switch; Select once per new slider step and none for the same step; a `KitStateView` going progress → ok gives Done **once**, and nothing when it is composed already ok (on mount) or under Off; a `KitChecklist` completing gives Done once, and nothing while not visible; the mic hold gives `[Commit]` at hold start, `[Select]` on each cancel-zone cross, `[Send]` on a committed release and nothing on a cancelled one; copy gives `[Send]` (D8-3 default); nothing on a row tap, sheet open or failure (B-KIT-014, B-KIT-031) |
| Device check H-1 to H-3 | emulator (no vibrator: verify calls via logcat tag in the `qa` build) and the owner's phone (feel) | §9.7 |

---

## 7. Reduced-motion matrix (every interaction)

"Instant" = the next frame shows the final state and nothing keeps running (MOT-7). System "Remove animations" behaves
exactly like Off.

| Interaction | Full | Calm | Off / Saver / system 0 |
|---|---|---|---|
| Streaming reveal | paced, words fade, box grows, caret breathes (1100 ms each way) | paced, words fade, box grows, caret still | text as published, no fade, no grow, caret still |
| Follow-scroll after send | glide (or jump if far) | glide (or jump if far) | jump |
| Jump pill | fade + 8 dp rise | fade | instant |
| Sent bubble lift | rise from the field, 0.92 → 1, fade | fade in place | none |
| Send ⇄ mic ⇄ "Stop and send" | cross-fade (`KitSwap`) | cross-fade | instant |
| Stop chip arrives / leaves | fade (`KitSwap`); an empty strip unfolds / folds (`KitReveal`) | same | instant |
| Voice "Stop reading" ring | turns only if the glow is off | still track | still track |
| Glow | travels + halo breathes | still ring + steady halo | none (Saver: still hairline) |
| Glow fade in/out | 450 ms | 450 ms | instant |
| Row → chat | container transform 450 / 300 ms | **same** (Flutter parity) | instant |
| Predictive preview | drawn | drawn | not drawn; commits on release |
| Page (Android) | fade-forwards 450 ms (D8-5 default) or shared axis 250 ms | **same** | instant |
| Full-screen dialog | slide up / down | **same** | instant |
| Top-level fade-through | fade-through 250 | **same** | instant |
| In-page tab fade-through | fade-through 250 | cross-fade 150 | instant |
| Settings gear quarter turn | 200 ms turn | same | none |
| Sheet open / close / settle | springs | springs | snap (drag still follows) |
| Sheet predictive back | M3 preview | M3 preview | commit only |
| Dialog / menu | fade + rise / drop | fade | instant |
| List insert / remove | fade (+ rise for late feed rows) | fade | instant |
| List move | placement spring | jump | jump |
| Swipe action | row out + gap closes | row out + gap closes | jump |
| Work-line fold / step arrival | fold + fade | fold + fade | instant |
| Step light (running / background) | lap 2000 / 4000 | lap 4000 / 4000 | still node |
| Step light waiting breathe | 1600 ms each way | same | still |
| Step light endings (done / failed / stopped) | 860 / 300 / 200 ms | same | final look at once |
| Step arrival rise | 6 dp rise + unfold + fade | unfold + fade | instant |
| Spinners, loading bar | full speed | half speed | still |
| Live line sweep | 2200 ms | 4400 ms | still |
| Feed-row "Working" sweep, step-name sweep | 2200 ms | 2200 ms | still |
| Signal go-lamp breath | breathes | still | still |
| Request card arrive / collapse | fold + fade | fold + fade | instant |
| Pull to refresh | follows; breath while running | follows; still while running | follows; still; leaves at once |
| Overscroll | platform stretch | platform stretch | platform stretch (release scaled by system scale) |
| IME | frame by frame | frame by frame | frame by frame (system) |
| Composer height | `SpatialFast` | `SpatialFast` | instant |
| Ripple | platform | platform | flat fill (in-app Off) |
| Arrival wash | scroll glide + wash fade (900 ms) | same | jump + wash removed at once |
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
owner's phone report shows them. `Display.hasArrSupport()` is API 36+: the call is guarded by
`Build.VERSION.SDK_INT >= 36`, and below 36 the ARR line is skipped (supported modes are still logged; minSdk 26).
`MotionSignalsSdkTest` (§3.6) covers 26, 29 and 36. Owner rule: nothing ever runs below 60 fps; `preferredFrameRate` is never set below
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

`MotionPolicyTest`, `MotionNoteTest`, `MotionPrefMigrationTest`, `MotionClassTableTest`, `KitMotionSpecsTest`,
`MovingPartTest`, `RevealClockTest`, `RevealInitTest`, `GrowOnlyTest`, `FadeMaskTest`, `FollowAnchorTest` (these five
are S2-KC's, §5.1), `StopGuardTest`, `RowTransformGateTest`, `PredictiveBackPolicyTest`, `ShellMotionTest`,
`FeedHoldViewModelTest`, `StepLightPhaseTest`, `SignalLampTest`, `CountRollTest`, `HeldAnswerTest`, `RefreshPhaseTest`,
`HapticPolicyTest`, `GlowColoursTest`, `SideSheetAnchorsTest`. All run in `commonTest` (desktop JVM) except
`HapticConstantsTest`, `MotionSignalsSdkTest` and `HaloPainterReuseTest` (Android host, Robolectric). Each is listed
with its rows in §3.6 and §5. The `MotionClassTableTest` manifest registers every part of the §5.15 table (including the
feed-row and step-name sweeps, the Signal lamps and the timer, the gear turn, the state view, the guide and the option
card) with exactly one class. Names follow the ratchet form, e.g.
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
- **`NoUnrequestedMovementTest`** (R13): with a fake feed and fake connection, (1) a timed notice that expires while
  the page is shown does not change any row's position until a page change; (2) replacing stand-in / last-known rows
  with live rows of the same keys leaves every row's bounds unchanged on the next frame and starts no animation;
  (3) the opening card keeps its height from Starting to Connecting; (4) a chip composed with a last-known value shows
  it on frame 1.
- Loops in tests: framework infinite animations are paused by the test harness's `InfiniteAnimationPolicy`
  [U: confirmed on CMP 1.13 in S8-5]; hand-written loops are banned (R4), so no test hangs.

### 9.4 Frame captures (screenshot frames)

- Tool: Roborazzi 1.77.0 (ADR §8), recorded **only on the VPS** (the golden host); the PC may compare but never record.
- **Two harnesses, split by what they can draw.** Desktop `runComposeUiTest` renders through Skiko and cannot show the
  Android ripple (`RippleDrawable`), the Android `dropShadow` renderer, `RenderEffect` frost or the Android `NavDisplay`
  predictive path. So:
  - **common parts** (word fade, caret, count roll, reveal/fold, step light and Signal lamps, live and feed sweeps,
    lift, feed insert/move/stand-in replace, request card, tabs, tips, menus' fade, page slides as `ContentTransform`s)
    use desktop Roborazzi (`roborazzi-compose-desktop`);
  - **Android-seam captures** (ripple, glow halo, frost header, predictive previews and their commit/cancel frames,
    sheets and the side sheet, `GrowFromRow` through the real `NavDisplay`) use Robolectric with
    `@GraphicsMode(GraphicsMode.Mode.NATIVE)` on the VPS (Android host tests in `androidUnitTest`).
- Method: set the state, `mainClock.autoAdvance = false`, advance with `mainClock.advanceTimeBy(ms)`, then
  `onRoot().captureRoboImage(path)`. Loops use `stillFrame`.
- Naming: `motion/<part>/<interaction>_<t>ms_<level>_<theme>_<dir>.png`, e.g.
  `motion/chat/grow_open_180ms_full_dark_ltr.png`.
- Matrix (kept small): every capture in **dark + en**; the direction-sensitive ones (lift, page slides, predictive shift,
  live and feed sweeps, menu drop, swipe, gear turn, Send glyph, word fade) also in **ar (RTL)**; one light capture per interaction; Calm one mid-frame; Off one
  frame (which must equal the Full end frame).
- The capture list is the union of the "Verified by" lines in §5 (about 170 images [E]), each tagged `common` or
  `android` for the harness split above. Each golden header comment
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
- Fixture: section 13's (`perf:stream-6000`, `perf:history-5000` on the mock-model server, testing-perf §6.4).
- Trace sections (androidx.tracing 2.0.5; never message text in names): `kit.reveal.word`, `kit.reveal.fade`,
  `kit.glow.draw`, `shell.rowTransform`, `shell.predictive`, `kit.sheet.settle`, plus section 09's `chat.applyDelta`.

| Journey | Class | Steps | Metric | Gate |
|---|---|---|---|---|
The journeys' **steps and fixture triggers are section 13's** (PJ-05 … PJ-11, §13 owns `:android:benchmark`); this
table keeps only what motion adds: the gate and the trace sections each journey must report.

| Journey | Class (section 13 id) | Motion adds | Gate |
|---|---|---|---|
| J2 Streaming | `StreamingBenchmark` (PJ-05) | the glow runs throughout; `kit.reveal.word`, `kit.reveal.fade`, `kit.glow.draw` | S-9, S-12 |
| J3 Fling | `TranscriptFlingBenchmark` (PJ-06) | no placement animation in the transcript | S-10 |
| J4 Row → chat | `RowToChatBenchmark` (PJ-07) | `shell.rowTransform`; includes open Settings and back (`Page`) | S-13 |
| J5 Predictive back | `PredictiveBackBenchmark` (PJ-08) | commit and cancel from both edges; `shell.predictive` | S-13 |
| J6 Sheet | `SheetBenchmark` (PJ-09) | `kit.sheet.settle`; predictive close | S-13 |
| J7 Keyboard | `ImeBenchmark` (PJ-10) | no list recomposition per IME frame | S-13 |
| J8 Refresh | `RefreshBenchmark` (PJ-11) | indicator draw only | S-13 |

```kotlin
// android/benchmark/src/main/kotlin/io/github/eslamasabry/oc/benchmark/motion/PredictiveBackBenchmark.kt
// (illustration of the gesture calls only; section 13 PJ-08 owns the steps, fixture and target)
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
| P2-5 | open a chat from its row; from a second client archive or delete that conversation (or use the `qa` build's debug hook "detach origin"); Back | falls back to the Page pop (the held list cannot move the row, so the origin must be removed, not moved) |
| P2-6 | Off and "Remove animations" | no preview; plain pop |
| P2-7 | 3-button navigation, tap Back / long-press Back (Android 16, `EDGE_NONE`) | tap = shrink into row; long-press = preview that scales and fades in place with no sideways shift |
| P2-8 | open a chat from its row, send a message, then Back | the chat shrinks into its **original** row; only after it lands does the list re-sort, once |
| P2-9 | compare the preview with the phone's own back preview (Settings app) frame by frame | decides the shift (`PREVIEW_EDGE_SHIFT`) and corner (`PREVIEW_CORNER`) values; no corner pop on release (commit and cancel) |
| P2-10 | tap a row of an old agent conversation, decline the resume confirm; tap another row; make an open fail (server down) | the list unfreezes; the next tap still grows from its row |
| ST-1 | stream the story fixture at the live edge | words fade in word by word; no jumping lines; glow turns; caret breathes |
| ST-2 | scroll up during the stream | follow stops; pill appears; tap returns |
| SS-1 | send, then double-tap where Send was | the turn is not stopped |
| OV-1..3 | overscroll top and bottom of chats, settings, transcript; API 30 | stretch (API 37) / glow (API 30); no double stretch |
| IME-1..4 | keyboard up/down at the live edge (gesture and 3-button navigation), while reading history, in a sheet; IME-4: rotation without recreation (the `configChanges` path) and one forced recreation ("Don't keep activities") on the Android 17 image | composer and words ride the keyboard; the newest words clear the composer with both nav-bar sizes; reading position kept; sheet actions pinned; the keyboard stays through rotation and returns after the forced recreation |
| JT-1..5 | cold start → Conversations; connect a server; open a chat (excerpt → live); phrase chips; the branch chip (R13) | nothing the person did not ask for moves: no row shifts when live rows replace last-known ones, the opening card keeps its height, timed notices fold only on a page change, chips show values from frame 1 |
| SH-1..3 | sheet drag, fling, predictive close; dirty sheet swipe | as §5.5 |
| H-1..3 | haptic log (`qa` build logs `KitHaptic` kinds) for send, stop, long-press, switch, segment, mic hold, copy, a server connecting while watched | exact sequence per §6.2; nothing on plain taps, scroll, sheets |
| RM-1 | toggle "Remove animations" while a reply streams | everything stops on the next frame; the reply finishes as plain text |
| PH-1 | owner phone (after the emulator pass, as part of §02 S-27) | the owner's verdict on smoothness, the haptic feel (D8-1) and the predictive preview, recorded in their words |

---

## 10. Spikes (each ≤ 2 days, one agent, own worktree, emulator only unless stated)

| # | Question | Acceptance | Fallback | Agent | When |
|---|---|---|---|---|---|
| S8-1 | Option B: does Nav3 1.3-alpha + Compose 1.13-beta deferred handoff with `permitTransformDuringDeferredTransition` give a jank-free shrink into the row under the gesture? Run on **its own worktree branch** with the Android-only version constraint and the gate's `deferredHandoff` flag (§5.4); never merged while Nav3 1.3 is not stable (ADR D12) | the P2 checks + fling velocity handoff; Android resolves to Google's nav3 binary | stay on option A | Opus | slice 0 (with P2), re-run at each Nav3 1.3 pre-release |
| S8-2 | Does the `DstOut` word-fade layer (cached line-box rectangles, §5.1 rule 6) meet the stream budget on the NX721J? Run inside 09's SP-3 | J2 within S-9 on the emulator; `kit.reveal.fade` ≤ 1 ms p95; italic and Arabic captures fade whole words | fade the newest **line** with one vertical gradient mask instead of per-run rectangles; if still over, whole-block fade for newly inserted blocks only | Opus (S2-KC) | slice 2 |
| S8-3 | `imeNestedScroll` on the NX721J: does dragging the transcript move the keyboard naturally without breaking scroll-to-read? | owner-visible clip on the emulator + a tip registered | stays off (D8-2) | Sonnet | slice 3 |
| S8-4 | Haptic constants on the NX721J: are `VIRTUAL_KEY`, `KEYBOARD_TAP`, `TOGGLE_*`, `SEGMENT_TICK` distinct and pleasant? | owner listens during the phone test | §6.2 table stays; owner may pick 06's alternative (D8-1) | coordinator with owner | slice 6 phone test |
| S8-5 | `InfiniteTransition` at animator scale 0 and the CMP 1.13 test harness's `InfiniteAnimationPolicy` | loops never block `waitForIdle`; at scale 0 our guard keeps them unstarted anyway | none needed (R4 already prevents it) | Sonnet | slice 0 (K0) |
| S8-6 | Is `Modifier.preferredFrameRate` exported to `commonMain` by CMP 1.13? | compiles in common | stays a `JETPACK_AHEAD` row behind `KitPlatform.highFrameRate` (already planned) | Sonnet | slice 0 |
| S8-7 | M3 1.5 in-window `BottomSheet` used modally: scrim, TalkBack focus trap, IME, predictive scaling | TalkBack cannot reach the page under an open sheet; IME rides; preview plays | `ModalBottomSheet` behind `KitSheetHost` | Sonnet | slice 0 (K0) |
| S8-8 | Reverse layout vs normal layout when the reader is near the bottom with the growing tail partly visible (text pushed up under the eyes) | the variant where reading position holds | compensate with `scrollBy(sizeDelta)` for the first visible item, or pause growth until the reader returns | Opus (with section 09) | slice 2 |
| S8-9 | Halo cost. **First variant:** a dedicated halo node whose `dropShadow` parameters are constant (alpha 1), modulated by `graphicsLayer { alpha }` (§5.3); second: three stacked strokes | `kit.glow.draw` ≤ 0.5 ms UI, no RenderThread spikes on the emulator, `HaloPainterReuseTest` green | stacked strokes | Sonnet | slice 2 |
| S8-10 | `KitSideSheet` on `AnchoredDraggableState` + `NavigationBackHandler` progress (no M3 side sheet exists) | drag, fling, predictive shift and cancel feel like the bottom sheet's on a tablet AVD, in LTR and RTL; TalkBack cannot reach the page under it | a centred 560 dp panel for `full` sheets on wide windows (no side sheet) | Sonnet | slice 1 (with M3) |

---

## 11. Work units (for agent briefs)

Each unit lists its write set, read set, acceptance and catalog rows. Units nest inside section 06's kit units and
section 07's proof P2, so ownership stays single.

| Unit | Slice | Inside | Agent | Write set | Acceptance | Rows | Effort [E] |
|---|---|---|---|---|---|---|---|
| M0 Motion foundation | 0 | §06 K0 | kit owner (Opus) | `:ui:design` `motion/MotionTokens.kt`, `MotionPolicy.kt` (resolver + reasons); `:ui:kit` `motion/KitMotion.kt` (all §2.7 builders), `KitMotionScheme.kt`, `KitEasing`, `LocalKitMotion(Specs)`, `LocalKitMotionProbe`; `platform/AndroidHaptics.kt`; motion signals in `AndroidKitPlatform` (SDK guards) | §3.6 tests green; `HapticConstantsTest`, `MotionSignalsSdkTest`; Konsist R1/R4/R10/R11 and `LiteralBanTest` live; ripple + haptic seen on the emulator (P7) | B-KIT-006, -008, B-STATE-003, B-MIGR-005 | 0.5 aw |
| M1a Predictive back + row transform A, **shell half** | 0 | §07 P2 | **coordinator** (Opus) | `:ui:shell` `nav/ShellMotion.kt`, the motion entry decorator (running set, dim, preview corners), gate wiring, `PopCause.AfterGuard`; `:ui:routes` `FeedHold.kt` + `holdOrder` helper | `ShellMotionTest`, `PredictiveBackPolicyTest`; device checks P2-1..10 | B-KIT-054 (row and back clauses) | 0.6 aw (+ S8-1 0.4 aw) |
| M1b Predictive back + row transform A, **kit half** | 0 | §06 K0 | **kit owner** (Opus) | `:ui:kit` `motion/RowTransformGate.kt` (gate + origin claim), `Modifier.growFromRow`, `KitRouteTransition` | `RowTransformGateTest` (all stuck-gate cases) | B-KIT-054 | 0.4 aw |
| M2 Kit motion parts | 1 | §06 K1 | kit agent (Sonnet) under the kit owner | `KitSwap`(+`count`), `KitSpin`, `KitReveal`, `KitEntrance`, `KitPresence`, `KitAnimatedRows`, `KitAnimatedValue`, `KitDim`, `KitRefresh`, `KitFeedList` motion (no hold; stand-in replace rule), `KitSwipeAction`, `KitArrival`, `KitTip`/`KitTour` motion, `KitMenu`/`KitUndo` motion, and the §5.15 rows: `KitIconButton` turn, `KitStateView`, `KitNeedsYou`, `KitGuide`, `KitOptionCard`, `KitProgressRing`, `KitImage`/`KitAvatar`, `KitTopBar` title, `KitSessionTabs`, `KitFeedItem` sweep, `KitTerminalKeys` flat fill | §5.15 lines in every KDoc; `ReducedMotionSettlesTest`, `FirstBuildFinalTest`, `NoUnrequestedMovementTest`, `FeedListMotionTest`, `RefreshPhaseTest`; the kit motion manifest lists every row; frame captures recorded on the VPS | B-KIT-011, -014, -015, -019, -022, -023, -025, -031, -046, -050, -051, B-NAV-073 | 1.8 aw |
| M3 Sheet and dialog motion | 1 | §06 K0/K1 | kit owner | `KitSheetHost` (Off back handler), `KitSheet`, `KitSheetStep`, `KitSideSheet`, `KitDialog` | §5.5 tests and captures; S8-7, S8-10 | B-KIT-015, -016, -032 | 0.8 aw |
| ~~M4 Streaming motion~~ | — | — | — | **folded into section 09's unit S2-KC** (one write set for `RevealRegistry`, `kitFreshFade`, the growth node, caret, lift, follow, jump pill); §5.1 gives S2-KC its motion values, rules and tests | — | B-CHAT-060, -096 (S2-KC) | (in S2-KC) |
| M5 Composer and turn motion | 2–3 | kit-chat | kit-chat owner (Sonnet; Opus for the glow render-compare loop) | `ComposerGlow.kt` (halo node + ring), `MovingPart.kt`, `KitComposerStop` chip and the status strip's reveal, field button changes incl. the "Stop and send" pill, the voice circle's ring (with section 10's owner), `KitTurnLive` sweep and Signal lamp, `KitWorkLine`/`KitStepTimeline` light and Signal lamps, `KitToolRow` status and body, `KitRequestCard` motion, `KitQueuedMessage`, `KitBackgroundWorkLine`, `KitFindMark`, IME frame | `MovingPartTest`, `StopGuardTest`, `GlowColoursTest`, `HaloPainterReuseTest`, `StepLightPhaseTest`, `SignalLampTest`, `HeldAnswerTest`, `ComposerKeysUiTest`, `ImeClearanceTest`; the glow compared side by side with a Flutter build capture on the same emulator (owner rule "judge visuals hard") | B-CHAT-006, -012, -050, -063, -117, -123, B-KIT-002, -036 | 2.2 aw |
| M6 Benchmarks | 2 onward | §13 owner | Sonnet | motion trace sections and the motion gates in section 13's `:android:benchmark` journeys PJ-05..PJ-11 (13 owns the files, steps and fixtures) | journeys run on `OC_API37`; numbers stored next to the APK; first native numbers replace the [E] budgets | S-9 … S-13 | 0.5 aw |
| M7 Motion evidence pass | 6 (before any owner APK of the switch) | coordinator | Sonnet + coordinator review | `docs/qa/native/motion-<date>/README.md` + clips | every §9.7 row passes on the emulator; then PH-1 with the owner | — | 0.5 aw |

Brief text every motion unit carries (owner rules, motion edition): 150–250 ms; exits faster; springs only for finger-driven
or interruptible motion; no overshoot in new motion; nothing moves under Off; Calm keeps fades, folds and route
transitions, turns small travels into fades, stops ambient loops and halves the live-line, step-light and spinner loops;
nothing that appears or changes on its own moves what the person is reading (R13); one ambient loop per region and one
moving part in the composer; native ripple, stretch, predictive back and the §6.2 haptics only; RTL-aware directions; a
tip for any hidden gesture; kit only; name a `KitMotionSpecs` builder, never a number; read animated values in draw or
layout; say in the brief what the change removes.

---

## 12. Owner decisions (each has a default; work proceeds on it until you answer)

| # | Question | Default (built if you say nothing) | Alternative |
|---|---|---|---|
| D8-1 | Vibration feel. Keep today's exact vibrations for send, stop, finish and long-press, and use Android 14+'s own switch and segment ticks? | **Yes** (§6.2) | section 06's proposal: send = Confirm, stop = LongPress |
| D8-2 | Drag the conversation to open or close the keyboard (Google Messages style)? It is a hidden gesture and needs a tip. | **Off** at the switch; on only if spike S8-3 looks right on your phone | on, with a tip |
| D8-3 | Copy ticks today (a light tick); swipe actions and pull-to-refresh do not. Keep it that way? | **Yes, as today**: copy keeps its tick; no tick for swipe or refresh (nothing lost) | remove the copy tick too (the written standard MOT-11 says "nothing on copy"); or add Android's threshold tick for swipe and refresh |
| D8-4 | Pull to refresh: keep the app's portal drawing, or use Android's stock spinner? | **Keep the portal drawing** | stock M3 indicator |
| D8-5 | Opening a page (Settings and most pages) on Android: keep today's Android slide-and-fade (the same as other apps on your phone, 450 ms), or use Material's shorter shared-axis slide (250 ms)? | **Keep today's Android move** (exception X14; your 2026-10-26 rule "max Android native") | shared axis X, 250 ms (the ADR's wording) |

Settled elsewhere and not asked again: Stop's place and look (2026-10-30), the glow (2026-10-14) and its single choice
(2026-10-23 Appearance cuts), no glass except the frost (D10), no bottom bar (2026-10-20), the chat motions (2026-10-27),
the bouncy lens springs (section 06 Q5).

---

## 13. Changes from Flutter, and corrections for sibling sections

### 13.1 Changes from the Flutter app (each with its reason)

| Change | Flutter | Native | Reason |
|---|---|---|---|
| Predictive back with the row transform | yields to the system builder during the gesture | the same idea as a level-triggered gate (§5.4); option B later | the demo's 700 ms latch was a guess; ADR §9.4 |
| Page transition on Android | Flutter's Android fade-forwards, 450 ms | **unchanged by default** (D8-5); shared axis X 250 ms only if the owner picks it | owner rule 2026-10-26; B-KIT-054 |
| Predictive preview | Flutter's `PredictiveBackPageTransitionsBuilder` (Android's full-screen preview) | Material full-screen values plus a swipe-edge shift and corners, settled by P2-9 | Nav3 draws the preview itself; P2-9 compares with the phone's own |
| Long-press on Stop | opens a one-item `KitMenu` ("Stop after this step") | **unchanged**: the same one-item menu (§5.2) | parity; no change |
| Sheets | Flutter 3.47 sheets had no predictive back | M3 in-window sheet with predictive back | native feel |
| Swipe action | the row snapped back, then the list removed it | the row continues out, then the gap closes | Android's standard pattern |
| Toggle and segment haptics | `CLOCK_TICK` | `TOGGLE_ON/OFF`, `SEGMENT_TICK` on API 34+ | max native (D8-1) |
| Copy haptic | a light tick on copy | **unchanged** (D8-3 default) | parity ("nothing lost"); removing it needs the owner's yes |
| Battery saver and heat | not wired (MOT-13 "missing: G39") | force Calm, and the Motion row says why (§3.2) | MOT-13; the reason note keeps it from being a silent downgrade |
| Streaming reveal | text shown as published, new runs fading | paced reveal (45 cps, ≤ 250 ms behind) + the same fade | the demo the owner felt used a paced reveal |

### 13.2 Corrections for sibling sections and the ADR (coordinator merges)

| Where | Says | Should say | Why |
|---|---|---|---|
| §06 §4.8 springs table | "`lift` (260, 20) … sent bubble lift from 0.92" | `lift` is the **tab lens** lifting while dragged; the sent-bubble lift is a 250 ms `EaseOutCubic` tween (X3) | `kit_message_lift.dart` uses `KitMotion.standard` + `KitMotion.enter`; `lift` belongs to the lens [R] |
| §06 §4.8 `MotionTokens.EXIT = 100` | exits 100 ms | exits use `QUICK` (150 ms), as every Flutter reverse slot did; drop `EXIT` or keep it unused | parity; no Flutter part used 100 ms |
| §06 §4.9 haptics | Send = `Confirm`, Commit = `LongPress` | §6.2 table (default of D8-1) | today's constants, read from the Flutter embedding [V] |
| §06 §5.1 `LocalKitMotion` | `KitMotionState(level, reduced, travels)` | add `stillFrame`, `debugTimeScale`, `ambientLoops`, `progressLoopSpeed` (§2.7) | loops and frame captures need them |
| §07 §3.2 sketch | clears `poppedByGesture` in `LaunchedEffect(isTransitionActive)` | clear on `TransitionSettled` from the entry decorator (§5.4 gate) | `isTransitionActive` stays false for a gesture pop with disabled shared bounds, so the flag would never clear and the next tap would lose its transform |
| §03 §15.4 `RouteMotion` | `Root, SharedAxisX, GrowFromRow, SlideUp` | section 07's names: `GrowFromRow, Page, FullScreenDialog, TopLevel, Replace, Root` | one name per class; §07 is newer and complete |
| §06 file tree (`motion/KitTransitions.kt`), §06 rows for `KitPageRoute`/`pushKitPage`, `KitPageOrigin` → `Modifier.kitPageOrigin(key)`/`kitPageOriginTarget(key)`, `KitAndroidPageTransitionsBuilder` → `KitTransitions` (specs), and test `KitTransitionsTest` | route transitions live in the kit owner's `KitTransitions.kt` with `KitTransitions.pageForward/pagePop/predictivePop` and `kitPageOrigin` modifiers | **retire** `KitTransitions`, `KitTransitions.*`, `Modifier.kitPageOrigin`, `kitPageOriginTarget`. Replacements: `ShellMotion` in `:ui:shell` (**coordinator**; combines builders only) + `RowTransformGate` with the one-shot origin claim and `Modifier.growFromRow(key)` in `:ui:kit` (**kit owner**); the numbers are `KitMotionSpecs` builders in `KitMotion.kt`. Tests: `ShellMotionTest` + `RowTransformGateTest` | one home per piece; R1 would reject `tween(` in a `KitTransitions.kt`; 03 §15 and 07 §3.2 already use `ShellMotion` and the gate |
| §03 §15.6 `KitRouteTransition.gestureInProgress` | the kit reads a per-entry gesture flag | the kit reads the gate's `sharedBoundsEnabled(state, level)`; `KitRouteTransition` keeps only the entry's `AnimatedContentScope` | the gate is the single source (§5.4) |
| §07 §3.1 route table, Calm column | "cross-fade 150 ms" / "cross-fade" for every class | **as Full** for route classes (Flutter parity: Calm only stops ambient loops and small travels); `GrowFromRow` "chosen when" = a row's own tap claimed the origin (`openChat(fromOrigin = true)` consumes the one-shot claim) | §3.3, §5.4; the owner's grow from the row must survive battery saver (MOT-13 → Calm) |
| §07 §3.1 `Page` forward | "shared-axis X, 250 ms" | on Android: Android's fade-forwards, 450 ms (D8-5 default, X14); shared axis X only if D8-5 picks it | owner rule 2026-10-26; B-KIT-054 |
| §07 §3.2 frozen-list bullet | the screen passes `frozen = true` to the kit feed list while the gate is not Idle or a page covers the list | the **feed ViewModels** hold the row order through `FeedHold` (`:ui:routes`), from the origin claim until the return settles; `KitFeedList` has no `frozen`; `LocalKitPageVisible` exists only in list-detail scenes | a covered single-pane entry leaves composition under Nav3, so a hold in the list part is destroyed (§5.6) |
| §07 §3.2 sketch (`gate.onBackCommitted()`, motion decorator "0 → TransitionSettled") | edge-triggered settle | the decorator keeps a level-based running set (removed on dispose too) and sends `TransitionSettled` when it empties and one frame after every gate change; `reduce(state, event, running)` | a cancel at ≈ 0, a guarded gesture or a disposed entry would leave the gate stuck (§5.4) |
| §07 §3.4 chat Back guard | on save, pop | the guard pops with `nav.pop(PopCause.AfterGuard)`, which the shell plays as a `Page` pop | §5.4 guarded-gesture rule |
| §09 §8.2–§8.6 (streaming) | a second spec of the same code: `RevealRegistry`, `kitFreshFade`, `animateContentSize`, and a level table | 09 stays the **single** spec and adds: (a) `dt` divided by the animator scale; (b) a state starts at `shown = text.length` unless its block or reply was born after the transcript was shown (`fadeIn`/`fromZero`), and the registry is keyed by part id + block start, outside the lazy items, keeping `shown` across a refetch; (c) the fade mask is line-box rectangles computed once per `TextLayoutResult` and widened by an overhang allowance (`getPathForRange` is `Layout.getSelectionPath`); (d) §8.4: the growth-only `LayoutModifierNode` is primary, `animateContentSize` is not used (it animates shrink, contradicting "shrinks taken at once"); (e) §8.6 is replaced by 08 §5.1's level table (Calm grows, glides, folds and sweeps at 4400 ms; the lift is a 250 ms `EaseOutCubic` tween, not the `lift` spring) | Flutter agrees with 08: `kit_markdown_fresh.dart:342`, `kit_turn.dart:1193`, `kit_message_lift.dart`; one write set |
| §09 §15 unit S2-KC | `RevealRegistry`, `kitFreshFade`, … | also owns the caret, lift, follow-scroll and jump pill motion and the tests of 08 §5.1 (`RevealClockTest`, `RevealInitTest`, `GrowOnlyTest`, `FadeMaskTest`, `FollowAnchorTest`); 08's unit M4 is folded into it | one write set |
| §09 §10.3 chip-row table, row "Stop" | "neutral circle with a ground square; amber when stuck" | the `KitComposerStop` chip: "Stop" + stop icon, neutral, attention tone when stuck, "Stop {agent}" spoken, disabled while working, hold or right-click opens "Stop after this step", tip on the chip; the neutral circle with a ring is only the voice "Stop reading" control | batch1 `kit_composer_status_strip.dart` (2026-10-30) |
| §05 migration (Appearance keys) | — | delete `oc.effectsGlowColours`, `oc.effectsGlowSpeed`, `oc.effectsHaptics`, `oc.effectsGlass` at first launch; never map them | §3.1; the glow is one choice; stale `false` values must not switch things off |
| §10 haptics | its own list of haptic moments | refers to 08 §6.2 (mic hold Commit / Select / Send, QR lock Done); adds no kinds | one list (R10) |
| §13 PJ-05 … PJ-11 | — | the only home of the journeys' steps and fixtures; 08 §9.5 lists only the motion gates and trace sections | the two sections gave the same classes different steps |
| ADR §9.4 "native feel" | "`LocalHapticFeedback` mapped from `KitHaptic`" | "`View.performHapticFeedback` constants mapped from `KitHaptic` in `AndroidKitPlatform`" | View constants keep `TOGGLE_ON/OFF` and `SEGMENT_TICK` exact on API 34+ and keep our `CLOCK_TICK` fallback below 34, where `ViewCompat` (inside `LocalHapticFeedback`) would remap them (§6.2) |
| ADR §9.4 item 2 | "Settings pages: shared-axis X with system predictive back" | "Settings pages: the `Page` class (D8-5: Android's fade-forwards by default) with system predictive back" | owner rule 2026-10-26; parity |
| ADR §8 navigation row | `navigationevent-compose` 1.1.0 (CMP) | test with Jetpack `navigationevent-testing` **1.1.2** (same line as the navigationevent the app ships), or move every navigationevent artifact to 1.2.0 together | otherwise tests resolve navigationevent 1.2.0 while the app ships 1.1.x |
| Catalog B-KIT-054 | "Page transitions use the Android system transition on Android" | covered by the D8-5 default; if D8-5 picks shared axis X, mark the row "changed by D8-5 / 08 §13.1" and name the native test after the amended row | the row must not be counted as covered by a different transition |
| Catalog B-KIT-055, B-KIT-028 | mapped to `KitTerminalView` / scenes | **deferred with ADR D11** (illustration scenes); the real terminal view adds no blink | `kit_states_scenes_test.dart` tests `StatesTerminalScene`, not the terminal |
| Not a correction: §06 `enum KitHaptic` | — | already the seven values `Send, Done, Commit, LongPress, Select, ToggleOn, ToggleOff` (06 file tree, line 173) | checked; no change needed |

---

## 14. Multiplatform notes (iOS and Linux desktop later)

| Piece | `commonMain` | Android | iOS (later) | Desktop (later) |
|---|---|---|---|---|
| Tokens, policy, specs, gate, feed hold, glow, lift (reveal clock: section 09) | all | — | — | — |
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
- Flutter source added in the 2026-11-01 review: `lib/ui/kit/chat/{kit_composer_status_strip,kit_composer_caption,
  kit_composer_layer,kit_step_signal,kit_mic_hold}.dart`, `lib/ui/kit/{kit_feed_item,kit_icon_button,kit_state_view,
  kit_checklist,kit_copy}.dart`, `lib/ui/kit/style/{kit_signal,kit_signal_slip}.dart`,
  `lib/ui/screens/chats/chats_home_screen.dart`, `lib/ui/screens/chat/{chat_stop_after_step,chat_session_actions,
  chat_notices}.dart`, `lib/state/app_preferences.dart`, `lib/ui/screens/settings/appearance_screen.dart`,
  `test/{appearance_effects_test,retired_prefs_test,kit_states_scenes_test,chat_stop_guard_test}.dart`; Shorebird Flutter
  `page_transitions_theme.dart` (`FadeForwardsPageTransitionsBuilder` tweens).
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
  haptic constants). Added in the review: `material3-android 1.5.0-alpha22` jar listing (no `SideSheet` class;
  `BottomSheet(…, backHandlerEnabled, …)`), `ui-android 1.12.1` `BlockDropShadowNode` (`setAlpha` → `invalidateShadow`
  → `shadowPainter = null`); Google Maven `navigationevent-testing` metadata (1.1.2 and 1.2.0 published).
- Web, accessed 2026-11-01: Navigation3 release notes https://developer.android.com/jetpack/androidx/releases/navigation3
  (1.2.0 stable 2026-09-23; 1.3.0-alpha01 `DeferredAnimatedContent` handoff fix; alpha02 title/url metadata; alpha03 no
  changes); androidx-main `navigation3/navigation3-ui/src/commonMain/kotlin/androidx/navigation3/ui/NavDisplay.kt`
  (transition parameters, metadata helpers, `defer`/`animateTo` under `InProgress`); `FrameRateCategory` and
  `preferredFrameRate` API reference https://developer.android.com/reference/kotlin/androidx/compose/ui/FrameRateCategory
  (CMP export not confirmed: spike S8-6). Everything else cites the research files, which list their own sources.

---

## Review notes (2026-11-01 revision)

Each issue was checked against batch1's Flutter source, the sibling sections, the Gradle-cache jars and Google Maven
before editing. What changed, one item per issue in roughly the review's order (duplicate issues are merged into one
item, so the numbers are this list's own):

1. **Frozen list under Nav3 (blocker).** Accepted. The hold moved out of `KitFeedList` into the feed ViewModels via a
   coordinator-owned `FeedHold` in `:ui:routes` (§5.6); `KitFeedList(frozen)` removed; `LocalKitPageVisible` limited
   to list-detail scenes (§3.5); `HeldSnapshotTest` replaced by `FeedHoldViewModelTest`; device check P2-8 added.
2. **08 and 09 specify streaming twice.** Accepted. §5.1 is now motion values, nine binding rules and levels only; 09
   §8 is the single code spec; M4 folded into S2-KC (§11). The edits to 09 itself (animator scale, init rule, mask,
   growth node primary, §8.6 table, lift tween) are §13.2 rows for the coordinator, since this pass edits only 08.
3. **Literal tweens outside the R1 files** (and issue "contradictions" item 3). Accepted. New `ROW_*`, `PAGE_*`,
   `ANDROID_PAGE_*`, `FADE_THROUGH_SPLIT`, `ARRIVAL_FADE_MS`, `ICON_TURN_MS`, `FSD_*` tokens and named builders in
   `KitMotionSpecs` (§2.7); §5.4 and §5.9 now name builders only; R1 says `ShellMotion` combines builders and
   `LiteralBanTest` exempts only the four R1 files. The old ratio tokens were replaced by durations.
4. **Gate edge-triggered; no `ContentTransform` for GrowFromRow; origin from any open** (merged with the second
   blocker, "gate can get stuck"). Accepted with one deviation: a new `Pending` state sits between the tap and the push,
   because a level-triggered settle would otherwise end `Opening` before the asynchronous open pushes anything. Added
   `OpenAbandoned`, `TransitionStarted(claimed)`, a level-based running set (removed on dispose), one-frame re-checks,
   `PopCause.AfterGuard` → `Page` pop, the row's "Opening" look, the one-shot origin claim with `ORIGIN_CLAIM_WITHIN`,
   the `KeepUntilTransitionsFinished` transforms with z-indexes, the `Page` fallback whenever shared bounds are off, and
   the stuck-gate test cases (§5.4).
5. **Preview corners snap on release.** Accepted. Corners now come from the exiting entry's own transition
   (`transition.animateDp`); captures `predictive_{commit,cancel}_+50ms` and P2-9 added.
6. **FullScreenDialog easing assumed a commit-only 30 %.** Accepted; the first shape was chosen (the whole seek range
   is a 10 % / 0.95 preview with a fade from 35 %; commit ends as a fade). The gesture-owning alternative is kept as a
   P2 fallback, not built. `fsdialog_predictive_90pct` added.
7. **IME clearance measured inside the nav-bar padding.** Accepted. `onSizeChanged` now sits before
   `navigationBarsPadding()`; `ImeClearanceTest` asserts positions for gesture and 3-button insets, keyboard up and down.
8. **Two homes for route transitions.** Accepted in part: §13.2 now retires `KitTransitions`, `kitPageOrigin` and
   `kitPageOriginTarget`, and M1 is split into a coordinator half (M1a) and a kit half (M1b). **Rejected:** "fix 06's
   `KitHaptic` to seven values" (06 already lists the seven, file tree line 173) and retiring `LocalRowTransformEnabled`
   (the name appears in no section).
9. **Named exceptions incomplete** (merged with the "contradictions" items 1–2). Accepted: X10 arrival wash, X11 step
   endings, X12 marks and refresh disc from 0.9, X13 FullScreenDialog preview, and X14 (conditional on D8-5);
   `ENTRANCE` taken off the reserved list as `ARRIVAL_FADE_MS`.
10. **`EDGE_NONE` shifted left.** Accepted: `sign = 0` for `EDGE_NONE`; `ShellMotionTest` and P2-7 cover it.
11. **Caret values drift.** Accepted: 1 → 0.25, `StepEase`, 1100 ms each way (verified in `kit_stream_caret.dart`).
12. **minSdk guards.** Accepted: thermal listener API 29+, `hasArrSupport()` API 36+, `MotionSignalsSdkTest`.
13. **Copy haptic default.** Accepted: D8-3's default now keeps today's copy tick (parity); removal is the alternative.
14. **ADR `LocalHapticFeedback` wording.** Accepted: reason stated in §6.2, ADR correction row in §13.2.
15. **Glow `dropShadow` rebuilt every frame.** Accepted (verified in `BlockDropShadowNode` bytecode): a dedicated halo
    node with constant shadow parameters, modulated by `graphicsLayer { alpha }`; first variant in S8-9;
    `HaloPainterReuseTest`.
16. **`getPathForRange` is line boxes.** Accepted: rule 6 of §5.1 (cached rectangles + overhang allowance); italic and
    Arabic captures.
17. **P2-5 cannot move a held row.** Accepted: P2-5 now removes the origin (second client or a `qa` hook); detached and
    off-viewport cases added to `RowTransformGateTest`.
18. **Option B is not a per-file seam.** Accepted: described as an Android-only version constraint plus a gate mode
    flag; S8-1 runs on its own branch and flips only when Nav3 1.3 is stable.
19. **Stop-after-step Undo and menu.** Accepted (inside 30): Undo only with `capabilities.inbox`; the hold opens the
    one-item menu as today, so no §13.1 change is needed.
20. **No M3 side sheet.** Accepted (verified: no `SideSheet` class in 1.5.0-alpha22): `KitSideSheet` on
    `AnchoredDraggableState` + `NavigationBackHandler`, tests, spike S8-10, owner M3.
21. **Sheet predictive scaling under Off.** Accepted: `BottomSheet(backHandlerEnabled = false)` plus the kit's own
    handler (parameter verified in the jar).
22. **Rotation and `configChanges`.** Accepted: `configChanges` is the main mechanism; `hadImeFocus` only for
    recreation; IME-4 tests both.
23. **Index 0 is the spacer.** Accepted: new blocks go in at index 1; `requestScrollToItem(0)` only past the spacer;
    `FollowAnchorTest`.
24. **`navigationevent-testing` version.** Accepted: 1.1.2 (exists on Google Maven), with an ADR row in §13.2.
25. **Journey steps duplicated with section 13.** Accepted: §9.5 keeps only motion gates and trace sections; steps and
    fixtures are PJ-05..PJ-11's.
26. **B-KIT-054's Android clause** (merged with the "Android page transition" issue). Accepted: owner decision D8-5,
    default = today's Android fade-forwards (X14), shared axis X as the alternative; the row is not counted as covered
    by a different transition; ADR §9.4 item 2 correction in §13.2.
27. **M8-9 values not backed.** Accepted: shift tagged [U], 28 dp corners [E], both settled by P2-9; dropping the enter
    scale is stated as deliberate.
28. **Frame-capture harness.** Accepted: desktop Roborazzi for common parts, Robolectric `NATIVE` graphics on the VPS
    for Android seams (§9.4).
29. **Stop design replaced on 2026-10-30.** Accepted: §5.2 rewritten from `KitComposerStop` and `_stopRowChip`
    (chip, spoken name, stuck, working, menu, tip, `KitSwap`, strip `KitReveal`), the "Stop and send" pill, and the ring
    re-scoped to the voice "Stop reading" circle; `KitStopKey` renamed in M5; captures and `ComposerKeysUiTest` fixed;
    09 §10.3 correction row added.
30. **Step light endings and states.** Accepted: §5.7 B is a state table (running, background, stalled, waiting,
    queued, done, failed, stopped) per level, with Signal lamps; tests and captures extended.
31. **Missing loops.** Accepted: `STEP_SWEEP` token, feed-row and step-name sweeps, Signal go-lamp and timer in §2.4,
    §5.15, §7 and the manifest; the invented Signal slip travel dropped (the slip is static today).
32. **Dead glow colour and speed choices.** Accepted (verified in `app_preferences.dart`): one three-way choice; the
    retired keys are deleted at migration; Slow/Fast/Two colours removed; `GlowColoursTest` reduced; migration test.
33. **Haptic call sites missing.** Accepted: §6.2 "When" column lists every Flutter call site (state view, checklist,
    mic hold, voice send, request sheet, choice list, copy, QR lock); "task merged" removed as post-switch;
    `KitHapticsUiTest` extended.
34. **`RevealState` started at 0.** Accepted: §5.1 rules 4–5 (start at `text.length` unless born after the transcript
    was shown; registry outside the lazy items; a refetch keeps `shown`); `RevealInitTest`.
35. **Calm silently redefined.** Accepted: Calm keeps route transitions and the grow from the row (Flutter parity;
    `transforms` flag); §3.3, §5.4, §5.9 and §7 updated; 07 §3.1 correction row added.
36. **Jitter fixes J1–J10.** Accepted: rule R13, no rise or placement for stand-in replacement or the first live
    snapshot, `NoUnrequestedMovementTest`, device checks JT-1..5.
37. **Resolver gives no reason.** Accepted: `MotionResolution(level, reason)`, Motion row notes in English and Arabic,
    `MotionPolicyTest`/`MotionNoteTest` rows.
38. **Micro motions missing from §5.15.** Accepted: rows added with Flutter values (gear turn, "+" turn, Send glyph
    RTL, state view, needs-you, guide, option card, progress ring, image, top bar, session tabs, queued bubble,
    background line, tool row and thought bodies, find band, jump-pill count and twin pill, terminal key caps), M2/M5
    write sets and captures (RTL for the gear and Send glyph).
39. **Stale `oc.effectsHaptics` / `oc.effectsGlass` and wrong-typed celebrations.** Accepted: migration deletes them;
    a future Vibration switch uses a new key; `MotionPrefMigrationTest` cases.
40. **Terminal blink (B-KIT-055).** Accepted (verified: the test covers `StatesTerminalScene`): blink removed from
    `KitTerminalView`; B-KIT-055 and B-KIT-028 deferred under D11.
41. **Rail indicator contradiction** (last item of the "contradictions" issue). Accepted: one spec, section 06's lens
    springs (`SpatialFast` if 06 Q5 flattens them), stated in §2.3 and §5.9.

Nothing was rejected outright. The partial rejections are in item 8. Item 2's edits to section 09, item 26's ADR
wording and the catalog rows are corrections that only the coordinator can merge; they are listed in §13.2.
