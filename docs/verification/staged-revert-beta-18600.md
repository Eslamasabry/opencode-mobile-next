# Staged revert verification — beta-18600

Verified on Windows on 2026-09-05 against a disposable authenticated loopback
server reporting `0.0.0-beta-18600`. The installed 1.18.28 server was not used:
its commit semantics differ. The fixture used real Git snapshot objects and an
imported completed assistant; it made no provider request and touched no user
project. See [captured results](staged-revert-beta-18600.json).

## Runtime identity

Package: `@opencode-ai/cli-windows-x64-baseline@0.0.0-beta-18600`, downloaded
from npm and checked against registry SHA-512 integrity. Extracted executable
SHA-256: `29443AC011DC37896A650A7267C8FA232533B9410E8185EA099C1D5214E658E0`.
The helper had separate XDG data/config/state/cache directories and a generated
password. Health reported ready and the exact version before the fixture ran.

## Observed results

| Operation | Working file | Raw API messages | Staged state |
|---|---|---|---|
| Import user prompt plus completed assistant | `after` | 2 | None |
| Stage the user prompt, `files:true` | `before` | 2 | Boundary, snapshot, one file patch |
| Replace that stage with `files:false` | `after` | 2 | Prior file rollback restored first |
| Restage with `files:true`, then clear | `after` | 2 | Cleared |
| Stage with `files:false` from clear state | `after` | 2 | History-only boundary |
| Clear, restage with `files:true`, then commit | `before` | 0 | Cleared |

Commit removes the selected prompt **inclusively** and all later messages.
Staging leaves the raw message API intact; the client hides the boundary and
later rows. Clear restores only staged paths from the saved original snapshot,
so edits to those paths made after staging can be replaced.

## Reproduction

1. Create an isolated Git project containing `fixture.txt` with content
   `before` (no newline), commit it, and record `git rev-parse HEAD^{tree}`.
   Change the working file to `after`, without committing.
2. Start the verified binary with `serve --hostname 127.0.0.1 --port <unused>`
   in that project, with isolated XDG directories and `OPENCODE_PASSWORD`.
3. POST `/api/session/import` with project location, session info, a user
   message and a completed assistant after it. The assistant uses
   `snapshot: {start: <baseline tree>, files: ["fixture.txt"]}`,
   `model: {providerID: "fixture", id: "snapshot-test"}`, `agent: "build"`,
   text content, and `time.created` plus `time.completed`. Incomplete
   assistants are discarded. Native snapshot storage references the project's
   Git objects, so the baseline tree is a valid snapshot.
4. Use the returned session ID for the table's operations. Inspect the file,
   session metadata and raw message list after each phase. Stage sends
   `{messageID: <user ID>, files: <bool>}`; clear and commit have no body.

## Additional pinned source findings

The integrity-checked binary defaults to file application unless
`files === false`. Clear wakes queued execution. Prompt admission commits an
existing stage, even with `resume:false`; template, review/init and MCP prompt
commands delegate to it. The app requires explicit resolution before Send,
prompt-based commands and offline replay. Session shell execution does not
itself commit a stage.

Source module UTF-8 SHA-256 hashes:

- Revert/projector, `chunk-8mstfxwz.js`:
  `2D47181C8E0DE9DFD286D0516BB368DFD184012DF8378FC3E676008DDDCB02CD`.
- SQL helpers, `chunk-greet1wt.js`:
  `6DC78B5D3BB976C29A4E77D088D1BE4565C97FB1B15937BDE3A1FD8B69700720`.
- Transcript, `chunk-9mbpxpa9.js`:
  `C15B4BC2998319BDD216E8DF63AD2F1E835DB5E1DBECB45C750773D7B2F31593`.

The commit projector uses `message.seq >= boundary.seq`. These findings
support the live observations, not a claim about every later server version.

## Limits

The protocol has no conditional commit or conditional prompt admission.
Fresh reads and event revisions reject known stale state but cannot eliminate
another client changing a boundary between the final read and POST. The fixture
verifies file/history effects, not a physical-device release campaign or every
snapshot edge case such as ignored and oversized files.
