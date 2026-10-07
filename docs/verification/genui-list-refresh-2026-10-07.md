# Agent cards: list refresh after answer — 2026-10-07

Base: `01f3076d`, branch `feat/genui-be`. The coordinator reported that an
answer from the Conversations list reached Claude on emulator APK 2170, but
the row retained its old age and position. This patch has automated evidence;
no new APK or device verification was performed here.

The finish line is that an answer delivered from the list refreshes its owning
phone feed and that the completed run refreshes it again, without opening a
chat. UI changes and changes to answer/protocol semantics are out of scope.

## Implementation

`answerGenUi` schedules the owning source refresh after a sent/confirmed answer.
Undo and pre-dispatch failures do not schedule it. The phone event subscription
also schedules the source on idle/turn-completion boundaries. Refreshes use the
existing source change stream, so the merged Conversations list receives fresh
activity timestamps, ordering and status.

Each source coalesces refresh requests over two seconds, like the existing
open-chat refresh. Only the owning source's bounded list is fetched. Pure delta
events do not trigger reads. A boundary arriving during a refresh schedules a
trailing read. `PaseoChatFeedSource.refreshAfterActivity` also waits for any older
read before starting a fresh one, so it cannot satisfy a completion with a
snapshot captured before that completion. Source identity, profile qualification
and disposal guards still apply; timers are canceled on source removal/disposal.
Refresh failures do not change the answer delivery outcome.

## Regression evidence

The new scenario is registered in `test/phone_agents_controller_test.dart`,
with its fixture and body in `test/support/phone_agents_genui_tests.dart` to keep
the parent below 1,500 lines. It uses the main controller and phone source,
never creates a chat backend, and supplies deterministic message history and
send results while exercising the real feed reads and event mapping.

Observed failing-then-passing sequence with the pinned SDK:

1. Before delivery refresh: expected activity `10:00`, actual old `08:00`.
2. After delivery refresh alone: expected completed activity `10:02`, actual
   `10:00` (proved the separate completion hook was needed).
3. With an older read paused across completion: expected `10:03`, actual
   `10:02`; fixed by forcing a fresh read after that older read.
4. Final scenario passes: Undo causes no read, delivery updates timestamp and
   row order, completion updates timestamp and idle status, duplicate idle
   boundaries coalesce to one read, token deltas cause no list reads, and a
   paused older read cannot leave the row stale.

## Final validation

Candidate SHA-256 (sorted changed/new Dart paths + NUL + contents + NUL):
`659368276cc891b4614b45744d09deada45abf2e29a5cb27187f21a8a21e36b6`.

All Flutter checks used the pinned Shorebird SDK and serial machine lock:

```text
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 test/phone_agents_controller_test.dart --plain-name 'list card answer refreshes'
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 test/phone_agents_controller_test.dart test/paseo_chat_feed_source_test.dart test/gen_ui_controller_test.dart
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh analyze -- <pinned-flutter> analyze --no-pub
```

Final focused/affected run: **55 passed** (26 phone-controller, 15 feed-source,
14 GenUI-controller), no failures or skips. Full analyzer: no issues. Pinned
Dart formatting used `--language-version=3.10`; `git diff --check` is clean.
All edited Dart files remain below 1,500 lines.

Frozen GenUI types, UI, native code, assets and dependencies are unchanged.
No full-suite run, commit, push, APK build, signing or release was performed.
