# TEAM-002 — Gas City inside the phone's proot rootfs (spike, in progress)

Date: 2026-09-10. Phone `the owner's phone` (arm64, 15 GB RAM, Android, Termux +
proot-distro `opencode-ubuntu` = Ubuntu 24.04 arm64), reached over Tailscale
SSH. Runs the app-managed OpenCode 1.18.29 server in the same rootfs.

Verdict (2026-09-11): **FAIL with Gas City 1.4.1 as shipped.** It installs
and the API comes up, but on the phone the supervisor's Go runtime
segfaults under proot, Dolt reports degraded storage, and the polecat's
OpenCode process never completes its ACP handshake. See §3c. Sprint C is
copy-only until one of the paths in §3c is taken.

Store decision (owner, 2026-09-10): the phone-hosted team stays in scope
because it serves users away from a PC and vibe coders with no PC at all.
TEAM-001 §4.1 rules out the file store, so the rootfs carries Dolt + bd.

## 1. What works on the phone

| Step | Result |
|---|---|
| `gascity_1.4.1_linux_arm64.tar.gz` (26 MB, sha256 `6620ef51…7e29` ✓ matches release checksums) | `gc version` → 1.4.1 under proot |
| `beads_1.2.2_linux_arm64.tar.gz` (45 MB, sha256 `501f38a1…fd83a` ✓) | `bd version` → 1.2.2 (warns it cannot read `/proc/sys/kernel/osrelease`; harmless) |
| `dolt-linux-arm64.tar.gz` (41 MB, sha256 `850a880a…aed3`, no upstream checksum file) | `dolt version` → 2.3.3 |
| `apt-get install tmux jq procps lsof` | ok |
| `gc init --file city.toml --name phone-lights --no-start` | ok after `dolt config --global user.name/email` and `git config --global beads.role maintainer` (gc init refuses without them) |
| `gc rig add /root/projects/aiteam-spike --name spike` | ok: prefix `sp`, default branch master, gastown import, beads DB initialised |
| `gc import install` | 3 remote imports installed (pack cache fetched over the phone's network) |
| Lean profile via `[[patches.agent]] name="gastown.mayor" suspended=true` (same for deacon, boot) | accepted by `gc doctor` |
| `gc supervisor run` (detached with `setsid nohup`) | API on `127.0.0.1:8372`, `/v0/cities` answers |
| `gc register --name phone-lights` | reached "[8/8] Waiting for supervisor to start city … Adopting sessions…" and then the SSH connection was closed by the remote host |

Install footprint in the rootfs: ~112 MB of tarballs, ~230 MB extracted
(dolt 2.3.3 is the bulk), plus apt packages. The plan's "about 60 MB"
becomes "about 250 MB".

Memory before start: 4.4 GB available of 15 GB (the app's OpenCode server
already running). Disk: 6.6 GB free.

## 2. Prerequisites the onboarding step must handle (learned here)

- Dolt identity (`dolt config --global --add user.name/user.email`) and
  `git config --global beads.role maintainer` before `gc init`; both are
  hard errors otherwise.
- `gc rig add <path> --name <rig>` (TEAM-001 §4.3).
- The project needs a git `origin`; a local bare repo next to it is enough.
- `gc doctor` on the phone reports `order-firing-current — order history
  lookup timed out after 15s` and `beads-store — store ping failed` on the
  first run while the managed Dolt server is still coming up; not fatal.
- `bd` and `gc` need `/proc/sys/kernel/osrelease`, which proot denies; they
  warn and continue.

## 3. What killed the run

`gc register` starts the city under the already-running supervisor, which
spawns the managed `dolt sql-server`, tmux, and the pack's agents (witness,
refinery, polecat pool, dog pool, plus control-dispatcher), each agent an
`opencode acp` process with its own MCP servers. Within seconds the SSH
session was closed by the remote host and port 8022 stopped answering while
the phone itself kept answering Tailscale pings. That pattern (the whole
Termux tree gone at once, phone alive) matches Android's **phantom process
killer** (limit of 32 child processes per app on Android 12+) or a low
memory kill of Termux. Which one it was needs `logcat` or the Termux
notification, which requires the phone in hand.

Consequences for the design, whichever it was:

1. The on-device city must run **far fewer processes** than the stock pack:
   polecat pool `max_active_sessions = 1`, refinery on demand, witness, dog
   pool, mayor, deacon and boot suspended. Even then: supervisor + dolt +
   tmux + one agent + its MCP servers is ~8–10 processes on top of the
   OpenCode server the app already runs.
2. The Termux setup step should apply the documented phantom-process
   mitigation (`settings put global settings_enable_monitor_phantom_procs
   false` via ADB) or at least detect the kill and explain it. Most users
   cannot run ADB; the copy must be honest about it.
3. The manager script must treat "Termux died" as a recoverable state:
   `gc supervisor run` again, `gc register`, sessions re-adopted.

## 3b. Second attempt (2026-09-11, lean profile)

With mayor, deacon, boot and witness suspended (`[[patches.agent]] …
suspended = true`; the dog pool is named `bd.dog` here and was left alone)
and `spike/gastown.polecat` capped at one session:

- Termux survived `gc register`. The city took ~9 min to report healthy;
  `starting_bead_store` alone took 3 m 10 s (managed Dolt on the phone).
- Process count peaked at 35 during start-up, then sat at 24; memory stayed
  above 5.7 GB available.
- Bead `sp-rde` was created with `gc bd create` and slung to the polecat pool
  (`POST /sling` with `X-GC-Request` accepted on loopback, same as the PC).
- While polling for the claim, SSH stopped completing its handshake (TCP
  connects, banner never arrives), then a few minutes later port 8022 was
  refused: the Termux tree was gone again. The owner reported the phone
  itself was fine and responsive throughout.

Reading: this is Android's background handling of Termux (screen off /
app not in foreground → CPU throttled, then the process tree killed), not a
crash of Gas City. The onboarding step must therefore require
`termux-wake-lock` and the "Unrestricted" battery setting for Termux, and
the manager must treat "team killed by Android" as a normal, recoverable
state. Whether a polecat can then finish a bead is still unmeasured.

## 3c. What the second run's log says (read after the fact)

The supervisor log survived (`docs/qa/ai-team/phone/supervisor-crash-excerpt.txt`,
full copy in the spike workspace). In order:

| Time after start | Line |
|---|---|
| 3 m 10 s | `starting_bead_store took 3m9.98s` (managed Dolt on the phone's file system) |
| from 6 min | `trace: slow_storage_degraded … durable` repeated 7×; `GET /agents` taking 4–14 s |
| ~9 min | polecat `pl-kau` spawned for bead `sp-rde`; `acp handshake … initialize timeout: context deadline exceeded` after 32.9 s → `outcome=provider_error` |
| ~11 min | `futexwakeup addr=… returned -38` then `SIGSEGV: segmentation violation` in `runtime.futexwakeup` (Go 1.26.5, `gc` linux_arm64) → supervisor dead |

Three independent blockers, none of them Android's process killer:

1. **Go runtime crash under proot.** `futex` returned `ENOSYS` (-38) and the
   runtime aborted. proot's syscall translation on this Android kernel does
   not support the futex variant the Go 1.26 runtime uses on wake-up. It is
   load-dependent (the binary ran for 40 min before it hit), so it will
   recur. This needs an upstream fix (Gas City built with an older Go, or a
   `GODEBUG` switch if one exists) or a different container than proot.
2. **Dolt is too slow on the phone.** Three minutes to start, then "slow
   storage degraded" continuously and a 14-second `/agents`. Dolt was never
   meant for a phone's flash + proot overhead.
3. **The agent could not start.** OpenCode's ACP `initialize` did not answer
   within the supervisor's 30 s deadline while Dolt and the app's own
   OpenCode server competed for the CPU. Even a healthy supervisor would have
   released the bead.

Earlier attributions (phantom-process killer, screen-off throttling) may
still be true on top of this, but they are not what stopped the run.

### Paths that keep "no PC required" alive

- **A. Fix upstream, retry on-device.** Report the futex crash to Gas City
  with the trace; ask for a beads backend the pack can use without Dolt
  (the file store exists but the Gas Town pack's queries need `bd`). Cost:
  unknown wait; still leaves the CPU contention.
- **B. Phone as control plane, team on a rented host.** The app offers
  "Host on a server" next to "On this computer": a one-command install on
  any small Linux VPS the user owns, reached over Tailscale like a PC. Same
  code path as the computer host; no proot, no phone CPU. Cost: the user
  pays for a VPS; privacy stays inside the tailnet.
- **C. A lighter on-device orchestrator.** One OpenCode agent at a time in a
  git worktree, driven by the app's own manager script and beads' file
  export, no Dolt, no supervisor. Loses the Gas Town pack (refinery,
  formulas) and the Gas City API. Cost: our own engine, our own bugs.

Recommendation: ship B in Sprint C's slot (it is the same adapter and UI),
file A upstream, keep C as a later experiment.

## 3d. Emulator replica over ADB (2026-09-11)

Owner asked for the same run on the Android emulator on the PC (AVD
`OCMN_UI_Refinement`, x86_64 Android 14, kernel 6.1, 4 GB RAM, 4 cores,
Termux 0.118.3 debug build driven through `adb shell run-as com.termux`,
the app's own `opencode-ubuntu` proot rootfs, amd64 `gc`/`bd`/`dolt`,
logcat captured for ActivityManager/phantom-process events).
Script: `scripts/termux/aiteam-spike-emulator.sh`.

| Observation | Emulator | Phone (§3b/3c) |
|---|---|---|
| `gc`, `bd`, `dolt` run under proot | yes | yes |
| City healthy after `gc register` | 1 m 56 s | ~9 min |
| `starting_bead_store` | ~12 s health check, "slow_storage_degraded" once | 3 m 10 s, degraded 7× |
| Android kills (logcat) | **none** in 40 min | not measurable (no logcat) |
| Supervisor death | only when the `proot-distro login` that spawned it exited (`--kill-on-exit`) | first run: same cause (my SSH session's proot); second run: Go futex `SIGSEGV` |
| ACP handshake | 4 of 6 agent starts hit `initialize timeout` at ~32 s; the others took 4–10 s | 1 of 1 timed out |
| Bead claimed by a polecat | yes, ~20 min after sling | never |
| CPU profile | `proot` itself 67–91 % CPU; `gc`/`bd` subprocesses 10–20 % each | phone load average > 5 |

What this settles:

1. **The "Android killed it" story was wrong.** proot's `--kill-on-exit`
   killed the supervisor whenever the login session that started it ended.
   The app's manager already avoids this for the OpenCode server (its own
   long-lived `proot-distro login`); a team supervisor must be launched the
   same way. Fixable.
2. **proot is the performance ceiling.** Every syscall of every child is
   ptrace-intercepted; the Gas Town pack runs dozens of short `gc`/`bd`
   processes per agent step, and each one pays. That is why the handshake
   misses a fixed 30 s deadline and why a claim takes 20 minutes. Not
   fixable from the app; only a native (non-proot) runtime or a pack that
   does not shell out would change it.
3. **The Go futex crash did not reproduce on kernel 6.1 in 40 min**; it
   remains a real-phone risk (older kernel / vendor seccomp).

## 3e. Decision proposed to the owner (2026-09-11)

1. Phone = control plane; the team runs on a computer (Sprint A) or on a
   small server the user owns ("Host on a server", one-command install,
   reached over Tailscale) — this covers the vibe coder with no PC and
   takes Sprint C's slot.
2. "On this phone" is kept, but as a second adapter behind the same
   `OrchestrationGateway` and Team UI: the app's own managed OpenCode
   running one background agent per task in a git worktree (already proven
   in `docs/qa/background-agent-proof/`), a small local queue, no Dolt, no
   supervisor, no proot subprocess storm. UI name: "Solo agent on this
   phone", experimental. Sized after Sprint A's UI exists.
3. Report the futex crash and the fixed 30 s ACP handshake deadline
   upstream to Gas City with the logs; revisit on-device Gas City only if a
   proot-friendly build appears.

## 3f. The way through: native Termux + one proot wrapper (2026-09-11, late)

Owner: "there must be a way". There is. Two facts made it:

1. **The crashes were Android's seccomp policy, not proot or Android's
   process killer.** Running the prebuilt `gc` natively in Termux on the
   phone gave the real trace: `SIGSYS: bad system call` in
   `syscall.faccessat2` from `os/exec.LookPath` (Go 1.26). Android forbids
   that syscall for apps and kills the process. Under proot the same policy
   surfaced as the futex `SIGSEGV`. Go's `GOOS=android` builds avoid the
   forbidden syscalls (Go's own `internal/syscall/unix/eaccess.go` returns
   ENOSYS on Android for exactly this reason). `dolt` hit the same syscall.
2. **`gc`, `bd` and `dolt` do not need the Ubuntu rootfs.** Only the
   OpenCode agent process needs glibc. So the layout is: supervisor, beads
   CLI and Dolt run natively in Termux; a 1 KB `opencode` wrapper in
   `$PREFIX/bin` runs the agent inside `proot-distro login --work-dir "$PWD"`
   with the `GC_*` env forwarded (`GC_BIN` pointed at the rootfs copy of
   `gc` so the agent's own `gc`/`bd` calls work), and creates the agent's git
   worktree first because the ACP provider never runs the pack's `pre_start`.

Builds (`scripts/termux/aiteam-build-android.sh`): `gc` and `bd` with
`CGO_ENABLED=0 GOOS=android GOARCH=arm64` (pure Go; PIE with
`/system/bin/linker64`); `dolt` with the NDK clang and Termux's `libicu`
78.3 headers/libs (`pkg install libicu` on the phone), `-static-libstdc++`,
rpath into `$PREFIX/lib`. `bd`'s released binary is glibc-dynamic, so it is
rebuilt rather than reused. Termux's exec shim must run with
`TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE=disable` for the prebuilt static
binaries; the Android builds run in either mode. One pack script uses
`#!/usr/bin/env bash`, which the shim does not rewrite; patch it to
`#!/bin/bash` after `gc import install`.

Results:

| | Phone, proot city (§3b/3c) | Emulator, proot city (§3d) | Emulator, native | Phone, native |
|---|---|---|---|---|
| `starting_bead_store` | 3 m 10 s | ~12 s | 5.4 s | 6.5 s |
| City healthy after start | ~9 min | 1 m 56 s | 27 s | 18 s |
| Agent start (ACP handshake) | timeout at 32 s | 4 of 6 timed out | 11 s, 5.6 s (refinery) | wrapper was a 0-byte file on the first try (PC root partition was full); retest pending |
| Polecat claimed → committed → pushed | never | claimed at ~20 min, no commit | claimed at ~5 min, commit `7c82e9e`, pushed `polecat/sp-0nk`, handed to refinery | pending |
| Supervisor crash | Go futex SIGSEGV | none | none | none (SIGSYS fixed by the Android build) |

Remaining on the phone: rerun with the repaired wrapper; keep the screen
on or hold `termux-wake-lock` during the run (SSH drops the moment the
phone sleeps).

Design consequences for Sprint C (supersedes §3e's options A–C):
- On-device stays **Gas City**, in the hybrid layout above. The manager
  script installs the three Android-built binaries into `$PREFIX/bin`,
  installs `libicu git jq tmux`, writes the wrapper, and launches
  `gc supervisor run` as its own long-lived process (like the OpenCode
  server), never from a login session that will exit.
- The app ships the Android builds itself (or downloads them from this
  project's releases with pinned checksums); upstream Gas City / beads /
  Dolt do not publish Android binaries.
- `termux-wake-lock` and the battery "Unrestricted" setting become part of
  the onboarding step; the copy in `03-onboarding-on-device.md` §2 stays
  honest about Android stopping the team when the screen is off.

## 3g. Third phone run (2026-09-11 morning): killed while working, wake lock held

With the native layout, the corrected wrapper (it created the polecat's
worktree: `HEAD is now at d94ef1d init` in the wrapper log) and
`termux-wake-lock` held, the city came up in ~15 s, the bead was slung at
08:31, and at 08:40 port 8022 went to "connection refused" again: the whole
Termux tree gone. The wake lock rules out doze; what remains is Android's
**phantom process killer** (Android 12+: max 32 child processes per app, and
the children of a background app are killed for "excessive CPU"), plus the
vendor's own background cleaner. Gas City's profile — dozens of short
`gc`/`bd` children and a polecat at full CPU — is exactly what it targets.

Mitigation (one-time, from Termux itself via Wireless debugging; no PC):

```
pkg install android-tools
adb pair <ip:port from the pairing dialog>        # 6-digit code
adb connect <ip:port from the Wireless debugging screen>
adb shell settings put global settings_enable_monitor_phantom_procs false
adb shell device_config set_sync_disabled_for_tests persistent
adb shell device_config put activity_manager max_phantom_processes 2147483647
```

plus Settings › Apps › Termux › Battery › Unrestricted and the vendor's
auto-clean switch off. Not yet confirmed on the owner's phone (`logcat`
needs the same Wireless debugging). Product consequence for Sprint C: the
on-device step must detect this kill (supervisor gone while the app was in
the background) and show these steps, in one sentence plus a "How" sheet.

## 4. Still to do

- [x] Get back in and read the logs (§3c).
- [ ] Re-run with the minimal process profile and one polecat; record
  `/health`, `/status`, `/agents`, memory, and whether the bead lands.
- [ ] Loopback write path: `X-GC-Request` header sufficed on the PC; confirm
  on the phone.
- [ ] Session link: the phone's OpenCode server is the app's own; check
  whether an `opencode acp` agent shows up in it (TEAM-001 says no).
- [ ] 30 min screen-off observation.
- [ ] Verdict.

Files: `/home/eslam/Storage/Code/gascity-spike/phone/{phone-spike,phone-start,phone-run}.sh`
are the exact scripts run inside the rootfs (copied to `/root/` there).
