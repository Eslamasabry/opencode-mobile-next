# Native preview text drafts — frozen integration contract

Finish line: type → acknowledged autosave → process restart → restore; confirmed send clears only the submitted revision; profile deletion fences writers and removes only that profile's drafts. A refused/unreadable store preserves existing data and exposes a visible failure. No automatic prompt replay. Non-goals: attachments, Flutter adoption/migration, other composer features.

Engine owner adds in `io.github.eslamasabry.oc.engine.composer.drafts`:

- `ConversationDrafts(drafts: Drafts, scope: AppCoroutineScope, dispatchers: AppDispatchers)`; reuse the real Drafts store.
- `fun open(ref: ConversationRef): DraftSession`.
- `suspend fun closeProfile(profileId: ProfileId): Outcome<Unit>` fences session writes and calls the real store's profile cleanup. Refusal leaves metadata available for recovery.
- `DraftSession.state: StateFlow<DraftState>`.
- `DraftState(text: String = "", saved: String = "", ready: Boolean = false, failure: Failure? = null, restoreRevision: Long = 0, editRevision: Long = 0)`; redact text in toString. restoreRevision changes for external restoration/clear, not each user keystroke.
- `DraftSession.changed(text: String)` accepts edits only once ready; debounce 600 ms with injected app scope/dispatchers.
- `suspend DraftSession.flush(): Outcome<Unit>` captures revision, acknowledges only durable success.
- `DraftSubmission(text: String, revision: Long)` redacted in toString; `DraftSession.captureSubmission(): DraftSubmission?` returns null before ready. Capture at send start.
- `suspend DraftSession.clearAfterSend(submission: DraftSubmission): Outcome<Unit>` conditional clear: a newer revision survives even if its text is identical. Clear only canonical/Accepted acknowledgement, not a local dispatch Outcome.Ok; failed/uncertain delivery never authorizes erasure. Capture the original draft even when the dispatch text is trimmed.
- `DraftSession.close()` requests bounded flush and closes display session without canceling already acknowledged storage work. Profile closure takes priority and prohibits late resurrection.

Feature owner owns all feature/chat changes. Inject optional `Set<ConversationDrafts>` into GraphChatWorlds via AppScope allow-empty multibinding (backward-compatible tests/desktop). Expose the selected handle through DraftFacts appended default fields ready=true, failure=null, restoreRevision=0 for existing fakes. Real connected store begins unready. Wire save/back guard, revision-safe editor restoration, acknowledged-send clearing and app-scope autosave. Preserve exact user whitespace and IME selection for ordinary edits; no initial empty-field write over restored content. Surface failed saves using existing kit error parts/localized copy. No new bar levels.

Runtime owner owns androidApp and binds one ConversationDrafts using real PreviewStores preferences, WriterLanes, ProfileStore and file vault; no MemoryComposerPrefs or volatile payload fallback. Warm gate precedes store reads. Profile removal calls closeProfile before its final metadata/secret sweep and refuses failures. Add focused adapter/deletion tests where a meaningful harness exists.

Ownership: engine/composer = engine worker; feature/chat and unique new words files = feature worker; androidApp = runtime worker; parent = contracts, quality integration checks, build orchestration, emulator proof, evidence/commits. No worker launches tests or Gradle; coordinator serializes one build per machine. Preserve others' edits.

## Feature repeat-confirmation extension

`DraftFacts` appends default fields `sendBlocked=false`, `editRevision=0`, `repeatAllowed=false`. These are metadata only; text remains redacted in `toString`. The input transaction reports accepted user edits synchronously through the optional `KitField.onTextChanged` callback after validation/secret registration. Programmatic restoration never invokes it.

`ChatWorld.sendRepeated(text, expectedRevision)` has a default Unavailable implementation for existing fakes. The live implementation permits exactly one explicit attempt only for canonical-history ambiguity, after rechecking the current submission revision and exact text. Pending/unknown receipts, unreadable storage and failed acknowledged clears cannot be bypassed. The composer confirmation captures target plus edit revision, closes on either changing, and performs no automatic send or retry.

## Refused profile deletion and explicit recovery

Profile deletion has ordered stages, not an all-or-nothing transaction across draft files, preferences and secrets. The draft stage fences and removes only the target profile's drafts; a later metadata or secret failure can leave that saved profile present after its drafts were removed. Unreadable or refused draft storage preserves the data it could not safely remove and returns failure.

After a deletion reaches the draft fence but keeps the saved profile, only an explicit Connect action may call `ConversationDrafts.reopenProfile(ProfileId): Outcome<Unit>`. Runtime validates saved profile existence and trust first and serializes recovery against removal. Bootstrap and internal conversation creation never reopen a deletion fence. Reopening permits new draft sessions; previously fenced handles remain retired and cannot resurrect pre-deletion writes.
