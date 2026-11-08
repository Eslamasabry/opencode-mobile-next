# Native CI lanes

Workflow: `.github/workflows/native-ci.yml` (branches `native/screens-merge*`, `native/ci-*`, and any branch by dispatch).

| lane | what runs | when |
|---|---|---|
| `fast` (default for a dispatch) | `plan` picks the modules changed against `base` plus their direct dependents (`tool/ci/fast_tasks.sh`); `compile` the app; desktop tests of those modules in 1-3 shards with `-PskipGoldens=true`; the preview APK. All jobs in parallel. | every iteration |
| `full` (push default) | every module's desktop tests in 4 shards, picture tests included, compared with the committed CI-recorded goldens; `compileAllTests`; the APK | release gates, after merging |
| `goldens` | `-Poc.recordGoldens=true -PonlyGoldens=true` on every module that wires `oc.goldens.record`; uploads artifact `ci-goldens` (a tgz of the `src/desktopTest/goldens` trees) | after a deliberate look change, or when a runner image changes fonts |

`tasks` (custom Gradle tasks) and `apk_only` work as before and ignore the lane. The last job, `summary`, prints one table
(compile, tests run/failed, new vs base, APK artifact name) and the first failing tests, so a red run reads from the run page.
"New vs base" diffs against the newest run on the `base` branch that kept `report-test-*` artifacts (14 days); with none it says so.

## Dispatch

```
gh workflow run native-ci.yml --ref <branch> -f lane=fast                 # base defaults to native/screens-merge41
gh workflow run native-ci.yml --ref <branch> -f lane=fast -f base=native/s33-theme-motion
gh workflow run native-ci.yml --ref <branch> -f lane=full
gh workflow run native-ci.yml --ref <branch> -f lane=goldens
gh workflow run native-ci.yml --ref <branch> -f tasks=":ui:kit:desktopTest"   # as before
gh workflow run native-ci.yml --ref <branch> -f apk_only=true                 # as before
```

## Landing a change: `native/next` (push to phone in minutes)

`native/next` is the integration branch (created from `native/screens-merge41`). It replaces the merge waves: a builder merges
their own finished branch into it as soon as the fast lane is green.

```
git fetch mobile-next
git merge mobile-next/native/next            # on your branch: fix conflicts HERE, then re-run your fast lane if code changed
git push mobile-next HEAD:native/next        # plain fast-forward push
gh workflow run native-ci.yml --ref native/next -f apk_only=true   # builds and uploads artifact native-next-apk
```

- Rules: merge and push; never force-push `native/next`; if the push is rejected, someone landed first: merge `native/next` into
  your branch again and retry. Conflicts are resolved on your branch, never on `native/next`.
- Why the dispatch: every commit carries `[skip ci]`, and GitHub then suppresses `push` triggers, so a push alone builds nothing.
  The dispatch is one command and works because `native-ci.yml` is on the default branch. (A push whose tip commit lacks
  `[skip ci]` would trigger the push lane instead; do not rely on it.)
- The preview APK (signer 12de0107, package `io.github.eslamasabry.opencode_mobile.native`, versionCode from `native/version.properties`,
  10010: equal versionCodes install fine with `adb install -r`) is uploaded as `native-next-apk` (7 days).
- On the owner's PC `phone-sync.sh` (systemd user timer `phone-sync.timer`, every minute) downloads the newest successful
  `native-next-apk`, checks the signer and package, installs it with `adb install -r` unless the native app is in the foreground
  (it retries next minute), copies it to `/home/eslam/Storage/tmp/oc-apk-share/native-next-latest.apk`
  (http://100.126.15.6:8765/native-next-latest.apk), appends a line to `/home/eslam/Storage2/tools/phone-sync.log` and sends a
  desktop notification. Read the log to see what the phone has.

## What counts as a picture test (`-PskipGoldens`, `-PonlyGoldens`; tool/ci/goldens.gradle.kts)

A class in a `*Test` source set (not `androidHostTest`, which CI does not run) that mentions `FlutterGoldenGate.`,
`oc.goldens.dir|record`, `compareGolden`, `looks-out` or imports `LooksCapture`, plus files named `*Golden*` /
`LooksCapture*` in `ui/` and `feature/`. Found by reading the sources: a new golden test needs no list edit.
Two kinds exist:

* Skiko desktop goldens (`feature/{chat,chats,chat-composer,review,settings,usage,voice}/src/desktopTest/goldens`):
  compared with the committed PNG, re-recordable. The CI runner is the reference now.
* Flutter gate shots (`FlutterGoldenGate`, the `rb/*GateTest` classes): compared with the frozen Flutter picture
  extracted from git history; not re-recordable, they stay in the full lane only.

## Re-recording

1. `gh workflow run native-ci.yml --ref <branch> -f lane=goldens`
2. `gh run download <run-id> -n ci-goldens` then `tar xzf ci-goldens.tgz` at the repo root
3. Look at a sample, commit the PNGs (`[skip ci]`, Change-Class). Never record on a PC: its fonts differ from the runner's.

Local, if you must: `./gradlew :feature:chat:desktopTest -Poc.recordGoldens=true --tests '*SomeTest*'`.

## Nightly schedule: not added

A `schedule:` trigger only runs from the default branch's copy of the workflow, which this work must not touch, and a
full run costs about 20 runner-minutes x 6 jobs. With a limited CI budget the full lane stays on demand and on pushes
to `native/screens-merge*`.

## The gate (summary job): what is FAIL and what is noise

The `summary` job prints `## Native CI: PASS` or `FAIL` with the reasons first. On work branches the job has
`continue-on-error`, so the run stays green and nobody is mailed, but the table still says FAIL; on `native/next`,
`native/screens-merge*` and `master` a FAIL turns the run red.

Always FAIL, never noise (`tool/ci/summary.py`, `tool/ci/test_report.py` reading the shard's Gradle log):
- a failed compile, APK, record or custom job;
- any Gradle task failure that is not a test task reporting failing tests: test compile, resource or strings generation,
  drift checks, detekt, konsist, any custom check;
- a selected module with no test results (its task never ran);
- a failing test in `quality`, `tools/strings`, `ui/words`, `core/security`, `ui/gate`, or in a Drift / Konsist / Detekt / Lint / SpecFile / Glossary class.

Noise (does not FAIL): an individual failing test class from a test report that also fails in the base reports, is listed in
`known_failures.txt`, or is quarantined. `tool/ci/test_gate.py` (run by the `plan` job) plants logs for each case.
