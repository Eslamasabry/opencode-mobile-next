# AI Team redesign: the person's words, not the engine's (2026-09-24)

## Scope

- **Spec:** [`docs/design/aiteam-redesign-2026-09-24.md`](../../design/aiteam-redesign-2026-09-24.md).
- **Why:** the owner's verdict on the kit-migrated AI Team home and Work tab card was "This is very ugly".
  - The kit had made the screens consistent, not good.
  - They still showed Gas City's insides (convoy, formula, city, 127.0.0.1, versions).
  - They gave a three-task team the controls of a dashboard.
- **What this changes:** presentation and copy only. The gateway, the data paths, the two-step confirmations and the capability gating are unchanged.

### Files

| File | What changed |
|---|---|
| `lib/ui/widgets/team_vocabulary.dart` (new) | One vocabulary for every AI Team surface, with helpers for the pieces below. <br>**Task words:** a run is a "task", its items are "steps". <br>**Stages:** Waiting, Working, Reviewing, Done. <br>**Host phrase:** "On this phone" / "On {computer}", plus "· Paused" or "· Not answering". <br>**Agent roles:** Worker, Reviewer (merges), Planner, Supervisor, Helper. <br>**Also:** the glyph for each gate kind, and how the gates are ordered. |
| `lib/ui/screens/team/team_home_screen.dart` | **Header:** title "AI Team" with the host phrase under it. The address, version and city moved behind the info button. <br>**Order:** "Needs you" first, then one "Tasks" list (running, then waiting, then "Done today", folded after 3). <br>**Agents:** one "3 agents · 1 working" row that opens the agents list; there are no tabs. <br>**Filters and search:** only when there are more than 8 tasks, behind a search icon in the top bar. <br>**Primary:** "Give the team a task". |
| `lib/ui/screens/team/team_needs_you.dart` (new) | **One question** is a `KitRequestCard` with its choices answered in place, as in chat. <br>**Several questions** are a short list of rows. <br>**Where it's used:** the home and the task's Overview. |
| `lib/ui/screens/team/team_agents_screen.dart` (new) | The agents list, which used to be the home's "Agents" tab. Agents are named by role, with the engine name under Technical details. |
| `lib/ui/widgets/team_card.dart` (Work tab) | One section:<br>• a header row ("AI Team · On dev-pc ›"; the whole row opens the team);<br>• under it, at most three rows: the question with "Answer", and up to two running tasks;<br>• when nothing is running, a single line, "Nothing running". <br>**Removed:** the percentage headline, the segmented bar, the 7-step strip, the agent dots and their pulse, "N completed runs", and Refresh. |
| `lib/ui/screens/team/run_screen.dart` | **Overview:** title, one status line ("Working · 1 of 5 steps done · 3 h 12 min"), a 4-stage line, "Needs you", then the steps as rows. <br>**Tabs:** "Work" is renamed "Steps". <br>**Details:** counts, cost and usage are under the Details row. |
| `lib/ui/widgets/team_agent_row.dart`, `team_technical_details.dart` | **Agent rows:** the role and what the agent is doing. `ctx 63%` is now only on the agent's own screen header. <br>**Technical details:** the host-kind disclaimer (TEAM-206: laptop, WSL, etc.) moved here from the card. |
| `lib/ui/screens/team/start_run_sheet.dart` | "Start a run" becomes "Give the team a task". "Planning… (Mayor)" becomes "Planning the steps…". The actions are pinned below the scrolling form (bug 1 below). |
| `lib/ui/kit/kit_task_mark.dart` (new, exported) | The one leading status mark for a task row (`KitTaskState`: waiting, working, done, failed, needsYou, stopped). |

### Words (en; ar updated to match)

| Before | After |
|---|---|
| AI Team · Gas City | AI Team |
| 127.0.0.1 · This phone · Gas City 1.4.1 · city phone · controls | On this phone |
| Batch · convoy · 1 of 5 done / Run · formula mol-upgrade | Working · 1 of 5 steps done |
| Start a run | Give the team a task |
| Planning… (Mayor) | Planning the steps… |
| Waiting for an agent / Waiting for merge | Waiting for a worker / Reviewing |
| Waiting (needs input) | Waiting for you |
| Completed | Done |
| No recent runs. / Start runs from the host for now. | No recent tasks / Start tasks on the computer for now. |
| Work (run tab) / "Waits on 2 items" | Steps / "Waits on 2 steps" |

The engine words remain only in the run's Technical details sheet: the glossary rule allows exactly `teamUiRunTermBatch` ("Task · convoy"), `teamUiRunTermFormula` and `teamUiRunLabelFormula`.

### Decisions

- **Card row budget:** the spec's "at most three rows" does not count the header row. The header is the tap target that opens the team, so the budget is the header plus three rows.
- **Timeline stays a tab.** It is still the only place the events of a finished task are listed. Folding it into Details would hide them one level deeper.

## Builds

- **Branch:** `ds/aiteam-redesign`.
- **Commits:** `710f8ab6`..`c83ff392`, then this record, on top of `51a775cf`.
- **APK:** none built for this record.

## Devices

None. This record is tests, goldens and renders only. On-device proof comes with the next integrated APK run (issue #87 plan).

## Runs

| # | Check | Expected | Actual |
|---|---|---|---|
| 1 | `test/ui_glossary_test.dart` engine-word rule on the old strings (`tests-without-fix.txt`) | The old copy fails: `teamUiCardTitle`, `teamUiHomeHostChip`, `teamUiHomeRunKind*`, `teamUiCardRunTerm*`, `teamUiCardCity` | FAIL on `51a775cf` as expected (9 strings); PASS after |
| 2 | `test/team_redesign_test.dart` | No engine words on the home, rows, card or Overview (computer and phone); Needs you first; 3 tasks have no filters, search or segments; more than 8 tasks put search behind the top bar; the card has at most 3 rows under its header and no `%`; agents are named by role | PASS (failed on `51a775cf`, see `tests-without-fix.txt`) |
| 3 | Bug 1 — the task sheet's Send off screen at 400×900 (`team_controls_test`) | Send is visible and tappable | FAIL before `96ee163d` (taps missed); PASS after. Three tests passed again without changing them |
| 4 | Bug 2 — a refused answer on the Needs-you block (`team_gate_answer_test` "home Needs you: the chip and the row removal") | The block shows the receipt for a refused answer as well as an accepted one | FAIL with the fix reverted (`team-home-gate-req-1-receipt` not found); PASS with it (96 tests) |
| 5 | Team suites and goldens, `-j 3`: every `test/*team*_test.dart`, `run_result*`, `work_tab_team_sessions`, `design_standard`, `test/goldens/team_*_golden_test.dart` | All pass | PASS, +823 −0 after `f19227e7`..`c83ff392` (first run on `297f0394`: 26 failures in five files the redesign had not updated; see "Suite result") |
| 6 | Before/after renders, `tool/capture/aiteam_redesign_test.dart` | 8 scenes each at 412×915, dark | Regenerated on `297f0394`; byte-identical to the committed after-renders |
| 7 | Bug 3 — the run Overview's 4-stage line at 320 dp, 2.5× text (`team_policy_test` Arabic case, `team_run_layout_test` ltr/rtl × en/ar) | The line fits | FAIL with the fix reverted: "A RenderFlex overflowed by 92 pixels on the right" (Row `team-run-stage-waiting`), then 27 and 124 px in Arabic and 30 px in English; PASS after `f19227e7` (the stage word wraps under its mark) |

### Tests changed, and why

Every changed expectation was checked against the spec first (test-audit rule: a failing test may be a product bug). Three were product bugs (runs 3, 4 and 7). The rest assert the new design:

- **Home tests** (`team_home_test`, `team_home_layout_test`, `team_home_stable_layout_test`):
  - the tabs and segments are gone, so the tests reach agents through the agents row;
  - "Needs you" is found by its block, not by a segment count;
  - filters are tested at more than 8 tasks.
- **Card tests** (`team_card_test`, `team_card_layout_test`):
  - the dots, pulse, percentage and Refresh were removed, and their tests went with them;
  - the disclaimer tests moved to Technical details (TEAM-206 still covered, en and ar).
- **Agent and cycle tests** (`team_agent_screen_test`, `team_cycle_test`, `team_merge_test`, `team_design_standard_test`):
  - `ctx N%` and the engine names are asserted absent from rows;
  - the merge section and cycle line are found on the new Overview;
  - "Done · merged" still holds for a finished task.
- **`test/design_standard_test.dart`:** `team_agents_screen.dart`, `team_needs_you.dart` and `team_agent_row.dart` joined the migrated list, and the `team_agents` goldens were added.

### Suite result

The first `-j 3` run on `297f0394` (+787 −26) failed in five files the redesign
had not updated. Each failure was checked for a product bug before any
expectation changed:

- **Product bug (1 of the 26):** the 4-stage line overflowed at 320 dp and 2.5×
  text, in Arabic and in English (run 7). Fixed in `f19227e7`; goldens
  unchanged.
- **Intended changes (25):** nothing else was missing. The supervision and
  boundaries block, the step counts and the usage figure are under the
  Overview's collapsed Details row (the spec's "Details"); TEAM-117's "since
  hand-off" timing, the blocked cause (now on the step's own row, "Blocked ·
  Tests failed: 2 of 18") and the "Answer this on the computer…" line for a
  watch-only host are all still shown. The tests now open Details or read the
  step rows (`f0e32c69`):
  - `team_policy_test` (4): the policy block asserted under Details, after
    the counts, and absent while Details is closed.
  - `team_run_layout_test` (4): walks the new Overview, opens Details, and
    finds "Task · convoy" only in Technical details.
  - `team_run_screen_test` (12): new order and words ("Waiting for a worker",
    "Reviewing", "This task is no longer on the host"); Needs you titled
    "Decision" instead of the agent's engine name; a new case for a
    watch-only host.
  - `team_usage_test` (5): usage under Details; the two "absent" cases now
    open Details too (they had passed only because it was closed).
  - `team_work_tab_test` (1): "Waits on 1 step".

Final: every `test/*team*_test.dart`, `run_result*`, `work_tab_team_sessions`,
`design_standard`, `ui_glossary` and `test/goldens/team_*_golden_test.dart`,
`-j 3`: **+823 −0**. `flutter analyze lib test`: no issues.

## Evidence

- **Before renders:** `before-1..9-*.png`, captured on `51a775cf`. `before-9-work-tab.png` is the whole Work tab with the old card.
- **After renders:** `after-1..8-*.png`.
  - `after-1-home-loaded`: the question first, then Tasks, then Done today and the agents row.
  - `after-4-card`: the header plus 2 rows.
  - `after-6-run-overview`: the 4-stage line and the steps.
- **`tests-without-fix.txt`:** the new tests failing on the old code.
- **Goldens:** `test/goldens/team_home_*`, `team_card_*`, `team_run_*` and `team_agents_*`, in computer and phone variants.

## How to reproduce

```bash
F=~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
$F test --concurrency=1 --dart-define=AITEAM_CAPTURE=before \
  tool/capture/aiteam_redesign_test.dart            # on 51a775cf
$F test --concurrency=1 tool/capture/aiteam_redesign_test.dart
$F test -j 3 test/team_redesign_test.dart test/ui_glossary_test.dart
```

## NOT proven

- **Not on a device:** no phone or emulator run and no video. The redesign has not yet been in front of the owner.
- **Arabic:** RTL layouts are covered only where the existing goldens render Arabic.
- **Large text:** only through the existing `team_home_stable_layout_test` text scales.
