# Native vs Flutter feature-parity audit

Date 2026-11-06. Read-only audit. Native tree: `/home/owner/Storage2/wt/scr-merge/native` (HEAD 27bc42b60). Flutter: `/home/owner/Storage2/wt/flutter-ref/lib` and the UX map `docs/ux-system/map/all.json` (356 pages, 1,233 listed actions). Machine-readable rows: `PARITY.json` (1266 rows: 1233 map actions plus 33 extra rows for features found in newer Flutter source that the map predates).

> **2026-11-07 — Termux retired.** The owner removed the Termux app from native: built-in Linux is the only engine on the phone and a computer is the other way. Every row that mentions Termux is now N/A ("retired") in `PARITY.json`; the Termux rows below are history, not to-do. A Flutter user with a Termux setup sees one notice instead.

> **2026-11-11 — the Conversations home shows one notice at a time (owner).** Native differs from Flutter on purpose:
> the home shows at most one notice, chosen by `HomeNotices` (feature/chats `presentation/HomeNotices.kt`), and drops
> three lines nobody can act on: Flutter's "Showing conversations in {project} only. This server can't list all
> projects." (`chatsHomeOnlyProject`, chats_home_screen.dart:468; the projects chip names that project instead of "All
> projects", and its menu has no "All projects" row), "Loading {agents} conversations…" (`chatsHomeStillLoading`; the
> refresh indicator says it), and native's own "Can't tell what's new on this server". Flutter's quiet plan-limit line
> stays, as a notice with the agent's page. The phone agents' connection (Paseo, `fetch_agents_request` has no folder
> filter) reports `globalSessionSearch = false`; the home counts it as reaching every project
> (`PhoneAgentRules.listsAcrossProjects`), so it never makes This phone's list look single-project.

> **2026-11-11 — one This phone (owner).** As in Flutter, This phone's agents are part of This phone, never a server:
> native still saves their helper connection as a profile (`<phone>-agents`), but it carries the phone's name, is left
> out of every server list (Servers sheet and pill, Settings, Servers, New conversation, Watched servers, search and
> usage, Projects), goes when This phone is removed, and its folders join This phone's projects.

> **2026-11-11 — Export projects counts folders only (device sweep S10).** Native differs from Flutter on purpose:
> Flutter's Export lists every top-level entry of the projects folder as a project (project_export.dart:332), so loose
> files (`notes.txt`) read as projects. Native counts and lists only folders as projects and lists loose files apart
> under "Files" (`ProjectStorageFacts.folders` / `looseFiles`, feature/phone `storage/ProjectStorage.kt`). The zip is
> unchanged: loose files are still exported (`NativeProjectFiles.plan`), and Manage space still lists every entry.
> Related: Add tools on an installed phone (S9) still opens as its own route, but draws nothing under its sheet and
> closing the sheet leaves that route (Flutter opens the sheet over This phone itself).


## How to read the counts

- **PRESENT**: a native screen or control with the same job exists. Most PRESENT rows rest on page-level evidence (the native screen exists and its strings/code were read), not a per-button check; rows with a precise `native_ref` were checked at that line.
- **PARTIAL**: the job can be done but part of it is missing or moved (what is missing is in `note`).
- **MISSING**: no native equivalent found (searched string resources, route table, view-model actions). Many Flutter strings for these exist in `ui/words` resources but have no `Res.string` caller: the words were ported, the UI was not.
- **UNVERIFIED**: could not be located either way in the time available.
- **N/A**: owner-dropped or out of scope: AI Team/FactsBook (deferred after the switch, plan/01 section 12 D6; a recommended default, no recorded owner approval was found), A2A external agents (dropped), Termux setup (migrate-out only, D7), desktop shortcuts/palette/drag-drop, Shorebird notice, and map entries whose Flutter screen no longer exists (Context capsule, Connection help).

## Counts per area

| Area | PRESENT | PARTIAL | MISSING | UNVERIFIED | N/A | Total |
|---|---|---|---|---|---|---|
| about | 9 | 0 | 5 | 0 | 0 | 14 |
| agents | 114 | 1 | 4 | 0 | 31 | 150 |
| ai-team | 0 | 0 | 0 | 0 | 157 | 157 |
| chat | 138 | 38 | 43 | 8 | 2 | 229 |
| composer | 71 | 14 | 6 | 0 | 8 | 99 |
| conversations | 41 | 13 | 5 | 0 | 4 | 63 |
| files | 45 | 5 | 3 | 0 | 0 | 53 |
| new-conversation | 7 | 2 | 1 | 0 | 0 | 10 |
| notifications | 11 | 0 | 0 | 0 | 0 | 11 |
| projects | 45 | 32 | 30 | 0 | 0 | 107 |
| servers | 40 | 21 | 0 | 1 | 4 | 66 |
| settings | 36 | 4 | 9 | 0 | 7 | 56 |
| setup | 29 | 1 | 0 | 0 | 0 | 30 |
| shell | 11 | 9 | 2 | 3 | 20 | 45 |
| terminal | 17 | 0 | 0 | 0 | 0 | 17 |
| this-phone | 27 | 7 | 0 | 0 | 62 | 96 |
| usage | 31 | 4 | 0 | 2 | 0 | 37 |
| voice | 23 | 3 | 0 | 0 | 0 | 26 |
| **All** | **695** | **154** | **108** | **14** | **295** | **1266** |

Area notes: "chat" includes sheets/menus of the conversation; "ai-team" is all deferred; "this-phone" N/A count is mostly Termux setup pages; "projects" MISSING is dominated by two capability-gated Flutter surfaces (Cloud environments and Move session / organization switch) plus Rename project.

## Why it was missed (root cause)

Native screens were built from design orders and screenshots. The map `actions` lists were never turned into a checklist, and most of the MISSING actions live in sheets, overflow menus and dialogs that no screenshot shows (command launcher, Compact, running-work sheet, move-session, plugins, error-card recovery). Many of those strings were already copied into `strings_*.xml` (for example `photoPendingTitle`, `projectFolderMissingTitle`, `e7ProjectProjectRenameTitle`, `e7LibraryResetWorktree`, `composerClearTextTitle`, `chatCollapseAllSteps` have resources and no caller), which makes a string-count check look healthy. A cheap guard: a CI check that lists `ui/words` string keys with zero `Res.string` references, grouped by Flutter source file.

## Ranked gaps (top 25, then the rest)

| # | Gap | Flutter evidence | Native place and note |
|---|---|---|---|
| 1 | Compact conversation (manual) | Session menu `compact` (lib/ui/screens/chat/chat_session_menu.dart, SessionMenuAction.compact), confirm sheet chat_session_actions.dart:362, context sheet, error card (assistant_error_row.dart:225), nudge (nudge_slot.dart:208) | ChatPresenter.compactAgain exists and `ChatWorld.compact()` is implemented, but the only trigger is a compaction marker (ChatScreen.kt:615). Add to ChatHeaderBar `menuItems` (HeaderAction) and the context sheet. |
| 2 | Error card recovery actions (Choose another model, Use X and resend, Open providers, Open account, Compact, Continue this reply) | lib/ui/screens/chat/assistant_error_row.dart:88,208,225,235,245; message_view.dart | Native error card shows text + Details/Copy only (ChatScreen.kt:1103 rememberReplyFailedWords, ChatTranscriptDetails.kt). A sign-in or no-credit failure is a dead end. Add actions to the failed-turn row. |
| 3 | Commands launcher (about 35 mobile commands: /new /sessions /files /terminal /models /diff /share /undo /redo /export /fork /compact /shell /retry /reload ...) and @subagent delegation | lib/ui/screens/chat/command_launcher.dart:3, chat_command_actions.dart:58, composer_tools.dart:218, chat_composer_region.dart:582 | Composer only has the "/" server-command strip and "@" files (BasicComposerViewModel.kt loadCommands). Needs a Commands row in the attach sheet (BasicComposerHost.kt) and a launcher sheet in feature/chat. This single gap also hides Retry last prompt, Redo, Reload, Export, Run shell. |
| 4 | Send later or repeat from the composer (long-press Send) | lib/ui/screens/chat/chat_schedule_send.dart:7 (tip tipSendLater) | SchedulesScreen/ScheduleEditScreen exist but are reachable only from Servers (ServersViewModel.kt:481). Native even teaches "Hold Send to send later" (TeachingScreen.kt:168) for a gesture that does not exist. Add the Send long-press menu in ComposerScreen/BasicComposerHost opening ScheduleEdit with this conversation as target. |
| 5 | Run shell command in the conversation | lib/ui/screens/chat/chat_commands.dart:90 (_runShellDialog); map chat-run-shell-dialog | No shell action in feature/chat. Needs a dialog plus a world.shell() call (OC protocols have it); gate on ServerCapabilities.conversationShell. |
| 6 | Tasks / running work sheet (background commands and agents, shell output, Stop command, Change timeout, Run in background, Ctrl+B) | lib/ui/screens/running_work_sheet.dart:108,736,965; chat_top_bar.dart:357 (workCount); map running-work-sheet, shell-output* | Native has Todos (ChatTasksSheet) and Dev services only. Needs a top-bar "Tasks - N running" line action in ChatScreen.kt (unitActions list) and a sheet in feature/chat. |
| 7 | Create/open project paths: New project only works on the phone server, no New project in New-conversation project sheet, no "Create this folder?" when a typed path is missing, no Rename project | project_folder_actions.dart:56 (canCreate), :602 (projectFolderMissingTitle), projects_screen.dart:234 (rename), folder_browser.dart:525 (name step) | New project row exists in ProjectsPickerScreen.kt:293 but is disabled unless `creator.available(server)` (ProjectsPickerViewModel.kt:112) and shows a greyed row on remote servers; NewConversationScreen.kt:360 project sheet has only the known-project list + typed path. This is the gap the owner hit. Decide: show New project on remote servers by creating the folder through the server (shell/mkdir) or keep it phone-only and hide the row. |
| 8 | Rename project | lib/ui/screens/projects_screen.dart:234; map projects-rename-dialog | Strings e7ProjectProjectRenameTitle/e7ProjectProjectRenameFailed are unused. Add to the project row menu in ProjectsPickerScreen.kt. |
| 9 | Settings > Server plugins page (details, Link commands, review /command, clear links) | lib/ui/screens/settings/server_plugins_section.dart:43, plugins_screen.dart; map plugins-settings, plugins-mapping-dialog | Only a one-line capability row (ServerCapabilitiesPage.kt:173). Needs a page under Settings > Server. |
| 10 | Chat list rows: Share / Details / Continue here / Open related / Copy handoff | global_sessions_screen.dart:1157,1170; workspace row menu; map workspace, global-sessions | Chats home row menu has Open + Archive (ChatsScreen.kt:407); All list adds Pin, Rename, Select, Delete (ChatsAllScreen.kt:437). Share and Details only from inside the conversation; Continue here and Copy handoff absent. |
| 11 | Move session to another project or cloud environment + Cloud environments (managed workspaces) + organization switch | session_destination_sheet.dart:24; managed_workspaces_screen.dart:32; map session-destination-*, managed-workspaces*, console-organization-* | Nothing in native beyond a capability explainer. Capability-gated in Flutter; confirm with owner whether any current server exposes it before porting. |
| 12 | Files: Add as reference / viewer Reference | files/files_actions.dart:72 (readerUiReferenceAdded); map files-row-actions-sheet, files-file-viewer-sheet | Native offers Attach to prompt and Ask about this file (FolderScreen.kt:611). References insert an @path without uploading. |
| 13 | Pending photo recovery after a crash (Add recovered photo / Discard) | chat_composer_region.dart:428 | Strings photoPendingTitle/photoDiscard are unused. |
| 14 | Open on another phone (copy session link) and Copy share link banner | chat_session_menu.dart (SessionMenuAction.continueOnPhone), session_handoff_sheets.dart:410, chat_states.dart:535 | Native has Continue on computer and Linked conversations, not the link for another phone; after Share there is no persistent banner with Copy link. |
| 15 | Run results page and completion-digest actions (Review next actions, Run results, Copy digest) | run_result_screen.dart; completion_digest.dart; map run-result*, embedded-completion-digest-card | Native shows results as turn-fact rows (TurnFactsPresenter.kt, RunResult.kt) and a return-brief Continue chip (ChatsScreen.kt:346). |
| 16 | App diagnostics: copy error log, Send to server log, performance report, expand an error | app_diagnostics_screen.dart:442; perf_trace_section.dart | Native keeps crash reports and exit history only (KeptScreen.kt). |
| 17 | Worktrees: Reset, open one, Copy folder path | worktrees_screen.dart:367,583 | Native lists/creates/removes (ProjectWorkspaceSheet.kt, WorktreeInspectionPane.kt); reset and open-in-worktree absent. |
| 18 | Opening failure card: Change server, Update password / token inline; global connection banner with Details | saved_server_connection_card.dart; connection_status_banner.dart | OpeningGateScreen.kt has retry only; chat shows reconnecting text (ChatScreen.kt:850). |
| 19 | Review: Copy patch / Copy updated file, expand context, change-scope picker | review_workspace.dart:1020,1127 | KitDiffView has copy lines, hide unchanged, side-by-side, comment, hunk nav (KitDiffView.kt:290-383). |
| 20 | Voice composer: edit transcript before Insert, Insert and send | voice_ui.dart (voice-composer-sheet) | Native is hold-to-talk into the field (ComposerVoiceSlot.kt:187). |
| 21 | MCP: Detect OAuth automatically, timeout/working directory; Tools: Copy parameter schema | mcp_setup_screen.dart:918,941; tools_screen.dart:358 | ToolsScreen.kt and McpCatalogScreen.kt cover add/connect/disconnect only. |
| 22 | Per-agent Sign out, Cancel setup, Switch to built-in server | agent_sheet.dart:446,753; agents_screen.dart:51 | AgentsScreen.kt has Reset saved sign-ins (all) only. |
| 23 | Pinned-conversation launcher shortcut | lib/main.dart (shortcuts); map system | androidApp shortcuts.xml has Connect and New task only. |
| 24 | Clear draft text (keeps attachments, undo) and Collapse all steps | composer_tools.dart:259; chat_top_bar.dart:347 | Strings composerClearTextTitle and chatCollapseAllSteps are unused. |
| 25 | Single active-server switcher / Switch server | server_switcher_sheet.dart; home_screen.dart:575 | Design change: native lists several connections at once (ChatsHomeSheets.kt:232 area). Confirm intended, then drop the Flutter rows as N/A. |

### Also noted

- Capability-gated rows show greyed instead of absent in native (New project). The owner rule "a row the platform cannot serve is absent, never greyed" (SettingsStructure.kt comment) is not applied in ProjectsPickerScreen.kt:294.
- Native-only additions not in Flutter: Git screen (clone, remotes, GitHub sign-in), Find on my network, Connection check, queue move between servers, kept prompts, session transfer. These are not gaps.
- The map is older than the code: Work screen retired, Context capsule and Connection help screens removed, session menu is now 16 items (SessionMenuAction). Rows tied to those are N/A or re-pointed.
- AI Team (157 rows), FactsBook (14 screens), A2A agents: not ported; 1,552 team strings exist in `strings_team.xml` with no UI. If D6 deferral was never approved by the owner, that is the largest gap by far.

## Full MISSING list

| Area | Page | Action | Flutter | Note |
|---|---|---|---|---|
| chat | chat | move running work to background (Ctrl+B) | lib/ui/screens/chat_screen.dart | Ctrl+B / run in background has no native equivalent |
| composer | chat-pending-photo-sheet | Discard pending photo | lib/ui/screens/chat_screen.dart | no recovered-photo sheet (photoPendingTitle/photoDiscard unused) |
| composer | chat-pending-photo-sheet | Keep editing | lib/ui/screens/chat_screen.dart | no recovered-photo sheet (photoPendingTitle/photoDiscard unused) |
| chat | session-menu-sheet | Results | lib/ui/screens/chat/attention_card.dart | Run results page absent |
| chat | session-menu-sheet | Retry last prompt | lib/ui/screens/chat/attention_card.dart | Flutter has Retry in the Commands launcher; native has no launcher |
| chat | session-menu-sheet | Compact | lib/ui/screens/chat/attention_card.dart | manual Compact absent: only "Compact again" after an automatic compaction (ChatPresenter compactAgain) |
| chat | session-menu-sheet | Run shell command | lib/ui/screens/chat/attention_card.dart | run shell command absent |
| chat | session-menu-sheet | Commands | lib/ui/screens/chat/attention_card.dart | command launcher absent |
| chat | session-menu-sheet | Open on another phone | lib/ui/screens/chat/attention_card.dart | link for another phone absent |
| chat | session-menu-sheet | Reload messages | lib/ui/screens/chat/attention_card.dart | no manual reload (refresh via re-open / pull) |
| chat | command-launcher-sheet | run a mobile command (~35: /new, /sessions, /files, /terminal, /models | lib/ui/screens/chat/command_launcher.dart | no mobile command launcher (~35 commands); only server slash commands in the composer strip |
| chat | command-launcher-sheet | delegate to a subagent | lib/ui/screens/chat/command_launcher.dart | no @subagent delegation |
| chat | command-launcher-sheet | search, clear, refresh | lib/ui/screens/chat/command_launcher.dart | no mobile command launcher (~35 commands); only server slash commands in the composer strip |
| chat | chat-run-shell-dialog | Run | lib/ui/screens/chat_screen.dart | no run-shell-command in a conversation (no caller of a shell action in feature/chat) |
| chat | chat-run-shell-dialog | Cancel | lib/ui/screens/chat_screen.dart:747 | no run-shell-command in a conversation (no caller of a shell action in feature/chat) |
| composer | prompt-tools-sheet | Commands | lib/ui/screens/chat/composer.dart:12 | no Commands row |
| composer | prompt-tools-sheet | Clear draft text | lib/ui/screens/chat/composer.dart | no Clear draft text with undo (composerClearTextTitle unused) |
| chat | embedded-message-view | error card actions (Choose model, Compact, Open providers, Continue, D | lib/ui/screens/chat/message_view.dart | native error card has only Details and Copy details: no Choose model, Compact, Open providers, Continue |
| chat | embedded-shared-session-banner | Copy share link | lib/ui/screens/chat/chat_states.dart:535 | no Copy share link after sharing |
| chat | embedded-chat-nudge-slot | Compact conversation | lib/ui/screens/chat/nudge_slot.dart:208 | compact nudge absent (no compact) |
| chat | embedded-completion-digest-card | Review next actions | lib/ui/widgets/completion_digest.dart | digest actions absent |
| chat | embedded-completion-digest-card | Run results | lib/ui/widgets/completion_digest.dart | digest actions absent |
| chat | embedded-completion-digest-card | Copy digest | lib/ui/widgets/completion_digest.dart | digest actions absent |
| files | files-row-actions-sheet | Add as reference | lib/ui/screens/files_screen.dart | "Add as reference" absent; Attach to prompt and Ask about this file exist |
| files | files-file-viewer-sheet | Reference | lib/ui/screens/files_screen.dart:41 | no reference action in viewer |
| chat | continue-on-phone-sheet | copy link | lib/ui/widgets/session_handoff_sheets.dart:410 | Open on another phone (copy session link) absent |
| agents | mcp-setup | Detect OAuth automatically | lib/ui/screens/mcp_setup_screen.dart:918 | OAuth auto-detect and timeouts absent |
| agents | tools-detail-sheet | Copy parameter schema | lib/ui/screens/tools_screen.dart:358 | no copy-schema action |
| settings | plugins-settings | refresh | lib/ui/screens/settings/plugins_screen.dart:99 | server Plugins page and command-link mapping absent (only a capability line in ServerCapabilitiesPage) |
| settings | plugins-settings | row menu: Details, Link commands, Review /command | lib/ui/screens/settings/plugins_screen.dart | server Plugins page and command-link mapping absent (only a capability line in ServerCapabilitiesPage) |
| settings | plugins-settings | clear personal links | lib/ui/screens/settings/plugins_screen.dart | server Plugins page and command-link mapping absent (only a capability line in ServerCapabilitiesPage) |
| settings | plugins-clear-mappings-sheet | Clear links | lib/ui/screens/settings/server_plugins_section.dart | server Plugins page and command-link mapping absent (only a capability line in ServerCapabilitiesPage) |
| settings | plugins-clear-mappings-sheet | Cancel | lib/ui/screens/settings/server_plugins_section.dart:105 | server Plugins page and command-link mapping absent (only a capability line in ServerCapabilitiesPage) |
| settings | plugins-mapping-dialog | check commands | lib/ui/screens/settings/server_plugins_section.dart | server Plugins page and command-link mapping absent (only a capability line in ServerCapabilitiesPage) |
| settings | plugins-mapping-dialog | Save links | lib/ui/screens/settings/server_plugins_section.dart | server Plugins page and command-link mapping absent (only a capability line in ServerCapabilitiesPage) |
| settings | plugins-mapping-dialog | Cancel | lib/ui/screens/settings/server_plugins_section.dart:105 | server Plugins page and command-link mapping absent (only a capability line in ServerCapabilitiesPage) |
| about | app-diagnostics | Send to server log | lib/ui/screens/app_diagnostics_screen.dart | error log / performance report not in native diagnostics |
| about | app-diagnostics | Copy | lib/ui/screens/app_diagnostics_screen.dart:442 | error log / performance report not in native diagnostics |
| about | app-diagnostics | Copy performance report | lib/ui/screens/app_diagnostics_screen.dart | error log / performance report not in native diagnostics |
| about | app-diagnostics | Clear performance (unconfirmed) | lib/ui/screens/app_diagnostics_screen.dart | error log / performance report not in native diagnostics |
| about | app-diagnostics | expand an error | lib/ui/screens/app_diagnostics_screen.dart | error log / performance report not in native diagnostics |
| shell | system | launcher shortcuts: Connect, New task, pinned conversation | lib/main.dart | pinned-conversation launcher shortcut not found (shortcuts.xml has Connect and New task) |
| shell | root-connecting | Change server | lib/ui/widgets/saved_server_connection_card.dart | no Change server from the opening failure card |
| conversations | global-sessions | Open related | lib/ui/screens/global_sessions_screen.dart:1157 | row actions Open related / Copy handoff / Continue here absent |
| conversations | global-sessions | Copy handoff | lib/ui/screens/global_sessions_screen.dart | row actions Open related / Copy handoff / Continue here absent |
| conversations | global-sessions | Continue here | lib/ui/screens/global_sessions_screen.dart:1170 | row actions Open related / Copy handoff / Continue here absent |
| conversations | global-sessions-continue-here-sheet | Continue here | lib/ui/screens/global_sessions_screen.dart:1170 | Continue here (adopt a conversation from another project/server) absent |
| conversations | global-sessions-continue-here-sheet | Cancel | lib/ui/screens/global_sessions_screen.dart | Continue here (adopt a conversation from another project/server) absent |
| projects | managed-workspaces | Discover existing | lib/ui/screens/managed_workspaces_screen.dart:19 | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces | Refresh | lib/ui/screens/managed_workspaces_screen.dart:460 | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces | New environment | lib/ui/screens/managed_workspaces_screen.dart:364 | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces | Open / Open again | lib/ui/screens/managed_workspaces_screen.dart | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces | Remove | lib/ui/screens/managed_workspaces_screen.dart:267 | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces | pull to refresh | lib/ui/screens/managed_workspaces_screen.dart:19 | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces | Try again | lib/ui/screens/managed_workspaces_screen.dart:443 | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces-create-dialog | pick adapter | lib/ui/screens/managed_workspaces_screen.dart | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces-create-dialog | branch | lib/ui/screens/managed_workspaces_screen.dart:707 | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces-create-dialog | Create and open | lib/ui/screens/managed_workspaces_screen.dart:180 | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces-create-dialog | Cancel | lib/ui/screens/managed_workspaces_screen.dart | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces-remove-dialog | type the name | lib/ui/screens/managed_workspaces_screen.dart | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces-remove-dialog | Remove permanently | lib/ui/screens/managed_workspaces_screen.dart | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | managed-workspaces-remove-dialog | Cancel | lib/ui/screens/managed_workspaces_screen.dart | Cloud environments / managed workspaces absent (capability flag capabilityCloud only); capability-gated in Flutter (managed workspaces), own |
| projects | projects-rename-dialog | edit | lib/ui/screens/projects_screen.dart:54 | Rename project dialog absent (projects rename strings unused) |
| projects | projects-rename-dialog | Save | lib/ui/screens/projects_screen.dart:245 | Rename project dialog absent (projects rename strings unused) |
| projects | projects-rename-dialog | Cancel | lib/ui/screens/projects_screen.dart | Rename project dialog absent (projects rename strings unused) |
| chat | running-work-sheet | Run in background | lib/ui/screens/running_work_sheet.dart | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | running-work-sheet | open an agent's conversation | lib/ui/screens/running_work_sheet.dart | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | running-work-sheet | open a command's output | lib/ui/screens/running_work_sheet.dart | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | running-work-sheet | Refresh | lib/ui/screens/running_work_sheet.dart:931 | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | running-work-sheet | Close | lib/ui/screens/running_work_sheet.dart:367 | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | running-work-sheet | Try again | lib/ui/screens/running_work_sheet.dart:515 | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | shell-output | Copy output | lib/ui/screens/running_work_sheet.dart:728 | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | shell-output | Refresh | lib/ui/screens/running_work_sheet.dart:931 | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | shell-output | Follow output | lib/ui/screens/running_work_sheet.dart | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | shell-output | Change timeout | lib/ui/screens/running_work_sheet.dart:965 | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | shell-output | Stop command | lib/ui/screens/running_work_sheet.dart:738 | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | shell-output | Load more output | lib/ui/screens/running_work_sheet.dart:1011 | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | shell-output | Try again | lib/ui/screens/running_work_sheet.dart:515 | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | shell-output-stop-dialog | Stop command | lib/ui/screens/running_work_sheet.dart:738 | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | shell-output-stop-dialog | Cancel | lib/ui/screens/running_work_sheet.dart:305 | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| chat | shell-output-timeout-sheet | pick a timeout | lib/ui/screens/running_work_sheet.dart | no Tasks / background-commands sheet in the conversation (only dev services in Project) |
| projects | session-destination-sheet | filter | lib/ui/screens/session_destination_sheet.dart:492 | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| projects | session-destination-sheet | pick destination | lib/ui/screens/session_destination_sheet.dart | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| projects | session-destination-sheet | Close | lib/ui/screens/session_destination_sheet.dart:21 | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| projects | session-destination-sheet | Try again | lib/ui/screens/session_destination_sheet.dart:506 | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| projects | session-destination-confirm-dialog | Move with changes / Move | lib/ui/screens/session_destination_sheet.dart | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| projects | session-destination-confirm-dialog | Move only | lib/ui/screens/session_destination_sheet.dart | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| projects | session-destination-confirm-dialog | Cancel | lib/ui/screens/session_destination_sheet.dart:126 | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| projects | console-organization-sheet | pick an organization | lib/ui/screens/session_destination_sheet.dart | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| projects | console-organization-sheet | Close | lib/ui/screens/session_destination_sheet.dart:21 | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| projects | console-organization-sheet | Try again | lib/ui/screens/session_destination_sheet.dart:506 | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| projects | console-organization-switch-dialog | Switch | lib/ui/screens/session_destination_sheet.dart:191 | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| projects | console-organization-switch-dialog | Cancel | lib/ui/screens/session_destination_sheet.dart:126 | Move session between projects/cloud environments + organization switch absent; capability-gated in Flutter |
| composer | chat-composer | Hold Send to send later or repeat (schedule this prompt for this conve | lib/ui/screens/chat/chat_schedule_send.dart:3 | Schedules exist (SchedulesScreen, reachable only from Servers); the composer has no long-press Send menu and the schedule editor cannot be o |
| chat | assistant-error-row | Error card: Choose another model / Use {model} and resend | lib/ui/screens/chat/assistant_error_row.dart:88 | native error card only has Details and Copy details |
| chat | assistant-error-row | Error card: Open providers / Open account (sign-in errors) | lib/ui/screens/chat/assistant_error_row.dart:235 | sign-in error is plain text with no way to the provider sign-in page |
| chat | assistant-error-row | Error card: Compact conversation (context overflow) | lib/ui/screens/chat/assistant_error_row.dart:225 | overflow error has no Compact action |
| chat | assistant-error-row | Continue this reply (after a paused/stopped reply) | lib/ui/screens/chat/assistant_error_row.dart:245 | strings exist but are unused |
| chat | chat-top-bar | Tasks · N running (background commands and agents sheet) | lib/ui/screens/chat/chat_top_bar.dart:357 | see running-work-sheet |
| chat | chat-top-bar | Collapse all steps | lib/ui/screens/chat/chat_top_bar.dart:347 | strings unused in native |
| chat | chat-session-actions | Manual Compact with confirmation ("Compact this conversation?") | lib/ui/screens/chat/chat_session_actions.dart:362 | world.compact() exists; only reachable from a compaction marker |
| agents | agent-sheet | Cancel setup of a phone agent | lib/ui/screens/agents/agent_sheet.dart:753 | agentsCancelSetup unused |
| agents | agents-screen | Switch to the built-in server | lib/ui/screens/agents/agents_screen.dart:51 |  |
| settings | settings-group | Server plugins: details, link commands, review /command | lib/ui/screens/settings/plugins_screen.dart | no native Plugins page |
| projects | projects | Rename project | lib/ui/screens/projects_screen.dart:234 | native strings for rename are unused |
| new-conversation | project-folder-actions | "Create this folder?" when an opened path does not exist | lib/ui/screens/project_folder_actions.dart:602 | Flutter offers to create a missing folder on open |
| files | files-viewer | Reference / Add as reference to the prompt | lib/ui/screens/files/files_actions.dart:72 |  |
| composer | chat-composer | Photo recovery: Pending photo / Add recovered photo to draft / Discard | lib/ui/screens/chat/chat_composer_region.dart:428 | strings unused |

## Full PARTIAL list

| Area | Page | Action | Flutter | Native | Note |
|---|---|---|---|---|---|
| chat | chat | commands and @subagents | lib/ui/screens/chat_screen.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/presentation/BasicComposerViewModel.kt:437 | server slash commands and @file only; no @subagent mention, no mobile commands |
| chat | chat | session menu (share, fork, compact, retry, revert, shell, ap | lib/ui/screens/chat_screen.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/header/ui/ChatHeaderBar.kt:213 | menu has fewer items |
| chat | session-menu-sheet | display toggles | lib/ui/screens/chat/attention_card.dart | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/presentation/SettingsStructure.kt:21 | only in Settings, not in the session menu |
| chat | session-menu-sheet | Use a skill | lib/ui/screens/chat/attention_card.dart | ToolsScreen.kt (file not found) | Tools page adds a skill to the current conversation; not a session-menu row |
| chat | session-menu-sheet | Revert last prompt | lib/ui/screens/chat/attention_card.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/MessageMenu.kt:69 | per-message Undo from here; no menu shortcut for the last prompt |
| chat | command-launcher-sheet | run a server command | lib/ui/screens/chat/command_launcher.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/presentation/BasicComposerViewModel.kt:437 | server commands run from the "/" strip in the composer |
| composer | embedded-composer | inline command/agent pick | lib/ui/screens/chat/composer.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/presentation/BasicComposerViewModel.kt:437 | / commands and @ files only |
| composer | prompt-tools-sheet | Voice input | lib/ui/screens/chat/composer.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/BasicComposerHost.kt:104 | attach sheet exists; Commands and Context capsule rows missing |
| composer | prompt-tools-sheet | Voice conversation | lib/ui/screens/chat/composer.dart:568 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/BasicComposerHost.kt:104 | attach sheet exists; Commands and Context capsule rows missing |
| composer | prompt-tools-sheet | Reuse a prompt | lib/ui/screens/chat/composer.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/BasicComposerHost.kt:104 | attach sheet exists; Commands and Context capsule rows missing |
| composer | prompt-tools-sheet | Older drafts | lib/ui/screens/chat/composer.dart | feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/KeptPromptsScreen.kt:1 | Kept prompts |
| composer | prompt-tools-sheet | Stash current prompt | lib/ui/screens/chat/composer.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/BasicComposerHost.kt:104 | attach sheet exists; Commands and Context capsule rows missing |
| composer | prompt-tools-sheet | Saved prompts | lib/ui/screens/chat/composer.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/BasicComposerHost.kt:104 | attach sheet exists; Commands and Context capsule rows missing |
| chat | embedded-shared-session-banner | Stop sharing | lib/ui/screens/chat/chat_states.dart:542 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/header/ui/ChatHeaderBar.kt:94 | stop sharing in menu; no banner with Copy share link |
| chat | embedded-chat-nudge-slot | Approvals | lib/ui/screens/chat/nudge_slot.dart:187 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/MessageTeaching.kt:1 | one-time tips exist as teaching; per-action nudges (Approvals/Review/Compact) not matched |
| chat | embedded-chat-nudge-slot | Review changes | lib/ui/screens/chat/nudge_slot.dart:202 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/MessageTeaching.kt:1 | one-time tips exist as teaching; per-action nudges (Approvals/Review/Compact) not matched |
| chat | embedded-chat-nudge-slot | Got it | lib/ui/screens/chat/nudge_slot.dart:215 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/MessageTeaching.kt:1 | one-time tips exist as teaching; per-action nudges (Approvals/Review/Compact) not matched |
| chat | embedded-chat-nudge-slot | Hide tip | lib/ui/screens/chat/nudge_slot.dart:158 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/MessageTeaching.kt:1 | one-time tips exist as teaching; per-action nudges (Approvals/Review/Compact) not matched |
| chat | embedded-completion-digest-card | Open conversation | lib/ui/widgets/completion_digest.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:133 | return-brief chip shows Continue; digest actions (Review next actions, Run results, Copy digest) absent |
| chat | embedded-completion-digest-card | Dismiss | lib/ui/widgets/completion_digest.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:133 | return-brief chip shows Continue; digest actions (Review next actions, Run results, Copy digest) absent |
| chat | embedded-markdown-text | open link (openExternalLink) | lib/ui/widgets/markdown.dart | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt:1 | links, copy code and wrap exist; full-screen Code reader sheet not found |
| chat | embedded-markdown-text | open file chip | lib/ui/widgets/markdown.dart | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt:1 | links, copy code and wrap exist; full-screen Code reader sheet not found |
| chat | embedded-markdown-text | copy code | lib/ui/widgets/markdown.dart:312 | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt:1 | links, copy code and wrap exist; full-screen Code reader sheet not found |
| chat | embedded-markdown-text | wrap | lib/ui/widgets/markdown.dart:167 | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt:1 | links, copy code and wrap exist; full-screen Code reader sheet not found |
| chat | embedded-markdown-text | full screen | lib/ui/widgets/markdown.dart | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt:1 | links, copy code and wrap exist; full-screen Code reader sheet not found |
| chat | embedded-markdown-text | choose option | lib/ui/widgets/markdown.dart | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt:1 | links, copy code and wrap exist; full-screen Code reader sheet not found |
| chat | embedded-markdown-text | copy command | lib/ui/widgets/markdown.dart | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt:1 | links, copy code and wrap exist; full-screen Code reader sheet not found |
| chat | markdown-code-reader | back | lib/ui/widgets/markdown.dart | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt:1 | links, copy code and wrap exist; full-screen Code reader sheet not found |
| chat | markdown-code-reader | wrap | lib/ui/widgets/markdown.dart:167 | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt:1 | links, copy code and wrap exist; full-screen Code reader sheet not found |
| chat | markdown-code-reader | copy | lib/ui/widgets/markdown.dart:170 | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/markdown/KitMarkdown.kt:1 | links, copy code and wrap exist; full-screen Code reader sheet not found |
| chat | embedded-return-brief-panel-status-dialog | (page itself) | lib/ui/widgets/return_brief_card.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:118 | return brief on Chats home; per-conversation Unreviewed work panel absent |
| chat | embedded-return-brief-panel | (page itself) | lib/ui/widgets/return_brief_panel.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:118 | return brief on Chats home; per-conversation Unreviewed work panel absent |
| voice | voice-composer-sheet | edit transcript | lib/voice/voice_ui.dart | feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/ui/ComposerVoiceSlot.kt:46 | hold-to-talk puts words in the field; separate transcript editor and Insert and send not found |
| voice | voice-composer-sheet | Insert | lib/voice/voice_ui.dart | feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/ui/ComposerVoiceSlot.kt:46 | hold-to-talk puts words in the field; separate transcript editor and Insert and send not found |
| voice | voice-composer-sheet | Insert & send | lib/voice/voice_ui.dart | feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/ui/ComposerVoiceSlot.kt:46 | hold-to-talk puts words in the field; separate transcript editor and Insert and send not found |
| files | files | order | lib/ui/screens/files_screen.dart:139 | feature/files/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/files/ui/FolderScreen.kt:107 | server order / source first only |
| files | files | Report a bug (failure state) | lib/ui/screens/files_screen.dart | ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitStateView.kt:51 | kit state view offers Report a problem |
| files | review-workspace | switch scope | lib/ui/screens/review_workspace.dart | feature/review/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/review/ui/ReviewScreens.kt:63 | scope notice; scope picker (turn/branch/uncommitted) not matched |
| files | review-workspace | copy selection/patch | lib/ui/screens/review_workspace.dart | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/diff/KitDiffView.kt:371 | copy lines; no Copy patch / Copy updated file |
| files | review-workspace | expand context | lib/ui/screens/review_workspace.dart | ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/diff/KitDiffView.kt:247 | hide unchanged only |
| chat | run-command-dialog | choose chat | lib/ui/widgets/run_command_dialog.dart | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/LibraryScreen.kt:175 | Library lists server commands, but no "choose chat + arguments + Run" dialog was found |
| chat | run-command-dialog | arguments | lib/ui/widgets/run_command_dialog.dart:19 | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/LibraryScreen.kt:175 | Library lists server commands, but no "choose chat + arguments + Run" dialog was found |
| chat | run-command-dialog | Run | lib/ui/widgets/run_command_dialog.dart:44 | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/LibraryScreen.kt:175 | Library lists server commands, but no "choose chat + arguments + Run" dialog was found |
| chat | run-command-dialog | Cancel | lib/ui/widgets/run_command_dialog.dart | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/LibraryScreen.kt:175 | Library lists server commands, but no "choose chat + arguments + Run" dialog was found |
| servers | servers | row menu: account, connect, edit, remove | lib/ui/screens/servers_screen.dart | feature/servers/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/servers/ui/ServerSavedRows.kt:110 | edit/connect/remove; account entry via Codex account |
| servers | profile-editor | Not on the same network? | lib/ui/screens/servers_screen.dart | feature/servers/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/servers/ui/TailscaleSetupSheet.kt:1 | Tailscale setup sheet covers it; inline help link absent |
| servers | server-settings | check health | lib/ui/screens/settings/server_settings_screen.dart | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/ServerSettingsScreen.kt:1 | health check, disconnect and update exist; host restart notice not matched |
| servers | server-settings | Run as a Linux service | lib/ui/screens/settings/server_settings_screen.dart | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/ServerSettingsScreen.kt:1 | health check, disconnect and update exist; host restart notice not matched |
| servers | server-settings | Update | lib/ui/screens/settings/server_settings_screen.dart | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/ServerSettingsScreen.kt:1 | health check, disconnect and update exist; host restart notice not matched |
| servers | server-settings | Restart notice | lib/ui/screens/settings/server_settings_screen.dart | feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/ui/ThisPhoneScreen.kt:125 | phone server update exists; remote host update/restart notice not matched |
| servers | server-settings | Update managed OpenCode | lib/ui/screens/settings/server_settings_screen.dart | feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/ui/ThisPhoneScreen.kt:125 | phone server update exists; remote host update/restart notice not matched |
| servers | server-settings-upgrade-sheet | Install | lib/ui/screens/settings/server_settings_screen.dart | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/ServerSettingsScreen.kt:1 | health check, disconnect and update exist; host restart notice not matched |
| servers | server-settings-upgrade-sheet | Cancel | lib/ui/screens/settings/server_settings_screen.dart | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/ServerSettingsScreen.kt:1 | health check, disconnect and update exist; host restart notice not matched |
| usage | usage | provider filter | lib/ui/screens/usage_screen.dart | feature/usage/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/usage/ui/UsageScreen.kt:252 | model-record inspection filters; provider filter UI not matched |
| usage | usage | search | lib/ui/screens/usage_screen.dart:28 | feature/usage/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/usage/ui/UsageScreen.kt:252 | model-record inspection filters; provider filter UI not matched |
| usage | usage | clear filters | lib/ui/screens/usage_screen.dart | feature/usage/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/usage/ui/UsageScreen.kt:252 | model-record inspection filters; provider filter UI not matched |
| chat | session-relations | Copy handoff | lib/ui/screens/session_relations_screen.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/relations/ui/SessionRelationsScreen.kt:364 |  |
| chat | session-relations | pin | lib/ui/screens/session_relations_screen.dart:498 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/relations/ui/SessionRelationsScreen.kt:364 |  |
| chat | session-import | change destination | lib/ui/screens/session_import_screen.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/sessiontransfer/ui/SessionTransferSheet.kt:72 |  |
| chat | session-import-destination-sheet | choose | lib/ui/screens/session_import_screen.dart:53 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/sessiontransfer/ui/SessionTransferSheet.kt:72 |  |
| chat | run-result | open conversation | lib/ui/screens/run_result_screen.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/presentation/TurnFactsPresenter.kt:1 | run results shown as turn-facts rows; no dedicated Run results page / recorded-output sheet |
| chat | run-result | open file/command output | lib/ui/screens/run_result_screen.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/presentation/TurnFactsPresenter.kt:1 | run results shown as turn-facts rows; no dedicated Run results page / recorded-output sheet |
| chat | run-result | retry | lib/ui/screens/run_result_screen.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/presentation/TurnFactsPresenter.kt:1 | run results shown as turn-facts rows; no dedicated Run results page / recorded-output sheet |
| chat | run-result | refresh | lib/ui/screens/run_result_screen.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/presentation/TurnFactsPresenter.kt:1 | run results shown as turn-facts rows; no dedicated Run results page / recorded-output sheet |
| chat | run-result-output-sheet | expand the tool card | lib/ui/widgets/run_result_view.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/presentation/TurnFactsPresenter.kt:1 | run results shown as turn-facts rows; no dedicated Run results page / recorded-output sheet |
| settings | info-label-sheet | Got it | lib/ui/widgets/info_label.dart | feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/ui/TeachingScreen.kt:1 | glossary info sheets replaced by tips |
| settings | embedded-info-label | tap for explanation | lib/ui/widgets/info_label.dart | feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/ui/TeachingScreen.kt:1 | glossary info sheets replaced by tips |
| this-phone | builtin-server-setup | Remove Ubuntu | lib/ui/screens/builtin_server_screen.dart | feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/storage/ProjectStorageScreen.kt:36 | delete-everything flow in Manage space; no separate Remove Ubuntu button |
| this-phone | builtin-server-remove-confirm-sheet | Remove | lib/ui/screens/builtin_server_screen.dart | feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/storage/ProjectStorageScreen.kt:39 |  |
| this-phone | builtin-server-remove-confirm-sheet | Cancel | lib/ui/screens/builtin_server_screen.dart | feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/storage/ProjectStorageScreen.kt:39 |  |
| this-phone | termux-storage | Scan storage | lib/ui/screens/termux_storage_screen.dart:370 | feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxStorageScreen.kt:50 | cleans Linux storage; per-category scan/clean list simplified |
| this-phone | termux-storage | Cancel scan | lib/ui/screens/termux_storage_screen.dart | feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxStorageScreen.kt:50 | cleans Linux storage; per-category scan/clean list simplified |
| this-phone | termux-storage | Scan again | lib/ui/screens/termux_storage_screen.dart:298 | feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxStorageScreen.kt:50 | cleans Linux storage; per-category scan/clean list simplified |
| this-phone | termux-storage | Clean | lib/ui/screens/termux_storage_screen.dart:2 | feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxStorageScreen.kt:50 | cleans Linux storage; per-category scan/clean list simplified |
| settings | settings | open any of 33 doors | lib/ui/screens/settings_screen.dart | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/presentation/SettingsStructure.kt:11 | 27 rows in five groups; Plugins, Accounts-style rows folded elsewhere |
| settings | privacy-settings | toggle share session views | lib/ui/screens/settings/personal_settings_screens.dart | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/LocalDataPage.kt:55 | replaced by "Sync read state" |
| setup | guide | Advanced | lib/ui/screens/guide_screen.dart:160 | feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/SettingsWords.kt:70 |  |
| composer | legacy-drafts | search | lib/ui/screens/legacy_drafts_screen.dart | feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/KeptPromptsScreen.kt:1 | Older drafts replaced by Kept prompts |
| composer | legacy-drafts | open a draft | lib/ui/screens/legacy_drafts_screen.dart | feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/KeptPromptsScreen.kt:1 | Older drafts replaced by Kept prompts |
| composer | legacy-drafts-review-sheet | Insert into draft | lib/ui/screens/legacy_drafts_screen.dart | feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/KeptPromptsScreen.kt:1 | Older drafts replaced by Kept prompts |
| composer | legacy-drafts-review-sheet | Copy | lib/ui/screens/legacy_drafts_screen.dart | feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/KeptPromptsScreen.kt:1 | Older drafts replaced by Kept prompts |
| composer | legacy-drafts-review-sheet | Delete saved copy | lib/ui/screens/legacy_drafts_screen.dart | feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/KeptPromptsScreen.kt:1 | Older drafts replaced by Kept prompts |
| composer | legacy-drafts-delete-sheet | Delete saved copy | lib/ui/screens/legacy_drafts_screen.dart | feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/KeptPromptsScreen.kt:1 | Older drafts replaced by Kept prompts |
| composer | legacy-drafts-delete-sheet | Cancel | lib/ui/screens/legacy_drafts_screen.dart | feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/KeptPromptsScreen.kt:1 | Older drafts replaced by Kept prompts |
| shell | system | share text into a new conversation | lib/main.dart | androidApp/src/main/AndroidManifest.xml:67 | share-in, launcher shortcuts (connect, new task), deep links present; Quick Settings tiles in android/tiles; pinned-conversation s |
| shell | system | Quick Settings tile: Inbox | lib/main.dart | androidApp/src/main/AndroidManifest.xml:67 | share-in, launcher shortcuts (connect, new task), deep links present; Quick Settings tiles in android/tiles; pinned-conversation s |
| shell | system | opencode-mobile:// session and team links, QR | lib/main.dart | androidApp/src/main/AndroidManifest.xml:67 | share-in, launcher shortcuts (connect, new task), deep links present; Quick Settings tiles in android/tiles; pinned-conversation s |
| shell | system | notification taps | lib/main.dart | androidApp/src/main/AndroidManifest.xml:67 | share-in, launcher shortcuts (connect, new task), deep links present; Quick Settings tiles in android/tiles; pinned-conversation s |
| shell | root-connecting | Start and connect / Restart (phone server) | lib/ui/widgets/saved_server_connection_card.dart | feature/onboarding/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/onboarding/ui/OpeningGateScreen.kt:1 | phone server restart from Chats opening state |
| shell | root-connecting | Try again | lib/ui/widgets/saved_server_connection_card.dart:244 | feature/onboarding/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/onboarding/ui/OpeningGateScreen.kt:1 | opening gate has retry; change server / update password inline not matched |
| shell | root-connecting | Update password | lib/ui/widgets/saved_server_connection_card.dart:340 | feature/onboarding/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/onboarding/ui/OpeningGateScreen.kt:1 | opening gate has retry; change server / update password inline not matched |
| shell | root-connecting | Update token | lib/ui/widgets/saved_server_connection_card.dart:348 | feature/onboarding/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/onboarding/ui/OpeningGateScreen.kt:1 | opening gate has retry; change server / update password inline not matched |
| shell | root-connecting | Details | lib/ui/widgets/saved_server_connection_card.dart:34 | feature/onboarding/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/onboarding/ui/OpeningGateScreen.kt:1 | opening gate has retry; change server / update password inline not matched |
| conversations | activity | Background updates | lib/ui/screens/activity_screen.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/monitor/MonitorInboxScreen.kt:1 | monitor status lines present; Background updates row not matched |
| conversations | activity | Check again | lib/ui/screens/activity_screen.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/monitor/MonitorInboxScreen.kt:1 | monitor status lines present; Background updates row not matched |
| conversations | activity | Report a bug | lib/ui/screens/activity_screen.dart | ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitStateView.kt:51 |  |
| servers | embedded-connection-status-banner | Try again | lib/ui/widgets/connection_status_banner.dart:505 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatScreen.kt:144 | chat shows reconnecting/not-connected text with retry; no global banner with Details/Update password |
| servers | embedded-connection-status-banner | Details (…) | lib/ui/widgets/connection_status_banner.dart:134 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatScreen.kt:144 | chat shows reconnecting/not-connected text with retry; no global banner with Details/Update password |
| servers | embedded-connection-status-banner | Update password | lib/ui/widgets/connection_status_banner.dart:184 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatScreen.kt:144 | chat shows reconnecting/not-connected text with retry; no global banner with Details/Update password |
| servers | embedded-connection-status-banner | Update token | lib/ui/widgets/connection_status_banner.dart:183 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatScreen.kt:144 | chat shows reconnecting/not-connected text with retry; no global banner with Details/Update password |
| servers | connection-status-details-sheet | Try again | lib/ui/widgets/connection_status_banner.dart:505 | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatScreen.kt:144 | chat shows reconnecting/not-connected text with retry; no global banner with Details/Update password |
| servers | connection-status-details-sheet | Change server | lib/ui/widgets/connection_status_banner.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatScreen.kt:144 | chat shows reconnecting/not-connected text with retry; no global banner with Details/Update password |
| servers | server-switcher-sheet | switch to a saved server | lib/ui/widgets/server_switcher_sheet.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsHomeSheets.kt:85 | native lists several connections at once (Connections in this list); no single active-server switcher |
| servers | server-switcher-sheet | Connect/Open the phone server | lib/ui/widgets/server_switcher_sheet.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsHomeSheets.kt:85 | native lists several connections at once (Connections in this list); no single active-server switcher |
| servers | server-switcher-sheet | phone card menu: Disconnect, Forget saved sign-in, Manage se | lib/ui/widgets/server_switcher_sheet.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsHomeSheets.kt:85 | native lists several connections at once (Connections in this list); no single active-server switcher |
| servers | server-switcher-sheet | Add server | lib/ui/widgets/server_switcher_sheet.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsHomeSheets.kt:85 | native lists several connections at once (Connections in this list); no single active-server switcher |
| servers | server-switcher-sheet | Manage servers | lib/ui/widgets/server_switcher_sheet.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsHomeSheets.kt:85 | native lists several connections at once (Connections in this list); no single active-server switcher |
| servers | server-switcher-sheet | Disconnect | lib/ui/widgets/server_switcher_sheet.dart:61 | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsHomeSheets.kt:85 | native lists several connections at once (Connections in this list); no single active-server switcher |
| conversations | workspace | New conversation | lib/ui/screens/workspace_screen.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:1 | Work screen retired for Chats; see per-action rows |
| conversations | workspace | open a conversation | lib/ui/screens/workspace_screen.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:1 | Work screen retired for Chats; see per-action rows |
| conversations | workspace | row menu: Details, Pin, Rename, Share/Stop sharing, Archive, | lib/ui/screens/workspace_screen.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:126 | Chats home menu: Open + Archive; All list adds Pin, Rename, Select, Delete; Share/Details only in the conversation |
| conversations | workspace | All conversations | lib/ui/screens/workspace_screen.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:1 | Work screen retired for Chats; see per-action rows |
| conversations | workspace | section menu: reload, background updates | lib/ui/screens/workspace_screen.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:1 | refresh exists; background updates entry not matched |
| conversations | workspace | archived | lib/ui/screens/workspace_screen.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:1 | Work screen retired for Chats; see per-action rows |
| conversations | workspace | pull to refresh | lib/ui/screens/workspace_screen.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:1 | Work screen retired for Chats; see per-action rows |
| projects | workspace-context-sheet | Switch project | lib/ui/screens/workspace_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:1 | switch/manage project present; cloud environment choice absent |
| projects | workspace-context-sheet | Manage project | lib/ui/screens/workspace_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:1 | switch/manage project present; cloud environment choice absent |
| projects | workspace-context-sheet | pick This computer or a cloud environment | lib/ui/screens/workspace_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:1 | switch/manage project present; cloud environment choice absent |
| conversations | workspace-share-session-sheet | Share session | lib/ui/screens/workspace_screen.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/header/ui/ChatHeaderBar.kt:92 | share only from inside the conversation, not the list row |
| conversations | workspace-share-session-sheet | Cancel | lib/ui/screens/workspace_screen.dart | feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/header/ui/ChatHeaderBar.kt:92 | share only from inside the conversation, not the list row |
| conversations | global-sessions | Report a bug | lib/ui/screens/global_sessions_screen.dart | ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/state/KitStateView.kt:51 |  |
| projects | manage-project | Development services | lib/ui/screens/manage_project_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectPageScreen.kt:1 | Dev services, Worktrees, Project health present; Switch project via picker; Cloud environments absent |
| projects | manage-project | Switch project | lib/ui/screens/manage_project_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectPageScreen.kt:1 | Dev services, Worktrees, Project health present; Switch project via picker; Cloud environments absent |
| projects | manage-project | Worktrees | lib/ui/screens/manage_project_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectPageScreen.kt:1 | Dev services, Worktrees, Project health present; Switch project via picker; Cloud environments absent |
| projects | manage-project | Cloud environments | lib/ui/screens/manage_project_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectPageScreen.kt:1 | Dev services, Worktrees, Project health present; Switch project via picker; Cloud environments absent |
| projects | manage-project | Project health | lib/ui/screens/manage_project_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectPageScreen.kt:1 | Dev services, Worktrees, Project health present; Switch project via picker; Cloud environments absent |
| projects | projects | search | lib/ui/screens/projects_screen.dart:22 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectsPickerScreen.kt:1 | list/search/new folder/open folder present; Rename project absent |
| projects | projects | Create a new folder | lib/ui/screens/projects_screen.dart:352 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectsPickerScreen.kt:1 | list/search/new folder/open folder present; Rename project absent |
| projects | projects | Open a project folder | lib/ui/screens/projects_screen.dart:364 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectsPickerScreen.kt:1 | list/search/new folder/open folder present; Rename project absent |
| projects | projects | switch project | lib/ui/screens/projects_screen.dart:37 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectsPickerScreen.kt:1 | list/search/new folder/open folder present; Rename project absent |
| projects | projects | Rename | lib/ui/screens/projects_screen.dart:495 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectsPickerScreen.kt:1 | list/search/new folder/open folder present; Rename project absent |
| projects | projects | Refresh | lib/ui/screens/projects_screen.dart:27 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectsPickerScreen.kt:1 | list/search/new folder/open folder present; Rename project absent |
| projects | projects | pull to refresh | lib/ui/screens/projects_screen.dart:316 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectsPickerScreen.kt:1 | list/search/new folder/open folder present; Rename project absent |
| projects | projects | Try again | lib/ui/screens/projects_screen.dart:385 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectsPickerScreen.kt:1 | list/search/new folder/open folder present; Rename project absent |
| projects | worktrees | New worktree | lib/ui/screens/worktrees_screen.dart:225 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees | open one | lib/ui/screens/worktrees_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees | Reset | lib/ui/screens/worktrees_screen.dart:365 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees | Remove | lib/ui/screens/worktrees_screen.dart:113 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees | Refresh | lib/ui/screens/worktrees_screen.dart:458 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees | pull to refresh | lib/ui/screens/worktrees_screen.dart:458 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees | info label | lib/ui/screens/worktrees_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees | Try again | lib/ui/screens/worktrees_screen.dart:528 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees-reset-dialog | Reset worktree | lib/ui/screens/worktrees_screen.dart:367 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees-reset-dialog | Cancel | lib/ui/screens/worktrees_screen.dart:113 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees-create-dialog | name (optional) | lib/ui/screens/worktrees_screen.dart:226 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees-create-dialog | Create | lib/ui/screens/worktrees_screen.dart:229 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees-create-dialog | Cancel | lib/ui/screens/worktrees_screen.dart:113 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees-remove-dialog | type name | lib/ui/screens/worktrees_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees-remove-dialog | Remove permanently | lib/ui/screens/worktrees_screen.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| projects | worktrees-remove-dialog | Cancel | lib/ui/screens/worktrees_screen.dart:113 | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt:179 | list/create/remove worktree; Reset worktree and open-one absent |
| agents | agent-sheet | Sign out of a phone agent | lib/ui/screens/agents/agent_sheet.dart:446 | feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/ui/AgentsScreen.kt:94 | only "Reset saved sign-ins" for all agents |
| usage | usage-alerts | Per-plan alert thresholds, quiet group, reset message overri | lib/ui/screens/usage/usage_alerts_screen.dart:127 | feature/plans/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/plans/ui/PlansManagementScreen.kt:48 | Alerts page exists, per-plan overrides not matched |
| new-conversation | new-chat | New conversation project sheet: no New project, no folder br | lib/ui/screens/chats/chats_project_sheet.dart | feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/NewConversationScreen.kt:317 | the sheet lists known projects plus a typed path and Use this folder; Flutter reaches New project, Open a project folder and the f |
| new-conversation | new-chat | New project is disabled on servers that cannot make folders | lib/ui/screens/project_folder_actions.dart | feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectsPickerScreen.kt:294 | row is present but disabled unless the server is the phone (creator.available); on remote servers the owner sees no way to create  |

## UNVERIFIED

- chat / embedded-message-view / choose a ```choices option (lib/ui/screens/chat/message_view.dart): choice-block rendering not located
- chat / embedded-tool-card / show full prompt (lib/ui/widgets/tool_card.dart): not located
- chat / embedded-tool-card / retry image (lib/ui/widgets/tool_card.dart): not located
- servers / server-settings-restart-dialog / Done (lib/ui/screens/settings/server_settings_screen.dart): remote restart dialog not located
- usage / provider-quota / Clear thresholds (lib/ui/screens/provider_quota_screen.dart): not located
- usage / provider-quota / Stop using collector (lib/ui/screens/provider_quota_screen.dart): not located
- chat / session-export / redact (lib/ui/screens/session_export_screen.dart:31): redaction toggle not located
- shell / share-session-failed-banner / Retry (lib/main.dart): share-intake failure banners not located in native
- shell / session-link-server-missing-banner / Dismiss (lib/main.dart:337): share-intake failure banners not located in native
- shell / session-link-server-missing-banner / Open Servers (lib/main.dart): share-intake failure banners not located in native
- chat / chat-top-bar / Stop reading aloud (top bar) (lib/ui/screens/chat/chat_top_bar.dart:337): stop control may live in the reader slot
- chat / stuck-command / Stuck command nudge (Stop the command / Stop this step) (lib/ui/screens/chat/stuck_command.dart): native has RetryStatus; stop-command copy unused
- chat / sent-photos / Sent photos gallery in the conversation (lib/ui/screens/chat/sent_photos.dart): pictures render inline; gallery not located
- chat / chat-handoff-link / Continued in {agent} / Continued from {title} link on a hand (lib/ui/screens/chat/chat_handoff_link.dart:70): linked conversations page exists
