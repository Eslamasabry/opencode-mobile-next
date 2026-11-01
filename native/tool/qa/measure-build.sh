#!/usr/bin/env bash
# P8 build-cost probe (ADR §11 P8): wall time and peak resident memory of every JVM the build starts (the Gradle
# daemon, Kotlin daemon if any, test and worker JVMs). Usage: tool/qa/measure-build.sh <label> <gradle args...>
# Prints one line: label | seconds | peak total JVM RSS MiB | peak single JVM RSS MiB | exit code.
set -uo pipefail
cd "$(dirname "$0")/../.."
label=$1; shift
peak_total=0; peak_single=0
sample() {
  while :; do
    local rss; rss=$(ps -C java -o rss= 2>/dev/null | awk '{s+=$1; if ($1>m) m=$1} END {print s+0, m+0}')
    local total=${rss% *} single=${rss#* }
    (( total > peak_total )) && peak_total=$total
    (( single > peak_single )) && peak_single=$single
    echo "$peak_total $peak_single" > "${TMPDIR:-/tmp}/oc-measure.$$"
    sleep 0.5
  done
}
sample & sampler=$!
start=$(date +%s.%N)
./gradlew "$@" --console=plain > "${TMPDIR:-/tmp}/oc-measure-$label.log" 2>&1; code=$?
end=$(date +%s.%N)
sleep 0.6; kill $sampler 2>/dev/null; wait $sampler 2>/dev/null
read -r peak_total peak_single < "${TMPDIR:-/tmp}/oc-measure.$$"; rm -f "${TMPDIR:-/tmp}/oc-measure.$$"
printf '%s | %.1f s | %d MiB | %d MiB | exit %d\n' "$label" "$(echo "$end - $start" | bc)" $((peak_total/1024)) $((peak_single/1024)) $code
