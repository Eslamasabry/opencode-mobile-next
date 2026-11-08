#!/usr/bin/env bash
# Prints the Gradle desktop-test tasks of shard <index> of <count> (round-robin over the sorted module list),
# one per line. Same discovery rule as tool/lib/common.sh oc_module_test_tasks: KMP modules run desktopTest,
# JVM modules and :desktopApp/:quality run test. Android host tests (Robolectric/Roborazzi goldens) are not run here.
set -euo pipefail
idx=${1:?shard index (0-based)} count=${2:?shard count}
cd "$(dirname "$0")/../.."
{
  for layer in core protocol engine ui feature android tools; do
    for bf in "$layer"/*/build.gradle.kts; do
      [ -f "$bf" ] || continue
      m=":${bf%/build.gradle.kts}"; m="${m//\//:}"
      if grep -qE 'id\("oc\.kmp\.' "$bf"; then echo "$m:desktopTest"
      elif grep -qE 'id\("oc\.jvm\.library"\)|kotlin\("jvm"\)' "$bf"; then echo "$m:test"; fi
    done
  done
  for m in desktopApp quality; do [ -f "$m/build.gradle.kts" ] && echo ":$m:test"; done
} | sort | awk -v i="$idx" -v n="$count" '(NR - 1) % n == i'
