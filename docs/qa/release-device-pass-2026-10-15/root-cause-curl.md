# Fresh setup: missing curl after an interrupted installer

Worktree: `oc_app-codex-curl`, branch `codex/setup-curl`.
Starting revision: `af7b138cc6c745d1b615901cd3ff2d09e3a3e900`.
Integration base (`git merge-base master HEAD` at investigation start):
`ab472e705e2ee235601d027096621b993c0abbfe`.

Finish line: an interrupted prerequisite must not be called Done or allow Node
to start, and Continue must be able to retry within the same app process.
Non-goals: runtime/provider changes, UI changes, signing, publication, or host
package changes. No `/home/eslam/package*` files were touched.

## Evidence and cause

Read [pass 3](pass3-2325.md), [pass 2](pass2-2324.md), and the pass-3 installing,
failed, details, and after-restart screenshots (`rel3-03`, `05`, `06`, `07`).

Reproduced with the supplied APK 2325 on a wiped Pixel_6, OpenCode 2 default,
using only `adb -s emulator-5554`. APK SHA-256:
`448a3023a20e9c4f7e4c9d8200c03d8754f47d4fcb84154b46763bf054587df0`.
The shared emulator flock was held for the whole session; emulator PID 578855
was stopped by that exact PID and its exit verified. The physical phone was not
accessed.

The fresh-run `setup.json` and log showed:

| Component | Recorded state | Duration | Last progress/version |
| --- | --- | --- | --- |
| Linux | done | 10,979 ms | 24.04.5 |
| Essentials | **done** | 40,857 ms | **8%, no version** |
| Python | **done** | 44,234 ms | **74%, no version** |
| Node | failed | 953 ms | curl exit 127 |

Essentials' log stopped during `libperl5.38t64` unpacking:

```text
Unpacking libperl5.38t64:amd64 (5.38.2-3.2ubuntu0.6) ...
proot info: vpid 1: terminated with signal 15
Reading package lists...
```

The next package operation was Python. Its apt plan listed `ca-certificates`
as NEW although essentials had requested it. `/usr/bin/curl` was absent from
the rootfs and curl had no installed dpkg record. Python was also terminated
with signal 15 before the runner moved to Node:

```text
Setting up ca-certificates (20260601~24.04.1) ...
proot info: vpid 1: terminated with signal 15
==> Downloading Node.js 24.21.0
/bin/sh: 11: curl: not found
[oc] Download failed (curl exit 127): https://nodejs.org/dist/v24.21.0/node-v24.21.0-linux-x64.tar.gz
```

Continue in the same APK process retried essentials for 55,321 ms, again
recorded it Done after signal 15, then failed Node in 951 ms. Thus this run
reproduces the failed warm retry, though the **whole retry** was longer than
the approximately one second reported in pass 3. Python's fresh check passed
on that retry. We did not force-stop/relaunch to repair this reproduction.

The confirmed defect is **installer termination being accepted as successful
completion**. `SetupRunner.runInstallerScript` previously accepted only the
launcher exit code. `BuiltinLinux.monitorInstallerOwnership` has a concrete
path that catches an observation/persistence failure, drains the guest with
SIGTERM, and discards the failure; `finishInstaller` then clears the slot and
returns successfully. The regression injects that path with a zero launcher
exit and demonstrates the false Done result.

The exact first exception that caused termination on the real APK was not
recorded by its catch block, so it remains unproven. A changing `/proc`
inventory is a hypothesis, not an established cause. The fix does not relax
ownership checks or retry an uncertain ownership decision automatically.

Other hypotheses ruled out by current source and reproduction:

- Node already depends on essentials; essentials explicitly requests curl,
  CA certificates, Git and SSH. Its check requires curl.
- Apt is synchronous and verifies requested dpkg status when the script
  reaches its end. The interrupted run never reached that verification.
- Each component gets a fresh ProcessBuilder/PRoot/shell and explicit PATH
  containing `/usr/bin`; there is no reused shell hash cache.
- Continue reruns component checks. There is no cached successful curl probe.
- Native cold component recovery restores program journals, not apt packages.

## Fix

1. Latch an ownership-monitor failure for the exact process/launch before
   draining. Completion still proves quiescence and clears the durable ticket
   and warm slot, then reports the original failure. Clear the latch for the
   next launch and invalidate component-journal recovery after interruption.
2. Wrap each setup script in a child shell with an invocation-specific terminal
   result record. The runner consumes that record privately and requires both
   launcher success and guest success. Missing/invalid/duplicate records cannot
   turn a truncated script into Done. Existing script exits and `set -e`
   behavior are preserved. Dependent components remain pending on failure.

**Dart setup scripts and their hash fixture are unchanged.** The Kotlin runner
adds the execution wrapper; no fixture regeneration is needed for
`SetupScripts`/`setupPrelude`. This is a native change and needs a new APK for
device qualification; a Dart-only patch is insufficient.

## Integration-base comparison

The essentials/Node dependency and scripts, fresh-shell launch, and faulty
monitor/completion methods were already present at `ab472e705`. The monitor
regression extracts the actual methods from that revision using `git show`:
it compiles, then fails the two interrupted-completion/retry assertions; the
stale-monitor control passes. The fixed methods pass all three. This establishes
a **pre-existing defect at the native behavior seam**, not a regression newly
introduced by APK 2325's OC2 default. A complete base APK was not rebuilt or
device-tested.

## Verification

All Flutter/Dart commands use the explicitly requested pinned binary:
`~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
Local heavy checks used `tool/qa/machine_lock.sh`.

- RED: `test/setup_runner_native_test.dart --plain-name interrupted-installer-retry`
  failed before the runner fix: `interrupted essentials was recorded as done`.
- BASE RED: `OC_INSTALLER_SOURCE_REF=ab472e705e2ee235601d027096621b993c0abbfe`
  with `test/installer_monitor_native_test.dart`: two expected assertion failures,
  one passing stale-monitor control; no compilation failure counted as evidence.
- GREEN: runner 15 scenarios, monitor 3 scenarios, admission 19 scenarios.
  The runner regression uses the same runner for an interrupted prerequisite
  followed by a successful prerequisite → Node retry. The monitor fixture
  checks failure reporting, cleared ownership, recovery invalidation, and an
  unaffected subsequent launch on the same runtime object.
- GREEN: 125 focused Dart tests across the five files below.
- GREEN: `flutter analyze --no-pub`, no issues. The first attempt lacked this
  fresh checkout's Widgetbook dependencies; after `flutter pub get` inside
  `widgetbook/`, the complete analyzer passed without source changes.
- Kotlin lint: `tool/qa/kotlin_static_analysis.sh` remains blocked by 286
  findings in unchanged files; no findings in the changed native files. No
  baseline expansion or suppression was added.
- GREEN: on `eslam@203.0.113.10`, isolated copy
  `~/wt/oc_app-codex-curl-unit-20261015`, pinned
  `flutter build apk --release --config-only`, then
  `cd android && ./gradlew :app:testReleaseUnitTest --no-daemon`
  with `-Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=2g" --max-workers=2`,
  under the server's build flock. **460 tests, 30 suites, zero failures/errors/
  skips**, including all four `SetupScriptCompletionTest` tests. Initial build
  8m 29s; final-source incremental rerun 2m 41s. The server supplies Ubuntu
  OpenJDK 17.0.20.1 (not Temurin). No signing properties or keys were copied.

Dart test files (run with `flutter test --no-pub --concurrency=1`):

```text
test/setup_runner_native_test.dart
test/installer_monitor_native_test.dart
test/installer_admission_native_test.dart
test/setup_engine_test.dart
test/setup_planner_test.dart
test/setup_scripts_test.dart
test/setup_apt_repair_test.dart
test/builtin_linux_test.dart
```

Kotlin regression: `SetupScriptCompletionTest` covers successful output without
a newline, shell quoting, errexit, explicit failure, a signalled guest, and
missing/duplicate/malformed/foreign result records.

Local detailed logs are in `build/curl-qa/`. The full serial Flutter suite was
not run; this is focused regression evidence. A rebuilt fixed APK's fresh setup
and in-app retry remain a device qualification gate. The original APK reproduction
and host regression are not claimed as fixed-APK device proof. No push, PR, CI,
signing, APK delivery, deployment, or release was performed.
