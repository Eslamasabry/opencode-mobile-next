# V1 release readiness

Objective: release the app with complete feature parity and polished UI/UX.
This is an active delivery checklist, not a claim that the release is ready.
The app currently declares `1.0.34+35`; a version number alone does not establish
stable-release readiness.

## Completion evidence

| Requirement | Evidence needed | Current state |
|---|---|---|
| Supported v1 and v2 workflows work end to end | Current feature matrix reconciled with implementation, protocol fixtures, and live server exercises | Incomplete. Review [backend backlog](backlog/backend.md), the [v2 matrix](opencode2-port-matrix.md), and open feature issues; older phase checkboxes are not sufficient. |
| Large histories and global search remain complete | Multiple-page sessions and messages, including newest messages beyond the old fetch cap | Client pagination implemented: global finder, message history, and scoped v2 session inventory preserve server cursors, including empty pages. Recent, Archived, and command destinations load older sessions; direct session reads cover chats outside loaded inventory. Pinned v1 scoped listing retains its native contract without an invented cursor; its global endpoint has an upstream timestamp-tie limitation documented in the backlog. Final live-server coverage remains part of release verification. |
| Provider setup, catalogs, and MCP setup retain location and recover from failures | Request scope, completed OAuth confirmation, timeout encoding, handled concurrent errors | BE-006–009 implemented. Cycle 13 corrects BE-011: v2 adds at runtime in the selected location, with the restart limit verified live; v1 retains persistent project/global save. Final native workflow verification remains part of release validation. |
| Daily mobile flows preserve input and show current server truth | New-chat commands, refresh failures, connection edits during probes, review selection, per-session model/agent state, resolved requests, unread completions and session notes | Partial: FE-005/006/009 implemented in cycle 03; review selection/patch retention and Files Back in cycle 04. V2 selections and offline snapshots implemented in cycle 07. Staged revert implemented in cycle 08 with pinned-server file/history verification. Cycle 09 implements scoped permission/question sheets and shared reply guards. Cycle 10 implements unread completions, foreground read receipts and privacy opt-out/local fallback. Cycle 11 implements the bounded agent note with draft/conflict protection and pinned-server authorization, size and storage verification. Remaining linked workflows and final cross-client/device verification are pending. |
| UI is usable at compact widths and enlarged text | Purposeful rendered review of welcome, chat/composer, providers, workspace/files, permissions/forms, review, and settings | Partial: existing composer evidence plus focused layout checks. New final release candidate needs a visual pass covering changed flows. |
| Usage totals reflect the selected period and project | Server aggregation, device timezone, empty/error states and capability checks | Cycle 12 implements Settings usage and cost with four calendar ranges, all/current-project scope, tokens, models and tool reliability. Thirty focused checks and the [pinned-server fixture](verification/usage-beta-18600.md) passed. Native timezone plugin integration still needs platform CI and the final device smoke. |
| Existing users can install the release predictably | Exact APK package/version/signer/checksum; install and upgrade smoke evidence with data-preservation behavior documented | Not verified for a final release candidate. Follow the existing signing-lineage requirements; never substitute the CI signer for the public signer. |
| Final source and artifacts pass release gates | Clean merged commit, full platform CI, Android release build/lint, signed artifact verification and physical-device smoke | Pending final candidate. Prior clean builds are supporting evidence only. |
| Public release is available and accurately documented | Published GitHub release, verified downloadable artifacts, matching tag/source/version, current notes and compatibility limits | Pending. No release tag or publication was performed by the parity batch. |

## Delivery order

1. Complete the ready correctness and missing-workflow backlog while keeping both
   protocol adapters working. Reconcile feature issues rather than duplicating
   them or hiding supported features behind permanent capability gates.
2. Verify the implemented pagination and
   model/agent synchronization against live servers;
   reconcile the remaining v2-native surfaces with the captured contract and live
   behavior. Verify claims about MCP persistence before describing them in UI.
3. Perform the final purposeful mobile UX pass, resolve its concrete findings,
   and update release notes from the actual shipped behavior.
4. Build and verify a signed final candidate through the existing release
   workflow, exercise install/upgrade and core flows, then publish and verify the
   release. Keep the goal active until all requirements above are evidenced.

Release sources: [release notes draft](release-alpha-notes.md),
[Android release workflow](../.github/workflows/android-release.yml),
[release preflight](../scripts/release.sh), and
[draft release helper](../scripts/cut-alpha.sh). Old public-launch audits contain
historical branch, licensing, and CI claims; inspect current state before using
them as blockers or completion evidence.
