# Beads — epic and tasks for ralph-tui

Conventions: prefix `TEAM-`; sizes fit one ralph-tui run; every bead ends
with the repo's quality gates:

- `flutter analyze --no-pub` → No issues found
- `flutter test --no-pub --concurrency=4 <touched suites>` green (full suite at each sprint end)
- `python3 tool/assemble_arabic_arb.py` → missing 0, mismatches 0; `flutter gen-l10n`
- `dart format` on changed files
- 320dp × 2.5x LTR + RTL layout test for any new screen

Dependencies use `bd dep add <bead> <depends-on>`.

## Epic

**TEAM-000 · AI Team (Gas City) as an optional plugin**
PRD: `docs/plans/gas-city-plugin-2026-09-10/01-prd.md`. UX:
`02-ux-flows-and-screens.md`. Architecture: `04-plugin-architecture.md`.
Gate: `06-decisions-and-readiness.md` must be fully ticked before any bead
after TEAM-002 starts.

---

## Gate spikes (evidence only, no product code)

### TEAM-001: Spike — Gas City on the dev PC, spec pin, fixture recording
As the owner, I want a real Gas City running on the Ubuntu host with the API
recorded so that every later bead codes against a pinned truth.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| contracts/gascity-supervisor-openapi-v0-<sha>.json | create | Pinned spec snapshot | from `GET /openapi.json` of the installed build |
| tool/qa/gascity_fixture/recordings/*.json | create | Recorded responses: health, cities, rigs, runs, run, beads, graph, agents, agent, sessions, pending, usage | secrets scrubbed |
| tool/qa/gascity_fixture/events/*.ndjson | create | Event logs: normal run, blocked, failed, stream drop | |
| docs/qa/ai-team/spike-pc-2026-09.md | create | Commands run, versions, what worked, what did not | |

Acceptance Criteria:
- [x] `gc version`, `gc doctor`, `gc supervisor start` outputs recorded; supervisor version and city name noted
- [x] `curl /health`, `/v0/cities`, `/v0/city/<c>/{rigs,agents,beads,runs,usage}` captured with response shapes matching `gas-town-findings.md` §2.2 or the differences listed
- [ ] A run created by `gc sling --formula` and a convoy created by the Mayor both appear in recordings
- [x] `events/stream` captured with `seq` and `Last-Event-ID` resume verified
- [ ] `tailscale serve --bg 8372` reachable from the phone over HTTPS (blocked: Serve not enabled on the tailnet); plain HTTP bind refused by `gc` documented ✔
- [x] Open questions 5, 7, 13 in `06-decisions-and-readiness.md` answered from evidence (06 §F); 1–3, 8, 12 unchanged by the spike

### TEAM-002: Spike — Gas City inside the on-device proot rootfs (arm64)
As the phone-first user, I want to know whether an AI team can run inside
the Linux environment the app already installs, so that the onboarding step
is real or absent.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| docs/qa/ai-team/spike-phone-2026-09.md | create | Evidence and verdict | |
| scripts/termux/aiteam-spike.sh | create | Reproducible spike script for the rootfs | not shipped in the app |

Acceptance Criteria:
- [ ] `gascity_<ver>_linux_arm64.tar.gz` checksum-verified and `gc version` runs under proot on the phone rootfs and on the emulator arm64 image if available
- [ ] `apt install tmux git jq procps lsof` succeeds in the rootfs; `gc doctor` output recorded
- [ ] `gc init` with the managed Dolt store (`bd` ≥ 1.0.4 and `dolt` arm64 inside the rootfs; `GC_BEADS=file` is ruled out by TEAM-001 §4.1) + `gc start` on `127.0.0.1:8372` answers `/health` and `/v0/cities` with `running=true`
- [ ] One agent using the on-device OpenCode harness (`opencode acp`) completes one trivial bead; whether its session is visible on the managed OpenCode server recorded (decides `sessionLink`)
- [ ] Loopback write path tested: does a mutation need a grant on loopback? Recorded
- [ ] Memory/CPU/battery observations over 30 min screen-off recorded; what Android kills and how the manager recovers
- [ ] Verdict line: PASS (Sprint C proceeds) or FAIL with reasons (Sprint C becomes copy-only)

---

## Sprint A — See (read-only)

### TEAM-101: Domain interface, capabilities and product models
As a developer, I want `OrchestrationGateway` and the product models so that
adapters and UI share one vocabulary.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/domain/orchestration_gateway.dart | create | Role interfaces, `OrchestrationCapabilities`, `MutationReceipt` | mirrors `server_gateway.dart` style |
| lib/orchestration/models/*.dart | create | Project, Run, Work, Agent, Gate, ActivityEvent, Usage with `raw` | |
| lib/orchestration/events/orchestration_event.dart | create | Sealed union + Unknown | |
| lib/orchestration/adapters/none.dart | create | Null gateway | |
| test/team_models_test.dart | create | State enums total, raw retained | |

Acceptance Criteria:
- [ ] Every product state enum has an `unknown` member and a `fromProvider(String)` that never throws
- [ ] `OrchestrationCapabilities` is const, all-`bool`, with `none`, `gascityRead`, `gascityFront`, `fixture` constants
- [ ] No import from `lib/api` or `lib/api2`
- [ ] Quality gates

### TEAM-102: Gas City DTO subset and mappers
Depends on TEAM-001, TEAM-101.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/orchestration/adapters/gascity/dto/*.dart | create | ~25 DTOs from the pinned spec | generated + committed |
| lib/orchestration/adapters/gascity/gascity_mappers.dart | create | DTO → product models, state table from 04 §4 | |
| test/team_gascity_mappers_test.dart | create | Round-trips every recording in tool/qa/gascity_fixture | |

Acceptance Criteria:
- [ ] Each recording parses; unknown fields ignored; unknown enum strings map to `unknown` and keep raw
- [ ] State table tests: one case per row of 04 §4 including `is_blocked`, `waiting`, `needs-review`, `last_error`, cancelled
- [ ] Quality gates

### TEAM-103: Fixture server and fixture adapter
Depends on TEAM-001.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| tool/qa/gascity_fixture/fixture_server.py | create | Stdlib HTTP+SSE server over recordings and event logs | like codex_fixture |
| tool/qa/gascity_fixture/README.md | create | How to run, scenarios | |
| lib/orchestration/adapters/fixture/fixture_gateway.dart | create | In-process gateway for widget tests | |

Acceptance Criteria:
- [ ] Scenarios: `normal`, `blocked`, `failed`, `stream-drop` selectable by query or env
- [ ] SSE honours `Last-Event-ID`, emits `event: heartbeat`
- [ ] `python3 -m unittest` for the fixture passes
- [ ] Quality gates

### TEAM-104: Gas City read adapter, probe and SSE client
Depends on TEAM-102.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/orchestration/client/http.dart | create | Dio + problem+json + request-id | |
| lib/orchestration/client/sse.dart | create | Parser, backoff, cursor | modelled on api2/sse2.dart |
| lib/orchestration/adapters/gascity/gascity_gateway.dart | create | Read roles + event channel | |
| lib/orchestration/adapters/gascity/gascity_probe.dart | create | `/health` + `/v0/cities` detection, TLS rule | |
| test/team_gascity_gateway_test.dart | create | Against the fixture server | |

Acceptance Criteria:
- [ ] Probe verdicts: Gas City found (version, city, readOnly), not a Gas City, city not running, plain-HTTP-refused, unreachable
- [ ] Reads succeed against fixture `normal`; `stream-drop` reconnects with `Last-Event-ID` and marks all scopes dirty on head-only replay
- [ ] No credential is ever sent on reads
- [ ] Quality gates

### TEAM-105: Profile config, OrchestrationController, cache and removal
Depends on TEAM-104.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/state/profiles.dart | modify | `OrchestrationConfig?` on ServerProfile, persisted | |
| lib/state/orchestration.dart | create | Controller: gateway lifecycle, dirty scopes, attention contribution | |
| lib/state/orchestration_store.dart | create | Cache + seq cursor per profile; drain; sweep keys | |
| lib/state/connection.dart | modify | Construct/dispose sibling; `unifiedAttentionCount` adds gates | minimal |
| test/team_controller_test.dart | create | Lifecycle, stale, removal, deletion sweep | |

Acceptance Criteria:
- [ ] Profile with config null creates no controller and no store keys
- [ ] Turning off deletes `oc.orchestration*.<profileId>` and secrets; profile deletion sweeps them (existing deletion test extended)
- [ ] Stale detection: data older than 60 s or stream disconnected → `isStale`
- [ ] Attention count includes pending interactions + open gate beads + failed runs
- [ ] Quality gates

### TEAM-106: More › Plugins screen and enable/disable/discovery sheets
Depends on TEAM-105.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/ui/screens/settings/plugins_screen.dart | create | Plugins group, AI Team row and sheet (02 §1, §9) | |
| lib/ui/screens/servers_screen.dart | modify | "AI Team (optional)" section in the editor | |
| lib/ui/screens/settings_screen.dart | modify | Plugins entry | |
| lib/l10n/app_en.arb | modify | `teamUi*` keys | |
| docs/qa/ai-team/messages_ar.json | create | Arabic fragment | |
| docs/ai-team-host.md | create | Host guide (computer path) | |
| test/team_plugins_screen_test.dart, test/team_plugins_layout_test.dart | create | States, verdicts, 320dp/2.5x LTR+RTL | |

Acceptance Criteria:
- [ ] Row subtitles match 02 §1.1 for each state
- [ ] Manual add refuses `http://` to non-loopback with the exact copy from 03 §5
- [ ] Discovery card appears once per server after a 200 from the well-known route and is remembered when dismissed
- [ ] Turn-off sheet copy and effects per 02 §1.3
- [ ] Quality gates

### TEAM-107: Workspace AI Team card
Depends on TEAM-105.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/ui/widgets/team_card.dart | create | Card per 02 §2 incl. constellation, segmented progress, pulse | |
| lib/ui/screens/workspace_screen.dart | modify | Place card above Sessions when enabled | |
| test/team_card_test.dart, test/team_card_layout_test.dart | create | States L/E/S/X/N, reduced motion, 320dp/2.5x | |

Acceptance Criteria:
- [ ] With config null the card widget is absent from the tree
- [ ] Header counts and run rows match fixture `normal`; blocked segment visible in `blocked`
- [ ] Stale shows "Showing data from HH:MM · host unreachable" and disables taps except Refresh
- [ ] Reduced motion: no animation controller running
- [ ] Quality gates

### TEAM-108: AI Team home and Run list
Depends on TEAM-107.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/ui/screens/team/team_home_screen.dart | create | Runs · Agents · Needs you segments, host chip | |
| test/team_home_test.dart | create | Filters, grouping, host chip → technical details | |

Acceptance Criteria:
- [ ] Runs ordered active → waiting/blocked → completed (collapsed group)
- [ ] Filter chips and search work on fixture data
- [ ] Host chip opens Technical details with provider, version, city, read-only
- [ ] Quality gates

### TEAM-109: Run detail — Overview and Timeline
Depends on TEAM-108.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/ui/screens/team/run_screen.dart | create | Tabs; Overview per 02 §4.1; Timeline per 02 §4.4 | |
| test/team_run_screen_test.dart | create | Five-question layout, blocked causes, stage strip, timeline filters, jump-to-latest | |

Acceptance Criteria:
- [ ] Overview shows objective, progress, Now, Blocked (with cause text), Needs you, Stages in that order
- [ ] Convoy run shows "Batch of N · M done" instead of stages
- [ ] Timeline filters reduce rows; new events prepend with jump-to-latest when scrolled
- [ ] Missing run → "no longer on the host" state
- [ ] Quality gates

### TEAM-110: Run detail — Work tab (list + graph) and Work sheet
Depends on TEAM-109.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/ui/screens/team/work_graph.dart | create | CustomPainter DAG, layered layout, pinch/pan, critical path | no new dependency |
| lib/ui/screens/team/work_sheet.dart | create | Work detail per 02 §4.2 | |
| lib/ui/screens/team/run_screen.dart | modify | Work tab with List/Graph toggle remembered per run | |
| test/team_work_graph_test.dart, test/team_work_sheet_test.dart | create | Layout determinism, node tap, 320dp defaults to List | |

Acceptance Criteria:
- [ ] Graph layout is deterministic for a fixture graph (golden positions asserted)
- [ ] Nodes carry glyph + colour + label; blocked chain highlighted; critical path thicker
- [ ] Work sheet shows dependencies and blocking as tappable chips; session link only when present
- [ ] Quality gates

### TEAM-111: Agents fleet, Agent detail, live output
Depends on TEAM-108.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/ui/screens/team/agent_screen.dart | create | Sections per 02 §5.2, output tail with Follow | |
| lib/ui/screens/team/team_home_screen.dart | modify | Agents segment rows, shift strip | |
| test/team_agent_screen_test.dart | create | Status vocabulary, context meter thresholds, output follow | |

Acceptance Criteria:
- [ ] Six status words rendered with glyphs; sort order per 02 §5.1
- [ ] Context meter texture changes at 75% and 90%; "recycling soon" shown when policy says so
- [ ] Output tail is LTR mono, follows by default, stops following on scroll-up
- [ ] Quality gates

### TEAM-112: Activity integration (read-only gates) and Gate sheet rendering
Depends on TEAM-105.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/ui/screens/activity_screen.dart | modify | AI Team items in BRD priority order | |
| lib/ui/screens/team/gate_sheet.dart | create | Choice / Confirmation / Free text / Gate bead / Run failed, read-only in Sprint A | |
| test/team_activity_test.dart | create | Ordering, per-server scoping, sheet variants | |

Acceptance Criteria:
- [ ] Ordering: decision → run failed → permission → review ready → agent blocked → completion
- [ ] Read-only sheets show "Answer this on the host" with the host guide link
- [ ] Failed run sheet shows classification, affected work, recoverable, recommended action
- [ ] Quality gates

### TEAM-113: Usage surfaces (estimated)
Depends on TEAM-109.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/ui/screens/team/run_screen.dart | modify | Cost chip on Overview | |
| lib/ui/screens/team/agent_screen.dart | modify | Tokens/context/cost on Runtime | |
| test/team_usage_test.dart | create | "est." suffix; absent when no data | |

Acceptance Criteria:
- [ ] Every cost shows "est."; no cost when `/usage` returns nothing
- [ ] Quality gates

### TEAM-114: Plugin-off regression, full suite, PC read proof, checkpoint
Depends on TEAM-106..113.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| test/team_plugin_off_test.dart | create | Tree assertions; no new ServerGateway calls | |
| docs/qa/ai-team/README.md | create | Evidence: emulator captures, real read proof against the PC | |
| CHANGELOG.md, HANDOVER.md, PRIVACY.md | modify | Checkpoint notes and data disclosure | |

Acceptance Criteria:
- [ ] Plugin off: Workspace, Activity, More trees contain no `team*` keys
- [ ] Full suite green at `--concurrency=6`
- [ ] Emulator captures of card, home, run, work graph, agent, gate sheet at 1x and 2.5x RTL
- [ ] Real read proof: the PC's Gas City run visible on the phone with matching counts
- [ ] Local APK checkpoint published to the preview server

---

## Sprint B — Answer and steer

### TEAM-115: Quiet the noise (first phone use, 2026-09-11)
Owner verdict on the first real screen: "so confusing". Hide the pack's
upkeep runs (`mol-*-patrol`, orders, nudges), name a batch by its work
item's title instead of `sling-<id>`, give a batch an honest state
("Waiting for an agent" rather than "Planning"), count and draw only live
agents (empty pool slots and suspended helpers are not agents), and name the
host (dev-pc / 100.101.102.103) in the chip instead of the OpenCode server.

### TEAM-116: Dispatch cycle — show the steps moving
Owner request 2026-09-11: "logically intercept the steps and render them in
a nice way so the user can know that the cycle is moving."

As a phone user who just started a task, I want to see which step the team
is on and why it is waiting, so that a two-to-six-minute dispatch never
looks like nothing is happening.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/orchestration/models/dispatch_cycle.dart | create | `DispatchStep` enum (routed, agentStarting, claimed, working, pushed, handedToMerge, merged) + `DispatchCycle` (step, since, stalled, stallReason) | derived, never fetched |
| lib/orchestration/adapters/gascity/gascity_mappers.dart | modify | Derive the cycle per work item from bead metadata + sessions + timeline events | evidence table below |
| lib/state/orchestration.dart | modify | Keep per-work step timestamps from events (append-only), expose `cycleFor(workId)`; stall detection timers | |
| lib/ui/widgets/team_cycle_strip.dart | create | Six-step strip: done ✓, current pulsing (static under reduced motion), future dim; timestamps; "usually 1–5 min" hint on Agent starting; stall sentence + one action | |
| lib/ui/widgets/team_card.dart, lib/ui/screens/team/run_screen.dart, lib/ui/screens/team/work_sheet.dart | modify | Strip on the card's headline batch, on the run Overview (replaces "Stages" for a batch), and in the Work sheet | |
| lib/l10n/app_en.arb, docs/qa/ai-team/messages_ar-cycle.json | modify/create | `teamUiCycle*` | |
| test/team_cycle_test.dart | create | Derivation from the recorded normal-run log; stall reasons; rendering states | |

Evidence → step:
- Routed: `bead.updated` with `metadata.gc.routed_to` (or the bead already carries it)
- Agent starting: `session.woke` for a session of the routed template in the rig; or a polecat session appearing in `/sessions`
- Claimed: `bead.updated` → status in_progress with `assignee`
- Working: agent output growing, or `metadata.gc.work_dir` / worktree present
- Pushed: `metadata.branch` set
- Handed to merge: assignee = the rig's refinery
- Merged: `bead.closed` or `close_reason` merged, or the run completed

Stall reasons (shown after the step's usual window):
- Routed > 3 min without Agent starting → "The host has not started an agent yet" · action: Refresh / "How the host dispatches" sheet
- Agent starting flapping (woke/stopped within 60 s, 2+ times) → "The agent could not start on the host" · action: open host guide
- Claimed/Working with the transcript containing "usage limit" / "quota" / "rate limit" → "The model provider reached its usage limit" · action: Open agent output; Stop
- Working > 30 min without Pushed → "Still working — check the agent's output" · action: Open output
- Handed to merge > 15 min → "Waiting for the merge agent" · action: Nudge refinery (when controls exist)

Acceptance Criteria:
- [ ] Over the recorded `normal-run` event log the derived steps are Routed 22:44:52 → Agent starting 22:44:59 → Claimed 22:46:34 → Pushed 22:51:04 → Handed to merge 22:53:47, in order, with those timestamps
- [ ] A batch that only has Routed shows "Waiting for an agent · usually 1–5 min" with the current step pulsing; reduced motion → static
- [ ] Each stall reason renders its sentence and action; the usage-limit reason is detected from a transcript containing "usage limit has been reached"
- [ ] Strip fits 320 dp at 2.5× (steps wrap to two rows), LTR and RTL, English and Arabic
- [ ] Quality gates

### TEAM-201: Control-plane front (host) MVP
Depends on TEAM-114 and decisions 9–11.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| tool/host/cp_front/front.py | create | Loopback behind `tailscale serve`; identity check via `tailscale whois`; mints grant; forwards mutations; SSE pass-through | Python stdlib |
| tool/host/cp_front/README.md, docs/ai-team-host.md | create/modify | Install, systemd unit, allowlist | |
| tool/host/cp_front/tests/ | create | Unittest with a stub supervisor | |

Acceptance Criteria:
- [ ] Refuses to start unless bound to loopback and fronted (documented check)
- [ ] `/.well-known/opencode-mobile-orchestration` returns provider, supervisorUrl, city, front=true, capabilities
- [ ] Mutation forwarding maps idempotency key → request_id; replays return the same receipt
- [ ] No secret logged

### TEAM-202: `OrchestrationControlGateway` + front adapter + receipts
Depends on TEAM-201.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/orchestration/adapters/gascity/gascity_control.dart | create | Mutations via front; receipt resolution from events | |
| lib/state/orchestration.dart | modify | Idempotency store, pending receipts, "sent, unconfirmed" state | |
| test/team_control_test.dart | create | Receipt lifecycle incl. stream drop | |

Acceptance Criteria:
- [ ] Idempotency key persisted before send; app restart shows the mutation as unconfirmed, never re-sends
- [ ] Receipt resolves on matching `request.result`; timeout → pending with copy from 02 §6
- [ ] Quality gates

### TEAM-203: Answer decisions and close gates from Activity
Depends on TEAM-202, TEAM-112.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/ui/screens/team/gate_sheet.dart | modify | Send/Approve/Mark done actions with confirmation states | |
| lib/background/… (notifications) | modify | Push for decision, run failed, review ready, completed; deep link by ids | |
| test/team_gate_answer_test.dart | create | Routing by request_id, states, notification deep link | |

Acceptance Criteria:
- [ ] Answer routes to exactly the requesting interaction; row leaves the list only after confirmation
- [ ] Destructive confirmations are two-step and red
- [ ] Notification tap opens the exact gate sheet; no auto-send
- [ ] Quality gates

### TEAM-204: Agent controls and run controls
Depends on TEAM-202, TEAM-111.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/ui/screens/team/agent_screen.dart | modify | Message, Nudge, Pause/Resume, Stop, Restart, Reassign | |
| lib/ui/screens/team/run_screen.dart | modify | Pause/Resume/Cancel, Start a run sheet (02 §7) | |
| test/team_controls_test.dart | create | Gating, confirmations, receipts | |

Acceptance Criteria:
- [ ] Controls absent (not disabled) without `control*` capabilities
- [ ] Stop/Restart/Cancel are two-step; Nudge is one tap with a receipt chip
- [ ] Start a run sends the objective to the planner and shows "Planning… (Mayor)" until beads exist
- [ ] Quality gates

### TEAM-205: Merge readiness, approve request, Merge button
Depends on TEAM-202, TEAM-109.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| tool/host/cp_front/front.py | modify | `/merge-readiness`, `/mr/{id}/approve`, `/merge` (idempotent, boundary-checked) | |
| lib/orchestration/adapters/gascity/gascity_control.dart | modify | Merge roles | |
| lib/ui/screens/team/run_screen.dart | modify | Merge section per 02 §8a | |
| test/team_merge_test.dart | create | Readiness gating, approve, two-step merge, boundary refusal | |

Acceptance Criteria:
- [ ] Merge button disabled with the missing readiness line named when any check fails
- [ ] Approve request needs one confirmation; Merge needs two-step and shows the boundary that blocks it when the host refuses
- [ ] Force-merge / reset / delete worktree are absent from the phone
- [ ] Quality gates

### TEAM-206: Host guides for Linux, macOS and Windows (WSL) with disclaimers
Depends on TEAM-114.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| docs/ai-team-host.md | modify | Per-OS install, tailscale serve, front, disclaimers | |
| lib/ui/widgets/team_card.dart, lib/ui/screens/settings/plugins_screen.dart | modify | Disclaimer line per host mode | |

Acceptance Criteria:
- [ ] Each host mode shows its disclaimer line from 03 §4
- [ ] Quality gates

### TEAM-207: Supervision policy and boundaries display; write proof; checkpoint
Depends on TEAM-203, TEAM-204, TEAM-205.

Acceptance Criteria:
- [ ] Run overview shows supervision level and boundaries from host config, read-only
- [ ] Real proof: answer an interaction and stop an agent from the phone against the PC; recorded in docs/qa/ai-team/
- [ ] Full suite green; checkpoint APK published

---

## Sprint C — On this phone (only if TEAM-002 = PASS)

### TEAM-301: Managed `gc` runtime in the rootfs (manager.sh verbs)
### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/termux/bridge.dart | modify | `aiteam install/start/stop/status/remove` verbs, pinned version + SHA | |
| lib/termux/team_runtime.dart | create | Dart side: state file parsing, capability `phoneHost` | |
| test/team_runtime_test.dart | create | Verb contracts, checksum failure, remove leaves project files | |

Acceptance Criteria:
- [ ] Install verifies the tarball checksum and refuses on mismatch
- [ ] `remove` deletes gc, city and file store; project files untouched (tested on fixture paths)
- [ ] Quality gates

### TEAM-302: Optional onboarding step and Settings "On this phone"
Depends on TEAM-301, TEAM-106.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/ui/screens/termux_setup_screen.dart | modify | Optional block per 03 §2, five visible steps, live output | |
| lib/ui/screens/settings/plugins_screen.dart | modify | On this phone section per 02 §9 | |
| test/team_phone_onboarding_test.dart | create | Absent without `supportsAiTeam`; skip path; step states | |

Acceptance Criteria:
- [ ] Block absent when the runtime does not advertise support
- [ ] Skip is primary; re-offer once from Settings
- [ ] Success card opens Workspace with the card present for the Termux profile
- [ ] Quality gates

### TEAM-303: Phone-host proof and lifecycle truth
Depends on TEAM-302.

Acceptance Criteria:
- [ ] Emulator rootfs: install, start, one run with one agent, stop, remove; captures in docs/qa/ai-team/phone/
- [ ] Physical phone: the same, plus a 30-minute screen-off observation recorded honestly
- [ ] "Android stopped the team" copy shown after a forced stop; Start again recovers
- [ ] Full suite green; checkpoint APK published

### TEAM-304: Storage on this phone (Termux server manager)
Owner request 2026-09-11 after finding Termux at 46 GB: "OpenCode Mobile
should allow me to clean this up through the server manager plugin."
Independent of TEAM-301..303; can ship in Sprint C or earlier.

As a phone-hosted user, I want to see what the managed Linux environment
is using and clean it from the app, so that I never need SSH to reclaim
tens of gigabytes of agent build caches.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| scripts/termux/manager.sh (source of `~/.oc/manager.sh`) | modify | `storage-scan` (JSON breakdown by category) and `storage-clean <category>` verbs | scan uses `du` inside the rootfs and Termux `$PREFIX`; clean removes only listed paths |
| lib/termux/storage.dart | create | Runs the verbs through the existing Termux bridge; parses the JSON | |
| lib/ui/screens/termux_storage_screen.dart | create | Settings › Termux server › Storage | |
| lib/ui/screens/termux_setup_screen.dart, settings | modify | Entry row with total size | |
| lib/l10n/app_en.arb, docs/qa/ai-team/messages_ar-storage.json | modify/create | `termuxStorage*` keys | |
| test/termux_storage_test.dart | create | Parsing, categories, confirmation, never-delete list | |

Categories (measured on the owner's phone, 2026-09-11):
- **Build caches** — rootfs `/root/.gradle/{caches,wrapper}`, `/root/.pub-cache`,
  `/root/.cache`, `/root/.dartServer`, `~/.npm`, `~/.cache` (11.6 GB)
- **Agent scratch** — rootfs `/tmp/opencode/*` (11 GB, 136 dirs), Termux
  `$PREFIX/tmp`
- **Project build outputs** — `build/`, `.dart_tool/`, `node_modules/`,
  `target/` inside `/root/projects/*` (most of IPTV_King's 3.6 GB)
- **Toolchains** — `/usr/lib/android-sdk`, `/usr/lib/jvm`, Flutter SDK
  copies under `/tmp/opencode` (about 6 GB); shown with "only if you build
  Android apps on this phone"
- **AI Team** — city dirs, Dolt store, polecat worktrees, `gc`/`bd`/`dolt`
  binaries (about 0.6 GB)
- **OpenCode itself** — node_modules, auth, sessions DB (never offered for
  cleaning here; sessions have their own screen)
- **Projects (your files)** — listed, never deletable from this screen

Acceptance Criteria:
- [ ] Scan shows total, per-category size and the exact paths that a clean
      would remove; scan runs in the background with the same live-output
      panel as install, and is cancellable
- [ ] Clean is per category, two-step for anything over 1 GB, shows freed
      bytes afterwards, and never touches `/root/projects/<name>` sources,
      OpenCode's auth/sessions, or the Termux packages the server needs
- [ ] A category with a running process using it (Gradle daemon, dolt,
      supervisor) refuses with the process name
- [ ] Copy is honest about regeneration ("the next build will download
      these again")
- [ ] Quality gates

### TEAM-305: Running servers and processes on this phone (Termux server manager)
Owner request 2026-09-11 ("and running servers plugin also"). Independent of
TEAM-301..303; pairs with TEAM-304.

As a phone-hosted user, I want to see what is running inside the managed
Linux environment and stop what I don't need, so that a stray agent helper
or build daemon cannot burn the battery for an hour unnoticed.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| scripts/termux/manager.sh | modify | `procs-scan` (JSON: pid, parent, group, name, cpu %, rss, elapsed, cwd) and `procs-stop <pid|group>` verbs | Termux `ps -eo` plus rootfs view; groups by ancestry |
| lib/termux/processes.dart | create | Runs the verbs; parses; groups | |
| lib/ui/screens/termux_processes_screen.dart | create | Settings › Termux server › Running now | |
| lib/ui/screens/termux_setup_screen.dart, settings | modify | Entry row with "N processes · CPU x%" | |
| lib/l10n/app_en.arb, docs/qa/ai-team/messages_ar-processes.json | modify/create | `termuxProcs*` keys | |
| test/termux_processes_test.dart | create | Grouping, orphan detection, two-step stop, protected groups | |

Groups (what the owner saw this week): **OpenCode server** (`opencode
serve`, its MCP servers), **AI Team** (gc supervisor, dolt, tmux, per-agent
`opencode acp` + their MCP servers, `gc`/`bd` children), **Build daemons**
(Gradle, Kotlin, dart analysis server, node), **Orphans** (a helper whose
parent is gone or that has used > 5 min CPU with no live parent — the
`minimax-coding-plan-mcp` case), **Other**.

Acceptance Criteria:
- [ ] Scan lists every process the app's Termux user owns, grouped, with CPU
      and memory, refreshed on pull and every 10 s while the screen is open
- [ ] Orphans are flagged with why ("parent exited 42 min ago", "3 h CPU")
      and can be stopped in one tap; stopping a whole group (AI Team, Build
      daemons) is two-step; the OpenCode server and sshd are protected and
      redirect to their own controls
- [ ] Stop sends TERM, waits 5 s, then KILL, and reports what remained
- [ ] Attention: when the app is open and an orphan has burned > 10 min of
      CPU, a Workspace line "Something is still running on this phone" links
      here (no push notification in v1)
- [ ] Quality gates

### TEAM-306: Direct task to an agent when the planner is off (phone lean profile)
Found during the TEAM-303 emulator proof on 2026-09-12: the phone's lean
profile keeps `gastown.mayor` suspended, so the Start-a-run sheet stopped
at "The planner (Mayor) is off on this host" and a phone-only user could
start nothing. Depends on TEAM-301 (loopback controls); independent of
TEAM-304/305.

As a phone-hosted user with no PC, I want to hand one task straight to a
project's agent pool from the Start-a-run sheet, so that the team does
work for me even though the planner is off on this host.

### Impact Table
| Path | Change | Purpose | Notes |
|---|---|---|---|
| lib/domain/orchestration_gateway.dart | modify | `OrchestrationControlGateway.createWork`, `OrchestrationCapabilities.controlCreateWork` (on for `gascityLoopback` and `fixture`, off for `gascityFront`), `MutationReceipt.createdId` | the front has no create route; a PC-hosted city keeps its planner |
| lib/orchestration/adapters/gascity/gascity_control.dart, gascity_gateway.dart | modify | `POST /beads` with the write-proof body (`title, description?, rig?, type: task, priority: 2, labels: [opencode-mobile]`) | 201 body is the bead; `raw['id']` is the new work id |
| lib/orchestration/adapters/fixture/fixture_gateway.dart, none.dart | modify | `createWork` double answering `fx-new-1`; the none adapter rejects | |
| tool/qa/gascity_fixture/fixture_server.py (+ test) | modify | `POST /beads` → 201 `fx-<n>` and a `bead.created` event | |
| lib/state/mutation_store.dart, lib/state/orchestration.dart | modify | `MutationKind.createWork` (target = title, text = details, new `projectId`), `createWork`, `giveTask` (create → sling the created id → refetch work and runs); a 201 with an id confirms at once, `bead.created` confirms too | |
| lib/state/team_planning.dart | modify | `teamWorkerPoolId(rig)` = `<rig>/gastown.polecat` | |
| lib/ui/screens/team/start_run_sheet.dart, team_home_screen.dart | modify | Direct task form (`team-start-run-direct*`) when the planner is off, the host creates work and a project is listed; the home shows "Task sent to an agent · Confirmed" or the refusal | the host guide stays below the form |
| lib/ui/widgets/team_controls.dart, team_receipt.dart | modify | Receipt word "Task sent to an agent"; gate matching ignores the kind | |
| lib/l10n/app_en.arb, docs/qa/ai-team/messages_ar-startrun.json, messages_ar-control.json | modify | `teamUiStartRunDirect*`, `teamUiControlCreateWork` | |
| test/team_controls_test.dart, team_control_test.dart, team_gascity_gateway_test.dart, team_fixture_gateway_test.dart, team_models_test.dart, team_runtime_test.dart | modify | Form → `createWork` then `assign` to `ocproof/gastown.polecat`; refused create stays on the sheet; front host keeps the planner-off copy; route body and headers; receipt `createdId`; request round-trip | |

Acceptance Criteria:
- [x] With the planner suspended or missing on a host that creates work
      (loopback) and at least one project, the sheet shows the direct task
      form: intro line, project (first by default), required title,
      optional details, "Send to an agent", host guide below
- [x] Send creates one bead (`POST /beads`) in the chosen rig and slings
      it at `<rig>/gastown.polecat` with `reassign: true`; the sheet closes
      and the home shows the receipt "Task sent to an agent · Confirmed"
- [x] An empty title shows "Write a task first." and sends nothing; a
      refused create stays on the sheet with the host's reason and slings
      nothing; a refused sling reaches the home as its own receipt
- [x] A front host (`gascityFront`, no `controlCreateWork`) keeps the
      planner-off copy; so does a loopback host with no project listed
- [x] Records persist with the title, details and rig; the fixture server
      answers 201 and emits `bead.created`
- [x] Quality gates: `flutter analyze`, the team suites and the fixture
      server tests green; Arabic strings assembled, never hand-edited

## Dependency summary

```
TEAM-001 ─┬─ TEAM-102 ─ TEAM-104 ─ TEAM-105 ─┬─ TEAM-106 ─┐
          └─ TEAM-103 ─┘                      ├─ TEAM-107 ─ TEAM-108 ─┬─ TEAM-109 ─ TEAM-110
TEAM-101 ─┘                                   │                       └─ TEAM-111
                                              └─ TEAM-112              TEAM-113 (after 109)
TEAM-106..113 ─ TEAM-114 ─ TEAM-201 ─ TEAM-202 ─┬─ TEAM-203 ─┐
                                                └─ TEAM-204 ─┴─ TEAM-205 ─ TEAM-207 (TEAM-206 after 114)
TEAM-002 (PASS) + TEAM-106 ─ TEAM-301 ─ TEAM-302 ─ TEAM-303
```
