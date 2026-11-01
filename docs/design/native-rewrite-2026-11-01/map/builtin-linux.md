# Map: builtin-linux (the phone's own Ubuntu, servers, setup, process safety)

Date: 2026-11-01. Source of truth: `/home/eslam/Storage/Code/wt/oc_app-batch1` (branch `int/batch1-2026-11-01`, HEAD `65e3f70a3`). Read-only study; nothing in any repo was changed.
Paths below are relative to that worktree unless they start with `/`. `K/` means `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/`. "Guest" means a path inside the Ubuntu root filesystem. "FS" = the Flutter source.

Feature ids are `BUILTIN-LINUX-NN`. A rewrite agent that reads only this file should be able to find every capability, its files, its data, its channel calls and the traps behind it.

---------------------------------------------------------------------------
## 0. Read this first (the ten things that matter most)

1. **The Kotlin half is already native and is the heavy half.** `K/BuiltinLinux.kt` (4,511 lines) plus ~60 small `Native*.kt` / `Setup*.kt` / `Phone*.kt` helpers (about 9,000 more lines) own the proot runtime, the install, every process, the foreground service, crash recovery, restore after boot/update, idle stop, wake locks and the setup job. Most of it is pure Kotlin with JVM unit tests (about 30 test classes). **It carries over almost as-is**; what goes away is only the `MethodChannel` glue (`MainActivity.handleBuiltinLinux`, `LocalTerminal.register`, `ThermalMonitor.register`, `AppLifecycle.register`, `ProjectExportBridge`).
2. **The Dart half (`lib/builtin`, 25,204 lines in 90 files, plus `lib/termux` 9,442 lines) is the policy and the UI model**: the setup engine and its shell scripts, the server start/probe flow, the recovery consumer, healing, idle-restore, thermal guard, the Linux manager scripts and parsers, process grouping, reset flow, terminal state, agent host orchestration, AI Team orchestration, quota/FactsBook hosts, Termux migration. All of that must be re-implemented in Kotlin (or moved into shared Kotlin). The shell scripts it embeds are language-neutral and should be moved verbatim into assets.
3. **Same package, same signing key, same files directory.** Everything native stores lives in `filesDir` and in four Kotlin-owned `SharedPreferences` files that keep the same names (see §5). The rootfs (`files/linux/ubuntu`, up to several GB) must survive the in-place switch untouched. Kotlin must not rename any of those files or keys.
4. **Kotlin reads Flutter-owned preferences today** (`FlutterSharedPreferences`, keys with the `flutter.` prefix). Three of them are *authority gates*; if they are missing after the switch, the phone server silently stops being restored after reboot/update. See §5.3 and trap T1.
5. **Everything is "prove ownership before you signal"**. Process identity is `(pid, start-ticks, uid)`, never a pid alone; nothing is killed by name or pattern; every restore needs a boot id + nonce + start-tick receipt. This is the most expensive code in the repo to get wrong and has a long list of device-found fixes (§9).
6. **Android constraints the design bends around**: programs may only be executed from `nativeLibraryDir` (so proot/loader/talloc/shmem ship as `jniLibs`, packaged uncompressed-extracted with `useLegacyPackaging = true`, 16 KB aligned); Android stops an app's child processes above 32 ("phantom process" limit); a foreground service started from the background must show its notification within seconds; no foreground service lives forever (`onTimeout`); `/proc/stat`, `/proc/uptime`, `/proc/net/tcp` are denied to apps; `kill(pid,0)` answers EPERM for other apps' reused pids.
7. **proot itself**: `SIGTERM` is ignored by proot; `SIGKILL` on proot detaches its tracees which then run on orphaned (10-27 hot-phone incident). Stop order and the orphan reaper exist for this.
8. **No Termux dependency is needed for the built-in path**, but Termux is still a second host (setup, server, AI Team, migration out of it). The owner must decide whether it survives the rewrite (§12, Q1). The test catalog counts many `termux_*` tests under this area.
9. **Four in-flight Flutter branches are not in this worktree's history** (Linux services supervisor, Services pages, vitals, setup backup/restore). They are complete, tested Dart + bash and are the spec for the next Linux features (§3.G). Decide before the freeze whether to land them in Flutter or port them straight from the branch.
10. **KMP shape:** this whole area is Android-only in its implementation (proot, `/proc`, `Os`, services) but its *rules* are portable. Put the rules in `commonMain` behind a `RuntimeHost` interface (Android = this code; Linux desktop = direct process spawn with no proot; iOS = remote-only, no built-in Linux). See §12.

---------------------------------------------------------------------------
## 1. Scope and boundaries

In scope: `lib/builtin/**` (all of it), `lib/termux/**` (legacy host; listed because the catalog files its tests under this area), the Kotlin files listed in §6, `lib/ui/screens/{linux_manager,phone_setup,this_phone*,running_on_phone_screen,local_terminal_screen,keep_running_screen,termux_*}`, `lib/ui/widgets/phone_server_card.dart`, `lib/platform/thermal.dart`.

Owned by other maps but touching this area (do not duplicate; only the seam is described here):
- AI Team engine (Rust, `engine/phone`, `libaiteam_*.so`, `lib/state/phone_project_engine.dart`) — seam in BUILTIN-LINUX-55/56.
- Paseo / Claude Code / ACP protocol clients (`lib/paseo`, `lib/acp`) — seam in BUILTIN-LINUX-52..54.
- Notifications and widgets (`BackgroundConnectionService`, `StatusNotification*`, widgets) — seam in BUILTIN-LINUX-29 (shared status row).
- Scheduled prompts (`K/ScheduledPrompt*`, `lib/scheduling`) — seam in BUILTIN-LINUX-27 (temporary phone server lease).
- BYO host over SSH (`lib/host/byo_host_ssh_runner.dart` uses `BuiltinLinux.startService`).
- Connection controller / profiles / secure storage (`lib/state/*`) — described only where they store this area's data.

---------------------------------------------------------------------------
## 2. Architecture in one picture

```
Flutter UI (screens)                                    lib/ui/screens/{this_phone*, linux_manager/*, phone_setup/*, running_on_phone, local_terminal}
   │ Riverpod providers: builtinLinuxProvider, builtinServerStarterProvider, phoneServerHealingProvider,
   │ linuxManagerProvider, linuxJobProvider, thermalGuardSlotProvider, setup engine (PhoneSetup.engine)
Dart policy layer                                       lib/builtin/*.dart  (server start, recovery consumer, healing, idle, thermal, watch, manager)
   │ MethodChannel "io.github.eslamasabry.opencode_mobile/builtin_linux" (+ oc/termux dummy, oc/lifecycle, oc/thermal, oc/project_export,
   │   oc/storage, local_terminal methods+events)
Kotlin glue                                             MainActivity.handleBuiltinLinux (every call off the main thread, result posted back)
   │
BuiltinLinux (singleton per process: BuiltinLinux.get(context))          K/BuiltinLinux.kt
   ├─ install / run / start / prootCommand / services map / installer slot / ownership proof
   ├─ recovery (budget, backoff, gate receipt, cold restore, system events, idle)
   ├─ agent private processes (PhoneAgentHost, PhoneAgentSignIn, PhoneAgentAuthRuntime)
   ├─ phone engine launch tiers (PhoneEngineNative)  ← AI Team seam
   ├─ WorkLeases / NativeWorkLeaseHost (wake lock)
   └─ NativeOrphanReaperHost (60 s sweep)
BuiltinServerService (foreground service, specialUse) + StatusNotification (shared status row id 4747)
BuiltinRestoreReceiver (BOOT_COMPLETED, MY_PACKAGE_REPLACED) + BuiltinIdleReceiver (alarm)
SetupRunner + SetupService (install jobs, files/linux/setup.json)
LocalTerminal (PTY via Termux terminal-emulator PtyAccess)
   │ ProcessBuilder([libproot.so, flags…, /usr/bin/env -i HOME=/root … , /bin/sh -c script])
proot → Ubuntu Base 24.04.5 rootfs in files/linux/ubuntu (Node, OpenCode 2, Paseo, Claude Code, AI Team…)
```

Every Ubuntu program, in every launch path, goes through `BuiltinLinux.prootCommand(program, agentUser)` so a terminal, a script check, a server and a setup step all see exactly the same filesystem and environment.

---------------------------------------------------------------------------
## 3. Feature catalog

### 3.A Runtime foundation

#### BUILTIN-LINUX-01 — Bundled proot runtime (no Termux)
- **User sees:** nothing; "In-app Ubuntu" works with one app installed.
- **How:** `tool/builtin_linux/fetch_proot.sh` downloads pinned Termux `.deb`s (proot 5.1.107.94, libtalloc 2.4.3, libandroid-shmem 0.7; aarch64 and x86_64, SHA-256 pinned in the script), renames them `libproot.so`, `libproot-loader.so`, `libtalloc.so`, `libandroid-shmem.so`, patches the `libtalloc.so.2` NEEDED entry with `patchelf`, strips RUNPATH. They live in `android/app/src/main/jniLibs/{arm64-v8a,x86_64}/`. `build.gradle.kts` sets `packaging.jniLibs.useLegacyPackaging = true` so Android extracts them to `nativeLibraryDir` (the only place an app may exec from). All are 16 KB aligned (p_align 0x4000 checked).
- **Launch line** (`BuiltinLinux.prootCommand`): `libproot.so --root-id | --change-id=1000:1000 (agent view)`, `--kill-on-exit`, `--link2symlink`, `-L`, `--sysvipc`, `--rootfs=<files/linux/ubuntu>`, `--bind=/dev`, `--bind=/proc`, `--bind=/sys`, `--bind=<rootfs>/tmp:/dev/shm`, optional agent `/root` view bind, `--bind=<files/projects>:/root/projects`, shared-storage binds (BUILTIN-LINUX-05), fake `/proc` binds (BUILTIN-LINUX-03), optional `/proc/<pid>` mask binds, `--cwd=/root` (or `/root/projects` for the agent), then `/usr/bin/env -i HOME=/root LANG=C.UTF-8 PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin TERM=xterm-256color TMPDIR=/tmp <program…>`.
- **Process env** (`prootEnvironment()`): `PROOT_LOADER=<nativeDir>/libproot-loader.so`, `PROOT_TMP_DIR=<cache>/proot-tmp`, `LD_LIBRARY_PATH=<nativeDir>`; the child environment is **cleared first** (`ProcessBuilder.environment().clear()`), so `.bashrc` / app env never leak in (provider keys written as `{env:X}` in OpenCode config resolve empty; use `{file:}` — memory `inapp-server-clean-env`).
- **Why these flags:** `--link2symlink` because Android denies app hard links (dpkg, git need them) — costs 3–13 %; seccomp acceleration must stay on (1.3–1.6× faster exec/IO; verified by `Seccomp_filters` > proot's own, BUILTIN-LINUX-14); proot-distro's own launch was measured slower (+7 ms start, +17 % IO) and is not used.
- **Risk:** proot is third-party (Termux build); Android 16 + 16 KB pages needed re-checking each bump. Termux ships 5.1.107.95; not adopted (correctness-only fixes).

#### BUILTIN-LINUX-02 — Ubuntu Base install (download, resume, verify, unpack, configure)
- **User sees:** "Linux base" row in phone setup with percent; runs in ~5 s on emulator, ~8–15 s on phone.
- **Image:** Ubuntu Base **24.04.5** from `https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-{arm64,amd64}.tar.gz`; SHA-256 arm64 `a91d5a93…05914f2`, amd64 `e77b6f10…47a0c86`. `imageForDevice()` picks by `Build.SUPPORTED_ABIS[0]` (`x86_64` → amd64, else arm64). Only `arm64-v8a` and `x86_64` are supported (preflight enforces).
- **Download** (`BuiltinLinux.download`): `HttpURLConnection`, connect 20 s / read 60 s, manual redirects (max 5, never auto-follow), `Range: bytes=<have>-` resume of `cache/ubuntu-base.tar.gz`; handles 206 (append), 200 (server ignores range → restart), 416 (already whole → verify). Whole-file SHA-256 (partial hashed first). Mismatch deletes the file and fails (`checksum mismatch`). Progress reported at most every 250 ms. Cancellable (`InstallProgress.cancelled` → `Cancelled`).
- **Unpack** (`unpack`): commons-compress `TarArchiveInputStream(GZIPInputStream)`; skips `..` names; dirs `chmod |0700`, files `|0600`; symlinks via `Os.symlink`; hard links deferred and **copied** at the end (target may come later); progress = compressed bytes read / archive size. The archive stays in cache until unpack completes, so a kill during unpack restarts from the unpack not the download.
- **Configure** (`configure`): new random `rootfsGeneration` (64 hex) written to `builtin_component_writer`; `/etc/resolv.conf` = `1.1.1.1` + `8.8.8.8` (static); `/etc/hosts` localhost only; `/etc/apt/apt.conf.d/01-oc-sandbox` = `APT::Sandbox::User "root";` (proot's fake root cannot switch users); `tmp` mode 1777; `root` dir. Writes `files/linux/ubuntu.ready` containing the image SHA-256 (this file *is* the "installed" flag). `phase` = `idle|installing|ready|failed`, `message` carries the last log line.
- **Channel:** `installUbuntu` (starts a thread, returns at once), `status` (poll). The setup job (BUILTIN-LINUX-34) calls `install(image, progress)` directly.
- **Guards:** `installingRuntime` mutex ("A runtime installation is already running"); `projectStorage.prepare()` before and after; `projectStorage.resetRootfs()` before unpack so projects are kept while a half rootfs is replaced.

#### BUILTIN-LINUX-03 — Guest preparation on every launch
- `prepareGuestForProot()` runs inside `prootCommand` (so every guest process, not only terminals): refuses while installing; `recoverColdComponentUpdates()` (BUILTIN-LINUX-08); `AndroidGroupNames.apply(rootfs)` (adds `aid_<gid>:x:<gid>:` lines to guest `/etc/group` for the app's supplementary groups read from `/proc/self/status`, atomic, idempotent — otherwise Ubuntu's login prints "cannot find name for group ID 3003"); `projectStorage.prepare()`; `BuiltinGitConfig.preferRenames(rootfs)` (appends `[core] createObject = rename` to guest `/etc/gitconfig` unless already set — proot's `--link2symlink` turns git's hard links into absolute-path links that break when a project folder moves → "bad object HEAD").
- **Fake /proc** (`fakeProcBinds`): for `stat`, `loadavg`, `uptime`, `version`, `vmstat` — when the app cannot read the real file (Android 8+ SELinux) a stand-in is written under `files/linux/proc/<name>` and bound over `/proc/<name>` so `top`/`htop` start. Numbers are **fixed** (CPU idle-looking; uptime 7200 s). Real vitals must come from Android, not from inside Ubuntu.
- **Proc mask binds** (`procMaskBinds`, only in the protected "proot tier", BUILTIN-LINUX-56): an empty dir `files/linux/proc/phone-engine-hidden` is bound over `/proc/<pid>` of the app and the engine so a guest cannot read their environ.

#### BUILTIN-LINUX-04 — Project storage lives outside the rootfs
- **User sees:** Remove/Reset keeps projects by default; a removal sheet shows bytes for "runtime" vs "projects".
- **How:** `K/BuiltinProjectStorage.kt`. Projects live in `files/projects` and are bound at guest `/root/projects`. Older installs had them inside the rootfs (`linux/ubuntu/root/projects`); `prepare()` does an atomic whole-directory migration (repeatable, recovers by location) and a link re-pointing pass for git object links (best effort). `measure()` = logical regular-file bytes (links/specials count 0, lstat, no follow; unknown ≠ zero → throws). `removeRuntime(alsoDeleteProjects)` deletes `ubuntu.ready` first (so a partial deletion is never "ready"), then `files/linux`, then the cache archive, and `files/projects` only on the destructive choice.
- **Deletion confirmation:** the native call requires `confirmationName == "OpenCode"` when `alsoDeleteProjects`; Dart repeats the check and uses a *distinct* method `removeRuntime` so an old APK ignoring arguments cannot be tricked into the destructive `uninstall`.
- **Channel:** `projectStorage` → `{runtimeBytes, projectsBytes, measuredAtMilliseconds}`; `removeRuntime`/`uninstall`; `setSharedProjects`.
- **Dart side:** `BuiltinProjectStorage` (Dart class in `builtin_linux.dart`: `keepProjectsFreedBytes`, `deleteEverythingFreedBytes`), sheet in `phone_server_card.dart`.

#### BUILTIN-LINUX-05 — Shared-storage project binds
- **User sees:** a project opened from `/sdcard/...` works inside Linux; "All files access" page appears when needed.
- **How:** `SharedStorageBinds` (end of `BuiltinLinux.kt`) canonicalises paths (`/sdcard`, `/mnt/sdcard`, `/storage/self/primary`, `/mnt/user/N/...` → `/storage/emulated/N/...`; only `/storage/emulated/N/<sub>` and `/storage/XXXX-XXXX/<sub>` accepted; `..` collapsed). Unconfined mode (default): `--bind=/storage` and `--bind=/storage/emulated/0:/sdcard`. Confined mode (once the protected AI Team tier is on): only the exact folders in `files/oc.sharedProjects` are bound, each at its own path and at its `/sdcard` alias. Mirrors `lib/domain/shared_storage_path.dart` — **two copies must stay in step**.
- **Channel:** `setSharedProjects {roots:[…]}` replaces the list (union over all profiles, from Dart; atomic via temp file + rename; empty list deletes the file).
- **Related:** `oc/storage` channel (`K/StorageAccess.kt`): `MANAGE_EXTERNAL_STORAGE` settings page (API 30+) or runtime dialog (≤ 29); asked only when a shared-storage project is opened. `lib/ui/screens/shared_storage_access_flow.dart`.

#### BUILTIN-LINUX-06 — Remove / uninstall the runtime
- `BuiltinLinux.uninstall(alsoDeleteProjects, confirmationName)`: refuses while installing, while a setup job runs (`SetupRunner.get(ctx).running`) or an installer is alive; `reclaimDeadInstaller` + `recoverColdComponentUpdates`; `stopAllServices()`; stops and removes every terminal; stops all tracked processes; requires that the same-uid inventory contains only registered app processes ("Another phone task is still running"); erases phone-engine credential copies; `projectStorage.removeRuntime`; clears `measured`, resets `phase`.
- Dart: `BuiltinLinux.remove()` bumps `setupToolsChanged`. UI: "Remove OpenCode" sheet in `phone_server_card.dart` (two choices), `LinuxResetFlow` (BUILTIN-LINUX-43) wraps it for Reset.

#### BUILTIN-LINUX-07 — Run a one-off guest script (`run`)
- **How:** `BuiltinLinux.run(script, timeoutSeconds, agentUser)` → `{exitCode, output}`. Takes the single **installer slot** (BUILTIN-LINUX-08) with a 10 s admission window ("Another phone task is running. Wait a moment and try again."); runs `/bin/sh -c script` (dash! no bashisms) through `startInstaller(... CHECK)`; stdin closed; reader thread `phone-setup-check`; output tail capped at 64 KiB (kept 128 KiB then trimmed); on timeout the installer tree is drained; `exitCode` is `-1` when output could not be read completely. In `agentUser` mode every line goes through `PhoneAgentCheckOutput.accept` (allow-list; nothing logged).
- Used by: every "check" in setup, Linux manager reads, project folder scripts, git shell, password write, history copy, quota, FactsBook, agent host checks.
- **Rule:** never start a long-running program from `run`: its proot dies with the script (`--kill-on-exit`). Long-running programs are services (BUILTIN-LINUX-13).

---------------------------------------------------------------------------
### 3.B Process ownership, safety and visibility

#### BUILTIN-LINUX-08 — Installer slot, ownership proof, component-update recovery
- **What:** exactly one "installer" proot (`run`, setup scripts, package jobs) may exist. It starts behind a stdin **gate**: the wrapper prints `OC-INSTALL-1 <nonce> <leader-pid>`, waits for the nonce on stdin, then `exec`s the script. The app reads the header (5 s timeout), takes `kernelIdentity` of the proot root and the leader (`/proc/<pid>/stat` field 22 = start ticks, session, group), checks uid, `nonceMatches` (nonce in `/proc/<pid>/environ` as `OC_RUNTIME_OWNER`), commits an `InstallerTicket` (JSON in `builtin_component_writer` pref `ticket`), and only then releases the script. A monitor thread (`phone-installer-ownership`, 100 ms) recomputes the drain plan; **5 consecutive failures** (≈0.5 s) stop the install (a single ESRCH from apt's short-lived processes must not).
- **Admission rules:** before any guest command `recoverColdComponentUpdates()` runs once per process: a leftover ticket from this boot is drained; from a previous boot requires that every same-uid process is a registered app process; pending journals (`*.oc-pending`, BUILTIN-LINUX-37) are rolled back after quiescence.
- **Unknown writer rule:** any same-uid process not accounted for by a known root (parent walk **and tracer walk**), the app itself, or a registered Android process (`:local_pdf`) blocks admission with `ownershipUnknown`. This is surfaced to Dart as channel error `runtime_ownership_unknown` ("Open app settings, force stop OpenCode, then open it again.").
- **Hard-won fixes inside:** ESRCH/ENOENT during a `/proc` read mean "gone" (`NativeProcRace.processGone`); EPERM on `kill(pid,0)` means the recorded pid was recycled by another app so also gone (`NativeInstallerVisibility`; 10-13 force-stop fix); consecutive-failure strikes; four-read plan settle; a `Throwable` in the monitor drains and records `InstallerInterruptedException` so the setup runner re-runs the step.
- **Files:** `K/BuiltinLinux.kt` 289–660, `NativeInstallerOwnership.kt`, `NativeInstallerAdmission.kt`, `NativeInstallerVisibility.kt`, `NativeRuntimeOwnership.kt`, `NativeProcRace.kt`, `NativeComponentUpdateRecovery.kt`.
- **Tests (JVM):** `NativeInstallerAdmissionTest`, `NativeInstallerOwnershipTest`, `NativeInstallerVisibilityTest`, `NativeRuntimeOwnershipTest`, `NativeProcRaceTest`, `NativeComponentUpdateRecoveryTest`; androidTest `BuiltinComponentFilesystemAcceptance`, `BuiltinComponentUpdateAcceptance`, `BuiltinRuntimeAcceptance`, `BuiltinRuntimeReclaimAcceptance`, `BuiltinInstallerProducerQaService`.

#### BUILTIN-LINUX-09 — Stopping a process tree safely
- `BuiltinLinux.stopTree(process, graceMs=3000)`: proot ignores SIGTERM and a SIGKILLed proot orphans its tracees. So: collect `descendants(rootPid)` (children by parent **plus** processes traced by anything in the tree: `NativeProcessTree.descendants` over `/proc/*/stat` ppid and `/proc/*/status` `TracerPid` for pid-1 orphans), snapshot their `ProcEntry`, SIGTERM every descendant (not proot), wait grace for proot to end; if it ended, SIGKILL any recorded straggler *still the same process (pid+start ticks)*; if not, SIGKILL descendants then `destroyForcibly`.
- `stopPidTree(root, exited, graceMs=2000)` for terminal PIDs: SIGHUP+SIGTERM descendants, wait, SIGKILL descendants and root.
- `stopAgentProcess(process)`: exact-tree stop that **throws** "The agent did not stop." if any captured child (pid+start) survives 2 s.
- Pid of a `java.lang.Process` is read by reflection (`ProcessImpl.pid`), with `Process.pid()` first in `PhoneEngineNative`.

#### BUILTIN-LINUX-10 — Orphan reaper (programs a dead proot left behind)
- **Why:** 2026-10-27 the phone ran at 71–83 °C: Android SIGKILLed the agent helper's proot, `--kill-on-exit` never ran, five Claude Code `bash -c` shells and a `flutter_tester` lost their tracer and spun at 100 % for 19 min.
- **Rule** (`NativeOrphanReaper.orphans`): same uid, not the app, parent is init (1), `TracerPid == 0` (a live proot traces everything it runs), state ≠ Z, `exe` is proot's loader (`libproot-loader*.so`, also with ` (deleted)`; so never the app, Android, or the Rust engine), not below any live root the app started (parent or tracer), plus untraced children of such an orphan. Unreadable status/exe is never chosen.
- **Host** (`NativeOrphanReaperHost`): daemon thread `phone-orphan-reaper`; wakes on `soon()` (called after every proot start/end/service-set change) and every **60 s** while any proot runs; ends when idle; each victim SIGTERM → 1.5 s → SIGKILL, re-verified by pid+start ticks before each signal. Counters `orphansReaped`, `orphansLastReapedAtMs` appear in the Performance report.
- **Tests:** `NativeOrphanReaperTest`, `NativeProcessTreeTest`.

#### BUILTIN-LINUX-11 — "Running on this phone": process list and stop
- **User sees:** `lib/ui/screens/running_on_phone_screen.dart` (718 lines): the app's processes grouped by what started them (left behind, OpenCode, one group per Claude Code conversation, agent helper, AI Team, terminal, setup, other services, app), CPU %, memory, age, Heavy / May-be-stuck marks, Stop for non-root rows, warning near the Android limit.
- **Native:** `processes` → two `/proc` scans 1 s apart (`NativeProcScan.scan(uid)` then with `details`: argv, tracer, exe), `NativeProcessList.rows`: `pid, ppid, name, command (≤200 chars, secret-looking flags/assignments masked as •••), cpuPercent (of one core), rssBytes, elapsedSeconds (from CLOCK_BOOTTIME because /proc/uptime is denied), startTicks, owner, session (value after --resume/--session-id/-r)` plus orphan counters. Owner is one of `app, opencode, paseo, agent-sign-in, engine, helper, task, terminal, orphan, other`, derived from the registered root pids of the services map / scheduled phone processes / installer / engine / terminals, extended by parent+tracer walks.
- `stopProcesses(pids)` (max 256): SIGTERM → 2 s → SIGKILL, only live processes of this uid, never the app, never a tree root (`serverRoot` refusal), `notFound`/`invalid`/`stillRunning` reasons returned as `{stopped:[…], refused:[{pid,reason}]}`.
- **Dart:** `lib/builtin/phone_processes.dart` (channel wrapper, `PhoneProcessOwner`), `phone_process_watch.dart` (groups, trees, `heavy` = ≥ 80 % in every reading for ≥ 2 min with gaps ≤ 30 s; `stuck` = orphan, or ≥ 90 % for two readings on a process older than 10 min with no conversation above; roots never stoppable), `lib/ui/screens/this_phone/this_phone_answer.dart` sentence logic. Samples **only while a page is open**.
- **Tests:** `NativeProcessListTest`, `test/phone_process_watch_test.dart`, `test/phone_processes_test.dart`.

#### BUILTIN-LINUX-12 — Phantom-process budget (Android's 32)
- Android 12+ stops an app's child processes above 32, oldest first (emulator Android 14 killed OpenCode's proot + team at 40; Android 15 emulator peak 33 with tuning). The app counts (`BuiltinLinux.appProcessCount()` = entries in `/proc` owned by the app's uid, exposed via local terminal `list`/`processCount`) and the UI warns from **28** (`PhoneProcessWatch.nearLimit`) against **32** (`androidLimit`). Design rule for new work: one proot per service, at most 16 user services, supervisor costs 2 processes (BUILTIN-LINUX-45). The developer-options switch (Android 12L+) is explained, never changed.

#### BUILTIN-LINUX-13 — Named services, logs and exit history
- **Model:** `services: LinkedHashMap<String, Service>` in `BuiltinLinux`. Service names `[a-z][a-z0-9-]{0,31}` double as log names. Known names: `server` (OpenCode), `aiteam` (AI Team supervisor, 8472), `quota`, `factsbook`, `linux-job` (package job), `agent-host.<profile>`, `agent-auth.<profile>`, `phone-engine`, BYO host runner, and (branch) `svc`. `startService(name, script, port, notice)` stops an earlier run of the same name first; each service is its own proot started and stopped **only here** (`startService` / `stopService` / `stopAllServices`). `PHONE_ENGINE` is rejected from `startService` (dedicated launcher).
- **Logs:** `files/linux/server.log` for the server, `files/linux/<name>.log` otherwise; each run renames the previous to `<name>.previous.log`; no size cap (design adds a 4 MiB cap, §3.G). Channel `serverLog {tailBytes}` / `serviceLog {name, tailBytes}` return the tail (default 16 KiB; names validated).
- **Diagnostics:** `builtin_service_diagnostics` prefs: `names` set, `<name>.exit`, `.uptime`, `.exitAt`, `.stopRequested`, `.restarts`, `.launched` (`ServiceDiagnostics`: `restartCount` increments only after the first launch; exit code 137 is labelled `memory_or_phantom_kill`, explicitly not proof of memory pressure). Surfaces in `performance` → `services` and `privateAgentDiagnostics`.
- **Status contract:** `status` returns `installed, phase, message, serverRunning, serverRestartWanted, serverRecoveryGeneration, serverRecoveryAuthority(true), serverRecoveryScheduled, restorePhase, restoreReason, serverPort, serverUptimeMs, services[], abi, bytesUsed` plus the idle block (BUILTIN-LINUX-25). `bytesUsed` is measured at most every 10 min on a background-priority thread and not while a reply holds the wake lock.
- **Dart:** `BuiltinLinuxStatus` (and the Dart-side enums `BuiltinLinuxPhase`, `BuiltinServerRestorePhase`, `BuiltinServerRestoreReason`).

#### BUILTIN-LINUX-14 — Performance report
- `performance` → `{serverRunning, prootMode: seccomp|ptrace|unknown, prootFilters, serverFilters, workHeld, workLeases{held,activeCount,kinds}, services{…diagnostics}, orphansReaped, orphansLastReapedAtMs}`. prootMode compares `Seccomp_filters:` (Linux ≥ 5.9) of the server vs its proot (server must have one more than proot). Shown under "This phone › Details" and in the problem report.
- Dart: `BuiltinPerformance`, `BuiltinProotMode`, `BuiltinWorkLeaseStatus`, `BuiltinServiceDiagnostics`; `test/builtin_performance_diagnostics_test.dart`.

---------------------------------------------------------------------------
### 3.C The OpenCode server on the phone

#### BUILTIN-LINUX-15 — Start the in-app OpenCode server and wait until it answers
- **User sees:** one Start / auto-start; card "Starting…", then connected; failures in plain words with Details.
- **Flow** (`lib/builtin/builtin_server.dart` `startBuiltinServer`, the single start path used by setup, the opening card, healing and recovery):
  1. refuse if the profile still needs OpenCode 2 migration unless `migrateLegacyHistory`;
  2. a lost Keystore password is replaced (`renewBuiltinPassword`: 32 random bytes, base64url) and saved;
  3. `run(writePasswordScript)` — `umask 077; mkdir -p /root/.oc-builtin; printf '%s' <pw> > server.password.tmp; mv` (guest `/root/.oc-builtin/server.password`);
  4. `startServer(script, port=4097, restoreRecipe{version:1, profileId, runtime:"opencode2"})` or, for automatic recovery, `restartServer(script, port, expectedGeneration)`;
  5. poll `serverProbe(baseUrl=http://127.0.0.1:4097, user opencode, password)` every **2 s up to 90 s**; each miss reads native `status`; `!serverRunning` → `exited`; deadline → `timedOut(90)`; success → (recovery) `confirmServerRecovery`.
- **Failure vocabulary** (`BuiltinStartProblem`): `exited, timedOut, interrupted, passwordMissing, passwordNotSaved, refused, componentRecovery (runtime_ownership_unknown), busy`; `retryable` only for `exited|passwordNotSaved|refused`. Details string = `The phone server could not start (<step>: <code>; <native detail>)` where native detail is `serverStartDetail`: exception kind, message and first 3 stack frames (≤ 600 chars) — **for the Details fold only** (rule "no raw errors").
- **`BuiltinServerStarter`** (ChangeNotifier): `starting`, `startingProfile`, `bringingUp` (10 s handover window), `readyCount` / `manualReadyCount`, `failureFor(profile)`, `autoStartIfStopped` (at most once per `allowAutoStart()`, never from a failure path so a dying server is not looped), `observeStatus`, `confirmRecovered`, `beforeManualStart` (persists the phone owner pointer before an explicit start), `saveRenewedPassword`. After a successful start it clears the deliberate-stop mark, restarts the quota collector if it was set up and the AI Team if the profile has it on.
- **Tests:** `test/builtin_server_test.dart`, `builtin_server_autostart_test.dart`, `builtin_password_self_heal_test.dart`.

#### BUILTIN-LINUX-16 — Profile, password and recognising "the in-app server"
- The in-app server is a normal `ServerProfile` with `baseUrl = http://127.0.0.1:4097`, `backend openCode`, username `opencode`, flavor OpenCode 2. `BuiltinLinux.managesServerUrl` (host 127.0.0.1, port 4097; deliberately not Termux's 4096), `looksLikeInAppServer`, `isInAppServer` (shape + Ubuntu installed), `findBuiltinProfile` (prefers active, then one pending OC2 migration, then first), `ensureBuiltinProfile`. Password: Keystore (`pw.<profileId>`) → rewritten into the guest file at every start, so a lost Keystore secret is simply replaced.
- The server password reaches the server through `OPENCODE_SERVER_PASSWORD` / `OPENCODE_PASSWORD` env read from the file (not on any command line, not in logs). Same-uid guest processes can read `/proc/<pid>/environ` (documented trust zone, option A in `docs/verification/in-app-ubuntu-trust-2026-10-07.md`; Landlock is not in Android GKI kernels).

#### BUILTIN-LINUX-17 — The server script (what runs inside proot)
- `BuiltinLinux.serverScript()` (Dart) and `NativeServerRecipe.restorationScript()` (Kotlin; **the canonical cold-start command: nothing is Dart-supplied after a reboot**). Both: `set -eu; mkdir -p /root/projects; cd /root/projects` (never the home folder, OpenCode would scan it); require non-empty `/root/.oc-builtin/server.password` (else exit 78); isolate OpenCode 2 under `/root/.oc-opencode2/{data,cache,state,config}` with `XDG_*_HOME`, `OPENCODE_CONFIG_DIR`, `OPENCODE_DB=/root/.oc-opencode2/data/opencode/opencode.db`, unset `OPENCODE_CONFIG(_CONTENT)`; if an OpenCode 1 DB exists and the OC2 DB does not, exit 74 ("Open the app to finish moving your conversations."); export `OPENCODE_SERVER_USERNAME=opencode`, password vars; `exec opencode2 serve --hostname 127.0.0.1 --port 4097`. The Dart variant also runs the OC1 history copy (`openCode1HistoryCopyScript`) and the phone-agent context script (writes agent instructions; never blocks the start).
- **Binds to 127.0.0.1 only**; Basic auth.
- **Pins:** OpenCode 2 `2.1.1` from npm tarballs `@opencode/cli-linux-arm64` (sha256 `cfa3ae63…01ca066`) and `@opencode/cli-linux-x64-baseline` (`216d3861…bba2858`), member `package/bin/opencode`, installed to `/opt/opencode2` with `/usr/local/bin/opencode2` link.

#### BUILTIN-LINUX-18 — Deliberate stop, auto-start and "once" rules
- `DeliberateServerStop` (`lib/builtin/deliberate_stop.dart`): pref `oc.phoneServerStopped.<profileId>`; set by Stop on This phone, cleared by any start that ended with the server answering; keeps the app from saying "starting again" or from reviving a server the person turned off.
- App open/resume allows exactly one automatic start (`allowAutoStart`), main.dart `_autoStartThenConnect` calls `autoStartIfStopped`.

#### BUILTIN-LINUX-19 — Gated server launch and the runtime receipt
- Every restorable server start (`startGatedServer`) writes a *prepared* `NativeRuntimeReceipt` first, launches `env OC_RUNTIME_OWNER=<nonce> setsid /bin/sh -c <gate>` where the gate prints `OC-GATE-1 <nonce> $$`, blocks on stdin, verifies the nonce, `exec`s the script. The app verifies leader is its own session/group leader and a descendant of the proot root with the right nonce and uid, then (under `recoveryLock`) re-checks admission (wanted, not user-stopped, bound profile, generation, schedule id, idle/event authority) and commits: receipt with root+leader identities, recipe JSON, `restoreOwner`. Only then is the nonce written to stdin. If anything fails before release the payload **never ran**, so the old plain `start()` may be used as a fallback (`GateUnavailable.legacySafe`); if it fails after release the restore is disarmed with `ownershipUnknown`.
- Receipts contain only kernel identities (pid, start ticks, parent, group, session, boot id, nonce, generation, ≤128 other runtime identities) — no argv, output, password.
- `ownership(profile)` JSON under `oc.builtinRuntimeOwnership.<profile>` in `builtin_server_recovery`; pending drain copy under `oc.builtinRuntimeDrain.<profile>` + `drainOwner`/`drainAll`.

#### BUILTIN-LINUX-20 — Native crash recovery with backoff and a budget of 3
- When `server` exits on its own (`handleServiceExit`), if recovery is enabled and admitted (`nativeAdmitted`: supervision enabled for the bound profile, wanted, not user-stopped, supervision generation matches, migration marker valid, automation policy permits), `scheduleNativeRecovery` starts thread `phone-native-recovery`: waits `RestartBackoff` (1 s doubling to 60 s; a run ≥ 30 s resets; persisted as `backoffNext/backoffAt/backoffBoot` so it survives the process but only within one boot), reserves attempt N (`NativeRecoveryBudget.reserve`, durable, **max 3**), launches with `ServiceLaunch(nativeOwned=true)`. The foreground service is **kept** during the delay (re-requesting a background FGS start would lose Android 12+ admission).
- Budget record `oc.builtinRecoveryBudget.<profile>`: `{version:1, attempts, nextAt, pending, eventId, recoveryGeneration, confirmedAt, revision}`; confirmed attempts are archived in `oc.builtinRecoveryReceipts.<profile>` (≤16) until Dart acks them (`ackServerRecoveryReceipt`) — Dart turns them into "While you were away" entries.
- **Budget resets:** a manual confirmed start (`confirmManualServerStart`), the app open with a healthy server once per visit (`confirmServerPresence`, refused while a restore is reserved/unconfirmed), and a boot/app-update event (`afterSystemEvent`: a settled count starts over; 10-25/10-26 fixes — otherwise every fourth update left the server off).
- Cold app (`setActivityResumed(false)`) cancels an *unconfirmed* automatic attempt but never a manual/confirmed process.

#### BUILTIN-LINUX-21 — Dart recovery consumer (foreground health + receipts)
- `BuiltinServerRecovery` (`builtin_server_recovery.dart`): foreground-only timer: 5 s while a restart is in play or the app's own connection to this server is down; 45 s steady (it costs two native status reads + a health probe each); immediate check on resume; states `idle, paused, stopped, checking, restarting, waiting, ready, exhausted, unconfirmed, storageUnavailable`; `maxAttempts = 3`; `retryDelay 15 s`. Moves legacy v1 budget to native (`stageServerRecovery`, `bindServerRecovery{profileId, legacyBudget, enabled}`); writes the migration marker `oc.builtinRecovery.<profileId>` (exactly `{"version":2,"nativeAuthority":true}`, `builtin_server_recovery.dart:388`); calls `onRestart(profileId, eventId, at)` for each native receipt so the shell records an automatic act (`AutomaticActKind`); `suspendForProfile` must be awaited before the scoped-key sweep on profile deletion (`audit2_*_deletion_test`).
- Channel methods: `stageServerRecovery, bindServerRecovery, serverRecoveryBudget, serverRecoveryReceipts, ackServerRecoveryReceipt, updateServerRecoveryReceipt, confirmManualServerStart, confirmServerPresence, unbindServerRecovery, deleteServerRecovery, cancelServerRecovery, confirmServerRecovery`. All map any failure to channel code `recovery_unavailable` ("The phone server could not restart.").
- **Policy gate:** `AutomationPolicy` (`oc.automation.<profileId>`, `lib/state/automation_policy.dart`): `version 1`, `supervision ∈ {high,balanced,autonomous}`, `behaviors.restartPhoneServer && behaviors.pollRestartHealth` must be true for native to restart anything (absence = bound default = allowed; malformed = denied).
- **Tests:** `test/builtin_server_recovery_test.dart`, `builtin_server_recovery_native_test.dart`, `builtin_recovery_bridge_test.dart`, `NativeRecoveryBudgetTest`.

#### BUILTIN-LINUX-22 — Restore after reboot or app update
- `BuiltinRestoreReceiver` (manifest, not exported) for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` → `prepareServerEventRestore(event)` returns an in-memory `NativeServerRestoreTicket` (never serialized, never rebuilt from Intent extras) or null → `BuiltinServerService.startForRestore` (foreground-service start with action `RESTORE_SERVER_EVENT`) → after the notification is up `restoreServerAfterSystemEvent(ticket)` spawns `phone-runtime-system-restore`: waits ≥ 1 s, re-validates authority, **drains the previous boot's server exactly by receipt** (or, after reboot, requires cross-boot quiescence), reserves a fresh budget attempt (`afterSystemEvent`), rewrites the recipe's `packageVersion` upward on update (`recipeForEvent`: `PACKAGE_REPLACED` accepts `packageVersion >= recipe.packageVersion`; `BOOT_COMPLETED` requires exact compatibility), launches the canonical script.
- Admission (`NativeServerEventPolicy.admitted`): idle policy off and not stopped, supervision & restore owners equal the recipe's profile, attempts ≤ 2 after the event reset, saved authority booleans (`wanted`, `!userStopped`, `enabled`, `owner`, `restoreOwner`) all true, no pending component recovery or installer ticket.
- **In-place update implication:** the restore compares `packageVersion` (= `versionCode`) and `rootfsGeneration`; the native build must have `versionCode` ≥ the Flutter build or the recipe is rejected.
- Emulator proof recipe: `memory/restore-proof-recipe.md` (edit `shared_prefs/builtin_server_recovery.xml`, `pm unstop`, `install -r`, poll `ps | grep opencode2`). `dumpsys activity broadcasts history` shows delivery; the receiver logs nothing.
- **Tests:** `NativeServerRestoreEventTest`, androidTest `BuiltinRestoreAcceptance`.

#### BUILTIN-LINUX-23 — Restore after Android reclaims the process (START_STICKY)
- `BuiltinServerService` returns `START_STICKY` only while restoration is armed (`serverRestorationArmed`: recipe compatible, receipt not merely prepared, `nativeAdmitted`, attempts < 3, server not running). A null-intent restart runs `restoreServerAfterProcessReclaim` (cold-restore worker `phone-runtime-restore`) after the same drain/reserve/launch sequence; an unarmed null-intent start calls `rejectServerRestoration` (records a reason, captures a pending drain, stops) — so a swiped app that the user stopped never revives.
- `restorePhase` (`idle|waiting|restoring|unavailable`) and `restoreReason` (`stopped|policyDisabled|budgetExhausted|ownershipUnknown|storageUnavailable|componentRecoveryRequired|systemTimeout`) are in memory + `restoreReason` pref; **not shown in the UI today** and the receiver does not log (dev only).
- `onTimeout` (Android's foreground-service limit): `stopRuntime(startId, "systemTimeout")` — nothing assumes unbounded lifetime (Android 15 caps `dataSync` at 6 h per 24 h; `specialUse` is used instead, with a Play-style property describing it).

#### BUILTIN-LINUX-24 — Stop semantics
- `requestServerStop(reason, includeOther, onRevoked)` (main thread, never waits for I/O): bumps `wantedRevision`, `recoveryGeneration`, `supervisionGeneration`, cancels recovery/idle/event tickets, writes `wanted=false, userStopped=true`, removes `restoreOwner` and the recipe, **captures the owed drain** (`drainOwner` + copy of the ownership receipt), sets `restoreReason`. Then `stopServer` / `stopService("server")` / `stopAllServices(revision)` run on a thread: remove service (stopTree), `drainPendingOwnedServer()` (SIGTERM → 3 s → SIGKILL exact identities, 5 s total).
- Entry points: UI Stop (channel `stopServer` calls `requestServerStop()` first, then stops), the notification's **Stop** (service action `stop` → `stopRuntime`), `stopService("server")`, uninstall.
- `stopServer(forPhoneEngineSetup)` remembers script+port to restart the server if the engine start fails (`restoreStoppedPhoneServer`).

#### BUILTIN-LINUX-25 — Idle stop and resume of the phone runtime
- **User sees:** Settings › Keep running: "Stop the phone server after N idle minutes" (1–60); a plain notification "come back" when it stopped for idleness; reopening restarts everything.
- **Native:** `NativeIdleState` (durable snapshot `idleState` in `builtin_server_recovery`: `enabled, idleMinutes, counter, generation, stopped, helperStopped, owner, helper`), `IdleStopPolicy` (deadline only when background AND confirmed idle), `NativeIdleHeartbeat` (Dart volatile work evidence, fresh for 45 s; unknown/expired never proves idle), `NativeIdleTimer` (AlarmManager exact-ish via `BuiltinIdleReceiver`, request code 4095, 8 s receiver budget; the alarm only *rechecks*), `NativeIdleReturnNotification(+Host)` (channel `opencode_builtin_server`; shown only when stopped-for-idle, wanted, not user-stopped, background). A stop requires: `workLeases.logicalWorkBusy() == false` AND heartbeat `busy == false` AND the services set ⊆ {server, agent-host.<owner>} AND a complete ownership plan. The stop captures a drain (including the helper), marks `restoreUnavailable("idleStopped")`.
- **Channel:** `setPhoneServerIdlePolicy{enabled, idleMinutes}`, `observePhoneAgentWork{profileId, busy}`, `resumeIdleStoppedPhoneServer{profileId, expectedIdleGeneration}`, `completePhoneServerIdleResume{…}`. Errors → `idle_resume_unavailable`.
- **Dart:** `PhoneServerIdle` (foreground restore: status → resume server → `resumeAgents` (helper) → status → complete; epoch-guarded), `PhoneAgentWorkWatch` (one 15-min lease per profile renewed every 5 min, ceiling 6 h, unknown work never starts a lease), `BuiltinLinuxStatus.serverIdle*`.
- **Tests:** `IdleStopPolicyTest`, `NativeIdle{State,Timer,Heartbeat,ReceiverDispatch,ReturnNotification}Test`, `test/builtin_idle_policy_test.dart`, androidTest `BuiltinIdleAcceptance`, `test/paseo_idle_*`, `phone_agents_idle_host_test`.

#### BUILTIN-LINUX-26 — Healing: reconnect, replace, retry
- `PhoneServerHealing` (`lib/builtin/phone_server_healing.dart`, 640 lines; app-lifetime provider `phoneServerHealingProvider`): owns the recovery, lifecycle report, idle, agent-work watch; follows the selected/owner profile (`BuiltinServerOwner` pointer pref `oc.builtinServerOwner`), foreground health poll, **connect retry with 10–60 s backoff after a failed connect** (QA-03: one connect per start left a healthy-but-unconnected server), replaces a running-but-stale process (password rejected, or silent past 90 s of native `serverUptimeMs`) through the app's own tracked tree, claims the profile before an explicit start, clears on profile deletion (`deleteProfileAndLocalData` sweeps `oc.builtinRecovery.*`, `oc.phoneServerStopped.*`, etc.).
- UI wording: "isn't answering" (process alive) vs "stopped".
- **Tests:** `test/phone_server_healing_test.dart` (41 behaviours).

#### BUILTIN-LINUX-27 — Temporary phone server for a scheduled prompt
- `acquireScheduledPhoneServer(profile, password, deadlineEpochMs)` / `releaseScheduledPhoneServer(token)` (invoked from `ScheduledPrompt*` native code, not Dart): only when installed, not installing, not protected; starts `opencode2 serve` wrapped in `timeout --signal=TERM --kill-after=5s <seconds>s` so Ubuntu bounds it even if Android kills the app; password must match `/root/.oc-builtin/server.password`; a watchdog thread releases at the budget; never arms restart policy or a permanent foreground service; lease state in the Flutter prefs `ScheduledPromptPhoneLease` (see scheduling map).

#### BUILTIN-LINUX-28 — App-exit recovery and "Keep running" guidance
- `K/AppLifecycle.kt` (`oc/lifecycle`): `launchReport` → newest `ApplicationExitInfo` since last launch (classified force-stop (subreason 21 parsed from `toString`) / low memory / crash / killed / update / normal), `previousServices` (the running-services set kept on disk on every change with `commit()`), `lastCrash` (`NativeCrashStore`); `exitHistory{limit}`; `keepAliveInfo`; `openKeepAliveSetting{setting}` (battery-optimisation exemption + per-maker pages: Nubia/RedMagic, Xiaomi, Oppo/OnePlus/Realme, Vivo, Huawei/Honor, Samsung, all guarded intents).
- Dart `AppExitRecovery` (360 lines): once per process; after a *notable* exit while the phone's OpenCode ran, shows ONE notice ("Android closed OpenCode Mobile at 00:06 …", folds away 15 s after the server is back), starts the built-in server (and AI Team) again through the shared starter, keeps a typed record in the problem report; `agent_helper_diagnostics.dart` adds the last helper exit.
- `LifecycleReportController` (`lib/state/lifecycle_report.dart`): foreground, read-only "everything is back" check — authenticated probe of the built-in server only; a pid alone is never evidence.
- UI: `lib/ui/screens/keep_running_screen.dart`, `lib/ui/widgets/thermal_notice.dart`.

---------------------------------------------------------------------------
### 3.D Foreground service, notification and wake locks

#### BUILTIN-LINUX-29 — Foreground service `BuiltinServerService`
- `foregroundServiceType="specialUse"` (property text: "Runs the user's own coding-agent server (OpenCode) inside the app, on this device only…"), `FOREGROUND_SERVICE_SPECIAL_USE` permission; starts with the first service and ends with the last (`serviceSetChanged`). `start(context, title)` uses `startService` while the app is in the foreground and `startForegroundService` (a *promise*: notification within seconds) when in the background; a `stop` arriving before the promise is kept (`stopPending`) so the notification is shown first. Notification = the one shared status row (`StatusNotification`, id 4747, channel `opencode_live_connection`, owners `live` and `server` — the row stays while either runs; throttled to one update per 2 s; action **Stop OpenCode**). Wording comes from Dart (`notice` per service; newest non-null wins, e.g. "OpenCode and AI Team are running") with a minimal native fallback ("Ready", "Paused").
- The service also stays up while an unresolved recovery delay is pending or work leases are held (`NativeRecoveryBudget.keepsForeground`, `workLeases.foregroundHeld`).
- `onDestroy`: ends the owner, cancels pending restore tickets, revokes foreground work leases.

#### BUILTIN-LINUX-30 — Setup foreground service `SetupService`
- Separate `specialUse` service (channel from Dart text, id distinct) shown during a setup job; mirrors overall percent; on end leaves one ordinary notification ("done" opens the app on the phone server screen via launch action `LAUNCH_ACTION_DONE`; "stopped" opens the progress screen via `LAUNCH_ACTION_PROGRESS`, which offers Continue). Texts are sent by Dart already localised (`texts{channel,title,progress("{percent}%"),done,stopped}`). `StatusNotification.begin` clears the stale setup "ready" row.

#### BUILTIN-LINUX-31 — Bounded work leases and the CPU wake lock
- **Rule:** nothing assumes unbounded background time. `WorkLeases` (pure, monotonic clock): per-lease hold ≤ **15 min** (renewed by the owner every **5 min**), aggregate lifetime ≤ **6 h** from the first acquisition (overlapping leases cannot extend it), ≤ 128 leases, kinds `CHAT, SETUP, SIGN_IN, TERMINAL`; a backward/negative clock closes all holds. `NativeWorkLeaseHost` owns a `PARTIAL_WAKE_LOCK` tag `OpenCode:work` and the logical owners: named chat leases (regex `[A-Za-z0-9_.-]{1,80}`), agent chat leases per `(profile, name)` admitted only while `agent-host.<profile>` is alive and the FGS is running, adopted process owners (setup, sign-in), terminal sessions. A capped/closed logical lease **cannot be renewed** — never release-and-reacquire to renew.
- **Channel:** `holdAwakeForWork{on, forMs}` (legacy reply lease), `setChatWorkLease{leaseId, on, forMs}`, `setPhoneAgentChatWorkLease{profileId, leaseId, on, forMs}` → `{held, capped}`.
- `revokeForegroundWork()` on service destroy / Stop; `serverGone()` and `helperGone(profile)` drop leases whose process vanished.
- **Tests:** `WorkLeasesTest`, `NativeWorkLeaseHostTest`, `test/builtin_work_leases_test.dart`, androidTest `BuiltinWorkLeaseAcceptance`.

#### BUILTIN-LINUX-32 — Reply watch: keep awake while a reply runs, time replies
- `ReplyWatch` (`lib/builtin/reply_watch.dart`): while any session of the connected **in-app** server has a reply running: lease 10 min, renew every 5 min, ceiling 6 h of one unbroken stretch, released the moment no reply runs; times prompt → first output → end into `reply.first_token` / `reply.done` perf traces and the last in-app timing (pref `oc.replySpeed.inApp`, device-wide JSON) shown on This phone ("Reply speed", plus Details: Linux speed mode, wake lock, first-words split, model). Reason it exists: the owner's "slow vs Termux" report (build 2063) — proot was not the cause (§Performance); the free rate-limited model was.

---------------------------------------------------------------------------
### 3.E Phone setup (install engine)

#### BUILTIN-LINUX-33 — Setup engine (Dart): check first, so every run is a resume
- `ChannelSetupEngine` (`lib/builtin/setup/setup_engine.dart` + `setup_engine/{start,poll,progress_helpers,job_record}.dart`, 1,600 lines total): builds the component list from the registry for the selection, runs every component's **check script** first and marks installed ones `skipped`, hands the rest to the native job runner, then polls `setupStatus` every 500 ms (60 s idle poll when the app is backgrounded; read timeout 10 s), turns `setup.json` into `SetupProgress` with weighted percent and an ETA that rescales the per-component estimates by measured pace. The last component `start` is a *job step*: the native job waits while the app's `SetupFinisher` starts OpenCode and connects, then `completeSetupStep`. `restore()` reads the persisted job (a job interrupted at the end only has the start left). Contract types in `setup_contract.dart` (`SetupEngine`, `SetupComponent`, `SetupProgress`, `SetupHostKind{builtin,termux}`, `SetupComponentIds`, `setupToolsChanged` notifier).
- `PhoneSetup.engine` / `PhoneSetup.termux` singletons; `SetupAuditStore` records what setup did (per-profile pref).
- UI model used by `phone_setup_*` screens (start, progress, ready, customize sheet, welcome entry, Termux job screen, routes) and `this_phone_screen`. Notification taps are routed to the progress screen (`test/phone_setup_notification_route_test.dart`).

#### BUILTIN-LINUX-34 — Setup runner (Kotlin): the durable job
- `K/SetupRunner.kt` (621 lines) + `SetupJob.kt` (state model + `SetupProtocol`) + `SetupService.kt` + `SetupPersistence.kt` + `SetupStepRetry.kt` + `SetupScriptCompletion.kt` + `SetupWorkScopes.kt`.
- A job = ordered components, each `native` (Linux base: `BuiltinLinux.install`), `step` (waits for `completeStep`), or a script run in Ubuntu through the installer slot (`startInstaller(..., INSTALL)`). One worker thread, under `SetupService`. State persisted to **`files/linux/setup.json`** (≤ 4 writes/s; atomic temp-file + `fsync` + `Os.rename`; failure raises `SetupPersistenceException` code `setup_persistence`), log to `files/linux/setup.log`; a job found `running` by a new process is marked **interrupted** ("Continue" resumes).
- Per component: `id, state (pending|running|done|failed|skipped), weight, stage, done, total, percent, version, error`; job: `jobId, state, components, startedAt…, logTail`.
- **Protocol** (`::oc` lines on stdout): `::oc stage <label ≤120>`, `::oc bytes <done> <total>`, `::oc percent <0-100>`, `::oc version <text ≤80>`; anything else is log text.
- **Interrupted-step retry:** an install step ended by the app's own safety check (`InstallerInterruptedException`) or by SIGTERM/SIGKILL (matching `terminated with signal (9|15)`) runs again up to **3** times ("Interrupted. Trying again"); a script that fails by itself is reported at once. PRoot exits 0 when its guest is signalled, so every script is wrapped (`SetupScriptCompletion`): `/bin/sh -c '<script>'; printf '\n::oc-setup-result <uuid> %s\n' $?` and the result line is consumed, never shown.
- **Disk admission** (`SetupDiskSpace`): free space ≥ max(2 × download bytes, 300 MB) at install; ≥ 128 MB to launch the agent host; message "There is not enough free space on this phone…".
- **CPU admission:** `SetupWorkScopes` — one lease per whole job (even before the worker is scheduled); a revoked/capped job cannot refill it with a child.
- **Channel:** `startSetup{jobId, components[], params, texts}`, `setupStatus`, `cancelSetup`, `completeSetupStep{jobId,id,ok,error,version}` (spec parsing in `MainActivityBuiltinSupport.setupSpec`).
- **Tests:** `SetupStepRetryTest`, `SetupScriptCompletionTest`, `SetupWorkScopesTest`, `test/setup_engine_test.dart` (53), `setup_transaction_test` (18), `setup_runner_native_test`, `setup_persistence_failure_test`.

#### BUILTIN-LINUX-35 — Component registry and install scripts
- `lib/builtin/setup/components.dart` (623 lines, `setupComponents(l10n, params, host)`); each component = `id, title, shortTitle, checkScript (exit 0 + last line = version), installScript (idempotent, `::oc` reporting), removeScript, presenceScript, sizeScript, dependsOn, required, defaultOn, estimatedSeconds, downloadBytes, installedBytes, downloadSize evidence, native, agentUser, jobStep, app`.
  | id | what | notes |
  |---|---|---|
  | `linux` | Ubuntu Base (native) | required; check `[ -s /etc/os-release ] && . /etc/os-release && echo ${VERSION%% *}` |
  | `essentials` | curl, ca-certificates, git, openssh-client | required; apt with status-fd progress |
  | `python` | Python 3 | default on (Ubuntu Base has none); removable |
  | `node` | Node.js **v24.21.0** official tarball (sha256 arm64 `724282c3…eabfe5d5`, x64 `6e1db87e…88dc5ff`) to `/opt/node`, links in `/usr/local/bin` | required; NOT Ubuntu's npm (10 min under proot vs seconds); npm needs `--foreground-scripts` under proot ("Exit handler never called!") |
  | `opencode` | pinned native OpenCode 2 (2.1.1) | download → verify sha → unpack member only → `--version` check → throwaway `serve` health probe on up to 3 random ports (`/api/info`, `/api/health`) → journalled activation + link; never npm (wrapper failed on the owner's phone, build 2062) |
  | `aiteam` | Gas City `gc` 1.4.1, Beads `bd` 1.2.2, Dolt 2.3.5 Linux tarballs (SHA pins in `aiteam_scripts.dart`); pack `gastownhall/gascity-packs/gastown` @ `33d3a430…` | opt-in, install only |
  | `start` | job step: start OpenCode + connect | required |
  | `voice` | speech model (app-side component, `SetupAppComponent`, works before Linux) | only if `supportsVoice` |
  | agent components | `agent-user` (user `oc`, uid/gid 1000, `/home/oc`), `agent-node`, `agent-paseo` (Paseo **0.11.2**, lockfile asset `PaseoPhoneScripts.packageLockAsset`), per-agent recipes (Claude Code `2.1.283` / `2.1.294` tested versions, Codex, Gemini, Qwen, Kilo, Cline, Goose, OMP-ACP, fx) | `lib/builtin/agents/*` |
- Install scripts all begin with `setupPrelude` (POSIX sh helpers: `oc_stage`, `oc_version`, `oc_download URL FILE SHA256 [BYTES]` (resumable curl `-C -`, 0.5 s byte reports, per-attempt resume from current size, retry on 6/7/18/28/35/52/55/56/92 and HTTP 408/429/5xx up to 4 times with backoff, never silently discards resumable progress on 33/36, deletes on checksum mismatch), `oc_apt_install` with `APT::Status-Fd`, stale-list refresh once a day, repair of interrupted dpkg first, `dpkg -s` verification). Scripts are run with dash and tested under dash+bash on the PC (`test/setup_scripts_test.dart`, `setup_apt_repair_test.dart`).

#### BUILTIN-LINUX-36 — Preflight (before anything downloads)
- `preflight.dart` using `oc/voice getDeviceInfo` (ABI, free storage, total RAM — not Android `memoryClass`): supported ABIs `{arm64-v8a, x86_64}`; **min total RAM 1,800 MB** (a nominal 2 GB phone reports ~1,972), "may be slow" under **3,072 MB**; required free space `max(2 × download, 300 MB)` or, with known installed bytes, `download + installed + 64 MiB`; low-space opens the Storage settings (`openStorageSettings` channel, `ACTION_INTERNAL_STORAGE_SETTINGS` then App Info). Estimates never count as download-consent evidence (`DownloadSize`).

#### BUILTIN-LINUX-37 — Setup component update journals (rollback)
- `component_updates.dart` prelude: `oc_update_activate target candidate` writes `<target>.oc-pending` (`new` | `existing`) and keeps `<target>.oc-good`; `oc_update_recover` completes or rolls back after an interruption; every guest launch first calls `recoverColdComponentUpdates` natively (`NativeComponentUpdateRecovery`: fixed target list `/opt/opencode`, `/opt/opencode2`, `/opt/oc-claude/claude`, Paseo and Claude version dirs, link files; refuses unsafe ancestry; atomic renames with directory fsync). Failure text: "A component update could not be restored. Run setup again."

#### BUILTIN-LINUX-38 — Component removal and inventory
- `ComponentRemoval` (`component_removal.dart`, 346 lines): "Remove tool" on This phone; inventory by *presence* probes (not version checks); blocks removal of something other installed tools depend on; refreshes inventory + native setup status before each mutation; registry-authored scripts only; verifies absence afterwards; `sizeScript` gives "gives back N MB" (null when unmeasurable, never "0 B"); AI Team removal on Termux goes through its team manager. UI disables setup/start while busy.
- `test/setup_component_removal_test.dart` (31).

#### BUILTIN-LINUX-39 — Voice component hook in setup
- `VoiceSetupComponent` (`voice_component.dart`) as an app-side component: check/install/remove are Dart calls; size comes from the device-chosen pack; runs after the start so a failed download never stops OpenCode.

---------------------------------------------------------------------------
### 3.F Linux manager (This phone › Linux)

#### BUILTIN-LINUX-40 — Linux hub, Running and Ports
- Screens in `lib/ui/screens/linux_manager/`: `linux_manager_screen` (hub), `linux_processes_page` (poll 10 s; **to be merged into Running on this phone**), `linux_ports_page`, `linux_storage_page`, `linux_packages_page`, `linux_reset`, `linux_manager_common`.
- `LinuxManager` (`linux_manager/linux_manager.dart`): all reads go through `BuiltinLinux.run` (same installer slot as setup, so a busy slot is `LinuxProblem.busy`); a *passing* failure (`runtime_ownership_unknown` or "Another phone task") is retried **twice** with 700 ms pause before the page says anything (8e2c3c47c).
- **Processes** (`linux_processes.dart`, 475 lines): shell scan script `linuxProcessScanScript` reading `/proc` inside Ubuntu; kinds (OpenCode server, Paseo, dev servers…), protected rows never offered, `scan.withDescendants`, stop script by identity (pid+start ticks).
- **Ports** (`linux_ports.dart`, shares `lib/domain/listening_ports.dart`): `/proc/net/tcp` is denied to apps, so candidates come from numbers in running commands, `PORT` variables, log mentions and common dev ports, and **a port is listed only if a loopback connect succeeds** (script is POSIX sh: dash has no `${c:0:400}` or `/dev/tcp`; a live PC test reproduces the 2400 failure). Owner = the newest process whose command names the port. Previewable unless OpenCode (4097) or Paseo.
- **Tests:** `test/linux_manager_parse_test.dart` (47), `linux_manager_retry_test`, `listening_ports_test`, `development_services_test` (dev-services scan shares the detector).

#### BUILTIN-LINUX-41 — Storage breakdown and safe cleanups
- `linux_storage.dart`: six disjoint categories (base `/usr /etc /var/lib/dpkg`; packages `/var/cache/apt /var/lib/apt`; tools `/opt/node /root/.npm /root/.cache /var/cache/oc-setup /home/oc/.cache`; agents `/opt/{opencode,opencode2,oc2,oc-claude,aiteam} /root/{aiteam,.gc,.oc-opencode2} /home/oc/{.local,.oc-profiles}`; projects `/root/projects`; temp `/tmp /var/tmp /var/log`). Cleanups (`aptCache, toolCaches, tempFiles, oldAgentVersions`) may only touch `_allowedRoots` and never a path that is, or holds, one of `linuxProtectedPaths` (projects, `/root/.oc-builtin`, `.oc-opencode2`, `.claude`, `.codex`, `.config`, `.ssh`, `.gc`, `aiteam`, `.local`, `/home/oc/.oc-profiles` & friends, `/opt /etc /usr`). `linuxCleanupPathIsSafe` is tested against every action. "Freed bytes" = two `du` readings.

#### BUILTIN-LINUX-42 — Packages and the `linux-job`
- `linux_packages.dart` (237 lines): installed list (`dpkg-query` → `::pkg` lines; half-installed omitted), name validation (Debian rules; typed text never becomes shell), candidate check (`apt-cache policy`-style `::cand` lines), removal plan (what apt would also remove), kept packages (`linuxKeptPackages`: tools setup relies on — removed via Remove tool instead), long jobs `install|remove|update` as service **`linux-job`** (`startService`) guarded by `flock`, reporting `::oc job <kind>`, stage/percent via the prelude, ending `::oc job-exit <code>` (0 done, 124 time budget `linuxJobBudget`, 75 other task held the slot); `LinuxJobController` polls the log every 2 s and files the state; `requireQuiet()` refuses while setup or a job runs.

#### BUILTIN-LINUX-43 — Reset Linux (finishes or leaves Linux as it was)
- `LinuxResetFlow` (`linux_manager/linux_reset_flow.dart`): writes marker pref `oc.linuxReset.pending` (`erasing <ts>` / `installing <ts>`) **before** erasing; `beforeErase` leaves the in-app server; `uninstall` (projects kept); if the phone refused before erasing anything → "Nothing was erased", marker removed; if erase stopped part way, Linux is gone either way and setup replaces it; the setup engine forgets the old job (`restore()` reads no job, so a stale "ready" is impossible) and setup starts at once with the first-setup selection; `afterErase` forgets the old server's saved connections and reports failures specifically without stopping the reinstall; a reset killed mid-way is resumed on the next start (`resumeIfPending`, and shows progress). Reply-speed memory is forgotten. Reset is never red.
- UI: Reset row on `linux_manager_screen` / `this_phone_screen`; tests `test/linux_reset_flow_test.dart`, `linux_reset_copy_test.dart`.

#### BUILTIN-LINUX-44 — This phone hub and the phone server card
- `this_phone_screen.dart` (1,334 lines) + `this_phone/*` + `widgets/phone_server_card.dart` (1,419 lines): status line from `BuiltinLinuxStatus`, Start/Stop/Restart, "isn't answering" vs "stopped", Performance block (reply speed, speed mode, wake lock, services, orphans), server log fold, installed tools with Remove, Add tools, Remove OpenCode (keep vs delete projects with typed name), Reset, Termux migration entry, Open a terminal, Export projects (moving into Backups in the branch), plain-words failures (`this_phone_plain_failures_test`), installed check retries once after a reset.

---------------------------------------------------------------------------
### 3.G In-flight Linux manager features (not in this worktree; Flutter branches with tests)

Design: `docs/design/linux-manager-2026-10-31/{1-use-cases,2-architecture,3-ui}.md`. Branches (all Dart/bash, **no Kotlin change**, chained): `feat/linux-services-core-2026-10-31` → `feat/linux-vitals-2026-11-01` → `feat/linux-backup-2026-11-01` (worktrees under `/home/eslam/Storage2/wt/oc_app-{svccore,svcui,vitals,backup}`). QA records on the backup branch: `docs/qa/linux-{services-s1a-2026-10-31,vitals-s2a-2026-11-01,backup-s3-2026-11-01}/README.md`. Owner freeze: some tests written but not run.

#### BUILTIN-LINUX-45 — Linux services supervisor `oc-svd` (S1a)
- Keep a bot / dev server / catalog app running: crash restarts, wanted state on disk, logs, give-up. Max **16** definitions; names `[a-z][a-z0-9-]{1,23}`; reserved: `server svc linux-job phone-engine`, prefixes `agent- aiteam oc-`; reserved ports `4096 4097 6767 8372 8472` plus 1024–65535 rule and uniqueness.
- **One bash process** (`lib/builtin/linux_services/oc_svd_script.dart`, version `ocSvdVersion = 2`) run as the single native service **`svc`** (`exec /bin/bash /root/.oc-builtin/svc/oc-svd`). `set -m` (own process group per service), tick every 2 s via `read -t 2` on a private FIFO (no fork while idle), starts services 1 s apart, backoff 1 s doubling to 60 s (≥ 30 s run resets), **give up after 5 quick exits** (`phase=failed`, wanted stays on until the person taps Start = new kick), `/dev/tcp` port probe → `port_busy`, log rotation at 1 MiB per file (`.log` + `.log.1`, check every 15 ticks), SIGTERM = "Stop is Stop": every service wanted `on` is turned **off** and stays off (except an app-initiated supervisor restart, marked by file `keep`), SIGKILL (Android ending the app) leaves wanted state so services come back.
- Files (guest): `/root/.oc-builtin/svc/{oc-svd,defs/<n>.svc,want/<n>,kick/<n>,state/<n>,hold,keep}`, logs `/var/log/oc-svc/<n>.log(.1)`, `_svd.log`. Def format: `version=1 name= title= command= dir= port= restart=always|on-failure|never stop_timeout= catalog= env.KEY=` (plain key=value, never eval'd/sourced). State file: `phase pid since restarts last_exit last_exit_at next_try fails held`. Markers in logs: `::svc start|exit|backoff|gave-up|port-busy|stop`.
- Dart API `LinuxServices` (`read, watch(2 s), save, start, stop, restart, remove, logTail, ensureSupervisor`) in `linux_services.dart`; real implementation `rootfs_linux_services.dart` reads/writes the rootfs files directly from `<support>/linux/ubuntu/…` (links are never followed; every write temp+rename; `ensureSupervisor` serialised and installs/upgrades the script then `startService("svc", ocSvdLaunchScript())`), called on app open and when the phone server starts. Failure vocabulary `LinuxServiceProblem{linuxMissing, invalidName, nameTaken, invalidCommand, reservedPort, portTaken, tooMany, supervisorDown, writeFailed}`.
- Tests: `test/linux_services_script_test.dart` (runs the real script under bash on desktop: 13), `linux_services_test.dart` (29), `fake_linux_services.dart`.

#### BUILTIN-LINUX-46 — Services pages (S1b) and heat hold
- Pages `linux_services_page`, `linux_service_page` (live log via `KitLogPanel`, redacted), `linux_service_add_page` (command of your own: name, command, folder, port); words like "Crashed (exit 137). Trying again in 4 s". Heat: `linux_services_heat.dart` — at CRITICAL or worse a `hold` file stops every service (wanted choices untouched), given back after the phone has stayed cool 2 min (same `ThermalPolicy.veryHot/cool`); persisted as a file so it outlives the app.
- Tests: `test/linux_services_ui_test.dart`.

#### BUILTIN-LINUX-47 — Vitals strip (S2a)
- `lib/builtin/linux_vitals.dart`: heat word (Cool/Warm/Hot/Too hot from `oc/thermal`), phone memory free (`MemAvailable` from the app's own `/proc/meminfo`), Linux CPU % (all processes but the app's own, per-core % ÷ cores, from native `processes`), Linux memory (sum RSS), heaviest program (named when ≥ 50 % of a core). Sampled every **5 s only while the Linux page is open and on top**, one read at a time, skips a covered page. Missing values are left out, never shown as 0; no amber for heat (degraded ≠ needs-you). Battery level/temperature and low-memory flag need native (S2b, not built).
- `KitVitalsStrip` kit part; `test/linux_vitals_test.dart`, `kit_vitals_strip_test.dart`.

#### BUILTIN-LINUX-48 — Setup backup and restore (S3)
- One zip saved via Android "Save to" (`oc/project_export`): `manifest.json` (format 1 — a newer one is refused), `apt-manual.txt`, `npm-global.json`, `pipx.txt`, `home.tar.gz` (allow-list: `.bashrc .bash_profile .bash_aliases .profile .inputrc .gitconfig .vimrc .tmux.conf .config/{git,nvim,htop} .ssh/{authorized_keys,config,known_hosts}`), `services.tar.gz`, optional `projects.tar.gz` (without `node_modules .venv venv __pycache__ build dist .dart_tool .gradle target .next`; a `.git/config` with a password in its remote also excluded). **Never**: sign-ins, tokens, `.env*`, keys, `.ssh/id_*`, `.gnupg .aws .docker .claude .config/gh .config/opencode .local/share/opencode .oc-opencode2` (owner decision; tested). Built by a `linux-job` kind `backup` in POSIX sh; **tar must not open its own `.tar.gz` under proot ("Function not implemented")** — tar streams to gzip through the shell; "Save to" is asked only once parts exist (a failed backup used to leave an empty zip). Restore: pick zip, pure-Dart zip reader (`zip_reader.dart`), extracts only the 7 allowed names, installs missing packages by name (lists the ones Ubuntu no longer has), keeps replaced files as `.before-restore`, never overwrites a project (`name (restored)`), starts services that were on. Reset gets "Back up first". Remove OpenCode clears every `oc.linux.*` key and says backups stay (`forgetLinuxDevicePreferences`); pref `oc.linux.lastBackupAt`.
- "Projects only" = the former Export projects (BUILTIN-LINUX-59).
- Tests: `linux_backup_plan_test`, `linux_backup_test`, `linux_backups_page_test`, `phone_server_remove_keep_projects_test`.

#### BUILTIN-LINUX-49 — Designed, not built (spec only)
S2b native vitals (battery %, charging, temp via sticky `ACTION_BATTERY_CHANGED`, `ActivityManager.getMemoryInfo`, `StatFs`) through `oc/thermal vitals`; S4 package search (`apt-cache search --names-only`, debounce 400 ms, 8 s "still searching", 20 s timeout, 30 rows); S5 native restore of `svc` after boot/update/reclaim with the awake switch (default "on only while charging") and a 4 MiB native per-run log cap (`BuiltinLinux.kt:3536`); S6 catalog (SSH via Dropbear on port 8022 bound to the Tailscale 100.64.0.0/10 address only, key-only; Syncthing; Forgejo; code-server; honest fit badges by ABI/RAM/storage; a "why not Docker/Pi-hole" page); S7 full copy (stream the rootfs as `tar.gz` level 1 with commons-compress, ABI must match, restore by rename swap with a marker for interrupted restores, new `rootfsGeneration`, Repair afterwards) and backup-folder URI list (`oc.linux.backupFolder`); S8 merge Linux › Running into Running on this phone. Open owner questions Q1–Q6 are in `2-architecture.md` §9; the owner already decided Q3 (heat holds services) and Q4 (no private keys in backups).

---------------------------------------------------------------------------
### 3.H Terminal

#### BUILTIN-LINUX-50 — Local terminal sessions (PTY in the built-in Ubuntu)
- `K/LocalTerminal.kt` (390 lines): sessions are `bash -l` through `BuiltinLinux.prootCommand` on a real PTY opened with Termux's **terminal-emulator** library (`com.github.termux.termux-app:terminal-emulator:v0.118.3`, Apache 2.0, via JitPack, `PtyAccess`). Sessions belong to the manager not a screen (survive leaving the screen; last 512 KiB of output kept for a screen that starts over). Back-pressure: Dart acks each chunk; output waits up to 256 KiB then the PTY itself blocks (so `yes` is slowed and Ctrl-C works); chunks ≤ 32 KiB. Channels `…/local_terminal` (methods `start{rows,cols,signInProfile,signInProgram}`, `list`→`{sessions[],processes}`, `processCount`, `attach`, `write{data}`, `ack`, `resize`, `stop`, `remove`) and `…/local_terminal/events` (output, exit, `openUrl`).
- **Sign-in sessions** (`signInProfile` + `signInProgram` from `SIGN_IN_PROGRAMS = {claude, codex, gemini, qwen, goose, omp, fx, kilo, cline}`): runs the agent's own login prompt in the profile's private home (`BuiltinLinux.agentSignInCommand`): `HOME=/home/oc/.oc-profiles/<id>`, `CLAUDE_CONFIG_DIR=…/claude`, `CLAUDE_HOME`, `CODEX_HOME`, `DISABLE_AUTOUPDATER=1`, `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1`, and stand-in `xdg-open`/`open-url` scripts that write the page the agent wants into `<home>/.oc-open-url` (atomic mv); a watcher thread turns it into an `openUrl` event — the app never reads a code or key. Terminal work holds a `TERMINAL` lease and an FGS ("Terminal is working on this phone").
- **Dart:** `lib/builtin/local_terminal.dart` (855 lines: `LocalTerminalSessions`, per-session `xterm` `Terminal` that outlives the screen, sticky Ctrl/Alt key bar transform, "Shell 1/2…" labels), `lib/ui/screens/local_terminal_screen.dart`, `terminal_screen.dart`, `ui/screens/terminal/*`, `third_party/xterm` (path dependency fork). Tests `local_terminal_test` (18), `local_terminal_signin_foreground_test` (14), `terminal_key_bar_test`, `terminal_lifecycle_test`, `xterm_sgr_test`.
- **Licence note:** Termux `terminal-view` is Apache 2.0 per termux-app `LICENSE.md` (checked 2026-11-01); `termux-app` and `termux-shared` are GPLv3 and must not be used. Re-verify the exact module licences before depending on `terminal-view` in a native Compose rewrite; otherwise draw with Compose Canvas on top of `terminal-emulator`'s `TerminalEmulator`/`TerminalBuffer`.

---------------------------------------------------------------------------
### 3.I Thermal guard

#### BUILTIN-LINUX-51 — Thermal monitor, guard and notices
- **Native** `K/ThermalMonitor.kt` (channels `oc/thermal` method `current` → `{status: none|light|moderate|severe|critical|emergency|shutdown|unknown, headroom (-1 = none; NaN → none), sdk}`, `notify{title,text}`; `oc/thermal/events` stream): `PowerManager.currentThermalStatus` + `addThermalStatusListener` (API 29+, registered once for the process), `getThermalHeadroom(30)` (API 30+; NaN if asked more than ~1/s, so polled every **30 s** only while Dart listens). `notify` posts ONE notification on the existing channel `opencode_coding_status` (id 0x7E4A1) only when that channel exists and is not silenced.
- **Policy** (`ThermalPolicy`, pure): SEVERE or forecast headroom ≥ **0.95** → pause the AI Team; CRITICAL/EMERGENCY/SHUTDOWN → stop it (paused first so work is saved); resume only what the guard itself paused/stopped, after ≤ MODERATE with headroom < 0.95 for **2 minutes** (any hot reading restarts the wait); disabled = starts nothing new but still gives back.
- **Guard** (`ThermalGuard`, one run at a time; `oc.thermalGuard` default on, episode saved under `oc.thermalPause.<profileId>` so a pause survives the app dying): acts only when a team works on THIS phone; pauses via the Gas City supervisor API on loopback — built-in team `127.0.0.1:8472`, Termux team `8372`: `PATCH /v0/city/{c} {suspended:true}` then `POST …/session/{id}/suspend` for each running session; resume = `PATCH {suspended:false}` + `POST …/wake` for exactly those sessions; a city/session the person had suspended is never touched; at CRITICAL it also stops the built-in team's `aiteam` service and later `ensureRunning` (waits ≤ 3 min); the OpenCode server is never stopped for heat. One line in the shell (`KitNotice`) per episode, and one background notification at pause and one at resume; filed in "While you were away" (`AutomaticActKind.heatPause|heatStop|heatResume`). `ThermalHttp` seam for tests.
- **Tests:** `test/thermal_guard_test.dart` (29, failing-first record `docs/qa/thermal-guard-2026-09-26/fails-without-hysteresis.txt`).

---------------------------------------------------------------------------
### 3.J Phone agents hosted in the built-in Linux (Claude Code, Codex, ACP agents via Paseo)

#### BUILTIN-LINUX-52 — Private agent processes and per-profile agent homes
- Agents run as the fixed non-root view `--change-id=1000:1000`, HOME `/home/oc/.oc-profiles/<profileId>` (regex `^[A-Za-z0-9_-]{1,80}$`; dirs mode 0700; `codex/` must exist or Codex refuses to run — issue #95), `/root` replaced by `files/linux/agent-root-view` so the agent can't see root's files, cwd `/root/projects`, PATH `/home/oc/.local/bin:…`. `startAgentProcess(profile, argv, foreground, privateOutput)` registers the process in `processes`, `agentProcessProfiles`, `authOtherOwners`, optional work admission; `blockAgentProfile`, `deleteAgentHome` (lstat-based recursive delete, refuses a running host/auth service, clears the Terminal's `~/.claude` link and the profile's diagnostics keys), `writeAgentConfig` (Paseo `config.json` version 1, ≤ 64 KiB, temp+rename, mode 0600), `agentPaseoHome`.
- **One Claude sign-in for app and Terminal:** `PhoneAgentClaudeHome` makes `/home/oc/.claude` a link to the profile's Claude folder and adopts a sign-in made in the Terminal before. `CLAUDE_HOME` is set because Paseo's usage reader looks there.
- **Tests:** `PhoneAgentClaudeHomeTest`, `NativeAgentHostAdmissionTest`, `phone_agent_paths_native_test`, `phone_agent_owner_test`.

#### BUILTIN-LINUX-53 — Agent host (Paseo daemon) lifecycle
- `K/PhoneAgentHost.kt` + `PhoneAgentChecks.kt` + `PhoneAgentPaths.kt`: `start(StartRequest{profile, password (64 hex), port 4099, config, HelperPolicy{expectedVersion, restartIfVersionChanged, forceRestart}})`, `status`, `stop(guardedForUpdate)`, `delete`, `prepareUpdate`, `checks.workspace`, `checks.version(executable, version)`; service name `agent-host.<profile>`; 1.5 s startup grace; stale Paseo lock (`paseo.pid`) cleared only when no helper of ours runs and nothing answers on the port (`PaseoStaleLock`); Claude OAuth refresh lock (`.oauth_refresh.lock`) cleared when none of that profile's processes run (`clearStaleAgentLoginLock`); failure codes through channel: `agent_unavailable`, `idle_resume_stale`, `agent_host_unowned`, `agent_host_busy`, …. Ticketed admission (`AgentHostTicket`, `captureAgentHostStart`, `withAgentHostStart`) ties a start to the current wanted revision/activity epoch/idle generation so a stale UI cannot start a helper.
- Channel: `startAgentHost, agentHostStatus, stopAgentHost, deleteAgentHost, agentHostPrepareUpdate, agentHostVersion, agentHostWorkspace`.
- Dart orchestration: `lib/builtin/agents/phone_agents_host.dart` (`BuiltinPhoneAgents`, 1,374 lines: status checks serialised per profile — a second concurrent check was refused as "host unavailable" and read as "sign in needed"; install/remove through the setup engine; Paseo websocket via `lib/paseo`; gates in prefs `oc.agentPhoneGate.<profile>`, `oc.agentInstall.<profile>`, secret `oc.agentHostSecret.<profile>` in secure storage), `agent_components.dart`, `agent_scripts.dart`, `paseo_scripts.dart`, `agent_install_guard.dart`, `agent_removal_scripts.dart`, `agent_host_cleanup.dart`, `gen_ui_*` (generative-UI MCP search/publish/server/install; see gen-UI map).

#### BUILTIN-LINUX-54 — Agent sign-in (private pipes) and auth probe
- `PhoneAgentSignIn` (442 lines): Claude subscription sign-in over private pipes (never a terminal/log): `start, challenge, submit(code), cancel, status` keyed by `(profileId, agentId, runId, method)` with tombstones so a cancelled run id can't be reused; phases `signedOut …`; failures `auth_unavailable|auth_cancelled|auth_stale_run|auth_invalid_code|auth_rejected`; verifies `claude --version` against `TESTED_CLAUDE_VERSIONS = {"2.1.283 (Claude Code)", "2.1.294 (Claude Code)"}` (kept equal to `claudeCodeTestedVersions` in `lib/paseo/tested_versions.dart` by a test). Terminal sign-in for other agents is BUILTIN-LINUX-50.
- `agentAuthProbe` (`PhoneAgentAuthRuntime`, `PhoneAgentAuth*.kt`, 10 files): private, output-filtered probe of "is this agent signed in" (`PhoneAgentCheckOutput` allow-list, `PhoneAgentAuthLock`, `…ProcessTree`, `…OtherOwners` so cleanup never kills another owner's helper). Dart: `agent_sign_in.dart` (`ChannelAgentSignInHost`), `domain/agent_auth_probe.dart`.

---------------------------------------------------------------------------
### 3.K AI Team on the phone (seam only)

#### BUILTIN-LINUX-55 — Built-in AI Team supervisor (Gas City) as a native service
- `lib/builtin/team/builtin_team.dart` (1,462 lines), `builtin_team_job.dart`, `builtin_team_start_progress.dart`, `setup/aiteam_scripts.dart`: `BuiltinTeam.turnOn` (store once, add project with a phone-side git `origin` under `/root/aiteam/origins/<rig>.git`, start supervisor, wait until it answers), `start/stop/ensureRunning` as service **`aiteam`** on `127.0.0.1:8472` (loopback only, explicit, no other Host header, no password yet), `config` = AI Team plugin config for the in-app profile, `turnOff/remove`. Phone-tuned city config (patrol 60 s, probe_concurrency 1, nudge dispatcher; skipped dog/maintenance) to stay ≤ 32 processes. Traps (memory `aiteam-builtin-component`): `gc init` needs `lsof` + `opencode` on PATH; `git push` to a local bare repo under proot needs `core.createObject rename`; `gc register` can block minutes (cut at 60 s); dolt `--version` invalid (use `dolt version`); `bd init` under slow proot can leave "dirty tables: child_counters" → retry from a clean prefix.
- Tests: `builtin_team_test` (14), `builtin_team_removal_test`, `builtin_team_startup_test`, `aiteam_component_test`, `phone_team_setup_test`.

#### BUILTIN-LINUX-56 — Phone engine protection tiers (native Rust daemon seam)
- `BuiltinLinux.startPhoneEngine/stopPhoneEngine/phoneEngineStatus/phoneEngineCredentials/deletePhoneEngine/startProtectedPhoneServer/runPhoneEngineBoundaryProbe` + `PhoneEngineNative.kt` (593 lines), `PhoneEngineAttestation.kt` (Keystore-signed receipt), `NativeEnginePeer`/`NativeEngineReadiness`, jniLibs `libaiteam_engine.so`, `libaiteam_sandbox.so`, `libaiteam_boundary_probe.so`. Two tiers decided at start: **landlock** (`libaiteam_sandbox.so --check-kernel`, 3 s, exit 78 = unsupported) or **proot-tier** (private store unbound, clean env, `/proc/<pid>` masked). On success a marker `files/oc.teamEngine.protection-required` is written (`required-v1` landlock / `required-proot-v1`) and `protectedProot=true`: **every later launch** (scripts, services, PTY) is wrapped (`protectedCommand`) or confined (shared-storage binds shrink to opened project folders). `restartRequired`/`unconfinedChildren` are reported when anything unconfined runs. Landlock is **not** in Android GKI kernels (measured 2026-10-07, `CONFIG_SECURITY_LANDLOCK` unset) so the proot tier is what ships.
- Error codes through the channel: `engine_unavailable`, `restart_required`, `invalid_port`, `boundary_unverified`, `boundary_proof_failed`, `boundary_signer_unavailable`, `foreground_unavailable`, …
- Treat as the AI Team map's; for this area it matters because it changes how `prootCommand` builds every command.

---------------------------------------------------------------------------
### 3.L Tools that run in or on the built-in Linux

#### BUILTIN-LINUX-57 — Project folders and the rootfs reader
- `BuiltinRootfsFolders` (`builtin_folders.dart`): lists folders **straight from files on disk** (`<support>/linux/ubuntu`, projects at `<support>/projects`), no Ubuntu process and no OpenCode, works while the server is stopped; never follows links (absolute targets mean paths inside Ubuntu); `/dev /proc /sys` left out of `/`; typed errors (not installed, gone, denied, link, not plain path, timeout, other). `BuiltinProjectFolders` (`builtin_server.dart`): list/exists/create (`git init` + empty "First commit" so a team has something to branch from)/first commit/is-empty-repo via scripts — OpenCode is never asked about a folder before it exists (it caches a failed instance; `POST /instance/dispose` clears it).

#### BUILTIN-LINUX-58 — Git on the phone: shell, secrets, link repair
- `git_shell.dart` runs the Git page's commands in Ubuntu; GitHub tokens are placed as 0600 random-named files under `/root/.oc-git`, written from the app side of the rootfs (never through a script), overwritten and deleted after the command. `git_link_repair.dart` (759 lines): finds object links broken by a moved folder (proot `.l2s.` files; also Termux `.l2s` store), follows hop by hop, repairs. Native side: `BuiltinGitConfig` (BUILTIN-LINUX-03). Tests `git_link_repair_test`, `builtin_git_secret_drop_test`.

#### BUILTIN-LINUX-59 — Export projects / manage space
- `ProjectExportBridge` (`oc/project_export`: `pickDestination{name}`, `export` (streams a plan file `name\tsource` per line from cache into a zip at the "Save to" URI; sources must be regular files inside filesDir checked with lstat; partial zip deleted on cancel/dispose), `cancel`, `cacheBytes`, `clearCache` (never the running server's `proot-tmp`), `clearAllData` (`ActivityManager.clearApplicationUserData`)). Dart `project_export.dart` (policy: `ProjectExportPolicy.privateNames/privateExtensions`, `.env` templates allowed, remote URLs with passwords are private), `project_export_controller.dart`. Private export adds OpenCode's sign-ins/settings/conversations.

#### BUILTIN-LINUX-60 — Quota collector host (Usage for Codex/GLM/MiniMax)
- `quota_collector_host.dart` (522 lines): optional Node program `tool/quota/collector.mjs` run as service **`quota`** inside the built-in Ubuntu; read token generated with `Random.secure`, kept in secure storage `oc.quotaToken.<profileId>`, staged as a 0600 file; provider keys never reach Dart (a helper in Ubuntu reads OpenCode's sign-in file and writes 0600 key files; Dart sees counts like `glm=1`); pref `oc.quotaCollector.<profileId>`; restarts after the server starts if set up.

#### BUILTIN-LINUX-61 — FactsBook node host
- `factsbook_node_host.dart` (424 lines): installs a checksum-verified `factsbook-node` build from a documented location, creates the vault, runs it as service **`factsbook`**, pairs (the one-time code and owner device id are never logged or shown); typed failure vocabulary.

#### BUILTIN-LINUX-62 — OpenCode 1→2 history copy
- `lib/domain/opencode_history_copy.dart` scripts (`openCode1HistoryCopyScript`, switch-check script) run inside the server script / before a runtime switch; exit 74 means "finish moving conversations in the app first". Profile fields `requiresOpenCode2Migration`, `openCode2HistoryPending`.

---------------------------------------------------------------------------
### 3.M Migration and the Termux host

#### BUILTIN-LINUX-63 — Move from Termux into the built-in Linux
- `lib/builtin/migration/*` (7 files): inventory → review selection → authenticated archive transfer over an IPv4 loopback socket with a bearer (bounded USTAR importer, per-item bounds, three simultaneous retained copies in the disk budget, journalled, resumable, discard) → verified import into `/root/projects` → switch the profile (session ids not portable; pins/drafts/queued prompts stay with the old profile). Provider **sign-ins never move**; offline provider labels from a config export only. State key `oc.termuxMigration.<id>`. Tests `termux_migration_*_test` (archive 8, controller 17, profile 8, space 7, transport 14, ui 6).

#### BUILTIN-LINUX-64 — Termux as an alternative host (legacy)
- `lib/termux/*` (9,442 lines: manager script 1,273, local-agents script 1,023, team scripts 1,187, tools script 937, managed-server recovery 896, bridge 624…), `K/Termux{ActivityBridge,ActivityCommands,ActivityEnvironment,ActivityPermissions,ActivitySetup,ResultService,SetupRunner,SetupShell}.kt`, channel `oc/termux`, `com.termux.permission.RUN_COMMAND`. Runs the same OpenCode/AI Team inside Termux's proot-distro Ubuntu; `SetupHostKind.termux` reuses the engine; port 4096 (built-in uses 4097 so both can coexist). **Owner decision pending (Q1)**: keep, keep only the migration-out, or drop.

---------------------------------------------------------------------------
## 4. Dart classes to re-implement in Kotlin (index)

`lib/builtin/`: `builtin_linux.dart` (channel facade + script builders + status models), `builtin_server.dart`, `builtin_server_recovery.dart`, `builtin_folders.dart`, `deliberate_stop.dart`, `app_exit_recovery.dart`, `phone_server_healing.dart`, `phone_server_idle.dart`, `phone_agent_work_watch.dart`, `phone_processes.dart`, `phone_process_watch.dart`, `reply_watch.dart`, `thermal_guard.dart`, `thermal_guard_teams.dart`, `local_terminal.dart`, `git_shell.dart`, `git_link_repair.dart`, `project_export*.dart`, `quota_collector_host.dart`, `factsbook_node_host.dart`, `agent_helper_diagnostics.dart`, `abi_*` / `int64_max_*` (platform shims only), `linux_manager/*`, `setup/*`, `agents/*`, `team/*`, `migration/*`. Also `lib/state/{builtin_server_owner,lifecycle_report,automation_policy,phone_project_engine,phone_team_setup}.dart` and `lib/platform/thermal.dart`.

Dart-only dependencies used here: `shared_preferences`, `flutter_secure_storage 11.2.0`, `path_provider`, `crypto`, `clock`, `xterm` (path fork), `file_picker 12.2.0` (restore picker), `flutter_riverpod`. Native replacements: `SharedPreferences`/DataStore, Keystore + EncryptedSharedPreferences/Tink, `MessageDigest`, kotlinx-datetime/clock, own terminal renderer, SAF pickers.

---------------------------------------------------------------------------
## 5. Data stored (exact keys, paths, formats)

### 5.1 Files (all under `context.filesDir`, a.k.a. Flutter's `getApplicationSupportDirectory()`; **do not move**)
| Path | Content / format | Owner |
|---|---|---|
| `linux/ubuntu/` | the Ubuntu root filesystem (symlinks, `.l2s.*` link files, `/root/.oc-builtin/server.password` 0600, `/root/.oc-opencode2/**`, `/opt/{node,opencode2,oc-claude,aiteam}`, `/home/oc/.oc-profiles/<profile>/{claude,codex,paseo,.oc-bin}`) | Kotlin |
| `linux/ubuntu.ready` | text: image SHA-256; presence = installed | Kotlin |
| `linux/setup.json`, `linux/setup.log` | setup job state (JSON, atomic), setup log | `SetupRunner` |
| `linux/server.log`, `linux/<service>.log`, `linux/<service>.previous.log` | service logs (append; previous kept) | `BuiltinLinux` |
| `linux/proc/{stat,loadavg,uptime,version,vmstat}`, `linux/proc/phone-engine-hidden/` | fake /proc stand-ins, empty mask dir | `BuiltinLinux` |
| `linux/agent-root-view/` (+`projects`) | agent's replacement /root | `PhoneAgentPaths` |
| `projects/` | all user projects (bound at /root/projects) | `BuiltinProjectStorage` |
| `oc.sharedProjects` | newline list of canonical shared-storage roots | `BuiltinLinux` |
| `oc.teamEngine.protection-required` | `required-v1` or `required-proot-v1` (presence even when corrupt = protection required) | `BuiltinLinux` |
| `oc.teamEngine.<profile>/` | phone engine private store | `PhoneEngineNative` |
| `cache/ubuntu-base.tar.gz` | partial/whole Ubuntu download | `BuiltinLinux` |
| `cache/proot-tmp/` | `PROOT_TMP_DIR` (never cleared while a proot runs) | `BuiltinLinux` |
| crash store (`NativeCrashStore`) | last native crash record (read by `launchReport`) | platform map |
| (branch) `linux/ubuntu/root/.oc-builtin/svc/**`, `var/log/oc-svc/**`, `…/backup/stage`, `…/restore` | services + backup staging inside the rootfs | Dart (patch) |

### 5.2 Kotlin-owned `SharedPreferences` (XML files in `shared_prefs/`; keep names and keys)
- **`builtin_server_recovery`**: `runtimeGeneration` (long), `wanted` (bool), `userStopped` (bool, default `!wanted`), `owner` (profile bound to supervision), `enabled` (bool), `restoreOwner`, `restoreReason`, `idleState` (JSON `{version:1, enabled, idleMinutes, counter, generation, stopped, helperStopped, owner, helper}`), `backoffNext`, `backoffAt`, `backoffBoot`, `drainOwner`, `drainAll`, per profile: `oc.builtinRuntimeRecipe.<p>` (`{version:1, profileId, runtime:"openCode2", packageVersion, rootfsGeneration}`), `oc.builtinRuntimeOwnership.<p>` (receipt JSON), `oc.builtinRuntimeDrain.<p>`, `oc.builtinRecoveryBudget.<p>`, `oc.builtinRecoveryReceipts.<p>` (JSON array ≤16). All crash-sensitive writes use `commit()`, not `apply()`.
- **`builtin_service_diagnostics`**: `names` (string set), `<name>.exit|uptime|exitAt|stopRequested|restarts|launched`.
- **`builtin_component_writer`**: `rootfsGeneration` (64 hex), `ticket` (installer ticket JSON), `generation` (long).
- **`oc_lifecycle`**: `running_services` (csv), `seen_exit_timestamp`.
- Also Kotlin-owned elsewhere: `camera_permissions`, `voice_permissions` (not this area).

### 5.3 Flutter-owned preferences that Kotlin reads or that this area writes (`FlutterSharedPreferences.xml`, keys prefixed `flutter.`)
- **Read by Kotlin as authority** (the migration must preserve or re-create them): `flutter.oc.builtinRecovery.<profile>` — JSON, must have `version == 2` and `nativeAuthority == true` or `migrationMarkerValid` fails and restore is disarmed; `flutter.oc.automation.<profile>` — JSON `AutomationPolicy` (see BUILTIN-LINUX-21); `flutter.oc.phoneAgentOwner.<profile>` — which profile owns the agent helper (default = the profile itself); scheduled-prompt phone lease keys (scheduling map).
- **Written by this area's Dart**: `oc.phoneServerStopped.<p>` (bool), `oc.builtinServerOwner` (pointer), `oc.linuxReset.pending` (`erasing|installing <iso>`), `oc.thermalGuard` (bool, default true), `oc.thermalPause.<p>` (JSON `{url, city, builtin, since, sessions[], serviceStopped, status}`), `oc.replySpeed.inApp` (JSON timing), `oc.quotaCollector.<p>`, `oc.termuxMigration.<id>`, `oc.sharedProjects.<p>` (per-profile shared-storage project roots, `lib/state/shared_project_roots.dart`; the union of all profiles is pushed to Kotlin's `files/oc.sharedProjects`), `oc.agentPhoneGate.<p>`, `oc.agentInstall.<p>`, `oc.phoneAgentsUsed.<p>`, `oc.phoneAgentGateMigration.<p>`, `oc.linux.lastBackupAt` and other `oc.linux.*` (branch). **Naming rule:** per-profile keys are `oc.<what>.<profileId>` so `ProfileStore.profileScopedPreferenceKeys` sweeps them on profile deletion; device-wide ones (`oc.linuxReset.pending`, `oc.replySpeed.inApp`, `oc.linux.*`, `oc.builtinServerOwner`) are intentionally not per profile.
- Profiles (`oc.profiles`, `oc.activeProfile`) are described by the state map.

### 5.4 Secure storage (flutter_secure_storage v11 → Android Keystore-backed)
- `pw.<profileId>` server password (rewritten into the guest file at every start); `oc.agentHostSecret.<profileId>` Paseo host secret (64 hex); `oc.quotaToken.<profileId>`; `oc.codexToken.<profileId>`. The migration must read these with flutter_secure_storage's exact scheme (see the state/packages maps) — a lost secret is *recoverable* for the in-app server (new password at next start) but **not** for agent host secrets that Paseo already persisted in its config.

### 5.5 Guest-side files that are part of the contract
`/root/.oc-builtin/server.password` (0600), `/root/.oc-opencode2/{data/opencode/opencode.db,cache,state,config/opencode}`, `/root/.local/share/opencode/opencode.db` (OC1 history), `/etc/gitconfig` (createObject), `/etc/group`, `/etc/resolv.conf`, `/etc/hosts`, `/etc/apt/apt.conf.d/01-oc-sandbox`, `/var/cache/oc-setup/*` (resumable download cache), `*.oc-pending`/`*.oc-good` update journals, `/root/.oc-git/*`, `/home/oc/.oc-profiles/<p>/.oc-open-url`.

---------------------------------------------------------------------------
## 6. Native bridges

### 6.1 Channels
| Channel (name) | Direction | Kotlin class | Dart class |
|---|---|---|---|
| `io.github.eslamasabry.opencode_mobile/builtin_linux` (MethodChannel) | Dart→Kotlin | `MainActivity.handleBuiltinLinux` → `BuiltinLinux` | `BuiltinLinux` (`lib/builtin/builtin_linux.dart`), `ChannelAgentHostCleanup`, `ChannelAgentSignInHost`, `BuiltinPhoneAgents` |
| `…/local_terminal` (Method) + `…/local_terminal/events` (Event) | both | `LocalTerminal` | `LocalTerminalSessions` |
| `oc/thermal` (Method) + `oc/thermal/events` (Event) | both | `ThermalMonitor` | `lib/platform/thermal.dart` |
| `oc/lifecycle` (Method) | Dart→Kotlin | `AppLifecycle` | `lib/platform/app_exit.dart` |
| `oc/project_export` (Method) | Dart→Kotlin | `ProjectExportBridge` | `project_export*.dart` |
| `oc/storage` (Method) | Dart→Kotlin | `StorageAccess` | `lib/platform/storage_access.dart` |
| `oc/voice` `getDeviceInfo` | Dart→Kotlin | `MainActivityMedia` | `lib/voice/device.dart` (preflight ABI/RAM/storage) |
| `oc/termux` (Method) | Dart→Kotlin | `TermuxActivityBridge` | `lib/termux/bridge.dart` (legacy) |
| `oc/background` (Method) | both | `MainActivityBackgroundChannel` (battery exemption etc.) | `lib/background/*` |

**Every `builtin_linux` call runs on a worker thread and answers on the main thread** (`inBackground`). The call set (arguments → result) and the error mapping are below. In a Kotlin-only app these become suspend functions on one `BuiltinLinuxService` interface; keep the *error vocabulary*, not the channel.

### 6.2 `builtin_linux` methods
- Install/state: `status` → status map (see 13); `installUbuntu`; `uninstall` / `removeRuntime{alsoDeleteProjects, confirmationName}`; `projectStorage`; `setSharedProjects{roots}`; `openStorageSettings` → bool; `performance`.
- Run: `run{script, timeoutSeconds=600, agentUser}` → `{exitCode, output}`.
- Server: `startServer{script, port, restoreRecipe{version,profileId,runtime}}`, `restartServer{script, port, expectedGeneration}`, `stopServer{phoneEngineSetup}`, `serverLog{tailBytes=16384}`; services `startService{name, script, port?, notice?}`, `stopService{name}`, `serviceLog{name, tailBytes}`.
- Recovery: `stageServerRecovery{profileId, legacyBudget}`, `bindServerRecovery{profileId, legacyBudget?, enabled}`, `serverRecoveryBudget`, `serverRecoveryReceipts`, `ackServerRecoveryReceipt{profileId,eventId}`, `updateServerRecoveryReceipt{profileId,budget}`, `confirmManualServerStart`, `confirmServerPresence`, `unbindServerRecovery`, `deleteServerRecovery`, `cancelServerRecovery`, `confirmServerRecovery{expectedGeneration}`.
- Idle: `setPhoneServerIdlePolicy{enabled,idleMinutes}`, `observePhoneAgentWork{profileId,busy}`, `resumeIdleStoppedPhoneServer{profileId,expectedIdleGeneration}`, `completePhoneServerIdleResume{…}`.
- Leases: `holdAwakeForWork{on,forMs}`, `setChatWorkLease{leaseId,on,forMs}`, `setPhoneAgentChatWorkLease{profileId,leaseId,on,forMs}`.
- Processes: `processes`, `stopProcesses{pids}`.
- Setup: `startSetup{jobId, components[], params, texts}`, `setupStatus`, `cancelSetup`, `completeSetupStep{jobId,id,ok,error,version}`.
- Agents: `startAgentHost{profileId,password,port(4099),config,expectedHelperVersion,restartIfVersionChanged,forceRestart,idleResume,expectedIdleGeneration}`, `agentHostStatus`, `stopAgentHost{guardedForUpdate}`, `deleteAgentHost`, `agentHostPrepareUpdate`, `agentHostVersion{executable,version}`, `agentHostWorkspace`, `startAgentSignIn / readAgentSignInChallenge / submitAgentSignInCode{code} / cancelAgentSignIn / agentSignInStatus {profileId, agentId, runId, method}`, `agentAuthProbe{…}`.
- Phone engine: `startPhoneEngine{profileId,port=4098,notice}`, `phoneEngineStatus`, `phoneEngineCredentials`, `stopPhoneEngine`, `deletePhoneEngine`, `startProtectedPhoneServer{profileId,script,port}`, `runPhoneEngineBoundaryProbe`.

### 6.3 Error codes the UI depends on
`builtin_linux` (generic; `details` = `serverStartDetail` only for `startServer|run|status|restartServer`), `runtime_ownership_unknown` ("Open app settings, force stop OpenCode, then open it again."), `recovery_unavailable`, `idle_resume_unavailable`, `idle_resume_stale`, `agent_unavailable`, `agent_host_unowned`, `agent_host_busy`, `engine_unavailable` + engine codes, `setup_persistence`, `invalid_profile`, `unsupported_platform`/`missing_plugin` (Dart-side), `confirmation_required`, `storage_unavailable`, `removal_failed`, `work_lease_invalid`, `server_start_unavailable`. The UI must never print the native message as copy ("no raw errors" rule): plain words + a way forward, technical text only under Details.

### 6.4 Manifest pieces
Services: `BuiltinServerService` (specialUse, property text), `SetupService` (specialUse), `BackgroundConnectionService` (dataSync|shortService; other map), `TermuxResultService`, `LocalPdfService` (isolated, `:local_pdf`; its pid is the one allowed foreign same-uid process). Receivers: `BuiltinRestoreReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED), `BuiltinIdleReceiver` (alarm action `…IDLE_CHECK`). Permissions: `INTERNET`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `MANAGE_EXTERNAL_STORAGE` (+ legacy read/write), `SCHEDULE_EXACT_ALARM`, `com.termux.permission.RUN_COMMAND` (Termux host). `compileSdk 37`, `minSdk max(flutter, 26)`.

---------------------------------------------------------------------------
## 7. Kotlin: what carries over as-is

Lines in parentheses. "As-is" = move the file, drop channel glue, keep tests.

| Group | Files | Verdict |
|---|---|---|
| Core runtime | `BuiltinLinux.kt` (4,511), `SharedStorageBinds` (in it) | **Keep logic; split into modules** (RuntimeInstall, ProotLauncher, ServiceRegistry, InstallerSlot, ServerRecovery, IdleController, AgentHostBridge, PhoneEngineBridge). Remove `flutterPreferences` reads (replace with Kotlin-owned equivalents + migration). `BuiltinLinux.get(context)` singleton stays process-wide. |
| Pure policy (JVM tested) | `RestartBackoff`, `NativeRecoveryBudget` (+`PresenceReset`), `NativeServerRecipe`, `NativeServerRestoreEvent`/`NativeServerEventPolicy`, `NativeRuntimeOwnership` (+`RuntimeProcessIdentity`, `NativeRuntimeReceipt`, `ColdWorker`), `NativeInstallerOwnership/Admission/Visibility`, `NativeProcRace`, `NativeProcessTree`, `NativeProcScan`, `NativeOrphanReaper`, `NativeProcessList`, `WorkLeases`, `IdleStopPolicy`, `NativeIdleState/Heartbeat/Timer/ReturnNotification`, `ServiceDiagnostics`, `SetupStepRetry`, `SetupScriptCompletion`, `SetupWorkScopes`, `NativeAgentHostAdmission`, `PaseoStaleLock`, `AndroidGroupNames`, `BuiltinGitConfig`, `BuiltinProjectStorage`, `NativeComponentUpdateRecovery` | **As-is.** These are the safety rules. Good `commonMain`/`jvmMain` candidates for the pure ones (no Android imports): budget, backoff, policies, process-tree/orphan/list logic over `/proc`-shaped data, lease registry, setup protocol parser. |
| Android hosts | `BuiltinServerService`, `SetupService`, `BuiltinRestoreReceiver`, `NativeIdleTimer` (AlarmManager part), `NativeIdleReturnNotificationHost`, `NativeWorkLeaseHost` (wake lock part), `NativeOrphanReaperHost`, `StatusNotification*`, `AppLifecycle` (non-channel parts), `ThermalMonitor` (listener part), `LocalTerminal` (session/PTY part), `ProjectExportBridge` (zip streaming), `StorageAccess` | **As-is minus channel glue**; replace `MethodChannel` with direct Kotlin interfaces/Flows. |
| Setup | `SetupRunner`, `SetupJob`, `SetupPersistence`, `SetupDiskSpace` | **As-is**; the job spec stops crossing a channel (typed Kotlin objects), `files/linux/setup.json` format unchanged for in-place resume. |
| Agent hosting | `PhoneAgentHost`, `PhoneAgentSignIn`, `PhoneAgentChecks`, `PhoneAgentPaths`, `PhoneAgentClaudeHome`, `PhoneAgentAuth*` (10), `PhoneAgentInventory` | **As-is** (agent map decides policy). |
| AI Team engine | `PhoneEngineNative`, `PhoneEngineAttestation`, `NativeEngineReadiness` (+`NativeEnginePeer`) | **As-is** if the engine stays; otherwise delete with the engine. |
| Termux host | `Termux*.kt` (8) | **Delete or keep per Q1.** |
| Channel glue to delete | `MainActivity.handleBuiltinLinux`, `MainActivityBuiltinSupport.setupSpec` (becomes a typed constructor), `NativeChannelReplies`, `serverStartDetail` (kept as a diagnostics helper) | Replace. |
| Android instrumentation tests | `androidTest/*Builtin*Acceptance`, `PhoneEngineAcceptance`, `Bb8/Bd9 DeviceSmoke`, `BuiltinInstallerProducerQaService` (633 lines; a QA-only service that lives in `src/main` but is not part of the shipped manifest — move it to a QA source set) | Keep: they prove runtime ownership on a real kernel; only `BuildConfig.BUILTIN_RUNTIME_QA` hooks (`gateCheckpointForQa`, Gradle property `-PocBuiltinRuntimeQa=true`, mutually exclusive with the Bb8/Bd9 smoke flags) are test seams. |

JVM unit tests in `android/app/src/test/kotlin/…` that belong here (port first, they are native already): `BuiltinGitObjectsTest, IdleStopPolicyTest, NativeAgentHostAdmissionTest, NativeComponentUpdateRecoveryTest, NativeEngineReadinessTest, NativeIdleHeartbeatTest, NativeIdleReceiverDispatchTest, NativeIdleReturnNotificationTest, NativeIdleStateTest, NativeIdleTimerTest, NativeInstallerAdmissionTest, NativeInstallerOwnershipTest, NativeInstallerVisibilityTest, NativeOrphanReaperTest, NativeProcessListTest, NativeProcessTreeTest, NativeProcRaceTest, NativeRecoveryBudgetTest, NativeRuntimeOwnershipTest, NativeServerRecipeTest, NativeServerRestoreEventTest, NativeWorkLeaseHostTest, PaseoStaleLockTest, PhoneAgentClaudeHomeTest, RestartBackoffTest, ServiceDiagnosticsTest, SetupScriptCompletionTest, SetupStepRetryTest, SetupWorkScopesTest, WorkLeasesTest` (Robolectric 4.17, JUnit 4.13.2, `org.json` test dep).

---------------------------------------------------------------------------
## 8. Packages, binaries and pins

- **Kotlin/Java:** `org.apache.commons:commons-compress:1.27.1` (tar/gzip unpack), `com.github.termux.termux-app:terminal-emulator:v0.118.3` (JitPack, `exclusiveContent` repo, Apache 2.0; PTY), `androidx.work:work-runtime:2.10.5` (scheduled prompts, not here), `androidx.core:core:1.13.1`.
- **Native libs in the APK:** `libproot.so` (Termux proot 5.1.107.94), `libproot-loader.so`, `libtalloc.so` (2.4.3), `libandroid-shmem.so` (0.7), plus AI Team `libaiteam_{engine,sandbox,boundary_probe}.so`. Sizes: proot 265 KB, engine 5.4 MB.
- **Downloaded into Ubuntu (all SHA-256 pinned in Dart):** Ubuntu Base 24.04.5; Node v24.21.0; OpenCode 2.1.1 (`@opencode/cli-linux-arm64`, `-x64-baseline`); Paseo 0.11.2 (npm lockfile asset); Claude Code 2.1.283/2.1.294 (tested set); Gas City 1.4.1, Beads 1.2.2, Dolt 2.3.5; per-agent recipes in `domain/agent_catalog.dart`. Pins file: `lib/termux/scripts/local_agents_script.dart` (`termuxLocalAgentsPins`), `lib/builtin/setup/components.dart` (`OpenCodePins`), `aiteam_scripts.dart` (`AiTeamPins`).
- **Ports:** OpenCode 4097 (loopback), phone engine default 4098, Paseo/agent host 4099 (loopback; reserved 6767 for Paseo's default), AI Team supervisor 8472 (Termux team 8372), Termux OpenCode 4096. Services page reserves `{4096, 4097, 6767, 8372, 8472}`.
- **Build tooling:** `tool/builtin_linux/fetch_proot.sh` (needs `patchelf`); Shorebird ends at the switch — any pin change now needs a new APK.

---------------------------------------------------------------------------
## 9. Hard-won fixes a rewrite must not lose (with commits and why)

| # | Behaviour to keep | Commit / evidence | What broke before |
|---|---|---|---|
| H1 | EPERM from `kill(pid,0)` = recorded pid is gone | `77657a437` (2026-10-13) | After a force stop, other apps reused the installer's pids; cold recovery read EPERM as "maybe running" and refused every guest command until reboot. |
| H2 | ESRCH/ENOENT on `/proc` read = process gone; 5-strike monitor; 4-read plan settle | `e958cf780` (10-16) | apt/dpkg start hundreds of short processes/s; one exiting mid-read stopped the install ("Setup stopped before it finished"). |
| H3 | Follow `TracerPid` of orphans when walking a tree | `ad2ce4d79` (10-16) | `(python3 -m http.server &)` left an orphan with PPid 1; admission saw an unknown writer → every Linux read failed ("Could not read Linux"). |
| H4 | Orphan reaper (+ `stopTree` kills recorded stragglers) | `74cdf5dd5`, `230e45e62` (10-27) | Dead proot left tool shells spinning at 100 % for 19 min, phone 71–83 °C. |
| H5 | `core.createObject = rename` in `/etc/gitconfig` | `31746ee52` (10-18) | `--link2symlink` object links broke on any folder rename: `fatal: bad object HEAD`. |
| H6 | Name Android groups in `/etc/group` on every guest launch | `23e443fc5` (10-19) | "cannot find name for group ID 3003" at every shell prompt. |
| H7 | A boot or app update resets a settled restore count; open app + healthy server resets it once per visit | `b138eb525`, `c7e3c8ad2`, `7efd7994b` (10-25/26) | Each update spent one of 3 restores; the 4th update left the server off for hours. |
| H8 | Retry the connect after a failed connect (10–60 s backoff); replace a stale server | `5d7c8dbef` (9-28) | One connect per start + a healthy health poll = never connected. |
| H9 | Keep the phone awake only while a reply runs (10 min hold, 5 min renew, 6 h ceiling) | `78a695b9c` (9-29) | Replies "felt slow vs Termux"; free model + no wake lock. |
| H10 | Fast Node via official tarball instead of Ubuntu npm; `--foreground-scripts` | spike notes (9-23/24) | 10 min → 2.5 min installs; npm "Exit handler never called!". |
| H11 | Setup poll loop must survive one bad status read | `b17c67c7` (9-24) | Frozen "Unpacking Linux base" screen. |
| H12 | Dev-service/ports scan in POSIX sh; listing only ports that accept a loopback connect; candidate ports from commands, env and logs | `0e5ab8251` (10-16) | dash `Bad substitution`; `/proc/net/tcp` denied. |
| H13 | Reset Linux is one act: marker first, setup restarts at once, engine forgets the old job | `77899d2f5`, `57a463bcf` (10-16) | Erased in 4 s then stopped; "OpenCode is ready" from a stale in-memory job. |
| H14 | Linux manager reads retry twice on `runtime_ownership_unknown`/busy | `8e2c3c47c` | Transient "Linux is not set up". |
| H15 | tar must not create its own archive under proot | `7fe40608d` (11-01) | "Cannot open: Function not implemented"; empty zip left in Downloads. |
| H16 | Process uptime from the boot clock (`SystemClock.elapsedRealtime`) | `b0363a2e4` (10-28) | `/proc/uptime` is SELinux-denied; ages wrong. |
| H17 | `Process` pid via reflection fallback; never trust a pid alone | many | Pid reuse. |
| H18 | Clean environment, never source `.bashrc`; `{env:}` provider keys unusable | memory `inapp-server-clean-env` (10-13) | "A valid gateway Bearer is required" on explabs provider. |
| H19 | Codex needs `CODEX_HOME` folder to exist | issue #95, `edd1c9aa3` | Codex never worked on the phone. |
| H20 | Claude sign-in shared between Terminal and app via link; `CLAUDE_HOME` set | `2209455c5`, `373685393` | Terminal sign-in invisible to the app; Usage widget never showed Claude. |
| H21 | Stale `paseo.pid` / Claude OAuth lock cleared when nothing of ours runs | `355fcad4b` (10-18) | Resume failed forever after a killed helper. |
| H22 | `ensure projects folder exists before opening` / `POST /instance/dispose` | spike notes | OpenCode cached a failed instance for a missing folder. |
| H23 | Foreground-service promise: always show the notification before stopping; keep service during recovery delay | `BuiltinServerService` | `ForegroundServiceDidNotStartInTimeException` / lost FGS admission. |
| H24 | Android 15 `dataSync` cap → use `specialUse`; handle `onTimeout` | `BuiltinServerService.onTimeout` | Service killed after 6 h. |
| H25 | Wake lock/CPU leases are bounded and never override FGS policy | `618a94c11` (10-8) | Unbounded background CPU. |
| H26 | Git GitHub tokens via 0600 random file written from the app side | `git_shell.dart` | Token in a script/log. |
| H27 | Setup retries interrupted steps ≤ 3 times, not failing ones | `e958cf780` | Real failures would be hidden / infinite loops. |
| H28 | Preserve projects across runtime removal (`files/projects` outside rootfs) | `70d3f8135` (9-27) | Removing Linux deleted work. |
| H29 | Recreate after proot update: `PROOT_LOADER` path deleted by update → reaper accepts `libproot-loader*.so (deleted)` | `NativeOrphanReaper.isGuestProgram` | Updated app could not recognise old guest programs. |
| H30 | ProcessBuilder children of the app die with the app cgroup on force stop (`am force-stop`/OEM swipe kill) — nothing of ours runs; set of running services is persisted with `commit()` for the next start | `02046777e` (9-26) | Silent loss of the server; no explanation. |

---------------------------------------------------------------------------
## 10. Tricky behaviours and invariants (read before touching anything)

- **T1 — Authority gates live across the Dart/Kotlin line today.** `migrationMarkerValid` and `policyPermits` read `flutter.oc.builtinRecovery.<p>` / `flutter.oc.automation.<p>`. In the native app these become Kotlin data; **on first launch the migration must carry them over (and the restore recipe must remain compatible)** or restore after the first reboot silently never happens (`restoreReason = policyDisabled|storageUnavailable`). Test it with the emulator recipe in BUILTIN-LINUX-22.
- **T2 — Never kill by name/pattern/port** (AGENTS.md: the Termux-hosted dev container shares the phone with the live session server). Signals go to `(pid, start-ticks)` verified identities only.
- **T3 — `--kill-on-exit` is the only thing that ties a script's background jobs to its proot.** Anything meant to outlive a script is a *service* with its own proot.
- **T4 — One installer slot.** Setup, checks, package jobs, project scripts, git commands all serialise on it (`Another phone task is running` after 10 s). UI must treat that as "busy", not "broken"; reads retry.
- **T5 — proot env/flags are part of the contract** (see BUILTIN-LINUX-01); changing a flag changes performance and correctness (hard links, `/dev/shm`, sysvipc).
- **T6 — Receipts hold no secrets**; scripts are never persisted; cold start uses `NativeServerRecipe.restorationScript()` only.
- **T7 — Crash-sensitive prefs use `commit()`** and temp-file+rename; a SIGKILL can follow immediately.
- **T8 — Android decides lifetimes**: Stop must revoke admission on the main thread without waiting for I/O; drains run on a thread; destroyed services owe Android nothing; `onTimeout` has seconds.
- **T9 — Time:** use `SystemClock.elapsedRealtime` for deadlines and leases (a reversed or unavailable clock closes holds), wall clock only for display; boot id from `/proc/sys/kernel/random/boot_id` (36 chars) is the epoch for process identity.
- **T10 — UI copy contract:** all failures are enums (`BuiltinStartProblem`, `LinuxProblem`, `ComponentRemovalBlock`, `LinuxServiceProblem`, `AgentHostFailure`, …) turned into plain words by the screens; technical text only under Details, redacted (`kit_redact`). Rewriting must keep the enum layer.
- **T11 — Sampling is page-scoped.** Process list, vitals, Linux job polling run only while the page is open (and covered pages skip). Nothing samples in the background except the thermal listener.
- **T12 — Per-profile data and deletion sweep.** Anything per profile uses the `oc.<what>.<profileId>` name; deleting a profile calls `BuiltinServerRecovery.suspendForProfile` first, then sweeps keys, then erases the agent home (`deleteAgentHost`) and the phone-engine copy; shared blobs need explicit handling in `deleteProfileAndLocalData`.
- **T13 — Don't read the rootfs through links** from the Android side; an absolute symlink inside Ubuntu points somewhere else on the phone.
- **T14 — Scripts target dash** (`/bin/sh` in Ubuntu). Bash-only syntax breaks silently; tests run each script under dash and bash.
- **T15 — Keep sync copies in step:** `SharedStorageBinds` ↔ `lib/domain/shared_storage_path.dart`; `PhoneAgentSignIn.TESTED_CLAUDE_VERSIONS` ↔ `claudeCodeTestedVersions`.

---------------------------------------------------------------------------
## 11. Performance notes

- **Cold start & footprint (demo vs Flutter):** native APK 2.5 MB / 0.25 s vs Flutter 107 MB / 0.4 s; the built-in Linux needs ~1–1.5 GB (Ubuntu 2.5–6 GB with tools) in app storage regardless of UI stack.
- **proot speed** (emulator x86_64, medians): `opencode --version` 622 ms (ours) vs 810 (no seccomp) vs 703 (proot-distro launch); 200×`/bin/true` 292 vs 461 ms; node 20k×1 KB write/read 2.9 s vs 4.1 s. Seccomp acceleration must remain on (`Seccomp_filters` check in `performance`). `--link2symlink` costs 3–13 %. Fake /proc binds cost nothing.
- **Install times:** Ubuntu base ~5 s (emulator); OpenCode npm path 621 s → official Node tarball 237 s → native pinned binary ≈ 26 s weight; essentials 29 s; AI Team 150 s estimate; on real phones proot makes apt/npm several times slower — the ETA rescales to measured pace.
- **Reads:** `status` is called ~2×/s during setup; `BuiltinLinux.status()` shares an in-flight read; `bytesUsed` is a ≥ 10-min cached background walk; Ubuntu size walk is paused while a reply holds the wake lock.
- **Sampling costs:** process list ≈ 1 s of `/proc` reads for ≤ 32 pids (page-scoped, 5 s); recovery health check every 45 s steady / 5 s recovering; orphan reaper 60 s while any proot runs (idle sleeps); thermal headroom 30 s only while listened; installer ownership monitor 100 ms during an install only.
- **Process budget:** one proot (3 processes: proot, sh, program) per service; Android stops children above 32; AI Team tuned to peak ~33 / median 15 on Android 15 emulator, killed at 40 on Android 14.
- **Wake lock:** only held while a bounded lease exists; battery cost of vitals < 1 % of screen-on (design estimate).
- **Terminal throughput:** back-pressure at 256 KiB pending, 32 KiB chunks, 16 KiB read buffer, 512 KiB scrollback replay buffer.
- **Dart-side cost that disappears natively:** channel hop per call (each `_invoke` is traced `linux.<method>`; the 2×/s `status` poll), JSON marshalling of setup.json, Dart isolate startup; do keep the *rate limits*.

---------------------------------------------------------------------------
## 12. Risks, decisions and a recommended rewrite shape

### Owner decisions needed
- **Q1 Termux:** keep the Termux host (9.4k Dart + 8 Kotlin files, 100+ tests), keep only "move from Termux" (BUILTIN-LINUX-63), or drop? The first-open path for new users no longer needs it.
- **Q2 In-flight Linux branches:** land `services/vitals/backup` in Flutter before the freeze (they have tests and device proof, patch-level) or port from the branch? Either way the bash (`oc-svd`, backup/restore scripts) and file formats are reusable verbatim and should become assets with the same names so a backup made by the Flutter build restores in the native build.
- **Q3 Phone engine / AI Team:** which tier of AI Team survives; the Rust engine and its two protection tiers dominate `BuiltinLinux.kt` complexity.
- **Q4 Provider keys:** `{env:X}` provider keys break on the in-app server; add a native env-file loader or keep the warning.
- **Q5 Restore reason in UI:** `restorePhase/restoreReason` exist natively and are not shown; a native UI could finally show "Restarting your server…".

### Rewrite risks (ranked)
1. **Silent loss of restore authority at the switch** (T1) — highest consequence, hardest to notice. Mitigation: a migration test that edits `shared_prefs` on an emulator like the restore-proof recipe and a reboot test.
2. **Behavioural drift in ownership/drain logic** — 30 JVM tests + 8 instrumentation classes encode device findings. Port tests before refactoring; never "simplify" the `NativeRuntimeOwnership.plan` family.
3. **Process-count regressions** (phantom limit): a Compose app with more in-process work is fine, but extra helper processes (workers, `:processes`) count toward 32.
4. **Terminal rendering** replaces the `xterm` Dart fork; PTY/back-pressure must be re-proven (`yes`, Ctrl-C, resize, 512 KiB replay, key bar sticky modifiers, RTL/IME).
5. **Scripts as assets**: ~6,000+ lines of embedded shell with interpolation (`$` escapes in Dart raw strings) — extract mechanically, keep the script tests (they run under dash/bash on the PC) and add a checksum list so an accidental edit fails CI.
6. **Update path:** `versionCode` must be ≥ the last Flutter build; `rootfsGeneration`, recipe and installer ticket survive; `PROOT_LOADER` filename changes on update are tolerated (reaper accepts `(deleted)`), but proot binaries must remain at `nativeLibraryDir/libproot*.so`.
7. **Shorebird ends:** every pin change in this area (OpenCode, Node, Paseo, Claude Code, AI Team) is a new APK; consider moving pins to a signed remote manifest (checksum-verified, same trust as today) so agents/tools can update without a release.
8. **16 KB pages / Android 16:** bundled libs are aligned; re-verify on each proot/talloc bump; test on the Nubia NX721J (Android 16) and the API 35 emulator.
9. **Play policy:** a `specialUse` FGS hosting a user-installed runtime is a sideload-only design (documented in `docs/design/built-in-linux-plan-2026-09-23.md`).

### Recommended KMP-ready layout
- `commonMain`: models and rules — recovery budget/backoff, idle policy, work-lease registry, setup protocol parser + job state, component registry data (ids, pins, estimates, dependency graph), preflight, `LinuxProblem`/`BuiltinStartProblem` vocabularies, process-list/orphan/tree algorithms over a `ProcEntry` list, linux manager parsers, thermal policy, process-watch grouping, services definition/validation formats, backup plan/manifest, script assets.
- `androidMain`: `BuiltinLinux` split (proot launcher, installer slot, ownership proofs, services, foreground service, receivers, alarms, wake lock, PTY, Keystore, SAF).
- `desktopMain` (Linux): a `RuntimeHost` that runs the same scripts with `sh`/`bash` directly against the host (no proot, no rootfs); orphan reaper unnecessary; supervisor/vitals can reuse commonMain.
- `iosMain`: no built-in Linux; remote servers only. Keep every screen of this area behind capability flags (`ServerCapabilities`, `supportsBuiltinLinux`).

### First slices (suggested order)
1. Move the pure Kotlin policy files + their JVM tests into the new module unchanged (green on day 1).
2. Extract scripts to assets with golden tests; port `setup_scripts_test` family to JVM (run sh).
3. Replace the channel with `BuiltinLinuxService` interface + in-process implementation (same threading rules); re-run instrumentation acceptances.
4. Re-implement Dart policy: server start/probe, recovery consumer, healing, idle, setup engine, thermal guard, reply watch, linux manager.
5. First-launch migration (§5) with emulator proof: Flutter APK → native APK over it, then reboot restore.
6. Terminal, agent host orchestration, AI Team seam, quota/FactsBook.
7. Land Linux services/backup/vitals natively.

---------------------------------------------------------------------------
## 13. Status payload and other contracts worth freezing

`status` (all keys): `installed, phase (idle|installing|ready|failed), message, serverRunning, serverRestartWanted, serverRecoveryGeneration, serverRecoveryAuthority(true), serverRecoveryScheduled, restorePhase, restoreReason, serverPort, serverUptimeMs, services[names], abi, bytesUsed, serverIdlePolicySupported(true), serverIdleEnabled, serverIdleMinutes, serverIdleStopped, serverIdleHelperStopped, serverIdleGeneration`. Dart `BuiltinLinuxStatus.fromMap` is strict (negative/fractional → null; invalid idle receipt → `serverIdleReceiptValid=false` and idle/healing refuse to act).

`processes` → `{processes:[row…], sampledMs, orphansReaped, orphansLastReapedAtMs}`; row keys in BUILTIN-LINUX-11. `performance` keys in BUILTIN-LINUX-14. `setupStatus` returns the `setup.json` text. `projectStorage` → `{runtimeBytes, projectsBytes, measuredAtMilliseconds}`.

`ServiceDiagnostics.snapshot` → `{running, lastExitCode, lastUptimeMs, lastExitAtMs, lastStopRequested, uptimeMs, restartCount, exitReason: memory_or_phantom_kill|exited|unknown}`.

---------------------------------------------------------------------------
## 14. Test inventory to port first (from `/home/eslam/Storage/Code/wt/native-plan/catalog`)

The catalog files **253 behaviours** under area `builtin-linux`: 133 integration, 66 unit, 30 contract, 15 migration, 6 e2e, 3 screenshot. Heaviest source files (behaviours): `setup_engine_test` 50, `linux_manager_parse_test` 45, `phone_agents_host_test` 33, `setup_component_removal_test` 31, `phone_server_healing_test` 41 (area-adjacent), `builtin_linux_test` 26, `termux_scripts_test` 49, `setup_preflight_test` 25, `phone_agent_scripts_test` 21, `agent_removal_scripts_test` 20, `listening_ports_test` 19, `builtin_server_recovery_native_test` 19, `opencode_history_copy_test` 19, `phone_project_scan_test` 19, `local_terminal_test` 18, `detected_dev_servers_test` 17, `phone_process_watch_test` 17, `builtin_server_recovery_test` 17, `termux_migration_controller_test` 17, `managed_runtime_switch_preflight_test` 17, `builtin_server_test` 16, `quota_collector_host_test` 16, `aiteam_component_test` 16, `phone_agents_idle_host_test` 16, `phone_agents_removal_host_test` 16, `setup_scripts_test` 15, `termux_processes_test` 15. Other relevant: `builtin_work_leases_test` 13, `builtin_project_removal_test` 13, `local_terminal_signin_foreground_test` 13, `builtin_folders_test` 10, `thermal_guard_test` 29, `app_exit_recovery_test` 25, `app_exit_history_test` 11, `reply_watch_test`, `git_link_repair_test`, `termux_migration_*`. Logic tests that read the native state through a fake channel become direct JVM tests; script tests (`setup_scripts_test`, `linux_manager_parse_test`, `linux_services_script_test`, `linux_backup_plan_test`) run real `sh`/`bash`.
Kotlin: 30 JVM test classes (§7) + androidTest acceptance classes `BuiltinComponentFilesystemAcceptance, BuiltinComponentUpdateAcceptance, BuiltinIdleAcceptance, BuiltinRestoreAcceptance, BuiltinRuntimeAcceptance, BuiltinRuntimeReclaimAcceptance, BuiltinWorkLeaseAcceptance, PhoneEngineAcceptance, PhoneEngineNativeRegressions, Bb8DeviceSmoke, Bd9DeviceSmoke`.
The 7,967 widget tests for the screens listed in §1 come later by owner decision; the Services/Backups/Vitals widget goldens (`test/goldens/**`, `phone_*` goldens) are the UI spec.

---------------------------------------------------------------------------
## 15. Source file index (where to look)

Kotlin (`K/`): `BuiltinLinux.kt` · `MainActivity.kt` (channel switch) · `MainActivityBuiltinSupport.kt` · `BuiltinServerService.kt` · `BuiltinRestoreReceiver.kt` · `NativeServerRecipe.kt` · `NativeServerRestoreEvent.kt` · `NativeRecoveryBudget.kt` · `RestartBackoff.kt` · `NativeRuntimeOwnership.kt` · `NativeInstaller{Ownership,Admission,Visibility}.kt` · `NativeProc{Race,Scan}.kt` · `NativeProcessTree.kt` · `NativeProcessList.kt` · `NativeOrphanReaper{,Host}.kt` · `NativeComponentUpdateRecovery.kt` · `NativeIdle{State,Timer,Heartbeat,ReturnNotification,ReturnNotificationHost}.kt` · `IdleStopPolicy.kt` · `WorkLeases.kt` · `NativeWorkLeaseHost.kt` · `ServiceDiagnostics.kt` · `Setup{Runner,Job,Service,Persistence,StepRetry,ScriptCompletion,WorkScopes}.kt` · `BuiltinProjectStorage.kt` · `BuiltinGitConfig.kt` · `AndroidGroupNames.kt` · `PaseoStaleLock.kt` · `LocalTerminal.kt` · `ThermalMonitor.kt` · `AppLifecycle.kt` · `AppExitHistoryPolicy.kt` · `NativeCrashStore.kt` · `StatusNotification{,Model,Renderer}.kt` · `StorageAccess.kt` · `ProjectExportBridge.kt` · `PhoneAgent*.kt` · `PhoneEngine{Native,Attestation}.kt` · `NativeEngineReadiness.kt` (also holds `NativeEnginePeer`) · `Termux*.kt`.
Dart: see §4. Scripts: `lib/builtin/setup/{setup_scripts,components,aiteam_scripts,claude_scripts,component_updates}.dart`, `lib/builtin/agents/{agent_scripts,paseo_scripts,agent_removal_scripts,gen_ui_install_scripts}.dart`, `lib/builtin/linux_manager/{linux_processes,linux_ports,linux_storage,linux_packages}.dart`, `lib/termux/scripts/*`.
Docs: `docs/design/built-in-linux-plan-2026-09-23.md`, `phone-setup-v2-2026-09-24.md`, `linux-manager-2026-10-31/{1,2,3}-*.md`, `docs/qa/{linux-fixes-2026-10-16,thermal-guard-2026-09-26,slice-builtin-speed-2026-09-28,fresh-setup-2026-10-16,aiteam-builtin-2026-09-24,force-stop-2026-09-26}/README.md`, `docs/verification/in-app-ubuntu-trust-2026-10-07.md`, `docs/ai-team-host.md`, `docs/claude-on-this-phone.md`, `docs/technical-overview.md`; branch QA `docs/qa/linux-{services-s1a-2026-10-31,vitals-s2a-2026-11-01,backup-s3-2026-11-01}/README.md`.
Memory: `built-in-linux-plan`, `proot-orphans-heat`, `inapp-server-clean-env`, `restore-proof-recipe`, `aiteam-builtin-component-2026-09-24`, `issue-87-night-2026-09-23`, `two-agents-one-phone`, `emulator-lock-holder`, `vps-test-env-gaps`.
