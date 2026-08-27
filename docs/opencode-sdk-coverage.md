# OpenCode Dart SDK coverage

Audit date: 2026-08-28

Authoritative inputs:

- generated SDK under `packages/opencode_sdk/lib/src/api/`
- source contract `contracts/opencode-openapi-03bba464.json`
- production call sites under `lib/`

The SDK exposes **188 generated HTTP operations** across 32 API groups. The app
currently calls **39 generated operations directly**. It also uses a set of
older or compatibility routes through `OpenCodeApi` and raw `Dio`; those are
listed separately so a working feature is not incorrectly labelled missing.

Legend:

- **Generated:** called through the generated Dart SDK.
- **Compatibility:** the capability is used, but through a handwritten route or
  raw response parser.
- **Hidden:** present in the generated contract but not exposed by the app.

| API | Generated | Compatibility | Hidden or incomplete |
|---|---|---|---|
| Commands | `v2CommandList` | - | - |
| Config | - | `configProviders` | `configGet`, `configUpdate` |
| Control | `authSet` | - | `appLog`, `authRemove` |
| Control plane | - | - | `experimentalControlPlaneMoveSession` |
| Event | - | `eventSubscribe` through the shared `/event` SSE transport | Generated event transport is unused |
| Events v2 | - | Some v2-shaped events are reduced from the legacy stream | `v2EventSubscribe` |
| Experimental | `experimentalResourceList` | - | capabilities, console state/org list/org switch, background sessions, global session list, tool IDs/list, worktree create/list/remove/reset |
| File | `findSymbols` | list, read, filename search, text search | status |
| Filesystem v2 | - | - | `v2FsFind`, `v2FsList`, `v2FsRead` |
| Global | - | health | config get/update, dispose, event, upgrade |
| Instance | `instanceDispose`, `vcsDiff`, `vcsGet`, `vcsStatus`, `lspStatus`, `formatterStatus` | - | agents, skills, command list, path, VCS raw diff/apply |
| Integrations | list, key connect, OAuth start, attempt status, complete, cancel | provider runtime refresh after confirmed completion | - |
| MCP | status, connect, disconnect, auth start | - | add, auth authenticate/callback/remove |
| Messages v2 | - | legacy session message list | `v2SessionMessages` |
| Models v2 | - | raw `/api/model` catalog parser | `v2ModelList` generated model is unused |
| OpenCode HTTP v2 | - | raw `/api/agent` catalog parser | agent list, credential update/remove, health, location |
| Permission | - | legacy list/reply | Generated legacy methods are unused |
| Permissions v2 | - | request list and session reply through compatibility routes | saved permission list/remove, per-session create/get/list |
| Project | `projectList` | - | current project, directories, Git init, update |
| Project copy | - | - | generated name, create, refresh, remove |
| Provider | - | provider list and configured-provider fallback | auth methods and OAuth authorize/callback |
| Providers v2 | - | raw `/api/provider` catalog parser | provider get/list generated models are unused |
| PTY | create, list, update, remove, connect token | WebSocket connect uses the guarded ticket | get, shells, direct connect, and all duplicate v2 PTY operations |
| Question | list, reply, reject | - | - |
| Reference | `v2ReferenceList` | - | References can be copied standalone or added to the active composer as OpenCode directory parts |
| Session | fork, revert, unrevert, share, unshare, summarize, update | list/create/get/delete/rename/status/messages/prompt/shell/command/abort/init/todos/diff and permission fallback | children, delete/update part, delete/get one message, synchronous prompt |
| Session questions v2 | - | list/reply/reject through compatibility routes | Generated v2 methods are unused |
| Sessions v2 | - | - | active, compact, context, create/get/list/message/prompt/history/events/wait/interrupt, model/agent switch, staged revert operations |
| Skills | `v2SkillList` | - | Skill content uses the shared Markdown/code-aware preview with rendered and raw modes |
| Sync | - | - | history, replay, start, steal |
| TUI control | - | - | append/clear/submit prompt, command execution, next-control flow, publish, select session, help/models/sessions/themes/toast |
| Workspace | `experimentalWorkspaceList` | - | adapter list, create, remove, status, sync list, warp |

## Product priorities derived from the audit

1. **Review truth:** use `vcsDiff(mode: git|branch)` alongside session diff so
   Review can show uncommitted work and the complete branch delta. This is now
   implemented as adjacent Session, Working tree, and Branch scopes.
2. **Provider OAuth completion:** now implemented. The app retains the attempt
   ID, mode, instructions, and expiry; it shows a visible pending row, checks
   automatic callbacks on resume or demand, accepts code-based completion,
   supports cancellation, and refreshes provider/model inventories only after
   the server confirms completion.
3. **Coding health and navigation:** VCS status/info, LSP, and formatter status now share one
   flat Project health surface in the selected workspace. Each section fails
   independently so one unavailable endpoint does not hide valid server truth.
   Files now has adjacent Files and Symbols tabs; generated LSP symbol results
   open the referenced source file at its exact one-based line and highlight it.
   Skill details now use the same smart preview path as project files, including
   formatted Markdown tables, fenced code, selection, and an explicit raw view.
   Project references are actionable from their native screen and preserve
   OpenCode's `@name` plus `file://` directory-part prompt contract.
4. **Transport modernization:** migrate compatibility routes to generated v2
   APIs only where current and older OpenCode contracts can be reconciled
   without losing sessions, events, or provider inventory.
5. **Deferred by owner:** agent/session trees, a Traycer-style task cockpit, and
   worktree lifecycle are deliberately not the current implementation lane.

## Deliberate non-goals

The TUI control API should not be mirrored button-for-button. Mobile should own
its native navigation, pickers, prompts, and notifications. Global config
mutation, sync takeover, raw patch apply, credential removal, and destructive
worktree operations also stay hidden until they have precise confirmation,
recovery, and version-compatibility contracts.
