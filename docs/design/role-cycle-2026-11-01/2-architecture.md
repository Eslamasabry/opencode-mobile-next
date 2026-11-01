# Role cycle for AI Team: architecture (2026-11-01)

Step 2 of 3. Builds on `1-use-cases.md`. Plan only: no app code.
Amended the same day for two owner requirements: an agent and a model per
role (§9), and a manager that escalates to a model (§10). The research in
`research/BEST-PRACTICES.md` (12 AI-team projects) added the verification
changes in §11.
The owner's decisions (Q1–Q7, all 2026-11-01) are listed at the top of
`1-use-cases.md`.

**Principle:** the role cycle is a better **prompt-and-artifact chain** on the
engine we already have, not a new orchestrator. The phone engine stays the
only authority over state, money, branches and `main`. Roles are sessions with
an agent, a model, a permission set and one artifact each.

## 1. Where each part runs

| Part | Runs on | Why |
|---|---|---|
| State machine, scheduling, lanes, budgets, fix loop, escalation triggers, merges, `main` promotion | **Phone engine** (Rust daemon, `engine/phone`, outside proot, durable SQLite) | It already owns jobs, clones, receipts and admission, and it survives the app being swiped away |
| Architect, developer, reviewer sessions; manager escalations that act on tasks (T1–T3) | **OpenCode 2 today** (`opencode2.rs`, the only driver the engine has). **Claude Code / Codex / Gemini through Paseo after S4c** (§9) | One protocol now; a driver interface for more agents in the release |
| Product owner; manager final check (T4) and answers (T5) in the first slices; role voices, cards, Now line, manager words | **App (Dart)**, on any agent the app can already chat with: OpenCode 2 sessions, or Paseo agents (Claude Code, Codex, Gemini and other ACP agents) | Ships as a Shorebird patch. These steps are read-only and talk to the person, which is chat work, and chat is the app's job |
| Gas City / laptop orchestration (`lib/orchestration`) | Unchanged | The cycle targets phone-engine projects (`TeamProjectController`) |

```
person ──message──▶ App (Dart)
                     │  Product owner  (any agent the app chats with, read-only)
                     │  spec ──saveSpecDraft/approveSpec──▶ Phone engine
                     │                                      │ planner job  = Architect   (agent·model per role)
                     │                                      │ task jobs    = Developers  (own clone + branch)
                     │                                      │ checker jobs = Reviewer    (read-only, independent)
                     │                                      │ Manager rules: lanes, fix loop, budget, triggers T1–T4
                     │                                      │ Manager escalation (model, read-only, capped)
                     │◀──────── workspace + timeline events ─┘
                     │  Manager final check (T4) / answers (T5)  (app-side until S4)
                     └─ Ship card ──acceptMilestone + promote(confirmed)──▶ engine
```

## 2. Roles: prompts, inputs and artifacts

| Role | Prompt lives in | Input | Output (validated) | Stored |
|---|---|---|---|---|
| Product owner | Dart, new `lib/state/team_cycle/` (patch) | Person's sentence, project name, earlier answers, current spec (for a change) | JSON: `understood[]`, `milestones[{title, criteria[{id, text, how: test·run·look}]}]`, `outOfScope`, `assumptions[]`, `questions[≤3 in total]{text, why, choices[{label, recommended}], other}`, `ready` | Spec → engine `specDraft` via `saveSpecDraft`. Assumptions go into the existing `decisions` field, because the engine's `normalize_spec` keeps only goal/constraints/decisions/outOfScope/milestones/contextFiles. Round state → `oc.teamcycle.<profileId>` |
| Architect | Workspace role `planner` instructions (editable via `saveRole`, so patchable) + `planner_request` schema in Rust (release) | Approved spec, repo (read-only clone), roles | Today: `spec`, `phases`, `tasks{roleId, criteria, dependsOn}`. Adds: `approach`, `risks[]`, `decided[]` (choices already made: names, interfaces, data shape, colours), `questions[{question, options[], recommended}]` (open choices, D1), task `files[]`, and `checks[{criterionId, kind: test·command, target, new}]` binding every test/run criterion | Engine plan. `normalize_task` must keep `files` and `checks`; the project keeps `approach`, `risks`, `decided` and `questions` |
| Developer | Workspace role instructions (personas) + `worker_request` (Rust) | Task, criteria and their checks; **the whole spec, approach and `decided[]`, plus sibling task titles and their files** (shared decisions); findings on a fix round; the manager's rewritten brief after an escalation. It may add the checks the plan marks `new`, but may not edit or delete checks it didn't create | Commits + `.aiteam-verification/<task>.md` (exists) | Task branch |
| Reviewer | Workspace role `checker` + `checker_request` (Rust). **Default: a different model family from the developers** | Task commit, criteria, **the output of the checks the engine ran**, the diff, the evidence report (only for criteria no check covers). A fresh session for every re-check, without the earlier review conversation | `criterionResults[]` + `findings[]`, each blocking finding with `reason: unmetCriterion · failingCheck · plannedRisk · outsidePlan`; anything else goes in `notes[]` and never blocks | Engine task/job |
| Manager (rules) | No prompt. Engine rules + l10n words | Engine state | Status lines, reports | Engine timeline (exists) |
| Manager (escalated) | Dart for T4/T5 (patch); Rust for T1–T3 (release) | Trigger, the task's brief, findings, diff summary, spec, timeline facts | JSON: `action` (restart / reassign / rebrief / resplit / proposeSpecChange / ask / report), `brief`, `toRole`, `specChange`, `summary` | Engine timeline entry tagged `manager-escalation` with agent·model; the change card through the existing request path |

Rules carried over from Overseer:
- **Quality gate before building:** each plan task names its files and has at
  least one criterion, and every test/run criterion has a check. A task goes
  back to the architect once when it names **more than 5 files, not counting
  tests, generated files and l10n files** (a small Flutter feature touches a
  widget, state, both ARB files and a test), or when a milestone has more
  than 8 tasks. Enforced in `validate_plan` (release).
- **Small path:** a spec with one milestone and 3 or fewer criteria gets a
  plan with **one task**: approach, files and checks, with no split and no
  parallel lanes. The roles, review and ship card stay the same; there are
  just fewer turns.
- **The engine runs the checks itself:** after the developer commits, the
  engine runs each bound check in the task's clone and stores the command,
  exit code and output. The reviewer reads that. A check that can't run on
  this host makes the criterion "checked by reading", and the person sees the
  difference under Details.
- **Protected checks:** the engine compares the diff of check files with the
  plan, the same way as `extraFiles`. Editing or deleting a check the
  developer didn't create is a blocking `outsidePlan` finding.
- **Who did what (run snapshot):** every role turn records role, agent·model,
  spec version, plan version, commit and output. The engine already keeps
  `resolvedModels` per job; the snapshot extends it. Details shows "Reviewer ·
  GPT-6 Sol · checked abc123 against spec v2".
- **Close evidence:** a task is done only with evidence. That is the engine-run
  check output where a check exists, and committed evidence otherwise; blocking
  needs a stated reason (below).
- **Files outside the plan:** the engine compares the diff's file names with
  the task's `files[]` and records `extraFiles`. This is deterministic, with no
  model, and it is learning L5.

## 3. Handoffs and state machine

Project (new parts in **bold**):

```
**asking** ──PO ready──▶ **specReady** ──A1 Start building──▶ spec(approved)
planning (architect) ──plan valid──▶ plan ──auto unless decision──▶ running
                             └─ questions[] ─▶ **deciding** ──D1──▶ running
running ──milestone tasks all in dev──▶ **integration** (engine runs the project's checks on dev)
integration ─fail─▶ bisect by merge order (once) ─▶ one fix task ─▶ running
integration ─pass─▶ **finalCheck** (manager T4, or rules-only)
finalCheck ─gap (a spec criterion no task covers, or one that failed)─▶ one fix task; ship card leads with "Fix it first"
finalCheck ─clean─▶ **shipReady**
shipReady ──A2 Put on main──▶ next milestone: planning │ done
(at most one fix task per milestone from integration or finalCheck; after that, the person decides)
any state ──person says "also…" or T3──▶ **changeProposed** ──approve──▶ planning (affected tasks)
```

Task (new in **bold**):

```
queued → starting → running (developer) → **checksRun** (engine runs bound checks) → checking (fresh reviewer)
checking ─pass→ mergeReady → merging → completed
checking ─blocking finding→ needsFix ─round < maxFixRounds→ **fixing** (same developer session, findings) → checksRun → checking
fixing ─**stalled** (no diff, or the same criterion unmet for the same reason twice)─▶ **replanning** (architect re-split, with the attempt record)
needsFix ─rounds used, not stalled (T2)─┐
running ─stuck/looping (T1)─────────────┴→ **escalated** (manager model, ≤2 per task)
escalated → rebrief/restart/reassign → queued │ resplit → architect │ ask → needsYou (D2)
```

**Attempt record** (input to a re-split or an escalation): the task and
criteria, what each attempt changed (file list and its last reply in two
lines), the blocking findings, and the check output: what's known, what was
tried, why it's stuck.

Handoffs are artifacts, never chat between agents. No role reads another
role's transcript; the manager's escalation reads summaries the engine
already has (findings, criteria, diff file list, last reply), not raw
transcripts. Messages from the person go to the running developer as a steer,
to the manager when addressed to it (T5), and otherwise to the product owner.

## 4. Human approval points

**Always (2):**
- **A1 Start building:** approves the spec, and shows the team line ("Team:
  Claude Code plans, OpenCode builds · Change"). Runs `saveSpecDraft` +
  `approveSpec`. It is blocked, with the fix named, when a chosen agent isn't
  ready (§9).
- **A2 Put <milestone> on main:** one card per milestone, after the
  integration check and the final check. Runs `acceptPhase`/`acceptMilestone`
  + `promote` with `confirmed`. The card lists the criteria with how each was
  checked ("test passed", "ran it"), and a **Try this** list made from the
  "look" criteria, the things only a person can judge. When the final check
  found a gap, the primary is **Fix it first** (it starts the one fix task),
  and **Put … on main anyway** is secondary.

**Only when needed:**
- **D1:** the architect's decision.
- **D2:** a stuck task after the manager's escalation.
- **D3:** the budget limit.
- **D4:** a developer's question or permission. *Gap:* `answerRequest` is not
  in the phone engine's `STORE_ACTIONS`.
- **D5:** a spec-change card. It comes from the person's "also…" or from the
  manager (T3), and is never applied silently.
- **D6:** a judgement role's agent failed mid-run (§9).

Plan approval is removed by default. `reviewLevel: everyStep` brings it back.

## 5. Cost and time controls

| Control | Value | Where |
|---|---|---|
| Product owner questions | **≤3 in total**, one card at a time, with **Use your picks** for the rest | Prompt + Dart |
| Spec size | ≤3 milestones, ≤6 criteria each (v1) | Dart |
| Architect | 1 plan per milestone, 1 "too broad" retry, re-splits only when the manager asks | Engine (release) |
| Fix rounds | `maxFixRounds` 0–3, default 2 (exists as a setting). A stalled round ends the loop early | Engine (release) |
| Integration | One fix task per milestone from the integration check or the final check | Engine (release) |
| Small path | One milestone with ≤3 criteria: one task, no split | Planner instructions (patch) + `validate_plan` (release) |
| Manager | Rules: free. Escalation: ≤2 per task (0–3), ≤10 per project per day, counted in the budget, skipped when the budget is paused (then D2/D3) | Engine + app |
| Money | Total, daily and per-task token budgets with pause (exist). Rows say "free / paid per use / uses your plan". No cost per role or per task. **At 80 % of the project or daily limit, the manager posts one line** ("Used 80 % of today's limit"); the engine's `budgetWarning` field exists but nothing sets it, so the app computes it from `spent`/`spentToday` (patch). A per-task allowance is reserved before dispatch, so parallel developers can't spend the same remainder (release) | App + engine |
| Unmetered agents (no usage reported) | Capped by time per task and number of sessions per day; the sheet says "unmetered" | Engine (release) |
| Time | Planner patience 30 min (exists); 15 min silence (T1) | Engine |

## 6. Failure modes

| Failure | Guard |
|---|---|
| Product owner asks too much | Hard caps, choices with one recommended, "Something else" |
| Product owner invents requirements | Assumptions are listed on the spec card and can be changed before A1 |
| Product owner can't see the repo (v1) | Its questions stay about intent. The architect or the manager (T3) raises a conflict with the code |
| Invalid or oversized plan | `validate_plan` + the size rule; one retry; then plain words and Try again |
| Developer fakes evidence | **The engine runs the bound checks itself** and the reviewer reads that output. Check files are protected. Only criteria with no runnable check fall back to the committed report, marked "checked by reading" |
| Reviewer nitpicks forever | Only the four stated reasons block; the rest are notes. Each re-check is a fresh session that doesn't see the last argument |
| A fix round that changes nothing | Stall detection goes straight to a re-split with the attempt record |
| Merges pass alone and break together | Integration check on `dev`, bisect once, one fix task, "Fix it first" |
| Endless loops | `maxFixRounds`, then ≤2 escalations, then D2 |
| The manager's model makes a bad call | Its actions are limited to restart/reassign/rebrief/resplit/propose. It never merges or edits the spec. Each call is visible and capped |
| Two developers edit the same files | `files[]` overlap means those tasks never run in parallel; a conflict means one rebase |
| A role's agent fails mid-run | §9: fallback or ask |
| Engine restarted mid-role | Durable jobs + `resume_job` (exists); models pinned per job (`resolvedModels`) |
| Profile deleted | `oc.teamcycle.<profileId>` joins the deletion sweep |

## 7. Patchable versus release

| Change | Ships as |
|---|---|
| Role voices; `KitMessage.reply(speaker:)`; l10n | Patch |
| Showing the model each role used (from `resolvedModels`, which exists) | Patch |
| PO: ≤3 questions in total, one card at a time, "why it matters", Use your picks; test/run/look marks (kept app-side, keyed by criterion, until the release moves them into the spec) | Patch |
| Small path through the planner's instructions (`saveRole`) | Patch |
| 80 % budget line computed by the app | Patch |
| Ship card "Try this" list, and "Fix it first" when the app's final check finds a gap | Patch |
| Product owner on any agent the app chats with (OpenCode 2 or Paseo) | Patch |
| "Who does what" sheet: phone-wide **model** per engine role through `saveRole` (exists); **agent and model** for the product owner and the manager's app-side checks; readiness notes | Patch |
| Manager final check (T4) and answers (T5) from the app | Patch |
| Auto plan approval from the app (interim, only while the app runs) | Patch |
| Plan schema (`approach`, `risks`, `decisions`, task `files`) | **Release** |
| Fix rounds, `extraFiles`, no parallel overlap, size rule (loosened) | **Release** |
| Criteria with ids and marks in the spec; checks bound in the plan; **engine-run checks**; protected check files | **Release** |
| Reviewer blocking reasons, `notes[]`, a fresh session per re-check, different family by default | **Release** |
| Stall detection + attempt record → re-split | **Release** |
| Integration check on `dev`, bisect once, one fix task | **Release** |
| Shared decisions (`decided[]`, sibling tasks) in every developer prompt | **Release** |
| Run snapshot (spec/plan version, commit) and per-task budget reservation | **Release** |
| Manager escalation T1–T3 with its actions and caps | **Release** |
| Per-project role choices (`agent`, `model`, `fallback`) and using `fallbackModel` | **Release** |
| Paseo driver for engine-run roles (Claude Code, Codex, Gemini, ACP) | **Release** |
| Steering a running developer, `answerRequest`, partial re-plan | **Release** |

## 8. Slices with finish lines

Codex builds the engine against a frozen contract, and Claude/Sonnet builds
the UI. One worktree per slice. Emulator proof with a
`docs/qa/<feature>-<date>/README.md` record. The owner tests the APK.

| # | Slice | Ships | Finish line | Non-goal |
|---|---|---|---|---|
| S1 | **Role voices** | Patch | Every line of an existing phone-engine conversation sits under its role; the Now line names who does what; cards name what they approve | New behaviour |
| S2 | **Product owner asks** | Patch | Sentence → PO turn (understood, assumptions) → ≤3 question cards in total, one at a time, each with why it matters and **Use your picks** → spec with test/run/look marks → **Start building** → `approveSpec`. A spec with one milestone and ≤3 criteria takes the small path (planner instructions). Resumes after restart; the profile deletion sweep removes the drafts | Repo reading; changes |
| S3 | **Who does what (patch half)** | Patch | Sheet from the task's ⋯ and project settings. The PO and manager rows take any agent·model the app can chat with. Architect, developer and reviewer rows take any OpenCode 2 model phone-wide through `saveRole`, and their agent row says "Claude Code and Codex come with the next app update". Readiness notes and the A1 block work | Per-project choices for engine roles |
| S4 | **Ship card + manager final check** | Patch | When a milestone is all in `dev`, the manager escalates once (T4) and posts "Manager (Opus 5.5): …". With "Never use a model" it does the rules-only check. Then one card: **Put <milestone> on main**, with how each criterion was checked and a **Try this** list. A gap gives **Fix it first** (it starts a fix task through `createQuickTask` on the same repo until the engine owns it). "Manager, …" messages get answers (T5). The 80 % budget line and the model behind each role line appear | Auto-promote |
| S5 | **Cycle core (engine)** | Release | Frozen contract first. On the emulator: **the engine runs a bound test itself and the reviewer cites its output**; a non-blocking note never stops a merge; a fix round with no diff goes straight to a re-split with the attempt record; **two tasks that pass alone but fail together on `dev` produce one fix task**; a seeded unmet criterion is fixed with no person, or the manager escalates (T2) once and then asks one D2 question; shared decisions reach every developer; a stuck developer (T1) is restarted with a new brief; a plan without decisions starts with no tap; overlapping `files[]` never run in parallel; `extraFiles` is recorded; per-project role choices and fallbacks apply; caps hold | Paseo driver |
| S5b | **Cycle core (UI)** | Patch on that release | Architect turn with approach and risks; D1/D2/D5/D6 cards; fix and escalation lines; per-project rows enabled | — |
| S6 | **Other agents in the engine** | Release | **The reviewer moves first** (read-only, one pass, JSON: the easiest adapter, and it gains most from a different family), then the architect, then developers. All go through Paseo (Claude Code, Codex, Gemini), with read-only proven for the architect, reviewer and manager; usage is "unmetered" where nothing is reported; the emulator runs one project with an OpenCode developer and a Codex reviewer | — |
| S7 | Change of mind + steering | Patch on S5 | "Also allow cancelling" → change card → only affected tasks re-planned; steers reach the running developer | — |

**Order:** S1 → S2 → S3 → S4 as patches, with S5 (Codex) in parallel from a
frozen contract → S5b → S6 → S7. The escalation lands in two halves: T4/T5
early in S4 (patch, app-side), and T1–T3 in S5 (they must act while the app is
closed, so they belong to the engine). S5 and S6 can ship in one native
release if S6 is ready; otherwise S6 rides the next one.

## 9. Agent and model per role

**Where the choice lives**

| Level | Holds | Stored | Today |
|---|---|---|---|
| Phone-wide default | `agent`, `model`, `fallback` for each role id (owner, planner, worker personas, checker, manager) | Engine workspace `roles[]` (has `model` and `fallbackModel` today; gains `agent`) | `model` per role works today through `saveRole`. **`fallbackModel` is stored, but the daemon never reads it.** There is no `agent` field |
| Per project | Overrides of the same fields | Engine project `roleChoices{roleId: {agent, model, fallback}}` (new) | **Not possible:** roles are workspace-wide, and a job copies its role's model when it is created |
| App-side roles (PO; manager T4/T5 before S5) | Same fields | `oc.teamcycle.<profileId>` (per project, swept on profile deletion) | Patch |
| Per job | The resolved agent·model, fixed once dispatched | `resolvedModels` (exists) | Reconnecting never switches models (exists) |

"Same for all developers" writes one choice to every worker persona; when it
is off, each persona (Frontend, Backend…) gets its own row.

**How the engine runs a role on an agent (verified in `engine/phone/src`)**

| Capability | Today | Needs |
|---|---|---|
| OpenCode 2 sessions with any provider/model OC2 resolves (`resolve_model`, `/api/model/default`) | Yes. `create_session` with agent `build`, read-only through OC2 permission rules that `confirm_policy` verifies | — |
| A Claude model through OpenCode | Only through an OC2 provider. OC2 2.2.x's built-in Claude Agent provider would allow it, but we pin 2.1.1 | Evaluate the 2.2.x pin (owner brief 10-30) |
| Claude Code, Codex, Gemini or other ACP agents | **No.** The engine has one driver, `opencode2.rs`. The app reaches these agents through Paseo (`lib/paseo`, providers `claude`, `codex`, `gemini`, `qwen`…) and Codex direct (`lib/codex`) | S6: an engine `AgentDriver` (create / prompt / observe / abort / verify) with a Paseo implementation. The engine sits outside proot, so it needs the Paseo daemon's address and token from the app's keystore handoff |
| Enforced read-only | OC2 permission rules, checked after creation | For each Paseo agent, a mode the engine can verify (Claude Code plan/read-only tools, Codex read-only sandbox, ACP permission denial). An agent that can't prove read-only is not offered for the architect, reviewer or manager |
| Strict JSON verdicts | `structured_server_output` accepts one trailing JSON object after prose | Reused for Paseo replies |
| Usage for budgets | OC2-reported tokens and cost | Paseo agents are often unmetered: time and run caps apply |

**Validation (preflight).** This runs when the sheet opens, on the A1 card,
and before each dispatch:
1. **Installed:** the Paseo snapshot lists the provider, or OC2 is reachable.
2. **Signed in:** Paseo's `AuthRequired`/provider error (the app already maps
   it to "needs sign-in"), or the OC2 provider is connected.
3. **Model available:** in OC2's catalog or the Paseo agent's model list.
4. **Fits the role:** read-only provable for the PO, architect, reviewer and
   manager; the reviewer's agent and model are not the same as the developers'
   (warning, not a block).
5. **Budget:** paid with no limit gives a note; unmetered gives a note.

Results show as one honest line per row ("Needs Claude Code sign-in · Sign
in"). Steps 1–3 failing for a role in the next milestone block A1. The card
names the role and offers the fix or "Use OpenCode for now".

**If the chosen agent fails mid-cycle**

| Failure | Developers | PO / architect / reviewer / manager |
|---|---|---|
| Signed out, uninstalled, model withdrawn | Switch to the role's fallback (default: OpenCode with the team model), start a fresh session, and say so: "Developer switched to OpenCode · Kimi K3: Claude Code is signed out" | Ask (D6): "Sign in to Claude Code" / "Use OpenCode for this project" / "Pause". An explicitly set fallback is used without asking |
| Rate limit or plan quota | Wait until the reset time if the agent gives one (≤60 min), else fall back | Wait, then ask |
| Session crash | `resume_job` observes it (exists); one restart on the same agent; then the row above | Same |
| A fallback would make the reviewer the same agent and model as the developers | — | Always ask |

A model is never switched inside a running session. A switch is a new session
with a visible line.

## 10. Manager escalation

- **Choice:** the Manager row is "When stuck: <agent · model>" or "Never use a
  model". **Owner decision (2026-11-01): the default is "When stuck: the
  strongest signed-in model, at most 2 times per task".** "Strongest" is
  picked from Claude Code · Opus 5.5, Codex · GPT-6 Sol and OpenCode's best
  configured model, in that order, among those signed in.
- **Triggers:** T1 stuck or looping, T2 fix rounds used up, T3 spec conflict,
  T4 final check, T5 the person asks (use cases). The rules detect them; the
  model never wakes itself up.
- **Session:** read-only, a fresh session per escalation, and its input is the
  engine's summaries (§3). Its output is one validated action (§2). The engine
  carries out that action, and only actions on the allowed list.
- **Visible:** a timeline entry "Manager (Opus 5.5): <summary>", with a fold
  showing what it changed (for example the old and new brief). It counts
  toward the budget.
- **Never:** merge, push, promote, approve its own change card, raise a
  budget, or change "Who does what".
- **Where:** T4/T5 in the app (patch, S4); T1–T3 in the engine (release, S5),
  because they must work while the app is closed.

## 11. Changes from the research (2026-11-01)

From `research/BEST-PRACTICES.md`, its top five, plus the minor ones that fit
the phone:

1. **Checkable criteria:** test / run / look marks, checks bound by the
   architect, run by the engine, and "look" criteria turned into Try this
   (§2, §4, §6).
2. **Reviewer:** blocks only for a stated reason, a fresh session per
   re-check, and a different model family by default (§2, §6).
3. **Stall detection:** no diff, or the same criterion unmet for the same
   reason twice, goes straight to a re-split with an attempt record (§3).
4. **Integration check on `dev`** before the ship card; one fix task; "Fix it
   first" (§3, §4).
5. **Fit the process to the job:** ≤3 PO questions in total, one card at a
   time, with Use your picks; the small path (§2, §5).

Minor ones applied: shared decisions to every developer (§2), the model each
role used (§2 run snapshot), the 80 % budget line (§5), and the loosened
too-broad rule (§2).

Kept despite the research: the manager can escalate to a model (owner
requirement, §10; default decided by the owner: the strongest signed-in model,
at most 2 times per task). The research's case for a rules-only manager is honoured as
the default behaviour, with escalation capped and labelled.
