# AI Team inside the app (no Termux): emulator proof, 2026-09-24

Branch `feat/aiteam-component` (from `feat/phone-setup-v2` @ `ed85171f`).

## Scope

- AI Team (Gas City) as a reusable, resumable setup component of the in-app
  Ubuntu, added through the existing "Add tools" / Customize screens.
- The team run as a second long-running service owned by the app.
- The in-app server's one AI Team path: Settings › Plugins › "AI Team on
  this phone" → Add → Turn on for a project → Work tab Team card → give a
  task.
- End-to-end proof on an Android 15 emulator with one real task, counting
  processes against Android's 32 child-process limit.

## Build under test

| | |
|---|---|
| Proof build commit | `ef18cd99` (the first commit on the branch) |
| APK | `app-x86_64-release.apk`, 33.4 MB, SHA-256 `70cdf92db64bccd9f8c610f54023f2531b1d18b338a321a4bd13d73ebf544207` |
| Built with | pinned Flutter 3.47.1 (`~/.shorebird/bin/cache/flutter/91f8bd75…/bin/flutter build apk --release --target-platform android-x64 --split-per-abi`) |
| Follow-up commit | the commit after it: a push to the phone-side origin after a project is added, staggered order intervals, the discovery rule as a function, tests, these docs. Checked with unit tests and in the adb-shell proot replica, **not re-run in the app** (see "Not proven"). |

Programs downloaded (pinned in `lib/builtin/setup/aiteam_scripts.dart`, the
upstream projects' own releases):

| Program | Version | x86_64 archive SHA-256 | arm64 archive SHA-256 |
|---|---|---|---|
| Gas City `gc` | 1.4.1 | `8d8c8b51…d33e42` | `6620ef51…407e29` |
| Beads `bd` | 1.2.2 | `8140098a…d321e8` | `501f38a1…fd83a` |
| Dolt `dolt` | 2.3.3 | `4acd730a…a43d9d` | `850a880a…aed3` |

Gas City and Beads hashes come from their `checksums.txt`; Dolt ships none, so
its hash is the asset digest GitHub reports, checked against a download.

## Devices

| | emulator-5554 | emulator-5556 |
|---|---|---|
| Android | 14 (API 34), `sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050` | 15 (API 35), `sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043` |
| Kernel / RAM | Android 14 goldfish / 2 GB | `6.6.50-android15-8` / 4 GB |
| Phantom settings | `settings_enable_monitor_phantom_procs` = null (default: on) | same; `max_phantom_processes` = null (default 32) |
| Role | iteration, first team run with Gas City's defaults | **final proof**: fresh install, set up, turn on, task |

Both emulators ran at the same time on one PC (8 cores, 15 GB), so every
duration below is slow; a phone does not share its CPU with a second phone.

## Steps and results (Android 15, emulator-5556, final run)

| # | Step | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | `adb uninstall`, `adb install` the APK, open, "On this phone" | Screen A still promises "About 4 minutes and ~208 MB" | Same promise (`api35-run1-02-screen-a-promise.png`) | PASS |
| 2 | Customize → switch **AI Team** on → Done | Row "AI Team ~125 MB"; totals include it | "About 6 minutes · ~333 MB"; hero "Includes Git and SSH, Python, Node.js and AI Team" (`api35-run1-03-customize-aiteam-on.png`) | PASS |
| 3 | Set up | Every component with real progress; AI Team downloads as three stages with bytes | 09:56:42 → 10:04:12 (7.5 min). AI Team row: "Installing packages 97%", "Downloading AI Team · 3 of 3 · 28 of 44 MB", "Getting AI Team ready" (`api35-final-01..03`) | PASS |
| 4 | The install runs each program once (SIGSYS check) | `gc`, `bd`, `dolt` answer under proot on Android 15 | Component finished, so all three ran; no SIGSYS in logcat (`api35-logcat-kills-and-sigsys.txt`: 0 matches over 09:56–11:28) | PASS |
| 5 | Name the first project: `my-app` → Create | Project and conversation | Done | PASS |
| 6 | Settings › Plugins | "AI Team on this phone" with "Turn on AI Team for my-app" | As expected (`api35-final-04-turn-on-offer.png`) | PASS |
| 7 | Turn on AI Team for my-app | Stages, then "AI Team · Running · Works on my-app"; plugin row "On · This phone" | 10:05:34 → 10:14:45 (9.2 min): store 2.1 min, project 2.9 min (first try), start + register 1.2 min, waiting for health 2.9 min (`api35-setup-and-turn-on.log`, `api35-final-05-team-running.png`) | PASS |
| 8 | Work tab → AI Team card → Open → Start a run | Direct-task form (planner off, loopback controls) | "Give one task straight to the project's agent", project my-app (`api35-final-06..08`) | PASS |
| 9 | Task "Create hello.txt containing the word hi" → Send to an agent | Bead made and slung to the project's worker | Sent 10:15:27; "Waiting for an agent" | PASS |
| 10 | Let it run, sampling processes every 20–30 s | Real work to a commit, never past 32 processes, nothing killed | Working from 10:19:47; branch `polecat/ma-2xs` pushed; "Waiting for merge" 10:46:56; refinery fast-forwarded `8d4304a Add hello.txt greeting` (1 file, `hello.txt`, +1 line) into `master`, pushed it to the phone-side origin and closed the bead by 11:20 (`api35-final-09-waiting-for-merge.png`, `api35-refinery-transcript.txt`, `api35-final-bead.json`, `api35-team-state.json`) | PASS |
| 11 | Process count during steps 9–10 (65 min, 133 samples) | Under 32 | min 7, median 15, max **33** (twice, at order-patrol spikes, e.g. 10:51:13: sh×10, gc×9, bash×5, opencode×2, proot×2, dolt, cat, timeout); **no** `Killing PhantomProcess` in logcat (`api35-process-samples.log`) | PASS, with a thin margin (see below) |
| 12 | The finished run in the app | Shows as completed | Gas City's `/convoys` lists open convoys only, so the run leaves the list (Runs (0)) instead of moving to Completed (`api35-final-10-completed.png`). Existing gateway behaviour, not changed here | FAIL (pre-existing) |
| 13 | Plugins → Stop AI Team | The team and everything it started stop; OpenCode keeps running | "AI Team · Stopped"; the app's processes 3 (OpenCode's proot + `opencode` + the app), no strays (`api35-final-11-stopped.png`, `api35-stop-start.log`) | PASS |
| 14 | Start AI Team | The same team comes back | 11:33:10 → "AI Team · Running" by 11:37:20 (store reused, register, health), peak 24 processes, no kills (`api35-final-12-started-again.png`); stopped again afterwards | PASS |

First Android 15 attempt (`api35-run1-*`, same day, earlier build): setup
passed; "Turn on" failed at "Adding my-app": `bd init` → `pending ignored
schema migrations alter pre-existing dirty tables: child_counters`. The same
error hit `gc init` once on Android 14. Likely cause (read from the script, not
traced on the device): Gas City's bridge
(`gc-beads-bd.sh`) waits ~4.5 s for the new store's schema, times out under a
slow proot, re-runs `bd init --force` over a half-made database, and Beads
refuses. The store and project scripts now retry from clean (a new city, or
the project under a new bead prefix = a new database); the final run did not
need the retry.

## Run 2: fresh install with video (Android 15, emulator-5556, afternoon)

A second fresh install, recorded, on the merged branch. It includes the
follow-up commit above, so this is that commit's first run in the app.

| | |
|---|---|
| Build | `feat/phone-setup-v2` @ `b17c67c7` (merge of this branch, plus the setup poll fix below) |
| APK | `app-x86_64-release.apk`, SHA-256 `e16b85bbdcaed14a0c500f94cd1e4e4e7d7366e8b9185d58e1b5d2cc4c567552` |
| Device | emulator-5556, Android 15, x86_64; app uninstalled first; host load 11–15 (the emulator at ~790% CPU) |
| Video (Tailscale only) | `http://100.101.102.103:8765/aiteam-fresh-install-guide.mp4` (7.5 min, captions, waits at 8× and 40×) and `aiteam-fresh-install-full.mp4` (78 min unedited, 10 fps, from host screenshots about once a second) |

**The first attempt failed, and it was an app bug.** On `d16ee762` the
progress screen stayed on "Unpacking Linux base · 29 of 30 MB" for over 20
minutes. Meanwhile `setup.json` showed every component done. The app's own
step, "Start OpenCode", failed after 10 minutes with "The app did not finish
this step".
- **Cause:** a status read that threw or never answered ended the engine's
  poll loop for good. No one polled, so no one ran the app's step. The
  exception went only to the in-memory diagnostics.
- **Fix (`b17c67c7`):**
  - the poll catches the error, logs it and carries on;
  - a read on the polling path times out after 10 s;
  - uncaught async errors also reach logcat.
- **Test:** `setup_engine_test` › "polling survives a status read that fails
  or never answers". It fails without the fix.
- The fixed run below logged no poll faults, so what broke the one read is
  still unknown.

| # | Step | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | Install, open, "On this phone", Customize → AI Team → Done → Set up | Totals include AI Team | "About 6 minutes · ~333 MB"; "Includes Git and SSH, Python, Node.js and AI Team" | PASS |
| 2 | Setup with progress | Every part advances to Ready | Linux 13.1 s, Git and SSH 130.8 s, Python 61.3 s, Node.js 34.2 s, OpenCode 54.2 s, AI Team 58.8 s, Start 12.2 s; job **364.7 s** (`run2-octrace.txt`, `run2-01-ready.png`) | PASS |
| 3 | Create "my-app" | Straight into a new conversation | Done; the folder open took **7.6 s**, 6.8 s of it the one-time provider refresh (fixed afterwards in `5afcfaee`, not re-measured) | PASS (slow) |
| 4 | Settings › Plugins › Turn on AI Team for my-app | Running | 12:28:43 → 12:38:00 (**9.3 min**): getting ready ~3 min, adding my-app ~2.6 min, starting ~1.2 min, waiting for health ~2.5 min (`run2-02-team-running.png`) | PASS |
| 5 | Work → AI Team → Open → Start a run → "Create hello.py that prints Hello from the AI Team" → Send to an agent | Run listed | 12:39:16 "Task sent to an agent · Confirmed"; "Waiting for an agent" (`run2-03-task-sent.png`) | PASS |
| 6 | The team works | An agent takes it; a commit on a branch | Claimed 12:45 by `gastown__polecat`; `hello.py` written 12:56; branch `polecat/ma-7mr` pushed to the phone-side origin; "Waiting for merge" 13:09:52 (`run2-team-watch.log`, `run2-04-waiting-for-merge.png`) | PASS |
| 7 | The merge | Commit on master | Refinery merged by 13:33: `86759b9 feat: add hello.py greeting (ma-7mr)`, 1 file (+1), body "Verified with python3 hello.py" (`run2-origin-git-log.txt`) | PASS |
| 8 | The run in the app after the merge | Shows as completed | "Runs (0) · No runs yet" (same as step 12 above) | FAIL (pre-existing) |
| 9 | New conversation in my-app: "Pull the latest master from origin, then run python3 hello.py and show the git log" | The team's work reaches the project | "Pulled (fast-forwarded to 86759b9), python3 hello.py prints Hello from the AI Team", and the log (`run2-05-pulled-and-ran.png`) | PASS |
| 10 | Process count, 12:38–13:41 (every 20 s) | Under 32, nothing killed | min 7, median 16, max **37** at 13:10:35, during the hand-off to the refinery; 33 at 12:45 when the polecat started. **No** `Killing PhantomProcess` and no SIGSYS in logcat (`run2-process-samples.log`; this count includes the app's own process) | PASS, over the limit for moments |

Found on the way and fixed on the branch afterwards (not in this APK):
- Settings › "On this phone" still said "Run OpenCode here with Termux" and
  opened the old Termux screen. It now opens the phone setup (`dc5a7afb`).
- Opening a new folder waited for the one-time provider refresh (`5afcfaee`).
  The same refresh still runs inside the first connect: 7.5 s of the 12.2 s
  "Start OpenCode" step.

Also seen: the team's own agent conversations showed on the Work tab under
"In other projects", one titled with raw `<tool_call>` text. Fixed afterwards
on `fix/work-tab-team-sessions`: see `../work-tab-team-sessions-2026-09-24/`.

## Run 3 (WIP): merged work reaches the project; the process peak (Android 15, emulator-5556, evening)

Branch `fix/aiteam-runtime` (from `feat/phone-setup-v2` @ `59822303`). Two
problems left by Run 2, fixed at the source and proven in the app:

1. **The merge never reached the project folder.** The refinery merges into
   the phone-side origin (`/root/aiteam/origins/my-app.git`), and
   `/root/projects/my-app`, where the person's own conversations work, stayed
   behind until someone ran `git pull` (Run 2 step 9).
2. **The app's process count went past Android's phantom-process limit (32).**

### What changed

| Commit | What |
|---|---|
| `ecbe2f8d` | **The origin hook.** Every origin this app made gets a `post-receive` hook (`BuiltinTeam.originHook`), written by `rigScript` and again on every team start (`hooksScript`, from the projects in `.gc/site.toml`), so a team made by an older version gets it too; the project and team name sit in the origin's own git config (`oc-mobile.project`, `oc-mobile.rig`). When the branch the project has checked out is updated, it fetches and fast-forwards the project, and only then. It leaves the project alone and says why when the project has changes of its own to tracked files (`dirty`; the team's own bookkeeping, `.beads/` and Gas City's `.gitignore` lines, does not count, and git still refuses to overwrite any of it), has commits of its own (`diverged`), is not on a branch (`skipped`), or git refuses (`failed`). It never fails the push and always exits 0. Every outcome is one tab-separated line in `/root/aiteam/pull.log`. When the hook is (re)installed it brings in once whatever was merged before it existed. Settings › Plugins shows the newest outcome for the open project ("my-app has the team's latest work (…)" or why it was left as it is) and, after `dirty` or `failed`, a "Bring the team's work into my-app" button that runs the same script by hand (`BuiltinTeam.bringIn`). An origin of the project's own (GitHub etc.) is never touched. |
| `3ff9522d` | **Phone tuning, from a per-process record.** `dolt-health`, `nudge-on-route`, `cascade-nudge-on-blocker-close` and `nudge-mail-sweep` are left out; `beads-health`, `order-tracking-sweep`, `gate-sweep`, `orphan-sweep` and `reaper` are switched to `trigger = "manual"` and run one after another by the city's own `phone-upkeep` order (`gc order run …` every 2 min; orphan sweep every 10, reaper every 30); agents start as `exec opencode acp` (one process per agent instead of `sh` + `opencode`). An older team is brought up to this on its next start (`tuneScript`: its `[daemon]`/`[orders]` tables are rewritten, `[providers.opencode]` gets `acp_command`, nothing else is touched). New tool `tool/qa/aiteam_builtin/procwatch.py` keeps every process row of every sample. |
| `32276c37` | **Upkeep one run at a time.** Found in this run (see step 11): Gas City starts the next `phone-upkeep` on its cooldown even while the last one is still in a slow orphan sweep. The script now holds a `flock` and a second run exits at once. |

Why these orders: at the 37-process peak **before** the change, 18 of the
processes were orders and 11 of them `dolt-health` alone (a health report
nothing reads on a phone; the supervisor's own watchdog keeps Dolt up);
`nudge-on-route` fires on every bead update and `cascade-nudge-on-blocker-close`
on every close, i.e. exactly when the agents hand work over. A task given with
"Send to an agent" goes straight to the project's worker and the worker wakes
the merger itself, so those nudges add nothing on a phone
(`run3-before-peaks.txt`).

**One agent session at a time is not possible with Gas City 1.4.1's knobs.**
`[workspace] max_active_sessions` (and the rig-level one) cap pool sessions
only: the refinery is a named `on_demand` session and is materialised outside
the capped pool path (`cmd/gc/pool_desired_state.go`: named-session work is
skipped by the cap; `compute_awake_set.go`: `on_demand` sessions with assigned
work, which the refinery's own patrol wisp always is, stay awake). The polecat
wakes the refinery itself before it drains (`mol-polecat-work`, steps 7–8), and
in this run it lingered for ~20 min after the hand-off. So both agents do run
side by side; the fix makes room for that instead of pretending otherwise.

### Before: the Run 2 build, measured with a per-process record

Same emulator, the Run 2 install (build `b17c67c7`), a second task
("Create greet.txt containing the word hello", given through the team API
the app uses) while the refinery from Run 2 still patrolled. Samples every
10 s, 18:10–19:06 (`run3-before-process-samples.log`: time, total, children):

| | |
|---|---|
| Samples | 337; min 10, median 15, max **37** (24 samples at 29 or more) |
| At 37 (18:13:20) | app 1, OpenCode server 2, team proot + supervisor 2, Dolt + watchdog 2, **two agents** 4 (`sh -c opencode acp` + `opencode acp` each), the refinery's event watch 2, the polecat's work query 6, **orders 18**: `dolt-health` 11, `cascade-nudge-on-blocker-close` 4, `nudge-on-route` 3 (`run3-before-peaks.txt`) |
| At the merge | 31 at 18:56:30 (orders: cascade-nudge 4, nudge-on-route 4, sweeps 4, `dolt-health` starting its 10), then at **18:56:33 Android killed the team**: `Killing PhantomProcessRecord … libproot.so … gc … gc: Trimming phantom processes`; the supervisor got signal 9 in the middle of the refinery's merge (`run3-before-logcat-kills.txt`) |
| Earlier the same afternoon | 17:16:32 another `Killing PhantomProcessRecord` of a proot of this install, after Run 2 was recorded; the app then showed "Connection lost" |

So Run 2's "max 37, no kill" was luck: the same build's next task was killed.

### Build under test

| | |
|---|---|
| Steps 1–13 | `3ff9522d`, `app-x86_64-release.apk` 33.4 MB, SHA-256 `0455fc437a1ece5ffeaf32af0ce6f0aed2174cb239710475691c3992ecc2e67d` |
| Step 14 (not yet run) | `32276c37` (head of the branch), SHA-256 `a26acabd1900dd90b51fdb19613e2f25ce927407bbf3dae29c054fab21877574`, installed over the first (`adb install -r`) |
| Device | emulator-5556, Android 15 (API 35) x86_64, 4 GB; `settings_enable_monitor_phantom_procs` and `max_phantom_processes` unset (defaults: on, 32); rebooted before the run (the PC ran out of memory at 21:27 and took the emulators down) |
| Sampling | `procwatch.py sample emulator-5556 … 10` for the whole run: every 10 s, every process of the app's uid with PPID, elapsed time and arguments. "Total" includes the app's own process; Android's limit counts the others ("children") |

### Steps and results

| # | Step | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | Fresh install (`3ff9522d`), On this phone › Customize › AI Team › Done | Totals include AI Team | "About 6 minutes and ~333 MB"; "Includes Git and SSH, Python, Node.js and AI Team" (`run3-01-customized.png`) | PASS |
| 2 | Set up | Ready | 21:37:58 → 21:48:40, no failure (`run3-02-setup-done.png`). (An earlier attempt at 19:29 hit an Ubuntu mirror sync, "File has unexpected size … Mirror sync in progress?", which the app shows as "No internet connection"; not this change) | PASS |
| 3 | Create my-app, Settings › Plugins › Turn on AI Team for my-app | Running | 21:52:20 → 22:00:27 (8.1 min): getting ready 2.0, adding my-app 2.6, starting 1.2, waiting 2.3 min; "my-app has the team's latest work (ab92be3 …)" right away (`run3-03-turn-on-offer.png`, `run3-04-team-running.png`) | PASS |
| 4 | The team's config on the device | New tuning; exec'd agents; hook | `[providers.opencode]` has `acp_command = "exec opencode"`, 5 `trigger = "manual"` overrides, `orders/phone-upkeep.toml`; origin hook with `oc-mobile.project=/root/projects/my-app`; agents show as a single `opencode acp` process each (`run3-peaks.txt`) | PASS |
| 5 | Work › AI Team › Open › Start a run › "Create greet.txt containing the word hello" › Send to an agent | Sent | 22:11 "Task sent to an agent · Confirmed" (`run3-05..07`) | PASS |
| 6 | The work | Branch, hand-off | Claimed 22:15 (`ma-j19`), "Waiting for merge" 22:29:54 (`run3-08-waiting-for-merge.png`) | PASS |
| 7 | **(a) The merge reaches the project folder, no pull** | `/root/projects/my-app` at the merged commit | Refinery closed the bead by 22:54; hook at 18:51:47Z (22:51 local): `brought-in ee82da7 Add greet.txt containing the word hello`; project `git log` = `ee82da7`, `greet.txt` = `hello`; Plugins: "my-app has the team's latest work (ee82da7 …)" (`run3-10-brought-in.png`, `run3-device-pull-log-and-git.txt`) | PASS |
| 8 | Make the project dirty: `greet.txt` gets a second, uncommitted line (stands in for the person's own unsaved edit) | — | `M greet.txt` | — |
| 9 | Second task "Create two.txt containing the word two" from the app | Merged on the origin | Sent 22:56:59, hand-off 23:11, closed 23:16; origin master `b593e1a Add two.txt …` | PASS |
| 10 | **(b) A dirty project is left alone and the app says so** | Project not moved, edit kept, log + app say why | Hook 19:14:09Z: `dirty b593e1a greet.txt`; project still at `ee82da7`, `greet.txt` keeps the edit, no `two.txt`; Plugins: "The team's work (b593e1a) is not in my-app yet: my-app has changes of its own (greet.txt), so it was left as it is." with "Bring the team's work into my-app" (`run3-11-dirty-left-alone.png`) | PASS |
| 11 | Tap "Bring the team's work into my-app" while still dirty | Still left alone | Log `dirty b593e1a greet.txt` again (19:17:57Z), message unchanged | PASS |
| 12 | Processes, 21:37–23:19 (both tasks, both hand-offs, the refinery patrolling throughout task 2) | ≤ 28, no kill | 616 samples: min 1, median 10, max **25**, none at 29+; `Killing PhantomProcess` / SIGSYS in logcat: **0** (`run3-process-samples.log`, `run3-peaks.txt`, `run3-logcat-kills-and-sigsys.txt`) | PASS |
| 13 | What the 25 was | — | 22:14:32: app 1, OpenCode server 2, team proot + supervisor 2, Dolt + watchdog 2, two agents 2, the polecat's work query 6, **two phone-upkeep runs side by side** 9 (an orphan sweep outlasted the 2-min cooldown). Fixed in `32276c37` | found, fixed |
| 14 | **Not done yet (WIP)**: install `32276c37` over it, `git stash` the edit, tap "Bring the team's work into my-app" (expect `brought-in b593e1a`), `git stash pop`; a third task on `32276c37` sampled through its hand-off | | | TODO |

### Process samples, after

| | Before (Run 2 build, 18:10–19:06) | After (`3ff9522d`, 21:37–23:19) |
|---|---|---|
| Samples (every 10 s) | 337 | 616 |
| min / median / max | 10 / 15 / **37** | 1 / 10 / **25** |
| Samples at 29 or more | 24 | 0 |
| Phantom-process kills | 1 in the sampled task (18:56:33, mid-merge), 1 earlier (17:16:32) | 0 |

### An older install is brought up to date (upgrade path)

Before the fresh install, the new APK (`3ff9522d` minus the provider fix,
SHA-256 `06538775…d5fadd`) was installed over the Run 2 install whose team
Android had just killed mid-merge. On the app's next start: `city.toml` got
the new orders tuning, the `phone-upkeep` order was written, the origin got
the hook (`oc-mobile.project=/root/projects/my-app`), and the catch-up logged
`up-to-date 86759b9`. The refinery resumed the interrupted merge; at
15:24:13Z the hook logged `brought-in 5bbdbb6 feat: add greet.txt with hello
greeting (ma-b9d)` and `/root/projects/my-app` was at `5bbdbb6` with no pull
(`run3-upgrade-brought-in.png`). Peak over those 20 minutes: 23, no kill.
That build wrote `acp_command` as a `[[patches.provider]]`, which Gas City
1.4.1 ignores ("unknown field"), so agents still ran as `sh` + `opencode`;
`3ff9522d` writes it in `[providers.opencode]` instead, and step 5 checks it.

### How to reproduce

```sh
D=emulator-5556; Q=tool/qa/aiteam_builtin
# Build and install as in "How to reproduce" above; then, detached, for the whole run:
python3 $Q/procwatch.py sample $D 14400 10 run3-procs.log
# Setup with AI Team, create my-app, Plugins › Turn on AI Team for my-app (as above).
# Work › AI Team › Open › Start a run › Task "Create greet.txt containing the word hello" › Send to an agent
curl -s http://127.0.0.1:18473/v0/city/phone/bead/<id> | jq '{status,assignee}'   # until closed
# Inside the app's Ubuntu (as the app's uid):
cat /root/aiteam/pull.log; git -C /root/projects/my-app log --oneline -3
# Dirty tree: change a tracked file in /root/projects/my-app, give a second task, wait for the merge,
# then Settings › Plugins; put the change away (git stash), tap "Bring the team's work into my-app".
python3 $Q/procwatch.py summary run3-procs.log 3     # min/median/max + breakdown of the 3 tallest samples
python3 $Q/procwatch.py counts run3-procs.log > run3-process-samples.log
python3 $Q/procwatch.py kills $D
```

Tests for this run's code: `test/builtin_team_bring_in_test.dart` (the hook
and `rigScript` with real git: clean project fast-forwarded with bookkeeping
changes aside; dirty project left alone and logged; diverged project never
rewritten; a push to another branch does nothing; `rigScript` installs the
hook and a re-run puts it back and brings in what was merged meanwhile; an
origin of the project's own is left alone; the status script and the
Plugins text), `test/builtin_team_test.dart` › "phone tuning" (the orders,
the upkeep order, an older `city.toml` tuned once with everything else kept
and still valid TOML, upkeep one run at a time). The `rigScript` test fails
without the hook, the upkeep test fails without the lock (checked by
reverting each).

## Android 14 (emulator-5554): the phantom-process kill, before tuning

With Gas City's defaults (patrol every 30 s, 8 store probes at once, a
nudge-poll process per session, every maintenance order) the team ran at a
median of 23 and a **peak of 40** processes (`api34-process-samples.log`). At
09:38:28, 36 minutes in, Android killed the **OpenCode server's** proot, the
`opencode` server itself and the team's proot:

```
ActivityManager: Killing PhantomProcessRecord {… 9168:9076:libproot.so/u0a197}: Trimming phantom processes
ActivityManager: Killing PhantomProcessRecord {… 9171:9076:opencode/u0a197}: Trimming phantom processes
ActivityManager: Killing PhantomProcessRecord {… 11988:9076:libproot.so/u0a197}: Trimming phantom processes
```

The task had reached its branch, not the merge. This is the failure the owner
saw on his Android 15 phone after ~9 minutes. `BuiltinTeam.phoneTuning` came
out of it: patrol every 60 s, health orders every 2–3 min, one probe and one
session start at a time, nudges delivered in the supervisor, and the
maintenance orders a single phone project does not need left out (two of them
wake an AI "dog", a whole extra OpenCode session). Median fell from 23 to 15;
the peak from 40 to 33.

Also on Android 14: the Linux builds of `gc`, `bd` and `dolt` ran under the
app's proot, and the team did real work (polecat branch `polecat/ma-vov`)
until the kill. `gc register` blocks until the city is up (over 2 min under
proot); it is now cut at 60 s and left to the health check.

## Review must-fix items (docs/reviews/aiteam-builtin-review-2026-09-24.md)

| # | Item | Status |
|---|---|---|
| 1 | Opt-in, install only; screen A still "About 4 minutes and ~208 MB"; a test pins it | Done (step 1; `aiteam_component_test`) |
| 2 | No team start in the setup job; "Turn on AI Team for this project" afterwards | Done (Plugins section, steps 6–7) |
| 3 | A second long-lived managed process; one notification for both; Remove stops the team first | Done (named services; uninstall stops all; steps 13–14) |
| 4 | One working in-app path; Add opens Add tools with AI Team selected; discovery no longer calls a phone team "a computer" | Done for Plugins and discovery. The Termux onboarding and re-offer card stay Termux-only; the in-app path is the Plugins section |
| 5 | Loopback only, own port, allowed hosts; token or unix socket | Loopback, port 8472, allowed hosts: done. Token / socket: **not done** (Security below) |
| 6 | "Adding AI Team" for add jobs | Done (screen B title and notification). Already-installed rows are not folded into one line |
| 7 | Count processes; keep them low; explain the Developer options switch | Done: measured (40 → killed on Android 14; peak 33, no kill on Android 15), tuned, and the section explains "Disable child process restrictions" |

## Architecture

**Modules** (AI Team code lives in its own files; the engine, contract and
screens have no AI Team special case):

| File | What |
|---|---|
| `lib/builtin/setup/aiteam_scripts.dart` | Pins (versions, URLs, SHA-256, sizes per CPU), the component's check / install / remove scripts, the agents' `opencode` wrapper. `AITEAM_BASE_URL` dart-define = a local mirror for testing (same checksums). |
| `lib/builtin/setup/components.dart` | One registry entry `aiteam`: optional, `defaultOn: false`, depends on `essentials` + `opencode`, size per CPU. |
| `lib/builtin/team/builtin_team.dart` | The team per project: store (`gc init`, retried clean), project (`gc rig add` with a phone-side origin, retried), supervisor as a service, register, health; `phoneTuning`; the plugin config. |
| `lib/ui/widgets/builtin_team_section.dart` | Settings › Plugins "AI Team on this phone": Add (opens Add tools with AI Team switched on), Turn on for {project} with its stages, Running/Stopped with Start/Stop, the child-process note. |
| `BuiltinLinux.kt` / `BuiltinServerService.kt` / `MainActivity.kt` | **Generic** named services (see below). |

**Generic capabilities added** (usable by any future tool):

- *Named services*: `startService {name, script, port?, notice?}`,
  `stopService {name}`, `serviceLog {name, tailBytes}`, and `status.services`.
  Each service is its own proot owned by the app; the OpenCode server is the
  service `server` (its old methods still work). The foreground notification
  lives while any service runs, shows the newest service's text ("OpenCode and
  AI Team are running on this phone", localised), and its Stop stops all.
  Uninstall stops every service before deleting.
- *Add-tools label*: `SetupJobParams.adding(ids)` → `SetupProgress.adding`;
  screen B and the notification say "Adding AI Team" (or "Adding Python and AI
  Team") for an add job, and a continued job keeps it.

**Contracts touched**: the `builtin_linux` method channel (new methods above,
`services` in `status`); `SetupProgress` (+`adding`); `SetupJobParams`
(+`adding`/`addingIds`). No change to the job runner or setup.json schema.

**Processes and ports**: the supervisor listens on `127.0.0.1:8472` (Termux's
team uses 8372; both can exist). Dolt listens on a random loopback port chosen
by Gas City. Agents are `opencode acp` children of the supervisor, through
`/opt/aiteam/agent-bin/opencode` (first on the supervisor's PATH only), which
makes each agent's folder a git worktree of the project before starting
OpenCode.

**App wiring**: turning on writes the in-app profile's `OrchestrationConfig`
(gascity, `http://127.0.0.1:8472`, city `phone`, host mode phone, no front),
which gives the loopback controls (create task, give it to the project's
worker). When the app later starts the in-app OpenCode server, it also starts
the team's service if that profile has the team on. Discovery no longer probes
loopback ports for the in-app profile (a team there is Termux's, and it was
labelled "a computer").

**Security**:
- Loopback only, explicitly: `bind = "127.0.0.1"`, `allowed_hosts =
  ["127.0.0.1", "localhost"]`, rewritten on every start.
- Its own port, so it never attaches to Termux's team.
- Usage metrics of all three programs off (`DO_NOT_TRACK`,
  `GC_DISABLE_USAGE_METRICS`, `BD_DISABLE_METRICS`, `bd metrics off`, Dolt
  `metrics.disabled`).
- **No token and no unix socket.** Gas City 1.4.1's supervisor keeps its TCP
  port open (port 0 means the default). Its only write gate is ed25519-signed
  per-request grants, which its own `gc` CLI inside the agents does not mint.
  Dolt also listens on loopback without a password. So any app on the phone
  that talks to 127.0.0.1:8472 can create and assign work while the team runs.
  The Termux team has the same exposure. See "Not proven / open".

**Remove**: "This phone" ⋯ → Remove stops every service (team included), then
deletes Ubuntu with AI Team in it. `AiTeamScripts.removeScript` (programs +
`/root/aiteam` + `/root/.gc`, projects kept) exists, but no screen offers
"Remove AI Team" alone yet.

## How to reproduce

```sh
# Build (from the worktree; android/key.properties copied from the main checkout)
~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter pub get
~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter build apk \
  --release --target-platform android-x64 --split-per-abi
# Optional, for a local mirror of the three archives (flat folder, same names):
#   python3 -m http.server 8876 --bind 127.0.0.1   (in that folder, detached)
#   adb reverse tcp:8876 tcp:8876
#   … flutter build apk … --dart-define=AITEAM_BASE_URL=http://127.0.0.1:8876/aiteam/

D=emulator-5556; Q=tool/qa/aiteam_builtin
adb -s $D uninstall io.github.eslamasabry.opencode_mobile
adb -s $D install build/app/outputs/flutter-apk/app-x86_64-release.apk
adb -s $D shell settings get global settings_enable_monitor_phantom_procs   # null = default
python3 $Q/flow.py $D launch tap:"On this phone" tap:Customize tap:"AI Team ~" tap:Done tap:"Set up"
python3 $Q/monitor.py $D 540 10 setup.log "progress|Name your" "Name your first project"
python3 $Q/flow.py $D tap:Create wait:"New conversation" back   # then Back to the Work tab
python3 $Q/flow.py $D plugins tap:"Turn on AI Team for"
python3 $Q/monitor.py $D 540 10 turnon.log "Getting|Adding|Starting|Waiting|Running|could not" "Running|could not"
python3 $Q/flow.py $D back tap:"Work Tab" tap:Open tap:"Start a run"
adb -s $D shell input tap 540 830     # the Task field
python3 $Q/flow.py $D type:"Create hello.txt containing the word hi" tap:"Send to an agent"
python3 $Q/monitor.py $D 540 30 task.log "Create hello|Agents \(" "Merged|1 of 1 done"   # repeat
# The team's own view, from the PC:
adb -s $D forward tcp:18473 tcp:8472
curl -s http://127.0.0.1:18473/v0/city/phone/beads | jq '.items[] | {id,status,assignee,title}'
adb -s $D logcat -d | grep -E 'Killing PhantomProcess|SIGSYS|seccomp'
```

`tool/qa/aiteam_builtin/` holds the scripts: `ui.py` (labels and taps via
uiautomator), `flow.py` (the steps), `procs.py` (the app's process count),
`monitor.py` (samples + the kill/SIGSYS check). Coordinates assume the
emulators' 1080×2400 screen.

## Tests

- `test/aiteam_component_test.dart` — default selection and screen A totals
  without AI Team (208 MB, about 4 minutes); the component is optional,
  depends on OpenCode, install only; job order; per-CPU pins and checksums in
  the script; the mirror override keeps the checksums; the check asks for the
  pinned versions; "Adding AI Team" (params, continue, setup.json); the service
  channel calls; discovery skips the in-app server only.
- `test/builtin_team_test.dart` — every team script parses in dash and bash;
  loopback/port/allowed hosts; the lean team and argument checks; "installed"
  means every line of the component check passes (a `set -e` inside a subshell
  on the left of `&&` is ignored — the test fails on that version); the status
  script reads projects from a real `city.toml`; turn-on order against a fake
  channel (store → project → service → register → health), a failing step
  says which, a dying supervisor is reported, `ensureRunning`.
- `test/setup_scripts_test.dart` (existing) now also parses the AI Team
  scripts in dash and bash.

## Not proven / open

- **A real phone.** Nothing ran on arm64; the arm64 pins are checksummed but
  never executed. Android 15's seccomp on a vendor kernel may still differ from
  the emulator's: in Termux without proot the owner's phone killed these Go
  Linux builds with SIGSYS (`faccessat2`); under the app's proot on the
  Android 15 emulator they ran about 75 minutes without it. The install fails with a
  plain "Android stopped gc … (SIGSYS)" if it happens. The Android builds of
  `gc`/`bd` (with `/system`, `/apex`, `/linkerconfig` bound into proot) are the
  fallback the review lists; not implemented, because `dolt`'s Android build
  also needs Termux's ICU.
- **The 32-process margin.** Peak 33 twice with no kill on Android 15 in the
  first proof, and 37 for a moment in Run 2, also with no kill; 40 was killed
  on Android 14. Run 3: the Run 2 build *was* killed at its next merge
  (18:56:33); after the tuning the peak was 25 over two tasks with no kill.
  Still open: a third task on `32276c37` (upkeep lock) sampled through its
  hand-off, and a real phone. Only one agent at a time is not possible with
  Gas City 1.4.1's knobs (Run 3, "What changed"). Other apps' child processes (Termux, for one) count
  too. The staggered intervals in the follow-up commit aim lower but were not
  measured in the app. "Developer options › Disable child process
  restrictions" is explained in the section; the owner asked to test with the
  default, so it was not switched.
- **Restart after Android or a reboot stops the app**: `ensureRunning` on the
  OpenCode start is unit-tested, not exercised on a device (Stop and Start
  from the Plugins section were, steps 13–14).
- ~~The merge lands on the phone-side origin, not in the project folder.~~
  Fixed and proven in Run 3 (steps 7, 10). Open: the "Bring the team's work"
  button bringing work in after the tree is clean again (step 14), and a
  real phone.
- **Finished runs vanish** from the team home (step 12): fixed on
  `fix/aiteam-finished-runs`, see "Finished runs" below.
- **No supervisor token / unix socket** (Security above).
- **No wake lock**: a phone with the screen off may slow the team; not added.
- Remove AI Team alone (no screen), Arabic layout (strings added, not viewed),
  a second project on the same team (script tested in the replica only).
- The follow-up commit's script changes (push after adding a project,
  gate-sweep 1 min, beads-health 3 min) now ran in the app too (Run 2); the
  staggered intervals did not keep the peak under 32.
- The cause of the one status read that froze the first Run 2 attempt is
  unknown; the engine now survives it (Run 2, `b17c67c7`).

## Finished runs (branch `fix/aiteam-finished-runs`, 2026-09-24)

**Problem** (step 12, Run 2 step 8): once the hello.py task merged, the home
showed "Runs (0)" and "No runs yet", and Completed was empty. The gateway
built runs from `/runs` plus `/convoys`, and Gas City's `/convoys` lists
open convoys only.

**What Gas City 1.4.1 offers.** Checked against the live supervisor, not
guessed. `GET /health` reports `version 1.4.1`, build `58ef17e3`, and
`GET /openapi.json` returns the supervisor's own spec (700 kB):

- `GET /v0/city/{city}/convoys` takes `index`, `wait`, `cursor` and `limit`.
  It has no status filter, so it cannot list closed convoys.
- `GET /v0/city/{city}/beads` takes `status`, `type`, `label`, `assignee`,
  `rig`, `all`, `limit` (default 100, max 1000) and `cursor`. It returns
  the newest first (`created_at DESC`).
- `GET /v0/city/{city}/events` takes `type`, `actor`, `since` (a Go
  duration), `limit` and `cursor`. It returns the newest first.
- The `Bead` schema has `created_at` and `updated_at`, but no `closed_at`.

**Live reads.** All were read-only GETs against the Android 15 emulator
city `phone`, after
`adb -s emulator-5556 forward tcp:18474 tcp:8472`, as
`curl -s http://127.0.0.1:18474/v0/city/phone/...`. Nothing was posted,
tapped or installed, because another agent was using the emulator.

| Request | Answer |
|---|---|
| `GET /convoys` | `{"items":[],"total":0}`: the finished convoy is missing |
| `GET /beads?type=convoy` | empty: closed beads are hidden by default |
| `GET /beads?status=closed&type=convoy&limit=20` | one item: `ma-lqw`, titled `sling-ma-7mr`, `status: closed`, `close_reason: convoy autoclose: all children closed`, tracking `ma-7mr`, `created_at 08:39:16Z`, with no `updated_at` |
| `GET /events?type=convoy.closed&since=10080m&limit=20` | seq 546, `ts 2026-09-24T09:33:03.458Z`, `subject ma-lqw` |
| `GET /events?type=convoy.closed&since=1m&limit=20` | empty: `since` does filter |
| `GET /convoy/ma-lqw` | the convoy, plus child `ma-7mr` "Create hello.py that prints Hello from the AI Team", `closed`, `merge_result: merged`, `merged_sha: 86759b90…`, `merged_target: master`, with `progress 1/1` |
| `GET /convoy/ma-7mr` | 404 `convoy-not-found`: "bead ma-7mr is not a convoy" |
| `GET /beads?status=closed&limit=2` | `total: 628` closed beads after about 5 h. This is why the history is filtered by type and bounded |

**What the app does now.** The change is in the gateway and mappers only;
the UI reads the new fields from the provider-neutral run model.

- `GasCityGateway._runs()` also reads
  `/beads?status=closed&type=convoy&limit=20` and
  `/events?type=convoy.closed&since=10080m&limit=20`.
- `selectFinishedConvoys` keeps at most 20 convoys that finished within
  7 days. It dates each one by the `convoy.closed` time, else `updated_at`,
  else `created_at`. A convoy that is open again is shown as open.
- For each convoy it keeps, the gateway reads `GET /convoy/{id}` once and
  caches it, since a closed convoy does not change.
- Both history reads are optional. A host without them, or an error,
  leaves the open runs as they were.
- `OrchestrationRun` gains `finishedAt` and `merged`. `merged` is set when
  every tracked item reports `merge_result=merged`.
- A completed row now reads "Done · merged", with "Finished 5h ago" in its
  subtitle.
- The empty title (card and home) now reads "No recent runs.", because the
  list only covers recent history.

**Fixtures.** Saved in `test/fixtures/gascity/finished_runs/` from the reads
above. To keep them small and free of internals, long descriptions are cut
to their first line, session and chore metadata to 5 keys, and `/runs` and
`/beads` to 4 items. Every field the gateway reads is unchanged.
`test/support/gascity_recorded_city.dart` serves them on a loopback socket.

**Tests.**

- `test/team_finished_runs_test.dart` has 14 tests: the mapping, close
  times, window/limit/open selection, and the real gateway over the
  recorded city. They check the exact history queries, the detail cache, a
  host without the routes, and an unreadable detail.
- `test/team_home_test.dart`, "a run that finished and merged stays under
  Completed…", reads the recorded city through the real gateway and renders
  the home. It checks that there is no empty state, "Runs (1)", the
  Completed group, and under Completed the task title, "Done · merged" and
  "Batch · convoy · 1 of 1 done · Finished 5h ago".
- With the fix disabled (`_runs()` without the finished convoys), both the
  gateway test ("the merged task stays listed…") and the widget test fail.
  The widget test fails at the empty-state check.
- 750 tests pass across `test/team_*`, `l10n_coverage`, `builtin_team` and
  `aiteam_component`.

**Not proven.**

- **The UI on a device.** No APK was built or installed, and the
  emulator's screen was neither viewed nor tapped. The widget test renders
  the home in the test harness, not on Android.
- **The app's gateway against the live city.** The live reads were made
  with `curl`. The gateway only ran against the recorded copies.
- **Other outcomes.** A convoy closed without a merge, a cancelled one, one
  with several items, or an `mr`/`pr` merge strategy (shown as "Done", not
  "merged") are covered only by unit tests over edited recordings. None
  existed live.
- **A long-lived city.** More than 20 closed convoys, or pruned
  `convoy.closed` events (the gateway then falls back to `created_at`), are
  unit-tested only.
- **The run detail of a finished run.** Its work list came from the open
  `/beads` only, so the merged task was not listed under the run. Since
  the next commit, `work()` adds the tracked items of the recent finished
  convoys: the recorded city lists `ma-7mr` (Completed) under `ma-lqw`
  (`team_finished_runs_test` › "a finished run keeps its work", which
  fails without it). The Work tab was not viewed on a device.
