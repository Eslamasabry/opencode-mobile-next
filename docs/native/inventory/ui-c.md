# Inventory UI-C: Flutter → native parity, file by file

Machine-readable record: [`ui-c.jsonl`](ui-c.jsonl) — 248 files, 96,128 Flutter lines. 
Analysis only; base `native/p12-integrate-2026-11-03` at the time of this unit. One JSON line per file with keys `file, lines, kind, summary, native, status, gaps, portability, target_module, catalog_rows, untested, uncatalogued_gaps`.

## Totals by status

| Status | Files | Flutter lines |
|---|---:|---:|
| done | 10 | 4,503 |
| partial | 110 | 44,318 |
| missing | 60 | 20,532 |
| after-switch | 59 | 24,844 |
| dropped | 6 | 1,699 |
| not-needed | 3 | 232 |
| **total** | **248** | **96,128** |

## Totals by kind

| Kind | Files | done | partial | missing | after-switch | dropped | not-needed |
|---|---:|---:|---:|---:|---:|---:|---:|
| widget | 132 | 3 | 67 | 27 | 30 | 5 | 0 |
| screen | 73 | 3 | 27 | 24 | 18 | 1 | 0 |
| other | 10 | 0 | 3 | 3 | 3 | 0 | 1 |
| state | 9 | 0 | 6 | 2 | 1 | 0 | 0 |
| domain | 8 | 1 | 2 | 1 | 4 | 0 | 0 |
| l10n | 7 | 0 | 3 | 2 | 2 | 0 | 0 |
| kit | 3 | 2 | 0 | 1 | 0 | 0 | 0 |
| dead | 3 | 0 | 0 | 0 | 1 | 0 | 2 |
| platform-bridge | 2 | 0 | 2 | 0 | 0 | 0 | 0 |
| generated | 1 | 1 | 0 | 0 | 0 | 0 | 0 |

Portability of the 170 partial/missing files: agent-portable 114, needs-design 35, platform-specific 21.

Partial/missing files by owning module: `:feature:chat` 22, `:feature:servers` 22, `:feature:settings` 21, `:feature:phone` 15, `:feature:usage` 13, `:feature:files` 9, `:engine:runtime` 6, `:feature:chat-pages` 5, `:feature:chats` 5, `:feature:project` 5, `:feature:projects` 4, `:feature:schedules` 3, `:feature:widget-panels` 3, `:feature:search` 3, `:ui:kit-chat` 3, `:engine:connection` 3, `:feature:about` 2, `:ui:kit` 2, `:engine:requests` 2, `:ui:shell` 2, `:engine:integrations` 2, `:engine:agents` 2, `:android:termux` 2, `:feature:review` 1, `:ui:kit-content` 1, `:feature:linux` 1, `:feature:chat-composer` 1, `:engine:settings` 1, `:protocol:paseo` 1, `:engine:diagnostics` 1, `:android:background` 1, `:core:security` 1, `:feature:agents` 1, `:engine:composer` 1, `:engine:delivery` 1, `:engine:media` 1, `:ui:words` 1.

Untested in Flutter (no test covers the main behaviour): 32 files; files with no catalog row at all: 66.

## The 20 biggest missing/partial files

| # | File | Lines | Status | Module | Gaps |
|---:|---|---:|---|---|---|
| 1 | `ui/screens/settings/appearance_screen.dart` | 1470 | partial | `:feature:settings` | Guided first visit (KitGuide chips, Next/Finish, All set, AppearanceGuideStore) absent; Accent picker absent (accent is applied from storage but cannot be chosen); Reply glow choice absent from the page (GlowStyle stored only); Background look and Strength questions absent (deferred until D11 per plan); Shape: corners and spacing absent; Open-at-section deep links (AppearanceSection) and theme pack preview absent |
| 2 | `ui/widgets/phone_server_card.dart` | 1445 | partial | `:feature:phone` | no This-phone row/card in the server list or switcher with connect/disconnect (Servers only has a 'This phone' link row); no remove-OpenCode flow (keep projects vs typed-name delete everything, sizes, backups kept); no forget of saved in-app profiles after removal; no server log view; no open terminal / add tools actions; no move queued prompts from the card |
| 3 | `ui/widgets/form_renderer.dart` | 1351 | partial | `:feature:chat` | KitFormSheet/KitFormCard are not used by any feature: server Form.Info requests (FormRequests) are not wired into feature:chat or androidApp; KitForm is reachable only inside agent cards (KitAgentCard/ChatCardSlot); multiline draft persistence across restart not reachable from a screen |
| 4 | `ui/screens/this_phone_screen.dart` | 1315 | partial | `:feature:phone` | Termux host choice (NG7); add tools / remove components rows; installed list, storage and log rows; OpenCode 1 to 2 history copy and migration-status rows; setup pages open the Linux page instead of a setup flow |
| 5 | `ui/widgets/local_agent_onboarding.dart` | 1270 | partial | `:feature:agents` | no install wizard UI (offer, five steps node/paseo/claude/sign-in/start with progress, live output, success/failure/'Android stopped it'); no resume-from-status on return; 'Not now' on the offer not remembered; Agents screen shows phone agent states read-only |
| 6 | `ui/screens/review_workspace.dart` | 1254 | partial | `:feature:review` | no view picker across session / working tree / branch (branch scope absent; empty-view hint words); no line-range comment sheet with kept draft and 'Add comment to prompt' flow matching Flutter; no copy patch / copy file from file header; no 'add file to prompt', all-viewed and slow-load notices; no 'Change 1 of N' navigator (N/P/F7) on the diff page |
| 7 | `ui/widgets/folder_browser.dart` | 1180 | partial | `:feature:projects` | no folder browser listing (go into folders, up one folder, project tap vs folder tap, chevron peek); New project row is disabled (p12NewProjectUnavailable): no name step, Create and open, Change folder; no Search this phone (consent, timer, cap, Look deeper); no phone-storage vs project-space place switch, no Show hidden folders; loading escalation/empty projects first step/error with Up one folder states missing |
| 8 | `ui/screens/termux_migration_screen.dart` | 1136 | missing | `:feature:phone` | PhoneRoute.TermuxMigration page and TermuxMigrationViewModel (plan 07 F16); Termux bridge/transport (:android:termux, D7 migrate-out only); failure-code plain words and per-item size limits; set up in-app server for a migration hand-off |
| 9 | `ui/screens/settings/server_page.dart` | 1025 | partial | `:feature:servers` | Only connect/remove/add exist on the native Servers list; no ServersRoute.ServerPage; Connection section (health, version, sign-in change, rename/address, what it supports) absent; In this app section (new-chat model, notifications, watching, read-state sharing consent) absent; On the server section (AI setup, plugins, default shell) absent; Use my default reset of every difference absent; Restart OpenCode on its host / pinned upgrade command dialog absent |
| 10 | `ui/screens/running_work_sheet.dart` | 1022 | missing | `:feature:chat` | no 'Work in this conversation' sheet (agents + commands ordered failed/running/rest); no stop agent / move to background actions; no shell output page (live output, timeout choice, copy, exit code/folder details); no plain command naming ('flutter test && build.sh') per B-LINUX-180; only transcript background-task facts exist (engine/transcript turnfacts/BackgroundTasks.kt), no sheet |
| 11 | `ui/screens/project_hub_screen.dart` | 987 | partial | `:feature:files` | no Files hub not tied to a conversation (FilesRoute always needs `from`; no rail/Search/Ctrl+2 door); no ordered project-tool rows/menu (Changes count, Terminal running count, worktrees, project health, dev services, cloud environments, Scheduled prompts row); no ProjectTools.available gating or 'no project open' chooser on the page; no project switch under the title / copy folder path; Codex no-tools rule absent (no Codex protocol natively) |
| 12 | `ui/widgets/server_switcher_sheet.dart` | 978 | missing | `:feature:servers` | no ServerSwitcher sheet or server pill (plan 07 A08 SheetKey.ServerSwitcher); same-host servers not told apart by port |
| 13 | `ui/widgets/tool_card_body.dart` | 962 | partial | `:feature:chat` | prose results (sub-agent answer, fetched page, skill) not rendered as Markdown; connector search 'catalogue off' turn-on action; kind-specific blocks (diff, todo, question, images) not in the opened step; shell exit words (exitLabel passed null) |
| 14 | `ui/screens/usage_screen.dart` | 954 | partial | `:feature:usage` | personal budgets; capability explainer for a server without usage statistics; charts |
| 15 | `ui/screens/session_context_screen.dart` | 935 | partial | `:feature:chat-pages` | a sheet in chat, not ChatPagesRoute.Context page (no /context or session-menu door); no Compact action near the limit; no session totals (accumulated cost, server totals, loaded-history note, load older); no active-context changes section and refresh |
| 16 | `ui/widgets/file_preview.dart` | 893 | partial | `:feature:files` | Markdown files shown as plain text: no rendered view and no Rendered/Source switch; no table (CSV/TSV) or SVG rendering in FileScreen; no 'line N is outside the preview' notice; More actions missing: copy original, copy path, open in Review, attach to prompt; SmartTextPreview (tool/subagent output with Show all into the viewer) has no native counterpart; viewer only reachable as the Files route; no sheet entry for attachments or tool outputs (showFilePreviewSheet/showFilePreviewSheetLoading) |
| 17 | `ui/screens/running_on_phone_screen.dart` | 810 | partial | `:feature:phone` | no grouping by owner (left behind, OpenCode, each Claude Code conversation, agent helper, AI Team, terminals, setup) with parent-child indentation; no 'Heavy' / 'May be stuck' marks from paired readings; no memory/uptime per row and no command line under Details; no 'Stop all left behind' and no 'what did not stop' outcome; plain names for kernel process names / commands (B-LINUX-180) not ported; refresh is 10 s not 5 s |
| 18 | `ui/screens/terminal/terminal_list.dart` | 805 | missing | `:feature:project` | server terminal list (list, create, tabs from two, rename, stop, remove, clear-up); refresh/start failure notice keeping rows; protocol client for OpenCode 2 PTY list/create/delete |
| 19 | `ui/screens/termux_processes_screen.dart` | 793 | partial | `:feature:phone` | Termux-host process list (Termux as runtime is NG7; only built-in Linux equivalent exists in PhoneRoute.Running); busy/idle measured between two readings; memory in MB and running time per row; TERM then KILL after grace on stop |
| 20 | `ui/screens/settings/notifications_settings_screen.dart` | 787 | partial | `:feature:settings` | Native page has what notifies, quiet hours, Wi-Fi only, test notification, Android-blocked fix and a background summary row; Per saved-server monitoring and notify switches absent; Background connection switch and restart from the status row absent; Keep running section absent (KeepRunning not in SettingsWiring OPENS); What runs by itself / automation section absent; Open at section slug and per-server variant with link to general page absent |

## Gaps no catalog row covers

These behaviours exist in Flutter but no row in `catalog/behaviors.jsonl` describes them (usually because Flutter had no test). They will not be caught by catalog-driven parity tests; the owning unit should add rows or test them directly. After-switch/dropped files are listed separately at the end.

- `ui/widgets/termux_migration_entry.dart` (missing, `:android:termux`): one-time Servers offer remembered per server and swept with the profile
- `ui/widgets/termux_problem_fix.dart` (missing, `:android:termux`): Termux-specific problem words and one-act fixes for the migration path
- `ui/widgets/external_link.dart` (partial, `:core:security`): shared confirm sheet (Don't open / Copy link / Full address) only drawn in DevPreviewScreen; chat link Ask path not verified to use externalLink* words; agent sign-in exact-page check (claude.com oauth authorize, no port/userinfo/fragment) not found in the link policy
- `ui/widgets/continue_with_sheet.dart` (partial, `:engine:agents`): no first-message field; no handoff error words (file, scope, too large, no writer)
- `ui/widgets/prompt_history_navigation.dart` (partial, `:engine:composer`): implemented in :feature:chat-composer, which is not wired into the chat (chat uses BasicComposerHost without history keys)
- `ui/widgets/connection_status_banner.dart` (partial, `:engine:connection`): no 'trying again in N s' countdown or Try now; no connection details sheet (diagnosis, what to check, raw error fold, Switch server); no agent killed-by-Android / back-after-kill lines
- `ui/widgets/queued_prompt_move_sheet.dart` (missing, `:engine:delivery`, untested): no move-queued-prompts sheet or queue retarget operation natively (queuedMove* words unused)
- `ui/widgets/connect_methods.dart` (partial, `:engine:integrations`): no keyLedConnectMethods filtering for key-only providers
- `ui/widgets/question_options.dart` (partial, `:engine:requests`, untested): native rule differs: Dart uses >2 prompts, any description past ~140 chars or any optional prompt; QuestionSheet uses one-prompt/label length/multiple
- `ui/widgets/process_budget_status.dart` (partial, `:engine:runtime`, untested): guard exists but no status-slot line (processBudgetWait* words unused)
- `ui/widgets/runtime_switch_status.dart` (missing, `:engine:runtime`): no in-app runtime switch status during the OpenCode 1 to 2 move (kept by NG1)
- `ui/widgets/thermal_notice.dart` (partial, `:engine:runtime`): heat line dismissible once per episode
- `ui/widgets/work_status_line.dart` (partial, `:engine:runtime`): confirm before restarting the phone's server
- `ui/teaching/teaching.dart` (partial, `:engine:settings`): tip overlay kit part
- `ui/screens/whats_new_places.dart` (missing, `:feature:about`): Show me targets and highlight anchors
- `ui/screens/whats_new_sheet.dart` (partial, `:feature:about`): Show me with highlight arrival; Share on last step
- `ui/widgets/local_agent_onboarding.dart` (partial, `:feature:agents`): no install wizard UI (offer, five steps node/paseo/claude/sign-in/start with progress, live output, success/failure/'Android stopped it'); no resume-from-status on return; 'Not now' on the offer not remembered; Agents screen shows phone agent states read-only
- `ui/screens/running_work_sheet.dart` (missing, `:feature:chat`): no 'Work in this conversation' sheet (agents + commands ordered failed/running/rest); no stop agent / move to background actions; no shell output page (live output, timeout choice, copy, exit code/folder details)
- `ui/send_failure_words.dart` (partial, `:feature:chat`, untested): agent name in the words ("Kilo needs you to sign in"); needs-account variant when agent already signed in; "lost its connection to Anthropic" company phrasing; redacted Details values (agent, model, code)
- `ui/widgets/agent_card_asks.dart` (partial, `:feature:chat`): danger confirm only tones the button; no confirm sheet before sending
- `ui/widgets/agent_card_photos.dart` (partial, `:feature:chat`): no per-conversation photo store so picked photos survive app restarts into the draft; no plain-words picker error mapping (only PhotoTooLarge/PhotosRefused refusals)
- `ui/widgets/command_sheet.dart` (partial, `:feature:chat`): no Agents tab to delegate a prompt to a server subagent; no Ctrl+K shortcut; Settings embedded variant does not ask which conversation to run in
- `ui/widgets/connector_card.dart` (missing, `:feature:chat`): no Connect flow with progress, OAuth sign-in, manual code or cancel
- `ui/widgets/form_renderer.dart` (partial, `:feature:chat`): KitFormSheet/KitFormCard are not used by any feature: server Form.Info requests (FormRequests) are not wired into feature:chat or androidApp; KitForm is reachable only inside agent cards (KitAgentCard/ChatCardSlot); multiline draft persistence across restart not reachable from a screen
- `ui/widgets/mobile_task_view.dart` (partial, `:feature:chat`): tasks only on a separate Tasks page, not inside the reply's Work step; no progress bar or step marks (KitTaskMark); no windowed long plan with 'Show all N tasks'; no Copy all (mobileTasksCopyAll)
- `ui/widgets/model_shortcuts.dart` (missing, `:feature:chat`): no F2/Shift+F2 recent-model, F4 thinking, Ctrl+B or PgUp/PgDn/Home/End bindings in chat; no model shortcut menu items on the model chip (next/previous recent, next favorite with disabled reasons); modelShortcuts* strings unused; no ModelCycleButton
- `ui/widgets/pickers.dart` (partial, `:feature:chat`): no new-conversations apply scope from Settings/search doors
- `ui/widgets/pickers_catalog_footer.dart` (partial, `:feature:chat`): no partial-save / selection-gone notices
- `ui/widgets/pickers_catalog_models.dart` (partial, `:feature:chat`): no favourites; no capability/provider filters or collections; no deprecated/preview/context badges or copy id
- `ui/widgets/pickers_catalog_sections.dart` (partial, `:feature:chat`): no model detail rows (price, capabilities); no plan/build agent explanations
- `ui/widgets/pickers_choices.dart` (partial, `:feature:chat`): no plain-choices step (Default / Recently used / Cheapest with reasons); no free-model detection
- `ui/widgets/run_command_dialog.dart` (missing, `:feature:chat`): no run-command review sheet (commandRunTitle, commandArguments, runCommandFailedTitle unused)
- `ui/widgets/session_handoff_sheets.dart` (missing, `:feature:chat`): no Continue on computer sheet; no Open on another phone sheet with link/QR (plan 07 C28)
- `ui/widgets/session_menu.dart` (partial, `:feature:chat`): missing Summarize locally, Dev preview entries
- `ui/widgets/tool_card.dart` (partial, `:feature:chat`): step note shown inside opened step
- `ui/widgets/tool_card_body.dart` (partial, `:feature:chat`): prose results as capped Markdown; shell exit-code words in the command box
- `ui/widgets/tool_card_parts.dart` (partial, `:feature:chat`): image preview with Attach/Download; structured input as labelled rows
- `ui/widgets/transcript_highlight.dart` (partial, `:feature:chat`): find mark painted in rendered Markdown; active-hit excerpt panel for hidden matches
- `ui/screens/session_context_screen.dart` (partial, `:feature:chat-pages`): no Compact action near the limit; no session totals (accumulated cost, server totals, loaded-history note, load older); no active-context changes section and refresh
- `ui/screens/session_destination_sheet.dart` (missing, `:feature:chat-pages`): no SessionDestination sheet or confirm question; no /move, /warp doors
- `ui/screens/session_export_screen.dart` (missing, `:feature:chat-pages`): no Markdown transcript export; no SAF save
- `ui/screens/session_note_screen.dart` (missing, `:feature:chat-pages`): no discard-changes dialog
- `ui/screens/session_relations_screen.dart` (missing, `:feature:chat-pages`): no ChatPagesRoute.Relations page; no parent/child session listing; no stop subagent confirm, pin/unpin, copy handoff
- `ui/screens/session_import_screen.dart` (missing, `:feature:chats`): no destination chooser
- `ui/widgets/last_known_sessions.dart` (missing, `:feature:chats`, untested): no cached session inventory shown on the opening frame; Chats shows skeleton rows while opening; lastKnownUpdated* strings exist but unused
- `ui/widgets/older_sessions_pager.dart` (partial, `:feature:chats`): paging only on All conversations; per-server Chats list and the run-command 'Runs in' picker do not page; no pinned-conversations-unreadable notice; refresh-failed notice not distinct
- `ui/screens/project_hub_screen.dart` (partial, `:feature:files`): no project switch under the title / copy folder path
- `ui/screens/shared_storage_access_flow.dart` (missing, `:feature:files`): Shared-storage access ask with re-check on return; Restart confined server to bind a new shared folder; Use the app's project space instead
- `ui/widgets/delimited_file_preview.dart` (partial, `:feature:files`, untested): parser and fileTable* strings exist but FileScreen has no table body (FileBody has no Table case); nothing calls FilePreview.table(); limit notices (malformed/tooLarge/tooWide/fieldTooLong) never shown; no Table/Source switch
- `ui/widgets/file_preview.dart` (partial, `:feature:files`): Markdown files shown as plain text: no rendered view and no Rendered/Source switch; no table (CSV/TSV) or SVG rendering in FileScreen; no 'line N is outside the preview' notice; More actions missing: copy original, copy path, open in Review, attach to prompt; SmartTextPreview (tool/subagent output with Show all into the viewer) has no native counterpart; viewer only reachable as the Files route; no sheet entry for attachments or tool outputs (showFilePreviewSheet/showFilePreviewSheetLoading)
- `ui/widgets/file_share_sheet.dart` (partial, `:feature:files`, untested): no Copy link action on the link (fileShareCopyLink string unused)
- `ui/widgets/link_file_share.dart` (partial, `:feature:files`, untested): tapped path opens FilesRoute.File and shares fetched bytes only; no streaming from the phone's own disk (shared storage / built-in Linux paths); no phone-disk path resolution for large files
- `ui/widgets/pdf_file_preview.dart` (partial, `:feature:files`): no cancellation of in-flight page renders when the app leaves the foreground; isolated-view explanation (filePreviewPdfIsolated) unused
- `ui/widgets/phone_file_prompt.dart` (partial, `:feature:files`): no confirm question before Open with (phoneFileOpenTitle); no .apk install path via package installer; no 'Open it in Files' fallback naming the file's folder with Copy path
- `ui/widgets/svg_file_preview.dart` (partial, `:feature:files`, untested): StaticSvg sanitiser exists but the file viewer shows SVG as source text only; no picture/source toggle
- `ui/screens/running_on_phone_screen.dart` (partial, `:feature:phone`): no grouping by owner (left behind, OpenCode, each Claude Code conversation, agent helper, AI Team, terminals, setup) with parent-child indentation; no 'Heavy' / 'May be stuck' marks from paired readings; no memory/uptime per row and no command line under Details; no 'Stop all left behind' and no 'what did not stop' outcome
- `ui/screens/termux_migration_screen.dart` (missing, `:feature:phone`): failure-code plain words and per-item size limits; set up in-app server for a migration hand-off
- `ui/screens/termux_processes_screen.dart` (partial, `:feature:phone`): busy/idle measured between two readings; memory in MB and running time per row
- `ui/screens/this_phone/this_phone_answer.dart` (partial, `:feature:phone`): round-figure size phrasing
- `ui/screens/this_phone/this_phone_details.dart` (missing, `:feature:phone`): server log row/view; reply speed reading; performance values; installed components list
- `ui/screens/this_phone_screen.dart` (partial, `:feature:phone`): add tools / remove components rows; installed list, storage and log rows
- `ui/widgets/local_server_row.dart` (partial, `:feature:phone`): Servers list has only a plain 'This phone' row opening the ThisPhone page; no status line, agent mark or tap-to-connect/start; no server-switcher row
- `ui/widgets/managed_move_view.dart` (partial, `:feature:phone`): no failure page with reasons (not ready, separate history, stopped, not confirmed), Details/Copy and Try again; no 'retry N of M' progress line (OpeningGate reuses openCode2MoveProgress only as a generic updating title)
- `ui/widgets/phone_server_card.dart` (partial, `:feature:phone`): no remove-OpenCode flow (keep projects vs typed-name delete everything, sizes, backups kept); no server log view; no open terminal / add tools actions
- `ui/widgets/phone_server_consents.dart` (partial, `:feature:phone`): in-flow consent sheets (consentBatteryTitle, consentMakerTitle) never shown: InFlowConsent has no caller; no ask-once-at-first-phone-server-start claim
- `ui/widgets/phone_server_restart.dart` (partial, `:feature:phone`, untested): restart only from the This phone page; no restart offered where the connected phone server is shown (Work tab / connection state)
- `ui/widgets/setup_progress_view.dart` (missing, `:feature:phone`): no setup progress screen (PhonePage.SetupProgress maps to LinuxRoute.Home)
- `ui/screens/terminal/terminal_list.dart` (missing, `:feature:project`): server terminal list (list, create, tabs from two, rename, stop, remove, clear-up); refresh/start failure notice keeping rows; protocol client for OpenCode 2 PTY list/create/delete
- `ui/screens/terminal/terminal_surface.dart` (partial, `:feature:project`): terminal key bar kit part; connection status line and reconnect; menu acts: paste, rename, details, stop; Copy only while text is selected
- `ui/screens/terminal_screen.dart` (partial, `:feature:project`): no PhoneRoute.LocalTerminal page (F14); source line switching server/local terminal with replaceTop; Ctrl+Backquote, /terminal, Files hub and Go to doors; page explaining a server without terminals
- `ui/screens/worktrees_screen.dart` (missing, `:feature:project`): last-act message above the list
- `ui/screens/projects_screen.dart` (partial, `:feature:projects`): no rename dialog (row menu, in-dialog failure keeping typed name); no 'Current' mark on the active project; no unopened project-space folders section (with Git badge); no one-folder server explanation (Codex/Paseo)
- `ui/widgets/folder_browser.dart` (partial, `:feature:projects`): no folder browser listing (go into folders, up one folder, project tap vs folder tap, chevron peek); New project row is disabled (p12NewProjectUnavailable): no name step, Create and open, Change folder; no Search this phone (consent, timer, cap, Look deeper); no phone-storage vs project-space place switch, no Show hidden folders; loading escalation/empty projects first step/error with Up one folder states missing
- `ui/widgets/phone_project_kind.dart` (missing, `:feature:projects`, untested): no project-kind detection or kind glyph/word natively (phoneScanKind* words exist but unused)
- `ui/widgets/remote_folder_picker.dart` (partial, `:feature:projects`): path field not prefilled from the folder last used
- `ui/screens/review_workspace.dart` (partial, `:feature:review`): no copy patch / copy file from file header; no 'add file to prompt', all-viewed and slow-load notices; no 'Change 1 of N' navigator (N/P/F7) on the diff page
- `ui/screens/scheduling/schedule_copy.dart` (partial, `:feature:schedules`): agent display label ('claude-code' → 'Claude Code') not ported; relative 'in 5 min' moment and year-only-when-far rule not ported (native: today/tomorrow/weekday/date)
- `ui/screens/scheduling/schedule_routes.dart` (partial, `:feature:schedules`, untested): schedule doors only from Servers; no chat session-menu, Files-hub or Search door with `from` conversation; ScheduleTarget gate (scheduledPrompts + non-transient transport + open project) not evaluated as a RouteRequirement; openScheduledRun fallback to history when the run's conversation cannot open not verified
- `ui/screens/scheduling/schedule_sheet.dart` (partial, `:feature:schedules`): editor is a page, not a sheet reachable from long-press Send / chat menu (C27); main action does not name the moment ('Schedule for 9:00 AM'); no Custom wheel (native has hour interval instead); last-run line with open-run link inside the editor not ported; typed prompt draft kept on dismiss not verified
- `ui/screens/servers/profile_editor.dart` (partial, `:feature:servers`): no separate editor route (ServersRoute.Editor); add is an inline form on Servers, no edit of a saved server; no focusPassword path from connection banner / EnterPhoneCredentials; no 'More options' fold (username, AI Team host); no stepped new-server flow
- `ui/screens/servers/profile_editor_fields.dart` (partial, `:feature:servers`): no Test connection button and inline verdict slots; no link drawing / slow-check stop offer
- `ui/screens/servers/profile_editor_pairing.dart` (partial, `:feature:servers`): phone vs desktop advice when every pairing address fails not ported
- `ui/screens/servers/profile_editor_probe.dart` (partial, `:feature:servers`, untested): no pre-save connection check that explains a non-answering server before storing; no held stored-secret reuse when editing (no edit flow); no auto-test after 800 ms typing pause; no verdict scroll-into-view / field focus on failure
- `ui/screens/servers/profile_editor_steps.dart` (partial, `:feature:servers`): no stepped add-server flow with 'Step N of M' progress; no 'what runs on the computer' backend choice (OpenCode / Codex / Paseo / this phone / outside agents); no Tailscale step; no ready/finished moment
- `ui/screens/servers/profile_editor_trust.dart` (partial, `:feature:servers`): no certificate inspection / trust sheet (showKitServerTrust) for new or changed certificates; pin storage exists only in engine
- `ui/screens/servers/profile_editor_widgets.dart` (partial, `:feature:servers`): compact re-pair actions when editing a saved server absent
- `ui/screens/servers/server_rows.dart` (partial, `:feature:servers`): rows lack monitor words, running-conversation count, queued-prompt count and version
- `ui/screens/servers/servers_actions.dart` (partial, `:feature:servers`): removal confirm does not name counted facts of what is deleted; no moving waiting prompts to the connected server; no EnterPhoneCredentials restore-first flow; no edit flow / runtime-choice detour
- `ui/screens/servers/servers_state.dart` (partial, `:feature:servers`): no welcome phone/computer choice state; no monitor/queue state per server
- `ui/screens/servers_screen.dart` (partial, `:feature:servers`): ServersRoute has only Home: no route requests (connect profile, add with kind/initial URL, EnterPhoneCredentials, edit profile)
- `ui/screens/settings/connection_check_screen.dart` (missing, `:feature:servers`, untested): Five-step connection check with per-step fix advice; Check again; Link to Reach your computer
- `ui/screens/settings/reach_computer_screen.dart` (missing, `:feature:servers`, untested): Reach your computer guidance page (Tailscale, SSH tunnel, no public tunnel)
- `ui/screens/settings/server_host_update.dart` (missing, `:feature:servers`): Paseo helper update row with reconnect and new version
- `ui/screens/settings/server_page.dart` (partial, `:feature:servers`): Restart OpenCode on its host dialog with 'I restarted it' re-check; Last good health cached per profile and painted at once
- `ui/screens/settings/server_plugins_section.dart` (missing, `:feature:servers`): Plugin inventory section with refresh on the page bar
- `ui/screens/tailscale_setup_screen.dart` (partial, `:feature:servers`): Tailscale steps panel re-checks the app on return; Continue disabled with its reason until an address is typed
- `ui/setup_commands.dart` (partial, `:feature:servers`): single copyable command source (commands only inline in copy)
- `ui/widgets/local_agent_server_entry.dart` (partial, `:feature:servers`): no Claude Code daemon row on the Servers list or server switcher; no start/restart/stop/remove/update controls for the phone daemon in UI; no 'Still starting' (8 s), 'Didn't start' retry, or 'Not answering' row states; Sign in to Claude only via separate sign-in terminal route
- `ui/widgets/phone_server_names.dart` (partial, `:feature:servers`, untested): no OpenCode-generation suffix for two in-app profiles; no phone-aware 'New conversations on this phone' / 'Saved on' titles
- `ui/widgets/saved_server_connection_card.dart` (partial, `:feature:servers`): no connecting/starting/not-answering state page while reconnecting (workServer*/e7SetupConnecting* unused)
- `ui/widgets/server_switcher_sheet.dart` (missing, `:feature:servers`): pending requests opened/allowed from the switcher
- `ui/screens/recent_error_words.dart` (partial, `:feature:settings`): crash-store source exclusion not ported
- `ui/screens/saved_permissions_screen.dart` (missing, `:feature:settings`): no SettingsRoute.SavedPermissions page or ViewModel; no revoke confirm, empty/error states
- `ui/screens/server_capabilities_screen.dart` (missing, `:feature:settings`): no 'N settings aren't available – Why' door from Settings/Tools
- `ui/screens/settings/agents_permissions_screens.dart` (partial, `:feature:settings`): Permissions and automation page (automation level, Always allowed actions) has no native page or route
- `ui/screens/settings/ai_setup_screen.dart` (missing, `:feature:settings`): Suggestions list (sign in / fix / choose model / add tools); OpenCode 2 ordered config sources shown as-is, never merged
- `ui/screens/settings/appearance_one_page.dart` (partial, `:feature:settings`): Two-column layout on wide windows; Reset to defaults with in-page confirm; Refused save said under its question
- `ui/screens/settings/appearance_screen.dart` (partial, `:feature:settings`): Shape: corners and spacing questions; Turn off Saver from a held section; Refused save puts the value back and says so
- `ui/screens/settings/default_shell_row.dart` (partial, `:feature:settings`): Only-one-shell row instead of a sheet; Load and save failure lines on the row
- `ui/screens/settings/hub_servers.dart` (partial, `:feature:settings`): Notifications row says 'Different on N servers'; This phone row says Running only while connected to the phone's server
- `ui/screens/settings/notifications_settings_screen.dart` (partial, `:feature:settings`): Background status row with restart one tap away; Refused write said on the page until the next one succeeds
- `ui/screens/settings/personal_settings_screens.dart` (partial, `:feature:settings`): Unsent work size and confirmed delete; Read-state sharing summary row
- `ui/screens/settings/phrase_chips_row.dart` (missing, `:feature:settings`): Show hidden phrases row that restores hidden chips
- `ui/screens/settings/settings_group_page.dart` (partial, `:feature:settings`): Background row value states whether runs keep updating after close
- `ui/screens/settings_screen.dart` (partial, `:feature:settings`): Opening Settings at an initial group from other entry points
- `ui/screens/tools_screen.dart` (missing, `:feature:settings`): search field from ~8 tools; Settings Tools row is hidden (not in OPENS)
- `ui/search/search_index/hub_rows.dart` (partial, `:feature:settings`): many Flutter hub entries (server status/version, per-server pages) have no native row
- `ui/widgets/appearance_picker.dart` (partial, `:feature:settings`): no Apply/Undo for a theme change
- `ui/widgets/default_notices.dart` (partial, `:feature:settings`): no modelDefaultOf equivalent bound to the live connection's selected model / explicit-pick flag; per-run (no saved server) announcement set not visible in a screen
- `ui/widgets/language_picker.dart` (partial, `:feature:settings`): refused language save not shown as a message with the old choice kept
- `ui/screens/usage/usage_actions.dart` (missing, `:feature:usage`): connection ranking for a provider (in-app Ubuntu, saved servers, phone)
- `ui/screens/usage/usage_add_picker_screen.dart` (missing, `:feature:usage`, untested): UsageRoute.Add provider picker step (plan 07 J03, merged)
- `ui/screens/usage/usage_add_screen.dart` (missing, `:feature:usage`, untested): UsageRoute.Add connection choice step
- `ui/screens/usage/usage_plan_card.dart` (missing, `:feature:usage`): plan block and needs-setup row kit arrangement
- `ui/screens/usage/usage_subscription_screen.dart` (missing, `:feature:usage`): UsageRoute.Subscription page
- `ui/screens/usage/usage_text.dart` (missing, `:feature:usage`): word helpers not ported (the copy keys exist in native/ui/words but no Kotlin uses them)
- `ui/screens/usage_hub_screen.dart` (partial, `:feature:usage`): Plans tab and section switch (UsageRoute.Hub has no section); hide Spending tab when server lacks usageSpending
- `ui/screens/usage_refresh_slot.dart` (partial, `:feature:usage`, untested): per-section Refresh offer (only the Spent section has a top-bar Refresh)
- `ui/screens/usage_screen.dart` (partial, `:feature:usage`): personal budgets; capability explainer for a server without usage statistics; charts
- `ui/screens/usage_widget_screen.dart` (partial, `:feature:usage`): usage_settings launch shortcut target
- `ui/screens/widgets/widget_project_screen.dart` (missing, `:feature:widget-panels`, untested): widget Project panel in the overlay
- `ui/widgets/safety_confirms.dart` (missing, `:ui:kit`): no disconnect confirm; no stop-local-server confirm; no stop-sharing confirm (Unshare acts directly); no MCP disconnect confirm; no local agent stop/restart/remove confirms (safety*/localAgent* words unused)
- `ui/widgets/agent_blocks.dart` (partial, `:ui:kit-chat`): no tappable choice rows that hand the option to the composer (AgentChoiceScope) or copy it as a fallback; no checklist rendering with done/to-do marks and spoken state; no 'Run on your computer' command block with per-line 48dp copy buttons
- `ui/widgets/markdown.dart` (partial, `:ui:kit-chat`): capped code block's full-screen reader: KitCodeBlock onMore/onOpenCode not wired by any feature; no interaction scope that makes markdown inert in isolated previews
- `ui/screens/terminal/terminal_accessible.dart` (partial, `:ui:kit-content`): kit accessible mode exists but no reachable terminal page hosts it (only the unwired SignInTerminalScreen)
- `ui/widgets/app_connection_status.dart` (partial, `:ui:shell`): no fold of the 'Restarted after the app closed' line on page change; no merged condition source per profile
- `ui/widgets/conversation_status_scope.dart` (partial, `:ui:shell`): no app-wide status layer to override or to keep Android-stopped/heat lines alongside
- `ui/widgets/relative_time.dart` (missing, `:ui:words`): no shared relative-age formatter; chat lists show no relative time (e7WorkspaceYesterday/JustNow unused; only Updates uses minutes-ago)

### Deferred (after-switch / dropped) files with uncatalogued behaviour

- `ui/screens/team/project_demo_screen.dart` (after-switch): project demo absent
- `ui/screens/team/project_destination.dart` (after-switch): routing of team links to a project workspace absent; native routes team links to NeedsYou/Conversations meanwhile (engine/entry)
- `ui/screens/team/projects/screen/servers_card.dart` (after-switch): max lanes editor and move/restart-elsewhere flow have no catalog row
- `ui/screens/team/projects/team_merge_flow.dart` (after-switch): merge-to-dev and promote-to-main confirm/progress/receipt flow absent
- `ui/screens/team/projects/team_project_editors_roles.dart` (after-switch): roles editor create/edit behaviour untested and uncatalogued beyond listing
- `ui/screens/team/projects/team_voice_words.dart` (after-switch): role voice names and sentences absent
- `ui/screens/team/role_screen.dart` (after-switch): role page create/edit/reset/delete absent
- `ui/screens/team/team_needs_you.dart` (after-switch): answering a team gate in place from chat has no catalog row
- `ui/screens/team/team_rig_screen.dart` (after-switch): pause/resume/remove rig confirmations uncatalogued
- `ui/widgets/team_agent_row.dart` (after-switch): dead in Flutter: no lib/ importer
- `ui/widgets/team_board_card.dart` (after-switch): team board card words (column, age, priority, type)
- `ui/widgets/team_board_move_sheet.dart` (after-switch): board move/priority/cancel/backlog sheets
- `ui/widgets/team_discover.dart` (after-switch): team server kind and Solo/Team choice
- `ui/widgets/team_host_form.dart` (after-switch): http allowed only to loopback/tailnet addresses in the team host form
- `ui/widgets/team_moments.dart` (after-switch): merged-task celebration once per task; Needs-you heading wave once
- `ui/widgets/team_now_line_view.dart` (after-switch): Why fold after eight seconds without the next stage
- `ui/widgets/team_phone_onboarding.dart` (after-switch): team on this phone setup and ready page; Android stopped the team notice
- `ui/widgets/team_phone_section.dart` (after-switch): phone team section Start/Stop/Delete and keep-running tips
- `ui/widgets/team_receipt.dart` (after-switch): gate receipt matching and row removal once confirmed
- `ui/widgets/team_role_copy.dart` (after-switch): team role name/purpose fallback to l10n by role id
- `ui/widgets/team_switch.dart` (after-switch): changed host drops old host cache

## Done files (with evidence)

- `ui/screens/scheduling/scheduled_prompts_screen.dart` → `native/feature/schedules/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/schedules/ui/SchedulesScreen.kt`, `native/feature/schedules/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/schedules/presentation/SchedulesViewModel.kt`, `native/feature/schedules/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/schedules/SchedulesEntries.kt`, `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SchedulesWiring.kt`
- `ui/screens/settings/updates_screen.dart` → `native/feature/updates/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/updates/UpdatesEntries.kt`, `native/feature/updates/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/updates/ui/UpdatesScreen.kt`, `native/feature/updates/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/updates/presentation/UpdatesViewModel.kt`, `native/feature/updates/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/updates/presentation/UpdatesUi.kt`
- `ui/screens/staged_revert_screen.dart` → `native/feature/review/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/review/ReviewEntries.kt`, `native/feature/review/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/review/ui/ReviewScreens.kt`, `native/feature/review/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/review/presentation/ReviewUi.kt`, `native/feature/review/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/review/presentation/ReviewPresenters.kt`
- `ui/theme_packs.dart` → `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/color/Packs.kt`, `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/color/CuratedPacks.kt`, `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/color/PackRecipe.kt`, `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/color/Dynamic.kt`
- `ui/theme_packs_generated.dart` → `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/color/PacksGenerated.kt`, `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/color/PackRecipe.kt`
- `ui/theme_roles.dart` → `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/color/Roles.kt`, `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/color/RoleDerivation.kt`, `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/color/Accents.kt`, `native/ui/design/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/design/color/ColorMath.kt`
- `ui/widgets/agent_card_form.dart` → `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/card/KitAgentCard.kt`, `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/form/KitForm.kt`, `native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/cards/ChatCardSlot.kt`
- `ui/widgets/agent_card_nodes.dart` → `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/card/KitAgentCard.kt`, `native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/cards/ChatCardSlot.kt`, `native/engine/cards/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/cards/CardModel.kt`
- `ui/widgets/child_limit_card.dart` → `native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/ChildLimitCard.kt`, `native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/ui/RunningScreen.kt`, `native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/RunningModel.kt`, `native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ChildProcessLimit.kt`
- `ui/widgets/tool_card_contract.dart` → `native/engine/transcript/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/transcript/turnfacts/ToolFacts.kt`, `native/engine/transcript/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/transcript/turnfacts/ToolDuration.kt`, `native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/TurnFactsWords.kt`

## Method

1. Mechanical pass (script): line counts; `part of` library; tests that import the file/its library and mention its identifiers, or are named after it; catalog rows whose `test:line` source block names the file's identifiers (strong) or come from those tests (candidate).
2. Per-file review (8 parallel readers, ~30 files each): read the Flutter file, grep `native/**` production sources (commonMain/androidMain/main only) for class names, route ids and the English text of its l10n keys, check reachability through `*Entries.kt`/app wiring, and decide the status strictly (done only with named native files; strings in `:ui:words` that no Kotlin uses do not count). Candidate rows were kept only when about the file.
3. Merge: native paths checked to exist, catalog ids checked against `behaviors.jsonl` (two non-existent ids dropped), every listed file present once.

