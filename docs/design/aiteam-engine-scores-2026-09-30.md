# AI Team engine evaluation — 2026-09-30

Phase B of [the build prompt](aiteam-build-prompt-2026-09-29.md). **Report only. No engine has been chosen, installed or connected.** Phase A remains an explicitly simulated, local fixture. Phase C waits for the owner's choice.

## Decision

Recommend **native OpenCode sessions plus a host-resident project service** as the direction to prototype, conditional on proving repository protection first. It reuses the app's session protocol and can keep lanes within one server. That process advantage is an architectural hypothesis, not a measured performance result. The service is substantial new work: project records, transactional scheduling, leases, recovery, budgets, authenticated commands and protected promotion. Calling it “small” would understate this scope.

**No candidate qualifies unchanged.** None demonstrates the required non-bypassable rule that only confirmed promotion can write `main`. Gas City's current phone deployment also fails the authenticated-listener rule. Choosing a direction does not waive these hard gates.

Gas City on a computer is the alternative if existing autonomous scheduling matters more than matching the session model. Advance and opencode-orchestrator may supply workflow ideas or components. OpenAgentsControl and opencode-spec primarily supply role/specification workflows; neither is a complete project engine.

## Method and score meaning

Sources were inspected on September 30, with exact identities below. `F` means a directly evidenced callable capability covers the item; `P` means useful mechanisms exist but the full requirement needs implementation or validation; `N` means absent from the current architecture or explicitly outside its scope; `U` means unestablished, not proven absent. Scores are conservative source-contract coverage, **not end-to-end passes or a performance ranking**. No live model runs were performed.

There are 19 mandatory items; E12 and E15 are the two should-haves. E21 counts mandatory OC1 support; optional OC2 compatibility is reported separately. The hypothetical host service receives no credit for unwritten code. “App fill” means future work, not behavior proven by a fixture.

## Rubric (§7)

| Candidate | M F/P/N/U (19); S F/P/N/U (2) | Phone lanes: processes, MB, cold start; chat 1/3 lanes | Durability E06; closed UI phone/host E07 | Lanes/placement E05/E08 | Branches/queue E13/E14 | Recovery/budgets E16/E17 | OC1/OC2; API | License; maturity | Fits as |
|---|---|---|---|---|---|---|---|---|---|
| Native, app-driven | 3/10/6/0; 0/1/1/0 | Unmeasured; child sessions share server, tool subprocesses still possible | Conversations persist; project loop stops with app; neither unattended phone nor host scheduler | Child-session primitive; dependency/capacity/migration service missing | Worktree/revert primitives; protection and queue missing | Session retries/usage only; project recovery/admission missing | OC1 API evidenced; OC2 app path exists but current execution unverified; HTTP/SSE | MIT; established session base, new project layer | **Not suitable unchanged**; foundation for both |
| Native + proposed host service | 3/10/6/0; 0/1/1/0 | Unmeasured; same hypothesis, service overhead unknown | Proposed durable records/host loop, not built; phone lifecycle still required | Proposed scheduler and placement, not built | Enforced git boundary, receipts, promotion to build | Transactional recovery and budgets to build | Same native APIs; new authenticated project API to build | MIT base; service has no production maturity | **Both**, conditional future target |
| Advance | 0/14/0/5; 0/2/0/0 | All unmeasured | JSON artifacts/locks; unattended restart behavior unproven | Sequential bounded subagents, per-change worktrees; no proven project lanes/moves | Worktree/gate mechanisms; guard is not promotion fence; no verified queue | Partial workflow recovery; budget admission unestablished | OC1 plugin; OC2 unverified; external MCP is read-only | Declares MIT, license file clarification needed; active 1.x | **Not suitable unchanged**; workflow component |
| opencode-orchestrator | 0/13/0/6; 0/0/0/2 | All unmeasured | Mission persistence and loop; crash atomicity/Android recovery unproven | Configurable workers; replaces active mission in same project; migration unestablished | No proven dev/main fence or durable per-repo queue | Iteration/stagnation controls; monetary admission unestablished | Documents OC1 ≥1.18.29 and OC2; app-facing project mutation API unproven | MIT; active 2.x, recent scope change | **Not suitable unchanged**; host mission-loop component |
| OpenAgentsControl | 0/12/2/5; 0/0/1/1 | All unmeasured | Context artifacts; approval-driven workflow, not unattended scheduler | TaskManager/BatchExecutor prompts; registered-server movement unproven | Prompt approval is not branch protection; queue unestablished | Stops on failure for approval; project budgets unestablished | OC1 usage documented; OC2 unverified; agent/command workflow, no separate project API | MIT; 0.x agent pack | **Not suitable** as complete engine |
| opencode-spec | 0/7/8/4; 0/0/2/0 | All unmeasured | Spec artifacts; explicitly no runtime execution loop | Outside runtime scope | Outside runtime scope | Outside runtime scope | Plugin commands/skills; no demonstrated project execution API; OC2 unverified | No license discovered; 0.2.0 | **Not suitable** as complete engine |
| Gas City computer/VPS | 3/15/1/0; 0/1/1/0 | No PC benchmark; remote workers avoid local phone worker allocation, not measured client cost | Persistent graph/reconcile; host autonomous; reboot acceptance still needed | Dependency pools/caps; ACP processes; active cross-server move incomplete | Worktree/refinery primitives; promotion fence missing; receipt contract partial | Recovery mechanisms; project budget admission incomplete | OC1 ACP path locally evidenced; OC2 unverified; HTTP/SSE, front compatibility audit needed | MIT; 1.4.2, current source newer | **Host engine**, conditional; not suitable unchanged |
| Gas City phone baseline | 2/15/2/0; 0/1/1/0 | Historical 563 MB/worker; 22 s–4 min start; process peak 33–37; paired 1/3-lane chat unknown | Beads persist; Android service/revival/charging acceptance incomplete | Same graph/pools, separate ACP processes | Existing refinery can write main; no promotion fence | Recovery partial; budget admission incomplete | OC1 ACP; OC2 unverified; unauthenticated loopback baseline | MIT; locally documented 1.4.1 | **Not suitable unchanged**; cost alone is not a ban |

## Complete E coverage

| E | Requirement | Native | + service today | Advance | Orchestrator | OAC | Spec | GC host | GC phone |
|---|---|---|---|---|---|---|---|---|---|
| 01 | Project/quick task creation | N | N | P | P | P | P | P | P |
| 02 | Editable versioned spec | N | N | P | U | P | P | P | P |
| 03 | Structured plan, affected-only replan | N | N | P | P | P | P | P | P |
| 04 | Role/model/fallback/server/branch | P | P | P | P | P | U | P | P |
| 05 | Dependency lanes, caps, memory | P | P | P | P | P | N | P | P |
| 06 | Durable cross-device project state | P | P | P | P | P | P | P | P |
| 07 | Advance without client UI | N | N | U | P | N | N | F | P |
| 08 | Multi-server moves | P | P | U | U | U | N | P | P |
| 09 | Refetchable progress | F | F | P | P | P | U | F | F |
| 10 | Task asks and steering | F | F | P | P | P | U | F | F |
| 11 | Read-only ranked check/fix/recheck | P | P | P | P | P | P | P | P |
| 12 S | Capped auto-fix | N | N | P | U | N | N | P | P |
| 13 | Protected dev/main worktrees | P | P | P | U | U | N | P | P |
| 14 | Checked queue, durable receipts | N | N | U | U | U | N | P | P |
| 15 S | Confirmed promotion/revert/PR | P | P | P | U | U | N | N | N |
| 16 | Recovery/backoff/stall/reconcile | P | P | P | P | N | N | P | P |
| 17 | Budget admission and usage | P | P | U | U | U | N | P | P |
| 18 | Complete actor timeline | P | P | P | P | P | U | P | P |
| 19 | Chat-first-word hold | N | N | U | U | U | N | N | N |
| 20 | Auth, no app provider keys, tailnet | P | P | P | P | P | P | P | N |
| 21 | In-app OC1; optional OC2 | F | F | P | P | P | P | P | P |

Native E21 credits its existing direct OC1 transport. Plugins and ACP are partial because their exact phone version path was not validated. Sharing OpenCode's transport does not prove their project-level E09/E10 behavior.

## Evidence and required fills

### Native sessions and a host service

Upstream [OpenCode v1.18.33](https://github.com/anomalyco/opencode/releases/tag/v1.18.33) is MIT. The [server API](https://opencode.ai/docs/server/) provides sessions, asynchronous prompts, status/messages/diffs, child sessions, abort and permission replies. These provide a sound adapter boundary; they do not provide an autonomous project scheduler.

The pinned [task tool](https://github.com/anomalyco/opencode/blob/v1.18.33/packages/opencode/src/tool/task.ts) executes child sessions inside the server. Role/model fallback, dependency ordering, capacity, lane memory and migration remain orchestration work. Tool and language-server children can still create processes.

The [agent defaults](https://opencode.ai/docs/agents/) ask permission for Plan-agent edits and commands; a checker must explicitly deny mutation or run in an isolated read-only view. The [worktree implementation](https://github.com/anomalyco/opencode/blob/v1.18.33/packages/opencode/src/worktree/index.ts) starts from current HEAD. It does not enforce `dev`, a queue or promotion. The [processor](https://github.com/anomalyco/opencode/blob/v1.18.33/packages/opencode/src/session/processor.ts) supplies retry/loop/usage mechanisms, not project-wide recovery or budget admission.

Server password authentication is available. Require it and restrict reachability to tailnet/loopback; retain provider credentials on the server. [Permission rules](https://opencode.ai/docs/permissions/) are useful controls, but selected shell-command patterns or prompt instructions do not establish an immutable `main` boundary. A service needs separately controlled repository promotion authority and adversarial checks before qualification.

### Plugins: exact projects, not interchangeable names

- **Advance:** [Sharper-Flow/Advance](https://github.com/Sharper-Flow/Advance), inspected `45fed37d55333d0dde9ab8fcf2b24f1b378e6781` (September 2), latest release [1.22.2](https://github.com/Sharper-Flow/Advance/releases/tag/v1.22.2). Current [README](https://github.com/Sharper-Flow/Advance/blob/45fed37d55333d0dde9ab8fcf2b24f1b378e6781/README.md) uses transactional JSON projections and file locks: **older indexed Temporal claims are stale**. Structured artifacts and gates are useful, but sequential subagents/per-change worktrees are not the required lane model. The external [MCP entry](https://github.com/Sharper-Flow/Advance/blob/45fed37d55333d0dde9ab8fcf2b24f1b378e6781/plugin/src/mcp-server/index.ts) is stdio/read-only; its richer internal mutation tools do not prove a remote app API. The [worktree guard](https://github.com/Sharper-Flow/Advance/blob/45fed37d55333d0dde9ab8fcf2b24f1b378e6781/plugin/src/tools/worktree-isolation-guard.ts) permits operation if git context detection fails. Package MIT declaration exists, but no standalone root license was found; resolve licensing before adoption.
- **opencode-orchestrator:** [agnusdei1207/opencode-orchestrator](https://github.com/agnusdei1207/opencode-orchestrator), `ee463a3a320779322694f4ae19108ddc4b61bb87`, [2.0.9](https://github.com/agnusdei1207/opencode-orchestrator/releases/tag/v2.0.9), September 28, MIT. [README](https://github.com/agnusdei1207/opencode-orchestrator/blob/ee463a3a320779322694f4ae19108ddc4b61bb87/README.md) requires Node ≥24.15 and documents OC1 ≥1.18.29/OC2, workers and mission replacement. [Loop state](https://github.com/agnusdei1207/opencode-orchestrator/blob/ee463a3a320779322694f4ae19108ddc4b61bb87/src/core/loop/mission-loop.ts) and [event ledger](https://github.com/agnusdei1207/opencode-orchestrator/blob/ee463a3a320779322694f4ae19108ddc4b61bb87/src/core/loop/mission-ledger.ts) show persistence but not complete crash-atomic project semantics. The [scope ADR](https://github.com/agnusdei1207/opencode-orchestrator/blob/ee463a3a320779322694f4ae19108ddc4b61bb87/docs/adr/0021-minimal-mission-plugin.md) contains proposals, not shipped APIs. Its optional [shell listener](https://github.com/agnusdei1207/opencode-orchestrator/blob/ee463a3a320779322694f4ae19108ddc4b61bb87/crates/orchestrator-cli/src/shell_listener.rs) is unauthenticated TCP: exclude it. The core plugin does not inherently require that listener.
- **OpenAgentsControl:** [darrenhinde/OpenAgentsControl](https://github.com/darrenhinde/OpenAgentsControl), `37ca233fa5597a5abb90cba73165deafffe0344f` (July 14), latest [0.7.1](https://github.com/darrenhinde/OpenAgentsControl/releases/tag/v0.7.1), MIT. Current [OpenCoder definition](https://github.com/darrenhinde/OpenAgentsControl/blob/37ca233fa5597a5abb90cba73165deafffe0344f/.opencode/agent/core/opencoder.md) includes TaskManager/BatchExecutor delegation; saying it has no parallel support would be inaccurate. Approval and stop-on-failure instructions are workflow policy, not unattended recovery or enforced repository isolation.
- **opencode-spec:** [devcxl/opencode-spec](https://github.com/devcxl/opencode-spec), `b78f07fca299e3bc07e03fb4b65514bc4e77096a`, [0.2.0](https://github.com/devcxl/opencode-spec/releases/tag/v0.2.0), August 11. [Architecture](https://github.com/devcxl/opencode-spec/blob/b78f07fca299e3bc07e03fb4b65514bc4e77096a/docs/en/architecture.md) explicitly excludes runtime execution; it injects commands and skills. [Package metadata](https://github.com/devcxl/opencode-spec/blob/b78f07fca299e3bc07e03fb4b65514bc4e77096a/package.json) does not establish a license or tested OC2 compatibility. Do not substitute similarly named spec/iteration projects.

All four require additional project API, budget, promotion and lifecycle work. App code cannot supply unattended host scheduling while the app is closed.

### Gas City: host and current phone baseline

[Gas City](https://github.com/gastownhall/gascity) is MIT, latest released [1.4.2](https://github.com/gastownhall/gascity/releases/tag/v1.4.2) (September 18); inspected current source `f5cf59ad119967958537f0ce1fd459108a94a23b` (September 29). The phone evidence names 1.4.1. Version 1.4.2 includes Beads compatibility/migration changes; these versions are not interchangeable.

Its [architecture](https://github.com/gastownhall/gascity/blob/f5cf59ad119967958537f0ce1fd459108a94a23b/docs/getting-started/how-gas-city-works.md) provides autonomous graph scheduling and persisted work. The [formula guide](https://github.com/gastownhall/gascity/blob/f5cf59ad119967958537f0ce1fd459108a94a23b/docs/guides/understanding-formulas.md) includes routing/check/retry/drain, but notes a parent/container dependency gap. Project hierarchy and affected-only replan require explicit mapping.

The [API](https://github.com/gastownhall/gascity/blob/f5cf59ad119967958537f0ce1fd459108a94a23b/docs/reference/api.md) is HTTP/SSE, not merely slash commands. `X-GC-Request` is anti-CSRF, not identity. New [configuration](https://github.com/gastownhall/gascity/blob/f5cf59ad119967958537f0ce1fd459108a94a23b/docs/reference/config.md) adds `read_auth_verify_key` for typed city routes, while supervisor feeds/dashboard remain outside the gate. Whole-listener authentication remains necessary; the older [hardening runbook](https://github.com/gastownhall/gascity/blob/f5cf59ad119967958537f0ce1fd459108a94a23b/docs/runbooks/remote-hardened-city.md) is not the entire current auth picture.

Local [host front](../../tool/host/cp_front/README.md) implements Tailscale identity allowlists and disk/fsync idempotency receipts. Its implementation forwards `X-GC-Request`; signed-grant minting was not found. The app [HTTP transport](../../lib/orchestration/client/http.dart) strips Authorization/Cookie, so compatibility with the newer grant mode requires proof. Neither this front nor the [upstream refinery example](https://github.com/gastownhall/gascity/blob/f5cf59ad119967958537f0ce1fd459108a94a23b/examples/lifecycle/packs/lifecycle/agents/refinery/prompt.template.md) implements the requested promotion-only fence: default merge targeting can reach main. Phone direct refinery writes bypass the app front altogether.

Required fills include immutable spec/project records, exact role/model/server mapping, ranked read-only verification, promotion authority, complete per-item receipts, budget admission, chat-first hold and Android lifecycle acceptance.

## Measurements and honest limits

Attempted on this x86_64 PC, September 30:

| Check | Result |
|---|---|
| PATH `opencode --version` | `/home/owner/.bun/bin/opencode`; fails because postinstall binary was not installed; package metadata 1.18.23 |
| PATH `opencode2 --version` | Same missing-postinstall-binary failure; package metadata 0.0.0-beta-19242 |
| Legacy `~/.opencode/bin/opencode --version` | 1.0.193; identified only, not substituted for the current candidate |
| `adb devices` | adb unavailable in this execution environment |

No benchmark server was launched, no dependency was installed, and no model/provider credential was used. Consequently current PC idle RSS, lane RSS/processes, cold start, paired 1/3-lane chat latency, battery/heat and OS-pause recovery are **unknown**. The build's simulated cost and timing are not measurements.

Historical phone numbers below come from [September 29 native analysis, §4](aiteam-native-analysis-2026-09-29.md), not a new controlled run:

| Observation | Historical Gas City phone result |
|---|---|
| Added idle resident components | 79 + 96 + 28 MB |
| Worker / refinery resident memory | 563 MB each |
| Observed child-process peak | 33–37 total; not measured processes per lane |
| Tick / ACP cold start | 5–8 s / 22 s–4 min |
| Team-on “Hello” | Approximately 2 minutes |
| Component download / installed | 44 MB / about 125 MB, excluding working data |

The separate Termux “Hello” took 4 s, but the runs/environments differ: **do not derive a controlled ratio**. Expected post-tuning 40–70 s and native “tens of MB” are estimates, not observations. Resource cost informs the user's choice; it does not ban phone execution.

## Owner decision and next qualification slice

Choose a direction before Phase C: recommended native + host service, or Gas City host adaptation, or another named candidate. No real engine wiring is included in this branch.

The first chosen-engine slice should prove authenticated command access without app provider keys and an enforced repository boundary where direct, indirect and tool-mediated main writes fail, while a confirmed expected-SHA promotion succeeds with a durable receipt. Then prove one task from spec through check/dev merge/restart recovery. Only after that run paired 1/3-lane measurements, chat-first admission, budget caps, app-closed progression and phone OS-pause checks using the same pinned versions. No design should be credited for these until observed.
