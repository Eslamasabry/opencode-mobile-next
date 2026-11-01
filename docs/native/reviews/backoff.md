Verdict: **REQUEST CHANGES — 6 MAJOR, 1 MINOR.**

1. **MAJOR — Lifecycle resume bypasses disabled reconnect.**  
   [EventStreamLoop.kt:111](/home/eslam/Storage/Code/wt/native-reviews/src-backoff/native/core/net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/stream/EventStreamLoop.kt:111)  
   **Problem/evidence:** With a connected stream and `reconnectAllowed=false`, HiddenIdle closes the stream. Returning to Foreground resets the count and proceeds directly to `connector.connect`; reconnect permission is checked only after that attempt ends. This violates plan 04 §6.4/N4-D21: lifecycle resume reconnects only while allowed. The added PB-53 tests all use reconnect enabled.  
   **Fix:** Gate lifecycle-triggered attempts before dispatch, preserving distinct authorization for the initial connection and a manual one-shot retry. Test disabled reconnect across Foreground → HiddenIdle → Foreground.

2. **MAJOR — HiddenIdle cannot cancel connection establishment.**  
   [EventStreamLoop.kt:130](/home/eslam/Storage/Code/wt/native-reviews/src-backoff/native/core/net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/stream/EventStreamLoop.kt:130)  
   **Problem/evidence:** The idle interrupt exists only inside `readUntilEnd`, after HTTP headers arrive and the `Connected` status is delivered. Entering HiddenIdle during DNS, connect, TLS, or response-header waiting leaves the request running until completion or transport timeout. Plan 13 §12.1 requires cancellation and `Suspended`, with no idle timer. The new tests cover immediate connection success and pending backoff, not an outstanding connect.  
   **Fix:** Race the entire connection attempt against HiddenIdle; cancel and join the attempt before publishing `Suspended`. Add a connector that suspends before invoking its session callback and verify prompt cancellation.

3. **MAJOR — Foreground return is missed when the wait began in foreground.**  
   [EventStreamLoop.kt:298](/home/eslam/Storage/Code/wt/native-reviews/src-backoff/native/core/net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/stream/EventStreamLoop.kt:298)  
   **Problem/evidence:** Visibility handling uses the snapshot taken when the wait began. Example: failures at 0, 1, 3, 7 and 15 seconds schedule the next attempt at 31 seconds; hide at 16 and return at 20. Because `seen` was Foreground, no foreground-return observer exists, so retry remains at 31 instead of firing at 20. This snapshot defect survives the rewritten wait and violates PB-51. The return test starts hidden, so it misses this transition.  
   **Fix:** Observe visibility transitions throughout the wait and recalculate the schedule when entering HiddenWatching. Test Foreground → HiddenWatching → Foreground during one pending wait.

4. **MAJOR — The proxy misses requests on reused connections.**  
   [conn_log_proxy.py:61](/home/eslam/Storage/Code/wt/native-reviews/src-backoff/native/tool/perf/conn_log_proxy.py:61)  
   **Problem/evidence:** Only the first request line is inspected; subsequent bytes are forwarded without request logging. A REST connection opened before hiding can carry further requests while idle, yet PR-01 reports zero requests. An in-memory reproduction forwarded two HTTP requests and logged one. The proxy tests open a fresh socket for each request.  
   **Fix:** Count every HTTP request using a framing-aware proxy or fixture-server instrumentation. Add a keep-alive test with the second request inside the idle window.

5. **MAJOR — PR-04 passes when reconnects stop for minutes.**  
   [hidden_checks.py:150](/home/eslam/Storage/Code/wt/native-reviews/src-backoff/native/tool/perf/hidden_checks.py:150)  
   **Problem/evidence:** PB-51 checks gaps only between recorded attempts. It never checks the interval from the final hidden attempt to foreground return. I supplied retries at 1, 3, 63 and 123 seconds, followed by **477 seconds without retries**; PB-51 still passed. The existing oversized-gap test places its violation between attempts, leaving this blind spot.  
   **Fix:** Check the trailing interval against the maximum permitted delay, allowing an unfinished final interval only within that bound. Add a test where retries cease early.

6. **MAJOR — A restarted process can receive a passing CPU verdict.**  
   [hidden_checks.py:120](/home/eslam/Storage/Code/wt/native-reviews/src-backoff/native/tool/perf/hidden_checks.py:120)  
   **Problem/evidence:** The checker subtracts cumulative CPU counters without validating process identity or a nonnegative delta. `hr_cpu_ticks` independently resolves the current PID at each sample. With start=1000 and end=5, the checker reported **−10.034 CPU seconds/hour** and passed PB-40. An app killed and restarted during the run can therefore be certified as meeting the budget.  
   **Fix:** Capture PID and process start time with both samples. Return “could not run” if identity changes or counters decrease; add a restart-counter regression.

7. **MINOR — Claimed catalog coverage does not match the catalog.**  
   [S2-BACKOFF.md:61](/home/eslam/Storage/Code/wt/native-reviews/src-backoff/docs/native/units/S2-BACKOFF.md:61)  
   **Problem/evidence:** The report lists B-PROTO-132/133/146 as passing rows. In `native/catalog/behaviors.jsonl`, 132 requires a live abort proof, 133 requires owned-state refetch and stream-query restrictions, and 146 is absent. Stall/network-loop assertions do not prove those behaviors.  
   **Fix:** Correct the test/report mappings and identify the actual integration evidence required for 132/133. Resolve missing row 146 with the catalog owner.

Validation: evaluated 13 verdict tests in memory. Three independent mutations—disabling the open-stream, CPU-limit and airplane-request checks—each caused its corresponding test to fail. Kotlin compatibility was inspected statically; Gradle, emulator and device checks were not run. No files changed.