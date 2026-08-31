#!/usr/bin/env bash
# Turn a built Flutter Linux bundle into distributable artifacts.
#
#   scripts/package-linux.sh [options]
#
# Produces, in build/linux/packages:
#   opencode-linux-x64-<version>.tar.gz   bundle + icons + .desktop + installer
#   opencode_<version>_amd64.deb          same payload under /usr
#   SHA256SUMS                            checksums for both
#
# The script never builds by itself unless asked (--build): CI builds once and
# packages the result, and a local run should package exactly the bundle you
# just tested.
#
# Reproducibility: packaging is deterministic, so the same bundle packaged
# twice yields byte-identical artifacts. The *Flutter build* is not itself
# reproducible (the linker stamps build ids), so rebuilding first will change
# the hashes even on an unchanged commit. Publish the checksums of the
# artifacts CI actually uploaded rather than ones regenerated later.
#
# Options:
#   --bundle DIR    built bundle (default build/linux/x64/release/bundle)
#   --out DIR       output directory (default build/linux/packages)
#   --version V     override the version (default: from pubspec.yaml)
#   --build         run `flutter build linux --release` first
#   --skip-deb      tarball only (for hosts without dpkg-deb)
#   -h, --help      this text
set -euo pipefail

readonly APP_ID="io.github.eslamasabry.opencode_mobile"
readonly BINARY_NAME="opencode_mobile"
readonly DEB_PACKAGE="opencode"
readonly ICON_SIZES=(16 24 32 48 64 128 256 512)

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bundle_dir="$repo_root/build/linux/x64/release/bundle"
out_dir="$repo_root/build/linux/packages"
packaging_dir="$repo_root/linux/packaging"
version=""
do_build=0
skip_deb=0

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

note() { echo "==> $*"; }

usage() { sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

while (($#)); do
  case "$1" in
    --bundle) bundle_dir="$(readlink -f "${2:?--bundle needs a directory}")"; shift 2 ;;
    --out) out_dir="$2"; shift 2 ;;
    --version) version="$2"; shift 2 ;;
    --build) do_build=1; shift ;;
    --skip-deb) skip_deb=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; fail "unknown option: $1" ;;
  esac
done

for tool in tar gzip sha256sum sed awk find; do
  command -v "$tool" >/dev/null 2>&1 || fail "missing required tool: $tool"
done

# ---------------------------------------------------------------- version ---
pubspec_version="$(
  sed -n 's/^version:[[:space:]]*\([^[:space:]#]*\).*/\1/p' "$repo_root/pubspec.yaml" | head -1
)"
[ -n "$pubspec_version" ] || fail "could not read version from pubspec.yaml"
version="${version:-$pubspec_version}"
# `1.0.29+30` -> marketing `1.0.29`, build `30`. The build number is the
# project's monotonic release ordering and is what lib/update/
# desktop_release_check.dart compares against the GitHub release tag, so it
# has to survive into the artifact name.
marketing_version="${version%%+*}"
build_number="${version#*+}"
[ "$build_number" != "$version" ] || fail "version '$version' has no +buildNumber"

# ------------------------------------------------------------------ build ---
if ((do_build)); then
  note "flutter build linux --release"
  # Only hosts whose clang cannot link C++ on its own get the extra flag; the
  # helper prints nothing on a healthy toolchain. See its header for why.
  gcc_dir="$("$packaging_dir/gcc-install-dir.sh")"
  if [ -n "$gcc_dir" ]; then
    note "clang needs a toolchain hint: --gcc-install-dir=$gcc_dir"
    export CXXFLAGS="${CXXFLAGS:-} --gcc-install-dir=$gcc_dir"
    export LDFLAGS="${LDFLAGS:-} --gcc-install-dir=$gcc_dir"
  fi
  flutter build linux --release
fi

# ----------------------------------------------------------------- verify ---
[ -d "$bundle_dir" ] || fail "no bundle at $bundle_dir (run with --build?)"
[ -x "$bundle_dir/$BINARY_NAME" ] || fail "no executable $bundle_dir/$BINARY_NAME"
[ -d "$bundle_dir/data" ] || fail "no $bundle_dir/data"
[ -d "$bundle_dir/lib" ] || fail "no $bundle_dir/lib"

# Refuse to ship a bundle from a different version than the one being
# labelled. version.json is what package_info_plus reads on Linux, which is
# what the in-app update check compares against the release tag.
version_json="$bundle_dir/data/flutter_assets/version.json"
[ -f "$version_json" ] || fail "no $version_json"
bundle_build="$(sed -n 's/.*"build_number":"\([^"]*\)".*/\1/p' "$version_json")"
bundle_marketing="$(sed -n 's/.*"version":"\([^"]*\)".*/\1/p' "$version_json")"
if [ "$bundle_build" != "$build_number" ] || [ "$bundle_marketing" != "$marketing_version" ]; then
  fail "bundle is ${bundle_marketing}+${bundle_build} but packaging $version; rebuild the bundle"
fi

for asset in "$packaging_dir/$APP_ID.desktop" \
             "$packaging_dir/$APP_ID.metainfo.xml"; do
  [ -f "$asset" ] || fail "missing packaging asset: $asset"
done
for size in "${ICON_SIZES[@]}"; do
  icon="$packaging_dir/icons/hicolor/${size}x${size}/apps/$APP_ID.png"
  [ -f "$icon" ] || fail "missing icon: $icon (run linux/packaging/render-icons.sh)"
done

# Fixed timestamp so repeated runs on the same commit produce the same
# tarball. Falls back to the epoch outside a git checkout.
if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
  SOURCE_DATE_EPOCH="$(git -C "$repo_root" log -1 --pretty=%ct 2>/dev/null || echo 0)"
fi
export SOURCE_DATE_EPOCH
readonly BUILD_DATE="$(date -u -d "@$SOURCE_DATE_EPOCH" +%Y-%m-%d 2>/dev/null || echo 1970-01-01)"
readonly RFC_DATE="$(date -u -d "@$SOURCE_DATE_EPOCH" -R 2>/dev/null || echo 'Thu, 01 Jan 1970 00:00:00 +0000')"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$out_dir"
out_dir="$(cd "$out_dir" && pwd)"

# ---------------------------------------------------------- shared payload ---
# Lays out the freedesktop half of the payload under $1, with $2 as the path
# the .desktop file's Exec should point at once installed.
stage_share() {
  local root="$1" exec_path="$2"
  install -d "$root/share/applications" "$root/share/metainfo"
  sed "s|^Exec=.*|Exec=$exec_path|" "$packaging_dir/$APP_ID.desktop" \
    > "$root/share/applications/$APP_ID.desktop"
  # Stamp the version being packaged into the AppStream release list.
  sed "s|<release version=\"0.0.0\" date=\"1970-01-01\"/>|<release version=\"$marketing_version\" date=\"$BUILD_DATE\"/>|" \
    "$packaging_dir/$APP_ID.metainfo.xml" \
    > "$root/share/metainfo/$APP_ID.metainfo.xml"
  # Redirection obeys the caller's umask; packaged data files must be 644.
  chmod 644 "$root/share/applications/$APP_ID.desktop" \
            "$root/share/metainfo/$APP_ID.metainfo.xml"
  for size in "${ICON_SIZES[@]}"; do
    install -Dm644 \
      "$packaging_dir/icons/hicolor/${size}x${size}/apps/$APP_ID.png" \
      "$root/share/icons/hicolor/${size}x${size}/apps/$APP_ID.png"
  done
  install -Dm644 "$packaging_dir/icons/hicolor/scalable/apps/$APP_ID.svg" \
    "$root/share/icons/hicolor/scalable/apps/$APP_ID.svg"
}

# ---------------------------------------------------------------- tarball ---
readonly TAR_NAME="opencode-linux-x64-$version"
tar_root="$work/$TAR_NAME"
note "staging tarball tree"
install -d "$tar_root/lib/opencode"
cp -a "$bundle_dir/." "$tar_root/lib/opencode/"
stage_share "$tar_root" '$PREFIX/lib/opencode/'"$BINARY_NAME"
install -Dm644 "$repo_root/LICENSE" "$tar_root/LICENSE"
install -Dm755 "$packaging_dir/install.sh" "$tar_root/install.sh"
install -Dm755 "$packaging_dir/uninstall.sh" "$tar_root/uninstall.sh"

sed -e "s/@VERSION@/$version/g" \
    -e "s/@APP_ID@/$APP_ID/g" \
    -e "s/@BINARY@/$BINARY_NAME/g" \
    "$packaging_dir/README.tarball.md" > "$tar_root/README.md"

tarball="$out_dir/$TAR_NAME.tar.gz"
note "writing $tarball"
# --sort/--mtime/--owner/--numeric-owner plus `gzip -n` make the archive a
# function of the tree alone, so the same commit repackages byte-identically.
tar --sort=name \
    --mtime="@$SOURCE_DATE_EPOCH" \
    --owner=0 --group=0 --numeric-owner \
    --format=gnu \
    -C "$work" -cf - "$TAR_NAME" \
  | gzip -9n > "$tarball"

# -------------------------------------------------------------------- deb ---
deb_path=""
if ((skip_deb)); then
  note "skipping .deb (--skip-deb)"
elif ! command -v dpkg-deb >/dev/null 2>&1; then
  note "skipping .deb (dpkg-deb not installed)"
else
  arch="$(dpkg --print-architecture 2>/dev/null || echo amd64)"
  deb_root="$work/deb"
  note "staging .deb tree ($arch)"
  install -d "$deb_root/usr/lib/opencode" "$deb_root/usr/bin" "$deb_root/DEBIAN"
  cp -a "$bundle_dir/." "$deb_root/usr/lib/opencode/"
  # The Flutter binary resolves its data/ and lib/ from $ORIGIN, and the
  # kernel resolves the symlink before $ORIGIN is expanded, so a link on PATH
  # is safe.
  ln -sf "../lib/opencode/$BINARY_NAME" "$deb_root/usr/bin/$DEB_PACKAGE"
  stage_share "$deb_root/usr" "/usr/lib/opencode/$BINARY_NAME"
  # Validate the copy with a real absolute Exec (the tarball's carries a
  # $PREFIX placeholder the installer substitutes).
  if command -v desktop-file-validate >/dev/null 2>&1; then
    desktop-file-validate "$deb_root/usr/share/applications/$APP_ID.desktop" ||
      fail "the generated .desktop entry is not valid"
  fi
  # stage_share writes to <root>/share; the deb wants /usr/share.
  install -d "$deb_root/usr/share/doc/$DEB_PACKAGE"
  install -Dm644 "$repo_root/LICENSE" \
    "$deb_root/usr/share/doc/$DEB_PACKAGE/copyright"

  # Depends: derived from what the shipped ELF objects actually need.
  # dpkg-shlibdeps wants a debian/control to exist, and -l points it at the
  # bundle's private libraries so those resolve instead of being reported as
  # missing dependencies.
  depends=""
  if command -v dpkg-shlibdeps >/dev/null 2>&1; then
    shlib_dir="$work/shlib"
    install -d "$shlib_dir/debian"
    cat > "$shlib_dir/debian/control" <<EOF
Source: $DEB_PACKAGE
Package: $DEB_PACKAGE
Architecture: $arch
EOF
    mapfile -t elf_objects < <(
      printf '%s\n' "$deb_root/usr/lib/opencode/$BINARY_NAME"
      find "$deb_root/usr/lib/opencode/lib" -name '*.so' -print | sort
    )
    if (cd "$shlib_dir" && dpkg-shlibdeps \
          --ignore-missing-info \
          -l"$deb_root/usr/lib/opencode/lib" \
          -O -e"${elf_objects[@]}" > "$work/shlibdeps.txt" 2> "$work/shlibdeps.log"); then
      depends="$(sed -n 's/^shlibs:Depends=//p' "$work/shlibdeps.txt")"
    fi
    [ -n "$depends" ] || {
      note "dpkg-shlibdeps produced nothing; see $work/shlibdeps.log"
      cat "$work/shlibdeps.log" >&2 || true
    }
  fi
  if [ -z "$depends" ]; then
    # Curated floor matching the GTK/X11 stack the runner links against.
    note "falling back to the static dependency list"
    depends="libc6, libgtk-3-0 | libgtk-3-0t64, libglib2.0-0 | libglib2.0-0t64, libstdc++6, libsecret-1-0"
  fi
  note "Depends: $depends"

  installed_kb="$(du -sk "$deb_root" | awk '{print $1}')"
  cat > "$deb_root/DEBIAN/control" <<EOF
Package: $DEB_PACKAGE
Version: $version
Section: devel
Priority: optional
Architecture: $arch
Maintainer: OpenCode <noreply@github.com>
Installed-Size: $installed_kb
Depends: $depends
Homepage: https://github.com/Eslamasabry/opencode-mobile
Description: OpenCode desktop client for opencode servers
 OpenCode connects to an opencode server you run and gives you its sessions,
 chat transcript, file browser, diff review and terminal.
 .
 The Linux build is experimental. Android-only features (Termux hosting,
 code-push patching, background-live notifications) are inert on desktop.
EOF

  cat > "$deb_root/DEBIAN/postinst" <<'EOF'
#!/bin/sh
set -e
if [ "$1" = configure ]; then
  if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database -q /usr/share/applications || true
  fi
  if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true
  fi
fi
EOF
  cat > "$deb_root/DEBIAN/postrm" <<'EOF'
#!/bin/sh
set -e
if [ "$1" = remove ] || [ "$1" = purge ]; then
  if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database -q /usr/share/applications || true
  fi
  if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true
  fi
fi
EOF
  chmod 755 "$deb_root/DEBIAN/postinst" "$deb_root/DEBIAN/postrm"

  printf '%s (%s) unstable; urgency=low\n\n  * Packaged from the repository tree.\n\n -- OpenCode <noreply@github.com>  %s\n' \
    "$DEB_PACKAGE" "$version" "$RFC_DATE" \
    | gzip -9n > "$deb_root/usr/share/doc/$DEB_PACKAGE/changelog.Debian.gz"
  chmod 644 "$deb_root/usr/share/doc/$DEB_PACKAGE/changelog.Debian.gz"

  find "$deb_root" -type d -exec chmod 755 {} +
  chmod 755 "$deb_root/usr/lib/opencode/$BINARY_NAME"
  # dpkg-deb copies each file's mtime into data.tar, so freshly generated
  # control files and sed output would otherwise make every run produce a
  # different .deb. Flattening the tree to SOURCE_DATE_EPOCH makes the
  # package a function of its contents alone.
  find "$deb_root" -exec touch --no-dereference \
    --date="@$SOURCE_DATE_EPOCH" {} +

  deb_path="$out_dir/${DEB_PACKAGE}_${version}_${arch}.deb"
  note "writing $deb_path"
  # --root-owner-group keeps root:root without needing fakeroot.
  dpkg-deb --root-owner-group -Zgzip --build "$deb_root" "$deb_path" >/dev/null
fi

# ------------------------------------------------------------- checksums ----
note "checksums"
(
  cd "$out_dir"
  artifacts=("$(basename "$tarball")")
  [ -n "$deb_path" ] && artifacts+=("$(basename "$deb_path")")
  sha256sum "${artifacts[@]}" | tee SHA256SUMS
)

echo
note "done: $out_dir"
ls -lh "$out_dir"
