# Unit S1-DATA: `:core:data`

Date: 2026-11-01. Branch: `native/data-2026-11-01` (from `claude/native-data-layer-rewrite-lqfkb0` at `6627d320`).
Write set: `native/core/data/**` (new module) and this report. Nothing outside it was edited.

Inputs read: `native/AGENTS.md`, `docs/native/slice0/P1-skeleton.md`, `DRAFT-STATUS.md`, plan 01 (binding), plan 15
(row S1-DATA), plan 05 §0–§4, §7.3, §10, §11, plan 03 §10.5, §16.1, §17.3, and both Codex audits.

**Result:** the finish line of the S1-DATA row is met on this container. `KeyValueStore` over the adopted Flutter
prefs file (Long ints, both list encodings, the double and BigInteger prefixes, `DartJson`), the fixed `WriterLane`,
`ProfileScope` with its sweep, `SecretStore` (AES-256-GCM, Keystore on Android behind the `Aead` interface) and the
`MigrationGate` interface are green, including on the real-format fixture read by Android's own `SharedPreferencesImpl`
under Robolectric.

Shipping state: implemented, verified locally (JVM desktop tests + Android host tests), committed and pushed to the
branch. Not merged, no PR, no CI run. The Keystore path has not run on a device or emulator (none in this container).

---

## 1. What was built

Package root `io.github.eslamasabry.oc.core.data`. `explicitApi()` is on (core layer).

| Area | Files | What it does |
|---|---|---|
| Contracts | `kv/KeyValueStore.kt`, `kv/PrefValue.kt`, `kv/RawPref.kt` | `KeyValueStore` (`get`, `keys`, `changes`, `commit(lane, edit)`, `compareAndSet(lane, …)`), `PrefEdit` (no putInt/putFloat/putStringSet), typed read extensions `string/long/bool/double/stringList`, `PrefsBackend` (the platform file seam, physical keys, blocking). `PrefValue` and `RawPref` never print values in `toString()`. |
| Codec | `kv/FlutterPrefsCodec.kt` | Copied from `tools/flutter-migration-readers` and made multiplatform. Decodes `boolean`, `long`, legacy `int`, `float`, `<double>`, `<set>`, `<null>` (invisible), unknown tags, the JSON list (`…Qu!`), the legacy Java-serialised list (`…Qu`, Android Base64 port + injected `JavaListDecoder`), the double prefix and the BigInteger prefix. Encodes exactly what the plugin writes; refuses strings starting with any Flutter prefix. |
| Store | `kv/PrefsKeyValueStore.kt`, `kv/InMemoryPrefsBackend.kt` | Logical ↔ `flutter.` physical keys. An immutable snapshot of **acknowledged** values serves reads. Writes wait for `MigrationGate.awaitWritable()`, run in the caller's `WriterLane`, then take ONE file-wide lock that covers reading the previous values, the platform commit and the compensation. A refused or throwing commit writes the previous values back and returns `Failure.Storage`; readers and `changes` never see the refused value. `compareAndSet` runs under the same lock. `migrationWriter()` (opt-in `@MigrationOnly`) passes the gate and uses no lane but the same lock. |
| Lanes | `lane/WriterLane.kt` | `WriterLane` with admission and in-flight registration in one CAS on one state (audit fix, §4.1); `stopAdmission`, `resumeAdmission`, `drain`. `WriterLanes`/`DefaultWriterLanes`: app lane + per-profile lanes, closed lanes stay closed. |
| Gate | `migration/MigrationGate.kt` | `GateState` (Closed, Running, Open, Blocked), `BlockReason`, `MigrationNotices`, `MigrationGate` (`state`, `awaitWritable`), `StateMigrationGate` (publish is `@MigrationOnly`), `alwaysOpen()`. |
| Scope | `scope/ProfileScope.kt`, `scope/ProfileKeyRule.kt`, `scope/DefaultProfileScope.kt` | `KeyScope`, `KeyShape`, `OwnedData`, `SweepReceipt`; registration of key shapes, secret prefixes and tables; `scopedKeys` computed late over the live store; `ownedData` (keys + secrets by registered prefix + tables); `reservedSegments`; `sweep(profileId, retained)` in ONE commit, idempotent, with per-owner counts. `ProfileKeyRule` is a line-by-line port of Flutter `ProfileStore.profileScopedPreferenceKeys`. |
| Secrets | `secret/SecretStore.kt`, `secret/AeadSecretStore.kt` | `SecretStore` (read, write, delete, names), `Aead` and `SecretEntries` seams. Entry value `Base64(0x01 ‖ IV[12] ‖ ciphertext ‖ tag[16])`, AAD = entry name. Values are registered with the `Redactor` before any cipher or disk call and before a read returns; writes read back and compare (a mismatch or a throwing read-back deletes the entry); a value cache; one lock; no plaintext fallback. |
| DartJson | `dart/DartJson.kt` | `encode`, `decode`, `encodeComponent`, `iso8601Utc`, `sha256Hex` (pure-Kotlin SHA-256), `utf8` (lone surrogate → U+FFFD as Dart does), `dartDouble`. Marked `TODO(move-to-core)`. |
| Android | `androidMain/…/android/*.kt` | `SharedPreferencesBackend` (`commit()`, never `apply()`), `ObjectStreamJavaListDecoder` (the plugin's class allowlist), `KeystoreAead` (alias `oc.secrets.aesgcm.v1`, AES-256 GCM, no padding, no user auth, no StrongBox, Keystore-generated IV), `SharedPreferencesSecretEntries` (`oc_secrets_v1`), `AndroidDataStores` (`flutterPrefs`, `secrets`, `flutterCodec`, `namedPrefs`: the only `getSharedPreferences(` call sites in the module, Konsist K7). |

Tests: `commonTest` (7 files), `desktopTest` (6 files plus a test-only copy of the readers' `AndroidPrefsXml` parser),
`androidHostTest` (Robolectric, 1 file plus an access shim).

## 2. Catalog rows covered

Test names start with the row id (AGENTS rule 11). "Part" means the row also needs an engine, the migrator or a device.

| Row | Status | Tests |
|---|---|---|
| B-STATE-004 | covered at the store level | refused write keeps the old value, publishes nothing, next write works; multi-key rollback; CAS; 200-coroutine CAS + commit on 4 threads with refusals, no lost update, no overlapping platform commits |
| B-STATE-005 | covered at the store level | a throwing write restores the cached and the platform value; details carry the class name only |
| B-STATE-013 | covered (lane) | drain waits for a writer paused between admission and the lock; refused after `stopAdmission`; real-thread drain test |
| B-STATE-002 | part (drain, cancellation, reopen) | queued writes drained in FIFO order; a cancelled waiting writer does not hold drain; failed deletion reopens the lane |
| B-CHAT-051 | part (fence) | an admitted write lands before the sweep, a late one is refused, nothing is resurrected |
| B-STATE-060 | covered (sweep rule + store) | owner positions, `.agents` backend, longer dotted ids, drafts' owner last, other profile whole, idempotent, nothing back after restart; all 107 profile shapes of the Flutter ledger |
| B-SEC-039 | part (sweep side) | every ledger row classified with a reason; no app-wide key is ever swept for any id. "Every literal in source is classified" is `PrefsLedgerTest` in `:quality` (not this unit). |
| B-MIGR-005 | part (store level) | every encoding in the real-format fixture reads as Dart saw it (JVM parser and Android's own loader); native writes use Flutter's encodings (checked in the XML Android writes); untouched raw values stay byte-identical; prefix clash refused; writes wait for the gate, reads do not |
| B-MIGR-007 | part (store level) | per-profile keys of the fixture stay scoped; the sweep removes all of one profile and only it, on the JVM store and through `SharedPreferencesImpl` |
| B-MIGR-006 | part (secret store) | v1 format, AAD swap rejected (fake and real AES-GCM), wrong key unreadable, read-back mismatch deletes, secrets persisted in `oc_secrets_v1.xml` without plaintext |
| B-MIGR-011 | part | decoded values, raw values and failure details never carry values; every secret is registered with the Redactor before persistence or return |

## 3. Commands and results

Environment (done first): Android SDK command-line tools `commandlinetools-linux-16111833_latest.zip` from
dl.google.com into `/opt/android-sdk/cmdline-tools/latest`, `ANDROID_HOME=/opt/android-sdk`, licences accepted,
`sdkmanager "platform-tools" "platforms;android-37.1" "build-tools;37.0.0"` (what `build-logic` `Sdk.kt` needs:
compileSdk 37 minor 1). JDK 21.0.12 runs Gradle; `openjdk-17-jdk-headless` was installed for the build toolchain.
`native/local.properties` (gitignored) holds `sdk.dir`.

Maven Central answered **HTTP 429** through this sandbox's proxy (as in P1). A container-only init script
`~/.gradle/init.d/central-mirror.gradle.kts` (not in the repo) puts Google Maven and Google's Central mirror
(`maven-central.storage-download.googleapis.com/maven2`) first, and sets `robolectric.dependency.repo.url` to the same
mirror for test JVMs (Robolectric downloads its Android runtime jar at test time).

All from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`:

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :core:data:compileKotlinDesktop` | BUILD SUCCESSFUL |
| 2 | `./gradlew :core:data:desktopTest` | first run: 49 tests, 1 failed (a read-back that throws left the entry behind: a real bug, fixed in `AeadSecretStore`); then **49/49 passed** |
| 3 | `./gradlew :core:data:testAndroidHostTest` | first run: Robolectric could not fetch `android-all-instrumented` (429); after the mirror property, **43/43 passed** (40 common tests + 3 Robolectric tests on the real `SharedPreferencesImpl`) |
| 4 | Plan 03 §17.3 `WriterLane` swapped in temporarily, then `:core:data:desktopTest --tests '*WriterLaneTest*' --tests '*ConcurrencyTest*'` | **3 failed** (the paused-writer drain test, the B-CHAT-051 fence test and the real-thread drain test): the tests catch the audit race. The fixed lane restored: passed |
| 5 | `./gradlew :core:data:desktopTest :core:data:testAndroidHostTest :core:data:compileKotlinDesktop :core:data:compileAndroidMain` (final, after the last edit) | BUILD SUCCESSFUL |
| 6 | `./gradlew :quality:test` | 17 tests, 16 passed (every Konsist rule passes over the new module); **`ModuleOwnersTest` fails** because `MODULE_OWNERS` has no `:core:data` row (coordinator file, see §6) |
| 7 | `git grep` for RFC 1918 / CGNAT addresses and email addresses over `native/core/data` | no match |

Per test class (desktop / android host): AdoptedFixtureTest 3/–, ProfileSweepShapesTest 2/–, AeadSecretStoreTest 5/5,
PrefsKeyValueStoreTest 9/9, SoftwareAeadSecretStoreTest 2/–, DartJsonTest 6/6, ProfileScopeTest 5/5, WriterLaneTest
7/7, FlutterPrefsCodecTest 8/8, ConcurrencyTest 2/–, FlutterPrefsStoreAndroidTest –/3. Skipped: 0.

Not run: `build` (rule 12), iOS (`oc.ios=false`), any emulator or device test.

## 4. Resolved versions (from Gradle)

Kotlin 2.4.21 (stdlib), kotlinx-coroutines-core 1.11.0, kotlinx-collections-immutable 0.5.2, kotlinx-coroutines-test
1.11.0 and Turbine 1.2.1 (through `:core:testing`), JUnit 4.13.2, Robolectric 4.17 with
`android-all-instrumented` `15-robolectric-13954326-i7` (`@Config(sdk = [35])`), AGP 9.3.3, compileSdk 37.1. No new
library: everything comes from the existing catalog.

## 5. Decisions and deviations from the plan

### 5.1 Audit fixes applied

1. **WriterLane drain race (codex-01+03 BLOCKER 1).** Admission and registration are one compare-and-set on one
   immutable state `(admitting, inFlight tickets)`. `stopAdmission()` flips the flag on that state; `drain()` awaits
   every ticket registered before it, including writers still waiting for the mutex or paused before reaching it. A
   writer cancelled before running releases its ticket in `finally`. Regression tests pause a writer between admission
   and the lock through an internal seam (`afterAdmission`), and a real-thread test repeats the race 50 times.
2. **CAS not atomic against commits (codex-04+05 MAJOR 8).** Every mutation (commit, CAS, migration writes and the
   compensation after a refusal) holds one file-wide lock from reading the previous values through compensation.
   Tested with real threads and refusals (`ConcurrencyTest`).
3. **One contract for the storage seams (codex-01+03 BLOCKER 4).** Plan 05 §2.1's shape was chosen (synchronous
   in-memory reads, reads never gated, gated writes, `commit` + `compareAndSet`); plan 03 §10.5's typed reads exist as
   extension functions. CAS takes a `WriterLane` so per-profile CAS writers are fenced like every other writer.
4. **`MigrationActions` is not in `:core:data`** (codex-04+05 BLOCKER 3: `:ui:shell` may not depend on `:core:data`).
   Only `MigrationGate` + `GateState` live here; the action surface belongs to an engine-level API.

### 5.2 Other deviations

1. **`changes` is emitted by the store after the acknowledged commit**, not from an `OnSharedPreferenceChangeListener`
   (plan 05 §2.2). `SharedPreferencesImpl.commit()` notifies listeners even when the disk write failed, so a listener
   would publish refused values, against B-STATE-004. Consequence: a writer that bypasses the store is not observed;
   Konsist K7 already forbids such writers.
2. **The sweep follows the Dart code, not the plan's summary.** Plan 05 §3.4 calls "ends with `.<id>` or contains
   `.<id>.`" verbatim, but Flutter's `profileScopedPreferenceKeys` matches the id only at its owner position and has
   per-namespace rules (drafts owner last, genui item, nested stash/orchestration/provider-runtime, `.agents`, app-wide
   multi-segment namespaces, longer dotted ids). The summary would sweep keys whose item text equals the id and
   `oc.transcript.<id>`-like app-wide keys. `ProfileKeyRule` is the line-by-line port.
3. **Failure variants.** `:core:common` has no `StorageRefused`, `InvalidValue` or `SecretUnavailable`; all map to
   `Failure.Storage` with class-name-only details (`CommitRefused`, `PrefixClash`, `AEADBadTagException`, …).
4. **BigInteger prefix decodes to `Int64`** (what the plugin hands to Dart, as the P5 readers do), not `Str` as the
   plan 05 §2.2 table says; values that do not fit 64 bits are `Undecodable(BadBigInteger)`.
5. **`DartJson` lives in `:core:data`** (`TODO(move-to-core)`): plan 05 puts it in `:core:common`, owned by S0-CORE.
   Its tests use hand-derived vectors; the generated Dart golden vectors (plan 05 §2.5) do not exist yet.
6. **Registrations are plain `register(...)` calls**, not yet a Metro `@ContributesIntoSet` multibinding; no app-graph
   wiring was done (the graphs are outside this write set).
7. **`commitBlocking`** (plan 05 §2.2, for synchronous framework callbacks) is not built: a blocking bridge over the
   suspend lane needs `runBlocking`, which K16 bans outside `:core:common`. `KotlinPrefsFile` is only
   `AndroidDataStores.namedPrefs(context, file)`.
8. **The fixture is read in place** from `native/tools/flutter-migration-readers/src/test/resources/fixtures/synthetic-v1`
   instead of copied, so there is one drift-guarded copy (P5's `CommittedFixturesTest`). The ledger test reads the
   Flutter ledger `test/fixtures/coverage/settings_preferences_ledger.json` until the native ledger exists.
9. **Tests were written before running anything, but in the same sitting as the code**; the red evidence is the
   original-lane run (§3 row 4) and the read-back bug the first run found.

## 6. Catalog requests and requests to other owners

No library requests: the module uses only existing catalog entries (`junit4`, `robolectric`, plus the convention
plugin's set).

- **Coordinator:** add `:core:data	S	core-owner	slice-1` to `native/MODULE_OWNERS` (`ModuleOwnersTest` is red until
  then).
- **S0-CORE (`:core:common`):** add `Failure` variants (or a reason enum) for refused storage writes, invalid values and
  unavailable secrets if the words layer needs to tell them apart; take `DartJson` into
  `io.github.eslamasabry.oc.core.common.dart` and generate the Dart golden vectors.
- **`:quality` owner:** `PrefsLedgerTest` with the native ledger, and a Konsist rule restricting `@MigrationOnly`
  opt-ins to `:android:migration`.

## 7. Open problems

1. `KeystoreAead` has not run anywhere: Robolectric has no AndroidKeyStore. `KeystoreSecretStoreDeviceTest` on the
   `OC_API37` emulator is still needed (plan 05 §10.3).
2. The Flutter ledger test seeds `<prefix><id>` for every family. Under Flutter's own rule, `oc.draft.`,
   `oc.promptStash.`, `oc.orchestration.`, `oc.providerRuntimeRefresh.` and `oc.providerRuntimeUnloadable.` keys built
   that way are not owner-position keys, so that Dart test looks like it cannot pass at HEAD. The native port seeds the
   real shapes. Someone with a Flutter toolchain should confirm.
3. `DartJson.dartDouble` takes digits from the platform's `Double.toString()`; on JDK 17 that is not always the
   shortest representation [U]. `encodeDouble` (what the store writes) is unaffected on a device, because the plugin
   uses the same Android call.
4. Compensating a refused write cannot restore a previous `<double>` or unknown-tag value through `SharedPreferences.Editor`
   (the Editor cannot write those tags); the snapshot still shows the acknowledged value. No such value exists in the
   fixture.
5. iOS sources were not compiled (`oc.ios=false`); there is no iOS-specific code in the module.
6. `tool/format-changed.sh` (rule 13) does not exist in the repo yet; no formatter was run.
7. Container-only setup (mirror init script, Robolectric repo property) is needed again in a fresh container.
