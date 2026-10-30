# Settings hub: search and five words (vision slice, 2026-10-20)

Branch `feat/vision-settings-hub` (from int `93bba38fa`). Concept: `b3-settings-hub/concept-c.png`
plus one value line for Server (from concept B). Pick and lists: the vision page "Settings hub",
`docs/design/vision-decisions-2026-10-20.md`, the five-group proposal in
`docs/design/settings-ia-audit-2026-10-12.md` §3 and the scope model in
`docs/design/settings-scope-2026-10-12.md`.

Finish line: Settings opens on a search field and five one-word rows; each row opens its group's
page holding every row that group has today; the search finds every setting. Non-goal: moving
settings between groups, renaming inner pages, or touching the shell, the dock or the aurora.

## 1. Data audit (written before building)

The question per element: what in the app or on the server makes it true?

| Element in the pick | Source | Verdict |
|---|---|---|
| "Settings" large title | The shell tab's own title (`librarySettingsTitle`), already drawn by `SettingsScreen(embedded: true)` | **Real.** Kept. |
| Search field "Search settings" | `searchEntries(l10n, scope, query)` in `lib/ui/search/search_index.dart`: the gated index (21 hub rows, ~45 inner settings, places) and the shared matcher `lib/domain/settings_search.dart` (word, prefix, one typo, every language's words). Read only; the index and the search screen are not edited. | **Real.** Built as an inline search on the hub: the five rows give way to the matching settings (hub rows and inner settings only, not places), each with where it lives. A result opens exactly what the search screen opens (`SearchEntry.open`). |
| Five rows: Server, Agent, Conversations, This app, Help | `SettingsGroup` (connections, ai, chat, thisApp, help) and the rows each group holds today (`_groups` in `settings_screen.dart`), which already follow the scope model (personal rows everywhere, server-owned rows "By server" once several are saved, SC4) | **Real.** Each row opens its group's page with the same rows, values, switches, gates and "By server" pages as today. No setting moves group. |
| Server value line "On this phone · Connected" | Where: `controller.profile` + `isPhoneOwnServer` (in-app Ubuntu, the Termux server, Claude Code on this phone) or `serverDisplayName` for a computer. State: `controller.connectionStatus.phase`, in the same words as the server switcher and the shell pill (Connected / Connecting / Reconnecting / Offline). | **Real.** Shown for the server in use. No server saved: "Not set up yet". Several saved: still the one in use (the list is one tap down). An isolated window or no server in use shows only where, never a guessed state. |
| Green dot before "Connected" (concept B) | Same phase | **Left out.** The word already says it; a dot plus the word is the same fact twice (LEARNINGS, "it repeats facts"). |
| Agent value "Sonnet 5.5 · 3 tools" (concept B) | Model name exists (`selectedModel` + catalog); a tool count would need a server read per open | **Rejected by the pick** (only Server earns a line). Not built. |
| Summaries "History, titles and sharing", "Appearance, alerts and language", "Guides, support and app info" (concept B) | Static copy | **Rejected by the pick.** Not built. |
| "Ask the setup assistant" row (concept B) | No assistant exists; Setup guide is in Help | **Rejected.** Not built. |
| "Some settings need a newer server · Why" (concept B) | `SearchEntry.hiddenByServer` per hub row | **Removed from the hub.** The honest count moves to the group page it belongs to ("1 setting isn't available on this server · Why"), so `Available on this server` stays reachable. |
| Section headers, sub-rows on the hub | Today's group labels | **Removed.** The group's name is now the row's title and its page's title. |
| "You can change some of these for one server" block (several servers) | `SettingsServersBlock`, `settingsServers()` | **Real, moved** to the Server page, where servers live. |
| Report a problem error badge | `reportProblemErrorCount` | **Real, kept** on the Help page's row; not on the hub (no summaries). |
| Line icons per row (server, robot, chat bubbles, sliders, question) | `AppIconography` (Phosphor) | **Real.** Plain glyphs without tiles, in the app's icon set (Phosphor stays; agent-glyphs consulted for the closest match). |
| Aurora behind the page, the dock | The shell (`home_screen.dart`, `KitNav`) and the live background parts | **Not this screen.** Owned by other agents; untouched. The captures show whatever the shell paints today. |
| Wide windows (tablet, desktop) | `KitScreen.twoPane` | **Real, kept.** The list pane is the same search and five rows; the chosen group's rows fill the detail pane. |

Capability and flavor: the hub reads no flavor. Every row's presence still comes from its index
entry's gate (`ServerCapabilities` flags and platform capabilities), unchanged.

OpenCode 2 and Paseo: nothing on this page talks to a server. The Server line reads the
controller's existing connection phase, which every backend (OC1, OC2, Paseo phone agents)
already feeds.

## 2. What was built

- `lib/ui/screens/settings_screen.dart`: the hub is the shell tab's large title, one search field
  and five rows (Server, Agent, Conversations, This app, Help) on one panel. Server carries one
  quiet line: where the server in use runs and its connection word (no dot, no colour). Typing
  swaps the rows for the matching settings (rows and inner settings from the shared index; places
  are left to the header's search), each with where it lives; Enter opens the first; no match is
  one `KitSearchNoMatch`. Rows and results cross-fade (`KitSwap`, instant under reduced motion).
  From 2x text the rows drop their glyph and chevron so the words keep the row. Wide windows:
  the same hub is the list pane, the chosen group fills the detail pane.
- `lib/ui/screens/settings/settings_group_page.dart` (new part): `SettingsGroupScreen`, one group
  on its own page titled with its word, holding exactly the rows the group held on the old hub
  (values, switches, "By server" pages, gates unchanged), the group's own "N settings aren't
  available on this server · Why" line, and on Server the per-server block once several are
  saved. `SettingsScreen(initialGroup:)` (what a search result that acts in place pushes) is
  that page on a phone.
- Kit variants that leave every other screen unchanged: `KitRow(roomy: true)` (64 dp rows) and
  `KitRow.glyph` (a bare 26 dp line glyph in a tile-wide slot), `KitSearchField(prominent: true)`
  (a pill with no hairline until focus). Tests in `test/kit/kit_row_test.dart` and
  `test/kit/kit_search_field_test.dart`.
- Copy (en + ar): `settingsHubSearch`, `settingsHubServer`, `settingsHubHelp`,
  `settingsHubServerOnPhone`, `settingsHubServerLine`, `settingsHubServerNone`. Removed the now
  unused `settingsV3GroupConnections/Ai/Chat/Help` (en, ar, es, zh).
- Not changed: the search index and search screen, the shell and dock, the chats home, the chat
  screen, the live background.

Every setting stays reachable: the 21 hub rows of `hub_rows.dart` are the rows of the five pages
(`settingsGroupRowIds`), and every inner setting is found by the hub's search.

## 3. Visual passes (against concept-c, 412x915 dark)

Captures: `tool/capture/vision_settings_hub_test.dart` (the real shell on its Settings tab, the
live background painted still, real fonts, 2x pixels). Passes in
`/home/eslam/Storage2/launch-footage/vision-settings-hub/pass1..3/`, before in `before/`.

| Pass | Less polished than the concept | Change |
|---|---|---|
| 1 | Icons in tiles on the old 18-row page (before) | Search pill + five roomy rows with bare glyphs; group pages |
| 2 | Hairlines ran to the panel edge from the icon; glyphs small (24 dp); Arabic in boxes (no Arabic font in the capture); 320 dp at 2x broke "Conversati/ons" | Hairlines inset to where the words start (kit rule); glyph 26 dp; the app's Arabic type in the capture; no glyph at 2x |
| 3 | At 2x the chevron still split "Conversation/s" | No chevron at 2x either: the whole row is the door |
| 4 | "No results" over "Nothing matches" said the same thing twice | The count line only when something matched |

Kept different from the concept on purpose: the title stays the kit's large title (Chats and Files
use the same size), the panel radius stays the kit's 18 dp, the aurora and the dock are the shell's.
The group pages keep the kit's icon tiles, like every inner settings page.

Final renders (one full phone screen each) in `/home/eslam/Storage2/launch-footage/vision-settings-hub/`:
`main-dark`, `main-light`, `several-servers-dark`, `offline-dark`, `no-server-dark`, `search-dark`,
`search-no-match-dark`, `agent-page-dark`, `conversations-page-dark`, `server-page-several-dark`,
`small-320-text2-dark`, `arabic-rtl-dark`.

## 4. Checks

- Tests updated to reach rows through the doors (`test/support/settings_doors.dart`:
  `openSettingsGroup`, `visitSettingsGroups`, `revealSettingsRow`) or through
  `initialGroup:` where a test means one group. New: the Server line in its states, each word
  opening its page, the hub search (result, open, no match, clear), 320 dp at 2x.
- Goldens regenerated with `TZ=UTC`: `fg6_settings_hub_*`, `p310_settings_hub_*`,
  `p310_settings_group_*` (new, one per page), `p66a_settings_model_*`, `shell_reconnecting_*`.
- Pre-existing on the base, not this slice: `test/kit/kit_manifest_test.dart` (KitChoiceTiles from
  the model-picker merge). `slice_p5_1` goldens fail only without `TZ=UTC`.
