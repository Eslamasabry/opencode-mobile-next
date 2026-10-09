# BB5 — idle stop and guarded foreground resume

Status: Dart foreground/heartbeat integration verified; BB4 qualified at
`30e5d9027`, BA hooks merged at `f561c3584`, and integration head `9485f0c7c`
merged at `7af5b544c`. Native wiring is drafted but not yet compiled or device
qualified. The coordinator lifted the memory hold with a
4GB Gradle heap, in-process Kotlin, at most two workers and at least 6GB available
memory before builds. Native/lifecycle wiring is in progress; supporting-device
qualification is still required before enabling the feature.

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

`PhoneServerIdle.check()` implements the proposed guarded resume sequence with
injected foreground, readable-owner and BA restoration callbacks. `running` and
`failure` expose bounded progress and the fixed `idle_resume_unavailable` code.
`invalidate()` revokes pending continuations; `dispose()` prevents later state
publication. This class is now wired into `PhoneServerHealing`, including BA
restoration and foreground/owner cancellation. The native channel handlers below
are drafted and remain uncompiled. Policy defaults to off. See
[the original groundwork](../qa/BB5-2026-10-08/README.md) and
[the integration checkpoint](../qa/BB5-integration-2026-10-08/README.md).

Foreground return already reaches `PhoneServerHealing.setForeground(true)` from
[`main.dart`](../../lib/main.dart). The current notification's content intent
opens MainActivity, so it uses that same path. No background FGS start from a
notification service action is required. The healing owner must serialize resume
with recovery/profile binding and recheck foreground, disposal and owner after
every await. Duplicate lifecycle callbacks share one in-flight operation.

### Idle notification and tap

An idle stop posts a normal, non-ongoing, auto-cancel notification with ID `4098`
on the existing phone-server channel. It reuses `native_phone_server_title` and
uses `native_phone_server_idle_body`: “Phone server paused while idle. Tap to
open OpenCode.” The Arabic resource is “خادم الهاتف متوقف مؤقتًا لعدم وجود نشاط.
اضغط لفتح OpenCode.”

Post only for the durable current idle-stopped generation: wanted intent remains
true, explicit Stop has not won, the owner is known and current, the generation
is positive, the app is backgrounded, and the server is not running. Deduplicate
by owner and generation. Cancel on foreground return, manual Stop, Android
timeout, owner transfer or deletion. Denied notification permission leaves the
existing foreground-return path available.

Its immutable content `PendingIntent` opens MainActivity. A real SystemUI tap
must reach the normal Activity lifecycle and the same guarded foreground resume;
an `am start` command alone does not qualify notification-tap behavior. The idle
notification has no Stop service action, starts no foreground service or helper,
and holds no CPU lease or wake lock. Android dataSync timeout handling remains
unchanged; the notification grants no extra background lifetime.

Each new post resolves `NativeStrings` in the current locale. This resource
requires no `NativeNotificationLocale` change: foreground return cancels the idle
notification before an in-app Settings locale change, and a later post resolves
the new locale.

Never use `BuiltinServerStarter.start()` or `startForLaunch()` for idle resume.
Their manual path can increment `manualReadyCount`, which recovery treats as a
request to reset its durable budget. An idle resume has a separate native path.

## Bridge and native contract

`BuiltinLinuxStatus` adds the following fields to native `status`:

| Field | Type | Meaning |
| --- | --- | --- |
| `serverIdlePolicySupported` | bool | This APK implements the complete idle contract. |
| `serverIdleEnabled` | bool | Persisted policy; default false until the person enables idle stop. |
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

`Future<BuiltinLinuxStatus> completePhoneServerIdleResume({required String
profileId, required int expectedIdleGeneration})` is the final completion
acknowledgement. The positive generation remains unchanged across native server
launch, BA helper readiness and feed reconnection. PhoneServerHealing calls this
only after the full guarded BA await succeeds and fresh foreground/owner checks.
Native compares the exact token again, confirms wanted intent and the live owned
server (and, when recorded previously live, the helper), then completes the
resume atomically. No recovery-budget or manual-ready counter changes.
The durable internal generation counter never resets; a stale acknowledgement
cannot authorize a later run.

When a helper was previously live and restored, completion clears the public
generation to zero and both stopped markers. When no helper was previously
live, retain a positive completed generation as an automatic-helper-start gate:
BA's existing zero-generation requirement prevents later row refresh from
starting that previously stopped helper. The server is running and no longer
idle stopped; a subsequent explicit foreground helper start may clear this
gate, but a background start may not. A future idle stop uses a new durable
generation. This distinction preserves prior helper intent without asking BA to
change its current public hooks.

`observePhoneAgentWork({required String profileId, required bool? busy})`
delivers current tri-state local work truth to native admission. It has no
credential or output payload. Missing, stale or wrong-owner observations deny
idle stop. BB repeats current observations with a bounded heartbeat; a cold
process begins unknown. CPU holds use an independent helper-scoped opaque chat
lease, never the server-gated OpenCode lease. A known busy run may retain its
existing capped token while observation becomes unknown; unknown never acquires
a new CPU hold. Only confirmed idle closes that logical run.

The Dart app owner sends work observations every 15 seconds. Native evidence
expires at 45 seconds; old/wrong readable aliases cannot replace evidence for
the bound runtime owner. Cold processes begin unknown.

Idle admission uses elapsed realtime while backgrounded. The interval starts
after confirmed work is idle; foreground return or new work invalidates it.
Admit only when all chat/setup/sign-in/terminal leases are absent and BA's local
agent-turn state is known idle. Unknown busy state denies idle admission. Lease
expiry alone does not prove a long capped agent turn finished.

The native stop-only timer uses one local scheduled check and an inexact
`setAndAllowWhileIdle` alarm, with a nonexported receiver and a bounded 8-second
broadcast dispatch. Each delivery rechecks current authority and work evidence.
Android may delay inexact alarms while asleep; the configured threshold is the
earliest eligible stop, not an exact wall-clock guarantee in Doze. Missing or
expired Dart observations deny a stop even when an alarm arrives. See
[Android AlarmManager documentation](https://developer.android.com/reference/android/app/AlarmManager#setAndAllowWhileIdle(int,long,android.app.PendingIntent)).

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

The merged BA prerequisite supplies these hooks; its authoritative details are
in [BA-idle-hooks-contract.md](BA-idle-hooks-contract.md):

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

Sanitized error codes: `idle_policy_invalid`, `idle_policy_unavailable`,
`idle_resume_stale`, `idle_resume_unavailable`, `agent_idle_resume_unavailable`.
No raw exception, process output, profile/lease identifier or credential appears
in user copy. Technical admission reason and budget counts belong under Details.
A refused resume leaves intent and stored budget intact; it does not silently
opt into manual Start. Exact wording/localization is frontend-owned.

## Acceptance and current blockers

BA busy truth and guarded helper restoration are merged, and foreground
orchestration passes focused Dart tests. Merged native Kotlin/Java compile and259 focused JVM cases now pass, including
rollback/admission regressions. QA target/runner builds and emulator qualification
remain gates. BB4's
OpenCode ReplyWatch lease does not establish all-agent busy truth.
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


2026-10-09 build-window checkpoint: default-off policy, channel methods and state
copy above are implemented; merged native compile/unit proof is recorded in
[BB5 evidence](../qa/BB5-integration-2026-10-08/README.md). Device acceptance is
still pending. Explicit runtime-QA builds retain the same Kotlin/Flutter ABI as
the separate instrumentation APK via existing QA-only ProGuard rules and supply
the generated integration-test plugin's release dependency. Ordinary builds
retain their existing configuration; the one-QA-runner limit remains in force.
