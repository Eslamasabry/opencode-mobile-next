# FC2 — one question/answer card shape, one receipt (2026-10-07)

Plan: `docs/design/FC2-one-card-shape.md`.

Finish line: permissions, OC1 questions, OC2 forms and agent cards are drawn
with one kit card shape and one answered row, in the chat and under a
Conversations row. Non-goal: changing answers, Undo windows, keys, copy,
routing or the question/form sheets.

## Change
- `lib/ui/kit/kit_request_card.dart`: the shape is four shared kit helpers
  (functions, so the kit gains no widget class): `kitRequestPlacement`,
  `kitRequestFrame`, `kitRequestHeading`, `kitAnsweredRow`.
  `KitRequestCard` draws through them and gains `inList`.
  **Its goldens are byte-identical** (`kit_request_card_golden_test`
  passes unchanged): its behaviour and look did not move.
- `lib/ui/kit/kit_agent_card.dart`: drawn through the same helpers. The
  agent's glyph now sits in the request tile beside the eyebrow and title;
  the answered / passed-over row is the request's answered row (receipt
  under the title, chevron on the title line, read-only body under it).
  Copy, keys, modes, Undo, semantics unchanged.
- List rows: `permissionRequestCard(inList)`, `questionRequestCard(inList)`
  and `capturedFormRequestCard(inList)` (new param; `chats_host.dart` passes
  it) place a request card as the list places an agent card, instead of
  adding a second page gutter inside the row.
- Spec note: `docs/ux-system/kit-api/KitRequestCard.md` "One shape".

## Evidence
- `contact-sheet-cards.jpg` (from the kit goldens): top before, bottom after
  — request question card (unchanged), agent card asking, agent report,
  request answered row (unchanged), agent answered row.
- `contact-sheet-list.jpg` (new golden `agent_card_list_mixed`): left a
  permission request under its row before (`inList` off: indented by a
  second gutter), right after (aligned with the agent card under the next
  row).

## Goldens updated deliberately
`test/goldens/kit/kit_agent_card_*` (13), `test/goldens/agent_card_*` (8),
new `agent_card_list_mixed_{dark,light}`. All looked at; only the agent
card heading/answered row and the list placement changed.

## Tests
- `test/kit/kit_agent_card_test.dart` group "one card shape with
  KitRequestCard (FC2)": the agent card has the request tile with the
  title at the same inset; both answered rows stack the receipt directly
  under the title at the same inset; under a list row both frames align.
  With the old `kit_agent_card.dart` the first two fail.
- Focused request/agent/list/kit suites listed in the final report.

## Test results (2026-10-07)
- 28 focused files (request/permission/question/form/agent-card/list/kit
  manifest/motion/overflow suites): 642 passed; the one failure
  (kit_ratchet G21, a literal 0 in an EdgeInsets) was fixed and re-checked.
- `test/kit/kit_agent_card_test.dart`: 24 passed.
- Golden sweep `test/goldens` + `test/revamp`: stopped by the session at
  2135 passed, 0 failed (through `kit/kit_message_golden_test.dart`) because
  the machine ran low on memory. The rest of `test/goldens/kit/` after
  `kit_message` and all of `test/revamp/` were **not** run in this sweep
  (kit_request_card and kit_agent_card goldens were run separately and pass).
