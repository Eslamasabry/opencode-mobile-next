# Job net — Android network state and download consent backend

Date: 2026-09-27. Worktree/branch: `oc_app-codex-net` / `codex/net`.
Base: `6ea5e83c`.

**Finish line:** expose tested Android network state and a per-profile mobile
download consent gate with trustworthy size inputs, and record a clean Android
scanner build result. **Non-goal:** UI redesign, connecting-screen or consent
presentation hookup, publishing, production signing, or device installation.

## Ownership and verification scope

Three independent implementation slices: network MethodChannel/EventChannel
(Dart and Kotlin owned together), consent/persistence/download size contracts,
and scanner dependency/build investigation. The coordinator owns this handoff,
integration review, focused tests, analyzer, and local commits. Heavy Flutter
and Gradle commands run serially. No screens or kit components are changed.

| Slice | Read set | Exclusive write set | Acceptance/dependency |
| --- | --- | --- | --- |
| Network | platform capabilities, existing channels/manifest | `platform/network.dart`, `NetworkMonitor.kt`, `MainActivity.kt`, network tests | typed snapshots/events, unknown fallback, lifecycle; independent |
| Consent | profile deletion, pinned voice/setup manifests | consent owner/new gate/size files, setup contracts/components, consent and voice-component tests | durable claim, guarded callback, deletion, size evidence; frozen network types |
| Scanner | pinned Flutter, plugin sources, Gradle outputs | `pubspec.yaml`/lock if upgrade verifies, temporary ignored build inputs | clean release reproduction and final Kotlin compile; final source candidate |

The user requested focused tests and analyzer for this job; this handoff does
not claim the repository's full stable-integration suite has passed.

## Android semantics

Network state is advisory device state, not a probe of a saved server.
Validated Internet access, an active default network, transport, and metering
are separate facts. An unvalidated Wi-Fi network may still reach a LAN server.
No default network must never prevent reaching a built-in/loopback server.
Desktop/web return unknown. Unknown is never equivalent to mobile or Wi-Fi.

The implementation follows Android's [network-state guidance](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)
and [callback contract](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback):
callback capability data comes from the callback argument, and a lost old
default network must not overwrite a replacement network's state.

## UI hookup contract for Claude

### Root connecting state

Import `lib/platform/network.dart`. Own one `NetworkState`, listen as a
`ChangeNotifier`, call `start()` once, `refresh()` on foreground/resume, and
`dispose()` with its owner. Read `value.status`, `.transport`, `.metered`, and
`.connected`. Initial, unsupported, failed, and ambiguous readings are unknown.
`NetworkBridge.current()` reads a fresh snapshot; `readings()` shares one
native event subscription among listeners.

Use `value.hasNoNetwork` for the root-connecting advice in `lib/main.dart`.
Do not derive this advice from `status == offline`: captive/unvalidated Wi-Fi
may still reach the user's server. Do not suppress built-in/loopback connection
attempts. No root screen hookup is enabled in this backend-only branch.

Wire channels: `oc/network` method `current`, and `oc/network/events` initial
snapshot plus changes. Payload fields are `status` (`unknown/offline/online`),
`transport` (`unknown/wifi/mobile/other`), nullable `metered`, and nullable
`connected`. VPN transport is unknown rather than an inferred underlying link.
No new Android permission is needed beyond existing `ACCESS_NETWORK_STATE`.

### Mobile download consent

Obtain one owner using `ConsentOwners.mobileDownloads(prefs, profileId)` for a
saved profile. Pass a `DownloadSize` and `const NetworkBridge().current` to
`request` (preflight) or `run` (authorize and invoke a download callback).
The threshold is strictly greater than **50,000,000 bytes**, on explicitly
mobile transport. Metered Wi-Fi is not relabeled as mobile.

```dart
final consent = await ConsentOwners.mobileDownloads(prefs, profileId);
final result = await consent.run<void>(
  size: DownloadSize.voicePack(manager.selectedPack),
  readNetwork: const NetworkBridge().current,
  operation: () => manager.downloadSelected(),
);
// Handle result.decision.kind/reason as described below.
```

Keep the selected pack/setup plan stable between sizing and callback invocation.
After a prompt or a changed selection, prepare it again before retrying. Bind the
gate to the profile that initiated the operation, not whichever profile becomes
active while the prompt is open.

Handle the typed decision:

- `allowed`: `run` invokes the supplied operation. A preflight decision alone
  must not be retained as a future authorization.
- `needsConsent`: this caller owns the first prompt, already claimed on disk.
  Present a kit consent at the action. Await `answer(allow: ...)`, then call
  `run` again so transport and consent are rechecked. Back/cancel is denial.
- `blocked`: explain offline, declined, or unfinished as selected by `reason`.
  Offered/denied answers do not automatically prompt again.
- `unavailable`: show a plain-language recoverable explanation for unknown
  network/size, unavailable storage, or a closed owner. Do not start a callback
  through the gate without an allowed result.

Settings can read `choice` (or use `ConsentOwners.mobileDownloadsLoaded`) and
explicitly call `changeFromSettings(allow: ...)`. Add the mobile-download row to
Your answers alongside the existing consent rows; this job adds no UI copy.
Map enum reasons to localized plain words, and route operation diagnostics to
the existing Details treatment. Never render exception strings as the message.

The owner stores only version and enum choice at
`oc.mobileDownloadConsent.<profileId>`. `ConsentOwners.closeProfile` drains its
writes before the existing profile preference sweep. No migration or shared
blob is needed. A failed storage write fails closed. Closing the owner prevents
later starts but does not cancel an already-running transfer.

This is a **start gate**, not a transfer monitor. No existing download call
site is automatically intercepted. Claude must wrap manual voice download and
redownload (`lib/voice/voice_ui.dart`), automatic voice setup
(`lib/voice/automatic_setup.dart`), and setup run/retry/resume entry points.
An active transfer switching from Wi-Fi to mobile needs a separate pause/cancel
policy; this API does not claim to enforce that transition.

### Trustworthy sizes

`DownloadSize` distinguishes exact, lower-bound, estimate, and unknown data.
`DownloadSize.voicePack(pack)` accepts shipped pinned voice manifests, whose
file lengths are checked by the downloader. `SetupComponent.downloadSize` and
`SetupAppOffer.downloadSize` carry consent evidence separately from the older
display/preflight estimate `downloadBytes`.

AI Team setup exposes the supported Android architecture's pinned archive sum
as a lower bound, excluding guessed apt-package bytes. `VoiceSetupComponent`
offers the actual selected pack's exact payload, or zero when already installed.
Core Linux/apt/npm setup estimates stay unknown for consent. Do not turn the
display values, arbitrary HEAD responses, or fallback architecture into proof.
For an app component, obtain its fresh `app.offer()` and carry that result with
`component.withAppOffer(offer)` before summing; `withDownloadBytes` by itself
updates only display bytes. A stale installed-zero offer is not authorization
for a later replacement download.

Use `DownloadSize.sum` for the components actually selected to install,
including dependencies, excluding already-installed components and steps that
only start the server. Unknown parts preserve the known sum only as a lower
bound. A lower bound above the threshold proves consent is needed; one below
it cannot prove the whole job is small. Payload sizes describe a fresh install,
not the remaining bytes after partial downloads/cache reuse.

## Verification and scanner investigation

Toolchain confirmed during the reproduction: the exact pinned Flutter 3.47.1 /
Dart 3.13.1 revision, Gradle 9.5.0, AGP 9.3.2. Formatting uses the requested
Dart language version 3.10. **Vendor deviation:** both builds use the available
Ubuntu OpenJDK `17.0.20+8-1-24.04-Ubuntu`, not the Temurin vendor named in
AGENTS.md. No installed Temurin was found; the comparison uses the same Java
runtime for both scanner versions.

Scanner baseline: `flutter clean` then
`flutter build apk --release --build-number 1`, pinned `mobile_scanner: 7.4.0`,
**passed** (exit 0; Gradle assemble 1037.9 seconds; APK 88.6 MB). The native
`MobileScannerPlugin.class` and plugin compile/runtime JARs were present.
The slow phase was verified to be active R8 shrinking under shared-host memory
pressure, not a missing scanner class or dependency wait.

No `android/key.properties` existed initially. Reproduction uses an authorized
temporary throwaway key, not either maintainer signer. No APK is delivered or
installed.

Scanner comparison: `flutter clean` then
`flutter build apk --release --build-number 1`, pinned `mobile_scanner: 7.4.2`,
**passed** (exit 0; Gradle assemble 295.5 seconds; APK 88.7 MB). The scanner
class is present in AGP's `built_in_kotlinc` output and compile JAR. Keep the
verified 7.4.2 upgrade; its lockfile change is limited to version and archive
checksum. Third-party notices track the new version.

**Cause finding:** the original `GeneratedPluginRegistrant` missing-symbol
failure did not reproduce with either clean version, so its root cause is
unconfirmed. Stale/incomplete generated build intermediates are a possible
explanation, not a proven diagnosis. Both package sources contain the plugin
class. 7.4.2 migrates to Gradle Kotlin DSL and removes the old explicit Kotlin
source-directory entry, but the clean compile proves that omission does not
prevent compiling the scanner in this configuration. 7.4.2 also removes
`mobile_scanner` from Flutter's legacy Kotlin-plugin warning (other plugins
still trigger that warning). No plugin-cache patch or Gradle workaround was
needed.

Requested native check on 7.4.2: `cd android && ./gradlew
:app:compileReleaseKotlin` **passed**, exit 0, `BUILD SUCCESSFUL` in 2m38s
(177 tasks: 5 executed, 172 up-to-date). This also compiled the new network
channel. Temporary `android/key.properties`, the throwaway keystore, and this
job's generated `android/.kotlin` cache were removed and absence verified.
No signing material is staged or committed.

Raw local build evidence is retained outside Git at
`/home/owner/Storage/tmp/claude-tmp/oc-net-scanner-_tbsijqm/`; it includes the two
clean/build logs, plugin-class/JAR checks, Java version, and Kotlin task output.

Backend checkpoint (scanner 7.4.0, final backend source):

- Pinned `dart format --language-version=3.10 --output=none
  --set-exit-if-changed` on the ten changed Dart files: pass, zero changes.
- `flutter test --concurrency=1 --reporter expanded
  test/network_test.dart test/mobile_download_consent_test.dart
  test/setup_voice_component_test.dart`: **42 passed**.
- `flutter test --concurrency=1 --reporter expanded
  test/setup_engine_test.dart test/consent_in_flow_test.dart
  test/no_raw_error_text_test.dart test/architecture_boundaries_test.dart`:
  **53 passed, 2 pre-existing failures**. Setup, consent, raw-error, and the
  remaining architecture checks passed. Failures are ARCH-1 (API imports in
  `tools_screen.dart`, `product_states.dart`, `session_handoff.dart`) and ARCH-2
  (`ServerFlavor` checks in `session_handoff.dart`). These are not new gate
  exemptions: `git diff --exit-code 6ea5e83c -- lib/ui
  test/architecture_boundaries_test.dart test/architecture_boundaries_baseline.json`
  returns 0, confirming the scanned UI, guard, and baseline are unchanged.
- `flutter analyze`: **clean**. The first run found one unnecessary test
  import; it was removed and the analyzer rerun successfully (no ignores).

Final 7.4.2 test candidate: ran serially with `--reporter expanded`:

```sh
flutter test --concurrency=1 --reporter expanded \
  test/network_test.dart test/mobile_download_consent_test.dart \
  test/setup_voice_component_test.dart test/setup_engine_test.dart \
  test/consent_in_flow_test.dart test/no_raw_error_text_test.dart \
  test/pairing_scanner_test.dart test/kit/kit_scanner_test.dart
```

Result: 106 passed, two old scanner test-copy expectations failed. Both expected
`Try again` on permission denial; the unchanged screen and English localization
already say `Allow camera`, before any scanner plugin is constructed. Updated
only those test expectations (including the permanent-denial absence check),
then reran **only** `test/pairing_scanner_test.dart`: **12 passed**. No production
source changed after the 106-pass batch; the seven other selected files retain
their passing coverage. All 108 selected cases are covered by this batch and
the corrected-file rerun. The separate ARCH-1/ARCH-2 failures above remain
outside this backend/scanner job; their baseline was not relaxed.

Final `flutter analyze` on 7.4.2 after the test correction: **no issues found**
(34.4 seconds). Final formatting and staged whitespace checks pass. Only the
listed source, tests, dependency notices, and this handoff are committed;
`COMMIT_MSG.txt`, signing files, and generated build/cache output are excluded.

The full repository suite and device transport switching are not claimed.
Device follow-up: Wi-Fi/mobile/airplane changes, captive Wi-Fi still connected,
VPN transport unknown, metered Wi-Fi distinct from mobile, foreground resume,
and connecting to a loopback server while no default network exists.
