#!/usr/bin/env bash
# Release smoke: four checks on one connected Android device or emulator (update keeps conversations, the phone's own
# server starts, a prompt gets a reply, a permission question is answered). Writes the result file the release script
# requires. Details and the checks: native/tool/qa/release_smoke.py.
#
#   OC_SMOKE_SERIAL=<adb serial> native/tool/qa/release_smoke.sh --new-apk <build.apk> [--previous-apk <old.apk>]
#       [--out DIR] [--commit SHA]        optional env: OC_SMOKE_PROJECT (a project name to pick for a new conversation)
#
# The serial is only ever read from the environment. Exit: 0 all four passed, 1 a check failed, 2 could not run.
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
case "${1:-}" in -h | --help) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;; esac
command -v python3 >/dev/null || { echo "release smoke: python3 is needed" >&2; exit 2; }
command -v "${OC_ADB:-adb}" >/dev/null || { echo "release smoke: adb is needed" >&2; exit 2; }
exec python3 -I "$here/release_smoke.py" "$@"
