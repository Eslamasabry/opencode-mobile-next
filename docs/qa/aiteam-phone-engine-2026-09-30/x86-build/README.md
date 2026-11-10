# Android engine ABI build slice

Finish line: the source build produces and can stage complete, source-matched engine,
sandbox, and boundary-probe bundles for both arm64-v8a phones and x86_64 emulators,
with an architecture-specific hash manifest and dependency notices for both targets.

Non-goal: signing or installing an APK, running model requests, or claiming that an
emulator kernel passes the runtime boundary proof. The native startup proof keeps
the same required controls on either architecture.

## Build

Install the Rust standard libraries once:

```bash
rustup target add aarch64-linux-android x86_64-linux-android
```

Run from the engine checkout under the shared machine lock:

```bash
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh build -- engine/phone/tool/build-android.sh --stage-android
```

The default artifact directory is
`/home/owner/Storage/tmp/aiteam-phone-engine-build`. Override with
`OC_ENGINE_BUILD_DIR`; `/tmp` locations, including resolved symlinks, are rejected.
`ANDROID_NDK_HOME` defaults to the pinned NDK 28.2.13676358. Both ABIs always build
before staging, so a single ABI cannot silently retain binaries from older sources.
A source digest change during the build rejects staging. Each output is checked for
64-bit little-endian ELF and the expected machine type before manifest generation.

## Manifest contract

`android/app/src/main/assets/aiteam-engine-manifest.json` uses schema version 2.
The shared fields are `sourceSha256`, `sourceRevision`, `sourceDirty`,
`rustcVersion`, and `ndkVersion`. `abis` has `arm64-v8a` and `x86_64` entries;
each carries its Rust `target`, `api: 26`, and a `sha256` map for
`libaiteam_engine.so`, `libaiteam_sandbox.so`, and `libaiteam_boundary_probe.so`.
Native verification selects the installed ELF architecture and verifies that entry.
Notices contain the union of the exact locked normal dependency trees for both
Android targets plus the Rust standard library notice.

## Verification

This slice received `bash -n`, Python syntax, and diff whitespace checks. The worker
ran no Cargo build or Android check; the coordinator owns serialized dual-ABI
build, packaged hash checks, native compilation, and live acceptance evidence.
