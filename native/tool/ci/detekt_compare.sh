#!/usr/bin/env bash
# Scratch (never merged): detekt.sh on this checkout and on a base commit, then the findings only this checkout has.
set -uo pipefail
base=$1; shift
git -C .. fetch --no-tags --depth=80 origin "$base" >/dev/null 2>&1
git -C .. worktree add --detach "$RUNNER_TEMP/base" "$base" >/dev/null
run() { (cd "$1" && bash tool/qa/detekt.sh "$@" --base origin/native/glass-next) ; }
bash tool/qa/detekt.sh "$@" --base origin/native/glass-next --report-dir "$RUNNER_TEMP/head" > "$RUNNER_TEMP/head.log" 2>&1; echo "DETEKT EXIT head $?"
(cd "$RUNNER_TEMP/base/native" && bash tool/qa/detekt.sh "$@" --base origin/native/glass-next --report-dir "$RUNNER_TEMP/basereport" > "$RUNNER_TEMP/base.log" 2>&1); echo "DETEKT EXIT base $?"
norm() { grep -E '^  [A-Za-z]+ ' "$1" | sed -E 's#/home/runner/work/[^ ]*/native/#native/#; s#'"$RUNNER_TEMP"'/base/##; s#:[0-9]+:[0-9]+ - Signature#:L - Signature#' | sort; }
norm "$RUNNER_TEMP/head.log" > "$RUNNER_TEMP/h.txt"; norm "$RUNNER_TEMP/base.log" > "$RUNNER_TEMP/b.txt"
echo "findings head $(wc -l < "$RUNNER_TEMP/h.txt"), base $(wc -l < "$RUNNER_TEMP/b.txt")"
echo "ONLY ON HEAD:"; comm -23 "$RUNNER_TEMP/h.txt" "$RUNNER_TEMP/b.txt" | cut -c1-300
echo "ONLY ON BASE:"; comm -13 "$RUNNER_TEMP/h.txt" "$RUNNER_TEMP/b.txt" | cut -c1-300
grep -E '^detekt|^ratchet' "$RUNNER_TEMP/head.log" "$RUNNER_TEMP/base.log"
