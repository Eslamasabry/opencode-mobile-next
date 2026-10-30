# Git in the chat: one-step save and send (vision slice, 2026-10-20)

Pick (owner, settled): **B's one step inside the voted C sheet.** The Git
sheet the chat's branch chip opens gains one named compound action, "Save 2
files and send to GitHub · main", as its one mint action; the person ticks
the files and can edit a prefilled message before the one tap that saves and
sends.

## Technical-honesty audit (written before any code change)

Sources read: `lib/state/git_page_controller.dart`, `lib/state/git_workspace.dart`,
`lib/domain/git/` (repository, credentials, problems), `lib/ui/git/`,
`lib/ui/screens/chat/chat_top_bar.dart` (the chip and what it passes),
`docs/opencode2-protocol-notes.md` §10–11 (shell, VCS), the vision page and
LEARNINGS.md.

| Element of the pick / concept | Real source | Verdict |
|---|---|---|
| The sheet itself, opened from the branch chip | `showGitSheet` from `chat_top_bar.dart`; workspace from `resolveGitWorkspace` (in-app Ubuntu, app-managed Termux, or a server with `ServerCapabilities.remoteShell`) | Real. Gated on where the project runs and the `remoteShell` flag, never the flavor. Unchanged. |
| Changed files with their kind (Modified / New) and +/− counts | `GitRepository.snapshot` (`git status --porcelain=v2`, `numstat`) | Real. Kept. |
| Ticking only some files | `GitRepository.commit(paths:)` commits exactly the given paths (renames carry the old path) | Real. Kept. |
| One tap: save **and** send | `commit(paths, message)` then `push()` on the same repository; nothing in the OC2 VCS API commits or pushes (`/api/vcs*` is read-only), so it runs through the same shell as today | Real: built as one controller call (`commitAndPush`) that stops after the save if the save fails and says the save is kept if the send fails. |
| Commit message "written by the agent" (B's "Write it for me") | The sheet receives only the Git page controller, the GitHub account and the host name; the chat library does not pass the turn's text or the session title, and the sheet cannot send a prompt | **Not available here.** Left out. The message is prefilled with a plain default built from the ticked files ("Update game.js and index.html"), which the person can edit. Hook needed from the chat owner: see "Not done". |
| "Ask the agent to fix conflicts" row (vote C) | Same: no way for the sheet to send a prompt into the conversation | **Not available here.** Left out; the conflicted Save row keeps saying "Fix the conflicts first". |
| Plain target "Send to GitHub · main" | `gitPlaceName` (GitHub, else the remote's host, else its name) + branch head | Real. Kept; origin/main stays under Details. |
| "Send N" when nothing is left to save | `GitBranchState.ahead`, `hasUpstream` from status | Real. Main action when there is nothing to save. |
| "Get latest · N new" as the main action | `behind` after the quiet `fetch` on open (`GitCheck.done`) | Real, but only after the check has finished; before that it stays a row with "Checking…". |
| "Up to date with origin/main" status line (concept A) | `GitCheck` + `behind` | Real, kept as the Get latest row's second line (said once, not in the title too). |
| "main · 2 changes" title (concept A) | Repeats the chip and the file count | Rejected (each count said once). |
| Branch chip | Chat library (not this agent's) | Unchanged; the chip is already neutral (the pick's "not solid mint"). |
| Push sign-in | GitHub device flow (`github_device_flow.dart`) on in-app Ubuntu only; Termux and servers use their own Git sign-in | Real. A push that needs sign-in still offers "Sign in with GitHub" in place. Password or token in a remote address stays refused (`remoteAddressHasPassword`). |
| Remotes and Clone | `addRemote`, `clone` | Real, but the pick removes them from the sheet's surface. Kept reachable one tap down, inside Details, so no action is lost. With no remote at all, Send and Get latest still open Remotes directly. |
| Never push without a tap | — | The compound runs only from its own button; opening the sheet only reads and fetches. |
| Paseo / Codex / OpenCode 1 connections | `resolveGitWorkspace` → `serverCannotRunCommands` unless the project is in the in-app Ubuntu or app-managed Termux | Real: they keep the honest "can't run Git here" state. Nothing faked. |
| File-type icons per row (concept B) | Only a path; a type icon would be guessed from the extension | Left out: the row's tick and name carry it; an extension guess adds an element without information. |
| "2 files selected" line | `KitChoiceList.multi` counts the ticks | Real, but it repeats the button's "Save 2 files": hidden here (`showCount: false`, a new opt-out that leaves every other list unchanged). |

## Not done, and why

- **Agent-written message / "Write it for me"** and **"Ask the agent to fix
  conflicts"** need the chat library to pass the sheet either the last turn's
  summary or a `onAskAgent(String prompt)` callback. The chat library is
  owned by another agent; requested in the report.
- **Full action text at 320 dp and 200 % text.** The kit's buttons wrap to
  two lines (KitAction.md, "200 % text"), so "Save 3 files and send to
  GitHub · main" ends in "…" there; screen readers get the whole label. Showing
  more lines would be a kit change for every screen; not made here.

## What was built

- `GitPageController.commitAndPush(paths, message)`: one busy action that
  reads commit, then push; a failed save stops there; `savedBeforeSend` lets
  a failed send say the save is kept. Token handling is the existing
  per-command drop (no token in any script).
- First step of the sheet: one mint main action from what is waiting:
  "Save N files and send to GitHub · main" (opens the review), else "Save N
  files" (no remote yet), else "Send N saves to GitHub · main", else "Get N
  new changes from GitHub · main", else none. After a send that needs
  GitHub, the main action becomes "Sign in to GitHub" (offered once).
  Rows: Get latest (unless it is the main action), Send (only when there is
  something to send or no remote yet), Save changes (only when conflicts
  stop the main action), Switch branch. Remotes, Clone, the signed-in
  account, the "uses the sign-in on …" note and the exact names sit in one
  fold, "Remotes, clone and details".
- The review ("Save and send"): every changed file ticked, a plain message
  prefilled from the ticked names that follows the ticks until the person
  types, the main button "Save 2 files and send to GitHub · main" that says
  "Saving…" then "Sending to GitHub · main…" while it runs, and a quiet
  "Save without sending".
- Plain words: "Saving…", "Sending…", "Getting the latest…" (were
  Committing/Pushing/Pulling), "Describe what changed first.", "Tick at
  least one file.", "Sending and getting latest use the Git sign-in…".
  English and Arabic.

## Visual passes (412×915 dp, 2× density, blurred shadows, dark)

Concept: `/home/eslam/Storage2/launch-footage/screen-concepts/b3-git-sheet/concept-b.png`
(picked one-step save and send) and `concept-a.png` (the named primary
pinned at the bottom). Pass folders sit beside the finals.

- **Pass 0 (today's sheet):** six rows in three groups plus the account
  row, no main action; the save step had an empty message, a disabled grey
  button and "Write a message that describes the commit."
- **Pass 1:** the compound main action pinned in mint, Remotes / Clone /
  account folded, prefilled message, "Save and send" review. Gaps found:
  "Save without sending" drawn as a second full-width filled button that
  competed with the main one; "Pushing…" (a Git word) in a notice plus the
  button spinner plus the loading bar (three busy signals); after a send that
  needed GitHub, "Sign in to GitHub" appeared twice (notice and row) beside a
  mint "Send" that could not work yet; the chat behind the sheet was empty.
- **Pass 2:** "Save without sending" became a quiet text action (later, on the coordinator's review, the full-width secondary kit button under the mint one, as on the Changes screen); the review
  drops its busy notice and the main button names the half that runs; the
  sign-in row hides while the notice offers it; Busy words in plain
  English; a longer conversation behind the sheet. Gaps found: Arabic drew
  boxes (no Arabic face loaded in the capture); "Get 3 new changes" did not
  name where from; the not-sent notice repeated "Sign in to GitHub" in its
  own sentence and button while the mint button said Send.
- **Pass 3:** Arabic type as the app applies it (Noto Sans Arabic), "Get 3
  new changes from GitHub · main", "Saved here, not sent yet. GitHub needs
  you to sign in first." Gap found: two calls to action after a failed send.
- **Coordinator trims:** the second action is the full-width secondary
  button under the mint one; the review drops "Tick the files to send to
  GitHub · main" and "2 selected", which repeated the button's count and
  target.
- **Final:** after a send that needs GitHub, the one mint action is "Sign
  in to GitHub" and the notice carries no second button and no Git text
  fold.

Final renders, one phone screen each, in
`/home/eslam/Storage2/launch-footage/vision-git-one-step/`:
`1-home-dark`, `1-home-light`, `2-review-dark`, `2-review-light`,
`3-sending-dark`, `4-saved-and-sent-dark`, `5-send-only-dark`,
`5-get-latest-dark`, `6-not-sent-sign-in-dark`, `7-home-320dp-2x-text`,
`7-review-320dp-2x-text`, `8-home-arabic-dark`, `8-review-arabic-dark`.
Capture: `TZ=UTC flutter test --no-pub --concurrency=1 tool/capture/vision_git_one_step_test.dart`.

## Verification

- `flutter test test/git/` (sheet, controller, repository, parsers, sign-in):
  all pass, including new tests for the main action per state, the one-tap
  save and send (commit then push, message prefilled), save without sending,
  a failed save sending nothing, and a failed send keeping the save and
  offering the sign-in once.
- `test/kit_ratchet_test.dart`, `test/file_size_ratchet_test.dart`,
  `test/ui_glossary_test.dart`, `test/l10n_coverage_test.dart`: pass.
- `test/l10n_new_locales_test.dart` fails on `modelSessionScopeNote`, a key
  from the model-picker slice already on the base (b436901e2), untouched
  here.
- `flutter analyze`: no issues.
