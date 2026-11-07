#!/usr/bin/env bash
# E01-S08: proves on the BUILT (R8-minified) APK that every class the component freeze names is still defined under its
# frozen name (quality/config/component-freeze/flutter-2.1.4.tsv: component, widget, tile and worker lines). R8 renaming
# or dropping one would break placed widgets, tiles, queued work or alarms on the update, and the manifest-only freeze
# test cannot see that.
#
#   native/tool/release/kept_classes_check.sh <apk>...
#
# Tools: OC_DEXDUMP, else the newest build-tools dexdump under ANDROID_HOME / ANDROID_SDK_ROOT / local.properties,
# else PATH. Needs python3 (reads the APK's classes*.dex).
# Exit: 0 every frozen class is in every APK, 1 one is missing (each printed), 2 could not run.
set -uo pipefail

native_dir="$(cd "$(dirname "$0")/../.." && pwd)"
freeze="$native_dir/quality/config/component-freeze/flutter-2.1.4.tsv"
cannot() { echo "kept classes: cannot run: $*" >&2; exit 2; }
case "${1:-}" in -h | --help) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;; "") cannot "no APK given" ;; esac
[[ -f "$freeze" ]] || cannot "no freeze list at $freeze"
command -v python3 >/dev/null || cannot "python3 is required"

dexdump="${OC_DEXDUMP:-}"
if [[ -z "$dexdump" ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -z "$sdk" && -f "$native_dir/local.properties" ]] && sdk="$(sed -n 's/^sdk\.dir=//p' "$native_dir/local.properties" | tail -n 1)"
  [[ -n "$sdk" && -d "$sdk/build-tools" ]] &&
    dexdump="$(find "$sdk/build-tools" -mindepth 2 -maxdepth 2 -name dexdump -type f 2>/dev/null | sort -V | tail -n 1)"
  [[ -n "$dexdump" ]] || dexdump="$(command -v dexdump 2>/dev/null)"
fi
[[ -n "$dexdump" ]] || cannot "dexdump not found (set OC_DEXDUMP or ANDROID_HOME)"

mapfile -t wanted < <(awk -F'\t' '!/^#/ && NF >= 2 {
  if ($1 == "component") { sub(/^[^:]*:/, "", $2); print $2 }
  else if ($1 == "widget" || $1 == "tile" || $1 == "worker") print $2 }' "$freeze" | sort -u)
[[ ${#wanted[@]} -gt 0 ]] || cannot "the freeze list names no classes"

work="$(mktemp -d "${TMPDIR:-/tmp}/oc-kept.XXXXXX")" || cannot "no temporary folder"
trap 'rm -rf "$work"' EXIT
missing=0
for apk in "$@"; do
  [[ -f "$apk" ]] || cannot "no APK at $apk"
  dir="$work/$(basename "$apk")"; mkdir -p "$dir"
  python3 -I -c 'import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
names = [n for n in z.namelist() if n.startswith("classes") and n.endswith(".dex") and "/" not in n]
if not names: sys.exit("no classes*.dex")
for n in names: z.extract(n, sys.argv[2])' "$apk" "$dir" || cannot "cannot read the dex files of $apk"
  defined="$(for dex in "$dir"/classes*.dex; do "$dexdump" "$dex" 2>/dev/null; done |
    sed -n "s/^[[:space:]]*Class descriptor[[:space:]]*:[[:space:]]*'L\(.*\);'.*/\1/p" | tr '/' '.' | sort -u)"
  [[ -n "$defined" ]] || cannot "dexdump listed no classes in $apk"
  for cls in "${wanted[@]}"; do
    if ! grep -qxF "$cls" <<<"$defined"; then
      echo "MISSING $(basename "$apk"): $cls (renamed or removed by R8; add a -keep line to androidApp/release-rules.pro)"
      missing=$((missing + 1))
    fi
  done
done
if ((missing > 0)); then echo "kept classes: FAIL ($missing missing)"; exit 1; fi
echo "kept classes: PASS (${#wanted[@]} frozen classes in each of $# APK(s))"
