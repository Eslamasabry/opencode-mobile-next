# TEAM-001 — Gas City on the dev PC (spike report)

Date: 2026-09-10. Host: Ubuntu PC `dev-pc` (Tailscale 100.101.102.103), 8 cores.
Verdict: **PASS with conditions.** One bead went sling → polecat → refinery →
merged on `origin/master` with the Gas Town pack and OpenCode as the harness.
The conditions are the findings in §4; the plan docs are updated from them.

Workspace (not in the repo): `/home/eslam/Storage/Code/gascity-spike/`
(`bin/gc`, `city/` file-backed attempt, `city2/` Dolt-backed city, `rec/`
raw recordings). Rig: `/home/eslam/Storage/Code/oc-bg-proof` (`calc.py`).

## 1. Versions

| Component | Version | Notes |
|---|---|---|
| Gas City `gc` | 1.4.1 (build 58ef17e3bd68) | linux_amd64 tarball; `gc` symlinked into `~/.local/bin` |
| Gas Town pack | `gastownhall/gascity-packs` @ 33d3a430 | pinned by `gc init` |
| beads `bd` | 1.2.2 | `gc init` refused 1.0.2 ("need v1.0.4+") |
| Dolt | 2.3.3 | managed `dolt sql-server` started by the city (`NativeDoltStore`) |
| OpenCode | 1.18.25 | agents run as `opencode acp` child processes |
| Model | `zai-coding-plan/glm-5.3-flash` | set in `~/.config/opencode/opencode.json` for the spike, restored after |

Supervisor API: `http://127.0.0.1:8372`, spec `GET /openapi.json` = 127 paths,
pinned at `contracts/gascity-supervisor-openapi-v0-3648ca2d499a.json`.

## 2. What was run

```
gc init --file city2.toml --name bright-lights --no-start city2   # opencode provider, gastown pack
gc rig add /home/eslam/Storage/Code/oc-bg-proof --name ocproof     # NOT "gc rig add ocproof <path>" (see §4.3)
gc start                                                           # installs a systemd user unit; see §4.4
gc bd create "Add subtract function to calc.py" -d "..." -p 1      # in the rig dir → oc-loy
POST /v0/city/bright-lights/sling  {"bead":"oc-loy","target":"ocproof/gastown.polecat","rig":"ocproof","merge":"local"}
```

Timeline for bead `oc-loy` (from `tool/qa/gascity_fixture/events/normal-run.ndjson`):

| t+ | Event |
|---|---|
| 0:00 | `sling` → `bead.updated` (metadata `gc.routed_to`), convoy `oc-xru` created (`sling-oc-loy`, `tracks` dep) |
| ~1:30 | pool spawns polecat session `bl-48k`; `gc hook --claim` → `in_progress`, assignee `gastown__polecat-bl-48k` |
| ~5:00 | worktree `city2/.gc/worktrees/ocproof/polecats/gastown.furiosa` on branch `polecat/oc-loy` |
| ~7:00 | commit `c02e375 Add subtract function to calc.py`, pushed to `origin/polecat/oc-loy`, `metadata.branch` set |
| ~8:00 | bead re-assigned to `ocproof/gastown.refinery`, status back to `open` |
| ~13:00 | refinery fast-forwards `origin/master` to `c02e375`; smoke-tested; pours next wisp |

The bead itself stayed `open` with assignee refinery. The refinery transcript
(`events/session-refinery.ndjson`) explains why: it was still holding the
stale bead `oc-cq6` from the earlier wrong-rig attempt (§4.3; `gc rig remove`
+ re-add kept the old Dolt rows), found no `polecat/oc-cq6` branch, "created
the declared branch at that commit as a trivial resolution", fast-forwarded
`master` to `c02e375` (the *correct* commit from `oc-loy`'s branch) and then
closed `oc-cq6`, not `oc-loy`. Lesson for the app: the merge is verifiable
from git, but bead closure after merge is not guaranteed, and stale beads
from a re-registered rig confuse the refinery.

## 3. Recorded API shapes

All in `tool/qa/gascity_fixture/recordings/` (per-session `instance_token`
values redacted). Highlights the adapter (TEAM-102) must honour:

- `GET /health` → `{status, version, city, uptime_sec}`; `GET /status` carries
  `work.{in_progress,ready,open}`, `agents`, `store_health`, `beads.beads_store`.
- `GET /agents` → `{items:[{name, state: idle|stopped|…, session:{name,last_activity,attached}, pool, pack, provider}]}`;
  pool templates (`ocproof/gastown.polecat`) appear once, instances are sessions.
- `GET /sessions` → `{items:[{id, state, template, …}]}`; polecat sessions are
  named `gastown__polecat-<id>`; `GET /session/{id}/stream` is SSE with
  `id:` cursors, `event: turn` carrying the whole transcript so far as
  `turns[].text` (token-per-line for streamed thoughts), and `data:{"timestamp"}` heartbeats.
  A finished session answers 404 `urn:gascity:error:session-not-found` ("no live output").
- `GET /bead/{id}` → flat bead with `metadata` map; routing state lives in
  metadata: `gc.routed_to`, `gc.session_id`, `gc.session_name`, `gc.work_dir`,
  `branch`, `merge_strategy`, `target`, `gc.work_branch`.
- `GET /beads?ready=true&rig=…` → `{items:[…]}`; `GET /convoys` → convoy beads
  with `dependencies[{type:"tracks"}]`.
- `GET /runs` answered `{"runs":[],"partial":true,"partial_errors":["run projection is warming"]}`
  for the entire spike (direct bead slings are not formula runs). Runs need a
  `--formula` sling; not exercised here (see §5).
- `GET /pending`, `GET /waits` → `{items:[],total:0}` throughout.
- `GET /events` is a JSON page (`{items:[{seq,type,ts,actor,subject,payload}]}`,
  newest first, supports `index`+`wait` long-poll). **SSE is `GET /events/stream`**
  with `id: <seq>` and `event: event`; resume works with `?after_seq=N`
  and with the `Last-Event-ID` header (both recorded).
- Writes need the `X-GC-Request` header (any value) on loopback; no grant.
  `POST /sling` → `{status:"slung",target,bead,mode:"direct",dashboard_url}`.

Event types seen: `bead.created/updated/closed`, `convoy.closed`,
`session.woke/stopped`, `order.fired/completed/failed`, `mail.read/archived`,
`controller.started`, `project.identity.stamped`.

## 4. Findings that change the plan

### 4.1 The file beads store cannot run the Gas Town pack
With `[beads] provider = "file"` the city boots and the API serves beads, but
every agent's `work_query`, the claim protocol and the formulas shell out to
`bd` (`bd ready --assignee …`, `gc bd update --claim`, `bd close`). `gc bd`
refuses on a file-backed scope and bare `bd` finds no database, so
`gc hook --claim` always returns `no_work` and the polecat loops forever. The
file store also reuses bead IDs across scopes (`gc-1` was both my task and a
closed order-tracking bead). **Consequence:** the phone plan in
`03-onboarding-on-device.md` §1 ("GC_BEADS=file removes Dolt, bd and flock")
is not viable with the Gas Town pack. TEAM-002 must either run Dolt + bd on
arm64 inside proot or use a pack whose queries do not need `bd`.

### 4.2 The rig needs an `origin` remote
The polecat formula fetches `origin/<base_branch>` and pushes
`origin/polecat/<bead>`; the refinery merges from `origin`. A plain local repo
without a remote made the first polecat bail out silently (bead released back
to `open`, process exited, no branch). A local bare repo as `origin` is enough.
Host guide (`docs/ai-team-host.md`) must say so.

### 4.3 `gc rig add` takes a path, not a name
`gc rig add ocproof /path` created an empty repo at `<city>/ocproof` and ignored
the path; the polecat then *invented* `calc.py` from the bead description and
"added subtract" to it. Correct form: `gc rig add /path --name ocproof`. The
app's host guide and any future setup script must use `--name`.

### 4.4 `gc start` installs a systemd user unit that lacks the tool PATH
`gc start` wrote `~/.local/share/systemd/user/gascity-supervisor.service`. Under
it, pack orders failed with `gc: command not found` (the unit's PATH did not
contain the `gc` location) and the reconciler logged `no tmux server running`
continuously. Running `gc supervisor run` from a shell with `gc` on PATH and
`TMPDIR=/tmp` behaved. For hosts, put `gc` in `~/.local/bin` before `gc start`.

### 4.5 Agents are ACP child processes, not OpenCode server sessions
Every agent is `sh -c opencode acp` with `GC_*` env in the agent's work dir;
nothing appears on an `opencode serve` instance. The only transcript source is
the supervisor's `session/{id}/stream`, and it is gone once the session stops.
**Decision 13 (`sessionLink`) = false for the OpenCode harness.** The app shows
the supervisor transcript and must persist what it has seen if it wants history.

### 4.6 Cost and hygiene
The Gas Town pack keeps mayor, deacon, boot, witness (+ dog pool) awake; each
is an `opencode acp` process with its own MCP servers. Two `minimax-coding-plan-mcp`
servers from stopped agents kept spinning at 100% CPU after their agent died
(66 CPU-minutes each) until killed by hand. The host guide needs a "stop the
team" step that also reaps orphaned MCP servers; the app's "AI team is
running" card should show agent count and warn about idle patrol agents.

### 4.7 Tailnet HTTPS is not enabled on this tailnet
`tailscale serve --bg 8372` → "Serve is not enabled on your tailnet. To enable,
visit https://login.tailscale.com/f/serve?node=…"; `CertDomains` is empty.
The read plane (decision 5) depends on this. **Owner action required** before
TEAM-101's connect flow can be verified from the phone. The supervisor binds
loopback only, so plain HTTP over the tailnet is refused by construction.

## 5. Acceptance criteria status (05-beads TEAM-001)

- [x] `gc version`, `gc doctor`, supervisor start outputs recorded (`rec/config_show.toml`, `supervisor2-run.log`, this doc)
- [x] `/health`, `/status`, `/rigs`, `/agents`, `/beads`, `/runs`, `/usage`, `/sessions`, `/pending`, `/convoys` captured
- [ ] A run created by `gc sling --formula` and a Mayor-created convoy: **not done**; direct sling + auto-convoy only. `/runs` stayed empty. Do this before TEAM-102 fixes the Run mapping.
- [x] `events/stream` with `seq` and `Last-Event-ID` resume verified (`events/resume-last-event-id.ndjson`)
- [ ] `tailscale serve` HTTPS from the phone: **blocked** (§4.7)
- [x] Decisions 5, 7, 13 answered from evidence (see 06 §F)

## 6. Cleanup done

City stopped (`gc supervisor stop`), leftover agent and MCP processes killed,
systemd unit removed, `~/.config/opencode/opencode.json` restored from the
backup, load back to normal. `dolt` 2.3.3 and `bd` 1.2.2 remain in
`~/.local/bin` (old `bd` kept as `bd-1.0.2.bak`).
