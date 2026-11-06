#!/usr/bin/env bash
# versionCode allocator (E01-S09, plan 05 §9.1): one counter and one ledger (native/BUILD_NUMBERS.md) for every APK.
#
#   native/tool/version_code.sh next     [--kind build|release] [--installed N]... [--device SERIAL]...
#   native/tool/version_code.sh allocate --kind build|release --for "<what>" [--code N] [--installed N]... [--device S]...
#   native/tool/version_code.sh seen     --code N --for "<where, what>"
#   native/tool/version_code.sh check
#
# next prints the number a build would get, writing nothing. allocate appends the ledger row, records any higher number
# a device holds as a `seen` row, and writes native/version.properties. A release is the next multiple of 10 above the
# largest known number, a build is largest + 1, never below 10000. A number already used, or not above every known
# one, is refused. --device reads `adb shell dumpsys package` (read-only); OC_ADB overrides the adb binary.
# Exit: 0 done, 1 refused or the check failed, 2 could not run.
set -uo pipefail
case "${1:-}" in
  -h | --help) sed -n '2,13p' "$0"; exit 0 ;;
  "") sed -n '2,13p' "$0" >&2; exit 2 ;;
esac
command -v python3 >/dev/null || { echo "error: python3 is required" >&2; exit 2; }
exec python3 -I "$(cd "$(dirname "$0")" && pwd)/lib/version_codes.py" "$@"
