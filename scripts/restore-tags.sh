#!/usr/bin/env bash
# Converts the tags/<name> branches on this repository back into real tags
# and deletes the branches. The tags could not be pushed from the tooling
# that created opencode-mobile-next (its proxy refuses refs/tags), so each
# tag's commit was preserved as a branch instead. Run once from any machine
# with push access:
#
#   scripts/restore-tags.sh [remote]      # remote defaults to origin
set -euo pipefail
remote="${1:-origin}"
git fetch --prune "$remote" 'refs/heads/tags/*:refs/remotes/'"$remote"'/tags/*'
mapfile -t names < <(git for-each-ref --format='%(refname:strip=3)' "refs/remotes/$remote/tags/" | sed 's#^tags/##')
[[ ${#names[@]} -gt 0 ]] || { echo "no tags/ branches on $remote"; exit 0; }
for name in "${names[@]}"; do
  sha="$(git rev-parse "refs/remotes/$remote/tags/$name")"
  git tag -f "$name" "$sha" >/dev/null
  echo "tag $name -> ${sha:0:9}"
done
git push "$remote" --tags
for name in "${names[@]}"; do git push -q "$remote" --delete "tags/$name"; done
echo "restored ${#names[@]} tags and removed the tags/ branches"
