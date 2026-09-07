# Rolling Luna swarm — 2026-09-07

Maintainer instruction: keep the existing ten Luna workers processing concrete
scoped tasks; review, replenish, batch-test, commit and push repeatedly. Use the
existing `dev` checkout and `mobile-next/dev`. Batch 3 begins at
`f866fd9f59276f62956339342459f882d2a7f138`, freshly verified equal to upstream.

Finish line for each batch: repair evidenced user workflow gaps, audit every
patch, verify an unchanged source candidate, commit logical batches with
`[skip ci]`, push and verify the remote revision, then replenish this queue.
Non-goals: speculative adapters, account access, live-server changes, automatic
feature opt-ins, CI/signing/release or unrelated artifact changes.

## Batch 3 assignments

Candidates are investigation targets, not completed fixes. Each worker must
identify exact source evidence before editing. Already-covered candidates are
replaced or retired; additional tests alone do not count as delivered features.

| Worker | Exclusive area | User outcome under investigation | State |
|---|---|---|---|
| Luna 01 | `state/offline_queue.dart`, dedicated queue tests | Queued input survives persistence failure and correct removal ordering | Verified |
| Luna 02 | `state/session_pins.dart`, `session_read_state.dart`, dedicated tests | Pins/read markers remain accurate through storage failures and restart | Verified |
| Luna 03 | Three budget state/persistence files and dedicated tests | Local budget values remain valid and honestly persisted | Verified |
| Luna 04 | Session note screen/domain and dedicated tests | Notes retain edits and offer safe save recovery | Verified |
| Luna 05 | Files screen and dedicated tests | File browsing/read errors recover in the current location | Verified |
| Luna 06 | Session import screen/domain and dedicated tests | Reviewed import remains bounded and tied to its target connection | Verified |
| Luna 07 | Saved permissions screen and dedicated tests | Permission removal/retry remains scoped and honest | Verified |
| Luna 08 | Voice model downloader and dedicated tests | Interrupted/corrupt model downloads cannot publish invalid installations | Verified |
| Luna 09 | Global sessions screen and dedicated tests | Search/filter results stay current and recoverable | Verified |
| Luna 10 | Prompt shelf state and dedicated tests | Stashed input survives failed save/restore/deletion operations | Verified |

## Operating rules

Root owns shared controller, profiles, main, chat library, gateways, localization,
native integration, verification artifacts and git operations. Workers preserve
other actors' changes, never write root logs, and run only formatting/diff checks.
Root serializes focused Flutter checks. UI copy arrives as per-worker temporary
JSON fragments for one localization pass.

Worker patches receive review and precise corrections before acceptance. During
the frozen recursive test gate workers may prepare read-only next-task proposals,
but cannot change source. Full source gates cover nested tests; final-candidate
edits invalidate earlier full coverage. Android checks run for affected native
and UI paths using synthetic data, with screenshots and explicit boundaries.

After a passing batch, root records outcomes and evidence, commits/pushes, then
assigns the next ranked runnable tasks. Completed workers are reused. Missing
external prerequisites park only the affected task, while other work continues.

## Replenishment pool

Review open workflow gaps in active context, session relations, managed
workspaces, project health, terminal, settings, and existing launch surfaces.
Pull only an evidenced, bounded source task with an exclusive file fence.
Contract-dependent new features require proof before an adapter/UI is enabled.

## Batch 3 review checkpoint

Implemented candidates now cover queue snapshot/write ordering and corrupt-data
recovery through Settings; read-marker write retry; valid partial budget recovery
with persistent degraded-state reporting; scoped note/import/permission/related
session operations; file viewer/attachment/status retirement; microphone/model
cancellation; and localization reuse in global session search.

The pin-limit proposal was rejected after diff review: the limits were newly
introduced, not baseline behavior, and could discard valid legacy pins/read
keys. Those changes were removed. Stash cleanup-after-commit was also rejected
because an error after acknowledged save would misreport the outcome. No stash
change is counted. Its worker moved to related-session recovery.

Lead corrections covered mutable controller listener ownership, stale
success/error/finally callbacks, note retention without a stuck Save state,
protocol-neutral file gateways, partial budget warning truth and explicit
recovery from unknown queue contents. Native inspection prompted the queue
storage summary to state unknown count instead of displaying zero.

Focused tests found compile-symbol/nullability errors and fixture defects; these
were corrected. Note fixtures were stopped by exact owned PIDs after waiting on
a Save button that had not rebuilt; the corrected test pumps the edit first and
uses bounded gate assertions. Passing runs do not erase failed/interrupted logs.

The frozen recursive gate passed 2,139 app tests in 215 files with three optional
skips; analyzer and 47 SDK tests also pass. Android checks use the isolated
Android14 x86_64 emulator and a synthetic v1 fixture on loopback4125. No live
provider, signing, CI or release action is part of this work.

## Next confirmed queue

- Offline delivery acknowledgement: final save failure after server acceptance
  can leave old queue data on disk. Design durable uncertain-delivery review or
  proven idempotency before a fix; do not remove queued text before sending.
- F6-S1a: native static Connect/New task launcher shortcuts; whitelisted actions,
  one-shot cold/warm consumption, explicit navigation, no automatic prompt send.
- Active context: reused controller/session inputs must retire old snapshots and
  listeners even when revision values coincide.
- Project health: repository-resolver exceptions need an error/retry state and
  must retire the refreshing spinner.

These tasks are scoped proposals, not implemented feature claims. Source edits
resume after the batch3 evidence/commit boundary.


## Priority correction: finish a major user journey

The maintainer rejected a queue dominated by minor fixes. Root accepts that
prioritization error. Batch 3 closes at its verified commit boundary; its fixes
are reliability work, not ten major new features.

The next active product slice is **F10: use Codex as a mobile backend**.
Finish line: a user adds a supported authenticated Codex connection, selects a
project, opens or starts a conversation, sends deliberately, reads streaming
output, answers a supported approval, cancels, and reopens after reconnect.
Unsupported operations must remain unavailable with clear local context.
Non-goals: speculative protocol adapters, deploying a proxy, provider sign-in,
account credential access, or claiming a synthetic turn proves live model use.

Root owns the interaction design, copy hierarchy, shared controller integration,
and rendered Android critique. Luna implements specified behavior after the
contract and file fences are fixed. Completion is measured against this journey,
not task count or isolated gateway tests.

Before edits, the same ten Luna agents are checking independent contracts:
1. Gateway connect/send/stream/approval/cancel/resume coverage.
2. Profile persistence, secret storage, migration and deletion.
3. Controller transport/events/health integration.
4. Chat approval/cancel/resume compatibility.
5. Existing connection editor components and validation seams.
6. Authenticated synthetic protocol fixture for Android proof.
7. Supported local app-server topology and authentication evidence.
8. Reconnect, uncertain delivery and mobile lifecycle behavior.
9. Capability gating throughout navigation and pickers.
10. End-to-end acceptance coverage and negative cases.

Design direction: add Codex in the existing connection flow, keep endpoint and
server connection token distinct, reveal only applicable fields, and provide one
clear connection action. Errors preserve entered data and allow explicit retry.
Connection success opens useful project/conversation navigation. Existing chat
remains the conversation surface; approvals identify the requested action and
allow a deliberate response. Interrupted delivery cannot silently resend.

Android launcher/pinned-session actions (F6) and per-run completion/attention
surfaces remain major follow-on candidates. The smaller replenishment pool above
is secondary unless a defect blocks the active journey. Feasibility must be
confirmed before enabling a backend; if its prerequisite fails, record the exact
blocker and select another runnable major journey.


## Codex implementation boundary

Base: `0159664a4393089e6ec11156cfce197eb5d0759c`, verified equal to
`mobile-next/dev` after Batch 3. The isolated real CLI 0.153.4 proof passed:
missing/wrong bearer returned 401; correct bearer returned 101 and completed
initialize plus an empty scoped thread list. No account or model call occurred.
See [protocol evidence](../verification/codex-connection-2026-09-07.md).

Frozen profile contract: `ServerBackend.openCode/codex`, with separate runtime
`codexToken`, secure `oc.codexToken.<id>`, metadata `codexDirectory`, and
`requiresCodexTokenReentry`. Old profiles retain OpenCode behavior. Existing
profile backend is fixed in the editor. Codex is not an HTTP probe flavor.

| Owner | Exclusive implementation fence | Acceptance |
|---|---|---|
| Root | Controller, domain capability definitions, localization, final integration | Correct backend and non-null folder through connect/reconnect; no HTTP fallback or automatic queued Codex replay |
| Luna 01 | Codex gateway and existing gateway tests | Scoped mutations, honest auth errors, supported capability set |
| Luna 02 | Profile store and two profile test files | Token isolation, compatible persistence, reentry and deletion |
| Luna 03 | New controller integration tests; read-only controller review | Connect, token failure, folder preservation, retired transport disposal |
| Luna 04 | Project/manage-project screens and new tests | Current folder context without unsupported management actions |
| Luna 05 | Server editor and new flow tests | Add, test, save/connect, failure retention, token reentry |
| Luna 06 | Temporary synthetic protocol fixture; profile monitor and existing tests | Complete scripted conversation, no unsupported background attention claim |
| Luna 07 | Codex connection guide and protocol proof document | Reproducible supported setup and explicit evidence limits |
| Luna 08 | Probe service/tests, then exclusive entire chat library and new tests | Text-only composer, preserved offline drafts, supported actions only |
| Luna 09 | Home/workspace/library and new tests | Stable navigation IDs, useful current-folder context, no unsupported routes |
| Luna 10 | New protocol journey test; auth banner and new test | Positive stream/approval/cancel/resume journey and actionable token rejection |

Root's first review rejected a navigation gate that hid the configured-folder
context, recovery notice and pinned-session heading together with unsupported
project management. Those surfaces must remain understandable independently.
Root also corrected a probe that ignored an unhealthy health result. Initial
focused checks found an omitted domain import, test teardown timers and a gated
fixture that discarded its own completion handle; failed logs remain retained.

The protocol has no pending-approval listing RPC. After reconnect, authoritative
history can be read but lost approval requests cannot be represented as recovered.
The product must disclose that limit and direct the user to their computer for
such requests. Synthetic streaming/approval evidence is not a live-model result.


## Machine-transfer checkpoint

At the maintainer's request, new task replenishment is stopped and all worker
edits are frozen. Codex source checkpoint `499c0e1a0aaaddd6edd9b1bd899b79133a687f87` includes
all accepted implementations and root corrections. The complete serial run
covered 223 files (2,186 passed, three skipped, six failed); the six failures
were corrected and their affected checks passed. Final analyzer, 47 SDK tests,
format/diff checks, debug build, saved startup and targeted Android approval/
cancellation checks pass. A corrected-source full rerun and complete final APK
journey remain pending. See [handover](../../HANDOVER.md) and
[verification](../verification/codex-connection-2026-09-07.md).

Transfer contains generalized planning and sanitized synthetic evidence.
Generated artifacts and original conversation captures are excluded.
