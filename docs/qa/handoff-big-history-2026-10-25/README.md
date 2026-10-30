# Continue with… on chats that hold screenshots (2026-10-25)

## Report

Owner, APK 3440, OC 2.1.1 on the phone: "Continue with…" from the
"Design review" chat (IPTV_King) to Claude Code · Opus 5.5 showed
"Couldn't read the conversation. Try preparing it again."

## Cause

The handoff reads the full history through the OC2 agent-card recovery
pages (`Api2GenUiHistory.genUiHistoryPage`), which refuse any response over
1 MB (`genUiHistoryByteLimit`). The owner's chat (copy of the phone database
taken earlier that day) has 181 messages; the oldest 50-message page is
3.55 MB because two assistant messages are 1.59 MB and 1.79 MB — `read`
tool results carrying PNG screenshots as `data:` URIs. Any refusal maps to
`AgentHandoffHistoryFailure.readFailed` → "Couldn't read the conversation".

The record itself never carries images: tool output is shortened to 512
characters and attachments are excluded (`agent_handoff_record.dart`).

## Fix

`Api2GenUiHistory` now implements `AgentHandoffHistoryGateway`: a complete
history read with 16 MB pages, a 64 MB total and a 30 s page deadline. Card
recovery keeps its 1 MB pages. Branch `fix/handoff-big-history` (44bb92ad9),
merged at aa602e95a.

## Verification

| Check | Result |
|---|---|
| `test/gen_ui_history_test.dart` new case (a page with one >1 MB screenshot message, two pages) | passes; fails with the fix removed |
| gen_ui_history + agent_handoff_history + agent_handoff_controller tests | 50/50 pass (local, pinned Flutter) |
| `flutter analyze` on changed files | clean |
| Emulator, 3460: chat with a 2 MB screenshot `read` result opens and renders; next send ran and stopped with the new usage-limit copy (free quota) | pass (`launch-footage/qa-stability/emu/h04, h09, h10`) |
| Emulator end-to-end handoff | not possible: no signed-in agent there (Codex signed out, Kilo/Cline model unavailable); only the owner signs in |
| Owner phone | 3460 installed over adb; phone locked, so the handoff retry is the owner's |

## Second report (3460, 14:16–14:17)

Reading now worked (preview, record about 93 KB). Two later failures:

1. **"Couldn't save the record. No message was sent."** The Paseo writer
   re-masks the whole record and refuses any change. Pieces are masked one by
   one and then JSON-quoted; in this chat a file list had
   `lib/l10n/app_localizations_ar.dart` (34 chars, masked) followed by
   `lib/l10n/app_localizations.dart` (31 chars, not masked). Quoted, the line
   break became `\nlib/l10n/app_localizations.dart`: 32 characters for the
   opaque-token mask. Reproduced with the chat copy: re-mask changed the
   record at byte 38218. Every attempt on this chat would fail.
   Fix: the builder repeats the opaque-token mask on the whole record until
   stable; the writer checks only that mask (whole-record credential masking
   was destructive: a masked `api_key=` swallowed the rest of the record).
2. **"The server or project changed. Start again."** after the preview. The
   folder sync keeps at most four agent folders (last used, folders with saved
   Claude conversations, then others); a row refresh closed the connection the
   sheet opened for IPTV_King. Fix: the sheet holds its folder until it closes.
   Test with three other Claude projects: fails without the hold.

Both: scope/file problems now offer Prepare again and name the failed check
or step under Details. Branch `fix/handoff-target-hold`, merged at b98fbed37.
Affected tests (11 files, 319 tests) pass locally; analyzer clean.

## Third pass (3480 on the phone 19:33; emulator sweep, evening)

1. **"Claude Code isn't ready on this phone yet."** with no way forward
   (owner screenshot). The helper was stopped by the app update; the row now
   has Try again (e893510e8, in 3480).
2. **Kilo/Cline "This model isn't available. Choose another one."** Two
   causes. Neither is certified for chat, so New conversation leaves them out;
   Continue with now offers the same agents (05b1fab49). And the helper lists
   an agent only after asking its CLI for models (slow the first time): an
   empty list is now "isn't ready" with Try again, not a dead end (ebc995261).
3. **Raw error text in an agent chat.** A signed-out Kilo chat (started with
   Use Kilo) showed `[System Error] Internal error: You need to sign in to use
   this model. code: -32603 … data={…}` as three reply paragraphs. Paseo's ACP
   bridge writes RPC failures into the reply text. Such replies are now an
   error row (text under Details); one asking to sign in is a sign-in error
   with Sign in again (f7a8bf49d).
4. **Cards from agents: "The tools cards need aren't on this phone yet."**
   Cards work through Claude Code; on a phone without it the note now says
   "Install Claude Code on this phone first." (30a168df6).

Checks: affected test files pass (phone agents, Continue with sheet, handoff
controller, agent cards, Paseo mappers and gateway); `flutter analyze` clean.
Verified on the emulator with 3490: Continue with lists only GLM (no Kilo,
Cline or Codex rows); the Cards note says "Install Claude Code on this phone
first."; a signed-out Kilo chat shows an error row with the raw text under
Error details.

## Fourth pass (3490/3500 on the emulator)

5. **Kilo's sign-in error still read as a generic error.** Live replies reach
   the chat as message JSON, which carried only the error text; the kind was
   lost. The JSON now names it (`25a0f97eb`).
6. **Two "agent stopped" notices.** Paseo's turn_failed raised the banner
   although the reply already was the error row; its fixed text also said
   "Check it on your computer" for phone agents (`c40e09338`).
7. **"Tool" with no pattern** for an OpenCode 2.1.1 glob answered after the
   app was reopened: a tool result whose start came before the reconnect
   had no name or input. Such a result now rereads the transcript
   (`13366168a`).
8. **"Model → big-pickle"** after a model switch: the marker now shows the
   catalog name, "Model → Big Pickle" (`f375ee90e`, verified on 3500).
9. **Phone server off for two hours after the 19:33 install.** The restore
   budget (three tries) was only reset by a manual Start; the app now resets
   it when it is open and the server passes its health check (`c7e3c8ad2`).
10. **Setup polled every minute all session.** The server auto-updater's host
    listened to the setup engine, which counts as a watching page; it now
    reads the engine only when it decides (`c8c3e1f48`). This also cleared
    eight app-level widget tests that failed on a pending timer.

OpenCode 2.1.1 permission flow on the emulator (Ask first, glob): the card
appears, survives leaving and reopening the app, and Allow once completes the
search.

## Fifth pass (review, full suite)

11. **The restore-count reset could undo a crash loop.** Codex review: a
    server that passed one health check and then crashed again got three
    fresh tries every time. The count now resets once per app visit, only
    when nothing is restoring, and only after the server is confirmed
    running (`43b2099aa`).
12. **A message sent from a little way up the chat stayed out of view.** The
    jump to the newest message only ran beyond 480 px; any offset now jumps
    (`a1de5069d`).
13. **The phone card's status line was hard to read over the aurora** in the
    Glass and Crystal styles; its words now sit on the kit plate
    (`021c4a7a1`).

Full suite on the build server at `c40e09338`: 41 failures in 27 files. Rerun
at `0e03d3f98`: the app-level timer tests, analyzer suppressions, Continue with
goldens and credential status all pass. The rest were:
- eleven team chat goldens: clock times from UTC fixtures printed in the
  server's zone, which left summer time today. The build server now runs tests
  in UTC like CI (`6aff16658`);
- two composer goldens still drawn with the old one-row composer
  (regenerated, `6aff16658`);
- the native admission fixture's 50 ms budget, too short on a loaded machine
  (`7e6c62631`);
- repository hygiene, which needs `.git` and so cannot run on the build server
  copy (passes locally).

## Sixth pass (3520, 10-26)

- The phone server stayed off after every install since 3440: each install
  is a package update, and the restore on update or boot refused once the
  three-try restart count was used up, which it never got back. A boot or
  update now starts a settled count over; a try still in flight is kept
  (`b138eb525`, Codex-reviewed fix `7efd7994b`, 23/23 budget tests).
- Emulator proof (count set to 3, app force-stopped, `pm unstop`, then
  `adb install -r`): 3500 left the server off after 75 s (control); 3520
  had `opencode2` up within 30 s and the count read 1 (pending). A first
  attempt without `pm unstop` proved nothing: a force-stopped app never gets
  the update broadcast.
- Owner phone (dozing, screen off, server off since the 19:33 install):
  3520 over adb at 03:01; `opencode2` up 3 s after the app process, still up
  at +2 min.
- New conversation no longer offers sign-in for uncertified agents (Codex,
  Kilo, Cline); stopped agents still show (`bb4bd0e84`).
- Emulator: Conversations loads, a send in "Running date command" got its
  reply. The agent helper starts when the app opens, as designed.
- Full suite at `7efd7994b` on the build server: 22,696 passed, 7 failed:
  five repository hygiene (no `.git` there) and two timing failures under
  load (`termux_setup_native_test` interrupted launch,
  `widget_action_runner_test` reject reconcile) that pass 3/3 alone.

## Seventh pass (3530–3550, 10-26)

- Agent chats jumped to Today and agent processes started whenever the app
  opened. Two causes, both loading idle agents through the helper:
  - Agent cards recovery read the history of the latest agent chats; each
    read loads the agent, whose events bumped its time and left "Some
    conversations couldn't load". Recovery now skips an idle agent whose last
    prompt it already read and that holds no card (`902399baa`,
    `e25778df3`; two Codex review rounds).
  - The phone-idle check asked for the sub-agents of up to 64 agents after
    every list read, and Paseo answers by loading each parent. A saved agent
    the helper has not loaded runs no process, so it now counts as covered
    and not busy without asking (`a2bbbed06`; Codex review found the
    reboot-mid-turn case).
  - Found with throwaway logging builds 3541/3542 on the emulator (no
    request from the app named the loads until the sub-agent scan was
    logged).
- Emulator, 3550: 150 s after opening, no `kilo` process (3520–3542 started
  two); no "couldn't load" line; Kilo chats keep their real times; a send in
  an OpenCode chat got its reply.
- Owner phone: 3550 over adb at 07:11 while dozing; `opencode2` back after
  ~2 s.
- Tests: card/agent selection 2,662 + 180 focused; Paseo/agent/idle
  selection 5,443 on the build server.

## Eighth pass (3560–3570, 10-26)

- After a send from a little way up, the prompt and reply stayed under the
  composer and phrase chips until scrolled (3550, emulator). Three parts:
  the jump to the newest row used alignment 0, flush with the list's edge
  under the composer (`8c614c58d`, 3560, not enough alone); OC2 delivers each
  prompt through the inbox, which reloads recent history and restored the
  anchor captured mid-jump; and rows landing after the jump fell below it.
  A send now follows the newest row, kept above the composer, until the
  person drags (`9064fc102`); a reload no longer restores an anchor at the newest row or
  one taken before a jump. The earlier test only checked the list's rect,
  which reaches behind the composer; the new tests measure against the
  composer.
- Emulator, 3570: "Running date command", scrolled up, sent; prompt and
  reply ("lime") both above the chips. Chat tests 1,485 on the build server.
- Owner phone: 3570 over adb at 08:17 while dozing; server back after ~3 s.
- Build server disk was 97% full: 86 stale worktree copies removed (now 44%).

## Open

- Kilo chats show Kilo's own "Auto Accept" chip next to Ask first, with the
  helper's wording ("ACP permission prompts") and its name shown while off.
  Kilo is not certified; left as is.
- Kilo's sign-in check reads a stored key file and says "Signed in", but its
  default model (Kilo Gateway) needs a Kilo account. Kilo is not certified for
  chat; left as is.
- A Kilo chat whose agent the helper restarted cannot send ("The request
  could not finish.") and offers no new chat: the helper still lists the old
  agent, so the app treats it as reopenable. Kilo is not certified.
- The phone has a secure lock, so Continue with on the owner's Design review
  chat still waits for the owner to try it.
