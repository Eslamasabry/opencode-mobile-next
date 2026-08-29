#!/usr/bin/env bash
# Install OpenCode from an unpacked release tarball.
#
#   ./install.sh                 install into ~/.local (no root needed)
#   ./install.sh --prefix DIR    install somewhere else
#   sudo ./install.sh            installs into /usr/local
#
# Places the runtime under <prefix>/lib/opencode, a launcher on PATH at
# <prefix>/bin/opencode, and the .desktop entry, icons and AppStream metadata
# under <prefix>/share so the shell shows the app with its own icon.
set -euo pipefail

readonly APP_ID="ai.opencode.opencode_mobile"
readonly BINARY_NAME="opencode_mobile"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ~/.local is the freedesktop per-user prefix: ~/.local/bin is on PATH on
# most distributions and ~/.local/share is always searched for .desktop files
# and icons, so a user install needs no root and no PATH surgery.
if [ "$(id -u)" -eq 0 ]; then
  prefix="/usr/local"
else
  prefix="$HOME/.local"
fi

while (($#)); do
  case "$1" in
    --prefix) prefix="${2:?--prefix needs a directory}"; shift 2 ;;
    -h|--help) sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 64 ;;
  esac
done

fail() { echo "ERROR: $*" >&2; exit 1; }

[ -x "$here/lib/opencode/$BINARY_NAME" ] ||
  fail "run this from the unpacked tarball (no lib/opencode/$BINARY_NAME here)"

mkdir -p "$prefix" 2>/dev/null ||
  fail "cannot create $prefix — pass --prefix, or re-run with sudo"
[ -w "$prefix" ] || fail "$prefix is not writable — pass --prefix, or use sudo"

echo "==> installing into $prefix"

# Replace wholesale rather than merging, so an upgrade cannot leave a stale
# plugin .so behind for the new binary to load.
rm -rf "$prefix/lib/opencode"
mkdir -p "$prefix/lib"
cp -a "$here/lib/opencode" "$prefix/lib/opencode"
chmod 755 "$prefix/lib/opencode/$BINARY_NAME"

mkdir -p "$prefix/bin"
# The binary resolves data/ and lib/ relative to its own real path, and the
# kernel resolves this symlink first, so launching through it is safe.
ln -sfn "$prefix/lib/opencode/$BINARY_NAME" "$prefix/bin/opencode"

mkdir -p "$prefix/share/applications" "$prefix/share/metainfo"
# The staged Exec line carries a literal $PREFIX placeholder.
sed "s|\$PREFIX|$prefix|g" "$here/share/applications/$APP_ID.desktop" \
  > "$prefix/share/applications/$APP_ID.desktop"
chmod 644 "$prefix/share/applications/$APP_ID.desktop"
install -m644 "$here/share/metainfo/$APP_ID.metainfo.xml" \
  "$prefix/share/metainfo/$APP_ID.metainfo.xml"

find "$here/share/icons" -type f | while read -r icon; do
  target="$prefix/share/icons/${icon#"$here/share/icons/"}"
  install -Dm644 "$icon" "$target"
done

if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database -q "$prefix/share/applications" || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
  gtk-update-icon-cache -q -t -f "$prefix/share/icons/hicolor" || true
fi

echo "==> installed"
echo "    binary : $prefix/bin/opencode"
echo "    runtime: $prefix/lib/opencode"
echo "    entry  : $prefix/share/applications/$APP_ID.desktop"
case ":$PATH:" in
  *":$prefix/bin:"*) ;;
  *) echo
     echo "NOTE: $prefix/bin is not on your PATH. Add it, or launch OpenCode"
     echo "      from your applications menu."
     ;;
esac
if [ "$prefix" != "/usr/local" ] && [ "$prefix" != "/usr" ]; then
  case ":${XDG_DATA_DIRS:-/usr/local/share:/usr/share}:" in
    *":$prefix/share:"*) ;;
    *) echo
       echo "NOTE: $prefix/share is not in XDG_DATA_DIRS. Most desktops read"
       echo "      ~/.local/share anyway; if the launcher does not appear, log"
       echo "      out and back in."
       ;;
  esac
fi
echo
echo "Uninstall with: ./uninstall.sh --prefix $prefix"
