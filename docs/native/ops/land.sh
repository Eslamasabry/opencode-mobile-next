#!/bin/bash
# land.sh <unit-branch-short> : merge origin/native/<x>-2026-11-01 into m0, safe-push, record handled
S=/home/eslam/Storage/tmp/claude-tmp/claude-1000/-home-eslam-Storage-Code-oc-app/87a8d964-900c-48f3-a841-cd593d87ac4c/scratchpad
cd /home/eslam/Storage/Code/cloud-launch || exit 1
git checkout -q native/m0-2026-11-01 && git pull -q --ff-only origin native/m0-2026-11-01 || exit 1
b=origin/native/$1-2026-11-01; sha=$(git rev-parse --short $b)
pre=$(git rev-parse --short HEAD)
if git -c user.email=104366884+Eslamasabry@users.noreply.github.com -c user.name=Eslamasabry merge --no-ff -q -m "merge: native/$1-2026-11-01 into the M0 branch [skip ci]

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01XdLZSEjfJHERa9NNNGhTM1" $b >/dev/null 2>&1; then
  $S/safe-push.sh /home/eslam/Storage/Code/cloud-launch native/m0-2026-11-01 && echo "$b@$sha" >> $S/handled.txt && echo "LANDED $1 range $pre..$(git rev-parse --short HEAD)"
else
  echo "CONFLICT $1: $(git diff --name-only --diff-filter=U | tr '\n' ' ')"; git merge --abort; echo "$b@$sha" >> $S/handled.txt
fi
