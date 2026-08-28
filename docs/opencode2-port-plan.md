# OpenCode 2 port plan

Owner directive (2026-08-29): the OpenCode founder asked for the app to be
ported to the OpenCode 2 API. OpenCode 2 is in beta (installed side by side
as `opencode2`, npm `@opencode-ai/cli@next`); its server API is an
intentional breaking change from v1 — new `/api/` prefix, mandatory HTTP
Basic auth with a per-run server password, cursor pagination, a durable
inbox with steering, forms replacing questions, integrations/credentials
replacing v1 provider auth, and WebSocket-token PTY connect.

Ground truth captured in this repo:

- `contracts/opencode2-openapi-beta-18600.json` — live spec dumped from
  `opencode2 serve` v0.0.0-beta-18600 (114 paths, 240+ schemas; strict
  superset of the earlier 17823 dump kept alongside it). Note the event
  payload is opaque in the spec (`V2EventEncoded` is an encoded JSON
  string); real event/message/part types come from the v2 client stack
  `@opencode-ai/client` → `@opencode-ai/schema` + `@opencode-ai/protocol`
  (all `0.0.0-beta-18600`). Caution: the npm package named
  `@opencode-ai/sdk` at 1.x is the V1 SDK — not a v2 source.
- `docs/opencode2-protocol-notes.md` — protocol reference (agent-produced):
  auth, event union, message/part model, prompt flow, permissions, forms,
  PTY, errors, pagination.
- `docs/opencode2-port-matrix.md` — app-feature → v1-op → v2-op mapping
  with reshaped/replaced/missing status per operation (agent-produced).

## Architecture decision: dual-stack behind one domain interface

The app keeps working against v1 servers (current stable 1.18.x — every
existing user, including Termux local installs) while gaining first-class
v2 support:

1. `lib/api/` stays the v1 client, untouched except where the domain
   interface extraction requires mechanical edits.
2. `lib/api2/` is a new v2 client: Basic-auth transport, v2 models
   (hand-written Dart from the SDK types — codegen from the spec is not
   possible for events), SSE `/api/event` consumer, cursor-pagination
   helpers, WebSocket PTY.
3. A protocol-neutral domain interface (what `ProductRepository` and the
   SSE dispatcher already implicitly define) gets two implementations.
   v2 responses map into the existing app model classes wherever the
   concepts survive; concepts that changed (questions → forms, provider
   auth → integrations) get new domain types with v1 shims.
4. `server_probe.dart` detects the protocol at connect time: v2 answers
   `GET /api/health` (401 without credentials — a 401 with the v2
   `WWW-Authenticate`/error envelope is itself a positive v2 signal);
   v1 answers its unprefixed routes. First-run flow gains a password
   field that appears only when a v2 server is detected.
5. Feature flags: v2-only capabilities (inbox steering, instructions,
   revert, export) ship as progressive enhancements gated on the detected
   protocol, not blockers for the port.

## Phasing

- **Phase 1 — transport + read path**: auth/transport, server probe,
  event stream, sessions list/get, messages/parts rendering. Exit
  criterion: connect to `opencode2 serve`, browse sessions, watch a live
  run streamed by another client.
- **Phase 2 — interaction**: prompt send (model/agent selection),
  interrupt, permissions (incl. notification-shade actions bound to
  request IDs), forms (replacing the questions dialog), session
  create/rename/delete/fork.
- **Phase 3 — surfaces**: files (fs read/list/find), VCS diff review,
  PTY terminal over WebSocket tokens, models/providers/commands/skills
  listings, config, MCP management, Mission Control stats.
- **Phase 4 — v2-native**: integrations/credentials auth flows, inbox
  queue/steer UI, session export/import, revert, instructions,
  websearch; Termux story re-check (`opencode2` under Termux).

Each phase lands behind the protocol switch with tests (unit for mappers,
widget for changed screens) and an emulator verification loop against the
live beta server, same as the facelift slices.

## Constraints carried over

- Pinned Shorebird Flutter 3.47.1 for all builds/tests; foreground
  chunked test runs.
- Never persist or echo the v2 serve password (or any provider keys)
  into the repo, logs, or chat.
- flutter_animate stays banned.
- Every preview APK ships as a GitHub prerelease with a link.

## Status

- [x] Beta CLI installed, spec captured, study agents dispatched.
- [ ] Protocol notes + port matrix docs landed.
- [ ] Phase 1.
- [ ] Phase 2.
- [ ] Phase 3.
- [ ] Phase 4.
