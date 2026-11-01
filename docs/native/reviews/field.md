**Verdict: REQUEST CHANGES — 6 MAJOR, 2 MINOR findings.**

1. **MAJOR — Trigger registration lacks the required result listener.**  
   [TraceRecorder.kt:130](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/TraceRecorder.kt:130)

   **Problem/evidence:** The implementation calls `addProfilingTriggers()` but never registers `registerForAllProfilingResults()`. Android delivers trigger results only through that global listener. The switch can report enabled without a working result-delivery path. Scanning a hardcoded directory also bypasses plan 13 §3.6’s required `ProfilingResult.getResultFilePath()`. [Android API contract](https://developer.android.com/reference/android/os/ProfilingManager#addProfilingTriggers(java.util.List%3Candroid.os.ProfilingTrigger%3E))

   **Fix:** Register an owned listener and executor before adding triggers. Accept successful, completed results using their returned paths; handle deferred delivery after process restart. Add an API-36 binding test that verifies listener registration and result intake.

2. **MAJOR — Failed disable falsely reports that recording stopped.**  
   [TraceRecorder.kt:58](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/TraceRecorder.kt:58)

   **Problem/evidence:** After successful enablement, let `removeProfilingTriggersByType()` throw. `AndroidTraceSystem` returns `false`, and this expression publishes `enabled=false` anyway. Existing triggers can remain registered after the person withdraws consent. The refusal test covers only unsuccessful enablement.

   **Fix:** Preserve the last successfully applied state when removal fails and return a failure or explicit uncertain state. Test successful enablement followed by refused disablement, including the actual registered-trigger state.

3. **MAJOR — Concurrent commands can overwrite each other’s state.**  
   [TraceRecorder.kt:67](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/TraceRecorder.kt:67)

   **Problem/evidence:** `withContext(io)` does not serialize commands. `refresh()` can construct its state copy while `enabled=false`, then pause before assignment. `setEnabled(true)` registers triggers and publishes true; the suspended refresh then writes its stale copy, restoring false while recording remains enabled. Likewise, refresh racing deletion can publish a path that was just deleted. All tests use sequential calls on `StandardTestDispatcher`.

   **Fix:** Serialize complete command operations with a mutex or command lane. Use atomic flow updates for independent fields, and add latch-controlled concurrency tests for refresh/enable and refresh/delete.

4. **MAJOR — Startup history mixes incompatible clocks.**  
   [StartupHistory.kt:85](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/StartupHistory.kt:85), [StartupHistory.kt:224](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/StartupHistory.kt:224)

   **Problem/evidence:** Activity startup timestamps use uptime, whereas `elapsedRealtimeNanos()` includes deep sleep. Subtracting the two backdates starts by accumulated sleep. For example, with one hour of uptime after nine days elapsed, a start one second ago appears almost nine days old and is excluded from “this week.” The tests assume both clocks coincide. [AOSP startup timing](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/services/core/java/com/android/server/wm/ActivityMetricsLogger.java), [Android clock definitions](https://developer.android.com/reference/android/os/SystemClock)

   **Fix:** Use the startup timestamp’s clock domain for comparisons. Establish wall-time age separately with reliable observation/boot metadata; do not fabricate historical epoch dates from mixed clocks. Test substantial sleep and reboot history.

5. **MAJOR — The replacement does not calculate JankStats jank.**  
   [FieldFrameStats.kt:198](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/FieldFrameStats.kt:198)

   **Problem/evidence:** The adapter passes `TOTAL_DURATION` into the jank rule. JankStats compares the sum of UI durations through synchronization against its adjusted expected duration; total duration is tracked separately. A frame with UI work below the threshold but total duration above it is classified differently here. The claimed equivalence to plan 13 §3.6 is therefore false. The arithmetic test never exercises this metric selection. [AndroidX implementation](https://raw.githubusercontent.com/androidx/androidx/androidx-main/metrics/metrics-performance/src/main/java/androidx/metrics/performance/JankStatsApi31Impl.kt)

   **Fix:** Integrate the planned JankStats library. If retaining the substitute, reproduce its metric calculation and add adapter tests where UI duration and total duration produce different classifications.

6. **MAJOR — The advertised API cannot reach its planned common consumers.**  
   [StartupHistory.kt:39](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/StartupHistory.kt:39), [FieldFrameStats.kt:24](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/FieldFrameStats.kt:24)

   **Problem/evidence:** The result types and services exist exclusively in the Android library. There is no corresponding common contract in `native/core` or diagnostics. “Platform-free” fields do not make these declarations available to `commonMain`. Plan 13 routes startup data into `:engine:diagnostics`; ADR §6.1 and the enforced `ModuleRules` prohibit engines/features from depending on `:android:platform`. Direct integration therefore cannot compile within the required graph.

   **Fix:** Coordinate a common service contract and result models in diagnostics or `:core:domain`, implement that contract here, and supply a desktop implementation. Verify consumption from common code.

7. **MINOR — “Last hour” excludes time spent asleep.**  
   [FieldFrameStats.kt:126](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/FieldFrameStats.kt:126)

   **Problem/evidence:** Both frame timestamps and summary time use the uptime-based `nanoTime()` domain. After eight hours of deep sleep, old buckets remain inside the reported last hour until another hour of awake time passes. This contradicts the displayed interval in plan 13 §3.6. [Android clock definitions](https://developer.android.com/reference/android/os/SystemClock)

   **Fix:** Age buckets using elapsed time that includes sleep, converting frame timestamps into that domain. Add a sleep/wake test that expires pre-sleep frames.

8. **MINOR — Binding tests permit materially broken implementations.**  
   [FieldFrameStatsRobolectricTest.kt:29](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/src/test/kotlin/io/github/eslamasabry/oc/android/platform/FieldFrameStatsRobolectricTest.kt:29), [FieldFrameStatsRobolectricTest.kt:70](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/src/test/kotlin/io/github/eslamasabry/oc/android/platform/FieldFrameStatsRobolectricTest.kt:70), [FieldFrameStatsTest.kt:72](/home/eslam/Storage/Code/wt/native-reviews/src-field/native/android/platform/src/test/kotlin/io/github/eslamasabry/oc/android/platform/FieldFrameStatsTest.kt:72)

   **Problem/evidence:** Lifecycle tests inspect the recorder’s map, so removing the real listener add/remove calls while retaining map updates still satisfies their assertions. The supported startup test still passes if history always returns empty and listener registration does nothing. The cost test still passes if counting becomes a no-op.

   **Fix:** Deliver frame events and assert admission stops after pause/close; seed platform startup data and assert mapped values; verify counted output alongside timing.

   Static assertion-sensitivity checks identified four mutations that **would** fail existing assertions:

   - Changing `>` to `>=`: on-time count becomes 2 instead of 3 (`FieldFrameStatsTest:22`).
   - Counting non-streaming frames as streaming: streaming on-time count becomes 2 instead of 1 (`FieldFrameStatsTest:36`).
   - Removing the activity filter: returns a record instead of null (`StartupHistoryTest:46`).
   - Removing pruning: the old file remains (`TraceRecorderTest:77`).

Reviewed `eba2e90f..97f5db42`. No files changed. Kotlin tests, Gradle builds, and emulator checks were not run; mutation checks above are static.