# OpenCode 2 port matrix

Inventory date: 2026-08-29. Branch: `production/android-release-hardening`.

Authoritative inputs:

- v1 API layer: `lib/api/opencode_api.dart`, `lib/api/product_repository.dart`,
  `lib/api/models.dart`, `lib/api/sse.dart`, `lib/api/server_probe.dart`,
  `lib/api/mcp_oauth.dart`
- v1 contract: `contracts/opencode-openapi-03bba464.json` (188 ops; includes a
  beta "v2 compatibility" surface under `/api/*` that the app already partly uses)
- v2 contract: `contracts/opencode2-openapi-beta-18600.json` (server
  v0.0.0-beta-18600; 135 ops across 114 paths, all under `/api/*`; a strict
  superset of the earlier beta-17823 capture — +19 ops: the
  `/api/experimental/persistent-pty` group and per-session terminal routes,
  `v2.project.update`, `v2.workspace.create/destroy`, `v2.session.stats`,
  `v2.session.view`, `v2.session.messageUpdate`, `v2.vcs.branches`,
  `v2.credential.activate`)
- Existing coverage audit: `docs/opencode-sdk-coverage.md`

Scope note: the v1 contract's `v2.*` compat operations are **not** identical to
the OpenCode 2 spec. Several were renamed or restructured between the compat
surface and beta-18600 (questions became forms, integration attempts became
integration-scoped, `directory`/`workspace` query pairs became a single
`location` parameter on most reads). Every row below maps against beta-18600.

---

## 1. App feature → v1 usage map

Line numbers refer to the declaration site of each method. `OpenCodeApi` =
`lib/api/opencode_api.dart`; `Repo` = `SdkProductRepository` in
`lib/api/product_repository.dart`. Consumer call sites are listed where they
anchor a feature.

### Chat / prompting

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `OpenCodeApi.promptAsync` | opencode_api.dart:334 | `POST /session/{id}/prompt_async` (`session.prompt_async`) | chat_screen.dart:968,1718; connection.dart:2268 (offline-queue flush) |
| `OpenCodeApi.shell` | opencode_api.dart:385 (handwritten Dio; generated request omits thinking variant) | `POST /session/{id}/shell` (`session.shell`) | chat_screen.dart:1842 |
| `OpenCodeApi.slashCommand` | opencode_api.dart:411 | `POST /session/{id}/command` (`session.command`) | chat_screen.dart:1081; library/commands_screen.dart:183 |
| `OpenCodeApi.abort` | opencode_api.dart:437 | `POST /session/{id}/abort` (`session.abort`) | chat_screen.dart:1256 |
| `OpenCodeApi.messages` | opencode_api.dart:292 | `GET /session/{id}/message` (`session.messages`) | chat_screen.dart:662,2893; session_context_screen.dart:220 |
| `Repo.deleteMessage` | product_repository.dart:2626 | `DELETE /session/{id}/message/{messageID}` (`session.deleteMessage`) | chat_screen.dart:1543 |
| `Repo.forkSession` | product_repository.dart:2610 | `POST /session/{id}/fork` (`session.fork`) | chat_screen.dart:1325,1592 |
| `Repo.revertSession` / `Repo.restoreSession` | product_repository.dart:2643 / 2654 | `POST /session/{id}/revert` / `POST /session/{id}/unrevert` | chat_screen.dart:1656,1666 |
| `Repo.compactSession` | product_repository.dart:2664 | `POST /session/{id}/summarize` (`session.summarize`) | chat_screen.dart:1620 |
| `Repo.shareSession` / `unshareSession` | product_repository.dart:2574 / 2585 | `POST /session/{id}/share` / `DELETE /session/{id}/share` | chat_screen.dart:1277,1308 |
| `OpenCodeApi.todos` | opencode_api.dart:451 | `GET /session/{id}/todo` (`session.todo`) | chat/session_sheets.dart:26 |
| `Repo.loadChatDefaults` | product_repository.dart:1890 | `GET /config` (`config.get`) | connection.dart:810 |

### Session list, Mission Control, home

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `OpenCodeApi.sessions` | opencode_api.dart:144 | `GET /session` (`session.list`) | connection.dart:1959 |
| `OpenCodeApi.sessionStatuses` | opencode_api.dart:260 | `GET /session/status` (`session.status`) | connection.dart:1970,2084 |
| `OpenCodeApi.createSession` | opencode_api.dart:174 | `POST /session` (`session.create`) | connection.dart:2338; chat_screen.dart:2253; chat/sessions_tab.dart:13; workspace_screen.dart:186 |
| `OpenCodeApi.renameSession` | opencode_api.dart:216 | `PATCH /session/{id}` (`session.update`) | connection.dart:2341; chat_screen.dart:2534; sessions_tab.dart:154; workspace_screen.dart:573 |
| `OpenCodeApi.deleteSession` | opencode_api.dart:201 | `DELETE /session/{id}` (`session.delete`) | connection.dart:2345; chat_screen.dart:2895; sessions_tab.dart:180; workspace_screen.dart:533 |
| `Repo.archiveSession` | product_repository.dart:2595 (sessionUpdate with `time.archived`) | `PATCH /session/{id}` | workspace_screen.dart:529 |
| `Repo.getSessionDetails` | product_repository.dart:1420 | `GET /session/{id}` (`session.get`) | chat_screen.dart:2714; session_relations_screen.dart:56,61 |
| `Repo.listSessionChildren` | product_repository.dart:1435 | `GET /session/{id}/children` (`session.children`) | session_relations_screen.dart:62 |

Mission Control (`mission_control_screen.dart`) and home (`home_screen.dart`)
render entirely from `AppConnection` state — sessions, statuses, permission and
question maps hydrated by the calls above and by SSE.

### Model & agent picker

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `OpenCodeApi.providers` | opencode_api.dart:857 (handwritten, with fallback) | `GET /provider` (`provider.list`) | connection.dart:817 |
| `OpenCodeApi.configuredProviders` | opencode_api.dart:870 | `GET /config/providers` (`config.providers`) | connection.dart:792 (and providers() fallback) |
| `OpenCodeApi.agents` | opencode_api.dart:875 (handwritten) | `GET /agent` (`app.agents`) | connection.dart:818; chat/command_launcher.dart:207 |
| `Repo.loadCatalog` | product_repository.dart:1916 (parallel `v2ProviderList` + `v2ModelList` + `v2AgentList`) | `GET /api/provider`, `GET /api/model`, `GET /api/agent` (v1 compat) | library/catalog_screen.dart:34 |

Model/agent/variant selection is client-side state; v1 sends it **per prompt**
inside `promptAsync`/`shell`/`slashCommand` bodies.

### Permissions & notification actions

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `OpenCodeApi.pendingPermissions` | opencode_api.dart:529 | `GET /permission` (`permission.list`) | connection.dart:1612 |
| `OpenCodeApi.pendingPermissionsV2` | opencode_api.dart:564 | `GET /api/permission/request` (compat `v2.permission.request.list`) | connection.dart:1619 |
| `OpenCodeApi.respondPermission` | opencode_api.dart:696 (falls back to deprecated `permission.respond` at :765 on 404/405) | `POST /permission/{requestID}/reply` (`permission.reply`); fallback `POST /session/{id}/permissions/{permissionID}` | connection.dart:1699 |
| `OpenCodeApi.respondPermissionV2` | opencode_api.dart:794 | `POST /api/session/{sID}/permission/{rID}/reply` (compat) | connection.dart:1693 |
| `Repo.listSavedPermissions` | product_repository.dart:2517 (projectCurrent + `v2PermissionSavedList`) | `GET /project/current` + `GET /api/permission/saved` | saved_permissions_screen.dart:57 |
| `Repo.removeSavedPermission` | product_repository.dart:2544 | `DELETE /api/permission/saved/{id}` | saved_permissions_screen.dart:133 |

Notification actions: `lib/background/live_background.dart` defines
`CodingAlertAction` (allow/deny/typed reply); `connection.dart:281`
`_handleCodingAlertAction` routes them into the same
`respondPermission`/`answerQuestion` paths. The background layer itself never
talks HTTP — it depends on connection state and SSE-derived alerts.

### Questions dialogs (Requests screen)

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `Repo.listQuestions` | product_repository.dart:2505 | `GET /question` (`question.list`) | connection.dart:1803 |
| `OpenCodeApi.pendingQuestionsV2` | opencode_api.dart:643 | `GET /api/question/request` (compat) | connection.dart:1811 |
| `Repo.answerQuestion` / `rejectQuestion` | product_repository.dart:2553 / 2564 | `POST /question/{id}/reply` / `/reject` | connection.dart:1874,1904; requests_screen.dart:459,494 |
| `OpenCodeApi.answerQuestionV2` / `rejectQuestionV2` | opencode_api.dart:824 / 842 | `POST /api/session/{sID}/question/{rID}/reply` / `/reject` (compat) | connection.dart:1872,1902 |

### Files browser & symbols

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `OpenCodeApi.listFiles` | opencode_api.dart:896 | `GET /file` (`file.list`) | files_screen.dart:214 |
| `OpenCodeApi.fileContent` | opencode_api.dart:945 | `GET /file/content` (`file.read`) | files_screen.dart:1053; chat_screen.dart:2796 |
| `OpenCodeApi.findFile` | opencode_api.dart:993 | `GET /find/file` (`find.files`) | files_screen.dart:261 |
| `OpenCodeApi.findText` | opencode_api.dart:1013 | `GET /find` (`find.text`) | **no consumer call site found** (declared but unused) |
| `Repo.findWorkspaceSymbols` | product_repository.dart:1719 | `GET /find/symbol` (`find.symbols`) | files_screen.dart:350 |
| `Repo.listFileStatuses` | product_repository.dart:1663 | `GET /vcs/status` (`vcs.status`) | files_screen.dart:299 |

### Diff review

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `OpenCodeApi.diff` | opencode_api.dart:486 | `GET /session/{id}/diff` (`session.diff`) | chat/session_sheets.dart:127; chat_screen.dart:2743 |
| `Repo.listVcsDiffs` | product_repository.dart:1045 | `GET /vcs/diff` (`vcs.diff`, mode=git\|branch) | chat_screen.dart:2750,2757; files_screen.dart:436 (feeds review_workspace.dart) |

### Terminal / PTY

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `Repo.listTerminals` | product_repository.dart:1741 | `GET /pty` (`pty.list`) | terminal_screen.dart:108 |
| `Repo.createTerminal` | product_repository.dart:1799 | `POST /pty` (`pty.create`) | terminal_screen.dart:130 |
| `Repo.renameTerminal` / `resizeTerminal` | product_repository.dart:1814 / 1825 | `PUT /pty/{ptyID}` (`pty.update`) | terminal_screen.dart:205,725 |
| `Repo.removeTerminal` | product_repository.dart:1841 | `DELETE /pty/{ptyID}` (`pty.remove`) | terminal_screen.dart:241 |
| `Repo.connectTerminal` | product_repository.dart:1851 (`ptyConnectToken` then WebSocket to `/pty/{id}/connect` with cursor resume, `_IoTerminalChannel` at :2843) | `POST /pty/{ptyID}/connect-token` + WS `GET /pty/{ptyID}/connect` | terminal_screen.dart:565 |
| `Repo.loadTerminalShellSettings` | product_repository.dart:1751 (`globalConfigGet` + `ptyShells`) | `GET /global/config` + `GET /pty/shells` | settings/coding_settings_screen.dart:51 |
| `Repo.selectTerminalShell` | product_repository.dart:1779 (`globalConfigUpdate` + read-back) | `PATCH /global/config` | settings/coding_settings_screen.dart:123 |

### More hub / Library: providers, MCP, commands, tools, skills, references

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `Repo.listIntegrations` | product_repository.dart:2202 | `GET /api/integration` (compat `v2.integration.list`) | library/integrations_screen.dart:118 |
| `Repo.connectIntegrationKey` | product_repository.dart:2257 (`v2IntegrationConnectKey` + legacy `authSet`) | `POST /api/integration/{id}/connect/key` + `PUT /auth/{providerID}` | integrations_screen.dart:679 |
| `Repo.disconnectIntegration` | product_repository.dart:2280 (`authRemove` then `v2CredentialRemove`) | `DELETE /auth/{providerID}` + `DELETE /api/credential/{credentialID}` | integrations_screen.dart:590 |
| `Repo.startIntegrationOAuth` | product_repository.dart:2361 | `POST /api/integration/{id}/connect/oauth` | integrations_screen.dart:693 |
| `Repo.integrationOAuthStatus` | product_repository.dart:2396 | `GET /api/integration/attempt/{attemptID}` (compat) | integrations_screen.dart:730,766 |
| `Repo.completeIntegrationOAuth` | product_repository.dart:2425 | `POST /api/integration/attempt/{attemptID}/complete` (compat) | integrations_screen.dart:762 |
| `Repo.cancelIntegrationOAuth` | product_repository.dart:2437 | `DELETE /api/integration/attempt/{attemptID}` (compat) | integrations_screen.dart:798 |
| `Repo.refreshProviderRuntime` | product_repository.dart:2347 | `POST /instance/dispose` (`instance.dispose`, twice: scoped + unscoped) | integrations_screen.dart:784 |
| `Repo.listMcpServers` | product_repository.dart:2055 | `GET /mcp` (`mcp.status`) | integrations_screen.dart:88 |
| `Repo.listMcpResources` | product_repository.dart:2067 | `GET /experimental/resource` (`experimental.resource.list`) | integrations_screen.dart:103 |
| `Repo.connectMcp` / `disconnectMcp` | product_repository.dart:2089 / 2099 | `POST /mcp/{name}/connect` / `/disconnect` | integrations_screen.dart:143,136 |
| `Repo.startMcpAuthentication` | product_repository.dart:2109 | `POST /mcp/{name}/auth` (`mcp.auth.start`) | integrations_screen.dart:166 (loopback listener in `lib/api/mcp_oauth.dart`) |
| `Repo.completeMcpAuthentication` | product_repository.dart:2126 | `POST /mcp/{name}/auth/callback` (`mcp.auth.callback`) | integrations_screen.dart:251 |
| `Repo.cancelMcpAuthentication` | product_repository.dart:2144 | `DELETE /mcp/{name}/auth` (`mcp.auth.remove`) | integrations_screen.dart:171-292 |
| `Repo.addMcpServer` | product_repository.dart:2154 (config patch: `configGet`+`configUpdate` or `globalConfigGet`+`globalConfigUpdate`) | `GET/PATCH /config` or `/global/config` | mcp_setup_screen.dart:90 |
| `Repo.listCommands` | product_repository.dart:2447 | `GET /api/command` (compat `v2.command.list`) | library/commands_screen.dart:36; chat_screen.dart:1881 |
| `Repo.listSkills` | product_repository.dart:2466 | `GET /api/skill` (compat `v2.skill.list`) | library/skills_screen.dart:31 |
| `Repo.listReferences` | product_repository.dart:2486 | `GET /api/reference` (compat `v2.reference.list`) | library/references_screen.dart:38 |
| `Repo.loadExperimentalCapabilities` | product_repository.dart:1994 | `GET /experimental/capabilities` | tools_screen.dart:88 |
| `Repo.listCodingToolIDs` / `listCodingTools` | product_repository.dart:2014 / 2028 | `GET /experimental/tool/ids` / `GET /experimental/tool` | tools_screen.dart:86,81 |

### Settings / config, diagnostics, upgrade

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `OpenCodeApi.health` | opencode_api.dart:133 | `GET /global/health` (`global.health`) | connection.dart:716,2355,2403; settings_screen.dart:66; settings/server_settings_screen.dart:40 |
| `Repo.upgradeServer` | product_repository.dart:977 | `POST /global/upgrade` (`global.upgrade`) | settings/server_settings_screen.dart:121 |
| `Repo.writeClientLog` | product_repository.dart:1023 | `POST /log` (`app.log`) | app_diagnostics_screen.dart:40 |
| `Repo.loadVersionControlHealth` | product_repository.dart:1600 (`projectCurrent` + `vcsGet` + `vcsStatus`) | `GET /project/current`, `GET /vcs`, `GET /vcs/status` | project_health_screen.dart:128; session_destination_sheet.dart:133 |
| `Repo.initializeGitRepository` | product_repository.dart:1649 | `POST /project/git/init` | project_health_screen.dart:108 |
| `Repo.listLanguageServices` / `listFormatters` | product_repository.dart:1682 / 1701 | `GET /lsp` / `GET /formatter` | project_health_screen.dart:144,160 |

### Host management, projects, workspaces, worktrees

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `Repo.listProjects` | product_repository.dart:1071 | `GET /project` (`project.list`) | workspace_screen.dart:70; projects_screen.dart:53; session_destination_sheet.dart:113 |
| `Repo.renameProject` | product_repository.dart:1081 | `PATCH /project/{projectID}` (`project.update`) | projects_screen.dart:121 |
| `Repo.loadCurrentProject` | product_repository.dart:1104 | `GET /project/current` | connection.dart:623 (cold-connect location verification) |
| `Repo.listProjectDirectories` | product_repository.dart:1453 | `GET /project/{projectID}/directories` | session_destination_sheet.dart:172 |
| `Repo.listWorktrees` | product_repository.dart:1126 (`worktreeList` + `projectDirectories` preference) | `GET /experimental/worktree` | worktrees_screen.dart:54 |
| `Repo.createWorktree` | product_repository.dart:1178 | `POST /experimental/worktree` | worktrees_screen.dart:172 |
| `Repo.listWorktreeFileStatuses` | product_repository.dart:1203 | `GET /vcs/status` | worktrees_screen.dart:225 |
| `Repo.resetWorktree` / `removeWorktree` | product_repository.dart:1223 / 1242 | `POST /experimental/worktree/reset` / `DELETE /experimental/worktree` | worktrees_screen.dart:248,281 |
| `Repo.listWorkspaces` / `listManagedWorkspaces` | product_repository.dart:1261 / 1267 (via `_loadWorkspaces` :1349 = `experimentalWorkspaceList` + `experimentalWorkspaceStatus` :1361) | `GET /experimental/workspace` + `GET /experimental/workspace/status` | workspace_screen.dart:125; session_destination_sheet.dart:202; managed_workspaces_screen.dart:60 |
| `Repo.listWorkspaceAdapters` | product_repository.dart:1275 | `GET /experimental/workspace/adapter` | managed_workspaces_screen.dart:69 |
| `Repo.syncWorkspaceList` | product_repository.dart:1293 | `POST /experimental/workspace/sync-list` | managed_workspaces_screen.dart:92 |
| `Repo.createManagedWorkspace` / `removeManagedWorkspace` | product_repository.dart:1301 / 1339 | `POST /experimental/workspace` / `DELETE /experimental/workspace/{id}` | managed_workspaces_screen.dart:116,170 |

### Global sessions, steal / move / warp / org

| App call | Declared at | v1 endpoint | Called from |
|---|---|---|---|
| `Repo.listGlobalSessions` | product_repository.dart:1391 | `GET /experimental/session` (`experimental.session.list`) | global_sessions_screen.dart:90,123 |
| `Repo.moveSession` | product_repository.dart:1471 | `POST /experimental/control-plane/move-session` | connection.dart:2564 |
| `Repo.warpSession` | product_repository.dart:1487 | `POST /experimental/workspace/warp` | connection.dart:2589 |
| `Repo.startWorkspaceSync` | product_repository.dart:1504 | `POST /sync/start` (`sync.start`) | (steal preflight) |
| `Repo.stealSessionIntoWorkspace` | product_repository.dart:1516 | `POST /sync/steal` (`sync.steal`) | global_sessions_screen.dart:220 |
| `Repo.listConsoleOrganizations` | product_repository.dart:1531 | `GET /experimental/console/orgs` | session_destination_sheet.dart:477 |
| `Repo.switchConsoleOrganization` | product_repository.dart:1554 (+ `instanceDispose`) | `POST /experimental/console/switch` + `POST /instance/dispose` | connection.dart:2611; session_destination_sheet.dart:518 |
| `Repo.addSessionLocationReminder` | product_repository.dart:1577 (synthetic `sessionPromptAsync`) | `POST /session/{id}/prompt_async` | connection.dart:2571,2596 |

### Offline queue, background service & widget

- `lib/state/offline_queue.dart`: pure persistence (`SharedPreferences`); no
  HTTP. `QueuedPrompt` snapshots text/attachments/mentions/model/agent/variant —
  the exact argument list of `promptAsync`. Flushed at connection.dart:2268.
- `lib/background/live_background.dart`: platform channel only; alerts derive
  from SSE (`permission.*`, `question.*`, `session.idle`, `session.error`);
  actions route back through `connection.dart:281`.
- `lib/background/widget_snapshot.dart`: persistence of the session list into
  `SharedPreferences` for the Android widget; no HTTP.

### Server probe / first-run test connection

- `probeServerConnection` (`lib/api/server_probe.dart:37`): raw Dio
  `GET /global/health` with Basic auth, used by servers_screen.dart:633.

### Event streams (transport for everything live)

- `OpenCodeApi.openEventStream` (opencode_api.dart:104): `GET /event`,
  location-scoped, `data:`-only frames.
- `OpenCodeApi.openGlobalEventStream` (opencode_api.dart:119): `GET /global/event`,
  wraps events in `{directory, project, workspace, payload}`
  (`EventEnvelope.fromGlobalJson`, models.dart:849).
- Reconnect/backoff loop: `EventStream` (`lib/api/sse.dart:14`); note sse.dart:133
  explicitly discards `event:`/`id:`/`retry:` SSE fields.
- Dispatch: `AppConnection._onEvent` switch at `lib/state/connection.dart:1146`
  (see risk section for the full case list). Secondary consumer:
  worktrees_screen.dart:79 `_handleEvent` (worktree.ready/failed via the global
  stream, filtered in connection.dart:1094).

---

## 2. v1 endpoint → v2 equivalent table

Status legend: **direct** (rename of path only, same semantics) · **reshaped**
(same capability, changed path/params/body) · **replaced-by** (different
concept in v2) · **NONE** (no v2 equivalent found in beta-18600).

### Transport, health, events

| v1 endpoint (as used) | v2 path / operationId | Status | Notes |
|---|---|---|---|
| `GET /global/health` | `GET /api/health` · v2.health.get | direct | Probe + settings + reconnect checks. |
| `GET /event` | `GET /api/event` · v2.event.subscribe | **reshaped** | v2 frames carry `id`/`event`/`data`; `data` is `V2EventEncoded` = a JSON **string** (`contentMediaType: application/json`). No typed event union in the spec at all. sse.dart currently drops `event:` lines. |
| `GET /global/event` | `GET /api/event` | **replaced-by** | v2 has a single stream; no `{payload}` global envelope. Location filtering must move client-side or use query semantics not declared in the spec. |

### Sessions & messages

| v1 | v2 | Status | Notes |
|---|---|---|---|
| `GET /session` (session.list) | `GET /api/session` · v2.session.list | reshaped | Adds `limit`, `order`, `search`, `parentID`, `cursor`, `subpath`; location via `workspace`(`^wrk`)/`directory`/`project` params. |
| `POST /session` (session.create) | `POST /api/session` · v2.session.create | direct | |
| `GET /session/{id}` (session.get) | `GET /api/session/{id}` · v2.session.get | direct | |
| `DELETE /session/{id}` (session.delete) | `DELETE /api/session/{id}` · v2.session.remove | direct | |
| `PATCH /session/{id}` rename (session.update) | `POST /api/session/{id}/rename` · v2.session.rename | reshaped | Body `{title}` only. |
| `PATCH /session/{id}` archive (`time.archived`) | — | **NONE** | No archive operation in beta-18600 (`archived` may survive as data; no write op). workspace_screen archive action loses its backend. |
| `GET /session/status` (session.status) | `GET /api/session/active` · v2.session.active | replaced-by | Map-of-status polling → active-session list + events. |
| `GET /session/{id}/message` (session.messages) | `GET /api/session/{id}/message` · v2.message.list | reshaped | Pagination (`limit`/`order`/`cursor`); v2 message/part schema differs from v1 `{info, parts}` bundles. |
| `DELETE /session/{id}/message/{mid}` (session.deleteMessage) | — | **NONE** | v2 has message get/list, plus `PATCH /api/session/{id}/message/{mid}` (v2.session.messageUpdate, body `{content}`) — an edit, not a delete; no removal op. |
| `GET /session/{id}/children` (session.children) | `GET /api/session?parentID=` · v2.session.list | replaced-by | Children become a list filter. |
| `POST /session/{id}/prompt_async` (session.prompt_async) | `POST /api/session/{id}/prompt` · v2.session.prompt | **reshaped (major)** | v1 typed `parts[]` (text/file/agent-mention) + per-prompt `model`/`agent`/`variant` → v2 `{text, files, agents, skills, metadata, delivery, resume}`. Model/agent are now session state via v2.session.switchModel (`POST /api/session/{id}/model`) and v2.session.switchAgent (`POST /api/session/{id}/agent`). |
| `POST /session/{id}/command` (session.command) | `POST /api/session/{id}/command` · v2.session.command | reshaped | Body gains `files/agents/skills/delivery/resume`; `model` is a string. |
| `POST /session/{id}/shell` (session.shell, handwritten) | `POST /api/session/{id}/shell` · v2.session.shell | reshaped | v2 body is `{id, command}` — no agent/model/variant; the reason the handwritten route existed disappears. |
| `POST /session/{id}/abort` (session.abort) | `POST /api/session/{id}/interrupt` · v2.session.interrupt | direct (rename) | |
| `GET /session/{id}/todo` (session.todo) | — | **NONE** | No todo op in v2; todo truth presumably event/part-derived. |
| `GET /session/{id}/diff` (session.diff) | `GET /api/vcs/diff` · v2.vcs.diff | replaced-by | Session-scoped diff gone; only location-scoped VCS diff (`location`, `mode`, `context`). |
| `POST /session/{id}/fork` (session.fork) | `POST /api/session/{id}/fork` · v2.session.fork | reshaped | `{messageID}` → `{boundary}`. |
| `POST /session/{id}/revert` / `/unrevert` | `POST /api/session/{id}/revert/stage` / `/commit` / `/clear` · v2.session.revert.* | reshaped | One-shot revert → staged three-phase model. |
| `POST /session/{id}/share` / `DELETE /session/{id}/share` | — | **NONE** | No share/unshare in v2. |
| `POST /session/{id}/summarize` (session.summarize) | `POST /api/session/{id}/compact` · v2.session.compact | reshaped | Body `{id, delivery}`; no providerID/modelID pair. |
| synthetic reminder prompt (prompt_async) | `POST /api/session/{id}/synthetic` · v2.session.synthetic | replaced-by | Dedicated synthetic-message op fits `addSessionLocationReminder` better than a real prompt. |

### Permissions & questions

| v1 | v2 | Status | Notes |
|---|---|---|---|
| `GET /permission` (permission.list) | `GET /api/permission/request` · v2.permission.request.list | reshaped | `{location, data}` envelope; `action`/`resources`/`save` field names (the app's V2 parser at opencode_api.dart:564 already speaks this). |
| `GET /api/permission/request` (compat) | same · v2.permission.request.list | direct | Param moves from `location[directory]`/`location[workspace]` pair to `location`. |
| `POST /permission/{rid}/reply` (permission.reply) | `POST /api/session/{sid}/permission/{rid}/reply` · v2.session.permission.reply | reshaped | Now session-scoped; body `{reply, message}`. |
| `POST /session/{sid}/permissions/{pid}` (deprecated permission.respond fallback) | same as above | replaced-by | Fallback path can be deleted. |
| `POST /api/session/{sid}/permission/{rid}/reply` (compat) | same · v2.session.permission.reply | direct | |
| `GET /api/permission/saved` (compat, projectID param) | `GET /api/permission/saved` · v2.permission.saved.list | direct | Check param shape (`location` vs `projectID`). |
| `DELETE /api/permission/saved/{id}` (compat) | same · v2.permission.saved.remove | direct | |
| `GET /question` (question.list) | `GET /api/form/request` · v2.form.request.list | **replaced-by (forms)** | Questions are gone as a concept. |
| `GET /api/question/request` (compat) | `GET /api/form/request` | replaced-by | |
| `POST /question/{id}/reply` / compat session question reply | `POST /api/session/{sid}/form/{formID}/reply` · v2.session.form.reply | **replaced-by** | v1 `answers: string[][]` → v2 form-field reply schema; also v2.session.form.get/state for hydration. |
| `POST /question/{id}/reject` / compat reject | `POST /api/session/{sid}/form/{formID}/cancel` · v2.session.form.cancel | replaced-by | |

### Catalog, config, auth

| v1 | v2 | Status | Notes |
|---|---|---|---|
| `GET /provider` (provider.list, handwritten) | `GET /api/provider` · v2.provider.list | direct | Drop handwritten parser. |
| `GET /config/providers` (config.providers fallback) | — | **NONE** | Old-server fallback; delete in v2 port. |
| `GET /agent` (app.agents, handwritten) | `GET /api/agent` · v2.agent.list | direct | |
| `GET /api/provider`, `/api/model`, `/api/agent` (compat catalog trio) | same paths · v2.provider.list / v2.model.list / v2.agent.list | direct | `location` param is now a union type; v2 adds v2.model.default (`GET /api/model/default`) which can replace config-derived model default. |
| `GET /config` (config.get) | `GET /api/config` · v2.config.get | direct | Read-only in v2. |
| `PATCH /config` (config.update — only used for MCP add) | — | **NONE** (general) / replaced-by v2.mcp.add for the MCP case | `PUT /api/mcp/{server}` with `{config}` body is now the sanctioned persistent MCP write. |
| `GET /global/config` / `PATCH /global/config` | — | **NONE** | Shell settings feature loses read & write; no global config surface in v2. |
| `PUT /auth/{providerID}` (auth.set) | `POST /api/integration/{id}/connect/key` · v2.integration.connect.key | replaced-by | Legacy credential write folds into integrations. |
| `DELETE /auth/{providerID}` (auth.remove) | `DELETE /api/credential/{credentialID}` · v2.credential.remove | replaced-by | |
| `DELETE /api/credential/{credentialID}` (compat) | same · v2.credential.remove | direct | v2 adds v2.credential.update (PATCH). |
| `GET /api/command` / `/api/skill` / `/api/reference` (compat) | same · v2.command.list / v2.skill.list / v2.reference.list | direct | v2 adds `POST /api/session/{id}/skill` (v2.session.skill) for invocation. |

### Integrations (provider OAuth)

| v1 | v2 | Status | Notes |
|---|---|---|---|
| `GET /api/integration` (compat) | same · v2.integration.list | direct | |
| `POST /api/integration/{id}/connect/key` | same · v2.integration.connect.key | direct | Body adds `answer`. |
| `POST /api/integration/{id}/connect/oauth` | same · v2.integration.oauth.connect | direct | |
| `GET /api/integration/attempt/{attemptID}` | `GET /api/integration/{integrationID}/connect/oauth/{attemptID}` · v2.integration.oauth.status | **reshaped** | Attempt routes are now integration-scoped: the app must retain the integrationID alongside the attemptID (it currently persists only the attempt). |
| `POST /api/integration/attempt/{attemptID}/complete` | `POST /api/integration/{integrationID}/connect/oauth/{attemptID}/complete` · v2.integration.oauth.complete | reshaped | Same integrationID requirement. |
| `DELETE /api/integration/attempt/{attemptID}` | `DELETE /api/integration/{integrationID}/connect/oauth/{attemptID}` · v2.integration.oauth.cancel | reshaped | |
| `POST /instance/dispose` (refreshProviderRuntime, org switch) | `DELETE /api/debug/location` · v2.debug.location.evict (nearest) | **NONE (direct)** | Debug-tagged eviction is the only analog; treat runtime refresh as a design question, not a rename. |

### MCP

| v1 | v2 | Status | Notes |
|---|---|---|---|
| `GET /mcp` (mcp.status) | `GET /api/mcp` · v2.mcp.list | direct | |
| `POST /mcp/{name}/connect` / `/disconnect` | `POST /api/mcp/{server}/connect` / `/disconnect` · v2.mcp.connect/disconnect | direct | |
| config-patch MCP add (`PATCH /config` or `/global/config`) | `PUT /api/mcp/{server}` · v2.mcp.add | replaced-by | v2 also has `DELETE /api/mcp/{server}` (v2.mcp.remove) — new capability for the setup screen. |
| `GET /experimental/resource` (experimental.resource.list) | `GET /api/mcp/resource` · v2.mcp.resource.catalog | direct (rename) | |
| `POST /mcp/{name}/auth` (mcp.auth.start) | — | **NONE** | No MCP auth ops in beta-18600. The whole mobile loopback OAuth flow (`lib/api/mcp_oauth.dart`, integrations_screen pending rows) has no v2 backend; auth presumably folds into connect or moves server-side. |
| `POST /mcp/{name}/auth/callback` (mcp.auth.callback) | — | **NONE** | |
| `DELETE /mcp/{name}/auth` (mcp.auth.remove) | — | **NONE** | |

### Files, search, VCS, project health

| v1 | v2 | Status | Notes |
|---|---|---|---|
| `GET /file` (file.list) | `GET /api/fs/list` · v2.fs.list | reshaped | Param/response shape differs; `location` param. |
| `GET /file/content` (file.read) | `GET /api/fs/read/*` · v2.fs.read | reshaped | Path moves into the URL wildcard; must verify binary/base64/MIME metadata survives. |
| `GET /find/file` (find.files) | `GET /api/fs/find` · v2.fs.find | reshaped | |
| `GET /find` (find.text) | — | **NONE** | Declared at opencode_api.dart:1013 but has no consumer — drop in port. |
| `GET /find/symbol` (find.symbols) | — | **NONE** | Files→Symbols tab loses its backend. |
| `GET /vcs` (vcs.get) | `GET /api/vcs` · v2.vcs.get | direct | |
| `GET /vcs/status` (vcs.status) | `GET /api/vcs/status` · v2.vcs.status | direct | |
| `GET /vcs/diff` (vcs.diff) | `GET /api/vcs/diff` · v2.vcs.diff | direct | Params now `location`/`mode`/`context`. |
| `GET /lsp` (lsp.status) | — | **NONE** | Project health LSP section. |
| `GET /formatter` (formatter.status) | — | **NONE** | Project health formatter section. |
| `POST /project/git/init` (project.initGit) | — | **NONE** | Git-init affordance loses backend. |

### Projects, worktrees, workspaces, global sessions

| v1 | v2 | Status | Notes |
|---|---|---|---|
| `GET /project` (project.list) | `GET /api/project` · v2.project.list | direct | |
| `GET /project/current` (project.current) | `GET /api/project/current` · v2.project.current | direct | Also `GET /api/location` (v2.location.get) for active location truth. |
| `PATCH /project/{id}` (project.update, rename) | `PATCH /api/project/{projectID}` · v2.project.update | direct | Body `{name, icon, commands}` — same rename semantics, plus icon/commands the app does not use. |
| `GET /project/{id}/directories` (project.directories) | `GET /api/worktree/{projectID}` · v2.worktree.list | replaced-by | Directory inventory is now the worktree list. |
| `GET /experimental/worktree` (worktree.list) | `GET /api/worktree/{projectID}` · v2.worktree.list | reshaped | Project-scoped path. |
| `POST /experimental/worktree` (worktree.create) | `POST /api/worktree/{projectID}` · v2.worktree.create | reshaped | Body `{strategy, from, directory, name}`. |
| `DELETE /experimental/worktree` (worktree.remove) | `DELETE /api/worktree/{projectID}` · v2.worktree.remove | reshaped | |
| `POST /experimental/worktree/reset` (worktree.reset) | `POST /api/worktree/{projectID}/refresh` · v2.worktree.refresh (nearest) | **NONE (direct)** | Refresh is not documented as destructive reset; verify before wiring the destructive UI to it. |
| `POST /experimental/workspace` (workspace.create) | `POST /api/workspace` · v2.workspace.create | **reshaped** | v1 body (adapter/branch/extra) → v2 `{id, provider}`; verify how adapters map onto `provider`. |
| `DELETE /experimental/workspace/{id}` (workspace.remove) | `DELETE /api/workspace/{workspaceID}` · v2.workspace.destroy | direct (rename) | |
| `GET /experimental/workspace` + `/status` + `/adapter`, `POST .../sync-list`, `POST .../warp` | — | **NONE** | Workspace **list, status, adapter discovery, sync-list, and warp** have no v2 ops in beta-18600. Sessions carry `workspace` (`^wrk`) IDs in v2 params and create/destroy exist, but managed_workspaces_screen's inventory and the warp flow have no backend. |
| `GET /experimental/session` (experimental.session.list) | `GET /api/session` · v2.session.list (`search`, `cursor`, `project`) | replaced-by | Global finder becomes the ordinary list without a location filter. |
| `POST /experimental/control-plane/move-session` | `POST /api/session/{id}/move` · v2.session.move | replaced-by | |
| `POST /sync/start` (sync.start) | — | **NONE** | |
| `POST /sync/steal` (sync.steal) | `POST /api/session/import` / `GET /api/session/{id}/export` (nearest) | **NONE (direct)** | Steal/continue-here likely reimplements over export+import+move. |
| `GET /experimental/console/orgs` / `POST /experimental/console/switch` | — | **NONE** | `/org` switching loses backend. |
| `GET /experimental/capabilities` | `GET /api/server` · v2.server.get (nearest) | **NONE (direct)** | Verify what server info v2 exposes. |
| `GET /experimental/tool/ids` / `GET /experimental/tool` | — | **NONE** | Tools screen inventory loses backend. |

### PTY, shell ops, global admin

| v1 | v2 | Status | Notes |
|---|---|---|---|
| `GET /pty` (pty.list) | `GET /api/pty` · v2.pty.list | direct | |
| `POST /pty` (pty.create) | `POST /api/pty` · v2.pty.create | direct | `location` param; body adds `env`. |
| `PUT /pty/{id}` (pty.update) | `PUT /api/pty/{ptyID}` · v2.pty.update | direct | |
| `DELETE /pty/{id}` (pty.remove) | `DELETE /api/pty/{ptyID}` · v2.pty.remove | direct | |
| `POST /pty/{id}/connect-token` + WS `GET /pty/{id}/connect` | `POST /api/pty/{ptyID}/connect-token` + `GET /api/pty/{ptyID}/connect` · v2.pty.connect.token / v2.pty.connect | direct | Verify the binary cursor-control frames survived. |
| `GET /pty/shells` (pty.shells) | — | **NONE** | Shell settings picker loses inventory; v2 `shell` tag (`/api/shell*` v2.shell.*) is one-shot command execution, not shell discovery. |
| `POST /global/upgrade` (global.upgrade) | — | **NONE** | Remote upgrade action loses backend. |
| `POST /log` (app.log) | — | **NONE** | Diagnostics send loses backend. |

---

## 3. v2 surface the app does not use yet (future-feature candidates)

- **inbox / steer** (`v2.session.inbox.list/cancel/queue/steer`): queued and steerable pending work per session — natural fit for Mission Control actions.
- **wait** (`POST /api/session/{id}/wait`): server-side wait-for-idle; could replace status polling in the background service.
- **instructions** (`v2.session.instructions.entry.list/put/remove`): per-session instruction entries — a new chat settings sheet.
- **revert stage/commit** granularity (`v2.session.revert.*`): staged review-then-commit revert UI instead of the current one-shot revert.
- **import / export** (`v2.session.import`, `v2.session.export`): session backup, transfer between servers, and a principled replacement for sync.steal.
- **generate** (`POST /api/generate`, `POST /api/session/{id}/generate`): one-shot text generation (e.g. title suggestions, commit messages) without a chat session.
- **session context/stats** (`GET /api/session/{id}/context`): now presumably populated in v2 — could replace the message-derived context inspector.
- **session log** (`GET /api/experimental/session/{sessionID}/log`): raw session log viewer for diagnostics.
- **background** (`POST /api/session/{id}/background`) and **environment** (`PUT /api/session/{id}/environment`): background execution and per-session env.
- **switchModel / switchAgent as session state**: enables showing the server-authoritative current model per session (today it is client state).
- **model.default** (`GET /api/model/default`): server default model for the picker.
- **forms beyond questions** (`v2.session.form.create/get/state`): richer structured input dialogs than the v1 question flow.
- **permission create/get** (`v2.session.permission.create/get/list`): per-session hydration and race-safe refetch of a single request.
- **websearch** (`POST /api/websearch`, `GET /api/websearch/provider`): server-side web search — a candidate composer tool.
- **shell ops** (`v2.shell.create/get/list/output/remove/timeout`): one-shot command execution with captured output — could back a lighter "run command" affordance than a full PTY.
- **integration command connect** (`v2.integration.command.connect/status/cancel`): CLI-driven integration auth (e.g. `gh auth login`-style) — new to v2.
- **wellknown integration add** (`POST /api/experimental/integration/wellknown`): registering custom OAuth providers.
- **plugin list** (`GET /api/plugin`), **debug location** (`GET/DELETE /api/debug/location`), **migration status** (`GET /api/experimental/migration/v1`): diagnostics surfaces; migration status is directly useful during the port itself.
- **worktree refresh** (`POST /api/worktree/{projectID}/refresh`).
- **credential update / activate** (`PATCH /api/credential/{credentialID}`, `POST /api/credential/{credentialID}/activate`): rotate keys and switch the active credential without disconnect/reconnect.
- **session stats** (`GET /api/session/stats`: `from`/`to`/`project`/`timezone`/`tools`): usage analytics — a natural Mission Control or settings dashboard.
- **view tracking** (`POST /api/session/{id}/view`, body `{idle}`): tell the server a session is being viewed — could suppress duplicate notifications while the chat is open.
- **message update** (`PATCH /api/session/{id}/message/{mid}`, body `{content}`): server-side message editing, new in beta-18600.
- **vcs branches** (`GET /api/vcs/branches`: `search`/`limit`): branch inventory for worktree-create and diff-scope pickers.
- **persistent PTY** (`server.experimental.persistentPty.*`: `/api/experimental/persistent-pty/{ptyID}` get/update/remove/connect/connect-token/snapshot/handoff/shutdown, plus per-session `GET|POST /api/experimental/session/{sessionID}/terminal` and `.../terminal/read`): session-attached terminals that survive reconnects, with snapshot reads — a strong future upgrade for terminal_screen and for showing an agent's terminal inside chat.

---

## 4. Port order recommendation

**Phase 0 — dual-stack transport + detection (blocks everything).**
`OpenCodeApi` base URL handling, Basic auth (v2 spec declares no security
scheme — verify header behavior against a live server), the
`directory`/`workspace` query-pair → `location`/`workspace`/`directory` param
model, and server flavor detection (probe `/api/health` vs `/global/health`, and
use `GET /api/experimental/migration/v1`). Affects `opencode_api.dart`
constructor + `_query()` (lines 36–68), `server_probe.dart`, `profiles.dart`,
servers/host_management screens.

**Phase 1 — event stream.** sse.dart must parse `event:` + `id:` fields and
decode the double-encoded `data` string; `EventEnvelope`
(models.dart:827) and the `_onEvent` switch (connection.dart:1146) need a new
v2 event-name table (the spec provides none — must be captured from a live
server). Everything live (chat updates, permissions, statuses, background
alerts, widget) sits on this.

**Phase 2 — sessions + messages + prompt.** session list/create/get/delete/
rename, v2.message.list pagination, and the prompt rewrite: per-prompt
model/agent/variant becomes v2.session.switchModel/switchAgent + a
`{text, files, agents, skills}` body. Rework `promptAsync`, `shell`,
`slashCommand`, the offline queue flush (`QueuedPrompt` snapshots the old
argument shape), and abort→interrupt. Screens: chat_screen, sessions_tab,
workspace_screen, home/mission control, widget snapshot.

**Phase 3 — permissions + forms.** Permission request list/reply are nearly
compat-shaped already; the question→form replacement is a real feature rewrite
(requests_screen, chat dialogs, notification actions in live_background +
connection.dart:281). Saved permissions are direct.

**Phase 4 — files, VCS review, catalog.** fs.list/read/find, vcs.get/status/diff
(session diff UI collapses onto vcs.diff), provider/model/agent catalog
(mostly direct), config.get. Screens: files_screen, review_workspace,
session_sheets, catalog/library, project_health (minus LSP/formatter).

**Can lag (own screens, no cross-dependencies):** PTY/terminal (near-direct;
persistent-PTY is a later upgrade), MCP management (list/connect/disconnect
direct; add moves to v2.mcp.add; OAuth flow has no v2 backend — gate it),
integrations (path reshape: persist integrationID with the attempt), worktrees
(project-scoped reshape; vcs.branches improves the create flow), projects
(list/current/update direct).

**Needs product decisions before porting (no v2 backend):** workspace
list/status/adapter discovery + warp + sync/steal + console orgs
(global_sessions_screen "Continue here", session_destination_sheet,
managed_workspaces_screen — even though workspace create/destroy exist),
session share/archive, todos sheet, symbols tab, LSP/formatter health, tools
inventory, shell settings, remote upgrade, diagnostics send, git init,
MCP OAuth.

---

## 5. Risk list

1. **SSE event contract is opaque in v2.** `V2EventEncoded` is just a JSON
   string; beta-18600 declares zero event type names. The entire dispatch
   switch in `AppConnection._onEvent` (`lib/state/connection.dart:1146-1385`,
   ~30 cases: `server.connected`, `installation.*`, `session.created/updated/
   deleted/status/error/idle/compacted`, `message.updated/removed`,
   `permission.asked/v2.asked/updated/replied/v2.replied`, `question.*` (6),
   `pty.*` (4), `worktree.ready/failed`, `integration.connection.updated`,
   `catalog.updated`, `agent.updated`, `config.updated`) must be re-derived
   from a live v2 server or upstream source, not from the spec. sse.dart:133
   discarding `event:` lines is an immediate functional break if v2 puts the
   type there.

2. **Loss of the global event stream envelope.** `EventEnvelope.fromGlobalJson`
   (models.dart:849) and the dual-stream design in connection.dart:1049-1098
   (plus worktrees_screen's lifecycle stream) assume `{directory, project,
   workspace, payload}` wrapping. v2 has one `/api/event`; cross-location
   filtering logic must be rebuilt.

3. **Prompt pipeline shape.** `promptAsync` parts-building
   (opencode_api.dart:334-383), `PromptAttachment`/`PromptAgentMention`
   (models.dart), `QueuedPrompt` persistence (offline_queue.dart — stored JSON
   on user devices includes model/agent/variant that v2 no longer accepts
   per-prompt), and chat_screen's composer all encode the v1 parts union.
   Migration must also handle already-queued offline prompts.

4. **Message/part rendering.** `MessageInfo`, `Part`, `ToolState`, `Tokens`
   in models.dart (155-500) parse v1 wire JSON directly, and chat rendering,
   the context inspector (session_context_screen deriving token truth from
   message JSON), and diff counting all consume it. v2.message.list is
   paginated and reshaped; hydration logic in chat_screen.dart:662/2893
   assumes a full unpaginated list.

5. **Deep v1 response-shape coupling in state classes.**
   `PermissionRequest` triple-parser (legacy, v2-compat, event shapes —
   connection.dart:1387-1480 and opencode_api.dart:564-694),
   `PendingQuestion` (dead in v2 — forms), `Session` loose parsing,
   `ProvidersResponse` (handwritten `/provider` parser), and the tolerant
   old-server fallbacks throughout opencode_api.dart (the
   `_wasSuccessfulResponse` re-parse pattern) all assume v1 JSON. Decide
   whether the v2 port keeps any v1 fallback or becomes a clean second client.

6. **No v2 counterpart at all** for: workspace list/status/adapter
   discovery/warp/sync-steal/console orgs (create/destroy alone survived into
   beta-18600), MCP OAuth (`mcp_oauth.dart` + integrations_screen pending-auth
   UI), session share/unshare/archive/todo/deleteMessage, find.symbols, lsp/
   formatter status, tool.ids/tool.list, pty.shells + global config (shell
   settings), global.upgrade, app.log, project.initGit, instance.dispose.
   Each backs a visible screen or action listed in section 2; these need
   feature-flagging by server flavor, not silent 404s.

7. **Integration OAuth attempt identity.** v2 attempt routes require the
   `integrationID`; the app's pending-attempt persistence (IntegrationAuthLaunch
   retained attemptID/mode/expiry only) must start storing the integration ID
   or pending v1 attempts cannot be completed after upgrade.

8. **Wake-reconciliation coupling.** Nearly every action waits on the paired
   `OpenCodeApi`/`SdkProductRepository` swap (documented in
   docs/opencode-sdk-coverage.md item 6). The v2 client must preserve that
   atomically-swapped transport pattern or Android wake regressions return.

### Tally

- Distinct v1 operations the app actually uses: **~110** (103 via the generated
  SDK/product repository + 7 handwritten Dio routes).
- Direct or trivially renamed in v2 (beta-18600): **~40** (project.update and
  workspace.destroy joined this bucket at beta-18600).
- Reshaped (path/param/body changes, same capability): **~31** (workspace.create
  joined at beta-18600).
- Replaced by a different v2 concept (questions→forms, auth→credentials/
  integrations, session diff→vcs diff, status→active, children→list filter,
  config-patch MCP→mcp.add, control-plane move→session.move): **~14**.
- No v2 equivalent found: **~25** (concentrated in workspace inventory/
  sync/console, MCP auth, LSP/formatter/symbols, global admin,
  share/todo/archive).

---

## 6. Deferred seams (domain-interface extraction, 2026-08-29)

The `port/domain-interface` pass put `lib/domain/server_gateway.dart` between
consumers and the v1 client. These raw v1 accesses were deliberately left in
place rather than funneled, because funneling them needs real redesign:

- **SSE construction stays on the v1 factory seam.** `ConnectionController`
  still builds `EventStream` (lib/api/sse.dart) through its injected
  `EventStreamFactory`, which is typed on `OpenCodeApi` — that typedef is the
  test-injection seam (test/connection_sse_test.dart and friends fake it).
  The interface funnel exists — `EventGateway.openEventChannel` /
  `openGlobalEventChannel` on `ServerGateway` return a `LiveEventChannel` of
  parsed `EventEnvelope` objects — so a v2 gateway can supply its own channel,
  but rerouting the controller and its tests onto `EventGateway` is a
  follow-up.
- **Event payload shapes.** `ConnectionController._onEvent`
  (lib/state/connection.dart) still decodes v1 JSON payloads out of
  `EventEnvelope.properties` for its ~30 cases; only the envelope is
  protocol-neutral.
- **PTY WebSocket URL.** `SdkProductRepository.connectTerminal` derives the
  socket URL from the v1 SDK client's Dio `baseUrl` — a raw transport
  reach-around inside the v1 implementation of `TerminalGateway`.
- **Wiring stays concrete by design.** `OpenCodeApiFactory`,
  `ProductRepositoryFactory` (`SdkProductRepository(api.sdkClient)`), and the
  event-stream factories keep v1 types; these are the construction sites where
  the protocol switch will be introduced.
- **First-run probe.** `probeServerConnection` (lib/api/server_probe.dart)
  issues a raw Dio `GET /global/health`; it runs before any gateway exists and
  is where Phase 0 protocol detection lands.
- **MCP loopback OAuth.** `lib/api/mcp_oauth.dart` is a v1-only flow with no
  v2 backend; it stays as-is behind `ServerCapabilities.mcpOAuth`.
- **Catalog merge semantics.** `_loadCatalog` in connection.dart reads
  `providers()`/`configuredProviders()` through `ProviderGateway` but its
  merge/fallback logic assumes the v1 `ProvidersResponse` shape.
