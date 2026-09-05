# Implementation cycles

## 2026-09-05 — Cycle 01

Implemented **BE-001–004** and **FE-001–004**:

- Queue writes report storage failures and preserve existing drafts. Queue edits
  are serialized; pending sends recheck location, transport, and membership.
- Global v2 session search omits active-project filters. Both terminal adapters
  count UTF-8 bytes when advancing reconnect cursors.
- Workspace refresh updates sessions, and its context reflects the execution
  directory. Selection state follows the connection controller.
- File searches run after a short typing pause, survive refresh, and restore
  directory contents when search mode ends. File preview actions wrap below the
  title, Close is visible, and Copy uses the full loaded text.

Verification: pinned Flutter analysis clean; **116 focused checks passed** across
queue behavior, API requests, terminal output, file workflows, workspace behavior,
and localization. The checks added three queue regressions and extended existing
API/search/terminal examples. No additional emulator campaign was run for this
batch. The preceding Running work native evidence is in [QA](../qa/running-work.md).

Next priorities: **BE-007** OAuth completion, **BE-009** MCP timeout encoding,
**FE-005** command launch into a new chat, then **BE-006** concurrent request errors.
Remaining ready work stays in the backend and frontend queues.

## 2026-09-05 — Adaptation and clean production baseline

Recovered the useful product changes from the preserved `afd5252` / `ca0b249`
commits onto the current app:

- Known context usage stays visible beside the model, including below 70%;
  unknown usage has no meter. Assistant messages use tighter vertical spacing.
- Copy gathers consecutive assistant messages into a complete reply; an active
  reply is labelled as text so far. Delete still targets only the selected
  message. Tool summaries distinguish work that was not executed.
- Source and configuration attachments become provider-readable UTF-8 text.
  Unsupported binary attachments are rejected before sending.
- Termux can restart the managed server without reinstalling packages or
  replacing the saved credential. Process ownership is checked before stopping
  it, and operation IDs distinguish restart completion from stale readiness.
- Updated the workspace regression assertion to identify the directory and
  active-session context labels independently, addressing the previous CI failure.

The current bounded, lifecycle-aware Running work implementation is retained.
The old branch and QA artifacts remain preserved outside the clean baseline.

Verification: pinned Flutter analysis clean; **155 focused checks passed** across
the initial checks and the final affected-file rerun (**111 passed**). One Linux
process-group check was skipped on Windows and remains covered by the existing
Linux CI workflows. No additional native build or emulator campaign was run.

## 2026-09-05 — Cycle 02: provider parity and catalog usability

Implemented **BE-006–009** and **FE-007–008**:

- Concurrent catalog requests attach failure handlers immediately while keeping
  gateway error types. Optional VCS requests retain independent fallbacks.
- V2 provider key, OAuth, and MCP actions carry their location. OAuth attempts
  keep the location where sign-in began, even if the active workspace changes.
- Code completion preserves the attempt until the confirming status read;
  bounded terminal results make completion/status retries reliable.
- V2 MCP timeouts use startup/catalog/execution fields; v1 keeps its scalar.
- Default shell retries failed refreshes even when old choices are cached.
  Commands & tools tabs scroll and remain reachable at 320dp with 2x text.
- Corrected the documented v2 compatibility target and signer migration notes.
  Added the full [V1 release readiness](../v1-release-readiness.md) requirements.

Verification: pinned Flutter analysis clean; **121 focused checks passed**.
The preceding PR #64 Linux and Windows builds passed; its Android workflow was
still running when checked. This cycle did not publish a release or create a tag.

Next: command creation/retry flow, stale connection probes, refresh/viewed-state
retention, opaque global pagination, and complete long-history navigation.
Release and protocol requirements remain open in the readiness document and
review queues; passing this batch does not establish full feature parity.

## 2026-09-05 — Cycle 03: commands and trustworthy refresh feedback

Implemented **FE-005, FE-006, and FE-009**:

- Commands can start a new chat directly. Submission keeps the dialog open,
  retains arguments and destination on failure, disables duplicate dispatch,
  and reuses a chat already created by a failed attempt. Existing chats use
  their own model and variant. Location changes prevent stale dispatch/navigation.
- Commands, Skills, References, and Terminal show refresh failures with Retry
  above cached content, including empty lists. The scrolling content stays
  mounted; overlapping catalog loads cannot replace a newer result or failure.
  Same-location terminal updates preserve rows while loading.
- Changing connection URL, username, or password invalidates pending probe
  results and focus changes. Password paste uses the same invalidation path,
  and pairing supersedes the previous test's busy state.
- The backend review documents exact opaque cursor contracts and page-aware
  merge/scroll requirements for BE-005 and BE-010. Pagination is not implemented
  by this batch.

Verification: pinned Flutter analysis clean; **61 focused checks passed** across
command dispatch, retained refresh content, connection editing/pairing, terminal
scope/lifecycle, skills, and localization. An initial command-test fixture omitted
a required field; it was corrected before the passing command checks. No native
build, emulator campaign, release tag, or publication was performed. The preceding
PR #65 Windows CI passed; Linux and Android were still running when checked.

Next: review file/patch retention, Files Back behavior, complete pagination, then
the remaining protocol workflows and final release-candidate evidence in the
[readiness checklist](../v1-release-readiness.md).

## 2026-09-05 — Cycle 04: stable review, folder navigation, global pagination

Implemented **FE-010, FE-011, and BE-005**:

- Review retains the selected file by path across reordered results and keeps
  readable patches on refresh failure. Unchanged patches retain line selection;
  changed patches clear invalid selections and Viewed marks. Switching scope
  clears old content and rejects late responses from the previous scope.
- Files consumes Back only in the active tab: clear search, then navigate to
  the parent folder, then use the existing root exit guard. Standalone Files
  routes also handle folder Back. Breadcrumbs name the project root, folder
  actions, and current folder for assistive technology.
- All sessions now returns and consumes `ServerPage` continuation tokens.
  V1 uses the returned `X-Next-Cursor`; v2 requests newest-first unscoped
  results and forwards subsequent opaque cursors alone. Empty/filtered and
  duplicate pages retain Load more. Failures retry the same token; cursor
  cycles offer a reload rather than looping. Search edits invalidate pending
  responses immediately, before debounce.
- Backend review recorded exact remaining model/agent synchronization and
  staged-revert state/API/UI work for existing issues #53 and #55.

Verification: Flutter analysis clean; **120 focused checks passed** across
Review, Home navigation, file workflows, both global-search adapters, finder
pagination, repositories, and localization. The final v1 cursor check traversed
two pages and rejected a malformed token before making a request. No emulator
campaign or release build was performed. PR #66's platform CI runs were still
active at the initial check.

BE-010 message-history pagination and scoped session inventory remain incomplete;
this cycle does not claim full pagination or release readiness. The pinned v1
global endpoint's equal-timestamp limitation remains documented as upstream
behavior. Next delivery work is message history, model/agent synchronization,
staged revert, and the remaining release requirements.

## 2026-09-05 — Cycle 05: newest-first message history

Implemented the message-history portion of **BE-010** on the preserved work
branch, keeping dev on the merged baseline until the completed batch is ready.

- Both gateways expose one chronological page with an opaque older cursor.
  V1 retains response-header/Link tokens; v2 requests descending order once
  and sends only its continuation on subsequent requests. Production reads use
  `messagePage`; the old `messages` method is a latest-page compatibility read.
- Chats open at their newest messages and offer older loading, including empty
  intermediate pages. Overlapping head refreshes retain the loaded prefix;
  reconnect and structural resets invalidate disjoint cached history. Failed
  reads preserve the transcript and draft; expired/repeated cursors offer reload.
- Older-page merges retain newer live changes and deletion tombstones. Unknown
  part updates wait for message identity; older metadata updates wait for server
  placement. Delta-before-base events survive an intervening page response.
- Loading preserves the same visible message and pinned live end. The loading
  spinner retains the button's dimensions to avoid moving a reader at the older
  edge. Timeline search remains entered while loading and follows parent resets.
- Timeline, context, Markdown export, and reply-copy labels identify partial
  history. Context recovers expired cursors and distinguishes loaded counts/cost
  from server-reported session usage. Provisional-session discard requires an
  empty page with no continuation.

Verification: analysis clean; **110 focused checks passed** across chat/live
events, transcript actions, context, both message transports, and localization
(98 final chat checks plus 12 unchanged context/transport/localization checks).
The transport fixture verifies a newest-first request returning records beyond
the previous cap and an older cursor request. Existing fixture gateways were
migrated to expose their lists as one complete page. No release build or native
visual campaign was performed. PR #67's three PR-triggered CI runs were cancelled
when inspected; cancellation is not passing platform evidence.

Scoped session inventory remains capped. Model/agent synchronization, staged
revert, and the broader parity and release gates remain open. The original WIP
checkpoint and its review notes remain in Git history; its stale working
document was removed after resolving those findings.

## 2026-09-05 — Cycle 06: complete scoped v2 session pagination

Removed the scoped v2 inventory's fixed enumeration cap. Recent sessions,
Archived, and command destinations now load older pages using the server's
opaque continuation, including pages with no matching rows. Loaded counts and
empty states identify partial results; command arguments and destination choices
survive loading. The workspace's empty Recent section now scrolls inline so it
cannot trap gestures before Archived and the pager.

Controller refreshes preserve cached metadata and alerts outside loaded pages.
A complete walk removes missing list membership, while confirmed deletion or
404 removes the entity. Revisions and tombstones reject stale pages and detail
reads. Direct chat opens and unknown running sessions hydrate by ID; foreign
session metadata stays outside scoped inventory and Activity opens its location.
Metadata hydration has a separate status revision guard, and a failed status
request leaves Load more available alongside the head retry action.

Verification: analysis clean; **94 focused checks passed** across inventory,
Activity, v1/v2 connection state, real HTTP page transports, command destinations,
Workspace/Archived, and localization. Three chat-entry checks also passed before
the final status-only fixes. Backend and frontend agents reviewed the batch;
their membership, read-race, status-race, and blocked-pagination findings were
resolved. PR #68's Linux, Windows, and Android CI all passed when inspected.

The WIP checkpoint remains preserved in Git history. V1 scoped listing keeps
its native contract without an invented continuation; the pinned v1 global
timestamp-tie limitation remains documented. No native release build, final
visual campaign, tag, or publication was performed. Model/agent synchronization,
staged revert, resolved request sheets, and the broader release checklist remain
open.

## 2026-09-05 — Cycle 07: server-owned session model and agent

Implemented the client workflow for **#53**. V2 session reads and creation retain
the full model, variant and agent, including the difference between unhydrated
and server-inherited values. Selected events update only their session. Rename
and move events patch metadata without erasing selections, usage or revert
state. Existing v2 chats keep server choices while connected or offline; profile
defaults apply only when creating a new chat, including command destinations.

Intentional picker/cycle changes serialize through the model/agent endpoints and
reconcile the server response. Ordinary v2 prompts, commands and shell calls make
no selection writes. Offline replay explicitly applies saved snapshots, checks
cancellation after waits, and stops before using a retired transport. A selection
failure restores the draft without enqueueing an undispatched prompt. A failed
dispatched prompt retains its captured selection and profile for offline replay.

Pickers follow remote changes while their drafts are untouched, preserve edits
on failure, display unavailable server models, and show agent saving/errors. An
open Options editor remains bound to its displayed model. Inherited v2 selections
use server-default copy and remain eligible for compaction. V1 per-prompt model,
variant and agent behavior is preserved.

Verification: analysis clean. The final controller/event/inventory run passed
**29 checks**, including **11 selection checks**. The actual v2 reconnect path and
gateway lifecycle passed **5 checks**; reconnect adopts a changed remote model,
agent and cleared variant. HTTP selection/creation/ordinary-dispatch fixtures,
picker and Options regressions, offline capture/cancellation/failure checks,
command destinations, mapping/localization and affected chat dispatch checks
passed. Three wake-replacement checks passed after correcting the fixture to
publish its returned API, as production recovery does. Both agents reviewed the
batch and their concrete interruption/editor findings were resolved.

PR #69's platform CI was still running at this cycle's initial check. No final
native build, live cross-client/device campaign, tag or publication was performed.
The pinned v2 contract has no reset-to-inherited mutation and no atomic per-inbox
model binding; explicit offline replay changes subsequent provider turns. #53
remains linked for final live verification. Staged revert, resolved request
sheets and the broader release checklist remain open.

## 2026-09-05 — Cycle 08: explicit staged revert

Implemented #55 in an isolated worktree from the clean dev/master baseline.
V2 hydration and events retain the boundary, original snapshot and optional
fixed file preview. Stage, Clear and Commit are separate operations. Chat has
a persistent Review banner; the review loads the boundary prompt and returned
patches, with explicit confirmations and scrolling at enlarged text. V1 keeps
its original revert/restore paths.

Matching-session structural events reset Chat, Timeline and Context. Hidden
history is excluded from prompt reuse, retry, export and usage. Reopening an
older boundary walks raw hidden pages until visible history is reached. Commit
prunes the removed tail before refresh, including when the refresh fails.
Scope, boundary/preview fingerprints, event revisions and fresh reads reject
known stale confirmations; exclusive actions and reconciliation cover busy and
ambiguous responses.

The pinned server implicitly commits staged history before prompt admission.
Send, prompt-based commands and offline replay now require explicit resolution,
retain drafts/queued entries, and check current server state before dispatch.
Shell execution does not itself commit and remains available for reviewing the
staged working tree.

Verification: a real beta-18600 server and Git snapshot fixture passed stage →
clear, history-only stage, replacing a file stage with history-only state, and
stage → commit. It proved that staging changes files immediately, raw history
remains until commit, and commit deletes the boundary inclusively while keeping
the staged file content. Captured evidence and reproduction notes are in
`docs/verification/staged-revert-beta-18600.md`.

Static analysis is clean. Focused verification passed: 23 state/selection checks (including 12 staged
revert checks), 20 v2 HTTP interaction checks, 5 revert transport checks,
7 context checks, 4 staged chat regressions and the existing paged-history
regressions. Initial fixture failures were corrected: a missing test import,
legacy selection fixtures unintentionally marked reverted, and scrolling/text
frame setup in the large-text and composer checks.

At the initial CI check, PR #70's Windows run passed while Android/Linux were
still running. No final APK, native device campaign, tag or public release was
created. The protocol has no atomic conditional commit/prompt; fresh reads
reduce but cannot eliminate a remote change between read and POST. Resolved
request sheets and the broader release checklist remain open. The hourly
automation remains paused at the user's clean-slate request.

## 2026-09-05 — Cycle 09: resolved permission and question lifetimes

Implemented in an isolated checkout, preserving the user's clean dev baseline.
Chat and Activity now share permission presentation. Permission/question sheets
observe the original location and request contents; removal, replacement or a
scope change retires the request immediately. Only that sheet and its owned
confirmation/full-diff routes are removed, even beneath an unrelated screen.
Equivalent hydration and normal transport wake retain drafts and open sheets.
Confirmations reserve the action before awaiting input without showing a
sending spinner until the user confirms. Genuine reply errors remain inline.

Controller actions capture request identity before waking. One shared slot
covers permission choices and another covers question answer/reject, including
native notification actions. A removal remains retired even if the server
reissues the same ID before wake completes. Old completions cannot resolve a
replacement request; late failures after remote resolution are suppressed.

Verification: analysis clean. Across the eight focused request suites, 86 checks
passed (the final affected UI rerun passed all 25). Two localization checks also
passed. The initial UI run exposed premature sending spinners behind the Always
and Dismiss confirmations; implementation was corrected. The recoverable-error
fixture was corrected to use a product API error instead of expecting raw
StateError text, which the app intentionally replaces with friendly copy.

PR #71's completed Windows CI passed; Android and Linux failed only the
localization ratchet, which mistook numeric interpolated diff counts for prose.
The detector now ignores simple interpolation identifiers when deciding whether
literal text contains words, with regressions for numeric counts and surrounding
English prose. Baselines decreased where this corrected existing false positives;
none increased. This is the observed CI failure, not evidence of a new platform
build. #10 stays open for final native multi-session, keyboard and notification
verification. No emulator, release APK, tag or publication was created. The hourly
automation remains paused; dev will be aligned with master after merge.

## 2026-09-05 — Cycle 10: unread completions and private read state

Implemented #57's client workflow in an isolated checkout from clean dev/master.
V2 session hydration retains idle and viewed watermarks; session.viewed events
update matching cached sessions without rewinding newer state. The optional
SessionReadStateGateway posts exactly {idle} to the scoped /session/{id}/view
endpoint and accepts its bodyless 204 response.

Recent, Sessions and All sessions show Unread result text with spoken semantics.
A visible, loaded ChatScreen owns acknowledgement. Covered/backgrounded routes,
loading/error states and pinned older transcript reading do not mark a result
viewed. The deferred callback captures session, location and completion; wake
rechecks visibility, scope, busy state and privacy. An old receipt cannot clear
a newer completed run. A new viewer can finish after a disposed viewer's wake;
foreground reconnect retries sync without a background replay queue.

Privacy adds Sync read state on v2 only. Opt-out keeps local read history and
ignores remote receipts when deciding local unread status. Local watermarks are
persisted per saved server/location, capped at 2048 entries per profile, and
survive a server sync failure. Opting in waits for the preference to save;
failed preference writes leave sharing off in this process and show an error.
Profile deletion blocks new receipts, drains local writes and clears its cache.

Verification: clean analysis. 17 focused read-state checks passed, alongside 28
related session-row/global-finder/profile-cleanup checks and 56 HTTP, mapper,
event and localization checks. The HTTP fixture verifies the exact timestamp,
location query and 204 handling; widget checks cover route coverage, background
resume, reconnect, local persistence, privacy gating and spoken unread status.
The initial widget run needed the existing English fallback for standalone
surfaces without a localization delegate; both failures were corrected.

PR #72's Android, Linux and Windows CI were still running at the one initial
check. No native/device run, release APK, tag or public release was created.
#57 remains linked for final live/device verification. The broader release
checklist remains open. The hourly automation remains paused; dev will be
realigned with master after merge.

## 2026-09-05 — Cycle 11

Implemented the bounded session note workflow linked to #58 in the isolated
`codex/session-note` branch, keeping the user's main `dev` checkout clean.
The session menu opens **Note for the agent**, a scrollable editor with a
JSON-byte counter, Save/Remove, inline failure feedback and discard protection.
It edits only `mobile.note`; unfamiliar value types remain untouched. V1 and
known unsupported endpoints hide the action.

Save/Remove re-read the saved value before writing. Scope and event revisions,
post-wake checks and a shared write slot prevent known stale or competing
decisions. Refresh retains the draft and displays the current saved note for
review. Authorization errors explain how to recover. Successful changes leave
feedback in chat; the next-step instruction event creates a durable transcript
notice with matching live/REST IDs. Entry names and values are redacted from
those notices, including server-owned instructions.

The isolated beta-18600 server verified authenticated GET/PUT/DELETE, 401s
without authorization, the exact 8,192-byte boundary, 413 rejection without
overwriting the saved note, removal and preservation of an unrelated fixture
entry. No provider run was started and the helper was stopped. Captured evidence
and source-derived step-boundary semantics are in
`docs/verification/session-note-beta-18600.md`.

Clean analysis and 69 focused checks cover note wire/controller/editor behavior,
transcript rendering, placement, event/mapping regressions and localization. A phone-width widget
preview uses the real app fonts; compact 320px/large-text interaction is covered
by the editor check. This is not a device release campaign. Initial UI checks
needed a frame after simulated typing, and the old event test was updated to
expect the new sanitized instruction hint instead of ignoring it.

PR #73's Android, Linux and Windows CI all passed. #58 remains linked for the
final device/model-step verification, alongside the remaining release backlog.
No release tag or public artifact was published by this batch. The recurring
automation stays paused; after merge, `dev` is realigned with `master`.

## 2026-09-05 — Cycle 12

Implemented #54's aggregate usage overview in `codex/aggregate-usage`, preserving
the clean main checkout. Settings → Usage and cost presents Today, 30 days,
This year and All time, plus all/current-project scopes. Cost, sessions, prompts,
subagents, steps, token breakdown, model usage and summarized tool reliability
come from the server's statistics response. The existing context view remains.

The global endpoint receives explicit project IDs only when selected, never the
pinned directory/workspace. Calendar ranges use local dates; the maintained,
pinned timezone plugin supplies the device's named zone. Unsupported and auth
failures remain distinct; a missing timezone/project cannot silently change the
query scope. Sequence and location guards drop superseded responses. Failed
refreshes keep the previous result labeled; changing filters clears it.

Thirty focused checks passed across statistics/controller/UI, existing Settings
flows and localization; final static analysis reports no issues. PR #74 passed
Android, Linux and Windows CI. The phone preview uses real fonts; the range controls
fit one row at 411px and wrap on smaller/large-text layouts. The initial preview
writer awaited filesystem I/O under the widget test's fake clock; it was stopped
and corrected to write synchronously. No app/server request was hung.

The beta-18600 fixture verified two-project totals, project filtering, timezone
grouping, completed/failed/unfinished tool counts, empty ranges and 400/401
responses. No provider execution was started. The native helper was stopped and
fixtures preserved; see `docs/verification/usage-beta-18600.md` and its JSON.

Dependency setup changed only flutter_timezone to 5.1.0. Its license and desktop
registrations are included. Local plugin junctions completed Flutter setup
without changing Windows settings. Platform CI and the final native/device
check remain necessary for the new plugin. This batch does not publish a release.
The recurring automation remains paused; dev is realigned after merge.

## 2026-09-05 — Cycle 13: MCP scope and PR review follow-ups

BE-011 is implemented with a live beta-18600 two-project add/restart check.
V2's MCP entry is runtime-only, so setup now states the restart limit and actual
location, retains v1's persistent scopes, rejects unsupported scope requests,
and prevents accidental replacement of an existing name. Connection changes
cannot retarget an open draft. A 411px preview uses the real app fonts.

Reviewed all 18 outstanding inline threads on PRs #63–75 and the created branches.
Thirteen comments led to corrections; five were retained with specific reasoning
in `docs/verification/pr-review-and-branches-2026-09-05.md`. Fixes cover keyboard
Back, read-cache failures, partial events and instruction redaction, usage retry,
request-route cleanup, stale pagination errors and small UI state improvements.

Final static analysis is clean. Focused checks cover the changed workflows and
their existing regressions; the affected review suite's 73 cases passed across
the initial run and corrected single-case rerun, and both restart checks passed.
The Files Back test exposed the shell's consumed keyboard inset; using the view
inset fixed the actual embedded-tab behavior and the targeted rerun passed.
Initial new MCP wire tests needed their local fixture signatures corrected;
these were test-helper compile errors, not server failures. The pinned MCP helper
was stopped and preserved. Android CI at prior master 697995d completed and
produced a test-signed APK; this batch's platform builds run after publication.

Existing feature branches are already merged, including the exact-tree squash
of model-workflow-polish in #63. The pre-alignment dev backup's useful changes
were adapted in #64 and remain preserved. No branch deletion, recurring-agent
restart, release tag, or stable release publication is part of this batch.
Main dev/master are synchronized after the current PR merge.

## Cycle 14 — complete server JSON export

Worked directly on local dev, following the simplified dev-to-master workflow.
The existing chat export now offers server-generated JSON beside loaded
Markdown on v2. Redaction defaults on; the pinned fixture exposed that it
replaces conversation text with placeholders, so the form explicitly explains
that preserving original text requires turning redaction off. JSON retains the
server envelope without model hydration or re-encoding. Save remains visible
while options scroll; cancellation, scope changes, typed errors and retries
are handled. V1 retains the existing Markdown export.

Static analysis passed. The focused transport/export checks cover full byte
responses beyond 5,000 messages, auth and missing-session distinction,
capability fallback, buffer reuse, cancellation, Markdown and retries. The
411px render was inspected; compact enlarged-text interaction is also checked.
Live beta-18600 export returned both modes, preserved unredacted Unicode,
redacted ordinary text, and returned the declared auth/missing-session errors.
The fixture also confirmed duplicate import returns 409. Import UI remains
pending as a separate parity workflow. See the export verification document.

No feature branch or PR was created. No release tag or stable release was
published. The prior 06447b6 dev/master Android, Linux and Windows builds all
completed successfully; its dev APK artifact is 9964432494 (test-signed).
