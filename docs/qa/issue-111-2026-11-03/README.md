# Issue #111: AI Team never starts on Android 16 (`boundary_proof_failed`)

Reporter: Samsung, arm64-v8a, Android 16, kernel `6.12.69-android16-6-…`.
Every real AI Team start stops at "Checking this phone is safe for the team"
with `boundary_proof_failed`; the Demo team works; 2.0.0+56 and 2.1.0+57.

Branch: `fix/issue-111-aiteam-android16-2026-11-03`. Bridge-release bug fix
only; no feature, no change to what the proof requires.

## Status at a glance

| State | |
| --- | --- |
| Implemented | Rust probe fix, Kotlin step reporting, Dart Details/diagnostics, tests |
| Verified (host) | Old probe crashes / new probe passes under an emulation of Android 16's app seccomp policy; Rust, Kotlin helper and Dart tests pass |
| **Not yet effective** | The prebuilt `libaiteam_boundary_probe.so` (both ABIs) in `android/app/src/main/jniLibs/` and `assets/aiteam-engine-manifest.json` are still the old ones. They must be rebuilt with the pinned NDK (see "Owner steps"). The release gate refuses an APK until then. |
| Not verified | A real Android 16 device; the Kotlin change in `BuiltinLinux.kt` was not compiled (no Android SDK here) |
| Shipping | A native change: needs a **new release**, a Shorebird patch cannot carry it |

## 1. What the boundary proof checks, and every way it fails

The Dart flow (`lib/state/phone_team_setup.dart`) calls
`startPhoneEngine` → `BuiltinLinux.startPhoneEngineChecked` (Kotlin).

1. **Kernel check** `requirePhoneBoundaryKernel`: runs
   `libaiteam_sandbox.so --check-kernel`, which forks a disposable child that
   creates, fills and applies a Landlock ruleset (needs Landlock ABI ≥ 6).
   - exit 0 → **Landlock tier**; exit 78 → `boundary_unsupported` → **proot
     tier**; other exit or > 3 s → `boundary_unavailable` (thrown directly).
   - binary not executable → `boundary_not_packaged`.
2. **Landlock tier: `runPhoneEngineBoundaryProbe`**
   - bundle hash check (`verifyBundle`) → `engine_bundle_invalid`;
     unconfined children alive → `restart_required`;
   - fixture: `probe --prepare-git-fixture` seeds a real Git repo in a private
     sentinel dir → `proof_fixture_unavailable` on timeout/bad output/bad refs;
   - **native leg**: `sandbox` (Landlock + seccomp, policy from
     `protectedCommand`) execs `probe <protected> <worker> <app-pid>`. The probe
     must see EACCES/EPERM (EXDEV for link/rename) for: 5 path aliases × 3
     open modes of the sentinel, 3 `.git` files × 3 modes, truncate, rename,
     link, chmod, chown, utime, xattr, kill(app), AF_UNIX socket,
     io_uring_setup, ptrace(SEIZE app), process_vm_readv(app),
     `/proc/<app>/{environ,mem,fd/0}`, setns, unshare; worker write and
     rename must **succeed**; and a re-exec'd descendant must pass all of it
     again. Any miss → exit 1; a crash → killed by signal.
   - **Ubuntu leg**: the same sandbox around proot runs a script that must
     write, `git init/config/add/commit/update-ref/show-ref` in the worker and
     must fail to write the sentinel, write via `/proc/<app>/root`, read
     `/proc/<app>/environ`, delete/rewrite the protected `main` ref or config.
   - **fixture unchanged**: sentinel content + mode, `main`, `HEAD`, config hash.
   - any of native / Ubuntu / unchanged false → `complete=false` →
     `boundary_proof_failed`.
3. **proot tier: `runProotTierProof`** (twice: before signing and against the
   live daemon PID): 16 labelled controls (positive clone/open/stat/readlink/ls,
   canonical paths, daemon `/proc`, fd hygiene, parent inspection, worker
   alias) plus exit 0 and fixture unchanged; any miss → `boundary_proof_failed`.
4. **Signing** (`PhoneEngineAttestation.issue`) → `boundary_signer_unavailable`;
   daemon rejecting the receipt → `attestation_rejected`.

Before this fix, every failure inside 2 and 3 surfaced as the bare code
`boundary_proof_failed`: the probe's own report and the Ubuntu script output
were redirected to `/dev/null`.

## 2. Root cause (with evidence)

**Android 16 is the first Android version on which this app takes the
Landlock tier, and the Landlock tier's native probe is killed by Android's own
app seccomp filter at its `io_uring_setup` check.**

The chain:

1. **The kernel now has Landlock.** `CONFIG_SECURITY_LANDLOCK=y` is in the
   GKI `android16-6.12` `arch/arm64/configs/gki_defconfig` and absent from
   `android15-6.6` (checked in the AOSP kernel_common mirror). Linux 6.12 is
   Landlock ABI 6, exactly what `boundary.rs` requires.
2. **Android 16 lets apps call Landlock.** bionic `android16-release`
   `libc/SECCOMP_ALLOWLIST_COMMON.TXT` lists `landlock_add_rule`,
   `landlock_create_ruleset`, `landlock_restrict_self` ("Probed for and
   conditionally used by Chrome GPU processes"); `android15-release` does not.
   So on Android ≤ 15 the kernel check's child got SIGSYS →
   `boundary_unsupported` → proot tier. **Every earlier pass (Android 14/15
   x86_64 emulators, kernels 6.1/6.6) was the proot tier**; the docs record
   `tier=proot` and "1 existing Landlock ABI6 proof ignored". The Landlock tier
   had never run on Android before this device.
3. **`io_uring_setup` is still not allowed for apps.** It is in none of
   android16-release `SYSCALLS.TXT`, `SECCOMP_ALLOWLIST_COMMON.TXT`,
   `SECCOMP_ALLOWLIST_APP.TXT`; bionic's app filter ends in
   `SECCOMP_RET_TRAP` (`libc/seccomp/seccomp_policy.cpp`). The kernel applies
   the strongest action of all stacked filters; TRAP outranks the sandbox's own
   `SECCOMP_RET_ERRNO(EPERM)` for io_uring. So the probe, which made the call
   in-process as its "io-uring" attack, received SIGSYS and died: Java sees
   exit 159 (128+31), `nativeAttacksDenied=false`, `complete=false`,
   `boundary_proof_failed`. Nothing was printed before the crash, and what the
   probe prints went to `/dev/null` anyway.

Every other syscall the probe tests (truncate, renameat, linkat, fchmodat,
fchownat, utimensat, setxattr, kill, socket, ptrace, process_vm_readv, setns,
unshare, openat) is on the Android 16 allowlist.

### Reproduction on a Linux host (no device)

`emulated-android16-seccomp.txt` (tool: `android16_app_seccomp.py`) runs the
sandbox + probe as an unprivileged user under a seccomp filter generated from
the android16-release allowlist files (TRAP for everything else; a few glibc
start-up calls bionic never makes are shimmed in):

- **old probe**: `Bad system call`, exit **159**, nothing printed; the only
  trap is `__NR_io_uring_setup`. That is exactly the reporter's symptom.
- **new probe**: `native-boundary-probes-passed` (parent and descendant);
  the only traps are `io_uring_setup` inside the disposable children.
- **JVM harness** (verbatim copy of the new Kotlin `runProbe` +
  `PhoneBoundaryProofSteps`): the old probe is now reported as
  `boundary_proof_failed:native.signal-31`.

Without any seccomp emulation the native leg already passed on Linux 6.18
(ABI 7) as uid `nobody`, so the probe logic itself was sound.

### Candidates ruled out

| Candidate | Evidence | Verdict |
| --- | --- | --- |
| 16 KB pages | Every bundled ELF (both ABIs: engine, sandbox, probe, proot, proot-loader, talloc, android-shmem) has LOAD `p_align` 16384 (some 65536) and 16 KB-congruent offsets; NDK 28.2 links 16 KB by default; AGP 9.3.2 | Not the cause; now guarded by `test/native_libs_page_alignment_test.dart` |
| exec from app storage / W^X / noexec | All executables run from `nativeLibraryDir` (`useLegacyPackaging = true`); the kernel check itself must have exec'd and exited 0 for this code to be reached (`boundary_unavailable` otherwise) | Not the cause |
| SELinux `execute_no_trans` / `execmem` | Same exec path works for the kernel check, proot and the supervisor; nothing maps W+X | Not the cause |
| ptrace / `/proc` restrictions, Samsung DEFEX/Knox | These can only make the attack checks *more* denied, which passes; a Samsung change that kills instead of refusing is now also counted as denied for these syscalls | Unlikely; would now be named |
| Landlock missing on Samsung's 6.12 | Possible if Samsung's GKI build differs; then the proot tier runs and fails somewhere else | Still open; the new step code names it |

## 3. The fix

**Rust probe** (`engine/phone/src/bin/oc-engine-boundary-probe.rs`): every raw
attack syscall (truncate, rename, link, chmod, chown, utime, xattr, signal,
unix-socket, io-uring, ptrace, process-vm-read, setns, unshare) now runs in a
disposable forked child (`attempt`) that reports `(result, errno)` over a
pipe. The verdict (`attack_denied`):

- EACCES/EPERM (EXDEV for link/rename): denied, as before;
- **killed by SIGSYS**: denied. The kernel refused the call before it ran
  (seccomp TRAP/KILL), which is at least as strong as EPERM;
- success, any other errno, any other signal, no answer in 5 s: **fails**,
  printing `boundary-control:<id>:signal:<n>` / `:no-answer`.

The child resets SIGSYS to the default action and sets `RLIMIT_CORE` to 0, so
no crash handler or core dump runs. Nothing else in the proof is relaxed:
open-based checks, positive controls, the descendant re-exec, the Ubuntu leg,
fixture checks and the sandbox policy are unchanged. The descendant failure
now says how it ended (`descendant:signal:N` / `exit:N` / `spawn`).

**Kotlin** (`BuiltinLinux.kt`, new `PhoneBoundaryProofSteps.kt`): the probe's
stdout and the sandbox's static stderr code are kept (64 lines, 160 chars,
read in background threads) instead of `/dev/null`; the Ubuntu script prints
`proof-step:<name>` before each check. A failure is thrown as
`boundary_proof_failed:<step>`, for example `native.signal-31`,
`native.io-uring.signal-11`, `native.chmod.errno-38`,
`native.sandbox.landlock_rule_failed`, `ubuntu.git-commit.exit-128`,
`fixture-changed`, `proot-tier.daemonProcDenied`,
`setup.proof_fixture_unavailable`. Only `[A-Za-z0-9._-]` codes are kept: no
path, argv, environment, sentinel content or OS message. The daemon config
`reason` is unchanged.

**Dart** (`phone_team_setup.dart`, `team_phone_setup_screen.dart`, ARB):
`PhoneProofStep.parse` reads the step. Details (and so Copy all) is now: the
plain sentence (`phoneTeamWhyUnsafe`, since a failed check is not an engine
crash), a plain line naming the step (nine new strings, English and Arabic),
then the code. The failed "Checking this phone is safe" step shows the same
plain line. Every failed run is recorded in the app diagnostics export
(source `aiteam-setup`) as short fields, e.g.
`AI Team setup stopped; step check; problem engine; code boundary_proof_failed; step native signal-31; kind signal; signal 31`.
Fields are split so the export's long-token mask cannot hide them.

## 4. Tests and results (2026-11-02, this container)

| Check | Result |
| --- | --- |
| New ignored Rust test `android16_app_seccomp_trap_on_io_uring_counts_as_denied_not_a_probe_crash` (Android-style TRAP on io_uring) | **fails on the old probe** ("got " empty), **passes** with the fix (uid nobody, Landlock ABI 7) |
| Existing ignored ABI 6 proof `raw_syscalls_proc_aliases_…` | passes (uid nobody) |
| New probe unit tests (`isolated_attack_tests`, SIGSYS vs other signals, errno rules) | 2/2 pass |
| `cargo test --locked --no-fail-fast` (engine) | 230 passed + 2 (`repository_search_ancestors`, needs non-root, run as nobody) = 232, 0 failed, 2 ignored (above) |
| `cargo clippy` / `cargo fmt --check` on changed files | clean |
| `PhoneBoundaryProofStepsTest` (JUnit, compiled with kotlinc 2.2.20 + JUnit 4.13.2, no Android SDK) | 7/7 pass |
| JVM harness: verbatim `runProbe` on old/new probe under emulated Android 16 policy | old → `boundary_proof_failed:native.signal-31`; new → ok |
| `flutter test --concurrency=1 test/phone_team_setup_test.dart` (Flutter 3.47.8 / Dart 3.13.7) | 43/43 pass (5 new) |
| `flutter test test/native_libs_page_alignment_test.dart` | 3/3 pass; a planted 4 KB-aligned `.so` fails it |
| `flutter analyze` (changed Dart files, l10n) | no issues |
| `tool/qa/preflight.sh` | see commit notes below |

Not run: `BuiltinLinux.kt` compile (`flutter build apk` needs the Android SDK
and the rebuilt bundle), the full serial Flutter suite, any device.

## 5. Owner steps before a release

1. On the build machine (NDK 28.2.13676358, the one the manifest records):
   `engine/phone/tool/build-android.sh --stage-android`. This rebuilds all
   three engine ELFs for both ABIs and rewrites
   `android/app/src/main/assets/aiteam-engine-manifest.json`. The network
   policy here blocked `dl.google.com`, so the NDK could not be fetched in this
   session, and an unofficial toolchain was not used for release binaries.
2. Run `test/native_libs_page_alignment_test.dart` on the staged libraries,
   `tool/qa/verify_phone_engine_apk.py` (it fails with
   `stale_engine_sources_rebuild_and_stage` until step 1 is done), then
   `flutter build apk --release` so the Kotlin change compiles.
3. New release (native change), signed with the same certificate as the
   reporter's install (2.0.0+56 / 2.1.0+57 are the CI sideload signer).

## 6. What still needs a real Android 16 device

- That the reporter's device takes the Landlock tier (it should: GKI 6.12 has
  Landlock and Android 16 allows it). If Samsung's kernel lacks Landlock, the
  proot tier runs instead and its failing control will now be named in
  Details, which is the next thing to read.
- That the Ubuntu leg (proot inside the Landlock sandbox) passes on a real
  device. It has never run on Android before either. Plausible risks: the
  sandbox's seccomp denies `chmod`/`AF_UNIX`, and the dynamic linker cannot
  read `/linkerconfig` under the sandbox (bionic falls back to defaults).
  If one bites, Details will read `ubuntu.<step>.exit-N` or `native.<check>`.
- Samsung-specific (DEFEX/Knox) behaviour for ptrace, `/proc/<pid>` and
  namespaces.
- A full start → Ready → a real task, on the device, with this build.

## 7. Draft reply to the reporter (for the owner to approve)

> Thank you for the very clear report, it made this findable.
>
> What went wrong: Android 16 is the first Android version that lets apps use
> a Linux security feature called Landlock, and your phone's 6.12 kernel has
> it. So on your phone the app picked its strongest protection mode for the
> first time. Our earlier testing was on Android 14 and 15, where that mode
> never runs. Part of its safety check deliberately tries something the team
> must not be allowed to do (start the kernel's "io_uring" feature). Android
> itself blocks that by stopping the program on the spot, instead of returning
> an error the way plain Linux does. Our check treated that as a crash, so it
> failed every time, and it never told the app why.
>
> The fix: the check now runs each of those tests in a separate throwaway
> process, so Android stopping it counts as "blocked", which is what it means.
> Nothing the check requires was loosened. If the check ever fails again,
> Details and Copy all will name the exact step in plain words (for example
> "The Ubuntu step “git-commit” failed inside the team's sandbox"), and it goes
> into the diagnostics export too.
>
> It needs a new app release, not a patch, because the fix is in the native
> part of the app. We'll let you know when it's out. If you can try it then,
> please send the text from Copy all whether it works or not. A Samsung phone
> on Android 16 is exactly the device we need to confirm it.
