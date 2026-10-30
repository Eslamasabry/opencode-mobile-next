# Files: just the files, then a reader (vision slice, 2026-10-20)

Branch `feat/vision-files` (from int `a173bd8bf`). Concepts:
`/home/eslam/Storage2/launch-footage/screen-concepts/b3-files/concept-c.png` (the list) and
`concept-b.png` (the reader). Pick and lists: the vision page "Files" ("C list + B reader"),
`LEARNINGS.md` beside it, and `docs/design/vision-decisions-2026-10-20.md`. How the page is
reached since the bottom bar went: `docs/qa/no-bottom-nav-2026-10-20/README.md`.

Finish line: the project's Files page is the project's files, one plain row each, with the
uncommitted changes marked on their names; a file opens in a reader with line numbers and one
"Ask about this file" that puts it in the conversation's composer. Every tool the page had stays
one or two taps away. Non-goal: the diff review, the terminal, the project chooser, the chat
library and the aurora themselves.

## 1. Data audit (written before building)

The question per element: what in the app or on a server makes it true?

| Element in the pick | Source | Verdict |
|---|---|---|
| The page itself | `ServerCapabilities.fileBrowsing` (OpenCode 2: `GET /api/fs/list`; Paseo phone agents: the helper's file listing; Codex: false). `ProjectHub.isAvailable` already gates the chat's project chip, Search "Files" and the + sheet on it. | **Real.** Gated on the flag, never on the flavor. A server without it keeps today's chip menu. |
| "Files" large title, project name in quiet text under it | The page's own name (`shellTabFiles`); the project is the open folder's last segment (`controller.directory`), the same name the chat's chip shows. | **Real.** Under the title, one quiet line. Tapping it opens the existing "Open a project" chooser where `projectManagement` is on (OpenCode 2), and is plain text where it is off (Paseo). Inside a folder the title is the folder's name and the quiet line says where it is ("neon-breaker / src"). |
| A plain list of file and folder names, one Phosphor icon each | `listFiles(path)` entries: name, path, `isDir`. Icons from `AppIconography` (Phosphor): folder and file. | **Real.** One row per entry, no card per row, hairlines between. Dot files stay hidden unless Show hidden files is on (today's behaviour). |
| The changed file in mint on its own name, plus a small mark | `listFileStatuses()` = Git's uncommitted changes (OpenCode 2 `GET /api/vcs/status`; Paseo: the checkout diff). Status is `added`, `modified` or `deleted`, per file. Folders: any changed file inside. | **Real, worded honestly.** Git cannot say *who* changed a file, so the mark says "Changed" / "New" / "Deleted" (in the row's semantics) and never "changed by the agent". Colour is not alone: a small mint dot follows the name. A folder with changes inside gets the same mark. Where the server cannot report statuses (no Git, an old server) nothing is marked and the page's one muted status line says the marks are unavailable (today's wording; no amber, no error colour). Deleted files stay listed where they were, dimmed, and open the review. |
| "4 min ago · changed by the agent" (concept A) | No modified time in `/api/fs/list`; no author for a change | **Left out.** No source for the time, and "by the agent" would be a guess. |
| Dates on every row, breadcrumb, always-visible search, tree card, New file button | — | **Removed by the pick.** The agent creates files; no server call to create one is offered anyway. |
| Search | `findFile(query)` (OpenCode 2 `GET /api/fs/find`; Paseo: the helper's find). Text and symbol search: `textSearch` / `workspaceSymbols`, false on OpenCode 2 and Paseo today. | **Real, quiet.** A search icon in the top bar opens the field under the title; Back or clearing it returns to the list. "Search inside files" and Symbols appear only where their flags are on. Not pull-down: the list's pull gesture is already Refresh. |
| Today's tools: Changes, Terminal, Project health, Worktrees, Development services, Cloud environments, Scheduled prompts, Switch project, Copy folder path | `ProjectHub.toolsFor(capabilities)` (fileBrowsing, terminal, projectManagement, worktreeBrowsing, developmentServices, managedWorkspaces), `canSchedulePrompts` | **Real, kept and quiet.** Search is an icon in the bar. Terminal (when `terminal` is on) is the second icon where the bar has room (tablet, PC) and the first ⋯ item on a phone, the bar's own rule for one icon on compact. The rest are in the page's ⋯, each gated as today: Changes first, named with its count ("Changes · 2 files"; the changed names on the list are the visible sign), then Project health, Worktrees, Development services, Cloud environments, Scheduled prompts, Switch project, Copy folder path, and the list's own Show hidden files and order. A server without file browsing keeps today's rows. |
| Reader: comfortable mono text, line numbers, no clipped lines | `fileContent(path)` (OpenCode 2 `GET /api/fs/read/<path>`), drawn by `KitViewer` → `KitCodeBlock.fill` (line numbers for code, wrap or sideways scroll). | **Real.** |
| Wrap as a quiet toggle | `ReaderPreferences.wrapCode` (saved per app) | **Real.** A neutral icon toggle in the reader's header, not a mint button. |
| "Ask about this file" (primary, solid mint, at the bottom) | From a conversation: `ReviewHandoffSession.stage(ReviewReference.file)` (UX-103), the composer's reference tray. Without a conversation (Files from Search on the home screen): `showNewChat(directory:, initialText: '@path ')`, the start screen's own prefill. | **Real.** In a conversation it adds the file (and the line, when opened at one) to that composer and returns there. From home it opens a new conversation in this project with the file named in the field, unsent. |
| "Copy" beside it (outlined) | `KitViewerContent.copyText`: the text the reader loaded | **Real** for text and code. Left out for pictures, PDFs and binary files (nothing to copy as text). |
| Path chip "src/game.js" (concept B) | The path | **Adapted.** The parent folder is the reader's quiet second line already; a chip with the full path repeats the name. Copy path stays in ⋯. |
| Tree above the reader (concept B) | — | **Removed by the pick.** |
| Syntax colours | `KitCodeBlock` highlighting | **Real.** The kit's existing palette (no red). |
| Bottom dock (Work, Inbox, Project, Settings) | — | **Not on phones any more** (no-bottom-nav, 2026-10-20). The page has Back. |
| Aurora behind the page | `KitLiveBackground` (another agent's) | **Not this screen.** Untouched; captures show what it paints. |

Capability and flavor: nothing on the page reads the flavor. OpenCode 2's protocol notes
(`docs/opencode2-protocol-notes.md` §11) confirm list, read, find and VCS status. Paseo phone
agents (Claude Code on this phone) list, read, find and report the checkout diff; they have no
project chooser (`projectManagement: false`), so the project line is plain text there. Codex has
no file browsing: no Files page.

## 2. What changed

Kit (opt-in variants; every other screen keeps its look):

- `KitRow(titleMark:)` + `KitRow.changeMark` (a small accent dot after the name with the state in
  words for screen readers) and `KitRow.glyph(accent:)`.
- `KitPageHeading` (new part): a large title and one quiet line close under it, the line optionally a
  switcher (chevron, 48 dp target, its own label).
- `KitViewer(reader: true)` / `showKitViewer(reader: true)`: the one action pinned at the bottom as the
  solid main button with Copy beside it (Copy stays in ⋯ at large text), Wrap as a neutral toggle in
  the header, code wrapped by default on a phone.
- `KitCodeBlock.fill(hangingIndent: true)`: a wrapped line continues under its own indent (the indent
  stays real, selectable text) and a hairline rule separates the line numbers from the code.

Screen:

- `ProjectHub` (the Files page and the rail's Files destination) is now the project's files: a bar
  with Back, Search and the tools' ⋯; the large title (or the open folder's name) and the project line
  under it; then `FilesScreen`'s list. No project: the title and one calm state with Choose a project.
- `FilesScreen`: one plain row per entry (folder or file glyph, the name; no dates, sizes, type icons,
  chevrons or breadcrumb); a change on the name; the search field opens from the host bar's Search and
  closes on Back; search results say their folder; the reader opens with Ask about this file; the row
  menu leads with Ask about this file. Without a host bar (tests, other hosts) the field stays visible
  as before.
- Ask about this file: in a conversation it stages the file reference (`ReviewHandoffSession`) and
  closes the reader and the page back to that conversation; elsewhere it opens a new conversation in
  this project with `@path ` in the field, unsent. Attach to prompt, Save, Reload, Open review and Copy
  path are in the reader's ⋯.
- Removed from the page: the tools rows (now ⋯), the always-visible search field, the breadcrumb, the
  "N changed files +a −b" row above the list (the marks and ⋯ › Changes replace it), the "1 running"
  line under Terminal (no row left to carry it), per-row status words for added/modified (now in
  semantics; "Deleted" stays as words because a deleted file is dimmed).

Copy: `filesAskAboutFile`, `filesMenuChanges`, `filesProjectSwitchLabel` (en + ar).

Widgetbook: `widgetbook/lib/files/files_page.dart` catalogues `ProjectHub` (Files, Inside a folder,
Reader, Empty folder, No project).

## 3. Visual passes against the concepts

Renders: `/home/eslam/Storage2/launch-footage/vision-files/` (`before/`, `pass1/`–`pass3/`, final in
the folder). Capture: `tool/capture/vision_files_test.dart` (412x915 dp at 2x, blurred shadows on,
aurora painted still).

- Pass 1 (vs concept C and B): the page had no ground (white, no Material) because the browser lost
  its screen wrapper; the reader cut long lines and wrapped them back to the margin; Arabic showed
  boxes (the capture's theme did not reach the pushed routes).
- Pass 2: page put back on its screen; code wraps by default on a phone with a hanging indent and a
  number rule (B); search results named the full path (repeat of the name) and the search field
  carried a filter button for Show hidden / order; the project line sat a full target below the title;
  no-project state was a small inset card under a small bar title, unlike the page.
- Pass 3: search results say only their folder; view choices moved to ⋯ (the field is just a field);
  the project line sits close under the title (C); the no-project state is the page's title and one
  calm state with the one main action; at 2x text the reader keeps the code's room (Copy into ⋯).
  Final: a step of air under the bar for the title on wide windows' toolbar.

Kept apart from the concepts on purpose: no bottom dock (gone on phones), a Back arrow (the page is
opened from a conversation), the mark dot beside the mint name (colour is never alone), hairlines
starting at the words (the kit's list rule), the code flat on the reader's surface rather than a
second card inside the sheet (one level).

## 4. Checks

- Affected tests (42 files: the Files, project hub, chat chip, home navigation, no-bottom-nav, Paseo
  tools, regression, kit viewer/code block/row and ratchet suites) and
  `test/kit/kit_files_reader_parts_test.dart`: all pass (`--concurrency=4`).
- Goldens regenerated with `TZ=UTC` and looked at: `test/goldens/shell_files_page_*`,
  `chat_project_files_page_*`, `shell_files_project_sheet_*`, `test/revamp/goldens/files_*`,
  `project_hub_*`.
- `flutter analyze`: no issues. Widgetbook: `flutter analyze lib` clean; `coverage_check` no longer
  lists ProjectHub; `standard_check_test` shows no findings for project/files (its other NEW findings
  and the chats-list failure are not from this branch).
- `tool/qa/preflight.sh a173bd8bf` (with the pinned Flutter first on PATH): commit rules, format,
  l10n output, analyze and the QA tool tests pass. Four repo-scanning tests fail on files this branch
  does not touch, all already on the base: the ledger lacks `settings/settings_group_page.dart`,
  `KitChoiceTiles` misses its G4 items, `tool/capture/vision_model_picker_test.dart` adds an
  `ignore`, and `p310_settings_group_this-app_*.png` breaks the golden naming rule. KitPageHeading's
  own G4 items (export, table row, States line, test, gallery) are in.
- Not run: the full suite (owner rule), emulator, adb.
