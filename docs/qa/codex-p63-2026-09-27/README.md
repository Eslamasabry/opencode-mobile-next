# P6.3 — The team starts work at once

Status: **blocked at feasibility; documentation only**.
Inspected candidate: `d97420b8aa70c0cf6ab17edde4b28670587f2f05`, branch
`codex/p63`, on 2026-09-27. The working tree was clean before this record.

Finish line: a newly created task reaches a worker session within 5 seconds,
and a stalled pool is automatically woken with a truthful report afterwards.
Non-goals: warm workers, UI changes, native changes, server deployment, or
changes to the single-owner files excluded by the task.

Read set: `AGENTS.md`; STANDARDS sections 2, 3, 13 and 15 only; current
orchestration controller, gateway, Gas City adapter, dispatch and TeamNow
models; the pinned supervisor contract; the existing team-hot QA record;
existing team control tests; and KitRedact (read-only).
Write set: this QA directory and, if committing is blocked, root
`COMMIT_MSG.txt`. Dependency: a verified host contract for faster dispatch
and safe pool recovery. Acceptance remains the requested emulator timing
and an observed recovery followed by an accurate Now line.

## Feasibility

Stopped before adding an adapter, as explicitly requested when feasibility
fails. This is a failure to establish the required callable contract and
timing, not a claim that faster host scheduling is impossible.

1. **Immediate routing already exists.**
   [`OrchestrationController.giveTask`](../../../lib/state/orchestration.dart)
   (lines 1111–1146) awaits creation, then immediately calls `assignWork`
   when creation was accepted and returned an ID. There is no patrol timer
   between these operations. [`GasCityControl.assign`](../../../lib/orchestration/adapters/gascity/gascity_control.dart)
   (lines 195–205) posts `/sling` with the bead, target and `reassign: true`.
   Another client timer or another sling call would not establish a faster
   worker-start path.
2. **The existing measurement misses the target.**
   [The team-hot investigation](../team-hot-2026-09-26/README.md), under
   “Feasibility / Instant dispatch”, records task creation to worker session
   at approximately 8 seconds on the owner's phone. It reports that the
   sling already pokes Gas City's reconciler and that the remaining delay
   occurs within the host tick, rather than waiting for patrol. This is
   historical repository evidence, not a fresh measurement or an emulator
   result. Its upstream source findings were not independently rerun here.
   The current [runtime pin](../../../lib/builtin/setup/aiteam_scripts.dart)
   still selects Gas City 1.4.1.
3. **No verified faster dispatch or stalled-pool recovery contract.**
   The checked-in [supervisor schema](../../../contracts/gascity-supervisor-openapi-v0-3648ca2d499a.json)
   has `/sling`, `/session/{id}/wake` and agent actions `suspend`/`resume`.
   `SlingInputBody` has no immediate-dispatch option; `SlingResponse` has
   no worker-session ID or recovery outcome. This schema alone is not live
   feasibility proof. The current adapter resumes a known agent entry or
   wakes a session; neither establishes how to recover an empty stalled
   pool safely, distinguish intentional suspension from failure, or confirm
   that the recovery caused a new worker to start. An accepted mutation is
   not that confirmation. No automatic resume, restart or retry was added.
4. **Existing transport permission remains the boundary.**
   [`GasCityGateway`](../../../lib/orchestration/adapters/gascity/gascity_gateway.dart)
   builds its control adapter only for the phone's loopback control or a
   configured host front. The [probe](../../../lib/orchestration/adapters/gascity/gascity_probe.dart)
   checks the front's control and identity permission; bare remote hosts
   remain read-only. The [HTTP client](../../../lib/orchestration/client/http.dart)
   strips credential headers and sends the mutation request header, plus
   idempotency keys for the front. `controlCreateWork` is not available
   behind the front. No new authentication path or credential use is needed
   for existing calls, and none was introduced to bypass the missing contract.

Contract problem: P6.3's proposed “dispatch at once instead of waiting for
the patrol” mechanism contradicts the current call path and the saved
host investigation. `blocks: true`. Proposed replacement: “Measure and
reduce the host's existing sling-triggered scheduling latency; establish
a supported, attributable stalled-pool recovery contract before enabling
automatic recovery.” This proposal has not changed the requested acceptance.

## UI hook-up

No new Dart API is exposed because feasibility failed. A later UI unit can
use these **existing** state/domain APIs without importing a protocol adapter:

- Listen to `OrchestrationController` (`ChangeNotifier`) from
  `lib/state/orchestration.dart`. Gate direct task submission on both
  `capabilities.controlCreateWork` and `capabilities.controlAssign`.
- Call `giveTask(title: ..., description: ..., projectId: ..., agentId: ...)`
  once. Its result is `({MutationRecord created, MutationRecord? assigned})`;
  `created.receipt?.createdId` identifies the task. A null assignment or an
  unconfirmed receipt must not become “worker started”. Do not repeat an
  uncertain create to obtain an ID.
- Read `controller.cycleFor(workId)` for `DispatchCycle` evidence and stall
  reason. Use `snapshot` for current work, agents, runs and gates. A
  `hostNotStarted` stall alone is not authorization to resume a pool.
- For the Now line, call `teamNow(run: ..., work: ..., cycleOf:
  controller.cycleFor, agents: ..., gates: ...)` from
  `lib/state/team_conversation.dart`, filtering inputs to the selected run.
  It returns `TeamNow.kind`, `since`, `agentName`, `workTitle`, `stall` and
  `gateTitle`. Localize the kind in the UI; preserve unknown timestamps.
  Recompute when the controller notifies. Existing states distinguish
  waiting, starting, working, review, stalled, needs-you and finished.
- There is **no confirmed automatic-recovery result** to display as “pool
  woken”. Keep that feature unavailable until its host contract is verified.
  Do not infer success from a POST receipt or an unrelated pool session.

No new state is persisted, so there is no storage migration or deletion
hook. If the subsequent implementation persists recovery reports, pass
persisted text through `KitRedact.text`, keep it scoped to the profile,
and add restart/deletion tests. Never persist raw credential-bearing errors.

## Unblocking work

The host owner needs to establish a supported operation for prompt scheduling
and recovery with task/pool/session correlation, acknowledgement versus
completion semantics, intentional-pause handling and retry/idempotency rules.
If the existing sling path is the fastest supported operation, the latency
fix belongs in the host scheduler; this mobile worktree cannot prove it by
adding a wrapper. The coordinator then measures task `bead.created` to the
matching worker session on the emulator, requiring less than 5 seconds, and
separately verifies a stalled-pool recovery and its subsequent UI report.

## Verification and state

The feasibility check used current source and a selectively decoded schema.
No live server, emulator, adb, native build, credential or signing operation
was used. Existing behavior tests relevant to a later implementation are
`test/team_control_test.dart` (giveTask accepted/refused/unknown-ID cases)
and `test/team_conversation_lead_test.dart` (Now stages and stalls).

No Dart behavior was added or changed; no new behavior tests, formatting,
Flutter test run or analyzer run applies to this documentation-only stop
(STANDARDS PROC-28). All eight documentation links resolve. Whitespace
checks include the untracked report. No test pass or under-5-second result
is claimed.

| State | Result |
|---|---|
| Implemented | No new P6.3 behavior; feasibility report only |
| Enabled | Existing immediate routing unchanged; automatic recovery unavailable |
| Verified | Source/contract inspection only; runtime acceptance unverified |
| Committed | No; Git metadata is read-only in this sandbox |
| Deployed / released | No |

## Commit outcome

`git add docs/qa/codex-p63-2026-09-27/README.md` failed because Git could
not create `/home/owner/Storage/Code/oc_app/.git/worktrees/oc_app-codex-p63/index.lock`:
`Read-only file system`. The report remains untracked in the working tree.
The requested message, including both attribution trailers, is saved in
root `COMMIT_MSG.txt` for the coordinator. No push was attempted.
