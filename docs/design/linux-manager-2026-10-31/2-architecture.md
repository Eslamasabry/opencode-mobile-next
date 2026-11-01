# Linux manager, part 2 of 3: architecture (2026-10-31)

This part builds the jobs ranked in [1-use-cases.md](1-use-cases.md) on the code that ships today. The screens are in [3-ui.md](3-ui.md). `K/` means `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/`. "Patch" means a Shorebird Dart patch; "release" means a new APK.

## 0. What exists and stays

| Part | Where | Kept as is |
|---|---|---|
| Native services: one proot per named service, an exit monitor, one log per run plus `.previous.log`, a crash diagnostics record | `K/BuiltinLinux.kt:1730-1800, 3494-3700` | Yes. The new supervisor is one more native service. |
| OpenCode server: crash backoff, restore after boot or update, recovery budget, idle stop | `K/RestartBackoff.kt`, `K/BuiltinRestoreReceiver.kt`, `K/NativeServerRecipe.kt`, `lib/builtin/builtin_server_recovery.dart` | Yes, untouched. User services never share its proot. |
| Paseo and agent helpers (`agent-host.*`), AI Team, quota collector, FactsBook | their own native services | Yes |
| Orphan reaper: kills untraced Linux programs whose proot died | `K/NativeOrphanReaper.kt`, `K/NativeOrphanReaperHost.kt` | Yes. It protects every live proot the app started, including the new one. |
| Package job `linux-job`, guarded by an `flock` | `lib/builtin/linux_manager/linux_packages.dart:212-215` | Yes. Restore jobs reuse it. |
| Dart reads rootfs files directly at `<support>/linux/ubuntu/...` | `lib/host/byo_host_ssh_runner.dart:119`, `lib/builtin/builtin_folders.dart:73` | It's the read path for supervisor state and logs: no channel call. |
| Zip to a "Save to" location | `K/ProjectExportBridge.kt` (`oc/project_export`) | Reused for setup backups |

## 1. Service model and supervisor

### 1.1 Decision

| Option | Processes per service (Android stops an app's children above 32) | Patchable | Boot restore | Verdict |
|---|---|---|---|---|
| A. Kotlin per service: one more `startService` proot each, with backoff and caps added in `BuiltinLinux.kt` | 3 (proot, sh, program) | No. Every change is in a 4,500-line single-owner file and needs a release. | Native | No |
| B. **One app-owned supervisor, `oc-svd`**: a bash script in the rootfs, run as a single native service `svc`, with all user services as its children | **1 per service, plus 2 shared** (proot, supervisor) | **Yes.** Dart writes the script, the definitions and the control files. | Native, one fixed command (slice S5) | **Chosen** |
| C. Ubuntu `runit` (`runsvdir`, `runsv`, `svlogd`), the termux-services model | 3 per service, plus 1 | Partly | Native | No. Twice the processes, no exponential backoff, and its package scripts are untested under proot. |

`oc-svd` borrows runit's ideas (one folder per service, a wanted state on disk, a restart loop) but runs them in one process.

### 1.2 Files (all inside the rootfs; Dart reads and writes them directly)

| Path (guest) | Written by | Content |
|---|---|---|
| `/root/.oc-builtin/svc/oc-svd` | Dart (versioned; rewritten when the app's copy is newer) | The supervisor, bash |
| `/root/.oc-builtin/svc/defs/<name>.svc` | Dart, atomically (temp file, then rename) | Definition, see 1.3 |
| `/root/.oc-builtin/svc/want/<name>` | Dart | `on` or `off`: the person's choice. Survives every restart. |
| `/root/.oc-builtin/svc/kick/<name>` | Dart | A restart token (counter). A new value means "restart now". |
| `/root/.oc-builtin/svc/state/<name>` | supervisor, rewritten only when it changes (`held=` is the last line, so a half-written file is recognised and read again) | `phase= pid= since= restarts= last_exit= last_exit_at= next_try= fails= held=` |
| `/root/.oc-builtin/svc/keep` | Dart, just before it restarts a running supervisor | The next SIGTERM is the app's own restart: the person's choices are kept. Removed by the supervisor. |
| `/root/.oc-builtin/svc/hold` | Dart's heat guard (`LinuxServicesHeat`) | Present while the phone is critically hot: every service is stopped, `want` is untouched, and they start again when the file goes |
| `/var/log/oc-svc/<name>.log`, `<name>.log.1` | supervisor | Output plus `::svc` marker lines, see 1.6 |
| `/var/log/oc-svc/_svd.log` | supervisor | The supervisor's own lines (capped the same way) |

Names match `[a-z][a-z0-9-]{1,23}`. Reserved names: `server`, `svc`, `linux-job`, `phone-engine`, and anything starting with `agent-`, `aiteam` or `oc-`. There are at most 16 definitions.

### 1.3 Definition format

The format is plain `key=value`, one per line. bash reads it with `IFS='=' read -r`; nothing is `eval`'d or sourced.

```
version=1
name=syncthing
title=Syncthing
command=syncthing serve --no-browser --gui-address=127.0.0.1:8384
dir=/root
port=8384
restart=on-failure        # always | on-failure | never
stop_timeout=10
catalog=syncthing         # absent for "a command of your own"
env.STNODEFAULTFOLDER=1
```

`command` runs as `bash -c` with job control, so each service gets its own process group. Ports must be 1024–65535 and are checked against the reserved ones: 4097 (server), Paseo's port, and the other definitions.

### 1.4 Life cycle

| Event | What `oc-svd` does |
|---|---|
| Supervisor starts | Reads every definition. Starts each one whose `want` is `on`, one second apart so startup doesn't spike the phone. |
| Tick (every 2 s, using `read -t 2` on a private FIFO, so no `sleep` process is forked) | Compares `want`/`kick` with what's running. Starts, stops or restarts to match. Reaps exited children with `wait -n`. |
| Start | Probes the port with bash's `/dev/tcp`. If something answers, the phase is `port_busy` and it retries on the backoff. Otherwise it runs `cd "$dir"; exec bash -c "$command"` with output appended to the log, and writes `phase=running`. |
| Stop (`want=off`) | Sends SIGTERM to the process group, waits `stop_timeout`, then sends SIGKILL. Writes `phase=off`. |
| Restart (new `kick`) | Stop, then start. The backoff resets. |
| Exit while wanted | `restart=never` or exit 0 with `on-failure` gives `phase=exited`. Otherwise it sets `phase=backoff` and `next_try`. |
| Backoff | Same rule as the server (`K/RestartBackoff.kt`): 1 s, doubling to 60 s, and a run that lasted 30 s or more resets it. |
| Give up | After 5 exits in a row that each lasted under 30 s: `phase=failed`, `want` stays `on`, and no more tries until the person taps Start (Dart writes a new `kick`). |
| SIGTERM to the supervisor (the notification's Stop, Reset) | Stop is Stop: every `want` that is `on` is set to `off` (the same file a person's Stop writes), then every service is stopped and it exits. Only the app's own restart of the supervisor (a newer script) writes `keep` first, which keeps the choices. Android ending the app is a SIGKILL, so nothing is turned off and services come back when the app opens. `--kill-on-exit` sweeps anything left. |

There is **one state, not two.** `want=on` means "keep it running": run now, restart on crash, start again whenever Linux comes up. Stop sets `off` and it stays off. A separate "start with Linux" switch is left out on purpose (Q1, decided).

### 1.5 How it survives app death, process death and the phantom-process limit

| Threat | Effect | Answer |
|---|---|---|
| Android kills the app (LMK, swipe on some OEMs, OEM killers) | The app's cgroup goes, and every proot and service goes with it | The wanted state is on disk. **Patch level:** on app start, and whenever the phone server starts, Dart calls `ensureSupervisor()`, which starts `svc` if any `want` is `on`. **Release level (S5):** native code also starts `svc` on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` and a START_STICKY reclaim, beside the server, with a 3-attempts-per-boot budget. |
| The supervisor or its proot dies on its own | Its children lose their tracer | The reaper kills the leftovers within seconds (`orphanReaper.soon()` runs on every service-set change, `K/BuiltinLinux.kt:3669-3671`). Dart sees `svc` missing in `status()` and starts it again (S1). From S5, native code restarts it with `RestartBackoff`. The port probe stops a double start while leftovers are still dying. |
| The phantom-process limit (32) | Android kills an app's oldest children first | One proot for all services. The supervisor costs 2 processes, and each service costs its own tree. The Services page counts processes and warns from 28, the same threshold as `lib/builtin/phone_process_watch.dart:195`. There are at most 16 definitions. |
| The CPU suspends with the screen off | Services freeze until something wakes the phone. Network packets usually do, briefly. | Say so plainly. S5 adds one switch, "Keep the phone awake while services run", using a partial wake lock held only while a service is `on`. Decided (Q2): held only while the phone is charging. |
| The foreground service is refused from the background (Android 12+) | `svc` can't start | Dart starts it only while the app is in front. Native restore uses the existing restore-FGS path (`BuiltinServerService.startForRestore`). |

### 1.6 Logs

| Rule | Value |
|---|---|
| Where | `/var/log/oc-svc/<name>.log` plus one older chunk, `.log.1` |
| Cap | 1 MiB per file, so 2 MiB per service and 34 MiB at worst (16 services plus the supervisor) |
| Rotation | On each tick, the supervisor checks sizes every 30 s with one `wc -c` per service. Over the cap, it copies the log to `.1` and truncates the live file. The program appends, so it keeps writing safely. |
| Markers | `::svc start <epoch> <restart#>`, `::svc exit <code> <uptime_s>`, `::svc backoff <delay_s>`, `::svc gave-up`, `::svc port-busy <port>`. The UI turns these into words ("Crashed, exit 1. Trying again in 4 s"). |
| Reading | Dart reads the tail straight from `<support>/linux/ubuntu/var/log/oc-svc/`, which costs no channel call and no proot. Every line is redacted by `KitLogPanel` before it's drawn. |
| Storage page | `/var/log/oc-svc` joins "Temporary files and logs" in `linux_storage.dart`. Its cleanup truncates the logs and never deletes definitions. |
| Native per-run logs | S5 caps `<name>.log` in `K/BuiltinLinux.kt:3536` at 4 MiB (today it has no cap). |

### 1.7 What runs where

| Program | Supervised by | Notes |
|---|---|---|
| OpenCode server | native (`server`) | Unchanged. Its port 4097 is reserved. |
| Paseo and agent helpers | native (`agent-host.*`) | Unchanged |
| AI Team, quota collector, FactsBook | native | Unchanged. These are app features, not user services. |
| Dev servers an agent starts | the agent's shell, inside the server's proot | Unchanged. A later slice adds "Keep it running" on a Ports or Running row, which turns its command and folder into a definition. |
| User services and catalog apps | `oc-svd` inside `svc` | New |
| Terminal tabs | native (`LocalTerminal.kt`) | Unchanged |

**Running page.** The scan already reads parent links (`lib/builtin/linux_manager/linux_processes.dart`). Processes under the `svc` proot are grouped by the `pid` in each state file. Stop on a service process becomes "Stop SSH" (`want=off`). Killing the process alone would only get it restarted.

**Thermal guard.** Today it pauses AI Team at SEVERE and stops it at CRITICAL (`lib/builtin/thermal_guard.dart`). Decided (Q3): at CRITICAL or worse it stops every service that is on, and starts them again once the phone has stayed cool, with the same rules as AI Team (S1a).

### 1.8 Dart API (frozen before parallel work)

`lib/builtin/linux_services/linux_services.dart`:

```dart
enum LinuxServicePhase { off, starting, running, backoff, portBusy, failed, exited, stopping }

class LinuxServiceDef { String name, title, command, dir; int? port;
  LinuxRestart restart; Duration stopTimeout; String? catalogId; Map<String,String> env; }

class LinuxServiceState { LinuxServiceDef def; bool on; LinuxServicePhase phase;
  int? pid; DateTime? since, lastExitAt, nextTryAt; int restarts; int? lastExit; }

enum LinuxServiceProblem { linuxMissing, invalidName, nameTaken, invalidCommand,
  reservedPort, portTaken, tooMany, supervisorDown, writeFailed }

abstract interface class LinuxServices {
  Future<List<LinuxServiceState>> read();          // defs + state files; never throws on one bad file
  Stream<List<LinuxServiceState>> watch({Duration every = const Duration(seconds: 2)}); // only while listened
  Future<void> save(LinuxServiceDef def);           // validates; does not start
  Future<void> start(String name);                  // want=on, new kick; ensures supervisor
  Future<void> stop(String name);                   // want=off
  Future<void> restart(String name);                // new kick
  Future<void> remove(String name);                 // stop, wait for off (≤ stopTimeout+2 s), delete def/state/logs
  Future<List<KitLogLine>> logTail(String name, {int bytes = 65536});
  Future<void> ensureSupervisor();                  // start `svc` if any want=on and it is not running
}
```

Every failure is a `LinuxServiceException(problem, details:)`. `details` holds technical text for Details only. Screens map `problem` to words (rule "no raw errors").

### 1.9 Native contract (release slices only)

Channel `io.github.eslamasabry.opencode_mobile/builtin_linux` (both halves: `K/MainActivity.kt` handler plus `K/BuiltinLinux.kt`, and `lib/builtin/builtin_linux.dart`):

| Method | Args | Result | Errors (`code`) | Slice |
|---|---|---|---|---|
| `setServicesWanted` | `{wanted: bool}` | `null` | `not_installed` | S5 |
| `setServicesAwake` | `{awake: bool}` | `{held: bool}` | `not_installed` | S5 |
| `pickBackupFolder` | — | `{uri, label}` or `null` if cancelled | `unavailable` | S7 |
| `listBackups` | `{uri}` | `[{name, uri, bytes, modifiedMs}]` | `folder_gone` | S7 |
| `fullBackup` | `{uri, includeProjects: bool}` | `null` (progress via `backupStatus`) | `busy`, `no_space`, `destination` | S7 |
| `fullRestore` | `{uri}` | `null` | `busy`, `abi_mismatch`, `bad_file`, `no_space` | S7 |
| `backupStatus` | — | `{phase, bytesDone, bytesTotal, error?}` | — | S7 |
| `cancelBackup` | — | `null` | — | S7 |

`svc` boots with one fixed native command, `exec /bin/bash /root/.oc-builtin/svc/oc-svd`. No Dart-supplied script is stored for boot, the same principle as `NativeServerRecipe` ("nothing here is a Dart command").

Channel `oc/thermal` (both halves: `K/ThermalMonitor.kt`, `lib/platform/thermal.dart`): `vitals` → `{batteryPct, charging, batteryTempDeciC, memAvailBytes, memTotalBytes, lowMemory, storageFreeBytes}` (S2b).

## 2. Vitals from Android

`/proc/stat`, `loadavg`, `uptime` and `vmstat` inside Linux are fixed stand-ins (`K/BuiltinLinux.kt:1692-1719`), because Android denies them to apps. Vitals come from Android.

| Reading | Source | Exists today | Cost per read |
|---|---|---|---|
| Thermal status (none … shutdown) | `PowerManager.getCurrentThermalStatus` plus a listener, through `oc/thermal` | Yes (`K/ThermalMonitor.kt`) | Event-driven; nothing while idle |
| Thermal headroom (30 s forecast) | `getThermalHeadroom(30)`, API 30+. Android returns NaN if it's asked more than about once a second. | Yes, polled every 30 s while listened | One binder call |
| Battery temperature, level, charging | Sticky `ACTION_BATTERY_CHANGED`, read with `registerReceiver(null, …)`, so no receiver is kept | No (S2b) | One binder call |
| Phone memory free and total, low-memory flag | `ActivityManager.getMemoryInfo` | Total only (`oc/voice getDeviceInfo`) | One binder call |
| Linux memory | Sum of RSS of the app's Linux processes | Yes (`lib/ui/screens/this_phone/this_phone_answer.dart:105`) | Part of the process scan |
| Linux CPU % | Tick deltas of the app's processes over the scan window | Yes (`lib/builtin/phone_processes.dart:195`, about 1 s) | About 1 s of `/proc` reads for ≤ 32 pids |
| Whole-phone CPU % | Not readable by apps since Android 8 | — | **Not shown.** We don't fake it. |
| Storage free | `StatFs(filesDir)` | Yes (`oc/voice getDeviceInfo`) | Trivial |

**Sampling:** every 5 s, and only while a page showing vitals is on screen. That's the Linux page or Running on this phone, using the same pattern as `running_on_phone_screen.dart`. Nothing is sampled in the background except what the thermal guard already does. **Battery cost:** under 1% of the screen-on cost. The process scan is the only part that takes measurable CPU, and it already runs at this rate on Running on this phone.

**Words, not just numbers.** The thermal status sets the word: none or light is "Cool", moderate is "Warm", severe is "Hot", and critical or worse is "Too hot". The battery temperature is shown next to it, never instead of it. OEMs report battery heat late.

**Split.** S2a (patch) shows the thermal word, Linux CPU, Linux memory and free storage. S2b (release) adds battery and phone memory.

## 3. Backup and restore

### 3.1 Setup backup (S3, patchable)

| Item | Decision |
|---|---|
| What | `manifest.json`: format 1, created, app version, Ubuntu 24.04.5, ABI, setup components and versions, `apt-mark showmanual` minus the base image's list, `npm ls -g --depth=0`, `pipx list --short`, service definitions and wanted states. Plus `home.tar.gz` with the home files on the allow list, and optionally `projects.tar.gz`. |
| Home allow list | `.bashrc .profile .bash_aliases .inputrc .gitconfig .vimrc .tmux.conf .config/{git,nvim,htop} .ssh/{authorized_keys,config,known_hosts} .local/bin/` |
| Never included (enforced by a test on the plan) | `/root/.oc-builtin` (server password), `/root/.oc-opencode2`, `/root/.local/share/opencode*`, `.claude*`, `.config/gh`, `.git-credentials`, `.netrc`, `.npmrc`, `.aws`, `.ssh/id_*` private keys, anything Paseo or the agent helper signs in with. Credentials never leave the phone in a backup (open question Q4 asks about SSH keys). |
| Projects (optional, on by default) | `/root/projects` without `node_modules .venv venv __pycache__ build dist .dart_tool .gradle target .next`. `.git` is kept. |
| How | A `linux-job` (kind `backup`) builds the two tars inside Linux, so modes and symlinks are kept. Then `oc/project_export` zips the manifest and the tars into `opencode-linux-<setup|setup-projects>-<date>.zip` at a place picked with "Save to". The bridge only reads regular files inside `filesDir`, and the tars are exactly that. Staging is deleted afterwards. |
| Size | Setup is 1–40 MB. Projects are the person's own. The page shows an estimate first: `du` with the same excludes. |
| Restore | Pick the zip (`file_picker`, already in `pubspec.yaml`). Dart unzips it into `/root/.oc-builtin/restore/` and checks the manifest. A `linux-job` (kind `restore`) runs `apt install` on the names that still exist (others are listed as skipped), then npm and pipx, then unpacks home files (anything it replaces is kept as `<file>.before-restore`). It writes service definitions with `want` as saved, but starts nothing until the job ends. Projects go into `/root/projects/<name>`, or `<name> (restored)` on a clash. Progress uses the `::oc` lines the package job uses. |
| Reset | The Reset question gets "Back up first" (setup and projects), which runs before erasing. |

### 3.2 Full copy (S7, release)

| Item | Decision |
|---|---|
| What | The rootfs as it is (`filesDir/linux/ubuntu`), streamed as `tar.gz` (gzip level 1, for speed) by Kotlin with commons-compress, which is already a dependency (`K/BuiltinLinux.kt:15-16`). Projects are an optional second part (they live outside the rootfs, bound at `/root/projects`). A `manifest.json` comes first. |
| Left out | `/tmp`, `/var/cache/apt/archives/*.deb`, `/root/.cache`, `/var/log/oc-svc/*.1` |
| Ownership | proot's `--root-id` fakes uid 0. Files on disk belong to the app's uid, and there's no ownership database to save. Mode bits are real and kept. `--link2symlink` stored hard links as symlinks plus `.l2s.*` files. The Android-side tar copies those verbatim, and restore makes symlinks exactly as `unpack` does today (`K/BuiltinLinux.kt:4118-4170`). |
| Consistency | The person confirms. Native code then stops `svc`, the server and agent helpers, and holds the foreground notice "Copying Linux…" and a wake lock for the run. Afterwards it starts again what was running. |
| Size and time | A typical rootfs is 2.5–6 GB. At level 1 the copy is about 45% of that, so 1.1–2.7 GB, taking 5–15 min on a mid-range phone. The estimate uses `bytesUsed` from `status()`. Data streams straight to the destination with no temporary copy. |
| Where | A backup folder picked once (`ACTION_OPEN_DOCUMENT_TREE`, permission kept), so the page can list backups. `oc.linux.backupFolder` stores the tree URI. |
| Restore | It checks the ABI, which must match. It unpacks to `linux/ubuntu.restoring`, checks that `/bin/sh` and the manifest's file count match, swaps by renaming (the old copy is kept until the swap succeeds), and writes a new `rootfsGeneration` (`configure`, `K/BuiltinLinux.kt:4175`). That invalidates the old server recipe on purpose. Then it runs Repair, so OpenCode and the tools match this app's pins. A marker file makes an interrupted restore finish or roll back on the next start, the same pattern as `linux_reset_flow.dart`. If there isn't room for both copies, it offers to erase first, through the Reset question. |

## 4. Package search and the apps catalog

### 4.1 Package search (S4, patchable)

| Step | Detail |
|---|---|
| Input | At least 2 characters, debounced by 400 ms. Words must match `[a-z0-9][a-z0-9+.-]{0,40}`; anything else is dropped before it reaches shell. |
| Query | `apt-cache search --names-only -- <words>`. If that gives fewer than 5 hits, it searches descriptions too. Then one `apt-cache show --no-all-versions` call for the top 30 to get `Installed-Size`. Everything goes through `LinuxManager._run` (it retries a busy slot). Output is capped at 30 rows, well under native's 64 KB tail (`K/BuiltinLinux.kt:4419`). |
| Results | Installed matches come first, from the list already loaded. Then "Available to install". Each row gets an Install chip that starts the existing `LinuxJobKind.install` job. The existing locks still apply (`linux_packages.dart:33`). |
| Freshness | Shows "From Ubuntu 24.04's list, updated <date>" (the mtime of `/var/lib/apt/lists`). Update system refreshes the list. |
| Slow or busy | A skeleton appears at once. After 8 s: "Still searching…". At 20 s it times out: "Search took too long" with Try again. |

### 4.2 Apps catalog (S6, patchable)

Each app is a definition template, plus an install recipe (apt, or a pinned download checked against its SHA-256), plus a fit badge that's worked out on this phone (ABI, total RAM, never `memoryClass`, and free storage).

| App | Install | Port | Fit badge | Phone-specific notes |
|---|---|---|---|---|
| **SSH** | `dropbear-bin` (about 300 KB). OpenSSH is the fallback if the spike shows Dropbear can't do something needed. | 8022 | Works | Key only, with no password. "Add your laptop's key" takes a pasted public key. **Binds only to the Tailscale address** (100.64.0.0/10, read in Dart from `NetworkInterface.list()`). It won't start without Tailscale and never binds `0.0.0.0`. Spike: Dropbear under proot, plus a tailnet bind. OpenSSH's privilege-separation `chroot` is the known proot risk, which is why Dropbear comes first. |
| **Syncthing** | apt `syncthing` (1.27.x) | 8384 GUI (localhost), 22000 sync | Works | Local discovery may fail under proot. Relays and Tailscale addresses still work. Shared storage is reachable through the existing storage binds. |
| **Forgejo** | Pinned binary from Codeberg, arm64 and armv7, with SQLite | 3000 (localhost) | Works | About 150 MB of memory. Its built-in SSH stays off. |
| **code-server** | Pinned standalone release, arm64 only | 8080 (localhost), password in a file | Heavy, or "Not on this phone" on 32-bit | 300–800 MB of memory, about 100 MB on disk |
| A command of your own | — | optional | — | Name, command, folder (defaults to the open project) and port (optional) |
| Not offered; explained on one page | Home Assistant Core (install deprecated; no BT, USB or mDNS), Jellyfin (no hardware transcoding), llama.cpp (too slow for agents), Docker, Podman, k3s, Pi-hole (ports below 1024) | | | |

Installing an app is a `linux-job` (kind `install-app`). It saves the definition with `want=off`, then asks: "Start Syncthing now?"

## 5. Layering

| Piece | Dart | Shell in rootfs | Kotlin | Ships as |
|---|---|---|---|---|
| Definitions, validation, `LinuxServices` | `lib/builtin/linux_services/` | — | — | patch |
| Supervisor | writes it (`oc_svd_script.dart`) | `oc-svd` | `svc` runs as an ordinary native service | patch |
| Start the supervisor on app open or server start | `ensureSupervisor()` | — | existing `startService` | patch |
| Restore the supervisor after boot, update or reclaim; awake switch; log cap | — | — | `BuiltinLinux.kt`, `BuiltinRestoreReceiver.kt` | **release** |
| Vitals: thermal, Linux CPU and memory, storage | `lib/builtin/linux_vitals.dart` | — | existing | patch |
| Vitals: battery, phone memory | — | — | `ThermalMonitor.kt` `vitals` | **release** |
| Setup backup and restore | job scripts, plan, unzip | `linux-job` | existing `oc/project_export` | patch |
| Full copy, backup folder | — | — | new `BuiltinLinux` methods | **release** |
| Package search, catalog | `linux_packages.dart`, `linux_apps.dart` | apt / download scripts | — | patch |
| Screens | `lib/ui/screens/linux_manager/` + new kit parts | — | — | patch |

### Persistence and deletion

| Data | Where | Scope | Removed by |
|---|---|---|---|
| Definitions, wanted state, logs | rootfs `/root/.oc-builtin/svc`, `/var/log/oc-svc` | device (the in-app Linux) | Reset Linux and Remove OpenCode (the rootfs goes). Restored by a setup backup. |
| `oc.linux.backupFolder` (tree URI), `oc.linux.lastBackupAt`, `oc.linux.servicesAwake` | SharedPreferences | device. **Not per profile:** the in-app Linux belongs to the phone, not to a connection, so the `oc.<what>.<profileId>` rule doesn't apply. The `oc.linux.` prefix is reserved for it. | Remove OpenCode clears every `oc.linux.*` key and gives back the folder permission. Reset keeps the folder, so restore can find backups. |
| Native `svcWanted`, `svcAwake` | `builtin_linux_svc` prefs | device | Reset and Remove (in the same place they stop all services) |
| Backup files | the person's chosen folder | the person's own | **Never** by the app. The Remove question says they stay. |

`ConnectionController.deleteProfileAndLocalData` doesn't change, because no new key is per profile.

## 6. Risks and failure modes

| # | Risk | Mitigation |
|---|---|---|
| R1 | Services freeze while the screen is off | Say it on the Services page. S5 adds the awake switch. Held only while charging (Q2). |
| R2 | The phantom-process limit kills the oldest children, possibly the server | One proot. The process count is on the page, with a warning at 28. At most 16 services. The Keep running page explains the Android 12L+ toggle. |
| R3 | OEM killer or swipe kills everything | State is on disk. Dart restarts on open; native code restarts after boot or reclaim (S5). The existing Keep running guidance applies. |
| R4 | A supervisor bug takes every service down | The real script is tested on desktop against `sleep`, `false` and a port listener (the same approach as `test/linux_manager_parse_test.dart`). Native restarts `svc` (S5). Backoff stops tight loops. |
| R5 | Port clash with the server, Paseo, a dev server or a leftover | Reserved-port validation, a `/dev/tcp` probe and the `port_busy` phase. The reaper clears leftovers. |
| R6 | Logs fill storage | 2 MiB per service. Counted and cleaned by the Storage page. |
| R7 | Credentials leak into a backup | Allow list plus deny list. A test checks every plan against the protected paths. The manifest never holds env values. |
| R8 | A backup from another app version or phone | The setup backup restores by name, not binary. A full copy checks the ABI and runs Repair after the swap. |
| R9 | Full restore is cut off mid-way | A staging folder plus a marker. It finishes or rolls back on the next start. The old rootfs is kept until the swap. |
| R10 | A slow or cloud SAF provider | Streaming with progress and Cancel. A partial file is discarded (`ProjectExportBridge.discard`). |
| R11 | SSH reachable by others | Off by default. Bound to the tailnet only, key only, port shown. It never listens on Wi-Fi or mobile addresses. Shared with the parked dev-box spike. |
| R12 | `apt-cache` is slow under proot | Debounce, skeleton, 8 s notice, 20 s timeout, 30-row cap |
| R13 | Reset or Remove while services run | `stopAllServices` already stops every native service. `svc` is one, so the supervisor's SIGTERM handler stops its children. |
| R14 | Play policy: a `specialUse` FGS declared "on this device only" now carries an SSH listener | Matters only if we ship on Play (research decision 1). SSH stays opt-in and local to the tailnet (Q5). |
| R15 | `dart:io` writes into the rootfs race the supervisor | Atomic renames both ways. The supervisor skips a definition it can't parse and logs it. Dart retries a state read that won't parse once. |

## 7. Slices

The order follows the ranking in part 1. "Lane" marks who can work in parallel. Each lane is a separate branch and worktree with one owner. Shared single-owner files: `K/BuiltinLinux.kt` + `K/MainActivity.kt` handler + `lib/builtin/builtin_linux.dart` (lane N only), and `lib/ui/screens/this_phone_screen.dart` (lane U only).

| # | Slice | Size | Lane | Ships | Depends on |
|---|---|---|---|---|---|
| S1a | Supervisor, file formats, `LinuxServices` | M | A | patch | — |
| S1b | Services page, service detail with log, "A command of your own", Linux hub row | M | U | patch | API frozen (1.8); builds against a fake |
| S2a | Vitals strip (thermal, Linux CPU and memory, storage) | S | C | patch | — |
| S3 | Setup backup and restore, Backups page, "Back up first" in Reset | M | D | patch | the 1.3 format, for including services |
| S4 | Package search | S | C | patch | — |
| S8 | Merge Linux › Running into Running on this phone | S | U | patch | S1b (the Services group) |
| S5 | Native: `svc` restore after boot, update or reclaim; awake switch; native log cap | M | N | **release** | S1a |
| S2b | Native vitals: battery, phone memory | S | N | **release** | S2a |
| S6 | Catalog: SSH (spike first), then Syncthing, Forgejo, code-server; the "why not" page | M (S per app) | A + U | patch | S1 |
| S7 | Full copy, backup folder list, full restore | L | N | **release** | S3 format |
| later | Scheduled shell jobs; "Keep it running" from Ports or Running; a notification when a service gives up; heat rules for services | — | — | — | — |

Native slices S5, S2b and S7 ride **one** release. Everything else ships as patches against it or before it.

### Finish lines, non-goals and checks

| # | Finish line (on the emulator, then the owner's phone) | Non-goals | Focused checks |
|---|---|---|---|
| S1a | Save "my-bot" (`python3 bot.py`), start it, and it runs. `kill -9` it and it's back within 2 s. A command that exits at once 5 times shows `failed`. Force-stop the app and reopen: the bot runs again. Reset Linux: it's gone. | boot restore, catalog, awake | `test/linux_services_script_test.dart` runs `oc-svd` under bash on desktop: start, stop, restart, backoff timing (fake clock via env), give up, port busy, log rotation at a 4 KiB test cap, SIGTERM stops children. Parser and validation tests. |
| S1b | From Linux › Services: add a command, see it running, open its log, restart it, stop it. The crash state reads in words. Every state renders. | catalog | Widget tests on the fake: empty, loading, list, each phase, error and busy. 320 dp at 2× text, RTL. `kit_ratchet` G16 doesn't grow. ARB en+ar. |
| S2a | The Linux page shows heat word, Linux CPU, Linux memory and free storage, updated every 5 s while shown. Nothing is read once the page is gone. | battery | A unit test for sampling start and stop with route visibility. Golden of the strip. |
| S3 | Back up setup and projects, Reset, restore: packages, dotfiles, services and projects are back. No protected path in the plan. | full copy, folder list | A plan test against the deny list. Manifest round trip. Restore job script on desktop with a fake apt. Widget states. |
| S4 | Type "ripgrep" and see `ripgrep` with its size; Install it and the job runs; it shows as installed. | descriptions in Arabic | Parser tests on real `apt-cache` output. Input validation. A timeout state test. |
| S8 | One Running page. Service processes are grouped under their service and stopped by name. | — | The existing `running_on_phone_screen_test.dart`, extended |
| S5 | Copy: put the phone restart back into `linuxSvcKeepsRunning`, `linuxSvcEmpty` and `linuxSvcStartDetail` (en and ar), which until S5 say only "when the app restarts". Reboot the emulator with a service `on`: after unlock it runs without opening the app. Swipe the app away and wait: it comes back on reclaim. With the awake switch on, a screen-off SSH session stays up for 30 min. | — | Kotlin unit tests (backoff, budget), detekt and lint, the restore proof recipe (`pm unstop`, `install -r`) |
| S6 | SSH: from the laptop on the tailnet, `ssh -p 8022 root@<100.x>` with a pasted key works, and connecting to the phone's Wi-Fi address is refused. Syncthing opens its page and syncs a folder with the laptop. | Home Assistant, Jellyfin | Catalog badge tests per ABI and RAM. A spike note in `docs/qa/linux-apps-2026-…/README.md` |
| S7 | Full copy to a folder, Reset, full restore: the person's own apt packages and a running service are back. An interrupted restore recovers. | encryption | Kotlin tar round trip with symlinks, `.l2s` files and modes. An ABI mismatch test. Emulator proof with timings recorded. |

## 8. Future extension point: dev box

Out of scope. The parked "serve this phone to my devices" idea would reuse three things from here: the tailnet-only bind from SSH (S6), the vitals as a remote health card (S2), and `svc` for anything it serves. Nothing in this plan binds the OpenCode server beyond 127.0.0.1.

## 9. Owner decisions

**Decided 2026-10-31 (owner: "all as recommended"):** one state per service; keep awake only while charging; the heat guard stops services at CRITICAL and restarts them when cool; no private SSH keys in backups; opt-in SSH, Tailscale only; Export projects moves into Backups as "Projects only"; code-server shown, tagged Heavy.

| # | Question | Decision (recommended, approved) |
|---|---|---|
| Q1 | One state per service ("on" means keep running, including after restarts), or a separate "Start with Linux" switch? | One state |
| Q2 | Default for "Keep the phone awake while services run": off, on, or on only while charging? | On only while charging |
| Q3 | Should the thermal guard stop services at CRITICAL, as it does AI Team? | Yes, and start them again once the phone has cooled, the same rule as AI Team |
| Q4 | Include private SSH keys (`~/.ssh/id_*`) in setup backups? | No. Add the option later with a passphrase. |
| Q5 | Is the SSH listener acceptable while the app may go to Play with the "on this device only" FGS text? | Yes, tailnet-only and opt-in. Revisit if we go to Play. |
| Q6 | Does Export projects on This phone move into Backups? | Yes, as "Projects only" |
