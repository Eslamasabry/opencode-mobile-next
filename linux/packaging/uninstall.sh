#!/usr/bin/env bash
# Remove an OpenCode install placed by install.sh.
#
#   ./uninstall.sh                 remove from ~/.local
#   ./uninstall.sh --prefix DIR    remove from somewhere else
#
# Only removes files this project owns. Your settings and saved servers live
# in ~/.config and ~/.local/share/opencode_mobile and are left alone; delete
# those by hand if you want a clean slate.
set -euo pipefail

readonly APP_ID="ai.opencode.opencode_mobile"

if [ "$(id -u)" -eq 0 ]; then
  prefix="/usr/local"
else
  prefix="$HOME/.local"
fi

while (($#)); do
  case "$1" in
    --prefix) prefix="${2:?--prefix needs a directory}"; shift 2 ;;
    -h|--help) sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 64 ;;
  esac
done

echo "==> removing OpenCode from $prefix"
rm -rf "$prefix/lib/opencode"
rm -f "$prefix/bin/opencode"
rm -f "$prefix/share/applications/$APP_ID.desktop"
rm -f "$prefix/share/metainfo/$APP_ID.metainfo.xml"
find "$prefix/share/icons/hicolor" \
  \( -name "$APP_ID.png" -o -name "$APP_ID.svg" \) -delete 2>/dev/null || true

if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database -q "$prefix/share/applications" 2>/dev/null || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
  gtk-update-icon-cache -q -t -f "$prefix/share/icons/hicolor" 2>/dev/null || true
fi

echo "==> removed (settings in ~/.config and ~/.local/share were kept)"
