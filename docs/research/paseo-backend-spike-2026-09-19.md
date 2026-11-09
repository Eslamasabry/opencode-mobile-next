# Paseo daemon as a backend for Claude Code and Pi — spike, 2026-09-19

Question: can the app control Claude Code and the Pi coding agent by speaking
to the open-source Paseo daemon (`getpaseo/paseo`, Apache-2.0) instead of us
writing a companion daemon? This is backlog item F10 (alternate-agent support).

Constraint from the owner: nothing crosses the open internet. Local or
Tailscale only. Paseo's relay is never enabled.

## Status

Implemented the same day as `lib/paseo/` (see `docs/paseo-connection.md`).
Findings since the spike: the released 0.8.0 daemon lacks
`agent.create.request`, so the app aliases its own session id to the daemon's;
agent snapshots lag the stream, so turn events own the busy state; a wrong
password is a 4401 close *after* a successful upgrade; and assistant text
arrives as deltas per `messageId` while tool calls arrive as full snapshots
per `callId`.

## Verdict

Feasible, and cheap on our side. The daemon speaks plain JSON over one
WebSocket, needs no pairing or relay for a direct connection, and a full
Claude Code session (create, stream, permission gate, mode change, follow-up
turn) ran through it on this machine. The shape maps onto `ServerGateway` the
same way Codex does (`lib/codex/`): one new `ServerBackend.paseo`, a transport,
a gateway and mappers.

Pi is supported by the daemon (`pi --mode rpc` subprocess) and the agent was
created, but no turn completed here: the only credential Pi has on this
machine is GitHub Copilot, which answers `429 quota exceeded`. That is a
credentials problem, not a Paseo one. The installed Pi 0.73.1 hangs silently on
that 429; Pi 0.85.1 reports it. Pi remains unproven until a working model key
exists.

## What was run

Workspace: `/home/owner/Storage/Code/paseo-spike` (outside this repo).
`@getpaseo/cli` 0.8.0, daemon home inside the spike folder, port 6790,
`--no-relay --no-inject-mcp --no-web-ui`. A logging WebSocket proxy on 6791
recorded every frame while the Paseo CLI drove the daemon: 163 frames in
`capture.jsonl`. Both processes were stopped afterwards.

## Protocol, as observed

Connect to `ws://host:port/ws`. Text frames, one JSON object each.

1. Client sends `hello`: `clientId`, `clientType`, `protocolVersion: 1`,
   `appVersion`, and a `capabilities` map of feature flags. `appVersion` must be
   at least 0.1.45 or the daemon hides every provider except claude, codex and
   opencode, which would hide Pi.
2. Daemon answers with `session` → `status` (`server_info`: server id, version,
   the permissions this connection holds, a `features` map).
3. Everything after is wrapped as `{"type":"session","message":{...}}`.
   Requests carry a `requestId`; the response type is the request type with
   `_response` (older messages) or `.response` (newer dotted names). `ping` /
   `pong` keep the socket alive.

Messages the app needs, all seen on the wire:

| Purpose | Message |
|---|---|
| Register a folder | `workspace.create.request` `{source:{kind:"directory",path}}` |
| Start an agent | `create_agent_request` `{config:{provider,cwd,model,title}, workspaceId, initialPrompt}` |
| Read an agent | `fetch_agent_request` → status, modes, `pendingPermissions`, `persistence` (native session id) |
| History | `fetch_agent_timeline_request` `{direction:"tail", limit, projection}` |
| Live stream | `agent.timeline.set_subscription.request` `{agentIds}` then `agent_stream` pushes |
| Send a turn | `send_agent_message_request` `{agentId, text, messageId}` |
| Answer a permission | `agent_permission_response` `{agentId, requestId, response:{behavior:"allow"}}` |
| Change mode | `set_agent_mode_request` `{agentId, modeId}` |

`agent_stream` events: `turn_started`, `turn_completed` (with token usage, cost
and context-window fill), and `timeline` items of type `user_message`,
`assistant_message` and `tool_call` (`callId`, `name`, `status`
running/completed, typed `detail` such as `write` with path and content). Each
timeline event carries `seq` and `epoch`, which is what a reconnecting client
needs to resume without duplicates.

A pending permission carries the tool name, raw input, a typed `detail`, and
`suggestions` such as "switch to acceptEdits for this session". Claude's modes
came back as plan, default, acceptEdits, auto and bypassPermissions.

The schemas are Zod, in `packages/protocol/src` of the Paseo repo. The project
documents a compatibility promise in `docs/protocol-compatibility.md`: old
clients must keep parsing new daemon messages.

## Tailscale-only deployment

Documented in Paseo's `SECURITY.md`, not yet exercised here:

- The daemon binds to 127.0.0.1 by default and, with no password, trusts
  anything that can reach the socket.
- For the phone: `--listen <tailscale-ip>:6767` and a password
  (`paseo daemon set-password`, stored bcrypt-hashed). The WebSocket upgrade
  must then carry `Sec-WebSocket-Protocol: paseo.bearer.<password>`.
- The `Host` header is checked against an allowlist. Literal IPs pass by
  default; a MagicDNS name needs `--hostnames`.
- `--no-relay` keeps the daemon off `relay.paseo.sh`. The app must never offer
  the relay or QR pairing path.

## Mapping onto the app

| App concept | Paseo |
|---|---|
| Server profile (`ServerBackend`) | daemon URL + password |
| Project / directory | workspace |
| Session | agent (one provider session, resumable via `persistence`) |
| Message parts | timeline items |
| Permission card | `pendingPermissions` + `agent_permission_response` |
| Agent / mode picker | provider + `availableModes` |
| Usage | `turn_completed.usage` |

`ServerCapabilities` would start almost entirely false, like Codex: chat,
permissions, modes and usage on; files, VCS, terminal, MCP and catalog off until
mapped. Paseo does have terminal, checkout/git and file RPCs, so those are
later work, not dead ends.

## Risks and open questions

- Protocol dependency on a fast-moving project (0.8.0, pushed daily). Pin a
  tested daemon version range and gate on `server_info.version` and `features`.
- The `hello` capability flags change daemon behaviour (compact snapshots,
  selective timelines). The app should claim only what it implements; which
  flags are safe to omit is untested.
- Text streaming granularity: assistant messages arrived as several
  `assistant_message` items per turn. Whether these are deltas or cumulative
  per `messageId` needs checking before writing the mapper.
- Questions and forms (Pi's `select`/`input`/`confirm` dialogs are bridged to
  "question permissions") were not captured.
- On-device hosting in Termux is unexplored. The daemon depends on `node-pty`
  (native build) and the Claude Agent SDK; both are unknowns on Android. PC
  hosting over Tailscale comes first.
- Licence: Apache-2.0 with third-party components under their own terms. We
  only speak the protocol, so nothing is redistributed unless we bundle the
  daemon in the Termux runtime later.

## Suggested next step

A `lib/paseo/` slice mirroring `lib/codex/`: transport (WebSocket, hello,
request/response correlation, bearer subprotocol), mappers from timeline items
to `lib/api/models.dart`, gateway with minimal capabilities, and a connection
probe. Drive it with fixtures cut from `capture.jsonl`, then one live proof
against a Tailscale-bound daemon with a password. Get a working Pi model key
first so Pi is proven in the same pass.
