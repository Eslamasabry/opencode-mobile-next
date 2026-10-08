# BD2 evidence and skip policy

Finish line: a full Android quality run rejects malformed commit subjects and unformatted changed Dart files. Non-goal: triggering CI, modifying history, installing shared git hooks, or weakening the mandated skip marker.

The existing G33 body/trailer rules and `[skip ci]` remain required. Conventional subjects use `type(scope): plain sentence`, optional scope and breaking `!`. Merge commits retain their exemption from subject/body/trailer rules. Full checkout history permits actual PR base/head ranges. The named commit check is inside required `checks`; formatting uses CI's selected Dart and language version 3.10. A manual full run must supply `commit_base` explicitly.

Policy: keep `[skip ci]` on swarm commits. GitHub skips push/pull_request workflows for that marker, so such PRs require an explicitly authorized full `workflow_dispatch` run on the exact candidate branch with its base revision before merge. Do not claim the marker can be ignored by a pull_request workflow or that adding a step makes skipped workflows run. Branch protection must require the full gate; APK-only runs are not merge evidence (existing release preflight also rejects them). A gate triggered by an unskipped PR checks the same mandatory rules and fails commits lacking the marker.

`python3 -m unittest tool/qa/test_check_commits.py`: 4 passed, covering valid range, malformed subject, missing marker, real pinned Dart format failure. Removed new subject validation: malformed-subject regression failed; restored. `bash -n` passed. Workflow Python YAML parse and required-check dependency read passed. actionlint is unavailable. No CI was triggered.

Reference: [GitHub skipping workflow runs](https://docs.github.com/en/actions/managing-workflow-runs-and-deployments/managing-workflow-runs/skipping-workflow-runs). Skip instructions affect push/pull_request, not workflow_dispatch.

## BD2 refresh — automatic conversation inventory first load

Status: **BD2 refresh behavior and release emulator qualification PASS**.
The immutable first-commit G33 metadata exception is recorded below for import.

Finish line: the state controller loads the global conversation inventory after
cold start, runtime switch and reconnect without requiring a user gesture or
depending on whether Home has mounted. Closely spaced refresh triggers coalesce
without losing a refresh requested during an outstanding fetch.

Non-goal: UI redesign, agent/model certification, native runtime supervision,
production credentials, publication, CI execution or a full-suite claim.

## Prior evidence and scope

[BD9](../bd9-2026-10-07/README.md#final-local-qualification--pass-2026-10-08)
qualified its release smoke with a user pull-to-refresh. It explicitly left
automatic startup inventory loading unqualified. Its title finder already
accounts for the kit's FSI/PDI isolation; BD2 retains that finder and the private,
read-only loopback fixture.

The QA release target launches the production bootstrap, controller, gateway,
client/SDK and app UI with an injected synthetic profile. Real profile storage
and secure storage are never read or rewritten. The fixture rejects non-GET
requests, has no provider credentials and makes no model calls. BD2 must assert
the visible fixture conversation and global-inventory request before any
pull-to-refresh; fixture write attempts must remain empty.

The state implementation belongs to `lib/state/connection.dart` and its parts.
No `lib/ui/` change is authorized for this slice. Root-cause locations, ordering
and coordinator handoff are recorded in the scratch `sol/BD-status.md` under
`BD2 refresh`; any required frontend API is described there before qualification.

## Qualification commands and device safety

Use only Flutter 3.47.1 from:
`/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
Run `flutter pub get` once in this worktree first. Format changed Dart files
with its sibling `dart format --language-version=3.10`.

Focused regression tests run through `tool/qa/machine_lock.sh test`, serially,
with deterministic fake clocks/streams and no test sleeps. Record the failing
pre-fix result, restored-fix result, test names and exact candidate below. Run
the pinned analyzer at the integration checkpoint. Claude owns the full suite.

The existing dry build planner preserves BD9's QA application version 2201 and
matching release instrumentation target:

```sh
python3 tool/qa/bd9_release_build.py qa
tool/qa/machine_lock.sh build -- "$PINNED_FLUTTER" build apk --release \
  --build-number 2201 --target integration_test/bd9_device_smoke_test.dart \
  --android-project-arg=ocBd9Smoke=true
tool/qa/machine_lock.sh build -- ./android/gradlew -p android \
  :app:assembleReleaseAndroidTest --no-daemon -PocBd9Smoke=true \
  -Ptarget="$PWD/integration_test/bd9_device_smoke_test.dart" \
  -PflutterVersionCode=2201
```

Pin Temurin17 for both Gradle launcher and Java toolchain. Build commands must
allow Flutter's registrant regeneration; do not add `--no-pub` to QA builds.
The pinned engine has no working debug APK route. Do not overlap Flutter host
commands with a native build in this checkout because they can regenerate the
ignored plugin registrant.

Use the existing `tool.qa.bd9_device_smoke.run_device` host with the callback
returned by `tool.qa.bd9_normal_restore.prepare_restore`. The host acquires
`/home/eslam/Storage/tmp/oc-emulator.lock` itself and keeps it through installation,
instrumentation, evidence and restoration on every outcome. Do not wrap an
additional independently opened flock around that helper. Only emulator-5554
is accepted. Preflight both candidate APKs and the owner normal APK against the
unchanged local certificate:
`1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`.

Before releasing the same lock, the restoration callback installs
`/home/eslam/Storage/tmp/oc-apk-share/oc-2195.apk` with
`adb -s emulator-5554 install -r -d`, then checks installed version 2195, a live
process, resumed MainActivity and first-frame marker. Failed restoration makes
the whole receipt fail. Never uninstall, clear application data, change signers
or access another device.

Retain only categorical reports and the fixture JPG (at most 480px wide,
under 200 KB); no raw device output, throwable, provider/config payload or secret
is retained. After each build delete this worktree's
`build/app/intermediates`, remove temporary signing properties, restore Gradle
settings, and stop only the exact owned Gradle daemon PID. Keep the newest app
and matching test APK in place; do not copy APKs.

## Results

| Evidence | Result |
| --- | --- |
| Deterministic pre-fix regression | PASS: deterministic regression |
| Revert-proof: remove fix, fail, restore, pass | PASS: deterministic regression |
| Cold start before feed reader mounts | PASS: deterministic regression |
| Runtime switch OpenCode 1→2 and 2→1 | PASS: deterministic regression |
| Reconnect without list gesture | PASS: deterministic regression |
| Debounce and outstanding-fetch coalescing | PASS: deterministic regression |
| Profile isolation, stale completion and retirement | PASS: focused regressions |
| Focused state and smoke tests | PASS: 195 Flutter tests, 44 offline Python tests |
| Pinned analyzer | PASS: no issues found (17.4s) |
| Kotlin gate | Not required unless native source changes |
| Release app and matching instrumentation build | PASS: final AOT260.7s; unchanged native harness173.5s |
| Automatic first-load device result and small JPG | PASS: global-only row, no input, 25,689-byte JPG |
| Same-lock normal 2195 restoration and launch | PASS: installed version, live/resumed activity, first frame |
| Signing, intermediates and owned-daemon cleanup | PASS: no remaining worktree Gradle daemon |
| Local commits with `[skip ci]` | ddd23e17, fe7052de0; evidence commit follows |

Implemented and enabled in the state layer; host and emulator verified;
source committed locally. The emulator has been restored to normal2195. No push, PR, tag, release, patch, CI invocation or publication
is authorized or claimed here.

### State checkpoint

Base `4fd44e04` starts streams in `connection/connect.dart:380`. Global stream
creation (`connection/events.dart:175`) and its connected callback (`:224`) ask
for inventory reconciliation, but `connection/chat_feed.dart:66` discards the
request until `_feedWanted` is true. Home mount only loads phone-agent rows
(`chats_home_screen.dart:54-67`); its build reads the feed (`:185`), and that
read (`chat_feed.dart:303`) sets the flag without scheduling a request. Thus
stream startup → Home mount loses the only first-load trigger. Selected-folder
rows can conceal the missing global inventory. These are base-source line numbers.

The fix removes the reader gate, retains the two-second debounce, and refreshes
on actual location-stream reconnect as well as global startup/reconnect.
Automatic OpenCode triggers use its own inventory read, avoiding a wait on a
merged agent helper. Explicit user refresh still refreshes all sources.
Single-flight reads are generation scoped; retired completion cannot clear newer
work. Inventory and pending-request invalidations survive a slow read as one
trailing refresh; retirement cancels pending timers. No UI hook, new storage,
migration, user copy or native change is required. Existing feed accessors and
loading/completeness states remain Claude's frontend contract.

The 13 new tests use WidgetTester's fake clock and explicitly driven streams and
Completers; no sleeps. They cover both reader orders, both protocol gateway paths,
OpenCode 1→2→1 with a held retired read, reconnect, bursts, outstanding reads,
explicit-refresh coalescing, disconnect, disposal and merged-helper independence.
The original state produces missing-fetch failures; removing location reconnect,
inventory invalidation retention or helper independence separately fails the
corresponding test. Restoring the final source passes all 13.
[Structured state verification](state-verification.json) records the focused
manifest and six expected-failure mutations. Kotlin gate is N/A because native
source is untouched; release builds will compile the existing native harness.

### Final edge-case checkpoint

A sliding debounce postponed the first fetch when session events kept arriving
within two seconds. Before the correction, both protocol tests observed zero
requests at the first deadline. The controller now retains the first trigger's
deadline: events share a bounded two-second window. Sustained startup and
reconnect events cannot defer the inventory forever. Quiet bursts still produce
one read; slow reads retain a single trailing reconciliation.

The loopback fixture now returns no folder-local sessions. Its visible title can
only come from `/experimental/session`; the smoke never pulls or sends input.
The real SDK fixture test fails if the folder-local row is restored, so a local
conversation cannot hide a missing global inventory in the device proof.

The final source passes all 195 affected Flutter tests plus the clean analyzer
(17.4s). Removing either bounded scheduling or the global-only fixture produces
the expected failures. All six state/fixture mutations fail as expected; exact
source restoration passes 13 state regressions and all 3 fixture tests.
These results supersede the earlier 193-test checkpoint. No full-suite pass is
claimed. The native harness is unchanged: the first QA app and test build pass
(724.8s and 173.5s); the final Dart-only follow-up rebuilds the app and retains
the same version2201/signer-qualified instrumentation APK. The final app artifact is device-qualified in the record below.

Commit metadata exception: immutable source commit `ddd23e17` has `[skip ci]`
but lacks G33's body and author trailer. The local range check reports those
two violations. No commit was amended or rewritten; the coordinator must
account for this when importing the patch. Subsequent commits carry complete
messages. This is not a passing G33 range or a merge-ready full-gate claim.

### Final device qualification — PASS (2026-10-08)

Final application source: `fe7052de0`. Pinned Flutter/Temurin17 release2201
AOT build passes in260.7s, including all six native ELF/source-attestation
checks. The unchanged native instrumentation harness was built from
`ddd23e17` in173.5s; the only later source changes are Dart and evidence, with
no Android diff. Both artifacts have the required stable package/version2201
(where present) and unchanged approved local signer. No older application
artifact is used for this qualification. Exact APK, source and screenshot
identities and commands are in [build.json](build.json).

Command: `python3 /tmp/bd2-qualify-and-restore.py`. The driver explicitly calls
`prepare_restore` before any device access, then passes that callback to
`run_device`. One `/home/eslam/Storage/tmp/oc-emulator.lock` covers install-r,
instrumentation, screenshot collection and the `finally` restoration. The native command is:

```sh
adb -s emulator-5554 shell am instrument -w -r -e bd9Qa true \
  io.github.eslamasabry.opencode_mobile.test/io.github.eslamasabry.opencode_mobile.Bd9DeviceSmoke
```

No other device is used.

[Device receipt](report.json): seven bounded native predicates PASS, exactly
one Flutter test PASS, complete terminal proof, and automatic first conversation
load PASS. The title exists only in the global endpoint's response: the test
requires that HTTP request, actual visible title and zero writes, with no pull
or other input. [Small screenshot](conversations.jpg) was visually checked:
the connected synthetic profile and global-only conversation are visible.

[Normal restoration](normal-restore.json) passes **inside the same lock before
release**: `adb -s emulator-5554 install -r -d` of the approved owner
`oc-2195.apk`, then installed2195, live process, resumed MainActivity and first
frame. No uninstall, clear-data, signer change or production-profile write.

Signing properties and app intermediates are absent; committed Gradle settings
are restored; no Gradle daemon remains in this worktree. The first app's owned
single-use daemon2438611 exited, and owned test daemon2481061 was stopped.
The final app's single-use daemon had already exited by cleanup. The duplicate
Gradle APK was deleted only after digest equality; the newest application and
matching test APK remain in their original output paths. No APK was copied.

No UI file, native source, storage format or localization changed. The existing
frontend feed accessors and loading/completeness states require no new hook.
This proof covers the release emulator and isolated read-only fixture; it does
not claim a physical device, real model, full PhoneEngine acceptance, full suite,
CI run, deployment or release. No push, PR, tag, patch or publication occurred.
