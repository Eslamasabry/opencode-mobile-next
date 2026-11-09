# Cache-first, no alternate layouts — native app, 2026-11-09

Branch `native/cache-first` (from `native/next` ece1e5d20). Owner, 2026-11-09, looking at the home while This phone was
connecting (a plain list of saved titles with bubble icons and a "Starting this phone…" notice, then a jump to the real
A1 home): "Why do we have this view in the code still and why not cache something to load fast? This applies
everywhere."

## The rule

For every screen and every server (This phone, a computer, a VPS):

- **Stale-while-revalidate.** On open, draw the exact real layout at once from the last-known data, then refresh in
  place.
- **No alternate layouts.** No offline, cached or connecting variant of a screen, no blank page, no full-screen spinner
  when data is known. A skeleton or loading body is allowed only for a first-ever load (nothing kept yet).
- **Status lives in one place:** the screen's existing status slot or the server pill.
- **Freshness:** live data replaces kept rows in place, with stable keys and the kit's list motion, never a re-layout.

## The cache layer (one place, not per screen)

`:core:data` `cache/` — `LastKnown` (interface) and `FileLastKnown` (files). The app binds it in
`androidApp/…/graph/LastKnownWiring.kt`.

1. **Shelves.** One `CacheShelf<T>` per kind of data (`transcript`, later `chatList`, `projects`, `folder`, `usage`,
   …), each with a serializer, a byte cap per entry and a count of entries kept per profile.
2. **What is kept.** Only display facts the person already saw: rows with the fields the real row draws (titles,
   previews, times, agent ids, status), a conversation's newest page (60 messages), listings seen. Never secrets.
3. **Server mark.** Every entry carries a hash of (backend, base URL, user name, folder). Data from another server
   never shows and is dropped on sight, so changing a profile's server invalidates its shelves.
4. **Size caps and eviction.** Per-entry byte cap (refused, never truncated, by the store; callers trim, e.g.
   `KeptTranscript.fit`); per-shelf entry count with least-recently-written eviction (an order file per shelf); memory
   holds decoded values up to 6 MB, least recently used first.
5. **First frames.** `peek()` answers from memory without I/O; `read()` fills memory; `warm()` preloads a shelf's
   newest entries.
6. **Storage.** `<app cache dir>/last-known/<hash(profile)>/<shelf>/<hash(item)>.json`, written through `.tmp` + atomic
   rename. Hashed names, so no path carries a server's words. The cache folder is never backed up; losing it costs one
   cold load.
7. **Deletion.** `LastKnownDeletion` (a `ProfileResourceCleaner`, step DEVICE_SURFACES) removes the profile's folder
   when the profile is deleted (AGENTS.md rule 9; files are not prefs keys, so they go through the cleaner, not the
   `oc.<what>.<profileId>` sweep).
8. **No secrets.** A value whose JSON carries a credential-shaped key (`password`, `token`, `apiKey`, `authorization`,
   …) is refused. Provider config (`/config/providers`) is never put on a shelf.
9. **Existing small caches stay where they are**: `oc.sessionInventory.<profile>` (home rows, richer since
   home-connecting), `oc.openedProjects.<profile>`, `oc.modelNames.<profile>`, `oc.setupRegistry.<profile>`.
   They are already per-profile prefs and swept; new kinds go on shelves.

## Audit (timeboxed, 2026-11-09)

What each screen shows instead of its real layout while its data loads or its server is away. "Fixed" means fixed on
this branch with a test; "home-connecting" means fixed by that branch (owner of the home).

| # | Screen | Where (commonMain) | Shows now | Data it needs kept | State |
|---|---|---|---|---|---|
| 1 | Conversations home | `chats/ui/ChatsScreen.kt:275-282`, `ChatsHomeSheets.kt:367-433` (`OpeningStatus`, `OpeningRow`) | The whole body replaced by a notice plus plain title rows with a bubble glyph (the owner's screenshot); skeleton when nothing saved | Feed rows: title, project, status, time, agent mark, preview, folder | home-connecting (d32c88ea7) |
| 2 | Conversations home | `ChatsScreen.kt:330-333` (`ChatsPhase.Opening`) | Skeleton rows instead of sections | Same as 1 | home-connecting |
| 3 | All conversations | `chats/ui/ChatsAllScreen.kt:252` (`AllPhase.Opening`), `:266` (`AllPhase.Failed`) | 7 skeleton rows; full-body error when every source failed | `AllRowUi`: title, project, server, running, archived, pinned | Pending (after home-connecting lands: feed from the saved inventory) |
| 4 | Chat transcript | `chat/ui/ChatScreen.kt:838` → `ChatStates.kt:73` (`KitSkeletonTranscript`) | Two-turn skeleton, no title, until the history read | The newest page of messages and the title | **Fixed** (CF-10…14) |
| 5 | Chat transcript, server away | `GraphChatWorld.kt` `bind(null)` → `ChatLoadError` | Full-page cable error, composer hidden | Same as 4 | **Fixed**: kept rows + Offline status line |
| 6 | Search | `search/ui/SearchScreen.kt:93-107` | Already the real layout; failure inline | Recent conversations (title, project, time, agent) | No change needed; recent conversations could come from the home inventory |
| 7 | Projects picker | `projects/ui/ProjectsPickerScreen.kt:339,349` | 3 skeleton rows / error for the server's projects ("Opened before" is instant) | `FolderRowUi`: folder, name, conversations, running, git | Pending |
| 8 | Project page | `projects/ui/ProjectPageScreen.kt:120-122,250-259` | Whole page skeleton, or a full error state | The project's conversations (title, running) and its flags | Pending |
| 9 | Open project (folders) | `projects/ui/OpenProjectScreen.kt:178,214-226` | Skeleton rows for folders | `FileNode` listing per path | Pending |
| 10 | Files, folder | `files/ui/FolderScreen.kt:318-358` | Full-body loading state, not-connected or error states | `FileNode` listing per path | Pending |
| 11 | Files, file | `files/ui/FileScreen.kt:394-431` | Skeleton, not-connected state | File text (bounded) per path | Pending |
| 12 | Agents account panel | `agents/ui/AgentAccountPanel.kt:104-135` | Full-body loading state | Account line: signed in, plan, usage lines | Pending |
| 13 | Agents hub | `agents/ui/AgentsScreen.kt:~340` | Notice only; tiles stay | — | No change needed |
| 14 | Usage | `usage/ui/UsageScreen.kt:610` | Skeleton while the first report loads | `UsageReport` rows and totals | Pending |
| 15 | Usage, limits | `usage/ui/LimitsContent.kt:106` | Skeleton until the first snapshot; offline keeps rows with a notice (the model to copy) | `UsageSnapshot` | Pending |
| 16 | Servers list / details | `servers/ui/ServersScreen.kt:99`, `ServerEditorScreen.kt:272,320` | Loading bar overlay; check result replaced by "running check" | Last check result and time | Local list; check result Pending |
| 17 | Server queue | `servers/ui/ServerQueueScreen.kt:81,239` | Refresh button instead of the plan | Last queue plan | Pending |
| 18 | Settings › Model defaults | `settings/ui/ModelDefaultsScreen.kt:42,50` | Skeleton; real rows hidden while loading | Server default model/agent; model names (`oc.modelNames` exists) | **Fixed** (CF-15, CF-16) |
| 19 | Settings › Library | `settings/ui/LibraryScreen.kt:450`, `LibraryRegistrySheet.kt:60-98` | Skeleton; Offline notice | Inventory rows; registry (`oc.setupRegistry` exists) | Pending |
| 20 | Settings › Server configuration | `settings/ui/ServerConfigurationScreen.kt:75-80` | Full-body loading / unavailable states | The configuration sections | Pending |
| 21 | Git | `git/ui/GitScreen.kt:150-170` | Blank body while loading; full-body unavailable state | Branch, ahead/behind, changed files, last commit | Pending |
| 22 | Terminals | `terminal/ui/TerminalScreen.kt:224-225` | Skeleton; blank body when failed with no list | Terminal list (id, title, status, folder) | Pending |
| 23 | AI Team | `team/ui/TeamHomeScreen.kt:92,110`, `TeamProjectScreen.kt:135`, `TeamBoardScreen.kt:92`, `TeamTaskScreen.kt:116`, `TeamEditorScreen.kt:128` | Blank body while loading | Projects, board lanes and cards, task detail | Pending |
| 24 | AI Team project pages | `team/ui/TeamProjectPagesScreens.kt:72-77,224-229` | Blank while loading; `stale` keeps rows (the right pattern) | Servers and tasks | Pending |
| 25 | FactsBook | `factsbook/ui/PageScreens.kt:134,240,427`, `StatusScreens.kt:160,327`, `RemindersScreens.kt:145,280`, `TriggersScreens.kt:161,578`, `NeedsYouScreen.kt:122`, `AboutYouScreen.kt:168`, `MemorySection.kt:84-93` | Skeleton rows until each list loads | Areas, things, reminders, triggers, needs-you, memory rows | Pending |
| 26 | Prompts (server lists) | `prompts/ui/ServerListsScreens.kt:126,149,213` | Skeleton rows | Prompts and skills (title, preview, scope) | Pending |

Local-only screens (no server data; a skeleton there is a quick local read, not a cache gap): Linux pages, Manage
space, voice teaching and licences, phone running, kept and saved prompts, setup flows, share and isolated-task sheets,
connection forms, permission settings.

## The guard

- **Source gate** `quality/…/CacheFirstGate.kt` (`CF-GATE-1…3`):
  - no feature composable named like an offline, cached, fallback or saved-titles layout (`OpeningRow` is the one left,
    listed until home-connecting removes it);
  - every loading branch in a feature that replaces a body (skeleton, loading state view, blank) is reviewed as
    Local, Cold (cache-first; named first-frame test) or Pending; Pending screens have a ceiling (38 at the start) that only goes down.
- **First-frame tests per screen**: the frame with kept data has the same structure as the live frame (same phase,
  rows, keys and title; status in the status slot). Chat: `CacheFirstChatTest` CF-12…14, `KeptTranscriptTest`
  CF-10…11. Store: `LastKnownTest` CF-01…06.

## Slices

| Slice | State | Commits |
|---|---|---|
| Cache layer (`LastKnown`, `FileLastKnown`) | done | 9e090f445 |
| Chat transcript opens on its kept page | done | see branch log |
| Gate | done | see branch log |
| Home | home-connecting branch | d32c88ea7 (not landed at time of writing) |
| All conversations, projects, Agents, Search, Usage, rest | pending | — |
