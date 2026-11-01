Verdict: **REQUEST CHANGES** — the range contains data-loss, cancellation, reconciliation, and catalog-coverage defects.

Paths below are relative to `native/feature/chat/src/`.

1. **MAJOR — System Back bypasses draft protection.**  
   `commonMain/…/ui/ChatScreen.kt:170` never consumes `state.guardBack`; only the top-bar button invokes `leaveRequested` (`:179`). A system Back gesture pops the entry directly, discarding the in-memory draft without confirmation. This violates ADR §9.4 and plan 07 §3.4. **Fix:** install a kit Back handler enabled by `guardBack`, routing Back through the presenter. Test an actual system Back with unsaved text.

2. **MAJOR — Leaving cancels an accepted send and loses its recovery state.**  
   `commonMain/…/composer/presentation/BasicComposerViewModel.kt:74` runs the entire gateway send in `viewModelScope`; `BasicComposerHost.kt:75` immediately clears the field. Popping the entry cancels that call, while releasing the world discards its pending bubbles. The server may have received the prompt, but reopening has no dispatch marker or receipt. **Fix:** hand sends to connection-owned delivery, persist required recovery state before clearing, and retain uncertain outcomes across entry destruction (plan 09 §4.2).

3. **MAJOR — Worlds remain attached to a closed connection generation.**  
   `commonMain/…/world/GraphChatWorld.kt:122` resolves the graph only during attachment. `ConnectionRegistry.close()` cancels its collectors, but leaves `attached` populated; commands at `:216`, `:254`, and `:276` continue using that obsolete graph. Reopening the profile does not rebind the screen, which can retain a frozen “live” state. **Fix:** observe registry generation changes, invalidate detached state, and fence commands against the current graph immediately before dispatch.

4. **MAJOR — Stale and reconnect events do not reliably trigger refetch.**  
   `commonMain/…/world/GraphChatWorld.kt:138` checks `refreshRequests` only inside `handle.state.collect`. The reducer increments that counter for `TranscriptStale` and `Connected` while returning `false`, so `TranscriptEngine.applyOne()` publishes nothing. An idle chat can remain stale indefinitely. Additionally, the existing connection count is never seeded into the handle, so its first observed reconnect is treated as its first connection. **Fix:** expose an observable refresh signal or explicitly schedule reconciliation for these events, including the first reconnect after attachment.

5. **MAJOR — A delayed history response overwrites newer streamed words.**  
   `commonMain/…/world/GraphChatWorld.kt:181` fetches history while event collectors remain active, then blindly calls `loadHistory`. That API replaces existing message/part values without checking their versions. If a snapshot contains `Hel` and a live delta has already produced `Hello`, the delayed snapshot rolls the visible reply back to `Hel`. **Fix:** capture the event version before fetching and merge without replacing newer live data, as required by plan 09 §3.4.

6. **MAJOR — Equal historical text can falsely settle a new send.**  
   `commonMain/…/world/GraphChatWorld.kt:246` matches pending sends against the newest prompt solely by trimmed text. Sending “continue” again lets any subsequent transcript publication match the previous “continue” prompt and remove the new bubble before its admission is known. Repeated publications can consume multiple identical pending sends against the same server message. **Fix:** correlate by dispatched/canonical message identity; any fallback must exclude pre-dispatch messages and enforce one-to-one matching.

7. **MAJOR — Pending permission requests have no response path on this screen.**  
   `commonMain/…/ui/ChatScreen.kt:159` hardcodes `requestWaiting = false`, and the screen never renders the existing `ChatRequestSlot` or owns a `ChatRequestsModel`. A running conversation waiting for permission therefore exposes no Allow/Reject controls and still presents ordinary composer controls. **Fix:** bind the conversation’s request state, render its slot during loading/error/live phases, and derive composer ownership and Stop visibility from it (plan 09 §5.1/§6.4).

8. **MAJOR — History and search silently exclude required content.**  
   `commonMain/…/world/GraphChatWorld.kt:181` reads only 100 messages and discards `nextCursor`; there is no older-page action. `presentation/ChatPresenter.kt:134` nevertheless declares `partial = false`. Separately, `ChatRules.kt:113` searches only displayed prose and the first reasoning line, excluding tool/file data required by B-CHAT-115. **Fix:** retain pagination state, implement older-history loading, expose partial-search status, and search the underlying transcript data. The current search tests contain none of these omitted cases.

9. **MAJOR — The notice bridge permits phantom transcript messages.**  
   `commonMain/…/world/P3Bridge.kt:52` forwards any non-null notice message ID. The receiving reducer handles it with `create = MessageInfo(..., Role.User)`, creating a message when that ID was never observed. This contradicts B-CHAT-097’s explicit unexpected-ID rule. `WorldRulesTest.kt:64` checks only the mapped event, never the resulting transcript. **Fix:** attach notices only to existing messages; unknown IDs should trigger reconciliation without creating messages. Test through the reducer.

10. **MAJOR — Claimed read acknowledgement has no production effect.**  
    `commonMain/…/world/GraphChatWorld.kt:282` implements `markRead()` as a no-op. Presenter tests increment a fake counter, so B-CHAT-012 remains green while opening the real chat cannot update its read watermark. **Fix:** connect acknowledgement to the connection’s read-state owner and verify the resulting watermark/finished-unseen state, including a reply finishing while visible.

Static test-sensitivity checks: making find case-sensitive breaks `ChatRulesTest.kt:49`; removing duplicate-error suppression breaks `ChatRulesTest.kt:118`; allowing Idle to replace failure breaks `WorldRulesTest.kt:79`. These checks establish assertion sensitivity only. No builds, tests, or mutations were executed; compilation remains unverified.

