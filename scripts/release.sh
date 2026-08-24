#!/usr/bin/env bash
# Validate or explicitly publish an Android Shorebird release/patch.
set -euo pipefail

readonly EXIT_USAGE=64
readonly RELEASE_BRANCH="master"
readonly SHOREBIRD_FLUTTER_VERSION="3.47.1"
readonly AAB_PATH="build/app/outputs/bundle/release/app-release.aab"

UPSTREAM_REMOTE=""

usage() {
  cat >&2 <<'EOF'
Usage: ./scripts/release.sh <release|patch> [--publish]

Without --publish, the command runs every gate and a Shorebird dry-run only.
Add --publish to upload to Shorebird after the dry-run succeeds.
EOF
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

if (( $# < 1 || $# > 2 )); then
  usage
  exit "$EXIT_USAGE"
fi

readonly MODE="$1"
case "$MODE" in
  release | patch) ;;
  *)
    usage
    exit "$EXIT_USAGE"
    ;;
esac

PUBLISH=false
if (( $# == 2 )); then
  if [[ "$2" != "--publish" ]]; then
    usage
    exit "$EXIT_USAGE"
  fi
  PUBLISH=true
fi
readonly PUBLISH

cd "$(dirname "$0")/.."
export PATH="$HOME/.shorebird/bin:$PATH"

for command_name in git flutter shorebird; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "Required command is not available: $command_name"
done

VERSION="$(sed -n 's/^[[:space:]]*version:[[:space:]]*//p' pubspec.yaml)"
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+\+[1-9][0-9]*$ ]]; then
  fail "pubspec.yaml must contain exactly one version in x.y.z+positive-build-number form. Found: ${VERSION:-<none>}"
fi
readonly VERSION
readonly BUILD_NAME="${VERSION%%+*}"
readonly BUILD_NUMBER="${VERSION##*+}"
readonly RELEASE_TAG="v${VERSION}"

assert_flutter_version() {
  local version_output detected_version
  version_output="$(flutter --version)"
  detected_version="$(sed -n '1s/^Flutter \([^[:space:]]*\).*/\1/p' <<<"$version_output")"
  [[ "$detected_version" == "$SHOREBIRD_FLUTTER_VERSION" ]] ||
    fail "Flutter $SHOREBIRD_FLUTTER_VERSION is required for analysis, tests, and release parity; PATH provides ${detected_version:-an unknown version}."
}

assert_git_ready() {
  git rev-parse --is-inside-work-tree >/dev/null 2>&1 ||
    fail "Release commands must run from a Git worktree."

  local status
  status="$(git status --porcelain=v1 --untracked-files=all)"
  [[ -z "$status" ]] ||
    fail "The worktree is not clean. Commit or remove every tracked and untracked change first."

  local branch
  branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null)" ||
    fail "Detached HEAD is not releasable."
  [[ "$branch" == "$RELEASE_BRANCH" ]] ||
    fail "Releases must run from $RELEASE_BRANCH, not $branch."

  local upstream
  upstream="$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null)" ||
    fail "$branch has no upstream branch."
  [[ "$upstream" == */* ]] || fail "Cannot determine the upstream remote for $branch."

  local remote="${upstream%%/*}"
  local upstream_branch="${upstream#*/}"
  [[ "$upstream_branch" == "$branch" ]] ||
    fail "$branch must track a same-named upstream branch; found $upstream."
  UPSTREAM_REMOTE="$remote"

  echo "==> Refreshing $upstream"
  git fetch --quiet "$remote" "$upstream_branch"

  local behind ahead
  read -r behind ahead < <(git rev-list --left-right --count "$upstream...HEAD")
  [[ "$behind" == "0" && "$ahead" == "0" ]] ||
    fail "$branch is not synchronized with $upstream (behind $behind, ahead $ahead)."
}

assert_store_signing_ready() {
  local gradle_file="android/app/build.gradle.kts"
  local properties_file="android/key.properties"
  [[ -f "$gradle_file" ]] || fail "Missing Android application Gradle configuration: $gradle_file"

  grep -Eq '(signingConfigs\.)?create\(["'\'']release["'\'']\)' "$gradle_file" &&
    grep -Eq 'signingConfig[[:space:]]*=[[:space:]]*signingConfigs\.getByName\(["'\'']release["'\'']\)' "$gradle_file" ||
    fail "Store release is blocked: Android does not have an explicit release signing configuration. Complete the documented signing migration first."

  [[ -f "$properties_file" ]] ||
    fail "Store release is blocked: $properties_file is missing. Complete the documented signing migration first."
  local property_name
  for property_name in storeFile storePassword keyAlias keyPassword; do
    grep -Eq "^[[:space:]]*${property_name}[[:space:]]*=[[:space:]]*[^[:space:]].*$" "$properties_file" ||
      fail "Store release is blocked: $properties_file has no non-empty $property_name."
  done

  [[ -n "${RELEASE_CERT_SHA256:-}" ]] ||
    fail "Store release is blocked: set RELEASE_CERT_SHA256 to the expected production upload-certificate SHA-256 fingerprint."
  [[ "${RELEASE_CERT_SHA256//:/}" =~ ^[0-9A-Fa-f]{64}$ ]] ||
    fail "RELEASE_CERT_SHA256 must be a 32-byte SHA-256 fingerprint, with or without colons."
  command -v keytool >/dev/null 2>&1 || fail "Required command is not available: keytool"
}

assert_aab_certificate() {
  [[ -f "$AAB_PATH" ]] || fail "Shorebird dry-run did not create the expected AAB: $AAB_PATH"

  local certificate_output actual_fingerprint expected_fingerprint
  certificate_output="$(keytool -printcert -jarfile "$AAB_PATH")" ||
    fail "Unable to read the signing certificate from $AAB_PATH."
  actual_fingerprint="$(sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' <<<"$certificate_output" | tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]')"
  expected_fingerprint="$(tr -d ':[:space:]' <<<"$RELEASE_CERT_SHA256" | tr '[:lower:]' '[:upper:]')"
  [[ -n "$actual_fingerprint" && "$actual_fingerprint" == "$expected_fingerprint" ]] ||
    fail "AAB signing certificate does not match RELEASE_CERT_SHA256."
}

assert_patchable_diff() {
  local base_commit
  base_commit="$(git rev-parse --verify "refs/tags/${RELEASE_TAG}^{commit}" 2>/dev/null)" ||
    fail "Patch baseline tag $RELEASE_TAG is missing. Tag the exact full-release commit before creating patches."
  git fetch --quiet "$UPSTREAM_REMOTE" "refs/tags/$RELEASE_TAG" ||
    fail "Patch baseline tag $RELEASE_TAG is missing from $UPSTREAM_REMOTE."
  local remote_base_commit
  remote_base_commit="$(git rev-parse --verify 'FETCH_HEAD^{commit}' 2>/dev/null)" ||
    fail "Cannot resolve $UPSTREAM_REMOTE tag $RELEASE_TAG."
  [[ "$base_commit" == "$remote_base_commit" ]] ||
    fail "Local tag $RELEASE_TAG does not match the immutable tag on $UPSTREAM_REMOTE."
  git merge-base --is-ancestor "$base_commit" HEAD >/dev/null 2>&1 ||
    fail "Patch baseline tag $RELEASE_TAG is not an ancestor of HEAD."

  local changed_files
  changed_files="$(git diff --name-only --diff-filter=ACDMRTUXB "$base_commit...HEAD")"

  local blocked=()
  local changed_path
  while IFS= read -r changed_path; do
    [[ -n "$changed_path" ]] || continue
    case "$changed_path" in
      android/* | packages/*/android/* | assets/* | fonts/* | pubspec.yaml | pubspec.lock | shorebird.yaml | THIRD_PARTY_NOTICES.md | LICENSES/*)
        blocked+=("$changed_path")
        ;;
    esac
  done <<<"$changed_files"

  if (( ${#blocked[@]} > 0 )); then
    printf 'ERROR: Patch %s changes native, dependency, or bundled-asset inputs:\n' "$VERSION" >&2
    printf '  - %s\n' "${blocked[@]}" >&2
    echo "Create a new full release; this script never enables --allow-native-diffs or --allow-asset-diffs." >&2
    exit 1
  fi
}

assert_flutter_version
assert_git_ready

if [[ "$MODE" == "release" ]]; then
  assert_store_signing_ready
else
  assert_patchable_diff
fi

echo "==> Analyzing Dart code"
flutter analyze
echo "==> Running Flutter tests"
flutter test

if [[ "$MODE" == "release" ]]; then
  release_args=(
    release android
    --build-name "$BUILD_NAME"
    --build-number "$BUILD_NUMBER"
    --flutter-version "$SHOREBIRD_FLUTTER_VERSION"
    --artifact aab
  )

  echo "==> Validating Android App Bundle release $VERSION (no upload)"
  shorebird "${release_args[@]}" --dry-run
  assert_aab_certificate

  if [[ "$PUBLISH" == true ]]; then
    echo "==> Publishing Shorebird release $VERSION"
    shorebird "${release_args[@]}"
    echo "==> AAB: ./$AAB_PATH"
    echo "==> Create immutable baseline tag $RELEASE_TAG on this commit before any patch."
  else
    echo "==> Validation passed; nothing was uploaded."
    echo "==> Publish explicitly with: ./scripts/release.sh release --publish"
  fi
else
  patch_args=(patch android --release-version "$VERSION")

  echo "==> Validating OTA patch for exact release $VERSION (no upload)"
  shorebird "${patch_args[@]}" --dry-run

  if [[ "$PUBLISH" == true ]]; then
    echo "==> Publishing OTA patch for exact release $VERSION"
    shorebird "${patch_args[@]}"
  else
    echo "==> Validation passed; nothing was uploaded."
    echo "==> Publish explicitly with: ./scripts/release.sh patch --publish"
  fi
fi
