# BB1 — service lifetime diagnostics

Candidate branch: `sol/bb-runtime`. Native diagnostics persist only service names,
exit codes, durations and restart counters in private device-wide preferences.
Current uptime uses Android's monotonic clock. Exit 137 reports a possible memory
or phantom-process kill; it does not distinguish these from another SIGKILL.

## Acceptance state

Implemented and enabled through the existing diagnostics API. Focused Dart/JVM checks, analyzer, release QA compilation and API 35 x86_64 emulator acceptance passed.
No claim of release, physical-device certification or full-suite success.

## Checks

- Pinned Flutter `pub get`: passed once in this worktree.
- Pinned Dart format (`--language-version=3.10`): changed Dart files formatted.
- `tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 test/builtin_performance_diagnostics_test.dart`: seven pass. Removing the service parser failed the channel round-trip test; restoring it passed seven.
- `tool/qa/machine_lock.sh build -- <gradle-9.5.0> -p android --no-daemon --max-workers=1 :app:testReleaseUnitTest --tests io.github.eslamasabry.opencode_mobile.ServiceDiagnosticsTest`: seven pass, native release compilation passed. Initial run used host OpenJDK 17; the regression/restore run uses Temurin 17.

Frontend integration: [BB1 contract](../../design/BB1-contract.md).

## Emulator procedure

Only `emulator-5554`, every session under `/home/eslam/Storage/tmp/oc-emulator.lock`.
Release QA APK retains the native test ABI (`ocStableEngineQa=true`). Test signer
must equal `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`.
Standalone test-APK runner: `BuiltinRuntimeAcceptance`, steps `diagnostics` then
`persisted` after force-stop. It owns only the `qa-bb1` service, runs synthetic
`sleep 2; exit 137` and `sleep 2; exit 7`, and emits numerical health fields only.
It does not read server output, credentials, provider state or existing service
commands. Force-stop is expected to halt the app; BB1 does not claim recovery.

- Temurin 17 regression proof: changing exit 137 to `unknown` failed the exact JVM classification test; restoring it passed seven JVM tests (42 s).
- Multi-ABI release build was stopped by exact owned PIDs when shared storage fell below 1 GB; no APK acceptance is claimed for that interrupted build. Only this worktree's `build/app/intermediates` was removed. Emulator-only x86_64 release build retry is in progress.

- First x86_64 release build passed packaging checks (64.8 MB), SHA-256
  `e6b8ea22270a66a6e1e101e97af12d9e3041808e57cf55cc0aaf6fe198659c9d`;
  APK certificate matched the authorized 1DE5BF08 test identity.
- Initial locked emulator install was refused without changing installed data:
  branch build code 52 is below emulator build code 2187. `install.txt` records
  Android API 35 / x86_64 and `INSTALL_FAILED_VERSION_DOWNGRADE`.
- QA-only retry uses `flutter build apk --release --no-pub --target-platform android-x64 --build-number=2187 --android-project-arg=ocStableEngineQa=true` under the build lock. `pubspec.yaml` is unchanged.
- Test runner build requires `-PocBuiltinRuntimeQa=true -PocStableEngineQa=true -Ptarget-platform=android-x64 :app:assembleReleaseAndroidTest`; default phone-engine runner is unchanged when that QA property is absent.

## Final emulator result

`emulator.txt`: both APKs installed in place with the authorized signer. Exit 137
was observed at 2110 ms, then exit 7 at 2115 ms with restart count 1. After
force-stop, a new app process exposed exactly exit 7 / 2115 ms / one restart,
with no current uptime. The native profile deletion path erased both seeded
profile-linked diagnostic records and their keys.

Final target QA APK SHA-256:
`d7b71e69468116cc1d0af40b4c4882c436d8f2668a2dbc7e22438a47d5d090fa`.
Build code override 2187 was used only for the emulator QA artifact. The release
AndroidTest APK compiled successfully with Temurin 17. All owned Gradle daemons
were stopped by exact PID or `--no-daemon`; this worktree's app intermediates were
removed after each completed or interrupted build. Only the newest target APK
and its necessary test runner APK remain. No screenshots are needed for this
backend fixture; no UI changed. Native changes need a later maintainer release.

Reproduce with:

```bash
python3 tool/qa/bb1_runtime_acceptance.py \
  --apk build/app/outputs/flutter-apk/app-release.apk \
  --runner-apk build/app/outputs/apk/androidTest/release/app-release-androidTest.apk \
  --apksigner /home/eslam/Android/Sdk/build-tools/36.0.0/apksigner \
  --out docs/qa/BB1-2026-10-07/emulator.txt
```

The runner owns the short device lock session. It does not uninstall, enumerate
other devices, read credentials, launch prompts or print arbitrary Android errors.

Final affected Dart checkpoint: both `test/builtin_linux_test.dart` and `test/builtin_performance_diagnostics_test.dart` passed, 24 tests total, pinned Flutter through the shared test lock with `--no-pub --concurrency=1`.
