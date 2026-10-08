# BD9 — Release Android device smoke

Date: 2026-10-07

Finish line: release AOT executes seven existing offline PhoneEngine predicates,
then launches the real app against an isolated local HTTP fixture and displays
its conversation in the conversation list. Device proof requires the native
instrumentation result and a small screenshot from emulator-5554.
Non-goal: paid model certification, full PhoneEngine acceptance, Ubuntu setup,
production profile/credential changes, or an automated nightly CI run.

## Scope and safety

The owner approved a dispatch-only workflow. No push, PR, or scheduled trigger
is enabled, and CI has not been triggered during this work. The Sol BD lead
owns the workflow, Gradle integration, signing, builds and device sessions.

`integration_test/bd9_device_smoke_test.dart` is a separate release target.
It launches the production `AppBootstrapGate`, app UI, connection controller,
OpenCode client and generated SDK. An injected profile store exposes one QA
profile and never calls secure storage. SharedPreferences uses the SDK's
in-memory mock store. Existing device profiles and preferences are not loaded
or rewritten. The local wake-lock callback is inert because the fixture is
not a hosted Termux runtime.

The fixture binds only `127.0.0.1` at an allocated port, supplies a synthetic
conversation and projects, and refuses every non-GET request. There is no
provider key, genuine provider endpoint or model call. The smoke requires real
health and global-session HTTP requests, the visible conversation title, and
zero attempted writes.

`Bd9DeviceSmoke` is installed only in the test APK. Explicit `-e bd9Qa true`
and the exact stable application package are required. The existing full
PhoneEngine acceptance and preview-only native regression guards are retained.
`runOfflineSmoke` selects these existing assertions without starting a real
PhoneEngine daemon, mutating service/Ubuntu state, or accessing providers:

| Result token | Existing behavior exercised |
| --- | --- |
| auth_pipe_before_http | Forged child startup authentication is refused before HTTP. |
| private_failure_frames | Only fixed private failure frames propagate. |
| qa_password_file | Genuine password rules are checked using a unique QA file, deleted afterward. |
| authenticated_health_tiers | Only authenticated health tiers are accepted. |
| registered_pdf_identity | Only registered PDF processes are trusted. |
| receipt_before_protection | Signing precedes requiring process protection. |
| stop_callback_order | Remaining services still stop after a stop callback fails. |

These seven offline checks are a bounded subset, not the full native/model
acceptance run. The stable-target guard is not bypassed with a forged package
or context.

Release AOT reports through the native integration_test plugin's public
`testResults` future; no Dart VM service is required. Because release registrants
exclude dev-only plugins, the QA Gradle flag supplies a test-only release
dependency and the native runner explicitly registers that plugin on the
activity engine on the main thread, skipping registration if already present.
Engine lookup uses the public FlutterView attached-engine API; the protected
FlutterActivity engine accessor is not used. The normal release retains the
SDK's filtering and does not receive the plugin. One Flutter test with a
`success` result and all seven native checks are required. Native exceptions
and Dart failure details are not emitted: errors produce the fixed token
`device_smoke_failed`. The host checker validates the full fixed-token report
and terminal success code, and retains a sanitized report only.

The screenshot contains synthetic fixture data. Flutter captures a temporary
PNG in the app's own external files directory; instrumentation writes a JPEG
at width at most 480 pixels, quality 75, and deletes the PNG. The host pulls
`/sdcard/Android/data/io.github.eslamasabry.opencode_mobile/files/bd9-conversations.jpg`.

## Commands

Pinned Flutter binary:
`/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
Dependencies were resolved once by the Sol BD lead after adding the SDK
`integration_test` dev dependency.

```sh
tool/qa/machine_lock.sh test -- "$PINNED_FLUTTER" test --no-pub --concurrency=1 \
  tool/qa/bd9_device_smoke_fixture_test.dart
"$PINNED_FLUTTER" analyze --no-pub integration_test
tool/qa/kotlin_static_analysis.sh

# Coordinator-operated release builds; signing identity must be unchanged.
tool/qa/machine_lock.sh build -- "$PINNED_FLUTTER" build apk --release \
  --build-number 2201 --target integration_test/bd9_device_smoke_test.dart \
  --android-project-arg=ocBd9Smoke=true
tool/qa/machine_lock.sh build -- ./android/gradlew -p android \
  :app:assembleReleaseAndroidTest --no-daemon -PocBd9Smoke=true \
  -Ptarget="$PWD/integration_test/bd9_device_smoke_test.dart" -PflutterVersionCode=2201

# Includes signer checks, install-r, instrumentation and screenshot collection
# under one emulator lock. No other device is accepted.
python3 tool/qa/bd9_device_smoke.py --expected-signer \
  1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C
```

The host helper wraps this exact instrumentation command with the shared
emulator lock:

```sh
adb -s emulator-5554 shell am instrument -w -r -e bd9Qa true \
  io.github.eslamasabry.opencode_mobile.test/io.github.eslamasabry.opencode_mobile.Bd9DeviceSmoke
```

Local device sessions use `/home/eslam/Storage/tmp/oc-emulator.lock`.
`OC_EMULATOR_LOCK` supplies a writable temporary path on the disposable CI
runner. The Sol BD lead restores a normal `lib/main.dart` release APK with the
same version number and signer after the smoke on the shared emulator.

## Verification to date

- Both release-target Dart files formatted with language version 3.10.
- Pinned focused analyzer: **no issues found** after removing an unused import.
- Whole Android detekt gate before the explicit-registration correction:
  **zero new issues**, with the existing 532-finding historical baseline
  unchanged. Includes the new native runner and subset. A final scan of the
  correction is deferred until the Sol BD lead's builds finish.
- Host fixture tests: **3 passed**, covering actual client/SDK decoding,
  rejected model dispatch, and unsupported read routes.
- Regression proof: in an isolated temporary fixture copy, removing the
  non-GET refusal made the model-dispatch test fail (**expected 405, actual
  404**, exit 1). The frozen release source was never changed; the temporary
  copy was deleted. The original fixture rerun passed (1 focused test).
- An early host-test attempt placed its test under `integration_test/`, causing
  Flutter to attempt a debug build. It failed on the release-only engine's
  debug AAR dependency before any installation. That attempt is not device
  proof. The independent unit test now lives under `tool/qa/` and runs on host.

The first release build exposed a pinned SDK distinction: Gradle excludes
dev-only plugins from release dependencies. The QA target needs a guarded
release dependency and registration, while the normal release must omit both.
Host Flutter commands also regenerate the ignored plugin registrant; they must
not overlap a native build in this shared checkout. The Sol BD lead is fixing
and rebuilding that boundary.

The Sol BD lead reports that the QA app release built successfully (99.9 MB),
with version code 2201 and the approved local signer verified. The first test
APK compilation exposed a protected FlutterActivity accessor; the runner was
corrected to the public FlutterView API. Its compilation retry, actual
instrumentation/screenshot proof, and restoration to the normal app remain
pending coordinator evidence. This document deliberately
makes no completed-device or nightly-verification claim before those results.

Frozen device-source SHA-256 values (after explicit plugin registration):

| Source | SHA-256 |
| --- | --- |
| integration_test/bd9_device_smoke_fixture.dart | bdf276b50021b670357e52b0b4dc97dd81bfbd01245f2e10412e7f625ba76f0f |
| integration_test/bd9_device_smoke_test.dart | 3dc383aaad10cc4f47c6cfb641a156f0af5f1574f34c29ce830c7251aa08da56 |
| Bd9DeviceSmoke.kt | b5a9bae0dc20dcd1925b8f14995599e99ddcb47043544d329b3d1d83292c398c |

## Settled build checkpoint

The release AOT APK built successfully (99,880,481 bytes), with all six packaged native ELF hashes and source attestation passing. The matching release androidTest APK compiled after replacing the protected activity engine accessor with public FlutterView access. Signer/package/version checks pass; instrumentation manifests intentionally have no versionCode. The checker accepts that for the test APK while requiring2201 for the application APK.

Final detekt on the corrected runner and Gradle wiring passed with zero NEW findings against unchanged532 historical findings. Host receipt checker:12 tests pass; removing signer/native-completeness/terminal-success/privacy guards makes regressions fail. Reverting support for the instrumentation APK empty versionCode makes its regression fail. [Build identities](build-summary.json) record both APK digests and native runner source. No APK copies were made. All copied signing properties and worktree intermediates were removed; exact owned single-use daemons exited.

The source/build checkpoint is committed separately from device qualification: the helper is waiting for another lead's emulator lock session. Actual device proof and normal restoration remain pending. CI bootstrap was dry-read (exact revision/engine metadata from the pinned installed SDK); all workflow YAML and run blocks parse, manual dispatch is the only trigger. No CI invocation is claimed.


## Public embedding ABI correction

The first signed device attempt did not produce a complete instrumentation proof;
its fixed-code failure receipt is [recorded here](initial-device-failure.json).
The separately shrunk test runner calls public Flutter activity/view/engine/plugin
APIs. The QA-only shrinker now keeps those exact APIs. An initial broad embedding
keep rule retained unused Play Store split/deferred classes and failed R8; the
narrowed rules compile without adding those dependencies. Normal product builds
do not enable these QA rules.

Fresh pinned release2201 build passed (252.9s), followed by matching release
androidTest compilation (117s), including native ELF/source attestation. Artifact
identities are in [build-summary.json](build-summary.json). All copied signing
properties and intermediates were removed; exact owned single-use daemon1934944
was stopped. This checkpoint still awaits the shared emulator lock, complete
instrumentation results/screenshot, and a normal-main-target restore.


## 2026-10-08 coordinator freeze checkpoint

The latest exact-candidate attempt installed both release APKs with the approved
local signer on emulator-5554 under the shared lock. **All seven bounded native
checks pass**, emitted individually as each assertion completes. The Flutter
result bridge returns a failed assertion; it is not a missing-result timeout.
The conversation/screenshot end-to-end smoke remains **unqualified**. The
[fixed-stage receipt](device-failure.json) includes only authored categories and
check names; no raw Dart failure, throwable, provider/config payload or device
log was printed or saved. No passing screenshot exists.

The QA runner now reports fixed failure stages, separates its result timeout
from a failed Flutter assertion, and preserves partial native check names for
local diagnosis. These are test-APK-only changes. The next step after permission
to resume heavy work is to classify the remaining Flutter assertion using fixed
categories, fix the release smoke, rerun the exact host checker, and restore the
normal lib/main.dart candidate with the same signer/build2201. Do not mark BD9
done from the native subset alone.

A local toolchain audit found earlier builds used Ubuntu OpenJDK17. They are
superseded for vendor qualification by an isolated **Temurin17.0.20.1+1** build.
The official Adoptium archive checksum was verified before extraction; its
identity is recorded in build-summary.json. Gradle's daemon and Java toolchain
were explicitly pinned to that JDK without changing machine-wide configuration.
The QA app built in188s and its matching initial test APK in119s; the final
fixed-stage test APK compiled in104s. All native packaging/ELF/source checks pass.
The final native diagnostic additions have not received another detekt checkpoint;
that awaits the coordinator's resume because no Gradle/Java-heavy work is allowed.

The coordinator requested no APK/Gradle builds or emulator sessions while the
full Flutter suite runs. The last bounded session finished before this handoff;
no further session or build was started. Temporary android/key.properties and
build/app/intermediates are absent, original Gradle settings restored, exact
owned final daemon1985521 stopped. **The emulator still has the QA target; the
normal product APK restoration is pending the coordinator's go.** No uninstall,
downgrade, other-device access, push, CI invocation, publication or Shorebird
release/patch was performed.


## Coordinator diagnosis and device handoff requirement

Claude identified the original2201 process failure at22:49:20 as
`java.lang.NoSuchMethodError: getPlugins()` in `Bd9DeviceSmoke.onStart`:
R8 had altered the public FlutterEngine plugin registry API called from the
separately shrunk test APK. This was a QA runner ABI failure, not an application
bug. The narrowed QA-only keep rules in e9b87212 preserve that public API;
subsequent exact-candidate sessions reached the Flutter assertion boundary and
passed all seven native checks. This coordinator diagnosis supersedes the
previous inference about the initial incomplete instrumentation receipt.

Every device session must retain the shared emulator flock from installation
through instrumentation/evidence/cleanup. Existing Python receipt sessions hold
one lock around all these operations; supplemental diagnostic sessions used an
outer flock around the entire command. No device operation is permitted outside
that lock. The eventual handoff must leave the normal application installed and
launchable. That handoff is still incomplete: the QA integration target remains
installed and normal-main restoration is blocked by the explicit coordinator
build/device freeze. The FYI does not explicitly lift that freeze; do not start
another build or device session until the coordinator says go.


## Coordinator integration follow-up (2026-10-08)

The explicit GO lifts the earlier machine/device freeze. Merged coordinator
candidate94d0dbd4 into this worktree in beabc90b before regression repair.
All four coordinator-named files now pass through the shared machine lock:
phone_crash_native_test.dart plus report_problem_capture_test.dart (17 tests),
repository_hygiene_test.dart (15 tests), release_script_contract_test.dart
(2 tests). Commits e80f5b7f,12906231,2cf16291 explain the intentional privacy,
notice-inventory and patch-plan usage snapshot updates. Each original failure
was reproduced first. No product behavior was weakened to satisfy a snapshot.

Full pinned Flutter analyzer reports no issues after this merge. Three BD9
host fixture/SDK tests and12 private receipt-checker tests pass. The Arabic
ratchet also passes with508 remaining missing keys after frontend translations.
No full Flutter suite is claimed by Sol; Claude owns that gate.

The Kotlin checkpoint discovered four new findings in the merged
PhoneAgentHost.kt from another lane (LongMethod, ThrowsCount, MaxLineLength,
MagicNumber). Its owner must resolve them, or delegate that file to Sol;
no baseline expansion is being used to admit them. The two temporary BD9
classifier findings have been refactored and await the next checkpoint.
Device qualification and normal-product restoration continue below.


## Renewed hold and latest device outcome (2026-10-08)

The next locked session again passed all seven native predicates. Flutter
returned a failed result with only generic release diagnostic text; there is
no passing conversation screenshot. The exact private host receipt is recorded
in device-failure.json. The normal product restoration hook ran inside the SAME
emulator flock, but its release build failed at compileReleaseJavaWithJavac:
the generated registrant referenced IntegrationTestPlugin after its dev-only
release dependency was filtered out. normal-restore.json honestly records FAIL.
**The emulator still has the QA target; normal installation/launch remains
incomplete.** No uninstall, data reset, signer substitution or downgrade occurred.

The coordinator renewed the memory hold during the in-flight session. That
existing build/session has ended. No new build or device session will start
until an explicit go. Signing properties and intermediates are absent; original
Gradle settings are restored and no worktree Gradle daemon remains.

The restore failure is traced to the local --no-pub optimization: the pinned
FlutterCommand.regeneratePlatformSpecificToolingIfApplicable returns before
release registrant generation when shouldRunPub is false. The committed dry
planner tool/qa/bd9_release_build.py now permits regeneration for both QA and
normal builds; seven offline tests pass and reintroducing --no-pub fails.
Existing CI builds already permit regeneration. The local authorized driver
now consumes this planner; its corrected build is NOT yet verified on device.

FlutterErrorDetails can omit diagnostic properties in release formatting.
The QA-only reporter now supplies authored phase/type labels through an
explicit toString override, excluding exceptions, frames and metadata. Native
classification accepts only known labels and records empty/multiple result
maps as failure. The host's phase allowlist passes21 tests and its strict PASS
parser remains unchanged. These phase changes still need native/device checks
after the hold. The next allowed device work must restore normal-main2201
FIRST, then resume BD9 diagnosis with restoration on every session outcome.

The final Temurin detekt checkpoint before this hold confirms ZERO new BD9
findings, but four new findings in merged PhoneAgentHost.kt still block the
global gate. Its owner/delegation remains an open question; the baseline was
not expanded. Subsequent code-only phase changes are not claimed native-checked.


Code-only follow-up checks during the renewed hold:

- tool/qa/bd9_smoke_reporting_test.dart:3 focused Flutter tests PASS through
  machine_lock, --no-pub --concurrency=1. Removing the fixed formatter and
  falling back to the SDK's details formatter fails the exact phase test
  (exit1); finally restored source and all3 PASS again. Provider/metadata
  sentinels are synthetic; metadata callbacks are not evaluated.
- python3 -m unittest tool.qa.test_bd9_release_build
  tool.qa.test_bd9_device_smoke:28 offline tests PASS. This covers the
  no-pub command regression, locked restoration, phase allowlist and strict
  result parsing. No signing/build/device action is invoked by these tests.
- Changed Dart files were formatted with the pinned Dart and language3.10.
  The new native phase markers are source-only until go; the previous full
  analyzer checkpoint predates these last Dart additions.


## Separate build GO and emulator authorization (2026-10-08)

The coordinator lifted the build hold and delegated resume steps 2–5. The
former normal-main2201 restore-first step is superseded: Claude owns the
initial normal restoration using APK2195 from feat/genui-fe e2b3fe16, saved
at /home/eslam/Storage/tmp/oc-apk-share/oc-2195.apk. Builds and checks use
machine_lock; device sessions still await a separate EMULATOR GO.

Every subsequent local QA run must retain one emulator flock through install,
instrumentation, evidence and restoration. The restoration callback preflights
the supplied owner APK's package/version2195/local signer/hash before any QA
session, then installs it with adb -s emulator-5554 install -r -d inside the
same flock. No normal release build or APK copy is needed. Normal launch must
be proved after restoration; a failed restore keeps the overall receipt FAIL.

This owner-authorized downgrade replaces the prior no-downgrade plan solely
for restoration to the approved2195 APK. Never uninstall or change the signer.
The strict seven-native/one-Flutter PASS parser remains unchanged. No device
work or passing screenshot is claimed by this authorization update.

Full pinned analyzer: no issues (15.9s). Focused host-native/contract tests:
20 PASS serially through machine_lock. Fixture and fixed phase-reporting tests:
6 PASS serially through machine_lock. Existing command/receipt Python tests:
28 PASS. Detekt and the updated QA build follow once the shared build lock
is available. No full-suite claim or CI execution.


The native static checkpoint is now green (40cbde67): zero new findings with
Temurin17, detekt1.23.8 and the unchanged baseline. Reverting the five corrected
findings causes the real gate to fail; restored source passes again.

Reusable owner restoration: tool/qa/bd9_normal_restore.py exposes
prepare_restore(apk, expected_signer, receipt_path). It performs APK-only
preflight before QA and returns a callback for run_device's existing same-flock
finally path. The callback rejects changed hashes/wrong device prefixes,
requires install -r -d Success, verifies installed version2195 and a live
resumed MainActivity plus its first-frame marker within a bounded poll, and
writes only categorical receipts. Synthetic image cleanup occurs after
installation and cannot prevent restoration. Raw command output stays in
memory. No builds or APK copies happen in this helper.

Offline restoration tests:16 PASS; combined with command/receipt tests:44 PASS.
Removing the authorized -d flag fails its regression (exit1), finally restored
helper passes again. Existing callback tests already prove the emulator flock
is held through restoration on both smoke success and failure.
The helper is implemented/unit-verified; actual owner APK and device launch
qualification still require the coordinator's file and EMULATOR GO.
