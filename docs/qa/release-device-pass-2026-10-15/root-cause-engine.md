# AI Team release engine blockers, 2026-10-15

Scope: P0-1 (Older OpenCode readiness) and P1-1 (failed turn-on retains the engine).
Starting revision: `c465947c84c6b6e3a451130ac0cd18a040022d99`, branch
`codex/release-engine-fixes`. The worktree started clean. No `HANDOFF.md` exists
in this checkout. The release-pass README and screenshots were read first.

Finish line: the installed OC1 pin passes the engine's version checks, and a
failed turn-on stops its engine before rollback/retry. Non-goals: chat migration,
other release-pass findings, signing, publication, or changing live servers.

## P0-1: two engine checks retained the previous OC1 pin

The release record and [Details screenshot](rel-19-details.png) show OC1 1.19.0
running, boundary setup completing, then `unsupported_server_version` during
the final readiness step. Both engine checks still required exactly 1.18.32:

- `engine/phone/src/opencode.rs::recognize_oc1`: protocol selection falls back
  from `/api/info` to `/global/health`, then rejects 1.19.0.
- `engine/phone/src/opencode1.rs::pinned_health`: the OC1 driver independently
  repeats the old version check before its callable OpenAPI verification.
  `verify` also reported hardcoded 1.18.32/source metadata.

The installation side agrees on 1.19.0: `lib/builtin/setup/components.dart`
contains the checksummed archive URLs, `lib/domain/agent_catalog.dart` has the
same pin/artifacts, and `lib/termux/scripts/manager_script.dart` selects 1.19.0
for OC1 setup and switching. Phase-2 commit `bab3e6bc8` changed the new-install
default and catalog order/name; its parent already installed OC1 1.19.0. This is
engine/installer pin drift, not the default selecting the wrong OC1 version.

## P1-1: failed readiness never stopped the successfully launched daemon

[The OC2 failure](rel-26-oc2-teamfail-details.png) shows `engine_in_use` at the
engine-start step. [After force-stop](rel-28-after-forcestop.png), setup succeeds.

`PhoneTeamSetupController._failWith` restored OpenCode but did not stop the
engine. Native startup intentionally accepts authenticated, profile-valid health
with `execution=false`: OpenCode is stopped while confinement is proved and is
verified after its protected restart. Thus the OC1 version rejection is a later
Dart readiness failure, after native startup has legitimately succeeded.

`PhoneEngineNative.prepareFreshStart` can restart the same profile but refuses
a live engine owned by a different profile. Moving to OC2 selects its profile;
the failed OC1 engine still owns the native process slot, so OC2 gets
`engine_in_use` repeatedly. The cross-profile refusal is correct and remains.
The fix belongs in failure rollback, not in weakening native ownership checks.

Native `stop(profile)` targets only its tracked child: close stdin, terminate,
wait, force termination if needed, wait again, then remove copied credentials
and clear tracked profile/port/proof. Successful return requires the process to
have exited; OS-held engine locks are released on exit. Team project data is not
deleted by stopping.

## Device evidence and boundaries

Only `adb -s emulator-5554` was used, for read-only `get-state` and
`shell ps -A -o USER,PID,PPID,NAME`. It showed the already recovered state:

```text
u0_a192  20099    387  io.github.eslamasabry.opencode_mobile
u0_a192  20668  20099  libaiteam_engine.so
u0_a192  20789  20099  libproot.so
u0_a192  20792  20789  opencode2
```

This corroborates the recorded post-force-stop OC2 state; it does not recreate
the earlier leak. No physical phone was accessed, no APK was built or installed,
and no Flutter command was run. Post-fix device acceptance remains to be run.

## Changes and verification

- Both OC1 health checks now call one exact allowlist for 1.18.32 and 1.19.0.
  Verification reports the actual checked release/source revision. Unknown
  releases, unhealthy responses and incompatible callable schemas still fail.
- Added an upstream-derived 1.19.0 driver OpenAPI fixture with provenance and
  HTTP regressions covering both pins, unknown pins, unhealthy health and missing
  schemas. The new Rust regressions failed on the original source at
  `unsupported_server_version`, then passed with the fix.
- Added `PhoneTeamSetupPorts.stopEngine` and serialized controller cleanup.
  Failure stops the original attempted profile before server restoration; a
  timeout/failure retains that owner for cleanup on retry. Heartbeats and clients
  are drained, but project/settings storage is retained. A successful run retains
  its engine. Refusing a restart discovered after launch also cleans up.
- Added Dart regressions for OC1 refusal followed by OC2 retry, cleanup ordering,
  cleanup failure/retry, profile changes, serialized start/stop, and preservation
  of stored configuration. Updated all setup-port fakes, including widgetbook.
- Added native device regression
  `PhoneEngineNativeRegressions.releasesUnavailableGenerationBeforeRetry` for
  non-executable health, wrong-owner stop refusal, reaping/credential cleanup,
  same-store retry and another-profile start. **Not run**; requires an
  instrumented preview APK. Native production behavior was not changed.
- `NativeEngineReadinessTest`: **7 passed** with direct Kotlin/JUnit under the
  machine test lock, without Gradle/Flutter. See [native-jvm-tests.txt](native-jvm-tests.txt).
- `python3 -m unittest tool.qa.tests.test_phone_engine_oc1_pin -v`: **1 passed**.
  This guards agreement among the runtime model, setup archive URLs, catalog and
  both manager defaults, and requires the installed pin in the engine allowlist.
- `git diff --check`: clean. Changed Dart files parsed with the pinned Dart
  formatter; no Flutter analyzer/test result is claimed.
- Rust: **253 unique tests passed, 1 existing Landlock ABI6 proof ignored**.
  The initial run passed 103 library tests (including daemon and stdin-EOF
  lifetime coverage), 10 attestation tests and 4 kernel tests, then failed two
  boundary-proof tests because `OC_PHONE_PROOF_ROOT` was unset. No source changed;
  the affected target and every remaining integration target were resumed with
  isolated Storage roots and passed. Doc tests contained no tests.
  See [first run](engine-rust-first-pass.txt), [resumed targets](engine-rust-resumed-pass.txt)
  and [doc tests](engine-rust-doc-tests.txt). The ignored proof is not device
  boundary qualification.

Reproduce the Rust suite with the required isolated directories (the initial run
omitted the two test-root variables):

```bash
mkdir -p /home/owner/Storage/tmp/relengine-rust-tests-20261015/{proof,engine}
tool/qa/machine_lock.sh test -- env CARGO_BUILD_JOBS=2 \
  CARGO_TARGET_DIR=/home/owner/Storage/tmp/aiteam-phone-engine-build/target \
  OC_PHONE_PROOF_ROOT=/home/owner/Storage/tmp/relengine-rust-tests-20261015/proof \
  OC_ENGINE_TEST_ROOT=/home/owner/Storage/tmp/relengine-rust-tests-20261015/engine \
  cargo test --locked --manifest-path engine/phone/Cargo.toml -- --test-threads=1
```

The failing-first filter was `--lib oc1_release_pin`: both new tests failed on
the old checks. That output was observed in the tool transcript but not saved as
a raw log. The focused green filter `--lib opencode` passed 39 tests.

### Actual 1.19.0 callable contract

The official [v1.19.0 source](https://github.com/anomalyco/opencode/tree/733b2c627e5aec39cb33cc22373bdce33053ef2c)
was inspected. Its status, session/global HTTP handlers and permission implementation
are byte-identical to the supported 1.18.32 source. The projected fixture preserves
the driver operations and permission schemas from upstream, and retains provenance.

The x64 baseline installer archive was downloaded to isolated scratch storage and
matched the app's pinned SHA-256
`6334d70db3990721ea454357e9cf2573c8685524ad7769af9d52cbffb7008438`.
A scratch server with separate HOME/XDG/project directories and per-run authentication
passed the actual Rust `OpenCodeClient.verify()` path, including `/global/health`
and live `/doc`: **1 live probe passed** ([log](engine-oc1-live-probe.txt)). Live `/doc` SHA-256:
`04b16616693498e2b92af87408d9df3ea4ce60176d8416d36dc6c5bcf8d3bfed`.
This is host protocol proof, not Android AI Team task execution. No provider
configuration or model calls were needed. The scratch server was stopped by its
exact PID (491637), and the temporary probe test was removed. No `/home/owner`
package files were changed.

### Staged native artifacts

Both ABIs were rebuilt locally from the final Rust source and staged with the
repository's build script:

```bash
CARGO_BUILD_JOBS=2 tool/qa/machine_lock.sh build -- \
  engine/phone/tool/build-android.sh --stage-android
```

[Build log](engine-android-build.txt). The manifest source digest and all six ELF
architecture/hash entries were independently checked after staging using
`verify_phone_engine_apk.py`'s source/manifest validators plus direct file hashes:
[verification](engine-staged-verification.txt).

- Source SHA-256: `f13b23f1ac87c57fd0b959e8cf15f129d244be6ca9d057fea2929807d62cf579`
- arm64-v8a engine: `ba782ea599f54157e45fbd28217edb27051a8ebba4596754b10d8e5a504a6f93`
- x86_64 engine: `fc5438f2ad9ac667079d581f0bae3fc5365fc94f128e68c3a5f12b771ec778bc`

Both engine `.so` files, the x86_64 sandbox artifact and
`aiteam-engine-manifest.json` changed in the complete-bundle rebuild. The ARM64
sandbox, both probe files and dependency notices matched the checked-in versions.
This is source-matched native staging only: no APK, signing, install, push or release.

### Flutter checks for the maintainer (not run here)

Use Flutter 3.47.1 from the pinned Shorebird cache and the machine lock:

```bash
tool/qa/machine_lock.sh test -- \
  /home/owner/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter \
  test --concurrency=1 \
  test/phone_project_engine_controller_test.dart \
  test/phone_team_setup_test.dart \
  test/phone_team_stuck_resume_test.dart \
  test/team_discover_test.dart \
  test/team_migration_test.dart
```

Also run the integration analyzer and widgetbook analyzer for its updated fake.
The full Flutter suite, Android instrumentation, and post-fix device journey remain
unverified. Recheck OC1 turn-on, then an induced readiness failure followed by both
same-runtime retry and OC1↔OC2 switching without force-stop, on a newly built APK.
