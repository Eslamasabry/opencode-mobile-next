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
#
# Everything is fail-closed: any failed step aborts with nothing half-done
# except the master promotion, which is pushed only after a clean merge.
set -euo pipefail

readonly PRODUCTION_BRANCH="production/android-release-hardening"
readonly VERSION="1.0.31+32"
readonly TAG="v${VERSION}"
readonly APK_SRC="build/app/outputs/flutter-apk/app-release.apk"
readonly APK_NAME="opencode-mobile-${VERSION}-alpha.apk"
readonly NOTES="docs/release-alpha-notes.md"
readonly SIDELoad_CERT="1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c"

PUBLISH=false
[[ "${1:-}" == "--publish" ]] && PUBLISH=true

fail() {
  echo "✗ $*" >&2
  exit 1
}

step() { echo "==> $*"; }

cd "$(dirname "$0")/.."

# The pinned Flutter is Shorebird's cache, not the distro one on PATH; and
# user-local tools (gh) live in ~/.local/bin.
PINNED_FLUTTER_BIN="$(ls -d "$HOME"/.shorebird/bin/cache/flutter/*/bin 2>/dev/null | head -1)"
export PATH="$PINNED_FLUTTER_BIN:$HOME/.shorebird/bin:$HOME/.local/bin:$PATH"

[[ -f android/key.properties ]] ||
  fail "android/key.properties is missing. Put the legacy GitHub sideload signing identity in place (see android/key.properties.example), then re-run."
[[ -f "$NOTES" ]] || fail "$NOTES is missing."

command -v gh >/dev/null 2>&1 ||
  fail "gh CLI is not installed. Install it and run 'gh auth login', then re-run."

step "Preflight: toolchains and auth"
command -v flutter >/dev/null 2>&1 ||
  fail "flutter is not on PATH (use the Shorebird-pinned 3.47.1 binary)."
gh auth status >/dev/null 2>&1 || fail "gh is not authenticated: run 'gh auth login'."
shorebird doctor >/dev/null 2>&1 ||
  fail "shorebird doctor failed — fix Shorebird auth/setup first."

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

step "Opening the GitHub release"
cp "$APK_SRC" "$APK_NAME"
gh release create "$TAG" \
  --title "OpenCode Mobile $VERSION — Alpha" \
  --notes-file "$NOTES" \
  "$APK_NAME" ||
  fail "release creation failed — attach $APK_NAME to tag $TAG by hand."

echo
echo "✓ Alpha $VERSION published. The Linux desktop packages attach to this"
echo "  tag automatically once CI can run (account billing); until then they"
echo "  are build-from-source via docs/desktop.md."
