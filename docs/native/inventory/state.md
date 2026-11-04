# Inventory: `lib/state` → native app

Machine-readable record: [`state.jsonl`](state.jsonl) — one line per Flutter file, 228 files, 70144 lines of Dart. Base: `native/p12-integrate-2026-11-03` (waves 1–7). Analysis only; no product code was changed.

Status meanings: **done** = every user-visible behaviour and rule exists natively (native files named); **partial** = some (gaps listed; includes engines ported but not wired into `androidApp`); **missing** = nothing native; **not-needed** = Flutter-only glue; **after-switch / dropped** = per `native/catalog/scope.jsonl` and plan/02 §3.7 (AI Team, FactsBook, Gas City, automatic-activity recorder = after-switch; A2A, web/browser, Termux-as-host, session-address links = dropped).

## Totals by status

| Status | Files | Dart lines |
|---|---:|---:|
| done | 25 | 5722 |
| partial | 94 | 34801 |
| missing | 59 | 14830 |
| not-needed | 3 | 467 |
| after-switch | 39 | 11657 |
| dropped | 8 | 2667 |
| **total** | **228** | **70144** |

## Totals by kind

| Kind | Files | done | partial | missing | not-needed | after-switch | dropped |
|---|---:|---:|---:|---:|---:|---:|---:|
| state | 215 | 23 | 92 | 58 | 1 | 36 | 5 |
| platform-bridge | 6 | 2 | 0 | 1 | 1 | 2 | 0 |
| other | 3 | 0 | 0 | 0 | 0 | 0 | 3 |
| background | 3 | 0 | 2 | 0 | 0 | 1 | 0 |
| dead | 1 | 0 | 0 | 0 | 1 | 0 | 0 |

## Totals by target module (switch scope: partial + missing)

| Target module | partial+missing files | of which missing | Dart lines |
|---|---:|---:|---:|
| `:engine:integrations` | 24 | 20 | 8170 |
| `:engine:agents` | 23 | 6 | 7098 |
| `:feature:chats` | 14 | 6 | 4064 |
| `:engine:settings` | 13 | 0 | 3492 |
| `:engine:runtime` | 13 | 6 | 2834 |
| `:engine:requests` | 12 | 2 | 4599 |
| `:engine:project` | 10 | 6 | 2095 |
| `:engine:attention` | 7 | 2 | 3400 |
| `:engine:connection` | 7 | 0 | 4364 |
| `:engine:composer` | 6 | 0 | 1549 |
| `:android:background` | 3 | 2 | 882 |
| `:engine:profiles` | 3 | 0 | 2174 |
| `:engine:delivery` | 3 | 2 | 1411 |
| `:protocol:oc2` | 2 | 1 | 1079 |
| `:feature:servers` | 2 | 1 | 322 |
| `:engine:media` | 2 | 1 | 160 |
| `:android:termux` | 2 | 2 | 391 |
| `:feature:settings` | 1 | 0 | 170 |
| `:protocol:codex` | 1 | 0 | 104 |
| `:engine:transcript` | 1 | 0 | 235 |
| `:android:widgets` | 1 | 0 | 600 |
| `:protocol:paseo` | 1 | 1 | 305 |
| `:android:platform` | 1 | 0 | 21 |
| `:feature:about` | 1 | 1 | 112 |

Modules named as targets that do not exist yet in `native/`: `:engine:integrations`, `:engine:project`, `:protocol:codex`, `:android:termux` (and, after the switch, `:engine:team`, `:engine:factsbook`). Most "missing" rows sit behind these.

## The 20 biggest missing/partial files

1. **`lib/state/connection/phone_agents.dart`** (1547 lines, partial, → `:engine:agents`): Runs agents on this phone: merges the OpenCode and Paseo feeds, opens a backend per agent conversation, and offers install, remove, check, sign-in, agent/model choice and helper resume.
   - engine-only, not wired: app binds PhoneAgents to NoPhoneAgents and :android:agenthost is not an app dependency
   - install/remove/check/sign-in and agent chat per-conversation backend not reachable
   - agent models list (agentModels) missing
2. **`lib/state/profiles.dart`** (1449 lines, partial, → `:engine:profiles`): ProfileStore: saved servers and their secrets, active server, deletion sweep of per-profile keys, sign-in reset, secure-storage health, and per-server location, model, agent, variant and provider-runtime choices.
   - per-server location and recent locations (oc.location.<id>, oc.recentLocations.<id>, setLocation/forgetRecentLocation) not persisted natively
   - model library (oc.modelLibrary) and per-session model choices (oc.sessionModels) not ported
   - provider-runtime refreshed/unloadable flags not persisted (only the sweep shape)
   - loadForWidgetAction lives in android/widgets, not the store
3. **`lib/state/profile_monitor.dart`** (1279 lines, partial, → `:engine:attention`): Background monitor of saved servers: bounded sequential polls of pending requests and busy sessions, policy/Wi-Fi gating, request alerts and check-in reminders reconciled with what is posted, routes and drain-before-deletion.
   - :engine:attention (S4-ATTNE) not built: no polling monitor of non-live servers
   - busy-interval tracking (oc.monitorBusy), check-in reminders and posted-alert reconciliation (oc.monitorAlerts) missing
   - monitor routes (oc.monitorRoutes) and automation-consent gating of monitor reads missing
   - only the alert routing table (AlertPolicy: quiet hours, Wi-Fi only, app visible) and the live-connection reply watch exist
4. **`lib/state/connection.dart`** (1132 lines, partial, → `:engine:connection`): The ConnectionController itself: holds the active server connection, profile store, agent and side backends, stop/interrupt memory, finished-unseen tags and turn-still-running checks, and stitches every part file together.
   - Part-file split is Flutter glue; native splits by engine and per-connection graphs
   - ConnectionSession is a slice-0 skeleton: status only goes Connecting/Connected/Disconnected
   - No finished-unseen 'Done' or 'Stopped' row tags (ChatStatus has only NeedsYou/Running/Failed/Idle)
   - No server interruption reason per conversation
   - No checkTurnStillRuns re-check before calling a reply dropped
   - Agent backends and side connections (Paseo, another saved server) not hosted beside the main connection
5. **`lib/state/gen_ui_state.dart`** (1121 lines, partial, → `:engine:requests`): The agent-card controller: admits cards from authoritative transcript parts, sends answers exactly once, recovers card state from bounded history and fences profile close/deletion.
   - background recovery across phone/list sources (skip sleeping sessions, retry with backoff, coverage watermarks) not wired; only the open conversation is read
   - connector card payload consent across suspension/replacement (B-AGENT-131) missing
   - no connector suggestion surface on cards
6. **`lib/state/connection/queue.dart`** (1037 lines, partial, → `:engine:delivery`): Runs the offline prompt queue: holds online sends with markers, flushes oldest first, keeps uncertain sends for review, checks receipts, evicts with a notice and moves or removes queued prompts.
   - moving queued prompts to another destination and its Undo (B-NAV-046) missing
   - rich (model/file) send path is unfinished (RUNBOOK §7 open findings)
7. **`lib/state/provider_quota_monitor.dart`** (980 lines, partial, → `:engine:integrations`): Background-polls enrolled provider quota sources with consent, shared alert rules (Wi-Fi only, quiet hours), deduped threshold alerts and the Usage widget snapshot.
   - no native quota monitor: enrolment consent, fair rotation, one bounded read at a time, background polling within FGS limits
   - no threshold alert delivery or dedupe across restart/account rotation
   - no producer of oc.widgetUsage.<id>: native widget only renders Flutter-era keys
   - Settings "Quota alerts" row hidden (usageAlertsShown never true)
   - no deletion/cancellation handling for oc.quotaMonitor.<id> beyond generic sweep
8. **`lib/state/connection/gen_ui.dart`** (958 lines, partial, → `:engine:requests`): Runs agent cards (generative UI): availability and setup status per agent, the per-profile on/off choice, waiting cards for chats and feed rows, answering, undo and delivery state, including phone agents.
   - Phone-agent (Paseo) cards not supported; only a connected OpenCode 2 server qualifies
   - Waiting cards do not reach Conversations rows or attention
   - Managed-runtime setup/installer path not ported (setup never installs)
   - Card search helper publication not ported
9. **`lib/state/connection/permissions.dart`** (872 lines, partial, → `:engine:requests`): Tracks waiting permission asks, answers them once (once/always/reject with optional OpenCode 2 message), hydrates them after reconnect, and answers automatically for sessions or servers set to auto-approve.
   - reject-with-message is in the engine but the chat request slot rejects without a note
   - recent auto-approvals indicator and auto-approval failure text are engine-only (engine/agents AutoApproval not wired)
   - feed-row permission routing for phone-agent rows missing (phone agents not wired)
10. **`lib/state/connection/connect.dart`** (867 lines, partial, → `:engine:connection`): Connects and disconnects a saved server: builds the transport, loads conversations and requests before the catalog, plain failure copy, mid-session password rejection, managed-runtime migration and switch preflight, and waits for a woken transport before actions.
   - No mid-session 'Update password' state when Basic auth is rejected after connect
   - No 'retrying will not start a server' line after three failed attempts, nor app-managed server wording (B-NAV-009)
   - Managed in-app runtime switch preflight not wired (phone Linux adoption not implemented)
   - Managed OpenCode 1 to 2 migration confirmation not ported
   - No prepareActionTransport wait for a woken transport; no schedule-owned phone send bound
   - Flavor re-detection for OpenCode 1 not needed (OC1 never); Termux wake lock out of scope (NG7)
11. **`lib/state/connection/catalog.dart`** (793 lines, partial, → `:protocol:oc2`): Loads and caches the provider, model and agent catalog per server and folder, shares in-flight loads, reloads on change events, and replaces stale saved model/agent choices.
   - Catalog is read fresh on each picker/header load; no freshness window or reuse window per server and folder
   - No shared in-flight load or single queued reload
   - No reload on catalog.updated / config.updated / agent.updated events
   - No per-session catalog for provider-bound runtimes (phone agents)
   - OpenCode 1 unloaded-provider heal not needed (OC1 client never ported)
12. **`lib/state/connection/sessions.dart`** (777 lines, partial, → `:feature:chats`): Lists conversations: refresh, paging, busy and retry status, polling fallback, create, rename, delete, archive and pins.
   - pin/unpin action missing (pins are only read from migrated oc.sessionPins for launcher shortcuts)
   - 5-second polling fallback missing
   - live-observed message completion set missing
13. **`lib/state/connection/chat_feed.dart`** (751 lines, partial, → `:feature:chats`): Derives the Conversations feed rows for the OpenCode connection: ordering, temporary folders, project summaries, git badge, preview line, last-used project, run-signal overrides of stale status reads, and a revision that moves only on visible change.
   - Last-used project per profile (persisted and swept on delete) not ported
   - startChatIn (open project, create chat, send) not ported
   - Live run events do not override a stale status read; the feed re-reads the whole listing on every session/run event instead of a bounded 2-second debounce
   - No Git badge rule for projects
   - No redacted tail-cache preview line
14. **`lib/state/setup_controller.dart`** (713 lines, missing, → `:engine:integrations`): Review coordinator for AI setup changes on a server: builds reviewable diffs, applies atomically with outcome verification and undo, records uncertainty and audit metadata.
   - no native setup controller; oc2 sets setupConfigRead/Write=false
   - no diff review, apply verification, undo or uncertain-write handling
   - credential stripping of config reads (B-SEC-165) not ported
15. **`lib/state/connection/integration_commands.dart`** (704 lines, missing, → `:engine:integrations`): Runs integration commands on the selected server: executable authentication with explicit consent, status and cancel, provider credential activate/rename/remove, MCP server removal, and reload after config change.
   - No integration command, credential management or MCP server removal natively (domain interfaces only, unimplemented)
   - No reload after configuration change
16. **`lib/state/usage_subscriptions_controller.dart`** (670 lines, missing, → `:engine:integrations`): Gathers plan subscriptions from every source across all connections into one list with cached numbers, staleness, alert thresholds and the Usage widget feed.
   - no subscriptions model: refresh across sources, cached oc.usageLast.<id> numbers shown before refresh, older-never-replaces-newer
   - offline/stale, rate-limit, broken-reader and cannot-read-yet states
   - default 80% watch with per-subscription overrides
   - widget feed (oc.widgetUsageSubs) producer
   - profile removal cleanup of cached numbers and overrides
17. **`lib/state/connection/lifecycle.dart`** (659 lines, partial, → `:engine:connection`): Suspends network work when the app goes to the background and recreates one transport on resume, reconciling by refetch; shares concurrent resumes and offers a reconnect that keeps the location and shown data.
   - Stream visibility is never bound (always Foreground): streams do not suspend in the background or resume on return
   - No explicit Reconnect that keeps the retained location and rendered data
   - Opted-in background mode keeping the connection live not ported
   - Folder-open failure kept as plain words with Details not ported
18. **`lib/state/phone_host.dart`** (642 lines, partial, → `:engine:runtime`): One This-phone model for OpenCode on the phone (in-app Linux or Termux): state, version, storage, failure kind, start/stop, log reading and the OC1-to-OC2 history switch admission.
   - in-app host status/start/stop/restart/update are native; Termux host kind is dropped (NG7)
   - no server log reading on This phone
   - history switch admission (refuses every failure, identifies separate history; B-LINUX-142) not ported
   - failure classification into install vs start problems and bytesUsed not evidenced
19. **`lib/state/connection/locations.dart`** (636 lines, partial, → `:engine:connection`): Chooses, saves, restores and validates the open project folder and workspace per server, blocks new chats in a home folder, probes typed folders, and keeps recent projects.
   - Native has no single selected location; per-folder state is replaced by an all-projects feed (design change)
   - No restore and revalidation of a saved project folder
   - No probe of a typed folder before opening it
   - No home-folder workspace-choice block before a new chat
   - Only recent opened folders are kept (oc.openedProjects.<profileId>); no forget-recent action
20. **`lib/state/connection/models.dart`** (627 lines, partial, → `:engine:agents`): Chooses the model, agent and variant per conversation and as profile default, remembers model display names and per-session display models, favorites, cycles models and loads the catalog.
   - model picker and session selection live in the unfinished composer working tree; not in the delivered app (RUNBOOK §7, open interleaving finding)
   - AgentSelection (oc.modelNames) is engine-only, not wired
   - oc.sessionDisplayModels display-model cache missing
   - model favorites toggle and cycle-model missing
   - variant validation against the catalog (B-STATE-112) not evidenced
   - reloadProviderRuntime missing

## Gaps not covered by any catalog row (untested behaviour the catalog missed)

The behaviour catalog was mined from Flutter tests, so these behaviours have no row and no parity test will notice them. Each needs a new catalog row (or an owner decision to drop it).

67 switch-scope files carry 106 uncatalogued gaps.

- `lib/state/appearance_guide.dart`
  - no guided Appearance (chips/Next bar/dots/"Guide me again") natively; AppearancePage is one flat page (S4-APPEAR)
  - oc.appearanceGuide record (seen, lastIn, last, done) not read or written
- `lib/state/automation_policy.dart`
  - team supervision/auto-merge fields belong to after-switch AI Team
- `lib/state/builtin_server_owner.dart`
  - no native writer of oc.builtinServerOwner for a newly set-up phone profile
- `lib/state/byo_host_controller.dart`
  - Flutter has no UI entry for BYO host; product path must be designed before porting
- `lib/state/chat_notice_memory.dart`
  - ChatNoticeMemory show-once/auto-fold notes
  - WithdrawnPromptMemory duplicate-prompt hiding
  - AgentTurnFailureMemory restart persistence of failure reason
- `lib/state/connection.dart`
  - No server interruption reason per conversation
  - No checkTurnStillRuns re-check before calling a reply dropped
- `lib/state/connection/agent_handoff.dart`
  - No plain last-state words (Working / Waiting for you / Failed / Unknown) for the handoff record
  - No hold/release of the agent host connection while the sheet may write
- `lib/state/connection/attention.dart`
  - PreviewRuntime never fills OpenCodeListing.needsYou, so Conversations rows never show Needs you from pending requests (only the widget derives it)
  - No unknownAttentionProfileCount / 'all caught up' claim
- `lib/state/connection/catalog.dart`
  - Catalog is read fresh on each picker/header load; no freshness window or reuse window per server and folder
  - No shared in-flight load or single queued reload
  - No reload on catalog.updated / config.updated / agent.updated events
  - No per-session catalog for provider-bound runtimes (phone agents)
- `lib/state/connection/chat_feed.dart`
  - Last-used project per profile (persisted and swept on delete) not ported
  - startChatIn (open project, create chat, send) not ported
  - Live run events do not override a stale status read; the feed re-reads the whole listing on every session/run event instead of a bounded 2-second debounce
  - No Git badge rule for projects
  - No redacted tail-cache preview line
- `lib/state/connection/connect.dart`
  - No mid-session 'Update password' state when Basic auth is rejected after connect
  - No prepareActionTransport wait for a woken transport; no schedule-owned phone send bound
- `lib/state/connection/drafts.dart`
  - Camera photo recovery (DraftPhotoRecovery) is engine-only, not wired
- `lib/state/connection/events.dart`
  - Terminal (pty) events not handled
  - No stop of a disallowed reconnect for managed runtimes
- `lib/state/connection/feed_forms.dart`
  - Conversations rows cannot answer forms; the form engine is not wired at all
  - No per-directory form inventory for other folders
- `lib/state/connection/feed_permissions.dart`
  - Conversations rows cannot answer permissions; requests are answered only inside the chat
  - No feed-scoped request identity separate from the wire id
- `lib/state/connection/feed_question_reads.dart`
  - No per-directory question inventory for conversations in other folders
  - Rows from other projects never show their waiting question
- `lib/state/connection/feed_questions.dart`
  - Conversations rows cannot answer or reject questions; only the chat can
- `lib/state/connection/forms_inbox.dart`
  - Global (MCP elicitation) forms not shown
- `lib/state/connection/integration_auth.dart`
  - No recoverable pending-attempt store
- `lib/state/connection/integration_commands.dart`
  - No reload after configuration change
- `lib/state/connection/lifecycle.dart`
  - Stream visibility is never bound (always Foreground): streams do not suspend in the background or resume on return
  - No explicit Reconnect that keeps the retained location and rendered data
- `lib/state/connection/locations.dart`
  - No restore and revalidation of a saved project folder
  - No probe of a typed folder before opening it
  - No home-folder workspace-choice block before a new chat
- `lib/state/connection/models.dart`
  - reloadProviderRuntime missing
- `lib/state/connection/monitors.dart`
  - background profile monitor of saved servers missing
  - provider quota monitor and quota alerts missing (toggle copy exists only)
  - Paseo usage monitor and usage-subscriptions controller missing
  - notify-rule changes not shown to re-sync the background services natively
- `lib/state/connection/pending_reads.dart`
  - refetch on every Connected and in-flight safety exist; the wake ordering (sessions first, requests read once per wake) is not evidenced
- `lib/state/connection/permissions.dart`
  - reject-with-message is in the engine but the chat request slot rejects without a note
- `lib/state/connection/phone_agents.dart`
  - agent models list (agentModels) missing
- `lib/state/connection/phone_agents_auto_update.dart`
  - only the row's auto-update state exists; the decision rules (nothing running, one try per pin per run, Wi-Fi for big downloads) not found
- `lib/state/connection/phone_agents_backend.dart`
  - banner Restart and watchdog recovery missing
- `lib/state/connection/phone_agents_cache.dart`
  - only key names and the deletion sweep/count exist; the first-paint row cache (oc.agentFeed, oc.mainFeed, oc.agentSignedOut) is not written or read
- `lib/state/connection/phone_agents_routes.dart`
  - rows open on their captured server for OpenCode feeds only; agent feed rows and their backend replacement/teardown drain missing
- `lib/state/connection/phone_agents_saved.dart`
  - saved-row first paint and live replacement by folder missing (only the key sweep exists in AgentOwnership)
- `lib/state/connection/phone_agents_startup.dart`
  - helper startup presentation window and row poll missing
- `lib/state/connection/prompt_shelf.dart`
  - sent-prompt history navigation is in :feature:chat-composer, which the app does not include
- `lib/state/connection/questions.dart`
  - answering from a Conversations feed row identity (phone-agent rows) missing
- `lib/state/connection/quiet_reconnect.dart`
  - 15-second quiet reconnect window missing (chat shows reconnecting at once)
  - session refresh that ends the quiet window missing
  - agent helper-exit reads on link loss missing
- `lib/state/connection/request_events.dart`
  - form events: FormRequests engine exists but nothing constructs it
  - session inbox events (InboxChanged) are not consumed by any engine or screen
- `lib/state/connection/session_events.dart`
  - events are modelled and applied per chat and feed, but session usage events and the running shell-command line (for status surfaces) are not used
  - provider retry backoff per session not surfaced in lists
- `lib/state/connection/sessions.dart`
  - 5-second polling fallback missing
  - live-observed message completion set missing
- `lib/state/connection/side_connections.dart`
  - per-server 'show in Conversations' switch (oc.chatListShown) and its default rule missing
  - retry of one failed side source missing
- `lib/state/connection/status.dart`
  - status grace period and background-return quiet start missing
  - retry countdown with Try now missing
  - agent command hold-off and 'Android stopped the agent' notices missing
  - server upgrade recording missing
- `lib/state/connection/turn_stall.dart`
  - keeping open turns and running tools across reconnect not evidenced
- `lib/state/connection/widgets.dart`
  - model display name missing
  - steer mode and on-demand row actions missing
- `lib/state/connection/worktrees.dart`
  - session move/warp and console-organization switch missing (no catalog row covers them)
- `lib/state/connection_check.dart` (no Flutter test at all)
  - manual connection check steps and reasons
- `lib/state/connector_card_host.dart`
  - connector card resolution and connect/OAuth flow from chat has no catalog row (connector_card_test only)
- `lib/state/consent_owners.dart`
  - showing-invitation set by sessionID/requestID (card rebuild keeps its invitation) not ported
- `lib/state/continue_with.dart`
  - only same-server model switch exists (ContinueChoice); no agent choices, block reasons, sign-in hop, or AgentHandoffController equivalent
- `lib/state/continue_with_connection.dart`
  - same-source proof (saved server, location generation, project / managed phone home) missing
  - agent folder hold/release around the sheet missing
- `lib/state/delayed_answers.dart`
  - held answer shared between Conversations list and chat
- `lib/state/download_size.dart`
  - DownloadSize.sum (only exact/lower-bound parts add up; any estimate/unknown downgrades to lower bound) missing natively
- `lib/state/effects.dart`
  - haptics preference
- `lib/state/free_model_notice.dart`
  - This phone screen free-model notice (with sign-in way out) not native: FreeModel.uses only called by the chat header
  - isolated-task exclusion not verified in native header
- `lib/state/glass_choice.dart`
  - stored and applied, but no Glass control on the native Appearance page
- `lib/state/in_app_quota_enroller.dart`
  - no In-app Ubuntu quota enrolment (read once per provider, enrol answering ones at 80%, skip signed-out providers, per-provider result)
- `lib/state/local_agent_server.dart`
  - create-or-update of the single saved helper server
- `lib/state/mcp_catalog_host.dart` (no Flutter test at all)
  - phone-host lookup for the MCP catalogue is untested and uncatalogued
- `lib/state/notification_preferences.dart`
  - check-in reminder duration (oc.notify.checkInAfterMinutes) and quota-alerts choice (oc.notify.quotaAlerts) have no native setting or reader
- `lib/state/phone_agents_auto_update.dart`
  - metered/unmetered download threshold and composer Send block are not named by any row
- `lib/state/profiles.dart`
  - recent locations persistence
  - model library / per-session model persistence
- `lib/state/server_trust_probe.dart`
  - probe of a confirmed cleartext origin
  - probe with certificate pin
- `lib/state/session_read_state.dart`
  - persisted read watermarks across restart
- `lib/state/shared_storage_gate.dart`
  - All files access gate has no catalog row
- `lib/state/tailscale_address.dart`
  - isValidTailscaleAddress rules have no catalog row of their own
- `lib/state/termux_migration_offer.dart`
  - offer dismissal and deletion drain have no catalog row
- `lib/state/usage_overview.dart`
  - provider filter and model search over model rows (and their reset on range/scope change) missing
- `lib/state/whats_new.dart`
  - no native walkthrough: About > What's new opens the GitHub releases page
  - no seen-build tracking (oc.whatsNewSeenBuild), version steps table or "Show me" targets

Deferred/dropped files with uncatalogued behaviour (for the after-switch slices):

- `lib/state/claude_agent_trial.dart` (after-switch): claude_agent_trial_test / claude_agent_picker_test have no catalog rows
- `lib/state/factsbook/phone_zone.dart` (after-switch): phone time-zone offer for Memory has no catalog row (factsbook_phone_findings_test only)
- `lib/state/team_model.dart` (after-switch): team model spec validation has no catalog row (team_models_setup_test only)
- `lib/state/team_roles.dart` (after-switch): team roles storage and editing has no catalog row (team_roles_ui_test only)
- `lib/state/team_voice.dart` (after-switch): role voice mapping has no catalog row (team_voice_test only)

## Files with no Flutter test covering their main behaviour

- `lib/state/browser_secret_storage_io.dart` (dropped)
- `lib/state/connection_check.dart` (missing)
- `lib/state/mcp_catalog_host.dart` (missing)
- `lib/state/server_presentation.dart` (not-needed)

## Method

Each file was read and its native counterpart searched in `native/**` production code (class/function names, `oc.*` storage keys, copy strings), then checked for reachability from `native/androidApp` (graph wiring, `EntryInstaller` contributions). `catalog_rows` started from rows whose `sources` point into tests that target the file by name or direct import, then were pruned (rows about other files removed) and extended (tests exercising part-file methods of `ConnectionController`). Seven read-only batches; merged and validated by script (every listed file present, enums valid, every `native` path exists, every row id exists in `behaviors.jsonl`, no addresses or emails).
