# BB5 — idle stop and guarded foreground resume

Status: proposed integration contract; no BB5 implementation or qualification.
BB4 native/device qualification and the BA integration below are prerequisites.

Finish line: after the configured background idle period, the app stops its
owned phone server and previously running phone-agent helper; foreground return
or a notification tap resumes only that idle-stopped runtime, without resetting
the crash-retry budget or overriding explicit Stop or Android timeout.
Non-goal: restoring arbitrary services, extending Android foreground-service
lifetime, or persisting scripts, passwords, CPU leases or authentication data.

## Ownership and current integration points

BB owns native idle admission, exact runtime drain, persisted idle policy, the
`BuiltinLinux` Dart bridge and `PhoneServerHealing`. BA owns all connection
library parts and phone-agent gateway integration. Claude owns settings/copy UI.
MethodChannel names and wire shapes must be frozen before parallel implementation.

Foreground return already reaches `PhoneServerHealing.setForeground(true)` from
[`main.dart`](../../lib/main.dart). The current notification's content intent
opens MainActivity, so it uses that same path. No background FGS start from a
notification service action is required. The healing owner must serialize resume
with recovery/profile binding and recheck foreground, disposal and owner after
every await. Duplicate lifecycle callbacks share one in-flight operation.

Never use `BuiltinServerStarter.start()` or `startForLaunch()` for idle resume.
Their manual path can increment `manualReadyCount`, which recovery treats as a
request to reset its durable budget. An idle resume has a separate native path.

## Proposed bridge and native contract

`BuiltinLinuxStatus` adds the following fields to native `status`:

| Field | Type | Meaning |
| --- | --- | --- |
| `serverIdlePolicySupported` | bool | This APK implements the complete idle contract. |
| `serverIdleEnabled` | bool | Persisted policy; default true on a supporting APK. |
| `serverIdleMinutes` | int? | Persisted threshold, 1–60; initial value 5. |
| `serverIdleStopped` | bool | An intentional idle stop, independent of user stop. |
| `serverIdleHelperStopped` | bool | This idle generation stopped a previously live phone-agent helper. |
| `serverIdleGeneration` | int? | Native monotonic admission token for this idle transition. |

Absent/malformed fields never authorize resume. Old APKs are unsupported, with
no Dart idle timer or fallback start. Numeric tokens must be nonnegative integers;
fractional, negative or malformed values are unavailable.

`Future<BuiltinLinuxStatus> setPhoneServerIdlePolicy({required bool enabled,
required int idleMinutes})` calls `setPhoneServerIdlePolicy` with
`{enabled: bool, idleMinutes: int}` and returns the complete status map. Native
validates the range and saves policy atomically before reporting success.
Turning idle-stop off does not itself start a stopped runtime or clear an
explicit stop/timeout. Policy is device-runtime scoped, not a credential store.

`Future<BuiltinLinuxStatus> resumeIdleStoppedPhoneServer({required String
profileId, required int expectedIdleGeneration})` calls
`resumeIdleStoppedPhoneServer` with `{profileId, expectedIdleGeneration}` and
returns the complete status map. Native requires the Activity to be resumed,
the same readable runtime owner and idle generation, wanted intent, valid
canonical recipe/rootfs ownership, and no explicit Stop/timeout/revocation.
Any stopped old owned children must be proven drained before launching.
Existing recovery policy and admission remain authoritative. The operation must
neither refill nor disguise exhaustion of the durable retry budget; native
decides whether the existing admission permits this resume. There is no Dart
manual-start fallback after refusal.

Idle admission uses elapsed realtime while backgrounded. The interval starts
after confirmed work is idle; foreground return or new work invalidates it.
Admit only when all chat/setup/sign-in/terminal leases are absent and BA's local
agent-turn state is known idle. Unknown busy state denies idle admission. Lease
expiry alone does not prove a long capped agent turn finished.

Idle stop retains wanted intent and the canonical server recipe, persists its
separate reason/generation, cancels pending recovery dispatches, and records
owed exact-child cleanup before drain. It stops only admitted owned server/helper
trees. Native crash recovery and sticky restoration must not reopen an
idle-stopped runtime while backgrounded. Explicit Stop, notification Stop or
`onTimeout` supersedes idle state and invalidates every pending resume token.
Android lifetime limits and timeout revocation remain unchanged.

## BA prerequisite: busy truth and helper restoration

Current public methods are `ConnectionController.resumeAgentHost()` through
`PhoneAgentsSource`, and `ConnectionController.recoverPhoneAgentBackend()`.
The first unconditionally starts the current host; the second needs an existing
backend and does not cover a cold process. Background row refresh can also call
`resumeAgentHost()` automatically. These are insufficient authorization for
idle restoration and can undo an idle stop unless their starts are gated.

Request these exact proposed BA hooks:

- `bool? localPhoneAgentWorkBusy(String profileId)`: true for any local Paseo,
  Claude or Codex turn; false only when known idle; null when unknown. The
  owning gateway updates native idle admission independently of CPU lease TTL.
- `Future<void> resumePhoneAgentsAfterIdle({required String profileId,
  required int expectedIdleGeneration})`: restore only the helper native
  recorded live at this idle stop, for the same owner/generation and foreground
  lifecycle. Coalesce calls, recheck ownership/disposal after awaits, and wait
  for bounded readiness before reconnecting existing sources/feed.

The guarded helper path must carry `idleResume: true` and
`expectedIdleGeneration` into the existing `startAgentHost` request, alongside
its existing `profileId`, password, port and authored configuration. Native
validates the token again at dispatch. Ordinary background refresh/watchdog
starts cannot bypass the idle gate, and stale helper restoration cannot start
after a newer Stop, timeout, owner transfer or deletion.

Reuse the profile's existing secure-storage host secret in
[`phone_agents_host.dart`](../../lib/builtin/agents/phone_agents_host.dart).
Do not copy it into native preferences, restore receipts, idle policy, logs or
diagnostics. Automatic restoration must not create or rotate a missing secret;
report storage unavailable and leave the helper stopped. Existing `start()`
can create a secret through `_password()`, so BA must provide the guarded
restore behavior rather than call that method blindly. No new secret persistence
is part of BB5. A helper that was not live before idle remains stopped.

## States, copy and errors

| State | Plain words | Way forward |
| --- | --- | --- |
| Running | “Running on this phone” | Normal use. |
| Idle stopped | “Paused while you were away” | “Open the app to continue.” |
| Resuming | “Starting on this phone” | Bounded progress; no duplicate starts. |
| Explicitly stopped | “Stopped” | “Tap Start when you want to use it.” |
| Android timeout | “The phone stopped background work” | “Open the app and tap Start.” |
| Resume refused/unavailable | “The phone server could not start” | “Open setup or try Start again.” |
| Helper storage unavailable | “The agents could not reconnect” | “Open agent setup and try again.” |

Proposed sanitized error codes: `idle_policy_invalid`, `idle_policy_unavailable`,
`idle_resume_stale`, `idle_resume_unavailable`, `agent_idle_resume_unavailable`.
No raw exception, process output, profile/lease identifier or credential appears
in user copy. Technical admission reason and budget counts belong under Details.
A refused resume leaves intent and stored budget intact; it does not silently
opt into manual Start. Exact wording/localization is frontend-owned.

## Acceptance and current blockers

Safe idle-stop is blocked today by missing BA busy truth for local agent turns,
missing owner/token guarded helper restoration, and unguarded automatic helper
starts. BB4's OpenCode ReplyWatch lease does not establish all-agent busy truth.
Do not enable a partial timer that can interrupt a live agent or immediately
restart a helper after stopping it.

Focused regressions must prove persisted enabled/minutes policy; malformed or
old-APK refusal; work/unknown-busy admission denial; elapsed-time idle threshold;
single foreground/notification resume; unchanged durable retry budget; no manual
reset event; Stop/timeout precedence; owner/generation/deletion/disposal races;
prior-live helper restoration using its unchanged secure secret; no restoration
of a previously stopped helper; and background refresh unable to defeat idle.

Locked emulator evidence must show server and helper exact PIDs stop after the
idle threshold, remain stopped while backgrounded, and resume on return/tap.
Repeat with a local agent turn, setup, sign-in and terminal active; each must
block idle stop. Exercise explicit Stop and timeout during a pending resume and
prove no late replacement. Host/JVM tests do not establish this device behavior.

Native release and all-agent BA integration/device proof remain separate gates;
this document is a dependency contract, not completion evidence.
