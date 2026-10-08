# BA — local agent idle hooks for BB5 / BB7

Finish line: BB can distinguish known idle phone-agent work from busy or unknown
work, and request a guarded foreground restoration of the helper belonging to
one native idle-stop generation, including after a cold Dart process.

Non-goal: a Dart idle timer, a new native busy bridge, manual server startup,
retry-budget reset, background restoration, or native/device qualification.

This answers the “BA prerequisite: busy truth and helper restoration” section of
BB's `docs/design/BB5-contract.md`. BA owns the connection/gateway/host hooks;
BB owns native admission, the persisted idle marker and server restoration.

## Public connection hooks

```dart
bool? localPhoneAgentWorkBusy(String profileId);

Future<void> resumePhoneAgentsAfterIdle({
  required String profileId,
  required int expectedIdleGeneration,
});
```

Both methods belong to `ConnectionController`. Use the canonical local
phone-agent owner, including a protocol-switch alias of that owner. A remote,
deleted, replaced or unreadable owner cannot authorize helper restoration.

### 1. Busy truth, independently of CPU lease TTL

`localPhoneAgentWorkBusy` returns `true` when any observed local agent turn is
busy; `false` only after complete, current helper inventory establishes known
idle; and `null` when that proof is unavailable. This covers local Paseo agents,
including Claude and Codex, rather than only the selected folder or visible
chat. A disconnected helper, incomplete inventory or stale owner is unknown.
Busy evidence wins over an otherwise unknown inventory. A helper positively
observed not running may establish `false` in the absence of busy evidence.
A connected gateway whose inventory is unknown remains `null`; a liveness
observation cannot turn that incomplete connected inventory into known idle.

The gateway exposes `bool? localWorkBusy` and `Stream<void> localWorkChanges`.
Host inventory and session events update this state across folders. Complete
parent and child coverage is required, including children of closed/error parent
records. An unsupported child list, pagination/size/budget limit, malformed
ordering or a newly observed parent without coverage stays unknown. Reads use
request-start revisions so intervening pushes win over stale responses. Active
turn/child overlays survive stale archived or absent snapshots.
Connection notifications propagate changes so BB can reread the public getter. CPU lease
expiry, elapsed time or a missing visible chat does not change a busy turn to
idle.

BB must treat `null` as busy and independently deliver the observation to its
native idle admission. BA adds no invented MethodChannel method: BB owns that
wire integration. After a cold process, native admission must deny idle while
the observation is absent or unknown; a persisted helper-live marker is not
proof that work is idle. An unsupported or disconnected gateway never supplies
an optimistic `false`.

### 2. Foreground restoration and cold processes

BB restores the idle-stopped **server first**, using its guarded native path,
then calls `resumePhoneAgentsAfterIdle` for the same owner and generation. BA
does not call `BuiltinServerStarter.start()` / `startForLaunch()` and does not
produce a manual-ready event or refill recovery's durable retry budget.

The helper hook can establish the local owner host after a cold Dart process;
it does not require an existing Paseo backend. It reads native idle status and
requires complete supported fields, a nonnegative integer generation matching
the argument, and the native marker recording that this generation stopped a
previously live helper. A helper that was not live before idle remains stopped.
Missing/malformed/unsupported status never authorizes startup.

Restoration is permitted only while this controller remains foreground, alive
and bound to the same owner. Duplicate calls for the same owner and generation
share the operation. Every await is followed by ownership, lifecycle and token
checks; owner deletion/replacement, a newer Stop/timeout or generation, and
backgrounding prevent a late restore from reconnecting sources/feed.

### 3. Guarded host dispatch and existing secret

The optional host port is `PhoneAgentIdleHostPort`:

```dart
Future<PhoneAgentIdleState> idleState();
Future<void> resumeAfterIdle({
  required int expectedIdleGeneration,
  required bool Function() stillCurrent,
});
```

Hosts without the port refuse guarded restoration.

The built-in implementation reads the profile's **existing** secure-storage
helper secret. Automatic restoration never creates or rotates a missing secret
and never copies it to native preferences, idle receipts, logs or diagnostics.
Secure-storage failure or a missing secret leaves the helper stopped.

At native dispatch, the existing authored `startAgentHost` request carries its
usual `profileId`, password, port and configuration, plus `idleResume: true` and
`expectedIdleGeneration`. Native must independently validate owner, foreground,
generation, prior-live marker and Stop/timeout precedence at dispatch. Dart
checks are not a substitute for that native validation.

Strict status reads, the existing-secret read and native launch dispatch each
have an eight-second bound. After dispatch, BA requires a successful Paseo
hello with the pinned helper version **0.9.2**, within a readiness window of at
most 30 seconds. It then reconnects existing sources and the feed. This path
does not create or resume an agent chat. Native receipt authorization is read
again after readiness and after feed reconciliation, so a Stop or new generation
during reconciliation cannot report successful restoration. A refused or failed
restore has no manual-start fallback.

### 4. Ordinary starts must respect idle state

Automatic row refresh, source synchronization and watchdog recovery cannot
restore an idle-stopped helper in the background. The ordinary helper-start
path honors the native idle gate; the guarded hook is the explicit foreground
path. On supporting APKs, automatic starts require a complete status receipt
with both idle markers cleared, generation **0**, wanted intent and a running
server. A positive generation blocks automatic row/watchdog startup even after
its helper-stopped marker has been consumed. An explicit user foreground start
can use cleared markers at a later generation. Unsupported legacy APKs retain
their ordinary-start behavior but cannot use guarded idle restoration.
The optional `PhoneAgentGuardedStartPort` provides
`Future<void> startWhileCurrent({required bool Function() stillCurrent})`.
It carries controller ownership/lifecycle validity into ordinary production
startup. It rechecks before secret access, after the secret
await immediately before native dispatch, and after dispatch completes. Native
must independently gate ordinary starts against background, Stop and idle state.
Cold-process row creation, keep-up behavior and stale recovery callbacks do not
clear the native idle marker or authorize a background start.

BB must retain its persisted idle-stop reason/generation across process death,
and keep background crash/sticky restoration gated. User Stop, timeout and
owner revocation invalidate the marker/token. No BA hook overrides those
decisions.

## Errors and frontend handling

Failures use a `ProductException` with fixed plain-language copy and a way
forward; native output, exception text, identifiers and secrets never become
user copy. Technical admission information belongs under Details.

Both errors have the complete message:
“The agents could not reconnect. Open agent setup and try again.”

| Condition | `ProductException.cause` |
| --- | --- |
| Stale owner, generation or lifecycle; strict receipt refuses authorization | `idle_resume_stale` |
| Unsupported host port; unavailable storage or native call; bounded helper readiness failure | `agent_idle_resume_unavailable` |

BB owns idle-server states and frontend localization. The caller must await
the helper hook before declaring the helper restored. The getter's `null` state
is admission information, not an error dialog.

## Acceptance and remaining integration gates

Focused offline tests must cover complete idle inventory, an active turn in
another folder, disconnected/unknown state, busy events independent of CPU
leases, same-generation coalescing, cold host restoration, unchanged existing
secret, missing-secret refusal, bounded readiness, and owner/generation/
foreground/deletion/disposal races. Automatic refresh must not undo idle stop.

BB's native implementation and a supporting APK remain prerequisites for idle
admission and guarded launch. An old/unsupported APK stays fail closed. Offline
tests establish these Dart contracts; they do not qualify native exact-child
drain, cold-process persisted markers, foreground-service lifetime or emulator
behavior. Device qualification belongs to the coordinated BB5 / BB7 batch.
