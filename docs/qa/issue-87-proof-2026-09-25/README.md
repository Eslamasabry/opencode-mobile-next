# Issue #87 proof, part 1: the 1.0.44+50 baseline and a Termux emulator (2026-09-25)

This record prepares the proof that closes
[#87](https://github.com/Eslamasabry/opencode-mobile-next/issues/87)
(see `../issue-87-status-2026-09-24.md` for the four complaints). It holds two
things:

- **Phase 1:** what the reporter has today, 1.0.44+50, measured on the Android 15
  emulator. The candidate build must later be measured the same way, against the
  same server data.
- **Phase 2:** an Android 14 emulator with Termux set up the way the app's
  "OpenCode in Termux" path expects. The Termux AI Team path can then be tried
  without the owner's phone.

Nothing was built, pushed, uploaded or installed on a physical device.

## Scope

| Item | Where |
|---|---|
| Timings without OCTRACE (1.0.44+50 has none): screen recording on the device → change timeline at 10 fps | `tool/qa/issue87/frames.py`, `tool/qa/issue87/measure.sh` |
| Frame figures for a Flutter app: SurfaceFlinger TimeStats of the Flutter `SurfaceView` layer | `tool/qa/issue87/timestats.py` |
| Server-side share of "send → first token": OpenCode 1 `/event` logged with the PC's clock (lengths only, never text) | `tool/qa/issue87/sse_log.py` |
| A deterministic model, so the model's speed does not decide the numbers | `tool/qa/issue87/mock_llm.py` (OpenAI-compatible, 127.0.0.1 only) |
| The long chat used for the scroll scenario | `tool/qa/issue87/seed_long_chat.py` |
| Driving 1.0.44+50's UI (new conversation, typing) | `tool/qa/issue87/new_conversation_1044.sh`, `tool/qa/aiteam_builtin/ui.py` |
| Termux on an emulator, ready for the app | `tool/qa/issue87/termux_emulator_prep.sh` |

## Builds

| | |
|---|---|
| App under test | **1.0.44+50**, public GitHub release `v1.0.44+50` of `Eslamasabry/opencode-mobile-next` (published 2026-09-21) |
| Asset | `opencode-mobile-1.0.44+50.apk`, 203,332,920 bytes, universal (arm64-v8a, armeabi-v7a, x86_64) |
| SHA-256 | `0c0f8add1a37f3cc763ec2eb81708b580518c8d945b4833ea546cfeac9978c52` (matches the release's `SHA256SUMS` and GitHub's asset digest) |
| Signer | `CN=OpenCode Mobile Release, O=OpenCode Mobile Community`, cert SHA-256 `842284b27aa297fb74cf831779fd16498517e1bc2104451459fec2ea7ac11d1c`. This is **not** the CI signer named in AGENTS.md (`2D010C21…`), so a candidate APK cannot be installed over it. Uninstall first. |
| versionCode / target | 50 / targetSdk 36, minSdk 24 |
| OCTRACE | none: 0 `OCTRACE` lines in logcat (perf tracing `7dcee568` is not an ancestor of the tag) |
| Termux | `termux-app_v0.118.3+github-debug_x86_64.apk` (termux/termux-app release `v0.118.3`, 2025-05-22, the latest stable), 35,189,933 bytes, SHA-256 `3550e61f4d9eb49b712fd1bd9519dc37085a4d8eb597c57a340f0a64859b7144` (matches the release's `sha256sums`). Signer `CN=APK Signer, OU=Earth, O=Earth`, cert SHA-256 `b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1`. versionCode 1002, targetSdk 28, **debuggable** (GitHub builds are). |
| PC server | OpenCode **1.18.23** (`~/node_modules/opencode-linux-x64/bin/opencode`). The `opencode` on `PATH` (`~/.bun/bin/opencode`) is broken: its postinstall never ran. Run `opencode serve --port 4096 --hostname 127.0.0.1` with isolated `XDG_DATA_HOME` / `XDG_STATE_HOME` / `XDG_CACHE_HOME` and the PC user's global config. Reach it from the emulator with `adb reverse tcp:4096 tcp:4096` only. |
| Models | Real: `opencode/nemotron-3.5-lightning-free` (a free model, no key). Deterministic: `mock/stream` from `mock_llm.py`, used in project `proj3` only. |

Local copies, not in git: `/home/owner/Storage/tmp/issue87-fixture/` has both APKs and
`pc-server-fixture-2026-09-25.tgz`. That archive holds the server's data plus
`proj`, `proj2` and `proj3`, with absolute paths, so extract it with `tar -C / -xzf`;
`mcp-auth.json` is left out.

## Devices

| | emulator-5556 | emulator-5554 |
|---|---|---|
| AVD | `OC_API35` | `Pixel_6` |
| Android | 15 (API 35), `sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925` | 14 (API 34), `…:14/UE1A.230829.050/12077443` |
| ABI / RAM | x86_64 / 4 GB | x86_64 / 2 GB, `/data` 4.2 GB free |
| GPU | `swiftshader_indirect` (software), headless | same |
| Before | `io.github.eslamasabry.opencode_mobile` versionCode **4050** (1.0.44, an x86_64 split build of the branch, installed 2026-09-24 21:36, 1.8 GB of test data from the AI Team Run 3). **Uninstalled**. | No Termux. `ai.opencode.opencode_mobile` **1.0.24** (the old app id, installed 2026-08-28) is present and was left alone. Our app is not installed. |
| After | 1.0.44+50 with a saved PC server profile (`http://127.0.0.1:4096`) and projects `proj`, `proj2`, `proj3` | Termux 0.118.3, bootstrap unpacked, `allow-external-apps=true` |

PC: 8 cores, 15 GB. Only one emulator ran at a time, started when ≥5 GB was
available. The host load average was 7–10 throughout because other agents were
running, so **all absolute numbers are slow**. Only the comparison with the
candidate on the same emulator, fixture and method means anything.

## Phase 1: the 1.0.44+50 baseline (Android 15, emulator-5556)

### First run

| # | Step | Actual | Result |
|---|---|---|---|
| 1 | Uninstall the old build, `adb install` 1.0.44+50, open | Welcome: "Where does your coding agent run?" On my computer / On this phone / Just show me (`p1-00-first-run.png`); `Displayed +1s638ms` | PASS |
| 2 | "On this phone" | **Needs Termux**: "Step 1 of 3, to do. Get Termux: Install the current F-Droid build of Termux" (`p1-01-on-this-phone.png`). On this emulator 1.0.44+50 has no on-device path. | as expected for 1.0.44 |
| 3 | On my computer → OpenCode → Server URL `http://127.0.0.1:4096` → Save & connect | Straight into a new conversation in `proj`, with the model chip "Nemotron 3.5 Lightning Free" (`p1-02-connect-form.png`) | PASS |
| 4 | Projects → Open a project folder → `…/proj2`, then `…/proj3` | Both open; the Work tab shows the other projects as chips | PASS |

Server fixture: `proj` (the long chat, 8 answered turns plus 1 aborted, ~30,000
characters, made with `seed_long_chat.py`; the send runs), `proj2` (two empty
conversations), `proj3` (the mock model).

### Method

- **Screen timings** (`measure.sh DEVICE OUT cold|project|settings|send RUN`):
  - `adb shell screenrecord` records the run.
  - `frames.py` samples the video at 10 fps. It compares each frame with the one
    before, with the status bar masked out, and a frame "changed" when the mean
    difference is over 0.5/255.
  - `settled` is the last change before 2 s of stillness. Every key frame was
    also checked by eye (the strips below).
  - Resolution is 0.1 s. The start is the first visible change (the tap or
    launch), so adb's own delay is left out.
- **Cold start** also records `am start -W` TotalTime, which is the logcat
  `Displayed` time.
- **Send → first token:**
  - On screen: the tap is the first change; the token is the first frame
    showing the reply (by eye).
  - On the server: `sse_log.py` gives the time from the user message event to
    the first reasoning or text event.
  - The two clocks are only aligned to about ±0.3 s.
- **Frames:** `dumpsys gfxinfo` **cannot measure this app**.
  - Flutter draws into its own `SurfaceView`, so HWUI's gfxinfo counts no Flutter
    frames: "Total frames rendered: 0" while scrolling and 2 while streaming,
    each run.
  - Instead, `dumpsys SurfaceFlinger --timestats` is enabled and cleared before
    the window and dumped after it. `timestats.py` reads the Flutter layer's
    present-to-present histogram: frames, average fps, p50/p90/p99 of the frame
    interval (histogram bucket floors), and the share of intervals over 17 ms
    (missed one 60 Hz vsync) and over 34 ms (missed two).
  - Idle pauses also count as long intervals, so each window keeps the screen
    moving.
  - **The `gfxinfo` table in `../motion-app-2026-09-25/README.md` needs this
    replacement too.**

### Results (3 repetitions each; full rows in `baseline.csv`)

| Scenario | Run 1 | Run 2 | Run 3 | Median |
|---|---|---|---|---|
| Cold start: `Displayed` (am start) | 1542 ms | 1315 ms | 1383 ms | **1383 ms** |
| Cold start: "Connecting to 127.0.0.1" card | 1.5 s | 1.3 s | 1.3 s | 1.3 s |
| Cold start: Work tab first frame (shell, "Reconnecting…", loading) | 3.2 s | 3.0 s | 3.1 s | **3.1 s** |
| Cold start: Work tab loaded (list shown, still) | 3.4 s | 3.2 s | 3.4 s | **3.4 s** |
| Open a project (tap the other project's chip → its Work tab loaded; each switch shows "Reconnecting…" first) | 1.1 s | 1.6 s | 1.7 s | **1.6 s** |
| Open Settings (Settings tab → still) | 0.2 s | 0.1 s | 0.2 s | **≤0.2 s** |
| Send "Reply with the word ok", free model: first token on screen (its reasoning) | 1.9 s | 2.0 s | 3.7 s | **2.0 s** |
| Same: "ok" on screen | 2.8 s | 2.3 s | 4.0 s | **2.8 s** |
| Same, server share: user message → first reasoning / → text | 1.73 / 2.44 s | 1.50 / 2.49 s | 3.17 / 3.62 s | 1.73 / 2.49 s |
| Send, mock model (300 ms to first token): "ok" on screen | 1.5 s | 1.5 s | 1.7 s | **1.5 s** |
| Same, server share: user message → text | 1.18 s | 1.03 s | 0.92 s | 1.03 s |

Send runs 2 and 3 on the free model went into the same conversation as run 1
(turns 2 and 3), because the helper tapped a conversation row titled "New
conversation". This was fixed afterwards in `new_conversation_1044.sh`. The mock runs
are each a new conversation.

| Frames (Flutter layer, TimeStats) | Run | frames | avg fps | p50 | p90 | p99 | >17 ms | >34 ms |
|---|---|---|---|---|---|---|---|---|
| A long reply streams (mock, 3,501 chars as 5-word chunks every 150 ms; window = the text part appears → idle, ~20.4 s) | 1 | 699 | 34.7 | 29 ms | 44 ms | 58 ms | 82.3 % | 34.2 % |
| | 2 | 781 | 38.9 | 26 ms | 40 ms | 48 ms | 76.8 % | 24.2 % |
| | 3 | 846 | 42.3 | 22 ms | 38 ms | 44 ms | 69.6 % | 22.1 % |
| | **median** | 781 | 38.9 | **26 ms** | **40 ms** | **48 ms** | **76.8 %** | **24.2 %** |
| Scrolling the long chat (10 flings up, 10 down) | 1 | 416 | 35.7 | 28 ms | 40 ms | 58 ms | 85.3 % | 27.2 % |
| | 2 | 506 | 45.2 | 21 ms | 36 ms | 44 ms | 69.8 % | 11.9 % |
| | 3 | 479 | 41.8 | 23 ms | 36 ms | 50 ms | 76.6 % | 14.6 % |
| | **median** | 479 | 41.8 | **23 ms** | **36 ms** | **50 ms** | **76.6 %** | **14.6 %** |

Supplementary runs (in `p1-runs/`, not in the medians):
- `mockfast-gfx-stream-*`: the mock at 40 ms per chunk, a 5.8 s window. p50 25–36 ms, p90 40–58 ms.
- `gfxwait-stream-*`: the free model with the window from Send to idle, so it includes the model's wait spinner. p50 30–38 ms, p90 46–58 ms, and p99 up to 350 ms because the idle cursor blink is counted.
- Three more free-model streaming attempts were thrown away. The free model sometimes queued 40–190 s, and once delivered a 2,815-character reply in 0.27 s. That is why the mock exists.

### What the baseline says

- **On a fast local server, 1.0.44+50 is not the 20-second app of the report.**
  - Cold start to a usable Work tab is ~3.4 s.
  - Switching projects takes ~1.6 s.
  - Settings opens at once.
  - Its share of "send → first token" is at most ~0.5 s: the rest is the model.
- **The reporter's 20 s must come from the server side.** The owner's phone had
  its server out of memory (see the status file). The candidate must be
  compared both on this PC fixture and against its **own in-app server**,
  which is the reporter's configuration.
- **The Flutter layer runs at 35–45 fps on this software-GPU emulator** while
  streaming or scrolling:
  - p90 ≈ 36–40 ms;
  - three-quarters of the intervals miss a 60 Hz vsync.
  - This is the "before" for complaint 2. Absolute values mean nothing for a
    phone GPU.

Evidence:
- `p1-runs/<scenario>-<n>/`: `summary.txt`, `timeline.csv`, `am_start.txt`, `sse.tsv` (times, types, ids and lengths, no text), `timestats.txt`, host times.
- Strips:
  - `p1-cold-2-strip.png` (home, splash, blank, connecting ×2, Work shell, Work loaded);
  - `p1-project-3-strip.png` (reconnecting, loading, loaded);
  - `p1-settings-1-strip.png`;
  - `p1-send-1-strip.png` and `p1-send-2-3-strip.png` (first reasoning, then "ok");
  - `p1-mock-send-strips.png`;
  - `p1-long-chat-scrolled.png`.
- The videos (0.5–1 MB each, 10 fps frames) stayed local and were not published.

## Phase 2: Termux on the Android 14 emulator (emulator-5554)

What the app checks, from `MainActivity.capabilities()` and `lib/termux/bridge.dart`:

| Check | Meaning |
|---|---|
| `installed` | the `com.termux` package is present |
| `protocolSupported` | the versionName is 0.109 or later |
| `serviceAvailable` | `com.termux.app.RunCommandService` exists |
| `permissionGranted` | **our** app holds `com.termux.permission.RUN_COMMAND` |

RUN_COMMAND also needs `allow-external-apps=true` in `~/.termux/termux.properties`.
That is the same line `TermuxBridge.unlockCommand` writes.

`termux_emulator_prep.sh emulator-5554 <apk_dir>` (it refuses any serial that is
not `emulator-NNNN`):

1. Downloads the x86_64 APK from termux/termux-app's GitHub release and checks it against the release's `sha256sums`.
2. Installs it.
3. Opens Termux once, so its bootstrap unpacks. This needed no network and took about 30 s.
4. Writes `allow-external-apps=true` with `run-as com.termux`. This works because the GitHub build is debuggable. For a non-debuggable F-Droid build, the script types the same command into the Termux window with `adb shell input text` instead; that branch was not exercised.
5. Force-stops Termux and reopens it, so the property is read, then reports the state.

| # | Step | Actual | Result |
|---|---|---|---|
| 1 | Run the script (first time) | Install, bootstrap, property, restart in 32 s | PASS |
| 2 | Run it again (idempotent) | "Termux already installed", checksum OK, the property not duplicated | PASS |
| 3 | State | versionName 0.118.3 (so `protocolSupported`); debuggable; `com.termux/.app.RunCommandService` exported with action `com.termux.RUN_COMMAND`, guarded by `com.termux.permission.RUN_COMMAND` (so `serviceAvailable`); `termux.properties` = `allow-external-apps=true`; 390 programs in `usr/bin`; the Termux prompt ready (`p2-termux-ready.png`) | PASS |
| 4 | Stop | `adb emu kill`; the data stays in the AVD (no snapshot needed) | done |

Deliberately **not** done, because the app's own flow must prove it:
- installing our app;
- granting RUN_COMMAND;
- `pkg update` or installing OpenCode in Termux;
- battery-optimisation exemption.

## Next phase: the integrated run on the candidate APK

The same method on the same emulators. The candidate needs its own x86_64 APK:
- build it only with the pinned Flutter, when builds are allowed again;
- record its SHA-256;
- it has a different signer, so uninstall 1.0.44+50 first.

**emulator-5556 (OC_API35, Android 15): before/after and the built-in path**

1. Restore the fixture: `tar -C / -xzf /home/owner/Storage/tmp/issue87-fixture/pc-server-fixture-2026-09-25.tgz`. Then start the server and the mock with the environment in "How to reproduce", and `adb -s emulator-5556 reverse tcp:4096 tcp:4096`.
2. `adb uninstall` 1.0.44+50 and install the candidate.
   - Welcome → On my computer → `http://127.0.0.1:4096`.
   - Open `proj2` and `proj3` as in Phase 1.
3. Run the identical scenarios, 3 times each, into a new OUT folder:
   - `cold`, `project` (chip → other project), `settings`;
   - `send` on the free model in `proj`, each in a **new** conversation;
   - `send` and `gfx-stream` on the mock in `proj3` (`PROJECT_DIR=…/proj3`, the mock with `CHUNK_MS=150`);
   - `gfx-scroll` in "Long chat for scrolling".
   - The candidate's Work tab and chat were redesigned, so `new_conversation_1044.sh` coordinates and chip positions must be checked with `ui.py list` first.
   - Also capture `adb -s emulator-5556 logcat -s flutter | grep OCTRACE` for every run: `app.first_frame`, `app.shell_frame`, `app.first_connected`, `connect`, `location.select`, `sessions.refresh`, `prompt.sent` / `prompt.accepted` / `prompt.first_token`.
   - Compare the screen-method numbers with the table above: same method, same fixture. The OCTRACE figures are extra; the old build has none.
4. The reporter's configuration: Settings → On this phone → the built-in setup (fresh), with setup timings from OCTRACE.
   - Then the same `cold`, `project`, `settings`, `send` and `gfx-stream` scenarios against the **in-app server**.
   - Ask the agent "where are you running?".
   - AI Team: turn it on and give one task, with `procwatch.py` sampling.
   - Record a video for the audit.
5. Stop everything by exact PID, remove the reverse, `adb emu kill`.

**emulator-5554 (Pixel_6, Android 14): the Termux path**

1. Start it. Termux is already ready. Re-running `termux_emulator_prep.sh` is harmless and prints the state.
2. Install the candidate. Optionally remove the stray `ai.opencode.opencode_mobile` 1.0.24 first, and record whether you did.
3. In the app, take the Termux path (Settings → On this phone → OpenCode in Termux, or wherever the candidate now puts it).
   - Grant RUN_COMMAND through the app's own permission dialog, not `pm grant`.
   - Let it install and start OpenCode in Termux, then connect.
   - Record each step's time and any Termux battery or notification prompts.
4. The Termux AI Team: try to add it.
   - **Expected to fail at the download** while the GitHub pre-release `aiteam-assets-1` does not exist (`9045386b`).
   - Record the exact message. The pass condition depends on the owner's decision: publish the assets, or send Termux users to the built-in setup.
5. The same `send` timing against the Termux server, then stop the emulator.

## How to reproduce

```bash
S=<work dir>   # the baseline used the session scratchpad …/scratchpad/i87
T=/home/owner/Storage/Code/oc_app/tool/qa/issue87
ADB=~/Android/Sdk/platform-tools/adb
# Release APK
gh release download -R Eslamasabry/opencode-mobile-next 'v1.0.44+50' -D $S
# Android 15 emulator (one emulator at a time, >= 5 GB free)
TMPDIR=/tmp/emu-tmp ~/Android/Sdk/emulator/emulator -avd OC_API35 -port 5556 -no-window \
  -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect &
$ADB -s emulator-5556 uninstall io.github.eslamasabry.opencode_mobile
$ADB -s emulator-5556 install $S/opencode-mobile-1.0.44+50.apk
# Server (localhost only), isolated data; project proj holds opencode.json {"model": "opencode/nemotron-3.5-lightning-free"}
cd $S/proj && XDG_DATA_HOME=$S/ocdata XDG_STATE_HOME=$S/ocstate XDG_CACHE_HOME=$S/occache \
  ~/node_modules/opencode-linux-x64/bin/opencode serve --port 4096 --hostname 127.0.0.1 &
CHUNK_MS=150 python3 $T/mock_llm.py 4199 &     # proj3/opencode.json: see mock_llm.py's docstring
$ADB -s emulator-5556 reverse tcp:4096 tcp:4096
python3 $T/seed_long_chat.py $S/proj 8         # the long chat (slow on a free model)
# Scenarios (Work tab showing for cold/project/settings)
for r in 1 2 3; do $T/measure.sh emulator-5556 $S/runs cold $r; done
CHIP_XY="145 532" $T/measure.sh emulator-5556 $S/runs project 1
$T/measure.sh emulator-5556 $S/runs settings 1
$T/new_conversation_1044.sh "Reply%swith%sthe%sword%sok"; SECS=60 $T/measure.sh emulator-5556 $S/runs send 1
HIDEKB=1 $T/new_conversation_1044.sh "Write%sa%s600-word%sstory%sabout%sa%slighthouse%skeeper,%sin%splain%sparagraphs."
PROJECT_DIR=$S/proj3 SECS=90 $T/measure.sh emulator-5556 $S/runs-mock gfx-stream 1
# (open "Long chat for scrolling" first)
$T/measure.sh emulator-5556 $S/runs gfx-scroll 1
# Stop: kill the server and mock by their exact PIDs; reverse --remove; emu kill
# Termux emulator
TMPDIR=/tmp/emu-tmp ~/Android/Sdk/emulator/emulator -avd Pixel_6 -port 5554 -no-window \
  -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect &
$T/termux_emulator_prep.sh emulator-5554 $S
$ADB -s emulator-5554 emu kill
```

## NOT proven

- **Anything about the candidate build.** This is the "before" only.
- **Any phone.** Two x86_64 emulators with a software GPU, on a PC loaded by other agents. Absolute frame times and fps are not what a phone GPU shows, and only a same-emulator comparison is meaningful.
- **The reporter's own slowness.**
  - Every Phase 1 number is against a fast server on the same PC, through `adb reverse`, with a small fixture (3 projects, 3–15 conversations each). 1.0.44+50 against a busy or remote server, or a large history, was not measured.
  - The reporter's case is on-device: 1.0.44 needs Termux for that, and it was not tried here.
- **1.0.44+50's Termux path** was not run. Phase 1's emulator has no Termux, and Phase 2's emulator has no app yet.
- **gfxinfo janky %**, as the task first asked, cannot be measured for this app (0 or 2 frames). The TimeStats figures replace it, and they are a different metric: present-to-present intervals, not HWUI frame deadlines.
- **The screen-based send timing** reads the first reasoning or "ok" frame by eye, at 0.1 s resolution. The split between the app's share and the server's share relies on two clocks aligned to about ±0.3 s.
- **The Termux emulator**:
  - Only the prep is proven. RUN_COMMAND from our app, `pkg` over the network and OpenCode in Termux have not run.
  - The F-Droid (non-debuggable) branch of the script, which types the command into the Termux window, was never executed.
  - The GitHub Termux build is signed differently from F-Droid's. It cannot be mixed with F-Droid Termux plugins; our app does not check the signer.
