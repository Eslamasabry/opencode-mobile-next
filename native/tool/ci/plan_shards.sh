#!/usr/bin/env bash
# Shard plan of the `plan` job (native-ci.yml), kept here so test_gate.py can run it.
#
#   tool/ci/plan_shards.sh <lane> <selection>      selection: ALL, or the module test tasks (whitespace separated)
#
# Prints key=value lines: mode (all|list), count (module-test shards), total (shards the matrix runs), tasks.
# count is 8 by default (full lane, and a fast run that fell back to ALL); for a fast selection it follows the number of
# non-quality tasks (more than 18: 8, more than 6: 4, otherwise 2) and never exceeds it. The fast lane adds one more shard,
# the last, that runs :quality:test alone, on every dispatch (a selection or ALL).
set -euo pipefail
lane=${1:?lane} sel=${2-ALL}   # an empty selection (nothing under native/ changed) is a list of nothing: only the quality shard
mode=all; tasks=""; count=8
if [ "$lane" = fast ] && [ "$(printf '%s' "$sel" | tr -d '[:space:]')" != ALL ]; then
  mode=list; tasks="$(printf '%s\n' $sel | tr '\n' ' ')"
  n=$(printf '%s\n' $sel | grep -v '^:quality:test$' | grep -c . || true)
  count=$(( n > 18 ? 8 : (n > 6 ? 4 : 2) )); [ "$count" -le "$n" ] || count=$n
fi
total=$count; [ "$lane" = fast ] && total=$(( count + 1 ))
echo "mode=$mode"; echo "count=$count"; echo "total=$total"; echo "tasks=$tasks"
