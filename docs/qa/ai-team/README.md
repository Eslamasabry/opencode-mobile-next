# AI Team · Gas City — QA evidence

Index of the evidence behind the "AI Team · Gas City" plugin (Sprint A,
TEAM-101..114, and Sprint B, TEAM-201..207; plan in
`docs/plans/gas-city-plugin-2026-09-10/`). Everything
here is either a recording of a real Gas City, a script you can rerun against
one, or a test you can rerun without one.

## 1. Spike reports (real hosts)

| Report | What it proves |
|---|---|
| [`spike-pc-2026-09.md`](spike-pc-2026-09.md) | TEAM-001: Gas City 1.4.1 on the dev PC (`dev-pc`, Tailscale 100.101.102.103) ran one bead sling → polecat → refinery → merged with OpenCode as the harness. PASS with conditions; the supervisor API shapes, SSE resume and the findings that shaped the plan. |
| [`spike-phone-2026-09.md`](spike-phone-2026-09.md) | TEAM-002: Gas City inside the phone's proot rootfs. FAIL with 1.4.1 as shipped (Go runtime segfault under proot, degraded Dolt, no ACP handshake); Sprint C stays copy-only. Crash excerpt in [`phone/supervisor-crash-excerpt.txt`](phone/supervisor-crash-excerpt.txt). |

## 2. Fixture recordings (what the tests run against)

`tool/qa/gascity_fixture/` — recorded from the spike city `bright-lights`
(see its [README](../../../tool/qa/gascity_fixture/README.md)):

- `recordings/*.json` — one file per supervisor read the adapter uses
  (`health`, `status`, `rigs`, `runs`, `convoys`, `beads`, `beads_ready`,
  `agents`, `sessions`, `pending`, `waits`, `usage`, `events_page`, …) plus
  the sling round-trip (`sling_response`, `bead_after_sling`,
  `bead_handed_to_refinery`).
- `events/*.ndjson` — scripted stream logs: `normal-run`, `events-snapshot`,
  `resume-last-event-id` (the cursor contract), and the two session
  transcripts (`session-polecat`, `session-refinery`).
- `fixture_server.py` (+ `test_fixture_server.py`) — serves the recordings
  over HTTP with the real paths, for manual runs of the app against a
  fixture host.

In the app, `FixtureOrchestrationGateway`
(`lib/orchestration/adapters/fixture/`) reads the same directory directly;
every `test/team_*_test.dart` suite boots over it.

## 3. Arabic copy fragments

`messages_ar-{card,home,run,work,agent,gate,plugins,usage,hostkind,receipt,control,gateanswer,startrun,merge,policy,noise,cycle,merge-wait}.json` — the Arabic
strings for each surface, as merged into `lib/l10n/app_ar.arb`; the RTL
layout suites (`test/team_*_layout_test.dart`) render from them at 320 dp
and 2.5× text scale.

`messages_ar-{storage,processes}.json` — the Termux server manager's
Storage and Running now screens (TEAM-304/305, `termuxStorage*` and
`termuxProcs*`); `test/termux_storage_test.dart` and
`test/termux_processes_test.dart` render them at 320 dp and 2.5× in RTL.

`messages_ar-phone.json` — the optional on-device AI Team step and the
Settings "On this phone" section (TEAM-302, `teamUiPhone*`);
`test/team_phone_onboarding_test.dart` renders both at 320 dp and 2.5× in
RTL Arabic and LTR English.

## 4. Real read proof (TEAM-114)

[`read-proof-2026-09-11.md`](read-proof-2026-09-11.md) — the app's own
adapter against the live supervisor, both on loopback and over the tailnet
address the phone uses. Probe → reads → counts compared with `curl` → event
stream → resume with `Last-Event-ID`. Both PASS: 14 agents, 12 work items
(27 raw beads − 15 internal), 15 runs (14 `/runs` + 1 convoy), 0 gates,
resume replays strictly increasing ids with no duplicate and no head-only
fallback. Rerun with:

```
dart run tool/qa/gascity_read_proof.dart --url http://127.0.0.1:8372
dart run tool/qa/gascity_read_proof.dart --url http://100.101.102.103:8372
```

## 4a. Real write proof (TEAM-207)

[`write-proof-2026-09-11.md`](write-proof-2026-09-11.md) — the app's own
write path (`GasCityGateway(front: true)` → `GasCityControl`) against the
live supervisor **through the host front on the tailnet address the phone
uses**, driven by `tool/qa/gascity_write_proof.dart`. One tiny bead per run:
probe (front, identity, capabilities) → policy read → sling with an
`Idempotency-Key` and its replay (`Idempotent-Replayed`) → the pool wakes a
session (4.6 s) → nudge through the front with the matching
`request.failed` for its correlation id on the stream (host refused: agent
busy) → stop through the front (accepted; the host records the runtime
gone at +34 s but emits no `session.stopped`, see the report) → merge
readiness (not ready, honest lines) → close batch → cleanup with nothing
left running. PASS with the unconfirmed/refused/skipped items listed, and
`respond` not proven live (no pending interaction on this host; the
fixture-server proof in `test/team_control_test.dart` stands in). Two
controller fixes came out of it (`bead.closed` on the session / convoy bead
confirms stop / close; a session bead change refreshes agents). Rerun with:

```
dart run tool/qa/gascity_write_proof.dart --url http://100.101.102.103:8373 --city bright-lights
```

## 4b. Supervision policy (TEAM-207)

`GET /v0/city/{c}/front/policy[?rig=]` on the front
(`tool/host/cp_front/README.md`, "Policy") answers the rig's supervision
level and boundary texts from `<state-dir>/rigs/<rig>.json`; the app reads
it with the projects scope (`OrchestrationController.policy`) and shows it
read-only on the run Overview ("Supervision · Balanced" + boundary chips)
and as the Start-a-run sheet's Boundaries row. Absent without a front.
`test/team_policy_test.dart` (13 tests: model, route with `?rig=`, 404/422
→ null, controller cache/refresh/stop, overview line + chips + order,
sheet row, absent without a policy side, Arabic 320 dp / 2.5×) and the
front's `PolicyTests` (6). Proven live in §4a (`extra` boundary included).

## 5. Plugin-off regression (TEAM-114)

`test/team_plugin_off_test.dart` — the assertion from
04-plugin-architecture §1.3/§8 that the plugin is absent, not hidden:

- With a profile whose `orchestration` is null, the Workspace, Activity, the
  Settings hub and the server editor contain no widget keyed `team-*` or
  `activity-team-*`, no `TeamCard`, and `ConnectionController.orchestration`
  is null. The entry points that must exist regardless are asserted present:
  `settings-category-plugins` and the editor's `server-editor-team-section`
  / `server-editor-team-add` (with no `team-host-form` behind them).
- Connect + Workspace render against a recording `ServerGateway` produce the
  same call list with the plugin on (fixture provider) as with it off: the
  plugin adds zero calls to `ServerGateway`.
- With the plugin on over a counting fixture gateway, every scope
  (projects, runs, work, agents, gates) is read through
  `OrchestrationGateway`, and the `ServerGateway` log is byte-for-byte the
  plugin-off baseline: zero `lib/api` / `lib/api2` calls for orchestration.

## 6. Full suite (2026-09-11, TEAM-114)

`flutter test --no-pub --concurrency=6` over `test/`, run in foreground
chunks (pinned Shorebird Flutter 3.47.1):

| Chunk | Files | Passed | Skipped | Failed | Wall clock |
|---|---|---|---|---|---|
| `test/[a-b]*_test.dart` | 36 | 302 | 5 | 0 | 28 s |
| `test/[c]*_test.dart` | 38 | 415 | 2 | 0 | 36 s |
| `test/[d-l]*_test.dart` | 60 | 559 | 1 | 0 | 50 s |
| `test/[m-r]*_test.dart` | 70 | 877 | 1 | 1 | 60 s |
| `test/[s]*_test.dart` | 48 | 477 | 0 | 0 | 52 s |
| `test/[t-z]*_test.dart` | 58 | 790 | 3 | 0 | 55 s |
| `test/goldens`, `test/preview`, `test/fixtures` | 1 | 8 | 0 | 0 | 6 s |
| **Total** | **311** | **3428** | **12** | **1** | **~4.8 min** |

The one failure is unrelated to the plugin and left as is:
`test/offline_queue_test.dart` "queue limits queuing past the entry cap
evicts and reports it" pins `now = 2026-08-29` for the pure
`enforceLimits` helper but drives `queuePrompt`, which uses the real clock;
its `daysAgo(1)` entries (2026-08-28) crossed the 14-day queue TTL on
2026-09-11, so `queuePrompt` now refuses them as expired. It fails
deterministically in isolation too. Fix: pin the controller's clock or
build the entries relative to `DateTime.now()`.

One plugin-related breakage was fixed in this bead: `test/stash_lifecycle_test.dart`
asserted profile deletion deletes exactly `['pw.a']` from secure storage;
since TEAM-105 the deletion sweep also clears the plugin's per-profile
secret (`oc.orchestration.<id>.grant`, as `test/profile_deletion_test.dart`
already asserts), so the expectation now includes
`OrchestrationStore.secretKeys('a')`.

## 6a. Full suite — Sprint B (2026-09-11, TEAM-207)

`flutter test --no-pub --concurrency=6` over `test/`, foreground chunks
under 600 s each (pinned Shorebird Flutter 3.47.1), after TEAM-201..207:

| Chunk | Files | Passed | Skipped | Failed | Wall clock |
|---|---|---|---|---|---|
| `test/[a-b]*_test.dart` | 36 | 302 | 5 | 0 | 33 s |
| `test/[c]*_test.dart` | 38 | 415 | 2 | 0 | 33 s |
| `test/[d-l]*_test.dart` | 60 | 559 | 1 | 0 | 51 s |
| `test/[m-r]*_test.dart` | 70 | 878 | 1 | 0 | 55 s |
| `test/[s]*_test.dart` | 48 | 477 | 0 | 0 | 47 s |
| `test/[t-z]*_test.dart` | 63 | 951 | 3 | 0 | 70 s |
| `test/fixtures`, `test/goldens`, `test/preview`, `test/support` | 1 | 8 | 0 | 0 | 5 s |
| **Total** | **316** | **3590** | **12** | **0** | **~4.9 min** |

No unrelated failure this time: the `test/offline_queue_test.dart` clock
issue from §6 was fixed on dev in the meantime (27d83af). Two plugin
changes were needed for green in this bead: `run_screen.dart`'s state
word now wraps (`Flexible`) — it overflowed a 320 dp / 2.5× Arabic row
once the policy block made the overview taller — and the new controller
confirmation rule above. The front's own suite: 59 tests, green.

## 7. Emulator captures — pending

The card, home, run, work graph, agent and gate sheet captures at 1× and
2.5× RTL (05-beads TEAM-114 acceptance) are not yet recorded: the emulator
was off for this checkpoint. The layout suites already render every surface
at 320 dp / 2.5× LTR+RTL under test; `test/team_plugins_layout_test.dart`
writes PNGs into this directory when run with
`--dart-define=TEAM_PLUGINS_CAPTURE=true`. Device captures go here as
`<surface>-<scale>-<ltr|rtl>.png` once taken.
