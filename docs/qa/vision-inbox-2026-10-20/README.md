# Inbox: one calm list (vision slice, 2026-10-20)

Branch `feat/vision-inbox` (from int `f83625fb4`). Pick: **One list, with C's calm**
(`screen-concepts/b1-inbox/concept-a.png` for the structure, `concept-c.png` for the calm),
owner decision 1 (permission asks only are answered outside the chat, always with the exact
command; questions with options or free text open the chat) and decision 2 (the command and Reject
stay).

## 1. Technical-honesty audit (written before building)

The question per element: what does the app or the server really hold for it, and can the Inbox
show it without inventing anything?

Where waiting things live today:

1. **The server in use** (`ConnectionController`): pending `PermissionRequest`s with command, file
   or patterns (`awaitingPermissions`, `permissionsForSession`), questions, OC2 forms, agent cards,
   and the reply path `answerPermissionRequest` (held for the 3 s Undo window by
   `delayedAnswers`, then `respondPermission`). Its other projects (OC2's server-wide channel,
   `ElsewhereAttention`) are known only as "this conversation waits": ids, no request body.
2. **Side connections** (other saved OpenCode servers the Conversations list keeps open): each is
   its own `ConnectionController`, reached through `connectionForRow(item)` on its feed rows, with
   the same requests and the same reply path.
3. **Agents on this phone (Paseo: Claude Code, …)**: their rows are in the same feed;
   `permissionForFeedItem(item)` holds the permission with its command and the main controller
   answers it (the Conversations list already does this).
4. **Watched servers** (`ProfileMonitor`, opt-in): `MonitoredRequest` = id, kind, session, session
   title, folder. No action, no command, no reply path. `openMonitoredRequest` switches to that
   server (asking first when work is running) and lands on the request card in its conversation.
5. **Codex** profiles: no pollable pending list.

All of 1–3 already reach the UI as one list: `chatFeed(...)` rows with `status == needsYou`.

| Element of the pick | Source | Verdict |
|---|---|---|
| One list of everything that waits, across connections | Feed rows with `ChatStatus.needsYou` (server in use, its other projects, side connections, phone agents, subagents included) + the monitor's requests for watched servers that have no live connection | **Build.** One list; the watched servers' requests are deduplicated against the live ones by server. |
| Question as the title | `permissionRequestQuestion` (shared with the chat card), a question's own words (`QuestionPrompt.question`), a form's `title` | **Build** with those words. The permission title is whatever the shared presentation says ("Run this command?" today); the chat-approval slice owns that wording, so a sharper "Run npm install?" arrives here by itself. A question whose text is a plan (Markdown), an agent card and a request known only by id get one plain line ("Asks you a question" / "Waiting for your answer"). |
| Where it is: server · project · conversation | Server: `serverDisplayName` of the row's profile (the switcher's names), or the agent's name for a phone agent row; project: `ChatFeedItem.projectName`; conversation: its title | **Build**, plain, one caption line with the amber dot. A watched server's row: server · folder name · session title, each only when the monitor kept it. |
| The exact command or path in one mono line | `permissionSummary`: `commandPreview ?? filePath ?? patterns` | **Build** for permissions (the row variant's mono line). Nothing for questions. |
| Allow once / Reject on the row | Server in use, side connections, phone agents: `answerPermissionRequest` with the held Undo window, gated on `capabilities.permissionRequests` (phone agents: the feed's own permission) | **Build.** The first row's Allow once is the screen's one mint action; every later row's is secondary. "Always allow" stays in the chat (its confirm lives there). |
| Questions with options or free text | Real, but owner decision 1 | **"Open"**, which opens that conversation on its card (`ChatsHost.openChat`). No option chips on the row. |
| A request on a watched server | `MonitoredRequest` has no command and no reply path | **Not answerable here.** Row says what kind it is with **Open**, which is `openMonitoredRequest` (switches server, asks first if work runs, lands on the card). Never Allow once on something the app cannot read. |
| A conversation in another project that waits (server-wide channel) | Ids only, no request body | **"Waiting for your answer" + Open**, which moves to that project and opens it. |
| "Waiting 18 min" / "since 10:42" | OC1 and OC2 `Permission.Request` carry no creation time; Paseo and Codex carry none. The app keeps one first-seen time per request on this phone (`requestFirstSeen`, `lib/ui/screens/chat/permission_sheet.dart`), the same clock the chat card's "Needs you · 20 s" uses. | **Built (2026-10-21):** every permission row's place line ends "· waiting 4 min", counted from when this phone first showed the request (the vision page allows a client-side elapsed time). It is not the time the agent asked: a request first seen after a reconnect counts from then. No "since 10:42" clock time. |
| Oldest first | No ask time (above) | **Adapted:** ordered by the conversation's last activity, oldest first (a waiting conversation's last change is when it stopped to ask); watched servers' requests after them. Each row's wait counts from first seen on this phone (above), so the order and the ages can disagree. |
| Answers use the same held Undo window | `delayedAnswers` (3 s) through `answerPermissionRequest`; the row variant draws the held answer with Undo | **Build**, unchanged from the chat and the server sheet. |
| Row rises and leaves gently | `KitAnimatedRows` (folds away, instant under reduced motion) | **Build.** |
| Empty: "Nothing waits for you" | Only true when every source was read | **Build**, with the honest qualifier: a side connection that isn't answering, or the server in use not connected, shows the kit status line ("Home PC isn't answering · Try again") so the calm line never claims a server it could not read. |
| What finished while away | `ChatFeedItem.finishedUnseen`: the run finished after the person last opened the conversation (server watermark on OC2, a run this connection watched end elsewhere) | **Build, only in the empty state**, as "Finished, not opened yet" rows that open the conversation. Worded as what the flag means, not "while you were away". |
| "Auto-approved 6 reads · Undo" (concept A) | A read cannot be undone | **Rejected** (vision). |
| Undo a finished task | `stagedRevert` (OC2 only), a separate screen | **Not on this screen** (no "Undo" on finished rows; the conversation and Changes hold it). |
| Sort control, section headers, per-item cards, "1 of 3" deck | — | **Removed** (vision). |
| Entry: home "Needs you · N" chip | Was a filter toggle on Conversations | **Build:** the chip opens the Inbox; its count is the Inbox's own count (all projects and servers), so the number on the door matches the list behind it. |
| Entry: Search › Go to | `goToCommands` | **Build:** a "Needs you" row with the count, opening the Inbox. |
| Entry: notification, Quick Settings tile, widget, team gate links (`OpenChatsIntent(needsYou: true)`) | Shell intent | **Build:** the shell selects Conversations and opens the Inbox over it. |
| Bottom dock with an Inbox badge (concepts) | There is no bottom bar on a phone (owner, 2026-10-20) | **Image artifact.** Ignored. |
| Laptop / desktop glyphs, invented servers (concepts) | A profile has no device kind | **Not used**; the place line names the server in words. |

## 2. What was built

- `lib/ui/screens/inbox/inbox_items.dart`: the one model. `inboxEntries(controller)` = feed rows
  that wait (`ChatFeedFilter(needsYou: true, includeSubagents: true)`), oldest activity first, each
  with its answerable permission and owner (the same owners the Conversations list answers through:
  side connection, phone agent on the main feed) or the question's / form's own words; then the
  watched servers' requests with no live connection here. `inboxCount` (the doors' number) and
  `inboxFinished` (finished, not opened, newest first).
- `lib/ui/screens/inbox/inbox_screen.dart`: `InboxScreen` / `openInbox`. Bar title "Inbox" with
  "{n} waiting" (what is left to clear; a held answer no longer counts). One panel of rows,
  hairlines between, rows enter and fold away with `KitAnimatedRows` (instant under reduced
  motion). Permission rows: `permissionRequestCard(...).asRow(place:, onOpen:)` with the chat's
  held answer and Undo; the oldest permission's Allow once is the one mint action. Question / form /
  unknown rows: the question and one Open (opens the conversation through `ChatsHost.openChat`).
  Watched rows: "Asks for your permission" / "Asks you a question" with Open
  (`openMonitoredRequest`: switch, confirm if work runs, land on the card). Empty: the big calm
  `KitStatusAnswer` "Nothing waits for you" (mint dot), with "Finished, not opened yet" rows under
  it when there are any; when a server isn't answering the answer gets a hollow mark, the line
  "{server} isn't answering, so it may have more" and Try again. Over a list, the same server shows
  in the kit status line with Try again. Pull to refresh.
- Kit, variants only (other screens look the same; their goldens are unchanged):
  `KitRequestCard.asRow(place:, onOpen:, openLabel:, openKey:)` (spec added to
  `docs/ux-system/kit-api/KitRequestCard.md`): amber place dot + "server · project · conversation",
  question in `headline`, place + question one open target, a no-answers row with one Open at the
  end of the question's line, the place kept above the answered line (dot turns `text3`).
  `_summary` moved into the row part (the card file stays under 1,500 lines).
  `KitFilterChip.opens` (an action chip in a filter row: button, never a toggle).
  `KitAnimatedRows`: a leaving row's tickers stop (`TickerMode`), so a row that folds away while
  still showing its Allow once is not counted as a second primary by `KitScreen`'s check.
- Doors: the Conversations "Needs you · N" chip opens the Inbox (count = the Inbox's count, every
  project and server; was a project-filtered filter toggle). `OpenChatsIntent(needsYou: true)`
  (notifications, Quick Settings tile, widgets, team gate links) and `HomeScreen(initialChatFilter:
  needsYou)` select Conversations and open the Inbox over it. Search › Go to has a first "Needs you"
  row with "{n} waiting" / "Nothing waits", also found by "inbox". `ChatsHost` gained `inboxCount`
  and `openInbox` (fakes in `test/support/chats_fakes.dart` and widgetbook updated).
- Copy (en, ar; gen-l10n run): `inboxTitle`, `inboxEmptyTitle`, `inboxEmptyPartialLine`,
  `inboxFinishedLabel`, `inboxOpenLabel`, `inboxOpenOnServer`, `inboxWaitingForAnswer`,
  `inboxAsksPermission`, `inboxAsksQuestion`, `inboxUnreachable`, `kitRequestOpen`,
  `goToNeedsYouHint`.

## 3. Visual passes against the pick (concept A's list, concept C's calm)

Renders: `/home/eslam/Storage2/launch-footage/vision-inbox/` (final), with `before/` (the
Conversations list on its old Needs you filter) and `pass1/`…`pass4/` kept as the record. Capture:
`tool/capture/vision_inbox_test.dart` (real HomeScreen, the Inbox opened from the chip, blurred
shadows, 412x915 at 2x).

- **Before:** the Inbox was a filter on Conversations: the chip, a "Today" label, a row per
  conversation with a full amber card under each, question cards with radio lists, "Always allow
  commands like this", a floating + over the answers.
- **Pass 1:** one panel, place line, question, mono command, Reject / Allow once, Open. Gaps: the
  dot drew as a big white disc; Open rows sat on their own line (tall); the empty state was a small
  icon tile and a left-aligned line, nothing like C's calm; the not-answering empty state claimed
  "Nothing waits" under a status line saying a server was down.
- **Pass 2:** small amber dot; Open beside the question; the empty state became the big centred
  answer (C's calm); "4 need you" under the title. Gaps: the bar's needs-you span drew a stray
  trailing "·"; Open shared its line with the place line, which then wrapped; a large gap above the
  panel; Arabic put the "?" of a server question on the wrong side and ran the place line LTR.
- **Pass 3:** "4 waiting" in the quiet tone (each row already has its amber dot); Open moved to the
  question's line only; the panel sits right under the bar; every server-chosen name isolated, the
  place line in the page's direction; the dot centred on the first line at any text size. Gap: the
  partial empty state's big answer ran to three heavy lines.
- **Pass 4:** "Nothing waits for you" stays the answer, with the hollow mark, the server named in
  the line under it and Try again (no second status line); the dot grows with the text; an answered
  row keeps its place line (grey dot) so you see which you answered; the count under the title
  drops while an answer is held.

Final images (one full phone screen each): `main-dark.png`, `main-light.png`,
`allowed-undo-dark.png`, `empty-finished-dark.png`, `empty-finished-light.png`,
`empty-not-answering-dark.png`, `one-server-dark.png`, `small-320-text2-dark.png`,
`arabic-rtl-dark.png`.

Left as the kit and the vision say, on purpose: rows on one panel, not a card each (vision rule);
Reject as the quiet text answer and Allow once as the pill (the kit row, as on the server sheet);
the bar title instead of C's page-size "Inbox" (a pushed page names itself in its bar, as This phone
and Settings do); no wait time (section 1); no dock (no bottom bar on a phone).

## 4. Verification

- New: `test/inbox_screen_test.dart` (every waiting thing oldest first with place and command,
  permissions answer and the question only opens, count; Allow once held with Undo and Undo keeps
  it, Reject sends after the window and the row leaves; a watched server only opens; empty with and
  without finished rows; a waiting row is never "finished"), Inbox-row cases in
  `test/kit/kit_request_card_test.dart`, `KitFilterChip.opens` in `test/kit/kit_filter_chip_test.dart`,
  gallery shots `kit_request_card_inbox_row_{dark,light}`.
- Updated: `test/chats_home_test.dart` (the chip opens the Inbox and counts every project),
  `test/home_navigation_test.dart`, `test/launch_session_shortcut_routing_test.dart` (Needs you
  opens the Inbox over Conversations), `test/search_go_to_test.dart` (the Needs you row);
  `test/goldens/chats_home_golden_test.dart`: the unreachable "Needs you only" scene became
  "Running only" (`chats_home_running_*`; `chats_home_needs_*` removed), `chats_home_nomatch_*`
  regenerated (the chip now counts 2 across projects). Goldens regenerated with `TZ=UTC` and looked
  at; the other KitRequestCard goldens are unchanged.
- Passed (serial): the files above plus kit ratchet (G1/G15/G16/G17/G21), kit manifest (G4), design
  standard, glossary (G28), file size, architecture boundaries, l10n coverage and new locales, kit
  motion, server switcher (both), shared servers (and goldens), phone server card, kit filter chips.
  `flutter analyze`: no issues; widgetbook `flutter analyze`: no issues.

## 5. What the Inbox still cannot do honestly

- **A wait time.** Needs a per-request first-seen time in the connection library, recorded from
  `permission.asked` (the OC2 envelope's `created`) and kept across the pending-list reread; until
  then the row says nothing about age.
- **Answer a watched server's request in place.** The monitor reads the permission and drops its
  action and command (`MonitoredRequest`), and there is no reply path per profile.
- **Question title (after the int merge).** Titles come from the chat card's own
  `permissionAsk` without the project (the place line names it): "Run npm install?"; a command
  too long for a title reads "Run this command?" with the exact command under it.
- **Merge note.** int deleted the server sheet's row part and split the card; the Inbox row is
  re-applied as `kit_request_row.dart` (a part, Inbox-only, `asRow(place:, onOpen:)`), its dot on
  `KitTokens.needsYouDotSize`. `KitPageHeading` (from int) gained its reduced-motion samples and
  English/Arabic overflow scenes, so `kit_motion_test` and the overflow gate and matrix pass.
