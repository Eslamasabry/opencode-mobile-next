# Agent cards (phone-native generated UI) — plan

Date: 2026-10-07 · Status: Astra review revision; pending coordinator acceptance · Contract: [genui-contract-2026-10-07.md](genui-contract-2026-10-07.md)

Review: [genui-review-astra-2026-10-07.md](genui-review-astra-2026-10-07.md).
This revision is a proposal, not a frozen implementation contract.

## Why

Our difference: the coding computer runs **on the phone** (in-app Ubuntu: Claude Code, OpenCode), and
its agents can **use the phone**. Agent cards make that visible: instead of walls of text, an agent shows
native app cards (choices, forms, checklists, charts, diff summaries) and asks the phone for a photo, a
file or a voice note. The person answers with one thumb, in the chat, from the Conversations list, or
from a notification.

The design is **declarative and app-owned**: the agent describes a card; the app draws it from `lib/ui/kit/` parts. No agent HTML, script,
URL loading or styling ever runs in the app (precedent: `MobileTaskView`, `lib/domain/mobile_tool_view.dart`).

## Finish line and non-goals (slice 1)

**Finish line:** on qualified in-app Ubuntu Claude Code and OpenCode runtimes, a completed `oc-ui`
call renders a native card; an idle-session choice/form/confirm/photo answer reaches the exact owning
conversation from chat or list; a fresh waiting card makes that row "Needs you"; authoritative answer
history becomes a receipt; bounded reconciliation recovers cards after restart and reconnect; disabling
blocks actions immediately and reports any remaining runtime-removal work honestly.

**Non-goals (slice 1):** remote installation (Termux, PC, VPS), location/new permissions,
notification quick-answers, blocking tool results, file and voice asks, offline full-card storage,
arbitrary HTML/layout, images loaded from URLs, multi-step wizards, secret collection, and silently
restarting live agents. A card confirmation is conversation text, not permission to run a tool.

**Readiness:** the current source proves normalized tool-input paths, not end-to-end Agent cards.
Each dialect requires direct-tool fixtures and installation/restart/answer qualification. OC2 direct
MCP tools and bounded Paseo history are explicit prerequisites; keep unsupported runtime paths
unavailable. Desired default-on does not imply verified availability.

## UX

### Journeys

1. **Pick one** — "Which database should I use?" → card with 2–8 options (label + one-line detail) →
   tap once the agent is idle → the answer message starts the next turn. Also answerable from the list row.
2. **Fill a form** — "Set up the deploy" → up to 12 fields (text, number, toggle, select, date) →
   Send. Required fields block Send with a field-level reason.
3. **Confirm** — "Delete 14 files?" → Confirm / Cancel; a destructive tone uses the kit's danger style
   and the confirm sheet.
4. **Show me** — display-only cards: checklist of what was done, key-value summary, small table, bar/line
   chart, diff stat, progress, callout. Nothing to answer; they read like a report.
5. **Use the phone** — "Send me a photo of the error on your TV" → camera/gallery → explicit Send →
   the photo goes with the answer. Existing image/MIME/size limits apply. File and voice asks are S2;
   Paseo currently rejects non-image attachments.

### States of a card

| State | When | Looks |
|---|---|---|
| Waiting | newest completed eligible ask, fresh tail proves no later person message | full card; controls only when idle; row "Needs you" |
| Held | answer tapped, within the 3 s Undo window | collapsed "You chose X · Undo" (reuses `DelayedAnswers`) |
| Answered | authoritative user message matches scope/call/id and valid answer | collapsed receipt "You answered · X", tap to expand read-only |
| Passed over | a nonmatching person message or newer eligible ask follows | read-only, quiet "Not answered" |
| Unknown | missing/stale history or unresolved delivery | read-only; reconcile, never auto-resend |
| Sending | Undo elapsed, delivery/echo pending | disabled controls; not an answered receipt |
| Report | no `ask` | full card, no controls |
| Unreadable | fails validation | one line "This card couldn't be shown" + Details (no raw errors rule) |

### Rules

- One control per turn: only the newest eligible ask is actionable; the composer stays usable and its hint
  says "Or type your answer". A typed reply passes the card over.
- Obvious credential fields are rejected, but label heuristics cannot guarantee intent. Never
  autofill credentials or clipboard data. Text fields carry the note "The agent sees this and it is
  saved in the conversation". Existing kit redaction covers displayed text and receipts.
- Every agent-supplied link goes through `openExternalLink`.
- Agent text is plain text (no markdown execution beyond the kit's inline styles), bounded, BiDi-safe.
- Eyebrow names the asker: "Claude Code asks" / "OpenCode asks" (same pattern as request cards).
- Accessibility: every control has a label; charts carry a text summary; choices are a radio group.
- Settings → Agents: "Cards from agents" switch (desired default on only for eligible in-app Ubuntu).
  Show verified per-runtime readiness, including unavailable, partial and restart-required states.
  Off immediately blocks actions; loaded tools may remain visible until a clean agent restart.
- Never route a list answer by bare session ID. Preserve profile, merged source, directory/workspace
  and transport call identity; revalidate after resume. No automatic retry after uncertain delivery.

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
- `KitSenseAsk` — S1 photo ask: purpose line, pick/take buttons, thumbnails, remove; file/voice in S2.

Placement: only a qualified completed tool call replaces its transcript row. Streaming, failed or
unexecuted calls retain their normal tool row. Details expose safe problem codes, never raw rejected
payloads or errors. In the list: the same card with `inList: true` under the row (the `listRequest` slot,
after permission requests).

## Frontend (Claude)

- `lib/ui/screens/chat/agent_card_*.dart` (chat library parts) — render `GenUiCard` from transcript parts,
  wire answers, receipt/passed-over states, composer hint.
- `lib/ui/screens/chats/` — list slot: waiting card inline, answer from the list.
- Settings switch UI and per-runtime setup status; show "on" only for verified runtimes.
- Copy in `app_en.arb` / `app_ar.arb`.
- Tests: widget tests per ask kind (answer, required field block, undo, receipt, passed over,
  unreadable), list answer test, goldens for each kit part, light/dark/RTL.

## Backend (Astra)

1. **Domain** `lib/domain/genui/` — strict bounded v1 parsing, immutable types, exact-name recognition,
   full trusted scope/call identity, deterministic state/receipt derivation and typed answer validation.
   Normalized input is `Part.toolState.input`; never parse streaming previews. The expected OC name
   `oc-ui_show` and Paseo name `mcp__oc-ui__show` need captured direct-tool fixtures before enablement.
2. **Tool server** — zero-dependency Node stdio MCP, embedded as a Dart string. Validate with the same
   fixture-tested limits as Dart; implement initialization, framing, list/call and generic errors.
   Return immediately, say "accepted for display" rather than asserting delivery, and instruct the
   model to end its turn for asks. No listener, credentials, tool-time downloads or arbitrary IO.
3. **Install/registration** — qualify an existing absolute Node executable; OpenCode-only installs
   may lack it. Claude registration runs as `oc` with the **profile daemon's HOME/CLAUDE_CONFIG_DIR**.
   OC1 uses existing persistent config integration. OC2 has a separate root config directory,
   `mcp.servers` schema/direct-tool prerequisite; runtime PUT alone is not durable. Follow contract §6,
   guard ownership/collisions, preserve unrelated config and report restart needs without auto-restart.
4. **State/feed** — track eligible source/location events, including OC global and separate Paseo feed
   gateways; normalize envelope session IDs and deduplicate. Existing feeds and tail cache do not
   track cards. Reconcile bounded newest tails after idle/reconnect/restart, prioritize persisted
   identities, expose incomplete coverage. Never fetch every session's unlimited history. Paseo needs
   a qualified bounded-tail path before recovery can be enabled. See contract §4 for fixed budgets.
5. **Answers** — `answerGenUi` routes from chat or list to the exact owner, validates ask/attachments,
   requires known idle state, guards stale/resumed/deleted scope, and serializes duplicate taps.
   Reuse the prompt gateway, not chat-screen UI logic. Undo/sending/unknown are distinct from an
   authoritative receipt; ambiguous sends persist a marker and reconcile without automatic retry.
6. **Deletion/off** — prefs `oc.genui.<what>.<profileId>`; no duplicated sensitive card/answer bodies.
   Drain writes and cancel stale work before deletion. Remove profile-owned Claude resources; remove
   shared root OpenCode registration only for its last owner. Effective capability defaults false
   and tracks readiness separately from desired enablement.
7. **Checks** — JS/Dart shared validation corpus and actual MCP subprocess framing; dialect fixtures,
   input-loss/reconnect cases; cross-source collisions; busy/stale/duplicate/unknown answers;
   bounded recovery; deletion races; idempotent/collision/partial-failure install tests; both feed
   projections. Run focused tests serially once implementation is authorized. No Flutter tests for
   this documentation-only review. Qualify actual phone behavior separately from unit tests.

### Implementation ownership and patch boundary

Astra owns domain/helper/install/controller/feeds; Claude owns `lib/ui/` and localization. The entire
connection library stays with one editor. Before parallel implementation, freeze the remaining
node/ask constructors and coordinate any shared protocol-cluster edits. No implementation begins
before the coordinator accepts the revised contract. This phase writes docs only; no commit.

S1 must add no Android/native, asset, plugin, pubspec or permission changes. Existing native execution
bridges can carry a Dart-embedded helper, but they must exist in the **actual released patch baseline**.
The current checkout alone cannot prove patch compatibility. No signing, deployment or release is
part of backend implementation.

## Slices

1. **S1 (this plan):** strict schema, direct tool server, managed install, chat/list choice/form/confirm/
   photo cards, authoritative receipts, bounded restart recovery, truthful setting/readiness.
2. **S2:** notification choice/confirm answers, capability-qualified file and voice asks; separately
   assess blocking answers only after a durable response protocol is specified.
3. **S3:** remote install, location and workspace images.

## Decisions requiring coordinator acceptance / qualification

1. Retain message-based answers for v1; sending waits for known idle state. No claim that a model obeys
   "end your turn", that a card confirms a destructive operation, or that delivery is exactly once.
2. Accept file/voice deferral to keep the first cross-backend journey honest and bounded.
3. Accept scoped identity/envelope/API changes and explicit unknown/partial/restart states.
4. Qualify Paseo bounded-tail reads and complete MCP `detail.input` snapshots; the current generic
   history method is unbounded and cannot be reused for automatic multi-session recovery.
5. Qualify direct MCP naming and OC2 persistent config/Code Mode on the pinned binaries; current
   public docs guide the candidate configuration but are not pinned-runtime execution evidence.
6. Accept per-owner registration semantics: another owner's shared OpenCode tool may remain enabled.
   Never silently erase another profile's configuration or restart their live work.
7. Verify patch eligibility against the release selected by the coordinator before shipping.
