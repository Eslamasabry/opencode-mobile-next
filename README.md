# OpenCode for Android

A native Flutter client for [opencode](https://opencode.ai) that connects to an
opencode server either **remotely** (your dev box / VPS) or **on-device** — the
app drives Termux itself, so you never manage a terminal by hand. Ships with
**Shorebird code push** so Dart-side fixes reach devices without a Play Store
round-trip.

## Features

- Server profiles with basic-auth (`OPENCODE_SERVER_PASSWORD`), stored in the Android Keystore
- **Guided on-device setup**: detects Termux, unlocks the bridge, installs
  opencode (node + npm), launches `opencode serve`, health-polls until live and
  connects — automatically
- Live SSE event stream (`/event`) with reconnect + backoff, polling fallback
- Chats: create/rename/delete sessions, streaming responses, optimistic send, abort
- Rich rendering: markdown, fenced code blocks w/ copy, reasoning blocks,
  tool-call cards with expandable input/output
- Permission requests surfaced as dialogs (allow once / always / reject)
- Session todos panel, file-change diff viewer (+/- counts per file)
- Run shell commands and slash commands in the session
- Model & agent picker persisted per server
- Project file browser with fuzzy name search + content viewer

## Requirements

| Tool | Version |
|---|---|
| Flutter | 3.38+ (built with 3.38.5) |
| Android SDK | API 35 |
| Shorebird CLI | 1.6.x |

## Build & run

```bash
flutter pub get
flutter run                # debug on device/emulator
flutter build apk --release
```

## Ship it with Shorebird

One-time:

```bash
curl --proto '=https' --tlsv1.2 \
  https://raw.githubusercontent.com/shorebirdtech/install/main/install.sh -sSf | bash
shorebird login     # free account at console.shorebird.dev
shorebird doctor    # expect "No issues detected!"
```

Every release / OTA patch:

```bash
./scripts/release.sh          # shorebird release android -> store-ready APK
./scripts/release.sh patch    # Dart-only change? push it over the air
```

`shorebird.yaml` already contains this app's `app_id`. Note Shorebird patches
**Dart only** — changes to `android/` need a new full release.

## Connecting to opencode

### Remote machine

```bash
OPENCODE_SERVER_PASSWORD=your-secret \
  opencode serve --hostname 0.0.0.0 --port 4096
```

Add a server in-app: `http://<machine-ip>:4096`, user `opencode`,
password as above. For anything beyond trusted LANs use Tailscale or an SSH
tunnel (`ssh -L 4096:127.0.0.1:4096 host`) and connect to localhost.

### On-device (Termux) — automated

Tap **On-device (Termux)** on the Servers screen. The app then:

1. Detects whether Termux is installed; if not, opens the F-Droid download page
   (the only manual step — Android forbids apps from installing other apps).
2. Copies an unlock line and jumps into Termux: you paste it once and press
   Enter. This sets `allow-external-apps=true` in `~/.termux/termux.properties`,
   which Termux itself requires before any external app may run commands.
3. Installs node + opencode inside Termux via Termux's RUN_COMMAND service.
4. Starts `opencode serve --hostname 127.0.0.1 --port 4096`, health-polls
   `127.0.0.1:4096` for up to 5 minutes, saves the profile and connects.

Native side lives in [`MainActivity.kt`](android/app/src/main/kotlin/ai/opencode/opencode_mobile/MainActivity.kt)
(method channel `oc/termux`) with the Dart wrapper in
[`lib/termux/bridge.dart`](lib/termux/bridge.dart).

Prefer manual? Inside Termux:

```bash
pkg update && pkg install nodejs-lts git
npm i -g opencode-ai
opencode serve --hostname 127.0.0.1 --port 4096
```

Then add a server pointing at `http://127.0.0.1:4096`.

> The server can execute commands on its host; treat app access like SSH access.

## Verifying against a real server

The repo ships two integration tests that run against any live opencode
instance (no emulator needed):

```bash
opencode serve --port 4123 &
dart run tool/smoke_test.dart http://127.0.0.1:4123    # API surface + SSE
dart run tool/prompt_test.dart http://127.0.0.1:4123   # full streamed prompt (needs model auth)
```

Verified passing against opencode 1.18.21.

## Architecture

```
lib/
├── api/
│   ├── models.dart        # hand-written DTO parsers for the opencode HTTP API
│   ├── opencode_api.dart  # Dio client: sessions, messages, prompts, files…
│   └── sse.dart           # /event stream parser with reconnect + backoff
├── state/
│   ├── profiles.dart      # server profiles (prefs + Keystore secrets)
│   ├── connection.dart    # ConnectionController: connection, events, catalogs
│   └── termux/
│       └── bridge.dart    # Dart wrapper for the Termux RUN_COMMAND channel
├── ui/
│   ├── screens/           # servers, home tabs, chat, files, guide, termux setup
│   └── widgets/           # markdown renderer, tool cards, pickers
└── main.dart              # bootstrap, routes, theme
```
