# P1.6a — Claude Code under in-app proot

**Final decision: NO-GO for marking `gate-P1.6a:go`.** The owner-authorized awake ARM64 retry passed Ubuntu, essentials, Node DNS/HTTP and both normal and IPv4 Node downloads. Claude's install/verification step failed because its ARM64 native optional dependency was absent. The phone subsequently entered Dozing, triggering an immediate stop of the probe; only cleanup followed. Paseo, gateway, PTY and authentication remain unverified on ARM64. This is incomplete feasibility evidence, not proof of ARM64 incompatibility. See the [awake retry](#arm64-awake-retry-2026-09-28) and its [measurements](arm64-awake-measurements.json). **The x86_64 feasibility experiment passed:** Claude Code installs and runs under the real app UID, and the existing Dart Paseo gateway can create a Claude agent, submit a signed-out prompt, retrieve the authentication error as conversation history, delete the conversation, and reconnect. This is not a finding that proot cannot run Claude.

The gate definition in `docs/ux-system/revamp/work-units.json` calls for ARM64 and x86_64 emulator evidence. This job explicitly prescribed `OC_API35`, which is x86_64; the installed API 35 image is x86_64 only. No ARM64 claim is inferred from an upstream download existing. The owner's phone was not accessed during that x86_64 run; the separately approved ARM64 follow-up is recorded below. The user explicitly requested **testing without authentication and marking authentication unverified**: no account credentials were used and no successful Claude turn is claimed. Keep P1.6b unavailable until the coordinator accepts the remaining gate evidence; this report does not change the work-unit gate state.

Finish line: produce a measured, reproducible emulator feasibility decision and identify the callable setup/daemon contract. Non-goal: implement the v2 component, redesign screens, authenticate an account, or publish an APK.

## Candidate and isolation

- Source base: `a98d2e8e` on `codex/net` (merged heal work).
- AVD `OC_API35`, Android 15 / API 35, Google APIs x86_64 revision 9, 2,048 MB / 2 cores, headless, `swiftshader_indirect`, no snapshot load/save. Kernel `6.6.50-android15-8-g8adecb593e9b-ab12525588`.
- Built a release-only x64 APK from `tool/qa/p16a_emulator_probe.dart`, with `ocPreview=true`, build number 1, pinned Flutter 3.47.1. A throwaway key was generated because `android/key.properties` was absent; both were removed after the build. No signing material is committed.
- The separate preview package was absent before this run. The existing stable emulator app was not replaced or cleared. The probe target is not imported by `lib/main.dart` and refuses to run unless Android reports `ro.kernel.qemu=1`.
- Probe commands are files inside the preview app's private directory, consumed by its Dart entrypoint and executed via the existing `BuiltinLinux` MethodChannel. There is no exported command endpoint. `adb root` only supplied files and observed `/proc`; the tested executables ran as Android UID **10209**, `untrusted_app`, SELinux **Enforcing**, seccomp mode **2**. This was not an adb-root proot approximation.
- Ubuntu **24.04.5** was downloaded, checksum-verified and unpacked by the existing Kotlin installer. Essentials used the existing `setupPrelude` and `oc_apt_install`; Node used the exact current component pin and extraction/symlink/prefix procedure.
- Every adb invocation selected `-s emulator-5554`. The process limit was unchanged; `dumpsys activity settings` reported `max_phantom_processes=32`.

## Measured results

Times below are the native bridge's monotonic duration unless otherwise stated. Sizes are allocated KiB from `du -sk` in the guest; they are not claimed to be network byte counts.

| Step | Result | Time | Size |
| --- | --- | --- | --- |
| Ubuntu native install | Ready marker created; bridge status ready | about 8–9 s, marker/poll bound | 103,236 KiB immediately after install |
| Essentials: curl, CA certificates, Git, SSH client | Exit 0 | 107.267 s | Final rootfs with all packages/caches: 2,000,488 KiB (1.91 GiB) |
| Node **24.21.0**, npm **11.19.0** | Exact Node version and npm ran | 26.198 s | 235,932 KiB in `/opt/node`; verified archive 58,088,022 bytes |
| Claude Code **2.1.283** via npm | Exit 0; native binary SHA-256 matched upstream | 21.936 s | 236,236 KiB in `/opt/oc-agents`; npm cache 110,676 KiB |
| Paseo CLI/server **0.9.2** | 286 packages installed; native PTY prebuild worked | 136.274 s | Combined Claude + Paseo 805,568 KiB; combined npm cache 429,500 KiB |
| `claude --version`, three launches | Correct version, exit 0 | 51.471 / 25.282 / 26.284 ms within one proot run | Native binary 241,556,664 bytes |
| Supported Paseo foreground launch → HTTP health | Password-enabled, loopback-only listener | 6.282 s first run; 9.762 s and 23.487 s after cold boots, including bridge request/poll | Health alone does not prove Claude authentication |
| Existing Dart gateway | Wrong secret rejected; authenticated daemon handshake; Claude provider; signed-out history; delete; reconnect | Successful focused test about 3 s | See [gateway-proof.json](gateway-proof.json) |
| `node-pty` → `/bin/sh` → known output | Exit 0; exact expected output observed | Helpers batch 607 ms | No compiler/toolchain workaround required |

`runuser -u oc -- id` returned UID/GID 1000 inside proot while outer Android UID remained 10209. Paseo ran as this Linux user with `HOME=/home/oc`, consistent with the existing Termux design; no root permission-bypass flag was used.

### Memory and processes

An outer adb observer sampled `ps -A -o UID,PID,PPID,RSS,NAME` about once a second and selected only preview UID 10209. Counts are **processes, not threads**; child counts exclude the Flutter process and include proot, shells, the Paseo supervisor/worker and Claude. Sampling can miss short-lived peaks. RSS sums double-count shared pages; they are not PSS or peak allocator requirements. No fake proot `/proc` data was used.

| Phase | Maximum sampled app-owned children | Maximum summed RSS, including Flutter |
| --- | ---: | ---: |
| Essentials | 13 | 297.7 MiB |
| Node install | 5 | 217.5 MiB |
| Claude install | 5 | 333.0 MiB |
| Paseo install | 3 | 466.6 MiB |
| Standalone login / signed-out prompt | 7 | 288.8 MiB |
| First gateway prompt | 10 | 1,000.2 MiB |
| Repeated prompt plus native-helper observation window | 11 | 1,096.0 MiB |

An idle snapshot with one retained signed-out agent had 7 children plus Flutter. Its summed PSS was about 469 MiB (plus about 19 MiB SwapPss); the raw per-process values are in [measurements.json](measurements.json). The guest reported 2,019,400 KiB total RAM. These numbers are for signed-out initialization only, **not** an authenticated turn or concurrent OpenCode + AI Team + Claude. The nominal 32-child Android limit is shared with other phantom processes; the remaining count is not a private allocation for this component. Full tool use can spawn more shells, Git processes and MCP servers. Anthropic currently documents 4 GB+ RAM as a system requirement; success in this 2 GB emulator does not establish a supported 2 GB product configuration.

## Authentication: what was and was not proven

- `claude auth status` returned exit 1, `loggedIn:false`, `authMethod:none`, both for the initial CLI home and the `oc` home used by Paseo.
- With `BROWSER=/bin/false`, `claude auth login` generated a `claude.com` sign-in URL and displayed a paste-code route. It was stopped by an owned 20-second timeout while awaiting the user. OAuth URLs/state were kept private and are not included in evidence.
- A direct `claude -p` stream emitted initialization, an assistant authentication error, and a result with `is_error:true`; exit 1. The result's subtype was `success`, so consumers must not equate that subtype with successful model execution.
- Through the app gateway, the signed-out response appeared as assistant history containing the login requirement. It was **not** a `session.error` event or `transport.lastDaemonError`. The strengthened probe asserts the actual history instead of treating an idle event or an accepted request as a successful turn.
- Opening the URL on another device, completing approval, exchanging the pasted code, persisting credentials, restarting authenticated, and running an actual model/tool turn are **unverified by explicit user direction**. Link generation does not prove the whole sign-in works without a browser on the phone.

## What breaks and what works under proot

1. **Existing launch command breaks with 0.9.2.** The app's current `paseo start --foreground --listen ... --no-relay --no-web-ui --no-inject-mcp` exits with the CLI's removed-`--listen` error. This is CLI contract drift, not a proot execution error. `start --help` no longer offers those deployment flags.
2. **Supported replacement was verified:** `paseo daemon run --home ...`, with deployment environment overrides, started successfully. It must remain owned by the native service so stopping the service kills the whole process tree. Do not start an unowned detached daemon.
3. **The current CLI is not Node-only.** npm installs the same platform native Claude executable. Node is required by npm and Paseo, but does not make native execution problems disappear. Upstream `2.1.112` was the last inspected `cli.js` package; `2.1.113` switched to native packages. No supported downgrade to the older JS line was established for this SDK.
4. Paseo 0.9.2 pins Agent SDK **0.3.246** and resolves `claude` from PATH into `pathToClaudeCodeExecutable`. The standalone **2.1.283** executable was used; its upstream manifest explicitly lists SDK 0.3.246 as tested. npm still installs the SDK's separate native payload (CLI 2.1.246), which consumes disk even when bypassed. Budget both payloads.
5. Foreground npm scripts, `UV_USE_IO_URING=0`, the existing proot options and the bundled native PTY prebuild were sufficient here. No SELinux relaxation, seccomp removal, process-limit increase, root Android execution of Claude, alternate kernel, or system compiler was needed.
6. Bubblewrap/namespaces, sandboxed tool execution, MCP workloads, long-lived background operation, Android service timeouts, simultaneous servers, Claude/Paseo ARM64 native packages and an authenticated Claude turn were not validated. Do not derive any of these from `--version` or daemon health.

### Exact launch used for the successful experiment

This is a replay recipe for the experiment, **not an approved P1.6b component manifest**. The gate remains closed, so no shipping check/install/remove contract is asserted.

```sh
export UV_USE_IO_URING=0 DISABLE_AUTOUPDATER=1
export NODE_OPTIONS=--dns-result-order=ipv4first
export PASEO_DICTATION_ENABLED=false PASEO_VOICE_MODE_ENABLED=false
export PASEO_LISTEN=127.0.0.1:6767
export PASEO_RELAY_ENABLED=false PASEO_WEB_UI_ENABLED=false
# Read a newly generated, mode-0600 daemon secret; never echo it.
export PASEO_PASSWORD=$(cat /home/oc/.oc-paseo/password)
export HOME=/home/oc
exec runuser -u oc -- env HOME=/home/oc \
  paseo daemon run --home /home/oc/.oc-paseo
```

The fresh home had no MCP-injection override; 0.9.2 defaults `daemon.mcp.injectIntoAgents` to false. MCP's own route still mounts; route presence does not mean automatic agent injection is enabled. The experiment's actual launch redirected stdout/stderr to a private file so the native bridge could not put daemon output in logcat.

Exact install commands after the current Ubuntu/essentials/Node components:

```sh
export NODE_OPTIONS=--dns-result-order=ipv4first UV_USE_IO_URING=0
npm install -g --foreground-scripts --no-fund --no-audit \
  --prefix /opt/oc-agents --cache /var/cache/p16a-npm \
  @anthropic-ai/claude-code@2.1.283
ln -sf /opt/oc-agents/bin/claude /usr/local/bin/claude
npm install -g --foreground-scripts --no-fund --no-audit \
  --prefix /opt/oc-agents --cache /var/cache/p16a-npm @getpaseo/cli@0.9.2
ln -sf /opt/oc-agents/bin/paseo /usr/local/bin/paseo
```

These pin the top-level packages, not all transitive npm ranges. A future GO manifest must freeze and verify that dependency graph, provide trustworthy download-byte accounting, and validate check/install/remove on both ABIs. Preserve projects and sign-in on ordinary removal; forgetting sign-in must be a separate explicit action. Do not reuse a heuristic provider `available` state as proof of sign-in.

Checksums and observed versions are recorded in [candidate-pins.json](candidate-pins.json). The awake ARM64 retry verified and executed Node. Claude ARM64 and Paseo hashes remain upstream/source metadata only; their ARM64 binaries were not executed.

## Reproduce and verify

Use the emulator boot recipe in `../emulator-boot-2026-09-27/README.md`. Build only the explicit preview QA target:

```sh
FLUTTER="$HOME/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter"
"$FLUTTER" build apk --release --build-number 1 --target-platform android-x64 \
  --target tool/qa/p16a_emulator_probe.dart --android-project-arg=ocPreview=true
adb -s emulator-5554 install build/app/outputs/flutter-apk/app-release.apk
adb -s emulator-5554 shell am start -n \
  io.github.eslamasabry.opencode_mobile.preview/io.github.eslamasabry.opencode_mobile.MainActivity
```

The probe accepts one `request.json` at a time under `/data/user/0/io.github.eslamasabry.opencode_mobile.preview/files/p16a`, after the `ready` marker. Publish requests atomically from an adb staging file; `id` is echoed in `response.json`. Operations: `status`, `install` (poll until Ubuntu is ready), `run` (`script`, bounded `timeout` seconds), `start` (owned service `p16a`), `stop`. The direct-run bridge logs output: redirect all login/daemon output into private guest files and export only allowlisted boolean/numeric summaries. No real credentials belong in probe requests.

For the gateway proof, forward loopback port 16767 to emulator port 6767. Save only the **newly generated test daemon secret** in a mode-0600 host scratch file, then run:

```sh
adb -s emulator-5554 forward tcp:16767 tcp:6767
P16A_SECRET_FILE=/private/scratch/daemon-secret \
P16A_REPORT_FILE=/private/scratch/gateway-proof.json \
  "$FLUTTER" test --concurrency=1 tool/qa/p16a_paseo_probe_test.dart
adb -s emulator-5554 forward --remove tcp:16767
```

The test uses the production `PaseoGateway`/`PaseoTransport`; it emits only fixed classifications, event type names and counts. It has no auto-allow handler and never prints daemon bodies or secrets. Without the explicit environment variables it skips.

## Remaining gate work / hand-off to Claude

- Repeat the native app-UID install, non-root Linux user, PTY and signed-out gateway proof on ARM64. Upstream ARM64 artifacts are available, but availability is not execution evidence.
- Resolve the 0.9.2 launch contract in the eventual component/Termux integration. The currently checked-in command is broken; do not simply copy it into built-in setup.
- Once authentication is explicitly authorized in a later job, verify browserless-phone completion, persistent sign-in, one actual model/tool turn, reconnect and real peak memory/process use.
- P1.6a is the signed-out runtime-feasibility gate; successful authentication remains separately unverified by explicit instruction. A future GO must include the checksum-backed, dependency-locked P1.6b check/install/remove handoff, including the Paseo 0.9.2 launch correction. This attempt remains NO-GO, so no new component manifest is issued and no Claude setup row is enabled.
- Future UI states: installed is distinct from signed in; daemon listening is distinct from Claude ready. Use plain copy such as “Sign in to Claude”, “Claude is starting”, and “Claude could not start”, with diagnostic output behind Details. The first signed-out turn must not appear successful merely because its session became idle.

## Evidence and validation

- Toolchain note: this host has Ubuntu OpenJDK 17.0.20 rather than the repository-preferred Temurin vendor. The pinned Flutter was used; no native code was changed. This is an explicit build-environment deviation, not a claim that the Temurin gate was run.
- Release probe build passed: pinned Flutter, x64, release engine, 369.3 s Gradle build. APK size/SHA-256 are in `measurements.json`; this is a temporary preview test APK, not an owner-phone release.
- The strengthened signed-out gateway test passed again after host recovery (5 s; current test source). [gateway-proof.json](gateway-proof.json) is the saved result. It asserts wrong-password rejection, an authentication error in actual conversation history, deletion and reconnect. Daemon handshake was 267 ms in this run.
- Two attempted repeats lost their target during host instability and failed the wrong-password assertion because the emulator was absent. They are not passing coverage. The first emulator log reported QEMU CPU/main-loop hangs; the host journal recorded a global OOM event, and a later interruption required a host restart. The exact cause of the first QEMU exit was not proven. The post-recovery passing rerun supersedes those interrupted attempts, without treating them as Claude/proot failures.
- Native `stopService('p16a')` completed in 2,250 ms. An outer process check then found **zero** remaining UID-10209 children (Flutter excluded). This proves ownership/stop for the observed tree, not long-term Android background service eligibility.
- Removed the port forward and the newly created preview app, including its Ubuntu, npm cache, OAuth scratch output and synthetic daemon state. Removed the host's ephemeral daemon-secret file; throwaway signing properties/key were already absent. Shut down the final owned emulator with `adb -s emulator-5554 emu kill` and verified its PID exited. The earlier owned emulator/sampler PIDs were absent after the host crash. No pattern kill was used and no other emulator or physical phone was operated during the x86_64 run.
- Dart 3.10 formatting check passed for both QA files. Pinned `flutter analyze --no-pub` passed with no issues (20.9 s). `flutter test --no-pub --concurrency=1 test/kit_ratchet_test.dart test/redaction_test.dart test/ui_glossary_test.dart test/no_raw_error_text_test.dart` passed **76 tests** (25 s). JSON parsing, local evidence links and staged whitespace checks passed.
- No product Dart/Kotlin/UI code, package versions, gate state, credentials or `COMMIT_MSG.txt` are part of this change. No full product suite was claimed for this QA-only spike; no APK was delivered, pushed or released.

## ARM64 owner-phone follow-up (2026-09-28)

**Initial attempt: NO-GO; superseded only where explicitly measured by the awake retry below.** This job explicitly authorizes the owner's physical ARM64 phone for the isolated preview probe only, replacing the earlier emulator-only scope. Authentication remains unverified. Finish line: repeat the signed-out native/gateway measurements, remove the preview, verify the stable package is unchanged, and issue a bounded feasibility decision. Non-goals: product integration, sign-in, device-setting changes, or release delivery.

### Isolation and build

- Exact adb target: `adb-XXXXXXXXXXXX-xxxxxx._adb-tls-connect._tcp`, selected with `-s` on every invocation. Only `io.github.eslamasabry.opencode_mobile.preview` was installed or operated. No `run-as`, adb root, stable-app data access, or another app's data access was used.
- Baseline stable package: versionCode **2055**, versionName **1.0.44**, lastUpdateTime **2026-09-27 20:59:37**. Preview was absent before installation.
- Android **15 / API 35**, `arm64-v8a`, kernel `6.1.145-android14-11-g11c274d0441f-ab14259673`, SELinux **Enforcing**, system MemTotal **15,715,596 KiB**, unchanged `max_phantom_processes=32`.
- Candidate base `01c3e7a2b13651b688b59d639c7122c34be861fc` on `codex/arm64`, plus the QA-only physical transport changes. Pinned Flutter 3.47.1; Ubuntu OpenJDK 17.0.20 (same vendor deviation as the x64 build). Release ARM64, `ocPreview=true`, versionCode 1, temporary signing key because `android/key.properties` was absent. The key and properties were deleted after the build.
- Built under `tool/qa/machine_lock.sh build`: Gradle **406.8 s**, whole Flutter command **417.188 s**. APK **48,585,889 bytes**, SHA-256 `23dae5d9a1b3d30c2fd13f8483ec4a28e6f3da4176946d768239e5467d689c69`. APK metadata verified the preview package ID and ARM64-only payload before installation. Wi-Fi streamed installation: **12.415 s**.
- The release probe is not debuggable. Its opt-in physical mode uses an authenticated HTTP client over an explicitly reversed loopback port; it exports no app command endpoint. It creates only the preview sandbox, rejects non-ARM64/emulator physical targets, and attempts to stop its owned service when transport ends. [Phone host helper](../../../tool/qa/p16a_phone_host.py) keeps the new test tokens in a mode-0700 scratch directory, never in source. Default emulator file-polling mode remains available.
- The QA target intentionally renders a blank screen. The first readiness attempt exposed a host-helper chunked-body bug; the helper was fixed and its chunked JSON regression check passed. The same APK then reported ready after a preview-only restart.

### Initial ARM64 results and unverified measurements

| Step | Observed ARM64 result |
| --- | --- |
| Ubuntu native installer | Ready in **13.133 s** (request/poll bound); native installer verifies its pinned ARM64 archive before extraction |
| Fresh rootfs | **111,331 KiB**, Android `du -sk` over the preview's own rootfs |
| Actual Android execution context | UID **10430**, `untrusted_app`, seccomp **2**; no Android root execution |
| Essentials | Exit 0 in **152.415 s**, Git **2.43.0** |
| Essentials sampled maximum | **12 children**, **319,188 KiB summed RSS**, including Flutter; approximately one sample/second, with occasional gaps |
| Node unchanged installer, attempt 1 | Exit **6**, **23.088 s**, repeated `Could not resolve host: nodejs.org`; zero downloaded bytes reported |
| Node unchanged installer, attempt 2 | Exit **6**, **22.785 s**, same failure |
| Bounded guest network diagnostic | IPv4 lookup of Node/npm/Ubuntu domains and `curl -4` HEAD to the pinned Node URL succeeded; this does not establish an IPv6-specific cause |

The phone was subsequently observed in **Dozing**, with `mDeviceIdleMode=true`; adb remained connected. Preview polling had stopped and no preview-owned native children remained. An IPv4-only Node request stayed queued and was cancelled before delivery: **the variant did not execute**. An awake foreground repeat was requested but not completed before this attempt was closed and the preview removed. Retest the unchanged installer while awake before attributing its failures to IPv6 or changing the install contract. No screen-timeout, battery, network, phantom-process or other device setting was changed. Doze is a possible confounder, not a proven cause of the earlier DNS failures.

Process observations are preview-UID-only, not other-app data. They are sampled process counts, not threads or guaranteed peaks; RSS double-counts shared pages. A sampler gap after the failed sequence means the second Node attempt has no claimed process/RSS coverage. Waiting-phase samples are not installation measurements. The phone's RAM and foreground state differ from the emulator; no background or concurrent-server capacity claim follows.

In this initial attempt, the following requested measurements could not be reached: installed Node version/archive checksum/size; Claude and Paseo ARM64 versions/checksums/install sizes and times; three Claude launches; guest non-root `oc` execution; signed-out auth/prompt behavior; Paseo health/start/restart/owned-stop; the Dart gateway test; node-pty; daemon/prompt process and RSS peaks; idle PSS/SwapPss; and final full rootfs size. No ARM64 result is inferred from the x86_64 evidence or upstream hashes. Authentication was not attempted on the phone and remains unverified. The Dart gateway test was not run against an unavailable daemon.

### Cleanup and verification

- Removed only `io.github.eslamasabry.opencode_mobile.preview`; `adb uninstall` returned **Success**. The required `pm list packages | grep -Fx` check returned no preview package. A final UID-10430 process check returned **zero processes**. Before uninstall, only Flutter remained; no native guest children or Paseo daemon were running.
- Removed the specific port-18761 reverse. A Paseo port forward was never created. Preview uninstall removed its Ubuntu, packages, QA files and private state. The owner did not need to uninstall or clear the stable app.
- Stable package remains present at **versionCode 2055**. The before/after metadata comparison was exact: versionName, versionCode, firstInstallTime, lastUpdateTime and APK path all matched. No stable-app data was read, cleared or changed.
- Removed host scratch files, new test tokens, the temporary APK/build outputs and signing material. Stopped only exact owned host PIDs; no pattern kill, device reboot or settings mutation was used.
- Pinned `flutter analyze --no-pub` passed with no issues (**111.0 s**) under the shared analyze lock. QA Dart formatting, Python syntax, host authentication/idle behavior, chunked JSON handling and whitespace checks passed. Full product-suite coverage is not claimed for this docs/tools probe.

### Physical-probe replay contract

Use a new private scratch directory and `tool/qa/p16a_phone_host.py <scratch>` to create `defines.json` and a fresh test-daemon secret. The directory must be mode 0700; generated secret files are mode 0600. Pass the definitions only to this explicit QA target:

```sh
tool/qa/machine_lock.sh build -- "$FLUTTER" build apk --release \
  --build-number 1 --target-platform android-arm64 \
  --target tool/qa/p16a_emulator_probe.dart \
  --android-project-arg=ocPreview=true \
  --dart-define-from-file=/private/scratch/defines.json
```

Inspect the APK package/ABI before installation. Every adb command must select the owner's approved exact serial. Reverse `tcp:18761` to `tcp:18761`; launch only the explicit preview activity. The app posts `/ready`, polls `/request`, and posts `/response` with the new bearer token. Atomically place one request JSON at a time in the private host scratch directory; IDs use letters, digits, `_` or `-`. Existing `status/install/run/start/stop` operations are preserved; `measure` reads only preview-rootfs allocated size and the app's own UID/seccomp/SELinux context. Responses are written to `<id>.json` privately. Native `run` still logs stdout, so redirect all authentication/daemon output and emit only allowlisted summaries. This is not an exported app server.

For an owner-approved awake retry, check `dumpsys power` before every phone step and during long-running commands. Require `mWakefulness=Awake` and `mDeviceIdleMode=false`; stop the probe immediately and report if either changes. Keep the preview visible without changing device settings or sending wake commands. If polling ends, a queued command is not execution evidence; check it was not consumed before a controlled preview restart. Do not replay an in-flight or lost-response command blindly. Stop on adb disconnection. At the end remove the preview, the specific reverse/forward, private scratch/build artifacts and throwaway key, then repeat the stable-package metadata comparison. A future successful awake run must still measure every skipped item above; it must not reuse this partial attempt as passing coverage.

## ARM64 awake retry (2026-09-28)

**Final verdict: NO-GO.** The owner woke and unlocked the phone and authorized this repeat at approximately 03:30 Asia/Dubai. Node now passes; Claude's native ARM64 package was not installed, so the full runtime gate remains incomplete. No P1.6b component manifest is issued. The **dark/blank preview screen is expected**: this QA entrypoint deliberately renders no visible UI while its private command loop runs.

### Repeat isolation and build

The previous temporary APK had been removed, so the unchanged QA target at `d723f0c4` was rebuilt under `tool/qa/machine_lock.sh build`, using pinned Flutter 3.47.1, release ARM64, `ocPreview=true`, build number 1 and a fresh private transport token. The whole Flutter command took **323.592 s** (Gradle **312.5 s**). APK: **48,585,885 bytes**, SHA-256 `4d8ea08da49a66da67b71ce60efa9eeb514a58642b6287308d75a712ff663867`. Preview package ID and ARM64-only payload were checked before streamed installation, which took **13.368 s**, including the preinstall awake check. The temporary signing key and `android/key.properties` were removed after building. The OpenJDK vendor deviation documented above still applies.

Every adb call selected the exact approved serial. Preview was absent before reinstall; the new preview UID was **10476**, seccomp **2**, SELinux `untrusted_app`. The stable app was accessed only for the explicitly requested package metadata comparison. No app data outside preview was read.

Before each phone action/request, the host checked connection state and `dumpsys power`. During commands it checked roughly every two seconds, alongside the once-per-second preview-only process observer. Every completed measurement below began and ended with `mWakefulness=Awake`, `mDeviceIdleMode=false`.

### Awake measurements

| Step | Result | Duration / size |
| --- | --- | --- |
| Ubuntu native install | Ready; existing native checksum verification | **13.298 s** request/poll bound; **111,331 KiB** fresh rootfs |
| Essentials | Exit 0; Git **2.43.0** | **57.748 s** |
| Node URL lookup / HTTP HEAD | Default and explicit IPv4 both exit 0 | **1.326 s** combined |
| Unchanged Node installer | Exit 0; Node **24.21.0**, npm **11.19.0** | **28.163 s**, **208,349 KiB** `/opt/node` |
| Explicit IPv4 Node repeat | Exit 0; same versions, archive hash and installed size | **29.883 s** |
| Claude **2.1.283** install/verification | Step exit **1**; native optional dependency absent | **19.227 s**; installed size/checksum unverified |

Both Node runs downloaded **57,824,078 bytes**, SHA-256 `724282c3b43aec998aa9527380465b45d229e021b58035f5f4f63095eabfe5d5`. The successful unchanged installer supersedes the earlier DNS failure as the latest observed result. It does not establish the cause of that earlier failure or prove an IPv6-specific fault.

Claude npm output reported one package added and showed that `install.cjs` ran. That script reported `@anthropic-ai/claude-code-linux-arm64` missing; the subsequent CLI verification failed with `claude native binary not installed`. npm also emitted an `allowScripts` warning, but the observed postinstall execution means that warning alone does not explain the missing native dependency. No native Claude version or checksum was obtained, and the reason the optional package was absent was not established. No install workaround or package downgrade was attempted.

| Completed phase | Samples | Maximum sampled children | Maximum summed RSS, including Flutter |
| --- | ---: | ---: | ---: |
| Essentials | 46 | 11 | 316,808 KiB |
| Node, unchanged | 18 | 6 | 287,308 KiB |
| Node, IPv4 | 24 | 5 | 262,416 KiB |
| Claude failed install | 15 | 3 | 354,372 KiB |

Counts use only preview UID 10476, exclude Flutter from children, and include it in RSS. Phase samples are bounded by request start and response completion, excluding later idle time. Maximum inter-sample gap was **4.074 s**. These observed counts are below the previously read, unchanged `max_phantom_processes=32`; the limit is shared and does not reserve capacity for this component. Sampling can miss peaks and RSS double-counts shared pages. Paseo/Claude-prompt capacity, idle PSS and final full rootfs size remain unmeasured.

At **03:40:57 Asia/Dubai**, the observer detected `mWakefulness=Dozing` with `mDeviceIdleMode=false`. The stop sentinel cancelled any queued request and prohibited further probe commands. The Claude failure had already completed while awake; it is not attributed to this later Dozing observation. No later install, diagnostic, auth, gateway or PTY step ran. There was no settings change, wake command or wait for another wake cycle; only the authorized removal and metadata verification followed.

Paseo install/version/checksum/health/start/restart/owned-stop, the Dart gateway, node-pty, non-root `oc` execution, three Claude launches and signed-out auth/prompt behavior remain **unverified on ARM64**. No account sign-in was attempted; authentication remains **unverified**. Successful x86_64 evidence is not substituted for these missing measurements.

### Retry cleanup and validation

Preview uninstall returned **Success**. The required `pm list packages | grep -Fx package:io.github.eslamasabry.opencode_mobile.preview` check found nothing, and a UID-10476 process check found **zero processes**. The specific reverse on port 18761 was removed; no Paseo forward or daemon had been created. Cleanup recorded power state before each action even though the probe had stopped for Dozing.

The stable app remains present at **versionCode 2055**. Its version, first-install and last-update timestamps, and APK path exactly match the before snapshot. No stable app data was read or changed. Removed preview guest data through uninstall, host private files/tokens, temporary APK/local build outputs and signing material. The sampler stopped on Dozing; only the exact owned host-server PID was terminated.

The release build passed. QA source files are unchanged from the prior analyzed commit; no new Flutter analyzer/full-suite run is claimed for this documentation-only update. JSON parsing, local evidence links and whitespace checks passed. Sanitized machine-readable evidence is in [arm64-awake-measurements.json](arm64-awake-measurements.json). A future owner-authorized awake retry must diagnose the missing native optional dependency and complete the remaining measurements before GO.

## Off-phone installer follow-up

The [npm diagnosis and prepared native installer fix](installer-fix.md) bypass npm optional-package selection while retaining Claude 2.1.283 and its official checksums. The x64 emulator regression passed; ARM64 static checks passed, but ARM64 execution and gate GO remain pending. The phone was not accessed for this follow-up.

## Primary sources checked on 2026-09-28

- [Claude setup and current hardware/npm requirements](https://code.claude.com/docs/en/setup)
- [Claude authentication](https://code.claude.com/docs/en/authentication)
- [Claude 2.1.283 release manifest and SDK compatibility](https://downloads.claude.ai/claude-code-releases/2.1.283/manifest.json)
- [Claude 2.1.283 npm metadata](https://registry.npmjs.org/@anthropic-ai%2fclaude-code/2.1.283)
- [Last inspected JS CLI package, 2.1.112](https://registry.npmjs.org/@anthropic-ai%2fclaude-code/2.1.112) and [native transition, 2.1.113](https://registry.npmjs.org/@anthropic-ai%2fclaude-code/2.1.113)
- [Paseo CLI 0.9.2 package](https://registry.npmjs.org/@getpaseo%2fcli/0.9.2)
- [Paseo server 0.9.2 package](https://registry.npmjs.org/@getpaseo%2fserver/0.9.2)
- [Agent SDK 0.3.246 package](https://registry.npmjs.org/@anthropic-ai%2fclaude-agent-sdk/0.3.246)
- [Paseo protocol compatibility](https://github.com/getpaseo/paseo/blob/v0.9.2/docs/protocol-compatibility.md)
