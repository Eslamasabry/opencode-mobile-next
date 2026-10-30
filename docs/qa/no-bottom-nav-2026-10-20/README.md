# No bottom bar on phones (2026-10-20)

Branch `feat/no-bottom-nav`, from int `c7ff5ec28`. Owner decision: concept 1
(Conversations is the only home, a Settings gear beside Search) plus concept 9
(files live where they are needed: the conversation's project chip, the
changed-file rows under a reply). Concepts:
`/home/eslam/Storage2/launch-footage/nav-concepts/01-gear-home/concept.png`,
`.../09-files-in-chat/concept.png`.

Finish line: on a phone (compact window) the dock (Conversations · Files ·
Settings) is gone; home is the Conversations list; Settings opens from the
gear; a project's Files open from inside a conversation and from Search.
Non-goal: changing what the Files hub or Settings contain.

## 1. Audit: what the two tabs offered, and where each thing goes

"Taps" counts from the Conversations home on a phone.

### Settings tab

| Old place (tab) | New place (phone) | Taps |
| --- | --- | --- |
| Settings tab in the dock | Gear at the top end of the home bar, beside Search; opens the same Settings hub as a page with Back | 1 |
| Every Settings group row (General, Appearance, Notifications, …) | Same rows on that page | 2 |
| Servers block under the groups | Same page | 2 |
| "N not available on this server · Why" note | Same page | 2 |
| Settings search (the shell's search button on the tab) | Search on the home bar: the same launcher, same index | 1 + typing |
| Ctrl/Cmd+3 | Opens the Settings page (phone) / the Settings rail destination (tablet, PC) | 1 |
| Ctrl/Cmd+, | Unchanged: Settings page over the current screen | 1 |

### Files tab (`ProjectHub`)

| Old place (tab) | New place (phone) | Taps |
| --- | --- | --- |
| Files tab in the dock | In a conversation: the project chip under the title ("neon-breaker ⌄") opens the project's Files page. From home: Search → "Files" (the first results, no typing needed) | 1 in a chat / 2 from home |
| Project chip (switch project: New project, Search this phone, Opened before) | Same chip at the top of the Files page | 2 in a chat |
| Recent projects ("Opened before") | Inside that chooser, unchanged | 3 in a chat |
| ⋯ menu: Scheduled prompts, Switch project, Copy folder path | Same menu on the Files page (left as it was: moving Scheduled prompts out of ⋯ is a separate change) | 2–3 in a chat |
| Changes row (+ "3 files changed") | Same row on the Files page; also ⋯ › Changes in the chat, and the changed-file rows under a reply | 2 |
| Browse files (and its search field) | Same row, now opening the browser as its own page (system Back walks folders, then returns) | 2 |
| Terminal row (+ running count, or the dimmed "why") | Same row | 2 |
| Project health, Worktrees, Development services, Cloud environments | Same rows | 2 |
| No project open: "Choose a project" state | Same state on the Files page | 2 |
| "Files isn't available" row/sheet on a server without project tools | The chip has no Files on such a server (it was a menu with no Files entry there already); Search "Files" explains with the same sheet and its Switch server action | 2 |
| Ctrl/Cmd+2, Ctrl+F in Files | Phone: opens the Files page (Ctrl+F there focuses its search). Tablet/PC: rail destination as before | 1 |
| Search results "Files", "Search files" | Phone: open the Files page (and its search). Tablet/PC: the rail destination | 2 |
| Search results "Changes", "Terminal", "Project health", "Worktrees", "Services", "Cloud environments" | Unchanged: they open their own pages | 2 |

### What has no natural new home

Nothing is dropped. One honest gap: from home with no conversation open, a
project's Files take two taps through Search (or three through a
conversation) instead of one tab tap; that is the trade the owner picked in
concept 1. To keep it short, Search now also lists each project by name
(found once typed) and opens that project's Files page.

### Tablets and PC (kit-v2 §8)

The rail (medium) and sidebar (expanded, large) keep Conversations · Files ·
Settings. Files stays: on a big screen it is the only one-tap way to a
project's tools without opening a conversation, and the rail has the room. No
gear there: the rail already names Settings.

## 2. Routes that opened a tab, and their remap

| Door | Before | After (phone) | After (tablet/PC) | Test |
| --- | --- | --- | --- | --- |
| `SelectDestinationIntent(0)` (Ctrl+1, launcher "Conversations", search "Conversations") | Chats tab | Back to the home route | Chats destination | `test/no_bottom_nav_test.dart` |
| `SelectDestinationIntent(1)` (Ctrl+2, launcher "Files", search "Files") | Files tab | Pushes the Files page | Files destination | same |
| `SelectDestinationIntent(2)` (Ctrl+3, launcher "Settings", search "Settings") | Settings tab | Pushes the Settings page | Settings destination | same |
| `OpenProjectToolIntent(files / search)` (search "Files", "Search files") | Files tab, Files open / its search focused | Files page with the browser open / its search focused | as before | same |
| Chat command "Workspaces" (`HomeScreen(initialTab: 0)` pushed as a second shell) | A second shell on top of the chat | Returns to the one home (`OpenChatsIntent`) | same | same |
| `OpenChatsIntent` (notifications, Quick Settings tile, sessions widget, team links, app shortcut) | Chats tab + filter | Unchanged: home + filter | unchanged | existing `home_navigation_test.dart` |
| `/home` (`pushNamedAndRemoveUntil`) from setup, servers, links | Chats | Unchanged | unchanged | — |
| Usage widget, `/chat/<id>` links, attention landing | Own pages | Unchanged | unchanged | — |

## 3. Back

- Phone: Settings and Files are pushed routes, so system Back (and
  predictive back) returns home; Files' browser is its own route above the
  Files page. Back on home keeps the "Press back again to exit" guard.
- Tablet/PC: unchanged (Files unwinds its browser, then any destination
  returns to Conversations).

## 4. Changed files under a reply

What the chat already has: every completed `edit` / `multiedit` / `write` /
`patch` / `apply_patch` tool part of the turn (the same tool evidence
`RunResult` already reads, `lib/domain/run_result.dart`), and the line counts
those tools report themselves: an edit's `metadata.filediff.additions/
deletions`, a patch's per-file `additions/deletions`. There is no server-side
per-turn diff on every backend (OpenCode 2 answers "this chat" with the
working tree), so the rows are built only from that tool evidence:

- `RunResult.editedFileStats` lists the turn's files in the order first
  touched, and sums the reported counts per file. A file gets **no numbers**
  as soon as one change to it did not report them; a `write` counts only when
  it made a new file (`exists: false`, its content's lines), never an
  overwrite. Nothing is estimated.
- The rows show under a **finished** turn (not while it runs), at most five,
  each opening the file's preview; under them "All files" (or "All files · N
  more changed") opens the project's Files page with the browser open, with
  Attach and review prompts going back to this conversation.
- Not shown in the demo (isolated) chat or a watched team worker, which
  cannot open project files; "All files" is left out on a server without
  project tools or in a temporary folder.

Honest limit: the counts are what the tool reported for its own change, not a
diff of the working tree now (a later manual edit is not reflected), and the
rows are absent for agents whose tools do not report edits this way.

## 5. Conversations list fixes (owner's phone screenshot, 2026-10-20)

- "Termux isn't answering. Its conversations aren't shown." was body text
  glued to "Today" with no way forward. It is now the one quiet status line
  under the bar (`KitStatus(quiet: true)`), "Termux isn't answering · Try
  again" with ×; the full sentence is its screen-reader label. Try again
  retries that connection; × hides it until the set of servers not answering
  changes. The other quiet notes keep a `space2` gap above "Today".
- The filter chips overflowed off the edge. Chosen fix, the simpler one that
  removes something: "All connections" shows only when more than one
  connection is answering (one live connection means there is nothing to
  choose). With two or more the row can still be wider than 412 dp (four
  chips are about 465 dp), so `KitFilterChips` also fades the chips under
  the two gutters; a row that fits leaves the gutters empty and the fade
  unseen.

## 6. Visual passes (412x915 dark, against the concepts)

Renders: `/home/eslam/Storage2/launch-footage/no-bottom-nav/` (`after-*.png`;
`before-home-*.png` is the shell before this change).

1. Pass 1: the dock gone, gear beside search; pill still one line
   ("Laptop · Connected"). Concept 1 has the name over the status: the bar
   pill is two lines now. The changed-file rows rendered; "All files" was a
   bold white button, much louder than the concept's muted link.
2. Pass 2: "All files" is a quiet secondary link starting on the rows' edge;
   the chat capture opens from home, so its bar has Back like the concept;
   Arabic renders with the device fallback font. At 320 dp and 2x text the
   pill cut the name to "Lap…" beside the gear.
3. Pass 3: the pill's words stop growing past 1.5x (like the page title),
   its gaps tighten at large text and "Connected" is left to the green dot
   there, so the name reads; roomier pill padding and more room above the big
   title at normal sizes.

Left as they are, on purpose: the conversation rows (their state words sit in
the meta line, not at the end with a chevron as in the AI concept); the
project line under the chat title stays the quiet folder line with its Git
chip rather than the concept's outlined pill.

## 7. Images (one full phone screen each)

`/home/eslam/Storage2/launch-footage/no-bottom-nav/`:
`after-home-dark.png`, `after-home-light.png`, `after-chat-files-dark.png`,
`after-settings-from-gear-dark.png` (and `-light`),
`after-files-from-chip-dark.png`, `after-home-pc-1280x800-dark.png` (the PC
sidebar keeps Conversations · Files · Settings),
`after-home-phone-320-text2-dark.png`, `after-home-ar-dark.png`,
`after-home-unreachable-dark.png`. Before: `before-home-dark.png`,
`before-home-light.png`. Renderer: `tool/capture/no_bottom_nav_test.dart`
(Flutter test renders with real fonts, not device screenshots).

## 8. Verification (candidate: this branch's commit)

- `flutter analyze`: no issues (after `flutter pub get` in `widgetbook/`).
- Focused files, `--concurrency=3`, all passing at the end: the new
  `test/no_bottom_nav_test.dart` (phone has no bar and a gear; gear → Settings
  page and Back; Ctrl/Cmd+1/2/3 and search "Files"/"Settings" remaps;
  OpenProjectToolIntent(files) → Files page with the browser, two Backs home;
  double-Back exit guard; the PC keeps the rail with Files; per-file change
  counts), `home_navigation`, `kit/kit_nav`, `project_hub`,
  `files_project_chip`, `chat_project_chip`, `chats_home` (unreachable status
  line, connections chip only with two live connections), `desktop_shortcuts`,
  `desktop_scrollbar`, `settings_rebuild`, `revamp/screen_shell_2`,
  `kit/kit_top_bar*`, `kit/kit_filter_chips`, `kit/kit_screen`,
  `product_ui_regression`, `search_index`, `codex_*navigation`,
  `first_run_landing`, `launch_session_shortcut_routing`, `server_switcher`,
  `paseo_project_tools`, `v2_feature_gating`, the chat set
  (`test/chat*_test.dart`, `test/revamp/chat_*`, `chat_live_events`),
  `text_scale_overflow*`, `kit_ratchet` (G16/G17), `ui_glossary` (G28),
  `l10n_coverage`, `file_size_ratchet`, `kit/kit_manifest` (G4),
  `app_theme`, `accessibility_guidelines`.
- Goldens regenerated with `TZ=UTC --update-goldens` for the files whose
  renders changed (chats home and shell: the two-line pill, the gear, the
  title's room, the chips' gutter fade; chat transcripts with edits: the
  changed-file rows; KitNav: dock goldens removed, phone "no bar" and rail
  part added; the theme gallery no longer draws a Material NavigationBar) and
  looked at. Not a full suite run (owner rule for this round).

Accessibility: the gear is a 48 dp KitIconButton labelled "Settings"; the
pill keeps "Laptop, Connected, Switch server" as one label; the changed-file
counts read as "30 lines added, 5 lines removed"; the project chip says
"Project neon-breaker. Opens its files and tools."; the status line's ×
and Try again are named.

## 9. Round 2 (coordinator review, same day)

### Fixes

1. **The Termux note capture.** The first render pumped the Conversations
   list on its own, without the shell, so the bar was missing from the image.
   The real screen was right: the note uses the status slot under the bar.
   The capture now renders the real shell over the same feed
   (`after-home-unreachable-dark.png`: pill, Search and gear on top, then the
   note, then the title). With larger text, the quiet status line no longer
   leaves a "·" at the start of a line: when "Try again" drops under the
   words it goes without the dot, and the icon stays by the first line
   (`KitStatusLine`).
2. **The giant "Working".** The running state word sat in a widget span,
   which the line already scales, and its own text was scaled a second time
   (4x at 2x text). It now has one size with the rest of the meta line. The
   dot before the next item has a no-break space, so it never starts a line,
   and the time ("2 min ago") never breaks. Needs you, Working and Stopped
   rendered at 320 dp and 2x text: `after-home-phone-320-text2-states-dark.png`.
3. **The model under a reply.** The footer showed "anthropic/claude-sonnet-4"
   when the model list had not been read. It now uses the catalog's name
   ("Claude Sonnet 4"), else the name this server gave before
   (`knownModelName`), else a readable name made from the id
   (`modelNameFromId`, "Sonnet 4"). Never the provider/ prefix.

### Search's own menu (mockup 6)

Search opens on a menu before anything is typed: a **Go to** group, then
**Recent** (the last three conversations, each with its state word). It is
the Search sheet's empty state (`lib/ui/search/go_to_commands.dart`,
`matchCommands`), not a new screen. Tablets and the PC get the same menu
from the same button or Ctrl/Cmd+K. Typing filters these rows with
the rest of the launcher through the same matcher. While typing, the Recent
rows drop out because conversations are searched as themselves. The places
already covered by an index entry (Servers, Terminal, the AI Team, Help) are
not listed twice.

Additions to the old → new table (section 1):

| Old place | Go to row | Gate |
| --- | --- | --- |
| Files tab | Files · <last used project> (Ctrl/Cmd+2) | `ProjectHub.isAvailable(capabilities)` |
| Files tab project chip, "All projects" | Projects: the "Open a project" chooser, then that project's Files | `capabilities.projectManagement` |
| Settings tab | Settings (Ctrl/Cmd+3) | always |
| Settings › Servers, Add server | Servers, or "Add a server" when none is saved | always |
| Files tab › Terminal | Terminal (Ctrl/Cmd+`) | `capabilities.terminal` |
| Files tab ⋯ › Scheduled prompts | Scheduled prompts | `canSchedulePrompts` |
| Settings › AI Team | AI Team | the connection has a team (`orchestration`) |
| Settings › Help › Setup guide | Help | always |

The sheet also lost its lightning icon, its title says "Search" (it was
"Command launcher"), the field hint is "Search or go to…", and the row groups
line up with the field (no second gutter). Files shows its project in the
quiet tone on the same line (a new `KitRow.titleDetail`).

Visual passes against mockup 6:
- **Pass 1:** the rows were inset twice and smaller than the field, and the
  big lightning tile and "Command launcher" title came before the field.
  The project sat in the title's colour.
- **Pass 2:** the groups now span the field's width, there is no icon tile,
  the title is "Search", the project is muted, and the Recent rows lead with
  Needs you or Working. The sheet takes the screen's text size and theme in
  the capture.

Renders: `after-search-open-dark.png`, `-light.png`,
`after-search-open-320-text2-dark.png`, `after-search-open-ar-dark.png`.

Tests: `test/search_go_to_test.dart`. It covers the empty state (Go to in
order, then Recent; "New conversation" waits for typing), gating (no
Files, Projects or Terminal on a server without them, and "Add a server" with
none saved), and typing (it finds Terminal, Settings, Files by the project's
name and New conversation, and the Recent rows step aside). It also opens the
real sheet, checks the group labels and rows, and narrows them by typing. That
widget test found a dangling `else` in the sheet's row list, which drew
nothing for a typed query, before it could ship.
