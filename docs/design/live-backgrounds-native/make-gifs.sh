#!/usr/bin/env bash
# Renders the live-background stills and frames through Skia on desktop, then turns each frame set into a GIF.
# Usage: docs/design/live-backgrounds-native/make-gifs.sh [look,look,...]   (needs ffmpeg; run from the repo root)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
export LIVE_STILLS_OUT="$here"
[ $# -gt 0 ] && export LIVE_STILLS_ONLY="$1"
(cd "$here/../../../native" && ./gradlew :ui:kit:desktopTest --tests 'io.github.eslamasabry.oc.ui.kit.live.LiveLooksBStills' --console=plain)
for dir in "$here"/frames/*/; do
  name="$(basename "$dir")"
  ffmpeg -loglevel error -y -framerate 10 -i "$dir/f%02d.png" \
    -vf "scale=270:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=64:stats_mode=full[p];[b][p]paletteuse=dither=sierra2_4a" \
    "$here/$name.gif"
done
rm -rf "$here/frames"
# 256-colour PNGs keep the stills small enough for git.
for png in "$here"/*.png; do ffmpeg -loglevel error -y -i "$png" -vf "split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse" "$png.tmp.png" && mv "$png.tmp.png" "$png"; done
