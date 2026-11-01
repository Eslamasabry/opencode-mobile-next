Verdict: **REQUEST CHANGES** for `af824d5d..ba8f649e`.

1. **BLOCKER — Credentials enter saved instance state.** [KitForm.kt:159](/home/eslam/Storage/Code/wt/native-reviews/src-reqforms/native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/form/KitForm.kt:159)  
   `WordsField` uses `rememberTextFieldState(initialValue)` even when `field.sensitive` selects `KitFieldKind.Secret`. That saves the credential into instance state; it also prefills sensitive fields from defaults. Plan 16 SI-4 and N16-D6 explicitly prohibit both. `FormRulesTest` only checks the sensitivity classifier, so it cannot catch this leak.  
   **Fix:** Use `remember { TextFieldState() }` for sensitive inputs, start empty, and verify saved-state output contains no credential canary.

2. **MAJOR — Real OC2 decoding discards `hidden` before this adapter reads it.** [Oc2FormAdapter.kt:74](/home/eslam/Storage/Code/wt/native-reviews/src-reqforms/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/forms/Oc2FormAdapter.kt:74)  
   The adapter expects `hidden` in `FormField.raw`. However, `Oc2FormField` has no `hidden` property, and `Oc2CoreProjection.kt:317` reconstructs `raw` by serializing that DTO. Consequently, real hidden fields become visible; an empty draft can also omit their preset answers. This breaks plan 09 §5.5 and B-PROTO-022. The passing test bypasses decoding by constructing `raw` directly.  
   **Fix:** Preserve `hidden` through the protocol/core projection and test server JSON → protocol decoder → core projection → form adapter → payload.

3. **MAJOR — Held answers are not bound to the schema or owner that was answered.** [FormRequests.kt:160](/home/eslam/Storage/Code/wt/native-reviews/src-reqforms/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/forms/FormRequests.kt:160)  
   `add()` replaces a form with the same ID without invalidating its held answer. `send()` then takes the **current** form’s session and sends the previously assembled payload. A schema update during the hold therefore sends stale answers; an owner change routes them using the replacement owner. `answer()` also accepts no captured revision from the sheet. The identity helper is unused by this lane, contrary to B-CHAT-028’s captured-schema requirement.  
   **Fix:** Bind drafts, admission, holds and receipts to the full captured identity. Reject stale sheets and retire old holds when revision or owner changes.

4. **MAJOR — “First decision wins” is not atomic.** [HeldAnswers.kt:66](/home/eslam/Storage/Code/wt/native-reviews/src-reqforms/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/forms/HeldAnswers.kt:66)  
   `answer()` checks the phase and installs `Held` separately. Two callers can both observe no answer, both succeed, and overwrite the first decision. `dispatch()` likewise separates reading `Held` from installing `Sending`, allowing duplicate dispatch under concurrency. The write mutex serializes sends but does not deduplicate them. Public synchronous calls are not moved onto the supplied scope’s lane. The B-CHAT-074 test makes sequential calls entirely inside the hold.  
   **Fix:** Atomically claim each phase transition with a decision token; serialize timer-map access and test competing callers plus timer/flush races.

5. **MAJOR — Cancelling a flush permanently strands an answer in `Sending`.** [HeldAnswers.kt:116](/home/eslam/Storage/Code/wt/native-reviews/src-reqforms/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/forms/HeldAnswers.kt:116)  
   `flush()` cancels the hold timer and runs `dispatch()` in its caller’s job. Cancellation while acquiring the mutex or awaiting the gateway skips all cleanup. The phase remains `Sending`, further answers are ignored, and refresh preserves it as in flight. The separately launched slow timer also survives caller cancellation.  
   **Fix:** Run admitted writes in the owning generation’s job, clean up timers in `finally`, and reconcile cancelled or uncertain sends without automatically replaying them. Cover cancellation before dispatch and during the gateway wait.

6. **MAJOR — A stale hydration response resurrects settled or deleted requests.** [FormRequests.kt:123](/home/eslam/Storage/Code/wt/native-reviews/src-reqforms/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/forms/FormRequests.kt:123)  
   Start `refresh()`, process `FormSettled` or `SessionDeleted` while the read is suspended, then return the earlier snapshot: `listed.forEach(::add)` restores the removed form. Conversely, a newly created form absent from the snapshot is removed. `QuestionRequests.refresh()` has the same race. This contradicts B-PROTO-048’s stale-resurrection protection. The “forced reconnect” test performs immediate, sequential reads and cannot expose it.  
   **Fix:** Fence snapshots with event/read versions and preserve newer updates and tombstones. Test a deferred read crossing settlement, deletion and creation events.

7. **MAJOR — Choice validation permits invalid answers onto the wire.** [FormRules.kt:202](/home/eslam/Storage/Code/wt/native-reviews/src-reqforms/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/forms/FormRules.kt:202)  
   Multi-select validation only checks count. A closed field offering `"a"` accepts `Choices(["forged"])`; duplicates can also satisfy `minItems`. Separately, `QuestionSheet.block()` accepts multiple offered choices for a `multiple=false` prompt. These answers pass engine validation despite plan 09 §5.4 and B-CHAT-072’s “invalid values never send” requirement.  
   **Fix:** Validate offered membership, uniqueness, custom-value rules and single-choice cardinality before constructing payloads. Exercise these cases through the public answer methods.

8. **MAJOR — Question replies bypass the required transport preparation and shared reply path.** [QuestionRequests.kt:136](/home/eslam/Storage/Code/wt/native-reviews/src-reqforms/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/questions/QuestionRequests.kt:136)  
   This class calls `QuestionGateway` directly through its own `HeldAnswers` mutex. It never calls `awaitActionTransport()` and shares neither admission nor the in-flight decision with the existing `RequestsEngine` path. B-PROTO-048 explicitly requires replies to wait for a woken transport and share one write slot. Its test fake has no transport-readiness behavior.  
   **Fix:** Route sheet answers through the existing generation-bound request admission/write path, with transport preparation before dispatch and shared ownership across surfaces.

9. **MAJOR — Unrelated 404s silently discard unanswered requests.** [QuestionRequests.kt:147](/home/eslam/Storage/Code/wt/native-reviews/src-reqforms/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/questions/QuestionRequests.kt:147)  
   Every `Failure.NotFound` settles the question, even one naming another request or lacking the expected tag. `FormRequests.kt:193` does the same for forms; its conflict handling also ignores the error tag. An endpoint/proxy failure can therefore remove a live request and discard the person’s answer. The existing core failure contract supplies tags and request IDs for this distinction.  
   **Fix:** Recognize only the matching request-specific terminal errors; reconcile ambiguous failures and retain the request when resolution is unknown. Add wrong-ID and unrelated-error cases.

10. **MAJOR — Multiline draft persistence is claimed but absent.** [FormDrafts.kt:16](/home/eslam/Storage/Code/wt/native-reviews/src-reqforms/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/forms/FormDrafts.kt:16)  
    Drafts exist only in a `HashMap`; there is no storage read/write or `ProfileScope` registration. Restart loses the answer. The B-STATE-024 test checks reopening the same object and the spelling of a generated key, proving neither persistence nor profile deletion. The unit report nevertheless lists the row as covered.  
    **Fix:** Implement and register the persistent draft store, test recreation and deletion, and keep sensitive fields out of ordinary draft storage.

11. **MAJOR — Text inputs stop reflecting the caller-owned draft.** [KitForm.kt:159](/home/eslam/Storage/Code/wt/native-reviews/src-reqforms/native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/form/KitForm.kt:159)  
    `rememberTextFieldState(initialValue)` only consumes the initial value. Later changes to `values` do not update the displayed text. `OwnWords()` has the same issue. Restoring or resetting a draft—or replacing a form while retaining field keys—can leave one answer displayed while the caller submits another. Existing UI tests only propagate edits outward.  
    **Fix:** Synchronize external draft changes into text state without feedback loops, and key state by captured form identity. Test replacing the caller’s draft after composition.

Static mutation checks inspected at least three assertion paths:

- A constant revision would fail `FormSchemaTest.kt:35`.
- Immediate dispatch would fail the hold-window assertion at `FormRequestsTest.kt:56`.
- Including inactive fields would fail `FormRulesTest.kt:27`.

No tests or builds were launched, per the binding reviewer rule. Core signatures and merged dependencies were inspected statically; compilation was not verified.