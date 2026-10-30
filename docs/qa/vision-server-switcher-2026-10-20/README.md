# Server switcher: a sheet near the thumb (vision slice, 2026-10-20)

Branch `feat/vision-server-switcher` (from int `247e2220e`). Pick: **A + B's question line**
(`screen-concepts/b2-server-switcher/concept-a.png`, the question line of `concept-b.png`),
owner decision 1 ("permission asks only" are answered outside the chat, always with the exact
command; questions with options or free text open the chat).

## 1. Technical-honesty audit (written before building)

The question per element: what does the app or the server really hold for it, and can the sheet
show it without inventing anything?

There are four kinds of "other server" in this app, and they know very different amounts:

1. **The server in use** (`ConnectionController`): everything. Connection phase and since when,
   busy conversations, pending `PermissionRequest`s with their command, file or patterns, and a
   reply path (`answerPermissionRequest`: held with Undo, then `respondPermission`).
2. **Side connections** (`lib/state/connection/side_connections.dart`): every saved OpenCode
   server on this phone (in-app Ubuntu, Termux), plus servers elsewhere the person turned on in the
   Conversations list, each on **its own live `ConnectionController`**. Reached through the public
   `ChatListSources.chatListSources` (shown, unreachable), `retryChatListSource(id)` (Try again) and
   `connectionForRow(item)` on its feed rows, which hands back that server's controller with the
   same permissions and reply path as the server in use. This is how the Conversations list already
   answers other servers' requests in place.
3. **Watched servers** (`ProfileMonitor`, opt-in per server): a poll every few minutes.
   `MonitoredRequest` = `{id, sessionID, kind, title, directory, workspace}`. The poll reads the
   full permission (`readPermissions`) but keeps **only the id, kind and session**: no action, no
   command, no file, and the snapshot has **no reply path**. Status `current / unavailable / paused
   / wifiRequired / waiting / disabled`, `runningCount`, `checkedAt` (last *successful* read) and
   `nextCheckAt` (the next poll, with backoff after a failure). `refresh()` skips a server whose
   next poll is still ahead, so there is no "check this one now".
4. **Everything else**: a saved profile and nothing live.

| Element of the pick | Source | Verdict |
|---|---|---|
| Bottom sheet near the thumb | `showKitSheet` (bottom on a phone, panel on a wide window) | **Build.** Title "Servers" with close; no header icon tile (concept A). |
| One row per saved server, plain name | `ProfileStore.profiles`, `serverDisplayName` | **Build.** "This phone" for the in-app server, the saved name otherwise. |
| Current server: mint check | `controller.profile` | **Build.** A check at the row's end and "Connected" in mint; no solid mint row and no filled tile (pick: "check, not a solid mint row"). |
| Current server: "Connected / Connecting / Reconnecting / Isn't answering" | `connectionStatus.phase` (+ `since`) | **Build.** The same words as the home's server pill. |
| Current server: Try again when it isn't answering | `retryConnection()` | **Build.** |
| "Working · 2 min" (elapsed per server) | Busy *count* is public (`busySessions`). The first-seen-busy time lives in the controller's private notification state. | **Count only:** "2 working". The elapsed time is left out; a public getter on the controller would be needed (connection library, not this slice). |
| Waiting question on the row ("Run npm install?") with the exact command and Allow once | Server in use and side connections: `awaitingPermissions` + `permissionRequestCard` words + `answerPermissionRequest`. Gated on `capabilities.permissionRequests`. | **Build** for those two kinds: the question, the command in one mono line, **Allow once** (mint) and **Reject**, with the same held answer and Undo as the chat. "Always allow" stays in the chat (a standing grant asks for its confirm there). |
| Waiting question on a *watched* server's row | `MonitoredRequest` has no action, command or reply path. | **Not real.** The earlier audit still holds: the row says **"Needs you"** with **Open**, which switches to that server. Never Allow once on something the app cannot read. |
| Questions with options or free text, forms | Real for the server in use and side connections. | **"Needs you · Open"**, which opens that conversation (decision 1). |
| "Needs you · 2 min" (how long it waited) | `Permission.Request` (OC1 and OC2) carries no creation time; the app's own first-seen clock is not the agent's wait. | **Left out.** |
| Watched server not answering: "tried 12 s ago · Try again" | `status == unavailable`, `nextCheckAt`; no per-server retry. | **Adapted:** "Isn't answering · checks again in 4 min"; no Try again (it would do nothing until the next poll). The row's tap still switches to it, which tries it now. |
| Side connection not answering · Try again | `ChatListSource.unreachable`, `retryChatListSource(id)` | **Build.** |
| "Asleep" | No server, monitor or bridge reports sleep. The in-app server reports Stopped (`BuiltinLinux.status`). | **Not used.** The phone's own row keeps "Stopped" with its Start OpenCode. |
| Laptop / desktop glyphs | A profile has no device kind. | **Phone glyph for this phone, server glyph for the rest.** |
| A server with nothing live (not watched, not a side connection) | Only the profile. | **Name only**, plus "Enter the password again" when the saved one can't be read. No state is invented. |
| Codex profiles | No pollable pending list (`ProfileMonitor.supportsProfile` is false). | **Name only.** |
| Claude Code on this phone (Paseo) | `LocalAgentServerEntry` already reads its daemon and controls it in place. | **Kept as it is**, in the same group. Its requests are answered on the Conversations list rows. |
| OpenCode in Termux | `TermuxRunningServerEntry`, controls in place. | **Kept**, in the same group. |
| "Add a server" as one quiet row at the bottom | `ServersRouteRequest.add()` | **Build.** "Pair with a code" is not in the list (it lives behind Add a server). |
| "Manage servers" row | `/servers` | **Removed** (simplify): Servers is still reached from Settings › Server, Go to and search, and every row's tap opens it to connect. |
| Composer floating above the sheet, lost header name (concept A) | Image artifacts | **Ignored.** |

## 2. What was built (rework after the owner's review, 2026-10-20)

The first build put the whole question card (title, mono block, Reject / Allow once) inside the
waiting server's row; the owner crossed it out ("ugly af"): a calm list became a form. Now every
server is one compact row of the same height (`KitRow.roomy`, 64 dp), as in concept A: glyph,
name, one state line, one act.

- Server in use: "Connected · 1 working" in mint words and the accent check (no solid fill).
- A server that needs you: "Needs you · `npm install`" (the exact command, mono, isolated left to
  right) and a compact mint-tinted **Allow once** pill, only when one request waits, it is a shell
  command, the server takes permission answers, and the whole line fits beside the pill (measured
  at the row's width and text size). Otherwise ("2 need you · npm install", a command too long, a
  file edit, a question with options, a form): a neutral **Open** pill that opens the waiting
  conversation, where the full card with Reject lives. No Reject in the sheet.
- After Allow once: "Allowed · `npm install`" with an **Undo** pill while the answer is held (3 s),
  then "Allowed" until the server takes it. Undo is a pill rather than a word in the line so it
  keeps its 48 dp target without changing the row height.
- Not answering: "Isn't answering" with a neutral **Try again** pill (same pill as Open). A watched
  server: "Isn't answering · checks again in 4 min", no pill; "Needs you" with Open (switch).
- One panel with hairlines; "Add a server" on its own quiet panel; bare glyphs (`KitRow.glyph`)
  instead of tiles, closer to the concept; the needs-you dot now on the glyph too.
- Removed: `KitRequestCard.asRow()` and `kit_request_row.dart` (no request block in the sheet).
- Files: `lib/ui/widgets/server_switcher_sheet.dart`, `lib/ui/widgets/phone_server_card.dart`
  (`switcherRow` takes `askLine` / `askAction`), `lib/ui/kit/kit_row.dart` (`glyph(needsYou:)`),
  copy `serverSwitcherAllowLabel`, `serverSwitcherUndoLabel` (en, ar).

## 3. Visual passes against concept A

Renders: `/home/eslam/Storage2/launch-footage/vision-server-switcher/` (final); `before/`,
`pass1/`…`pass4/` (first build), `pass5/` (rework pass 1).

- Rework pass 1 (`pass5/`): compact rows, line + pill. Checked: all rows the same height; the check,
  Allow once, Open and Try again end on one edge; the long command ellipsizes and gets Open; at
  320 dp / 2x text the line wraps and the pill moves under it; RTL mirrors the dot and pills.
- Rework pass 2 (final): bare glyphs instead of tiles, Allow once as the accent-tinted pill (not a
  solid fill), neutral pills for the rest, Undo as a pill.

Final images: `main-dark.png`, `main-light.png`, `long-command-opens-chat-dark.png`,
`watched-servers-dark.png`, `current-not-answering-dark.png`, `question-opens-chat-dark.png`,
`allowed-undo-dark.png`, `one-server-dark.png`, `this-phone-current-dark.png`,
`small-320-text2-dark.png`, `arabic-rtl-dark.png`.

## 4. Verification

- `test/server_switcher_live_test.dart`: one compact row with "Needs you · npm install" and Allow
  once, the same height as its neighbours, no Reject and no card; Allow once answers on that
  server's own connection after the Undo window ("Allowed · npm install" + Undo); a long command or
  two requests get Open, which opens the conversation; a server that never asks shows no Allow
  once; Try again on an unreachable side; a question opens its conversation; a watched server says
  Needs you with Open and "checks again in 4 min".
- Updated tests and goldens (`test/revamp/goldens/server_switcher_sheet_*`, regenerated with
  `TZ=UTC`, looked at). Kit ratchet, design standard, glossary, file size, architecture, l10n
  coverage and new-locales tests pass; `flutter analyze`: no issues.
- Failing on the int tip `f83625fb4` without this slice (checked in a clean worktree), not mine:
  `test/phone_server_screens_test.dart` "On this phone" (3 tests, the This phone screen).

## 5. What the sheet still cannot do honestly

- Allow once on a server that is only watched: the monitor keeps no action, command or reply path
  (`MonitoredRequest` in `lib/state/profile_monitor.dart`).
- Open on a watched server switches to it but cannot land on the waiting conversation.
- "Try now" on a watched server: `ProfileMonitor.refresh()` skips a server whose next poll is ahead.
- "Working · 2 min": the first-seen-busy time is private to the connection library.
