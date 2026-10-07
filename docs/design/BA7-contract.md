# BA7 — Silent turn classification

Finish line: a running turn that stops emitting relevant events is classified
within 60 seconds as model slow, helper down or an interrupted/unconfirmed
connection, without resending its prompt or cancelling it.
Non-goals: automatic retry, provider failure diagnosis from error strings,
network settings changes, native lifecycle changes, and new UI components.

## Frozen domain API

`lib/domain/turn_stall.dart` is protocol-neutral and reads no credentials. It
exports `turnStallSilence` (45 seconds), `turnStallTick` (5 seconds), and
`turnStallProbeBudget` (10 seconds). The connection timer must honor all three:
45 seconds silence + at most 5 seconds timer delay + 10 seconds combined probe
budget = at most 60 seconds in an active foreground app. Android suspension does
not promise timer execution; reset/reconcile observations on resume.

`TurnStallEvidence(transportConnected:, helperRunning:, endpointReachable:)`
accepts only safe booleans. `helperRunning` and `endpointReachable` are nullable;
unknown remains unknown. `classifyTurnStall(evidence)` returns:

| Evidence | `TurnStallKind` | User copy / way forward |
| --- | --- | --- |
| Helper observed stopped (`helperRunning == false`) | `helperDown` | The agent helper stopped. Restart it and try again. |
| Stream connected and endpoint probe succeeded | `modelSlow` | The model may be taking longer. Wait or stop and try again. |
| Stream disconnected or endpoint unreachable | `network` | The connection to the agent was lost. Reconnect and try again. |
| Probe could not confirm health, while stream still appears connected | `network` | The connection could not be checked. Reconnect and try again. |

Helper down is direct observation. Model slow is an inference: endpoint health
does not measure provider execution. `network` is a connection classification;
it never asserts that the phone's internet is down. `TurnStallDiagnosis.message`
provides these contract words; the frontend lane owns localization and kit copy.
No raw probe exception reaches this result.

`boundedTurnStallProbe(probe:, transportConnected:, timeout:)` caps the complete
combined probe at 10 seconds. Throws/timeouts produce unknown evidence. The
connection must use only read-only endpoint/helper checks: never start/stop an
agent or create an authenticated chat just to measure health.

`TurnStallTracker(elapsed:, silenceThreshold:, maximumSessions:)` uses a
Stopwatch by default, never the wall clock. Its methods:

- `begin(sessionId)` on a prompt or initial busy observation. It is idempotent;
  repeated busy polls never reset the silence clock.
- `noteProgress(sessionId)` for progress from that turn only. It clears the
  diagnosis and invalidates an in-flight probe.
- `setWaiting(sessionId, bool)` for permission/question/form/card waits. A user
  wait suppresses stall probes. An answer starts a fresh silence period.
- `finish(sessionId)` on idle/error/stop/deletion; `clear()` on connection reset,
  disconnect disposal, profile/location replacement or lifecycle suspension.
- `dueSessionIds` is a bounded snapshot of silent active sessions (maximum 100).
- `takeProbe(sessionId)` returns an opaque token once per silence interval.
- `completeProbe(token, evidence)` returns `TurnStallDiagnosis?`, with `kind`,
  `evidence`, `silentFor`, and `message`. Null means progress, a user wait, stop,
  reset or a replacement turn invalidated the token. Discard null immediately.
- `diagnosisFor(sessionId)` exposes the current safe diagnosis. A diagnosis stays
  until actual progress/finish/reset; the tracker avoids hammering a silent model
  with repeated health probes. If a later helper or transport state changes,
  the connection must reset/reconcile the observation.

A diagnosis never finishes a turn, changes its server status, resends the
prompt, alters retry state, or cancels execution. A user can wait, stop, or use
the existing reconnect/helper recovery action.

## Connection owner wiring

The BA7 lead owns `lib/state/connection.dart` and all its parts. This domain
slice does not edit them. Current timings/wake lock live in
`lib/builtin/reply_watch.dart`, attached only to the main connection. The phone
agent backend has a separate 10-second watcher in
`lib/state/connection/phone_agents_backend.dart`; it recovers after two offline
observations but does not detect a connected turn with no events.

Wire one tracker per actual connection (including the phone-agent backend).
Start a five-second timer only while a person has an active turn; release it
when there are no active turns/disposal. For each due token, await the bounded
combined helper/endpoint probe, then publish only a still-valid diagnosis.
Keep a single timer and avoid overlapping probes for the same session.

Relevant target progress includes assistant text/reasoning deltas, tool
progress/update events, and completed tool/message events. Requests waiting for
a person must setWaiting. Global stream heartbeats, server-connected events,
metadata/read marks, unrelated conversations and repeated busy observations
must not call noteProgress. Identify a session from the event's own
sessionID/part/info, not the currently visible session. Durable reconnect
refetch reconciles state instead of replaying volatile tool deltas.

## Focused verification

`test/turn_stall_test.dart` covers all three classes, a thrown/hung combined
probe, the 60-second bound, target-only progress, stale-token invalidation,
waiting/answer recovery, stop/reset/replaced sessions, repeated busy statuses,
and bounded memory. Coordinator runs it through the shared test lock. Real
connection timer tests and a revert-the-fix check of timer wiring are required
before the whole BA7 item is done; this domain classifier alone is groundwork.

Implemented connection projection: `ConnectionController.turnStallFor(sessionId)`
returns the current `TurnStallDiagnosis?`. The notifier updates on diagnosis,
target progress, completion and connection reset. The actual connection now
owns its timer, combined read-only probe and wait suppression. The phone-agent
backend probes native helper process liveness before its endpoint. Main
OpenCode probes health. Transport changes invalidate prior evidence without
resetting the last-progress clock. One shared probe covers the due sessions on
that connection, with no overlapping health reads.


## Timer ownership follow-up

Watchdog scheduling requires an event channel or active polling transport owned
by this controller. Busy snapshots in a transportless controller never create
wakeup work. `configureTurnStallForTesting` opts into the deterministic clock and
probe explicitly; it is not a production bypass.

`boundedTurnStallProbe` now accepts optional `cancelled: Future<void>`. Cancellation
completes with unknown evidence and cancels the deadline timer. It never claims
a helper or endpoint measurement, and late probe futures cannot complete again.
Connection disposal, idle/deletion/error of all tracked turns, disconnect,
transport retirement/generation changes and lifecycle suspension cancel the
pending deadline. Resume starts a fresh silence period; keeping a background
connection alive does not rearm foreground stall checks.
