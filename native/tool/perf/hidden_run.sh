#!/usr/bin/env bash
# PR-01 (plan 13 §3.5): the app hidden and idle for 30 min, then 30 min more in forced Doze. Budgets judged here:
# PB-40 (app CPU ≤ 1 s per hour, 0 wake-up alarms) and PB-53 (0 open streams, 0 requests); the raw dumps for
# PB-23, 42, 43 and 44 are kept in the run folder for their owners. Owner: S2-BACKOFF (WP-13.6).
#
#   native/tool/perf/hidden_run.sh --scenario idle [--minutes 30] [--serial emulator-5584] [--port 4190]
#                                  [--upstream-port 4191] [--grace-s 30] [--detach] [--dry-run]
#
# Needs: the bench APK installed (WP-13.0, with BenchSeedReceiver), FX-OC2-PERF listening on --upstream-port, the
# emulator lock. The app dials the counting proxy (conn_log_proxy.py) on --port, which forwards to the fixture.
# Steps: restore; proxy; seed; open; connected; Home; screen off; <minutes>; `dumpsys deviceidle force-idle`;
# <minutes>; readings; verdicts (hidden_checks.py pr01). Other scenarios (watching, phone-server, linux-service:
# PR-02, PR-03, PR-03b) belong to their own units and exit 2 here.
# Exit: 0 pass, 1 a budget failed, 2 could not run. Plan 13 §3.7 rule 7: run it with --detach.
set -uo pipefail
# shellcheck source=../lib/common.sh
source "$(dirname "$0")/../lib/common.sh"
oc_maybe_detach native-pr01 "$@"
# shellcheck source=hidden_lib.sh
source "$(dirname "$0")/hidden_lib.sh"

scenario="" minutes=30 serial="${OC_T4_SERIAL:-emulator-5584}" port=4190 upstream=4191 grace=30 dry=0
while (($#)); do
  case "$1" in
    --scenario) scenario="${2:-}"; shift 2 ;;
    --minutes) minutes="${2:-}"; shift 2 ;;
    --serial) serial="${2:-}"; shift 2 ;;
    --port) port="${2:-}"; shift 2 ;;
    --upstream-port) upstream="${2:-}"; shift 2 ;;
    --grace-s) grace="${2:-}"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    -h | --help) sed -n '2,15p' "$0"; exit 0 ;;
    *) oc_usage "hidden_run.sh --scenario idle [--minutes N] [--serial S] [--port P] [--upstream-port U] [--grace-s G] [--detach] [--dry-run]" ;;
  esac
done
[[ "$scenario" == idle ]] || { echo "scenario '$scenario': only 'idle' (PR-01) is implemented here" >&2; exit 2; }
[[ "$minutes" =~ ^[0-9]+$ && "$grace" =~ ^[0-9]+$ ]] || oc_usage "--minutes and --grace-s are whole numbers"

oc_run_init native-pr01 "${OC_RUN_ID:-$(date +%Y%m%d-%H%M%S)}"
oc_say "PR-01 idle: $minutes + $minutes min, serial $serial, proxy :$port -> :$upstream, grace $grace s"
if ((dry)); then
  oc_say "dry run: restore, proxy, seed, open, connect, Home, screen off, $minutes min, force-idle, $minutes min, verdicts"
  exit 0
fi

hr_prepare "$serial"
hr_start_proxy "$port" "$upstream"
hr_seed_and_open "$port"
hr_wait_stream 60
hr_adb shell input keyevent KEYCODE_HOME
hr_adb shell input keyevent KEYCODE_SLEEP
hr_mark hidden
sleep "$grace"
cpu0="$(hr_cpu_ticks)" wake0="$(hr_wakeups)"
hr_sleep_min "$minutes"
hr_adb shell dumpsys deviceidle force-idle >"$OC_RUN_DIR/force-idle.txt" 2>&1
hr_mark forced_idle
hr_sleep_min "$minutes"
cpu1="$(hr_cpu_ticks)" wake1="$(hr_wakeups)"
hr_mark end
clk="$(hr_adb shell getconf CLK_TCK 2>/dev/null | tr -d '\r')"
[[ "$clk" =~ ^[0-9]+$ ]] || clk=100
if [[ "$cpu0" == -1 || "$cpu1" == -1 ]]; then
  oc_say "the app process was not running at a reading (killed while hidden?): CPU cannot be judged"; oc_status 2; exit 2
fi
printf '{"cpu_ticks": {"start": %s, "end": %s}, "clk_tck": %s, "wakeups": {"start": %s, "end": %s}}\n' \
  "$cpu0" "$cpu1" "$clk" "$wake0" "$wake1" >"$OC_RUN_DIR/readings.json"
# Raw dumps for PB-23, 42, 43, 44 (other owners judge them).
hr_adb shell dumpsys meminfo "$HR_PKG" >"$OC_RUN_DIR/meminfo.txt" 2>&1
hr_adb shell dumpsys alarm >"$OC_RUN_DIR/alarm.txt" 2>&1
hr_adb shell dumpsys batterystats "$HR_PKG" >"$OC_RUN_DIR/batterystats.txt" 2>&1
hr_stop_proxy

python3 "$PERF_DIR/hidden_checks.py" pr01 --run "$OC_RUN_DIR" --grace-s "$grace" | tee -a "$OC_SUMMARY"
oc_status "${PIPESTATUS[0]}"
