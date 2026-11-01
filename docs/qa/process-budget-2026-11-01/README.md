# Process budget: evidence (2026-11-01)

Branch `feat/process-budget-2026-11-01` (from `patch/2.1.0-p1`). Android 12+
stops all of an app's child programs past 32 ("phantom processes"); on the
owner's phone that took down Paseo and every Claude Code session (helper exit
137) while 5 agents and a build ran.

## Baseline: what runs at idle

Emulator-5584 (Android 15, API 35), APK 4373 (before), app idle on the
conversation list:

| | before (4373) | after (4419) |
|---|---|---|
| Idle children (all but the app) | 10 ([idle-before-4373.txt](idle-before-4373.txt)) | 9 ([after_idle.txt](after_idle.txt)); the one fewer is an `oc-ui` helper for a project not opened since restart, not this change |
| List refreshes (New conversation, x3) | each refresh starts a sign-in check: `libproot.so`, `sh`, `python3` and `claude`/`kilo` (`python3` seen 2x, `claude` 1x, `sh` 3x in 80 samples; [before_refresh.txt](before_refresh.txt)) | one check after start-up, then none: 0 `python3`, 0 `claude`, 0 `sh` in 89 samples ([after_refresh.txt](after_refresh.txt)) |
| Usage collector | not set up on this emulator; on the owner's phone it is a `libproot.so` + `node` pair running all day | starts on the first usage read, stops 2 min after the last (unit test `process_baseline_test` fails on the old start-up) |

So on the owner's phone's idle set of about 14: 2 fewer all the time (the
collector) and 4 fewer on every list refresh (the sign-in check), 6 in all.

Idle set on the emulator: OpenCode (proot, opencode2, two `oc-ui` card helpers,
one per open project), Paseo (proot, CLI node, Supervisor, Daemon, terminal
worker, esbuild).

Not cut, and why:
- The `oc-ui` helper per open OpenCode project: OpenCode 2.1.x answers 404 to
  `POST /api/mcp/oc-ui/disconnect` and to `PUT /api/mcp/oc-ui` (checked on the
  emulator), so it cannot be stopped when idle without restarting OpenCode.
- Paseo's CLI wrapper (`paseo daemon run`, ~115 MB) could be skipped by running
  its supervisor entrypoint directly; that is native (PhoneAgentHost.kt) and
  needs a release. Paseo's terminal worker and esbuild service start with its
  daemon.

## Budget guard, kill line and card: device proof (emulator-5584, API 35, APK 4419)

Recorded in `/home/eslam/Storage2/wt/shots-2026-11-01/procbudget/` (raw clips
`raw-a-queue.mp4`, `raw-b-kill.mp4`, `raw-c-card.mp4`; edited
`procbudget.mp4`).

1. The terminal starts 16 `sleep 600` (27 children). A new Kilo conversation
   then shows "Waiting for room: the phone allows about 32 programs at once.
   See what runs" and starts nothing; Running on this phone says "27 of them
   count toward the limit of about 32". Killing the 16 sleeps: the line goes
   and the conversation starts by itself (Kilo then asks for a sign-in: this
   emulator has no Kilo account).
2. The agent helper's proot gets `kill -9` from adb (as Android's limit
   does): about 12 s later the conversation says "Android stopped the agent
   because too many programs were running. Starting it again…", then "…It's
   back; your conversation continues." with See what runs, which opens Running
   on this phone with the card "Let agents run more programs at once".
3. `Settings.Global` is readable by the app (targetSdk 36) on API 35:
   `development_settings_enabled=1` turns the card's button into "Open
   Developer options"; `settings_enable_monitor_phantom_procs=false` hides the
   card; both were deleted again afterwards (back to `null`). "Open About
   phone" opens `MyDeviceInfoActivity`.

Seen on the way, not from this branch: after the helper restart Kilo's turn
shows "It answered, but this app couldn't read the reply" (the unsigned-in
Kilo turn).
