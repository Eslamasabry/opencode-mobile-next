# File inventory: lib/builtin, lib/orchestration, lib/paseo → native

Unit INVENTORY-BUILTIN-ORCH-PASEO (P12 parity push, 2026-11-03). One record per Flutter file in [`builtin-orch-paseo.jsonl`](builtin-orch-paseo.jsonl) (166 files, 50,901 lines). Base: `native/p12-integrate-2026-11-03` at `71f74de8`.

How it was made: each file was read and its native counterpart searched in `native/**` (class/function names, strings, scripts, constants), and the match was opened to confirm it. "done" needs named native files that cover every user-visible behaviour **and** are reachable from `native/androidApp`. A native engine that no wiring builds is "partial" with an "engine only" gap. `catalog_rows` were first gathered mechanically, as the rows whose `sources` cite a Flutter test that imports the file or is named after it. They were then narrowed by hand to the rows about the file's own logic.

## Totals by status

| Status | Files | Lines |
|---|---:|---:|
| done | 8 | 3,191 |
| partial | 52 | 19,016 |
| missing | 47 | 13,731 |
| after-switch | 50 | 14,394 |
| dropped | 3 | 544 |
| not-needed | 6 | 25 |
| **total** | **166** | **50,901** |

| Area | done | partial | missing | after-switch | dropped | not-needed |
|---|---:|---:|---:|---:|---:|---:|
| `lib/builtin` | 3 | 36 | 34 | 6 | 3 | 6 |
| `lib/orchestration` | 0 | 1 | 0 | 43 | 0 | 0 |
| `lib/paseo` | 5 | 15 | 13 | 1 | 0 | 0 |

## Totals by kind

| Kind | Files | done | partial | missing | after-switch | dropped | not-needed |
|---|---:|---:|---:|---:|---:|---:|---:|
| domain | 57 | 3 | 12 | 19 | 22 | 1 | 0 |
| protocol | 57 | 5 | 17 | 13 | 22 | 0 | 0 |
| state | 21 | 0 | 11 | 7 | 3 | 0 | 0 |
| platform-bridge | 13 | 0 | 5 | 5 | 1 | 2 | 0 |
| other | 16 | 0 | 6 | 3 | 1 | 0 | 6 |
| background | 1 | 0 | 1 | 0 | 0 | 0 | 0 |
| test-support | 1 | 0 | 0 | 0 | 1 | 0 | 0 |

## Portability and owner of the open work (partial + missing)

agent-portable: 77, needs-design: 12, platform-specific: 10.

| Target module | Files |
|---|---:|
| `:engine:runtime` | 33 |
| `:protocol:paseo` | 28 |
| `:engine:agents` | 10 |
| `:engine:integrations` | 3 |
| `:engine:requests` | 3 |
| `:engine:project` | 3 |
| `:android:linux` | 3 |
| `:feature:linux` | 3 |
| `:android:termux` | 3 |
| `:engine:migration` | 3 |
| `:android:agenthost` | 1 |
| `:engine:git` | 1 |
| `:android:terminal` | 1 |
| `:android:platform` | 1 |
| `:engine:transcript` | 1 |
| `:engine:media` | 1 |
| `:protocol:team` | 1 |

Modules named as owners that do **not exist yet** in `native/`: `:engine:integrations`, `:engine:project`, `:android:termux`, `:android:terminal` (and, after the switch, `:engine:team`/`:protocol:team`). `:android:agenthost` exists but is not a dependency of `native/androidApp`, and `AgentsWiring` binds `NoPhoneAgents`.

## The 20 biggest missing/partial files

| # | File | Lines | Status | Owner | Gaps |
|---:|---|---:|---|---|---|
| 1 | `lib/builtin/agents/phone_agents_host.dart` | 1524 | partial | `:engine:agents` | engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this; autoUpdate yield-to-person and usePreviousVersion rollback not ported (PhoneCheck drops the Claude rollback path); resumeAfterIdle strict idle receipt logic only in native agenthost, not driven; installer/removal ports have no Android implementation |
| 2 | `lib/paseo/gateway.dart` | 1501 | partial | `:protocol:paseo` | Dart implements 15 facets; native implements 4 (ServerGateway, SessionSelection, CorrelatedPrompt, MidTurnPrompt); missing native facets: ServerOperationsGateway, HostAgentProvider/Permission (only public methods, unreachable from app), GenUiHistory/RecoverySnapshot/LoadGuard, HostDaemonUpdate, AgentFeature, ProviderConversationImport, AgentTurnFailure (lastAgentTurn), AgentHandoffTarget; listFiles/fileContent/findFile/findText unavailable; todos returns empty; shell unavailable |
| 3 | `lib/builtin/builtin_linux.dart` | 1467 | partial | `:android:linux` | BuiltinRuntime binds only run/services/start/stop/status; recovery API (confirm/ack/unbind/cancel receipts), idle policy, work/chat leases, performance(), projectStorage, setup start/status/cancel and remove(keepProjects) are not exposed; readStatus fills only installed/phase/server/port/uptime/services: restore, idle and recovery fields of RuntimeStatus stay default; Phone agent-context block (PhoneAgentContext) left out of the native server script; Setup/install scripts (installOpenCodeScript, versionScript, listProjects, createFolder, repoState, firstCommit) not ported; Removal with measured freed bytes (B-LINUX-029/030) not reachable |
| 4 | `lib/builtin/local_terminal.dart` | 855 | partial | `:android:terminal` | No PTY: :android:terminal not created; the LocalTerminal shim refuses every start; TerminalHost is not bound in androidApp, so the sign-in terminal cannot run; No local terminal screen, shell adoption, sticky Ctrl, resize (B-LINUX-097..101) |
| 5 | `lib/orchestration/adapters/inapp/phone_engine_gateway.dart` | 832 | partial | `:protocol:team` | Client not ported (AI Team after switch, D6); PhoneEngineNative is only the daemon's native half; switch row B-STATE-058 only partly met: ProfileDeletion keeps the oc.teamEngineDeleted tombstone and retries via a ProfileResourceCleaner, but no production cleaner sends DELETE /v1/profile to the engine (only a test stub; no androidApp wiring); B-AGENT-193 deletion fence met in ProfileDeletionTest |
| 6 | `lib/builtin/git_link_repair.dart` | 759 | missing | `:engine:project` | No git object-link scan/repair natively; No Termux store fetch with name verification; No 'Termux access not granted' / 'Termux gone' plain answers |
| 7 | `lib/builtin/builtin_server_recovery.dart` | 754 | partial | `:engine:runtime` | No native BuiltinServerRecovery consumer: no paced foreground check loop (recovering vs steady interval), no receipt ack, no healthy-visit budget reset; PhoneServerPort.confirmRecovery is bound to Ok(Unit) and LaunchMode.Recovery is never used; Profile-deletion drain of owner claims (B-SEC-011) not ported; Kotlin native half (budget, backoff, receiver) copied but the app-side half is absent |
| 8 | `lib/builtin/builtin_server.dart` | 725 | partial | `:engine:runtime` | ensureBuiltinProfile (create the loopback:4097 'This phone' profile with a generated password) not ported: wiring needs an existing oc.builtinServerOwner; looksLikeInAppServer/isInAppServer recognition not ported; Wiring builds PhoneServerStarter without DeliberateStop, so a confirmed start never clears oc.phoneServerStopped.<id>; AppOpenStart engine not used: wiring has a simplified openStart (no one-retry, no stale/young server rules); Owner binding before explicit start, handover/bringingUp state and start-generation counter missing |
| 9 | `lib/builtin/phone_server_healing.dart` | 675 | partial | `:engine:runtime` | AppOpenStart engine (port of startForLaunch) is not wired; BuiltinRuntime.openStart is a simplified once-per-process start without the retry or stale/young rules (B-LINUX-152); No background idle heartbeat renewal every 15 s; No local-agent CPU lease (B-LINUX-155); No reconnect back-off / Try again bypass (B-LINUX-153) |
| 10 | `lib/builtin/project_export.dart` | 644 | missing | `:android:platform` | No project export plan/scanner, credential classifier or native zip writer; No storage measurement for Manage space (B-STATE-028) |
| 11 | `lib/builtin/setup/components.dart` | 625 | missing | `:engine:runtime` | phone setup engine not ported: LinuxVoiceWiring.startSetup fails with NotInstalled when Linux is absent and updateOpenCode/repair return ServerStartUnavailable; no native component registry or OpenCode install script |
| 12 | `lib/builtin/quota_collector_host.dart` | 623 | missing | `:engine:integrations` | :engine:integrations not created; no collector setup, token staging or loopback 4195 quota reader natively |
| 13 | `lib/builtin/migration/termux_migration_transport.dart` | 621 | missing | `:android:termux` | Loopback receiver/transport for Termux move-out not ported (B-SEC-193) |
| 14 | `lib/builtin/agents/agent_scripts.dart` | 601 | partial | `:engine:agents` | install/check/bootstrapUser/rollback/terminalLauncher script texts are not ported; AndroidAgentHost.auth expects the caller to pass the authProbe/signOut script text, which no native code authors; engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this |
| 15 | `lib/builtin/migration/termux_migration_controller.dart` | 601 | missing | `:android:termux` | Termux migration controller and its UI not ported (D7 says it ships at the switch) |
| 16 | `lib/builtin/migration/migration_archive_store.dart` | 560 | missing | `:android:termux` | Termux move-out archive importer not ported; :android:termux not created |
| 17 | `lib/builtin/reply_watch.dart` | 552 | partial | `:engine:transcript` | Per-turn timing exists, but This phone's persisted reply speed (oc.replySpeed.inApp) is never written natively, only cleared on reset; No reply speed line on This phone |
| 18 | `lib/paseo/chat_feed_source.dart` | 538 | partial | `:protocol:paseo` | PaseoChatFeed is never instantiated outside protocol code; app builds OpenCodeChatFeedSource over plain session listings for Paseo profiles too (PreviewRuntime.rebuildFeed); no adapter from PaseoChatFeed to engine/agents ChatFeedSource; backgroundTasksRunning port is a hard-coded false stub (TODO S5-PASEOP1); phone Claude asking-mode (defaultProviderModes claude->default) not configured by AppPaseoGatewayFactory |
| 19 | `lib/builtin/thermal_guard.dart` | 536 | partial | `:engine:runtime` | engine only: ThermalPolicy is never constructed and no thermal listener runs; No thermal notice/shell line or setting (B-ANDR-110), no once-per-episode notification; Readings not handed to the diagnostics report (B-ANDR-069); Team pause/resume parts are after-switch (D6) |
| 20 | `lib/builtin/linux_services/rootfs_linux_services.dart` | 517 | missing | `:android:linux` | No rootfs services file store or svc service driver natively |

## Gaps the catalog does not cover (untested behaviour the catalog missed)

The behaviour catalog was built from Flutter tests, so these gaps have no row to drive a native test. Each one needs a top-up row (append-only, `native/catalog/behaviors.jsonl`) or a decision.

### A. Files whose main behaviour no Flutter test covers, or that have no catalog row

| File | Status | Lines | Rows | What is uncovered |
|---|---|---:|---:|---|
| `lib/builtin/agents/agent_host_cleanup.dart` | partial | 23 | 1 | AgentHomeCleaner (ProfileResourceCleaner) is not contributed to the app graph: only StashDeletionCleaner is bound in PromptsWiring; engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this |
| `lib/builtin/agents/gen_ui_connector_search_js.dart` | missing | 162 | 1 | no native connector-search bridge script or :engine:integrations module (not yet created) |
| `lib/builtin/builtin_server_restore.dart` | partial | 55 | 2 | RestorePhase/RestoreReason enums exist but the app binding never parses BuiltinLinux.restorePhase/restoreReason into RuntimeStatus; Restore recipe (version, profileId, runtime) is not sent from the app side |
| `lib/builtin/deliberate_stop.dart` | partial | 42 | 0 | engine only: DeliberateStop is never constructed; wiring passes no deliberateStop to PhoneServerStarter and stoppedByPerson reads native builtin_server_recovery prefs instead; Stop on This phone does not write the mark natively |
| `lib/builtin/phone_agent_work_watch.dart` | partial | 192 | 0 | no native observer turns Paseo busy/idle into lease hold/renew/end with the 15 min/5 min/6 h rules; engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this |
| `lib/builtin/setup/termux_setup_finish.dart` | dropped | 279 | 0 | D7: Termux setup is not ported; only Termux migrate-out ships |
| `lib/builtin/team/builtin_team_start_progress.dart` | after-switch | 142 | 0 | AI Team is after-switch (D6) |
| `lib/orchestration/adapters/gascity/gascity_work_edits.dart` | after-switch | 118 | 0 | No native board edits (D6); its cited rows B-BUILD-004/005 were amended to drop the Team ledger |
| `lib/orchestration/models/merge.dart` | after-switch | 297 | 0 | Not ported (D6) |
| `lib/orchestration/models/scheduled_job.dart` | after-switch | 74 | 0 | Not ported (D6) |

The six `abi_*`/`int64_max_*` files are untested too, but they are web conditional-import glue (not-needed).

### B. Wiring gaps: native engine exists and its unit rows can pass, but the app never reaches it (21 files)

Catalog rows are mostly unit-level (`native_level: unit`). A row turns green as soon as the engine class passes, even when nothing in `native/androidApp` builds it. No row checks that these are reachable:

- `lib/builtin/agent_helper_diagnostics.dart` — engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this
- `lib/builtin/agents/agent_components.dart` — engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this
- `lib/builtin/agents/agent_host_cleanup.dart` — engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this
- `lib/builtin/agents/agent_removal_scripts.dart` — engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this
- `lib/builtin/agents/agent_scripts.dart` — engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this
- `lib/builtin/agents/agent_sign_in.dart` — SignInTerminal screen/ports not wired in androidApp; engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this
- `lib/builtin/agents/paseo_scripts.dart` — engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this
- `lib/builtin/agents/phone_agents_host.dart` — engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this
- `lib/builtin/app_exit_recovery.dart` — engine only: LaunchIntake/PolicyExitHistory are not constructed or bound in any androidApp wiring
- `lib/builtin/builtin_linux.dart` — Removal with measured freed bytes (B-LINUX-029/030) not reachable
- `lib/builtin/deliberate_stop.dart` — engine only: DeliberateStop is never constructed; wiring passes no deliberateStop to PhoneServerStarter and stoppedByPerson reads native builtin_server_recovery prefs instead
- `lib/builtin/heat_tier.dart` — engine only: HeatTierPolicy/AgentThrottle are not constructed in any wiring and no thermal/battery listener feeds them; 'Always full speed' preference not wired; Priority script (renice helper tree, B-LINUX-254) not reachable from the app
- `lib/builtin/linux_services/linux_services_heat.dart` — engine only: ServicesHeatHold is never constructed; no hold-file binding and no thermal listener
- `lib/builtin/phone_agent_work_watch.dart` — engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this
- `lib/builtin/phone_server_healing.dart` — AppOpenStart engine (port of startForLaunch) is not wired; BuiltinRuntime.openStart is a simplified once-per-process start without the retry or stale/young rules (B-LINUX-152)
- `lib/builtin/process_budget.dart` — engine only: ProcessBudgetGuard is never constructed and admits no agent/setup work
- `lib/builtin/setup/claude_scripts.dart` — engine only: :android:agenthost is not a dependency of native/androidApp and AgentsWiring binds PhoneAgents to NoPhoneAgents, so no phone-agent screen reaches this
- `lib/builtin/thermal_guard.dart` — engine only: ThermalPolicy is never constructed and no thermal listener runs
- `lib/paseo/gateway.dart` — missing native facets: ServerOperationsGateway, HostAgentProvider/Permission (only public methods, unreachable from app), GenUiHistory/RecoverySnapshot/LoadGuard, HostDaemonUpdate, AgentFeature, ProviderConversationImport, AgentTurnFailure (lastAgentTurn), AgentHandoffTarget
- `lib/paseo/gateway/idle_guard.dart` — PaseoIdleGuard is never constructed or called (no settle/markAwake call sites) so list-time guarding is inert
- `lib/paseo/gateway/permissions.dart` — ACP host permissions only via public PaseoGateway methods with no domain facet; unreachable from app/features

Suggested top-up: one Android-level row per journey ("from This phone, Start/Stop/Restart reaches the real starter", "Agents hub lists phone agents from `AndroidAgentHost`", "heat tier throttles agents in the running app", "Paseo chats appear through `PaseoChatFeed`") checked by an androidApp graph test.

### C. Script and shell bodies

Rows for the install/removal/setup scripts (`agent_scripts`, `agent_removal_scripts`, `paseo_scripts`, `gen_ui_install_scripts`, `setup_scripts`, `aiteam_scripts`, `oc_svd_script`, `claude_scripts`) check selected properties: rollback, pinned versions, marker lines. They do not check the full script text. A native port must copy each script body from the Flutter file itself and diff it against the Flutter output. The catalog alone is not enough.

## How to use the record

```bash
# open work for one owner module
jq -c 'select((.status=="partial" or .status=="missing") and .target_module==":engine:runtime") | {file,gaps}' docs/native/inventory/builtin-orch-paseo.jsonl
# rows to port for a file
jq -r 'select(.file=="lib/builtin/thermal_guard.dart") | .catalog_rows[]' docs/native/inventory/builtin-orch-paseo.jsonl
```
