#!/bin/bash
# device-bench.sh <startup|frames|profile|all> <out-dir> [serial]
# Runs the Macrobenchmark classes on the attached device or emulator and collects the numbers into <out-dir>:
#   startup  StartupBenchmark (cold start, no profile and with the baseline profile) -> startup.json + summary.md (p50/p90)
#   frames   StreamBenchmark (frame timing while a reply streams)
#   profile  StartupProfileGenerator -> baseline-prof.txt + startup-prof.txt (copy them with tool/qa/apply-profile.sh)
# Needs: the previewBenchmark APK and the benchmark test APK built (:androidApp:assemblePreviewBenchmark
# :benchmark:assembleBenchmark), `adb` on PATH. Used by .github/workflows/native-device.yml and by hand.
set -euo pipefail
what=${1:-startup}; out=${2:?out dir}; serial=${3:-}
cd "$(dirname "$0")/../.."
adb=(adb); [ -n "$serial" ] && adb=(adb -s "$serial")
app=$(ls androidApp/build/outputs/apk/preview/benchmark/*.apk | head -1)
test=$(ls benchmark/build/outputs/apk/benchmark/*.apk | head -1)
PKG=io.github.eslamasabry.opencode_mobile.native
T=io.github.eslamasabry.oc.benchmark
mkdir -p "$out"
"${adb[@]}" install -r -t "$app" >/dev/null
"${adb[@]}" install -r -t "$test" >/dev/null
# Test Lab and CI devices run with animations off; the benchmarks that care set them themselves.
run() { # <class> <name>
  local cls=$1 name=$2 dir=/sdcard/Download/oc-bench-$2
  "${adb[@]}" shell "rm -rf $dir; mkdir -p $dir"
  "${adb[@]}" shell am instrument -w -r \
    -e class "$T.$cls" -e additionalTestOutputDir "$dir" \
    -e androidx.benchmark.suppressErrors EMULATOR,LOW-BATTERY,UNLOCKED \
    "$T/androidx.test.runner.AndroidJUnitRunner" | tee "$out/$name.instrument.txt"
  mkdir -p "$out/$name"
  "${adb[@]}" pull "$dir/." "$out/$name/" >/dev/null || true
}
case "$what" in startup|all) run StartupBenchmark startup ;; esac
case "$what" in frames|all) run StreamBenchmark frames ;; esac
case "$what" in profile|all) run StartupProfileGenerator profile ;; esac
python3 "$(dirname "$0")/bench_summary.py" "$out" > "$out/summary.md" || true
cat "$out/summary.md"
