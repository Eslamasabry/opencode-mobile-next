# FC2 — one question/answer card shape, one receipt (2026-10-07)

Finish line: everything an agent asks the person (a permission, a question, a
form, an agent's own card) is drawn with one kit card shape and, once
answered, one answered row with its `KitReceipt`, in the chat and under a
Conversations row. Non-goal: changing what any request does (answers,
Undo windows, keys, copy, routing, sheets) or the question and form sheets
themselves.

## Today (feat/genui-fe at dc67923c)

| Surface | Part | Waiting look | Answered look | Under a list row |
|---|---|---|---|---|
| Permission (`permission_sheet.dart` `permissionRequestCard`) | `KitRequestCard.ask` | needs-you frame + ring; 32 dp tinted tile; caption "reason · who · age"; headline; detail; "if ignored" | `_collapsed`: icon, title over receipt | centred, page gutter (no `inList`) |
| OC1 question (`attention_card.dart` `_QuestionAttentionCard`) | `KitRequestCard.ask` | same | same | same |
| OC2 form (`form_flow.dart` `capturedFormRequestCard`) | `KitRequestCard.ask` | same | (sending receipt only) | same |
| Agent card (`widgets/agent_card_view.dart`) | `KitAgentCard` | its own copy of the frame and ring; small inline glyph + eyebrow; headline | its own row: icon, title, chevron on one line, receipt indented under it | `inList`: no gutter, no centring |

So the kit has two card frames (copied code), two headings and two answered
rows, and a list row places a request differently from an agent card.

## Change

1. **Kit, one shape** (`lib/ui/kit/kit_request_card.dart`): the frame, the
   heading, the answered row and the placement become shared kit helpers
   (top-level functions, so the kit gains no new widget class):
   - `kitRequestFrame` — needs-you surface, attention line, 4 dp ring
     (LOOK-20), or the plain `surface1` card for a report; focus border;
     the 45 % height cap at 2.0 text.
   - `kitRequestHeading` — the tinted tile with the glyph, the caption line,
     the headline, then the lines under it.
   - `kitAnsweredRow` — the one answered receipt row: glyph, title, the
     outcome (a `KitReceipt`, "Not answered", "Stopped waiting") under the
     title, and, when the body can be read again, the chevron and the
     read-only body.
   - `kitRequestPlacement` — in the chat: reading width, page gutter,
     entrance; `inList`: as the list places it.
2. **`KitRequestCard`** draws through them (no visual change: its goldens
   must stay byte-identical) and gains `inList` (default false).
3. **`KitAgentCard`** draws through them: the eyebrow sits in the heading's
   caption beside the agent's glyph in the tile, and receipt / passed-over
   use `kitAnsweredRow`. Copy, keys, semantics, modes and behaviour stay.
   Goldens change deliberately (`kit_agent_card_*`, `agent_card_*`).
4. **List rows** (`chats/chats_host.dart`): the permission, question and
   form cards under a row pass `inList: true`, like agent cards, so all four
   sit the same way under a row.

## Checks

- `test/kit/kit_request_card_test.dart`, `test/kit/kit_agent_card_test.dart`,
  `test/goldens/kit/kit_request_card_golden_test.dart` (unchanged),
  `test/goldens/kit/kit_agent_card_golden_test.dart`,
  `test/goldens/agent_card_golden_test.dart` (updated deliberately),
  chat request and list tests (`chat_question_card_test`,
  `chat_permission_test`, `agent_card*_test`, `chats_*` request tests),
  `kit_ratchet_test`, `kit_manifest_test`.
- New test: the heading and the answered row are the same widgets in both
  parts (an agent card's tile, a request card's tile; one answered-row key).
- Evidence: `docs/qa/FC2-2026-10-07/` before/after contact sheet from the
  goldens.
