# BB4 — bounded independent CPU work leases

Finish line: chat, setup, sign-in and terminal each own a separate expiring token;
CPU wake lock exists only while at least one valid token remains. Non-goal: UI or
connection-library edits, persistence of CPU holds or extending Android lifetime.
Owned native/OpenCode paths are implemented and device-qualified. The complete all-agent item depends on the BA integration below.

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

Native performance includes `workHeld` (aggregate) and `workLeases` with `held`,
`activeCount`, `kinds` (nonzero chat/setup/sign_in/terminal counts). No identifiers,
process output or credentials reach diagnostics. UI copy for an unavailable hold:
“This chat could not keep the phone awake. Try again.” A capped hold can say:
“The phone can rest now. Open the app to check the reply.” Technical timings and
kind counts belong under Details.

Connection-library integration request for BA: ConnectionReplySource currently
reports OpenCode in-app busy sessions only. When a local Paseo/Claude/Codex turn
is active, its gateway owner should call the same named chat lease API using a
unique opaque per-owner ID, renew before TTL, and close on confirmed idle,
cancel/disconnect/deletion/disposal. Idle agent-host readiness is never busy work.
No BA-owned connection files are edited in this lane. This extra gateway adapter
needs lane BA integration evidence before an all-agent CPU-lease claim. Native
chat admission currently requires the tracked OpenCode server; a helper-only
backend also needs a narrow validated active-helper admission before using this
API without OpenCode. A successful helper readiness check alone is not busy.
