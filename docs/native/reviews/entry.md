Verdict: CHANGES REQUESTED — 5 MAJOR, 1 MINOR.

1. **MAJOR — Quota notifications take the monitored-chat route.**  
   [EntryDecider.kt:164](/home/eslam/Storage/Code/wt/native-reviews/src-entry/native/engine/entry/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/entry/EntryDecider.kt:164)  
   **Problem/evidence:** Valid `quota` and `quota_reset` notifications carry `sessionID="quota"` and a 64-hex `monitorToken` (plan 11 §11 notification contract; existing `BackgroundConnectionService.validCodingAlert`). The unconditional token branch intercepts both before the quota cases, calling `ResolveMonitoredRequest` and targeting a conversation named `quota`. The later quota branch also passes `sessionId.raw` to `VerifyQuotaToken`, rather than the monitor token.  
   **Fix:** Dispatch quota kinds before monitored-conversation alerts, validate their required payload, and verify `monitorToken.raw`. Test actual valid and stale quota payloads through parser → router.

2. **MAJOR — Failed shares lose their only recoverable text.**  
   [EntryDecider.kt:146](/home/eslam/Storage/Code/wt/native-reviews/src-entry/native/engine/entry/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/entry/EntryDecider.kt:146), [EntryRouter.kt:166](/home/eslam/Storage/Code/wt/native-reviews/src-entry/native/engine/entry/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/entry/EntryRouter.kt:166)  
   **Problem/evidence:** No active server, failed creation, and failed draft seeding all produce a handoff and clear the share slot. Neither the handoff nor another retained state contains the text. Connecting afterward cannot recover it; neither can **Try again**, Copy or Undo. A seeding failure opens an empty conversation while discarding the input. This contradicts plan 07 §7.7’s explicit recovery behavior. The failed-share test asserts the lossy notice-only result.  
   **Fix:** Retain the share until successful seeding or completed explicit discard. Retain the created conversation reference after creation so retrying seeding does not create another session.

3. **MAJOR — Superseded slots can still execute server mutations.**  
   [EntryRouter.kt:149](/home/eslam/Storage/Code/wt/native-reviews/src-entry/native/engine/entry/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/entry/EntryRouter.kt:149)  
   **Problem/evidence:** `ordered` snapshots every channel before any effects run. Generation validation happens only afterward. Enqueue an internal new-task request and an old share; suspend the first creation; replace both requests; then complete creation. The first result is discarded, but iteration proceeds to the obsolete share and executes `CreateSession` and `SeedDraft` before discovering its replacement. This creates an unwanted session and stores superseded text despite “newer wins.” The captured `world` can likewise become stale across that suspension.  
   **Fix:** Recheck slot identity and read the current world before starting each decision/effect batch. Revalidate after suspension before further effects and publication. Add a test with two queued channels and replacement while the first effect is suspended.

4. **MAJOR — Cancellation of one effect permanently stops routing.**  
   [EntryRouter.kt:115](/home/eslam/Storage/Code/wt/native-reviews/src-entry/native/engine/entry/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/entry/EntryRouter.kt:115), [EntryRouter.kt:259](/home/eslam/Storage/Code/wt/native-reviews/src-entry/native/engine/entry/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/entry/EntryRouter.kt:259)  
   **Problem/evidence:** Core `attempt` deliberately rethrows `CancellationException`. If a port awaits a cancelled connection command or lets a timeout escape, that exception exits the sole signal-consumer loop. Later submissions still update slots and signal successfully, but nothing evaluates them again. AppScope supervision does not restart this coroutine.  
   **Fix:** Isolate each request’s execution in a supervised child while keeping the signal consumer alive. Preserve process-scope cancellation and avoid replaying mutations whose outcome is uncertain. Test effect cancellation followed by an unrelated submission.

5. **MAJOR — Confirmed server switches bypass canonical conversation resolution.**  
   [EntryDecider.kt:133](/home/eslam/Storage/Code/wt/native-reviews/src-entry/native/engine/entry/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/entry/EntryDecider.kt:133)  
   **Problem/evidence:** Cross-server links, alerts and scheduled taps embed an unresolved `Chat` inside `SwitchServerThen`. After confirmation, the documented continuation resubmits that target as `EntryRequest.Internal`; line 33 returns it directly without `ResolveRef`. A phone-agent conversation therefore opens using its carried owner/session pair instead of the canonical reference. Missing conversations also bypass the resolution fallback. This violates plan 07 §7.3 rule 6.  
   **Fix:** Preserve and resubmit the original typed request after the confirmed switch, allowing normal resolution and failure handling before publishing `Chat`.

6. **MINOR — The stripping test never exercises stripping.**  
   [EntryIntentParserTest.kt:131](/home/eslam/Storage/Code/wt/native-reviews/src-entry/native/engine/entry/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/entry/EntryIntentParserTest.kt:131)  
   **Problem/evidence:** “The reader strips every extra it owns so nothing can replay” only checks membership in `EntryExtras.owned`. Replacing `EntryIntentReader.strip()` with a no-op would leave it passing. It proves neither removal nor action/data/type clearing, despite the unit report claiming reader coverage.  
   **Fix:** Commit an Android contract test using real intents: read → strip → read must produce no second request, with owned extras removed and action/data/type normalized.

Static fault checks covered at least three discriminating tests:

| Test location | Fault that would fail its assertions |
|---|---|
| `EntryRouterTest.kt:38` | Removing the bootstrap/gate guard |
| `EntryRouterTest.kt:163` | Removing generation protection after effects |
| `EntryDeciderTest.kt:121` | Allowing untrusted `new_task` to emit `CreateSession` |

Read-only static review of `c5654cf7..4589ba71`; no edits, builds, test execution or mutation runs.