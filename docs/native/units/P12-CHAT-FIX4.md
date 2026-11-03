VERIFIED: green — :feature:chat:desktopTest 261/261, :engine:transcript:desktopTest 160/160, :engine:delivery:desktopTest 99/99, :engine:composer:desktopTest 155/155, :engine:migration:desktopTest 12/12, :feature:projects:desktopTest 21/21, :core:common:desktopTest 24/24, :ui:kit:desktopTest 148/148, :ui:kit-chat:desktopTest 124/124, :quality:test 59/59, :android:notifications:testDebugUnitTest 41/41, :androidApp:testPreviewDebugUnitTest 15/15, :androidApp:assemblePreviewDebug OK

# P12-CHAT-FIX4 — chat batch 3: Codex chat-bugs review, B-AGENT-024, gap list, mislabelled tests

Branch `claude/chat-native-parity-o47cy2`, base `native/p12-integrate-2026-11-03` (`5c01fd3f`, chat-fix2 and
chat-fix3 merged). Every commit carries `[skip ci]`.

Finish line: every Codex chat-bugs finding is checked against the current code and each real one is fixed in the
Android app with a test; a failed reply sorts into Flutter's six `AgentRunFailureKind`s through one classifier, each
with its own words and next step; the mislabelled tests cite the row or plan section they really check.
Non-goal: the projects path rule (B-STATE-157 gap, P12-PROJECTS) and new composer features.

## 1. Codex review of chat-bugs (`docs/native/reviews/p12/chat-bugs.md`, on `native/p12-qa-wave4-2026-11-03`)

All five are real in the current code (chat-fix2/3 fixed none of them).

| # | Finding | Verdict | Fix | Test |
|---|---|---|---|---|
| 1 MAJOR | Failed-reply Details show credentials (B-SEC-014, B-SEC-133) | **fixed** | `ChatTranscript` failed footer: the server's text goes through the app's one redactor (`rememberKitMask`) before it is a Details note, so the fold shows and copies it masked | `ChatFeelScreenTest` "B-SEC-014 B-SEC-133 a failed reply's Details never show a key or an Authorization header" |
| 2 MAJOR | A Stop ignored inside 800 ms after a send holds "Stopping…" and blocks every later Stop (QA B10) | **fixed** | `PromptDelivery.stopTurn` answers `StopAnswer.Ignored` / `Asked` (`stop()` keeps its shape); `ChatWorld.stopTurn()` (default from `stop()`, `GraphChatWorld` overrides) returns whether the server was asked; `ChatPresenter.stop` holds `stopping` only for an asked Stop, so an ignored tap leaves Stop usable (Flutter's guard) | `StopTest` "B10 Codex chat-bugs 2 an ignored Stop says it asked nothing, a heard one says it asked"; `ChatPresenterTest` "B10 Codex chat-bugs 2 a Stop the guard after a send ignored leaves Stop usable" |
| 3 MAJOR | A recovered turn still reads "Didn't finish" (B-CHAT-101) | **fixed** | `TranscriptModel.buildTurn`: the outcome (failed / stopped / reason) is the newest assistant step's; each step resets the previous one (Flutter) | `TranscriptReducerTest` "B-CHAT-101 a step that failed and a later step that finished leave a finished turn…", "B-CHAT-101 a newest step that failed after a finished one is still a failed turn" |
| 4 MINOR | Page-capped or repeated-cursor conversation lists claim completeness (B-NAV-031) | **fixed** | `readSessionPages` (`ChatBugsWiring.kt`): a cursor left over after the loop marks the list incomplete, so the feed's partial-source notice shows | `SessionPagesTest` "B1 B-NAV-031 the page cap and a repeated cursor stop the read without a failure but say the list is incomplete" (the old test asserted `complete=true`, the defect; that assertion now says incomplete) |
| 5 MINOR | The picker does not lead with the current model (B-NAV-034) | **fixed** | `ModelPicker.sections`: inside its provider group the current model comes first, then the rest by name | `ModelPickerTest` "B-NAV-034 the current model leads its provider's group, before its alphabetical siblings" |

Deviation (3): Flutter also keeps an earlier step's error as a note under the finished turn; native does not show
that note yet (no new copy was added for it).

## 2. B-AGENT-024 — reply failure kinds match Flutter, one classifier

- New `:engine:transcript` `turnfacts/AgentRunFailure.kt`: `AgentRunFailureKind` (SignIn, NoCredit,
  ModelUnavailable, Busy, ConnectionLost, Unknown) and `classifyAgentRunFailure`, a port of
  `lib/domain/agent_run_failure.dart`. Its fallback is the existing B-CHAT-016 server-cause rules (`RetryStatus.kt`
  `causeOf`, now module-internal instead of file-private), so the server text has **one** set of rules: the swarm's
  third classifier was rejected earlier, and `ReplyFailureKind` no longer has its own keyword list.
- `:feature:chat` `ReplyFailureKind` gains `NoCredit` and `ConnectionLost` and maps from the classifier. "No credit"
  is no longer folded into Busy; a dropped/timed-out provider connection is no longer "Other".
- Words (`ui/words` new `strings_chat_fix4.xml`, English + Arabic): no credit — "…has no credit for this model. Add
  credit with that provider, or pick another model."; connection lost — "The connection to the model's provider was
  lost, so the reply stopped. Ask it to continue, or send again." Busy no longer says "out of quota".
- Next step for ConnectionLost is a visible **Continue** button under the failed reply (Flutter `error-action-continue`):
  `ChatActions.continueReply(words)` sends the localized "Continue" alone through `ChatWorld.sendAgain` (the draft
  and its files stay, as Flutter's `_sendInsteadOfDraft`).
- Tests: `AgentRunFailureTest` (the whole Flutter `agent_run_failure_test.dart` "sorting a refusal" table, plus no
  credit ≠ busy), `QaWave1ChatTest` "B-AGENT-024 no credit is not busy and a lost connection is its own kind…",
  `ChatFeelScreenTest` "B-AGENT-024 a reply that lost its provider connection says so and offers Continue, which
  sends only Continue", "B-AGENT-024 no credit reads as no credit, not busy, and offers no Continue". The B5 test's
  "socket hang up → Other" assertion now expects ConnectionLost (that was the gap).
- Not ported: Flutter's per-agent names ("Claude Code lost its connection to Anthropic") — native has no agent-run
  record on the failed reply (server-agnostic words instead); Sign in / Add credit actions (no provider sign-in flow
  in the native chat yet; the line says what to do).

## 3. Chat items from the parity gap list

- **B-AGENT-024** — done (§2).
- **B-CHAT-057** (three `ContextLevel`s) — already resolved on this base: only
  `engine/transcript/turnfacts/ContextUsage.kt` `ContextLevel` exists; `TurnFactsPresenter` and `ChatContextSheet`
  use it; the header copy is gone. Nothing to do.
- B-CHAT-029 — retracted by the parity lead. B-STATE-157 / B-STATE-158 — `:feature:projects` (P12-PROJECTS); only the
  mislabelled test name is fixed here (§4).

## 4. Mislabelled tests renamed (names only; no assertion changed)

No catalog row covers these behaviours, so where none fits the test now names the binding plan section (the repo's
existing `09-6_6` convention).

| Class | Old id | New id | Why |
|---|---|---|---|
| ProjectRulesTest | B-STATE-158 | B-STATE-157 | typed-folder path rule |
| DlvChipTest (DLV-9), DlvStopTest (DLV-6), PromptDeliverySelectionTest (DLV-11) | B-AGENT-243 | 09-4_4 | delivery Stop obligations (plan 09 §4.4) |
| ComposerFindingsDeliveryTest finding 2 | B-PROTO-028 | 09-10_3 | model chip/selection survives a failed transport |
| WorldComposerFilesModelsTest replaceAttachments | B-STATE-104 | 09-10_6 | attachments |
| WorldComposerFilesModelsTest file-only unconfirmed send | B-STATE-103 | 09-4_6 | optimistic bubble reconcile |
| ConversationDraftAttachmentsTest (9 tests) | B-STATE-103 | B-STATE-016 (restart exact bytes), B-STATE-100 (add/remove persist, profile files, files-only drafts), B-STATE-101 (safe saves: missing payload, older token, refused commit, newer edits, vault refusal) | draft attachment persistence rows |
| ComposerFilesViewModelTest (11), ComposerModelsFilesTest (10) | B-STATE-103 | 09-10_6 (attachments), 09-10_3 (model button/sheet), 09-10_2 (send gate) | composer controls |
| InlineAnnotatedTest | B-SEC-064 | 09-9_1 | Markdown renderer hands links on |
| MigrationEngineTest deferred-features notice | B-MIGR-009 | 05-7_3 | MigrationGate notice |
| NumbersTest, NumberDigitsTest ×2, PhoneNumbersTest | B-L10N-012 | 06-12_1-8 | digits rule |
| PartBRulesTest number field | B-L10N-012 | 06-12_1-9 | number input rule |

`ChatWorldReviewTest` "B-AGENT-243 stopping a waiting draft clears its Sending chat bubble" is not in
`mislabelled.json` and was left as is. `docs/native/parity/mislabelled.json` is on the parity lead's branch, not
this base; the integrator can drop the entries above from it.

## How to reach each fix on the phone

- Failed reply: Conversations → a chat whose last reply failed → the reply's footer shows the plain reason; Details
  opens the masked server text; a lost connection shows Continue.
- Stop: send, tap Stop at once (ignored), tap again → the turn stops.
- Recovered turn: a turn whose first step failed and a later step finished shows a normal footer.
- Conversations list: a server with more than 1,000 conversations (or a server that repeats a page cursor) now shows
  the feed's partial-list notice instead of claiming the list is whole.
- Model picker: composer model chip → the current model is the first row of its provider.

## Tests and results

This container (JDK 21, Android SDK platforms;android-37.1, build-tools 37.0.0), on the branch head:

- `./gradlew :feature:chat:desktopTest :engine:composer:desktopTest :engine:migration:desktopTest :feature:projects:desktopTest :core:common:desktopTest :ui:kit:desktopTest :ui:kit-chat:desktopTest :engine:transcript:desktopTest :engine:delivery:desktopTest --continue` — green, counts in the first line, 0 failed, 0 skipped. Every new and renamed test is in the result files.
- `./gradlew :quality:test :android:notifications:testDebugUnitTest :androidApp:testPreviewDebugUnitTest :androidApp:assemblePreviewDebug --continue` — quality 59/59 and the APK built (`androidApp-preview-debug.apk`). The two unit-test tasks failed for environment reasons and passed on rerun:
  - `:android:notifications` Robolectric classes could not download `android-all-instrumented` (Maven 429). After placing the 15 and 16 jars from Google's Central mirror in `~/.m2`, 41/41.
  - `:androidApp` `FilesWiringTest` "B-MEDIA-034 … the Android socket …" failed once (a GET still answered right after `server.close()`), then passed 15/15 on rerun. It is a timing flake in the Wi-Fi share socket test (P12 files owner), untouched by this unit.
- Red-first: the new tests were written against each reported defect; they were not run separately against the old code before the fix (no stash run), so "fails before" is by construction, not by a recorded red run.

## For the integrator

- `:engine:transcript` (shared): `AgentRunFailure.kt` added; `RetryStatus.kt` `Cause`/`causeOf` widened to internal;
  `TranscriptModel.buildTurn` outcome rule. `:engine:delivery`: `StopAnswer` + `stopTurn` added (additive).
  `ui/words`: new `strings_chat_fix4.xml` (en, ar); `chatReplyFailedBusy` wording changed in `strings_chat_bugs.xml`.
- `ChatWorld.stopTurn()` and `ChatActions.continueReply()` have defaults, so other implementers compile unchanged.
- Container note: Maven Central answered 429; a local `~/.gradle/init.d/mirror.gradle.kts` (outside the repo) asks
  Google's Central mirror first. No build file changed.
