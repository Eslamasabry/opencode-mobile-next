Verdict: **REQUEST CHANGES — 5 MAJOR, 1 MINOR.** Cancellation and concurrent settlement can break the claimed exactly-once behavior.

1. **MAJOR — Cancelling the first caller strands the shared write.**  
   [RequestsEngine.kt:208](/home/eslam/Storage/Code/wt/native-reviews/src-req/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/RequestsEngine.kt:208)

   **Problem/evidence:** The engine publishes `Sending`, then performs transport wake, reply, and failure reconciliation in the caller’s coroutine. Cancellation at any suspension skips `sending.result.complete(outcome)`. The deferred has no parent, so another `answerNow` caller waits indefinitely. Refetch explicitly retains `Sending`; Undo and Retry cannot recover it. This violates plan 09 §5.1 and B-AGENT-031.

   **Fix:** Own the write in the connection scope and let callers await its shared result independently. Ensure every termination completes the shared result and establishes a recoverable state without automatically replaying an uncertain mutation. Extend the gated-write test to cancel the initiating caller while a second caller waits.

2. **MAJOR — Remote settlement can crash the sender after its atomic claim.**  
   [RequestsEngine.kt:216](/home/eslam/Storage/Code/wt/native-reviews/src-req/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/RequestsEngine.kt:216)

   **Problem/evidence:** `answerNow` and `retry` do not enter the serial lane. After their successful CAS, the event collector can process `PermissionSettled` and remove the entry before `state.value.first { … }`. That lookup throws `NoSuchElementException` instead of returning an `Outcome`, also leaving any shared waiter unresolved. The existing tests use one `StandardTestDispatcher`, which cannot exercise this between-instruction thread race.

   **Fix:** Capture the claimed entry atomically with the phase transition, or perform claim and capture on the structural lane. Handle settlement as a normal resolved outcome, and revalidate the claim after transport wake before dispatch. Add a controlled concurrent-settlement regression.

3. **MAJOR — A location-scoped refetch deletes asks received from other projects.**  
   [RequestsEngine.kt:77](/home/eslam/Storage/Code/wt/native-reviews/src-req/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/RequestsEngine.kt:77)

   **Problem/evidence:** The collector accepts `events.all` and discards `GatewayEnvelope.location`. However, plan 04 §7.10 defines `GET /permission/request` as location scoped. `reconcile` at lines 299–305 treats that list as authoritative for every stored permission. An ask from project B received while the gateway is pinned to A is removed by A’s next successful refetch. If Held, its accepted answer is silently dropped. Its tombstone then prevents subsequent list hydration from restoring it.

   **Fix:** Preserve request location and reconcile only entries covered by the snapshot’s captured location. Alternatively, consume `events.project` for this engine and leave other-project attention to its owner. Test two directories with distinct pending lists and a refetch during B’s hold.

4. **MAJOR — An unrelated generic 404 silently resolves a still-pending request.**  
   [RequestsEngine.kt:255](/home/eslam/Storage/Code/wt/native-reviews/src-req/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/RequestsEngine.kt:255)

   **Problem/evidence:** `notFoundTags` includes generic `NotFoundError`. Thus `Failure.NotFound(WireTag("NotFoundError"), null)` makes `timedOut` true. The short circuit at line 243 skips the pending-list check, removes the card, tombstones its ID, and returns success—even when the server still lists it. B-CHAT-076 and plan 09 §5.1 require a different 404 to remain a failure. The existing negative test covers only `SessionNotFoundError` with a different ID.

   **Fix:** Recognize only request-kind-specific not-found evidence. Generic route/session failures must follow reconciliation and remain `NotAccepted` when the request is listed. Add the generic-tag/no-ID case with the request still pending.

5. **MAJOR — The engine sends invalid required question answers.**  
   [RequestsEngine.kt:275](/home/eslam/Storage/Code/wt/native-reviews/src-req/native/engine/requests/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/requests/RequestsEngine.kt:275)

   **Problem/evidence:** Question admission rejects only permission-shaped answers. For a required, single-choice question with `custom=false`, both an empty answer payload and an unoffered label are accepted by `answer`/`answerNow` and forwarded to `answerQuestion`. Prompt count and selection cardinality are also unchecked. The presentation guard protects `choose`, but the public engine command bypasses it. This contradicts plan 09 §5.4 and the claimed B-CHAT-072 requirement that invalid values never send.

   **Fix:** Validate against `QuestionPrompt` in the engine before claiming: prompt count, required nonempty answers, single/multiple cardinality, offered labels, and custom-answer permission. Add direct engine tests asserting **zero port writes** for invalid payloads.

6. **MINOR — A closed Always-allow confirmation can reopen without another tap.**  
   [ChatRequestsModel.kt:163](/home/eslam/Storage/Code/wt/native-reviews/src-req/native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/requests/presentation/ChatRequestsModel.kt:163)

   **Problem/evidence:** Invalidating a confirmation only hides its derived UI; `local.confirm` retains the ID. Open the confirmation, switch `persistentPermissionGrants` off, then back on: the confirmation reappears automatically. Held → Undo similarly restores it. The existing test switches grants back on but immediately answers once, masking the reopening. Plan 09 §5.3 requires invalidation to close the confirmation.

   **Fix:** Clear the stored confirmation when its request stops Waiting or grants become unavailable. Assert it stays closed after capability restoration and Undo until another explicit Always-allow tap.

Static test-sensitivity checks:

| Test | Incorrect implementation its assertions would detect |
|---|---|
| `RequestsEngineTest` “an answer is held 3 s…” | Removing the delay produces a reply before the empty-write assertion at line 79. |
| “a second answer during the hold is ignored” | Allowing replacement returns `Ok` instead of the asserted refusal at line 127. |
| “a refetch that started before a settle…” | Removing the settled-ID guard resurrects the request, failing line 310. |

Read-only review: no files changed; no Gradle or executable mutation tests run. Core contract compatibility was inspected statically; compilation remains unverified.