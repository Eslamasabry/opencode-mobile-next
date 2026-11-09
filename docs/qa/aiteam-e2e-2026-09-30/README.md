# AI Team end-to-end test, APK 2079 (2026-09-30)

Tester: Claude (emulator only). Wall time about 65 min (14:18 to 15:22). **Result: the project-level AI Team could not be turned on, so the real journey (plan, lanes, findings, merge, promote) is BLOCKED by two P0s.** Only the demo project UI, the turn-on flow up to the safety check, regular chat and first-run setup were exercised.

## Environment
- AVD Pixel_6, android-34 google_apis x86_64, kernel 6.1.23-android14. Emulator lock held for the whole session, physical phones untouched. The emulator process died once (about 14:29, log shows a clean VkDevice teardown, cause unknown); rebooted.
- Build: `opencode-mobile-2079.apk` installed with `-r` over an old state (an old Gas City team was On, "my-app" project, old chats).
- Model: zai-coding-plan key added through the Providers screen with the typing helper (key never shown or logged; the one screenshot that showed the echoed last character was deleted). GLM-5.3 selected as the model. Project used: the emulator's own `my-app`.
- Because of P0-1 a **re-signed copy** of 2079 was used for everything after 14:40 (same debug certificate, only the three `libaiteam_*.so` per ABI replaced by the unstripped files that match the manifest): `/home/owner/Storage/tmp/e2e/opencode-2079-hashfix.apk`. No app code was changed.

## Results

| Test | Result | Evidence |
|---|---|---|
| A. In-app Ubuntu/OpenCode setup (fresh `pm clear` run) | PASS, about 5 min (Linux base 10 s, Git/SSH 109 s, Python 63 s, Node 30 s, OpenCode 36 s, start 36 s) | onboarding.mp4 |
| A. GLM-5.3 key added, model picked | PASS (model list is unsorted, see B-13) | contact-sheet-1 |
| A. Plain chat "Hello", GLM-5.3 | PASS but **slow: first word 115 s cold**, tens of seconds warm (edge shows "No answer yet 33 s") | contact-sheet-1 (c6) |
| A. Hello on the free model after fresh setup | PASS, 13 s total | onboarding.mp4, z13/z14 in contact-sheet-3 |
| B. Wait-for-reply step | PASS with a long reply ("Finishing your current reply... 5 s so far", took 59 s). FAIL once: a reply started 6 s earlier was reported "Done, Took 0 s" (B-6) | i7.jpg |
| B. Stop confirm sheet / "Not now" | PASS. "Not now" reads "Nothing changed. OpenCode stays as it is" | contact-sheet-1 (g2) |
| B. Safety self-check | **FAIL (P0-1, P0-2)**. Steps: reply 0 s, stop OpenCode 14 to 16 s, safety check fails in under 7 s | g3.jpg, g5.jpg, j1 |
| B. Failure copy, Details | Headline in plain words, but Details shows only `engine_bundle_invalid` / `engine_ready_invalid` (a code, no explanation); "Nothing else was changed" is false (B-4) | g3.jpg |
| B. Restart OC protected, ready | BLOCKED by the safety check | |
| B. Re-run after app restart | PARTIAL: the old Gas City team re-proved itself on app start (4 steps, step 3 took 3.5 min "Taking longer than usual, the phone is busy"). New flow re-run gives the same failure each time | e3.jpg |
| C. `phone_engine_acceptance.sh` | BLOCKED. Needs the `.preview` app plus the androidTest APK. The emulator has a 2026-09-29 preview app and no instrumentation. The script also requires the engine boundary, which fails (P0-2) | |
| D. New project sheet (demo) | PARTIAL, see bugs B-7, B-8, B-9 | n3.jpg |
| D. Spec editor, question to Decisions, approve, plan card (demo) | PASS for the flow. Plan card has no "Ask to change" and no server choice (B-10) | p2.jpg |
| D. Lanes, living edge, step folding, collapse all | BLOCKED (demo finishes in seconds; the task page shows "Work completed" right away and no living edge) | p4.jpg |
| D. Needs you in task, Inbox and Work strip (demo) | PASS | contact-sheet-3 (s2) |
| D. Checker findings, fix, re-check | BLOCKED (demo never produces findings) | |
| D. Merge queue (demo) | PARTIAL: button merges silently, no receipt or progress (B-11) | w2 |
| D. Promote dev to main | BLOCKED: not reachable anywhere in the demo | |
| D. Stop project confirm (demo) | PASS | |
| D. Budget reached / chat-first / app killed mid-task / digest timing | BLOCKED (need the real engine) | |
| D. Board, timeline, servers, roles, team settings (demo) | PASS, with polish bugs B-12, B-14, B-15, B-16 | contact-sheet-2 |
| D. Rotation, wide layout | NOT RUN | |
| D. 2.0x text | PARTIAL: no overflow, but spacing bugs B-17, B-18 | y1.jpg |
| D. Dark and light | NOT RUN properly (app stayed in its own theme after `cmd uimode night yes`; the fresh install showed dark) | |
| E. Regression in chat: new chat, reply streams, stop button present, queued second message, model picker | PASS (queued message sent while a reply ran, edge shows wait time). Re-opening a running chat briefly shows a stale transcript (B-19) | contact-sheet-3 |

Timings: chat first word 115 s cold on GLM-5.3 (13 s on the free model); plan time, lane pickup and check time not measurable (engine blocked). Safety check failure 7 s after the stop step.

Videos: `onboarding.mp4` (2.5 min, sped up; shows setup and two replies on the free model. It contains one stray "XHello" composer frame from the test keyboard, and the model is the free one, not GLM). **`ai-team-enable.mp4` was not made**: the journey cannot reach "ready".

## BUGS

**P0-1. Release APK fails the engine bundle hash check on every device (engine/backend, build).**
Steps: install 2079, AI Team, Turn on AI Team on this phone, Stop and continue. Expected: proof runs. Actual: "AI Team didn't start", Details `engine_bundle_invalid`. Cause: Gradle `stripReleaseDebugSymbols` strips `lib/*/libaiteam_*.so`; the packaged files' SHA-256 (e.g. x86_64 engine `687ca5...`, arm64 `606ed8...`) differ from `assets/aiteam-engine-manifest.json` (`9e4efb...`, `9aa1cf...`). Verified by hashing the APK. The unstripped files in `android/app/src/main/jniLibs` match the manifest. Fix: keep debug symbols for these libs (`packaging.jniLibs.keepDebugSymbols`) or hash the stripped output. Screenshot g5.jpg. Probably also breaks the arm64 phone.

**P0-2. On Android 14 the safety probe is killed by seccomp instead of reporting "unsupported" (engine/backend).**
After fixing hashes: `libaiteam_sandbox` dies with SIGSYS, syscall 444 (`landlock_create_ruleset`) twice at 14:49:06 (logcat). The UI shows `engine_ready_invalid`. Expected: on a device that cannot run the protection, a typed "this phone can't run it" result. Actual: a generic start failure, OpenCode left stopped. The app's seccomp filter on this image blocks Landlock, so the engine cannot run on an Android 14 emulator at all. The AVD `OC_API35` exists and may allow it (not used, rules said Pixel_6 only). Needs a re-run on API 35 or the owner's phone. Screenshot g3.jpg.

**P1**
- B-3 (UI+engine). After a failed turn-on, OpenCode stays stopped. The banner says "OpenCode on this phone isn't answering" and Work is blank until the person finds Restart. Expected: the flow restarts OpenCode itself on failure.
- B-4 (UI, copy). Failure text "Nothing else was changed" is false, OpenCode was stopped and terminals closed. Details only holds a raw code.
- B-6 (UI). Reply wait passed "Took 0 s" when a reply had started about 6 s earlier (14:46:05 send, 14:46:11 turn on; the same reply was still running at 14:47:32). Second attempt waited correctly.
- B-7 (UI). New project sheet does not show the measured or estimated host cost (R-02). Demo says cost is not measured, but the real sheet needs the line too. Not testable live.
- B-8 (UI). Repo name and folder typed in the sheet do not count until "Add repo" is tapped; the disabled button then says only "Add a goal and at least one repo" while both are filled.
- B-10 (UI). Plan card has no "Ask to change" or "Not yet" and no per-task server or dependency choice (R-13, R-21). Risky-phase flag reads "Pause for review after this phase" instead of "Review gate · risky".
- B-11 (UI). "Merge checked work into dev" gives no confirm, progress or receipt (R-61). Promote dev to main (R-64) is not reachable in the demo.
- B-2 (UI). A previously-on Gas City team stays on after updating; none of the project-level screens or "New project" appear there (Quick task only, "The planner is off on this host"). The person has to turn the team off first. Expected a migration path.
- B-1 (perf, engine). Chat first word on GLM-5.3 took 115 s cold with the old team On (matches the "2 min with Gas City on" note in the requirements). After app restart, enabling the team took 3.5 min at "Opening the task store".
- B-5 (UI). Roles: Model and Fallback model are free-text fields, not pickers. Checker shows no read-only marker.

**P2**
- B-9 Budget hint says a total is optional while the button hint says "Enter a positive per-day limit and total limit".
- B-12 Board opens on the empty Backlog tab while the only task is in Done; segmented control and timeline filter card touch both screen edges (no gutter).
- B-13 Model picker list is not sorted (GLM-5.3 shown after GLM-5-Turbo and GLM-4.7).
- B-14 Project overview groups Board, Timeline, Servers and Settings under the "Cost" heading.
- B-15 Servers: "Home PC, 0 of 8 lanes busy" while the project says "up to 3 lanes". Lumen merge queue shows "Waiting for dependencies" for tasks of milestones that read 100 %.
- B-16 Timeline is full of identical "Simulated work advanced" rows (demo). "Parallel" default shows max lanes = 1.
- B-17 At 2.0x text the "Merge checked work into dev" button touches the next heading; the pinned Start planning bar plus its hint leaves only a few form rows visible.
- B-18 On this phone "AI Team" in Settings reads "On · This phone" while only the demo is open.
- B-19 Re-opening a chat that is working shows a stale transcript for about 5 s with no loading hint.
- B-20 The On-this-phone page shows "Computer at 127.0.0.1 isn't answering" while OpenCode is reported ready; Inbox lists my own Restart as "Restarted by itself".
- B-21 Onboarding keyboard: first keystroke often dropped and "X" prefix appeared (emulator IME, likely harness; not counted).

## Fixed in (UI, branch crit/aiteam-integration)

| Bug | Fixed in | Note |
|---|---|---|
| B-3 | 3092aa78 | Failed turn-on restarts OpenCode and says so (UI half) |
| B-4 | 3092aa78 | Failure copy states the real state; Details = sentence + code |
| B-6 | 3092aa78, 61f2da05 | Reply wait reads `replyInFlight`; chat reports a just-sent prompt. Root cause of the 6 s lag not confirmed on a device |
| B-7 | 470f526f | Host cost line; "Not measured on <host> yet" when unknown |
| B-8 | 470f526f | Typed repo counts; hint says what is missing |
| B-9 | 470f526f | Budget hints agree |
| B-5 | 470f526f | Roles use the model picker; Checker read-only. Demo roles editor still typed (no catalog) |
| B-10 | 723869a1 | "Not yet", per-task server, "Review gate · risky". NOT done: per-task dependency choice, per-phase flag on the card |
| B-11 | 723869a1 | Confirm, progress, receipt row; Promote dev to main on the overview. Real-engine receipts need the adapter |
| B-2 | 353d6c75, e2a8e150 | Migration screen: switch, keep old team, demo |
| B-12 | e2a8e150 | Board opens on first non-empty column; gutter |
| B-13 | e5bc8c02 | Models sorted by family, newest first |
| B-14 | e2a8e150 | "Project pages" heading |
| B-15 | e2a8e150 | Lane limit and merge wording agree |
| B-16 | 723869a1 | Timeline folds repeated rows; default lanes 3 |
| B-17 | 470f526f, 723869a1 | Hint scrolls at 1.5x+; flat cards keep a gap |
| B-18, B-20 (address) | e71546a0 | Settings row reads Off when unproven; "This phone" not 127.0.0.1 |
| B-19 | not fixed | Not reproducible without a device; blind edit risked the cached-excerpt contract |
| B-20 (Inbox "Restarted by itself") | not fixed | Cause in `lib/builtin/builtin_server_recovery.dart`, needs device/log check |
| P0-1, P0-2, B-1 | Codex | Engine branch |

## Counts
P0: 2, P1: 10, P2: 12 (B-21 not counted). UI vs engine: P0-1 and P0-2 are engine/build (Codex); B-1 engine; B-3 both; the rest UI (Claude).

## Files
contact-sheet-1-setup-and-enable.jpg, contact-sheet-2-demo-projects.jpg, contact-sheet-3-misc.jpg, g3/g5/i7/n3/p2/p4/u2/y1/e3.jpg, onboarding.mp4. The emulator lock is released.
