# BC diagnostics verification

Candidate: branch `sol/bc-diagnostics` based on `feat/genui-fe` e2b3fe1664659e26b3316bd53ab5790dbd39e1aa.

[Frontend contract](../../design/BC-diagnostics-contract.md) was written before implementation. This lane implements the local device gateway and native/platform adapters; coordinator builds the UI. Capture stays OFF by default. No network upload or emulator/device job occurs.

Root alone runs checks. Workers own disjoint lifecycle history, complete background channel, and category-only report slices. Checks use pinned Flutter/Dart and `tool/qa/machine_lock.sh`; Kotlin gate takes its own build lock. Native host harnesses compile actual production policies against controlled Android dependencies, without APK or emulator execution.

Focused exit-history and existing recovery checks:46 pass. Crash-report and existing capture/startup checks:39 pass. Regression removals: dropping exact-process policy filter fails main-process-history; dropping report evidence-revision guard shares a stale preview and fails the explicit expectation. Mutations restored before integration checks. Pause/integration and static results are recorded below.

Evidence logs under coordinator scratchpad `sol/bc-diagnostics/`: pub-get.log, fd1-tests.log, fd1-red.log, fd2-tests.log, fd2-red.log; further checks added at completion. No logs or test values are production credentials.

Native Android binding/device behavior remains separate: no real six-hour timeout, OEM swipe, foreground permission flow or power-loss qualification is claimed from host tests. Pause reason uses confirmed callbacks/policy facts or says interruption unknown; no budget is synthesized.


Final pause/integration check:95 tests passed in24s across background_pause_test.dart, background_pause_native_test.dart, background_live_test.dart, background_action_test.dart, background_coding_alert_test.dart, background_notification_navigation_test.dart, ios_remote_platform_gating_test.dart and device_diagnostics_gateway_test.dart. FD1:46; FD2:39; aggregate180 focused tests. All run serially with --concurrency=1 through machine_lock. Three existing Android fakes now answer getBackgroundPause explicitly; action/notification assertions remain intact. Initial six notification-action failures were missing new fixture replies, corrected before final pass. No snapshot baselines changed.

FD3 red proof removed only timeout's durable Flutter opt-in correction; native timeout-reopen then failed because the old enabled flag survived. Restore and Resume tests include cold receipt reads, pending/denied starts, duplicate taps, old receipt replies, timeout during Resume and explicit Pause races. Production store+policy host harness confirms commit/reopen/clear, current restriction distinction, main-process user exits10/11, old-exit rejection and commit failure. Controlled preferences are not a physical power-loss proof.

Kotlin production gate command: `tool/qa/kotlin_static_analysis.sh --input android/app/src/main/kotlin --report-dir build/reports/detekt-bc-diagnostics-main` passed with zero new findings against the unchanged baseline. The default WHOLE-ANDROID gate still fails one inherited `ReturnCount` in `android/app/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/Bd9DeviceSmoke.kt:74`, function `failureStage` (3 returns vs2). That file is byte-identical to base e2b3fe16 and outside this lane's ownership. It was not suppressed, edited or added to the baseline. The three diagnostics findings in the first run were corrected. This is a concrete integration handoff to BD9/coordinator, not a claimed whole-gate pass.

Evidence: fd3-and-integration-final.log, fd3-red.log, detekt-final-full.log, detekt-main.log, analyze-final.log. The first Dart analyzer found only fixture braces and an unused import; these were fixed without behavior changes. Final full pinned analyzer passed with no issues (13.6s). All18 changed Dart files are formatted; diff check passed. No full Flutter suite, Android APK compile/sign, UI implementation/screenshots, emulator session, release or network upload. EMULATOR GO remains pending.
