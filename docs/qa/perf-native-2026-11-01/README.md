# Perf, the native half: state and device proof still owed (2026-11-01)

Branch `perf/native-2026-11-01`. It merges `perf/dart-2026-11-01` (see
[../perf-dart-2026-11-01/README.md](../perf-dart-2026-11-01/README.md)) and
`feat/process-budget-2026-11-01` (see
[../process-budget-2026-11-01/README.md](../process-budget-2026-11-01/README.md))
and adds the pieces that need a new release because they are Kotlin. Audit:
`/home/eslam/Storage2/wt/perf-audit-2026-11-01/REPORT.md`.

Finish line: heat and low battery ease the phone's agents (lower priority, then
new heavy work waits), the phone server's status row stops being re-posted for
every change of the program set, and Always full speed turns the easing off.
Non-goal: killing a running turn; the protective pause at SEVERE is unchanged.

## What is in, by shipping state

| Piece | Implemented | Enabled | Verified |
|---|---|---|---|
| `AgentPriority.kt`: agent helper and the proot tracer below it go to Android's background class with `Process.setThreadPriority` (hold = nice 19), `normal` restores. Dart falls back to `renice` on a build without it. | yes | yes (heat tier slow or hold) | unit test `AgentPriorityTest` written, not run; no device record |
| Battery saver (`powerSave` channel call) is a heat input: slow tier | yes | yes | `heat_tier_test` written, not run; no device record |
| `ServiceNoticeGate.kt`: an unchanged notice is skipped, changed words wait 5 s and go once | yes | yes | `ServiceNoticeGateTest` written, not run. Cause measured on the owner's phone before the change: `lastStartId=804` in 1 h 50 min, 3015 notification updates (755 rate-limited). No after-measurement |
| Agent-backend watch: every 60 s instead of 10 s while the app is out of sight, back to 10 s the moment the app returns (`VisibilityPacedTimer`; the first version only noticed the return at its next 60 s tick) | yes | yes | `test/visibility_paced_timer_test.dart` written, not run |
| Process budget holds new heavy work on the heat-tier hold; Always full speed lifts it (the pause at SEVERE stays) | yes | yes | `test/heat_hold_test.dart` written, not run |

Nothing in the freeze was run: no `flutter test`, no Gradle unit tests, no
APK, no emulator, no phone. `flutter analyze` and `flutter gen-l10n` (no
drift) are the only checks made on this branch tip.

## Spike removed: `setProcessGroup`

`93bb40eeb` called the hidden `Process.setProcessGroup` by reflection on every
priority change, to see whether Android would move a whole process. It is
reverted here:
- nothing read its answer (`groupMoved` and `groupError` were never consumed by
  Dart, so it could not produce evidence in any build);
- `Process.setThreadPriority` already puts a thread at background priority
  into the background group, which is the effect the helper needs and what
  `AgentPriority.Result.group` reads back;
- a hidden-API reflection call is a store and compatibility risk in a build
  that ships to people, with no device record to justify it.

If a whole-process group is ever wanted, prove it first on a device and add it
back with a result Dart records.

## Device proof owed before this ships (needs a new release, then the coordinator)

Emulator with the release engine, a Paseo agent started from the app. Reset
afterwards with `adb shell cmd thermalservice reset` and
`adb shell settings put global low_power 0`.

1. Slow tier: `adb shell cmd thermalservice override-status 1`. Within one
   thermal reading, `adb shell ps -A -o PID,NI,ARGS | grep paseo` shows the
   helper tree at nice 10, and `/proc/<pid>/task/*/cgroup` of the helper shows
   the background cpuset. Reset: priorities return to 0 after about 2 minutes.
2. Hold tier: `override-status 2`. Nice 19, and a new Kilo or Claude Code
   conversation shows the "waiting" line (the phone is warm) instead of
   starting. `override-status 3` still pauses, with or without Always full
   speed.
3. Battery saver: `adb shell settings put global low_power 1` gives the slow
   tier with the phone cool.
4. Always full speed (Keep running, switch off by default): with it on, steps
   1 to 3 change nothing and the phone-budget note is removed from the
   profile's `claude/CLAUDE.md`.
5. Status row: with 3 agents running tools for 10 minutes,
   `adb shell dumpsys activity services BuiltinServerService | grep lastStartId`
   should grow by a handful (before: hundreds), and the row's words still
   follow the program count within about 5 s.
