# BYO host signer backend verification — 2026-10-02

Candidate: `codex/byo-host-signer`, base `071a76f1` + Task B2 diff. Backend only,
`OC_BYO_HOST=false` by default. No UI, physical-device proof, publication, provider
resources or live host changes. Contract: [BYO host](../design/byo-host-contract.md).

Pinned Flutter 3.47.1:
`~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
`flutter pub get` ran once; format used pinned Dart `--language-version=3.10`.
Final full-source machine-locked `flutter analyze --no-pub`: **no issues**.
Final serial command: **89 passed** (BYO directory + profile persistence/reset).

```sh
tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 \
  test/byo_host test/profile_store_test.dart test/profile_sign_in_reset_test.dart
python3 -m unittest discover -s test/byo_host -p '*_test.py'
```

Python: **35 passed**, fake local HTTP/child only, no resource API calls.
Agent protocol harness: **112 checks passed**, JCA fake signer, public-key and
DER/SSH signature verification, malformed/oversized frames and auth purposes.

```sh
kotlinc android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ByoHostAgentProtocol.kt \
  tool/qa/byo_host_agent_protocol_test.kt -include-runtime -d /tmp/byo-host-agent-protocol.jar
java -jar /tmp/byo-host-agent-protocol.jar
tool/qa/machine_lock.sh build -- ./android/gradlew -p android :app:compileReleaseKotlin --no-daemon
```

Kotlin: **BUILD SUCCESSFUL**, JDK17. Ignored Gradle wrapper restored from pinned
Flutter artifacts. Initial unsupported Os.unlink call fixed to File.delete;
compile rerun passed. Existing repository/plugin warnings remain. No signing,
APK delivery or release. `android/app/build` removed/confirmed absent afterwards.
Initial analyzer style findings fixed without ignores. One erroneous Python
module-path invocation corrected to discovery; only successful final runs counted.
Child revoke test deadline widened to 10s for fsync on the shared disk; successful
receipt and unchanged child PID assertions retained. Full repo suite not run.

Reproducible bundle build used public frozen OC1 1.18.32 archives, never executed
those binaries, verified upstream digests, then byte-compared two builds per arch.
Python 3.12.3/zlib1.3; pipeline requires Python3.12 and fails closed on digest drift.
`build_release.py verify` passed for app **1.1.0+51**, bundle **1.1.0**:

| Archive | SHA-256 |
|---|---|
| amd64 | `014d92fefcbb520af55af5bf6e3290715c47030f0c1a9b4aa5c91ddc44ff9118` |
| arm64 | `691396beda4d71cb5c5cbd43951260e56d67fb5b3543a39a8be8dd46a757def0` |

The release ledger and compiled Dart pins match; unknown app versions return null.
Artifacts remain under `/tmp/byo-host-final`, outside Git. No workflow execution,
tag, draft creation, asset upload, push or publication occurred. SHA pin review
and future app-version/tag selection remain the owner's release responsibility.
Shell syntax, Python compilation, Markdown local targets and diff whitespace pass.

No-private-key-file evidence: AndroidKeyStore generator creates the identity;
only public certificate/alias crosses Flutter; Keystore handle signs internally.
No private serialization/export method exists. Fake runner captures commands and
written files: public `device.pub` only, IdentityFile=none + private IdentityAgent,
no device ssh-keygen invocation/private key file; imported keys fail before writes.
This source/fixture proof is not physical hardware or SELinux/proot validation.
Host installer may generate an unrelated temporary sshd policy-probe key on the
host; it is not the phone identity and never transfers to the phone.

Required owner proof before enabling: real Keystore signature via built-in Linux
socket on API26+ and newer Android, inspect files/Logcat/argv with dummy sentinels,
reboot/restart and deletion/reset, actual tailnet routing (range checks alone do
not prove VPN membership), independent host pin/change rejection, manual linger
and sshd policies, selected-port private listener gate plus all alternate ports,
real OC1 session survival/two-phone revoke, and localhost port allocation race.
Same-UID runtime compromise can use an active signing oracle; destination binding
is not implemented, hardware backing is device-dependent. No backend of ours.
