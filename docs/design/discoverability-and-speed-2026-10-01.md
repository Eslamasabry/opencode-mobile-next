# Discoverability, unified status and speed (2026-10-01)

Proposal only. No product code changed. Input: a Galaxy S23 power user who now prefers this app over AndCode and AnyClaw: "make the existing features easier to discover and more unified … a clear tool hub, stronger project overview, smoother navigation, and more visible agent/tool status". He also saw occasional long loading when opening Settings, loading chats and sending messages. Owner: "let's also fine tune existing features."

Rethink method (owner rule): for each page, question its need, the situations, missing information and actions, the flow; then UI fixes. Standards: `docs/design/design-standard.md`, `docs/ux-system/kit-v2.md`, AGENTS.md (kit only, UI talks to the domain gateway only, gate on `ServerCapabilities`).

Samples (412x915 dark and light, 1280x800 tablet): contact sheets `scratchpad/hub/sheets/` (hub, running now, navigation, light overview); the test that drew them was temporary and is deleted.

## 1. Inventory: what exists and where it is reached

Taps are counted from Work home (the tab bar visible, no chat open) and from inside a chat. "Hard" = 3 or more taps, hidden in a menu, or only via the `/` command sheet. The chat's `/` sheet is `lib/ui/screens/chat_screen.dart` `_builtinChatCommands` (about 40 actions) opened from the composer tools button or by typing `/`; the conversation menu is `lib/ui/widgets/session_menu.dart` (Go to: Changes, Timeline, Find, Subagents, Details; Do: Share, Compact, Fork, Rename, Continue on computer or phone).

| Feature | Where today | From Work | From chat | Hard |
|---|---|---|---|---|
| Open a conversation | Work list (`workspace_screen.dart`) | 1 | back, 1 | |
| New conversation | Work button | 1 | back, 1 or `/new` (2) | |
| All, archived, find conversations | Work, scroll to "All conversations" row, `global_sessions_screen.dart` | 1 (after scroll) | `/` Sessions (3) | scroll |
| Import a conversation | All conversations, overflow, Import | 3 | | yes |
| Inbox (needs you) | Inbox tab, `activity_screen.dart` | 1 | back, 1 | |
| While you were away | Work return brief and Inbox | 1 | | |
| Files, file viewer | Project tab, Files, file (`project_hub_screen.dart`, `files_screen.dart`) | 2, viewer 3 | `/` Files (3) | chat: yes |
| Edit a file | none (viewer, save-as only) | n/a | n/a | missing |
| Changes (working tree review) | Project, Changes | 2 | `/` (3) | |
| Changes (this conversation's diff) | chat menu, Changes (`review_workspace.dart`) | n/a | 2 | two doors, same name |
| Branch and git state | Project, Project health (2) shows branch; Worktrees (2) | 2 | `/` Move or Health (3) | branch name only inside Health |
| Commit, push, pull | no surface (agent or terminal) | n/a | n/a | missing |
| Worktrees | Project, Worktrees | 2 | `/` (3) | chat: yes |
| Cloud environments | Project, Cloud environments | 2 | `/` (3) | chat: yes |
| Dev services | Project, Services | 2 | not in chat | chat: yes |
| Terminal (server) | Project, Terminal (`terminal_screen.dart`) | 2 | `/` Terminal (3) | chat: yes |
| Shell command in chat | `/` Run shell command | | 3 | yes |
| Local terminal (this phone) | Terminal page "Use phone" or This phone | 3 to 4 | 4+ | yes |
| Phone processes | Settings, This phone, Running now (`termux_processes_screen.dart`) | 3 | | yes |
| Running work (agents, commands) | chat top bar chip, only while something runs (`running_work_sheet.dart`) | none | 1 (conditional) | no door from Work |
| Subagents | chat menu, Subagents | | 2 | |
| AI Team | Work strip (when on), Settings, AI Team | 1 to 2 | | |
| Slash and server commands | composer tools, Commands; Settings, Tools, Commands | 3 | 2 | |
| MCP servers | Settings, Tools, MCP, server | 4 | `/` (3) | yes |
| Skills, references, tool list | Settings, Tools (`tools_hub_screen.dart`) | 3 to 4 | `/` (3) | yes |
| Providers and accounts | Settings, Providers | 2 | `/` Connect provider (3) | |
| Model, mode, thinking | composer model chip; Settings, Model | 2 | 1 | |
| Usage, quota | Settings, Usage, section (`usage_hub_screen.dart`) | 3 | none | yes |
| This phone server, keep running | Settings, This phone (`this_phone_screen.dart`) | 2 | | |
| Servers, switch server | server pill (1), Servers (2) | 2 | 2 | |
| Voice | Settings, Voice; composer mic | 2 | 1 | |
| Saved prompts, stash, history | composer tools only | n/a | 2 | not in Settings |
| Export, copy transcript | `/` Export only (`session_export_screen.dart`) | n/a | 3 | yes |
| Timeline, Find, Details (context, tokens) | chat menu | n/a | 2 | |
| Fork, compact, rename, continue elsewhere | chat menu | n/a | 2 | |
| Undo, redo (staged revert), notes, approvals, todos, reload | `/` only | n/a | 3 | yes |
| Saved permissions | Settings, Background and automation, Saved permissions | 4 | | yes |
| Search | shell search button opens the command palette (commands and settings search) | 1 | menu Find (2) | no single search across conversations, files and settings |
| Notifications, keep running, appearance, privacy | Settings (2) | 2 | | |
| Diagnostics, report, capabilities, guide | Settings, Help; `/` Diagnostics | 2 | 3 | |

Findings:
1. Project tools are reached by 2 taps from Work but 3 from a chat, and the chat is where people are. The conversation menu has no Files, Terminal or Health.
2. Many chat actions exist only in the `/` sheet (export, undo, notes, approvals, todos, shell). A person who never types `/` never finds them.
3. Tools, MCP, commands and skills are 3 to 4 taps deep in Settings, with no link from Project or chat.
4. Two "Changes" doors (project working tree, conversation diff) look the same and are different.
5. Usage and quota is a Settings sub-page with no live number anywhere else.

## 2. Status visibility today

| Thing | Where it shows | Gap |
|---|---|---|
| Server health | server pill with status word on every tab (`home_shell` `_serverStatus`), `AppConnectionStatusScope` banner, Settings This server row | fine |
| Agent working | Work row mark (`busySessions`), transcript work line, chat chip "N work" | not on dock, not on Project, not from other tabs |
| Command or tool running | chat only (running work sheet, tool card) | no view outside the chat that started it |
| Terminal alive | Project, Terminal row "N running", read once, not polled (`project_hub_screen.dart` `_readStatus`) | stale after the tab opens; local terminal and phone processes not counted |
| AI Team lanes | Work strip and Settings, AI Team value | only Work and Settings |
| Other servers | Work, "On your other servers" (scroll end), Inbox badge | far down the page |
| Dev services | Project, Services page | no live line on the hub |
| Git state (branch, changed files) | Project health page; Changes row after one lazy read | not on the project header |
| Background service limit, thermal, app stopped | one status line (design standard section 5) | fine |

Missing: one place that lists every running thing, any running count on the dock, and a project header with branch and changes.

## 3. Proposal

### 3a. Project hub (the Project tab grows up)

- Header: project name, chips for branch, changed count, ahead/behind (`KitChip`), one overflow menu (switch project, copy path).
- "Running now" line directly under it, only when something runs (3b).
- Tool tiles in a grid (2 columns phone, 3 on tablet) each with one live line and one mark: Changes (count badge), Files, Terminal (running mark), Agents (working and needs-you), Branches and environments (worktrees + cloud environments), Services (up or down), Commands and tools (counts of commands and MCP servers), Health. Tiles for capabilities the server lacks are dimmed with the reason (`KitRow.unavailable` rule), never absent while the tab exists.
- Recent activity: three rows, the same marks (`KitTaskMark`).
- Tablet and PC: two panes. Left: header and grid. Right: Running now and Recent activity as a standing column.
- Live lines refresh on the same triggers as today plus the running-now source; no timer polling.

Kit parts: existing `KitChip`, `KitChipWrap`, `KitStatusLine`, `KitRowGroup`, `KitRow`, `KitTaskMark`, `KitSurface.tile`, `KitTappable`, `KitText`, `KitNav`. New (two or more pages need them, per kit-v2 entry rule): `KitToolTile` (hub and chat Tools sheet) and `KitProjectHeader` (hub and Work header). The sample builds the tile from `KitTappable` + `KitSurface.tile`; it moves into the kit before the screen uses it (G16).

### 3b. Running now (global)

- One aggregate, `RunningNow`, in `lib/state/`: conversations busy, managed shells, terminals, AI Team lanes, other servers (the profile monitor), each with kind, title, start time, target route.
- Phone: a `KitStatusLine` of kind `work` under the shell bar on every tab: "5 running, 1 needs you" with "See all". Priority under connection, app stopped and heat (design standard says one line per screen). Needs-you count already lives on the Inbox badge, so the line only counts when nothing more urgent shows.
- "See all" opens a `KitSheet` ("Running now"): groups Needs you, Working now, each a `KitRow` with a `KitTaskMark` and elapsed time; a tap jumps to the conversation, terminal, lane or server. The chat's existing per-conversation sheet (`running_work_sheet.dart`) becomes this sheet filtered to "This conversation".
- Tablet: the same list as the hub's right column and in the sidebar under the server pill.
- Absent when nothing runs, so resting screens stay still (motion rule).

### 3c. Navigation

- Chat top bar gets a Tools action (and the title tap) that opens "Project tools" (`KitSheet`, same six rows as the hub plus "Switch conversation", running first). From chat, Files, Terminal, Changes go from 3 taps to 2.
- Conversation menu gains Export and Undo/Redo under Do; the `/` sheet stays as the typed shortcut, not the only door.
- One Changes page with two tabs: "This conversation" and "Whole project" (both already use `ReviewWorkspace`).
- Back: Project sub-pages, chat and Settings sub-pages all return to the page that opened them; the root back-exit rule stays (`home_screen.dart` `_onRootPop`).
- Quick switcher: server pill stays; project title in the shell bar opens the same switcher on phone (today only on the PC sidebar). Settings search and command palette results include tools and hub tiles.

### 3d. Remove or merge

- Merge Settings, Tools into the hub tile "Commands and tools" (Settings keeps the page, the hub deep-links to it; one door in each place instead of three).
- Merge running work sheet, Termux processes and hub terminal counts into Running now.
- Merge Worktrees and Cloud environments into one tile "Branches and environments".
- Drop the "Search files" row from the hub (Files has its own field; global search covers it).
- Fold Project health into the header chips; keep the page as "Git and health".
- Do not add: file editor, Git commit/push UI. They are features, not discoverability; record them as backlog.

### Samples

Today: the real Project tab (fixture, offline pill because the fixture has no stream). Proposed: hub phone dark and light, hub tablet dark and light, Running now strip and sheet, chat Project tools sheet. Files are in the contact sheet; the first column compares today and proposed.

## 4. Speed (from code; no device runs)

Measured evidence in the repo is debug-widget-test only (`docs/qa/codex-perf-2026-09-28/`, `test/perf_*_test.dart` assert counters, not phone time). PerfTrace spans exist (`lib/diagnostics/perf_trace.dart`: `app.*`, `connect.health`, `catalog.load`, `sessions.refresh`, `lifecycle.*`, `session.create`) but none around Settings health, chat load, send, or `prepareActionTransport`.

Steps:
- Settings first open (`settings_screen.dart`): lazy tab build; `initState` calls `_checkHealth` (line 144) which awaits `prepareActionTransport()` then `api.health()` and shows the loading bar; every build runs `searchIndex()` and `_groups` runs `allSearchEntries()` again (two 81-entry index builds); a listener rebuilds on every controller notification, even while the tab is hidden.
- Open a chat (`chat_screen.dart` `initState`): four fire-and-forget loads (history tail, commands behind `prepareActionRepository`, background support with 8 s timeout, running shells); the tail is one 100-message request, shared with the tap prefetch, with a cached excerpt painting first; each build recomputes `_timelineDisplayParts(_messages)` and scans messages; the whole screen rebuilds on each controller notification.
- Send (`_send`): optional command refresh, then `await prepareActionTransport()` before the optimistic bubble appears, then draft persist, `waitForSessionSelection`, `promptAsync` POST. Every notification also runs widget-snapshot JSON and launch-surface publishing.

Ranked suspects:

| # | Suspect | Evidence | Fix | Expected effect |
|---|---|---|---|---|
| 1 | `prepareActionTransport` blocks Send (bubble appears only after it) and Settings health after wake or background | `connection.dart:7966-7993` waits for lifecycle resume, health or reconnect, up to the 8 s connect timeout (`api2/transport.dart`); `chat_screen.dart:2919` precedes the bubble at 2965 | Add the optimistic bubble and "Sending" first, then await the transport; Settings shows cached health and refreshes without a blocking bar | After a wake the bubble is instant; the wait moves behind a visible state. Inference, no phone timing yet |
| 2 | Whole-chat rebuild on every controller notification and per-build derivation | `chat_screen.dart:5119`, `:8139`; `docs/qa/codex-perf-2026-09-28/chat.md` items 2 and 3 | Per-turn derived cache and a per-session `ValueListenable` | Ten spaced deltas currently cost ten full screen builds; target one narrow rebuild |
| 3 | Settings rebuilds two search indexes per notification, also while hidden | `settings_screen.dart:136`, `:433`, `:508` | Memoize by locale and scope signature; skip `setState` when the tab is offstage | Removes constant background UI-thread work; first-open cost drops by one index build |

Also, lower: very long single replies (`perf_chat_test`: 15,988 ms for 5,000 fragments versus 1,033 ms for 1,000, debug pumps); the v1 catalog download can compete with chat requests on a loaded server (already moved last); notification side effects (snapshot JSON, prefs write) can be debounced about 1 s.

Already mitigated (do not redo): lazy tab mount, text delta coalescing at 50 ms with no controller notification, linear text merge, Markdown settled-block cache, shared and prefetched tail read, catalog loaded last, virtualized rows.

First step before fixing: add PerfTrace spans `settings.health`, `chat.load`, `chat.send_to_bubble`, `transport.prepare` so the owner's Performance report (Settings, Help) shows which suspect is real.

## 5. Slice plan (parallel agents)

Freeze first: `RunningNowItem {kind, id, title, since, tone, route}`, `ProjectOverview {branch, changedCount, ahead, terminals, services, agents}`, the `KitToolTile` and `KitProjectHeader` signatures. One feature per worktree and branch (AGENTS.md workflow).

| Slice | Owns (write set) | Reads | Depends on | Focused checks |
|---|---|---|---|---|
| A. Kit parts | `lib/ui/kit/kit_tool_tile.dart`, `kit_project_header.dart`, `kit.dart` export, kit gallery and goldens | kit tokens | none | kit golden tests, `design_standard_test`, `kit_ratchet_test` |
| B. Project hub | `lib/ui/screens/project_hub_screen.dart`, `lib/state/project_overview.dart` (new), goldens `project_hub_*` | gateway, `ServerCapabilities` | A | `test/project_hub_test.dart`, new overview test, goldens phone and 1280x800 |
| C. Running now | `lib/state/running_now.dart` (new), `lib/ui/screens/running_now_sheet.dart` (new, replaces `running_work_sheet.dart` call sites outside chat), `lib/ui/screens/home_screen.dart` (status slot, owned) | connection busy sets, shell output, profile monitor, team glance | A | aggregate unit test, sheet golden, home shell test |
| D. Navigation | `lib/ui/screens/chat_screen.dart` and every `chat/*.dart` (one owner), `session_menu.dart`, search index entries | slice B tool list | A, B | chat tests, menu test, `settings_hub_test` |
| E. Speed | `lib/state/connection.dart` (owner), `settings_screen.dart`, `perf_trace.dart`; chat send ordering coordinated with D (same agent after D) | perf docs | none for connection and Settings | `perf_chat_test`, new send-bubble-first test, Settings rebuild-count test |
| F. Copy, l10n, ledger | `lib/l10n/*.arb` and generated output (one owner, once copy settles), `docs/localization-todo.md`, verification record `docs/qa/<feature>-<date>/README.md` | all | after A to E | `ui_glossary_test`, ARB parity |

Order: A and E (connection, Settings) start together; B and C after A; D after B; F last, then the serial full suite at one stable boundary.

Acceptance:
- Hub shows branch, changed count, running marks and live lines; tiles for missing capabilities are dimmed with a reason; no raw paths or errors.
- From a chat, Files, Terminal and Changes are 2 taps; from Work, any running thing is reachable in 2 taps.
- Running now lists every kind and each row jumps to its target; absent when idle; no new loop animation.
- Send shows the user's bubble before any network wait; Settings opens with no blocking bar when health was read in the last minute.
- Phone and tablet goldens, dark and light; Arabic parity; a11y labels and 48 dp targets; kit-only (G16 count does not rise).
- Perf spans present; owner's Performance report shows `chat.send_to_bubble` under 100 ms at wake.

Owner decisions are listed in the hand-off message.
