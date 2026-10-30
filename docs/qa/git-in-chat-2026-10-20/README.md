# Git in the chat: emulator proof (2026-10-20)

- Build: release APK **2560** from `feat/git-in-chat` at `904a45eef`, built on the build server with `tool/qa/vps_build_apk.sh 2560`. Signer SHA-256 `1DE5BF08...94D60C` (verified with apksigner).
- Device: emulator `emulator-5584` (AVD OC_Launch_Demo), 838x1860. Never the owner's phone.
- Project: `neon-game` in the app's built-in Linux (`/root/projects/neon-game`, in-app Ubuntu connection). Everything below was done through the UI; the terminal was used only to set up the bare origin (`git init --bare /root/origin.git`, `git remote add origin`, first push), to create three untracked files (alpha, beta, gamma), to push one commit from a second clone, and to read `git log --stat`.
- Screenshots: `/home/eslam/Storage2/launch-footage/git-in-chat/emu/` (one screen per image).

| # | Step (UI only) | Result | Evidence |
|---|---|---|---|
| 1 | Open the chat | Branch chip "master" sits beside the project name | 01-chat.png |
| 2 | Tap the chip | Git sheet: Get latest / Send name origin/master; "3 changed files"; Remotes "origin"; GitHub row | 10-sheet.png |
| 3 | Save changes: untick gamma.txt, write a message | "Save 2 files", gamma unticked | 11-save.png, 12-save-ready.png |
| 4 | Save 2 files | "Committed 57cdce1."; `git log --stat -1` shows only alpha.txt and beta.txt; gamma.txt still untracked | 13-saved.png, 21-term-state.png (and 20-term-log.png) |
| 5 | Switch branch: new branch feature/neon | Chip changes to feature/neon; "Created the branch and switched to it." | 14-branches.png, 15-branch-typed.png, 16-branch-created.png |
| 6 | Send to origin/feature/neon (first time) | "Pushed. This branch now follows the one on the remote." | 17-sent.png |
| 7 | Get latest after another clone pushed a commit | "Pulled the latest commits." | 22-get-latest.png |
| 8 | Remotes: add "backup" with an address containing a password | Refused before anything runs; the way forward is in words; Add stays disabled | 24-password-refused.png |
| 9 | Remotes: add "backup" with /root/backup.git | "Remote added."; list shows origin and backup with their addresses | 25-remote-added.png |
| 10 | GitHub sign-in (code step only) | Sign in to GitHub requests a real device code, shown large with Copy; Open GitHub goes through the external-link confirmation, then Chrome opens. No credentials entered; sign-in cancelled | 30-signin-step.png, 31-signin-code.png, 32-open-github.png, 33-browser.png, 35-cancelled.png |

## Notes
- Before Get latest ran, the row said "nothing new": the app does not fetch on open, so it can only know after the pull. The result line is the truth.
- The device flow worked against GitHub with the app's client id, so device flow is switched on for it.
- Not done on purpose: finishing a GitHub sign-in or pushing to GitHub.
- The sign-in code in 31-signin-code.png is a one-time code that was cancelled; it is not a secret.
