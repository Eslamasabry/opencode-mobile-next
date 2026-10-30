# Chat fidelity round (2026-10-20)

Finish line: the conversation screen matches picked concept C while the agent
works, gets its Model and Auto-approve chips back while idle, and "+" opens
plus-menu concept C (an inline tray) instead of a sheet.
Non-goal: redesigning the More sheet's rows.

## What changed, per state

- **Idle**: the Auto-approve chip (filled shield "Auto-approve", outline
  "Asks first", opens the mode menu) and the model chip are back over the
  composer, in one quiet neutral-pill look on the composer's edges. No
  "Auto-approve is on" line, since the chip says it. The composer is one row:
  "+", the field, mic or Send.
- **Working**: the chips fold away (KitReveal, 250 ms, instant with reduced
  motion). The composer is one glowing row reading "Steer while it works…",
  with the neutral round Stop inside it at the end and no mic beside it (voice
  is in More). The live line is a mint dot plus "Writing index.html · 12 s"
  ("Reading…", "Editing…", "Running…", "Searching for…"). The quiet
  "Auto-approve is on · Turn off" line shows under the header only while the
  agent works with Auto-approve on. The phrase chips ("What's the update?")
  hide while it works, because the live line already answers that.
- **Question**: the merged card stays, the composer reads "Waiting for you",
  and the chips stay hidden.
- **Done**: the chips come back, and the work folds into its one line as before.
- **Header**: the title, then one quiet line of words, "neon-breaker · main"
  ("Git" until a branch is known). There are no glyphs and no chevron. Each word
  is its own 48 dp target: the project opens Files, and the branch opens the
  Git sheet (issue #104).
- **"+" (plus-menu concept C)**: the box grows upward into one attached row
  with Photos, Camera, File and More, and "+" turns into "×" (150 ms, instant
  with reduced motion; Esc closes the row). More opens the existing sheet,
  which no longer repeats Photos, Camera and File. The sheet holds Model,
  Auto-approve, Approval settings, File from the project, Voice input,
  Commands and agents, Prompts and More tools. A server that takes no files
  has no tray, so "+" opens the sheet as before.
- The composer's editor button shows only once the words run past one line
  (or files are attached).

## Evidence

Renders at 412x915 dp with blurred shadows:
`/home/eslam/Storage2/launch-footage/chat-fidelity/` (idle, working, question,
done, 320 dp at 2x, Arabic, and the "+" tray in idle and working states, dark
and light). Iterations are in `pass0` to `pass4`. Capture:
`tool/capture/chat_fidelity_test.dart`.

## Checks

Kit composer, turn and tray tests, plus the chat tests that touch the
composer, chips, approvals, the live line and "+" (about 100 files), run
serially with `-j 1`. 178 goldens were regenerated with `TZ=UTC`, and kit
ratchet G16 and G21 pass.

These tests already fail on base `7373b4f15` and were left untouched:
`chat_live_events_test` "/files attaches a project artifact",
`text_scale_overflow_test` G6 matrix (KitPageHeading, KitWheel), and the
settings goldens in `screen_settings_1_golden_test`. Those settings goldens
are stale on base, so they were not regenerated here.
