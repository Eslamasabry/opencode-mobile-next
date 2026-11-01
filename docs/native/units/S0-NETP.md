# S0-NETP: network proofs P-N1..P-N6 (report)

| | |
|---|---|
| Unit | S0-NETP (plan 15 unit row; plan 04 §17) |
| Branch | `native/netp-2026-11-01`, from `claude/native-netp-implementation-5hsqny` at `6627d320` |
| Write set used | `native/spikes/netp/**`, `docs/qa/native-oc2-probe-2026-11/README.md`, and this report |
| Finish line | P-N1…P-N6 have verdicts with evidence. Non-goal: production network code. |
| State | Implemented, verified in this container, committed, pushed. Nothing is released or enabled. |

## Verdicts

| Proof | Verdict | Evidence (numbers from the final full run) |
|---|---|---|
| **P-N1** WebSocket over an OkHttp upgrade + Ktor `RawWebSocket` | **PASS**. The plan's design holds, so the own-framer fallback is not needed. | Round trip with mockwebserver3's OkHttp WebSocket server. The 101 is checked for status, `Upgrade`, `Connection`, `Sec-WebSocket-Accept`, no `Sec-WebSocket-Extensions`, and the subprotocol echo. 401 and 403 are told apart. We never offer `permessage-deflate`. One frame of 8 MiB + 1 closes with **1009** (Ktor `maxFrameSize`). Fragments that add up to 8 MiB + 1 also close with **1009** (our running total). Exactly 8 MiB arrives whole. Server pings get a pong. Our ping goes out every interval (4–6 in 1.1 s at 200 ms, asserted; default 20 000 ms). A missing pong closes with **1001**. The trust gate runs for the upgrade. **Live Paseo 0.11.2**: hello → `server_info` `version=0.11.2` in 303 ms, no extension negotiated, app-level ping/pong, clean close 1000. A wrong password closes with **4401**. |
| **P-N2** pinned TLS under Conscrypt | **PASS on the JVM with Conscrypt 2.5.2** (and SunJSSE). **Emulator half NOT RUN.** | A matching pin works, and `Conscrypt.isConscrypt(socketFactory)` confirms Conscrypt did the handshake. A changed leaf fails inside the handshake (`SSLHandshakeException` ← `PinMismatchException`), latches, the next call is refused before connecting (`TrustRefusal.CertificateChanged`), and **the server records 0 requests**, so no Authorization header leaves the client. An expired matching leaf fails with `CertificateExpiredException`, separate from a mismatch, and does not latch (B-SEC-007). A leaf for another hostname with a matching pin passes, because the pin is the identity. A pinned client refuses `http://` before any socket. The container has no `/dev/kvm`, so the API 37 emulator run against a server on the host (the emulator's host alias) is still owed (see open problems). |
| **P-N3** SSE through Ktor's OkHttp engine, live OpenCode 2.1.1 | **PASS** | First event `server.connected` after 79 ms (191 ms on a cold first run; 2.2.5: 11–13 ms). Heartbeat `: heartbeat` measured at 14 988 / 15 003 / 15 005 ms, so 15 s as expected. Through a paused proxy the stall fired at **45 000 ms** with `socketTimeoutMillis = 45_000`, and the stream reconnected 1 016 ms later (OC2 backoff 1 s). `HttpTimeoutConfig.INFINITE_TIMEOUT_MS` compiles and is used for the request timeout. The offline variant fires at 1 503 ms for a 1 500 ms stall. |
| **P-N4** 2.1.1 vs 2.2.5 | **PASS, with drift to act on** | See `docs/qa/native-oc2-probe-2026-11/README.md`. Both `openapi.json` files are recorded byte for byte. 2.2.5 adds 25 ops and removes 0. Heartbeat is 15 s on both. Receipt `type` is `user` on both (N4-D13 confirmed). The session-log route is present on both. `/api/info` gains `hostname` in 2.2.5. **Drift:** receipt and inbox items use `time.created`, but the Flutter fixtures use `timeCreated`. `/api/health` returns 404 on both. Durable log `after=0` replays nothing. |
| **P-N5** retry trap | **PASS, with the codex-04 BLOCKER fix**. The plan builder alone **FAILS**. | Full Ktor client, mockwebserver3. A POST on a reused connection whose socket is cut after the request is read: **1** request, and the caller gets an IOException. Evidence of the BLOCKER: the plan's §3.2 builder **resends a POST on `503 Retry-After: 0`** (2 requests, final 200). Ktor's engine without the `config {}` block **resends the POST on the dropped reused connection** (2 requests, final 200), so B-PROTO-128 is load-bearing. With the fixes, these all see exactly **1** request: 503 + Retry-After 0 on a bodied POST, on a bodyless POST, and on a DELETE; a 408; a 307 (not followed). A GET 503 is not retried either. The audit fixes alone still hold if Ktor's default retry leaks back in. |
| **P-N6** network change | **PASS on the JVM simulation, with one design change.** **Emulator half NOT RUN.** | Simulation: open sockets go silent with no FIN or RST, as when the old interface is gone, and new sockets work. With `evictAll()` plus a `retryNow()` that **also cancels the in-flight attempt**, the stream is back in **6 ms** offline and **15 ms against live 2.1.1**. The prompt that was in flight (request delivered, reply held) is cancelled and becomes DeliveryUnknown. It is never resent: the server sees it once offline, and live 2.1.1 shows `GET …/message/{clientId}` 200 with **1 copy** in the session after 3 s. Evidence: `evictAll()` alone does **not** bring a stream back within 3 s, because OkHttp does not evict in-use connections, so the stream waits for the 45 s stall. |

## Deviations from the plan, and audit findings applied

1. **Audit codex-04 BLOCKER #1 applied (it disagrees with plan 04 §3.2/§3.5; per DRAFT-STATUS the audit wins).**
   `retryOnConnectionFailure(false)` is not enough. Proposal for WP-04.3 `HardenedOkHttp.build`:
   - `OneShotMutationBodies`, an application interceptor that wraps every non-GET/HEAD/OPTIONS body as
     `isOneShot() = true`. This stops body replay on connection-failure retry, on 408 and on 421.
   - `NoStatusFollowUps`, a network interceptor that moves `Retry-After` on 503 and 408 into `X-Oc-Retry-After`,
     so OkHttp's `RetryAndFollowUpInterceptor` (5.5.1, lines 258–270) cannot issue its follow-up. Bodyless
     mutations have nothing to make one-shot, so this layer is the one that protects them.
   - 421 follows up only on a coalesced HTTP/2 connection with a non-one-shot body. That is not reproducible over
     HTTP/1.1 loopback, so it is covered by the one-shot body and not tested.
   - P-N5 should be widened in plan 04 §17 to the six cases above. B-PROTO-128 should name 503/Retry-After 0 and
     bodyless mutations.
2. **`retryNow()` must cancel the current attempt, not only the wait (P-N6).** Plan 04 §6.2 lists
   "waiting for delay → network changed / retryNow() → Reconnecting (cancel the delay)". A stream that is
   *Connected* on a dead path is not covered by that row or by `evictAll()`. Add a row: "`Connected` → network
   changed / `retryNow()` → `Reconnecting` (cancel the request; attempt = 0; connect now)". Row B-PROTO-138 should
   assert it with a stream held open on a silent socket.
3. **The stall shows up as `ClosedByteChannelException` → … → `SocketTimeoutException`** (Ktor 3.6.1 wraps the
   OkHttp read timeout four times). Plan 04 §3.8's `JvmErrorKind` classifier must walk the cause chain, or a stall
   gets reported as a generic closed channel.
4. **Ktor `RawWebSocket` writer and uncaught exceptions.** After the socket closes, the writer's
   `finally { output.flushAndClose() }` throws `SocketException: Socket closed` into its coroutine. The spike's
   test server showed it as an uncaught exception. `OkHttpWebSockets.connect` gives the raw session a
   `CoroutineExceptionHandler`. Production should do the same and route the exception to `AppLog` (no message
   text), otherwise an Android app crashes on an ordinary disconnect.
5. **Ktor `RawWebSocket.maxFrameSize` caps a frame, not a message.** Continuation frames come through as the first
   frame's type with `fin = false`. The running total in `WsSession` is required, as plan 04 §5.2 step 5 says, and
   the test proves it.
6. **Test names.** The unit row lists no catalog rows ("—"), so the tests are named after the proof ids (`P-N1 …`).
   The evidence touches the plan 04 §15 rows B-PROTO-128, 129, 131, 132, 138 and B-SEC-007. Those rows are
   still owned by WP-04.3/04.4/04.6. These spike tests are inputs to them, not replacements.
7. **Version pin mismatch, for the owner (N4-O3).** The Flutter builtin (`lib/builtin/setup/components.dart`
   `OpenCodePins`) already installs `@opencode/cli` **2.2.5**. The plan pins **2.1.1** for the switch. P-N4 found
   nothing in 2.2.5 that breaks the native client (no removed routes, same heartbeat and receipt), but the phone
   server and the plan disagree today.
8. **The live probe ran in this cloud container, not on the VPS** (plan says "on the VPS"). The results are
   loopback-only, as plan 04 §17 intends.
9. **Conscrypt version.** The spike uses `conscrypt-openjdk-uber` 2.5.2. Maven Central's latest is 2.8.0. On
   Android, the platform's Conscrypt is used and no artifact is needed.

## Commands and results

Toolchain: JDK 21.0.12.1 (OpenJDK, Ubuntu), Gradle 9.7.1 (the repo wrapper), Kotlin 2.4.21, Node 22.23.4, npm 10.9.9.
No Android SDK was installed because the spike is JVM only and the emulator halves need KVM, which this container
lacks.

```bash
# scratch servers (loopback, isolated HOME/XDG, password generated into a mode-600 file, never printed)
npm i --prefix /tmp/oc/211 @opencode/cli@2.1.1; npm i --prefix /tmp/oc/225 @opencode/cli@2.2.5
OPENCODE_PASSWORD=<from file> opencode serve --port 4097|4098 --hostname 127.0.0.1
npm i --prefix /tmp/paseo @getpaseo/cli@0.11.2
PASEO_PASSWORD=<from file> PASEO_LISTEN=127.0.0.1:4099 (+ the PhoneAgentHost.kt env set) paseo daemon run --home <dir>

native/gradlew -p native/spikes/netp test       # BUILD SUCCESSFUL in 18s — 34 passed, 0 failed, 0 skipped
NETP_OC2_URL=http://127.0.0.1:4097 NETP_OC2_URL_NEXT=http://127.0.0.1:4098 NETP_PASEO_URL=ws://127.0.0.1:4099/ws \
NETP_PASSWORD_FILE=<file> native/gradlew -p native/spikes/netp liveTest   # BUILD SUCCESSFUL in 2m 22s — 7 passed
python3 -I native/spikes/netp/probe/route_diff.py recorded/2.1.1/openapi.json recorded/2.2.5/openapi.json
```

The 34 offline tests are P-N1 ×10, P-N2 ×10 (5 cases × Conscrypt/SunJSSE), P-N3 ×2, P-N5 ×10 and P-N6 ×2. The 7
live tests are P-N1 Paseo ×2, P-N3 2.1.1, P-N3/P-N4 heartbeat on 2.2.5, P-N4 ×2 and P-N6 2.1.1.

Maven Central answered HTTP 429 through the sandbox proxy. As in P1 (`docs/native/slice0/P1-skeleton.md` D10), an
untracked `~/.gradle/init.d/central-mirror.gradle.kts` puts Google's Central mirror first. The checked-in spike
settings use plain `mavenCentral()`.

## Resolved versions

okhttp-jvm 5.5.1, mockwebserver3 5.5.1, okhttp-tls 5.5.1, okio-jvm 3.18.2, ktor-client-okhttp-jvm 3.6.1,
ktor-websockets-jvm 3.6.1 (`RawWebSocket` public on JVM), kotlinx-coroutines 1.11.0, conscrypt-openjdk-uber 2.5.2,
junit-jupiter 5.10.1 (from kotlin-test-junit5), kotlin-stdlib 2.4.21. Servers: OpenCode v2.1.1 and v2.2.5
(`cli-linux-x64`, SHA-256s in the probe README), Paseo 0.11.2.

## Catalog requests (for the build-logic / catalog owner)

| Alias | Coordinate | Why |
|---|---|---|
| `ktor-websockets` | `io.ktor:ktor-websockets:3.6.1` | `RawWebSocket` for `:core:net-okhttp` (already proposed in plan 04 §2.3; P-N1 confirms it) |
| `okhttp-tls` | `com.squareup.okhttp3:okhttp-tls:5.5.1` | self-signed leaves for the pin tests (plan 04 §2.3) |
| `conscrypt-openjdk-uber` | `org.conscrypt:conscrypt-openjdk-uber:2.5.2` (or 2.8.0) | **test only**, `:core:net-okhttp` desktopTest/jvm tests, to run the pin tests under the Android TLS provider |
| `junit-jupiter-params` | `org.junit.jupiter:junit-jupiter-params` (BOM) | provider-parameterised tests (only if the module's test framework is JUnit 5) |

## Open problems

1. **P-N2 and P-N6 on an emulator are still owed.** They need KVM (the PC or the VPS). P-N2: API 37 AVD, an
   okhttp-tls server on the PC reached through the emulator's host alias, the same five cases. The platform Conscrypt path differs from the JVM
   uber jar in `checkServerTrusted(…, socket)` routing only. P-N6: `svc wifi disable/enable` and `svc data` with
   the stream open and a prompt in flight, using the real `ConnectivityManager` callback. The JVM result says the
   reaction works once the callback fires. It does not measure callback latency.
2. **Fixture drift (`timeCreated` vs `time.created`)** needs a re-record before WP-04.5 (owner: WP-04.7). Plan 04
   §17 already states this fallback.
3. **The durable session log replays nothing from `after=0`** on 2.1.1 and 2.2.5. This does not matter while the
   log is unused (§6.7). Probe it again before anyone builds a resume on it.
4. **`docs/opencode2-protocol-notes.md` still documents `GET /api/health`.** That file is outside this unit's write
   set. Its owner should switch the discovery probe to `/api/info`.
5. The spike's `MiniStreamLoop` is not the plan's `EventStreamLoop`. It has no gate, no stability timer and no
   status labels. Only the transport behaviour was proved.
