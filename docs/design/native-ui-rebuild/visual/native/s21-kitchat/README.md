# s21 kit-chat: side-by-sides

Each PNG is Flutter (the spec, left), then native before (when the shot rendered before this branch), then native
after. Judged by eye, not by pixel numbers.

| Part | Now | Files |
|---|---|---|
| KitDiffView | matches: header, change bar, gutter, tints, fold bars, selection bar, too-big footer, loading/empty/error/binary | `kit_diff_view_*` |
| KitMessage (prompt, thought, notice, marker) | matches | `kit_message_*` |
| KitAgentStrip | matches | `kit_agent_strip_*` |
| KitTurn | matches, incl. the ask card and the live line; the find band (highlighted) is not drawn | `kit_turn_*` |
| KitRequestCard (waiting, disabled) | matches; new gate cases | `kit_request_card_*-cases.png` |
| KitCodeBlock | already matched; no change | `kit_code_block_*` |
| KitSteerBox | layout matches; the field (KitField, `:ui:kit`) draws a 2 dp focus border and is ~6 dp shorter | `kit_steer_box_default_light.png` |

Left for other owners (outside `:ui:kit-chat`):

- `KitField` (`:ui:kit`): multiline field is 96 dp tall where Flutter is 102, its focused border is 2 dp solid accent
  where Flutter draws a 1 dp, softer line, and its input node sits 12 dp inside the box, which the layout guard reads
  as "Write a message 46 dp under Cancel" / "Dictate a message 30 dp under Write a message" (all 20 steer-box cases).
- `KitComposerModelChip`, `KitChip` (`:ui:kit`): Flutter ends the model name and an attachment path in "…" on
  purpose (kit_composer_chips.dart); the native chips need `kitMayTruncate` (kit_composer_chips_* findings).
- `KitAskLine` (`:ui:kit`): Flutter cuts the question at two lines with "…" (kit_ask_line.dart:102-108).
- `KitSkeletonRows` (`:ui:kit`): an align-gate gap-step (35 dp) inside the skeleton, same geometry as Flutter's
  (kit_diff_view_loading).
- Layout guard (`:ui:gate`): inline Markdown links are flagged as small targets (one line tall); Flutter's are the same.
