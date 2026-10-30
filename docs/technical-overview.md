# Technical overview

The README is written for people who want to use the app. This page keeps
the detail that used to live there: platform state, how connections work,
the supported server backends, building, releasing, and the code layout.

## Platform state

| Platform | State |
|---|---|
| **Android** (phone/tablet) | Primary target. Emulator- and device-verified, 1,200+ widget/transport tests, Shorebird-patched previews. |
| **Linux desktop** (x64) | Alpha. Builds, packages (`.deb` + tarball), CI-gated — the window has now been seen on a display (Xvfb, 1440x900, connected to a live server), but the `.deb` has still not been installed on a real machine. Contributor testing required. |
| **Windows desktop** (x64) | Experimental. Compiled and packaged in CI; routine hands-on testing is still needed. Please file Windows reports. |

The app ships **English and Arabic** (`lib/l10n/app_en.arb`, `app_ar.arb`, a
language picker in Settings); what is still English-only is listed in
[localization-todo.md](localization-todo.md).

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
opencode2 serve --hostname 127.0.0.1 --port 4096
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
whenever the server restarts without `OPENCODE_PASSWORD` set. Manual address
and password entry remains available for OpenCode 2. OpenCode 1 servers are
recognized only to show an upgrade instruction; the app cannot connect to them.

Pairing supplies credentials, not a route or transport consent. OpenCode 2
accepts an explicitly entered HTTP origin on a LAN, tailnet or public host,
but every non-loopback origin needs a warning and an explicit per-server
choice before any probe or Basic credentials are sent. The warning says the
password and code travel unencrypted; private ranges and Tailscale get softer
wording, while public hosts get the public-network warning. Consent is stored
as the exact origin in `oc.cleartextOk.<profileId>`; changing the address asks
again, and profile deletion removes it. Pairing and QR addresses follow the
same gate. Bare remote addresses still default to HTTPS; no failure silently
downgrades a connection. Codex, Paseo and AI Team orchestration keep their
existing transport policies, and quota reads remain off for plain HTTP.

For self-signed HTTPS, the person inspects the SHA-256 certificate fingerprint
and explicitly pins it for that profile. Inspection performs a TLS handshake
without sending HTTP or credentials. `oc.certificatePin.<profileId>` stores the
fingerprint and exact HTTPS origin separately from profile metadata, survives
restart and is removed on profile deletion or an origin change. The transport
checks the pinned leaf certificate and its validity dates during TLS, before
HTTP/authentication; a changed certificate blocks connection and offers a
re-check, never automatic acceptance. Unpinned HTTPS retains normal PKI
validation. OpenCode 2 requests never follow redirects, so neither credentials
nor trust cross origins through a redirect. A tunnel can also keep both ends
on loopback: bind the server to `127.0.0.1` and bring the connection to
`127.0.0.1` on the phone:

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
  plus Node and `@opencode/cli@2.1.1` in the chroot, launches
  `opencode2 serve` detached, health-polls until live, and connects. A "Live
  log in Termux" button streams install/server logs at any time. The native
  side is
  [`MainActivity.kt`](../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt)
  + [`lib/termux/bridge.dart`](../lib/termux/bridge.dart). (Why the chroot:
  the managed installer uses the Linux binaries inside Ubuntu, whose glibc
  environment shares the phone’s network stack. The app reaches it at
  `127.0.0.1:4096`.) Prefer manual? Inside Termux:

  ```bash
  pkg install proot-distro
  proot-distro install ubuntu
  proot-distro login ubuntu     # then inside the chroot:
    apt update && apt install -y nodejs npm
    npm i -g @opencode/cli@2.1.1
    opencode2 serve --hostname 127.0.0.1 --port 4096
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


### Provider sign-ins

OpenCode 2 exposes providers and models separately from integration credentials.
The app lists integrations, starts an integration OAuth attempt, polls its state,
and supports completion or cancellation through `/api/integration/...`.
API-key and command-based integrations use their corresponding endpoints.
Credentials stay on the server; catalog and diagnostic output must never include
provider keys. The removed OpenCode 1 provider-runtime refresh and
`/config/providers` fallback are not used.

### Sending while a run is active

OpenCode 2 supports steering a running turn and reports pending work through
its inbox. Where the connected gateway advertises steering, Send adds input
at the next supported boundary; the composer does not offer a queue choice.
Direct Codex steering is bound to the expected active turn. Other agent
backends report their own supported send behavior through the domain gateway;
uncertain delivery must not silently retry or resend a mutation.

## OpenCode 2 and supported backends

OpenCode 2 is the only supported OpenCode protocol. The handwritten
`lib/api2/` client handles HTTP Basic authentication, `/api` routes, typed
messages, volatile SSE events, cursor pagination, forms, inbox delivery and
single-use terminal connection tickets. Stable 2.1.1 uses `/api/info` for
identity; the earlier beta uses `/api/health`. Reconnects reconcile state by
refetching because the global event stream does not replay missed events.

Codex direct and Paseo remain separate gateway implementations. Shared models
and operations live under `lib/domain/`; screens gate actions through
`ServerCapabilities`, rather than selecting a protocol flavor. Unsupported
OpenCode 2 features stay unavailable. Revert stage/clear/commit, integration
sign-in, git initialization, worktree creation and shell settings are supported
by the existing OpenCode 2 adapters.

A remote OpenCode 1 response produces the plain explanation that support has
ended and a copyable `npm uninstall -g opencode-ai && npm install -g @opencode/cli@2.1.1` upgrade command.
Managed phone installations instead enter the automatic history-migration
flow. It preserves the saved profile ID and original database, copies history
before the first OpenCode 2 start, and requires server-side import confirmation
before clearing the pending migration marker. Unsafe or ambiguous copies stop
with a recovery message; separate existing histories are not overwritten.
Legacy runtime identifiers remain only for detection and migration, never as
an installation or start option.

The decommission decision and outstanding validation are recorded in
[the removal plan](design/oc1-decommission-plan-2026-10-14.md). Wire details and
version-specific limits are in [protocol notes](opencode2-protocol-notes.md),
[the port matrix](opencode2-port-matrix.md) and [capability gaps](opencode2-capability-gaps-ux.md).
The retained [contracts](../contracts/) describe OpenCode 2 and Gas City;
there is no generated OpenCode 1 SDK or generator.


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

Optional integration tools target an explicitly selected scratch OpenCode 2
server, with no emulator. Start it in a separate terminal on an unused port:

```bash
opencode2 serve --port 4097 --hostname 127.0.0.1
```

Supply its password through `OC2_LIVE_PASSWORD` in the test process environment,
never as a command-line argument or in committed output. Then run:

```bash
dart run tool/smoke_test.dart http://127.0.0.1:4097 /scratch/project
dart run tool/prompt_test.dart http://127.0.0.1:4097   # needs model auth
```

Stop only the scratch process you started; never target an owner server.


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

**Release notes have two layers** (`docs/releases/TEMPLATE.md`): "What changed
for you", two to five plain-word bullets a user can act on, above "Technical
changes", the full list. `python3 tool/release/release_notes.py new x.y.z+N`
drafts the file and the technical list from the commits since the last tag;
`check` validates it (`scripts/release.sh release|sideload` runs it first);
`store --write` saves the top layer as the fastlane changelog
`fastlane/metadata/android/en-US/changelogs/N.txt` (write the Arabic one in
`ar/` by hand).

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
`master`, `dev`, and pull requests. It includes
analysis, the serial test suite, Android release lint, and a test-signed release
compile. The short-lived APK artifact proves the app compiles, but its isolated
CI certificate is intentionally not the public upgrade lineage and the APK
must not be distributed as a release.


## Architecture

```
lib/
├── api2/          # OpenCode 2 client: Basic-auth transport, typed models, SSE
├── domain/        # shared models, capabilities and backend-neutral gateways
├── codex/         # direct Codex app-server adapter
├── paseo/         # Paseo agent adapter
├── state/         # profiles (Keystore secrets), ConnectionController,
│                  #  offline queue, session drafts, review→prompt handoff
├── termux/        # Dart side of the Termux RUN_COMMAND bridge
├── background/    # foreground service, live-session notifications, home widget
├── voice/         # sherpa-onnx Whisper: model manager, downloader, recognizer
├── update/        # Shorebird update ownership
├── diagnostics/   # redacted, explicit-only app diagnostics
└── ui/            # screens (workspace, chat, files, review, activity,
                   #  terminal, more hub, settings…) and widgets
contracts/               # retained OpenCode 2 and Gas City OpenAPI snapshots
tool/demo-video/         # Remotion showcase project
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

UI talks to domain gateways, never directly to `api2/`, `codex/` or `paseo/`. See
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
- [OpenCode 2 capability gaps](opencode2-capability-gaps-ux.md) — supported
  operations and intentionally unavailable features
- [docs/showcase-video-plan.md](showcase-video-plan.md) — the showcase
  storyboard, shot list, and render plan
