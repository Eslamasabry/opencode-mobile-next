# Thermal guard: pause the AI Team when the phone is hot (2026-09-26)

## Scope

The owner asked how to stop Android from killing the app and its workers "unless
the phone is overheating". Android never closes an app for heat: it throttles the
CPU and lets the heat build. So the app now does it: the AI Team runs normally,
is paused while Android says the phone is hot, and resumes by itself once the
phone has cooled.

| Part | Files |
|---|---|
| `oc/thermal` + `oc/thermal/events`, both halves: `PowerManager.getCurrentThermalStatus` + `addThermalStatusListener` (API 29+, registered once for the process's life), `getThermalHeadroom(30)` (API 30+, read at most every 30 s while Dart listens) → `{status: none…shutdown\|unknown, headroom (-1 = none), sdk}`; `notify {title, text}` posts one notification on the team's existing status channel `opencode_coding_status`, only when that channel exists and is not silenced. Registered from `AppLifecycle.register` (no `MainActivity` change). The listener type is created only on Android 10+. | `android/.../ThermalMonitor.kt`, `AppLifecycle.kt` (one line), `lib/platform/thermal.dart` |
| Policy (pure Dart): SEVERE, or headroom ≥ 0.95, pauses; CRITICAL / EMERGENCY / SHUTDOWN stops (paused first); resumes only what the guard itself took, after status ≤ MODERATE (and headroom < 0.95) for 2 minutes; a hot reading in between restarts the wait; switched off it starts nothing but still gives back what it holds | `lib/builtin/thermal_guard.dart` (`ThermalPolicy`) |
| Guard: only when a team works on **this** phone; one run at a time; each episode saved under `oc.thermalPause.<profileId>` (swept with the profile) so a pause survives the app dying and the next process resumes it; `thermal.pause` / `thermal.stop` trace marks with status, headroom, teams, sessions; `thermal.resume` as a duration span; diagnostics entries (`thermal`) | `lib/builtin/thermal_guard.dart` (`ThermalGuard`) |
| How a team is paused (Gas City supervisor API on loopback, the built-in team on 8472 and a Termux team on 8372): `PATCH /v0/city/{c} {suspended: true}` (kept across supervisor restarts; nothing new is started), then `POST session/{id}/suspend` for each running session ("save state, free resources": the bead and conversation stay). Resume: `PATCH {suspended: false}` and `POST session/{id}/wake` for exactly those sessions. A city already suspended, or sessions already asleep/suspended, are the person's and never touched. Stop: the built-in team's supervisor service is also stopped (`BuiltinTeam.stop`) and restarted on resume (`ensureRunning`, then waits up to 3 min for the city to answer); a Termux team stays paused (the app does not own its process). The OpenCode server is never stopped: the person's conversation may be running there. A team the person turned off meanwhile is not started again. | `lib/builtin/thermal_guard_teams.dart` |
| Hook: `_RootState.initState` starts the guard once, Android only | `lib/main.dart` |
| The line: one `KitNotice` under the exit notice in the shell, once per episode (pause, or a stop without a pause), updated on escalation, then "Resumed …"; dismissible | `lib/ui/widgets/thermal_notice.dart`, `lib/ui/screens/home_screen.dart` (one line) |
| Setting: Settings › This app › Keep running in the background › "Pause the AI Team when the phone is hot" (on by default, `oc.thermalGuard`) | `lib/ui/screens/keep_running_screen.dart` |
| Copy | 5 strings in `app_en.arb` and `app_ar.arb` |

Notifications: only while the app is in the background, one when the team is
paused (or stopped) and one when it resumes; nothing when the person has no team
status channel yet or silenced it. No new channel.

## Builds

Branch `feat/thermal-guard` from `feat/phone-setup-v2` (`13152fdc`). No APK published.

## Devices

None. No emulator, no phone (not allowed for this slice).

## Runs

| # | Step | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | `flutter test test/thermal_guard_test.dart` (21 tests) | Policy: none…moderate do nothing, SEVERE pauses, CRITICAL/EMERGENCY/SHUTDOWN stop; headroom 0.95 pauses at LIGHT, 0.94 does not; paused → CRITICAL stops, stopped does not escalate; resume only after 2 cool minutes, a SEVERE reading at 120 s restarts the wait (resume at 270 s); off: no action, but a held team is still given back. Guard: SEVERE pauses once, saves `oc.thermalPause.phone` with the sessions and status, one line, one notification, nothing more on further hot readings; CRITICAL after a pause stops it, still one notification; cool 2 min → resume with service and sessions, "Resumed" line and notification, record removed; on screen: line, no notification; no team on this phone: nothing; switched off: nothing; unreachable team retried after another wait; a new process restores the pause and resumes it. Supervisor API: only loopback teams; the exact requests (city suspend, only the running session suspended, wake only it, city resume); a city the person suspended gets no write; a team turned off meanwhile gets no request. Kotlin half through a fake channel: status words, headroom -1/NaN → none, unknown words, null, events stream, missing channel → safe, notify arguments. Widgets: line once, dismissed stays away, "Resumed" after cooling; the Keep running switch on by default, a tap turns it off and saves it | All pass | PASS |
| 2 | Same file with the two-minute wait removed (`>= Duration.zero`) | The hysteresis tests fail | 3 failures: "resumes only after two cool minutes…", "CRITICAL stops it…", "a pause survives the app dying…" (`fails-without-hysteresis.txt`) | PASS (fails without the change) |
| 3 | Affected tests: app_exit_recovery, l10n_coverage, design_standard, ui_glossary, accessibility_guidelines, app_lifecycle, home_navigation, release_blockers, settings_hub, server_switcher, first_run_landing, codex_navigation, team_home, project_hub, text_scale_overflow, safety_confirms, work_tab_cleanup, desktop_shortcuts, search_index, product_ui_regression; goldens work_tab, work_parts, team_discover (`-j 2`) | Pass | 358 tests (with run 1's file), all pass | PASS |
| 4 | `flutter analyze lib test` | Clean | No issues | PASS |
| 5 | `flutter build apk --release --target-platform android-arm64` under `/home/owner/Storage/tmp/oc-build.lock` | Kotlin and Dart compile | Dart AOT and `compileReleaseKotlin` done (`build/app/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/…/ThermalMonitor.class` written); the build then stops at `validateSigningRelease` (no `android/key.properties` here), as AGENTS.md expects | PASS (compiles; not signed) |

## Evidence

- `1-work-tab-paused.png`: the Work tab after SEVERE (headroom 1.02) while the
  phone's AI Team worked, 412×915 dark.
- `2-work-tab-resumed.png`: the same after two cool minutes.
- `3-keep-running-thermal-switch.png`: Keep running in the background with the
  new switch (a Pixel, battery already allowed).
- `fails-without-hysteresis.txt`: run 2.

## How to reproduce

```sh
F=~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
$F pub get
$F test test/thermal_guard_test.dart
$F test --concurrency=1 tool/capture/thermal_guard_test.dart   # renders
flock /home/owner/Storage/tmp/oc-build.lock $F build apk --release --target-platform android-arm64
```

On-device steps for the coordinator (a test phone on Android 10+, the in-app
OpenCode set up, AI Team on, a task being worked on; not the owner's phone):

1. `adb logcat -s flutter OcThermal | grep -E 'OCTRACE|thermal'` in one terminal.
2. `adb shell cmd thermalservice override-status 3` (SEVERE). Expected within a
   second: the Work tab says "Your phone is hot — paused the AI Team to cool down.
   It resumes by itself."; `OCTRACE mark thermal.pause status=severe … teams=1
   sessions=N`; on the Team screen the agents stop; `curl -s
   127.0.0.1:8472/v0/city/phone` from the built-in Ubuntu shows `"suspended":true`
   and `/v0/city/phone/sessions` shows the working sessions suspended. With the app
   in the background (Home first), one notification on the team's status channel.
3. `adb shell cmd thermalservice override-status 4` (CRITICAL). Expected: the line
   becomes "Your phone is very hot — stopped the AI Team …"; `thermal.stop`; the
   "OpenCode and AI Team are running" notification drops the team (the aiteam
   service stops); the OpenCode server keeps running and a conversation continues.
4. `adb shell cmd thermalservice override-status 1` (LIGHT), wait 2 minutes.
   Expected: the team's service starts again, the city resumes, the suspended
   sessions wake and the task continues; "Resumed the AI Team — your phone has
   cooled down."; `OCTRACE span thermal.resume … minutes=2`. Back and forth between
   3 and 1 within 2 minutes must not resume.
5. Person's pause: suspend the city by hand (`gc suspend` in the built-in Ubuntu,
   `cd /root/aiteam/city`), then override-status 3 → no line, no change; override
   1 for 2 minutes → the city stays suspended.
6. Settings › This app › Keep running in the background: switch "Pause the AI
   Team when the phone is hot" off, override-status 3 → nothing happens.
7. Force-stop during a pause (step 2, then `adb shell am force-stop …`), reopen,
   override 1 for 2 minutes → the team resumes (the pause was saved).
8. `adb shell cmd thermalservice reset` at the end. Settings › Diagnostics lists
   the `thermal` entries.

## NOT proven

- Nothing ran on a device: the Kotlin half (the status listener, headroom
  readings, the 30 s poll, the notification on the existing channel) is compiled,
  not exercised.
- That the supervisor honours `PATCH /v0/city/{c}` and `session/{id}/suspend` /
  `wake` on the phone's Gas City exactly as the pinned OpenAPI and `gc --help`
  describe (checked against `contracts/gascity-supervisor-openapi-v0-3648ca2d499a.json`
  and the PC's `gc` 1.4.1 help, not a live city), and how quickly a suspended
  session's processes (opencode acp, MCP servers) actually exit.
- A Termux-hosted team: reached on loopback like the built-in one, but not run.
  A team the app reaches through the host front (`front: true`, 8373) is left
  alone: the guard only talks to a bare supervisor on loopback.
- How much the pause cools a real phone, and whether 2 minutes at MODERATE is the
  right wait on the owner's RedMagic.
- Arabic copy not viewed on screen.
