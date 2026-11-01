Verdict: **REQUEST CHANGES** — lifecycle, rollback, and admission guarantees are not met. Static, read-only review; no test processes were run.

1. **MAJOR — Update restart cannot work through the claimed native API.**  
   [OpenCodeUpdate.kt:149](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/server/OpenCodeUpdate.kt:149)

   `afterInstall` invokes `AfterUpdate` specifically when the server is running. Its documented binding is native `restartServer`, but `BuiltinLinux.kt:3326` requires `!serverRunning`. Consequently, an ordinary connected-server update fails and rolls back without starting the new version.

   Native restart also reserves a recovery attempt and requires confirmation to survive app pause. `AfterUpdate` claims to spend no budget and skips confirmation. `FakeLinuxHost.launch` models none of these restrictions, so the update tests conceal the incompatibility.

   **Fix:** add an explicit, generation-guarded native update replacement operation that drains the existing owner, avoids recovery-budget consumption, and acknowledges the replacement’s health. Test against those native semantics.

2. **MAJOR — Failed rollback is reported as completed rollback.**  
   [OpenCodeUpdate.kt:156](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/server/OpenCodeUpdate.kt:156)

   The result of `journal.rollBack()` is discarded. If restoring `*.oc-good` fails, the following start executes the still-committed new binary. Nevertheless, the result is `RolledBack`, potentially with `oldRestarted = true`.

   This violates R7’s requirement to restore and restart the old binary. `FakeJournal.rollBack` always succeeds, leaving this path untested.

   **Fix:** require successful rollback before restarting; return a distinct recovery-required failure otherwise. Add a journal-refusal test proving no subsequent launch occurs.

3. **MAJOR — Update failure adopts a newer owner’s generation.**  
   [OpenCodeUpdate.kt:157](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/server/OpenCodeUpdate.kt:157)

   After a failed restart, the code replaces the original generation with `now.serverRecoveryGeneration`. If the person stops and manually starts the server during the suspended operation, `serverRestartWanted` is true again under a newer generation. This stale update then rolls back the binary and attempts a restart using that newer authority.

   Checking only `serverRestartWanted` does not establish ownership. The Stop test covers Stop without a subsequent Start.

   **Fix:** retain the original generation throughout the transaction. A generation change must terminate update recovery without rollback or further launch; enforce that condition atomically at the native boundary.

4. **MAJOR — Update can activate and restart after work becomes busy.**  
   [OpenCodeUpdate.kt:137](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/server/OpenCodeUpdate.kt:137)

   `idle()` is checked once, before the suspending status read and journal activation. A reply, setup job, or handoff can begin during either suspension; activation and restart still proceed. The starter receives its default `stillWanted = { true }`.

   R7 requires the update to run only while idle. The current test checks only an already-busy entry state.

   **Fix:** acquire an exclusion lease shared with work admission for activation and replacement. Revalidate that lease and generation before every mutation; add suspension-controlled busy-transition tests.

5. **MAJOR — Concurrent admissions can consume the same process capacity.**  
   [ProcessBudgetGuard.kt:115](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/budget/ProcessBudgetGuard.kt:115)

   Decision and reservation are separate operations. `checking` protects only `check()`, leaving concurrent `admit()` calls—and admission versus polling—unserialized.

   With 24 measured processes, concurrent agent admissions can each observe zero reservations and independently accept four more processes. Two consume the protected reserve; three exceed Android’s 32-process limit. Atomic updates of individual lists do not make the decision-and-reservation transaction atomic.

   **Fix:** serialize admission and queue draining through one state owner or mutex, committing each reservation before another decision can use the capacity. Test overlapping admissions with controlled suspension points.

6. **MAJOR — Closing during admission can leave a waiter pending forever.**  
   [ProcessBudgetGuard.kt:113](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/budget/ProcessBudgetGuard.kt:113)

   `closed` is checked before `decide()`, which suspends in device reads. If `close()` runs during that suspension, it completes existing waiters and sets `closed`; admission then resumes and appends a new waiter without checking closure again.

   That waiter missed closure and can wait indefinitely. If the decision permits admission, work can instead be admitted after closure.

   **Fix:** make closure and enqueue/reservation mutually exclusive, checking `closed` inside the committing operation. Test closure while a reading is suspended, for both admitted and queued decisions.

7. **MAJOR — Successful manual and app-open starts never reset recovery budget.**  
   [PhoneServerStarter.kt:183](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/server/PhoneServerStarter.kt:183)

   Successful starts confirm only `LaunchMode.Recovery`. The port has no `confirmManualStart(profile)` operation.

   Plan §6.2 explicitly requires manual confirmation. Native `confirmManualServerStart` archives confirmed receipts and resets the persisted attempt count; merely starting the process does not perform that reset. After an exhausted budget and a successful manual restart, the next crash therefore remains ineligible for recovery.

   **Fix:** expose and invoke manual confirmation for successful manual/app-open starts, preserving native generation validation. Test an exhausted budget through start, confirmation, and subsequent recovery eligibility.

8. **MAJOR — Final app-open admission can supersede newly scheduled native recovery.**  
   [AppOpenStart.kt:170](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/server/AppOpenStart.kt:170)

   `wanted()` checks native recovery ownership, then `admission()` performs another suspending status read but ignores `serverRecoveryAuthority`, `serverRecoveryScheduled`, and `restorePhase`.

   Native recovery can become scheduled between those reads. Admission still returns its generation, and native `startServerForLaunch` checks foreground/wanted/generation rather than pending recovery ownership. Its subsequent manual start revokes the scheduled recovery.

   Additionally, encountering an existing restore returns immediately after consuming `used`; it does not implement R9’s wait-for-restore-then-probe behavior.

   **Fix:** revalidate recovery ownership during final admission and enforce it atomically natively. Represent Waiting/Restoring as a wait-and-adopt path. Test recovery becoming scheduled between the two reads.

9. **MAJOR — Wall-clock changes invalidate safety intervals.**  
   [PhoneServerStarter.kt:178](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/server/PhoneServerStarter.kt:178)

   Startup deadlines, budget reservation expiry, and thermal calm periods use `Clock.now()`/`Instant` subtraction. The core binding supplies `Clock.System` (`Dispatchers.kt:38`).

   A backward clock correction extends the startup wait beyond 90 elapsed seconds. A forward correction can expire reservations prematurely and release a thermal hold before two continuous cool minutes have elapsed. Plan 11 §2.4 explicitly requires monotonic deadlines and leases.

   **Fix:** inject a monotonic time source for elapsed intervals; retain wall-clock instants only for display/history. Add independent wall-clock-jump tests.

10. **MAJOR — Runtime failure types conflict with the existing core contract.**  
    [RuntimeCall.kt:10](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/host/RuntimeCall.kt:10)

    The module introduces another `RuntimeProblem` and `RuntimeCall`, claiming core lacks the required failure leaf. At the reviewed tip, core already defines `Failure.Runtime(core.common.RuntimeProblem)` and the matching enum.

    These are nominally different types: passing a runtime port’s `Refused.problem` to `Failure.Runtime` does not compile. The port results also cannot satisfy the frozen `Outcome<T>` signatures without adapters. The report’s D1 justification is stale.

    **Fix:** remove the duplicate vocabulary and return core `Outcome` with `Failure.Runtime`. Add a compile-level contract consumer using the frozen signatures.

11. **MINOR — Failed periodic priority application is suppressed for another five minutes.**  
    [AgentThrottle.kt:102](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/heat/AgentThrottle.kt:102)

    After a successful Slow application, suppose the five-minute reapplication fails. `applied` remains Slow and `lastRun` advances despite failure. The next reading therefore sees neither a tier change nor an expired interval and does not retry.

    B-ANDR-130 requires failed lowering to retry on the next reading. Its test covers only failure before the first successful application.

    **Fix:** advance the successful-application timestamp only on success, or track a pending retry. Test failure of a periodic reapplication.

12. **MINOR — Queue processing violates its oldest-first guarantee.**  
    [ProcessBudgetGuard.kt:148](/home/eslam/Storage/Code/wt/native-reviews/src-rteng/native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/budget/ProcessBudgetGuard.kt:148)

    When an older waiter’s reason changes, processing continues to younger waiters instead of stopping. Queue an AI Team job and then a Linux service during heat; after cooling with 25 processes, the older job changes Warm→Room while the younger service fits and starts. Fresh `admit()` calls also bypass existing waiters.

    The oldest-first test uses identical jobs and an unchanged Room reason, so it misses both cases.

    **Fix:** enqueue newcomers behind existing work and stop draining whenever the oldest live waiter remains blocked, including after a reason change.

Test sensitivity was checked statically, without executing mutations:

| Test | Incorrect change that would fail its assertions |
|---|---|
| `PhoneServerStarterTest.kt:66` | Removing the post-password `stillWanted` check causes a launch; the asserted launch count is zero. |
| `ProcessBudgetGuardTest.kt:94` | Removing reservations admits the third session; the asserted Room wait disappears. |
| `ThermalPolicyTest.kt:46` | Removing the hot-reading timer reset resumes at 150 seconds; the test expects no action. |