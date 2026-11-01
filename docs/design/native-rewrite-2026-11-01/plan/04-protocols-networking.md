# 04. Protocols and networking

Written 2026-11-01. Builds on `01-architecture-decision.md` (the ADR, binding) and uses the names of
`03-architecture-modules.md` (the `native/` root, `GatewayFactory` + `BackendKind`, `ConnectionGraph`, the version
catalog). Where this file and the ADR disagree, the ADR wins and this file is corrected.

## For the owner, in five lines

1. The new app talks to OpenCode 2, to Paseo (Claude Code and the other phone agents, including the ACP ones) and to Codex with small Kotlin clients we write ourselves; OpenCode 1 stays retired and is only recognised so the app can say "update this server".
2. Every hard-won rule of the Flutter app comes across: a prompt is never sent twice, plain HTTP needs your yes for each address (this phone and Tailscale addresses are checked by the app itself), a pinned certificate is checked before any byte leaves, and errors show plain words.
3. Live updates use one connection per server that retries after 1, 2, 4 and 8 seconds and then every 16, waits quietly while the phone has no network, and re-reads what it missed instead of guessing.
4. Before the clients are written, the Flutter app's recorded server answers and its 127 protocol test rows become native tests first; rows we want to drop (the unused direct ACP client, OpenCode 1 checks) are listed for your OK.
5. Your decisions are in §19; each has a recommended default, and work starts on the defaults.

Tags: **[V]** checked today against a primary source (library sources jars, Maven metadata, the OpenCode 2 binaries on
the PC, `npm view`). **[R]** read in the Flutter repo, a map, a research file, the ADR or memory. **[U]** unverified; the
named proof in §17 settles it. Behaviour ids such as `B-PROTO-033` are rows of `../catalog/BEHAVIORS.md`.

---

## 0. How to read this section

| Reader | Read | Then |
|---|---|---|
| Codex (builds `:core:net`, `:core:net-okhttp`, `:protocol:*`, `:engine:connection`, `:engine:delivery`) | all | start with the work package (§16) named in your brief; write its catalog tests first (§15) |
| Test-writer lane (Sonnet) | §14, §15, §16 | turn the rows of the next slice into failing `commonTest`s, using the fixtures in §14 |
| Kit and feature builders (Claude) | §3.8 (failure categories), §11 (capabilities, gating), §12.4 (status words) | never import a protocol; read `ConnectionGraph` state only |
| Reviewer | §1.3 (decisions), §20 (done checklist) | reviewers never launch test processes (ADR §10.3) |

Ids used here: **N4-Dx** = a decision made in this section; **N4-Ox** = an owner decision (§19); **WP-04.x** = a work
package (§16); **P-Nx** = a proof before fan-out (§17); **B-PROTO-128+** = new catalog rows this section adds (§15.5).

---

## 1. Scope, corrections and decisions

### 1.1 In scope

| Topic | Native home | Map features |
|---|---|---|
| HTTP client, trust, timeouts, errors, tracing, downloads | `:core:net`, `:core:net-okhttp`, `:core:security` | PROTOCOLS-01, 02, 03, 07, 36, 45 |
| SSE engine (parser, reconnect, backoff, jitter, network gating) | `:core:net` | PROTOCOLS-17, 21 |
| WebSockets (Paseo, Codex, OpenCode PTY) | `:core:net`, `:core:net-okhttp` | PROTOCOLS-28, AGENTS-BACKENDS-19, 35 |
| OpenCode 2 client | `:protocol:oc2` | PROTOCOLS-04 to 35, 46 |
| Paseo client (ACP agents ride on it) | `:protocol:paseo` | AGENTS-BACKENDS-19 to 31, 38 |
| Codex app-server client | `:protocol:codex` | AGENTS-BACKENDS-35 to 37 |
| Gateway contracts, `ServerCapabilities`, neutral event vocabulary | `:core:domain` | PROTOCOLS-37, 38, 39 |
| Connect, reconnect, reconcile-by-refetch, quiet status, busy polling | `:engine:connection` | PROTOCOLS-20, STATE-DATA-10 to 14 |
| Offline queue, dispatch markers, command receipts | `:engine:delivery` | PROTOCOLS-35, STATE-DATA-18 to 20 |
| Contract tests, recorded fixtures, live proofs | `:core:testing`, `native/fixtures/`, `:tools:contracts` | PROTOCOLS-44, 46 |

Not here: the transcript reducer (chat section; it consumes `GatewayEvent`, §11.2), the Flutter data migration (migration
section; this file only lists the protocol keys it must carry, §13.2), notification and widget surfaces (Android
surfaces section; this file gives them a transport, §12.7), the built-in Linux runtime (it starts the phone server; this
file connects to it like any other server).

### 1.2 Corrections to the brief

1. **There is no OpenCode 1 client to write.** Commit `40ba4fa32` (2026-10-15) deleted `lib/api/`, the generated SDK
   and its generator [R protocols §0.1]. The ADR already says `:protocol:oc2` holds only *recognition* of a legacy
   server (ADR §5.2). The native app recognises an OpenCode 1 answer and says how to upgrade (B-PROTO-094/095). The
   "generated from `contracts/`" idea applied to OpenCode 1 only; `contracts/README.md` in the Flutter repo is stale.
2. **OpenCode 2 is hand-written, not generated** (ADR §3.3). The 2.x spec types `/api/event` as an opaque string and has
   390 `anyOf`, 0 discriminators [V network-data §5]. The spec is used only for drift tests (§14.5).
3. **ACP is not a separate client.** The app never speaks ACP to an agent: Gemini, Qwen, Kilo, Cline, Goose, Oh My Pi
   and fx run as generic ACP providers inside the Paseo daemon (AGENTS-BACKENDS-38). The Dart `lib/acp/` client is a
   spike with no caller (AGENTS-BACKENDS-39). Its 11 catalog rows are listed for retirement (N4-O1).
4. **A2A is dropped** (ADR D6 default). **Gas City, the phone-engine `/v1` client and FactsBook come after the switch**
   (ADR D6). Their 23 PROTO rows are deferred, not dropped (§15.4); `:core:net` is built so they plug in (§6.2).

### 1.3 Decisions made in this section

| # | Decision | Why | Changes from Flutter? |
|---|---|---|---|
| N4-D1 | One `OkHttpClient` (own pool and dispatcher) per live connection, built by `:core:net-okhttp`; Ktor 3.6.1 on top with no ContentNegotiation. | Trust isolation per server; deterministic close. | No |
| N4-D2 | `retryOnConnectionFailure(false)`, no redirects, `Proxy.NO_PROXY` — **re-asserted inside Ktor's `engine { config {} }`** because Ktor applies its own `retryOnConnectionFailure(true)` on top of a preconfigured client [V `OkHttpConfig.kt:17-22`, `OkHttpEngine.kt:159-163`]. | A POST must never be resent by the stack. | No (same rule, new trap) |
| N4-D3 | Certificate pin = SHA-256 of the **leaf DER**, checked in an `X509ExtendedTrustManager` during the handshake; a mismatch latches the client shut. | Same identity override as Flutter; `CertificatePinner` pins SPKI after PKI. | No |
| N4-D4 | One address classifier (`AddressClass`: Loopback, Private, Tailnet, Public) in `:core:security`, with its own IP-literal parser in common code. Tailnet hosts get the private-network consent wording. | Flutter had three loopback checks and two tailnet checks that disagreed [R]. | Wording unified (matches the profile editor) |
| N4-D5 | WebSockets: OkHttp 5 HTTP/1.1 upgrade (`Response.socket`) + Ktor's public `RawWebSocket` with a message cap; no compression. | OkHttp's WebSocket always offers `permessage-deflate` and has **no inbound size cap**; Ktor's OkHttp WebSocket session cannot set `maxFrameSize` [V]. | No (keeps Flutter's caps and no-extension rule) |
| N4-D6 | SSE: our own byte parser and reconnect loop (ADR §3.3). OpenCode 2 backoff is exactly Flutter's: 1, 2, 4, 8, 16 s cap, no jitter, reset after 30 s stable, label "disconnected" after 6 failures while still retrying. Jitter exists in the same loop for Gas City later. | Parity of timing and tests. | No |
| N4-D7 | SSE read stall: 45 s without a byte (3 × the 15 s heartbeat) counts as a drop. | Flutter had no read timeout, so a half-open socket hung until the network callback [R]. The 15 s heartbeat is in the 2.0.10 and 2.0.28 binaries [V]. | **Yes** (new row B-PROTO-132) |
| N4-D8 | Network gating: with no default network, a non-loopback stream waits without spending backoff attempts; a new default network evicts pooled sockets of every live connection and retries at once with the backoff reset; loopback (the phone's own server) is never gated. | Flutter's network reading was "advice only" [R]; this keeps that for loopback and stops useless retries elsewhere. | **Yes** (rows B-PROTO-133, 138) |
| N4-D9 | One physical `/api/event` stream per OpenCode 2 connection feeds both the project-filtered channel and the global channel. | Flutter opened two identical streams per connection [V `gateway.dart:804-828`]. Halves sockets and battery. | **Yes** (row B-PROTO-134) |
| N4-D10 | Reconnect reconciles by refetch, never by replay (ADR §9.5). The durable session-log client is ported and tested but unused, as in Flutter. | Binding ADR rule. | No |
| N4-D11 | Protocol adapters emit a neutral `GatewayEvent` vocabulary (§11.2), not Flutter's v1-shaped names. | The transcript engine is new; v1 names only existed for old consumers [R protocols §8.10]. | Internal only |
| N4-D12 | `ServerCapabilities` stays the 85-flag static set per backend at the switch, plus runtime latches; a version-rule table exists and is empty at the switch. | Parity first; version rules are post-switch features. | No |
| N4-D13 | Command-receipt admission accepts the **real** inbox item type `"user"`. Flutter checks `"prompt"`, which no server sends [V 2.0.28 schema: `user\|synthetic\|compaction\|move`; fixture `prompt_receipt.json`]. | Latent Flutter bug: receipts never confirm on real servers. | **Yes** (row B-PROTO-135; N4-O5) |
| N4-D14 | The only automatic HTTP retry is `GET /agent/{id}` on 404, twice, 500 ms apart (server boot). Ktor `HttpRequestRetry` is banned (Konsist). | Matches Flutter exactly [V `gateway.dart:442-454`]. | No |
| N4-D15 | The offline queue keeps its Flutter storage and format at the switch (`flutter.oc.offlineQueue` in the adopted prefs file); Room only at switch+1 or by the ADR §9.8 trigger. | ADR D9; markers must survive byte for byte. | No |
| N4-D16 | OpenCode 2 pin at the switch stays **2.1.1** (owner may change, N4-O3). 2.2.5 (published today [V npm]) is probed in P-N4 and recorded, not adopted. | Phone server, scripts and BYO bundle all pin 2.1.1 [R]. | No |
| N4-D17 | `WireJson` has `exceptionsWithDebugInfo = false` (kotlinx 1.12 defaults to `true`, which puts input JSON, possibly API keys, into exception text [V]). | Credentials invariant. | n/a |

---

## 2. Where the code lives

All paths are under `native/` (section 03, choice S1). Packages are `io.github.eslamasabry.oc.<layer>.<module>`.

### 2.1 Modules this section fills

| Module | Plugin | Owner | Starts | This section puts here |
|---|---|---|---|---|
| `:core:security` | `oc.kmp.library` | S core | 0 | address classifier, URL rules per backend, trust origin, pin value type, consent model |
| `:core:common` | `oc.kmp.library` | S core | 0 | `WireJson`, tolerant serializers, `Failure.Net` subtypes (§3.8) |
| `:core:domain` | `oc.kmp.library` | S core | 0 | gateway interfaces, `GatewayEvent`, `ServerCapabilities`, `GatewayFactory`, pairing order rules, `ProbeResult` |
| `:core:net` | `oc.kmp.library` (c, a, d, t) | S backend | 1 | Ktor client factory, request helpers, status→`Failure`, SSE parser, `EventStreamLoop`, `WsSession`, `NetworkStatus`, `HttpTrace`, downloader |
| `:core:net-okhttp` | `oc.jvm.library` | S backend | 1 | hardened OkHttp builder, Ktor engine factory, pin trust manager, trust gate interceptor, certificate inspector, WebSocket upgrade, JVM error classifier. External deps only (OkHttp 5.5.1, `ktor-client-okhttp`, `ktor-websockets`), so ADR §6.1 "no project dependencies" holds |
| `:protocol:oc2` | `oc.kmp.library` | F backend (Codex) | 1 | OpenCode 2 client, dialect, routes, DTOs, event registry, adapter, gateways, probe, pairing codec, PTY |
| `:protocol:paseo` | `oc.kmp.library` | F backend (Codex) | 3 | Paseo transport, gateway, mappers, terminal frames |
| `:protocol:codex` | `oc.kmp.library` | F backend (Codex) | 5 | Codex app-server JSON-RPC client and gateway |
| `:engine:connection` | `oc.kmp.library` | S backend | 1 | `ConnectionRegistry`, `ConnectionGraph`, connect sequence, reconcile, status model, busy polling, probe orchestration, pairing dialer |
| `:engine:delivery` | `oc.kmp.library` | F | 2 | send pipeline, offline queue, online marker, receipt journal |
| `:core:testing` | test-only KMP | S core | 0 | `FixtureEngine`, `SseReplay`, `FakeNetworkStatus`, `TestClock`, fixture root constant |
| `:tools:contracts` | `oc.jvm.library` (CLI) | S backend | 1 | recording proxy, fixture redaction check, OpenAPI drift report (**proposed addition** to ADR §5.2 and 03 §1.2; coordinator approves) |

`:android:platform` implements `NetworkStatus` (§6.4). `:androidApp` keeps `res/xml/network_security_config.xml` (§4.7).

### 2.2 File plan (create in this order inside each module)

```
core/security/src/commonMain/kotlin/io/github/eslamasabry/oc/core/security/
  address/IpLiteral.kt            IPv4/IPv6 literal parser (brackets, zone, trailing dot, IPv4-mapped)
  address/AddressClass.kt         enum + classifyHost(host): AddressClass
  address/ServerUrlRules.kt       normalize/validate for OpenCode, Paseo, Codex, Gas City; UrlRejection enum
  address/ConnectionAdvice.kt     explainConnectionAddress(input): ConnectionAdvice
  trust/TrustOrigin.kt            origin(scheme://host:port), effective port, IPv6 brackets, lower-case
  trust/CertificatePin.kt         value class Sha256Pin(hex64); parse accepts colons; display "AA:BB:…"
  trust/TransportPolicy.kt        decide(backend, url, consentOrigin, pin): Outcome<TransportPermit>
core/common/src/commonMain/kotlin/io/github/eslamasabry/oc/core/common/
  json/WireJson.kt  json/TolerantListSerializer.kt  json/LenientDoubleSerializer.kt  json/OpenEnum.kt
  failure/NetFailure.kt           (the Failure.Net subtypes, §3.8)
core/net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/
  TransportSpec.kt  EngineFactory.kt  HttpClientFactory.kt  Requests.kt  StatusMapping.kt  ErrorBody.kt
  sse/SseFrame.kt  sse/SseParser.kt
  stream/BackoffPolicy.kt  stream/ResumeMode.kt  stream/StreamSignal.kt  stream/EventStreamLoop.kt
  ws/WsSpec.kt  ws/WebSocketConnector.kt  ws/WsSession.kt  ws/WsRpc.kt
  gate/NetworkStatus.kt  gate/NetworkGate.kt
  trace/HttpTrace.kt  download/ResumableDownloader.kt
core/net/src/androidMain/.../OkHttpBindings.kt     (@ContributesBinding: EngineFactory, WebSocketConnector, CertificateInspection)
core/net/src/desktopMain/.../OkHttpBindings.kt     (same three bindings; identical bodies, 20 lines)
core/net-okhttp/src/main/kotlin/io/github/eslamasabry/oc/core/net/okhttp/
  JvmTransportSpec.kt  HardenedOkHttp.kt  PinnedLeafTrustManager.kt  TrustGateInterceptor.kt
  CertificateInspector.kt  OkHttpWebSockets.kt  JvmErrorKind.kt
protocol/oc2/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/oc2/
  Oc2Wire.kt (constants)  Oc2Routes.kt  Oc2Dialect.kt  Oc2Transport.kt  Oc2Client.kt
  wire/  (Session, Message union, Content, Tool, Permission, Form, Inbox, Catalog, Fs, Vcs, Pty, Shell, Mcp,
          Integration, Plugin, WebSearch, Stats, Info, Errors — one file each)
  events/Oc2Envelope.kt  events/Oc2Event.kt  events/Oc2EventRegistry.kt
  adapter/Oc2EventAdapter.kt  adapter/OpenCallTracker.kt  adapter/PartIds.kt
  mappers/  (session, message, catalog, permission, form, vcs, fs — pure)
  policy/SessionPermissionPolicy.kt
  probe/Oc2Probe.kt  pairing/Oc2PairingCodec.kt
  gateway/Oc2Gateway.kt  gateway/Oc2OperationsGateway.kt  gateway/ops/*.kt  gateway/Oc2GatewayFactory.kt
  pty/Oc2PtyChannel.kt  shell/Oc2ShellRunner.kt  history/BoundedHistoryReader.kt
  Oc2Capabilities.kt
protocol/paseo/…  PaseoTransport.kt PaseoHello.kt PaseoFailure.kt PaseoTestedVersions.kt PaseoGateway.kt
                  gateway/*.kt mappers/*.kt PaseoTerminalFrames.kt PaseoCapabilities.kt PaseoGatewayFactory.kt
protocol/codex/…  CodexTransport.kt CodexGateway.kt CodexAccountSession.kt CodexCapabilities.kt CodexGatewayFactory.kt
```

### 2.3 Library entries this section needs (proposed additions to 03 §5; build-logic owner edits)

| Alias | Coordinate | Version | Where | Status |
|---|---|---|---|---|
| (exists) `ktor-client-core`, `ktor-client-okhttp`, `ktor-client-mock`, `okhttp`, `okhttp-mockwebserver` | | 3.6.1 / 5.5.1 | as 03 | in 03 |
| `ktor-websockets` | `io.ktor:ktor-websockets` | 3.6.1 | `:core:net`, `:core:net-okhttp` (`RawWebSocket`) | add [V sources jar] |
| `okhttp-tls` | `com.squareup.okhttp3:okhttp-tls` | 5.5.1 | `:core:net-okhttp` tests (self-signed leaves) | add [V Maven] |
| `kotest-property` | `io.kotest:kotest-property` | 6.2.6 | `:core:net` tests (SSE chunk-boundary fuzz) | add [V Maven] |
| `ktor-server-cio` | `io.ktor:ktor-server-cio` | 3.6.1 | `:tools:contracts` (recorder, replay server) | add [V Maven] |
| `json-schema-validator` | `com.networknt:json-schema-validator` | 3.0.8 | `:tools:contracts` drift job (T2, JVM) | add [V testing-perf §1] |

`ktor-client-websockets` (in 03) is not needed by this design on Android or desktop; keep it only if iOS later uses the
Darwin WebSocket path.

---

## 3. The HTTP transport: `:core:net` and `:core:net-okhttp`

### 3.1 Lifetime

- A `ConnectionGraph` (one per live connection, ADR §9.1) owns exactly one `OkHttpClient` and one Ktor `HttpClient`.
  Several graphs can be alive at once (primary server, side servers, the agents backend, monitors).
- `ConnectionGraph.close()` order: stop admission → cancel the event stream job → drain writer lanes → `HttpClient.close()`
  → `HardenedOkHttp.shutdown(client)` (evict pool, shut down the dispatcher's executor).
- Short-lived graphs (schedules, widget actions, probes, monitors) use the same builder and the same trust (§12.7).

### 3.2 The hardened OkHttp builder (`:core:net-okhttp`, JVM, no project dependencies)

```kotlin
package io.github.eslamasabry.oc.core.net.okhttp

/** Plain values only: this module may not see :core:* types (ADR §6.1). */
public class JvmTransportSpec(
    public val origin: String,            // "https://host:443", from TrustOrigin (lower-case, IPv6 bracketed, explicit port)
    public val cleartextPermitted: Boolean, // decided by :core:security TransportPolicy; false ⇒ any http:// call is refused
    public val pinnedLeafSha256: ByteArray?, // 32 bytes or null
    public val connectTimeoutMs: Long = 8_000,
    public val readTimeoutMs: Long = 120_000,
)

public object HardenedOkHttp {
    public fun build(spec: JvmTransportSpec): OkHttpClient {
        val pin = spec.pinnedLeafSha256?.let { PinnedLeafTrustManager(it) }
        val builder = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 15, TimeUnit.SECONDS))     // Flutter: 15 s idle keep-alive [R]
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 10 })
            .proxy(Proxy.NO_PROXY)                                         // Flutter: findProxy = DIRECT
            .retryOnConnectionFailure(false)                               // never resend a POST
            .followRedirects(false).followSslRedirects(false)              // Basic auth never crosses origins
            .connectTimeout(spec.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(spec.readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(spec.readTimeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor(TrustGateInterceptor(spec, pin))               // application interceptor: runs before any socket
        if (pin != null) {
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(pin), null) }
            builder.sslSocketFactory(tls.socketFactory, pin).hostnameVerifier(pin.hostnameVerifier())
        }
        return builder.build()
    }

    /** Ktor engine over [client]. Ktor runs its own default block (retry ON) after `preconfigured`; re-assert ours. */
    public fun ktorEngine(client: OkHttpClient): HttpClientEngine = OkHttp.create {
        preconfigured = client
        config {
            retryOnConnectionFailure(false)
            followRedirects(false)
            followSslRedirects(false)
        }
    }

    public fun shutdown(client: OkHttpClient) {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}
```

Why the `config {}` block is mandatory [V, Ktor 3.6.1 sources]: `OkHttpEngine.createOkHttpClient` does
`(preconfigured ?: prototype).newBuilder()` and then `builder.apply(config.config)`, and the default `config` sets
`retryOnConnectionFailure(true)`. `OkHttpConfig.config(block)` chains `oldConfig(); block()`, so our block wins. A
mockwebserver3 test proves it (B-PROTO-128).

### 3.3 The Ktor client (`:core:net` commonMain)

```kotlin
public fun interface EngineFactory { public fun create(spec: TransportSpec): HttpClientEngine }   // androidMain/desktopMain bind it

public class HttpClientFactory(private val engines: EngineFactory, private val trace: HttpTrace) {
    public fun create(spec: TransportSpec): HttpClient = HttpClient(engines.create(spec)) {
        expectSuccess = false              // status codes become Failure values in StatusMapping
        followRedirects = false            // Ktor's HttpRedirect is ON by default [V network-data §0]
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 120_000 // Flutter receive timeout 2 min [R]
            socketTimeoutMillis = 120_000
        }
        install(UserAgent) { agent = "OpenCodeMobile" }   // no version, no device facts
        install(trace.plugin)              // OCTRACE-style spans ≥ 50 ms; path template only, never query or body
        // No ContentNegotiation, no HttpRequestRetry, no Logging plugin (Konsist rule, §11.7).
    }
}
```

`TransportSpec` (common) carries the `TransportPermit` from `:core:security` (origin, `cleartextPermitted`, pin) plus
the backend kind. The androidMain/desktopMain `EngineFactory` converts it to `JvmTransportSpec` and calls
`HardenedOkHttp.ktorEngine(HardenedOkHttp.build(...))`.

### 3.4 Timeouts

| Call | Connect | Request | Socket read | Source |
|---|---|---|---|---|
| OpenCode 2 REST (default) | 8 s | 2 min | 2 min | Flutter Dio config [R PROTOCOLS-07] |
| `POST /session/{id}/wait` (long poll) | 8 s | 10 min | 10 min | [R] |
| `GET /api/event`, session log (SSE) | 8 s | infinite | **45 s** (3 × heartbeat) | N4-D7 |
| Web search providers / query | 8 s | 15 s / 30 s | same | [R PROTOCOLS-33] |
| Gen-UI history read | 8 s | 10 s deadline, 1 MiB budget | | [R PROTOCOLS-10] |
| Handoff history page | 8 s | 30 s per page; 16 MiB/page; 64 MiB total | | [R] |
| Managed shell list | 8 s | 8 s | | [R PROTOCOLS-29] |
| Turn-run check (`checkTurnStillRuns`) | 8 s | 8 s | | [R STATE-DATA-12] |
| Paseo handshake / default request / create agent / browser launch | 8 s | 20 s / 90 s / 60 s | WS ping 20 s | [V `paseo/transport.dart`] |
| Paseo bounded history (second connection) | 8 s | 10 s, 1 MiB frames | | [V] |
| Codex handshake / request | 8 s | 15 s | WS ping 20 s | [V `codex/transport.dart`] |
| Certificate inspection | 8 s | handshake only | 8 s | [R] |

Per-call overrides use Ktor's `timeout { }` block on the request builder (`HttpTimeoutConfig.INFINITE_TIMEOUT_MS` for
SSE [V class name in 3.6.1 sources]).

### 3.5 Retries

| Case | Automatic retry? | Rule |
|---|---|---|
| Any POST/PUT/PATCH/DELETE | **Never** by the stack | N4-D2. Lost response → `DeliveryUnknown`; receipts look it up (§13.6). |
| `GET /agent/{id}` → 404 | Twice, 500 ms apart | Server boot after an idle hour (`800f93a4c`) [V] |
| 503 `service_starting` | No | `Failure.Net.ServerStarting`; the person sees "The server is starting" with Try again; the stream loop retries on its own schedule. |
| Event stream drop | Yes, by `EventStreamLoop` | §6.2 |
| Paseo/Codex mutation timeout | **Never** | `DeliveryUnknown` [R AGENTS-BACKENDS-19] |
| Paseo/Codex read timeout | No (caller may re-read) | `Disconnected` |

### 3.6 Building requests

- **Root:** `normalizeServerRoot(url)` trims trailing `/` and a trailing `/api`; every OpenCode 2 path is
  `<root>/api<path>` (B-PROTO-037).
- **Auth:** `Authorization: Basic base64(user:password)`, user `opencode` unless the profile carries a tunnel username;
  header only. A URL with userinfo is refused by the trust gate before any socket opens (B-SEC-153). The old
  `?auth_token=` helper stays deleted (`932210227`).
- **Path segments:** ids and file paths are encoded per segment (`/fs/read/<a>/<b>` keeps `/` between segments, encodes
  inside them). Use Ktor `URLBuilder.appendPathSegments(segments)`; a table test pins `#`, `%`, space, `?`, Arabic and
  `..` cases [U exact API name; compile-checked in WP-04.4].
- **Query:** null values dropped; empty map dropped. Location scoping adds `location[directory]=<abs>` and, when set,
  `location[workspace]=<id>` on the calls listed in PROTOCOLS-08; `/session/{id}…`, `/session/stats`, `/session/import`,
  `/permission/saved`, `/project`, `/worktree/{id}` never carry a location. `Oc2Client` captures `(directory, workspace)`
  when an operation starts and re-checks it after every suspension point (PROTOCOLS-08).
- **SSE requests:** `Accept: text/event-stream`, Basic header, no request timeout, 45 s socket timeout.

### 3.7 Reading responses

- Bodies are read as bytes, decoded with `WireJson` on `AppDispatchers.default` (never the main thread; big pages are
  ≥ 50 KiB today [R PROTOCOLS-07]).
- `WireJson` (in `:core:common`):

```kotlin
public val WireJson: Json = Json {
    ignoreUnknownKeys = true          // servers add fields weekly
    explicitNulls = false
    coerceInputValues = true          // null / unknown enum → default
    isLenient = false
    exceptionsWithDebugInfo = false   // N4-D17: never echo input (credentials) in exception text
}
```

- Tolerant helpers in `:core:common`: `TolerantListSerializer<T>` (drops bad items one by one, like Dart `_mapList`),
  `LenientDoubleSerializer` (accepts `"Infinity"`, `"-Infinity"`, `"NaN"`; 30 `anyOf[enum, number]` sites [V]),
  open enums as `@JvmInline value class X(val raw: String)` with known constants (phase, finish, interrupt reason,
  delivery, status). Free-form fields (`metadata`, tool `input`, gen-UI payloads) stay `JsonElement`.
- **Credential ingress:** `/provider`, `/config` and `/config/providers`-style bodies are parsed to a `JsonElement` and
  passed to `Redactor.registerCredentialValues(tree)` **before** DTO decoding, tracing or diagnostics capture
  (B-SEC-055/056, B-PROTO-104). The transport registers the serve password with `Redactor.registerKnownSecret` in its
  constructor, before its first request.
- **Size caps:** JSON bodies are capped by call (history 1 MiB / 16 MiB as in §3.4; everything else 32 MiB); a body over
  its cap is closed unread and becomes `Failure.Net.UnreadableReply`.

### 3.8 Errors: transport and status → `Failure`

Translated once, in `:core:net` (status) and `:core:net-okhttp` (JVM exception kinds), so nothing above sees an
exception for an expected failure (ADR §9.6). Server text (`message`, HTML bodies, provider errors) goes only into
`details: Redacted?`; the symbolic server tag (`_tag` or `code`) is kept for logic as `WireTag` and never shown.

```kotlin
// :core:common — proposed for the slice-0 freeze of Failure categories (ADR §10.3); section 03 owns Failure itself.
public sealed interface Net : Failure {
    public data class Offline(override val details: Redacted? = null) : Net
    public data class Unreachable(val why: Why, override val details: Redacted?) : Net {
        public enum class Why { Dns, Refused, Timeout, Reset, NoRoute }
    }
    public data class TrustRefused(val why: Why, override val details: Redacted?) : Net {
        public enum class Why { CleartextNotConfirmed, OriginChanged, CredentialsInUrl, PinNeedsHttps,
                                CertificateChanged, CertificateExpired, CertificateUntrusted }
    }
    public data class SignInNeeded(val why: Why, override val details: Redacted?) : Net {
        public enum class Why { PasswordMissing, PasswordRejected, TokenMissing, TokenRejected, HostNotAllowed }
    }
    public data class ServerBusy(val kind: Kind, val tag: WireTag?, override val details: Redacted?) : Net {
        public enum class Kind { Starting, Stopping, Failed, Overloaded, RateLimited }
    }
    public data class NotFound(val tag: WireTag?, val requestId: String?, override val details: Redacted?) : Net
    public data class Conflict(val tag: WireTag?, val requestId: String?, override val details: Redacted?) : Net
    public data class Rejected(val status: Int, val tag: WireTag?, val extras: WireExtras, override val details: Redacted?) : Net
    public data class ServerFailed(val status: Int, val tag: WireTag?, override val details: Redacted?) : Net
    public data class UnreadableReply(override val details: Redacted?) : Net
    public data class DeliveryUnknown(override val details: Redacted?) : Net   // a mutation may or may not have landed
    public data class StaleRequest(override val details: Redacted? = null) : Net // connection epoch changed
    public data class NotSupported(val feature: Feature, override val details: Redacted? = null) : Net
    public data class Incompatible(val kind: Kind, override val details: Redacted?) : Net {
        public enum class Kind { LegacyOpenCode1, UnsupportedServerVersion, ProtocolMismatch }
    }
}
```

| Input | `Failure.Net` | Flutter tag / category it replaces |
|---|---|---|
| no default network, non-loopback host | `Offline` | network |
| `UnknownHostException` | `Unreachable(Dns)` | "failed host lookup" string checks [R] |
| `ConnectException` refused | `Unreachable(Refused)` | `suggestsMissingServer` |
| connect / read timeout, 408 | `Unreachable(Timeout)` | timedOut |
| reset, EOF mid-call | `Unreachable(Reset)` | network |
| pin mismatch | `TrustRefused(CertificateChanged)` | `ClientCertificateChanged` |
| matching pin, outside validity | `TrustRefused(CertificateExpired)` | (distinct error, B-SEC-007) |
| PKI failure, no pin | `TrustRefused(CertificateUntrusted)` | `ClientCertificateInvalid` |
| http, non-loopback, no consent | `TrustRefused(CleartextNotConfirmed)` | `ClientCleartextNotAllowed` |
| origin differs from the profile | `TrustRefused(OriginChanged)` | `ClientOriginChanged` |
| 401, or tag `UnauthorizedError` (even empty body) | `SignInNeeded(PasswordMissing or PasswordRejected)` | `Api2AuthRequired` |
| 403 (OpenCode) | `SignInNeeded(PasswordRejected)` | signIn |
| 403 on Paseo upgrade without password | `SignInNeeded(HostNotAllowed)` | host not in daemon allowlist [R] |
| 503 or tags `ServiceUnavailableError`, `service_starting`, `service_stopping`, `service_failed` | `ServerBusy(Starting/Stopping/Failed)` | `Api2Unavailable` |
| 429 | `ServerBusy(RateLimited)` | busy |
| 404, 410 | `NotFound` | notFound |
| 409 | `Conflict` | conflict |
| other 4xx | `Rejected` | rejected |
| ≥ 500 | `ServerFailed` | server |
| undecodable / over-cap body | `UnreadableReply` | unreadableReply |
| WS mutation timeout or link lost after send | `DeliveryUnknown` | deliveryUnknown |
| answer on an old epoch | `StaleRequest` | staleRequest |
| 404 non-session / 405 / 501 on an optional feature | `NotSupported` + latch (§11.5) | per-feature latches |
| OpenCode 1 health shape | `Incompatible(LegacyOpenCode1)` | `unsupportedLegacy` |
| Paseo close 4003 / `hello.rejected` | `Incompatible(ProtocolMismatch)` | incompatibleProtocol |
| cancellation | **not a Failure**: rethrown | — |

`:ui:words` maps every subtype with an exhaustive `when` (ADR §9.6). The NAV rows B-NAV-007/008/009 (loopback offers
Retry, Tailscale address says "check Tailscale", after three tries say retrying will not start a server) are word rules
over these values plus the address class; they are tested in `:engine:connection` (`ConnectionFailureExplainTest`).

### 3.9 Tracing

`HttpTrace` (`:core:net`) records a span per request: `http <backend> <METHOD> <path template>` with attributes
`status`, `ms`, `bytes`; logged through `AppLog` when ≥ 50 ms (Flutter `OCTRACE`, PROTOCOLS-45). Marks:
`events.connected`, `events.first`, `events.first.session_log`, `app.first_connected`, `messages.page`. The template
replaces ids with `{id}`; query strings, bodies and headers are never recorded (PTY tickets ride in the query).

### 3.10 Resumable pinned downloads

`ResumableDownloader` (`:core:net`) serves the updater, voice packs and runtime payloads: HTTPS only; `Range` resume
(206 appends, 200 restarts from zero, 416 means complete); SHA-256 of the whole file checked before the atomic rename;
mismatch deletes the part file; no credentials ever. Owners of those features own their pins; this file owns the
mechanism and its MockEngine tests (testing-perf §10).

---

## 4. Trust and address policy (localhost, Tailscale, cleartext, pins)

### 4.1 One classifier

```kotlin
public enum class AddressClass { Loopback, Private, Tailnet, Public }

/** Brackets, IPv6 zone and a trailing dot are ignored; IPv4-mapped IPv6 is judged by its IPv4. */
public fun classifyHost(host: String): AddressClass
```

| Class | Exact rule (ported from Dart; vectors from `private_network_cleartext_test.dart`, `tailscale_profile_test.dart`) |
|---|---|
| Loopback | exactly `localhost`, `127.0.0.1`, `::1` (case-insensitive). One predicate on purpose [R `loopback_host.dart`]. |
| Tailnet | IPv4 in `100.64.0.0/10` (`b0 == 100 && (b1 and 0xC0) == 0x40`), or a name ending `.ts.net` |
| Private | IPv4 10/8, 172.16/12, 192.168/16, 169.254/16; IPv6 `fc00::/7`, `fe80::/10`; names `*.local` with a non-empty label (not bare `local`) |
| Public | everything else, including `0.0.0.0` and every other name |

`IpLiteral.parse` is written in common code (no `java.net`): dotted IPv4 (4 decimal parts, 0–255, no leading `+`),
IPv6 with `::` compression, embedded IPv4 tail and zone suffix. Table tests cover each vector of the two Dart files plus
`100.63.255.255` (Public), `100.128.0.0` (Public), `100.64.0.0` and `100.127.255.255` (Tailnet).

### 4.2 Policy per backend

| Backend / client | Allowed schemes | Allowed hosts | Plain-text needs consent? | Rows |
|---|---|---|---|---|
| OpenCode 2 profile | `https`, `http` | any | `http` to anything but Loopback needs per-origin consent; wording: Private and Tailnet use the private-network sentence, Public the stronger one (N4-D4) | B-SEC-119..125, 153, 154 |
| Paseo profile | `ws`, `wss` | **Loopback or Tailnet only**, even over `wss` (owner rule: nothing on the open internet) | no consent step; `ws` allowed because the host class is restricted | B-SEC-113, 123 |
| Codex profile | `ws` (Loopback only), `wss` otherwise | any for `wss` | `ws` to non-loopback is refused (not consentable) | B-SEC-123, B-AGENT-062 |
| OpenCode PTY WebSocket | `ws`/`wss` follows the profile's `http`/`https` | the profile origin | inherits the profile's consent | B-PROTO-036 |
| Gas City (later) | `https` any; `http` | Loopback or Tailnet for `http` | none | `isOrchestrationUrlAllowed` |
| Quota reader | in-app server: `http`; remote server: its profile scheme | in-app: `127.0.0.1:4195` with Bearer only; remote: the profile origin with Basic | in-app none (loopback); remote inherits the profile's consent | B-PROTO-058, 084 |
| Downloads (updater, packs, rootfs) | `https` only | any | n/a | §3.10 |

`TransportPolicy.decide(backend, url, consentOrigin, pin)` returns `TransportPermit(origin, cleartextPermitted, pin)` or
a `Failure.Net.TrustRefused`. It is the only producer of `cleartextPermitted = true`.

### 4.3 Cleartext consent

- Stored per profile in the adopted prefs: `oc.cleartextOk.<profileId>` = the confirmed origin string
  (`http://host:port`, lower-case, IPv6 bracketed, explicit port). Swept by the `oc.<what>.<profileId>` rule.
- Valid only while `cleartextOriginOf(profile.baseUrl)` equals the stored value. Changing host, port or scheme asks again
  (B-SEC-121). A different private origin is not covered.
- **Held, never probed:** pairing and the server editor never send a byte to an unconfirmed plain-HTTP address
  ("nothing was sent to it") (B-SEC-124, B-PROTO-068).
- The URL validators return a `UrlRejection` enum (`MissingScheme, BadScheme, BadPort, BadHost, CredentialsInUrl,
  QueryOrFragment, PathNotAllowed, TooLong`); words live in `:ui:words` (ADR: no copy in logic).

### 4.4 Certificate pin

- Stored as `oc.certificatePin.<profileId>` = `{"origin":"https://host:port","sha256":"<64 hex>"}`. Restored only if the
  origin equals the profile's current origin, the scheme is `https` and the hex is exactly 64 characters
  (colon-separated input is accepted when pinning). Another profile never inherits it; a host, port or scheme change
  deletes it (B-SEC-153/154). A pin cannot be downgraded to HTTP (`PinNeedsHttps`).

```kotlin
public class PinnedLeafTrustManager(
    private val pin: ByteArray,
    private val nowMs: () -> Long = System::currentTimeMillis,   // JVM module: the KMP System.* ban does not apply
) : X509ExtendedTrustManager() {
    @Volatile public var latchedMismatch: Boolean = false
        private set

    private fun check(chain: Array<out X509Certificate>?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no server certificate")
        val digest = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
        if (!MessageDigest.isEqual(digest, pin)) {
            latchedMismatch = true                       // every later call on this client is refused (§4.6)
            throw PinMismatchException()
        }
        leaf.checkValidity(Date(nowMs()))                // expired/not-yet-valid ≠ changed (B-SEC-007)
    }
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = check(chain)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = check(chain)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = check(chain)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?): Unit = throw CertificateException()
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?): Unit = throw CertificateException()
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?): Unit = throw CertificateException()
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    /** With a pin, the pin is the identity: hostname and CA checks do not apply (Flutter semantics [R]). */
    public fun hostnameVerifier(): HostnameVerifier = HostnameVerifier { _, session ->
        val leaf = session.peerCertificates.firstOrNull() as? X509Certificate ?: return@HostnameVerifier false
        MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(leaf.encoded), pin)
    }
}
```

- Unpinned clients use the platform trust store and OkHttp's hostname verifier (normal PKI). A CA-valid certificate
  with a different leaf never passes a pinned client ("normal PKI cannot bypass an existing pin").
- The check runs inside the TLS handshake, before OkHttp writes the request line, so no HTTP byte or credential is sent
  to a mismatched server. Proof P-N2 confirms this on the API 37 emulator with Conscrypt.

### 4.5 Certificate inspection (pin review) without HTTP

```kotlin
public object CertificateInspector {
    /** TLS handshake only; accepts any chain to show it; writes no application bytes; no proxy. */
    public fun inspect(host: String, port: Int, timeoutMs: Int = 8_000): InspectedCertificate {
        val capture = CapturingTrustManager()                                // records the chain, accepts it
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(capture), null) }
        Socket(Proxy.NO_PROXY).use { raw ->
            raw.connect(InetSocketAddress(host, port), timeoutMs); raw.soTimeout = timeoutMs
            (tls.socketFactory.createSocket(raw, host, port, true) as SSLSocket).use { s ->
                if (!isIpLiteral(host)) s.sslParameters = s.sslParameters.apply { serverNames = listOf(SNIHostName(host)) }
                s.startHandshake()
            }
        }
        return capture.leafSummary()   // subject, issuer, notBefore, notAfter, sha256 hex, "AA:BB:…" display
    }
}
```

`:core:net` exposes it as `interface CertificateInspection { suspend fun inspect(origin): Outcome<InspectedCertificate> }`
(run on IO). The test server must record **zero** HTTP requests during inspection (B-SEC-007).

### 4.6 Trust gate interceptor (first application interceptor)

Runs for every request and every WebSocket upgrade of the client, in this order; each failure throws a
`TrustRefusal : IOException` that `JvmErrorKind` classifies:

1. URL has userinfo → `CredentialsInUrl`.
2. `origin(url) != spec.origin` → `OriginChanged` (covers a changed address and any redirect target).
3. Scheme `http` and `!spec.cleartextPermitted` → `CleartextNotConfirmed`.
4. Pin set and scheme `http` → `PinNeedsHttps`.
5. `pin.latchedMismatch` → `CertificateChanged` (no silent concurrent retry clears it; only a new `ConnectionGraph` after
   the person reviews the new certificate does).

### 4.7 Android network security config

Keep `androidApp/src/main/res/xml/network_security_config.xml` as Flutter has it today: one
`<base-config cleartextTrafficPermitted="true" />` element; only the comment changes ("Dart remains the security
boundary" becomes "Kotlin remains the security boundary"). The
manifest keeps `android:usesCleartextTraffic="false"` and `android:networkSecurityConfig="@xml/network_security_config"`.
No `<trust-anchors>` element, so user-added CAs stay untrusted (targetSdk ≥ 24 default). A `:quality` test pins the
elements, not the comment (row B-PROTO-139): tightening it would break Tailscale and LAN servers; loosening it with user CAs would weaken TLS.

### 4.8 Credentials

- Never in a URL (OpenCode uses Basic headers; the PTY uses a 60 s single-use ticket in the query; Paseo uses a
  subprotocol plus a header plus in-protocol `hello.auth`; Codex a Bearer header on the upgrade).
- Read from `SecretStore` (`pw.<profileId>`, `oc.codexToken.<profileId>` — also the Paseo daemon password) at graph
  creation, registered with the `Redactor` on read, held only by the transport object.
- A 401 mid-session sets `passwordRejected` on the graph, stops the stream loop and every retry loop until the person
  updates the password (banner "Update password for <server name>", never a modal; "actions name their target").
- Pairing payloads are credentials: `toString()` redacts; the password is behind `consume()` (B-SEC-107).

---

## 5. WebSockets (Paseo, Codex, OpenCode PTY)

### 5.1 Verified facts that force the design

| Fact | Source |
|---|---|
| OkHttp 5.5.1 `RealWebSocket.connect` always sends `Sec-WebSocket-Extensions: permessage-deflate` and fails the call if the caller sets that header. | [V `RealWebSocket.kt:147-166`] |
| OkHttp's `WebSocketReader` has no inbound message-size limit; it buffers a whole message. | [V `WebSocketReader.kt`] |
| Ktor's OkHttp WebSocket session: `maxFrameSize` getter returns `Long.MAX_VALUE`, the setter throws; extensions unsupported. | [V `OkHttpWebsocketSession.kt:48-50, 71-72`] |
| OkHttp 5.5.1 supports any HTTP/1.1 upgrade: a request with `Connection: Upgrade` that gets a 101 returns `Response.socket` (okio `Socket`). | [V `CallServerInterceptor.kt:43-149`, `Response.kt:81-84`] |
| Ktor's `RawWebSocket(input, output, maxFrameSize, masking, coroutineContext)` is public common API in `ktor-websockets`; `DefaultWebSocketSession.start()` is `@InternalAPI`. | [V ktor-websockets 3.6.1 sources] |
| Flutter's Paseo client refuses any `Sec-WebSocket-Extensions` in the 101, disables compression, caps frames at 8 MiB; Codex at 4 MiB. | [V `paseo/transport.dart:245, 294`; `codex/transport.dart:182`] |

### 5.2 Design (N4-D5)

```kotlin
// :core:net commonMain
public data class WsSpec(
    val url: String,                       // ws:// or wss:// (converted to http/https for the upgrade request)
    val headers: List<Pair<String, String>>, // e.g. Authorization; never logged
    val subprotocol: String?,              // Paseo: "paseo.bearer.<password>"
    val maxMessageBytes: Long,             // Paseo 8 MiB, Codex 4 MiB, PTY 4 MiB, bounded history 1 MiB
    val pingIntervalMs: Long,              // 20 s
    val pongTimeoutMs: Long = 15_000,
    val handshakeTimeoutMs: Long = 8_000,
)
public interface WebSocketConnector { public suspend fun connect(spec: WsSpec): Outcome<WsSession> }

public interface WsSession {
    public val incoming: Flow<WsMessage>          // Text(String) | Binary(ByteArray); pings answered inside
    public suspend fun sendText(text: String): Outcome<Unit>
    public suspend fun sendBinary(bytes: ByteArray): Outcome<Unit>
    public val closeReason: Deferred<WsClose?>     // code + reason (reason is server text → Redacted)
    public suspend fun close(code: Short = 1000)
}
```

`OkHttpWebSockets.connect` in `:core:net-okhttp` (JVM; deps: OkHttp, `ktor-io`, `ktor-websockets`):

1. Build the upgrade request on a client derived from the graph's client:
   `client.newBuilder().protocols(listOf(Protocol.HTTP_1_1)).readTimeout(0, MILLISECONDS).build()`. The trust gate
   interceptor still runs (§4.6).
2. Headers: `Connection: Upgrade`, `Upgrade: websocket`, `Sec-WebSocket-Version: 13`, a fresh 16-byte base64
   `Sec-WebSocket-Key`, optional `Sec-WebSocket-Protocol`, the caller's auth headers. **No** extensions header.
3. Validate the response, in this order (each failure → `Failure.Net`, no retry): status `101` (401 → `SignInNeeded`;
   403 → `HostNotAllowed` or `PasswordRejected` per Paseo rules); `Upgrade: websocket` and `Connection: Upgrade`;
   `Sec-WebSocket-Accept == base64(sha1(key + 258EAFA5-E914-47DA-95CA-C5AB0DC85B11))`; **no**
   `Sec-WebSocket-Extensions`; if a subprotocol is echoed it must equal ours.
4. Wrap `response.socket.source/sink` into Ktor `ByteReadChannel`/`ByteWriteChannel` (JVM stream adapters) and call
   `RawWebSocket(input, output, maxFrameSize = spec.maxMessageBytes, masking = true, coroutineContext = ioScope)`.
5. `WsSessionImpl` (common) sits on the raw session: answers `Ping` with `Pong`, sends a `Ping` every `pingIntervalMs`
   and closes with 1001 if no `Pong` within `pongTimeoutMs`, reassembles fragments with a **running total cap**
   (`maxMessageBytes`; over cap → close 1009 and `Failure.Net.UnreadableReply`), answers `Close` and completes
   `closeReason`.

Fallback if P-N1 fails: an own RFC 6455 framer (about 350 lines) in `:core:net` common over the same upgraded socket.
Not acceptable as a fallback: OkHttp's own WebSocket (no inbound cap, forced deflate).

### 5.3 Settings per protocol

| | Paseo | Codex | OpenCode PTY |
|---|---|---|---|
| URL | `<base>/ws` | profile URL | `ws(s)://<host>/api/pty/{id}/connect?ticket=…&cursor=<n>&location[directory]=…` |
| Auth | `Sec-WebSocket-Protocol: paseo.bearer.<pw>` + `Authorization: Bearer <pw>` (only when a password is set) + `hello.auth {kind:"password"}` | `Authorization: Bearer <token>` | Basic header **and** the one-time ticket (`POST /pty/{id}/connect-token` with header `x-opencode-ticket: 1`, 60 s) |
| Message cap | 8 MiB (1 MiB on the bounded-history connection) | 4 MiB | 4 MiB |
| Ping | WS ping 20 s + app `ping` 20 s | WS ping 20 s | WS ping 20 s |
| Frames | one JSON object per text frame; binary `[opcode, slot, …payload]` = terminal output | one JSON-RPC message per text frame | server→client binary = raw output; first byte `0x00` = meta JSON `{"cursor":n}`; client→server **text** frames of raw input |
| Close codes | 4401 = wrong password; 4003 = protocol mismatch | — | 1000 = process ended; 4404 = not found/exited |
| Max pending requests | 64 | 64 | n/a |

### 5.4 `WsRpc`: shared request/response bookkeeping (Paseo and Codex)

- `requestId` generation per protocol (Paseo `m<epochMs>_<n>`, Codex JSON-RPC integer ids).
- **Connection epochs:** every request is stamped with the epoch of the socket it was written on; an answer from another
  epoch resolves as `StaleRequest`. A reconnect increments the epoch and fails all pending requests of the old one.
- **Read vs mutation timeout:** each request declares `kind = Read | Mutation`. A read that times out → `Unreachable(Timeout)`
  (caller may re-read). A mutation that times out, or whose socket drops after the frame was written →
  `DeliveryUnknown`, never retried (B-AGENT-062, 066).
- At most 64 pending; the 65th fails at once with `ServerBusy(Overloaded)` without writing.
- In-order delivery: one reader coroutine; JSON decoding for frames ≥ 64 K chars runs on `AppDispatchers.default` but
  results are handed over in arrival order (Flutter's background-decode inbox [R]).

---

## 6. The SSE engine (`:core:net`)

### 6.1 Parser (exact port of `lib/api2/sse2.dart` `Sse2Parser`)

Rules, each a test vector (B-PROTO-033, plus kotest-property fuzz: random chunk boundaries must yield identical frames):

1. Input is raw bytes fed in arbitrary chunks. A line ends at `\n` (LF) only; one trailing `\r` is stripped (CRLF).
   Lone CR is **not** a line end (Flutter parity; OpenCode and Gas City never send it).
2. Bytes are decoded as UTF-8 per completed line with malformed sequences replaced (never an error). Multi-byte
   characters split across chunks decode correctly because decoding happens per line.
3. A line starting with `:` is a comment (`: heartbeat`): ignored, but it counts as activity for the 45 s stall timer.
4. `field:value` — one leading space after the colon is removed; a line without a colon is a field with an empty value.
   Fields: `data` (appended; multiple `data:` lines join with `\n`), `event`, `id`. `retry` and unknown fields ignored.
5. An empty line dispatches the frame if it has `data`; a frame with only `event`/`id` and no data is not delivered.
6. **Caps:** a line longer than 16 MiB, or a frame whose `data` exceeds 16 MiB, switches to discard mode until the next
   empty line; the stream survives and the next frame parses normally.
7. `flush()` dispatches a trailing frame without its blank line. The loop calls `flush()` **only when the body ended
   cleanly**; a body that ends with an IO error discards the partial frame ("a frame cut mid-write is discarded").
8. The parser is allocation-light: it scans a `ByteArray` window and copies only completed lines (Flutter allocated per
   line [R PROTOCOLS-45]).

```kotlin
public class SseFrame(public val event: String?, public val id: String?, public val data: String)
public class SseParser(private val maxFrameBytes: Int = 16 shl 20, private val onFrame: (SseFrame) -> Unit) {
    public fun feed(bytes: ByteArray, offset: Int, length: Int)
    public fun flush()
    public val sawActivity: Boolean        // reset by the loop's stall timer
}
```

### 6.2 `EventStreamLoop`: one loop, three resume modes, two backoff policies

```kotlin
public sealed interface ResumeMode {
    public data object None : ResumeMode                                    // OpenCode 2 /api/event: refetch after (re)connect
    public data class QueryCursor(val param: String = "after") : ResumeMode // OC2 session log: ?after=<last durable.seq>&follow=true
    public data object LastEventId : ResumeMode                             // Gas City (later): Last-Event-ID + head-only replay check
}

public data class BackoffPolicy(
    val baseMs: Long,
    val capMs: Long,
    val preIncrement: Boolean,              // OC2: attempt++ before computing (first wait 1 s)
    val jitter: ClosedFloatingPointRange<Double>?, // null = none
    val resetAfterStableMs: Long = 30_000,
    val disconnectedLabelAfter: Int = 6,    // label only; retrying continues
    val stallTimeoutMs: Long,
) {
    public fun delayMs(attempt: Int, random: Random): Long
    public companion object {
        public val OpenCode2: BackoffPolicy = BackoffPolicy(500, 16_000, true, null, 30_000, 6, 45_000)
        public val GasCity: BackoffPolicy = BackoffPolicy(500, 30_000, false, 0.5..1.5, 30_000, Int.MAX_VALUE, 90_000)
    }
}
```

| Policy | Delay formula | Sequence | Source |
|---|---|---|---|
| OpenCode 2 | `clamp(500 ms × 2^min(attempt, 5), 500 ms, 16 s)`, attempt incremented before use | 1 s, 2 s, 4 s, 8 s, 16 s, 16 s … | [V `sse2.dart:205`] |
| Gas City (later) | `min(500 ms × 2^min(attempt, 16), 30 s) × U[0.5, 1.5)`, capped again at 30 s | jittered, ≤ 30 s | [V `orchestration/client/sse.dart:55-67`] |

State machine (one coroutine per stream, launched in the owning graph's scope):

| From | Event | To | Effects |
|---|---|---|---|
| start | — | `Connecting` | attempt = 0 |
| `Connecting` / `Reconnecting` | gate says offline (non-loopback) | `WaitingForNetwork` | no attempt spent; resume on network |
| any waiting state | HTTP 200 + `text/event-stream` | `Connected` | `nextAttemptAt = null`; start 30 s stability timer (resets attempt to 0); trace `events.connected`; caller reconciles (§12.3) |
| `Connected` | first frame | `Connected` | trace `events.first` |
| `Connected` | clean end of body | `Reconnecting` | `flush()` the parser; attempt++ (policy); schedule |
| `Connected` | IO error, reset, 45 s stall | `Reconnecting` / `Disconnected` (label when attempt ≥ 6) | discard partial frame; attempt++; `nextAttemptAt` set **before** the status is published |
| any | 401 / `UnauthorizedError` | `Stopped(SignIn)` | no further tries until `retryNow()` after a password update |
| any | 404 / 5xx / refused / timeout | `Reconnecting`/`Disconnected` | backoff |
| waiting for delay | network changed / `retryNow()` | `Reconnecting` | cancel the delay, attempt = 0, connect now |
| any | owner scope cancelled | (none) | cancel request; no further status |

```kotlin
public class EventStreamLoop(
    private val client: HttpClient,
    private val request: (attemptCursor: Long?) -> HttpRequestBuilder,   // rebuilt every try: auth, after=, Last-Event-ID
    private val policy: BackoffPolicy,
    private val resume: ResumeMode,
    private val gate: NetworkGate,
    private val clock: Clock,
    private val random: Random,
) {
    /** Cold flow: collecting starts the loop; cancelling stops it. */
    public fun signals(): Flow<StreamSignal>
    public fun retryNow()                 // after password update, manual "Try again", network change
}
public sealed interface StreamSignal {
    public data class Status(val status: StreamStatus) : StreamSignal
    public data class Frame(val frame: SseFrame, val connectGeneration: Int) : StreamSignal
}
```

### 6.3 Stream status

```kotlin
public sealed interface StreamStatus {
    public data object Connecting : StreamStatus
    public data object Connected : StreamStatus
    public data class Reconnecting(val attempt: Int, val nextAttemptAtMs: Long?) : StreamStatus
    public data class Disconnected(val attempt: Int, val nextAttemptAtMs: Long?) : StreamStatus   // label after 6 failures
    public data object WaitingForNetwork : StreamStatus
    public data object Suspended : StreamStatus                                                 // app in background, nothing watching
    public data class Stopped(val failure: Failure) : StreamStatus                              // sign-in needed, incompatible server
}
```

`nextAttemptAtMs` is a real schedule ("trying again in 12 s" counts down to it); it is set when the delay is chosen,
kept while that try is in flight, and null while connected (Flutter `nextAttempt` [V]). The UI never sees this type
directly: `:engine:connection` turns it into the quiet `LinkStatus` (§12.4).

### 6.4 Network and lifecycle gating

```kotlin
// :core:net commonMain (consumed by :core:net, :engine:connection, :engine:delivery, :engine:attention)
public interface NetworkStatus { public val state: StateFlow<NetworkState> }
public data class NetworkState(
    val online: Online,          // Yes (INTERNET ∧ VALIDATED), No (no default network), Unknown
    val transport: Transport,    // Wifi, Mobile, Other, Unknown (VPN, e.g. Tailscale, or wifi+cellular → Unknown)
    val metered: Boolean?,
    val defaultNetworkId: Long?, // changes when the default network changes
)
public interface NetworkGate {
    public suspend fun awaitUsable(hostClass: AddressClass)    // returns at once for Loopback; suspends while online == No
    public val changes: Flow<NetworkChange>                    // DefaultChanged, Lost, Regained
}
```

- Android: `:android:platform` ports `NetworkMonitor.kt` (default-network callback; callback arguments are
  authoritative; an `onLost` for an old default never erases a replacement announced by `onAvailable`; no probes, no
  network names, nothing persisted) and binds `NetworkStatus` (B-ANDR-037).
- Desktop: `UnknownNetworkStatus` (always `Unknown`, never gates). iOS later: `NWPathMonitor`.
- Reactions:

| Change | Effect |
|---|---|
| `online` becomes `No` | non-loopback streams move to `WaitingForNetwork` at the end of the current attempt; nothing new is dialed; delivery stops flushing |
| default network id changes (Wi-Fi ↔ mobile, Tailscale VPN up/down) | `ConnectionRegistry` calls `evictAll()` on every live graph's pool; every stream gets `retryNow()` (attempt 0) |
| `online` returns to `Yes`/`Unknown` | same as a change |
| loopback host (phone server) | never gated; a "no network" reading is advice only [R ANDROID-PLATFORM-42] |

- Lifecycle: `LinkDemand` from `:engine:connection` (input from `:android:background`): `Foreground`,
  `BackgroundWatching` (the short `dataSync` "watching this reply" service runs, or Stay connected is on),
  `BackgroundIdle`. On `BackgroundIdle` streams of every graph are cancelled and report `Suspended` (Flutter
  `suspendForLifecycle` [R]); on return they reconnect with the 4 s quiet grace (§12.4).

### 6.5 One stream, two channels (N4-D9)

`Oc2EventChannel` opens one `EventStreamLoop` on `/api/event` per graph, decodes once, adapts once (§7.6), then
publishes into a `MutableSharedFlow<GatewayEnvelope>` (replay 0). Two views:

- `projectEvents`: envelopes whose `location.directory` equals the graph's current directory, plus envelopes without a
  location (Flutter filter rule [V `gateway_events.dart:917-925`]);
- `allEvents`: everything, with `directory/project/workspace` stamped (attention for other projects, chat-feed refresh,
  `installation.*`, `worktree.*`).

Both views share the stream's status. Row B-PROTO-134 asserts one HTTP request serves both.

### 6.6 Backpressure and threads

- Reader: one coroutine on `AppDispatchers.io` reads `bodyAsChannel()` into a 16 KiB buffer and feeds the parser.
- Frames go through `Channel<SseFrame>(capacity = 4096, onBufferOverflow = SUSPEND)` to the decoder on
  `AppDispatchers.default`. A stalled consumer suspends the reader, TCP pushes back, the server may drop a slow
  consumer, and the loop reconnects and refetches — the protocol's own contract (comment in code names B-PROTO-034).
- Durable events are never dropped. Coalescing of deltas happens only in `:engine:transcript` (50 ms publish, ADR §9.3).

### 6.7 Durable session log (ported, not used in production)

`Oc2SessionLogStream` = `EventStreamLoop(resume = QueryCursor("after"))` on
`/api/experimental/session/{id}/log?after=<seq>&follow=true`; tracks the last `durable.seq`; emits the `log.synced`
boundary marker. The route exists in 2.0.28 [V binary: `/api/experimental/session/:sessionID/log`]. Tested by
B-PROTO-034 with the captured stream; not wired into the transcript (N4-D10).

---

## 7. OpenCode 2 client (`:protocol:oc2`)

### 7.1 Build order

`Oc2Wire` constants → `Oc2Dialect` (pure table) → `Oc2Routes` → wire DTOs → `Oc2Transport` (auth, dialect, status
mapping, bytes, SSE open) → `Oc2EventRegistry` → `Oc2Probe` → mappers → `Oc2EventAdapter` → `Oc2Client` (typed
calls) → `SessionPermissionPolicy` → gateways → PTY and shell runner. Each step lands with its catalog tests green.

### 7.2 Generations and the dialect table

- **Generation detection** (no extra request on beta): first `GET /health` success = beta; a 404 on a moved route while
  the generation is unknown triggers one `GET /info` (version string = stable; clean 404 = beta; other errors leave it
  unknown). The connect-time probe settles it (`settleDialect`, B-PROTO-039).
- Call sites speak the beta vocabulary; `Oc2Dialect.stableRoute()`/`stableResponse()` translate at the edge. 2.1.x moved
  nothing [R]; 2.2.x is unverified (P-N4).

| Beta call | Stable (2.0.4+, 2.1.x) wire |
|---|---|
| `GET /health`, `GET /server` | `GET /info` (response gains `healthy: true`) |
| `GET /project/current` | `GET /location`, response re-wrapped `{data: project}` |
| `GET /form/request` | `GET /form` |
| `/workspace*` | removed → `NotSupported(CloudEnvironments)` without a request |
| `PATCH /shell/{id}/timeout` | removed → `NotSupported(ShellTimeout)` |
| `GET/POST/DELETE /worktree/{projectID}`, `…/refresh` | project id moves to query (GET) or body (POST/DELETE/refresh) |
| `PUT/DELETE/POST /mcp/{server}[/connect\|/disconnect]` | `/experimental/mcp/…` (list `GET /mcp` stays) |
| `POST /generate` | `/experimental/generate` |
| `/session/import`, `/session/stats` | `/experimental/session/import`, `/experimental/session/stats` |
| `/session/{id}/skill`, `/wait`, `/export`, `/instructions/entries[/key]` | prefixed `/experimental` |
| `POST /session/{id}/inbox/{inboxID}/steer\|queue` | `PATCH /session/{id}/inbox/{inboxID} {delivery}` |
| `POST /session/{id}/form/{formID}/cancel` | `DELETE /session/{id}/form/{formID}` |
| `GET /session/{id}/form/{formID}/state` | `GET /session/{id}/form/{formID}`; `{data:{state}}` unwrapped to `{data: state}` |
| `POST /session/{id}/rename` | `PATCH /session/{id} {title}` |
| `POST /session/{id}/revert/clear` | `DELETE /session/{id}/revert` |
| `POST /session/{id}/command {command}` | field `command` → `name` |
| `POST /session/{id}/interrupt?continue=true` | query `continue` → `resume` |
| `POST /session/{id}/fork {boundary:{type:before,messageID}}` | `{before: messageID}`; `type: through` = no `before` |
| `POST /session/{id}/permission/{rid}/reply {reply}` | field `reply` → `decision` |
| plugin rows | stable nests `state.status` and `features.tui`; mapper reads both |

SSE paths are never translated. `Oc2DialectTableTest` (B-PROTO-013) is a table test over every row above.

### 7.3 Route table

`object Oc2Routes` lists every route the app calls as `Route(method, betaPath, locationScoped: Boolean)`. A JVM test in
`:tools:contracts` loads `native/fixtures/oc2/<pinned>/openapi.json` and asserts each route (after dialect translation)
exists with that method, and that every DTO property the mappers read exists in the referenced schema (§14.5).

### 7.4 Wire DTOs

Ported one to one from `lib/api2/models*.dart` with `WireJson` and the tolerant helpers (§3.7). Rules:

- Unions without a discriminator (message union of 10 types; assistant content `text|reasoning|tool`; tool state
  `streaming|running|completed|error`) decode from `JsonObject` by reading `type` first; unknown `type` →
  `UnknownMessage` / `UnknownContent` that the mapper turns into a quiet "Server message" notice (B-PROTO-018).
- Ids are `String`; `created`, `idle`, `timeCreated` are epoch-ms `Long`; `durable.seq` is `Long`.
- `GET /session/active` is validated strictly: a map of ≤ 1000 entries, keys non-empty ≤ 256 without whitespace,
  values exactly `{type:"running"}`; anything else rejects the **whole** snapshot (never read as "all idle")
  (B-PROTO-012).
- Model variant `"default"` reads as no variant; composite model ids are sent bare (`ModelRef.normalized`,
  B-PROTO-062).

### 7.5 Event registry

`Oc2EventRegistry` maps type strings to serializers of `Oc2Event` variants. The envelope decodes to `JsonObject`; the
payload is `data`, or the whole envelope when `data` is missing; any decode failure gives `Oc2Event.Unknown(type, raw)`;
nothing throws (B-PROTO-014, B-PROTO-027).

| List | Types | Count |
|---|---|---|
| **Typed** (exactly Flutter's set [V `events.dart`]) | `server.connected`, `log.synced`, `shell.created\|exited\|deleted`, `session.created\|renamed\|deleted\|moved\|forked\|viewed`, `session.agent.selected`, `session.model.selected`, `session.usage.updated`, `session.execution.started\|succeeded\|failed\|interrupted`, `session.inbox.enqueued\|delivered\|cancelled`, `session.inbox.delivery.changed`, `session.instructions.updated`, `session.synthetic`, `session.skill.activated`, `session.shell.started\|ended`, `session.step.started\|streamed\|ended\|failed`, `session.text.started\|delta\|ended`, `session.reasoning.started\|delta\|ended`, `session.tool.input.started\|delta\|ended`, `session.tool.called\|progress\|success\|failed`, `session.retry.scheduled`, `session.compaction.started\|delta\|ended\|failed`, `session.revert.staged\|cleared\|committed`, `session.message.content.updated`, `session.status`, `session.idle`, `session.compacted`, `permission.asked\|replied`, `form.created\|replied\|cancelled`, `mcp.status.changed\|resources.changed\|tools.changed`, `vcs.branch.updated`, `filesystem.changed`, `tui.toast.show`, `agent.updated`, `command.updated`, `config.updated`, `skill.updated`, `catalog.updated`, `models-dev.refreshed`, `integration.updated`, `credential.updated`, `credential.switched`, `plugin.added\|updated`, `reference.updated`, `websearch.updated`, `project.updated`, `worktree.updated`, `lsp.updated` | 83 |
| **Pass-through by name** (raw kept) | `installation.updated`, `installation.update-available`, `pty.created\|updated\|exited\|deleted`, `worktree.ready\|failed`, `workspace.ready\|failed` | 10 |
| **Ignored** (become `Unknown`, counted) | `persistent-pty.added\|removed`, `workspace.status`, `global.disposed`, `tui.prompt.append`, `tui.command.execute`, `tui.session.select`, `worktree.resolved`, `session.archived\|unarchived` (2.1), anything new | open |

`Oc2EventRegistryTest` (row B-PROTO-136) asserts the three lists exactly and that every type documented in
`docs/opencode2-protocol-notes.md` §3.2 (copied to `native/protocol/oc2/docs/`) is in exactly one list.

### 7.6 Event adapter → `GatewayEvent` (PROTOCOLS-19 rules, kept as semantics)

`Oc2EventAdapter` is stateful per graph (one instance, §6.5). It emits the neutral events of §11.2.

| OpenCode 2 event | `GatewayEvent`(s) | Rule that must not be lost |
|---|---|---|
| `server.connected` | `Connected` | first frame of every connection |
| `session.created` | `SessionUpserted` | carries project, directory, parent, workspace, subpath, model, agent |
| `session.renamed`, `session.moved` | `SessionChanged` | a move replaces **both** directory and workspace (moving back clears a workspace) |
| `session.deleted` | `SessionDeleted` | drops that session's forms and inbox items downstream |
| `session.model.selected`, `session.agent.selected` | `SessionSelection` | empty values ignored; never creates an unhydrated session (B-PROTO-102) |
| `session.usage.updated` | `SessionUsage` | merged in place, never a full session replace |
| `session.viewed` | `SessionViewed(idleMs)` | read watermark |
| `session.execution.started` | `RunStarted` | busy |
| `session.step.started` | `RunStarted` + `MessageUpserted(assistant)` | restores a lost busy; settles earlier steps' open calls; remembers agent/provider/model per message id |
| `session.step.ended` | `MessageUpserted(completed)` | **does not end the run** (B-PROTO-040) |
| `session.step.failed` | `MessageUpserted(completed + error)` | blank message → fixed "step failed without a message" + type + status in Details |
| `session.execution.succeeded` | `RunEnded(Succeeded)` | settles open calls as completed |
| `session.execution.interrupted` | `RunEnded(Interrupted(reason))` | reason `user\|inactivity\|shutdown\|superseded` rides along; open calls → failed "interrupted" |
| `session.execution.failed` | `RunEnded(Failed(type, details))` **only** | never also emit idle (it would overwrite the error); blank message = missing → words from type |
| `session.status {busy\|idle\|retry}`, `session.retry.scheduled` | `RunStarted` / (idle ignored on stable) / `RunRetrying(attempt, nextAtMs, typeTag)` | retry text only under Details |
| `session.text.started\|delta\|ended` | `PartUpserted("text-<n>")` / `PartDelta(Text)` / `PartUpserted(full text)` | part ids `text-<ordinal>` identical to the refetch mapper |
| `session.reasoning.*` | same with `reasoning-<n>` | |
| `session.tool.input.started\|delta\|ended` | `PartUpserted(pending)` / `PartDelta(ToolInputRaw)` / `PartUpserted(pending, raw)` | tool part id = call id |
| `session.tool.called` | `PartUpserted(running, input)` | |
| `session.tool.progress` | `PartUpserted(running, metadata replaced wholesale)` | |
| `session.tool.success\|failed` | `PartUpserted(completed\|error, joined text, attachments)` | a result for a call never seen → also `TranscriptStale` |
| open calls at step end / next step / run end | `PartUpserted(completed or error)` + `TranscriptStale` | tracker keeps ≤ **256** calls and steps, oldest evicted (B-CHAT-005) |
| `session.inbox.enqueued\|delivered\|cancelled`, `session.inbox.delivery.changed` | `InboxChanged` (+ for a `user` item: `MessageUpserted(user)` + `PartUpserted("text-0")` with **inbox id as message id**) | a waiting prompt joins the **newest** turn (`9de34551c`) |
| `session.instructions.updated` | `Notice(InstructionsUpdated)` with id `evt_…`→`msg_…` | server instruction text never shown |
| `session.skill.activated`, `session.shell.*` | `Notice(SkillChanged\|ShellChanged)` | text never shown |
| `session.revert.*` | `RevertChanged` | resets history cursors downstream |
| `permission.asked\|replied` | `PermissionAsked` (copies `source.id` to `callId`) / `PermissionSettled` | asks are never replayed; refetch on reconnect |
| `form.created\|replied\|cancelled` | `FormCreated` / `FormSettled` | ephemeral |
| refresh pings (`agent`, `config`, `plugin.*`, `websearch`, `catalog`, `models-dev`, `integration`, `credential.updated`, `command`, `skill`, `reference`, `project`, `worktree`, `lsp`, `mcp.*`, `vcs.branch`, `filesystem`) | `RefreshHint(kind)` | bursts share one reload downstream |
| `credential.switched` | `CredentialSwitched(integrationId, credentialId?)` only if well-formed | never a credential payload |
| `installation.*` | `Installation(available\|installed, version)` | exact versions kept (B-PROTO-047) |
| `pty.*`, `shell.*`, `worktree.ready\|failed`, `workspace.ready\|failed` | `TerminalChanged`, `ShellChanged`, `WorktreeOutcome` | freshness hints only, never messages |
| `session.compaction.*` | `PartUpserted(v2:compaction …)` | compaction is a notice, not a reply |

### 7.7 Probe and legacy recognition (`Oc2Probe`; orchestration in `:engine:connection`)

Order (B-PROTO-094/095, B-PROTO-021):

1. Validate the URL and the trust permit (consent, pin). An unconfirmed plain-HTTP address is not dialed.
2. `GET /api/health` with Basic.
   - 401 (empty body counts) = OpenCode 2 is there: `NeedsPassword` if none was given, else `PasswordRejected`
     ("it changes on every restart unless OPENCODE_PASSWORD is set").
   - 2xx JSON with `version` = success; `healthy: false` = "reported itself unhealthy", **no** fallback to the OpenCode 1
     check. 503 = starting.
   - Version must start `0.0.0-beta-` or have major ≥ 2; otherwise `Incompatible(UnsupportedServerVersion)` and one
     re-probe (an OpenCode 2 host serves its web UI HTML with 200 on the OpenCode 1 path).
3. 404 → one `GET /api/info`: accepted only in its real shape (`version` string AND (`pid` number OR `urls` list)), or
   401, or 503.
4. Anything else → one `GET /global/health`: `{healthy: bool, version: string}` = OpenCode 1 →
   `Incompatible(LegacyOpenCode1)`; the UI shows the upgrade command
   `npm uninstall -g opencode-ai && npm install -g @opencode/cli@2.1.1`. Otherwise "did not answer as an OpenCode 2
   server".
5. Failure words: DNS = spelling; refused = "is `opencode2 serve` running?"; timeout = reachability + setup guide;
   certificate; 401/403 never claim a missing server.
6. A failed connect re-probes once (`redetectOnFailure`), the retry passes false.

### 7.8 Sessions and messages

- `GET /session` with `directory` (not `location[…]`), `limit`, `order`, `search`, `parentID`, `cursor`; **pass the cursor
  alone** on continuation; page size 100 desc; global finder 50/page, unscoped, roots only, cursor kept after
  client-side archived filtering (B-PROTO-010, B-PROTO-020).
- `GET /session/{id}/message?limit=100&order=desc`, reversed to chronological; "older pages exist" only when
  `nextCursor` is non-empty **and** the page holds `limit` items (OpenCode returns a cursor on the last page)
  (B-PROTO-061).
- Staged-revert history walks cursors (loop-guarded) until a page holds an id below the boundary (PROTOCOLS-10).
- Handoff and gen-UI history readers are byte- and time-budgeted (§3.4), dedupe ids and fail rather than return partial
  history.

### 7.9 Sending a prompt (wire steps; the pipeline lives in `:engine:delivery`)

1. Dispatch fence: `beforeSessionDispatch` / `sessionDispatchSettled` (phone-engine admission; B-PROTO-029).
2. Staged revert pending → refuse with `Conflict(tag = SessionRevertPending)` before any POST (B-PROTO-043).
3. Permission preparation (`SessionPermissionPolicy`, B-PROTO-032, B-PROTO-028):
   - Default (ask): `PATCH /session/{id} {permissions, metadata}` with
     `[{action:"*",resource:"*",effect:"ask"}, …agent rules with allow→ask, …original session rules with allow→ask]`,
     denials kept in order; provenance in session metadata `oc.mobile.permissionRequests = {version:1, original, applied}`;
     max 1000 rules; then **read back** and require an exact match, else abort with "Could not turn on permission
     requests. Your message was not sent." Malformed rules or provenance fail closed.
   - Auto-approve sessions: restore the original rules (the server runs what its rules allow; its asks and denials still
     reach the phone) — the 2026-10-25 root-cause fix (`3b90a4c65`).
   - `GET /agent/{id}` 404 → retry twice at 500 ms (N4-D14).
4. `POST /session/{id}/prompt {id?, text, files?, agents?, skills?, metadata?, delivery?}` — `delivery` omitted for the
   default steer (owner rule "steer, never queue"); `"queue"` is sent only when the caller passes `Delivery.Queue`
   explicitly, and no native UI path does that on a runtime that can steer (B-PROTO-025). Flipping an item already in
   the inbox uses the inbox endpoint (§7.10). Attachments are `data:` URIs or `file:///abs` server paths, ≤ 20 MB each;
   `@path` mentions carry UTF-16 offsets; directories use mime `application/x-directory`; agent mentions are hints.
5. The response is an **inbox receipt** `{data:{id, sessionID, timeCreated, type:"user", payload, delivery}}`, not the
   reply. 409 when a client id is reused. Completion arrives by events or `/wait`.

`promptAttachmentMime` and office-text conversion are pure functions in `:engine:media` (media section); the protocol
only carries the result.

### 7.10 Permissions, forms, inbox, revert, notes (wire summary)

| Feature | Endpoints | Rules | Rows |
|---|---|---|---|
| Permissions | `GET /permission/request` (L), `GET /session/{id}/permission`, `POST /session/{sid}/permission/{rid}/reply {reply\|decision, message?}`; saved: `GET /permission/saved?projectID`, `DELETE /permission/saved/{id}` | reply by request id only (session recovered from the pending list; not pending → `NotFound(PermissionNotFoundError)`, idempotent upstream); `allow→once`, `deny→reject`; reject message steers the model | B-PROTO-019, 025, 073, 074 |
| Forms | `GET /form/request` (L) / `/form`; `GET /session/{id}/form`; reply `POST …/reply {answer}`; cancel `DELETE` | 400 `FormInvalidAnswerError` keeps the form; 409 `FormAlreadySettledError` resolves locally; hidden fields become preset answers (B-PROTO-022) | B-PROTO-024, 044 |
| Inbox | `GET /session/{id}/inbox`; `DELETE …/inbox/{id}` (409 if delivered); delivery flip `PATCH` | cancel returns text to the composer; 409 drops the item | B-PROTO-024, 045 |
| Staged revert | `POST …/revert/stage {messageID, files}` → fixed preview; commit `POST …/revert/commit`; clear `DELETE …/revert` | 409 `SessionBusyError` while running; send blocked while staged | B-PROTO-031 |
| Session note | `GET/PUT/DELETE …/instructions/entries[/mobile.note]` (stable `/experimental`) | ≤ 8192 bytes of the JSON encoding (quotes and escapes count); 413 carries `maxBytes`; re-read before save | B-PROTO-100, 101 |
| Export / import | `GET /session/{id}/export?sanitize=` bytes kept exactly; `POST /session/import` | 405/501/non-session 404 latch the feature off | B-PROTO-096..099 |
| Read state | `POST /session/{id}/view {idle}` | posts exactly the displayed idle timestamp | B-PROTO-023 |

### 7.11 PTY over WebSocket

`Oc2PtyChannel`: `POST /pty/{id}/connect-token` (header `x-opencode-ticket: 1`) → `{ticket, expires_in: 60}` →
`WebSocketConnector.connect(WsSpec(url = …/connect?ticket&cursor&location[directory], headers = Basic, maxMessageBytes = 4 MiB))`.
Cursor bookkeeping: add the byte length of every binary data frame; a `0x00` meta frame sets the cursor; reconnect
resumes with `cursor=<last>` (`-1` = all history). Resize is REST `PUT /pty/{id} {size}`, never over the socket.
`DELETE /pty/{id}` 404 = done. B-PROTO-036.

### 7.12 Other operations (one table)

| Area | Endpoints / rules | Rows |
|---|---|---|
| Catalog | `GET /provider`, `/model`, `/model/default`, `/agent` in parallel; a later failure while the first waits keeps its own type; keys by server `id` (model modes are separate models); `'default'` variant = none; only connected providers in connection order | B-PROTO-019, 030, 081 |
| Files | `GET /fs/read/<path>` raw bytes; `/fs/list?path=` (dir trailing `/` stripped); `/fs/find`; first 8192 bytes with NUL = binary | B-PROTO-011, 082 |
| VCS / projects / worktrees | `/vcs`, `/vcs/status`, `/vcs/diff?mode`, `/vcs/branches`, `POST /vcs/init` (409 = done); worktree create needs a commit; delete error uses old `{name, data:{forceRequired}}` shape | B-PROTO-035, 082 |
| Managed shells / shell runner | `POST /shell` (≤ 4096 chars, metadata owner token written **before** the request), poll 400 ms, page output by byte cursor (≤ 64 KiB × 64), `DELETE` (5 s cap); transport errors never attached (they can echo the command) | B-PROTO-059, B-BUILD-013 |
| MCP | runtime scope only (`mcpConfigWrites: false`); name checked against `GET /mcp` before `PUT` (PUT replaces silently); same captured location for both | B-PROTO-026, 080, 108 |
| Integrations | key, OAuth (attempt id remembered with integration + location, LRU 32, terminal statuses cached, restorable without contacting the server), command sign-in (fixed "Command sign-in failed") | B-PROTO-030, 022 |
| Plugins | bounded display ids, never raw loader errors; invalid inventory → fixed sentence | B-PROTO-078, 079 |
| Web search | strict validation, response location must match; 503 discards server copy | B-PROTO-125, 126 |
| Usage statistics | `/session/stats` server-wide; native IANA time zone; `from` omitted for all time; missing project never becomes all projects | B-PROTO-121, 122 |
| History migration (OpenCode 1 data) | `GET /experimental/migration/v1` → `required\|completed\|running{progress}\|error`; unknown shape = null | B-PROTO-063 |
| Setup config read | borrowed authenticated client; captured location; credentials registered for redaction before return; writes always refused | B-PROTO-104, 105 |

### 7.13 Command receipts and the `type` finding

Rule: a receipt means **admission, never completion**; a retry only **reads** the server. A client id is posted as the
prompt `id` (and as the session `id` at creation). If the response is lost, look the id up in
`GET /session/{id}/inbox` (pending) or `GET /session/{id}/message/{id}` (promoted user message). Present = confirmed;
absent, or capability lost, never authorises a resend (B-PROTO-042, B-STATE-008/009, B-AGENT-226).

**Finding (N4-D13):** Flutter's `CommandReceiptTransport` requires `receipt.type == 'prompt'` and `item.type == 'prompt'`
[V `command_receipt_transport.dart:89, 117`]. The server schema has inbox item types `user | synthetic | compaction |
move` only [V 2.0.28 binary, `Session.Inbox.Item`], and the captured receipt says `"type":"user"`
[V `test/fixtures/api2/prompt_receipt.json`, `inbox_list.json`]. So on real servers every Flutter receipt stays
"uncertain" and asks the person to review. The native check accepts `"user"`; the contract test runs against the real
fixtures (B-PROTO-135). P-N4 re-checks on 2.1.1 and 2.2.5.

### 7.14 Pin and live proof

- Pinned server: `@opencode/cli@2.1.1` (N4-D16). Latest today: 2.2.5 [V npm]. The Rust phone engine still pins 2.0.29
  (AI Team, later).
- `Oc2LiveProof` (JVM test task `:protocol:oc2:liveProof`, never part of normal runs) ports the 46 steps of
  `tool/qa/oc2_live_proof_test.dart` and adds: heartbeat ≤ 20 s apart, receipt `type`, durable log route present,
  `/api/info` shape. Env: `OC2_LIVE_URL`, `OC2_LIVE_PASSWORD` (read from a file path, never argv), `OC2_LIVE_DIR`,
  `OC2_LIVE_MODEL` (pick a listed free model; `jev-1.13-free` 500s upstream [R]).

---

## 8. Paseo client (`:protocol:paseo`), with ACP agents through it

Port of `lib/paseo` (9.1k lines) on `WsSession` + `WsRpc` (§5). Pins: Paseo tested set `{0.9.2, 0.11.2}`, phone pin
0.11.2, Claude Code `{2.1.283, 2.1.294}` [R `tested_versions.dart`]; npm latest is 0.14.0 today [V] (probe after the
switch, not before).

| Item | Rule |
|---|---|
| Endpoint | `ws`/`wss`, host Loopback or Tailnet only, path empty, `/` or `/ws`; no credentials, query or fragment; password ≤ 4096 with no whitespace, commas or control characters (it travels in a subprotocol) (B-SEC-113) |
| Hello | `{type:"hello", clientId:"cid_<32 hex>" (random per transport), clientType:"mobile", protocolVersion:1, appVersion:"0.8.0", auth?:{kind:"password", password}, capabilities:{all_providers:true, hello_rejection:true, provider_subagents:true, projected_subagent_timeline:true}}`; **never** claim `explicit_event_subscriptions` (permission and stream events would stop) |
| Answer | `server_info` (version, permissions, `features` incl. `workspaceFileEditing`) within 8 s |
| Requests | `{type:"session", message:{…body, type:T, requestId:"m<epochMs>_<n>"}}`; answer type ends `_response` or `.response`; any payload carrying the requestId settles it (archive answers by push) |
| Failure kinds | `invalidEndpoint, authentication, hostRefused, disconnected, invalidResponse, unavailable, newChatRequired, overloaded, deliveryUnknown, staleRequest, scopeMismatch, notFound, incompatibleProtocol` → `Failure.Net` + `PaseoFailure.reason` (daemon text trimmed to 240 chars, Details only) |
| Rejections | close 4401 after upgrade = wrong password; 4003 or `hello.rejected` = protocol mismatch; HTTP 403 with password = authentication, without = host not allowed (B-PROTO-071) |
| Turn truth | turn events own busy state (snapshots lag); a `running` record is believed only for a bound after its last change unless the snapshot carries `activeTurn` |
| Mid-turn | `joinsTurn` for `claude, codex, opencode, pi` (`activeTurnBehavior:"steer"`, "Add to this turn"); `restartsTurn` for ACP agents ("Stop and send"); unknown while the agent identity is not loaded — wait, never guess from the name |
| Permissions | provider suggestions only for "Always allow"; ACP host requests get exact offered actions; no reject choice → cancel the turn; questions and plans travel on the permission wire but are never tool grants |
| Probe | validate → `health()` → one-item session page → `providers()` names → close (B-PROTO-070) |
| Bounded history | private second connection, 1 MiB frames, 10 s |
| Terminal | binary frames `[opcode, slot, …]`; restore frame first, reset then replay; frames of other slots ignored |
| Monitor trap | a daemon reports pending permissions only after a session page ran: **list before reading requests** (AGENTS-BACKENDS-44; monitor rows B-AGENT-203) |

ACP agents (Gemini, Qwen, Kilo, Cline, Goose, Oh My Pi, fx) are Paseo providers registered by the phone helper; the
app sees them through `HostAgentProviderGateway` (flag `hostAgentProviders`). No ACP code exists in the native app.

Live proof: `PaseoLiveProof` (JVM task) against a scratch `@getpaseo/cli@0.11.2` daemon on the VPS for hello,
providers and agent listing; the full chat proof runs on the emulator through the in-app helper (E2E-30).

---

## 9. Codex client (`:protocol:codex`)

Port of `lib/codex` (2.6k lines). Codex app-server **0.153.4** JSON-RPC over `WsSession`, one message per text frame,
4 MiB cap, 64 pending, 15 s request timeout; `initialize {clientInfo:{name:"opencode_mobile"}, capabilities:{experimentalApi:false}}`
then `initialized`; Bearer token on the upgrade; `ws://` only to Loopback. Account API only when the server's
`userAgent` matches 0.153.4. Steering: `turn/steer {threadId, input, expectedTurnId}`; a stale or idle target fails
`StaleRequest` and is never retried as `turn/start` or interrupt. Approvals are answered once; a pending approval cannot
be reloaded after a disconnect (no list operation). Handoff reads `thread/read(includeTurns:true)` without resuming,
≤ 5000 turns, ≤ 10000 items, 4 MiB.

Pin stays 0.153.4 for parity (npm latest 0.166.0 [V]); a re-pin is a post-switch slice with re-recorded fixtures.
Contract tests use `tool/qa/codex_fixture/fixture_server.py` recordings.

---

## 10. Outside this section's switch scope

| Client | State | Native plan |
|---|---|---|
| Direct ACP v1 client (`lib/acp`) | spike, no caller | not ported; rows retired after N4-O1 |
| A2A external agents | no owner use | dropped (ADR D6) |
| Gas City supervisor (HTTP + SSE with Last-Event-ID, jitter) | AI Team | `:protocol:team` after the switch; uses `EventStreamLoop(ResumeMode.LastEventId, BackoffPolicy.GasCity)` |
| Phone-engine `/v1` (Rust, Bearer, loopback) | AI Team | `:protocol:team` after the switch |
| FactsBook node API (Ed25519, JCS) | FactsBook | after the switch (ADR D6) |
| Session-address links (`sessionAddressHandoff`, off in Flutter) | off | not ported (N4-O4) |

---

## 11. Gateway contracts and capability detection (`:core:domain`)

### 11.1 Gateway interfaces

A 1:1 port of `lib/domain/server_gateway/interfaces.dart` (494 lines) with three mechanical rules: every call is
`suspend` and returns `Outcome<T>`; every optional facet method has a default body returning
`Outcome.Err(Failure.Net.NotSupported(feature))` (the Dart `ProductRepository` graceful defaults); OC2-only types that
Dart leaked into the interfaces (`Api2FormInfo`, `Api2InboxItem`, `Api2FormState`) move into `:core:model` as
`FormSpec`, `InboxItem`, `FormState`.

```kotlin
public enum class BackendKind { OpenCode, Paseo, Codex }          // profile JSON "backend": openCode|paseo|codex

public interface GatewayFactory {                                   // contributed as a Metro set (03 §0.2 S3)
    public val kind: BackendKind
    public suspend fun probe(target: ProbeTarget): ProbeResult
    public fun open(spec: GatewaySpec, scope: CoroutineScope): GatewayPair   // no I/O until first use
}
public class GatewayPair(public val live: ServerGateway, public val operations: ServerOperationsGateway)

public interface ServerGateway {
    public val capabilities: StateFlow<ServerCapabilities>
    public val location: StateFlow<Location>                       // directory + workspace
    public fun setLocation(location: Location)
    public val events: GatewayEvents                                // §11.2
    // HealthGateway
    public suspend fun health(): Outcome<Health>
    // SessionGateway
    public suspend fun sessionPage(cursor: String?, limit: Int = 100): Outcome<ServerPage<Session>>
    public suspend fun createSession(spec: NewSession): Outcome<Session>
    public suspend fun session(id: String): Outcome<Session>
    public suspend fun renameSession(id: String, title: String): Outcome<Unit>
    public suspend fun deleteSession(id: String): Outcome<Unit>
    public suspend fun sessionStatuses(): Outcome<Map<String, SessionStatus>>   // strict (§7.4)
    public suspend fun messagePage(sessionId: String, cursor: String?, limit: Int = 100): Outcome<ServerPage<MessageWithParts>>
    public suspend fun diff(sessionId: String): Outcome<List<FileDiff>>
    // PromptGateway
    public suspend fun prompt(sessionId: String, prompt: OutgoingPrompt, commandId: String?): Outcome<PromptAdmission>
    public suspend fun shell(sessionId: String, command: String): Outcome<Unit>
    public suspend fun slashCommand(sessionId: String, name: String, args: String): Outcome<Unit>
    public suspend fun stop(sessionId: String, resume: Boolean = false): Outcome<StopResult>
    // PermissionGateway, FormGateway, InboxGateway, QuestionGateway (legacy, inert on OC2)
    public suspend fun pendingPermissions(): Outcome<List<PermissionRequest>>
    public suspend fun replyPermission(requestId: String, reply: PermissionReply, message: String?): Outcome<Unit>
    public suspend fun pendingForms(sessionId: String?): Outcome<List<FormSpec>>
    public suspend fun replyForm(sessionId: String, formId: String, answer: FormAnswer): Outcome<Unit>
    public suspend fun cancelForm(sessionId: String, formId: String): Outcome<Unit>
    public suspend fun inbox(sessionId: String): Outcome<List<InboxItem>>
    public suspend fun cancelInboxItem(sessionId: String, inboxId: String): Outcome<Unit>
    public suspend fun setInboxDelivery(sessionId: String, inboxId: String, delivery: Delivery): Outcome<Unit>
    // ProviderGateway, FileGateway
    public suspend fun catalog(): Outcome<CatalogSnapshot>
    public suspend fun listFiles(path: String): Outcome<List<FileNode>>
    public suspend fun fileContent(path: String): Outcome<FileContent>
    public suspend fun findFile(query: String): Outcome<List<String>>
    public suspend fun awaitActionTransport(): Outcome<Unit>       // Flutter prepareActionTransport (§12.6)
    public suspend fun close()
}
// Optional facets, each its own interface implemented by the gateways that support it (ported names):
// CorrelatedPromptGateway, MidTurnPromptGateway, SessionSelectionGateway, StagedRevertGateway, SessionReadStateGateway,
// SessionNoteGateway, SessionExportGateway, SessionImportGateway, SessionSkillGateway, ActiveContextGateway,
// PluginGateway, McpRemovalGateway, IntegrationCredentialGateway, IntegrationCommandGateway,
// IntegrationAuthRecoveryGateway, UsageStatisticsGateway, HistoryMigrationGateway, WebSearchGateway, RemoteShellGateway,
// OneShotCommandGateway, GenUiHistoryGateway, AgentHandoffHistoryGateway, TerminalCommandGateway,
// HostAgentProviderGateway, ProviderConversationImportGateway, HostDaemonUpdateGateway, AgentAccountGateway.
// ServerOperationsGateway = SessionOperations + Host + Vcs + Terminal + ManagedShell + Catalog + Mcp + Integration + Request.
```

`PromptAdmission` = `Confirmed(receiptId)` | `Uncertain` (lost response; look it up) — never "completed".

### 11.2 `GatewayEvent` (neutral vocabulary, N4-D11)

```kotlin
public class GatewayEnvelope(public val event: GatewayEvent, public val location: EventLocation?, public val receivedAtMs: Long)

public sealed interface GatewayEvent {
    public data object Connected : GatewayEvent
    // sessions
    public data class SessionUpserted(val session: Session) : GatewayEvent
    public data class SessionChanged(val sessionId: String, val title: String?, val directory: String?,
                                     val workspaceId: String?, val replacesLocation: Boolean) : GatewayEvent
    public data class SessionDeleted(val sessionId: String) : GatewayEvent
    public data class SessionSelection(val sessionId: String, val model: ModelRef?, val agent: String?) : GatewayEvent
    public data class SessionUsage(val sessionId: String, val cost: Double, val tokens: Tokens) : GatewayEvent
    public data class SessionViewed(val sessionId: String, val idleMs: Long) : GatewayEvent
    // runs
    public data class RunStarted(val sessionId: String) : GatewayEvent
    public data class RunRetrying(val sessionId: String, val attempt: Int, val nextAtMs: Long?, val tag: WireTag?,
                                  val details: Redacted?) : GatewayEvent
    public data class RunEnded(val sessionId: String, val outcome: RunOutcome) : GatewayEvent  // Succeeded | Interrupted(reason) | Failed(kind, details)
    // transcript
    public data class MessageUpserted(val message: MessageInfo) : GatewayEvent
    public data class PartUpserted(val part: Part) : GatewayEvent
    public data class PartDelta(val sessionId: String, val messageId: String, val partId: String,
                                val field: DeltaField, val delta: String) : GatewayEvent       // Text, Reasoning, ToolInputRaw
    public data class TranscriptStale(val sessionId: String) : GatewayEvent                   // "re-read the real result"
    public data class Notice(val sessionId: String, val messageId: String, val kind: NoticeKind) : GatewayEvent
    public data class RevertChanged(val sessionId: String, val kind: RevertKind) : GatewayEvent
    // requests and inbox
    public data class PermissionAsked(val request: PermissionRequest) : GatewayEvent
    public data class PermissionSettled(val sessionId: String, val requestId: String) : GatewayEvent
    public data class FormCreated(val form: FormSpec) : GatewayEvent
    public data class FormSettled(val sessionId: String, val formId: String, val answered: Boolean) : GatewayEvent
    public data class InboxChanged(val sessionId: String, val inboxId: String, val change: InboxChange) : GatewayEvent
    // hints and server facts
    public data class RefreshHint(val kind: HintKind) : GatewayEvent
    public data class CredentialSwitched(val integrationId: String, val credentialId: String?) : GatewayEvent
    public data class Installation(val kind: InstallationKind, val version: String) : GatewayEvent
    public data class TerminalChanged(val ptyId: String, val kind: TerminalChange) : GatewayEvent
    public data class ShellChanged(val shellId: String) : GatewayEvent
    public data class WorktreeOutcome(val ready: Boolean) : GatewayEvent
}
public interface GatewayEvents {
    public val status: StateFlow<StreamStatus>
    public val project: Flow<GatewayEnvelope>     // filtered to the current directory (+ location-less)
    public val all: Flow<GatewayEnvelope>         // unfiltered, location stamped
    public val connects: StateFlow<Int>           // successful (re)connects so far (Flutter _streamConnects)
    public fun retryNow()
}
```

The chat section owns `MessageInfo`, `Part`, `ToolState` in `:core:model` and the reducer in `:engine:transcript`;
Paseo and Codex mappers emit the same `GatewayEvent`s.

### 11.3 `ServerCapabilities` (85 flags, defaults exactly as Dart [V `capabilities.dart`])

```kotlin
// Stable for Compose through compose-stability.conf (`…oc.core.domain.**`, ADR §9.3); no Compose dependency here.
public data class ServerCapabilities(
    val scheduledPrompts: Boolean = false, val phoneTeamExecution: Boolean = false, val genUi: Boolean = false,
    val sessionAddressHandoff: Boolean = false, val agentHandoffFiles: Boolean = false, val setupConfigRead: Boolean = false,
    val setupConfigWrite: Boolean = false, val setupMcpInventory: Boolean = false, val setupAssistantSession: Boolean = false,
    val clientPromptMessageID: Boolean = false, val commandReceipts: Boolean = false, val agentAccount: Boolean = false,
    val hostAgentProviders: Boolean = false, val hostAgentPermissionActions: Boolean = false,
    val promptAttachments: Boolean = true, val promptImagesOnly: Boolean = false, val promptEchoTextOnly: Boolean = false,
    val subagentReplies: Boolean = true, val subagentSessions: Boolean = false, val promptAgentMentions: Boolean = true,
    val promptSteering: Boolean = false, val offlinePromptQueue: Boolean = true, val fileBrowsing: Boolean = true,
    val terminal: Boolean = true, val conversationShell: Boolean = true, val projectManagement: Boolean = true,
    val globalSessionSearch: Boolean = true, val sessionDiff: Boolean = true, val sessionFork: Boolean = true,
    val sessionCompact: Boolean = true, val persistentPermissionGrants: Boolean = true, val permissionRequests: Boolean = true,
    val savedPermissionList: Boolean = true, val messageCompletionEndsRun: Boolean = true, val sessionRevert: Boolean = true,
    val sessionImportExport: Boolean = true, val sessionNotes: Boolean = true, val serverCatalog: Boolean = true,
    val sessionModelProviderSwitching: Boolean = true, val agentSelection: Boolean = true, val slashCommands: Boolean = true,
    val profileAttentionPolling: Boolean = true, val managedWorkspaces: Boolean = true, val workspaceWarp: Boolean = true,
    val sessionSteal: Boolean = true, val consoleOrganizations: Boolean = true, val mcpOAuth: Boolean = true,
    val mcpChatConnect: Boolean = false, val mcpChatOAuth: Boolean = false, val mcpChatToolRefresh: Boolean = false,
    val mcpConfigWrites: Boolean = true, val mcpRuntimeAdds: Boolean = false, val mcpRuntimeRemovals: Boolean = false,
    val integrationCredentials: Boolean = false, val integrationCommandAuth: Boolean = false, val pluginInventory: Boolean = false,
    val webSearch: Boolean = false, val sessionShare: Boolean = true, val sessionArchive: Boolean = true,
    val sessionDelete: Boolean = true, val sessionTodos: Boolean = true, val messageDelete: Boolean = true,
    val workspaceSymbols: Boolean = true, val textSearch: Boolean = true, val languageServiceStatus: Boolean = true,
    val formatterStatus: Boolean = true, val toolInventory: Boolean = true, val experimentalCapabilities: Boolean = true,
    val shellSettings: Boolean = true, val developmentServices: Boolean = false, val remoteUpgrade: Boolean = true,
    val clientDiagnostics: Boolean = true, val gitInit: Boolean = true, val providerRuntimeRefresh: Boolean = true,
    val configuredProviderFallback: Boolean = true, val globalEventStream: Boolean = true, val worktreeReset: Boolean = true,
    val worktreeCreate: Boolean = true, val worktreeBrowsing: Boolean = false, val legacyQuestionRequests: Boolean = true,
    val forms: Boolean = false, val inbox: Boolean = false, val cliSessionResume: Boolean = true,
    val projectsAreGitRepositories: Boolean = true, val remoteShell: Boolean = false,
) {
    public companion object {
        /** "Unknown/offline": used before a gateway exists and while an OC1 history import is pending. */
        public val Unknown: ServerCapabilities = ServerCapabilities()
    }
}
```

### 11.4 Per-backend constants

- `Oc2Capabilities.Base` = PROTOCOLS-38's OC2 list (`api2ServerCapabilities`, `gateway_mappers.dart:47`);
  `PaseoCapabilities.Base` (`lib/paseo/gateway/capabilities.dart`); `CodexCapabilities.Base` (`lib/codex/gateway.dart:18`).
  `withGenUi(…)` re-applies managed-host readiness (`genUi`, `agentHandoffFiles`) on top.
- **Snapshot test:** once, in a throwaway Flutter worktree branch (not merged), a 30-line Dart script prints each constant
  as JSON into `native/fixtures/capabilities/{default,oc2,paseo,codex}.json`. `CapabilitySnapshotTest` (B-PROTO-016)
  asserts the Kotlin constants equal those files flag for flag. The file stays as the parity record.

### 11.5 Runtime latches

`CapabilityLatches` lives in each `ConnectionGraph`: a feature whose endpoint answers 404 for a non-session reason, 405
or 501 is latched off for that graph and recorded (`activeContext`, `sessionExport`, `sessionImport`, `sessionNotes`,
`managedShells`, `messageDelete`, `historyMigration`). A missing *session* (404 with `SessionNotFoundError`) never
latches; authorisation errors never latch (B-PROTO-101). The graph's effective capabilities =
`base ∧ ¬latched`. Latches reset when the graph is rebuilt.

### 11.6 Version rules (mechanism only)

`CapabilityRules.apply(base, serverVersion): ServerCapabilities` reads a table `VersionRule(flag, minVersion, maxVersion?)`.
The table is **empty at the switch** (N4-D12). Example of a later row: `sessionArchive` from 2.1.1 (2.0.x answers 204 and
ignores it [R]). Adding a row is a feature slice with its own live proof.

### 11.7 Gating rules

- UI gates on `ServerCapabilities` and `PlatformCapabilities` flags only, never on `BackendKind` (AGENTS.md). Konsist:
  no `BackendKind` reference in `:feature:*` or `:ui:*` except `:feature:servers` (which shows the backend type).
- "Absent, never disabled": a feature whose flag is false does not render (B-PROTO-124; Tools gate keeps its
  explanation, B-KIT-013).
- Konsist bans in every module: `HttpRequestRetry`, `ContentNegotiation`, `io.ktor.client.plugins.logging`,
  `HttpLoggingInterceptor`, `.newWebSocket(` (OkHttp's own WebSocket), `CertificatePinner`, `followRedirects = true`,
  `retryOnConnectionFailure(true)`.

---

## 12. Connection engine (`:engine:connection`)

### 12.1 API

```kotlin
public interface ConnectionRegistry {
    public val live: StateFlow<ImmutableMap<String, ConnectionSession>>     // by profile id (agents backend: "<id>.agents")
    public suspend fun open(profileId: String, purpose: Purpose): Outcome<ConnectionSession>
    public suspend fun openEphemeral(profileId: String, purpose: EphemeralPurpose, deadlineMs: Long): Outcome<EphemeralConnection>
    public fun graphFor(ref: ConversationRef): ConnectionGraph?
    public suspend fun close(profileId: String)
}
public enum class Purpose { Primary, Side, AgentsBackend, Monitor }
public enum class EphemeralPurpose { Probe, Schedule, WidgetAction, WidgetStatus, NotificationReply, Quota }

public interface ConnectionSession {
    public val graph: ConnectionGraph                    // Metro ConnectionScope extension (03 §13.4)
    public val link: StateFlow<LinkStatus>               // the quiet, UI-facing status (§12.4)
    public val capabilities: StateFlow<ServerCapabilities>
    public val passwordRejected: StateFlow<Boolean>
    public suspend fun retryNow()
}
```

### 12.2 Connect sequence (STATE-DATA-10, 7.2)

1. Read the profile; validate by backend (URL rules, consent, pin origin, secret present). A failure is a typed
   `Failure`, nothing dialed.
2. Bump the generation: cancel the previous graph's child scope (stale work ends; no generation counters needed,
   ADR §9.5).
3. Build the `TransportPermit` → `TransportSpec` → `GatewayFactory(kind).open(...)`. One `Oc2Client` is shared by the
   live and operations gateways (Flutter `_createV2GatewayPair`).
4. Restore the saved location (`oc.location.<id>`) **without waiting** for the project list; revalidate later; a missing
   catalog entry never authorises switching.
5. Start the event stream (§6.5) and the 5 s busy poller (§12.5).
6. Hydrate in parallel, each tolerant: sessions page, statuses, permissions, forms, inbox of open sessions, catalog.
7. Sync services (monitors, background, widgets) through their engines' own observers.
8. On failure with `redetectOnFailure = true`: re-probe the address once, then connect again with `false`.

### 12.3 Reconcile on every (re)connect — exact order

Triggered by `StreamStatus.Connected` (HTTP 200), not by a timer:

1. Clear `lastFailure` and `passwordRejected`.
2. In parallel: refetch pending permissions, forms (and legacy questions on backends that have them), inbox of every open
   session; refresh gen-UI scope. Partial failures keep reply provenance (B-PROTO-048).
3. Flush the offline queue (§13.4), after step 2, in Flutter's order [R PROTOCOLS-20].
4. If the previous status was `Reconnecting`/`Disconnected`/`WaitingForNetwork`/`Suspended`: mark "data refresh ready"
   (open screens re-read from REST: the open chat re-reads its newest page and merges by id) and refresh the session list
   and statuses; then clear the quiet-reconnect token.
5. If this is the first attach after `Connecting`, the open chat reloads once more (events between transport-up and
   stream-live were never heard).
6. Record the automatic act "reconnected" (While you were away) when recovery happened without the person.
7. `connects += 1` (a waiting-request read uses it to know whether the stream carried every change since it started).

Global-channel loss marks other-project attention stale and clears feed question/form caches; any
`SessionUpserted|SessionChanged|SessionDeleted` on `all` schedules a chat-feed refresh (PROTOCOLS-20).

### 12.4 Quiet status and timers (port verbatim)

| Timer / threshold | Value | Meaning |
|---|---|---|
| quiet reconnect | 15 s | a healthy link that drops is not shown for 15 s |
| resume grace | 4 s | returning from background reconnects without a banner |
| connect status grace | 8 s | first connect shows nothing for 8 s |
| agent command hold-off | 10 min | a long agent command keeps "busy" after a drop |
| phone server "busy, not dead" | 2 min | alive but not answering → neutral line after 2 min |
| busy poll | 5 s | §12.5 |
| run end confirmation | 2 consecutive idle answers | OpenCode omits a running session between steps (`f267a25a2`) |
| turn stall start / probe / tick | 45 s / 10 s / 5 s | `:engine:transcript` owns it; asks `checkTurnStillRuns` before saying "dropped" |
| waiting-request re-read | 15 s | permissions are re-read every 15 s while a turn waits |
| turn-run check freshness / timeout | 20 s / 8 s | |
| stream "disconnected" label | after 6 failed tries | label only |

`LinkStatus` (UI-facing) = `Hidden | Connecting | Reconnecting(nextAttemptAtMs) | WaitingForNetwork | SignInNeeded(target) |
ServerStarting | Unreachable(why, addressClass) | Incompatible`. Words (in `:ui:words`) follow the owner rules: plain
words, name the server, Try again as a visible action, technical text only under Details.

### 12.5 Busy polling and run end

- Every 5 s while any session is busy: `sessionStatuses()`; a run ends only after **two consecutive** idle answers.
- `messageCompletionEndsRun = false` on OpenCode 2: a completed assistant message (step end) never ends the run; only
  `RunEnded` does (B-PROTO-040).
- Snapshots never override later live run events (a mid-run read must not keep a row "Working").

### 12.6 Wake and action transport

`awaitActionTransport()` completes as soon as the wake-time transport carries requests (one cheap call answered, or the
stream is `Connected`), before the session list and catalog reload. Sends, permission replies and notification replies
await it, share one write slot per request, and cannot cross a replaced connection (B-PROTO-048).

### 12.7 Secondary clients (same trust, own short scope)

| Client | Rules | Rows |
|---|---|---|
| Scheduled prompt runner | refuses transient-transport profiles, unconfirmed cleartext, missing password, non-OpenCode-2 servers; for the in-app server first `RuntimeHost.acquirePhoneServer(profileId, runtime, deadline)` → token, released in `finally`; same consent and pin as the foreground | B-AGENT-* scheduling rows (schedules section) |
| Widget action / status runners | one read; discarded if the world changed (new turn, new permission, run identity changed, scope change, stale ticket); timeout closes the transport; never clears a permission or marks idle wrongly; run identity = last real user prompt id, ignoring `v2:` rows | B-ANDR-103..107, 118, 119, B-PROTO-127, B-SEC-180 |
| Notification reply | uses the live background transport directly; never the foreground wake path (that clears every alert); a stale request id refreshes the alert | B-PROTO-046 |
| Profile monitor | sequential bounded snapshots of other servers; Paseo: list before requests | B-ANDR-056..064, B-AGENT-203 |
| Quota reader | in-app server: `http://127.0.0.1:4195`, Bearer only; remote server: same origin, header-only Basic; one GET; no token → unsupported, nothing sent | B-PROTO-058, 084..087 |

---

## 13. Offline queue and delivery (`:engine:delivery`)

### 13.1 Invariants (never relax)

1. A prompt is never sent twice and never silently lost (B-STATE-040..045).
2. A **dispatch marker** is written to disk before the request leaves the device; if the write is refused, the prompt
   stays unsent (B-STATE-042).
3. After acceptance, removal from the queue is persisted; if that removal fails or the process dies in between, the entry
   stays "possibly sent" and asks the person (B-STATE-043).
4. A failure after dispatch never auto-retries (B-STATE-040). Only an explicit Resend clears the marker (durably, first).
5. A corrupt or wrongly typed stored queue fails closed: nothing sent or overwritten until the person clears it
   (B-STATE-034).
6. The offline queue exists only for real offline sending. While a turn runs and the runtime can steer, sending steers
   (owner rule); the queue never offers "send after the reply".

### 13.2 Storage at the switch and after

- **Switch release:** `KeyValueStore` key `oc.offlineQueue` (file key `flutter.oc.offlineQueue`), app-wide JSON array,
  written through the queue's `WriterLane` with `commit()`. The migration must not tidy it: no eviction, no marker
  clearing, no re-enqueue (state-data risk 6). Limits apply on the next normal read, as today.
- **Switch+1 (or the ADR §9.8 trigger):** Room table `queued_prompt(profile_id, id, json, dispatched_at, created_at)`
  behind the same `OfflineQueueStore` interface (03 choice S14); import with counts and SHA-256, source key kept one
  release.
- Receipt journal: `oc.pendingCommands.<profileId>` (§13.6). Other keys carried: `oc.cleartextOk.*`,
  `oc.certificatePin.*`, `pw.*` and `oc.codexToken.*` (secrets), `oc.sessionInventory.*`, `oc.chatTail.*`,
  `oc.mainFeed.*`, `oc.sideFeed.*` (first-paint caches). The migration section owns the mechanics; WP-04.14 checks
  them on the upgrade journey.

### 13.3 Entry format and limits (read and write exactly)

```json
{"id":"…","profileID":"…","sessionID":"…","text":"…",
 "attachments":[{"mime":"…","filename":"…","url":"data:…|file:…|http(s):…"}],
 "mentions":[{"name":"…","value":"…","start":0,"end":5}],
 "modelProviderID":"…","modelID":"…","agent":"…","variant":"…",
 "createdAt":1787961231753,"error":"…","dispatchedAt":1787961231999}
```

Limits: 50 entries, 14 days, 60 MiB total, 20 MiB per entry. An entry over 20 MiB is refused (the composer keeps the
text); eviction is oldest first, reported once ("Discarded N queued drafts"); a single oversized entry is never evicted
into nothing; unreadable timestamps are kept (B-STATE-036, 046). The Flutter writer emits **every** key, with explicit
`null` for absent optionals [V `offline_queue.dart:119-148`]; the Kotlin codec for this type uses its own `Json` with
`explicitNulls = true` (not `WireJson`) so a queue written by native reads the same in a byte diff.

### 13.4 Flush algorithm

Runs on the queue's serial dispatcher; one flush at a time; requests during a flush coalesce into one more pass.

1. Preconditions: graph `Connected` (or loopback reachable); `capabilities.offlinePromptQueue`; not in OpenCode-2
   history migration; automation policy allows `reconcileQueuedSends`, unless the entry was explicitly resent
   (B-STATE-038).
2. Take entries of the **active profile**, oldest first; count others ("N drafts waiting for other servers", B-STATE-039).
3. For each entry:
   1. Re-check `(profileId, baseUrl, directory, workspace)` and that the graph is the same instance after every
      suspension (B-STATE-037). A draft discarded meanwhile is never sent.
   2. On staged-revert servers, read the session; staged → keep the entry with a `Conflict(SessionRevertPending)` error.
   3. Apply the entry's model/agent through `SessionSelectionGateway` when it was an intentional choice.
   4. Write `dispatchedAt` (queue lane, `commit()`); refusal stops the batch.
   5. Re-check policy; if it changed, roll the marker back (a refused rollback keeps the conservative marker).
   6. Dispatch: with `commandReceipts`, `prompt(..., commandId = "queue:<entryId>")` through the receipt journal
      (§13.6); otherwise plain `prompt`.
   7. Accepted → persist removal. Removal refused → leave the marker, note "accepted, unrecorded" (a resend would be a
      guaranteed duplicate, so it is not offered as automatic).
4. Failures: before dispatch with an HTTP status → entry keeps `error`, batch continues; no status (offline) → stop the
   batch; after dispatch → never retry, entry waits in "review" (`queuedPromptReviewCount`).
5. Publish `lastFlushedPromptCount` and `offlineFlushRevision++` for the one-shot "Sent N queued prompts".

### 13.5 Online sends use the same marker

`holdOnlinePrompt → canDispatchOnlinePrompt → finishOnlinePrompt`: an online send writes a marked entry first, so a
process death shows a review entry instead of losing or duplicating the prompt; confirmed acceptance and pre-flight
refusals remove it; profile deletion fences online sends (B-STATE-041).

### 13.6 Command receipt journal

`oc.pendingCommands.<profileId>` = `{"schemaVersion":1,"commands":[{commandID, receiptID, sessionID, tabID, scope,
createdAt, state}]}` with `state ∈ sent|uncertain|confirmed|failed`; ≤ 1000 entries / 1 MiB; tombstones kept, never
evicted; claims are atomic (only a new id claims); uncertain or terminal never become `sent` again; metadata only, never
prompt bodies. `scope = sha256(jsonEncode([profileId, baseUrl, directory, workspace]))` — the Kotlin encoder must give the
**same bytes** as Dart `jsonEncode` (compact, `null` for a missing directory/workspace) [V `state/connection/queue.dart:246-252`,
`state/session_creation_receipts.dart:35-45`]. `ScopeHashTest` uses vectors
produced once by Dart, including non-ASCII paths and a `"` in a path.

### 13.7 OpenCode 2 inbox

The server inbox ("Waiting to send" strip) is separate from the offline queue: items the server admitted but has not
delivered. Cancel returns the text to the composer; a 409 already-delivered cancel just drops the item; a delivery flip
updates server and UI (B-PROTO-045). Default delivery is steer (field omitted).

---

## 14. Contract tests with recorded fixtures

### 14.1 Fixture tree

```
native/fixtures/
  oc2/flutter-v57/           the 24 files of oc_app test/fixtures/api2/ copied byte-for-byte + MANIFEST.sha256
  oc2/2.1.1/<scenario>/NNN-<method>-<path-slug>.json   new recordings (§14.4); .sse for streams
  oc2/2.1.1/openapi.json     live /openapi.json of the pinned server (missing today [R])
  oc2/2.2.5/openapi.json     recorded in P-N4 for the diff; not used by tests
  oc2/beta-17823/openapi.json, oc2/beta-18600/openapi.json   copied from the Flutter repo contracts/
  paseo/flutter-v57/native_questions_0_9_2.json + README.md
  paseo/0.11.2/<scenario>/…   recordings (hello, providers, agents page, permission, timeline)
  codex/0.153.4/<scenario>/…  recordings from tool/qa/codex_fixture
  coverage/                   oc2_events_*, oc2_files_*, terminal_wire_*, terminal_paseo_*, tools_paseo_*, tools_wire_*,
                              codex_account_* ledgers + samples (oc1_* dropped)
  capabilities/{default,oc2,paseo,codex}.json   §11.4
  genui/runtime_paseo_claude_tool.json, runtime_paseo_paging.json
  host_agent_providers/*.json, agent_auth/*.json
```

The 24 OpenCode 2 Flutter fixtures: `agents.json`, `commands.json`, `error_form_invalid_answer.json`,
`error_not_found.json`, `events.sse` (12.7 KB real prompt round-trip), `forms_session.json`, `form_state_pending.json`,
`fs_list.json`, `inbox_list.json`, `location.json`, `message_assistant.json`, `messages.json`, `messages_2_1_1.json`,
`messages_page1.json`, `messages_page2.json`, `messages_variants.json`, `model_default.json`, `models_sample.json`,
`prompt_receipt.json`, `providers.json`, `session_create.json`, `session_stats.json`, `sessions_page.json`,
`skills_sample.json` [V `ls`].

Tests read fixtures through `OcFixtures.ROOT`, a `const val` that `build-logic` generates into `:core:testing` at
configuration time (absolute path of `native/fixtures`), read with kotlinx-io. No `System.getProperty` in KMP code, no
`expect/actual`.

### 14.2 `FixtureEngine` (`:core:testing`)

- Ktor `MockEngine` with `reuseHandlers = false`; matches method + decoded path + an allowlist of query keys
  (`location[directory]` matches both encoded and decoded brackets) + body shape; fails on an unmatched request and on
  leftover expected requests (strict mode).
- JSON exchanges return the recorded status, allowlisted headers and body.
- SSE exchanges stream the `.sse` file into a `ByteChannel` from `backgroundScope`; `: t=+40ms` comment lines become
  `delay()` (virtual time under `runTest`); chunk boundaries can be forced (every byte, random) to fuzz the parser
  through the real loop.
- Fault knobs: drop after N bytes, 401/503 at attempt k, stall (no bytes) for D ms, cut mid-frame.

### 14.3 Engine-level tests (`:core:net-okhttp`, JVM, mockwebserver3 + okhttp-tls 5.5.1)

| Test | Proves |
|---|---|
| `B-SEC-007 …` (8 cases) | HTTPS never sends Basic in clear; remote HTTP needs exact-origin consent; credentials never move origin; redirects blocked; inspection sends no HTTP (server records zero requests); only a matching pin releases credentials; expired matching cert ≠ untrusted TLS; PKI cannot bypass a pin |
| `B-PROTO-128 POST not resent after pooled connection failure` | a mockwebserver3 response that closes the socket after reading the request, on a reused pooled connection [U exact 5.x API name] → exactly one request recorded, `DeliveryUnknown` returned |
| `B-PROTO-129 redirects never followed` | 301/302/307 with `Location` → `Rejected`, no second request |
| `B-PROTO-130 no proxy` | with a system proxy property set, the call goes direct |
| `B-PROTO-131 WebSocket handshake and caps` | server offering `permessage-deflate`, wrong Accept, wrong subprotocol → refused; 8 MiB + 1 message → close 1009 |
| `B-PROTO-072 socket reuse` | two calls 4 s apart share one TCP connection (15 s keep-alive) |

### 14.4 Recorder (`:tools:contracts`)

`./gradlew :tools:contracts:run --args="record --server oc2 --version 2.1.1 --scenario prompt-roundtrip --target http://127.0.0.1:4197"`:
a Ktor-server reverse proxy between a scripted client (the live-proof steps, or a Maestro flow pointed at the proxy) and
a real server. Mandatory scrubbing: drop `Authorization`, cookies and `Sec-WebSocket-Protocol`; scrub API keys from
`/provider` and `/config` bodies; **fail the recording** if any value matches the per-run password (read from a file) or a
provider-key pattern (`sk-`, `ghp_`, `xox`, `AIza`, JWT, PEM). Never print the password.

Server setup on the VPS (AGENTS.md traps apply): `npm install -g --prefix <scratch> @opencode/cli@2.1.1`, HOME and XDG in
the scratch dir, `OPENCODE_PASSWORD` from a generated file, `opencode serve --port 41xx --hostname 127.0.0.1`; find the
listener with `ss -ltnp` (the npm wrapper starts the real binary as a child) and stop it by that exact PID.

### 14.5 Drift checks (T2, JVM)

1. Route table vs the pinned `openapi.json` (§7.3).
2. Every recorded JSON response validates against its operation's response schema (networknt 3.0.8; pre-bundle with
   swagger-parser if `$ref` resolution fails [U testing-perf §8]).
3. A server bump re-records, re-snapshots `openapi.json`, and the diff report lists moved, removed and new routes.

### 14.6 Live proofs (VPS, on demand, before any APK that changes a protocol)

| Proof | Target | Steps |
|---|---|---|
| `:protocol:oc2:liveProof` | scratch OpenCode 2.1.1 (and 2.2.5 in P-N4) | the 46 Flutter steps + heartbeat, receipt type, session log, info shape |
| `:protocol:paseo:liveProof` | scratch Paseo 0.11.2 daemon | hello, rejection with a wrong password, providers, agents page |
| `:core:net-okhttp:trustLiveProof` | port of `tool/qa/httptrust_live_proof.{py,dart}` | inspect, pin, consent against a real TLS host and a self-signed one |
| Codex | `tool/qa/codex_fixture/fixture_server.py` | initialize, thread list/read, steer, approval |

### 14.7 Fault scenarios recorded once and replayed forever

401 mid-stream; 503 `service_starting` on every non-health call; agent 404 after an idle hour; 2.0.29-style 400 on the
pending-permission list (`oc2-stuck-tools-root-cause`); stream drop mid-delta; lost `tool.success` then step end; a
result for an unseen call after reconnect; a steer sitting in the inbox during a long tool; a cut prompt response; an
OpenCode 1 server answering `/global/health`; an OpenCode 2 web UI returning HTML 200 on the OpenCode 1 path.

---

## 15. Catalog rows → native tests

### 15.1 Naming and placement

- Test names start with the row id: `` @Test fun `B-PROTO-033 SSE parser frames, comments, CRLF, chunk edges, oversize`() ``.
  One row may have several tests (`… (2)`); the ratchet counts rows with at least one passing test (ADR §6.3).
- **Exception:** instrumented tests on device cannot contain spaces in method names below DEX 040 (minSdk 26), so
  `androidTest` names use `B_PROTO_088_reconnect_flap`.
- Level → home: `unit` and `contract` → `commonTest` of the module (runs as `desktopTest`); `integration` → `commonTest`
  with fakes (`FixtureEngine`, `FakeNetworkStatus`, `TestClock`) or Robolectric in `:android:*`; `e2e` → Maestro flow
  named in `../catalog/e2e-flows.md`.
- A test is written **red first**, from the row text and the cited Flutter test (`sources` in `behaviors.jsonl`), before
  its code, by the test-writer lane one slice ahead (ADR §10.3).

### 15.2 The 127 PROTO rows

Status: **S** = in scope for the switch, **R** = retire (needs owner OK, §19), **L** = later with ADR D6.

| Rows | Flutter source | Native module · test class | Fixture | Slice | St |
|---|---|---|---|---|---|
| 001–009 | `acp_client_test` | — (direct ACP client not ported) | — | — | R (N4-O1) |
| 010–012 | `api2_client_test` | oc2 · `Oc2ClientContractTest` | `sessions_page`, `messages_page1/2`, `prompt_receipt`, `fs_list` | 1 | S |
| 013 | `api2_dialect_test` | oc2 · `Oc2DialectTableTest` | — | 1 | S |
| 014 | `api2_events_test` | oc2 · `Oc2EventRegistryTest` | `events.sse` | 1 | S |
| 015 | `api2_gateway_events_test` | oc2 · `Oc2EventAdapterTest` | `events.sse` + scenarios | 1 | S |
| 016 | `api2_gateway_mappers_test` | oc2 · `CapabilitySnapshotTest` | `capabilities/oc2.json` | 1 | S |
| 017–018 | `api2_gateway_mappers_test` | oc2 · `Oc2MessageMapperTest` | `messages*.json`, `message_assistant` | 1 | S |
| 019 | `api2_gateway_mappers_test` | oc2 · `Oc2CatalogMapperTest` | `providers`, `models_sample`, `agents` | 1 | S |
| 020 | `api2_global_pagination_test` | oc2 · `Oc2GlobalSessionsTest` | recorded | 2 | S |
| 021 | `api2_health_identity_test` | oc2 · `Oc2ProbeTest` | recorded | 1 | S |
| 022 | `api2_integration_form_test` | oc2 · `IntegrationFormTest` | recorded | 5 | S |
| 023–025 | `api2_interaction_gateway_test` | oc2 · `Oc2InteractionGatewayTest` | `forms_session`, `inbox_list` | 2 | S |
| 026 | `api2_mcp_scope_test` | oc2 · `Oc2McpGatewayTest` | recorded | 5 | S |
| 027 | `api2_models_test` | oc2 · `Oc2WireModelsTest` | all 24 | 1 | S |
| 028 | `api2_permission_dispatch_test` | oc2 · `Oc2PermissionDispatchTest` | scenarios | 2 | S |
| 029 | `api2_phone_chat_dispatch_test` | oc2 · `Oc2DispatchFenceTest` | — | 3 | S |
| 030 | `api2_provider_workflows_test` | oc2 · `Oc2ProviderWorkflowTest` | recorded | 5 | S |
| 031 | `api2_revert_test` | oc2 · `Oc2StagedRevertTest` | recorded | 4 | S |
| 032 | `api2_session_permission_policy_test` | oc2 · `SessionPermissionPolicyTest` | — | 2 | S |
| 033 | `api2_sse_test` | net · `SseParserTest` (+ property fuzz) | vectors | 1 | S |
| 034 | `api2_sse_test` | oc2 · `Oc2EventStreamTest`, `Oc2SessionLogStreamTest` (+ E2E-05) | `events.sse` | 1 | S |
| 035 | `api2_stable_flags_gateway_test` | oc2 · `Oc2StableFeaturesTest` | recorded | 5 | S |
| 036 | `api2_terminal_auth_test` | oc2 · `Oc2PtyConnectTest` | recorded | 5 | S |
| 037–039 | `api2_transport_test` | oc2 · `Oc2TransportTest` | `error_*` | 1 | S |
| 040 | `chat_oc2_step_gap_test`, `connection_v2_requests_test` | transcript · `RunLifecycleOc2Test` | scenarios | 2 | S |
| 041 | `command_receipt_transport_test` (OpenCode 1 history lookup) | — | — | — | R (N4-O2) |
| 042–043 | `command_receipt_transport_test` | delivery · `CommandReceiptTransportTest` | `prompt_receipt`, `inbox_list` | 2 | S |
| 044 | `connection_interaction_test` | requests · `Oc2FormsInteractionTest` | `forms_session` | 3 | S |
| 045 | `connection_interaction_test` | delivery · `InboxProjectionTest` (+ E2E-10) | `inbox_list` | 2 | S |
| 046 | `connection_interaction_test` | background · notification reply (E2E-07) | — | 4 | S |
| 047 | `connection_sse_wake_test` | oc2 · `Oc2InstallationEventTest` | — | 1 | S |
| 048 | `connection_v2_requests_test` | requests · `RequestReplyFenceTest` | scenarios | 3 | S |
| 049 | `connector_search_step_test` | integrations · `ConnectorSearchStatusTest` | — | 5 | S |
| 050–057 | `factsbook/http_factsbook_gateway_test` | — | — | after switch | L |
| 058 | `in_app_quota_gateway_test` | integrations · `InAppQuotaClientTest` | — | 5 | S |
| 059 | `managed_shell_gateway_test` | oc2 · `Oc2ManagedShellTest` | recorded | 5 | S |
| 060 | `message_delete_transport_test` | oc2 · `Oc2UnsupportedOpsTest` | — | 4 | S |
| 061 | `message_page_transport_test` | oc2 · `Oc2MessagePagingTest` (+ E2E-05) | `messages_page1/2` | 1 | S |
| 062 | `model_ref_normalization_test` | domain · `ModelRefTest` | — | 1 | S |
| 063 | `opencode_history_copy_test` | oc2 · `Oc2HistoryMigrationTest` | recorded | 3 | S |
| 064–067 | `pairing_payload_test` | oc2 · `Oc2PairingCodecTest`; domain · `PairingCandidateOrderTest` | vectors | 2 | S |
| 068 | `pairing_payload_test` | connection · `PairingDialerTest` (+ E2E-02) | FixtureEngine | 2 | S |
| 069 | `pairing_scanner_test` | connection · `PairingScanIngressTest` (+ E2E-02) | — | 3 | S |
| 070 | `paseo_gateway_test` | paseo · `PaseoProbeTest` (+ E2E-30) | recorded | 3 | S |
| 071 | `paseo_hello_rejection_test` | paseo · `PaseoHelloTest` | recorded | 3 | S |
| 072 | `perf_transport_decode_test` | net-okhttp · `ConnectionReuseTest`; net · `LargeJsonDecodeTest` | — | 1 | S |
| 073–074 | `permission_migration_test` | oc2 · `Oc2PermissionParseTest` | recorded | 2 | S |
| 075–077 | `phone_project_engine_gateway_test` | — | — | after switch | L |
| 078–079 | `plugin_inventory_test` | oc2 · `Oc2PluginInventoryTest` | recorded | 5 | S |
| 080 | `product_repository_mcp_test` | domain · `McpServerDraftTest` | — | 5 | S |
| 081 | `product_repository_projects_test` | oc2 · `Oc2CatalogMapperTest` | `providers` | 2 | S |
| 082 | `product_repository_projects_test` | oc2 · `Oc2ProjectFilesTest` | recorded | 5 | S |
| 083 | `product_repository_server_test` | domain · `ServerUpgradeVersionTest` | — | 4 | S |
| 084–087 | `provider_quota*_test` | integrations · `QuotaClientTest`, `QuotaSnapshotTest` | recorded | 5 | S |
| 088 | `reconnect_hardening_test` | connection · `ReconnectHardeningTest` (+ E2E-10) | faults (§14.7) | 2 | S |
| 089–093 | `server_data_widening_test` | oc2 · `Oc2DataWideningTest` (the v2 halves; v1 halves → N4-O2) | `messages*` | 1 | S |
| 094–095 | `server_probe_test` | connection · `ServerProbeTest` (+ E2E-03) | faults | 1 | S |
| 096–099 | `session_export_test`, `session_import_test` | oc2 · `Oc2SessionExportImportTest` (+ E2E-21 for 096) | recorded | 4 | S |
| 100–101 | `session_note_test` | oc2 · `Oc2SessionNoteTest` | recorded | 4 | S |
| 102 | `session_selection_sync_test` | oc2 · `Oc2SelectionSyncTest` | recorded | 3 | S |
| 103 | `session_skill_test` | oc2 · `Oc2SkillActivationTest` | `skills_sample` | 3 | S |
| 104–105 | `setup_config_adapter_test` | oc2 · `Oc2SetupConfigReadTest` | recorded | 5 | S |
| 106–107 | `setup_registry_test` | integrations · `SetupRegistryClientTest` | recorded | 5 | S |
| 108 | `sv3_mcp_test` | oc2 · `Oc2McpRemovalTest` | recorded | 5 | S |
| 109–120 | `team_control*`, `team_gascity_*` | — | — | after switch | L |
| 121–122 | `usage_statistics_test` | oc2 · `Oc2UsageStatisticsTest` | `session_stats` | 5 | S |
| 123 | `usage_subscription_sources_test` | integrations · `OpenCodeSignInSourceTest` | — | 5 | S |
| 124 | `v2_feature_gating_test` | domain · `CapabilityGatingTest` | — | 1 | S |
| 125–126 | `web_search_test` | oc2 · `Oc2WebSearchTest` | recorded | 5 | S |
| 127 | `widget_status_runner_test` | attention · `RunIdentityTest` | — | 4 | S |

Totals: **94 S**, **10 R** (9 ACP + 041), **23 L** (8 FactsBook + 3 phone engine + 12 Gas City).

### 15.3 Protocol rows in other areas

| Rows | Native home · test class | Slice |
|---|---|---|
| B-SEC-007 | net-okhttp · `TransportTrustTest` (§14.3) | 1 |
| B-SEC-119..125, 153, 154 | security · `AddressClassTest`, `ServerUrlRulesTest`, `TrustOriginTest`; profiles · `TrustPersistenceTest`; connection · `CleartextConnectRefusalTest` | 1 |
| B-SEC-055, 056 | oc2 · `CredentialIngressTest` (with the security section's `Redactor`) | 1 |
| B-SEC-094, 144, 145 | security section (`Redactor`); this section only calls it | 1 |
| B-SEC-107, 108, 124 | oc2 · `Oc2PairingCodecTest`; connection · `PairingDialerTest` | 2 |
| B-SEC-113, 114, 115, 116, 109, 110, 111 | paseo · `PaseoEndpointTest`, `PaseoPermissionMappingTest`, `HostAgentPermissionTest`, `PaseoHandoffTest`, `PaseoUsageTest` | 3 |
| B-SEC-174 | platform · `TailscaleSetupTest`; security · `ServerUrlRulesTest` | 2 |
| B-SEC-180 | widgets · `WidgetActionRunnerTest` | 4 |
| B-SEC-001, B-AGENT-001 | — (direct ACP) | R (N4-O1) |
| B-SEC-158 | — (session-address links, off) | R (N4-O4) |
| B-SEC-112, B-AGENT-152 | agents section decides (agent-browser scaffolding; owner said no browser 2026-11-01) | — |
| B-STATE-008, 009, 033..046, 066, 067 | delivery · `OfflineQueueTest`, `OfflineQueueConcurrencyTest`, `DispatchMarkerTest`, `ReceiptJournalTest`, `QueuedPromptRemovalTest` | 2 |
| B-STATE-010 | attention · `AttentionFeedConnectionTest` | 4 |
| B-STATE-011 | domain · `DirectoryPathTest` | 1 |
| B-AGENT-013, 142..191, 203, 226, 244 | paseo · one test class per Flutter file (`PaseoIdleBusyTest`, `PaseoUncertainStartTest`, `PaseoToolOutputTest`, …); agents engine for 153/154 (feed), integrations for 189..191 (usage) | 3 (usage 5) |
| B-AGENT-051..070, 220 | codex · `CodexTransportTest`, `CodexGatewayTest`, `CodexSteeringTest`, `CodexAccountSessionTest`, `CodexHandoffRecordTest` | 5 |
| B-CHAT-005 | oc2 · `Oc2OpenCallSettlingTest` | 1 |
| B-CHAT-070..072 | paseo · `PaseoStreamingTest`, `PaseoNativeQuestionsTest` | 3 |
| B-NAV-007..009 | connection · `ConnectionFailureExplainTest`; words · `NetFailureWordsTest` | 2 |
| B-ANDR-037 | platform (Robolectric) · `NetworkMonitorTest` | 2 |
| B-ANDR-056..064, 103..107, 118, 119 | attention / widgets / background (§12.7) | 4 |
| B-BUILD-003, 006; B-KIT-013; B-L10N-018; B-XPLAT-012, 013 | quality · `CapabilityLedgerTest`; domain · `PlatformCapabilitiesTest` | 1 / 4 |
| B-LINUX-114, 147, 223, 237, 239 | built-in Linux section (they only use this transport) | 3 |

### 15.4 Rows that need the owner's OK before the switch (ADR §10.1)

| Rows | Why | Decision |
|---|---|---|
| B-PROTO-001..009, B-AGENT-001, B-SEC-001 (11) | direct ACP client has no caller; ACP agents run through Paseo | N4-O1 |
| B-PROTO-041 and the OpenCode 1 halves of B-PROTO-089..093 | OpenCode 1 removed 2026-10-15 | N4-O2 |
| B-SEC-158 | session-address links are off in Flutter | N4-O4 |
| B-PROTO-050..057, 075..077, 109..120 (23) | FactsBook, phone engine, Gas City follow ADR D6 | already covered by ADR D6 |

### 15.5 New rows this section adds (append to the catalog; coordinator owns the catalog)

| Id | Behaviour | Level | Test home |
|---|---|---|---|
| B-PROTO-128 | A POST is never resent by the HTTP stack after a pooled connection fails; the caller gets "delivery unknown". | contract | net-okhttp |
| B-PROTO-129 | Redirects are never followed by Ktor or OkHttp; a 3xx is a plain failure. | contract | net-okhttp |
| B-PROTO-130 | No proxy is used, even when the system has one. | contract | net-okhttp |
| B-PROTO-131 | A WebSocket handshake refuses compression, a wrong Accept and a mismatched subprotocol; an inbound message over the cap closes with 1009 and a plain failure. | contract | net-okhttp |
| B-PROTO-132 | An event stream with no byte for 45 s counts as dropped and reconnects. | contract | net |
| B-PROTO-133 | With no network, a remote stream waits without spending retries and reconnects at once when the network returns; the phone's own server never waits. | integration | net |
| B-PROTO-134 | One OpenCode 2 event connection feeds both the project channel and the all-projects channel. | contract | oc2 |
| B-PROTO-135 | A prompt receipt with the real inbox type `user` confirms admission; other types, sessions or ids stay unconfirmed. | contract | delivery |
| B-PROTO-136 | The event registry equals the typed, pass-through and ignored lists; every documented type is in exactly one. | unit | oc2 |
| B-PROTO-137 | Decoding errors never contain the input JSON (a fake key in a body never appears in an exception message). | unit | common |
| B-PROTO-138 | A change of default network evicts pooled connections of every live server and retries every stream. | integration | connection |
| B-PROTO-139 | The network security config stays "cleartext permitted, system CAs only" (guard against tightening or loosening). | unit | quality |

---

## 16. Work packages (WP-04.x)

Every package: one agent, one worktree, one branch; reads `contracts/<slice>.md`; writes the listed rows red first; T0 =
`./gradlew :<module>:desktopTest --tests '*B-PROTO-0NN*'` while editing; T1 on the VPS before hand-off; format changed
files only (`tool/format-changed.sh`). Builders: Codex for core/protocol/engine (owner rule "Codex backend, Claude
frontend"); Opus reviews the event adapter because it feeds the transcript.

| WP | Slice | Builder | Module(s) | Rows (red first) | Done when |
|---|---|---|---|---|---|
| WP-04.0 | 0 | Codex + coordinator | `:core:domain`, `:core:common` | B-PROTO-137; default-flags snapshot (`capabilities/default.json`) | gateway interfaces, `GatewayEvent`, `ServerCapabilities` + snapshots, `Failure.Net`, `StreamStatus`, `NetworkStatus` compile for android+desktop and are frozen in `contracts/slice-1.md` |
| WP-04.1 | 0 | Codex | spikes | — | P-N1, P-N2, P-N3, P-N5 pass (§17) |
| WP-04.2 | 1 | Codex | `:core:security` | B-SEC-119..125, 153, 154 (unit parts), 113 (rules), 123, 174 (rules) | all vectors green; no `java.*` in commonMain (iOS klib job compiles) |
| WP-04.3 | 1 | Codex | `:core:net-okhttp` | B-SEC-007, B-PROTO-072, 128..131 | §14.3 table green on the VPS |
| WP-04.4 | 1 | Codex | `:core:net` | B-PROTO-033, 132, 133 | parser fuzz 10k cases; loop timing table test under virtual time |
| WP-04.5 | 1 | Codex (Opus reviews adapter) | `:protocol:oc2` | B-PROTO-010..019 (incl. 016 snapshots), 021, 027, 034, 037..039, 047, 061, 062, 089..093, 134, 136; B-CHAT-005; B-SEC-055/056 | all 24 Flutter fixtures decode to the same variants as Dart; live proof steps 1–25 green on 2.1.1 |
| WP-04.6 | 1 | Codex | `:engine:connection` | B-PROTO-094, 095, 124, 138; B-STATE-011 | connect → stream → reconcile order test (§12.3) with `FixtureEngine` |
| WP-04.7 | 1 | Sonnet (test-writer) + Codex | `:core:testing`, `:tools:contracts`, `native/fixtures` | — | fixtures copied with SHA-256 manifest; recorder refuses a planted password; 2.1.1 `openapi.json` committed |
| WP-04.8 | 2 | Codex | `:engine:delivery`, `:protocol:oc2` | B-STATE-008, 009, 033..046, 066, 067; B-PROTO-023..025, 028, 032, 042, 043, 045, 073, 074, 135 | queue reads a real Flutter `oc.offlineQueue` blob unchanged; flush tests green |
| WP-04.9 | 2 | Codex + Sonnet (servers UI) | `:engine:connection`, `:android:platform` | B-PROTO-020, 040, 064..068, 081, 088; B-SEC-107, 108, 124; B-NAV-007..009; B-ANDR-037 | E2E-02, E2E-03, E2E-05, E2E-10 pass on the release build (T3) |
| WP-04.10 | 3 | Codex | `:protocol:paseo` | B-PROTO-070, 071; B-AGENT-013, 142..191; B-SEC-109..111, 113..116; B-CHAT-070..072 | Paseo live proof + E2E-30 on the emulator |
| WP-04.11 | 3 | Codex | `:protocol:oc2`, `:engine:requests` | B-PROTO-029, 044, 048, 063, 069, 102, 103 | forms/permissions hydrate after a forced reconnect (fault replay) |
| WP-04.12 | 4 | Codex | `:protocol:oc2`, attention/widgets/background bindings | B-PROTO-031, 046, 060, 083, 096..101, 127; B-STATE-010; B-ANDR-056..064, 103..107, 118, 119; B-SEC-180 | E2E-07, E2E-21 green |
| WP-04.13 | 5 | Codex | `:protocol:codex`, `:protocol:oc2` (PTY, shells, files, MCP, integrations, plugins, web search, usage), `:engine:integrations` (quota, registry) | B-PROTO-022, 026, 030, 035, 036, 049, 058, 059, 078..080, 082, 084..087, 104..108, 121..123, 125, 126; B-AGENT-051..070, 220 | Codex fixture proof; PTY reconnect resumes at the right cursor |
| WP-04.14 | 6 | Opus (migration) + Codex | upgrade journey checks | — | after `adb install -r` over Flutter 2.1.0+57: servers connect without re-entering passwords; plain-HTTP consents and pins still apply; a queued prompt is still queued, not resent; pending receipts reconcile; final OC2 and Paseo live proofs at the pinned versions |

---

## 17. Proofs before fan-out (slice 0, each ≤ 2 days, each with a fallback)

| # | Proof | Acceptance | Fallback |
|---|---|---|---|
| P-N1 | WebSocket over OkHttp upgrade + Ktor `RawWebSocket` against a scratch Paseo 0.11.2 daemon and a mockwebserver3 WS endpoint | hello/answer round trip; 101 validation; no extension negotiated; subprotocol echo checked; 8 MiB + 1 → 1009; ping every 20 s | own RFC 6455 framer in `:core:net` (≈ 350 lines) |
| P-N2 | Pinned TLS on Android: `PinnedLeafTrustManager` + hostname verifier on the API 37 emulator with Conscrypt, against an okhttp-tls self-signed server on the PC (`10.0.2.2`) | matching pin works; changed cert latches; expired matching cert is its own error; inspection: server logs zero requests | none (security); escalate to Opus; optional repeat on an API 26 AVD |
| P-N3 | SSE through Ktor's OkHttp engine against live OpenCode 2.1.1 | first event < 1 s on loopback; heartbeat measured (expect 15 s); a paused proxy triggers the 45 s stall reconnect; `HttpTimeoutConfig` names compile | raw OkHttp call for the stream inside `:core:net-okhttp` |
| P-N4 | OpenCode 2.1.1 and 2.2.5 probe on the VPS | `openapi.json` of both recorded; route diff report; heartbeat; receipt `type` = `user`; session-log route present; `/api/info` shape; result in `docs/qa/native-oc2-probe-2026-11/README.md` | if 2.1.1 differs from the fixtures, re-record before WP-04.5 |
| P-N5 | Retry trap | mockwebserver3: POST on a reused connection, disconnect after request → exactly one request seen through the full Ktor client | none (must hold) |
| P-N6 | Network change on the emulator (`svc wifi disable/enable`, `svc data`) with a stream open and a prompt in flight | stream back < 2 s after the new default network; server sees the prompt once | widen the eviction to a full graph rebuild on network change |

---

## 18. Risks

| Risk | Likelihood / impact | Mitigation |
|---|---|---|
| OpenCode 2 moves weekly; 2.2.x unprobed | high / medium | dialect table + route drift test + P-N4; pin stays 2.1.1 at the switch |
| Ktor/OkHttp defaults silently re-enable retries or redirects in a future version | medium / high | B-PROTO-128..130 run at every T2; Konsist bans |
| The hand-written WS path has a framing bug | medium / medium | Ktor's `RawWebSocket` does the framing; our code is handshake + assembly only; P-N1 against a real daemon |
| Custom trust manager behaves differently under Conscrypt | low / high | P-N2 on the emulator; JVM tests cover the logic |
| Event-adapter rules lost in the port (stuck "Working", lost tool results) | medium / high | port the tests first (B-PROTO-015, B-CHAT-005, B-PROTO-040, 088); fault replays §14.7; Opus review |
| The 45 s stall timer fires on a server that heartbeats less often | low / medium | P-N4 measures; the constant lives in `Oc2Wire` with the measured value in its comment |
| Offline queue blob (up to 60 MiB) slows cold start before Room | medium / medium | ADR §9.8 trigger measures it; Room moves earlier if hit |
| Receipt fix changes behaviour the owner is used to (fewer "review" prompts) | low / low | N4-O5 tells the owner; it only removes false "possibly sent" states |
| Paseo 0.14 behaviour differs from 0.11.2 when the owner updates the helper | medium / medium | tested-versions gate; version added only with runtime evidence |
| Secrets in fixtures | low / high | recorder fails on the password or key patterns; T2 scan of `native/fixtures` |

---

## 19. Owner decisions (work proceeds on the default)

| # | Question | Recommended default |
|---|---|---|
| N4-O1 | Retire the 11 catalog rows of the unused direct ACP client? ACP agents (Gemini, Qwen, Kilo, Cline, Goose, Oh My Pi, fx) keep working through Paseo. | Yes, retire |
| N4-O2 | Retire the OpenCode 1-only rows (B-PROTO-041 and the OpenCode 1 halves of 089–093)? OpenCode 1 servers are still recognised and get the upgrade message. | Yes, retire |
| N4-O3 | Which OpenCode 2 version does the native switch pin? | 2.1.1 (today's pin); 2.2.x after the switch, with its own live proof |
| N4-O4 | Session-address links (off in Flutter): retire their row (B-SEC-158)? | Yes; rebuild only if you want shareable session links |
| N4-O5 | The Flutter app's prompt receipts never confirm on real servers (they check a type the server never sends). Fix only in the native app, or also patch Flutter now? | Native only (Flutter is frozen; the effect today is an extra "check this prompt" review, never a duplicate) |

---

## 20. Done checklist for this section

- [ ] `contracts/slice-1.md` freezes the interfaces of §11.1–§11.3, `Failure.Net` (§3.8), `StreamStatus`, `NetworkStatus`.
- [ ] Every S row of §15.2 and §15.3 has a passing native test named with its id; R and L rows are approved (§19, ADR D6).
- [ ] New rows B-PROTO-128..139 are in the catalog and green.
- [ ] `native/fixtures` holds the copied Flutter fixtures with a SHA-256 manifest and the 2.1.1 recordings; the T2 secret scan passes.
- [ ] The OpenCode 2 and Paseo live proofs pass on the pinned versions on the VPS; results recorded in `docs/qa/`.
- [ ] E2E-02, 03, 05, 07, 10, 21, 30 pass on the native **release** build on the emulator.
- [ ] The upgrade journey (ADR §10.2) shows: passwords, consents, pins, queued prompts and receipts survive; nothing is resent.
- [ ] Konsist bans of §11.7 are active; `network_security_config.xml` guard test passes.

---

## 21. Sources

- ADR: `01-architecture-decision.md` (§3.3, §5.2, §6, §7, §8, §9.5, §9.6, §9.8, §10, §12). Modules and catalog:
  `03-architecture-modules.md` (§0.2 S1, S3, S6, S14; §1.2; §5).
- Maps: `../map/protocols.md` (PROTOCOLS-01..46, §3, §6, §8, §11), `../map/agents-backends.md` (19–31, 35–39, 42–45),
  `../map/state-data.md` (STATE-DATA-10..20, §4.2, §10), `../map/android-platform.md` (42, 48).
- Research: `../research/network-data.md` (§0, §2, §3, §4, §5, §8, §11), `../research/testing-perf.md` (§2, §3, §8).
- Catalog: `../catalog/BEHAVIORS.md` (PROTO, SEC, STATE, AGENT, ANDR, NAV rows), `../catalog/behaviors.jsonl` (sources),
  `../catalog/e2e-flows.md` (E2E-02, 03, 05, 07, 10, 21, 30).
- Flutter source read (read-only): `lib/api2/{sse2,transport,gateway,gateway_events,events}.dart`,
  `lib/domain/{loopback_host,server_gateway/capabilities,server_gateway/interfaces,command_receipt_transport}.dart`,
  `lib/state/profiles/validation.dart`, `lib/platform/connection_advice.dart`, `lib/orchestration/client/sse.dart`,
  `lib/paseo/transport.dart`, `lib/codex/transport.dart`, `android/app/src/main/res/xml/network_security_config.xml`,
  `docs/opencode2-protocol-notes.md` §3, `test/fixtures/api2/*`, `tool/qa/`.
- Verified today [V]:
  - `okhttp-jvm-5.5.1-sources.jar`: `RealWebSocket.kt` (forced `permessage-deflate`, refused caller header),
    `WebSocketReader.kt` (no inbound cap), `Response.kt` (`socket`), `CallServerInterceptor.kt` (generic HTTP/1.1
    upgrade), `OkHttpClient.kt`.
  - `ktor-client-okhttp-jvm-3.6.1-sources.jar`: `OkHttpConfig.kt` (default `retryOnConnectionFailure(true)`; `config`
    chaining), `OkHttpEngine.kt` (`preconfigured.newBuilder()` then the config block; timeout mapping),
    `OkHttpWebsocketSession.kt` (no `maxFrameSize`, no extensions).
  - `ktor-websockets-3.6.1-sources.jar`: public `RawWebSocket`, `DefaultWebSocketSession` fragment-size check,
    `start()` is `@InternalAPI`.
  - OpenCode 2 binaries at `/home/eslam/Storage/Code/oc2-spike` (2.0.10, 2.0.28): SSE heartbeat every `"15 seconds"`;
    route `/api/experimental/session/:sessionID/log`; inbox item schema `user|synthetic|compaction|move`.
  - `npm view` 2026-11-01: `@opencode/cli` 2.2.5, `@getpaseo/cli` 0.14.0, `@openai/codex` 0.166.0.
  - Maven Central metadata: `okhttp-tls` 5.5.1, `mockwebserver3` 5.5.1, `ktor-client-mock` 3.6.1, `ktor-server-cio` 3.6.1,
    `turbine` 1.2.1, `kotest-property` 6.2.6.
- Owner memory: `steer-not-queue`, `no-raw-errors-rule`, `oc2-stuck-tools-root-cause`, `opencode2-stable-2.0-port`,
  `dev-freeze-native-rewrite-2026-11-01`, `freeze-owner-decisions-2026-11-01`, `codex-backend-claude-frontend`,
  `actions-name-their-target`, `build-vps`, `quiet-hours-vps-only`.
