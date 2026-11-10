# Force stop: recover and explain (2026-09-26)

## Scope

The owner's phone (RedMagic / Nubia NX721J, Android 15, build 2054) force-stops
OpenCode Mobile when it is swiped from Recents or by its battery manager, even with
the "OpenCode is running" foreground service up. `dumpsys activity exit-info` showed
`reason=10 (USER REQUESTED) subreason=21 (FORCE STOP) … from pid 2285 (system) …
importance=125` at 2026-09-26 00:06:33 and 2026-09-25 20:19:05. A force stop kills
the app and every child: the built-in Ubuntu's OpenCode server, the AI Team
supervisor, Dolt and working agents. The next launch said nothing.

What changed:

| Part | Files |
|---|---|
| `oc/lifecycle` channel, both halves: `launchReport` (the newest main-process `ApplicationExitInfo` not reported before, read once per process; subreason parsed from the record's text), `keepAliveInfo` (`Build.MANUFACTURER`/`BRAND`, battery exemption), `openKeepAliveSetting` (battery via the existing `requestBatteryOptimizationExemption`, the maker's auto-start screen, App info; each candidate in try/catch, false when nothing opened) | `android/.../AppLifecycle.kt`, `MainActivity.kt` (registration), `lib/platform/app_exit.dart` |
| What ran at death: `BuiltinLinux.kt` writes the running service names (`server`, `aiteam`) to `oc_lifecycle` prefs on every change (`commit`). A stop the person asks for clears them; a force stop runs none of our code and leaves them. The previous process's set is captured once per process before this one records anything. | `BuiltinLinux.kt`, `AppLifecycle.kt` |
| Classification: force stop (10/11, or subreason 21/22/23), low memory (3, or memory subreasons), crash (4/5/6/7), killed (other), update (15/16, or subreason 25), normal (1/0) | `lib/platform/app_exit.dart` |
| Recovery, once per process from the app shell (`_RootState.initState`): trace marks `app.lastExit` and `app.recover`, a diagnostics entry (`android.exit`) for notable exits; when `server`/`aiteam` ran: the notice (only for notable kinds) and a restart. Opened on the in-app server, the shell's existing `autoStartIfStopped` starts it (no second path); opened on another server, the recovery calls the same `BuiltinServerStarter.autoStartIfStopped` for the saved in-app profile. The team follows the server in `BuiltinServerStarter.start` (`BuiltinTeam.ensureRunning`), unchanged. | `lib/builtin/app_exit_recovery.dart`, `lib/main.dart` (one hook) |
| The notice: one `KitNotice` under the shell's connection banner, until dismissed; "Keep it running" except after a crash | `lib/ui/widgets/app_exit_notice.dart`, `lib/ui/screens/home_screen.dart` |
| Keep-alive guidance: Settings › This app › Keep running in the background (search entry `settings-keep-running`, Android only), also a row on the built-in server's page and the notice's action. Per maker: Nubia/RedMagic (swipe warning, battery, lock in Recents, background, auto-start), Xiaomi, Oppo/OnePlus/Realme, Vivo, Huawei/Honor, Samsung, other (battery, background). Never opens by itself. | `lib/platform/keep_alive_advice.dart`, `lib/ui/screens/keep_running_screen.dart`, `settings_screen.dart`, `search/search_index.dart`, `builtin_server_screen.dart`, ledger page `keep-running` |
| Copy | 33 strings in `app_en.arb` and `app_ar.arb` |

Termux-hosted servers and teams are not affected: they run in Termux's own process
(`com.termux.RUN_COMMAND`), so a force stop of this app leaves them running. The
recovery only looks at the built-in Ubuntu's services.

Work in progress: Gas City keeps routed work in its store and resumes it when the
supervisor starts again. Evidence: `aiteam-builtin-2026-09-24` Run 3 "upgrade
path" (Android killed the team mid-merge; on the app's next start the refinery
resumed the interrupted merge and the hook brought the commit in). No extra wake
was added.

`lib/state/connection.dart` is not touched.

## Builds

Branch `fix/force-stop-resilience` from `feat/phone-setup-v2` (`6b35f523`). No APK
published. `flutter build apk --release --target-platform android-arm64` compiled
the Kotlin and Dart sides and stopped at signing (see Runs).

## Devices

None. No emulator, no phone (not allowed for this slice).

## Runs

| # | Step | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | `flutter test test/app_exit_recovery_test.dart` (23 tests) | Owner's record → force stop; every reason → one kind; channel absent or failing → safe answers; force stop with server+team → one notice, server then team started, diagnostics entry; nothing running → no notice, no start; update / normal / no record → no notice, server started; opened on the phone server → notice only, shell starts it; runs once; makers mapped from `Build.MANUFACTURER`/`BRAND`; words per maker; notice text "Android closed OpenCode Mobile at 00:06. Your phone's OpenCode and the AI Team stopped with it; they're starting again.", dismiss folds it, "Keep it running" opens the guidance, earlier day named, crash without the action, nothing after normal exit/update; RedMagic guidance (swipe warning, lock text, opens autostart and battery, the lock opens nothing); Pixel (no lock, battery Allowed); a missing screen → "This phone has no such screen…", a throwing channel → no crash | All pass | PASS |
| 2 | Same file with the recovery's restart disabled (`if (false) await _restart(`) | The restart tests fail | `Expected: ['start server', 'start aiteam'] Actual: []` (`fails-without-restart.txt`) | PASS (fails without the change) |
| 3 | Affected tests: builtin_linux, builtin_server(_autostart, _screen), builtin_team, design_standard, l10n_coverage, search_index, settings_hub, ui_glossary, accessibility_guidelines, app_lifecycle, app_text_scale, home_navigation, first_run_landing, release_blockers, server_profile_reentry, product_ui_regression, server_switcher; goldens settings, work_tab, work_parts, team_discover, phone_server_screens | Pass | All pass except `l10n_coverage_test` "no file gained hardcoded UI strings": `lib/ui/widgets/team_now.dart` (not touched here, fails on the base too). `team_discover_settings_{dark,light}.png` updated deliberately: the new row in "This app". | PASS (one unrelated failure) |
| 4 | `flutter analyze lib test` | Clean | No issues | PASS |
| 5 | `flutter build apk --release --target-platform android-arm64` under `/home/owner/Storage/tmp/oc-build.lock` | Kotlin and Dart compile | Dart AOT and `compileReleaseKotlin` done (`build/app/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/…/AppLifecycle.class` written); the build then stops at `validateSigningRelease` (no `android/key.properties` here), as AGENTS.md expects | PASS (compiles; not signed) |

## Evidence

- `1-work-tab-notice.png`: the Work tab after the owner's force stop (server and
  team were running), 412×915 dark.
- `2-settings-keep-running-row.png`: Settings, the "Keep running in the background"
  row (the notice still up above).
- `3-keep-running-redmagic.png`: the guidance on a nubia/RedMagic.
- `4-keep-running-pixel.png`: the guidance on a Pixel, battery already allowed.
- `fails-without-restart.txt`: run 2.
- Trace lines from the render run: `OCTRACE mark app.lastExit … kind=forceStop
  reason=10 subReason=21 importance=125 … server=true team=true` and `OCTRACE mark
  app.recover … by=none reason=profile` (no in-app profile in the render fixture).

## How to reproduce

```sh
F=~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
$F pub get
$F test test/app_exit_recovery_test.dart
$F test --concurrency=1 tool/capture/force_stop_test.dart   # renders
flock /home/owner/Storage/tmp/oc-build.lock $F build apk --release --target-platform android-arm64
```

On-device steps for the coordinator (a Nubia/RedMagic, the in-app OpenCode set up,
AI Team on, a task in progress):

1. `adb shell dumpsys activity exit-info io.github.eslamasabry.opencode_mobile | head`
   to note the newest record.
2. Open Recents and swipe OpenCode Mobile away (or `adb shell am force-stop
   io.github.eslamasabry.opencode_mobile` on any phone).
3. `adb shell dumpsys activity exit-info …` shows a new `reason=10 … subreason=21
   (FORCE STOP)` record.
4. Reopen the app. Expected: on the Work tab, "Android closed OpenCode Mobile at
   HH:MM. Your phone's OpenCode and the AI Team stopped with it; they're starting
   again." with Keep it running; the phone's server connects without a tap
   (`adb logcat -s flutter | grep OCTRACE` shows `app.lastExit kind=forceStop …
   server=true team=true` and `app.recover`); `adb logcat -s OcLifecycle` shows
   "previous process ended: ApplicationExitInfo(…)"; the AI Team comes back and
   the task continues (Team home: the worker starts again; watch the bead reach
   Review/Done).
5. Close the app with the notification's Stop, then reopen: no notice and no
   restart (a stop the person asked for). Update the APK over itself: no notice.
6. Tap Keep it running: the RedMagic steps; Don't optimize battery shows Android's
   prompt; Allow auto-start opens the maker's screen or App info; lock the card in
   Recents, swipe: the app should survive.
7. Settings › Diagnostics lists the `android.exit` entry.

## NOT proven

- Nothing ran on a device: the Kotlin half (`getHistoricalProcessExitReasons`, the
  subreason parse from `toString()`, the prefs record surviving a force stop, the
  vendor intents) is compiled, not exercised.
- The Nubia auto-start component names (`cn.nubia.security2/…SelfStartActivity`)
  are unverified; a missing one falls back to App info.
- Whether RedMagic's "lock in Recents" also stops its battery manager's force
  stops.
- The team's in-progress task resuming after a force stop (as opposed to the
  phantom-process kill of Run 3) on a phone.
- Arabic copy not viewed on screen.
- A process started without UI (widget, tile) that dies again before the app is
  opened loses the older exit record; the restart still happens (the running set
  stays on disk).
