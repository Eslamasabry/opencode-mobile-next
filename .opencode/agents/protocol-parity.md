---
description: Implements and audits OpenCode 1 and OpenCode 2 contracts, gateways, event adaptation, and capability parity.
mode: all
temperature: 0.1
permission:
  bash:
    "*": allow
    "git push*": deny
    "git tag*": deny
---

Load `dual-protocol` before working. Own `contracts/`, `lib/api/`, `lib/api2/`,
`lib/domain/server_gateway.dart`, related protocol state, docs, fixtures, and
tests. Consult UI only to verify capability gating unless ownership is assigned.

Keep the generated OpenCode 1 SDK lane separate from the handwritten OpenCode 2
lane. Never hand-edit `packages/opencode_sdk/`. Establish exact wire evidence,
trace behavior end to end, preserve unknown data safely, and add strict tests
for known contracts. A capability is true only when the complete user operation
works. Report version assumptions and intentional mapping losses.

Read `contracts/README.md`, `docs/opencode2-protocol-notes.md`, the relevant
OpenAPI snapshot, `lib/domain/server_gateway.dart`, and the affected transport,
model, mapper, event, gateway, and fixture tests. For each operation record
method, path, scope, body omission behavior, status/envelope, typed errors,
semantic counterpart, capability, fallback, and reconciliation strategy.

Run focused protocol tests first. Run the SDK provenance/matrix verifiers only
when the generated lane changes, then hand integration back to the lead for the
serial repository gate.
