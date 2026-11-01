# App updates: two layers (owner design, 2026-10-31)

Finish line: a person on 2.2.0 learns about 2.3.0 inside the app, installs it with one confirmation, and their phone server's OpenCode, Paseo, Claude Code and Node move to the versions the new app pins, without anyone visiting GitHub.

Non-goals: silent APK installs (Android always asks), auto-following untested "latest" versions, updating a server the app does not manage (the person's own computer).

## Layer 1: the app itself

Owner's words: "the user should not go to GitHub every time to update. He should know always on the phone itself… show a notification inside the app that there is an update. He can dismiss it, but he can always access it also from the settings button… the settings button will show some icon or update overlay… when you click on settings while there is an update, you will always be asked: do you want to update now or go to the settings?"

### Sources
1. **New release (APK)**, from GitHub `Eslamasabry/opencode-mobile-next`. `GET https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases/latest` returns only published, non-draft, non-prerelease releases. The tag is `v<x.y.z>+<build>`.
   - An update exists when the tag's build number is greater than the installed build number (`PackageInfo.buildNumber`).
   - Check on app start and on resume, at most once every 6 hours. No polling while backgrounded.
   - It's on by default, with a switch in the Updates page: "Check GitHub for new versions". The privacy line says the app asks GitHub for the latest version number and sends nothing about the person.
2. **Code-push patch** (Shorebird). This already exists: lib/update/shorebird_update_notice.dart downloads silently and then shows "App update ready / takes effect when you fully close the app". It joins the same model as state `restartToUpdate`.
   - If a new release exists too, the release wins, because the patch is for the older version.

### Getting the right file
- Pick the asset by the device's supported ABIs. If the device lists arm64-v8a, use `opencode-mobile-<v>-arm64-v8a.apk`; else armeabi-v7a; else the universal `opencode-mobile-<v>.apk`. Fall back to universal if the ABI asset is missing.
- Download to the app's cache with progress, resumable via Range. Cancel is allowed. Use Wi-Fi or mobile data as Android allows; show the size before starting ("Download 40 MB").
- **Verify before installing**, and refuse with a plain reason on any mismatch:
  - sha256 equals the line in the release's `SHA256SUMS` asset;
  - the APK's signing certificate SHA-256 equals the installed app's own signer. Read both with PackageManager (`getPackageArchiveInfo` with GET_SIGNING_CERTIFICATES, versus our own package);
  - the package name equals ours;
  - the versionCode is greater than ours.
  - A mismatch never installs. The message is "This download isn't signed like your app, so it wasn't installed", with the technical detail under Details.

### Installing (native, Kotlin; channel `oc/app_update`, both halves single-owner)
- `PackageInstaller` session with `REQUEST_INSTALL_PACKAGES` in the manifest.
- If the app may not install apps (`canRequestPackageInstalls()` is false), explain in one line and open `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for our package. When the person comes back, continue automatically.
- Android shows its own confirm dialog, and that is expected. The result comes back through the session's status intent: success means the app restarts as the new version; failure or abort returns to "Update ready".
- **Channel contract (freeze before building):**
  - `canInstall() -> bool`
  - `openInstallPermission()`
  - `inspectApk(path) -> {packageName, versionCode, signerSha256}`
  - `ownSignerSha256() -> String`
  - `install(path) -> stream/callback {state: pendingUser|success|failure|aborted, message?}`
  - `supportedAbis() -> List<String>`

### UI (kit only; no new bar or row level)
- **In-app notice.** When an update is found, show it once in the app's existing status line (same slot as the patch notice): "Version 2.2.0 is ready", with the actions Update and Not now.
  - Not now hides it for that version until the next app start. It does not hide the gear badge.
- **Settings gear badge.** While an update is available and not installed, the gear on the Conversations home (and the rail on wide screens) carries a small dot. Its TalkBack label is "Settings, update available".
- **Gear tap while an update is pending.** Open a small sheet. Title "Version 2.2.0 is ready"; one plain line from the release (first sentence of the notes); a "What's new" link; buttons **Update now** (primary) and **Open Settings**. Back closes the sheet.
- **Settings › This app › Updates page** (new; also findable by settings search "update"):
  - The current version, then the state: "Up to date · checked 5 min ago", "2.2.0 is ready · 40 MB" with an Update button, a progress bar, "Installing…", or a failure with a way forward.
  - What's new: the release notes as Markdown, with links through `openExternalLink`.
  - The check switch, and "Check now".
- **Teaching:** none needed beyond the badge; the sheet itself explains.
- **Motion:** badge fade-in 150 ms, sheet with the kit's standard sheet motion; respect reduced motion.
- Strings go in app_en.arb with Arabic in app_ar.arb; other locales fall back.

### State model (one controller, Riverpod; tests drive it with fakes)
`AppUpdateState`: `none | checking | available(release) | downloading(progress) | verifying | readyToInstall | installing | failed(reason) | restartToUpdate (patch)`. It's held app-wide; the notice, badge, sheet and page all read it.

## Layer 2: what the app runs inside (after the app updates)

Each app version pins tested versions of OpenCode, Paseo, Node and Claude Code (lib/termux/bridge.dart, lib/termux/scripts/local_agents_script.dart, lib/paseo/tested_versions.dart). After the app updates, every host the app manages moves to the new pins by itself, using the rules of the existing `InAppAutoUpdater` (lib/state/in_app_auto_update.dart):
- only when nothing is running
- one try per pin per app run
- keep the old version until the new one starts, roll back on failure
- show progress and the result on This phone

Hosts:
- **built-in Linux on this phone:** fully automatic; extend from OpenCode only to Paseo, Node and Claude Code
- **Termux:** the bridge scripts already install pinned versions, so the same updater goes through the bridge
- **BYO VPS over SSH** (lib/host/byo_host_ssh_runner.dart): automatic when the person turned on "keep it updated" for that host; otherwise "Update to 2.2.5" on that host's page
- **the person's own computer:** the app can't touch it. That server's page says "This server runs OpenCode 2.1.1; this app is tested with 2.2.5", with a copyable update command

The component list and pins come from the upgrade-scope report (/home/eslam/Storage2/wt/upgrade-scope-2026-10-31/REPORT.md). Layer 2 is built after that report.

## Frozen native channel contract: `oc/app_update` (Layer 1)

Dart calls Kotlin (`MethodChannel('oc/app_update')`). Arguments are maps; results are plain values.

| Method | Arguments | Result |
|---|---|---|
| `canInstall` | none | `bool` (`PackageManager.canRequestPackageInstalls()`) |
| `openInstallPermission` | none | `null`; opens `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for our package |
| `inspectApk` | `{path: String}` | `{packageName: String, versionCode: int, signerSha256: String}` (uppercase hex, no colons; several signers are sorted and joined with `,`). Error code `bad_apk` when Android cannot read the file |
| `ownSignerSha256` | none | `String`, same format |
| `supportedAbis` | none | `List<String>` (`Build.SUPPORTED_ABIS`, preferred first) |
| `install` | `{path: String}` | `null` once the PackageInstaller session is committed; progress comes back as events |

Kotlin calls Dart on the same channel: method `installState`, argument `{state: "pendingUser"|"success"|"failure"|"aborted", message?: String}`.
`pendingUser` means Android is showing its own confirm dialog. `success` normally never arrives (the app is replaced and restarts as the new version). `aborted` is the person cancelling; `failure` carries Android's status message (technical, shown only under Details).

Dart wraps it as `Stream<AppInstallEvent> install(path)`.

## Layer 1 decisions made while building

- Phase names: `none, checking, available, downloading, verifying, readyToInstall, installing, failed, restartToUpdate` (`AppUpdatePhase`; the older `AppUpdateState` enum of the patch service keeps its name).
- Last check time, the check switch and the last found release are stored app-wide (`oc.appUpdate.*`; not per profile).
- Debug/proof override: `--dart-define=OC_APP_UPDATE_FEED=<url>` points the release check at a JSON with the same shape as one GitHub release. Empty by default, never passed by any release script; a test pins both.
- The gear badge shows for a release only; a ready code-push patch keeps its status-line notice and has no badge.
