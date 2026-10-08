# P6.3 — Immediate task dispatch and truthful progress

Status: source-confirmed contract and UI handoff, 2026-09-28. Reviewed revision:
`98c4c67a` on `codex/audit`. Source line references below apply to that revision.
No application behavior or host configuration changes in this document.

Finish line: establish the actual create-to-dispatch ordering and specify what
the UI may claim before, during and after dispatch. Non-goals: a new scheduler,
automatic pool recovery, a worker-start deadline, or a screen redesign.

## What happens today

**Direct task routing is already immediate after successful creation.**
“Immediate” means the next awaited control operation in the same submission;
it does not mean immediate worker execution or a measured latency guarantee.
The [X52 handoff](../qa/codex-x52-2026-09-27/README.md) is consistent with current
source. There is no patrol timer or polling prerequisite between create and
assignment.

| Step | Current source and semantics |
|---|---|
| Submit direct work | [`start_run_sheet.dart:360`](../../lib/ui/screens/team/start_run_sheet.dart#L360) calls `giveTask` once, with the selected project and `teamWorkerPoolId(projectId)`. `_sending` blocks duplicate taps while that sheet remains mounted. |
| Create | [`orchestration.dart:1123`](../../lib/state/orchestration.dart#L1123) awaits `createWork`; the adapter posts `POST /v0/city/{city}/beads` with title, optional description/rig, task type and app label ([`gascity_control.dart:210`](../../lib/orchestration/adapters/gascity/gascity_control.dart#L210)). |
| Dispatch | As soon as the returned receipt is accepted and has a non-null `createdId`, `giveTask` awaits `assignWork` on that exact ID. The adapter posts `POST /v0/city/{city}/sling` with `{bead, target, reassign: true}` ([`gascity_control.dart:195`](../../lib/orchestration/adapters/gascity/gascity_control.dart#L195)). |
| Bookkeeping | Each control operation first persists its own mutation record before issuing network I/O ([`orchestration.dart:1224`](../../lib/state/orchestration.dart#L1224)). Thus disk and network time still occur between creation and the sling request. |
| Refresh | After the two-step operation, work and run scopes are refreshed without awaiting them. Refresh is observation, not the trigger for assignment ([`orchestration.dart:1140`](../../lib/state/orchestration.dart#L1140)). |
| Worker observation | A fresh, error-free snapshot must identify an explicitly running session with `currentWorkId` equal to the created task. A receipt, generic working agent, or unrelated pool member is insufficient ([`team_dispatch.dart:62`](../../lib/state/team_dispatch.dart#L62)). |

A refused create or accepted response without an ID sends no sling. A creation
with unknown outcome must remain unknown; the client cannot invent a task ID or
repeat creation to discover one. Once creation succeeded, dispatch failure does
not delete or roll back the task. There is no atomic create-and-dispatch endpoint
in this app path.

This conclusion covers **direct task submission**. The planner-enabled path
sends an objective to the planner (and can first request its start), rather
than making the same direct task-to-pool call
([`start_run_sheet.dart:307`](../../lib/ui/screens/team/start_run_sheet.dart#L307)).
“Keep in backlog” deliberately creates without dispatch
([`start_run_sheet.dart:232`](../../lib/ui/screens/team/start_run_sheet.dart#L232)).
Neither should acquire a misleading immediate-worker claim.

## Capability, permission and policy boundaries

The reusable `TeamDispatchController.canSubmit` requires a ready, non-stale
source and **both** `controlCreateWork` and `controlAssign`, plus one unused
attempt; empty inputs send nothing
([`team_dispatch.dart:40`](../../lib/state/team_dispatch.dart#L40)). The controller
also checks the relevant capability independently for each mutation, so a
connection or permission change between operations can leave a created task
with rejected assignment. That partial result is a valid state to display.

The current Gas City gateway supplies controls only through phone loopback or a
configured authorized front; bare remote hosts remain read-only
([`gascity_gateway.dart:102`](../../lib/orchestration/adapters/gascity/gascity_gateway.dart#L102),
[`gascity_gateway.dart:153`](../../lib/orchestration/adapters/gascity/gascity_gateway.dart#L153)).
The front capability currently lacks work creation
([`orchestration_gateway.dart:157`](../../lib/domain/orchestration_gateway.dart#L157)).
Use capability flags, not protocol flavor or an assumption that a Tailscale
address grants write permission. The adapter sends front idempotency keys only
when configured for a front
([`gascity_control.dart:339`](../../lib/orchestration/adapters/gascity/gascity_control.dart#L339));
phone loopback must not be described as providing the same server-side replay
guarantee.

Submitting work does not authorize waking an intentionally paused pool,
overriding supervision or thermal holds, or restarting a stopped service.
`giveTask` contains no pool recovery policy check because it does not implement
pool recovery. Current dispatch code cannot promise that the host will start a
worker despite those constraints. Show the actual hold/block/stale evidence
from the team overview and cycle instead of attempting extra wake calls.

## UI hook-up contract for Claude

Consume state/domain APIs; do not import a Gas City adapter or issue `/sling`
from UI. Keep one `TeamDispatchController` per deliberate task attempt, listen
to it, and dispose it before its source and on profile change/deletion.
`submit` invokes the existing `giveTask`; do not call both APIs for the same tap.
Its repeated taps share the same future even after an uncertain result.

**This wrapper is not wired into the current UI.** The sole screen caller still
uses `giveTask` directly. The sheet's `_direct` gate currently checks creation
capability and projects, rather than both create/assign flags
([`start_run_sheet.dart:350`](../../lib/ui/screens/team/start_run_sheet.dart#L350)).
The existing spinner/disabled fields at
[`start_run_sheet.dart:610`](../../lib/ui/screens/team/start_run_sheet.dart#L610)
cover the whole two-step call. A later UI unit must use the stricter admission
contract; this document does not claim that gap is fixed.

| Evidence/state | What to show | Permitted next action |
|---|---|---|
| Unavailable / stale / invalid input | Localized reason, retained draft and no working indicator. | Correct input or reconnect/refresh; send only when both capabilities allow it. |
| `submitting` | A bounded layout with an indeterminate indicator, “Sending task…” and disabled repeat submission. | Wait or leave the view; leaving is not cancellation. Preserve the attempt for reconciliation. |
| Create explicitly in flight, if a future state contract exposes it | “Creating task…” | Do not offer a second create. |
| Creation acknowledged; assignment explicitly in flight, if exposed | “Task created. Sending it to the team…” | Open the known task; do not restart creation. |
| Accepted assignment / `awaitingWorker` | “Task sent. Waiting for a worker…” with task link and evidence-based Now line. | Observe/refetch host state. No “Working” claim from the receipt alone. |
| `workerObserved` | “Worker session running” with the matching session/task. | Open current output. Do not imply useful model progress, completion or a historical start time. |
| Create rejected | “The task could not be created.” Keep editable draft. | Explain/retry after correction only when the failure establishes no creation. |
| Create unconfirmed / missing returned ID | “Could not confirm whether the task was created.” | Check host task/mutation state; no blind create retry. |
| Known created task; assignment rejected | “Task created, but it could not be sent to the team.” Keep its ID and show the existing task. | Refresh, inspect target/permission/hold, then explicitly retry assignment only when safe. Do not recreate or automatically delete the task. |
| Assignment pending, timed out or disconnected | “Task created. Could not confirm dispatch.” A failure to observe is not proof nothing ran. | Check task routing and mutation/session state before any new assignment. |
| Later stale/disconnected observation | Last-known state, explicitly marked stale; current worker status unknown. | Reconnect/refetch. No green live state or invented elapsed working duration. |

These phrases are proposed copy, not new hardcoded strings: implementation
adds English to `lib/l10n/app_en.arb`, generates localization, and uses kit
components. Visible errors use app-authored plain words. Raw receipts, reasons,
task content and exceptions never become the visible error sentence. Optional
Details and copy/export must pass through `KitRedact`; no credentials in reports
or notifications.

There is an important staging limitation: the wrapper sets `_created` and
`_assigned` only after `giveTask` returns
([`team_dispatch.dart:133`](../../lib/state/team_dispatch.dart#L133)). It currently
exposes **one combined `submitting` phase**, not distinct create/dispatch phases
or an early task ID. Use the combined row until a state-owned attempt/progress
contract exposes per-attempt mutation keys while each step is running. Do not
infer the stage from time elapsed or the controller's globally latest mutation.

Current `_sendDirect` returns the rejected assignment when present; otherwise
it closes with the creation record, including uncertain assignment cases
([`start_run_sheet.dart:380`](../../lib/ui/screens/team/start_run_sheet.dart#L380)).
`_close` also clears composer drafts
([`start_run_sheet.dart:211`](../../lib/ui/screens/team/start_run_sheet.dart#L211)).
The later hook-up must carry the combined outcome and preserve unresolved draft
or attempt recovery data; a successful create alone cannot be shown as successful
dispatch. Merely changing a toast would leave that contract incomplete.

## Retry, restart and recovery

Mutation receipts distinguish synchronous confirmation from asynchronous
acceptance and uncertainty
([`orchestration.dart:1364`](../../lib/state/orchestration.dart#L1364)). Even
“confirmed” here confirms the control operation, not worker execution. A later
matching host result can settle an uncertain record.

The wrapper prevents repeat submission only for its own in-memory instance.
It adds no timer, persistent attempt association or retry/recovery job. After
restart, read existing mutation records and task/cycle state first; do not
create a fresh wrapper to replay the old task. Disposing a wrapper suppresses
notifications but does not cancel the already-running two-step operation.

Generic `MutationRecord.canRetry` permits rejected **and unconfirmed** records
([`mutation_store.dart:280`](../../lib/state/mutation_store.dart#L280));
`retryMutation` mints a new request key
([`orchestration.dart:1281`](../../lib/state/orchestration.dart#L1281)). That API
is not a duplicate-free replay guarantee. A new direct-task UI must first
reconcile the known task, receipt and routing. If the host cannot establish
whether an uncertain create/assignment happened, keep it unconfirmed and offer
inspection rather than an unconditional new request. Safe resumption across
restart requires a durable parent attempt identifier and operation lookup or
host idempotency binding; the two current mutation records alone do not prove
the full two-step operation is atomic.

Automatic stalled-pool recovery remains unavailable. The
[P6.3 feasibility record](../qa/codex-p63-2026-09-27/README.md) and
[X52 proposed recovery contract](../qa/codex-x52-2026-09-27/README.md)
require task/target/generation correlation, permission checks, intentional-pause
and thermal-policy handling, idempotency plus operation lookup, and a named
matching worker session before reporting a recovery success. A current sling
receipt lacks that complete recovery evidence. No extra client timer or repeat
sling establishes it.

## Verification and acceptance still needed

Existing source-level behavior tests support the ordering and uncertainty model:

- [`team_control_test.dart:806`](../../test/team_control_test.dart#L806) asserts
  create then assign using the returned task ID; line 854 covers refused create
  and missing ID. Line 542 covers persistence before sending; line 967 covers a
  sent record becoming unconfirmed across restart without automatic resend.
- [`team_dispatch_test.dart:106`](../../test/team_dispatch_test.dart#L106)
  covers both capability gates. Line 133 covers duplicate submissions, ordered
  calls and exact running-session evidence. Lines 209, 231 and 250 cover create
  refusal/missing ID, uncertain assignment, and disposal during submission.
- [`team_controls_start_run_test.dart:209`](../../test/team_controls_start_run_test.dart#L209)
  covers the current direct form's create-and-assign call order.

These tests were inspected, not executed for this docs-only job; no fresh pass,
runtime measurement or sub-five-second result is claimed. The historical
[team-hot measurement](../qa/team-hot-2026-09-26/README.md) reports about eight
seconds; it is not current acceptance evidence.

Before the UI hook-up is called complete, add behavior tests with independently
blocked create and assign responses; verify exactly one request per step,
combined or correctly staged copy, retained task on assignment refusal, unknown
outcome on disconnect, no replay after reopening/restart, and stale worker
evidence. Include permission changes between steps, a known thermal/policy hold,
and unconfirmed create with no ID. Test server-derived reasons only in redacted
Details. Measure create acknowledgment, sling send/acceptance and matching worker
start separately against a real supported host. Host scheduling latency, model
startup, and automatic recovery remain separate acceptance gates.

Documentation validation: all 73 relative links/line anchors across the four
contracts resolve, both JSON examples parse, and the staged whitespace check
passes. No application source changed; no runtime test pass is claimed.
