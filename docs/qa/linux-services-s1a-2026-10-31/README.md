# Linux services S1a (supervisor + API): verification, 2026-10-31

Branch `feat/linux-services-core-2026-10-31`. This record covers the supervisor, the API and the heat hold. The Services pages are S1b.

## Tests (VPS, `OC_VPS_JOBS=4 tool/qa/vps_test.sh`)
| File | Result |
|---|---|
| `test/linux_services_script_test.dart` (supervisor run for real under bash) | 13/13 pass |
| `test/linux_services_test.dart` (formats, rules, rootfs API with a fake native side, heat, fake) | 29/29 pass |
| Touched neighbours: `builtin_server_test`, `builtin_server_autostart_test`, `thermal_guard_test`, `app_exit_recovery_test`, `phone_server_healing_test`, `linux_manager_screens_test` | 184/184 pass |
| App-root tests: `app_lifecycle_test`, `background_notification_navigation_test`, `bootstrap_start_fresh_test`, `credential_unreadable_status_test` | 21/21 pass |
| `flutter analyze` on every changed file | no issues |

## Each test fails without its fix (mutation runs)
| Mutation | Test that then fails |
|---|---|
| No give-up after quick crashes | quick crashes give up, and a kick starts it again |
| Neither the stop nor the reap kills the process group | stop ends the whole process group and it stays stopped |
| The heat hold is ignored | the heat hold stops services and gives them back |
| No log rotation | logs are rotated at the cap |
| A crash goes straight to failed (no backoff) | a killed service comes back after its backoff |
| Start kicks a service that is already running | start: on, a kick, the supervisor installed and started |
| No refusal of linked folders on the way | a linked folder on the way is refused, not read through |
| Two ensures not serialised | two ensures at once start the supervisor once |

Removing only the stop's group kill does not fail its test, because the reap also kills the group. That safety net is intentional, so the mutation removes both kills.

## Emulator proof (emulator-5584, APK 4370, 2.1.0)
The APK was built with a temporary hook (`--dart-define=OC_SVC_DEMO=true`, never committed). On app open, the hook saved and started `demo` (`python3 -m http.server 8090 --bind 127.0.0.1`, port 8090) through `RootfsLinuxServices.save` and `RootfsLinuxServices.start`. Every later step used the files the API writes, set over `adb root`.

| Step | Observed |
|---|---|
| App opened | `phase=running pid=8082`. `GET /` on 8090 returned `HTTP/1.0 200 OK`. `BuiltinServerService` was in the foreground. |
| `kill -9 8082` | Back as pid 8343 after a 1 s backoff, with `restarts=1 last_exit=137`. Served 200. |
| `want` set to `off` | `phase=off`. Still off 8 s later, with the port refusing connections. |
| `want` set to `on` | `phase=running pid=8919`. Served 200. |
| `am force-stop`, then reopen | The old pid was gone. The app open hook (`ensureSupervisor`) brought `demo` back as pid 9064. Served 200. |
| Log | `::svc start`, `::svc exit 137 3`, `::svc backoff 1`, `::svc start`, `::svc stop`, `::svc exit 143 5`, `::svc start` (×2), plus Python's request lines |

**Known noise:** with `set -m`, bash writes job notices ("Killed …", "Terminated …") into `_svd.log`. That file is capped like every other log.

## Stop is Stop: the notification's Stop (fix 57767cf64, APK 4371, no debug hooks)
Everything ran in one lock hold of 40 s.

| Step | Observed |
|---|---|
| Set `demo` to on and open the app | The app-open hook started the supervisor (`oc-svd v2`). `phase=running pid=9114`, and HTTP returned 200. |
| Notification › **Stop OpenCode** (tapped through uiautomator) | The supervisor logged "stopping all", "turned off demo" and "stopped". `want=off`, `phase=off`, and the port refused connections. |
| `am force-stop`, reopen, wait 25 s | Still `want=off` and `phase=off`, the port refused connections, and no foreground service was left. |

Tests: "SIGTERM (the notification's Stop) turns every service off" and "a restart for a newer script marks itself so choices are kept". Each one fails when its fix is removed (mutation runs M9 and M10).

## S1 integrated (S1a + S1b), real UI, APK 4372, no debug hooks
The flow was recorded with `screenrecord` on emulator-5584 and edited with Remotion on the build server: `/home/eslam/Storage2/wt/shots-2026-10-31/linux-s1-device/services-demo.mp4` (1080×1920, 51 s, no sound). The raw segments are next to it: the video uses `svc-1`, `svc-3` and `svc-7`; the other segments are retakes.

| Step | Seen in the footage |
|---|---|
| This phone › Linux › Services, then Add a service | The empty list, then the form |
| `web`: `python3 -m http.server 8091`, port 8091. Port 8090 was taken by another agent's demo shop, so 8091 avoided a "port in use" state. | Running for less than a minute · port 8091 |
| Open web | The preview shows the served directory listing |
| The terminal (This phone › Open a terminal) runs `(sleep 20; kill -9 <pid>) &`; the Services list stays on screen | The row reads "Crashed (exit 137). Trying again in 0 s", then "Running for less than a minute". The service page says "Came back once after a crash". |
| Stop web | `Off`. The log reads Started, the request lines, Crashed (exit 137), Trying again in 1 s, Started, then "Ended · exit 143" |
| Remove web (asks first) | The definition, choice and state files are gone, and the list is empty again |
