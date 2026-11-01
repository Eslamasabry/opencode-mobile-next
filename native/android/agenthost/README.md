# :android:agenthost

Slice 3 (unit S3-AGENTHOST; plan 11 §2.1, §4.1, §5.3 `AgentProcesses`; plan 17 §1.2). The phone agents' host: private
agent processes and per-profile agent homes (BUILTIN-LINUX-52), the Paseo helper's lifecycle (53), the Claude sign-in
pipes and the private auth probe (54). The report is `docs/native/units/S3-AGENTHOST.md`.

Package `io.github.eslamasabry.oc.android.agenthost` (not the Flutter package): the same classes are still copied in
`:android:linux` under `io.github.eslamasabry.opencode_mobile` until S3-RTSPLIT deletes them there, and two copies may
not share a FQCN on one classpath.

## Layout

| Path | What |
|---|---|
| `PhoneAgentAuth*.kt` (12), `PhoneAgentCheckOutput`, `PhoneAgentPaths`, `PhoneAgentInventory`, `PhoneAgentClaudeHome`, `PaseoStaleLock`, `NativeAgentHostAdmission` | **Twins**, byte-equal to the Flutter source except the `package` line. |
| `PhoneAgentHost`, `PhoneAgentChecks`, `PhoneAgentSignIn` | **Twins with a seam**: `BuiltinLinux` → `AgentLinux` (the type of the `linux` field); `PhoneAgentHost` reads the ABI from `linux.primaryAbi` instead of `Build.SUPPORTED_ABIS`; `PhoneAgentSignIn.TESTED_CLAUDE_VERSIONS` gained `2.1.316` (report D5). |
| `SetupDiskSpace.kt` | Twin of `object SetupDiskSpace` from `SetupRunner.kt` (verbatim). |
| `NativeIdleState.kt` | The `Snapshot` data class of `NativeIdleState.kt` (fields, defaults, `holds`) — what the admission reads. |
| `AgentLinux.kt` | The seam the three classes above call (exactly the members they use). |
| `AgentGuestPorts.kt` | CR11-0's `GuestLauncher` / `GuestProcessRegistry` / `ProcessOwner` (plan 11 §5.4 shapes, declared here until the `:android:agenthost → :android:linux` edge is allowed) and the other runtime ports. |
| `AgentProcesses.kt` | Native-authored move of `BuiltinLinux`'s agent block (lines 731–1076, 1804–1811, 2096–2131) over those ports; the production `AgentLinux`, and the process budget hooks. |
| `AndroidAgentHost.kt`, `PaseoHealth.kt`, `AgentHostWiring.kt` | The Outcome surface (what `MainActivity.handleBuiltinLinux` did for the agent methods) and its phone wiring. |
| `src/test/…/*Scenarios.kt` | Ports of the Flutter Kotlin harnesses `test/native/phone_agent_{host,auth,auth_other_owners}_harness.kt` (scenarios unchanged). |
| `src/test/resources/auth-scripts/` | The Dart-authored probe scripts the auth harness reads, written by `tool/extract_auth_scripts.py` from `lib/builtin/agents/agent_scripts.dart`; `PhoneAgentAuthRequest`'s SHA-256 check proves them byte-identical. |

Twin rows (copy base: this repository at `580803d4`, the freeze snapshot; "source commit" is the last commit that
changed the source file) are listed in the report §5.1 for `native/TWINS.md`, which the coordinator owns.

## Checks (T0)

```bash
./gradlew :android:agenthost:testDebugUnitTest     # 9 classes, 137 tests, plain JVM (no Robolectric)
./gradlew :android:agenthost:compileReleaseKotlin
python3 -I native/android/agenthost/tool/extract_auth_scripts.py . native/android/agenthost/src/test/resources/auth-scripts
```

The tests start real `/bin/sh` children and read `/proc`, so they need a Linux host (the PC, the VPS or a container).
