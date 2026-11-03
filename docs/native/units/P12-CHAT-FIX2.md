VERIFIED: green

# P12-CHAT-FIX2: wave 4 QA blockers and Codex header and composer findings

Branch `claude/chat-parity-push-w4-9n35ma`, base `native/p12-qa-wave4-2026-11-03` (`6b451faf`). Every commit carries `[skip ci]`.

Sources:
- the QA report: `docs/native/qa/wave4/README.md`
- the Codex reviews: `docs/native/reviews/p12/chat-header.md` and `composer.md`

Each Codex finding was checked against the current source before it was fixed. All 15 are real; none is false.

## Gate run (this container, JDK 21, Android SDK 37.1)

The final command was:

`./gradlew :core:net:desktopTest :engine:transcript:desktopTest :engine:delivery:desktopTest :protocol:oc2:desktopTest :ui:kit:desktopTest :ui:kit-chat:desktopTest :feature:chat:desktopTest :feature:servers:desktopTest :feature:chats:desktopTest :quality:test :androidApp:testPreviewDebugUnitTest :androidApp:assemblePreviewDebug --continue`

Result: green (see "Final run").

Container note: Maven Central answered `429 Too Many Requests` to Gradle's download bursts. A local init script, `~/.gradle/init.d/mirror.gradle.kts`, asks Google's Maven Central mirror first. It lives outside the repo; no build file was changed.

## B2 (S1): sends fail with no request

**Cause, confirmed by test.** The OpenCode 2 server closes an idle socket after 6 s, and the pool kept idle sockets for 15 s. OkHttp also skips its pooled-socket health check below 10 s of idle time. That means a POST could also be written into a dead socket, which the QA report did not know.

**Fix** (`core/net/.../okhttp/OkHttpEngineFactory.kt`):
1. Idle keep-alive is now 4 s (`IDLE_KEEP_ALIVE_MS`), below the server's 6 s.
2. Before any byte is written on a reused HTTP/1.1 socket, a network interceptor probes it with a 1 ms read. If the server already closed it, the attempt stops with `StaleSocketException`, which proves nothing was sent. It is retried once on a fresh connection, and that includes a mutation. The probe runs before the MutationGuard's attempt counter, so the retry is still the mutation's first network attempt.
3. A GET, HEAD or OPTIONS that fails with an I/O error on a reused connection is retried once.
4. A mutation that may have left is never retried; the guard still refuses any second network attempt. A fresh connection's failure, a timeout or a cancelled call is not retried. Before a retry, the other idle sockets are evicted.

**Tests** (`StaleConnectionTest`: real loopback server, real time):
- "a send 7 s after the last request reaches a 6 s idle-closing server exactly once" (the QA repro)
- "a read on a stale pooled socket is retried once on a fresh connection"
- "a prompt after a stale pooled socket is sent exactly once"
- "a read that fails on a fresh connection is not retried"
- "the pool drops idle sockets before the server's 6 s idle close"

**Red before:** with the old client, 4 of the 5 tests fail, the QA repro among them. The fifth, the fresh-connection case, passes on both. The existing `NoResendTest` cases still pass, including "POST on a reused connection that drops after the request is sent once" (DispatchUncertain, exactly one POST).

## QA wave 4 items

| Id | Status | Test |
|---|---|---|
| N3 S2 Stop shown as failure | **fixed**: the transcript marks an interrupted or aborted step (`Step interrupted`, `MessageAbortedError`) as stopped, not failed. The footer says "Stopped", with no error line and no Details. | `TranscriptReducerTest` "N3 … Stopped, not a failure" and "N3 … a real error is still a failure" |
| N6 S2 privacy, password in the accessibility tree | **fixed, red first**: before the fix the test saw the password in `EditableText` and `InputText`. The kit secure field now sets `password()`, and its editable and input text are dots, or the value only while the person has chosen Show. **Audit:** every secret field (servers add, pairing, agents/Paseo password, `KitForm` secret, secret dialogs) is `KitFieldKind.Secret`, so this kit fix covers all of them. | `SecretSemanticsTest` "N6 B-SEC-204 …" |
| N1 S2 chat title squeezed | **fixed, red first** (94 dp wide at a 40 % cap before tightening; 2 px in QA): the bar's inline actions may use at most 35 % of the bar, and their labels wrap or end in "…". No new bar or row. | `PartADesktopTest` "N1 B-KIT-029 the title keeps room …" (≥ 90 dp at 360 dp with "Context 34 % used" + "Git changes") |
| N2 approval chip clipped | **fixed**: the model button shrinks first and keeps to one line. | `QaWave1ComposerTest` "N2 QA wave4 …" |
| N5 text in box and failed bubble | **fixed**: sending again from the box removes the "wasn't sent" bubble (and its Try again) at that moment, so only one copy can go out. Send is also off while the same words and files are sending or unconfirmed. | `WorldComposerFilesModelsTest` "N5 QA wave4 …" |
| B6 composer three rows | **fixed**: the box has no "Message" label row. The words show inside the empty box and stay its accessible name (kit `KitField(labelShown = false)`). | `QaWave1ComposerTest` "B6 QA wave4 …" |
| B12 bubble and thinking direction | **fixed**: the prompt bubble and the quiet thinking line use `TextDirection.Content`. | `PromptDirectionTest` "B12 …" (Arabic layout: English is LTR, Arabic is RTL) |
| B13 chip says Server default after restart | **fixed**: "Server default" only once the server confirmed the session uses it; before the read, the chip says "Model". | `QaWave1ComposerTest` "B13 QA wave4 …"; `ComposerModelsFilesTest` B-STATE-103 cases updated to the confirmed state |
| B15 rest | **partly**: no Connect next to Connected; the thinking line reads "Thought · …". **Not done:** Settings › Server › Servers still takes three levels (a Settings-unit route). | `ServerFailureNoticeTest` "B15 QA wave4 …" |
| N4 notification dialog after the file picker | **fixed (no test)**: the one-time ask now happens only on the conversation list, never mid-task. This is one guard line in `PlatformWiring.kt`, which belongs to P12-PLATFORM; that unit's owner should review it. There is no androidApp harness for `PlatformHub`. | none |
| N7 New chat opens a server page | **fixed**: when the server is known (named by the route, the only saved one, or the only connected one), New chat creates the conversation and opens it at once, and Back returns to Conversations. With several servers the chooser still shows. | `ServersViewModelTest` "N7 …" (two tests) |
| N8 edit a saved server | **not done, blocked**: it needs an `update(id, name, address, username, password?)` on `ServerConnections` in the shared `:engine:connection` contract, plus its app runtime implementation. Remove-and-re-add would change the profile id and lose per-profile data, so I did not build that. Proposed to the connection owner. | none |
| N9 code Copy | **fixed**: Copy says "Copied" for 2 s (polite live region), and the button adds no height, so a one-line block stays one line tall. | `KitCodeBlockTest` "N9 QA wave4 …" |

## Codex chat-header findings

| # | Status | Test |
|---|---|---|
| 1 BLOCKER: Ask first not enforced on OC2 | **fixed**. `Oc2PermissionPolicy` ports Flutter `session_permission_policy.dart`. Before every prompt, command and shell line, `Oc2Gateway.preparePermissions` reads the session, refuses a staged revert, and reads the session's agent (or `/agent`'s default). It then installs `* * ask` plus every allow turned into an ask, keeping denials, PATCHes it and reads it back. An unconfirmed layer sends nothing (`Refused.ConsentMissing`). An installed layer is not written again. | `Oc2AskFirstTest` (5 tests); `Oc2LiveGatewayTest` and `ComposerCommandsFilesTest` now serve the permission round-trip |
| 2 BLOCKER: `parentOf` always null | **fixed**: `GraphChatHeaderPort` reads each ask's parent chain from the server before it publishes the ask to the sweep. | `WorldComposerFilesModelsTest` "B-AGENT-223 Codex header 2 …" |
| 3 key split across chunks | **fixed**: the export masks each part, or each whole block in the row fallback. | `TranscriptCopyTest` "Codex header 3 …" |
| 4 auto-approval stops when the last chat closes | **fixed**: `ConnectionAutoApprovals` runs one sweep per live connection at app scope, started with the approval store. It uses the same idempotent `answerNow`, so the chip's sweep and this one answer an ask once. | `WorldComposerFilesModelsTest` "B-AGENT-225 Codex header 4 …" (no chat open, saved Auto, ask answered) |
| 5 header writes bypass deletion fencing | **fixed (no test)**: approval and free-note writes use `lanes.forProfile`, and are refused once the profile is gone (the `StoredOpenedFolders` pattern). There is no `PreviewStores` test harness in androidApp. | none |
| 6 copy drops content | **fixed**: Copy transcript is built from the loaded messages (`TurnFeedSource` ledger), the Flutter `_transcriptMarkdown` port: tools with output, attachments, full reasoning and errors. | `TranscriptCopyTest` "Codex header 6 …" |
| 7 long code blocks corrupted | **fixed**: chunks join with their newline. | `TranscriptCopyTest` "Codex header 7 …" |
| 8 Details loses context on OC2 | **fixed**: Details falls back to `contextReading(messages, windows)` and the replies' token total. | `ChatHeaderPresenterTest` "B-CHAT-019 Codex header 8 …" |

## Codex composer findings

| # | Status | Test |
|---|---|---|
| 1 duplicate send before acknowledgement | **fixed**: Send is blocked while a bubble with the same words and files is Sending or Unconfirmed. | "B-STATE-041 Codex composer 1 …" |
| 2 a command that ran stays in the box | **fixed**: its draft is acknowledged and cleared. | "B-NAV-051 Codex composer 2 …" |
| 3 picker bypasses the selection lock | **fixed**: `PromptDelivery.withSelection` / `ConnectionDelivery.withSelection` take the send's per-conversation lock, and `selectModel` runs under it. | covered by the existing selection tests (still green). There is no dedicated race test: reproducing the pause between preflight and prompt needs a scripted link, which is left for the delivery owner. |
| 4 remote model change ignored | **fixed**: a `SessionSelection` event updates the chip's model and agent, unless a local pick is in flight. | "B-STATE-118 Codex composer 4 …" |
| 5 `/new` and `/sessions` sent to the server | **fixed**: `AppCommands` plus a contributed `NavigatorAppCommands`. `/new` and `/clear` open New chat on this server; `/sessions`, `/resume` and `/continue` go to Conversations. The box clears and nothing goes to the server. With words after it, it stays a message. **Not done:** the command list does not show them yet. | `ComposerDraftViewModelTest` "B-NAV-051 Codex composer 5 …" |
| 6 sub-agent mentions dropped | **fixed**: visible sub-agents from the catalog; `Mentions.agentMentions` rides `ConnectionDelivery.send(mentions)`. | "Codex composer 6 …" |
| 7 camera unreachable | **not done**. It needs a TakePicture launcher registered with the activity, a camera permission gate, a FileProvider `camera-capture-v1/` path, and a real `PhotoRecord` in `AndroidComposerFiles.kt`. None of that can be checked without a device in this container. The plan is in the review triage: wire `android/camera` `CaptureFiles` and `AndroidActivityResults.takePhoto`. | none |

## Catalog row ids covered

B-PROTO-128, B-SEC-204, B-KIT-029, B-AGENT-221, B-AGENT-223, B-AGENT-225, B-CHAT-019, B-CHAT-111, B-STATE-041, B-STATE-118, B-NAV-051, and B-STATE-103 (updated).

**Still missing:** B-MEDIA-008 (camera) and an edit-server row (N8).

## How to reach each fix

- **B2, composer 3/4/6, header 1:** any chat send.
- **N3:** Stop during a reply.
- **N6:** Settings › Servers › add a server, password field.
- **N1 and header 3/6/7/8:** chat header (the ⋯ menu has Copy transcript and Details).
- **N2, N5, B6, B13, N9:** the chat composer and transcript.
- **N7:** Conversations › New chat.
- **Composer 5:** type `/new` or `/sessions` in the composer.
- **Header 4:** set Auto on the approval chip, then go back to Conversations.

## Deviations and notes for the integrator

- **OC2 server-run setting:** `serverRuns` is always `false` on OC2, so auto modes ask the server and the app answers. Flutter returns the original rules to the server when a conversation is approved automatically. That needs a `serverRunsTools` hook on `GatewaySpec` (`:core:domain`, a shared module), which I left for its owner. The result is secure, but with Auto on, a tool asked while the phone is away waits.
- **Files edited outside `feature/chat`:**
  - `core/net` (B2)
  - `engine/transcript` (N3: `FooterRow.stopped`)
  - `engine/delivery` (`withSelection`, `mentions`)
  - `protocol/oc2`
  - `ui/kit` (`KitTopBar` share, `KitField.labelShown`, secret semantics)
  - `ui/kit-chat` (`CopyCorner`, text direction)
  - `feature/servers`
  - androidApp: `ChatHeaderWiring.kt` (lanes and the sweep start) and one line in `PlatformWiring.kt` (N4)

  These are the files the listed ids live in. The shared-module owners should review them.
- **Not changed:** no reformatting of untouched files, no new bar level, and no settings or module list edits.

## Final run
Two runs on the final source, in order:

1. The full command above, with `--continue`. Every task passed except `:quality:test` K1, the kit-only import rule: `ConnectionAutoApprovals` imported `coroutineScope` and `ExperimentalCoroutinesApi`. Those tasks were:
   - the desktopTest suites of `core:net`, `engine:transcript`, `engine:delivery`, `protocol:oc2`, `ui:kit`, `ui:kit-chat`, `feature:chat`, `feature:servers` and `feature:chats`
   - `:androidApp:testPreviewDebugUnitTest`
   - `:androidApp:assemblePreviewDebug`
2. I rewrote that file to use flows, then ran `:feature:chat:desktopTest :quality:test :androidApp:testPreviewDebugUnitTest :androidApp:assemblePreviewDebug`. Result: **BUILD SUCCESSFUL**.

No test was skipped or disabled. iOS and the device/emulator QA were not run in this container. A fresh APK QA pass on a device is the integrator's next step, especially for B2, B6, N1, N2 and N4.
