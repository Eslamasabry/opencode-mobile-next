# BB5 QA 2203 build-only artifacts

Source: `feat/genui-fe` at `9c99e609a7187d5f38e5364cc56a9f3900b8d78f`.
Built in `sol/bb-qa-2203` without production or runner source changes.
No device commands, installation, emulator reservation, or device qualification occurred.

## Build and resource evidence

Both builds used the pinned Shorebird Flutter, JDK 17, the shared `machine_lock.sh build` lock,
`-Xmx4g`, in-process Kotlin compilation, and two workers. Each had a fresh >=6144 MiB
available-memory admission check and an owned-process watchdog below 800 MiB.

- Target admission: 6629 MiB available; release build passed in 260.0 seconds.
- Runner admission: 7243 MiB available; `:app:assembleReleaseAndroidTest` passed.
- No watchdog abort. Exact owned Gradle daemons 3929294 and 3934704 were cleaned up;
  both cleanup checks reported no remaining owned daemon and no app intermediates.
- The duplicate target APK was hash-compared and removed; the target and runner remain
  in their original build output paths. No APK copies were made.

Commands (wrapper supplies the resource settings and cleanup):

```sh
BB7_BUILD_NUMBER=2203 tool/qa/machine_lock.sh build -- python3 /home/eslam/Storage/tmp/bb5-2203-build.py app
tool/qa/machine_lock.sh build -- python3 /home/eslam/Storage/tmp/bb5-2203-build.py runner
python3 /home/eslam/Storage/tmp/bb5-2203-verify.py
```

## Artifact verification

`apksigner verify --print-certs` passed for target, runner, and existing normal 2203.
All use certificate SHA-256 `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`.
The QA target and normal app have package `io.github.eslamasabry.opencode_mobile`
and versionCode 2203. Generated release BuildConfig confirms `BUILTIN_RUNTIME_QA = true`.
The instrumentation package is `io.github.eslamasabry.opencode_mobile.test`, targeting
that app with `io.github.eslamasabry.opencode_mobile.BuiltinRuntimeAcceptance`.
Instrumentation has no standalone versionCode; it was built immediately after the target
from the same unchanged source and build settings.

Full paths, SHA-256 hashes, sizes, and identities are in [artifacts.json](artifacts.json).
The existing normal 2203 checksum sidecar matched. Adjacent checksum sidecars were
written for both new artifacts. [final-pass-inputs.json](final-pass-inputs.json)
contains the BB5 row configuration for `final_pass.py`.

## Remaining final-pass blocker

The artifacts are ready, but the driver in this source revision still declares
`VERSION = 2202` and `NORMAL_VERSION = 2202` in `tool/qa/bb5_runtime_acceptance.py`.
It also retains normal-2202 source/hash guards and restoration markers. Meanwhile
`final_pass.py` uses normal 2203. `final_pass_install.py` rejects this mismatch as
`candidate_incompatible`. The coordinator must update and test those driver guards
before a 2203 final pass. No guards were weakened and no final pass was attempted.

Captured build logs have trailing whitespace normalized.
