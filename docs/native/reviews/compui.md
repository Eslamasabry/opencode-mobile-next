Verdict: **REQUEST CHANGES — 8 MAJOR, 1 MINOR.**

1. **MAJOR — Field clears before delivery owns the prompt.**  
   `native/feature/chat-composer/.../presentation/ComposerPresenter.kt:94–97`  
   **Problem:** Clear and `sends++` happen before `port.send` is called. While delivery waits for transport or durable storage, the UI can autosave the empty field. Cancellation or process death can then lose an unsent prompt. Conversely, an immediate successful send calls `flushDraft()` before the UI applies Clear, persisting the old prompt.  
   **Evidence:** Plan 09 §4.2 requires the durable marker and bubble before clearing; Clear is applied asynchronously by `ComposerScreen.kt:103–106`.  
   **Fix:** Clear only after successful delivery handoff. Persist consumption of the submitted draft revision without clearing newer typing; emit `onSent` at that point.

2. **MAJOR — Uncertain delivery is presented as definitely unsent and retryable.**  
   `native/feature/chat-composer/.../presentation/ComposerPresenter.kt:96–105`  
   **Problem:** Every `Outcome.Failed` restores text and offers Try again, including `Failure.Refused(DispatchUncertain)`. Retrying can duplicate a prompt already accepted by the server.  
   **Evidence:** The core Failure contract explicitly contains `DispatchUncertain`; plan 09 §4.2 requires “Check again,” which must never resend. The presenter discards the failure category.  
   **Fix:** Preserve delivery identity and uncertainty in UI state. Offer receipt reconciliation for uncertain dispatch; restore and retry only confirmed-unsent work.

3. **MAJOR — Retrying a failed steer can start a new turn.**  
   `native/feature/chat-composer/.../presentation/ComposerPresenter.kt:82–90`  
   **Problem:** `failedText` retains only words. Retry recalculates `SendHow` from current facts: a failed steer followed by `busy=false` becomes `Start`.  
   **Evidence:** Plan 09 §4.3 expressly forbids retrying a stale or idle Codex steer as a start. Existing failure tests exercise only ordinary sends.  
   **Fix:** Retain the original delivery intent and authoritative turn identity. A stale steer must remain refused; starting another turn requires a separate action.

4. **MAJOR — Failure recovery loses either the failed prompt or newer typing.**  
   `native/feature/chat-composer/.../presentation/ComposerPresenter.kt:92–104`  
   **Problem:** Send A, type B while A waits, then fail A. A exists only in volatile `failedText`. Sending B clears that reference; retrying A instead issues an unconditional Clear that erases B.  
   **Evidence:** Plan 09 §4.2 requires confirmed-unsent text to be prepended to newer typing through durable composer outcomes. The synchronous fake cannot exercise this sequence.  
   **Fix:** Merge returned work into the durable draft, preserving both texts. Retrying a separate failed send must leave the current draft untouched.

5. **MAJOR — Repeated Send actions can dispatch the same text twice.**  
   `native/feature/chat-composer/.../presentation/ComposerPresenter.kt:86–95`  
   **Problem:** Admission relies solely on externally published `facts.sending`; the presenter neither reserves the submission nor synchronously consumes its text. Two calls before facts or the field update launch two sends.  
   **Evidence:** Calling `onSend()` twice against the included fake records two identical sends. The 800 ms guard protects Stop only.  
   **Fix:** Reserve the submission synchronously before launching delivery and release that reservation after handoff or confirmed refusal. Add a delayed-port test issuing two immediate Send actions.

6. **MAJOR — Persisted drafts cannot populate this composer.**  
   `native/feature/chat-composer/.../ui/FullComposerHost.kt:38`  
   **Problem:** A fresh host creates an empty field, while neither `ComposerPort` nor `ComposerFacts` exposes restored draft content. Reopening a conversation cannot reload its saved words; typing then overwrites that saved draft through autosave.  
   **Evidence:** B-STATE-100 requires persistence **and reload**. Its presenter test checks only `draftChanged` calls. Plan 01 §9.2 requires restored drafts to seed the field.  
   **Fix:** Add conversation-scoped draft restoration, including unconfirmed-send filtering, and apply it before accepting field edits or autosaving.

7. **MAJOR — Insert requests are acknowledged before insertion occurs.**  
   `native/feature/chat-composer/.../ui/FullComposerHost.kt:40–43`  
   **Problem:** `insertDone(id)` runs after scheduling an edit, before the field applies it. The presenter also records the ID immediately and has only one edit slot. A second insert can overwrite the first; both requests are then acknowledged and deduplicated despite missing text.  
   **Evidence:** `ComposerPresenter.kt:125–127` replaces the pending edit. The insert test acknowledges the first edit before issuing another, avoiding this failure.  
   **Fix:** Acknowledge the originating request from `onEditApplied`, retain pending edits until application, and test two distinct inserts before a UI frame.

8. **MAJOR — The introduced module fails repository admission gates.**  
   `native/feature/chat-composer/build.gradle.kts:1` and `.../ComposerPresenterTest.kt:3–19`  
   **Problem:** The module has no `MODULE_OWNERS` row. Its tests also import packages forbidden by K1, which scans test files as well as production.  
   **Evidence:** `ModuleOwnersTest` compares discovered modules with registry rows; `KonsistRulesTest` applies `KitOnly` to every feature file. The unit report acknowledges these unresolved failures.  
   **Fix:** Include the ownership registration and an agreed test-location or rule-scope change before integration.

9. **MINOR — History restoration discards selection and composing ranges.**  
   `native/feature/chat-composer/.../presentation/ComposerPresenter.kt:120`  
   **Problem:** The returned `FieldValue` is reduced to text plus `selectionEnd`; applying Replace collapses the selection and omits composition.  
   **Evidence:** B-CHAT-089 requires preservation. The engine returns the original complete `ComposerValue`, but both new history tests return only collapsed values.  
   **Fix:** Carry and restore the complete field value; test a noncollapsed selection and composing range.

Read-only static review of `48af726f..997da66b`; no builds or mutations executed. Assertion sensitivity was checked for three tests: steering-precedence assertions detect routing to `StopThenSend`; the 799/800 ms Stop assertions detect guard removal or an incorrect boundary; model-label assertions detect returning raw IDs. Those checks do not cover the asynchronous failures above.