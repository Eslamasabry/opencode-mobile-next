# Issue #117 part 2: "Segmentation fault" while unpacking Node.js (2026-11-06)

Report (Poco M4 Pro, MediaTek, 2.1.3+61): built-in Linux setup stops at
`==> Unpacking Node.js` / `Segmentation fault` / `Could not install Node.js`,
also after resetting the app. 2.1.3 already hands a crashing `curl` download to the app.

## Root cause (hypothesis, not reproduced)
Programs inside the app's proot segfault on this phone/ROM pair: first `curl`, now `tar`,
while apt/dpkg and earlier steps work. So it is specific programs/syscalls, not setup logic.
Candidates, in order of how well they fit:
1. The bundled proot is old. `tool/builtin_linux/fetch_proot.sh` pins Termux `proot_5.1.107.94`
   (both ABIs, `libproot.so` strings confirm `5.1.107.94`). Termux's build is now
   `5.1.107.102`: 74 upstream commits later (termux/proot compare v5.1.107.94...v5.1.107.102).
   Ones that bear on exit-139 crashes of ordinary guest programs on arm64:
   `arm64: keep the fixed load addresses out of the way of big mmap(2)s` (37c09de3, 2026-10-12),
   `seccomp: don't rely on the event message to tell which exits to handle` (d4d2a190),
   `seccomp: run fork(2) as clone(2) when the outer policy rejects it` + register fix
   (9bd53079, e2d843ee), `tracee: register a new child even if its fork event has no PID`
   (8b994150), `tracee: don't restart a held child that was killed meanwhile` and neighbours
   (ef54aad1, 37f5cef7, 83c31d2d, b27d81a6). **Not swapped in this fix** (needs a native release
   and its own proof); recorded here as the most promising real fix.
2. proot's seccomp acceleration under a new ROM kernel/seccomp policy
   (termux/proot#56, proot-me/proot#134): the retry below turns it off (`PROOT_NO_SECCOMP=1`).
3. glibc restartable sequences (rseq) with proot: the retry also sets
   `GLIBC_TUNABLES=glibc.pthread.rseq=0` inside the guest.
4. 32-bit/64-bit mix: ruled out by inspection. The rootfs is `ubuntu-base-24.04.5-base-arm64`
   (BuiltinLinux.kt, chosen by `Build.SUPPORTED_ABIS`), and the Node step picks
   `node-v24.21.0-linux-arm64` by `uname -m`.

## What changed
1. **Unpack fallback** (same pattern as the download fallback). New prelude helper
   `oc_untar ARCHIVE DEST STRIP [MEMBER]` (lib/builtin/setup/setup_scripts.dart). It runs
   `tar -xzf`; when tar dies from a signal (exit >= 128) it prints
   `::oc unpack ARCHIVE DEST STRIP MEMBER|-` and waits for marker files next to the archive
   (`.oc-unpack-ack`, `.oc-unpack-done` = `ok` | `fail <plain reason>`). The app side
   (`SetupAppUnpack.kt`, called from `SetupRunner.appUnpack`) unpacks the gzip tar itself
   with `GZIPInputStream` and a small tar reader: regular files, directories, symlinks,
   mode bits (executable), pax and GNU long names, strip-components, one named member.
   Refused: absolute paths, any `..` part (whole unpack fails), writing through a link that
   leaves the destination, archive/dest paths that leave the rootfs. Checksum: unchanged,
   `oc_download` verified the archive before the script gets here. Other tar failures
   (exit < 128) are returned as before, never handed on.
2. **Generic safety net.** `SetupRunner.runScriptRetrying` runs a step once more in
   compatibility mode when it ended with exit 139, or failed after the shell printed
   `Segmentation fault` (scripts that turn a crash into `exit 1`, e.g. `if ! npm ci`).
   Compatibility mode = host `PROOT_NO_SECCOMP=1` (BuiltinLinux.prootEnvironment(compat=true))
   and guest `GLIBC_TUNABLES=glibc.pthread.rseq=0` (added to the `env -i` list of
   `prootCommand(compat=true)`). The default environment set is unchanged (the
   acceptance tests that assert it still see the same keys). Once per step; the row
   says "Trying again in compatibility mode" (new string `phoneSetupStageCompat`, en/ar/zh;
   other locales fall back to English like the rest of the untranslated messages).
   A second segfault ends the step as a failure with the usual plain message.
3. Steps that unpack now use `oc_untar`: Node (setup), OpenCode, AI Team (gc, bd, dolt),
   Paseo's Node. The Termux AI Team unpack keeps plain `tar` (`unpackScriptPlainTar`;
   no prelude and no app there).

## Every step, and what covers it
| Step | Kind | Covered by | Notes |
|---|---|---|---|
| linux (Ubuntu base) | download + unpack | Kotlin, outside proot (`BuiltinLinux.install`) | never ran a guest program, so cannot hit this |
| essentials (curl, ca-certificates, git, ssh) | apt | `oc_apt_install`; generic compat retry | idempotent: dpkg repair first |
| python | apt | generic compat retry | same |
| node | download + unpack | `oc_download` (app fetch) + `oc_untar` (app unpack) + compat retry | `rm -rf /opt/node.new` first, cached archive re-verified |
| opencode / opencode2 | download + unpack + run | `oc_download`, `oc_untar ... member`, compat retry for the `--version` runs | `rm -rf $oc_new` first; the `.tgz` is verified before unpack |
| aiteam | 3 downloads + 3 unpacks + runs | `oc_download` x3, `oc_untar` x3, compat retry | `rm -rf $home.new` first |
| claude | download of one binary, run | `oc_download`, compat retry (`--version` crash reported as exit 1 is caught by the "Segmentation fault" text) | `claude.new` is cleaned by its trap |
| agent-paseo: Node | download + unpack | `oc_download`, `oc_untar`, compat retry | `rm -rf $node_dir.new` first |
| agent-paseo: npm ci | other (npm) | compat retry only | `rm -rf $host_new $host_cache` first; npm output is not echoed |
| voice | download | Kotlin downloader in the app, not proot | unaffected |
| start (server) | other | not a setup script run; unchanged | |
| `.tar.xz` / `.zip` | none used by any step | not implemented | commons-compress is a dependency but no pinned archive needs it |
| `BuiltinLinux` `_fastPrerequisites` (old fast path) | curl + tar | not changed | legacy, runs without the prelude and without the setup runner; judged out of scope (no `::oc` channel there) |

Nothing is skipped silently: a failing fallback leaves the original exit status and a plain
message. A retried step starts from a clean `.new` directory in every case above.

## Tests (worktree /home/eslam/Storage2/wt/fix-117-unpack)
All with the pinned Flutter 3.47.1, serial.

- `flutter test --concurrency=1 test/setup_scripts_test.dart`: 33 passed (new group `oc_untar`, 6 tests:
  healthy tar not handed on; crashing tar handed to the app with the exact `::oc unpack` line;
  member + strip passed on; no app answers -> exit 139 and plain message; plain tar failure
  (exit 2) not handed on; app failing -> exit 139 and "The app could not unpack it").
- `test/setup_engine_test.dart` 60 passed (label set now includes `compat`),
  `test/opencode_native_install_test.dart` 8 (+1 skipped smoke), `test/termux_aiteam_script_test.dart`
  33 (+1 skipped; Termux expects `unpackScriptPlainTar`), `test/phone_agent_setup_engine_test.dart`,
  `test/local_agent_runtime_test.dart`, `test/host_script_pin_test.dart`, `test/setup_termux_*`,
  `test/setup_inventory_test.dart`, `test/setup_preflight_test.dart`, `test/phone_agents_host_test.dart`,
  `test/aiteam_component_test.dart`, `test/mobile_download_consent_test.dart`: passed.
- `test/termux_script_hashes_test.dart`: the six `aiteamVerbScript` snapshots refreshed deliberately
  (`WRITE_SCRIPT_HASHES=1`, only those six changed; they embed the prelude).
- `test/setup_runner_native_test.dart`: 19 passed (real SetupRunner on the JVM; new scenarios
  `segfault-compat-retry`: exit 139 first, ok in compat mode, exactly 2 starts / 1 compat;
  `segfault-report-retries`: exit 1 + "Segmentation fault" text is retried; `segfault-twice-fails`: second
  segfault is a failure, no third run).
- Kotlin: `android/ ./gradlew :app:testReleaseUnitTest --tests '*SetupAppUnpackTest*' --tests
  '*SetupAppFetchTest*' --tests '*SetupStepRetryTest*'`: 22 passed (SetupAppUnpackTest 10: strip + symlink + exec bit +
  dir, named member, missing member, `..`/absolute refused, write through an escaping link refused,
  marker-file handshake, damaged archive, protocol parsing, retry rule, segfault-report rule).
  (`testDebugUnitTest` does not exist in this project; the generated plugin registrant had to lose its
  dev-only integration_test line for the Java compile, an ignored file.)
- `flutter analyze lib test`: no issues (the widgetbook/ tool errors are pre-existing and unrelated).
- `flutter build apk --release --build-number=4601` succeeded, signed with the local key
  (SHA-256 1DE5BF08...D60C, the same signer as the build on the emulator).


## Emulator proof
**Not run.** The emulator lock `/home/eslam/Storage2/wt/emu.lock` was held by another agent for the
whole time I waited (about 25 minutes), so I did not touch the emulator. The APK is built
(`build/app/outputs/flutter-apk/app-release.apk` in the worktree, 4601, local signer) and
`emulator-proof.sh` next to this file does the whole procedure (wrap `tar` and `ln` with real
SIGSEGV, remove /opt/node, install over the existing app, restore afterwards) for whoever holds the lock.
What is proven instead: the shell handshake against a fake crashing tar with the app side played by the
real tar (oc_untar tests), the real Kotlin unpacker on generated archives, and the real SetupRunner's
retry decisions on the JVM. The real `::oc unpack` path through the running app and proot is the part
still unproven on a device.

## Not done / open
- Not run on the owner's Poco M4 Pro. The crash was simulated with a `kill -SEGV` wrapper.
- libproot is not upgraded (see hypothesis 1); that is the next real fix, and needs a native release.
