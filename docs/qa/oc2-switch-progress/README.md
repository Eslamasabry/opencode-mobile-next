# OCSW-PROGRESS-01 — fresh progress for each runtime switch

Base: a2525f0e6398a0cfdd77546ab8043d0aeec62b2e. Branch: fix/oc2-switch-progress. Owner: onboarding_journey.

Finish line: a newly requested runtime switch shows no prior operation elapsed time or output, then uses its manager-owned durable clock and log across app restart; bounded earlier logs remain available.

Non-goal: change switching, data isolation, profiles, installation retries, layout or copy. The coordinator owns native testing and integration. This branch performs no service operations.

## Confirmed findings and correction

Native review of the base candidate found that switch launch retained completed OC1 status, showing 99m 30s elapsed, and appended old OC1 apt output before new OC2 output. The dispatch logger also inherited result file descriptors, delaying asynchronous acknowledgement until installation finished.

The switch launch now clears the old status until the matching manager snapshot supplies durable timing. The detached dispatcher redirects to `/dev/null`; the manager rotates the install log only after acquiring its operation lock, then starts its own logger. The existing two bounded history files remain available. Status polling and reopening do not rotate logs.

## Verification

Pinned Flutter 3.47.2: `/home/owner/.shorebird/bin/cache/flutter/e16cf749ccaa38d7050335ff305def49b1c7c84c/bin/flutter`.

All checks ran serially in this worktree, after pinned `flutter pub get`:

- `flutter test --no-pub --concurrency=1 --reporter expanded test/termux_runtime_switch_test.dart`: 15 passed.
- `flutter test --no-pub --concurrency=1 --reporter expanded test/termux_scripts_test.dart test/termux_recovery_scripts_test.dart test/termux_setup_screen_test.dart`: 91 passed.
- Scoped `flutter analyze --no-pub` on the four changed Dart files: no issues.
- `dart format --output=none --set-exit-if-changed` on those files: zero changes.
- `git diff --check`: clean.

The 106 final focused cases cover held dispatch hiding the previous elapsed clock, matching durable timing after acknowledgement and route recreation, prompt acknowledgement while the detached manager remains active, fresh current output with bounded `.1`/`.2` history, unchanged logs after status reopening, and no rotation without lock ownership. Existing switch preservation and recovery cases remain green.

An initial UI run failed only because the new test used an obsolete action label; the selector was corrected to the current “Switch version” text. The exact regression and final affected file both passed afterward.

Logs are saved outside the repository at `/home/owner/Storage/Code/oc_app-ui-audit-20260909/oc2-setup/`: `progress-script-tests.log`, `progress-final-focused.log`, `progress-ui-clock-rerun.log`, and `progress-analyze.log`. The initial failure log is retained as `progress-ui-tests.log`.

Status: implemented and verified by focused host/widget checks. Final integrated APK acknowledgement, output and clock validation remains coordinator-owned; this branch makes no new device verification claim. No new screenshot is required for unchanged layout and copy; the defect concerns operation timing and streamed output.

## Final source and test hashes

```text
9936b72d54953d6ac7a1e4eaee8c6bf3a539fdc21ccb077b17dd226f10d30c59  lib/termux/bridge.dart
aa4f8960af54baac649e7df69ab489b294f7a486abfd09f7992235f69f427beb  lib/ui/screens/termux_setup_screen.dart
084f074b6baf7398f3cacda0dfcf1b1d1dfcc0b55f3ece877d975e291450313f  test/termux_runtime_switch_test.dart
e730bc242614d7f0db9e53ee0c514ef9aa1d4604492142f9161cfc2bc346da0b  test/termux_setup_screen_test.dart
```
