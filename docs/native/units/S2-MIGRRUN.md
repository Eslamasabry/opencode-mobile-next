# Unit S2-MIGRRUN: `:android:migration` (the day-1 migration runner)

Date: 2026-11-01. Branch: `native/migrrun-2026-11-01` (from `claude/native-migrrun-2026-11-01-q9qlkg` at `6b9a2b21`).
Write set: `native/android/migration/**` (new module) and this report. Nothing outside it was edited.

Inputs read: `native/AGENTS.md`, `docs/native/slice0/P5-readers.md` (and the other slice-0 and unit reports, S1-DATA in
detail), plan 01 (§6.1, §9.7, §9.8, §14.1 Astra B2/B3/M1), plan 15 (row S2-MIGRRUN), plan 05 (all), plan 02 §3.7, plan 16
§5.3, and the Dart sources the transforms port (`lib/state/profiles.dart`, `notification_preferences.dart`,
`read_state_consent.dart`, `prompt_photos.dart`, `profiles/models.dart`, `builtin_server_owner.dart`) plus the moved
Kotlin `NativeRecoveryBudget.kt`.

**Finish line (plan 15 row):** met on this container at the JVM/Robolectric level. The journal, S0 backup, M1 census,
M2 secrets (with the durable credential ledger), M3 prefs adoption, M4 restore check, M5 audit and M6 complete pass on
the P5 fixture, through plain JVM files and through Android's own `SharedPreferencesImpl` (Robolectric). `StepInterrupter`
exists in the `qa` build type only, and a kill at every step and every S0 filesystem point resumes to the same end
state. The census records AI Team, Memory and Gemma data for S2-GATEUI's notice; A2A bearers are counted, never copied;
the runner emits `app_updated` and `migration_done`.

Shipping state: implemented, verified locally (host tests on the `qa` variant, lint), committed and pushed to the branch.
Not merged, no PR, no CI run. Not wired into `:androidApp` (not in this write set) and not run on an emulator.

---

## 1. What was built

Package root `io.github.eslamasabry.oc.android.migration`. Dependencies: `:core:common`, `:core:security` (Redactor).

| Area | Files | What it does |
|---|---|---|
| Readers (ported from P5) | `readers/{xml,json,prefs,secure,migration}/*` | Copied from `native/tools/flutter-migration-readers` with `internal` visibility: Android-exact prefs XML parser (`.bak` rule, loads-empty detection), Dart-compatible `MiniJson`, `FlutterPrefsCodec` (+ `clashes`), `ImagePickerCache`, `FlutterSecureStorageReader` (FSS 11.2.0), `Fs` (atomic writes, durable copies, `FaultPoints`). The P5 fixture writer and software unwrapper are test-only copies. |
| Paths, ports, state | `MigrationPaths.kt`, `Ports.kt`, `RunnerState.kt` | Every path under the data dir. Ports: `FlutterPrefsFile` (physical keys, `commit()`), `KotlinPrefsSource`, `MigrationSecretSink` (the migrator's ungated secret handle), `MigrationSignals` (`app_updated`, `migration_done`, the M5 diagnostics line; enums and buckets only). `RunnerState` = the gate states of 05 §7.3 with `BackupInvalid`, `restoreAvailable`, notices by kind and `DeferredFeature`. |
| Journal | `MigrationJournal.kt` | `files/native/migration.json` in the 05 §7.2 shape, kept as an ordered map so unknown fields survive; atomic save (tmp, fsync, rename, dir fsync). Unreadable or newer-schema journals are never overwritten. |
| StepInterrupter | `StepInterrupter.kt`, `src/qa/…/QaStepInterrupter.kt`, `src/{qa,debug,release}/…/StepInterrupters.kt` | The crash seam. Points: `S0`, `M1`, `M2:1`, `M2:all`, `M3`, `M4`, `M5`, `M6`, `barrier:journal` and every S0 filesystem point. Only the `qa` build type reads `files/native/qa-kill-after` (deleted before the kill), `qa-delay-ms` and `qa-free-bytes`; debug and release return the no-op. |
| S0 | `steps/S0Backup.kt` | Byte backup of the six Flutter stores (each with its `.bak` twin, marked authoritative) into `flutter-v57.partial/`, manifest, atomic rename; a published dir is verified and adopted, never copied onto; a failing one is `BackupInvalid`; `LowSpace` before anything is touched (16 MiB margin). |
| M1 | `steps/M1Census.kt`, `steps/Prefs.kt` | Integrity (own parse of the backup copy vs the live map → `PrefsUnreadable`), the hashed baseline (`m1-baseline.bin`, deleted when v1 completes), codec census, strays, picker lost result, profiles (invalid ids, reserved collisions, the L4 OC1 flags counted with zero writes), bounded directory inventory (`m1-inventory.json`, names and sizes), interrupted `.pending` writes, AI Team / Memory / Gemma presence, A2A key counts. |
| Barrier | `MigrationRunner.initBarrier` | `oc.native.flutterReadable`: journal first, then the pref (read back), right after M1 and before any write; `false` wins and is never raised again (M6 writes the journal's value). |
| M2 + ledger | `steps/M2Secrets.kt`, `ledger/CredentialLedger.kt`, `ledger/LedgeredSecretWriter.kt` | Decrypt FSS, register every value (and every 8+ char JSON string field) with the Redactor first, then `alreadyMigrated` / `nativeNewer` (kept) / write-and-verify. Kinds per Appendix B: typed password and token, GitHub sign-in, BYO setup are flagged in `oc.secretReentry.<id>` and listed; app-made (`pw` of the phone server, agent host, quota, team engine) are `renewedByOwner`, never listed; FactsBook copied; A2A `countedOnly`. The ledger (`credential-ledger.json`, names/revs/states only) records the FSS ciphertext digest of each migrated name; any record suppresses the 3-launch retry; `secretChanges()` gives engines the invalidate-first writer (ledger → `oc.native.staleLegacySecrets` → store). |
| M3 | `steps/M3LegacyTransforms.kt` | L1 retire, L2 notification merge (pure `NotifyMerge` port, active server first, sources untouched), L3 read-state consent (Dart journal rules, fail closed on an invalid journal), L4b picker-only photo recovery into `oc.pendingPromptPhoto` with every other field byte-identical. One atomic commit per transform; a refused commit is `WriteRefused`. |
| M4 | `steps/M4RestoreAuthority.kt` | The Flutter half (recovery, automation, phone-agent owner, owner pointer) must equal the baseline (`AuthorityChanged` otherwise); the predicate mirrors `NativeRecoveryBudget.migrationAllows`/`policyAllows` (strict integer `version`); the Kotlin half (`restoreOwner`, recipe `packageVersion` ≤ native code and `rootfsGeneration` match) is counted, never written, never blocks. |
| M5 | `steps/M5Audit.kt` | `migration-audit.json` (counts and digests only) and the `unchangedKeysDigest` proof: any undeclared key that changed since M1 → `AuthorityChanged` at M5. One diagnostics line, counts only. |
| M6 + runner | `MigrationRunner.kt` | FSS snapshot (ciphertext digests), journal complete, barrier commit, `gate.firstOpenAt`, open. Step protocol: `attempts+1, running` journaled before the body; `done` with counts after; 3 attempts → `StepCrashed`. `run()` is single-flight; `retry()` while running awaits the same run, from `Blocked` resets unfinished steps. `acknowledgeNotices()` hides the sheet and the deferred-feature notice for good. `app_updated` on every new versionCode (null from Flutter), `migration_done` once. |
| Android | `android/SharedPreferencesFlutterPrefs.kt`, `AndroidKeystoreUnwrapper.kt`, `AndroidMigration.kt` | `commit()` adapter opened lazily (after S0); read-only Kotlin prefs; the plugin-exact RSA-OAEP unwrap that never creates the alias; the factory `:androidApp` will call (StatFs free space, `StepInterrupters.forBuild`). |

## 2. Catalog rows covered

Tests are named after the rows (AGENTS rule 11). "Part" = the row also needs an engine, `:androidApp` or a device.

| Row | Status | Tests (class) |
|---|---|---|
| B-MIGR-001 | covered (L2) | 5 cases ported from `notification_preferences_test.dart` (`LegacyTransformsTest`) |
| B-MIGR-002 | part (census) | OC1 flags counted by the Dart read rule, `oc.profiles` byte-identical (`MigrationRunnerTest` B-MIGR-005, audit counts) |
| B-MIGR-003 | covered (L3) | legacy value once, resume from an incomplete journal, never rerun (`LegacyTransformsTest`) |
| B-MIGR-004 | part | fresh install is a clean first run (`MigrationRunnerTest`); the in-place install itself is J1 |
| B-MIGR-005 | covered at runner level | every undeclared key byte-identical (queue, double-prefixed and unknown keys); an undeclared change blocks M5 (`MigrationRunnerTest`, `RestoreAuthorityCheckTest`); fixture via `SharedPreferencesImpl` (`AndroidPrefsMigrationTest`) |
| B-MIGR-006 | covered at runner level | all kinds, `nativeNewer`/`alreadyMigrated`, v9 markers, `_BACKUP`, prefix match, Keystore failure, retries and ledger suppression (`SecretsMigrationTest`); the real Keystore is device work |
| B-MIGR-007 | part | per-profile keys untouched and still profile-scoped (digest); the sweep is `:core:data`/`:engine:profiles` |
| B-MIGR-008, 009 | part | files are only inventoried, never moved (`m1-inventory.json`, AI Team dir untouched); "starts without re-download" is J1 |
| B-MIGR-010 | covered at runner level | kill at every step and S0 point, StepCrashed + Try again, single-flight retry, `.bak`, BackupInvalid, LowSpace, unreadable journal, CorruptPrefsXml (`MigrationRunnerTest`, `AndroidPrefsMigrationTest`, `QaStepInterrupterTest`) |
| B-MIGR-011 | covered at runner level | planted secrets, prompt text and addresses absent from journal, audit, inventory, ledger, snapshot, events and the diagnostics line; event fields are enums; Redactor registration incl. JSON fields (`MigrationAuditPrivacyTest`, `SecretsMigrationTest`) |
| B-MIGR-013 | part | M3 never touches schedules, widgets, shortcuts or live mode (digest); re-arming is H2 |
| B-SEC-063 | covered (migration side) | A2A bearers counted, not copied, FSS entry untouched (`SecretsMigrationTest`) |
| B-SEC-142 | covered (L3) | invalid legacy or journal never grants sharing (`LegacyTransformsTest`) |
| B-STATE-081, 068 | covered (L1) | `LegacyTransformsTest` |
| B-STATE-088 | covered (L4b, migration side) | recovered path/name byte-exact, error → failed, no file → unchanged, finished record untouched (`LegacyTransformsTest`) |
| B-LINUX-032 | part | predicate vectors, armed count, Kotlin half recorded never blocked, a changed authority key blocks (`RestoreAuthorityCheckTest`); `RestoreAuthorityParityTest` belongs to `:androidApp` |

## 3. Commands and results

Environment: Android command-line tools `commandlinetools-linux-16111833_latest.zip` from dl.google.com in
`/opt/android-sdk/cmdline-tools/latest`, `ANDROID_HOME=/opt/android-sdk`, licences accepted, `platform-tools`,
`platforms;android-37.1`, `build-tools;37.0.0`; `native/local.properties` (gitignored); JDK 21 runs Gradle, JDK 17
installed for the toolchain. Container-only `~/.gradle/init.d/central-mirror.gradle.kts` (not in the repo) puts
Google's Maven Central mirror first (plus the plugin portal and Google) and sets `robolectric.dependency.repo.url`.

All from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`:

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :core:data:compileAndroidMain :core:data:compileKotlinDesktop` | **FAILED**: `PrefsKeyValueStore.kt:190` passes `Redacted` where `:core:common` now expects `StorageProblem` (the parallel core reconciliation). Hence §5.1. `:core:common`, `:core:model`, `:core:security` compile. |
| 2 | `./gradlew :android:migration:compileDebugKotlin :android:migration:compileQaKotlin` | BUILD SUCCESSFUL (after making `FaultPoints` public) |
| 3 | `./gradlew :android:migration:testDebugUnitTest --tests '*MigrationRunnerTest*'` | first run 14 tests, 2 failed: the "changes no byte" checks caught the designed `secretRetries.remaining` countdown on relaunch; tests now exhaust the three retries before the byte check. Then 14/14 |
| 4 | `./gradlew :android:migration:testQaUnitTest` (after `testBuildType = "qa"`) | first: compile error (`;` in a test name); then **57/57 passed** incl. 2 Robolectric (SDK 35) |
| 5 | Mutation check: removed the ledger check in `retryFlagged`, the M5 digest comparison, and the `BackupInvalid` return; `testQaUnitTest` | **3 failed, exactly the expected tests** (Astra B3 ledger, M5 AuthorityChanged, tampered backup); code restored |
| 6 | `./gradlew :android:migration:testQaUnitTest :android:migration:compileDebugKotlin :android:migration:compileReleaseKotlin --rerun-tasks` | BUILD SUCCESSFUL, **59/59**, zero compiler warnings |
| 7 | `./gradlew :android:migration:lintQa` | first: 1 error (literal BOM in the copied parser → `﻿`); then BUILD SUCCESSFUL |
| 8 | `git grep`-style scan of `git diff 6b9a2b21..HEAD` for RFC 1918 / CGNAT / link-local addresses and e-mail addresses | no match; no file outside `native/android/migration/` (and this report) |

Per class (qa host tests): LegacyTransformsTest 14, SecretsMigrationTest 14, MigrationRunnerTest 16,
MigrationAuditPrivacyTest 6, RestoreAuthorityCheckTest 5, QaStepInterrupterTest 2, AndroidPrefsMigrationTest 2
(Robolectric). Skipped 0.

Not run: `:quality:test` (needs the whole graph, and `:core:data` does not compile today), `build` (rule 12), any
emulator or device test, J1–J8.

## 4. Resolved versions

Kotlin 2.4.21, AGP 9.3.3, compileSdk 37.1, minSdk 26, kotlinx-coroutines 1.11.0 (+ `-test`), JUnit 4.13.2,
Robolectric 4.17 (`@Config(sdk = [35])`). No new library.

## 5. Deviations and decisions

### 5.1 Contract seams (because `:core:data` does not compile today)

1. **No `:core:data` / `:core:model` edge yet.** The module reaches the prefs file, the secret store, the gate and
   analytics through its own ports (`FlutterPrefsFile`, `MigrationSecretSink`, `KotlinPrefsSource`, `MigrationSignals`,
   `RunnerState`). `:androidApp` adapts them. When the core contract settles, `RunnerState` maps onto `GateState`.
2. **Writes go through `SharedPreferences.Editor.commit()` directly**, not `PrefsKeyValueStore.migrationWriter()`, so
   they do not share the store's file lock (05 §2.2, audit 04+05 #8). This is safe only while nothing else writes the
   file before the gate opens; plan 05 §7.1 step 6 "warm the prefs file" is read-only. TODO in `Ports.kt`.
3. **`RunnerState` carries what `:core:data`'s `GateState` lacks:** `BlockReason.BackupInvalid`, `restoreAvailable`,
   `ReentryNotice(profileId, kind)` and `deferredFeatures` (S2-GATEUI's notice).

### 5.2 Behaviour choices

1. **Step states** are `running → done` (05 §7.11's `committed`/`verified` merged; each step verifies inside its body:
   M3 reads back, S0 verifies the manifest, M4/M5 recompute). A done step is not re-verified on later launches.
2. **One atomic commit per transform** (L1, L2, L3, L4b) instead of Dart's key-by-key writes: same end state, never a
   half-applied transform.
3. **Built-in phone server** (for M2's app-made `pw.<id>`) = the `oc.builtinServerOwner` id or any id with an
   `oc.builtinRecovery.<id>` key [U: confirm against the Dart model; `ServerProfile` has no built-in flag].
4. **Flags only for listed servers:** a failed typed/sign-in/setup secret of an orphan id is counted (`failedUnlisted`),
   never listed (no server to name).
5. **The M2 retry** reads its names from the `oc.secretReentry.*` records (no secret names in the journal) and stops
   after three launches; `secretRetries.remaining` is decremented before the retry runs.
6. **M4 "armed"** also requires the owner pointer to equal the profile id (05 says only "matches the regex").
7. **M1 inventory** counts `files/linux` and `files/projects` at depth 1 only, and caps every walk at 20,000 files
   (`truncated` flag), to stay inside the first-launch budget. Reserved segments are a fixed list until the ledger
   exists.
8. **`migration_done`** follows 16 §5.3: `Failed` is emitted once when the runner blocks (05 M5 says "Recovered at the
   next Open"); `Done`/`ReentryNeeded` at the first Open. `app_updated` has `fromCode = null` after Flutter (its code is
   not readable after `adb install -r`). Both are handed to the port at once; `:androidApp` queues until analytics run.
9. **The baseline** keeps its 05 name `m1-baseline.bin` but holds JSON (key hash → declared flag, value hash).
10. **`testBuildType = "qa"`:** AGP 9 creates host tests for the tested build type only, so `testQaUnitTest` is the
    module's test task (it compiles main + qa).
11. **Fixture read in place** from `native/tools/flutter-migration-readers/src/test/resources/fixtures/synthetic-v1`
    (one drift-guarded copy, as S1-DATA did).
12. **Tests were written with the code in one sitting**, not strictly red first; §3 row 5 is the red evidence
    (mutations caught).

### 5.3 Not built here (plan 15 non-goals, or other owners)

R1 `R1.return`, R2 and Start fresh (S6), `reportProblem()`, `MigrationFollowUpWorker`, the StartupFence and its
app-locale mirror (`:androidApp`), first-launch hooks H1–H3, the deferred-process stop of 02 §3.7 item 2 (S3-RTWIRE
stops, the audit line is S6), `SyntheticFlutterSecretsTest` (device).

## 6. Catalog requests and requests to other owners

**Catalog requests:** none (only `junit4`, `robolectric`, `kotlinx-coroutines-test`, already in the catalog).

- **Coordinator:** add `:android:migration	S	migration-owner	slice-2` to `native/MODULE_OWNERS` (`ModuleOwnersTest`).
  `:androidApp` needs a `qa` build type (or `matchingFallbacks`) to consume this module's `qa` variant.
- **Core owner (`:core:data` / `:core:model`):** `BlockReason.BackupInvalid`; `Blocked.restoreAvailable`;
  `ReentryNotice(profileId?, kind)` instead of `List<ProfileId>`; a deferred-feature notice set; a raw physical read plus
  `migrationWriter()` that the migrator can use so it shares the file lock (§5.1 item 2).
- **`:androidApp`:** bind `AndroidMigration.create(...)` as the gate; adapt `SecretStore` → `MigrationSecretSink`
  (raw, ungated); route engine secret changes through `runner.secretChanges()`; call `run()` from the StartupFence
  thread; queue `MigrationSignals` until `:engine:analytics`/`:engine:diagnostics` run; `RestoreAuthorityParityTest`
  against `NativeRecoveryBudget` with the vectors in `RestoreAuthorityCheckTest`.
- **S2-GATEUI:** read `RunnerState.Open.notices` (`reenter`, `deferredFeatures`) and call `acknowledgeNotices()` once.
- **`:quality`:** allow `getSharedPreferences(` in `:android:migration` (Konsist K7 already lists it).

## 7. Open problems

1. `AndroidKeystoreUnwrapper` has not run anywhere (no emulator here); `SyntheticFlutterSecretsTest` on `OC_API37` is
   next, then J1/J2 on a real Flutter install (P5 open risks 1–5 still apply).
2. The P5 fixture holds placeholder JSON for many keys (`oc.activeProfile`, `oc.builtinServerOwner`, …), so M3/M4 on it
   exercise the fail-closed paths; the real-install fixture (05 §10.4) is needed for meaningful L2/L3/L4b/M4 counts.
3. §5.1 item 2 (shared file lock) must be closed before any other writer can run before the gate opens.
4. `:quality:test` and the Konsist rules were not run over this module (whole-graph build is red today).
5. `tool/format-changed.sh` does not exist; no formatter was run.
6. The bounded inventory walk may mark large trees `truncated`; the v3 cleanup needs the real-install numbers.
