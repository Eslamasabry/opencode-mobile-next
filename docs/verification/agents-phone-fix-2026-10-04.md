# Agents phone failure investigation — 2026-10-04

Candidate: `codex/agents-phone-fix`, based on APK 2105's `13756021`.
No device, account, signing, publication or full-suite proof is claimed.

The 1 ms `agent-node` failure is consistent with a reproduced native defect:
`BuiltinLinux.prootCommand(agentUser=true)` compared an app directory's
absolute and canonical paths. Android's trusted `/data/user/0` alias can
resolve to `/data/data`; the first non-root component therefore failed before
`ProcessBuilder.start()`. Dart script/pin construction had already succeeded
before the native component's recorded duration. The regression harness
compiles that production path block and failed twice before the fix. The fix
resolves only the Android-owned anchor and rejects managed descendant links,
including profile homes, configuration, deletion and project-view directories.
OpenCode 1 remains root-owned and works on the same project bind.

That caught component exception does **not** establish the JVM crash's cause.
The closest trace-aligned unguarded path was the terminal notification after
failure: `SetupRunner.runJob → SetupService.finish → notification API`.
Foreground-service promotion in `SetupService.onStartCommand` was also outside
the background channel's catch. Android policy/notification exceptions could
escape either callback and terminate the foreground app. No old stack exists,
so the observed exception cannot be named with certainty.

Setup/agent process readers, startup/cleanup, notification callbacks and service
supervisors now have safe boundaries. Built-in Linux, Termux and lifecycle
channel replies have one terminal attempt, including queued replies on detach;
a failed messenger is never retried. Synchronous failures use fixed error codes
and plain copy. Asynchronous service-policy denial stops work; the durable
setup snapshot/status remains authoritative. Android lifetime remains bounded.

`OcApplication` installs a JVM uncaught-exception hook before activities/services.
It writes an atomic app-private `native-last-crash.properties`, then chains to
the previous handler even if writing fails. Only exception types, timestamp,
structured source frames and fixed message categories survive. Raw messages,
URLs, codes, home paths, thread names and user content are discarded; up to three
safe nested causes preserve Android's wrapped service exception. OS exit
records' raw description and full `toString()` no longer enter diagnostics/logs.
`oc/lifecycle.launchReport.lastCrash` reaches the existing startup diagnostics
and persisted problem report as `crash.last`, with top frames in Performance.

Checks and next device action are recorded in the coordinator's c6-report.md.

Validation: pinned `flutter pub get`; Dart 3.10 formatting; clean diff check.
233 unique focused Flutter tests across 22 files passed, including actual
Kotlin path/check/service/reply/crash harnesses and the size/architecture gates.
The old path check failed alias/repeat regressions first; fixed path checks pass.
Ten existing fake-process Claude sign-in scenarios also pass. Whole-repository
pinned `flutter analyze --no-pub` is clean on the final candidate.
`:app:compileReleaseKotlin` passed using pinned Flutter/Gradle 9.5.0, ARM64 target,
one Gradle worker and in-process Kotlin compilation; existing compiler/Gradle
warnings remain. Logs are local under `build/traycer/c6-*`, not committed.
No fresh native build has been installed on nx721j. The coordinator must rebuild
with the existing signing certificate; native fixes cannot use a Dart-only patch.
Retry Claude install → phone check → subscription sign-in without clearing data.
If Android crashes again, reopen and export Performance/diagnostics (`crash.last`).
