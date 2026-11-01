Verdict: **REQUEST CHANGES** — duplicate dispatch and durable queue-loss paths remain in `d049231f..45a3b0bb`.

1. **BLOCKER — Concurrent flushes can dispatch the same prompt twice.**  
   [OfflineQueue.kt:403](/home/eslam/Storage/Code/wt/native-reviews/src-dlv/native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/queue/OfflineQueue.kt:403)

   `flushing` uses an unsynchronized check/set, and public commands never enter an engine serial lane. Two threads can enter `flush()`, both pass the unmarked-entry check, and suspend in preflight. The marker update at line 476 then unconditionally marks the entry for each caller; it does not reject an already-dispatched entry. Without receipts, both callers POST.

   This violates plan 03 §17.2 and B-STATE-040/042. **Fix:** enforce the owning serial lane, atomically admit one flush, and check entry identity plus `!dispatched` inside the marker transaction. Add a parallel-dispatcher test with both flushes paused before marking; the current single-scheduler tests cannot exercise this race.

2. **BLOCKER — Deferred trimming overwrites newer queue contents and dispatch markers.**  
   [OfflineQueue.kt:132](/home/eslam/Storage/Code/wt/native-reviews/src-dlv/native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/queue/OfflineQueue.kt:132)

   First load captures `limited.kept`, then schedules that snapshot for later persistence. For example, `queuePrompt(new)` holds `changes`, loads an expired queue, schedules trimming, and successfully saves the new prompt. The trimming job subsequently acquires `changes` and writes its older snapshot, deleting the acknowledged addition while memory still contains it.

   The same ordering can overwrite a newly persisted dispatch marker with an unmarked snapshot; a crash before subsequent cleanup permits automatic replay. This violates B-STATE-035/042/043. **Fix:** persist trimming within the current queue transaction, or validate a revision and save the latest cache under the lock. Test a first-load trim interleaved with admission and marker persistence.

3. **MAJOR — Online sends do not fence changes to project/location.**  
   [PromptDelivery.kt:310](/home/eslam/Storage/Code/wt/native-reviews/src-dlv/native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/PromptDelivery.kt:310)

   The dispatch fence compares link identity and online-marker eligibility, but never captures and compares the original `DispatchScope`. `GatewaySendLink.scope` changes when the same gateway receives `setLocation()`. Change directory/workspace while `awaitActionTransport()` waits: the identity check passes and the old request can dispatch under the new location. Receipt scope is also taken from the changed location.

   Commands have an additional gap: `sendCommand()` dispatches after its transport wait without even rechecking link identity. This violates B-PROTO-043 and plan 03 §17.5. **Fix:** capture origin and generation before waiting, recheck both after suspensions, and enforce them immediately before every mutation.

4. **MAJOR — A previous receipt can silently consume a replacement draft.**  
   [OfflineQueue.kt:574](/home/eslam/Storage/Code/wt/native-reviews/src-dlv/native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/queue/OfflineQueue.kt:574)

   Send entry `a` successfully, then queue different text using ID `a` in the same session. Admission permits it because the original queue entry is gone. `CommandReceiptController.send()` returns the old confirmed tombstone without dispatching; `dispatchWithReceipt()` treats that as acceptance and removes the replacement.

   The B-STATE-009 replacement test stops before flushing, so it misses this data loss. **Fix:** prevent reuse of durable command identities, including IDs present in receipt tombstones; replacements need fresh identities. Extend the test through flush and restart, asserting delivery of the replacement text.

5. **MAJOR — Check again and resend lose the ID returned by `send()`.**  
   [PromptDelivery.kt:456](/home/eslam/Storage/Code/wt/native-reviews/src-dlv/native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/PromptDelivery.kt:456)

   An uncertain send removes its bubble at line 353. That bubble held the only mapping from `local-…` to the durable `online-…` review ID. Consequently, `checkAgain(originalLocalId)` looks for `queue:local-…` and cannot find the receipt; `resend(originalLocalId, true)` cannot find the queue entry.

   `PromptDeliveryTest:76` hides the defect by constructing `LocalSendId(reviewId)` instead of using the returned ID. **Fix:** retain the mapping through review resolution, or expose a separate typed review API. Exercise both operations with the actual `send()` result.

6. **MAJOR — One completion clears Stop protection for other in-flight sends.**  
   [PromptDelivery.kt:343](/home/eslam/Storage/Code/wt/native-reviews/src-dlv/native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/PromptDelivery.kt:343)

   `stopAfterDispatch` contains conversation references, although multiple sends can be on the wire for one conversation. After Stop, the first accepted send removes the flag and performs the second stop. Another outstanding send can then arrive and restart work without another stop. A failed sibling also clears the shared flag at line 356.

   This violates plan 09 §4.4. **Fix:** track the individual dispatch IDs outstanding when Stop is pressed, and preserve each dispatch’s stop obligation until it resolves. Test two delayed sends with reversed completion order and mixed outcomes.

7. **MAJOR — Bulk clear deletes an offline send’s marker while it is on the wire.**  
   [OfflineQueue.kt:204](/home/eslam/Storage/Code/wt/native-reviews/src-dlv/native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/queue/OfflineQueue.kt:204)

   `clearAll()` refuses active online sends but ignores `inFlight`, which protects queue-flush dispatches. While a flush awaits its response, bulk clear can erase the entry and its durable marker. A lost response then leaves no review entry or retained prompt.

   This contradicts B-STATE-045’s prohibition on removing an on-wire entry. **Fix:** refuse clearing while either kind of dispatch is active, or retain protected entries and report a partial clear. Test clear during a delayed flush followed by response loss and restart.

8. **MAJOR — “Stop and send” sends even when stopping failed or was uncertain.**  
   [PromptDelivery.kt:250](/home/eslam/Storage/Code/wt/native-reviews/src-dlv/native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/PromptDelivery.kt:250)

   The `RestartTurn` branch discards the entire result of `link.stop()`. Both `Outcome.Failed` and `StopResult.Uncertain` proceed to prompt dispatch, despite the current turn potentially still running. The test only supplies a successful stop.

   **Fix:** proceed after a confirmed stop or `NothingRunning`; preserve the draft and surface failed/uncertain stopping without dispatch. Add both failure cases to the restart-route test.

9. **MINOR — Stopping a chip send puts its text into the draft.**  
   [PromptDelivery.kt:427](/home/eslam/Storage/Code/wt/native-reviews/src-dlv/native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/PromptDelivery.kt:427)

   `sendInsteadOfDraft()` passes `putBack=false`, but waiting-send cancellation unconditionally emits `PutBack`. Stop therefore inserts chip/Continue text into the composer, contrary to plan 09 §4.2. The chip test covers transport failure only.

   **Fix:** retain the restoration policy with each pending send and honor it during Stop. Test stopping a transport-blocked `sendInsteadOfDraft()`.

Static test-sensitivity checks covered three concrete mutations: bypassing refused marker admission fails `PromptDeliveryTest:57–59`; dispatching before journal claim fails `ReceiptsTest:98–100`; sending `Queue` instead of `Steer` fails `SteerTest:58`. The claimed remove/save race test at `OfflineQueueStoreTest:76–77` calls two saves and never invokes `remove()`, so it does not establish the engine-level removal guarantee.

Read-only source review; no builds, test executions, or mutation runs. No source-level incompatibility with the referenced `native/core` contracts was identified; compilation remains unverified.

