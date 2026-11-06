# Native preview QA, build 27bc42b60 (Glass merged), OC_API35 emulator

Server: own opencode2 2.2.5 started inside seeded git repo demo-app (README.md, package.json, src/app.ts 53 lines, src/version.ts,
2 commits + uncommitted README/app.ts edits). Flutter comparison: installed build 4520 (different server/project).
Coverage: PARTIAL, 45 screenshots. The Style chooser CRASHES (N-29), so Glass/Soft/Brutal/Paper/Signal could not be viewed: only
Solid light and dark were captured. Not walked: widgets list, Voice screen, Usage beyond its empty state, Agent sign-in/Remove
(not reachable: install needs the built-in server), Git remote/sign-in entry (no add-remote control exists, N-34).

## Rechecks of N-19..N-28
| id | verdict | evidence |
|---|---|---|
| N-19 chat field raises keyboard | PASS | mInputShown=true after one tap, full sentence typed and stayed in the chat. 03-chat/04-composer-keyboard-typed-native.png |
| N-20 Stop vs approval card | NOT TESTED this round (approvals answered with Allow; card appeared, then 4 more approvals in sequence, then summary). 03-chat/02-approval-card-native.png |
| N-21 Details | PASS | Details sheet: Server, Project, folder, Model, Agent, context used, tokens. 03-chat/09-details-native.png |
| N-22 sheets in accessibility tree | PASS for Details, Timeline-style sheets now dumpable; Project/Agent sheets also listed |
| N-23 Agents install list | PASS (partial) | Install list present (Claude Code 328 MB, Codex, Gemini CLI, Qwen Code...), Install opens a "Set up Claude Code" sheet. Sign-in/Remove unreachable. 05-other/12, 13 |
| N-24 Terminal | NOT RETESTED |
| N-25 Git | PASS | Branch, Switch branch, Get latest, Send, Remotes, Changes list with +/- counts, commit message with "Save 2 files" (committed OK: "Saved 2 files.", Recent list shows it), diff viewer. 05-other/04..06 |
| N-26 largest text | NOT RETESTED |
| N-27 Find in chat | NOT RETESTED |
| N-28 Agents "On this phone" row | PASS (now a heading with agents beneath) 05-other/12-agents-native.png |

## New issues (from N-29)
| id | screen | type | what I did | what happened | what Flutter does | screenshots |
|---|---|---|---|---|---|---|
| N-29 | Settings > Appearance > Style | CRASH | tapped the "Style" chip (twice, reproducible; fresh process each time) | App closes at once. `java.lang.StackOverflowError: stack size 8188KB`. Endless recursion `LayoutNodeDrawScope.getDensity` <-> `CanvasDrawScope.getDensity` (8,238 frames). First app frames: `io.github.eslamasabry.oc.ui.kit.choice.KitOptionCardKt.StyleSample$lambda$0$0$0(KitOptionCard.kt:408)` called from `io.github.eslamasabry.oc.ui.kit.foundation.BackdropSourceNode.draw$lambda$1(KitSurfaceGlass.kt:289)` (draw inside a `record{}` layer). The style samples (Glass backdrop) draw with a DrawScope whose density delegates to itself. In-app diagnostics logged "java.lang.StackOverflowError ... / Application error". Class: `KitOptionCard.StyleSample` / `BackdropSourceNode`. This blocks choosing any style | Style chooser opens | 06-styles/00-style-chooser-native.png (shows launcher after the crash) |
| N-30 | Conversation row long-press | MISSING | long-pressed a conversation | Menu has only "Open" (no Rename, Pin, Archive, Delete, Fork, Copy link) | Flutter long-press: not captured (shot saved: 02-conversations/10-row-long-press-flutter.png), row actions live in menus there | 02-conversations/10-row-long-press-native.png |
| N-31 | New conversation > Agent chip | UI | opened "Agent and model" while the prompt had focus | The sheet is squashed by the keyboard: only the title and Done show; the agent/model options are hidden until the keyboard is dismissed | Flutter hides the keyboard first | 05-other/10-agent-chip-sheet-native.png |
| N-32 | New conversation | UI | looked at the chip row | Row (agent, model, project) is wider than the screen; the Project chip is off screen to the right (needs a horizontal swipe), and a stray "What would you like to build?" repeats under it | Flutter lists Model and Project as full rows | 02-conversations/03-new-conv-native.png, 04-new-conv-chips-scrolled-native.png |
| N-33 | Send long-press | MISSING | long-pressed Send | Sends immediately, no "send later / steer / queue" choice | Flutter: not captured in this run | 03-chat/05-send-long-press-native.png |
| N-34 | Git > Remotes | BROKEN | opened Git on a repo with no remote | Text says "Add a remote below first" and "No remote yet. Add one below" but there is no add-remote field or button anywhere (only "Clone a repository") | not compared | 05-other/04-git-bottom-native.png |
| N-35 | Chat menu / Context | MISSING | looked for Compact | No Compact action in More, Go to, or the Context window sheet (8 % used, info only) | Flutter parity audit lists Compact | 03-chat/10-context-compact-native.png, 03-chat/07-menu-more-native.png |
| N-36 | Projects | UI | More > Projects | "New project" says the server can't make folders from the phone and "Folder on this phone" says not set up yet; both entries are dead ends that still look like actions | Flutter: New project, Search this phone, Opened before, Choose a folder | 02-conversations/12-projects-list-native.png, 02-conversations/05-new-conv-project-picker-flutter.png |
| N-37 | Approvals | UI | asked to run ls -la | Several approvals in a row (ls, cat...) each needs a tap; there is no "Allow for this conversation" shown on the card | Flutter has always-allow on card | 03-chat/02-approval-card-native.png |

## UPGRADE ideas (best first; BIG marked)
1. BIG Fix the Style chooser (N-29) and ship a static preview image per style so the chooser never draws live Glass samples. Why: it is the headline feature of this build and it crashes.
2. Conversation row long-press: Rename, Pin, Archive, Delete (with undo), Fork. Why: expected phone gesture.
3. Send long-press: Send now / Send when finished / Steer. Why: parity and power.
4. Add a Compact action to the context sheet and the menu. Why: the sheet shows 8 % used with nothing to do.
5. Git: an "Add remote" field with a pre-filled origin URL from the last clone, and sign-in right there. Why: today's text points at a control that does not exist.
6. New conversation: three full-width rows (Agent, Model, Project) or a two-line chip wrap; never off-screen chips. Why: Project is currently hidden.
7. Hide the keyboard before any sheet opens from the composer area. Why: sheets are squashed.
8. Approval card: "Allow similar commands in this project" with confirmation. Why: removes the tap storm.
9. Projects: hide "New project / Folder on this phone" when unsupported and say so once at the top. Why: no dead-end actions.
10. File viewer: show Changed files first with a diff toggle (git changes were visible in Git but only a "Changed" tag in Files). Why: one place to review.
11. Predictive back and haptics on Send/Allow/Deny. Why: Android-native feel.
12. Slash commands launcher: add icons and group Built-in / Skills / MCP. Why: scannable.
13. Details sheet: tap Project to open Files, Model to open the picker. Why: shortcuts.
14. Dark/light switch in the chat menu. Why: reading comfort.
15. Home: swipe row for archive. Why: native gesture.
