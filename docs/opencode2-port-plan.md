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

- [x] Beta CLI installed; spec captured (`contracts/opencode2-openapi-beta-18600.json`).
- [x] Protocol notes + port matrix + locked UI design landed.
- [x] **Phase 1 — transport + read path.** `lib/api2/` typed client
      (live-verified 30 steps); `lib/domain/server_gateway.dart` extracts
      109 operations behind protocol-neutral gateways with a
      `ServerCapabilities` surface, v1 implementing all of it with zero
      behavior change; `lib/api2/gateway*.dart` implements the same
      interfaces over v2 (live smoke 40/40, incl. a streamed prompt and a
      ticketed-WebSocket PTY round-trip).
- [x] **Phase 2 — interaction.** Connect flow: probe flavor detection
      (401-with-empty-body on `/api/health` is the v2 signal; missing vs
      rejected password distinguished), paste-first password field,
      `ServerProfile.flavor` persisted, v2 gateway pair built through the
      v1 seams, mid-session 401 → banner action. Forms replace questions
      end to end (auto-present, inline reopen card, pending counts);
      permission bottom sheet with reject-with-reason, wired to the
      notification Reply action; one pending-sends strip for offline
      drafts + v2 inbox with cancel and delivery flips; Send stays live
      mid-turn (tap steers, long-press queues).
- [x] **Phase 3 — surfaces.** Files/VCS/PTY/catalog/commands/skills/MCP
      all flow through the v2 gateway; v2's message variants render
      natively (switch marker pills, shell rows with status, compaction
      pill and summary, notices) and tool results keep their text/file
      interleaving; the model picker applies session-scoped on v2.
      Capability gating of v1-only surfaces (design §7) is done and
      tested (`test/v2_feature_gating_test.dart`).
- [ ] **Phase 4 — v2-native.** Integrations/credentials screens (design
      §8); inbox steering beyond the strip; session export/import;
      revert; instructions; websearch; Termux bootstrap flipped to
      `opencode2` (see docs/opencode2-termux.md).

Known gaps carried forward: message list is cursor-walked to a bounded
fetch-all rather than paginated in the UI; OAuth attempts can't resume
across app restarts (v2 scopes them per integration, the domain
addresses them by id); v2's experimental durable session log replays
nothing on beta-18600, so transcripts reconcile via refetch; v2-only
message variants render on hydration, not synthesized mid-turn.
