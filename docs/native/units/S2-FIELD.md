# Unit S2-FIELD: the field recorder (start-up history, frame stats, speed-up profile, trace switch)

Date: 2026-11-01. Branch: `native/field-2026-11-01`, cut from `claude/native-field-recorder-ps14lj` at `ba8f649e`.
Inputs: `native/AGENTS.md`, plan 01, plan 15 row S2-FIELD (and P13-D3, S3-PHONEUI), plan 13 §3.6, §7.1, §11.1,
WP-13.4, §19 P13-D3, plan 11 §2 (`:android:platform`), the S1-PLAT0, INTEGRATE-W1 and INTEGRATE-W2 reports.

**Shipping state:** implemented, and verified on this container with JVM and Robolectric tests, Android lint and the
preview APK build. Committed and pushed to the branch. **Not** wired into `:androidApp` (outside the write set). No
emulator run, no PR, no CI, no release.

**Finish line (plan 15):** "The field recorder runs on the emulator, JankStats costs ≤ 0.1 ms per frame, and
everything is off while hidden; its This phone › Details › Performance rows are built by S3-PHONEUI from this API."

| Part | State |
|---|---|
| The API the Performance rows are built from | **Met.** Four classes in `:android:platform` with platform-free result types (§1). |
| Everything off while hidden | **Met and tested.** The frame listener is attached only to resumed windows and removed on pause, stop and close (Robolectric over the real activity lifecycle). Start-up history and traces run nothing on their own: no timer, thread or callback (§1). |
| ≤ 0.1 ms per frame | **Partly.** The counting step runs off the UI thread with no allocation; a JVM test shows it far below budget (it asserts < 10 µs per frame). The UI-thread cost (the platform's FrameMetrics copy) still needs the emulator measurement in PJ-05, with and without the listener (open problem P2). |
| Runs on the emulator | **Not done.** No emulator in this container, and the app graph does not create the recorder yet (P1). |
| JankStats | **Deviation D1.** The library is not in the catalog and this unit writes new files only, so the recorder uses JankStats' own platform source and jank rule. Switching is a catalog request. |

**Non-goal (kept):** analytics, any network. No copy, no screen, no settings key.

---

## 1. What was built

All files are new and inside the write set (`StartupHistory`, `FieldFrameStats`, `ProfileStatus`, `TraceRecorder`
files in `native/android/platform/**`), plus this report. Package `io.github.eslamasabry.oc.android.platform`.

| Path (`native/android/platform/src/…`) | What it holds |
|---|---|
| `main/…/StartupHistory.kt` | `StartKind`, `StartRecord(atEpochMs, kind, firstFrameMs, fullyDrawnMs?)`, `StartupSummary(supported, typicalFirstFrameMs?, starts, coldStarts, latest?)`, `RawStart`. Pure `StartupRules.recordOf` (activity starts only; measured from the launch request, or the fork time when launch is missing; first frame 0–60 s; a fully-drawn time earlier than the first frame is dropped; wall time placed with one elapsed/wall clock pair) and `StartupRules.summarize` (median of the last 7 days). `StartInfoSource` seam; `StartupHistory(source, clock: kotlin.time.Clock, io)` with `summary: StateFlow`, `start()` (one-shot completion listener for this process's start), `suspend refresh()` (binder read on `io`), `close()`; history and listener readings of the same start are counted once; at most 32 records, in memory only. `AndroidStartInfoSource`: `getHistoricalProcessStartReasons`, `add/removeApplicationStartInfoCompletionListener` (API 35+); activity starts come from `startComponent` on API 36+ and from the start reason (launcher, recents, start activity) on API 35. |
| `main/…/FieldFrameStats.kt` | `FrameSummary(frames, onTime, streamingFrames, streamingOnTime, minutes)` with `onTimePerHundred` ("99 of 100 frames on time"). `FrameCounter`: 60 one-minute buckets on the monotonic frame clock (≈ 1.4 KB of arrays, under plan 13 §7.1's 10 KB), no allocation per frame, thread-safe; late = total duration > 2 × deadline (JankStats' default heuristic); unknown deadline counts as on time; malformed readings dropped. `FieldFrameStats(application)`: `start()`/`close()` (main thread, repeat safely), activity callbacks attach `Window.addOnFrameMetricsAvailableListener` on resume and remove it on pause/destroy; `attach(activity)` for an activity already resumed; the listener runs on one `HandlerThread`, skips the first-draw frame, reads `DEADLINE` on API 31+ or the display refresh interval below; `setStreaming(Boolean)` is the `streaming` state tag; `summary()` is the last hour; `attachedWindows` is 0 while hidden. |
| `main/…/ProfileStatus.kt` | `SpeedUpProfile { Ready, WaitsForRest, NotInstalled, Unsupported, Unknown }`, `ProfileReading` (the three fields of profileinstaller's `CompilationStatus`), `fun interface ProfileReadingSource`, and `ProfileStatus.of(reading)` / `suspend ProfileStatus.read(source)` (a throwing or silent source reads as Unknown; cancellation still propagates). |
| `main/…/TraceRecorder.kt` | `KeptTrace(path, atEpochMs, sizeBytes)`, `TraceState(supported, enabled, latest?)`, `TraceSystem` seam, `TraceRecorder(system, io)`: off by default; `setEnabled` registers or removes the triggers (a refused registration leaves it off); `refresh()` lists `files/profiling/`, ignores empty files and symlinks, keeps the newest 2 and deletes the rest; `deleteAll()` keeps the switch. `AndroidTraceSystem`: `ProfilingManager.addProfilingTriggers` with `TRIGGER_TYPE_APP_FULLY_DRAWN` and `TRIGGER_TYPE_ANR`, `setRateLimitingPeriodHours(24)`, `removeProfilingTriggersByType` (API 36+). The switch value belongs to settings; the recorder keeps no preference. |
| `test/…/StartupHistoryTest.kt` | 7 JVM tests. |
| `test/…/FieldFrameStatsTest.kt` | 5 JVM tests (one is the cost check). |
| `test/…/ProfileStatusTest.kt` | 4 JVM tests. |
| `test/…/TraceRecorderTest.kt` | 7 JVM tests over a temp folder. |
| `test/…/FieldFrameStatsRobolectricTest.kt` | 5 Robolectric tests (`sdk = 35`, one at `sdk = 26`): attach/detach over the real activity lifecycle, the API 35 start-history binding, and no history (API 26) or trace switch (API 35) on older Androids. |

How S3-PHONEUI (or the App diagnostics owner, plan 13 §3.6) builds the rows from this API:

| Row | Source |
|---|---|
| "Opens in 0.3 s (typical this week)" | `StartupHistory.summary.typicalFirstFrameMs` after `refresh()` when the page opens; hidden when `supported` is false or the value is null |
| "Smooth: 99 of 100 frames on time in the last hour" | `FieldFrameStats.summary().onTimePerHundred` (null = no frames yet) |
| "Speed-up: ready / waits for the phone to rest" | `ProfileStatus.read(source)` |
| Trace switch, "Share performance trace" | `TraceRecorder.state` (`supported`, `enabled`, `latest`), `setEnabled`, `refresh`; sharing goes through `ShareService.shareFile` with a `content://` URI (needs a FileProvider path for `files/profiling/`, P4) |

Privacy: nothing is persisted by these classes (the system keeps the start history and the trace files), nothing is
logged, nothing touches the network, and trace paths are app-private and are never shown or logged.

## 2. Catalog rows covered

Plan 15 lists no catalog row for this unit ("— (performance budgets)"), and plan 13 assigns no PB id to the
recorder's own cost. Every test is therefore named `WP-13_4 FIELD …` after the work package (the same approach S1-PLAT0
took with `PLAT0 …`; `:android:*` is outside Konsist rule 12's scope). 28 new tests:

| Area | Tests |
|---|---|
| Start-up history | an activity start becomes a record from the launch request; fork stands in for launch; non-activity starts and starts with no frame or origin are not counted; implausible timings dropped; median of this week (odd and even counts, last week excluded); older Android unsupported and reads nothing; refresh + listener count a start once, close repeats safely; API 35 binding reads without throwing; API 26 has none. |
| Frame stats | 2× deadline rule; streaming frames counted apart; only the last hour in 60 buckets, and an overwritten minute is ignored; no frames → no number, clear, malformed readings; counting cost; listener only while resumed (pause, stop, close, resume after close); nothing before start, one listener per window. |
| Speed-up profile | compiled → Ready; enqueued → WaitsForRest; no profile, unsupported API and verifier errors told apart; a failing verifier reads Unknown. |
| Trace recorder | off until turned on; refused registration stays off; older Android has no switch and touches nothing; newest two kept, empty files ignored; symlinks never offered or followed; delete keeps the switch; no folder yet; API 35 binding unsupported. |

Mutation check: changing the jank rule from `>` to `>=` and dropping the activity filter each failed their tests.
The tests were written alongside the code rather than strictly before it.

## 3. Commands and results

Environment (container only, nothing in the repo): Android command-line tools 16111833 in
`/opt/android-sdk/cmdline-tools/latest`, licences accepted, `platform-tools`, `platforms;android-37.1`,
`platforms;android-36`, `build-tools;37.0.0` (AGP added `build-tools;36.0.0` itself); `openjdk-17-jdk-headless` for
the toolchain; `~/.gradle/gradle.properties` (`-Xmx6g`, 4 workers, JDK 17/21 paths); `native/local.properties`
(gitignored); `BASH_DEFAULT_TIMEOUT_MS` raised. Maven Central answered 429 through the proxy, so a container-only
`~/.gradle/init.d/central-mirror.gradle.kts` removes `repo.maven.apache.org`, puts Google's Central mirror ahead of the
plugin portal and sets `robolectric.dependency.repo.url`, as in earlier units.

All from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`:

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :android:platform:testDebugUnitTest` (base, before any edit) | BUILD SUCCESSFUL, 23 tests |
| 2 | same, with the new files | BUILD SUCCESSFUL, **51 tests, 0 failures, 0 skipped** (23 old + 28 new) |
| 3 | same, filtered to the two JVM classes, after two planted mutations | FAILED in exactly the two expected tests; mutations reverted |
| 4 | `./gradlew :android:platform:lintDebug :androidApp:assemblePreviewDebug` | FAILED: lint `NewApi` ×4 (API checks behind the `supported` property, which lint does not follow; `startComponent` is API 36) and `WrongConstant` ×1 (trigger list built from an `IntArray`) |
| 5 | `./gradlew :android:platform:lintDebug` after the fix (inline `SDK_INT` checks, start reason on API 35, literal trigger constants) | BUILD SUCCESSFUL, no lint issues |
| 6 | `./gradlew :android:platform:testDebugUnitTest :androidApp:assemblePreviewDebug` | BUILD SUCCESSFUL; 51 tests, 0 failures; `androidApp-preview-debug.apk` 18.6 MB |
| 7 | `tool/check_commits.sh ba8f649e HEAD`; `tool/check_write_set.sh --base ba8f649e --globs '<the four name globs>' --also docs/native/units/S2-FIELD.md`; `tool/scan_push.sh ba8f649e HEAD` | all OK: 5 commits pass the message rules; 10 changed paths, all inside the write set; scan clean (no keys, private addresses or personal e-mail) |

No common code changed, so `compileKotlinDesktop` and the desktop app are unaffected; they were not rerun.

## 4. Deviations

- **D1 — no JankStats library.** Plan 13 §3.6 names JankStats 1.0.0 (`androidx.metrics:metrics-performance`). It is
  not in `libs.versions.toml`, and this unit may not edit the catalog or the module's `build.gradle.kts`. The recorder
  binds the same platform source JankStats uses on API 24+ (`Window.addOnFrameMetricsAvailableListener` on a
  background handler) with JankStats' default rule (late when longer than 2 × the frame's deadline). The
  `PerformanceMetricsState` `screen` tag is not carried; `streaming` is (`setStreaming`). The result types do not
  change if JankStats replaces the listener.
- **D2 — ProfileVerifier through a seam.** `androidx.profileinstaller` is in the catalog but not on this module's
  classpath, so `ProfileStatus` holds the mapping and a `ProfileReadingSource`; the one-line binding
  (`ProfileVerifier.getCompilationStatusAsync().await()` → `ProfileReading`) belongs to whichever module has the
  library. The result codes are written from profileinstaller 1.4.1's `CompilationStatus` (0 no profile,
  1 compiled, 2 enqueued, 3 compiled non-matching, errors shifted by 16 with 4 = unsupported API). Check them against
  the library at binding time.
- **D3 — "This phone › Details" vs App diagnostics.** Plan 15 puts the rows on This phone › Details › Performance;
  plan 13 §3.6 and its change log move them to App diagnostics' Kept page (07 L02). This unit ships only the API, which
  serves either page. The coordinator picks the page.
- **D4 — test names.** No catalog row or PB id exists for the recorder, so tests use `WP-13_4 FIELD …` (§2).
- **D5 — traces kept.** The newest 2 trace files are kept and older ones deleted on `refresh()`. Plan 13 does not set
  a number; 2 bounds storage (a system trace is several MB).

## 5. Catalog requests

1. `androidx.metrics:metrics-performance:1.0.0` as `androidx-metrics-performance`, plus
   `implementation(libs.androidx.metrics.performance)` in `:android:platform` (D1).
2. `implementation(libs.androidx.profileinstaller)` in `:android:platform` (already in the catalog, 1.4.1), so the
   `ProfileReadingSource` binding can sit next to the mapping (D2). `kotlinx-coroutines-guava` or `concurrent-futures`
   would be needed to `await()` the `ListenableFuture`; a plain listener with `suspendCancellableCoroutine` works
   without it.

## 6. Open problems

- **P1 — not wired.** `:androidApp` does not depend on `:android:platform` yet (since S1-PLAT0). The graph has to
  create `StartupHistory(AndroidStartInfoSource(app), clock, dispatchers.io)` and call `start()`,
  `FieldFrameStats(app).start()` in `Application.onCreate`, `attach(activity)` for an already resumed activity, and
  `TraceRecorder(AndroidTraceSystem(app), dispatchers.io)` with `setEnabled` from settings at start-up. Engine side:
  `setStreaming` from the transcript's streaming state. The rows are S3-PHONEUI's (or the App diagnostics owner's).
- **P2 — UI-thread cost unmeasured.** The ≤ 0.1 ms per frame budget needs PJ-05 on the emulator with and without
  the listener (WP-13.4). Only the counting step is measured here.
- **P3 — the trace switch preference.** The switch (off by default) needs a settings key owned by `:engine:settings`,
  and the app has to re-apply it at start-up. ProfilingManager keeps triggers per process, so a fresh process starts
  with none.
- **P4 — sharing a trace.** `ShareService.shareFile` needs a `content://` URI, so the app's FileProvider needs a path
  for `files/profiling/` (manifest and `res/xml`, outside this write set). Plan 16 should review it with P13-D3 part 1
  (profileable release).
- **P5 — Robolectric reach.** Robolectric has no real `ApplicationStartInfo` history or `ProfilingManager`, so the API
  35/36 bindings are only proven to compile, pass lint and not throw; the emulator run (OC_API37) has to show a real
  start record and a trigger registration.
