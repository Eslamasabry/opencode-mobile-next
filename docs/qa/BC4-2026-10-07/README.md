# BC4 — update journal interruption proof

**BLOCKED: full app next-start recovery requires the coordinator-owned
`BuiltinLinux` startup hook in [BC4-contract.md](../../design/BC4-contract.md).
This worktree proof does not apply that hook or claim app startup qualification.**

Date: 2026-10-07. Device: `emulator-5554`, installed app Ubuntu/PRoot.
Production helper: the literal `componentUpdatePrelude` from
`lib/builtin/setup/component_updates.dart`, extracted without changes.

Finish line: a process interruption during an isolated component update is
recovered by the production helper in a fresh shell, including launch links and
replay after recovery interruption. Non-goal: editing `BuiltinLinux`, installing
an APK, modifying installed agents, provider accounts, power-loss qualification
or release.

Run:

```sh
python3 tool/qa/bc4_update_emulator.py
```

The harness holds the shared machine test lock. Every ADB session targets only
`emulator-5554` and holds the shared emulator flock; the whole PRoot fixture runs
in one short locked session. All helper targets, candidates, journals, backups,
mock commands and test programs live in a disposable
`/data/local/tmp/oc-bc4-<uuid>` directory bound as `/bc4-qa`. The installed Ubuntu
is used for its real shell, `mv`, `sync`, and other programs, with scratch HOME
and `/tmp`; no installed package file or account configuration is changed.

The test programs are tiny controlled executables with a good version and a
candidate that fails. For a crash after activation, the harness waits until the
real activation helper has returned with a pending journal, then sends SIGKILL
to the exact fixture shell PID before commit. For rename checkpoints, a mock
`mv` runs the real `/usr/bin/mv` first and waits after the move. The test kills
only the captured activation/recovery shell, its exact helper subshell PID when
distinct, and that exact mock `mv` PID. It never kills by pattern. A new shell
then invokes the actual `oc_update_recover TARGET` helper.

Results in [emulator.log](emulator.log):

- Claude and OpenCode executable-file fixtures: a crash after activating bad
  code restores the previous runnable version; a second recovery is stable.
- Paseo directory fixture: recovery restores the original runnable tree, and
  its stable launch symlink resolves correctly again.
- A separately journaled Paseo launch-symlink update: a crash restores the old
  link and the runnable program it points to.
- Crash after old active code moves to `.oc-good`, before candidate publication:
  the durable marker and backup survive; fresh recovery restores good code.
- Crash while recovering, after the backup is moved into place but before the
  journal clears: fresh recovery replay succeeds and clears the pending marker.
- Successful commit keeps one `.oc-good` version. The next successful update
  replaces that backup with the immediately preceding good version.
- Interrupted first install with no previous version removes the incomplete
  activation; replay remains stable.
- Corrupt journal fails closed. The active version, backup and marker remain
  intact rather than being guessed away.

Verified helper SHA-256: `cda3ab05a9e0ba2dbb9055392f442108177434e6ce9234a1c6ef06509e867bee`.
The log records the production helper SHA-256. Python syntax and scoped
`git diff --check` passed. Temporary host/emulator fixture files are removed.
This is a process-interruption proof of the shared helper and a simulated fresh
startup shell; it does not establish Android power-loss durability, full
Claude/Paseo/OpenCode installer integration, or automatic app next-start
recovery. Candidate integration tests and the exact startup patch belong to the
BC lead/coordinator handoff. No signing, publication, deployment or release
occurred.

## Owned installer integration checks

The owned legacy Claude installer, OpenCode native installer/check and Paseo
installer/check now use the shared journal. Candidates must pass their existing
pin/probes before publication; the activated command is probed again before
code then launch-link receipts commit. Immediate activation failure restores the
previous code/link and returns fixed retry guidance. One `.oc-good` generation
remains after success. This does not cover the separate catalog Claude installer
until the coordinator patch is applied.

Before-fix proof: restoring the three installer sources from commit `82b124c5`
and bypassing `oc_update_recover` caused all four selected regressions to fail:
uncommitted recovery, activated Claude, activated OpenCode and activated Paseo.
The candidate sources were restored byte-for-byte afterward. Output:
[before-regressions.log](before-regressions.log).

Candidate focused check (pinned Flutter, `OC_TEST_SLOTS=1`, shared test lock,
`--no-pub --concurrency=1`):

```sh
test/setup_component_update_test.dart
test/claude_install_script_test.dart
test/opencode_native_install_test.dart
test/paseo_phone_install_test.dart
test/setup_scripts_test.dart
test/setup_engine_test.dart
```

Result: **83 passed, one optional real-OpenCode download smoke test skipped**
(`OC_SMOKE_REAL_OPENCODE=1`, ~150 MB). All new regressions ran and passed.
[focused-tests.log](focused-tests.log) records the run. Pinned Flutter analyzer
under the shared lock: **no issues**, 11.9 seconds; [analyze.log](analyze.log).
No full suite or replacement APK was built. The coordinator owns merge gates and
must prove actual app restart recovery after applying the contract patches.

Storage format: adjacent `<target>.oc-pending` contains only `existing` or `new`;
`<target>.oc-good` retains the prior code/link. Existing unjournaled installs are
left alone until their next successful update. Recovery is idempotent, handles
broken launch symlinks without following them, and rejects corrupt receipts.
No credentials or user projects are copied. Retained component code consumes
additional disk; BC2 remains the guard before installation/launch. See the
contract for safe removal and startup serialization obligations.

Logs linked above are local evidence; this report and the reproducible harness
are committed. No installed component, account or runtime was modified by the
emulator proof.
