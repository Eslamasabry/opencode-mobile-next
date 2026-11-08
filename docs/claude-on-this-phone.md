# Claude Code on this phone

> **Retired (2026-11-07).** The app no longer runs anything in Termux;
> built-in Linux (`lib/builtin/`) is its only engine on the phone. This page
> is kept as history.

The app can install and run Claude Code on the phone itself, with no computer.
It does this the way it already runs OpenCode: inside Termux, in the Ubuntu it
manages (`proot-distro` container `opencode-ubuntu`). Claude Code has no
network API of its own, so the app also installs the open-source
[Paseo](https://github.com/getpaseo/paseo) daemon (`@getpaseo/cli`,
Apache-2.0), which drives Claude Code and which the app already knows how to
talk to (see [paseo-connection.md](paseo-connection.md)).

Read the [Verification status](#verification-status) first: this was built and
unit-tested without a device.

## Where to find it

**On this phone** setup, once Ubuntu is installed: the block titled
**Claude Code on this phone**. That block is the only place it is installed,
signed in to and removed.

- On a phone with nothing set up yet, the setup's runtime step offers
  **Claude Code** as a third choice beside OpenCode 1 and OpenCode 2. The
  script that installs Ubuntu always finishes by starting an OpenCode server,
  and the setup screen's progress is built on that, so there is no clean
  "Ubuntu only" path. Choosing Claude Code therefore installs the default
  OpenCode runtime as usual, and the Claude Code block then starts its own
  install by itself.
- If Ubuntu is missing, the block says so and offers **Open phone setup**.
  There is no second way to install Ubuntu.
- Once something is installed, a card for it appears on the Servers screen and
  in the server switcher, beside the OpenCode server's card, with the same
  controls: Connect or Open, Start, Restart, Stop, and a menu with Disconnect,
  Refresh, Manage setup and Forget saved sign-in.

## What gets installed, and where

Everything lives inside the app-managed Ubuntu except the script's own small
state. Nothing is installed with `apt`, and no remote script is ever piped into
a shell.

| What | Where | Notes |
|---|---|---|
| Node.js (official binary tarball) | `/opt/oc-node` in Ubuntu | Pinned version, SHA-256 verified before it is unpacked. Ubuntu's own Node is too old. |
| Paseo daemon, `@getpaseo/cli@0.8.0` | `/opt/oc-agents` in Ubuntu | Pinned exactly: the app's protocol client was verified against 0.8.0. |
| Claude Code, `@anthropic-ai/claude-code` | `/opt/oc-agents` in Ubuntu | Not pinned; the installed version is recorded and shown. |
| The daemon's home | `/root/.oc-paseo` in Ubuntu | |
| Projects | `/root/projects` in Ubuntu | `my-first-project` is created with `git init` when there are none. |
| Script, pins | `~/.oc/claude.sh`, `~/.oc/claude-pins` in Termux | Rewritten by the app on every call. |
| State, logs, password | `~/.oc/claude/` in Termux | `state`, `config`, `install.log`, `daemon.log` (rotated at 2 MB), `password` (mode 600). |
| Your Claude sign-in and history | `/home/oc/.claude` in Ubuntu | Written by Claude Code itself, never by the app. |

The pins are one Dart constant, `TermuxBridge.localAgentsPins` in
`lib/termux/bridge.dart`. The Node.js checksums are the
`node-v24.21.0-linux-arm64.tar.gz` and `node-v24.21.0-linux-x64.tar.gz` lines
of <https://nodejs.org/dist/v24.21.0/SHASUMS256.txt>. `linux-x64` exists so
the x86_64 Android emulator can run the same flow as an arm64 phone.

### Sizes

The Node.js download is about 58 MB. The install asks for **1.5 GB free**
before it downloads anything and refuses with a plain sentence otherwise. The
installed size in this document's first version was an estimate (about
450 MB); it has not been measured on a device. See the checks owed below.

If npm reports that a native module has to be compiled, the install stops with
that as its reason and shows npm's output. It does not install a compiler
behind your back.

## Loopback only, relay never

- The daemon is started with `--listen 127.0.0.1:6767 --no-relay --no-web-ui
  --no-inject-mcp`. The listen address is a constant in the script; no verb,
  flag or variable widens it, and Paseo's relay and QR pairing are never
  offered.
- The daemon requires a password. The script generates 24 random bytes once,
  keeps them in `~/.oc/claude/password` (mode 600), and hands them to the
  daemon through its environment: the inner shell reads the value from stdin
  and exports it, so it is in no argument list (nothing to see in `ps`), no
  log line and not in the state file.
- The app reads the password with the script's `password` verb and stores it
  only as the saved server's secret, in the platform's secure storage.
- "Running" is never taken from the state file. `status` reports `ready` only
  after the daemon's pid is alive and `http://127.0.0.1:6767/api/health`
  answers. A state that says ready with a dead process reads as installed and
  stopped, with a note that Android stopped it.

## Signing in to Claude

The app never asks for, sees or stores an Anthropic credential.

**Sign in to Claude** opens a Termux terminal you can see and type into, running
Claude Code's own sign-in inside Ubuntu (`claude auth login`, or plain `claude`
on a version without that command). Claude Code shows a link; you approve it in
your browser, paste the code back into Termux, and return to the app. When the
app resumes it re-reads the sign-in state.

`claude setup-token` is deliberately not used. It prints a token meant for an
environment variable and stores nothing, so the daemon's Claude Code would not
find a sign-in afterwards.

The sign-in check looks for a stored sign-in (`/home/oc/.claude/.credentials.json`)
or a configured API key, without reading either out. If it cannot see a sign-in
you know exists, **I already signed in** continues anyway.

Opening a visible terminal needed a small addition to the Android side
(`openTermuxSession`): the bridge's normal path feeds a script on stdin, which
Termux only does for background commands, and waits for a result that a
sign-in may take minutes to produce.

## Connecting

When the daemon is ready, **Connect** asks for a project folder (a folder under
`/root/projects`, or a typed path, always as Ubuntu sees it) and saves exactly
one server: Paseo, `ws://127.0.0.1:6767`, the daemon password, named "Claude
Code on this phone". Connecting again updates that server instead of adding a
second one. The connection itself is the app's normal one.

## Removing it

The block's menu, **Remove from this phone**, stops the daemon and deletes
`/opt/oc-node`, `/opt/oc-agents`, `/root/.oc-paseo` and `~/.oc/claude`.

It keeps your projects and it keeps `/home/oc/.claude`, your Claude sign-in and
history. To remove that too, run this in Termux:

```sh
bash ~/.oc/claude.sh remove --forget-signin
```

## The script, by hand

`~/.oc/claude.sh` runs standalone in Termux:

```sh
bash ~/.oc/claude.sh status          # key=value: phase, versions, pid, signed_in, ...
bash ~/.oc/claude.sh install         # Node.js, Paseo, Claude Code
bash ~/.oc/claude.sh start | stop | restart
bash ~/.oc/claude.sh signin          # Claude Code's own sign-in, in this terminal
bash ~/.oc/claude.sh signin-status
bash ~/.oc/claude.sh remove [--forget-signin]
```

Phases: `absent`, `needs_ubuntu`, `installing`, `installed`, `starting`,
`ready`, `stopping`, `removing`, `failed` (with a `failure_kind` such as
`needs_ubuntu`, `no_space`, `download`, `checksum`, `native_build`,
`port_in_use`, `timeout`). A second install after an interruption is safe: the
download only gets its final name once verified, the unpacked Node.js only
replaces the old one once its own binary answers, and npm installs are
re-runnable.

## Known limits

- **Android may stop Termux in the background**, exactly as with the OpenCode
  server. The same guidance applies: exempt Termux from battery optimisation
  and keep its wake lock. The daemon takes the wake lock when it starts and
  releases it on stop only when neither the OpenCode server nor the AI Team
  still needs it. If Android stops the daemon, the block and the card say so
  and offer Start.
- **Pi is not included.** The daemon can drive it and it would install the same
  way, but it is not installed or offered here.
- The daemon runs as Ubuntu's root user, the same user the app runs OpenCode
  as. Whether Claude Code accepts every permission mode under that user is one
  of the checks owed below; nothing in this build changes Claude Code's own
  safety checks to get around it.
- Files, diffs, git and the terminal are not mapped for the Paseo backend yet
  (see [paseo-connection.md](paseo-connection.md)).
- Remove (the app's button) and `remove` differ from the original brief in one
  way: the verb runs detached like install and start, because deleting tens of
  thousands of small files can outlast the bridge's two-minute result window.

## Verification status

**Run for real on 2026-09-19 in an Android 14 x86_64 emulator** with genuine
Termux 0.118 and the app-managed `opencode-ubuntu` (installed by the app's own
setup). Everything below went through the app's buttons, not by hand:

| Check | Result |
|---|---|
| Install from the block (Node download, SHA-256 verify, Paseo, Claude Code) | passed, about 5 minutes; no compiler needed; `node-pty` loads under proot |
| Installed size | about 1 GB (Node 216 MB, packages 773 MB), so the guard asks for 2 GB free |
| "Sign in to Claude" opens a visible Termux window | passed; Claude Code printed its real sign-in link and waited for the code |
| Start, health-confirmed, loopback only, password required | passed |
| Connect: project chooser, one saved server `ws://127.0.0.1:6767`, lands connected | passed |
| A turn reaches Claude Code | passed up to the account: it answers "Not logged in · Please run /login" |
| Daemon runs as the ordinary user `oc`, not root | passed; as root every turn died because Claude Code refuses its permission-skipping mode under root. The `IS_SANDBOX` switch that turns that guard off is deliberately not used |
| No speech-model downloads on start | passed with `PASEO_DICTATION_ENABLED=false` and `PASEO_VOICE_MODE_ENABLED=false` |
| Both live cards in the Servers screen and the switcher | passed; Stop and Start of the OpenCode card also run against real Termux |

**Still owed, on a physical arm64 phone:** the same install on arm64 (the
packages ship arm64 builds, but it has not been run); the sign-in round trip
with a real account and a real answered turn; ten minutes in the background
with the daemon surviving (Android battery settings apply, as for OpenCode);
Stop ending every process; Remove freeing the space while keeping
`/home/oc/.claude`.
