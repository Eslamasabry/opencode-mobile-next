# Scoreboard group F: the composer seam (order 43), 2026-11-07

Base: `native/screens-merge22` (e6a10900e). Branch: `native/s17-composer`. Spec: Flutter at FLUTTER_REF `8302de537`:
`lib/ui/kit/chat/kit_composer.dart:796-808` (one row: `Row(leading, field, trailing)` unless `stacked`),
`lib/ui/screens/chat/chat_composer_region.dart:44-77` (the strip above the box: the approval chip at the start, the
model chip at the end), `lib/ui/screens/chat/approvals_sheet.dart:257-330` (the approval chip).

## What changed

| | Before (screens-merge22) | After (this branch) | Flutter |
|---|---|---|---|
| Message box | two rows: the field, then + · Server default · Ask first · mic | one row: + · Ask OpenCode… · mic; words past one line move the controls under a full-width field | one row |
| Chips | inside the box, model first | a strip above the box: approval chip at the start, model chip at the end | strip above |
| Approval chip | "Ask first" / "Auto-approve on" / "Approve everything", no glyph, caret | shield + "Ask first"; for either automatic mode the kit's selected check + "Auto-approve on" | same |
| Empty box without voice | Send, enabled | Send, disabled (`_Trailing.sendDisabled`) | disabled |
| Room under the box | 16 dp | 8 dp | 8 dp |
| Field target (layout guard) | "Ask OpenCode…" 268 x 24 dp, small-target | 48 dp tall: the field's own node is the target | 48 dp |

Kept: the + tray (in the box, above the row), the translucent blurred face on the chat's veil, the Send ⇄ mic ⇄ Stop
morph on the round target (with words typed during a run Stop moves beside the plus and the words take the full
width, B8). Stop stays on the round target; Flutter 8302de537 puts it at the end of the strip. Owner rule for this run
was "keep the send/stop morph", so it is not moved.

Side by side (Flutter | before | after), all at 412 x 915, density 1, Solid:
[ask dark](score-F/chat_approval_ask_dark.png), [auto light](score-F/chat_approval_auto_light.png),
[everything dark](score-F/chat_approval_everything_dark.png), [confirm dark](score-F/chat_approval_confirm_dark.png),
[plus dark](score-F/chat_approval_plus_dark.png).

## Gate cases (order 43)

`native/feature/chat/src/desktopTest/kotlin/io/github/eslamasabry/oc/feature/chat/rb/o43/ChatApprovalGateTest.kt`: the
real native `ChatScreen` + `BasicComposer` with Flutter's scene inputs (`test/goldens/chat_approval_chip_golden_test.dart`).
They live in `:feature:chat` because the goldens are whole chat screens and `:feature:chat-composer` may not depend on
`:feature:chat`; `rb_gate.py tests` finds `rb/o43` in any module.

| Golden (light + dark) | Rendered by | Note |
|---|---|---|
| `chat_approval_ask` | `approvalAsk` | |
| `chat_approval_auto` | `approvalAuto` | |
| `chat_approval_everything` | `approvalEverything` | |
| `chat_approval_plus` | `approvalPlus` (taps + then the tray's More) | |
| `chat_approval_confirm` | `approvalConfirm` | |
| `chat_approval_sheet_{ask,auto,everything}` | none: **blocked** | Flutter's "+" sheet has Model, Auto-approve and "Approval settings…" rows that open an Approvals sheet (`showSessionApprovalsSheet`). Native has neither the rows nor the sheet, only the chip's popup menu, which the gate cannot capture (a second window root). Needs `:feature:chat` (the "+" sheet's settings rows + an Approvals sheet). |

Rendered: 10 of 16 (before: 0 of 16, "FAIL-unrendered" in the scoreboard).

### Measurements (`rb_gate.py evaluate --order 43`, T5: box 4 %, blob 100 px)

Whole frame, as the gate measures it. Before = the same gate cases on the base code.

Box % is shown only where the evaluator counts it against the 4 % limit ("—": not over the limit).

| Shot | before: box % / blob px | after: box % / blob px |
|---|---|---|
| ask dark | 6.51 / 346 | 4.13 / 258 |
| ask light | 7.29 / 3,586 | — / 203 |
| auto dark | 7.61 / 4,156 | 4.13 / 258 |
| auto light | 7.71 / 4,202 | — / 203 |
| everything dark | 7.61 / 4,156 | 4.13 / 258 |
| everything light | 7.71 / 4,202 | — / 203 |
| confirm dark | 4.27 / 4,554 | 4.09 / 3,922 |
| confirm light | 4.82 / 4,409 | 4.77 / 4,174 |
| plus dark | 42.67 / 145,928 | 42.68 / 145,993 |
| plus light | 45.02 / 156,346 | 45.07 / 156,360 |

Every rendered shot also fails the line checks (ink ratio, line moved 84-174 px), driven by the transcript above the
composer (the reply sits 8 px higher and has no paragraph gap, the turn footer is missing).

Pass count: **0 → 0 of 16** strict. The composer region alone (y ≥ 790, pixels differing by more than 24 per
channel):

| Shot | before | after |
|---|---|---|
| ask dark / light | 8.8 % / 18.3 % | 1.5 % / 1.6 % |
| auto dark / light | 16.4 % / 21.3 % | 1.4 % / 1.5 % |
| everything dark / light | 16.4 % / 21.3 % | 1.4 % / 1.5 % |

What still fails the whole-frame check is outside this group: the header (back arrow, title chevron, missing "· Git"),
the transcript (no gap between paragraphs, the "Sonnet 4" footer, the reply 8 px higher, the hairline at the top of
the chat's bottom veil) and, for `plus`, the "+" sheet's missing settings rows (Model, Auto-approve, Approval
settings…, File from the project, Voice input, Prompts). Those are groups B/E.

### Layout guard and alignment gate

- Fixed (composer, both): "small-target: Ask OpenCode…" (phone 268 x 24, tablet 588 x 24). The stale baseline lines it
  left in `native/config/layout/baseline/feature-chat.tsv` (8) and `native/config/align/baseline/feature-chat.tsv` (4)
  are removed (the baselines shrink).
- Not fixed, not baselined: each `chat_approval_*` shot still breaks the transcript and header rules that
  `chat_transcript_turn` carries in its baseline (edge-tap on the bubble, the reply and "More for this reply", tight
  bubble/reply, content-edge of the bar title, tablet wide-text) plus one alignment finding in the "+" sheet
  (`gap-step "Commands and agents"`). They are not composer findings and baselining them would grow the baseline, so
  the five gate cases throw in a strict run until groups B/E fix them. The measurements above were taken with
  `OC_LAYOUT_BASELINE=update OC_ALIGN_BASELINE=update` and the rewritten baselines thrown away.

## Tests

- `ui/kit-chat` `ComposerRowFieldWidthTest` (3): the one-row field width from Flutter's `rowField`, used by the new
  kit part `KitComposerRowFit` (measures whether the box is one row or two). Pass.
- `feature/chat` `F1ComposerOneRowTest` (2): the idle box is one row with the chips above it (fails on the base:
  verified by running it against the reverted sources), and long words stack. Pass.
- Updated to Flutter's order (approval first, chips above the box): `E20S11ComposerChipsTest`, `E20S08ComposerCraftTest`,
  `QaWave1ComposerTest` N2; `ComposerModelsFilesTest` reads the model words in the unmerged tree (the button is now one
  node, "Model" with the words as its state).
- Run: `:feature:chat:desktopTest --tests '*E20S*' '*QaWave1*' '*LooksCapture*' '*Composer*' '*.rb.o42.*'` and
  `:ui:kit-chat:desktopTest --tests '*omposer*'`: 13 failures after, the same 13 as on the base minus the ones fixed,
  plus "no golden yet" recordings (E20-S11/12/13/14 goldens are not in git). None caused by this change, apart from
  the five order-43 gate cases, which throw on the transcript/header guard findings above.
- detekt (`tool/qa/detekt.sh :feature:chat :ui:kit-chat :ui:kit`): the base already reports new findings in these
  modules; compared with the base's report, this change adds none.

## Emulator checks (coordinator)

1. Open any conversation, empty box: one pill row (+, "Ask OpenCode…", mic); above it "Ask first" with a shield on the
   left and the model chip ("Server default ⌄") on the right, both on the box's edges.
2. Type a short line: the mic turns into Send in place (cross-fade), the row stays one row. Keep typing past one line:
   the words take the whole width and +/Send move under them; delete back to one line: they return to one row with
   the keyboard and cursor kept.
3. Tap the approval chip: the three modes; choose Approve everything, confirm: the chip reads "Auto-approve on" with a
   check.
4. Tap the model chip: the model sheet opens (TalkBack reads "Model, Server default").
5. Start a run with an empty box: the round target is the ringed Stop. Type during the run: Stop moves beside the plus,
   Send says "Add to this turn", the words get the full width.
6. Press +: the tray unfolds inside the box above the row; + turns to ×.

## Shared files touched (outside `:feature:chat-composer` / the kit-chat composer parts)

The live composer is `BasicComposer` in `:feature:chat`, so the seam had to land there. All edits are small:

- `feature/chat/.../composer/BasicComposerHost.kt`: the strip above the box, the box through `KitComposerRowFit`, the
  model chip as the kit's `KitComposerModelChip` (one "Model" node with the words as its state), disabled Send on an
  empty box.
- `feature/chat/.../header/ui/ApprovalChip.kt`: the chip is `KitChip` (shield; selected check + "Auto-approve on" for
  either automatic mode), as Flutter's `_AutoApprovalIndicator`.
- `feature/chat/.../ui/ChatScreen.kt`: the composer slot pads only its top (the bottom room was doubled).
- `ui/kit/.../input/KitField.kt` (kit-owner): the composer field's centring padding moves into the decoration so the
  field node is the 48 dp target (layout guard small-target). Non-composer fields are unchanged.
- Tests in `feature/chat/src/desktopTest` that encoded the old two-row order, and the stale baseline lines in
  `native/config/{layout,align}/baseline/feature-chat.tsv` (removed only).

New: `ui/kit-chat/.../composer/KitComposerRowFit.kt` (kit-chat composer part). `KitComposerBox` and
`KitComposerTextBox` are unchanged (other callers keep their behaviour).

Not done here: `:feature:chat-composer`'s own `ComposerScreen` already draws the chips above a one-row
`KitComposerTextBox`; it is not on the live chat path (`FullComposerHost` has no binding yet), so it was left as is.
