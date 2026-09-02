#!/usr/bin/env bash
# Cut the alpha: promote the production line to master, publish the signed
# GitHub APK, tag it, and open the GitHub release with the alpha notes.
#
# Why this script exists: scripts/release.sh is fail-closed by design — it
# demands a clean synced master, the private signing identity, and Shorebird,
# and it never creates GitHub releases or tags. The private key is kept
# outside the repository and outside this script's reach on purpose; an
# agent cannot cut a release alone, and should not be able to.
#
# Prerequisites (checked, never worked around):
#   * android/key.properties present with the legacy GitHub sideload key
#     (certificate SHA-256 1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c)
#   * Shorebird CLI installed and authenticated (shorebird doctor)
#   * gh CLI installed and authenticated (only needed for --publish)
#
# Usage:
#   ./scripts/cut-alpha.sh              # promote + dry-run, uploads nothing
#   ./scripts/cut-alpha.sh --publish    # promote + publish + tag + release
#   ./scripts/cut-alpha.sh --print-notes  # assemble + validate the release body only
#
# Everything is fail-closed: any failed step aborts with nothing half-done
# except the master promotion, which is pushed only after a clean merge.
#
# The release body is read from HEAD with `git show` (no pipe, so a failure
# aborts under `set -e`), must carry the alpha heading and at least
# NOTES_MIN_LINES lines before anything is published, and is read back from
# GitHub after `gh release create` — a truncated body once shipped because a
# failed `git show` inside a pipeline produced an empty file that was
# uploaded anyway.
set -euo pipefail

readonly PRODUCTION_BRANCH="production/android-release-hardening"
readonly VERSION="1.0.31+32"
readonly TAG="v${VERSION}"
readonly APK_SRC="build/app/outputs/flutter-apk/app-release.apk"
readonly APK_NAME="opencode-mobile-${VERSION}-alpha.apk"
readonly NOTES="docs/release-alpha-notes.md"
# The published body starts at this heading; the draft preamble above it in
# $NOTES is for the person cutting the release, not for GitHub.
readonly NOTES_ANCHOR="# OpenCode Mobile — Alpha"
readonly NOTES_MIN_LINES=10
readonly SIDELoad_CERT="1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c"

PUBLISH=false
PRINT_NOTES=false
case "${1:-}" in
  "") ;;
  --publish) PUBLISH=true ;;
  --print-notes) PRINT_NOTES=true ;;
  *)
    echo "Usage: ./scripts/cut-alpha.sh [--publish|--print-notes]" >&2
    exit 64
    ;;
esac

fail() {
  echo "✗ $*" >&2
  exit 1
}

step() { echo "==> $*"; }

# Prints the validated release body on stdout, or fails the cut.
build_release_body() {
  local raw body line_count
  raw="$(git show "HEAD:$NOTES")" ||
    fail "cannot read $NOTES from HEAD (git show failed) — run from the repository checkout; the release body would be truncated."
  body="$(awk -v anchor="$NOTES_ANCHOR" 'index($0, anchor) == 1 { found = 1 } found { print }' <<<"$raw")"
  [[ "$body" == "$NOTES_ANCHOR"* ]] ||
    fail "release notes body is missing the heading '$NOTES_ANCHOR' — refusing to publish a truncated body."
  line_count="$(wc -l <<<"$body")"
  (( line_count >= NOTES_MIN_LINES )) ||
    fail "release notes body has only $line_count lines (need at least $NOTES_MIN_LINES) — refusing to publish a truncated body."
  printf '%s\n' "$body"
}

# Reads the body GitHub actually stored and fails unless it carries the
# heading, so a truncated upload is caught before it is announced.
assert_published_body() {
  local published
  published="$(gh release view "$TAG" --json body --jq .body)" ||
    fail "could not read back release $TAG from GitHub — verify its body by hand before announcing."
  [[ "$published" == *"$NOTES_ANCHOR"* ]] ||
    fail "GitHub release $TAG body is missing '$NOTES_ANCHOR' (truncated). Repair it with: gh release edit $TAG --notes-file <validated body>"
}

cd "$(dirname "$0")/.."

if [[ "$PRINT_NOTES" == true ]]; then
  build_release_body
  exit 0
fi

# The pinned Flutter is Shorebird's cache, not the distro one on PATH; and
# user-local tools (gh) live in ~/.local/bin.
# `|| true`: with pipefail an absent cache would otherwise abort here with
# ls's status instead of the flutter-on-PATH check below.
PINNED_FLUTTER_BIN="$(ls -d "$HOME"/.shorebird/bin/cache/flutter/*/bin 2>/dev/null | head -1 || true)"
export PATH="$PINNED_FLUTTER_BIN:$HOME/.shorebird/bin:$HOME/.local/bin:$PATH"

[[ -f android/key.properties ]] ||
  fail "android/key.properties is missing. Put the legacy GitHub sideload signing identity in place (see android/key.properties.example), then re-run."
[[ -f "$NOTES" ]] || fail "$NOTES is missing."

command -v gh >/dev/null 2>&1 ||
  fail "gh CLI is not installed. Install it and run 'gh auth login', then re-run."

step "Preflight: toolchains and auth"
command -v flutter >/dev/null 2>&1 ||
  fail "flutter is not on PATH (use the Shorebird-pinned 3.47.2 binary)."
gh auth status >/dev/null 2>&1 || fail "gh is not authenticated: run 'gh auth login'."
shorebird doctor >/dev/null 2>&1 ||
  fail "shorebird doctor failed — fix Shorebird auth/setup first."

step "Preflight: release notes body assembles from HEAD"
build_release_body >/dev/null

step "Preflight: signing identity matches the public sideload lineage"
properties_cert="$(grep -oP '(?<=^certificate_sha256=).*' android/key.properties 2>/dev/null || true)"
# key.properties carries whatever the owner stored; the authoritative check
# is release.sh's post-build identity gate. Here we only warn early.
if [[ -n "$properties_cert" ]]; then
  normalized="${properties_cert//:/}"
  [[ "${normalized,,}" == "$SIDELoad_CERT" ]] ||
    echo "  ⚠ key.properties certificate differs from the public sideload lineage — release.sh will refuse later if it is wrong."
fi

step "Promoting $PRODUCTION_BRANCH to master"
git checkout master ||
  fail "cannot check out master."
git merge --no-ff "$PRODUCTION_BRANCH" -m "Cut alpha $VERSION from the production line" ||
  fail "merge conflict — resolve by hand; the release is not cut."
step "Pushing master"
git push origin master ||
  fail "push failed; fix the remote and re-run."

step "Running the fail-closed sideload release (dry-run, uploads nothing)"
./scripts/release.sh sideload || fail "dry-run failed — nothing was uploaded."

if [[ "$PUBLISH" != true ]]; then
  echo
  echo "Dry-run passed. Re-run with --publish to build, upload to Shorebird,"
  echo "tag $TAG, and open the GitHub release."
  exit 0
fi

step "Publishing the signed APK through Shorebird"
./scripts/release.sh sideload --publish || fail "publish failed."
[[ -f "$APK_SRC" ]] || fail "expected APK missing at $APK_SRC."

step "Tagging $TAG"
git tag -f "$TAG"
git push origin "$TAG"

step "Assembling the release notes from the merged HEAD"
BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT
build_release_body >"$BODY_FILE"

step "Opening the GitHub release"
cp "$APK_SRC" "$APK_NAME"
gh release create "$TAG" \
  --title "OpenCode Mobile $VERSION — Alpha" \
  --notes-file "$BODY_FILE" \
  "$APK_NAME" ||
  fail "release creation failed — attach $APK_NAME to tag $TAG by hand."

step "Verifying the published release body"
assert_published_body

echo
echo "✓ Alpha $VERSION published. The Linux desktop packages attach to this"
echo "  tag automatically once CI can run (account billing); until then they"
echo "  are build-from-source via docs/desktop.md."
