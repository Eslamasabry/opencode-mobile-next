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
