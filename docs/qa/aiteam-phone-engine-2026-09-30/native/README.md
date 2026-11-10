# Native lifetime and boundary slice

> Historical worker-slice record. The runtime authority/heartbeat follow-up and
> executed integration checks supersede the unavailable/deferred conclusions
> below; see [current integrated QA](../README.md).


Finish line: the native Rust daemon survives UI closure under the existing foreground service, uses private per-profile state and credentials, stops and deletes honestly, and exposes a reproducible OS boundary harness with execution unavailable until complete evidence exists.

Non-goal: UI, release/signing/publishing, Cargo/build tooling, OC2, per-worker processes, charging telemetry, or a claim that proot is a security sandbox.

Owned read/write set: both builtin MethodChannel halves (`BuiltinLinux.kt`, `MainActivity.kt`, `lib/builtin/builtin_linux.dart`), `BuiltinServerService.kt`, `LocalTerminal.kt`, new `PhoneEngineNative.kt`, Rust boundary launcher/probe and their deferred tests. Repository/store/pipeline and package integration remain coordinator/other-slice owned. No other checkout was edited.

## Frozen bridge

Existing channel: `io.github.eslamasabry.opencode_mobile/builtin_linux`.

| Dart call | Native arguments | Result |
|---|---|---|
| `startPhoneEngine({required profileId, port=4098, notice})` | `profileId`, `port`, optional `notice` | `BuiltinPhoneEngineStatus` |
| `phoneEngineStatus(profileId)` | `profileId` | same status |
| `phoneEngineCredentials(profileId)` | `profileId` | `BuiltinPhoneEngineCredentials(baseUrl, bearerToken)` |
| `stopPhoneEngine(profileId)` | `profileId` | status |
| `deletePhoneEngine(profileId)` | `profileId` | void; errors retain deletion intent |
| `startProtectedPhoneServer({required profileId, required script, port=4097})` | same named values | void |
| `runPhoneEngineBoundaryProbe()` | none | isolated proof summary; never enables execution |

Status: `running`, `profileId`, nullable `port`, `boundary=false`, `execution=false`, `restartRequired`, `boundaryReason=boundary_unverified`. Credentials are never status/log fields; their Dart representation is redacted. The handoff rejects non-loopback URLs and malformed tokens. Native errors expose stable codes with generic text. There is one tracked phone daemon, independent of worker count; a different active profile/port is refused as `engine_in_use`.

The native launch is the packaged executable `nativeLibraryDir/libaiteam_engine.so --config <private-file>`. Config uses schemaVersion=1, profileId, privateRoot, workerRoot, guestWorkerRoot, port, authTokenFile, oc1CredentialFile, oc1BaseUrl, boundary, and sourceRoots. Source mapping is only app-private `filesDir/projects` to `/root/projects`; no broad `/root` or canonical root is imported. The daemon must apply `PR_SET_DUMPABLE=0` and parent-death termination before reading secrets. App release dumpability is not accepted as the boundary proof.

Per-profile root: `filesDir/oc.teamEngine.<profile>`, directory 0700, atomic/fsynced config and token files 0600, nofollow reads. Authentication is 32 random bytes encoded as 64 hex characters. OC1 Basic credentials are copied from the existing native-owned password file; no provider credential is queried. No secret enters argv, environment, logs or error strings. Daemon stdout/stderr are discarded. Health is authenticated before native start returns. `close()` in a Flutter client does not stop this process.

Deletion invokes the live authenticated durable `DELETE /v1/profile`, then stops the process, then erases worker clones and private state without following links. A fsynced `oc.teamEngineDeletion.<profile>` marker survives interrupted native erasure; subsequent native start completes erasure before recreating a generation. A stopped profile is deleted through the same native path. HTTP rejection/uncertainty returns `engine_delete_failed`; it does not pretend deletion succeeded. Stop/FGS timeout uses the existing exact tracked-process lifecycle, clears restart intent, and includes the daemon. Android FGS lifetime is bounded; no persistent auto-relaunch is introduced.

## Enforcement and gate

The existing runtime runs under the app UID and binds `/proc`. PRoot's path rewriting is not an OS authority boundary. A protected restart refuses any existing server, proot service/run process, or terminal session as `restart_required`; it never kills a person's chat to change the security mode. All subsequently created native proot services, runs and terminal PTYs use `libaiteam_sandbox.so` as argv[0] and the actual executable. Ordinary execution remains available in the existing legacy path when protected mode was never explicitly requested; native engine execution remains unavailable in either path.

The launcher requires Landlock ABI >= 6, handles all filesystem access rights through ABI 5, scopes cross-domain signals and abstract UNIX sockets, and sets no_new_privs before irreversible restriction. It allows read/execute only for shipped native libraries/system paths and `/proc`/`/sys`, write only for the Linux runtime, project directory and proot scratch directory, and only the four explicit null/zero/random devices. It never grants the entire app-data root or `/dev`. Unneeded inherited descriptors are closed. Landlock's process-domain restrictions constrain ptrace/process_vm and sensitive proc aliases outside the tool domain; children inherit confinement.

Landlock does not mediate every metadata syscall and pre-ABI9 pathname UNIX sockets are a gap. An additional native seccomp filter denies chmod/chown/xattr/timestamp mutations, mount/namespace/handle syscalls, io_uring, keyctl/bpf, AF_UNIX socket creation and terminal command injection. Architectures other than aarch64/x86_64 refuse execution; the x32 syscall-number escape is denied. The proot tracer retains ptrace within its own descendant domain. No unconfined fallback occurs on any policy/kernel failure.

Compatibility is unresolved: denied metadata and AF_UNIX operations may prevent real Git/Bun/tools from working on a target Android kernel. That requires the exact shipped APK/device acceptance harness, not an ABI query. `--check-kernel` is only a prerequisite. No boolean persisted by a caller/config is proof. The native status and daemon config remain hardcoded unverified; this slice does not grant promotion or worker execution even if a harness reports complete.

Primary contract references: [Linux Landlock documentation](https://docs.kernel.org/userspace-api/landlock.html) (ABI, ptrace domains, current filesystem limitations) and [Android kernel Landlock UAPI](https://android.googlesource.com/kernel/common/+/d0e91e401e31959154b6518c29d130b1973e3785/include/uapi/linux/landlock.h). Kernel/SELinux support on the owner's phone has not been measured in this slice.

## Proof implementation and deferred checks

Package names: `oc-engine-sandbox` -> `libaiteam_sandbox.so`; `oc-engine-boundary-probe` -> `libaiteam_boundary_probe.so`. Root owns target compilation/staging.

The raw probe attacks canonical sentinel read/write/truncate, traversal, symlink aliases, proc root/fd/environment/memory/namespace aliases, rename/link, permission/ownership/timestamp/xattr changes, cross-domain ptrace/process_vm, signals and IPC. Permission denial is checked immediately per syscall, rather than treating an absent path as proof. A worker write positive control and fork/exec descendant repetition are required. The Android native harness creates only isolated disposable fixtures, exercises this probe under the exact native policy, and runs proot real Git init/add/commit/update-ref positive controls plus direct main/config/hook-override attempts. It checks sentinel content/mode and removes fixtures without following links. Summary fields: schemaVersion=1, nativeAttacksDenied, prootGitCompatible, fixtureUnchanged, complete, enablesExecution=false, boundaryReason=boundary_unverified. Invocation creates no live repository change and never changes capability state.

Deferred commands (coordinator must serialize and keep artifacts on Storage):

```bash
OC_PHONE_PROOF_ROOT=/home/owner/Storage/Code/oc_app-phone-proof-artifacts \
  cargo test --manifest-path engine/phone/Cargo.toml --test boundary_proof -- --include-ignored
flutter test --concurrency=1 test/builtin_phone_engine_test.dart
```

Both commands are **not run**: the owner deferred tests. No Rust/Kotlin/Android build, Flutter analyzer, device proof, signing, push, deployment or release is claimed. `dart format` and `rustfmt` were run. Dart formatting warned that flutter_lints package resolution is absent in the isolated worktree; formatting succeeded and is not analysis. `git diff --check` is clean. Target device kernel capability, proot/Git compatibility, native lifecycle restart/timeout/deletion and complete promotion acceptance still require execution.
