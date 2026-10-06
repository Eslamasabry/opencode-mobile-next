# revamp-screen-team-1: Revamp team (3 files) (2026-09-27)

## 1. Scope

- Unit: `screen-team-1` (wave 2b, screen-revamp). Finish line: the three write-set files have a G16 count of zero (and G1, G2, G7, G17, G21, G48 zero), each page is handled by its map proposal with its wave-2 missing states and actions, in the VL look. Non-goal: no gateway call, controller field or persistence format added (STATE-21); no wave-3 slice behaviour (P3.6 messaging in the conversation, P4.1c gate cards).
- Files changed: `lib/ui/screens/team/agent_screen.dart`, `lib/ui/screens/team/gate_sheet.dart`, `lib/ui/screens/team/team_intro_screen.dart`, `lib/l10n/app_en.arb` (+32 keys, English only per the owner's 2026-09-27 decision) and the generated `lib/l10n/app_localizations*.dart`; tests `test/goldens/team_agent_golden_test.dart`, `test/team_usage_test.dart`, new `test/revamp/screen_team_1_test.dart`, `test/revamp/screen_team_1_golden_test.dart`; goldens under `test/goldens/team_agent_*`, `test/goldens/team_gate_*`, `test/revamp/goldens/team_intro_*` (the two `team_agent_controls_*` goldens were deleted with their test case).
- Pages (map ids): gate-sheet, gate-sheet-confirm-sheet, team-agent, team-agent-details-sheet, team-agent-message-sheet, team-agent-reassign-sheet, team-agent-restart-confirm-sheet, team-agent-stop-confirm-sheet, team-intro.
- Specs followed: STANDARDS.md KIT-1, KIT-2, KIT-11, KIT-15, KIT-16, KIT-20, KIT-25, KIT-28, KIT-32, KIT-33, KIT-34, KIT-37, KIT-43, LOOK-4, LOOK-5, LOOK-12, LOOK-14, LOOK-24, DATA-1, DATA-11, MAP-1; kit-v2 §9.1; visual-language §5 (rows, sheets, buttons).
- Contract problems (PROC-20): none blocking. Note: the task text asks for `app_en.arb` **and** `app_ar.arb` (R04), the owner decision of 2026-09-27 drops Arabic; the later owner decision was followed (English only; `app_localizations_ar.dart` falls back to English for the new keys).
- New kit parts (KIT-3): none.
- Moved or removed items (owner rule "rethink, not just restyle"):
  - team-agent: Pause, Nudge, Restart and Stop moved from the page's action block to the top bar overflow, each named after the agent ("Stop fox"), Stop last in the destructive tone. The pinned block keeps only Open conversation / Live output and "Message fox".
  - team-agent: Reassign work… and its sheet removed (owner verdict: "Why manual it's all about automated dev right"; the dispatcher routes ready work, the board's "Start now" is the manual fallback).
  - team-agent: the Technical details sheet (overflow item) removed; its values live once in the page's KitDetailsFold (merge-into:team-agent).
  - team-agent: the current task and the team's usage moved out of Technical details into the status panel (they are not technical); the context number moved to the status row.
  - team-agent: the read-only "Needs you" panel became the pointing KitNeedsYou row that opens the Gate sheet, where it can be answered.
  - gate-sheet: the Gas City term row (`Decision · interaction req-…`) and the raw error text moved under Details ("ids under Details"); the kind and age became the sheet header's one kicker line.
  - gate-sheet: work chips became rows that open the Work sheet; "Restart or reassign" became "Open fox" (the agent page no longer reassigns).
  - team-intro: "Set it up" / "Turn on" renamed to name what they act on ("Set up AI Team on this phone", "Set up AI Team on dev-pc", "Turn on AI Team on dev-pc").
- Map items (EVID-11):
  - team-agent: actionsMissing "undo pause/stop as a snackbar" → Pause: done (`test/revamp/screen_team_1_test.dart` "Pause fox is undone in place"); Stop stays confirmed (DATA-11: confirm or undo, not both). statesMissing "crashed/restarting automatically" → crashed: done (notice "fox stopped unexpectedly" + "Start fox again", golden not captured); "restarting automatically" → no owner (the host reports no auto-restart state). "context recycling in progress (only 'Recycling soon')" → "Recycling soon" now explains what happens; an in-progress state → no owner (not reported). infoMissing "model in plain words above Details" → done (`team_agent_*` goldens, "gpt-x from openai"); "this agent's own cost" → no owner (the host reports team-wide usage only).
  - team-agent-details-sheet: merge-into:team-agent → done (sheet deleted; `screen_team_1_test.dart` "Reassign work is gone" also asserts the menu item is gone).
  - team-agent-message-sheet: merge-into:team-conversation → deferred to slice-P3.6 (`// revamp:` comment above `_message`); actionsMissing "draft kept on dismiss (lost today)" → done (`screen_team_1_test.dart` "survives type, swipe and reopen; cleared once sent"); "undo/cancel a sent message" → no owner (no host inverse); statesMissing "sent/confirmed receipt inside the sheet" → the receipt shows on the page (KitReceipt) after the sheet closes; in-sheet receipt deferred to slice-P3.6.
  - team-agent-reassign-sheet: remove → done (code removed).
  - team-agent-stop-confirm-sheet: infoMissing "the worker by role and task" → done (`screen_team_1_test.dart` "Stop names the worker and its task, then stops it"); "one cancel label" → done (KitConfirmKind.stop: "Keep running"); actionsMissing "start again from the receipt" → the stopped page shows "Start fox again" (notice action).
  - team-agent-restart-confirm-sheet: "tonal, not error, primary" → done (KitConfirmKind.neutral).
  - gate-sheet: actionsMissing "draft kept for free text" → done (`screen_team_1_test.dart` "survives type, close and reopen"); "'Ask the team to fix it' on failure" → done (`screen_team_1_test.dart` "Ask the team to fix it sends the error to the worker"; `team_gate_run_failed_*` goldens); "undo an answer" → no owner (no host inverse). statesMissing "failed with a doable next step from the phone" → done (same). infoMissing "what the team will do after each answer" → done (note "The team carries on as soon as the host confirms your answer."). whenMissing team.control "explains" → done (`screen_team_1_test.dart` "a read-only host: the gate says where to answer, with How").
  - gate-sheet-confirm-sheet: keep → rebuilt on showKitConfirm raised inside the sheet (KIT-16); deny is neutral, destructive approve is destructive, cancel work is the stop tone.
  - team-intro: infoMissing "time the first setup takes" and "memory cost per worker on a phone" → done (KitNotice.cost; `team_intro_phone_*` goldens render the page; the notice is below the fold at 412×915). statesMissing "no census render: layout unreviewed" → done (`test/revamp/goldens/team_intro_*`); "offline while looking" → no owner (TeamDiscovery exposes no failure reason; would need a controller field, STATE-21). Proposal "route the in-app path to phone setup v2's Add tools › AI Team" → kept on Settings › Plugins, whose AI Team section already opens Add tools › AI Team and then turns the team on for the project (routing straight to Add tools would skip the turn-on step).
- States per page (STATE-20): team-agent: loading, error, missing, loaded, stale (status line), no controls (explained), stopped, crashed, did-not-start, recycling → `team_agent_*` goldens (loaded), `screen_team_1_test.dart` (no controls), others code only. gate-sheet: decision, free text, run failed → `team_gate_*` goldens; answered elsewhere, receipts → code; no control → test. team-intro: in-app, computer not found → goldens; termux, unsupported, found → code.
- Deferred states (STATE-21): auto-restart and recycling-in-progress → need host data, no owner; intro offline → needs a discovery error field, no owner.

## 2. Builds

- Branch `revamp/screen-team-1`, base `9007257d` (feat/phone-setup-v2 when the branch was cut), code head `346ca55c`.
- No APK (unit agents do not build).

## 3. Devices

None: tests, goldens and renders only. Device proof is in the wave 2b checkpoint record.

## 4. Runs

| # | Step | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | Fixes only: the fix's test on the base | fails | not captured (owner decision 2026-09-27: do not spend time on tests) | n/a |
| 2 | `test/revamp/screen_team_1_test.dart` | passes | 8 passed | PASS |
| 3 | `test/team_usage_test.dart` | passes | 19 passed | PASS |
| 4 | `test/goldens/team_agent_golden_test.dart` (`--update-goldens`, then looked at) | renders without exceptions | 14 rendered | PASS |
| 5 | `test/revamp/screen_team_1_golden_test.dart` (`--update-goldens`, then looked at) | renders without exceptions | 8 rendered | PASS |
| 6 | `test/kit_ratchet_test.dart` with `KIT_RATCHET_WRITE=1` (baseline restored after) | no entry for the three files in any gate | none in G1, G2, G7, G16, G17, G21, G48 | PASS |
| 7 | `test/kit_ratchet_test.dart` | passes | G17 fails on `lib/ui/widgets/quota_monitor_section.dart` and G21 on several `lib/ui/kit/*` files, all on the base branch, none in this unit's files | FAIL (not this unit) |
| 8 | `dart analyze lib test` | no issues in changed paths | 5 pre-existing infos in other units' tests; none in changed paths | PASS |

## 5. Evidence

- Rule evidence (PROC-31):

  | Rule | Test (`file` + `--plain-name`) or golden | Output |
  |---|---|---|
  | DATA-1 (P7.1) | `test/revamp/screen_team_1_test.dart` "survives type, swipe and reopen; cleared once sent", "survives type, close and reopen" | run 2 |
  | STATE-12 (P7.4) | `test/revamp/screen_team_1_test.dart` "a read-only host: …" (2 tests) | run 2 |
  | DATA-11 | `test/revamp/screen_team_1_test.dart` "Pause fox is undone in place", "Stop names the worker and its task, then stops it" | run 2 |
  | KIT-1, KIT-2 | `test/kit_ratchet_test.dart` write mode | run 6 |

- Changed test expectations (TEST-19): `test/team_usage_test.dart` agent group: the usage line is read from the status panel (`team-agent-usage-value`), not from inside Technical details (KIT-33: only technical values fold); the Arabic agent-usage case was dropped (owner decision 2026-09-27: Arabic dropped). `test/goldens/team_agent_golden_test.dart`: `team_agent_controls` replaced by `team_agent_details`; wide 1280×800 and Gate sheet variants added.
- Goldens changed (each opened and looked at):
  - `test/goldens/team_agent_{dark,light}.png` (+ `_1280x800`): kit status page, needs-you row, pinned two-button block; approved render `docs/design/visual-language-2026-09-26/Main.png` (row panels): same row look, no differences beyond content.
  - `test/goldens/team_agent_details_{dark,light}.png`: the fold open, mono copyable values.
  - `test/goldens/team_gate_{choice,free_text,run_failed}_*.png`: kit sheet frame; approved render `docs/design/visual-language-2026-09-26/Confirm.png` (sheet frame): same grabber, icon tile, start-aligned title; difference: the approved stop confirmation lists consequences, the agent Stop confirmation here has none yet.
  - `test/revamp/goldens/team_intro_{phone,computer}_*.png`: new (no before for the computer case in this unit; the in-app before is `team_intro_phone_dark.png` from `test/goldens`).
- Before and after (EVID-10): `before-team-agent-top.png` (base `test/goldens/team_agent_dark.png`) → `after-team-agent-top.png`, `after-team-agent-top-1280x800.png`; `before-gate-sheet-decision.png`, `before-gate-sheet-free-text.png`, `before-gate-sheet-run-failed.png` (census `docs/qa/screen-census/i2-team-sheets/`) → `after-gate-sheet-decision.png`, `after-gate-sheet-free-text.png`, `after-gate-sheet-run-failed.png`; `before-team-intro-in-app.png` (base `test/goldens/team_intro_phone_dark.png`) → `after-team-intro-in-app.png`.
- Accessibility: every control is a kit part with its label (KitTopBar actions and menu items, KitAction, KitMenuItem); state rows carry mark + word (KitStatusMark); the needs-you row uses KitNeedsYou.row; choice rows are KitChoiceList rows (≥56 dp, semantics group). 200 % text not checked in this unit.
- Privacy and security: the agent and gate Details skip any raw value KitRedact recognises as a secret before building KitTechnicalValue (SEC-4); drafts are stored under `oc.draft.team-agent-message.<agentId>.<profileId>` and `oc.draft.team-gate.<gateId>.<profileId>`, inside the profile deletion sweep's `oc.*.<profileId>` naming. The "Ask the team to fix it" message carries the host's own error text back to the host's worker.
- Migration: n/a (no stored format changed; drafts are new keys).

## 6. How to reproduce

```bash
F=~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
$F test -j 1 test/revamp/screen_team_1_test.dart test/team_usage_test.dart
$F test -j 1 test/goldens/team_agent_golden_test.dart test/revamp/screen_team_1_golden_test.dart
KIT_RATCHET_WRITE=1 $F test -j 1 test/kit_ratchet_test.dart && git diff test/kit_ratchet_baseline.json && git checkout test/kit_ratchet_baseline.json
$F analyze lib test
```

## 7. NOT proven

- Not run on a device or emulator.
- Shared tests were not run (owner decision 2026-09-27). Expected to need the integrator: `test/team_controls_test.dart` (controls moved to the top bar menu, Reassign removed, `team-agent-controls` and `kit-actions-more` gone), `test/team_agent_screen_test.dart` (`team-agent-controls`, `team-agent-details`, `team-agent-work-dependency`, `team-agent-context` gone), `test/team_gate_answer_test.dart` (choices send on tap, no `team-gate-send` for choices, `team-gate-options-hint` and `team-gate-receipt-line` gone), `test/team_activity_test.dart` (gate sheet body), `test/team_discover_test.dart` (renamed intro actions), `test/goldens/team_sheets_golden_test.dart` and `test/goldens/team_discover_golden_test.dart` (gate and intro goldens change).
- The fix-fails-first outputs were not captured.
- 200 % text, the stopped / crashed / did-not-start / recycling notices and the termux and unsupported intro states are not rendered in a golden.

## State

| State | Yes/No | Where |
|---|---|---|
| Implemented | Yes | `revamp/screen-team-1` |
| Enabled | Yes | |
| Verified | tests and goldens only | this record |
| Committed | Yes | code head `346ca55c` |
| Deployed | No | |
| Released | No | |
