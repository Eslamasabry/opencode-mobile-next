# Unit S1-LINUX: the built-in Linux Kotlin copied into `:android:linux`

Date: 2026-11-01. Branch: `native/linux-2026-11-01`, cut from `claude/native-linux-module-setup-xjlasj` at `6627d320`.
Inputs: `native/AGENTS.md`, `docs/native/slice0/P1-skeleton.md`, plan 01 (binding), plan 15 row S1-LINUX, plan 11 §0.2
S11-1, §2, §4, plan 03 (android library recipe), plan 02 §5.6 (twins), plan 14 §2.9, map `builtin-linux.md` §7, §14,
§15, and both audit files (neither has a finding about this unit).

**Shipping state:** implemented and verified on this container (compile plus JVM tests). Committed and pushed to the
branch. No PR, no CI, no emulator run, no release.

**Finish line (plan 15):** "Every built-in Linux Kotlin file is copied unchanged into `:android:linux`, and its 30 JVM
tests and 11 instrumented acceptance classes pass with zero edits."
- Met for the copy and the JVM tests: 71 main files and 34 test files are byte-identical to their sources (`cmp`). All
  491 tests pass, with zero edits to any copied file.
- Not met for the instrumented classes. The brief said to list them, not run them: there is no emulator. They compile.
  §5 lists them.
- `LocalTerminal` (and `PtyAccess.java`) are not copied. They cannot compile here unchanged (§4, deviation D1).

---

## 1. What was built

All new files are inside the write set: `native/android/linux/**` and new rows in `native/TWINS.md`.

| Path | What it holds |
|---|---|
| `native/android/linux/build.gradle.kts` | `oc.android.library`. It sets `BuildConfig.BUILTIN_RUNTIME_QA` from `-PocBuiltinRuntimeQa` (default false, as in the Flutter app) and adds `src/shims/kotlin` to `main`. Dependencies: `commons-compress`, `androidx.core:core-ktx` (catalog), `junit4` (catalog) and `org.json` (tests). |
| `src/main/kotlin/io/github/eslamasabry/opencode_mobile/` (71 files) | Copies, unchanged, in the original package. |
| `src/main/res/values{,-ar}/strings.xml` | Verbatim subsets of the app's `strings.xml`. They hold only the 20 strings and 2 plurals the copies read, with the same names. |
| `src/shims/kotlin/io/github/eslamasabry/opencode_mobile/` (6 files) | The only native-authored Kotlin. See §3. |
| `src/test/kotlin/…` (34 files) | Copies, unchanged. |
| `src/androidTest/{kotlin,java}/…` (9 files) | Copies, unchanged: 7 `Builtin*Acceptance`, `RuntimeQaGuard.java`, and `BuiltinInstallerProducerQaService`. Plan 11 §4.1 moves the QA service from `main` to the test source set. |
| `native/android/linux/README.md` | Copy base, layout, shim rule and checks (plan 11 §2.4 rule 1). |
| `native/TWINS.md` | New file. It has a header and 116 rows (114 code files + 2 resource files). Each row gives the native path, the source path, the last commit that changed the source, the same commit as "last synced", and the owner "S runtime". |

### 1.1 Copied main files (71)

- **Plan 11 §4.1 runtime:** `BuiltinLinux`, `NativeRuntimeOwnership`, `NativeInstaller{Ownership,Admission,Visibility}`,
  `NativeProcRace`, `NativeProcScan`, `NativeProcessTree`, `NativeProcessList`, `NativeOrphanReaper`,
  `NativeComponentUpdateRecovery`, `NativeRecoveryBudget`, `RestartBackoff`, `NativeServerRecipe`,
  `NativeServerRestoreEvent`, `IdleStopPolicy`, `NativeIdle{State,Heartbeat,ReturnNotification}`, `WorkLeases`,
  `ServiceDiagnostics`, `ServiceNoticeGate`, `SetupStepRetry`, `SetupScriptCompletion`, `SetupWorkScopes`,
  `ChildProcessLimit`, `NativeIdleTimer`, `NativeIdleReturnNotificationHost`, `NativeWorkLeaseHost`,
  `NativeOrphanReaperHost`, `SetupRunner`, `SetupJob`, `SetupPersistence`, `BuiltinProjectStorage`, `BuiltinGitConfig`,
  `AndroidGroupNames`, `ScheduledPromptPhoneLease`, `AgentPriority`.
- **Agent hosting (moves to `:android:agenthost` in slice 3):** `PhoneAgentHost`, `PhoneAgentChecks`,
  `PhoneAgentPaths`, `PhoneAgentClaudeHome`, `PhoneAgentSignIn`, `PhoneAgentInventory`, `PhoneAgentCheckOutput`, the
  12 `PhoneAgentAuth*` files, `NativeAgentHostAdmission` and `PaseoStaleLock`.
- **Phone-engine seam (compile only; moves to `:android:team` later):** `PhoneEngineNative`, `PhoneEngineAttestation`
  and `NativeEngineReadiness`.
- **Services and status row (slices 1–3):** `BuiltinServerService`, `SetupService`, `BuiltinRestoreReceiver` and
  `StatusNotification{,Model,Renderer}`.
- **Closure (pure, no Flutter imports):** `NativeStrings`, `CodingAlertRequest` and `ScheduledPromptPolicy`. The status
  row, setup service and phone lease call them. Plan 11 §4.1 does not list them, but they are needed to compile.

### 1.2 Copied JVM tests (34 files, 36 classes, 491 `@Test`)

These are the 30 classes in map §7: `BuiltinGitObjectsTest`, `IdleStopPolicyTest`, `NativeAgentHostAdmissionTest`,
`NativeComponentUpdateRecoveryTest`, `NativeEngineReadinessTest`, `NativeIdleHeartbeatTest`,
`NativeIdleReceiverDispatchTest`, `NativeIdleReturnNotificationTest`, `NativeIdleStateTest`, `NativeIdleTimerTest`,
`NativeInstallerAdmissionTest`, `NativeInstallerOwnershipTest`, `NativeInstallerVisibilityTest`,
`NativeOrphanReaperTest`, `NativeProcessListTest`, `NativeProcessTreeTest`, `NativeProcRaceTest`,
`NativeRecoveryBudgetTest`, `NativeRuntimeOwnershipTest`, `NativeServerRecipeTest`, `NativeServerRestoreEventTest`,
`NativeWorkLeaseHostTest`, `PaseoStaleLockTest`, `PhoneAgentClaudeHomeTest`, `RestartBackoffTest`,
`ServiceDiagnosticsTest`, `SetupScriptCompletionTest`, `SetupStepRetryTest`, `SetupWorkScopesTest` and
`WorkLeasesTest`.

Four more test files moved with their subjects ("each moves with its subject", plan 11 §4.3): `AgentPriorityTest`,
`ChildProcessLimitTest`, `ServiceNoticeGateTest` and `StatusNotificationModelTest`. The last file also holds
`AlertButtonsTest` and `AlertCopyTest`.

None of these tests needs Robolectric. They run on the plain JVM with `org.json` on the test classpath.

## 2. Catalog rows covered

**No row ids.** No row in `catalog/behaviors.jsonl` cites a Kotlin test as its source: every `B-LINUX-*` row (251)
comes from a Dart test. Plan 14 §2.9 says the mover adds the row id to the front of a Kotlin test's name when that
test proves a row. Renaming a test is an edit to a copied file, which this unit forbids, so the mapping is left to
S3-RTSPLIT or the test-writer lane (open problem P4).

There was no tests-first step to do. The tests are the Flutter app's existing JVM tests, and they were copied before
the build file and shims that make them run (commit order: sources → build and shims → tests).

## 3. Shims (package-level, native-authored)

Each file is in `src/shims/kotlin/io/github/eslamasabry/opencode_mobile/` and carries a TODO that names the unit that
removes it.

| Shim | Why it exists | What it does |
|---|---|---|
| `R` | The copies use `R.string/plurals/mipmap` in the app package. This library's `R` is generated as `io.github.eslamasabry.oc.android.linux.R`. | Forwards each of the 22 names to the module `R`. `mipmap.ic_launcher` → `android.R.mipmap.sym_def_app_icon`, because the launcher icon belongs to `:androidApp`. |
| `BuildConfig` | `BuiltinLinux` and `BuiltinServerService` read `BuildConfig.BUILTIN_RUNTIME_QA` in the app package. | Forwards to the module's generated `BuildConfig`. |
| `MainActivity` | Launch intents name `MainActivity::class.java` and `MainActivity.EXTRA_LAUNCH_ACTION`. The Flutter class is a `FlutterActivity`. | An empty `Activity` with the frozen constant `"oc.shortcut"`. |
| `StatusActionReceiver` | The status row's Stop actions target it. The Flutter class forwards over a `MethodChannel`. | A no-op `BroadcastReceiver`. |
| `AppLifecycle` | `BuiltinLinux` records running services through it. The Flutter object imports `io.flutter`. | The Flutter bodies of `previousServices` and `recordServices`, verbatim: prefs file `oc_lifecycle`, key `running_services`, `commit()`. Behaviour is unchanged. |
| `LocalTerminal` | `BuiltinLinux` asks it about live or unconfined sessions, lists, stops and removes sessions, and reports terminal work leases. | The same signatures, and it never holds a session. `start(...)` throws `UnsupportedOperationException`. |

## 4. Deviations from the plan

- **D1. `LocalTerminal.kt` and `PtyAccess.java` are not copied.** Plan 11 §4.1 says "copy, then move in slice 5".
  `LocalTerminal` imports `io.flutter.plugin.common.*`, which plan 11 §2.4 rule 5 bans in `native/`. It also needs
  `com.github.termux.termux-app:terminal-emulator`, which `ExternalRules` allows only in `:android:terminal` and
  `:ui:kit-content`. The shim stands in for it. Effect: no terminal sessions exist in this module, so the terminal
  checks in `BuiltinLinux` (unconfined children, live sessions, terminal work leases, the stop-all before removal)
  always see none.
- **D2. Two libraries are used before the catalog has them.** `org.apache.commons:commons-compress:1.27.1`
  (`BuiltinLinux` unpacks the Ubuntu tarball) and `org.json:json:20180813` (tests) are not in
  `gradle/libs.versions.toml`, and the copies cannot compile without them. Plan 03's own `android/linux` recipe
  already names `libs.commons.compress`. The build file looks up the catalog aliases `commons-compress` and `json`
  first, and uses the Flutter app's exact pins only while those aliases are missing. When the coordinator adds the
  aliases, the catalog is used with no edit to this module. The alternative was a module that does not compile, which
  would leave the unit's finish line unmet.
- **D3. Three closure files beyond plan 11 §4.1** (`NativeStrings`, `CodingAlertRequest`, `ScheduledPromptPolicy`).
  They are pure and have no Flutter imports. Plan 11 lists `NativeStrings` as "delete" (S11-8). Here it stays as a
  copy until the `Words` resolver replaces it.
- **D4. The module does not depend on `:engine:runtime`, `:core:data`, `:core:common` or `:core:model`**, unlike the
  plan 03 recipe. Nothing copied uses them yet, and `:engine:runtime` does not exist yet. S3-RTSPLIT adds those edges
  with the `RuntimeHost` implementation.
- **D5. `BuiltinInstallerProducerQaService` is in `src/androidTest`**, as plan 11 §4.1 says. It is not yet declared in
  a QA manifest, so on a device it is compiled but not registered.

## 5. Instrumented tests: listed, not run

These are copied into `src/androidTest` and compile (`compileDebugAndroidTestKotlin`,
`compileDebugAndroidTestJavaWithJavac`). They were **not run**: there is no emulator.

| Class | Home | Notes |
|---|---|---|
| `BuiltinComponentFilesystemAcceptance` | `:android:linux` | step-driven `Instrumentation` |
| `BuiltinComponentUpdateAcceptance` | `:android:linux` | |
| `BuiltinIdleAcceptance` | `:android:linux` | |
| `BuiltinRestoreAcceptance` | `:android:linux` | |
| `BuiltinRuntimeAcceptance` | `:android:linux` | |
| `BuiltinRuntimeReclaimAcceptance` | `:android:linux` | |
| `BuiltinWorkLeaseAcceptance` | `:android:linux` | its terminal step calls `LocalTerminal.start` and will fail until `:android:terminal` (D1) |
| `RuntimeQaGuard.java`, `BuiltinInstallerProducerQaService` | `:android:linux` | support classes |
| `PhoneEngineAcceptance`, `PhoneEngineDeviceBoundaryRegressions`, `PhoneEngineNativeRegressions` | `:android:team` (later, plan 11 §4.3) | not copied |
| `Bb8DeviceSmoke`, `Bb8MainCallback`, `Bd9DeviceSmoke` | retired with the Flutter QA flavours (plan 11 §4.3) | not copied |

Running them later needs an androidTest manifest that declares each `<instrumentation>` and the QA service. The
Flutter app's QA build sets these up with `-PocBuiltinRuntimeQa=true`. The runtime also needs `jniLibs`
(`libproot.so` and the rest), which were not part of this unit's copy list. The acceptance runs also need an `:androidApp`
host. Until then they are compile-only.

## 6. Commands and results

Environment: Android SDK commandline-tools `16111833` in `/opt/android-sdk`, with `platform-tools`,
`platforms;android-37.1` and `build-tools;37.0.0`. JDK 21 (OpenJDK 21.0.12.1). Gradle 9.7.1 wrapper.
`native/local.properties` has `sdk.dir=/opt/android-sdk` and is gitignored. Maven Central often answered **HTTP 429**,
because many sessions download in parallel, so builds were retried with backoff.

| Command (from `native/`) | Result |
|---|---|
| `./gradlew :android:linux:compileDebugKotlin` | BUILD SUCCESSFUL |
| `./gradlew :android:linux:testDebugUnitTest` | BUILD SUCCESSFUL: **491 tests, 0 failures, 0 errors, 0 skipped** across 36 classes (from `build/test-results/testDebugUnitTest/*.xml`) |
| `./gradlew :android:linux:compileDebugAndroidTestKotlin :android:linux:compileDebugAndroidTestJavaWithJavac` | BUILD SUCCESSFUL |
| `cmp` of every copied file against its source (114 files) | 0 differences |
| `./gradlew :quality:test` | 17 tests, 1 failure: `ModuleOwnersTest`. It discovers `:android:linux`, which is not in `MODULE_OWNERS`; that file is coordinator-owned (request R3). The same test also lists `:tools:arb2xml` and `:tools:flutter-migration-readers` as missing, so it already failed on the base branch. All Konsist rules pass. Their production scope does not include `android/`. |
| `./gradlew -p build-logic test` | BUILD SUCCESSFUL. It passed on the third attempt; the first two failed with HTTP 429 while downloading `kotlin-test-2.4.0.jar`. This unit does not touch build-logic. |
| Privacy grep (private IPv4 ranges, e-mail addresses) over the added lines | No private IPs. The only addresses are `proof@invalid.example`, a reserved example domain inside copied setup scripts. They stay, because the copies cannot be edited. |

## 7. Resolved versions (`debugUnitTestRuntimeClasspath`, direct)

`org.jetbrains.kotlin:kotlin-stdlib:2.4.21`, `org.apache.commons:commons-compress:1.27.1`,
`androidx.core:core-ktx:1.19.1`, `junit:junit:4.13.2`, `org.json:json:20180813` and `dev.zacsweers.metro:runtime:1.5.1`
(added by the convention plugin). AGP 9.3.3, compileSdk 37.1 and minSdk 26 come from the convention.

## 8. Catalog requests (coordinator)

- **R1.** `commons-compress = { module = "org.apache.commons:commons-compress", version = "1.27.1" }`. This is the
  Flutter app's pin, and plan 03's recipe already names it.
- **R2.** `json = { module = "org.json:json", version = "20180813" }`, used only by tests.
- **R3.** Add a `MODULE_OWNERS` row: `:android:linux	S	runtime-owner	slice-1`. While it is missing, `ModuleOwnersTest`
  fails.

These are not catalog entries, but they are needed later: `androidx-test-ext` and `androidx-test-runner` library
aliases (only their versions are in the catalog) for when the acceptance harnesses get JUnit wrappers (plan 14 §2.9).

## 9. Open problems

- **P1. FQCN clashes block joining `:androidApp`.** The shims `MainActivity` and `R` (and `BuildConfig` if the app
  generates one) have the same FQCNs as classes in `:androidApp` (`io.github.eslamasabry.opencode_mobile.*`). Do not
  add `:android:linux` to `:androidApp` until S3-RTSPLIT replaces the copies' direct references: intents through an
  injected launch-intent factory, resources through the module `R`, QA hooks through `AppBuildInfo.qaHooks`.
- **P2. Real `LocalTerminal` behaviour is missing** (D1). `BuiltinLinux` safety paths that count terminal sessions are
  exercised only with zero sessions.
- **P3. No `jniLibs` in the module** (proot, loader, talloc, shmem). Plan 11 §2.3 puts them in `:android:linux`. They
  are binaries, not Kotlin, so they fall outside this unit's "Kotlin files" copy list. They are needed before any
  device run.
- **P4. No catalog row ids on the copied tests** (§2). This needs a decision on whether the twin rule allows a rename,
  or whether a wrapper test class should carry the id.
- **P5. `native/TWINS.md` is new.** If another unit creates it at the same time, the coordinator merges the headers.
  The rows are independent.
- **P6. Maven Central's 429 rate limit** makes cold builds in parallel cloud sessions flaky. Retry with backoff.
