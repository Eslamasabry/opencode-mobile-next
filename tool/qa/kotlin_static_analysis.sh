#!/usr/bin/env bash
# Standalone, syntax-based Kotlin gate. No Android build or signing required.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
version=1.23.8
jar_name="detekt-cli-${version}-all.jar"
jar_sha256=2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1
url="https://github.com/detekt/detekt/releases/download/v${version}/${jar_name}"
cache_dir="${OC_DETEKT_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/oc-mobile-tools}"
input="$repo_root/android"
baseline="$repo_root/android/config/detekt/baseline.xml"
report_dir="$repo_root/build/reports/detekt"
create_baseline=false

usage() {
  cat <<'USAGE'
Usage: tool/qa/kotlin_static_analysis.sh [options]
  --input PATH             Kotlin source tree (default: android/)
  --baseline PATH          Reviewed baseline to check against
  --report-dir PATH        Reports (default: build/reports/detekt/)
  --create-baseline PATH   Explicitly generate a new baseline for review
  --help                   Show this help

The normal gate never updates a baseline. Java runs through machine_lock build.
The pinned jar is downloaded once and its SHA-256 is checked on every run.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --help) usage; exit 0 ;;
    --input|--baseline|--report-dir|--create-baseline)
      option="$1"
      if [[ $# -lt 2 || -z "$2" || "$2" == --* ]]; then
        echo "Missing value for $option" >&2; exit 64
      fi
      case "$option" in
        --input) input="$2" ;;
        --baseline) baseline="$2" ;;
        --report-dir) report_dir="$2" ;;
        --create-baseline) baseline="$2"; create_baseline=true ;;
      esac
      shift 2
      ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 64 ;;
  esac
done

[[ -d "$input" ]] || { echo "Kotlin input directory does not exist: $input" >&2; exit 66; }
if [[ -z "$(find "$input" -type f \( -name '*.kt' -o -name '*.kts' \) \
  ! -path '*/build/*' ! -path '*/.gradle/*' -print -quit)" ]]; then
  echo "Kotlin input directory contains no source files: $input" >&2; exit 66
fi
if [[ "$create_baseline" == false && ! -f "$baseline" ]]; then
  echo "Reviewed baseline is missing: $baseline" >&2
  exit 66
fi

mkdir -p "$cache_dir"
jar="$cache_dir/$jar_name"
# Coordinate downloads independently from the shared heavy-work lock.
exec {download_lock}>"$cache_dir/.detekt-download.lock"
flock "$download_lock"
if [[ ! -f "$jar" ]]; then
  temporary_jar="$(mktemp "$cache_dir/.detekt-download.XXXXXX")"
  trap 'rm -f "$temporary_jar"' EXIT
  curl --fail --location --silent --show-error --retry 2 \
    --connect-timeout 20 --max-time 180 \
    --proto '=https' --proto-redir '=https' "$url" -o "$temporary_jar"
  if ! printf '%s  %s\n' "$jar_sha256" "$temporary_jar" | sha256sum --check --status; then
    echo "detekt download checksum mismatch; analysis was not run." >&2; exit 65
  fi
  mv "$temporary_jar" "$jar"
  trap - EXIT
fi
if ! printf '%s  %s\n' "$jar_sha256" "$jar" | sha256sum --check --status; then
  echo "Cached detekt checksum mismatch; remove $jar and retry." >&2; exit 65
fi
flock -u "$download_lock"

mkdir -p "$report_dir"
args=(
  --input "$input"
  --excludes '**/build/**,**/.gradle/**'
  --config "$repo_root/android/config/detekt/detekt.yml"
  --build-upon-default-config
  --baseline "$baseline"
  --max-issues 0
  --jvm-target 17
  --language-version 2.0
  --base-path "$repo_root"
  --report "txt:$report_dir/detekt.txt"
  --report "xml:$report_dir/detekt.xml"
)
if [[ "$create_baseline" == true ]]; then
  mkdir -p "$(dirname "$baseline")"
  args+=(--create-baseline)
  echo "Generating detekt $version baseline for explicit review: $baseline"
else
  echo "Checking detekt $version: zero new findings against $baseline"
fi
# No --parallel, no classpath/type resolution, no autocorrect, no ignore-failure.
exec "$repo_root/tool/qa/machine_lock.sh" build -- \
  java -Xmx512m -jar "$jar" "${args[@]}"
