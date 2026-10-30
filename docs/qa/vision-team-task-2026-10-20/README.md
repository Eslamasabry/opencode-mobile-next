# AI Team task: the gate is a question card, the Now line is the header (vision slice, 2026-10-20)

Branch `feat/vision-team-task` (from int `bc5cd4ae6`). Pick: **B's gate + A's Now line in the
header** (`screen-concepts/b3-team-task/concept-b.png` for the gate, `concept-a.png` for the Now
line). Owner decision (2026-09-26): an AI Team task is a conversation, its workers are its
sub-agents. Adopt: the gate card with Merge into main; the Now line (what, since when, what next).
Adapt: the Now line inside the header's status line. Reject: the workers row, the 60 % bar, "Waiting
for you" in mint, the same state three times. Remove: the Now card, workers row, progress bar, Now
pill.

## 1. Technical-honesty audit (written before building)

The screen is `TeamConversationScreen` (part of the chat library,
`lib/ui/screens/chat/team_conversation_*.dart`) fed by `OrchestrationController`
(`lib/state/orchestration.dart`) over an `OrchestrationGateway`. Which gateway decides what is
real:

| Host | Gateway | Runs, steps, agents, gates | Answer a gate | Message | Merge gate |
|---|---|---|---|---|---|
| Gas City behind the host front (a computer) | `gascity` + front, `OrchestrationCapabilities.gascityFront` | yes | `controlRespond` | `controlMessage` | **yes**: `mergeReadiness` + `OrchestrationMergeGateway` (`/front/merge-readiness/{run}`, approve, merge) |
| Gas City on this phone (Termux / built-in Linux loopback) | `gascity`, `gascityLoopback` | yes | yes | yes | **no**: the front-only merge roles are off; the team's own refinery merges, and the lead line says "Merged" |
| The in-app phone engine (Rust, project-level AI Team) | `inapp/phone_engine_gateway.dart` | projects and lanes, no runs | — | — | no (its merge queue is a project action, not this page) |
| OpenCode servers, Paseo and phone agents (Claude Code, Codex) | not team hosts | — | — | — | — |

Every element of the pick, against that:

| Element of the pick | Source | Verdict |
|---|---|---|
| The task is a conversation; your words are the prompt | `OrchestrationRun.title` + description (or the pending Start-a-task text) | **Real, exists.** Unchanged. |
| Header title | `run.title` | **Real, exists.** |
| Header second line = the Now line: **what** | `teamNow(...)` (`lib/state/team_conversation.dart`): kind (planning, waiting for a worker, starting, working, review, needs you, stalled, finished), the step's title (`TeamNow.workTitle`), the agent on it (`currentWorkId`) | **Build.** "Now: Worker 1 on Sync engine". The worker is named the way the transcript's sub-agent lines name it (role word from the host's pool, numbered when there are several, or the role the task was given to); never an invented persona ("Alex-the-tester", "Designer"). |
| Now line: **since when** | `TeamNow.since`: the dispatch cycle's host times (claimed, handed to merge), the session start, the gate's `createdAt` | **Build** as elapsed minutes ("· 5 min"), from host times only; under a minute nothing is said. |
| Now line: **what next** | `TeamNowNext` from `TeamNowInput.forRun` (worker → work → review → finish) | **Build**, short: "Next: review". "Next: merge" only where a merge gate can follow (`mergeReadiness`); elsewhere the team merges by itself and "Next: the task finishes" is the truth. |
| "Needs you" on the header | `KitTopBar.needsYou`: open gates of this task (+1 while the merge gate waits) | **Build.** The one needs-you exception (vision "Chat with a question": the header mark is fine for needs-you). Not repeated in the Now words. |
| Escalation (taking longer, the team isn't answering, request refused) | `TeamNowLineController` (8 s rule, stall detection, `connected`) | **Kept** as the one honest status line under the header, with its way out (Why?, Try again); it is not a calm-state card any more. |
| A gate = one question card in the conversation, amber question look | `OrchestrationGate` (choice, confirmation, free text, gate bead, run failed) answered through `answerGate` (`controlRespond`) | **Build:** the same `KitRequestCard` question look as the chat's permission card. The gate's prompt is context under the question (Markdown, bounded), never promoted over it. |
| Plan approval | A planner's `confirmation`/`approval` gate | **Real when the host sends one**; it is a gate card like any other. No separate plan UI is invented. |
| Merge gate: "Merge 6 changes into main?" | `MergeReadiness`: `files`, `additions`, `deletions`, `targetBranch`, `lines` (work, tests, build, review, conflicts), `canMerge`, `mergeRequest` | **Build** where `mergeReadiness` + the merge gateway exist (the host front). The question names the action, the count and the branch from the host's document. |
| "6 files, all checks passed" | `readiness.files` / `+additions −deletions`; every `lines[].ok` | **Build** as the card's one supporting line. The checks themselves move under Review changes. |
| Merge into main (primary) | `OrchestrationController.mergeRun` | **Build.** The card is the question, so the separate confirm sheet goes (it said the same thing). A refused merge keeps the host's reason on the card's receipt with Try again. |
| Approve the merge request first | `readiness.mergeRequest` not approved → `approveMergeRequest` | **Build** as the same card's first answer ("Approve and merge" is not offered: the host confirms the approval separately, so it is two honest steps). |
| Review changes (secondary) | `readiness.changes` (path + counts) and the run's work items | **Build:** opens the existing Changes sheet, now with the checks on top. There is no per-file diff (the readiness carries no patch, slice-P3.7a); none is promised. |
| **Cost so far: $0.42** | Gas City reports host-wide `today` / `recent` estimates only; no task-attributed cost, and worker sessions are not an accounting join (`docs/qa/codex-p52-2026-09-27/README.md`). OC2 `step.ended` costs belong to OpenCode sessions the team does not map to a task. | **Left out.** Never relabel the team's day total as a task's cost, never show zero. Task details keeps "Not reported for one task". |
| "Auto-merge is off for this project" | `MergeReadiness.boundaries` ("Never merge without approval") | **Only when it blocks**, as the card's reason; a satisfied boundary is not news. |
| Workers as quiet folded lines in the turn | `teamConversationAgents` → `KitToolRow.agent`, opening the worker's real session (`sessionLink`) | **Real, exists.** The separate workers row (concept A) is **removed** (it was already gone; nothing re-added). |
| Avatars with letters (D / B / T), "Done / Working / Waiting" pills | — | **Rejected** (invented; status shown three times). |
| 60 % progress bar, "3 of 5 steps" in the header | `run.completedSteps/stepCount` exists but is not a percentage of effort | **Removed.** The step count stays where it is, on the folded steps line. |
| Composer "Message the team…" | `controlMessage` → the worker on the task | **Real, exists** (chat library, unchanged here). |
| Stop in the composer (concept A) | `controlCancelRun` | **Exists** as Stop task in the page's menu; the composer is the chat library's (not this slice). |
| "Details" | Task details sheet | **Exists** (menu). |
| Status bar, dock, aurora strength | — | Image artifacts; the aurora is the app's own `KitLiveBackground`. |

Ownership note: the conversation page itself is a part of the chat library, which the chat-fidelity
agent is rebuilding now. This slice changes only the parts outside it (kit, `lib/ui/screens/team/`,
`lib/ui/widgets/`); the few lines the page needs to use them are in
`chat-team-conversation.patch` next to this file, for the coordinator to apply once that library
settles.

## 2. What was built

Committed on this branch (outside the chat library):

- **Kit, variants only** (other screens draw the same; their goldens are unchanged):
  - `KitRequestGo` (`kit_request_answers.dart`, drawn in `kit_request_card.dart`, the sheet's
    primary in `kit_request_sheet.dart`): a gate's one named act with the way to look above it,
    no refusal. Two full-width buttons, the act under the thumb; at 2.0 text the way to look
    scrolls with the words so the question stays readable. Spec: `KitRequestCard.md` §"A gate's
    one act".
  - `KitTopBar.subtitleTail` (`kit_top_bar_tail.dart`): the subtitle's last words ("Next:
    review"), drawn only while the whole line fits; always read aloud. Spec: `KitTopBar.md`.
- **The merge gate** (`lib/ui/screens/team/merge_section.dart`): ready, it is the question card
  (question look, amber edge, "● Needs you · 6 min") "Merge 6 changes into main?", "6 files ·
  +312 / −87 · all 5 checks passed", Review changes, **Merge into main** (one tap: the card is
  the confirmation; the old confirm sheet said the same and is gone). Approve the merge first
  while the host's merge request waits for one. Refusals stay on the card with the host's words.
  Review changes opens the checks first, then the files and steps. Not ready: the calm panel
  (never amber). Merged: one quiet line "Merged into main · 4f9c2a1" that opens the changes
  (the "Merged" heading that repeated the lead's words is gone). `teamMergeWaitsOnYou`,
  `teamMergeGateOnHost` for the header.
- **The Now line in the header** (`lib/ui/widgets/team_now_line_view.dart`):
  `teamTaskHeaderLine` (what · since when, and "Next: …" as the tail), and
  `TeamNowLineView(calmInHeader: true)`, which keeps a line under the header only for a problem
  with a way out or a wait past its usual time (`teamNowShowsUnderHeader`).
- `TeamNeedsYouCard(look:)`: the question look for the conversation; the gate's prompt reads under
  the question (bounded Markdown), never promoted over it.
- Copy: 19 keys in `app_en.arb` / `app_ar.arb`; 5 keys the confirm sheet and the old headings used
  are removed.
- Tests: `test/team_merge_test.dart` (rewritten for the card), `test/team_task_header_test.dart`
  (new), `test/kit/kit_request_card_test.dart` (KitRequestGo), `test/kit/kit_top_bar_test.dart`
  (subtitleTail); goldens `team_merge_*`, `close_team_merged_*` regenerated with `TZ=UTC` and
  looked at; the merge golden pins the wait clock.

**Not committed, for the coordinator: `chat-team-conversation.patch`** (this folder). The page is
`lib/ui/screens/chat/team_conversation_*.dart`, part of the chat library the chat-fidelity agent
owns now. The patch (about 60 lines of code) puts `teamTaskHeaderLine` into the `KitTopBar`
subtitle (the host words stay when the host is paused or not answering, or a question waits),
counts the waiting merge gate in the header's needs-you mark, passes `calmInHeader: true` to the
Now line and `look: KitRequestLook.question` to the gate cards, and updates the four test files
that asserted the old Now block. After applying it, regenerate two golden files with `TZ=UTC
flutter test --update-goldens test/revamp/chat_4_golden_test.dart
test/revamp/slice_p3_5_golden_test.dart` (16 PNGs: the header line and the question look; checked
here). Apply with `git apply docs/qa/vision-team-task-2026-10-20/chat-team-conversation.patch`;
re-check it against the rebuilt chat library first.

## 3. Render and compare (concept B's gate + concept A's header)

Capture: `tool/capture/vision_team_task_test.dart` (412×915 dp at 2×, real fonts, blurred
shadows, the census team world with its clock set to now). The final renders were made **with the
patch applied** (the screen as it will be).

| Pass | Less polished than the concept | Changed |
|---|---|---|
| before | A separate "Now" block under the header; the merge as a green-titled panel with "merge request gc-mr-14" and a six-line checklist; Merge behind a confirm sheet; the gate question card in the old amber-filled look with its prompt promoted over the question. | — |
| 1 | Gate card double-inset (the transcript's gutter plus the card's own); age "39 d 8 h" (fixture clock vs the phone's); the card hidden under the composer; the review run's lead lines said "Merged … The task is done" beside "Ready to merge" (inconsistent scene data); header "Now: Worker 1 on Schema…" named a worker busy with another step. | Card placed by the transcript (`inList` + its own entrance); the world's clock is now; the capture scrolls to the question; the review run stays open with its last step handed to review; who and step come from the same fact. |
| 2 | Header line cut mid-word ("Next: re…"); the main state showed "Approve the merge" (unapproved request) instead of Merge into main. | `KitTopBar.subtitleTail` drops "Next" before the state is cut; main scene uses an approved request, approve-first is its own scene; Merge into main carries the branch glyph. |
| 3 | The merged state said "merged" four times (header, two lead lines, a "Merged" panel with a green title) plus the celebration; Arabic rendered Latin as boxes (capture fonts); at 320 dp / 2× the card's question was hidden by its own large-text scroll. | Merged is one quiet line that opens the changes; Latin fallback font in the capture; at 2.0 text the way to look scrolls with the words so the question stays readable and Merge into main stays in sight. |
| 4 | A wait past its usual time lost its reason and ways out once the calm line moved to the header; "Now: starting Worker" read badly. | The line under the header returns for a wait past its usual time (`teamNowShowsUnderHeader`) and the header then keeps its host words (never twice); "Now: Worker is starting". |

What is still less polished than the concept, and why: the composer (empty, with "Your message
goes to …" above it) is taller than A's one-line composer and sits under the gate card where B
shows no composer; the lead's reply lines are full-size body text with seconds ("· 18:42:25");
the conversation opens at its top rather than at the waiting question. All three are the chat
library's (composer kit, `team_conversation_parts.dart`, the transcript's scroll), out of this
slice's ownership; see the report.

Final renders (`/home/eslam/Storage2/launch-footage/vision-team-task/`): `merge-dark.png`,
`merge-light.png` (main state), `approve-first-dark.png`, `working-dark.png`,
`working-light.png`, `question-dark.png`, `taking-longer-dark.png`, `merged-dark.png`,
`small-320-text2-dark.png`, `arabic-rtl-dark.png`; earlier passes in `before/`, `pass1/`–`pass4/`.

## 4. Checks

- `flutter analyze`: no issues (after `flutter pub get` in `widgetbook/`).
- Ran, all passing on the committed state: `team_merge_test`, `team_task_header_test`,
  `kit/kit_request_card_test`, `kit/kit_top_bar_test` (+ large-text, r1, transparent),
  `team_conversation_screen_test`, `revamp/slice_p5_1_test`, `slice_close_team_test`,
  `team_controls_start_run_test`, `revamp/slice_p4_1c_test`, `kit_ratchet_test`,
  `ui_glossary_test`, `l10n_coverage_test`, `file_size_ratchet_test`; goldens
  `goldens/team_sheets`, `revamp/slice_close_team`, `goldens/kit/kit_request_card`,
  `kit_request_sheet`, `kit_top_bar`, `revamp/chat_4`, `revamp/slice_p3_5`.
- With the patch applied (then reverted): the same conversation tests plus `revamp/slice_p3_5`,
  `p3_6`, `team_cycle`, `team_gate_rows_notifications`, `aiteam_component`,
  `team_agent_chat_render`, `team_agent_screen`, `team_design_standard`, `team_motion`,
  `team_one_page`, `team_redesign`, `team_task_details`, `termux_processes` pass; goldens
  `p5_1`, `p63`, `team_phone_v2`, `p4_1c` unchanged; `chat_4` and `p3_5` regenerated as above.
- No full suite, no emulator.
