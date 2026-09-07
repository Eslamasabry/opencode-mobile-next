# First-install runtime choice — 8 September 2026

Finish line: a fresh managed installation offers OpenCode 1 (default) and
OpenCode 2 beta, installs the selected pinned runtime, and reconnects with the
matching saved profile after restart. Existing installations retain their
generation. Migrating an existing installation between generations is outside
this change.

## Runtime and storage

| Choice | Main npm package | Pinned version | Health route |
| --- | --- | --- | --- |
| OpenCode 1 | `opencode-ai` | `1.18.29` | `/global/health` |
| OpenCode 2 beta | `@opencode-ai/cli` | `0.0.0-beta-18600` | `/api/health` |

The matching Linux ARM64 or baseline x64 native package is an explicit npm
dependency in both cases. Upstream postinstall, optional dependencies and retry
handling remain enabled. The PRoot bootstrap retains `UV_USE_IO_URING=0` and
the smaller apt dependency set with Git, SSH and CA certificates explicit.

The accepted dispatcher records `$HOME/.oc/runtime` alongside the existing
manager state before starting setup. A missing marker remains the legacy v1
default. The marker is installation metadata, not a provider credential or a
profile-owned cache: deleting a saved connection does not erase the user's
Termux installation. Profile flavor and credentials use the existing profile
serialization, secure storage and deletion paths. No new account access occurs.

## Android runtime feasibility evidence

Executed in the root-owned Android 16 emulator's separate
`oc-qa-lean-20260907` Ubuntu container. The maintainer's `opencode-ubuntu`
container and its sessions were not used.

- npm metadata and both official package archives were fetched over HTTPS and
  checked against their registry SHA-512 integrity values.
- The x64 baseline executable SHA-256 was
  `a2f9e4a580fc51b655c48fa3590fb262f083c2003d51838b393bc9d5a0b63cf1`.
- npm installed the two archives into an isolated prefix and ran the unmodified
  upstream postinstall. The command reported `opencode2 v0.0.0-beta-18600`.
- A server on isolated port 4137 returned 401 without credentials and 200 with
  a per-run synthetic password at `/api/health`; its reported version matched.
- An empty QA session was created successfully. Two independent event-stream
  connections each received `server.connected`. No prompt or provider request
  was sent. The captured server PID was stopped by the probe's exit trap.
- The server log was checked locally for the synthetic password; only a boolean
  result was emitted, and no password was found.

The emulator's outbound DNS failed with `EAI_AGAIN`. This proof therefore used
host-fetched, integrity-checked archives copied into the QA container; it does
**not** establish a successful first-time network download. npm/postinstall took
53 seconds; the complete isolated package/runtime probe took 71 seconds. Those
figures exclude Ubuntu/apt bootstrap and cannot be advertised as fresh-install
timings. ARM64 package metadata was checked; ARM64 execution remains unverified.

Primary package metadata: [CLI beta](https://registry.npmjs.org/@opencode-ai%2fcli/0.0.0-beta-18600),
[x64 baseline](https://registry.npmjs.org/@opencode-ai%2fcli-linux-x64-baseline/0.0.0-beta-18600).

## Generated manager on Android

The current manager and installation-inspection scripts were extracted from
`bridge.dart` and executed as Termux UID 10217 on the Android 16 emulator. The
harness changed only the container name to `oc-qa-lean-20260907`; it supplied a
separate Termux home and guest HOME/XDG directories, a PATH pointing to the
already verified beta installation, and no-op wake-lock commands so the test
did not change the user's wake-lock state. No package installation ran here.

- Start reached authenticated ready state with `runtime=opencode2`, the exact
  beta version and the accepted operation ID. Fresh-process inventory agreed.
- Restart replaced only the recorded runner, retained the runtime and advanced
  the accepted-operation timestamp. The old runner was absent afterwards.
- Stop retained the runtime marker, reported stopped, left the recorded runner
  absent and released port 4139. The exit trap repeated the owned cleanup.
- The whole manager proof took 37 seconds. This is **not** an installation time.

Extracted manager SHA-256 before the container-name substitution:
`1e8a39c9adc4c776bfcf9b4d260205349e61400c0428c207e7e5e631fd3950b6`.
The safe retained log is `build/traycer/runtime-manager-proof.log`; the harness
and extraction helper are retained beside it. Authentication and server logs
remain private inside the isolated QA home.

## Focused verification

Pinned Flutter 3.47.2 / Dart 3.13.2, serial execution:

```text
flutter pub get
flutter gen-l10n
flutter test --no-pub --concurrency=1 test/termux_scripts_test.dart test/termux_setup_screen_test.dart test/termux_recovery_scripts_test.dart test/managed_server_recovery_test.dart test/l10n_coverage_test.dart
flutter test --no-pub --concurrency=1 tool/capture/termux_runtime_choice_test.dart
flutter analyze --no-pub
```

There are 93 passing focused checks and one existing Windows-only process-group
skip across the five files. The initial run found an obsolete v1-only update
dialog assertion; its fixture now retains an installed environment after Stop,
and the assertion names the selected runtime and exact pinned version. The
affected screen and localization files were rerun after the final copy changes:
all 38 checks passed. The unchanged shell/recovery checks retained their earlier
passes. This is focused coverage, not a completed repository suite.

Localization generation, changed-file formatting and the final analyzer passed
with no issues. `pub get` resolved dependencies and generated this worktree's
own package configuration, then exited 1 at the Windows desktop-plugin symlink
requirement. The checks above passed with `--no-pub`; Developer Mode and global
settings were not changed.

Three capture cases passed and produced six actual-screen images: light/dark at
390 logical pixels and 320 pixels with 2x text. Visual inspection caught stale
copy saying beta installation was unavailable; the final copy and screenshots
are corrected. Selection, version text, controls and large-text wrapping are
readable. See [the capture note](../qa/first-run-runtime/README.md).

Independent source review passed after correcting legacy stopped-state selection
and same-route install/ready/Stop inventory retention. A fresh full network
installation, ARM64 execution, and the combined repository/native app gate
remain open. No CI, signing, publication or user-device replacement was performed.
