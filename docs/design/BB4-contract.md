# BB4 — bounded independent CPU work leases

Finish line: chat, setup, sign-in and terminal each own a separate expiring token;
CPU wake lock exists only while at least one valid token remains. Non-goal: UI or
connection-library edits, persistence of CPU holds or extending Android lifetime.
The original native/OpenCode slice at `618a94c1` is device-qualified on QA2213.
The helper chat extension is qualified by focused native/Dart checks, clean
analyzer and fresh QA2214 release/locked-device scope evidence. Real-account
authentication and actual Claude/Codex turns remain separate qualification. See
[original QA](../qa/BB4-2026-10-07/README.md) and
[helper extension QA](../qa/BB4-agent-work-2026-10-08/README.md).

Dart `BuiltinLinux.setChatWorkLease({required String leaseId, required bool on,
Duration hold = 10 minutes})` calls native `setChatWorkLease` with
`{leaseId, on, forMs}`. Reply is `{held: bool, capped: bool}` for that own lease.
Opaque IDs match `[A-Za-z0-9_.-]{1,80}` and contain no account/profile/session text.
`held=false` does not mean another work owner released its hold. Missing/invalid
reply refuses admission. Existing `holdAwakeForWork` maps to its own legacy token.

Renew the same logical ID while busy. Native uses elapsed realtime, max15min TTL,
immutable max6h token and overlapping aggregate lifetime, max128 active tokens.
Expired/revoked tokens cannot revive; logical idle (`on=false`) closes the name.
Native scheduled expiry runs even when Dart is paused. No lease bypasses Android
foreground-service rules or its onTimeout. Held tokens are process-local.

Helper chats use the separate Dart API
`BuiltinLinux.setPhoneAgentChatWorkLease({required String profileId,
required String leaseId, required bool on, Duration hold = 15 minutes})`.
MethodChannel `setPhoneAgentChatWorkLease` takes `{profileId, leaseId, on, forMs}`
and returns the same `{held, capped}` fields. `profileId` is the canonical helper
owner matching `[A-Za-z0-9_-]{1,80}`. Native requires that owner's exact tracked
`agent-host.<profileId>` process to be alive, its profile unblocked, and the
foreground service actually shown before admitting a positive hold. OpenCode
server readiness is not required for this helper scope. OFF always releases only
the exact helper profile and lease ID, including after helper loss.

The native host freezes `agentChat(profile, name, on, holdMs, helperRunning)`,
`helperGone(profile)` and `agentProfiles(): Set<String>`. OpenCode and agent names
occupy separate scopes; equal names in different profiles do not share tokens.
`serverGone()` closes only OpenCode tokens; `helperGone(profile)` closes only
that helper profile's tokens. Both retain exhausted logical names until OFF.
All chat scopes and adopted native/terminal owners share the 128 logical-owner
bound; CPU tokens remain separately capped by the registry.

ReplyWatch holds one unique aggregate chat token while any OpenCode in-app session is
busy. A second chat ending keeps the hold. Idle, source switch or disposal drains
only this watch's token, including a delayed successful acquire. Native cap ends
renewal until actual idle. Setup whole-job, native private sign-in process
and terminal session tokens are independently owned. Setup admission is captured
before requesting its service/worker. Delayed workers reuse that one admission;
installers inside the same logical job acquire no extra token after expiry or
revocation. Standalone checks keep a separate bounded operation token. Idle server/Paseo helper
processes get no CPU hold. Existing terminal lifecycle snapshots supply exact
session ends; unknown visibility revokes CPU permission while retaining closed
logical owners. A pending long expiry must yield to earlier work observations.

`PhoneAgentWorkWatch.observe({required String? profileId, required bool? busy})`
owns one random opaque ID for aggregate agent work under that canonical helper
owner. Confirmed busy starts its hold; unknown does not create a run or end an
already busy run. Renewal uses the same ID every five minutes, with a fifteen
minute TTL and six-hour ceiling. Native cap, failed/refused handoff or invalid
clock closes CPU renewal while preserving the busy run. An ON request with zero
hold releases any existing token and retains its native logical key; it cannot
create a token. Late replies for that retained closed run use the same CPU-only
close. Confirmed idle, owner loss/switch or disposal sends OFF, including for a
late successful acquisition. Queue serialization preserves an owed old OFF
before a new run's ON. `dispose()` prevents later observation or renewal.

`NativeWorkLeaseHost.logicalWorkBusy(): Boolean?` is independent of CPU TTL:
retained chat keys or positively live native/terminal work mean true, confirmed
no work means false, and unavailable/replaced ownership means unknown unless
another positive busy source exists. Liveness callbacks run outside the host
lock. A capped setup job or agent chat is still busy; idle-stop must never infer
completion from `workHeld=false` or an expired lease.

Stop, service loss and timeout close fresh chat admission and revoke foreground
tokens. `foregroundGeneration(): Long` snapshots its revision;
`authorizeForegroundWork(expectedGeneration: Long): Boolean` opens admission only
for that still-current revision. Native authored manual server launches and
foreground private-child launches capture before dispatch and authorize after
registration. A pre-Stop launch cannot authorize after Stop. Sticky restoration
and notification updates do not reopen admission. Reauthorization admits fresh
logical runs and never revives retained capped IDs. Exact OFF and zero-hold
closure remain available while admission is closed. Setup uses its separate
service and revocation authority.

Native performance includes `workHeld` (aggregate) and `workLeases` with `held`,
`activeCount`, `kinds` (nonzero chat/setup/sign_in/terminal counts). No identifiers,
process output or credentials reach diagnostics. UI copy for an unavailable hold:
“This chat could not keep the phone awake. Try again.” A capped hold can say:
“The phone can rest now. Open the app to check the reply.” Technical timings and
kind counts belong under Details.

BA integration freezes the optional injection
`PhoneServerHealing.localAgentWorkBusy: bool? Function(String readableProfileId)?`.
Healing queries it with the readable server profile alias, so BA can validate
readability and resolve shared agent inventory. Separately, healing maps that
alias through `ProfileStore.phoneAgentOwnerId(alias)` and passes the canonical
helper owner to `PhoneAgentWorkWatch` and the native bridge. The readable alias
and canonical helper owner are not interchangeable. Missing/throwing inventory
is unknown; idle helper readiness never means busy work. Connection/store
notifications trigger observations; the BA adapter must notify on agent-work
changes and provide confirmed idle/cancel/deletion truth. The app-lifetime
provider now assigns `ConnectionController.localPhoneAgentWorkBusy` to this hook;
focused provider tests use a mocked controller. Merged BA busy-inventory offline checks and fresh QA2214 native/device scope evidence
are recorded in docs/qa/BB4-agent-work-2026-10-08/README.md. These prove the lease
integration; real-account authentication or real Claude/Codex turns remain separate.
No BA-owned connection files are edited in this lane.
