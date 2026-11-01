Verdict: **REJECT** — unsafe approval rendering, cancellation and persistence defects remain in `f5facaab..066a00be`.

1. **BLOCKER — Allow can approve a command whose tail is hidden.**  
   **File:** [ConversationsWidgetBinder.kt:307](/home/eslam/Storage/Code/wt/native-reviews/src-widgets/native/android/widgets/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ConversationsWidgetBinder.kt:307)  
   **Problem/evidence:** `commandLines` estimates width using a fixed `13sp × 0.6`, without the current font scale. `needs()` then limits the actual TextView to two lines and enables Allow when that estimate is ≤2. The renderer adjusts height for font scale but leaves the width estimate unchanged. A roughly 70-character command at 340dp width is estimated as two lines; enlarged text can require three, hiding the tail while Allow remains available. This violates plan 11 §11.3 rule 2.  
   **Fix:** Measure the complete command using the actual font metrics, scaled text size and available width. Suppress Allow whenever complete visibility cannot be proved. Add rendered tests that inspect ellipsis/clipping at enlarged text sizes.

2. **MAJOR — Widget persistence bypasses the native store’s acknowledged cache and deletion rules.**  
   **File:** [AndroidWidgetSurface.kt:39](/home/eslam/Storage/Code/wt/native-reviews/src-widgets/native/android/widgets/src/main/kotlin/io/github/eslamasabry/oc/android/widgets/AndroidWidgetSurface.kt:39)  
   **Problem/evidence:** The surface writes directly to `FlutterSharedPreferences`. Existing `core:data` contracts use `PrefsKeyValueStore`, which loads an immutable snapshot once and updates it only through its own mutations; it has no external-write listener. After that store is warm, widget writes remain invisible to native readers, `changes`, and `ProfileScope.scopedKeys()`. Newly written private targets or claims can therefore escape the generic profile sweep. Direct writes also bypass its refusal compensation and profile-lane admission.  
   **Fix:** Route adopted widget preferences through `KeyValueStore` using logical keys and the appropriate writer lanes. Register widget-owned key shapes and cleanup with the existing profile contracts.

3. **MAJOR — A late settlement can overwrite a newer foreground snapshot.**  
   **File:** [WidgetActionSnapshot.kt:46](/home/eslam/Storage/Code/wt/native-reviews/src-widgets/native/android/widgets/src/main/kotlin/io/github/eslamasabry/opencode_mobile/WidgetActionSnapshot.kt:46)  
   **Problem/evidence:** `apply()` captures snapshot A, validates against a separately loaded current identity, then unconditionally writes A at line 109. A foreground publication B between validation and save is overwritten. Even B adding another conversation while preserving the action’s identity is lost. `AndroidWidgetSurface`’s instance monitor does not protect this writer. This contradicts plan 11 §11.3 rule 7’s “concurrent foreground update wins.”  
   **Fix:** Compare-and-set against the exact snapshot bytes originally read, using `KeyValueStore.compareAndSet`; discard the patch on mismatch. Apply the same protection to idle patches and result publication.

4. **MAJOR — Inline completion loses the runner’s cancellation handle.**  
   **File:** [WidgetActionRuntime.kt:29](/home/eslam/Storage/Code/wt/native-reviews/src-widgets/native/android/widgets/src/main/kotlin/io/github/eslamasabry/oc/android/widgets/WidgetActionRuntime.kt:29)  
   **Problem/evidence:** `runner.start()` may invoke `complete` before returning its handle; the interface prohibits neither inline nor background-thread callbacks. Completion reaches the controller’s `finish()`, which calls `destroy()` while `handle` is still null. Once `start()` returns, its handle is assigned to an already destroyed runtime. The controller has removed its watchdog and discarded the runtime, so that handle is never cancelled. Unsynchronized `done` also races with callbacks from another thread.  
   **Fix:** Serialize lifecycle transitions and callbacks. If completion/destruction occurs during `start`, immediately cancel the returned handle instead of retaining it. Test inline completion, cancellation during startup, and concurrent timeout/completion. The existing test only completes after `start()` returns.

5. **MAJOR — Definite live-dispatch rejection permanently retains the claim.**  
   **File:** [WidgetActionBridge.kt:32](/home/eslam/Storage/Code/wt/native-reviews/src-widgets/native/android/widgets/src/main/kotlin/io/github/eslamasabry/opencode_mobile/WidgetActionBridge.kt:32)  
   **Problem/evidence:** `submissionError()` durably claims the action, but the live callback only publishes its result. Unlike cold execution, it never releases a definite rejection. For example: admit a tap, disconnect before the posted dispatch executes, receive `rejected/unavailable`, reconnect, then tap again. The unchanged deterministic action ID now returns `busy` indefinitely despite no dispatch having occurred. This violates plan 11 §11.3 rule 4.  
   **Fix:** Release the claim for a matched, sanitized, definite `rejected` result before publishing it. Retain accepted and uncertain claims. Add the disconnect-before-dispatch regression.

6. **MAJOR — The cold-action implementation cannot be called across the planned module boundary.**  
   **File:** [BackgroundWidgetActionController.kt:20](/home/eslam/Storage/Code/wt/native-reviews/src-widgets/native/android/widgets/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BackgroundWidgetActionController.kt:20)  
   **Problem/evidence:** The sole cold-action controller is `internal`, with no public admission facade or ingress implementation. A service shell in `:androidApp` cannot construct or call it: Kotlin module visibility rejects that integration. Plan 11 §11.3 explicitly requires `WidgetActionExtras` and a common `WidgetActionIngress` boundary so the background service can reach admission without a forbidden sibling-module dependency. Neither boundary exists here.  
   **Fix:** Expose the planned typed ingress and implement admission behind it; keep foreground-service ownership behind `ForegroundWork`. Add a consumer-module compilation check.

7. **MINOR — `Restoring` never reaches the Status widget.**  
   **File:** [FrozenComponents.kt:41](/home/eslam/Storage/Code/wt/native-reviews/src-widgets/native/android/widgets/src/main/kotlin/io/github/eslamasabry/oc/android/widgets/FrozenComponents.kt:41)  
   **Problem/evidence:** `PhoneServerLine.from()` produces `Restoring`, but the seam reduces it to `== Running`. `StatusWidgetProvider` passes a Boolean to a model that renders only running/stopped. Waiting and restoring therefore display stopped, contrary to plan 11 §11.3. The new tests assert the enum conversion without rendering that state.  
   **Fix:** Carry the three-state value through the model and renderer, with localized restoring copy and an assertion on the resulting RemoteViews.

Static test audit, without executing tests:

- `WidgetActionDispatchTest:77`: removing claim deduplication would violate `dispatched.size == 1`.
- `WidgetActionDispatchTest:122`: forwarding raw errors would violate both the null-error and stored-snapshot assertions.
- `WidgetSurfaceTest:51`: removing unchanged-payload suppression would violate the redraw-count assertion. Its listener alone cannot prove that identical values were never committed.

No builds, tests, or repository mutations were executed.

