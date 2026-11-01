# Role cycle: best practices from multi-agent "AI team" projects (2026-11-01)

Research input for `../1-use-cases.md`, `../2-architecture.md` and `../3-ui.md`.
I read the repos read-only with `gh` and checked papers, docs and posts on the
web. Everything below is cited, and numbers are the sources' own unless marked
"(calc)". I also reused the earlier Paperclip source review in
`oc_app/docs/research/paperclip-2026-10-12.md`.

**Bottom line:** the plan's spine is right, and the evidence backs it: artifacts
between roles, a manager made of rules, two human approvals, and a reviewer
that blocks merges. The weak spots are where verification can be fooled and
where loops end only by counting. Today the reviewer trusts evidence the
developer wrote. Criteria are plain text, not checks. A fix round that changes
nothing is still counted as progress. Nothing checks `dev` after parallel
merges. Small jobs pay for the full cycle.

---

## 1. Comparison

Repo state as of 2026-11-01 (`gh api repos/...`): MetaGPT 71k stars (last
push 2026-01); ChatDev 35k (2.0 "DevAll" released 2026-01-07); BMAD 55k (v6.12
stable, v7 alpha); CrewAI 60k; AutoGen 62k, **archived**, with Microsoft Agent
Framework (MAF, 14k) as its successor; OpenHands 92k; OpenAI Agents SDK 30k;
Gas Town 19k (now `gastownhall/gastown`) and Gas City 1.5k; Paperclip 109k;
GitHub Spec Kit 144k; oh-my-openagent (OmO, formerly oh-my-opencode) 71k.

| Project | Roles | Handoff artifacts | Process | Verification | Human in the loop | Cost and loop controls | Known failures and criticism | What users praise |
|---|---|---|---|---|---|---|---|---|
| **MetaGPT** | Product manager, architect, project manager, engineer, QA | PRD (goals, user stories, requirement pool), system design (file list, interfaces, sequence diagram), task list (one task per file), code, tests | SOP "assembly line". Agents publish structured documents to a shared pool and subscribe by role. An agent starts only when its inputs exist | Executable feedback: the engineer runs unit tests and retries **up to 3** times. QA writes tests | Little; it runs end to end | Retry cap 3; about $1–1.4 per small project | MAST: fewer design and alignment failures than ChatDev but **1.56× more verification failures**. Toy-scale apps | Structured documents instead of dialogue; the ablation shows each role adds value |
| **ChatDev 1.0 / 2.0** | CEO, CPO, CTO, programmer, reviewer, tester (1.0). 2.0 is a zero-code platform for building your own teams | Each phase's solution (design → code → review → test) | "Chat chain": instructor/assistant pairs per subtask, in a waterfall. 2.0 adds configurable graphs and an RL "puppeteer" orchestrator (NeurIPS 2025) | Static code review plus dynamic system test | Optional "Human" mode, where the person plays the reviewer | A subtask ends after **10 rounds or 2 unchanged code edits**; consensus marker `<SOLUTION>` | Its own paper says agent ability is "overestimated", the code has "low information density", and it is "more suitable for prototype systems". MAST measured high failure rates | Cheap, fast prototypes; the dehallucination trick (the assistant asks the instructor for specifics) |
| **BMAD-Method** | Analyst, PM, architect, UX, scrum master, dev, QA/test architect (v4–v6). v7 centres a PO ("Sofia") plus `bmad-build` units | Brief → PRD → architecture → epics sharded into **story files**, each carrying full context, a task list, a Dev Agent Record and a File List. QA gate YAML (PASS / CONCERNS / FAIL / WAIVED). v7: `tickets.toml` | Sequential SOP with **scale-adaptive paths**: trivial → one session → epic → project → organisation | The dev marks tasks done only after tests pass. A QA gate and traceability matrix. An epic closes only after combined verification plus a retrospective | The person picks the path and story checkpoints. "Early foundational stories get human attention before automation repeats their patterns." In organisations the PRD is approved | Process sizing. `bmad-build-auto` runs only "after important implementation decisions are stable" | The docs call the full method "likely overkill" for small changes. PRD plus architecture can take tens of thousands of tokens; slow on small tasks | Decisions stay explicit and durable; context carries forward; story files make the dev agent's context self-contained |
| **CrewAI** | Free-form agents with role, goal and backstory | Task `expected_output`, optional Pydantic/JSON output, and `context` = earlier task outputs | Sequential, or hierarchical with a required `manager_llm` / `manager_agent` that plans, delegates and validates. Flows give deterministic control | Task **guardrails** (function or LLM), `guardrail_max_retries` default **3** | `human_input` per task (off by default) | Guardrail retries, `max_iter`, rpm limits | Hierarchical mode widely reported broken: the manager can't delegate, runs tasks in sequence anyway, reasons wrongly, high latency (GitHub #4783, community threads, TDS article) | Quick to start; readable role definitions; structured task outputs |
| **AutoGen → MAF** | Any agents; a planner in `SelectorGroupChat`. Magentic-One: Orchestrator + WebSurfer, FileSurfer, Coder, Terminal | A shared group-chat transcript. Magentic-One: **Task Ledger** (facts, guesses, plan) and **Progress Ledger** | Group chat with model-based speaker selection (`selector_func` / `candidate_func` overrides). MAF adds sequential, concurrent, handoff, group chat and Magentic | None built in. Magentic-One's ledger asks every step: done? looping? progress? | `UserProxyAgent`; MAF approval-required tools pause the workflow | `MaxMessageTermination(25)`, `TextMentionTermination`. Magentic-One **stall counter > 2 → replan**; 25-min task budget | AutoGen is archived. Selector agents hallucinated figures in the docs' own example. Prompt tokens grow every turn. Magentic-One: persistent inefficient actions, and agents tried to recruit humans (emails, posts) | Flexible research platform; the ledger idea |
| **OpenHands** | CodeActAgent, which can delegate to micro-agents (browsing etc.) | Delegation action with a task string; results return to the parent | Single agent loop plus delegation | Runs code in a sandbox; the person reviews | The person watches and steers in the UI | **StuckDetector**: same action→observation ×4, same action→error ×3, monologue ×3, ping-pong ×6 cycles, context-window error loops; iteration and budget limits | Long runs drift; costs vary | Strong SWE-bench results; the stuck detector is concrete and deterministic |
| **Claude Code subagents / agent teams** | Subagents: specialised workers (Explore is read-only, Plan). Teams: lead plus teammates | Subagents return a summary. Teams: a **shared task list** (pending / in progress / completed, dependencies, file-lock claiming) plus mailboxes | Orchestrator-workers. Teams self-claim tasks; the lead synthesises | Hooks: `TaskCompleted` / `TeammateIdle` exit 2 = refuse completion and send feedback. A teammate's plan needs approval before it builds | Permission prompts bubble up to the lead. Messages from another agent can't grant consent | "Start with **3–5 teammates**", "5–6 tasks per teammate". Teams cost "significantly more tokens" | Experimental: task status lags and blocks dependants; the lead stops early or does the work itself; no resume. "Two teammates editing the same file leads to overwrites" | Context isolation; per-role tools and `model` (e.g. haiku for cheap work); a debate structure for hypotheses |
| **OpenAI Agents SDK** | Agents with instructions, tools and `handoffs` | A handoff is a tool (`transfer_to_x`) with a typed `input_type`; `input_filter` trims the history passed on | Decentralised handoffs, or the "manager" pattern (agents as tools) | Input/output/tool guardrails | The guide's two triggers: **exceeding failure thresholds** and **high-risk actions** | `max_turns`; built-in tracing with `handoff_span` | By default the receiver sees the **full history** (context bloat). Input guardrails apply only to the first agent | Small API surface; tracing; "maximize a single agent's capabilities first" |
| **Gas Town / Gas City** | Mayor (coordinator, no code), Polecats (workers), Witness (stuck detection per rig), Refinery (merge queue), Deacon (patrol), Dogs. Gas City: an SDK of these primitives | **Beads** (git-backed issues), convoys, hooks, formulas/molecules | Hierarchical, long-lived tmux agents; escalation by severity | **Refinery: Bors-style bisecting merge queue running verification gates**; failed MRs are isolated | `--human` convoys; escalations through Deacon → Mayor → Overseer | Scheduler `max_polecats`; stall detection on "mountain" convoys | Yegge: "100% vibe coded"; "I only ever wound up using it to build itself"; "fell apart at the seams with Opus 4.7". Reported $2–5k/month (secondhand) | Durable work state across restarts; merge-queue discipline |
| **Paperclip** | Company org chart: CEO, CTO, engineers… with any adapter per agent | Tickets with goal ancestry, documents, work products, approvals | Heartbeat-driven agents, delegation up and down the org chart | Configured review/approval stages before completion; verify from diffs, screenshots and tests | Board approves strategy and hires; pause / reassign / terminate | Budgets per company / agent / project: **warn at 80 %, hard stop and auto-pause at 100 %**, per-run reservations, blocked while spend is uncertain | Org-chart-first UX is heavy; server-shaped defaults (20 concurrent runs per agent); telemetry on by default (see our Paperclip review) | "Runaway loops waste hundreds of dollars" fixed by budgets; durable sessions; one dashboard |
| **GitHub Spec Kit** (2026's most-starred SDD kit) | Not personas: phases run by your agent | `constitution.md` → `spec.md` → `plan.md` → `tasks.md` (`[P]` = parallel-safe: different files, no dependencies) → `/converge` | Sequential, spec-driven | `/analyze` (read-only consistency check across artifacts); `/converge` checks the build against the spec and **creates remediation tasks** | Clarify: **≤5 questions in total, one at a time, each with a recommended answer and a "why it matters" line**; "yes" accepts the recommendation | At most 3 `[NEEDS CLARIFICATION]` markers per spec | Ceremony for tiny changes (it ships bug/assess extensions to cut this down) | Testable, measurable, technology-free success criteria; the spec as the source of truth |
| **oh-my-openagent (OmO)** | A main agent plus read-only explore, librarian, plan-consultant and **plan-reviewer (one-shot)** agents, and category workers (quick, deep, architect, visual…) | `task` delegation; a handoff document for a fresh session | Orchestrator-workers. **Category workers can't re-delegate** ("prevents infinite delegation loops") | "Proves each step", checks on the real surface | Low by design ("taking the wheel back = the system failed") | **Model chain per role and category** with fallbacks; a cheap "kibitzer" model watches the expensive one. Goal continuation has **no iteration cap** | Aggressive autonomy; token-hungry "ultrawork" | Mixing cheap and frontier models; finish-the-job persistence |

Sources: MetaGPT [paper](https://arxiv.org/html/2308.00352), [repo](https://github.com/FoundationAgents/MetaGPT) ·
ChatDev [paper](https://arxiv.org/html/2307.07924), [repo](https://github.com/OpenBMB/ChatDev), [puppeteer](https://arxiv.org/abs/2505.19591) ·
BMAD [repo](https://github.com/bmad-code-org/BMAD-METHOD), [planning paths](https://docs.bmad-method.org/plan/choose-a-planning-path/), [QA gate task (v4 mirror)](https://cdn.jsdelivr.net/npm/@ptnglobal/bmad-method@4.43.1/bmad-core/tasks/qa-gate.md), [critique](https://codemyspec.com/blog/bmad-method-explained) ·
CrewAI [processes](https://docs.crewai.com/en/concepts/processes), [tasks](https://docs.crewai.com/en/concepts/tasks), [issue #4783](https://github.com/crewAIInc/crewAI/issues/4783), [community thread](https://community.crewai.com/t/does-hierarchical-process-even-work-your-experience-is-highly-appreciated/2690), [TDS](https://towardsdatascience.com/why-crewais-manager-worker-architecture-fails-and-how-to-fix-it/) ·
AutoGen [repo (archived)](https://github.com/microsoft/autogen), [SelectorGroupChat](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/selector-group-chat.html), [Magentic-One](https://arxiv.org/html/2411.04468), [MAF orchestrations](https://learn.microsoft.com/en-us/agent-framework/user-guide/workflows/orchestrations/overview) ·
OpenHands [repo](https://github.com/OpenHands/OpenHands), [stuck detector](https://docs.openhands.dev/sdk/guides/agent-stuck-detector) ·
Claude Code [agent teams](https://code.claude.com/docs/en/agent-teams), [subagents](https://code.claude.com/docs/en/sub-agents) ·
OpenAI [handoffs](https://openai.github.io/openai-agents-python/handoffs/), [tracing](https://openai.github.io/openai-agents-python/tracing/), [practical guide (PDF)](https://cdn.openai.com/business-guides-and-resources/a-practical-guide-to-building-agents.pdf) ·
Gas Town [repo](https://github.com/gastownhall/gastown), [Gas City](https://github.com/gastownhall/gascity), [Yegge via Simon Willison](https://simonwillison.net/2026/Aug/4/), [cost (secondhand)](https://news.hada.io/topic?id=26091) ·
Paperclip [repo](https://github.com/paperclipai/paperclip), [costs](https://docs.paperclip.ing/guides/day-to-day/costs/) ·
Spec Kit [repo](https://github.com/github/spec-kit), [clarify template](https://github.com/github/spec-kit/blob/main/templates/commands/clarify.md) ·
OmO [repo](https://github.com/code-yeongyu/oh-my-openagent), [features](https://github.com/code-yeongyu/oh-my-openagent/blob/main/docs/reference/features.md).

Cross-cutting studies used below:
- [MAST, "Why do multi-agent LLM systems fail?"](https://arxiv.org/html/2503.13657): 1,642 traces from 7 systems, failure rates of 41–86.7 %.
- [Google, "Towards a science of scaling agent systems"](https://research.google/blog/towards-a-science-of-scaling-agent-systems-when-and-why-agent-systems-work/): 180 configurations.
- [Anthropic multi-agent research system](https://www.anthropic.com/engineering/multi-agent-research-system).
- [Anthropic long-running harnesses](https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents).
- [Anthropic C compiler with 16 agents](https://www.anthropic.com/engineering/building-c-compiler).
- [Cognition, "Don't build multi-agents"](https://cognition.com/blog/dont-build-multi-agents).
- [Huang et al., self-correction](https://arxiv.org/abs/2310.01798).
- [Multi-turn review, "More rounds, more noise"](https://arxiv.org/abs/2603.16244).
- [Self-preference bias](https://arxiv.org/abs/2410.21819).
- [Persona prompts](https://arxiv.org/abs/2311.10054).
- [Aider architect/editor](https://aider.chat/2024/09/26/architect.html).
- [AgentCoder](https://arxiv.org/pdf/2312.13010).

A caution on benchmarks: MetaGPT and ChatDev each report beating the other.
MetaGPT reports executability of 3.75 against ChatDev's 2.25; ChatDev reports
0.88 against MetaGPT's 0.41. Self-reported team benchmarks are weak evidence.
MAST's independent annotation is the stronger source.

---

## 2. Best practices, with evidence

**BP1. Hand off structured artifacts, never free chat.**
- MetaGPT: agents "communicate through documents and diagrams (structured outputs) rather than dialogue". Its role ablation went from 1 to 4 roles and moved executability from 1.0 to 4.0, with human revisions falling from 10 to 2.5.
- BMAD story files, Spec Kit's spec → plan → tasks, and Anthropic's "save subagent outputs to external storage and pass lightweight references back" all do the same.
- Anthropic's harness chose **JSON over Markdown** for the feature list because "models are less likely to inappropriately change or overwrite JSON".
- *Plan status:* already the plan's rule (arch §3, "Handoffs are artifacts").

**BP2. Give each role a narrow, validated output schema, and reject bad output deterministically.**
- MetaGPT's fixed document templates, CrewAI's `output_pydantic` and guardrails, OpenAI's typed `input_type`, BMAD's gate YAML, and Spec Kit's task line format (`T012 [P] [US1] … in path`) all do this.
- MAST: "disobey task specification" causes 11.8 % of failures. Improving role specifications alone gave ChatDev **+9.4 %** success.
- *Plan status:* in place (arch §2 JSON schemas, `validate_plan`).

**BP3. Make acceptance criteria executable, and keep them out of the builder's reach.**
- The C compiler post: "the task verifier [must be] nearly perfect, otherwise Claude will solve the wrong problem".
- Anthropic's harness gives each feature test steps and `passes:false`, and says "It is unacceptable to remove or edit tests".
- MetaGPT's executable feedback added **+4.2 % (HumanEval) and +5.4 % (MBPP)**.
- Spec Kit requires criteria that are "testable", "measurable" and "technology-agnostic".
- *Plan status:* **gap.** Criteria are prose, and the reviewer checks the developer's own report.

**BP4. Use an independent verifier, at more than one level.**
- MAST: verification failures are the largest category (no or incomplete verification 8.2 %, incorrect verification 9.1 %, premature termination 6.2 %). "Multi-level verification is needed… sole reliance on final-stage, low-level checks is inadequate". Adding a high-level verification step gave ChatDev **+15.6 %**.
- Self-checking doesn't work: models "struggle to self-correct… performance even degrades" (Huang et al.).
- AgentCoder: tests written independently from the requirements beat the programmer alone (61.0 → 64.0 % in the ablation; 77.4 % on HumanEval-ET for the full system).
- Other projects that use one: Spec Kit `/converge` (spec level), BMAD epic closure (epic level), and Gas Town's Refinery gates (merge level).
- *Plan status:* partly in place. There are per-task checks and a PO milestone check, but no check of `dev` after merges.

**BP5. Bind the reviewer to blocking rules, and re-check in one fresh pass.**
- Multi-turn review raised recall by only 0.08. It produced 62 % more false positives, and precision fell from 0.30 to 0.20 (F1 0.376 for single-pass against 0.303). Later rounds "invent findings once the real errors are exhausted".
- BMAD separates **FAIL** from **CONCERNS** (non-blocking, tracked) and **WAIVED**.
- Judges favour text that reads like their own output (self-preference; GPT-4 strongest). That argues for a reviewer from a **different model family** from the developer.
- *Plan status:* **gap.** "Any finding blocks the merge" invites nitpick loops.

**BP6. Bound every loop, and detect stalls by rule, not just by counting.**
- ChatDev: 10 rounds or **2 unchanged edits**. MetaGPT: 3 retries. CrewAI: 3 guardrail retries. AutoGen: `MaxMessageTermination(25)`.
- Magentic-One: a stall counter that triggers a replan when it exceeds 2.
- OpenHands: same action→observation ×4, same error ×3, ping-pong ×6.
- MAST: step repetition (**15.7 %**) and "unaware of termination conditions" (**12.4 %**) are two of the three most common failure modes.
- OmO's uncapped goal continuation is the counter-example.
- *Plan status:* round counts are in place (`maxFixRounds`); no-progress detection is a **gap**.

**BP7. Make the manager rules, and call a model only to escalate.**
- CrewAI's LLM manager is the most-reported failure in the field (it doesn't delegate, or runs tasks in sequence anyway).
- Google: on sequential tasks "every multi-agent variant… degraded performance by **39–70 %**". Centralised control contains error amplification at **4.4×**, against 17.2× for independent agents.
- OpenAI: "maximize a single agent's capabilities first".
- Magentic-One shows the right shape for escalation: a ledger of facts, what was tried and why it stalled, then a replan.
- *Plan status:* in place (the manager is not an agent). The escalation input needs a ledger.

**BP8. Ask the person at the spec and at shipping, and otherwise only on failure thresholds or high-risk actions.**
- OpenAI's two intervention triggers are "exceeding failure thresholds" and "high-risk actions".
- BMAD and Paperclip approve the PRD or strategy. Spec Kit asks at most 5 questions, one at a time, each with a recommendation and a "why it matters" line.
- Claude Code auto-approves teammate plans by default, so a plan gate is not essential.
- *Plan status:* in place (A1/A2, D1–D4). Tune the question cap.

**BP9. Choose models per role: strong where few tokens carry high leverage, cheaper where most tokens go.**
- Aider architect/editor pairs scored up to **85.0 %** against 79.7 % solo. o1-mini went from 61.1 % to 71.4 % with DeepSeek as editor.
- Anthropic: an Opus lead with Sonnet subagents beat Opus alone by **90.2 %**.
- OmO ships a model chain per role and category with fallbacks. Claude Code subagents take a per-agent `model:`. Paperclip and Gas City take an adapter per agent.
- *Plan status:* only the optional Roles page. Recommend defaults instead of a picker.

**BP10. Isolate context per role, but write shared decisions down.**
- The isolation side: Claude Code subagents and teammates get their own context, and the lead's history does not carry over. OpenAI's `input_filter` exists because the default full-history handoff bloats context. AutoGen prompt tokens grow every turn.
- The tension: "Actions carry implicit decisions, and conflicting decisions carry bad results" (Cognition, with parallel subagents building a mismatched game). The fix is to make those decisions explicit artifacts every developer receives.
- *Plan status:* isolation is in place. Shared decisions are a **gap**.

**BP11. Run in parallel only on disjoint files, and default to sequential.**
- Spec Kit's `[P]` flag means "different files, no dependencies".
- Claude Code: "Two teammates editing the same file leads to overwrites". "For sequential tasks… a single session… [is] more effective."
- The C compiler post: when every agent worked on one big task, they "hit the same bug"; separating the work fixed it.
- Google: sequential tasks degrade by 39–70 % under multi-agent setups.
- *Plan status:* in place (`files[]` overlap rule).

**BP12. Gate merges on an integration check.**
- C compiler: late on, Claude "started to frequently break existing functionality each time it implemented a new feature", so a CI gate was added.
- Gas Town's Refinery runs a Bors-style bisecting merge queue with verification gates.
- *Plan status:* **gap.** Nothing checks `dev` after parallel merges.

**BP13. Cap cost with reservations and an early warning.**
- Agents use about 4× the tokens of chat, and multi-agent systems about **15×** (Anthropic).
- Paperclip warns at 80 % and hard-stops at 100 %. It holds a per-run reservation and blocks work while spend is uncertain.
- Claude Code teams: "token costs scale linearly"; "start with 3–5 teammates".
- *Plan status:* pause on budget is in place. Reservation and the 80 % warning are gaps.

**BP14. Make every handoff observable: who, which artifact version, which model.**
- OpenAI traces each handoff as a `handoff_span`. Anthropic traced "decision and interaction patterns" without reading contents.
- Paperclip records each run's adapter, model and inputs, which our earlier review called "per-job execution snapshots".
- *Plan status:* role voices are planned (S1), but they don't record artifact versions or the model.

**BP15. Size the process to the work.**
- BMAD v7 has trivial, one-session, epic and project paths, and calls the full method "likely overkill" for small changes.
- Anthropic's scaling rules: simple work gets 1 agent and 3–10 tool calls; comparisons get 2–4 subagents; complex work gets 10+. Its early system spawned "50 subagents for simple queries".
- Claude Code: "Three focused teammates often outperform five scattered ones."
- *Plan status:* **gap.** Every task pays for the full cycle.

---

## 3. Anti-patterns and failure modes to avoid

| Anti-pattern | Evidence | Guard |
|---|---|---|
| **Role-play chatter** (agents "meeting" in dialogue) | ChatDev's chat chain gives "low information density" code; personas in system prompts "do not improve performance" on objective tasks (162 roles) | Roles are prompts plus a schema; the role "voice" in the UI is rendered from artifacts |
| **LLM manager in the loop** | CrewAI hierarchical failures; Google's 39–70 % degradation on sequential tasks | Engine rules; a model only on escalation, once |
| **Unbounded loops and retries** | MAST step repetition 15.7 %; OmO's goal continuation with no cap; Paperclip's "runaway loops waste hundreds of dollars" | Round caps **and** no-progress detection |
| **Rubber-stamp reviewer** | MAST incorrect verification 9.1 %; self-preference bias; a reviewer reading the builder's own evidence; Anthropic's "marking features done without end-to-end testing" | The reviewer reads check output the engine produced; different model family; can't approve with an unmet criterion |
| **Nitpick reviewer** (blocking forever) | Multi-turn review: +62 % false positives in later rounds | Blocking findings must cite a criterion, failing check, planned risk or out-of-plan file; everything else is a non-blocking note |
| **Premature "done" or declaring victory** | MAST premature termination 6.2 %; Anthropic: a later agent "considered the project finished" | Done means every criterion maps to a passing check; the engine decides, not the agent |
| **Context bloat** | OpenAI's default full-history handoffs; AutoGen tokens growing per turn; Anthropic: write outputs to files and pass references | Each role gets only its input artifacts, never another role's transcript |
| **Parallel conflicting decisions** | Cognition's mismatched Flappy Bird; Claude Code same-file overwrites | Shared `decisions` in the plan go to every developer; disjoint `files[]` |
| **Ceremony for small jobs** | BMAD "overkill"; tens of thousands of tokens for PRD plus architecture | A small path (see R5) |
| **Building the orchestrator instead of the product** | Gas Town "only ever… used to build itself", then "fell apart" on a model change | Keep the cycle a thin prompt-and-artifact chain on the existing engine (arch principle) |
| **Agents acting outside scope** | Magentic-One agents tried to recruit humans by email and posts; Claude Code: agent messages can't grant consent | Least privilege; read-only roles stay read-only; agent messages never count as approval |
| **Coordination state owned by agents** | Claude Code teams: "task status can lag… blocks dependent tasks"; the lead "stops early" | The engine owns state; agents only produce artifacts |
| **Too many agents** | Anthropic: 50 subagents for simple queries; Claude Code: 3–5 teammates | Lanes (phone 1); the architect splits into few tasks |

---

## 4. Recommendations for our plan

Constraints kept: phone-first, "simplify and natural", no new screens or bars,
the manager is not a model, and the person approves only the spec and main.

### Change

**R1. Make "done when" lines checkable** (BP3, BP4).
*Docs:* `2-architecture.md` §2 (PO and architect rows), §6 ("Developer fakes evidence"); `1-use-cases.md` "Roles" table.
1. The PO's criterion becomes `{id, text, how: test | run | look}`. Plain words stay plain for the person: "look" criteria become a two-line "Try this" list on the ship card.
2. The architect binds each `test`/`run` criterion to a **check** (an existing or new test file, or a command) in the plan.
3. The developer may add the check but not edit or delete checks it didn't create. The engine compares the check files' diff against the plan, the same mechanism as `extraFiles`.
4. The **engine** runs the bound checks deterministically after the developer commits, and stores the output. The reviewer reads that output plus the diff, not only `.aiteam-verification/<task>.md`.
5. If a check can't run on the phone, the reviewer reads the evidence report as today and marks the criterion "checked by reading". The person sees that distinction in Details.

**R2. Give the reviewer blocking rules and a fresh re-check** (BP5).
*Docs:* `1-use-cases.md` "Roles" (reviewer row) and "When the cycle repeats"; `2-architecture.md` §2, §3.
1. Replace "any finding blocks the merge" with: a finding blocks only if it cites an unmet criterion id, a failing check, an architect risk, or out-of-plan files. Anything else is a **note**: shown, never blocking (BMAD's CONCERNS).
2. Each re-check after a fix is a **new single-pass session** that sees the criteria, the new diff and the check output. It does not see the previous review conversation.
3. Default the reviewer to a **different model family** from the developer when the server has one. This is an engine default, not a picker.

**R3. Detect stalls by rule in the fix loop, and give the re-split a ledger** (BP6, BP7).
*Docs:* `2-architecture.md` §3 (task state machine), §5, §6 ("Endless fix loop").
1. A fix round counts as **stalled** if it produced no diff, or if the same criterion id fails for the same reason twice (finding hash). A stalled round skips straight to `replanning`.
2. Add a repeat-error rule: the same tool error 3× in a developer session ends that attempt (OpenHands thresholds).
3. The architect's re-split input is a short ledger: the task and criteria, what each attempt changed (file list plus a summary of their last replies), the reviewer's blocking findings, and check output. That is Magentic-One's "facts / tried / why stuck", so the re-split isn't a blind re-plan.

**R4. Add an integration gate before the ship card, and let the milestone check request one fix** (BP4, BP12).
*Docs:* `2-architecture.md` §3 (`running → ownerCheck`), S3 and S4 in §8; `3-ui.md` concept 4.
1. When a milestone's tasks are all merged, the engine runs the project's check command (from AGENTS.md/README, or the architect's plan) on `dev`. On failure it bisects by merge order, at most once, and sends the culprit task back as a fix round.
2. If the PO's milestone check finds a spec criterion that no task covers or that failed, it creates **one** remediation task (Spec Kit `/converge`) instead of only reporting it. The ship card's primary action then reads **Fix it first**, and **Put … on main anyway** becomes secondary. This is bounded to once per milestone, then the person decides.

**R5. Right-size the cycle and the questions** (BP8, BP15).
*Docs:* `1-use-cases.md` A and C and the "Roles" PO row; `2-architecture.md` §5 (PO questions, architect); `3-ui.md` concept 1.
1. Cap PO questions at **3 in total**, down from 3 per round × 2 rounds = 6. Show them **one card at a time**, each with a one-line "why it matters" and a recommended answer, plus one tap **Use your picks for the rest**. That is Spec Kit's pattern, and it is natural on a phone.
2. Add a **small path**: if the PO's spec has one milestone with ≤3 criteria, the architect returns approach plus files for a **single task**, with no split and no parallel lanes. Sara's "CSV export" and most phone requests take this path. The cycle still reads as a team; it just has fewer turns.

### Add

**R6. Shared decisions for every developer** (BP10).
*Docs:* `2-architecture.md` §2, architect output.
- Add `decisions[]` to the plan for choices already made, not only open ones: names, interfaces, the colour, the data shape.
- Every developer gets the whole spec, approach and decisions, plus sibling task titles and their files. This counters Cognition's "implicit decisions" failure.

**R7. Per-role model defaults, without a picker** (BP9).
*Docs:* `1-use-cases.md` "What we will NOT do" (keep "no picker in the main flow"); `2-architecture.md` §1, §2.
- The engine picks the **strongest configured model for PO, architect and reviewer**. These are a few short turns but carry high leverage.
- Developers get the default model. The existing `fallbackModel` covers outages.

**R8. Make the reviewer the first non-OC2 role** (BP5, BP9).
*Docs:* `2-architecture.md` §1 (Paseo row) and S6.
- When roles first move beyond OC2, start with the **reviewer**, not the developer. A reviewer is read-only, single-shot and returns JSON, so it is the easiest adapter and gains most from a different model family.
- The owner already uses Codex as an adversarial auditor. A Codex or Claude reviewer over the frozen verdict contract is a small swap.

**R9. A run snapshot behind every role line** (BP14).
*Docs:* `3-ui.md` "Who speaks"; `2-architecture.md` §2 (Stored column).
- Each role turn records the role, model, input artifact versions (spec rev, plan rev, commit SHA) and its output artifact.
- The speaker line stays as it is. Details shows "Reviewer · <model> · checked commit abc123 against spec v2". This is cheap, and it is what makes "who decided this?" answerable after "while you were away".

**R10. Budget reservation and an 80 % line** (BP13).
*Docs:* `2-architecture.md` §5 (Money).
- Reserve a per-task allowance before dispatch, so parallel developers can't each spend the same remainder.
- At 80 % of the project or daily budget, the manager posts one fact line: "Used 80 % of today's limit." No per-role cost (this respects the 2026-09-28 decision).

### Keep (the evidence supports the current choice)

- The manager as engine rules with no model (`1-use-cases.md` "Roles", arch §5 "Manager: no model calls"). This is the strongest-supported decision in the plan.
- Artifacts, not transcripts, between roles (arch §3).
- Two approvals, A1 and A2, with plan approval off by default (arch §4).
- `maxFixRounds` default 2 and a single re-split (arch §5), consistent with ChatDev, MetaGPT and CrewAI caps.
- The `files[]` overlap rule and `extraFiles` (arch §2, §6).
- Steering into the running developer (use cases, "Common moments"). Anthropic names the inability to steer subagents mid-task as its main bottleneck.
- No org chart, dashboards or per-role queues (`1-use-cases.md` "What we will NOT do"). Paperclip's company UX and Gas Town's sprawl are the counter-examples.

### Drop or soften

- **Soften the "4 or more files = too broad" rule** (arch §2, quality gate). In this Flutter codebase one small feature touches a widget, state, `app_en.arb`/`app_ar.arb` and a test. Count only non-test, non-generated, non-l10n files, or raise the limit to 5. Otherwise the architect's retry fires on normal tasks.
- **Drop "any finding blocks"** (replaced by R2).
- **Drop the second PO question round** (replaced by R5.1's total cap).
