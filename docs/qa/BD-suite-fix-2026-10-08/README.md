# BD integration suite fixes — 2026-10-08

Branch `sol/bd-suite-fix`, base `feat/genui-fe` **aa3847d36**. Both reported full-suite failures reproduce when their files run individually. They are stale test contracts; no production change is needed. Both entered through the `sol/bb-runtime` integration merge **ccd4c900216dec017f5250bcb31d27efcee1fdc9**. BD5's unmerged detekt refactor is not an ancestor of this candidate.

| Failure | Introducing commit | Why the expectation changed |
| --- | --- | --- |
| `builtin_project_lifecycle_guard_test.dart`: notification Stop waits off the UI thread | `2fe344b570c0e7f093c5e04b145adc7866073cc8` | Stop passes its captured revision to the worker's `stopAllServices(capturedRevision)`; the old zero-argument literal is absent, so its index is `-1`. |
| `perf_builtin_pollers_test.dart`: healthy 45-second foreground/resume/background checks | `e657025dd93f7352079aa5ac41ba167db5479307` | Native recovery adds an initial authority status read before the existing health and post-probe Stop checks: 14 probes require 42 status reads, exceeding the stale cap of 30. |

The merge's first parent is `e01237d2f23ec1e154e71e40298ff4a83afa512d`; its second is `2b653a10a21b3466ba1e8f18ce5d3e97da9a4f0b`. Both introducing commits are absent from the first parent's ancestry and present in the second's. Both test files remained unchanged between the first parent and this candidate. Before the merge, the source matched the old expectations; this is source-history evidence, not a claimed historical test execution.

The lifecycle guard now checks synchronous revision capture before the worker, both revision-aware and fallback drain calls inside the worker, absence of those drain calls from the UI-thread prefix, and worker-finally cleanup. Its scope is runtime-monitor/storage drainage and child shutdown; it does not claim that every durable admission operation is asynchronous. The poller still bounds health probes to 13–15 in ten minutes, requires exactly three status reads per healthy check, and now also verifies zero background status reads and exactly three on immediate resume. Both updates contain a one-line rationale identifying their introducing commit. Production cadence, ownership and Stop logic are unchanged.

Verification uses pinned Shorebird Flutter and `tool/qa/machine_lock.sh test -- <flutter> test --concurrency=1 <one-file>`:

- [Lifecycle red](lifecycle-red.txt): 6 pass, the reported test fails because `lessThan(-1)` compares against the missing literal.
- [Pollers red](pollers-red.txt): 4 pass, the reported test fails with **42 status reads / expected at most 30**.
- [Lifecycle green](lifecycle-green.txt): **7 passed**.
- [Pollers green](pollers-green.txt): **5 passed**.
- Reverting each test fix to the candidate's original file independently reproduces the exact original failure; fixed bytes are restored in `finally`: [summary](reverted-fix-summary.json), [lifecycle](lifecycle-reverted-red.txt), [pollers](pollers-reverted-red.txt).
- [Pinned analyzer](analyze.txt): **No issues found**. Both files were rerun independently after the revert controls and fixed-source restoration; all 12 tests pass.

Only these two tests and this evidence directory change. No native/production/UI edits, full-suite run, APK/device work, push, amend, CI or release. The coordinator owns the full-suite run and integration merge.
