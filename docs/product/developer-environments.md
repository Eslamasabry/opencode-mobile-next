# Developer environments — scope register — 2026-09-08

Each feature has one owner and worktree. Implementation owner for this lane is
agent `2e0ed0f8-753e-4af7-a524-728ecdc1d4ac`; coordinator
`50dce737-95f4-4a97-b220-ab2631eebe4d` owns integration and release validation.

| Slice | Current state | Evidence and constraints |
| --- | --- | --- |
| Fresh-worktree task launch | Implemented at `a4114cc`, corrected at `d0fe80bb`; independent scope recheck passed | [Verification](../verification/isolated-task-launch-2026-09-08.md). Creation is v1-only until the v2 create contract is proven. Original profile/project scope is retained through the sheet and every asynchronous preparation step. No prompt is sent and cancellation never deletes a worktree. |
| Development services | Implemented at `da6a9ba`; independent ownership/scope review passed; integrated locally | [Verification](../verification/development-services-2026-09-08.md). Owned managed-shell POST/GET/output/DELETE passed an isolated beta-18600 proof; 56 focused checks and 15 synthetic captures passed. Only persisted feature-created ownership receipts authorize controls. V1/Codex retain saved commands and reviewed Visit. |
| Language-server and formatter readiness | Proposed; no new adapter implemented | Project health already exposes supported server status. An empty active-LSP list does not prove that an LSP is uninstalled. The pinned v2 snapshot has no equivalent status endpoints; do not invent formatter execution or setup APIs. Verify version-specific upstream behavior before adding guidance. |
| Project rewind | Proposed only | File/configuration checkpoints require a separate usable slice. They cannot promise to undo external side effects. |
| Tailscale connection | Explicitly requested for the current release; implementation follows the check-in reminder checkpoint in a separate worktree | Use the official Android app and existing authenticated server connection flow. Do not infer VPN connectivity merely from an installed package or introduce managed hosting. |

## Development-services journey

Workspace → Manage project → Development services saves a foreground command
and reachable preview URL without starting it. Explicit Start creates an owned
managed shell. The panel reconciles command status, reads a bounded log tail,
and offers reviewed Visit and ownership-checked Stop/Restart. Saving or removing
configuration does not start or stop a process. Recovery uses the receipt saved
before POST; unknown ownership cannot authorize control or a silent duplicate
start. Arbitrary process adoption, public binding and tunnels are excluded.

Focused branch checks and source reviews are complete for isolated tasks and
development services. Combined candidate checks and the installed Android
walkthrough remain required before release.

## Canceled history

Managed hosting / SaaS was explored earlier and explicitly canceled by the user
on 2026-09-08. It is outside the active queue. No further SaaS planning,
provisioning, billing or provider selection is planned.
