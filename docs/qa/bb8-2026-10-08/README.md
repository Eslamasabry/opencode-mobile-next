# BB8 — private phone-agent authentication bridge

Status: SEQUENCE COMPLETE: BB8. Native bridge and final QA runner verified; normal 2195 restored.

The existing `BuiltinPhoneAgents.agentAuthProbe` call now has a native handler.
It returns only validated authentication state, an optional account display name,
and fixed errors. [Native contract](../../design/BB8-native-contract.md) and
[BA frontend contract](../../design/BA1-contract.md) describe the unchanged API.

The coordinator transferred BB8 from BB on 2026-10-08. Base is `d777082c3`
(`feat/genui-fe`); branch is `sol/bd-bb8`. No UI, connection/BA-owned Dart,
generic `PhoneAgentCheckOutput`, account storage, or credential migration edits.
Small `BuiltinLinux` hooks opt the private launch into discarded stderr and
confirmed profile-deletion drainage. `MainActivity` only dispatches the method.
The test-only Gradle flag selects the BB8 runner; normal runner selection stays
unchanged. These shared-file hook changes are recorded in BD-status.md for BB3.

## Native behavior

Requests must have the exact profile/catalog/action/deadline fields and pinned
Dart-authored script. Appended shell, unknown fields, foreign/symlinked homes,
unsupported logout actions, and blocked owners are rejected before execution.
Probe uses a 10-second budget; logout uses 20 seconds. The monotonic budget
includes launch and reserves two seconds for tree drainage plus 400 ms for
capture cleanup. Stdout is ephemeral and capped at 64 KiB; stderr is discarded.
Strict UTF-8/JSON projection rejects unknown/duplicate keys, contradictory state,
invalid account labels and overflow. Overflow remains `invalidResponse` even
when the process subsequently times out, provided drainage is confirmed.

The exact PID/start identity ledger retains children after root exit and retains
failed or incomplete drainage. Signals revalidate each identity immediately;
PID reuse and unattributed same-UID work fail closed without signalling unknown
processes. A retry drains retired work, never a newer active request. Deletion
blocks further private requests before cleanup. Claude stale-lock cleanup checks
cold same-UID ownership and removes only the exact empty profile lock directory;
nonempty locks and symlinks are retained. Generic filtering remains unchanged.

No scripts, process output, account values, credentials or raw exceptions are
written to setup logs, diagnostics, notifications or evidence. Device receipts
record fixed states and whether a Claude label exists, never its value. The only
real logout in the smoke target is fx.

## Deterministic host evidence

The new JVM harness runs production Kotlin with fake processes, inventories,
clocks and latches; it has 38 scenarios and no test sleeps. It validates actual
Dart-generated scripts, request rejection, strict projection/Unicode bounds,
stdout failures/overflow, timeout/logout timeout, retained ownership/deletion,
dead-root children, PID reuse between signals, orphan/failed-start handling,
absolute budgets, unreadable process inventories and cold/foreign lock safety.

The bridge-absent test failed first. Thirteen removed-guard controls each failed
and were restored to a green 34-case harness. Two lock controls then failed and
were restored to a green 37-case harness. The final overflow/timeout regression
failed against the preceding implementation and passed after its fix. All final
38 cases pass as part of the affected 88-test checkpoint. Earlier exploratory
runs that failed to select a test are excluded from this evidence.

| Check | Final result |
| --- | --- |
| Eight affected Dart/native files | PASS, 88 tests (38 auth native, 6 readiness, 2 QA callback) |
| Existing native sign-in harness | PASS, 13 tests |
| Offline device-driver and restoration tests | PASS, 30 tests |
| Pinned analyzer | PASS, no issues (15.1 s) |
| Kotlin detekt | PASS, zero new findings; baseline unchanged |
| Formatting and diff check | PASS |
| Full suite | Coordinator-owned; not run here |

Commands (pinned Flutter/Dart only):

```bash
tool/qa/machine_lock.sh test -- "$FLUTTER" test --concurrency=1 \
  test/bb8_main_callback_native_test.dart test/bb8_native_ready_test.dart test/phone_agent_auth_native_test.dart test/agent_auth_probe_test.dart \
  test/phone_agents_host_test.dart test/phone_agents_legacy_auth_test.dart \
  test/phone_agent_paths_native_test.dart test/phone_agent_run_native_test.dart
tool/qa/machine_lock.sh test -- tool/qa/phone_agent_sign_in_harness.sh
python3 -m unittest tool.qa.test_bb8_agent_auth_device_smoke tool.qa.test_bd9_normal_restore
tool/qa/machine_lock.sh analyze -- "$FLUTTER" analyze
tool/qa/kotlin_static_analysis.sh
```

`FLUTTER` resolves to
`~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
Native/static and release builds use the shared build lock and Temurin 17.

## Signed device qualification

The release AOT target is `integration_test/bb8_agent_auth_device_test.dart`,
version 2204, with `BB8_PROFILE_ID=1790839392073695`. Both app and test APK builds
need `ocBd9Smoke=true` (integration plugin/proguard) and `ocBb8Smoke=true`
(runner selector). An initial APK runner mismatch was rejected by preflight
before any device access; [receipt](packaging-preflight.json). A dedicated runner
flag and offline packaging regression fixed the build configuration.

The [initial device pass](initial-device-pass.json) used the preceding native
candidate. The rebuilt final native candidate's [first attempt](final-attempt1.json)
failed with `flutter_results` and still restored 2195. An
[unchanged retry](native-final-before-handshake.json) passed Claude signedIn,
fx signedOut, fx-only logout and a fresh signedOut probe through the real
merged adapter/channel; [2195 restore](native-final-before-handshake-restore.json)
also passed. These receipts retain both outcomes.

Read-only framework review found a QA registration race: the pinned Flutter
binding reports completion once, and catches a missing native integration plugin
without retrying. The runner registered that plugin after launching Dart. The QA
readiness rendezvous gates all probes until plugin registration is complete;
five fake-clock regressions first failed with the rendezvous absent, then passed.
It retries missing handlers, rejects wrong replies, cancels retries at ten seconds
and ignores late replies. Native result timeout has a distinct fixed token; its
projection regression failed with the new tokens removed and passed restored.
The first handshake-enabled run still timed out before native registration;
[attempt](handshake-attempt1.json) and [diagnostic retry](handshake-attempt2.json)
both restored 2195. Bounded private diagnostics recorded only booleans:
[signals](handshake-attempt2-diagnostic.json) show a running test/app and a missing
integration plugin, with no raw output/account data. Android's synchronous
launch waits for idle/drawing, while Dart awaited readiness before producing a
frame. The target now renders the empty frame first, then awaits readiness before
any auth work. That ordering regression failed first and passes restored.

SDK review also found that `SyncRunnable` marks completion only after its target
returns, without `finally`. A callback exception can strand `runOnMainSync`.
A two-case pure JVM fixture reproduces that incomplete framework completion;
the corrected wrapper catches inside the Runnable and returns a fixed failure
on the instrumentation thread. Cleanup failure cannot produce passing evidence.

The production auth scripts/bridge stay unchanged. The empty-frame prerequisite alone still hit the outer timeout and restored
2195: [receipt](frame-attempt1.json). The final runner removes
`startActivitySync` entirely. It uses a nonblocking launch and a 20-second
ActivityMonitor wait, avoiding Android's unbounded idle/animation synchronization;
cleanup removes the monitor. The existing app APK remains byte-identical because
only instrumentation source changed; the test APK is rebuilt for this correction.
The bounded-monitor run returned `register_plugin` before authentication;
[receipt](monitor-attempt1.json) and [restore](monitor-attempt1-restore.json)
retain that failed outcome and successful 2195 restoration. Registration now
searches recursively for the actual Flutter view instead of depending on an ID,
and reports only fixed view/engine/plugin/ready failure stages. Final proof is
recorded below once the corrected runner and readiness ABI complete.
The recursive-view run reached `register_ready` and restored 2195:
[receipt](view-attempt1.json), [restore](view-attempt1-restore.json). Release
R8 mapping confirmed the readiness channel types/getter had been renamed or
inlined. Narrow keep rules in the existing explicit QA configuration preserve
DartExecutor, BinaryMessenger and the MethodChannel callback boundary. Normal
release configuration is unchanged; both QA APKs are rebuilt for this ABI fix.

The driver validates package/version/certificate, confirms the existing shared
owner privately, and retains one `/home/eslam/Storage/tmp/oc-emulator.lock`
through install, instrumentation and `finally` restoration. Only emulator-5554
is used. Restore is the approved `oc-2195.apk`, installed with `-r -d`, using the
same `1DE5BF08…` signer. No uninstall, clear-data, credential movement/deletion,
real Claude logout, or process-pattern termination occurs. Restoration checks
installed 2195, a live app process, resumed MainActivity and first frame. This is
normal-app launch proof; no separate authenticated OpenCode 2 health claim is
made by this smoke.

```bash
python3 tool/qa/bb8_agent_auth_device_smoke.py --version-code 2204 \
  --profile-id 1790839392073695 \
  --expected-signer 1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C \
  --output build/bb8-device-proof-abi
```

The driver owns the lock; do not wrap it in a second flock.

## Handoff

Native implementation: `e9c38ffc4`. QA readiness/runner: `9c57fd626`.
Final QA-only view lookup and readiness ABI correction: `0ed9ddb1f`.
All are new local commits; no amend, rewrite or push. Evidence is committed
separately. Source hashes and verification controls are in
[state-verification.json](state-verification.json).

The final [device receipt](report.json) matches the app SHA-256 in
[build.json](build.json). Real `BuiltinPhoneAgents` calls through the native
channel passed Claude signedIn, fx signedOut, fx-only logout, then a fresh fx
signedOut probe. Claude account-label presence alone is recorded; no identity
value or credentials appear in evidence. Real Claude was never logged out.
The [normal-app receipt](normal-restore.json) proves 2195 install, matching signer,
live process, resumed MainActivity and first frame inside the same lock.
Final [R8 ABI checks](readiness-abi.json) confirm readiness type and method names
survive the explicit QA build. Both release builds passed, temporary signing and
intermediates were removed, settings restored, and exact owned daemon 2675363
stopped. Only the newest candidate app and matching test APK remain in the
worktree; no APK was copied.

Small shared native hooks are documented for BB3. No frontend/state hook is
needed. An authored Dart script change must update the native digest and pass
the cross-language harness at merge. There are no open BB8 implementation
questions. This is not full model, physical-device or all-agent certification;
full-suite validation remains coordinator-owned. No CI run, PR, tag, patch,
publication, deployment or release is claimed.

## Current restoration target

After the completed BB8 qualification, the coordinator designated normal 2196
(`feat/genui-fe` d777082c, approved 1DE5 signer) for future device runs. BB8 now
uses `/home/eslam/Storage/tmp/oc-apk-share/oc-2196.apk`; the shared restore helper
requires package/installed version 2196 and still installs with `-r -d` inside
the existing emulator lock. Historical 2195 receipts above remain unchanged.

Two updated offline success checks failed against the old defaults, then all
30 driver/restoration tests passed. Tests also reject a stale 2195 APK or
installed version. The supplied [2196 APK preflight](normal-2196-preflight.json)
passed file, package, version and signer verification without invoking adb.
This is preflight evidence, not a new installation or device proof. The
coordinator reports the emulator currently uses OpenCode 1 and has 21% free
space; no emulator access or cleanup was performed for this update.
