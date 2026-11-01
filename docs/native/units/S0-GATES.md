# Slice 0 · S0-GATES: the check ladder and the push gates

Date: 2026-11-01. Branch: `native/gates-2026-11-01`, cut from `claude/native-gates-validation-3uph7e` at `6b9a2b212`
(the tip of `native/m0-2026-11-01`). Write set: `native/tool/**` (scripts only); this report is the only file outside it.

**Result:** every script the unit row names exists and ran at least once (outputs below):
- the ladder: `t0.sh`…`t4.sh`, `vps_gradle.sh`;
- the gates: `check_write_set.sh`, `twin_check.sh`, `check_commits.sh`, `scan_push.sh`, `snapshot_push.sh`.

A self-test suite proves each gate fails on a planted violation for its own reason and passes the clean case:
127 of 127 pass, with no Gradle and no network.

**Shipping state:** written, self-tested, committed and pushed to the branch. Nothing is merged, and there is no PR
and no CI run. T1 and T2 run green only once the tree outside `native/tool` compiles again (§3). T3 and T4 have
never reached a device: there is no emulator here, and no APK can be built today.

**Not in this unit, although plan 15 lists them in the S0-GATES row:**
- the Konsist rules in `native/quality/**` (planted `material3`, `LocalContext`, raw `clickable`);
- the Termux `TerminalView` through `PlatformViewHandle`.

The brief limited the write set to `native/tool/**` scripts, so both are open (§6).

---

## 1. What was built

| Path | What it does |
|---|---|
| `native/tool/lib/common.sh` | The plan 02 §8 script contract in one place. It holds:<br>- exit codes 0 / 1 / 2, where 2 is "could not run" and is never a pass;<br>- `build/native-t<N>/<id>/summary.txt`, the `exit-code` done-file and the last 20 lines printed;<br>- the build slot (the PC's `oc-build.lock`, the same lock as `tool/qa/machine_lock.sh build`, with `flock -w`);<br>- the memory (≥ 7 GB), disk and JDK 21 checks;<br>- `--detach` (`setsid nohup`);<br>- path → module mapping, per-module test tasks, the failing-test names from JUnit XML, and Gradle log classification (network/429/disk/lock → 2, missing task → MISSING). |
| `native/tool/lib/gradlew.sh` | Starts `./gradlew` inside `native/`, so `-p build-logic` resolves (found by T2, §4 D5). |
| `native/tool/ladder/tasks.tsv`, `ladder/t2.tasks` | Per-module test-task overrides; the T2 steps, one Gradle call per line. |
| `t0.sh <module> [filter] [--android] [--vps]` | One module's `desktopTest` (or `testAndroidHostTest`, or `test` for JVM modules), in the foreground. A catalog id becomes `--tests '*B-…*'`. |
| `t1.sh [--base] [--brief\|--globs] [--record M] [--where] [--detach]` | **PC part:** the touched modules and `check_write_set.sh`.<br>**Build part:** their tests (a module that records goldens verifies Roborazzi instead), `-p build-logic test` when build-logic is touched, `:quality:test`, and the `:tools:strings` drift check when strings are touched.<br>detekt runs only when `tool/qa/detekt.sh` exists; otherwise "not run" is recorded.<br>`--record` is refused anywhere but the VPS. |
| `t2.sh [--where] [--steps] [--detach]` | Holds `~/.oc-native-t2.lock` and needs ≥ 30 GB free disk. It runs the `t2.tasks` steps. A task Gradle cannot find is **MISSING, which is a failure**. A `--steps` run is PARTIAL. A clean full pass prints the `LAST_GREEN` line. |
| `t3.sh <apk> --signer <sha>` | In order:<br>- versionCode ≥ 10000 (`aapt2`) and the signer (`apksigner`, never substituted);<br>- the emulator lock; the device must be `emulator-<port>` with `ro.kernel.qemu=1`; downgrades are refused;<br>- the flows with animations off through `e2e/runner/run.py`, then E2E-05 with animations on;<br>- `connectedStableReleaseAndroidTest`, the S-28 sweep (MISSING until `tool/qa/a11y_sweep.sh` exists), and `--upgrade` (E2E-39).<br>Animator scales are restored at exit. |
| `t4.sh --apks <dir>` | Refuses unless all of these hold: the quiet window, load < 2.0, no Gradle daemon, ≥ 4 GB RAM, the emulator lock, and `tool/perf/restore_device.sh`. Then it installs the VPS-built APKs, runs `am instrument` per journey from `tool/perf/journeys.tsv`, pulls the JSON next to the APKs, and runs `compare.py`. The phone needs `--phone` plus `OC_OWNER_PHONE_OK=1`. |
| `vps_gradle.sh [--unit] -- <args>` | Rsyncs `native/` (uncommitted edits included) to `~/wt/ocn-<U>`; an APK build goes to `~/wt/ocn-<U>-apk`. Then:<br>- checks disk and runs Gradle under `flock -w`;<br>- pulls test results and reports back.<br>The host comes only from `$OC_VPS_HOST` and is never printed. |
| `check_write_set.sh <brief.md>\|--globs` | Checks every path changed since the merge-base, plus staged, unstaged, untracked and deleted files, against the write set.<br>- Glob syntax: `**`, `*`, `?`, `{a,b}`.<br>- `--also` adds the report path.<br>- The Flutter `android/` tree never matches `native/android/**`. |
| `check_commits.sh [base [head]]` | G33 without the Dart format step, plus the plan 02 §9.1 rules:<br>- subject types and a `module-name` scope, at most 72 characters before `[skip ci]`;<br>- a body, and a trailer block with `Co-Authored-By`;<br>- `Claude-Session` must be https, and `Behaviors:` must list `B-AREA-NNN` ids;<br>- `Change-Class` values are checked, and a test-only or docs-only commit may not carry one.<br>A message mode exists for a personal hook; the repo installs no hook. |
| `twin_check.sh [--kind] [--verify-copy] [--ref]` | Checks each `TWINS.md` row:<br>- both SHAs exist, and "last synced" is not older than the copy;<br>- the source exists at "last synced", and the native copy exists;<br>- "last synced" is on the history of `--ref` (otherwise drift ranges would lie);<br>- `--verify-copy` compares Kotlin/Java twins byte for byte.<br>It lists the Flutter commits to mirror. A shallow clone gives 2, not a false pass. |
| `scan_push.sh` (+ `lib/scan_push.py`, `scan_push.allow`) | Scans **every commit** a push publishes: each commit's added lines (so a secret added and later removed still blocks), each message, and the author/committer addresses.<br>**What blocks:**<br>- key material and tokens (PEM, AWS, GitHub, Slack, Google, Anthropic, OpenAI, JWT), credentials in URLs, and `password/secret/token = "<value>"`;<br>- the local `git config user.email` when it is not no-reply, `$OC_SCAN_EMAILS`, and personal-mail domains;<br>- RFC 1918, `100.64/10`, `169.254/16`, `fc00::/7` and `fe80::/10` (CIDR ranges and network/broadcast addresses are not hosts);<br>- `*.ts.net` and `*.home.arpa` names, and `.lan`/`.internal` names used as URL hosts;<br>- patterns from `~/.config/oc/scan_push.local`; forbidden files (`*.jks`, `key.properties`, `local.properties`, `.env` …); non-no-reply authors.<br>**Output:** hits are printed masked.<br>**Modes:** range, `--push <remote> <refspec>` (scans, then pushes only when clean), `--pre-push` (opt-in hook) and `--diff-file`. |
| `snapshot_push.sh` | Builds the plan 15 §11.0 snapshot:<br>- `commit-tree <source>^{tree} -p <onto>` with the no-reply identity;<br>- checks the tree is equal and the parent is right;<br>- runs `scan_push.sh` over `onto..S`, then pushes.<br>A work snapshot refuses master, main and tags. A release needs `[skip ci]`, a `Change-Class` trailer, `--go` **and** `OC_OWNER_GO=<full candidate sha>`. It never tags. |
| `native/tool/test/run_tests.sh` | 127 cases in throwaway repositories. An `ssh` shim runs the VPS side locally, and a fake `gradlew` drives t0/t1/t2. Planted secrets and addresses are built at run time, so the file scans clean. |
| `native/tool/README.md` | A one-page index of the scripts. |

## 2. Catalog rows

**None.** The plan 15 S0-GATES row lists "—", and `scope.jsonl` maps no row to `native/tool`. The nearest row,
B-SEC-150 ("no keystores, signing secrets … committed"), belongs to S1-SECGATES as a Gradle test. `scan_push.sh`'s
forbidden-file rule enforces the same rule at push time but does not claim the row. The tests are named after behaviour
(`write-set: …`, `scan: planted … blocks`), as plan 02 §6 allows for gate tests without a row.

## 3. Each script, run once (commands and results)

Environment: a cloud container (4 vCPU, 15 GB), `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`,
`ANDROID_HOME=/opt/android-sdk` (`platforms;android-37.1`, `build-tools;37.0.0`, platform-tools), run from `native/`
or the repository top. A container-only init script, `~/.gradle/init.d/central-mirror.gradle.kts`, puts Google's
Central mirror first; it is not committed (see §4 D6).

| Script and command | Exit | Output (abridged) |
|---|---|---|
| `tool/t0.sh :core:common` (cold) | **0** | `step gradle: exit 0 in 183 s`, `tests run: 12`, `result: PASS`. |
| `tool/t0.sh core/common PacerTest` (warm, filtered, final code) | **0** | `--tests *PacerTest*`, `exit 0 in 7 s`, `tests run: 7`, `result: PASS (exit 0) in 7 s`. |
| `tool/t1.sh --detach --base origin/native/m0-2026-11-01 --globs 'native/tool/**' --also docs/native/units/S0-GATES.md --where local` | **1** | `changed paths: 18; touched modules: (none)`.<br>`write-set: OK`; `tasks: :quality:test`.<br>The tests step fails on `e: …/engine/connection/…/ConnectionRegistry.kt … Unresolved reference 'backend'` (the contract being reconciled elsewhere).<br>`detekt: not run (tool/qa/detekt.sh does not exist yet)`; `result: FAIL (exit 1) in 4 s`. |
| `tool/t2.sh --where local` (default floor) | **2** | `disk: 23 GB free … (need >= 30)`, `result: COULD NOT RUN (exit 2)`: the refusal works. |
| `OC_T2_MIN_DISK_GB=20 tool/t2.sh --detach --where local` | **1** | `build-logic: exit 0 in 36 s`.<br>`tests: exit 1 in 234 s`: 792 tests ran, but compilation failed in `:core:net`, `:core:data`, `:core:domain` tests, `:engine:connection`, `:engine:analytics`, `:ui:kit-chat` and `:ui:shell` tests.<br>Failing tests: `AndroidSystemMotionTest > classMethod`, and two `FlutterKeyRegistryTest` cases in `:tools:flutter-migration-readers`.<br>`goldens: exit 0 in 58 s`.<br>`lint`, `assemble`, `r8-config` and `catalog-report` are **MISSING**: `:androidApp` has only the `preview` flavor, so `lintStableRelease`, `assembleStableRelease` and `analyzeStableReleaseR8Config` do not exist, and nor does `:quality:catalogReport`.<br>`result: FAIL (exit 1) in 342 s`. |
| `tool/t3.sh androidApp/build/outputs/apk/preview/release/androidApp-preview-release.apk --signer 1DE5…D60C` | **2** | `APK not found`: no APK can be built today (`:engine:connection:compileAndroidMain FAILED`), and there is no emulator here. The device path is covered only by the self-tests' refusals. |
| `tool/t4.sh --apks <dir with app and *bench* placeholders>` | **2** | `load: 2.25 (need < 2.0)`, `PC not quiet`. An earlier run with an empty folder gave `the folder needs the app APK and a *bench*.apk` (2). |
| `OC_VPS_HOST= tool/vps_gradle.sh -- :core:common:desktopTest` | **2** | `error: OC_VPS_HOST is not set`. The end-to-end behaviour is proven by the ssh-shim self-tests (sync, lock, disk, exit codes, pull-back, the `-apk` folder). |
| `tool/vps_gradle.sh --unit gates --dry-run -- :core:common:desktopTest` | **0** | Prints the rsync/ssh/rsync plan with `$VPS` in place of the host. |
| `tool/check_write_set.sh --globs 'native/tool/**' --base origin/native/m0-2026-11-01 --also docs/native/units/S0-GATES.md` | **0** | `changed paths: 18`, `write-set: OK`. |
| `tool/check_commits.sh origin/native/m0-2026-11-01` | **0** | `native commits: OK, 9 commit(s)`. |
| `tool/twin_check.sh --verify-copy` (full clone) | **0** | `116 of 116 rows checked … 0 with Flutter commits to mirror, 0 invalid, 0 not checkable`. All Kotlin twins are byte-equal. |
| the same, while the clone was shallow | **2** | `57 not checkable … (git fetch --unshallow)`. The first version listed false drift here; that was fixed (§4 D3). |
| `tool/twin_check.sh --ref origin/master` | **1** | `7 invalid`: the rows synced at `580803d4` (the freeze snapshot) are not on GitHub `master`'s snapshot history. This is the §11.0 C15-17 case. |
| `tool/scan_push.sh origin/native/m0-2026-11-01` | **1, then 0** | The first run blocked **this branch**: `HIT private-ipv6 … run_tests.sh:146 fd7a:…` and `HIT private-host … ts.net`. These were literals in an early test commit, already replaced in a later one, but still present in history. The unpushed history was rewritten (the tree is unchanged), and the scan then gave `clean, 8 commit(s) scanned`. |
| `OC_SCAN_EMAILS=<owner> tool/snapshot_push.sh --source HEAD --onto origin/master --remote origin --name snapshot/native-gates-dryrun --dry-run` | **1** | `tree-equal: yes; parent: origin/master`, author no-reply.<br>`scan_push: 91 hit(s) … push BLOCKED`. The hits by file are in §6.2. |
| `tool/scan_push.sh --push origin native/gates-2026-11-01 -u` | **0** | The push of this branch itself went through the scanner: `scanned clean and pushed` (§5). |
| `tool/test/run_tests.sh` | **0** | `native/tool self-tests: 127 passed, 0 failed` (about 15 s). |
| `shellcheck -x -S warning` on every script | **0** | Clean. The two deliberate exceptions carry reasons in the scripts: remote `~` paths, and a literal word match. |

## 4. Deviations and fixes found by running

- **D1. `t1.sh` runs locally when `OC_VPS_HOST` is unset.** Plan 02 §8 puts the T1 build part on the VPS. The cloud
  lane has no VPS (plan 15 §2.1.1 item 4), so the default is `vps` when the host is set and `local` otherwise.
  `--where` overrides it.
- **D2. `t3.sh` takes `--signer` as required.** Without the expected certificate T3 exits 2 rather than pass on an
  unchecked signer (AGENTS.md: never substitute a signer). The flows run through S0-FLOWS's `run.py` with
  `--lock <run-dir>/inner.lock`, because `t3.sh` already holds the real emulator lock for the whole run, and taking the
  same `flock` twice would deadlock.
- **D3. `twin_check.sh` refuses drift ranges whose "last synced" commit is not an ancestor of the ref.** In a shallow
  clone (2) or a snapshot history (1), `git log A..B` lists unrelated commits; the first run reported 57 false drifts.
- **D4. Scanner precision.** Run over the whole native tree, the first version also hit:
  - Kotlin packages ending in `.internal`;
  - network and broadcast addresses.

  Both are excluded now. Fixture files can be allowed per rule (`@path <glob> <rule>  # reason`). No such lines were
  added for other units' files: that call is the coordinator's (§6.2).
- **D5. A real bug found by T2:** the scripts passed `-p native` together with the step's own `-p build-logic`, which
  Gradle refuses ("Multiple arguments … '-p'"). Local Gradle now runs through `lib/gradlew.sh`, and a fake-gradlew
  self-test pins this.
- **D6. Maven Central returned 429 in this container.** Like every slice-0 report, I used a local init script that puts
  `maven-central.storage-download.googleapis.com` first, in both `beforeSettings` (plugins) and `settingsEvaluated`.
  It is not committed. `common.sh` classifies 429 and unresolved plugins as exit 2.
- **D7. `t4.sh` deliberately does not implement plan 13's state-file resume and `setsid flock … sleep` lock holder.**
  Those belong with `tool/perf/*` (WP-13.0, the perf lead). `t4.sh` runs detached, holds the lock by file descriptor,
  and calls `restore_device.sh` first and again at exit.
- **D8. `t2.tasks` lists the plan's gates even where the task does not exist yet** (stable flavor, R8 analysis,
  `:quality:catalogReport`), so they stay visible as MISSING until their owners add them.

## 5. Push scrub of this branch (plan 15 §2.1.1 step 6)

`native/tool/scan_push.sh` scanned every commit of this branch, with `OC_SCAN_EMAILS` set in the environment only:
- what it covers: the added lines, the messages and the authors;
- the result: clean.

It also caught and blocked two private-looking test literals in an earlier commit of this same branch. That history
was rewritten before the first push.

I also searched the diff by hand for the owner's address and for any private IPv4/IPv6 address. Neither is present,
and this report quotes no flagged value.

## 6. Open problems

### 6.1 For owners

- **Konsist planted rules (K: `material3`, `LocalContext`, raw `clickable`) and the `TerminalView` /
  `PlatformViewHandle` proof** are in the plan 15 row, but `native/quality/**` and `:android:*` were outside this brief's
  write set. They need a follow-up unit, or the `:quality` owner.
- **`tool/qa/detekt.sh`** (plan 03 check 11): T1 records "not run" until it exists.
- **`tool/qa/a11y_sweep.sh`** (S-28): T3 reports it MISSING.
- **`tool/perf/{restore_device.sh,journeys.tsv,compare.py}`** (WP-13.0): T4 cannot run without them.
- **The stable flavor** (`assembleStableRelease`, `lintStableRelease`, `analyzeStableReleaseR8Config`, plan 03 checks 7
  and 8) and **`:quality:catalogReport`**: T2 reports these as MISSING.
- **`native/briefs/`** does not exist. `check_write_set.sh <brief.md>` reads the "Write set:" line of the 02 §7.13
  template; until the briefs exist, pass `--globs`.

### 6.2 For the coordinator: the first snapshot is blocked

A dry-run snapshot of today's tree onto GitHub `master` gets 91 hits, so the snapshot cannot be pushed until they
are reviewed. Hits by place:

| Hits | Where |
|---|---|
| 36 | `native/core/security/…` (address-rule and redactor tests) |
| 11 | `docs/design/…` (the plan; incl. plan 16's credential-canary examples) |
| 10 | `test/browser_shell_test.dart` |
| 9 | `native/ui/words/…` (example addresses in strings) |
| 5 | `native/engine/analytics/…` (privacy-test canaries) |
| 5 | `docs/verification/…` |
| 15 | single files across the Flutter `test/`, `lib/l10n/`, `native/e2e/`, `native/quality/`, `native/core/{model,data}` and `native/engine/connection` |

Nearly all are generic test addresses and fake canary tokens. The scanner cannot know which, and the owner already
scrubbed real values from `master` once (`ec2633fbd`). For each hit, review it, then either:
- add an exact-value or `@path <file> <rule>` line to `native/tool/scan_push.allow`, with a reason; or
- replace the value with a documentation address (`192.0.2.x`, `198.51.100.x`, `203.0.113.x` are never flagged).

### 6.3 Other open problems

- **`twin_check.sh` against GitHub `master`:** 7 rows synced at `580803d4` are not on `master`'s history. If option
  C15-17 is taken, the script needs the "last synced freeze commit" file named in plan 15 §11.0. Today it compares
  against the ref you pass (default `HEAD`).
- **T3's `--upgrade` uses one `--expect-signer` for both APKs.** If the Flutter baseline and the native build are
  signed differently, run.py needs two signer arguments (S0-FLOWS owner).
- **Not proven:** none of these have run yet:
  - any VPS run;
  - a real T3 or T4 on a device;
  - a green T1 or T2 (both need the tree to compile);
  - the PC build slot path (`/home/eslam/Storage/tmp/oc-build.lock`). Here the scripts fell back to `$TMPDIR` because
    that folder does not exist.

## 7. Catalog requests

None. No library, version-catalog or build-logic change is needed. The scripts need bash, git, flock, python3 (standard
library only), rsync and ssh on the PC, and the Android SDK's `aapt2`, `apksigner` and `adb` for T3/T4.
