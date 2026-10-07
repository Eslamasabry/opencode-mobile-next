# Agent cards (phone-native generated UI) — plan

Date: 2026-10-07 · Status: draft for Astra review · Contract: [genui-contract-2026-10-07.md](genui-contract-2026-10-07.md)

## Why

Our difference: the coding computer runs **on the phone** (in-app Ubuntu: Claude Code, OpenCode), and
its agents can **use the phone**. Agent cards make that visible: instead of walls of text, an agent shows
native app cards (choices, forms, checklists, charts, diff summaries) and asks the phone for a photo, a
file or a voice note. The person answers with one thumb, in the chat, from the Conversations list, or
from a notification.

Competitors render agent UI on the web (HTML, iframes) or not at all. Ours is **declarative and
app-owned**: the agent describes a card; the app draws it from `lib/ui/kit/` parts. No agent HTML, script,
URL loading or styling ever runs in the app (precedent: `MobileTaskView`, `lib/domain/mobile_tool_view.dart`).

## Finish line and non-goals (slice 1)

**Finish line:** on the in-app Ubuntu, Claude Code and OpenCode can call the `oc-ui` tool; the card
renders in the chat; a choice/form/confirm/photo answer goes back to the agent; a waiting card makes the
conversation "Needs you" in the list with the card inline; answered cards collapse to a receipt; this
survives an app restart; turning the feature off removes the tool from both agents.

**Non-goals (slice 1):** remote servers (Termux, PC, VPS) — slice 3; location (new Android permission =
native change) — slice 3; notification quick-answers — slice 2; tables wider than 6 columns, images,
free-form layout, themes chosen by the agent, multi-step wizards; secrets of any kind.

## UX

### Journeys

1. **Pick one** — "Which database should I use?" → card with 2–8 options (label + one-line detail) →
   tap → the agent continues. Also answerable from the list row.
2. **Fill a form** — "Set up the deploy" → up to 12 fields (text, number, toggle, select, date) →
   Send. Required fields block Send with a field-level reason.
3. **Confirm** — "Delete 14 files?" → Confirm / Cancel; a destructive tone uses the kit's danger style
   and the confirm sheet.
4. **Show me** — display-only cards: checklist of what was done, key-value summary, small table, bar/line
   chart, diff stat, progress, callout. Nothing to answer; they read like a report.
5. **Use the phone** — "Send me a photo of the error on your TV" → camera/gallery → the photo goes with
   the answer. Same for a file (picker) and a voice note (on-device transcription → text).

### States of a card

| State | When | Looks |
|---|---|---|
| Waiting | has an `ask`, no person message after it | full card, primary answer control; row "Needs you" |
| Held | answer tapped, within the 3 s Undo window | collapsed "You chose X · Undo" (reuses `DelayedAnswers`) |
| Answered | the answer message follows the card | collapsed receipt "You answered · X", tap to expand read-only |
| Passed over | a person message without the answer follows | read-only, quiet "Not answered" |
| Report | no `ask` | full card, no controls |
| Unreadable | fails validation | one line "This card couldn't be shown" + Details (no raw errors rule) |

### Rules

- One control per turn: a waiting card is the turn's control; the composer stays usable and its hint
  says "Or type your answer". A typed reply passes the card over.
- Cards never ask for passwords, keys or tokens; text fields carry the note "The agent sees this and
  it is saved in the conversation".
- Every agent-supplied link goes through `openExternalLink`.
- Agent text is plain text (no markdown execution beyond the kit's inline styles), bounded, BiDi-safe.
- Eyebrow names the asker: "Claude Code asks" / "OpenCode asks" (same pattern as request cards).
- Accessibility: every control has a label; charts carry a text summary; choices are a radio group.
- Settings → Agents: "Cards from agents" switch (default on for the in-app Ubuntu).

## UI (kit)

All parts come from `lib/ui/kit/`. Reused: `KitRequestCard` frame/eyebrow, `KitChoiceList`, `KitField`,
`KitDateTimePicker`, `KitChecklist`, `KitCodeBlock`, `KitDiffView` (stat only), `KitProgressRow`,
`KitNotice`, `KitReceipt`, `KitUndo`, `KitConfirmSheet`, `KitRow`, buttons.

New kit parts (each with States line, gallery entry, docRow, golden):
- `KitAgentCard` — the frame: eyebrow, title, body slot, ask slot, collapsed/receipt modes, `inList`.
- `KitKeyValue` — label/value rows (tabular numbers).
- `KitMiniTable` — ≤6 columns × ≤20 rows, horizontal scroll inside, header row.
- `KitChart` — bar and line, ≤3 series × ≤30 points, CustomPaint, theme roles, text summary for
  semantics, reduced motion respected.
- `KitSenseAsk` — photo/file/voice ask: purpose line, pick/take buttons, thumbnails, remove.

Placement: in the transcript the `oc-ui` tool row is replaced by the card (tool details stay under the
card's Details). In the list: the same card with `inList: true` under the row (the `listRequest` slot,
after permission requests).

## Frontend (Claude)

- `lib/ui/screens/chat/agent_card_*.dart` (chat library parts) — render `GenUiCard` from transcript parts,
  wire answers, receipt/passed-over states, composer hint.
- `lib/ui/screens/chats/` — list slot: waiting card inline, answer from the list.
- Settings switch UI, setup status line ("Cards from agents: on · Claude Code, OpenCode").
- Copy in `app_en.arb` / `app_ar.arb`.
- Tests: widget tests per ask kind (answer, required field block, undo, receipt, passed over,
  unreadable), list answer test, goldens for each kit part, light/dark/RTL.

## Backend (Astra)

1. **Domain** `lib/domain/genui/` — parse + validate the v1 schema into `GenUiCard` (strict allowlist,
   bounds, reasons); recognise `oc-ui` tool parts on every dialect (OC1 `oc-ui_show`, OC2, Paseo
   `mcp__oc-ui__show`); derive card state from the transcript; encode answers as the answer message.
2. **Tool server** — `oc-ui` MCP stdio server, single zero-dependency Node file, embedded as a Dart
   string (like `lib/builtin/agents/paseo_scripts.dart`) so it ships in a Shorebird **patch**. Validates
   with the same limits and returns immediately: "Shown. The answer arrives as the person's next
   message; end your turn now." Tool description teaches the schema.
3. **Install/registration** in the in-app Ubuntu: write the script, register with Claude Code (user
   scope MCP config) and the in-app OpenCode (config `mcp` entry or runtime `POST /mcp`, whichever the
   pinned versions support — research first), idempotent, removable, restart rules stated.
4. **State** — `ConnectionController`: pending asks per session from the live event stream (all
   sessions, not only the open one), recovered after restart by reading the last message of recently
   active idle sessions (bounded); feed rows with a waiting card are `needsYou`; `answerGenUi(...)`
   sends the answer message (+ attachments) through the existing prompt path; capability
   `ServerCapabilities.genUi`.
5. **Agents backend (Paseo)** — same for Claude Code conversations: tool parts reach the gateway with
   name and input; confirm Paseo forwards MCP tool calls with full input (research item).
6. **Deletion** — prefs keyed `oc.genui.<what>.<profileId>`; feature-off removes registration.
7. Tests: parser fuzz/limits, every dialect's tool-part shape, state derivation, answer encoding,
   pending-ask recovery, install script dry-run, feed needs-you.

## Slices

1. **S1 (this plan):** schema v1, tool server, in-app install, chat cards, list answer, receipt, setting.
2. **S2:** notification quick-answers (choice/confirm), blocking answers for the in-app server (tool
   waits for the answer instead of ending the turn), voice ask polish.
3. **S3:** remote servers (offer "Add agent cards to this server"), location ask, images from the
   workspace.

## Risks / open questions for review

1. Message-based answers (non-blocking tool) vs blocking tool result — chosen: message-based for v1
   (works on every backend, survives app death). Agree?
2. Will models end their turn after a non-blocking ask? Mitigation: tool result text + description.
3. Pending-ask recovery cost after restart (reading last messages) — bound and measure.
4. Paseo MCP passthrough and tool naming on 0.9.2.
5. OpenCode MCP registration without a server restart.
6. Patchability: keep native/asset changes at zero for S1.
