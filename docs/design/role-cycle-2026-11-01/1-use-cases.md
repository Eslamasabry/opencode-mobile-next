# Role cycle for AI Team: use cases (2026-11-01)

Step 1 of 3 (use cases, then architecture, then UI). Plan only: no app code.

**Finish line:** a person gives a team task one sentence, answers at most a few
questions, approves a spec, and later approves putting the result on main. In
between, the conversation reads like a small team at work: product owner,
architect, manager, developers and reviewer.

**Non-goal:** Overseer's company machinery (org chart, role dashboards,
opportunity scanning, approval inbox, KPIs).

## Owner decisions (2026-11-01)

| # | Question | Decided |
|---|---|---|
| Q1 | Plan approval | Off by default; the person is asked only when the architect has a real choice (D1). "Every step" still shows the plan |
| Q2 | Shipping a milestone | One tap: accept the milestone and put it on main together (after "Fix it first", when needed) |
| Q3 | Product owner | Ships first as a patch, without reading the repo; the engine version with repo access comes later |
| Q4 | Role names | Keep Product owner, Architect, Manager, Developer, Reviewer |
| Q5 | Caps | 3 product owner questions in total, 2 fix rounds, 3 milestones per spec |
| Q6 | Scope | Phone-engine projects first; Gas City hosts stay unchanged for now |
| Q7 | Manager default | "When stuck: the strongest signed-in model, at most 2 times per task" |
| — | Agent and model per role | Yes: a "Who does what" sheet with defaults picked for you |

## What Overseer does (the source)

Overseer (`src/daemon/command-roles.ts`, `command-role-activity.ts`,
`product-ops/role-work-mapper.ts`, `autonomy/bead-quality-governor.ts`,
`prompts/registry.ts`) defines five command roles. Each role has a work queue
(`RoleWorkItem`: queued → claimed → in_progress → blocked/stale →
completed/cancelled, with a lease, source references and **close evidence**).

| Overseer role | Owns | Produces | Hands off to |
|---|---|---|---|
| Product Ops (manager) | Scheduler sweeps, opportunity synthesis, routing work to roles, architecture-review coordination | Role work items with dedupe keys, handoff plans, beads | BA, PO, CTO, workers |
| Business Analyst | Problem statements, constraints, success metrics, refining unclear beads | Refined bead packets, missing-evidence questions | PO |
| Product Owner | Value, priority, acceptance criteria, scope tradeoffs, approving "prime runs" | Acceptance criteria, approved/rejected prime runs, close evidence expectations | Workers (via dispatchable beads) |
| CTO / Architect | Technical direction, interfaces, architecture reviews with blocking findings | Implementation contracts, risk calls, blocking findings | Workers; blocks dispatch while a finding is open |
| CEO / Operator | High-risk approvals, release and budget exceptions | Approve/reject decisions | Everyone (unblocks) |
| Workers (frontend, backend, tester) and reviewer | Building and checking | Commits, test evidence, review findings | Landing gate, merge |

How work moves: Product Ops finds or receives work, BA and PO refine it until a
**quality gate** passes (every packet needs "Read order", "Source anchors",
"Implementation contract", "Minimum validation", "Close evidence", and must be
small enough for one worker: 4 or more file anchors means split it). The CTO's
blocking findings stop dispatch. Workers build in worktrees, the reviewer
checks, and a **landing gate** holds finished work until evidence and merge
are clean. The CEO is only asked for high-risk items (Overseer's own audit
warns when routine items reach the CEO). A human approves prime runs (PO) and
high-risk actions (CEO).

The ideas worth keeping: **each role produces one artifact the next role
consumes**, **a quality gate before building**, **close evidence before
done**, **the human is asked rarely and only by name**. Everything else (queues
per role, leases, memory blocks per role, scheduler sweeps, dashboards) is
there because Overseer runs dozens of agents unattended on a server.

## What AI Team does today (our starting point)

The phone engine (`engine/phone/src`, Rust) already has most of the bones:

- A **project** starts with a spec the person fills in as a form: goal,
  constraints, decisions, out of scope, milestones with criteria
  (`team_project_editors.dart`). `approveSpec` records "approved by person".
- A read-only **planner** session turns the approved spec into phases and
  tasks, each with a role, criteria and dependencies (`planner_request`,
  `validate_plan`). The person approves the plan (`approvePlan`).
- **Workers** build each task in their own clone and branch, and must commit
  an evidence report `.aiteam-verification/<task>.md` (`worker_request`).
- A read-only **checker** checks the commit against each criterion, using only
  that committed evidence, and returns strict JSON. Any finding blocks the
  merge (`checker_request`, `validate_check`).
- Passed tasks merge into `dev`. The person accepts phases and milestones, and
  promotes to `main` (`promote` needs `confirmed`).
- Budgets (total, daily, per-task tokens), lanes (phone 1 by default) and
  "chat first" (team work waits while you chat) hold the engine back.
- Roles (personas) exist: Product, Frontend, Backend, Tester, General. A worker
  "puts on" a task's role through its instructions (`team_roles.dart`).

**Gaps that matter to a role cycle:**
1. The spec is a five-field form. On a phone this is the hardest step, and the
   person does the product owner's job.
2. The plan carries no approach, files or risks. The engine keeps only fixed
   task keys (`normalize_task`), so an architect's notes would be dropped.
3. A failed check stops at `needsFix`. `maxFixRounds` and `autoFix` are stored
   settings, but the phone engine has no fix round: `fixFindings`, `recheckTask`
   and `messageTask` exist only in the fixture gateway, not in the engine's
   `STORE_ACTIONS`.
4. The person approves three or four times per project: spec, plan, each
   phase or milestone, then main.
5. The conversation's lines say what happened ("A step was merged"), but not
   who did it.
6. The checker can't run anything. It trusts the evidence report that the
   developer wrote, criteria are plain text rather than checks, and nothing
   checks `dev` after several merges (research
   `research/BEST-PRACTICES.md`).

## Who uses it, and for what

### A. Solo builder with an idea
*Mona, evenings only, phone in hand, knows what she wants but not how to write
acceptance criteria.* She types "a habit tracker that reminds me at 9pm and
shows a streak".
- Today she meets a form with "Constraints", "Decisions" and "Milestones" and
  gives up, or fills it badly.
- With the cycle, the product owner says what it understood and asks two
  questions, one card at a time. Each card has a line on why it matters,
  answers she can tap, and **Use your picks** so she can stop at any point.
  It then shows a spec with four plain "done when" lines. She taps **Start
  building**. She checks in twice over the evening, and at the end gets one
  card, **Put it on main**, with a short **Try this** list for what only a
  person can judge ("Does the streak look right to you?").

### B. Small business owner
*Karim runs a barbershop; his site is a repo his nephew set up. He reads every
word and is scared of breaking the live site.* "Add online booking; I get a
message for each booking."
- What he needs: questions in his words ("WhatsApp or email?"), no Git words,
  a clear statement of what will **not** change, and certainty that nothing
  goes live until he says so.
- The product owner writes "No payments, no accounts, no changes to the other
  pages". The architect flags the one real risk (a WhatsApp token) and the team
  asks him for it only when it is needed. The ship card says "Your live site
  updates from main", and its Try this list says "Book Saturday 10:00 on your
  phone; a WhatsApp message should arrive".

### C. Developer delegating a feature
*Sara, a developer, on her phone between meetings; the repo has AGENTS.md and
tests.* "Add CSV export to the reports screen."
- What she needs: speed, evidence, and control at two points. She does not
  want questions she has already answered in the repo.
- The product owner asks nothing if the sentence and the repo cover it; it
  still shows its assumptions. Her spec has one milestone with three criteria,
  so it takes the **small path**: one task, no split, no parallel lanes, and
  fewer turns. The architect binds each criterion to a test, and **the engine
  runs those tests itself**, so the reviewer reads real output, not the
  developer's word. She approves the merge to main from the notification.

### Common moments for all three
- **Away and back:** "While you were away: 3 of 4 tasks done, one fixed after
  review, nothing needs you."
- **Changing your mind mid-way:** "Also allow cancelling a booking." The
  product owner turns it into a spec change, shows what changes, and asks for
  approval of the change only.
- **Steering:** "Use the shop's green for the button." This reaches the
  developer working on that task now (steer, never queue).

## Roles: what each adds over today's workers plus checker

| Role | Kind | Adds over today | Artifact | Model cost |
|---|---|---|---|---|
| **Product owner** | Distinct agent (its own read-only session) | Replaces the spec form: turns the sentence into criteria and an explicit "not doing" list, asks only what is missing (**at most 3 questions in total**, one card at a time, each with "why it matters", a recommended answer and **Use your picks**), and states its assumptions. It marks every criterion **test**, **run** or **look**. It also writes the change cards when the person changes their mind. | Spec (goal, criteria by milestone, each marked test / run / look, out of scope, assumptions) | 1 to 3 short turns per project, plus 1 per change |
| **Architect** | Distinct agent: today's planner, with a new prompt and schema | Approach in two lines, files per task, risks, **decisions already made** (names, data shape, colours) that every developer receives, and a question only when a real choice exists. **Binds each test/run criterion to a test file or command.** Splits work so that tasks don't touch the same files (parallel-safe), or keeps a small job as one task. | Plan (approach, risks, decisions, phases, tasks with files, criteria and their checks, dependencies) | 1 turn per milestone, plus 1 re-split per stuck task |
| **Manager** | **Rules by default, a model when stuck.** Engine rules plus the app's words; it escalates to a model only when the rules can't move things forward (see "Manager escalation" below). | Tracks, schedules lanes, runs the fix loop, reports "while you were away". When escalated, it unsticks tasks, re-briefs or reassigns them, proposes spec edits as change cards, and does the final cross-check before main. | Status lines, reports, escalation notes, final check | None by rule; at most 2 escalations per task (default) |
| **Developers** | Distinct agents, one session per task (today's workers) | Unchanged, plus a fix round with the reviewer's findings, and steering into the running session. Personas (Frontend, Backend…) stay. | Commits plus `.aiteam-verification/<task>.md` | As today |
| **Reviewer** | Distinct agent (today's checker), **by default a different model family from the developers** | Reads the output of the checks **the engine ran itself**, plus the diff, not only the developer's report. A finding blocks only for a stated reason: an unmet criterion, a failing check, a planned risk, or a file outside the plan. Anything else is a note. Every re-check is a fresh session. | Verdict JSON (criteria met/unmet, blocking findings, notes) | As today |
| **CEO** | **The person** | Approves the spec and the move to main. Answers decisions only when a role can't decide alone. | Approvals | — |

Overseer's Business Analyst folds into the product owner, CTO into architect,
Product Ops into manager, tester into developer evidence plus reviewer.

**Count:** four model roles (product owner, architect, developer, reviewer)
where today there are three (planner, worker, checker). The product owner is a
new session type, and so is the manager's escalation, but that runs only when
the rules are stuck. Most of the time the manager is a voice over the engine's
facts. This is the smallest set that still reads as a team and adds real
value.

## When the cycle repeats

| Trigger | Loop | Bound | When the bound is hit |
|---|---|---|---|
| Review fails | Blocking findings → same developer fixes in its session → engine re-runs the checks → a **fresh** reviewer session checks again | `maxFixRounds` (default 2). **A stalled round goes straight to a re-split:** a round that changed nothing, or the same criterion unmet for the same reason twice. The architect gets a short record of what was tried | **Manager escalates** (once): re-briefs the task, reassigns it to another developer agent, or asks the architect to re-split it → if that fails too, it asks the person one named question: "Try another way / Skip this task / I'll look" |
| Scope change (person says "also…") | Product owner writes a spec change → person approves the change → architect re-plans only the affected tasks → manager reschedules | One change card per message | — |
| Small job (one milestone, ≤3 criteria) | **Small path:** the architect returns approach plus files for one task: no split, no parallel lanes, the same review and ship card | — | — |
| Steering ("use green") | Goes straight to the running developer's session | — | If nothing is running, the product owner decides whether it's a change |
| Architect sees a real choice | Decision card with 2 or 3 options and a recommendation | Only for choices that change cost, data or what users see | Default after 24 h: none. The task waits, and other tasks go on |
| Merge conflict | The developer of that task rebases onto `dev` and the reviewer checks again | 1 | Manager asks the person |
| Milestone done | **Integration check on `dev`:** the engine runs the project's checks after the milestone's merges (rules, no model). A failure, or a gap the final check finds, creates **one** fix task, and the ship card leads with **Fix it first** (once per milestone; then the person decides). Then the **manager escalates for the final check** (all tasks together against the spec, plus files outside the plan) → ship card. With "Never use a model", a rules-only check (every criterion met, the list of extra files). Next milestone: architect plans it (the spec is already approved) | — | — |
| Budget reached | Engine pauses (exists) → manager says how much is spent and offers "Raise the limit to …" | — | — |

## What we will NOT do

- No org chart, role pages, role dashboards, KPIs or per-role queues.
- No separate CTO, BA, Product Ops or tester agents.
- No agent that starts work on its own (no opportunity scanning). Every cycle
  starts from the person's message.
- No manager that runs a model all the time. Its everyday lines come only from
  recorded facts. A model runs only on the five escalation triggers, every run
  shows as "Manager (model): …", and it never merges, pushes, moves to main or
  edits the spec without the person.
- No approval inbox and no "approve the plan" step by default.
- No automatic move to `main`, ever.
- No per-role memory store. The spec and plan are the memory, and the repo's
  AGENTS.md and README are the context.
- No role picker in the main flow. The choice of agent and model per role
  lives in one "Who does what" sheet, with defaults picked for you.
- No new screen, tab or bar. Everything lives in the team task conversation.
- No Paseo (Claude Code / Codex / Gemini) for the engine-run roles until the
  engine release (S4). The product owner and the manager's app-side checks can
  use them sooner. See architecture §1 and §9.

## Choosing an agent and a model per role (owner requirement, 2026-11-01)

The owner: *"I should be able to select which agent and which model for each
role."* Every role except the person has one row: **agent · model**. Defaults
are picked for you.

| Mix | Why someone picks it |
|---|---|
| Product owner and architect on **Claude Code · Opus 5.5** | Judgement: few calls, and the most expensive mistakes happen here. These roles are cheap in volume and expensive to get wrong |
| Developers on **OpenCode · a free model**, or **Codex** | Volume: most of the tokens are spent here. Mona (A) runs free models; Sara (C) uses Codex on her laptop host for speed |
| Reviewer on **a different agent than the developers** (for example Codex reviewing OpenCode's work) | Independence: a second model family catches what the first one is blind to. The sheet warns when the reviewer equals the developers |
| Manager: **"When stuck: Claude Code · Opus 5.5"** (the default, by owner decision: the strongest signed-in model, at most 2 times per task) or **"Never use a model"** | Karim (B) wants it cheap and predictable and picks "Never". Sara wants unsticking without being asked |

**Who sees the choice:**
- Mona never opens it. The defaults are "OpenCode with the team model" for
  everyone, plus "When stuck: the strongest model you're signed in to, at most
  2 times a task".
- Karim opens it once, because the spec card says "Team: OpenCode, free
  models · Change".
- Sara sets phone-wide defaults once, and changes them per project when a
  project matters.

**When an agent isn't installed or signed in:**
- The row says so before anything starts: "Needs Claude Code sign-in · Sign
  in", "Gemini isn't installed on this phone · Install", or "Model no longer
  offered · Choose another". The **Start building** card names the blocking
  role ("The reviewer needs Codex sign-in") and offers the fix and a fallback
  ("Use OpenCode for now").
- If sign-in lapses mid-run, developers switch to their fallback by themselves
  and say so. Judgement roles (product owner, architect, reviewer, manager)
  ask first. A fallback never makes the reviewer the same agent and model as
  the developers.

**Budget awareness:**
- Each row's model line says how it costs: "free", "paid per use", or "uses
  your Claude plan". The sheet shows the project's spending limit beside the
  choices. There is no cost per role or per task (decision 2026-09-28).
- Agents that don't report usage (often plans through Paseo) count as
  "unmetered". The engine limits them by time and number of runs, and says
  so.
- A paid model with no spending limit set gets one line on the spec card:
  "Opus 5.5 is paid per use and this project has no limit · Set a limit".

## Manager escalation (owner requirement, 2026-11-01)

The owner: *"Manager can upgrade itself to a model to unstick agents, or edit
specs, or do final checks, or whatever."* The manager follows rules by
default and escalates to its chosen model only on these triggers:

| Trigger | Detected by (rules) | Typical result |
|---|---|---|
| **T1 Stuck or looping** | No new output for 15 min while busy; the same command or the same failure 3 times; a planner past its 30 min patience | Restart with a sharper brief, or reassign to another developer agent |
| **T2 Fix rounds used up** | `fixRounds == maxFixRounds` and still unmet | Re-brief, reassign, ask the architect to re-split, or ask the person |
| **T3 Spec conflict mid-run** | A reviewer finding or developer question says a criterion can't be met as written, or the architect flags that the code contradicts the spec | A spec-change card for the person (never applied silently) |
| **T4 Final check before main** | Every task of a milestone has merged into `dev` | A cross-check of all tasks together against the spec and the list of files outside the plan, then the ship card |
| **T5 You ask the manager** | A message addressed to the manager ("Manager, why is this slow?") | A plain answer; it may take any of the actions below |

**What it may do:** restart or reassign a task (to an agent already allowed
in "Who does what"); rewrite the stuck task's instructions; ask the architect
for a re-split; propose a spec edit as a change card the person approves; run
the final cross-check; answer the person.

**What it never does:** merge, push, move to main, approve its own change
card, raise a budget, or change the role choices permanently.

**Limits:**
- Every escalation shows in the conversation as "Manager (Opus 5.5): …" and
  counts against the project's budget.
- At most 2 escalations per task by default (0–3), and 10 per project per day.
  **Owner decision, 2026-11-01:** the default is "When stuck: the strongest
  signed-in model, at most 2 times per task".
- With "Never use a model", the triggers go straight to the person's named
  question, and T4 becomes the rules-only check.
