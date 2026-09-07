# Product preview checkpoint — 2026-09-08

This integration checkpoint preserves the locally developed Codex capability and
recovery fixes, Termux paste guide and persistent installation clock, reduced
Ubuntu dependency installation, Android launcher actions, offline delivery
review, Workspace attention hierarchy, and task-card priority/progress/copy.
It is a branch baseline for isolated feature work, not a release or a claim that
the full repository gate passed.

## Evidence

- Combined focused check: 141 tests passed across Workspace, accessibility,
  localization, launcher/share routing, offline queue and profile deletion.
- Final affected-file rerun: 77 tests passed, including the corrected navigation
  hit targets, 12 task-card cases, queue concurrency and launcher routing.
- `flutter analyze --no-pub`: no issues, pinned Flutter 3.47.2.
- Independent source reviews passed for Workspace, task cards and the corrected
  queue concurrency paths. The earlier queue findings are superseded by the
  serialized profile-deletion and dispatch-bookkeeping fixes.
- Workspace capture: two real-widget fixture renders at 390 px, dark theme.
  Task-card capture: seven renders covering light/dark, 320 px with 2x text,
  filtered/full tasks and copy feedback. A truncated filter label was corrected
  and rechecked visually. These are fixture captures, not live-server journeys.
- Local x64 debug APK compiled, installed with `adb install -r`, and launched on
  emulator-5554. Version 1.0.36+37; update time 2026-09-08 00:08:26 local.
  SHA256: `9005b7b3cc237a94c0ee06071db605b1ab717eee1441ef3b6e46200995cc357e`.
  Android registered both static shortcuts. Existing app data was retained.
- Lean bootstrap: matched apt plans reduced newly installed packages 667 to 410
  and archive size 228 MB to 66.4 MB. Corrected PRoot/Node bootstrap installed
  pinned OpenCode 1.18.29; loopback health returned healthy. The 121-second npm
  stage reused the successfully installed candidate prerequisites after the
  initial io_uring failure; it is not a complete fresh-install timing comparison.

## Remaining validation and compatibility

The earlier full serial test run stopped at chunk 4, with Windows golden-render
differences and a Bash PATH failure; later source edits invalidate that candidate.
No current full-suite pass is claimed. Full frozen-candidate verification,
physical-device journeys, and native cold/warm shortcut acceptance remain open.
No signing secrets, native CI, release tags or publication were used. This debug
APK is an emulator preview, not a replacement for the maintainer's stable signer.

Queued prompts now persist a dispatch marker before sending. Uncertain delivery
stays available for explicit review rather than automatically resending. Older
builds do not understand the marker; downgrading with marked queue entries can
reintroduce automatic replay. Accepted-but-unrecorded knowledge is retained in
memory and explanatory error text; after restart the entry is reviewed as
unconfirmed. Per-entry queue persistence adds writes in exchange for recovery
safety.

## Worktree workflow

The maintainer requested one end-to-end feature owner per branch/worktree on
2026-09-08. New feature branches start from this checkpoint. Each owner implements
the full user journey, tests and evidence in its own checkout. The coordinator
reviews the complete branch and runs integration checks before merging; no worker
merges another branch or edits the coordinator checkout. Machine-heavy Flutter
and native operations remain serialized to avoid resource contention.
