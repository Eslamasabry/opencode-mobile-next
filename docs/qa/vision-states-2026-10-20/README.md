# Offline and empty states (vision slice, 2026-10-20)

Branch `feat/vision-states` (from int `4e851845e`). Pick: **Both, toned down**
(`screen-concepts/b3-states/concept-a.png` for the reconnect line, `concept-b.png` for the calm
empty state): A's honest countdown line for connection trouble, muted not amber, and B's calm
empty states (a big calm line, one plain next step, the main action at the bottom, one tertiary
link), in one kit part across the screens outside the chat library.

## 1. Technical-honesty audit (written before building)

The question per element: what does the app or the server really hold for it?

Where reconnects happen today:

1. **OpenCode 2** (`Api2EventStream`, `lib/api2/sse2.dart`): after a dropped or failed stream it
   waits `500 ms × 2^attempt` (1 s, 2 s, 4 s, 8 s, then 16 s for good) on a real `Timer`, then
   tries again, forever, until disposed. After six attempts it reports `disconnected` but keeps
   the same schedule. The time of the next try is known the moment the timer is set, but nothing
   outside the stream can read it.
2. **Phone agents (Paseo)** and **Codex** (`lib/paseo/gateway/lifecycle.dart`,
   `lib/codex/gateway.dart`): the gateway's `_scheduleReconnect` waits `2^attempt` s (1, 2, 4, 8,
   16 s, capped) on a `Timer`, then recovers. Same gap: the time is not exposed.
3. **The controller** (`ConnectionController`): no schedule of its own. The automation policy can
   forbid automatic reconnects (`AutomationBehavior.reconnect`); then the channels are retired and
   nothing retries until the person taps Reconnect. A first connection that fails before a stream
   exists is not retried either.
4. **The 8-second rule** (`_syncConnectionStatusClock`): for 8 s a lost link reads
   "Reconnecting…" (the shell pill says it, no line); after that the phase is `notAnswering`.

| Element of the pick | Source | Verdict |
| --- | --- | --- |
| "MacBook isn't answering" | Phase `notAnswering` (8-second rule); the server named as the switcher names it (`serverDisplayName`) | **Build** (exists). The concept's "MacBook" is invented; the words name the real server. |
| "trying again in 12 s" | The stream's or gateway's real retry `Timer` (1–16 s backoff) | **Build**, after exposing it: a channel that retries on a schedule publishes when its next try is (`RetryingEventChannel.nextAttempt` in the domain contract, set by OC2, Paseo and Codex). The controller passes it on in `ConnectionStatusSnapshot.nextAttemptAt`, only while not connected and while the policy allows automatic reconnects. The line counts down once a second; at zero, while the try is in flight, it says "Trying again now". No schedule (policy off, a first connection that failed, a fake) means **no countdown**: the line keeps its Reconnect action and never claims a retry that will not happen. |
| "Try now" | `ConnectionController.retryConnection()` | **Build** (exists), as the line's own action while a retry is scheduled. Neutral text button, never amber. |
| Muted tone, no amber banner fill | Kit tone map | **Build**: the line with a countdown uses the neutral tone (`text2` glyph); nobody has to act. Amber stays for Needs you. |
| One line under the header, transcript stays readable | `KitStatusLineSlot` (one condition per window) | **Build** (exists): the same line shows in the chat, on Conversations and on every screen under the shell, from the one adapter (`connectionKitStatus`). |
| "Waiting to send" with a clock on the sent message | Offline queue (`OfflineQueue`, `pending_sends_strip.dart`, `KitQueuedMessage`) | **Exists, chat library**: owned by the chat-fidelity agent; described in the report, not edited here. |
| Chips row hidden while offline | Chat composer | **Chat library**: owner decision 4 already moves the chips row behind +; not edited here. |
| Big calm line ("Nothing running") | Conversations' Running filter with no running conversation; the empty list; a project with none | **Build**, worded as what the list really holds: "Nothing running" only for the Running filter; "No conversations yet" for an empty list; the project's name for an empty project. |
| One plain next step | Copy | **Build**, one sentence each. |
| "New conversation" in mint at the bottom | `showNewChat` | **Build**: the empty state's one primary, pinned at the bottom of the body within thumb reach (the floating + is already hidden while the list is empty). |
| "Pair a computer" as a tertiary link | `/servers` with `ServersRouteRequest.add()` (the new-server editor, which offers pairing) | **Build** on the empty Conversations list only, when no saved server is on another computer, as "Connect a computer". |
| "This phone's server is running · 410 MB" | Memory is read on This phone by running a command in the in-app Linux; the server's state is already the shell pill ("In-app Ubuntu · Connected") | **Left out.** Running that read on the home screen costs a process per visit, and the state is already said once in the pill: showing it again is a repeat. |
| Dock Work / Inbox / Project / Settings | The app's real dock is Conversations / Files / Settings | **Image artifact.** Not used. |
| Inbox empty | `KitStatusAnswer` "Nothing waits for you" (vision Inbox slice) | **Already calm.** Same part family; unchanged. |
| Files: empty folder, no project, no match | `KitStateView` with drawings and a full-width Refresh | **Build**: the calm form; Refresh moves to pull-to-refresh with one tertiary Refresh; Choose a project stays the one primary at the bottom. |
| Projects: none yet | `KitStateView` inline | **Build**: the calm form. |
| Servers: none saved | That is the Welcome page (vision Welcome slice) | **Unchanged.** |
| Scheduled: none yet | `KitStateView` with a clock tile; New sits in the bar | **Build**: the calm form with "New scheduled prompt" as the bottom primary. |
| Search with no results | `KitSearchNoMatch` in the Search sheet and Projects | **Build**: a calm variant (centred, no icon tile, Clear search as text) used by the Search sheet and Projects; other screens keep theirs. |
| AI Team: no tasks yet | `KitStateView` inline with the board drawing; the pinned button is the action | **Build**: the calm form; the pinned button stays the one primary. The task screen and the team conversation belong to the team-task agent and are not edited. |
| Never a spinner forever | `KitStateView.since` / `onSlow` (escalates at 8 s), `KitStatusLine.since` | **Kept**: every first load already escalates to "Still waiting after 8 s" with Try again. |

## 2. What was built

**The real retry schedule, exposed (domain + transports + controller).**

- `RetryingEventChannel` (`lib/domain/server_gateway/value_types_core.dart`): a live channel that
  retries on its own timer publishes `nextAttempt` (`ValueListenable<DateTime?>`): the moment its
  retry timer fires, kept while that try is in flight (a time already past), null while connected,
  once retrying stopped (a refused sign-in) and once disposed. A channel without a real timer does
  not implement it.
- OpenCode 2 (`Api2EventStream` / `Api2LiveEventChannel`): set with the backoff timer, before the
  `reconnecting` status goes out; cleared on connect and dispose.
- Phone agents (Paseo) and Codex: the gateway's `_scheduleReconnect` sets it; `connected` /
  `disconnected` and close clear it; the conversation channel exposes it (the global channels do
  not).
- `ConnectionController`: listens to the channel's `nextAttempt` (notifies, and drops the listener
  once the channel is no longer current) and passes it on as
  `ConnectionStatusSnapshot.nextAttemptAt` only while not connected and while the automation
  policy allows automatic reconnects.

**Coordinator fix (after the int merge):** the connection line with a countdown is now the quiet
one-line caption everywhere, at any text size: "Laptop isn't answering · trying again in 12 s ·
Try now", muted, Try now a quiet underlined text action (48 dp), no More and no divider. The words
open Details (`KitStatus.onOpen`), which holds Try again, Switch server and, for this phone's server
or an agent's helper, Restart; the server sheet keeps switching and retrying. The countdown rules
are unchanged. The paragraph below describes the first version.

**The honest line (`connectionKitStatus`).** With a scheduled try and no Try now in flight:
"Laptop isn't answering" (the switcher's name for the server), the second line counting down
"Trying again in 12 s" / "Trying again now…", **Try now** as the line's one action (neutral text,
never amber), neutral tone with the muted cloud glyph, and Restart (this phone's server or an
agent's helper), Details and Switch server behind More. The same line shows in the chat (via
`ConversationStatusScope`), on Conversations under the server pill, and on every screen under the
shell. Without a schedule the line is as before (Reconnect to …).

**Kit (variants; other screens keep their looks).**

- `KitStateView.calm` (page and inline): the one part for calm empty and offline states. See
  `docs/ux-system/kit-api/KitStateView.md`.
- `KitStatusAnswer`: `tone` optional (no mark), `KitAnswerScale.calm` / `compact`; fixed a word
  break: the balanced width could go under the longest word at 320 dp and 2x text.
- `KitStatus` / `KitStatusLine`: `countdownTo` + `countdownLine`; when the line would stack it is
  drawn as the quiet form with the countdown inline so Try now stays in sight; the quiet form draws
  `more` behind a small More.
- `KitSearchNoMatch(calm: true)`.

**Screens.**

| Screen | Before | Now |
| --- | --- | --- |
| Conversations, empty | Icon tile, start-aligned title, "Start a conversation" under it, chips row | "No conversations yet", "Say what you want to build, and an agent gets to work.", **New conversation** at the bottom, "Connect a computer" (only with no computer saved); no chips row |
| Conversations, Running with nothing running | "No matching conversations" + Clear filters | "Nothing running", "Start a conversation, or open one to pick up where you left off.", **New conversation**, "Show all conversations" |
| Conversations, a project with none | Icon tile + "Start a conversation in X" | Calm, with one plain next step |
| Conversations / chat / every shell screen, link down | "Laptop isn't answering" + Reconnect to Laptop, failure tone | The countdown line above |
| Files: empty folder, no match, no project, load failed, text and symbol search | Drawings (folder, magnifier, unplugged), full-width Refresh, failure tone | Calm; Refresh is a quiet text action (pull to refresh too); Choose a project stays the one primary; Try again is the primary on a failed load |
| Projects: none, list failed | Inline icon tile; red error form | Calm inline; "Projects didn't load" with Try again as text |
| Scheduled: none | Clock tile + title | Calm, "Pick a time, and this phone sends the prompt for you."; the pinned New scheduled prompt is the action |
| Search: no result | Icon tile + Clear search button | Calm compact line, Clear search as text |
| AI Team home: no tasks | Board drawing | Calm inline; the pinned "Give the team a task" is the action |
| Inbox: empty | `KitStatusAnswer` (vision Inbox slice) | Unchanged, same part family |

## 3. Visual passes (412x915 dark unless named; concept-a.png and concept-b.png beside each)

- **Pass 1** (`launch-footage/vision-states/pass1/`): the calm Conversations empty state matched B's
  structure, but the offline line did not show at all in the capture (the app-wide scope was not
  hosted) and the Projects fixture failed to load. Search's no-result line at 28 dp wrapped to two
  heavy lines in the sheet.
- **Pass 2** (`pass2/`): the line under the header matched A's structure, muted, with Try now; the
  phone-only empty state showed "Connect a computer". Seen: at 320 dp and 2x text the calm title
  broke inside "conversations" (a `KitStatusAnswer` balancing bug), and the stacked offline line
  pushed Try now out of the slot's room. Inline states needed a smaller scale than a page.
- **Pass 3** (`pass3/`): word break fixed; the countdown line falls back to the quiet one-liner
  ("Laptop isn't answering · Trying again in 13 s · Try now") at large text; the words sit at the
  optical centre (10 % above the middle) like B; the compact scale for lists and sheets.
- **Pass 4** (`pass4/`): the filter chips leave a list that holds nothing yet (B has none; nothing to
  filter); Files and Scheduled rendered through their own captures; final set copied to
  `launch-footage/vision-states/`.

## 4. Checks

Run on this candidate (serial, `--concurrency=1`): the kit tests (`kit_state_view`,
`kit_status_answer`, `kit_status_line`, `kit_search_field`, `kit_ratchet`), the G6 overflow
matrix shards 0-5 (new calm and countdown scenes), `connection_status_presentation`,
`connection_sse_stream` / `_wake` / `connection_v2_lifecycle`, `api2_sse`, `codex_gateway`,
`paseo_gateway`, `paseo_reconnect_state`, `chats_home`, `home_navigation`, the Files tests,
`project_hub`, `projects_screen`, `scheduled_prompt_screens`, `team_home`, `team_home_layout`,
`team_discover`, `team_motion`, `team_controls_start_run`, `motion_states`, `kit_states_scenes`,
`kit_scenes`, `search_go_to`, `search_index`, `command_palette_focus`, `inbox_screen`,
`this_phone_plain_failures`, `text_size`, `work_tab_status_line`, `codex_connection_banner`,
`revamp/screen_work_4`, `revamp/chat_3`, `revamp/slice_p5_1`, `model_picker`; `flutter analyze`
clean. Goldens regenerated with `TZ=UTC` and looked at: new kit galleries (calm state view, calm
status answer, countdown status line) and the screens that show these states
(`chats_home_empty*`, `chats_home_nomatch*`, `team_home_empty*`, `project_hub_no_project*`,
`work_projects_error*`, and the shell Files goldens, which were already stale from the Files merge
and now also show the calm empty folder). Pre-existing failures seen on this base (not from this
slice): `kit_manifest_test` (KitWheel has no gallery), `kit_draft_manifest_test`
(`schedule_sheet.dart` multiline field without a draft), `desktop_shortcuts_test` "Ctrl+F focuses
the Files find field" and `screen_shell_2_test` "a PC window gets the sidebar" (both fail with this
slice's UI files reverted to the base), `phone_agents_controller_test` "list permission …" (the
question card now repeats the command, from the chat-approval merge).

## 5. Not done here, and why

- **"Waiting to send" with a clock on the sent message (concept A).** The offline queue is real
  (`pending_sends_strip.dart`, `KitQueuedMessage`), but it lives in the chat library, which the
  chat-fidelity agent owns. Wanted there: while the link is down, a queued message shows under its
  bubble as one muted line "Waiting to send" with the clock glyph (KitQueuedMessage), instead of the
  composer's "Sends when OpenCode answers" header; once online, steering never queues.
- **Chips row while offline (A's mint Auto-approve and model chips).** Chat library; owner decision
  4 already moves both behind the composer's +.
- **"This phone's server is running · 410 MB" (B).** Left out: memory is only read on This phone by
  running a command in the in-app Linux, and the server's state is already the shell pill.
- **The AI Team task screen and team conversation** (team-task agent): their empty and offline
  states should take `KitStateView.calm` and the same countdown line; not edited here.
