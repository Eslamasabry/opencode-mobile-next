# Verification runner

`run_serial_tests.py` discovers every `test/**/*_test.dart` file recursively,
sorts the manifest, and runs bounded chunks one at a time with
`flutter test --no-pub --concurrency=1`. It retains stdout/stderr logs and one
JSON result per chunk attempt under `build/traycer/serial-<run-id>/` by default.

Start a run with an explicit Flutter executable (use the full `.bat` path on
Windows):

```powershell
python tool/qa/run_serial_tests.py `
  --flutter 'C:\path\to\flutter.bat' `
  --chunk-size 25
```

Resume an interrupted or failed run:

```powershell
python tool/qa/run_serial_tests.py --resume build/traycer/serial-<run-id>
```

The default output location is `build/traycer/serial-<run-id>`.

## Deadline and process ownership

Every chunk is bounded: `--chunk-timeout` defaults to 900 seconds and a chunk
that exceeds it is stopped and recorded as `timed_out`. `--chunk-timeout 0`
opts out explicitly (the run is then only bounded by the outer shell or CI
job). On resume the stored value applies unless a new one is passed.

Stopping a chunk stops everything the chunk started, not just the `flutter`
launcher:

- **POSIX** (Linux, Termux, macOS): the launcher is started in a new session,
  so its pid is the id of a process group the runner owns. Deadline and Ctrl-C
  send `SIGTERM` to that group, wait up to 10 s for the launcher, then send
  `SIGKILL` to the group whether or not the launcher left (a `flutter_tester`
  that ignores `SIGTERM` must not survive a launcher that honoured it). The
  same group `SIGKILL` runs after a normal exit, so a process left behind by a
  passed chunk never runs into the next chunk or a retry. Signals go only to
  the captured group id, never to a process name or pattern; the launcher is
  not reaped until the group has been signalled, so the id cannot have been
  recycled. Because the launcher is in its own session, a terminal Ctrl-C
  reaches the runner only; the runner then stops the group.
- **Windows**: the launcher (`cmd.exe` running `flutter.bat`) and its
  descendants are stopped with `taskkill /T /F /PID <pid>`, scoped to the
  captured pid. Limitation: the tree walk cannot find descendants whose parent
  already exited, and nothing is swept after a normal exit; binding the tree
  to a job object would need extra dependencies. If `taskkill` is unavailable
  the launcher alone is killed. This path is exercised only with mocks in
  `test_run_serial_tests.py`; it has not been run on a Windows machine.

`run.json`, `test-manifest.json`, and `source-manifest.json` record the run
snapshot. Resume is refused when application code, tests, assets, contracts,
SDK packages, Flutter configuration, or the helper scripts and native/release
contracts inspected by tests change. The exact paths are listed in
`SOURCE_DIRECTORIES` and `SOURCE_FILES` in the runner. Local signing credentials
are not included. Generated golden-failure images and tool caches are excluded.
Old chunk logs/results are retained; retries receive a new attempt number.

## Shards, concurrency and the fast local run

`--shard-index I --shard-count N` (1-based) runs one of N disjoint shards. The
split is a deterministic longest-first partition weighted by
`test_timings.json` (seconds per file, load included); a file missing from it
weighs the median. Every file lands in exactly one shard, so the N shards
together are the full suite. A sharded or `--concurrency C` run schedules the
heaviest files first; the default (one shard, `--concurrency 1`) keeps the
sorted serial order and command line `scripts/release.sh` uses. Shard
arguments are part of the run snapshot (resume refuses different ones);
concurrency, like the deadline, can be overridden on resume.

`--json-report` also writes each chunk's Flutter JSON report
(`chunks/*.report.jsonl`) so `summary.json` lists `failed_files`. Refresh the
weights from reports, preferably of a serial run:

```bash
python3 tool/qa/update_test_timings.py build/traycer/*/chunks/*.report.jsonl
```

`run_tests_fast.sh` is the fast full local run: 4 shards side by side, each
`--concurrency 2` and holding one `machine_lock.sh` test slot, then
`summary.txt`/`summary.json` with each shard's status, the failing files and
an every-file-exactly-once coverage check. CI (`android-quality.yml`) runs the
same runner as a 6-shard matrix with `--concurrency 4`.

# Commit rules check (opt-in)

`check_commits.sh` (STANDARDS.md G33, PROC-14) checks a branch's commits
before a merge: every message carries `[skip ci]`, a non-merge commit has a
body and ends with a `Co-Authored-By:` trailer block, and every Dart file the
range changed passes `dart format --language-version=3.10`.

```bash
tool/qa/check_commits.sh feat/phone-setup-v2      # <base> [<head>]
```

It is opt-in: the repository installs no git hook, because a hook in a
shared `.git` (or `core.hooksPath`) would run on every agent's commits in
every worktree. Its `--message-file` mode can back a personal commit-msg hook
in a checkout only you use.

# ARM smoke before a release (E02-S92)

Two checks that cost nothing and run on real ARM hardware. Neither is part of
CI; run them by hand on the release candidate before it ships.

## Phone smoke over adb: `phone_smoke.sh`

```bash
tool/qa/phone_smoke.sh <adb-serial> build/vps-apk/app-release-<build>.apk
```

What it does, in order, printing one `PASS <check>` or `FAIL <check>: <why>` line
per check and `RESULT: PASS|FAIL` last:

| Check | How |
|---|---|
| `install_same_signer` | Pulls the installed `base.apk`, compares its signer SHA-256 with the APK's (`apksigner verify --print-certs`), and only when they are equal runs `adb install -r`. A different signer, or no installed app, is `REFUSED` before anything is installed. |
| `phone_connected` | Opens the app, taps through `--phone-path` (default `Settings>Servers`) and expects the `On this phone` row to show `Connected`. |
| `new_chat_reply_ok` | Taps through `--new-chat-path` (default `New conversation>armsmoke`), types "Reply with just the word ok" and waits for an `ok` reply. |
| `shell_git_uname_arm` | Asks the agent to run `git --version; uname -m`; expects `git version N.N` and an ARM machine name (`aarch64`, `arm64`, `armv8l`, `armv7l`). |
| `tar_roundtrip_tmp` | Asks the agent to `tar -czf` a file in `/tmp/armsmoke<random>`, delete it, `tar -xzf` it back and print it; expects the uppercased random token. |
| `cleanup` | Asks the agent to `rm -rf` that `/tmp` folder (expects `REMOVED`), then deletes the scratch conversation through `--delete-path` (default `More options>Delete conversation>Delete`) and checks it is no longer listed. Runs even when an earlier check failed. |

Safety rules the script enforces:

- It never uninstalls, never runs `pm clear` and never captures the screen
  (no `screencap`/`screenrecord`; owner rule: read text only). The adb wrapper
  refuses those commands outright.
- Every tap, text entry, swipe and key press first checks that
  `io.github.eslamasabry.opencode_mobile` holds the foreground (both
  `dumpsys window` focus and the dumped window's package). If something else
  holds it, such as an incoming call or the notification shade, for 8 s, it stops
  with `REFUSED: foreground is <package>` and exit 3 without tapping, and says
  what to clean up by hand.
- Nodes are found by visible text or content-desc from `uiautomator dump`;
  coordinates are always computed from the node's bounds.
- It prints only check names, expected tokens and short signer digests, never
  other screen text. One run per phone at a time (a lock file in `$TMPDIR`).

Exit codes: 0 all PASS, 1 a check failed, 2 refused before touching the app
(wrong signer, app not installed, not ARM, phone not connected), 3 another app
took the foreground.

One-time setup on the phone: install the app from the same signer as the
candidate APK (see the signer list in AGENTS.md), connect it to its own
server so `On this phone` shows `Connected`, sign in to a model, and create a
scratch project named `armsmoke` (any empty folder). On the computer you need
`adb` and `apksigner` (Android build-tools) on PATH, or set `ADB=` and
`APKSIGNER=`. If a label differs on your build, override it, for example
`--phone-path 'Settings>Server'` or `--delete-path 'Conversation options>Delete conversation>Delete'`;
`>` separates taps.

## Firebase Test Lab on the free Spark plan: `testlab_smoke.sh`

```bash
tool/qa/testlab_smoke.sh <firebase-project-id> build/vps-apk/app-release-<build>.apk
```

Runs one Robo smoke (5 min) of the APK on 3 ARM (`arm64-v8a`) physical
devices: the newest-API models in the Test Lab catalog, or exactly three from
`TESTLAB_DEVICES='model=a15,version=35 model=e3q,version=34 model=oriole,version=33'`.
It fails closed:

- it reads the project's billing state with `gcloud billing projects describe`
  and refuses (`REFUSED: ...`, exit 2, nothing uploaded) when a billing
  account is attached or billing is enabled, which is the Blaze plan and could
  be charged. It also refuses when the billing state cannot be read;
- Spark allows 5 physical-device tests per day, and one run uses 3. The script
  counts today's runs from this machine in `build/testlab/` and, when another run
  would go over, prints `NOT RUN: free quota used` and exits 0. When Test Lab
  itself answers with a quota error it prints the same marker and exits 0;
- the last line is always one of `TESTLAB: PASS`, `TESTLAB: FAIL (...)` (exit 1),
  `REFUSED: ...` (exit 2) or `NOT RUN: free quota used (...)` (exit 0).
  `TESTLAB_DRY_RUN=1` prints the `gcloud` command instead of running it.

One-time setup the owner does (the script creates no projects or accounts):

1. In the Firebase console, create a project and leave it on the free **Spark**
   plan. Do not add a billing account. Note its project id.
2. Install the Google Cloud CLI, then `gcloud auth login` with the same Google
   account.
3. Enable the APIs Test Lab and the plan check need, all free on Spark:
   `gcloud services enable testing.googleapis.com toolresults.googleapis.com cloudbilling.googleapis.com --project <id>`.
4. Dry-run once: `TESTLAB_DRY_RUN=1 tool/qa/testlab_smoke.sh <id> <apk>`.

The APK is uploaded to Google's Test Lab for the run, and Robo takes its own
screenshots on Google's devices; that is the point of this check, so run it
only on a build the owner has agreed to upload.

Tests for both scripts' refusal paths use fake `adb`, `apksigner` and `gcloud`:
`python3 -m unittest tool.qa.test_phone_smoke tool.qa.test_testlab_smoke`.
