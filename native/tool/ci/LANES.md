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

`native/next` is the integration branch (created from `native/screens-merge41`). Owner direction: merge first, then test
everything once. There are no per-branch full test waits and no merge waves.

1. Land after your branch merely COMPILES: the fast lane's `compile` job green on your branch, or a local compile of the
   modules you changed. Do not wait for the desktop tests.
2. Merge `mobile-next/native/next` into your branch (fix conflicts there, never on `native/next`), then push without force:
   `git fetch mobile-next && git merge mobile-next/native/next && git push mobile-next HEAD:native/next`.
   A rejected push means someone landed first: merge again and retry. Use a commit subject whose scope is your module
   (`fix(feature-chats): ...`, `feat(engine-composer): ...`), so a red run is traceable to the module's owner.
3. Do nothing else. The batcher (`/home/owner/Storage2/tools/next-batcher.sh`, systemd user timer, every 2 minutes) sees the new
   tip, and when no `native-ci` run on `native/next` is in progress dispatches ONE fast-lane run
   (`-f lane=fast -f base=<last green native/next sha>`), so the fast lane tests every module changed since the last green run,
   and the same run builds the preview APK and uploads `native-next-apk`. Everything that landed meanwhile is tested together once.
4. If the run goes red, the agent whose module failed fixes forward right away, as a new commit on `native/next` (same
   steps). Do not revert other people's work. The run summary lists the failing modules; `next-batcher.log` records each
   dispatched sha, the result and the last green sha.
5. On the owner's PC `phone-sync.sh` (timer, every minute) installs the APK only from a finished run whose gate is green
   (`summary.py` already treats the known pre-existing failures in `known_failures.txt` as green). A red run is logged as
   `skipped: red` with the failing modules and a desktop notification; the phone keeps the last good build. A green run
   is verified (signer 12de0107, package `io.github.eslamasabry.opencode_mobile.native`), installed with `adb install -r`
   unless the native app is in the foreground (it retries every minute), copied to
   `/home/owner/Storage/tmp/oc-apk-share/native-next-latest.apk` (http://<tailnet-host>:8765/native-next-latest.apk), and logged
   in `/home/owner/Storage2/tools/phone-sync.log`.

Rules: merge and push; never force-push `native/next`. Every commit carries `[skip ci]` (GitHub then suppresses push triggers,
which is why the batcher dispatches). versionCode stays 10010 (`native/version.properties`); equal codes install with `-r`.
A manual `gh workflow run native-ci.yml --ref native/next -f apk_only=true` still works for an immediate APK (not gated on tests).

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

## Self-hosted runner (`oc-native`)

Native CI runs on an always-warm machine instead of cold GitHub-hosted runners (a new branch there starts with an empty
Gradle cache, about 18 min for the fast lane and 9 min for an APK).

- Machine: the second PC `desktop-in65ssc` (WSL2 Ubuntu, 12 GB for WSL). Three runner instances `wsl-oc-1..3` (user systemd
  units `oc-runner-N`, linger on) in `~/actions-runner-N`, labels `self-hosted, linux, x64, oc-native`. They share one
  persistent Gradle home `~/gradle-home-oc` on WSL's ext4 (daemon, build cache and configuration cache on, `-Xmx4g`), JDK 21
  (JDK 17 comes from Gradle toolchains) and `~/Android/Sdk`. Settings are in the runner `.env` files and
  `~/gradle-home-oc/gradle.properties`.
- Staying up: a Windows scheduled task `OcWslKeepalive` (at logon) holds a WSL session, and `.wslconfig` has `vmIdleTimeout=-1`.
- Routing: the `runner` job in `native-ci.yml` picks the runners for every other job. Self-hosted is chosen only when ALL hold:
  the event is `workflow_dispatch` or `push`, the repository is this one, the branch is `native/next`, `native/screens-merge*`
  or `master`, the `runner` input is not `github`, and an `oc-native` runner is online. Everything else (every work branch)
  runs on `ubuntu-latest`. Dispatch with `-f runner=github` to force hosted.
- Offline check: the `runner` job lists runners with the secret `RUNNER_STATUS_TOKEN` (a fine-grained token with repository
  Administration: read) when it exists. Without it the workflow token cannot list runners, so the job assumes the runner is up;
  check it yourself before dispatching: `gh api repos/Eslamasabry/opencode-mobile-next/actions/runners --jq '.runners[]|[.name,.status,.busy]'`.
- Security (the repository is public): no workflow uses `pull_request` or `pull_request_target` with the self-hosted labels
  (only the `runner` job's guard above ever selects them), fork pull requests cannot start jobs on it, and the repository
  setting "approval for fork pull request workflows" is `all_external_contributors`. Anyone with write access can run code on
  the machine through a branch workflow, so keep collaborators to the owner and agents. Never put the machine's secrets in
  `_work`; jobs only get the repository secrets they name.
- Gradle daemon: `RUNNER_TRACKING_ID` is set empty so the warm daemon survives the end of a job. A cancelled job kills the
  daemon that has files open in its workspace (workflow step), and the next job on that workspace does the same before it builds.
- One runner takes one job at a time, so a run's compile, test shards and APK jobs queue behind each other; keep work branches on
  the hosted runners so `native/next` never waits.
