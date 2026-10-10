#!/usr/bin/env bash
# Read-only download of the public 1.2.0+52 APK into $SHOTS_CACHE (idempotent).
set -euo pipefail
D="${SHOTS_CACHE:?set SHOTS_CACHE}"; mkdir -p "$D"
ls "$D"/*.apk >/dev/null 2>&1 || gh release download 'v1.2.0+52' -R Eslamasabry/opencode-mobile-next -p '*.apk' -D "$D"
