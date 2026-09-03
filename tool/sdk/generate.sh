#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONTRACT="$ROOT/contracts/opencode-openapi-f12e14cf.json"
MANIFEST="$ROOT/contracts/opencode-sdk-manifest.json"
MATRIX_JSON="$ROOT/contracts/opencode-sdk-matrix.json"
MATRIX_MD="$ROOT/contracts/opencode-sdk-matrix.md"
CONFIG="$ROOT/tool/sdk/generator-config.json"
TEMPLATES="$ROOT/tool/sdk/templates"
PACKAGE_PARENT="$ROOT/packages"
PACKAGE="$PACKAGE_PARENT/opencode_sdk"
LOCKFILE="$PACKAGE/pubspec.lock"
VERIFY="$ROOT/tool/sdk/verify_contract.dart"
GENERATOR_VERSION="7.19.0"
GENERATOR_SHA256="3d8140c691410e0004b1bb9b1e431c1293734830f30d6d5922f8e5dbf2e42e19"
CACHE_DIR="${XDG_CACHE_HOME:-${TMPDIR:-/tmp}/opencode-sdk-cache}"
JAR="$CACHE_DIR/openapi-generator-cli-$GENERATOR_VERSION.jar"
OFFLINE="${OPENCODE_SDK_OFFLINE:-0}"
WORK_DIR=""
DOWNLOAD_TMP=""

die() {
  printf 'generate.sh: %s\n' "$*" >&2
  exit 1
}

safe_remove_work_dir() {
  local path="$1"
  case "$path" in
    "$PACKAGE_PARENT"/.opencode_sdk.generate.*) rm -rf "$path" ;;
    *) die "refusing to remove unexpected work directory: $path" ;;
  esac
}

cleanup() {
  if [[ -n "$DOWNLOAD_TMP" ]]; then
    case "$DOWNLOAD_TMP" in
      "$CACHE_DIR"/.openapi-generator-cli-*.jar.*) rm -f "$DOWNLOAD_TMP" ;;
      *) printf 'generate.sh: refusing to remove unexpected download: %s\n' "$DOWNLOAD_TMP" >&2 ;;
    esac
  fi
  if [[ -n "$WORK_DIR" && -d "$WORK_DIR" ]]; then
    safe_remove_work_dir "$WORK_DIR"
  fi
}
trap cleanup EXIT

verify_sha256() {
  dart run "$VERIFY" --verify-sha256 "$1" "$2"
}

[[ "$PACKAGE_PARENT" == "$ROOT/packages" ]] || die "invalid package parent"
[[ "$PACKAGE" == "$ROOT/packages/opencode_sdk" ]] || die "invalid package path"
[[ "$OFFLINE" == 0 || "$OFFLINE" == 1 ]] ||
  die "OPENCODE_SDK_OFFLINE must be 0 or 1"
[[ -d "$PACKAGE" ]] || die "generated package is missing: $PACKAGE"
[[ -f "$LOCKFILE" ]] || die "pinned SDK lockfile is missing: $LOCKFILE"

mkdir -p "$CACHE_DIR" "$PACKAGE_PARENT"
WORK_DIR="$(mktemp -d "$PACKAGE_PARENT/.opencode_sdk.generate.XXXXXX")"
PACKAGE_CANDIDATE="$WORK_DIR/packages/opencode_sdk"
NORMALIZED="$WORK_DIR/openapi.normalized.json"
NORMALIZATION_REPORT="$WORK_DIR/normalization-report.json"
STAGED_CONTRACT="$WORK_DIR/contracts/opencode-openapi-f12e14cf.json"
STAGED_MANIFEST="$WORK_DIR/contracts/opencode-sdk-manifest.json"
STAGED_MATRIX_JSON="$WORK_DIR/contracts/opencode-sdk-matrix.json"
STAGED_MATRIX_MD="$WORK_DIR/contracts/opencode-sdk-matrix.md"
mkdir -p "$WORK_DIR/contracts" "$WORK_DIR/packages" "$WORK_DIR/tool"
ln -s "$CONTRACT" "$STAGED_CONTRACT"
ln -s "$MANIFEST" "$STAGED_MANIFEST"
ln -s "$ROOT/tool/sdk" "$WORK_DIR/tool/sdk"
PACKAGE_GUARD="$(dart run "$VERIFY" --tree-sha256 "$PACKAGE")"

if [[ ! -f "$JAR" ]] ||
  ! verify_sha256 "$JAR" "$GENERATOR_SHA256" >/dev/null 2>&1; then
  [[ "$OFFLINE" == 0 ]] ||
    die "verified generator JAR is not cached (offline mode)"
  DOWNLOAD_TMP="$(mktemp "$CACHE_DIR/.openapi-generator-cli-$GENERATOR_VERSION.jar.XXXXXX")"
  curl --fail --location --retry 3 \
    "https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/$GENERATOR_VERSION/openapi-generator-cli-$GENERATOR_VERSION.jar" \
    --output "$DOWNLOAD_TMP"
  verify_sha256 "$DOWNLOAD_TMP" "$GENERATOR_SHA256" >/dev/null
  mv -f "$DOWNLOAD_TMP" "$JAR"
  DOWNLOAD_TMP=""
fi
verify_sha256 "$JAR" "$GENERATOR_SHA256" >/dev/null

dart run "$VERIFY" "$CONTRACT" "$MANIFEST" before
dart run "$ROOT/tool/sdk/normalize_openapi.dart" \
  "$CONTRACT" "$NORMALIZED" "$NORMALIZATION_REPORT"
dart run "$VERIFY" "$NORMALIZED" "$MANIFEST" after
dart run "$ROOT/tool/sdk/test/normalize_openapi_test.dart" "$CONTRACT"

java -jar "$JAR" generate \
  --generator-name dart-dio \
  --input-spec "$NORMALIZED" \
  --output "$PACKAGE_CANDIDATE" \
  --config "$CONFIG" \
  --template-dir "$TEMPLATES" \
  --global-property apiTests=false,modelTests=false,apiDocs=false,modelDocs=false
dart run "$ROOT/tool/sdk/clean_generated_markdown.dart" \
  "$PACKAGE_CANDIDATE/README.md"

cp "$LOCKFILE" "$PACKAGE_CANDIDATE/pubspec.lock"
dart run "$ROOT/tool/sdk/preserve_unions.dart" "$NORMALIZED" "$PACKAGE_CANDIDATE"
dart run "$ROOT/tool/sdk/preserve_additional_properties.dart" \
  "$NORMALIZED" "$PACKAGE_CANDIDATE"
dart run "$ROOT/tool/sdk/install_runtime.dart" \
  "$ROOT/tool/sdk/runtime" "$PACKAGE_CANDIDATE"
dart run "$ROOT/tool/sdk/generate_http_contracts.dart" \
  "$CONTRACT" "$PACKAGE_CANDIDATE"
if [[ "$OFFLINE" == 1 ]]; then
  dart pub get --directory "$PACKAGE_CANDIDATE" --enforce-lockfile --offline
else
  dart pub get --directory "$PACKAGE_CANDIDATE" --enforce-lockfile
fi
(cd "$PACKAGE_CANDIDATE" && dart run build_runner build)
dart run "$ROOT/tool/sdk/import_spec.dart" \
  "$ROOT/tool/sdk/union_fixtures_test.dart.template" \
  "$PACKAGE_CANDIDATE/test/union_fixtures_test.dart"
dart run "$ROOT/tool/sdk/import_spec.dart" \
  "$ROOT/tool/sdk/contract_parity_test.dart.template" \
  "$PACKAGE_CANDIDATE/test/contract_parity_test.dart"
dart run "$ROOT/tool/sdk/import_spec.dart" \
  "$ROOT/tool/sdk/additional_properties_test.dart.template" \
  "$PACKAGE_CANDIDATE/test/additional_properties_test.dart"
dart run "$ROOT/tool/sdk/import_spec.dart" \
  "$ROOT/tool/sdk/schema_category_fixtures_test.dart.template" \
  "$PACKAGE_CANDIDATE/test/schema_category_fixtures_test.dart"
dart run "$ROOT/tool/sdk/import_spec.dart" \
  "$ROOT/tool/sdk/independent_verifier_mutation_test.dart.template" \
  "$PACKAGE_CANDIDATE/test/independent_verifier_mutation_test.dart"
dart format "$PACKAGE_CANDIDATE/lib" "$PACKAGE_CANDIDATE/test"
dart run "$ROOT/tool/sdk/generate_contract_matrix.dart" \
  "$CONTRACT" "$MANIFEST" "$PACKAGE_CANDIDATE" \
  "$STAGED_MATRIX_JSON" "$STAGED_MATRIX_MD"
dart run "$ROOT/tool/sdk/verify_contract_matrix.dart" \
  "$CONTRACT" "$MANIFEST" "$PACKAGE_CANDIDATE" \
  "$STAGED_MATRIX_JSON" "$STAGED_MATRIX_MD"
dart run "$ROOT/tool/sdk/verify_generated.dart" "$PACKAGE_CANDIDATE" "$MANIFEST"
dart run "$ROOT/tool/sdk/verify_artifacts_independent.dart" \
  "$CONTRACT" "$MANIFEST" "$PACKAGE_CANDIDATE" \
  "$STAGED_MATRIX_JSON" "$STAGED_MATRIX_MD"
(cd "$PACKAGE_CANDIDATE" && dart test)
dart analyze "$PACKAGE_CANDIDATE"
dart run "$ROOT/tool/sdk/import_spec.dart" \
  "$ROOT/tool/sdk/smoke.dart.template" \
  "$PACKAGE_CANDIDATE/tool/sdk_smoke.dart"
(cd "$PACKAGE_CANDIDATE" && \
  dart compile exe tool/sdk_smoke.dart --output "$WORK_DIR/sdk_smoke")
"$WORK_DIR/sdk_smoke"

[[ "$(dart run "$VERIFY" --tree-sha256 "$PACKAGE")" == "$PACKAGE_GUARD" ]] ||
  die "live SDK package changed during generation; refusing to publish"
[[ -f "$MATRIX_JSON" && -f "$MATRIX_MD" ]] ||
  die "existing contract matrices are missing"

cp -p "$MATRIX_JSON" "$WORK_DIR/previous-matrix.json"
cp -p "$MATRIX_MD" "$WORK_DIR/previous-matrix.md"
if ! mv -f "$STAGED_MATRIX_JSON" "$MATRIX_JSON"; then
  die "failed to publish JSON contract matrix"
fi
if ! mv -f "$STAGED_MATRIX_MD" "$MATRIX_MD"; then
  mv -f "$WORK_DIR/previous-matrix.json" "$MATRIX_JSON"
  die "failed to publish Markdown contract matrix"
fi
if ! mv "$PACKAGE" "$WORK_DIR/previous-package"; then
  mv -f "$WORK_DIR/previous-matrix.json" "$MATRIX_JSON"
  mv -f "$WORK_DIR/previous-matrix.md" "$MATRIX_MD"
  die "failed to preserve the current SDK package"
fi
if ! mv "$PACKAGE_CANDIDATE" "$PACKAGE"; then
  if ! mv "$WORK_DIR/previous-package" "$PACKAGE"; then
    printf 'generate.sh: package recovery failed; backup retained at %s\n' \
      "$WORK_DIR/previous-package" >&2
    WORK_DIR=""
    exit 1
  fi
  mv -f "$WORK_DIR/previous-matrix.json" "$MATRIX_JSON" || true
  mv -f "$WORK_DIR/previous-matrix.md" "$MATRIX_MD" || true
  die "failed to publish the generated SDK package"
fi
