# Phone-agent rows stuck on "Working" (2026-10-20)

Branch `fix/agent-rows-stuck-working`, based on int `c7ff5ec28`.

## Symptom

On the owner's phone, the Conversations list showed four Claude Code
conversations from two days earlier as "✳ Claude Code · Working ·
tip-calculator · 2d ago". None of them was running. Other Claude Code rows from
the same days showed no state. Claude Code runs on the phone as a phone agent:
built-in Linux, through the Paseo helper. It does not go through OpenCode 2.

## Root cause

The row's state comes from the Paseo daemon's agent `status`. The app believed
that status even when nothing was running.

1. `lib/ui/screens/chats/chats_home_screen.dart:580` draws "Working" for
   `ChatStatus.running`.
2. `lib/paseo/chat_feed_source.dart:225` sets `ChatStatus.running` when
   `gateway.sessionStatuses()` says `busy`.
3. `lib/paseo/gateway/sessions.dart:47` (before the fix) set that status from
   every list or fetch snapshot through `paseoSessionStatus(agent)`.
   `lib/paseo/mappers.dart:234` (before the fix) mapped `running` and
   `initializing` to `busy` with no other check.
4. The daemon, checked in the Paseo 0.9.1 source installed on the PC, answers
   for any agent it has not loaded from that agent's saved record.
   `agent-projections.js:168` sends `status: record.lastStatus`, and
   `agent-projections.js:37` saves `lastStatus: agent.lifecycle` each time
   the record is written. After the helper restarts, no agent is loaded, so
   every agent comes from its record (`session.js` `listAgentPayloads`). Only
   archiving resets a saved `running` (`agent-archive.js:14`). Nothing does it
   at startup.

When Android stops the helper with the app, or the phone restarts during a
turn, the record keeps `running` forever. The agent process is gone, and the
record is not rewritten until the conversation is opened. Every list read
after that, on every app start, reported the conversation as busy.

Hypotheses ruled out:

- **The app's own cache.** `oc.agentFeed.<profile>`
  (`phone_agents_cache.dart`) never stores a status. Saved rows are always
  decoded as idle.
- **A lost turn-end event.** `_recoverNow` (`gateway/lifecycle.dart:58-66`)
  clears the stream's turn state on every new socket, and the next list read
  decides. The gateway is in memory only, so an app restart starts it empty.
- **The OpenCode 2 `_feedBusy` snapshot.** That was fixed in 58343f841. Phone
  agent rows do not use it.

## Fix

The fix is in `lib/paseo`, where the status comes in. A busy status now needs
live evidence:

- **A loaded agent.** Paseo always sends `activeTurn` for an agent it holds in
  memory: the turn, or `null` (`toAgentPayload`). A saved record never has it.
  A loaded agent's running status is believed at any age, so a long turn still
  shows Working.
- **A turn this gateway saw start on the stream** (`_turnActive`), or a prompt
  that was accepted but whose turn has not started yet (`_awaitingTurn`).
  These already overrode the snapshot.
- **A saved record that changed recently.** A running status without
  `activeTurn` is believed for `paseoRecordBusyFreshFor` (3 minutes, the same
  bound as `_openTurnGrace`) after the record's `updatedAt`. After that it
  reads idle. If the bound runs out with no newer snapshot, a timer reports
  the session idle and sends `session.idle`, the turn-end event, so the list
  reads the daemon again (reconcile by reading, not by trusting the flag). A
  record with no readable `updatedAt` is never believed.

Changed files:

- `lib/paseo/mappers.dart`: `paseoRecordBusyFreshFor`, `paseoSessionStatus`
  (now checks for live evidence), and `paseoRecordBusyUntil`.
- `lib/paseo/gateway/sessions.dart`: `_remember` applies the rule and arms the
  expiry timer. `_recordBusyExpired` and `_cancelRecordBusyExpiry` are new.
  `_forget` cancels the timer.
- `lib/paseo/gateway.dart` and `lib/paseo/gateway/lifecycle.dart`: the timer
  map, which is cleared when the folder changes and when the gateway closes.

The chat's own reads use the same rule. `messages()` no longer opens a stale
record with a running reply, and `_watchUntilIdle` ends on it.

No UI or copy changes.

## Tests

New tests:

- `test/paseo_stale_working_test.dart` (7 tests). They cover a fresh gateway
  (app restart) given a record that has said `running` for two days: the row
  and the session status are idle. A loaded agent whose turn has run for two
  days stays Working. A turn seen on the stream stays Working over a stale
  record. A recent record is Working only until its bound, then reads idle by
  itself with a `session.idle` event. The rest are unit tests of the mapper.
- `test/phone_agents_controller_test.dart`, "after a restart, a conversation
  whose helper stopped mid-turn two days ago is not Working". This runs the
  whole controller path: saved rows, including one with a stray
  `"status":"running"`, then the helper's live rows, with a stale record
  beside a loaded long-running agent.

Fixture updates. The fake daemon payloads for running agents now carry
`activeTurn`, as the real daemon sends for a loaded agent:

- `agentJson` in `test/paseo_gateway_test.dart`, with a new `loaded` option
  for saved records.
- `_agent` in `test/phone_agents_controller_test.dart`.
- One running agent in `test/support/phone_agents_genui_tests.dart`.

### Failing first

The fix was reverted by making `paseoSessionStatus` map running to busy again
and `paseoRecordBusyUntil` return null:

- `paseo_stale_working_test.dart` had 5 of 7 tests fail. Each stale or expiry
  case gave `Expected: ChatStatus.idle, Actual: ChatStatus.running` or
  `Expected: 'idle', Actual: 'busy'`. The two "stays Working" tests passed.
- The new controller test failed at line 1328 with
  `Expected: ChatStatus.idle, Actual: ChatStatus.running`.

With the fix restored, all of them pass.

### Results with the fix

Checks used the pinned Flutter 3.47.1. No emulator was used.

- 59 affected test files, run with `flutter test --no-pub -j 3`:
  `test/paseo_*_test.dart`, `test/phone_agent*_test.dart`, `chat_feed_*`,
  `chat_feed_test`, `chats_home_test`, `merged_chat_feed_test`,
  `side_connections_test`, and the attention and work-status tests
  (`elsewhere_attention`, `monitor_attention_reader`, `attention_feed`,
  `profile_monitor_attention`). Result: **+919, all passed**.
- `flutter analyze lib test` found no issues. The full-repo `flutter analyze`
  reports errors only under `widgetbook/tool/` (analyzer packages that are
  not resolved in this worktree). This change does not touch that code.

## What this does not cover

- **A daemon that sends no `activeTurn`.** On such a daemon, a turn started
  elsewhere, and longer than 3 minutes since its record last changed, reads
  idle until this app sees a turn event. Paseo 0.9.1 sends `activeTurn` for
  every loaded agent. 0.11.2 (the owner's phone) was not inspected, because
  only 0.9.1 is installed here.
- **A running conversation on another device or another Paseo host.** If its
  daemon holds the agent, `activeTurn` keeps it Working. If that host
  restarted mid-turn, it gets the same 3-minute bound.
- **The helper's idle-stop inventory** (`gateway/idle_work.dart`). It still
  counts a saved `running` as work in progress, so a stale record can keep
  the phone's helper from stopping when idle. This was left unchanged on
  purpose: that inventory must never call live work finished. A follow-up
  could use the same `activeTurn` evidence there.
- **The daemon's own record.** It stays `running` until the conversation is
  opened. The app no longer believes it, but other Paseo clients may.
