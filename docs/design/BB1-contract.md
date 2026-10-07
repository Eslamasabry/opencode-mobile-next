# BB1 — per-service lifetime diagnostics

Finish line: `BuiltinLinux.performance()` exposes each app-owned service's last
exit, current and last uptime, and restart count through a typed snapshot.
Non-goal: this Dart contract does not change restart policy or implement UI.

## Read API

Call the existing `Future<BuiltinPerformance> BuiltinLinux.performance()`.
Its `services` property is `Map<String, BuiltinServiceDiagnostics>`, keyed by
native service name (`server`, and other app-owned services when present).
Parsed maps are immutable. The native `performance` MethodChannel response adds:

```text
services: {
  <service-name>: {
    running: bool,
    lastExitCode: int?,
    lastUptimeMs: int?,
    uptimeMs: int?,
    restartCount: int,
    exitReason: memory_or_phantom_kill | exited | unknown
  }
}
```

The Dart snapshot retains those field names and uses `BuiltinServiceExitReason`
for `exitReason`: `memoryOrPhantomKill`, `exited`, or `unknown`. A zero exit code
or zero uptime is a real observation. Null exit code or uptime means no valid
observation. Uptime is in milliseconds; `uptimeMs` describes the current run and
`lastUptimeMs` describes the last completed run. `restartCount` is the native
persisted restart counter, not a count inferred by the frontend.

## States and defensive parsing

- A running service can also retain the previous exit and completed uptime.
- A stopped service can retain diagnostics; do not hide its record because
  `running` is false.
- Older APKs and unsupported platforms return an empty service map. Display
  unavailable diagnostics rather than suggesting no crashes have ever occurred.
- Invalid containers, invalid names and entries that are not maps are ignored.
  Invalid individual fields become unknown/null, false, or zero for the counter.
  Negative uptimes and restart counts are invalid. Unknown exit reason strings
  remain `unknown`; Dart does not guess a cause from an exit code.

## Frontend copy and errors

Use plain copy such as “Last run stopped after 4 seconds” and “Restarted 3 times”.
For `memoryOrPhantomKill`, use “The system may have stopped this service to free
memory or limit background processes. Try again; close other apps if it keeps
happening.” Exit 137 is a probable system/SIGKILL cause; it does not prove an
out-of-memory event or Android phantom-process kill.

Keep the numeric exit code and reason identifier under Details. For unknown
causes, use “The service stopped. Try starting it again.” Missing observations
should read “Not available”. Existing channel errors continue through
`BuiltinLinuxException`; this snapshot adds no new error codes or user actions.

## Privacy and persistence

The snapshot contains service names and numeric lifetime data only. It must not
contain scripts, environment, provider keys, passwords or log output. Native code
owns persistence; Dart neither stores these diagnostics nor changes profile
deletion rules.

## Verification

`test/builtin_performance_diagnostics_test.dart` covers the actual MethodChannel
map round-trip, older/absent responses, malformed entries/numbers, unknown exit
reasons, zero values, immutable parsed maps and unsupported platforms. Native
persistence and emulator exit evidence are recorded by the BB runtime owner.

Dart checks on 2026-10-07 used the pinned Flutter/Dart toolchain:

```bash
dart format --language-version=3.10 lib/builtin/builtin_linux.dart test/builtin_performance_diagnostics_test.dart
tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 test/builtin_performance_diagnostics_test.dart
```

The focused file passed all seven tests. Regression proof replaced the
`BuiltinPerformance.fromMap` service-map parser with `services: const {}` and ran
only `--plain-name 'performance reads each service lifetime through the channel'`.
It failed with expected service names `[server, team]` versus actual `[]` (exit 1).
Restoring the parser and rerunning the entire focused file passed seven tests
(exit 0). `git diff --check` was clean. No analyzer, full suite, native build or
device acceptance is claimed by these Dart checks.
