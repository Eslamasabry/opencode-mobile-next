#!/usr/bin/env bash
# Owner switch-day preflight (E00-S16, runbook docs/native/switch-day.md). READ-ONLY: it asks the phone what is
# installed and how much room there is, and changes nothing. It never takes a screenshot or screen recording, never
# installs, uninstalls, clears or starts anything, and never reads app files.
#
#   native/tool/qa/owner_preflight.sh --expect-signer <SHA-256> [--serial S] [--apk <native.apk>] [--min-free-mb N]
#
# Order (it stops at the first REFUSED line):
#   1. exactly one device (or --serial), and the Flutter app io.github.eslamasabry.opencode_mobile is installed;
#   2. the installed signer: base.apk is pulled to a temporary folder (adb pull) and apksigner must print exactly one
#      signer equal to --expect-signer. Anything else STOPS here: an APK from another lineage cannot update this phone;
#   3. installed versionCode/versionName (dumpsys package); with --apk, the new APK's versionCode must be higher and
#      its package and signer must match;
#   4. free space on /data (df) against --min-free-mb (default 1536);
#   5. the app's data size, which holds the built-in Linux (dumpsys diskstats), reported so the owner knows what the
#      update keeps.
# Tools: OC_ADB (default adb), OC_APKSIGNER / OC_AAPT (default: newest Android SDK build-tools, then PATH).
# Exit: 0 every check passed, 1 refused (stop: do not install), 2 could not run.
set -uo pipefail

readonly PKG="io.github.eslamasabry.opencode_mobile"
readonly OWNER_LOCAL="1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"
readonly PUBLIC_CI="842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"

usage() { sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; }
cannot() { echo "owner preflight: cannot run: $*" >&2; exit 2; }
refuse() { echo "REFUSED: $*"; echo "owner preflight: STOP. Do not install anything on this phone."; exit 1; }
ok() { echo "ok      $*"; }

native_dir="$(cd "$(dirname "$0")/../.." && pwd)"
expect="" serial="" new_apk="" min_free_mb=1536
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help) usage; exit 0 ;;
    --expect-signer) [[ $# -ge 2 ]] || cannot "--expect-signer needs a value"; expect="$2"; shift 2 ;;
    --serial) [[ $# -ge 2 ]] || cannot "--serial needs a value"; serial="$2"; shift 2 ;;
    --apk) [[ $# -ge 2 ]] || cannot "--apk needs a file"; new_apk="$2"; shift 2 ;;
    --min-free-mb) [[ $# -ge 2 ]] || cannot "--min-free-mb needs a number"; min_free_mb="$2"; shift 2 ;;
    *) cannot "unknown argument $1 (see --help)" ;;
  esac
done
[[ -n "$expect" ]] || cannot "--expect-signer <SHA-256> is required (the lineage the phone should hold; never guessed)"
expect="$(tr -d ':[:space:]' <<<"$expect" | tr '[:lower:]' '[:upper:]')"
[[ "$expect" =~ ^[0-9A-F]{64}$ ]] || cannot "--expect-signer is not a SHA-256 fingerprint"
[[ "$expect" == "$OWNER_LOCAL" || "$expect" == "$PUBLIC_CI" ]] ||
  refuse "--expect-signer is not a live lineage (Owner local 1DE5BF08…, Public CI 842284B2…)"
[[ "$min_free_mb" =~ ^[0-9]+$ ]] || cannot "--min-free-mb is not a number"
[[ -z "$new_apk" || -f "$new_apk" ]] || cannot "no APK at $new_apk"

ADB="${OC_ADB:-adb}"
command -v "$ADB" >/dev/null 2>&1 || cannot "adb not found (set OC_ADB)"
sdk_dir() {
  local d="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$d" && -f "$native_dir/local.properties" ]]; then d="$(sed -n 's/^sdk\.dir=//p' "$native_dir/local.properties" | tail -n 1)"; fi
  printf '%s' "$d"
}
find_tool() {
  local override="$1" sdk bt name; shift
  if [[ -n "$override" ]]; then printf '%s' "$override"; return 0; fi
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
APKSIGNER="$(find_tool "${OC_APKSIGNER:-}" apksigner)" || cannot "apksigner not found (set OC_APKSIGNER or ANDROID_HOME)"

# The only device commands this script sends. Each is read-only.
dev() { if [[ -n "$serial" ]]; then "$ADB" -s "$serial" "$@"; else "$ADB" "$@"; fi; }
signers_of() { # signers_of <apk>: one upper-case SHA-256 per line
  "$APKSIGNER" verify --print-certs "$1" 2>/dev/null |
    sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest:[[:space:]]*//p' | tr -d ': \t\r' | tr '[:lower:]' '[:upper:]'
}

work="$(mktemp -d "${TMPDIR:-/tmp}/oc-owner-preflight.XXXXXX")" || cannot "no temporary folder"
trap 'rm -rf "$work"' EXIT

# 1. Device and package.
if [[ -z "$serial" ]]; then
  mapfile -t devices < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  [[ ${#devices[@]} -eq 1 ]] || refuse "${#devices[@]} devices are connected; connect only the phone or pass --serial"
  serial="${devices[0]}"
fi
[[ "$(dev get-state 2>/dev/null | tr -d '\r')" == "device" ]] || refuse "device $serial is not ready (unlock it and allow USB debugging)"
ok "device $serial"
path="$(dev shell pm path "$PKG" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep '/base\.apk$' | head -n 1)"
[[ -n "$path" ]] || refuse "$PKG is not installed on $serial (nothing to update; this runbook is for the update)"

# 2. Installed signer: stop here unless it is the expected lineage.
dev pull "$path" "$work/installed.apk" >/dev/null 2>&1 || refuse "could not copy the installed base.apk to read its signer"
mapfile -t installed_signers < <(signers_of "$work/installed.apk")
[[ ${#installed_signers[@]} -eq 1 ]] || refuse "the installed app has ${#installed_signers[@]} signers; expected exactly one"
[[ "${installed_signers[0]}" == "$expect" ]] ||
  refuse "the installed signer is ${installed_signers[0]:0:8}…, not the expected lineage ${expect:0:8}…; an APK signed for ${expect:0:8}… cannot update this phone"
ok "installed signer ${expect:0:8}… (one signer)"

# 3. Installed version (and the new APK's, when given).
dump="$(dev shell dumpsys package "$PKG" 2>/dev/null | tr -d '\r')"
installed_code="$(grep -m1 -o 'versionCode=[0-9]*' <<<"$dump" | cut -d= -f2)"
installed_name="$(grep -m1 -o 'versionName=[^[:space:]]*' <<<"$dump" | cut -d= -f2)"
[[ "$installed_code" =~ ^[0-9]+$ ]] || refuse "could not read the installed versionCode"
ok "installed version ${installed_name:-?}+$installed_code"
if [[ -n "$new_apk" ]]; then
  AAPT="$(find_tool "${OC_AAPT:-}" aapt2 aapt)" || cannot "aapt2/aapt not found (set OC_AAPT or ANDROID_HOME)"
  line="$("$AAPT" dump badging "$new_apk" 2>/dev/null | grep -m1 '^package:')"
  new_pkg="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$line")"
  new_code="$(sed -n "s/^package:.* versionCode='\([^']*\)'.*/\1/p" <<<"$line")"
  [[ "$new_pkg" == "$PKG" ]] || refuse "the new APK is package ${new_pkg:-<none>}, not $PKG"
  [[ "$new_code" =~ ^[0-9]+$ ]] && ((new_code > installed_code)) ||
    refuse "the new APK's versionCode ${new_code:-<none>} is not above the installed $installed_code"
  mapfile -t new_signers < <(signers_of "$new_apk")
  [[ ${#new_signers[@]} -eq 1 && "${new_signers[0]}" == "$expect" ]] ||
    refuse "the new APK is not signed by exactly the installed lineage ${expect:0:8}…"
  ok "new APK $new_pkg $new_code, same single signer"
fi

# 4. Free space on /data.
free_kb="$(dev shell df -k /data 2>/dev/null | tr -d '\r' | awk 'NR == 2 { print $4 }')"
[[ "$free_kb" =~ ^[0-9]+$ ]] || refuse "could not read the free space on /data"
free_mb=$((free_kb / 1024))
((free_mb >= min_free_mb)) || refuse "only $free_mb MB free on /data; the update needs at least $min_free_mb MB (free some space first)"
ok "free space $free_mb MB (at least $min_free_mb MB)"

# 5. App data size, which includes the built-in Linux.
stats="$(dev shell dumpsys diskstats 2>/dev/null | tr -d '\r')"
size_of() { # size_of <list label>: the entry at the package's index in that list
  local names sizes
  names="$(sed -n 's/^Package Names: \[\(.*\)\]$/\1/p' <<<"$stats" | tr -d '"' | tr ',' '\n')"
  sizes="$(sed -n "s/^$1: \[\(.*\)\]$/\1/p" <<<"$stats" | tr ',' '\n')"
  local i
  i="$(grep -n -x "$PKG" <<<"$names" | head -n 1 | cut -d: -f1)"
  [[ -n "$i" ]] && sed -n "${i}p" <<<"$sizes"
}
data_bytes="$(size_of "App Data Sizes")"
if [[ "$data_bytes" =~ ^[0-9]+$ ]]; then
  ok "app data (projects and the built-in Linux) $((data_bytes / 1024 / 1024)) MB; the update keeps it"
else
  echo "note    the app data size is not in dumpsys diskstats yet (Android updates it about once a day)"
fi

echo "owner preflight: PASS ($PKG ${installed_name:-?}+$installed_code, signer ${expect:0:8}…). Nothing on the phone was changed."
exit 0
