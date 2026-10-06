# QA record: OpenCode inside the app (phone setup v2)

Audit record for running OpenCode inside the app with no Termux: the built-in Linux,
the reusable and resumable setup engine, and screens A–D. It lists what was tested,
on which build and device, and how to reproduce it, and it ends with what is still
NOT proven.

## Scope

| Area | Module | Contract / spec |
|---|---|---|
| Built-in Linux: proot as native libs, Ubuntu Base 24.04.5, process runner, server process, foreground service | `android/.../BuiltinLinux.kt`, `BuiltinServerService.kt`, `SetupRunner.kt`, `SetupService.kt`, `tool/builtin_linux/fetch_proot.sh` | `docs/design/built-in-linux-plan-2026-09-23.md` |
| Setup engine: components, `::oc` progress protocol, resume rule | `lib/builtin/setup/` | `docs/design/phone-setup-v2-2026-09-24.md`, `setup_contract.dart` |
| Screens A (start + Customize), B (progress), C (ready), D ("This phone" card), welcome entry, notification routes | `lib/ui/screens/phone_setup/`, `lib/ui/widgets/setup_progress_view.dart`, `lib/ui/widgets/phone_server_card.dart` | spec above |
| Project open no longer waits for the model catalog | `lib/state/connection.dart` (`_selectLocationUntraced`) | commit `3b16f306` |
| Performance tracing (`OCTRACE`) | `lib/diagnostics/perf_trace.dart` | merged `7dcee568` |

## Builds

| Run | Branch @ commit | APK (x86_64 for emulator, arm64 published) |
|---|---|---|
| R1–R3 | `feat/builtin-linux-spike` @ `bb3d551d` … `324830c3` | x86_64, emulator only |
| R4 (first video) | `feat/builtin-linux-spike` @ `196c9dc9` | arm64 `opencode-mobile-no-termux.apk`, later replaced |
| R5 (v2 video) | `feat/phone-setup-v2` @ `89e27507` | arm64 `opencode-mobile-no-termux.apk`, SHA-256 `3f812764…2855ddf` (at `http://100.101.102.103:8765/`, Tailscale only) |
| Branch head at the time of writing | `feat/phone-setup-v2` @ `ed85171f` | not yet rebuilt |

## Devices

- `emulator-5554`: AVD `Pixel_6`, Android 14 (API 34), x86_64, google_apis, headless. **No Termux installed.**
- `emulator-5556`: AVD `OC_API35`, Android 15 (API 35), x86_64. Booted 2026-09-24 for the AI Team proof; not used in R1–R5.
- The owner's phone (Android 15, arm64): **not used**. Nothing was installed on it.

## Runs

### R1: Spike: Ubuntu inside the app (2026-09-23)
1. Download and unpack Ubuntu Base 24.04.5 (SHA-256 pinned). **PASS**: 4.8 s.
2. `apt-get update`, then `apt install git curl ca-certificates` inside proot. **PASS**.
3. `git init` + commit, `curl https://example.com` → HTTP/2 200. **PASS**.

### R2: OpenCode server inside the app
1. `npm install -g opencode-ai@1.18.29`. It **failed** at first: npm under proot crashed with "Exit handler never called!".
   - The fix was `--foreground-scripts`, which the Termux path already used.
   - Re-run: **PASS**.
2. `opencode serve` on 127.0.0.1:4097:
   - health 200 `{"healthy":true,"version":"1.18.29"}` about 6 s after start;
   - `/session` in 98 ms;
   - a request without the password → 401.
   - **PASS**.
3. The app's UI connected. Big Pickle turn: "create hello.py, run it" → "Output: hello from the phone". **PASS**.

### R3: The server survives the app in the background
1. Start the server, press Home, wait 90 s. **PASS**:
   - health 200;
   - the process is `fg +50 … (fg-service-act)`;
   - the notification "OpenCode is running on this phone" is present.

### R4: First guided video (old four-step screen)
- A fresh install worked end to end. Measured: Install OpenCode took **621 s** with Ubuntu's npm package.
- That led to the fix: Node from its official pinned tarball, which took **237 s** in the next run.
- UX findings from this run that were then fixed:
  - the no-Termux option was buried;
  - a misleading "Download Ubuntu" button;
  - "0 B of storage";
  - a "Reconnecting" flash after creating a project.

### R5: Phone setup v2 end to end, with a forced interruption (2026-09-24)
Captions and timings are in `run-marks.tsv`; frames are in `guide-frames-resume-ready-leave.png`.

| # | Step | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | Uninstall app, install APK, open | Welcome | Welcome, three choices | PASS |
| 2 | On this phone | One screen, one primary button, real totals | "Run a coding agent right here · About 4 minutes and ~208 MB · Set up" (`screen-a-fresh.png`) | PASS |
| 3 | Set up | Real progress per component | Linux base ✓ 24.04.5; "Git and SSH · Installing packages · 15%" (apt status); "~3 min left" (`screen-b-progress.png`) | PASS |
| 4 | Force-stop at Node.js 15 of 58 MB | Job persisted | — | — |
| 5 | Reopen → On this phone | "Setup is NN% done — Continue" | "Setup is 46% done … Continue setup" | PASS |
| 6 | Continue | Finished parts skipped; download resumes | Linux, Git 2.43.0, Python 3.12.3 skipped with versions; Node at 39 of 58 MB after 6 s (partial file reused) | PASS |
| 7 | Wait | Ready screen | "OpenCode is ready" 121 s after Continue | PASS |
| 8 | Create "my-app" | Straight into a new conversation | New conversation, keyboard up. **Slow:** see R6 | PASS (slow) |
| 9 | Prompt: notes.py CLI with two notes | Python available; task done | "Edited 1 file, ran 2 commands"; output shown; no permission detour | PASS |
| 10 | Home → notification shade → back | Server keeps running | Notification shown; conversation intact | PASS |

Videos (Tailscale-only server):
- `phone-setup-v2-guide.mp4`: 3.8 min, captions, waits at 8×.
- `phone-setup-v2-full.mp4`: unedited.

### R6: Why "Create" was slow (timers on a throwaway build)
Opening a project on the in-app server, with the last project restored on app start:
- `sessions` 147 ms;
- `catalog` (OpenCode 1 `/provider`, about 6 MB) **7,694 ms**;
- `permissions` / `questions` **6,203 ms**, queued behind the catalog on the server's single thread.

The fix (commit `3b16f306`): the folder counts as open after sessions, permissions and
questions; the catalog loads afterwards; the models already shown stay.

The test `connection_sse_test.dart` › "opening another folder does not wait for the
catalog" fails without the fix and passes with it. **Not yet re-measured on the
emulator with the fixed build.** Do that with `OCTRACE` (below).

### Engine agent's own emulator proof (from its report, `feat/setup-engine` @ `34f18e5f`)
- Linux base download: 30,028,293 bytes with byte progress.
- Force-stopped at 23.1 of 58 MB of Node; Continue resumed at 47,648,768 bytes.
- Switching to OpenCode 2 worked.
- Cancel during npm stopped within 1 s, with no processes left.
- Measured on a fast network: Linux base 8.5 s, essentials 29 s, Python 17 s, OpenCode 26 s, start 2–7 s.
- Bug found and fixed: a force-killed proot left OpenCode running and holding port 4097. Stop now kills the whole process tree.

### R7: Android 15 fresh setup, with AI Team (2026-09-24 afternoon)
Recorded as Run 2 of `../aiteam-builtin-2026-09-24/README.md`, which has the
steps and evidence.
- On `d16ee762`: the screen froze on "Unpacking Linux base" while the job
  finished. **FAIL.** The cause was a poll loop that one bad status read could
  end.
- Fixed in `b17c67c7`, with a test that fails without the fix.
- On `b17c67c7`: all seven parts in 364.7 s, then Ready. **PASS.**
- Create took 7.6 s, 6.8 s of it the one-time provider refresh. Fixed in
  `5afcfaee`, with a test that fails without it; not yet re-measured on a device.

## How to reproduce

```bash
# Build (pinned Flutter only)
F=~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
$F build apk --release --target-platform android-x64 --split-per-abi
# Android 14 emulator (Storage must have free space; ~/.android lives there)
TMPDIR=/tmp/emu-tmp ~/Android/Sdk/emulator/emulator -avd Pixel_6 -no-window -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect &
# Android 15 emulator (on the main disk)
TMPDIR=/tmp/emu35-tmp ~/Android/Sdk/emulator/emulator -avd OC_API35 -port 5556 -no-window -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect &
adb -s emulator-5554 uninstall io.github.eslamasabry.opencode_mobile   # clean slate
adb -s emulator-5554 install build/app/outputs/flutter-apk/app-x86_64-release.apk
# Timings of every step, from the same build
adb -s emulator-5554 logcat -s flutter | grep OCTRACE
```

In the app, Settings → App diagnostics → Performance shows the same spans, with Copy report.

## NOT proven

- **A real arm64 phone.** Every run above is on x86_64 emulators. The owner's phone has not been used.
- **Android 15 on a phone.** R7 ran setup on the Android 15 emulator only.
- **The Create timing after both fixes** (`3b16f306`, `5afcfaee`). Unit tests only. R7 measured 7.6 s before the second fix.
- **Behaviour under a slow or metered network.** Every download ran on a fast connection.
- **Long background runs** (more than 10 minutes) and Android's phantom-process limit with the OpenCode server alone.
