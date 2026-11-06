#!/usr/bin/env bash
# Upgrade gate (E00-S09): proves the native APK replaces the released Flutter app in place and keeps everything.
#
#   native/tool/qa/upgrade_e2e.sh --flutter-apk <R.apk> --native-apk <N.apk> --expect-signer <sha256> --avd <AVD>
#       [--use-running] [--reseed] [--snapshot flutter-2.1.4-seeded] [--fixtures-env FILE] [--lock FILE]
#       [--evidence-dir DIR] [--keep-emulator] [--dry-run]
#
# 1. Refuses unless both APKs are signed by --expect-signer only, share the package and the native versionCode is
#    higher (aapt2 + apksigner from PATH or $ANDROID_HOME/build-tools).
# 2. Under the emulator lock ($OC_EMULATOR_LOCK), boots AVD <AVD> on port 5584 (emulator-5584): from the snapshot
#    flutter-2.1.4-seeded when it exists with its fixture state, else wiped so E2E-39 Part A seeds it (and the snapshot
#    is saved for next time).
# 3. Runs `run.py --app native --flows E2E-39`: Part A/B on Flutter, `adb install -r` of the native APK, Part C.
# 4. Prints one PASS/FAIL line per assertion and writes docs/qa/native-switch-<date>/README.md with versions, signers,
#    verdicts and one screenshot per screen.
# Headless (-no-window). Exit: 0 every assertion passed, 1 one failed, 2 could not run (refused, no device, no tool).
# Never targets anything but emulator-5584, never uninstalls, never prints a password.
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
case "${1:-}" in -h | --help) sed -n '2,17p' "$0"; exit 0 ;; esac
command -v python3 >/dev/null || { echo "FAIL tools: python3 is needed"; exit 2; }
exec python3 -I "$here/../../e2e/runner/upgrade.py" "$@"
