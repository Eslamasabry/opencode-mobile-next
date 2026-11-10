# Emulator boot repair — 2026-09-27

Goal: a headless Android 15 emulator on the shared Linux PC that boots reliably
so the app can be proven on it later. No app was built or installed; the
owner's physical phone was not touched.

## Result

`OC_API35` (Android 15, API 35, x86_64 `google_apis` rev 9) cold-boots
headless in about 36 seconds:

| Run | Command | `sys.boot_completed=1` after | `pm list packages` |
| --- | --- | --- | --- |
| 1 | working command + `-verbose -show-kernel`, old AVD config | 36 s | 242 packages |
| 2 | working command as below, repaired AVD config | 37 s | works |

After run 1 the guest ran for about 6 minutes. `system_server` kept the same
PID the whole time (no restart), and logcat had no `Watchdog ***`, ANR or
`FATAL EXCEPTION`. During one spike in host load (load average 15 on 8
threads) adb shell stopped responding for about 30 seconds, then came back.
Both runs were shut down with `adb -s emulator-5554 emu kill`, and the qemu
PID exited within 3 seconds.

## Working launch command

```bash
~/Android/Sdk/emulator/emulator -avd OC_API35 -port 5554 -memory 2048 -cores 2 \
  -no-snapshot-load -no-snapshot-save -gpu swiftshader_indirect \
  -no-audio -no-window -no-boot-anim &
EMU_PID=$!
ADB="$HOME/Android/Sdk/platform-tools/adb -s emulator-5554"   # always pass -s: the owner's phone may be listed
until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 3; done
$ADB shell pm list packages | head
# ... use it ...
$ADB emu kill            # clean shutdown; then confirm: ps -p $EMU_PID
```

To diagnose a stall, add `-verbose -show-kernel` and redirect the output to a
log file.

## Environment checked

- Emulator 36.2.12.0; system images: android-34 and android-35 `google_apis/x86_64`.
- KVM: `/dev/kvm` is present, the user is in group `kvm`, and
  `emulator -accel-check` reports "KVM (version 12) is installed and usable".
  `kvm-ok` is not installed.
- Host: i7-9700 (8 threads), 15.7 GB RAM with only about 4–5 GB available,
  load average 6–15 from about 15 build agents.
- Disks: the `OC_API35` AVD lives at `/home/owner/avd-root/OC_API35.avd` on the
  root disk (96–97 % used, about 8–9 GB free). `~/.android` is a symlink to
  `/home/owner/Storage/relocated-home/.android` on the Storage disk, so
  `Pixel_6` and the other `~/.android/avd/*` AVDs live on the disk that
  filled up earlier today.
- The system image was intact and did not need to be recreated. Nothing was
  downloaded.

## Root cause

The earlier stall was not reproduced, so this cause is inferred from the AVD's
state and the host's load:

1. **Quick boot restored an old 4 GB snapshot.** `config.ini` had
   `fastboot.forceFastBoot=yes`, `hw.ramSize=4096` and `hw.cpu.ncore=4`, and the
   earlier launch commands (see `docs/qa/issue-87-proof-2026-09-25` and
   `docs/qa/phone-setup-v2-2026-09-24`) did not pass `-no-snapshot-load`. So
   every launch tried to restore the `default_boot` snapshot saved on
   2026-09-26 at 18:14: 4 GB of guest RAM plus GPU textures from an earlier
   session.
2. **The host could not back a 4 GB, 4-core guest.** The emulator backs guest
   RAM with a file on the AVD's own disk
   (`-mem-path …/snapshots/default_boot/ram.img`). That disk was 96–97 % full,
   the host had about 4 GB of RAM available, and it was already under load
   from the build agents. The likely result was a guest that swapped instead
   of finishing boot.
3. **`Pixel_6` (watchdog):** its disks are on the Storage disk, which was full
   earlier today, and its userdata overlay is 8.8 GB. When writes to a full
   disk stall the guest's I/O, `system_server` hangs long enough to trigger
   the watchdog. `Pixel_6` also has only 1536 MB of RAM with 4 cores on
   API 34. It was not relaunched in this repair.

## Fix applied (outside the repo, on this PC)

- `OC_API35` `config.ini`: `hw.ramSize=2048`, `hw.cpu.ncore=2`,
  `fastboot.forceColdBoot=yes`, `fastboot.forceFastBoot=no`. Older commands
  that leave out `-no-snapshot-load` now cold-boot within the PC's resource
  limit too. The original config is backed up in the session scratchpad.
- Deleted the old `snapshots/default_boot` snapshot (the 4 GB / 4-core
  state from 2026-09-26). The emulator recreates `ram.img` there as a
  sparse RAM-backing file on every launch. With `-no-snapshot-save` it logs
  "Not saving state: RAM not mapped as shared" and saves no snapshot.

## Still open

- `Pixel_6` was not re-verified. If it is needed, launch it with the same
  flags (`-memory 2048 -cores 2 -no-snapshot-load`) and only when the Storage
  disk has several GB free.
- The root disk that holds `OC_API35` has only about 9 GB free, and its
  userdata overlay is already 6.9 GB. Keep an eye on free space before long
  runs.
- When the host is under heavy load, adb can stop responding for up to about
  30 seconds. Use timeouts of 30 seconds or more on adb calls in proof
  scripts.
