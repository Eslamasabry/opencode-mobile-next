# BA7 — owned watchdog timers and cancelled probe deadlines

Finish line: stall wakeups and probe deadlines belong to live controller/turn
lifetimes; every coordinator-named regression file passes after merging FE.
Non-goal: replacing deadline checks with frame polling, altering stall copy, or
changing UI/native files.

Base: requested git merge of feat/genui-fe completed cleanly as 98f48d33.
The branch contains earlier backend and owner/Cards corrections too.

Cause: every busy snapshot/notification scheduled a periodic watchdog, even
on controllers with no owned transport. Widget-test invariants run before
addTearDown controller cleanup. Disposing a real controller cancelled its periodic
watchdog but left an in-flight Future.timeout deadline pending for up to 10 s.

Fix: arm the production watchdog only alongside an owned event channel or
polling transport. The existing deterministic BA7 clock injection explicitly
owns test wakeups. Real static foreground screens retain the 45 s silence /
5 s cadence / 10 s budget; no frame-loop replacement or test-global switch.
Probe deadline is explicitly cancellable; idle/deletion/error of the last turn,
controller disposal, disconnect, transport retirement/generation replacement,
and suspension cancel outstanding deadlines. Backgrounded-but-connected
controllers cannot rearm through notifications. Late evidence is fenced by
tracker identity, generation and the exact cancellation owner.

Regression coverage includes snapshot controllers and six pending-probe ending
paths; existing BA7 60 s classification and late-result tests remain in place.

`test-manifest.txt` lists all 18 named test targets, plus the golden variant of
slice_p10_3, all six text-scale matrix files, and three targeted BA7/auth test
files. All are run serially through the shared machine lock; never the full
repository suite. Validation and candidate hashes follow below.


## Regression proof

With the connection implementation restored to merge 98f48d33 and the new tests
retained, all seven regressions failed specifically with Pending timers. The
probe starts at the final tick (44 s + 1 s), leaving its ten-second deadline
unresolved at lifetime end. Restore-and-run of those seven tests passed.
`negative-control.txt` records the failures; there was no compile-error shortcut.
The broader focused three-file run (domain/controller/Settings) passed 93 tests
before the probe test timeline was tightened; final verification uses the
corrected pending-deadline tests in the 28-file manifest.

A read-only review confirmed the event channel is owned before stream.start,
so live disconnected/reconnecting channels continue to support diagnosis. No
frame polling, root-zone timers or global test-only disable switch were added.


## Final verification

- Requested FE revision 281356fb was merged (no rebase) into this branch at
  98f48d33, retaining previous BA commits.
- Final unchanged candidate: **983 tests passed in all 28 files** (7 min 11 s),
  no skips/failures. All coordinator-named files are included, including the
  exact connection-v2 session snapshot and built-in Settings Agents tests.
- Pinned Flutter serial/no-pub, through machine_lock test. Exact command,
  manifest, loaded-file list and final result are in test-command.txt,
  test-manifest.txt and test-result.json. candidate.sha256 matches all seven
  source/test files after the run. This is an affected-file batch, not the
  complete repository suite.
- machine_lock analyze with pinned Flutter analyze --no-pub: **No issues found**
  (24.3 s). Pinned Dart format language3.10 and git diff --check passed.
- No hand edits in lib/ui, Kotlin, generated SDK or another worktree. No native
  build/APK/device/push/release work. Reviewer did not launch any test process.
