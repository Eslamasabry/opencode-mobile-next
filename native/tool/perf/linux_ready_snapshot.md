# `linux-ready` emulator snapshot: recipe (WP-13.6, for S3)

Written by S2-BACKOFF (plan 15 row, plan 13 WP-13.6). It is a recipe and has not been run yet. The snapshot needs
a `stableQa` APK with the built-in Linux installer, and that APK is not built yet. PR-03 and PR-03b
(`hidden_run.sh --scenario phone-server|linux-service`, plan 13 §3.5) load this snapshot. `t4.sh` reloads
`perf-base` once they finish (§3.7 rule 2).

## What it holds

- AVD `OC_API37`, the same device as `perf-base`. It is a second snapshot of that AVD, not a new AVD (14 T14-3:
  disk space).
- The `stableQa` build of the native app, with the built-in Linux installed by the app's own installer and the
  agents backend set up. It has no personal data, no Flutter app, no sign-in to any outside service, and no
  provider keys (16: credentials never go into a shared image).
- The device settings of §3.7 rule 2: animator scales 1.0, gesture navigation on, font scale 1.0, dark theme off,
  screen timeout 30 min, stay awake on.

## Steps (on the PC, with the emulator lock held; launch detached, §3.7 rule 7)

1. Take the emulator lock (`setsid flock "$OC_EMULATOR_LOCK" sleep 7200`) and record both PIDs.
2. Cold-boot `OC_API37` from `perf-base` (`emulator -avd OC_API37 -snapshot perf-base -no-snapshot-save`), then
   run `tool/perf/restore_device.sh --serial <serial>`.
3. Uninstall the Flutter R package and the `….bench` package, so that the snapshot holds one app only:
   `adb uninstall <pkg>` for each.
4. Install the VPS-built `stableQa` APK (§3.10). Use `adb install -r`, never a debug APK.
5. Open the app. In This phone › Linux, install the built-in Linux with the default components and wait until the
   setup job reports done. Then start the phone server and the agents backend once, and stop both. PR-03 starts them
   itself, so they are stopped in the snapshot.
6. Check: `adb shell run-as <pkg> ls files/` shows the Linux root. No proot process is left
   (`adb shell ps -A -o PID,NAME | grep proot` is empty; kill any leftover by its exact PID). `dumpsys deviceidle`
   is not forced.
7. Press Home, turn the screen off, and wait 60 s so the app settles into HiddenIdle.
8. Save the snapshot: `adb emu avd snapshot save linux-ready`.
9. Load `perf-base` again (`adb emu avd snapshot load perf-base`), run `restore_device.sh`, and release the lock by
   killing the recorded `sleep` PID.

## Refresh

Remake the snapshot whenever the Linux component manifest or the `stableQa` installer changes. Record the date, the
APK `versionCode` and the component manifest hash in `docs/qa/native/snapshots.md`; that file is created on the
first run, and the file's owner is the perf lead.
