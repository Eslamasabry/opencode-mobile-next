# desktop-web-cross: desktop and web paths, accessibility, security invariants, privacy, diagnostics, crash handling, analytics

Area map for the native rewrite (Android first, KMP-ready). Written 2026-11-01 from
`/home/eslam/Storage/Code/wt/oc_app-batch1` (branch `int/batch1-2026-11-01`, HEAD `65e3f70a3`,
app version 2.1.0+57). Read-only study; nothing in the repo was changed.

Scope covered here: the Linux and Windows desktop runners and packaging, the iOS scaffold, the web
shell (which is NOT in this tree, see DESKTOP-WEB-CROSS-24), the desktop interaction layer
(`lib/ui/desktop`, `lib/desktop`), the `PlatformCapabilities` seam, accessibility, the security
invariants (external links, per-profile keys, credential redaction, WebView/LAN-server hardening,
manifest posture), privacy (what leaves the phone, consents, policy documents), diagnostics and
logging (`lib/diagnostics`, `lib/feedback`), crash handling (Dart and Kotlin), and analytics (there
is none; a plan exists).

Feature ids are `DESKTOP-WEB-CROSS-NN`. One id is one user-visible feature or one important internal
capability a rewrite must reproduce or consciously drop. Every id ends with a **Rewrite stance**
(`PORT` = must exist in the Android rewrite; `PORT-LATER` = needed for the iOS / Linux-desktop
follow-up, design for it now; `REBUILD` = keep the behaviour, change the mechanism; `DROP`; `DECIDE`
= owner decision needed).

Sibling maps own the neighbouring ground and are cited, not repeated: `screens-nav.md` (shell,
navigation, shortcut table, command launcher UI), `state-data.md` (every preference key, deletion
sweep, secure-storage keys), `protocols.md` (server address rules, transport trust, error words),
`builtin-linux.md` (lifecycle, thermal, foreground services, process safety), `ui-kit.md` (kit parts,
motion, text-scale tokens), `agents-backends.md` (Paseo, ACP, sign-in). Where this file repeats a
fact from them it is because the fact is a cross-cutting invariant.

## 0. What to trust

- **Code is the truth; some docs are stale.** Known drift (verified against code on 2026-11-01):
  - `PRIVACY.md` (bundled in-app, effective 2026-09-06) says App diagnostics are "in process memory
    only", maximum 20 entries, and that **Send** posts them to the OpenCode server. The code now
    persists a redacted problem report to disk (128 events / 256 KiB, `diagnostics/report_problem.json`)
    **with no consent switch**, offers an opt-in crash-category store, and sends nothing to the
    server: "Report a problem" builds a prefilled GitHub issue link or Copy/Share text. The policy
    text must be rewritten before the native app ships (DESKTOP-WEB-CROSS-49).
  - `SECURITY.md` says the installed signing fingerprint is shown in Settings > About; no code reads it
    (grep for `signingInfo`/`apkContentsSigners` finds nothing).
  - `docs/desktop.md` says the desktop update notice is "one snackbar with a View action"; the code
    uses the app's one status line (`KitStatus` kind `update`). Flutter version text varies
    (AGENTS.md 3.47.1, CI and docs 3.47.2).
  - `docs/security/threat-model.md` (2026-10-02) still lists "No server identity pinning" as open gap 6 in
    its summary while its scenario 3 already describes the shipped pin (`oc.certificatePin.<id>`); scenario 1
    says there is no `local_auth` dependency, but `local_auth` 3.0.2 is now in `pubspec.yaml` (used only by
    FactsBook's device authenticator). Treat the scenarios as current and the summary as stale.
  - `docs/aptabase-telemetry-plan.md` (2026-08-31) is a plan; no `lib/telemetry` exists.
- **The web shell is not merged.** Four branches (`feat/web-shell-20260913`, `feat/web-storage-20260913`,
  `feat/web-transports-20260913`, `fix/web-preview-20260913`) exist, all diverged from a base that
  predates ~900 files of later work. In the current tree web exists only as an `isWeb` flag in
  `PlatformCapabilities` and the string `'Web (unsupported)'` in the bug-report platform label.
- **Desktop is experimental by the project's own words.** Linux builds, packages, installs and launches
  under Xvfb in CI; it has had little hands-on use. Windows compiles and zips in CI and has never been
  run on hardware by a maintainer. There is no macOS target (the `macOS` branches in code are
  defensive). iOS is a source scaffold that has never been built on a Mac.
- Test and rule ids quoted below (`A11Y-8`, `G2`, `STATE-5`, `LOOK-21`, `SEC-1`, ...) come from
  `docs/ux-system/revamp/STANDARDS.md` and code comments. Test names are Flutter test files; the owner
  said the 7,967 widget tests come later and the 6,663 logic tests are catalogued in
  `/home/eslam/Storage/Code/wt/native-plan/catalog` (areas `security-privacy` 188 rows and
  `desktop-web-cross` 16 rows are the ones for this file; section 10).

## 1. Ten things a rewrite must know first

1. **There is no analytics, telemetry, ad SDK or crash-upload service.** Nothing leaves the phone to the
   developer unless the person taps an explicit action (open a prefilled GitHub issue, Copy, Share).
   Every diagnostic store is local, bounded, redacted before truncation, and erasable. An opt-in Aptabase
   plan exists on paper only. Keep the property: no network library that phones home may be linked.
2. **One capability seam hides every non-Android difference.** `PlatformCapabilities` has 16 named
   `supports*` flags plus `isAndroid/isDesktop/isWeb`; the Android-only features (Termux, voice,
   read-aloud, notifications, widgets, shortcuts, tile, code push, QR, photos, PDF) answer false on
   desktop/iOS/web and the UI hides them (never a dead button). It is overridable in tests. The
   native rewrite should keep this exact shape as an `expect/actual` object in `commonMain`.
3. **`openExternalLink` is the only door from "a URL the app did not author" to the OS.** https opens
   after a host-naming confirm; http only after a separate insecure confirm; every other scheme,
   userinfo, hostless and over-2048-char URLs are refused. There is exactly one `launchUrl` call in
   `lib/`. A repo gate (`G2`) keeps it that way. Port the policy and its test vectors, not the dialog.
4. **Credential redaction is a registry plus patterns, and it exists in seven near-duplicate forms.**
   `KitRedact` (static registry of exact secrets >= 6 chars + patterns) is the core; diagnostics,
   perf trace, problem report, notification text, failed-job report and pairing printing each add their
   own scrubber. The rewrite should have ONE redaction module with ONE test-vector suite (section 3,
   DESKTOP-WEB-CROSS-36/37) and a rule that every secret is registered at ingress before it can be
   captured.
5. **Per-profile storage keys must be named `oc.<what>.<profileId>`** so the deletion sweep finds them
   (`endsWith('.<id>')` or `contains('.<id>.')`). App-wide drafts use the pseudo-profile `app`. This is
   the privacy-delete contract; new stores in the native app must follow it or declare themselves.
6. **Diagnostics are bounded, atomic, fail-closed and layered.** In-memory ring (20) -> persisted problem
   report (128 events, 256 KiB, atomic rename, timings evicted before errors) -> opt-in crash categories
   (20 records / 8 KiB, fixed vocabularies, consent epoch) -> explicit preview-then-share text (<= 20
   records / 16 KiB). The native JVM handler stores category-only data (no message, no stack, no class
   beyond a fixed allowlist). A rewrite that "improves" this with real stack traces breaks a deliberate
   privacy decision.
7. **Compose changes the crash model.** Flutter replaces a failing widget with `KitErrorFallback` and
   keeps running; an exception in Compose composition/layout kills the process. The native app needs
   crash-loop-safe startup, a last-crash notice on next launch (the data path exists in
   `NativeCrashStore` / `oc/lifecycle launchReport`), and defensive screens (DESKTOP-WEB-CROSS-52).
8. **The desktop layer is additive and its Android twin matters.** Hardware-keyboard shortcuts in chat
   (Esc, F2, F3, F4, Ctrl+B, PgUp/PgDn, Home/End, Ctrl+Enter) work on Android too (tablets, keyboards,
   ChromeOS-like use) and `finePointer` (a connected mouse) turns on hover/tooltips/Enter-to-send on
   Android. Do not scope these to "desktop only".
9. **Accessibility is a contract enforced by tests, not a feature.** Always-visible nav labels,
   labelled 44-48 dp targets, status announced once by a live region, no gesture without a visible
   twin, text at 2x without clipping, critical flows at 2.5x on 360x740, reduced-motion respected,
   Arabic RTL with bidi isolates for technical values. The tests (`accessibility_guidelines_test`,
   `text_scale_overflow_*`, `gesture_audit_test`, ...) are the spec; their Compose equivalents must
   exist before screens are ported.
10. **Update channels are partitioned by capability.** Android = Shorebird patches (`supportsCodePush`,
    ends at the native switch); Linux/Windows = GitHub-release tag check that only opens a page
    (`supportsDesktopReleaseCheck`); everything else = `UnavailableAppUpdateService`. The in-app update
    notice (one status line, lowest priority) stays in the native app (owner decision). Android native
    will need its own update story (Play / sideload APK check) designed with the owner.

## 2. Feature index

| Id | Feature | Stance |
|---|---|---|
| 01 | `PlatformCapabilities` seam | PORT |
| 02 | Linux GTK runner (app id, title, header bar, WM_CLASS) | PORT-LATER |
| 03 | Desktop window state (size/position/maximized restore) | PORT-LATER |
| 04 | Desktop window icon | PORT-LATER |
| 05 | Linux packaging and install (tarball, .deb, .desktop, AppStream, icons) | PORT-LATER |
| 06 | Desktop CI (Linux, Windows) and local build caveats | PORT-LATER |
| 07 | Windows runner (experimental) | DECIDE |
| 08 | iOS remote-only scaffold | PORT-LATER |
| 09 | Desktop release check and notice | PORT-LATER |
| 10 | Update-channel partition and version contract | REBUILD |
| 11 | Desktop shortcut layer and surface-claim bus | PORT-LATER |
| 12 | Keyboard shortcuts help sheet | PORT-LATER |
| 13 | Command launcher (palette) matcher | PORT |
| 14 | Right-click / Shift+F10 context menu | PORT-LATER |
| 15 | Scroll behaviour and persistent scrollbars | PORT-LATER |
| 16 | Mouse text selection and copy | PORT-LATER |
| 17 | File drop onto the composer | PORT-LATER |
| 18 | Enter-to-send, Ctrl/Cmd+Enter, focus after send | PORT |
| 19 | Hardware-keyboard chat shortcuts (also Android) | PORT |
| 20 | Terminal input path on desktop (OSC 52, links, key bar) | PORT-LATER |
| 21 | Android-only surfaces gated off elsewhere | PORT |
| 22 | Desktop keyring requirement, loopback-first pairing | PORT-LATER |
| 23 | Fine-pointer behaviour (hover, tooltips, cursors) | PORT |
| 24 | Web shell (unmerged) | DROP (keep lessons) |
| 25 | Accessibility rules A11Y-1..8 | PORT |
| 26 | Screen-reader announcements and live regions | REBUILD |
| 27 | Text-scale policy | PORT |
| 28 | Reduced motion, high contrast, platform focus ring, haptics | PORT |
| 29 | Gesture twins (no feature only behind a gesture) | PORT |
| 30 | Terminal accessible mode | PORT |
| 31 | RTL, Arabic face, bidi isolates | PORT |
| 32 | Accessible-navigation-aware transient UI | PORT |
| 33 | Accessibility of native surfaces (widgets, notifications) | PORT |
| 34 | External link gate (`openExternalLink`) | PORT |
| 35 | Per-profile key convention and deletion sweep | PORT |
| 36 | `KitRedact` credential redaction | PORT |
| 37 | The other scrubbers (to consolidate) | REBUILD |
| 38 | Fail-closed persistence; no raw errors | PORT |
| 39 | Transport trust summary (cross-reference) | PORT |
| 40 | Android manifest and backup posture | PORT |
| 41 | In-app preview WebView hardening | PORT |
| 42 | Wi-Fi file share server | PORT |
| 43 | Supply-chain and repository hygiene gates | PORT |
| 44 | Approval authentication, biometric, no app lock | DECIDE |
| 45 | Notification and lock-screen privacy | PORT |
| 46 | No-analytics policy and the Aptabase plan | DECIDE |
| 47 | Network egress inventory | PORT |
| 48 | Consent inventory | PORT |
| 49 | Privacy documents in the app, and their drift | PORT |
| 50 | Data removal paths (profile delete, Manage space, backup off) | PORT |
| 51 | `AppDiagnosticsController` (in-memory ring) | PORT |
| 52 | Global error capture, error fallback widget | REBUILD |
| 53 | Bootstrap gate containment and "Start fresh" | PORT |
| 54 | Crash diagnostics: opt-in categories (Dart controller) | PORT |
| 55 | Native crash store and uncaught-exception handler | PORT |
| 56 | Crash report preview and share | PORT |
| 57 | Exit history and launch report | PORT |
| 58 | Persisted problem report (`ReportProblem`) and capture | PORT |
| 59 | Performance trace (OCTRACE), PromptTrace, Dio/route observers | PORT |
| 60 | Report a problem: builder, GitHub link, clipboard fallbacks | PORT |
| 61 | Failed-job report | PORT |
| 62 | Logging policy (device log, logcat) | PORT |
| 63 | Diagnostics UI sections (kept screen, exits, performance, crash switch) | PORT |
| 64 | Secret input, clipboard and screen-capture hygiene | PORT (DECIDE `FLAG_SECURE`) |
| 65 | Process-wide secret registry lifecycle | PORT |

## 3. Feature catalog

### 3.A Platform seam, desktop and mobile-target scaffolds

**DESKTOP-WEB-CROSS-01 - `PlatformCapabilities` seam.** `lib/platform/platform_capabilities.dart`
(156 lines). An immutable `{platform: TargetPlatform, isWeb}` value derived from
`defaultTargetPlatform` (not `dart:io.Platform`, which reports the test host). Getters:
`isAndroid`, `isDesktop` (linux/macOS/windows and not web), and capability flags:

| Flag | Meaning | True on |
|---|---|---|
| `supportsTermux` | `oc/termux` bridge, guided setup, on-device server | Android |
| `supportsTailscaleHandoff` | open the Tailscale app | Android |
| `supportsVoice` | `oc/voice` mic permission, recorder, on-device speech | Android |
| `supportsReadAloud` | system TTS with offline voices | Android |
| `supportsLocalPdf` | read-only raster PDF bridge (Android 10+) | Android |
| `supportsVoiceConversation` | `supportsVoice && supportsReadAloud` | Android |
| `supportsPromptPhotos` | camera/gallery photos in composer | Android |
| `supportsBackgroundService` | `oc/background` foreground service, battery exemption | Android |
| `supportsNotifications` | privacy-safe coding alerts and their actions | Android |
| `supportsHomeWidget` | widget snapshot publish and redraw | Android |
| `supportsLaunchShortcuts` | static + dynamic pinned-session launcher shortcuts | Android |
| `supportsQuickSettingsTile` | needs-attention tile cache | Android |
| `supportsCodePush` | Shorebird patches | Android |
| `supportsDesktopReleaseCheck` | GitHub release check (`!isWeb && linux/windows`) | Linux, Windows |
| `supportsUsbHostBridge` | `adb reverse`/USB host copy | Android |
| `supportsQrPairing` | camera permission, `oc/camera`, scanner preview | Android |

Test seams: `debugPlatformCapabilities` setter/getter; constructors `.android()` and `.linuxDesktop()`.
Read at point of use, never cached. The test (`platform_capabilities_test`) asserts: Android has every
feature; Linux none of the Android-only ones; iOS leaves each unsupported feature off; macOS is
desktop with no release artifact; web is neither Android nor desktop; **every capability is claimed by
exactly one update channel** (`update_channel_platform_test`: Shorebird xor desktop release check xor none).
Rewrite stance: PORT as `expect object PlatformCapabilities` with an Android `actual` first; keep the
"named after the capability, not the OS" rule. iOS/desktop `actual`s answer false for the Android-only
flags and add their own (`supportsBiometricUnlock`, `supportsKeyringBackedSecrets`, ...) when needed.

**DESKTOP-WEB-CROSS-02 - Linux GTK runner.** `linux/runner/my_application.cc` (161 lines),
`linux/CMakeLists.txt`. Binary `opencode_mobile`; GApplication id and program name
`io.github.eslamasabry.opencode_mobile` (changed 2026-08-31 from `ai.opencode.opencode_mobile`, which sat
under a domain the project does not own; old desktop entries must be removed by hand);
`G_APPLICATION_NON_UNIQUE` (several windows allowed). Window title "OpenCode" natively (frames drawn before
Dart starts); GNOME Shell / Wayland gets a header bar with a close button, other X11 window managers get
a normal title bar (tiling WMs); default size 1280x720 (Dart then applies its own geometry);
background colour `#000000`; the window is shown on Flutter's `first-frame` signal, not at creation
(no white flash). Both halves of `WM_CLASS` are pinned to the app id (`g_set_prgname` +
`gdk_set_program_class`) so the shell matches `StartupWMClass` on X11 and `app_id` on Wayland.
`test/desktop_packaging_contract_test.dart` fails if the app id, desktop entry name, `StartupWMClass`,
icon names, binary name or version convention drift apart (a mismatch has no runtime symptom except a
generic icon). Stance: PORT-LATER; with Compose Desktop this is `Window(title, icon)` plus
`StartupWMClass` in the generated `.desktop` (jpackage `--linux-*` options) - keep the app id.

**DESKTOP-WEB-CROSS-03 - Desktop window state.** `lib/desktop/window_state.dart` (440 lines), started from
`main()` on Linux/Windows/macOS only (`dart:io Platform`, deliberately not the capability seam because
it is about the running process). Stored in SharedPreferences key `desktop_window_state_v1` as a JSON
string `{"x":..,"y":..,"width":..,"height":..,"maximized":bool}` (finite doubles; width/height > 0;
anything else = nothing remembered; `bounds` is always the **restored** rectangle even when maximized).
Constants: default 900x700, minimum 480x600 (below it the rail plus a readable transcript stop fitting),
title "OpenCode". Pure functions (unit-tested without a window):
`displayForBounds` (display sharing the most area, else first = primary), `fitBoundsToDisplay`
(clamp into work area, at least the minimum size, at most the display; a window as wide as the display
has exactly one legal left edge), `centerInDisplay`, `resolveStartupGeometry` (rules in order: nothing
saved or no displays -> default size centred; overlapping a display -> fitted into it; overlapping
nothing e.g. unplugged monitor or negative coordinates -> saved **size** kept, centred on primary).
Controller: reads work areas through `screen_retriever` (`visiblePosition`/`visibleSize` so panels and
docks are excluded), `setPreventClose(true)`, `waitUntilReadyToShow` -> `setBounds` (only when not
centring) -> **maximize last** (setBounds on a maximized GTK window is a no-op and would lose the
restored rectangle) -> show -> focus; listens for resize/move/maximize/unmaximize; saves are debounced
400 ms (GTK emits a configure event per frame while dragging); on close it re-reads bounds if not
maximized, flushes synchronously, then `destroy()`. Every failure is swallowed (`debugPrint` only): window
placement must never block start or close. Stance: PORT-LATER; Compose Desktop's `rememberWindowState`
+ a persisted `{x,y,w,h,maximized}` record with the same clamp rules; keep the pure functions and port
`desktop_window_state_test` rows (catalog: 4 rows).

**DESKTOP-WEB-CROSS-04 - Desktop window icon.** `lib/desktop/window_icon.dart` (44 lines):
`windowManager.setIcon('assets/branding/app-icon-256.png')` on Linux only, after `setUpDesktopWindow`
completes (GTK needs a realised window); failure is swallowed. Only the 256 px variant is a bundled asset
(the 1024 px master is deliberately not bundled: 795 KB in every Android build for nothing). Windows would
need an `.ico` (`windows/runner/resources/app_icon.ico` exists for the exe itself), macOS has no
implementation. The test only pins that the asset exists and is bundled (catalog: DROPPED as
Dart-layout-specific). Stance: PORT-LATER.

**DESKTOP-WEB-CROSS-05 - Linux packaging and install.** `scripts/package-linux.sh` (365 lines),
`linux/packaging/{install.sh,uninstall.sh,*.desktop,*.metainfo.xml,README.tarball.md,render-icons.sh,
gcc-install-dir.sh,icons/hicolor/*}`.
- Artifacts (in `build/linux/packages`): `opencode-mobile-linux-<x64|arm64>-<version>.tar.gz`,
  `opencode-mobile_<version>_<amd64|arm64>.deb`, `SHA256SUMS`.
- The script refuses a bundle whose `data/flutter_assets/version.json` disagrees with `pubspec.yaml`,
  validates the `.desktop` with `desktop-file-validate`, derives the artifact architecture from the
  runner's ELF header (`readelf`) and requires every bundled `.so` to match (the host architecture never
  labels a foreign bundle), is **deterministic** (tar `--sort/--mtime/--owner/--numeric-owner`,
  `gzip -n`, the `.deb` staging tree flattened to `SOURCE_DATE_EPOCH`; the Flutter build itself is not
  reproducible, so publish checksums of the CI artifacts, never regenerated ones).
- `.deb` `Depends` are derived with `dpkg-shlibdeps` (so the .deb targets the distro it was built on:
  Ubuntu 24.04 `t64` names will not install on Debian 12); a curated fallback list exists. Runtime
  needs: GTK 3, **`libsecret-1-0` and a running keyring daemon** (credentials otherwise do not persist),
  `zenity` or `qarma` for file pickers.
- `install.sh`: per-user `~/.local` (no root) / `/usr/local` as root / `--prefix`; places the runtime at
  `<prefix>/lib/opencode-mobile`, a symlink launcher `<prefix>/bin/opencode-mobile`, `.desktop` (with `Exec`
  rewritten), AppStream metainfo, hicolor icons 16..512 + scalable SVG; **ownership marker**
  `.opencode-mobile-install` - it refuses to replace a runtime or launcher it cannot prove it owns;
  replaces the runtime directory wholesale on upgrade (no stale plugin `.so`); runs
  `update-desktop-database`/`gtk-update-icon-cache` if present. `uninstall.sh` removes only owned files
  and leaves `~/.config` / `~/.local/share/opencode_mobile` (settings and saved servers).
- Desktop entry: `Name=OpenCode Mobile`, `Categories=Development;IDE;`, `StartupWMClass` = app id;
  AppStream `<id>io.github.eslamasabry.opencode_mobile</id>`, MIT, `oars-1.1`.
- Clang/libstdc++ trap: on hosts whose newest GCC tree has headers but no `libstdc++.so`, the link fails;
  `gcc-install-dir.sh` compiles a probe, and only then prints a `--gcc-install-dir` flag (never a flag it did
  not prove).
Not shipped: AppImage, Flatpak, code signing of any kind, 32-bit. Stance: PORT-LATER; Compose Gradle
plugin `packageDeb`/`packageRpm`/`packageAppImage` (verify in the version pinned) replaces the script, but keep
the **contract test** idea (id/icon/version coupling) and the ownership-marker installer if a tarball
stays.

**DESKTOP-WEB-CROSS-06 - Desktop CI and workflows.** `.github/workflows/desktop-linux.yml` (analyze,
tests in thirds at `--concurrency=1`, `flutter build linux --release`, `ldd` shows nothing missing, Xvfb
launch, `package-linux.sh`, upload tar/.deb/SHA256SUMS; on a `v*` tag **attaches** assets to the existing
release - never creates a release, `scripts/release.sh` is the only thing that makes releases),
`desktop-windows.yml` (analyze, `flutter build windows --release`, zip artifact
`opencode-windows-x64-<version>`, **not** attached to releases until a contributor proves it launches),
`ios-quality.yml` (unsigned simulator build + three contract tests). Action versions are pinned by SHA.
Stance: PORT-LATER (re-create as Gradle jobs); keep "attach, never create".

**DESKTOP-WEB-CROSS-07 - Windows runner.** `windows/` (CMake, `runner/main.cpp` window title
`"OpenCode Mobile"`, `Runner.rc` product strings, `resources/app_icon.ico`). Credentials would use
`flutter_secure_storage`'s wincred backend, file pickers the Windows implementation. The bug-report
platform label for Windows is `'Windows desktop (experimental — contributor-tested)'` so reports arrive
contexted. No MSIX/installer (needs a signing decision), no release attachment. Stance: DECIDE. The
owner's stated follow-ups are iOS and Linux desktop; Windows has never been run by a maintainer and
should stay out of the first native slices unless a Compose Desktop build gives it for free.

**DESKTOP-WEB-CROSS-08 - iOS remote-only scaffold.** `ios/` (README, Runner with `AppDelegate.swift`,
`SceneDelegate.swift`, `Info.plist`, entitlements, icons). Source only: generated with Flutter 3.47.2,
identifier `io.github.eslamasabry.opencodeMobile`, deployment target iOS 15.0, empty keychain-access-group
array in both entitlements files, local-network purpose text, no ATS cleartext exemption, no
background mode/microphone/camera prompt/push entitlement. Capability behaviour: no Termux, foreground
service, notification reply, widget, voice, photo capture or QR on iOS; "use a reachable HTTPS server and
paste/manual pairing; background suspension requires refetch on return, not continuous SSE"; About shows
`iosRemoteSummary` instead of the voice line. Tests: `ios_runner_contract_test`,
`ios_remote_platform_gating_test` (stale keep-live preference cannot prevent suspension; native
backstops stay unavailable without outgoing native calls). Open gates before any iOS claim: Keychain
save/restart/failure, file picking/export, local-network permission and TLS, keyboard/safe areas,
foreground/resume reconciliation, VoiceOver, text scaling, a physical-device run, and the App Store
privacy-manifest / required-reason API inventory (no empty manifest "to pretend"). Stance: PORT-LATER;
this is the best written statement of what an iOS client may assume, reuse it as the spec for the iOS
`actual` capabilities (remote-only; HTTPS or Tailscale only; refetch on resume).

**DESKTOP-WEB-CROSS-09 - Desktop release check and notice.** `lib/update/desktop_release_check.dart`
(297 lines). Constants: API `https://api.github.com/repos/Eslamasabry/opencode-mobile-next/releases`
(`per_page=10`, header `Accept: application/vnd.github+json`), page
`https://github.com/Eslamasabry/opencode-mobile-next/releases`, network timeout 10 s (connect, receive,
send, and a hard `Future.timeout` that cancels the token so a hanging adapter cannot hang the notice).
- Tag grammar `^v\d+\.\d+\.\d+\+(\d+)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?$`; the **build number after `+`**
  is the monotonic order (`v1.0.25+26-preview.5` -> 26). A tag without one is never "newer".
- The checker skips drafts, accepts pre-releases (the preview lineage is the distribution channel), picks
  the highest valid build (GitHub orders by publication time, not build number), and reads only
  `tag_name` and `html_url` - never asset names.
- `html_url` is trusted only if it is `https://github.com/Eslamasabry/opencode-mobile-next/releases` or
  `.../releases/tag/<x>` with no port, query, fragment or userinfo (case-insensitive owner/repo);
  otherwise the compiled-in releases page is used.
- `DesktopReleaseNotice` checks on first frame and on `resumed`, at most once per 15 minutes, only where
  `supportsDesktopReleaseCheck`; when newer it shows ONE status line (`KitStatusKind.update`, id
  `update:desktop-release`): "OpenCode {tag} is available" / "The release page lists what changed" /
  action "Open release page" (through `openExternalLink`, using the navigator's context because the notice
  sits above the Navigator) / Dismiss (hides for the rest of the run; next start checks again). Nothing
  downloads or installs. Current build number from `PackageInfo.buildNumber` (on Linux read from
  `data/flutter_assets/version.json` next to the resolved `/proc/self/exe`).
- Tests: `desktop_release_check_test` (tag parsing, newer-means-strictly-larger, drafts skipped, highest valid
  build from unordered entries, malformed entries do not hide a valid one, bounded wait, untrusted URL
  fallback, one status line, production path uses the link gate, dismiss, throttle, never on non-desktop).
Stance: PORT-LATER with Ktor; same grammar and URL trust. The same grammar may serve an Android
"new APK available" notice if the owner wants an in-app update check after Shorebird ends (DECIDE).

**DESKTOP-WEB-CROSS-10 - Update-channel partition and version contract.** `OcApp.initState` picks
`ShorebirdAppUpdateService` when `supportsCodePush` else `UnavailableAppUpdateService`;
`ShorebirdUpdateNotice` wraps the app (silent download when the profile's
`AutomationBehavior.applyCodePush` allows, "App update ready" lowest-priority status, recorded in
"While you were away" via `recordServerAct`), `DesktopReleaseNotice` wraps it too; both feed the single
`UpdateStatusScope`. `shorebird.yaml`: `auto_update: false` - the app owns checks, downloads, progress and
retry (running the engine updater in parallel makes an in-progress download look finished). Version
contract: `pubspec.yaml` `version: 2.1.0+57` -> `version.json` `{version, build_number}`; tags
`vX.Y.Z+N[-preview.K]`; Android `versionCode` = N. Hard rule from memory: Shorebird patches only ship
Dart; native/asset/pubspec changes need a new release; **Shorebird ends at the native switch**, so the
first native build is a full release over the Flutter one (same package, same signing key) - see the
migration notes (section 9). Stance: REBUILD (new Android update story; keep the one-status-line
presentation and the lowest priority).

### 3.B Desktop interaction layer (additive; most of it also serves Android with a keyboard or mouse)

Gate: `desktopInteractions` = `platformCapabilities.isDesktop` (`lib/ui/desktop/desktop_interaction.dart`).
It is a named getter (not 40 inline checks) so the reason for each gate stays legible and one test
override swaps all of them. `finePointer` (`KitLayout.finePointer`) = `desktopInteractions` OR a mouse
the framework has seen connected; it adds hover/tooltips/cursors, **never smaller targets**.
`shortcutModifierLabel` is `Ctrl` (or the Command glyph if the platform is macOS).

**DESKTOP-WEB-CROSS-11 - Shortcut layer and surface-claim bus.** `lib/ui/desktop/shortcuts.dart`
(816 lines). Design: the shell binds keys to intents; surfaces **claim** intents by registering with
`AppShortcutSignals` (consulted newest-first = route push order); anything unclaimed falls to the shell's
handler. Claiming (not nested `Actions`) is used because a freshly pushed route has no focused widget yet
and action lookup starts at the primary focus - a surface would silently stop responding until clicked.
`AppShortcutSurface` mixin registers in `didChangeDependencies`, and each claim re-checks
`ModalRoute.isCurrent` so a buried surface never steals a key. Off desktop only the bus is installed
(no key bindings): search results, notifications, tiles and pinned shortcuts reach the shell's tabs through
the same intents, and the shell's Search button opens the same launcher as Ctrl/Cmd+K.
- Bindings (each registered for Control AND Meta, so a keyboard attached to any platform works - 10
  accelerators = 20 entries): K palette, N new session, F find in surface, `,` settings, W close route
  (`maybePop`, never `pop`, so a `PopScope` veto survives), `/` shortcuts help, `` ` `` terminal, 1/2/3
  destinations (Chats, Files, Settings). **No plain-letter accelerators**, so typing in the composer, a
  rename dialog or a search field can never fire one; Ctrl+Enter (send) stays on the field; Esc is left to
  Flutter's modal-dismiss action.
- Intents: `OpenCommandPaletteIntent, NewSessionIntent, FindInSurfaceIntent, OpenSettingsIntent,
  CloseRouteIntent, ShowShortcutsHelpIntent, OpenTerminalIntent, SelectDestinationIntent(index),
  OpenChatsIntent(needsYou, running)` (the last has no key; notifications, tile, pinned shortcuts and search
  use it). `FindInSurfaceIntent` has no shell fallback but is still registered so the keystroke never leaks
  as a literal character. Destinations/terminal/OpenChats from a pushed route call `dispatchAtShellRoot`:
  pop to first route, dispatch, and re-dispatch after the next frame if the surface was still animating in
  (fix: Ctrl+1..4 and Ctrl+` "must not silently do nothing" while chat/terminal/review is open, commit
  `e2346919d`, 2026-09-02).
- Shell actions behind the intents: `_startNewSession` (create session, push `/chat/<id>` with
  `ChatRouteArguments.newlyCreated()`, failure = one status line "could not start a conversation" with
  plain-words support text), `_openSettings`. Stance: PORT-LATER as a `KeyEvent` router in Compose
  Desktop / Android keyboards; the claim-bus idea is worth keeping.

**DESKTOP-WEB-CROSS-12 - Keyboard shortcuts help sheet.** `showShortcutsHelp` (also reachable from About
and the More hub row `library-keyboard-shortcuts`, "so it is not itself hidden behind a shortcut"),
grouped Anywhere / Conversation / Review, keys in a mono column, description wraps (max 4 lines), a
closing line that says the shortcuts do not fire while typing. Rows: `Mod+K` launcher, `Mod+N` new session,
`Mod+F` find, `Mod+1..3` destinations, `Mod+,` settings, ``Mod+` `` terminal, `Mod+W` close, `Mod+/` this
list, `Esc` close overlay, context keys (Shift+F10 / Menu / right-click); conversation: `Mod+Enter` send,
Stop reply keys, `Mod+C` copy transcript, `F2 / Shift+F2` recent model, `Ctrl+B` move work to background,
`F3 / Shift+F3` next/previous find match, `Up / Down` prompt history, `F4 / Shift+F4` thinking level,
`PgUp/PgDn` transcript pages, `Home/End` transcript ends; review: `] / [` next/previous file, `N / F7`,
`P / Shift+F7` next/previous change. A repo audit (`gesture-audit.md`, commit `b2cf153f8`) found
handled-but-unlisted shortcuts and added them: every shortcut must be listed. Stance: PORT-LATER; derive
the list from the key bindings (single source) so it cannot drift.

**DESKTOP-WEB-CROSS-13 - Command launcher matcher.** `showCommandPalette(context, commands)` in the kit
sheet (centred panel on wide windows); `DesktopCommand{label, detail, icon, onInvoke, hint, keys,
keywords, section(goTo|recent), onlyWhenSearched, onlyWhenEmpty, key}`. `matchCommands`: empty query
lists Search's own menu (the `goTo` group then `recent`; or every non-`onlyWhenSearched` command when no
sections); a query is matched by the **same matcher as Settings search** (`SettingsSearchIndex`: word by
word, prefix or one typo, any case; whole label first) over label, hint, detail and keywords; Enter runs the
first match (drawn filled so the choice never rests on colour alone), Arrow Down moves into the rows. The
field is focused on a post-frame callback (autofocus alone loses to the button that still holds focus
while the sheet animates; `command_palette_focus_test`). Commands assembled in
`main_parts/alerts_and_shortcuts.dart::_shellCommands`: New session, Chats, the Go-to places
(`goToCommands`: Files, Projects, Settings, Servers, Terminal, Scheduled prompts, AI Team, Help), keyboard
shortcuts, Refresh sessions, Diagnostics (`/debug`), then the whole `searchIndex` (minus ids already
shown), up to **400 conversations** (`!isSubagent`; label = title, hint = project + agent label, opens on
its own backend via `ConnectionChatsHost.openChat`) and up to **200 projects** (not temporary
directories; opens that project's Files page). Duplicate labels get numbered row keys. Stance: PORT (the
matcher and command assembly are shared product logic; the UI is Compose).

**DESKTOP-WEB-CROSS-14 - Context menus.** `lib/ui/kit/kit_context_region.dart` (111 lines; the old
`lib/ui/desktop/context_menu.dart` forwards). `KitContextRegion`: secondary click (position = pointer),
**Shift+F10** and the **context-menu key** (position = centre of the region while it has focus), one menu at a
time (`_menuOpen` guard), the kit focus ring (2 physical px accent) while keyboard-focused, menu built by
callback at click time, region excluded from semantics because every action also exists in the child's own
sheet/menu. Off desktop returns the child untouched; long-press sheets stay the only touch surface. Where
the transcript is mouse-selectable, right-click still opens the per-message menu (the nearer region wins
the gesture arena). Stance: PORT-LATER.

**DESKTOP-WEB-CROSS-15 - Scroll behaviour and scrollbars.** `lib/ui/kit/kit_scrollbar.dart` (131 lines):
`KitScrollBehavior` (app-wide `scrollBehavior`): on desktop a **vertical** scrollable that owns a
controller gets an always-visible, draggable thumb; horizontal strips none; `KitScrollArea` supplies the
controller on desktop only (null on touch so touch scrolling is byte-for-byte unchanged); `KitScrollbar`
(explicit scrollbar for code blocks, tables, logs: visible when `finePointer`) and `KitOwnScrollbar` (stops
a second thumb). **The mouse is deliberately not a drag device**: drag-to-scroll would steal the gesture
that selects text; wheel and trackpad arrive as pointer signals / pan-zoom, not drags. RawScrollbar asserts
on a visible thumb without a position, hence the controller-owning rule. Stance: PORT-LATER.

**DESKTOP-WEB-CROSS-16 - Mouse text selection and copy.** `KitSelectable(mode: finePointer)` wraps the
transcript: with a fine pointer the primary button selects text across messages and Ctrl/Cmd+C copies it;
touch keeps long-press for the message menu (`transcript_rows.dart`: `selectable: !desktopInteractions`).
`desktop_selection_test` also asserts Android selects reply prose without transcript-wide selection.
Stance: PORT-LATER (Compose `SelectionContainer`; keep the per-message right-click menu winning).

**DESKTOP-WEB-CROSS-17 - File drop onto the composer.** `lib/ui/desktop/file_drop.dart` (148 lines),
package `desktop_drop` 0.8.4 (pinned exactly - it registers a platform plugin; on Android it also adds a
Gradle subproject that prints a KGP warning, harmless because no `DropTarget` is ever built there).
`DroppedFile{name, mimeType, length(): Future<int>, readBytes()}` is the plugin-free abstraction (a widget
test drives `debugHandleDrop` with no platform channel); `length` is consulted before `readBytes` so an
oversized file is refused without pulling it into memory. Behaviour: drag-enter shows a solid surface
panel (`composer-drop-highlight`, `surface3`, outlined, never tinted or blurred) with the attach icon and
the `desktopDropHint` text; drop calls the same attachment pipeline as a picker; a second drop while one
is handled is ignored; any exception shows `showKitAlert` with fixed words (`desktopDropFailedTitle` /
`desktopDropFailedRecovery`), **never exception text** (it can hold local paths), with no automatic retry
because some files may already be attached. Off desktop returns the child (no plugin, no listener).
Stance: PORT-LATER (Compose `dragAndDropTarget` + `DragData.FilesList`; docs say it is the way to accept
data from other apps; type checks on dragged images were reported unreliable, so detect type from the
path/bytes; verify against the pinned Compose version).

**DESKTOP-WEB-CROSS-18 - Enter-to-send and typing-focus rules.** `lib/ui/kit/kit_field.dart` (multiline
fields): `Ctrl+Enter`, `Meta+Enter` and `Ctrl+NumpadEnter` always submit; plain `Enter` submits **only
when the field is the composer and `finePointer`** (so a phone, even with a hardware keyboard but no mouse
seen, keeps Enter = newline); `Shift+Enter` is a newline (`composer_desktop_enter_test`: desktop Enter
sends, Shift+Enter does not, Ctrl+Enter still sends, mobile Enter never sends). The composer's send
control shows the "Enter" shortcut hint when the window is wide. After sending, **desktop keeps focus** for
the next line; a phone unfocuses so the keyboard does not cover the reply (`chat_send.dart`).
`kitTypingInField()` (`kit_typing_guard.dart`) guards the plain-key shortcuts - PgUp/PgDn/Home/End in
`ModelShortcuts` and the review page's letter keys (N/P, in `kit_diff_view.dart`) - so they never fire while a
text field has focus (the only plain-letter accelerators in the app are these review ones, local to the diff
page). Stance: PORT (Compose `onPreviewKeyEvent`; the mouse-seen heuristic needs a
Compose equivalent - e.g. `PointerType.Mouse` events or `InputDevice` source checks).

**DESKTOP-WEB-CROSS-19 - Hardware-keyboard chat shortcuts (also on Android).**
`lib/ui/widgets/model_shortcuts.dart` (`ModelShortcuts`, "chat-local shortcuts also work with a hardware
keyboard on Android"), `chat_page.dart`. A `Focus(canRequestFocus:false, skipTraversal:true,
includeSemantics:false)` wrapper (so keyboard handling does not merge the chat's independent TalkBack
nodes) ignores keys when its route is not the current one (a sheet or dialog above the chat keeps the
keyboard). Keys: `Ctrl+B` move running work to background (`includeRepeats:false`), `F2/Shift+F2` cycle recent
model (favourites variant), `F4/Shift+F4` thinking level, `PageUp/PageDown/Home/End` transcript pages/ends
**only without Ctrl/Meta/Alt/Shift and not typing**, `F3/Shift+F3` find next/previous, `Esc` closes find
(`chat_page.dart:309`) and (since 2026-10-31, `3302ab129`) stops the running reply, `Up/Down` at the caret
edge walks prompt history (`PromptHistoryNavigation` keeps the original draft), review page `]`/`[`/N/F7/P.
All have a visible control (gesture audit). Stance: PORT.

**DESKTOP-WEB-CROSS-20 - Terminal input on desktop.** `terminal_surface.dart`, `terminal_input.dart`,
`kit_terminal_view.dart`, third-party `third_party/xterm` fork. Desktop terminal uses **hardware keys only**
(`hardwareOnly = desktopInteractions`; `terminalKeyEventText(event)` accepts a key-down whose character is
printable (code >= 0x20, not DEL) with no Ctrl/Meta held and writes `event.character`; control keys and
chords are left to xterm's own key table) so a keystroke never arrives twice (key event + IME); input goes through **one ordered FIFO** so bursts arrive
once and in order (commit `e2346919d`: "terminal input goes through one ordered FIFO and desktop keys bypass
xterm's IME path"); the on-screen key bar is hidden on a wide desktop window and shown on phones/tablets
(Ctrl-C = 0x03, Ctrl-D = 0x04 bytes). Terminal understands OSC 52 (set-clipboard decodes; **a query is never
answered**; oversize/garbage ignored), detects links in output even when soft-wrapped or newline-wrapped
(trailing punctuation excluded), resize is debounced 150 ms, the accessible transcript is capped at 100,000
characters. Stance: PORT-LATER for the key path; PORT for OSC 52 safety rules (they are security rules:
never answer a clipboard query).

**DESKTOP-WEB-CROSS-21 - Android-only surfaces gated off elsewhere.** Mechanism: 16 flags
(DESKTOP-WEB-CROSS-01) read in ~60 call sites. Visible effects, pinned by `desktop_platform_gating_test`
(and `ios_remote_platform_gating_test`): first-run welcome never mentions the phone path; the Servers
quick-add list offers only the remote path; the setup guide drops the Termux path; the Termux setup
screen, if ever reached, says so plainly; composer prompt tools drop voice input; the settings hub hides
the Background category (and Notifications and background); host management drops the `adb reverse` USB
command; `BackgroundLiveController` and the widget snapshot/launch-surface publishers never reach their
channels (and no longer park a "available in the Android app" error on a controller whose UI is hidden,
commit `5d1991c8c`); `thisPhoneRoute` is not even registered in the route table off Android (a desktop deep
link or leftover push must not land on a page with no phone behind it); the URL validator does not explain
http:// by naming Termux off Android; voice/read-aloud/QR/photos/PDF each check their flag. Principle: an
unavailable thing is **absent, never disabled**. Stance: PORT (native screens query the capability object;
add a "feature X absent when flag false" test per screen).

**DESKTOP-WEB-CROSS-22 - Desktop keyring requirement and loopback-first pairing.**
(a) `SecureStorageUnavailable.forPlatform(linux)` = "Could not store the password: no keyring is
available. Install GNOME Keyring or KWallet, or run the app inside a desktop session, then try again."
(generic: "Could not store the password securely on this device."); the Servers editor probes the keyring on
Linux and warns above the form (issue #5, commit `e2346919d`); `productErrorText` no longer labels platform
errors as "unreachable". There is **no plaintext fallback anywhere**: without libsecret + an unlocked
keyring, passwords silently fail to persist rather than being written in the clear. `dbus` (MPL-2.0) is reached
only through `flutter_secure_storage_linux`. (b) `pairing.dart:497`: `preferLocal = platformCapabilities.isDesktop`
- when an `opencode2 pair` payload lists several addresses, a desktop probes loopback first (the server is
usually on the same machine; reading a QR off the same machine's screen makes no sense, so desktop has
no scan affordance, only paste). Stance: PORT-LATER (JVM keyring: `java-keyring`/`java-keytar` class of
libraries use Freedesktop Secret Service/libsecret on Linux and Credential Manager on Windows - verify
maintenance; headless sessions have no unlocked keyring and must fail closed with plain words).

**DESKTOP-WEB-CROSS-23 - Fine-pointer behaviour.** `desktop_pointer_test`: a mouse on Android also takes the
click cursor on file links; Android keeps the plain Files hairline divider (the Files tree|preview split
gets a draggable divider on desktop). Hover states and tooltips appear for `finePointer`; tap targets never
shrink. Stance: PORT.

### 3.C Web shell

**DESKTOP-WEB-CROSS-24 - Web shell (not in this tree).** What exists on `master`/`batch1`: `isWeb` in
`PlatformCapabilities` (no secure-context field), `kIsWeb` checks in 12 files (`wifi_file_share` supported =
`!kIsWeb`, `kit_ink_layer`, `kit_scanner`, `kit_web_preview.platformSupported`, `kit_page_transitions`,
`crash_reports_section` hidden on web, `crash_diagnostics` channel only on Android, `phone_zone`), and the
label `'Web (unsupported)'`. There is no `web/` directory in the tree. On the unmerged branches (Sept 13,
all `[skip ci]`, tests written but **never executed**): `web/index.html`, browser capabilities
(`secureContext` derived from `Uri.base`: https, file, or http on `localhost`/`*.localhost`/`127.0.0.1`/`::1`;
`supportsClipboardRead`, `supportsSecureCredentialStorage`, `supportsCodexConnections = !isWeb`),
desktop conventions in a desktop browser (Alt+N / Alt+W instead of browser-reserved Ctrl+N / Ctrl+W;
browser context menu suppressed only while an app region is hovered), `lib/network/`
(`BrowserStreamingHttpClientAdapter`: fetch + AbortController because `dio_web_adapter` 2.2.1 completes
only on XHR `onLoad`, so SSE delivered nothing until close; `PlatformWebSocket` over the DOM socket; honest
"a browser cannot tell CORS, mixed content, DNS and a stopped server apart"), an IndexedDB attachment vault,
`ProfileStore.secureStorageProblem()` for insecure contexts, a "Browser mode" settings page, A2A request
preservation, `tool/preview/run_web.py` hot-reload runner. **Browser facts established from engine/plugin
source**: `navigator.clipboard` is absent outside a secure context (Flutter then throws a platform error);
`flutter_secure_storage_web` 2.1.1 throws `UnsupportedError` outside a secure context; the Codex transport's
Authorization header on a WebSocket upgrade is impossible in a browser (no query-token workaround);
`http://100.x.x.x:8789` is not a secure origin, `http://localhost` (SSH tunnel) or https is. Stance: **DROP**
for the native rewrite (owner targets are Android, iOS, Linux desktop). If a web build is ever wanted, the
branch ledger `docs/verification/web-shell-2026-09-13.md` (on `feat/web-shell-20260913`) is the
requirements list; do not forward-port the branches.

### 3.D Accessibility

Sources of truth: `docs/ux-system/revamp/STANDARDS.md` (A11Y-1..8), `docs/ux-system/kit-v2.md` §8,
`docs/design/ui-ledger/gesture-audit.md`, tests listed per feature. Nothing here is a screen; it is the
contract every screen and kit part must meet.

**DESKTOP-WEB-CROSS-25 - Accessibility rules A11Y-1..8.**

| Rule | Statement | Enforced by |
|---|---|---|
| A11Y-1 | Every control has a semantic label; an icon alone is never enough; nav labels (dock/rail/sidebar) always visible; decorative icons excluded from semantics | `accessibility_guidelines_test`, G5 gallery guidelines |
| A11Y-2 | Every screen and part reflows at 200 % text with nothing clipped and no overflow at widths 320, 360, 412, 600, 800, 840, 1280, 1600 dp plus 915x412 landscape; critical flows also pass at `AppTheme.maxTextScale` 2.5 on 360x740; supporting lines wrap rather than truncate | `text_scale_overflow_test` + 6 matrix shards (`text_scale_overflow_matrix_0..5`), `app_text_scale_test`, baseline JSON ratchet |
| A11Y-3 | A status change is announced **exactly once, by its host's live region**; rows and log lines are not live regions; a rebuild with the same words is not a change | `work_tab_status_line_test`, per-part tests |
| A11Y-4 | Semantics traversal order is top-to-bottom, then start-to-end (by rect) in galleries and goldens | G5 |
| A11Y-5 | Every gesture has a visible twin (button or menu item) or, for row menus, a semantic custom action | `gesture_audit_test`, G37 |
| A11Y-6 | Critical flows (chat, busy composer, home shell, workspace, Activity, Settings, manage project, servers first run, permission sheet, form renderer) and every kit gallery pass `androidTapTargetGuideline`, `labeledTapTargetGuideline`, `textContrastGuideline` in dark and light | `accessibility_guidelines_test` |
| A11Y-7 | Each wave checkpoint record contains one TalkBack walk of the five top journeys | reviewer / G32 |
| A11Y-8 | Only kit parts clamp text scale, each clamp a named `KitTokens` constant with a reason; no file outside the kit uses `TextScaler.linear`, `maxScaleFactor:` or `TextScaler.noScaling`; text may truncate only in a row title (one line below 1.3x, two lines from 1.3x), a name/path in a list (middle ellipsis for paths) and a chip; every truncated text carries its full value in semantics and in a tooltip/detail page | G21 ratchet |

Tap-target sizes: minimum 44-48 dp (`KitTokens.minTarget`); a number in a gutter gets a 48 dp reach.
Stance: PORT. Compose equivalents: `Modifier.semantics`, `minimumInteractiveComponentSize`, Compose UI test
`assertIsDisplayed`/accessibility checks (`enableAccessibilityChecks()` in Espresso/Compose test; verify
available checks), a font-scale test matrix via `LocalDensity`/`fontScale`, and the same 9 critical flows.

**DESKTOP-WEB-CROSS-26 - Announcements and live regions.** All announcements go through Flutter's
`SemanticsService.sendAnnouncement(view, message, direction)` (14 call sites in 12 files: `KitCopy` ("copied"),
`KitSearchField` result counts, `KitField` counters/errors, `KitViewer`, `KitTerm`, `KitTip`/tour steps,
`KitDateTimePicker`, `chat_notices` ("a reply finished / failed / needs you" - commit `a3fd9cb83`),
`agent_sheet`, `file_preview`, `work_status_line`, `queued_prompt_move_sheet`, `background_pause_notice`)
plus `Semantics(liveRegion: true)` on `KitErrorFallback`, `KitNotice`, status lines and checklists.
Rule: one polite live region per host whose own label is the message; "read once when it appears" for
cards; timers (relative ages) tick once a minute and are never live regions. Research note (2026-11-01):
Android's API diff lists `View.announceForAccessibility(CharSequence)` as deprecated (it makes TalkBack
clear its speech queue); Flutter's own docs steer to implicit announcement via Semantics. For Compose,
prefer `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` on the host and change its text once;
use an assertive region only for failures that interrupt; confirm the replacement API in the Android 17
reference before depending on `announceForAccessibility`. Stance: REBUILD (mechanism), PORT (the
"exactly once" rule and the list of announced moments).

**DESKTOP-WEB-CROSS-27 - Text-scale policy.** `KitText.appScale` at the app root (`AppTheme.maxTextScale =
2.5`): the system scale passes through untouched below the cap, including sub-1.0 values people pick on
purpose; the app's own Text size (`oc.textSize`: smallest 0.85, small 0.92, normal 1.0, large 1.15)
multiplies the system scale **before** the cap and eases in on change. Named kit clamps (not per screen):
`navLabelMaxScale` 2, `badgeTextScaleMax` 1.3, `monogramMaxTextScale` 1.3, `terminalMaxTextScale` 2,
`terminalKeyMaxTextScale` 1.3, `maxIconScale` 1.5 (icons grow with text, capped), `KitWheel.maxTextScale`
1.5, and `capScale(max: 1.5)` on the Chats home title, the hero question and the server pill in the top bar
("a page title never breaks mid-word: past 1.5x it stops growing"; the sidebar variant is unclamped). The
written standard (A11Y-8) says the top-bar title is never clamped below 2.0 - code and standard differ per
part, so re-derive each clamp from the owner's 1.3x/full split below rather than copying numbers. **Owner decision 2026-10-31 (memory `text-scale-cap`,
branch `fix/large-text-2026-10-31`, NOT in this checkout):** app frame text (bars, chips, composer,
buttons, sheet headers) never scales above **1.3x**; reading text (messages, replies, settings rows, help)
follows the system size fully; implemented in kit parts with a clamped scaler that keeps Android's
nonlinear font scaling, TalkBack labels unchanged, no mid-word ellipsis on chips at 2x in en/ar. Stance:
PORT with the 1.3x frame / full reading split as the baseline. Compose note: use `LocalDensity.fontScale`
and `Density(density, fontScale = min(fontScale, cap))` locally for chrome; Android 14+ nonlinear scaling
means a plain linear clamp changes proportions - use the platform's `TypedValue.applyDimension`-style
scaling or `FontScaleConverter` equivalents (verify API in compileSdk 37).

**DESKTOP-WEB-CROSS-28 - Reduced motion, contrast, platform focus ring, haptics.**
- `KitMotion.reduced(context)` = system "remove animations" (`MediaQuery.disableAnimations`) OR the app's
  Animations: Off; **the system setting always wins** over `oc.effectsMotion` (`full|calm|off`). `travels`
  (small changes may move) only under Full; `loopsIn` (ambient loops) only under Full. Durations: quick
  150 ms, standard 250 ms. `disableAnimationsOf` is read only inside `kit_motion.dart` (gate G21).
- High contrast: theme pack `highContrast` (one of 31 packs; `oc.themePack`), `MediaQuery.highContrast`
  drops decorative layers (`KitFrost` returns null under saver, reduced motion or high contrast; icon
  duotone backgrounds dropped; `effectiveStyle` falls back to a style that "keeps under high contrast";
  Fine-tune panel shows `heldByContrast`). Saver appearance (`oc.appearance=saver`) forces dark, no
  effects. Contrast guard in `ThemeRoles` (see ui-kit UI-KIT-01).
- Platform focus highlight is turned off in `LaunchTheme`/`NormalTheme` (day and night `styles.xml`,
  `android:defaultFocusHighlightEnabled=false`) because after a cold relaunch a thin lime outline framed
  the whole app whenever the window left touch mode (key press, D-pad, `adb shell input`); Flutter paints
  its own focus ring only for keyboard navigation (`android_focus_highlight_contract_test`).
- Haptics: `KitHaptics.enabled` (Settings > Effects > Vibration) is honoured even for calls without a
  context (a send from a controller). See ui-kit UI-KIT-09.
Stance: PORT; the Android native theme item is already native (keep both styles.xml entries in the new
project).

**DESKTOP-WEB-CROSS-29 - Gesture twins.** `docs/design/ui-ledger/gesture-audit.{md,json}`: 101 ledger
gestures each mapped to a visible control (commit `b2cf153f8` closed two: Files row actions button for
Attach / Add reference / Open review / Copy path - previously only behind long press or right click - and
three unlisted shortcuts). `test/gesture_audit_test.dart` fails when a ledger gesture has no answer.
Teaching of hidden gestures is a separate owner rule (tips, tour; ui-kit UI-KIT-20). Stance: PORT the audit
as a checklist; every new Compose gesture gets a twin or a `customActions` semantic.

**DESKTOP-WEB-CROSS-30 - Terminal accessible mode.** `terminal_surface.dart`: a screen reader cannot read a
character grid, so the terminal view is `ExcludeSemantics` inside a labelled container and the toolbar
offers an **accessible mode** (`terminal-accessible-mode`): a plain-text transcript (ANSI/OSC stripped by
`_transcriptSanitizer`, capped 100,000 characters) plus a text field that sends `value + "\r"`.
`terminal_accessibility_test` covers it. Stance: PORT (keep as a toggle; with Compose consider
exposing the transcript as the semantics tree's live region of the last N lines).

**DESKTOP-WEB-CROSS-31 - RTL, Arabic face, bidi isolates.** Locales `en, ar, es, ja, pt, ru, zh`
(`oc.appLocale`; resolution must walk the device language list - Flutter's own fallback would pick
Arabic as the alphabetically first supported locale). `AppTheme.forLocale` swaps Arabic to the platform
`sans-serif` with fallbacks `Noto Sans Arabic`, `Noto Naskh Arabic`, `Arial` and **zero letter spacing**
(preserves connected shaping); directional shadows and drop highlights use `PositionedDirectional`/
`EdgeInsetsDirectional`; `KitBidi` wraps technical values (paths, commands, URLs, hashes: `ltr`; names that
may be either script: `auto` via FSI) in Unicode isolates (LRI U+2066 / FSI U+2068 ... PDI U+2069) so an
Arabic sentence never reorders their slashes and dots and the value never reorders the sentence; terminal
and code blocks are forced LTR. Pseudo-localisation (+40 %) tests catch mid-word cuts. The Arabic privacy
text is a separate asset `assets/l10n/PRIVACY.ar.md`. Stance: PORT (Compose handles layout direction;
the isolates and the Arabic font rule are explicit work). `l10n` has its own catalog area.

**DESKTOP-WEB-CROSS-32 - Accessible-navigation-aware transient UI.** One-shot status notices disappear
after `KitMotion.undoWindow` **unless** `MediaQuery.accessibleNavigationOf` is true, in which case they wait
for Dismiss (`main.dart::_showNotice`); `showKitUndo` never auto-dismisses under accessible navigation
(shows a Dismiss action instead); the agent sheet skips auto-advance under it; the demo screen skips its
auto-play. Android signal: touch-exploration / accessibility services enabled. Stance: PORT.

**DESKTOP-WEB-CROSS-33 - Accessibility of native surfaces.** Widgets: decorative dots/marks are
`importantForAccessibility="no"` (layouts `widget_card`, `widget_card_row`, `widget_status`,
`widget_status_line`, `widget_sessions_strip`), rows carry `contentDescription` via
`SessionWidgetRowEnv`, `SessionWidgetCardBinder`, `SessionWidgetStripBinder`, `ConversationsWidgetBinder`
(a Chronometer ignores content descriptions: the "spoken age" fix was reverted, 2026-10-28). Notifications
are `VISIBILITY_PRIVATE` with a count-only public version (DESKTOP-WEB-CROSS-45). Quick Settings tiles have
`android:label`. Stance: PORT (widgets are another map's area; this is the a11y checklist for them).

### 3.E Security invariants

**DESKTOP-WEB-CROSS-34 - External link gate.** `lib/ui/widgets/external_link.dart` (276 lines). Entry
`openExternalLink(context, value, {launcher})` -> `ExternalLinkOutcome {blocked, cancelled, opened,
noHandler, failed}`. `safeExternalLinkUri(value)`: trim; reject empty or **> 2048 chars**; `Uri.tryParse`;
scheme (case-folded) must be `https` or `http`; host required; **userinfo refused**; scheme normalised so
`HTTPS://` cannot present as something the policy never inspected. Flow: refused -> modal alert (title/body
fixed, **the refused value is not echoed**); https -> `showKitConfirm` naming the host (`host` or
`host:port`, run through `KitRedact`), Copy-link alternative (copies the **masked** address), the full
address under Details; http -> separate "insecure" confirm in the destructive kind (the risky choice is
error-toned, Enter never confirms it, cancel label "Don't open"), consequence line "HTTP is not encrypted -
other devices on the network may see it". Before display/copy the address is masked: query and fragment
parameters named `code`, `state`, `session_state`, `code_verifier`, or anything `KitRedact` flags, become
`•••` (also `#/callback?code=` SPA fragments; malformed percent-encoding is masked wholesale); the
**original URI alone** goes to `launchUrl(mode: externalApplication)`. Failures: "no app could open this
link" alert / mapped error alert, both with the masked address under Details.
`openAgentSignInPage(context, value)`: if (and only if) the value passes the policy AND is exactly the
verified Claude authorization URL (`AgentAuthorizationUrl.validate('claude', ...)`: `https://claude.com/cai/oauth/authorize`,
no port/userinfo/fragment) it launches **without** a confirmation sheet (the destination is fixed and
known, so no sheet-on-sheet); anything else takes the confirmed path. First-party links (bug report repo
`https://github.com/Eslamasabry/opencode-mobile-next`, desktop release page, Termux F-Droid, Tailscale Play
page and docs) also pass through it - there are 30 `openExternalLink(` call sites and `url_launcher` is
imported nowhere else in `lib/`. Callers: markdown links in agent output, OpenCode 2 external form
fields (the original motive: a server put `intent:`/`file:`/`content:`/`data:`/`javascript:` behind a card
labelled "Opens in your browser", commit `7836a6d4b`), update notices, OAuth/authorization launches, the
bug-report GitHub form, preview "Open in browser". **Ratchet G2**: `launchUrl(` appears only in this file
(`kit_ratchet_baseline.json`). Tests: `external_link_test` (8 + 70). Stance: PORT as a commonMain policy
function (pure; `expect fun openUrl`) plus the Compose confirm; port the vector table. Android's
`Intent.ACTION_VIEW` must be the only launcher and must never receive a URL that skipped the policy; the
policy's deliberate gap is that **non-http(s) deep links the app itself authors** (`opencode-mobile://`, Termux
and Tailscale package intents) use their own bridges, not this gate.

**DESKTOP-WEB-CROSS-35 - Per-profile key convention and deletion sweep.** Contract: every per-server
preference is named `oc.<what>.<profileId>` (secure storage: `pw.<id>`, `oc.codexToken.<id>`,
`oc.teamEngineAuth.<id>`, `oc.quotaToken.<id>`, `oc.githubToken.<id>`, FactsBook and BYO secrets; see
state-data section 4.3). `ProfileStore.profileScopedPreferenceKeys(id)` (`profiles.dart:603`) matches any
`oc.*` key where `key.endsWith('.<id>') || key.contains('.<id>.')`. Profile ids never contain `.`
(digits/UUID/`[A-Za-z0-9_-]`) so one id cannot be a dotted segment of another - **keep that property for any
new id scheme and never use `app` as an id** (`oc.draft.<target>.app` is app-wide:
`KitDraft.appWide = 'app'`; every app-wide draft target is listed with its reason in
`test/kit/kit_draft_manifest_test.dart`). Non-profile keys that must never match: `oc.profiles`,
`oc.activeProfile`, `oc.offlineQueue`, `oc.sessionDrafts`, `oc.widgetSessions`, appearance/theme/effects.
`settings_preferences_coverage_test` classifies **every stored key** (none stale; the sweep takes every
per-server shape). The deletion transaction (`ConnectionController.deleteProfileAndLocalData` ->
`connection/deletion.dart`, phases 0-2, restore-on-failure semantics, "server kept" when anything could not
be deleted) is described step by step in state-data section 5 - cross-reference, do not re-derive. Shared
blobs (queue, drafts, stash) need explicit rewrite steps. Stance: PORT; the native persistence layer should
expose `deleteProfile(id)` over typed stores and keep a test that fails when a store is not covered.

**DESKTOP-WEB-CROSS-36 - `KitRedact` credential redaction.** `lib/ui/kit/kit_redact.dart` (411 lines).
Display/copy/log-safe masking; mask `•••`; where a key prefix is public it stays as a hint
(`sk-ant-•••`, `ghp_•••`, `AIza•••`). **Registry:** `registerKnownSecret(value)` (ignored if < 6 chars;
not trimmed; kept sorted longest-first so a long secret masks before its substrings),
`registerCredentialValues(json)` (walks a trusted credential/config response and registers fields named
`key, apikey, password, passwd, secret, clientsecret, token, accesstoken, refreshtoken, access, refresh,
authorization, proxyauthorization, xapikey, cookie, setcookie` after stripping `-`/`_`; for `authorization`
also registers the part after the first space), `clearKnownSecrets()` (tests; the app must re-register what
is still loaded). Registration is process-local and **at trusted ingress, before model decoding or
diagnostic capture** (commit `509c8cd0d`): profile load/save, server probe (password), pairing, GitHub
device flow (device code, tokens, refresh), quota client, setup config revisions, phone-engine bearer, and
any `KitField` marked secret (on edit and on submit). **Patterns, in order:** registered exact values;
PEM private-key blocks incl. markers; `Authorization / Proxy-Authorization / Cookie / Set-Cookie /
x-api-key` header values (line-start allowing indentation, or an explicitly quoted header incl. `curl -H
"Authorization: …"`; through the closing quote or end of line; a quoted key with an unquoted value
`"Authorization": null` to the next `, } ]` or newline); `Bearer <token>`; named values (`api_key, apikey,
token, secret, password, passwd`, tails like `client_secret`, UPPER_SNAKE `*_KEY|_SECRET|_TOKEN|_PASSWORD`,
`access_key, secret_key, private_key`) followed by `=` or `:` - a quoted value to its closing quote
(escaped quotes and embedded delimiters included, even across lines; a serialized-string form with a
backslash run), an unquoted one up to whitespace or `"',;&#<>` or `*/`; **a name inside a file path
(`/tmp/token=cache/x`) is not a credential, but after `?`, `&`, `#` of a URL it is**; declaration sites such
as `final token = fetch(...)` are not masked; URL user-info (`postgres://user:pass@host` ->
`postgres://•••@host`, also protocol-relative `//u:p@h`); provider keys (`sk-ant-`, `sk-proj-`, `sk-` >= 16,
`AIza`+30, `github_pat_`, `gh[pousr]_`, `xox[abpr]-`); JWTs (three base64url segments whose first two decode
to JSON objects, or both start `eyJ`; retried inside a rejected candidate because claims can be a header).
`opaqueTokens(s)` (separate, for text that leaves the phone): any 32+ run of `[A-Za-z0-9_+./=-]` that
contains a digit -> `•••`; long letters-only runs stay (they are code names a diagnostic needs, e.g.
`ForegroundServiceDidNotStartInTimeException` - the filter once hid this crash name, commit `532b34a5a`).
`containsSecret(s)` = `text(s) != s`. **Never touched:** file paths, git SHAs, UUIDs, ordinary long words;
JSON stays valid. Fix chronology: quoted/multi-line values (`45aee4e7e`), protocol-relative and
header-delimited URL credentials, escaped-quote delimiters (`6275970c1`), whole `Authorization` headers and
any-JWT (`2fe4160b1`), registration at ingress (`509c8cd0d`), opaque tokens in saved reports (`8fcced24a`).
Companion `KitCopy.copy(context, text, redact:, announcement:)` masks by default and has a reviewed
`redact:false` escape hatch for the person's own words (SEC-13); `redaction_test:852` reviews every verbatim
copy in the app and ratchets the reviewed list down. Tests: `kit/kit_redact_test`, `redaction_test` (419:
the fixtures are not vacuous), `credential_ingress_redaction_test` (a credential loaded from storage is masked
in persisted diagnostics; a new/updated one is registered **before** it is stored or used, even if secure
storage then fails), `diag_privacy_test` (marker-secret gate over diagnostics copy/JSON, saved report,
Report-a-problem text/link/clipboard, failed-job reports, timing report, device log), `settings_secrets_coverage_test`
(a server config screen shows none of its secrets, a changed secret is masked by the name it sits under).
Stance: PORT as `commonMain` `object Redactor` with a thread-safe registry and the same vector fixtures;
mark the secret type in the data model (`Secret` value class whose `toString()` is masked) so a secret
cannot be logged by accident - this is the cheapest upgrade over pattern matching.

**DESKTOP-WEB-CROSS-37 - The other scrubbers (consolidate).** Each is a separate regex set today:
1. `AppDiagnosticsController.sanitize` (`app_diagnostics.dart`): `KitRedact.text`, then every `http(s)://`
   URL is rebuilt as scheme+host+port+path (query, fragment, userinfo dropped; unparseable -> `[REDACTED URL]`),
   `authorization|proxy-authorization` lines -> `…: [REDACTED]`, a second named-value pass
   (`api-key|token|password|passwd|secret|access-token|refresh-token`), 32+ digit-bearing runs ->
   `[REDACTED]`; limits message 2000, stack 12000, source 80, then `… [truncated]`.
2. `PerfTrace.scrub` (`perf_trace.dart`): attribute **keys** matching
   `auth|token|password|passwd|secret|cookie|api[-_]?key|credential` make the value `[redacted]`;
   values are `KitRedact`-ed, whitespace-collapsed, `//user@` -> `//[redacted]@`, 32+ token-like runs ->
   `[redacted]`, limit 80; step names also pass `KitRedact.opaqueTokens`; request paths become templates
   (`/session/:id/message`): ids (`^[a-z]{2,5}_[A-Za-z0-9]{6,}$`, UUID, digits, 12+ hex, percent-encoded,
   20+ chars with a digit) -> `:id`, query dropped.
3. `ReportProblem._text` (`report_problem.dart`): `opaqueTokens(KitRedact.text(x))` then truncate **by runes**
   (never a half surrogate); limits source 128, message 2048, stack 8192; static `notificationText`
   (300) and `logText` (2048) for the notification and log boundaries. **Redact before truncate** (a
   truncated secret is not recognisable).
4. `problemReportScrub` (`feedback/problem_report.dart`): `KitRedact`, then `scheme://host[:port]` ->
   `scheme://[server]` (path kept - it says which call failed), IPv4 `a.b.c.d[:port]` -> `[address]`, 32+
   `[A-Za-z0-9_-]` runs -> `•••` - because the report becomes a **public GitHub issue**.
5. `redactForNotification` (`background/notification_text.dart`): its own pattern table (bearer/basic,
   `key=value` names, `--password` style flags, `user:pass@`, `sk-/pk-/rk-`, `gh*_`, `xox*`, `AKIA…`, `AIza…`,
   any 32+ run) -> `•••`, one line, whitespace collapsed, default 140 chars, ellipsis.
6. `FailedJobReport._clean`: strips OSC and CSI escape sequences and `\r`, then `opaqueTokens(KitRedact)`;
   redacts the **whole multiline log before** taking the last 120 lines / 16,384 chars.
7. `PairingPayload` printing/errors: password redacted, URL credentials and query secrets stripped,
   untrusted usernames not echoed, per-address failure reasons contain no probe text.
8. Kotlin `NativeCrashStore`: not a scrubber - **no free text is stored at all** (fixed classes + fixed
   messages).
Stance: REBUILD into one `Redactor` with named profiles (`Display`, `Diagnostics`, `PublicReport`,
`Notification`, `Log`) over shared primitives and one vector file; add property tests "idempotent",
"redact-before-truncate", "never changes JSON validity" (kit_redact_test already asserts these three).

**DESKTOP-WEB-CROSS-38 - Fail-closed persistence and "no raw errors".** (a) Stores that hold consents,
automation policy, handoff links, queue/stash and similar **refuse to write redaction-unstable content**
(`KitRedact.text(x) != x`) and treat unreadable data as "no grant" (fail closed): see state-data
STATE-DATA-35. Three-state semantics (absent / false / corrupt) differ per store and must be preserved.
(b) The no-raw-errors owner rule: server/native/exception text is never copy. Mapping layer:
`domain/product_failure.dart`, `ui/widgets/product_states.dart::productErrorText`, `agent_error_words`,
`send_failure_words`, `connection_failure`, `setup_ui_messages`, `recent_error_words`; technical text goes
only under a **Details** fold, redacted (`KitTechnicalValue`, `KitDetailsFold`). Tests: `no_raw_error_text_test`
(the domain mapper never promotes protocol text; the allowlist of verbatim exceptions only shrinks),
`product_error_text_test` (502 and other statuses in words with raw text only in Details; socket errors
collapse to one connectivity line; invalid-state errors do not blame the network; keyring failure and
storage failure have their own words), `audit2_product_error_mapping_test` (OpenCode 2 operation errors keep
server prose only inside a redacted Details section). Stance: PORT (sealed `UserFacingFailure` hierarchy
whose `message` is words and whose `technical` is a `Redacted` type).

**DESKTOP-WEB-CROSS-39 - Transport trust (cross-reference, invariants only).** Owned by protocols
PROTOCOLS-01/02. Invariants to carry: only **loopback** HTTP needs no consent; every other `http://`
origin (private, Tailscale `100.64.0.0/10` and `*.ts.net`, public) needs per-profile, **origin-bound**
consent (`oc.cleartextOk.<id>` stores the exact origin incl. port; changing origin asks again; deleting the
profile removes it); private ranges (10/8, 172.16/12, 192.168/16, 169.254/16, IPv6 fc00::/7 and fe80::/10,
`*.local`) and Tailscale get softer warning copy, **not** an exemption; credentials/query/fragment/path
secrets in a server URL are rejected; no failed HTTPS ever silently downgrades; OpenCode 2 clients refuse
redirects (`followRedirects:false` in `api2/transport`, `paseo/transport`, `termux_running_server`,
`phone_engine_gateway`) so credentials cannot cross origins; self-signed HTTPS uses a person-approved SHA-256
leaf pin (`oc.certificatePin.<id>`, 64 hex, colon formatting accepted, bound to profile + exact HTTPS
origin, inspected **before** any HTTP/auth, expired matching cert and untrusted TLS are distinct errors,
removed on origin change or profile delete, a pin cannot be downgraded to HTTP); Codex and Paseo stay
HTTPS/wss-only off the device; pairing over private http is held (never probed) until confirmed.
**Android's static `network_security_config.xml` sets `<base-config cleartextTrafficPermitted="true"/>`
(demo lesson: it cannot express 100.64.0.0/10 or runtime origins), so the Dart/Kotlin policy is the only
boundary - a new HTTP client that bypasses it silently weakens the app.** The manifest also says
`usesCleartextTraffic="false"`; the network-security-config wins on API 24+. Stance: PORT in `commonMain`
(a `TrustPolicy` the single HTTP client factory enforces; a lint/test that no other `HttpClient` is
constructed).

**DESKTOP-WEB-CROSS-40 - Android manifest and backup posture.** `AndroidManifest.xml`: permissions
`INTERNET, ACCESS_NETWORK_STATE, RECORD_AUDIO, POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS (Android 16
live updates), FOREGROUND_SERVICE (+DATA_SYNC, +SPECIAL_USE), REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
WAKE_LOCK, CAMERA, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, MANAGE_EXTERNAL_STORAGE (lazy; plus
READ_EXTERNAL_STORAGE <= 32, WRITE_EXTERNAL_STORAGE <= 29), com.termux.permission.RUN_COMMAND`;
`uses-feature` microphone and camera `required=false`; `<queries>` for TTS service, Termux, Tailscale,
`PROCESS_TEXT`. `allowBackup=false` + `fullBackupContent` and `dataExtractionRules` (cloud-backup **and**
device-transfer) excluding every domain (root, file, database, sharedpref, external, device_*): profiles and
encrypted credentials must never leave the device. Exported components (everything else
`exported=false`): `MainActivity` (launcher; `SEND text/plain` share target; VIEW for
`opencode-mobile://session` and `://team`), `ManageSpaceActivity` (reads no extras),
`WidgetConfigureActivity`, the 7 widget providers (`SessionsWidgetProvider`, `SessionsWideWidgetProvider`,
`SessionsListWidgetProvider`, `SessionsStripWidgetProvider`, `UsageWidgetProvider`, `UsageWideWidgetProvider`,
`StatusWidgetProvider`; `APPWIDGET_UPDATE`), 2 Quick Settings tile services (`AttentionTileService`,
`PauseTileService`; protected by `BIND_QUICK_SETTINGS_TILE`). Non-exported: `CodingActionReceiver`,
`StatusActionReceiver`, `LivePauseReceiver`, `WidgetActionReceiver`, `ScheduledPromptReceiver`,
`BuiltinRestoreReceiver`, `BuiltinIdleReceiver`, `WidgetRouteActivity`, `WidgetOverlayActivity`, and every
other service (`TermuxResultService`, `BuiltinServerService`, `SetupService`, `BackgroundConnectionService`);
`LocalPdfService` runs `isolatedProcess=true` in process `:local_pdf`. Foreground
service types: `BackgroundConnectionService` `dataSync|shortService`; `BuiltinServerService` and
`SetupService` `specialUse` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` text. `enableOnBackInvokedCallback=true`
(predictive back). The activity's `configChanges` list includes `layoutDirection|fontScale|locale|uiMode`
so the app handles those without recreation. Threat-model mitigations tied to this file: notification
actions through a non-exported receiver with immutable `PendingIntent`s (reply is mutable for
`RemoteInput`); **Allow once** sets `setAuthenticationRequired(true)` on Android 12+; Always allow is
never a notification action; channels use `VISIBILITY_PRIVATE`. Stance: PORT. These are the native
project's baseline; add a manifest-lint test that asserts the exported set equals this list.

**DESKTOP-WEB-CROSS-41 - In-app preview WebView hardening.** `lib/ui/kit/kit_web_preview.dart` (269 lines),
`lib/domain/dev_preview.dart` (231 lines), `webview_flutter` + `webview_flutter_android`. Platform support:
Android and iOS only. Policy: the preview may load only the **same host and port** as the origin the person
asked for (`devPreviewMayLoad`), never a non-http(s) scheme, never a URL with embedded credentials
(`dev_preview_test:154`); a navigation to anywhere else does not load - only a **main-frame** navigation
(a person's) calls `onLeave` (which hands the URL to `openExternalLink`), sub-frames and scripts asking for
another address are silently refused. Hardening: `clearCache`, `clearLocalStorage`, `clearCookies` before
every load; Android: file access off, content access off, geolocation off, third-party cookies off;
JavaScript on (dev servers need it). `DevPreviewLink.parse` recognises only `localhost`, `127.0.0.1`,
`0.0.0.0`, `::1` with an explicit port 1-65535, scheme http/https exactly as written, no userinfo, <= 2048
chars; `scan` finds them in transcript text (trailing punctuation trimmed); targets are rewritten to the
phone's loopback (own server) or the remote server's host (`DevPreviewRoute.phone|remote|blocked`).
Documented exception to "every URL through `openExternalLink`" (screens-nav SCREENS-NAV-58). Stance: PORT
(Android `WebView` with `WebViewClient.shouldOverrideUrlLoading` mirroring the rule; also disable
`allowFileAccessFromFileURLs`, safe browsing as the platform default).

**DESKTOP-WEB-CROSS-42 - Wi-Fi file share server.** `lib/platform/wifi_file_share.dart` (296 lines, new
2026-11-01, `1be94da76`), pure Dart `HttpServer`. Security shape: bound to the phone's **Wi-Fi IPv4 only** -
never `0.0.0.0`, never mobile data (interfaces must start `wlan`, `swlan`, `ap`; `rmnet*`, `ccmni*`, VPNs never
qualify) and the address must be private LAN (10/8, 172.16/12, 192.168/16); the only reachable URL is
`/<token>/<name>` with a 192-bit `Random.secure()` base64url token, compared in constant time; every other
path 404; only GET and HEAD (405 + `Allow`); streams the file; `Content-Disposition: attachment` with
sanitised name (`[\x00-\x1F\x7F"]` -> `_`, `filename*=UTF-8''`), `X-Content-Type-Options: nosniff`,
`Cache-Control: no-store`; lifetime 15 minutes, ends on `stop`, on a new share, and when the app is detached;
**private app storage is refused** (`FileShareFailure.privateFile`: resolves symlinks, compares against
application support and documents directories); download counter updates state. UI: QR code or link
sheet. Plain HTTP on a LAN by design (a one-time unguessable URL, short lifetime, read-only). Stance: PORT
(Ktor CIO/Netty server or `ServerSocket`; keep every rule; NetworkCallback to bind only to the Wi-Fi
`Network`).

**DESKTOP-WEB-CROSS-43 - Supply-chain and repository hygiene gates.** Tests that protect the public tree
and what ships: `repository_hygiene_test` (no `HANDOFF.md`/internal log; CONTRIBUTING facts; `.claude/skills/`
is git-ignored third-party content; every bundled third-party component has a license text under
`LICENSES/`; no keystores or signing secrets committed), `shipped_download_urls_test` (issue #87: the AI Team
manifest once shipped with the owner's Tailscale address as its download server - every text asset is scanned
for URLs to loopback, `.local/.lan/.ts.net/.internal/.home.arpa`, 10/8, 172.16/12, 192.168/16, 100.64/10,
link-local, unique-local IPv6, and download manifests must be https), `host_script_pin_test` (host scripts are
pinned by hash), `release_blockers_test` (release URL policy, IPv6 loopback), `release_metadata_test`,
CI `tool/release` unit tests, `kit_ratchet_test` (G2 launchUrl, G7, G15x, G17, G21, G48), APK size-growth gate and
startup budget script. Pinned downloads: `assets/aiteam/manifest*.json`, `assets/agents/paseo-package-lock.json`,
Rust engine `Cargo.lock` + executable SHA-256 manifest verified at native launch, voice model manifest.
Signing: never rotate or substitute a signer (stable CI `2D010C21…D0EC`, local release key `1DE5BF08…D60C`);
repository rule "no tags, publishes or signing secrets without explicit approval". Stance: PORT the tests as
Gradle verification tasks; keep "no private addresses in shipped assets".

**DESKTOP-WEB-CROSS-44 - Approval authentication, biometric, no app lock.** There is **no app lock** and no
per-action authentication inside the app (threat-model gap #1); anyone holding the unlocked phone can
approve pending permissions on every saved server. Mitigations that exist: Keystore-backed passwords;
backup disabled; **Allow once** on a notification requires device authentication on Android 12+; Deny and the
quick Reply do not (gap #2: on OpenCode 2 a permission Reply is a reject with a message the model reads, so
a locked-phone attacker could inject instructions); per-session auto-approval never replies "always" and is
off by default (`AutoApprovalMode.ask`, gated by `automationPolicy.allowsAutoApproval`). `local_auth` 3.0.2 is
used only by FactsBook's `device_authenticator.dart` (developer flag `oc.dev.factsbook`; "device
authentication before signing"). Stance: DECIDE. A native rewrite makes an optional app lock
(`BiometricPrompt`, per-action step-up for "Always allow"/auto-approve) cheap; the owner has not asked for it
- record it as an option, not scope.

**DESKTOP-WEB-CROSS-45 - Notification and lock-screen privacy.** Coding-alert and status notifications show
**no prompt text, tool input, file names, session titles or server error details on the lock screen** (public
version = count only); the shade (unlocked) may show a command or the first line of a reply, secrets masked by
`redactForNotification` (140 chars, one line, ellipsis) - `notification_text_test`: ordinary commands stay
whole, bearer tokens / provider keys / passwords / URL credentials (host kept) / long token runs masked, key
names kept. Channels `VISIBILITY_PRIVATE`; widgets and the tile show titles/counts only ("never prompt text,
tool input, file names or server addresses"; removing a server clears them; disconnecting withdraws the
long-press entries and the tile count; session titles on a locked home screen are a known, documented leak by
widget design). `ReportProblem.notificationText` (300 chars) is the diagnostic-notification boundary.
Native: `StatusNotificationRenderer`, `BackgroundConnectionService.codingAlertActions`,
`NativeNotificationLocale` (notification language follows `oc.appLocale`). Stance: PORT; a Kotlin unit test
over the same vectors.

### 3.F Privacy

**DESKTOP-WEB-CROSS-46 - No-analytics policy and the Aptabase plan.** Fact: no analytics, advertising,
crash-reporting or A/B SDK in `pubspec.yaml`, `build.gradle.kts` or manifest (grep for
aptabase/firebase/crashlytics/sentry/telemetry in `lib/` finds only unrelated words in FactsBook outbox, kit
copy, l10n). Policy text: "does not provide a developer-operated account, advertising, analytics, or
crash-reporting service. The app does not sell personal data." `docs/aptabase-telemetry-plan.md` (2026-08-31)
proposes optional, anonymous, **opt-in, default-off** usage stats on Aptabase (`aptabase_flutter` 0.5.0, MIT;
self-hosted `A-SH-*` key recommended): lazy init only after enablement, app key via `--dart-define`
(feature compiled inert and Settings entry hidden when absent), exactly four events (`app_open`,
`server_connected{stack}`, `feature_used{feature}`, `telemetry_disabled`), props are coarse enums behind an
in-code allowlist, no identifiers, no content, a release-gate wire-capture audit under `docs/audits/`.
Owner has not approved it; it is not built. Stance: DECIDE. If wanted in the native app: same four
principles; implement as a `TelemetryBackend` interface with a no-op default so a build without a key links
no network code.

**DESKTOP-WEB-CROSS-47 - Network egress inventory.** Every destination the app itself contacts, other than
servers the person chose (OpenCode 1/2, Paseo, Codex, ACP, Gas City supervisor, quota collector at the
saved server's exact origin):

| Destination | Why | Initiated by | Data sent |
|---|---|---|---|
| `api.github.com/repos/Eslamasabry/opencode-mobile-next/releases` | desktop update check | Linux/Windows only | IP, `Accept` header |
| `github.com/Eslamasabry/opencode-mobile-next/issues/new?template=bug_report.yml&...` | Report a problem | person's tap -> browser via `openExternalLink`; GitHub form is the person's to submit | scrubbed report text in the URL (<= 2048 chars) or clipboard |
| `github.com/login/*`, `api.github.com` | Git page device-flow sign-in | person | public OAuth client id, device code |
| Shorebird servers | code-push check/download (Android) | app, automatic when policy allows | IP + app/update identifiers (PRIVACY.md) |
| `models.dev/logos/<provider>.svg` | provider logo for providers without a bundled mark | app | provider name + IP; cached on disk, refreshed <= weekly; monogram on failure |
| `huggingface.co` (voice model manifests) | voice model packs and optional natural voice | person's download choice; mobile-data downloads gated by `oc.mobileDownloadConsent.<id>` | IP |
| `registry.npmjs.org`, `nodejs.org`, `downloads.claude.ai`, `cdimage.ubuntu.com`, `packages.termux.dev`, Ubuntu mirrors | installs inside the built-in Linux / Termux setup (agents, Node, base system) | person's setup action, in a controlled script with pinned versions/hashes | IP, package names |
| `registry.modelcontextprotocol.io` | MCP catalog search | person | query |
| `auth.openai.com`, `claude.com/cai/oauth/authorize`, provider sign-in pages | agent/provider sign-in | person, in the browser | n/a (browser) |
| Tailscale, F-Droid, Play pages | install help links | person, through `openExternalLink` | n/a |

Nothing else; no ad network, no analytics, no fonts/CDN (Geist and Phosphor are bundled). Diagnostics are
never uploaded automatically. Stance: PORT; keep this table as a CI check (grep the project for hostnames not
on the allowlist).

**DESKTOP-WEB-CROSS-48 - Consent inventory.** Each is a privacy decision the person made once and the rewrite
must keep, with its storage (details in state-data STATE-DATA-35 and section 4):

| Consent | Default | Storage | Notes |
|---|---|---|---|
| Plain-HTTP to a non-loopback origin | off | `oc.cleartextOk.<id>` (exact origin) | per profile; origin change re-asks |
| Self-signed certificate pin | none | `oc.certificatePin.<id>` | per profile + origin |
| Saved crash reports (Flutter + native categories) | **off** | file `crash-diagnostics-consent` (epoch ms) in app files dir | shared by Dart and Kotlin; fails closed |
| Share which sessions I viewed with the server | per-profile default migrated | `oc.shareSessionViews.<id>` (+ Origin, Default, `oc.readStateConsentMigration.v1`) | invalid legacy never grants |
| Read-aloud use of the system speech engine | off | `oc.readAloudConsent` | prose is sent to the separately installed TTS engine |
| Voice-model download on mobile data | ask | `oc.mobileDownloadConsent.<id>` | |
| In-flow consents (phone server first start, notification question, ...) | ask once | `oc.inFlowConsent.<id>` | |
| "Always allow this" invitation history | | `oc.permissionConsent.<id>` | third identical request offer |
| Notify me when the agent needs me? | asked once after first reply | `oc.firstRun.notifyAsk` | both answers final |
| Quota monitoring / alerts / Wi-Fi-only / quiet hours | off | `oc.quotaMonitor.<id>`, `oc.notify.*` | per-screen-visit quota consent is memory-only |
| Automation behaviours needing consent (restart dev services, stop idle helpers, clean caches, update when idle, monitor other servers, monitor quota) | off | `oc.automation.<id>` | missing record = defaults; corrupt = all off |
| Battery-optimisation exemption, All files access, notifications, mic, camera | asked at the moment of need | OS | never at start-up |
| Wi-Fi file share | per share | none (15-minute lifetime) | |
| Telemetry | n/a (does not exist) | | see 46 |

Stance: PORT. Consent-once prompts need a single `ConsentStore` abstraction with the three-state read.

**DESKTOP-WEB-CROSS-49 - Privacy documents in the app, and their drift.** `PRIVACY.md` is a bundled asset
(`pubspec.yaml` assets) shown by the About/Privacy screen (`assets/l10n/PRIVACY.ar.md` for Arabic);
`THIRD_PARTY_NOTICES.md` + `LICENSES/` (20 license texts incl. OFL-1.1-Geist, MIT-Phosphor, MIT-lobe-icons,
MIT-ONNX-Runtime, MIT-OpenAI-Whisper, Apache-2.0-Kokoro, Rust-Phone-Engine, BSD-3 record/scrollable-
positioned-list/vector-graphics, MPL-2.0 for `dbus`) are viewable in About > open-source notices; the
non-affiliation notice ("independent community project, not affiliated with the official OpenCode team") is
shown in About and repeated in README/SECURITY/PRIVACY. Sections of PRIVACY.md a rewrite must preserve
(truthfully): where your data goes (your servers/providers), plain HTTP rules (loopback; Tailscale
100.64.0.0/10 and `*.ts.net`; private ranges only after "Use it anyway"), AI Team plugin reads/writes,
data stored on device (backup disabled; not separately encrypted from the sandbox; passwords in Keystore),
widgets/shortcuts/tile contents, remaining-usage collector (consent, exact origin, no redirect, no Claude
collection), pending sign-in recovery (no URL/code/answers/tokens kept), App diagnostics, microphone and local
voice, read-aloud, files/terminal/Termux, background mode and updates (Shorebird exposes IP and app/update
ids), provider and agent logos (models.dev), permissions list, iOS source preparation. **Required fixes before
the native release:** rewrite "App diagnostics" (persisted redacted report, optional crash categories, no
send-to-server, GitHub issue link), the Shorebird paragraph (ends at the switch), the permissions list
(native app's real set), and drop the Termux-only claims if Termux goes. Stance: PORT (documents), REBUILD
(the diagnostics section).

**DESKTOP-WEB-CROSS-50 - Data removal paths.** (1) Delete a server: the sweep in DESKTOP-WEB-CROSS-35. (2) Android
App info > Storage > **Manage space** -> `ManageSpaceActivity` (own Flutter engine, entrypoint
`manageSpaceMain`, `lib/manage_space_main.dart`, bridge `ProjectExportBridge` on `oc/project_export`): page
says what clearing deletes (the in-app server, its projects and conversations), offers **Export projects
first**, "Clear the app's cache only", and "Delete everything" (confirmed, then `clearApplicationUserData`);
works whether or not the app runs; reads no extras. (3) Crash/diagnostics: turning crash capture off or
"Delete saved crash reports" erases Flutter and native evidence and clears App diagnostics (which clears the
saved problem report and timing history); Clear on the diagnostics screen erases the persisted report
(including when the in-memory list is empty). (4) Android backup disabled (DESKTOP-WEB-CROSS-40). Stance: PORT;
in the native app `ManageSpaceActivity` becomes a plain Compose activity (no second engine).

### 3.G Diagnostics, logging and crash handling

The layers, from cheapest to most durable (all local, none uploaded):

```
handled error / FlutterError / PlatformDispatcher / ErrorWidget
   -> AppDiagnosticsController (RAM, 20 entries, redacted)             [51, 52]
        |-> ReportProblemCapture -> ReportProblem file (128 / 256 KiB) [58]
        '-> CrashDiagnosticsController (opt-in, 20 records / 8 KiB)    [54]
PerfTrace spans (RAM 2000) -> device log "OCTRACE ..." + ReportProblem [59]
JVM uncaught exception -> NativeCrashStore (category only, consent)    [55]
Android ApplicationExitInfo -> exit history / launch report            [57]
```

**DESKTOP-WEB-CROSS-51 - `AppDiagnosticsController` (in-memory ring).** `lib/diagnostics/app_diagnostics.dart`
(252 lines). `record(error, stack, source, at)`: sanitises (see DESKTOP-WEB-CROSS-37 #1), **dedupes** an
entry equal in source+message+stack within 10 s of the previous one (increments `occurrences`, updates the
time), keeps the newest 20 (`maxEntries`), `notifyListeners`. `clear()` notifies even when empty (persistence
listeners must also erase a restored report). `reportJson()` =
`{"app":"opencode-mobile","privacy":"redacted-process-memory-only","entryCount","entries":[{time (UTC ISO),
source,message,stack?,occurrences?}]}` + `reportText()` (2-space pretty JSON). `appErrorCategory(error)`:
`StateError` -> "Invalid state", `ArgumentError` -> "Invalid argument", `TypeError` -> "Missing value",
`UnsupportedError` -> "Unsupported operation", else "Application error". Stance: PORT.

**DESKTOP-WEB-CROSS-52 - Global error capture and the error fallback.** `installAppErrorCapture(diagnostics,
crashCapture)` (called in `main()` before `runApp`): wraps `FlutterError.onError` (captures, then hands
**`FlutterErrorDetails` with only the category string** to the previous handler, so default console output
never prints stacks or messages), `PlatformDispatcher.onError` (captures; prints `Uncaught error: <category>`
via `debugPrintSynchronously` so release logcat shows only the category; returns true = handled), and
`ErrorWidget.builder` (captures, then draws `KitErrorFallback`). Returns a handle whose `restore()` puts the
previous hooks back (tests). Capture is `try/catch`-wrapped and never throws. `KitErrorFallback` is drawn
wherever the failed widget was, possibly above every theme/Navigator/Localizations, so it looks nothing up
that can be missing: theme roles derived from the default theme if the app's is absent, a complete text
style of its own (no yellow double-underlined fallback), English copy when localisations are missing, "Back"
only under a navigator that can pop and only when the box is at least 200x160; the technical text goes only
to App diagnostics; it is a `liveRegion`. **Consequences (owner memory 2026-10-29):** the release app keeps
no Dart stack by design, so the 4071 cold-start "Missing value" was found only with a never-shipped
diagnostics build that printed `details.exception`/`details.stack`; and the owner's phone has
`persist.log.tag=S` (logcat silent), so field debugging uses `dumpsys activity exit-info`, a UI text dump
of an error card's Details, and R8 `retrace` with the exact build's `mapping.txt`. **Compose difference:**
an exception during composition/layout/draw propagates and kills the process; there is no widget-level
fallback. The native app therefore needs (a) an app-level uncaught handler that records the category
(DESKTOP-WEB-CROSS-55), (b) crash-loop protection (see 53), (c) coroutine exception handlers on every scope
that touches the network, (d) a debug-only "stack-printing" switch that never ships enabled, and
(e) a decision about whether release builds may keep an R8-obfuscated frame list (the Flutter app already
shows "3 obfuscated frames" in the in-app server start error Details, `fix/builtin-start-error`). Stance:
REBUILD.

**DESKTOP-WEB-CROSS-53 - Bootstrap gate containment.** `main_parts/bootstrap_gate.dart` (286 lines).
`runApp(AppBootstrapGate)` paints an "Opening..." state immediately so a preferences or Keystore failure
can never leave Android on a blank native window; the first `_load` waits for a post-frame callback; load =
`PerfTrace.span('app.bootstrap', AppBootstrap.create)` then build the `ConnectionController`,
`migrateOlderDrafts()` (older drafts -> Saved prompts), `recoverPendingPhoto()` (a camera result handed back
after Android killed the app; only where `supportsPromptPhotos`), `migrateNotificationPreferences()`
(idempotent; failure leaves the legacy per-server records in charge and is recorded as `notify-migration`)
- **all before any conversation or connection is exposed**. After 8 s the state says it is still opening and
offers Try again (STATE-5, `KitStateView.since/onSlow`). Failure -> `KitStateView.error` with plain title/body,
Try again, **Start fresh** (destructive confirm: erases saved sign-ins via `AppBootstrap.resetSavedSignIns`
after closing phone-agent sign-ins first; generation counter invalidates any in-flight load so an old result
cannot mount a controller with credentials the reset is erasing), Copy details and Report a problem (the
details are `diagnostics.sanitize(error, limit: 300)`). `KitReportHook.handler` is installed at gate init, so
even a failed start can be reported. Stance: PORT (startup state machine: Opening -> Ready | Failed(retry,
start fresh); a splash that never blocks on storage).

**DESKTOP-WEB-CROSS-54 - Crash diagnostics: opt-in categories (Dart).** `lib/diagnostics/crash_diagnostics.dart`
(613 lines). **Capture is OFF until the person turns on a switch** (Report a problem > "Keep crash reports
on this phone"). Vocabularies are fixed: sources `flutter, platform, widget, native, anr`; categories
`Invalid state, Invalid argument, Missing value, Unsupported operation, Application error, Native application
error, Permission denied, Input/output failure, Interrupted operation, Android reported that the app stopped
responding`. A record is `{source, category, time(ms)}` - **no throwable value, no stack, no path**, even if a
credential was never registered. Ring 20 entries / 8,192 bytes (oldest dropped first), atomic write
(`.pending` -> rename, flushed), de-duplicated on (source, time). Consent = file `crash-diagnostics-consent`
containing the enable time in epoch ms (<= 32 bytes, must be > 0 and <= now, else disabled/fail closed);
records older than the consent time are ignored; `setEnabled(false)` erases the Flutter and native evidence
and consent immediately; `clear()` retains consent but bumps `consentEpoch` (so a historical ANR cannot
re-appear after clear + restart); both also `diagnostics.clear()` (which wipes the saved problem report).
Failed storage -> `storageFailed` (UI says so; never a success confirmation). Symlinked files are refused
(`_file()` checks `followLinks:false`). Imports: `importAndroidAnr(ms)` and `importNativeCrash(map)` accept
numeric evidence only, newer than consent. **Startup** (`CrashDiagnosticsStartup.start`, after first frame):
the launch caller waits at most **300 ms** (`launchBudget`); opening (channel `oc/crash_diagnostics open` ->
`{directory, nativeCrash, anrTimestamp}`, then a `compute()` isolate that reads consent/records and imports
native evidence) continues in the background; `ready` completes whenever opening finishes (null on failure);
a generation counter discards stale results after `resetForTesting`; restored records replay once into the
in-memory diagnostics via `Timer.run` after readiness; errors captured before opt-in are never replayed into
saved crash records. **Files and location:** on Android the bridge returns `filesDir` itself, so the four
files sit directly in `filesDir` (`crash-diagnostics-consent`, `crash-diagnostics.json`,
`crash-diagnostics.pending`, `native-last-crash.properties` + `.tmp`); on desktop they sit in
`<appSupport>/crash-diagnostics/` (note: `state-data.md` shows a `crash-diagnostics/` directory - on Android
that is not what the bridge returns). Tests: `crash_diagnostics_test` (off by default; hostile `toString` never
called; corrupt record or storage failure never throws; symlink cannot redirect; ring/size bounds; ANR/native
imports only since consent), `crash_diagnostics_startup_test` (never blocks launch; slow or never-answering
channel gives up after the budget but readiness resolves later; reset drops old replies), `crash_report_test`
(11 rows). Contract documents: `docs/design/bd7-contract.md`, `bd7-late-open-contract.md`,
`docs/privacy/bd7-crash-diagnostics.md`. Stance: PORT as a single Kotlin owner (the Dart/Kotlin split via a
shared consent file disappears), keep file formats readable by the old code only if a downgrade is a goal
(it is not: migration is one-way).

**DESKTOP-WEB-CROSS-55 - Native crash store and uncaught-exception handler.** `OcApplication.onCreate`
installs `NativeCrashStore(filesDir).install()` before activities/services (so foreground-service failures are
captured). The handler writes (only when consent is valid and `timestamp >= consent`; otherwise it **deletes**
any stored record) then calls the previous handler with a **sanitised `RuntimeException("Native application
error")` with an empty stack trace** - always, regardless of consent - so Android's logger, Android vitals
and logcat never receive an exception message, cause or stack that might hold chat text, login codes or short
tokens. Stored file `native-last-crash.properties` (java Properties; <= 24,576 bytes on read): `timestamp`,
`exceptionClass`, `message`, `frames`, then `cause.N.*` for up to 3 causes (4 summaries total, cycle-safe via an
identity set). **Allowlisted classes only**: `SecurityException` ("Permission denied"), `IOException`
("Input/output failure"), `NullPointerException` ("Missing value"), `IllegalArgumentException` ("Invalid
argument"), `IllegalStateException` ("Invalid state"), `InterruptedException` ("Interrupted operation"),
else `Throwable` ("[redacted]"); frames are **always empty** (stack symbols are not stored). Written to a
`.tmp` with owner-only permissions, `fd.sync()`, an **epoch recheck** (if consent changed while writing, the
temp is deleted) then rename; a crash in the final check/rename window can leave a category-only file that
`read()` discards while disabled and startup erases. `read()` (also feeds `oc/lifecycle launchReport.lastCrash`)
validates classes and messages against the allowlists again. Dart `NativeCrashRecord.fromMap` re-validates: class
regex `^(java|javax|android|kotlin|kotlinx|io.flutter|io.github.eslamasabry.opencode_mobile).` symbols, frame
regex, <= 24 frames, <= 3 causes x 8 frames, message must be in the fixed set else "[redacted]";
`diagnosticMessage` renders `java > lang > X: message` (dots replaced so the token scrubber does not mistake a
long class name for a credential). `ServiceDiagnostics.snapshot` (for the helper services) maps exit code 137 to
`memory_or_phantom_kill` (cannot tell memory pressure from Android's phantom-process policy). **Decision for the
rewrite:** the sanitised-throwable hand-off costs real debuggability (every crash looks the same in logcat);
keep it only if the owner wants that privacy level on a self-use app, else pass the real throwable in debug
builds and keep category-only persistence (DESKTOP-WEB-CROSS-62). Stance: PORT (with the DECIDE above).

**DESKTOP-WEB-CROSS-56 - Crash report preview and share.** `lib/diagnostics/crash_report.dart` (155 lines),
domain `crash_report.dart`, `diagnostics_error.dart`, `app_diagnostics_gateway.dart`,
`crash_report_share.dart` (UI). `CrashReportBuilder.preview()` builds from the validated snapshot only:
header "OpenCode Mobile crash report / Captured error categories and times only. / No messages, stacks or
conversations are included.", then at most **20 lines** `<ISO time> | <source> | <category>` within a
**16 KiB** budget. Errors are fixed `DiagnosticsError` values (`unavailable, invalidLimit, storageFailed,
captureDisabled, noCapture, stalePreview, shareUnavailable, resumeFailed, busy`), never text. `share(preview)`
shares exactly the previewed text through the system chooser (`ShareOut.text` -> `oc/share`; unsupported on a
platform without the channel); it re-validates that the preview is still current: same controller object, same
`consentEpoch` and `revision`, `available && enabled && !storageFailed`, else `stalePreview`; overlapping
shares -> `busy`; `opened` means the chooser opened, not that delivery happened. Sharing writes nothing
locally. Stance: PORT (system share sheet via `Intent.ACTION_SEND`; same stale-preview protocol).

**DESKTOP-WEB-CROSS-57 - Exit history and launch report.** `lib/platform/app_exit.dart` (356 lines),
`AppLifecycle.kt` (`oc/lifecycle`), `AppExitHistoryPolicy.kt`, `lib/domain/app_exit_history.dart`,
`lib/diagnostics/device_diagnostics_gateway.dart`. API 30+ `ApplicationExitInfo`. `launchReport` ->
`{exit, previousServices, lastCrash}` (the newest main-process exit **not reported before**, using prefs
`oc_lifecycle.seen_exit_timestamp`, read once per process and cached so a second engine sees the same;
`description` deliberately blank because OS crash descriptions can contain the raw exception message;
`subReason` parsed from `toString()` `subreason=N`; `previousServices` = the built-in Ubuntu service set kept on
disk with `commit()` on every change so a force stop that runs none of our code still leaves the truth).
`exitHistory{limit 1..50, default 10}` is read-only and never consumes launch-recovery state: queries **all
retained records** (`maxNum=0`) before filtering by process name so secondary processes cannot crowd the main
process out, sub-reason via a reflective hidden getter, timestamps bounded, entries carry only `{reason,
importance, timestamp, subReason, status, description=fixed category}`; Dart derives the category itself and
**ignores wire text**. Classification (`classifyAppExit`): `normal` (exit-self/unknown), `update`
(package updated/state change, or user-requested with subreason 25), `forceStop` (user requested/stopped, or
subreason 21/23/22), `lowMemory` (reason 3, subreasons 2-6, 16), `crash` (4, 5 native, 6 ANR, 7 init failure),
`killed` (anything else, "Android ended the app"); `notable` = not normal and not update. Helper-process
deaths (the phone agents' helper) are merged as `AgentHelperExit` separately from app exits
(`withAgentHelperExits` + `BuiltinLinux().performance`), described "Agent helper stopped (possible memory or
process limit)". `AppExitRecovery` (builtin-linux) turns a notable exit with services that were running into
a one-time notice and a server restart; `LifecycleReport` is the presentation-safe readiness; keep-alive advice
opens vendor auto-start pages (Nubia/RedMagic/ZTE, Xiaomi, OnePlus, Oppo/Realme, Vivo/iQOO, Huawei/Honor,
Samsung) with guarded intents (`openKeepAliveSetting`). Tests: `lifecycle_report_test` (15 rows),
`device_diagnostics_gateway_test`. Stance: PORT (builtin-linux owns the recovery side).

**DESKTOP-WEB-CROSS-58 - Persisted problem report (`ReportProblem`) and capture.**
`lib/diagnostics/report_problem.dart` (317), `report_problem_capture.dart` (117), `report_problem_startup.dart`
(142). Opened once per process after the first frame by `ReportProblemStartup.start`; the file is
`<appSupport>/diagnostics/report_problem.json` (on Android `filesDir/diagnostics/`) with a `report_problem.pending`
sibling: `{"version":1,"entries":[{kind: error|timing|androidExit|thermal, time (UTC ISO), source, message,
stack?}]}`. Limits: **128 entries, 262,144 bytes** per snapshot (each snapshot and temp file); every write
redacts (`opaqueTokens(KitRedact.text())`) **before** truncation (source 128, message 2048, stack 8192 chars,
rune-safe), commits with **synchronous flushed IO + atomic rename** ("fatal handlers cannot rely on an async
queue draining before Android exits"; process-crash durability, not power-loss), restores by discarding any
`.pending`, deleting an oversize or malformed/foreign-version file, and re-applying today's redaction and
limits to what it reads. IO errors become a fixed `StateError('Could not save the diagnostic store.')` and
`storageFailed` (never paths or raw exceptions). **Eviction rule (fix `4cbdfca3e`, 2026-10-17):** make room by
dropping the oldest *timing* first; an error, Android exit or thermal event goes only when no timing is left.
Timings arrive several times a second (each Linux status read is one), so a shared oldest-first ring lost a
failure within about a minute. `ReportProblemCapture` connects: buffered errors and the newest
`maxEntries` timings are imported once at attach (timings never replayed after Clear), live timings arrive over
the synchronous `PerfTrace.recorded` stream, error entries are re-captured when `occurrences`/time change,
thermal readings (`android.thermal`, status + headroom; unknown and repeated statuses add nothing), and typed
Android exits (`typedAndroidExits: true` skips the `android.exit` error source so an exit is not stored twice;
an exit already kept at the same timestamp is not re-added). Clearing the diagnostics controller
(Clear on the diagnostics screen) deletes the persisted report even when the in-memory list was empty. Open
failure records one fixed line (`The saved problem report is unavailable on this device.`, source
`report-problem`) in memory. The report is **never uploaded, logged or notified automatically**; its exact
snapshot (`reportJson()`: `app: opencode-mobile`, `privacy: redacted-app-local`) is what Details previews,
Copy copies and the GitHub form receives. Stance: PORT (keep the file name/format so a downgrade or a second
reader still works; migrate in place).

**DESKTOP-WEB-CROSS-59 - Performance trace (OCTRACE), PromptTrace, observers.** `lib/diagnostics/perf_trace.dart`
(844 lines). Always-on, in-memory ring of **2000** spans/marks (`PerfTrace.capacity`); the clock starts with
`PerfTrace.markOnce('app.main')` as the first statement of `main()`, so `at=` is time since launch. API:
`span`/`spanSync` (Zone-based parenting: a request made while `location.select` is open records it as
parent; a finished span is skipped so a stream started inside `connect` does not parent requests minutes
later), `begin` (manual handle; `finish(error:, attrs:)` idempotent), `mark`, `markOnce`, `recordSince`,
`recordDuration` (native-measured). Marks used: `app.main`, `app.first_frame`, `app.shell_frame`,
`app.bootstrap` span, `prompt.sent`, `route <name>`. Every finished span goes to the device log as one line
`OCTRACE <12ms|1.2ms> <name> key=value ... [outcome=error] [parent=<name>]` (marks: `OCTRACE mark <name>
at=<ms>`; values with spaces quoted), but spans shorter than their `logMinMs` (50 ms for HTTP) are only
buffered unless they fail; `logSink` null in tests. `stats()` = per-name count/errors/p50/p95/max/total
(nearest-rank), slowest p95 first; `reportText(slowest 30, recent 50)` is a copyable plain-text report
(redacted again as a whole). `PerfTraceChanges` coalesces notifications into one microtask and schedules
nothing while nobody listens ("an app that never opens the screen pays nothing"). `PerfTraceInterceptor`
(Dio): one span `http <api> <METHOD> <path template>` with `status`, `bytes` (Content-Length else raw body
length, never re-encoding JSON), `cause` on failure; **no headers, bodies or query values are read**;
attached once per Dio (`oc1`, `oc2`, `gascity`, `release`, ...). `PerfTraceNavigatorObserver`: one span
`route <name>` from push to first frame (unnamed routes are named after the first `*Screen/*Page/*Sheet/*Dialog`
widget found in the route's subtree, visiting <= 400 elements). `PromptTrace` (`sent`, `track`, `accepted`,
`observe(eventType, props)`): `prompt.accepted`, `prompt.first_token` (first assistant output; the user's
own echoed text part does not count), `prompt.turn` (idle after the turn visibly started - a leftover idle from
the previous turn right after send is ignored), at most 16 sessions tracked. `OCTRACE` lines are also written
into `ReportProblem` as `timing` events and shown in the diagnostics "Performance" section (steps grouped by
name, slowest first; a failed step says "2 failed" in words, nothing red). Setup job logs do **not** carry
OCTRACE (commit `7694c7c37`). Stance: PORT (same span names so before/after comparison with the Flutter
build is possible; use `android.os.Trace`/`perfetto` sections in parallel for system traces).

**DESKTOP-WEB-CROSS-60 - Report a problem: builder, GitHub link, clipboard fallbacks.**
`lib/feedback/problem_report.dart` (359), `bug_report.dart` (72), UI `app_diagnostics_screen.dart` (494),
`app_diagnostics_kept_screen.dart` (341). Every error state's "Report a problem" ends at
`openReportProblem(context, error: KitReport?)`. Page: one calm card with the failure that opened it (else the
newest error kept, else "nothing noticed" and the person's words become required); what goes with the report;
Details = exactly the text that leaves the phone; "Everything this phone kept"; a privacy line; an optional
text field whose draft is the **app-wide** `oc.draft.report-problem.app` (survives leaving the page, cleared
once sent); primary action "Send as a GitHub issue" through `openExternalLink`, Copy beside it; **nothing
leaves the phone before one of those taps, and nothing is filed until the person submits GitHub's form.**
`ProblemReport.build`: title (error title, else first description line, else fallback, else "Problem report";
<= 80 chars), "What happened" (description <= 4000, `(not described)`), `Error` block (Type, Where, details <=
2000), `Environment` (`App: 2.1.0+57`, `Platform: <label>`, `Connection:` in words with no address or name),
then diagnostics: a failed job's log first ("Failed job log (last lines)" or "none was kept"), then recent
events newest first with `[UTC time] kind · source ×N`, message <= 1000, stack first 8 lines + "… N more
lines"; up to **10 errors and 10 timings** (timings omitted entirely when a failed-job log is attached so the
real reason is not buried). Platform labels: `Android`, `Windows desktop (experimental — contributor-tested)`,
`Linux desktop (alpha)`, `macOS (untested)`, `iOS (untested)`, `Fuchsia (untested)`, `Web (unsupported)`;
version from `PackageInfo` with a 2 s timeout else `unknown+0`. Link =
`https://github.com/Eslamasabry/opencode-mobile-next/issues/new?template=bug_report.yml&title=&app-version=&what-happened=&logs=`;
`link(maxLength: 2048)` degrades: full link -> `logs` replaced by "The diagnostics were copied to the clipboard:
paste them here." with the diagnostics on the clipboard -> `what-happened` replaced by "The whole report was
copied to the clipboard: paste it here." with the whole report copied. Scrub = `problemReportScrub`
(DESKTOP-WEB-CROSS-37 #4: server host and IP never in a public issue). Tests: `problem_report_test`,
`diag_privacy_test`. Stance: PORT (the repo URL and template name are product config; consider a
`mailto:`/share alternative later - DECIDE).

**DESKTOP-WEB-CROSS-61 - Failed-job report.** `lib/diagnostics/failed_job_report.dart` (202). Immutable,
redacted preview built only from a terminal failure: setup (`SetupProgress` failed, optional component),
team setup (`BuiltinTeamJob`), team work item, team run gate, development service (requires an **owned**
terminal failure: ownership token, shell id, start time, command and directory all match; unknown exits and
deliberate stops are not failures; ownership tokens and commands are never exported). Fields bounded: job id
256, title 256, detail 2048, log excerpt = last **120 lines / 16,384 characters** of the whole-log-redacted,
escape-stripped text (`setupLogForPeople` drops timing lines). A log is attached only when its session id
matches (never "the currently active session of a reused agent"). `failedJobReportAction(...)` returns null
when nothing is reportable so no dead button shows (STATE-13); `capture` runs again at the tap. Stance: PORT.

**DESKTOP-WEB-CROSS-62 - Logging policy.** No logging framework. Dart: `debugPrint` (release too:
`adb logcat -s flutter | grep OCTRACE` works on a release build), `PerfTrace.logSink`, the category-only
"Uncaught error" line; every other `debugPrint` is a fixed string (e.g. `Desktop window state read failed:
$error` - note these interpolate platform exceptions; keep such sinks to non-secret domains). Kotlin: ~25
`android.util.Log` calls with class tags (`OcLifecycle`, `BuiltinLinux`, ...), mostly warnings on failed
platform reads; some pass a throwable (`Log.w(TAG, "...", error)`). Policy from AGENTS.md and the invariants:
**provider credentials never reach logs, diagnostics, notification copy or test output** (`/config/providers`
returns API keys). Device reality: the owner's phone silences logcat for the app, so in-app Details,
`exit-info` and R8 retrace are the real channels. Stance: PORT with a thin `Log` facade in `commonMain`
(`expect`), a redacting formatter, `Timber`-style planting only in debug, and a lint that forbids raw
`Log.*` outside it.

**DESKTOP-WEB-CROSS-63 - Diagnostics UI sections.** (screens-nav SCREENS-NAV-102 is the screen map; the
data/behaviour:) `CrashReportsSection` - a switch with plain words (what is kept, where, how much), saved
reports as rows (kind + time; source/category only under Details) that open a preview, "Delete saved crash
reports" as its own named confirm (destructive; says when other errors go with it), "Share saved crash reports"
when the gateway's share works; hidden on web, hidden while the store is still opening, a disabled switch with
words if it could not open. `ExitHistorySection` - "Recent app exits": problem exits lead (crashes/not
responding, low memory, Android ending the app, no reason), at most 5 until "Show all N", routine exits
(updates, the person closing, normal) folded under one quiet row, none = "No unexpected closes recently.", times as
Today/Yesterday/date; unsupported (Android 10 and earlier) and failed-read (Try again) states; refresh on open
and on return. `PerfTraceSection` - described in 59. `AppDiagnosticsKeptScreen` - everything kept (errors, exits,
performance) with Copy and Clear. Route `/debug` and the desktop palette "Diagnostics". Stance: PORT; these are
the user-visible half of the privacy promise, so port them with the stores, not after.

### 3.H Additions found late (security hygiene of inputs, clipboard and screen capture)

**DESKTOP-WEB-CROSS-64 - Secret input, clipboard and screen-capture hygiene.**
- `KitField.secret` (`lib/ui/kit/kit_field.dart`): obscured with a reveal toggle and a Paste button; **never
  prefilled** (asserts an empty controller on mount); a stored value shows "Saved · Replace" and no input until
  the person chooses Replace; no suggestions, no autocorrect, `enableIMEPersonalizedLearning: false`, no
  autofill (the keyboard must not learn passwords); left-to-right and bidi-isolated; the masked value is never
  the semantics value, never in the widget's diagnostics, never handed to a copy part; **every keystroke
  registers the value with `KitRedact` before the host controller or field callbacks are notified**
  (`_editToHost`, `_onText`). MCP HTTP-header values are masked with the key visible and reveal on press
  (`aa3c37290`). Tests: `settings_secrets_coverage_test`.
- Clipboard: `KitCopy.copy` is the one copy service (`redact: true` by default for logs, diagnostics, report
  previews, technical values; `redact:false` is a reviewed escape hatch for the person's own words and code);
  it plays a light haptic tick and announces once; it does **not** mark clips sensitive (Flutter's
  `Clipboard.setData` cannot set `ClipDescription.EXTRA_IS_SENSITIVE`) - a native app can and should for any
  secret-bearing copy and for the Wi-Fi share link.
- Screen capture: `FLAG_SECURE` is set only on `WidgetOverlayActivity` (the transparent host for the widget
  steer/style/voice panels). `MainActivity` does **not** set it, so transcripts, approvals and pairing codes
  appear in Recents and screenshots. No code path blocks screenshots on server-password screens. Stance: PORT
  the field behaviour exactly (Compose: `KeyboardOptions(autoCorrectEnabled = false, keyboardType =
  KeyboardType.Password, imeAction...)`, `visualTransformation`, and `contentType`/autofill disabled; verify how to
  set `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` (API 26) from Compose - `KeyboardType.Password` normally
  implies it, but the Flutter field sets it explicitly, so test it); DECIDE whether to
  add `FLAG_SECURE` on the server editor, pairing and password screens (cheap; the owner's "never screencap
  the owner's phone" rule is about agents, not the app).

**DESKTOP-WEB-CROSS-65 - Process-wide secret registry lifecycle.** The registry in `KitRedact` is static and
process-local: values come from `ProfileStore.load` (batched secure reads), `upsert`, pairing, probes, GitHub
device flow, quota tokens, setup config responses and secret fields, and are **never cleared in production**
(only `clearKnownSecrets()` in tests). Consequences to design for: (1) a secret deleted from the Keystore stays
masked until the process dies (harmless); (2) the registry holds plaintext secrets in memory for the process
lifetime (an accepted trade-off; the same strings are in the HTTP stack); (3) values shorter than 6 characters
are ignored, so a 4-digit pairing PIN is masked only by pattern; (4) ordering: register **before** persisting or
using a value (`credential_ingress_redaction_test`: even if secure storage then fails); (5) the registry is
sorted longest-first on every insert, fine for tens of entries. Stance: PORT as an injected `SecretRegistry`
with `register(String)`, thread-safe, plus an app-scope singleton for log formatters that cannot take
parameters.

## 4. Data stored (exact keys, paths, formats)

Only items owned by this area. Other keys: state-data sections 4.1-4.6.

### 4.1 SharedPreferences (Flutter plugin; XML `FlutterSharedPreferences.xml`, every key prefixed `flutter.` on disk)

| Key | Type | Content | Owner | Notes |
|---|---|---|---|---|
| `desktop_window_state_v1` | String | JSON `{x,y,width,height,maximized}` | `desktop/window_state.dart` | desktop only; never swept; lenient decode (bad = nothing remembered) |
| `oc.draft.report-problem.app` | String | the person's words for a report | `app_diagnostics_screen.dart` via `KitDraft.appWide` | app-wide (`.app`), not swept by a profile delete; cleared on send; empty removes the key |
| `oc.appLocale` | String | `en,ar,es,ja,pt,ru,zh` | `state/app_locale.dart` | **native reads it** (`NativeStrings`, notification language) |
| `oc.textSize`, `oc.effectsMotion`, `oc.themePack` (incl. `highContrast`), `oc.appearance` (`saver`), `oc.effects*` | String/Bool | accessibility-relevant appearance | `state/effects.dart`, `app_preferences.dart` | full table in state-data 4.4 |
| `oc.readAloudConsent` | Bool | consent | `ui/screens/chat/read_aloud.dart` | |
| `oc.cleartextOk.<id>`, `oc.certificatePin.<id>` | String | exact origin / 64-hex SHA-256 | `state/profiles.dart` | swept |
| `oc.shareSessionViews*`, `oc.mobileDownloadConsent.<id>`, `oc.inFlowConsent.<id>`, `oc.permissionConsent.<id>`, `oc.automation.<id>`, `oc.firstRun.notifyAsk` | | consents (section 48) | `state/*consent*.dart` | formats in state-data |

### 4.2 Files (all under `filesDir` = `getApplicationSupportDirectory()` on Android)

| Path | Content | Writer | Notes |
|---|---|---|---|
| `crash-diagnostics-consent` | ASCII epoch ms (<= 32 bytes) | Dart `CrashDiagnosticsController` (Kotlin reads) | absent/corrupt/future = disabled; deleted when disabled at open |
| `crash-diagnostics.json` | JSON array of `{"source","category","time"}` <= 20 items <= 8,192 bytes | Dart | atomic via `crash-diagnostics.pending` |
| `native-last-crash.properties` (+ `.tmp`) | Java properties `timestamp`, `exceptionClass`, `message`, `frames`, `cause.N.*` | Kotlin `NativeCrashStore` | owner-only perms; <= 24,576 bytes on read |
| `diagnostics/report_problem.json` (+ `report_problem.pending`) | `{"version":1,"entries":[...]}` | Dart `ReportProblem` | 128 entries / 262,144 bytes |
| (desktop) `<appSupport>/crash-diagnostics/*` | same four files | Dart | when no native directory is supplied |
| `assets/branding/app-icon-256.png` | bundled asset | | Linux window icon |

### 4.3 Kotlin SharedPreferences

| File / key | Content |
|---|---|
| `oc_lifecycle` / `running_services` | comma-joined service names (`server`, `aiteam`) running when the process last changed set; `commit()` |
| `oc_lifecycle` / `seen_exit_timestamp` | long; newest `ApplicationExitInfo` timestamp already reported |

### 4.4 Linux install layout (not app data)

`<prefix>/lib/opencode-mobile/` (runtime + `.opencode-mobile-install` ownership marker),
`<prefix>/bin/opencode-mobile` (symlink), `<prefix>/share/applications/io.github.eslamasabry.opencode_mobile.desktop`,
`<prefix>/share/metainfo/io.github.eslamasabry.opencode_mobile.metainfo.xml`, `<prefix>/share/icons/hicolor/*`.
User data on Linux lives in `~/.config` and `~/.local/share/opencode_mobile` (Flutter plugin defaults: prefs file
and app-support directory); the secret service holds credentials.

## 5. Native bridges (channels and Kotlin classes)

| Channel | Dart half | Kotlin half | Methods (this area) |
|---|---|---|---|
| `oc/crash_diagnostics` | `diagnostics/crash_diagnostics.dart` (`CrashDiagnosticsStartup._channel`) | `CrashDiagnosticsBridge.kt`, `NativeCrashStore.kt` | `open` -> `{directory, nativeCrash, anrTimestamp}`; error `unavailable` ("Saved crash details are unavailable. Try again after restarting.") |
| `oc/lifecycle` | `platform/app_exit.dart` (`AppLifecycleBridge`) | `AppLifecycle.kt`, `AppExitHistoryPolicy.kt` | `launchReport`, `exitHistory{limit}`, `keepAliveInfo`, `openKeepAliveSetting{setting}` (builtin-linux owns the rest) |
| `oc/thermal` + `/events` | `platform/thermal.dart` | `ThermalMonitor.kt` | `current`, `notify{title,text}`; events (feeds the problem report's thermal events) |
| `oc/network` + `/events` | `platform/network.dart` | `NetworkMonitor.kt` | `current` -> `{status online|offline|unknown, transport wifi|mobile|other|unknown, metered, connected}` (desktop/web report unknown; used only for advice, never for blocking a connection - a built-in/loopback server stays reachable) |
| `oc/share` | `platform/share_intent.dart` (`ShareIntent`, `ShareOut.text`) | `LaunchShareChannel.kt` | inbound `SEND text/plain`; outbound `shareText{text, subject?}` chooser (empty text -> false; missing/failing side -> false, never throws) used by crash-report share |
| `oc/storage` | `platform/storage_access.dart` | `StorageAccess.kt` | All-files-access state and request (lazy, with a plain explanation) |
| `oc/tailscale` | `platform/tailscale.dart` | `TailscaleHandoff.kt` | `check` -> installed/missing/unavailable; `open` (package presence is not VPN state; no tunnel is read or configured) |
| `oc/camera` | `platform/camera.dart` | `MainActivityMedia.kt` | QR pairing camera permission and the composer's Take photo (Android only) |
| `oc/project_export` | `builtin/project_export.dart` | `ProjectExportBridge.kt` (also used by `ManageSpaceActivity`) | zip export of projects |
| Desktop: `window_manager`, `screen_retriever`, `desktop_drop` plugin channels | `lib/desktop/*`, `ui/desktop/file_drop.dart` | plugin code (Linux/Windows) | window geometry, icon, close veto, display work areas, native drop |

Everything in `lib/platform` guards on `platformCapabilities` and answers something safe when the channel is
absent (desktop, tests), catching `MissingPluginException` and `PlatformException`. In the native Android
rewrite these channels disappear (direct Kotlin interfaces); on iOS/desktop each interface gets an `actual`
that answers `Unavailable` rather than throwing.

## 6. Packages used

| Package (pubspec) | Role in this area | Native/KMP replacement candidate |
|---|---|---|
| `window_manager ^0.5.2`, `screen_retriever ^0.2.0` | desktop window geometry, icon, close veto | Compose Desktop `WindowState`/`Window` (JVM) |
| `desktop_drop 0.8.4` | file drop on the composer | Compose `dragAndDropTarget` with `DragData.FilesList` (verify per version) |
| `package_info_plus ^10.2.1` | version/build number (desktop release check, report) | `PackageManager.getPackageInfo`, BuildConfig, a Gradle-generated constants file |
| `dio ^5.7.0` | release check, all clients | Ktor client with a single factory enforcing trust policy |
| `url_launcher ^6.3.1` | only inside `external_link.dart` | `Intent.ACTION_VIEW` behind the policy |
| `webview_flutter ^4.9.0`, `webview_flutter_android ^4.10.0` | preview WebView | `android.webkit.WebView` / `androidx.webkit` |
| `flutter_secure_storage 11.2.0` (Keystore; libsecret on Linux; wincred on Windows; Keychain on iOS) | all secrets | Android Keystore + AES-GCM (Tink) with DataStore; iOS Keychain; JVM keyring libs (libsecret/Credential Manager) - **no plaintext fallback** |
| `local_auth 3.0.2` | FactsBook device authenticator only | `androidx.biometric.BiometricPrompt` |
| `shared_preferences ^2.3.3` | window state, all prefs | DataStore / Multiplatform Settings (migration reads `FlutterSharedPreferences.xml`) |
| `path_provider 2.1.6` | support dir for diagnostics | `Context.filesDir`, `appDirs` on desktop |
| `shorebird_code_push 2.0.7` | Android patches | none (ends at the switch) |
| `mobile_scanner 7.4.2`, `qr 3.0.2` | QR scan / encode | CameraX + ML Kit (bundled model, no Play-Services download), pure-Kotlin QR encoder |
| `dynamic_color ^1.9.0` | Material You | `dynamicColorScheme` |
| `flutter_localizations`, `intl`, ARB | l10n incl. Arabic | Compose Resources / Android `strings.xml` + plurals |
| Rust `engine/phone` | not this area | |
| Apps/actions: GitHub Actions pinned by SHA; `subosito/flutter-action` | desktop CI | Gradle + `actions/setup-java` |
| Dev only: `flutter_test` guidelines (`androidTapTargetGuideline`, `labeledTapTargetGuideline`, `textContrastGuideline`) | a11y gates | Compose UI test + accessibility checks (verify the checks available in the pinned androidx versions) |

Licensing consequences of a rewrite: every bundled component needs a license text in the About notices
(`THIRD_PARTY_NOTICES.md` is generated from `pubspec.lock`; the native app needs the equivalent from Gradle
dependency licences plus the non-Gradle assets: Geist and Geist Mono (OFL 1.1), Phosphor 2.1.0 (MIT),
lobe-icons marks (MIT), sherpa-onnx / ONNX Runtime (MIT), Whisper models (MIT), Kokoro (Apache-2.0), Termux
terminal-emulator, the Rust phone engine (incl. libgit2 GPL-2 with linking exception and SQLite public domain
in `LICENSES/Rust-Phone-Engine.txt`), the xterm fork under `third_party/xterm`).

## 7. Hard-won behaviours a rewrite must not lose (chronological digest)

Commit ids are from `git -C .../oc_app-batch1 log` on the files named.

1. **2026-08-28/30 `34cc539f3`, `5d1991c8c`** - Gate every Android-only surface through one seam; the
   GitHub release check once keyed off `dart:io Platform` (the Linux *host*), so every widget test that
   mounted the app silently ran a desktop release check; now `supportsDesktopReleaseCheck`, the exact
   complement of `supportsCodePush`. Background-live, widget snapshot, shortcuts refuse **before** touching a
   channel so no "available in the Android app" error is parked on a controller whose UI is hidden.
2. **2026-08-29 `7836a6d4b`** - One hardened opener: a server-supplied URL in an OpenCode 2 external form went
   straight to `launchUrl`; policy lifted out of markdown into `external_link.dart`. The desktop release
   notice stopped trusting GitHub's `html_url` blindly.
3. **2026-08-30 `03e80652f`, `dab85ce3f`** - `WM_CLASS` both halves pinned to the app id (otherwise taskbar
   grouping/icons differ between X11 and Wayland and between DEs); window icon from Dart with a 256 px asset
   only (the 1024 px master is not bundled). 2026-08-31 `9a58a3297`: app id moved to a namespace the project
   controls.
4. **2026-09-02 `e2346919d`** - Saved directory survives relaunch (normalised, provisional until the project
   list loads, falls back to the newest project with a notice, never silently to the server default); keyring
   failure is a typed `SecureStorageUnavailable` with an install hint on Linux and a probe in the Servers
   editor; `cut-alpha.sh` refuses a release body missing the anchor or shorter than ten lines and reads the
   published body back; **Ctrl+1..4 and Ctrl+` pop to the shell root first**; terminal input is one ordered
   FIFO and desktop keys bypass xterm's IME path.
5. **2026-09-06/07 `061d524dd`, `c659ad5eb`** - Release validation: tag grammar, strictly-newer by build number,
   drafts skipped, trusted release URL; Linux packages validate architecture from ELF headers.
6. **2026-09-13** - Web shell investigated on side branches (browser facts in DESKTOP-WEB-CROSS-24); nothing
   merged.
7. **2026-09-19 `b2cf153f8`** - Gesture audit: two gestures had no visible twin; three shortcuts were handled
   but unlisted.
8. **2026-09-24 `ad8f5759f`** - OCTRACE performance tracing (top to bottom, redacted attributes, route and HTTP
   spans). **2026-09-26/27** - `KitRedact`/`KitCopy`/`KitBidi` created; masking of quoted values, whole
   Authorization headers, any JWT (`2fe4160b1`); multi-line quoted secrets (`45aee4e7e`); protocol-relative
   and header-delimited URL credentials, escaped-quote delimiters, registered exact secrets longest first
   (`6275970c1`); **credentials registered at trusted ingress before capture** (`509c8cd0d`); credentials masked
   before copying untrusted URLs (`8dc17fdc1`) and authorization parameters removed from link previews and
   copies (`fa9d1eba7`).
9. **2026-09-27** - Persisted redacted diagnostic reports and capture hooks (`0fb097406`); Report a problem
   page previewing exactly what is sent (`1505b8496`, P8.2); failed jobs carry their log to Report (`46380182c`,
   P8.4); startup opens the persisted report after the first frame (`737899a5e`).
10. **2026-09-28 `fb2b7c440`** - Defer bootstrap and bound diagnostic/pin lookup costs; **`7694c7c37`** no OCTRACE
    in setup job logs.
11. **2026-10-04 `532b34a5a`** - A setup job that failed within milliseconds called `stopService` before
    `onStartCommand` ran `startForeground`, and Android crashed the app with
    `ForegroundServiceDidNotStartInTimeException` (owner's phone, APK 2105/2106); the diagnostics token filter
    had hidden that exception name as a "token" - hence **long letters-only words are code names and stay;
    only 32+ digit-bearing runs are masked**. `ea4480657`: native failures guarded with safe crash
    diagnostics.
12. **2026-10-07/08** - Opt-in crash categories (`dcbd17712`, UI `4ef7b3775`); crash startup bounded at 300 ms
    after first frame (`26384555f`) then finished after the budget (`4e4218d46`); exit/pause/report gateway
    (`76035c6af`, `8644d15d5`).
13. **2026-10-09 `8fcced24a`** - Coverage for exit history, kept errors, crash store; a **marker-secret privacy
    gate** (`diag_privacy_test`); opaque tokens masked in saved reports, failed-job reports and timing names;
    transcript export states that it is not redacted.
14. **2026-10-17 `4cbdfca3e`** - A failure stays in the saved report past later timings (eviction rule).
15. **2026-10-18..21** - Dev-server preview (`56147403f`, `539b736fa`, `70009601f`, `cd827d277`): same-origin
    WebView, cleared storage, no file/content access.
16. **2026-10-26** - Owner: maximum Android-native feel; kit ripple ban lifted; glass removed (a11y gain: fewer
    low-contrast surfaces). **2026-10-30/31** - owner decision: frame text capped at 1.3x, reading text full
    (branch `fix/large-text-2026-10-31`, unmerged).
17. **2026-10-31 `3302ab129`** - Esc stops the running reply; review keys listed in the help.
18. **2026-11-01 `1be94da76`** - Wi-Fi file share (LAN server hardening, DESKTOP-WEB-CROSS-42).

## 8. Performance notes

- Startup: `PerfTrace.markOnce('app.main')` first; the bootstrap gate paints "Opening..." before any storage
  read; crash storage opens after the first frame with a **300 ms** caller budget and a `compute()` isolate;
  the problem-report store opens after the first frame and imports buffered errors/timings on attach; secure
  credentials load in batches of 4; owner target in the Flutter app was ~0.4 s cold start vs 0.25 s for the
  native demo (APK 107 MB vs 2.5 MB).
- Diagnostics cost model: `PerfTrace` is O(1) per span with a bounded deque; `changes` notifications cost
  nothing while the screen is closed; HTTP spans below 50 ms are not logged; the persisted report writes
  **synchronously** with a flush on every event (bounded to 256 KiB; status polls on Linux produce ~2 timings
  per second, so the eviction rule matters for both durability and disk churn). In the rewrite, coalesce
  timing writes (e.g. flush at most every 1-2 s and on `onPause`) while still flushing errors immediately.
- Redaction cost: `KitRedact.text` is a dozen regex passes plus JWT candidate checks (base64 decode + JSON
  parse) - cheap per string but called on every span, every log line, every copy; cache compiled patterns,
  never redact per frame, and avoid redacting transcript bodies at render time.
- Desktop: the always-visible scrollbar needs a `ScrollController` per long list (`KitScrollArea`);
  window-state saves are debounced to 400 ms; the release check is throttled to 15 minutes and bounded to 10 s.
- Text scale: Android nonlinear font scaling means a linear clamp changes proportions; test at 1.0, 1.3, 2.0,
  2.5 (matrix: 6 test shards x widths 320..1600 dp).

## 9. Risks, decisions and recommendations

### 9.1 Owner decisions needed

1. **Windows**: keep out of the first native slices? (Never run by a maintainer; no installer/signing choice made.)
2. **Linux desktop packaging**: tarball + .deb only, or add AppImage/Flatpak? Flatpak sandboxing conflicts with
   "open any project path" and the keyring portal.
3. **Sanitised-throwable hand-off** (DESKTOP-WEB-CROSS-55): keep the empty-stack `RuntimeException` hand-off to
   Android's crash logger in release builds, or pass the real throwable and keep only category persistence?
4. **Telemetry** (46): leave dead, or approve the Aptabase plan (self-hosted) for the native app?
5. **App lock / step-up auth** (44): want it? (Cheap in native; absent today.)
6. **Android update path after Shorebird**: in-app GitHub release check that links to the APK, Play, or none?
7. **Report destination** (60): GitHub issue only, or also an e-mail/share option that does not need a GitHub account?
8. **Web**: confirm DROP.
9. **PRIVACY.md rewrite** before the first native release (49).

### 9.2 Ranked rewrite risks

1. **Silent privacy regression** from a new dependency or a convenience log (crash libs, Coil/Ktor logging,
   OkHttp `HttpLoggingInterceptor`, Compose Navigation arguments in logs). Mitigation: the egress allowlist
   check (47), a lint banning `Log.*` outside the facade, release `HttpLoggingInterceptor` banned.
2. **Redaction drift** while splitting the seven scrubbers (36/37). Mitigation: port `kit_redact_test`,
   `redaction_test`, `diag_privacy_test`, `credential_ingress_redaction_test`, `notification_text_test`,
   `problem_report_test`, `perf_trace_test`, `failed_job_report_test` vectors **first** as the ground truth,
   in `commonTest`.
3. **Lost safety net in Compose** (no `ErrorWidget`): one bad composable can crash-loop the app. Mitigation:
   crash-loop counter in the startup path (consecutive crashes within N seconds -> safe mode that opens
   Settings/Report), defensive state holders, `try/catch` around derived UI models, the launch report path.
4. **Secure-storage migration**: Flutter `flutter_secure_storage` 11 stores with its own cipher/key layout
   (state-data section 2.2/3). A wrong read looks like "no password" and the existing code deliberately treats
   an undecryptable entry as `requiresPasswordReentry`, never as an empty password. Keep that.
5. **Accessibility regression** (the Flutter suite encodes many decisions as tests). Mitigation: build the
   Compose accessibility/overflow harness before screens; port the 9 critical flows, the font-scale matrix and
   the gesture audit as the first UI tests.
6. **Announcements**: `announceForAccessibility` deprecation (research above); live-region semantics differ per
   TalkBack version. Mitigation: one `Announcer` abstraction, tested on the emulator with TalkBack.
7. **Cleartext boundary** relies on app code (network-security-config permits cleartext). One `HttpClient`
   factory, ban others.
8. **Docs drift** (section 0). Mitigation: derive shortcut help and capability tables from code; regenerate
   PRIVACY.md facts from the egress and storage tables.
9. **Desktop/iOS surprise cost**: Compose Multiplatform desktop and iOS differ in text input (IME vs hardware
   keys), drag/drop, scrollbars, system bars; the 16-flag seam is the cheap hedge, the terminal input path is
   the likely tricky one.

### 9.3 Recommended shape (KMP-ready)

- `commonMain`: `PlatformCapabilities`, `ExternalLinkPolicy` (pure), `Redactor` (+ profiles), `Diagnostics`
  (ring, dedupe, sanitise), `ProblemReportBuilder`, `CrashReportBuilder`, `ReleaseTag` parser, window-geometry
  pure functions, `TrustPolicy`/loopback/private-network classification, `PerfTrace`, `PromptTrace`,
  `ConsentStore` three-state reads, all behind small `expect`/interfaces for storage, share, clock, clipboard.
- `androidMain`: manifest posture, `NativeCrashStore` + uncaught handler, `ApplicationExitInfo` source,
  notifications privacy, WebView hardening, Wi-Fi share server binding, Keystore secret store.
- `desktopMain` (JVM): window state, file drop, keyring, packaging, release check, shortcuts (key event router),
  context menu region.
- `iosMain`: Keychain, remote-only capabilities, resume-refetch rule.
- Tests: `commonTest` vector suites for every pure piece above (they are the largest block of logic tests in the
  owner's catalog for this area: `security-privacy` 188 rows).

### 9.4 First slices (suggested order for this area)

1. `Redactor` + vectors, `ExternalLinkPolicy` + vectors, `PlatformCapabilities` (before any screen).
2. Diagnostics core: ring, problem-report store (atomic file, eviction rule), `PerfTrace`.
3. Native crash store + opt-in consent file + exit history (reuse the Kotlin as-is).
4. Report-a-problem builder and page; crash switch; kept screen.
5. Compose accessibility/overflow harness; gesture audit checklist.
6. Manifest-lint tests (exported set, backup rules, permission list).
7. Later: desktop (window state, shortcuts, drop, packaging), iOS capabilities.

### 9.5 Day-1 in-place update notes for this area

- Same package, signing key SHA-256 `1DE5BF08...D60C` (local release) - CI stable signer is a different key; an
  APK must match the installed one or Android refuses the update (AGENTS.md).
- Migrate/keep: `flutter.desktop_window_state_v1` (desktop only), `flutter.oc.draft.report-problem.app`,
  `diagnostics/report_problem.json`, `crash-diagnostics*` and `native-last-crash.properties` in `filesDir`
  (the consent epoch lives in the file; do not reset it or users silently lose or regain consent),
  `oc_lifecycle` prefs, `flutter.oc.readAloudConsent`, the consent keys in 48. Anything unreadable must
  **fail closed** (no consent, no pin, no cleartext approval).
- Old crash/problem files are readable only with the same vocabularies; if the native app changes them, write a
  one-time migration and delete the old files, never leave two truths.
- The Flutter `app_flutter` directory and `FlutterSecureStorage` data are read once by the migration (state-data
  section 3), after which the native app owns them.

## 10. Test-first map

Catalog rows (`/home/eslam/Storage/Code/wt/native-plan/catalog`, `raw-*.jsonl`): area `desktop-web-cross` = 16
rows; area `security-privacy` = 188 rows. Highest-leverage Flutter test files for this file (rows in catalog):
`crash_report_test` 11, `private_network_cleartext_test` 7, `byo_host/ssh_runner_test` 6, `git/github_device_flow_test`
5, `problem_report_test` 4, `product_error_text_test` 4, `crash_diagnostics_test` 3, `external_link_test` 2,
`redaction_test` 2, `credential_ingress_redaction_test` 2, `report_problem_capture_test` 2,
`device_diagnostics_gateway_test` 2, `kit/kit_redact_test`, `app_diagnostics_test`, `perf_trace_test`,
`failed_job_report_test`, `notification_text_test`, `diag_privacy_test`, `no_raw_error_text_test`,
`server_trust_test`, `server_trust_profiles_test`, `api2_transport_trust_test`, `dev_preview_test`,
`repository_hygiene_test`, `shipped_download_urls_test`, `settings_secrets_coverage_test`,
`settings_preferences_coverage_test`.

Desktop/platform test files (logic parts to port; widget parts later): `desktop_window_state_test` (geometry),
`desktop_release_check_test` (tag + URL + throttle), `desktop_packaging_contract_test`, `platform_capabilities_test`,
`update_channel_platform_test`, `desktop_platform_gating_test`, `ios_runner_contract_test`,
`ios_remote_platform_gating_test`, `desktop_shortcuts_test`, `desktop_context_menu_test`, `desktop_scrollbar_test`,
`desktop_selection_test`, `desktop_pointer_test`, `desktop_file_drop_test`, `composer_desktop_enter_test`,
`command_palette_focus_test`, `kit/kit_terminal_links_test`, `kit/kit_terminal_view_test`, `terminal_accessibility_test`.

Accessibility test files (widget level; owner: later, but plan the Compose harness now):
`accessibility_guidelines_test`, `android_focus_highlight_contract_test` (an XML contract - portable as-is),
`app_text_scale_test`, `text_scale_overflow_test` + matrix 0-5 (+ `text_scale_overflow_baseline.json`),
`kit_motion_test`/`kit_motion_app_test`/`motion_*` (reduced motion), `gesture_audit_test`,
`settings_access_test`, `exit_list_focus_test`, `picker_search_keyboard_test`.

Portable "contract" tests that are plain-text assertions and survive the rewrite unchanged in spirit:
manifest/XML contracts (focus highlight, exported components, backup rules), shipped-asset URL scan, hygiene.

Behaviour a rewrite must demonstrate with a failing-first test (owner rule: verify fixes with a failing test):
`ReportProblem` eviction keeps an error past 200 timings; the crash consent epoch blocks a pre-clear ANR;
`external link` refuses `intent:` / userinfo / > 2048 chars; `Redactor` masks a registered secret before and
after truncation; `window geometry` re-homes a window saved on an unplugged monitor; a notification text never
contains a bearer token.

## 11. Source index (start here when porting)

Dart: `lib/platform/platform_capabilities.dart`, `lib/desktop/window_state.dart`, `lib/desktop/window_icon.dart`,
`lib/ui/desktop/{shortcuts,desktop_interaction,context_menu,file_drop}.dart`,
`lib/ui/kit/{kit_scrollbar,kit_context_region,kit_error_fallback,kit_redact,kit_copy,kit_bidi,kit_web_preview,kit_field}.dart`,
`lib/ui/widgets/{external_link,model_shortcuts}.dart`, `lib/update/{desktop_release_check,shorebird_update_notice}.dart`,
`lib/diagnostics/*` (9 files), `lib/feedback/{problem_report,bug_report}.dart`, `lib/platform/{app_exit,native_crash,network,
thermal,tailscale,storage_access,share_intent,wifi_file_share}.dart`, `lib/domain/{app_exit_history,crash_report,
diagnostics_error,app_diagnostics_gateway,dev_preview,loopback_host,server_trust}.dart`,
`lib/ui/screens/{app_diagnostics_screen,app_diagnostics_kept_screen,crash_reports_section,exit_history_section,
perf_trace_section,about_screen}.dart`, `lib/main.dart`, `lib/main_parts/{bootstrap_gate,alerts_and_shortcuts}.dart`,
`lib/background/notification_text.dart`, `lib/state/profiles/preferences_types.dart` (`SecureStorageUnavailable`),
`lib/ui/theme_packs*.dart`, `lib/ui/app_theme.dart`.

Kotlin: `OcApplication.kt`, `NativeCrashStore.kt`, `CrashDiagnosticsBridge.kt`, `AppLifecycle.kt`,
`AppExitHistoryPolicy.kt`, `ServiceDiagnostics.kt`, `ManageSpaceActivity.kt`, `ThermalMonitor.kt`, `NetworkMonitor.kt`,
`StatusNotificationRenderer.kt`, `NativeNotificationLocale.kt`, `StorageAccess.kt`, `TailscaleHandoff.kt`.

Native/platform trees: `linux/runner/my_application.cc`, `linux/CMakeLists.txt`, `linux/packaging/*`,
`scripts/package-linux.sh`, `windows/runner/*`, `ios/*`, `android/app/src/main/AndroidManifest.xml`,
`android/app/src/main/res/xml/{network_security_config,backup_rules,data_extraction_rules}.xml`,
`android/app/src/main/res/values{,-night}/styles.xml`, `.github/workflows/{desktop-linux,desktop-windows,ios-quality,
android-quality}.yml`.

Docs: `docs/desktop.md`, `docs/desktop-feasibility.md`, `docs/stack-gaps-and-desktop.md`,
`docs/security/threat-model.md`, `docs/privacy/bd7-crash-diagnostics.md`, `docs/aptabase-telemetry-plan.md`,
`docs/design/{bd7-contract,bd7-late-open-contract,BC-diagnostics-contract}.md`,
`docs/ux-system/revamp/STANDARDS.md` (A11Y/SEC/G rules), `docs/design/ui-ledger/gesture-audit.md`, `PRIVACY.md`,
`SECURITY.md`, `THIRD_PARTY_NOTICES.md`, `ios/README.md`, and (on the unmerged branch)
`docs/verification/web-shell-2026-09-13.md`.

Memory files that bind this area: `no-raw-errors-rule`, `text-scale-cap`, `phone-logcat-silenced`,
`night-2026-10-29-state` (diag-build recipe), `component-max-width-rule`, `android-native-feel`,
`design-standard-rule` (kit only), `micro-animations-wanted`, `teach-what-is-hidden`, `no-users-yet`,
`web-shell-slice-2026-09-13`, `verify-fixes-with-a-failing-test`.
