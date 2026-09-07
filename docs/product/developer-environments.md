# Developer environments — scope register — 2026-09-08

Queued product slices after the fresh-worktree task launch. One slice is
active per worktree; the others hold a plan and an owner, not code. Facts
below come from current source and pinned contracts; anything unverified is
marked.

Implementation owner for this lane is agent
`2e0ed0f8-753e-4af7-a524-728ecdc1d4ac`; coordinator
`50dce737-95f4-4a97-b220-ab2631eebe4d` owns integration and branch handoff.
The language-service upstream-doc note below was supplied by the coordinator
and still needs independent version verification before implementation.

| # | Slice | Status | Owner | What exists today | Known constraints |
|---|---|---|---|---|---|
| 0 | Fresh-worktree task launch | Implemented on `feature/isolated-task-launch`; independent-review scope corrections in progress; live proof pending | this lane | `IsolatedTaskLaunch`, `openSessionInWorktree`, sheet, captures; see [verification](../verification/isolated-task-launch-2026-09-08.md) | v1 only (`worktreeCreate`); v2 create body unproven |
| 1 | Developer-service control panel | Committed at `da6a9ba` on `feature/development-services`; independent review pending | this lane | Managed-shell ownership proof passed against isolated beta-18600; 56 focused checks and 15 synthetic phone captures passed | Feature-created nonce ownership only; no arbitrary adoption. V1/Codex retain saved commands and reviewed Visit without fake controls |
| 2 | Language-server / formatter readiness and actionable setup | Queued; plan only | this lane | `project_health_screen.dart` lists v1 LSP and formatter status; `api2/gateway_mappers.dart` sets `languageServiceStatus`/`formatterStatus` false because the pinned v2 snapshot has no status endpoints | "No active LSP" is not "not installed"; no fake formatter-run endpoint; upstream docs (https://opencode.ai/docs/lsp/, formatters, plugins) say LSP is disabled by default with project prerequisites — version-sensitive, verify against the pinned schema before any UI claim |
| 3 | Project rewind | Queued proposal only | this lane | Files/configuration checkpoints are proposed, not implemented | No promise to undo external side effects |

## Current finish line

Correct only the two independent isolated-task scope findings: a sheet opened
in one profile/project/location cannot create after its scope changes, and a
superseding same-directory workspace selection cannot retarget blank-session
creation or routing. No-send/no-delete behavior remains unchanged. The correction
passed 69 focused/capture checks and a clean analyzer under its granted runtime
lease; integration review is next.

## Canceled history

Managed hosting / SaaS was explored earlier and explicitly canceled by the user
on 2026-09-08. It is removed from the active Portfolio B queue. No further SaaS
planning, implementation, provisioning, billing or provider selection is planned.
