# Unit S2-REQ: permission and one-question asks (report)

Date: 2026-11-01. Branch `native/req-2026-11-01`, created from `claude/native-req-2026-11-01-lhtxd8` at `7db496b2`
(the M0 tip with INTEGRATE-W1's flat core contract). Write set: permission and one-question parts of
`native/engine/requests/**` (new module), request files of `native/feature/chat/**`, and this report.

Plan sections read: `plan/01` (binding), `plan/15` (S2-REQ row, C15-5), `plan/09` §5 (requests) and §6.4, `plan/08`
§5.8 (request card motion), `native/AGENTS.md`, `docs/native/slice0/*`, `docs/native/units/*` (INTEGRATE-W1's flat
`Failure` is the only contract used).

**Finish line (plan 15 + this brief):** a permission and a one-question ask are answered in the chat exactly once,
with the 3 s undo hold; Always allow sits on the card per the owner decision, behind one confirm.
**Non-goal:** forms and `FormSpec`, approval modes / auto-approval, the "third ask" invitation, notifications, the
details sheet, card motion (kit part missing, see §6).

**Shipping state:** implemented; verified locally on the desktop JVM (common tests) and compiled for Android; committed
and pushed to the branch. Not wired into the connection graph or `ChatViewModel`/`ChatScreen` (owners in §7), so a
person cannot reach it in the app yet. No PR, no CI run, no device run, no release; `master` and tags untouched.

---

## 1. What was built

### 1.1 `:engine:requests` (new, `io.github.eslamasabry.oc.engine.requests`)

| File | Content |
|---|---|
| `Requests.kt` | `HELD_ANSWER = 3 s`; `RequestAnswer` (`Permission(reply, note)`, `Question(answers)`, `Decline`); `AnswerPhase` (`Waiting`, `Held(answer, untilMs, token)`, `Sending(answer, sinceMs, result)`, `Sent`, `NotAccepted(answer, failure)`); `PendingRequest.Permission` / `.Question` (with `callId`, `oneAnswer`, `firstShownAtMs`); `SessionRequests.of(all, session)` = the oldest card of one chat + "N more wait". |
| `RequestsPort.kt` | The narrow seam the engine uses (events, capabilities, `awaitActionTransport`, pending/reply for permissions and legacy questions) and `ServerGateway.asRequestsPort()`, the one adapter. |
| `RequestsEngine.kt` | One per connection. `pending` (oldest first), `forSession`, `markShown` (age ledger by id, 64 kept), `answer` (claims Waiting → Held atomically, returns at once; a second answer → `Refused(AlreadyAnswered)`), `undo` (Held → Waiting), `answerNow` (notification path: first decision wins, a held answer goes now, an in-flight write is awaited and shared, a request already gone is Ok), `retry` (NotAccepted → resend the same answer), `flushHeld(session)` / `close()` (held answers go now), `refresh()` (refetch on every `Connected` and on `RequestsTimers.REREAD`; a failed read keeps what is known; a request added after the refetch started is kept; a settled id is never resurrected by a stale list). Write outcome: Ok → `Sent` (the card leaves when `PermissionSettled` or a refetch drops it; "Sent" is never "Done"); 404 naming the request (or `PermissionNotFoundError`/`QuestionNotFoundError` with no id) → leaves with no error; any other failure is checked against the server's list: gone → no error, still listed → `NotAccepted`. Always allow is refused (`Unavailable(persistentPermissionGrants)`) unless the server keeps grants and the request offers one. Structure changes run on `dispatchers.serial("requests")`; phase changes are `MutableStateFlow.update` CAS. |
| `RequestsTimers.kt` | `requests.reread`: 15 s foreground, 60 s hidden-watching, off when hidden-idle, ±10 % (rows B-PROTO-034, B-CHAT-075). |
| `ask/PermissionAsk.kt` | Port of `permission_ask.dart` + `permissionRequestQuestion`/`externalDirectoryAction`: `AskView(verb, name, where, count, tool, targets, always)` with `AskVerb` (Run, Edit, Create, Delete, Read, Fetch, WebSearch, Search, Task, Skill, Mode, Outside×4, DoomLoop, Use, Generic), `AskTarget(text, added, removed, file)`, `AlwaysGrant` (Named, AnyCommand, Edits, Commands); `summary(request)`. Short commands in the question, long ones by their `always` family only when the command starts with it; Paseo `cwd` names the place; word joiners around hyphens; OC2 apply_patch lists every path; OC1 `metadata.files` sizes; unified-diff size for one file; Delete/Create only when every file says so; absolute paths read from the project; outside asks keep the exact path. No copy. |

### 1.2 `:feature:chat` request files (`…feature.chat.requests`)

| File | Content |
|---|---|
| `presentation/RequestSlotUi.kt` | `RequestSlotUi(card, moreWaiting, confirmAlways)`, `RequestCardUi.Permission(ask, canAlwaysAllow)` / `.Question(title, question, options, oneAnswer, custom, other)`, `CardState` (Waiting, Held, Sending, Sent, NotAccepted), `AnswerWords`, `AlwaysConfirmUi(id, grant)`, `OtherAnswerUi`. No Compose, no copy (K2). |
| `presentation/ChatRequestActions.kt` | Every action names its request: shown, allowOnce, reject, askAlwaysAllow, confirmAlwaysAllow, cancelAlwaysAllow, choose, openOther, editOther, sendOther, openSheet, undo, retry. |
| `presentation/ChatRequestsModel.kt` | Plain class the `ChatViewModel` owns per open chat (`engine`, `session`, `capabilities`, `place`, `scope`). Actions decide on the slot as of now, never a stale frame, and only for this chat's current card. Always allow: shown only when `persistentPermissionGrants && canAlwaysAllow`; a tap opens one confirm; the confirm closes by itself when the card stops waiting or grants switch off; confirming sends `always` through the same 3 s hold; cancel sends nothing. Questions: an offered option or non-blank "Something else" text (kept as the request's draft through Undo) answers; longer questions set `sheetFor`. `leave()` flushes the chat's held answers. |
| `ui/ChatRequestSlot.kt` | Arranges kit parts only: `KitNeedsYou` (+ age from the caller's `nowMs`), `KitPermissionCard` (question, "The agent waits until you answer. Nothing is lost." + more waiting, mono subject, Allow once / Reject, quiet Always allow), the held line (`KitText` + `KitButton` Tertiary "Undo", polite live region), `KitReceipt` (Sending / Sent → Not confirmed yet after 8 s / Refused + Try again), question rows (`KitChoiceRow`, `KitField`, `KitButton`), `KitConfirm`. |
| `ui/RequestWords.kt` | `AskView` → sentences from existing `:ui:words` keys (`chatRequestAsk*`, `chatRequestTitle*`, `chatRequestAlways*`, `kitRequestChangeSize`, …), LTR/FSI isolates for names, "20 seconds"/"4 minutes" age. No new strings; Arabic exists for all of them. |

## 2. Rows covered

| Row | Tests |
|---|---|
| B-CHAT-050 (3 s hold, Undo, second answer ignored, leave sends now) | `RequestsEngineTest`: held 3 s then sent once; Undo sends nothing and a later answer gets its own window; second answer refused; leaving sends only that chat's held answers; close sends held answers. `ChatRequestsModelTest`: Allow once → "Allowed · Undo" → Undo → Reject → Sent → leaves; leave() sends at once. |
| B-CHAT-074 (exactly one reply, first decision wins) | engine: notification racing a held in-app answer → one reply, the held one; model: a tap naming another chat's or a stale card never answers. |
| B-AGENT-031 (shared in-flight write) | engine: two `answerNow` while the write is out → one reply, both get its outcome; an in-app tap then is refused. |
| B-CHAT-075 (answered elsewhere / gone after refetch) | engine: settle during the hold → card leaves, nothing sent; reconnect refetch drops unlisted asks. model: a settle while the confirm is open closes it and confirming sends nothing. |
| B-CHAT-076 (timed out → no error; other 404 keeps it) | engine: 404 naming the request → leaves quietly, notification Allow after it is Ok; a different 404 → NotAccepted, Try again sends once more. model: "Not accepted" then Try again. |
| B-PROTO-074 | engine: answering an already resolved permission is Ok with no write. |
| B-PROTO-048 (engine part) | engine: the link is woken before every reply; a late failure after remote resolution shows no error; a refetch started before a settle never brings the ask back; QuestionNotFound resolves locally. |
| B-PROTO-034 (requests part) | engine: asks are refetched on Connected; a failed refetch keeps what is known. |
| B-PROTO-028 (Allow once part) | engine: Allow once sends `Once`. |
| B-CHAT-072 (one-question part) | engine: one-answer question sends the exact label after the hold; Decline rejects (never approves); several prompts / multiple are not one-answer; questions are read only with `legacyQuestionRequests`; a permission answer never fits a question. model: only offered options send; "Something else" keeps its draft through Undo and sends trimmed text; longer questions go to the sheet. |
| B-CHAT-122 | `PermissionAskTest` (7): the seven `vision_chat_approval_test.dart` cases plus read and Paseo cwd. |
| B-CHAT-073 (engine half) | `PermissionAskTest`: every kind has its own verb; outside asks by tool (write/edit, read/grep, bash, other) with the exact path. The Arabic half is the existing `values-ar` strings (no new copy). |
| B-CHAT-123 (engine/model part) | engine: one card per chat, oldest first, more-waiting count; age from first display, not restarted. model: the chat's own oldest ask, worded from the request. The screenshot half is not done (§8). |
| B-AGENT-148 + owner decision 13B | engine: Always only with grants + `canAlwaysAllow`. model: the action is gated; one confirm naming what; cancel sends nothing; only the confirm answers always, through the hold. |
| PB-42 | `RequestsTimers.ALL` passes `assertTimerTable`. |

Tests were written first; the first red state was the compile failure of the new test files (command 1). Mutation
checks: removing the hold delay and the stale-refetch guard turned 5 engine tests red; removing the offered-option
guard and the confirm's waiting/grant guard each turned a model test red (the confirm one only after the test was
tightened to assert the confirm closes, see §5 D9).

## 3. Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, `ANDROID_HOME=/opt/android-sdk`
(`commandlinetools-linux-16111833_latest.zip`; `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`;
licenses accepted). Container: 4 vCPUs, 15 GB; `~/.gradle/gradle.properties` `-Xmx6g`, 4 workers. Container-only
(not committed): `~/.gradle/init.d/central-mirror.gradle.kts` puts `google()` and Google's Maven Central mirror ahead
of the settings' repositories (Central answers 429 through the proxy) and sets `robolectric.dependency.repo.url`.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :engine:requests:desktopTest` (tests only, before the engine) | red: compile (first attempt failed on the plugin repo order of the init script; fixed in the container) |
| 2 | `./gradlew :engine:requests:desktopTest` | BUILD SUCCESSFUL: `RequestsEngineTest` 23, `PermissionAskTest` 11, 0 failures |
| 3 | same, with the hold delay and the settled guard removed | 5 failures (B-CHAT-050 ×3, B-CHAT-075, B-PROTO-048); restored |
| 4 | `./gradlew :feature:chat:compileKotlinDesktop` (baseline + my files, before the dependency line) | only my files failed (`engine.requests` unresolved): the baseline module compiles |
| 5 | `./gradlew :feature:chat:desktopTest` | BUILD SUCCESSFUL: `ChatRequestsModelTest` 11, 0 failures |
| 6 | same, with the option guard and the confirm guard removed | 2 failures (B-CHAT-072, B-CHAT-075); restored |
| 7 | `./gradlew :feature:chat:desktopTest :engine:requests:desktopTest --rerun` (final, committed tree) | BUILD SUCCESSFUL: 45 tests, 0 failures |
| 8 | `./gradlew :engine:requests:compileAndroidMain :feature:chat:compileAndroidMain` | BUILD SUCCESSFUL (3 m 42 s) |
| 9 | `./gradlew :quality:test --tests '*KonsistRulesTest*'` | 11 rules, 2 failed: **K2 passes**; K1 flags only `ChatRequestsModelTest.kt` (K1 scans test sources, and `kotlinx.coroutines.test`/`:core:testing` are not on its allowlist; every production file of mine passes); K4 flags `ui/kit-chat/…/KitChatLocals.kt` (pre-existing, not mine) |
| 10 | `native/tool/check_commits.sh 7db496b2 HEAD` | OK |
| 11 | `native/tool/check_write_set.sh --base 7db496b2 --globs '…'` | OK, 16 changed paths inside the write set |
| 12 | `git diff 7db496b2..HEAD` grep for private IPv4 ranges and e-mail addresses | no hits (only the `noreply@anthropic.com` trailer) |

No `ktfmt`/detekt exists in the tree yet, so no formatter was run (AGENTS rule 13 names `tool/format-changed.sh`,
which does not exist).

## 4. API decisions

| # | Decision | Why |
|---|---|---|
| D1 | The engine takes a `RequestsPort`, not the whole `ServerGateway`; `asRequestsPort()` is the adapter. | Tests script suspension and failure per call (the shared fake cannot hold a write open); nothing else of the gateway is needed. |
| D2 | `answer` returns `Outcome<Unit>` (plan: Unit). | A refused second answer is a value (`Refused(AlreadyAnswered)`), and the Always gate is checked in the engine too. |
| D3 | Added `answerNow`, `retry`, `markShown`, `refresh`, `close`. | Notification race (B-CHAT-074, B-AGENT-031), Try again (STATE-10), the age ledger, Connected/reread, and Flutter's "held answers are sent, not lost" on dispose. |
| D4 | `forSession` is a `Flow`, not a `StateFlow`; `SessionRequests.of` is pure. | No per-session `stateIn` scope in the engine; the model `stateIn`s on its own scope. |
| D5 | A second answer during the hold is **ignored** (plan 09 §5.1), not "replaces" (Dart `DelayedAnswers.hold`). | The plan and B-CHAT-050 ("duplicate taps send once"). |
| D6 | A write failure other than a 404 naming the request re-reads the server's list before saying "Not accepted". | B-PROTO-048 "a late failure after remote resolution shows no error", without timing on the event stream. |
| D7 | Slice-2 questions come through the legacy `QuestionGateway` (gated on `legacyQuestionRequests`, reread because no event exists). | OC2's `question` tool arrives as a form (`Oc2FormAdapter`), which is S3-REQFORMS. |
| D8 | No Metro annotations on the engine. | No `ConnectionScope` graph binding exists yet; the connection engine owner binds it (§7). |
| D9 | The confirm closes in the presentation when the card stops waiting or grants switch off, not only when the engine refuses. | Owner rule "an answer that landed meanwhile sends nothing" holds even if the engine guard changed; the test asserts the confirm closes. |

## 5. Deviations from the brief and the plan

1. **`native/feature/chat/build.gradle.kts`** gained one line, `implementation(project(":engine:requests"))`. It is
   not a "request file", but the feature cannot compile without it. Trivial to merge with S2-CHAT.
2. **Always allow is in scope** (this brief's owner decision), though plan 15's S2-REQ row lists it as a non-goal.
   Only the card action + confirm; the invitation (B-SEC-147) and saved-grants page stay S3/S4.
3. **No card motion** (plan 08 §5.8): `KitReveal` does not exist in the kit; the slot changes in place.
4. Unknown tools read "Permission needed" instead of "Use <tool in words>": `ToolPresentation` (S2-MODEL) is not in
   the tree; the tool id is never shown (B-CHAT-111).
5. The confirm says "in this project" (the existing `chatRequestAlwaysConfirm` string), not the project's name.
6. Reject has no note (the Details sheet is S3); the engine and port carry `note` already.
7. The card's age needs the caller's `nowMs` (rule 6: no clock in UI code); `ChatScreen` passes it.

## 6. Catalog requests

- **`MODULE_OWNERS`** (coordinator): `:engine:requests	S	chat-lead	slice-2` (or the owner the coordinator names);
  without it `ModuleOwnersTest` fails, as for `:engine:entry`.
- **Kit-chat owner:** `KitReveal` for the card arrive/collapse (plan 08 §5.8), a `KitRequestCard` held-line/receipt
  variant (today arranged from `KitText` + `KitButton` + `KitReceipt`), and a `KitBidi` helper (`ltr`/`auto`
  isolates; `RequestWords.kt` has a private copy).
- **Quality owner (coordinator):** K1 (`features arrange kit parts only`) scans feature test sources, so any feature
  test using `runTest` or `:core:testing` fails it; scope it to production source sets like K3–K5, or allow
  `kotlin.test`, `kotlinx.coroutines.test.` and `io.github.eslamasabry.oc.core.testing.` in `commonTest`.
- **Words owner:** none needed now; when `ToolPresentation` lands, `e7PermissionAction7` ("Use %1$s") takes its words.
- No library or version-catalog entry.

## 7. Hand-offs (wiring not in this write set)

- **Connection engine owner:** in the connection graph, `RequestsEngine(gateway.asRequestsPort(), connectionScope,
  dispatchers, clock, pacer)` (`@SingleIn(ConnectionScope)`), and `close()` before the scope is cancelled.
- **S2-CHAT (chat lead):** `ChatViewModel` creates `ChatRequestsModel(engine, sessionId, capabilities, place,
  viewModelScope)`, calls `leave()` when the chat leaves, and puts `ChatRequestSlot(state, model, nowMs)` in the
  `KitComposerLayer` request slot; the transcript hides the step whose `callId` the card is for.
- **S3-REQFORMS:** `sheetFor` is the hook for the question sheet; forms and auto-approval build on `RequestAnswer`
  and the same hold.
- **S4 notifications:** call `answerNow` with the exact request id.

## 8. Open problems

1. Not reachable in the app until §7 is wired.
2. No Compose UI test or screenshot for the slot (B-CHAT-123's screenshot half; `KitPermissionCardTest` covers the card shell).
3. `:quality:test` as a whole was not run (ModuleOwnersTest needs §6's row); only the Konsist rules were. K1 fails
   on my model test file only because K1 scans `commonTest` too (§6, quality owner); I did not hide the imports
   behind fully qualified names to pass it.
4. iOS targets not compiled; no live OpenCode 2 check (no server in this container; AGENTS forbids the owner's).
5. The 15 s reread runs whenever the engine lives in the foreground, even with nothing waiting; it could be armed
   only while a chat is open or a request waits (plan 13 pace review).
