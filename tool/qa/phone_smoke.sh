#!/usr/bin/env bash
# ARM phone smoke before a release (story E02-S92). See tool/qa/README.md.
#
#   tool/qa/phone_smoke.sh <adb-serial> <apk> [options]
#
# Installs <apk> with `adb install -r` only when the installed app's signer is
# the APK's signer, then drives the app by visible text through
# `uiautomator dump` + `input tap/text` and prints PASS/FAIL per check.
# It never uninstalls, never clears app data and never takes screenshots.
# Exit: 0 all PASS, 1 a check failed, 2 refused before touching the app,
# 3 stopped because another app took the foreground.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$here/phone_smoke.py" "$@"
