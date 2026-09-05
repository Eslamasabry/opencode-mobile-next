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
