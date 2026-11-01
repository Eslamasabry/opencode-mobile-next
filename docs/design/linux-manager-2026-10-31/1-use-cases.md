# Linux manager, part 1 of 3: use cases (2026-10-31)

The owner asked for use cases, then architecture, then UI, each approved before any code. This is the first. Part 2 is [2-architecture.md](2-architecture.md); part 3 is [3-ui.md](3-ui.md).

**Inputs:** the old-phones research, "Better Linux management UI" section (`/home/eslam/Storage2/wt/research-old-phones-2026-10-31/REPORT.md`), and today's code on `int/ui-batch-2026-10-14` at `998e4563f`. Paths below are from the repo root. `K/` means `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/`.

**Finish line for the whole plan:** a person keeps their own programs running in the phone's Linux, sees how the phone is coping, finds and installs packages by what they do, and can undo a broken Linux from a backup. All of this works without a terminal.

**Non-goal:** serving this phone to other devices (dev box mode). It's parked in the backlog. Part 2 marks where it would plug in.

## 1. Who uses this

| # | Person | Phone and setup | Situation that brings them here |
|---|---|---|---|
| P1 | **Amira, phone-only coder** | Mid-range Android, 8 GB. Codes only on the phone with OpenCode and Claude Code. | Her Telegram bot and a Vite dev server die whenever she leaves the chat or the app restarts. She wants them to keep running, to see why one crashed, and to restart it by name. |
| P2 | **Karim, phone runs hot** | Older Snapdragon, often in a pocket, on charge overnight. | The phone is warm and the battery drains. He can't tell whether Linux is the cause, or which program. He wants to see the temperature and stop the culprit. |
| P3 | **Lina, broke a tool** | Installed `nodejs` from apt over the setup's Node, or ran `pip install` as root. | OpenCode won't start any more. Repair puts the setup's tools back, but her own packages, dotfiles and services aren't covered. She wants "go back to Tuesday". |
| P4 | **Omar, short on storage** | 64 GB phone, 3 GB free. | Android warns about storage. He wants to know what Linux takes, free the safe parts, and keep a copy of his setup somewhere else so he can reset without losing it. |
| P5 | **Dev, SSH from the laptop** | Phone and laptop on the same tailnet. | He wants `ssh -p 8022 root@phone` (or VS Code Remote-SSH) into the phone's Linux to edit with a real keyboard and copy files. This is for his own use, not for serving agents to other devices. |

Every person also meets the shared situations: first open, Linux still installing, Linux missing (Termux host), large text, Arabic RTL, one hand, and the app killed by Android.

## 2. Jobs to be done

| ID | When … | I want to … | so I can … | People |
|---|---|---|---|---|
| J1 | I've written a bot, script or server | keep it running after I leave the screen or chat | rely on it the way I would on a laptop | P1 |
| J2 | it crashes, the app is killed or the phone restarts | have it come back by itself, with a limit | not babysit it, and not have it loop forever | P1, P5 |
| J3 | something I keep running misbehaves | read its recent output | see why without a terminal | P1, P3 |
| J4 | I want to change what runs | start, stop or restart a program **by its name** | stay in control without hunting for process IDs | P1, P2 |
| J5 | the phone feels warm or slow | see temperature, battery, memory and how much CPU Linux uses | know whether Linux is the cause | P2 |
| J6 | Linux is the cause | find the heavy program and stop it | cool the phone down | P2 |
| J7 | I need a tool but don't know its exact package name | search Ubuntu's packages by name or by what they do, then install | stop guessing names in a terminal | P1, P3 |
| J8 | I'm about to try something risky, or I've already broken Linux | back up my setup and restore it later | undo the damage without starting from zero | P3, P4 |
| J9 | I get a new phone, or reset this one | move my Linux setup and projects across | carry on where I was | P3, P4 |
| J10 | storage runs low | see what Linux takes and free the safe parts | keep the phone usable | P4 |
| J11 | I'm at my laptop on the same tailnet | SSH into the phone's Linux | work with a real keyboard and editor | P5 |
| J12 | I want a known self-hosted app (Syncthing, Git server) | install it with one tap and an honest note on whether it runs well on a phone | avoid an evening of guides that end in failure | P1, P5 |
| J13 | I've read that phones can run Docker or Pi-hole | find out quickly why this app can't | not waste time trying | all |
| J14 | a program serves a web page | open it | use its interface | P1, P5 |
| J15 | Linux is beyond repair | reset it without losing my work | start clean | P3 |
| J16 | a script should run every night | schedule it | automate upkeep | P1 |

## 3. Today's coverage

| Job | Today | Evidence |
|---|---|---|
| J1 Keep running | **Partly.** A terminal tab outlives screens while the app lives. Services exist natively, but only app-internal callers use them (AI Team, quota collector, FactsBook, BYO-host runner, package job). The user can't create one. | `lib/builtin/local_terminal.dart:12`; `lib/builtin/builtin_linux.dart:1058-1080` (`startService`/`stopService`/`serviceLog`); callers `lib/builtin/team/builtin_team.dart:811`, `lib/builtin/quota_collector_host.dart:341`, `lib/builtin/factsbook_node_host.dart:370`, `lib/builtin/linux_manager/linux_manager.dart:217` |
| J2 Come back | **Partly.** Only the OpenCode server has crash backoff (1 s doubling to 60 s, reset after 30 s up) and restore after boot or update. Other services are dropped when they exit. | `K/RestartBackoff.kt:1-26`; `K/BuiltinLinux.kt:954-972` (`handleServiceExit` recovers `SERVER` only); `K/BuiltinRestoreReceiver.kt`; `K/NativeServerRestoreEvent.kt:5` |
| J3 Read output | **Partly.** There's a server log fold on This phone and the package job's log tail. A native `serviceLog` keeps one log per run plus the previous one, with no size cap. No page shows a service's log. | `lib/ui/screens/this_phone_screen.dart:949`; `lib/builtin/linux_manager/linux_manager.dart:232-236`; `K/BuiltinLinux.kt:3536-3538` (rename to `.previous.log`), `K/BuiltinLinux.kt:3730-3740` |
| J4 By name | **Partly.** You can only *stop* a process, chosen by process. Start and restart exist only for the OpenCode server. | `lib/ui/screens/linux_manager/linux_processes_page.dart:1-13`; `lib/builtin/linux_manager/linux_manager.dart:145-157`; `lib/ui/screens/this_phone_screen.dart:1014-1033` |
| J5 Vitals | **Partly.** CPU % and memory per process are shown. Android's thermal status is read only by the thermal guard, and never shown. Battery level and temperature, and free memory, aren't shown. `/proc/stat` inside Linux is a fixed stand-in. | `lib/ui/screens/running_on_phone_screen.dart:1-22`; `K/ThermalMonitor.kt:17-33`; `lib/builtin/thermal_guard.dart`; `K/BuiltinLinux.kt:1692-1719` (fake `/proc`) |
| J6 Stop the culprit | **Fully.** "Running on this phone" marks Heavy and May be stuck, groups processes by owner, stops leftovers, and warns at 28 of Android's 32-process limit. The orphan reaper cleans up after a proot dies. | `lib/ui/screens/running_on_phone_screen.dart:1-22`; `lib/builtin/phone_process_watch.dart:193-196`; `K/NativeOrphanReaper.kt` |
| J7 Find a package | **Partly.** You can search installed packages and install by **exact name** (typed into a dialog, checked against apt). There's no search of what's available. | `lib/ui/screens/linux_manager/linux_packages_page.dart:139-150, 418`; `lib/builtin/linux_manager/linux_manager.dart:199-200` |
| J8 Backup / restore | **Not at all.** Export projects writes a zip of projects only. It doesn't keep links or packages, and there's no import. | `K/ProjectExportBridge.kt:1-20`; `lib/builtin/project_export.dart` |
| J9 New phone | **Partly.** Projects only, via the same export. | as J8 |
| J10 Storage | **Fully.** Breakdown, allow-listed cleanups, and space actually freed. | `lib/ui/screens/linux_manager/linux_storage_page.dart:1-12`; `lib/builtin/linux_manager/linux_storage.dart` |
| J11 SSH in | **Not at all.** No SSH server. The OpenCode server binds 127.0.0.1 only. | `K/NativeServerRecipe.kt:57` |
| J12 One-tap apps | **Partly.** Add tools installs the setup's own components (Python, Node, OpenCode, AI Team, voice), not self-hosted apps. | `lib/builtin/setup/components.dart:1-30`; `lib/ui/screens/this_phone_screen.dart:618, 1148-1157` |
| J13 Why not Docker | **Not at all.** | — |
| J14 Open a web UI | **Fully.** Ports lists only ports that answered, and opens them in the dev preview. | `lib/ui/screens/linux_manager/linux_ports_page.dart:1-10, 97` |
| J15 Reset safely | **Partly.** Reset keeps projects, finishes after a kill, and reinstalls. It doesn't offer a backup first. | `lib/builtin/linux_manager/linux_reset_flow.dart:1-23`; `lib/builtin/builtin_linux.dart:1105-1108` |
| J16 Schedule | **Not at all** for shell jobs. Only prompts can be scheduled. | `K/ScheduledPrompt*.kt` |

**Overlap found.** Linux › Running (`linux_processes_page.dart`, polls every 10 s) and This phone › Running on this phone (`running_on_phone_screen.dart`, polls every 5 s) both list Linux processes with CPU, memory and Stop. Part 3 merges them.

## 4. What's missing, and how often it matters

| Gap | Jobs | How often | Cost of the gap today |
|---|---|---|---|
| User services: define, start, stop, restart, start with Linux, crash backoff | J1, J2, J4 | **Daily** for P1 and P5. Every bot or dev server dies on app restart. | High. The workaround is a terminal tab, which dies with the app and has no restart. |
| Service logs with a size cap | J3 | Every crash | High. You can't see why something died. The native per-run log grows without bound. |
| Vitals from Android | J5 | Weekly for P2. Peaks when the phone is hot. | Medium. The data exists (thermal guard, process scan) but isn't shown. |
| Search available packages | J7 | Weekly for P1 and P3 | Medium. Installing needs the exact name, so people guess in a terminal. |
| Setup backup and restore | J8, J9, J15 | Rare, but critical. Once per breakage, once per phone. | **Very high when it happens.** A full rebuild takes 20–40 min on a phone, plus the person's own packages and dotfiles. |
| Full Linux copy | J8, J9 | Rare | High when it happens. Covers what the setup backup can't rebuild. |
| SSH server | J11 | Daily for P5, never for others | Medium for one group |
| One-tap apps with honest badges | J12, J13 | Occasional | Low to medium. Mostly a way in for P5. |
| Scheduled shell jobs | J16 | Rare | Low |

## 5. What we will not do

| Not doing | Reason |
|---|---|
| **Docker, Podman** | proot has no cgroups, namespaces or overlay mounts, and Android apps can't get them without root. |
| **k3s or Kubernetes clusters** | Same kernel limits as Docker. They also expect systemd and iptables. |
| **Pi-hole, or anything on ports below 1024** | Android only lets an app without root bind ports 1024 and up, and proot can't change that. Pi-hole needs port 53. Every catalog app uses a high port (SSH on 8022). |
| **systemd units** | systemd must be PID 1 and needs cgroups. The supervisor in part 2 reads its own small service file instead. |
| **Kernel modules, FUSE, iptables, mounts** | Not available to a non-root app on stock Android. |
| **Bluetooth, USB, mDNS for Linux programs** (so no Home Assistant integrations) | Android keeps these from app processes. Home Assistant Core install is also deprecated upstream. |
| **Raising Android's 32-process limit** | Only the user can change it, in Developer options on Android 12L and newer. We explain it and count against it. |
| **Setting a battery charge cap** | No public API. We show the battery's temperature and point to the phone's own setting. |
| **Serving this phone to other devices** | Parked as "dev box". SSH (J11) is the only listener in scope, for the owner's own laptop over Tailscale. |

## 6. Ranking

| Rank | Jobs | Why here |
|---|---|---|
| **Must have** | J1, J2, J3, J4 (services with logs); J8 + J15 (setup backup, offered before Reset); J5 (vitals) | Daily pain for the main persona. The highest damage when missing (lost setups). Vitals reuse data we already read. |
| **Should have** | J7 (package search); J11 + J12 (SSH, then Syncthing, Forgejo and code-server as catalog services); J9 (full Linux copy for a new phone); J13 (one "why not" explainer) | Each builds on the services layer or the backup format. |
| **Later** | J16 (scheduled shell jobs); "keep this dev server running" from the Ports page; a notification when a service gives up; heat rules per service | Small audiences, or they need a native release with little extra value. |
| **Already done, keep** | J6, J10, J14 | Covered today. Part 3 only moves them. |

### Top five use cases
1. **Keep my bot or dev server running and bring it back after a crash or restart** (J1 + J2)
2. **See why it stopped: its log** (J3)
3. **Back up my setup before something risky, and restore it after it breaks** (J8 + J15)
4. **See whether Linux is heating the phone** (J5, leading into J6)
5. **Find a package by what it does, and install it** (J7)

Plan approved by the owner on 2026-10-31, with every open question decided as recommended (see part 2 §9 and part 3 §7).

SSH from the laptop (J11) ranks sixth. It needs the services layer first and one Tailscale bind spike.
