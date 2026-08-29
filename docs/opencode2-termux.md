# OpenCode 2 on-device (Termux) feasibility — beta-18600

Desk research, 2026-08-29. Verdict up front: **the self-contained-phone story
survives the v2 port, essentially unchanged**, because our shipping v1 pipeline
never depended on anything v2 removed. What changes is a package name, a binary
name, and two env-var/endpoint details.

**Method note — verified vs inferred.** This machine is x86_64 Pop!_OS with no
Android device attached. "Verified" below means: read from the installed
packages under `/home/eslam/node_modules/`, from tarballs downloaded with
`npm pack` into the session scratchpad and inspected with `file`/`readelf`/
`strings`, from `npm view` against the live registry, or from this repo's own
shipping code (`lib/termux/bridge.dart`, `README.md`). "Inferred" means: prior
art, upstream issue references, or community documentation not re-tested here.
Everything inferred is flagged. Nothing here was executed inside Termux.

---

## 1. Can `opencode2` (npm `@opencode-ai/cli@beta`) run under Termux?

**Not in plain (bionic) Termux. Yes inside a proot-distro glibc rootfs — which
is what our app already ships for v1.**

### What the package actually is (verified)

`@opencode-ai/cli@0.0.0-beta-18600` (dist-tag `beta`; `latest` is still
`0.0.0-beta-17823`) is a **launcher-only** package:

- `package.json` declares `bin.opencode2 = ./bin/opencode2.exe`, a
  `postinstall.mjs` script, and twelve `optionalDependencies` — one per
  platform binary. `os: ["darwin","linux","win32"]`, `cpu: ["arm64","x64"]`.
- `postinstall.mjs` (read in full) resolves the matching platform package,
  hard-links/copies its `bin/opencode2` over the placeholder, and runs
  `--version` to verify. There is **no JS application code anywhere in the
  package** — the "`.exe`" placeholder is a shell script that just prints
  "postinstall was not run". If no platform package matches, postinstall
  throws `OpenCode does not provide a binary for <platform>-<arch>`.
- musl detection: `/etc/alpine-release` or `ldd --version` mentioning "musl".
  Termux's bionic is neither, so on Termux it would pick the glibc chain.

### The Linux binaries that exist on npm (verified via `npm view` + `npm pack`)

| Package | Exists @beta-18600 | Inspected |
| --- | --- | --- |
| `@opencode-ai/cli-linux-x64` (+`-baseline`, `-musl`, `-baseline-musl`) | yes | installed locally: ELF x86-64, dynamic, `/lib64/ld-linux-x86-64.so.2` |
| `@opencode-ai/cli-linux-arm64` | **yes** | tarball unpacked: **ELF aarch64, dynamically linked, interpreter `/lib/ld-linux-aarch64.so.1`, max symbol version `GLIBC_2.17`**, needs `libc/libm/libdl/libpthread` |
| `@opencode-ai/cli-linux-arm64-musl` | **yes** | tarball unpacked: ELF aarch64, interpreter `/lib/ld-musl-aarch64.so.1`, needs `libstdc++.so.6` + musl libc |
| `@opencode-ai/cli-android-*` / bionic build | **no** (searched optionalDependencies + registry) | — |

The binary is a **Bun 1.4.0 single-file executable** (strings contain
`Bun v1.4.0 (34cbb9a40) Linux arm64`, plus Zig 0.16 runtime markers): the whole
TypeScript app is embedded in a compiled Bun runtime. Consequences:

- **v2 is compiled-binary-only. There is no pure-JS npm entry point** you could
  run with Termux's own Node. (The source repo is Bun-first; running it from
  source would itself require Bun, and Bun has no Android/bionic build —
  inferred, long-standing upstream position, oven-sh/bun#1901.)
- The glibc floor is generous (2.17, ~2012), so *any* proot distro qualifies.
- On plain Termux, two independent blockers (both verified from package
  metadata, matching the upstream issues we already cite for v1):
  1. Termux's Node reports `process.platform === "android"`, so npm refuses
     the install outright (`os` allowlist → EBADPLATFORM). Same failure mode as
     v1 — anomalyco/opencode#12515, #10504 (referenced in our README).
  2. Even hand-extracted, the glibc binary's interpreter
     `/lib/ld-linux-aarch64.so.1` does not exist on bionic Android; the musl
     binary likewise expects musl's loader. Neither runs under bionic directly.

### Bottom line for Q1

`npm i -g @opencode-ai/cli@beta` **fails in plain Termux by design** and there
is no JS fallback to rescue it. Inside a glibc arm64 environment (proot-distro
Ubuntu/Debian) it installs and should run: the arm64 glibc binary exists on the
registry at exactly `0.0.0-beta-18600`, and its GLIBC_2.17 floor is met by any
Ubuntu since 13.10. **Actual execution under proot on a phone is the one thing
this desk pass cannot verify** — see §6.

---

## 2. How did v1 actually run in Termux? (correcting the premise)

**v1 was never a JS-under-Termux-Node story either.** Verified from
`/home/eslam/node_modules/opencode-ai@1.18.25`:

- Identical launcher architecture: `bin/opencode.exe` placeholder,
  `postinstall.mjs`, per-platform `optionalDependencies`
  (`opencode-linux-arm64`, `opencode-linux-arm64-musl`, …),
  `os: ["darwin","linux","win32"]`.
- The installed `opencode-linux-x64@1.18.25` binary is likewise a Bun-compiled
  glibc ELF (`BUN_SHOW_BUN_STACKFRAMES` marker; `libc.so.6` NEEDED).
- Very early releases (`0.0.0-202506…` line) predate this repo's Termux work
  and are irrelevant to the comparison.

What our app actually ships for v1 (verified, `lib/termux/bridge.dart` +
README "On-device (Termux) — automated"):

> Termux → `pkg install proot-distro` → `proot-distro install` an Ubuntu
> rootfs (container name `opencode-ubuntu`) → inside the chroot
> `apt install nodejs npm` → `npm i -g opencode-ai` → run
> `proot-distro login opencode-ubuntu -- env OPENCODE_SERVER_USERNAME=opencode
> OPENCODE_SERVER_PASSWORD=$(cat server.password) opencode serve --hostname
> 127.0.0.1 --port 4096`, detached under `nohup`, logs rotated by a manager
> script, health-polled by the app over `127.0.0.1`.

So v1 "worked in Termux" **because we run the glibc arm64 Bun binary inside a
proot glibc rootfs** — npm inside the chroot sees `linux`/`arm64`/glibc and
picks `opencode-linux-arm64`. v2 preserves every property that pipeline relies
on. **The selling point survives with a package/binary rename, not a
re-architecture.**

---

## 3. If binary-only: realistic Termux paths, ranked

### #1 — proot-distro glibc rootfs (what we ship today) — **recommended**

- **How**: exactly the existing `bridge.dart` flow; swap
  `opencode-ai@<ver>` → `@opencode-ai/cli@beta` (or a pinned
  `@0.0.0-beta-18600`), `opencode` → `opencode2`.
- **Practicality**: highest. Zero new user-visible steps; the app's entire
  bootstrap, log, health-poll, and recovery machinery already targets it.
  proot needs no root; the chroot shares the Termux network namespace, so the
  app reaches `127.0.0.1:<port>` (verified in production for v1).
- **Performance** (inferred, and consistent with v1 field behavior): proot
  intercepts syscalls via ptrace, so syscall-heavy work (npm installs, big
  `git status`, file scans) is noticeably slower than native — 
  CPU-bound LLM-free work is fine, and the server is mostly I/O + network
  bound. v1 already lives with this; v2's Bun runtime doesn't change it.
- **Setup cost**: ~1–2 GB rootfs + node + npm tree; first-run install is the
  long pole (our app already budgets up to 15 min).
- **Trim option** (inferred, worth testing): v2's chroot no longer strictly
  needs Node at runtime — Node/npm are only the *installer*. Downloading the
  `@opencode-ai/cli-linux-arm64` tarball with `curl` and un-tarring the single
  binary would drop the apt nodejs/npm step entirely. Keep npm for now
  (version resolution, `beta` tag following) but note the slimmer variant.

### #2 — glibc-in-Termux (termux-pacman `gpkg` repo), no proot

- **How**: the community `termux-pacman/glibc-packages` project publishes
  glibc runtime packages for aarch64 served from
  `https://service.termux-pacman.dev/gpkg/` (repo existence and aarch64
  support verified from the project README; install ergonomics **inferred** —
  it is primarily a pacman-based-Termux repo, with binaries run against a
  glibc prefix rather than bionic).
- **Practicality for our users**: low-medium. It requires either the
  pacman flavor of Termux or bolting the gpkg repo onto apt Termux, plus
  loader-path surgery (`--interpreter`/patchelf or the repo's wrapper) so the
  Bun binary finds `ld-linux-aarch64.so.1`. None of that is scriptable as
  robustly as proot-distro across devices, and it is community-maintained
  infrastructure we'd be taking a hard dependency on.
- **Performance**: native speed (no ptrace) — the one real advantage.
- **Verdict**: keep as a future optimization experiment, not the product path.

### #3 — Box64 / emulation

Not applicable: the phone is arm64 and a native arm64 binary exists. (Box64 is
for running x86_64 binaries on arm64.)

### Non-paths, for completeness

- **musl binary on bionic**: bionic is not musl; the musl loader isn't present
  either. No help.
- **Termux Node running v2 as JS**: impossible — no JS artifact exists (§1).
- **Requesting an upstream `-android-arm64` build**: the real long-term fix
  (Bun would have to support bionic first, or upstream would need a
  static-musl build with a bionic-safe syscall surface — both upstream work,
  not ours). Worth raising with the founder as a roadmap item; the proot path
  keeps us shipping meanwhile. A fully-static musl build (no dynamic loader)
  *might* run on bionic — today's musl binary is dynamically linked, so it
  does not (verified).

### Can `scripts/host/ubuntu-opencode.sh` be adapted?

Only conceptually. It's a **systemd user-service** manager (`systemctl --user`,
`journalctl`, `loginctl enable-linger`) around the official curl installer.
proot-distro Ubuntu has **no systemd** (no PID 1 init in a chroot), so the
unit-file machinery is dead weight there; its useful ideas (idempotent
install, find-binary-in-known-paths, listening check, update-then-restart) are
*already re-implemented* in `lib/termux/bridge.dart`'s manager script, which is
the right base to modify. Adapt `bridge.dart`, not `ubuntu-opencode.sh`.
(`ubuntu-opencode.sh` itself needs a separate v2 pass for *desktop* hosts:
binary name `opencode2`, password env, port — out of scope here.)

---

## 4. Auth on-device: getting the per-run password to the app

v2 facts (from `docs/opencode2-protocol-notes.md` §1, captured against a live
beta-18600 server):

- Every endpoint including `/api/health` requires HTTP Basic,
  username `opencode`.
- Password source: env **`OPENCODE_PASSWORD`**, falling back to
  **`OPENCODE_SERVER_PASSWORD`**; if neither is set, `opencode2 serve`
  generates a random password and prints `server password <pw>` on stdout.
  No CLI flag.
- Discovery file: `~/.local/state/opencode/service.json`
  (`{"id","url","pid","version","password"}`) written by the background
  service for first-party local clients.

**Can the app read `service.json`? No — and it doesn't need to.**

- The file lives in the chroot's `$HOME`, i.e. under
  `$PREFIX/var/lib/proot-distro/installed-rootfs/opencode-ubuntu/root/.local/state/opencode/service.json`,
  inside **`/data/data/com.termux/`** — another app's private storage. Android
  sandboxing makes that unreadable to our app, full stop. Termux "shared
  storage" (`~/storage`) only bridges `/sdcard`; state files never land there,
  and asking users to symlink state into world-readable `/sdcard` would leak
  the password to every app with storage permission. Dead end, and an anti-goal.
- Also note: whether the plain `opencode2 serve` path even writes
  `service.json` (vs only the managed background service) is untested here —
  another reason not to depend on it.

**The env-pinning path is already built.** For v1 the app *generates* the
password in Dart, pushes it through the RUN_COMMAND bridge into
`$OC_DIR/server.password` (chmod 600, inside Termux), and the server runner
exports `OPENCODE_SERVER_PASSWORD` from that file on every launch
(`bridge.dart` lines ~505–520). The app keeps its copy in secure storage. For
v2, keep the identical flow and export **both** names:

```sh
OPENCODE_PASSWORD="$(cat "$password_file")" \
OPENCODE_SERVER_PASSWORD="$(cat "$password_file")" \
opencode2 serve --hostname 127.0.0.1 --port "$port"
```

Result: **no pasting, no discovery file, no stdout scraping** — the app knows
the password because it minted it. Health-poll becomes authed:
`GET /api/health` with `Authorization: Basic base64("opencode:<pw>")`
(expect 401 without it, 503 during boot, 200 `{"healthy":true,...}` when up).
Manual-setup users (README's "Prefer manual?" box) pin `OPENCODE_PASSWORD`
themselves and paste it into the app once — the only flow where a human
touches the password.

---

## 5. Recommended bootstrap sketch (path #1, happy path)

Delta from today's shipped v1 bootstrap in `bridge.dart` — shown as the full
target script for clarity, not a committed file:

```sh
# ---- inside Termux (bionic side), run by the app via RUN_COMMAND ----
yes | pkg upgrade
pkg install -y proot-distro curl
termux-wake-lock                       # keep the server alive with screen off

proot-distro install ubuntu --name opencode-ubuntu   # ~1 GB, once
# (app already pins/caches the rootfs archive and retries — keep that)

# ---- one-time setup inside the chroot ----
proot-distro login opencode-ubuntu -- bash -s <<'SETUP'
set -Eeuo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update -y && apt-get install -y nodejs npm curl ca-certificates
# npm here sees linux/arm64/glibc -> resolves @opencode-ai/cli-linux-arm64
npm install -g @opencode-ai/cli@0.0.0-beta-18600   # pin; 'beta' tag moves
opencode2 --version                                 # fails fast if postinstall broke
SETUP

# ---- per-boot server runner (replaces v1 runner; password file unchanged:
#      app-generated, pushed over the bridge to $OC_DIR/server.password 600) ----
port=4097
proot-distro login opencode-ubuntu -- env \
  OPENCODE_PASSWORD="$(cat "$OC_DIR/server.password")" \
  OPENCODE_SERVER_PASSWORD="$(cat "$OC_DIR/server.password")" \
  opencode2 serve --hostname 127.0.0.1 --port "$port" \
  2>&1 | manager write-log server &   # keep v1's nohup/log-rotate/exit-report wrapper

# ---- app side ----
# poll http://127.0.0.1:4097/api/health with Basic opencode:<password>
# 503 => still booting (retry-after: 1); 200 {"healthy":true} => save profile, connect
```

Concrete `bridge.dart` edits implied: package spec + binary name, both
password env names, authed health probe, default port (4096 → 4097 or keep
4096 — v2 doesn't care, but don't collide with a lingering v1 install during
migration), and replace `opencode models --refresh` with a v2-appropriate
readiness call. Optional later: drop nodejs/npm by curl-ing the
`cli-linux-arm64` tarball directly (§3, "trim option").

---

## 6. What must be tested on a real device (nothing else can close these)

1. **The one load-bearing unknown**: `opencode2 serve` (Bun 1.4 runtime) boots
   and stays healthy under proot on Android — v1's Bun 1.x binaries do, and
   the glibc floor got no stricter, so risk is low but nonzero (Bun versions
   have historically shifted syscall usage; proot must fake e.g. modern
   `io_uring`-adjacent or `pidfd` calls correctly, or Bun must fall back).
2. npm-in-chroot resolves `@opencode-ai/cli-linux-arm64` (not `-musl`) and
   postinstall's `--version` check passes under proot.
3. Memory: the v2 binary is ~120 MB unpacked and Bun's RSS under proot on a
   4–6 GB phone during a long session (v1 baseline exists to compare).
4. Boot time to first healthy `/api/health` under proot, and behavior under
   Android's phantom-process killer with `termux-wake-lock` (expected same as
   v1, but the v2 server may spawn a different child-process pattern —
   §9 PTYs).
5. That `serve` respects `OPENCODE_PASSWORD` exactly as documented when both
   env vars are set (trivial, but auth is now load-bearing for the health poll).

