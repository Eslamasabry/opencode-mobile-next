# OpenCode for Android

A native Flutter client for [opencode](https://opencode.ai) that connects to an
opencode server either **remotely** (your dev box / VPS) or **on-device** — the
app drives Termux itself, so you never manage a terminal by hand. Ships with
**Shorebird code push** so Dart-side fixes reach devices without a Play Store
round-trip.

The app's data handling is documented in the [privacy policy](PRIVACY.md), which
is also bundled under **Settings > Privacy and data use**.

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
- Session todos and a full Review workspace with session, working-tree, and
  branch diff scopes, unified/split modes, line selection, and prompt comments
- Run shell commands and slash commands in the session
- Model & agent picker persisted per server
- Project file browser with adjacent file/symbol search, smart previews, and
  source-line navigation from language-server symbols
- Project health for branch changes, language services, and formatters
- Smart skill previews with rendered Markdown tables, code blocks, and raw mode
- Server-backed project references that can be copied or added to an active prompt

## Requirements

| Tool | Version |
|---|---|
| Flutter | 3.47.1 (the version pinned for Shorebird releases) |
| Android SDK | API 36 |
| Shorebird CLI | 1.6.x |

## Build & run

```bash
flutter pub get
flutter run                # debug on device/emulator
flutter build apk --release
```

## Store releases and Shorebird patches

One-time:

```bash
curl --proto '=https' --tlsv1.2 \
  https://raw.githubusercontent.com/shorebirdtech/install/main/install.sh -sSf | bash
shorebird login     # free account at console.shorebird.dev
shorebird doctor    # expect "No issues detected!"
```

The release helper is fail-closed. It accepts only `release` or `patch`, requires
a clean `master` synchronized with its same-named tracked upstream (normally
`origin/master`), and runs `flutter analyze`, the complete `flutter test` suite,
and a Shorebird dry-run. It uploads nothing unless `--publish` is supplied
explicitly.

Pull requests and pushes to `master` run the pinned Flutter analysis and test
gate in GitHub Actions, plus Android `lintRelease` and a test-signed release APK
compile. The CI key is generated per job and its artifact is never distributed.

`shorebird.yaml` disables the engine's automatic updater because the app already
owns checks, downloads, progress, and resume-time retries through
`ShorebirdUpdateNotice`. Do not enable both owners: `ShorebirdUpdater.update()`
can validly return while the automatic updater is still working, which would let
the UI announce restart readiness before a patch is actually staged.

### Signing identity decision gate

**Do not publish a store release yet.** The current
[`android/app/build.gradle.kts`](android/app/build.gradle.kts) has a dedicated
`release` signing configuration and no debug-key fallback. It reads the ignored
`android/key.properties`; use [`android/key.properties.example`](android/key.properties.example)
as the schema. `./scripts/release.sh release` intentionally stops unless the
complete signing identity and expected certificate are supplied.

Before the first store release, choose and protect a production upload key,
enroll in Play App Signing, move key material and passwords outside version
control, and populate `android/key.properties`. Do not commit a keystore or the
populated properties file; both are ignored under `android/`. Export
`RELEASE_CERT_SHA256` as the expected upload certificate fingerprint before
running the helper. The properties file and keystore must be private to the
current OS user, the keystore must be a regular file outside the repository, and
its alias must already match `RELEASE_CERT_SHA256`. The helper rejects Android
debug certificates, then checks the generated AAB certificate again after its
dry-run and before any upload.

Also decide how to handle existing debug-signed sideload installs: Android
cannot upgrade them in place to an APK signed by a different key, so those users
must uninstall the old build (losing its local app data) before installing the
new production-signed line. This repository does not perform that signing
migration automatically.

### Full Play Store release (AAB)

After the signing blocker is resolved, bump `version:` in `pubspec.yaml` to a
unique `x.y.z+build` value, commit it, and push it so local `master` exactly
matches `origin/master`. Ensure `flutter --version` reports exactly 3.47.1, the
same Flutter version pinned for the Shorebird build. Then validate and publish
as two distinct actions:

```bash
./scripts/release.sh release             # gates + dry-run; uploads nothing
./scripts/release.sh release --publish   # repeats validation, then uploads
```

The store artifact is
`build/app/outputs/bundle/release/app-release.aab`. The helper creates the
Shorebird release but does not upload to Google Play, create a GitHub release,
tag, commit, or push. After verifying the published release, create an immutable
baseline tag containing the exact `pubspec.yaml` version (including build
number), for example `v1.0.12+13`, and push that tag explicitly. Patches fetch
that tag from the tracked remote and require its commit to match the local tag
before using it as the full-release source tree.

### OTA patch

Keep `pubspec.yaml` at the exact full-release version, commit and push the Dart
fix, then run:

```bash
./scripts/release.sh patch             # gates + dry-run; uploads nothing
./scripts/release.sh patch --publish   # repeats validation, then uploads
```

The patch always passes the exact `x.y.z+build` value to Shorebird; it never
targets `latest`. It rejects changes since `v<x.y.z+build>` to Android native
files (including Android code inside local packages), dependencies, Shorebird
configuration, and bundled asset inputs. It also never enables Shorebird's
`--allow-native-diffs` or `--allow-asset-diffs` escape hatches. Use a new full
store release for any rejected change.

`shorebird.yaml` already contains this app's `app_id`. Shorebird patches update
Dart code only; the installed app applies a downloaded patch after restart.

### GitHub sideload APK (separate distribution)

Every installable GitHub APK must first be registered as a full Shorebird
release. Until the production-signing migration is complete, create the next
versioned sideload baseline with the pinned Flutter toolchain:

```bash
shorebird release android --artifact apk --flutter-version 3.47.1
```

Publish the exact APK produced by that command. Never publish an APK from a raw
`flutter build apk`: using Shorebird's Flutter binary alone does not register a
release, so that install cannot receive automatic patches. Tag the published
source with the exact `v<x.y.z+build>` value so `./scripts/release.sh patch`
can verify and target the baseline later.

After production signing is configured, use the store AAB derivation below so
the GitHub channel has an explicit, stable certificate lineage.

The Play Store AAB is not a sideloadable APK, and the release helper never
publishes GitHub assets. Derive a universal APK from the exact published AAB
with Android's `bundletool`, passing the production signing key explicitly.
Password files and the keystore in this example must live outside the
repository:

```bash
release_version='1.0.12+13'
aab='build/app/outputs/bundle/release/app-release.aab'
apks="build/sideload/$release_version/app.apks"
apk="build/sideload/$release_version/opencode-$release_version.apk"

mkdir -p "build/sideload/$release_version"
java -jar "$BUNDLETOOL_JAR" build-apks \
  --bundle="$aab" \
  --output="$apks" \
  --mode=universal \
  --ks="$ANDROID_KEYSTORE_PATH" \
  --ks-pass="file:$ANDROID_KEYSTORE_PASSWORD_FILE" \
  --ks-key-alias="$ANDROID_KEY_ALIAS" \
  --key-pass="file:$ANDROID_KEY_PASSWORD_FILE"
unzip -p "$apks" universal.apk >"$apk"
apksigner verify --verbose --print-certs "$apk"
```

Compare the printed SHA-256 certificate digest with `RELEASE_CERT_SHA256`, then
native-test the universal APK on every supported device mode before attaching
it manually to a GitHub Release for `v$release_version`. Never use an APK set
generated without explicit bundletool signing parameters for distribution;
bundletool otherwise falls back to a debug key. Keep GitHub sideload
publication separate from the Shorebird/Play release so neither path silently
publishes the other.

This command signs the GitHub APK with the **upload-key** certificate represented
by `RELEASE_CERT_SHA256`; that certificate defines the GitHub sideload upgrade
line. With Play App Signing, Google normally signs Play-delivered APKs with the
separate **app-signing** certificate shown in Play Console. A user generally
cannot switch between the Play and GitHub channels in place unless those app
certificates were intentionally made identical; switching requires uninstalling
the existing app and loses its local data. Verify the upload-key fingerprint for
GitHub artifacts and the Play app-signing fingerprint for Play artifacts. The
current signing-identity decision gate applies to both production channels.

## Connecting to opencode

### Remote machine

```bash
OPENCODE_SERVER_PASSWORD=your-secret \
  opencode serve --hostname 127.0.0.1 --port 4096
```

Expose it through an HTTPS reverse proxy or encrypted tunnel, then add the
resulting `https://` URL in-app with user `opencode` and the password above.
Remote HTTP is intentionally blocked. With an SSH tunnel
(`ssh -L 4096:127.0.0.1:4096 host`) and a port-forward app, connect to
`http://127.0.0.1:4096`.

### On-device (Termux) — automated

Tap **On-device (Termux)** on the Servers screen. The app then:

1. Detects whether Termux is installed; if not, opens the F-Droid download page
   (the only manual step — Android forbids apps from installing other apps).
2. Copies an unlock line and jumps into Termux: you paste it once and press
   Enter. This sets `allow-external-apps=true` in `~/.termux/termux.properties`,
   which Termux itself requires before any external app may run commands.
3. Installs **Ubuntu via proot-distro** inside Termux, then Node.js +
   `opencode-ai` in the chroot, and starts
   `opencode serve --hostname 127.0.0.1 --port 4096` detached.
   (Plain-Termux npm installs of opencode are broken upstream — npm sees
   `os=android` and no `opencode-android-arm64` package exists;
   anomalyco/opencode#12515, #10504. The chroot is real glibc and shares the
   network stack, so localhost works from the app.)
4. Health-polls `127.0.0.1:4096` (up to 15 min on first run), saves the
   profile and connects. A "Live log in Termux" button streams install/server
   logs any time.

Native side lives in [`MainActivity.kt`](android/app/src/main/kotlin/ai/opencode/opencode_mobile/MainActivity.kt)
(method channel `oc/termux`) with the Dart wrapper in
[`lib/termux/bridge.dart`](lib/termux/bridge.dart).

Prefer manual? Inside Termux:

```bash
pkg install proot-distro
proot-distro install ubuntu
proot-distro login ubuntu     # then inside the chroot:
  apt update && apt install -y nodejs npm
  npm i -g opencode-ai
  opencode serve --hostname 127.0.0.1 --port 4096 &
  exit
```

The chroot shares the network stack, so the app reaches it at
`http://127.0.0.1:4096`. Run `termux-wake-lock` to keep it alive.

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
