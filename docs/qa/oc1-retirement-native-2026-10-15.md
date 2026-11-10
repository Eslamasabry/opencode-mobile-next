# OpenCode 1 retirement: native engine verification

Source commit: `9755484d0` (2026-10-15).

The phone engine now calls the pinned OpenCode 2 driver directly. The OpenCode 1
client and schema fixture are deleted. Protocol-neutral session, role, model and
directory checks remain, and the OC2 constructor now owns the unchanged strict
loopback and credential validation. Usage reads OC2 messages directly.

The config writer uses `serverCredentialFile` and `serverBaseUrl`. Rust accepts
the former field names as deserialization aliases only, preserving existing
private configuration without retaining an OC1 connection path. Native health
emits `oc2` and `oc2Verified`; the OC1 verification flag is removed.

## Host checks

All checks held `tool/qa/machine_lock.sh build`; compilation used
`CARGO_BUILD_JOBS=2`, with artifacts and proof fixtures isolated under Storage.
No physical phone, owner server, signing key, or release command was used.

- `rustfmt --check` on changed Rust sources and `git diff --check`: pass.
- `cargo test --manifest-path engine/phone/Cargo.toml --locked`: 80 unit tests,
  10 attestation tests and 4 boundary-kernel tests passed before continuation.
- Remaining integration binaries were resumed against the unchanged candidate:
  136 passed. Total: **230 passed**, **1 existing ignored proof**.
- `cargo test --manifest-path engine/phone/Cargo.toml --locked --doc`: pass
  (no documentation tests).

The first integration attempt lacked `OC_PHONE_PROOF_ROOT`; setting an isolated
Storage directory resolved that prerequisite. One process-start test returned
`WouldBlock` under the default test thread count; its test binary passed when
rerun with `--test-threads=2`. Remaining binaries ran with the same two-thread
bound. These are host results, not Android runtime qualification.

The ignored test is
`raw_syscalls_proc_aliases_metadata_and_descendants_cannot_reach_private_state`,
which explicitly requires a Landlock ABI6 kernel. No capability was enabled on
its behalf.

Raw local logs (not committed):
`/home/owner/Storage/tmp/killoc1-native-test/{full-tests,resumed-tests,resumed-tests-2}.log`.

## Android staging

`engine/phone/tool/build-android.sh --stage-android` passed under the build lock
with `CARGO_BUILD_JOBS=2` and an isolated `OC_ENGINE_BUILD_DIR`.
The arm64-v8a build took 3m21s; x86_64 took 2m06s.

All six staged ELF files match their ABI architecture and manifest SHA-256.
The packaging verifier's `verify_source` confirms the current source digest:
`876c842e34cb76ae538628f5cb68c84e7de46e577512b0d5c909eb6b0e528b1f`.

The staged manifest is `android/app/src/main/assets/aiteam-engine-manifest.json`.
Its source revision is the then-current shared branch HEAD; `sourceDirty:true`
records concurrent non-engine edits. The source digest, checked before and after
the build, is the authoritative native-source match.

Build log: `/home/owner/Storage/tmp/killoc1-native-test/android-build.log`.

## JVM checks

The green phase of `tool/qa/bb3_pure_jvm_check.py` passed all **56 tests** using
OpenJDK 17.0.20 and isolated runner artifacts. No mutation experiments ran.
`NativeServerRecipeTest` now checks OC2-only startup and executes a shell fixture
that refuses a cold start when the old history database exists but its OC2
destination does not (exit 74). The source database remains untouched; startup
proceeds after the target exists. Log: `build/oc1-decommission/jvm-green.txt`.

## Unsigned APK compilation

On 2026-10-16, `bash build/oc1-decommission/vps_build_unsigned_locked.sh --unsigned 54`
passed on the build server in **789 seconds**, including the shared build-lock
wait (Gradle: 419.9 seconds). The product revision was `e4f0d88a2`, including the
managed migration capability gate and the remote upgrade command's removal of
the conflicting old npm package. The app input digest was identical before and
after compilation.
The digest covers sorted tracked paths and contents under `lib`, `android`,
`assets`, `engine/phone/src`, plus `pubspec.yaml` and `pubspec.lock`, with NUL
separators between each path and content:
`f92a1f93bab7753f4179f35ae1cd9c97872b44ff7387554a6f58fbff9517d5b4`.

Artifact: `build/vps-apk/app-release-unsigned-54.apk` (83,297,416 bytes).
SHA-256: `86af913924e75dbcf52074b3169d7ddec80701180e0d1fc31f85ea3f05387779`.
Log: `build/vps-apk/server-build-unsigned-54.log`.

The ignored helper derives the unsigned build path from `9bcf40aeb`, removes its
signing branch, and wraps the remote Flutter build with the coordinator's
`$HOME/.oc-vps-apk-build.lock`. It never reads or creates signing credentials.
The tracked coordinator build script remained byte-identical, SHA-256:
`13f0d85d83469a93021218f5c121bcdbadad2d69ffa02b52d02e76cc96ab6ef7`.
Validation
confirmed ZIP integrity, no APK signing block or JAR signatures, all six packaged
phone-engine library hashes matching the staged manifest, and the native source
digest above. Independent ELF-header checks confirmed AArch64 for all three
arm64-v8a libraries and x86-64 for all three x86_64 libraries.

This is a compilation artifact, not an installable replacement for the owner's
signed app. Nothing was signed, installed, deployed, or released. No device or
owner server was accessed. The queued local APK compilation was cancelled before
Gradle started; APK compilation ran only on the build server.
