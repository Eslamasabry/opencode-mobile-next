# Unit S3-AGENTHOST: `:android:agenthost`, the phone agents' host

Date: 2026-11-01. Branch: `native/agenthost-2026-11-01`, cut from `claude/native-agenthost-2026-11-01-dw9h4g` at
`7db496b2` (the M0 tip with the kita2 merge). Write set: `native/android/agenthost/**` (new module) and this report.
Inputs: `native/AGENTS.md`, `docs/native/slice0/*`, `docs/native/units/*` (S1-LINUX, S3-RTENG, INTEGRATE-W1 above
all), plan 01 (binding), plan 15 row S3-AGENTHOST, plan 11 §0.2 (S11-1, CR11-0, CR11-13), §2.1, §4.1, §5.3, §5.4, §7,
plan 17 §1.2, §1.10, §1.12–§1.14, map `builtin-linux.md` BUILTIN-LINUX-52…54, map `agents-backends.md`, the Flutter
app's Kotlin (`android/app/src/main/kotlin`, freeze snapshot `580803d4`) and its Kotlin harnesses (`test/native/`).

**Shipping state:** implemented and verified in this container (compile, plain-JVM tests). Committed and pushed to the
branch. No PR, no CI, no emulator, no device, no release.

**Finish line (plan 15):** "`PhoneAgent*`, `PaseoStaleLock`, admission, readiness, idle, leases, sign-in over private
pipes and the auth probe run behind `AgentHost`; their Kotlin tests pass."

- **Met:** every `PhoneAgent*` class, `PaseoStaleLock` and `NativeAgentHostAdmission` is in the module; the agent block
  of `BuiltinLinux` (homes, private processes, the ticketed helper admission bound to the activity epoch, the wanted
  revision and the idle generation, the foreground work-lease admission, sign-in pipes, the private auth probe, config
  and home deletion) is moved into `AgentProcesses`; their Kotlin tests pass — the 3 copied JVM test classes, the 3
  Flutter Kotlin harnesses ported to JUnit (71 scenarios), and 31 new tests (137 tests, 0 failures).
- **Not met:** "behind `AgentHost`". `AndroidAgentHost` has the plan 17 §1.2 method set as Outcome calls, but it does
  not implement `io.github.eslamasabry.oc.engine.agents.host.AgentHost`: `:engine:agents` is not in this tree (S3-AGENTSE
  is a parallel branch) and that branch still codes against the pre-W1 `Failure.Net.*` contract, so it does not compile
  on the flat contract. The adapter is a thin file once it does (P1).
- **Not met:** the runtime binding. `AgentProcesses` reaches the runtime through ports; S3-RTSPLIT (this unit's
  dependency, not merged) binds them from the split `BuiltinLinux` (P2).

---

## 1. What was built

Package `io.github.eslamasabry.oc.android.agenthost`: the same classes are still copied in `:android:linux` under the
Flutter package until S3-RTSPLIT deletes them there, and two copies may not share a FQCN on the app classpath
(S1-LINUX P1).

| Path (under `native/android/agenthost/`) | Kind | What it holds |
|---|---|---|
| `build.gradle.kts` | new | `oc.android.library`; `:core:common`, `:engine:runtime`, coroutines; Java 17 bytecode (D3); tests: JUnit 4, `kotlin-test-junit`, coroutines-test, `org.json` (catalog alias first, S1-LINUX R2 fallback). |
| `PhoneAgentAuth{Capture,ColdOwner,Deadline,Inventory,Json,Lock,OtherOwners,Probe,ProcessTree,Projection,Request,Runtime}.kt`, `PhoneAgentCheckOutput`, `PhoneAgentPaths`, `PhoneAgentInventory`, `PhoneAgentClaudeHome`, `PaseoStaleLock`, `NativeAgentHostAdmission` | twin | Byte-equal to the Flutter source except the `package` line (checked with `diff`). |
| `PhoneAgentHost`, `PhoneAgentChecks`, `PhoneAgentSignIn` | twin + seam | `BuiltinLinux` → `AgentLinux` (type of the `linux` field and of `cleanup`'s parameter); `PhoneAgentHost` reads `linux.primaryAbi` instead of `Build.SUPPORTED_ABIS` (null off-device); `TESTED_CLAUDE_VERSIONS` + `2.1.316` (D5). 4, 5 and 6 changed diff lines. |
| `SetupDiskSpace.kt` | twin | `object SetupDiskSpace` from `SetupRunner.kt`, verbatim (the launch space guard). |
| `NativeIdleState.kt` | twin (part) | `NativeIdleState.Snapshot`: fields, defaults and `holds` only, which the admission reads. |
| `AgentLinux.kt` | new | The seam the three classes call: exactly their members (the Flutter harness stub `test/native/phone_agent_host_stubs/linux.kt` lists the same surface). |
| `AgentGuestPorts.kt` | new | CR11-0's `GuestLauncher`, `GuestProcessRegistry`, `ProcessOwner` in plan 11 §5.4's shapes (D1), plus `AgentLocks` (S11-2 lock family), `AgentServiceTable`, `AgentRuntimeFacts`/`AgentRuntimeState`/`AgentIdleFacts`, `AgentWorkLeases`, `AgentProcessControl`, `PrivateAgentAuth`. Each member names the `BuiltinLinux` code it stands for. |
| `AgentProcesses.kt` | moved | Plan 11 §5.3 `AgentProcesses`: `BuiltinLinux.kt` lines 731–1076, 1804–1811 and 2096–2131 (`agentSignInCommand`, `prepareAgentProfileHome`, `shareClaudeSignIn`, `clearStaleAgentLoginLock`, `agentPaseoHome`, `agentPortAnswers`, `startAgentProcess`/`startSignInProcess`/`startPrivateAgentProcess`, `registerAgentProcess`, `trackPrivateAgentService`, `admittedHostTicket`, `commitHostAdmission`, `requireAgentHostUpdateIdle`, `stopAgentProcess`, `agentAuthProbe`, `blockAgentProfile`, `writeAgentConfig`, `deleteAgentHome`, `privateAgentDiagnostics`, `captureAgentHostStart`, `agentHostAdmissionCurrent`, `withAgentHostStart`, `SIGN_IN_PROGRAMS`). Moves only: order, checks and words unchanged; `synchronized(locks.instance)` replaces `@Synchronized`; `AppLog` replaces `Log`; `FileModes` wraps `Os.chmod`/`Os.lstat`. Also the runtime's two reverse reads (`anyAgentProcessAlive`, `isBlocked`) and the **process budget hooks** (§1.1). |
| `AndroidAgentHost.kt` | new | The Outcome surface (what `MainActivity.handleBuiltinLinux` did for `startAgentHost`, `stopAgentHost`, `agentHostPrepareUpdate`, `deleteAgentHost`, `agentHostVersion`, `agentHostWorkspace`, `agentAuthProbe`, `agentSignInStatus`, plus `LocalTerminal`'s `agentSignInCommand` call), `status: StateFlow<HelperStatus>`, `AgentHostCode` (the five CR17-1 codes, D4), value types whose `toString` never prints the password or the account label. `ensureRunning` returns only after `GET /api/health` answers (30 s budget, 500 ms pauses, as `PhoneAgentsHost` did). |
| `PaseoHealth.kt` | new | The loopback health check (`HttpURLConnection`, 3 s timeouts, no redirects, 200 only). |
| `AgentHostWiring.kt` | new | `androidPrivateAuth(context)`: the phone binding of `PrivateAgentAuth` over the kept `PhoneAgentAuthRuntime`, wired as `BuiltinLinux.privateAgentAuth` was. |
| `tool/extract_auth_scripts.py`, `src/test/resources/auth-scripts/*.txt` (11) | new | The probe/sign-out scripts the Flutter test wrote at run time from `AgentPhoneScripts`, extracted from `lib/builtin/agents/agent_scripts.dart` without a Dart SDK. `PhoneAgentAuthRequest`'s SHA-256 (`d06e0af7…`) matches for every agent, so they are byte-identical to Dart's. |
| `src/test/…` | see §2 | 3 copied test classes (twins), 3 harness ports, 4 new classes, 2 fakes. |
| `README.md` | new | Layout, twins, checks. |

### 1.1 Process budget hooks (plan 11 §7.2)

The budget guard itself is `:engine:runtime`'s and is called by the agents engine; this module supplies what only it
knows:

- `GuestProcessRegistry.registerRootPids(ProcessOwner.Paseo) { helperRootPids() }` and `(ProcessOwner.AgentSignIn)` for
  auth checks and sign-in pipes, registered when `AgentProcesses` is built: the runtime's `ProcessHost` counts and
  `setAgentPriority` slows exactly these trees (CR11-0 "registrations").
- `liveAgentProcesses()` (what the agent host adds to Android's 32) for `BudgetReadings`, and `helperRootPids()`.
- Every agent launch goes through `GuestLauncher.startRegistered(owner, …)`, so the runtime inventory, the orphan reaper
  and the confinement map see each process with its owner.

## 2. Catalog rows covered

Tests are named `B-AREA-NNN what it proves` (rule 11). The three copied test classes keep their Flutter names
(twin rule; S1-LINUX P4).

| Row | Tests | Coverage |
|---|---|---|
| B-LINUX-115 | `PhoneAgentAuthTest` (49, one per Flutter scenario), `PhoneAgentAuthOtherOwnersTest` (8), `AgentProcessesTest` (1) | full: the private auth and other-owner native tables (`phone_agent_auth_native_test`, `phone_agent_auth_other_owners_test`) |
| B-LINUX-117 | `PhoneAgentHostTest` (14, one per Flutter scenario), `AgentProcessesTest` (13), `AndroidAgentHostTest` (1) | the phone host table (`phone_agent_host_native_test`) in full; the agent half of the launch directory (`phone_agent_paths_native_test` needs `BuiltinLinux.prootCommand`'s agent-root code, which is the runtime's: not here). The setup run, service callback and crash tables belong to `:android:linux` / `:android:background`. |
| B-LINUX-125 | `AgentProcessesTest` (2), `AndroidAgentHostTest` (1) | full: every phone agent's program is a sign-in program; Codex's folder exists and `CODEX_HOME` names it before any agent process or sign-in terminal starts |
| B-LINUX-129 | `AndroidAgentHostTest` (4), `PhoneAgentHostTest` (2) | the native half: a refused start carries its code (as a `WireTag`), status reads narrow facts, the strict inventory, no exception text leaves |
| B-LINUX-130 | `PhoneAgentAuthTest` (6), `AndroidAgentHostTest` (1) | the native half: narrow projection, timeout ≠ signed out, the label never prints |
| B-LINUX-131 | `PhoneAgentHostTest` (2), `AndroidAgentHostTest` (1) | the native half: launches Paseo without the removed flags (the copied `hostScript`), an older helper asks for a helper update |
| B-LINUX-137 | `AndroidAgentHostTest` (1) | the native half: a stale idle-resume ticket is refused before anything starts |
| B-AGENT-050 | `TestedClaudeVersionsTest` (2), `AgentProcessesTest` (1), copied `PhoneAgentClaudeHomeTest` | full (after D5) |
| B-AGENT-316 | `AgentProcessesTest` (1) | full: every launch that sets `CLAUDE_CONFIG_DIR` sets `CLAUDE_HOME` to it (helper, sign-in pipes, sign-in terminal) |
| B-STATE-056 | `AndroidAgentHostTest` (1) | the native half: the helper is drained first; a failed delete keeps the home and returns a failure, a retry succeeds |
| (plan 11 §7.2) | `AgentProcessesTest` › `P11-7-2 process budget hooks …` | no catalog row; plan reference used instead |

Tests first: the harness ports, the copied tests and the new tests were written before `AgentProcesses` and
`AndroidAgentHost` existed; the first Gradle run was red on compile, then 9 red tests (7 from a test-side
`Build.SUPPORTED_ABIS`/`Process.pid()` problem, 1 too-strict assertion, and `TestedClaudeVersionsTest`, which found D5),
then green.

## 3. Commands and results

Environment: Android SDK from `commandlinetools-linux-16111833_latest.zip` in `/opt/android-sdk/cmdline-tools/latest`
(`ANDROID_HOME=/opt/android-sdk`; licences accepted; `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`;
AGP fetched `build-tools;36.0.0` itself), `native/local.properties` (gitignored), Gradle on JDK 21
(`/usr/lib/jvm/java-21-openjdk-amd64`), JDK 17 toolchain through foojay. Container-only, not committed:
`~/.gradle/gradle.properties` (`-Xmx6g`, 4 workers) and `~/.gradle/init.d/central-mirror.gradle.kts` (Google's Maven
Central mirror first, as INTEGRATE-W1 did).

| Command (from `native/`) | Result |
|---|---|
| `./gradlew :android:linux:compileDebugKotlin` (cache warm-up only) | BUILD SUCCESSFUL (8 min, downloads) |
| `./gradlew :android:agenthost:compileDebugUnitTestKotlin` | first: FAILED, `INLINE_FROM_HIGHER_PLATFORM` (D3); then BUILD SUCCESSFUL |
| `./gradlew :android:agenthost:testDebugUnitTest` | first: 137 tests, 9 failed (see §2); final, on the committed tree with `--rerun-tasks`: **BUILD SUCCESSFUL, 9 classes, 137 tests, 0 failures, 0 skipped** |
| `./gradlew :android:agenthost:compileReleaseKotlin` | BUILD SUCCESSFUL (one warning, inside the copied `PhoneAgentInventory.kt:26`, as in the Flutter app) |
| `python3 -I native/android/agenthost/tool/extract_auth_scripts.py . native/android/agenthost/src/test/resources/auth-scripts` | 11 files; SHA-256 of the normalized body = `PhoneAgentAuthRequest.SCRIPT_DIGEST` for all |
| `native/tool/check_commits.sh <base> HEAD` | OK, 5 commits (after shortening two subjects) |
| `native/tool/check_write_set.sh --globs 'native/android/agenthost/** docs/native/units/S3-AGENTHOST.md' --base <base>` | OK |
| `native/tool/scan_push.sh <base> HEAD` + a grep of the added lines for private IPv4 ranges and e-mail addresses | clean (an e-mail-shaped `.invalid` test label was rewritten out of history before the push) |

Not run (other owners' modules, or not needed for this unit): `:quality:test` (would fail `ModuleOwnersTest`, R3),
`-p build-logic test`, `:androidApp:*`, instrumented tests.

## 4. Deviations

- **D1. CR11-0's seams are declared in this module.** `ModuleRules` allows `:android:*` → `:core:*`/`:engine:*` only;
  the `:android:agenthost → :android:linux` edge (CR11-0) is not in it and `:android:linux` has no `GuestLauncher` yet.
  `GuestLauncher`, `GuestProcessRegistry` and `ProcessOwner` are declared here in plan 11 §5.4's exact shape (only the
  package differs), so the swap is an import change. `startRegistered`'s `command` is read as a `prootCommand` result.
- **D2. More ports than CR11-0 names.** The moved block also touched the services map, the recovery/idle facts, work
  leases and process-tree helpers. They are five narrow ports (`AgentGuestPorts.kt`), each member citing the
  `BuiltinLinux` code; S3-RTSPLIT binds them from `ServiceRegistry`, `ServerRecovery`, `IdleController`,
  `WorkLeaseBridge`, `ProcessInventory`. Two tiny moves of order: `trackPrivateAgentService` puts the services-map
  insert inside the try that already covered the foreground start (a failure there now also stops the child), and
  the other-owner token is `pid` + `/proc` start time through `AgentProcessControl.startToken` (the same number
  `kernelIdentity().startTicks` gave).
- **D3. Java 17 bytecode in this module's build file.** `oc.android.library` leaves AGP's default target (11), and
  `:core:common`'s inline `attempt {}` is JVM 17 bytecode, so it cannot be inlined (`INLINE_FROM_HIGHER_PLATFORM`).
  `compileOptions` 17 here; the convention should set it (R4).
- **D4. Agent-host failures on the flat contract.** CR17-1's `RuntimeProblem` values do not exist yet. Following
  INTEGRATE-W1's flat list and the encoding `:engine:agents` already decodes: `agent_unavailable` →
  `Unavailable(FeatureId("builtinLinux"))`, `helper_update_required` → `Busy(Starting, tag)`, `agent_host_busy` →
  `Busy(Overloaded, tag)`, `agent_host_unowned` → `Conflict(tag)`, `agent_host_exited` → `Busy(Failed, tag)`, the code
  as `WireTag`; `idle_resume_stale` → `Runtime(IdleResumeStale)`; the launch space guard → `Runtime(StorageUnavailable)`;
  anything else → `agent_unavailable` (as the channel did). Details carry only the exception's class name.
- **D5. A behaviour fix in a twin: `TESTED_CLAUDE_VERSIONS` + `"2.1.316 (Claude Code)"`.** At the freeze tip
  `claudeCodeTestedVersions` (`lib/paseo/tested_versions.dart`, `580803d4`) and S3-AGENTSE's `agent-pins.json` list
  2.1.283, 2.1.294, 2.1.316, but the Kotlin list stopped at 2.1.294 — the Flutter test `claude_phone_auth_test` would be
  red there too. A phone on the current Claude pin fails the native sign-in check, which the picker shows as "Sign in
  needed". Plan 17 §1.3 requires the lists to be equal (`TestedClaudeVersionsTest`), so the line is fixed here; **the
  Flutter twin needs the same one-line fix** (twin rule, plan 02 §5.6).
- **D6. The ABI is an input.** `Build.SUPPORTED_ABIS` is null in plain-JVM tests; `AgentLinux.primaryAbi` (default:
  `Build`) and an `abi` constructor parameter of `AgentProcesses` carry it.
- **D7. Partial twins.** `SetupDiskSpace` and `NativeIdleState.Snapshot` are copied here because this module cannot see
  `:android:linux`; both go once CR11-0 is in `ModuleRules`.
- **D8. `AndroidAgentHost` takes the authored auth script as an argument.** The kept native side admits only the
  Dart-authored probe (digest-checked); the scripts move to `:engine:runtime` templates with S11-3, so the engine passes
  the rendered script. `installedVersion` takes the owner (the native inventory runs as the owner's agent user).
- **D9. No Metro binding yet.** `AndroidAgentHost` is not `@ContributesBinding(AppScope::class)`: its ports have no
  bindings until S3-RTSPLIT, and `AgentHost` is not in the tree (P1, P2).

## 5. Requests

**Catalog (coordinator):** R1 `json = { module = "org.json:json", version = "20180813" }` (same as S1-LINUX R2; test
only; the build file uses the alias as soon as it exists).

**Module rules and owners (coordinator):**
- R2. `ModuleRules`: CR11-0's edge `:android:agenthost → :android:linux` (and `:android:terminal → :android:linux`).
- R3. `MODULE_OWNERS`: `:android:agenthost	S	runtime-owner	slice-3`. Until it exists, `ModuleOwnersTest` fails.
- R4. `OcAndroidLibraryPlugin`: `compileOptions` source/target 17 for every Android library (D3).

**Core (core owner, CR17-1):** R5. Append `AgentUnavailable, HelperUpdateRequired, AgentHostBusy, AgentHostUnowned,
AgentHostExited` to `RuntimeProblem`; then `AgentHostCode.failure()` maps to `Failure.Runtime` and the WireTag interim
goes (D4).

**Runtime owner (S3-RTSPLIT):** R6. Bind `GuestLauncher`, `GuestProcessRegistry`, `AgentLocks`, `AgentServiceTable`,
`AgentRuntimeFacts`, `AgentWorkLeases`, `AgentProcessControl` from the split classes; delete the agent block,
`PhoneAgent*`, `PaseoStaleLock` and `NativeAgentHostAdmission` from `:android:linux`; call
`AgentProcesses.anyAgentProcessAlive()` where `BuiltinLinux.kt:456` read `agentProcessProfiles`, and `isBlocked()`
where `:3885` read `blockedAgentProfiles`; give `LocalTerminal`'s sign-in path `AgentProcesses.agentSignInCommand`.

**Flutter twin (S runtime, twin rule):** R7. Add `"2.1.316 (Claude Code)"` to `PhoneAgentSignIn.TESTED_CLAUDE_VERSIONS`
in `android/app` (D5).

### 5.1 `native/TWINS.md` rows (coordinator owns the file)

Copy base `580803d4`. "Twin + seam" rows differ from the source by the package line and the seams in §1; the rest by
the package line only.

| Native path (`native/android/agenthost/src/…/oc/android/agenthost/`) | Flutter source (`android/app/src/…/opencode_mobile/`) | Source commit at copy | Last synced | Owner |
|---|---|---|---|---|
| main `PhoneAgentAuthCapture.kt`, `PhoneAgentAuthColdOwner.kt`, `PhoneAgentAuthDeadline.kt`, `PhoneAgentAuthInventory.kt`, `PhoneAgentAuthJson.kt`, `PhoneAgentAuthLock.kt`, `PhoneAgentAuthOtherOwners.kt`, `PhoneAgentAuthProbe.kt`, `PhoneAgentAuthProjection.kt`, `PhoneAgentAuthRuntime.kt`, `PhoneAgentCheckOutput.kt`, `PhoneAgentPaths.kt`, `NativeAgentHostAdmission.kt` (one row each) | main, same names | da11a4f3 | da11a4f3 | S runtime |
| main `PhoneAgentAuthRequest.kt` | main, same name | e44b581a | e44b581a | S runtime |
| main `PhoneAgentAuthProcessTree.kt`, `PhoneAgentInventory.kt`, `PhoneAgentClaudeHome.kt`, `PaseoStaleLock.kt` | main, same names | cf86762f | cf86762f | S runtime |
| main `PhoneAgentHost.kt`, `PhoneAgentChecks.kt`, `PhoneAgentSignIn.kt` (twin + seam) | main, same names | cf86762f | cf86762f | S runtime |
| main `SetupDiskSpace.kt` (part of `SetupRunner.kt`) | main `SetupRunner.kt` | cf86762f | cf86762f | S runtime |
| main `NativeIdleState.kt` (part) | main `NativeIdleState.kt` | da11a4f3 | da11a4f3 | S runtime |
| test `NativeAgentHostAdmissionTest.kt` | test, same name | da11a4f3 | da11a4f3 | S runtime |
| test `PaseoStaleLockTest.kt`, `PhoneAgentClaudeHomeTest.kt` | test, same names | cf86762f | cf86762f | S runtime |
| test `PhoneAgentHostScenarios.kt` (port), `FakeAgentLinux.kt` (port of the stub) | `test/native/phone_agent_host_harness.kt`, `test/native/phone_agent_host_stubs/linux.kt` | e44b581a, cf86762f | same | S runtime |
| test `PhoneAgentAuthScenarios.kt` (port) | `test/native/phone_agent_auth_harness.kt` | e44b581a | e44b581a | S runtime |
| test `PhoneAgentAuthOtherOwnersScenarios.kt` (port) | `test/native/phone_agent_auth_other_owners_harness.kt` | da11a4f3 | da11a4f3 | S runtime |
| test resources `auth-scripts/*.txt` (generated) | `lib/builtin/agents/agent_scripts.dart` | e44b581a | e44b581a | S runtime |

## 6. Open problems

- **P1. `AgentHost` adapter.** When `:engine:agents` compiles on the flat contract, add
  `AndroidAgentHostBinding : AgentHost` (`@ContributesBinding(AppScope::class)`) over `AndroidAgentHost`: `OwnerId` →
  `String`, `Secret` → `HelperPassword`, `PaseoConfigFile.json` → `config`, `AuthAction` → `AuthRequestAction` plus the
  rendered script (D8), `AgentHostCode` ↔ `AgentHostProblem`. S3-AGENTSE's `AgentHostProblem.asFailure()` still uses
  `Failure.Net.*`; its flat mapping must equal `AgentHostCode.failure()` (D4).
- **P2. No runtime binding** until S3-RTSPLIT (R6). Until then nothing on a phone constructs `AgentProcesses`.
- **P3. Preview ports.** The kept `PhoneAgentHost` admits only port 4099 (`AGENT_PORT`); seam E8 (plan 11 §4.4,
  S11-6) changes it to `AppBuildInfo.ports.agentHost` (4199 on the preview) — that is W11-01's edit, not done here.
- **P4. P17-1 (password rotation proof)** needs an emulator with the phone's Linux; not run.
- **P5. `phone_agent_paths_native_test`'s agent-root scenarios** (B-LINUX-117, 5 scenarios) test `BuiltinLinux`'s
  `prootCommand` agent root, which stays in the runtime (`ProotLauncher`); they belong to `:android:linux`.
- **P6. The second `TestedClaudeVersionsTest` case** passes without checking while `native/engine/agents/pins/` is
  absent from the tree; it bites once S3-AGENTSE merges.
- **P7. The tests need Linux `/proc` and `/bin/sh`** (real child processes, as the Flutter harnesses did); fine on the
  PC, the VPS and CI containers, not on a non-Linux host.
- **P8. Maven Central 429s** (S1-LINUX P6) were avoided only by the container-only mirror init script.
