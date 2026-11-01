# Motion and native feel in Compose (research for the native rewrite)

Topic: `motion-native`. Written 2026-11-01; every source was read on 2026-11-01, and the full list is at the end.
Scope: Material 3 motion (MotionScheme and springs), predictive back (NavigationEvent, Navigation 3, sheets), shared elements and container transforms and how they clash with predictive back, edge-to-edge, IME animation, overscroll, haptics, reduced motion, and Compose performance for smooth streaming.

Labels follow the sibling file `kmp-cmp-status.md`:

- **[V]** verified on 2026-11-01 from a primary source. That means official docs or release notes, AOSP/androidx source, or the bytecode and `.module` files of the published artifacts in `~/.gradle/caches`.
- **[S]** secondary source.
- **[U]** unverified, my recollection or projection. Check it in the first spike.

---

## 0. Bottom line

1. **The predictive back vs container transform clash has a real fix, but it is not stable yet.** [V]
   - The demo's clash has a root cause. Nav2 and Nav3 1.2 *scrub the pop transition with the finger*, so a "shrink into the row" `sharedBounds` gets dragged by the gesture.
   - Google's 2024 design guidance says the 90 % preview "doesn't work with … Navigation Compose" and to run the shared element only after the user commits.
   - Compose 1.12 (Aug 2026) added **deferred two-stage transitions**: `DeferredTransitionState.defer()`, `DeferredAnimatedContent`, `mutableTransformSpec`, and `SharedContentConfig.permitTransformDuringDeferredTransition`. In the first stage the page follows the finger (preview). On release, the real transition takes over with velocity handoff.
   - Navigation3 **1.3.0-alpha01** (2026-09-23) moved `NavDisplay` onto `DeferredAnimatedContent`. The deferred APIs drop their experimental opt-in in Compose **1.13** (beta02, 2026-10-21).
   - **Plan:** ship first with the proven fallback (§4.4 A, the demo/Flutter approach without the 700 ms latch). In parallel, run a one-agent spike on Nav3 1.3-alpha + Compose 1.13-beta (§4.4 B). Switch when both are stable.
2. **Use the *standard* motion scheme, never expressive.** [V]
   - Material's expressive springs overshoot: 9.5 % on fast spatial (damping 0.6) and 1.5 % on default. That breaks the owner's "nothing bouncy" rule.
   - The standard scheme (damping 0.9) settles in about 126 / 178 / 271 ms (fast/default/slow spatial) with 0.2 % overshoot. That is the owner's 150–250 ms band, computed in §2.
   - The demo's `Motion.bouncy()` (0.62/600, 8.4 % overshoot) must not survive into the kit.
3. **On Android, the CMP UI *is* Jetpack Compose.** [V]
   - Checked locally: the demo's `org.jetbrains.compose.material3:material3:1.12.0-alpha03` resolved to `androidx.compose.material3:material3-android:1.5.0-alpha22`, and `animation-android` to `1.12.1`.
   - So ripple, stretch overscroll, predictive back, IME animation and haptics are Google's Android code. Writing the kit in `commonMain` costs no smoothness.
4. **Reduced motion is mostly free on Android.** [V]
   - Compose reads `Settings.Global.ANIMATOR_DURATION_SCALE` through a `ContentObserver` and installs it as `MotionDurationScale` in the recomposer context (`WindowRecomposer.android.kt`). At scale 0, animations end on the next frame.
   - The kit still needs its own policy for Full/Calm/Off, Saver, heat and loops. Keep it as pure Kotlin with unit tests.
5. **Edge-to-edge is mandatory and predictive back is on by default.** [V]
   - Edge-to-edge cannot be opted out of at targetSdk 36.
   - Predictive back system animations are on by default at targetSdk 36. `onBackPressed` is not called and `KEYCODE_BACK` is not dispatched.
   - Never intercept back on the root screen, or back-to-home loses its animation.
6. **Smooth streaming is a data-shape problem more than an animation problem.** [V for APIs, U for numbers]
   - Coalesce deltas to one publish per frame.
   - Split replies into keyed block items so only the open tail block re-lays out.
   - Animate words in with a paint-only mask.
   - Read every animated value in the draw phase (`graphicsLayer {}` / `drawBehind {}`).
   - Ship a baseline profile.
7. **Native parts to use as they are:**
   - Ripple: Compose's Android ripple is a platform `RippleDrawable` subclass [V, bytecode].
   - Stretch overscroll on API 31+ and glow on 26–30 (minSdk 26).
   - Platform fling.
   - Haptics through `LocalHapticFeedback` / `View.performHapticFeedback`: no VIBRATE permission, and they respect user settings [V].
   - In-window `BottomSheet` (M3 1.5) with built-in predictive back.

---

## 1. Version snapshot (as of 2026-11-01)

| Library | Stable | Pre-release | Motion-relevant notes |
|---|---|---|---|
| Compose ui/foundation/animation/runtime | **1.12.1** (BOM `2026.09.00`) [V] | 1.13.0-beta02 (2026-10-21) [V] | 1.12: deferred transitions; compileSdk 37 and AGP ≥ 9.1.1 [V]. 1.13: deferred APIs non-experimental; compileSdk 37.1 [V] |
| Compose material3 | 1.4.0 (2025-09-24) [V] | 1.5.0-beta02 (2026-10-21) [V] | 1.4.0 removed every Expressive API. `MotionScheme` non-experimental in 1.5.0-alpha15. `MaterialExpressiveTheme` promoted in alpha18. `LocalMotionScheme` removed in alpha27 [V] |
| Navigation3 | **1.2.0** (2026-09-23) [V] | 1.3.0-alpha03 (2026-10-21) [V] | 1.2: `NavigationBackHandler`, mid-flight predictive back. 1.3.0-alpha01: `NavDisplay` on `DeferredAnimatedContent` [V] |
| NavigationEvent | **1.2.0** (2026-10-07) [V] | none | `NavigationBackHandler`, `rememberNavigationEventState`, `transitionState` Idle/InProgress; KMP Compose since 1.1.0 [V] |
| Activity | 1.13.0 (2026-03-11) [V] | 1.14.0-alpha03 | 1.12+: `ComponentActivity` is a `NavigationEventDispatcherOwner`. 1.13: edge-to-edge re-invoked on config change. 1.14-alpha: `WindowCompat.enableEdgeToEdge(window)` [V] |
| CMP (JetBrains) | 1.12.2 (2026-10-15) → Jetpack 1.12.1; material3 `1.12.0-alpha03` = Jetpack M3 1.5.0-alpha22; nav3 `1.2.0-rc01`; navigationevent `1.1.0` [V] | 1.13.0-beta01 (2026-10-23): material3 1.13.0-beta01, nav3 1.2.0 [V] | 1.12/1.13 fixed iOS back-swipe bugs; 1.13.0-alpha01 makes Nav3 default push/pop follow layout direction on iOS [V] |
| Kotlin / Compose compiler | demo uses 2.4.0; sibling file pins 2.4.21 | n/a | Strong skipping on by default since 2.0.20 [V] |

The sibling `kmp-cmp-status.md` owns the toolchain pins. Nothing here contradicts it. This file adds the motion consequences.

---

## 2. Motion system: MotionScheme, springs, tokens

### 2.1 What Material ships [V]

- `MotionScheme` (interface, `@Immutable`) has six specs: `defaultSpatialSpec`, `fastSpatialSpec`, `slowSpatialSpec`, `defaultEffectsSpec`, `fastEffectsSpec` and `slowEffectsSpec`.
  - Spatial specs are for position, size and shape.
  - Effects specs are for alpha and colour, and never overshoot.
- `MotionScheme.standard()` and `MotionScheme.expressive()` are factories. `MaterialTheme(motionScheme = …)` is public in the 1.5 line, and components read it through `MaterialTheme.motionScheme`. Since 1.5.0-alpha16, bottom sheets respect it during drag and nested scroll.
- `MaterialExpressiveTheme` defaults to `expressive()`.

Token values come from the androidx-main source (`StandardMotionTokens.kt`, `ExpressiveMotionTokens.kt`). The settle times and overshoot are **my computation**: a unit-mass spring simulated to within 2 % of target; overshoot is `exp(-ζπ/√(1-ζ²))`.

| Spec | Standard (ζ / k) | ≈ settle, overshoot | Expressive (ζ / k) | ≈ settle, overshoot |
|---|---|---|---|---|
| fastSpatial | 0.9 / 1400 | 126 ms, 0.2 % | 0.6 / 800 | 210 ms, **9.5 %** |
| defaultSpatial | 0.9 / 700 | 178 ms, 0.2 % | 0.8 / 380 | 193 ms, 1.5 % |
| slowSpatial | 0.9 / 300 | 271 ms, 0.2 % | 0.8 / 200 | 266 ms, 1.5 % |
| fastEffects | 1.0 / 3800 | 95 ms, 0 | same | same |
| defaultEffects | 1.0 / 1600 | 146 ms, 0 | same | same |
| slowEffects | 1.0 / 800 | 206 ms, 0 | same | same |
| demo `spatial()` | 0.86 / 520 | 188 ms, 0.5 % | n/a | n/a |
| demo `bouncy()` | 0.62 / 600 | 244 ms, **8.4 %** | n/a | n/a |

### 2.2 Recommendation for this app

- **Kit motion = the standard scheme for spatial, plus fixed-duration tweens for fades and colour.** This keeps the Flutter kit's vocabulary that the owner has approved (`KitMotion.quick` 150 ms, `standard` 250 ms, `rowReturn` 300 ms; exits faster than entrances; `easeOutCubic` in, `easeInCubic` out).
  - Compose has `EaseOutCubic`, `EaseInCubic` and `EaseInOutCubic` in `androidx.compose.animation.core` [V, bytecode 1.12.1].
- **Use springs for anything a finger drives or can interrupt:** sheets, predictive-back cancel, drag release, list placement, the container transform. Springs carry velocity on interruption and tweens do not.
- Pass the kit scheme into `MaterialTheme(motionScheme = KitMotionScheme)`, so that the few M3 parts the kit wraps (sheet, switch, pull-to-refresh) move like the rest.
- Only `:ui:kit` touches M3. That rule is already in the sibling file.

```kotlin
// commonMain, :ui:kit  (sketch)
object KitMotion {
    const val QUICK = 150; const val STANDARD = 250; const val ROW_RETURN = 300
    fun <T> quick(): FiniteAnimationSpec<T> = tween(QUICK, easing = EaseOutCubic)
    fun <T> standard(): FiniteAnimationSpec<T> = tween(STANDARD, easing = EaseOutCubic)
    fun <T> exit(): FiniteAnimationSpec<T> = tween(100, easing = EaseInCubic)   // exits faster
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(0.9f, 1400f)          // = standard tokens
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(0.9f, 700f)
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(0.9f, 300f)
}

/** Same numbers as MotionScheme.standard(); Off swaps every spec for snap(). */
class KitMotionScheme(private val off: Boolean) : MotionScheme {
    override fun <T> defaultSpatialSpec() = if (off) snap() else KitMotion.spatial<T>()
    override fun <T> fastSpatialSpec()    = if (off) snap() else KitMotion.spatialFast<T>()
    override fun <T> slowSpatialSpec()    = if (off) snap() else KitMotion.spatialSlow<T>()
    override fun <T> defaultEffectsSpec() = if (off) snap() else spring<T>(1f, 1600f)
    override fun <T> fastEffectsSpec()    = if (off) snap() else spring<T>(1f, 3800f)
    override fun <T> slowEffectsSpec()    = if (off) snap() else spring<T>(1f, 800f)
}
```

`snap()` is a `DurationBasedAnimationSpec`, so it satisfies `FiniteAnimationSpec` [U: compile check]. Whether `MotionScheme` is an interface you may implement outside Material is shown as public `@Immutable` in the CMP 1.10 docs [V]. Confirm it is not sealed in 1.5 [U].

Two more mapping rules:

- Keep the Flutter kit's three switches as kit API: `reduced`, `travels` (motion level Full only) and `loopsIn`. Also keep the rules MOT-1/2/5/6/7/13/15 (ui-kit map §07). They port almost one-to-one.
  - "No scale or fade-scale in transitions" (MOT-2) has the named exceptions the owner approved: the sent bubble lifts from 0.92, and the chat grows from its row.
  - In Compose, give scaled text `TextStyle(textMotion = TextMotion.Animated)` so glyphs don't jitter while scaling [U: name and behaviour from memory].
- Under motion level Full, the composer's green breathing glow (owner-liked) is an `rememberInfiniteTransition` whose value is read **only inside `drawBehind`/`drawWithCache`**. Then it never recomposes the composer, and MOT-15 ("only one thing moves while a reply runs") holds.

---

## 3. Predictive back

### 3.1 Platform facts [V]

- **Android 15+:** back-to-home, cross-activity and cross-task animations are on by default for apps that use the supported back APIs. The developer option is gone.
- **Android 16, targetSdk 36:**
  - Predictive back system animations are on by default.
  - **`onBackPressed` is not called and `KEYCODE_BACK` is not dispatched.**
  - `android:enableOnBackInvokedCallback="false"` is only a temporary opt-out.
  - Android 16 also brought predictive back to **3-button navigation**: a long press on back shows the preview [S, Android Authority]. A developer reports that long-press back no longer reaches apps [S].
- The system animations run only when no `PRIORITY_DEFAULT`/`PRIORITY_OVERLAY` callback is enabled. If you intercept back at the root activity, you lose back-to-home.
  - For logging without consuming the event: Compose → `ViewModel.onCleared()`; Android 16+ → `PRIORITY_SYSTEM_NAVIGATION_OBSERVER`.
- **Material preview values** (developer.android.com predictive-back design page, last updated 2024-10-24):

  | Transition | Values |
  |---|---|
  | Full-screen | exit scale 100 → 90 %; enter scale 110 → 100 %; fade-through at 35 % progress; interpolator `(.1, .1, 0, 1)`; commit = fade-through; cancel = spring back |
  | Shared-element preview | x-shift up to `(width/20 − 8) dp`; y-shift `(height/20 − 8) dp` on a decelerate curve; min scale 90 %; 8 dp edge gap; never feed linear progress directly |

  The same page: *"the shared element transition doesn't work with … Navigation Compose. In those cases, don't scale the screen to 90%. Run the shared element transition as the user navigates back instead."* That is exactly the demo's problem.
- **Android 17** (behavior pages updated 2026-10-28/31) lists **no** predictive back, edge-to-edge, haptics or motion changes.
  - It does list "**IME visibility is no longer restored after rotation**" when the app doesn't handle the configuration change. That matters for the composer (§6).
  - At targetSdk 37, sw600dp orientation and resizability restrictions are always ignored, so rotation and recreation paths must be smooth.

### 3.2 APIs [V]

- **NavigationEvent** (`androidx.navigationevent`, KMP):
  - `NavigationBackHandler(state, isBackEnabled, onBackCancelled, onBackCompleted)` hooks into the nearest `LocalNavigationEventDispatcherOwner`.
  - `rememberNavigationEventState(...)`; `dispatcher.transitionState` is `Idle` or `InProgress(latestEvent)`; events carry `progress` and `swipeEdge`.
  - `toBackEvent()` and `toNavigationEvent()` convert to and from Android's `BackEvent`.
  - Activity 1.12+ rebuilt `OnBackPressedDispatcher` on top of it.
- **activity-compose `PredictiveBackHandler(enabled) { progress: Flow<BackEventCompat> -> }`** is still documented on developer.android.com (updated 2026-10-28). "Last composed wins"; compose it unconditionally and toggle `enabled`.
  - The sibling file reports that CMP's common `PredictiveBackHandler` is deprecated in favour of `NavigationBackHandler`. **Use `NavigationBackHandler` in kit code** so it is KMP-clean.
- **Navigation3 `NavDisplay`** takes `transitionSpec`, `popTransitionSpec` and `predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.(@SwipeEdge Int) -> ContentTransform`, plus per-entry overrides through metadata.
  - Android defaults (androidx-main `NavDisplay.android.kt`): push and pop are a plain cross-fade. The predictive default is `fadeIn(spring(1f, 1600f))` + `scaleOut(0.7f)`.
  - The app should set its own specs: the Material values above and the kit tokens.
- **Navigation 2** (`navigation-compose` ≥ 2.8, which the demo used): the gesture **seeks** the `popExitTransition`/`popEnterTransition` pair. Anything inside the pop transition, including `sharedBounds`, is scrubbed by the finger.

### 3.3 Sheets, drawers, search [V]

- Material components with built-in predictive back: `ModalBottomSheet`, `SearchBar`, and the modal and dismissible navigation drawers and sheets.
- `ModalBottomSheet` (still `@ExperimentalMaterial3Api` in androidx-main) renders in a **dialog-like separate window** (`ModalBottomSheetDialog`). The shared element APIs explicitly don't work across `Dialog`/`ModalBottomSheet`, and the sheet gets its own insets.
- **M3 1.5 adds a public in-window `BottomSheet`** (`@ExperimentalMaterial3Api`; "renders directly in the composition hierarchy", no scrim):
  - Its back handling is a `PredictiveBackHandler` on `backHandlerEnabled && state.isVisible`.
  - The preview scales the surface by up to 48 dp in x and 24 dp in y, keeping the content's aspect ratio.
  - `rememberBottomSheetState` replaces the two old state factories (1.5.0-alpha20). That alpha also lets the drawer go back while it is still opening.

### 3.4 Recommendation

- **Nav3 1.2 now; Nav3 1.3 once stable**, for the deferred handoff (§4).
- **No back interception on the root (Conversations) screen**, so back-to-home animates.
- Never block back with a "Discard draft?" dialog. Drafts autosave (the owner's "steer, never queue" spirit), so back always previews and commits.
- Set `predictivePopTransitionSpec` to the Material full-screen preview:
  - target scale 0.9;
  - fade-through at 35 %;
  - shift **away from `swipeEdge`**, not by layout direction (Arabic RTL: either edge can start the gesture).
- **Kit sheets:** one `KitSheetHost` at the app root renders the in-window M3 `BottomSheet` plus a kit scrim (opaque surfaces, no glass).
  - What this buys: one window means one inset model and IME handling, and shared elements work. Predictive back is built in.
  - It sits above `NavDisplay` and owns back while visible. Its `NavigationBackHandler` composes later, so it wins.
  - Keep `ModalBottomSheet` only as a fallback behind the same kit API. The API is experimental either way, and the kit absorbs the churn.
- **Tests (logic now, UI later per the owner):** the back-dispatch *policy* is plain Kotlin and unit-testable:
  - which layer owns back: sheet > selection mode > find bar > page > root;
  - the root never consumes back;
  - drafts are saved on pop.

---

## 4. Shared elements, container transforms, and the predictive-back clash

### 4.1 Status [V]

- Shared transition APIs became **stable in Compose 1.10** (Dec 2025).
  - 1.10 added: dynamic enable/disable (`SharedContentConfig.isEnabled(state)`), initial velocity from a fling, alternative target bounds when the target is removed mid-transition, and `skipToLookaheadPosition`.
  - 1.11 fixed `isTransitionActive` so it is true only for matched elements, and fixed several crashes.
- **Compose 1.12.1 (stable) contains `DeferredAnimatedContent`, `DeferredAnimatedVisibility`, `DeferredTransitionState` (`defer`, `animateTo`) and `SharedContentConfig.permitTransformDuringDeferredTransition`.**
  - Checked by `javap` on the cached 1.12.1 AARs.
  - In 1.12 they are behind `ExperimentalDeferredTransitionApi`. That opt-in was removed in 1.13.0-alpha03 (2026-09-09).
- `ExperimentalSharedTransitionApi` still exists in 1.12.1 for the newer extras.
- **Navigation3 1.3.0-alpha01** (2026-09-23): "Fixed predictive back animation handoff in `NavDisplay` by migrating to `DeferredAnimatedContent`."
  - androidx-main `NavDisplay.kt` now calls `transitionState.defer(previousScene)` while `navigationEventState.transitionState is InProgress`, and `animateTo` otherwise.
  - During the gesture, the predictive spec is converted into a mutable transform driven by gesture progress.

### 4.2 Why the demo fought, and why the Flutter app did the same thing

- With seeking (Nav2, and Nav3 ≤ 1.2), the pop transition is the gesture. A container transform shrinking the chat into its row is scrubbed by the finger. On cancel it re-grows, and on commit it jumps.
- The demo's fix:
  - watch `NavigationEventTransitionState.InProgress`;
  - turn off `renderInOverlayDuringTransition` and the transform;
  - **latch for 700 ms** so a committed pop doesn't switch mid-way.
- The Flutter kit hands over to the system builder while `popGestureInProgress` (ui-kit map §08).
- Both are the same idea. The 700 ms latch is a timing guess and should go.

### 4.3 Container transform done right (row → chat, chat → row)

Notes for the kit agent:

- `Modifier.sharedBounds(rememberSharedContentState(ChatKey(sessionId)), animatedVisibilityScope = LocalNavAnimatedContentScope.current, …)` goes on the row's surface and on the chat page's root surface.
  - Keys are **data classes, not strings** (official best practice).
  - `boundsTransform = { _, _ -> KitMotion.spatial() }`.
  - `clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(animatedCorner))`. Shape morphing is **not** automatic, so animate the corner radius yourself.
- Put **`Modifier.skipToLookaheadSize()` on the chat content** (transcript and composer).
  - The page then lays out once at full size and the growing bounds reveal it: no per-frame remeasure of a long `LazyColumn`.
  - Or use `ResizeMode.scaleToBounds()`, which scales instead of re-laying out. Both are named as the performance path in the docs.
- **The list stays still under the opening chat** (owner/Flutter decision). The list entry's exit is effectively "keep until finished" with no movement.
  - Freeze list reordering while `SharedTransitionScope.isTransitionActive`. This is the Flutter fix `f7e775dd3`: "the list holds still until a chat has landed in its row". Apply queued reorders after it settles.
- **Row gone or scrolled off:** use `SharedContentConfig.alternativeTargetBoundsInTransitionScopeAfterRemoval`, or let the row-return fall back to the plain pop. Never animate toward stale bounds.
- **Reduced motion:** no container transform. Use a plain cross-fade at Calm, and instant at Off.
- **Limits (docs):** no View or `Dialog` interop, `ContentScale` is not animated, and shapes are not morphed.

### 4.4 Predictive back plus container transform: two options

**A. Ship first (stable APIs: Compose 1.12.1, Nav3 1.2.0).** [V APIs; U exact hook]

- Forward navigation and button-back use the container transform (`rowReturn` 300 ms).
- While `transitionState is InProgress`:
  - `SharedContentConfig.isEnabled` returns **false** for the chat key (dynamic enabling, 1.10);
  - `predictivePopTransitionSpec` = the Material full-screen preview (scale to 0.9, fade-through at 35 %).
- Replace the demo's 700 ms latch with state:
  - remember `poppedByGesture = true` when the gesture *commits* (`onBackCompleted`);
  - clear it on the transition's settle (`transition.isRunning == false` / `!isTransitionActive`), not on a timer.

```kotlin
// sketch; hook names checked against 1.12.1/NavEvent 1.2 bytecode, wiring is [U]
val gesture by dispatcher.transitionState.collectAsState()
val inGesture = gesture is NavigationEventTransitionState.InProgress
val chatBoundsConfig = remember {
    object : SharedTransitionScope.SharedContentConfig {
        override fun isEnabled(state: SharedTransitionScope.SharedContentState) =
            !inGestureRef.value && !poppedByGestureRef.value
    }
}
```

**B. Target (Compose 1.13 + Nav3 1.3, both pre-release today).** [V that APIs exist; U that it gives a correct row-return]

- During the gesture, `NavDisplay` defers the pop. The chat page follows the finger through the predictive spec as a **mutable transform**: scale toward 0.9, shift away from the swipe edge, corners rounding.
- Set `permitTransformDuringDeferredTransition = true` so the shared bounds ride along with the page.
- On release, `animateTo(previousScene)` hands over with velocity, and the shared bounds animate from the *previewed* rectangle into the row. That is the real "shrink back into its row" under a gesture.
- On cancel, it springs back.
- **Spike acceptance** (emulator first, then owner phone):
  - (1) slow drag + commit lands in the row with no jump;
  - (2) cancel returns without flicker;
  - (3) a fling commit carries velocity;
  - (4) RTL from both edges;
  - (5) the row is off-screen;
  - (6) reduced motion = plain pop.
  - If any of these fail, stay on A.

```kotlin
// Compose 1.12 blog sample shape (verbatim API names)
val state = remember { DeferredTransitionState(initialScreen) }
val transition = rememberDeferredTransition(state)
if (predictiveBackInProgress) state.defer(targetScreen) else state.animateTo(targetScreen)
transition.DeferredAnimatedContent(
    mutableTransformSpec = {
        MutableContentTransform { initialContentTransform { scale = lerp(1f, 0.9f, easedProgress) } }
    },
) { screen -> ScreenContent(screen) }
```

### 4.5 Other owner-approved chat motions, in Compose terms

- **Sent message lifts from the input** (from 0.92): paint-only. Record the field's bounds (`onGloballyPositioned`) before sending. The new bubble starts with `graphicsLayer { translationY = Δ; scaleX = scaleY = 0.92f; alpha }` and springs to identity with `KitMotion.spatial()`.
  - This is cheaper and simpler than a shared element in the same screen, and the same technique as the Flutter `KitMessageLift`.
- **Steps slide in, groups fold:** use `AnimatedVisibility(expandVertically + fadeIn)` inside **one** lazy item. This is the single allowed layout animation (`KitReveal`). Use `Modifier.animateItem(fadeInSpec, placementSpec)` for steps arriving as new items.
- **Words fade in:** see §9.3. It is paint-only and not a layout animation.

---

## 5. Edge-to-edge and system bars [V]

- targetSdk 35 enforces edge-to-edge, and targetSdk 36 removes `windowOptOutEdgeToEdgeEnforcement`.
- Call `enableEdgeToEdge()` in `onCreate`. With activity 1.14 (alpha) it becomes `WindowCompat.enableEdgeToEdge(window)`.
  - Defaults: transparent bars, plus a translucent scrim on 3-button navigation.
  - Activity 1.13 re-invokes it on configuration change.
- **The app's own light/dark choice is not a configuration change.** Re-apply bar icon colours from the kit theme whenever the theme changes (`WindowCompat.getInsetsController(...).isAppearanceLightStatusBars`).
  - That is owner rule R4, "system bars from the theme".
- Where the opaque composer or dock sits under a 3-button bar, set `window.isNavigationBarContrastEnforced = false`. The composer's surface is the protection, so no grey scrim is drawn over it.
- Insets are read one frame late in composition; the built-in modifiers apply them in layout. **Use the modifiers, never `WindowInsets.ime.getBottom(density)` in composition.**
- `WindowInsetsRulers` + `Modifier.fitInside(...)` (present in ui/foundation 1.12.1 [V]) position floating parts by absolute rulers.
  - They are useful for the jump pill, undo bar and tips over the composer when an ancestor consumed insets.
  - They are not usable for measurement, and are risky inside `verticalScroll`.
- The widget overlay activities (steer box, style picker; `WidgetOverlayTheme`) get the same edge-to-edge and IME handling.
- Desktop windowing has a caption bar. Use `WindowInsets.systemBars`, which includes `captionBar`, for the top bar on tablets and PC.

---

## 6. IME animation [V unless marked]

- Keep `android:windowSoftInputMode="adjustResize"` (the Flutter manifest already has it). It is required for IME insets.
- **Composer:** use `Modifier.imePadding()` after `navigationBarsPadding()`. Consumption makes them combine correctly (`max(ime, navBar)`, not the sum).
  - The composer then rides the keyboard **frame by frame**. Compose drives `WindowInsets.ime` from `WindowInsetsAnimation` on API 30+ [U: older APIs may jump; minSdk is 26].
- **Transcript:** a `LazyColumn` with `reverseLayout = true` keeps the newest message pinned to the bottom edge as the viewport shrinks or grows with the keyboard. That is the native chat feel.
  - Bottom clearance for the composer: use a measured `Spacer` item or padding fed by the composer's measured height (the `KitBottomInset` idea).
  - The docs warn: use a `Spacer`, not `contentPadding`, for the last field above the IME.
- `Modifier.imeNestedScroll()` lets you drag the transcript to pull or push the keyboard (Google-Messages-style).
  - It is still `@ExperimentalLayoutApi` in 1.12.1 [V, bytecode].
  - It is a hidden gesture. If enabled, it needs a `KitTip` (owner rule "teach hidden gestures"), and tapping outside or back still closes the keyboard.
  - Spike it on the nubia before committing.
- `WindowInsets.imeAnimationSource/Target` and `isImeVisible` exist with experimental annotations [V, bytecode]. Use them only for "where will the keyboard end", for example to pre-scroll.
- **Android 17:** after an unhandled rotation, the IME is not re-shown.
  - Either request it again in the chat screen when the composer had focus and a draft (saved state: `hadImeFocus`), or handle orientation config changes in Compose.
  - Pick one in the architecture slice [U: which the sibling architecture file prefers].
- Back with the keyboard open closes the keyboard first (system). Don't add handlers for it.

```kotlin
// chat screen skeleton (sketch)
Box(Modifier.fillMaxSize()) {
    LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize()) {
        item(key = "clearance") { Spacer(Modifier.height(composerHeight)) }   // measured, not contentPadding
        items(blocks, key = { it.id }, contentType = { it.type }) { KitBlock(it) }
    }
    KitComposer(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
        .onSizeChanged { composerHeight = with(density) { it.height.toDp() } })
}
```

---

## 7. Overscroll, fling, ripple, press [V unless marked]

- **Stretch overscroll** is the Android 12+ (API 31) platform effect. The platform factory uses glow on older versions, so this app's minSdk 26 shows glow on Android 8–11.
  - Compose 1.12.1 ships `rememberOverscrollEffect()`, `Modifier.overscroll()`, `OverscrollEffect.withoutVisualEffect()`/`withoutEventHandling()` and `LocalOverscrollFactory` [V, bytecode].
  - **Never replace `LocalOverscrollFactory` app-wide.** Use `withoutVisualEffect()` only on an inner horizontal scroller (code block, diff) if a double stretch shows. This mirrors Flutter's "`KitScrollBehavior` overrides neither physics nor overscroll".
- **Fling:** leave `ScrollableDefaults.flingBehavior()` alone. It uses the platform spline decay, which is the same feel as Views [U: from memory, standard knowledge].
- **Ripple:** Compose's Android ripple is `UnprojectedRipple extends android.graphics.drawable.RippleDrawable`, hosted in `RippleHostView` [V, javap on material-ripple-android]. It is the platform ripple, including the platform's API 31+ style [U on sparkle specifics].
  - Bound it to the part's shape (`clip(shape)` before `clickable`, or `ripple(bounded = true)`).
  - The Flutter "12 % of the part's ink" is a ripple colour/alpha choice. Use `RippleConfiguration`/`LocalRippleConfiguration` in M3 [U: exact 1.5 API name].
  - Keep the Flutter lessons: the minimum visible press time (`pressHold` 100 ms) for custom pointer code, and Send acting on the pointer's lift.
- **iOS and desktop later:** a ripple is wrong on iOS. Make press feedback a kit `expect`: ripple on Android, highlight/dim on iOS, hover fill + focus ring on desktop.

---

## 8. Haptics [V unless marked]

- Compose `LocalHapticFeedback.performHapticFeedback(HapticFeedbackType.X)` has 13 types (androidx-main source): `Confirm`, `ContextClick`, `GestureEnd`, `GestureThresholdActivate`, `KeyboardTap`, `LongPress`, `Reject`, `SegmentFrequentTick`, `SegmentTick`, `TextHandleMove`, `ToggleOff`, `ToggleOn` and `VirtualKey`.
- Android guidance (updated 2026-10-26):
  - Prefer `View.performHapticFeedback` constants. They are action-oriented, have the widest support, need no VIBRATE permission, fall back per device, and **respect the user's touch-feedback setting**.
  - Avoid `createOneShot`/`createWaveform` ("often too loud").
  - Richer `VibrationEffect.Builder` composition and envelope effects (`BasicEnvelopeBuilder`, `areEnvelopeEffectsSupported()`) are new platform APIs labelled "Android 16 (26Q4)". Don't build on them.
- **Mapping the Flutter `KitHaptics` events** (MOT-11: never on scroll, plain taps, failures, copy, sheets or dialogs; obey Settings → Vibration and the system):

| Kit event (Flutter) | Meaning | Compose type (proposal) |
|---|---|---|
| `send` | the person's words leave | `Confirm`, or `VirtualKey` if Confirm feels too strong on the nubia [U: tune on device] |
| `done` | a waited-for finish, once; skipped under reduced motion | `Confirm` |
| `commit` | a confirmed stop/destructive/discard act | `LongPress` (firm) or `ContextClick` [U: tune] |
| `longPress` | long-press opens a menu or selection | `LongPress` |
| `select` | switch flips, segment chosen, wheel/step passes a value | `ToggleOn`/`ToggleOff` for switches; `SegmentTick` for segments, steps and wheels |
| (new, optional) | swipe action crosses its threshold | `GestureThresholdActivate`. Not in MOT-11 today, so it is an owner decision |

- Text-handle haptics come with `BasicTextField`. Don't add more.
- Notification-arrival haptics belong to the notification channel, not the UI.
- iOS later: `UIImpactFeedbackGenerator`/`UISelectionFeedbackGenerator` behind the same kit enum. How CMP's iOS `LocalHapticFeedback` maps these is [U]. Desktop: no-op.

---

## 9. Reduced motion, motion levels, and performance for smooth streaming

### 9.1 Reduced motion [V unless marked]

- Android: `WindowRecomposer.android.kt` reads `Settings.Global.ANIMATOR_DURATION_SCALE`, observes it with a `ContentObserver`, and adds `MotionDurationScaleImpl` to the recomposer's coroutine context.
  - Every Compose animation run in composition-derived scopes (`animate*AsState`, `Animatable` in `LaunchedEffect`/`rememberCoroutineScope`, transitions) is scaled. **At 0 it ends on the next frame.**
  - `InfiniteTransition` also consults the duration scale [V, bytecode]. Its exact behaviour at 0 (freeze at start or end value) is [U].
- **The demo's bug:** `AndroidPlatform.reduceMotion` reads the setting once and only treats `== 0f` as reduced. Compose already follows live changes; the kit's own `reduced` flag must too.
- **Kit `MotionPolicy` (pure Kotlin, `commonMain`, unit-tested now):**

```kotlin
enum class MotionLevel { Full, Calm, Off }
data class MotionSignals(
    val systemScale: Float,          // Android ANIMATOR_DURATION_SCALE; iOS isReduceMotionEnabled → 0f
    val userLevel: MotionLevel,      // Settings › Appearance › Motion (oc.effectsMotion)
    val saver: Boolean,              // app Saver or PowerManager.isPowerSaveMode
    val severeHeat: Boolean,         // PowerManager thermal status >= SEVERE (MOT-13)
)
fun resolve(s: MotionSignals): MotionLevel = when {
    s.systemScale == 0f || s.userLevel == MotionLevel.Off -> MotionLevel.Off
    s.saver -> MotionLevel.Off            // Flutter underSaver(): motion off
    s.severeHeat || s.userLevel == MotionLevel.Calm -> MotionLevel.Calm
    else -> MotionLevel.Full
}
// reduced = level == Off; travels = level == Full; loopsIn = loops && level == Full
```

- The catalog already holds the source tests to port first:
  - `test/appearance_effects_test.dart:94`: motion on by default, choice persists, old installs map.
  - `test/appearance_final_test.dart:71`: Saver turns motion off without touching stored choices.
  - `test/app_preferences_store_test.dart:117`: celebration switches fold into Motion.
  - The motion-parts and reduced-motion widget tests come later, with the widget batch.
- **In-app Off while system animations are on:** Compose doesn't know about it. The kit handles it in three places:
  - `KitMotionScheme(off = true)` (snap) for M3 parts;
  - `EnterTransition.None`/`ExitTransition.None` for `NavDisplay` specs;
  - kit parts check `reduced`.
  - A custom `MotionDurationScale` for the whole composition via a custom `Recomposer` might do it in one place, but it is [U] and heavier. Not recommended for slice 0.
- **Signals to observe live:**
  - `ContentObserver` on the animator scale URI;
  - `ACTION_POWER_SAVE_MODE_CHANGED`;
  - `PowerManager.addThermalStatusListener`;
  - iOS `UIAccessibility.isReduceMotionEnabled` plus its notification (iOS 8+ API [V, Apple docs]);
  - Linux desktop: none built in (GNOME `enable-animations` via gsettings is [U]).
- **Compose UI tests later:** `runComposeUiTest(effectContext = <MotionDurationScale>)` drives both reduced paths. The ported Flutter rule "settles after one pump under reduced motion" becomes "idle after one frame with scale 0".

### 9.2 Compose performance rules for the kit and chat [V unless marked]

- **Strong skipping is on by default** (Kotlin ≥ 2.0.20):
  - unstable params are compared by `===`, stable ones by `equals`;
  - lambdas are auto-remembered;
  - `@NonSkippableComposable` and `@DontMemoize` opt out.
- **2026 guidance changed: don't reach for `ImmutableList` by default.** With strong skipping, a plain `List` skips by reference. `ImmutableList` forces element-by-element `equals` on every pass, which is costly in lazy layouts.
  - **Preserve instances** instead: cache transformations in the state holder; closed message blocks are immutable objects reused across emissions.
  - Use `@Immutable`/`@Stable` only on small classes with cheap `equals`.
- **Stability config file** for types you don't own (`java.time.*`, the protocol models module):
  - `composeCompiler { stabilityConfigurationFile = … }` per docs (singular property on the page). The plural `stabilityConfigurationFiles` also exists in recent Kotlin Gradle plugins [U: confirm on 2.4.21].
  - Enable the Compose compiler on the data/model modules, or wrap their types in UI models.
  - Turn on `reportsDestination` in CI for `:ui:kit` and the chat module to catch regressions in unstable params.
- **Defer reads to layout/draw:**
  - `Modifier.graphicsLayer { alpha = a.value }` (lambda form), `offset { }`, `drawBehind { }`, `drawWithCache { }`;
  - `derivedStateOf` for "is at bottom" and "show jump pill" from `LazyListState`.
- **Lazy lists:**
  - `key` (stable id) and `contentType` (text, code, tool row, diff, image, notice) on every item;
  - `Modifier.animateItem()` only for arrive/leave/reorder;
  - no `animateContentSize` on list items except the one fold (MOT-5).
  - Pausable composition in lazy prefetch is **on by default since Compose 1.10**. `LazyLayoutCacheWindow` (1.9) can prefetch more if profiling shows a need.
- **Effects:** `SideEffect(key…)` is up to 90 % faster than `LaunchedEffect` (Compose 1.12 blog). Use it for fire-and-forget tracking. It runs earlier than the others.
- **Frame rate:** `Modifier.preferredFrameRate(Float | FrameRateCategory)` exists in ui 1.12.1 [V, bytecode].
  - Owner rule: never below 60 fps.
  - Use it only if the nubia's adaptive refresh visibly drops the streaming transcript or glow below 60. Measure first with `dumpsys gfxinfo <pkg> framestats`, which is allowed on the owner's phone.
- **Baseline + startup profiles:** about 30 % faster code from first launch; startup profiles add about 15 % more (AGP 8.2+); profiles must be under 1.5 MB [V].
  - Generate them with Macrobenchmark `BaselineProfileRule` on the emulator: cold start, open chat, scroll the transcript, stream a fixture reply, open and close a sheet, predictive back.
  - R8 full optimisation is needed for DEX layout optimisation.

### 9.3 Smooth streaming: the pipeline (recommendation)

1. **Coalesce:** SSE deltas append to a per-part buffer off the main thread. A main-thread loop publishes **at most once per frame** (`withFrameNanos { }`, then publish if dirty). The demo did `p.text += delta` per event.
2. **Block model:** the reply is split into blocks (paragraph, list, code fence, table, tool step).
   - Closed blocks are immutable and reused, and each is a lazy item keyed `"$partId:$index"`.
   - Only the open tail block re-parses and re-lays out.
   - This keeps long replies virtualised and is the single biggest win.
3. **Words fade in (owner-approved), paint-only:**
   - draw the open block's text fully;
   - mask the tail with `graphicsLayer(compositingStrategy = Offscreen)` + `drawWithContent { drawContent(); drawRect(revealBrush, blendMode = BlendMode.DstIn) }`;
   - `revealBrush` comes from `TextLayoutResult` line and offset boxes for characters added in the last ~200 ms.
   - Under Calm or Off, no mask.
   - [U: tune cost on the nubia; fall back to a whole-block fade if the mask costs too much.]
4. **Follow-scroll:**
   - `reverseLayout = true` pins the newest content while the reader is at the bottom (within ~48 dp).
   - Once they scroll up, stop following and show the jump pill.
   - Spike case: the reader is near the bottom with the *growing* tail block partly visible. Reverse layout then pushes text up under their eyes. Compare it against a normal layout with `requestScrollToItem(...)` (applies on the next remeasure without animation; it exists on `LazyListState` [V]) and pick by feel.
5. **One thing moves while running (MOT-15):** the glow or Stop's ring, drawn in the draw phase. Never animate the chip row's layout while streaming.
6. **Fixture-driven benchmark:** a recorded OC2 stream (tokens at 30–80/s, code fences, tool steps). Measure frame duration P50/P90/P99 with `FrameTimingMetric` on the emulator, and confirm on the owner's phone with `gfxinfo`.
   - Budget at 120 Hz: P90 < 8.3 ms. That number is my target, not an official one.

---

## 10. KMP layering for motion (iOS and Linux desktop to follow)

| Piece | commonMain | androidMain | iosMain (later) | desktop (later) |
|---|---|---|---|---|
| Tokens, `KitMotionScheme`, `MotionPolicy` | ✓ | | | |
| Kit parts (AnimatedVisibility/Content, shared bounds, item animations) | ✓ | | | |
| Back | `NavigationBackHandler`, Nav3 `NavDisplay` | system predictive back | edge swipe-back (CMP; Nav3 iOS defaults follow layout direction since CMP 1.13.0-alpha01) | Esc / mouse back [U] |
| Motion signals | interface | animator scale, saver, thermal | `isReduceMotionEnabled` | none or gsettings [U] |
| Haptics | `KitHaptic` enum | `LocalHapticFeedback` / `performHapticFeedback` | UIFeedbackGenerators [U] | no-op |
| Press feedback | `KitPress` interface | platform ripple | highlight/dim | hover + focus ring |
| Overscroll | default factory | stretch / glow | CMP iOS bounce (aligned in 1.13.0-alpha02) | none |
| Insets / IME | modifiers | edge-to-edge + adjustResize | safe area | n/a |

---

## 11. Recommendations for this app (ordered)

1. **Kit motion foundation** (one kit owner, first). This is pure logic, so tests come first, ported from the catalog entries in §9.1.
   - `KitMotion` tokens, `KitMotionScheme` (standard numbers), `MotionPolicy` + signals, `KitHaptic` + Android mapping, `KitPress` (ripple).
2. **Navigation shell on Nav3 1.2.0** with `SharedTransitionLayout`, the kit specs, Material predictive-preview values, and swipe-edge-aware shifts (RTL).
   - The root never consumes back.
   - Option A wiring for the row → chat transform (§4.4), with no timers.
3. **Spike B** (one Opus agent, its own worktree, emulator only): Compose 1.13-beta + Nav3 1.3-alpha deferred handoff, with the acceptance list in §4.4.
   - Result: adopt when both are stable, or stay on A.
4. **`KitSheetHost`** on the in-window M3 `BottomSheet` with a kit scrim. The single back owner while visible.
5. **Chat screen motion slice:**
   - reverse-layout transcript with block items;
   - frame-coalesced streaming;
   - composer on `imePadding`;
   - sent-bubble lift (paint-only);
   - word reveal mask;
   - jump pill;
   - glow in the draw phase.
   - Then a Macrobenchmark with the stream fixture.
6. **Edge-to-edge** from day one: theme-driven bar icons, a contrast-scrim decision under the composer, and the same for the widget overlay activities.
7. **Baseline profile module** as soon as the shell and chat exist. It is cheap and covers startup, which is a demo strength worth keeping.
8. **Gates to port from Flutter:**
   - no raw `tween`/`spring` outside the kit (MOT-1);
   - no raw `performHapticFeedback` outside the kit (G2);
   - no `ModalBottomSheet`/`Dialog` outside the kit;
   - only the kit depends on material3.

   A Gradle dependency rule plus a detekt/grep test replace the Dart gate tests.
9. **Owner-rule checklist for every UI brief, motion edition:**
   - 150–250 ms;
   - exits faster;
   - springs only for gesture and interruptible motion;
   - no overshoot;
   - nothing moves at Off;
   - Calm = fades only;
   - one ambient loop per screen, only while waiting;
   - native ripple, stretch, predictive back and haptics (MOT-11 list only);
   - RTL-aware directions;
   - a tip for any hidden gesture (`imeNestedScroll`, long-press).

---

## 12. Unknowns (verify in the first spikes)

1. Whether Nav3 1.3 + Compose 1.13 deferred handoff actually delivers a jank-free shrink into the row with `permitTransformDuringDeferredTransition`, including RTL, fling and an off-screen row. No official sample combines them.
2. Release dates for Compose 1.13.0 stable, Nav3 1.3.0 stable and the CMP 1.13 stable that picks them up. None was announced in what I read. Compose has shipped roughly every 4 months (1.10 Dec 2025, 1.11 Apr 2026, 1.12 Aug 2026), which suggests about Dec 2026 [U].
3. Exact API names in M3 1.5: `RippleConfiguration`, whether `MotionScheme` may be implemented by apps, `BottomSheet` scrim and accessibility semantics when used modally.
4. `InfiniteTransition` behaviour at animator scale 0, and whether CMP iOS maps Reduce Motion to `MotionDurationScale` (I found no evidence it does).
5. How the nubia (MyOS/Android 16) vibrator renders `Confirm` vs `VirtualKey` vs `LongPress`, and whether the owner hears "send" as too strong. Tune on device.
6. Whether the nubia's adaptive refresh lowers the frame rate during streaming or glow. Measure with `gfxinfo` before using `preferredFrameRate`.
7. IME animation on API 26–29 (minSdk 26): frame-synced or a jump. This only affects old devices.
8. Android 17's "IME not restored after rotation": which configuration-change strategy the architecture slice chooses.
9. Android 16 long-press back on 3-button navigation: does an in-app predictive preview (NavDisplay) run for it the same way as for the gesture? Test on the emulator with 3-button navigation.
10. `stabilityConfigurationFile` vs `stabilityConfigurationFiles` on Kotlin 2.4.21, and any `featureFlags` defaults (`OptimizeNonSkippingGroups`, `PausableComposition`) for that compiler.

---

## Sources (accessed 2026-11-01; page dates where shown)

- Compose release table (updated 2026-10-21): https://developer.android.com/jetpack/androidx/releases/compose
- Compose BOM mapping (2026.09.00 → 1.12.1, M3 1.4.0): https://developer.android.com/develop/ui/compose/bom/bom-mapping
- Compose Animation release notes (1.10 stable shared transitions; 1.12 deferred; 1.13-alpha03 opt-in removed): https://developer.android.com/jetpack/androidx/releases/compose-animation
- Compose material3 release notes (1.4.0, 1.5.0 alphas/betas): https://developer.android.com/jetpack/androidx/releases/compose-material3
- Jetpack Compose August '26 release blog (2026-08-12): https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html
- Compose December '25 release blog (pausable composition default): https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-december-release
- MotionScheme source and tokens (androidx-main): https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MotionScheme.kt, `.../tokens/StandardMotionTokens.kt`, `.../tokens/ExpressiveMotionTokens.kt`, `.../MaterialTheme.kt`
- MotionScheme API (CMP docs): https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-motion-scheme/
- ModalBottomSheet and BottomSheet source (androidx-main): `compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ModalBottomSheet.kt`, `BottomSheet.kt`
- Navigation3 release notes (1.2.0 stable; 1.3.0-alpha01–03): https://developer.android.com/jetpack/androidx/releases/navigation3
- NavDisplay source (androidx-main; DeferredAnimatedContent wiring; Android defaults): `navigation3/navigation3-ui/src/commonMain/.../NavDisplay.kt`, `src/androidMain/.../NavDisplay.android.kt`
- NavigationEvent release notes (1.2.0, 2026-10-07): https://developer.android.com/jetpack/androidx/releases/navigationevent
- Activity release notes: https://developer.android.com/jetpack/androidx/releases/activity
- DeferredAnimatedContent API: https://composables.com/jetpack-compose/androidx.compose.animation/animation/composable-functions/DeferredAnimatedContent/api.md
- Shared elements guide (updated 2026-10-28): https://developer.android.com/develop/ui/compose/animation/shared-elements
- Shared elements with navigation and predictive back (updated 2026-10-28): https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation
- Predictive back setup (updated 2026-10-28): https://developer.android.com/develop/ui/compose/system/predictive-back-setup
- Predictive back gesture guide (updated 2026-10-28): https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
- Predictive back design values (updated 2024-10-24): https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back
- Android 16 behavior changes (targetSdk 36): https://developer.android.com/about/versions/16/behavior-changes-16
- Android 17 behavior changes, targeting (updated 2026-10-31) and all apps (2026-10-28), plus features: https://developer.android.com/about/versions/17/behavior-changes-17, https://developer.android.com/about/versions/17/behavior-changes-all, https://developer.android.com/about/versions/17/features
- Three-button predictive back on Android 16 [S]: https://www.androidauthority.com/android-16-predictive-back-three-button-navigation-3566000
- Edge-to-edge setup (updated 2026-10-28): https://developer.android.com/develop/ui/compose/system/setup-e2e
- Window insets overview, insets UI, keyboard animations, WindowInsetsRulers (all updated 2026-10-28): https://developer.android.com/develop/ui/compose/system/insets, https://developer.android.com/develop/ui/compose/system/insets-ui, https://developer.android.com/develop/ui/compose/system/keyboard-animations, https://developer.android.com/develop/ui/compose/system/evaluate-rulers
- Scroll and overscroll (updated 2026-10-28): https://developer.android.com/develop/ui/compose/touch-input/pointer-input/scroll; `rememberOverscrollEffect` API: https://developer.android.com/reference/kotlin/androidx/compose/foundation/rememberOverscrollEffect.composable
- Haptic feedback guide (updated 2026-10-26): https://developer.android.com/develop/ui/views/haptics/haptic-feedback; haptics APIs: https://developer.android.com/develop/ui/views/haptics/haptics-apis
- HapticFeedbackType source (androidx-main): `compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType.kt`
- WindowRecomposer (animator scale → MotionDurationScale, androidx-main): `compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/WindowRecomposer.android.kt`
- MotionDurationScale API: https://composables.com/jetpack-compose/androidx.compose.ui/ui/interfaces/MotionDurationScale.md
- Strong skipping (updated 2026-10-20): https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- Fixing stability (updated 2026-10-20): https://developer.android.com/develop/ui/compose/performance/stability/fix; Compose compiler: https://developer.android.com/develop/ui/compose/compiler
- Baseline profiles (updated 2026-10-28): https://developer.android.com/topic/performance/baselineprofiles/overview
- `preferredFrameRate` API: https://developer.android.com/reference/kotlin/androidx/compose/ui/preferredFrameRate.modifier
- LazyListState API (`requestScrollToItem`): https://composables.com/jetpack-compose/androidx.compose.foundation/foundation/classes/LazyListState/api.md
- CMP compatibility and versioning; what's new in 1.12; GitHub releases (1.12.2, 1.13.0-beta01): https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html, https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html, https://github.com/JetBrains/compose-multiplatform/releases
- Apple `isReduceMotionEnabled`: https://developer.apple.com/documentation/uikit/uiaccessibility/isreducemotionenabled
- **Local verification (2026-11-01):**
  - `~/.gradle/caches`: `org.jetbrains.compose.material3:material3-android:1.12.0-alpha03` `.module` depends on `androidx.compose.material3`, and the cache holds `material3-android 1.5.0-alpha22`;
  - `javap` on `animation-android 1.12.1` (`DeferredAnimatedContent`, `SharedContentConfig` members), `animation-core-android 1.12.1` (`DeferredTransitionState.defer/animateTo`, `EaseOutCubic`, InfiniteTransition duration scale), `foundation(-layout)-android 1.12.1` (overscroll APIs, `imeNestedScroll` `@ExperimentalLayoutApi`), `ui-android 1.12.1` (`preferredFrameRate`, `WindowInsetsRulers`), and `material-ripple-android` (`UnprojectedRipple extends RippleDrawable`).
- Repo and plan inputs:
  - `/home/eslam/Storage/Code/kmp-demo` (`App.kt` gesture latch, `Theme.kt` springs, `AndroidPlatform.kt` reduce-motion read);
  - `/home/eslam/Storage/Code/wt/native-plan/map/ui-kit.md` §07–§10;
  - `/home/eslam/Storage/Code/wt/native-plan/research/kmp-cmp-status.md`;
  - `/home/eslam/Storage/Code/wt/oc_app-batch1/docs/qa/micro-motion-2026-10-27/README.md`;
  - owner memory files `android-native-feel`, `micro-animations-wanted`, `no-glass-rule`, `text-scale-cap`, `teach-what-is-hidden`, `composer-green-glow-liked`, `chat-kit-decisions-2026-10-02`.
