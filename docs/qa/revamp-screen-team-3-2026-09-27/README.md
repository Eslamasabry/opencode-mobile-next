# revamp-screen-team-3: Revamp team (4 files) (2026-09-27)

## 1. Scope

- Unit: `screen-team-3` (wave 2b, screen-revamp). Finish line: `policy_block.dart`, `team_agents_screen.dart`, `team_needs_you.dart` and `work_sheet.dart` construct only kit parts (G1, G7, G16, G17 at 0; G21 at 0 except one retired helper, see contract problems), the agents list is one list by urgency with Wake and freshness, and each page is handled by its map proposal. Non-goal: the work sheet's new "Step · task" structure, the worker row opening the conversation (slice-P3.6), any new gateway call, controller field or persistence.
- Files changed: `lib/ui/screens/team/{policy_block,team_agents_screen,team_needs_you,work_sheet}.dart`; `lib/l10n/app_en.arb` (11 keys, English only: `teamAgentsChecked`, `teamAgentsAsleep`, `teamAgentsAsleepHint`, `teamAgentsPaused`, `teamAgentsPausedHint`, `teamAgentsWake`, `teamWorkSheetMissingTitle`, `teamWorkSheetMissingBody`, `teamWorkSheetNotOnHost`, `teamWorkSheetOpenStepConversation`, `teamWorkSheetOpenAgentConversation`) + generated `app_localizations*.dart`; owned tests `test/team_work_sheet_test.dart`, `test/team_cycle_test.dart`; new `test/revamp/screen_team_3_test.dart`, `test/revamp/screen_team_3_golden_test.dart`, `test/revamp/screen_team_3_fixtures.dart` (+ 8 goldens under `test/revamp/goldens/team_agents*`, `team_work_sheet*`).
- Pages (map ids): team-agents, work-sheet. The needs-you block (`team_needs_you.dart`) and policy block (`policy_block.dart`) are embedded parts of team-home / team-run pages owned by other units; they got a kit-only rebuild with no behaviour change.
- Specs followed: STANDARDS.md §1, MAP-1, KIT-1, KIT-2, KIT-16, KIT-27, KIT-32, KIT-33, LOOK-12, LOOK-14, LOOK-15, LOOK-24, LAY-8, STATE-20, DATA-11 (Wake is a reversible act: neither undo nor confirm); kit-api KitTopBar, KitScreen, KitRow/KitRowGroup, KitTaskMark, KitSheet, KitDetailsFold, KitCodeBlock, KitChip, KitText, KitIcon, KitSince; visual language §5 (rows on a surface1 panel, sheet frame), owner rules 2026-09-27 (no state sections; actions name their object; Arabic dropped).
- Contract problems (PROC-20):
  - Task text says `docs/qa/revamp-<unit id>/README.md`; EVID-1 says `…-<YYYY-MM-DD>/`. The rulebook form was used.
  - Task text asks for real Arabic in `app_ar.arb` (R04); the owner decision of 2026-09-27 drops Arabic. New keys are in `app_en.arb` only.
  - Attribution trailer: the task text names "Claude Opus 5.5 (1M context)"; the session's attribution instruction names "Claude Opus 5.5". The session's form was used.
  - G21 `.toUpperCase()` stays at 1 in `work_sheet.dart`: `workOwnerInitial` builds the monogram letter for `run_screen.dart`'s hand-built owner glyph (not this unit's file). It is marked `/// Retired by screen-team-3: use KitAvatar(name:)`; the count goes to 0 when the run screen uses `KitAvatar`, which draws its own initials. Removing it here would break `run_screen.dart`.
  - Map team-agents lists "expand suspended" as an action; the owner rule of 2026-09-27 (no state sections) is later and wins: the folded group is gone and asleep/paused agents are the last rows of the one list.
  - `KitTopBar` has no subtitle key, so `ValueKey('team-agents-host')` is gone.
- New kit parts (KIT-3): none.
- Moved or removed (owner rule "rethink, not just restyle"):
  - Agents: the "Suspended on the host (n)" folded group row is removed; its agents are rows of the one list, ordered last.
  - Agents: the stale `Opacity(.6)` dimming is removed (the status line already says the data is old; LOOK-14).
  - Work sheet: branch, worktree and merge target moved from a "Code" section above the fold into the one Technical details fold (KIT-33); the "Code" heading is gone.
  - Work sheet: the title moved into the kit sheet header (with the term as its subtitle); the body starts with the cycle strip.
  - Work sheet: "Open session" renamed "Open this step's conversation" and "Open conversation" renamed "Open <agent>'s conversation" (actions name what they act on; glossary: conversation, not session).
- Map items (EVID-11):
  - team-agents (fix):
    - actionsMissing "wake/start a suspended agent from the list" → done: "Wake <name>" on a paused agent's row (only when `capabilities.controlAgent`), sends `controlAgent(id, resume)` and shows the receipt — `screen_team_3_test.dart` "Wake slit asks the host to resume that agent, then shows the receipt", "no Wake when the host keeps agent controls to itself". Asleep agents (stopped, not suspended) get no Wake: they wake when there is work (automation-first).
    - statesMissing "freshness ('checked 20 s ago')" → done: top bar subtitle "On dev-pc · checked less than a minute ago" (kit age words, rebuilt by `KitSince` ticks) — `screen_team_3_test.dart` "the top bar says when the list was checked".
    - infoMissing "agent name when two share a role" → done: "furiosa · Worker" / "nux · Worker" — "rows name the agent and say its state in words". "which agents are pool vs fixed" → deferred to slice-P3.6 (no pool word in the glossary yet; the agent page shows the pool).
    - rationale "one mark style", "'Asleep' not 'Suspended on the host'" → done: `KitTaskMark` on every row; Asleep / Paused words — same test, goldens `team_agents_*`.
    - rationale "a row opens the worker's conversation (watching)" → deferred to slice-P3.6 (its finish line); the row still opens the agent page.
    - elements "four leading icon styles at one level" → done (one `KitTaskMark` style).
  - work-sheet (redesign → kit-only rebuild of today's layout; the "Step · task" structure with one primary per state is deferred: no owning slice in `work-units.json` (G30 gap, coordinator)):
    - statesMissing "missing as a designed state" → done: sheet titled "Work item gone" with a `KitStateView` that says what may have happened and what to do — `screen_team_3_test.dart` "a work item the host no longer lists is a designed state", `team_work_sheet_test.dart` "an item the host no longer lists says so". "failed step" → deferred with the redesign.
    - actionsMissing "a primary per state: Watch the worker / Open the blocker / Answer" → deferred with the redesign (today's secondary button kept, now naming the agent).
    - infoMissing "the task it belongs to", "the worker's conversation link" → deferred with the redesign; the conversation button exists when an agent works on the item.
    - elements: ActionChip → `KitRow`s on a `KitRowGroup` (with the other item's state word, and "No longer listed" for an item the host dropped, not a dead chip); ExpansionTile → `KitDetailsFold`; branch/worktree above Details → inside the fold — `team_work_sheet_test.dart` "oc-loy: title, term, state, owner, description, code", "dependency and blocking rows open the other sheet", "Technical details lists the raw fields with the ids LTR".
    - verticals "stages contradict status" → deferred with the redesign (the cycle strip is `lib/ui/widgets/team_cycle_strip.dart`, not this unit's file; visible in golden `team_work_sheet_*`: "Pushed" beside "Working").
- States per page (STATE-20): team-agents loaded (goldens `team_agents[_1280x800]_{dark,light}`; `screen_team_3_test.dart`), empty (test "empty only when the host lists no agent at all"), loading / error / not answering / stale (unchanged shared `teamScreenState` / `teamStatusLine` from `team_states.dart`), Wake sending → sent → not confirmed (test "Wake slit …"). work-sheet loaded (goldens `team_work_sheet[_1280x800]_{dark,light}`; `team_work_sheet_test.dart`), missing (two tests above), output + validation + closed (test "output excerpt, validation and a closed stamp render").
- Deferred states (STATE-21): work-sheet "failed step" → needs the redesign's per-state primary, no owner in work-units.json.

## 2. Builds

- Branch `revamp/screen-team-3`, base `9007257d` (feat/phone-setup-v2), code head `ac649a70`.
- No APK (unit agents do not build).

## 3. Devices

None: tests, goldens and renders only. Device proof is coordinator work at the wave 2b checkpoint.

## 4. Runs

| # | Step | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | Fixes only: failing-first | n/a: Wake, freshness, the one list and the missing state are new behaviour with new tests | n/a | PASS |
| 2 | `test/revamp/screen_team_3_test.dart` | passes | 8 passed | PASS |
| 3 | `test/revamp/screen_team_3_golden_test.dart --update-goldens`, then looked at | 8 images | 8 passed | PASS |
| 4 | `test/team_work_sheet_test.dart` | passes | 7 passed | PASS |
| 5 | `test/team_cycle_test.dart` | passes | 32 passed | PASS |
| 6 | `KIT_RATCHET_WRITE=1 test/kit_ratchet_test.dart`, read, then `git checkout test/kit_ratchet_baseline.json` | no entry for the four files | one entry: `work_sheet.dart` G21 `.toUpperCase()` 1 (contract problems) | PASS with the recorded exception |
| 7 | `test/ui_glossary_test.dart` | no offender from this unit's keys | none from this unit (after rewording three "host" sentences); 2 failures from other units' keys (G11 confirm labels in `development_services_screen.dart`, `managed_workspaces_screen.dart`, `project_folder_actions.dart`, `personal_settings_screens.dart`; G28 `kitCap*`, `teamMergeFailedNext`) | recorded |
| 8 | `test/l10n_coverage_test.dart` | passes | passed | PASS |
| 9 | `flutter analyze lib test` | no issue in changed paths | 5 infos, all in other units' test files (`kit_tappable_golden_test.dart`, `screen_shell_1_*`) | PASS |

## 5. Evidence

- Rule evidence (PROC-31):

  | Rule | Test (`file` + `--plain-name`) or golden | Output |
  |---|---|---|
  | Owner rule no state sections | `test/revamp/screen_team_3_test.dart` "one list, most urgent first; no group by state" | step 2 |
  | STATE-9 (state in words), LOOK-24 | `screen_team_3_test.dart` "rows name the agent and say its state in words" | step 2 |
  | DATA-11, STATE-10 | `screen_team_3_test.dart` "Wake slit asks the host to resume that agent, then shows the receipt" | step 2 |
  | STATE-12 (capability) | `screen_team_3_test.dart` "no Wake when the host keeps agent controls to itself" | step 2 |
  | STATE-20 freshness | `screen_team_3_test.dart` "the top bar says when the list was checked" | step 2 |
  | KIT-33, KIT-32 | `test/team_work_sheet_test.dart` "oc-loy: title, term, state, owner, description, code", "Technical details lists the raw fields with the ids LTR" | step 4 |
  | KIT-16 | `team_work_sheet_test.dart` "dependency and blocking rows open the other sheet" | step 4 |
  | KIT-1, G1, G7, G16, G17, G21 | `test/kit_ratchet_test.dart` (write mode, read, reverted) | step 6 |

- Changed test expectations (TEST-19):
  - `team_work_sheet_test.dart`: texts are read from `KitText` (not `Text`); the title and term are in the kit sheet header; branch, worktree and target are read after opening Technical details and are `KitText.mono`; the raw `metadata.gc.session_id` is no longer listed twice (the fold shows each value once); output is matched inside the `KitCodeBlock`; "Open session" reads "Open this step's conversation"; the missing item shows the "Work item gone" sheet (the sheet key exists, the body is the state).
  - `team_cycle_test.dart` "the Work sheet shows the full strip at the top": the title is the sheet header, so `team-work-sheet-title` no longer exists in the body.
- Goldens (each opened and looked at): `test/revamp/goldens/team_agents_{dark,light}.png`, `team_agents_1280x800_{dark,light}.png`, `team_work_sheet_{dark,light}.png`, `team_work_sheet_1280x800_{dark,light}.png`. Approved renders (EVID-12): `docs/design/visual-language-2026-09-26/Settings.png` for the agents list (rows on one surface1 panel with hairlines, muted supporting line, headline top bar; difference: the leading slot is the task mark, not an icon tile, per the map's "one mark style" and the team task rows); `Confirm.png` for the sheet frame (grabber, icon tile, start-aligned title, close at the end); `Desktop.png` for the 1280x800 shots (list centred at the list width; the sheet as a centred panel).
- Before and after: `before-team-agents.png` (base `test/goldens/team_agents_dark.png`) → `after-team-agents.png`, `after-team-agents-1280x800.png`; `before-team-work-sheet.png` (base `test/goldens/team_work_sheet_dark.png`) → `after-team-work-sheet.png`, `after-team-work-sheet-1280x800.png`. The before and after use different fixtures (the base goldens are integrator-owned and were not re-rendered).
- Accessibility: every row is a `KitRow` (48 dp, focus ring, Enter/Space); the mark announces its word (`KitTaskMark`), and the state is also in the supporting text; "Wake <name>" is a labelled `KitButton` with its working state; the dependency rows are one Tab stop each with a chevron, and an unlisted item's row is disabled with its reason in words; the fold is the kit's 48 dp disclosure; section headings carry header semantics.
- Privacy and security: no credentials, links, notifications or stored keys changed; technical values pass through `KitDetailsFold`'s redaction.
- Migration: n/a: no stored format changed.

## 6. How to reproduce

```bash
F=~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
$F test -j 1 test/revamp/screen_team_3_test.dart test/revamp/screen_team_3_golden_test.dart
$F test -j 1 test/team_work_sheet_test.dart test/team_cycle_test.dart
KIT_RATCHET_WRITE=1 $F test -j 1 test/kit_ratchet_test.dart   # read the four files' entries, then:
git checkout test/kit_ratchet_baseline.json
$F analyze lib test
```

## 7. NOT proven

- Not run on a device or emulator.
- Shared tests outside the unit's write set were not run (owner decision 2026-09-27). Expected to break (integrator, TEST-19 case 1):
  - `test/team_home_test.dart` (lines ~900–952, ~1391–1402), `test/team_agent_screen_test.dart` (~436–470), `test/team_home_layout_test.dart` (~487–495): tap or expect `team-home-suspended-group` / "Suspended on the host (4)" (the group is gone; asleep and paused agents are rows of the one list, ordered last).
  - `test/team_policy_test.dart` (~602–706): `tester.widget<Text>(key('team-run-policy-…'))` — those keys are now on `KitText` (read `.text`).
  - `test/team_run_screen_test.dart` (~832, ~869): `tester.widget<Text>(key('team-run-needs-you-question'))` — now a `KitText`.
  - `test/team_work_tab_test.dart` (~517, ~562), `test/team_work_layout_test.dart` (~431): `team-work-sheet-title` — the title is the kit sheet header (find the text inside `team-work-sheet`).
  - `test/team_controls_test.dart` (~423, ~464): expects no "Open session" text — still true, but the label is now "Open this step's conversation".
  - `test/team_motion_test.dart`, `test/team_redesign_test.dart`, `test/team_design_standard_test.dart`: may rely on the old AppBar, the stale Opacity, or the group row.
  - `test/goldens/team_golden_test.dart` (`team_agents`, `team_home_loaded`, `team_run_overview`) and `test/goldens/team_sheets_golden_test.dart` (`team_work_sheet`): images change (not re-rendered: integrator-owned).
  - `test/design_standard_test.dart` `_migrated`: the new goldens live under `test/revamp/goldens/` (integrator, R10).
- `test/ui_glossary_test.dart` fails on the base for other units' copy (step 7); not this unit's.
- The needs-you block and the policy block have no golden of their own here; the integrator-owned `team_home_loaded` and `team_run_overview` goldens show them.
- The work sheet's output block content sits below the fold in the phone golden; its text is asserted by `team_work_sheet_test.dart` only.
- `test/kit_ratchet_baseline.json`, the `l10n_coverage_test` baseline and `_migrated` are not updated (integrator, R05/R10); the ui-ledger part file was not touched.

## State

| State | Yes/No | Where |
|---|---|---|
| Implemented | Yes | `revamp/screen-team-3` |
| Enabled | Yes | |
| Verified | tests and goldens only | this record |
| Committed | Yes | code head `ac649a70` |
| Deployed | No | |
| Released | No | |
