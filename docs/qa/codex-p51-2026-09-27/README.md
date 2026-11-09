# P5.1 Team Now line — backend/state handoff (2026-09-27)

## Scope

Finish line: expose one observable team status with current activity, host start time, next step, honest timing guidance, and an explanation/recovery path after eight seconds without requiring another server event.

Non-goals: UI/kit implementation, replacing the dispatch strip, changing dispatch speed (P6.3), server adapters, automatic retries, releases.

Branch: `codex/p51`; base: `64128dbac103a0cfd17d179ff9bfb11935c7ab4e`.
Read sets: AGENTS.md; STANDARDS.md §§2, 3, 13, 15 only; existing team conversation/planning, orchestration controller/gateway/models, mutation records, KitRedact and relevant tests.
Write sets: `lib/state/team_now_line.dart`, `test/team_now_line_test.dart`, this QA directory, and the requested fallback `COMMIT_MSG.txt` if needed.
Dependencies: existing `teamNow`, `TeamPlanningRequest`, orchestration models and `ValueNotifier`; no new package, authentication or endpoint.
This is one bounded state slice; no independent implementation slices were opened.

## Feasibility

The current callable source exposes `teamNow(run, work, cycleOf, agents, gates)`, dispatch stall reasons, host timestamps, planning request status and durable request creation time. `OrchestrationController.refresh()`, `dismissPlanning(key)` and capability-gated `cancelRun(runId)` already exist. The new controller uses those facts through pure factories; it does not call a server or need credentials. Source-level feasibility passed; live server behavior was not tested.

The example “usually under a minute” is **not established by current evidence**. Existing `DispatchHint.usualWait` documents 1–5 minutes for worker startup, not a measured under-minute estimate. The API therefore leaves timing unknown unless the caller supplies an evidence-backed `typicalUpperBound`. Do not treat a stall timeout, desired latency or this test's synthetic one-minute estimate as performance evidence. The under-minute copy remains blocked on timing evidence; no speed claim or workaround was added.

## UI hook-up

Import `package:opencode_mobile/state/team_now_line.dart`.

1. Own one `TeamNowLineController` per visible conversation/request. Its `value` is a `TeamNowLineState`; it implements `ValueListenable` and supports `addListener`.
2. For a run, construct `TeamNowInput.forRun(activityKey: ..., run: ..., work: ..., cycleOf: team.cycleFor, agents: ..., gates: ..., connected: ..., canCancel: ...)`. Pass only that run's work and pending gates. Pass current reconciled facts after reconnect; do not replay volatile events. `connected` must reflect whether the facts can currently be trusted; default `true` is for an already-loaded connected caller. `canCancel` comes from the existing capability flag and mutation availability, never the server flavor.
3. Before a run exists, use `TeamNowInput.forPlanning(activityKey: ..., request: ...)`. It accepts unresolved requests only; a matched request must switch to `forRun`. Do not derive labels from the legacy `stillPlanning` status. On a disconnected planning screen, submit a `TeamNowInput` with `activity: unavailable`, `next: checkActivity`, and `reason: connectionUnavailable` instead of claiming ongoing progress.
4. Call `controller.update(input)` whenever orchestration/request facts change. Keep `activityKey` stable across refreshes; include profile, task/request, and selected work identity. Change it when those change. The phase and changed host timestamp also reset local observation. The controller has a single one-shot timer, not polling or background work.
5. Feed exactly one kit Now line from `value.activity`, `since`, `next`, `typicalUpperBound`, `reason`, `explain`, and `actions`. Dispose the controller on exit/profile deletion and remove the source listener. No edits to connection/main are required.

Example state lifecycle (the UI owner supplies the kit builder and localized copy):

```dart
final nowLine = TeamNowLineController(input);
// In the existing orchestration listener:
nowLine.update(nextInput);
// Bind a ValueListenableBuilder to nowLine and render one KitNowLine.
// On route/profile disposal:
nowLine.dispose();
```

`since` is the original host/request timestamp or null. Local observation only drives the eight-second explanation; it never masquerades as host start time. Future/skewed host times cannot suppress the explanation indefinitely. Repeated refreshes do not postpone it. A recreated controller immediately explains an already-old host/request timestamp. With no timestamp, it explains after eight seconds observed locally, while still reporting the start as unknown.

`explain` becomes true at eight seconds, immediately for known problems/questions/disconnection, and remains false for terminal states. When true, show the factual short reason and recovery actions in place, and offer the expandable Why content there. Expansion itself belongs to the UI. Do not gate the reason and way out behind technical Details. A failed terminal result still exposes `reason: causeUnknown` and `openActivity`.

Map enum values through en/ar localization; never render `.name`. No raw host text or engine identifiers are supplied as presentation copy. Suggested product vocabulary:

| Activity | Line | Next |
| --- | --- | --- |
| planning | Waiting for a plan | See the plan |
| waitingForWorker | Waiting for a worker | A worker starts |
| startingWorker | Starting a worker | Work begins |
| working | Working on your task | Review the changes |
| reviewing | Reviewing the changes | Finish the task |
| needsYou | Your answer is needed | Answer the question |
| delayed | Taking longer than expected | Check activity |
| unconfirmed | Request not confirmed | Check activity |
| refused | Request was not accepted | Check activity |
| unavailable | Cannot check progress | Reconnect and check |
| completed / failed / cancelled | Finished / Could not finish / Cancelled | See activity |

The displayed next step is an expected stage, not a promise that it has started. For timing null, say “Usual time unknown”; otherwise localize the supplied bound as usual timing, never a remaining-time countdown. Do not keep claiming the usual bound as a completion deadline.

Reason copy must keep uncertainty explicit: `noPlanReported` means “No plan has been reported yet. The reason is unknown”; `noWorkerReported` means “No worker has been reported yet”; `workerStarting` means “The worker has started but has not begun the task”; `workInProgress` means “The task is being worked on”; `reviewPending` means “Review or completion is still pending”; `answerNeeded` means “The team is waiting for your answer”; `workerCouldNotStart` means “The worker could not stay running”; `providerLimit` means “The AI service reported a usage limit”; `workTakingLonger` means “The work is taking longer than expected” (not “nothing is happening”); `confirmationMissing` means “We cannot confirm the request was received; check activity before sending again”; `requestRefused` means “The request was not accepted”; `connectionUnavailable` means “Progress cannot be checked while disconnected”; `causeUnknown` means “The reason is unknown; check activity”. Technical host diagnostics, if separately shown under Details, must pass through KitRedact.

Action routing stays with the existing UI/controller:

| Action | Hook |
| --- | --- |
| refresh | `await team.refresh()`; show its existing error state if needed |
| openActivity | Open the existing task/planner activity in this conversation |
| answer | Open the existing pending question flow |
| dismissRequest | `await team.dismissPlanning(request.key)`; hide the request (does **not** cancel host work) |
| cancelRun | Existing confirmation flow, then `await team.cancelRun(run.id)`; keep waiting for its receipt |

Actions are suggestions, never executed by this state controller. In particular there is no retry/send action or automatic retry. Cancellation is not offered offline or after completion/failure/cancellation. At 31 minutes an unresolved planning request has a reason, activity/refresh access and local dismissal, even if its legacy status still says `planning`.

## Persistence and security

No new persistence, raw server text, credential handling, logs or diagnostic output. Only product enums, timestamps, timing bounds and an opaque activity identity are retained in memory. No serialization API is exposed, so there is nothing new for KitRedact to sanitize or the profile deletion sweep to remove. Existing dismissal persistence remains in `OrchestrationController.dismissPlanning`, already swept by orchestration removal. No changes to UI, single-owner files, localization outputs, Android, gateway contracts or existing stores.

## Verification

Six focused `flutter_test` cases cover: exact eight-second notification without an event; refresh stability; missing/skewed time, task replacement and disposal; 31-minute planning and safe dismissal; refused/unconfirmed requests; run progress, all stall reasons and disconnection; starting timing, question precedence and capability gating; distinct terminal outcomes.

- Requested pinned launcher format: blocked by read-only SDK cache (`engine.stamp.tmp.*`, `engine.realm`).
- Format completed with the **same pinned SDK's** `bin/cache/dart-sdk/bin/dart --suppress-analytics format --language-version=3.10 lib/state/team_now_line.dart test/team_now_line_test.dart`. Exit 0; [saved output](format-output.txt) includes unavailable package-resolution warnings. No different Flutter/Dart SDK was used.
- `.../bin/flutter test --concurrency=1 test/team_now_line_test.dart`: launcher exited before compilation because the SDK cache is read-only. [Saved output](test-output.txt). **No test pass claimed.**
- `.../bin/flutter analyze lib test`: same launcher restriction. [Saved output](analyze-output.txt). **No analyzer pass claimed.**
- Full suite, UI goldens, APK/device work and live-server checks not run in this scoped unit. The verifier must run the focused file and whole-tree analyzer with the pinned Flutter in a writable environment. This adds a new API, not a wired UI fix; no failing-first claim.

## State

Implemented: backend API and focused behavior tests. Enabled: not yet wired into the UI. Verified: formatted; execution/analysis blocked by the environment. Committed: see commit outcome below. Deployed/released: no.

The UI finish line remains with the UI owner: create/wire the kit Now line, replace the cycle strip, add en/ar copy, and verify rendering, accessibility and expansion. No UI files were edited here.

Commit outcome: staging was blocked because `/home/owner/Storage/Code/oc_app/.git/worktrees/oc_app-codex-p51/index.lock` is on a read-only filesystem. No commit was created. The complete requested message, including both trailers, is in [`COMMIT_MSG.txt`](../../../COMMIT_MSG.txt). Changes remain in this worktree. New-file whitespace checks passed using `git diff --no-index --check /dev/null <file>` for both Dart files and this README; no protected-path edits were made.
