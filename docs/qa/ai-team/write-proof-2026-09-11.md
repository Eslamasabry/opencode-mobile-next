# AI Team · Gas City — real write proof (2026-09-11)

TEAM-207. The app's own write path (`GasCityProbe` → `GasCityGateway(front:
true)` → `GasCityControl`, `lib/orchestration/adapters/gascity/`) against the
live supervisor on the dev PC **through the host front over the tailnet
address the phone uses**, driven by `tool/qa/gascity_write_proof.dart` — a
plain-Dart script (no Flutter widgets) that exits non-zero on any hard
failure and records, rather than invents, anything it could not confirm.

```
dart run tool/qa/gascity_write_proof.dart --url http://100.101.102.103:8373 --city bright-lights
```

Host: Gas City 1.4.1 (build 58ef17e3bd68), city `bright-lights`, rig
`ocproof` = `/home/eslam/Storage/Code/oc-bg-proof` (bare origin
`oc-bg-proof.git`, default branch `master`), lean profile (mayor, deacon,
boot and witness suspended; polecat pool and refinery active). Front:
`tool/host/cp_front/front.py` **from this bead** (the policy route is new)
on `http://100.101.102.103:8373`, allowlist `owner@example.com`, state dir
`/home/eslam/Storage/Code/gascity-spike/front-state`; this PC's tailnet
address is identified as that login. The front was restarted from the
TEAM-207 worktree for the proof (the dev copy had no `/front/policy`) and
a per-rig config was added at `front-state/rigs/ocproof.json`:

```json
{"supervision": "balanced",
 "boundaries": {"require_approval": true, "extra": ["Keep changes inside calc.py"]}}
```

Three runs were made while the script was being corrected (§5); the
result below is the third, `oc-fvp`. Every run created exactly one tiny
bead, stopped what it started, and closed the bead at the end; no polecat
session of any run was left active (§4).

## 1. What the script does

| Step | Through | Route |
|---|---|---|
| a | `GasCityProbe.probe(url, city)` | `GET /.well-known/opencode-mobile-orchestration`; needs `front: true`, `identity.allowed`, control capabilities |
| a′ | `GasCityGateway.policy()` | `GET /v0/city/{c}/front/policy` (TEAM-207) |
| b | loopback supervisor (the only call not through the front: the app has no "create work" verb) | `POST /v0/city/{c}/beads` with `X-GC-Request` — title "Write proof: add a docstring to add() in calc.py", label `opencode-mobile-write-proof` |
| b | `gateway.assign(bead, agentId: 'ocproof/gastown.polecat')` | `POST /sling` `{bead, target, reassign: true}` with `Idempotency-Key`; then the same call with the same key |
| c | wait ≤ 360 s for the pool to wake a session for the bead (`session.woke` on the stream or the app's agent list, confirmed by the session bead's `gc.trigger_bead_id`); then `gateway.controlAgent(agent, nudge)` | `POST /session/{id}/messages` `{message: "please continue"}` → 202 with `request_id`; wait ≤ 90 s for the matching `request.result.*` / `request.failed` on `/events/stream` |
| d | `gateway.controlAgent(agent, stop)` | `POST /session/{id}/stop`; wait ≤ 90 s for `session.stopped` or `bead.closed` on the session bead |
| e | `gateway.mergeReadiness(convoy)` while the convoy is open | `GET /v0/city/{c}/front/merge-readiness/{convoy}` |
| f | `gateway.cancelRun(convoy)` | `POST /convoy/{id}/close`; then `GET /convoy/{id}` must say `closed` |
| g | identity refusal | skipped, see §3 |
| cleanup | loopback supervisor | `POST /bead/{id}/close`; `/sessions` scanned for any running pool session whose `gc.trigger_bead_id` is the bead, stopped if found |

The event stream (`gateway.events()`, i.e. `/events/stream` through the
front) stays open for the whole proof so every effect is looked for on the
same stream the app would use.

## 2. Results (run 3, bead `oc-fvp`)

**Result: PASS** (`ok: true`; 0 failures, 1 unconfirmed, 1 host-refused, 1
skipped). Started 2026-09-11T06:27:58Z, wall clock 166.5 s.

| Step | Front request id | Receipt | Upstream | Effect seen on the stream | Timing |
|---|---|---|---|---|---|
| a probe | – | `Gas City 1.4.1 city bright-lights via front (controls)`; front true; login `owner@example.com`, allowed true; every `control*` + `mergeReadiness` on | 200 | – | 601 ms |
| a′ policy | – | `{rig: ocproof, supervision: balanced, boundaries: [require_approval "Never merge without approval", extra-1 "Keep changes inside calc.py"]}` | 200 | – | 51 ms |
| b create bead (loopback) | – | `oc-fvp` open | 201 | `bead.created oc-fvp` seq 4732; auto-convoy `bead.created oc-kua` seq 4734 (`sling-oc-fvp`, `tracks` oc-fvp) | 37 ms |
| b sling | `20bc97762bb2c08c` | accepted, key `mtwkr8t9-sling-1`, replayed false; body `{status: slung, target: ocproof/gastown.polecat, bead: oc-fvp, mode: direct}` | 200 | session bead `bl-kon` created seq 4740 (0.5 s), `session.woke bl-kon` agent `ocproof/gastown.furiosa` seq 4750 (4.6 s) | 75 ms |
| b sling replay (same key) | `20bc97762bb2c08c` (same) | accepted, **replayed true** (`Idempotent-Replayed: true`), same body; the front log shows the two requests 2 ms apart with one request id — the supervisor was not called again | 200 | – | 42 ms |
| c await session | – | `bl-kon` / `ocproof/gastown.furiosa` (pool `ocproof/gastown.polecat`) found after 2 polls, 3.2 s; the bead itself never got `gc.session_id` (not claimed, see §3) | – | – | 3 239 ms |
| c nudge | `b4cdca07ba0862ca` | accepted, key `mtwkr8t9-nudge-2`; body `{status: accepted, request_id: req-647b0c159b000a4c7f4d9372, event_cursor: "4748"}` | 202 | **`request.failed` seq 4771 with that `request_id`**, operation `session.message`, error `sending message to session: agent "gastown__polecat-bl-kon" busy, timed out waiting for idle` — 61.6 s after the receipt | 93 ms + 61.6 s |
| d stop | `77ad7375eed7bc23` | accepted, key `mtwkr8t9-stop-3`; body `{status: ok, id: bl-kon}` | 200 | no `session.stopped`; `bead.updated bl-kon` ×3 at seq 4777–4780, 34 s after the receipt, with the session bead's `state: asleep`, `sleep_reason: runtime-missing` (the runtime was gone); `bead.closed bl-kon` seq 4793 only at +95 s, after cleanup closed the work bead (`close_reason: session swept: no assigned work in any rig`) | 118 ms + 90 s window |
| e merge readiness | – | `ready: false`, rig ocproof, target `master`; lines: work ✗ "1 open: oc-fvp", tests ✓ "not configured on the host", build ✓ "not configured on the host", review ✓ "no review requested", conflicts ✗ "no branch polecat/oc-fvp on origin for oc-fvp", acceptance ✓ "not reported by the host"; boundaries: require_approval unsatisfied; mergeRequest `oc-kua` "sling-oc-fvp"; branches [] | 200 | – | 127 ms |
| f close convoy | `de4a64c0afc72dfd` | accepted, key `mtwkr8t9-close-4`; body `{status: closed}` | 200 | `bead.closed oc-kua` seq 4783 (0.1 s); `GET /convoy/oc-kua` → `closed` | 109 ms |
| cleanup | – | bead `oc-fvp` closed (`bead.closed` seq 4784); session `bl-kon` gone from `/sessions`; stray pool sessions stopped: none | – | – | – |

Stream frames observed during the run: 75 (heartbeats and the
controller's `order.*` orders included); the 28 that name this run's
objects are listed in the raw report (§6).

### What this confirms

- **Discovery and identity through the front** from the address the phone
  uses: `front: true`, the login, `allowed: true`, and every control
  capability the app gates its buttons on.
- **Policy read** (TEAM-207): the front reads the rig config and answers
  the supervision level and the boundary texts, including a free-text
  `extra` boundary; the app's `OrchestrationPolicy` parsed it.
- **Sling through the front with an `Idempotency-Key`**: accepted with the
  supervisor's `X-GC-Request-Id` in the receipt; the pool woke a session
  for the bead in 4.6 s; **the replay answered the stored receipt with
  `Idempotent-Replayed: true` and did not reach the supervisor**.
- **Message (nudge) round trip end to end**: receipt with the 202
  correlation id → the matching `request.failed` for that exact id on the
  same stream the app listens to. The *effect* was refused by the host
  (the polecat was busy in its first turn), which is precisely the message
  the app's receipt chip shows for a rejected record; the correlation, not
  the outcome, is what this step proves.
- **Stop through the front**: accepted `{status: ok, id: bl-kon}`; 34 s
  later the host's own reconciler recorded the session's runtime as
  missing and the session was gone from `/sessions` at the end; the pool
  did not respawn a session for the bead before cleanup closed it (run 2
  did respawn one, `bl-bdz`, which cleanup stopped).
- **Close batch (convoy) through the front**: accepted, the convoy is
  `closed` on re-read, `bead.closed` for the convoy on the stream within
  0.1 s.
- **Merge readiness** for a run whose work is not done: `ready: false`
  with the two honest failing lines (open work item, no branch) and the
  unsatisfied approval boundary — the app's Merge button would be disabled
  and name "Work items".
- **Cleanup**: bead closed, no session of this run left running.

## 3. What did not confirm, and why

- **`respond` (answer an interaction)**: there was no pending interaction on
  this host during the proof (`/pending` stayed empty; the pack's agents
  did not ask anything in the ~70 s a polecat was awake), so the gate
  answer could not be exercised live. The write path is proven by the
  fixture-server test `test/team_control_test.dart` ("receipt lifecycle":
  `respond` → `POST /session/{id}/respond` `{action, request_id, text?}`,
  202 receipt, confirmed by `gate.resolved`) and by
  `test/team_gate_answer_test.dart`. Left as **not proven live**.
- **Stop confirmation event**: Gas City 1.4.1 emitted **no
  `session.stopped`** for an explicit `POST /session/{id}/stop` in any of
  the three runs. What it emits is `bead.updated` on the session bead
  (state `asleep`, `sleep_reason: runtime-missing`, +34 s) and later
  `bead.closed` on it (+26 s in run 2 with `close_reason: session
  terminated: runtime-missing`; +95 s in run 3, after the work bead
  closed). The controller confirmed a stop only on `session.stopped`, so a
  stop from the phone would have gone "unconfirmed" after 60 s although it
  worked. **Fixed in this bead** (`OrchestrationController._confirms`):
  `bead.closed` on the target's session bead now confirms stop/pause, and
  `bead.closed` on the convoy confirms a batch close (`cancelRun`), both
  covered by `test/team_control_test.dart` "a stop and a batch close
  confirm on bead.closed…". A session bead changing also marks the agents
  scope dirty so the Agents list refreshes. The script counts the stop as
  *unconfirmed* in run 3 because the `bead.closed` arrived after its 90 s
  window; the receipt was accepted and the host's own record shows the
  runtime gone at +34 s.
- **Nudge delivery**: refused by the host as "busy, timed out waiting for
  idle" (the polecat was in its first turn). The round trip is proven; the
  message was not delivered. A nudge to an idle agent would carry
  `request.result.session.message` instead — not exercised, to keep the
  polecat's paid work to one turn.
- **The bead was never claimed**: in all three runs the polecat session
  woke within 5 s but `gc hook --claim` never set `gc.session_id` on the
  bead before it was stopped (~65 s) — in the TEAM-001 spike the claim came
  at ~1:30. The work-item row on the phone would therefore have shown the
  bead as *ready* with the agent *working* on the pool, not as *in
  progress*. Host behaviour, recorded as is.
- **g. Mutation from a non-allowlisted identity**: the front identifies the
  peer by its tailnet address (`tailscale whois`), so from this allowlisted
  PC there is no way to present another identity, and the front offers no
  test hook (by design: nothing on the request can override whois). Skipped;
  covered by `tool/host/cp_front/tests/test_front.py`
  `MutationTests.test_post_refused_for_non_allowlisted_is_problem_json`
  (403 `identity-not-allowed`, nothing forwarded),
  `test_same_key_different_identity_is_409` and
  `ReadTests.test_get_refused_for_identified_but_not_allowlisted_peer`,
  all green (59 tests).
- **Not from a phone**: the script ran on the PC against the PC's tailnet
  address, which the front identifies as the same login the phone carries.
  The APK on the phone was not driven for this proof (emulator captures
  are still pending, see README §7).

## 4. Nothing left running

At the end of each run: the proof bead `closed`, the convoy `closed`,
`/sessions` lists only the pre-existing `bd.dog` and refinery sessions, and
the pool slot's session bead is closed (`session swept` / `session
terminated` / `pool slot retired`). Run 1's session `bl-jsu` (see §5) ran
until cleanup closed the bead and was then drained by the reconciler
(`close_reason: session drained: pool slot retired by reconciler`); run 2's
respawned `bl-bdz` was stopped by cleanup. The rig checkout and its origin
were not touched: `origin` still has only `master` and the spike's
`polecat/oc-loy`; readiness reported "no branch polecat/oc-fvp on origin".

## 5. The three runs

| Run | Bead / convoy / session | Outcome | What changed after it |
|---|---|---|---|
| 1 | `oc-602` / `oc-2tj` / `bl-jsu` | sling + replay OK; session woke at +4.5 s but the detector polled the bead's `gc.session_id`, which never appeared → 6 min wait, nudge/stop not exercised; readiness 404 because the convoy was closed first | detector rewritten to use `session.woke` + the app's agent list + `gc.trigger_bead_id`; readiness moved before the close |
| 2 | `oc-v1t` / `oc-rmn` / `bl-ex0` | session found in 3.2 s; nudge 202 → `request.failed` (busy) at +61 s; stop 200, session bead `bead.closed` at +26 s (`session terminated: runtime-missing`), no `session.stopped`; readiness not ready; close OK; pool respawned `bl-bdz`, stopped by cleanup | host-refused effects recorded separately from failures; stop confirmation accepts `bead.closed` on the session bead (script and controller) |
| 3 | `oc-fvp` / `oc-kua` / `bl-kon` | **PASS** as reported in §2 | – |

## 6. Raw report (run 3)

Heartbeats and `order.*` frames removed from `events` (47 omitted); the
full JSON is what the script prints on stdout.

```json
{
 "url": "http://100.101.102.103:8373",
 "supervisor": "http://127.0.0.1:8372",
 "city": "bright-lights",
 "rig": "ocproof",
 "pool": "ocproof/gastown.polecat",
 "startedAt": "2026-09-11T06:27:58.941233Z",
 "probe": "Gas City 1.4.1 city bright-lights via front (controls)",
 "front": true,
 "identityLogin": "owner@example.com",
 "identityAllowed": true,
 "capabilities": {
  "projects": true,
  "runs": true,
  "runSteps": true,
  "workGraph": true,
  "workReady": true,
  "agents": true,
  "agentOutput": true,
  "sessionLink": true,
  "gatesInteractions": true,
  "gatesBeads": true,
  "usage": true,
  "eventStream": true,
  "eventReplay": true,
  "controlRespond": true,
  "controlMessage": true,
  "controlAgent": true,
  "controlCancelRun": true,
  "controlAssign": true,
  "changes": true,
  "verification": true,
  "mergeReadiness": true,
  "phoneHost": false
 },
 "host": {
  "provider": "gascity",
  "version": "1.4.1",
  "city": "bright-lights",
  "url": "http://100.101.102.103:8373"
 },
 "policy": {
  "rig": "ocproof",
  "supervision": "balanced",
  "boundaries": [
   {
    "key": "require_approval",
    "text": "Never merge without approval"
   },
   {
    "key": "extra-1",
    "text": "Keep changes inside calc.py"
   }
  ]
 },
 "bead": {
  "id": "oc-fvp",
  "status": "open",
  "title": "Write proof: add a docstring to add() in calc.py"
 },
 "convoy": "oc-kua",
 "session": {
  "sessionId": "bl-kon",
  "agentId": "ocproof/gastown.furiosa",
  "agentName": "ocproof/gastown.furiosa",
  "agentState": "stopped",
  "agentPool": "ocproof/gastown.polecat",
  "beadStatus": "open",
  "assignee": null,
  "claimed": false,
  "polls": 2,
  "waitedMs": 3238
 },
 "nudgeResult": {
  "type": "request.failed",
  "seq": 4771,
  "at": "2026-09-11T06:29:04.397353Z",
  "requestId": "req-647b0c159b000a4c7f4d9372",
  "ok": false,
  "operation": "session.message",
  "error": "sending message to session: agent \"gastown__polecat-bl-kon\" busy, timed out waiting for idle"
 },
 "stopEvent": null,
 "mergeReadiness": {
  "ready": false,
  "rig": "ocproof",
  "targetBranch": "master",
  "lines": [
   {
    "key": "work",
    "ok": false,
    "detail": "1 open: oc-fvp"
   },
   {
    "key": "tests",
    "ok": true,
    "detail": "not configured on the host"
   },
   {
    "key": "build",
    "ok": true,
    "detail": "not configured on the host"
   },
   {
    "key": "review",
    "ok": true,
    "detail": "no review requested"
   },
   {
    "key": "conflicts",
    "ok": false,
    "detail": "no branch polecat/oc-fvp on origin for oc-fvp"
   },
   {
    "key": "acceptance",
    "ok": true,
    "detail": "not reported by the host"
   }
  ],
  "boundaries": [
   {
    "key": "require_approval",
    "satisfied": false,
    "text": "Never merge without approval"
   }
  ],
  "mergeRequest": {
   "id": "oc-kua",
   "title": "sling-oc-fvp"
  },
  "branches": []
 },
 "convoyStatusAfterClose": "closed",
 "cleanup": {
  "beadClose": {
   "status": "closed"
  },
  "beadStatus": "closed",
  "sessionStateAtEnd": "gone",
  "straySessionsStopped": []
 },
 "receipts": {
  "sling": {
   "key": "mtwkr8t9-sling-1",
   "status": "accepted",
   "upstreamStatus": 200,
   "hostRequestId": "20bc97762bb2c08c",
   "correlationId": null,
   "replayed": false,
   "retryable": false,
   "body": {
    "status": "slung",
    "target": "ocproof/gastown.polecat",
    "bead": "oc-fvp",
    "mode": "direct",
    "dashboard_url": "http://127.0.0.1:8372/city/bright-lights/runs"
   }
  },
  "slingReplay": {
   "key": "mtwkr8t9-sling-1",
   "status": "accepted",
   "upstreamStatus": 200,
   "hostRequestId": "20bc97762bb2c08c",
   "correlationId": null,
   "replayed": true,
   "retryable": false,
   "body": {
    "status": "slung",
    "target": "ocproof/gastown.polecat",
    "bead": "oc-fvp",
    "mode": "direct",
    "dashboard_url": "http://127.0.0.1:8372/city/bright-lights/runs"
   }
  },
  "nudge": {
   "key": "mtwkr8t9-nudge-2",
   "status": "accepted",
   "upstreamStatus": 202,
   "hostRequestId": "b4cdca07ba0862ca",
   "correlationId": "req-647b0c159b000a4c7f4d9372",
   "replayed": false,
   "retryable": false,
   "body": {
    "status": "accepted",
    "request_id": "req-647b0c159b000a4c7f4d9372",
    "event_cursor": "4748"
   }
  },
  "stop": {
   "key": "mtwkr8t9-stop-3",
   "status": "accepted",
   "upstreamStatus": 200,
   "hostRequestId": "77ad7375eed7bc23",
   "correlationId": null,
   "replayed": false,
   "retryable": false,
   "body": {
    "status": "ok",
    "id": "bl-kon"
   }
  },
  "cancelRun": {
   "key": "mtwkr8t9-close-4",
   "status": "accepted",
   "upstreamStatus": 200,
   "hostRequestId": "de4a64c0afc72dfd",
   "correlationId": null,
   "replayed": false,
   "retryable": false,
   "body": {
    "status": "closed"
   }
  }
 },
 "events": [
  {
   "type": "bead.created",
   "seq": 4732,
   "at": "2026-09-11T06:27:59.841253Z",
   "beadId": "oc-fvp"
  },
  {
   "type": "bead.updated",
   "seq": 4733,
   "at": "2026-09-11T06:27:59.841772Z",
   "beadId": "oc-fvp"
  },
  {
   "type": "bead.created",
   "seq": 4734,
   "at": "2026-09-11T06:27:59.843903Z",
   "beadId": "oc-kua"
  },
  {
   "type": "bead.updated",
   "seq": 4735,
   "at": "2026-09-11T06:27:59.849972Z",
   "beadId": "oc-kua"
  },
  {
   "type": "bead.created",
   "seq": 4740,
   "at": "2026-09-11T06:28:00.356323Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.updated",
   "seq": 4741,
   "at": "2026-09-11T06:28:00.374093Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.updated",
   "seq": 4742,
   "at": "2026-09-11T06:28:00.643812Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.updated",
   "seq": 4743,
   "at": "2026-09-11T06:28:00.654206Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.updated",
   "seq": 4744,
   "at": "2026-09-11T06:28:00.664786Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.updated",
   "seq": 4749,
   "at": "2026-09-11T06:28:04.437371Z",
   "beadId": "bl-kon"
  },
  {
   "type": "session.woke",
   "seq": 4750,
   "at": "2026-09-11T06:28:04.437540Z",
   "sessionId": "bl-kon",
   "agentId": "ocproof/gastown.furiosa"
  },
  {
   "type": "bead.updated",
   "seq": 4751,
   "at": "2026-09-11T06:28:04.441264Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.updated",
   "seq": 4753,
   "at": "2026-09-11T06:28:04.950334Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.updated",
   "seq": 4754,
   "at": "2026-09-11T06:28:04.957763Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.created",
   "seq": 4755,
   "at": "2026-09-11T06:28:05.966011Z",
   "beadId": "bl-wisp-6xq8d"
  },
  {
   "type": "bead.created",
   "seq": 4759,
   "at": "2026-09-11T06:28:36.053043Z",
   "beadId": "bl-wisp-kukvz"
  },
  {
   "type": "bead.updated",
   "seq": 4760,
   "at": "2026-09-11T06:28:36.058271Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.closed",
   "seq": 4761,
   "at": "2026-09-11T06:28:36.068662Z",
   "beadId": "bl-wisp-6xq8d"
  },
  {
   "type": "worker.operation",
   "seq": 4770,
   "at": "2026-09-11T06:29:04.397176Z"
  },
  {
   "type": "request.failed",
   "seq": 4771,
   "at": "2026-09-11T06:29:04.397353Z",
   "requestId": "req-647b0c159b000a4c7f4d9372",
   "ok": false,
   "operation": "session.message",
   "error": "sending message to session: agent \"gastown__polecat-bl-kon\" busy, timed out waiting for idle"
  },
  {
   "type": "bead.updated",
   "seq": 4777,
   "at": "2026-09-11T06:29:39.756548Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.updated",
   "seq": 4779,
   "at": "2026-09-11T06:29:39.762198Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.updated",
   "seq": 4780,
   "at": "2026-09-11T06:29:39.767909Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.closed",
   "seq": 4783,
   "at": "2026-09-11T06:30:35.439882Z",
   "beadId": "oc-kua"
  },
  {
   "type": "bead.closed",
   "seq": 4784,
   "at": "2026-09-11T06:30:35.711506Z",
   "beadId": "oc-fvp"
  },
  {
   "type": "bead.updated",
   "seq": 4789,
   "at": "2026-09-11T06:30:40.482614Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.updated",
   "seq": 4790,
   "at": "2026-09-11T06:30:40.488010Z",
   "beadId": "bl-kon"
  },
  {
   "type": "bead.closed",
   "seq": 4793,
   "at": "2026-09-11T06:30:40.743879Z",
   "beadId": "bl-kon"
  }
 ],
 "timingsMs": {
  "probe": 601,
  "connect": 11,
  "policy": 51,
  "createBead": 37,
  "sling": 75,
  "slingReplay": 42,
  "findConvoy": 44,
  "awaitSession": 3239,
  "nudge": 93,
  "nudgeResult": 61628,
  "stop": 118,
  "stopEvent": 90180,
  "mergeReadiness": 127,
  "cancelRun": 109,
  "convoyAfterClose": 57
 },
 "totalMs": 166542,
 "unconfirmed": [
  "stop: neither session.stopped nor bead.closed for bl-kon within 90 s (the receipt was accepted)"
 ],
 "hostRefused": [
  "nudge: sending message to session: agent \"gastown__polecat-bl-kon\" busy, timed out waiting for idle"
 ],
 "skipped": [
  "g: a mutation from a non-allowlisted identity cannot be sent from this PC (the front identifies the peer by tailnet address; no test hook); covered by tool/host/cp_front/tests/test_front.py MutationTests.test_post_refused_for_non_allowlisted_is_problem_json and test_same_key_different_identity_is_409"
 ],
 "failures": [],
 "ok": true,
 "eventsOmitted": 47
}
```


## Addendum (2026-09-11, evening): formula run recorded; `respond` cannot be provoked with this harness

- **Formula run recorded.** `POST /sling {target, formula: "mol-do-work", attached_bead_id, force}` (the `bead`+`formula` form is refused as mutually exclusive) created workflow/run `oc-qgi`; `GET /runs/{id}` shape saved as `recordings/run_formula.json`, the `/runs` page with it as `recordings/runs_with_formula.json`, the workflow root bead as `recordings/bead_workflow_root.json`. `mol-do-work` is the pack's minimal lifecycle (no branch, no push, no refinery): it committed `5e839e4` with `gc.work_outcome=shipped` and a passing verification command, and the run stays `waiting`.
- **Pending interactions never appeared.** Two normal tasks (`oc-duc`, `oc-87j`) were run with `"git push*": "ask"` set first in the supervisor's `OPENCODE_CONFIG` and then committed in the rig's own `opencode.json`; OpenCode's log shows every push evaluated as `action=allow pattern=*` under the ACP provider, and `/pending` stayed empty while both tasks pushed and were handed to the refinery. Gas City runs OpenCode agents unattended (a provider `permission_mode` option exists in its config schema); tool permission prompts are therefore not a source of decisions with this pack and harness. Product consequence: the "Decision requested" kind is fed by gate beads, `needs-review` work and failed runs; the `respond` route stays proven on the fixture (`test/team_control_test.dart`) until a harness or formula that asks is used.
- **Local merge strategy.** `merge_strategy: local` merges into the rig checkout's `master` (`4bc8e26 Add multiply() with docstring to calc.py` landed there from the owner's phone-started task), not into `origin`. The host guide says so.
