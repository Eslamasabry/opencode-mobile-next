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
