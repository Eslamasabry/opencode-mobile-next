# Chats-first shell (2026-10-03)

Branch `feat/chats-first-shell` (on `feat/chats-first-state`). Owner decision:
"chats first, project as a setting" (mockup UxsoCXcQfjeVXxpEoWJb7Y).

## What changed

- Tabs: Work, Inbox, Project, Settings became **Chats, Files, Settings**
  (`lib/ui/screens/home_screen.dart`). Chats carries the single pending badge.
  `ChatsHomeScreen` and `showNewChat` are STUBS here; the home branch replaces
  them. `ChatsHomeScreen.initialFilter` (`ChatFeedFilter?`) is the contract the
  shell uses.
- Files tab is the old Project tab (`ProjectHub`): title "Files", a chip naming
  the project (tap: Open a project / switch), the project follows
  `lastUsedProjectDirectory`, a temporary folder is never shown as a project.
- Work screen removed (`workspace_screen.dart`, `workspace/*`,
  `other_projects_panel.dart`) with its tests and goldens.
- Chat header: a small project chip under the title; its menu offers Files,
  Terminal and Changes (each only when the server has the capability). Opening
  a chat records its project as last used.
- Every former Inbox door opens Chats on a filter through `OpenChatsIntent`
  (`dispatchAtShellRoot`): Quick Settings tile, sessions widget and app
  shortcut (wire id `activity`, native side unchanged) = Needs you; AI Team gate
  link without a project page = Needs you; AI Team run link without a plugin =
  Running; question notification = the question card in its chat.
- Keyboard: Ctrl/Cmd+1..3 = Chats, Files, Settings (4 is gone).

## Evidence

- Contact sheet: `shell-sheet.png` (scratchpad `chats-first/`).
- New tests: `test/chat_project_chip_test.dart`, groups in
  `test/home_navigation_test.dart` (tabs, deep links, Files tab, temp folders).
- New goldens: `shell_files_tab_*`, `chat_project_menu_*`,
  `shell_home_shell_files_*`.

## Known gaps

- `ActivityScreen` (the old Inbox) is kept but nothing opens it: its "While you
  were away" digest and automatic-acts log have no home in Chats yet.
- Widgets only the Work tab used and now unreferenced: `other_servers_panel`,
  `team_project_strip`, `phone_team_setup_strip`, `team_task_row`,
  `new_conversation_sheet`, parts of `work_status_line`,
  `work_row_status_controller`. Left for the merge, since the home branch may
  reuse them.
