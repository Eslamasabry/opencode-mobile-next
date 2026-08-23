#!/usr/bin/env bash
# Release & patch this app through Shorebird code push.
#
# First time setup (one-off):
#   1. curl --proto '=https' --tlsv1.2 https://raw.githubusercontent.com/shorebirdtech/install/main/install.sh -sSf | bash
#   2. shorebird login            # opens browser, uses your Shorebird account
#   3. shorebird doctor           # should print "No issues detected!"
#
# Usage:
#   ./scripts/release.sh          # ship a new store/APK release
#   ./scripts/release.sh patch    # ship an OTA Dart-only patch to the latest release
set -euo pipefail

cd "$(dirname "$0")/.."
export PATH="$HOME/.shorebird/bin:$PATH"

VERSION=$(grep '^version:' pubspec.yaml | sed 's/version: *//')

if [[ "${1:-release}" == "patch" ]]; then
  echo "==> Shipping OTA patch for $VERSION"
  shorebird patch android --release-version "$VERSION" --flavor '' --dry-run && \
    shorebird patch android --release-version "$VERSION" --flavor ''
else
  echo "==> Shipping new release $VERSION"
  shorebird release android --release-version "$VERSION" \
    --artifact apk
  echo "==> APK: ./build/app/outputs/flutter-apk/app-release.apk"
fi
