#!/usr/bin/env bash
# Sourced only by release.sh after its clean/synced master gate.
# The build and full-quality jobs must already have succeeded for this source.
release_github() (
  set -euo pipefail
  local repo remote_url head local_tag remote_tag apk_name
  local build_run="${OC_RELEASE_BUILD_RUN_ID:-}"
  local quality_run="${OC_RELEASE_QUALITY_RUN_ID:-}"
  [[ "$build_run" =~ ^[1-9][0-9]*$ && "$quality_run" =~ ^[1-9][0-9]*$ ]] ||
    fail "Set OC_RELEASE_BUILD_RUN_ID and OC_RELEASE_QUALITY_RUN_ID to the successful candidate runs."

  remote_url="$(git remote get-url "$UPSTREAM_REMOTE")"
  case "$remote_url" in
    https://github.com/*) repo="${remote_url#https://github.com/}" ;;
    git@github.com:*) repo="${remote_url#git@github.com:}" ;;
    *) fail "GitHub publication requires a github.com upstream." ;;
  esac
  repo="${repo%.git}"
  [[ "$repo" == Eslamasabry/opencode-mobile-next ]] ||
    fail "Refusing publication outside Eslamasabry/opencode-mobile-next; check master's upstream."

  head="$(git rev-parse HEAD)"
  local_tag="$(git rev-parse --verify "refs/tags/${RELEASE_TAG}^{commit}")" ||
    fail "The immutable candidate tag $RELEASE_TAG must exist before publication."
  git fetch --quiet "$UPSTREAM_REMOTE" "refs/tags/$RELEASE_TAG" ||
    fail "Cannot verify the remote candidate tag."
  remote_tag="$(git rev-parse --verify 'FETCH_HEAD^{commit}')"
  [[ "$local_tag" == "$head" && "$remote_tag" == "$head" ]] ||
    fail "Local tag, remote tag and synchronized master must identify the same source."

  local scratch
  scratch="$(mktemp -d)"
  trap 'rm -rf -- "$scratch"' EXIT
  mkdir "$scratch/ci" "$scratch/draft"
  apk_name="opencode-mobile-$VERSION.apk"
  # GitHub's by-tag REST endpoint does not expose drafts. Resolve the draft
  # through gh, then pin every metadata check to the same database identity.
  local release_id
  release_id="$(gh release view "$RELEASE_TAG" --repo "$repo" --json databaseId --jq .databaseId)" ||
    fail "Cannot resolve the candidate GitHub release."
  [[ "$release_id" =~ ^[1-9][0-9]*$ ]] ||
    fail "GitHub release database ID must be a positive integer."
  fetch_candidate_release() {
    gh api "repos/$repo/releases/$release_id" > "$scratch/release.json"
    python3 - "$scratch/release.json" "$release_id" <<'PYRELEASE'
import json
import sys

with open(sys.argv[1]) as source:
    actual = json.load(source).get("id")
if type(actual) is not int or actual != int(sys.argv[2]):
    raise SystemExit("ERROR: GitHub release identity differs from the resolved draft")
PYRELEASE
  }
  gh api "repos/$repo/actions/runs/$build_run" > "$scratch/build.json"
  gh api "repos/$repo/actions/runs/$build_run/jobs?per_page=100" > "$scratch/build-jobs.json"
  gh api "repos/$repo/actions/runs/$quality_run" > "$scratch/quality.json"
  gh api "repos/$repo/actions/runs/$quality_run/jobs?per_page=100" > "$scratch/jobs.json"
  fetch_candidate_release
  python3 scripts/verify_github_release.py preflight "$scratch" "$head" "$VERSION"

  gh run download "$build_run" --repo "$repo" \
    --name "opencode-mobile-signed-$head" --dir "$scratch/ci"
  gh release download "$RELEASE_TAG" --repo "$repo" --dir "$scratch/draft" \
    --pattern "$apk_name" --pattern SHA256SUMS
  python3 scripts/verify_github_release.py artifacts "$scratch" "$head" "$VERSION"
  EXPECTED_CERT_SHA256="$PUBLIC_SIDELOAD_CERT_SHA256"
  assert_apk_identity "$scratch/draft/$apk_name"

  if [[ "$PUBLISH" != true ]]; then
    echo "==> Verified draft $RELEASE_TAG, CI provenance, full quality gate and public APK identity."
    echo "==> Nothing published. After candidate upgrade/runtime review: ./scripts/release.sh github --publish"
    return
  fi

  cp "$scratch/release.json" "$scratch/release-verified.json"

  # Recheck the draft and synchronized source immediately before the mutation.
  assert_git_ready
  [[ "$(git rev-parse HEAD)" == "$head" ]] || fail "Source changed during verification."
  git fetch --quiet "$UPSTREAM_REMOTE" "refs/tags/$RELEASE_TAG"
  [[ "$(git rev-parse --verify 'FETCH_HEAD^{commit}')" == "$head" &&
     "$(git rev-parse --verify "refs/tags/${RELEASE_TAG}^{commit}")" == "$head" ]] ||
    fail "Candidate tag changed during verification."
  fetch_candidate_release
  python3 scripts/verify_github_release.py preflight "$scratch" "$head" "$VERSION"
  python3 scripts/verify_github_release.py artifacts "$scratch" "$head" "$VERSION"
  gh release edit "$RELEASE_TAG" --repo "$repo" \
    --draft=false --prerelease=false --latest \
    --title "OpenCode Mobile $VERSION" --notes-file "$scratch/ci/RELEASE_NOTES.md"
  fetch_candidate_release
  gh api "repos/$repo/releases/latest" > "$scratch/latest.json"
  python3 scripts/verify_github_release.py published "$scratch" "$head" "$VERSION"
  echo "==> Published https://github.com/$repo/releases/tag/$RELEASE_TAG"
)
