#!/usr/bin/env bash
# Renders the still of every shader look, dark and light, through the real page ground (KitLiveBackground on desktop
# Skia, the same shader text Android runs as AGSL) behind a home list of real kit rows, into this folder.
# Usage: docs/design/live-backgrounds-native/make-stills.sh [look,look,...]   (run from the repo root; needs ffmpeg)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
export LIVE_STILLS_OUT="$here"
if [ $# -gt 0 ]; then export LIVE_STILLS_ONLY="$1"; fi
(cd "$here/../../../native" && ./gradlew :ui:kit:desktopTest --tests 'io.github.eslamasabry.oc.ui.kit.live.LiveLookStills' --rerun --console=plain)
# Saved at 720x1600 in 256 colours, so the stills stay small enough for git.
for png in "$here"/*.png; do
  ffmpeg -loglevel error -y -i "$png" \
    -vf "scale=720:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse" "$png.tmp.png"
  mv "$png.tmp.png" "$png"
done
