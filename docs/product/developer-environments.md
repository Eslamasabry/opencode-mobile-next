# Developer environments — scope register — 2026-09-08

Product slices after the fresh-worktree task launch. One slice is
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
| 0 | Fresh-worktree task launch | Implemented at `a4114cc` on `feature/isolated-task-launch`; independent-review corrections are next in that worktree | this lane | `IsolatedTaskLaunch`, `openSessionInWorktree`, sheet and captures retained in its own branch | v1 only (`worktreeCreate`); v2 create body unproven |
| 1 | Developer-service control panel (React/Vite/etc. running in the coding environment: status, logs, Visit, Restart/Stop) | Implemented on `feature/development-services`; native contract proof and focused Flutter verification passed; integration review pending | this lane | Owned managed-shell POST/GET/output/DELETE verified against isolated beta-18600; saved-service/controller/UI and captures complete; see [evidence](../verification/development-services-2026-09-08.md) | Only feature-created ownership nonces authorize controls; no arbitrary PID adoption. V1/Codex keep saved commands and reviewed URLs without process controls. No automatic public binding or tunnels |
| 2 | Language-server / formatter readiness and actionable setup | Queued; plan only | this lane | `project_health_screen.dart` lists v1 LSP and formatter status; `api2/gateway_mappers.dart` sets `languageServiceStatus`/`formatterStatus` false because the pinned v2 snapshot has no status endpoints | "No active LSP" is not "not installed"; no fake formatter-run endpoint; upstream docs (https://opencode.ai/docs/lsp/, formatters, plugins) say LSP is disabled by default with project prerequisites — version-sensitive, verify against the pinned schema before any UI claim |
| 3 | Managed hosting / SaaS | Research and plan only | this lane | Nothing in source; `F9` connectivity guidance and `F10` adapters are the nearest related work | No checkout, cloud provisioning or public exposure until costs, isolation, data deletion/export, provider BYO billing, sleep/resume, limits and the security model are concrete; candidate model is managed workspace compute subscription, not resale of model subscriptions |

## Slice 1 finish line

From Workspace → Manage project → Development services, save a foreground
command and a reachable preview URL without starting it. Explicit Start creates
an owned managed shell. The panel reconciles its command status, reads a bounded
log tail, and offers reviewed Visit and ownership-checked Stop/Restart. Saving
and removing configuration never starts or stops a process. Recovery uses the
receipt persisted before POST; arbitrary adoption and PID control are excluded.
The native proof establishes the pinned v2 contract. Unsupported transports keep
saved commands and reviewed links. Language readiness and hosted SaaS remain
queued, with their decisions above preserved for later implementation.
