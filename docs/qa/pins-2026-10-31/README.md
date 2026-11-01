# Pins 2026-10-31 (fix/pins-2026-10-31)

Owner rule: components move only to tested pins, delivered by app updates and
Shorebird patches, never by floating to "latest". Dart-only; no Kotlin, assets
or pubspec. Baseline int f46ec689e (= published 2.1.0+57). APK build 4300.

## Slice 2: OpenCode 2.2.5

- Pins: arm64 `f8c5a820...b053`, x64-baseline `873856ac...7a08` (SHA-512 matched
  the npm registry integrity). BYO bundle (`scripts/byo-host`) left on 2.1.1.
- `tool/qa/oc2_live_proof_test.dart` (build server, servers on 127.0.0.1): 46/46 on
  2.2.5 and 46/46 on 2.1.1. OpenAPI diff: 25 operations added (experimental/debug),
  none removed, only additive fields.
- Emulator (x86_64, build 4300 installed over 2.1.0 with `install -r`): This phone
  reads "Up to date . 2.2.5" (shot 01); a chat turn that asked to run `ls`
  showed the permission card (shot 02) and, after Allow once, finished with the
  answer (shot 03).
- Rollback, deeper: a conversation of 54 messages (13 turns, 14 tool parts, edits,
  shell, reads) was created on 2.2.5 with the free model `big-pickle`. Reopened
  on 2.1.1: the message JSON is byte-identical (28,743 bytes, same ids and order).
  A new turn on 2.1.1 worked (58 messages); reopened on 2.2.5 the first 54 are still
  byte-identical. Upgrade then downgrade then upgrade lost nothing.

## Slice 3: Claude Code 2.1.316

- Built-in catalog and `ClaudeScripts`: sha256 from the official manifest
  (arm64 `55cdb8e1...2624`, x64 `fd13c700...d824`), re-downloaded and hashed on
  the build server; `claude --version` = 2.1.316, `auth status` signed out.
  Binary is 328 MB (was 252 MB).
- Legacy Termux installer pinned (`claude_version` in the pins file, exact npm
  version, fails if another version lands). Test keeps it equal to the catalog.
- Emulator: Agents > Claude Code "Check" passed Installed / Version / Connection and
  stops at "Sign in needed" (shot 05). No signed-in turn: no account on the
  emulator or build server.
- Not proven: a signed-in Claude turn on 2.1.316.

## Slice 1: tested sets

- Claude Code: `{2.1.283, 2.1.294, 2.1.316}`.
- Paseo: `{0.9.2, 0.11.2, 0.12.0, 0.13.2, 0.14.0}`. Each daemon ran live on the
  build server with Claude Code 2.1.316 and the app's `PaseoGateway`
  (`pins_paseo_probe_test.dart.txt` here (rename to .dart under tool/qa to run), a parameterised copy of
  `tool/qa/p16a_paseo_probe_test.dart`): handshake, wrong password refused,
  provider list (claude, 17 models), draft session, prompt accepted, signed-out
  error in history, delete, reconnect: all passed on 0.11.2 (control), 0.12.0,
  0.13.2, 0.14.0. Protocol schema diff 0.11.2 to 0.14.0 is additive.
- Left refused: 0.13.0, 0.13.1, 0.14.1 and newer (no run).
- Not proven: signed-in turns, the large-history bounded-frame path, tool permission
  cards on 0.12+.
- Finding for the Paseo install (needs 2.2.0, not done here): from 0.11.x the
  daemon refuses `paseo start --foreground --listen ... --no-relay --no-web-ui
  --no-inject-mcp` ("--listen was removed"). The built-in host already uses
  `paseo daemon run` plus config; the legacy Termux script
  (`lib/termux/scripts/local_agents_script.dart` `run_daemon`) still uses the removed
  flags, so that path cannot start a 0.11.2 or newer daemon. Pre-existing.

## Slice 5: other agents

Pins (sha256 computed from the downloaded files; installed sizes measured):
Codex 0.166.0 (GitHub release rust-v0.166.0), Gemini CLI 0.66.0, Qwen Code 0.26.4,
Kilo 7.8.18 (binary 169 MB to 258 MB), Cline 3.0.74.

| Agent | Build-server smoke (x64, signed out) | Emulator (app install and Check) |
|---|---|---|
| Codex 0.166.0 | `--version`, `login status` = Not logged in | Check passed, "Sign in needed" |
| Gemini 0.66.0 | `--version`; ACP `initialize` answered | Install passed, "Sign in needed" |
| Qwen 0.26.4 | `--version`; ACP `initialize` answered | Install passed |
| Kilo 7.8.18 | `--version`; `acp` initialize answered | Check passed (shot 06) |
| Cline 3.0.74 | `--version`; `--acp` initialize answered | Check passed (shot 07) |

None dropped. After the pin bump an existing install shows "The required agent
version changed. Check this phone again." with a Check button until it is
checked (shot 04): expected, but users on the patch will see it for Claude,
Codex, Kilo and Cline.

Shots: /home/eslam/Storage2/wt/shots-2026-10-31/pins/

## Termux daemon launch (follow-up)

`run_daemon` now sets the daemon's config (`daemon config set ...`) and runs
`paseo daemon run --home`, as the built-in host does. Proof on the build server
(`paseoprobe3.sh`, `termux_daemon_body.sh`): the exact inner script of
`run_daemon`, password on stdin, against a 0.11.2 daemon: it wrote listen
127.0.0.1:16771, relay off, MCP injection off, web UI/dictation/voice off, and the app's
gateway then passed the whole signed-out probe (wrong password refused,
claude 17 models, session, prompt, history, delete, reconnect). The old command on
the same daemon: "--listen was removed". `test/local_agent_runtime_test.dart`
now has a Paseo stub that refuses the old flags like the real one.

## Agents move to the pins by themselves (follow-up)

Emulator (x86_64), 4270 set up from scratch (OpenCode 2.1.1, Claude Code 2.1.294,
Codex 0.162.1, Kilo 7.8.3, Cline 3.0.68; Kilo and Cline given placeholder
credentials in their own stores so they read "Signed in"), then 4301 installed
over it with `adb install -r`, nothing tapped afterwards except opening the
Agents page to look:
- OpenCode moved 2.1.1 to 2.2.5 on its own (shot 11).
- Claude finished; Codex "Updating to 0.166.0..."; Kilo and Cline "Will update
  when nothing is running" (shot 12), then all four Ready/Signed in (shot 13).
- On disk: launchers point at claude 2.1.316, codex 0.166.0, kilo 7.8.18, cline
  3.0.74; the old version folders are still there (2.1.294, 0.162.1, 7.8.3,
  3.0.68); the Kilo and Cline credential files are byte-identical (md5) before
  and after.
- Side note: downgrading with `adb install -r -d` wipes the app's data on this
  emulator, so the old state had to be rebuilt by hand. In 4270 the first install
  of Kilo and Cline failed once ("didn't install") and passed on retry; not
  investigated (4270 code, not this branch).

## The person's work wins over an automatic update (follow-up, APK 4362)

Owner's phone (4360): starting agent work during the automatic update showed
"The Paseo daemon could not complete this action. / The phone refused new agent
work: Agent setup is in progress ... (phone check running) / Paseo unavailable".

- Host: a send that starts while an automatic update is only downloading sets
  it aside (cancel; the older version stays installed, qualified and running),
  and the update retries when nothing is running; in any other step the send
  waits for the update. The old proof is kept until the new version passes.
  An agent still on its older tested version stays Ready ("behind the pin") with
  "Updating to X..." / "Will update when nothing is running" under its line.
- Composer: where a message cannot be taken for a moment, Send is off with
  "{agent} is updating, N%. You can send when it's done."; the draft stays.
- Words: the refusal is its own plain category with a way forward; the daemon
  text stays under Details.
- Wi-Fi: automatic downloads over 50 MB wait for Wi-Fi ("Will update on Wi-Fi").
- Emulator (APK 4362, dexdump GeneratedPluginRegistrant count 6): Kilo 7.8.3 and
  Claude 2.1.294 put back on their old versions and old proofs; with 4362 the
  update to Kilo 7.8.18 ran by itself while Kilo stayed "Signed in" with
  "Updating to 7.8.18..." (shot 15); a conversation started with Kilo during it
  was accepted and Kilo answered (a plain "Kilo needs you to sign in": the
  placeholder key is not a real login) (shot 16); no refusal. The update then
  finished (launcher 7.8.18).
- Not proven on the emulator: the exact moment of a send during the download
  (tests cover it: host test "while it downloads, work sets it aside and runs"),
  and Claude's update in that run did not switch the launcher (the 2.1.316
  folder already existed from an earlier run and the state was set by hand); a
  manual Check on it passes. Worth one look on a clean phone.
