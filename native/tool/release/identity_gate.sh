#!/usr/bin/env bash
# Release identity gate (E01-S01, docs/native/release-identity.md). Refuses any APK that would not install over the
# Flutter app as an update on the phones that hold the expected signing lineage.
#
#   native/tool/release/identity_gate.sh --expect-signer <SHA-256> [--version-code N] [--version-name X.Y.Z]
#                                         [--package P] <apk>...
#
# Every APK must:
#   - be package io.github.eslamasabry.opencode_mobile (the switch build; --package only for a planted test);
#   - carry versionCode N and versionName X.Y.Z (default: native/version.properties), N at least 10010, X.Y.Z plain;
#   - verify with apksigner and print exactly ONE signer whose SHA-256 equals --expect-signer;
# and all APKs given (the ABI splits and the universal one) must carry the same versionCode.
# --expect-signer must be one of the two live lineages (Owner local 1DE5BF08…, Public CI 842284B2…); the Legacy CI
# certificate and debug keys are refused. Nothing here reads, prints or needs a key.
# Tools: OC_APKSIGNER / OC_AAPT, else the newest build-tools under ANDROID_HOME / ANDROID_SDK_ROOT / local.properties,
# else PATH.
# Exit: 0 every APK passed, 1 refused (each problem printed), 2 could not run.
set -uo pipefail

readonly SWITCH_PACKAGE="io.github.eslamasabry.opencode_mobile"
readonly FIRST_RELEASE_CODE=10010
readonly OWNER_LOCAL="1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"
readonly PUBLIC_CI="842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"
readonly LEGACY_CI="2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC"

usage() { sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; }
cannot() { echo "identity gate: cannot run: $*" >&2; exit 2; }

native_dir="$(cd "$(dirname "$0")/../.." && pwd)"
expect="" want_code="" want_name="" package="$SWITCH_PACKAGE"
apks=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help) usage; exit 0 ;;
    --expect-signer) [[ $# -ge 2 ]] || cannot "--expect-signer needs a value"; expect="$2"; shift 2 ;;
    --version-code) [[ $# -ge 2 ]] || cannot "--version-code needs a value"; want_code="$2"; shift 2 ;;
    --version-name) [[ $# -ge 2 ]] || cannot "--version-name needs a value"; want_name="$2"; shift 2 ;;
    --package) [[ $# -ge 2 ]] || cannot "--package needs a value"; package="$2"; shift 2 ;;
    --) shift; apks+=("$@"); break ;;
    -*) cannot "unknown option $1 (see --help)" ;;
    *) apks+=("$1"); shift ;;
  esac
done
[[ -n "$expect" ]] || cannot "--expect-signer <SHA-256> is required (the installed lineage; never guessed)"
[[ ${#apks[@]} -gt 0 ]] || cannot "no APK given"

expect="$(tr -d ':[:space:]' <<<"$expect" | tr '[:lower:]' '[:upper:]')"
[[ "$expect" =~ ^[0-9A-F]{64}$ ]] || cannot "--expect-signer is not a SHA-256 fingerprint"
case "$expect" in
  "$OWNER_LOCAL" | "$PUBLIC_CI") ;;
  "$LEGACY_CI") echo "REFUSED: --expect-signer is the Legacy CI certificate; native never ships on it." >&2; exit 1 ;;
  *) echo "REFUSED: --expect-signer is not a live lineage (Owner local 1DE5BF08…, Public CI 842284B2…)." >&2; exit 1 ;;
esac

props="$native_dir/version.properties"
prop() { sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*\([^[:space:]#]*\).*/\1/p" "$props" | tail -n 1; }
if [[ -z "$want_code" || -z "$want_name" ]]; then
  [[ -f "$props" ]] || cannot "no $props (pass --version-code and --version-name)"
  [[ -n "$want_code" ]] || want_code="$(prop versionCode)"
  [[ -n "$want_name" ]] || want_name="$(prop versionName)"
fi
[[ "$want_code" =~ ^[1-9][0-9]*$ ]] || cannot "expected versionCode '$want_code' is not a number"

sdk_dir() {
  local d="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$d" && -f "$native_dir/local.properties" ]]; then
    d="$(sed -n 's/^sdk\.dir=//p' "$native_dir/local.properties" | tail -n 1)"
  fi
  printf '%s' "$d"
}
find_tool() { # find_tool <override var value> <name>...
  local override="$1"; shift
  if [[ -n "$override" ]]; then printf '%s' "$override"; return 0; fi
  local sdk name bt
  sdk="$(sdk_dir)"
  for name in "$@"; do
    if [[ -n "$sdk" && -d "$sdk/build-tools" ]]; then
      bt="$(find "$sdk/build-tools" -mindepth 2 -maxdepth 2 -name "$name" -type f 2>/dev/null | sort -V | tail -n 1)"
      [[ -n "$bt" ]] && { printf '%s' "$bt"; return 0; }
    fi
    command -v "$name" 2>/dev/null && return 0
  done
  return 1
}
apksigner="$(find_tool "${OC_APKSIGNER:-}" apksigner)" || cannot "apksigner not found (set OC_APKSIGNER or ANDROID_HOME)"
aapt="$(find_tool "${OC_AAPT:-}" aapt2 aapt)" || cannot "aapt2/aapt not found (set OC_AAPT or ANDROID_HOME)"

problems=0
refuse() { echo "REFUSED $1: $2"; problems=$((problems + 1)); }
codes=()
for apk in "${apks[@]}"; do
  name="$(basename "$apk")"
  before=$problems
  if [[ ! -f "$apk" || -L "$apk" ]]; then refuse "$name" "not a regular file"; continue; fi

  if ! badging="$("$aapt" dump badging "$apk" 2>/dev/null)"; then
    refuse "$name" "aapt cannot read the package identity"; continue
  fi
  line="$(grep -m1 "^package:" <<<"$badging")"
  got_pkg="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$line")"
  got_code="$(sed -n "s/^package:.* versionCode='\([^']*\)'.*/\1/p" <<<"$line")"
  got_name="$(sed -n "s/^package:.* versionName='\([^']*\)'.*/\1/p" <<<"$line")"
  codes+=("$got_code")
  [[ "$got_pkg" == "$package" ]] || refuse "$name" "package is ${got_pkg:-<none>}, expected $package"
  [[ "$got_code" == "$want_code" ]] || refuse "$name" "versionCode is ${got_code:-<none>}, expected $want_code"
  if [[ "$got_code" =~ ^[0-9]+$ ]] && ((got_code < FIRST_RELEASE_CODE)); then
    refuse "$name" "versionCode $got_code is below the first native release number $FIRST_RELEASE_CODE"
  fi
  [[ "$got_name" == "$want_name" ]] || refuse "$name" "versionName is ${got_name:-<none>}, expected $want_name"
  [[ "$got_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || refuse "$name" "versionName ${got_name:-<none>} is not a plain X.Y.Z"

  if ! certs="$("$apksigner" verify --print-certs "$apk" 2>/dev/null)"; then
    refuse "$name" "apksigner does not verify the signature (unsigned or broken)"; continue
  fi
  mapfile -t signers < <(sed -n 's/^.*Signer[^:]*:\{0,1\}[[:space:]]*certificate SHA-256 digest:[[:space:]]*//p' <<<"$certs" |
    tr -d ': \t\r' | tr '[:lower:]' '[:upper:]' | sort -u)
  if [[ ${#signers[@]} -ne 1 ]]; then
    refuse "$name" "apksigner printed ${#signers[@]} signers; exactly one is allowed"
  elif [[ "${signers[0]}" != "$expect" ]]; then
    refuse "$name" "signer ${signers[0]:0:8}… is not the expected lineage ${expect:0:8}…"
  fi
  ((problems == before)) && echo "ok      $name  $got_pkg $got_name+$got_code signer ${expect:0:8}…"
done

if [[ ${#codes[@]} -gt 1 ]] && [[ "$(printf '%s\n' "${codes[@]}" | sort -u | wc -l)" -ne 1 ]]; then
  refuse "set" "the APKs carry different versionCodes ($(printf '%s ' "${codes[@]}" | sed 's/ $//'))"
fi

if ((problems > 0)); then
  echo "identity gate: REFUSED ($problems problem(s)); do not deliver these APKs."
  exit 1
fi
echo "identity gate: PASS (${#apks[@]} APK(s), $package $want_name+$want_code, signer ${expect:0:8}…)"
exit 0
