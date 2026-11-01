# OpenCode 2.1.1 vs 2.2.5 probe (P-N4), 2026-11-01

Plan 04 §17 P-N4. Unit S0-NETP. The spike code is in `native/spikes/netp/`, and the full unit report is
`docs/native/units/S0-NETP.md`.

**Verdict: PASS, with three things to act on.** 2.1.1 stays the pin (N4-D16). 2.2.5 adds routes and removes none.
The heartbeat, the receipt `type` and the `/api/info` core match on both versions. Act on these:

1. **Fixture drift, re-record before WP-04.5.** On both servers, receipt and inbox items carry
   `time: {created}`, which is required in the spec (`Session.Inbox.User`). The Flutter fixtures
   `test/fixtures/api2/prompt_receipt.json` and `inbox_list.json` carry `timeCreated` instead, from an older beta.
   The native DTO must decode `time.created`.
2. **The Flutter builtin installs 2.2.5, but the plan pins 2.1.1.** `lib/builtin/setup/components.dart`
   (`OpenCodePins`) pins `@opencode/cli` **2.2.5** for the phone's own Ubuntu. Plan 04 N4-D16 and §7.14 pin
   **2.1.1** for the switch. The owner decision N4-O3 should be read with that in mind.
3. **`GET /api/health` is gone.** It returns 404 on both 2.1.1 and 2.2.5. `docs/opencode2-protocol-notes.md` §1 and
   item 16 still describe it as the discovery and version probe. Use `GET /api/info` (`version`, `pid`).

## Setup (container, loopback only)

| | |
|---|---|
| Install | `npm i --prefix <dir> @opencode/cli@2.1.1` and `@2.2.5` (npm 10.9.9, Node 22.23.4); ran `node_modules/@opencode/cli-linux-x64/bin/opencode` |
| Binaries (SHA-256) | 2.1.1 `0e81c77fcc27b263708b133bbf9880cd641060c20e22d3dec8a79b9a8db90450`; 2.2.5 `8d060e1d2fbb9bb44cf09ae6ed1f3f6a7cff070b5227e2f90970132b23a92050` (`cli-linux-x64`) |
| Serve | `opencode serve --port 4097` (2.1.1) or `4098` (2.2.5), `--hostname 127.0.0.1`; isolated `HOME` and `XDG_*`; `OPENCODE_PASSWORD` generated per run into a mode-600 file, never printed |
| Run | `NETP_OC2_URL=http://127.0.0.1:4097 NETP_OC2_URL_NEXT=http://127.0.0.1:4098 NETP_PASSWORD_FILE=<file> native/gradlew -p native/spikes/netp liveTest` |

No provider or credentials were configured. Prompts were accepted (receipts) and then failed to run, which is all
the probe needs.

## Results

| Check | 2.1.1 | 2.2.5 |
|---|---|---|
| `GET /openapi.json` (needs Basic auth; 401 without) | 281 322 B, sha256 `af5b79b1…98f4`; 119 paths, 144 ops, 290 schemas | 350 880 B, sha256 `f5c4571b…6c8e`; 138 paths, 169 ops, 363 schemas |
| Route diff | | +25 ops, **0 removed**, 36 changed definitions, 7 schemas removed (`Form.Fields_1..3`, `Session.Message.Native_1..4`, inlined) |
| No auth | 401, `WWW-Authenticate: Basic realm="Secure Area"` | same |
| `GET /api/info` keys | `version, pid, urls, paths, capabilities` | adds **`hostname`** (the machine name; treat it as data, never show it unasked) |
| `capabilities` | `{"persistentPty":true}` | same |
| `GET /api/health` | **404** | **404** |
| `/api/event` first frame | `server.connected` after 79 ms (cold JVM, 191 ms on the first run) | `server.connected` after 11–13 ms |
| Heartbeat | `: heartbeat` comment, gaps 14 988 / 15 003 / 15 005 ms | gaps 15 006 / 15 001 / 15 010 ms |
| Prompt receipt (`POST /api/session/{id}/prompt` with a client `id`) | 200, **`type":"user"`**, id echoed, `delivery":"steer"`, keys `id, sessionID, time, type, payload, delivery` | same |
| Inbox after a steer prompt | `{"data":[]}` (promoted at once) | same |
| `GET /api/session/{id}/message/{clientId}` | 200, `type":"user"` | same |
| Session log `GET /api/experimental/session/{id}/log?after=0&follow=false` | 200 `text/event-stream`. The route is there, but it **replays nothing**: only `{"type":"log.synced","seq":7}`. `after=-1` gives 400 | same; `operationId` renamed `session.log` → `experimental.session.log` |

Receipt type: N4-D13 is confirmed on both versions. Flutter's `CommandReceiptTransport` requires `"prompt"`, so it
never confirms a receipt. The native check must accept `"user"`.

The heartbeat is a measured 15 s on both versions. The 45 s stall timeout in `Oc2Wire` (3 × heartbeat, N4-D7)
stands. P-N3 measured the stall firing at 45 000 ms through a paused proxy, and the stream reconnected 1 016 ms later.

The session log replays nothing from `after=0`. The log is "ported, not used in production" (plan 04 §6.7), so
this blocks nothing. Anyone who brings it back must probe it again first, because a resume built on it would lose
events.

### What 2.2.5 adds (no removals)

The full list is in `native/spikes/netp/recorded/route-diff-2.1.1-to-2.2.5.md`. In short:
`/api/experimental/artifacts*` (7), `/api/experimental/schedule*` (6), `fs/watch` (2), `generate/{decisions,image,text}`,
`client-mcp` (+token), `media/{hash}`, `usage/limits`, `voice/models`, `GET /api/plugin/gui`, `POST /api/debug/heap-dump`.
`GET /api/event` gains an optional `?types=` filter (an enum of event types). This could later cut traffic for the
attention and widget graphs (2.2.x only, behind a capability).

## Files

- `native/spikes/netp/recorded/2.1.1/openapi.json` and `2.2.5/openapi.json`: the raw server bytes. Their SHA-256s
  equal the live fetches above. WP-04.7 copies the 2.1.1 file into `native/fixtures/oc2/2.1.1/`.
- `native/spikes/netp/recorded/route-diff-2.1.1-to-2.2.5.md`: written by `python3 -I probe/route_diff.py OLD NEW`.
- `native/spikes/netp/src/test/kotlin/netp/PN4ProbeTest.kt` and `PN3SseTest.kt`: the live checks. They print
  `NETP-RESULT` lines and refuse to print a line that contains the password.
