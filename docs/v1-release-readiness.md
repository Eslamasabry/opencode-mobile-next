# V1 release readiness

Objective: release the app with complete feature parity and polished UI/UX.
This is an active delivery checklist, not a claim that the release is ready.
The app currently declares `1.0.34+35`; a version number alone does not establish
stable-release readiness.

## Completion evidence

| Requirement | Evidence needed | Current state |
|---|---|---|
| Supported v1 and v2 workflows work end to end | Current feature matrix reconciled with implementation, protocol fixtures, and live server exercises | Incomplete. Review [backend backlog](backlog/backend.md), the [v2 matrix](opencode2-port-matrix.md), and open feature issues; older phase checkboxes are not sufficient. |
| Large histories and global search remain complete | Multiple-page sessions and messages, including newest messages beyond the old fetch cap | Partial: BE-005 global finder preserves server cursors across pages, including empty/filtered pages. BE-010 message history and scoped session inventory remain incomplete. The pinned v1 server has an upstream timestamp-tie limitation documented in the backlog. |
| Provider setup, catalogs, and MCP setup retain location and recover from failures | Request scope, completed OAuth confirmation, timeout encoding, handled concurrent errors | Implemented in the current parity batch; see BE-006–009 and focused checks in the cycle log. MCP scope/persistence claims require separate reconciliation. |
| Daily mobile flows preserve input and show current server truth | New-chat commands, refresh failures, connection edits during probes, review selection, and resolved requests | Partial: FE-005/006/009 implemented in cycle 03; review selection/patch retention and Files Back (FE-010/011) implemented in cycle 04. Resolved-request handling and remaining linked feature workflows are still open. |
| UI is usable at compact widths and enlarged text | Purposeful rendered review of welcome, chat/composer, providers, workspace/files, permissions/forms, review, and settings | Partial: existing composer evidence plus focused layout checks. New final release candidate needs a visual pass covering changed flows. |
| Existing users can install the release predictably | Exact APK package/version/signer/checksum; install and upgrade smoke evidence with data-preservation behavior documented | Not verified for a final release candidate. Follow the existing signing-lineage requirements; never substitute the CI signer for the public signer. |
| Final source and artifacts pass release gates | Clean merged commit, full platform CI, Android release build/lint, signed artifact verification and physical-device smoke | Pending final candidate. Prior clean builds are supporting evidence only. |
| Public release is available and accurately documented | Published GitHub release, verified downloadable artifacts, matching tag/source/version, current notes and compatibility limits | Pending. No release tag or publication was performed by the parity batch. |

## Delivery order

1. Complete the ready correctness and missing-workflow backlog while keeping both
   protocol adapters working. Reconcile feature issues rather than duplicating
   them or hiding supported features behind permanent capability gates.
2. Finish pagination, model/agent synchronization, and staged revert parity;
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
