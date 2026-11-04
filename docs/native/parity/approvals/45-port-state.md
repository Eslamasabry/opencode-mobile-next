RESULT: APPROVED WITH FIXES

# Approval — job 45-port-state (Flutter state and connection logic)

Reviewer: Opus 5.5 (cloud). Branch `native/p12x/job-45-port-state`. P12 head `b6c30a59` was already an
ancestor (merge was a no-op). Reviewed the nine job commits `a35ca077`…`50a4ac46` against
`behaviors.jsonl`, the Flutter sources in `lib/` and `test/`, `native/AGENTS.md` and plan 01. No Gradle build,
tests or APK here (Google Maven is blocked in this environment); the build VPS compiles next.

## What holds

- Ledger: exactly 87 assigned paths, one disposition each (9 already there / 7 ported now / 15 not needed /
  56 blocked). Blocked rows name real missing native owners (phone-agent host/feeds, lifecycle/location
  coordinator, automatic-activity history, quota adapters, integration contracts); none is papered over with a
  stub. "Not needed" rows cite D6/D17/plan 02 decisions.
- connection_check: 15 s bound, 1500 ms warning, five steps and reason mapping match
  `lib/state/connection_check.dart`; reachable from the Servers row/sheet; saved trust/credentials, cleartext
  and pin rules enforced by the factory probe; nothing logged.
- read_state: `oc.sessionViews.<id>` (2048 cap, oldest dropped) and `oc.returnBrief.<id>` registered in the
  deletion sweep and drained; remote receipts only behind explicit per-server consent (off by default).
- Requests/forms: shade actions carry a per-post random token bound to the exact saved owner, graph and
  request incarnation; replaced/resolved/process-dead actions fail closed; PendingIntents immutable, receivers
  not exported. Reviewed-card identity is used by the card, header and both automatic-approval callers. Form
  transport matches `docs/opencode2-protocol-notes.md` §8 (revision re-read, one attempt per revision). Only
  multiline, non-hidden, non-sensitive form drafts persist, under `oc.draft.form-<sha256>.<profile>`, registered
  for deletion.
- appearance_guide / whats_new: Flutter keys `oc.appearanceGuide` / `oc.whatsNewSeenBuild` (device-wide, as in
  Flutter); the launch path is gated on a fresh MAIN launch and the first-run/migration fence; About replays.
- `MANAGE_EXTERNAL_STORAGE` is requested only from the visible shared-storage notice (plan/16, plan/11).
- Kit-only UI throughout; all copy in string resources with matching EN/AR keys, all used, none missing.
- No credential reaches logs, notifications or storage; no server URL is launched outside the safe opener.

## Fixes made in this review

1. `ChatForms`: a shared-folder change hid the chat's forms for good and the next pending update deleted the
   person's saved draft. It now rebinds to the new source, and a draft is deleted only when the ask left
   `FormRequests` itself.
2. `ConnectionRequests`/`FormRequests`: the Connected refetch no longer runs inside the event collector, and
   the connection-wide form read has the same 3 s bound as the session read.
3. File viewer, folder and project page: every resume ran a full reload (skeleton, re-download, Termux probe).
   Resume now re-checks only a page still blocked by shared storage, as Flutter asks the gate only on open.
4. `SharedStorageWiring`: a non-shared folder returns None before any warm-up/graph read can fail it.
5. `NativeSessionReads`: the remote receipt was sent while holding the profile lane that brief publication
   (under the runtime-wide order lock) waits on; it is now sent after the local commit, outside the lane.
6. Connection check timing uses a monotonic mark instead of the wall clock.
7. Standalone What's new: Back after "Show me" left the person in a Settings hub they never opened; it now
   leaves the route.
8. `KitChip.kt`: reverted the whole-file reformat (it merged the structured States/Text/Cap/Motion/Replaces
   KDoc lines); only the additive `KitStepChip` remains.
9. Manifest: `READ_EXTERNAL_STORAGE` maxSdk 29 → 32 (plan/11, Flutter).
10. Shade review compares the saved profile using the graph's `managedOnPhone`.
11. Report corrected: pending_reads `covers` skip not ported; provider route names only in the picker;
    What's new has no 3.0.0 notes yet; read-consent migrated marker reads as off; storage interface deviation.

## Follow-ups for the coordinator (not blocking)

- Shared modules touched (rule 14): `KitStepChip` (`:ui:kit`), `SettingsRoute.WhatsNew` (`:ui:routes`), six new
  `strings_*` files (`:ui:words`) — owners to accept at integration.
- plan/11 CR11-4 chose `AppPermission.AllFilesAccess`; the job added the rejected `SharedStorageAccess`
  interface in `:core:domain`. Reconcile with the platform owner.
- `SharedStorageNotice` is repeated in three screens — a kit candidate.
- Connection check: a missing secret shows the password reason on both Address and Sign-in;
  `InvalidAddress` maps to the "server changed" copy.
- Shade: reposts re-alert after a full reconnect (consider only-alert-once for an unchanged request); a
  `Blocked` show mints a fresh token on every update.
- `FormRequests.attempts` is unbounded; `Oc2Gateway.decodeForms` rejects the whole list for one bad row
  (Flutter skips it).
- Appearance guide taps before `load()` completes act on the empty record.
- Native has no reachable equivalent for most blocked rows; they stay blocked, not dropped.

Not verified here: compilation, tests, device behavior (owner: testing after all coding).
