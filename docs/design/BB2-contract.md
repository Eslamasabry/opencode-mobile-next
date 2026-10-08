# BB2 native crash supervision contract

Date: 2026-10-07; verified on 2026-10-08. Status: IMPLEMENTED / FOCUSED CHECKS AND NATIVE EMULATOR ACCEPTANCE PASS. OpenCode2 is restored and connected. The coordinator relaxed pre-integration restoration to installed app/data and runnable server, because BA5 shared-agent ownership was absent from the test APK; this requirement is met. Native changes remain unreleased.

Finish line: killing the exact tracked OpenCode server revives it through native
1–60 second backoff, resets that backoff after 30 seconds of stable uptime,
honors persisted Stop intent and the existing policy and three-attempt budget,
and survives cancellation and generation races.

Non-goal: BB3 app-process/sticky-service restoration, boot receivers, wake-lock
policy, agent launch changes, account authentication, or UI changes.

## Authority and stored-format migration

`BuiltinLinux` owns the tracked process and autonomous exit supervisor. Its
in-memory script is reused only for that process; the script is not persisted.
Native code is the sole writer of the migrated retry count. Dart confirms health
and records automatic acts through the existing store; it does not reserve a
second budget. Three durable reservations are the lifetime limit until a
person's explicit Start is confirmed healthy. Stable uptime resets delay only.

Migration first stages the existing spent count and pending/confirmed receipt
in private native preferences while disabled. Existing native records always
win. Dart then saves `oc.builtinRecovery.<profileId>` as
`{"version":2,"nativeAuthority":true}`, which older APKs reject. Native binding
and every reservation read this durable marker before admitting work. A failed
stage leaves v1 retryable; a failed marker write leaves native scheduling
disabled. Missing/corrupt native records after v2 fail closed and cannot create
fresh retries. Older APKs cannot spend the stale v1 count after rollback.

Native JSON budget version1 lives at `oc.builtinRecoveryBudget.<profileId>`;
archived confirmed receipts live at `oc.builtinRecoveryReceipts.<profileId>` in
private `builtin_server_recovery` preferences. Receipts contain only attempt ID,
generation, counters and timestamps; no output or credentials. The archive is
bounded at16 and refuses overflow without discarding unrecorded acts. Existing
`suspendForProfile` drains work and deletes both records before scoped Dart
storage deletion. The private device binding and Stop flag are not per-profile
secrets.

Admission reads the existing pinned legacy Android preferences implementation
(`FlutterSharedPreferences`, `flutter.oc.automation.<profileId>`) without writing
its cache. Malformed/unknown policy or marker denies admission. Absent policy
uses the explicitly enabled default binding. Profile change, Stop, policy
disable, deletion and manual replacement invalidate scheduled generations.
Pausing cancels old foreground-owned recovery proof while retaining independently
authorized native supervision. The existing foreground service stays alive only
for a bounded admitted retry window and its children; no new wake lock is used.

## Callable MethodChannel contract

All methods use `io.github.eslamasabry.opencode_mobile/builtin_linux`. IDs are profile scoped; scripts/passwords
never appear in recovery snapshots.

| Method | Arguments | Result / responsibility |
| --- | --- | --- |
| `status` | none | Existing status plus `serverRecoveryAuthority`, `serverRecoveryScheduled`, and live `serverRecoveryGeneration`. |
| `stageServerRecovery` | `profileId`, `legacyBudget` | Persist spent count while disabled; existing native record wins. Return authoritative budget. |
| `bindServerRecovery` | `profileId`, `enabled` | Activate an existing valid record after marker acknowledgement. Return budget. |
| `serverRecoveryBudget` | `profileId` | Return attempts, nextAt, pending, eventId, recoveryGeneration, confirmedAt and revision. |
| `restartServer` | script, port, expectedGeneration | Durably reserve one native attempt before dispatch. |
| `confirmServerRecovery` | expectedGeneration | Confirm that exact live process after foreground Dart health proof. |
| `updateServerRecoveryReceipt` | `profileId`, budget | Revision/count CAS for receipt changes only; cannot reserve or reset attempts. Return updated budget. |
| `serverRecoveryReceipts` | `profileId` | Return bounded historical confirmed receipts for existing automatic-act recording. |
| `ackServerRecoveryReceipt` | `profileId`, eventId | Remove archived receipt only after act storage accepts it; never refund an attempt. |
| `confirmManualServerStart` | `profileId` | Reset after a same-profile/current-process explicit Start health proof, including when automatic policy is off. |
| `unbindServerRecovery` | `profileId` | Synchronously revoke scheduling, then persist revocation. |
| `deleteServerRecovery` | `profileId` | Revoke and erase scoped budget/receipts through the existing deletion hook. |

All recovery bridge failures forward `BuiltinLinuxException` with code
`recovery_unavailable` and plain message “The phone server could not restart.”
Raw exception details do not reach frontend copy or notifications.

## Frontend states and way forward

The existing `BuiltinRecoveryState` exposes phase, profileId, attempts and
nextAttemptAt. Native-authority launch recovery never dispatches an unattended
manual Start: opening/recreating the app cannot replenish an exhausted count or
supersede a scheduled native retry. A live stale/unhealthy native-owned process
remains unconfirmed; the person must choose Start for replacement.

| Phase | Plain copy / way forward |
| --- | --- |
| waiting | “The phone server stopped. Trying again soon.” Wait for the admitted native retry. |
| restarting / checking | “Checking the phone server.” Wait for health proof. |
| ready | “The phone server is ready.” |
| exhausted | “The phone server stopped after three restart attempts. Start it to try again.” |
| stopped | “The phone server is stopped.” Choose Start. |
| unconfirmed | “The phone server could not be confirmed. Start it to try again.” |
| storageUnavailable | “The restart setting could not be saved. Start the server to try again.” If storage remains unavailable, recovery stays disabled. |
| idle / paused | No active foreground health check; native admission still honors its persisted policy and Stop intent. |

UI/localization consumes this contract under its own ownership; this slice does
not edit Connection or UI.

## Qualification boundary

Focused JVM, bridge/consumer/healing tests and exact red probes accompany this
slice. Device acceptance uses actual packaged OpenCode, authenticated loopback
health, exact executable PIDs, activity-absent recovery, all three admitted
restarts, fourth-crash exhaustion, policy-off manual reset, scoped cleanup and
persisted Stop. It reruns BB1 diagnostics/deletion/persistence on the final APK.
Results are recorded in [BB2 evidence](../qa/BB2-2026-10-07/README.md).

OS reclamation of the Android app process belongs to BB3 sticky restoration.
Explicit Android force-stop sets stopped-package state and remains stopped until
the person opens the app; force-stop/reopen tests persisted Stop intent only.
Native changes need a new owner-approved release. This slice does not publish.

The shared-emulator test preserves existing saved configuration and account state. After isolated acceptance, restore the app through its existing Start/connect path and verify OpenCode 2 connected and Claude Code Ready. This QA authorization does not enable sticky restoration or reboot behavior.

Coordinator continuation: merge feat/genui-fe without rebasing before BB3 and build future device APKs from that merged branch. BA5 supplies shared phone-agent ownership; do not move credentials. BB6 browser account qualification is skipped pending accounts.
