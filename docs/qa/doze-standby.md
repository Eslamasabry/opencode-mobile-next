# Doze and app standby test procedure (Android 13 to 16)

Covers issue #33. It tests that "Keep live in the background"
(`lib/background/live_background.dart`, `BackgroundConnectionService.kt`)
keeps an active run and its notifications accurate under Doze, app standby
buckets and the Android 15+ `dataSync` time limit. Results go in a record
under `docs/qa/doze-standby-<YYYY-MM-DD>/README.md` using the format in
[README.md](README.md).

Package: `io.github.eslamasabry.opencode_mobile`. Service:
`.BackgroundConnectionService` (foreground service type `dataSync`; a second
`specialUse` service carries the in-app server).

Use a release build (debug builds do not work with the Shorebird engine). A
physical device is required for the standby-bucket and OEM rows; an emulator
is enough for the forced-Doze rows.

## Setup

1. Install the build, connect to a server you control, and open a session.
2. Settings, Keep running: turn on "Keep live in the background" and grant
   the notification permission. Leave battery optimization at the default
   ("Optimized") for the first pass.
3. Plug the device into adb and note the starting state:

```sh
PKG=io.github.eslamasabry.opencode_mobile
adb shell getprop ro.build.version.release        # 13, 14, 15 or 16
adb shell dumpsys deviceidle | head -20           # mDeepEnabled, mLightEnabled
adb shell am get-standby-bucket $PKG              # expect 10 (active) while in use
adb shell dumpsys deviceidle whitelist | grep $PKG || echo "not exempt"
```

4. Start a long run in the app (one that streams for more than 10 minutes, or
   a prompt that sleeps in a tool), then press Home. Confirm the ongoing
   "session is live" notification is showing.

## A. Forced deep Doze

Simulate the screen-off, unplugged state. Do not unplug the cable for real;
tell Android it is unplugged.

```sh
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle enable deep
adb shell dumpsys deviceidle force-idle        # deep: IDLE
adb shell dumpsys deviceidle get deep          # expect IDLE
```

Expected, with the default battery setting (not exempt):

- The foreground service is still running:
  `adb shell dumpsys activity services $PKG | grep -i BackgroundConnection`.
- Network access may be blocked in deep Doze for apps that are not exempt.
  Record whether the stream stayed connected (`adb logcat -s flutter`).
- The notification text still matches what the app shows (no stale "running"
  after a stall).

Wake and compare:

```sh
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
```

Expected after wake: the app reconciles by refetch (it does not replay), the
transcript has the text produced meanwhile once, with no duplicates, and the
notification matches the session state. Repeat with the exemption granted
(Settings, Keep running, "Allow background use", or
`adb shell dumpsys deviceidle whitelist +$PKG`). Record whether the stream
survived this time.

## B. Light Doze

```sh
adb shell dumpsys deviceidle enable light
adb shell dumpsys deviceidle force-idle light
adb shell dumpsys deviceidle get light         # IDLE
# wait 2 minutes, then
adb shell dumpsys deviceidle unforce
```

Expected: a permission request raised on the server while idle produces its
notification within the window Android allows (high priority alerts are
delivered; note the delay). Tapping Allow once on a locked screen asks for
authentication (Android 12+).

## C. Standby buckets

```sh
adb shell am set-standby-bucket $PKG active        # baseline
adb shell am set-standby-bucket $PKG working_set
adb shell am set-standby-bucket $PKG frequent
adb shell am set-standby-bucket $PKG rare
adb shell am set-standby-bucket $PKG restricted
adb shell am get-standby-bucket $PKG
```

For each bucket, with a run active and the app in the background for 5 minutes:

| Bucket | Check |
|---|---|
| active, working_set, frequent | foreground service survives; notification updates on time |
| rare | notifications still arrive; record any delay of the attention alert |
| restricted | record what Android blocks (jobs, alarms, FCM-like delivery). The foreground service must still run. If it is killed, that is a finding |

Also read what the system thinks:

```sh
adb shell dumpsys usagestats | grep -A3 $PKG
adb shell dumpsys deviceidle whitelist | grep $PKG
```

## D. Android 15 and 16 `dataSync` limit

Android 15+ lets a `dataSync` foreground service run about 6 hours in a
rolling 24 hours; the battery exemption does not lift it. The app handles the
callback in `BackgroundConnectionService.onTimeout`: it removes the ongoing
notification, tells Dart (`backgroundServiceTimeout`), and stops.

Fast check (emulator or device, Android 15+):

```sh
adb shell cmd activity set-fgs-timeout-override $PKG dataSync 60000  # if available
# otherwise use the platform test: wait for the real timeout on a spare device
```

Expected: after the limit, the "session is live" notification is gone, the
Keep running screen shows the timeout notice rather than a stale "on", and
turning the setting back on restarts the service. Record the Android build
and whether the override command exists on it.

## E. OEM kill check (physical device only)

On a Xiaomi, Samsung, OnePlus, Oppo or Huawei device (see
https://dontkillmyapp.com for the vendor's switches):

1. Run steps A and C with the vendor's battery manager at its default.
2. Swipe the app from recents with a run active. Record whether the service
   survives (it should not be expected to on every OEM; the point is the
   notice shown the next time the app opens).
3. Apply the vendor's "allow background activity" switch and repeat.

## What to record

For each Android version and device: build and commit, which sections were
run, PASS or FAIL per expected line, the output of
`adb shell dumpsys deviceidle | head -20` before and after, any log excerpt
with credentials redacted, and what was not run.

## Open questions this procedure answers

- Does the app keep a run alive in deep Doze with and without the battery
  exemption? (A, with both settings.)
- Does the in-app battery prompt (`lib/ui/screens/keep_running_screen.dart`)
  appear when the exemption is missing and the OEM is known to kill services?
  (E, step 1 versus 3.)
- Is the 6-hour `dataSync` limit reported to the user? (D.)
