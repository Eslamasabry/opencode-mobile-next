#!/usr/bin/env bash
# T4 (plan 02 §8, plan 13 §3.5–§3.10): Macrobenchmark journeys on the PC emulator, at slice ends, after
# performance-sensitive merges and on switch candidates. Starts no Gradle: it installs the VPS-built APKs.
#
#   native/tool/t4.sh --apks <dir> [--journeys PJ-01,PJ-01f] [--serial emulator-5584] [--phone] [--detach] [--dry-run]
#
#   --apks      folder from tool/perf/vps_build.sh: the app APK and the benchmark APK (*bench*.apk); the result JSON is
#               written next to them (results-<journey>.json) and compared by tool/perf/compare.py when it exists
#   --journeys  default PJ-01,PJ-01f (the slice-0 skeleton, WP-13.0); class names come from tool/perf/journeys.tsv
#   --phone     allow a non-emulator serial (the owner's NX721J); also needs OC_OWNER_PHONE_OK=1 for this run
#
# Refuses to start (exit 2) unless: the quiet window ($OC_PERF_QUIET_WINDOW, e.g. 00:00-02:00; empty = none) allows
# it, the 1-minute load average is < 2.0, no Gradle daemon runs, >= 4 GB RAM is available, the emulator lock is free,
# and tool/perf/restore_device.sh exists and succeeds (it runs first; it also runs at exit as a second layer).
# Journeys without a class in journeys.tsv are MISSING (a failure). Plan 13 §3.7 rule 7: run it detached.
# Exit: 0 pass, 1 a journey failed, regressed or is missing, 2 could not run.
set -uo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"
oc_maybe_detach native-t4 "$@"

apks="" journeys="PJ-01,PJ-01f" serial="${OC_T4_SERIAL:-emulator-5584}" phone=0 dry=0
while (($#)); do
  case "$1" in
    --apks) apks="${2:-}"; shift 2 ;;
    --journeys) journeys="${2:-}"; shift 2 ;;
    --serial) serial="${2:-}"; shift 2 ;;
    --phone) phone=1; shift ;;
    --dry-run) dry=1; shift ;;
    -h | --help) sed -n '2,19p' "$0"; exit 0 ;;
    *) oc_usage "t4.sh --apks <dir> [--journeys L] [--serial S] [--phone] [--detach] [--dry-run]" ;;
  esac
done

oc_run_init native-t4 "${OC_RUN_ID:-$(date +%Y%m%d-%H%M%S)}"
oc_say "journeys: $journeys; serial: $serial"
perf="$OC_NATIVE/tool/perf"
[[ -d "$apks" ]] || { oc_say "no APK folder (--apks)"; oc_status 2; exit 2; }
app_apk="" bench_apk=""
for f in "$apks"/*.apk; do
  [[ -f "$f" ]] || continue
  case "${f,,}" in *bench*) bench_apk="${bench_apk:-$f}" ;; *) app_apk="${app_apk:-$f}" ;; esac
done
[[ -n "$app_apk" && -n "$bench_apk" ]] || { oc_say "the folder needs the app APK and a *bench*.apk"; oc_status 2; exit 2; }
oc_say "apks: $(basename "$app_apk"), $(basename "$bench_apk")"

# Quiet window and quiet PC (plan 13 §3.7 rules 1 and 8).
if [[ -n "${OC_PERF_QUIET_WINDOW:-}" ]]; then
  now="$(date +%H:%M)"; from="${OC_PERF_QUIET_WINDOW%-*}"; to="${OC_PERF_QUIET_WINDOW#*-}"
  if [[ "$now" > "$from" && "$now" < "$to" ]]; then oc_say "quiet window $OC_PERF_QUIET_WINDOW: not now"; oc_status 2; exit 2; fi
fi
load="$(cut -d' ' -f1 /proc/loadavg)"
oc_say "load: $load (need < 2.0)"
awk -v l="$load" 'BEGIN { exit !(l < 2.0) }' || { oc_say "PC not quiet"; oc_status 2; exit 2; }
if ps -eo args= | grep -q '[G]radleDaemon'; then oc_say "a Gradle daemon is running; T4 needs a quiet PC"; oc_status 2; exit 2; fi
OC_MIN_FREE_MB=4096 oc_check_memory || { oc_status 2; exit 2; }

table="$perf/journeys.tsv"
declare -A cls
[[ -f "$table" ]] && while IFS=$'\t' read -r j c; do [[ "$j" == \#* || -z "$j" ]] || cls[$j]="$c"; done <"$table"
missing=()
IFS=, read -r -a js <<<"$journeys"
for j in "${js[@]}"; do [[ -n "${cls[$j]:-}" ]] || missing+=("$j"); done
((${#missing[@]})) && { oc_say "MISSING journeys (no class in tool/perf/journeys.tsv): ${missing[*]}"; oc_status 1; }

if ((dry)); then oc_say "dry run: device steps not started"; exit "$OC_STATUS"; fi

restore="$perf/restore_device.sh"
[[ -x "$restore" ]] || { oc_say "tool/perf/restore_device.sh missing: a run that cannot restore the device cannot start"; oc_status 2; exit 2; }
if [[ ! "$serial" =~ ^emulator-[0-9]+$ ]]; then
  ((phone)) && [[ "${OC_OWNER_PHONE_OK:-}" == 1 ]] ||
    { oc_say "serial $serial is not an emulator: the phone needs --phone and OC_OWNER_PHONE_OK=1 (the owner's OK)"; oc_status 2; exit 2; }
fi
lock="${OC_EMULATOR_LOCK:-/home/eslam/Storage/tmp/oc-emulator.lock}"
[[ -d "$(dirname "$lock")" ]] || lock="${TMPDIR:-/tmp}/oc-emulator.lock"
exec {emufd}>"$lock"
flock -w "${OC_LOCK_WAIT:-1800}" "$emufd" || { oc_say "lock: emulator lock busy"; oc_status 2; exit 2; }
adb="${ADB:-${ANDROID_HOME:+$ANDROID_HOME/platform-tools/}adb}"
[[ "$("$adb" -s "$serial" get-state 2>/dev/null)" == device ]] || { oc_say "device $serial not online"; oc_status 2; exit 2; }
oc_step restore "$restore" --serial "$serial" || { oc_say "restore failed"; oc_status 2; exit 2; }
oc_restore_perf() { "$restore" --serial "$serial" >/dev/null 2>&1; }
OC_CLEANUP=oc_restore_perf

oc_step install-app "$adb" -s "$serial" install -r "$app_apk" || { oc_status 2; exit 2; }
oc_step install-bench "$adb" -s "$serial" install -r "$bench_apk" || { oc_status 2; exit 2; }
bench_pkg="$("${AAPT2:-aapt2}" dump badging "$bench_apk" 2>/dev/null | sed -nE "1s/.*package: name='([^']+)'.*/\1/p")"
[[ -n "$bench_pkg" ]] || { oc_say "cannot read the benchmark package"; oc_status 2; exit 2; }

for j in "${js[@]}"; do
  [[ -n "${cls[$j]:-}" ]] || continue
  oc_step "journey-$j" "$adb" -s "$serial" shell am instrument -w -e class "${cls[$j]}" \
    -e androidx.benchmark.output.enable true "$bench_pkg/androidx.test.runner.AndroidJUnitRunner" || oc_status 1
  grep -q 'FAILURES!!!\|INSTRUMENTATION_FAILED' "$OC_RUN_DIR/journey-$j.log" && oc_status 1
  "$adb" -s "$serial" shell "cat /sdcard/Android/media/$bench_pkg/*benchmarkData.json" >"$apks/results-$j.json" 2>/dev/null ||
    oc_say "journey $j: no result JSON pulled"
done
if [[ -x "$perf/compare.py" ]]; then
  oc_step compare python3 -I "$perf/compare.py" "$apks" || oc_status 1
else
  oc_say "compare: tool/perf/compare.py does not exist yet; results not compared with last-green"; oc_status 1
fi
exit "$OC_STATUS"
