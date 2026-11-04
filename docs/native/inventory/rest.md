# Inventory: INVENTORY-REST (199 Flutter files under lib/)

Machine-readable record: [`rest.jsonl`](rest.jsonl), one JSON line per Flutter file (keys: file, lines, kind, summary, native, status, gaps, portability, target_module, catalog_rows, untested). Base: `native/p12-integrate-2026-11-03` (waves 1–7). Catalog: `native/catalog/behaviors.jsonl` + `scope.jsonl`.

How a file got its catalog rows: rows whose `sources` are Flutter tests that import the file directly (close-named or narrow-import tests only), pruned by hand and topped up by searching the catalog. A gap marked **[uncataloged]** is a behaviour no catalog row covers — the catalog was built from Flutter tests, so these would be lost by a catalog-only port.

## Totals by status

| status | files | lines |
|---|---:|---:|
| done | 41 | 10,134 |
| partial | 69 | 81,590 |
| missing | 36 | 7,567 |
| not-needed | 16 | 262,186 |
| after-switch | 11 | 3,957 |
| dropped | 26 | 7,660 |
| **total** | **199** | **373,094** |

Line counts include ~322k lines of generated l10n output (8 files), seven per-locale files are not-needed; the 59k-line `app_localizations.dart` is partial and dominates that line total.

## Totals by kind × status

| kind | done | partial | missing | not-needed | after-switch | dropped | total |
|---|---:|---:|---:|---:|---:|---:|---:|
| api | 2 | 3 | 4 |  |  | 1 | 10 |
| background | 1 | 8 |  |  |  |  | 9 |
| dead |  |  | 1 | 1 |  | 2 | 4 |
| domain | 10 | 10 | 8 |  | 3 |  | 31 |
| generated |  |  |  | 8 |  | 1 | 9 |
| l10n |  | 1 |  |  |  |  | 1 |
| other | 1 | 10 | 4 | 1 | 3 | 12 | 31 |
| platform-bridge | 13 | 13 | 7 | 6 | 3 | 7 | 49 |
| protocol | 6 | 13 | 6 |  |  | 1 | 26 |
| screen |  | 1 | 1 |  |  |  | 2 |
| state | 5 | 8 | 5 |  | 1 | 1 | 20 |
| widget | 3 | 2 |  |  | 1 | 1 | 7 |

## Totals by folder

| folder | done | partial | missing | not-needed | after-switch | dropped | total |
|---|---:|---:|---:|---:|---:|---:|---:|
| a2a |  |  |  |  |  | 4 | 4 |
| acp |  |  |  |  |  | 2 | 2 |
| api2 | 7 | 17 | 5 | 1 |  |  | 30 |
| background | 1 | 11 |  | 1 | 1 |  | 14 |
| codex |  |  | 5 |  |  |  | 5 |
| demo |  |  | 3 |  |  |  | 3 |
| desktop |  |  |  |  | 2 |  | 2 |
| diagnostics |  | 5 | 4 |  |  |  | 9 |
| feedback |  | 2 |  |  |  |  | 2 |
| host |  |  | 4 |  |  |  | 4 |
| l10n |  | 1 |  | 7 |  |  | 8 |
| lib/main.dart |  | 1 |  |  |  |  | 1 |
| lib/manage_space_main.dart |  |  | 1 |  |  |  | 1 |
| local_llm |  |  |  |  | 4 |  | 4 |
| main_parts |  | 7 |  |  |  |  | 7 |
| network |  | 2 | 3 | 5 |  | 4 | 14 |
| platform | 9 | 8 | 4 | 2 |  | 2 | 25 |
| quota |  |  | 1 |  |  |  | 1 |
| scheduling | 5 | 4 |  |  |  |  | 9 |
| termux |  | 5 | 1 |  | 3 | 13 | 22 |
| update | 5 |  |  |  | 1 | 1 | 7 |
| voice | 14 | 5 |  |  |  |  | 19 |
| widgets_overlay |  | 1 | 5 |  |  |  | 6 |

## The 20 biggest missing / partial files

Generated l10n files are left out of this ranking (see the l10n rows in the jsonl).

1. **`lib/codex/gateway.dart`** — 1124 lines, missing, protocol, → `:protocol:codex` (agent-portable)  
   Implements the ServerGateway for the experimental Codex app-server (threads, turns, streaming, approvals, steering, reconnect/resume, project scoping) so a Codex profile works like any other server.  
   Native now: `Profiles.kt`  
   - no CodexGatewayFactory / Codex gateway: only BackendKind.Codex and the codexDirectory field exist in the profile model
   - thread list/resume scoped to the project, item-id coalescing of streamed and resumed messages
   - one-shot approvals invalidated by location change or socket loss
   - steer to authoritative active turn, default send while busy steers
   - reconnect stays degraded when a tracked thread cannot resume; auth failure to error channel
   - connection probe (health + scope, then close)
   - rejects remote cleartext and URL credentials; hides unsupported product operations
   - uncertain lost mutation never retried; uncertain first turn on new thread

2. **`lib/background/live_background.dart`** — 1003 lines, partial, background, → `:android:background` (platform-specific)  
   Controls background live mode (foreground service opt-in, pause/resume, Android timeout), posts privacy-safe coding alerts and the test notification, publishes the live status, and routes notification/status/widget actions back into the app over oc/background.  
   Native now: `Alerts.kt`, `AlertShade.kt`, `NotificationActionBody.kt`, `ReplyWatch.kt`, `Channels.kt`  
   - no BackgroundConnectionService shell or AndroidForegroundWork implementation: live mode (oc.keepLiveInBackground) cannot be turned on, and turn-on is not gated on notification permission + confirmed service start
   - Android foreground-service timeout does not turn live mode off or show the stopped-by-Android notice
   - no durable pause receipt: a paused live mode is not kept across cold start, and resume-only-after-native-activation (resumeFailed) is absent
   - notification Pause action and native pause payload parsing absent (PauseTileState holds only a paused flag)
   - live status (running count, pending count, freshest busy session) is never computed or published; StatusThrottle has no producer
   - [uncataloged] battery-optimization exemption request and open-notification-settings path from the live-mode setting not implemented (only the AppPermission enum exists)
   - widget actions from a live connection: LiveWidgetDispatch exists but no WidgetLiveDispatcher is bound in the app graph
   - after-switch: team_* alert kinds (AI Team decision/failed/review/completed/progress)

3. **`lib/api2/gateway_events.dart`** — 971 lines, partial, protocol, → `:protocol:oc2` (agent-portable)  
   Adapts the OpenCode 2 event stream into the app's event envelopes (rebuilding message/part shapes from step/text/tool events, settling lost tool results) and runs the location-scoped and global live channels.  
   Native now: `Oc2EventAdapter.kt`, `Oc2Event.kt`, `Oc2EventRegistry.kt`, `Oc2Gateway.kt`, `EventStreamLoop.kt`  
   - global all-location channel not advertised (native globalEventStream=false), so other-location events are not stamped for cross-project attention
   - consumers of terminal/worktree/workspace/plugin/websearch/integration refresh hints do not exist natively, so those adapted events go nowhere

4. **`lib/host/byo_host_ssh_runner.dart`** — 912 lines, missing, platform-bridge, → `:engine:integrations` (platform-specific)  
   Runs OpenSSH inside the built-in Ubuntu to adopt a user VPS: host-key pinning, key install via stdin, bundle digest check, tunnels, orphan cleanup and authenticated descriptor/revoke calls.  
   - no BYO SSH runner in the native Linux runtime
   - host-key pin and identity-change rejection before forwarding
   - private payload via stdin and bundle digest verified before execution
   - orphan host data recovery for this profile only
   - target cannot inject shell/SSH options; symlinked shared-files boundary refused
   - descriptor/revoke over authenticated loopback without redirects

5. **`lib/api2/gateway_mappers.dart`** — 902 lines, partial, protocol, → `:protocol:oc2` (agent-portable)  
   Maps OpenCode 2 wire data (sessions, messages, parts, permissions, providers, files, VCS, errors, health) to the common domain model and declares the OpenCode 2 capability matrix.  
   Native now: `Oc2CoreProjection.kt`, `Message.kt`, `Session.kt`, `Catalog.kt`, `Errors.kt`  
   - capability matrix diverges from Flutter: native advertises false for remoteShell, developmentServices, mcpChatConnect, mcpRuntimeAdds/Removals, integrationCredentials, integrationCommandAuth, pluginInventory, webSearch, shellSettings, gitInit, globalEventStream, worktreeCreate, forms, inbox, setupConfigRead, setupMcpInventory, terminal, projectManagement, commandReceipts (B-PROTO-016 uncited)
   - VCS status/file-status mapping not ported (no /vcs/status consumer)
   - shell-message mapping (status and shell id kept, failed shell as tool error) not evidenced by native test (B-PROTO-017 uncited)

6. **`lib/api2/gateway.dart`** — 859 lines, partial, protocol, → `:protocol:oc2` (agent-portable)  
   The OpenCode 2 live gateway: health, sessions, message pages, prompt/command/shell dispatch with permission preparation, permissions, forms and inbox, providers/agents, files, web search and event channels.  
   Native now: `Oc2Gateway.kt`, `Oc2GatewayFactory.kt`, `Oc2PromptSend.kt`, `Oc2PermissionPolicy.kt`, `Oc2SessionSelection.kt`  
   - forms and inbox (list/state/reply/cancel, inbox steer/queue/cancel with 400/409 mapping) not implemented: native capabilities forms=false, inbox=false
   - [uncataloged] automatic-approval path (serverRunsTools) never wired: preparePermissions always passes serverRuns=false
   - phone chat dispatch admission fence (beforeSessionDispatch / sessionDispatchSettled exactly once) absent
   - web search providers/search (/websearch) absent: webSearch=false
   - session diff via /vcs/diff (sessionDiff=false) and remote shell runShellCommand not exposed on the gateway (remoteShell=false; Git uses its own ServerGitShell)
   - unsupported health reply triggering connection re-detection not evidenced by any native test
   - gen-UI history / agent handoff history mixin not implemented on the gateway (genUi=false)

7. **`lib/diagnostics/perf_trace.dart`** — 844 lines, partial, state, → `:engine:diagnostics` (agent-portable)  
   Always-on span tracer with ring buffer, stats, redacted labels, HTTP request interceptor, navigation observer and prompt timeline for diagnostics.  
   Native now: `PerfTrace.kt`, `TraceLabels.kt`, `ReplyTiming.kt`  
   - no HTTP span interceptor in :core:net (method, path template, status, bytes)
   - [uncataloged] no route/navigation span observer
   - prompt timeline (sent/accepted/first token/done, refused prompt ends tracking) only partly covered by ReplyTiming
   - finished timings are not fed to a persisted report

8. **`lib/voice/voice_ui.dart`** — 787 lines, partial, screen, → `:feature:voice` (needs-design)  
   Voice model setup sheet, Settings › Voice typing page and the first-mic automatic setup sheet (consent, progress, verify, listen).  
   Native now: `VoiceSettingsScreen.kt`, `VoiceSettingsPresenter.kt`, `ComposerVoiceSlot.kt`, `VoiceEntries.kt`  
   - first-mic automatic setup sheet (pack chosen for you, mobile-data consent, progress, verify then listen) is absent; mic tap only points to settings
   - [uncataloged] setup sheet primary "Use Balanced" after download / "Done" when already in use is not reproduced as a sheet

9. **`lib/main_parts/root.dart`** — 662 lines, partial, other, → `:engine:entry` (needs-design, untested)  
   Root screen logic: tracks the top route, decides the start destination, auto-starts and connects the saved or in-app server, guards heat by pausing the phone AI Team, reports why Android last ended the app and restores the phone server, finishes an interrupted Linux reset, attaches phone setup, and offers the managed-server upgrade/move.  
   Native now: `RootDecision.kt`, `FirstRunFlow.kt`, `SettingsWiring.kt`, `ExitClassifier.kt`, `ThermalPolicy.kt`  
   - [uncataloged] last-exit report and restore of the phone server after Android ended the app: ExitClassifier exists but is not run at app start
   - [uncataloged] interrupted Reset Linux finished on next open (never left erased with no setup)
   - thermal guard pausing AI Team: after-switch (ThermalPolicy present, not wired)
   - auto-start in-app server then connect on cold start and phone-server healing at open (B-LINUX-152/156) not shown wired from the root
   - managed-server upgrade/move prompt on first launch (B-NAV-029)

10. **`lib/background/widget_snapshot.dart`** — 655 lines, partial, background, → `:engine:attention` (platform-specific)  
   Writes the compact recent-sessions snapshot for the home-screen widgets (titles, project, redacted reply excerpt, permission ask, servers, busy flags, opaque private target) and asks native to redraw, clearing it on profile deletion.  
   Native now: `PlatformWiring.kt`, `AndroidWidgetSurface.kt`, `WidgetPersistence.kt`, `WidgetProfileCleaner.kt`, `SessionWidgetRenderer.kt`  
   - no assistant reply excerpt (reasoning stripped, Markdown flattened) or generic tool step in the snapshot
   - permission ask fields (question, kind, file, folder) not written
   - project list / project scope for the Conversations widget not written; project line is the raw project name
   - private widget target (oc.widgetTarget.<profile>) never written (publish passes a null target), so widget actions have no route
   - run identity and steer-mode fields widget actions need (runMarker, startedAt, steerMode) not written
   - phone-server row always phone=false and server "since" kept only in memory
   - [uncataloged] other-project / other-connection conversation rows (WidgetOtherConversation) not produced

11. **`lib/main.dart`** — 649 lines, partial, other, → `:engine:entry` (needs-design)  
   App entry point and root widget: starts trace/crash capture, wraps everything in the bootstrap gate, builds MaterialApp with theme pack, locale, text scale, named routes, notice line and lifecycle suspend/resume, and owns the share/launch/link/alert/schedule routing listeners and the once-after-update What's new sheet.  
   Native now: `OcApplication.kt`, `MainActivity.kt`, `App.kt`, `ShellNavigator.kt`, `AppRoute.kt`  
   - engine/entry AppBootstrap (ADR 9.7 ordered start-up, safe mode) exists only with tests; OcApplication/MainActivity do not run it
   - routing listeners are a simplified PlatformHub.route; EntryRouter (wait-for-connection, switch-server, consumed-with-notice) is not wired (MainActivity comment: slice 2)
   - no native test cites B-NAV-086 (What's new once after update, never on fresh install)
   - [uncataloged] lifecycle: suspend connection on pause/hidden and resume + in-app server healing on resume as one app-level hook
   - desktop window size/position/icon restore: desktop-phase

12. **`lib/termux/bridge.dart`** — 624 lines, partial, platform-bridge, → `:engine:runtime` (platform-specific)  
   Drives Termux over the oc/termux RUN_COMMAND channel: capabilities/permission, setup jobs, script runs, managed-server status/start/restart/recovery, storage/tools, AI Team and local-agent verbs, plus the pinned OpenCode version and managed-URL check.  
   Native now: `BuiltinLinux.kt`, `PhoneServerStarter.kt`, `OpenCodeUpdate.kt`, `RuntimeStatus.kt`  
   - dropped: Termux as a runtime host (D7 / NG7; plan 11 row 64 "not ported") for setup/start/restart/recovery/tools verbs; built-in Linux equivalents live in :android:linux
   - Termux migrate-out host missing: no :android:termux module / TermuxHost (RUN_COMMAND run, permission request, result service) for B-LINUX-235, B-LINUX-237, B-SEC-192, B-SEC-193
   - install/update script rules (checksum-pinned download, leave supported install alone, never downgrade, keep old on failure: B-LINUX-075..077, B-SEC-092) not ported as native script templates (engine/runtime/src/commonMain/scripts absent)
   - setup-bridge redaction / no credential parameters dispatched (B-LINUX-246) and script safety rules (B-LINUX-242..244) have no native test citation

13. **`lib/diagnostics/crash_diagnostics.dart`** — 613 lines, partial, state, → `:engine:diagnostics` (agent-portable)  
   Opt-in, app-private crash/ANR evidence store (fixed categories and times only) with consent, clear, native-crash/ANR import and a non-blocking startup.  
   Native now: `CrashDiagnostics.kt`, `CrashStartup.kt`, `CrashStore.kt`, `NativeCrashRecord.kt`, `NativeCrashStore.kt`  
   - [uncataloged] CrashDiagnostics/CrashStartup are not wired into the Android app graph, so capture never runs
   - [uncataloged] no Settings consent toggle or crash records list (Crash reports section) UI
   - ANR import since consent not evidenced by native tests

14. **`lib/background/widget_action_runner.dart`** — 530 lines, partial, background, → `:engine:attention` (agent-portable)  
   Runs one explicit home-widget tap (allow once, reject, stop, steer, approve plan, resume) without the app: checks ownership and health, confirms the exact pending request or run identity, sends once, and reconciles status before closing.  
   Native now: `WidgetActionAdmission.kt`, `WidgetActionRuntime.kt`, `WidgetActionPolicy.kt`, `WidgetActionLease.kt`, `WidgetActionSnapshot.kt`  
   - no WidgetBackgroundRunner implementation is bound: cold widget taps cannot run without the app
   - health-first gating before reading or answering permissions not implemented
   - exact pending-permission confirmation before allow once / reject not implemented
   - stop identity check (server user-message id + busy status, old run cannot stop replacement) not implemented
   - post-reply status reconciliation per protocol not implemented
   - steer vs after-reply fallback and uncertain (never retried) mutation errors not implemented in a runner

15. **`lib/api2/transport.dart`** — 527 lines, partial, api, → `:protocol:oc2` (agent-portable)  
   OpenCode 2 HTTP transport: root normalization, Basic auth header, trust gate, timeouts, dialect detection, SSE open and typed error mapping.  
   Native now: `Oc2Gateway.kt`, `Oc2GatewayFactory.kt`, `Http.kt`, `OkHttpEngineFactory.kt`, `TrustGate.kt`  
   - error body tags are not used: 503 service_starting/stopping/failed and an UnauthorizedError tag on a non-401 are not refined (Oc2ErrorEnvelope is parsed nowhere in the gateway)
   - [uncataloged] a server root with a path prefix (reverse-proxy subpath) cannot be represented: ServerOrigin is scheme/host/port only
   - [uncataloged] an OpenCode 1 or non-OC2 /api/health answer is a generic unreadable reply, not the distinct legacy-upgrade signal
   - no per-request performance span (method, path template, status, bytes) on the net client

16. **`lib/background/notification_status.dart`** — 511 lines, partial, domain, → `:engine:attention` (agent-portable)  
   Pure situation table that turns app state into the one ongoing status notification (needs you > Android limit > reconnecting > working > hot > waiting > paused idle > setup > ready) with its title, lines, progress, actions and lock-screen counts.  
   Native now: `StatusRow.kt`, `StatusRenderer.kt`, `StatusOwners.kt`, `ReplyWatch.kt`  
   - the situation builder (priority order and wording of every state) is not ported; only reply watch supplies a fixed "working" row
   - working row with tasks ("5 of 6"), real progress fraction, hours formatting and several-chats line not computed
   - needs-you row (whose request, plural, still-working count) not produced
   - waiting row naming provider and next-try time ("soon" when past) not produced
   - paused rows (Android limit, phone hot, idle with Start) and reconnecting row not produced
   - idle row (last reply, New chat, phone server Stop OpenCode / where it runs) and setup step row not produced
   - "on <server>" app-name suffix when several servers exist not applied

17. **`lib/api2/client.dart`** — 506 lines, partial, api, → `:protocol:oc2` (agent-portable)  
   Typed OpenCode 2 REST client: health, sessions, messages, prompt/inbox, models/providers/agents, permissions, forms, files, commands, skills and config.  
   Native now: `Oc2Gateway.kt`, `Oc2PromptSend.kt`, `Oc2SessionSelection.kt`, `Oc2SearchUsage.kt`, `Oc2History.kt`  
   - form endpoints missing (pending/session forms, read form, form state, reply, cancel); native capability forms=false
   - inbox item steer/queue/cancel routes missing (only inbox read after send)
   - [uncataloged] GET /session/{id}/wait long-poll (10-minute receive timeout) missing
   - [uncataloged] skills list (GET /skill) missing
   - configured entries read (GET /config) missing
   - [uncataloged] per-session permission list (GET /session/{id}/permission) missing; only global /permission/request

18. **`lib/api2/gateway_operations/ops_integrations.dart`** — 499 lines, missing, protocol, → `:protocol:oc2` (agent-portable)  
   Lists integrations and credentials and drives connecting by key, external, OAuth and command sign-in, plus rename/activate/remove credentials and disconnect.  
   - integration list (/integration) absent (integrationCredentials=false)
   - key connect, external connect, disconnect absent
   - OAuth start/status/complete/cancel attempts absent
   - command sign-in start/status/cancel absent (integrationCommandAuth=false)
   - credential rename/activate/remove (/credential/{id}) absent
   - hidden form fields as preset answers for integration forms not wired to a gateway

19. **`lib/codex/transport.dart`** — 496 lines, missing, protocol, → `:protocol:codex` (agent-portable)  
   JSON-RPC-over-WebSocket transport to the Codex app-server with initialize handshake, bearer upgrade without redirects, fixed safe failures and no mutation replay on socket loss.  
   - no Codex WebSocket JSON-RPC transport (one RPC per text frame, initialize/version check)
   - bearer upgrade refuses redirects without forwarding credentials
   - socket loss fails all pending work without replaying mutations; requests wait for replacement initialization
   - remote error bodies never surfaced (fixed CodexFailure kinds)

20. **`lib/termux/storage.dart`** — 495 lines, partial, domain, → `:feature:linux` (agent-portable)  
   "Storage on this phone": category scan with progress/cancel, summary and safe per-category cleaning of regenerable caches.  
   Native now: `LinuxStorage.kt`, `LinuxStorageScreen.kt`, `LinuxSize.kt`  
   - Termux tools.sh path: dropped: Termux as a runtime host (D7 / NG7; plan 11 row 64 "not ported")
   - background scan with stage/progress log, cancel file and stale-after-clean rule (B-LINUX-247/248) have no native test citation

## Gaps no catalog row covers

Behaviours found in the Flutter source that are missing or partial natively **and** that no row in `behaviors.jsonl` describes. A port driven only by the catalog would miss these; the owning unit should add top-up rows (catalog README, "top-up") or port them straight from the named file.


### `:android:background`

- `lib/background/live_background.dart`: battery-optimization exemption request and open-notification-settings path from the live-mode setting not implemented (only the AppPermission enum exists)

### `:android:platform`

- `lib/platform/app_exit.dart`: no Android LifecycleSource binding: nothing calls ActivityManager.getHistoricalProcessExitReasons, so exit history and the launch report are never fed on device
- `lib/platform/app_exit.dart`: 'autostart' setting opens only the app-details page; the maker-specific autostart activities (MIUI, ColorOS, EMUI, ...) are not tried
- `lib/platform/storage_access.dart`: re-check of the grant on resume after the settings round-trip (waits up to 10 min)
- `lib/platform/thermal.dart`: no Android battery reader (BatteryManager level/charging, sysfs fallback); AndroidDeviceFacts says battery waits for S3
- `lib/platform/thermal.dart`: no thermal headroom forecast (PowerManager.getThermalHeadroom); heat steps and holds that use forecast >= 0.7 never trigger on device (B-ANDR-128, B-ANDR-129)

### `:android:schedules`

- `lib/scheduling/platform.dart`: restoreProfile after an aborted profile deletion not present on ScheduleAlarms (only cancelProfile)

### `:android:tiles`

- `lib/background/attention_tile_snapshot.dart`: the tile count is never cleared on an explicit disconnect (AttentionTileCache.clear is not called; only clearOwner on profile deletion)

### `:core:common`

- `lib/platform/app_visibility.dart`: the app graph binds the shared Pacer to a constant Foreground visibility (LinuxWiring.pacer); AndroidDeviceFacts.appVisible is not fed to it, so hidden pacing never applies app-wide

### `:core:net`

- `lib/api2/sse2.dart`: no offline check before a hidden retry: Oc2Gateway passes NetworkGate(UnknownNetworkStatus)
- `lib/api2/sse2.dart`: no net.stream perf mark (bytes, uptime, attempt, hidden) per stream session
- `lib/network/platform_websocket.dart`: no generic WebSocket client in :core:net (only Paseo has its own PaseoSocket)
- `lib/network/transport_failure.dart`: OkHttpEngineFactory.classify folds UnknownHostException into ConnectFailed, so a typo'd host is not told apart from a refused connection
- `lib/network/transport_failure_io.dart`: no distinct TransportError for DNS failure (UnknownHostException mapped to ConnectFailed), so the probe cannot say the address did not resolve

### `:engine:attention`

- `lib/background/widget_snapshot.dart`: other-project / other-connection conversation rows (WidgetOtherConversation) not produced

### `:engine:connection`

- `lib/demo/demo_gateway.dart`: no demo gateway / openDemo(script) in ConnectionRegistry: Try demo has no simulated session, streamed reply, patch review or simulated permission
- `lib/demo/demo_gateway.dart`: Reset demo / Leave demo flow without touching real profiles or files

### `:engine:diagnostics`

- `lib/diagnostics/crash_diagnostics.dart`: CrashDiagnostics/CrashStartup are not wired into the Android app graph, so capture never runs
- `lib/diagnostics/crash_diagnostics.dart`: no Settings consent toggle or crash records list (Crash reports section) UI
- `lib/diagnostics/device_diagnostics_gateway.dart`: no single device-diagnostics gateway/screen showing exits and helper deaths separately
- `lib/diagnostics/perf_trace.dart`: no route/navigation span observer

### `:engine:entry`

- `lib/main.dart`: lifecycle: suspend connection on pause/hidden and resume + in-app server healing on resume as one app-level hook
- `lib/main_parts/alerts_and_shortcuts.dart`: widget row of another project/connection: resolve, else land on Conversations, not a broken chat (wired app)
- `lib/main_parts/alerts_and_shortcuts.dart`: an alert for the chat already on top must not push a second copy; widget Reply re-opens with composer focused
- `lib/main_parts/bootstrap_gate.dart`: 8 s "still opening" with Try again and Report a problem from the first page not wired
- `lib/main_parts/bootstrap_gate.dart`: pre-conversation migrations ordering (old drafts into Saved prompts, camera photo rejoining its draft, quiet hours/Wi-Fi-only merge) before any conversation opens
- `lib/main_parts/link_routes.dart`: AndroidManifest of androidApp declares no VIEW intent filter for the session link scheme, so links never reach the app (B-ANDR-072 contract)
- `lib/main_parts/root.dart`: last-exit report and restore of the phone server after Android ended the app: ExitClassifier exists but is not run at app start
- `lib/main_parts/root.dart`: interrupted Reset Linux finished on next open (never left erased with no setup)
- `lib/main_parts/share_route.dart`: "a second failure says so" and "a newer share replaces the waiting one" not shown natively

### `:engine:media`

- `lib/platform/wifi_file_share.dart`: share stop when the app process is detached not evidenced

### `:engine:project`

- `lib/demo/demo_store.dart`: in-memory profile store isolating the demo from saved servers
- `lib/platform/phone_storage.dart`: built-in host usage measured only on explicit request; open storage settings with fallback
- `lib/platform/phone_storage_folders.dart`: make-folder in shared storage and .git detection per entry

### `:engine:runtime`

- `lib/platform/child_process_limit.dart`: process-budget reading hardcodes limitLifted=false (LinuxWiring), so the lifted ceiling is never used even when the person turned the limit off
- `lib/termux/bridge_models.dart`: runtime selection wire names (opencode1 detection -> opencode1_migration_required, invalid_runtime) not modelled natively

### `:engine:schedules`

- `lib/scheduling/agent_connection.dart`: no explicit auth_required refusal for profiles needing password / Codex-token re-entry (native relies on credential source failure)
- `lib/scheduling/agent_connection.dart`: no explicit refusal of transient-transport profiles ("unsupported")
- `lib/scheduling/runner.dart`: remote vs phone-owned deadline semantics (remote accepted run reports "started", phone-owned run must finish before lease release) not distinguished

### `:feature:about`

- `lib/feedback/bug_report.dart`: report hook from KitStateView/KitNotice not routed to the Settings report page in screens

### `:feature:schedules`

- `lib/scheduling/controller.dart`: platform status (notifications off / exact alarms not allowed) not shown to the person on the Schedules page

### `:feature:settings`

- `lib/manage_space_main.dart`: no page that measures app storage and offers Export projects first / Clear cache only / Clear everything
- `lib/manage_space_main.dart`: manage-space page reads only plain appearance/theme prefs, never secure storage
- `lib/voice/notices.dart`: no in-app voice notices page: native Voice › notices opens THIRD_PARTY_NOTICES.md on GitHub in a browser instead of listing components and reflowed license texts offline

### `:feature:voice`

- `lib/voice/presentation.dart`: sizes always shown in MB; no "1.2 GB" decimal-GB wording
- `lib/voice/voice_ui.dart`: setup sheet primary "Use Balanced" after download / "Done" when already in use is not reproduced as a sheet

### `:protocol:oc2`

- `lib/api2/client.dart`: GET /session/{id}/wait long-poll (10-minute receive timeout) missing
- `lib/api2/client.dart`: skills list (GET /skill) missing
- `lib/api2/client.dart`: per-session permission list (GET /session/{id}/permission) missing; only global /permission/request
- `lib/api2/gateway.dart`: automatic-approval path (serverRunsTools) never wired: preparePermissions always passes serverRuns=false
- `lib/api2/gateway_operations.dart`: redirect-free terminal upgrade (followRedirects=false) not ported
- `lib/api2/terminal_command_gateway.dart`: not wired in lib/ (only test/terminal_lifecycle_test.dart constructs it), so it may be dropped instead of ported
- `lib/api2/transport.dart`: a server root with a path prefix (reverse-proxy subpath) cannot be represented: ServerOrigin is scheme/host/port only
- `lib/api2/transport.dart`: an OpenCode 1 or non-OC2 /api/health answer is a generic unreadable reply, not the distinct legacy-upgrade signal

### `:ui:words`

- `lib/demo/demo_copy.dart`: demo sample prompt, simulated reply, allow/deny simulated edit and outcome strings are not in :ui:words (only demoScreen* chrome strings exist)
- `lib/l10n/app_localizations.dart`: androidApp res/xml/locales_config.xml declares only en and ar, so Android 13+ per-app language settings cannot offer es/ja/pt/ru/zh although :ui:words ships them
- `lib/l10n/app_localizations.dart`: 6 English keys not ported to :ui:words (kitAgentLabel, activeContextMessageTitle, runningPhoneGroup/Name/Cpu/About); 130 more parked in native/tools/strings/unused


## Untested Flutter files still missing or partial natively (4)

No Flutter test exercises the main behaviour of these files, so the catalog has no rows for it at all.

| file | lines | status | target | gaps |
|---|---:|---|---|---|
| `lib/main_parts/root.dart` | 662 | partial | `:engine:entry` | last-exit report and restore of the phone server after Android ended the app: ExitClassifier exists but is not run at app start; interrupted Reset Linux finished on next open (never left erased with no setup); thermal guard pausing AI Team: after-switch (ThermalPolicy present, not wired) |
| `lib/main_parts/alerts_and_shortcuts.dart` | 346 | partial | `:engine:entry` | PlatformHub.alertTap only opens the named chat; quota/quota-reset alerts to Usage (token check, "That plan changed") are only in the unwired EntryDecider; widget row of another project/connection: resolve, else land on Conversations, not a broken chat (wired app); an alert for the chat already on top must not push a second copy; widget Reply re-opens with composer focused |
| `lib/scheduling/agent_connection.dart` | 141 | partial | `:engine:schedules` | phone-server lease not wired: AppScheduleProfiles.open never calls BuiltinLinux.acquireScheduledPhoneServer / release for an on-phone OpenCode server, so a scheduled run cannot start the stopped phone server (plan 11 PhoneServerHost.acquireScheduledServer); no startedPhoneServer signal to the runner (phone-started runs vs remote runs); no explicit auth_required refusal for profiles needing password / Codex-token re-entry (native relies on credential source failure) |
| `lib/network/transport_failure_io.dart` | 19 | partial | `:core:net` | no distinct TransportError for DNS failure (UnknownHostException mapped to ConnectFailed), so the probe cannot say the address did not resolve |

## Files with no catalog row at all, in scope (4)

`lib/demo/demo_copy.dart` (missing), `lib/platform/app_visibility.dart` (partial), `lib/platform/clipboard_image.dart` (done), `lib/voice/natural_voice_ui.dart` (done)
