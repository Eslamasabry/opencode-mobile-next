# P12 COMPOSER — the native composer to Flutter parity

Branch `native/p12-composer-2026-11-03`, based on `native/m1-2026-11-03` (`8a4eee69`). Commits are
local; the branch name is in `~/wt/p12-ready.txt` for the coordinator to pull (no token on the VPS).

## What a person can do now (Android preview, routed and fed by the real OC2 gateway)

How to reach it: Servers → a saved server → Conversations → open a conversation. Everything below lives
in the chat's composer at the bottom of that screen; no new bar level was added.

| Journey | What happens | Engine / gateway |
|---|---|---|
| Model choice | The **Model** button names `agent · model · thinking level` (or "Server default"). Its sheet lists the server's enabled models, then **Thinking** (Default + the model's variants) and **Agent** (primary, non-hidden agents). A choice counts only after the session reads it back; a refused change keeps the previous one and says "Could not confirm the model choice…". | `GraphChatWorld.loadModels/selectModel/selectAgent` → OC2 `GET /provider,/model,/agent,/model/default`, `POST /session/{id}/model`, `POST /session/{id}/agent`, readback `GET /session/{id}` |
| Send uses the choice | Each send carries the verified model + level; delivery applies it before the prompt, and no other send's choice can land between selection and admission. | `PromptDelivery` (per-conversation selection lock), `GatewaySendLink.applySelection` |
| Attach | **Attach** opens **Add to prompt**: *Attach file* (system document picker), *Photo library* (system photo picker, several at once, downscaled), *Paste image from clipboard*. Limits from the shared intake: 5 files, 10 MB each, 20 MB total, clipboard 10 MB; office files become text. Chips show names with **Remove**. "Attachments save with this draft on this device." | `AndroidComposerFiles` / `AndroidComposerMedia` → `AttachmentIntake`; `DraftSession.replaceAttachments` |
| `@` files | Typing `@word` (after a space, `(`, `[`, `{`, `"`, `'` or at the start) lists project files after a 150 ms pause, name-prefix first, at most 20, no folders or paths with spaces. A tap writes `@path ` and stages the file; it is sent with its mention range, and only while its `@path` is still in the words. | `ChatWorld.findFiles/mentionFile` → OC2 `GET /fs/find?type=file`; `Mentions.resolveFileMentions` at dispatch |
| `/` commands | A field that is exactly `/word` lists the server's commands (loaded once); a tap fills `/name `. Send runs it as a command; with files attached, `/…` and `!…` go out as a normal prompt that keeps its files (Flutter `chat_send.dart:293`). | `ChatWorld.loadCommands` → OC2 `GET /command`, `POST /session/{id}/command`, `POST /session/{id}/shell` |
| Drafts with files | Text and files survive restart. Data files restore from the vault; a `file:` reference (a staged `@` file) restores only in the directory/workspace it was saved in and otherwise fails closed, keeping the saved draft. | `ConversationDrafts` + `Drafts.saveRevision(location)` |
| Send ↔ Stop, steer | While a reply runs, **Stop** sits beside Send; on a steering server the field reads "Steer while it works…" and Send reads "Add to turn" (joins the running turn, never queued). | existing `ComposerChrome`, `SteerPolicy` |

## The 8 review findings (RUNBOOK §7), each with a test that failed before its fix

Red runs were made in a separate worktree at `8a4eee69` with only the new tests copied in (a no-op
`bindLocation` shim stood in for the absent API in finding 7's test). Green runs are on this branch.

| # | Fix | Regression (red before → green after) |
|---|---|---|
| 1 | A send carrying a model/agent choice holds a per-conversation lock from selection through prompt admission. Plain sends and steers never take it. | `ComposerFindingsDeliveryTest` "B-STATE-119 finding 1 a second send's model never lands between…" |
| 2 | Transport and epoch are checked before the session model is touched; `/`/`!` text with files is a prompt that keeps its files. | "B-PROTO-028 finding 2 a failed transport never mutates the session model", "B-STATE-119 finding 2 a changed epoch…", "B-PROTO-043 finding 2 slash or shell syntax with files…" |
| 3 | Send/repeat/model change read in-flight pick, commit and model state directly. Removals latch synchronously. A pick returning for a replaced draft owner (`ChatWorld.draftGeneration`) writes nothing. | `ComposerFilesViewModelTest` "B-STATE-101 finding 3 …" ×4 |
| 4 | A load never overwrites a choice verified after it began (selection epoch). Failed session/default reads leave the choice unknown with a failure. A refused change keeps the previous choice and publishes the failure. | `WorldComposerFilesModelsTest` "B-CHAT-105 finding 4 …" ×4 |
| 5 | A file-only send settles on its server user message id (events and history), even though the transcript has no prompt row for it. | "B-STATE-043 finding 5 an accepted file-only send settles…" |
| 6 | Unconfirmed-send protection compares complete file payloads, not names; equal words with other files are not blocked. | "B-STATE-043 finding 6 a different file with the same name…", "…after a restart equal text with different files is not blocked"; "…attachment-only unknown receipt blocks the same files" passed before too and stays as a regression |
| 7 | Whole-state draft writes record the location; `file:` references wait for it and restore only in the same place. | `ConversationDraftAttachmentsTest` "B-STATE-016 finding 7 …" ×2 (+1 guard that data files never wait) |
| 8 | The default model maps to the catalog row id (mode rows such as `gpt-5-high`), and a requested variant `default` is the no-variant readback. | `ComposerFindingsModelTest` "B-NAV-034 finding 8 the default of a mode row…" (red: got `gpt-5`); `ComposerFindingsDeliveryTest` "B-PROTO-018 finding 8 …" |

Red receipts: finding 8a failed in the baseline run; findings 1, 2 and 8b failed 5/5 and findings 3–6 failed 11/11
(plus 5 GLM tests that were also failing). Finding 7 failed 2/2.

The GLM WIP was worse than "unqualified": `:engine:delivery` and `:feature:chat` **test sources did not compile**.
Causes: missing `createSelectedSession`/`setSessionAgent` in test fakes, a JVM-illegal `;` in a test name,
`failed<Unit>()`, an ambiguous `assertEquals`, a final `FakeChatWorld`, and VM tests that assumed one emission per
step. All of these were repaired. One GLM retry test injected a network failure and expected a retryable bubble. A
network failure may have left the device, so it correctly stays Unconfirmed; the test now injects a refusal.

## Tests and results (VPS, JDK 21, workers 3)

Final run: one Gradle invocation with `--rerun-tasks` on this branch's head (it took 13m 49s).

| Command | Result |
|---|---|
| `:engine:composer:desktopTest` | 155 tests, 0 failures |
| `:engine:delivery:desktopTest` | 96 tests, 0 failures |
| `:protocol:oc2:desktopTest` | 97 tests, 0 failures, 1 skip (the opt-in live-server smoke) |
| `:feature:chat:desktopTest` | 128 tests, 0 failures |
| `:androidApp:testPreviewDebugUnitTest` | 11 tests, 0 failures |
| `:quality:test` | 48 tests, 1 failure: `TranscriptStreamUiTest` P3 hit its 1-minute watchdog. It **also fails on the base** `8a4eee69` (run in a scratch tree after a one-line compile shim, because the base's `:quality` tests did not compile). K1 kit-only, which the GLM host broke, now passes. |
| `:androidApp:assemblePreviewDebug` | BUILD SUCCESSFUL (`androidApp-preview-debug.apk`, 22.6 MB) |

New tests are named after catalog rows: B-STATE-119, B-PROTO-028, B-PROTO-043, B-PROTO-018, B-NAV-034,
B-STATE-101, B-STATE-043, B-STATE-016, B-CHAT-087, B-NAV-049, B-NAV-051, B-CHAT-009, B-AGENT-172.
Not run: live OC2 server, emulator and device acceptance. These checks are unit, ViewModel and desktop-screen only.

## Catalog rows still missing or partial

- **Camera (B-MEDIA-008, Take photo):** hidden. `TakePicture` needs a `FileProvider` in `AndroidManifest.xml`, which is
  outside this write set. `AttachKind.Camera` and the row exist; the Android source must add `Camera` to `kinds` once
  the provider exists (see integrator steps).
- **Model picker depth (B-NAV-033/036):** "All models" search, favourites, recently used, cheapest and cycling are not
  built. Plain model chips only.
- **App commands (B-NAV-049/051):** only the server's commands are listed. Flutter's ~43 built-in app commands
  (new, sessions, compact, undo…) and the "Commands and agents" launcher are not ported.
- **Subagent `@` mentions:** not offered in the strip. The engine's `Mentions.agentMentions` exists but is not wired.
- **Previews:** chips show names only. There are no image thumbnails and no file preview sheet.
- **IME image commit / keyboard paste:** not wired (`AttachmentIntake.fromInserted` exists).
- **Pending photo recovery (B-STATE-087/088):** not used. Only the gallery path exists, and it needs no record.
- **Restored mention files:** after a restart, a staged `@` file comes back as a plain project file. `AttachmentRef`
  stores no `mentionPath`, so it is sent without a range.
- **Merging waiting steers into one (Flutter `chat_queue.dart:338`):** not done.
- **Several GLM test names cite unrelated rows** (`B-STATE-103`, `B-CHAT-105/106/107` for composer facts). They were
  left as written; the new tests cite rows that match.

## Deviations

- **Copy:** EN/AR copy is in a new resource file `ui/words/.../strings_composer_full.xml` (values and values-ar). This
  follows the precedent of `strings_composer_preview.xml`; `ui/words` is a shared module, so the owner should adopt or
  move it.
- **OC2 capabilities:** `slashCommands`, `agentSelection` and `conversationShell` are now on, matching Flutter's
  `api2ServerCapabilities`. `fileBrowsing` stays off because list/read are not implemented; the `@` strip calls
  `findFile` directly and hides itself when that fails.
- **Small edits outside the listed folders:**
  - `Oc2Gateway.kt` (commands, shell, find): not a model-selection file, but needed for the slash and `@` journeys.
  - `ChatFakes.kt`: `FakeChatWorld` is now `open`.
  - `BasicComposerViewModel`: `filePickers` defaults to empty so `:quality`'s `ChatWorldReviewTest` compiles
    unchanged.
- **Selection lock:** only sends that carry a choice take it. A plain send and a choice-carrying send can still
  interleave, but the plain one just uses whatever the session holds.

## Integrator steps

1. Pull `native/p12-composer-2026-11-03` (7 local commits on top of `8a4eee69`).
2. **Camera:** add a `FileProvider` (authority `${applicationId}.composer`, cache path) to
   `androidApp/src/main/AndroidManifest.xml`. Then implement `takePhoto` in `PickerActivityResults` with
   `ActivityResultContracts.TakePicture` and add `AttachKind.Camera` to `AndroidComposerMedia.kinds`.
3. Adopt `strings_composer_full.xml` into the words owner's layout, plus the Flutter string ids in the
   localization ledger if one is kept.
4. **`TranscriptStreamUiTest` P3:** it times out on the VPS on both this branch and the base. This branch changes
   nothing in the transcript or chat screen. The runbook already records this renderer watchdog failing on the
   VPS and passing on the PC.
5. Before delivery, check against a live OC2 2.2.5/beta server on a task-owned session:
   - `/command`, `/fs/find` and `/session/{id}/command` shapes
   - a model + thinking + agent change, then one send
   - an `@` file
   - a photo
