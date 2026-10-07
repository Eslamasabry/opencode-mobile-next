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
