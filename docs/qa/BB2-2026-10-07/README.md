# BB2 native supervision — runtime acceptance passed

Started 2026-10-07; emulator qualification completed 2026-10-08. Base: `3bd79a8c`, branch `sol/bb-runtime`; BB2 candidate committed as `e657025d` before integration merge.

Implemented: native exact-process crash supervision with 1–60 second backoff, a 30-second stable-uptime delay reset, persisted person Stop intent, current policy/generation admission and the existing single durable three-attempt budget. Migration stages the spent Dart count while disabled, saves a rejecting Dart v2 marker, then activates the existing native record. Rebinding cannot replenish retries. Historical confirmed receipts are bounded and deleted through the existing suspension/deletion hook. App launch cannot bypass scheduled/exhausted native recovery or use unattended manual Start. No UI or Connection files changed; BB3 is unstarted.

Enabled: migrated profiles consume the existing restart/health policy. Missing or malformed budget, marker or policy denies automatic recovery. The signed QA candidate is installed only on emulator-5554; native changes are unreleased.

## Actual device result

[Final safe acceptance output](emulator-2201-pid.txt) proves the newest 2201 candidate:

- BB1 exit 137 classification, exit 7, persisted uptime 2028 ms/restart count 11 after force-stop, and profile diagnostics deletion pass.
- Actual authenticated OpenCode server recovers after exact mapped-child SIGKILL in **1127, 2133 and 4143 ms**, consuming native attempts **1, 2 and 3**. Each replacement has authenticated JSON `healthy=true` and a nonempty version. No Activity is open; the foreground service remains through every admitted delay/replacement.
- Fourth crash reaches scheduler/process/foreground-service quiescence with count 3 and no fourth replacement.
- Healthy explicit Start resets the same budget with automatic policy off.
- Explicit Stop persists across Android force-stop/reopen. This does not claim BB3 OS process-reclamation recovery.

PID selection requires exactly one descendant of the tracked service process with an executable mapping of the canonical installed OpenCode binary. `/proc/exe` basename alone is insufficient under PRoot. The isolated config/data/project fixture and native keys are deleted after exact children drain. Safe output contains counts, enum classes and booleans only.

## Emulator interruption and restoration

The coordinator explicitly authorized stopping the dev emulator runtime, killing exact app-owned PIDs and force-stop/restart for BB2/BB3, with the emulator lock held throughout. Both target and runner were installed in place using the authorized signer; no uninstall, downgrade or clear-data occurred.

Original native recovery keys were kept in memory and restored in `finally`, with temporary stopped intent until the app's explicit Start. Existing profile, policy, marker and account configuration were preserved. A normal healthy manual Start legitimately reset the person retry count **1→0** during the first restoration; subsequent runs began at 0. Optional background permission was dismissed with Not now.

Final authored Start restores real **OpenCode2 2.0.10**, authenticated health and Connected/Running UI. See [final server screenshot](restored-opencode2-final.jpg). Full harness exit is **1 solely because Claude Ready remains unproven**; native BB2 assertions themselves passed.

The existing Check Claude Code action changed its same card from Phone check needed to Sign in needed. No authentication was started and no credential files were edited or moved. The [scoped presence probe](claude-scope-presence.txt) reads only file presence/nonempty booleans: active authored profile matches native owner, its Claude credential file is absent/empty, and another existing profile has a nonempty credential file. This is a profile-scoping/restoration prerequisite; file presence alone does not prove another account is authenticated. The other credential file matches a saved profile, but no saved authored in-app OpenCode2 profile owns it. See [final Claude card](claude-sign-in-needed-final.jpg). No valid existing in-app2 selection resolves the gap. The coordinator subsequently explained that BA5's shared phone-agent owner migration exists in feat/genui-fe and was absent from this pre-integration APK. The restoration requirement was relaxed to installed app/data and runnable in-app server, which is satisfied. No credential movement is needed. This historical harness exit1 remains accurate; it does not block BB3. Future device APKs must be built from the merged integration branch.

## Focused checks

- Pinned Flutter tests under `machine_lock.sh test`, serial, five affected files: **79 pass** in [flutter-focused.txt](flutter-focused.txt). Files: builtin_linux, builtin_performance_diagnostics, builtin_server_recovery, builtin_server_recovery_native and phone_server_healing.
- Pinned [analyzer clean](analyze.txt), 234.1s, no ignores. Dart format language version 3.10; diff check clean.
- Locked [native mutation regression](native-red.txt): nine exact probes fail with fixes removed; source restored. [32 JVM pass](native-green.txt): 21 NativeRecoveryBudget, 4 RestartBackoff and 7 BB1 ServiceDiagnostics. JDK17/Gradle9.5, no daemon, max-workers1, bounded2GB heap.
- Exact Dart red probes: [staging](flutter-migration-red.txt), [manual-ready race](flutter-manual-red.txt), [safe errors](flutter-errors-red.txt), [launch admission](flutter-healing-red.txt); production fixes restored.
- Host harness [two focused regressions](host-restoration-green.txt) pass; [primary-failure preservation](host-restoration-red.txt) and [correct profile connection selection](host-selection-red.txt) fail with their fixes removed then restore green.

No full-suite claim. Pub get ran once; pubspec unchanged. No push, tag, PR, release or publication.

## Retained build artifacts

Only newest target and runner APKs are retained. Target command used the pinned Flutter through build lock: `build apk --release --no-pub --target-platform android-x64 --build-number=2201 --android-project-arg=ocStableEngineQa=true`. [2201 build pass](apk-2201-build.txt), 949.9s Gradle, 64.8MB; phone-engine packaging hashes passed.

- Target: `build/app/outputs/flutter-apk/app-release.apk`, versionCode2201; SHA-256 `d0af50d0469a835dcff3e3ed868c63a317e8d5576747ea87aa8f7068d2c14556`.
- Runner: `build/app/outputs/apk/androidTest/release/app-release-androidTest.apk`; SHA-256 `f7a9e1b40a9baeef446d805169b8eb0adca86a5f3a58aa3a663312621979fc46`; [final runner build](runner-pid-build.txt) passed.
- Both match `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`. Single-use owned Gradle daemons exited, owned intermediates and duplicate target outputs removed; [cleanup](cleanup.txt).

Earlier failures are retained honestly: unrelated inherited emulator lock ([blocked](emulator-blocked.txt), coordinator released), an older2190 APK safely refused after another lane installed2201, Flutter fixture-owner race, invalid bootstrap helper name, and `/proc/exe` PID mismatch. Runner-only fixes resolved fixture sequencing/name and mapped process identity; target production source stayed unchanged. They are not crash-recovery failures or completed earlier acceptance claims.

Frontend methods, stored-format migration, rollback behavior, phases and safe errors: [BB2 contract](../../design/BB2-contract.md). Native changes require a later owner-approved new Shorebird release.
