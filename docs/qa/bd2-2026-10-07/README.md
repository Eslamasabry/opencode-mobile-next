# BD2 evidence and skip policy

Finish line: a full Android quality run rejects malformed commit subjects and unformatted changed Dart files. Non-goal: triggering CI, modifying history, installing shared git hooks, or weakening the mandated skip marker.

The existing G33 body/trailer rules and `[skip ci]` remain required. Conventional subjects use `type(scope): plain sentence`, optional scope and breaking `!`. Merge commits retain their exemption from subject/body/trailer rules. Full checkout history permits actual PR base/head ranges. The named commit check is inside required `checks`; formatting uses CI's selected Dart and language version 3.10. A manual full run must supply `commit_base` explicitly.

Policy: keep `[skip ci]` on swarm commits. GitHub skips push/pull_request workflows for that marker, so such PRs require an explicitly authorized full `workflow_dispatch` run on the exact candidate branch with its base revision before merge. Do not claim the marker can be ignored by a pull_request workflow or that adding a step makes skipped workflows run. Branch protection must require the full gate; APK-only runs are not merge evidence (existing release preflight also rejects them). A gate triggered by an unskipped PR checks the same mandatory rules and fails commits lacking the marker.

`python3 -m unittest tool/qa/test_check_commits.py`: 4 passed, covering valid range, malformed subject, missing marker, real pinned Dart format failure. Removed new subject validation: malformed-subject regression failed; restored. `bash -n` passed. Workflow Python YAML parse and required-check dependency read passed. actionlint is unavailable. No CI was triggered.

Reference: [GitHub skipping workflow runs](https://docs.github.com/en/actions/managing-workflow-runs-and-deployments/managing-workflow-runs/skipping-workflow-runs). Skip instructions affect push/pull_request, not workflow_dispatch.

## BD2 refresh — automatic conversation inventory first load

Status: state implementation and focused host qualification PASS. Release
emulator qualification is pending. No completed device result is claimed yet.

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
| Focused state and smoke tests | PASS: 193 Flutter tests, 44 offline Python tests |
| Pinned analyzer | PASS: no issues found (33.2s) |
| Kotlin gate | Not required unless native source changes |
| Release app and matching instrumentation build | Pending |
| Automatic first-load device result and small JPG | Pending |
| Same-lock normal 2195 restoration and launch | Pending |
| Signing, intermediates and owned-daemon cleanup | Pending |
| Local commits with `[skip ci]` | Pending |

Implemented, enabled, verified and committed states will be recorded separately
after execution. No push, PR, tag, release, patch, CI invocation or publication
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

The 11 new tests use WidgetTester's fake clock and explicitly driven streams and
Completers; no sleeps. They cover both reader orders, both protocol gateway paths,
OpenCode 1→2→1 with a held retired read, reconnect, bursts, outstanding reads,
explicit-refresh coalescing, disconnect, disposal and merged-helper independence.
The original state produces missing-fetch failures; removing location reconnect,
inventory invalidation retention or helper independence separately fails the
corresponding test. Restoring the final source passes all 11.
[Structured state verification](state-verification.json) records the focused
manifest and four expected-failure mutations. Kotlin gate is N/A because native
source is untouched; release builds will compile the existing native harness.
