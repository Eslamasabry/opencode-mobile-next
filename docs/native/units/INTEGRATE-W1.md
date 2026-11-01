# INTEGRATE-W1 — the merged M0 tree compiles on one core contract

Date: 2026-11-01. Branch: `native/integrate-2026-11-01`, created from `claude/native-integrate-2026-11-01-ab413u` at
`6b9a2b21` (the M0 tip with the 11 unit merges). Write set: anything under `native/` needed to make the merged tree
compile, preferring consumers over core; this report.

**Result:** the merged tree compiles and every test task passes. `:core:*` is the single source of truth for the
contract types: the stand-in `Failure` variants, `StreamState`, `NetworkState`/`NetworkStatus`, `DartJson`, the tolerant
JSON helpers and the oc2 `GatewayEvent` copies are gone or moved into core, and no `TODO(move-to-core)` is left. Core
changed only where plans 01/03/04 say S0-CORE's shape is wrong (§4).

Shipping state: implemented, verified locally (commands in §3), committed and pushed to the branch. No PR, no CI run,
no signing, no release. `master` and tags untouched.

---

## 1. What was built

| Area | Change |
|---|---|
| `:core:common` `Failure` | Plan 03 §10.1's **one flat, closed list** with plan 04 §3.8's detail fields (`Network(NetworkWhy)`, `Certificate(CertificateWhy)`, `SignIn(SignInWhy)`, `Busy(BusyKind, tag)`, `NotFound`/`Conflict(tag, requestId)`, `Server`/`Rejected(status, tag, …)`, `Incompatible(IncompatibleKind)`), plan 05's `StorageRefused`/`SecretUnavailable`, plan 10's `Media(MediaProblem)`, plan 11's `Runtime(RuntimeProblem)`. S0-CORE's nested `Failure.Net` family and `Storage(StorageProblem)` are deleted. `RefusalReason` = plan 03's list (incl. `OriginChanged`, `CredentialsInUrl`, `HostNotAllowedForBackend`, `BackgroundLimit`, `NotificationsBlocked`, `PhoneLocked`, `UndoUnresolved`) + `InvalidAddress`. |
| `:core:common` `json/`, `dart/` | `TolerantJson`, `RawObjectSerializer`, `SurrogateSerializer`, `WireEnum`, `OpenEnumSerializer` moved from `:protocol:oc2` (plan 04 §2.2). `DartJson` moved from `:core:data` unchanged (plan 05 §2.5), plus `sha256Hex(ByteArray)`. |
| `:core:model` | `NetworkStatus` interface and `UnknownNetworkStatus` moved here from `:core:domain`; `NetworkState` gains `onWifi` (plan 04 §6.4) and defaults for every field but `online`. |
| `:core:domain` | `GatewayFactory { backend; probe; connect(spec): Outcome<GatewayPair> }`, `GatewaySpec(profile, credentials, scope, location, probe)`, `ConnectionCredentials` (resolved, redacting `toString`). `CredentialReader` deleted. `platform/Network.kt` deleted (`NetworkGate` is `:core:net`'s). `GatewayEvent` gains `SessionCompacted`, `RunOutcome.Idle`, `SessionChanged.projectId/subpath/replacesSubpath`; `Notice.messageId`, `TerminalChanged.ptyId`, `ShellChanged.shellId` are nullable. |
| `:core:testing` | `FakeGatewayFactory` on the new factory (`opened`, `probed`, `failNextConnect`). `FakeNetworkGate` deleted (`:core:testing` may not see `:core:net`; tests build the real gate over `FakeNetworkStatus`). |
| `:core:net` | `StreamState` deleted; `StreamSignal.Status(StreamStatus)` carries core's type; the connect generation is the count of `Connected` signals (plan 04 §6.2), and `Frame` still carries it. Own `NetworkState`/`NetworkStatus`/`Online`/`Transport` deleted. `StatusMapping`/`TransportFailures` map onto the flat `Failure` per plan 04 §3.8 (401 → `SignIn(PasswordRejected | PasswordMissing)`, 403 → `SignIn(Forbidden)`, 429 → `Busy(RateLimited)`, 503 → `Busy(Overloaded)`, connect failure → `Network(Refused)`, other read errors → `Network(Reset)`). |
| `:core:data` | `StorageRefused` for refused commits and prefix clashes; `SecretUnavailable` for every `SecretStore` failure. |
| `:engine:connection` | `ConnectionRegistry.open` creates the graph scope, calls `factory.connect(spec)`, cancels the scope when the connect fails; `ConnectionSession` uses sealed `StreamStatus`. |
| `:engine:analytics` | `ErrorCategory` = one value per flat subtype; `Failure.SecretUnavailable` reports as `KeystoreUnavailable` (the leak detector rightly refuses the word "secret" on the wire). |
| `:protocol:oc2` | The adapter's vocabulary is `internal` and `Oc2`-prefixed (rules untouched); new `Oc2CoreProjection` is the one place it becomes core's `GatewayEvent` (§4, D10). Depends on `:core:model`/`:core:domain` (ADR §6.1 allows it). |
| `:ui:kit-chat`, shell/quality tests | Follow the `:ui:design` and kit APIs that moved under them before this merge (`motion.MotionTokens.QUICK/STANDARD`, `KitTheme(appearance, platform, localeTag)`, `DesktopKitPlatform`). |
| `MODULE_OWNERS` | Rows for `:core:data`, `:core:net`, `:protocol:oc2`, `:engine:analytics`, `:android:linux`, `:tools:strings`, `:tools:flutter-migration-readers` (owners as their units asked). |
| `build-logic` `ModuleRules` | `analytics` second in the engine order (ADR §6.2), with a rule test; the credential KDoc follows plan 03 §10.7. |
| `:quality` Konsist | `production` now scans `protocol/` and `src/jvmShared/` (S1-OC2 and S1-NET requests). It found one K11 text hit (`System.serializer()` of the wire variant `Oc2Message.System`), fixed in oc2. |
| `:tools:flutter-migration-readers` | The ledger test's repo root was `native/../../..` (= `/home`); now `native/..`. |
| `native/contracts/s1.md` | Describes the reconciled contract. |

## 2. Catalog rows and tests

New or rewritten tests (written red first against the new contract; the first red state was a compile failure):

| Row / contract | Test |
|---|---|
| B-PROTO-015 | `:protocol:oc2` `Oc2CoreProjectionTest`: the prompt round-trip reaches core as typed ids, parts and deltas; a failed run carries its type as `WireTag` and server words only as redacted `details` (a registered fake key never survives); idle is `RunEnded(Idle)`; hints keep their wire family; session-level notices have no message id. |
| B-PROTO-019 | `Oc2CoreProjectionTest`: permission asked maps like Dart `mapApi2PermissionRequest` (`source.id` → call id). |
| B-CHAT-005 | `Oc2CoreProjectionTest`: a settled lost result reaches core as a tool that `ran`, and the chat rereads. |
| plan 03 §10.1 contract | `:core:common` `CommonTest` › `Failure is one flat closed list…` (an exhaustive `when` with no `else`). |
| plan 04 §11.1 contract | `:engine:connection` `ConnectionRegistryTest` › `a failed connect cancels its scope and leaves nothing live`; the idempotence test now also checks the spec's scope is the graph's. |
| ADR §6.2 | build-logic `ModuleRulesTest` › `analytics sits right after settings`. |
| B-ANLY-004 | Unchanged test, now green with the new `ErrorCategory` values. |

Every other row test of the merged units ran unchanged in substance (only constructor shapes changed), e.g. B-PROTO-129/132/133 in `:core:net`, B-STATE-004 in `:core:data`, B-SEC-119/153 in `:core:security`, N4-D10 in `EventStreamLoopTest` (now counting `Connected` signals).

## 3. Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` (JDK 17 installed for the toolchain),
`ANDROID_HOME=/opt/android-sdk` (cmdline-tools `commandlinetools-linux-16111833_latest.zip`; `platform-tools`,
`platforms;android-37.1`, `build-tools;37.0.0`). Container: 4 vCPUs, 15 GB RAM; `~/.gradle/gradle.properties` raised
to `-Xmx6g`, 4 workers.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew compileKotlinDesktop --continue` (baseline, before any edit) | FAILED: `:core:data`, `:core:net`, `:engine:analytics`, `:engine:connection`, `:ui:kit-chat` (`MotionTokens`) |
| 2 | `./gradlew compileKotlinDesktop --continue` (after the core + consumer commits) | BUILD SUCCESSFUL |
| 3 | `./gradlew desktopTest :quality:test --continue` | first: B-ANLY-004, shell/quality test compile (kit API), `ModuleOwnersTest` red → fixed; then only `ModuleOwnersTest` → fixed |
| 4 | `./gradlew -p build-logic test` | 20 tests, 0 failures |
| 5 | `./gradlew :quality:test` (Konsist with `protocol` + `jvmShared`) | 31 tests; K11 red on `Oc2Message` → fixed → 0 failures |
| 6 | **Final, on the committed tree:** `./gradlew --no-configuration-cache -Pkotlin.incremental=false :androidApp:assemblePreviewDebug compileKotlinDesktop :desktopApp:compileKotlin allTests :quality:test --continue` | **BUILD SUCCESSFUL.** Preview APK 17.7 MB (`androidApp-preview-debug.apk`). **466 tests, 0 failures**: core/common 18+18 (desktop+Android host), core/data 43+37, core/net 41+35, core/security 20, core/domain 12, core/model 4, engine/analytics 49, engine/connection 3, engine/transcript 19, protocol/oc2 51, feature/chats 1, ui/design 9, ui/kit 7+23, ui/kit-chat 4, ui/routes 1, ui/shell 22+14, ui/words 4, quality 31 (Konsist, module owners, graph smoke, shell UI, P3 stream). |
| 7 | `./gradlew :android:linux:testDebugUnitTest :tools:strings:test :tools:flutter-migration-readers:test --continue` | android/linux 491, tools/strings 60 pass; readers 2 red (ledger path) → fixed → 84 pass |
| 8 | `./gradlew -Pkotlin.incremental=false -Poc.ios=true --continue :core:{common,model,security,domain,testing}:compileKotlinIosArm64 :core:{common,domain}:compileKotlinIosSimulatorArm64` | BUILD SUCCESSFUL in 54 s (the moved `json/` and `dart/` code compiles for Kotlin/Native; nothing linked or run on an Apple host) |

Every commit before #6 was also checked with the focused module tasks. The Kotlin incremental cache failed once
("FqNames can't be derived from DirtyData", as S0-CORE saw); `-Pkotlin.incremental=false` cleared it.

Container-only setup (not committed): `~/.gradle/init.d/central-mirror.gradle.kts` puts Google's Maven Central mirror
first (Central answers 429 through the proxy) **and** sets `robolectric.dependency.repo.url` on every `Test` task —
without the latter the Android host tests fail with "Failed to fetch maven artifact android-all-instrumented".

## 4. API decisions (every one)

| # | Decision | Why |
|---|---|---|
| D1 | `Failure` is plan 03 §10.1's flat list; `Failure.Net`, `Storage(StorageProblem)` and `StorageProblem` are deleted. | Plan 03 §10.1 ("no nested sub-hierarchy") and plan 04 §3.8 ("this file does not add a Net sub-hierarchy") both say so; S0-CORE's D-C1 read an earlier draft. Every consumer (net, data, analytics) was already coded against the flat shape. |
| D2 | Plan 04 §3.8 mappings: delivery unknown = `Refused(DispatchUncertain)`, stale answer = `Refused(StaleScope)`, offline = `Network(Offline)`, timeouts = `TimedOut`, latched optional features = `Unavailable(FeatureId)` (no `NotSupported`). | The plan's input table. |
| D3 | Plan 05's `InvalidValue` (prefix clash) is `StorageRefused`. | Not in plan 03's closed list; it is a refused write. |
| D4 | `RefusalReason.CredentialsInAddress` → `CredentialsInUrl` (plan name); S0-CORE's `InvalidAddress` kept (AddressRules needs it; appended). | Plan 03 name; additive. |
| D5 | `WireExtras` stays S0-CORE's symbolic map (plan 04 sketches `None | MaxBytes`). | Equivalent and more general; not contradicted by 03. |
| D6 | `GatewayFactory.backend` + `connect(spec): Outcome<GatewayPair>`; `probe` keeps `Outcome<ProbeResult>` (rule 4). The scope travels in `GatewaySpec`. | Plan 03 §10.4 and plan 04 §11.1 (S0-CORE's `kind`/`open` read an earlier draft). Paseo/Codex connect does I/O and can fail. |
| D7 | Credentials arrive resolved as `ConnectionCredentials(username, secret)`; `CredentialReader` deleted. `ProbeTarget(origin, credentials?)`. | Plan 03 §0.3 and §10.7: "no reader seam is added". `permit`/`reconnectAllowed`/`serverRunsTools`/`options` are appended by S1-SECNET and the connection engine when their types exist. Until the `ProfileSource` seam lands, the registry passes `ConnectionCredentials.None`. |
| D8 | `NetworkStatus`/`UnknownNetworkStatus` live in `:core:model`; the gate is `:core:net`'s `NetworkGate` class; `:core:domain`'s `NetworkGate`/`OpenNetworkGate` and `:core:testing`'s `FakeNetworkGate` are deleted. | Plan 04 §6.4 homes them so; `:core:net` may not depend on `:core:domain` (ADR §6.1), so a domain interface could never be implemented by net (S0-CORE D-C4 contradiction). |
| D9 | `:core:net` keeps only `StreamSignal { Status(StreamStatus), Frame(frame, generation) }`. | Core's sealed `StreamStatus` is plan 04 §6.3's; the generation is "the number of Status(Connected) signals" (§6.2). `Frame.generation` stays so a consumer can drop a frame from a replaced connection. |
| D10 | `:protocol:oc2` keeps its adapter rules and its OC2-shaped intermediate types, now `internal` and `Oc2`-prefixed; `Oc2CoreProjection` is the only exit and emits core's `GatewayEvent`. | The rules stay a reviewable line-for-line port of Dart `Api2EventAdapter` (plan 15: Opus reviews it), while nothing outside the module can see a second event vocabulary. The detail core does not carry (revert payload, permission reply, inbox delivery, worktree vs workspace) is all refetch-or-words detail, listed in the KDoc. |
| D11 | Core `GatewayEvent` additions: `SessionCompacted`, `RunOutcome.Idle` (both in plan 04 §11.2; S0-CORE omitted them), and defaulted `SessionChanged.projectId/subpath/replacesSubpath` (Dart moves carry them). | Plan text, or additive with defaults. |
| D12 | `Notice.messageId`, `TerminalChanged.ptyId`, `ShellChanged.shellId` nullable. | **Deviation from plan 04 §11.2** (non-null there): OC2 sends `session.shell.*`, `session.skill.activated` and managed-shell events without those ids; null means "reconcile the list". |
| D13 | Tool error text goes to `ToolState.output` when the status is `Error`; a run failure's type becomes `RunOutcome.Failed.tag`, its message `details`; `MessageErrorKind` stays null. | Plan 09 §2.2 reads `declined` from `output`; `fromName` belongs to S2-MODEL. |
| D14 | `ErrorCategory.KeystoreUnavailable` for `Failure.SecretUnavailable`. | Analytics' own leak detector (B-ANLY-004) refuses "secret" in wire values. |
| D15 | `DartJson` and the tolerant JSON machinery live in `:core:common` (`dart/`, `json/`); oc2's raw/surrogate serial names are `wire.raw.oc2.<Name>` / `oc2.<Name>`. | Plan 05 §2.5 and plan 04 §2.2 homes; one copy for Paseo/Codex later. |
| D16 | Engine order: `settings, analytics, diagnostics, …`. | ADR §6.2 (binding) lists analytics second. |
| D17 | Shell and quality test harnesses use the kit's own `DesktopKitPlatform` instead of hand stubs. | The stubs drifted from `KitPlatform`; the real desktop platform tracks it. |

## 5. Deviations from the brief

- **`:engine:transcript` keeps its P3 copies** of `GatewayEvent`/`MessageInfo`/`Part` (`state/Wire.kt`). They are not
  marked `TODO(move-to-core)`; the file says slice 2 swaps the import, and the reducer is the chat lead's single-owner
  unit. Swapping it means changing reducer code (String ids → value-class ids, `Notice` shape), which is S2 work, not
  integration. Listed in §8.
- **`ServerOrigin`/`ApprovedLink` stay in `:core:model`**, and the two extra `ModuleRules` edges stay. Plan 03 §0.3
  wants them in `:core:common` in slice 1; it is not needed to compile and touches every `ServerOrigin` user, so it is
  left for a dedicated change (KDoc in `ModuleRules` now says so).
- Build-logic and `MODULE_OWNERS` were edited (coordinator files) because the brief's "make the module rules pass"
  needs them; both edits are what the unit reports asked for.

## 6. Catalog requests

No library was added. Still open from the merged units (not done here, because they need the catalog owner):
`ktor-client-core/okhttp/mock` aliases (S1-NET), `commons-compress` and `json` (S1-LINUX), `androidx-test-ext/runner`
library aliases.

## 7. Requests to other owners

- **S2-MODEL / S2-TRANSCRIPT:** swap `:engine:transcript` onto core's `GatewayEvent` and `core.model.transcript.*`
  (delete `state/Wire.kt`); handle `RunOutcome.Idle` (never replaces Failed/Interrupted) and nullable `Notice.messageId`.
  Port `MessageErrorKind.fromName` so `Oc2CoreProjection` can fill `kind`.
- **Connection engine:** the `ProfileSource` seam (plan 04 §12.1) so `ConnectionRegistry` stops passing
  `ConnectionCredentials.None`; append `permit`, `reconnectAllowed`, `serverRunsTools`, `options` to `GatewaySpec`.
- **S1-SECNET:** `TransportPermit` in `:core:security`; add `NetworkGate`'s `changes` flow if the plan's interface
  shape is kept.
- **Core owner:** plan 03 §10.1 byte primitives (`expect class Sha256`, `hmacSha256`, `secureRandomBytes`,
  `inflateRaw`) are still missing; `DartJson.sha256Hex` is a pure-Kotlin stand-in and analytics' `Identity` waits
  for `secureRandomBytes` (`TODO(core-bytes)`).
- **`:ui:words`:** the words layer needs one sentence per flat `Failure` subtype and per `MediaProblem`,
  `RuntimeProblem`, `NetworkWhy`, `SignInWhy`, `BusyKind`, `IncompatibleKind` value (no Kotlin mapping exists yet).

## 8. Open problems

1. `:engine:transcript` duplicates (§5) — the last copies of core types in the tree.
2. `ServerOrigin`/`ApprovedLink` move and the two `ModuleRules` edges (§5).
3. `ConversationRef` lacks plan 07's `backend: ConversationBackend` field (not needed to compile; route owner).
4. `StatusMapping` sees only the status: 503 is `Busy(Overloaded)` and 409 is `Conflict` until the OC2 gateway refines
   them from `_tag` (`service_starting` → `Busy(Starting)`, `SessionBusyError` → `Busy(SessionBusy)`).
5. The OC2 `EventStreamLoop` still stops on 401/403 (`Stopped`); plan 04 §4.8 wants the OC2 stream to keep its
   backoff after a 401 and only Paseo/Codex to stop. Net owner's call; unchanged here.
6. `Oc2CoreProjection` sets `PermissionRequest.canAlwaysAllow = true` (Dart's default; OC2 has no grantable flag).
7. Konsist K-P1/K-P2 (pace rules) are still unwritten (S0-CORE open item 6).
8. `:android:linux` is still not a dependency of `:androidApp` (S1-LINUX P1, FQCN clashes).
9. iOS: klibs compile (§3 #8); `:core:net`, `:core:data` and `:protocol:oc2` were not compiled for iOS.
10. Live-server checks were not run (no OpenCode 2 server in this container; AGENTS forbids the owner's servers).
