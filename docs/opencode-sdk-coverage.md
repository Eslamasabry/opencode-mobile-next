# OpenCode Dart SDK coverage

Audit date: 2026-08-28

Authoritative inputs:

- generated SDK under `packages/opencode_sdk/lib/src/api/`
- source contract `contracts/opencode-openapi-03bba464.json`
- production call sites under `lib/`

The SDK exposes **188 generated HTTP operations** across 32 API groups. The app
currently calls **93 generated operations directly**. It also uses a set of
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
| Control | `authSet`, `authRemove`, `appLog` | - | - |
| Control plane | `experimentalControlPlaneMoveSession` | - | - |
| Event | - | `eventSubscribe` through the shared `/event` SSE transport | Generated event transport is unused |
| Events v2 | - | Some v2-shaped events are reduced from the legacy stream | `v2EventSubscribe` |
| Experimental | `experimentalResourceList`, global session list, Console org list/switch, worktree create/list/remove/reset | - | capabilities, console state, background sessions, tool IDs/list |
| File | list, read, filename search, text search, `findSymbols` | - | `status` is declared but its OpenCode 1.18.23 handler is a stub that always returns an empty list |
| Filesystem v2 | - | - | `v2FsFind`, `v2FsList`, `v2FsRead` |
| Global | `globalConfigGet`, `globalConfigUpdate`, `globalUpgrade` | health, update events through the shared SSE runtime | dispose |
| Instance | `instanceDispose`, `vcsDiff`, `vcsGet`, `vcsStatus`, `lspStatus`, `formatterStatus` | - | agents, skills, command list, path, VCS raw diff/apply |
| Integrations | list, key connect, OAuth start, attempt status, complete, cancel | provider runtime refresh after confirmed completion | - |
| MCP | status, connect, disconnect, auth start/callback/remove | - | runtime-only add and same-host auth authenticate |
| Messages v2 | - | legacy session message list | `v2SessionMessages` |
| Models v2 | `v2ModelList` | - | - |
| OpenCode HTTP v2 | agent list, credential remove | - | credential update, health, location |
| Permission | list, reply, deprecated session reply fallback | - | - |
| Permissions v2 | request list, session reply, saved permission list/remove | - | per-session create/get/list |
| Project | `projectList`, `projectDirectories`, current project, Git init | - | update |
| Project copy | - | - | generated name, create, refresh, remove |
| Provider | - | provider list and configured-provider fallback | auth methods and OAuth authorize/callback |
| Providers v2 | provider list | - | provider get |
| PTY | create, list, update, remove, shells, connect token | WebSocket connect uses the guarded ticket and OpenCode cursor resume | get, direct connect, and all duplicate v2 PTY operations |
| Question | list, reply, reject | - | - |
| Reference | `v2ReferenceList` | - | References can be copied standalone or added to the active composer as OpenCode directory parts |
| Session | list, create, get, delete, update/rename, status, messages, children, todos, diff, fork, revert, unrevert, share, unshare, summarize, async prompt including native agent mention parts and synthetic location reminder, command, abort | shell | init endpoint, delete/update part, delete/get one message, synchronous prompt |
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
   Current-project metadata distinguishes an uninitialized folder from a clean
   Git repository without guessing from an empty status response. The native
   surface offers `project.git.init` only after explicit confirmation, sends
   the exact directory/workspace context, requires the returned project to
   confirm `vcs: git`, then refreshes all health sections. Older servers that
   cannot provide current-project metadata retain their existing VCS status
   without being mislabeled as non-Git.
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
10. **Server-backed default shell:** Settings now lists the actual shells from
   generated `pty.shells` and updates the global OpenCode `shell` configuration
   used by new terminals and compatible shell commands. Duplicate shell names
   retain their full paths, terminal-only shells are identified truthfully, and
   Automatic removes the override. Mobile follows the upstream web client by
   using `global.config.get/update`: live OpenCode `1.18.23` proved the scoped
   `config.update` route writes a legacy project `config.json` that its current
   loader does not read. Every mutation is read back from global config before
   mobile claims success, and shell actions use the wake-reconciled repository.
11. **Generated catalog transport:** detailed providers, models, and agents now
   use `v2.provider.list`, `v2.model.list`, and `v2.agent.list` rather than three
   handwritten `/api/*` parsers. The SDK generator now protects the canonical
   v2 `ModelCapabilities` component from an OpenAPI Generator inline-name
   collision that previously replaced its `tools`/`input`/`output` shape with
   the legacy model schema and made valid OpenCode `1.18.23` responses fail
   deserialization. Detailed v2 rows still only enrich the authoritative
   connected-provider catalog, so a location that reports only Zen cannot hide
   a connected Z.AI plan. The merge also retains richer reasoning, attachment,
   tool, and variant metadata from that connected-provider payload. If an older
   server cannot satisfy the strict v2 envelope, the controller keeps its
   existing basic provider/agent catalog instead of clearing the selector.
12. **All-project session finder:** Workspace and `/sessions` now open a native
   root-session finder backed by `experimental.session.list`. Title search runs
   on the OpenCode server across every project rather than filtering only the
   selected location. Results are cursor-paginated, archived sessions are an
   explicit opt-in, and child/subagent sessions stay out of this navigation
   surface. Opening a result switches to its exact directory/workspace before
   hydrating the chat. The finder re-queries through the replacement repository
   after Android wake, while unsupported older servers keep the error scoped to
   this screen. Live OpenCode `1.18.23` proved root filtering, search, archive
   inclusion, cross-location opening, and an external session appearing after
   background/resume.
13. **Failure containment and explicit diagnostics:** Flutter framework,
   platform-dispatcher, widget-build, and bootstrap failures now enter one
   bounded, process-local ring instead of leaving Android with a blank startup
   window or raw exception surface. Reports redact credential-like fields,
   authorization headers, URL credentials/query data, and long token-like
   values; they never collect chat messages or file contents. Settings and
   `/debug` show the exact retained entries with copy and clear controls.
   Nothing is persisted or uploaded automatically. Only an explicit Send action
   posts the currently visible redacted snapshot through generated `app.log`
   with the selected directory/workspace context.
14. **Remote server upgrades:** Settings retains OpenCode's authoritative
   `installation.update-available` semantic version and offers the generated
   `global.upgrade` action only for that exact target. It confirms the server
   profile, current and target versions, detected host-side installation, and
   restart requirement before writing. A successful response is not mislabeled
   as a running upgrade: the app retains the installed version until a later
   health or `server.connected` response proves that process is actually
   running it. Servers without an update event keep the external command path,
   and managed loopback Termux profiles keep their safer native setup flow.
15. **Native worktree lifecycle:** Workspace now opens one flat, phone-first
   Worktrees surface for the selected Git project. Create uses the generated
   `worktree.create` contract and retains its returned name, branch, and exact
   directory while OpenCode prepares files and startup tasks. A separate
   lifecycle-owned global event stream forwards only matching
   `worktree.ready`/`worktree.failed` truth, including the outer directory and
   project identity; it cannot mutate chat state or connection health.
   Listing still calls generated `worktree.list`, then prefers Git-derived
   `project.directories` entries when available so stale sandbox aliases from
   older OpenCode metadata do not become duplicate rows. Reset and remove first
   verify generated VCS status. Reset explicitly warns that tracked, untracked,
   ignored, and submodule changes will be destroyed. Remove requires the exact
   worktree name and switches an active worktree to the primary directory before
   deleting its directory and branch. The primary directory is never offered a
   destructive action. Older servers keep a scoped unsupported-state error.
16. **Subagent session navigation:** Chat's Session views now opens a flat
   parent/children surface backed by generated `session.children`. It keeps
   child sessions out of the ordinary chat list while making every delegated
   transcript reachable, including children missing from the location's cached
   root-session list. A child transcript has a persistent parent/all-subagents
   strip with its sibling position, so mobile does not hide the route back to
   the main task. The primary agent selector also excludes `mode: subagent`
   entries, matching OpenCode's own distinction between the active primary
   agent and subagents invoked for delegated work.
17. **Native delegation and configured defaults:** Composer tools now places
   Commands and Delegate in adjacent tabs. Delegate is populated only by
   visible `mode: subagent` definitions returned by the connected OpenCode
   server. Selecting or typing an `@agent` emits the generated async-prompt
   agent part with exact UTF-16 source offsets; no subagent inventory is
   hardcoded. The model and primary-agent defaults come from generated
   `config.get` at the active location, ahead of provider-list order. A valid
   explicit user model remains pinned, while an older automatic fallback is
   migrated to the current project default. Short and expanded reasoning use
   the same Markdown-aware renderer as assistant text while retaining their
   distinct muted presentation.
18. **Deferred by owner:** a Traycer-style task cockpit remains outside the
   current implementation lane.

## Deliberate non-goals

The TUI control API should not be mirrored button-for-button. Mobile should own
its native navigation, pickers, prompts, and notifications. Arbitrary global
config mutation, sync takeover, and raw patch apply remain hidden. Destructive
worktree operations are exposed only through the guarded native lifecycle
above; they are not generic command shortcuts.
