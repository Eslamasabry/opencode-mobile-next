# BA suite integration repair — 2026-10-08

Finish line: both requested files pass without weakening nudge behavior or wake
request/timing budgets, with each failure traced to its introducing merge.
Non-goal: production behavior changes, UI/native/device work or a full suite.
Branch `sol/ba-suite-fix`, base `feat/genui-fe` `aa3847d365`.

## Cause and decision

Both originate in **ddd23e17d7385404fabbd9014e4dd144683a776a**, merged into
frontend by **d777082c3** (`Merge sol/bd-refresh`). BD2 removed `_feedWanted`
from inventory scheduling and reconciles inventory on startup/reconnect before
Home reads it. **fe7052de0** subsequently keeps the first debounce deadline;
it does not introduce these reads. The new production behavior is intended.

The two approvals tests already pass their nudge/action/dismissal assertions.
They fail only at Flutter's pending-timer invariant: permission events now queue
`_feedScheduleRefresh` via `_invalidateFeedDirectoryQuestions`, leaving its
2-second timer alive until their `addTearDown(controller.dispose)` runs after
that invariant. Production disposal already cancels the timer and fences late
callbacks. Each test now disposes its controller after its final assertion,
inside the widget callback. The one-line reason is next to each disposal.

The wake test already passes its readiness, reload and fresh-catalog assertions.
The fake repository inherits `_SdkWorkspaceOps.listGlobalSessions`, so the new
inventory read accidentally invokes the real SDK's `GET /experimental/session`
and leaves Dio's zero-duration error timer pending. The repository fixture now
implements that read through the existing fake server, counting the endpoint
and modeling 150ms latency with an empty page. The one-line reason is above the
override. All original timing/request-budget assertions remain intact.

## Verification

Pinned Flutter3.47.1 pub get completed once before checks. Both changed files
formatted with `dart format --language-version=3.10`. Each file ran alone,
serially through the shared machine lock, first on the base and then fixed:

```sh
tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 test/nudge_moments_test.dart
tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 test/perf_catalog_budget_test.dart
tool/qa/machine_lock.sh test -- <pinned-flutter> analyze --no-pub test/nudge_moments_test.dart test/perf_catalog_budget_test.dart
```

| File | Base | Fixed | Evidence |
| --- | --- | --- | --- |
| nudge_moments_test.dart | 15 pass / 2 pending-timer failures | 17 pass | [base](nudge-baseline.log), [fixed](nudge-fixed.log) |
| perf_catalog_budget_test.dart | 6 pass / 1 Dio pending-timer failure | 7 pass | [base](perf-baseline.log), [fixed](perf-fixed.log) |

24 focused tests pass. [Analyzer](analyzer.log) checks the two changed test files;
[candidate hashes](candidate-tests.sha256) record the verified contents. Logs
are normalized only for trailing whitespace. No production files changed;
no full suite, build, device, credentials, push or publication involved.
