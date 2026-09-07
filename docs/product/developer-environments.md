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
| 0 | Fresh-worktree task launch | Implemented on `feature/isolated-task-launch`; live proof pending | this lane | `IsolatedTaskLaunch`, `openSessionInWorktree`, sheet, captures; see [verification](../verification/isolated-task-launch-2026-09-08.md) | v1 only (`worktreeCreate`); v2 create body unproven |
| 1 | Developer-service control panel (React/Vite/etc. running in the coding environment: status, logs, Visit, Restart/Stop) | Next; plan only | this lane (next branch) | v2 PTY/shell endpoints and `pty.*` events are in `contracts/opencode2-openapi-beta-18600.json` and `lib/api2/gateway_events.dart`; the app has a terminal screen and `RUN_COMMAND` returns bounded output only | Must prove a callable process/log contract first; track the exact owned PID/process identity and scope; never pattern-kill or restart the agent's own server; Visit uses an explicit reachable host mapping through `openExternalLink` (phone `localhost` is not the host) and never exposes a port publicly |
| 2 | Language-server / formatter readiness and actionable setup | Queued; plan only | this lane | `project_health_screen.dart` lists v1 LSP and formatter status; `api2/gateway_mappers.dart` sets `languageServiceStatus`/`formatterStatus` false because the pinned v2 snapshot has no status endpoints | "No active LSP" is not "not installed"; no fake formatter-run endpoint; upstream docs (https://opencode.ai/docs/lsp/, formatters, plugins) say LSP is disabled by default with project prerequisites — version-sensitive, verify against the pinned schema before any UI claim |
| 3 | Managed hosting / SaaS | Research and plan only | this lane | Nothing in source; `F9` connectivity guidance and `F10` adapters are the nearest related work | No checkout, cloud provisioning or public exposure until costs, isolation, data deletion/export, provider BYO billing, sleep/resume, limits and the security model are concrete; candidate model is managed workspace compute subscription, not resale of model subscriptions |

## Next end-to-end finish line (slice 1)

From the workspace, open a "Services" destination that lists only services
the app started or the user explicitly adopted, each with a live status, a
bounded log tail, a Visit action that opens the mapped host URL externally,
and Restart/Stop bound to the recorded process identity. Stopping the agent's
own server, killing by name pattern, or exposing a port must be impossible by
construction. Evidence before build: the PTY/process contract callable on the
pinned v2 snapshot, plus a v1 statement of what is and is not available.
