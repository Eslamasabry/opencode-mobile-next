# Unit S1-NET: `:core:net`

Date: 2026-11-01. Branch: `native/net-2026-11-01`, made from `claude/s1-net-ktor-sse-my1ew7` at `6627d320`.
Write set: `native/core/net/**`, plus this report.

Inputs:
- plan 04 WP-04.4 and §3.2–3.8, §6.1–6.6;
- plan 13 §2.6 and §11.3 (PB-50…52);
- ADR 01;
- roadmap row S1-NET (plan 15);
- audit `codex-04` BLOCKER 1 and 3 and MAJOR 10;
- Flutter `lib/api2/sse2.dart`, `lib/api2/reconnect_backoff.dart` and `test/api2_sse_test.dart`.

**Result:** the module is written and the tests were written first. All of these pass on Android (host tests) and on
desktop, with warnings as errors:
- the Ktor client factory;
- the SSE parser, with its 16 MiB cap and a 10,000-case fuzz test;
- the `EventStreamLoop` timing table under virtual time;
- the guard that stops mutations from being sent again.

**Shipping state:** implemented, verified locally, committed and pushed. There is no PR, no CI run and no release.

**Not done: the WebSocket path from P-N1** (the roadmap row lists it; the brief did not). See "Open problems" 1.

---

## 1. What was built

All code is under `native/core/net/`, package `io.github.eslamasabry.oc.core.net`. Settings discover the module, so no
settings edit was needed.

| File | Content |
|---|---|
| `build.gradle.kts` | `oc.kmp.library`. Common code uses `:core:common`, `:core:security`, `:core:model` and `ktor-client-core` (api). androidMain and desktopMain use `ktor-client-okhttp` (api) and also compile `src/jvmShared/kotlin`. commonTest uses `ktor-client-mock`. |
| `sse/SseParser.kt` | `SseFrame` and `SseParser(maxFrameBytes = 16 MiB, onFrame)` with `feed`, `flush` and `discardPartial`. It ports Flutter's `Sse2Parser` rule by rule (plan 04 §6.1). It uses one growable line buffer and shrinks back after a line over 64 KiB. |
| `Http.kt` | Common HTTP pieces (see the list below). |
| `stream/BackoffPolicy.kt` | `BackoffPolicy` with `delayMs(attempt, hidden, random)`. Two policies: `OpenCode2` (1, 2, 4, 8, then 16 s; stall after 45 s; hidden pace of 2 quick tries, then 60–120 s) and `GasCity` (jittered, at most 30 s). `HiddenPace` holds the hidden numbers. |
| `stream/StreamState.kt` | `StreamState` (Connecting, Connected(generation), Reconnecting / Disconnected(attempt, nextAttemptAtMs), WaitingForNetwork, Stopped(failure)), plus `toModel()` to the 4-value `core.model.StreamStatus`. Also `StreamSignal` (Status, Frame + generation) and `ResumeMode` (None, QueryCursor, LastEventId). |
| `stream/SseConnector.kt` | `SseConnector`, `SseBody` and `SseConnectResult` (Ended, or Failed(failure, stop)). `KtorSseConnector` sends `Accept: text/event-stream`, sets no request timeout and a 45 s socket timeout. 401/403 stop the stream; other statuses and a wrong content type are retried. |
| `stream/EventStreamLoop.kt` | The loop: a cold `signals()` flow plus `retryNow()`. Its timing is described in §1.1. |
| `gate/Network.kt` | `Online`, `Transport`, `NetworkState`, `NetworkStatus`, `UnknownNetworkStatus` (for desktop), and `NetworkGate` (`isUsable`, `awaitUsable`, `awaitNetworkChangeFrom`). Loopback is never gated. |
| `jvmShared/okhttp/OkHttpEngineFactory.kt` | Shared JVM pieces (see the list below). |

`Http.kt` contains:
- `TransportSpec`;
- `EngineFactory` (`create` and `classify`) and `TransportError`;
- `CallKind` (Read for GET, HEAD and OPTIONS; Mutation for everything else);
- `StatusMapping`: a status becomes a `Failure`, and its details hold the status only, never server text;
- `TransportFailures`: a mutation that may have reached the server becomes `Refused(DispatchUncertain)`;
- `HttpClientFactory`, whose client has `expectSuccess = false`, `followRedirects = false`, `HttpTimeout` (8 s / 2 min / 2 min) and `UserAgent "OpenCodeMobile"`, and no retry, content-negotiation or logging plugins;
- `NetClient.exchange(build, read): Outcome<T>`.

`OkHttpEngineFactory.kt` contains:
- `OkHttpEngineFactory(base)`, which re-asserts `retryOnConnectionFailure(false)` and turns redirects off inside Ktor's `config {}`;
- `hardenedBaseClient(spec)`: the §3.2 settings without the trust parts. It is a stand-in until `:core:net-okhttp` exists;
- `MutationGuard`: one-shot bodies for mutations, plus a network-attempt counter that throws `ResendRefusedException` on a second attempt;
- a JVM error classifier.

### 1.1 Loop timing (all from an injected `Clock` and the caller's dispatcher)

| Situation | Behaviour | Source |
|---|---|---|
| A try fails or the stream drops | It waits 1, 2, 4 and 8 s, then every 16 s. `nextAttemptAtMs` is set before the status is published. | N4-D6, `sse2.dart` |
| Connected for 30 s | The failure count resets. | N4-D6 |
| 6 or more failures | The label becomes `Disconnected`, and retrying continues. | N4-D6 |
| No byte for 45 s | Counts as a drop. Heartbeat comments count as bytes. | N4-D7, B-PROTO-132 |
| Remote host, no network | `WaitingForNetwork`, with **no dial and no spent tries**. When the network returns it connects at once, with the count reset. | N4-D8, B-PROTO-133, PB-52 |
| Remote host, new default network | A waiting try fires at once with the count reset. A **live** stream reconnects at once. | N4-D8, B-PROTO-133 |
| Loopback | Never gated. | N4-D8 |
| Hidden | 1 s, 2 s, then 60–120 s with jitter. Coming back to the foreground fires a waiting try at once. | PB-51, `reconnect_backoff.dart` |
| 401 / 403 | `Stopped(SignIn)`. Nothing is tried until `retryNow()`. | plan 04 §6.2 |
| `retryNow()` | Cancels the wait or drops the live stream, resets the count and connects at once. | plan 04 §6.2 |
| Reconnect | `Connected(generation = n + 1)` is emitted **before** any frame of that generation, so the owner refetches first. | N4-D10 |
| Body ends cleanly or with an error | A clean end flushes the trailing frame. An error or a stall discards the partial frame. | B-PROTO-033 |
| `ResumeMode.LastEventId` | The id of the last frame seen is passed to the next connect. | plan 04 §6.2 |

---

## 2. Catalog rows and tests

Every test is named after its row or plan id (AGENTS rule 11).

| Row / id | Tests | Where |
|---|---|---|
| **B-PROTO-033** | 13 parser tests: bare data and keep-alive comments; event and id; multi-line data; CRLF and mid-line chunk boundaries (1, 2, 3, 7, 64); multi-byte characters split across chunks; malformed UTF-8 replaced; a lone CR is not a line end; the trailing frame is delivered only on flush; a frame cut mid-write is discarded; oversized frames are dropped without harming the next (data line, many lines, comment, a line one byte over); the 16 MiB cap at the real size; frames with no data or unknown fields; **fuzz of 10,000 cases**. One loop test covers a cut frame plus a clean-end flush through the real loop. | `SseParserTest`, `EventStreamLoopTest` |
| **B-PROTO-132** | No byte for 45 s → reconnect at 46 s. Heartbeats every 15 s keep a stream up for 10 min with one connection. | `EventStreamLoopTest` |
| **B-PROTO-133** | Offline: no dial for 10 min, `WaitingForNetwork`, then a connect at once when the network returns. Network lost during backoff: no tries spent, and the count is reset on return. Loopback never waits. A new default network reconnects a live stream at once. | `EventStreamLoopTest` |
| B-PROTO-128 (audit BLOCKER 1) | Full Ktor + OkHttp stack against a loopback socket server. A POST on a reused pooled connection that drops after the request is seen **once** and gives `DispatchUncertain`. 503 `Retry-After: 0` reaches the server once for a POST with a body and once for a bodyless DELETE. A forced second `proceed` of a mutation is refused, the server sees it once, and the result is `DispatchUncertain`. GET still works. A unit test covers `CallKind` and `TransportFailures`. | `NoResendTest` (desktop JVM), `HttpTest` |
| B-PROTO-129 | 307 on a POST is not followed: one request, `Rejected(307)`. The status table. | `NoResendTest`, `HttpTest` |
| N4-D6 / N4-D10 / PB-51 | The timing table (opens at 0, 1, 3, 7, 15, 31, 47, 63, 79 and 95 s; label from the 6th failure). Reset after 30 s stable. Refetch before the first frame of a new generation. 401 stops until `retryNow`. `retryNow` resets. Hidden pace and return to the foreground. Last-Event-ID resume. Policy unit tests. The SSE connector request and its stop/retry statuses (MockEngine). | `EventStreamLoopTest`, `HttpTest` |

B-PROTO-128 and 129 belong to S1-SECNET's `:core:net-okhttp` rows. They are used here only because the brief asks this
unit to prove "no automatic resend". S1-SECNET's mockwebserver3 versions still apply to its hardened builder.

**Red check.** With `MutationGuard` removed and Ktor's `retryOnConnectionFailure(true)` default put back, 4 of the 6
`NoResendTest` cases fail:
- 503 + POST reached the server **2** times;
- 503 + DELETE reached the server **2** times;
- the pooled-connection drop reached the server **3** times;
- the forced second proceed was not refused.

This confirms the audit's finding against OkHttp 5.5.1. The guard was then restored.

---

## 3. Commands and results

All commands ran from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and `ANDROID_HOME=/opt/android-sdk`.

| Command | Result |
|---|---|
| `./gradlew :core:net:desktopTest :core:net:testAndroidHostTest -Poc.werror=true --rerun-tasks` | BUILD SUCCESSFUL in about 21 s. desktopTest: **41 tests** (HttpTest 7, NoResendTest 6, SseParserTest 13, EventStreamLoopTest 15), 0 failed, 0 skipped. testAndroidHostTest: **35 tests** (the common ones), 0 failed. The 10k fuzz takes about 1.2 s. |
| `./gradlew :core:net:compileKotlinDesktop :core:net:compileAndroidMain :core:net:compileTestKotlinDesktop :core:net:compileAndroidHostTest -Poc.werror=true` | BUILD SUCCESSFUL, with no warnings. |
| `./gradlew :quality:test` | 16 of 17 pass, including every Konsist rule. **`ModuleOwnersTest` fails** because `MODULE_OWNERS` has no row for `:core:net`. It also has none for `:tools:arb2xml` or `:tools:flutter-migration-readers`, which already exist on the base branch. Only the coordinator edits that file (catalog request 3). |
| The red check from §2 (guard removed) | 4 of 6 `NoResendTest` cases fail as expected. Restored. |
| `grep` of `native/core/net` for private IPv4 ranges, 100.64/10 and e-mail addresses | Clean in the sources. The tests use `127.0.0.1` only. |

How the environment was set up (as the brief asked):
1. Downloaded `commandlinetools-linux-16111833_latest.zip` from dl.google.com and unpacked it to `/opt/android-sdk/cmdline-tools/latest`.
2. Accepted the licences with `yes | sdkmanager --licenses`.
3. Installed `platform-tools`, `platforms;android-37.1` and `build-tools;37.0.0`. AGP installed `build-tools;36.0.0` by itself on the first host-test run.
4. JDK 21 was already present. Installed `openjdk-17-jdk-headless` 17.0.20.1 for the toolchain.
5. Wrote `~/.gradle/gradle.properties` (auto-download off, explicit JDK paths) and `native/local.properties` (gitignored).
6. Set `BASH_DEFAULT_TIMEOUT_MS` in `~/.claude/settings.json`.
7. Wrote a container-only `~/.gradle/init.d/central-mirror.gradle.kts`, the same workaround as P1 D10: Google Maven, Google's Central mirror and the plugin portal. It is not in the repo.

## 4. Versions actually resolved

From `./gradlew :core:net:dependencies --configuration desktopRuntimeClasspath`:

| Library | Version |
|---|---|
| `io.ktor:ktor-client-core` | 3.6.1 |
| `io.ktor:ktor-client-okhttp` | 3.6.1 |
| `io.ktor:ktor-client-mock` (tests) | 3.6.1 |
| `com.squareup.okhttp3:okhttp` | 5.5.1 (through `ktor-client-okhttp`) |
| `com.squareup.okio:okio` | 3.18.2 (3.18.1 requested by Ktor, raised by OkHttp) |
| Kotlin | 2.4.21 |
| coroutines | 1.11.0 |
| `org.jetbrains.kotlinx:kotlinx-io-core` | 0.9.1 (through Ktor; the catalog pins 0.9.2 but nothing here asks for it) |

---

## 5. Deviations from the plan, and why

| # | Plan says | Built | Why |
|---|---|---|---|
| D1 | Catalog aliases `libs.ktor.client.*` (03 §5) | Coordinates spelled in `build.gradle.kts` using the catalog's own `libs.versions.ktor`, so there is no second version. | The catalog pins `ktor` but has no library entries, and this unit may not edit the catalog. Catalog request 1 lists them. |
| D2 | Identical `OkHttpBindings.kt` in androidMain and desktopMain, on top of `:core:net-okhttp`'s `HardenedOkHttp` | One copy in `src/jvmShared/kotlin`, added to both source sets with `kotlin.srcDir`. The builder is a stand-in, `hardenedBaseClient`, behind an injectable `base` parameter. | `:core:net-okhttp` belongs to S1-SECNET and does not exist on this branch. `srcDir` avoids a custom hierarchy that would turn off the default template. Konsist's `production` slice does not scan `src/jvmShared`; add it there (see §7). |
| D3 | Resend safety rests on `retryOnConnectionFailure(false)` (N4-D2, P-N5) | Two layers in `MutationGuard`: one-shot bodies, including an empty one-shot body for bodyless mutations, and a network-attempt counter that refuses any second attempt. | Audit codex-04 BLOCKER 1, followed as written: the audit's fix wins over the plan (DRAFT-STATUS). Side effect: a bodyless DELETE or POST now carries `Content-Length: 0`. |
| D4 | `Failure.Net.*` subtypes (§3.8) | Mapped onto the existing frozen `Failure` categories: SignIn, NotFound, Conflict, TimedOut, Busy, Server, Rejected, Network and UnreadableReply. "Delivery unknown" uses `Failure.Refused(RefusalReason.DispatchUncertain)`. | `Failure` is a sealed type in `:core:common` (S0-CORE), so it cannot be extended from here. When `Failure.Net` lands, only `StatusMapping` and `TransportFailures` change. |
| D5 | `StreamStatus` (rich) in `:core:net`, also used by `:core:domain` | `StreamState` here, marked `TODO(move-to-core)`, plus `toModel()` to `core.model.StreamStatus`. | Audit BLOCKER 3: domain may not see net. S0-CORE should freeze the rich type in `:core:model` (catalog request 4). |
| D6 | `Channel<SseFrame>(4096)` between reader and decoder (§6.6) | No frame queue. The flow is rendezvous-buffered, frames go out with a suspending send after each 16 KiB read, and at most one 16 MiB frame is held. | Audit MAJOR 10: 4096 × 16 MiB is about 64 GiB. The decoder hop to `AppDispatchers.default` is the consumer's (`:protocol:oc2`), with a byte budget if it buffers. |
| D7 | `EventStreamLoop(client: HttpClient, request: …)` | `EventStreamLoop(connector: SseConnector, …)`, with `KtorSseConnector(client, classify, request)` as the real connector. | The loop runs entirely in virtual time against a fake connector. The Ktor connector is tested separately with MockEngine. |
| D8 | `NetworkGate` as an interface with a `changes` flow | `NetworkGate` is a class over `NetworkStatus`, and the loop watches the default-network id itself. | Fewer seams. `:android:platform` binds only `NetworkStatus`. The registry-wide pool eviction (B-PROTO-138) stays in `:engine:connection`. |
| D9 | `SseParser.sawActivity` for the stall timer | Removed. The loop times every read with `withTimeoutOrNull(45 s)`. | Same rule, testable in virtual time, and no shared mutable flag. |
| D10 | Flutter parser parity | One fix. In Flutter, a line exactly one byte over the cap left an empty "residue" line that ended discard mode early, so the rest of that frame leaked as a new frame. Here the frame stays dropped (covered by a test). | Plan §6.1 rule 6 says "discard until the next empty line". |
| D11 | `HttpTrace` plugin (§3.9), `ResumableDownloader` (§3.10), request helpers for path segments and location queries (§3.6), JSON body caps (§3.7) | Not built. | Outside this unit's finish line (WP-04.4 lists only the parser, the loop and the client factory). Listed under open problems. |
| D12 | Plan 13 rule 1 (K-P1): no `while`/`delay` loop outside `:core:common` `pace/`; timers in `*Timers.kt` | The loop is a `while (true)` with a one-shot `delay` per wait. There is no `*Timers.kt`. | `pace/` and `TimerSpec` do not exist yet, and K-P1 is not enforced yet. The waits are backoff deadlines, not a repeating ticker. Move them onto `Pacer` when it lands. |
| D13 | Test-first with kotest-property for the fuzz (§2.3) | A seeded `kotlin.random` fuzz of 10,000 cases, checked against an independent model of the wire and against the whole-buffer parse. | No catalog entry for kotest. The result is deterministic and reproducible. |
| D14 | Metro `@ContributesBinding` for `EngineFactory` | Not annotated. | No graph uses `:core:net` yet. Binding it into `AppScope` from here would pull the module into the app graphs. The connection graph owner binds `OkHttpEngineFactory`. |

## 6. Catalog requests (coordinator / build-logic owner)

1. **`[libraries]` entries:**
   - `ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }`
   - `ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }`
   - `ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }`

   Then replace the three strings in `native/core/net/build.gradle.kts` with the aliases.
2. For P-N1 later (plan 04 §2.3): `ktor-websockets` 3.6.1. For the S1-SECNET tests: `okhttp-mockwebserver3` and `okhttp-tls` 5.5.1. Optional: `kotest-property` 6.2.6.
3. **`MODULE_OWNERS`:** add the row `:core:net	S	backend-owner	slice-1`. The `:tools:*` rows are also missing on the base branch, so `ModuleOwnersTest` fails without this unit too.
4. **For S0-CORE:**
   - freeze the rich stream status (`StreamState` here) in `:core:model`;
   - freeze `Failure.Net` (or confirm that `DispatchUncertain` is the delivery-unknown value);
   - add `TransportCredentials` (audit BLOCKER 3, third item).
5. **Konsist (`:quality`):** add `/src/jvmShared/` to the `production` slice so K3, K5 and K16 also scan the shared JVM code.

## 7. Open problems

1. **The WebSocket path (P-N1) is not built.** The roadmap row mentions it, the brief did not, and it depends on S0-NETP
   (the P-N1 proof) and on `:core:net-okhttp`, neither of which is on this branch. The common `WsSpec`, `WsSession` and
   `WsRpc` contract still needs the audit BLOCKER 3 fix: a primitive-only WebSocket boundary in `:core:net-okhttp` and
   an adapter here.
2. **Trust is not enforced by this module.** It has no cleartext consent check, no pin trust manager and no trust gate
   interceptor; those belong to S1-SECNET's `HardenedOkHttp`. Until S1-SECNET passes `base = HardenedOkHttp::build`,
   `hardenedBaseClient` is **not safe for remote servers**.
3. **HTTP/2 `421` on a coalesced connection is not reproduced on the wire.** It needs TLS and HTTP/2. It is covered
   by the one-shot body, which OkHttp checks first on that path, and by the forced second-proceed test, which stands for
   any follow-up. An okhttp-tls HTTP/2 test belongs with S1-SECNET's mockwebserver3 suite.
4. **Measured in a cloud container, not on a phone.** The `sse.parse` budget (PB-18, ≤ 0.2 ms per 4 KB frame) and the
   `SseParserBench` (plan 13 §3.9) were not run.
5. **`retryNow()` while a stream is live drops it and reconnects.** That is the right behaviour for a network change
   and a password update, but a manual "Try again" while connected does the same. `:engine:connection` should call it
   only when the link is not Connected.
6. **Live-server checks were not run** (P-N3, P-N5 with live OpenCode 2.1.1). This container has no server, and AGENTS
   forbids probing the owner's servers.
