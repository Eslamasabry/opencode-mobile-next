# Fresh-worktree task launch — verification record — 2026-09-08

Branch `feature/isolated-task-launch` on baseline `6a02e7c`. Synthetic
checks only; no server, provider, device or release step ran.

## Contract evidence

| Path | Source | Result |
|---|---|---|
| v1 create | `contracts/opencode-openapi-f12e14cf.json` (upstream `anomalyco/opencode@f12e14cf`, `packages/sdk/openapi.json`): `POST /experimental/worktree?directory=` body `WorktreeCreateInput{name?, startCommand?}` → `Worktree{name, directory, branch?}`; events `worktree.ready{name, branch?}` / `worktree.failed{message}` with `directory` on the global-stream envelope | Callable through the generated SDK (`lib/api/product_repository.dart` `createWorktree`). **Live server exercise still pending.** |
| v2 create | `contracts/opencode2-openapi-beta-18600.json`: `POST /api/worktree/{projectID}` requires `{strategy, directory}` (`additionalProperties: false`) | Adapter sends `{name}` only; no strategy values are enumerated; no local copy of `@opencode-ai/schema@0.0.0-beta-18600` exists and no probe was run. **Unproven → `ServerCapabilities.worktreeCreate = false` on v2.** |

`worktreeCreate` is a capability, not a flavor check: true on v1, false on
v2, Codex and demo. It gates the new launch and the existing Worktrees
"New worktree" action; listing, opening, inspecting, reset and the inspected
`force: false` removal are unchanged.

## Behaviour (see `lib/state/isolated_task_launch.dart`)

- Subscribes to controller events before the create call; a `worktree.ready`
  that beats the create response is retained and applied by directory.
- Correlation: `event.project` must match the project when present;
  directories compared after slash normalization.
- 45 s readiness wait → `unconfirmed`; the user may keep waiting, stop, or
  open anyway. Ready opens the blank session without another tap.
- Cancel and timeout never delete. Cancel before the response does not
  assume rollback: the answer, when it lands, is recorded and the worktree
  stays in the inventory.
- `openSessionInWorktree` is profile-bound, verifies the switched directory,
  requires the current project to resolve to the launching project, and
  checks the created session's project/directory. Nothing is sent.

## Commands and results (pinned toolchain via `build/traycer/env.ps1`)

```
flutter pub get --offline            Got dependencies! (exit 1 only from the
                                     Windows symlink/Developer Mode notice)
flutter gen-l10n                     reproduced the hand-written getters;
                                     diff limited to isolatedTask* entries
dart format <touched files>          0 changed
flutter analyze --no-pub             No issues found! (ran in 20.9s)
flutter test --no-pub --concurrency=1
  test/isolated_task_launch_test.dart
  test/isolated_task_sheet_test.dart
  test/worktrees_screen_test.dart
  test/l10n_coverage_test.dart
  test/api2_gateway_mappers_test.dart
  test/connection_sse_test.dart      00:10 +80: All tests passed!
flutter test --no-pub --concurrency=1
  tool/capture/isolated_task_test.dart   +4: All tests passed!
```

Captures: `docs/qa/isolated-task/{workspace,sheet-form,sheet-creating,
sheet-preparing,sheet-unconfirmed,sheet-failed}-{light,dark}.png`.

## Still open

- Live v1 server: create, `worktree.ready` delivery on the global stream, and
  the session opening inside the worktree.
- v2 create body proof before the capability can be enabled there.
- Physical-device journey with keyboard and TalkBack.

## Recovery checkpoint (final source candidate)

The interrupted turn left the changes staged, not committed. Recovery found
no running Flutter/Dart process. The original focused and capture logs above
were complete and were retained, rather than inferred from the interruption.

Before commit, the launch guards were tightened across asynchronous repository
and transport preparation. A changed profile/location cannot retarget the
worktree POST or session POST; a missing current-project response now refuses
session creation. Automatic opening refuses a scope change while readiness is
pending. After an attempted open, Retry explicitly targets the same worktree.
The new sheet imports its types through the domain gateway.

Final checks, using the same pinned environment:

- `dart format lib/state/connection.dart lib/ui/screens/isolated_task_sheet.dart test/isolated_task_sheet_test.dart`: 0 changed.
- `flutter analyze --no-pub`: clean, 17.0 seconds;
  `build/traycer/isolated-recovery-analyze.log`.
- `flutter test --no-pub --concurrency=1 test/isolated_task_sheet_test.dart`:
  14 passed, including four new scope/project guards, using the real controller
  session-creation method rather than overriding it.
- `flutter test --no-pub --concurrency=1 test/isolated_task_launch_test.dart test/isolated_task_sheet_test.dart test/worktrees_screen_test.dart test/l10n_coverage_test.dart test/api2_gateway_mappers_test.dart test/connection_sse_test.dart test/session_selection_sync_test.dart test/chat_live_events_test.dart`:
  **186 passed, 1 failed** in 43 seconds. The original six focused files all
  passed (84 tests on this final candidate). All chat live-event tests passed.
  Log: `build/traycer/isolated-recovery-focused.log`.
- The failing test, `a queued prompt cancelled during selection is never
  delivered`, also fails when rerun alone with `--plain-name`. It expects only
  `model:a:p/saved:` but observes an additional `agent:a:plan`. Its exercised
  queue/selection paths are unchanged by this slice; baseline execution was
  not performed, so this is **not claimed as a proven baseline failure**.
  Assigned back to the integration coordinator for queue-owner triage.
- The 12 saved captures remain applicable: recovery changed no layout or
  displayed captured state. Workspace light and unconfirmed dark were visually
  inspected again; capture generation was not repeated.

The broadened focused run is not green. No full-suite or live-server pass is
claimed. Root owns review, queue-failure triage and the stable integration gate.
