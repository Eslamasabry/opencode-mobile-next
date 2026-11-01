# Testing and performance tooling for the native rewrite (KMP + Compose), as of 2026-11-01

Research for the full native rewrite of OpenCode Mobile (`io.github.eslamasabry.opencode_mobile`).
Scope: logic tests, Compose UI tests, screenshot tests, black-box E2E that drives both the Flutter app and the
native app, UIAutomator, Macrobenchmark, Baseline and Startup Profiles, R8, APK size, Perfetto, and HTTP contract
tests with recorded fixtures.

Evidence tags:

- **[V]** verified today from an official or primary source: vendor docs, release notes, Maven metadata or the GitHub API.
- **[R]** read in this repo, the catalog, the owner's memory files or on the machines.
- **[S]** a secondary source: a search snippet, a forum post or a community page.
- **[U]** unverified: my inference or estimate. Check before relying on it.

Companion research in the same folder: `kmp-cmp-status.md` (toolchain versions, Gradle shape),
`network-data.md` (Ktor/OkHttp, SSE parser) and `architecture-stacks.md` (fakes, state holders, DI). This file does
not repeat their decisions; it only says how to test and measure them.

---

## 0. Bottom line

1. **Logic tests go in `commonTest`, using `kotlin.test` + `kotlinx-coroutines-test` 1.11.0 + Turbine 1.2.1 and
   hand-written fakes.** They run on the JVM (`jvmTest` / `testAndroidHostTest`) on the VPS, with no emulator. This is
   where the 712 unit and 268 contract rows of the catalog land, and most of the 445 integration rows. Name each test
   after its catalog row and track coverage of the catalog with a ratchet (§3). [R][V]
2. **Contract tests replay recorded server traffic.** Store real exchanges (JSON + raw SSE) per server and version,
   and replay them through Ktor `MockEngine` 3.6.1 in `commonTest`. Validate the recordings against the pinned
   OpenAPI 3.1 snapshots in `contracts/`. Pact and WireMock are not worth it here: the server is upstream OpenCode,
   which will never run consumer verification (§8). [V][U]
3. **UI behaviour tests come later** (the owner said the 7,967 widget tests come later). Build the harness now so the
   kit can be tested from its first component:
   - common `runComposeUiTest` (the v2 API, `StandardTestDispatcher`) runs on desktop JVM;
   - Android-only behaviour runs as Robolectric 4.17 host tests (`createComposeRule` v2);
   - the emulator is only for what neither can show (predictive-back gesture feel, IME, real notifications, widgets).
   [V]
4. **Screenshots: use Roborazzi 1.77.0.** It is the only screenshot tool that covers Android (Robolectric), Compose
   Desktop and iOS, and it can generate tests from `@Preview`s in `commonMain` (ComposablePreviewScanner 0.9.3).
   - Not Paparazzi (2.0 still alpha, Android only).
   - Not Google's Compose Preview Screenshot Testing: its standalone plugin is now "no longer maintained", its
     replacement is experimental AGP 9.5 test suites, and it does not support non-Android KMP targets.
   - Pick **one golden host (the VPS)**. The Flutter era showed CPUs rasterize differently (§5). [V][R]
5. **Black-box parity E2E: use Maestro CLI 2.12.0.** It reads the Android accessibility tree, so the same YAML flow
   drives the Flutter release APK today and the Compose APK later, and later the iOS app too.
   - Write the 215 `portable_e2e` catalog rows as flows **now, against the Flutter app**, so that green Flutter runs
     become the parity baseline.
   - Select by visible text taken from a generated string table, never by key or id.
   - Run with system animations off.
   - Keep the Python adb harness for process, file and logcat checks that Maestro cannot do. [V][R]
6. **The day-1 in-place upgrade is the most important test.**
   - Seed a real Flutter build on a rootable `google_apis` emulator and save an emulator snapshot.
   - Install the native APK over it with `adb install -r` (never `-d`).
   - Verify with Maestro flows plus `adb root` file checks.
   - Repeat for interrupted migrations (§6.6). [R][U]
7. **Performance tooling: androidx.benchmark 1.5.0** (Macrobenchmark plus the Baseline Profile plugin, which has
   partial KMP support and needs no `newDsl=false` on AGP 9), UIAutomator 2.4.0, Perfetto v59.1 and trace_processor
   (PyPI `perfetto` 0.59.1), `androidx.tracing` 2.0.5 and JankStats 1.0.0. [V]
   - Generate Baseline and Startup Profiles on the PC's API 37 emulator; it needs no root on API 33+.
   - Measure **relative** numbers on the emulator. Take absolute numbers on the owner's phone, and only when the
     owner allows it.
8. **Baseline Profiles help sideloaded users less than Play users.** Outside Play the profile is compiled only when
   background dexopt runs, "likely overnight". [V]
   - Startup Profiles (DEX layout, applied at build time by R8) help from the first launch. Generate both.
   - Report `ProfileVerifier` status in the Performance report.
   - Spike shipping a `.dm` file through the planned in-app updater (§9.4, unknown).
9. **R8: on AGP ≥ 9.3, use the new `optimization { enable = true }` DSL** with `src/main/keepRules/*.keep` files.
   - Run `:androidApp:analyzeReleaseR8Config` at every integration checkpoint.
   - Keep the Flutter era's DEX-level guards (`verify_scheduled_prompt_apk.py`, `verify_widget_release_rules.py`).
   - Judge smoothness only on minified, non-debuggable builds. A debug Compose build is not representative. [V][R]
10. **APK size: per-ABI APKs via AGP `splits.abi`** (arm64-v8a + x86_64, plus a universal APK), with `useLegacyPackaging`
    kept for the proot and engine executables.
    - Keep the size-growth gate, pointed at the arm64 file.
    - "On-demand assets" outside Play means the app's own pinned-SHA-256 downloader, as today. Play Asset Delivery
      is Play-only.
    - Estimated arm64 size ≈ 18–20 MB, against about 36 MB today. [R][U]
11. **Machines.**
    - VPS: no KVM, so no emulator (verified 2026-11-01). It runs JVM tests, goldens and builds.
    - PC: owns the emulator, which handles instrumented tests, Maestro, benchmarks and profile generation.
    - Robolectric at SDK 36/37 needs **JDK 21** for the test JVM, while the build stays on JDK 17. [V][R]

---

## 1. Version snapshot (checked 2026-11-01)

| Tool | Version to pin | Date | Status / note | Source |
|---|---|---|---|---|
| kotlin.test | = Kotlin (2.4.21) | 2026-10-08 | Comes with the Kotlin plugin; `kotlin("test")` in `commonTest` picks the right JVM adapter | `kmp-cmp-status.md` [V] |
| kotlinx-coroutines-test | **1.11.0** | Maven lastUpdated 2026-05-08 | `runTest`, `StandardTestDispatcher`, `backgroundScope` | Maven Central [V] |
| Turbine | **1.2.1** | 2025-06-11 | No release since. KMP incl. wasmJs/wasmWasi; still the standard | GitHub releases + Maven [V] |
| Kotlin power-assert | = Kotlin | page 2026-08-20 | Experimental compiler plugin, KMP-wide | kotlinlang.org [V] |
| kotest-property (optional) | 6.2.6 | 2026-10-14 | Property tests for parsers, usable inside `runTest` | Maven Central [V]; usage with kotlin.test [U] |
| Mokkery (optional) | 3.6.0 | 2026-10-29 | KMP mocking compiler plugin. Prefer fakes | Maven Central [V] |
| Kover | 0.9.11 | 2026-09-29 | KMP coverage | Maven Central [V] |
| Konsist | 0.18.1 | 2026-10-10 | Architecture tests (kit-only rule) | GitHub releases [V] |
| Compose MP `ui-test` | **1.12.2** (1.13.0-beta01 pre) | 2026-10-15 / 10-23 | `runComposeUiTest` (v2) in `commonTest`, Experimental | kotlinlang compose-test (updated 2026-05-15) + Maven [V] |
| Jetpack `ui-test-junit4` | 1.12.1 (1.13.0-beta02 pre) | — | v2 rules `androidx.compose.ui.test.junit4.v2.*`; v1 deprecated | developer.android.com migrate-v2 (2026-10-28) [V] |
| Robolectric | **4.17** | 2026-09-10 | Supports SDK 37. SDK 36/37 need **Java 21** | GitHub releases, compatibility table [V] |
| Roborazzi | **1.77.0** | 2026-10-22 | Android (Robolectric), Compose Desktop, iOS (experimental) | GitHub + Maven [V] |
| ComposablePreviewScanner | 0.9.3 | 2026-08-17 | Scans `@Preview`s, incl. `commonMain` | GitHub [V] |
| Paparazzi | 2.0.0-alpha05.1 | 2026-09-28 | layoutlib 16.2.3, AGP 9; Java 21 to build; Android only | GitHub releases [V] |
| Compose Preview Screenshot Testing | plugin 0.0.1-alpha16 | page 2026-10-27 | **"no longer maintained"**, moved to AGP 9.5 test suites (experimental); no non-Android KMP | developer.android.com [V] |
| Maestro CLI | **2.12.0** | 2026-10-26 | Java 17+, installs a driver app on the device, `maestro mcp` for agents | GitHub API, CHANGELOG, docs [V] |
| UIAutomator | **2.4.0** | 2026-07-01 | New `uiAutomator { onElement { … } }` DSL | AndroidX release notes [V] |
| androidx.benchmark (macro + baseline-profile plugin) | **1.5.0** (1.6.0-alpha01) | 2026-09-09 (10-07) | AGP 9 without `newDsl=false`; partial KMP support; Perfetto v56 inside | AndroidX release notes [V] |
| profileinstaller | 1.4.1 | 2024-10-02 | Still current; required by Macrobenchmark (≥1.3) | AndroidX release notes [V] |
| androidx.tracing | **2.0.5** | 2026-10-21 | 2.0: in-process Perfetto API, KMP + host JVM, `traceCoroutine {}` | AndroidX release notes [V] |
| tracing-perfetto / -binary | 1.0.1 | 2025-11-19 | Composition tracing via adb broadcast; **never ship `-binary`** | AndroidX + Compose tracing doc [V] |
| compose runtime-tracing | 1.12.1; **1.13.0+** for recomposition tracing | — | Composition tracing needs API 30; recomposition tracing needs API 29+ and runtime-tracing 1.13 | Compose tracing doc (2026-10-28) [V] |
| JankStats (`metrics-performance`) | 1.0.0 | — | Field jank per frame with UI state | Google Maven [V] |
| navigationevent (+ `-testing`) | 1.2.0 | 2026-10-07 | `TestNavigationEventCallback`, `navigationEventInput` for predictive-back tests | AndroidX release notes [V] |
| Perfetto | **v59.1** | 2026-10-27 | Python trace_processor `perfetto` 0.59.1 (2026-10-26) | GitHub API, PyPI [V] |
| Ktor `ktor-client-mock` | 3.6.1 | 2026-10-29 | `MockEngine`, `MockEngine.Queue` | Maven + ktor.io (2026-10-05) [V] |
| OkHttp `mockwebserver3` | 5.5.1 | 2026-10-22 | JVM-only fake server, if needed for engine-level tests | Maven Central [V] |
| networknt json-schema-validator | 3.0.8 | 2026-10-02 | JVM; to validate fixtures against OpenAPI 3.1 schemas | Maven Central [V]; OAS 3.1 dialect handling [U] |
| goncalossilva `resources` | 0.16.2 | 2026-09-08 | Reads test resource files from `commonTest` on all targets | Maven Central [V] |
| AGP | 9.3.3 (per `kmp-cmp-status.md`); 9.5.0 latest | Oct 2026 | 9.3 adds `optimization {}` DSL + `keepRules` + R8 Configuration Analyzer | developer.android.com shrink-code (2026-08-27) [V] |
| diffuse (APK diff) | 0.3.0 | 2024-02-21 | Old but works on APK/AAB/JAR. Use `apkanalyzer` as the official tool | GitHub [V]; current compatibility [U] |

What the repo already uses: Robolectric 4.17 in the Flutter Android module (`android/app/build.gradle.kts`,
`unitTests.isIncludeAndroidResources = true`); 46 Kotlin JVM test files / 645 `@Test`, which carry over
(`map/packages-build.md`). [R]

---

## 2. The test pyramid for this app

### 2.1 Where the catalog rows go

The catalog `/home/eslam/Storage/Code/wt/native-plan/catalog/raw-*.jsonl` has 1,574 behaviour rows condensed from the
6,663 logic tests. Of these, 215 are marked `portable_e2e`. [R]

| `native_level` | Rows | Native home | Runs on | Typical tool |
|---|---|---|---|---|
| unit | 712 | `commonTest` (or `androidHostTest` for Android-only helpers) | VPS JVM | kotlin.test, coroutines-test, Turbine |
| contract | 268 | `commonTest` of the protocol modules | VPS JVM | MockEngine replay of recorded fixtures (§8) |
| integration | 445 | `commonTest` with fakes; Android parts (prefs, WorkManager, notifications, files) as Robolectric host tests | VPS JVM | kotlin.test, Robolectric 4.17 |
| migration | 56 | Robolectric for SharedPreferences/files; **instrumented on the emulator** for Keystore-backed `flutter_secure_storage`; plus the upgrade journey (§6.6) | VPS JVM + PC emulator | Robolectric, AndroidJUnit4, Maestro |
| screenshot | 63 (ui-kit 26, screens-nav 17, …) | Roborazzi from `@Preview`s and explicit captures | VPS JVM | Roborazzi |
| e2e | 30 | Maestro flows | PC emulator | Maestro + Python adb wrapper |
| `portable_e2e = true` | 215 (screens-nav 43, builtin-linux 42, android-platform 33, security-privacy 20, agents-backends 20, chat-genui 15, state-data 14, protocols 13, media-voice 6, l10n 4, ui-kit 3, packages-build 2) | Maestro parity flows, written against Flutter first | PC emulator | Maestro |

Robolectric cannot stand in for the Android Keystore: it has no real `AndroidKeyStore` provider. So decrypting the
`flutter_secure_storage` v11 data must be tested on the emulator. [U: confirm that Robolectric 4.17 has no usable
AndroidKeyStore shadow]

### 2.2 Tiers (what to run when)

This follows the Flutter-era check ladder (AGENTS.md §5–6), translated to Gradle:

| Tier | When | What | Where | Time budget [U] |
|---|---|---|---|---|
| T0 | while editing | one test class or row: `./gradlew :core:chat:jvmTest --tests '*STATE_DATA_18*'` | PC or VPS | seconds |
| T1 | branch ready | all JVM tests of touched modules + Roborazzi verify of touched modules + detekt | VPS (`tool/qa/vps_test` equivalent) | minutes |
| T2 | integration candidate | all JVM + Robolectric + `verifyRoborazzi*` + Konsist + lint + `assembleRelease` + R8 analyzer + APK size gate | VPS | target < 20 min |
| T3 | before any APK reaches the owner | emulator: `connectedAndroidTest` (small), Maestro smoke + parity subset on the **release** build, upgrade journey | PC emulator (one holder of the emulator lock) | 20–40 min |
| T4 | milestones / perf-sensitive changes | Macrobenchmark startup + streaming frame timing; regenerate Baseline/Startup Profiles | PC emulator; owner phone only on request | 15–30 min |

Rules that carry over from the owner's memory files:

- One agent at a time owns the emulator (`shared-emulator-contention`).
- No `adb install -d`.
- Restore font scale and density after a run.
- Builds and JVM suites go to the VPS ([R]: `build-vps`, `pc-capacity-ceiling`).
- Watch the VPS disk: it was 89% full on 2026-11-01.

---

## 3. Logic tests: kotlin.test, coroutines-test, Turbine

### 3.1 Choices

- **Framework: `kotlin.test` only.** In `commonTest`, `implementation(kotlin("test"))` adds the right per-platform
  adapter. `kotlin.test` has no parameterized tests; use loops over vector tables (as the Dart tests do), or
  `kotest-property` `checkAll` for parsers and tolerant decoders. Do not adopt the Kotest framework engine as a
  second runner. [V/U]
- **Time: `runTest` with `StandardTestDispatcher`.** This gives virtual time for the app's many timers (50 ms
  coalescing, 5 s polls, 15-minute stuck command, 60-minute sweeps, 6-hour wake-lock ceiling). Use `backgroundScope`
  for collectors that never end, and `Dispatchers.setMain` for ViewModels on the JVM (`architecture-stacks.md` §4.4). [V]
- **Flows: Turbine 1.2.1**, using `test { awaitItem() … }` and `turbineScope { … testIn(…) }` for several flows. [V]
- **Readable failures for agents: Kotlin power-assert** on `kotlin.test.assertEquals/assertTrue/assertNull` in test
  compilations only. A failing assertion then prints the values of every sub-expression, which saves agents a
  debugging round. [V]
- **Fakes over mocks.** This matches AGENTS.md ("tests assert behavior, not implementation") and Android guidance.
  Use Mokkery 3.6.0 only to verify call patterns at a seam where a fake would be pointless. [V/R]
- **Architecture tests: Konsist 0.18.1** in a `:quality` JVM module.
  - It enforces "kit only": outside `:ui:kit`, files may import only the allowlist in `docs/ux-system/kit-v2.md` §9
    (layout, scroll, semantics, focus).
  - It also enforces "UI talks to the domain gateway only".
  - Better still, make the rule structural: only `:ui:kit` depends on `compose.material3`, so a screen that imports
    it fails to compile. Konsist then covers what Gradle cannot (foundation APIs that are not on the allowlist). [V/U]
- **Coverage: Kover 0.9.11.** Use it for information only; the real gate is the catalog ratchet below.

### 3.2 Catalog-driven test names and a coverage ratchet

- Give every catalog row a stable id. The maps already use ids such as `STATE-DATA-18` and `BUILTIN-LINUX-10`.
- Name the native test after the row: `` fun `STATE-DATA-18 dispatch marker blocks auto resend`() ``.
  `architecture-stacks.md` proposes the same scheme.
- A small JVM tool (`tool/catalog/coverage.kts`) reads the JUnit XML reports and lists rows with no passing test. It
  fails T2 if the number of covered rows drops. This is the native equivalent of the Flutter G16 ratchet.
- Test-first means the tests for a slice are written before its code, from the rows alone. Write them in the same
  branch, red first, then green. Do not keep them as `@Ignore`d placeholders in `master`.

### 3.3 Sketch

```kotlin
// shared/core/chat/src/commonTest/kotlin/.../DispatchMarkerTest.kt
class DispatchMarkerTest {
    @Test
    fun `STATE-DATA-18 dispatch marker blocks auto resend`() = runTest {
        val gateway = FakeGateway()
        val queue = OfflineQueue(gateway, clock = testScheduler.asClock(), scope = backgroundScope)
        queue.enqueue(prompt("hello"))
        gateway.failNextSendAfterDispatch()            // server accepted, reply lost

        queue.states.test {
            assertEquals(QueueState.Sending, awaitItem().state)
            advanceTimeBy(5.seconds)
            assertEquals(QueueState.NeedsDecision, awaitItem().state)   // never auto-resend
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, gateway.sentPrompts.size)
    }
}
```

`testScheduler.asClock()` is a helper in `:core:testing`. It is not a library API.

---

## 4. Compose UI tests (harness now, the 7,967 widget tests later)

### 4.1 What runs where [V]

- **Common UI tests:** `runComposeUiTest` from `androidx.compose.ui.test.v2` in `commonTest`, with
  `@OptIn(ExperimentalTestApi::class)`.
  - They run on `jvmTest` (desktop), the iOS simulator, wasm, and Android **instrumented** (`withDeviceTestBuilder`).
  - JetBrains: "Currently, you cannot run common Compose Multiplatform tests using `android (local)` test
    configurations."
- **Android host tests:** Jetpack `createComposeRule` (v2) in `androidHostTest`, under Robolectric 4.17. Roborazzi
  is built on this. Robolectric needs `@Config(sdk = [...])`, and **Java 21 for SDK 36/37**. Two ways to satisfy that:
  - run the test task on a JDK 21 toolchain (the compile toolchain stays on 17); or
  - pin `@Config(sdk = [35])` for most tests and keep a JDK 21 job for the SDK 37 ones.
- **The v2 API changes timing.** Coroutines launched in composition are queued until the clock advances
  (`StandardTestDispatcher`).
  - Tests call `waitForIdle()` or `runOnIdle {}`, or `mainClock.advanceTimeBy(…)`.
  - `runComposeUiTest` already wraps `runTest` and syncs the clocks.
  - v2 forces `InputMode.Touch` unless a `ComposeUiTestConfig` says otherwise (1.13+).

### 4.2 Rules for this app's UI tests

- **Find nodes by user-visible text.** Use `testTag` only where no text exists: the AGENTS.md rule, unchanged.
- **Infinite animations must use the framework.** The running green glow, the live line and the Send→Stop ring must
  use `rememberInfiniteTransition` / `withInfiniteAnimationFrameNanos`, never a hand-written
  `while (true) { delay() }` loop. A hand loop keeps the test scheduler busy forever, which is the Compose version of
  the Flutter `flutter_animate` ban. [U: the test harness's `InfiniteAnimationPolicy` handling should be confirmed on
  CMP 1.12]
- **Reduced motion has to be testable.** The kit reads one `LocalMotion` (or `MotionDurationScale`) value that tests
  set, and the Android shell maps it from `Settings.Global.ANIMATOR_DURATION_SCALE` and the accessibility "Remove
  animations" setting. [U]
- **Frame text capped at 1.3×, reading text unlimited.** Test it with a `Density(fontScale = 2f)` wrapper and assert
  the measured text sizes of frame vs reading slots. This is a kit test, not a per-screen test.
- **RTL.** Run each kit test twice, the second time under
  `CompositionLocalProvider(LocalLayoutDirection provides Rtl)`. Run Arabic strings through Roborazzi (§5).
- **Predictive back.** Test the logic with `navigationevent-testing` 1.2.0:
  - `navigationEventInput` drives start, progress, cancel and complete;
  - `TestNavigationEventCallback` records the events.
  - The demo's fight between predictive back and the container transform becomes a test: progress events during an
    active row→page transform must not start the transform's back animation.
  - The gesture *feel* stays an emulator/phone check. [V/U]
- **Haptics.** Use a fake `HapticFeedback` through `LocalHapticFeedback` and assert the feedback types that were
  requested.
- **Max widths.** Use `onRoot().fetchSemanticsNode().size` at tablet widths (`DeviceConfigurationOverride` or a sized
  `Box`) and assert the kit part's hard cap.

### 4.3 Desktop target as the fast UI loop

A `jvm("desktop")` target from day 1 gives every agent an emulator-free UI loop: common UI tests and Roborazzi desktop
captures run on the VPS. This is consistent with `kmp-cmp-status.md` §9.

- [U] Whether Compose Desktop tests run on a display-less Linux VPS without Xvfb is **not confirmed**. Skiko renders
  offscreen in tests, and third-party tools report CI use without a display. Verify in slice 0, and fall back to
  `xvfb-run`.

---

## 5. Screenshot tests

### 5.1 Comparison

| | Roborazzi 1.77.0 | Paparazzi 2.0.0-alpha05.1 | Compose Preview Screenshot Testing |
|---|---|---|---|
| Engine | Robolectric Native Graphics (`@GraphicsMode(NATIVE)`), Compose Desktop Skia, iOS | layoutlib 16.2.3 | layoutlib; now AGP 9.5 "test suites" |
| KMP | Android + Desktop + iOS (experimental for the last two) | Android only; alpha05 supports the Android-KMP library plugin | "doesn't support non-Android KMP targets" |
| Status | Active: monthly releases, perf work, agent features | 2.0 still alpha | standalone plugin "no longer maintained"; replacement experimental |
| Preview-driven | yes: `generateComposePreviewRobolectricTests`, CMP previews in `commonMain` via `CommonComposablePreviewScanner` | via helper libs | yes (its whole model) |
| Interaction before capture | yes (full Robolectric + Compose test) | limited | no |
| Agent features | `-Proborazzi.dumpUiTree=true` writes a deterministic `.uitree.json` and an annotated image; skill via `npx skills add takahirom/roborazzi` | — | — |

**Recommendation: Roborazzi.** Use it for (a) generated kit previews and (b) explicit captures of screen states after
interactions. [V]

### 5.2 Determinism and the golden host

- Flutter lesson [R]: the VPS's AMD EPYC rasterized glyph edges about 0.2% differently from the PC's i7-9700, so
  goldens had to run on the PC.
- For the rewrite, **record and verify all goldens on the VPS**. It is always available and keeps the PC free for the
  emulator.
- The PC may run `compareRoborazzi*` for a local look but never records.
- Set `changeThreshold` to a small value (e.g. 0.001–0.005) only if same-host reruns show noise. Never use it to
  absorb cross-host differences. [U: measure in slice 0]
- Roborazzi 1.75 changed ordering, and its notes say to re-record after upgrading. Treat every Roborazzi bump as a
  re-record commit of its own. [V]

### 5.3 The matrix (keep it small)

- Kit components:
  - light/dark × LTR(en)/RTL(ar) at font scale 1.0;
  - one capture at fontScale 2.0, which proves the 1.3× frame cap and full scaling of reading text;
  - one tablet-width capture, which proves the max-width caps.
- Screens: 1–3 key states each, en light only, plus one Arabic dark capture.
- The 4,937 Flutter goldens are **not** a target to match. Ports go state by state, as the catalog's 63 screenshot
  rows ask.
- Use `@Preview(locale = "ar", fontScale = 2f, uiMode = …, widthDp = …)` parameters where the preview tester honours
  them. [U: check which `@Preview` parameters Roborazzi's CMP preview tester applies]

### 5.4 Gradle sketch

```kotlin
// ui/kit/build.gradle.kts (Android-KMP library)
plugins { id("io.github.takahirom.roborazzi") }
roborazzi {
    outputDir.set(file("src/screenshots"))           // goldens live next to the module
    generateComposePreviewRobolectricTests {
        enable = true
        packages = listOf("io.github.eslamasabry.ocm.ui.kit")
        useScanOptionParametersInTester = true
        // a custom tester class can apply locale/fontScale/RTL per preview
    }
}
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
// verify: ./gradlew :ui:kit:verifyRoborazziAndroidHostTest   (exact task name depends on the AGP-KMP variant naming) [U]
```

---

## 6. Black-box E2E that drives both apps: Maestro

### 6.1 Why Maestro fits the parity idea [V]

- Maestro drives the **compiled app** through the platform accessibility layer and installs a small driver app on the
  device. It needs Java 17+ on the PC.
- **Flutter.** Maestro reads Flutter's semantics tree. Text widgets match by text, and icons need `semanticLabel`.
  `Semantics(identifier:)` gives an `id:` (Flutter ≥ 3.19). Flutter `Key`s are invisible.
- **Compose.** Visible text and `contentDescription` match. A `testTag` is reachable as `id` only when
  `Modifier.semantics { testTagsAsResourceId = true }` is set high in the tree.
- **The common denominator is visible text and content descriptions.** These are what the owner sees, and what the
  existing Python device scripts already match (`tool/qa/bd7_device_ui.py` parses `uiautomator dump` `text` /
  `content-desc`) [R].
- **Text selectors are regexes.** They match visible text or the accessibility label (`contentDescription` on
  Android), so `.*Continue.*` gives partial matches.
- **Flows can share data.** A `runScript` JS file that sets `output.*` makes values usable in later selectors as
  `${output.strings.x}`.
- **Agents can drive it.** `maestro mcp` exposes Maestro to agents (`claude mcp add maestro -- maestro mcp`), and
  Maestro Viewer (2.6.0) shows the live hierarchy.
- **iOS comes for free later.** The same flows can drive the iOS app.

### 6.2 Selector strategy for flows that must pass on both apps

1. **Use strings, not literals.** A generator (`tool/e2e/gen_strings.py`) turns `lib/l10n/app_en.arb` and
   `app_ar.arb` into `e2e/strings.en.js` / `strings.ar.js` (`output.s = {newChat: "New chat", …}`).
   - Flows say `tapOn: ${output.s.newChat}`.
   - The native app ports the same keys to its resources, so one table serves both apps.
   - A copy edit then updates the flows automatically. A key rename shows up as a missing key when the strings are
     generated.
2. **Match rows with a contains pattern** (`.*${output.s.rowTitle}.*`). Flutter merges a ListTile's title and subtitle
   into one label, and Compose's merged semantics join texts differently. [U: check both dumps per kit row type]
3. **No ids in parity flows.** Native-only flows may use `id:` from `testTagsAsResourceId`, for lists without text.
4. **Turn animations off for parity runs:**
   `adb shell settings put global {animator_duration_scale,transition_animation_scale,window_animation_scale} 0`.
   Otherwise the running glow and streaming deltas keep the screen "unsettled", and every Maestro command waits for its
   timeout. Compose follows the animator scale. The Flutter app's reduced-motion path follows "Remove animations".
   Keep one small smoke flow with animations on.
5. **Wait for streaming, don't sleep.** Use `extendedWaitUntil: visible: ${output.s.replyDone}` with a timeout, plus
   the deterministic mock model (§6.4).
6. **Run in English by default; an Arabic subset checks RTL.** Set the locale with Maestro or `adb shell cmd locale`.
   Maestro 2.8.0 fixed setting the locale after `start-device`. [V]

### 6.3 Example flow

```yaml
# e2e/flows/screens-nav/SCREENS-NAV-01-cold-start-opens-chats.yaml
appId: io.github.eslamasabry.opencode_mobile
tags: [parity, screens-nav, smoke]
---
- runScript: ../../strings.en.js
- launchApp:
    clearState: false          # never clear: the built-in Linux lives in app data
- extendedWaitUntil:
    visible: ${output.s.chatsTitle}
    timeout: 15000
- assertVisible: ".*${output.s.newChat}.*"
- takeScreenshot: SCREENS-NAV-01
```

Run it with `maestro test --format junit --output e2e-report.xml e2e/flows -e APP=flutter|native`. Check the exact
report flags against `maestro test --help` 2.12.0. [U]

A Python wrapper (`tool/e2e/run.py`) holds the emulator lock. It installs the build under test (checking
`dumpsys package` versionCode first), sets animations, starts the fixture servers, runs Maestro, and then runs the adb
checks that Maestro cannot do: `ps` process trees for proot services, `run-as` / `adb root` file checks, logcat
patterns and `dumpsys notification`.

### 6.4 Fixture servers that both apps can reach (reuse what exists) [R]

- **Real OpenCode 2 at the pinned version (2.1.1) on the PC, with a deterministic model.**
  `tool/qa/issue87/mock_llm.py` is an OpenAI-compatible endpoint: first token after 300 ms, then 5-word chunks every
  40 ms for about 20 s. It plugs into OpenCode through `opencode.json` and gives identical streaming for E2E and for
  frame-timing benchmarks.
- **Codex.** `tool/qa/codex_fixture/fixture_server.py` is a JSON-RPC Codex app-server fixture.
- **Replay server.** A small JVM fixture-replay server (Ktor server) serves the recorded contract fixtures (§8) for
  failure scenarios a real server cannot produce on demand: 401/503, a stuck permission, a 2.0.29-style 400 on the
  pending list (`oc2-stuck-tools-root-cause` memory).
- The emulator reaches the PC at `10.0.2.2`. Follow the AGENTS.md traps for test servers:
  - kill them only by exact PID, never by pattern;
  - never print the per-run password.

### 6.5 Do this before native code exists

1. Write flows for the 30 `e2e` rows and the 215 `portable_e2e` rows, grouped by area.
2. Run them against the current Flutter release build (signed with `1DE5BF08…D60C`) on `OC_API37`.
3. Commit the green list and the screenshots as the **parity baseline** (`docs/qa/native-parity-<date>/README.md`, per
   the audit-trail rule).
4. A native slice is "at parity" when its flows pass unchanged.
5. A flow that fails on Flutter because of a Flutter bug is marked `flutter-known-bad` and must pass on native.

### 6.6 The day-1 upgrade journey (Flutter → native, same package, same key)

This is the highest-risk path. Data loss on the owner's phone cannot be undone: reinstalling Flutter would be a
downgrade, which needs an uninstall.

1. **Seed once.** Use a fresh `OC_API37` (google_apis x86_64, so `adb root` works [R]).
   - Install the last Flutter build.
   - Run `e2e/seed/*.yaml`: add an OpenCode 2 server with a password, add a Paseo profile, change settings in every
     group, queue a prompt offline, save a draft with a photo, schedule a prompt, install the built-in Linux, download
     a small voice pack.
   - Save the emulator state: `adb emu avd snapshot save flutter-seeded`.
2. **Upgrade.**
   - `adb emu avd snapshot load flutter-seeded`.
   - `adb install -r native.apk`. Its versionCode must be above every Flutter build; `map/packages-build.md` proposes
     10000+.
   - Launch the app.
3. **Verify.** Run the `e2e/upgrade/*.yaml` flows:
   - the server connects without re-entering the password;
   - the settings are the same;
   - the queued prompt is still queued, not resent;
   - the draft and photo are back;
   - the schedule fires;
   - Linux starts **without reinstalling**;
   - the voice pack is still "Ready".
4. **File checks** with `adb root`:
   - the native stores exist;
   - `shared_prefs/FlutterSharedPreferences.xml` and the secure-storage prefs are handled as the migration design says
     (kept read-only for N launches, then removed);
   - `files/linux/ubuntu.ready` is unchanged;
   - the rootfs inode count is unchanged.
5. **Interrupted migration.** Use a test-only hook, available only in the `benchmark`/`qa` build type, to stop at
   step k of the migration (or `am force-stop` after a logcat marker). Relaunch and verify again.
6. **Also test:** a fresh install (no Flutter data); a downgrade attempt (must be refused); and two sequential native
   updates.

The owner's phone gets the upgrade only after this passes on the emulator (`test-on-emulator-not-phone`).

---

## 7. UIAutomator 2.4 (where Maestro stops)

The new API [V]:

- `uiAutomator { startApp(pkg); onElement { textAsString() == "Submit" }.click(); activeWindow().waitForStable() }`
- `onElement`/`onElements`/`onElementOrNull` with a built-in 10 s wait
- `watchFor(PermissionDialog) { clickAllow() }`
- window APIs, `takeScreenshot()` and `ResultsReporter`

Since benchmark 1.5.0, `MacrobenchmarkScope` extends `UiAutomatorTestScope`, so benchmarks and profile generators use
the same DSL.

Use it, in the instrumented `:androidApp` test source set or the `:benchmark` module, for:

- **Home-screen widgets:** pin, resize and tap through to the conversation. The ZTE launcher traps stay owner-phone
  checks.
- **The notification shade:** the finished-reply notification from the short foreground service, permission
  notifications ("never carry secrets"), and actions.
- **System sheets:** the share target, the "Save to" document picker, and the PackageInstaller confirmation for the
  in-app updater.
- **Macrobenchmark journeys** and **Baseline Profile generation** (§9).

Compose test tags reach UIAutomator through `By.res(tag)` / `viewIdResourceName` only with `testTagsAsResourceId`.
Set it once on the app root in benchmark and qa builds. Leaving it on in release costs nothing visible, but the tags
are then readable by any accessibility service. [V/U]

---

## 8. HTTP contract tests with recorded fixtures

### 8.1 What exists [R]

- `contracts/` holds OpenAPI **3.1.0** snapshots:
  - `opencode2-openapi-beta-17823.json` (99 paths);
  - `opencode2-openapi-beta-18600.json` (114 paths);
  - Gas City supervisor (127 paths).
- `test/fixtures/api2/` has 24 recorded fixtures, including `events.sse` (raw `data:` frames and `: heartbeat`
  comments) and `messages_2_1_1.json`. Other fixture folders cover `paseo`, `a2a`, `genui`, `host_agent_providers` and
  `factsbook`.
- The OpenCode SDK for v1 is generated and pinned by SHA-256.
- The phone pins OpenCode 2 **2.1.1**, but the newest OpenAPI snapshot is a beta (18600). [U: refresh for 2.1.1]

### 8.2 Recommended design

1. **One fixture format.** `contracts/fixtures/<server>/<version>/<scenario>/NNN-<method>-<path>.json`, holding
   `{request: {method, path, query, headers (allowlist), body}, response: {status, headers (allowlist), body | bodyFile}}`.
   - SSE streams are raw `.sse` files.
   - Pacing goes into **SSE comment lines** (`: t=+40ms`). These are legal SSE and ignored by any parser, so the
     replayer can reproduce timing without a side file. [U, a design choice]
2. **A recorder, not hand-written JSON.**
   - A JVM CLI (`tool/contracts/record`) runs a recording reverse proxy (Ktor server + OkHttp client) between a
     scripted client and a real server: `opencode2 serve --hostname 127.0.0.1`, Paseo, the Codex app-server, Gas City.
   - Scenarios are scripted calls, or the Maestro flows pointed at the proxy.
   - **Redaction is mandatory.** Drop `Authorization` and cookies, and scrub API keys from `/config/providers` bodies
     (an AGENTS.md security invariant).
   - Fail the recording if any value matches the per-run password or a provider key pattern.
3. **Replay in `commonTest` with Ktor `MockEngine`.**
   - A `FixtureEngine(dir)` matches requests (method + path + selected query) and returns the recorded status, headers
     and body.
   - Strict mode fails on unmatched requests and on leftover expected requests, like the "call chain" mode
     `reuseHandlers = false`.
   - SSE replay writes into a `ByteChannel` from a coroutine, using `delay()` for the `t=` comments. Under `runTest`,
     those delays are virtual time. [V for MockEngine API; U for the SSE pattern, since Ktor has no SSE MockEngine example]
4. **Reading fixtures from `commonTest`.** Use `com.goncalossilva:resources` 0.16.2 (all targets), or pass the
   absolute fixture directory as a system property/env var on JVM and read it with kotlinx-io. Keep the fixtures in
   the repo, not in Compose resources. [V/U]
5. **Schema check, JVM-only, in T2.**
   - Validate every recorded response body against the matching operation's response schema in the pinned OpenAPI 3.1
     file (networknt json-schema-validator 3.0.8, draft 2020-12).
   - On a server bump: re-record, re-snapshot `/doc`, diff both, and let the contract tests show what changed.
   - [U: confirm networknt resolves OpenAPI-3.1 `$ref`s into `components/schemas` with the OAS dialect; else
     pre-bundle the schema with swagger-parser 2.1.48]
6. **Byte-level vectors stay separate.** The SSE parser port (16 MiB frame cap, fragmented UTF-8, CRLF/CR/LF, comments,
   `retry:`) is tested with the 9 vectors from `api2_sse_test.dart` plus kotest-property fuzzing (random chunk
   boundaries must give identical events). This is pure `commonTest`, with no HTTP (`network-data.md` §3.3).
7. **What not to use.**
   - **Pact** needs the provider to verify the contract, and upstream OpenCode, Paseo and Codex will not.
   - **WireMock 4** is still beta (4.0.0-beta.41) and JVM-only. Our own replayer is about 200 lines and works in
     common code.
   - OkHttp `mockwebserver3` 5.5.1 is fine for the few engine-level tests: TLS pinning with a self-signed leaf,
     `retryOnConnectionFailure(false)` proofs, redirect refusal. [V/U]

### 8.3 Sketch

```kotlin
class FixtureEngine(private val scenario: Scenario) {
    val engine = MockEngine.config {
        reuseHandlers = false
        scenario.exchanges.forEach { ex ->
            addHandler { req ->
                ex.assertMatches(req)                      // method, path, query allowlist, body shape
                when (val b = ex.response.body) {
                    is Body.Json -> respond(b.text, HttpStatusCode.fromValue(ex.response.status), ex.headers())
                    is Body.Sse -> respond(sseChannel(b.frames), HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "text/event-stream"))
                }
            }
        }
    }
    private fun sseChannel(frames: List<SseFrame>): ByteReadChannel = ByteChannel(autoFlush = true).also { ch ->
        scenario.scope.launch {                           // backgroundScope of runTest → virtual time
            for (f in frames) { delay(f.delayMs); ch.writeStringUtf8(f.raw) }
            ch.flushAndClose()
        }
    }
}
```

The `writeStringUtf8` / `flushAndClose` names follow the Ktor 3.x `ByteChannel` API; check them at compile time
against 3.6.1. [U]

---

## 9. Performance: Macrobenchmark, Baseline and Startup Profiles, R8, Perfetto

### 9.1 Macrobenchmark (androidx.benchmark 1.5.0) [V]

- **Setup.** A separate `com.android.test` module (`:benchmark`) with `targetProjectPath = ":androidApp"`. The target
  app is `profileable`, non-debuggable and minified, and includes profileinstaller ≥ 1.3. Add a `benchmark` build type
  with `initWith(release)` and `matchingFallbacks += "release"` for the KMP library modules.
- **Signing.** The benchmark build type is installed on the emulator under the same applicationId. Sign it with the
  same `1DE5BF08…` key, or use an `applicationIdSuffix` so it never collides with the owner's install. Prefer the
  suffix for routine runs. [U: the built-in Linux and migration paths key off package-private dirs, so the suffix build
  starts clean, which is fine for startup and streaming benchmarks]
- **Metrics:** `StartupTimingMetric` (cold/warm), `FrameTimingMetric` (`frameOverrunMs` preferred, `frameCount`),
  `TraceSectionMetric` for our own sections (e.g. `chat.applyDelta`), and `ArtMetric` (JIT and class-load detection,
  1.4.0+).
- **Emulator.**
  - Google discourages emulator numbers and Macrobenchmark errors on emulators unless suppressed
    (`androidx.benchmark.suppressErrors=EMULATOR`).
  - The VPS cannot host an emulator: no `/dev/kvm` and no vmx/svm flags (checked 2026-11-01 [R]).
  - So use the PC emulator for **relative** comparisons only: same AVD, same host load, quiet PC, and N ≥ 10
    iterations.
  - Absolute numbers come from the owner's NX721J, run only when the owner allows it (`owner-phone-debug-allowed`).
  - API 34+ keeps compilation state between runs, so use `OC_API37`.
- **Journeys** (this app's smoothness claims, stated as numbers):
  1. Cold start to the Chats list: `StartupTimingMetric`, `StartupMode.COLD`, `CompilationMode.Partial()` vs `None()`.
  2. **Streaming reply:** open a conversation on the mock-model server (§6.4), send "story", and measure
     `FrameTimingMetric` over the ~20 s stream with auto-scroll on. This is the owner's main "smoother" claim.
  3. Fast fling through a 5,000-part transcript, using a fixture conversation.
  4. Row→conversation container transform and predictive-back cancel, for frame overrun during transitions.
  5. Opening the composer with the keyboard (IME animation).
- **Budgets** [U, set after the first measurements]:
  - cold start TTID p50 ≤ 300 ms on the emulator (the demo measured ~0.25 s);
  - streaming p95 `frameOverrunMs` ≤ 0 (no missed deadlines) and p99 ≤ 4 ms;
  - keep `tool/release/cold_start.py` (`am start -W`, median ≤ 3000 ms) as a quick check, but tighten its budget.

```kotlin
@RunWith(AndroidJUnit4::class)
class StreamingBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    @Test fun streamReply() = rule.measureRepeated(
        packageName = PKG, metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(), iterations = 10, startupMode = StartupMode.WARM,
        setupBlock = { uiAutomator { startApp(PKG); onElement { textAsString() == "Fixture chat" }.click() } },
    ) {
        uiAutomator {
            onElement { viewIdResourceName == "composer-input" }.setText("story")
            onElement { contentDescription == "Send" }.click()
            onElement(30_000) { textAsString()?.startsWith("Night 4 passed") == true }   // mock_llm's last paragraph
        }
    }
}
```

### 9.2 Comparing the Flutter and native builds on the same journey

Macrobenchmark can target any installed package. However, Flutter draws into its own surface, so the
`FrameTimingMetric` slices (Choreographer/RenderThread) may not describe Flutter frames. [U]

For an apples-to-apples comparison, record a **Perfetto** trace of the same journey for both apps and compare the
SurfaceFlinger **FrameTimeline** jank tables (`actual_frame_timeline_slice` by layer, `jank_type`), which see every
layer's buffers. [U: verify Flutter layers appear with usable names] This turns "feels smoother" into a number for the
switch decision.

### 9.3 Baseline Profiles and Startup Profiles [V]

- **Plugins.** Use `androidx.baselineprofile` in a producer `com.android.test` module (it can be the same `:benchmark`
  module or a separate `:baselineprofile`) and in the `:androidApp` consumer.
  - 1.5.0 needs no `newDsl=false` on AGP 9.
  - KMP library modules have *partial* support: `baselineProfile { variants { androidMain { from(project(":x")) } } }`.
    It is simpler to generate one profile for the app, which covers all library code exercised at runtime. [U on
    whether per-module profiles add anything here]
- **Startup Profiles.**
  - Add `includeInStartupProfile = true` for the cold-start journey. The output goes to
    `src/release/generated/baselineProfiles/startup-prof.txt`.
  - AGP applies DEX layout optimization (on by default since 8.3), which needs R8 with all optimizations.
  - Google claims 15–30% faster startup than Baseline Profiles alone. Startup code should fit in `classes.dex`.
- **Devices.** Generation runs on a rooted device or **any API 33+ device or emulator without root**. Use the PC's
  `OC_API37`. A Gradle Managed Device needs `systemImageSource = "aosp"`, and KVM, so not on the VPS.
- **Profile journeys:**
  - cold start → Chats;
  - open a conversation, stream a reply, scroll;
  - open Settings and two sub-pages;
  - open the composer with the keyboard;
  - open a file view;
  - the This-phone page.
  Keep them short, and add new screens as they land.
- **Sideloading reality.**
  - Outside Play, "users… don't see the benefits until background dexopt runs, which is likely overnight". Cloud
    Profiles are Play-only.
  - ProfileInstaller writes the profile at first launch, and ART compiles it later.
  - Startup Profiles (DEX layout) are baked into the APK, so they help from the first run.
- **Make the profile state visible.** Add `ProfileVerifier.getCompilationStatusAsync()` to *This phone › Details* and
  to the problem report (`isCompiledWithProfile`, `profileInstallResultCode`).
- **Manual checks:**
  - `adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE <pkg>/androidx.profileinstaller.ProfileInstallReceiver`
  - `adb shell cmd package compile -f -m speed-profile <pkg>`
  - `adb shell dumpsys package dexopt | grep -A1 <pkg>`
  - reset on API 34+: `cmd package compile -f -m verify <pkg>` + `pm art clear-app-profiles <pkg>`
- **Spike (unknown): DexMetadata through the in-app updater.**
  - Play compiles at install from a `.dm` file. The manual docs show `adb install-multiple app.apk app.dm`
    (`[reason=install-dm]`), with a note that the documented sideload workflow covers Android 9–11.
  - Planned updater design (`app-updates-two-layers`): the in-app updater uses `PackageInstaller`, so it could write
    a `base.dm` next to the APK in the session.
  - If Android 12–17 accept it, updated users get compiled code on the first launch after each update.
  - Test on `OC_API37`: does `dumpsys package dexopt` show `install-dm`? Is a signature or fs-verity required?

```kotlin
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()
    @Test fun startupAndChat() = rule.collect(packageName = PKG, includeInStartupProfile = true) {
        uiAutomator {
            startApp(PKG)
            onElement { textAsString() == "Chats" }
            onElement { textAsString() == "Fixture chat" }.click()
            activeWindow().waitForStable()
        }
    }
}
```

### 9.4 R8 (AGP 9.x) [V]

- **Full mode** has been the default since AGP 8.0.
- **AGP 9.0:**
  - optimized resource shrinking is on with `isShrinkResources`;
  - `proguard-android.txt` is gone;
  - global options such as `-dontobfuscate` in **library consumer rules are filtered out**;
  - Kotlin null checks are optimized.
- **AGP 9.3:** the `optimization { enable = true }` DSL in the release build type (code + resources, default Android
  keep rules opt-out), with keep rules in `src/<variant>/keepRules/*.keep`. KMP consumer rules no longer need
  `publish = true`.
- **R8 Configuration Analyzer** (AGP ≥ 9.3.0-alpha05): `./gradlew :androidApp:analyzeReleaseR8Config` writes
  `build/reports/r8/r8-config-analyzer-release.html` with shrink, optimize and obfuscate scores, the broadest rules,
  and unused or duplicate rules. Put the scores in the T2 report and ratchet them.
- **Known traps to repeat or avoid in this app** [R]:
  - **WorkManager + Room** reflection: the `WorkDatabase_Impl` keep, and the Worker `(Context, WorkerParameters)`
    constructors. Keep `tool/qa/verify_scheduled_prompt_apk.py` and `verify_widget_release_rules.py`, which inspect the
    real DEX.
  - **kotlinx.serialization** ships its own rules. Avoid `serializer<T>()` lookups by reflection on generic types, and
    never add kotlin-reflect.
  - **Ktor:** construct the engine explicitly (`HttpClient(OkHttp)`) so no `ServiceLoader` lookup is needed.
  - **Compose tracing strings** can be stripped with `-assumenosideeffects` on `ComposerKt`. Google notes that keeping
    them means the profiled APK is the shipped one. Keep them: they cost little and keep Perfetto useful.
- **The R8'd APK is what gets tested.** T3 (Maestro, instrumented smoke) runs on the minified build; debug-only
  testing does not catch R8 breakage. Keep the per-build mapping files (43 MB each today) for retrace, as `build-vps`
  does.

```kotlin
// androidApp/build.gradle.kts (AGP ≥ 9.3)
android {
    buildTypes {
        release { optimization { enable = true } }        // + src/main/keepRules/app.keep
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".bench"
        }
    }
}
```

### 9.5 Compose performance hygiene (tooling side)

- **Compiler reports:** `composeCompiler { reportsDestination = …; metricsDestination = … }` in a `-Pcompose.reports`
  build. Agents check the stability of state classes (strong skipping is the default). `kmp-cmp-status.md` covers the
  Kotlin 2.4 runtime stability inference.
- **Composition tracing:** add `androidx.compose.runtime:runtime-tracing` to the benchmark build only. For terminal
  capture, add `tracing-perfetto` + `tracing-perfetto-binary` to the `:benchmark` module, **never to the app**.
  Benchmarks pass `androidx.benchmark.fullTracing.enable=true`. Recomposition tracing needs runtime-tracing 1.13.0+,
  so it comes with CMP 1.13.
- **Own trace sections:** `androidx.tracing` 2.0.5 `trace("chat.applyDelta") {}` around the hot paths: SSE frame
  decode, delta merge, markdown layout, transcript diff. The 2.0 in-process API can also record a short ring buffer
  for problem reports. Keep it off by default and never put message text or paths in slice names (privacy). [V/U]
- **Field jank:** JankStats 1.0.0, with the UI state (`screen=chat`, `streaming=true`) attached. Show a "smoothness in
  the last hour" line in the Performance report. This replaces the Flutter-era frame stats. [V/U]

### 9.6 Perfetto [V/U]

- Versions: Perfetto v59.1 (2026-10-27) and Python `perfetto` 0.59.1.
- Keep one config in `tool/perf/oc.pbtxt`:
  - `linux.ftrace` (sched, freq, idle, power; atrace categories `gfx view input am wm dalvik` + the app);
  - `android.surfaceflinger.frametimeline`;
  - `linux.process_stats` (catches proot process trees and orphans: the 10-27 hot-phone class of bug);
  - `android.power` / thermal;
  - `track_event` for androidx.tracing.
- Record: `adb shell perfetto -c - --txt -o /data/misc/perfetto-traces/oc.pftrace < tool/perf/oc.pbtxt`, then pull
  the file.
- Analyse with `tool/perf/*.sql` through trace_processor:
  - janky frames by layer;
  - main-thread long slices > 16 ms during streaming;
  - CPU time of `libproot*` descendants;
  - wake-lock spans.
- Store the numbers as JSON next to the APK (as `cold_start.py` does today) so builds can be compared.

---

## 10. APK size

Measured today [R] (`map/packages-build.md`):

- the universal Flutter APK is 108 MB;
- the per-ABI arm64 APK is about 36 MB;
- arm64 libs include `libapp.so` 12.9 MB (compressed), `libonnxruntime.so` 8.0 MB, `libflutter.so` 6.7 MB,
  `libaiteam_engine.so` 2.4 MB and ML Kit 2.1 MB.

The demo was 2.5 MB with no native libraries.

### 10.1 Plan

- **ABI splits in Gradle** (replacing `split_apk_by_abi.py`): `splits { abi { isEnable = true; reset();
  include("arm64-v8a", "x86_64"); isUniversalApk = true } }`.
  - `armeabi-v7a` is an owner decision: today it exists only for remote-client use, and gets no proot or engine.
  - **versionCode:** Google's per-ABI offsets (`abiCode * 1000 + versionCode`) are a Play rule. This app ships on
    GitHub/Obtainium and the release tooling expects **one** versionCode for all files. Keep one, i.e. don't add the
    `onVariants` override. [V for Google's guidance; R for the repo's convention]
  - Splits are signed by AGP. The VPS build → PC re-sign path must re-sign all outputs and check the digest
    `1DE5BF08…` on each.
- **Keep `packaging.jniLibs.useLegacyPackaging = true`.** proot, its loader, talloc, shmem and the Rust
  `libaiteam_*` binaries must be extracted to `nativeLibraryDir` to be executable. Keep `keepDebugSymbols` for
  `libaiteam_*` and the hash check against `aiteam-engine-manifest.json`.
- **R8 + resource shrinking** (§9.4). Compose resources: only en/ar/zh/es/ja/pt/ru strings and the fonts actually used
  (Geist in the demo).
- **Replace ML Kit with a FOSS QR decoder** (owner decision, `media-voice.md`). This saves about 2–3 MB.
- **Voice stays bundled** (owner decision): sherpa-onnx + ONNX Runtime ≈ 9.8 MB compressed. [U] Moving the native
  runtime to a download is **not** recommended. Whether `System.load` from app-private storage is allowed under
  targetSdk ≥ 29 W^X rules plus the Android 14+ dynamic-code-loading rules was not confirmed, and it would add a
  failure mode.
- **"On-demand assets" outside Play.** Play Asset Delivery and Play Feature Delivery need Play. The equivalent here is
  the existing pattern: resumable `Range` downloads from GitHub release assets with SHA-256 pins, atomic install and
  rollback. It is used for voice models (104–375 MB), Ubuntu Base, Node, OpenCode, AI Team binaries
  (`aiteam-assets-N`) and Paseo.
  - Consolidate it into one shared KMP download module.
  - Test it with MockEngine against the catalog's Range rows: 206 append, 200 restart, 416 complete, checksum mismatch
    deletes.
- **Gates (T2):**
  - Keep `tool/release/check_apk_size.py`, pointed at the arm64 APK. Start budgets at arm64 ≤ 22 MB and universal
    ≤ 45 MB [U], with growth ≤ 1 MiB per release unless the release notes explain it.
  - Report `apkanalyzer apk compare` (official, cmdline-tools) or `diffuse` 0.3.0 between builds in the T2 report.
  - Keep `tool/qa/verify_phone_engine_apk.py`.
- **Estimate** [U]: arm64 ≈ 3 MB app + 3.1 MB Rust/proot + 9.8 MB voice + ~1 MB QR ≈ 17–20 MB.

---

## 11. Machine plan and commands

| Job | Machine | Command (target names to confirm with the final module layout) |
|---|---|---|
| Logic, contract, Robolectric, Konsist, Kover | VPS | `./gradlew jvmTest testAndroidHostTest :quality:test --parallel` (avoid `build`/`assemble` in multi-target modules, per `kmp-cmp-status.md`) |
| Screenshot record/verify | VPS only | `./gradlew recordRoborazzi… / verifyRoborazzi…` |
| Release APK + R8 analyzer + size gate | VPS → PC re-sign | `:androidApp:assembleRelease :androidApp:analyzeReleaseR8Config` then sign + `check_apk_size.py` |
| Instrumented tests (migration with Keystore, widgets, notifications) | PC `OC_API37` | `./gradlew :androidApp:connectedReleaseAndroidTest` (testBuildType release, as today) |
| Maestro parity, smoke and upgrade journey | PC `OC_API37` + fixture servers on the PC | `tool/e2e/run.py --app native|flutter --tags parity` |
| Baseline/Startup Profile generation | PC `OC_API37` | `./gradlew :androidApp:generateBaselineProfile` (commit the generated `baseline-prof.txt` and `startup-prof.txt`) |
| Macrobenchmark | PC `OC_API37` (relative); owner phone (absolute, on request) | `./gradlew :benchmark:connectedBenchmarkAndroidTest -P android.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR` |
| Perfetto analysis | PC | `python3 -I tool/perf/analyze.py trace.pftrace` (trace_processor 0.59.1) |

Other notes:

- Gradle on the PC: keep workers low (`kmp-cmp-status.md` §12.5).
- Robolectric test JVMs need about 1–1.5 GB each, so `maxParallelForks` ≈ 3–4 on the VPS (23 GB) and 1–2 on the PC.
  [U]
- **JDK:** compile on 17 (AGENTS.md), and run Robolectric SDK 36/37 test JVMs on 21 via Gradle toolchains. Install
  Temurin 21 on the VPS and PC. Paparazzi would need 21 even to build, which is one more reason not to use it.

---

## 12. Recommendations for this app (in order)

1. **Slice 0 (harness, before features):**
   - a `:core:testing` module (fakes, `TestClock`, `TestDispatchers`, `FixtureEngine`);
   - power-assert;
   - the catalog-coverage ratchet tool;
   - Roborazzi on `:ui:kit` with one recorded golden from the VPS;
   - a desktop `jvmTest` UI test proving headless operation on the VPS;
   - a JDK 21 Robolectric run at SDK 37;
   - `:benchmark` with one startup benchmark and the profile generator;
   - R8 analyzer output in the T2 report.
2. **Parity baseline now:**
   - the string-table generator from the ARBs;
   - Maestro flows for the 30 `e2e` + 215 `portable_e2e` rows;
   - run them on the current Flutter release build;
   - record `docs/qa/native-parity-2026-11-xx/`.
   This is the most useful thing to do while Flutter is frozen and the native code does not exist yet.
3. **Upgrade journey first among the E2E suites:** seed snapshot, `install -r`, verify flows, file checks, interrupted
   migration. Run it on every candidate that touches migration code and before every owner APK until the switch ships.
4. **Contract recordings:**
   - record OpenCode 2 **2.1.1** (the pinned version), Paseo 0.11.2, the Codex app-server and Gas City scenarios with
     the redacting proxy;
   - refresh the OpenAPI snapshot for 2.1.1;
   - port the 268 contract rows as replay tests;
   - add the networknt schema check.
5. **Streaming smoothness as a number:**
   - the mock-model streaming Macrobenchmark plus Perfetto FrameTimeline comparison of Flutter vs native on the same
     journey;
   - record the result for the switch decision;
   - set budgets after three runs.
6. **Profiles:**
   - generate Baseline + Startup Profiles at each milestone;
   - surface `ProfileVerifier` in Details;
   - spike `.dm` delivery through the in-app updater.
7. **Size:**
   - Gradle ABI splits with one versionCode;
   - FOSS QR;
   - a size gate on the arm64 file;
   - keep the engine/proot hash checks.
8. **Later, the 7,967 widget tests:**
   - common `runComposeUiTest` on desktop for behaviour;
   - Robolectric host tests for Android-specific behaviour;
   - Roborazzi for looks;
   - port them area by area, alongside each screen's slice.

**Agent brief lines to reuse:**

- Tests find by visible text.
- No hand-written infinite loops in UI code.
- Record goldens only on the VPS.
- Never `adb install -d` and never `pm clear` on the shared emulator.
- Benchmarks run on the release-like build only.
- Never put secrets or message text in trace names, fixtures or logs.

---

## 13. Unknowns (verify in slice 0 or before relying on them)

1. Do Compose Desktop `runComposeUiTest` and Roborazzi desktop work on the display-less VPS, or is `xvfb-run` needed?
2. Exact Gradle task names for Roborazzi and host tests under the AGP-KMP library plugin (`testAndroidHostTest`
   naming, Roborazzi's variant tasks).
3. Which `@Preview` parameters (locale, fontScale, uiMode, widthDp, RTL) Roborazzi's CMP preview tester applies; a
   custom tester may be needed.
4. Same-host determinism of Roborazzi on the VPS (AMD EPYC) across reruns, and whether any threshold is needed.
5. How Flutter vs Compose merged rows appear in `uiautomator dump`, which decides the contains-regex patterns; and
   whether Maestro's settle-wait misbehaves with infinite animations even at animator scale 0.
6. Whether `FrameTimingMetric` reports anything meaningful for the Flutter app (likely not), and whether FrameTimeline
   layer names make a fair Flutter-vs-native comparison possible.
7. **`.dm` DexMetadata through `PackageInstaller` sessions on Android 12–17** for sideloaded updates. The docs only
   describe Android 9–11 for the manual workflow.
8. Whether Robolectric 4.17 can emulate `AndroidKeyStore` well enough for `flutter_secure_storage` v11 decrypt tests.
   Assumed **no**, so they run instrumented.
9. Whether networknt 3.0.8 handles OpenAPI 3.1 component `$ref`s directly, or whether bundling with swagger-parser is
   needed first.
10. Whether OpenCode 2 2.1.1 serves its OpenAPI document (the `/doc` route) for a fresh snapshot. The repo's newest
    snapshot is beta-18600.
11. Whether loading native libraries (`System.load`) from app-private storage is allowed for this targetSdk. This only
    matters if anyone proposes downloading the voice runtime.
12. The `InfiniteAnimationPolicy` behaviour of the CMP 1.12 test harness with `rememberInfiniteTransition`. Confirm with
    one kit test of the composer glow.
13. Time budgets in §2.2 and the size and frame budgets in §9–10 are estimates. Replace them with the first
    measurements.
14. Maestro 2.12 report flags (`--format junit`, `--output`) and `runScript` relative paths. Confirm with
    `maestro test --help`.

---

## 14. Sources (fetched 2026-11-01 unless stated)

- Kotlin Multiplatform, testing Compose UI (`runComposeUiTest`, Android-local limitation), updated 2026-05-15:
  https://kotlinlang.org/docs/multiplatform/compose-test.html
- Compose v2 testing APIs (StandardTestDispatcher, deprecation of v1), updated 2026-10-28:
  https://developer.android.com/develop/ui/compose/testing/migrate-v2
- Compose UiAutomator interop (`testTagsAsResourceId`), updated 2026-10-28:
  https://developer.android.com/develop/ui/compose/testing/interoperability
- Kotlin power-assert, updated 2026-08-20: https://kotlinlang.org/docs/power-assert.html
- Turbine releases: https://github.com/cashapp/turbine/releases
- Roborazzi README and releases (1.73–1.77): https://github.com/takahirom/roborazzi ,
  https://github.com/takahirom/roborazzi/releases
- Paparazzi releases: https://github.com/cashapp/paparazzi/releases
- Compose Preview Screenshot Testing (deprecation notice), updated 2026-10-27:
  https://developer.android.com/studio/preview/compose-screenshot-testing
- Screenshot testing with AGP test suites, updated 2026-09-23:
  https://developer.android.com/studio/preview/compose-screenshot-testing-with-testsuites
- Robolectric releases and compatibility table: https://github.com/robolectric/robolectric/releases ,
  https://robolectric.org/compatibility_table/
- Maestro releases (GitHub API: cli-2.12.0 2026-10-26), CHANGELOG, docs:
  https://github.com/mobile-dev-inc/maestro/releases ,
  https://raw.githubusercontent.com/mobile-dev-inc/maestro/main/CHANGELOG.md ,
  https://docs.maestro.dev/platform-support/flutter ,
  https://docs.maestro.dev/get-started/supported-platform/android/jetpack.md ,
  https://docs.maestro.dev/api-reference/selectors , https://docs.maestro.dev/llms-full.txt
- UIAutomator releases (2.4.0, 2026-07-01) and guide (updated 2026-10-23):
  https://developer.android.com/jetpack/androidx/releases/test-uiautomator ,
  https://developer.android.com/training/testing/other-components/ui-automator
- Benchmark releases (1.5.0 2026-09-09; 1.6.0-alpha01 2026-10-07):
  https://developer.android.com/jetpack/androidx/releases/benchmark
- Macrobenchmark overview, updated 2026-10-29:
  https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- Baseline Profiles overview (sideload note), create, manual measure, all updated 2026-10-28:
  https://developer.android.com/topic/performance/baselineprofiles/overview ,
  https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile ,
  https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure
- Startup Profiles / DEX layout, updated 2026-10-23:
  https://developer.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
- ProfileInstaller releases: https://developer.android.com/jetpack/androidx/releases/profileinstaller
- R8 app optimization (AGP 9.0/9.3 changes, `optimization {}` DSL), updated 2026-08-27:
  https://developer.android.com/build/shrink-code
- R8 Configuration Analyzer, updated 2026-09-08:
  https://developer.android.com/topic/performance/app-optimization/r8-configuration-analyzer
- Multiple APKs / ABI splits, updated 2026-02-26: https://developer.android.com/build/configure-apk-splits
- Compose composition tracing, updated 2026-10-28: https://developer.android.com/develop/ui/compose/tooling/tracing
- androidx.tracing releases (2.0.5, 2026-10-21): https://developer.android.com/jetpack/androidx/releases/tracing
- NavigationEvent releases (1.2.0, 2026-10-07): https://developer.android.com/jetpack/androidx/releases/navigationevent
- Ktor client testing (MockEngine), updated 2026-10-05: https://ktor.io/docs/client-testing.html
- Perfetto releases (GitHub API: v59.1 2026-10-27) and PyPI `perfetto` 0.59.1:
  https://github.com/google/perfetto/releases , https://pypi.org/project/perfetto/
- Maven Central / Google Maven metadata, queried 2026-11-01:
  - kotlinx-coroutines-test, turbine, roborazzi, paparazzi, robolectric, kover, mokkery, kotest-property,
    ktor-client-mock, mockwebserver3, pact consumer-junit5, wiremock, networknt json-schema-validator, swagger-parser,
    goncalossilva resources, compose ui-test;
  - uiautomator, benchmark, profileinstaller, tracing, runtime-tracing, metrics-performance, ui-test-junit4,
    navigationevent-testing, AGP, R8, compose screenshot plugin.
- Secondary sources: Kotlin Slack (v2 `runComposeUiTest` desktop issue fixed in Compose 1.11 beta02); search
  snippets on Compose Desktop headless CI. These are marked [S]/[U] where used.
- Repo and machine facts:
  - `/home/eslam/Storage/Code/wt/oc_app-batch1`: `android/app/build.gradle.kts`, `contracts/`,
    `test/fixtures/api2/`, `tool/qa/issue87/mock_llm.py`, `tool/qa/codex_fixture/fixture_server.py`,
    `tool/qa/bd7_device_ui.py`, `tool/release/cold_start.py`, `tool/release/check_apk_size.py`;
  - `native-plan/map/*.md`, `native-plan/catalog/raw-*.jsonl`;
  - owner memory files `build-vps`, `vps-test-env-gaps`, `shared-emulator-contention`, `test-on-emulator-not-phone`,
    `pc-capacity-ceiling`, `dev-freeze-native-rewrite-2026-11-01`;
  - the VPS check over SSH (2026-11-01): no `/dev/kvm`, 0 vmx/svm flags, 8 vCPU, 23 GB, disk 89% used;
  - local AVDs: `OC_API37` (android-37.0 google_apis x86_64), `OC_API35`.
