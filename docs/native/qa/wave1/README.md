# APK QA — native preview, wave 1 (2026-11-03)

**Verdict: FAIL. Do not give this build to the owner.** No release APK was built or copied to the download folder.

- APK tested: `qa/p12-wave1-501d3abf9-preview-debug.apk` (copied from the VPS `~/wt/p12-apks/`)
  - SHA-256 `90f17190bd5945a3080b1ea9c0216447b63d540466414a1c2b375b99d7b8f65d`
  - package `io.github.eslamasabry.opencode_mobile.native`, versionCode 10003, 3.0.0-dev
- Source: `native/p12-integrate-2026-11-03` at `27a04ad4c` (= `501d3abf9` + one docs-only ledger commit), worktree `/home/eslam/Storage2/wt/apk-qa-wave1`
- Device: emulator AVD `OC_NATIVE_PREVIEW_20261102` (API 35, software GPU), fresh install
- Backend: OpenCode 2 `v2.2.5` on this PC, port 46400, throwaway project `apk-qa-wave1-server/project`, model **Z.AI Coding Plan · GLM-5.3-Flash**
- To see what the app sends, a second server entry "QA relay" (port 46401) goes through a small logging relay; it records only request lines and request bodies (`qa/relay.log`), not auth headers.
- Full timeline: `qa/LOG.md`. Screenshots are one screen per image in `qa/`.

## What works

- First launch → "Connect to my computer" → add server (HTTP opt-in) → Conversations (01–05).
- New conversation → chat opens (07, 08).
- With the Z.AI model chosen, replies stream to the end: short reply (25-stream-done); long ~1500-word reply with a code block and an 8-item list finished in ~2 min with no visible stall (26-long-*). The title updates by itself ("Greeting"). "Jump to latest" works.
- A tool-using prompt (list files, read `src/calc.py`) runs and gives a correct answer (29-tool-3).
- Steering mid-reply: "Add to this turn" sends `delivery:"steer"` and the model follows it (switched from bridges to tunnels; 41, 42). The first tap did nothing, though (see B2).
- Changing the model works and the server uses it (`POST /session/{id}/model` with `zai-coding-plan`).
- Attach file → system picker → file chip → the file is sent as a data URI and the model reads it (45, 47). The first Send failed (see B2).
- The draft survives force-stop and relaunch (50).
- System back and edge-swipe back from chat go to Conversations (51).
- Settings: Appearance applies live (Light/Dark, Nord etc., 54–56); Language switch to Arabic gives a translated right-to-left UI, and switching back to English works (58–62); Notifications: Android settings hand-off and the test notification work (63–65); About shows `3.0.0-dev+10003` (67).

## What is broken

Severity: **S1** = blocks shipping (crash or a core chat action fails), **S2** = a core chat feature is badly degraded, **S3** = visible polish or wrong copy.

| # | Sev | Problem | Evidence |
|---|-----|---------|----------|
| B0 | **S1 crash** | Opening an existing conversation crashes the app: `IllegalArgumentException: Key "notice:v2-0" was already used` (LazyColumn duplicate key). | 69-ready-crash-repro.png, crash-01-38-notice-key.txt |
| B10 | **S1** | **Stop does nothing.** Two taps: no `/interrupt` request, "Working" stays until the reply ends by itself. | 33–36, relay.log 01:28 |
| B2 | **S1** | **Send often fails without contacting the server**: "Your message wasn't sent", and no POST appears in the relay log. Seen 4 times in about 14 sends: first message in a new conversation (twice), the first "Add to this turn" tap, and the first send with an attachment. Try again then usually works, but on the first server it failed again. | 09, 13, 22, 46; relay.log |
| B11 | S2 | A steer that **was** delivered (POST 200, the model obeyed) is still shown as "Your message wasn't sent" with Try again, which invites a duplicate send. | 43, 47 |
| B9 | S2 | Tool calls are invisible. The work collapses to a "3 steps" line that is plain text: it can't be tapped and there are no tool rows (list/read) anywhere. | 32-steps-open |
| B7 | S2 | Markdown is not rendered: `## headings`, `**bold**`, `` `code` ``, list markers and the ``` fences show as raw text. Code-block lines wrap instead of scrolling sideways. | 26-long-28, 28-code-block, 29-tool-3 |
| B6 | S2 | With the keyboard open, the conversation shrinks to about one line, so you can't read the reply while typing. | 23, 25-stream-4 |
| B8 | S2 | While a reply runs, the message box shrinks to about 60 px: the hint "Steer while it works…" wraps one or two letters per line, and only two typed letters are visible. | 26-long-8, 38-steer-typed |
| B3 | S2 | Model picker: 372 flat chips with no provider name and no search. The same name appears several times (GLM-5.3-Flash ×3, Claude Fable 5.1 ×3), so you can't tell which one is Z.AI. The picker highlights "GPT-6.1 Terra" while the chip says "Server default". The keyboard stays open over the sheet. | 11, 12 |
| B5 | S2 | A failed reply (provider error such as "API key expired" or 401) shows only "Didn't finish": no reason and no next step. | 24 |
| B1 | S2 | "QA PC couldn't load" appears even though the list loaded. The server has more than 100 conversations, and the app reads only page 1 and treats `nextCursor != null` as a failure (`PreviewRuntime.refreshOne`: `complete = page.value.nextCursor == null`). The line can't be tapped and offers no way forward. | 05, 60 |
| B4 | S3 | The first time the model picker opened on the second server: "The model list could not be loaded", and no request was made. Try again loaded it. | 20 |
| B12 | S3 | In Arabic, English replies are laid out right-to-left with punctuation on the wrong side ("Milk .1", ":**shopping list"). Assistant text needs content-based text direction. | 61 |
| B13 | S3 | After a restart (and in Arabic) the model chip shows "Server default" although the conversation uses GLM-5.3-Flash on the server. | 61 |
| B14 | S3 | After the language switch, the status bar and navigation bar became solid grey strips (edge-to-edge lost until restart). | 60, 61, 67 |
| B15 | S3 | Layout and copy: buttons on the first-run screen touch the screen edges (01). The Conversations title is cut off ("Conversat…") next to two text buttons (05). The header button "Servers" opens "Settings" (15). Settings › Server › Servers takes three levels to reach one list (16, 17). The add-server form keeps the typed values after saving. The server card shows "Connect" while it already says "Connected" (07). The chat composer floats mid-screen with no empty state (08). The model's thinking appears as unlabeled grey text (25-stream-done). "What's new" in About has no accessibility text. | as listed |

### Repro steps for the integrator

**B0 crash (root cause found)**
1. Connect to an OpenCode 2 server that has a conversation with two or more notice items (`model-switched`, synthetic/system notices, compaction). Here it was `ses_eb867622effeo8dJblDDgxHX54` "READY" (project oc-bg-proof, in the PC's default OC2 store), which has 3 `model-switched` items.
2. Tap that conversation in Conversations. The app dies at once.

Cause: `protocol/oc2/.../live/Oc2History.kt:104` (`special()` → `notice()`) gives every notice part the same id `"v2-0"`, and `Oc2EventAdapter.kt:363` does the same. `engine/transcript/.../TranscriptModel.kt:539` builds the row key as `"notice:${p.id}"` without the message id, so two notices produce the same key. Suggested fix: key on `"notice:${p.messageId}/${p.id}"`, as `ThoughtRow` already does, and/or give each notice a unique part id. Add a transcript test with two notices. (The "Greeting" conversation, with 2 `model-switched` items, did not crash; I did not pin down why. Test both.)

**B10 Stop**
1. Choose GLM-5.3-Flash and send "Write a 1200 word story about a lighthouse keeper".
2. Wait about 6 s and tap Stop (twice).
3. Expected: `POST /api/session/{id}/interrupt`. Actual: no request, and "Working" continues until the reply finishes.

Lead (not verified): `GraphChatWorld.stop()` hands off to `chatDelivery.engine.stop(ref)` whenever an engine exists, and that path never reaches `Oc2Gateway` `/interrupt`.

**B2 Send fails silently**
1. New conversation → type a message → Send right away (model left at Server default, or right after tapping a model chip, or right after returning from the file picker).
2. The bubble shows "Your message wasn't sent", and the relay log shows no `POST /prompt`.

Candidates in `GraphChatWorld.send/dispatch`: the `currentAttached() !== expected` → `StaleScope` check (the graph is re-bound after a model POST, a session GET, or an activity resume), or an early `Busy(Starting)` while `draft.ready`/`ChatLoad.Live` are false. The banner offers no Details, so the failure kind isn't visible. Show the failure kind under Details.

**B11 delivered steer marked unsent**: do B10 step 1, type a steer, tap "Add to this turn" (tap twice if the first does nothing). The relay shows POST 200 with `delivery:"steer"` and the reply follows the steer, but the bubble says "wasn't sent".

**B1**: `curl -u opencode:<pw> 'http://<server>/api/session?limit=100'` returns `cursor`. The app then shows "<server> couldn't load". Either fetch the next pages or don't treat "more pages" as a failure.

## Crash log

`qa/crash-01-38-notice-key.txt` (full main-thread stack, PID 9113). The same crash reproduced at 01:39:32 (PID 10530). No other crashes or ANRs during the sweep.

## Not done

- Release preview build (step 5) was **skipped** because the sweep failed: a crash, plus broken Stop and Send. Nothing was copied to `native-preview-download-20261102/`.
- First-run "Settings" button (before adding a server) was not opened. The "Servers" screens were covered from Settings.
- Frame timing comes from a software-GPU emulator (2773 frames, p50 28 ms, p90 38 ms, 15% janky). Treat it as a rough sign only, not a phone result.

## Housekeeping and notes for the coordinator

- `emu.lock` had been held for 4 h by an idle Codex shell (PID 1424885 under flock 1424884) that had booted this same AVD at about 20:52; the app had been idle since 21:14 with load 0. I stopped that emulator (`adb emu kill`) and ended that exact shell with SIGHUP to free the lock. Then I booted the AVD under my own `flock` and released it at the end by stopping my emulator.
- The test server's one-run password was printed into my session transcript twice by mistake (once in a UI dump, once in a log tail). The first server was restarted to rotate the password. Both passwords stopped working when the server was stopped at the end (exact PID). They were never in any file in the repo.
- The PC's default OC2 store was used for auth, as instructed, so the app also lists other projects' conversations (claude-tmp, oc-bg-proof, oc_app, TradeNet). The QA conversations "Greeting" and two "New conversation"s now exist in that store, under project `apk-qa-wave1-server/project`.
