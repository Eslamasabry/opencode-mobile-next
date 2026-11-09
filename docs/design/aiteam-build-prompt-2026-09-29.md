# Build prompt: AI Team (project-level, engine-neutral), OpenCode Mobile

Copy everything below the line into the building AI.

---

You are building the **AI Team** feature of **OpenCode Mobile**, a Flutter Android client (also tablet/PC/web) for OpenCode 1 and 2, Codex, and Claude Code/Pi. Repo: `/home/owner/Storage/Code/oc_app`, integration branch `feat/phone-setup-v2` (local only, never push). You work in your own git worktree and branch per slice; the owner's coordinator reviews and merges.

AI Team lets a person run a **whole software project** with several AI agents, across days, repos, servers and branches, from their phone. The phone is the remote control and can also be an engine. The person steers, reviews and approves. The agents plan, build, check and merge.

## 0. Read first (in this order, do not skip)

1. `AGENTS.md`: toolchain, gates, architecture boundaries, security invariants, workflow rules. It is binding.
2. `docs/design/aiteam-requirements-2026-09-29.md`: **the source of truth.** Requirements R-xx with device checks, UI spec per screen, engine contract E-xx, engine rubric, and the owner's decisions (§8).
3. `docs/qa/aiteam-mockups-2026-09-29/`: rendered mockups of the key screens (`1-projects.png` to `6-promote-card.png`, the wide overview, and the full-scroll versions). They are the approved visual direction. Match them, and fix the known issues listed in §6 below.
4. `docs/design/aiteam-native-analysis-2026-09-29.md`: what Gas City really provides, what OpenCode already provides (sub-agents, agents, sessions, permissions, todos, events), the Traycer-style flow, and the phone cost measurements.
5. `docs/design/visual-language-2026-09-26.md` and `docs/ux-system/kit-v2.md` (especially §8 adaptive layout and §9 the widget allowlist). These are the design language and the **kit-only** rule.
6. What exists today:
   - `lib/domain/orchestration_gateway.dart`: the engine-neutral interface the team UI talks to.
   - `lib/orchestration/adapters/{fixture,gascity,none.dart}`: the engine adapters.
   - `lib/state/orchestration.dart` and `lib/state/team_*.dart`: team state, roles (`team_roles.dart`), glance, model, worker-start timing.
   - `lib/ui/screens/team/**`: team home, settings, roles/Agents, role page, start-run sheet, etc.
   - `lib/ui/screens/chat/team_conversation_view.dart`: a task as a chat.
   - `lib/ui/kit/**`: the only allowed UI components.
7. Today's QA records `docs/qa/crit-*-2026-09-29/`. They show what the owner rejected and why. Section 5 summarises them.

## 1. What to build, in this order

**Phase A: engine-neutral UI on a fixture engine.**
- Extend the `OrchestrationGateway` contract to cover the engine contract in the spec (§6: projects, living spec, milestones/phases/tasks, lanes, questions, verification findings, fix/re-check, diffs, merge queue to `dev`, promote `dev → main`, cancel, resume, budgets, since-you-were-away digest).
- Implement it in the **fixture adapter** with realistic, time-evolving fake data (lanes progress, a question appears, findings arrive, a stall happens, a phase fails and recovers).
- Build every screen in the spec against it: Work strip, Projects, Project overview, Spec editor, Board/graph, Timeline, Servers/placement, Task conversation (plan card, phase cards, findings card, merge/promote card, living-edge composer), New project sheet, Quick task sheet, Roles/Agents, Team settings, notifications, and the "Since you were away" digest.
- Everything is reachable and demoable without any real engine.

**Phase B: engine evaluation (report only, no code).**
- Score the candidates in the spec's rubric (§7): native (sub-agent sessions inside one OpenCode server), native plus a small host service, the OpenCode plugins (Advance, opencode-orchestrator, OpenAgentsControl, opencode-spec), Gas City on a computer, and Gas City on the phone (today's baseline).
- Measure on this PC where you can. For the phone, give numbers from the analysis doc and mark them as such.
- Write `docs/design/aiteam-engine-scores-<date>.md` and **stop for the owner's choice.**

**Phase C: the chosen engine behind the same gateway.**
- Only after the owner picks one. The UI from Phase A must not change beyond wiring.

Each phase is a set of slices. One slice is one branch and one worktree, and it ends usable: controller, then UI, then persistence and deletion. Write a one-sentence finish line and one non-goal before editing each slice.

## 2. Non-negotiable engineering rules

**Toolchain**
- Pinned Flutter only: `~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
- `dart format --language-version=3.10`.
- Run heavy commands through `tool/qa/machine_lock.sh test|analyze|build`.
- Never build debug APKs.

**Architecture**
- The UI talks to `lib/domain` gateways only, never `lib/api`, `lib/api2`, or an adapter directly.
- Gate features on capability flags, never on the server flavour.
- Single-owner files are listed in `AGENTS.md`. `chat_screen.dart` and `chat/**` form one library.

**Security**
- Tailnet or loopback only. Nothing on the open internet.
- External URLs only via `openExternalLink`.
- Per-profile storage keys are named `oc.<what>.<profileId>`, so the deletion sweep finds them.
- Never echo provider credentials.
- Nothing public (push, PR, release) without explicit owner approval.
- `main` is protected: only a confirmed "Promote dev → main" writes to it.

**Copy**
- English only, in `lib/l10n/app_en.arb`; run `gen-l10n` once the copy settles.
- Plain words. Never show raw exception, native or server text; that goes under a **Details** fold, redacted.
- Actions name their target and say in one line what happens, but never repeat what the title already says.

**Tests**
- Write behaviour tests with fakes.
- Run your new tests and the existing test files for the files you touch.
- Run the gates: `kit_ratchet`, `golden_harness`, `architecture_boundaries`, `no_raw_error_text`, `redaction`, `l10n_coverage`, `ui_ledger_coverage`, `search_index`, `design_standard`.
- `flutter analyze` must be clean.
- Goldens follow G23:
  - a leading comment containing "Regenerate deliberately, and look at every changed image before committing it";
  - render as Android, with `TargetPlatformVariant.only(TargetPlatform.android)` or the override reset inside the test body;
  - gallery size names only.
- `flutter_animate` is banned. Use the framework's animation APIs.

**Evidence per slice** goes in `docs/qa/<slice>-<date>/README.md`:
- what changed;
- before and after images (phone 412×915 and wide 1280×800, dark and light);
- the tests run;
- what still needs a device.

**UI ledger**
- Update `docs/design/ui-ledger/parts/*.json` and rebuild with `python3 docs/design/ui-ledger/build_ledger.py`.
- `reachedFrom` entries are `{page, element}` objects.

**Commits**
- Every commit message ends with `[skip ci]`.
- Keep commits local.

## 3. Heavy UI guidance: read twice

### 3.1 Kit only, behaviour in the kit's config

- Every visual element comes from `lib/ui/kit/**`. Outside the kit, screens may only use the layout, scroll, builder, semantics and focus allowlist (kit-v2 §9).
- A missing part is **added to the kit**, even if one screen uses it.
  - New parts in this feature: plan card, phase card, findings card, merge/promote card, lane row/strip, project row, milestone row, spec editor blocks, since-you-were-away digest.
  - Each gets a doc in `docs/ux-system/kit-api/` and is registered in the kit manifest.
- **Screens never add padding, width, colour, radius, elevation or text styles.** New visual behaviour is an **option on the kit part with a sensible default**, e.g. `KitBubbleWidth`, `KitStateSize.inline`, `KitTurnLive.pace`. Screens only choose which part and which option, based on the data.
- `test/kit_ratchet_test.dart` counts may only go down.

### 3.2 Layout and spacing

- **One gutter.**
  - The page or transcript applies the gutter once (16 dp on phone, from `KitLayout`/tokens).
  - No child adds its own outer side padding.
  - No box inside a box: nested panels and cards inside cards are forbidden.
  - Code blocks, quotes and tables sit flush with their text column.
- **Vertical rhythm** comes from tokens (`space1`–`space6`). Measure the stack from the screen edge to the text for every new part and record it in the README.
- **One list, ordered by urgency.**
  - "Needs you" first, then running, then waiting, then done and failed.
  - No state section headers when the rows already say their state.
  - Nothing shown twice: if a "Needs you" block exists, the row doesn't also say "needs you". The mockup's project row repeats it; fix that.
- **Adaptive** (kit-v2 §8):
  - Phone: one pane.
  - Tablet: two panes.
  - PC: three panes for the project overview (projects | overview | selected task/lane). The mockup's wide shot has only 2 columns; build 3.
  - Nothing may overflow at 360 dp or at 2.0× text scale.
  - Landscape phone keeps the transcript usable.
- The **pinned bottom action** (New project, Approve and start, Promote dev to main) is the one primary. Secondary actions are neutral tertiary buttons ("Edit plan", "Ask to change", "Not yet").

### 3.3 Colour and meaning

- **One accent: green, for the primary action only.** Cancel, dismiss and secondary actions are neutral.
- **Amber means "needs you"**, and only that: questions, approvals, promote-ready. Never use it for "waiting on a server".
- **Red means destroy, stop or failure**: Stop, Delete, failed phase. "Stop" is always red.
- Severity chips need **distinct tones**: Critical red, Major amber, Minor neutral. Add a tone option to `KitChip`; the mockup's chips all look the same.
- One flat background everywhere: no glow grounds, no gradients behind content.
- Glass only where the kit already uses it: the floating nav and the composer.
- Light and dark must both pass AA contrast. Colour is never the only signal; pair it with a word or icon.

### 3.4 Motion (the owner is strict here)

- **No sharp lines, no fast motion.** Status light is a soft hue and a fade: wide blur, low alpha, long falloff.
- Speed is capped by `KitMotion` constants: at most one lap per 6 s, thinking about one lap per 10 s, speed changes eased over at least 800 ms, colour cross-fades at least 400 ms.
- Use the **living edge** composer (`KitComposer` rail/failure options, already built) for any running agent in a conversation. The status bends the composer's top edge in one smooth, shallow dip, with no shoulders or corners, and the glow's pace follows real events.
- Always honour the app's Motion setting: Full, Calm (fade and breathing only, no travel), and Off/reduced motion (static). Animations stop when off-screen or backgrounded.
- New cards arrive with the kit's `KitArrival`/`KitReveal`. No bouncing or scale-pops.

### 3.5 Words

- **Plain words only.** Banned in user copy:
  - internal names: "polecat", "refinery", "mayor", "bead", "convoy", "rig", "Gas City", "furiosa", or any generated agent name;
  - raw ISO timestamps and session ids;
  - raw tool names: "glob", "bash";
  - "SocketException", "errno", "Unreachable:".
  Internal names may appear only under Details.
- Show agents by **role name**: "Frontend", "Tester", "Reviewer". Use "Worker 2" only when two share a role.
- Time uses the one helper, `lib/domain/relative_age.dart`: "Just now", "5 min ago", "2h ago", "Yesterday", then a short date. Elapsed times read "12 s" or "4 min".
- Every failure says what failed, in plain words, plus a way forward (Retry, Open settings, Start again), with technical text under Details.
- Every waiting state says **what it is waiting for and since when**: "Waiting for your answer · 2 min", "No answer from Home PC yet · 41 s". A wait longer than its usual time says so.
- Headings and buttons don't repeat the page title.

### 3.6 States, per part

For every new part, design and test: empty, loading (skeleton or honest step list, never a fake progress bar), running, needs-you, stalled, failed, done, and stale ("last known" with its age when the engine isn't answering).

**Progress** must come from real signals:
- named steps, each with elapsed time, and a tick when done;
- a determinate bar only when it's counting real steps.

The AI Team start page is the model to copy.

### 3.7 Accessibility

- 48 dp targets. Every icon button is labelled.
- Text fields expose their label as the semantic label.
- Live regions announce **phase changes only**, not every second.
- Reading order: title, then content, then the pinned action.
- Stop, Approve and Promote are reachable by TalkBack and keyboard.
- Honour 2.0× text scale and reduced motion.

### 3.8 The conversation surfaces

- A **task** is a conversation.
  - The person's prompts are compact bubbles that hug their text, growing up to width minus 48 dp.
  - Agent replies are frameless text at the gutter.
  - Steps fold under one work line, and only the last 3 steps show while running.
  - A collapse-all action appears only when something is expanded.
- Messages the **team** injected (agent instructions, nudges) are not user bubbles. They show as a folded "Instructions from the team" row.
- Plan, phase, findings and promote are **kit cards inside the transcript** at the gutter, not nested in another box. The composer's living edge carries the live status. There is no second status line in the transcript.

## 4. Product decisions already made by the owner (do not re-open)

1. **Naming:** a "project" is the initiative; a "repo" is a codebase.
2. **Phone-only projects:** whether a phone-only person runs whole projects is the customer's choice.
3. **Execution mode:** chosen per project on **any host, including the phone**: **Single lane** or **Parallel agents** with a user-set maximum.
   - The app shows the measured cost on that host (memory, battery, heat, chat slowdown), and it **warns, never forbids**.
   - The New project sheet preselects nothing.
4. **Review gates:** at major milestones, and at phases the planner flags as high rework risk (schema/API changes, large refactors, cross-repo changes). The person can flag or unflag any phase.
5. **Branches:** work integrates into `dev`. `main` is protected; only a confirmed **Promote dev → main** reaches it, per milestone or on request, with a receipt.
6. **Budgets:** the person sets them. There's no preset spend. "No limit" is allowed and shown plainly. A project can't start without a budget choice.
7. **Night charging:** for Parallel on the phone at night (after 23:00), "Only while charging" is **on** by default, switchable per project.
8. **Chat first:** the person's own chat always wins on the phone. Team processes run at the lowest CPU/IO priority, and chat latency with a team running must stay within the spec's budget.
9. **Nothing public:** nothing leaves the tailnet, and nothing is published without explicit approval.

## 5. What the owner rejected today (don't repeat it)

- **Status UI:**
  - a status pill floating above the composer ("so ugly");
  - status colours per state (blue, amber, green "Response ready");
  - hard cut lines and gaps in borders;
  - fast comets;
  - a status line in the transcript *and* in the composer.
- **Layout:**
  - boxes inside boxes;
  - double padding that wastes text width;
  - huge empty gaps above the composer.
- **Wording and naming:**
  - internal agent names ("furiosa", "Reviewer (merges)", "Polecat claim and refinery handoff");
  - "New conversation" as a title when the task is known;
  - Gas City's instructions shown as the user's message.
- **Silent or fake states:**
  - a working reply with nothing on screen;
  - "Nothing here yet" while a strip says "Working";
  - fake progress bars;
  - "Try again" as the only way out when the fix is "Start".
- **Speed:** a 2-minute wait with no explanation. Always say which phase is slow and why, e.g. "AI Team is also working on this phone".

## 6. Known issues in the mockups to fix while building

1. Critical/Major/Minor chips need distinct tones.
2. The project row repeats "needs you" beside the Needs you block.
3. The PC overview needs 3 panes.
4. Not drawn yet, to design: the merge queue view, the "Since you were away" digest, the servers/placement view, the spec editor, the board/graph, and the timeline.
5. The New project budget needs a per-day and a total field.

## 7. How to report each slice

Reply with:
- the commit hash;
- what the person can now do, in one sentence;
- the files and kit parts added or changed, with the new kit options;
- the tests run and their results, including gates;
- image paths (before and after, phone and wide, dark and light);
- what needs a device check;
- open questions, each with a recommended default.

Stop and ask only for:
- the engine choice (end of Phase B);
- anything public;
- anything that would remove or merge pages the owner hasn't approved.
