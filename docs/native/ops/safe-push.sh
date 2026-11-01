#!/bin/bash
# safe-push.sh <repo-dir> <branch>: refuses to push if any private value appears in the tree.
# The patterns live OUTSIDE the repo (one extended regex per line) so this script never contains them:
#   ~/.config/oc-private/push-block-patterns.txt
P="$HOME/.config/oc-private/push-block-patterns.txt"
[ -s "$P" ] || { echo "BLOCKED: no pattern file at $P"; exit 1; }
cd "$1" || exit 2
hits=$(git grep -lE -f "$P" HEAD)
if [ -n "$hits" ]; then echo "BLOCKED, private values in:"; echo "$hits"; exit 1; fi
git push -q origin "$2" && echo "pushed $2 $(git log -1 --format=%h)"
