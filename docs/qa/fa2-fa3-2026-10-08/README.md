# FA2 / FA3: agent account on Settings > Agents (2026-10-08)

Branch `fe/agent-account`, based on `feat/genui-fe` at `f0e33d96d`.

**Not device proof.** Every image below is a widget golden rendered against a
fake source (`FakeAccountAgentsSource` in `test/support/agents_fakes.dart`)
that plays the controller's part: it returns the sign-in status check and
logout results the test scripts. Nothing ran on a phone, an emulator or a real
agent, and the account is the fixture `sam@example.com`. The backend contract
is `docs/design/BA1-contract.md` and `BB8-native-contract.md`.

## Finish line

A phone agent row says "Signed in" only after the agent's own sign-in status
check confirmed it, never from an install, a phone check or a terminal exit.
When the check names the account, the row and the agent's sheet show it as one
plain line. The sheet has "Sign out of <Agent>", which asks first, names the
agent, says conversations stay, and runs the agent logout. Only Claude and fx
have a qualified check and logout; every other agent keeps today's wording.

## Goldens

Folder `test/goldens/`, 412x915. Each exists as `_light`, `_dark`, and Arabic
right to left (`_ar_light`, `_ar_dark`). Test: `agents_account_golden_test.dart`.

| File stem | What it shows |
| --- | --- |
| `agents_account_row` | Settings > Agents after the check: Claude Code "Signed in as sam@example.com" over the quiet "Can't reopen old conversations" line; fx "Signed in" (the check gave no name); Codex "Sign in needed" with a Sign in chip (no qualified check, today's wording); Gemini CLI "Not installed · 21 MB" with an Install chip (Arabic shows "21 MB" in order). |
| `agents_account_sheet` | Claude Code's sheet, signed in: title "Signed in", body, the account as its own ellipsized line, "Use Claude Code", "Sign in again", and the tertiary "Sign out of Claude Code". |
| `agents_account_confirm` | The question before signing out: danger-filled "Sign out of Claude Code", neutral Cancel, "Your conversations with Claude Code stay." marked as kept. |
| `agents_account_failed` | The sign-out ended without a confirmed signed-out status: the plain sentence "Couldn't confirm that Claude Code signed out. Try again.", technical text folded under Details, and the sheet back in its signed-in layout with Sign out still available. |

## Behaviour tests

`test/agents_account_ui_test.dart` (33 tests): confirmed sign-in reads Signed
in, never Ready; account line and its single-line wrapping; an install and a
passed phone check do not make a row Signed in; signed out, timed out,
unsupported, invalid and unavailable checks keep the existing wording; other
agents are unchanged; Sign out is offered only where logout is qualified and
the sign-in is confirmed; the question names the agent and Cancel changes
nothing; confirm calls `signOutAgent`; an unconfirmed sign-out says so in plain
words, re-reads the real state and never flashes the signed-out layout; a
finished sign-in terminal ends on the status check, not the exit code; the
download size is isolated left to right.

## Also in this branch

`test/golden_harness_test.dart` (G23) failed on four entries that came in with
earlier merges (`background_pause_golden_test`, `runtime_switch_golden_test`,
`exit_history_dark/light`). They now conform to the harness rules (Android
platform override, a `<module>_<page>_<state>` name) without a baseline entry
and without regenerating any golden.
