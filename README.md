# OpenCode mobile

A native Android client for [OpenCode](https://opencode.ai) — the coding
agent, in your pocket. It connects to an `opencode serve` instance on your
dev box over the network, or runs **fully on-device** with the server inside
Termux, driven and installed by the app itself. The same codebase builds a
working Linux desktop binary.

**Watch the 2.5-minute showcase** (cut from real on-device recordings):

- [Landscape 1080p](https://github.com/Eslamasabry/oc_app/releases/download/v1.0.27%2B28-preview.7/opencode-mobile-showcase-landscape-1080p.mp4)
- [Vertical 1080×1920](https://github.com/Eslamasabry/oc_app/releases/download/v1.0.27%2B28-preview.7/opencode-mobile-showcase-vertical-1080x1920.mp4)
  (for shorts/stories)

Both are attached to release
[v1.0.27+28-preview.7](https://github.com/Eslamasabry/oc_app/releases/tag/v1.0.27%2B28-preview.7);
the Remotion source lives in [`video/`](video/), with the full storyboard in
[docs/showcase-video-plan.md](docs/showcase-video-plan.md).

## Screenshots

| Workspace | Live run | More hub | Session steal |
|---|---|---|---|
| ![Workspace](video/public/shots/still-workspace.png) | ![Live run with context meter](video/public/shots/still-rec3-end.png) | ![More grid](video/public/shots/still-more-grid.png) | ![Continue-here sheet](video/public/shots/still-steal-sheet.png) |

## What it does

- **Chat with live tool streaming** — prompts stream token by token behind a
  terminal-style blinking caret; consecutive tool calls group into a growing
  run with a live ticker (`Shell · flutter test…`); tool cards expand to
  full input/output; markdown, fenced code with syntax highlighting and
  copy, reasoning blocks, image/file previews.
- **Context window meter** — the divider under the composer is an ambient
  gauge of the current model's context window, filling from the newest
  assistant message's token totals, with escalating colors at 70% and 90%.
- **Mission Control** — one glanceable cockpit for the whole fleet: pending
  permissions and questions to resolve, running sessions with live subagent
  counts, recent activity, and a bridge to the all-project session finder.
  Built only on server truth.
- **Model & agent picker** — the full server catalog (349 models on a stock
  install) with search, variant chips, and a pinned apply bar that never
  scrolls away. Selection persists per server.
- **Permissions from the notification shade** — approve a tool call, deny
  it, or answer the agent's question without opening the app; every action
  is bound to its exact request ID. An opt-in foreground service keeps live
  sessions streaming in the background and across device sleep.
- **File browser + diff review** — type-aware browsing with symbol search
  and source-line navigation from the language server; a Review workspace
  with session, working-tree, and branch scopes, unified/split modes, line
  selection, prompt comments, and viewed-file progress counts.
- **A real terminal** — server-side PTY rendered through xterm.dart,
  cursor-safe across app sleep/wake.
- **Theme packs** — Catppuccin, Gruvbox, Solarized, or Android's own
  Material You dynamic color; JetBrains Mono throughout.
- **Offline compose queue** — prompts written on a dead connection queue as
  editable bubbles and flush in order on reconnect (experimental).
- **Home-screen widget** — up to four sessions with working/idle status
  dots; tap a row to land in that exact chat. Updates ride the app's own
  refreshes, so it costs no battery.
- **Session steal & global finder** — search sessions across every project
  on the server (title search, archived included, cursor pagination) and
  continue a workspace session from another device on the phone.
- **Local voice input** — on-device Whisper transcription via sherpa-onnx;
  model packs download on demand, audio never leaves the phone.
- **Shorebird code push** — Dart-side fixes reach installed devices without
  a new APK; the app owns update checks, download progress, and restart.

Server profiles authenticate with HTTP basic auth
(`OPENCODE_SERVER_PASSWORD`); secrets live in the Android Keystore. Data
handling is documented in the [privacy policy](PRIVACY.md), also bundled
under **Settings → Privacy and data use**.

## Install

Grab the APK from the
[latest release](https://github.com/Eslamasabry/oc_app/releases) — currently
the *Operation Facelift* preview line (`oc_app-1.0.27+28-facelift-preview7.apk`);
`v1.0.19+20` is the last stable cut.

**Honest signing caveat:** these APKs are sideload builds signed with the
project's own certificate, not a Play Store production key. Android will
warn about unknown sources — that is expected. The signing lineage is
stable and pinned, so previews upgrade in place: certificate SHA-256
`1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c`. Verify
any downloaded APK with
`apksigner verify --print-certs app.apk` and compare against that
fingerprint and the SHA-256 listed in the release notes.

## Connect to a server

Start OpenCode on the machine with your code:

```bash
OPENCODE_SERVER_PASSWORD=your-secret \
  opencode serve --hostname 127.0.0.1 --port 4096
```

Then pick whichever path fits:

- **USB / adb** — `adb reverse tcp:4096 tcp:4096`, connect to
  `http://127.0.0.1:4096`. Zero network setup; ideal for a desk-adjacent
  phone or emulator.
- **LAN / tunnel** — serve on a reachable interface and connect to
  `192.168.x.x:4096` (the first-run flow normalizes a bare `host:port` and
  live-tests the connection before saving), or expose the server through an
  HTTPS reverse proxy / Tailscale / SSH tunnel. Remote plain-HTTP is
  intentionally blocked; loopback and tunnels are fine.
- **On-device (Termux)** — tap **On-device (Termux)** on the Servers
  screen. The app detects Termux (or opens its F-Droid page), walks you
  through the one required unlock line, installs Ubuntu via proot-distro
  plus Node and `opencode-ai` in the chroot, launches
  `opencode serve` detached, health-polls until live, and connects. A "Live
  log in Termux" button streams install/server logs at any time. The native
  side is
  [`MainActivity.kt`](android/app/src/main/kotlin/ai/opencode/opencode_mobile/MainActivity.kt)
  + [`lib/termux/bridge.dart`](lib/termux/bridge.dart). (Why the chroot:
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

> The server can execute commands on its host; treat app access like SSH
> access. Always set `OPENCODE_SERVER_PASSWORD` when binding beyond
> localhost.

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
sends, a real terminal over ticketed WebSockets, and native rendering of
v2's own message shapes. Every capability is negotiated, so features a
server does not implement are not offered.

Plan and status: [docs/opencode2-port-plan.md](docs/opencode2-port-plan.md);
captured ground truth in [docs/opencode2-protocol-notes.md](docs/opencode2-protocol-notes.md),
[docs/opencode2-port-matrix.md](docs/opencode2-port-matrix.md), and the live
OpenAPI dumps under [contracts/](contracts/).

## Build from source

| Tool | Version |
|---|---|
| Flutter | **3.47.1** — the exact version pinned for Shorebird releases |
| Android SDK | API 36 |
| Shorebird CLI | 1.6.x (only needed for release/patch work) |

```bash
flutter pub get
flutter run                  # debug on device/emulator
flutter build apk --release
flutter build linux          # desktop build, same codebase
```

The Flutter pin matters: release artifacts and the 500+ test suite are
validated against Shorebird's pinned 3.47.1
(`~/.shorebird/bin/cache/flutter/<rev>/bin/flutter` after installing
Shorebird). Older local Flutters may fail to resolve packages.

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
([example](android/key.properties.example)); no keystore or populated
properties file is ever committed. The GitHub sideload lane hard-pins the
public certificate fingerprint above and independently verifies signer,
package ID, and version on every artifact. Shorebird patches are Dart-only
and always target an exact `x.y.z+build`; never distribute an APK from a raw
`flutter build apk` — it cannot receive patches. CI
([android-quality.yml](.github/workflows/android-quality.yml)) runs the same
gates plus contract/SDK verification and a test-signed release compile whose
artifact is never distributed.

## Architecture

```
lib/
├── api/           # v1 client: DTOs, Dio HTTP client, /event SSE w/ reconnect
├── api2/          # OpenCode 2 client: Basic-auth transport, typed models, SSE
├── state/         # profiles (Keystore secrets), ConnectionController, offline queue
├── termux/        # Dart side of the Termux RUN_COMMAND bridge
├── background/    # foreground service + live-session notifications
├── voice/         # sherpa-onnx Whisper: model manager, downloader, recognizer
├── update/        # Shorebird update ownership
├── diagnostics/   # redacted, explicit-only app diagnostics
└── ui/            # screens (chat, workspace, files, review, terminal,
                   #  mission control, settings hub…) and widgets
packages/opencode_sdk/   # generated Dart SDK from the checked-in OpenAPI contract
contracts/               # OpenAPI dumps (v1 + v2 beta) + SDK coverage matrix
video/                   # Remotion showcase project
```

## Project docs

- [docs/showcase-video-plan.md](docs/showcase-video-plan.md) — the showcase
  storyboard, shot list, and render plan
- [docs/opencode2-port-plan.md](docs/opencode2-port-plan.md) /
  [port matrix](docs/opencode2-port-matrix.md) /
  [protocol notes](docs/opencode2-protocol-notes.md) — the v2 lane
- [docs/opencode2-ui-design.md](docs/opencode2-ui-design.md) — locked UI
  decisions for v2 surfaces (forms, integrations, inbox)
- [docs/opencode2-termux.md](docs/opencode2-termux.md) — the on-device story
  under OpenCode 2
- [docs/design-inspiration.md](docs/design-inspiration.md) — the researched
  pattern library behind the facelift
- [docs/desktop-feasibility.md](docs/desktop-feasibility.md) — Linux desktop
  findings
- [docs/ubuntu-host.md](docs/ubuntu-host.md) — one-command server setup for
  an Ubuntu host ([script](scripts/host/ubuntu-opencode.sh))
- [docs/opencode-sdk-coverage.md](docs/opencode-sdk-coverage.md) /
  [command feature map](docs/opencode-command-feature-map.md) — how much of
  the server API the app exercises
- [CONTRIBUTING.md](CONTRIBUTING.md) — toolchain pin, the gates a change must
  pass, architecture boundaries, and PR expectations
- [docs/internal/handoff.md](docs/internal/handoff.md) — the append-only
  engineering working log. Archaeology, not documentation: it carries stale
  release claims and one machine's local details, and nothing in it is
  authoritative.

## License

[MIT](LICENSE). Bundled
third-party components — JetBrains Mono (OFL-1.1), sherpa-onnx
(Apache-2.0), ONNX Runtime (MIT), OpenAI Whisper models (MIT), and others —
are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) with full
texts under [LICENSES/](LICENSES/).
