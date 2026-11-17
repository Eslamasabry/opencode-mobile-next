#!/usr/bin/env bash
# base_check.sh <base> <line-ref> <branch>: the fast lane's `base` must not be far older than where the branch left the line.
# The fast lane picks the modules `git diff <base>...HEAD` touches and runs EVERY module when more than 30 changed; with a base
# copied from an old how-to that happens on every dispatch, wastes a full run and (with the detekt ratchet) compares the branch
# with code it never had. The selection is computed from <base> and from merge-base(HEAD, <line-ref>), the right base:
#   <base> gives ALL and the merge-base does not:  a work branch is REFUSED (exit 1) with the exact command to rerun;
#   the line itself (native/glass-next, native/next, native/screens-merge*), its throwaway copies native/night-*, and master are only warned (exit 0).
# Details go to stdout; exit 2 when it cannot check (no git, unknown line ref): the caller does not block on that.
set -uo pipefail
base=${1:?base ref or sha}; line=${2:?line ref}; branch=${3:?branch name}
cd "$(dirname "$0")/../.."
mb=$(git merge-base HEAD "$line" 2>/dev/null) || { echo "base_check: cannot find the merge-base with $line; not checked"; exit 2; }
short=${mb:0:12}
sel() { tool/ci/fast_tasks.sh "$1" 2>/dev/null || echo ALL; }
from_base=$(sel "$base"); from_mb=$(sel "$mb")
if [ "$from_base" != ALL ] || [ "$from_mb" = ALL ]; then
  echo "base_check: base=$base selects $(printf '%s\n' "$from_base" | grep -c . ) module task(s); fine"; exit 0
fi
n=$(printf '%s\n' "$from_mb" | grep -c . || true)
msg="base=$base makes the fast lane run ALL modules, but this branch left $line at $short, where it would run $n module task(s)."
cmd="gh workflow run native-ci.yml --ref $branch -f lane=fast -f runner=github -f base=$short"
# At the line's own tip the merge-base IS HEAD: "base = HEAD" would run nothing, so never offer it; the answer is the line ref.
at_tip=0; [ "$mb" = "$(git rev-parse HEAD 2>/dev/null)" ] && at_tip=1
if [ "$at_tip" = 1 ]; then
  msg="base=$base makes the fast lane run ALL modules; this ref is the line's tip: dispatch on the line ref ($line), with base = the last line tip that had a PASS."
  cmd=""
fi
case "$branch" in
  native/glass-next|native/next|native/screens-merge*|native/night-*|master)  # the line, and its throwaway copies
    echo "::warning::base_check: warning: $msg ${cmd:+Better: $cmd}"; echo "warning: $msg"; [ -z "$cmd" ] || echo "Better: $cmd"; exit 0 ;;
esac
echo "::error::base_check: REFUSED: $msg"
echo "REFUSED: $msg"
if [ -n "$cmd" ]; then echo "Rerun with the right base:"; echo "  $cmd"; fi
exit 1
