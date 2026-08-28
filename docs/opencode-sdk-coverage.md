# OpenCode Dart SDK coverage

Audit date: 2026-08-28

Authoritative inputs:

- generated SDK under `packages/opencode_sdk/lib/src/api/`
- source contract `contracts/opencode-openapi-03bba464.json`
- production call sites under `lib/`

The SDK exposes **188 generated HTTP operations** across 32 API groups. The app
currently calls **80 generated operations directly**. It also uses a set of
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
| Config | `configGet`, `configUpdate` | `configProviders` | - |
| Control | `authSet`, `authRemove` | - | `appLog` |
| Control plane | `experimentalControlPlaneMoveSession` | - | - |
| Event | - | `eventSubscribe` through the shared `/event` SSE transport | Generated event transport is unused |
| Events v2 | - | Some v2-shaped events are reduced from the legacy stream | `v2EventSubscribe` |
| Experimental | `experimentalResourceList`, Console org list/switch | - | capabilities, console state, background sessions, global session list, tool IDs/list, worktree create/list/remove/reset |
| File | list, read, filename search, text search, `findSymbols` | - | `status` is declared but its OpenCode 1.18.23 handler is a stub that always returns an empty list |
| Filesystem v2 | - | - | `v2FsFind`, `v2FsList`, `v2FsRead` |
| Global | `globalConfigGet`, `globalConfigUpdate` | health | dispose, event, upgrade |
| Instance | `instanceDispose`, `vcsDiff`, `vcsGet`, `vcsStatus`, `lspStatus`, `formatterStatus` | - | agents, skills, command list, path, VCS raw diff/apply |
| Integrations | list, key connect, OAuth start, attempt status, complete, cancel | provider runtime refresh after confirmed completion | - |
| MCP | status, connect, disconnect, auth start/callback/remove | - | runtime-only add and same-host auth authenticate |
| Messages v2 | - | legacy session message list | `v2SessionMessages` |
| Models v2 | - | raw `/api/model` catalog parser | `v2ModelList` generated model is unused |
| OpenCode HTTP v2 | credential remove | raw `/api/agent` catalog parser | agent list, credential update, health, location |
| Permission | list, reply, deprecated session reply fallback | - | - |
| Permissions v2 | request list, session reply, saved permission list/remove | - | per-session create/get/list |
| Project | `projectList`, `projectDirectories`, current project | - | Git init, update |
| Project copy | - | - | generated name, create, refresh, remove |
| Provider | - | provider list and configured-provider fallback | auth methods and OAuth authorize/callback |
| Providers v2 | - | raw `/api/provider` catalog parser | provider get/list generated models are unused |
| PTY | create, list, update, remove, connect token | WebSocket connect uses the guarded ticket and OpenCode cursor resume | get, shells, direct connect, and all duplicate v2 PTY operations |
| Question | list, reply, reject | - | - |
| Reference | `v2ReferenceList` | - | References can be copied standalone or added to the active composer as OpenCode directory parts |
| Session | list, create, get, delete, update/rename, status, messages, todos, diff, fork, revert, unrevert, share, unshare, summarize, async prompt including synthetic location reminder, command, abort | shell | init endpoint, children, delete/update part, delete/get one message, synchronous prompt |
| Session questions v2 | global request list, session reply/reject | - | per-session list |
| Sessions v2 | - | - | active, compact, context, create/get/list/message/prompt/history/events/wait/interrupt, model/agent switch, staged revert operations |
| Skills | `v2SkillList` | - | Skill content uses the shared Markdown/code-aware preview with rendered and raw modes |
| Sync | - | - | history, replay, start, steal |
| TUI control | - | - | append/clear/submit prompt, command execution, next-control flow, publish, select session, help/models/sessions/themes/toast |
| Workspace | list, connection status, session warp | - | adapter list, create, remove, sync list |

## Product priorities derived from the audit

1. **Review truth:** use `vcsDiff(mode: git|branch)` alongside session diff so
   Review can show uncommitted work and the complete branch delta. This is now
   implemented as adjacent Session, Working tree, and Branch scopes.
2. **Provider OAuth completion:** now implemented. The app retains the attempt
   ID, mode, instructions, and expiry; it shows a visible pending row, checks
   automatic callbacks on resume or demand, accepts code-based completion,
   supports cancellation, and refreshes provider/model inventories only after
   the server confirms completion.
3. **Provider access removal:** stored integration credentials can now be
   revoked from the same native provider row. The app uses OpenCode's exact
   returned credential ID for the v2 store and the exact integration ID for
   the legacy runtime store; it does not infer aliases. Legacy removal happens
   first so a failure leaves the visible v2 connection untouched for retry.
   Environment-backed connections remain explicitly server-managed because
   mobile cannot remove the server process environment.
4. **Coding health and navigation:** VCS status/info, LSP, and formatter status now share one
   flat Project health surface in the selected workspace. Each section fails
   independently so one unavailable endpoint does not hide valid server truth.
   Files now has adjacent Files and Symbols tabs; generated LSP symbol results
   open the referenced source file at its exact one-based line and highlight it.
   The Files browser also uses OpenCode's generated VCS status contract:
   existing file rows show added, modified, or deleted truth with line counts,
   while folders show the number of changed descendants. Status refreshes after
   Android wake and file search, but a missing older-server endpoint remains a
   small retryable notice and never replaces valid directory contents.
   Each changed-file row also has a distinct review action that opens the
   authoritative Working tree diff with that exact file already selected.
   Ordinary row taps remain file previews, deleted files remain reviewable, and
   a review comment returns to the active chat composer or copies to the system
   clipboard when Files was opened outside a chat.
   Symbol queries debounce as users type, and a successful empty response states
   that some language services do not support workspace-wide symbol search
   instead of presenting a blank surface. Live OpenCode `1.18.23` returned this
   empty result for Dart even while its Dart service was connected; its separate
   document-symbol debugger did return file-local Dart symbols, so the mobile UI
   does not invent a fallback result.
   Skill details now use the same smart preview path as project files, including
   formatted Markdown tables, fenced code, selection, and an explicit raw view.
   Project references are actionable from their native screen and preserve
   OpenCode's `@name` plus `file://` directory-part prompt contract.
5. **Session location and organization parity:** `/move`, `/warp`, and `/org`
   now use their generated contracts instead of redirecting to the generic
   workspace screen. Move can transfer working changes, warp can copy them to
   a connected workspace or return Local, and organization switching disposes
   and rebuilds the location-scoped transport so providers and models reload.
   Servers without workspace-status support retain their workspace list with
   an unknown status rather than losing the entire surface.
6. **Transport modernization:** migrate compatibility routes to generated v2
   APIs only where current and older OpenCode contracts can be reconciled
   without losing sessions, events, or provider inventory. The highest-volume
   async prompt path now uses `session.prompt_async` directly while preserving
   the exact model, variant, agent, text, file, reference, and location wire
   fields plus product-facing server errors. Server commands now use the
   generated contract too. Shell remains handwritten because the generated
   `SessionShellRequest` omits the selected thinking variant; migrating it now
   would silently change execution.
   Session abort now waits for the wake-reconciled transport and exposes
   generated OpenCode failures instead of swallowing them. The unused direct
   init wrapper was removed; `/init` remains available through the authoritative
   server-command catalog.
   Create, rename, and delete now share the same wake-reconciled controller
   path. Their generated transports preserve loose successful responses from
   older servers, while declared failures retain OpenCode details. Every visible
   session deletion now requires explicit confirmation.
   Session list, detail, status, and message hydration now use generated paths
   as well. Session metadata retains loose older-server fallback, while the
   generated raw unions preserve full message and tool/plugin part JSON before
   the existing renderer parses it.
   Session todos and diffs now use their generated location-scoped paths too.
   The app retains todo priority and typed diff counts/status, while successful
   responses from older servers that omit required generated fields fall back
   to the tolerant parser so legacy todo and before/after diff data is not lost.
   Legacy and V2 permission/question hydration and replies now use generated
   contracts, including the deprecated generated reply only as a bounded
   old-server fallback. Successful loose envelopes remain supported, declared
   request identities remain available for race-safe resolution, and all reply
   actions wait for wake-time transport reconciliation before sending.
   File listing, file reads, filename search, and text search now use the
   generated location-scoped contracts as well. Binary type, base64 encoding,
   and MIME metadata survive the generated mapping so tool images and shared
   previews remain downloadable and attachable. Successful old-server nodes,
   raw text bodies, line-array content, and loose search matches retain their
   tolerant compatibility path. File browsing, search, symbols, direct file
   viewing, and chat tool-output previews wait for wake-time transport
   reconciliation before reading.
   Wake reconciliation now resolves the generated API and its paired product
   repository atomically for every retained foreground action. This covers
   session share/unshare, fork, compact, revert/restore, retry and shell;
   Review, Todos, server commands, Skills and References; MCP and provider
   integration actions; workspace session actions and destination discovery;
   project health, settings health, and terminal mutations. A screen may keep
   already-rendered data while Android wakes, but it cannot send a new request
   through the repository being retired.
   Terminal WebSockets also retain OpenCode's authoritative stream cursor from
   binary control frames and send it on reconnect, preventing the server's PTY
   replay from duplicating accessible transcript content after Android wake.
7. **Persistent MCP setup:** the Library now has a native remote/local MCP form
   that writes either the selected project's config or the server-wide config.
   It validates URLs, commands, headers, environment variables, and timeouts;
   rejects duplicate names before patching; then rebuilds the location transport
   because OpenCode invalidates the configured instance. The runtime-only
   `mcp.add` endpoint remains hidden because its state does not survive a server
   restart. A saved config is never offered for duplicate submission when the
   subsequent app reconnect fails.
8. **Durable permission control:** Settings now exposes OpenCode's saved
   `Always allow` grants for the exact current project. The app resolves that
   project through `project.current`, lists grants through
   `v2.permission.saved.list`, and revokes one server-issued grant ID through
   `v2.permission.saved.remove` only after explicit confirmation. Loading and
   mutation both wait for the wake-reconciled repository; an unavailable older
   endpoint stays scoped to this screen instead of breaking Settings.
9. **Mobile MCP OAuth completion:** remote MCP authorization no longer stops
   after opening the phone browser. Mobile retains OpenCode's `oauthState`,
   discovers only an explicit HTTP loopback redirect from the validated HTTPS
   authorization URL, listens on that phone-local callback, rejects mismatched
   state, and forwards the code through generated `mcp.auth.callback`. A flat
   pending row exposes manual callback-URL/code entry when the port is occupied
   or a provider uses a custom redirect, plus cancellation through generated
   `mcp.auth.remove`. The listener is bounded to loopback and ten minutes, and
   route disposal cleans up the server-side pending transport. The blocking
   `mcp.auth.authenticate` endpoint remains hidden because it opens a browser on
   the OpenCode host and is correct for same-host desktop clients, not Android.
10. **Deferred by owner:** agent/session trees, a Traycer-style task cockpit, and
   worktree lifecycle are deliberately not the current implementation lane.

## Deliberate non-goals

The TUI control API should not be mirrored button-for-button. Mobile should own
its native navigation, pickers, prompts, and notifications. Arbitrary global
config mutation, sync takeover, raw patch apply, and destructive worktree
operations also stay hidden until they have precise
confirmation, recovery, and version-compatibility contracts.
