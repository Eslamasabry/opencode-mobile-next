# S3-AGENTSE: the agents engine (`:engine:agents`)

Date: 2026-11-01. Branch: `native/agentse-2026-11-01`, created from `claude/agentse-phone-agents-0xcovc` at `6b9a2b21`.
Write set: `native/engine/agents/**` (new module) and this report. Nothing else was edited.

Inputs: `native/AGENTS.md`, `docs/native/slice0/*.md`, `docs/native/units/*.md`, plan `01` (ADR), `15` (S3-AGENTSE row),
`17` §1 (the agents design), `11` §1.2/§22.1 (row homes), `04` (§8 pointers), `map/agents-backends.md` (features 01–16,
33, 34, 40, 42, §4 stored data), the catalog rows (`catalog/behaviors.jsonl`), and the Flutter sources and tests they
port.

## Result

**Implemented and verified on desktop (JVM); compiled for Android. Not wired into an app, not enabled.** No Metro
bindings, no `:android:agenthost` binding (S3-AGENTHOST), no screens (S3-AGENTSUI).

- `./gradlew :engine:agents:desktopTest`: **230 tests in 33 classes, 0 failures**.
- `./gradlew :engine:agents:compileAndroidMain`: passes.
- `:quality:test` was **not run**: it compiles every module, and `:core:data` (and others) do not compile on this branch
  while the core contract is being reconciled (the brief's warning). The Konsist rules were checked by hand on the new
  code instead (no `Dispatchers.*`, `Clock.System`, `runBlocking`, `GlobalScope`, `runCatching`, `println`, `java.*`
  in commonMain, `RedactorApi`). `ModuleOwnersTest` will fail until `MODULE_OWNERS` has the row requested below.

Plan 15 finish line, piece by piece:

| Piece | State |
|---|---|
| Agent catalog, pins copied verbatim | done: `catalog/`, `pins/agent-pins.json`, byte-for-byte parity test against the Dart sources |
| Readiness | done: `readiness/AgentReadiness.row` (the 17 §1.4 order, table-tested), timers as constants |
| Install pipeline | engine side done: `install/AgentInstaller` (components, marker, restore, cancel, storage guard). The scripts stay the runtime owner's `.sh.tmpl` resources (11 S11-3) |
| Phone check | done: `check/PhoneCheck`, `check/GateInspector`, `gate/PhoneGate` (byte-exact fingerprint). The Claude "Use previous version" rollback is not ported (below) |
| Sign-in model | done: `signin/` (terminal plan, command policy, output reader, foreground failure codes), `auth/AgentAuth` |
| Auto-approval | done: `approval/` (store + policy over a reply port) |
| Merged feed | done: `feed/` (sources, merge, saved rows). The controller that owns the first-paint timers is not built (below) |
| Failure / title / context memory | failure and title done (`memory/`). Context memory is `:engine:transcript`'s (17 §1.8); this engine has no reader yet |
| `AgentHost` interface | done: `host/AgentHost` (17 §1.2 shape), `UnavailableAgentHost`, `AgentHelper`, `AgentHostSecret`, `PaseoConfig` |
| Claude Agent via OpenCode (brief) | done as in the Flutter trial: `claudeagent/` (state machine, link script byte-for-byte, result parsing) |

Handoff and sub-agents are the row's non-goals (S5) and were not built.

## What was built (package `io.github.eslamasabry.oc.engine.agents`)

| Package | What |
|---|---|
| (root) | `AgentId`, `OwnerId`, `agentsBackend()` (`"<owner>.agents"`), `AgentKeys` (every key name, Flutter formats) |
| `catalog` | `AgentCatalog`, `AgentDescriptor`, `AgentInstallRecipe`, `AgentArtifact` (all Dart validators), `AgentCapabilities`, routes and enums; `AgentPins` parses `pins/agent-pins.json`, embedded byte for byte as `AgentPinsJson.kt`; `pins/generate_pins.py` regenerates it from the Dart freeze tip |
| `store` | Narrow ports `AgentPrefs`, `AgentSecrets`, `SecureBytes` (see deviation 1) |
| `json` | `AgentJson` (JsonElement) and `DartJsonText` (Any?): Dart `jsonEncode`-compatible writers for stored formats |
| `host` | `AgentHost` + value types, `AgentHostProblem` (CR17-1 codes), `UnavailableAgentHost`, `Secret`/`TransientLabel` (mask in `toString`), `AgentHostSecret` (64-hex, committed before start, rotation, sign-in reset), `AgentHelper` (start/rotate), `PaseoConfig` (equals Dart `configuration()`) |
| `gate` | `PhoneGate` (fingerprint, read/write, change reason, older qualified version), `CheckRequiredReason` |
| `check` | `PhoneCheck` (Install → Version → Connection → Ready, gate write, Claude sign-in read), `PhoneCheckPorts`, `GateInspector` (`inspect()`), `GateInspection.toRuntime()` |
| `install` | `AgentInstaller`, `AgentSetupJobs` port, `AgentSetupProgress`, `InstallRefusal` |
| `ownership` | `AgentOwnership` (owner aliasing, scoring, one-time gate adoption, retained keys) |
| `home` | `AgentHomeCleaner` (drain → password → keys), `AgentsSignInReset` |
| `removal` | `AgentRemoval` (catalog executables only, exit codes 0/16/17/18, 32-hex receipt, measured freed bytes) |
| `readiness` | `AgentReadiness.row`, `PhoneAgentRuntime`, `AgentRow`, `AgentStatusKey`, `AgentFixAction`, auto-update and behind-pin rows, timers |
| `certification` | `AgentCertification` over the bundled matrix (`pins/agent-certification-matrix.json`, verbatim, embedded byte for byte) |
| `persona` | `AgentPersona` (ids, marks, aliases, `other`) |
| `providers` | `HostAgentProviders` rows (always reach a final state), fixed once-only permission labels |
| `selection` | `AgentSelection` (agent chip, model per agent, model names ≤ 40, `/root/projects/` rule) |
| `signin` | `SignInOutputReader`, `AgentSignIn` (plan, command policy, re-probe after the terminal), `SignInForegroundFailure` codes |
| `auth` | `AgentAuth` (one chain per owner, shared reads, 65/10/20 s, keep last answer, sign-out confirmed), `AuthProjectionJson` |
| `endpoint` | `HelperEndpointMatch` (loopback names are one server) |
| `diagnostics` | `HelperDiagnostics` over `AgentHost.helperExits` |
| `claudeagent` | `ClaudeAgentTrial`, `ClaudeAgentLink` (+ script template generated from the Dart) |
| `feed` | `ChatFeedItem`, `ChatFeedSource`, `MergedChatFeed`, `SavedChatFeed` (`oc.agentFeed` rows, exact JSON) |
| `approval` | `AutoApprovalStore` (`oc.autoApprove.<profile>`, `*`, inheritance), `AutoApproval` (once only, sweep, hydration) |
| `memory` | `ConversationTitleMemory`, `AgentTurnFailureMemory` |
| `family` | `RunningFamily` |

Work was split into four slices (foundation + D by the lead; A, B, C in parallel worktrees), each with its own
package write set; all were merged into this branch and run together.

## Rows covered

| Rows | Test classes |
|---|---|
| B-AGENT-007, 008 | `AgentCatalogTest` (9), `AgentPinsParityTest` (3, desktop: every URL, hash, size, member, args, Paseo/lock/Node and tested versions equal the Dart sources; generated Kotlin equals the file) |
| B-AGENT-009, 010, 011 | `AgentCertificationTest` (15), `AgentCertificationFileTest` (2, desktop) |
| B-AGENT-004, 006 | `AgentAuthTest`, `AuthProjectionJsonTest`, `AuthProjectionFixtureTest` (reads `test/fixtures/agent_auth`) |
| B-AGENT-022 | `HelperDiagnosticsTest` |
| B-AGENT-023 (engine half) | `AgentPersonaTest` |
| B-AGENT-026 | `SignInOutputReaderTest` (12) |
| B-AGENT-027, 028 (status-read clause only) | `LegacySignInStatusTest` |
| B-AGENT-111, 112, 113 | `HostAgentProvidersTest`, `AgentCatalogTest`, `AgentSignInTest` |
| B-AGENT-119 | `HelperEndpointMatchTest` |
| B-AGENT-120, 124, 125 (transferable clauses) | `AgentInstallerTest`, `AgentHelperTest` |
| B-AGENT-153 (feed-level part) | `MergedChatFeedTest` |
| B-AGENT-149, 181 | `MemoryTest` |
| B-AGENT-214 | `RunningFamilyTest` |
| B-AGENT-221…225 | `AutoApprovalTest`, `AutoApprovalStoreTest` |
| B-CHAT-013, 014 | `ChatFeedTest` |
| B-STATE-029 | `MergedChatFeedTest` |
| B-STATE-056 | `AgentHomeCleanerTest` |
| B-STATE-099 | `AutoApprovalStoreTest` |
| B-NAV-030, 031, 032 | `MergedChatFeedTest` (the merged-feed NAV rows; their mapper should know) |
| B-LINUX-118 | `AgentOwnershipTest` (8: all of `phone_agent_owner_test.dart`) |
| B-LINUX-125, 126 | `AgentSignInTest`, `AgentAuthTest` |
| B-LINUX-129…134 (engine clauses) | `PhoneCheckTest`, `PhoneGateTest`, `AgentHelperTest`, `PaseoConfigGoldenTest`, `AgentReadinessTest` |
| B-LINUX-135, 136 | `AgentInstallerTest` |
| B-LINUX-137 (automatic-start fields) | `AgentHelperTest` |
| B-LINUX-139 (engine clauses) | `AgentRemovalTest` |
| B-LINUX-140, 141 | `AgentReadinessTest` (all of `phone_agents_test.dart` + the 17 §1.4 order table), `PhoneCheckTest` (inspection → ready row) |
| proposed B-AGENT-401…409 (Claude Agent trial) | `ClaudeAgentTrialTest` (15), `ClaudeAgentLinkScriptParityTest` (1, desktop: the root script equals the Dart one byte for byte) |
| proposed B-AGENT-SEL-001…004 | `AgentSelectionTest` (6) |
| proposed B-AGENT-P01, P02 | `SavedChatFeedTest`, `MergedChatFeedTest`, `MemoryTest` |

Not covered here (their homes): B-AGENT-002/003 (Codex direct account, P2 with AGENTS-BACKENDS-35; see deviation 8);
B-AGENT-005 per-agent probe scripts and B-AGENT-050 (kept Kotlin/scripts in `:android:agenthost`); B-AGENT-154 and the
Paseo-wire half of 153 (S3-PASEO); B-AGENT-014…021, 227, 228 (handoff, S5); B-AGENT-029 (S5 cards); B-AGENT-036
(delivery); B-LINUX-049…053, 114…117, 119…124, 127, 128, 138, 142 (scripts, native, controller or Termux clauses).

## Commands and results

Environment: Android SDK from `commandlinetools-linux-16111833_latest.zip` in `/opt/android-sdk/cmdline-tools/latest`
with `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0` (AGP also pulled `build-tools;36.0.0`);
`ANDROID_HOME=/opt/android-sdk`; Gradle 9.7.1 on JDK 21; `native/local.properties` (gitignored) holds `sdk.dir`.
Maven Central answered **429** here, so a **local-only** `~/.gradle/init.d/central-mirror.gradle.kts` puts Google's
Central mirror and `google()` first (plus the plugin portal for the included build). It is not in the repository.

```
cd native
./gradlew :engine:agents:desktopTest          # 230 tests, 33 classes, 0 failures
./gradlew :engine:agents:compileAndroidMain   # BUILD SUCCESSFUL
```

The first attempt with `:core:data` as a dependency failed in `:core:data:compileKotlinDesktop`
(`PrefsKeyValueStore.kt:190`, `AeadSecretStore.kt:137`: `Redacted` passed where `StorageProblem` is expected), which is
why the module depends only on `:core:common`, `:core:model`, `:core:domain` and `:core:security`.

Privacy check of the diff: the one private-range address (a CGNAT `100.64.x` test input) was replaced with the
documentation address `203.0.113.2`; an account-label test value was replaced. The remaining `user:…@example.*` strings
are credential-in-URL test inputs on reserved example domains (the URL validators must refuse them), not addresses of
people. No credentials, tokens or real hosts are in the diff.

## Deviations

1. **Storage ports instead of `:core:data`.** `AgentPrefs` / `AgentSecrets` are narrow ports with
   `KeyValueStore` / `SecretStore` semantics; the adapters (with the profile's `WriterLane` and 05's list codec) bind
   above both modules. The module keeps its own Dart-compatible JSON writers (`json/`) for the same reason; both are
   marked to move to `DartJson`.
2. **Pins are the repo's freeze tip, not plan 17's list.** "Copied verbatim from `_builtInCatalog()`" wins over the
   versions written in 17 §1.3 (taken at an older commit): OpenCode 2 2.2.5, Claude Code 2.1.316, Codex 0.166.0,
   Gemini 0.66.0, Qwen 0.26.4, Kilo 7.8.18, Cline 3.0.74, Goose 1.54.0, Oh My Pi 18.5.1, fx 0.0.13; Paseo 0.11.2, lock
   `b0bcc4f6…`, Node v24.21.0. The pins file also carries `claudeCodeTestedVersions` and `paseoTestedVersions`.
3. **Pins resource.** `oc.kmp.library` has no Compose resources, so `pins/agent-pins.json` (and the certification
   matrix) are embedded as generated Kotlin constants; desktop tests fail if a constant and its file differ.
4. **Errors.** There is no `Failure.Runtime` yet (CR17-1). `AgentHostProblem` carries the five channel codes on today's
   leaves (`Unavailable(builtinLinux)`, `Net.ServerBusy`/`Net.Conflict` with the code as `WireTag`) and
   `Failure.agentHostProblem()` reads it back losslessly. App refusals the `RefusalReason` enum lacks are engine enums
   (`InstallRefusal`, `PhoneCheckFailure`, `RemovalRefusal`, …).
5. **No copy in the engine.** Status lines, limitations and resume notes are enum keys (English below); `resetAt` is
   epoch millis; an untitled feed row stores `null` (Dart reads it back as "New chat").
6. **Phone check without the Claude rollback.** The "Use previous version" path (`previousClaudeRecipe`,
   `usePreviousVersion`) is not ported; plan 17 §1.5 keeps `NativeComponentUpdateRecovery` in Kotlin, and its engine
   orchestration needs the runtime's script runner. The hello, idle-probe and install-and-wait steps are a port
   (`PhoneCheckPorts`) for S3-PASEO / S3-RTWIRE.
7. **Claude Agent trial included** as the brief asks ("as in the Flutter trial"), although plan 02 records the owner's
   2026-10-31 "not in the product". It is engine code only, gated as in Flutter (phone server ≥ 2.2.3, provider
   offered); `capable` feeds `ServerCapabilities.withClaudeAgent` rather than being gated on it (that flag is computed
   from it). Whether the picker shows it stays with S3-AGENTSUI and the owner.
8. **`AgentAccount`** in `auth/` is the per-agent auth answer; the Dart `agent_account.dart` (direct Codex account,
   B-AGENT-002/003) is not ported (Codex direct is P2).
9. **Auth sharing.** A list refresh shares an in-flight read; "Check again" and the end of a sign-in terminal make their
   own read, queued on the same per-owner chain (so reads never overlap and an older signed-in answer cannot complete a
   newer attempt, B-LINUX-126). Sign-out's reply already contains the following probe (the kept script runs logout then
   status).
10. **Feed.** `ChatFeedSource` is `StateFlow<SourceFeed>` + `suspend refresh()`; starting a chat is reduced to picking
    the source. The first-paint hold and folder re-read schedule are constants for the backend controller.
11. **Auto-approval** decides and replies through `PermissionReplyPort.replyOnce`; the v1/v2/legacy reply shapes and
    the OpenCode 2 policy hand-off stay with the requests engine.
12. **Tests were written with the code**, from the Dart cases, not run red first in every slice.

## Words keys (English, for `:ui:words`)

- `AgentStatusKey`: AgentUnavailable "This agent is not available yet." · AgentUnsupportedArchitecture "This phone cannot
  run this agent." · AgentNoVerifiedDownload "This agent has no verified download yet." · AgentInstallToStart "Install
  this agent on your phone to get started." · AgentCheckNeeded "Run the phone check before using this agent." ·
  AgentStoppedInBackground "Stopped while the app was in the background." · AgentHostStopped "The host is not
  available. Resume it and check again." · AgentCheckingSignIn "Checking sign-in on your phone." · AgentUsageLimit
  "Usage limit reached. Try again when it resets." · AgentSignInFix "Sign-in could not be checked. Try signing in
  again." · AgentSignedOutApiKey "Set up a provider key on the host to use this agent." · AgentSignedOut "Sign in on the
  host to use this agent." · AgentResumeUnverified "Can't reopen old chats" · AgentReady "Ready to use."; resume note
  "Starts a new chat".
- `AgentFixAction`: Install, Sign in, Resume, Run phone check.
- `AgentLimitation`: NeedsPhoneCheck "Needs a check on your phone before use." · GeminiHostSignIn "Needs host sign-in and
  a phone check. Optional terminal extensions are not installed." · QwenProviderKey "Set up a provider key on the host.
  The old Qwen sign-in option is discontinued." · KiloSignInOrKey "Sign in or add a provider key, then check it on your
  phone. Large download." · ClineSignIn "Sign in on this phone, then check it. Large download." · GooseProvider
  "Configure a provider on the host. Credential storage needs a phone check." · OmpLogin "Run /login on the host and
  choose a provider. Needs a phone check." · FxProvider "Choose and sign in to a provider on the host. Needs a phone
  check." · NeedsHostSetup "Needs host setup." (new, test catalogs).
- `AgentResumeReason`: ResumeUnverified "Can't reopen old chats" · SavedChatsNeedCheck "Saved conversations need a
  check."
- `HostAgentReasonKey`: HostAgentReady "Ready" · HostAgentSignInFirst "Sign in on your computer first." ·
  HostAgentChecking "Checking your computer." · HostAgentResumeUnverified "Conversation restoration has not been
  checked." · HostAgentResumeUnsupported "This agent cannot restore a conversation." · HostAgentNotPilot "This agent is
  not available in this preview." · HostAgentUnavailable "This agent is not available on your computer." ·
  HostAgentDisabled "This agent is turned off on your computer."; sign-in messages (Gemini, Omp,
  generic) and the permission labels "Allow once", "Reject once" as in `host_agent_providers.dart`.
- `agentFolderOutsideProjects`: "Agents on this phone work in projects under /root/projects. Choose one of those."
- Sign-in: ProviderKeyOnHost "Set up a provider key on the host to use this agent." · OpenSignInPage "Open sign-in page"
  · CopyCode "Copy code".
- Helper notices: StoppedUnexpectedly "The agent helper stopped unexpectedly. Restart it and try again." ·
  PossiblyStoppedByAndroid "The agent helper stopped. Android may have stopped it to free memory or limit background
  processes. Restart it and try again."
- New sentences to write (no Flutter copy maps one to one): `InstallRefusal` (SetupBusy = existing "Another setup is
  running"; NotInstallable, NotEnoughSpace, AlreadyInstalling), `AgentSetupFailure` (Download, Checksum, Storage,
  Interrupted, Version, Unknown → "Setup didn't finish · Try again" family), `PhoneCheckStep` (Install, Version,
  Connection, Ready), `PhoneCheckFailure` (11 values), `RemovalRefusal` (5 values), `ClaudeAgentTrialState` (picker copy
  in `lib/ui/widgets/pickers_claude_agent.dart`).

## Catalog and coordinator requests

- **MODULE_OWNERS:** add `:engine:agents	S	agents-owner	slice-3` (owner name the coordinator's choice);
  `ModuleOwnersTest` fails without it. `ModuleRules.engineOrder` already lists `agents`.
- **Behaviour catalog:** add B-AGENT-401…409 (Claude Agent trial: version gate, phone/provider gate, late re-read,
  connect once, needs sign-in and link outcomes, failed, check again, off-phone, root script), B-AGENT-SEL-001…004
  (agent chip, model per agent, model names, projects folder rule), B-AGENT-P01 (saved feed rows paint first, never
  running, replaced per source, ≤ 4 Paseo folders, exact JSON), B-AGENT-P02 (turn-failure memory); amend
  B-AGENT-027/028 to the status-read clause (17 §1.14 already says so). Renumber as preferred; tests will follow.
- **CR17-1:** `Failure.Runtime(RuntimeProblem)` with the five agent values; then `AgentHostProblem.asFailure` becomes a
  one-liner.
- **`:core:data` dependency** once it compiles: bind `AgentPrefs`/`AgentSecrets` adapters and replace the two local
  JSON writers with `DartJson`.
- **ProfileScope registration** (wherever modules register key shapes): `oc.chatAgent`, `oc.agentModel`,
  `oc.modelNames`, `oc.autoApprove`, `oc.agentTurnFailures`, `oc.conversationTitles`, `oc.agentSignedOut` (Profile);
  `oc.agentPhoneGate`, `oc.phoneAgentGateMigration` (StringList), `oc.agentInstall`, `oc.agentFeed`,
  `oc.phoneAgentsUsed`, `oc.<what>.<owner>.agents` (Owner); `oc.phoneAgentOwner.*` (Other, swept by value);
  secret `oc.agentHostSecret.`.
- **No new libraries** were needed.

## Open problems

- **Tested Claude versions disagree at the freeze tip:** Dart `claudeCodeTestedVersions` = {2.1.283, 2.1.294, 2.1.316}
  but Kotlin `PhoneAgentSignIn.TESTED_CLAUDE_VERSIONS` lists only 2.1.283 and 2.1.294. `TestedClaudeVersionsTest`
  (B-AGENT-050, `:android:agenthost`) will fail on the kept Kotlin until 2.1.316 is added — the same failure mode
  map 16 records ("every check after the update read Sign in needed").
- **Older-proof quirk, kept faithfully:** `olderQualifiedVersion` compares the agent version before the helper pins
  (as Dart `_gateChangeReason` does), so an old proof whose Paseo/lock/Node also moved is still offered as "behind the
  pin". The inventory must still report that exact old version, but the owner should decide whether helper moves
  should block it (`PhoneGateTest` pins today's behaviour).
- **Not built:** `PhoneAgentsBackend` (the transient `"<owner>.agents"` `ProfileSource`, watchdog, recover) and
  `AgentConversations` (open/resume/replace) need `:engine:connection` and the Paseo gateway (S3-PASEO);
  `AgentReadinessClock` exists as timer constants only; `AgentToolAdapters` is S5; `ContextUsageMemory` reader.
- **Not run:** `:quality:test` (see Result); no device or emulator run (nothing here touches a device).
- **Gate fingerprint against a real install:** `GateFingerprintTest` with the real-install fixture (05 §2.5) needs that
  fixture; today the test pins the exact bytes from the Dart expression instead.
