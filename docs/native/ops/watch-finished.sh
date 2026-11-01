#!/bin/bash
# watch-finished.sh: exits listing native/* branches whose tip has a report under docs/native/units/ or docs/native/slice0/ that m0 lacks, and that are not merged
cd /home/eslam/Storage/Code/cloud-launch || exit 1
known="$1"   # file listing branch@sha already handled
for i in $(seq 1 240); do
  git fetch -q origin '+refs/heads/native/*:refs/remotes/origin/native/*' 2>/dev/null
  out=""
  for b in $(git branch -r | grep -oE 'origin/native/[a-z0-9-]+' | grep -v 'native/m0-' | sort -u); do
    sha=$(git rev-parse --short $b); grep -qx "$b@$sha" "$known" 2>/dev/null && continue
    git merge-base --is-ancestor $b origin/native/m0-2026-11-01 && continue
    new=$(comm -13 <(git ls-tree -r --name-only origin/native/m0-2026-11-01 docs/native/units docs/native/slice0 2>/dev/null | sort) <(git ls-tree -r --name-only $b docs/native/units docs/native/slice0 2>/dev/null | sort))
    [ -n "$new" ] && out="$out$b@$sha $new"$'\n'
  done
  [ -n "$out" ] && { printf '%s' "$out"; exit 0; }
  sleep 60
done
echo "nothing finished in 4h"
