# APK 2197 queued device work

Finish line: rerun the five pending OC2 assertions on the approved normal APK
2197, retain scoped pass/fail evidence and exact cleanup/restoration, and record
FQ9 preconditions without manufacturing an upgrade baseline or a clean device.
Non-goals: Claude enrollment/session changes, new builds, application changes
without a reproduced defect, long shared-device reservations, resets or pushes.

Branch `sol/bc-2197-device`, base `9485f0c7c7c39e721d50fb036a76ae77596d14f0`.
Root owns FQ3 adapters/device runner, FQ9 ports and device execution. Three
independent reviewers cover model/image, FQ9 baseline/first-run prerequisites,
and background/evidence limits; they launch no device, test or build jobs.
The FQ9 reviewer owns only additional installed-package identity fixtures.

Coordinator-approved normal APK: `/home/eslam/Storage/tmp/oc-apk-share/oc-2197.apk`.
Verify posted SHA-256, package/build and installed same signer before mutation.
All device work holds `/home/eslam/Storage/tmp/oc-emulator.lock`; each phase has
a unique run/session title and cleans only its owned sessions. Restore normal
2197 with `adb install -r` inside that lock when needed, retaining application
data. No Claude sign-in/logout/configuration actions.

Five standalone OC2 phases do not certify other capabilities or mix older
2196 results into a 2197 full-run matrix. Actual failures remain failures.
FQ9's 30-minute gate conflicts with the coordinator's current short-session
constraint and remains deferred until a reservation is supplied. Upgrade
requires an exact already-installed previous baseline and seeded receipts;
published-stable requires verified provenance and a compatible signer; fresh
first run requires a separately provisioned clean AVD. Missing preconditions
remain unqualified and never justify downgrade, uninstall or data clearing.

No build is needed. Any future build requires at least 6 GB available memory,
the coordinator's capped `GRADLE_OPTS`, and exact owned Gradle-daemon shutdown.
Flutter/Dart checks remain serial under `OC_TEST_SLOTS=1` machine_lock.
