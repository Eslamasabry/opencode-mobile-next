# BC2 — disk guard on Android

Date: 2026-10-07. Device: `emulator-5554`. Execution: Android `dalvikvm`.
Production source: `SetupDiskSpace` in
`android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupRunner.kt`.

Finish line: low disk space produces plain guidance before work, while healthy
checks permit work. Non-goal: filling shared disks, app installation, APK/native
app integration or other BC items.

Run:

```sh
python3 tool/qa/bc2_disk_emulator.py
```

The harness extracts the production object without rewriting it, compiles that
object and a small Kotlin proof under `tool/qa/machine_lock.sh build`, converts
the temporary JAR with Android D8, then pushes only the resulting temporary dex.
Every ADB session uses `flock /home/eslam/Storage/tmp/oc-emulator.lock` and targets
only `emulator-5554`. The proof uses a disposable directory under
`/data/local/tmp/oc-bc2-<uuid>`, which is removed in `finally`.

Low-space readings come from a `java.io.File` subclass overriding `usableSpace`;
the directory and marker writes are real Android filesystem operations. The
production guard is called before the marker write. No disk image is filled,
app state is changed, APK is installed, or credentials are read.

Results in [emulator.log](emulator.log):

- Install checks reject 0 and 299,999,999 available bytes, with a required
  threshold of 300,000,000 bytes. Both leave the write marker absent.
- Launch checks reject 0 and 127,999,999 available bytes, with a required
  threshold of 128,000,000 bytes. Both leave the marker absent.
- Both exact thresholds allow the real marker write.
- Reusing a directory after its measured space changes from healthy to low
  blocks the next write, proving the policy reads space again.
- The real Android scratch directory passes. A nonexistent nested destination
  also passes by inspecting its nearest existing ancestor, without creating the
  missing directories.
- `requiredInstallBytes(Long.MAX_VALUE)` saturates at `Long.MAX_VALUE`.
- Every blocked check returns: "There is not enough free space on this phone.
  Free some storage and try again." No `ENOSPC` is shown.

The log records the extracted production-object SHA-256. Python syntax and
scoped `git diff --check` passed. The first bounded build-lock wait expired
while another lane built; the retry completed the unchanged policy proof.

This verifies the production policy executing on Android. It does not claim a
full APK check or that the native setup/agent paths invoke it correctly; those
call-site and between-component regression checks are separate host-runner
evidence recorded by the BC lead. No APK, signing, release or deployment was
performed.

Root call-site checks: `flutter test --no-pub --concurrency=1 test/setup_preflight_test.dart test/setup_engine_test.dart test/setup_runner_native_test.dart test/phone_agent_host_native_test.dart` through the shared test lock: **64 passed**, no skips. The original runner failed `low-space-first` and `low-space-next`; the original private host failed `low-space-launch`. Production fixes restored and suite passed. Analyzer through shared analyze lock: **No issues found** (14.2 s). Local logs: `focused-after.log`, `regression-before.log`, `launch-before.log`, `analyze.log`. These logs stay local; this report and harness are committed.
