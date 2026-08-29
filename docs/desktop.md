# OpenCode on Linux desktop

**Status: experimental.** The Linux target compiles, packages and installs,
and it shares every line of Dart with the Android app. It has had far less
testing than Android, nothing is signed, and some of what follows was
verified by hand on one machine rather than by a green CI run — the
[Verification log](#verification-log) says exactly which is which.

Only `linux/` exists. There is no `windows/` or `macos/` runner in this
repository; `docs/desktop-feasibility.md` covers what Windows would take.

---

## Build

Local builds and tests use the Shorebird-pinned Flutter, which is what the
Android release path uses too:

```sh
~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
# Flutter 3.47.1 · Dart 3.13.1
```

```sh
flutter config --enable-linux-desktop
flutter pub get
flutter build linux --release
# -> build/linux/x64/release/bundle/   (75 MB, binary `opencode_mobile`)
```

### The clang / libstdc++ caveat

On some hosts — this development machine (Pop!_OS 24.04, clang 18) among
them — the plain build fails at link time:

```
ld.lld: error: unable to find library -lstdc++
```

This is **not a code defect**. clang auto-selects the newest GCC tree it can
find; if that tree ships headers but not `libstdc++.so`, the link fails even
though a complete older toolchain is installed right next to it. The
workaround is to point clang at the complete tree:

```sh
CXXFLAGS="--gcc-install-dir=/usr/lib/gcc/x86_64-linux-gnu/13" \
LDFLAGS="--gcc-install-dir=/usr/lib/gcc/x86_64-linux-gnu/13" \
  flutter build linux --release
```

The permanent fix is to install the missing development package for the GCC
version clang picks — on this machine `sudo apt install libstdc++-14-dev`.

You do not have to work this out by hand. `linux/packaging/gcc-install-dir.sh`
compiles and links a small C++ probe with the default compiler; if that
succeeds it prints nothing and no flag is needed (this is the expected case
on a normal CI runner). Only when the probe fails does it walk the GCC trees
newest-first and re-run the probe with `--gcc-install-dir` before printing
one, so it can never hand you a directory that does not actually fix the
build. Both `scripts/package-linux.sh --build` and the CI workflow use it:

```sh
dir="$(linux/packaging/gcc-install-dir.sh)"
[ -n "$dir" ] && export CXXFLAGS="--gcc-install-dir=$dir" \
                        LDFLAGS="--gcc-install-dir=$dir"
flutter build linux --release
```

### Running it

```sh
build/linux/x64/release/bundle/opencode_mobile
```

The binary resolves `data/` and `lib/` relative to its own real path, so the
bundle directory has to stay together. Point the app at an opencode server —
`http://127.0.0.1:4096` if you run one locally.

---

## Package

```sh
scripts/package-linux.sh              # package the bundle that already exists
scripts/package-linux.sh --build      # build first, then package
scripts/package-linux.sh --skip-deb   # tarball only
scripts/package-linux.sh --help
```

Output lands in `build/linux/packages`:

| Artifact | Contents |
|---|---|
| `opencode-linux-x64-<version>.tar.gz` | runtime, icons, `.desktop`, AppStream file, `install.sh`, `uninstall.sh`, README, LICENSE |
| `opencode_<version>_amd64.deb` | the same payload under `/usr` |
| `SHA256SUMS` | checksums for both |

The script refuses to run if `data/flutter_assets/version.json` in the
bundle disagrees with `pubspec.yaml`, so a stale bundle can never be shipped
under a new version number, and it validates the generated `.desktop` entry
with `desktop-file-validate` when that tool is installed.

### Installing from the tarball

```sh
tar -xzf opencode-linux-x64-1.0.29+30.tar.gz
cd opencode-linux-x64-1.0.29+30
./install.sh                     # into ~/.local — no root
sudo ./install.sh                # into /usr/local
./install.sh --prefix /opt/oc    # anywhere
./uninstall.sh --prefix <same>
```

It puts the runtime in `<prefix>/lib/opencode`, a symlink on PATH at
`<prefix>/bin/opencode`, and the desktop entry, icons and AppStream file
under `<prefix>/share`, rewriting `Exec=` to the prefix it used. It replaces
the runtime directory wholesale rather than merging, so an upgrade cannot
leave a stale plugin `.so` behind for the new binary to load.

### The .deb's dependencies

`Depends` is derived, not guessed: `dpkg-shlibdeps` reads the shipped binary
and every bundled plugin `.so`, with the bundle's private `lib/` on its
search path so those resolve instead of being reported as missing. On Ubuntu
24.04 that produced:

```
libatk1.0-0t64, libc6, libcairo-gobject2, libcairo2, libepoxy0, libgcc-s1,
libgdk-pixbuf-2.0-0, libglib2.0-0t64, libgtk-3-0t64, libharfbuzz0b,
libpango-1.0-0, libpangocairo-1.0-0, libsecret-1-0, libstdc++6, zlib1g
```

Because those names come from the build host's package set, **the .deb
targets the distribution it was built on** (the `t64` suffixes are Ubuntu
24.04's time_t transition). A `.deb` built on 24.04 will not satisfy its
dependencies on Debian 12. The tarball has no such constraint. If
`dpkg-shlibdeps` is unavailable the script falls back to a curated list with
`libgtk-3-0 | libgtk-3-0t64` style alternatives.

### Reproducibility

Packaging is deterministic: the same bundle packaged twice produces
byte-identical artifacts (verified). The tarball uses
`--sort/--mtime/--owner/--numeric-owner` with `gzip -n`; the `.deb` also has
its whole staging tree flattened to `SOURCE_DATE_EPOCH`, because `dpkg-deb`
copies each file's mtime into `data.tar` and the generated control files
would otherwise be new on every run.

The **Flutter build itself is not reproducible** — the linker stamps build
ids — so rebuilding first changes the hashes even on an unchanged commit.
Publish the checksums of the artifacts CI actually uploaded, never ones
regenerated afterwards.

---

## Desktop integration

- `linux/packaging/ai.opencode.opencode_mobile.desktop` — named after the
  GApplication application id set in `linux/CMakeLists.txt`.
- `linux/packaging/icons/` — the icon as an SVG plus committed hicolor PNGs
  at 16/24/32/48/64/128/256/512. They are committed so packaging and CI
  never need an SVG rasteriser; `linux/packaging/render-icons.sh`
  regenerates them (needs ImageMagick) when the SVG changes.
- `linux/packaging/ai.opencode.opencode_mobile.metainfo.xml` — AppStream, so
  GNOME Software and Discover describe the app instead of showing a bare
  package name. The packaging script stamps the version into its
  `<releases>` block.

### Why the window groups with its icon

Two different mechanisms have to agree, and both are pinned to the single
string `ai.opencode.opencode_mobile`:

- **Wayland.** GTK reports `g_get_prgname()` as the xdg-shell `app_id`, and
  the compositor pairs that with the desktop file *of the same name*. The
  runner already called `g_set_prgname(APPLICATION_ID)`.
- **X11.** The shell matches `StartupWMClass` against `WM_CLASS` instead.
  GDK derives `WM_CLASS`'s `res_name` from the program name and its
  `res_class` from the program *class*, which by default is the program name
  with its first letter capitalised — `Ai.opencode.opencode_mobile`.
  Different desktop environments compare different halves. So
  `my_application_new()` also calls `gdk_set_program_class(APPLICATION_ID)`,
  making both halves identical and equal to `StartupWMClass`.

`test/desktop_packaging_contract_test.dart` fails if any of those strings
drift apart, because a mismatch has no runtime symptom beyond a generic
icon.

---

## Window behaviour

`lib/desktop/window_state.dart`, wired in from `main()` on Linux, Windows and
macOS only.

- Position, size and maximized state are remembered in SharedPreferences and
  restored on the next launch.
- Restored geometry is clamped against the displays that exist **now**,
  using each display's work area (which excludes panels and docks):
  - a window that merely hangs off an edge is pulled fully inside;
  - a window larger than the current resolution shrinks to fit;
  - a window smaller than the 480×600 floor grows back to it;
  - a window saved on a monitor that is no longer attached — including at
    the negative coordinates a display to the left produces — keeps its size
    but comes back centred on the primary display rather than off-screen.
- A maximized window restores maximized, and still carries its *restored*
  rectangle, so un-maximizing gives back a real window rather than a sliver.
- Defaults: 900×700, minimum 480×600, title "OpenCode". The floor was
  already in place before this work and was kept: below it the navigation
  rail plus a readable transcript stop fitting.
- Saves are debounced (GTK emits a configure event per frame during a drag),
  and the app takes over its own close (`setPreventClose`) so the final
  geometry is flushed before the window is destroyed.
- Every failure path is swallowed. Window placement is a convenience and
  must never be able to stop the app starting or closing.

The geometry rules are pure functions, unit-tested in
`test/desktop_window_state_test.dart` without needing a window.

---

## Updates

Shorebird patches Android only. On desktop
`lib/update/desktop_release_check.dart` polls the GitHub releases feed and,
when a newer build exists, shows one snackbar with a **View** action that
opens the release page in a browser. **Nothing downloads or installs
itself.**

The version contract, end to end:

1. `pubspec.yaml` says `version: 1.0.29+30`.
2. `flutter build linux` writes that into
   `data/flutter_assets/version.json` as
   `{"version":"1.0.29","build_number":"30"}`.
3. On Linux `package_info_plus` reads that file next to the **resolved**
   `/proc/self/exe`, so it is correct even when launched through the
   installer's PATH symlink. `PackageInfo.buildNumber` is `"30"`.
4. Releases are tagged `v1.0.29+30-preview.9` (or `v1.0.19+20`).
   `buildNumberFromTag` pulls `30` out of either shape; a tag with no `+N`
   is never treated as newer.
5. The checker reads only `tag_name` and `html_url` — **never asset names**
   — so artifact naming is free. It still embeds the same version string so
   a downloaded file identifies itself.
6. CI *attaches* assets to the existing release for a tag rather than
   creating one, leaving `scripts/release.sh` the only thing that makes
   releases.

No mismatch was found, so nothing in `lib/update/` needed changing.
`test/desktop_packaging_contract_test.dart` locks the contract down.

---

## CI

`.github/workflows/desktop-linux.yml` — installs the toolchain, runs
`flutter analyze`, runs the suite in thirds at `--concurrency=1` (the same
shape as the Android gate), builds the release bundle, checks `ldd` reports
nothing missing, runs the packaging script, and uploads the tarball, the
`.deb` and `SHA256SUMS`. On a `v*` tag a second job attaches those to that
tag's release.

CI pins **upstream stable Flutter 3.47.1** — the same version
`flutter --version` reports from the Shorebird toolchain used locally.
Shorebird's fork is not installable on a runner, so the *version* is matched
rather than the toolchain.

The apt list is the minimum that actually works: `clang cmake ninja-build
pkg-config libgtk-3-dev liblzma-dev` for the build, `libsecret-1-dev`
because `flutter_secure_storage_linux` is the only plugin in this app
needing anything past GTK, and `desktop-file-utils` + `dpkg-dev` because the
packaging script uses `desktop-file-validate` and `dpkg-shlibdeps`.

> **This workflow has never been observed running.** Every recorded run of
> this repository's Actions (35/35 as of 2026-08-28) failed in about five
> seconds with zero steps and `runner_id 0`, annotated *"The job was not
> started because recent account payments have failed or your spending limit
> needs to be increased."* That is an account-level block at
> <https://github.com/settings/billing>, not a workflow defect, and nothing
> in the file can lift it. Treat the first green run as the real
> verification.

---

## What works, and what does not

**Works on Linux:** connecting to a server, sessions, chat, the file
browser, diff review, the terminal, theming, window persistence, the
GitHub-releases update notice, the file picker (via zenity/qarma), secure
credential storage (via libsecret).

**Inert on Linux, by design:**

- **Termux hosting** (`lib/termux/`) — Android-only. The setup screens do
  nothing here.
- **Shorebird code-push** — Android-only; the notice layer no-ops off its
  `isAvailable` check.
- **Background-live notifications** — an Android foreground-service feature.
  `lib/background/live_background.dart` catches the missing-plugin case and
  reports the feature unavailable.
- **On-device voice** — `sherpa_onnx` ships Linux natives and `record` has a
  Linux backend, so the pieces exist, but `lib/voice/device.dart` is still
  gated on `Platform.isAndroid`. Enabling it is a separate slice.

**Runtime requirements users hit:**

- `libsecret-1-0` **and a running keyring daemon** (gnome-keyring, KWallet
  with the libsecret bridge). Without one, saved server passwords do not
  persist — this is the first thing to check when credentials vanish.
- `zenity` or `qarma` for file-picker dialogs.

**Still missing:**

- No visual verification of the window on a real display from this work (see
  below).
- No AppImage or Flatpak. The tarball is the portable format for now; the
  `.deb` is distro-narrow by construction.
- No code signing of any kind.
- Desktop keyboard shortcuts (Ctrl+Enter to send, Ctrl+K, Esc) are not part
  of this work.
- 32-bit and non-x86_64 architectures are not built.

---

## Verification log

Run and passing on this machine (Pop!_OS 24.04, clang 18):

- `flutter analyze` — clean.
- Full suite, `--concurrency=1`, in two halves — **1021 tests green**,
  including the 32 added here (24 window-geometry, 8 packaging-contract).
- `flutter build linux --release` with the `--gcc-install-dir` workaround —
  75 MB bundle.
- `scripts/package-linux.sh` end to end. From the run on 2026-08-30:

  ```
  opencode-linux-x64-1.0.29+30.tar.gz   27,579,750 bytes
    6c1d1d395be9fb79d963799b2685df01c74e7d4985425f23a62878476e99df82
  opencode_1.0.29+30_amd64.deb          27,551,400 bytes
    836055f2e16f87b89a7d5646b068ae33ae224790c8ac455a8b90f99b9aa721cd
  ```

  (These describe one build. Rebuilding changes them — see
  [Reproducibility](#reproducibility).)
- Packaging the same bundle twice — byte-identical artifacts.
- `install.sh` and `uninstall.sh` against a scratch prefix; the installed
  binary launches through its PATH symlink and finds its bundled libraries.
- `desktop-file-validate` on the generated entry — clean.
- `dpkg-deb -I` / `-c` on the package — correct control fields, root-owned
  tree, 644 data files, working `/usr/bin` symlink.
- `linux/packaging/gcc-install-dir.sh` — prints
  `/usr/lib/gcc/x86_64-linux-gnu/13`, exactly the flag verified by hand.

**Not verified, and why:**

- **The window has never been seen.** This build shell is a tty with no
  display, so the binary reaches GTK's `cannot open display` and stops.
  Window restore, multi-monitor re-homing, the maximized path, taskbar
  grouping and the icon are all reasoned from the APIs and unit-tested where
  they are pure logic, but nobody has watched them happen. Run the binary
  from a normal desktop session to confirm.
- **The CI workflow has never run** — account billing, above.
- **The `.deb` has not been installed** with `dpkg -i`; that needs root. Its
  contents and metadata were inspected instead.
