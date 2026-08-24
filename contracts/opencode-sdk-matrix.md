# OpenCode Dart SDK contract matrix

Generated from `opencode-openapi-03bba464.json` at upstream commit `03bba464d46f3eddf74195919b1344aa937f7b11` (SHA-256 `5bbd6493a1a488ef4294889341c896e420f814ecea95822100aaa9f3f95ab2d1`).

**Totals:** 162 paths, 188 operations (87 GET, 78 POST, 14 DELETE, 6 PATCH, 3 PUT), 418 effective parameters, 60 request schema slots, 520 response objects, 497 response schema slots, 472 component schemas, 89 Event variants, and 617 enum sites / 870 entries.

Machine-readable component/schema hashes, exact parameters, request and response media, Event discriminators, enums, and runtime replacement links are in `contracts/opencode-sdk-matrix.json`.

Schema hashes are SHA-256 over canonical JSON with recursively sorted object keys; array order and JSON scalar types are preserved.

## Explicit canonical override

`v2.session.history` declares `limit` and `after` as strings in the canonical OpenAPI, while the upstream generated JS SDK build patches both to numbers. The Dart parity target is the upstream generated SDK, so Dart emits nullable `num` query parameters. Both descriptors and their hashes are retained in the machine-readable matrix; they are not claimed to be identical.

## `lib/src/api/commands_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `CommandsApi` | `v2CommandList()` | `v2.command.list` | `GET /api/command` | commands | no | Dio |

## `lib/src/api/config_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `ConfigApi` | `configGet()` | `config.get` | `GET /config` | config | no | Dio |
| `ConfigApi` | `configProviders()` | `config.providers` | `GET /config/providers` | config | no | Dio |
| `ConfigApi` | `configUpdate()` | `config.update` | `PATCH /config` | config | no | Dio |

## `lib/src/api/control_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `ControlApi` | `appLog()` | `app.log` | `POST /log` | control | no | Dio |
| `ControlApi` | `authRemove()` | `auth.remove` | `DELETE /auth/{providerID}` | control | no | Dio |
| `ControlApi` | `authSet()` | `auth.set` | `PUT /auth/{providerID}` | control | no | Dio |

## `lib/src/api/control_plane_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `ControlPlaneApi` | `experimentalControlPlaneMoveSession()` | `experimental.controlPlane.moveSession` | `POST /experimental/control-plane/move-session` | controlPlane | no | Dio |

## `lib/src/api/event_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `EventApi` | `eventSubscribe()` | `event.subscribe` | `GET /event` | event | no | sse: `eventSubscribeStream` |

## `lib/src/api/events_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `EventsApi` | `v2EventSubscribe()` | `v2.event.subscribe` | `GET /api/event` | events | no | sse: `v2EventSubscribeStream` |

## `lib/src/api/experimental_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `ExperimentalApi` | `experimentalCapabilitiesGet()` | `experimental.capabilities.get` | `GET /experimental/capabilities` | experimental | no | Dio |
| `ExperimentalApi` | `experimentalConsoleGet()` | `experimental.console.get` | `GET /experimental/console` | experimental | no | Dio |
| `ExperimentalApi` | `experimentalConsoleListOrgs()` | `experimental.console.listOrgs` | `GET /experimental/console/orgs` | experimental | no | Dio |
| `ExperimentalApi` | `experimentalConsoleSwitchOrg()` | `experimental.console.switchOrg` | `POST /experimental/console/switch` | experimental | no | Dio |
| `ExperimentalApi` | `experimentalResourceList()` | `experimental.resource.list` | `GET /experimental/resource` | experimental | no | Dio |
| `ExperimentalApi` | `experimentalSessionBackground()` | `experimental.session.background` | `POST /experimental/session/{sessionID}/background` | experimental | no | Dio |
| `ExperimentalApi` | `experimentalSessionList()` | `experimental.session.list` | `GET /experimental/session` | experimental | no | Dio |
| `ExperimentalApi` | `toolIds()` | `tool.ids` | `GET /experimental/tool/ids` | experimental | no | Dio |
| `ExperimentalApi` | `toolList()` | `tool.list` | `GET /experimental/tool` | experimental | no | Dio |
| `ExperimentalApi` | `worktreeCreate()` | `worktree.create` | `POST /experimental/worktree` | experimental | no | Dio |
| `ExperimentalApi` | `worktreeList()` | `worktree.list` | `GET /experimental/worktree` | experimental | no | Dio |
| `ExperimentalApi` | `worktreeRemove()` | `worktree.remove` | `DELETE /experimental/worktree` | experimental | no | Dio |
| `ExperimentalApi` | `worktreeReset()` | `worktree.reset` | `POST /experimental/worktree/reset` | experimental | no | Dio |

## `lib/src/api/file_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `FileApi` | `fileList()` | `file.list` | `GET /file` | file | no | Dio |
| `FileApi` | `fileRead()` | `file.read` | `GET /file/content` | file | no | Dio |
| `FileApi` | `fileStatus()` | `file.status` | `GET /file/status` | file | no | Dio |
| `FileApi` | `findFiles()` | `find.files` | `GET /find/file` | file | no | Dio |
| `FileApi` | `findSymbols()` | `find.symbols` | `GET /find/symbol` | file | no | Dio |
| `FileApi` | `findText()` | `find.text` | `GET /find` | file | no | Dio |

## `lib/src/api/filesystem_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `FilesystemApi` | `v2FsFind()` | `v2.fs.find` | `GET /api/fs/find` | filesystem | no | Dio |
| `FilesystemApi` | `v2FsList()` | `v2.fs.list` | `GET /api/fs/list` | filesystem | no | Dio |
| `FilesystemApi` | `v2FsRead()` | `v2.fs.read` | `GET /api/fs/read/*` | filesystem | no | wildcard-path: `v2FsReadPath` |

## `lib/src/api/global_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `GlobalApi` | `globalConfigGet()` | `global.config.get` | `GET /global/config` | global | no | Dio |
| `GlobalApi` | `globalConfigUpdate()` | `global.config.update` | `PATCH /global/config` | global | no | Dio |
| `GlobalApi` | `globalDispose()` | `global.dispose` | `POST /global/dispose` | global | no | Dio |
| `GlobalApi` | `globalEvent()` | `global.event` | `GET /global/event` | global | no | sse: `globalEventStream` |
| `GlobalApi` | `globalHealth()` | `global.health` | `GET /global/health` | global | no | Dio |
| `GlobalApi` | `globalUpgrade()` | `global.upgrade` | `POST /global/upgrade` | global | no | Dio |

## `lib/src/api/instance_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `InstanceApi` | `appAgents()` | `app.agents` | `GET /agent` | instance | no | Dio |
| `InstanceApi` | `appSkills()` | `app.skills` | `GET /skill` | instance | no | Dio |
| `InstanceApi` | `commandList()` | `command.list` | `GET /command` | instance | no | Dio |
| `InstanceApi` | `formatterStatus()` | `formatter.status` | `GET /formatter` | instance | no | Dio |
| `InstanceApi` | `instanceDispose()` | `instance.dispose` | `POST /instance/dispose` | instance | no | Dio |
| `InstanceApi` | `lspStatus()` | `lsp.status` | `GET /lsp` | instance | no | Dio |
| `InstanceApi` | `pathGet()` | `path.get` | `GET /path` | instance | no | Dio |
| `InstanceApi` | `vcsApply()` | `vcs.apply` | `POST /vcs/apply` | instance | no | Dio |
| `InstanceApi` | `vcsDiff()` | `vcs.diff` | `GET /vcs/diff` | instance | no | Dio |
| `InstanceApi` | `vcsDiffRaw()` | `vcs.diff.raw` | `GET /vcs/diff/raw` | instance | no | Dio |
| `InstanceApi` | `vcsGet()` | `vcs.get` | `GET /vcs` | instance | no | Dio |
| `InstanceApi` | `vcsStatus()` | `vcs.status` | `GET /vcs/status` | instance | no | Dio |

## `lib/src/api/integrations_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `IntegrationsApi` | `v2IntegrationAttemptCancel()` | `v2.integration.attempt.cancel` | `DELETE /api/integration/attempt/{attemptID}` | integrations | no | Dio |
| `IntegrationsApi` | `v2IntegrationAttemptComplete()` | `v2.integration.attempt.complete` | `POST /api/integration/attempt/{attemptID}/complete` | integrations | no | Dio |
| `IntegrationsApi` | `v2IntegrationAttemptStatus()` | `v2.integration.attempt.status` | `GET /api/integration/attempt/{attemptID}` | integrations | no | Dio |
| `IntegrationsApi` | `v2IntegrationConnectKey()` | `v2.integration.connect.key` | `POST /api/integration/{integrationID}/connect/key` | integrations | no | Dio |
| `IntegrationsApi` | `v2IntegrationConnectOauth()` | `v2.integration.connect.oauth` | `POST /api/integration/{integrationID}/connect/oauth` | integrations | no | Dio |
| `IntegrationsApi` | `v2IntegrationGet()` | `v2.integration.get` | `GET /api/integration/{integrationID}` | integrations | no | Dio |
| `IntegrationsApi` | `v2IntegrationList()` | `v2.integration.list` | `GET /api/integration` | integrations | no | Dio |

## `lib/src/api/mcp_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `McpApi` | `mcpAdd()` | `mcp.add` | `POST /mcp` | mcp | no | Dio |
| `McpApi` | `mcpAuthAuthenticate()` | `mcp.auth.authenticate` | `POST /mcp/{name}/auth/authenticate` | mcp | no | Dio |
| `McpApi` | `mcpAuthCallback()` | `mcp.auth.callback` | `POST /mcp/{name}/auth/callback` | mcp | no | Dio |
| `McpApi` | `mcpAuthRemove()` | `mcp.auth.remove` | `DELETE /mcp/{name}/auth` | mcp | no | Dio |
| `McpApi` | `mcpAuthStart()` | `mcp.auth.start` | `POST /mcp/{name}/auth` | mcp | no | Dio |
| `McpApi` | `mcpConnect()` | `mcp.connect` | `POST /mcp/{name}/connect` | mcp | no | Dio |
| `McpApi` | `mcpDisconnect()` | `mcp.disconnect` | `POST /mcp/{name}/disconnect` | mcp | no | Dio |
| `McpApi` | `mcpStatus()` | `mcp.status` | `GET /mcp` | mcp | no | Dio |

## `lib/src/api/messages_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `MessagesApi` | `v2SessionMessages()` | `v2.session.messages` | `GET /api/session/{sessionID}/message` | messages | no | Dio |

## `lib/src/api/models_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `ModelsApi` | `v2ModelList()` | `v2.model.list` | `GET /api/model` | models | no | Dio |

## `lib/src/api/opencode_http_api_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `OpencodeHttpApiApi` | `v2AgentList()` | `v2.agent.list` | `GET /api/agent` | opencode HttpApi | no | Dio |
| `OpencodeHttpApiApi` | `v2CredentialRemove()` | `v2.credential.remove` | `DELETE /api/credential/{credentialID}` | opencode HttpApi | no | Dio |
| `OpencodeHttpApiApi` | `v2CredentialUpdate()` | `v2.credential.update` | `PATCH /api/credential/{credentialID}` | opencode HttpApi | no | Dio |
| `OpencodeHttpApiApi` | `v2HealthGet()` | `v2.health.get` | `GET /api/health` | opencode HttpApi | no | Dio |
| `OpencodeHttpApiApi` | `v2LocationGet()` | `v2.location.get` | `GET /api/location` | opencode HttpApi | no | Dio |

## `lib/src/api/permission_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `PermissionApi` | `permissionList()` | `permission.list` | `GET /permission` | permission | no | Dio |
| `PermissionApi` | `permissionReply()` | `permission.reply` | `POST /permission/{requestID}/reply` | permission | no | Dio |

## `lib/src/api/permissions_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `PermissionsApi` | `v2PermissionRequestList()` | `v2.permission.request.list` | `GET /api/permission/request` | permissions | no | Dio |
| `PermissionsApi` | `v2PermissionSavedList()` | `v2.permission.saved.list` | `GET /api/permission/saved` | permissions | no | Dio |
| `PermissionsApi` | `v2PermissionSavedRemove()` | `v2.permission.saved.remove` | `DELETE /api/permission/saved/{id}` | permissions | no | Dio |
| `PermissionsApi` | `v2SessionPermissionCreate()` | `v2.session.permission.create` | `POST /api/session/{sessionID}/permission` | permissions | no | Dio |
| `PermissionsApi` | `v2SessionPermissionGet()` | `v2.session.permission.get` | `GET /api/session/{sessionID}/permission/{requestID}` | permissions | no | Dio |
| `PermissionsApi` | `v2SessionPermissionList()` | `v2.session.permission.list` | `GET /api/session/{sessionID}/permission` | permissions | no | Dio |
| `PermissionsApi` | `v2SessionPermissionReply()` | `v2.session.permission.reply` | `POST /api/session/{sessionID}/permission/{requestID}/reply` | permissions | no | Dio |

## `lib/src/api/project_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `ProjectApi` | `projectCurrent()` | `project.current` | `GET /project/current` | project | no | Dio |
| `ProjectApi` | `projectDirectories()` | `project.directories` | `GET /project/{projectID}/directories` | project | no | Dio |
| `ProjectApi` | `projectInitGit()` | `project.initGit` | `POST /project/git/init` | project | no | Dio |
| `ProjectApi` | `projectList()` | `project.list` | `GET /project` | project | no | Dio |
| `ProjectApi` | `projectUpdate()` | `project.update` | `PATCH /project/{projectID}` | project | no | Dio |

## `lib/src/api/project_copy_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `ProjectCopyApi` | `experimentalProjectCopyGenerateName()` | `experimental.projectCopy.generateName` | `POST /experimental/project/{projectID}/copy/generate-name` | projectCopy | no | Dio |
| `ProjectCopyApi` | `v2ProjectCopyCreate()` | `v2.projectCopy.create` | `POST /experimental/project/{projectID}/copy` | projectCopy | no | Dio |
| `ProjectCopyApi` | `v2ProjectCopyRefresh()` | `v2.projectCopy.refresh` | `POST /experimental/project/{projectID}/copy/refresh` | projectCopy | no | Dio |
| `ProjectCopyApi` | `v2ProjectCopyRemove()` | `v2.projectCopy.remove` | `DELETE /experimental/project/{projectID}/copy` | projectCopy | no | Dio |

## `lib/src/api/provider_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `ProviderApi` | `providerAuth()` | `provider.auth` | `GET /provider/auth` | provider | no | Dio |
| `ProviderApi` | `providerList()` | `provider.list` | `GET /provider` | provider | no | Dio |
| `ProviderApi` | `providerOauthAuthorize()` | `provider.oauth.authorize` | `POST /provider/{providerID}/oauth/authorize` | provider | no | Dio |
| `ProviderApi` | `providerOauthCallback()` | `provider.oauth.callback` | `POST /provider/{providerID}/oauth/callback` | provider | no | Dio |

## `lib/src/api/providers_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `ProvidersApi` | `v2ProviderGet()` | `v2.provider.get` | `GET /api/provider/{providerID}` | providers | no | Dio |
| `ProvidersApi` | `v2ProviderList()` | `v2.provider.list` | `GET /api/provider` | providers | no | Dio |

## `lib/src/api/pty_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `PtyApi` | `ptyConnect()` | `pty.connect` | `GET /pty/{ptyID}/connect` | pty | no | Dio |
| `PtyApi` | `ptyConnectToken()` | `pty.connectToken` | `POST /pty/{ptyID}/connect-token` | pty | no | Dio |
| `PtyApi` | `ptyCreate()` | `pty.create` | `POST /pty` | pty | no | Dio |
| `PtyApi` | `ptyGet()` | `pty.get` | `GET /pty/{ptyID}` | pty | no | Dio |
| `PtyApi` | `ptyList()` | `pty.list` | `GET /pty` | pty | no | Dio |
| `PtyApi` | `ptyRemove()` | `pty.remove` | `DELETE /pty/{ptyID}` | pty | no | Dio |
| `PtyApi` | `ptyShells()` | `pty.shells` | `GET /pty/shells` | pty | no | Dio |
| `PtyApi` | `ptyUpdate()` | `pty.update` | `PUT /pty/{ptyID}` | pty | no | Dio |
| `PtyApi` | `v2PtyConnect()` | `v2.pty.connect` | `GET /api/pty/{ptyID}/connect` | pty | no | Dio |
| `PtyApi` | `v2PtyConnectToken()` | `v2.pty.connectToken` | `POST /api/pty/{ptyID}/connect-token` | pty | no | Dio |
| `PtyApi` | `v2PtyCreate()` | `v2.pty.create` | `POST /api/pty` | pty | no | Dio |
| `PtyApi` | `v2PtyGet()` | `v2.pty.get` | `GET /api/pty/{ptyID}` | pty | no | Dio |
| `PtyApi` | `v2PtyList()` | `v2.pty.list` | `GET /api/pty` | pty | no | Dio |
| `PtyApi` | `v2PtyRemove()` | `v2.pty.remove` | `DELETE /api/pty/{ptyID}` | pty | no | Dio |
| `PtyApi` | `v2PtyUpdate()` | `v2.pty.update` | `PUT /api/pty/{ptyID}` | pty | no | Dio |

## `lib/src/api/question_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `QuestionApi` | `questionList()` | `question.list` | `GET /question` | question | no | Dio |
| `QuestionApi` | `questionReject()` | `question.reject` | `POST /question/{requestID}/reject` | question | no | Dio |
| `QuestionApi` | `questionReply()` | `question.reply` | `POST /question/{requestID}/reply` | question | no | Dio |

## `lib/src/api/reference_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `ReferenceApi` | `v2ReferenceList()` | `v2.reference.list` | `GET /api/reference` | reference | no | Dio |

## `lib/src/api/session_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `SessionApi` | `partDelete()` | `part.delete` | `DELETE /session/{sessionID}/message/{messageID}/part/{partID}` | session | no | Dio |
| `SessionApi` | `partUpdate()` | `part.update` | `PATCH /session/{sessionID}/message/{messageID}/part/{partID}` | session | no | Dio |
| `SessionApi` | `permissionRespond()` | `permission.respond` | `POST /session/{sessionID}/permissions/{permissionID}` | session | yes | Dio |
| `SessionApi` | `sessionAbort()` | `session.abort` | `POST /session/{sessionID}/abort` | session | no | Dio |
| `SessionApi` | `sessionChildren()` | `session.children` | `GET /session/{sessionID}/children` | session | no | Dio |
| `SessionApi` | `sessionCommand()` | `session.command` | `POST /session/{sessionID}/command` | session | no | Dio |
| `SessionApi` | `sessionCreate()` | `session.create` | `POST /session` | session | no | Dio |
| `SessionApi` | `sessionDelete()` | `session.delete` | `DELETE /session/{sessionID}` | session | no | Dio |
| `SessionApi` | `sessionDeleteMessage()` | `session.deleteMessage` | `DELETE /session/{sessionID}/message/{messageID}` | session | no | Dio |
| `SessionApi` | `sessionDiff()` | `session.diff` | `GET /session/{sessionID}/diff` | session | no | Dio |
| `SessionApi` | `sessionFork()` | `session.fork` | `POST /session/{sessionID}/fork` | session | no | Dio |
| `SessionApi` | `sessionGet()` | `session.get` | `GET /session/{sessionID}` | session | no | Dio |
| `SessionApi` | `sessionInit()` | `session.init` | `POST /session/{sessionID}/init` | session | no | Dio |
| `SessionApi` | `sessionList()` | `session.list` | `GET /session` | session | no | Dio |
| `SessionApi` | `sessionMessage()` | `session.message` | `GET /session/{sessionID}/message/{messageID}` | session | no | Dio |
| `SessionApi` | `sessionMessages()` | `session.messages` | `GET /session/{sessionID}/message` | session | no | Dio |
| `SessionApi` | `sessionPrompt()` | `session.prompt` | `POST /session/{sessionID}/message` | session | no | Dio |
| `SessionApi` | `sessionPromptAsync()` | `session.prompt_async` | `POST /session/{sessionID}/prompt_async` | session | no | Dio |
| `SessionApi` | `sessionRevert()` | `session.revert` | `POST /session/{sessionID}/revert` | session | no | Dio |
| `SessionApi` | `sessionShare()` | `session.share` | `POST /session/{sessionID}/share` | session | no | Dio |
| `SessionApi` | `sessionShell()` | `session.shell` | `POST /session/{sessionID}/shell` | session | no | Dio |
| `SessionApi` | `sessionStatus()` | `session.status` | `GET /session/status` | session | no | Dio |
| `SessionApi` | `sessionSummarize()` | `session.summarize` | `POST /session/{sessionID}/summarize` | session | no | Dio |
| `SessionApi` | `sessionTodo()` | `session.todo` | `GET /session/{sessionID}/todo` | session | no | Dio |
| `SessionApi` | `sessionUnrevert()` | `session.unrevert` | `POST /session/{sessionID}/unrevert` | session | no | Dio |
| `SessionApi` | `sessionUnshare()` | `session.unshare` | `DELETE /session/{sessionID}/share` | session | no | Dio |
| `SessionApi` | `sessionUpdate()` | `session.update` | `PATCH /session/{sessionID}` | session | no | Dio |

## `lib/src/api/session_questions_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `SessionQuestionsApi` | `v2QuestionRequestList()` | `v2.question.request.list` | `GET /api/question/request` | session questions | no | Dio |
| `SessionQuestionsApi` | `v2SessionQuestionList()` | `v2.session.question.list` | `GET /api/session/{sessionID}/question` | session questions | no | Dio |
| `SessionQuestionsApi` | `v2SessionQuestionReject()` | `v2.session.question.reject` | `POST /api/session/{sessionID}/question/{requestID}/reject` | session questions | no | Dio |
| `SessionQuestionsApi` | `v2SessionQuestionReply()` | `v2.session.question.reply` | `POST /api/session/{sessionID}/question/{requestID}/reply` | session questions | no | Dio |

## `lib/src/api/sessions_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `SessionsApi` | `v2SessionActive()` | `v2.session.active` | `GET /api/session/active` | sessions | no | Dio |
| `SessionsApi` | `v2SessionCompact()` | `v2.session.compact` | `POST /api/session/{sessionID}/compact` | sessions | no | Dio |
| `SessionsApi` | `v2SessionContext()` | `v2.session.context` | `GET /api/session/{sessionID}/context` | sessions | no | Dio |
| `SessionsApi` | `v2SessionCreate()` | `v2.session.create` | `POST /api/session` | sessions | no | Dio |
| `SessionsApi` | `v2SessionEvents()` | `v2.session.events` | `GET /api/session/{sessionID}/event` | sessions | no | sse: `v2SessionEventsStream` |
| `SessionsApi` | `v2SessionGet()` | `v2.session.get` | `GET /api/session/{sessionID}` | sessions | no | Dio |
| `SessionsApi` | `v2SessionHistory()` | `v2.session.history` | `GET /api/session/{sessionID}/history` | sessions | no | Dio |
| `SessionsApi` | `v2SessionInterrupt()` | `v2.session.interrupt` | `POST /api/session/{sessionID}/interrupt` | sessions | no | Dio |
| `SessionsApi` | `v2SessionList()` | `v2.session.list` | `GET /api/session` | sessions | no | Dio |
| `SessionsApi` | `v2SessionMessage()` | `v2.session.message` | `GET /api/session/{sessionID}/message/{messageID}` | sessions | no | Dio |
| `SessionsApi` | `v2SessionPrompt()` | `v2.session.prompt` | `POST /api/session/{sessionID}/prompt` | sessions | no | Dio |
| `SessionsApi` | `v2SessionRevertClear()` | `v2.session.revert.clear` | `POST /api/session/{sessionID}/revert/clear` | sessions | no | Dio |
| `SessionsApi` | `v2SessionRevertCommit()` | `v2.session.revert.commit` | `POST /api/session/{sessionID}/revert/commit` | sessions | no | Dio |
| `SessionsApi` | `v2SessionRevertStage()` | `v2.session.revert.stage` | `POST /api/session/{sessionID}/revert/stage` | sessions | no | Dio |
| `SessionsApi` | `v2SessionSwitchAgent()` | `v2.session.switchAgent` | `POST /api/session/{sessionID}/agent` | sessions | no | Dio |
| `SessionsApi` | `v2SessionSwitchModel()` | `v2.session.switchModel` | `POST /api/session/{sessionID}/model` | sessions | no | Dio |
| `SessionsApi` | `v2SessionWait()` | `v2.session.wait` | `POST /api/session/{sessionID}/wait` | sessions | no | Dio |

## `lib/src/api/skills_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `SkillsApi` | `v2SkillList()` | `v2.skill.list` | `GET /api/skill` | skills | no | Dio |

## `lib/src/api/sync_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `SyncApi` | `syncHistoryList()` | `sync.history.list` | `POST /sync/history` | sync | no | Dio |
| `SyncApi` | `syncReplay()` | `sync.replay` | `POST /sync/replay` | sync | no | Dio |
| `SyncApi` | `syncStart()` | `sync.start` | `POST /sync/start` | sync | no | Dio |
| `SyncApi` | `syncSteal()` | `sync.steal` | `POST /sync/steal` | sync | no | Dio |

## `lib/src/api/tui_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `TuiApi` | `tuiAppendPrompt()` | `tui.appendPrompt` | `POST /tui/append-prompt` | tui | no | Dio |
| `TuiApi` | `tuiClearPrompt()` | `tui.clearPrompt` | `POST /tui/clear-prompt` | tui | no | Dio |
| `TuiApi` | `tuiControlNext()` | `tui.control.next` | `GET /tui/control/next` | tui | no | Dio |
| `TuiApi` | `tuiControlResponse()` | `tui.control.response` | `POST /tui/control/response` | tui | no | Dio |
| `TuiApi` | `tuiExecuteCommand()` | `tui.executeCommand` | `POST /tui/execute-command` | tui | no | Dio |
| `TuiApi` | `tuiOpenHelp()` | `tui.openHelp` | `POST /tui/open-help` | tui | no | Dio |
| `TuiApi` | `tuiOpenModels()` | `tui.openModels` | `POST /tui/open-models` | tui | no | Dio |
| `TuiApi` | `tuiOpenSessions()` | `tui.openSessions` | `POST /tui/open-sessions` | tui | no | Dio |
| `TuiApi` | `tuiOpenThemes()` | `tui.openThemes` | `POST /tui/open-themes` | tui | no | Dio |
| `TuiApi` | `tuiPublish()` | `tui.publish` | `POST /tui/publish` | tui | no | Dio |
| `TuiApi` | `tuiSelectSession()` | `tui.selectSession` | `POST /tui/select-session` | tui | no | Dio |
| `TuiApi` | `tuiShowToast()` | `tui.showToast` | `POST /tui/show-toast` | tui | no | Dio |
| `TuiApi` | `tuiSubmitPrompt()` | `tui.submitPrompt` | `POST /tui/submit-prompt` | tui | no | Dio |

## `lib/src/api/workspace_api.dart`

| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |
|---|---|---|---|---|---:|---|
| `WorkspaceApi` | `experimentalWorkspaceAdapterList()` | `experimental.workspace.adapter.list` | `GET /experimental/workspace/adapter` | workspace | no | Dio |
| `WorkspaceApi` | `experimentalWorkspaceCreate()` | `experimental.workspace.create` | `POST /experimental/workspace` | workspace | no | Dio |
| `WorkspaceApi` | `experimentalWorkspaceList()` | `experimental.workspace.list` | `GET /experimental/workspace` | workspace | no | Dio |
| `WorkspaceApi` | `experimentalWorkspaceRemove()` | `experimental.workspace.remove` | `DELETE /experimental/workspace/{id}` | workspace | no | Dio |
| `WorkspaceApi` | `experimentalWorkspaceStatus()` | `experimental.workspace.status` | `GET /experimental/workspace/status` | workspace | no | Dio |
| `WorkspaceApi` | `experimentalWorkspaceSyncList()` | `experimental.workspace.syncList` | `POST /experimental/workspace/sync-list` | workspace | no | Dio |
| `WorkspaceApi` | `experimentalWorkspaceWarp()` | `experimental.workspace.warp` | `POST /experimental/workspace/warp` | workspace | no | Dio |
