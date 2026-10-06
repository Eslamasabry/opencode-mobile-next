# AI Team · Gas City — real read proof (2026-09-11)

TEAM-114. The app's own Gas City read adapter (`GasCityProbe` + `GasCityGateway`,
`lib/orchestration/adapters/gascity/`) against the live supervisor on the dev PC,
driven by `tool/qa/gascity_read_proof.dart` — a plain-Dart script (no Flutter
widgets) that exits non-zero on any failed check. Both addresses were proved:
loopback on the PC and the tailnet address the phone uses.

```
dart run tool/qa/gascity_read_proof.dart --url http://127.0.0.1:8372
dart run tool/qa/gascity_read_proof.dart --url http://100.101.102.103:8372
```

Host: Gas City 1.4.1 (build 58ef17e3bd68), city `bright-lights`, rig `ocproof`
(the TEAM-001 spike city, see `spike-pc-2026-09.md`). The supervisor had been up
~47 min; the controller's `order.fired` / `order.completed` heartbeat orders are
what keeps the event log moving while no run is active.

## 1. What the script checks

1. Probe URL + city → `ProbeFound` with version `1.4.1` and city `bright-lights`.
2. `GasCityGateway.connect()` then reads: projects, runs (with the `partial`
   notice), work (list + ready set), agents (names and states), gates, usage,
   and the activity head (`/events`).
3. Raw counts over plain HTTP through the same client but no mapping:
   `/agents`, `/beads`, `/convoys`, `/pending`, `/runs`; the adapter's counts
   must match (`/beads` minus Gas City's internal beads — sessions, convoys,
   molecules, nudges — which the adapter hides by design, `isInternalBead`).
4. Open `/events/stream` fresh; wait ≤ 20 s for one frame or heartbeat; record
   the last seq (stream cursor, else the activity head); close.
5. Reopen with `Last-Event-ID = lastSeq − 5`; the replay must start at
   `lastSeq − 4`, carry strictly increasing ids, no duplicates, and no
   head-only replay (the host honoured the cursor).

## 2. Results

| | `http://127.0.0.1:8372` | `http://100.101.102.103:8372` |
|---|---|---|
| Result | **PASS** | **PASS** |
| Started (UTC) | 2026-09-11T02:16:16Z | 2026-09-11T02:16:43Z |
| Probe | Gas City 1.4.1 city bright-lights (read-only) | Gas City 1.4.1 city bright-lights (read-only) |
| Projects | ocproof | ocproof |
| Runs (adapter) | 15 (completed 7, planning 8); partial: false | 15 (completed 7, planning 8); partial: false |
| Work items (adapter) | 12, ready set 12, blocked 0; partial: false | 12, ready set 12, blocked 0; partial: false |
| Agents (adapter) | 14 (crashed 4, idle 1, stopped 9) | 14 (crashed 4, idle 1, stopped 9) |
| Gates (adapter) | 0 | 0 |
| Usage | activeAgents 1, workOpen 26, workReady 11, tokens 0/0, cost $0.0 | activeAgents 1, workOpen 26, workReady 11, tokens 0/0, cost $0.0 |
| Activity head seq | 2176 | 2180 |
| Stream: first frame | 1 frame (1 heartbeat, 0 event) in 15.0 s; statuses live → closed | 1 frame (1 heartbeat, 0 event) in 15.0 s; statuses live → closed |
| Last seq recorded | 2176 | 2180 |
| Resume with Last-Event-ID | asked 2171 → replayed [2172, 2173, 2174, 2175, 2176]; head-only: false; cursor 2176 | asked 2175 → replayed [2176, 2177, 2178, 2179, 2180]; head-only: false; cursor 2180 |
| Wall clock (total) | 17.3 s | 17.7 s |

Only a heartbeat arrived inside the 20 s window on both runs (the supervisor
heartbeats every 15 s and the controller's orders fire every ~30 s), so the
last seq comes from the activity head; the resume step then shows real
`order.*` events flowing through the stream with the cursor honoured.

### Per-read timings (ms)

| Read | loopback | tailnet |
|---|---|---|
| probe | 112 | 162 |
| connect | 3 | 10 |
| projects | 2 | 48 |
| runs | 26 | 53 |
| work | 6 | 47 |
| readyWork | 6 | 60 |
| agents | 37 | 68 |
| gates | 8 | 13 |
| usage | 2 | 48 |
| activity | 2 | 12 |
| rawCounts | 6 | 67 |
| streamFirst | 15035 | 15030 |
| streamResume | 2042 | 2017 |

`streamFirst` is the wait for the first heartbeat (15 s cadence);
`streamResume` includes a fixed 2 s drain after the first replayed frame.

## 3. Matching counts: adapter vs `curl`

Direct `curl` of the raw endpoints at 2026-09-11T02:17:01Z, both addresses
answering identically:

```
for p in agents beads convoys pending; do
  curl -s http://127.0.0.1:8372/v0/city/bright-lights/$p | jq '.items | length'
done
```

| Endpoint | `curl` items (loopback) | `curl` items (tailnet) | Script raw count | Adapter | Relation |
|---|---|---|---|---|---|
| `/agents` | 14 | 14 | 14 | 14 agents | equal |
| `/beads` | 27 | 27 | 27 (15 internal) | 12 work items | 27 − 15 internal = 12 |
| `/convoys` | 1 | 1 | 1 | part of 15 runs | `/runs` 14 + `/convoys` 1 = 15 |
| `/pending` | 0 | 0 | 0 | 0 gates | equal |

The 15 internal beads are 7 `molecule`, 6 `chore` carrying `gc:nudge`,
1 `convoy` and 1 `session` (`gc:session`); the 12 the adapter lists are the
9 `task` beads plus 3 `task` beads labelled `daily`/`digest`. `/beads?ready=true`
reports all 12 ready, matching the adapter's ready set of 12.

## 4. Raw reports

Loopback:

```json
{
 "url": "http://127.0.0.1:8372",
 "city": "bright-lights",
 "startedAt": "2026-09-11T02:16:16.597763Z",
 "probe": "Gas City 1.4.1 city bright-lights (read-only)",
 "version": "1.4.1",
 "readOnly": true,
 "host": {
  "provider": "gascity",
  "version": "1.4.1",
  "city": "bright-lights"
 },
 "projects": [
  {
   "id": "ocproof",
   "name": "ocproof",
   "rig": "ocproof"
  }
 ],
 "runs": {
  "count": 15,
  "partial": false,
  "states": {
   "completed": 7,
   "planning": 8
  }
 },
 "work": {
  "count": 12,
  "ready": 12,
  "partial": false,
  "states": {
   "ready": 12
  },
  "blocked": 0
 },
 "agents": {
  "count": 14,
  "states": {
   "crashed": 4,
   "idle": 1,
   "stopped": 9
  },
  "names": [
   "bd.dog-1:stopped",
   "bd.dog-2:stopped",
   "core.control-dispatcher:stopped",
   "ocproof/core.control-dispatcher:stopped",
   "ocproof/gastown.furiosa:stopped",
   "ocproof/gastown.nux:stopped",
   "ocproof/gastown.slit:stopped",
   "ocproof/gastown.rictus:stopped",
   "ocproof/gastown.capable:stopped",
   "ocproof/gastown.refinery:idle",
   "ocproof/gastown.witness:suspended",
   "gastown.boot:suspended",
   "gastown.deacon:suspended",
   "gastown.mayor:suspended"
  ]
 },
 "gates": {
  "count": 0,
  "kinds": {}
 },
 "usage": {
  "activeAgents": 1,
  "runsInProgress": 0,
  "workOpen": 26,
  "workReady": 11,
  "inputTokens": 0,
  "outputTokens": 0,
  "costUsd": 0.0
 },
 "activityHeadSeq": 2176,
 "raw": {
  "agents": 14,
  "beads": 27,
  "beadsInternal": 15,
  "beadsWork": 12,
  "convoys": 1,
  "pending": 0,
  "runs": 14,
  "runsPartial": false
 },
 "stream": {
  "frames": 1,
  "heartbeats": 1,
  "events": 0,
  "timedOut": false,
  "lastSeq": null,
  "lastEventId": null,
  "connections": 1,
  "statuses": [
   "live",
   "closed"
  ]
 },
 "lastSeq": 2176,
 "resume": {
  "requestedSeq": 2171,
  "ids": [
   2172,
   2173,
   2174,
   2175,
   2176
  ],
  "headOnlyReplay": false,
  "cursorSeq": 2176
 },
 "timingsMs": {
  "probe": 112,
  "connect": 3,
  "projects": 2,
  "runs": 26,
  "work": 6,
  "readyWork": 6,
  "agents": 37,
  "gates": 8,
  "usage": 2,
  "activity": 2,
  "rawCounts": 6,
  "streamFirst": 15035,
  "streamResume": 2042
 },
 "totalMs": 17301,
 "failures": [],
 "ok": true
}
```

Tailnet:

```json
{
 "url": "http://100.101.102.103:8372",
 "city": "bright-lights",
 "startedAt": "2026-09-11T02:16:43.722855Z",
 "probe": "Gas City 1.4.1 city bright-lights (read-only)",
 "version": "1.4.1",
 "readOnly": true,
 "host": {
  "provider": "gascity",
  "version": "1.4.1",
  "city": "bright-lights"
 },
 "projects": [
  {
   "id": "ocproof",
   "name": "ocproof",
   "rig": "ocproof"
  }
 ],
 "runs": {
  "count": 15,
  "partial": false,
  "states": {
   "completed": 7,
   "planning": 8
  }
 },
 "work": {
  "count": 12,
  "ready": 12,
  "partial": false,
  "states": {
   "ready": 12
  },
  "blocked": 0
 },
 "agents": {
  "count": 14,
  "states": {
   "crashed": 4,
   "idle": 1,
   "stopped": 9
  },
  "names": [
   "bd.dog-1:stopped",
   "bd.dog-2:stopped",
   "core.control-dispatcher:stopped",
   "ocproof/core.control-dispatcher:stopped",
   "ocproof/gastown.furiosa:stopped",
   "ocproof/gastown.nux:stopped",
   "ocproof/gastown.slit:stopped",
   "ocproof/gastown.rictus:stopped",
   "ocproof/gastown.capable:stopped",
   "ocproof/gastown.refinery:idle",
   "ocproof/gastown.witness:suspended",
   "gastown.boot:suspended",
   "gastown.deacon:suspended",
   "gastown.mayor:suspended"
  ]
 },
 "gates": {
  "count": 0,
  "kinds": {}
 },
 "usage": {
  "activeAgents": 1,
  "runsInProgress": 0,
  "workOpen": 26,
  "workReady": 11,
  "inputTokens": 0,
  "outputTokens": 0,
  "costUsd": 0.0
 },
 "activityHeadSeq": 2180,
 "raw": {
  "agents": 14,
  "beads": 27,
  "beadsInternal": 15,
  "beadsWork": 12,
  "convoys": 1,
  "pending": 0,
  "runs": 14,
  "runsPartial": false
 },
 "stream": {
  "frames": 1,
  "heartbeats": 1,
  "events": 0,
  "timedOut": false,
  "lastSeq": null,
  "lastEventId": null,
  "connections": 1,
  "statuses": [
   "live",
   "closed"
  ]
 },
 "lastSeq": 2180,
 "resume": {
  "requestedSeq": 2175,
  "ids": [
   2176,
   2177,
   2178,
   2179,
   2180
  ],
  "headOnlyReplay": false,
  "cursorSeq": 2180
 },
 "timingsMs": {
  "probe": 162,
  "connect": 10,
  "projects": 48,
  "runs": 53,
  "work": 47,
  "readyWork": 60,
  "agents": 68,
  "gates": 13,
  "usage": 48,
  "activity": 12,
  "rawCounts": 67,
  "streamFirst": 15030,
  "streamResume": 2017
 },
 "totalMs": 17655,
 "failures": [],
 "ok": true
}
```
