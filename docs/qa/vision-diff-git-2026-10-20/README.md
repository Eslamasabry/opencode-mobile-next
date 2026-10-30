# Vision slice: Changes screen, Git sheet, answers outside the chat (2026-10-20)

Decisions: `docs/design/vision-decisions-2026-10-20.md` rows 1, 6, 7.

## A. Changes screen
- One-sentence summary on top ("3 files changed · +42 −8") plus "The agent already made these changes in your project."
- Question: **Looks good** (the one main action) and **Send back with a note** (writes a note for the file on screen; it lands on the chat prompt as a reference). Steps aside while lines are selected (the diff's own bar owns the primary) and the summary hides at large text.
- **Undo all** is built behind `ReviewWorkspace.onUndoAll` (null = not shown), with a confirm naming the files ("Undo 3 changed files?"), neutral not red. NOT wired: see below.
- Undo-all verdict: OpenCode 2 has a real revert (`POST /session/{id}/revert/stage|clear|commit`, gateway `revertSession` / `stageSessionRevert`, `ServerCapabilities.sessionRevert`), but it is scoped by message id (undo from a prompt on), and this screen shows project working-tree/branch diffs on OpenCode 2, not one conversation's. The only door that opens it is `lib/ui/screens/chat/chat_session_menu.dart` (`_showDiff`), owned by another agent. Follow-up for the chat owner: pass `onUndoAll` there, calling `stageSessionRevert` for the conversation's first prompt then the staged-revert screen.

## B. Git sheet
"Send to GitHub · main" (or "Send to <host> · <branch>"), second lines only say state ("Up to date", "1 commit to push", "Nothing to send"); results say "Sent." / "Got the latest changes." Exact branch, upstream, remote name/address are under one folded Details at the bottom. Remotes and Clone stay in their own group below the four main actions.

## C. Answer outside the chat (audit)
Permission asks only. Already there: chat card and Chats-list rows (`ChatsHost.listRequest` -> `permissionRequestCard`, Allow once / Reject, command in mono, held with Undo); Android notifications (`AlertButtons.plan`: Allow once with unlock + Reject, command in the body, "Always allow" never offered); questions only open the chat. Not there: rows for other saved servers (`ProfileMonitorInbox`) and the Servers list are pointing rows only; the monitor snapshot carries no command and no reply path for a non-connected server, so that needs a domain change, not a small Dart one. Nothing built.
The card's mono command is capped at two lines with an ellipsis (kept: a one-line cut could hide the end of a command).

## Evidence
`/home/eslam/Storage2/launch-footage/vision-diff-git/{before,after}/` via `tool/capture/vision_diff_git_test.dart` (invented data; the permission card is unchanged, so before = after).
Tests: `test/git/*`, `test/review_workspace_test.dart`, `test/review_handoff_test.dart`, `test/diff_page_test.dart`, `test/kit_ratchet_test.dart`.
