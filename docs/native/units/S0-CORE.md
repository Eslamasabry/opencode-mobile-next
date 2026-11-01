# S0-CORE — the five core modules

Date: 2026-11-01. Branch: `native/core-2026-11-01` (from `claude/native-core-setup-hnp65y` at `6627d320`).
Write set: `native/core/{common,model,domain,security,testing}/**`, the interface part of `native/contracts/s1.md`,
this report.

**Result:** the five core modules are filled. They compile for Android, desktop and iOS klibs (iosArm64 and
iosSimulatorArm64). All 86 `ServerCapabilities` defaults equal the Dart vector file. `Outcome`/`Failure`, the
gateways, `GatewayEvent`, `StreamStatus` and `NetworkStatus` are frozen in `native/contracts/s1.md`.

Shipping state: implemented, verified locally, committed and pushed to the branch. No PR, no CI run, no release.
**Consumers outside the write set do not compile against the frozen API until the 4-file patch in §7 is applied**
by their owners or the coordinator. With that patch applied locally, the whole graph builds and its tests pass (§3).

---

## 1. What was built

| Module | Content |
|---|---|
| `:core:common` | `Failure` with the plan 04 `Failure.Net` family replacing plan 03's flat network categories; `StorageProblem`; `WireTag`, `WireExtras`; `RefusalReason` + `InvalidAddress`, `CredentialsInAddress`, `PinNeedsHttps`. `Outcome` helpers (`onOk`, `onFailed`, `ok()`, `failed()`). Ids: + `PartId`, `ScheduleId`, `ProjectId`, `WorkspaceId`, `InboxItemId`, `FormId`, `PtyId`. `pace/`: `Visibility`, `Cadence` (PB-42 in `init`), `TimerSpec`, `Pacer`, `DefaultPacer`, `timerTableProblems`. |
| `:core:model` | `StreamStatus` (plan 04 sealed form), `NetworkState`/`NetworkChange`, `ServerLocation`/`EventLocation`, `BackendKind` with Dart serial names, `ServerOrigin.toString()` = Dart `serverOrigin`. Value types ported from `lib/domain/server_gateway/value_types_*.dart`, `models*.dart`, `managed_shell.dart`, `web_search.dart` and `api2/models/models_inbox.dart` (Session family, permissions, OC2 forms and inbox, legacy questions, prompts, files/VCS, catalog/MCP/integrations, projects/worktrees/terminals/managed shells). `transcript/Transcript.kt` holds the plan 09 §2.2 shapes for S2-MODEL to complete. |
| `:core:domain` | Live `ServerGateway` as 10 facets (port of `interfaces.dart`: suspend + `Outcome` + value-class ids; forms/inbox/questions inert by default). 11 optional facets. `ServerOperationsGateway` (9 facets, every method defaults to `Unavailable(<capability flag>)`). `GatewayFactory` (plan 04: `kind`, `probe`, `open`), `GatewaySpec`, `ProbeTarget`, `ProbeResult`, `CredentialReader`. `GatewayEvent` + `GatewayEvents` (plan 04 §11.2). `ServerCapabilities` (86 flags) and `PlatformCapabilities` (18 flags). `NetworkStatus`, `NetworkGate`, `UnknownNetworkStatus`, `OpenNetworkGate` and the plan 03 §10.4 platform services. |
| `:core:security` | `Redactor`: a line-for-line port of `KitRedact`, plus `diagnostics()` (the `AppDiagnosticsController.sanitize` rules), `excerpt()` (the `FailedJobReport` tail) and `registerCredentialValues`. `AddressRules`: `isLoopback`, `isPrivateNetwork` (with an IPv6 parser in common code), `isTailnet`, `classify`, `origin`, `normalize`, `needsCleartextConsent`, `normalizePin`, `checkPin`. A small `UrlParts` splitter, because there is no `java.net` in common code. |
| `:core:testing` | `TestDispatchers`, `TestPacer` (the real `DefaultPacer` on virtual time), `TestClock`, `assertTimerTable`, `FakeServerGateway`, `FakeGatewayEvents`, `FakeOperationsGateway`, `FakeGatewayFactory`, `FakeNetworkStatus`, `FakeNetworkGate`. |
| `native/contracts/s1.md` | The interface part: one section per frozen surface, with its rules. |

Build files changed (all in the write set): `:core:model`, `:core:domain` and `:core:security` now expose their
core dependencies as `api`, because these types appear in their public signatures. `:core:model` also exposes
`kotlinx-serialization-json` as `api`, because `JsonObject` appears in public value types. `:core:testing` exposes
common, model and domain as `api` and adds `kotlin("test")` for `assertTimerTable`. No catalog entry was added.

## 2. Catalog rows and vectors covered

| Row / vector | Test (module, name) |
|---|---|
| **B-PROTO-137** | `:core:common` `CommonTest` › `B-PROTO-137 decoding errors never contain the input JSON` (5 bodies: wrong type, nested, truncated, malformed, wrong shape; a fake key never appears in the error). |
| **Capability snapshot** (default vector of B-PROTO-016) | `:core:domain` desktopTest `CapabilitySnapshotTest` (3 tests): the Kotlin defaults equal `capabilities/default.json` flag for flag and in order; `default.json` still equals `lib/domain/server_gateway/capabilities.dart` (a drift check that parses the Dart constructor); `flags()` is the serial form. The JSON was generated from the Dart constructor at `580803d4`. |
| FeatureId contract | `:core:domain` desktopTest `FeatureNamesTest`: every `unavailable("x")` default names a real capability flag. |
| **Redactor vectors** | `:core:security` `RedactorTest` (10 tests): the 11 G12 fake keys from `test/redaction_test.dart`; the minimum secret length and `forget`; user info, private keys, named values, JWTs, paths and declarations; **B-SEC-008** (3 tests: secrets/URLs/tokens; long code names kept; bounded); **B-SEC-079** (2 tests: whole-input redaction before truncation; 120-line / 16 384-char tail); details; credential registration from config JSON (part of B-PROTO-104). |
| **Address vectors** | `:core:security` `AddressRulesTest` (9 tests): **B-SEC-119** (all three Flutter groups, verbatim); B-SEC-122 classifier part; **B-SEC-153** pure parts (origin with effective port, refusals, loopback vs remote consent, pin normalisation and the HTTPS requirement); B-SEC-120 normaliser part (a bare address gains https, loopback gains http). |
| PB-42 / plan 13 §11.2 | `:core:common` `PacerTest` (7 tests): the hidden-idle floor, the jitter range, the timer-table problems, the first tick after one period, no timer when the cadence is null, an immediate tick on return to the foreground, jitter bounds. |
| Contract shape tests | `DomainTest` (4: operation defaults, inert forms/inbox, facets, project vs all views), `CapabilitiesTest` (4), `ModelTest` (4: profile JSON names, origin printing, closed `StreamStatus`, `isRenderable`), `LinksTest` (1). |

The tests were written before the code for each module. The first red state was a compile failure, because the
types did not exist yet; no runtime red run is recorded.

Not covered here, because other units own them: the rest of B-SEC-120…125, 153 and 154 (consent persistence, pairing,
connect refusal: S1-SECNET and the profiles engine), B-PROTO-016's OC2 matrix (`Oc2Capabilities.Base`, S1-OC2), and
the PB-51 schedule (S1-NET).

## 3. Commands and results

Every command was run from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and
`ANDROID_HOME=/opt/android-sdk`. The container has 4 vCPUs, 15 GB RAM and the checked-in caps (`-Xmx2g`, 2 workers).

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :core:common:desktopTest :core:security:desktopTest :core:domain:desktopTest :core:model:desktopTest` (baseline, before any edit) | BUILD SUCCESSFUL, 27 tasks |
| 2 | `./gradlew -Pkotlin.incremental=false :core:{common,model,domain,security}:desktopTest :core:testing:desktopTest` | BUILD SUCCESSFUL. common 12, model 4, domain 12, security 20 tests; 0 failures. |
| 3 | `./gradlew -Pkotlin.incremental=false :core:common:testAndroidHostTest :core:{security,model,domain,testing}:compileAndroidMain` | BUILD SUCCESSFUL. Only `:core:common` has Android host tests (its 12 pass). |
| 4 | `./gradlew -Poc.ios=true --continue :core:{common,model,security,domain,testing}:compileKotlin{IosArm64,IosSimulatorArm64}` | BUILD SUCCESSFUL in 59 s: 10 klibs (common 396K, model 680K, security 232K, domain 568K, testing 188K for iosArm64). Linux klib cross-compilation; Kotlin/Native prebuilt 2.4.21 was downloaded to `~/.konan`. |
| 5 | §7 patch applied, uncommitted: `./gradlew -Pkotlin.incremental=false --continue desktopTest testAndroidHostTest :quality:test :desktopApp:compileKotlin :androidApp:assembleDebug` | `assembleDebug` OK (13.9 MB preview APK); desktop compiles; **81 tests, 1 failure**. The failure is `ModuleOwnersTest`, which was already failing at `6627d320` (see §8.1). `GraphSmokeTest` (2), `DesktopShellUiTest`, the Konsist rules (11), `ConnectionRegistryTest` and `RouteSavingTest` pass. The patch was then reverted. |

An incremental-compilation cache error appeared once ("FqNames can't be derived from DirtyData") after
`BackendKind` became `@Serializable`. Running with `-Pkotlin.incremental=false` cleared it, and the rest of the
runs used that flag.

## 4. Resolved versions (from this build)

Gradle 9.7.1 (wrapper), AGP 9.3.3, Kotlin 2.4.21 (JVM, Android and the Kotlin/Native prebuilt
`kotlin-native-prebuilt-linux-x86_64-2.4.21`), kotlinx-coroutines 1.11.0, kotlinx-serialization-json 1.12.0,
kotlinx-collections-immutable 0.5.2, Metro 1.5.1, Turbine 1.2.1. JDK 21.0.12.1 for Gradle and JDK 17.0.20.1 for the
toolchain. Android SDK: `platforms;android-37.1`, `android-36`, `build-tools;37.0.0`, platform-tools; cmdline-tools
`commandlinetools-linux-16111833_latest.zip`. No new library was used.

Environment notes (container only, nothing checked in):
- `~/.gradle/init.d/central-mirror.gradle.kts` points `repo.maven.apache.org` at Google's Central mirror and puts
  that mirror, Google and the plugin portal first in `pluginManagement`. Without it, Maven Central's HTTP 429 through
  the proxy aborts resolution, as the P1 report also found.
- `~/.gradle/gradle.properties` sets `org.gradle.java.installations.auto-download=false`.
- `~/.claude/settings.json` raises `BASH_DEFAULT_TIMEOUT_MS`.

## 5. Decisions and deviations from the plan

| # | Plan says | Built | Why |
|---|---|---|---|
| D-C1 | 03 §10.1: flat `Failure` with `Network`, `TimedOut`, `Certificate`, `SignIn`, `NotFound`, `Conflict`, `Busy`, `Server`, `Rejected`, `UnreadableReply`. 04 §3.8: `Failure.Net` "proposed for the freeze". | 03's app-level categories, plus `Failure.Net`. The flat network categories are removed. | Otherwise one input would have two values, and `:ui:words` would need two sentences for it. 04's table is more precise. |
| D-C2 | 03: `Storage(details)`. Audit 03 BLOCKER 4 asks for storage failure kinds. | `Storage(problem: StorageProblem, details)`: `WriteRefused`, `InvalidValue`, `Unreadable`, `Full`, `KeystoreUnavailable`, `Unknown`. | S1-DATA can map 05's `StorageRefused`/`InvalidValue` without changing the hierarchy. S1-DATA confirms the list. |
| D-C3 | 04 §11.1 uses `String` ids; 03 §10.4 uses value-class ids. | Value-class ids in gateways and `GatewayEvent`. The plan 09 transcript types keep `String`, as 09 wrote them. | Type safety at the boundary every engine uses. The transcript types belong to S2-MODEL. |
| D-C4 | 04 §6.3: `StreamStatus` in `:core:net`; 04 §6.4: `NetworkStatus` in `:core:net`. | `StreamStatus`, `NetworkState` and `NetworkChange` are in `:core:model`. The `NetworkStatus`/`NetworkGate` interfaces are in `:core:domain`, with `UnknownNetworkStatus`/`OpenNetworkGate`. | Audit 04 BLOCKER 3 (domain → net is forbidden). `:core:net` implements `NetworkGate`. |
| D-C5 | 03 §10.3: `StreamSignal<T>` and `EventGateway.events(scope): Flow<StreamSignal<ServerEvent>>`. | Plan 04's `GatewayEvents` (status, project, all, connects, retryNow). | 04 is the later, more detailed design, and it is what N4-D9 (one stream, two views) needs. |
| D-C6 | 13 §11.2: `DefaultPacer(visibility, clock, dispatchers)`. | `DefaultPacer(visibility, random)`. | `delay()` is the clock (virtual time in tests). Unused constructor parameters would warn, and warnings fail T2. Jitter needs a seedable `Random`. |
| D-C7 | 03 §10.4: `GatewayFactory.connect(profile, credentials)`. 04 §11.1: `probe(target): ProbeResult` and `open(spec, scope)`. | 04's shape. `probe` returns `Outcome<ProbeResult>` (rule 4: suspend commands return `Outcome`). Credentials come through a new `CredentialReader`. | 04 is later. `CredentialReader` is the seam audit 03 BLOCKER 3 asks for. |
| D-C8 | Plan: 85 capability flags. | **86**. | Dart added `claudeAgent` (with `withClaudeAgent`) after the plan was written. The Dart file is the source of truth, and the snapshot test pins it. |
| D-C9 | 04: "every optional facet method has a default returning `Net.NotSupported`". | Operation defaults return `Failure.Unavailable(FeatureId)` (03's rule). `Net.NotSupported` is reserved for a server's 404/405/501, which gets latched. | They mean different things to the person and to the latch logic. |
| D-C10 | 04 §11.1 `BackendKind { OpenCode, Paseo, Codex }`. | `OpenCode2`, `Paseo`, `Codex`, with `@SerialName("openCode")` etc. | Keeps the P1 name (fewer consumer edits), and the persisted values still equal Dart's. |
| D-C11 | Plan 04 lists ~17 more optional facets (SessionNote, Export/Import, …). | Not defined. | Their Dart definitions are outside `interfaces.dart`. The slice that ports each feature freezes its facet. |
| D-C12 | `AddressRules.classify` in P1 treated all of 127/8 as loopback. | Dart parity: only `localhost`, `127.0.0.1` and `::1` count as loopback. | B-SEC-153 vectors; one predicate on every platform. |
| D-C13 | P1 `Redactor` registered secrets of ≥ 4 characters. | ≥ 6 (Dart `registerKnownSecret`). | Parity. |
| D-C14 | Write the transcript types? (They belong to S2-MODEL, plan 15 row S2-MODEL.) | The 09 §2.2 shapes only, without `fromName`/`refineFromText`/`ToolStatus.parse`/`declined` and the trap tests; marked `TODO(S2-MODEL)`. | `GatewayEvent` and the gateways must compile today. |
| D-C15 | Instruction: remove private IPs and emails from the changes. | The kept literals are synthetic test vectors: private-range boundaries copied verbatim from `test/private_network_cleartext_test.dart` and `test/server_trust_test.dart`, RFC 5737 documentation addresses, and credential-in-URL vectors such as `user:pass@example.com`. None is a real host or mailbox. | B-SEC-119/153 *are* those literals; removing them removes the rows. The coordinator may veto. |

`FormField` is a deliberately thin port: key, type, title, description, required, options, plus `raw: JsonObject`
for conditions, limits and defaults. The forms engine owner refines it.

## 6. Catalog requests

None. No new library was needed.

## 7. Consumer patch (outside the write set — owners or the coordinator apply it)

These 4 files under `:engine:connection` and `:quality` need edits to compile against the freeze. This is the exact
patch that §3 #5 verified. It covers `GatewayFactory.kind`/`open`, the sealed `StreamStatus`, the fake factory's
`opened` list, and the shorter `Session` constructor.

```diff
diff --git a/native/engine/connection/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionRegistry.kt b/native/engine/connection/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionRegistry.kt
index 8b8151f4..d8a3ed4a 100644
--- a/native/engine/connection/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionRegistry.kt
+++ b/native/engine/connection/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionRegistry.kt
@@ -14,7 +14,9 @@ import io.github.eslamasabry.oc.core.common.Outcome
 import io.github.eslamasabry.oc.core.common.ids.ConversationRef
 import io.github.eslamasabry.oc.core.common.ids.ProfileId
 import io.github.eslamasabry.oc.core.common.supervisedContext
+import io.github.eslamasabry.oc.core.domain.CredentialReader
 import io.github.eslamasabry.oc.core.domain.GatewayFactory
+import io.github.eslamasabry.oc.core.domain.GatewaySpec
 import io.github.eslamasabry.oc.core.model.ServerProfile
 import kotlinx.collections.immutable.ImmutableMap
 import kotlinx.collections.immutable.PersistentMap
@@ -45,7 +47,7 @@ public class ConnectionRegistry(
     private val dispatchers: AppDispatchers,
 ) {
     init {
-        check(factories.groupBy { it.backend }.all { it.value.size == 1 }) { "two GatewayFactory for one backend" }
+        check(factories.groupBy { it.kind }.all { it.value.size == 1 }) { "two GatewayFactory for one backend" }
     }
 
     private val mutableLive = MutableStateFlow<PersistentMap<ProfileId, ConnectionGraph>>(persistentMapOf())
@@ -59,21 +61,18 @@ public class ConnectionRegistry(
         admission.withLock {
             live.value[profile.id]?.let { return@withLock Outcome.Ok(it) }
             val factory =
-                factories.firstOrNull { it.backend == profile.backend }
+                factories.firstOrNull { it.kind == profile.backend }
                     ?: return@withLock Outcome.Failed(Failure.Unavailable(FeatureId("backend.${profile.backend}")))
-            when (val pair = factory.connect(profile)) {
-                is Outcome.Failed -> pair
-                is Outcome.Ok -> {
-                    val scope =
-                        ConnectionCoroutineScope(
-                            supervisedContext(appScope.coroutineContext.job, dispatchers.default, "conn-${profile.id.raw}"),
-                        )
-                    val graph = graphFactory.create(profile, pair.value, scope)
-                    graph.session.markConnected()
-                    mutableLive.update { it.putting(profile.id, graph) }
-                    Outcome.Ok(graph)
-                }
-            }
+            val scope =
+                ConnectionCoroutineScope(
+                    supervisedContext(appScope.coroutineContext.job, dispatchers.default, "conn-${profile.id.raw}"),
+                )
+            // Slice 0: no SecretStore yet, so no password (S1-DATA binds the CredentialReader adapter).
+            val pair = factory.open(GatewaySpec(profile, CredentialReader { Outcome.Ok(null) }), scope)
+            val graph = graphFactory.create(profile, pair, scope)
+            graph.session.markConnected()
+            mutableLive.update { it.putting(profile.id, graph) }
+            Outcome.Ok(graph)
         }
 
     /** The conversation's own backend. Null = that connection is not live. */
diff --git a/native/engine/connection/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionSession.kt b/native/engine/connection/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionSession.kt
index e5edb933..d4d677aa 100644
--- a/native/engine/connection/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionSession.kt
+++ b/native/engine/connection/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionSession.kt
@@ -18,7 +18,7 @@ public class ConnectionSession(
     public val profile: ServerProfile,
     private val gateway: ServerGateway,
 ) {
-    private val mutableStatus = MutableStateFlow(StreamStatus.Connecting)
+    private val mutableStatus = MutableStateFlow<StreamStatus>(StreamStatus.Connecting)
     public val status: StateFlow<StreamStatus> = mutableStatus.asStateFlow()
     public val capabilities: StateFlow<ServerCapabilities> get() = gateway.capabilities
 
@@ -27,7 +27,7 @@ public class ConnectionSession(
     }
 
     internal suspend fun close() {
-        mutableStatus.value = StreamStatus.Disconnected
+        mutableStatus.value = StreamStatus.Disconnected(attempt = 0, nextAttemptAtMs = null)
         gateway.close()
     }
 }
diff --git a/native/engine/connection/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionRegistryTest.kt b/native/engine/connection/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionRegistryTest.kt
index 648d05c8..20822960 100644
--- a/native/engine/connection/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionRegistryTest.kt
+++ b/native/engine/connection/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionRegistryTest.kt
@@ -52,7 +52,7 @@ class ConnectionRegistryTest {
         val first = assertIs<Outcome.Ok<ConnectionGraph>>(registry.open(profile)).value
         val second = assertIs<Outcome.Ok<ConnectionGraph>>(registry.open(profile)).value
         assertSame(first, second)
-        assertEquals(1, fake.connected.size)
+        assertEquals(1, fake.opened.size)
         assertEquals(StreamStatus.Connected, first.session.status.value)
 
         registry.close(profile.id)
diff --git a/native/quality/src/test/kotlin/io/github/eslamasabry/oc/quality/GraphSmokeTest.kt b/native/quality/src/test/kotlin/io/github/eslamasabry/oc/quality/GraphSmokeTest.kt
index 5fffaea4..bd048fda 100644
--- a/native/quality/src/test/kotlin/io/github/eslamasabry/oc/quality/GraphSmokeTest.kt
+++ b/native/quality/src/test/kotlin/io/github/eslamasabry/oc/quality/GraphSmokeTest.kt
@@ -50,7 +50,7 @@ import kotlin.test.assertTrue
  * Graph smoke (plan 03 §13.8), desktop half. The Android graph is checked by Metro at compile time in
  * :androidApp:assembleDebug; its Robolectric runtime half is an open item (P1 report).
  */
-private val fake = FakeGatewayFactory(BackendKind.OpenCode2, FakeServerGateway(listOf(Session(SessionId("s1"), "One", 1L))))
+private val fake = FakeGatewayFactory(BackendKind.OpenCode2, FakeServerGateway(listOf(Session(SessionId("s1"), "One"))))
 
 @OptIn(ExperimentalCoroutinesApi::class)
 class GraphSmokeTest {
```

## 8. Open problems

1. **`ModuleOwnersTest` already fails at `6627d320`.** `MODULE_OWNERS` lacks `:tools:arb2xml` and
   `:tools:flutter-migration-readers`, which came with the P5/P6 merges. The coordinator owns that file.
2. **Consumers do not compile until §7 is applied.** That includes `:engine:connection`, `:engine:transcript` (it
   compiles once connection does), `:quality`, `:androidApp` and `:desktopApp`.
3. **Persisted profile DTO.** Audit 03 MAJOR 5 is still open: `ServerProfile` is the connection shape. S1-DATA
   freezes the persisted DTO.
4. **`FormField`, `HintKind` and `NoticeKind`** are open/thin by design. Their owners (forms engine, `:protocol:oc2`
   adapter) pin the final vocabularies.
5. **iOS:** klibs compile on Linux by cross-compilation. Nothing was linked or run on an Apple host.
6. **Konsist K-P1/K-P2** (no `while`+`delay` outside `pace/`; `TimerSpec` constants in `*Timers.kt`) are not
   written. They belong in `:quality`, which the coordinator owns.
7. **The `Redactor` has no memo**, unlike KitRedact's 512-entry cache. It is unmeasured; add a memo if diagnostics
   profiling shows a cost.
