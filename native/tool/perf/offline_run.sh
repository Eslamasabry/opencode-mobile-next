#!/usr/bin/env bash
# PR-04 (plan 13 §3.5): offline and server-down while hidden and watching (BackgroundWatching). Budgets: PB-52
# (0 attempts while Android has no network; one try at once on return) and PB-51 (hidden retries 1 s, 2 s, then every
# 60–120 s jittered; a waiting retry fires at once on return to the foreground). Owner: S2-BACKOFF (WP-13.6).
#
#   native/tool/perf/offline_run.sh [--minutes 10] [--serial emulator-5584] [--port 4190] [--upstream-port 4191]
#                                   [--detach] [--dry-run]
#
# Needs what hidden_run.sh needs, and BenchSeedReceiver's `stayConnected` extra (live mode on, so the hidden app is
# BackgroundWatching without a running turn). Steps: restore; proxy; seed (stay connected); open; connected; Home;
# screen off; airplane mode on <minutes>, off; 1 min; server down (proxy cuts and refuses at HTTP level, every attempt
# logged) <minutes>; app to the foreground while still down; 10 s; server up; verdicts (hidden_checks.py pr04).
# Exit: 0 pass, 1 a budget failed, 2 could not run. Plan 13 §3.7 rule 7: run it with --detach.
set -uo pipefail
# shellcheck source=../lib/common.sh
source "$(dirname "$0")/../lib/common.sh"
oc_maybe_detach native-pr04 "$@"
# shellcheck source=hidden_lib.sh
source "$(dirname "$0")/hidden_lib.sh"

minutes=10 serial="${OC_T4_SERIAL:-emulator-5584}" port=4190 upstream=4191 dry=0
while (($#)); do
  case "$1" in
    --minutes) minutes="${2:-}"; shift 2 ;;
    --serial) serial="${2:-}"; shift 2 ;;
    --port) port="${2:-}"; shift 2 ;;
    --upstream-port) upstream="${2:-}"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    -h | --help) sed -n '2,13p' "$0"; exit 0 ;;
    *) oc_usage "offline_run.sh [--minutes N] [--serial S] [--port P] [--upstream-port U] [--detach] [--dry-run]" ;;
  esac
done
[[ "$minutes" =~ ^[0-9]+$ ]] || oc_usage "--minutes is a whole number"

oc_run_init native-pr04 "${OC_RUN_ID:-$(date +%Y%m%d-%H%M%S)}"
oc_say "PR-04: airplane $minutes min, server down $minutes min, serial $serial, proxy :$port -> :$upstream"
if ((dry)); then
  oc_say "dry run: restore, proxy, seed (stay connected), open, connect, Home, screen off, airplane $minutes min, 1 min," \
    "server down $minutes min, foreground, 10 s, server up, verdicts"
  exit 0
fi

hr_prepare "$serial"
hr_start_proxy "$port" "$upstream"
hr_seed_and_open "$port" --ez stayConnected true
hr_wait_stream 60
hr_adb shell input keyevent KEYCODE_HOME
hr_adb shell input keyevent KEYCODE_SLEEP
hr_mark watch_hidden
sleep 30

hr_adb shell cmd connectivity airplane-mode enable
hr_mark airplane_on
hr_sleep_min "$minutes"
hr_adb shell cmd connectivity airplane-mode disable
hr_mark airplane_off
sleep 60

touch "$OC_RUN_DIR/server-down"
sleep 0.3 # the proxy polls the flag every 0.2 s and cuts the live stream
hr_mark server_down
hr_sleep_min "$minutes"
hr_adb shell input keyevent KEYCODE_WAKEUP
hr_mark foreground # before the launch: the retry may fire before `monkey` returns
hr_adb shell monkey -p "$HR_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
rm -f "$OC_RUN_DIR/server-down"
hr_mark server_up
sleep 10
hr_mark end
hr_stop_proxy

python3 "$PERF_DIR/hidden_checks.py" pr04 --run "$OC_RUN_DIR" | tee -a "$OC_SUMMARY"
oc_status "${PIPESTATUS[0]}"
