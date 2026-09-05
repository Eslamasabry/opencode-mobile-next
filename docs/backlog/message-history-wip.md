# Preserved message-history pagination work

This is an unfinished checkpoint, preserved when the user requested a clean dev/master baseline. It is not ready to merge or release.

Base: `45ce2c0933bcafe96d2c4e28df9f564d1af9684a` (PR #67).

Implemented so far: chronological message-page gateway contracts, v1 header/Link continuation, v2 descending page transport, chat older-history loading and merging, timeline loading, partial context/export labels, and fixture migration.

Review findings to resolve before integration:

- Unknown message part updates currently create a newest assistant placeholder. Establish message membership, role, and ordering before rendering edits to unloaded history; preserve normal part-before-message streaming.
- The timeline snapshots its rows and can become stale when the parent resets after reconnect or compaction. Reconcile it with current history while retaining search input.
- Context retries HTTP 400/410 expired cursors indefinitely. Recover through recent-history reload and preserve visible metrics.
- Copy-complete-reply can overclaim completeness when a page splits a reply. Context totals also need to distinguish loaded message counts from server-reported usage.

Also verify head-refresh errors over cached messages, scroll anchoring when an anchor disappears, overlapping refreshes, reconnect invalidation, and pinned reading position during older-page loads and live updates.

Validation status: the latest focused run passed 31 tests but failed to compile chat_live_events_test.dart because ApiException was incorrectly const. That constructor call was subsequently fixed; the run was not repeated. A newly added scrollable_positioned_list test import is unused pending scroll coverage. Do not claim clean analysis or passing complete pagination checks.

Scoped session-inventory pagination remains separate unfinished work. Existing release-readiness and parity gates remain open.
