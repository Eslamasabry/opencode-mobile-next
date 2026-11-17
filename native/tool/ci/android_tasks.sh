#!/usr/bin/env bash
# Android units, plus KMP Android host tests for the complete candidate lane. No device tests.
set -euo pipefail
cd "$(dirname "$0")/../.."
for d in android/*/; do
  [ -d "$d/src/test" ] && echo ":android:$(basename "$d"):test"
done
echo ':androidApp:testPreviewDebugUnitTest'
if [ "${1:-}" = candidate ]; then
  for bf in {core,protocol,engine,ui,feature,tools}/*/build.gradle.kts; do
    [ -f "$bf" ] || continue
    d=${bf%/build.gradle.kts}
    [ -d "$d/src/androidHostTest" ] || continue
    m=":${d//\//:}"
    echo "$m:testAndroidHostTest"
  done
fi
