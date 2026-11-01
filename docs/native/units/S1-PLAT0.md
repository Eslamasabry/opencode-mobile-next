# Unit S1-PLAT0: `:android:platform` starts with the crash store, the network monitor and basic DeviceFacts

Date: 2026-11-01. Branch: `native/plat0-2026-11-01`, cut from `claude/native-plat0-2026-11-01-8h4zzx` at `6b9a2b21`.
Inputs: `native/AGENTS.md`, plan 01, plan 15 row S1-PLAT0 and C15-3, plan 11 §1.1 rows 01, 42, 45 (and §2 module row,
CR11-5, CR11-11), plan 04 §6.4, plan 16 §1.10, plan 03 §10.4 and §18 (`CrashStore`), map `android-platform.md` §45,
catalog rows B-ANDR-036, B-ANDR-037, B-ANDR-093, B-SEC-043.

**Shipping state:** implemented and verified on this container (compile, Android lint and JVM + Robolectric tests).
Committed and pushed to the branch. Not wired into `:androidApp` (outside the write set), no emulator run, no PR, no CI,
no release.

**Finish line (plan 15):** "`NativeCrashStore`, the network monitor and basic `DeviceFacts` run behind their
`:core:domain` interfaces (C15-3)."
- Met for the module: `AndroidNetworkStatus : NetworkStatus` and `AndroidDeviceFacts : DeviceFacts` implement the
  existing `:core:domain` interfaces; `NativeCrashRecords : CrashStore` implements a crash port that does not exist in
  core yet, so it is defined in this module with `TODO(move-to-core)` (D1).
- "Run" means on Robolectric's real `ConnectivityManager`/`PowerManager`/activity lifecycle shadows here. The app graph
  does not create or start them yet; that is a `:androidApp` edit (open problem P1).

**Non-goal (kept):** entry intents, pickers, permissions, share (S2-PLATFORM); battery, thermal headroom, exit history
and the native/ANR import (slice 3).

---

## 1. What was built

All files are inside the write set `native/android/platform/**`, plus this report.

| Path | What it holds |
|---|---|
| `build.gradle.kts` | `oc.android.library`; `api(:core:domain)`, `androidx.core:core-ktx`; tests: `kotlin("test-junit")`, junit4, Robolectric, coroutines-test (all from the catalog). |
| `src/main/AndroidManifest.xml` | `ACCESS_NETWORK_STATE` only (merged into the app; `:androidApp` declares no permissions today). |
| `src/main/kotlin/…/oc/android/platform/NativeCrashStore.kt` | **File 1.** The Flutter app's `NativeCrashStore` copied at `cdfd713b`, body unchanged except the package line and one marked fix (D2). Below it, native-authored: `CrashStore` (`latest(): CrashRecord?`, `clear(): Boolean`, never throw), `CrashKind` (7 fixed categories), `CrashRecord(kind, atEpochMs, causes ≤ 3)` and the adapter `NativeCrashRecords`. |
| `src/main/kotlin/…/AndroidNetworkStatus.kt` | **File 2.** Port of `NetworkMonitor.kt` at `cdfd713b` without channel glue: `NetworkCaps` + `networkReading()` (pure rules), `DefaultNetworkTracker<N>` (the default-network state machine, generic over the handle so JVM tests drive it), and `AndroidNetworkStatus : NetworkStatus` (one `registerDefaultNetworkCallback(cb, mainHandler)`; registration, seed and callbacks all on the main looper; `start()`/`close()` idempotent from any thread). |
| `src/main/kotlin/…/AndroidDeviceFacts.kt` | **File 3.** `AndroidDeviceFacts : DeviceFacts`: `network` is the one `NetworkStatus.state` (no second reading, plan 04 §6.4); `thermal` from `PowerManager` current status + listener (API 29+, `None` below); `powerSave` from `isPowerSaveMode` + `ACTION_POWER_SAVE_MODE_CHANGED`; `totalRamBytes` from `ActivityManager.MemoryInfo.totalMem` (lazy, never memoryClass); `appVisible` from activity start/stop counts that ignore configuration-change stops (`VisibilityTracker`). Pure helper `thermalLevelOf(Int)`. |
| `src/test/kotlin/…/NativeCrashStoreTest.kt` | 10 plain-JVM tests over the real store in a temp dir. |
| `src/test/kotlin/…/NetworkTrackerTest.kt` | 5 plain-JVM tests over the tracker and reading rules. |
| `src/test/kotlin/…/AndroidPlatformRobolectricTest.kt` | 8 Robolectric tests (`sdk = 35`) over the two Android bindings. |

Mapping rules worth knowing (plan 04 §6.4 types, which have no "offline but connected" value):

| Android | `NetworkState` |
|---|---|
| no default network / `onLost` of the current default | `Online.No`, transport Unknown, id null |
| default network, INTERNET ∧ VALIDATED | `Online.Yes` + transport, metered, `networkHandle` |
| default network, not validated (captive Wi-Fi, hotspot with no uplink) | `Online.Unknown` + transport (never `No`, so LAN/Tailnet are never gated) |
| `onAvailable`, or capabilities unreadable | `Online.Unknown`, transport Unknown, id set |
| VPN (Tailscale) or Wi-Fi + cellular | transport Unknown |
| not started, closed, registration failed (e.g. permission) | `NetworkState.Unknown` |

## 2. Catalog rows covered

| Row | Tests (name prefix = row id) |
|---|---|
| B-ANDR-036 | `NativeCrashStoreTest`: one category-only record; wrapped service crash keeps the permission cause; causes bounded to 3 and a cause cycle ends; invalid input fails closed (unknown class, future time, corrupt, oversized); clear removes record and temporary. |
| B-SEC-043 | `NativeCrashStoreTest`: only categories saved and a hostile `toString`/`message` is never called; no consent keeps nothing and erases an old record; corrupt/future/earlier consent fails closed; a symlinked snapshot cannot redirect evidence (existing link, dangling link, dangling `.tmp` link); the handler hands Android only the scrubbed `RuntimeException("Native application error")` with an empty stack and no cause. |
| B-ANDR-037 | `NetworkTrackerTest` (5) + 3 Robolectric: no network vs unvalidated Wi-Fi vs unknown; typed mobile/metered; never combine capabilities with a different default; newer callbacks beat the seed and a late `onLost` for the old default is ignored; repeats and reset; one registration, follows callbacks, unregisters on close; a stale callback after close changes nothing; DeviceFacts reads the same `NetworkStatus` flow. |
| B-ANDR-093 (Kotlin half) | thermal status values map to levels, changes arrive as readings, nothing after close. The Dart "words" half belongs to `:engine:runtime` in slice 3. |

Three tests are named `PLAT0 …` because no catalog row covers them: power save follows the broadcast, total RAM is read
from the platform, app visibility (Activity start/stop and the rotation case, twice: Robolectric and the pure tracker).

The parts of B-ANDR-036 that are Dart-side (launch report contract, frame regexes, ≤ 24 frames, the startup trace
record) belong to `:engine:diagnostics` and are not covered here.

## 3. Commands and results

Environment (container only, nothing in the repo): Android command-line tools 16111833 in
`/opt/android-sdk/cmdline-tools/latest`, licences accepted, `platform-tools`, `platforms;android-37.1`,
`build-tools;37.0.0` (AGP added `build-tools;36.0.0` itself); `openjdk-17-jdk-headless` installed for the toolchain;
`~/.gradle/gradle.properties` (auto-download off, explicit JDK 17/21 paths); `native/local.properties` (gitignored);
`BASH_DEFAULT_TIMEOUT_MS` raised in `~/.claude/settings.json`. Maven Central answered **429** through the sandbox
proxy, so a container-only `~/.gradle/init.d/central-mirror.gradle.kts` puts Google's Central mirror first (and sets
`robolectric.dependency.repo.url`), as in P1 D10 and S1-DATA.

All from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`:

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :core:domain:compileAndroidMain` | BUILD SUCCESSFUL (the dependency compiles on this base) |
| 2 | `./gradlew :android:platform:testDebugUnitTest` (first) | compile error: `ShadowPowerManager.setThermalStatus` does not exist in Robolectric 4.17 → `setCurrentThermalStatus`; the binding switched to the one-argument `addThermalStatusListener` (main executor), which Robolectric shadows |
| 3 | same | 23 tests, 1 failed: the copied store throws on a link to an existing file but **writes through a dangling `.tmp` link** (D2) |
| 4 | red check for D2 (test first, fix absent) | 10 tests, 1 failed as expected |
| 5 | same, fix in | **23/23 passed** (Robolectric 8, NativeCrashStore 10, NetworkTracker 5) |
| 6 | red check: late-`onLost` guard removed from `DefaultNetworkTracker` | `NetworkTrackerTest` 5 tests, 1 failed; restored |
| 7 | `./gradlew :android:platform:lintDebug` | No issues found |
| 8 | `./gradlew :android:platform:testDebugUnitTest` (final, after the last edit) | **23/23 passed** |
| 9 | `grep` of `native/android/platform` for RFC 1918 / CGNAT addresses and e-mail addresses | no match (only `@androidx.annotation…` lines) |

Not run: `:quality:test` and anything outside this module (the brief: other modules may not compile during the core
reconciliation); no emulator.

## 4. Deviations

- **D1 — `CrashStore` lives here for now.** Plan 03 §18 puts `interface CrashStore` in `:engine:diagnostics`, which
  does not exist. `CrashStore`, `CrashRecord` and `CrashKind` are defined in `NativeCrashStore.kt` with
  `TODO(move-to-core)`; they are platform-free and move unchanged. The names avoid `:engine:analytics`'
  `CrashCategory` (Kotlin/Native/OOM…, a different axis); diagnostics can map `CrashKind` → `CrashCategory.Kotlin`.
- **D2 — one fix in the copied `NativeCrashStore` (security).** `target()` compares canonical and absolute paths, which
  catches a link to an existing file but not a dangling link: `FileOutputStream` followed a dangling
  `native-last-crash.properties.tmp` link and created the record outside `filesDir` (category-only content, and it needs
  write access to the app's private directory, so low practical risk; but it breaks B-SEC-043 "a symlinked snapshot
  cannot redirect evidence"). Fix: `Files.deleteIfExists(temporary.toPath())` (removes the link itself) before opening.
  Marked in the file. **The Flutter original has the same gap** (P3).
- **D3 — five files, not three.** The three Kotlin files are as planned; `build.gradle.kts` and an
  `AndroidManifest.xml` (one permission) are module plumbing.
- **D4 — `appVisible` without `ProcessLifecycleOwner`.** `androidx.lifecycle:lifecycle-process` is not in the catalog,
  so visibility counts started activities and ignores stops during configuration changes (no 700 ms debounce like
  ProcessLifecycleOwner). Behaviour is the same for single-activity rotation; switching to the library later is a
  one-class change.
- **D5 — unvalidated network reads `Online.Unknown`.** Flutter called it `offline` with `hasNoNetwork = false`;
  `NetworkState` defines `No` as "no default network" only, so the Flutter distinction survives as `No` vs
  `Unknown + transport`.
- **D6 — explicit `start()`/`close()`.** Both bindings are inert until started (Unknown / None / false), so constructing
  them in the graph does no binder or disk work on the start-up path (PB-70). Plan 11 CR11-5's "only while collected"
  polling is for thermal headroom, not built yet.
- **D7 — `onWifi` missing.** Plan 04 §6.4 lists `NetworkState.onWifi`; the frozen `:core:model` type does not have it,
  so it is not produced. The Wi-Fi bit is available in `NetworkCaps` when the core owner adds the field.
- **D8 — no formatter run.** `native/AGENTS.md` names `tool/format-changed.sh`, which does not exist yet.

## 5. Catalog requests

None needed. Optional, for later units: `androidx-lifecycle-process` (D4) and `androidx-test-core`
(`ApplicationProvider`; tests use `RuntimeEnvironment.getApplication()` instead).

## 6. Open problems

- **P1 — wiring (`:androidApp`, coordinator).** `OcApplication` step 1 should install
  `NativeCrashStore(filesDir).install()` from this module (plan 03 §13 moves it here from `:androidApp`; this unit
  makes it available now), and the graph should `@Provides` `AndroidNetworkStatus` as `NetworkStatus`,
  `AndroidDeviceFacts` as `DeviceFacts`, `NativeCrashRecords` as `CrashStore`, and call `start()` once per process.
- **P2 — `MODULE_OWNERS` has no `:android:platform` row** (plan 11 §2: kind F); `ModuleOwnersTest` will fail until the
  coordinator adds it (as with `:android:linux`).
- **P3 — Flutter `NativeCrashStore` has the D2 gap.** Same one-line fix applies to
  `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeCrashStore.kt` (outside this write set).
- **P4 — `native/TWINS.md` rows** for `NativeCrashStore.kt` (copy + D2 fix) and `NetworkMonitor.kt` (port), source
  commit `cdfd713b`, are not added: the file is outside this write set.
- **P5 — CR11-5 extensions** (`battery`, `thermalHeadroom`) and moving `CrashStore` (D1) need the `:core:domain` /
  `:engine:diagnostics` owners.
- **P6 — not proven on a device.** Robolectric shadows only; the emulator check (default network flaps, Tailscale up,
  thermal status) waits for a T3 run.
