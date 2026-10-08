DONE: native BB9 cold-start rollback and surviving-installer qualification passed on 2026-10-08.

# BB9 — cold recovery after a failed pinned activation

The [final whole locked session](host-device-orphan-final-native-ack-restore.txt)
passed on private release/AOT QA2211. The actual original installer root and
leader both survived Main's exact SIGKILL. The next normal Main startup drained
those owned survivors and restored the original good OpenCode 2 executable and
its command link **before** read-only native Verify. Native cleanup then removed
the fixture, journals, writer receipt, export and producer metadata and restored
the original retained-good backup. No helper-only rollback substitutes for this
product startup proof.

Original native preferences, the active-profile pointer and typed owner policy
and recovery markers were restored. The original metadata comparator ran after
that restoration and passed. Real explicit Start rearmed fresh kernel ownership
and authenticated OpenCode 2 Connected on the QA app. Normal app 2195 was then
reinstalled with the same authorized signer inside the same emulator lock; its
installed hash/version were verified and app data was preserved. The final
[normal-app screenshot](restored-normal-2195.jpg) shows startup in progress;
Connected qualification belongs to the preceding QA restoration, not this frame.

## Final candidate and checks

- Release target QA2211 SHA-256
  `5b7ecd62ddbec636cb811c0b5ae764fbfb8a752882deca3787df6309b3ebcce8`;
  [artifact](target-producer-title-artifact.json),
  [build](target-producer-title-build.txt),
  [exact owned cleanup](target-producer-title-owned-processes.json).
- Matching private runner SHA-256
  `34d4796ac310e61fb93e546e3e43d43b94310016194ef58a1739425cc3cf3eff`;
  [artifact](runner-producer-title-artifact.json),
  [build](runner-producer-title-build.txt),
  [exact owned cleanup](runner-producer-title-owned-processes.json).
  Both use signer `1DE5BF08…`. Source hashes are frozen in
  [candidate](producer-build-candidate.sha256). Intermediates were removed,
  owned Gradle processes exited and only the newest APKs remain.
- [Focused native compile/JVM](native-visibility-build.txt): 69 tests,
  zero failures/errors = ownership35 + visibility13 + filesystem21;
  [result](native-visibility-result.json). Four removed policy/integration
  guards failed and byte-restored pure ownership/visibility48 passed:
  [red](visibility-jvm-red.txt), [restored](visibility-jvm-restored.txt).
- Affected Dart34 and analyzer clean: [restored](dart-final-restored.txt),
  [analyzer](analyze.txt). Four removed integration controls failed as expected.
- [Latest host checks](host-final-native-ack-focused.txt): 211 passed = main57 +
  external35 + ordinary service66 + bootstrap5 + final metadata14 + BB3 restore34.
  Final native-drain guards: [red](host-service-native-drain-red.txt),
  [restored](host-service-native-drain-restored.txt). Metadata guard controls:
  [red](host-final-metadata-red.txt), [restored](host-final-metadata-restored.txt).

The private auxiliary producer is absent from the source production manifest and
is disabled by the normal build flag. It uses an ordinary app context without
security-policy, cgroup or credential changes. Native admission still requires
kernel identity, UID, ancestry, nonce, full inventory, signal capability and a
committed durable receipt before update permission. Missing recorded processes
require independent exact signal-zero ESRCH; missing `/proc` reads are insufficient.

The real interrupted activation proof is OpenCode 2. Claude/Paseo have compiled
fixed adapters and focused journal tests; separate live interrupted downloads,
account/model round-trips, power-loss durability and an all-agent/device matrix
are not claimed. No full Flutter suite, CI, release or publication was run.
BB4/BB5/BB7 continue after this item; BB8 belongs to BD2 and BB6 is skipped.

## Historical checkpoints — superseded by the final pass above

The following earlier findings and failures remain unchanged as historical
evidence. Their then-current blocker statements do not describe the final candidate.

IN PROGRESS: ordinary app-context QA producer qualification; mandatory orphan proof remains open.

# BB9 — core cold recovery passes; orphan qualification remains open

Current checkpoint (2026-10-08): BB9 source integration, focused checks,
normal QA2205 cold-command-link/queue/peer and real Android filesystem sessions
pass. Mandatory orphan survival/drainage remains **UNQUALIFIED**. After fixing
the QA launcher and reflection, the private QA2206 run-as producer reached native
admission but was refused as `bb9_external_identity_file_unavailable`. A second
same-lock run observed both exact processes still alive, with original protected
argv/UID/nonce, after native refusal. No permit was issued. The exact Android
access-denial cause is unknown; no context, cgroup or security policy changes
were attempted. See [fixed observations](native-live-refusal-result.json),
[session](host-device-orphan-native-live-refusal-witness.txt) and
[reproducible fixed-projection witness](native-refusal-witness-session.py).

Exact actor drainage, native fixture cleanup, original native preferences and
owner/policy/marker values, and authenticated OpenCode2 Connected restoration
passed. Latest completed sessions restored normal2195 inside the same emulator lock, retaining app/account data. An ordinary app-context QA producer is being built for the remaining proof. BB4/BB5/BB7 remain pending. BB9 is not certified complete.

Finish line: restore a failed pinned program activation to its prior good
version through the next normal app start, only after exact installer quiescence.
No UI, connection-library or account edits. BB3 lifecycle acceptance is committed
as `2fe344b57`; BB8 was transferred to BD2.

## Latest ordinary producer finding

The latest whole locked session reached native preflight and PrepareExternal,
then auxiliary Main `/proc` access failed as `producer_app_identity_missing`
while host revalidation confirmed the original Main PID/start-time still live.
No child root or workload permit was issued. The platform cause is unknown.
See [fixed observations and restoration](host-device-orphan-ordinary-producer-export-diagnosis-warm.txt).
Native cleanup, original executable/native preferences, authenticated Connected
restoration and final typed owner-policy/active-pointer restoration plus verified
normal2195 reinstall passed. The inner comparator still reported its original
metadata mismatch; that failed outcome remains recorded.

Production now requires exact signal-zero ESRCH before any absent recorded
installer or saved pre-rollback runtime member can be treated as gone. The QA
helper replaces unnecessary sibling Main reads with private native-authored
export/fixture/writer equality; Main's actual admission adds self-stamp equality
and retains kernel/UID/nonce/ancestry/cgroup/signal/full-inventory checks. Native
removed-fix proofs, focused compile and a coherent new signed candidate are in
progress. No orphan acceptance is claimed.

## Passed device evidence on normal QA2205

| Whole locked session | Evidence | Result |
| --- | --- | --- |
| Actual next normal app startup restores prior-good executable and command link before read-only Verify | [cold-command-link](host-device-cold-command-link.txt) | PASS |
| Generic-check queue and pending Stop integration, with app restoration | [queue](host-device-pending-stop-queue.txt) | PASS |
| Exact tracked live peer handling, with app restoration | [peer](host-device-peer-final.txt) | PASS |
| Real Android filesystem: first-install absent, invalid receipt, unknown quiescence and nonempty-lock refusals, with fixture cleanup and app restoration | [filesystem](host-device-filesystem.txt) | PASS |

These sessions restored authenticated OpenCode2 Connected/Running, native
preferences and original owner/policy/marker values. The cold run removed the
stale empty catalog lock and journal/writer receipts while preserving the
original retained-good backup. See the [latest restored screen](restored-opencode2-current.jpg).
The installer naturally died with the app in the core runs (zero observed
survivors); those passes do not establish orphan drainage.

Normal QA2205 target SHA-256:
`0f468d37c4bdb6b9593dd609a0e3614d3bb03c6693b448e7df86c7819a5a8c5f`.
It uses the authorized `1DE5BF08…` signer. These are QA2205 results, not device
qualification of the latest QA2206 target.

## Current focused check evidence

- Native ownership: 33 tests, zero failures/errors
  ([result](native-pending-stop-result.json), [build](native-pending-stop-build.txt)).
  Native filesystem: 21 tests, zero failures/errors
  ([filesystem result](native-integration-result.json)). These are separate
  focused checks, not a full native suite.
- Dart: 34 affected tests passed ([final restored](dart-final-restored.txt)).
  Analyzer clean ([log](analyze.txt), 42.4 seconds).
- Frozen host checks: 117 green = 48 main + 35 external-producer + 34 BB3
  ([current frozen tests](host-final-frozen-tests.txt)). This supersedes the
  earlier 47-test and 32-test host counts below.
- Removed-integration controls produced their expected assertion failures and
  restored green; see [focused driver](../../../tool/qa/bb9_dart_regression.py),
  [Dart activation red](dart-red-catalog-activation.txt),
  [cold red](dart-red-catalog-cold.txt), [lock red](dart-red-catalog-lock.txt),
  [installer routing red](dart-red-installer-routing.txt), and
  [restored](dart-final-restored.txt).

No full Flutter suite or all-edge device certification is claimed. Checks and
builds use the shared locks; device sessions use the exclusive emulator lock.

## Orphan fixture — UNQUALIFIED

The independent external producer must survive app death, prove exact ownership,
and reach native commit/permit before its drainage can qualify BB9. The retained
`su` and `run-as` launch diagnosis reports instead show exit 127 and an
`env`/missing-executable failure **before native commit/permit**. Root identity was
not proven and update permission was not granted. The private token comparison established that `env` consumed the APK executable
pathname containing `=` as an assignment and attempted the next argument. The
[fixed host launcher](../../../tool/qa/bb9_external_installer.py) uses a fixed
shell `exec` to preserve the original protected argv; [removed-fix red and full
35-test restored green](host-external-equals-red-restored-final.txt) prove the
regression. This changes only QA code, not the installed production runtime.
The [post-fix session](host-device-orphan-runas-equals-fixed.txt) reached native
commit but refused before permit because of QA reflection; exact host drainage,
native fixture cleanup and original Connected restoration passed. The outer
class has no `descendants` method; the runner now uses its existing bounded
same-UID tree helper. No orphan recovery is inferred from these failed runs.

[External launcher diagnosis](host-device-orphan-launcher-code.txt) and
[run-as retry](host-device-orphan-runas-retry.txt) retain the failed outcomes.
Native fixture cleanup and the person's authenticated Connected restoration
passed, but the whole sessions failed with `external_cleanup_unproven` and
`external_root_exited_before_gate`. Those cleanup passes do not convert the
failed qualification to a pass. The earlier [orphan run](host-device-orphan.txt)
also failed external cleanup/root-header proof.

## Latest installed private QA2206 target

[Artifact metadata](target-runas-artifact.json) records version code 2206,
SHA-256 `d9b50e822873ed64c3b37036ffbfa80fe755fec8677915b59b307ebeedf8f1f7`,
the same authorized signer, and the unchanged release engine SHA-256. The
[private init script](runas-private-init.gradle) uses the public AGP generated
manifest artifact API to set `debuggable` only on that private QA manifest.
The release DSL remains `debuggable=false`; the normal Gradle files, source
manifest, SDK configuration and release engine are unchanged by this fixture.
It is a private release-engine QA target, not a published release.

- First QA2206 attempt: fixture **NOT REACHED**, whole session failed
  `instrumentation_replaced_app` ([first attempt](host-device-orphan-runas.txt)).
  Safe person restoration passed.
- Second QA2206 attempt: external launcher failed before native commit/permit;
  native fixture cleanup and person restoration passed, while the whole session
  remained failed `external_cleanup_unproven`
  ([retry](host-device-orphan-runas-retry.txt)).

The latest target is installed. The latest fixed-category runner is [cbc54626](runner-external-admission-artifact.json),
compiled116.9s with the same signer; [owned-process cleanup](runner-external-admission-owned-processes.json) has zero survivors and intermediates absent.
A normal-app-readable surviving installer fixture remains a prerequisite;
external run-as context is not equivalent to ordinary app context.

## Historical checkpoints and failed runs

The following logs remain evidence of their particular candidates and attempts;
they are not current target or completed-gate claims:

- Initial Android integration compiled Kotlin/Java and passed 39 focused JVM
  tests (18 ownership + 21 filesystem), with no failures/errors
  ([build](native-integration-build.txt), [result](native-integration-result.json)).
  Ownership later expanded to 33 tests. Initial catalog/setup checks passed 28;
  the affected Dart files later passed 34 after the cancellation correction.
- Earlier host counts were 47 main tests with 23 removed-guard controls
  ([supplemental](host-supplemental-red-restored.txt)), and 32 external-producer
  mocks with 11 removed-guard controls
  ([red](host-external-red.txt), [restored](host-external-restored.txt)).
  The current frozen run is the 116-test record above.
- Signed QA2203 target built in 229.7 seconds (wrapper 233.1), SHA-256
  `9cccd8234ea4f22083057d94e5d72d2a4b6a4ae60f01ee5aa3443f924555d0d7`;
  matching private runner built in 117.9 seconds, SHA-256
  `c6828276df34a305721361f5908d71e21f3fa242cd1a21b82fc8433e4dfe2d98`.
  Both used the authorized signer. Build-time fingerprints and process cleanup
  belong to that historical build, not the current installed artifact.
- [QA2203 primary retry](host-device-primary-retry.txt) passed core rollback and
  native/account restoration, then failed a serialized prebootstrap XML-node
  equality check. Typed-value comparison replaced that layout-dependent check
  ([red](host-policy-layout-red.txt), [restored](host-policy-layout-restored.txt)).
  The exact cause of that historical comparison failure was not established
  from retained raw snapshots, which were deliberately not logged.
- [QA2203 corrected repeat](host-device-primary-final.txt) failed Connected
  recovery and native fixture cleanup. Final native preferences/owner digests,
  typed policy values and authenticated Connected restoration passed.
  [Subsequent diagnosis](host-device-repeat-diagnosis.txt) found the original
  executable active, journal/lock/ticket absent, remaining fixture cleaned and
  authenticated Connected restored. The failed run remains failed.
- [Earlier cold checkpoint](host-device-cold-final.txt) passed before the explicit
  command-link repeat above. It also observed zero installer survivors and did
  not qualify an orphan.

Final read-only locked check: [no pending journal/legacy lock/fixture/export](final-readonly-restoration.json); viewed current small JPG shows OpenCode2 2.0.10 Connected/Running. Only latest target/runner APKs retained. All build-owned processes exited and intermediates are absent. Source hashes for the frozen production native files remain unchanged. Text evidence has trailing-space/stack-indent normalization only; failed outcomes are retained. Owned text diff and document-link checks pass. No BB9 completion commit, push, full suite or publication; working changes are reviewable but BB9 remains uncommitted.

## Ordinary app-context QA producer checkpoint (2026-10-08)

Normal2195 is restored after both newer whole sessions. The su route's actual
`/system/xbin/su` path works for a fixed harmless command, but its whole rollback
session failed before orphan setup while obtaining the normal startup baseline.
Those failures remain in the evidence; no qualification was inferred.

The next private QA2207 release adds only a generated-manifest service protected
by Android DUMP permission, running in `:bb9producer`. Source manifest/Gradle and
security policy remain unchanged; the APK remains nondebuggable with the pinned
release engine. The service uses the unchanged native-authored protected argv,
waits for durable native admission before permit, and ends within 180 seconds.
Actual native readability, separate lifecycle and surviving orphan drainage are
still unqualified until the device scenario passes.

[Focused host checks](host-producer-final-focused.txt): 162 pass.
[Service removed-guard checks](host-service-red-restored.txt): 11 fail when their
guard is removed, then source restoration passes.
[Native acknowledgement checks](host-native-ack-red-restored.txt): 26 removed
controls fail, then 51 host tests pass. These are mocked host checks.
[Whole-session wrapper](service-normal-restore-session.py) restores only the
original active-profile pointer while dead, then reinstalls verified normal2195
in place inside the same lock. Native fixture cleanup separately preserves the
original program, preferences and policy values.

Ordinary-context QA2207 startup attempt: [original session](host-device-orphan-ordinary-producer.txt) failed before fixture because no memory recipe was armed. Explicit person Stop/Start in its restore passed. [Explicit-start session](host-device-orphan-ordinary-producer-explicit-start.txt) then passed the real baseline and native preflights/PrepareExternal; its auxiliary producer failed before a root/permit. [Fixed safe diagnosis](host-device-orphan-ordinary-producer-fixed-diagnosis.txt) projects producer_unavailable only. Native fixture/program cleanup, kernel-backed Stop/Start, existing policy/marker digests and authenticated OC2 Connected restoration pass; external-tree cleanup remains unproven. Each final wrapper restores original active selection and owner typed policy/marker values while the entire app UID is dead, then reinstalls verified normal2195 under the same lock. Failure receipts remain unchanged; no orphan qualification claim. QA2208 adds only finite operation/exception categories to narrow the producer failure.
