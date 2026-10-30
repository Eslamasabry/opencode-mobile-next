# Starting a conversation is one question (2026-10-20)

Finish line: on the Work tab a round + sits above the dock at the thumb; it opens
one screen that asks "What would you like to build?" with a glowing field and one
quiet line, "OpenCode · alpha ⌄", that opens a sheet for agent, project, model and
the separate copy. Decisions: `docs/design/vision-decisions-2026-10-20.md` rows 3 and 5.

Non-goals: the chat screen, the diff screen, the Git sheet, Inbox.

## What changed

| Before | After |
| --- | --- |
| Wide "New conversation" button floating bottom-end | Round 56 dp mint + (label heard, not drawn), same place, clears the dock |
| Project chip, Git chip, agent chip, separate-copy chip, import chip, model chip and a composer before the first word | One big glowing field (the question is its placeholder), one quiet line, one Start button |
| "What should we work on?" | "What would you like to build?" |
| Failure inside the composer's top row | Plain-words notice under the line: Try again (or Repair), Details; the draft stays |

Where each old option lives now (the sheet behind the quiet line, "How this starts"):
Agent (only where the phone runs other agents; opens the agent sheet), Project
(Git label under it), Model (the same chip), In a separate copy (only where the
server can make one), Import from Claude Code (only where supported).
Default agent is still the last used one (`selectedChatAgentId`, unchanged).

Not present in the old flow, so nothing to move: Solo / Team / Cloud cards (the
`newConversationTeam*` ARB keys have no caller). Starter chips: none existed here.

## Kit

New parts: `KitAgentLine` (one muted line, 48 dp target, semantics label),
`KitStartBox` (the glowing field: still accent ring and halo, brighter on focus
over 150 ms, instant under reduced motion). `KitFloatingAction` is now the round +.
Each has a unit test, a gallery (412x915 and 1280x800, dark and light, 2.0 text),
a still-motion registration and an overflow scene. G4 manifest passes with no
allowlist entry; G16/G21 ratchet did not go up.

## Evidence

Images in `/home/eslam/Storage2/launch-footage/vision-start/` (golden renders at
412x915, one screen each, dark and light): `before-` / `after-work-tab`,
`before-` / `after-new-conversation`, `after-options-sheet`,
`before-new-conversation-with-copy-option`, `before-` / `after-agent-sheet(-list)`.

Tests run (all passed): chats_new_chat, chats_home, agents_ui, agents_account_ui,
claude_import_screen, kit manifest / floating action / agent line / start box,
kit_ratchet, kit_motion, text_scale_overflow, ui_glossary, search_index, revamp
chat/shell/shared-system, and the goldens chats_home, shell_integrated, agents_ui,
work_parts (regenerated and viewed) plus the three kit galleries. The full suite
was not run (owner rule).

## Review round 1 (same day)

- Send is a round arrow inside the field (bottom end, quiet until there is text);
  the bottom Start button is gone. Draft is kept on failure as before.
- The field's glow is a soft blurred halo plus a hairline (no 2 px border).
- "How this starts": In a separate copy is a switch row with one muted line;
  with it on, Start opens the copy step with the typed words carried in
  (`initialTask`). Project has no Git subtitle. Model is a plain value row
  ("Server default >") that closes the sheet and opens the picker.
- New image: `after-new-conversation-typed-*` (the field with text, arrow lit).

## Round 2: agent cards on the one-question screen

- `lib/domain/agent_persona.dart` + `lib/ui/agents/agent_persona_text.dart`: one
  registry for an agent's identity, mark, maker and words (docs/design/agent-personas.md).
- Start screen, top to bottom: glowing `KitStartBox` (no arrow), `KitAgentGrid` of
  `KitAgentChoice` cards (selected = mint hairline ring + check, matte; "Set up"
  = dimmed), a Model / Project (/ separate copy / import) group, and one full-width
  "Start with <agent>" button, pinned and disabled until there is text.
- Which agents show: from `PhoneAgentsSource.chatAgentChoices` filtered by
  `agentChoosable` (ready and certified), plus dimmed "Set up" cards for rows with
  a way forward (install, phone check, sign-in, resume). Rows with no way forward
  are left out. With fewer than two cards (a remote OpenCode 2 server) the quiet
  `KitAgentLine` shows instead, and its tap opens the project picker.
- The "How this starts" sheet is gone; nothing is left behind it.
- Images: `/home/eslam/Storage2/launch-footage/vision-start/round2/` (cards, typed,
  single, text2 = 320x640 at 2x text, rtl; dark and light).

## Round 3: concept A's look (Opus, same day)

Finish line: the screen sits beside concept A
(`/home/eslam/Storage2/launch-footage/screen-concepts/b1-new-conversation/concept-a.png`)
with the decided question field on top. Non-goals: Solo / Team / Separate copy /
Cloud strip (Team and Cloud have no backend), any change to the live background.

- Page: opens as a full-screen page with Close (`fullscreenDialog`), title centred
  in title size (`KitTopBar(centred: true)`); the live aurora is the page's own
  `KitLiveBackground` (on by default; captures paint it still).
- `KitStartBox`: two lines tall beside the cards (four with no cards), grows as
  you type, theme content padding removed, a hairline and a wide soft halo.
- `KitAgentChoice`: 52 dp monochrome mark, name in title size, a calm line in
  body size (two lines reserved), 20 dp padding, 22 dp radius, about 190 dp tall;
  selected = mint ring (twice the focus ring), a breath of mint in the solid
  surface, a filled mint check badge that lands over 250 ms (ring 150 ms;
  instant under reduced motion). An odd last card spans the row sideways
  (`KitAgentGridSpan`); at large text one column of full-width cards.
- Model and Project: one group with icon tiles and body-size values
  (`KitRowValue(prominent: true)`).
- `KitButton.primary(hero: true)`: 56 dp full-width pill, the only solid mint;
  disabled until there is text, waiting in a quiet mint.
- Honesty: Set up cards only for agents certified for chat on this version
  (installing an uncertified one would end at "not certified", so it is left out).
  Today that means Claude Code (ready, or Set up) and OpenCode. The four- and
  three-agent images offer Codex and Gemini as certified agents would be
  (`FakePhoneAgentsSource.alsoReady`), to show the layout.
- OpenCode mark: `assets/marks/opencode.svg` carries its two-tone lower block.
- Arabic: the boxes in round 2's button label were the golden harness (no system
  font fallback in `flutter test`); the real bug was that `AppTheme.forLocale`
  left the button themes on the Latin face. Buttons now take the Arabic face
  like all other text (Arabic button goldens re-rendered).

Images (one full phone screen each, 412x915 at 2x unless named):
`/home/eslam/Storage2/launch-footage/vision-start/opus/` — four-agents-dark,
four-agents-light, three-agents-dark, typed-dark, single-agent-server-dark,
small-320-text2-dark, arabic-rtl-dark, plus today-claude-ready-dark and
today-fresh-phone-dark (this build's real agent sets). Capture:
`tool/capture/vision_start_test.dart`.
- Model row (review fix): an agent on this phone shows the model it will use by
  its plain name: the person's last choice for that agent, else the agent's own
  default from the helper's list ("Default (Opus)" names Opus, a model marked
  default names itself), isolated for right-to-left. "Default model" only when
  the list names nothing. OpenCode keeps the server's resolved model
  (config, then the server's /model/default); "Server default" only when none
  is known. The row follows the chosen card (test: agents_ui_test "the row
  follows the chosen card's own default", seen failing without the fix).
