**Verdict: BLOCKED — the merged range does not compile against its core contracts and contains memory, delivery, lifecycle, and trust defects.**

Static, read-only review of `c76a79e1^1..5ec8616d`. No builds or tests executed. Paths below are relative to `native/core/`.

1. **BLOCKER — Removed `Failure` constructors remain throughout networking.**  
   **Location:** `net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/Http.kt:74`  
   **Problem/evidence:** `StatusMapping` and `TransportFailures` construct `Failure.SignIn`, `Network`, `TimedOut`, `Busy`, etc. This range removes those leaves in `common/.../Failure.kt`, replacing them with `Failure.Net.*`. `SseConnector.kt:65` and network tests also reference removed constructors. The merged module cannot compile; the separate branch test results do not establish integration.  
   **Fix:** Reconcile the hierarchy with plan 04 §3.8, then update every mapper and test atomically. Preserve the agreed delivery-unknown representation and distinct authentication reasons.

2. **BLOCKER — The status bridge uses data classes as singleton values.**  
   **Location:** `net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/stream/StreamState.kt:36`  
   **Problem/evidence:** `StreamStatus.Reconnecting` and `Disconnected` now require `(attempt, nextAttemptAtMs)`. Returning their names without construction does not compile. The bridge also collapses `WaitingForNetwork` and `Stopped(failure)` into disconnected, discarding states expressly frozen in `contracts/s1.md`.  
   **Fix:** Use the shared `StreamStatus` directly, or construct each corresponding variant while preserving schedule and failure information. Add an exhaustive bridge test.

3. **BLOCKER — Gateway contract changes leave existing consumers uncompilable.**  
   **Location:** `domain/src/commonMain/kotlin/io/github/eslamasabry/oc/core/domain/Factory.kt:43`  
   **Problem/evidence:** The new contract exposes `kind` and `open(spec, scope)`, while `native/engine/connection/.../ConnectionRegistry.kt:48` still accesses `backend`, and line 63 still calls `connect(profile)`. `ConnectionSession.kt` also retains obsolete singleton-status assumptions. S0’s report documents a consumer patch but explicitly says it was reverted.  
   **Fix:** Land the consumer adaptations with this contract change and validate the unchanged merged graph. Documentation containing an unapplied patch cannot satisfy the integration gate.

4. **MAJOR — The default engine silently ignores its trust specification.**  
   **Location:** `net/src/jvmShared/kotlin/io/github/eslamasabry/oc/core/net/okhttp/OkHttpEngineFactory.kt:37`  
   **Problem/evidence:** The default `hardenedBaseClient` ignores `origin`, `cleartextPermitted`, and `pinnedLeafSha256`. A caller can create a client with cleartext forbidden or a certificate pin, yet send an authenticated request over HTTP, to another origin, or to a CA-valid certificate with the wrong leaf. Plan 04 §§4.4–4.6 requires refusal before HTTP credentials leave the device.  
   **Fix:** Require the trust-enforcing builder. Until available, fail closed for unsupported trust specifications; keep the permissive builder confined to tests.

5. **MAJOR — Empty SSE data lines bypass the memory cap.**  
   **Location:** `net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/sse/SseParser.kt:128`  
   **Problem/evidence:** `frameBytes` counts only value bytes, while every `data:` line appends another list entry. An endless sequence of `data:\n` grows `data` indefinitely with `frameBytes == 0`; eventual dispatch also allocates an unbounded newline string. Small nonempty values similarly omit separator bytes. This violates plan 04 §6.1’s frame cap and B-PROTO-033. The fuzz oracle repeats the same omission at `SseParserTest.kt:181`.  
   **Fix:** Account for joined separators and retained representation, including empty lines. Enforce a bounded accumulator and test repeated empty lines, boundary separators, and recovery after discard.

6. **MAJOR — Closing `NetClient` leaves its engine open.**  
   **Location:** `net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/Http.kt:104`  
   **Problem/evidence:** The factory passes an already-created engine to `HttpClient(engine)`, then `NetClient.close()` only calls `http.close()`. Inspection of the cached Ktor 3.6.1 bytecode confirms this overload sets `manageEngine=false`; client closure does not close that engine. Repeated connection creation/deletion leaves engine resources alive, contrary to plan 04 §§3.1–3.3.  
   **Fix:** Retain explicit engine ownership and close it during transport shutdown, including the underlying OkHttp resources. Verify closure and cancellation of an active streaming request.

7. **MAJOR — Automatic reconnect cannot be disabled.**  
   **Location:** `net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/stream/EventStreamLoop.kt:82`  
   **Problem/evidence:** The loop retries indefinitely and reconnects on network changes. Neither its constructor nor `GatewaySpec` carries `reconnectAllowed`. Plan 04 §6.2 and N4-D21 require disabled reconnect to retain data, publish a null next-attempt deadline, and allow only a single manually requested attempt. Keeping a healthy stream connected while disabling future retries cannot be represented.  
   **Fix:** Thread the policy through the gateway contract and observe it during backoff and network changes. Test disabling during a live stream and during a wait, plus manual one-attempt retry.

8. **MAJOR — OC2 authentication responses permanently stop its stream.**  
   **Location:** `net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/stream/SseConnector.kt:62`  
   **Problem/evidence:** Both 401 and 403 produce `stop=true`; `EventStreamLoop.kt:101` then waits indefinitely for manual retry. Plan 04 §§4.8 and 6.2 explicitly requires OC2’s normal backoff to continue after 401; 403 must not set password rejection. The tests at `HttpTest.kt:111` and `EventStreamLoopTest.kt:224` enforce the incorrect behavior. Additionally, line 102 discards a password-update retry received while the rejected request was in flight.  
   **Fix:** Apply backend-specific authentication policy, expose the last authentication failure, and retain OC2 backoff. Replace these tests with plan-conforming cases, including password update during an outstanding request.

9. **MAJOR — Resume cursor advances before frame delivery, allowing event loss.**  
   **Location:** `net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/stream/EventStreamLoop.kt:198`  
   **Problem/evidence:** `onId` advances `lastEventId` before the suspending rendezvous `send`. If the consumer is busy, a network change or `retryNow()` cancels the reader while that send is blocked. The next request resumes after an ID whose frame the consumer never received. The existing resume test uses a fast consumer and misses this case.  
   **Fix:** Advance the cursor only after successful handoff, with a documented acknowledgement policy. Test a blocked consumer interrupted between cursor assignment and delivery.

10. **MAJOR — Default REST timeout cuts off healthy long responses.**  
    **Location:** `net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/Http.kt:109`  
    **Problem/evidence:** Every request receives a 120-second total deadline. Plan 04 §§3.3–3.4 explicitly requires no default total deadline: two minutes is the inactivity timeout. A download or export receiving chunks continuously still fails at two minutes; a mutation awaiting its response becomes dispatch-uncertain unnecessarily.  
    **Fix:** Separate socket inactivity from optional per-call total deadlines. Test a response lasting over 120 seconds with regular chunks.

11. **MAJOR — Networking defines an incompatible second network contract.**  
    **Location:** `net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/gate/Network.kt:27`  
    **Problem/evidence:** Net defines its own `NetworkStatus`, `NetworkState`, and concrete `NetworkGate`. Core domain separately freezes identically purposed interfaces using `core.model.NetworkState`; the net gate implements neither. A platform implementation or `core.testing.FakeNetworkStatus` conforming to the frozen contract cannot be passed into this loop. Net tests hide the mismatch by implementing another local fake.  
    **Fix:** Consume one shared network contract and provide a compatible gate implementation. Add a composition test using the core fake.

12. **MINOR — Jitter defeats the hidden-idle minimum.**  
    **Location:** `common/src/commonMain/kotlin/io/github/eslamasabry/oc/core/common/pace/DefaultPacer.kt:37`  
    **Problem/evidence:** `Cadence` accepts `hiddenIdle=60.seconds, jitter=0.5`, but the pacer can then fire after 30 seconds. PB-42 requires actual hidden-idle intervals of at least 60 seconds. Existing tests separately check the declared floor and foreground jitter, leaving their interaction unchecked.  
    **Fix:** Enforce the minimum after jitter, or validate the minimum possible interval. Add a hidden-idle jitter test.

Assertion sensitivity was inspected for at least three tests, without execution:

- `NoResendTest.kt:74`: permitting the 503 POST follow-up makes the server-count assertion at line 79 fail.
- `SseParserTest.kt:86`: retaining the partial frame makes the exact output assertion at line 92 fail.
- `EventStreamLoopTest.kt:147`: removing stall detection makes the expected reconnect at 46 seconds fail.

One additional coverage hole: `HttpTest.kt:86` claims to prove “no request timeout,” but supplies an immediate response and asserts no timeout setting. Removing the connector’s infinite-timeout override would leave this test passing.