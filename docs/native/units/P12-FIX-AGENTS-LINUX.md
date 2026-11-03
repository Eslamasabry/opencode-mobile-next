VERIFIED: green — gates below pass; Linux BLOCKER 1 and Agents MAJOR 5 remain open (blocked: no built-in Linux runtime/setup engine can join the APK yet, §3)

# P12-FIX-AGENTS-LINUX — Codex second-eye findings for AGENTS and LINUX

Date: 2026-11-03. Branch: `claude/native-parity-p12-s49v30`, base `native/p12-qa-wave4-2026-11-03` (`cbad87e1`).
Reviews: `docs/native/reviews/p12/agents.md`, `docs/native/reviews/p12/linux.md`. Each finding was checked against
the native code and the Flutter source (`lib/paseo/**`, `lib/builtin/linux_manager/**`, `lib/main_parts/root.dart`).

## 1. Findings

| # | Finding | Verdict | Fix | Test (red before → green after) |
|---|---|---|---|---|
| A1 BLOCKER | Pairing sends the daemon password to a public host (`wss://attacker.example\.tail1234.ts.net`) | **Real** | `PaseoUrl.parse` accepts only plain hostname / IPv6 characters; `OkHttpPaseoSockets.open` refuses (InvalidEndpoint) when OkHttp's parsed host differs from the validated host, before any header is attached | `PaseoEndpointTest` "B-SEC-113 a host with characters a socket library reads as an authority end is refused" (1 failed before); `AgentsWiringTest` "B-SEC-113 the socket refuses an endpoint OkHttp would read as another host…" |
| A2 MAJOR | Production never calls `startListening()`: stream stays Connecting, sends refused, no reconnect | **Real** (the OC2 factory starts its stream in `connect`; Paseo did not) | `PaseoGatewayFactory.connect` starts listening | `PaseoCheckConnectionTest` "B-AGENT-163 a connected computer listens…" (failed before) |
| A3 MAJOR | An uncertain first send strands its draft | **Real** | Port of Dart `gateway/uncertain_start.dart` (`PaseoUncertainStart.kt`): a lost create remembers message id/runtime/time; a read or the next send adopts the daemon agent whose first user message carries that id, or frees the draft to send again; a started first message is never sent twice | `PaseoUncertainStartTest` (2 tests, B-AGENT-188) |
| A4 MAJOR | Sending bypasses the ACP resume safeguard | **Real** (Dart `sendPrompt` checks `loadHostAgentContinuation().requiresNewChat`) | `sendPrompt` refuses with `NewChatRequired` (→ `Failure.Conflict`) before any selection change or send when restoration is unverified or the handle is missing; live/daemon-held agents continue | `PaseoAcpResumeSendTest` (3 tests, B-AGENT-144; 2 failed before) |
| A5 MAJOR | Phone-agent management has no production wiring | **Real, not fixed — blocked** | Phone agents run inside the built-in Linux, which cannot be in the APK yet (§3). The hub keeps its honest one-line "not available on this phone yet" | — |
| A6 MINOR | Saved conversation titles never restored | **Real** | `PaseoGatewayFactory(savedTitles = …)` installs the lookup on every gateway before it lists; `AgentsWiring` binds it to `ConversationTitleMemory` over the app's key-value store via a new `KvAgentPrefs` (`oc.conversationTitles.<profileId>`, Flutter format) | `PaseoCheckConnectionTest` "B-AGENT-181 a connected computer shows the title saved…" |
| L1 BLOCKER | Linux is unreachable in the APK (`AbsentLinuxHost(NotOnThisBuild)`) | **Real, not fixed — blocked** | See §3 | — |
| L2 MAJOR | A job that ends between the log read and the status read is read as Interrupted | **Real** (Kotlin evaluated the log argument first) | `LinuxManager.readJob` reads status first, as Flutter does | `LinuxManagerTest` "B-LINUX-091 a job that ends between the two reads is never read as interrupted" (failed before) |
| L3 MAJOR | Port discovery drops Flutter's process-command candidates | **Real** | `scanPorts` now reads every process's command (`LinuxPortScan.PROCESS_SCRIPT`, ≤400 processes, printable, 240 chars), probes the ports they name (≤64) with the usual ones, and passes those commands to owner matching (Flutter `scanPorts(scan)`) | `LinuxManagerTest` "B-LINUX-085 a port only a launcher's command names is probed…"; `LinuxScriptsShellTest` covers the new script (`sh -n`/`bash -n`). The B-LINUX-092 retry test now asserts the three tries are of the process read, then the probe (4 runs) |
| L4 MAJOR | Interrupted-reset recovery has no startup caller | **Real** | `LinuxStartup` (in `LinuxWiring.kt`) runs `LinuxResetFlow.resumeIfPending()` once at app start; one line in `OcApplication` (Flutter `root.dart _finishLinuxReset`). Failures are logged by type only and keep the marker | `LinuxStartupTest` (2 tests, B-LINUX-093) |

No finding was judged false.

## 2. Tests and results (final head, `--rerun-tasks`, all one Gradle run → BUILD SUCCESSFUL)

```
cd native
./gradlew --rerun-tasks :protocol:paseo:desktopTest :engine:agents:desktopTest :feature:agents:desktopTest \
  :feature:linux:desktopTest :feature:phone:desktopTest :feature:servers:desktopTest :quality:test \
  :androidApp:testPreviewDebugUnitTest :androidApp:assemblePreviewDebug --continue
```

| Task | Tests | Failures | Skipped |
|---|---:|---:|---:|
| `:protocol:paseo:desktopTest` | 239 | 0 | 0 |
| `:engine:agents:desktopTest` | 225 | 0 | 0 |
| `:feature:agents:desktopTest` | 45 | 0 | 0 |
| `:feature:linux:desktopTest` | 49 | 0 | 0 |
| `:feature:phone:desktopTest` | 42 | 0 | 0 |
| `:feature:servers:desktopTest` | 60 | 0 | 0 |
| `:quality:test` (Konsist, ModuleOwners, graph smoke) | 59 | 0 | 0 |
| `:androidApp:testPreviewDebugUnitTest` | 18 | 0 | 0 |
| `:androidApp:assemblePreviewDebug` | — | APK built (36.8 MB, Metro graph incl. `LinuxStartup`/`KvAgentPrefs`) | |

The touched KMP modules have no `testDebugUnitTest` task; their Android side compiles in the APK. The first
`:quality:test` run caught K3 (`runCatching` in `AgentsWiring.kt`); it is now an explicit `GateNotOpen` catch.
Environment: Android SDK at `/opt/android-sdk` (cmdline-tools 16111833, platform-tools, android-37.1, android-36,
build-tools 37.0.0/36.0.0), JDK 21; a user-level Gradle init script (outside the repo) puts Google's Maven Central
mirror first because `repo.maven.apache.org` answered 429. No device or emulator run.

## How to reach each screen

- Agents hub: Conversations gear → Servers → **Agents** (add/check/save a computer, start a conversation). A1, A2,
  A3, A4, A6 act inside the computer's conversations in the shared chat.
- Linux pages: Servers → **This phone** → **Linux** (shown only with `PlatformCapabilities.builtinLinux`); they say
  Linux is not set up until §3 is resolved. L4 runs at app start with no screen.

## 3. What stays blocked (L1, A5) and why

Binding a real built-in Linux needs three things this unit cannot supply inside its write set or time box:

1. `:android:linux` still carries the S1 shims `io.github.eslamasabry.opencode_mobile.MainActivity`, `R` and
   `BuildConfig` — the app's own FQCNs — so adding it to `:androidApp` is a duplicate-class build failure until
   S3-RTSPLIT replaces them (that module belongs to S1-LINUX / S3-RTSPLIT).
2. The proot/loader `jniLibs` are only in the Flutter tree (`android/app/src/main/jniLibs`), not in the module.
3. There is **no setup engine** (`:engine:runtime` has heat, budget and server lifecycle only; S3-RTENG §7). The
   preview package starts with no Ubuntu, so even a joined runtime would answer "not installed" with nothing that can
   install it. Phone agents (A5: inventory, account probe, sign-in terminal) sit on the same runtime.

The unit's code is ready for the swap (the adapter sketch is in `P12-LINUX.md` §5 item 5); L2–L4 are fixed now so
they do not resurface once the runtime is bound.

## 4. Deviations and notes for the integrator

- Edits outside `protocol/paseo`, `feature/linux` and the two wiring files: one line + one import in
  `androidApp/.../OcApplication.kt` (the start-up call, same pattern as P12-PLATFORM/P12-VOICE).
- A4 returns `Failure.Conflict` (from `PaseoFailureKind.NewChatRequired`); the chat shows its generic plain line.
  A dedicated "Start a new chat" action on that failure belongs to the chat owner.
- A1's guard compares OkHttp's host with ours literally; an IPv6 address typed in a non-canonical form is refused
  rather than risked.
- Saved titles are restored (read); writing new ones (Flutter `saveTitle` from the feed) is not wired because the
  native list reads `sessionPage()` directly.
