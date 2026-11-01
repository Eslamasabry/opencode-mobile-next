# Slice 0 · P2: predictive back and the row → chat container transform

Date: 2026-11-01. Branch: `native/p2-back-2026-11-01`, created from `claude/native-p2-back-2026-11-01-jwae7y` at
`6627d320` (the M0 branch after the P1, P5 and P6 merges).

Inputs:
- ADR `plan/01-architecture-decision.md` §11 P2 (binding) and §9.4;
- `plan/08-motion-animation.md` §2.6, §3, §5.4, §9.7 and §13.2;
- `plan/07-screens-navigation.md` §3;
- `research/motion-native.md` §3–§4;
- `DRAFT-STATUS.md`.

The Codex audits cover plans 01/03/04/05 and have no P2 item. The one audit-related fix (BLOCKER 2, compileSdk 37.1)
was already in P1 and still holds.

## Result

**Option A is built and used. Option B was not spiked**, because option A meets every check that can be run here
(see "Six checks" below). The brief allowed B only if A failed.

What works:
- Tapping a conversation row grows the chat out of that row.
- Top-bar Back, and a key or 3-button Back, shrinks the chat back into its row.
- A back **swipe** turns the shared bounds off and shows the Material predictive preview:
  - the page scales to 0.9;
  - it shifts away from the swiped edge;
  - it fades through from 35 % progress;
  - its corners round to 28 dp.
- The transform comes back only when the navigation transition has settled. This is a state machine with no timer:
  the demo's 700 ms latch is gone.
- Under the system "Remove animations" setting there is no transform and no preview.

Proven on this container:
- unit tests;
- headless Compose UI tests on the desktop JVM, which drive a fake back gesture through the real `NavShell` /
  `NavDisplay`;
- a Robolectric test of the Android animator-scale source;
- rendered frame captures, which I inspected.

**Not proven:** anything on an emulator or device, because this container has no emulator. Those steps are in
"Emulator steps" below, for the coordinator to run on the PC.

Shipping state: implemented, verified locally (host and desktop JVM only), committed and pushed to the branch. There is
no PR, no CI run, no signing and no release.

---

## 1. What was built

### 1.1 `:ui:shell` (coordinator-owned; the brief's module)

| File | Content |
|---|---|
| `commonMain/.../nav/RowTransformGate.kt` | Plan 08 §5.4 gate:<br>• `GateState` (Idle, Opening, Closing, Gesture, GestureSettling)<br>• `GateEvent`<br>• the pure `reduce`<br>• `sharedBoundsEnabled(state, level)`<br>• the snapshot-backed `RowTransformGate` holder, which also records the push/pop direction and a 64-event trace<br>• `ShellMotionLevel` (Full/Off) and `motionLevelOf(scale)` |
| `commonMain/.../nav/ShellMotion.kt` | Route transitions per `RouteMotion`:<br>• `GrowFromRow`: the list holds still and the kit's shared bounds do the motion<br>• `SharedAxisX`: 30 dp, 250 ms, direction-aware through `slideIntoContainer(Start/End)`<br>• `SlideUp`<br>• `Root`: cross-fade<br>The Material predictive preview `predictivePop(edge, level, edgeShiftPx)`. The helpers `shiftSign(edge)` and `edgeShiftPx = width/20 − 8 dp`. Under Off, every spec is `None togetherWith None`. |
| `commonMain/.../nav/ShellNavDisplay.kt` | Wiring:<br>• `SharedTransitionLayout` around `NavDisplay`<br>• `GestureStarted`/`GestureCancelled` from `LocalNavigationEventDispatcherOwner…transitionState`<br>• `GestureCommitted` vs `ButtonBack` decided in `NavDisplay(onBack)` by the gate state<br>• `TransitionSettled` when no more than one entry is composed (an entry decorator counts them)<br>• the `ShellGrowHost` (the kit seam) provided per entry with that entry's `LocalNavAnimatedContentScope`<br>• preview corners drawn in `graphicsLayer` on the leaving entry<br>• route motion added to each `NavEntry`'s metadata so the specs can read it |
| `commonMain/.../nav/SystemMotion.kt` | `interface SystemMotion { val animatorScale: StateFlow<Float> }`: a service, ADR §7.3 rule 1. |
| `androidMain/.../nav/AndroidSystemMotion.kt` | `Settings.Global.ANIMATOR_DURATION_SCALE` through an app-scoped `ContentObserver`, so it is **live**. Plan 08 §3.1: the demo read it once. Bound with `@ContributesBinding(AppScope)`; it uses the `Application` that the graph already has. |
| `desktopMain/.../nav/DesktopSystemMotion.kt` | Always 1. |
| `App.kt`, `ShellGraph.kt`, `ShellNavigator.kt` | `NavShell` uses `ShellNavDisplay`; `ShellDependencies` gains `motion: SystemMotion`. `ShellNavigator` gains `gateListener`: `openChat` → `TapOpen`, an in-app `back()` → `ButtonBack`. It also gains `systemBack()` for `NavDisplay`'s `onBack`, which does not send `ButtonBack` again. `App(...)`'s signature is unchanged, so neither app module was edited. |
| `build.gradle.kts` | Additions:<br>• `navigationevent-compose` (catalog entry already there)<br>• `junit4` + `robolectric` for `androidHostTest`<br>• the JDK 21 `--add-exports` that Robolectric needs |

### 1.2 `:feature:chats` (the brief's module)

- `ChatsUi` gains `rows`. `ConversationRowUi(ref, title, preview)` uses `ref` as the grow key.
- `ChatsActions.openConversation(ref)` → `AppNavigator.openChat(ref)`.
- `ConversationFixture`: 36 fake conversations, including Arabic titles, so the list scrolls past the screen. They are
  data, not copy.
- `ChatsScreen`: a `LazyColumn` of kit rows (`KitRow(growKey = row.ref)`) under the existing placeholder and button.
- `ChatPreviewViewModel` (route-keyed, assisted) and `ChatPreviewScreen` (`KitGrowPage(growKey = ref) { KitScreen(…) }`).
  This is the page for `ChatRoute`, installed by `ChatsEntries` **until `:feature:chat` exists**; the chat lead
  replaces the entry.
- All files keep to the K1 allowlist: kit, layout, lazy, runtime, `Modifier`, `unit`.

### 1.3 `:ui:kit`: a minimal addition outside the brief's modules (see D1)

The edit adds two **new files** and changes no existing kit file.
- `motion/KitGrow.kt`:
  - `KitGrowHost` (the seam the shell provides) and `LocalKitGrowHost`;
  - `KitGrowMotion` (the X1/X2 numbers from plan 08 §5.4);
  - `KitGrowPage`;
  - the internal `Modifier.kitGrowSource`;
  - `GateConfig : SharedContentConfig`, with:
    - `isEnabled` = host gate;
    - `shouldKeepEnabledForOngoingAnimation = false`;
    - `alternativeTargetBoundsInTransitionScopeAfterRemoval = null`.
- `KitRow.kt`: a tappable row (M3 `Surface(onClick)`: platform ripple, `KitHaptic.Select`, 20 dp corners) with an
  optional `growKey`.

The motion tracks follow plan 08 §5.4:

| Track | Value |
|---|---|
| Open bounds | 360 ms emphasized |
| Return bounds | 300 ms emphasized, run forward (not reversed) |
| Row words out | 0–90 ms |
| Page words in | 90–270 ms |
| Page words out on return | 0–120 ms |
| Row hand-back | 240–300 ms |
| Corners | 20 → 0 dp |
| Page ground | the page's ground from frame 1 |

`skipToLookaheadSize` is on the page content.

Two details:
- **The ground.** The page ground is drawn **outside** the words' fade. The first build had it inside the fade; the
  frame captures showed the list bleeding through, with no visible box. This is fixed (see §3).
- **No matching row.** In that case the ground fades together with the words. This is the P2-5 fallback.

---

## 2. Commands and results

Environment set-up came first, as the brief asked. Everything ran from `native/` with
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and `ANDROID_HOME=/opt/android-sdk`.

| Step | Command / result |
|---|---|
| SDK tools | `curl -sSfLo cmdline.zip https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip`, unzipped to `/opt/android-sdk/cmdline-tools/latest` |
| Licences | `yes \| sdkmanager --licenses` |
| Packages | `sdkmanager "platform-tools" "platforms;android-36" "platforms;android-37.0" "platforms;android-37.1" "build-tools;37.0.0" "build-tools;36.1.0"`. Installed: android-36, 37.0 and 37.1; build-tools 36.1.0 and 37.0.0 (`Sdk.kt`: compile 37 minor 1, AGP default build-tools 37.0.0). |
| JDK 17 | `apt-get install openjdk-17-jdk-headless`; Gradle itself runs on 21 (P1 D1). |
| Timeouts | `~/.claude/settings.json`: `BASH_DEFAULT_TIMEOUT_MS=1800000`, `BASH_MAX_TIMEOUT_MS=3600000`. They apply from the next session; this one used explicit timeouts. |
| Maven Central | HTTP 429 through the sandbox proxy, as in P1. A **container-only** `~/.gradle/init.d/central-mirror.gradle.kts` (not in the repo) rewrites `repo.maven.apache.org` to Google's Central mirror `maven-central.storage-download.googleapis.com/maven2/`. It also sets `robolectric.dependency.repo.url` to the same mirror, because Robolectric downloads `android-all-instrumented` itself. The checked-in settings are unchanged. |

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :androidApp:assembleDebug` (baseline, before any change) | BUILD SUCCESSFUL in 4 m 48 s (cold) |
| 2 | `./gradlew :desktopApp:compileKotlin compileKotlinDesktop` | BUILD SUCCESSFUL, no warnings |
| 3 | `./gradlew :ui:shell:desktopTest -Poc.werror=true` | BUILD SUCCESSFUL. 22 tests: `RowTransformGateTest` 9, `ShellMotionTest` 3, `PredictiveBackShellTest` 6, `FrameCaptureTest` 4; 0 failures |
| 4 | `./gradlew :ui:shell:testAndroidHostTest` | BUILD SUCCESSFUL. 14 tests: `AndroidSystemMotionTest` 2 (Robolectric 4.17, `@Config(sdk = [36])`), plus the commonTest suites 9 + 3 |
| 5 | Planted fault: `reduce` sends `GestureCommitted` → `Idle` instead of `GestureSettling` | 4 tests fail, including the UI test `P2-1 a committed gesture keeps shared bounds off until the pop settles`. Reverted. |
| 6 | `./gradlew :androidApp:assembleDebug compileKotlinDesktop :desktopApp:compileKotlin desktopTest testAndroidHostTest :quality:test` | Everything passes except `:quality:test` `ModuleOwnersTest`, which **fails on the base commit too** (open problem 1). Konsist K1 to K19 pass, including K1 on the new feature files, as do `GraphSmokeTest` (the desktop graph resolves `ShellDependencies` with `DesktopSystemMotion`) and P1's `DesktopShellUiTest`. |
| 7 | `aapt2 dump badging` on the APK | `package: name='io.github.eslamasabry.opencode_mobile.native' versionCode='10000' versionName='3.0.0-dev' compileSdkVersion='37'`, `targetSdkVersion:'36'`, `launchable-activity: io.github.eslamasabry.opencode_mobile.MainActivity` |

**APK:** `native/androidApp/build/outputs/apk/preview/debug/androidApp-preview-debug.apk`. It is about 13.8 MB, an
unminified debug build with the preview id. This is a native build, not Flutter/Shorebird, so a debug APK is fine
here. Metro validated the Android graph at compile time, including the new `AndroidSystemMotion` binding.

### Tests added (all named after catalog row B-KIT-054, or B-KIT-008 for the motion signal)

| Suite | Where | What it proves |
|---|---|---|
| `RowTransformGateTest` (9) | `commonTest` (desktop and Android host) | Every gate transition:<br>• tap → settle → Idle;<br>• button back from Idle and from Opening;<br>• a gesture keeps the transform off through `GestureSettling`, and the next tap transforms again (the 07-sketch bug);<br>• commit/cancel ordering in either order;<br>• a gesture during Opening disables at once;<br>• settle never ends a live gesture;<br>• taps and button backs are ignored mid-gesture;<br>• Off never enables;<br>• direction tracking. |
| `ShellMotionTest` (3) | `commonTest` | The shift sign follows the edge: LEFT → +1, RIGHT → −1, NONE (3-button long press) → 0. `edgeShiftPx` equals `width/20 − 8 dp` and is never negative. Under Off, predictive = `None`/`None`; under Full, the page underneath has no enter motion. |
| `PredictiveBackShellTest` (6) | `desktopTest` (`v2.runComposeUiTest`; real `NavShell`, `NavDisplay`, kit parts; a fake `NavigationEventInput` on the dispatcher) | • tap → chat, gate trace `[TapOpen, TransitionSettled]`; Back → list.<br>• **P2-1:** start/progress/complete. The list is composed under the preview, the gate stays `GestureSettling` one frame after commit, then `Idle`. A commit is never a `ButtonBack`.<br>• **P2-2:** cancel → still on the chat, gate `Idle`. After button back, the next tap has the transform enabled.<br>• a gesture 120 ms into the opening transform pops cleanly.<br>• **P2-6:** at scale 0 the gesture pops and the gate stays consistent.<br>• The root never consumes Back. |
| `FrameCaptureTest` (4) | `desktopTest` (`v2.runDesktopComposeUiTest(412, 860)`) | Writes plan 08 §5.4 frames to `ui/shell/build/p2-frames/`:<br>• `grow_open_{0,90,180,270,360,450}ms`<br>• `grow_return_{0,60,120,240,300}ms`<br>• `predictive_{25,50,75}pct_{left,right}_{ltr,rtl}`<br>• `off_open_2frames`, `off_predictive_50pct`<br>These are evidence, not goldens; Roborazzi goldens are P7. |
| `AndroidSystemMotionTest` (2) | `androidHostTest`, Robolectric | A scale of 0 reads as Off. A scale change while running reaches the `StateFlow` through the `ContentObserver` (live, not read once). |

### What the frames show (inspected by eye; numbers measured from the PNGs)

- **Open:**
  - 0 ms: the row shows its pressed ripple.
  - 90 ms: an opaque box covers rows 2–5, growing out of row 3.
  - 180 ms: the box is nearly full and the words are fading in.
  - 270 ms onward: the chat is full and still.
  - The rows outside the box do not move ("the list holds still").
- **Return:**
  - 60 ms: the box is near full, with the words fading.
  - 120 ms: the box covers rows 2–5.
  - 240 ms: the box is in row 3's slot.
  - 300 ms: row 3 is handed back.
- **Predictive preview:** a scaled card, fading through from 35 %; at 75 % the list shows through. Shift direction,
  measured from the Send button's x-range at 25 %:

  | Swiped edge | LTR | RTL | Card moves |
  |---|---|---|---|
  | Left | 39 px | 313 px | right |
  | Right | 21 px | 295 px | left |

  The direction is the same in LTR and RTL, so it follows the swiped edge, not the layout direction.
- **Off:** the chat is complete two frames after the tap, and there is no preview at 50 % progress.

---

## 3. Deviations from the plan, and why

| # | Plan says | Built | Why |
|---|---|---|---|
| D1 | Brief: build only in `:ui:shell` (`androidMain`) and the chats feature. Plan 08 §11 M1: `RowTransformGate`, `Modifier.growFromRow` and `KitRouteTransition` live in `:ui:kit`. | Two **new** files in `:ui:kit` (`motion/KitGrow.kt`, `KitRow.kt`); no existing kit file changed. The gate is in `:ui:shell`. | A feature may not touch `sharedBounds` or the nav scope (Konsist K1), so the row and page parts must come from the kit. The gate is shell state the kit only reads through `KitGrowHost.transformEnabled`. The kit owner can fold it into `KitRouteTransition` (M1). |
| D2 | "Build in `:ui:shell` `androidMain`" | Option A is in `:ui:shell` **`commonMain`**. Only the animator-scale source is in `androidMain` (plus a desktop stub). | Option A uses only APIs CMP 1.13 already has in common: `SharedTransitionLayout`, `SharedContentConfig`, Nav3 1.2 `NavDisplay` with `predictivePopTransitionSpec`, and `navigationevent` `transitionState`. ADR §9.4 puts only **option B** in `androidMain` as a `JETPACK_AHEAD` item. Common code also lets the headless desktop tests run the real wiring. `JETPACK_AHEAD.md` gains no row: nothing Android-ahead is used. |
| D3 | Plan 08 §3: the motion level is Full/Calm/Off from the kit's `MotionPolicy` and `KitPlatform` signals (unit M0) | `ShellMotionLevel` Full/Off from the live system animator scale only. Calm, Saver, power save, heat and the in-app choice are not wired. | M0 (kit owner) is not built. ADR §7.3 forbids `expect` outside `:core:common`, so the scale is a Metro-bound `SystemMotion` service. It moves behind `KitPlatform` with M0. |
| D4 | Plan 08 §5.4: `TransitionSettled` when the count of entries whose `transition.isRunning` drops to 0 | `TransitionSettled` when **no more than one entry is composed**. | After a committed pop the leaving entry is disposed when its exit ends, and after a cancel the revealed entry is disposed. The composed count is therefore exact and also works when the transition is `None` (Off). It is still state, not a timer. |
| D5 | Plan 08 §5.4 fallback: "the gate checks the origin is attached and inside the viewport before choosing", then a `Page` pop | With no matching row, the page's ground and words fade out together (120 ms). `alternativeTargetBounds…AfterRemoval` returns null, so the transform never aims at stale bounds. There is no shared-axis `Page` pop. | In Nav3's single-pane scene the list is not composed while the chat is on top, so "attached and in the viewport" cannot be known when the pop spec is chosen. Compose matches live bounds at transition time instead. Check P2-5 on the emulator decides whether the fade is acceptable. |
| D6 | Dim over the list (0 → 0.32 scrim) during open/return; TalkBack focus moves to the chat title on settle; the list freezes reorders while the gate is not Idle | Not built. | These are polish around the proof. The settle signal they need now exists (`TransitionSettled`). They are listed as open problems. |
| D7 | `RouteMotion` names `GrowFromRow, Page, FullScreenDialog, TopLevel, Replace, Root` (plan 08 §13.2) | The existing `:ui:routes` enum `Root, SharedAxisX, GrowFromRow, SlideUp` is used unchanged. | `:ui:routes` is coordinator-owned and outside this brief. |
| D8 | Tween/spring only in the kit (plan 08 §4 R1) | `ShellMotion` builds the route and predictive specs with `tween`/`keyframes` in `:ui:shell`. | Plan 08 §5.4's own `ShellMotion` sketch does the same, and the Konsist R1 rule does not exist yet. The numbers match plan 08 §2.7; they move to `MotionTokens`/`KitMotion` with M0. |
| D9 | Compose UI tests in `commonTest` | In `desktopTest`. | AGP-KMP feeds `commonTest` into `androidHostTest`, where Compose UI tests need Robolectric (`Looper.getMainLooper not mocked`). Robolectric Compose tests are P7's decision. |
| D10 | `ChatRoute`'s page belongs to `:feature:chat` | A P2 proof page `ChatPreviewScreen` is installed by `:feature:chats`. | `:feature:chat` does not exist, and the brief limits work to the chats feature. Its fixture lines are data in the ViewModel, not copy. |

Settled unknowns from the plan:
- **Option A works on Nav3 1.2.0 with a gate only.**
  - `SharedContentConfig.isEnabled` and `shouldKeepEnabledForOngoingAnimation = false` are honoured mid-transition.
    The planted-fault run shows that the tests depend on them.
  - The headless frames show that with the transform off the page under the preview keeps its row visible: the
    `sharedBounds` enter/exit do not run when there is no match.
  - On Compose 1.13.0-beta01, `isEnabled` is an extension **property**
    (`val SharedContentState.isEnabled`), not a function. `alternativeTargetBoundsInTransitionScopeAfterRemoval` is
    an extension function `(targetBoundsBeforeRemoval: Rect, sharedTransitionLayoutSize: Size): Rect?`. Plan 08's
    sketch has the old shape.
- **The `NavigationEventDispatcher.transitionState` hook:**
  - It is a `StateFlow`: `Idle` or `InProgress(latestEvent, direction)`.
  - The dispatcher NavDisplay registers on is the one from `LocalNavigationEventDispatcherOwner`.
  - A fake `NavigationEventInput` subclass can drive it in tests. Its protected `dispatchOnBack*` methods are enough,
    so `navigationevent-testing` was not needed.
- **Android resolves Google's Nav3 binary:** `androidx.navigation3:navigation3-ui-android:1.2.0` and
  `navigation3-runtime-android:1.2.0`, under `org.jetbrains.androidx.navigation3:navigation3-ui` 1.2.0. That is ADR
  §11 P2's acceptance.

---

## 4. Resolved versions (from `./gradlew :androidApp:dependencies --configuration previewDebugRuntimeClasspath`)

| Area | Resolved |
|---|---|
| Navigation 3 (Android) | `androidx.navigation3:navigation3-ui-android` 1.2.0, `navigation3-runtime-android` 1.2.0 |
| navigationevent | `androidx.navigationevent:navigationevent-android` 1.1.1, `navigationevent-compose-android` 1.1.1. The catalog pins 1.1.0; transitive requests raise it. P1 saw the same. |
| Compose animation | `androidx.compose.animation:animation` 1.13.0-beta01 (CMP 1.13.0-beta01) |
| Lifecycle Nav3 | `androidx.lifecycle:lifecycle-viewmodel-navigation3` 2.11.0 |
| Robolectric | 4.17. It runs at `@Config(sdk = [36])` and downloads `android-all-instrumented:16-robolectric-13921718-i7`; it needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` on JDK 21. |
| JUnit | 4.13.2 |
| SDK | compileSdk 37.1, targetSdk 36, minSdk 26, build-tools 37.0.0 |

---

## 5. The six checks (motion-native §4.4) and plan 08 §9.7 P2-1..7

| Check | Here (headless) | Emulator (to run) |
|---|---|---|
| (1) / P2-1 slow drag + commit lands with no jump, no shared-bounds flash | Gate stays off until settle (UI test, planted fault caught); preview frames | E3 |
| (2) / P2-2 cancel returns without flicker; next tap still grows | UI test | E4 |
| (3) / P2-3 fling commit carries velocity, ≤ 300 ms | **Not testable here.** The commit animation after release is Nav3 1.2's seek-to-end, and the fake input has no velocity. | E5 |
| (4) / P2-4 RTL from both edges | Frames: the shift follows the edge in LTR and RTL | E6 |
| (5) / P2-5 row gone or off screen | Kit config returns no alternative bounds; ground and words fade (D5) | E7 |
| (6) / P2-6 reduced motion = plain pop | UI test, Off frames, Robolectric scale observer | E8 |
| P2-7 3-button tap = shrink; long-press = preview | The gate maps a non-gesture `onBack` to `ButtonBack` (tested) | E9 |

---

## 6. Emulator steps for the coordinator (PC)

Use an API 37 or API 36 Google APIs x86_64 AVD with gesture navigation, a 1080 × 2400 screen and no other apps running.
Run E1–E9 in order and record each with `screenrecord`. Evidence goes to `docs/qa/native/motion-2026-11-0x/README.md`
(plan 08 §9.7). The package is `io.github.eslamasabry.opencode_mobile.native`.

```bash
APK=native/androidApp/build/outputs/apk/preview/debug/androidApp-preview-debug.apk
PKG=io.github.eslamasabry.opencode_mobile.native
ACT=io.github.eslamasabry.opencode_mobile.MainActivity
adb install -r "$APK"
adb shell settings put global animator_duration_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global window_animation_scale 1
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural
rec() { adb shell screenrecord --time-limit 20 /sdcard/$1.mp4 & sleep 1; }    # then: adb pull /sdcard/$1.mp4
```

Row y-positions below assume 1080 × 2400. Read the real ones with `adb shell uiautomator dump` and look for
`Fix the reconnect loop #1`.

**E1. Launch.**
`adb shell am start -n $PKG/$ACT`.
Look for: "Conversations", the placeholder line, the "Show all conversations" button, then a list of rows
("Fix the reconnect loop #1", …, Arabic titles among them).
Back at this screen goes home, with the system back-to-home preview. The root never intercepts Back.

**E2. Open and button-back return (X1/X2).**
`rec e2`, tap a row (`adb shell input tap 540 1100`), wait 1 s, then tap the chat's top-left "Back".
Repeat once with `adb shell settings put global animator_duration_scale 5` to inspect frames, then set it back to 1.

Look for, on open:
- The chat grows out of **that** row's box.
- Its corners go from 20 dp to square.
- The words fade in after the box starts moving.
- The rows outside the box stay still.

Look for, on Back:
- The box shrinks back into the same row.
- The row's words return at the end.
- No flash and no jump at the end.

**E3. P2-1: slow drag + commit.**
Open a row. Then:
```bash
rec e3
adb shell input motionevent DOWN 3 1200
for x in 40 120 200 280 360 440; do adb shell input motionevent MOVE $x 1200; sleep 0.15; done
adb shell screencap -p /sdcard/e3-mid.png        # held at about 40 %
adb shell input motionevent UP 440 1200
```
Look for while held:
- The chat card is scaled toward 90 %, moved to the **right**, with rounded corners, and fading after about 35 %.
- The list underneath is still, with the opened row visible in its slot.

Look for after release:
- The card finishes leaving, and the list is there with **no** box shrinking into the row (no shared-bounds flash)
  and no jump.

**E4. P2-2: drag + cancel, then tap again.**
Open a row, then:
```bash
adb shell input motionevent DOWN 3 1200
adb shell input motionevent MOVE 300 1200
adb shell input motionevent MOVE 20 1200
adb shell input motionevent UP 20 1200
```
Look for:
- The card springs back to full screen, with no flicker or flash.
- Tap Back: the shrink-into-row plays.
- Tap another row: it **grows** (the gate was cleared). If it cross-fades instead, the gate did not settle: fail.

**E5. P2-3: fling.**
Open a row, then `adb shell input swipe 3 1200 700 1200 60` (a fast edge swipe).
Look for:
- The pop finishes quickly, in at most about 300 ms, continuing the finger's motion with no pause or restart.
- With `screenrecord`, step through the frames: no frame shows the card going back to full size.

**E6. P2-4: RTL from both edges.**
```bash
adb shell cmd locale set-app-locales $PKG --locales ar
```
If the app does not flip to RTL, the app has no `localeConfig` yet (P6 on-device work). Use the system-wide switch
instead: `adb shell settings put global debug.force_rtl 1`, then `adb shell am force-stop $PKG` and relaunch.
- Open a row and drag from the **left** edge, as in E3. Look for: the card moves **right**.
- Open a row and drag from the **right** edge:
  ```bash
  adb shell input motionevent DOWN 1077 1200
  # then MOVE 1000 / 900 / 800 / 700 1200, then UP
  ```
  Look for: the card moves **left**.
- Run E2 in RTL. Look for: the rows are mirrored and the box still grows from and returns to the tapped row.

Reset: `adb shell cmd locale set-app-locales $PKG --locales en` and/or `debug.force_rtl 0`.

**E7. P2-5: the row off screen.**
1. Scroll the list so that a row sits at the bottom edge.
2. Open that row.
3. Rotate to landscape: `adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1`.
4. Tap Back.

Look for: the row is not on screen in landscape, so the chat fades out (about 120 ms) instead of flying to stale
bounds. Nothing animates to where the row used to be.
Restore with `user_rotation 0`.
Report whether this fade-out is acceptable, or whether a shared-axis Page pop is wanted (D5).

**E8. P2-6: reduced motion.**
```bash
adb shell settings put global animator_duration_scale 0
```
Look for, with E2 and E3 while the app keeps running:
- The scale is picked up **live**, without a restart.
- Open: the chat appears at once.
- Back: the list at once.
- Back swipe: no card preview; release pops.

Also try Settings › Accessibility › "Remove animations": the result must be the same.
Restore the scale to 1.

**E9. P2-7: 3-button navigation (Android 16+).**
```bash
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton
```
Open a row.
- Tap the nav-bar Back (`adb shell input keyevent KEYCODE_BACK`, or tap the button). Look for: shrink into the row,
  as in E2.
- Open a row again and long-press the nav-bar Back:
  `adb shell input swipe <backX> <backY> <backX> <backY> 1500`, with the coordinates from `uiautomator dump`. Look for
  the preview (card scaled, **no** horizontal shift, because the edge is NONE). Release commits.

Restore with the `navbar.gestural` overlay.

Optional frame check (plan 08 §8): run `adb shell dumpsys gfxinfo $PKG reset`, then E2 three times, then
`adb shell dumpsys gfxinfo $PKG`. Record "Janky frames" and the 90th/95th percentile. This is a debug build, so the
numbers are indicative only. The budget is checked in the release Macrobenchmark (J4/J5, later).

---

## 7. Open problems

1. **`ModuleOwnersTest` fails on the base commit** (`6627d320`), independent of P2. The P5/P6 merges added
   `:tools:arb2xml` and `:tools:flutter-migration-readers`, and `MODULE_OWNERS` has no rows for them.
   `MODULE_OWNERS` is coordinator-only, so it was left alone.
2. **Nothing was run on an emulator or device.** Fling velocity (P2-3), 3-button long press (P2-7), the real cancel
   curve of Nav3 1.2 (plan 08 §5.4 asks P2 to record it), and the feel all need §6 on the PC.
3. **Option B was not spiked**, because A passed what can be run here. S8-1 stays open for the Nav3 1.3 pre-releases.
   It would also give the "shrink into the row under the finger" that A deliberately does not do.
4. **The P2-5 fallback is a fade, not a `Page` pop (D5).** This needs an owner or coordinator decision after E7.
5. **Missing polish from plan 08 §5.4 (D6):**
   - the list dim;
   - frozen reorders while the gate is not Idle;
   - TalkBack focus on settle;
   - `Calm` (cross-fade 150 ms);
   - `KitPlatform.highFrameRate` during transform and preview.
6. **The `:ui:kit` files (D1) need the kit owner's review.** `KitGrowMotion.Emphasized` is the cubic approximation
   `(0.2, 0, 0, 1)`, not plan 08's `PathEasing`, because `PathEasing` needs a `Path`; switch it when M0 lands.
   `KitRow` is a placeholder of the conversation row.
7. **The Robolectric JVM flag** (`--add-exports java.base/jdk.internal.access`) is set in `:ui:shell`'s build file. It
   belongs in the `oc.kmp.*` convention when P7 adopts Robolectric (`build-logic` was not touched).
8. **This container's mirror workaround** (Maven Central 429, and Robolectric's own downloader) is in
   `~/.gradle/init.d`, not in the repo. A fresh container needs it again (P1 §2 has the same note).
9. **Plan corrections for the coordinator:**
   - 08 §5.4: the `SharedContentConfig` member shapes on 1.13 (§3);
   - 08 §5.4 and 07 §3.2: the settle signal is the composed-entry count (D4);
   - 08 §5.4: the box ground must be drawn outside the content fade (found through the frame captures).
10. **Not run:** ktfmt / `tool/format-changed.sh` (it does not exist yet, P1 §8.9).
