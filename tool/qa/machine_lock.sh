#!/usr/bin/env bash
# Shares this PC's heavy work between many agents (STANDARDS.md PROC-16, G43).
#
#   tool/qa/machine_lock.sh test  -- flutter test -j 1 test/foo_test.dart
#   tool/qa/machine_lock.sh analyze -- flutter analyze lib/ui/kit
#   tool/qa/machine_lock.sh build -- flutter build apk --release ...
#
# test and analyze share OC_TEST_SLOTS slots (default 4); build takes the one
# Gradle lock every build on this machine already uses. The command runs with
# the slot held and the slot is released when it exits, even on failure.
set -euo pipefail

kind="${1:-}"
shift || true
[[ "${1:-}" == "--" ]] && shift
if [[ -z "$kind" || $# -eq 0 ]]; then
  echo "usage: $0 test|analyze|build -- <command...>" >&2
  exit 64
fi

dir="${OC_LOCK_DIR:-/home/eslam/Storage/tmp/oc-locks}"
mkdir -p "$dir"

case "$kind" in
  build)
    build_lock="${OC_BUILD_LOCK_FILE:-/home/eslam/Storage/tmp/oc-build.lock}"
    mkdir -p "$(dirname "$build_lock")"
    exec flock "$build_lock" "$@"
    ;;
  test | analyze)
    # Tests run in UTC like CI, so goldens that show clock times match there.
    export TZ="${OC_TEST_TZ:-UTC}"
    slots="${OC_TEST_SLOTS:-4}"
    waited=0
    while true; do
      for ((i = 1; i <= slots; i++)); do
        exec {fd}>"$dir/test.$i"
        if flock -n "$fd"; then
          [[ $waited -gt 0 ]] && echo "machine_lock: got test slot $i after ${waited}s" >&2
          status=0
          "$@" || status=$?
          flock -u "$fd"
          exit $status
        fi
        exec {fd}>&-
      done
      sleep 3
      waited=$((waited + 3))
      if ((waited % 60 == 0)); then echo "machine_lock: waiting for a test slot (${waited}s)" >&2; fi
    done
    ;;
  *)
    echo "unknown kind: $kind (test, analyze or build)" >&2
    exit 64
    ;;
esac
