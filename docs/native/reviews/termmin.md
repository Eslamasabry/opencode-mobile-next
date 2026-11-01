Verdict: **REQUEST CHANGES** — lifecycle races can leave orphaned or unprotected sign-in terminals.

1. **MAJOR — Foreground loss is missed during native startup.** [SignInTerminalSession.kt:139](/home/eslam/Storage/Code/wt/native-reviews/src-termmin/native/feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/signin/SignInTerminalSession.kt:139)  
   **Problem/evidence:** Protection is checked before `host.open()`, but loss collection starts only after it returns. If startup suspends, protection disappears, and `lost` emits without replay, the returned terminal becomes `Running` without protection. The supplied `FakeLease.losses` has exactly these semantics. This violates B-LINUX-094/096 and plan 17 §1.5.  
   **Fix:** Observe loss before opening, recheck protection after opening, and drain any late terminal before publishing it when protection has disappeared. Add a test that loses the lease while `startGate` is closed.

2. **MAJOR — Concurrent retries create orphaned terminals.** [SignInTerminalPresenter.kt:177](/home/eslam/Storage/Code/wt/native-reviews/src-termmin/native/feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/presentation/SignInTerminalPresenter.kt:177)  
   **Problem/evidence:** Two retry taps capture the same `previous` session. Both await its shared successful drain, then both call `open()`. The second call replaces `session` and cancels the first replacement’s observers without ending that replacement. Its terminal and foreground claim remain alive but inaccessible. A single main thread does not prevent this interleaving.  
   **Fix:** Gate retry synchronously before launching, serialize session replacement, and disable retry until replacement settles. Test two retries while the previous drain is suspended.

3. **MAJOR — An obsolete account probe can overwrite a newer session or foreground-loss failure.** [SignInTerminalPresenter.kt:144](/home/eslam/Storage/Code/wt/native-reviews/src-termmin/native/feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/presentation/SignInTerminalPresenter.kt:144)  
   **Problem/evidence:** `check()` suspends in `confirmSignedIn()` and updates UI unconditionally afterward. Its job is absent from both watcher lists. During that suspension, foreground loss can drain the session and display `Failed`; retry can then open another terminal. The old probe subsequently publishes `SignedIn` or `NotYet` over the replacement, hiding a live terminal.  
   **Fix:** Track and cancel probe jobs, and verify session identity plus generation after every suspension before publishing results. Test with a gated probe, foreground loss, and retry.

4. **MAJOR — ViewModel cancellation has no cleanup path.** [SignInTerminalViewModel.kt:33](/home/eslam/Storage/Code/wt/native-reviews/src-termmin/native/feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/presentation/SignInTerminalViewModel.kt:33)  
   **Problem/evidence:** Admission, startup, loss observation, and draining all use `viewModelScope`. Clearing the ViewModel cancels them without calling `end()` or releasing its reserved lease. Cancellation during `host.open()` also leaves `opening` at `Pending`, so a later `end()` waits indefinitely. The documented host fallback has no screen-owner registration in this interface. The “disposing” test explicitly calls `end()`; it does not dispose or cancel the scope.  
   **Fix:** Put resource ownership and cleanup in a lifecycle owner that survives screen cancellation, with explicit owner closure and guaranteed publication/draining of late startup results. Add actual scope-cancellation cases during admission and startup.

5. **MAJOR — Cancellation during release permanently suppresses release retries.** [SignInTerminalSession.kt:218](/home/eslam/Storage/Code/wt/native-reviews/src-termmin/native/feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/signin/SignInTerminalSession.kt:218)  
   **Problem/evidence:** `released = true` is set before the suspending `lease.release()`. If release is cancelled before completing, `end()` records a failed drain, but the next attempt skips release and returns success. The foreground claim can remain held permanently.  
   **Fix:** Record release completion only after successful release, serialize concurrent release attempts, and ensure cleanup can finish despite caller cancellation. Test cancellation inside a suspended release followed by retry.

6. **MAJOR — The page button bypasses the foreground check required for URL handoff.** [SignInTerminalPresenter.kt:149](/home/eslam/Storage/Code/wt/native-reviews/src-termmin/native/feature/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/agents/presentation/SignInTerminalPresenter.kt:149)  
   **Problem/evidence:** `openSignInPage()` checks only whether UI still contains a terminal. It never checks the lease or whether ending has begun. Unlike `onEvent(OpenUrl)`, it opens the browser after protection disappears but before the loss collector updates UI. Its launched work can also execute after `leave()` drains the terminal.  
   **Fix:** Route button and event handoffs through one session-owned operation that checks current protection, identity, and ending state immediately before dispatch. Cover the button path separately from the existing event-path test.

7. **MAJOR — Accessible output is stripped but never redacted.** [KitTerminalView.kt:155](/home/eslam/Storage/Code/wt/native-reviews/src-termmin/native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/terminal/KitTerminalView.kt:155)  
   **Problem/evidence:** Plan 06 §7.7 requires a redacted transcript. `TerminalTranscript.forDisplay()` removes controls and truncates; printable credentials such as `Authorization: Bearer …` pass through unchanged into selectable text and the live-region announcement. The local `TerminalModel.transcript` contract promises only stripping and capping, so it does not establish upstream redaction either.  
   **Fix:** Produce a redacted display transcript through the shared redactor before selection or announcement. Keep permitted sign-in page/code extraction separate. Test credential canaries in both rendered text and accessibility descriptions.

8. **MAJOR — Both new modules fail the module-registration gate.** [feature/agents/build.gradle.kts:1](/home/eslam/Storage/Code/wt/native-reviews/src-termmin/native/feature/agents/build.gradle.kts:1)  
   **Problem/evidence:** At `da529d18`, neither `:feature:agents` nor `:ui:kit-content` appears in `native/MODULE_OWNERS`. Settings discovers both automatically; `ModuleOwnersTest` requires exact equality between discovered projects and registry rows. The focused module checks reported by the authors omit this failing integration gate.  
   **Fix:** Have the coordinator register both modules and their ownership before accepting the range.

9. **MINOR — Tests claim ordering guarantees they do not assert.** [SignInTerminalPresenterTest.kt:151](/home/eslam/Storage/Code/wt/native-reviews/src-termmin/native/feature/agents/src/commonTest/kotlin/io/github/eslamasabry/oc/feature/agents/presentation/SignInTerminalPresenterTest.kt:151)  
   **Problem/evidence:** The “checked while protection holds” test checks only final confirmation/release counters. Releasing before probing still satisfies every assertion. Likewise, the Done test at line 182 does not assert drain-before-probe ordering. `FakeAgents.confirmSignedIn()` completes immediately, hiding suspended-probe races.  
   **Fix:** Record operation order, assert lease state inside the probe, and gate the probe so loss, retry, and leaving can interleave.

Static mutation checks identified three tests with assertions that would detect the corresponding broken behavior:

| Test | Detectable mutation |
|---|---|
| `SignInTerminalSessionTest.kt:21` | Starting the PTY before admission violates the empty-start assertion. |
| `SignInTerminalSessionTest.kt:105` | Releasing or completing before late startup violates the captured early-state assertions. |
| `TerminalAccessibleModeTest.kt:164` | Omitting CR or retaining field text violates the exact write/clear assertions. |

Read-only review of `95e4efbc..da529d18`. No builds or tests launched, as required by plan 01 §10.3; compilation was not independently verified.