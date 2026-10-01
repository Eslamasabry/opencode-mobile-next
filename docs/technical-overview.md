# Technical overview

The README is written for people who want to use the app. This page keeps
the detail that used to live there: platform state, how connections work,
the two server protocols, building, releasing, and the code layout.

## Platform state

| Platform | State |
|---|---|
| **Android** (phone/tablet) | Primary target. Emulator- and device-verified, 1,200+ widget/transport tests, Shorebird-patched previews. |
| **Linux desktop** (x64) | Alpha. Builds, packages (`.deb` + tarball), CI-gated — the window has now been seen on a display (Xvfb, 1440x900, connected to a live server), but the `.deb` has still not been installed on a real machine. Contributor testing required. |
| **Windows desktop** (x64) | Experimental. Compiled and packaged in CI; routine hands-on testing is still needed. Please file Windows reports. |

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
OPENCODE_SERVER_PASSWORD=<your password> \
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
public addresses and ordinary host names. Plain HTTP is accepted to the
phone's own loopback, and to a private network address (10/8, 172.16/12,
192.168/16, 169.254/16, IPv6 `fc00::/7` and `fe80::/10`, `*.local`) only after
an inline warning that the password and conversation travel unencrypted on
that network and an explicit "Use it anyway", recorded per server as
`oc.cleartextOk.<profileId>` (the confirmed origin; a changed address asks
again, and profile deletion removes it). A pairing code or QR carrying such an
address goes through the same confirm before anything is sent. Codex and
Paseo stay `wss://`-only off this device (Paseo also allows Tailscale), and
quota reads are off for a plain-HTTP server. A client for plain HTTP never
follows redirects. The safest layout still keeps the server on `127.0.0.1`
and brings the connection to the phone through a tunnel that ends at
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
  `http://<hostname>:4096` across a network is rejected by the app (a private
  network address is allowed only after the warning above), because the
  password would cross it in clear text. Binding the server itself to a
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
| Flutter | **3.47.1** (Shorebird's fork) — the exact version pinned for Shorebird releases; CI's quality gate uses upstream 3.47.2 |
| Android SDK | API 37 (`flutter_secure_storage` 11 requires it) |
| Shorebird CLI | 1.6.x (only needed for release/patch work) |

```bash
flutter pub get
flutter run                  # debug on device/emulator
flutter build apk --release
flutter build linux          # desktop build, same codebase
```

The Flutter pin matters: release artifacts and the local test suite use
Shorebird's pinned 3.47.1
(`~/.shorebird/bin/cache/flutter/<rev>/bin/flutter` after installing
Shorebird); `SHOREBIRD_FLUTTER_VERSION` in `scripts/release.sh` and
`android-release.yml` must agree. Older local Flutters may fail to resolve
packages. Run tests serially — `flutter test --concurrency=1`.

Two integration tests run against any live server, no emulator needed:

```bash
opencode serve --port 4123 &
dart run tool/smoke_test.dart http://127.0.0.1:4123 /server/project
dart run tool/prompt_test.dart http://127.0.0.1:4123   # needs model auth
```


## Releases and code push

Every installable release is a Shorebird release, so later Dart-only fixes
can ship as patches. There are two ways to make one:

- **CI tag build** (the normal path).
  [android-release.yml](../.github/workflows/android-release.yml) runs on a
  `v<x.y.z+N>` tag of the current `master`. It runs
  `shorebird release android --build-name x.y.z --build-number N
  --flutter-version 3.47.1 --artifact apk`, with the same version derivation as
  `scripts/release.sh`. It signs with the `RELEASE_*` keystore secrets, checks
  the APK signer, package and version, and stages a draft GitHub release. The
  draft notes record `Shorebird release <version>, Flutter 3.47.1 (Shorebird
  engine)`. Only a tag run uploads to Shorebird. A manual dispatch on a branch
  builds with `--dry-run` and uploads nothing. `./scripts/release.sh github`
  will not publish that draft unless the build run ran on the candidate tag and
  its `Build and upload Shorebird release APK` step succeeded. It also refuses
  a plain `flutter build apk` or a dry-run build.
- **Local sideload**: `./scripts/release.sh sideload --publish`.

Shorebird accepts each version once. Once a tag run's Shorebird step has
succeeded, re-running it fails. If a later step fails, bump the build number in
`pubspec.yaml`, add the matching `docs/releases/v<version>.md`, and tag again.
Dart-only fixes ship as `./scripts/release.sh patch --publish`, run against the
exact released `x.y.z+N`. Changes to native code, assets or
`pubspec.yaml`/`pubspec.lock` need a new release.

**CI token (one-time owner step).** The release job fails closed without the
`SHOREBIRD_TOKEN` repository secret. To create it, the owner runs
`shorebird login:ci` locally, then
`gh secret set SHOREBIRD_TOKEN --repo Eslamasabry/opencode-mobile-next` and
pastes the token at its prompt, which keeps it out of shell history. Never
print the token in logs, paste it into issues or chat, or commit it. To rotate
it, run `shorebird login:ci` again and overwrite the secret.

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

CI runs [android-quality.yml](../.github/workflows/android-quality.yml) on
`master`, `dev`, and pull requests. It includes contract/SDK verification,
analysis, the serial test suite, Android release lint, and a test-signed release
compile. The short-lived APK artifact proves the app compiles, but its isolated
CI certificate is intentionally not the public upgrade lineage and the APK
must not be distributed as a release.


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

### Projects in shared storage

A project under `/sdcard`, `/storage/emulated/N` or `/storage/XXXX-XXXX`
(`lib/domain/shared_storage_path.dart`) is read by the server's host process:
this app for OpenCode inside the app, Termux for the Termux server. Android 11+
shows such a folder to a process without storage access but hides other apps'
non-media files, so it lists only dot-folders such as `.git`. Opening or
creating one therefore goes through `SharedStorageAccessFlow` (explanation,
then Android's "All files access" page, `oc/storage` / `StorageAccess.kt`,
`MANAGE_EXTERNAL_STORAGE`), and `SharedStorageGate` names which host lacks
access (app, or Termux via `ls /storage/emulated/0`). The Files tab shows
"Allow access to files" when such a folder lists only hidden entries. proot
binds all of `/storage` and `/sdcard` until AI Team is on; from then on one
confined launcher covers every built-in process, so only the exact folders the
person opened are bound (own path plus `/sdcard` alias) and allowed by
Landlock (`SharedStorageBinds`, `BuiltinLinux.protectedCommand`). Those folders
are kept per profile as `oc.sharedProjects.<profileId>` (swept on deletion,
`lib/state/shared_project_roots.dart`) and mirrored to a native file so binds
survive a restart. A folder opened for the first time while AI Team is on is
visible after the next server start. Other servers are never gated.

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
