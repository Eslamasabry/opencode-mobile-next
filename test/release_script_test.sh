#!/usr/bin/env bash
set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_SCRIPT="$REPO_ROOT/scripts/release.sh"
readonly CUT_SCRIPT="$REPO_ROOT/scripts/cut-alpha.sh"
readonly NOTES_ANCHOR="# OpenCode Mobile - Alpha 1.0.12+13"
readonly LEGACY_SIDELOAD_FINGERPRINT="8F51FBCA8101DE600C0E878DF7E2CC65DFA29ADD58A1771D776908349CD82053"
TEST_ROOT="$(mktemp -d)"
readonly TEST_ROOT
trap 'rm -rf "$TEST_ROOT"' EXIT

TEST_NUMBER=0
FIXTURE=""
KEYSTORE_PATH=""
OUTPUT=""
STATUS=0

fail_test() {
  echo "FAIL: $*" >&2
  echo "--- in test case $TEST_NUMBER (fixture $FIXTURE) ---" >&2
  if [[ -n "$OUTPUT" ]]; then
    echo "--- command output ---" >&2
    echo "$OUTPUT" >&2
  fi
  exit 1
}

new_fixture() {
  TEST_NUMBER=$((TEST_NUMBER + 1))
  FIXTURE="$TEST_ROOT/case-$TEST_NUMBER"
  KEYSTORE_PATH="$TEST_ROOT/keystores/case-$TEST_NUMBER/release.jks"
  mkdir -p "$FIXTURE/scripts" "$FIXTURE/android/app" "$FIXTURE/mock-bin" "$FIXTURE/home" "$(dirname "$KEYSTORE_PATH")"
  cp "$SOURCE_SCRIPT" "$FIXTURE/scripts/release.sh"
  cp "$CUT_SCRIPT" "$FIXTURE/scripts/cut-alpha.sh"
  chmod +x "$FIXTURE/scripts/release.sh" "$FIXTURE/scripts/cut-alpha.sh"
  : >"$FIXTURE/commands.log"

  # The alpha notes as committed: a draft preamble for the person cutting
  # the release, then the body that goes to GitHub.
  mkdir -p "$FIXTURE/docs"
  {
    printf '# Release notes draft\n\nOwner: paste into the GitHub release body.\n\n---\n\n'
    printf '%s\n\n' "$NOTES_ANCHOR"
    printf 'Alpha warning block.\n\n## Report a bug\n\nOpen an issue.\n\n## What is in this cut\n\n- one\n- two\n- three\n\n## Install\n\nSideload the APK.\n'
  } >"$FIXTURE/docs/release-alpha-notes.md"

  cat >"$FIXTURE/pubspec.yaml" <<'EOF'
name: release_fixture
version: 1.0.12+13
EOF

  cat >"$FIXTURE/android/app/build.gradle.kts" <<'EOF'
android {
    signingConfigs.create("release")
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
EOF

  : >"$KEYSTORE_PATH"
  chmod 600 "$KEYSTORE_PATH"
  cat >"$FIXTURE/android/key.properties" <<EOF
storeFile=$KEYSTORE_PATH
storePassword=fixture-store-password
keyAlias=release
keyPassword=fixture-key-password
EOF
  chmod 600 "$FIXTURE/android/key.properties"

  cat >"$FIXTURE/mock-bin/git" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'git' >>"$MOCK_COMMAND_LOG"
printf ' %s' "$@" >>"$MOCK_COMMAND_LOG"
printf '\n' >>"$MOCK_COMMAND_LOG"

case "${1:-}" in
  status)
    printf '%s' "${MOCK_GIT_STATUS:-}"
    ;;
  symbolic-ref)
    [[ "${MOCK_DETACHED:-false}" != true ]] || exit 1
    printf '%s\n' "${MOCK_BRANCH:-master}"
    ;;
  fetch)
    if [[ "${*: -1}" == refs/tags/* && "${MOCK_REMOTE_TAG_EXISTS:-true}" != true ]]; then
      exit 1
    fi
    [[ "${MOCK_FETCH_FAIL:-false}" != true ]] || exit 1
    ;;
  rev-list)
    printf '%s\n' "${MOCK_COUNTS:-0 0}"
    ;;
  merge-base)
    [[ "${MOCK_TAG_ANCESTOR:-true}" == true ]] || exit 1
    ;;
  diff)
    printf '%s' "${MOCK_DIFF_FILES:-}"
    ;;
  show)
    if [[ "${MOCK_GIT_SHOW_FAIL:-false}" == true ]]; then
      echo 'fatal: not a git repository (or any of the parent directories): .git' >&2
      exit 128
    fi
    if [[ -n "${MOCK_GIT_SHOW_BODY+x}" ]]; then
      printf '%s' "$MOCK_GIT_SHOW_BODY"
    else
      cat "$MOCK_NOTES_FILE"
    fi
    ;;
  checkout | merge | push | tag)
    ;;
  rev-parse)
    case "${2:-}" in
      --is-inside-work-tree)
        printf 'true\n'
        ;;
      --abbrev-ref)
        [[ "${MOCK_NO_UPSTREAM:-false}" != true ]] || exit 1
        printf '%s\n' "${MOCK_UPSTREAM:-origin/master}"
        ;;
      --verify)
        if [[ "${3:-}" == 'FETCH_HEAD^{commit}' ]]; then
          printf '%s\n' "${MOCK_REMOTE_TAG_COMMIT:-base-commit}"
        else
          [[ "${MOCK_TAG_EXISTS:-true}" == true ]] || exit 1
          printf '%s\n' "${MOCK_LOCAL_TAG_COMMIT:-base-commit}"
        fi
        ;;
      *)
        exit 2
        ;;
    esac
    ;;
  *)
    exit 2
    ;;
esac
EOF

  cat >"$FIXTURE/mock-bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'gh %s\n' "$*" >>"$MOCK_COMMAND_LOG"
case "${1:-} ${2:-}" in
  "auth status")
    ;;
  "release create")
    shift 2
    while (( $# > 0 )); do
      if [[ "$1" == --notes-file ]]; then
        cp "$2" "$MOCK_GH_BODY_FILE"
        shift
      fi
      shift
    done
    ;;
  "release view")
    if [[ "$*" != *'--json body'* && "${MOCK_GH_RELEASE_EXISTS:-false}" != true ]]; then
      exit 1
    fi
    if [[ "${MOCK_GH_TRUNCATE_BODY:-false}" == true ]]; then
      printf 'Release notes draft\n'
    else
      cat "$MOCK_GH_BODY_FILE"
    fi
    ;;
  *)
    exit 2
    ;;
esac
EOF

  cat >"$FIXTURE/mock-bin/flutter" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'flutter %s\n' "$*" >>"$MOCK_COMMAND_LOG"
if [[ "${1:-}" == "--version" ]]; then
  printf 'Flutter %s • channel stable\n' "${MOCK_FLUTTER_VERSION:-3.47.2}"
  exit 0
fi
if [[ "${MOCK_FLUTTER_FAIL:-}" == "${1:-}" ]]; then
  exit 1
fi
EOF

  cat >"$FIXTURE/mock-bin/shorebird" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'shorebird %s\n' "$*" >>"$MOCK_COMMAND_LOG"
[[ "${MOCK_SHOREBIRD_FAIL:-false}" != true ]] || exit 1
if [[ "${1:-}" == release && "$*" == *'--artifact aab'* ]]; then
  mkdir -p build/app/outputs/bundle/release
  : >build/app/outputs/bundle/release/app-release.aab
fi
if [[ "${1:-}" == release && "$*" == *'--artifact apk'* ]]; then
  mkdir -p build/app/outputs/flutter-apk
  : >build/app/outputs/flutter-apk/app-release.apk
fi
EOF

  cat >"$FIXTURE/mock-bin/keytool" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'keytool %s\n' "$*" >>"$MOCK_COMMAND_LOG"
if [[ "$*" == *'-list -v'* ]]; then
  printf 'Owner: %s\n' "${MOCK_KEYSTORE_OWNER:-CN=OpenCode Release}"
  fingerprint="${MOCK_KEYSTORE_FINGERPRINT:-AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA}"
else
  fingerprint="${MOCK_AAB_FINGERPRINT:-AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA}"
fi
printf 'Certificate fingerprints:\n'
printf '         SHA256: %s\n' "$fingerprint"
EOF

  mkdir -p "$FIXTURE/home/Android/Sdk/build-tools/99.0.0"
  cat >"$FIXTURE/home/Android/Sdk/build-tools/99.0.0/apksigner" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'apksigner %s\n' "$*" >>"$MOCK_COMMAND_LOG"
printf 'Signer #1 certificate SHA-256 digest: %s\n' "${MOCK_APK_FINGERPRINT:-8F51FBCA8101DE600C0E878DF7E2CC65DFA29ADD58A1771D776908349CD82053}"
EOF

  cat >"$FIXTURE/home/Android/Sdk/build-tools/99.0.0/aapt" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'aapt %s\n' "$*" >>"$MOCK_COMMAND_LOG"
printf "package: name='%s' versionCode='%s' versionName='%s'\n" \
  "${MOCK_APK_PACKAGE:-io.github.eslamasabry.opencode_mobile}" \
  "${MOCK_APK_VERSION_CODE:-13}" \
  "${MOCK_APK_VERSION_NAME:-1.0.12}"
EOF

  chmod +x "$FIXTURE/mock-bin/git" "$FIXTURE/mock-bin/gh" "$FIXTURE/mock-bin/flutter" "$FIXTURE/mock-bin/shorebird" "$FIXTURE/mock-bin/keytool"
  chmod +x "$FIXTURE/home/Android/Sdk/build-tools/99.0.0/apksigner" "$FIXTURE/home/Android/Sdk/build-tools/99.0.0/aapt"
}

run_release() {
  set +e
  OUTPUT="$({
    env \
      HOME="$FIXTURE/home" \
      PATH="$FIXTURE/mock-bin:/usr/bin:/bin" \
      ANDROID_HOME= \
      ANDROID_SDK_ROOT= \
      MOCK_COMMAND_LOG="$FIXTURE/commands.log" \
      MOCK_GIT_STATUS="${MOCK_GIT_STATUS:-}" \
      MOCK_BRANCH="${MOCK_BRANCH:-master}" \
      MOCK_DETACHED="${MOCK_DETACHED:-false}" \
      MOCK_NO_UPSTREAM="${MOCK_NO_UPSTREAM:-false}" \
      MOCK_UPSTREAM="${MOCK_UPSTREAM:-origin/master}" \
      MOCK_FETCH_FAIL="${MOCK_FETCH_FAIL:-false}" \
      MOCK_COUNTS="${MOCK_COUNTS:-0 0}" \
      MOCK_TAG_EXISTS="${MOCK_TAG_EXISTS:-true}" \
      MOCK_TAG_ANCESTOR="${MOCK_TAG_ANCESTOR:-true}" \
      MOCK_LOCAL_TAG_COMMIT="${MOCK_LOCAL_TAG_COMMIT:-base-commit}" \
      MOCK_REMOTE_TAG_EXISTS="${MOCK_REMOTE_TAG_EXISTS:-true}" \
      MOCK_REMOTE_TAG_COMMIT="${MOCK_REMOTE_TAG_COMMIT:-base-commit}" \
      MOCK_DIFF_FILES="${MOCK_DIFF_FILES:-}" \
      MOCK_FLUTTER_VERSION="${MOCK_FLUTTER_VERSION:-3.47.2}" \
      MOCK_FLUTTER_FAIL="${MOCK_FLUTTER_FAIL:-}" \
      MOCK_SHOREBIRD_FAIL="${MOCK_SHOREBIRD_FAIL:-false}" \
      MOCK_KEYSTORE_OWNER="${MOCK_KEYSTORE_OWNER:-CN=OpenCode Release}" \
      MOCK_KEYSTORE_FINGERPRINT="${MOCK_KEYSTORE_FINGERPRINT:-AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA}" \
      MOCK_AAB_FINGERPRINT="${MOCK_AAB_FINGERPRINT:-AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA}" \
      MOCK_APK_FINGERPRINT="${MOCK_APK_FINGERPRINT:-$LEGACY_SIDELOAD_FINGERPRINT}" \
      MOCK_APK_PACKAGE="${MOCK_APK_PACKAGE:-io.github.eslamasabry.opencode_mobile}" \
      MOCK_APK_VERSION_CODE="${MOCK_APK_VERSION_CODE:-13}" \
      MOCK_APK_VERSION_NAME="${MOCK_APK_VERSION_NAME:-1.0.12}" \
      RELEASE_CERT_SHA256="${RELEASE_CERT_SHA256:-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA}" \
      "$FIXTURE/scripts/release.sh" "$@"
  } 2>&1)"
  STATUS=$?
  set -e
}

# Runs the fixture's cut-alpha.sh with the mock toolchain. The sideload
# signing mocks default to the public lineage so release.sh passes and the
# cases below exercise the notes and publish gates only.
run_cut() {
  set +e
  OUTPUT="$({
    env \
      HOME="$FIXTURE/home" \
      PATH="$FIXTURE/mock-bin:/usr/bin:/bin" \
      ANDROID_HOME= \
      ANDROID_SDK_ROOT= \
      MOCK_COMMAND_LOG="$FIXTURE/commands.log" \
      MOCK_NOTES_FILE="$FIXTURE/docs/release-alpha-notes.md" \
      MOCK_GH_BODY_FILE="$FIXTURE/gh-release-body" \
      MOCK_GIT_SHOW_FAIL="${MOCK_GIT_SHOW_FAIL:-false}" \
      MOCK_GH_TRUNCATE_BODY="${MOCK_GH_TRUNCATE_BODY:-false}" \
      MOCK_TAG_EXISTS=false \
      MOCK_REMOTE_TAG_EXISTS=false \
      MOCK_KEYSTORE_OWNER='CN=Android Debug,O=Android,C=US' \
      MOCK_KEYSTORE_FINGERPRINT="$LEGACY_SIDELOAD_FINGERPRINT" \
      MOCK_APK_FINGERPRINT="$LEGACY_SIDELOAD_FINGERPRINT" \
      "$FIXTURE/scripts/cut-alpha.sh" "$@"
  } 2>&1)"
  STATUS=$?
  set -e
}

# Runs cut-alpha.sh --print-notes with the real git, from the fixture — a
# directory that is not a repository.
run_cut_notes_outside_repository() {
  set +e
  OUTPUT="$({
    env \
      HOME="$FIXTURE/home" \
      PATH="/usr/bin:/bin" \
      "$FIXTURE/scripts/cut-alpha.sh" --print-notes
  } 2>&1)"
  STATUS=$?
  set -e
}

assert_output_not_contains() {
  [[ "$OUTPUT" != *"$1"* ]] || fail_test "output unexpectedly contains: $1"
}

assert_status() {
  [[ "$STATUS" == "$1" ]] || fail_test "expected status $1, got $STATUS"
}

assert_output_contains() {
  [[ "$OUTPUT" == *"$1"* ]] || fail_test "output does not contain: $1"
}

assert_log_contains() {
  local log
  log="$(<"$FIXTURE/commands.log")"
  [[ "$log" == *"$1"* ]] || fail_test "command log does not contain: $1"
}

assert_log_not_contains() {
  local log
  log="$(<"$FIXTURE/commands.log")"
  [[ "$log" != *"$1"* ]] || fail_test "command log unexpectedly contains: $1"
}

assert_log_line_count() {
  local expected="$1"
  local pattern="$2"
  local actual
  actual="$(awk -v pattern="$pattern" 'index($0, pattern) == 1 { count++ } END { print count + 0 }' "$FIXTURE/commands.log")"
  [[ "$actual" == "$expected" ]] ||
    fail_test "expected $expected log lines beginning with '$pattern', got $actual"
}

test_strict_arguments() {
  new_fixture
  run_release
  assert_status 64
  assert_output_contains 'Usage: ./scripts/release.sh <release|sideload|patch> [--publish]'
  assert_log_line_count 0 'shorebird '

  run_release ship
  assert_status 64
  assert_log_line_count 0 'shorebird '

  run_release release --yes
  assert_status 64
  assert_log_line_count 0 'shorebird '

  run_release release --publish extra
  assert_status 64
  assert_log_line_count 0 'shorebird '
}

test_git_gates() {
  new_fixture
  MOCK_GIT_STATUS=' M lib/main.dart' run_release patch
  assert_status 1
  assert_output_contains 'worktree is not clean'
  assert_log_not_contains 'flutter analyze'
  assert_log_not_contains 'shorebird '

  new_fixture
  MOCK_BRANCH='feature/risky' run_release patch
  assert_status 1
  assert_output_contains 'Releases must run from master'

  new_fixture
  MOCK_NO_UPSTREAM=true run_release patch
  assert_status 1
  assert_output_contains 'has no upstream branch'

  new_fixture
  MOCK_COUNTS='1 2' run_release patch
  assert_status 1
  assert_output_contains 'behind 1, ahead 2'
  assert_log_not_contains 'shorebird '
}

test_flutter_toolchain_gate() {
  new_fixture
  MOCK_FLUTTER_VERSION='3.38.5' run_release patch --publish
  assert_status 1
  assert_output_contains 'Flutter 3.47.2 is required'
  assert_output_contains 'PATH provides 3.38.5'
  assert_log_not_contains 'git status'
  assert_log_not_contains 'shorebird '
}

test_release_signing_blocker() {
  new_fixture
  cat >"$FIXTURE/android/app/build.gradle.kts" <<'EOF'
android {
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
EOF
  run_release release --publish
  assert_status 1
  assert_output_contains 'does not have an explicit release signing configuration'
  assert_log_not_contains 'flutter analyze'
  assert_log_not_contains 'shorebird '

  new_fixture
  cat >"$FIXTURE/android/app/build.gradle.kts" <<'EOF'
android {
    buildTypes {
        release {}
    }
}
EOF
  run_release release
  assert_status 1
  assert_output_contains 'does not have an explicit release signing configuration'
  assert_log_not_contains 'flutter analyze'
  assert_log_not_contains 'shorebird '

  new_fixture
  : >"$FIXTURE/android/key.properties"
  chmod 600 "$FIXTURE/android/key.properties"
  run_release release
  assert_status 1
  assert_output_contains 'no non-empty storeFile'
  assert_log_not_contains 'flutter analyze'
  assert_log_not_contains 'shorebird '

  new_fixture
  sed -i "s#storeFile=.*#storeFile=$FIXTURE/release.jks#" "$FIXTURE/android/key.properties"
  : >"$FIXTURE/release.jks"
  chmod 600 "$FIXTURE/release.jks"
  run_release release
  assert_status 1
  assert_output_contains 'keystore must live outside the repository'
  assert_log_not_contains 'flutter analyze'

  new_fixture
  chmod 644 "$FIXTURE/android/key.properties"
  run_release release
  assert_status 1
  assert_output_contains 'must not be readable or writable by group or other users'
  assert_log_not_contains 'keytool '

  new_fixture
  MOCK_KEYSTORE_OWNER='CN=Android Debug,O=Android,C=US' run_release release
  assert_status 1
  assert_output_contains 'contains an Android debug certificate'
  assert_log_not_contains 'flutter analyze'
}

test_release_is_dry_run_by_default_and_builds_aab() {
  new_fixture
  run_release release
  assert_status 0
  assert_output_contains 'nothing was uploaded'
  assert_log_contains 'flutter analyze'
  assert_log_contains 'flutter test --concurrency=1'
  assert_log_contains 'shorebird release android --build-name 1.0.12 --build-number 13 --flutter-version 3.47.2 --artifact aab --dry-run'
  assert_log_line_count 1 'shorebird '
  assert_log_not_contains '--artifact apk'

  new_fixture
  run_release release --publish
  assert_status 0
  assert_output_contains 'AAB: ./build/app/outputs/bundle/release/app-release.aab'
  assert_output_contains 'Create immutable baseline tag v1.0.12+13'
  assert_log_line_count 2 'shorebird '

  new_fixture
  MOCK_AAB_FINGERPRINT='BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB' run_release release --publish
  assert_status 1
  assert_output_contains 'AAB signing certificate does not match'
  assert_log_line_count 1 'shorebird '
}

test_sideload_requires_public_lineage_and_verifies_apk() {
  new_fixture
  MOCK_KEYSTORE_OWNER='CN=Android Debug,O=Android,C=US' \
    MOCK_KEYSTORE_FINGERPRINT="$LEGACY_SIDELOAD_FINGERPRINT" \
    run_release sideload
  assert_status 0
  assert_output_contains 'nothing was uploaded'
  assert_output_contains 'Publish explicitly with: ./scripts/release.sh sideload --publish'
  assert_log_contains 'flutter analyze'
  assert_log_contains 'flutter test --concurrency=1'
  assert_log_contains 'shorebird release android --build-name 1.0.12 --build-number 13 --flutter-version 3.47.2 --artifact apk --dry-run'
  assert_log_contains 'apksigner verify --print-certs build/app/outputs/flutter-apk/app-release.apk'
  assert_log_contains 'aapt dump badging build/app/outputs/flutter-apk/app-release.apk'
  assert_log_not_contains '--artifact aab'
  assert_log_line_count 1 'shorebird '
  assert_log_line_count 1 'apksigner '
  assert_log_line_count 1 'aapt '

  new_fixture
  MOCK_KEYSTORE_OWNER='CN=Android Debug,O=Android,C=US' \
    MOCK_KEYSTORE_FINGERPRINT="$LEGACY_SIDELOAD_FINGERPRINT" \
    run_release sideload --publish
  assert_status 0
  assert_output_contains 'APK: ./build/app/outputs/flutter-apk/app-release.apk'
  assert_output_contains 'GitHub release v1.0.12+13'
  assert_log_line_count 2 'shorebird '
  assert_log_line_count 2 'apksigner '
  assert_log_line_count 2 'aapt '

  new_fixture
  run_release sideload --publish
  assert_status 1
  assert_output_contains 'does not match the public APK upgrade lineage'
  assert_log_not_contains 'flutter analyze'
  assert_log_not_contains 'shorebird '

  new_fixture
  MOCK_KEYSTORE_FINGERPRINT="$LEGACY_SIDELOAD_FINGERPRINT" \
    MOCK_APK_FINGERPRINT='BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB' \
    run_release sideload --publish
  assert_status 1
  assert_output_contains 'APK certificate does not match the public upgrade lineage'
  assert_log_line_count 1 'shorebird '

  new_fixture
  MOCK_KEYSTORE_FINGERPRINT="$LEGACY_SIDELOAD_FINGERPRINT" \
    MOCK_APK_PACKAGE='example.wrong.application' \
    run_release sideload --publish
  assert_status 1
  assert_output_contains 'APK package is example.wrong.application'
  assert_log_line_count 1 'shorebird '

  new_fixture
  MOCK_KEYSTORE_FINGERPRINT="$LEGACY_SIDELOAD_FINGERPRINT" \
    MOCK_APK_VERSION_CODE='12' \
    run_release sideload --publish
  assert_status 1
  assert_output_contains 'APK version is 1.0.12+12, expected 1.0.12+13'
  assert_log_line_count 1 'shorebird '
}

test_quality_gate_failure_prevents_shorebird() {
  new_fixture
  MOCK_FLUTTER_FAIL='analyze' run_release release --publish
  assert_status 1
  assert_log_contains 'flutter analyze'
  assert_log_not_contains 'flutter test'
  assert_log_not_contains 'shorebird '

  new_fixture
  MOCK_FLUTTER_FAIL='test' run_release patch --publish
  assert_status 1
  assert_log_contains 'flutter analyze'
  assert_log_contains 'flutter test'
  assert_log_not_contains 'shorebird '
}

test_patch_targets_exact_version_and_requires_baseline() {
  new_fixture
  MOCK_DIFF_FILES=$'lib/main.dart\n' run_release patch
  assert_status 0
  assert_output_contains 'nothing was uploaded'
  assert_log_contains 'shorebird patch android --release-version 1.0.12+13 --dry-run'
  assert_log_not_contains 'latest'
  assert_log_line_count 1 'shorebird '

  new_fixture
  MOCK_DIFF_FILES=$'lib/main.dart\n' run_release patch --publish
  assert_status 0
  assert_log_line_count 2 'shorebird '
  assert_log_not_contains '--allow-native-diffs'
  assert_log_not_contains '--allow-asset-diffs'

  new_fixture
  MOCK_TAG_EXISTS=false run_release patch
  assert_status 1
  assert_output_contains 'Patch baseline tag v1.0.12+13 is missing'
  assert_log_not_contains 'flutter analyze'
  assert_log_not_contains 'shorebird '

  new_fixture
  MOCK_REMOTE_TAG_EXISTS=false run_release patch
  assert_status 1
  assert_output_contains 'baseline tag v1.0.12+13 is missing from origin'
  assert_log_not_contains 'flutter analyze'
  assert_log_not_contains 'shorebird '

  new_fixture
  MOCK_REMOTE_TAG_COMMIT='different-commit' run_release patch
  assert_status 1
  assert_output_contains 'Local tag v1.0.12+13 does not match'
  assert_log_not_contains 'flutter analyze'
  assert_log_not_contains 'shorebird '

  new_fixture
  MOCK_TAG_ANCESTOR=false run_release patch
  assert_status 1
  assert_output_contains 'baseline tag v1.0.12+13 is not an ancestor'
  assert_log_not_contains 'flutter analyze'
  assert_log_not_contains 'shorebird '
}

test_patch_rejects_native_and_asset_inputs() {
  new_fixture
  MOCK_DIFF_FILES=$'lib/main.dart\nandroid/app/src/main/kotlin/MainActivity.kt\npackages/plugin/android/build.gradle\npubspec.lock\n' run_release patch --publish
  assert_status 1
  assert_output_contains 'changes native, dependency, or bundled-asset inputs'
  assert_output_contains 'android/app/src/main/kotlin/MainActivity.kt'
  assert_output_contains 'packages/plugin/android/build.gradle'
  assert_output_contains 'pubspec.lock'
  assert_log_not_contains 'flutter analyze'
  assert_log_not_contains 'shorebird '
}

test_invalid_version_is_rejected() {
  new_fixture
  sed -i 's/version: .*/version: latest/' "$FIXTURE/pubspec.yaml"
  run_release patch --publish
  assert_status 1
  assert_output_contains 'x.y.z+positive-build-number'
  assert_log_not_contains 'shorebird '
}

test_alpha_notes_body_is_validated_before_publish() {
  # A body assembled where `git show` cannot run must fail the cut instead of
  # producing an empty file that gets uploaded.
  new_fixture
  command -v /usr/bin/git >/dev/null 2>&1 || fail_test "real git is required at /usr/bin/git"
  run_cut_notes_outside_repository
  assert_status 1
  assert_output_contains 'git show failed'
  assert_output_not_contains "$NOTES_ANCHOR"

  new_fixture
  MOCK_GIT_SHOW_FAIL=true run_cut --print-notes
  assert_status 1
  assert_output_contains 'git show failed'
  assert_log_not_contains 'gh '

  # The committed body prints from its heading onward, preamble dropped.
  new_fixture
  run_cut --print-notes
  assert_status 0
  [[ "$OUTPUT" == "$NOTES_ANCHOR"* ]] || fail_test "notes do not start with the anchor heading"
  assert_output_not_contains 'Release notes draft'
  assert_output_contains '## Install'

  # A body without the heading, or shorter than ten lines, is refused.
  new_fixture
  MOCK_GIT_SHOW_BODY=$'# Something else\n\nline\nline\nline\nline\nline\nline\nline\nline\nline\nline\n' run_cut --print-notes
  assert_status 1
  assert_output_contains "missing the heading '$NOTES_ANCHOR'"

  new_fixture
  MOCK_GIT_SHOW_BODY="$NOTES_ANCHOR"$'\n\nToo short.\n' run_cut --print-notes
  assert_status 1
  assert_output_contains 'only 3 lines'

  # The same gate runs before anything is pushed on a real cut.
  new_fixture
  MOCK_GIT_SHOW_FAIL=true run_cut --publish
  assert_status 1
  assert_output_contains 'git show failed'
  assert_log_not_contains 'git merge'
  assert_log_not_contains 'git push'
  assert_log_not_contains 'shorebird release'
  assert_log_not_contains 'gh release'
}

test_alpha_publish_verifies_the_body_github_stored() {
  new_fixture
  run_cut --publish
  assert_status 0
  assert_log_contains 'gh release create v1.0.12+13'
  assert_log_contains 'gh release view v1.0.12+13 --json body'
  assert_output_contains 'Alpha 1.0.12+13 published'
  local stored
  stored="$(<"$FIXTURE/gh-release-body")"
  [[ "$stored" == "$NOTES_ANCHOR"* ]] || fail_test "uploaded body does not start with the anchor heading"
  [[ "$stored" != *'Release notes draft'* ]] || fail_test "uploaded body carries the draft preamble"

  new_fixture
  MOCK_GH_TRUNCATE_BODY=true run_cut --publish
  assert_status 1
  assert_output_contains 'truncated'
  assert_output_not_contains 'Alpha 1.0.12+13 published'
}

test_strict_arguments
test_git_gates
test_flutter_toolchain_gate
test_release_signing_blocker
test_release_is_dry_run_by_default_and_builds_aab
test_sideload_requires_public_lineage_and_verifies_apk
test_quality_gate_failure_prevents_shorebird
test_patch_targets_exact_version_and_requires_baseline
test_patch_rejects_native_and_asset_inputs
test_invalid_version_is_rejected
test_alpha_notes_body_is_validated_before_publish
test_alpha_publish_verifies_the_body_github_stored

echo "PASS: release script safety contract"
