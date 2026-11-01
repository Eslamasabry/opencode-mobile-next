# 04. Protocols and networking

Written 2026-11-01, revised the same day after review (see "Review notes" at the end). Builds on
`01-architecture-decision.md` (the ADR, binding). **03 §10 is the single source of shared names and signatures**; §11.0
lists every 03 name this file uses and the amendments to 03 it needs (the coordinator applies them to 03 before the
slice-0 freeze). For the `:core:net` and `:core:net-okhttp` seams, this file is the source and 03 §10.7 is amended to
match. Where this file and the ADR disagree, the ADR wins and this file is corrected.

## For the owner, in five lines

1. The new app talks to OpenCode 2, to Paseo (Claude Code and the other phone agents, including the ACP ones) and to Codex with small Kotlin clients we write ourselves; OpenCode 1 stays retired and is only recognised so the app can say "update this server".
2. Every hard-won rule of the Flutter app comes across: a prompt is never sent twice, plain HTTP needs your yes for each address (this phone and Tailscale addresses are checked by the app itself), a pinned certificate is checked before any byte leaves, and errors show plain words.
3. Live updates use one connection per server that retries after 1, 2, 4 and 8 seconds and then every 16 (only while that server's "reconnect automatically" setting is on; Try again always works), waits quietly while the phone has no network, and re-reads what it missed instead of guessing.
4. Before the clients are written, the Flutter app's recorded server answers and its 127 protocol test rows become native tests first; rows we want to drop (the unused direct ACP client, OpenCode 1 checks) are listed for your OK.
5. Your decisions are in §19; each has a recommended default, and work starts on the defaults.

Tags: **[V]** checked today against a primary source (library sources jars, Maven metadata, the OpenCode 2 binaries on
the PC, `npm view`). **[R]** read in the Flutter repo, a map, a research file, the ADR or memory. **[U]** unverified; the
named proof in §17 settles it. Behaviour ids such as `B-PROTO-033` are rows of `../catalog/BEHAVIORS.md`.

---

## 0. How to read this section

| Reader | Read | Then |
|---|---|---|
| Coordinator (before the slice-0 freeze) | §11.0 (names and 03 amendments), §22 (changes other sections take) | apply them to 03, 11, 16 and the catalog before WP-04.0 freezes anything |
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

Not here: the transcript reducer (chat section; it consumes `ServerEvent`, §11.2), the Flutter data migration (migration
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
| N4-D2 | `retryOnConnectionFailure(false)`, no redirects, `Proxy.NO_PROXY` — **re-asserted inside Ktor's `engine { config {} }`** because Ktor applies its own `retryOnConnectionFailure(true)` on top of a preconfigured client [V `OkHttpConfig.kt:17-22`, `OkHttpEngine.kt:159-163`]. That flag alone is not enough: OkHttp 5.5.1 `RetryAndFollowUpInterceptor` resends a request on a 503 with `Retry-After: 0` and on a 421 over a coalesced HTTP/2 connection whatever the flag says, unless the body `isOneShot()` [V lines 258-286]; Ktor's `ByteArrayContent`/`NoContent` bodies are not one-shot [V `OkHttpEngine.kt:231-253`]. So `HardenedOkHttp` also wraps every non-GET body (an empty one for bodyless POST/DELETE) as one-shot and strips `Retry-After` from 503s (§3.2). | A POST must never be resent by the stack. | No (same rule, new traps) |
| N4-D3 | Certificate pin = SHA-256 of the **leaf DER**, checked in an `X509ExtendedTrustManager` during the handshake; a mismatch latches the client shut. | Same identity override as Flutter; `CertificatePinner` pins SPKI after PKI. | No |
| N4-D4 | One address classifier: 03's `HostClass` (Loopback, PrivateLan, Tailnet, LinkLocal, Public, Invalid) and `AddressRules.classify` in `:core:security`, with its own IP-literal parser in common code. Tailnet hosts get the private-network consent wording. Tailnet stays IPv4 `100.64.0.0/10` + `.ts.net` (Flutter parity; no IPv6 range). | Flutter had three loopback checks and two tailnet checks that disagreed [R]. | Wording unified (matches the profile editor) |
| N4-D5 | WebSockets: OkHttp 5 HTTP/1.1 upgrade (`Response.socket`) on the **connection's own hardened client** + Ktor's public `RawWebSocket` with a message cap, a bounded incoming queue and no compression. | OkHttp's WebSocket always offers `permessage-deflate` and has **no inbound size cap**; Ktor's OkHttp WebSocket session cannot set `maxFrameSize` [V]; `RawWebSocket` queues incoming frames without bound by default [V `RawWebSocketCommon.kt:38`]. | No for Paseo and Codex (Flutter's caps and no-extension rule); **Yes** for the PTY (N4-D18) |
| N4-D6 | SSE: our own byte parser and reconnect loop (ADR §3.3). OpenCode 2 backoff is exactly Flutter's: 1, 2, 4, 8, 16 s cap, no jitter, reset after 30 s stable, label "disconnected" after 6 failures while still retrying. Jitter exists in the same loop for Gas City later. | Parity of timing and tests. | No |
| N4-D7 | SSE read stall: 45 s without a byte (3 × the 15 s heartbeat) counts as a drop. | Flutter had no read timeout, so a half-open socket hung until the network callback [R]. The 15 s heartbeat is in the 2.0.10 and 2.0.28 binaries [V]. | **Yes** (new row B-PROTO-132) |
| N4-D8 | Network gating: with no default network, a **Public or Tailnet** stream or socket waits without spending backoff attempts (Loopback, PrivateLan and LinkLocal are never gated: a server on the phone's hotspot or USB tether is reachable without a default network). On a new default network, every *Connected* non-loopback stream and socket is cancelled and reconnects at attempt 0, waiting ones retry at once, and idle pooled sockets of non-loopback connections are evicted; **loopback graphs (the phone server) are left alone**. | Flutter's network reading was "advice only" and its connection code had no network listener [R]; this keeps that for loopback and LAN, and stops useless retries and half-dead sockets elsewhere. | **Yes** (rows B-PROTO-133, 138; N4-O6) |
| N4-D9 | One physical `/api/event` stream per OpenCode 2 connection feeds the project view and the all-projects view, **each through its own bounded queue**, so a slow consumer of one never delays the other (§6.5). | Flutter opened two identical streams per connection [V `gateway.dart:804-828`]. Halves sockets and battery. | **Yes** (rows B-PROTO-134, 147) |
| N4-D10 | Reconnect reconciles by refetch, never by replay (ADR §9.5). The durable session-log client is ported and tested but unused, as in Flutter. | Binding ADR rule. | No |
| N4-D11 | Protocol adapters emit a neutral `ServerEvent` vocabulary (03's name; §11.2), not Flutter's v1-shaped names. | The transcript engine is new; v1 names only existed for old consumers [R protocols §8.10]. | Internal only |
| N4-D12 | `ServerCapabilities` at the switch = the 85-flag base per backend, plus runtime facts the server announces (Paseo `server_info.features`, §11.4), minus runtime latches; it is a `StateFlow` recomputed on every (re)connect. The version-rule table (03's "computed from the probed server version") is the mechanism and is **empty at the switch**. | Parity first; version rules are post-switch features. | No |
| N4-D13 | Command-receipt admission accepts the **real** inbox item type `"user"` at dispatch and in the inbox lookup. Flutter checks `"prompt"`, which no server sends [V 2.0.28 schema: `user\|synthetic\|compaction\|move`; fixture `prompt_receipt.json`], so its dispatch always reports "uncertain"; its lookup then falls back to `GET /session/{id}/message/{id}`, which confirms once the inbox item was delivered [V `command_receipt_transport.dart:110-125`]. | Latent Flutter bug: confirmation is delayed until delivery, and a prompt still waiting in the inbox shows a review prompt. | **Yes** (row B-PROTO-135; N4-O5) |
| N4-D14 | The only automatic HTTP retry is `GET /agent/{id}` on 404, twice, 500 ms apart (server boot). Ktor `HttpRequestRetry` is banned (Konsist). | Matches Flutter exactly [V `gateway.dart:442-454`]. | No |
| N4-D15 | The offline queue keeps its Flutter storage and format at the switch (`flutter.oc.offlineQueue` in the adopted prefs file); Room only at switch+1 or by the ADR §9.8 trigger. | ADR D9; markers must survive byte for byte. | No |
| N4-D16 | OpenCode 2 pin at the switch stays **2.1.1** (owner may change, N4-O3). 2.2.5 (published today [V npm]) is probed in P-N4 and recorded, not adopted. | Phone server, scripts and BYO bundle all pin 2.1.1 [R]. | No |
| N4-D17 | `WireJson` has `exceptionsWithDebugInfo = false` (kotlinx 1.12 defaults to `true`, which puts input JSON, possibly API keys, into exception text [V]). | Credentials invariant. | n/a |
| N4-D18 | OpenCode PTY WebSocket: no compression offered, and an inbound message cap (provisional 16 MiB; final value = max(4 MiB, 2 × the largest replay frame measured in P-N4)). | Flutter's PTY used dart:io `WebSocket.connect` (deflate offered, no cap); our upgrade path has no deflate, and an uncapped buffer can exhaust the heap. | **Yes** (row B-PROTO-143; N4-O6) |
| N4-D19 | HTTP/1.1 only (`protocols(listOf(HTTP_1_1))`) on every hardened client. | Flutter's dart:io client was HTTP/1.1-only; h2 through ALPN is the only route to OkHttp's 421 resend. | No (parity) |
| N4-D20 | On every (re)connect, the refetches (permissions, forms, questions, inbox) and the offline-queue flush **start together and none waits for another** (§12.3), through `ReconnectParticipant`s contributed by higher engines. | Flutter starts all four unawaited [V `state/connection/events.dart:95-100`]; a hung read must never hold back the flush. | No (parity) |
| N4-D21 | Automatic reconnects (backoff, network-change retries, resume reconnects, Paseo/Codex reconnect loops) run only while the server's automation policy allows `reconnect`; a person's Try again always works; turning it off retires the streams and keeps the data. | Flutter checks `AutomationBehavior.reconnect` at 10 sites, fail-closed [V]. | No (parity; rows B-SEC-017, B-LINUX-156, B-PROTO-146) |
| N4-D22 | Only Flutter's forwarded refresh hints become `RefreshHint` at the switch (agent, config, plugin, websearch, catalog/models-dev, integration/credential, credential.switched); the rest are counted and dropped. | `filesystem.changed` fires on every tool write; forwarding it would start a reload storm [V `gateway_events.dart:619-656`]. | No (parity; row B-PROTO-141) |

---

## 2. Where the code lives

All paths are under `native/` (section 03, choice S1). Packages are `io.github.eslamasabry.oc.<layer>.<module>`.

### 2.1 Modules this section fills

| Module | Plugin | Owner | Starts | This section puts here |
|---|---|---|---|---|
| `:core:security` | `oc.kmp.library` | S core | 0 | 03's `HostClass`/`AddressRules`, URL rules per backend, trust origin, pin value type, consent model, `TransportKind`, `TransportPolicy`, `TransportPermit` (only `:core:common` below it) |
| `:core:common` | `oc.kmp.library` | S core | 0 | `WireJson`, tolerant serializers, the detail fields this file adds to 03's `Failure` (§3.8) |
| `:core:model` | `oc.kmp.library` | S core | 0 | (03 owns the module) `BackendKind`, `StreamStatus` (sealed, §6.3), `StreamSignal<T>`, `NetworkState` + `NetworkStatus` (§6.4): types that `:core:net`, `:core:domain`, engines and `:android:platform` all need, and that `:core:net` cannot get from `:core:domain` (ADR §6.1) |
| `:core:domain` | `oc.kmp.library` | S core | 0 | gateway interfaces, `ServerEvent`, `ServerCapabilities`, `GatewayFactory`, `GatewaySpec`, `ProbeTarget`, `ProbeResult`, pairing order rules |
| `:core:net` | `oc.kmp.library` (c, a, d, t) | S backend | 1 | `TransportFactory` + `Transport` seam, Ktor client factory, request helpers, status→`Failure`, SSE parser, `EventStreamLoop`, `WsSession`, `WsReconnectPolicy`, `NetworkGate`, `HttpTrace`, downloader |
| `:core:net-okhttp` | `oc.jvm.library` | S backend | 1 | hardened OkHttp builder, `OkHttpTransport` (Ktor engine + WebSocket connector + error classifier + evict/close over one client), pin trust manager, trust gate interceptor, one-shot body and `Retry-After` interceptors, certificate inspector. External deps only (OkHttp 5.5.1, `ktor-client-okhttp`, `ktor-websockets`), so ADR §6.1 "no project dependencies" holds |
| `:protocol:oc2` | `oc.kmp.library` | F backend (Codex) | 1 | OpenCode 2 client, dialect, routes, DTOs, event registry, adapter, gateways, probe, pairing codec, PTY |
| `:protocol:paseo` | `oc.kmp.library` | F backend (Codex) | 3 | Paseo transport, gateway, mappers, terminal frames |
| `:protocol:codex` | `oc.kmp.library` | F backend (Codex) | 5 | Codex app-server JSON-RPC client and gateway |
| `:engine:connection` | `oc.kmp.library` | S backend | 1 | `ConnectionRegistry`, `ConnectionGraph`, connect sequence, reconcile, status model, busy polling, probe orchestration, pairing dialer |
| `:engine:delivery` | `oc.kmp.library` | F | 2 | send pipeline, offline queue, online marker, receipt journal |
| `:core:testing` | test-only KMP | S core | 0 | `FixtureEngine`, `SseReplay`, `FakeNetworkStatus`, `TestClock`, fixture root constant |
| `:tools:contracts` | `oc.jvm.library` (CLI) | S backend | 1 | recording proxy, fixture redaction check, OpenAPI drift report (**proposed addition** to ADR §5.2 and 03 §1.2; coordinator approves) |

`:android:platform` implements `NetworkStatus` (§6.4). `:androidApp` keeps `res/xml/network_security_config.xml` (§4.7).
The `oc.jvm.library` module `:core:net-okhttp` runs its tests with `test`, not `desktopTest`.

### 2.2 File plan (create in this order inside each module)

```
core/security/src/commonMain/kotlin/io/github/eslamasabry/oc/core/security/
  address/IpLiteral.kt            IPv4/IPv6 literal parser (brackets, zone, trailing dot, IPv4-mapped)
  address/AddressRules.kt         03's HostClass + AddressRules.classify(host) (§4.1)
  address/ServerUrlRules.kt       normalize/validate for OpenCode, Paseo, Codex, Gas City; UrlRejection enum
  address/ConnectionAdvice.kt     explainConnectionAddress(input): ConnectionAdvice
  trust/TrustOrigin.kt            origin(scheme://host:port), ws→http and wss→https, effective port, IPv6 brackets, lower-case
  trust/CertificatePin.kt         value class Sha256Pin(hex64); parse accepts colons; display "AA:BB:…"
  trust/TransportKind.kt          OpenCodeHttp, OpenCodePty, PaseoWs, CodexWs, Download, FixedOrigin, LoopbackService
  trust/TransportPolicy.kt        decide(kind, url, consentOrigin, pin) / fixedOrigin(feature, url) / loopbackService(port): Outcome<TransportPermit>
core/common/src/commonMain/kotlin/io/github/eslamasabry/oc/core/common/
  json/WireJson.kt  json/TolerantListSerializer.kt  json/LenientDoubleSerializer.kt  json/OpenEnum.kt
  failure/FailureDetail.kt        (NetworkWhy, CertificateWhy, SignInWhy, BusyKind, IncompatibleKind, WireTag, WireExtras; §3.8)
core/model/src/commonMain/kotlin/io/github/eslamasabry/oc/core/model/
  stream/StreamStatus.kt  stream/StreamSignal.kt  net/NetworkState.kt  net/NetworkStatus.kt
core/domain/src/commonMain/kotlin/io/github/eslamasabry/oc/core/domain/
  gateway/*.kt (§11.1)  event/ServerEvent.kt (§11.2)  GatewaySpec.kt  ProbeTarget.kt  ServerCapabilities.kt
core/net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/
  TransportSpec.kt  Transport.kt (Transport + TransportFactory)  HttpClientFactory.kt  Requests.kt  StatusMapping.kt  ErrorBody.kt
  sse/SseFrame.kt  sse/SseParser.kt
  stream/BackoffPolicy.kt  stream/ResumeMode.kt  stream/EventStreamLoop.kt  stream/ByteBudget.kt
  ws/WsSpec.kt  ws/WebSocketConnector.kt  ws/WsSession.kt  ws/WsRpc.kt  ws/WsReconnectPolicy.kt
  gate/NetworkGate.kt
  trace/HttpTrace.kt  download/ResumableDownloader.kt
core/net/src/androidMain/.../OkHttpBindings.kt     (@ContributesBinding: TransportFactory, CertificateInspection)
core/net/src/androidMain/.../OkHttpTransport.kt  OkHttpWebSocketConnector.kt  BoundedWsChannels.kt (the one @OptIn(InternalAPI) file)
core/net/src/desktopMain/.../                      (the same four files; identical bodies)
core/net-okhttp/src/main/kotlin/io/github/eslamasabry/oc/core/net/okhttp/
  JvmTransportSpec.kt  HardenedOkHttp.kt  OneShotBodyInterceptor.kt  RetryAfterStripInterceptor.kt
  PinnedLeafTrustManager.kt  TrustGateInterceptor.kt  CertificateInspector.kt
  JvmWsUpgrade.kt (upgrade + 101 validation, plain OkHttp/Okio types)  JvmErrorKind.kt   (no io.github.eslamasabry.oc.* import, §3.3)
protocol/oc2/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/oc2/
  Oc2Wire.kt (constants)  Oc2Routes.kt  Oc2Dialect.kt  Oc2Transport.kt  Oc2Client.kt  Oc2ClientIds.kt
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
                  gateway/*.kt (incl. questions, host permissions, gen-UI guards, background tasks) mappers/*.kt
                  PaseoReconnect.kt PaseoTerminalFrames.kt PaseoCapabilities.kt PaseoGatewayFactory.kt
protocol/codex/…  CodexTransport.kt CodexGateway.kt CodexReconnect.kt CodexAccountSession.kt CodexCapabilities.kt
                  CodexGatewayFactory.kt
engine/connection/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/
  ConnectionRegistry.kt  ProfileSource.kt  ReconnectParticipant.kt  Reconcile.kt  LinkStatus.kt  BusyPoller.kt
  AutomationGate.kt (§12.9)  ServerRunsToolsHolder.kt  DemoGraph.kt (§12.8)
```

Each protocol module builds its own `TransportSpec` from the `GatewaySpec` it receives and creates its `Transport` with
the injected `TransportFactory` (§3.3, §11.1); nothing above `:protocol:*` sees a `TransportSpec` or a `Transport`.
`:engine:connection` drives the transport through the gateway (`ServerGateway.linkControl`, `close()`).

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

- A `ConnectionGraph` (one per live connection, ADR §9.1) owns exactly one `Transport` (§3.3): one `OkHttpClient`, the
  Ktor engine over it, the WebSocket connector derived from it, and one Ktor `HttpClient`. Several graphs can be alive
  at once (primary server, side servers, the agents backend, monitors).
- `ConnectionGraph.close()` order: stop admission → cancel the event stream job → drain writer lanes → `HttpClient.close()`
  → `transport.close()`, which runs `engine.close()` (Ktor builds `HttpClient(engine)` with `manageEngine = false`
  [V ktor-client-core 3.6.1 bytecode], so `HttpClient.close()` alone leaves the engine's `requestsJob` waiter alive
  [V `OkHttpEngine.kt:47-58`]) and then `HardenedOkHttp.shutdown(client)` (evict pool, shut down the dispatcher's
  executor). `ServerGateway.close()` runs this order over its `Transport`; `:engine:connection` reaches it only through
  the gateway (`close()`, `linkControl`, §11.1) and never sees `:core:net-okhttp`.
- Short-lived graphs (schedules, widget actions, probes, monitors) use the same builder and the same trust (§12.7).
  Row B-PROTO-148: opening and closing 100 ephemeral graphs leaves no engine, socket, dispatcher thread or coroutine.

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
            .protocols(listOf(Protocol.HTTP_1_1))                          // N4-D19: Flutter parity; no h2, no 421 resend
            .proxy(Proxy.NO_PROXY)                                         // Flutter: findProxy = DIRECT
            .retryOnConnectionFailure(false)                               // never resend a POST (also blocks the 408 path)
            .followRedirects(false).followSslRedirects(false)              // Basic auth never crosses origins
            .connectTimeout(spec.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(spec.readTimeoutMs, TimeUnit.MILLISECONDS)         // per read: an inactivity timeout, not a total
            .writeTimeout(spec.readTimeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor(TrustGateInterceptor(spec, pin))               // application interceptor: runs before any socket
            .addInterceptor(OneShotBodyInterceptor)                        // every non-GET body one-shot; bodyless POST/DELETE get an empty one-shot body
            .addNetworkInterceptor(RetryAfterStripInterceptor)             // drops Retry-After from 503s (OkHttp resends on "Retry-After: 0")
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

    /** WebSocket upgrades: same pool, pin trust manager, trust gate and NO_PROXY; no read timeout once open;
     *  the call timeout bounds only the handshake (OkHttp ends it at RealCall.upgradeToSocket(), `timeoutEarlyExit`). */
    public fun webSocketClient(client: OkHttpClient, handshakeTimeoutMs: Long): OkHttpClient = client.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(handshakeTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

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

Why the two extra interceptors [V OkHttp 5.5.1 `RetryAndFollowUpInterceptor.kt:95-110, 240-286`]: `followUpRequest`
returns the same request for a 503 whose `Retry-After` is `0` and for a 421 on a coalesced connection, independent of
`retryOnConnectionFailure`; the loop stops only when the follow-up body `isOneShot()`. `RetryAfterStripInterceptor`
(a network interceptor, so `RetryAndFollowUpInterceptor` sees the stripped response) removes the header from 503s, which
also covers GETs; `OneShotBodyInterceptor` covers any other follow-up path for mutations. HTTP/1.1 only (N4-D19) makes
connection coalescing, and so the 421 path, impossible.

### 3.3 The transport seam and the Ktor client (`:core:net` commonMain)

The engine side of a connection is one object, so a protocol's gateway can evict, close and classify without seeing
`:core:net-okhttp` (ADR §6.1), and `:engine:connection` reaches those acts through the gateway's `LinkControl`. This replaces 03 §10.7's `HttpClientFactory.clientFor` / `onNetworkChanged` /
`TransportFailures` sketch (shared pool) with per-connection clients (N4-D1).

```kotlin
/** One per connection; built once from one hardened OkHttpClient (androidMain/desktopMain: OkHttpTransport). */
public interface Transport {
    public val engine: HttpClientEngine
    public val ws: WebSocketConnector          // derived from the same client: same pin, trust gate, NO_PROXY (§5.2)
    public fun classify(error: Throwable): Failure   // JVM exception kinds (JvmErrorKind) → 03 Failure; cancellation rethrown
    public fun evictIdle()                     // network change (§6.4)
    public suspend fun close()                 // engine.close(), then HardenedOkHttp.shutdown(client)
}
public fun interface TransportFactory { public fun create(spec: TransportSpec): Transport }   // androidMain/desktopMain bind it

public class TransportSpec(
    public val permit: TransportPermit,        // from :core:security TransportPolicy: origin, cleartextPermitted, pin, kind
    public val connectTimeoutMs: Long = 8_000,
    public val readTimeoutMs: Long = 120_000,  // inactivity between reads (Flutter Dio receiveTimeout semantics)
)

public class HttpClientFactory(private val trace: HttpTrace) {
    public fun create(transport: Transport): HttpClient = HttpClient(transport.engine) {
        expectSuccess = false              // status codes become Failure values in StatusMapping
        followRedirects = false            // Ktor's HttpRedirect is ON by default [V network-data §0]
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 120_000  // inactivity, like Dio's 2 min receiveTimeout [R]; no default total
            // requestTimeoutMillis is NOT set: a big export or file read on slow data must not be cut off.
            // Totals are per call, only where §3.4 names one.
        }
        install(UserAgent) { agent = "OpenCodeMobile" }   // no version, no device facts
        install(trace.plugin)              // OCTRACE-style spans ≥ 50 ms; path template only, never query or body
        // No ContentNegotiation, no HttpRequestRetry, no Logging plugin (Konsist rule, §11.7).
    }
}
```

The androidMain/desktopMain `TransportFactory` converts the spec to `JvmTransportSpec` and returns `OkHttpTransport`:
`client = HardenedOkHttp.build(...)`, `engine = HardenedOkHttp.ktorEngine(client)`,
`ws = OkHttpWebSocketConnector(client)` (each `connect` derives `HardenedOkHttp.webSocketClient(client, wsSpec.handshakeTimeoutMs)`).

**Which side of the module line each piece sits on (audit 04+05 #3, 2026-11-01).** `:core:net-okhttp` has no project
dependencies, so it can name neither `Transport`, `WebSocketConnector`, `WsSpec`, `WsSession`, `Outcome` nor
`Failure`. The split is therefore:

| Piece | Module | Signature uses only |
|---|---|---|
| `HardenedOkHttp`, `JvmTransportSpec`, the interceptors, `PinnedLeafTrustManager`, `CertificateInspector` | `:core:net-okhttp` | OkHttp, JDK, plain values |
| `JvmWsUpgrade.open(client: OkHttpClient, url: String, headers: List<Pair<String,String>>, subprotocol: String?, handshakeTimeoutMs: Long): JvmUpgradedSocket` (steps 1–3 of §5.2: request, headers, `101` validation) and `JvmUpgradedSocket(source: BufferedSource, sink: BufferedSink, protocol: String?, cancel: () -> Unit)` | `:core:net-okhttp` | OkHttp, Okio, JDK; failures are thrown as `JvmUpgradeException(kind: JvmErrorKind, status: Int?)` |
| `OkHttpTransport : Transport`, `OkHttpWebSocketConnector : WebSocketConnector` (wraps `JvmWsUpgrade`, maps `JvmErrorKind` → `Failure`, then step 4–5 of §5.2: `RawWebSocket` + `WsSessionImpl`), `BoundedWsChannels.kt` | `:core:net` **androidMain/desktopMain** (shared through `oc.jvm.library`'s source dir or one identical file each, as `OkHttpBindings.kt` already is) | `:core:net` types + `:core:net-okhttp` |

So the file plan (§2.2) moves `OkHttpTransport.kt`, `OkHttpWebSockets.kt` (renamed `OkHttpWebSocketConnector.kt`) and
`BoundedWsChannels.kt` from `:core:net-okhttp` to `core/net/src/{androidMain,desktopMain}`, and `:core:net-okhttp` gains
`JvmWsUpgrade.kt`. `ModuleRulesTest` gets a vector: a `:core:net-okhttp` file importing `io.github.eslamasabry.oc.*`
fails. `ktor-websockets` and `ktor-io` are dependencies of `:core:net` (a, d), not of `:core:net-okhttp`.
Konsist: `HttpClient(` (Ktor), `OkHttpClient(`, `OkHttpClient.Builder`, `HttpURLConnection` and `URL.openConnection`
appear only in `:core:net` and `:core:net-okhttp` (§11.7), so every request of the app goes through a `Transport`.

### 3.4 Timeouts

| Call | Connect | Request (total) | Socket read (inactivity) | Source |
|---|---|---|---|---|
| OpenCode 2 REST (default) | 8 s | none | 2 min | Flutter Dio config: `receiveTimeout` is between chunks [R PROTOCOLS-07] |
| `POST /session/{id}/wait` (long poll) | 8 s | 10 min | 10 min | [R] |
| `GET /api/event`, session log (SSE) | 8 s | infinite | **45 s** (3 × heartbeat) | N4-D7 |
| Web search providers / query | 8 s | 15 s / 30 s | same | [R PROTOCOLS-33] |
| Gen-UI history read | 8 s | 10 s deadline, 1 MiB budget | | [R PROTOCOLS-10] |
| Handoff history page | 8 s | 30 s per page; 16 MiB/page; 64 MiB total | | [R] |
| Managed shell list | 8 s | 8 s | | [R PROTOCOLS-29] |
| Turn-run check (`checkTurnStillRuns`) | 8 s | 8 s | | [R STATE-DATA-12] |
| Paseo handshake / default request / create agent / browser launch | 8 s | 20 s / 90 s / 60 s | WS ping 20 s | [V `paseo/transport.dart`] |
| Phone agents helper (`ws://127.0.0.1:4099/ws`) default request | 8 s | **15 s** | WS ping 20 s | [V `phone_agents_host.dart:963`] |
| Phone agents helper health (`GET http://127.0.0.1:4099/api/health`, no password, 200 = up) | 3 s | 3 s + 2 s drain | | [V `phone_agents_host.dart:174-193`] |
| WebSocket handshake (all) | 8 s | 8 s (`callTimeout`, ends at the upgrade) | 0 after the upgrade (pings detect loss) | §5.2 |
| Paseo bounded history (second connection) | 8 s | 10 s, 1 MiB frames | | [V] |
| Codex handshake / request | 8 s | 15 s | WS ping 20 s | [V `codex/transport.dart`] |
| Certificate inspection | 8 s | handshake only | 8 s | [R] |

Per-call overrides use Ktor's `timeout { }` block on the request builder (`HttpTimeoutConfig.INFINITE_TIMEOUT_MS` for
SSE [V class name in 3.6.1 sources]).

### 3.5 Retries

| Case | Automatic retry? | Rule |
|---|---|---|
| Any POST/PUT/PATCH/DELETE | **Never** by the stack (not even on 503 `Retry-After: 0` or 421) | N4-D2. Lost response → `Refused(DispatchUncertain)`; receipts look it up (§13.6). |
| `GET /agent/{id}` → 404 | Twice, 500 ms apart | Server boot after an idle hour (`800f93a4c`) [V] |
| 503 `service_starting` | No | `Busy(Starting)`; the person sees "The server is starting" with Try again; the stream loop retries on its own schedule. |
| Event stream drop | Yes, by `EventStreamLoop`, while `reconnect` is allowed (N4-D21) | §6.2 |
| Paseo/Codex socket drop | Yes, by `WsReconnectPolicy`, while `reconnect` is allowed | §5.5 |
| Paseo/Codex mutation timeout | **Never** | `Refused(DispatchUncertain)` [R AGENTS-BACKENDS-19] |
| Paseo/Codex read timeout | No (caller may re-read) | `TimedOut` |

### 3.6 Building requests

- **Root:** `normalizeServerRoot(url)` trims trailing `/` and a trailing `/api`; every OpenCode 2 path is
  `<root>/api<path>` (B-PROTO-037).
- **Auth:** `Authorization: Basic base64(user:password)`, user `opencode` unless the profile carries a tunnel username;
  header only. A URL with userinfo is refused by the trust gate before any socket opens (B-SEC-153). The old
  `?auth_token=` helper stays deleted (`932210227`).
- **Path segments:** ids and file paths are encoded per segment (`/fs/read/<a>/<b>` keeps `/` between segments, encodes
  inside them). Use Ktor `URLBuilder.appendPathSegments(segments)`; a table test pins `#`, `%`, space, `?`, Arabic and
  `..` cases [U exact API name; compile-checked in WP-04.4]. The same table also runs **through `HardenedOkHttp`
  against mockwebserver3, asserting the recorded request line**: OkHttp's `HttpUrl` resolves dot segments before
  sending, so a `MockEngine`-only test cannot see what the device actually sends (a `..` or `%2e%2e` segment must reach
  the server encoded, never resolved).
- **Query:** null values dropped; empty map dropped. Location scoping adds `location[directory]=<abs>` and, when set,
  `location[workspace]=<id>` on the calls listed in PROTOCOLS-08. `/session/{id}…` carries no location **except**
  `POST /session/{id}/view` and `POST /session/{id}/background` (query) [V `ops_sessions.dart:76`, `ops_shells.dart:152`];
  `POST /session` carries it in the **body** as `location: {directory, workspaceID}` [V `client.dart:167`];
  `/session/stats`, `/session/import`, `/permission/saved`, `/project`, `/worktree/{id}` never carry a location.
  `Oc2Routes` marks each route `locationScoped = Query | Body | None` and `Oc2RouteScopeTest` pins these three routes.
  `Oc2Client` captures `(directory, workspace)` when an operation starts and re-checks it after every suspension point
  (PROTOCOLS-08).
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
  its cap is closed unread and becomes `Failure.UnreadableReply`.

### 3.8 Errors: transport and status → `Failure`

Translated once, in `:core:net` (`StatusMapping`) and in `Transport.classify` (JVM exception kinds, `JvmErrorKind`), so
nothing above sees an exception for an expected failure (ADR §9.6). Server text (`message`, HTML bodies, provider
errors) goes only into `details: Redacted?`; the symbolic server tag (`_tag` or `code`) is kept for logic as `WireTag`
and never shown.

There is **one** failure hierarchy: 03's flat, closed `Failure` (03 §10.1). This file does not add a `Net`
sub-hierarchy; it adds detail **fields** to 03's subtypes, one new subtype (`Incompatible`) and four refusal reasons.
These are amendments to 03 §10.1 (listed again in §11.0), frozen with it in slice 0:

```kotlin
// :core:common — amendments to 03 §10.1. Fields have defaults where 03's other callers construct the type.
public data class Network(val why: NetworkWhy = NetworkWhy.Unknown, override val details: Redacted?) : Failure
public enum class NetworkWhy { Offline, Dns, Refused, Reset, NoRoute, Unknown }
public data class TimedOut(override val details: Redacted?) : Failure                         // unchanged
public data class Certificate(val why: CertificateWhy, override val details: Redacted?) : Failure
public enum class CertificateWhy { Changed, Expired, Untrusted }
public data class SignIn(val why: SignInWhy, override val details: Redacted?) : Failure
public enum class SignInWhy { PasswordMissing, PasswordRejected, TokenMissing, TokenRejected, HostNotAllowed, Forbidden }
public data class Busy(val kind: BusyKind = BusyKind.Overloaded, val tag: WireTag? = null, override val details: Redacted?) : Failure
public enum class BusyKind { Starting, Stopping, Failed, Overloaded, RateLimited, SessionBusy }
public data class NotFound(val tag: WireTag? = null, val requestId: RequestId? = null, override val details: Redacted?) : Failure
public data class Conflict(val tag: WireTag? = null, val requestId: RequestId? = null, override val details: Redacted?) : Failure
public data class Rejected(val status: Int?, val tag: WireTag? = null, val extras: WireExtras = WireExtras.None,
                           override val details: Redacted?) : Failure
public data class Server(val status: Int?, val tag: WireTag? = null, override val details: Redacted?) : Failure
public data class Incompatible(val kind: IncompatibleKind, override val details: Redacted?) : Failure   // NEW subtype
public enum class IncompatibleKind { LegacyOpenCode1, UnsupportedServerVersion, ProtocolMismatch }
// RefusalReason (append-only) gains: OriginChanged, CredentialsInUrl, PinNeedsHttps, HostNotAllowedForBackend.
@JvmInline public value class WireTag(public val raw: String)            // logic only; never shown
public sealed interface WireExtras { public data object None : WireExtras; public data class MaxBytes(val max: Long) : WireExtras }
```

| Input | 03 `Failure` | Flutter tag / category it replaces |
|---|---|---|
| no default network, Public or Tailnet host (§6.4) | `Network(Offline)` | network |
| `UnknownHostException` | `Network(Dns)` | "failed host lookup" string checks [R] |
| `ConnectException` refused | `Network(Refused)` | `suggestsMissingServer` |
| reset, EOF mid-call | `Network(Reset)` | network |
| no route | `Network(NoRoute)` | network |
| connect / read timeout, WS handshake `callTimeout`, Paseo/Codex read timeout, 408 | `TimedOut` | timedOut |
| pin mismatch | `Certificate(Changed)` | `ClientCertificateChanged` |
| matching pin, outside validity | `Certificate(Expired)` | (distinct error, B-SEC-007) |
| PKI failure, no pin | `Certificate(Untrusted)` | `ClientCertificateInvalid` |
| http, non-loopback, no consent | `Refused(CleartextNotConfirmed)` | `ClientCleartextNotAllowed` |
| origin differs from the profile (incl. any redirect target) | `Refused(OriginChanged)` | `ClientOriginChanged` |
| URL with userinfo | `Refused(CredentialsInUrl)` | B-SEC-153 |
| pin on an `http` origin | `Refused(PinNeedsHttps)` | B-SEC-153 |
| Paseo URL on a Public host | `Refused(HostNotAllowedForBackend)` | B-SEC-113 |
| 401, or tag `UnauthorizedError` (even empty body) | `SignIn(PasswordMissing` if none was sent, else `PasswordRejected)` | `Api2AuthRequired` |
| 403 (OpenCode, PTY CSRF guard, revoked BYO ticket) | `SignIn(Forbidden)` — words only; **does not** set `passwordRejected` | signIn |
| 403 on Paseo upgrade without password | `SignIn(HostNotAllowed)` | host not in daemon allowlist [R] |
| Paseo/Codex `authentication` kind, Paseo close 4401 | `SignIn(PasswordRejected` / `TokenRejected)` | authentication |
| 503 or tags `ServiceUnavailableError`, `service_starting`, `service_stopping`, `service_failed` | `Busy(Starting/Stopping/Failed)` | `Api2Unavailable` |
| 429 | `Busy(RateLimited)` | busy |
| tag `SessionBusyError` (409) | `Busy(SessionBusy)` | busy |
| WS 65th pending request | `Busy(Overloaded)` | overloaded |
| 404, 410 | `NotFound(tag, requestId)` | notFound |
| 409 (other tags) | `Conflict(tag, requestId)` | conflict |
| staged revert pending (app check or tag `SessionRevertPending`) | `StagedRevert` (03) | stagedRevert |
| other 4xx | `Rejected(status, tag, extras)` | rejected |
| ≥ 500 | `Server(status, tag)` | server |
| undecodable / over-cap body | `UnreadableReply` | unreadableReply |
| WS mutation timeout or link lost after send; lost POST response | `Refused(DispatchUncertain)` | deliveryUnknown |
| answer on an old epoch / scope changed mid-call | `Refused(StaleScope)` | staleRequest |
| 404 non-session / 405 / 501 on an optional feature; facet missing | `Unavailable(FeatureId)` + latch (§11.5) | per-feature latches |
| Paseo daemon did not answer | `ComputerDidNotAnswer` (03) | computer |
| OpenCode 1 health shape | `Incompatible(LegacyOpenCode1)` | `unsupportedLegacy` |
| Paseo close 4003 / `hello.rejected` | `Incompatible(ProtocolMismatch)` | incompatibleProtocol |
| cancellation | **not a Failure**: rethrown | — |

**Who sets `passwordRejected`** (Flutter `_noteAuthFailure` [V `connect.dart:561`]): only a 401 / `UnauthorizedError`
and the Paseo/Codex `authentication` kinds. A 403 produces words only and stops nothing. What happens to the retry loop
after a 401 is in §4.8.

`:ui:words` maps every subtype with an exhaustive `when` (ADR §9.6). The NAV rows B-NAV-007/008/009 (loopback offers
Retry, Tailscale address says "check Tailscale", after three tries say retrying will not start a server) are word rules
over these values plus the host class (§4.1); they are tested in `:engine:connection` (`ConnectionFailureExplainTest`).

### 3.9 Tracing

`HttpTrace` (`:core:net`) records a span per request: `http <backend> <METHOD> <path template>` with attributes
`status`, `ms`, `bytes`; logged through `AppLog` when ≥ 50 ms (Flutter `OCTRACE`, PROTOCOLS-45). Marks:
`events.connected`, `events.first`, `events.first.session_log`, `app.first_connected`, `messages.page`. The template
replaces ids with `{id}`; query strings, bodies and headers are never recorded (PTY tickets ride in the query).

### 3.10 Resumable pinned downloads

`ResumableDownloader` (`:core:net`) is **the only downloader in the app**: the switch-release updater (ADR §9.9), voice
packs, the built-in Linux runtime payloads (11's `RuntimeInstall.download` calls it; it does not keep its own
`HttpURLConnection` loop from `BuiltinLinux.kt:4047`), and AI Team assets later. Owners of those features own their
URLs and SHA-256 pins; this file owns the mechanism.

Almost every source redirects: GitHub `/releases/download/…` always answers 302 to `release-assets.githubusercontent.com`,
and Hugging Face `/resolve/…` answers 302 to its CDN [V Flutter follows up to 5 hops by hand,
`lib/voice/model_download.dart:230-269`]. So the downloader follows redirects **itself**; the stack never does
(N4-D2, §4.6):

1. Every hop gets a fresh permit from `TransportPolicy.decide(TransportKind.Download, hopUrl)` and its own
   credential-free `Transport` (no `Authorization`, no cookies, never the caller's headers on a later hop). HTTPS only on
   every hop; a hop to `http` fails `Refused(OriginChanged)` before any byte is sent to it.
2. 301, 302, 303, 307 and 308 with a `Location` are followed; at most **5** hops; a 6th, or a missing `Location`, fails.
   The redirect body is drained and closed.
3. `Range: bytes=<partLength>-` is sent **only to the final URL** (the one that answers 2xx/416), never to a hop.
4. 206: `Content-Range` must start at the part file's length, else the part file is deleted and the download restarts
   from zero; 200: restart from zero; 416: the part file is complete.
5. SHA-256 of the whole file is checked before the atomic rename; a mismatch deletes the part file; no credentials ever.

Tests (mockwebserver3 through `HardenedOkHttp`, plus MockEngine for the state machine; row B-PROTO-144): a 302 chain to
another host succeeds; a redirect to `http` is refused; 6 hops are refused; resume after a redirect sends `Range` only
to the final host; a wrong `Content-Range` start restarts from zero.

---

## 4. Trust and address policy (localhost, Tailscale, cleartext, pins)

### 4.1 One classifier

03's names (03 §10.2), one implementation:

```kotlin
public enum class HostClass { Loopback, PrivateLan, Tailnet, LinkLocal, Public, Invalid }
public object AddressRules {
    /** Loopback: exact strings only (below). Other classes: brackets, IPv6 zone and a trailing dot are ignored and
     *  IPv4-mapped IPv6 is judged by its IPv4. */
    public fun classify(host: String): HostClass
}
```

| Class | Exact rule (ported from Dart; vectors from `private_network_cleartext_test.dart`, `tailscale_profile_test.dart`) |
|---|---|
| Loopback | exactly `localhost`, `127.0.0.1`, `::1`, compared case-insensitively **after removing brackets only**. No other normalisation: `localhost.` (which a resolver may send upstream) and `::ffff:127.0.0.1` are **not** Loopback, as in Flutter's `isLoopbackHost` [R `loopback_host.dart`]. One predicate on purpose. |
| Tailnet | IPv4 in `100.64.0.0/10` (`b0 == 100 && (b1 and 0xC0) == 0x40`), or a name ending `.ts.net`. IPv4 only, as Flutter [V `gascity_probe.dart:26-38`, `connection_failure.dart:57-59`]; the IPv6 Tailscale range `fd7a:115c:a1e0::/48` is **not** Tailnet (it is PrivateLan through `fc00::/7`, which already gets the private-network consent sentence). |
| PrivateLan | IPv4 10/8, 172.16/12, 192.168/16; IPv6 `fc00::/7`; names `*.local` with a non-empty label (not bare `local`) |
| LinkLocal | IPv4 169.254/16; IPv6 `fe80::/10` (Flutter counted these as private; policy treats LinkLocal exactly like PrivateLan) |
| Invalid | empty host, unparsable literal, a host with whitespace or control characters |
| Public | everything else, including `0.0.0.0` and every other name |

`IpLiteral.parse` is written in common code (no `java.net`): dotted IPv4 (4 decimal parts, 0–255, no leading `+`),
IPv6 with `::` compression, embedded IPv4 tail and zone suffix. Table tests cover each vector of the two Dart files plus
`100.63.255.255` (Public), `100.128.0.0` (Public), `100.64.0.0` and `100.127.255.255` (Tailnet), `localhost.` and
`::ffff:127.0.0.1` (not Loopback), `[fd7a:115c:a1e0::1]` (PrivateLan).

### 4.2 Policy per backend

| Client (`TransportKind`) | Allowed schemes | Allowed hosts | Plain-text needs consent? | Rows |
|---|---|---|---|---|
| OpenCode 2 profile (`OpenCodeHttp`) | `https`, `http` | any | `http` to anything but Loopback needs per-origin consent, **including PrivateLan, LinkLocal and Tailnet** (the validator accepts a private `http` URL; connecting still needs consent); wording: PrivateLan, LinkLocal and Tailnet use the private-network sentence, Public the stronger one (N4-D4) | B-SEC-119..125, 153, 154 |
| Paseo profile (`PaseoWs`) | `ws`, `wss` | **Loopback or Tailnet only**, even over `wss` (owner rule: nothing on the open internet) | no consent step; `ws` allowed because the host class is restricted | B-SEC-113, 123 |
| Codex profile (`CodexWs`) | `ws` (Loopback only), `wss` otherwise | any for `wss` | `ws` to non-loopback is refused (not consentable) | B-SEC-123, B-AGENT-062 |
| OpenCode PTY WebSocket (`OpenCodePty`) | `ws`/`wss` follows the profile's `http`/`https` | the profile origin | inherits the profile's consent and pin (same `Transport`, §5.2) | B-PROTO-036, 131 |
| Gas City (later) | `https` any; `http` | Loopback or Tailnet for `http` | none | `isOrchestrationUrlAllowed` |
| Quota reader | in-app server: see "Loopback helper ports"; remote server: its profile scheme | remote: the profile origin with Basic | remote inherits the profile's consent | B-PROTO-058, 084 |
| Downloads (`Download`: updater, packs, rootfs) | `https` only, every hop | any; redirects followed by the downloader only (§3.10) | n/a | §3.10, B-PROTO-144 |
| **App-authored fixed origin** (`FixedOrigin`) | `https` only | an exact host allowlist per feature, declared in code: `github.com`, `api.github.com` (device flow, Git page), `registry.modelcontextprotocol.io` (MCP registry), `models.dev` (provider logos), the release-check host, the analytics/report host of section 16 | n/a; no redirects; no credentials except the feature's own bearer token (GitHub); response size cap per feature (default 1 MiB) | B-PROTO-145 |
| **Loopback helper ports** (`LoopbackService`) | `http` | `127.0.0.1` only, on a declared port: 4099 (phone agents helper: health, no auth; WS uses the Paseo row), 4195 (quota collector: Bearer only), 4097 (phone OpenCode server, as an OpenCode 2 profile) | none (Loopback); header-only auth; no redirects | B-PROTO-058, 145 |

`TransportPolicy` (`:core:security`) has three entry points, all returning
`Outcome<TransportPermit>` or a `Refused(…)` failure:
`decide(kind: TransportKind, url, consentOrigin, pin)` for profiles and downloads,
`fixedOrigin(feature: FixedOriginFeature, url)` and `loopbackService(port: LoopbackPort)`. It is the only producer of
`cleartextPermitted = true` and the only producer of a `TransportPermit`. `TransportKind` lives in `:core:security`
(which may see only `:core:common`); `:engine:connection` maps 03's `BackendKind` to it
(`OpenCode2 → OpenCodeHttp`, `Paseo → PaseoWs`, `Codex → CodexWs`).

### 4.3 Cleartext consent

- Stored per profile in the adopted prefs: `oc.cleartextOk.<profileId>` = the confirmed origin string
  (`http://host:port`, lower-case, IPv6 bracketed, explicit port). Swept by the `oc.<what>.<profileId>` rule.
- Valid only while `cleartextOriginOf(profile.baseUrl)` equals the stored value. Changing host, port or scheme asks again
  (B-SEC-121). A different private origin is not covered.
- Every non-loopback `http` origin needs it, private ones included [V `validation.dart:125-131`,
  `private_network_cleartext_test.dart:120-137`]. Explicit vector: `http://192.168.1.20:4096` passes
  `ServerUrlRules.validate` **and** `needsCleartextConfirmation` is true; connecting before consent fails
  `Refused(CleartextNotConfirmed)` with nothing sent (B-SEC-120 as corrected in §15.5, B-SEC-125).
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

Runs for every request and every WebSocket upgrade of the client (the WebSocket connector is derived from the same
client, §5.2), in this order; each failure throws a `TrustRefusal : IOException` that `Transport.classify` turns into
the `Failure` of §3.8:

1. URL has userinfo → `Refused(CredentialsInUrl)`.
2. `origin(url) != spec.origin` → `Refused(OriginChanged)` (covers a changed address and any redirect target). OkHttp
   rewrites `ws://`/`wss://` to `http://`/`https://` before interceptors run, so `TrustOrigin` stores WebSocket origins
   already mapped (`ws→http`, `wss→https`); `TrustOriginTest` pins that a Paseo `ws://127.0.0.1:4099/ws` permit passes
   the gate for its own upgrade.
3. Scheme `http` and `!spec.cleartextPermitted` → `Refused(CleartextNotConfirmed)`.
4. Pin set and scheme `http` → `Refused(PinNeedsHttps)`.
5. `pin.latchedMismatch` → `Certificate(Changed)` (no silent concurrent retry clears it; only a new `ConnectionGraph`
   after the person reviews the new certificate does).

### 4.7 Android network security config

Keep `androidApp/src/main/res/xml/network_security_config.xml` as Flutter has it today: one
`<base-config cleartextTrafficPermitted="true">` element; only the comment changes ("Dart remains the security
boundary" becomes "Kotlin remains the security boundary"). Section 16 (N16-D4) may add exactly one child,
`<certificateTransparency enabled="true"/>`, after its proof P16-2 passes; nothing else may appear under
`base-config`. The manifest keeps `android:usesCleartextTraffic="false"` and
`android:networkSecurityConfig="@xml/network_security_config"`. No `<trust-anchors>` element, so user-added CAs stay
untrusted (targetSdk ≥ 24 default). A `:quality` test pins the elements, not the comment (row B-PROTO-139; the element
list is 16 §1.7 rule 4, and 16's `NetworkSecurityConfigTest` is the same test): tightening it would break Tailscale and
LAN servers; loosening it with user CAs would weaken TLS.

### 4.8 Credentials

- Never in a URL (OpenCode uses Basic headers; the PTY uses a 60 s single-use ticket in the query; Paseo uses a
  subprotocol plus a header plus in-protocol `hello.auth`; Codex a Bearer header on the upgrade).
- Read at graph creation through a `ProfileSource` (§12.1), registered with the `Redactor` on read, held only by the
  transport object:

  | Connection | Secret | Notes |
  |---|---|---|
  | Saved OpenCode 2 server | `pw.<profileId>` (Basic, user `opencode` or the profile's username) | |
  | Saved Paseo daemon, Codex app-server | `oc.codexToken.<profileId>` | Paseo password / Codex bearer |
  | Phone agents helper (runtime-only profile `<profileId>.agents`, `ws://127.0.0.1:4099/ws`) | `oc.agentHostSecret.<profileId>` (64 hex, created by the agents engine on first use) [V `phone_agent_host.dart:3`, `phone_agents_host.dart:796`] | never `oc.codexToken`; 15 s request timeout (§3.4) |
  | BYO host (transient profile) | the device token from `oc.byoHostSecrets.<profileId>`, Basic with username = the profileId | `http://127.0.0.1:<tunnel port>`; never persisted (§12.1) |

- A 401 mid-session (or a Paseo/Codex `authentication` failure) sets `passwordRejected` on the graph and shows the banner
  "Update password for <server name>" (never a modal; "actions name their target"). What the loops do (parity with
  Flutter, [V `sse2.dart:189-222`, `paseo/gateway/lifecycle.dart:61`, `codex/gateway.dart:1016`]):
  the OpenCode 2 event stream **keeps its normal backoff** (1…16 s) and the next `Connected` clears the flag; the
  Paseo and Codex reconnect loops treat `authentication` and `hostRefused` as terminal (§5.5) until the person saves a
  password or taps Try again. A 403 sets nothing (§3.8).
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
    val maxMessageBytes: Long,             // Paseo 8 MiB, Codex 4 MiB, PTY N4-D18 (16 MiB provisional), bounded history 1 MiB
    val pingIntervalMs: Long,              // 20 s
    val pongTimeoutMs: Long = 15_000,
    val handshakeTimeoutMs: Long = 8_000,  // applied as the upgrade call's callTimeout
    val incomingByteBudget: Long = 2 * maxMessageBytes,  // undelivered inbound bytes before the reader suspends
)
/** Not an app-wide binding: each connection's `Transport.ws` (§3.3), so the pin, the trust gate (consent origin,
 *  cleartextPermitted) and NO_PROXY of that connection apply to its sockets. A connector never builds its own client. */
public interface WebSocketConnector { public suspend fun connect(spec: WsSpec): Outcome<WsSession> }

public interface WsSession {
    public val incoming: Flow<WsMessage>          // Text(String) | Binary(ByteArray); pings answered inside
    public suspend fun sendText(text: String): Outcome<Unit>
    public suspend fun sendBinary(bytes: ByteArray): Outcome<Unit>
    public val closeReason: Deferred<WsClose?>     // code + reason (reason is server text → Redacted)
    public suspend fun close(code: Short = 1000)
}
```

`OkHttpWebSocketConnector.connect` in `:core:net` androidMain/desktopMain; steps 1–3 are `JvmWsUpgrade.open` in
`:core:net-okhttp` (plain OkHttp types only, §3.3 split table), steps 4–5 run in `:core:net`:

1. Build the upgrade request on `HardenedOkHttp.webSocketClient(connectionClient, spec.handshakeTimeoutMs)` (§3.2):
   `protocols(HTTP_1_1)`, `readTimeout(0)`, `callTimeout(handshakeTimeoutMs)`. It shares the connection's pool, pin trust
   manager, hostname verifier, `TrustGateInterceptor` (§4.6) and `NO_PROXY`. The call timeout bounds the handshake (a
   server that accepts TCP and stays silent fails `TimedOut` at 8 s) and stops counting once OkHttp hands over the
   socket (`RealCall.upgradeToSocket()` → `timeoutEarlyExit()` [V `RealCall.kt:478-485`]). The PTY socket of a pinned
   self-signed server therefore passes the pin exactly as its REST calls do (Flutter builds it from
   `createServerHttpClient(cleartextConfirmedOrigin, certificatePinSha256)` [V `ops_dev.dart:286-296`]).
2. Headers: `Connection: Upgrade`, `Upgrade: websocket`, `Sec-WebSocket-Version: 13`, a fresh 16-byte base64
   `Sec-WebSocket-Key`, optional `Sec-WebSocket-Protocol`, the caller's auth headers. **No** extensions header.
3. Validate the response, in this order (each failure → a `Failure` of §3.8, no retry): status `101` (401 → `SignIn`;
   403 → `SignIn(HostNotAllowed)` or `SignIn(PasswordRejected)` per Paseo rules, `SignIn(Forbidden)` elsewhere); `Upgrade: websocket` and `Connection: Upgrade`;
   `Sec-WebSocket-Accept == base64(sha1(key + 258EAFA5-E914-47DA-95CA-C5AB0DC85B11))`; **no**
   `Sec-WebSocket-Extensions`; if a subprotocol is echoed it must equal ours.
4. Wrap `response.socket.source/sink` into Ktor `ByteReadChannel`/`ByteWriteChannel` (JVM stream adapters) and call
   `RawWebSocket(input, output, maxFrameSize = spec.maxMessageBytes, masking = true, coroutineContext = ioScope,
   channelsConfig = boundedWsChannels())`. Ktor's default `channelsConfig` is `WebSocketChannelsConfig.UNLIMITED`
   [V `RawWebSocketCommon.kt:38`], so frames up to the cap would queue without bound while the consumer decodes.
   `boundedWsChannels()` lives in `BoundedWsChannels.kt`, the **one** file with `@OptIn(InternalAPI::class)` (the
   `WebSocketChannelsConfig()` constructor is `@InternalAPI` [V `WebSocketChannelsConfig.kt:133`]): incoming
   `bounded(8, SUSPEND)`, outgoing `bounded(64, SUSPEND)`. Konsist allows `InternalAPI` only in that file. If a Ktor
   bump removes the constructor, the fallback is to drain `incoming` at once into `WsSessionImpl`'s byte-budgeted
   channel.
5. `WsSessionImpl` (common) sits on the raw session: answers `Ping` with `Pong`, sends a `Ping` every `pingIntervalMs`
   and closes with 1001 if no `Pong` within `pongTimeoutMs`, reassembles fragments with a **running total cap**
   (`maxMessageBytes`; over cap → close 1009 and `Failure.UnreadableReply`), answers `Close` and completes
   `closeReason`. Assembled messages enter `incoming` through a `ByteBudget` (`incomingByteBudget`): the reader suspends
   while undelivered bytes exceed the budget, so TCP pushes back instead of the heap growing.

Fallback if P-N1 fails: an own RFC 6455 framer (about 350 lines) in `:core:net` common over the same upgraded socket.
Not acceptable as a fallback: OkHttp's own WebSocket (no inbound cap, forced deflate).

### 5.3 Settings per protocol

| | Paseo | Codex | OpenCode PTY |
|---|---|---|---|
| URL | `<base>/ws` | profile URL | `ws(s)://<host>/api/pty/{id}/connect?ticket=…&cursor=<n>&location[directory]=…` |
| Auth | `Sec-WebSocket-Protocol: paseo.bearer.<pw>` + `Authorization: Bearer <pw>` (only when a password is set) + `hello.auth {kind:"password"}` | `Authorization: Bearer <token>` | Basic header **and** the one-time ticket (`POST /pty/{id}/connect-token` with header `x-opencode-ticket: 1`, 60 s) |
| Message cap | 8 MiB (1 MiB on the bounded-history connection) | 4 MiB | N4-D18: 16 MiB provisional, set from the P-N4 replay measurement (Flutter: no cap, deflate offered) |
| Ping | WS ping 20 s + app `ping` 20 s | WS ping 20 s | WS ping 20 s |
| Frames | one JSON object per text frame; binary `[opcode, slot, …payload]` = terminal output | one JSON-RPC message per text frame | server→client binary = raw output; first byte `0x00` = meta JSON `{"cursor":n}`; client→server **text** frames of raw input |
| Close codes | 4401 = wrong password; 4003 = protocol mismatch | — | 1000 = process ended; 4404 = not found/exited |
| Max pending requests | 64 | 64 | n/a |

### 5.4 `WsRpc`: shared request/response bookkeeping (Paseo and Codex)

- `requestId` generation per protocol (Paseo `m<epochMs>_<n>`, Codex JSON-RPC integer ids).
- **Connection epochs:** every request is stamped with the epoch of the socket it was written on; an answer from another
  epoch resolves as `Refused(StaleScope)`. A reconnect (§5.5) increments the epoch and fails all pending requests of the
  old one.
- **Read vs mutation timeout:** each request declares `kind = Read | Mutation`. A read that times out → `TimedOut`
  (caller may re-read). A mutation that times out, or whose socket drops after the frame was written →
  `Refused(DispatchUncertain)`, never retried (B-AGENT-062, 066).
- At most 64 pending; the 65th fails at once with `Busy(Overloaded)` without writing.
- In-order delivery: one reader coroutine; JSON decoding for frames ≥ 64 K chars runs on `AppDispatchers.default` but
  results are handed over in arrival order (Flutter's background-decode inbox [R]).

### 5.5 WebSocket reconnect (Paseo, Codex)

`WsReconnectPolicy` (`:core:net`) is used by `PaseoReconnect` and `CodexReconnect`; it is a port of Flutter's two loops
[V `paseo/gateway/lifecycle.dart:40-110`, `codex/gateway.dart:966-1040`], not the SSE backoff of §6.2.

| Rule | Value |
|---|---|
| Delay | `1 s << clamp(attempt, 0, 4)`: 1, 2, 4, 8, 16 s, then 16 s |
| Reset | attempt = 0 after a **successful recovery read** (below), not after a stable period |
| Gate | runs only while `reconnectAllowed` (N4-D21); a non-loopback socket waits on `NetworkGate` like a Public/Tailnet stream (N4-D8) — this covers a remote Paseo daemon over Tailscale |
| Terminal failures | `authentication`, `hostRefused` (Paseo) and `authentication` (Codex): status `Stopped(SignIn(...))`, listening stops, no more tries until Try again or a saved password |
| Status | `StreamStatus.Reconnecting(attempt, nextAttemptAtMs)` / `Disconnected` after 6, the same quiet model as SSE (§12.4); `nextAttemptAtMs` is the countdown Flutter exposes as `nextAttempt` |
| Recovery read on a fresh socket | Paseo: `sessions()` (one page) — which re-subscribes and makes the daemon report pending permissions and questions (the "list before requests" trap, AGENTS-BACKENDS-44); Codex: `sessionPage(limit = 200)`; then pending permissions/questions are re-read |
| Announce grace | "connected" is announced when the recovery read finishes **or after 3 s**, whichever is first, so a list read that hangs behind a long tool call does not keep the banner up [V `lifecycle.dart:99`] |
| Turn state | busy/turn state is cleared only when the socket epoch changed since the last reconcile (`reconciledEpoch`), never on a same-epoch re-read |
| Codex extras | re-`thread/resume` every thread the app had open; retire never-dispatched empty threads it created |
| Codex pending approvals | cannot be reloaded after a drop (no list operation, §9); they are never answered on the new socket |

Tests are ported from `paseo_gateway_test`, `codex_transport_test` and `codex_gateway_test` (B-AGENT-066 and the Paseo
reconnect rows), in WP-04.10 and WP-04.13, under virtual time with `FakeNetworkStatus` and a fake `reconnectAllowed`.

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
public class SseFrame(public val event: String?, public val id: String?, public val data: String) {
    public val sizeBytes: Int get() = data.length * 2   // budget unit (UTF-16 in memory), see §6.6
}
/** No callback: the reader coroutine owns the frames and does the suspending `send` (a non-suspending callback could
 *  only `trySend`, which drops or fails when the queue is full). Amends 03 §10.7's `onFrame` signature. */
public class SseParser(private val maxFrameBytes: Int = 16 shl 20) {
    public fun feed(bytes: ByteArray, offset: Int, length: Int): List<SseFrame>   // frames completed by this chunk
    public fun flush(): SseFrame?
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
| start (`start()` after subscribers, §6.5) | — | `Connecting` | attempt = 0 |
| `Connecting` / `Reconnecting` | gate says offline (Public or Tailnet host) | `WaitingForNetwork` | no attempt spent; resume on network |
| any waiting state | HTTP 200 + `text/event-stream` | `Connected` | `nextAttemptAt = null`; start 30 s stability timer (resets attempt to 0); trace `events.connected`; caller reconciles (§12.3) |
| `Connected` | first frame | `Connected` | trace `events.first` |
| `Connected` | clean end of body | `Reconnecting` | `flush()` the parser; attempt++ (policy); schedule |
| `Connected` | IO error, reset, 45 s stall | `Reconnecting` / `Disconnected` (label when attempt ≥ 6) | discard partial frame; attempt++; `nextAttemptAt` set **before** the status is published |
| any | 401 / `UnauthorizedError` | `Reconnecting`/`Disconnected` | **backoff continues** (Flutter parity, §4.8); `lastFailure` becomes `SignIn(...)`, so the graph sets `passwordRejected`; the next `Connected` clears both |
| any | 404 / 5xx / refused / timeout | `Reconnecting`/`Disconnected` | backoff |
| waiting for delay | network changed / `retryNow()` | `Reconnecting` | cancel the delay, attempt = 0, connect now |
| `Connected` (non-loopback) | default network changed (§6.4) | `Reconnecting` | cancel the body read, attempt = 0, connect now |
| any waiting state | `reconnectAllowed` is false | `Disconnected(attempt, nextAttemptAtMs = null)` | no automatic try (no next attempt is scheduled); `retryNow()` (a person's Try again) still dials once; data and caches are kept (Flutter `_stopDisallowedReconnect`); `LinkStatus` says reconnecting is off for this server |
| `Connected` | `reconnectAllowed` turns false | `Connected` | nothing now; after the next drop the row above applies |
| any | owner scope cancelled | (none) | cancel request; no further status |

```kotlin
public class EventStreamLoop(
    private val client: HttpClient,
    private val request: (attemptCursor: Long?) -> HttpRequestBuilder,   // rebuilt every try: auth, after=, Last-Event-ID
    private val policy: BackoffPolicy,
    private val resume: ResumeMode,
    private val gate: NetworkGate,
    private val hostClass: HostClass,
    private val reconnectAllowed: StateFlow<Boolean>,  // N4-D21, from GatewaySpec (§12.9)
    private val clock: Clock,
    private val random: Random,
) {
    /** 03's StreamSignal<T> (`:core:model`): Status(status) | Item(frame). The connect generation is the number of
     *  Status(Connected) signals seen so far; no separate field. Cold flow: collecting starts the loop. */
    public fun signals(): Flow<StreamSignal<SseFrame>>
    public val lastFailure: StateFlow<Failure?>   // why the last try failed (401 → SignIn); null after Connected
    public fun retryNow()                 // after password update, manual "Try again", network change
}
```

### 6.3 Stream status

```kotlin
// :core:model — amends 03 §10.3: StreamStatus becomes this sealed type (03's 4-value enum is the first four states);
// 03's StreamSignal.Status(status, nextAttemptAtMs) becomes Status(status): the countdown lives inside the state.
public sealed interface StreamStatus {
    public data object Connecting : StreamStatus
    public data object Connected : StreamStatus
    public data class Reconnecting(val attempt: Int, val nextAttemptAtMs: Long?) : StreamStatus
    public data class Disconnected(val attempt: Int, val nextAttemptAtMs: Long?) : StreamStatus   // label after 6 failures; null = not retrying
    public data object WaitingForNetwork : StreamStatus
    public data object Suspended : StreamStatus                                                 // app in background, nothing watching
    public data class Stopped(val failure: Failure) : StreamStatus                              // Paseo/Codex terminal sign-in, incompatible server
}
```

`nextAttemptAtMs` is a real schedule ("trying again in 12 s" counts down to it); it is set when the delay is chosen,
kept while that try is in flight, and null while connected (Flutter `nextAttempt` [V]). The UI never sees this type
directly: `:engine:connection` turns it into the quiet `LinkStatus` (§12.4).

### 6.4 Network and lifecycle gating

```kotlin
// :core:model (03 owns the module; frozen in slice 0). Consumed by :core:net, :protocol:*, engines and :android:platform.
// Replaces 03 §10.4's DeviceFacts.network: StateFlow<NetworkFact> (one network reading in the app, not two).
public interface NetworkStatus { public val state: StateFlow<NetworkState> }
public data class NetworkState(
    val online: Online,          // Yes (INTERNET ∧ VALIDATED), No (no default network), Unknown
    val transport: NetTransport, // Wifi, Mobile, Other, Unknown (VPN, e.g. Tailscale, or wifi+cellular → Unknown)
    val onWifi: Boolean?,        // the active network carries TRANSPORT_WIFI, VPN included (Tailscale over Wi-Fi = true);
                                 // Flutter's monitorNetworkPolicy reading [V MainActivityBackgroundChannel.kt:39-43]
    val metered: Boolean?,
    val defaultNetworkId: Long?, // changes when the default network changes
)
// :core:net
public interface NetworkGate {
    /** Returns at once for Loopback, PrivateLan and LinkLocal; suspends while online == No for Public and Tailnet. */
    public suspend fun awaitUsable(hostClass: HostClass)
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
| `online` becomes `No` | Public and Tailnet streams and sockets move to `WaitingForNetwork` at the end of the current attempt; nothing new is dialed to them; delivery stops flushing to them |
| default network id changes (Wi-Fi ↔ mobile, Tailscale VPN up/down) | for every live graph whose host class is **not Loopback**, `ConnectionRegistry` calls `linkControl.onNetworkChanged()`: `transport.evictIdle()`, every *Connected* stream and WebSocket is cancelled and reconnects at attempt 0, every waiting one gets `retryNow()`; Loopback graphs (the phone server) are untouched, so a Wi-Fi or Tailscale flap costs it nothing (row B-PROTO-138). Only while `reconnectAllowed` (N4-D21). |
| `online` returns to `Yes`/`Unknown` | same as a change |
| Loopback, PrivateLan, LinkLocal hosts | never gated; a "no network" reading is advice only [R ANDROID-PLATFORM-42] (a hotspot or USB-tether server is reachable without a default network) |

- Lifecycle: `LinkDemand` from `:engine:connection` (input from `:android:background`): `Foreground`,
  `BackgroundWatching` (the short `dataSync` "watching this reply" service runs, or Stay connected is on),
  `BackgroundIdle`. `BackgroundIdle` runs in this order (Flutter `suspendForLifecycle` [V `lifecycle.dart:47-58`]):
  1. flush every answer still in its Undo window (`:engine:requests` `DelayedAnswers.flushNow()`, contributed as a
     `SuspendParticipant`) through the still-live transport, awaited up to 5 s ("the app may not wake"; an Allow tapped
     just before leaving must reach the server, or the tool waits for the server's 60-minute idle sweep);
  2. suspend the agents backend graph and every side graph;
  3. cancel the streams of every graph; they report `Suspended`.
  On return they reconnect with the 4 s quiet grace (§12.4), only while `reconnectAllowed` (N4-D21). Integration test
  (WP-04.11): an answer in its Undo window, then `BackgroundIdle`, reaches the server exactly once.

### 6.5 One stream, two views (N4-D9)

`Oc2EventChannel` opens one `EventStreamLoop` on `/api/event` per graph, decodes once and adapts once (§7.6). It does
**not** use a shared flow: a `MutableSharedFlow(replay = 0)` drops everything emitted before a subscriber exists, and
with `SUSPEND` the slowest collector would gate every other one (Flutter avoided both with two independent channels
[V `gateway.dart:804-828`, `gateway_events.dart:905-925`]). Instead:

- Each view has its **own bounded queue** (capacity 256 envelopes and a 16 MiB byte budget, §6.6) fed by the one
  decoder:
  - `EventScope.Location`: envelopes whose `location.directory` equals the graph's current directory, plus envelopes
    without a location (Flutter filter rule [V `gateway_events.dart:917-925`]);
  - `EventScope.All`: everything, with `directory/project/workspace` stamped (attention for other projects, chat-feed
    refresh, `installation.*`, `worktree.*`).
- **Subscribe, then start.** `ConnectionRegistry` builds the graph, lets its consumers (transcript, requests,
  attention, connection's own reconcile) collect `events(scope)`, and only then calls `EventGateway.start()`. Nothing
  is emitted before `start()`, so no durable event is lost between `connect()` and the first collector (ADR §9.5).
- **Overflow is never silent.** If one view's queue is full (its consumer stalled past the budget), that view discards
  its backlog and delivers `ServerEvent.ResyncNeeded` next; its consumers reconcile by refetch exactly as after a
  reconnect (§12.3). The other view and the physical stream are untouched; the event is counted in diagnostics.
- Both views share the stream's status (`EventGateway.status`). Rows: B-PROTO-134 (one HTTP request serves both views)
  and B-PROTO-147 (with the `All` consumer stalled, `Location` deltas still arrive within the 50 ms publish window;
  overflow of `All` yields `ResyncNeeded` on `All` only).

### 6.6 Backpressure and threads

- Reader: one coroutine on `AppDispatchers.io` reads `bodyAsChannel()` into a 16 KiB buffer, calls
  `parser.feed(...)` and **itself** `send`s each completed frame (the parser has no callback, §6.1).
- Frames go through `Channel<SseFrame>(capacity = 16, SUSPEND)` to the decoder on `AppDispatchers.default`, guarded by
  a 32 MiB `ByteBudget` (a semaphore over `SseFrame.sizeBytes`, released after decode). Worst case in memory is
  therefore about 32 MiB plus one frame in decode, not 4096 × 16 MiB. A stalled consumer suspends the reader, TCP
  pushes back, the server may drop a slow consumer, and the loop reconnects and refetches — the protocol's own contract
  (comment in code names B-PROTO-034).
- Decoded envelopes go into the two view queues of §6.5 (each with its own budget).
- Durable events are never dropped without a resync marker. Coalescing of deltas happens only in `:engine:transcript`
  (50 ms publish, ADR §9.3).
- Tests (`SseBackpressureTest`, WP-04.4): maximum-size frames (16 MiB) with a stalled consumer; peak heap stays under
  the budget plus one frame (measured with a `Runtime` probe on the JVM test).

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
| `/workspace*` | removed → `Unavailable(managedWorkspaces)` without a request |
| `PATCH /shell/{id}/timeout` | removed → `Unavailable(shellSettings)` |
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

`object Oc2Routes` lists every route the app calls as `Route(method, betaPath, locationScoped: LocationScope)` with
`LocationScope = Query | Body | None` (§3.6: `POST /session/{id}/view` and `…/background` are `Query`, `POST /session` is
`Body`). A JVM test in
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

### 7.6 Event adapter → `ServerEvent` (PROTOCOLS-19 rules, kept as semantics)

`Oc2EventAdapter` is stateful per graph (one instance, §6.5). It emits the neutral events of §11.2.

| OpenCode 2 event | `ServerEvent`(s) | Rule that must not be lost |
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
| `session.status {busy\|retry}`, `session.retry.scheduled` | `RunStarted` / `RunRetrying(attempt, nextAtMs, typeTag)` | retry text only under Details |
| `session.idle`, `session.status {type: idle}` | `RunEnded(Idle)`, on **every** generation (beta and stable) | Flutter maps both to idle [V `gateway_events.dart:183-198`]; on beta servers this is the only run end. `Idle` ends the run but never replaces a `Failed` or `Interrupted` outcome already recorded for that run (so an idle after `execution.failed` cannot overwrite the error) |
| `session.compacted` | `SessionCompacted(sessionId)` | refresh the session row and reload the open chat's history (Flutter `session_events.dart:448`) |
| `session.message.content.updated` (server-side edit) | `PartUpserted` for every content item, ids `text-<n>`, `reasoning-<n>`, tool `callID` (counters per message, as the refetch mapper) | [V `gateway_events.dart:516-563`] |
| `session.text.started\|delta\|ended` | `PartUpserted("text-<n>")` / `PartDelta(Text)` / `PartUpserted(full text)` | part ids `text-<ordinal>` identical to the refetch mapper |
| `session.reasoning.*` | same with `reasoning-<n>` | |
| `session.tool.input.started\|delta\|ended` | `PartUpserted(pending)` / `PartDelta(ToolInputRaw)` / `PartUpserted(pending, raw)` | tool part id = call id |
| `session.tool.called` | `PartUpserted(running, input)` | |
| `session.tool.progress` | `PartUpserted(running, metadata replaced wholesale)` | |
| `session.tool.success\|failed` | `PartUpserted(completed\|error, joined text, attachments)` | a result for a call never seen → also `TranscriptStale` |
| open calls at step end / next step / run end | `PartUpserted(completed or error)` + `TranscriptStale` | tracker keeps ≤ **256** calls and steps, oldest evicted (B-CHAT-005) |
| `session.inbox.enqueued\|delivered\|cancelled`, `session.inbox.delivery.changed` | `InboxChanged` (+ for a `user` item: `MessageUpserted(user)` + `PartUpserted("text-0")` with **inbox id as message id**) | a waiting prompt joins the **newest** turn (`9de34551c`). `InboxChanged(Delivered)` obliges the transcript to re-read the session's newest page: delivery creates a canonical server message without a message event [V `ui/screens/chat/chat_stream.dart:34`] |
| `session.instructions.updated` | `Notice(InstructionsUpdated)` with id `evt_…`→`msg_…` | server instruction text never shown |
| `session.skill.activated`, `session.shell.*` | `Notice(SkillChanged\|ShellChanged)` | text never shown |
| `session.revert.*` | `RevertChanged` | resets history cursors downstream |
| `permission.asked\|replied` | `PermissionAsked` (copies `source.id` to `callId`) / `PermissionSettled` | asks are never replayed; refetch on reconnect |
| `form.created\|replied\|cancelled` | `FormCreated` / `FormSettled` | ephemeral |
| refresh pings forwarded at the switch (N4-D22): `agent.updated`, `config.updated`, `plugin.added\|updated`, `websearch.updated`, `catalog.updated` + `models-dev.refreshed` (one kind), `integration.updated` + `credential.updated` (one kind) | `RefreshHint(kind)` | exactly Flutter's set [V `gateway_events.dart:619-656`]; bursts share one reload downstream; row B-PROTO-141 |
| `command.updated`, `skill.updated`, `reference.updated`, `project.updated`, `worktree.updated`, `lsp.updated`, `mcp.*`, `vcs.branch.updated`, `filesystem.changed` | none (counted) | Flutter drops them (`default: []`); `filesystem.changed` fires on every tool write. Forwarding any of them later needs a per-kind reload rule and debounce and its own row |
| `credential.switched` | `CredentialSwitched(integrationId, credentialId?)` only if well-formed, plus `RefreshHint(Integration)` always | never a credential payload |
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
2. Staged revert pending → refuse with `StagedRevert` (03) before any POST (B-PROTO-043).
3. Permission preparation (`SessionPermissionPolicy`, B-PROTO-032, B-PROTO-028):
   - Default (ask): `PATCH /session/{id} {permissions, metadata}` with
     `[{action:"*",resource:"*",effect:"ask"}, …agent rules with allow→ask, …original session rules with allow→ask]`,
     denials kept in order; provenance in session metadata `oc.mobile.permissionRequests = {version:1, original, applied}`;
     max 1000 rules; then **read back** and require an exact match, else abort with "Could not turn on permission
     requests. Your message was not sent." Malformed rules or provenance fail closed.
   - Auto-approve sessions: restore the original rules (the server runs what its rules allow; its asks and denials still
     reach the phone) — the 2026-10-25 root-cause fix (`3b90a4c65`).
   - **Where the mode comes from:** `SessionPermissionPolicy.prepare(sessionId, serverRunsTools: Boolean)`. The gateway
     reads `serverRunsTools` from the `ServerRunsTools` hook in its `GatewaySpec` (§11.1). `ConnectionRegistry` creates
     the hook fail-closed (`false` = ask) for this graph instance; `:engine:requests` installs
     `AutoApproval.effective(sessionId).automatic` into it once the graph exists. `effective` resolves exactly as
     Flutter's `_autoApprovalFor` [V `state/connection/permissions.dart:330-335`, `connect.dart:283`]: the session's own
     setting, then its **parent chain** (`inheritToChildren`), then the server-wide `"*"` key in
     `oc.autoApprove.<profileId>` (the agents backend uses `oc.autoApprove.<profileId>.agents`), else ask. The hook is
     bound to one graph instance (Flutter `identical(api, gateway)`): a replaced graph's hook answers `false`. If only
     the session's own flag were read, a sub-agent session of an auto-approved parent would be rewritten to
     ask-everything, which recreates the 2026-10-25 stuck-tools root cause. Test (`SessionPermissionPolicyTest`, B-PROTO-032):
     an auto-approved parent with `inheritToChildren` and a new child session → no `PATCH` on the child.
   - The same preparation runs before `shell` and `slashCommand` (Flutter `_prepareSessionDispatch` [V `gateway.dart:469-593`]).
   - `GET /agent/{id}` 404 → retry twice at 500 ms (N4-D14).
4. `POST /session/{id}/prompt {id?, text, files?, agents?, skills?, metadata?, delivery?}` — `id` is a client message id
   from `Oc2ClientIds.message()` (§7.13), never anything else; `delivery` omitted for the
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
| Permissions | `GET /permission/request` (L), `GET /session/{id}/permission`, `POST /session/{sid}/permission/{rid}/reply {reply\|decision, message?}`; saved: `GET /permission/saved?projectID`, `DELETE /permission/saved/{id}` | reply by request id only (session recovered from the pending list; not pending → `NotFound(tag = PermissionNotFoundError)`, idempotent upstream); `allow→once`, `deny→reject`; reject message steers the model. A **4xx on either pending list** (the 2.0.29 `glob`/`grep` metadata 400) means "requests unreadable", never "none pending": cards that arrived live stay, the chat shows plain words with Try again, nothing is cleared | B-PROTO-019, 025, 073, 074, 142 |
| Forms | `GET /form/request` (L) / `/form`; `GET /session/{id}/form`; reply `POST …/reply {answer}`; cancel `DELETE` | 400 `FormInvalidAnswerError` keeps the form; 409 `FormAlreadySettledError` resolves locally; hidden fields become preset answers (B-PROTO-022) | B-PROTO-024, 044 |
| Inbox | `GET /session/{id}/inbox`; `DELETE …/inbox/{id}` (409 if delivered); delivery flip `PATCH` | cancel returns text to the composer; 409 drops the item | B-PROTO-024, 045 |
| Staged revert | `POST …/revert/stage {messageID, files}` → fixed preview; commit `POST …/revert/commit`; clear `DELETE …/revert` | 409 `SessionBusyError` while running; send blocked while staged | B-PROTO-031 |
| Session note | `GET/PUT/DELETE …/instructions/entries[/mobile.note]` (stable `/experimental`) | ≤ 8192 bytes of the JSON encoding (quotes and escapes count); 413 carries `maxBytes`; re-read before save | B-PROTO-100, 101 |
| Export / import | `GET /session/{id}/export?sanitize=` bytes kept exactly; `POST /session/import` | 405/501/non-session 404 latch the feature off | B-PROTO-096..099 |
| Read state | `POST /session/{id}/view {idle}` + `location[…]` query (§3.6) | a **privacy-gated write**: sent only when `ReadStateConsent(profileId)` (read-receipt sharing) is on and not mid-save, only from a visible, loaded chat, for exactly the observed idle timestamp; never queued and never retried automatically (a failure keeps the local watermark and retries only on the next explicit observation); dropped on wake or scope change; the local watermark is kept whether sharing is off or the call fails; with sharing off, other clients' `session.viewed` watermarks are ignored | B-PROTO-023, B-SEC-142, B-SEC-163, B-STATE-116 |

### 7.11 PTY over WebSocket

`Oc2PtyChannel`: `POST /pty/{id}/connect-token` (header `x-opencode-ticket: 1`) → `{ticket, expires_in: 60}` →
`transport.ws.connect(WsSpec(url = …/connect?ticket&cursor&location[directory], headers = Basic, maxMessageBytes = N4-D18 cap))`
— the connection's own connector, so the profile's pin and consent apply (§5.2).
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

**Client id format (`Oc2ClientIds`, `:protocol:oc2`).** The server orders messages and sessions by id, so the id must be
OpenCode's sortable id, exactly Flutter's `createCommandReceiptID` [V `lib/domain/command_receipts.dart:150-159`]:

```kotlin
public class Oc2ClientIds(private val clock: Clock, private val random: Random) {   // injected; Random.secure-backed in prod
    public fun message(): String = id("msg", descending = false)
    public fun session(): String = id("ses", descending = true)
    private fun id(prefix: String, descending: Boolean): String {
        var t = (clock.now().toEpochMilliseconds() * 4096) and 0xffffffffffffL
        if (descending) t = 0xffffffffffffL xor t                       // newest session sorts first
        val time = t.toString(16).padStart(12, '0')
        val tail = CharArray(14) { ALPHABET[random.nextInt(62)] }       // 0-9A-Za-z
        return "${prefix}_$time${tail.concatToString()}"               // 30 characters, e.g. msg_04acb4987001Ei5pD9H8pLWeKb
    }
}
```

`Oc2ClientIdsTest` (row B-PROTO-140) uses vectors produced once by Dart with a fixed clock: prefix, length 30, base62
tail, message ids ascending across milliseconds, session ids descending. A UUID or any other shape is a bug. The offline
queue's `queue:<entryId>` (§13.4) is **only** the local journal's `commandID` and is never sent on the wire.

**Finding (N4-D13):** Flutter's `CommandReceiptTransport` requires `receipt.type == 'prompt'` and `item.type == 'prompt'`
[V `command_receipt_transport.dart:89, 117`]. The server schema has inbox item types `user | synthetic | compaction |
move` only [V 2.0.28 binary, `Session.Inbox.Item`], and the captured receipt says `"type":"user"`
[V `test/fixtures/api2/prompt_receipt.json`, `inbox_list.json`]. So on real servers Flutter's dispatch always reports
"uncertain"; its `lookup()` then falls back to `GET /session/{id}/message/{id}` [V `command_receipt_transport.dart:120-125`],
which confirms once the inbox item has been delivered. Receipts do confirm; the bug is a delayed confirmation, and a
review prompt while the item still waits in the inbox. The native check accepts `"user"` at dispatch and in the inbox
lookup; the contract test runs against the real fixtures (B-PROTO-135). P-N4 re-checks on 2.1.1 and 2.2.5.

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
| Answer | `server_info` (version, permissions, `features` incl. `workspaceFileEditing`) within 8 s; `features` feeds the runtime capabilities (§11.4) on every hello |
| Requests | `{type:"session", message:{…body, type:T, requestId:"m<epochMs>_<n>"}}`; answer type ends `_response` or `.response`; any payload carrying the requestId settles it (archive answers by push) |
| Failure kinds | `invalidEndpoint, authentication, hostRefused, disconnected, invalidResponse, unavailable, newChatRequired, overloaded, deliveryUnknown, staleRequest, scopeMismatch, notFound, incompatibleProtocol` → the 03 `Failure` of §3.8 (`deliveryUnknown` → `Refused(DispatchUncertain)`, `staleRequest`/`scopeMismatch` → `Refused(StaleScope)`, `unavailable` → `ComputerDidNotAnswer`) + `PaseoFailure.reason` (daemon text trimmed to 240 chars, Details only) |
| Rejections | close 4401 after upgrade = wrong password; 4003 or `hello.rejected` = protocol mismatch; HTTP 403 with password = authentication, without = host not allowed (B-PROTO-071) |
| Turn truth | turn events own busy state (snapshots lag); a `running` record is believed only for a bound after its last change unless the snapshot carries `activeTurn` |
| Mid-turn | `joinsTurn` for `claude, codex, opencode, pi` (`activeTurnBehavior:"steer"`, "Add to this turn"); `restartsTurn` for ACP agents ("Stop and send"); unknown while the agent identity is not loaded — wait, never guess from the name |
| Permissions | provider suggestions only for "Always allow"; ACP host requests get the exact offered actions (`HostAgentPermissionGateway`, security-relevant: the person approves exactly what the agent offered); no reject choice → cancel the turn |
| Questions and plans | a permission-wire request with `kind = question` (Claude Code's AskUserQuestion) or `kind = plan` (ExitPlanMode plan approval) maps to `ServerEvent.QuestionAsked(PendingQuestion)`, **never** to `PermissionAsked`; replies go through `QuestionGateway.replyQuestion` / `rejectQuestion` and emit `QuestionSettled(Replied\|Rejected)` [V `paseo/gateway/questions.dart:51, 269`]; ≤ 64 retained; `QuestionNotFound` on reply settles locally (B-PROTO-048); rows B-CHAT-070..072 |
| Probe | validate → `health()` → one-item session page → `providers()` names → close (B-PROTO-070) |
| Bounded history | private second connection, 1 MiB frames, 10 s |
| Terminal | binary frames `[opcode, slot, …]`; restore frame first, reset then replay; frames of other slots ignored |
| Monitor trap | a daemon reports pending permissions only after a session page ran: **list before reading requests** (AGENTS-BACKENDS-44; monitor rows B-AGENT-203) |
| Reconnect | §5.5 (`WsReconnectPolicy`): 1→16 s, reset after the recovery read, `authentication`/`hostRefused` terminal, 3 s announce grace, epoch-scoped turn reset |
| Phone agents helper | runtime-only profile `<profileId>.agents` at `ws://127.0.0.1:4099/ws`, opened with `ConnectionRegistry.openTransient` (§12.1) from the agents engine's `ProfileSource`; password `oc.agentHostSecret.<profileId>`; 15 s request timeout; gateway options `trackLocalWork = true`, `defaultProviderModes = {claude: default}` [V `phone_agents_host.dart:931-963`]; the helper is **started before dialing** (`AgentHost.ensureRunning`, 17 §1.2); a separate password-less `GET http://127.0.0.1:4099/api/health` (§3.4) tells "helper down" from "link waiting during a long tool call" and feeds the turn-stall `helperDown` classification (B-CHAT-118) |

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
`Refused(StaleScope)` and is never retried as `turn/start` or interrupt. Approvals are answered once; a pending approval cannot
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
| Session-address links (`sessionAddressHandoff`, off in Flutter), including the `/.well-known/opencode-mobile-session-handoff` descriptor fetch and `SessionAddressLookupGateway` | off | not ported (N4-O4); 11 §14.6 drops its "the handoff descriptor fetch is 04's" sentence (§22) |

---

## 11. Gateway contracts and capability detection (`:core:domain`)

### 11.0 Names: 03 §10 is the source; amendments this file needs

WP-04.0 freezes nothing until the coordinator has applied the "Amend 03" column to 03 (ADR §10.3 freeze items). Every
name below is used in this file exactly as 03 spells it.

| 03 name (module) | Used here as | Amend 03 |
|---|---|---|
| `BackendKind { OpenCode2, Paseo, Codex }` (`:core:model`) | as is; profile JSON `"backend": openCode\|paseo\|codex` reads `openCode` as `OpenCode2` | — |
| `GatewayFactory { backend; connect(profile, credentials) }` (`:core:domain`) | `connect(spec: GatewaySpec): Outcome<GatewayPair>` and `probe(target: ProbeTarget): ProbeResult` | parameter becomes `GatewaySpec`; add `probe`; add `GatewaySpec`, `ProbeTarget`, `ServerRunsTools`, `GatewayOptions` (§11.1) |
| `GatewayPair(live, operations)` | as is | — |
| `EventGateway.events(scope): Flow<StreamSignal<ServerEvent>>`, cold | `events(scope): Flow<ServerEventEnvelope>` per view, **hot**: subscribe, then `start()` (§6.5); plus `status`, `lastFailure`, `connects`, `retryNow()` | make it hot with `start()`; `EventScope { Location, All }`; element `ServerEventEnvelope`; the stream status moves to `status: StateFlow<StreamStatus>` |
| `ServerEvent` (`:core:domain`) | the neutral vocabulary of §11.2 (was "GatewayEvent" in the first draft) | contents from §11.2 |
| `StreamStatus` enum, `StreamSignal.Status(status, nextAttemptAtMs)` (`:core:model`) | sealed `StreamStatus` with `WaitingForNetwork`, `Suspended`, `Stopped(failure)` (§6.3); `StreamSignal.Status(status)` | adopt the sealed type; drop the separate `nextAttemptAtMs` |
| `Failure` (flat, closed; `:core:common`) | the only failure type; §3.8 maps every input | detail fields, `Incompatible`, four `RefusalReason`s (§3.8) |
| `HostClass`, `AddressRules.classify` (`:core:security`) | as is; exact rules §4.1 | — |
| `ConnectionRegistry.open(profile): Outcome<ConnectionGraph>`, `live: StateFlow<ImmutableMap<ProfileId, ConnectionGraph>>` (`:engine:connection`) | as is, plus `purpose`, `openTransient`, `openEphemeral`, `openDemo` (§12.1, §12.8), and the `ProfileSource`, `ReconnectParticipant`, `SuspendParticipant` seams | add those |
| `ConnectionGraph.session: ConnectionSession` | members defined in §12.1 | — |
| `PromptGateway.prompt(session, request: PromptRequest): Outcome<Unit>`, `abort(session)` | as is: `Ok` = admission confirmed, `Failed(Refused(DispatchUncertain))` = look it up (§7.13); the client id travels in `PromptRequest.clientMessageId` | `abort(session, resume: Boolean = false)` (OpenCode 2 `interrupt?resume=`) |
| `SessionGateway.createSession()` | `createSession(spec: NewSession = NewSession())` with `clientSessionId: SessionId?` (idempotent create, §13.6), title, agent, model | add the parameter |
| `QuestionGateway` ("legacy questions") | **live** on Paseo: `pendingQuestions`, `replyQuestion`, `rejectQuestion` (§11.1) | replace "legacy" with the methods |
| `SessionId`, `MessageId`, `RequestId`, `ProfileId` value classes | every id in every signature here | — |
| `ServerGateway` (03 §10.4) | plus `awaitActionTransport()` and `linkControl: LinkControl` (§11.1) | add both |
| `ServerGateway.capabilities` "computed from the probed server version" | N4-D12: base per backend + announced runtime facts − latches, recomputed on every (re)connect; the version-rule table is that computation and is empty at the switch | none (the comment stands) |
| `DeviceFacts.network: StateFlow<NetworkFact>` (`:core:domain`) | replaced by `NetworkStatus`/`NetworkState` in `:core:model` (§6.4) | remove `network` from `DeviceFacts` |
| 03 §10.7 `:core:net` sketch (`HttpClientFactory.clientFor`, shared pool, `onNetworkChanged`, `TransportFailures`, `ReconnectPolicy`, `eventStream`, `Downloader`, `OkHttpClients.hardened`) | §3 `TransportFactory`/`Transport`, per-connection client (N4-D1), `BackoffPolicy` + `EventStreamLoop`, `ResumableDownloader`, `HardenedOkHttp` | replace 03 §10.7 with a pointer to this file |
| `SseParser(maxFrameBytes, onFrame)` | `feed(...)` returns the completed frames (§6.1) | signature |

### 11.1 Gateway interfaces

A port of `lib/domain/server_gateway/interfaces.dart` (494 lines) **and of every facet declared elsewhere** (table
below), with 03's facet split and three mechanical rules: every call is `suspend` and returns `Outcome<T>`; every
optional facet method has a default body returning `Outcome.Failed(Failure.Unavailable(FeatureId(flag)))` (03's
`unavailable(...)`, the Dart `ProductRepository` graceful defaults); OC2-only types that Dart leaked into the interfaces
(`Api2FormInfo`, `Api2InboxItem`, `Api2FormState`) move into `:core:model` as `FormSpec`, `InboxItem`, `FormState`.

```kotlin
// :core:domain
public data class GatewaySpec(
    val profile: ServerProfile,                   // 03 :core:model; saved or transient (§12.1)
    val permit: TransportPermit,                  // :core:security TransportPolicy (§4.2); domain may see security
    val credentials: ConnectionCredentials,       // 03; already registered with the Redactor
    val location: ServerLocation?,                // restored location (§12.2 step 4)
    val scope: ConnectionCoroutineScope,          // the graph's scope
    val reconnectAllowed: StateFlow<Boolean>,     // N4-D21 (§12.9)
    val serverRunsTools: ServerRunsTools,         // auto-approval hook, fail-closed (§7.9)
    val options: GatewayOptions = GatewayOptions.Default, // requestTimeoutMs (helper 15 s), trackLocalWork, defaultProviderModes
)
public fun interface ServerRunsTools { public fun forSession(id: SessionId): Boolean }
public data class ProbeTarget(val url: String, val permit: TransportPermit, val credentials: ConnectionCredentials?)

public interface GatewayFactory {                 // contributed as a Metro set (03 §0.2 S3, §13.5)
    public val backend: BackendKind
    public suspend fun probe(target: ProbeTarget): ProbeResult
    /** OpenCode 2: no network I/O until first use. Paseo/Codex: opens the socket and runs hello/initialize. Each
     *  protocol builds its own TransportSpec from spec.permit; nothing above :protocol:* sees one. */
    public suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair>
}

// 03's facets, members completed (03 §10.4 names kept)
public interface HealthGateway { public suspend fun health(): Outcome<Health> }
public interface SessionGateway {
    public suspend fun sessionPage(cursor: String? = null, limit: Int = 100): Outcome<ServerPage<Session>>
    public suspend fun createSession(spec: NewSession = NewSession()): Outcome<Session>
    public suspend fun session(id: SessionId): Outcome<Session>
    public suspend fun renameSession(id: SessionId, title: String): Outcome<Unit>
    public suspend fun deleteSession(id: SessionId): Outcome<Unit>
    public suspend fun sessionStatuses(): Outcome<ImmutableMap<SessionId, SessionStatus>>   // strict (§7.4)
    public suspend fun messagePage(id: SessionId, cursor: String? = null, limit: Int = 100): Outcome<ServerPage<MessageWithParts>>
    public suspend fun diff(id: SessionId): Outcome<ImmutableList<FileDiff>>
}
public interface PromptGateway {
    public suspend fun prompt(session: SessionId, request: PromptRequest): Outcome<Unit>
    public suspend fun shell(session: SessionId, request: ShellRequest): Outcome<Unit>
    public suspend fun slashCommand(session: SessionId, command: String, args: String, model: ModelSelection?): Outcome<Unit>
    public suspend fun abort(session: SessionId, resume: Boolean = false): Outcome<Unit>
}
public interface PermissionGateway {
    public suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>>   // 4xx = Failed, never empty (§7.10)
    public suspend fun respondPermission(id: RequestId, reply: PermissionReply, message: String?): Outcome<Unit>
}
public interface QuestionGateway {                // live on Paseo (AskUserQuestion, ExitPlanMode); inert on OpenCode 2
    public suspend fun pendingQuestions(): Outcome<ImmutableList<PendingQuestion>> = unavailable("legacyQuestionRequests")
    public suspend fun replyQuestion(session: SessionId, id: RequestId, answers: List<List<String>>): Outcome<Unit> = unavailable("legacyQuestionRequests")
    public suspend fun rejectQuestion(session: SessionId, id: RequestId): Outcome<Unit> = unavailable("legacyQuestionRequests")
    // NotFound(tag = QuestionNotFound) on reply/reject → the engine settles the card locally (B-PROTO-048)
}
public interface FormGateway {
    public suspend fun pendingForms(session: SessionId?): Outcome<ImmutableList<FormSpec>>
    public suspend fun replyForm(session: SessionId, formId: String, answer: FormAnswer): Outcome<Unit>
    public suspend fun cancelForm(session: SessionId, formId: String): Outcome<Unit>
}
public interface InboxGateway {
    public suspend fun inbox(session: SessionId): Outcome<ImmutableList<InboxItem>>
    public suspend fun cancelInboxItem(session: SessionId, inboxId: String): Outcome<Unit>
    public suspend fun steerInboxItem(session: SessionId, inboxId: String): Outcome<Unit>
    public suspend fun queueInboxItem(session: SessionId, inboxId: String): Outcome<Unit>   // never offered where steering exists
}
public interface ProviderGateway {                // §7.12 Catalog row: the engine assembles the catalog snapshot
    public suspend fun providers(): Outcome<ProvidersSnapshot>
    public suspend fun configuredProviders(): Outcome<ProvidersSnapshot>
    public suspend fun agents(): Outcome<ImmutableList<AgentInfo>>
}
public interface FileGateway {
    public suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>>
    public suspend fun fileContent(path: String): Outcome<FileContent>
    public suspend fun findFile(query: String): Outcome<ImmutableList<String>>
    public suspend fun findText(query: String): Outcome<ImmutableList<TextMatch>>
}
public interface ServerGateway : HealthGateway, SessionGateway, PromptGateway, PermissionGateway, QuestionGateway,
    FormGateway, InboxGateway, ProviderGateway, FileGateway, EventGateway {
    public val capabilities: StateFlow<ServerCapabilities>       // N4-D12; recomputed on every (re)connect / hello
    public val location: StateFlow<ServerLocation>
    public fun setLocation(location: ServerLocation)
    public suspend fun awaitActionTransport(): Outcome<Unit>      // Flutter prepareActionTransport (§12.6); amends 03
    public val linkControl: LinkControl                           // amends 03; drives the gateway's own Transport
    public suspend fun close()                                    // full close order of §3.1
}
public interface LinkControl {
    public fun onNetworkChanged()                 // evictIdle + cancel connected streams/sockets → reconnect at attempt 0 (§6.4)
    public suspend fun suspendStreams()           // LinkDemand.BackgroundIdle step 3 (§6.4)
    public fun resumeStreams()                    // back to foreground (only while reconnectAllowed)
}
// ServerOperationsGateway = SessionOperations + Host + Vcs + Terminal + ManagedShell + Catalog + Mcp + Integration +
// Request (03 §10.4), members from interfaces.dart:252–494.
```

**Every facet**, found with `grep -rE "^(abstract|mixin|sealed).*class \w+Gateway" lib/` (79 abstract types) and the
`implements` lists of `Api2Gateway`, `Api2OperationsGateway`, `PaseoGateway` and `CodexGateway`. Engines test for a
facet with 03's `facet<F>()`; features gate on capabilities only. This table is frozen in `contracts/slice-1.md`.

| Facet (Flutter file) | Implemented at the switch by | Status |
|---|---|---|
| The 19 composed facets of `ServerGateway` / `ServerOperationsGateway` (`interfaces.dart`) | OpenCode 2, Paseo, Codex (Unavailable defaults where a backend lacks a call) | switch |
| `CorrelatedPromptGateway` (`interfaces.dart`) | Paseo; OpenCode 2 (new: exposes `Oc2ClientIds`, §7.13, so `:engine:delivery` gets ids without seeing the protocol) | switch |
| `MidTurnPromptGateway` (`interfaces.dart`) | Paseo, Codex | switch |
| `SessionSelectionGateway`, `StagedRevertGateway` (`value_types_core.dart`) | selection: OpenCode 2, Paseo; staged revert: OpenCode 2 | switch |
| `SessionReadStateGateway`, `SessionRetryGateway` (`interfaces.dart`) | read state: OpenCode 2 (§7.10 consent rules). `SessionRetryGateway`: **no production implementer** in Flutter (consumer only, `state/connection/sessions.dart:213`) | read state switch; retry: port the interface and consumer, inert |
| `IntegrationCredentialGateway`, `IntegrationCommandGateway`, `IntegrationAuthRecoveryGateway`, `McpRemovalGateway` (`interfaces.dart`) | OpenCode 2 | switch |
| `SessionNoteGateway`, `SessionExportGateway`, `SessionImportGateway`, `SessionSkillGateway`, `ActiveContextGateway`, `PluginGateway`, `UsageStatisticsGateway`, `HistoryMigrationGateway` (own files) | OpenCode 2 (`Api2OperationsGateway`) | switch |
| `WebSearchGateway` (`web_search.dart`), `RemoteShellGateway` (`remote_shell.dart`) | OpenCode 2 (`Api2Gateway`, `Api2ShellRunner`) | switch |
| `OneShotCommandGateway`, `TerminalCommandGateway` (`terminal_lifecycle.dart`) | OpenCode 2 (`_Api2DevOps`, `Api2TerminalCommandGateway`) | switch |
| `GenUiHistoryGateway` (`genui/gen_ui_history.dart`) | OpenCode 2, Paseo | switch |
| `GenUiLoadGuardGateway`, `GenUiRecoverySnapshotGateway` (`genui/gen_ui_history.dart`) | Paseo (the load guard prevents the "idle chats jump to Just now" incident) | switch |
| `AgentHandoffHistoryGateway` (`agent_handoff_history.dart`) | OpenCode 2, Codex | switch |
| `AgentHandoffTargetGateway` ⊃ `AgentHandoffFileGateway` (`agent_handoff.dart`) | Paseo (gated by runtime `agentHandoffFiles`, §11.4) | switch |
| `HostAgentProviderGateway`, `HostAgentPermissionGateway` (`host_agent_providers.dart`) | Paseo (exact ACP permission choices) | switch |
| `HostDaemonUpdateGateway`, `AgentFeatureGateway` (fast mode), `AgentTurnFailureGateway`, `ProviderConversationImportGateway` (own files) | Paseo | switch |
| `BackgroundTaskGateway` (`background_tasks.dart`) | Paseo (`_PaseoBackgroundTasksApi`) | switch |
| `ProviderUsageGateway` (`provider_usage.dart`) | `PaseoProviderUsageGateway` (separate client over the Paseo transport) | switch (`:engine:integrations` consumer) |
| `AgentAccountGateway` (`agent_account.dart`) | Codex | switch |
| `SetupConfigGateway` (`setup_assistant.dart`) | `OpenCode2SetupConfigGateway` (read only) | switch |
| `ProviderQuotaGateway` (`provider_quota.dart`) | `HttpProviderQuotaGateway` (quota reader, §12.7) — not a server facet | switch (`:engine:integrations`) |
| `FileShareGateway`, `AppDiagnosticsGateway` | platform services (Wi-Fi share, device diagnostics) — not server facets | switch, owned by media / diagnostics sections |
| `SetupTransactionalConfigGateway`, `McpConfigurationGateway`, `ToolConfigurationGateway`, `SessionCommandHandoffGateway` | **no production implementer** in Flutter | later (port when a backend implements them) |
| `SessionAddressLookupGateway` | session-address links | retired (N4-O4) |
| `ExternalAgentGateway` | A2A | retired (ADR D6) |
| `FactsBookGateway`; the ten `Orchestration*Gateway`s (`orchestration_gateway.dart`, `team_project_gateway.dart`, `orchestration_work_edits.dart`) | FactsBook, AI Team | later (ADR D6) |

### 11.2 `ServerEvent` (neutral vocabulary, N4-D11) and `EventGateway`

```kotlin
public class ServerEventEnvelope(public val event: ServerEvent, public val location: EventLocation?, public val receivedAtMs: Long)

public sealed interface ServerEvent {
    public data object Connected : ServerEvent
    public data object ResyncNeeded : ServerEvent                 // this view overflowed (§6.5): reconcile by refetch
    // sessions
    public data class SessionUpserted(val session: Session) : ServerEvent
    public data class SessionChanged(val sessionId: SessionId, val title: String?, val directory: String?,
                                     val workspaceId: String?, val replacesLocation: Boolean) : ServerEvent
    public data class SessionDeleted(val sessionId: SessionId) : ServerEvent
    public data class SessionSelection(val sessionId: SessionId, val model: ModelRef?, val agent: String?) : ServerEvent
    public data class SessionUsage(val sessionId: SessionId, val cost: Double, val tokens: Tokens) : ServerEvent
    public data class SessionViewed(val sessionId: SessionId, val idleMs: Long) : ServerEvent   // ignored when read-state sharing is off (§7.10)
    public data class SessionCompacted(val sessionId: SessionId) : ServerEvent                 // refresh the row, reload the open chat
    // runs
    public data class RunStarted(val sessionId: SessionId) : ServerEvent
    public data class RunRetrying(val sessionId: SessionId, val attempt: Int, val nextAtMs: Long?, val tag: WireTag?,
                                  val details: Redacted?) : ServerEvent
    public data class RunEnded(val sessionId: SessionId, val outcome: RunOutcome) : ServerEvent
    // RunOutcome = Succeeded | Idle | Interrupted(reason) | Failed(kind, details); Idle never replaces Failed/Interrupted (§7.6)
    // transcript
    public data class MessageUpserted(val message: MessageInfo) : ServerEvent
    public data class PartUpserted(val part: Part) : ServerEvent
    public data class PartDelta(val sessionId: SessionId, val messageId: MessageId, val partId: PartId,
                                val field: DeltaField, val delta: String) : ServerEvent        // Text, Reasoning, ToolInputRaw
    public data class TranscriptStale(val sessionId: SessionId) : ServerEvent                  // "re-read the real result"
    public data class Notice(val sessionId: SessionId, val messageId: MessageId, val kind: NoticeKind) : ServerEvent
    public data class RevertChanged(val sessionId: SessionId, val kind: RevertKind) : ServerEvent
    // requests and inbox
    public data class PermissionAsked(val request: PermissionRequest) : ServerEvent
    public data class PermissionSettled(val sessionId: SessionId, val requestId: RequestId) : ServerEvent
    public data class QuestionAsked(val question: PendingQuestion) : ServerEvent              // Paseo question.v2.asked (incl. plan approval)
    public data class QuestionSettled(val sessionId: SessionId, val requestId: RequestId,
                                      val outcome: QuestionOutcome) : ServerEvent             // Replied | Rejected
    public data class FormCreated(val form: FormSpec) : ServerEvent
    public data class FormSettled(val sessionId: SessionId, val formId: String, val answered: Boolean) : ServerEvent
    public data class InboxChanged(val sessionId: SessionId, val inboxId: String, val change: InboxChange) : ServerEvent
    // hints and server facts
    public data class RefreshHint(val kind: HintKind) : ServerEvent          // N4-D22: Agent, Config, Plugin, WebSearch, Catalog, Integration
    public data class CredentialSwitched(val integrationId: String, val credentialId: String?) : ServerEvent
    public data class Installation(val kind: InstallationKind, val version: String) : ServerEvent
    public data class TerminalChanged(val ptyId: String, val kind: TerminalChange) : ServerEvent
    public data class ShellChanged(val shellId: String) : ServerEvent
    public data class WorktreeOutcome(val ready: Boolean) : ServerEvent
}

public enum class EventScope { Location, All }
public interface EventGateway {                    // amends 03 §10.4 (hot, explicit start; §6.5)
    public fun events(scope: EventScope = EventScope.Location): Flow<ServerEventEnvelope>   // its own bounded queue
    public fun start()                             // ConnectionRegistry calls it after the graph's consumers subscribed
    public val status: StateFlow<StreamStatus>     // :core:model (§6.3); domain never sees :core:net
    public val lastFailure: StateFlow<Failure?>    // why the stream's last try failed; drives passwordRejected (§4.8)
    public val connects: StateFlow<Int>            // successful (re)connects so far (Flutter _streamConnects)
    public fun retryNow()
}
```

The chat section owns `MessageInfo`, `Part`, `ToolState` in `:core:model` and the reducer in `:engine:transcript`;
Paseo and Codex mappers emit the same `ServerEvent`s.

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
- **Paseo capabilities are not constant.** `ServerGateway.capabilities` is recomputed on every hello and every
  disconnect: `PaseoCapabilities.Base.withGenUi(false, agentHandoffFiles = connected ∧ server_info.features.workspaceFileEditing)`
  [V `lib/paseo/gateway.dart:369-372`]. Hard-coding `agentHandoffFiles` either loses "Continue with… → Claude Code" or
  writes handoff records to daemons that refuse file editing.
- **Snapshot test:** once, in a throwaway Flutter worktree branch (not merged), a 30-line Dart script prints each **base**
  constant (static part only) as JSON into `native/fixtures/capabilities/{default,oc2,paseo,codex}.json`.
  `CapabilitySnapshotTest` (B-PROTO-016) asserts the Kotlin constants equal those files flag for flag. The file stays as
  the parity record. `PaseoRuntimeCapabilitiesTest` flips `workspaceFileEditing` and `connected` and asserts
  `agentHandoffFiles` follows both.

### 11.5 Runtime latches

`CapabilityLatches` lives in each `ConnectionGraph`: a feature whose endpoint answers 404 for a non-session reason, 405
or 501 is latched off for that graph and recorded (`activeContext`, `sessionExport`, `sessionImport`, `sessionNotes`,
`managedShells`, `messageDelete`, `historyMigration`). A missing *session* (404 with `SessionNotFoundError`) never
latches; authorisation errors never latch (B-PROTO-101). The graph's effective capabilities =
`base ∧ ¬latched`. Latches reset when the graph is rebuilt.

### 11.6 Version rules (mechanism only)

`CapabilityRules.apply(base, serverVersion): ServerCapabilities` reads a table `VersionRule(flag, minVersion, maxVersion?)`.
The table is **empty at the switch** (N4-D12); this table is how 03's "computed from the probed server version" is
implemented, so 03 and this file agree. Example of a later row: `sessionArchive` from 2.1.1 (2.0.x answers 204 and
ignores it [R]). Adding a row is a feature slice with its own live proof.

One version fact is used at the switch, as words only: when `/api/info` of the **phone's** server reports a version
below the pin (the in-app server update is a manual button, so a phone can still run 2.0.29), `LinkStatus` offers
"Update the phone's OpenCode" (row B-PROTO-142).

### 11.7 Gating rules

- UI gates on `ServerCapabilities` and `PlatformCapabilities` flags only, never on `BackendKind` (AGENTS.md). Konsist:
  no `BackendKind` reference in `:feature:*` or `:ui:*` except `:feature:servers` (which shows the backend type).
- "Absent, never disabled": a feature whose flag is false does not render (B-PROTO-124; Tools gate keeps its
  explanation, B-KIT-013).
- Konsist bans in every module: `HttpRequestRetry`, `ContentNegotiation`, `io.ktor.client.plugins.logging`,
  `HttpLoggingInterceptor`, `.newWebSocket(` (OkHttp's own WebSocket), `CertificatePinner`, `followRedirects = true`,
  `retryOnConnectionFailure(true)`.
- Konsist: **no ad-hoc clients** (DESKTOP-WEB-CROSS-39, row B-PROTO-145). `HttpClient(` (Ktor), `OkHttpClient(`,
  `OkHttpClient.Builder`, `HttpURLConnection`, `URL.openConnection` and `java.net.http.HttpClient` may appear only in
  `:core:net` and `:core:net-okhttp`. Flutter built about 15 ad-hoc clients (GitHub device flow and API, MCP registry,
  models.dev logos, the update check, the helper health check on :4099, the quota collector on :4195,
  `termux_running_server`); natively each one is a `TransportPolicy.fixedOrigin(...)` or
  `TransportPolicy.loopbackService(...)` permit (§4.2) on a `Transport`. Reused Kotlin that opens connections
  (`BuiltinLinux.kt` download, §3.10) is rewritten onto `ResumableDownloader`; `PhoneEngineNative.kt` (AI Team, later)
  is on a dated allowlist in `:quality` until `:protocol:team` exists.
- Konsist: `@OptIn(InternalAPI::class)` only in `BoundedWsChannels.kt` (§5.2).

---

## 12. Connection engine (`:engine:connection`)

### 12.1 API

03 §13.4 names, with the amendments listed in §11.0:

```kotlin
public class ConnectionRegistry /* 03 §13.4: @SingleIn(AppScope) @Inject, ProfileCloser */ {
    public val live: StateFlow<ImmutableMap<ProfileId, ConnectionGraph>>   // agents backend keyed "<id>.agents"
    /** Idempotent. Saved profiles only: reads them through the app's ProfileSource (oc.profiles). */
    public suspend fun open(profile: ServerProfile, purpose: Purpose = Purpose.Primary): Outcome<ConnectionGraph>
    /** Runtime-only profiles that are never written to the profile store: the phone agents helper ("<id>.agents")
     *  and BYO host tunnels. The spec comes from the owning engine's ProfileSource. */
    public suspend fun openTransient(spec: TransientProfile, purpose: Purpose): Outcome<ConnectionGraph>
    public suspend fun openEphemeral(profile: ServerProfile, purpose: EphemeralPurpose, deadlineMs: Long): Outcome<EphemeralConnection>
    public fun openDemo(script: DemoScript): ConnectionGraph               // isolated, never in `live` (§12.8)
    public fun graphFor(ref: ConversationRef): ConnectionGraph?
    public suspend fun close(profile: ProfileId)
}
public enum class Purpose { Primary, Side, AgentsBackend, Monitor }
public enum class EphemeralPurpose { Probe, Schedule, WidgetAction, WidgetStatus, NotificationReply, Quota }

/** Where a graph's profile and secrets come from. The app binds the saved-profile source; :engine:agents contributes the
 *  phone-helper source and :engine:integrations the BYO source (Metro set; keyed by TransientKind). */
public interface ProfileSource {
    public val kind: TransientKind?                                      // null = saved profiles
    public suspend fun credentials(profile: ServerProfile): Outcome<ConnectionCredentials>
    public suspend fun beforeDial(profile: ServerProfile): Outcome<Unit> = Outcome.Ok(Unit)   // helper: ensure it runs
}
public data class TransientProfile(val kind: TransientKind, val profile: ServerProfile, val options: GatewayOptions)
public enum class TransientKind { PhoneAgentsHelper, ByoHost }

public interface ConnectionSession {               // 03's ConnectionGraph.session
    public val link: StateFlow<LinkStatus>               // the quiet, UI-facing status (§12.4)
    public val capabilities: StateFlow<ServerCapabilities>
    public val passwordRejected: StateFlow<Boolean>
    public suspend fun retryNow()                        // a person's Try again: always dials, even with reconnect off
}
```

Transient profiles: `ProfileStore.upsert` refuses them (B-SEC-136); schedule and widget runners refuse them
(B-AGENT-219, §12.7); they never appear in `oc.profiles` or the deletion sweep's profile list (their own keys are swept
with the owning profile, §13.2).

### 12.2 Connect sequence (STATE-DATA-10, 7.2)

1. Get the profile: a saved profile from `oc.profiles`, or a `TransientProfile` from its `ProfileSource`. Validate by
   backend (URL rules, consent, pin origin, secret present). A failure is a typed `Failure`, nothing dialed. Run
   `ProfileSource.beforeDial` (the phone agents helper is started before dialing).
2. Bump the generation: cancel the previous graph's child scope (stale work ends; no generation counters needed,
   ADR §9.5).
3. `TransportPolicy.decide(kind(backend), …)` → `TransportPermit`; create the `ServerRunsTools` hook (fail-closed) and
   the `reconnectAllowed` flow (§12.9); `GatewayFactory(backend).connect(GatewaySpec(...))`. One `Oc2Client` is shared
   by the live and operations gateways (Flutter `_createV2GatewayPair`). Create the `ConnectionGraph` extension.
4. Location: **OpenCode 2** restores the saved location (`oc.location.<id>`) **without waiting** for the project list;
   revalidate later; a missing catalog entry never authorises switching. **Codex and Paseo**: location =
   `profile.codexDirectory`; `oc.location.<id>` is ignored, so an older OpenCode-style saved selection never undoes an
   edit of the profile's directory (STATE-DATA-13).
5. Let the graph's consumers subscribe to `events(Location)` and `events(All)` (transcript, requests, attention,
   connection), then `EventGateway.start()` (§6.5), and start the 5 s busy poller (§12.5).
6. Hydrate in parallel, each tolerant: sessions page, statuses, permissions, forms, questions, inbox of open sessions,
   catalog.
7. Sync services (monitors, background, widgets) through their engines' own observers.
8. On failure with `redetectOnFailure = true`: re-probe the address once, then connect again with `false`.

### 12.3 Reconcile on every (re)connect

Triggered by `StreamStatus.Connected` (HTTP 200, or the Paseo/Codex recovery read of §5.5), not by a timer.
`:engine:connection` may not import `:engine:requests` (6) or `:engine:delivery` (7) (ADR §6.2, 03 S4), so the
refetches and the flush reach it as contributions:

```kotlin
// :engine:connection
public interface ReconnectParticipant {
    public val name: String                                   // for traces and tests
    public suspend fun onLinkRestored(graph: ConnectionGraph, kind: RestoreKind)
}
public enum class RestoreKind { FirstAttach, AfterDrop, AfterResume }
/** Runs first on LinkDemand.BackgroundIdle, before any stream is cancelled (§6.4); bounded by the caller (5 s). */
public interface SuspendParticipant { public suspend fun beforeSuspend(graph: ConnectionGraph) }
// :engine:requests  @ContributesIntoSet(ConnectionScope::class): permissions, forms, questions, gen-UI scope refetch;
//                   and a SuspendParticipant that flushes answers still in their Undo window
// :engine:delivery  @ContributesIntoSet(ConnectionScope::class): inbox of every open session, offline-queue flush (§13.4)
```

On `Connected`, in this order, without awaiting anything slow:

1. Clear `lastFailure` and `passwordRejected`; `connects += 1` (a waiting-request read uses it to know whether the
   stream carried every change since it started).
2. Launch **every** participant at once in the graph scope, each in its own child job with its own bound (refetches
   10 s each; the flush keeps its own per-entry rules). None waits for another: the flush does not wait for the
   refetches (Flutter starts all four unawaited [V `events.dart:95-100`]; N4-D20). One hung forms read cannot hold back
   the queue. Partial failures keep reply provenance (B-PROTO-048).
3. If the previous status was `Reconnecting`/`Disconnected`/`WaitingForNetwork`/`Suspended`: mark "data refresh ready"
   (open screens re-read from REST: the open chat re-reads its newest page and merges by id) and refresh the session list
   and statuses; then clear the quiet-reconnect token.
4. If this is the first attach after `Connecting`, the open chat reloads once more (events between transport-up and
   stream-live were never heard).
5. Nothing is recorded for "While you were away": the native app stops that recording at the switch (02 S02-8,
   17 N17-D4). The quiet-reconnect token of step 3 is the only trace, and it is in memory.

Test (`ReconcileOrderTest`, WP-04.6) with fake participants: all start on one `Connected`; a participant that never
returns does not delay the others; a second `Connected` cancels the first round's unfinished children.
`ResyncNeeded` on a view (§6.5) runs the same participants with `RestoreKind.AfterDrop` for that view's consumers.

Global-channel loss marks other-project attention stale and clears feed question/form caches; any
`SessionUpserted|SessionChanged|SessionDeleted` on `All` schedules a chat-feed refresh (PROTOCOLS-20).

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

`LinkStatus` (UI-facing) = `Hidden | Connecting | Reconnecting(nextAttemptAtMs) | WaitingForNetwork |
ReconnectOff | SignInNeeded(target) | ServerStarting | Unreachable(why, hostClass) | Incompatible |
PhoneServerOutdated`. Words (in `:ui:words`) follow the owner rules: plain words, name the server, Try again as a
visible action, technical text only under Details. `ReconnectOff` = the link dropped and the server's automation
policy does not allow automatic reconnects (§12.9); `PhoneServerOutdated` = §11.6.

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

Scheduled and widget runners refuse transient profiles (`TransientProfile`, §12.1) and a transient phone-agent route
on a cold connection (B-AGENT-219).


| Client | Rules | Rows |
|---|---|---|
| Scheduled prompt runner | refuses transient-transport profiles, unconfirmed cleartext, missing password, non-OpenCode-2 servers; for the in-app server first `RuntimeHost.acquirePhoneServer(profileId, runtime, deadline)` → token, released in `finally`; same consent and pin as the foreground | B-AGENT-* scheduling rows (schedules section) |
| Widget action / status runners | one read; discarded if the world changed (new turn, new permission, run identity changed, scope change, stale ticket); timeout closes the transport; never clears a permission or marks idle wrongly; run identity = last real user prompt id, ignoring `v2:` rows | B-ANDR-103..107, 118, 119, B-PROTO-127, B-SEC-180 |
| Notification reply | uses the live background transport directly; never the foreground wake path (that clears every alert); a stale request id refreshes the alert | B-PROTO-046 |
| Profile monitor | sequential bounded snapshots of other servers; Paseo: list before requests; runs only while the server's consent behaviour `monitorOtherServers` is on (§12.9); "Wi-Fi only" reads `NetworkState.onWifi` (true under Tailscale over Wi-Fi), and **loopback servers are exempt** from it (Wi-Fi-only never holds back a server reached without a network) | B-ANDR-056..064, B-AGENT-203 |
| Quota reader | in-app server: `TransportPolicy.loopbackService(4195)`, Bearer only; remote server: same origin, header-only Basic; one GET; no token → unsupported, nothing sent; runs only while the consent behaviour `monitorQuota` is on (§12.9) | B-PROTO-058, 084..087 |
| Phone agents helper health | `TransportPolicy.loopbackService(4099)`, `GET /api/health`, no password, 3 s; used by the agents engine and the turn-stall classifier (`helperDown`) | B-CHAT-118 |

### 12.8 Isolated (demo) graph

Section 07 builds "Try it offline" (SCREENS-NAV-61, 07 C39) on a `DemoConnectionGraph`. Its rules (STATE-DATA-10
isolated mode):

- `DemoGatewayFactory` returns a scripted `DemoGateway` pair (fixture-driven events and pages, no `Transport`); it is
  not in the `Set<GatewayFactory>` and has no `BackendKind`.
- The graph is created by `ConnectionRegistry.openDemo()` but **never** enters `live`; `graphFor` never returns it.
- No network: no `Transport`, `NetworkGate` bypassed, no `ReconnectParticipant` runs except the transcript's.
- Delivery (offline queue, receipts), read-state receipts, monitors, notifications, widgets and background services are
  disabled for it; its preferences are separate in-memory maps (B-STATE-014: independent maps, defensive copies).
- Test: `DemoGraphIsolationTest` (WP-04.6) — opening and using the demo graph writes no `oc.*` key, opens no socket and
  leaves `live` unchanged.

### 12.9 Automation gate (N4-D21)

`AutomationGate` (`:engine:connection`) reads `oc.automation.<profileId>` through `:engine:settings` and exposes, per
graph, live `StateFlow<Boolean>`s that update when the policy changes:

| Flow | Flutter behaviour | Gates |
|---|---|---|
| `reconnectAllowed` | `AutomationBehavior.reconnect` (10 call sites [V `events.dart:41,230`; `lifecycle.dart:158,273,335,383`; `status.dart:395,476`; `quiet_reconnect.dart:13`; `phone_server_healing.dart:576`]) | `EventStreamLoop` backoff and network-change retries (§6.2, §6.4), resume reconnects (§6.4), Paseo/Codex reconnect (§5.5), quiet reconnect (§12.4), phone-server healing reconnect (built-in Linux section) |
| `monitorOtherServers` | consent behaviour | profile monitor (§12.7) |
| `monitorQuota` | consent behaviour | quota reader (§12.7) |
| `reconcileQueuedSends` | already in §13.4 | offline-queue flush |

Rules: fail closed exactly as Flutter (`domain/automation_policy.dart:120-140`): a corrupt `oc.automation.<id>`, an
unknown version or a stored record without the behaviour all read as **not allowed**. A person's Try again
(`ConnectionSession.retryNow()`) and an explicit Connect always dial (B-LINUX-156 "explicit connect works with
auto-reconnect off"). Turning `reconnect` off retires the streams that are waiting to retry and keeps every cached
row and draft (Flutter `_stopDisallowedReconnect`). `FakeAutomationGate` in `:core:testing`; tests in WP-04.6 (rows
B-SEC-017, B-LINUX-156, B-PROTO-146).

### 12.10 Side connections (STATE-DATA-39; other saved servers in the one Conversations list)

`SideConnections` (`:engine:connection`, App scope) keeps one `ConnectionGraph` opened with `Purpose.Side` per other shown
server, so its conversations join `ChatFeed` (17 §1.6). Rules, ported from `connection/side_connections.dart`:

| Rule | Value |
|---|---|
| Eligible | saved **OpenCode** profiles (Paseo and Codex servers appear through the monitor, AGENTS-BACKENDS-44, not as sides) whose stored data is readable (no re-entry flag, secret readable) and that are **not the same server as the main one**, compared by scheme, host and port (`ServerIdentity`, 04 §4) |
| Default shown | on-this-phone servers (the built-in Linux server and any loopback address) are shown; remote servers are opt-in |
| Override | `oc.chatListShown.<serverId>` (bool; set from Conversations' connections chip, 07 B04); the active connection cannot be hidden |
| First paint | saved rows `oc.sideFeed.<serverId>` paint at once and are replaced by live rows (they never claim running state) |
| Role | a side graph **never becomes the active profile** and **never owns profile-wide services**: no monitors, background live mode, widgets, schedules, quota or usage monitors, thermal guard or built-in recovery start for it (`ProfileServices` are bound to `Purpose.Primary` only; `SideOwnsNoServicesTest`) |
| Edited profile | a side whose profile changes reconnects only when its connection signature changes (base URL, username, backend, trust, credential revision); a name change does not reconnect (`SideSignatureTest`) |
| In-app Ubuntu sides | when the side is the built-in phone server and it is stopped, it is started **once** per app process through `LocalServerStarter.ensureRunning(StartReason.SideConnection)` (an interface in `:core:domain`, bound by `:engine:runtime`'s `PhoneServer`: `connection` sits above `runtime` in ADR §6.2 and may not import it); a failure leaves the side's saved rows with the quiet "not running" line, no retry loop |
| Closing | removing the profile, turning its row off or it becoming the main connection closes the side graph; a side that becomes main is reopened as `Primary` (never two graphs for one server) |
| Limits | sides are read with the main graph's paging limits; one unreachable side never hides the others' rows (sources keep partial failures) |

Tests (`:engine:connection` commonTest, WP-04.6): `SideEligibilityTest`, `SideDefaultsTest`, `SideOwnsNoServicesTest`,
`SideSignatureTest`, `SideBuiltinStartOnceTest`. Slice 2 (local sides) with S2-CHATS; remote opt-in with E2E-36 in slice 4.

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
  `oc.certificatePin.*`, `pw.*`, `oc.codexToken.*`, `oc.agentHostSecret.*` (phone agents helper) and
  `oc.byoHostSecrets.*` (BYO host device tokens) (secrets), `oc.autoApprove.*` (incl. the `"*"` server-wide key and the
  `.agents` variants), `oc.automation.*` (automation policy, §12.9), `oc.location.*`, `oc.sessionInventory.*`,
  `oc.chatTail.*`, `oc.mainFeed.*`, `oc.sideFeed.*` (first-paint caches). The migration section owns the mechanics;
  WP-04.14 checks them on the upgrade journey.

### 13.3 Entry format and limits (read and write exactly)

```json
{"id":"…","profileID":"…","sessionID":"…","text":"…",
 "attachments":[{"mime":"…","filename":"…","url":"data:…|file:…|http(s):…"}],
 "mentions":[{"name":"…","value":"…","start":0,"end":5}],
 "modelProviderID":"…","modelID":"…","agent":"…","variant":"…",
 "createdAt":1787961231753,"error":"…","dispatchedAt":1787961231999}
```

Limits: 50 entries, 14 days, 60 MiB total, 20 MiB per entry. The two size limits are measured exactly as Flutter's
`payloadBytes` [V `offline_queue.dart:115-117`]: Kotlin `String.length` (UTF-16 code units) of `text` plus the sum of
every attachment `url.length` — **not** bytes — so the same stored queue keeps and evicts the same entries; a vector
with non-ASCII text just under and just over the cap pins it. An entry over 20 MiB is refused (the composer keeps the
text); eviction is oldest first, reported once ("Discarded N queued drafts"); a single oversized entry is never evicted
into nothing; unreadable timestamps are kept (B-STATE-036, 046). The Flutter writer emits **every** key, with explicit
`null` for absent optionals [V `offline_queue.dart:119-148`]; the Kotlin codec for this type uses its own `Json` with
`explicitNulls = true` (not `WireJson`) so a queue written by native reads the same in a byte diff.

### 13.4 Flush algorithm

Runs on the queue's serial dispatcher; one flush at a time; requests during a flush coalesce into one more pass.

1. Preconditions: graph `Connected` (or loopback reachable); `capabilities.offlinePromptQueue`; not in OpenCode-2
   history migration; automation policy allows `reconcileQueuedSends`, unless the entry was explicitly resent
   (B-STATE-038); the migration journal's `outboundHold` is not set (after a data restore the queue waits for the
   person's review, 05 §9.4 step 5). An entry whose dispatch id is in `outbound-tombstones.json` is removed, never
   sent.
2. Take entries of the **active profile**, oldest first; count others ("N drafts waiting for other servers", B-STATE-039).
3. For each entry:
   1. Re-check `(profileId, baseUrl, directory, workspace)` and that the graph is the same instance after every
      suspension (B-STATE-037). A draft discarded meanwhile is never sent.
   2. On staged-revert servers, read the session; staged → keep the entry with a `Conflict(SessionRevertPending)` error.
   3. Apply the entry's model/agent through `SessionSelectionGateway` when it was an intentional choice.
   4. Write `dispatchedAt` (queue lane, `commit()`); refusal stops the batch.
   5. Re-check policy; if it changed, roll the marker back (a refused rollback keeps the conservative marker).
   6. Dispatch: with `commandReceipts`, through the receipt journal (§13.6) with journal `commandID = "queue:<entryId>"`
      and a fresh wire id from `CorrelatedPromptGateway` (`Oc2ClientIds.message()`, §7.13) in
      `PromptRequest.clientMessageId`; `queue:<entryId>` is local only and never sent. Otherwise plain `prompt`.
   7. Accepted → append the entry's dispatch id to `outbound-tombstones.json` (05 §9.4; outside every snapshot, so a
      data restore cannot bring the entry back as unsent), then persist removal. Removal refused → leave the marker,
      note "accepted, unrecorded" (a resend would be a guaranteed duplicate, so it is not offered as automatic).
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

**Session creation uses the same journal** (`state/session_creation_receipts.dart`, row B-AGENT-226,
`SessionCreationReceiptTest` in WP-04.8): the client id comes from `Oc2ClientIds.session()` and is claimed in the
journal before `POST /session {id, …, location}`; a lost answer is looked up with `GET /session/{id}` and **never**
re-created; `NotFound` stays uncertain; the identity and owning tab survive restart; another tab cannot borrow the
command; a changed profile, endpoint or gateway scope cannot check an old receipt.

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
| `B-PROTO-128 POST not resent by the stack` | through the full Ktor client: (1) a response that closes the socket after reading the request, on a reused pooled connection [U exact 5.x API name]; (2) **503 with `Retry-After: 0`** to a POST with a JSON body, to a bodyless POST and to a DELETE; (3) the negotiated protocol is HTTP/1.1 even when the TLS server offers h2 (so 421-on-coalesced cannot occur) → in every case **exactly one request recorded** by mockwebserver3; (1) returns `Refused(DispatchUncertain)` |
| `B-PROTO-129 redirects never followed` | 301/302/307 with `Location` → `Rejected`, no second request |
| `B-PROTO-130 no proxy` | with a system proxy property set, the call goes direct |
| `B-PROTO-131 WebSocket handshake, trust and caps` | server offering `permessage-deflate`, wrong Accept, wrong subprotocol → refused; 8 MiB + 1 message → close 1009; **a pinned self-signed PTY endpoint connects through the connection's `Transport.ws`; a changed certificate is refused (`Certificate(Changed)`); `ws://` to an origin without consent is refused before any byte (server records zero connections); a server that accepts TCP and stays silent fails `TimedOut` at 8 s; once upgraded, an idle socket is not cut by the call timeout** |
| `B-PROTO-144 downloader redirects` | §3.10 cases |
| `B-PROTO-148 no leaks on close` | 100 ephemeral transports opened and closed: no live OkHttp dispatcher threads, pooled sockets or engine coroutines remain |
| `Oc2PathEncodingWireTest` | the §3.6 path-segment table through `HardenedOkHttp`: the request line recorded by mockwebserver3 keeps `..`/`%2e` segments encoded |
| `B-PROTO-072 socket reuse` | two calls 4 s apart share one TCP connection (15 s keep-alive) |

### 14.4 Recorder (`:tools:contracts`)

`./gradlew :tools:contracts:run --args="record --server oc2 --version 2.1.1 --scenario prompt-roundtrip --target http://127.0.0.1:4197"`:
a Ktor-server reverse proxy between a scripted client (the live-proof steps, or a Maestro flow pointed at the proxy) and
a real server. Mandatory scrubbing: drop `Authorization`, cookies and `Sec-WebSocket-Protocol`; scrub API keys from
`/provider` and `/config` bodies; **fail the recording** if any value matches the per-run password (read from a file) or a
provider-key pattern (`sk-`, `ghp_`, `xox`, `AIza`, JWT, PEM). Never print the password.

Server setup on the VPS (AGENTS.md traps apply): `npm install -g --prefix <scratch> @opencode/cli@2.1.1`, HOME and XDG in
the scratch dir, `OPENCODE_PASSWORD` from a generated file; start it by **absolute path**,
`<scratch>/bin/opencode2 serve --port 41xx --hostname 127.0.0.1` (never `opencode` from PATH: the VPS may also carry
OpenCode 1's `opencode`), and check `<scratch>/bin/opencode2 --version` equals the pin before recording; find the
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
pending-permission list (`oc2-stuck-tools-root-cause`) — **expected:** the requests are marked unreadable (never
"none"), cards that arrived live stay, plain words with Try again, and on the phone's own server "Update the phone's
OpenCode" when `/api/info` is below the pin (§7.10, §11.6, B-PROTO-142); stream drop mid-delta; lost `tool.success` then
step end; a result for an unseen call after reconnect; a steer sitting in the inbox during a long tool; a cut prompt
response; an OpenCode 1 server answering `/global/health`; an OpenCode 2 web UI returning HTML 200 on the OpenCode 1
path; a 503 with `Retry-After: 0` on a prompt POST (sent once).

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
- **When a row's text and its cited Flutter test disagree, the Flutter test wins.** The test-writer writes the test from
  the Flutter assertion and reports the row to the coordinator for a catalog correction (§15.5 lists the ones known
  today, e.g. B-SEC-120).

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
| 040 | `chat_oc2_step_gap_test`, `connection_v2_requests_test` | transcript · `RunLifecycleOc2Test` (**co-owned**: the chat lead writes and owns the test in `:engine:transcript`; WP-04.9 supplies the OpenCode 2 fixture scenarios and the adapter half in `Oc2EventAdapterTest`) | scenarios | 2 | S |
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
| B-SEC-119..125, 153, 154 | security · `AddressRulesTest`, `ServerUrlRulesTest`, `TrustOriginTest`; profiles · `TrustPersistenceTest`; connection · `CleartextConnectRefusalTest` | 1 |
| B-SEC-055, 056 | oc2 · `CredentialIngressTest` (with the security section's `Redactor`) | 1 |
| B-SEC-094, 144, 145 | security section (`Redactor`); this section only calls it | 1 |
| B-SEC-107, 108, 124 | oc2 · `Oc2PairingCodecTest`; connection · `PairingDialerTest` | 2 |
| B-SEC-113, 114, 115, 116, 109, 110, 111 | paseo · `PaseoEndpointTest`, `PaseoPermissionMappingTest`, `HostAgentPermissionTest`, `PaseoHandoffTest`, `PaseoUsageTest` | 3 |
| B-SEC-174 | platform · `TailscaleSetupTest`; security · `ServerUrlRulesTest` | 2 |
| B-SEC-180 | widgets · `WidgetActionRunnerTest` | 4 |
| B-SEC-001, B-AGENT-001 | — (direct ACP) | R (N4-O1) |
| B-SEC-158 | — (session-address links, off) | R (N4-O4) |
| B-SEC-112, B-AGENT-152 | — (agent-browser scaffolding retired: 17 N17-D1, owner said no browser 2026-11-01) | R |
| B-STATE-008, 009, 033..046, 066, 067 | delivery · `OfflineQueueTest`, `OfflineQueueConcurrencyTest`, `DispatchMarkerTest`, `ReceiptJournalTest`, `QueuedPromptRemovalTest` | 2 |
| B-STATE-010 | attention · `AttentionFeedConnectionTest` | 4 |
| B-STATE-011 | domain · `DirectoryPathTest` | 1 |
| B-AGENT-013, 142..191 | paseo · one test class per Flutter file (`PaseoIdleBusyTest`, `PaseoUncertainStartTest`, `PaseoToolOutputTest`, …); agents engine for 153/154 (feed), integrations for 189..191 (usage) | 3 (usage 5) |
| B-AGENT-226 | delivery · `SessionCreationReceiptTest` (OpenCode 2 session-creation receipts, §13.6) | 2 |
| B-AGENT-203 | attention · `ProfileMonitorPolicyTest` (consent, `onWifi`, loopback exempt, Paseo list-before-requests) | 4 |
| B-AGENT-244 | profiles · `TailscaleGuidanceTest` | 1 |
| B-AGENT-219, B-SEC-136 | connection · `TransientProfileTest` (never persisted; refused by schedule and widget runners) | 3 |
| B-CHAT-118 | transcript · turn-stall classifier, with connection's `HelperHealthProbe` fake | 3 |
| B-SEC-142, B-SEC-163, B-STATE-116 | connection · `ReadStateReceiptTest` (consent-gated view receipts, §7.10) | 4 |
| B-SEC-017, B-LINUX-156 (connection half) | connection · `AutomationGateTest` (§12.9) | 1 |
| B-STATE-014 | connection · `DemoGraphIsolationTest` (§12.8) | 5 |
| B-AGENT-051..070, 220 | codex · `CodexTransportTest`, `CodexGatewayTest`, `CodexSteeringTest`, `CodexAccountSessionTest`, `CodexHandoffRecordTest` | 5 |
| B-CHAT-005 | oc2 · `Oc2OpenCallSettlingTest` | 1 |
| B-CHAT-070..072 | paseo · `PaseoStreamingTest`, `PaseoNativeQuestionsTest` | 3 |
| B-NAV-007..009 | connection · `ConnectionFailureExplainTest`; words · `NetFailureWordsTest` | 2 |
| B-ANDR-037 | platform (Robolectric) · `NetworkMonitorTest` | 2 |
| B-ANDR-056..064, 103..107, 118, 119 | attention / widgets / background (§12.7) | 4 |
| B-BUILD-003, 006; B-KIT-013; B-L10N-018; B-XPLAT-012, 013 | quality · `CapabilityLedgerTest`; domain · `PlatformCapabilitiesTest` | 1 / 4 |
| B-LINUX-114, 147, 223, 237, 239 | built-in Linux section (they only use this transport) | 3 |
| B-STATE-098 | profiles · `ServerProfileReentryTest` (failed secure write leaves metadata and cache unchanged; failed secure delete keeps an active profile) | 2 |
| B-STATE-103, 104 | connection · `SessionInventoryCacheTest` (bounded preview exact to profile and location, read-only; swept with the profile; corrupt or foreign previews read as empty) | 2 |
| B-STATE-105…107 | connection · `SessionInventoryPagingTest` (details never hide a concurrent busy status; late pages never overwrite newer live updates; partial refresh keeps older metadata; direct reads coalesce; confirmed delete or 404 clears) | 2 |
| B-STATE-108…110 | connection · `SessionLinkBindingsTest` (only the canonical verified binding stored; closing drains; unreadable storage fails closed). Session-address links stay retired (N4-O4); the bindings store remains because verified `opencode-mobile://session` links use it | 2 |
| B-STATE-117, 118 | connection · `SessionSelectionSyncTest` (cold-start model fallback per conversation; OC2 metadata stays server-owned; failed writes keep the confirmed selection) | 2 |
| B-STATE-055 | connection · `WorkListScopeTest` | 2 |
| B-STATE-026, 027 | diagnostics · `LifecycleReportTest` (11 §22.1; race-safe refresh) | 3 |

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
| B-PROTO-133 | With no network, a Public or Tailnet stream or socket waits without spending retries and reconnects at once when the network returns; the phone's own server and LAN/hotspot/tether servers never wait. | integration | net |
| B-PROTO-134 | One OpenCode 2 event connection feeds both the project channel and the all-projects channel. | contract | oc2 |
| B-PROTO-135 | A prompt receipt with the real inbox type `user` confirms admission; other types, sessions or ids stay unconfirmed. | contract | delivery |
| B-PROTO-136 | The event registry equals the typed, pass-through and ignored lists; every documented type is in exactly one. | unit | oc2 |
| B-PROTO-137 | Decoding errors never contain the input JSON (a fake key in a body never appears in an exception message). | unit | common |
| B-PROTO-138 | A change of default network cancels every connected non-loopback stream and socket and reconnects it at once, evicts their idle pooled connections and retries waiting ones; loopback (phone server) connections are untouched. | integration | connection |
| B-PROTO-139 | The network security config stays "cleartext permitted, system CAs only", with at most the certificate-transparency child of 16 §1.7 (guard against tightening or loosening). | unit | quality |
| B-PROTO-140 | Client message and session ids have OpenCode's sortable shape (prefix, 12 hex time, 14 base62; sessions descending); the offline queue's `queue:` id never reaches the wire. | unit | oc2 |
| B-PROTO-141 | Only agent, config, plugin, web-search, catalog and integration/credential events trigger a reload; file-system, command, skill, project, worktree, LSP, MCP and branch events do not. | unit | oc2 |
| B-PROTO-142 | A 4xx on the pending-permission list marks requests unreadable (never "none pending"), keeps live cards, and an outdated phone server shows "Update the phone's OpenCode". | integration | requests |
| B-PROTO-143 | The server terminal socket offers no compression and closes with a plain failure above its message cap (N4-D18; owner OK N4-O6). | contract | oc2 |
| B-PROTO-144 | Downloads follow at most five HTTPS redirects themselves, never send credentials or Range to a hop, refuse a redirect to http, and resume only when Content-Range matches. | contract | net |
| B-PROTO-145 | Every request of the app goes through a hardened transport: no other HTTP client is constructed, and app-authored fixed origins and loopback helper ports have their own allowlists. | unit | quality |
| B-PROTO-146 | Automatic reconnects (backoff, network change, resume, Paseo/Codex) happen only while the server's reconnect setting allows them; Try again always dials; a corrupt or missing policy means no automatic reconnect. | integration | connection |
| B-PROTO-147 | A stalled all-projects consumer never delays the open chat's live text; a view that overflows says so and is refreshed, never silently dropped. | integration | oc2 |
| B-PROTO-148 | Closing a connection releases its engine, sockets, dispatcher threads and coroutines (100 open/close cycles leave nothing). | contract | net-okhttp |

**Catalog corrections (coordinator applies them to `../catalog/`):**

| Row / flow | Today's text | Corrected text | Evidence |
|---|---|---|---|
| B-SEC-120 | "Plain http to a private address is accepted (with credentials)" | "The URL validator accepts a plain-http private address (with credentials); connecting to it still needs per-origin consent with the private-network wording; public addresses get the stronger wording; loopback and https never need confirmation; a bare private address still gains https." | `private_network_cleartext_test.dart:120-137` (`serverUrlNeedsCleartextConfirmation` is true for every private URL) |
| E2E-04 step 2 | "Add the plain-HTTP server on a private address with credentials: accepted without a prompt" | "…: the address is accepted and a consent prompt with the private-network wording names the origin; nothing is sent before yes" | same |

---

## 16. Work packages (WP-04.x)

Every package: one agent, one worktree, one branch; reads `contracts/<slice>.md`; writes the listed rows red first; T0 =
`./gradlew :<module>:desktopTest --tests '*B-PROTO-0NN*'` for KMP modules and `./gradlew :core:net-okhttp:test --tests
'*B-PROTO-12*'` for the JVM module while editing; T1 on the VPS before hand-off; format changed files only
(`tool/format-changed.sh`). Builders: Codex for core/protocol/engine (owner rule "Codex backend, Claude frontend");
Opus reviews the event adapter because it feeds the transcript. Every row of §15.2 (S) and §15.3 appears in exactly
one WP below (checked in §20).

| WP | Slice | Builder | Module(s) | Rows (red first) | Done when |
|---|---|---|---|---|---|
| WP-04.0 | 0 | Codex + coordinator | `:core:common`, `:core:model`, `:core:domain`, `:core:security` (types only) | B-PROTO-137; default-flags snapshot (`capabilities/default.json`); B-BUILD-003, 006, B-KIT-013, B-L10N-018, B-XPLAT-012, 013 (capability ledger + `PlatformCapabilities`) | 03 amended per §11.0 first; then gateway interfaces and the facet table (§11.1), `ServerEvent` + `EventGateway` (§11.2), `ServerCapabilities` + snapshots, the `Failure` amendments (§3.8), `StreamStatus`/`StreamSignal`/`NetworkState`/`NetworkStatus` (`:core:model`), `GatewaySpec`/`ProbeTarget`/`ServerRunsTools` (`:core:domain`), `TransportKind`/`TransportPermit` (`:core:security`) compile for android+desktop and are frozen in `contracts/slice-1.md` |
| WP-04.1 | 0 | Codex | spikes | — | P-N1, P-N2, P-N3, P-N5 pass (§17) |
| WP-04.2 | 1 | Codex | `:core:security`, `:engine:profiles` (one test) | B-SEC-119..125, 153, 154 (unit parts), 113 (rules), 123, 174 (rules); B-AGENT-244 | all vectors green (incl. `localhost.`, `::ffff:127.0.0.1`, `http://192.168.1.20:4096` needs consent, ws→http origins); no `java.*` in commonMain (iOS klib job compiles) |
| WP-04.3 | 1 | Codex | `:core:net-okhttp`, `:quality` (one test) | B-SEC-007, B-PROTO-072, 128..131, 144 (wire half), 148; B-PROTO-139 | §14.3 table green on the VPS (`:core:net-okhttp:test`); E2E-04 steps 1, 3, 4 pass on the emulator release build together with WP-04.9 |
| WP-04.4 | 1 | Codex | `:core:net` | B-PROTO-033, 132, 133, 144 (state machine); `SseBackpressureTest` | parser fuzz 10k cases; loop timing table test under virtual time; peak-heap budget test |
| WP-04.5 | 1 | Codex (Opus reviews adapter) | `:protocol:oc2` | B-PROTO-010..019 (incl. 016 snapshots), 021, 027, 034, 037..039, 047, 061, 062, 089..093, 134, 136, 140, 141, 147; B-CHAT-005; B-SEC-055/056 | all 24 Flutter fixtures decode to the same variants as Dart; live proof steps 1–25 green on 2.1.1; **starts only after P-N4 (WP-04.7)** |
| WP-04.6 | 1 | Codex | `:engine:connection` | B-PROTO-094, 095, 124, 138, 146; B-STATE-011, 014; B-SEC-017, B-LINUX-156 (connection half) | `ReconcileOrderTest` with fake `ReconnectParticipant`s (§12.3), `AutomationGateTest`, `DemoGraphIsolationTest` green with `FixtureEngine` |
| WP-04.7 | 1 | Sonnet (test-writer) + Codex | `:core:testing`, `:tools:contracts`, `native/fixtures` | — | fixtures copied with SHA-256 manifest; recorder refuses a planted password; 2.1.1 `openapi.json` committed; **P-N4 done** (gates WP-04.5) |
| WP-04.8 | 2 | Codex | `:engine:delivery`, `:protocol:oc2` | B-STATE-008, 009, 033..046, 066, 067; B-PROTO-023..025, 028, 032, 042, 043, 045, 073, 074, 135; B-AGENT-226 | queue reads a real Flutter `oc.offlineQueue` blob unchanged; flush tests green; `SessionCreationReceiptTest` green |
| WP-04.9 | 2 | Codex + Sonnet (servers UI) | `:engine:connection`, `:android:platform` | B-PROTO-020, 064..068, 081, 088; B-PROTO-040 (OC2 fixtures and adapter half; test co-owned with the chat lead); B-SEC-107, 108, 124; B-NAV-007..009; B-ANDR-037 | E2E-02, E2E-03, **E2E-04**, E2E-05, E2E-10 pass on the release build (T3); **P-N6 done** and its fallback decided |
| WP-04.10 | 3 | Codex | `:protocol:paseo` | B-PROTO-070, 071; B-AGENT-013, 142..191; B-SEC-109..111, 113..116; B-CHAT-070..072; §5.5 Paseo reconnect tests | Paseo live proof + E2E-30 on the emulator; questions and plan approval round-trip as `QuestionAsked`/`QuestionSettled` |
| WP-04.11 | 3 | Codex | `:protocol:oc2`, `:engine:requests` | B-PROTO-029, 044, 048, 063, 069, 102, 103, 142; B-AGENT-219, B-SEC-136; B-CHAT-118 (helper probe) | forms/permissions/questions hydrate after a forced reconnect (fault replay); Undo-window answer reaches the server once across `BackgroundIdle` (§6.4) |
| WP-04.12 | 4 | Codex | `:protocol:oc2`, attention/widgets/background bindings | B-PROTO-031, 046, 060, 083, 096..101, 127; B-STATE-010; B-ANDR-056..064, 103..107, 118, 119; B-SEC-180; B-AGENT-203; B-SEC-142, 163, B-STATE-116 | E2E-07, E2E-21 green |
| WP-04.13 | 5 | Codex | `:protocol:codex`, `:protocol:oc2` (PTY, shells, files, MCP, integrations, plugins, web search, usage), `:engine:integrations` (quota, registry) | B-PROTO-022, 026, 030, 035, 036, 049, 058, 059, 078..080, 082, 084..087, 104..108, 121..123, 125, 126, 143, 145; B-AGENT-051..070, 220; §5.5 Codex reconnect tests | Codex fixture proof; **E2E-29** green; PTY reconnect resumes at the right cursor on a pinned self-signed server |
| WP-04.14 | 6 | Opus (migration) + Codex | upgrade journey checks | — | after `adb install -r` over the **latest local-key Flutter build of the same signing lineage as the owner's phone** (signer `1DE5BF08…D60C`, checked with `apksigner verify --print-certs` before the run; the release section owns the lineage choice — the public 2.1.0+57 is CI-signed and cannot be the base): servers connect without re-entering passwords; plain-HTTP consents and pins still apply; `oc.autoApprove.*` modes survive and a child of an auto-approved parent still runs tools without a `PATCH`; `oc.automation.*` still gates reconnects; a queued prompt is still queued, not resent; pending receipts reconcile; final OC2 and Paseo live proofs at the pinned versions |

---

## 17. Proofs before fan-out (each ≤ 2 days, each with a fallback and an owning WP)

| # | Owner | Proof | Acceptance | Fallback |
|---|---|---|---|---|
| P-N1 | WP-04.1 | WebSocket over OkHttp upgrade + Ktor `RawWebSocket` against a scratch Paseo 0.11.2 daemon and a mockwebserver3 WS endpoint | hello/answer round trip; 101 validation; no extension negotiated; subprotocol echo checked; 8 MiB + 1 → 1009; ping every 20 s; bounded `channelsConfig` compiles from `BoundedWsChannels.kt`; a pinned self-signed endpoint connects through the derived client | own RFC 6455 framer in `:core:net` (≈ 350 lines) |
| P-N2 | WP-04.1 | Pinned TLS on Android: `PinnedLeafTrustManager` + hostname verifier on the API 37 emulator with Conscrypt, against an okhttp-tls self-signed server on the PC (`10.0.2.2`) | matching pin works; changed cert latches; expired matching cert is its own error; inspection: server logs zero requests | none (security); escalate to Opus; optional repeat on an API 26 AVD |
| P-N3 | WP-04.1 | SSE through Ktor's OkHttp engine against live OpenCode 2.1.1 | first event < 1 s on loopback; heartbeat measured (expect 15 s); a paused proxy triggers the 45 s stall reconnect; `HttpTimeoutConfig` names compile | raw OkHttp call for the stream inside `:core:net-okhttp` |
| P-N4 | WP-04.7 (gates WP-04.5) | OpenCode 2.1.1 and 2.2.5 probe on the VPS | `openapi.json` of both recorded; route diff report; heartbeat; receipt `type` = `user`; session-log route present; `/api/info` shape; **largest PTY replay frame** after a long session (sets the N4-D18 cap); pending-permission list on 2.0.29 vs 2.1.1; result in `docs/qa/native-oc2-probe-2026-11/README.md` | if 2.1.1 differs from the fixtures, re-record before WP-04.5 |
| P-N5 | WP-04.1 | Retry traps | mockwebserver3 through the full Ktor client: POST on a reused connection, disconnect after request; 503 + `Retry-After: 0` on a JSON POST, a bodyless POST and a DELETE; TLS server offering h2 → exactly one request each, HTTP/1.1 negotiated | none (must hold) |
| P-N6 | WP-04.9 | Network change on the emulator (`svc wifi disable/enable`, `svc data`) with a remote stream open, a phone-server stream open and a prompt in flight | remote stream back < 2 s after the new default network (connected streams are cancelled and redialed, §6.4); the phone-server stream is untouched; server sees the prompt once | widen to a full rebuild of non-loopback graphs on network change (WP-04.9 records the decision) |

---

## 18. Risks

| Risk | Likelihood / impact | Mitigation |
|---|---|---|
| OpenCode 2 moves weekly; 2.2.x unprobed | high / medium | dialect table + route drift test + P-N4; pin stays 2.1.1 at the switch |
| Ktor/OkHttp defaults silently re-enable retries or redirects in a future version | medium / high | B-PROTO-128..130 (incl. the 503 `Retry-After: 0` and h2 cases) run at every T2; Konsist bans |
| A feature agent builds its own HTTP client (redirects, proxy and retries on; no consent or pin) | medium / high | Konsist ad-hoc client ban + fixed-origin and loopback permits (§4.2, §11.7, B-PROTO-145) |
| The frozen contracts drift from 03 again | medium / high | §11.0 is the single reconciliation; WP-04.0 starts only after 03 is amended |
| The hand-written WS path has a framing bug | medium / medium | Ktor's `RawWebSocket` does the framing; our code is handshake + assembly only; P-N1 against a real daemon |
| Custom trust manager behaves differently under Conscrypt | low / high | P-N2 on the emulator; JVM tests cover the logic |
| Event-adapter rules lost in the port (stuck "Working", lost tool results) | medium / high | port the tests first (B-PROTO-015, B-CHAT-005, B-PROTO-040, 088); fault replays §14.7; Opus review |
| The 45 s stall timer fires on a server that heartbeats less often | low / medium | P-N4 measures; the constant lives in `Oc2Wire` with the measured value in its comment |
| Offline queue blob (up to 60 MiB) slows cold start before Room | medium / medium | ADR §9.8 trigger measures it; Room moves earlier if hit |
| Receipt fix changes behaviour the owner is used to (prompts confirm at once instead of after delivery) | low / low | N4-O5 tells the owner; it only removes the delayed confirmation and the review prompt while a prompt waits in the inbox |
| A slow consumer or a large frame exhausts the heap | low / high | bounded queues with byte budgets (§5.2, §6.5, §6.6); peak-heap tests |
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
| N4-O5 | The Flutter app's prompt dispatch always reports "uncertain" (it checks a type the server never sends); it confirms only after delivery, through the message lookup. The native app accepts the real type `user` at dispatch and in the inbox lookup. Fix only in the native app, or also patch Flutter now? | Native only (Flutter is frozen; the effect today is a delayed confirmation and a "check this prompt" review while the prompt waits in the inbox, never a duplicate) |
| N4-O6 | Three small transport changes from Flutter: (a) a dead network connection is noticed after 45 s of silence (N4-D7); (b) on a Wi-Fi/mobile/Tailscale switch, remote servers reconnect at once while the phone's own server is left alone, and with no network only remote and Tailscale servers wait (N4-D8); (c) the server terminal stops offering compression and gets a size cap (N4-D18). OK? | Yes (each is listed with its catalog row; parity is kept everywhere else) |

---

## 20. Done checklist for this section

- [ ] 03 is amended per §11.0, and 11, 16 and the catalog per §22, **before** the freeze.
- [ ] `contracts/slice-1.md` freezes §11.1 (interfaces and the facet table), §11.2 (`ServerEvent`, `EventGateway`),
      §11.3, the `Failure` amendments (§3.8), `StreamStatus`, `NetworkState`/`NetworkStatus`, `GatewaySpec`,
      `ProbeTarget`, `TransportKind`.
- [ ] Every S row of §15.2 and §15.3 has a passing native test named with its id; R and L rows are approved (§19, ADR D6).
- [ ] A script check passes: every row of §15.2 (S) and §15.3 appears in exactly one WP of §16, and every proof of §17
      has an owning WP.
- [ ] New rows B-PROTO-128..148 are in the catalog and green; the §15.5 catalog corrections are applied.
- [ ] `native/fixtures` holds the copied Flutter fixtures with a SHA-256 manifest and the 2.1.1 recordings; the T2 secret scan passes.
- [ ] The OpenCode 2 and Paseo live proofs pass on the pinned versions on the VPS; results recorded in `docs/qa/`.
- [ ] E2E-02, 03, 04, 05, 07, 10, 21, 29, 30 pass on the native **release** build on the emulator.
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
  - Added in the review revision (same jars): OkHttp `RetryAndFollowUpInterceptor.kt:95-110, 228-286` (503
    `Retry-After: 0` and 421 resends, one-shot exit, 408 honours the flag), `RealCall.kt:478-485` (`upgradeToSocket` →
    `timeoutEarlyExit`); Ktor `OkHttpEngine.kt:43-59` (engine-close waiter), `:225-256` (body conversion);
    `ktor-websockets` `RawWebSocketCommon.kt:31-38` (`channelsConfig` default `UNLIMITED`),
    `WebSocketChannelsConfig.kt:133-160` (`@InternalAPI` constructor, `bounded`).
  - Flutter files read in the revision: `lib/api2/gateway_operations/ops_dev.dart:280-300`, `ops_sessions.dart:73-80`,
    `ops_shells.dart:148-156`, `lib/api2/client.dart:155-175`, `lib/api2/gateway.dart:400-440`,
    `lib/api2/gateway_events.dart:183-198, 277-280, 516-563, 619-656`, `lib/api2/sse2.dart:186-300`,
    `lib/domain/command_receipts.dart:150-159`, `lib/domain/command_receipt_transport.dart:80-125`,
    `lib/domain/automation_policy.dart`, `lib/domain/phone_agent_host.dart`, `lib/voice/model_download.dart:225-275`,
    `lib/state/connection/{events,lifecycle,connect,permissions}.dart`, `lib/state/offline_queue.dart:115-117`,
    `lib/state/byo_host_controller.dart:54-61`, `lib/state/connection/phone_agents_backend.dart:120-131`,
    `lib/builtin/agents/phone_agents_host.dart:174-193, 790-800, 925-965`, `lib/paseo/gateway.dart:58-74, 368-372`,
    `lib/paseo/gateway/{questions,lifecycle}.dart`, `lib/codex/gateway.dart:101-107, 966-1040`,
    `lib/orchestration/adapters/gascity/gascity_probe.dart:24-38`,
    `android/.../MainActivityBackgroundChannel.kt:39-43`, `test/private_network_cleartext_test.dart:118-137`.
- Owner memory: `steer-not-queue`, `no-raw-errors-rule`, `oc2-stuck-tools-root-cause`, `opencode2-stable-2.0-port`,
  `dev-freeze-native-rewrite-2026-11-01`, `freeze-owner-decisions-2026-11-01`, `codex-backend-claude-frontend`,
  `actions-name-their-target`, `build-vps`, `quiet-hours-vps-only`.

---

## 22. Changes other sections must take (coordinator applies before the slice-0 freeze)

| Section | Change | Why |
|---|---|---|
| 03 §10.1, §10.3, §10.4, §10.7, §13.4 | every "Amend 03" item of §11.0 | one source of names; the frozen contracts must compile under ADR §6.1 |
| 03 §10.4 `DeviceFacts` | drop `network`; `NetworkStatus` (`:core:model`) is the one network reading | two readings would disagree |
| 11 §RuntimeInstall | `download` calls `ResumableDownloader` (§3.10) instead of its own "resume, manual redirects ≤ 5, SHA-256" loop | one downloader; the policy (HTTPS every hop, no credentials, Range only to the final URL) lives in one place |
| 11 §14.6 | tailnet rules are IPv4 `100.64.0.0/10` + `.ts.net` only (no `fd7a:115c:a1e0::/48`, Flutter parity, B-SEC-113/123); delete "the handoff descriptor fetch … is 04's" (session-address links are retired, N4-O4) | 04 §4.1, §10 |
| 16 §1.7 rule 4 | already compatible: `NetworkSecurityConfigTest` is B-PROTO-139; 04 §4.7 now names N16-D4 | — |
| 07 C39 | `DemoConnectionGraph` follows §12.8 (`ConnectionRegistry.openDemo`) | isolated mode rules |
| Catalog | §15.5 rows B-PROTO-140..148 and the B-SEC-120 / E2E-04 step 2 corrections | row text contradicted the Flutter test |
| Agents section (17 §1) | `AgentHost.ensureRunning` before dialing the helper (17 §1.2); `ProfileSource` for `<id>.agents` (17 §1.6); owns `oc.agentHostSecret.*` (17 §1.12) | §8, §12.1 |
| Requests / delivery engines | contribute `ReconnectParticipant`s (§12.3) and the `BackgroundIdle` Undo-window flush (§6.4); requests installs `ServerRunsTools` (§7.9) | engine order (ADR §6.2) |

---

## Review notes (2026-11-01 revision)

Every issue was checked against the Flutter source (`oc_app-batch1`), 03, 11, 16, the catalog and the OkHttp/Ktor
source jars before editing. All 42 issues were accepted, two of them only in part. None were rejected outright.

**What changed, by issue**

1. *WebSocket trust (blocker).* The connector is now part of each connection's `Transport` (§3.3). It is derived from
   the same hardened client with `callTimeout` for the handshake (§3.2, §5.2). New B-PROTO-131 cases cover a pinned
   PTY, a changed certificate, refused `ws://` and an 8 s silent server.
2. *Downloader redirects (blocker).* `ResumableDownloader` follows up to 5 HTTPS redirects itself and is the only
   downloader. 11's RuntimeInstall must call it (§3.10, §22). Row B-PROTO-144 added.
3. *Names vs 03.* Added §11.0, which makes 03 the single source and lists every amendment. §3.8, §4.1, §6.3, §11.1,
   §11.2 and §12.1 now use 03's names: flat `Failure` with detail fields, `HostClass`, `BackendKind.OpenCode2`,
   `connect(GatewaySpec)`, `ServerEvent`, `SessionId`, `prompt(): Unit` and `abort`. N4-D12 now agrees with 03.
4. *Module placement.* `StreamStatus`, `NetworkState` and `NetworkStatus` moved to `:core:model`. `GatewaySpec` and
   `ProbeTarget` are in `:core:domain`; `TransportKind` and `TransportPermit` are in `:core:security`. Updated §2.1,
   §2.2 and WP-04.0.
5. *Engine order.* Added `ReconnectParticipant` (§12.3). Refetches and flush now start together, matching Flutter
   (N4-D20), so a hung read cannot hold the flush. The order test uses fake participants.
6. *Automation policy.* Added `AutomationGate` (§12.9) and N4-D21. These gate every automatic reconnect, the profile
   monitor and the quota reader. They fail closed, and Try again always dials. Row B-PROTO-146 added.
7. *Client ids.* Added `Oc2ClientIds` with Flutter's exact algorithm and Dart vectors (§7.13, B-PROTO-140).
   `queue:<id>` is never sent on the wire.
8. *OkHttp resend.* Verified in the 5.5.1 source: 503 with `Retry-After: 0` and 421 bypass the retry flag. Added a
   one-shot body interceptor and a `Retry-After` strip, and pinned HTTP/1.1 (N4-D19). B-PROTO-128 and P-N5 are
   extended.
9. *Buffers.* `SseParser.feed` returns frames. The frame channel holds 16 frames under a 32 MiB budget. RawWebSocket
   uses a bounded `channelsConfig` from one `@OptIn(InternalAPI)` file. Added peak-heap tests.
10. *SharedFlow replay 0.* Replaced by per-view bounded queues with subscribe-then-`start()`. Overflow now gives
    `ResyncNeeded` instead of a silent drop (§6.5, B-PROTO-147).
11. *Transport seam.* `Transport { engine, ws, classify, evictIdle, close }` is created by each protocol through the
    injected `TransportFactory`. `:engine:connection` drives it through `ServerGateway.close()` and the new
    `LinkControl`, because `:core:domain` cannot name a `:core:net` type inside `GatewaySpec`. The close order now
    includes `engine.close()`; verified that `HttpClient(engine)` does not manage the engine. Row B-PROTO-148 added.
12. *Undeclared behaviour changes.* (a) Parity kept: a 401 keeps the OpenCode 2 backoff and shows the banner.
    (b) Loopback graphs are skipped on a network change. (c) Only Public and Tailnet hosts are gated. (d) The PTY cap and
    the lack of compression are declared as N4-D18 (P-N4 measures the size), with owner question N4-O6.
13. *Unowned rows and proofs.* Every row and proof now has a WP; WP-04.3 uses `:core:net-okhttp:test`; B-PROTO-040 is
    co-owned.
14. *Tailscale IPv6 and handoff fetch.* Kept IPv4-only for parity. 11 must be corrected and must drop the handoff
    fetch (§4.1, §10, §22).
15. *Real-engine tests and traps.* The path table also runs through OkHttp, `TrustOrigin` maps ws→http, and the
    recorder starts `<scratch>/bin/opencode2` by absolute path.
16. *Questions (blocker).* Added `QuestionGateway` methods, `QuestionAsked` and `QuestionSettled`, and the Paseo
    `kind=question|plan` mapping. Questions are in the reconnect refetch.
17. *Facets.* Replaced the comment with a full table of all 79 abstract `*Gateway` types. Partly accepted: five of the
    named facets (`SessionRetryGateway`, `SessionCommandHandoffGateway`, `McpConfigurationGateway`,
    `ToolConfigurationGateway`, `SetupTransactionalConfigGateway`) have **no production implementer** in Flutter. They
    are marked "inert" or "later", not "implemented by a switch backend".
18. *WebSocket reconnect.* Added §5.5 `WsReconnectPolicy` with Flutter's delays, terminal kinds, recovery read,
    3 s grace, epoch-scoped reset and Codex re-resume. Non-loopback sockets are network-gated.
19. *Transient profiles.* Added `openTransient`, `ProfileSource` and `TransientProfile`. Fixed the helper secret
    (`oc.agentHostSecret`), its 15 s timeout, start-before-dial and the health probe. BYO credentials and new keys added.
20. *Read receipts.* The view receipt is now consent-gated, never queued and dropped on wake (§7.10). Rows B-SEC-142,
    B-SEC-163 and B-STATE-116 added.
21. *Location scoping.* `/view` and `/background` carry the query and `POST /session` carries the body; there is a
    route-table test.
22. *Adapter rows.* Idle now maps to `RunEnded(Idle)`, which never overwrites a failure. Added `SessionCompacted`, the
    content-updated mapping and the inbox-delivered re-read.
23. *Ad-hoc clients.* Added the Konsist ban, the fixed-origin and loopback-port policy rows and their
    `TransportPolicy` entry points (B-PROTO-145).
24. *B-SEC-120.* Added a catalog correction and the E2E-04 fix. The Flutter test wins over row text, plus an explicit
    vector.
25. *Auto-approve source.* Added the `ServerRunsTools` hook with parent-chain and `"*"` resolution, a child-session
    test, and `oc.autoApprove.*` in WP-04.14.
26. *BackgroundIdle order.* The Undo-window flush now comes first, then side graphs, then streams. Integration test
    added.
27. *`onWifi`.* Added to `NetworkState`. The monitor uses it and loopback is exempt. B-AGENT-203 assigned.
28. *Paseo capabilities.* Now recomputed on every hello and disconnect; only the base is snapshotted, plus a flip test.
29. *Row homes.* Moved B-AGENT-226, 203 and 244. Assigned B-PROTO-139 and the capability-ledger rows. Added a §20
    script check that every row is in exactly one WP.
30. *NSC and CT.* §4.7 allows exactly 16's `certificateTransparency` child.
31. *Network change.* Connected non-loopback streams are cancelled and redialed at attempt 0, so P-N6's target is
    reachable.
32. *Loopback normalisation.* Loopback is the exact three strings; vectors for `localhost.` and `::ffff:127.0.0.1`.
33. *Failure naming.* Now consistent under 03 names. 403 maps to `SignIn(Forbidden)` and only sets words.
34. *Timeouts.* No default total; 2 min inactivity; totals only per call.
35. *N4-D13 wording.* Corrected in N4-D13, §7.13, N4-O5 and the risks table: Flutter receipts do confirm after
    delivery through the message lookup.
36. *Refresh hints.* Flutter's forwarded set is kept (N4-D22, B-PROTO-141).
37. *Demo graph.* Added §12.8.
38. *WP-04.14 base APK.* Now named by signer and checked with apksigner; the lineage is left to the release section.
39. *2.0.29 400.* Requests are "unreadable", never "none", plus words for an outdated phone server (B-PROTO-142).
40. *Codex/Paseo location.* Now comes from `profile.codexDirectory` (§12.2 step 4).
41. *Queue units.* UTF-16 length, as Dart, with a non-ASCII vector.
42. *E2E-04 and E2E-29.* Added to WP-04.9, WP-04.13 and §20.

**Partly accepted and why:** issue 17 (five named facets have no implementer, see above). Issue 12(a) and issue 33
asked for different 401 behaviour. The resolution follows Flutter: the OpenCode 2 stream keeps backing off on a 401,
Paseo and Codex `authentication` is terminal, and only 401 and those kinds set `passwordRejected`.

## Codex Sol audit follow-up (2026-11-01, second pass)

The Sol audit of 04+05 (`../audit/codex-04-protocols-networking+05-data-migration.md`) was re-checked against the text
above after the revision. For this file:

| Finding | State |
|---|---|
| 1 BLOCKER, OkHttp resends a mutation on `503 Retry-After: 0` / 421 | already fixed (§3.2 `OneShotBodyInterceptor` + `RetryAfterStripInterceptor` + HTTP/1.1; §3.5; B-PROTO-128 and P-N5 assert exactly one server-observed request) |
| 3 BLOCKER (04 part), `StreamStatus` in `:core:net`; `OkHttpWebSockets` below `WsSpec` | `StreamStatus` already fixed (moved to `:core:model`, §2.1). The WebSocket boundary is fixed now: §3.3 split table, `JvmWsUpgrade` in `:core:net-okhttp` with plain types, the connector and `OkHttpTransport` in `:core:net` androidMain/desktopMain |
| 10 MAJOR, SSE buffer counted in frames, not bytes | already fixed (§6.1 parser returns frames; §6.6 16-frame channel under a 32 MiB `ByteBudget`; `SseBackpressureTest`) |
| 13 MAJOR, downloader drops HTTPS redirects and range checks | already fixed (§3.10: 5 manual HTTPS hops with a permit each, `Range` only on the final URL, exact `Content-Range`, SHA-256; B-PROTO-144) |
| 15 MINOR, WebSocket handshake timeout not applied | already fixed (§3.2 `webSocketClient(…, handshakeTimeoutMs)` → `callTimeout`; §5.2 step 1; the 8 s silent-server case in B-PROTO-131) |

Hard rule restated for this file: **a prompt is never sent twice.** No layer below `:engine:delivery` may resend a
mutation; a lost response is `Refused(DispatchUncertain)` and the receipt lookup (§13.6) decides.
