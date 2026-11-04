# Inventory: `lib/domain/` → native

Generated 2026-11-03 by unit INVENTORY-DOMAIN from [`domain.jsonl`](domain.jsonl) (185 files, 39,518 Dart lines). Each line maps one Flutter file to its native counterparts and judges parity (`done` only with named native evidence). Catalog row ids come from `native/catalog/behaviors.jsonl`, the design catalog plus top-up rows.

## Totals by status

| Status | Files | Dart lines |
|---|---:|---:|
| done | 35 | 8,707 |
| partial | 65 | 13,947 |
| missing | 50 | 7,368 |
| after-switch | 22 | 8,383 |
| dropped | 4 | 631 |
| not-needed | 9 | 482 |
| **total** | **185** | **39,518** |

## Totals by kind (files per status)

| Kind | Total | done | partial | missing | after-switch | dropped | not-needed |
|---|---:|---:|---:|---:|---:|---:|---:|
| domain | 164 | 34 | 62 | 43 | 19 | 4 | 2 |
| dead | 9 |  | 1 | 4 |  |  | 4 |
| other | 4 |  | 1 | 1 |  |  | 2 |
| test-support | 3 |  |  |  | 2 |  | 1 |
| protocol | 2 |  |  | 1 | 1 |  |  |
| generated | 1 | 1 |  |  |  |  |  |
| api | 1 |  | 1 |  |  |  |  |
| state | 1 |  |  | 1 |  |  |  |

`dead` = nothing in `lib/` imports the file (only tests). It is still judged on native evidence when its catalog rows are in switch scope.

## The 20 biggest missing/partial files

| # | File | Lines | Status | Target | Rows | Gaps |
|---|---|---:|---|---|---:|---|
| 1 | `domain/git/git_repository.dart` | 1021 | partial | `:engine:project` | 11 | • pull/fetch/push take no GitHub token (no credential drop path, B-SEC-081/082)<br>• workspaces resolve only to server shells; built-in Linux / Termux phone Git shells absent |
| 2 | `domain/usage_subscriptions.dart` | 865 | missing | `:engine:integrations` | 23 | • no subscription/reading model, sources (Codex, Paseo, collector, OpenCode sign-ins) or merge rules<br>• no plans page, alert thresholds or plan alerts<br>• widget only renders a feed nothing native produces |
| 3 | `domain/models/models_messages.dart` | 741 | partial | `:core:model` | 0 | • behaviour helpers still TODO on core model (Transcript.kt TODO S2-MODEL); refineFromText duplicated in several engines<br>• sessionErrorFallbackText kind-specific sentences not found<br>• TurnRunCheck (ask server before 'connection dropped') not found |
| 4 | `domain/settings_scope.dart` | 576 | partial | `:engine:settings` | 9 | • LocalSettingsAccess/ChatAgentChoices not bound in the app graph or used by the server page<br>• consent writes through edit tokens not reachable from UI |
| 5 | `domain/product_failure.dart` | 495 | partial | `:engine:connection` | 5 | • no agentUpdating category (phone refusing agent work during setup)<br>• no fileTooBig/fileMissing/fileDenied/fileUnreadable agent-host file categories<br>• no conversationNeedsNewChat (Paseo newChatRequired)<br>• no single app-wide failure-to-words mapper; each feature maps Failure itself |
| 6 | `domain/background_tasks.dart` | 493 | partial | `:engine:agents` | 5 | • Paseo gateway backgroundTasksRunning/lastTurnFailed hard-coded false (TODO S5-PASEOP1): BackgroundTaskBook not wired<br>• BackgroundTaskGateway change stream not ported (revision counter only)<br>• agent-row Running/Failed status from the book (B-AGENT-151) not reachable |
| 7 | `domain/models/models_requests.dart` | 471 | partial | `:core:model` | 0 | • bareModelID (strip provider prefix from composite model ids) not found |
| 8 | `domain/server_gateway/value_types_core.dart` | 468 | partial | `:core:model` | 0 | • CatalogModel/ModelCost helper getters (isFree, isFast, reasoningEffort, deprecated, preview) not on the native model; not confirmed elsewhere |
| 9 | `domain/chat_feed.dart` | 467 | partial | `:engine:agents` | 4 | • stoppedByPerson ('Stopped' instead of 'Done' after a local stop) not on native ChatFeedItem<br>• contextUsage ring value not on row<br>• isGit badge missing on OpenCode rows<br>• startChatIn / lastUsedProject contract not part of native feed (only PaseoChatFeed has rememberLastUsedProject) |
| 10 | `domain/models.dart` | 452 | partial | `:core:model` | 0 | • SessionRevert.fingerprint (used to detect a moved staged boundary) not found<br>• wrong-generation health shape tag (UnexpectedHealthShape) not found<br>• ApiException not-found helpers map to typed Failure only implicitly |
| 11 | `domain/phone_agents.dart` | 433 | partial | `:engine:agents` | 2 | • not reachable in app: androidApp binds AppPhoneAgents = NoPhoneAgents and does not depend on :android:agenthost |
| 12 | `domain/agent_sign_in.dart` | 429 | partial | `:engine:agents` | 4 | • AgentSignInSession state machine (start/inspect/submitCode/cancelAndDrain, late-challenge suppression) not ported to common code; native engine only plans a terminal sign-in<br>• AgentSignInCode single-use type and rate-limit reset handling missing<br>• android PhoneAgentSignIn start/submit/cancel unused (only status called)<br>• not reachable in app: androidApp binds AppPhoneAgents = NoPhoneAgents and does not depend on :android:agenthost |
| 13 | `domain/listening_ports.dart` | 399 | partial | `:engine:runtime` | 7 | • native scan is loopback-probe only: no /proc/net/tcp socket-table parse or fd socket inode mapping<br>• no tailScript (regular-file check) for program logs<br>• no stopScript (pid + expected command identity)<br>• no portsInOutput for server shells; no detected-dev-servers list<br>• ANSI stripping of logs not ported |
| 14 | `domain/product_repository.dart` | 398 | partial | `:core:domain` | 1 | • isExactServerVersion upgrade gate (B-PROTO-083) not ported; no upgradeServer implementation (remoteUpgrade=false in OC2) |
| 15 | `domain/server_gateway/value_types_catalog.dart` | 397 | partial | `:core:model` | 1 | • McpServerDraft has no validation/toConfig (URL scheme/credentials, local command lines, header/env pair checks) - B-PROTO-080<br>• IntegrationInfo credentialIDs/hasEnvironmentConnection helpers not confirmed<br>• ProviderRuntimeBusyException not mirrored |
| 16 | `domain/setup_registry.dart` | 387 | missing | `:engine:runtime` | 5 | • no registry client, consent or cache<br>• no metadata redaction of credentials/URLs/args |
| 17 | `domain/byo_host.dart` | 373 | missing | `:engine:integrations` | 16 | • :engine:integrations ByoHost/ByoSigner not built<br>• no host target validation, key handling, install/adopt/revoke flow or record store<br>• only a BYO-identity slot in profile deletion and a gate row saying this version cannot set it up |
| 18 | `domain/merged_chat_feed.dart` | 370 | partial | `:engine:agents` | 4 | • production merge holds OpenCode sources only; Paseo feed not joined<br>• no startChatInSource / 'Choose which agent should start this chat' routing<br>• no routeFor/sourceFor validation of a held row before navigation<br>• no per-source refresh timeout |
| 19 | `domain/agent_handoff_record.dart` | 357 | missing | `:engine:agents` | 3 | • no handoff record builder (byte budget, whole-turn dropping, redaction before compaction) |
| 20 | `domain/provider_presentation.dart` | 342 | partial | `:engine:agents` | 3 | • no Z.AI/Zhipu alias consolidation or route labels<br>• no known-name map or tidied model id labels<br>• no family/newest-first model order (alphabetical instead)<br>• no provider logo domain or monogram<br>• no integrations grouping |

## Gaps no catalog row covers (untested behaviour the catalog missed)

These partial/missing files have no catalog row, or no Flutter test covers their main behaviour. Their gaps are invisible to a row-by-row parity check, so they need new rows or a direct port.

| File | Lines | Status | Rows | Untested | Gaps |
|---|---:|---|---:|---|---|
| `domain/models/models_messages.dart` | 741 | partial | 0 | no | • behaviour helpers still TODO on core model (Transcript.kt TODO S2-MODEL); refineFromText duplicated in several engines<br>• sessionErrorFallbackText kind-specific sentences not found<br>• TurnRunCheck (ask server before 'connection dropped') not found |
| `domain/models/models_requests.dart` | 471 | partial | 0 | no | • bareModelID (strip provider prefix from composite model ids) not found |
| `domain/server_gateway/value_types_core.dart` | 468 | partial | 0 | yes | • CatalogModel/ModelCost helper getters (isFree, isFast, reasoningEffort, deprecated, preview) not on the native model; not confirmed elsewhere |
| `domain/models.dart` | 452 | partial | 0 | no | • SessionRevert.fingerprint (used to detect a moved staged boundary) not found<br>• wrong-generation health shape tag (UnexpectedHealthShape) not found<br>• ApiException not-found helpers map to typed Failure only implicitly |
| `domain/phone_file.dart` | 117 | partial | 0 | no | • no phoneFileAction rule (shared-storage path on this phone -> open with / install APK)<br>• no fileUriPath handling of file:// links<br>• native 'open with' only hands over bytes already fetched through the server |
| `domain/termux_migration_service.dart` | 110 | missing | 0 | yes | • no migration offer (oc.termuxMigrationOffer.<id>) or eligibility check<br>• no controller creation / orphaned cache cleanup |
| `domain/connection_status.dart` | 58 | partial | 0 | no | • no shared app-wide connection phase: StreamStatus collapses to 3-state ChatLink (Live/Connecting/Offline)<br>• no credentialsUnreadable / credentialsRequired phases in link status<br>• no serverBusy (slow phone server) neutral phase<br>• no quiet presentation grace or serverName/usesToken facts |
| `domain/provider_conversation_import.dart` | 57 | missing | 0 | yes | • no importable-conversation list or import_agent call; only named as a frozen facet in core/domain Facets.kt |
| `domain/relative_age.dart` | 43 | partial | 0 | yes | • no shared relative-age helper in :ui:words or kit; only Updates and the widget have their own copies<br>• short locale date after a week not implemented in shared code |
| `domain/session_history.dart` | 35 | partial | 0 | yes | • chat history load reads one page and does not skip staged-away pages to the boundary<br>• no repeated-cursor loop guard ("history cursor expired") on history reads |
| `domain/app_diagnostics_gateway.dart` | 21 | partial | 0 | no | • no single diagnostics gateway; exit history and crash diagnostics engines are not bound in androidApp<br>• background pause state/resume not in a diagnostics surface<br>• no crash report preview/share flow |
| `domain/session_stop.dart` | 12 | partial | 0 | yes | • transcript marks stopped replies, but no attention-feed / Inbox / Work rule keeps a stop out of failed runs (no attention engine) |

## Files nothing in `lib/` imports

- `domain/attention_item.dart`: not-needed. Nothing in lib/ imports it; superseded by attention_feed.dart.
- `domain/byo_host_setup.dart`: missing. Nothing in lib/ imports it (only test/byo_host/setup_tailnet_test.dart); row B-SEC-028 is scope switch.
- `domain/environment_projects.dart`: partial. Nothing in lib/ imports it; only test/environment_projects_test.dart.
- `domain/session_command_handoff.dart`: not-needed. Nothing in lib/ imports it; its namesake test exercises ui/widgets/session_handoff.dart instead.
- `domain/session_slice_capabilities.dart`: not-needed. No lib/ importer (only test/staged_revert_message_count_test.dart); all four flags are constant false.
- `domain/setup_planner.dart`: missing. Dead in Flutter (tests only); confirm whether B-AGENT-232/B-SEC-167 should be dropped.
- `domain/sv3_mcp_capabilities.dart`: missing. Dead in Flutter lib (tests only) but B-PROTO-108 is switch-scoped.
- `domain/sv3_tool_configuration.dart`: not-needed. Nothing in lib/ imports it and no catalog rows.
- `domain/worktree_inspection.dart`: missing. Nothing in lib/ imports it (only test/worktree_inspection_test.dart); row B-AGENT-318 is scope switch.
