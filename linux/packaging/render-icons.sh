#!/usr/bin/env bash
# Rasterise the OpenCode icon into the freedesktop hicolor sizes.
#
# The generated PNGs are committed, so neither packaging nor CI needs an SVG
# rasteriser. Re-run this only when the SVG changes:
#
#   linux/packaging/render-icons.sh
#
# Requires ImageMagick (`convert`). Output is 8-bit and stripped of metadata
# so re-running on an unchanged SVG produces a byte-identical tree.
set -euo pipefail

readonly APP_ID="ai.opencode.opencode_mobile"
readonly SIZES=(16 24 32 48 64 128 256 512)

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SOURCE="$here/icons/$APP_ID.svg"

command -v convert >/dev/null 2>&1 || {
  echo "ERROR: ImageMagick 'convert' is required." >&2
  exit 1
}
test -f "$SOURCE" || {
  echo "ERROR: missing $SOURCE" >&2
  exit 1
}

for size in "${SIZES[@]}"; do
  out="$here/icons/hicolor/${size}x${size}/apps"
  mkdir -p "$out"
  convert -background none "$SOURCE" \
    -resize "${size}x${size}" \
    -depth 8 -strip \
    "png32:$out/$APP_ID.png"
  echo "wrote ${size}x${size}"
done

# The scalable entry is the SVG itself.
mkdir -p "$here/icons/hicolor/scalable/apps"
cp "$SOURCE" "$here/icons/hicolor/scalable/apps/$APP_ID.svg"
echo "wrote scalable"
