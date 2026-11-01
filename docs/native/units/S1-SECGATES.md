# Unit S1-SECGATES: K-SEC rules and security gates (report)

| | |
|---|---|
| Unit | S1-SECGATES (plan 15 roadmap row; plan 16 §2, WP-16.2; plan 03 §19.5, §23) |
| Branch | `native/secgates-2026-11-01`, from `claude/native-secgates-b5n0c0` at `6b9a2b21` |
| Write set used | `native/quality/**`, `native/androidApp/lint.xml`, the lint part of build-logic (`LintPolicy.kt` and the one `lint { … }` line in each Android convention plugin), and this report |
| Finish line | K-SEC-1…12, the lint severity table, ExportedSurfaceTest, NetworkSecurityConfigTest, FileProviderPathsTest, TlsFloorTest, QaHooksAbsentTest and RepositoryHygieneTest each fail on a planted violation and pass on the live tree. Non-goal: new rules beyond plan 16 §2. |
| State | Implemented, verified in this container, committed, pushed. Not enabled in CI (no CI run asked for), nothing released. |

## 1. What was built

### `:quality:secTest`, a suite that compiles no project module

`native/quality/build.gradle.kts` registers a JVM test suite `secTest` (`./gradlew :quality:secTest`). It depends on
Konsist, `kotlin-test-junit5` and the Compose runtime (only because `:quality` applies the Compose compiler plugin),
and on **no project module**, so it runs while the rest of the tree is being reconciled. `check` depends on it. Its
inputs are the whole source tree, build scripts, build-logic, the catalog, `version.properties`, `androidApp/lint.xml`
and `quality/config/**`, so it reruns whenever any of them change. The existing `:quality:test` is untouched.

Sources (`native/quality/src/secTest/kotlin/io/github/eslamasabry/oc/quality/sec/`):

| File | What it is |
|---|---|
| `SecTree.kt` | the scanner: `.kt`/`.kts`/`.xml` of the product layers (`core`, `protocol`, `engine`, `ui`, `feature`, `android`, `androidApp`, `desktopApp`; not `tools`, `spikes`, `quality`, `e2e`); production = non-test, non-`qa` source sets; a comment stripper that keeps string literals and line numbers; `SecConfig` for the allowlists |
| `SecTextRules.kt` | K-SEC-1, 2, 3, 4 (`reveal()` half), 5 (call-site half), 6, 7, 8, 9, 10, 12, as pure functions of the scanned files |
| `SecDeclarationRules.kt` | Konsist rules: K-SEC-4 (secret-named String-like properties of data and `@Serializable` classes), K-SEC-5 (a `Secret` in a `NavKey`/`AppRoute` class), K-SEC-11 (analytics event constructor properties) |
| `SecGates.kt` | NetworkSecurityConfig, FileProviderPaths, ExportedSurface, TlsFloor, QaHooks, LintTable, and `Pending` (the shrink-only tolerance list) |
| `RepositoryHygiene.kt` | the tracked-tree checks and the leak gate |
| `KSecRulesTest`, `SecGatesTest`, `RepositoryHygieneTest` | the live tree |
| `KSecPlantedTest`, `SecGatesPlantedTest`, planted half of `RepositoryHygieneTest` | the P9-style proofs: the same functions on planted trees, XML, scripts and diffs |

Planted trees are temporary folders laid out like `native/`, read by the same loader and the same Konsist code as the
live tree. Konsist reads them with `scopeFromExternalDirectories`, one directory per source set.

### Configuration (`native/quality/config/sec/`)

`secret-dto-allowlist.txt` (empty), `secret-name-allowlist.txt` (`monitorToken`, with its reason),
`secret-reveal-allowlist.txt` (`Redactor.kt`, `SecretStore.kt`, `AeadSecretStore.kt`),
`mutable-pendingintent-allowlist.txt` (the three §1.8.2 files), `knownhosts-pending.txt` (`cdimage.ubuntu.com` until
`KnownHosts` exists), `exported-surface.txt` (the reviewed snapshot), `sec-pending.txt` and `ksec-pending.txt`
(shrink-only tolerance lists, below). `README.md` explains each.

**Pending lists.** A gate finding or rule hit in code this unit does not own is listed with its reason. A listed item
is tolerated; a listed item that no longer fires fails the test until it is removed, so the lists can only shrink
(plan 03's `freeze-pending` pattern):

| List | Entry | Owner, action |
|---|---|---|
| `sec-pending.txt` | `NSC-ABSENT` | `androidApp/src/main/res/xml/network_security_config.xml` does not exist yet (04 §4.7, 16 §1.7) |
| `sec-pending.txt` | `FP-ABSENT` | `androidApp/src/main/res/xml/local_file_paths.xml` arrives with the FileProvider slice |
| `sec-pending.txt` | `SURFACE-MAIN-TASKAFFINITY` | coordinator: `.MainActivity` lacks `android:taskAffinity=""` (16 §1.8.1) |
| `ksec-pending.txt` | `K-SEC-4 …/core/model/Host.kt ManagedShell.ownerToken` | core-owner: an ownership tag, not a credential, typed `String?` in a data class. Rename it (`ownerTag`) or add it to `secret-name-allowlist.txt` with a CR16 row |
| `knownhosts-pending.txt` | `cdimage.ubuntu.com` | `:android:linux` `BuiltinLinux.kt` rootfs URL; remove when `KnownHosts` (WP-16.1) lists it |

### Lint (build-logic and `androidApp/lint.xml`)

- `build-logic/convention/src/main/kotlin/oc/buildlogic/LintPolicy.kt`: `SECURITY_ERRORS` (the 30 ids of plan 16
  §2.2) and `Lint.ocLint(config)`: `abortOnError = true`, `error += SECURITY_ERRORS`, and the module's `lint.xml` as
  `lintConfig` when it has one. No baseline.
- `OcAndroidApplicationPlugin` and `OcAndroidLibraryPlugin`: their `lint { abortOnError = true }` line became
  `lint { ocLint(file("lint.xml")) }`. Nothing else in those files changed.
- `androidApp/lint.xml`: the same 30 ids at `severity="error"` and the single path suppression,
  `InsecureBaseConfiguration` for `src/main/res/xml/network_security_config.xml`, with the plan's reason text.
- `LintTableTest` checks both files against the plan table: no id missing or downgraded, no `id="all"`, no path
  ignore on a table id, the suppression exactly that one path with its reason, `abortOnError = true`, no baseline.

## 2. Rules and gates, and how each is proven

Every row has a live test (green on the tree at `6b9a2b21` plus this branch) and a planted test (red on the plant,
green on the near misses beside it).

| Rule / gate | Checks (plan 16 §2.1 / §2.3) | Planted violations that fail | Near misses that pass |
|---|---|---|---|
| K-SEC-1 | `Linkify`, `autoLinkMask`, `setAutoLinkMask`, `android:autoLink` (Kotlin and XML); `LocalUriHandler provides` outside `:ui:shell` `App.kt` | 4: `autoLinkMask`, `Linkify`, a feature `provides`, an XML `android:autoLink` | the shell `App.kt` provider; the word in a comment |
| K-SEC-2 | `X509(Extended)TrustManager`, `HostnameVerifier`, `.sslSocketFactory(`/`{`, `.hostnameVerifier(`/`{`, `SSLContext`/`TrustManagerFactory` `.init(` outside `:core:net-okhttp` | 3 in `:core:net` | `PinnedLeafTrustManager` in `:core:net-okhttp` |
| K-SEC-3 | `CertificatePinner`, `HttpLoggingInterceptor`, `install(Logging / HttpRequestRetry / HttpRedirect)`, the Ktor logging import | 6 | `install(HttpTimeout)` |
| K-SEC-4 | Konsist: secret-named `String`/`CharSequence`/`CharArray`/`ByteArray` (nullable or not) properties of data and `@Serializable` classes; exemptions by suffix (`Name`, `Id`, `Count`, `Rejected`, `Reentry`), name allowlist and DTO packages. Text: `.reveal()` only in allowlisted files | the plan's set: `Provider(val apiKey: String)`, `Login(val password: CharArray)`, plus `@Serializable Wire(val bearerToken: String?)`; a feature calling `reveal()` | `Usage(val tokens: Tokens)`, `Auth(val secretName: String)`, `Flags(val passwordRejected: Boolean)`, `monitorToken`, `credentialId`, `Secret`-typed fields, a non-data class; `reveal()` in `Redactor.kt` |
| K-SEC-5 | Konsist: a `Secret`-typed property in a `NavKey`/`AppRoute` class. Text: a `Secret` (or a `Secret`-typed name) passed to `rememberSaveable`, `rememberTextFieldState`, `workDataOf`, `putExtra`, `Bundle.put*` (also catches `Data.Builder().put*`), `SavedStateHandle` set | 7 | `remember { TextFieldState() }`, `putExtra("id", 3L)`, a route with only ids |
| K-SEC-6 | `ClipData.newPlainText`, `ClipEntry(` outside `:ui:kit` `KitCopy*`/`KitPlatform*` | 2 (feature, shell) | `KitCopy.android.kt` |
| K-SEC-7 | every `PendingIntent.get{Activity,Activities,Broadcast,Service,ForegroundService}(` in `:android:*`/`:androidApp` has `FLAG_IMMUTABLE` in its arguments, except the three allowlisted files | 2 | an immutable one; Termux `TermuxActivityCommands.kt`; a call outside the Android layer |
| K-SEC-8 | `Intent.parseUri`, `Intent.getIntent(`, and `startActivity(`/`startActivities(` of an intent from `getParcelable(Extra)` (directly or through a local) in `:android:*`/`:androidApp`, except `PackageInstallerUpdater.kt` | 4 | `startActivity(Intent(this, MainActivity::class.java))`; the allowlisted updater |
| K-SEC-9 | `WebView(` outside `KitWebPreview.kt`; `addJavascriptInterface`, file access true, universal file access, `MIXED_CONTENT_ALWAYS_ALLOW`, `handler.proceed(` | 6 | `chain.proceed(request)`, `allowFileAccess = false`, `WebView(` in `KitWebPreview.kt` |
| K-SEC-10 | every `"http(s)://` / `"ws(s)://` literal in production names a host from `KnownHosts.kt` (wildcards `*.x`) or `knownhosts-pending.txt`, or loopback; templated hosts skipped | 2 unknown hosts | `KnownHosts` hosts and a `*.hf.co` subdomain, `127.0.0.1`, `localhost`, `http://$host`, `src/qa/`, `*Fixtures.kt`, test sets |
| K-SEC-11 | Konsist: primary-constructor properties of `AnalyticsEvent` implementors are enums (declared in scope), `Boolean`, `CountBucket`, `DurationBucket`, `VersionCode` | 3 (`String`, `Int`, `List`) | enum, Boolean, the three bucket types; an inherited `name` getter |
| K-SEC-12 | `kotlin.random.Random`/`java.util.Random` (import or qualified) in `:core:security`, `:engine:profiles`, `:engine:media` `share/`, `:engine:diagnostics` `analytics/`, `:engine:analytics`, `:android:linux`, `:android:platform` | 3 | `:core:net` backoff jitter; `:engine:media` outside `share/` |
| NetworkSecurityConfigTest | exactly one `<base-config cleartextTrafficPermitted="true">`, optionally holding only `<certificateTransparency enabled="true"/>`; nothing else; no DTD; the manifest points at the file | 7 XML plants (user trust, `@raw` CA, domain-config + pin-set, CT disabled, extra attribute, debug-overrides, DOCTYPE entity) and a manifest without `networkSecurityConfig` | the plan's file, with and without the CT opt-in |
| FileProviderPathsTest | exactly the three roots of §1.8.3 | 6 (`root-path`, `files-path .`, `cache-path /`, a missing root, `external-files-path`, a duplicate) | the plan's file |
| ExportedSurfaceTest | the source manifest's canonical component lines equal `exported-surface.txt`; §1.8.1 invariants: explicit `exported` with filters, exported only for the listed names / widget providers / system-permission-guarded components, no exported provider, `.MainActivity` `singleTop` + `taskAffinity=""`, `allowBackup="false"`, no `debuggable`, no `QUERY_ALL_PACKAGES`, the FileProvider shape | 9 invariant plants plus a new intent filter that is not in the snapshot | a §1.8.1-shaped manifest; an unchanged manifest |
| TlsFloorTest | catalog `okhttp` and every `okhttp3` library version ≥ 5.5.1 (pre-releases below), literal coordinates and `strictly/prefer/require` constraints in build scripts ≥ 5.5.1, no `.pem/.crt/.cer/.der/.bks/.p12/.pfx/.jks/.keystore` in a product module's `src/` | 5.5.0, 5.5.1-alpha1, no okhttp version, an `okhttp3` library on another ref at 4.12.0; a 4.12.0 coordinate and a `strictly("5.4.0")`; a `.pem` in `res/raw` and a `.bks` in resources | 5.5.1, 5.6.0, a 5.5.1 coordinate, a `.p12` in a spike's tests |
| QaHooksAbsentTest | no `src/qa` wired into a non-qa source set (block or same-line `getByName`), no production code naming a class declared in a `src/qa/` set (plus `QaHooksFileReader`, `EgressRecorder`, `QaFixtureAnalyticsSender`), no `qa-hooks.json` or `10.0.2.2` in production, no `android:debuggable="true"`, no `-qa` versionName | `src/qa` into `main` and into `release`; 2 qa classes, 2 markers, `3.0.0-qa` | `src/qa` into `stableQa` (block and same line); markers inside `src/qa/` |
| LintTableTest | §1 "Lint" above | 7 `lint.xml` plants and 3 convention plants | the full table |
| RepositoryHygieneTest | tracked tree: no `*.jks`, `*.keystore`, `*.p12`, `key.properties`, `.claude/skills/`; ignore rules; governance files; non-affiliation statement in `README.md`, `SECURITY.md`, `SUPPORT.md` | 5 tracked paths, 2 missing ignores, 2 entry points without the statement | `native/README.md`; the statement split by Markdown quote marks |
| Leak gate | added lines of `-Poc.leakBase..HEAD`: no personal mail address, no 100.64/10 address except `100.64.0.10`, no deny-list term; `native/e2e/fixtures/` exempt; author and committer = the public noreply address | a mail address, a tailnet address and a deny-listed name in added lines; a non-noreply identity | the documentation address; context (unchanged) lines; a fixture file |

**Mutation check on the live path.** With two throwaway files in the real tree (`data class ZzPlanted(val apiKey:
String)` plus a `Linkify` use in `:core:model`, and `data class ZzEvent(val host: String) : AnalyticsEvent` in
`:engine:analytics`), `:quality:secTest` failed exactly K-SEC-1, K-SEC-4 and K-SEC-11 at those lines. The files were
removed, and the suite was green again. A non-vacuity test also asserts the live Konsist scope sees more than 20 data
classes, more than 5 analytics events and the `android/` layer.

## 3. Catalog row ids

| Row | Test |
|---|---|
| B-SEC-150 | `RepositoryHygieneTest` › `B-SEC-150 no keystores, signing secrets or third-party skill packs are committed` and its planted twin |
| B-BUILD-030 (non-affiliation and governance part only) | `RepositoryHygieneTest` › `B-BUILD-030 …` (two tests). The notice-inventory part stays with the release unit. |

The K-SEC and gate tests are named after their rule or gate (`K-SEC-7 …`, `ExportedSurfaceTest - …`), as `:quality`'s
existing `K1 …` tests are (infrastructure tests, exempt from 03 K12). See open problem 1 for the other ids the
roadmap row names.

## 4. Commands and results (this container, 2026-11-01)

Setup: Android command-line tools 16111833 in `/opt/android-sdk` with `platform-tools`, `platforms;android-37.1`,
`build-tools;37.0.0`; JDK 21; `native/local.properties` (`sdk.dir`, git-ignored). Maven Central answered HTTP 429 to
this container, so a local, uncommitted Gradle init script (`~/.gradle/init.d/central-mirror.gradle.kts`) replaces
`repo.maven.apache.org` with Google's Maven Central mirror. Nothing in the repository depends on it.

| Command | Result |
|---|---|
| `./gradlew :quality:secTest` (before `lint.xml`, `LintPolicy.kt`, snapshot and pending lists existed) | red as expected: 7 of 54 failed (missing files, the `ownerToken` hit, a templated `$okhttp` version the TLS check misread, fixed) |
| `./gradlew :quality:secTest` (final) | **BUILD SUCCESSFUL, 55 tests, 0 failed, 0 skipped**: `KSecRulesTest` 14, `KSecPlantedTest` 13, `SecGatesTest` 8, `SecGatesPlantedTest` 14, `RepositoryHygieneTest` 6 |
| same, with the two throwaway files in the live tree | failed exactly K-SEC-1, K-SEC-4, K-SEC-11 (files removed afterwards) |
| `./gradlew -p build-logic test` | BUILD SUCCESSFUL (`ModuleRulesTest` unchanged and green with the `LintPolicy` change) |
| `./gradlew :quality:secTest -Poc.leakBase=claude/native-secgates-b5n0c0` | first run **failed**: the gate found the range notation `100.64/10` in two lines of its own source (fixed by writing `100.64/10`). Rerun: BUILD SUCCESSFUL, 55 tests, 0 failed; it reported "deny-list missing" and 10 non-noreply identity fields (5 commits × author and committer), which fail only with `-Poc.leakMode=release` |
| `./gradlew :androidApp:lintPreviewRelease` | **not a result for this unit**: configuration with `ocLint` succeeded, then compilation stopped in `:engine:connection` (`ConnectionRegistry.kt`, `ConnectionSession.kt`: the core contract being reconciled). So no real lint run proves the table yet. The planted `lint.xml`/convention tests prove the table; a lint run with a planted `TrustAllX509TrustManager` is owed once `:androidApp` compiles (open problem 7) |

`:quality:test` (the existing Konsist and graph tests) was not run: it compiles `:desktopApp` and most modules, which
the brief says may not compile during the parallel reconciliation.

## 5. Deviations

1. **Config folder.** Plan 16 names the allowlists under `native/config/`. That folder is outside this unit's write set,
   so they live in `native/quality/config/sec/`. Move them when the coordinator wants them in `native/config/`.
2. **Source-level gates instead of build-logic tasks.** Plan 16 §2.3 makes `verifyExportedSurface`,
   `verifyOkHttpFloor` and `verifyQaHooksAbsent` build-logic verification tasks on `:androidApp` (merged manifest,
   resolved `stableReleaseRuntimeClasspath`, release APK). The brief names them `ExportedSurfaceTest`, `TlsFloorTest`
   and `QaHooksAbsentTest`, and this unit owns only the lint part of build-logic. So they are `:quality` tests on the
   source manifest, the catalog and build scripts, and the source tree. The merged-manifest, resolved-graph and APK
   forms are still owed (open problem 2). There is no `stableRelease` variant yet (only `preview`).
3. **K-SEC-12 scope** adds `:engine:analytics` whole. Plan 18 placed analytics in `:engine:analytics`, not
   `:engine:diagnostics` `analytics/` as plan 16 says. Both paths are covered.
4. **Scope of "production" and "everywhere".** Product layers only: `tools/`, `spikes/`, `quality/`, `e2e/` are not
   scanned. `:tools:flutter-migration-readers` has an emulator fixture URL, and `spikes/netp` has its own trust
   managers. Neither ships. If the migration readers are later bundled into the app, add `tools/` for them.
5. **K-SEC-5 call sites and K-SEC-8 relaunches are token checks, not type resolution.** They catch a `Secret`
   constructor or any name declared `: Secret` in the same file, and an intent from `getParcelable(Extra)` used
   directly or through a local. A `Secret` passed through another file's helper is not seen. The Konsist route rule is
   exact.
6. **K-SEC-7** accepts `FLAG_IMMUTABLE` only inside the call's own arguments, as the plan words it. Flags kept in a
   variable fail on purpose.
7. **No `tool/format-changed.sh`** exists in `native/tool/` yet, so formatting was by hand (ktfmt not run).
8. **LegacyReplyAction.kt** in `mutable-pendingintent-allowlist.txt` is a placeholder name for the legacy
   `RemoteInput` reply, which is not built yet. Correct the name when it lands.

## 6. Catalog requests

None. The suite uses only catalog entries that already exist (`konsist`, `compose-runtime`) and `kotlin-test-junit5`,
which the Kotlin plugin versions.

## 7. Open problems

1. **Catalog id collision (needs the catalog owner).** The roadmap row and plan 16 §2.3 cite B-SEC-193 (TLS floor and
   NSC), B-SEC-194 (exported surface), B-SEC-195 (FileProvider) and B-PROTO-139 (NSC elements). In
   `native/catalog/behaviors.jsonl`, B-SEC-193 is the Termux loopback receiver's 403, B-PROTO-139 is the OpenCode 2
   identity rules, and B-SEC-194/195 do not exist. So the gate tests are not named after those ids. The catalog
   needs new rows for plan 16's gates, or plan 16 needs renumbering.
2. **The build-logic task forms (build-logic owner + `:androidApp`).** `verifyExportedSurface` (merged manifest, library
   components such as WorkManager's `SystemJobService`), `verifyOkHttpFloor` (resolved
   `stableReleaseRuntimeClasspath`) and `verifyQaHooksAbsent` (dexdump/aapt2 on the stable release APK, with the
   planted `stableRelease`-with-`src/qa` build) need a `stable` flavour and the AGP Artifacts API in
   `OcAndroidApplicationPlugin`. The pure checkers here (`ExportedSurface.render/invariants`, `TlsFloor.compare`,
   `QaHooks.qaClassNames`) can be reused there.
3. **Reported live findings (owners).** `ManagedShell.ownerToken` (core-owner, K-SEC-4); `.MainActivity`
   `taskAffinity=""` (coordinator); `network_security_config.xml` and `local_file_paths.xml` absent (androidApp
   owner, FileProvider slice); `cdimage.ubuntu.com` waiting for `KnownHosts` (WP-16.1).
4. **Not in this unit, still owed by WP-16.2:** `KnownHostsDocTest`, `PrivacyCoverageTest`, `SecRowCoverageTest`,
   `CanaryLeakTest`, `tool/release/leak_gate.sh` (the script form of the leak gate; the Kotlin gate here is
   `-Poc.leakBase`), `verifyAnalyticsEndpoint`, the `NoTraceReadTest` rule of §1.10, and K-SEC-13 (git/guest script
   injection, slice 3). The roadmap row also lists `CanaryLeakTest`; it scans T2 test reports, which do not exist
   in a single-module run.
5. **Leak gate identities.** Commits made in these cloud sessions carry the Anthropic noreply address as author and committer,
   not the owner's noreply address. On a work branch the gate reports them; with `-Poc.leakMode=release` it fails
   them. The coordinator rewrites identities at merge, or the gate's identity list gains this address by decision.
6. **`native/config/private-denylist.txt`** (git-ignored) does not exist in this container, so the deny-list half
   of the leak gate was not exercised on the real list. The planted test covers the logic.
7. **A real lint run.** Once `:androidApp` compiles, run `:androidApp:lintPreviewRelease` with a throwaway
   trust-all `X509TrustManager` in `androidApp/src/main` and confirm it fails as `TrustAllX509TrustManager` (error),
   then remove the file.
