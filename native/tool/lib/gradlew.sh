#!/usr/bin/env bash
# Runs native/gradlew from native/ (so "-p build-logic" resolves there), plain console, plus $OC_GRADLE_ARGS.
# A script, not a function, so flock can run it while holding the build slot.
cd "$(dirname "$0")/../.." || exit 2
# shellcheck disable=SC2086 # OC_GRADLE_ARGS is a space-separated list on purpose
exec ./gradlew --console=plain ${OC_GRADLE_ARGS:-} "$@"
