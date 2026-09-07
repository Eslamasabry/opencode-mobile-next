# Local Android toolchain preparation — 2026-09-07

Finish line: identify the actual local build prerequisites and prepare an
Android release compile command without using signing material. Non-goal:
signing, publishing, running CI, or changing the live phone server.

## Prepared and executable

- The command host is ARM64 Ubuntu under Termux/proot. It differs from the
  `opencode-ubuntu` rootfs containing the repository and existing SDK.
- Eclipse Temurin **17.0.20.1+1**, Linux ARM64, is installed in the isolated
  a private temporary tools directory. `java
  -version` identifies Temurin and exits successfully. The official archive
  SHA-256 is
  `457b57af8f9c93ec39080bb8c764f559dc8c89a6da1a39d718a400b7890d3e41`;
  the downloaded archive matched the publisher's checksum.
- Termux **aapt2 16.0.0.4-2** and its dependencies were downloaded from the
  official Termux package repository, verified against its package-index
  SHA-256 values, and extracted into the same isolated staging directory.
  The prepared AAPT2 wrapper sets a process-local library path and
  executes the ARM64 binary. `aapt2 version` reports
  `2.20-android-16.0.0_r4`. The main package SHA-256 is
  `ae91c1c1cf743098a4d847e6d44e37a8fd8b83e00548ad7e50e8fbdc4ef0741e`.
  Package metadata is retained in `termux-packages.json` beside the tools.
  The live Termux package installation was not modified.
- `/usr/lib/android-sdk` now aliases the existing SDK in the
  `opencode-ubuntu` rootfs, resolving the repository's existing local SDK
  path in this command environment. Its `platforms/android-37.0` contains
  `android.jar` and package metadata for API **37.0**, revision 2. Whether
  AGP resolves the project's integer `compileSdk = 37` to this installed
  package still needs an actual Gradle configuration/build check.

These are tool executable checks, not an app compile or resource-link pass.

## Exact engine and host limitations

The available local Flutter installation is upstream Flutter **3.47.2**,
framework `d3b14c876900e553bc736ca19295fc09e3853e8e`, Dart **3.13.2**,
engine `a804b261645ef8c13eb3d5c44a5c2fb0340c5539`. Its Git remote and cached
version metadata identify `flutter/flutter`, not the required Shorebird fork.
No Shorebird cache was found in the bounded standard locations checked.

The documented Shorebird revision prefix resolves in the official fork to
`91f8bd75076e9c740aa13cf67eb9ec1a093f68f5`, whose engine pin is
`03e67977a7ff5893d96ac97f22c6a795530c0040`. Its Android host-artifact table
lists Linux **x64**, not Linux ARM64. For that exact engine, HTTP checks found
the ARM64 Dart SDK and Android ARM64 target engine archives available, while
`android-arm64-release/linux-arm64.zip` returned 404 and the corresponding
`linux-x64.zip` returned 200. An ARM64 Dart SDK alone therefore does not
establish an executable Android AOT compiler for this host. No fork cache was
fabricated, upstream engine artifacts substituted, or version metadata edited.

The installed SDK build-tools 36 AAPT2 and NDK 28.2.13676358 compiler are
x86-64 binaries. The isolated AAPT2 addresses the resource-tool architecture
problem; NDK tasks and the Flutter Android AOT compiler remain separate host
prerequisites. No app build was run by this worker, so Kotlin/Dart compile
success, SDK compatibility, or a signing-stage-only failure is not claimed.

## Coordinator command and limits

Once an executable Android host compiler from the required Flutter fork is
available, the coordinator can run the release compile with the prepared
Java and AAPT2 tools. The project path must have no signing configuration;
do not run this command against provisioned signing material under this task.

```bash
env JAVA_HOME=/path/to/temurin-jdk-17 \
  ANDROID_HOME=/usr/lib/android-sdk \
  ANDROID_SDK_ROOT=/usr/lib/android-sdk \
  /path/to/verified-shorebird-flutter/bin/flutter build apk --release \
  --target-platform android-arm64 \
  --android-project-arg=android.aapt2FromMavenOverride=/path/to/aapt2-wrapper
```

Using the current upstream Flutter path would be a supplementary diagnostic
only. It cannot satisfy the repository's pinned-fork gate. The coordinator
must record actual completed tasks and the first failing prerequisite rather
than interpreting every failed release command as Kotlin/Dart compile proof.
No signing files, signing secrets, credentials, workflows, or releases were
used or changed during preparation.

## Primary sources checked

- [Temurin 17 release and checksum assets](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.20.1%2B1)
- [Official Termux package index](https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-aarch64/Packages)
- [Termux Android build tools](https://github.com/termux/android-build-tools)
- [Pinned Shorebird engine version](https://github.com/shorebirdtech/flutter/blob/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/internal/engine.version)
- [Pinned Shorebird host-artifact table](https://github.com/shorebirdtech/flutter/blob/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/packages/flutter_tools/lib/src/flutter_cache.dart)
