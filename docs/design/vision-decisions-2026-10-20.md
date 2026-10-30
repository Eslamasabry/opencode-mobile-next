# Vision decisions, 2026-10-20

The owner answered the eight open questions from the screen-concepts vision page
(Codex concept images in `launch-footage/screen-concepts/`). Lead direction:
simplify and make things natural; judge every idea by what it removes.

| # | Question | Decision |
| --- | --- | --- |
| 1 | Answer outside the chat? | Permission asks only: Allow once / Reject on Inbox rows, the server sheet and notifications, always showing the exact command. Questions with options or free text open the chat. |
| 2 | How minimal? | Remove until the screen stops being safe or honest: keep the exact command and Reject on a question, a visible line while Auto-approve is on, and "All models". |
| 3 | New conversation | One question, "What would you like to build?", a glowing field and one quiet "Claude Code · on this phone ⌄" line. Team and separate copy move into that line's sheet. |
| 4 | Model and Auto-approve while working | The chips row leaves the chat; both open from the composer's +. One muted line under the header while Auto-approve is on. |
| 5 | New conversation on Work | A round + above the dock, at the thumb. |
| 6 | Diff screen question | "Looks good" / "Undo all" after a one-sentence summary; "Send back" becomes a composer note. |
| 7 | Git words | Plain words on the surface ("Send to GitHub · main", "Get latest"); origin/main under Details. |
| 8 | Steps rail | No rail. Only when the agent wrote a todo list: one folded "Step 3 of 5" under the live line. |

Also adopted from the page: no header status pill while working (the glow and the
live line say it), composer placeholder "Steer while it works…", Send becomes a
neutral Stop ring (02B/11B), grouped hairline rows instead of a card per row, solid
mint only for the one main action and the selected tab or segment.

Owner 2026-10-21: the conversation shows the plain model name only ("Opus 5.5").
The provider and the exact id live on the Context page; the OC2 coverage ledger
marks `model.providerID` as ignored in the conversation for this reason.
