# Role cycle for AI Team: UI (2026-11-01)

Step 3 of 3. Builds on `1-use-cases.md` and `2-architecture.md`. Plan only:
no app code. The renders are concepts made from existing kit parts with fake
data (light, Solid, 412 dp at 720 px), in
`/home/eslam/Storage2/wt/shots-2026-11-01/role-cycle/`.
The owner's decisions (Q1–Q7, all 2026-11-01) are listed at the top of
`1-use-cases.md`.

## Rules applied

- **One place:** everything happens in the AI Team task conversation. There is
  no new screen, tab, bar, dashboard or role page.
- **No new levels:** "who's doing what now" is the existing Now line under the
  top bar (`TeamNowLineView` → `KitStatusLine`), with words that name the role.
  The top bar's subtitle carries the progress ("2 of 4 done").
- **Teammates talk:** each role's turn is a reply with a speaker line (icon +
  role name + age), for example "Product owner: here's what I understood…".
  The manager speaks too: facts the engine recorded, and, when it escalates
  to a model, a line labelled with that model ("Manager (Opus 5.5): …"), so
  every paid call is visible.
- **Cards name what they approve:** "Start building the booking page" and "Put
  the booking page on main", never "Approve" or "OK".
- **Obvious options first:** a question's answers are visible rows with one
  marked as recommended, plus "Something else". They are not hidden in a menu.
- **Steer, never queue:** while a role works, sending a message steers. The
  composer shows no queue choice.
- **No raw errors:** a failed plan or check is plain words plus a way forward;
  the technical text goes under Details.
- **Simplify:** the spec form (5 fields) is gone from the main path. The plan
  card is gone by default. Accept milestone and promote become one card.

## Who speaks, and how

| Speaker | Icon (Phosphor via AppIconography) | Says | Never says |
|---|---|---|---|
| Product owner | `checklist` | What it understood, its assumptions, at most 3 questions, the spec, change cards | Code or file names |
| Architect | `layers` | The approach in 2 sentences, risks, a decision when there's a real choice | Task-by-task progress |
| Manager | `kanban` | Splits, lanes, "sent back to the developer (fix 1 of 2)", "while you were away" | Anything the engine didn't record |
| Manager (*model*) | `kanban` | Only on an escalation: "Restarted *task* with a sharper brief", "Proposed a spec change", the final check, answers to "Manager, …" | Anything without its model label |
| Developer · *persona* | `code` | What it built or fixed, in one or two lines (from its session's last reply); tap opens its session in watching mode (exists) | — |
| Reviewer | `checkCircle` | "Meets 3 of 3 criteria" or the unmet criterion and why it blocks, citing the tests the team ran itself ("See the output"); non-blocking notes fold under "Also noticed" | Findings as raw JSON; blocking for taste |
| You | Your prompt bubble | — | — |

Speaker lines need one kit addition: **`KitMessage.reply(speaker:,
speakerIcon:, since:)`**. The renders draw it as a stand-in. It is the only new
kit part. Cards reuse `KitPlanCard`, `KitPhaseCard`, `KitPromoteCard`,
`KitFindingsCard` and `KitChoiceList`. Long role turns fold like today's work
line (the first two lines, then "More").

## Now line wording (existing slot, new words)

| State | Line | Next |
|---|---|---|
| asking | Product owner is waiting for your answer | Then: the spec for you to approve |
| specReady | Waiting for you to approve the spec | Then: the architect plans it |
| planning | Architect is planning Milestone 1 | About 2 min |
| running | Developer is building *task* | Then the reviewer checks it |
| checksRun | Running the tests for *task* | Then the reviewer checks it |
| checking | Reviewer is checking *task* | — |
| integration | Testing the merged work | Then the final check |
| fixing | Developer is fixing *task* (fix 1 of 2) | — |
| deciding | The architect needs a decision | Other tasks keep going |
| escalated | Manager is looking at *task* (Opus 5.5) | 1 of 2 for this task |
| shipReady | Milestone 1 is ready for main | Waiting for you |
| paused (budget) | Paused at today's limit | Raise the limit, or it resumes tomorrow |

The amber "needs you" tone and the top bar's needs-you count carry urgency.
Lists are not split into states.

## The six concepts

1. **`1-product-owner-asks.png`:** the person's sentence → "Product owner:
   Here's what I understood" (3 bullets, one stated assumption) → **one
   question card at a time** ("Question 1 of 2"), with a "Why it matters"
   line, a `KitChoiceList` with a recommended answer and "Something else", and
   the tertiary **Use your picks for the rest**. There are at most 3 questions
   in total. The Now line is amber: "Product owner is waiting for your
   answer".
2. **`2-approve-the-spec.png`:** the answer → a short PO reply → the spec as a
   `KitPlanCard`: criteria as "done when" rows, each saying how it will be
   checked ("Checked by a test", "Checked by running it", "You try it before
   it goes live"), a summary naming what will not change, primary **Start building the booking page**, secondary **Change
   something** (approval point A1).
3. **`3-team-at-work.png`:** the architect's approach and its one risk → the
   manager's split → `KitPhaseCard` with four tasks (who · state) → the
   reviewer's verdict, citing the tests **the team ran itself** ("See the
   output"). The Now line says who does what
   now and who's next. The composer steers.
4. **`4-fix-and-ship.png`:** manager: sent back, fix 1 of 2 → developer:
   fixed, added a test → reviewer: "Checked again from scratch: all met" →
   manager: "Ran all the tests on the merged work: 31 passed" (the
   integration check, rules only) → **Manager (Opus 5.5) · final check** →
   `KitPromoteCard` titled with the milestone: how each criterion was checked,
   **Try this** in the summary ("book Saturday 10:00 on your phone…"), and the
   button **Put the booking page on main** (approval point A2).
5. **`5-team-roles.png`:** the "Who does what" sheet over the conversation.
   See below.
6. **`6-fix-it-first.png`:** the integration check on `dev` fails (two tasks
   together send two messages) → the manager's final check writes **one** fix
   task → the ship card leads with **Fix it first** and offers **Put it on main
   anyway** as a quiet secondary, saying in one line what "anyway" would
   mean.

## What the renders taught (fix in the build slices)

- **Spec rows:** `KitTeamItem` in the empty state draws an inbox glyph, which
  reads oddly for "done when" rows that haven't been checked yet. The spec card
  needs plain bullets: a small `KitPlanCard` option in S2.
- **Ship card:** the title names the milestone ("Milestone 1 · Booking works")
  and the button names the action and target ("Put the booking page on
  main"). Repeating "Put … on main" in both read twice.
- **Chats open at the newest turn:** the ship card must be fully visible above
  the composer when the conversation opens (render 4 is scrolled to the end).
- **Speaker line:** an inline icon at 20 dp plus the role name in label style is
  quiet enough. Five speakers in one screen (render 4) still scan well because
  each turn is one or two lines.
- **Now line:** naming the role ("Developer is building…", "Product owner is
  waiting…") was enough to answer "who's doing what". No agent strip or extra
  row was needed.

## "Who does what" sheet (owner requirement: an agent and a model per role)

**Where it opens:**
- The task's ⋯ menu, as "Who does what" under *Go to*.
- The project's settings (same rows).
- The A1 spec card's team line ("Team: Claude Code plans, OpenCode builds ·
  Change"), so the choice is offered at the moment it matters.

It is a sheet, not a page, and adds no level.

**Rows, in cycle order** (obvious options first, one row per role):

| Row | Shows | Tap |
|---|---|---|
| Product owner | `Claude Code · Opus 5.5` | Opens the existing agent choice (`KitAgentChoice` cards from New chat) and then the existing model picker (`pickers.dart`) for that agent |
| Architect | `Claude Code · Opus 5.5` | Same |
| Developers | `OpenCode · DeepSeek V4 · free` | Same |
| Same for all developers | A switch, on by default. Off: one row per persona (Frontend, Backend, Tester…) | — |
| Reviewer | `Codex · GPT-6 Sol` and, when needed, a second line: "Needs Codex sign-in. Until then: OpenCode · Kimi K3" | Same; the note's fix ("Sign in") is offered in the picker |
| Manager | "When stuck: Claude Code · Opus 5.5", plus "Rules first; the model at most 2 times a task" | The same pickers, with "Never use a model" as the first choice and the cap (0–3) under it |
| Spending limit | The project's limit, and how the chosen models cost ("paid per use", "free", "uses your Claude plan", "unmetered") | The existing budget editor |
| Use these for new projects | What new projects use now | Saves these as the phone-wide defaults |

**Honest notes** (one line in the row, plain words, no raw errors):
- "Needs Claude Code sign-in"
- "Gemini isn't installed on this phone"
- "This model is no longer offered"
- "Can't be kept read-only, so it can't review"
- "Same as the developers: the reviewer checks more when it's different" (a
  warning, not a block)
- Before the engine release: "Claude Code and Codex come with the next app
  update" on the architect, developer and reviewer rows (only OpenCode models
  until S6)

**Defaults chosen for you:**
- Product owner and architect: the strongest signed-in model.
- Developers: the team model, or the best free model when no paid provider is
  set up.
- Reviewer: a different agent or model family than the developers when one is
  available.
- Manager: **"When stuck: the strongest signed-in model, at most 2 times per
  task"** (owner decision, 2026-11-01).
- Reviewer: a **different model family** from the developers by default
  (research R2).

The subtitle says "picked for you, change any".

**Render notes (`5-team-roles.png`):**
- `KitSwitchRow` has no leading column, so "Same for all developers" starts at
  the gutter, not under the role names. The build should indent it to the
  text column, as a sub-option of Developers: a `leadingSpacer` option on the
  kit row.
- The sign-in note should use the attention tone for its first words ("Needs
  Codex sign-in") through the kit row's supporting tone. The render keeps it
  neutral.
- The manager's long explanation was cut off at three lines. Keep the row to
  two short lines, and put the trigger list in the picker.

## Motion and accessibility (for the build slices)

- New role turns enter with the existing reveal (150–250 ms, reduced motion
  respected). The Now line cross-fades its words, and the speaker line doesn't
  animate on its own.
- The speaker line is read out once per turn as "Product owner says: …". Cards
  read their title, then their action, which names its target.
- Answer rows are 48 dp or taller. Role icons are decorative (the role name is
  text).
- At 200 % text the speaker line wraps (name, then age on the next line), and
  cards keep their kit width caps.
- Arabic: role names are l10n strings, and icons don't mirror (Phosphor set
  rules).

## What the UI will not have

No role avatars or org chart, no per-role colour, no role filter, no "team
dashboard", no plan card by default, no separate "accept milestone" tap, no
queue/steer choice, no cost per role, no role picker outside "Who does what",
and no hidden model calls (every escalation is labelled).
