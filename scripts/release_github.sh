#!/usr/bin/env bash
# Sourced only by release.sh after its clean/synced master gate.
#
# CI (android-release.yml) publishes every new release as a GitHub
# pre-release (E02-S90). `github` proves that pre-release's CI provenance and
# full quality gate and never changes anything; `promote` re-verifies the
# published assets after the quiet days and only then marks the release
# non-pre-release and latest.

# Prints owner/name of master's github.com upstream, or fails.
github_release_repo() {
  local remote_url repo
  remote_url="$(git remote get-url "$UPSTREAM_REMOTE")"
  case "$remote_url" in
    https://github.com/*) repo="${remote_url#https://github.com/}" ;;
    git@github.com:*) repo="${remote_url#git@github.com:}" ;;
    *) fail "GitHub publication requires a github.com upstream." ;;
  esac
  repo="${repo%.git}"
  [[ "$repo" == Eslamasabry/opencode-mobile-next ]] ||
    fail "Refusing publication outside Eslamasabry/opencode-mobile-next; check master's upstream."
  printf '%s\n' "$repo"
}

# The build and full-quality jobs must already have succeeded for this source.
release_github() (
  set -euo pipefail
  local repo head local_tag remote_tag apk_name
  local build_run="${OC_RELEASE_BUILD_RUN_ID:-}"
  local quality_run="${OC_RELEASE_QUALITY_RUN_ID:-}"
  [[ "$PUBLISH" != true ]] ||
    fail "github only verifies the CI pre-release. Make it latest after the quiet days with: ./scripts/release.sh promote $RELEASE_TAG --publish"
  [[ "$build_run" =~ ^[1-9][0-9]*$ && "$quality_run" =~ ^[1-9][0-9]*$ ]] ||
    fail "Set OC_RELEASE_BUILD_RUN_ID and OC_RELEASE_QUALITY_RUN_ID to the successful candidate runs."

  repo="$(github_release_repo)"

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
    --pattern "opencode-mobile-$VERSION*.apk" --pattern SHA256SUMS
  python3 scripts/verify_github_release.py artifacts "$scratch" "$head" "$VERSION"
  EXPECTED_CERT_SHA256="$PUBLIC_SIDELOAD_CERT_SHA256"
  # The universal APK of record and the per-ABI APKs derived from it.
  local candidate
  for candidate in "$apk_name" "opencode-mobile-$VERSION-arm64-v8a.apk" "opencode-mobile-$VERSION-armeabi-v7a.apk"; do
    assert_apk_identity "$scratch/draft/$candidate"
  done

  echo "==> Verified pre-release $RELEASE_TAG, CI provenance, full quality gate and public APK identity."
  echo "==> Nothing changed. After 1-2 quiet days: ./scripts/release.sh promote $RELEASE_TAG --publish"
)

# Makes a published pre-release the latest normal release, after re-verifying
# its tag, its age, every APK against SHA256SUMS and every APK's signer,
# package and version. Without --publish nothing is changed.
release_promote() (
  set -euo pipefail
  local repo local_tag remote_tag scratch candidate
  repo="$(github_release_repo)"

  # The release must belong to the immutable tag that master already contains.
  git fetch --quiet "$UPSTREAM_REMOTE" "refs/tags/$RELEASE_TAG" ||
    fail "Tag $RELEASE_TAG is missing from $UPSTREAM_REMOTE."
  remote_tag="$(git rev-parse --verify 'FETCH_HEAD^{commit}')" ||
    fail "Cannot resolve $UPSTREAM_REMOTE tag $RELEASE_TAG."
  local_tag="$(git rev-parse --verify "refs/tags/${RELEASE_TAG}^{commit}")" ||
    fail "Local tag $RELEASE_TAG is missing; run git fetch --tags first."
  [[ "$local_tag" == "$remote_tag" ]] ||
    fail "Local tag $RELEASE_TAG does not match the immutable tag on $UPSTREAM_REMOTE."
  git merge-base --is-ancestor "$remote_tag" HEAD ||
    fail "Tag $RELEASE_TAG is not part of $RELEASE_BRANCH."

  scratch="$(mktemp -d)"
  trap 'rm -rf -- "$scratch"' EXIT
  mkdir "$scratch/assets"
  # The by-tag endpoint returns published releases only; a draft is refused.
  fetch_promoted_release() {
    gh api "repos/$repo/releases/tags/$RELEASE_TAG" > "$scratch/release.json" ||
      fail "Cannot read published release $RELEASE_TAG (a draft cannot be promoted)."
  }
  fetch_promoted_release
  gh api "repos/$repo/releases/latest" > "$scratch/previous-latest.json" 2>/dev/null ||
    printf '{}\n' > "$scratch/previous-latest.json"
  gh release download "$RELEASE_TAG" --repo "$repo" --dir "$scratch/assets" \
    --pattern "opencode-mobile-$VERSION*.apk" --pattern SHA256SUMS
  python3 scripts/verify_github_release.py promote "$scratch" - "$VERSION"
  EXPECTED_CERT_SHA256="$PUBLIC_SIDELOAD_CERT_SHA256"
  for candidate in "opencode-mobile-$VERSION.apk" "opencode-mobile-$VERSION-arm64-v8a.apk" "opencode-mobile-$VERSION-armeabi-v7a.apk"; do
    assert_apk_identity "$scratch/assets/$candidate"
  done

  if [[ "$PUBLISH" != true ]]; then
    echo "==> Verified pre-release $RELEASE_TAG: SHA256SUMS, public signer, package and version."
    echo "==> Nothing changed. Promote explicitly with: ./scripts/release.sh promote $RELEASE_TAG --publish"
    return
  fi

  # Recheck immediately before the mutation: same release, same assets.
  cp "$scratch/release.json" "$scratch/release-verified.json"
  fetch_promoted_release
  python3 scripts/verify_github_release.py promote "$scratch" - "$VERSION"
  gh release edit "$RELEASE_TAG" --repo "$repo" --prerelease=false --latest
  fetch_promoted_release
  gh api "repos/$repo/releases/latest" > "$scratch/latest.json"
  python3 scripts/verify_github_release.py promoted "$scratch" - "$VERSION"
  echo "==> Promoted https://github.com/$repo/releases/tag/$RELEASE_TAG to latest"
)
