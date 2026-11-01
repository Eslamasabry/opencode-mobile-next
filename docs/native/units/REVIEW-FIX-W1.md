# REVIEW-FIX-W1 — the wave-1 second-eye findings, checked and fixed

Date: 2026-11-01. Branch: `native/reviewfix-w1-2026-11-01`, created from `claude/native-reviewfix-w1-0yiiex` at
`bad3a328` (the M0 tip after INTEGRATE-W1). Write set: `native/core/**`, `native/protocol/oc2/**`,
`native/engine/connection/**` (fixes only), this report.

Inputs: `docs/native/reviews/w1-core-net.md` (12 findings) and `docs/native/reviews/w1-data-oc2.md` (8 findings);
plan 01, 03, 04, 05; `docs/native/units/INTEGRATE-W1.md` (the contract decisions D1–D17).

**Result:** the 4 BLOCKERs (3 in `w1-core-net`, 1 in `w1-data-oc2`) were already fixed by INTEGRATE-W1 and are
verified on the integrated tree. All 13 MAJORs were checked against the integrated code: 12 still applied and are
fixed, each with a test that fails on `bad3a328` and passes now; 1 (the second network contract) was already fixed by
INTEGRATE-W1 and is verified. Of the 3 MINORs, 2 are fixed (with tests) and 1 is deferred with a reason.

Shipping state: implemented, verified locally (commands in §4), committed and pushed to the branch. No PR, no CI run,
no signing, no release. `master` and tags untouched.

---

## 1. Per-finding verdicts

### 1.1 `w1-core-net.md`

| # | Severity | Finding | Verdict | Evidence (test) |
|---|---|---|---|---|
| 1 | BLOCKER | Removed `Failure` constructors still used in networking | **Already fixed** (INTEGRATE D1: one flat `Failure`) | `:core:net` compiles; `HttpTest › B-PROTO-129 statuses become plain failures` and the B-PROTO-128 tests pass |
| 2 | BLOCKER | The status bridge used data classes as singletons and dropped `WaitingForNetwork`/`Stopped` | **Already fixed** (INTEGRATE D9: the bridge is gone, the loop emits core's sealed `StreamStatus` directly) | `EventStreamLoopTest › B-PROTO-133 offline remote stream waits…` (`WaitingForNetwork`), `› N4-D6 a terminal sign-in failure (stop) parks the stream until retryNow` (`Stopped`), `› N4-D6 backoff 1 2 4 8…` (`Reconnecting`/`Disconnected` with schedule) |
| 3 | BLOCKER | Gateway contract change left `:engine:connection` uncompilable | **Already fixed** (INTEGRATE D6) | `:engine:connection` compiles against `backend` + `connect(spec)`; `ConnectionRegistryTest` 3/3 pass |
| 4 | MAJOR | The default engine ignored origin, cleartext consent and the pin | **Fixed** | `TransportTrustTest › B-SEC-007 plain http without consent is refused and nothing is sent`, `› B-SEC-007 a request to another origin is refused…`, `› B-SEC-007 a pin on plain http is refused`, `› B-SEC-007 a different leaf never passes a pinned client and no request is written`, `› B-SEC-007 the pinned leaf is the identity, without CA trust` |
| 5 | MAJOR | Empty `data:` lines bypassed the SSE memory cap | **Fixed** | `SseParserTest › B-PROTO-033 empty data lines count toward the cap`; fuzz oracle corrected (`› B-PROTO-033 fuzz 10k cases…`) |
| 6 | MAJOR | Closing `NetClient` left the engine (and an open stream) alive | **Fixed** | `TransportTrustTest › B-PROTO-148 closing the client ends an open stream and frees the engine` |
| 7 | MAJOR | Automatic reconnect could not be disabled | **Fixed** (loop + `GatewaySpec.reconnectAllowed`) | `EventStreamLoopTest › B-PROTO-146 reconnect off - a drop schedules nothing and retryNow dials once`, `› B-PROTO-146 turning reconnect off during a wait cancels the scheduled try, turning it on dials`, `› B-PROTO-146 a live stream stays up when reconnect turns off, and a new network does not drop it` |
| 8 | MAJOR | OC2 401/403 stopped the stream; a password-update retry during the refused request was dropped | **Fixed** | `HttpTest › N4-D6 OpenCode 2 401 and 403 keep the backoff…`, `› N4-D6 a backend whose sign-in failure is terminal stops on 401`, `EventStreamLoopTest › N4-D6 OpenCode 2 sign-in failure keeps the backoff and is published as lastFailure`, `› N4-D6 a retryNow sent while a refused request was in flight is kept` |
| 9 | MAJOR | The resume cursor advanced before the frame was handed over | **Fixed** | `EventStreamLoopTest › N4-D6 the resume cursor moves only after the collector took the frame` |
| 10 | MAJOR | 120 s total deadline on every REST call | **Fixed** | `TransportTrustTest › plan 04 section 3_4 a reply that keeps sending past the read timeout is not cut off` (+ `› … a reply that goes quiet for the read timeout times out`, which keeps the inactivity rule) |
| 11 | MAJOR | A second, incompatible network contract in `:core:net` | **Already fixed** (INTEGRATE D8) | `NetworkGate` takes `core.model.NetworkStatus`; the net test fake and `:core:testing`'s `FakeNetworkStatus` both implement that one interface. No composition test with `:core:testing` was added (`:core:net` has no test edge to it; not added here). |
| 12 | MINOR | Jitter took hidden-idle ticks under 60 s | **Fixed** | `PacerTest › PB-42 no hidden-idle timer fires faster than 60 s, jitter included` |
| — | coverage | `HttpTest:86` "no request timeout" uses an immediate reply | **Partly**: REST "no total deadline" is now proven with real sockets (#10). The SSE connector's own `INFINITE_TIMEOUT_MS` override is still only asserted by that weak test (§7). | |

### 1.2 `w1-data-oc2.md`

| # | Severity | Finding | Verdict | Evidence (test) |
|---|---|---|---|---|
| 1 | BLOCKER | The OC2 adapter declared its own `GatewayEvent` vocabulary | **Already fixed** (INTEGRATE D10/D11: `Oc2CoreProjection` is the only exit and emits core's `GatewayEvent`; `RunOutcome.Idle` and `SessionCompacted` added to core) | `:protocol:oc2` compiles; `Oc2CoreProjectionTest` (B-PROTO-015/019, B-CHAT-005) passes. "Idle never replaces Failed" is the transcript reducer's rule (S2-TRANSCRIPT, INTEGRATE §7). |
| 2 | MAJOR | A failed secret delete could report success on retry and resurrect after restart; a refused put was not compensated | **Fixed** | `AeadSecretStoreTest › B-MIGR-006 a refused delete is retried for real and the password does not come back after restart`, `› B-MIGR-006 a refused write is never persisted by a later successful write of the file` |
| 3 | MAJOR | Cancellation could suppress the change notification after a successful commit | **Fixed** (commit, CAS) | `PrefsKeyValueStoreTest › B-STATE-004 a caller cancelled during the commit still publishes the acknowledged change` |
| 4 | MAJOR | `WriterLane` did not keep admission order | **Fixed** | `WriterLaneTest › B-STATE-002 a write paused after admission is not overtaken by a later one`; regression guard `› B-STATE-002 cancelling a queued write never lets the next one start early` |
| 5 | MAJOR | Preference reads bypassed the migration gate; secrets exposed without the gated binding | **Fixed** | `PrefsKeyValueStoreTest › B-MIGR-005 reads before the gate opens throw GateNotOpen in test builds, MigrationReader reads`, `› B-MIGR-005 a release build counts an early read and answers it`, `› B-MIGR-005 writes wait for the migration gate and the migrator writes past it` (replaces the old "reads are never gated" test), `GatedSecretStoreTest › B-MIGR-006 every secret call waits for the migration gate` |
| 6 | MAJOR | The adapter emitted refresh hints the plan excludes | **Fixed** | `Oc2EventAdapterTest › B-PROTO-141 only Flutter's refresh set becomes a hint, every other family is counted and dropped` |
| 7 | MINOR | `session.compaction.*` decodes but emits nothing | **Deferred** (not fixed): core has no compaction notice part shape yet (`GatewayEvent.SessionCompacted` covers `session.compacted`; the `v2:compaction` part belongs to the chat section / S2-MODEL). Flutter's adapter emits nothing either, and the refetch shows the compaction message. The TODO in `Oc2EventAdapter` stays. | — |
| 8 | MINOR | The CAS-versus-commit test never raced the same key | **Fixed** (test only; the lock was already right) | `PrefsKeyValueStoreTest › B-STATE-004 a refused write's compensation never overwrites a racing compare-and-set of the same key` |

---

## 2. What was built

| Module | Change |
|---|---|
| `:core:net` jvmShared | New `TrustGate.kt`: `TrustGateInterceptor` (plan 04 §4.6 order: user info → origin → cleartext → pin-needs-https → latched pin) and `PinnedLeafTrustManager` (§4.4, ported from the P-N2 spike). `OkHttpEngineFactory.create` installs both on **whatever `base` returns**, so no caller can opt out; handshake failures map to `Certificate(Changed | Expired | Untrusted)`. New `LiveCalls` interceptor and `EngineFactory.release(engine)`: cancels live calls (an open stream's socket closes at once), evicts the pool, shuts the dispatcher's executor down. |
| `:core:net` common | `TrustRefusal(failure) : IOException` (no URL in its message); `TransportFailures.of` returns its failure first. `TransportSpec.requestTimeoutMs` → `readTimeoutMs` (inactivity only, plan 04 §3.3 name); `HttpClientFactory` no longer sets `requestTimeoutMillis`. `NetClient.close()` = `http.close()` → `engine.close()` → `release`. |
| `:core:net` SSE | `SseParser` holds a frame's data as one joined byte buffer (`\n` between lines), and the cap counts exactly those bytes, so empty `data:` lines count; decode once at dispatch (identical to per-line decode: LF is never inside a UTF-8 sequence). |
| `:core:net` loop | `EventStreamLoop(reconnectAllowed: StateFlow<Boolean>)` and `lastFailure: StateFlow<Failure?>` (plan 04 §6.2). Disallowed: no scheduled try, `Disconnected(attempt, null)`, network changes dial nothing and do not drop a live stream, `retryNow()` dials once, the flag turning true dials at once. Stop path no longer drains a pending `retryNow` (pending requests from *before* a try are consumed when the try starts). Frames are handed over before the cursor moves. `KtorSseConnector(stopOnAuthFailure = false)`: OC2 401/403 are ordinary failed tries. |
| `:core:domain` | `GatewaySpec.reconnectAllowed: StateFlow<Boolean>` (default always allowed; plan 04 §11.1/§12.9). |
| `:core:data` secrets | `AeadSecretStore.delete` always commits the removal; `write` restores the previous entry (or its absence) after a refused commit. `SecretEntries.remove` contract: true = acknowledged, also when absent. New `GatedSecretStore` (all four calls await `gate.awaitOpen()`); `AndroidDataStores.secrets(context, gate, …)` returns it, `@MigrationOnly secretsForMigration(…)` returns the raw store. |
| `:core:data` prefs | `PrefsKeyValueStore(earlyReads = EarlyReadPolicy.Throw)`: `get`/`keys` before Open throw `GateNotOpen` (or count, in release); `@MigrationOnly migrationReader()`. Commit, snapshot and `changes` emission are one `NonCancellable` step for `commit`, `compareAndSet` and the migration writer. `MigrationGate.awaitOpen()` (default = `awaitWritable()`). |
| `:core:data` lane | `WriterLane` keeps a tail ticket in the same atomic state as admission; each write awaits its predecessor; a cancelled queued write releases its ticket only when its predecessor completes (`invokeOnCompletion`, no suspension). The mutex is gone. |
| `:protocol:oc2` | `Oc2HintKind` = N4-D22's six kinds; `command/skill/reference/project/worktree/lsp.updated`, `mcp.*.changed`, `vcs.branch.updated`, `filesystem.changed` are dropped and counted in `Oc2EventAdapter.dropped` (wire type → count). |
| `:core:common` | `DefaultPacer` floors a hidden-idle delay at `Cadence.HIDDEN_IDLE_MIN` after jitter. |
| `:engine:connection` | No change needed (finding 3 verified; `ConnectionSession.close()` already closes the gateway). |

## 3. Catalog rows covered

B-SEC-007 (5 tests), B-PROTO-033 (cap + fuzz oracle), B-PROTO-146 (3), B-PROTO-148, B-MIGR-005 (3), B-MIGR-006 (3),
B-STATE-002 (2), B-STATE-004 (2), B-PROTO-141, PB-42; plan-decision names where the plan has no catalog row:
N4-D6 (4 loop/connector tests). B-PROTO-146/148/141 are plan 04 §15.5 rows (coordinator appends them to the catalog).
The two timeout tests have no catalog row; they are named after plan 04 §3.4 (see §5).

## 4. Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` (foojay provided JDK 17 for the toolchain),
`ANDROID_HOME=/opt/android-sdk` (`commandlinetools-linux-16111833_latest.zip`; `platform-tools`,
`platforms;android-37.1`, `build-tools;37.0.0`). Container-only setup (not committed): `~/.gradle/gradle.properties`
`-Xmx6g`, 4 workers; `~/.gradle/init.d/central-mirror.gradle.kts` puts Google's Maven Central mirror first and sets
`robolectric.dependency.repo.url` on every `Test` task (as INTEGRATE-W1 §3).

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew -Pkotlin.incremental=false :core:net:desktopTest :core:data:desktopTest :protocol:oc2:desktopTest :engine:connection:desktopTest :core:common:desktopTest :core:domain:desktopTest --continue` (baseline, BLOCKER check) | Every module compiles and runs (BLOCKERs 1–3 of both reviews verified). The one red test was the old `N4-D6 401 stops the stream…`, already mid-edit. |
| 2 | `./gradlew :core:net:desktopTest` (after the net fixes) | 57 tests, 0 failures |
| 3 | `./gradlew :core:data:desktopTest` | 52 tests, 0 failures (43 before) |
| 4 | `./gradlew :protocol:oc2:desktopTest :core:domain:desktopTest :engine:connection:desktopTest` | 52 / 12 / 3 tests, 0 failures |
| 5 | `./gradlew :core:common:desktopTest` | 19 tests, 0 failures |
| 6 | **Red proof**: worktree at `bad3a328` + the new/changed test files, compile-only edits (drop tests that need new API: `lastFailure`, `reconnectAllowed`, `stopOnAuthFailure`, `GateNotOpen`, `EarlyReadPolicy`, `migrationReader`, `GatedSecretStore`, `dropped`; `readTimeoutMs` → `requestTimeoutMs`), then `./gradlew -Pkotlin.incremental=false :core:net:desktopTest :core:data:desktopTest :protocol:oc2:desktopTest :core:common:desktopTest --continue` | **18 red, as intended**: net 12 (all 7 `TransportTrustTest` fix tests, both SSE tests, cursor, retry-during-stop, OC2 401/403), data 4 (2 secret, cancellation, lane order), oc2 1 (B-PROTO-141), common 1 (PB-42). Green there, as expected: the inactivity-timeout test, the same-key CAS race and the cancelled-queued-writer guard (behaviour that was already right). Tests dropped for compile are red by compile on the old tree. |
| 7 | `./gradlew :quality:test :core:data:testAndroidHostTest :core:net:testAndroidHostTest :core:common:testAndroidHostTest --continue` | Android host: data 46, net 43, common 19, 0 failures. `:quality` 31 tests, 1 failure: `ModuleOwnersTest` — **pre-existing on `bad3a328`**: `MODULE_OWNERS` has no rows for `:android:platform` and `:engine:entry` (coordinator file, outside this write set). Konsist rules all pass, also over the new `jvmShared` code. |
| 8 | `./gradlew compileKotlinDesktop :desktopApp:compileKotlin --continue` | BUILD SUCCESSFUL (nothing downstream broke on the changed contracts) |
| 9 | `git diff bad3a328 HEAD` grep for private IPv4 ranges and e-mail addresses | none (only `127.0.0.1` loopback in tests) |

## 5. Deviations

1. **The trust gate lives in `:core:net` jvmShared**, not in `:core:net-okhttp` (plan 04 §3.2): that module does not
   exist, and adding one needs the coordinator (`MODULE_OWNERS`, settings). The code is a self-contained port of
   the spike, ready to move. `RetryAfterStripInterceptor` and HTTP/1.1-only from §3.2 are not added here (the existing
   `MutationGuard` covers mutations; out of this review's findings).
2. **Self-signed leaves come from the JDK's `keytool`** (subprocess in the test): the module rules forbid
   `okhttp-tls` in `:core:net` ("Module rule: :core:net may not declare com.squareup.okhttp3:okhttp-tls").
3. **`TransportSpec.requestTimeoutMs` renamed `readTimeoutMs`** (plan 04 §3.3's name and meaning). Only `:core:net`
   used it.
4. **`MigrationReader` is in commonMain** (plan 05 §2.2 says androidMain): the store it reads is common code; the
   reader is opt-in `@MigrationOnly`, so the Konsist allowlist can still pin its callers.
5. **`EarlyReadPolicy.Throw` is the default** for every build: the release binding (app graph) must pass a counter
   into `AndroidDataStores.flutterPrefs(…, earlyReads = …)`.
6. **Choices the plan leaves open:** the very first connect is not gated by `reconnectAllowed` (only reconnects are);
   `reconnectAllowed` turning true while disconnected dials at once with the count reset; an OC2 403 keeps the
   backoff like a 401 (plan: "a 403 sets nothing"), and `lastFailure` then reads `SignIn(Forbidden)`, never
   `PasswordRejected`; the pacer floor applies to hidden-idle only (PB-42), not to hidden-watching.
7. **Close cancels via a weak registry of live calls** (`LiveCalls`): OkHttp's dispatcher forgets an async call once
   its headers arrive, so `dispatcher.cancelAll()` alone cannot end an open stream. The reader then sees a failed
   exchange or the engine's cancellation; the test accepts either.
8. Two timeout tests are named after plan 04 §3.4, not a catalog row (no row covers the REST inactivity rule).

## 6. Catalog requests

- `okhttp-tls` alias (plan 04 §2.3 already lists it) — for `:core:net-okhttp` tests once that module exists; then the
  `keytool` helper in `TransportTrustTest` can go.
- Still open from INTEGRATE-W1 §6: `ktor-client-core/okhttp/mock` aliases.
- Not a catalog item, for the coordinator: `MODULE_OWNERS` rows for `:android:platform` and `:engine:entry` (§4 #7).

## 7. Open problems

1. `:quality` `ModuleOwnersTest` is red on the base (§4 #7); not touched here.
2. The SSE connector's `requestTimeoutMillis = INFINITE` is still only covered by `HttpTest › B-PROTO-033 the SSE
   request asks for an event stream with no request timeout…`, which uses an immediate reply (review coverage note).
3. Nothing calls `EventStreamLoop` with a real `reconnectAllowed` yet: the OC2 gateway (which builds the loop from
   `GatewaySpec`) and the connection engine's `passwordRejected` flag over `lastFailure` are later slices.
4. Metro bindings: `GatedSecretStore` for everyone and `secretsForMigration` only for `:android:migration`, plus a
   Konsist allowlist for `migrationReader()`/`secretsForMigration` callers (plan 05 §2.2, §4.2) — app graph and
   `:quality` owners.
5. Review w1-data-oc2 MINOR 7 (compaction notice part) waits for the part shape (S2-MODEL / chat section).
6. The pinned trust manager is proven on the JDK provider only; Conscrypt/API 37 is P-N2's emulator half.
   `CertificateInspector` (plan 04 §4.5) is not ported.
7. iOS: the trust code is JVM-only (jvmShared); nothing was compiled for iOS in this unit.
8. Live-server checks were not run (no OpenCode 2 server here; AGENTS forbids the owner's servers).
