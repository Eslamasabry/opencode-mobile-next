# Claude Agent trial: evidence, 2026-11-01

**Slice.** Dart-only trial of OpenCode 2's built-in "Claude Agent" provider on this phone's server. It sits beside Claude Code through Paseo, which is untouched.
- Branch: `feat/claude-agent-trial-2026-11-01`, based on `patch/2.1.0-p1`.
- Commit: 51462fa01.

**Plan.** `/home/eslam/Storage2/wt/paseo-exit-plan-2026-11-01/`.

## What was proven, and how

| # | Claim | Evidence |
|---|---|---|
| 1 | OpenCode 2.2.5 (the patch-line pin) has the provider in both builds | `strings` on the pinned x64-baseline and arm64 binaries (sha256 `873856ac…` / `f8c5a820…`) finds `claude-agent`, `claude-login` and `OPENCODE_CLAUDE_PATH` |
| 2 | The integration is listed before it is connected, with method `claude-login` (type `external`). The provider shows no models until it is connected. | PC run of the pinned x64 binary (isolated XDG dirs, port 4731, stopped by PID): `GET /api/integration`, `GET /api/provider` |
| 3 | `POST /api/integration/claude-agent/connect/external {"methodID":"claude-login"}` (204) makes OpenCode run its own login check through `claude`. The models then appear in `GET /api/model` under `providerID: claude-agent`, with variants low…max. | Same run, with a stand-in `claude` (`tool/qa/claude_agent_fake/`) first on PATH |
| 4 | A full turn with a tool works: a session with model `claude-agent/opus` → Claude calls OpenCode's `shell` (`ls`) through the SDK's MCP bridge → OpenCode runs it → a text reply → session `idle / succeeded` | Same run: `GET /api/session/{id}/message` shows the tool part (`shell`, `completed`, output `README.md`) and the reply text |
| 5 | Signed out, OpenCode reports `needs_auth` on the connection. The app reads only that word. | Same run, stand-in answering signed out: the connection's `status` reads `{"status":"needs_auth", …}` |
| 6 | Connecting again creates a second connection, and only a credential switch re-runs the check. So the app connects once, and **Check again** connects anew and removes the old connection. | Same run: a second `connect/external` gave "Claude Agent 2"; `activate` on the active credential did not re-check; switching credentials did |
| 7 | The root script makes the launcher and the link, is idempotent, retargets, refuses a launcher that isn't its own, leaves a real `/root/.claude` alone, and a piped run gets `CLAUDE_CONFIG_DIR` | Sandbox run of the script text with path prefixes. Results: `not-installed`, `linked`, `linked` again, retarget, `blocked`, `linked-without-resume`, and the empty-folder case |
| 8 | Gate, setup order, states, picker row, Trial tag, sign-in notice, and nothing shown elsewhere | `test/claude_agent_trial_test.dart` (14) and `test/claude_agent_picker_test.dart` (4). Both pass on the VPS, along with `test/picker_signin_test.dart` (26 in all). With the capability wiring in `status.dart` reverted, the picker test fails 2 of 4. |
| 9 | APK 4420 builds and still has the plugin registrant. It is signed with the local release key `1DE5BF08…D60C`. | `tool/qa/vps_build_apk.sh 4420`, using `tool/qa/apk_has_plugins.sh` |

## Emulator proof, build 4460 (emulator-5584, 2026-11-01 04:30–04:44)

Videos and stills are in `/home/eslam/Storage2/wt/claude-agent-trial-2026-11-01-video/`:
- `ca1-needs-sign-in.mp4`
- `ca2-turn.mp4`
- `ca_*.png`

| # | Claim | Evidence |
|---|---|---|
| E1 | The phone server (OpenCode 2.2.5 under proot) offers `claude-agent`. The first integration list after a server start can miss it, because OpenCode loads its providers for a location on the first read. | Terminal: the list showed `claude-agent` only on a later read. The picker row appeared after a reopen. Fixed by one delayed re-read (`lateLoadDelay`, with its own test). |
| E2 | **Try Claude Agent** writes the launcher and the link to the shared agent home (`/root/.claude -> /home/oc/.oc-profiles/1793454775471416/claude`). OpenCode then probes the **real, signed-out Claude Code 2.1.294**, run as root through the launcher. The picker shows "Claude isn't signed in on this phone" with **Open Agents** and **Check again**. | `ca1-needs-sign-in.mp4`, `ca_top.png`. Claude's model list also appears, tagged Trial (`ca_after_try.png`): OpenCode lists models even while signed out. |
| E3 | Full turn with the stand-in: models "Opus/Sonnet/Haiku (test replay)" with Trial → new chat on Opus → the **shell permission card** → Allow once → `Shell, ls, Done` → a reply quoting the real `ls` output | `ca2-turn.mp4`. The launcher's last line was pointed at `tool/qa/claude_agent_fake`, and the re-check was done by connect plus remove over the API. |
| E4 | Survives an app restart and a server restart: after `am force-stop`, the app came back and restarted the phone server. A second message in the same chat went through permission, tool and reply. | `ca_after_restart.png` |
| E5 | Cleanup: the launcher was put back to the real Claude Code, and the stand-in and probe folders were removed. The home screen's "Claude Code signed out · Sign in" row is unchanged throughout. | Terminal output |

## Not proven yet

| What | Why | Who |
|---|---|---|
| Root-run `claude` reading the `oc` user's sign-in folder under proot, including a token refresh, while Paseo's Claude Code also runs | Needs the device. File ownership looks the same in both proot modes, but this is not verified. | Emulator (read part), owner's phone (refresh) |
| A real signed-in turn: Opus, a tool, a permission card | No Claude sign-in on the emulator | Owner's phone |
