# Networking and persistence for the native (KMP) rewrite

Research for the OpenCode Mobile native rewrite, topic **network-data**. Written 2026-11-01.

Tags: **[V]** means verified today, either by reading the library source (pub cache or a Maven sources jar) or by fetching the official page or Maven metadata. **[U]** means unverified: it is an inference, it comes from a third party, or it needs a spike.

Inputs:
- Flutter app, read-only: `/home/eslam/Storage/Code/wt/oc_app-batch1`.
- Demo: `/home/eslam/Storage/Code/kmp-demo`.
- Area maps: `../map/protocols.md` and `../map/state-data.md`. This file adds library-level facts and corrects or extends those maps where needed.

---

## 0. Bottom line (recommendations first)

1. **HTTP client.** Use Ktor 3.6.1 in `commonMain`. Use the OkHttp 5.5.1 engine on Android and on Linux desktop (both run on the JVM), and the Darwin engine on iOS.
   - Don't install ContentNegotiation. Decode bodies yourself with one shared `Json` instance, so tolerance and redaction stay under our control.
   - Install WebSockets for the PTY, Codex app-server and Paseo connections.
   - This is the stack the demo already proved.
2. **Harden OkHttp. The defaults differ from the Flutter app's Dart `HttpClient` in ways that break our invariants:**
   - `retryOnConnectionFailure(false)`. OkHttp silently resends a non-one-shot POST body after a stale pooled connection fails, which can send a prompt twice [V, from source].
   - `followRedirects = false` in the Ktor config. Ktor's `HttpRedirect` plugin is on by default. The OkHttp engine already turns off OkHttp's own redirects [V].
   - `proxy(Proxy.NO_PROXY)`.
   - One client per server profile, with a TrustManager that pins the leaf certificate's DER SHA-256.
   - A cleartext consent interceptor, because the network security config keeps `cleartextTrafficPermitted="true"`: Android cannot express `100.64.0.0/10`.
3. **SSE: don't use Ktor's built-in reconnection for OpenCode 2. Port the Dart parser and loop instead.** Ktor's reconnection:
   - waits a fixed delay (honouring the server's `retry:`), with no exponential backoff;
   - replays the *initial* request, so the durable log's `?after=` cursor can never move forward;
   - gives no "reconnected" status;
   - has no frame-size cap;
   - and its Last-Event-ID support does nothing for OC2, whose frames carry no `id:` [V, from source].

   So: `prepareGet().execute { bodyAsChannel() }`, a ~120-line `SseParser` (16 MiB frame cap), and an `EventStream` with the existing timings:
   - first retry after 1 s, doubling to a 16 s cap;
   - the backoff resets after 30 s of a stable connection;
   - the UI shows "disconnected" after 6 failed tries but retrying continues;
   - every successful connect and reconnect triggers reconcile-by-refetch.
4. **The 91-event union.**
   - Envelope DTO with `data: JsonElement`, then an explicit `type → KSerializer` registry.
   - A missing type, or a payload that fails to decode, becomes `UnknownEvent(type, raw)` and never throws. This matches the Dart contract.
   - Tolerant list and number serializers, and open enums as `String` value classes.
   - Use kotlinx.serialization 1.12.0 with **`exceptionsWithDebugInfo = false`**. It defaults to `true`, which puts the JSON input (possibly provider API keys) into exception messages [V].
5. **No OpenAPI codegen for OpenCode 2.**
   - The spec types `/api/event` as an opaque JSON string.
   - It has 390 `anyOf` and zero discriminators.
   - OpenAPI Generator's Kotlin `multiplatform` library lists oneOf, anyOf, allOf and polymorphism as unsupported.

   Write the client by hand, as the Dart client is. Use the spec only for **drift tests**: a route table checked against a committed `/openapi.json` for each pinned server version.
6. **Persistence: three stores, no fourth.**
   - **`KeyValueStore`.** On Android, on day 1, it is the *existing* `FlutterSharedPreferences` file with the `flutter.` prefix and the same encodings, adopted in place. That means no copy, and every reused Kotlin reader keeps working.
   - **Room 3 (`androidx.room3` 3.0.3, KMP-stable).**
     - It holds collections and large blobs: the offline queue (up to 60 MB in XML today), drafts and stash indexes, command receipts, and session and transcript caches for first paint.
     - Use `BundledSQLiteDriver` everywhere, so JVM tests on the VPS run the same SQLite as the phone (+1.28 MB arm64).
     - SQLDelight 2.4.1 is the fallback if the Room/KSP spike fails.
   - **Files** stay exactly where they are.

   DataStore 1.3.0 is optional, as the iOS/JVM backing of `KeyValueStore` only.
7. **Secrets.**
   - Use our own `SecretStore`. On Android it is an AES-256-GCM key held directly in the Keystore (non-exportable), with AAD = the entry name, stored in a separate prefs file.
   - Don't use `androidx.security:security-crypto`, which is deprecated [V].
   - Don't put a third-party wrapper on Android: we must own the format of the most sensitive data.
   - On iOS, Keychain generic passwords (`AfterFirstUnlockThisDeviceOnly`). On Linux, Secret Service later.
   - The day-1 migration decrypts flutter_secure_storage v11 natively (exact recipe in section 8.3), re-encrypts, reads back to verify, and keeps the old files.
8. **Before touching anything on first launch, copy these files byte for byte** to `files/migration/flutter-v57/`:
   - `shared_prefs/FlutterSharedPreferences.xml`
   - `FlutterSecureStorage.xml`
   - `FlutterSecureKeyStorage.xml`
   - `FlutterSecureStorageConfiguration*.xml`

   It costs nothing and makes every later step reversible. The secure copies stay protected by the same Keystore key.

---

## 1. Versions snapshot (2026-11-01)

| Library | Version | Date | Source |
|---|---|---|---|
| Kotlin | 2.4.21 (latest stable). 2.5.0-Beta2 exists. | - | Maven Central metadata [V] |
| Ktor (core, okhttp, darwin) | **3.6.1** | 2026-10-29 | GitHub releases + Maven Central [V] |
| OkHttp | **5.5.1** (Ktor 3.6.1's okhttp engine `requires` 5.5.1) | Maven lastUpdated 2026-10-22 | Maven Central + Ktor `.module` [V]. The changelog page returned 404, so per-release notes are [U]. |
| kotlinx.serialization | **1.12.0**, built with Kotlin 2.4.10 | 2026-10-19 | GitHub release [V] |
| kotlinx-coroutines | 1.11.0 | Maven lastUpdated 2026-05-08 | Maven Central [V] |
| kotlinx-io / Okio | 0.9.2 / 3.18.2 | 2026-10-30 / 2026-09-04 | Maven Central [V] |
| Room 3 (`androidx.room3`) | **3.0.3 stable**, 3.1.0-alpha03 | 2026-09-09 / 2026-10-21 | Room 3 release notes [V] |
| Room 2 (`androidx.room`) | 2.8.5 (maintenance mode) | 2026-09-09 | Google Maven [V] |
| androidx.sqlite `sqlite-bundled` | 2.7.1 stable, 2.8.0-alpha03 | - | Google Maven [V]. The aar ships `libsqliteJni.so`: 1.28 MB arm64, 1.25 MB armv7 [V] |
| SQLDelight | **2.4.1** | 2026-10-06 | CHANGELOG [V] |
| DataStore (incl. `datastore-tink`) | **1.3.0** | 2026-10-21 | DataStore release notes [V] |
| KSP | 2.3.13 | 2026-10-30 | GitHub releases + Maven [V]. 2.3.10 fixed a Kotlin 2.4.0 issue and AGP 9 built-in Kotlin. |
| androidx.security `security-crypto` | 1.1.0. **All APIs deprecated** since 1.1.0-alpha07 (2025-04-09). | 2025-07-30 | release notes [V] |
| Tink Android | 1.24.0 | Maven lastUpdated 2026-10-16 | Maven Central [V] |
| KSafe (`eu.anifantakis:ksafe`) | 3.3.0 | Maven lastUpdated 2026-10-03 | Maven + README [V] |
| multiplatform-settings | 1.3.0 (last publish 2024-11-29) | - | Maven Central [V]. Slow cadence. |
| OpenAPI Generator | 7.26.0 | 2026-10-06 | Maven Central [V] |

The demo pinned Kotlin 2.4.0, Ktor 3.6.1, serialization 1.12.0 and coroutines 1.11.0. The sibling file `kmp-cmp-status.md` recommends Kotlin 2.4.21, which works with all of the above.

---

## 2. HTTP client: Ktor 3 + OkHttp (Android, Linux JVM) + Darwin (iOS)

### 2.1 Facts [V]

- **Engines.** OkHttp covers JVM and Android, with HTTP/2 and WebSockets. Darwin (NSURLSession) covers iOS and macOS, also with HTTP/2 and WebSockets. CIO is HTTP/1.x only. Since the Linux desktop app is Compose Desktop on the JVM, OkHttp serves both Android and Linux. (Ktor engines page, Ktor 3.6.1.)
- **Recent OkHttp-engine changes:**
  - 3.4.0 added duplex streaming.
  - 3.5.0 added DNS configuration.
  - 3.6.0 reduced allocations when reading response bodies, and fixed closing a body on the Android main thread.
- **Recent Darwin-engine changes:** 3.3.3 fixed an SSE hang after cancellations, 3.4.0 deprecated DarwinLegacy, and 3.4.3 fixed a crash when `close()` raced with `execute()`. (Ktor releases page.)
- **Engine defaults** (`OkHttpConfig.kt`, 3.6.1): `followRedirects(false)`, `followSslRedirects(false)` and `retryOnConnectionFailure(true)`.
  - With `preconfigured == null`, each engine client gets a fresh `Dispatcher()`, and timeouts create cached client variants (`clientCacheSize = 10`).
  - The `config {}` block runs after that, so a dispatcher or pool set there wins.
- **Calls go through `call.enqueue`** (`OkUtils.kt`), so OkHttp's `Dispatcher` limits apply. The defaults are `maxRequests = 64` and `maxRequestsPerHost = 5`.
  - OkHttp frees the slot after `onResponse` returns, and Ktor's callback only resumes a continuation.
  - So a long-lived SSE or download body does **not** hold a dispatcher slot (`RealCall.AsyncCall.run` → `dispatcher.finished`) [V].
- **`retryOnConnectionFailure` KDoc** (OkHttp 5.5.1): it silently recovers from unreachable IPs, **stale pooled connections** and unreachable proxies. The KDoc says: "Set this to false to avoid retrying requests when doing so is destructive."
  - In `RetryAndFollowUpInterceptor`, a request whose sending had started is retried unless its body `isOneShot()`.
  - Ktor's `ByteArrayContent` → `RequestBody` is not one-shot. **A POST can therefore be sent twice** [V].

### 2.2 Recommended client setup for this app

```kotlin
// androidMain/jvmMain actual: one per server profile, shared pool across profiles
val sharedPool = ConnectionPool(maxIdleConnections = 5, keepAliveDuration = 15, TimeUnit.SECONDS) // Dart used 15 s idle
fun profileClient(trust: ProfileTrust, json: Json) = HttpClient(OkHttp) {
    expectSuccess = false              // map status codes ourselves into plain-words failures
    followRedirects = false            // Ktor HttpRedirect is ON by default; consent/credentials must not cross origins
    install(HttpTimeout) { connectTimeoutMillis = 8_000; requestTimeoutMillis = 30_000; socketTimeoutMillis = 30_000 }
    install(WebSockets)                // PTY, Codex app-server, Paseo (Basic header on the upgrade)
    engine {
        config {
            connectionPool(sharedPool)
            dispatcher(Dispatcher().apply { maxRequestsPerHost = 10 }) // REST bursts during reconcile
            proxy(Proxy.NO_PROXY)                                      // Dart: "no proxy"
            retryOnConnectionFailure(false)                            // prompts are never double-sent
            trust.pinnedLeafSha256?.let { pin ->
                val tm = PinnedLeafTrustManager(pin)                    // accept iff sha256(leaf.encoded) == pin
                sslSocketFactory(sslContextWith(tm).socketFactory, tm)
                hostnameVerifier { _, session -> tm.leafMatches(session) }
            }
        }
        addInterceptor(CleartextConsentInterceptor(trust))             // http:// non-loopback needs origin-bound consent
    }
}
```

Notes for implementers:

- **Retries.** The Flutter transport retried only specific, known-safe cases: agent GET 404 or 503 `service_starting` during server boot, twice at 500 ms. Recreate those as explicit code, scoped to GET. Don't use Ktor's `HttpRequestRetry` broadly.
- **Pinning semantics.** Pin the leaf DER fingerprint, not the SPKI.
  - OkHttp's `CertificatePinner` pins SPKI hashes, and only *after* normal chain validation. So it cannot express "trust this exact self-signed leaf". Use a custom `X509TrustManager`.
  - Port `api2_transport_trust_test.dart` as the contract test.
  - On iOS this becomes `handleChallenge` in the Darwin engine, comparing `SecCertificateCopyData(leaf)` [U, design].
- **Cleartext.** Keep the manifest's `network_security_config` with `<base-config cleartextTrafficPermitted="true"/>`. It is the same file and comment as today. The interceptor is then the security boundary.
  - Tailnet addresses (`100.64.0.0/10`, `*.ts.net`) are checked in code, as the demo does.
- **Network changes.** Register `ConnectivityManager.registerDefaultNetworkCallback`. On `onAvailable`/`onLost`:
  - call `sharedPool.evictAll()`, because sockets bound to the old network go stale (Wi-Fi ↔ mobile, Tailscale up/down);
  - cancel the current SSE job and reconnect at once, with the backoff reset.
- **No read timeout on SSE.** The Dart transport sets `receiveTimeout: null` on SSE (`transport.dart:312`), so a half-open socket can hang until the network callback fires.
  - Ktor's `HttpTimeout` already skips the *request* timeout for SSE content (`HttpTimeout.kt`: "Request timeout shouldn't be applied to WebSocket or SSE connection") [V].
  - Set `socketTimeoutMillis` to about 3× the server heartbeat interval. **The OC2 heartbeat interval is unknown** (section 11).

---

## 3. SSE: Ktor's plugin vs our own loop

### 3.1 What Ktor 3.6.1's SSE client actually does [V, read in the `ktor-client-core-jvm-3.6.1-sources.jar`]

- `SSEConfig`: `reconnectionTime = 3000 ms`, `maxReconnectionAttempts = 0` (no reconnection by default), plus `showCommentEvents`, `showRetryEvents` and `bufferPolicy`.
- `DefaultClientSSESession.doReconnection()`:
  - waits `reconnectionTimeMillis` (a fixed delay, replaced by any `retry:` field);
  - re-executes `HttpRequestBuilder().takeFrom(initialRequest)` and appends `Last-Event-ID` if one was seen;
  - stops on status 204.
  - So: **no exponential backoff, no jitter, no status callback, and the query can't change between attempts.**
- `checkResponse` throws `SSEClientException` for a status other than 200 or a Content-Type other than `text/event-stream`.
- The parser reads lines with `readLine(LineEnding.Lenient)` into a `StringBuilder` with **no size cap**. The Dart client caps one frame at 16 MiB, which is what the protocol notes recommend.
- The docs (Ktor 3.6.1 SSE page) say reconnection needs `maxReconnectionAttempts > 0`, and that typed events come from a `deserialize` lambda.

### 3.2 Why OpenCode 2 needs its own loop

- **No resume IDs.** `/api/event` frames are bare `data: <json>` with `: heartbeat` comments and no `id:` (`docs/opencode2-protocol-notes.md` §3), so Last-Event-ID resume can't help.
- **The stream is volatile.** The client must know *when* it reconnected so it can refetch sessions, messages and pending permissions/forms. `server.connected` arrives first on every connection.
- **The durable log moves its cursor.** `/api/experimental/session/{id}/log?after=N&follow=true` resumes by query parameter, which changes on every attempt (`Api2SessionLogStream`).
- **Visible retry timing.** The UI counts down to the next try (`nextAttempt`) and shows "disconnected" after 6 failures while still retrying. Ktor gives neither.

### 3.3 Shape to port (from `lib/api2/sse2.dart`)

```kotlin
sealed interface StreamSignal {
    data class Status(val state: State, val nextAttemptAt: Instant?) : StreamSignal // Connecting/Connected/Reconnecting/Disconnected
    data class Event(val envelope: Envelope) : StreamSignal                           // Connected ⇒ caller reconciles by refetch
}
fun eventStream(client: HttpClient, request: () -> HttpRequestBuilder /* rebuilt each try: auth, after= */): Flow<StreamSignal> =
  channelFlow {
    var attempt = 0
    while (currentCoroutineContext().isActive) {
        send(Status(if (attempt == 0) Connecting else Reconnecting, null))
        try {
            client.prepareRequest(request().apply { timeout { requestTimeoutMillis = INFINITE_TIMEOUT_MS } })
                .execute { resp ->
                    if (resp.status != HttpStatusCode.OK) throw StreamRefused(resp.status.value) // plain words later
                    val parser = SseParser(maxFrameBytes = 16 shl 20) { frame -> trySendBlocking(Event(decodeEnvelope(frame.data))) }
                    val ch = resp.bodyAsChannel(); val buf = ByteArray(16 * 1024)
                    // after 30 s of a healthy stream, reset attempt = 0 (Dart _backoffResetAfter)
                    while (true) { val n = ch.readAvailable(buf, 0, buf.size); if (n < 0) break; parser.feed(buf, 0, n) }
                    parser.flush()
                }
        } catch (e: CancellationException) { throw e } catch (e: Throwable) { /* classify; never surface raw text */ }
        attempt += 1
        val ms = (500L shl attempt.coerceIn(0, 5)).coerceIn(500, 16_000)  // identical to Dart: 1 s, 2 s, 4 s, 8 s, 16 s, 16 s...
        send(Status(if (attempt >= 6) Disconnected else Reconnecting, Clock.System.now() + ms.milliseconds))
        delay(ms)
    }
  }
```

The `SseParser` port is byte-level:

- It handles CRLF and LF line endings.
- It ignores `:` comments, joins multi-line `data:` with `\n`, and keeps `event:` and `id:`.
- A frame over the cap is dropped *without* killing the stream: skip to the next blank line.
- `flush()` emits a trailing frame that has no terminating blank line.

The test vectors come from `api2_sse_test.dart` (9 rows in the catalog). The exact `readAvailable` and `timeout {}` names are those of Ktor 3.6; check them at compile time.

**Where Ktor's SSE plugin is fine:** short-lived or third-party SSE where none of the above matters (perhaps A2A streaming). Even there, cap frame size with our parser.

**Streaming smoothness (feeds the UI topic):**
- Decode on `Dispatchers.Default`.
- Append `session.text.delta` into a per-part `StringBuilder`.
- Publish through a `StateFlow` sampled at most once per frame, so 50–100 deltas a second don't each cause a recomposition.
- The Dart notes already list "throttle stream deltas into the UI" and "decode big JSON off the main thread" as performance rules.

---

## 4. kotlinx.serialization for the 91-variant event union

### 4.1 Wire shape (from `docs/opencode2-protocol-notes.md` §3.1 and `lib/api2/events.dart`)

The envelope is `{id, created, type, location?, metadata?, durable?, data}`. The **discriminator `type` sits on the envelope, and the payload is in `data`**: the type and the payload are siblings. kotlinx's class-discriminator polymorphism needs the discriminator inside the same object as the fields, so it doesn't fit as-is.

The Dart contract is "parsing must never throw":
- an unknown or malformed event becomes `UnknownApi2Event(type, raw)`;
- a missing `data` falls back to the whole envelope;
- malformed list items are skipped one by one.

### 4.2 Facts about kotlinx.serialization 1.12.0 [V, read in the 1.12.0 sources jar and release notes]

- **Sealed fallback works.** `SealedClassSerializer.findPolymorphicSerializerOrNull` falls back to `serializersModule.getPolymorphic(baseClass, name)`, so a `polymorphicDefaultDeserializer` registered in the module also works for **sealed** hierarchies.
- **`subclassesOfSealed`** (experimental, since 1.10) registers a sealed hierarchy's subclasses in an open polymorphic scope.
- **Every deserialization error is wrapped in `JsonException`** in 1.12.0. `JsonDecodingException` carries the JSON path and the **input**.
- **`exceptionsWithDebugInfo` defaults to `true`.** Set it to `false` in production, or provider keys from `/config/providers`-style bodies can end up in exception text and crash records.
- **`maxNestingDepth` defaults to 400** (new in 1.12). Deeper input throws instead of overflowing the stack. That is fine for generative UI trees; note it in the gen-UI renderer limits.
- **Faster parsing.** `JsonElement` decoding got faster (#3220), and the ASCII parser speeds up decoding from Okio and kotlinx-io sources.
- **Per-class unknown keys.** `@JsonIgnoreUnknownKeys` (since 1.8) allows unknown keys per class. A global `ignoreUnknownKeys = true` is simpler for us.

### 4.3 Recommended design

```kotlin
val WireJson = Json {
    ignoreUnknownKeys = true      // server adds fields weekly
    explicitNulls = false
    coerceInputValues = true      // null/unknown enum → property default
    isLenient = false
    exceptionsWithDebugInfo = false  // never echo input (credentials) in exceptions
}

@Serializable data class WireEnvelope(
    val id: String? = null, val created: Long? = null, val type: String = "",
    val location: Location? = null, val metadata: JsonObject? = null,
    val durable: Durable? = null, val data: JsonElement? = null,
)

sealed interface Api2Event { @Serializable @SerialName("session.text.delta")
    data class TextDelta(val sessionID: String, val assistantMessageID: String, val ordinal: Int = 0, val delta: String = "") : Api2Event
    /* … 90 more, one per row of protocol-notes §3.2 … */
    data class Unknown(val type: String, val raw: JsonObject) : Api2Event }

// Explicit registry: readable by every agent, and a test pins it to the 91-type list.
val EventRegistry: Map<String, KSerializer<out Api2Event>> = mapOf(
    "session.text.delta" to Api2Event.TextDelta.serializer(), /* … */)

fun decodeEnvelope(text: String): Envelope {
    val tree = runCatching { WireJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
        ?: return Envelope.malformed()                      // counted, never thrown
    val wire = runCatching { WireJson.decodeFromJsonElement(WireEnvelope.serializer(), tree) }.getOrNull()
        ?: return Envelope.malformed()
    val payload = (wire.data as? JsonObject) ?: tree       // Dart: data ?? whole envelope
    val event = EventRegistry[wire.type]?.let { s -> runCatching { WireJson.decodeFromJsonElement(s, payload) }.getOrNull() }
        ?: Api2Event.Unknown(wire.type, payload)
    return Envelope(wire, event)
}
```

Helpers to write once in `:protocol-core`:

- **`TolerantListSerializer<T>(element)`.** It decodes a `JsonArray` item by item and drops failures, like Dart's `_mapList`. kotlinx's default list serializer fails the whole list when one element is bad.
- **`LenientDoubleSerializer`.** The OC2 schema encodes non-finite numbers as the strings `"Infinity"`, `"-Infinity"` and `"NaN"`. There are 30 `anyOf[enum, number]` sites, for example `Session.Message.Shell.exit` and the `Form.NumberField` bounds [V, counted in `contracts/opencode2-openapi-beta-18600.json`].
- **Open enums as `@JvmInline value class Phase(val raw: String)`** with known constants, for server-evolving vocabularies such as phases, finish reasons, interrupt reasons and delivery. Use Kotlin `enum` only for closed sets.
- **Free-form fields stay `JsonElement`/`JsonObject`:** `metadata`, tool `input`, gen-UI payloads and provider state.
- **IDs and timestamps:** IDs are `String`; `created` and `idle` are `Long` epoch milliseconds; `seq` is `Long`.

Why not `JsonContentPolymorphicSerializer` or sealed polymorphism directly? Both work, but both expect the type *inside* the payload object. Because of the envelope shape we would have to splice `type` into `data` first. The registry is simpler, explicit and testable:
- `EventRegistry.keys` must equal the documented 91 types;
- every `@SerialName` must appear in the registry.

Performance: `parseToJsonElement` followed by `decodeFromJsonElement` means one tokenizer pass plus a tree walk. That is fine for deltas of a few hundred bytes. If profiling shows otherwise, add a fast path for `session.text.delta` and `session.reasoning.delta` only.

---

## 5. OpenAPI → Kotlin codegen for multiplatform

### 5.1 Facts

- **OC2 spec shape** [V, counted]: `contracts/opencode2-openapi-beta-18600.json` is OpenAPI 3.1.0 with 114 paths and 222 schemas.
  - It has **390 `anyOf`, 0 `oneOf`, 0 `discriminator`**.
  - About 250 of the `anyOf`s are just nullability (`[null, string]` ×168, `[null, object]` ×57, `[null, ref]` ×17, …).
  - About 70 are real unions without a discriminator (`[ref, ref]` ×49, `[ref, ref, ref]` ×14, `[object, object, object]` ×5, …).
  - `GET /api/event` returns `V2EventEncoded = {"type":"string","contentMediaType":"application/json"}`, so **the 91-event union is not in the spec at all**. `protocols.md` §0.2 says the same.
- **Gas City supervisor spec** (used by AI Team) [V]: OpenAPI 3.1.0, 127 paths and 510 schemas, with 249 type arrays (`type: [x, null]`), 14 `oneOf` and 7 `discriminator`.
- **OpenAPI Generator, Kotlin generator** (page updated 2026-09-24) [V]:
  - The `multiplatform` library documents Ktor 1.6.7 and kotlinx.serialization 1.2.1, both stale.
  - **oneOf, anyOf, allOf, Polymorphism and Union are marked unsupported.**
  - `generateOneOfAnyOfWrappers` exists for `jvm-retrofit2` only.
  - OpenAPI 3.1 isn't mentioned.
  - The latest generator is 7.26.0 (2026-10-06).
- **Fabrikt** (GitHub README) [V for the README claims]:
  - supports OpenAPI 3.1 type arrays;
  - generates a `sealed interface` for every `oneOf` with a discriminator, with open-enum support;
  - can produce a Ktor client with kotlinx.serialization models;
  - has a Gradle plugin.
  - Kotlin Multiplatform (`commonMain`) compatibility **isn't stated** [U]. A Gradle plugin listing shows 1.35.0 (2026-05-12) [U].
- **Kotlin SDKGen** (`nabobery/kotlin-sdkgen`, klibs.io) claims OpenAPI 3.1 to a KMP Ktor client with oneOf and anyOf support. It is at 0.4.0, and its maturity is unknown [U].

### 5.2 Recommendation

- **OC2: no codegen.**
  - Hand-write tolerant DTOs, ported one to one from `lib/api2/models*.dart` and its tests.
  - Keep a **route table** (`object Routes { val SessionList = Route(GET, "/api/session") … }`), with the beta→stable dialect as table rows (`dialect.dart`).
  - Add a JVM test that loads `contracts/opencode2-openapi-<pinned>.json`. It asserts every route used exists with the same method, and that every DTO property we read is a property of the referenced schema.
  - When the pin moves (2.1.1 now; 2.2.5 is out and unprobed, per `protocols.md` §0.3), refresh the snapshot from the live `/openapi.json`. The test then shows the drift.
- **Gas City: optional spike.** If the AI Team client is large enough to justify it, try Fabrikt with the Ktor client and kotlinx.serialization in a JVM module first, then check whether the output compiles in `commonMain` (no `java.*` types). Otherwise port the hand-written Dart client.

---

## 6. Persistence options compared (for this app)

### 6.1 Facts

- **Room 3** [V, Room 3 release notes and the KMP guide]:
  - New package `androidx.room3` with `room3-*` artifacts. 3.0.0 shipped 2026-07-01; 3.0.3 is stable.
  - It is KSP-only and generates only Kotlin. It uses only `androidx.sqlite` drivers (no SupportSQLite), with suspend DAOs (or Flow) outside Android.
  - `@TypeConverter` becomes `@ColumnTypeConverter`. `InvalidationTracker.createFlow` replaces observers. Executors are replaced by a `CoroutineContext`.
  - Targets: Android, iOS and JVM, plus JS and Wasm in 3.0. The guide doesn't mention Linux native, which doesn't matter: our desktop is the JVM.
  - The KMP guide recommends `BundledSQLiteDriver` in common code; platform drivers are optional.
  - Not available off Android: query callbacks, auto-close, prepackaged databases and multi-instance invalidation. We need none of these.
  - Room 2.x is in maintenance mode.
- **SQLDelight 2.4.1** [V, CHANGELOG]:
  - SQL-first: you write `.sq` files and get typesafe Kotlin, with no KSP.
  - 2.4.0 needs Gradle 8.2.1+ and supports isolated projects and SQLite 3.44 features. 2.3.2 has the full AGP 9 DSL, Android minSdk 23 and a `TransactionDispatcher`.
  - It has a mature KMP history (2.0 in 2023).
- **DataStore 1.3.0** [V, release notes]:
  - New `DataStore.Builder` (needs an application-scoped `CoroutineContext` with a `Job`), plus web storage and tracing.
  - New **`datastore-tink`** (`AeadSerializer`) for Android and JVM.
  - KMP Preferences have worked since 1.1.
  - Multi-process support since 1.1.0.
  - `SharedPreferencesMigration` exists, but it copies values *raw* (our list and double prefixes would come through as strings) and deletes the keys it migrates from the source [U for the 1.3 default; 1.0-era behaviour].
  - Its native helper `libdatastore_shared_counter.so` in `datastore-core-android` 1.2.0 and 1.3.0 has 16 KB-aligned LOAD segments on arm64 (`readelf`, 0x4000) [V]. Yet the Flutter plugin reverted DataStore 1.2.0 "due to a regression 16 KB page size support" (`shared_preferences_android` 2.4.21 changelog) [V that the note exists; the cause is U]. If adopted, check the final APK with `zipalign -c -P 16`.
- **SharedPreferences.**
  - The whole XML loads into memory on first access, and every `commit` rewrites the whole file.
  - That is fine for small values, and bad for today's offline-queue blob of up to 60 MB (`state-data.md` §2.1).
- **Is the app multi-process?** The manifest has only `:local_pdf` as a separate process [V]. Widgets, tiles and workers run in the main process, so the cross-engine `reload()` dance of the Flutter app largely disappears.

### 6.2 Recommendation for this app

| Data | Store | Why |
|---|---|---|
| Small settings, consent markers, per-profile small values (most of the ~210 `oc.*` shapes) | `KeyValueStore`. **Android day 1: the existing `FlutterSharedPreferences` file, adopted in place.** iOS: NSUserDefaults or DataStore. JVM: DataStore file under `$XDG_DATA_HOME/io.github.eslamasabry.opencode_mobile/`. | No copy, nothing to lose. The reused Kotlin readers (widgets, tile, scheduler, recovery, locale) and the key-shape deletion sweep keep working. |
| Offline queue, drafts index, stash, command receipts, pending commands, session list cache, transcript and first-paint caches, usage history | **Room 3.0.x**, one DB `oc.db`. **Every table has a `profile_id` column.** | Moves big blobs out of the XML (cold start), gives atomic multi-row deletion, and Flow invalidation for the UI. |
| Attachments, voice models, diagnostics, Linux rootfs, engine dirs | Files, **same paths and sharding** as today (`state-data.md` §4.5) | Already Kotlin-owned or path-addressed; moving them gains nothing. |
| Secrets | `SecretStore` (section 7) | Never in the DB or in prefs plaintext. |

Room implementation notes:

- **Driver.** Use `BundledSQLiteDriver` on all targets. The JVM unit tests the agents run on the VPS then exercise the same SQLite as the phone. The cost is about +1.28 MB on arm64 with ABI splits. The alternative is `AndroidSQLiteDriver` with the OS SQLite: minSdk 26 means SQLite 3.19 on the oldest devices.
- **Builder** in `androidMain`: `Room.databaseBuilder<OcDatabase>(ctx, ctx.getDatabasePath("oc.db").path).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()`. The KMP guide's prose says `setCoroutineContext`, so check the name at compile time [U].
- **Schema export.** Use `room3 { schemaDirectory("$projectDir/schemas") }` and commit the schemas.
- **Deletion-sweep invariant, as a test.** Parse the exported schema JSON and fail if any table lacks `profile_id` and isn't on an explicit allowlist. Add `ProfileDao.deleteEverything(profileId)` inside one `immediateTransaction`. This is the Room equivalent of the AGENTS.md rule that per-profile keys are named `oc.<what>.<profileId>`.
- **JSON columns.** Store wire blobs (transcript pages, tool results) as `TEXT` JSON decoded with `WireJson`. Don't normalise server shapes that change weekly.
- **Spike first** (half a day, before agents fan out): Kotlin 2.4.21, AGP 9.3.x with `com.android.kotlin.multiplatform.library`, KSP 2.3.13 and `room3` 3.0.3, compiling for `android`, `jvm` and `iosArm64`, with `kspAndroid`/`kspJvm`/`kspIosArm64` configurations (KSP 2.3.10 deprecated plain `ksp(...)` in KMP).
  - If it fails, switch to **SQLDelight 2.4.1**, which has no KSP and is SQL-first. The Kotlin APIs stay behind the same repository interfaces either way.

---

## 7. Encrypted secure storage

### 7.1 Facts

- **`androidx.security:security-crypto`** (EncryptedSharedPreferences, MasterKey): "Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore", since 1.1.0-alpha07 (2025-04-09). 1.1.0 stable came out 2025-07-30 [V].
- **flutter_secure_storage 11** removed its ESP backend after the v10 migration [V, changelog].
- **DataStore 1.3.0** has `datastore-tink`: Tink AEAD around any DataStore serializer, on Android and JVM [V]. Tink Android is 1.24.0 [V].
- **KSafe 3.3.0** (Apache-2.0, one maintainer, 343 stars) [V, README]:
  - KMP encrypted key-value storage with AES-256-GCM, on Android (Keystore and DataStore), iOS (Keychain), JVM (Windows DPAPI, macOS Keychain, Linux Secret Service, with a software fallback), JS and Wasm.
  - It has `delete`/`clearAll` but **no documented list-by-prefix**, and we need one (the reset flow enumerates orphan secrets).
  - Its own README advises hiding it behind an interface you own.
- **multiplatform-settings 1.3.0:** `KeychainSettings` (experimental, Apple only). No Android encryption. Last publish 2024-11 [V].
  - **Trap if it is used over `FlutterSharedPreferences`:** `SharedPreferencesSettings.getInt` calls `getInt`, but Flutter stored every int as a **Long**. That throws `ClassCastException`, so always use `getLong` [V for the Flutter side; library internals U].
- **KVault 1.12.0:** last publish 2023-10. Treat it as abandoned [V for the date].

### 7.2 Recommendation: own a `SecretStore`

```kotlin
interface SecretStore {                       // commonMain
    suspend fun read(name: String): String?    // name keeps today's shapes: pw.<id>, oc.codexToken.<id>, …
    suspend fun write(name: String, value: String)
    suspend fun delete(name: String)
    suspend fun names(prefix: String): Set<String>   // reset flow enumerates orphans (state-data §4.3)
}

// androidMain
class KeystoreSecretStore(ctx: Context) : SecretStore {
    private val prefs = ctx.getSharedPreferences("oc_secrets_v1", Context.MODE_PRIVATE) // excluded from backup like today
    private val alias = "oc.secrets.aesgcm.v1"
    @Synchronized private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())         // no user-auth: background workers must read secrets
        }.generateKey()
    }
    // blob = 0x01 | IV(12, Keystore-generated) | ciphertext+tag(16) ; AAD = entry name (stops ciphertext swapping, which FSS lacks)
}
```

- **Android:** use the Keystore AES key directly, so the key never leaves secure hardware. Don't add StrongBox: it is slow and needs no extra guarantee here. Don't require user authentication: the scheduled-prompt worker and background service read the server password.
- **iOS:** use a Keychain `kSecClassGenericPassword` with `kSecAttrService` set to the bundle id and `kSecAttrAccount` set to the name, accessible `AfterFirstUnlockThisDeviceOnly`. `SecItemCopyMatching` with `kSecMatchLimitAll` supports `names(prefix)`. The cinterop is about 150 lines; KSafe is acceptable there if we prefer.
- **Linux (JVM):** use Secret Service over D-Bus, with a schema of our own (`io.github.eslamasabry.opencode_mobile.secret`, attribute `name`).
  - Options: KSafe JVM (needs `jdk.unsupported` in `nativeDistributions`), dbus-java, or a guarded `secret-tool` shell-out.
  - Keep today's behaviour for a missing keyring: a sentence *before* the user types a password that can't be kept (`SecureStorageUnavailable`). This can come later.
- **Redaction.** Register every secret with the redactor (the `KitRedact.registerKnownSecret` equivalent) on every read and write, before any logging.

---

## 8. EXACT Flutter on-disk formats the native app must read (Android)

The package is `io.github.eslamasabry.opencode_mobile`, so the data dir is `/data/data/io.github.eslamasabry.opencode_mobile/`. The resolved plugin versions come from `pubspec.lock`: `shared_preferences` 2.5.5, `shared_preferences_android` 2.4.27, `flutter_secure_storage` 11.2.0, `path_provider_android` 2.3.1. **Every fact below was read in those exact package sources in `~/.pub-cache`** [V].

### 8.1 `shared_preferences` legacy API (what the app uses: 0 call sites of `SharedPreferencesAsync` or `SharedPreferencesWithCache`)

- **File:** `shared_prefs/FlutterSharedPreferences.xml` (`SHARED_PREFERENCES_NAME = "FlutterSharedPreferences"`, `SharedPreferencesPlugin.kt:33`).
- **Key prefix:** the Dart side adds `"flutter."` to every key (`shared_preferences_legacy.dart:22`, `_prefix = 'flutter.'`). The app never calls `setPrefix`.
- **Type mapping** (`LegacySharedPreferencesPlugin.kt`; the Dart side is `lib/src/shared_preferences_android.dart`):

| Dart type | Stored as | Decode rule |
|---|---|---|
| `bool` | `putBoolean` | `getBoolean` |
| `int` | **`putLong`** | `getLong`. `getInt` throws `ClassCastException`. |
| `double` | `putString("VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu" + value.toString())` | strip the prefix, then `toDouble()` |
| `String` | `putString`. The plugin **refuses** strings that start with any of the three prefixes, so a prefix match is unambiguous. | as is |
| `List<String>` | **since `shared_preferences_android` 2.4.3:** `putString("VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!" + jsonEncode(list))` | strip the `…Qu!` prefix, then decode a JSON string array |
| `List<String>` (older writers) | `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu" + Base64.DEFAULT(ObjectOutputStream(ArrayList<String>))` | Base64-decode, then read with an `ObjectInputStream` that allows only `java.util.ArrayList`, `java.util.Arrays$ArrayList`, `java.lang.String` and `[Ljava.lang.String;` (copy `StringListObjectInputStream`). The plugin does *not* rewrite these on read. |
| BigInteger (pre-2023 API) | `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBCaWdJbnRlZ2Vy" + toString(36)` | `BigInteger(s, 36)`. Never written by this app. |
| `Set<String>` (pre-2020 `setStringSet`) | native string set | to list. Never written by this app. |

- **App history:** the first commit is 2026-08-23, on `shared_preferences ^2.3.3`, so every list it ever wrote should be the JSON form [U that no old-format list exists; decode both anyway, it is 15 lines].
- **The app's own writers:** 126 `setString`, 48 `setBool`, 15 `setStringList`, 5 `setInt` and 1 `setDouble` call sites [V, grep].
- **Kotlin already writes into this file.** `ScheduledPromptStore` writes `flutter.oc.schedule*` with `commit()`, and widgets and the tile read `flutter.oc.*`. The native writer must keep the same encodings: lists as `…Qu!` + JSON, ints as Long, doubles as the prefix string.
- **Write semantics:** the legacy plugin uses `commit()` and returns its boolean. The native `KeyValueStore` should also `commit()` on `Dispatchers.IO` and keep the fail-closed paths: a refused write rereads and reports.

```kotlin
object FlutterPrefsCodec {
    const val FILE = "FlutterSharedPreferences"; const val KEY_PREFIX = "flutter."
    private const val LIST = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"; private const val JSON_LIST = "$LIST!"
    private const val DOUBLE = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
    private const val BIGINT = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBCaWdJbnRlZ2Vy"
    fun decode(raw: Any?): PrefValue? = when (raw) {
        null -> null
        is Boolean -> PrefValue.Bool(raw)
        is Long -> PrefValue.Int(raw)
        is Int -> PrefValue.Int(raw.toLong())                 // defensive: Kotlin writers might use putInt
        is Float -> PrefValue.Dbl(raw.toDouble())
        is Set<*> -> PrefValue.StrList(raw.filterIsInstance<String>())
        is String -> when {
            raw.startsWith(JSON_LIST) -> PrefValue.StrList(Json.decodeFromString<List<String>>(raw.substring(JSON_LIST.length)))
            raw.startsWith(LIST) -> PrefValue.StrList(decodeJavaSerializedList(raw.substring(LIST.length)))
            raw.startsWith(DOUBLE) -> PrefValue.Dbl(raw.substring(DOUBLE.length).toDouble())
            raw.startsWith(BIGINT) -> PrefValue.Str(BigInteger(raw.substring(BIGINT.length), 36).toString())
            else -> PrefValue.Str(raw)
        }
        else -> PrefValue.Undecodable(raw::class.simpleName ?: "?")
    }
    fun encodeList(v: List<String>) = JSON_LIST + Json.encodeToString(v)
    fun encodeDouble(v: Double) = DOUBLE + v.toString()
}
```

### 8.2 `SharedPreferencesAsync` / DataStore backend (NOT used by this app; detect only)

- **DataStore backend** (the default for `SharedPreferencesAsyncAndroidOptions`):
  - The file is `files/datastore/FlutterSharedPreferences.preferences_pb`, from `preferencesDataStore("FlutterSharedPreferences")`.
  - Keys have **no** `flutter.` prefix.
  - Types: bool → boolean key, int → long key, double → **double key**, String → string key, list → string `…Qu!` + JSON.
  - The store is created lazily, so the file only exists if the async API was ever used.
- **SharedPreferences backend of the async API:** with no `fileName` it uses `PreferenceManager.getDefaultSharedPreferences` (`shared_prefs/io.github.eslamasabry.opencode_mobile_preferences.xml`); otherwise the named file. Doubles and lists use the same string prefixes; there is no `flutter.` prefix.
- **Migration action:** if either file exists, log a count of its keys (never the values) and ignore it. Nothing first-party writes there [V, 0 call sites].

### 8.3 `flutter_secure_storage` 11.2.0, Android, default options (the app passes no `AndroidOptions` anywhere) [V]

The Dart defaults (`android_options.dart`) are:
- `resetOnError: true` (a decrypt failure deletes the data!);
- `keyCipherAlgorithm: RSA_ECB_OAEPwithSHA_256andMGF1Padding`;
- `storageCipherAlgorithm: AES_GCM_NoPadding`;
- no `storageNamespace` and no `preferencesKeyPrefix`.

The Java side (`FlutterSecureStorageConfig`, `StorageCipherFactory`, `StorageCipherImplementationGCM`, `KeyCipherImplementationRSAOAEP`) uses these names:

| Item | Exact value |
|---|---|
| Value prefs file | `shared_prefs/FlutterSecureStorage.xml` (`DEFAULT_PREF_NAME`) |
| Entry name | `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg" + "_" + key`, for example `…Cg_pw.1735…` |
| Entry value | `Base64.DEFAULT` (with line breaks) of `IV[12] ‖ AES-GCM ciphertext ‖ tag[16]`. The plaintext is UTF-8, with no AAD. |
| Wrapped AES key file | `shared_prefs/FlutterSecureKeyStorage.xml` (`getEffectiveKeyStoragePrefsName()` with no namespace) |
| Wrapped AES key entry | `AESVGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK`. The value is `Base64.DEFAULT` of an RSA-OAEP-wrapped **16-byte** AES key (AES-128). |
| Keystore key | RSA key pair, alias **`io.github.eslamasabry.opencode_mobile.FlutterSecureStoragePluginKeyOAEP`** (`packageName + ".FlutterSecureStoragePluginKeyOAEP" + ""`) |
| Unwrap cipher | `Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround")`, `init(UNWRAP_MODE, privateKey, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT))`, `unwrap(bytes, "AES", Cipher.SECRET_KEY)`. **Note MGF1 uses SHA-1**: copy this exactly. |
| Algorithm markers | `shared_prefs/FlutterSecureStorageConfiguration:FlutterSecureStorage.xml` (namespaced, written now), falling back to legacy `FlutterSecureStorageConfiguration.xml`. Keys `FlutterSecureSAlgorithmKey` = `RSA_ECB_OAEPwithSHA_256andMGF1Padding` and `FlutterSecureSAlgorithmStorage` = `AES_GCM_NoPadding`. |

```kotlin
fun readFlutterSecrets(ctx: Context): Map<String, Result<String>> {     // never throws; per-entry results
    val data = ctx.getSharedPreferences("FlutterSecureStorage", Context.MODE_PRIVATE).all
    val prefix = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg_"
    val names = data.keys.filter { it.startsWith(prefix) }
    if (names.isEmpty()) return emptyMap()
    val aes: SecretKey = runCatching {
        val wrapped = ctx.getSharedPreferences("FlutterSecureKeyStorage", Context.MODE_PRIVATE)
            .getString("AESVGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK", null) ?: error("no wrapped key")
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val priv = ks.getKey(ctx.packageName + ".FlutterSecureStoragePluginKeyOAEP", null) as PrivateKey
        Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround").run {
            init(Cipher.UNWRAP_MODE, priv, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT))
            unwrap(Base64.decode(wrapped, Base64.DEFAULT), "AES", Cipher.SECRET_KEY) as SecretKey
        }
    }.getOrElse { e -> return names.associate { it.removePrefix(prefix) to Result.failure(e) } }   // ⇒ "enter the password again"
    return names.associate { n ->
        n.removePrefix(prefix) to runCatching {
            val b = Base64.decode(data[n] as String, Base64.DEFAULT)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, aes, GCMParameterSpec(128, b, 0, 12))
                String(doFinal(b, 12, b.size - 12), Charsets.UTF_8)
            }
        }
    }
}
```

- **Markers.** Check them first. If they are missing or have other values (data written by v9: `RSA_ECB_PKCS1Padding` with alias `…FlutterSecureStoragePluginKey`, and AES-CBC), treat every entry as unreadable. The person re-enters the password; this mirrors `requiresPasswordReentry`.
  - The app went `^9.2.2` (2026-08-23) → 10.0.0 (08-27) → 11.0.0 (09-02) → 11.2.0 (09-27) (`state-data.md` §2.2).
  - A native reader *could* also decode the v9 format and so rescue installs that skipped 10.x. Skip that ("no users yet").
- **Plugin quirk.** The plugin's `readAll` matches with `contains(prefix)` and strips with `replaceFirst(prefix + "_")`. Use `startsWith`: the result is the same for real data.
- **The Keystore survives the switch.** Keystore entries belong to the app UID. An in-place update with the same package and signing key keeps the UID, so the alias stays reachable. This is standard Android behaviour; **prove it on the emulator** with a real Flutter build → native build update (section 10).
- **Never write** with `resetOnError` semantics. A native decrypt failure must never delete the source.

### 8.4 Directories (`path_provider_android` 2.3.1, JNI) [V]

| Dart call | Android path |
|---|---|
| `getApplicationSupportDirectory()` | `context.filesDir` = `files/`. All first-party file stores live here (`state-data.md` §4.5). |
| `getApplicationDocumentsDirectory()` | `context.getDir("flutter", MODE_PRIVATE)` = **`app_flutter/`**. Nothing first-party lives here; the Flutter engine and Shorebird may leave files. Ignore it, and delete it in a *later* release. |
| `getTemporaryDirectory()` / cache | `context.cacheDir` (the project-export plan only) |

### 8.5 Linux desktop (Flutter build) formats [V from source; path confirmed on the owner's PC]

- **Prefs.** The file is `$XDG_DATA_HOME/io.github.eslamasabry.opencode_mobile/shared_preferences.json`, usually `~/.local/share/…`.
  - `path_provider_linux` 2.2.2 uses the GApplication id (`APPLICATION_ID` in `linux/CMakeLists.txt`), falling back to the executable name.
  - It is a JSON object with **native JSON types** and the same `flutter.` key prefix: lists are JSON arrays and doubles are numbers.
  - The owner's PC has this file (736 B, 2026-09-08). Its keys include `flutter.oc.profiles` and `flutter.oc.activeProfile`.
- **Secrets** (`flutter_secure_storage_linux` 3.0.2): **one** libsecret item whose secret is a JSON object `{key: value, …}` of all entries.
  - Attribute `account = "io.github.eslamasabry.opencode_mobile.secureStorage"`, label `"io.github.eslamasabry.opencode_mobile/FlutterSecureStorage"`.
  - Look it up **by the `account` attribute only**, with `SECRET_SCHEMA_DONT_MATCH_NAME` or `secret-tool search account …`. The plugin builds its `SecretSchema` name from a `std::string` that is reassigned later (`Secret.hpp`), so the stored `xdg:schema` value is unreliable [U].

---

## 9. Day-1 migration: network-data view of the order

This complements `state-data.md` §3, which has the full per-key recipe.

0. **Marker check.** If `native.migration.v1` already says the run is complete, skip everything. Steps are idempotent, and progress is recorded after each one.
1. **Backup.** Copy these files into `files/migration/flutter-v57/`, then fsync:
   - `shared_prefs/FlutterSharedPreferences.xml`
   - `FlutterSecureStorage.xml`
   - `FlutterSecureKeyStorage.xml`
   - `FlutterSecureStorageConfiguration*.xml`

   Use raw file copies, because SharedPreferences may already be loaded. Copy before the first `getSharedPreferences` call of the native app.
2. **Detect strays.** Look for the DataStore and default-prefs files from 8.2, and log key counts only.
3. **Secrets.** For each entry, run `readFlutterSecrets`, then `SecretStore.write`, then `read` back and compare. On any failure, set that profile's re-enter flag and continue. Never abort the whole run on one entry. Register each value with the redactor first.
4. **Prefs.** Adopt them in place: no copy. Run the Flutter-side idempotent migrations the old app would have run (`state-data.md` §3.5).
5. **Big blobs into Room.**
   - Covers the offline queue, drafts and stash indexes, receipts and caches.
   - Decode with the codecs, insert inside one transaction, then verify counts and SHA-256 per blob.
   - **Leave the source keys in place for this release.** The next release removes them (keep a `oc.olderDraftsBackupV1`-style backup) once the queue's dispatch markers have been honoured.
6. **Files.** Nothing moves.
7. **Failure.** Show a plain-words failure ("Some saved sign-ins need your password again"), with technical details under Details only. The Flutter data stays as it is, so a retry is always possible.

---

## 10. Tests to write first (this topic)

**JVM (commonTest/jvmTest):**
- `FlutterPrefsCodec` vectors: JSON lists, legacy Java-serialized lists (generate fixtures once with `ObjectOutputStream` on the JVM), doubles, a Long → Int narrowing guard, and refusal of the clash prefixes.
- SSE parser vectors (port `api2_sse_test.dart`) and the reconnect timing schedule.
- Event registry completeness against the 91-type list, plus every fixture in `test/fixtures/api2/*` decoding to the same variant as in Dart.
- Unknown and malformed events never throw.
- `exceptionsWithDebugInfo` off: an exception message for a body containing a fake key does not contain the key.
- Route table vs the committed OpenAPI snapshot.
- Room schema `profile_id` coverage.
- No-redirect, no-proxy, no-retry-on-POST, against MockWebServer (OkHttp's `mockwebserver3`): a POST over a connection that drops after the request is sent must not be sent twice.

**Instrumented (emulator, `connectedAndroidTest`):** this test needs the real Keystore.
1. Install the **Flutter APK** (version 2.1.0+57, local release key `1DE5BF08…D60C`).
2. Seed known prefs and secrets. Use the debug UI or a seeded profile; Flutter debug APKs don't run, so seed through the UI or the existing `BuiltinRestoreAcceptance`-style harness.
3. Install the **native APK** over it with `adb install -r`, using the same key and a higher versionCode.
4. Assert that every secret decrypts, every pref decodes, Room counts match, and the backup files exist.
5. Re-run: there must be no change (idempotent).

---

## 11. Unknowns and things to verify

1. **OC2 SSE heartbeat interval** on 2.1.1 and 2.2.x. It is needed to set the SSE socket idle timeout to about 3× that value. Measure it with `curl -N` against a live server.
2. **Room 3 toolchain combination:** Room 3.0.3 + KSP 2.3.13 + Kotlin 2.4.21 + AGP 9.3 + the Android-KMP library plugin + iOS targets. It is unproven together (spike, section 6.2). Also check the builder API name (`setQueryCoroutineContext` vs `setCoroutineContext`).
3. **SQLite features:** whether `sqlite-bundled` 2.7.1 compiles in FTS5 and JSON1. This only matters if we add transcript search.
4. **DataStore 16 KB claim:** the reason the Flutter plugin reverted DataStore 1.2.0 for 16 KB pages. The `.so` alignment looks fine; check `zipalign -c -P 16` on a real APK if DataStore is adopted.
5. **OkHttp 5.x release notes:** the official changelog URL returned 404. Versions come only from Maven metadata. Read GitHub `CHANGELOG.md` before pinning 5.5.1 deliberately; Ktor 3.6.1 already pulls it.
6. **Codegen candidates:** Fabrikt's and Kotlin SDKGen's KMP compatibility and maturity. Only relevant if a Gas City codegen spike happens.
7. **Durable session log** (`/api/experimental/session/{id}/log`): its frame format, `follow` semantics and stability on 2.1.1 and 2.2.x. The Dart class exists but is unused in production (`protocols.md` §8.6).
8. **Linux libsecret schema name** of the Flutter Linux build (8.5). Confirm by inspecting the owner's PC keyring with `secret-tool search --all account io.github.eslamasabry.opencode_mobile.secureStorage`, without printing secrets.
9. **Old-format lists:** whether any Java-serialized list survives on the owner's phone. Count prefix types at migration and log the counts.
10. **KSafe's on-disk format and key custody details**, if it is chosen for iOS or JVM. Not needed for Android.
11. **Darwin engine pinning:** the exact `handleChallenge` API shape in Ktor 3.6.1 for leaf-DER pinning on iOS. iOS is a later phase.

---

## 12. Sources (fetched or read 2026-11-01)

**Web (official unless noted):**
- Ktor releases. 3.6.1 is dated 2026-10-29: https://github.com/ktorio/ktor/releases
- Ktor SSE client docs (Ktor 3.6.1): https://ktor.io/docs/client-server-sent-events.html
- Ktor client engines (Ktor 3.6.1): https://ktor.io/docs/client-engines.html
- kotlinx.serialization releases. v1.12.0 is dated 19 Oct and built with Kotlin 2.4.10: https://github.com/Kotlin/kotlinx.serialization/releases and https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.12.0
- Room 3 release notes: https://developer.android.com/jetpack/androidx/releases/room3
- Room KMP guide: https://developer.android.com/kotlin/multiplatform/room
- Room 3.0 announcement (Android Developers Blog, 2026-03): https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html
- DataStore release notes: https://developer.android.com/jetpack/androidx/releases/datastore
- AndroidX Security release notes (deprecation): https://developer.android.com/jetpack/androidx/releases/security
- SQLDelight changelog: https://github.com/sqldelight/sqldelight/blob/master/CHANGELOG.md
- OpenAPI Generator Kotlin generator (page updated 2026-09-24): https://openapi-generator.tech/docs/generators/kotlin/
- Fabrikt README: https://github.com/fabrikt-io/fabrikt
- Kotlin SDKGen (third-party listing): https://klibs.io/project/nabobery/kotlin-sdkgen
- KSP releases: https://github.com/google/ksp/releases
- KSafe README: https://github.com/ioannisa/KSafe
- multiplatform-settings README: https://github.com/russhwolf/multiplatform-settings

**Maven metadata** (versions and lastUpdated):
- `https://repo1.maven.org/maven2/<group>/<artifact>/maven-metadata.xml` for okhttp, okhttp-sse, ktor-client-core, ktor-client-darwin, kotlinx-serialization-json, sqldelight runtime, multiplatform-settings, openapi-generator, tink-android, ksafe, kvault, java-keyring, KSP, kotlin-stdlib, coroutines, kotlinx-io, okio and kotlinx-datetime.
- `https://dl.google.com/android/maven2/<group>/<artifact>/maven-metadata.xml` for room3-runtime, room-runtime, datastore-preferences, datastore-tink, sqlite-bundled, security-crypto and biometric.
- The Ktor 3.6.1 `ktor-client-okhttp-jvm` `.module` file is the source of the OkHttp 5.5.1 dependency.

**Library source read directly:**
- From Maven sources jars:
  - `ktor-client-core-jvm-3.6.1-sources.jar`: `DefaultClientSSESession.kt`, `SSE.kt`, `SSEConfig.kt` and `HttpTimeout.kt`.
  - `ktor-client-okhttp-jvm-3.6.1-sources.jar`: `OkHttpConfig.kt`, `OkHttpEngine.kt` and `OkUtils.kt`.
  - `okhttp-jvm-5.5.1-sources.jar`: `OkHttpClient.kt` KDoc, `RetryAndFollowUpInterceptor.kt`, `Dispatcher.kt` and `RealCall.kt`.
  - `kotlinx-serialization-core` and `-json` 1.12.0: `SealedSerializer.kt`, `AbstractPolymorphicSerializer.kt`, `PolymorphicModuleBuilder.kt` and `JsonConfiguration.kt`.
  - `datastore-core-android` 1.2.0 and 1.3.0 aars: `.so` alignment.
  - `sqlite-bundled-android` 2.7.1 aar: `.so` sizes.
- From `~/.pub-cache/hosted/pub.dev/`:
  - `shared_preferences_android-2.4.27` (Kotlin sources and CHANGELOG)
  - `shared_preferences-2.5.5`
  - `flutter_secure_storage-11.2.0` (Android Java sources, `android_options.dart` and CHANGELOG)
  - `path_provider_android-2.3.1`
  - `shared_preferences_linux-2.4.1`
  - `path_provider_linux-2.2.2`
  - `flutter_secure_storage_linux-3.0.2`

**Repo (read-only):**
- From `/home/eslam/Storage/Code/wt/oc_app-batch1`: `pubspec.yaml`, `pubspec.lock`, `lib/api2/{sse2,events,transport}.dart`, `docs/opencode2-protocol-notes.md`, `contracts/*.json`, `android/app/src/main/{AndroidManifest.xml,res/xml/network_security_config.xml}` and the Kotlin readers of `FlutterSharedPreferences`.
- Demo: `/home/eslam/Storage/Code/kmp-demo/gradle/libs.versions.toml` and `shared/.../api/{OcClient,JsonX}.kt`.
- Area maps: `../map/protocols.md` and `../map/state-data.md`.
