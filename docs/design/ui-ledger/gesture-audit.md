# Gesture audit: no feature only behind a gesture

UX plan 5.8, item 2: "No feature is reachable *only* by a gesture (long-press,
swipe); gestures are accelerators for something a menu also offers. The ledger
lists every gesture-only element so this is checkable."

This file answers, for every `type == "gesture"` element of `ledger.json`
(the pseudo-page `system` excluded), which visible, labeled control offers the
same action. Each answer was read from the callback in code, not guessed.
`gesture-audit.json` is the machine-readable twin; `test/gesture_audit_test.dart`
fails when a ledger gesture has no row, a row has no visible equivalent or
evidence, or a row names an element the ledger does not have.

Evidence is `file:line` of the visible control (or of the code that proves the
"not a gesture" reason) at the commit that wrote this file. The element ids
are the ledger's; the ledger's own `line` values may lag behind.

## How each kind was judged

- **Pull to refresh.** Allowed as pull-only (plan 5.1 / 6) when the screen
  loads on open and a failed load shows "Try again". Both were verified per
  screen: the evidence column is the load call in `initState`, the visible
  equivalent names the Try again site. `ProductErrorState` carries the literal
  "Try again" label (`lib/ui/widgets/product_states.dart`). Screens that also
  have a visible Refresh button say so.
- **Keyboard shortcuts.** Fine when `showShortcutsHelp` lists them and/or a
  visible control does the same. The help itself is a visible row in Settings.
- **Long-press, swipe, right-click, drag, drop.** Must have a visible control.
- **System back, scroll tracking, automatic callbacks, pinch-zoom.** Not
  features behind a gesture; the reason is recorded and was checked in code.
- **Plain taps the ledger files under "gesture"** (diff lines, expand bars,
  graph nodes, the model summary header). A tap on a visible, labeled target
  is the control itself.

## What changed

1. **Files rows** had their actions (Attach to prompt, Add reference, Open
   review, Copy path; for folders, Copy path) only behind a long press (touch)
   or a right click (desktop). A quiet trailing "more" button now opens the
   same sheet (`project-file-actions-<path>`, tooltip "Actions for {name}").
   The old comment argued against a second control in a compact row; the rule
   wins; a 20 px muted glyph keeps the row quiet while the button keeps the
   48dp touch floor.
2. **Keyboard shortcuts help** did not list three handled shortcuts: `Ctrl + B`
   (move running work to background), `F3 / Shift + F3` (next / previous find
   match) and the prompt-history arrows. All three already had visible
   controls; they are now listed too. `F2 / Shift + F2`, `Esc` and
   `Shift + F10 / Menu` were already listed.

The transcript message long-press needed nothing: every message already
carries a visible "Message actions" button whose `onTap` is the same callback
as `onLongPress`.

## Known gap, not fixed here

On desktop there is no pull gesture (`AppScrollBehavior` deliberately keeps
the mouse out of `dragDevices`, `lib/ui/desktop/desktop_interaction.dart`) and
no refresh shortcut. Screens whose only manual refresh is the pull (the rows
below without a "visible 'Refresh'" note) therefore refresh on desktop only by
reopening, by the automatic reload on reconnect (`dataRefreshRevision`), or by
"Try again" after a failure. Closing this needs a shell-level Refresh intent
that some thirty screens claim, which is larger than this step.

Tapping a diff line to select it is a plain tap, so it passes this audit, but
nothing on the review screen says lines are tappable. That is an empty-state /
nudge question (plan 5.8 items 3-4), not a gesture one.

## Rows

| page | element id | gesture | action | visible equivalent | evidence | change made |
|---|---|---|---|---|---|---|
| activity | `activity-pull-refresh` | pull to refresh | reload from the server | visible 'Refresh' icon button (tooltip), lib/ui/screens/activity/activity_question_form.dart:380; also automatic load on open; 'Try again' on failure (lib/ui/screens/activity_screen.dart:455) | `lib/ui/screens/activity_screen.dart:519` | none |
| active-context | `active-context-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/active_context_screen.dart:173) | `lib/ui/screens/active_context_screen.dart:45` | none |
| command-launcher-sheet | `chat-command-launcher-refresh` | refresh callback handed to the command sheet | reload server commands | the command sheet's visible 'Try again' on its error notice (lib/ui/widgets/command_sheet.dart:410) runs the same callback; also loaded automatically when the conversation opens | `lib/ui/screens/chat/chat_command_actions.dart:82` | none |
| run-result | `run-result-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/run_result_screen.dart:223) | `lib/ui/screens/run_result_screen.dart:74` | none |
| session-context | `session-context-pull-refresh` | pull to refresh | reload from the server | visible 'Refresh' button in the empty state, lib/ui/screens/session_context_screen.dart:432; also automatic load on open; 'Try again' on failure (lib/ui/screens/session_context_screen.dart:416) | `lib/ui/screens/session_context_screen.dart:223` | none |
| session-relations | `session-relations-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/session_relations_screen.dart:254) | `lib/ui/screens/session_relations_screen.dart:43` | none |
| global-sessions | `global-sessions-pull-refresh` | pull to refresh | reload from the server | visible 'Refresh' button in the empty state, lib/ui/screens/global_sessions_screen.dart:672; also automatic load on open; 'Try again' on failure (lib/ui/screens/global_sessions_screen.dart:658) | `lib/ui/screens/global_sessions_screen.dart:167` | none |
| managed-workspaces | `managed-workspaces-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/managed_workspaces_screen.dart:304) | `lib/ui/screens/managed_workspaces_screen.dart:42` | none |
| project-health | `project-health-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/project_health_screen.dart:268) | `lib/ui/screens/project_health_screen.dart:46` | none |
| projects | `projects-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/projects_screen.dart:319) | `lib/ui/screens/projects_screen.dart:40` | none |
| workspace | `workspace-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/workspace/workspace_widgets.dart:505) | `lib/ui/screens/workspace/workspace_build.dart:278` | none |
| worktrees | `worktrees-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/worktrees_screen.dart:494) | `lib/ui/screens/worktrees_screen.dart:45` | none |
| files | `files-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/files/files_body.dart:200) | `lib/ui/screens/files/files_body.dart:233` | none |
| review-workspace | `review-workspace-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/review_workspace.dart:340) | `lib/ui/screens/review_workspace.dart:453` | none |
| terminal | `terminal-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/terminal/terminal_list.dart:161) | `lib/ui/screens/terminal/terminal_list.dart:509` | none |
| usage | `usage-pull-refresh` | pull to refresh | reload usage | visible 'Refresh' icon button (key refresh-usage, tooltip usageRefresh); also loads on open (lib/ui/screens/usage_screen.dart:58) | `lib/ui/screens/usage_screen.dart:88` | none |
| development-services | `development-services-pull-refresh` | pull to refresh | re-check the development services | visible 'Refresh' icon button (tooltip servicesRefresh); also polls every 5 s (lib/ui/screens/development_services_screen.dart:89) | `lib/ui/screens/development_services_screen.dart:254` | none |
| termux-processes | `termux-processes-pull-refresh` | pull to refresh | reload the process list | visible 'Refresh' icon button (tooltip termuxProcsRefresh); also loads on open (lib/ui/screens/termux_processes_screen.dart:76) | `lib/ui/screens/termux_processes_screen.dart:281` | none |
| team-agent | `team-agent-pull` | pull to refresh | refresh the AI Team snapshot | visible 'Refresh' icon button in the app bar (tooltip teamUiRefresh); 'Try again' on failure (lib/ui/screens/team/agent_screen.dart:238) | `lib/ui/screens/team/agent_screen.dart:200` | none |
| team-home | `team-home-pull` | pull to refresh | refresh the AI Team snapshot | visible 'Refresh' icon button in the app bar (tooltip teamUiRefresh); 'Try again' on failure (lib/ui/screens/team/team_home_screen.dart:215) | `lib/ui/screens/team/team_home_screen.dart:182` | none |
| commands | `commands-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/library/commands_screen.dart:68) | `lib/ui/screens/library/commands_screen.dart:28` | none |
| integrations | `integrations-pull-refresh` | pull to refresh | reload from the server | visible 'Refresh' button in the empty providers state, lib/ui/screens/library/integrations_page.dart:208; also automatic load on open; 'Try again' on failure (lib/ui/screens/library/integrations_page.dart:326) | `lib/ui/screens/library/integrations_page.dart:28` | none |
| references | `references-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/library/references_screen.dart:81) | `lib/ui/screens/library/references_screen.dart:29` | none |
| skills | `skills-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/library/skills_screen.dart:119) | `lib/ui/screens/library/skills_screen.dart:33` | none |
| saved-permissions | `saved-permissions-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/saved_permissions_screen.dart:282) | `lib/ui/screens/saved_permissions_screen.dart:59` | none |
| tools | `tools-pull-refresh` | pull to refresh | reload from the server | automatic load on open; 'Try again' on failure (lib/ui/screens/tools_screen.dart:351) | `lib/ui/screens/tools_screen.dart:51` | none |
| global-shortcuts | `global-shortcuts-palette` | keyboard shortcut: Ctrl/Cmd+K | open the command launcher | listed in Keyboard shortcuts help ('Command launcher'); the help is itself a visible row in Settings | `lib/ui/desktop/shortcuts.dart:114` | none |
| global-shortcuts | `global-shortcuts-new-session` | keyboard shortcut: Ctrl/Cmd+N | start a new conversation | listed in Keyboard shortcuts help; Work has a visible new-conversation control (lib/ui/screens/workspace/workspace_widgets.dart:403) | `lib/ui/desktop/shortcuts.dart:115` | none |
| global-shortcuts | `global-shortcuts-settings` | keyboard shortcut: Ctrl/Cmd+, | open Settings | listed in Keyboard shortcuts help; same destination as the visible Settings tab | `lib/ui/desktop/shortcuts.dart:118` | none |
| global-shortcuts | `global-shortcuts-close-route` | keyboard shortcut: Ctrl/Cmd+W | close the current screen (maybePop) | listed in Keyboard shortcuts help; same as the visible app bar back button | `lib/ui/desktop/shortcuts.dart:120` | none |
| global-shortcuts | `global-shortcuts-help` | keyboard shortcut: Ctrl/Cmd+/ | open Keyboard shortcuts help | listed in Keyboard shortcuts help; the visible 'Keyboard shortcuts' row in Settings calls the same showShortcutsHelp (lib/ui/screens/settings_screen.dart:586) | `lib/ui/desktop/shortcuts.dart:131` | none |
| global-shortcuts | `global-shortcuts-find` | keyboard shortcut: Ctrl/Cmd+F | focus the find field of the visible surface | listed in Keyboard shortcuts help; the surfaces that claim it show a visible find control (Files search field, conversation menu 'Find') | `lib/ui/desktop/shortcuts.dart:116` | none |
| global-shortcuts | `global-shortcuts-destinations` | keyboard shortcut: Ctrl/Cmd+1..4 | select a primary tab | listed in Keyboard shortcuts help; same as tapping the visible navigation tabs | `lib/ui/desktop/shortcuts.dart:117` | none |
| global-shortcuts | `global-shortcuts-terminal` | keyboard shortcut: Ctrl/Cmd+` | open the terminal | listed in Keyboard shortcuts help; the Project tab has a visible Terminal row (lib/ui/screens/project_hub_screen.dart:308) | `lib/ui/desktop/shortcuts.dart:119` | none |
| command-palette-dialog | `command-palette-dialog-arrows` | keyboard shortcut: Arrow Up / Arrow Down | move the highlighted command | every command is a visible row that runs on tap or click; the arrows only move the highlight | `lib/ui/desktop/shortcuts.dart:534` | none |
| home-shell | `home-shell-shortcut-destinations` | keyboard shortcut: Ctrl/Cmd+1..4 | select a primary tab | listed in Keyboard shortcuts help; same _selectTab as tapping the visible navigation tabs | `lib/ui/screens/home_screen.dart:80` | none |
| home-shell | `home-shell-shortcut-find` | keyboard shortcut: Ctrl/Cmd+F | focus the Files search field on the Project tab | listed in Keyboard shortcuts help; the Files search field is always visible (lib/ui/screens/files/files_body.dart:85) | `lib/ui/screens/home_screen.dart:85` | none |
| home-shell | `home-shell-shortcut-terminal` | keyboard shortcut: Ctrl/Cmd+` | open the terminal | listed in Keyboard shortcuts help; the Project tab has a visible Terminal row (lib/ui/screens/project_hub_screen.dart:308) | `lib/ui/screens/home_screen.dart:90` | none |
| embedded-composer | `embedded-composer-submit-shortcut` | keyboard shortcut: Ctrl/Cmd+Enter (desktop: Enter) | send the prompt | visible Send button (tooltip 'Send'); also listed in Keyboard shortcuts help (lib/ui/desktop/shortcuts.dart:121) | `lib/ui/kit/chat/kit_composer.dart:511` | none |
| embedded-composer | `embedded-composer-history-keys` | keyboard shortcut: Arrow Up / Arrow Down at the caret edge | recall an earlier / later prompt | visible 'Reuse a prompt' row in the composer tools sheet (composer-tool-history); now also listed in Keyboard shortcuts help (lib/ui/desktop/shortcuts.dart:130) | `lib/ui/screens/chat/prompt_history.dart:134` | added to the shortcuts help |
| chat | `chat-prompt-history-keys` | keyboard shortcut: Arrow Up / Arrow Down in composer | recall an earlier / later prompt | visible 'Reuse a prompt' row in the composer tools sheet (composer-tool-history); now also listed in Keyboard shortcuts help (lib/ui/desktop/shortcuts.dart:130) | `lib/ui/screens/chat/prompt_history.dart:134` | added to the shortcuts help |
| embedded-transcript-find-bar | `embedded-transcript-find-bar-escape` | keyboard shortcut: Esc | close the find bar | visible Close icon button on the find bar (tooltip transcriptFindClose); Esc is listed in Keyboard shortcuts help | `lib/ui/screens/chat/transcript_find.dart:69` | none |
| chat | `chat-shortcut-command-palette` | keyboard shortcut: Ctrl+K | open the conversation command launcher | visible 'Commands' row in the composer tools sheet; listed in Keyboard shortcuts help | `lib/ui/screens/chat_screen.dart:577` | none |
| chat | `chat-shortcut-find` | keyboard shortcut: Ctrl+F | open find in the conversation | visible 'Find' in the conversation menu (SessionMenuAction.find, same _openFind, lib/ui/screens/chat/chat_session_menu.dart:168); listed in Keyboard shortcuts help | `lib/ui/screens/chat/chat_page.dart:246` | none |
| chat | `chat-shortcut-find-close` | keyboard shortcut: Esc | close find | visible Close icon button on the find bar; Esc is listed in Keyboard shortcuts help | `lib/ui/screens/chat/transcript_find.dart:69` | none |
| chat | `chat-shortcut-find-next-prev` | keyboard shortcut: F3 / Shift+F3 | next / previous match | visible 'Next match' / 'Previous match' icon buttons on the find bar; now also listed in Keyboard shortcuts help (lib/ui/desktop/shortcuts.dart:129) | `lib/ui/screens/chat/transcript_find.dart:97` | added to the shortcuts help |
| chat | `chat-shortcut-model-cycle` | keyboard shortcut: F2 / Shift+F2 | cycle recent models | visible model switch menu beside the model chip (ModelCycleButton keeps both directions as menu items); listed in Keyboard shortcuts help (lib/ui/desktop/shortcuts.dart:123) | `lib/ui/widgets/model_shortcuts.dart:65` | none |
| chat | `chat-shortcut-background-work` | keyboard shortcut: Ctrl+B | move running work to the background | visible 'Move running work to background' button above the composer (key background-running-work, same _backgroundRunningWork); now also listed in Keyboard shortcuts help (lib/ui/desktop/shortcuts.dart:128) | `lib/ui/screens/chat/chat_page.dart:273` | added to the shortcuts help |
| embedded-model-shortcuts | `embedded-model-shortcuts-ctrl-b` | keyboard shortcut: Ctrl+B | move running work to the background | visible 'Move running work to background' button above the composer (key background-running-work, same _backgroundRunningWork); now also listed in Keyboard shortcuts help (lib/ui/desktop/shortcuts.dart:128) | `lib/ui/screens/chat/chat_page.dart:273` | added to the shortcuts help |
| embedded-model-shortcuts | `embedded-model-shortcuts-f2` | keyboard shortcut: F2 | next recent model | visible model switch menu beside the model chip (ModelCycleButton keeps both directions as menu items); listed in Keyboard shortcuts help (lib/ui/desktop/shortcuts.dart:123) | `lib/ui/widgets/model_shortcuts.dart:65` | none |
| embedded-model-shortcuts | `embedded-model-shortcuts-shift-f2` | keyboard shortcut: Shift+F2 | previous recent model | visible model switch menu beside the model chip (ModelCycleButton keeps both directions as menu items); listed in Keyboard shortcuts help (lib/ui/desktop/shortcuts.dart:123) | `lib/ui/widgets/model_shortcuts.dart:65` | none |
| files | `files-search-focus-shortcut` | keyboard shortcut: Ctrl+F | focus the Files search field | the search field is always visible at the top of Files; listed in Keyboard shortcuts help | `lib/ui/screens/files_screen.dart:264` | none |
| embedded-context-menu-region | `embedded-context-menu-region-keyboard` | keyboard shortcut: Shift+F10 / Menu key | open the row context menu from the keyboard | listed in Keyboard shortcuts help ('Right click / Shift + F10 / Menu'); every region also has a visible control, see the right-click row | `lib/ui/kit/kit_context_region.dart:81` | none |
| embedded-message-view | `embedded-message-view-long-press` | long-press on a message | open the message actions sheet | visible per-message 'Message actions' button (small 'more' disc under each message, 44dp target, tooltip and semantics label) whose onTap is the same callback as onLongPress | `lib/ui/screens/chat/message_view.dart:245` | none |
| chat | `chat-message-long-press` | long-press on a transcript message | open the message actions sheet (copy, fork, read aloud, revert, delete) | visible per-message 'Message actions' button (small 'more' disc under each message, 44dp target, tooltip and semantics label) whose onTap is the same callback as onLongPress | `lib/ui/screens/chat/message_view.dart:245` | none |
| embedded-message-view | `embedded-message-view-context-menu` | right-click on a message (desktop) | open the message context menu | visible 'Message actions' button opens a sheet with the same copy / fork / revert / delete actions, same gates and handlers (_showMessageActions next to _messageContextActions) | `lib/ui/screens/chat/chat_transcript.dart:374` | none |
| chat | `chat-message-context-menu` | right-click on a transcript message (desktop) | open the message context menu | visible 'Message actions' button opens a sheet with the same copy / fork / revert / delete actions, same gates and handlers (_showMessageActions next to _messageContextActions) | `lib/ui/screens/chat/chat_transcript.dart:374` | none |
| workspace | `workspace-session-row-swipe` | swipe end-to-start on a conversation row | archive (or delete where the server cannot archive) | the row's visible trailing overflow menu has 'Archive' and 'Delete' | `lib/ui/screens/workspace/workspace_sheets.dart:260` | none |
| workspace | `workspace-session-context-menu` | right-click on a conversation row (desktop) | open the row context menu | same entries as the row's visible trailing overflow menu | `lib/ui/screens/workspace/workspace_widgets.dart:223` | none |
| files | `files-row-long-press` | long-press on a file row | open the file row actions sheet (Open, Attach to prompt, Add reference, Open review, Copy path) | NEW visible trailing 'more' button on every row (tooltip 'Actions for {name}') opens the same sheet | `lib/ui/screens/files/files_body.dart:343` | added the trailing actions button |
| files | `files-row-context-menu` | right-click on a file row (desktop) | open the file row context menu | NEW visible trailing 'more' button on every row opens a sheet built from the same _fileRowActions list | `lib/ui/screens/files/files_body.dart:343` | added the trailing actions button |
| terminal | `terminal-process-context-menu` | right-click on a terminal row (desktop) | Open / Rename / Stop or Remove | the row's visible trailing overflow menu (tooltip 'Terminal actions') has Rename and Stop/Remove; Open is the row tap | `lib/ui/screens/terminal/terminal_list.dart:401` | none |
| embedded-context-menu-region | `embedded-context-menu-region-secondary-tap` | right-click (desktop) | open the context menu of a row | every ContextMenuRegion wraps a row with a visible door to the same actions: the message 'Message actions' button; the trailing overflow menus of Work, all-conversations, worktree, environment and terminal rows; the new trailing button on Files rows | `lib/ui/kit/kit_context_region.dart:103` | none |
| embedded-desktop-file-drop-target | `embedded-desktop-file-drop-target-drop` | drop files onto the composer (desktop) | attach the dropped files | visible 'Attach file' row in the composer tools sheet | `lib/ui/desktop/file_drop.dart:96` | none |
| chat | `chat-composer-drop-target` | drop files onto the composer (desktop) | attach the dropped files | visible 'Attach file' row in the composer tools sheet | `lib/ui/screens/chat/chat_top_bar.dart:86` | none |
| chat | `chat-composer-content-inserted` | keyboard (IME) image/GIF insertion or image paste | attach the inserted image | visible 'Attach file' and 'Photo library' rows in the composer tools sheet attach the same kind of content | `lib/ui/screens/chat/chat_composer_region.dart:415` | none |
| files | `files-split-handle` | drag the tree/preview divider (desktop) | resize the tree pane | view-only layout manipulation, no action; the divider is visible and shows a resize cursor | `lib/ui/screens/files/files_body.dart:9` | none |
| home-shell | `home-shell-back-gesture` | system back | return to Work, then double-back exit | system navigation; the visible navigation tabs select Work the same way | `lib/ui/screens/home_screen.dart:322` | none |
| chat | `chat-back-leave` | system back / app bar back | leave the conversation (offers to keep an unsent draft) | system navigation; the app bar back button does the same (same PopScope handler) | `lib/ui/screens/chat/chat_page.dart:171` | none |
| session-note | `session-note-back` | back with unsaved changes | ask before discarding the note | system navigation; the app bar back button does the same (same PopScope handler) | `lib/ui/screens/session_note_screen.dart:213` | none |
| isolated-task-sheet | `isolated-task-sheet-back` | system back | close the sheet and stop waiting | visible close / cancel buttons call the same _close | `lib/ui/screens/isolated_task_sheet.dart:191` | none |
| files | `files-system-back` | system back | go up a folder, clear the search, or close the keyboard | visible breadcrumbs (root and each parent folder) navigate up; the search field's visible clear button (lib/ui/screens/files/files_body.dart:90) clears the search | `lib/ui/screens/files_screen.dart:446` | none |
| project-hub | `project-hub-system-back` | system back | leave Files back to the Project hub | visible back icon button on the Files header (project-hub-files-back) | `lib/ui/screens/project_hub_screen.dart:234` | none |
| external-task | `external-task-back` | system back | leave an unsaved external task draft | system navigation; the app bar back button does the same (same PopScope handler) | `lib/ui/screens/external_agents_screen.dart:801` | none |
| chat | `chat-transcript-scroll` | scroll the transcript | scroll tracking: pause follow, load older messages | not a feature behind a gesture: scroll tracking; the visible 'Jump to latest' button returns to the end | `lib/ui/screens/chat/chat_body.dart:92` | none |
| global-sessions | `global-sessions-scroll-load-more` | scroll near the end | load the next page | not a user gesture: automatic pagination; on failure a visible 'Try again' button loads the page | `lib/ui/screens/global_sessions_screen.dart:766` | none |
| shell-output | `shell-output-scroll` | drag the output up | stop following the output | visible Follow switch toggles the same _follow flag | `lib/ui/screens/running_work_sheet.dart:775` | none |
| chat-watching-live | `chat-watching-live-scroll` | drag the output away from the end | stop following the output | not a feature behind a gesture: scroll tracking pauses follow-latest while the person reads; the visible Jump to latest pill (chat-watching-live-jump) resumes it, as in the chat | `lib/ui/screens/chat/team_watch_live.dart:278` | none |
| review-workspace | `review-workspace-diff-line` | tap a diff line | select the line (a second tap makes a range) | Tap a numbered diff line; the visible KitDiffView selection bar offers Clear, Copy, Add to prompt and Comment. | `lib/ui/kit/diff/kit_diff_rows.dart:270` | none |
| review-workspace | `review-workspace-hunk-header` | tap a hunk header | select the whole hunk | Tap the labelled hunk header; the visible KitDiffView selection bar offers Clear, Copy, Add to prompt and Comment. | `lib/ui/kit/diff/kit_diff_rows.dart:94` | none |
| review-workspace | `review-workspace-expand-above` | tap "+N lines" | reveal hidden context above | is itself a visible, labeled tap target ('+{count} lines') | `lib/ui/kit/diff/kit_diff_rows.dart:459` | none |
| review-workspace | `review-workspace-expand-below` | tap "Expand (N more)" | reveal hidden context below | is itself a visible, labeled button (semantics 'Expand. N unchanged lines hidden below') | `lib/ui/kit/diff/kit_diff_rows.dart:446` | none |
| diff-view | `diff-view-collapse-context` | tap "Hide revealed context" | collapse revealed context | is itself a visible, labeled tap target | `lib/ui/kit/diff/kit_diff_rows.dart:430` | none |
| diff-view | `diff-view-expand-down` | tap "Show next N lines" | reveal the next lines of a hidden gap | is itself a visible, labeled tap target | `lib/ui/kit/diff/kit_diff_rows.dart:446` | none |
| diff-view | `diff-view-expand-up` | tap "Show N previous lines" | reveal the previous lines of a hidden gap | is itself a visible, labeled tap target | `lib/ui/kit/diff/kit_diff_rows.dart:459` | none |
| embedded-file-preview-body | `embedded-file-preview-body-image-zoom` | pinch / pan an image | zoom the preview | view-only manipulation, no action | `lib/ui/widgets/file_preview.dart:396` | none |
| embedded-file-preview-body | `embedded-file-preview-body-pdf-zoom` | pinch / pan a PDF page | zoom the page | view-only manipulation, no action | `lib/ui/widgets/pdf_file_preview.dart:200` | none |
| embedded-work-graph | `embedded-work-graph-node` | tap a graph node | open the work sheet | plain tap on a visible, titled KitWorkGraph node or row; the same onOpen opens the work sheet | `lib/ui/kit/kit_work_graph.dart:965` | none |
| pairing-scanner | `pairing-scanner-detect` | point the camera at a pairing QR | submit the scanned pairing code | not a user gesture: automatic camera detection, announced by the visible on-screen instruction; when the camera cannot be used the screen offers a visible 'Paste instead' button (lib/ui/screens/pairing_scanner_screen.dart:200) | `lib/ui/screens/pairing_scanner_screen.dart:243` | none |
| commands | `commands-row-open-chat` | after the run dialog returns a conversation | open the new conversation | not a user gesture: automatic follow-up navigation after the visible Run dialog | `lib/ui/screens/library/commands_screen.dart:122` | none |
| integrations | `integrations-mcp-loopback-callback` | automatic OAuth loopback callback | finish MCP sign-in | not a user gesture: automatic callback from the browser after the visible sign-in button | `lib/ui/screens/library/integrations_mcp.dart:181` | none |
| tools | `tools-model-summary` | tap the model summary header | open the model picker | is itself a visible, labeled tap target (model name and provider/model) | `lib/ui/screens/tools_screen.dart:212` | none |
| team-board | `team-board-card-long-press` | long press on a card | open the move sheet | visible '⋯' action on the same card (team-board-card-more-<id>, label 'Move or change') when moves exist; the card's own tap opens the conversation directly (team-board-card) | `lib/ui/widgets/team_board_card.dart:227` | none |
| mcp-catalog | `mcp-catalog-pull-refresh` | pull to refresh | read this server's MCP list and the registry again | automatic load on open; 'Try again' when the registry or this server's list could not be read (lib/ui/screens/mcp_catalog_screen.dart:539); a search reads the registry again | `lib/ui/screens/mcp_catalog_screen.dart:156` | none |
