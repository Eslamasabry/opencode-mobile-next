# Technical overview

The README is written for people who want to use the app. This page keeps
the detail that used to live there: platform state, how connections work,
the two server protocols, building, releasing, and the code layout.

## Platform state

| Platform | State |
|---|---|
| **Android** (phone/tablet) | Primary target. Emulator- and device-verified, 1,200+ widget/transport tests, Shorebird-patched previews. |
| **Linux desktop** (x64) | Alpha. Builds, packages (`.deb` + tarball), CI-gated — the window has now been seen on a display (Xvfb, 1440x900, connected to a live server), but the `.deb` has still not been installed on a real machine. Contributor testing required. |
| **Windows desktop** (x64) | Experimental. Runner scaffolded, CI artifact only — **never launched by anyone**. Please file Windows reports. |

The app ships **English only**. The localization layer is wired (`l10n.yaml`,
`lib/l10n/app_en.arb`) but most user-facing strings are still hardcoded in
the widgets. The plan to finish that is in [localization-todo.md](localization-todo.md).

Two independent audits — public-launch readiness and UI/UX — are under
[audits/](audits/) with a [post-remediation status](audits/post-remediation-status-2026-08-29.md).

## Installing and upgrading

Builds are sideload APKs signed with the project's own certificate, not a
Play Store key. Android will warn about unknown sources; that is expected.
Verify a download with `apksigner verify --print-certs app.apk` and compare
the fingerprint and SHA-256 against the release notes.

The Android application ID moved from `ai.opencode.opencode_mobile` to
`io.github.eslamasabry.opencode_mobile` before the 1.0.31 line. The old one
sat under the OpenCode project's own reverse domain, which this project does
not control. To Android that makes it a different app: a build from the new
line installs beside an old preview rather than replacing it. Uninstall the
old one first and expect to pair again; app data does not carry across.

## Connecting to a server, in depth

An OpenCode server runs shell commands as your user. Treat app access to it
like SSH access, and do not put one on a network you do not control.

Start OpenCode on the machine with your code, bound to loopback:

```bash
OPENCODE_SERVER_PASSWORD=your-secret \
  opencode serve --hostname 127.0.0.1 --port 4096
```

**Pair, don't type.** On an OpenCode 2 server, `opencode2 pair` prints the
server's addresses, the username, and the current serve password, together
with a QR encoding all three:

```bash
opencode2 pair
```

In the app's server editor, tap **Scan** and point the camera at that QR
(Android), or copy what it printed and tap **Paste pairing code** (anywhere,
no permissions). The app fills the address, username and password in one
step, tries each address the code carries, and names the one it connected
to. It is strictly less work than copying a 32-byte random password by hand,
and there is nothing to mistype.

The pairing code carries the serve password, so it is as good as shell
access to that machine — treat it accordingly, and note that it goes stale
whenever the server restarts without `OPENCODE_SERVER_PASSWORD` set.
OpenCode 1 servers have no `pair` command, so the manual path below remains
for them, and for anyone who prefers it.

Pairing supplies credentials, not a route. The app refuses plain HTTP to
anything but the phone's own loopback, so the server stays on `127.0.0.1`
and you bring the connection to the phone through a tunnel that ends at
`127.0.0.1` there too:

- **USB / adb (simplest)** — with the phone plugged in and USB debugging on,
  `adb reverse tcp:4096 tcp:4096`, then connect the app to
  `http://127.0.0.1:4096`. Zero network setup; ideal for a desk-adjacent
  phone or emulator.
- **SSH** — any SSH client on the phone that forwards a local port works:
  forward phone-local `4096` to `127.0.0.1:4096` on the host, then connect
  to `http://127.0.0.1:4096`.
- **HTTPS** — Tailscale Serve or another reverse proxy that terminates TLS
  in front of the server; connect to the `https://` address. Plain
  `http://<hostname>:4096` across a network is rejected by the app, because
  the password would cross it in clear text. Binding the server itself to a
  network interface is an advanced path, requires
  `OPENCODE_ALLOW_REMOTE_BIND=1`, and should only be taken behind TLS.
- **On-device (Termux)** — tap **On-device (Termux)** on the Servers
  screen. The app detects Termux (or opens its F-Droid page), walks you
  through the one required unlock line, installs Ubuntu via proot-distro
  plus Node and `opencode-ai` in the chroot, launches
  `opencode serve` detached, health-polls until live, and connects. A "Live
  log in Termux" button streams install/server logs at any time. The native
  side is
  [`MainActivity.kt`](../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt)
  + [`lib/termux/bridge.dart`](../lib/termux/bridge.dart). (Why the chroot:
  plain-Termux npm installs of opencode are broken upstream — npm sees
  `os=android` and no `opencode-android-arm64` package exists. The chroot is
  real glibc and shares the network stack, so the app reaches it at
  `127.0.0.1:4096`.) Prefer manual? Inside Termux:

  ```bash
  pkg install proot-distro
  proot-distro install ubuntu
  proot-distro login ubuntu     # then inside the chroot:
    apt update && apt install -y nodejs npm
    npm i -g opencode-ai
    opencode serve --hostname 127.0.0.1 --port 4096 &
    exit
  ```

  Run `termux-wake-lock` to keep it alive.

For a persistent Linux host, [docs/ubuntu-host.md](ubuntu-host.md) and
[`scripts/host/ubuntu-opencode.sh`](../scripts/host/ubuntu-opencode.sh) install
a `systemd --user` service bound to `127.0.0.1` with a generated password
kept in a `0600` env file, and print the tunnel commands. The app's
**Settings → Ubuntu host management** shows the same commands with your
port filled in. Leave your firewall closed: opening a port does nothing for
you and everything for anyone else on the network.


### Provider sign-ins on OpenCode 1

OpenCode 1 builds its provider runtime once per server instance and caches
it. A sign-in that lands after startup (an OAuth flow finished in the TUI, or
one this app ran) is written to the auth store but not loaded: `/provider`
then lists the provider as connected with the entire models.dev catalog,
while `/config/providers`, the runtime view, still omits it, and every
prompt to one of its models fails with `Model not found: openai/gpt-5.6`
(the "Did you mean" list even names the model you picked). The app compares
the two lists on every catalog load; when a connected provider is missing
from the runtime it asks the server to dispose the instance
(`POST /instance/dispose`, the same call OpenCode's own clients make) and
re-reads, once per connection. The picker then shows only the models the
loaded provider can serve; for a ChatGPT Plus/Pro sign-in that is the Codex
set, and plain `gpt-5.6` is deliberately not in it. If the reload does not
bring the provider up, the picker says so and offers **Reload providers**,
and a "Model not found" error in chat re-reads the catalog on arrival.

### Sending while a run is active

Both server generations accept a prompt mid-turn, so Send stays live next
to Stop. OpenCode 1 creates the user message at once and runs it after the
current turn; the app marks such messages "Queued · runs after this turn"
and the composer says "Sends after this run finishes". OpenCode 2 has an
inbox with two delivery modes, so the composer shows a Steer / Queue toggle
above the field while a turn runs (steer injects at the next step, queue
waits), the caption under it states what Send will do, and pending inbox
items carry inline flip and cancel actions.

## OpenCode 2

The app speaks **both protocols**. The existing v1 client keeps serving
current 1.18.x servers (including Termux installs); a typed `lib/api2/`
client speaks the v2 beta API (`opencode2`) — Basic-auth transport, the
`/api/` surface, cursor pagination, forms, inbox delivery, and WebSocket
PTY — and both sit behind one protocol-neutral gateway, so the screens
never know which server they are talking to.

Protocol detection happens at connect time: paste an address, and a v2
server announces itself (a `401` on `/api/health`) so the app asks for
its serve password. Against a v2 server you get streamed turns, forms
in place of questions, permission approvals with a reason, steer-or-queue
sends (a labelled control, not a hidden long press), a real terminal over
ticketed WebSockets, and native rendering of v2's own message shapes. Every
capability is negotiated, so features a server does not implement are not
offered.

Plan and status: [docs/opencode2-port-plan.md](opencode2-port-plan.md);
captured ground truth in [docs/opencode2-protocol-notes.md](opencode2-protocol-notes.md),
[docs/opencode2-port-matrix.md](opencode2-port-matrix.md), and the live
OpenAPI dumps under [contracts/](../contracts/).


## Build from source

| Tool | Version |
|---|---|
| Flutter | **3.47.2** — the exact version pinned for Shorebird releases |
| Android SDK | API 37 (`flutter_secure_storage` 11 requires it) |
| Shorebird CLI | 1.6.x (only needed for release/patch work) |

```bash
flutter pub get
flutter run                  # debug on device/emulator
flutter build apk --release
flutter build linux          # desktop build, same codebase
```

The Flutter pin matters: release artifacts and the 1,200+ test suite are
validated against Shorebird's pinned 3.47.2
(`~/.shorebird/bin/cache/flutter/<rev>/bin/flutter` after installing
Shorebird). Older local Flutters may fail to resolve packages. Run tests
serially — `flutter test --concurrency=1`.

Two integration tests run against any live server, no emulator needed:

```bash
opencode serve --port 4123 &
dart run tool/smoke_test.dart http://127.0.0.1:4123 /server/project
dart run tool/prompt_test.dart http://127.0.0.1:4123   # needs model auth
```


## Releases and code push

`./scripts/release.sh {release|patch|sideload}` is fail-closed: it demands a
clean synced `master`, runs analysis, the full test suite, and a Shorebird
dry-run, and uploads nothing without an explicit `--publish`. Release
signing reads the gitignored `android/key.properties`
([example](../android/key.properties.example)); no keystore or populated
properties file is ever committed. The GitHub sideload lane hard-pins the
public certificate fingerprint above and independently verifies signer,
package ID, and version on every artifact. Shorebird patches are Dart-only
and always target an exact `x.y.z+build`; never distribute an APK from a raw
`flutter build apk` — it cannot receive patches.

**CI is defined but not currently running.**
[android-quality.yml](../.github/workflows/android-quality.yml) encodes the same
gates plus contract/SDK verification and a test-signed release compile whose
artifact is never distributed, but every recorded run has failed in seconds
on a GitHub account-level billing block. Until that is resolved by the
repository owner, no release here is backed by CI evidence — the gates were
run locally. The workflow file says so at the top, and so does this README,
because a green-looking badge nobody can run is worse than no badge.


## Architecture

```
lib/
├── api/           # v1 client: DTOs, Dio HTTP client, /event SSE w/ reconnect
├── api2/          # OpenCode 2 client: Basic-auth transport, typed models, SSE
├── domain/        # protocol-neutral gateway over v1 and v2
├── state/         # profiles (Keystore secrets), ConnectionController,
│                  #  offline queue, session drafts, review→prompt handoff
├── termux/        # Dart side of the Termux RUN_COMMAND bridge
├── background/    # foreground service, live-session notifications, home widget
├── voice/         # sherpa-onnx Whisper: model manager, downloader, recognizer
├── update/        # Shorebird update ownership
├── diagnostics/   # redacted, explicit-only app diagnostics
└── ui/            # screens (workspace, chat, files, review, activity,
                   #  terminal, more hub, settings…) and widgets
packages/opencode_sdk/   # generated Dart SDK from the checked-in OpenAPI contract
contracts/               # OpenAPI dumps (v1 + v2 beta) + SDK coverage matrix
video/                   # Remotion showcase project
```

UI talks to the gateway, never to `api/` or `api2/` directly. See
[CONTRIBUTING.md](../CONTRIBUTING.md) for the boundaries a change must respect.


## Project docs

- [docs/audits/](audits/) — the two independent audits and the
  [post-remediation status](audits/post-remediation-status-2026-08-29.md)
- [docs/ubuntu-host.md](ubuntu-host.md) — a `systemd --user` server on an
  Ubuntu host ([script](../scripts/host/ubuntu-opencode.sh))
- [docs/opencode2-port-plan.md](opencode2-port-plan.md) /
  [port matrix](opencode2-port-matrix.md) /
  [protocol notes](opencode2-protocol-notes.md) — the v2 lane
- [docs/opencode2-ui-design.md](opencode2-ui-design.md) — locked UI
  decisions for v2 surfaces (forms, integrations, inbox)
- [docs/opencode2-termux.md](opencode2-termux.md) — the on-device story
  under OpenCode 2
- [docs/design-inspiration.md](design-inspiration.md) — the researched
  pattern library behind the facelift
- [docs/desktop-feasibility.md](desktop-feasibility.md) — Linux desktop
  findings. The desktop build compiles and runs; it has no dedicated
  interaction layer yet and should be treated as experimental.
- [docs/opencode-sdk-coverage.md](opencode-sdk-coverage.md) /
  [command feature map](opencode-command-feature-map.md) — how much of
  the server API the app exercises
- [docs/showcase-video-plan.md](showcase-video-plan.md) — the showcase
  storyboard, shot list, and render plan

