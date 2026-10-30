# Chat while the agent works (vision slice, 2026-10-20)

Finish line: while the agent works, the chat shows only the conversation, one
live line in the turn and the glowing composer. Owner decisions 4 and 8 in
`docs/design/vision-decisions-2026-10-20.md`.

## What changed

- The chips row over the composer (Auto-approve, model, tasks, Background) is gone.
  What it did now lives behind the composer's +, first rows of the "Prompt tools" sheet:
  - **Model** (name of the current model; opens the picker) - 2 taps.
  - **Auto-approve** switch (ask first / approve automatically) - 2 taps, no confirm as before.
  - **Approval settings...** (all three modes incl. Approve everything, record) - 2 taps.
  - **Plan** (`1/4 · current task`; opens the Tasks sheet) - only if the agent wrote a todo list.
  - **Background** (send the running work to the background) - only while it can.
- One muted line under the header while Auto-approve is on: "Auto-approve is on · Turn off"
  (tap Turn off = back to asking first). It uses the chat's existing single status slot, so
  it is not a new bar and is absent when Auto-approve is off. Paused while disconnected:
  "Auto-approve paused". In a sub-agent chat the parent-link line owns the slot and carries
  the words as its second line.
- The live line names the step in hand ("Write · index.html · 21 s") and, only with a todo
  list, a muted "Step 2 of 4" under it (reuses `kitProgressStep`). While the live line shows,
  the running step draws no folded line of its own; after the turn it folds into the one work line.
- Composer placeholder while working and steering is possible: "Steer while it works...".
  The neutral Stop ring is unchanged.
- Fix: a running Read/Edit/Write row showed a bare "Read"/"Edit" when the agent's input said
  `file_path` or `path` (or the file was not yet known); it now names the file.
- Header: no status pill existed (nothing added); the Git branch chip is untouched.

## Removed

`_AutoApprovalIndicator` chip, `_showApprovalModeMenu` menu (its three modes are in Approval
settings), model chip row and its tight-window variant, tasks chip and Background chip in the
strip (`KitTaskChip` stays in the kit, now unused by chat). Agent-features chip and the
"context waiting" label stay in the strip (rare, agent-specific).

## Not done

- "After a user Stop the chats list says Done, should say Stopped": the list row's word is
  produced outside the files this slice owns; not found in the chat or connection code. Left alone.
- Model cycle shortcuts (the model chip's long-press menu) have no row of their own; the
  picker lists recent and favourite models.

## Evidence

Before/after renders of the real chat while working (412x915, one phone screen per image):
`/home/eslam/Storage2/launch-footage/vision-chat/` - `before-{dark,light}.png`,
`after-{dark,light}.png`, `after-plus-{dark,light}.png` (the + sheet). Produced by
`tool/capture/vision_chat_test.dart` (`--dart-define=VISION_PHASE=before` on the old code).

## Tests run (all pass; serial or concurrency 3, `TZ=UTC` for goldens)

- New: `test/vision_chat_working_test.dart` (live line + step, no tool line, steer hint, no chips,
  `file_path` name; fails with the tool-card fix reverted).
- Rewritten for the new surfaces: `approval_mode_chip_test`, `e7_session_approvals_layout_test`,
  `agent_features_sheet_test`, `composer_layout_test`, `chat_context_ring_test`,
  `model_name_from_id_test`, `revamp/slice_p3_3_model_sheet_test`, `calm_chat_disclosure_test`,
  `paseo_real_chat_test`, `voice_composer_test`, `revamp/slice_p10_3_test`,
  `chat_live_events/commands_timeline_tests`, `chat_request_no_duplicate_test`,
  `chat_landscape_request_test`, `revamp/chat_1_test`, `chat_oc2_step_gap_test`,
  `nudge_moments_test` (one pump for a feed timer).
- Goldens regenerated and spot-checked (chip row gone, line under header): 14 golden-bearing
  chat test files, 85 PNGs; `chat_approval_menu_*` replaced by `chat_approval_plus_*`.
- All 93 test files referencing `ChatScreen` pass, plus `kit_ratchet_test`, `file_size_ratchet_test`,
  `ui_glossary_test`, `l10n_*`, `kit_turn_test`, `kit_work_line_test`, `tool_card_test`.
- `flutter analyze lib test tool` clean (the `widgetbook/` errors predate this slice).
- Full suite not run (coordinator runs it at merge).

## Follow-ups (coordinator review)

- The Auto-approve line is now one small muted caption ("Auto-approve is on · Turn off", small
  outline shield, underlined quiet "Turn off" with a 48 dp target, no divider). New
  `KitStatus.quiet` / `KitStatusLine.quiet`; wraps to a second line at large text.
- "Steer while it works…" shows on OpenCode 2 (its gateway advertises `promptSteering`); the capture
  fake lacked it. The capture now uses OC2's capabilities.
- The mic beside Stop is the quiet surface style (was solid mint).
- The + sheet's Model row always shows its value ("Server default" when none is picked).
- "Undo all" on the changes screen is wired (`_showDiff` -> `ReviewWorkspace.onUndoAll`): stages an
  undo from the first saved prompt, files included, gated on `sessionRevert` and staged-revert
  support; the chat then shows its staged-undo line. Test: `test/chat_undo_all_test.dart`
  (fails with the wiring removed). Merged `int/ui-batch-2026-10-14` first; gen-l10n re-run.
- After images re-rendered to the same paths.
