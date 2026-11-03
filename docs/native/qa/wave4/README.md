# APK QA — native preview, wave 4 (2026-11-03)

**Verdict: FAIL. Do not give this build to the owner.** No release APK was built or copied to the download folder.

The build is much better than wave 1: no crash, Stop works, steering works, Markdown, code blocks and tool rows all render. One S1 is left. **Sending still fails often (B2)**, and I found the cause: stale pooled connections (see "B2 root cause" below). Expect a phone to hit it whenever the user sends 6–15 s after the app's last request.

- APK tested: `qa/p12-wave4-030afe554-preview-debug.apk` (from the VPS `~/wt/p12-apks/`)
  - SHA-256 `2ce2c8f8332b5c6e0b10f9995af445ac85c3913b1de58bae4f4fd7b173c4038f`
  - package `io.github.eslamasabry.opencode_mobile.native`, versionCode 10003, 3.0.0-dev
- Source: `native/p12-integrate-2026-11-03` at `c6d14e8d6` (= `030afe554` + one docs line), worktree `/home/eslam/Storage2/wt/apk-qa-wave4`
- Device: emulator AVD `OC_NATIVE_PREVIEW_20261102` (API 35, software GPU, 720×1280), fresh install
- Backend: OpenCode 2 stable CLI on this PC, port 46400, throwaway project `apk-qa-wave4-server/project`, model **Z.AI Coding Plan · GLM-5.3-Flash**
- A second server entry, "QA relay" (port 46401), goes through a small logging relay. It records request lines, request bodies and socket closes (`qa/relay.log`), never auth headers.
- Full timeline: `qa/LOG.md`. Screenshots are one screen per image, numbered in `qa/`.

## Wave 1 bugs, by id

| # | Wave 1 sev | Now | Evidence |
|---|---|---|---|
| B0 crash on chats with several notices | S1 | **FIXED**: "READY" (3 `model-switched` notices) opens fine | 06 |
| B10 Stop does nothing | S1 | **FIXED**: Stop → `POST /interrupt` 200 within 2 s, and the reply stops. The footer copy is wrong, though (N3). | 44–47, relay.log 08:34:06–08 |
| B2 Send fails without reaching the server | S1 | **STILL BROKEN**: 6 failures in about 17 sends, on both servers: first send in a new chat (twice), send right after the model pick, sends after 12 s idle (twice), the tool prompt on the relay. The bubble now says "The server can't be reached right now", with Details `Network.Reset`. Try again usually works, but sometimes fails once more. | 09, 14, 22–24, 40; relay.log 08:32 |
| B11 delivered steer marked unsent | S2 | **FIXED**: the first tap sends `delivery:"steer"`, the model follows it, and no "wasn't sent" bubble appears. A steer can still hit B2. | 48–50 |
| B9 tool calls invisible | S2 | **FIXED**: the work line "Read 2 files · searched once" is tappable and opens tool rows (Read project, Read calc.py, Find files); a row opens its output | 41-02, 42, 43 |
| B7 Markdown raw | S2 | **FIXED**: headings, bold, italic, numbered and bullet lists, inline code. Code block: monospace, syntax colours, sideways scroll, Copy | 34-long-06/10, 35–37 |
| B6 keyboard leaves no room | S2 | **PARTLY**: about 95 dp of conversation stays visible above the composer with the keyboard open (wave 1: one line). The composer is still three rows (chips, "Message" label, field) and the field is only half the width, next to the mic and a large Send. | 59, 22-stream-6 |
| B8 steer box crushed | S2 | **FIXED**: while a reply runs, the field has the full width and Stop + "Add to this turn" sit underneath | 34-long-06 |
| B3 model picker | S2 | **FIXED**: search (it also matches provider names, e.g. "Z.AI"), groups named by provider, the current model marked, keyboard dismissed. Small mismatch: the picker marks "Server default · Current" while the chip says "Pulse Alpha Free". | 17–20, 31–33 |
| B5 failed reply has no reason | S2 | **FIXED**: "The model's provider didn't accept the sign-in. Sign in to that provider again, or pick another model." + Details | 16 |
| B1 "couldn't load" with >100 chats | S2 | **FIXED**: no false line. New cost: each list refresh now loads both pages, and that happened about 4 times in 4 s after one send (relay.log 08:26:21–25). | 05, relay.log |
| B4 picker "could not be loaded" on first open | S3 | **Not seen** (picker opened first time on both servers) | 17, 31 |
| B12 Arabic UI flips English replies | S3 | **PARTLY**: in Arabic, an English assistant reply is left-to-right and an Arabic reply right-to-left (fixed). The **user bubble and the thinking text** are still right-to-left with the full stop on the wrong side (".Answer in one line"). | 69, 71 |
| B13 chip says "Server default" after restart | S3 | **PARTLY**: after force-stop + reopen the chip said "Server default" for more than 10 s while the server has `zai-coding-plan/glm-5.3-flash` (variant `default`), then it corrected itself | 56, 57, 59 |
| B14 grey bars after language switch | S3 | **FIXED**: bars keep the app ground in Arabic and back | 67, 72 |
| B15 layout/copy batch | S3 | **PARTLY**: first-run side margins fixed (01); Conversations title no longer cut (05). Still open: "Connect" shown next to "Connected" (07, 77); Settings › Server › Servers takes three levels (76, 77); thinking is unlabeled grey text (25-stream, 41-02); the chat title is crushed (now worse, see N1). | as listed |

## New problems

Severity: **S1** = blocks shipping, **S2** = a core feature is badly degraded or there is a privacy/security risk, **S3** = polish or wrong copy.

| # | Sev | Problem | Evidence |
|---|---|---|---|
| N1 | **S2** | Chat header: once "Context N % used" appears (every chat with a reply), the conversation title is squeezed to nothing ("Q…" with a chevron; uiautomator gives the title 2 px). In Arabic the title is missing completely. You can't see which conversation you are in. | 06, 29, 34-long-*, 69 |
| N3 | **S2** | A user's own Stop is reported as a failure: "Didn't finish. The reply stopped with an error. Send again, or pick another model.", with Details "Step interrupted". It should say the reply was stopped. | 46, 47 |
| N6 | **S2 privacy** | The password field on the add-server form exposes the real password as plain text in the accessibility tree (uiautomator `text=` is the password; the screen shows dots). Any accessibility service can read it. Compose fix: give the field `password()` semantics, or use the secure text field. | 04 (screen shows dots; the dump is in my session only, masked) |
| N2 | S3 | The approval-mode chip at the end of the model/Attach row is clipped: it shows ".." or an empty pill at the right edge. In Arabic it reads "اس…". | 06, 08, 22-stream-6, 69 |
| N4 | S3 | An unexplained Android notification-permission dialog popped up the moment I came back from the file picker into the chat. | 53 |
| N5 | S3 | After a failed send, the words stay in the message box **and** appear in the failed bubble. Send plus Try again would send the message twice. | 23 |
| N7 | S3 | "New chat" in Conversations opens a server page (Saved servers → Start conversation) instead of a new chat: one extra step and a heavy page. | 07 |
| N8 | S3 | A saved server can't be edited (for example a changed password); the only option is Remove and add again. | 11 |
| N9 | S3 | Code block "Copy": no visible confirmation. A one-line block also keeps a tall empty area under the line. | 36-scroll-2, 38 |

## B2 root cause (for the integrator)

**Cause:** the OpenCode 2 server closes an idle keep-alive socket after **6 s**. The app's OkHttp pool keeps idle sockets for **15 s** (`core/net/.../okhttp/OkHttpEngineFactory.kt:87`, `ConnectionPool(5, 15, SECONDS)`) with `retryOnConnectionFailure(false)` (line 90). A send started 6–15 s after the app's last request reuses a socket the server has already closed. The send pipeline starts with a GET (`GET /api/session/{id}`, then `POST /model`, then `POST /prompt`). That GET fails at once with a reset, and the send stops before any byte reaches the server. Details shows `Network.Reset`. Nothing is sent, so no prompt is lost or duplicated, but the user sees "wasn't sent".

**Evidence:**
- Raw socket probe from the PC: after `GET /api/agent` the server closed the idle socket after 6.0 s.
- relay.log: every idle connection shows "server side closed" about 6 s after its last request. The 08:32 tool-prompt send failed and **no request reached the relay**: the app wrote into c16, which the server had closed at 08:32:03.
- Timing on the direct server: sends after about 12 s idle failed twice in a row; an immediate retry worked; sends after 5 s or 25 s idle (the pool had dropped the socket) worked.
- The wave 1 pattern fits: the first send in a new chat, after the model sheet, after the file picker and the first steer all come a few seconds after the last request.

**Repro:**
1. Open any conversation and wait for the screen to finish loading.
2. Type a message, wait 10 s without touching anything, and tap Send.
3. "Your message wasn't sent", Details `Network.Reset`, and no `POST /prompt` on the server.

**Fix options (integrator's choice):**
- Keep the pool's idle keep-alive below the server's, for example 4 s.
- Or allow OkHttp's retry for idempotent GETs only (the MutationGuard already blocks POST resends), so a stale socket on the first GET is retried on a fresh connection.
- Or evict idle connections before a send.

Add a NoResendTest-style loopback test: the server closes idle sockets after 1 s and the client sends after 2 s; the send must reach it exactly once.

## Not done / notes

- **Release preview build (step 5) was skipped** because B2 (S1) is still broken. Nothing was copied to `native-preview-download-20261102/`.
- Frame timing from a software-GPU emulator during the long reply: 3441 frames, 6.0 % janky, p50 30 ms, p90 40 ms, p99 65 ms. That is better than wave 1 (15 % janky) but only a rough sign, not a phone number. No visible stall; the stream reveal is smooth.
- No crashes or ANRs: `logcat -b crash` is empty, and exit-info shows only my two force-stops.
- Both server entries point at the same backend, so each conversation appears twice in Conversations. That comes from my test setup, not a bug.
- Arabic prompts were typed in English ("Reply only in Arabic …") because `adb input` can't type Arabic.

## Housekeeping

- Credentials: I printed the test server's one-run password into my session transcript once by mistake (tailing serve.log). I restarted the server right away to rotate it and re-added it in the app. Neither password is valid now (server stopped). They are not in any file; `qa/` was scanned for both.
- Servers stopped by exact PID: opencode2 2425724 (first run 2294648) and relay 2455568. Ports 46400/46401 are free. Ports 4150/4199 and the owner's servers were not touched.
- Emulator: I waited for the issue-111 OC_API37 emulator to finish (until 08:10), booted mine under `flock emu.lock` (qemu inherited the lock) and stopped it with `adb emu kill` at 08:43. Then a queued `flock emu.lock sleep 36000` hold took the lock (PID 2416087, child `sleep` 2491850; it started 08:11:06 with parent 1, and it is **not mine**). The issue-111 worker's `flock … emulator -avd OC_API37` (PID 2458667) is waiting behind that 10-hour hold. The coordinator may want to check whose it is.
- The PC's default OC2 store holds the QA conversations ("Greeting", "Word FIVE reply test", "New conversation"s) under project `apk-qa-wave4-server/project`. Only the preview package was installed, cleared and uninstalled. The real app and the owner's phone were not touched.
