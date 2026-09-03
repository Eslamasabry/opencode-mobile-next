#!/usr/bin/env bash
# Validate or explicitly publish an Android Shorebird release/patch.
set -euo pipefail

readonly EXIT_USAGE=64
readonly RELEASE_BRANCH="master"
readonly SHOREBIRD_FLUTTER_VERSION="3.47.2"
readonly AAB_PATH="build/app/outputs/bundle/release/app-release.aab"
readonly APK_PATH="build/app/outputs/flutter-apk/app-release.apk"
readonly ANDROID_APPLICATION_ID="io.github.eslamasabry.opencode_mobile"
readonly PUBLIC_SIDELOAD_CERT_SHA256="842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"

UPSTREAM_REMOTE=""
RELEASE_KEYSTORE=""
RELEASE_KEY_ALIAS=""
EXPECTED_CERT_SHA256=""
ACTUAL_CERT_SHA256=""
SIGNING_CERTIFICATE_OUTPUT=""

usage() {
  cat >&2 <<'EOF'
Usage: ./scripts/release.sh <release|sideload|patch> [--publish]

Without --publish, the command runs every gate and a Shorebird dry-run only.
Add --publish to upload to Shorebird after the dry-run succeeds.

release  creates the production/store AAB and rejects the legacy certificate.
sideload creates the GitHub APK and requires the exact public legacy certificate.
patch    creates a Dart-only patch for the exact tagged release version.
EOF
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

read_property() {
  local property_name="$1"
  local properties_file="$2"
  sed -n "s/^[[:space:]]*${property_name}[[:space:]]*=[[:space:]]*//p" "$properties_file" | tail -n 1
}

normalize_fingerprint() {
  tr -d ':[:space:]' <<<"$1" | tr '[:lower:]' '[:upper:]'
}

assert_private_file() {
  local path="$1"
  local description="$2"
  local permissions
  permissions="$(stat -c '%a' "$path")" || fail "Cannot inspect permissions for $description."
  (( (8#$permissions & 8#077) == 0 )) ||
    fail "$description must not be readable or writable by group or other users (found mode $permissions)."
}

if (( $# < 1 || $# > 2 )); then
  usage
  exit "$EXIT_USAGE"
fi

readonly MODE="$1"
case "$MODE" in
  release | sideload | patch) ;;
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

load_release_signing_identity() {
  local release_kind="$1"
  local gradle_file="android/app/build.gradle.kts"
  local properties_file="android/key.properties"
  [[ -f "$gradle_file" ]] || fail "Missing Android application Gradle configuration: $gradle_file"

  grep -Eq '(signingConfigs\.)?create\(["'\'']release["'\'']\)' "$gradle_file" &&
    grep -Eq 'signingConfig[[:space:]]*=[[:space:]]*signingConfigs\.getByName\(["'\'']release["'\'']\)' "$gradle_file" ||
    fail "$release_kind release is blocked: Android does not have an explicit release signing configuration. Complete the documented signing setup first."

  [[ -f "$properties_file" ]] ||
    fail "$release_kind release is blocked: $properties_file is missing. Complete the documented signing setup first."
  [[ ! -L "$properties_file" ]] ||
    fail "$release_kind release is blocked: $properties_file must be a regular file, not a symbolic link."
  assert_private_file "$properties_file" "$properties_file"
  local property_name
  for property_name in storeFile storePassword keyAlias keyPassword; do
    grep -Eq "^[[:space:]]*${property_name}[[:space:]]*=[[:space:]]*[^[:space:]].*$" "$properties_file" ||
      fail "$release_kind release is blocked: $properties_file has no non-empty $property_name."
  done

  command -v keytool >/dev/null 2>&1 || fail "Required command is not available: keytool"

  local configured_store_file configured_alias repository_root resolved_store_file
  configured_store_file="$(read_property storeFile "$properties_file")"
  configured_alias="$(read_property keyAlias "$properties_file")"
  [[ "$configured_store_file" == /* ]] ||
    fail "$release_kind release is blocked: storeFile must be an absolute path outside the repository."
  [[ -f "$configured_store_file" && ! -L "$configured_store_file" ]] ||
    fail "$release_kind release is blocked: storeFile does not identify a regular, non-symlink keystore."
  resolved_store_file="$(realpath -e "$configured_store_file")" ||
    fail "$release_kind release is blocked: storeFile cannot be resolved."
  repository_root="$(pwd -P)"
  case "$resolved_store_file" in
    "$repository_root" | "$repository_root"/*)
      fail "$release_kind release is blocked: the keystore must live outside the repository."
      ;;
  esac
  assert_private_file "$resolved_store_file" "The release keystore"

  RELEASE_KEYSTORE="$resolved_store_file"
  RELEASE_KEY_ALIAS="$configured_alias"

  local store_password
  store_password="$(read_property storePassword "$properties_file")"
  SIGNING_CERTIFICATE_OUTPUT="$(
    OC_RELEASE_STORE_PASSWORD="$store_password" keytool \
      -J-Duser.language=en \
      -list -v \
      -keystore "$RELEASE_KEYSTORE" \
      -alias "$RELEASE_KEY_ALIAS" \
      -storepass:env OC_RELEASE_STORE_PASSWORD
  )" || fail "Unable to read the configured release keystore and alias."
  unset store_password
  ACTUAL_CERT_SHA256="$(sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' <<<"$SIGNING_CERTIFICATE_OUTPUT" | tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]')"
  [[ "$ACTUAL_CERT_SHA256" =~ ^[0-9A-F]{64}$ ]] ||
    fail "Unable to read a single SHA-256 certificate fingerprint from the configured release alias."
}

assert_store_signing_ready() {
  load_release_signing_identity "Store"
  [[ -n "${RELEASE_CERT_SHA256:-}" ]] ||
    fail "Store release is blocked: set RELEASE_CERT_SHA256 to the expected production upload-certificate SHA-256 fingerprint."
  [[ "${RELEASE_CERT_SHA256//:/}" =~ ^[0-9A-Fa-f]{64}$ ]] ||
    fail "RELEASE_CERT_SHA256 must be a 32-byte SHA-256 fingerprint, with or without colons."
  EXPECTED_CERT_SHA256="$(normalize_fingerprint "$RELEASE_CERT_SHA256")"
  [[ "$EXPECTED_CERT_SHA256" != "$PUBLIC_SIDELOAD_CERT_SHA256" ]] ||
    fail "Store release is blocked: RELEASE_CERT_SHA256 is the legacy Android debug certificate."
  [[ "$SIGNING_CERTIFICATE_OUTPUT" != *"CN=Android Debug"* ]] ||
    fail "Store release is blocked: the configured alias contains an Android debug certificate."
  [[ "$ACTUAL_CERT_SHA256" == "$EXPECTED_CERT_SHA256" ]] ||
    fail "Configured release keystore certificate does not match RELEASE_CERT_SHA256."
}

assert_sideload_signing_ready() {
  load_release_signing_identity "GitHub sideload"
  EXPECTED_CERT_SHA256="$PUBLIC_SIDELOAD_CERT_SHA256"
  [[ "$ACTUAL_CERT_SHA256" == "$EXPECTED_CERT_SHA256" ]] ||
    fail "GitHub sideload release is blocked: the configured certificate does not match the public APK upgrade lineage."
}

assert_aab_certificate() {
  [[ -f "$AAB_PATH" ]] || fail "Shorebird dry-run did not create the expected AAB: $AAB_PATH"

  local certificate_output actual_fingerprint
  certificate_output="$(keytool -printcert -jarfile "$AAB_PATH")" ||
    fail "Unable to read the signing certificate from $AAB_PATH."
  actual_fingerprint="$(sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' <<<"$certificate_output" | tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]')"
  [[ -n "$actual_fingerprint" && "$actual_fingerprint" == "$EXPECTED_CERT_SHA256" ]] ||
    fail "AAB signing certificate does not match RELEASE_CERT_SHA256."
}

resolve_android_tool() {
  local tool_name="$1"
  local local_properties_sdk=""
  if [[ -f android/local.properties ]]; then
    local_properties_sdk="$(read_property sdk.dir android/local.properties)"
  fi

  local sdk_root candidate
  for sdk_root in \
    "${ANDROID_HOME:-}" \
    "${ANDROID_SDK_ROOT:-}" \
    "$local_properties_sdk" \
    "${HOME:-}/Android/Sdk"; do
    [[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]] || continue
    candidate="$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name "$tool_name" -print | sort -V | tail -n 1)"
    if [[ -n "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done

  # Distro packages can lag the SDK that compiled the app. Prefer the latest
  # tool from the configured Android SDK, then fall back to PATH.
  local direct
  direct="$(command -v "$tool_name" 2>/dev/null || true)"
  if [[ -n "$direct" ]]; then
    printf '%s\n' "$direct"
    return
  fi
  fail "Required Android build tool is not available: $tool_name"
}

assert_apk_identity() {
  [[ -f "$APK_PATH" ]] || fail "Shorebird dry-run did not create the expected APK: $APK_PATH"

  local apksigner aapt certificate_output badging
  local -a signer_fingerprints
  apksigner="$(resolve_android_tool apksigner)"
  aapt="$(resolve_android_tool aapt)"
  certificate_output="$("$apksigner" verify --print-certs "$APK_PATH")" ||
    fail "Unable to verify the APK signature: $APK_PATH"
  mapfile -t signer_fingerprints < <(
    sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest:[[:space:]]*//p' <<<"$certificate_output" |
      tr '[:lower:]' '[:upper:]'
  )
  [[ "${#signer_fingerprints[@]}" == 1 ]] ||
    fail "GitHub sideload APK must contain exactly one signing certificate."
  [[ "${signer_fingerprints[0]}" == "$EXPECTED_CERT_SHA256" ]] ||
    fail "GitHub sideload APK certificate does not match the public upgrade lineage."

  badging="$("$aapt" dump badging "$APK_PATH")" ||
    fail "Unable to inspect the APK package identity: $APK_PATH"
  local package_line package_name version_code version_name
  package_line="$(sed -n '1p' <<<"$badging")"
  package_name="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$package_line")"
  version_code="$(sed -n "s/^package:.* versionCode='\([^']*\)'.*/\1/p" <<<"$package_line")"
  version_name="$(sed -n "s/^package:.* versionName='\([^']*\)'.*/\1/p" <<<"$package_line")"
  [[ "$package_name" == "$ANDROID_APPLICATION_ID" ]] ||
    fail "GitHub sideload APK package is $package_name, expected $ANDROID_APPLICATION_ID."
  [[ "$version_code" == "$BUILD_NUMBER" && "$version_name" == "$BUILD_NAME" ]] ||
    fail "GitHub sideload APK version is ${version_name:-<missing>}+${version_code:-<missing>}, expected $VERSION."
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

case "$MODE" in
  release) assert_store_signing_ready ;;
  sideload) assert_sideload_signing_ready ;;
  patch) assert_patchable_diff ;;
esac

echo "==> Analyzing Dart code"
flutter analyze
echo "==> Running Flutter tests"
flutter test --concurrency=1

case "$MODE" in
  release)
    release_args=(
      release android
      --build-name "$BUILD_NAME"
      --build-number "$BUILD_NUMBER"
      --flutter-version "$SHOREBIRD_FLUTTER_VERSION"
      --artifact aab
    )

    echo "==> Validating Android App Bundle release $VERSION (no upload)"
    rm -f -- "$AAB_PATH"
    shorebird "${release_args[@]}" --dry-run
    assert_aab_certificate

    if [[ "$PUBLISH" == true ]]; then
      echo "==> Publishing Shorebird release $VERSION"
      rm -f -- "$AAB_PATH"
      shorebird "${release_args[@]}"
      assert_aab_certificate
      echo "==> AAB: ./$AAB_PATH"
      echo "==> Create immutable baseline tag $RELEASE_TAG on this commit before any patch."
    else
      echo "==> Validation passed; nothing was uploaded."
      echo "==> Publish explicitly with: ./scripts/release.sh release --publish"
    fi
    ;;
  sideload)
    sideload_args=(
      release android
      --build-name "$BUILD_NAME"
      --build-number "$BUILD_NUMBER"
      --flutter-version "$SHOREBIRD_FLUTTER_VERSION"
      --artifact apk
    )

    echo "==> Validating GitHub sideload APK $VERSION (no upload)"
    rm -f -- "$APK_PATH"
    shorebird "${sideload_args[@]}" --dry-run
    assert_apk_identity

    if [[ "$PUBLISH" == true ]]; then
      echo "==> Publishing Shorebird sideload release $VERSION"
      rm -f -- "$APK_PATH"
      shorebird "${sideload_args[@]}"
      assert_apk_identity
      echo "==> APK: ./$APK_PATH"
      echo "==> Publish this exact APK in GitHub release $RELEASE_TAG, then create and push the immutable tag."
    else
      echo "==> Validation passed; nothing was uploaded."
      echo "==> Publish explicitly with: ./scripts/release.sh sideload --publish"
    fi
    ;;
  patch)
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
    ;;
esac
