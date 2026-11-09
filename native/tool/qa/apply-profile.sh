#!/bin/bash
# apply-profile.sh <out-dir>: copy the profiles that tool/qa/device-bench.sh profile collected into :androidApp.
#   baseline-prof.txt -> androidApp/src/main/baseline-prof.txt          (profileinstaller / ART AOT of hot methods)
#   startup-prof.txt  -> androidApp/src/main/baselineProfiles/startup-prof.txt (dex layout: startup classes first, R8 + AGP 8.2+)
set -euo pipefail
out=${1:?out dir from device-bench.sh profile}
cd "$(dirname "$0")/../.."
base=$(find "$out" -name '*baseline-prof.txt' | head -1)
start=$(find "$out" -name '*startup-prof.txt' | head -1)
[ -n "$base" ] && [ -n "$start" ] || { echo "no baseline-prof.txt / startup-prof.txt under $out" >&2; exit 1; }
cp "$base" androidApp/src/main/baseline-prof.txt
mkdir -p androidApp/src/main/baselineProfiles
cp "$start" androidApp/src/main/baselineProfiles/startup-prof.txt
wc -l androidApp/src/main/baseline-prof.txt androidApp/src/main/baselineProfiles/startup-prof.txt
