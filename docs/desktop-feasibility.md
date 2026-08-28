# Desktop feasibility: Linux and Windows

Study date: 2026-08-28. Proven on this machine: the unmodified app **builds a
working Linux release binary today** (`flutter build linux --release`,
pinned Flutter 3.47.1) after `flutter create --platforms=linux` added the
runner. No Dart code changes were required to compile. Launch on this
machine's display could not be visually verified only because the build
shell lacks X authorization (a host condition, not an app defect): the
binary starts and fails with GTK's generic `cannot open display`, exactly as
any GTK app does from that shell. Run it from a normal desktop terminal to
see the window.

## Dependency audit (from the exact locked versions)

Every plugin in the app declares desktop implementations, and every
federated desktop sub-package is already present in the lockfile:

| Package | Linux | Windows | Notes |
|---|---|---|---|
| dio, flutter_riverpod, scrollable_positioned_list, uuid, xterm, highlight, crypto, cupertino_icons, opencode_sdk (local) | ✅ | ✅ | Pure Dart |
| file_picker 12.1.1 | ✅ | ✅ | `file_picker_linux` (zenity/qarma portal), `windows_file_picker` |
| flutter_secure_storage 10.0.0 | ✅ | ✅ | Linux backend needs **libsecret** at runtime (`libsecret-1-0`, plus a keyring daemon); Windows uses wincred |
| record 7.1.1 | ✅ | ✅ | `record_linux` 2.1.1 / `record_windows` 2.2.3 |
| sherpa_onnx 1.13.6 | ✅ | ✅ | `sherpa_onnx_linux/windows` ship native ONNX libs — **on-device voice works on desktop** |
| shared_preferences | ✅ | ✅ | File-backed on desktop |
| url_launcher | ✅ | ✅ | `xdg-open` / `ShellExecute` |
| path_provider | ✅ | ✅ | XDG dirs / Known Folders |
| shorebird_code_push 2.0.7 | ⚠️ | ⚠️ | Pure-Dart facade; without the Shorebird engine `ShorebirdUpdater.isAvailable` is false and the app's existing notice layer already no-ops on it. Desktop needs its own update story (below). |

**Conclusion: no dependency blocks desktop.** The only functional gap is
code-push, which is Android-scoped by design.

## Android-coupled code and how it degrades

- `lib/background/live_background.dart` (MethodChannel `oc/background`):
  already catches `MissingPluginException` and `PlatformException` — on
  desktop, background-live silently reports unavailable. Later: map to
  desktop notifications (`local_notifier` or D-Bus) in a dedicated slice.
- `lib/voice/device.dart` (`oc/voice`): every call is behind
  `if (!Platform.isAndroid)` early-returns with safe defaults. Voice capture
  via `record` + sherpa decode should work on desktop once the device-info
  shim returns a desktop profile; until then voice remains Android-gated.
- `lib/termux/bridge.dart` (`oc/termux`): meaningless on desktop. Its
  channel calls catch `PlatformException` but **not**
  `MissingPluginException` — before shipping desktop, gate the Termux entry
  points (`Platform.isAndroid`) so the setup screen is simply hidden.
- `lib/update/shorebird_update_notice.dart`: gated on
  `ShorebirdUpdater.isAvailable` — safe no-op on desktop. Replace with a
  GitHub-releases version check on desktop (the notice UI pattern is
  reusable as-is).
- Android launcher/notification resources, foreground service: not compiled
  on desktop; no action needed.

## UI readiness

Already desktop-friendly from the facelift: NavigationRail ≥760dp (extended
≥1040dp), Files master-detail ≥900dp, Review two-pane ≥840dp, chat
transcript/composer width caps, bundled JetBrains Mono. Remaining for
desktop-class feel:

1. Keyboard: Ctrl+Enter send, Ctrl+K command launcher, Esc closes sheets,
   arrow navigation in pickers (`Shortcuts`/`Actions`).
2. Window management: `window_manager` for min-size (~420×640), remembered
   size/position, title.
3. Pointer polish: hover states on rows (mostly free via Material),
   always-visible scrollbars on desktop scroll views
   (`ScrollbarTheme`/`interactive`).
4. Bottom sheets read fine at desktop widths thanks to the 720dp cap on the
   picker; confirm sheets could become dialogs on desktop via an adaptive
   helper later — not required for v1.

## Distribution

- **Linux:** start with a tarball of `build/linux/x64/release/bundle` +
  `.desktop` file; then AppImage (best portability, single file) and/or
  Flathub (best discoverability, sandbox caveats for arbitrary project
  paths — needs filesystem access grants). A `.deb` is easy from CI but
  distro-narrow.
- **Windows:** `flutter build windows` on a Windows CI runner; ship both a
  zip and an MSIX (winget-friendly). Requires code-signing decisions
  (self-signed MSIX installs need developer mode; a cert is the store-grade
  path).
- **Updates without Shorebird:** in-app check of the GitHub Releases feed
  (same UX as the existing Shorebird notice), download-and-run installer or
  open the release page. Desktop full builds are cheap; no code-push needed.
- **CI:** add `build-linux` (ubuntu-latest + the gtk/clang deps) and
  `build-windows` (windows-latest) jobs to the existing pinned workflow;
  artifacts on tags.

## Host toolchain note (this machine)

The stock build failed with `cannot find -lstdc++`: clang 18 auto-selects
the GCC 14 tree, which is present but incomplete (no `libstdc++.so`).
Workaround used (no root needed):
`CXXFLAGS/CFLAGS=--gcc-install-dir=/usr/lib/gcc/x86_64-linux-gnu/13`.
Permanent fix: `sudo apt install libstdc++-14-dev` (or `g++-14`).

## Phased plan

- **Phase 1 — minimal viable desktop (1–2 slices):** commit the `linux/`
  runner (done here); gate Termux entries and the managed-loopback flow
  behind `Platform.isAndroid`; hide background-live and Shorebird settings
  rows off-Android; smoke the core journeys (connect, chat, files, review,
  terminal) manually on Linux. Windows runner scaffold on a Windows machine
  or CI.
- **Phase 2 — desktop polish (2–3 slices):** keyboard shortcuts,
  window_manager, scrollbars/hover, desktop voice enablement
  (device shim), desktop notifications for background-live parity.
- **Phase 3 — distribution (1–2 slices + CI):** GitHub-release update
  check, AppImage + MSIX packaging jobs, docs.

## Recommendation

Green-light. The hard part usually — dependencies — is already solved by
the package choices this app made. Phase 1 is mostly feature-gating, and the
Linux binary from this branch is usable for daily driving on this machine
right now (connect it to `http://127.0.0.1:4096`).
