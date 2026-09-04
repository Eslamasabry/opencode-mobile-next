---
name: dual-protocol
description: Use for OpenCode API, v1, v2, SSE, events, generated SDK, OpenAPI, gateways, server flavor, or capability changes.
---

# Dual Protocol Workflow

## Establish The Lane

- Generated/v1 SDK: `contracts/opencode-openapi-f12e14cf.json`, its manifest,
  generator, and `packages/opencode_sdk/`.
- OpenCode 1 facade: generated SDK plus `lib/api/` behavior.
- OpenCode 2 HTTP: matching OpenAPI, upstream source, and live captured evidence.
- OpenCode 2 events: matching schemas/protocol source and sanitized captures.
- Product parity: domain semantics, capability gate, state reconciliation, UI.

Never use a similarly named endpoint from one lane as proof for another.

## Evidence Ledger

Record the version, authority, method/path or event, location scope, request,
success response, errors, semantic counterpart, mapping losses, capability,
unknown-version behavior, and tests.

## Trace

For HTTP, trace contract to transport, client, gateway, domain, controller,
capability, consumer, and test. For events, trace capture/schema to parser,
typed event, adapter, controller, reconciliation, and strict fixture test.

## Rules

- Compare semantics, not endpoint count.
- Keep Basic credentials out of URLs and output.
- Accept proven 204 responses and exact location/query encoding.
- Preserve unknown runtime data safely; fail strict fixtures on known drift.
- Reconcile after reconnect because event delivery may be volatile.
- A capability is true only for a complete callable user operation.
- Never hand-edit generated SDK files; change generator inputs and regenerate.
- Report unresolved evidence conflicts rather than guessing.

Run focused protocol tests, generated verification when applicable, analyzer,
then the serial full suite.
