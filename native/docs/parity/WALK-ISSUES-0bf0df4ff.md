# Native preview QA, build 0bf0df4ff (OpenCode Native 3.0.0-dev, versionCode 10003)

Emulator OC_API35 (1080x2400), Solid dark look. Server: opencode2 2.2.5 on the PC (port 4123, reached as 10.0.2.2:4123), started in a fresh
seeded git repo `demo-app` (README.md, package.json, src/, 2 commits + 1 uncommitted edit), isolated HOME/XDG, free model
deepseek-v4.1-flash-free. Password was a throwaway value; the saved server entry was updated with "Replace".
Coverage: about 35 minutes of driving, ~110 screenshots. Style chooser skipped as told. Phone-hosted (in-app) server NOT tested:
the emulator has no built-in Linux set up, "Folder on this phone" says "OpenCode on this phone isn't set up in this version yet",
so B-068 was verified only against a PC-hosted OpenCode 2 server.

Screenshots with names like 05-project-detail.png, 30-lists/31-projects-filter-open.png, 50-projects/05-* are the launcher screen
shown after a crash (kept as evidence). Tooling note: during a run the green composer glow makes `uiautomator dump` fail intermittently.

## Result table
| # | check | verdict | evidence |
|---|---|---|---|
| 1a | First send on a new conversation ("Hi") confirms and reply streams | PASS (PC server only) | "Working" at 4 s, reply "Hi! What can I help you with today?" at 16 s, no "not confirmed" row. 10-chat/06, 07 |
| 1b | Send inside existing chat | PASS | message appears at once, turn starts, approval card arrives. 10-chat/09, 15 |
| 1c | Send later (long-press Send) | PASS with caveat | opens "New scheduled prompt" sheet (Once/Every day/Weekdays/Custom). Caveat N-44. 10-chat/21 |
| 2 | Composer on screen in every chat state | PASS | empty, running (Steer while it works + Stop), waiting on approval, finished, stopped, server-down error all show the composer. Find bar replaces it by design. 10-chat/06,15,17,35,52. See N-38 UI overlap, N-45 |
| 3 N-20 Stop vs approval card | PASS | Stop with card visible removes the card, row says "Stopped", composer returns. 10-chat/36, 37 |
| 3 N-24 Terminal | FAIL (MISSING) | no Terminal in chat Go to, Commands launcher ("term" = no results), Settings search, Home More. 60-terminal/ |
| 3 N-26 largest text (font_scale 2.0) | FAIL | chat composer text, chips and transcript overlap each other. N-46. 70-largest-text/02 |
| 3 N-27 Find in chat | PASS | "README" gives "1 of 5 matches" with previous/next. 20-menu/05, 06 |
| 3 N-30 row long-press actions | STILL OPEN | only Open, Share, Details, Open related, Continue on computer. 30-lists/30-longpress-row |
| 3 N-31 agent sheet under keyboard | STILL OPEN | sheet shows only title and Done while keyboard is up. 90-agent/90-agent-chip-with-keyboard |
| 3 N-32 chip row | STILL OPEN, worse | model chip cut at the right edge, no Project chip at all, no way to pick a project. 50-projects/04 |
| 3 N-33 Send long-press | FIXED (see 1c) | |
| 3 N-34 Git add remote | STILL OPEN | "Remotes: None yet" but only "Clone a repository". The misleading "add one below" text is gone. 90-agent/80-git-1 |
| 3 N-35 Compact | STILL OPEN | Context sheet 8 % used, information only. 20-menu/16-context |
| 3 N-36 Projects dead ends | IMPROVED | "Folder on this phone" greyed with a reason; "New project" no longer a dead end but gone entirely (N-41) |
| 3 N-37 approval card always-allow | STILL OPEN | card only has Deny/Allow; edit card shows only the file name. 10-chat/40 |
| 4 Settings | PASS | Server, Agent (model, providers, usage, tools), Conversations (phrase suggestions, voice), This app (notifications, background, appearance, language, privacy, updates, storage), Help (tips, what works, report, about) all open, no crash. 40-settings/ |
| 4 Projects incl. New project | FAIL | list opens; New project missing (N-41); tapping a project row CRASHES (N-39) |
| 4 Conversation lists: filters | FAIL | "Running" filter works; "All projects" chip CRASHES (N-38) |
| 4 Row long-press | PARTIAL | menu works, lacks rename/pin/archive/delete (N-30) |
| 4 Chat menu | PASS | Find, Rename, Fork, Copy transcript, Go to (20 entries), More (continue elsewhere, import). 20-menu/01-04 |
| 4 Commands launcher | PASS | grouped Navigate / Model and agent / OpenCode (+ server commands), searchable. 20-menu/04 |
| 4 Running-work sheet | NOT FOUND | no control for it in menu, plus menu or running row; tapping the running row only expands it |
| 4 Clear draft | PASS | plus menu > Clear draft text: "Draft text cleared" with Undo. 20-menu/09 |
| 4 Collapse steps | PASS (per step) | tapping a step row toggles it; no collapse-all found. "Expand reasoning" entry exists under Go to. 20-menu/13 |

## New issues (N-38 onward)
| id | screen | type | what I did | what happened | evidence |
|---|---|---|---|---|---|
| N-38 | Conversations > "All projects" filter chip | CRASH | tapped the chip (reproduced twice, fresh process) | App closes. `java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints...` (a scrolling list inside another scroller / unbounded sheet). R8 hid the app frames | 50-projects/crash-N38.txt |
| N-39 | Projects > any project row | CRASH | More > Projects > tapped "demo-app" (reproduced, both rows) | App closes. `IllegalStateException: Resource with ID='string:linuxSvcTitle' has more than one file: composeResources/.../values/strings_common.commonMain.cvr, .../strings_linux_services.commonMain.cvr`. The same string id is defined in two resource files | 50-projects/crash-N39.txt |
| N-40 | Chat, tool approvals | BROKEN | sent prompts that need approval (run pwd, run uname, write file then read). 7 requests: 3 never showed a card | The server holds a pending permission (confirmed with GET /api/permission/request, with the conversation's directory header) but the chat shows only "Running pwd" / "Reading 1 file" forever, with Stop. Reopening the chat and restarting the app does not bring the card back. Stop is the only way out. Pattern seen: the second request in a turn (after Allow) and some first requests are missed | 10-chat/18, 32, 34, 41 |
| N-41 | Projects | MISSING | looked for New project | Projects offers only "Open a folder on the server" and "Folder on this phone" (disabled). No New project. Wave-1 note says it should exist | 50-projects/02 |
| N-42 | Projects list | UI | looked at the project list | two projects both named "demo-app" (one is the previous walk's seeded repo under the scratchpad, one is mine) are indistinguishable except by a clipped path; row shows the long path in the subtitle only | 50-projects/02 |
| N-43 | Back from first chat | UI | started a chat with Start, then Back | returns to the empty New conversation form instead of the list | 10-chat/ (state seen before 30-home-after-chat) |
| N-44 | Send later sheet | UI/BROKEN | scheduled "say the word done" | sheet's pickers read "Today / 8:45 AM" (in the past) while the button reads "Schedule for Tomorrow 09:00". After tapping, no confirmation, the draft stays in the composer, and the server answers "Scheduled tasks are disabled; set experimental.schedules in config", so nothing was scheduled and the app did not say so | 10-chat/21, 22 |
| N-45 | Chat transcript | UI | looked at finished chats | a grey "Server message" row appears under every turn; the header has no background so scrolled transcript text draws over the title (e.g. "Shell pwd" over "demo-app · Computer"); each turn shows duplicate "Ran 1 command" rows | 10-chat/10, 18, 22 |
| N-46 | Composer, all states | UI | typed a message / opened keyboard | the dictation mic icon sits on top of the first characters of the text ("Ster while it works", "run" + mic). At largest text the composer overlaps the transcript completely | 10-chat/08, 14; 70-largest-text/02 |
| N-47 | Chat title after server loss | UI | stopped the server, reopened chat | title becomes "Untitled conversation" and the whole transcript is replaced by "Couldn't open this conversation"; draft is kept and send is disabled (OK) | 10-chat/52 |

Notes: Settings search for "terminal" finds nothing. Language list shows Español at 20 % translated. Notifications are off on the emulator; the page explains it well.

## Top 3 to fix
1. N-40 missed approvals: a pending permission that never shows leaves the conversation hanging with no way forward except Stop. Poll the pending list on open and on resume.
2. N-38 and N-39 crashes (All projects chip, project row): both are one-line layout / duplicate-resource bugs and block lists and projects.
3. N-24/N-41/N-30 missing parity features: Terminal, New project and row actions (rename, pin, archive, delete), plus N-26/N-46 composer layout at large text.
